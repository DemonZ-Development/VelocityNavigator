/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator.routing;
import com.demonz.velocitynavigator.FloodgateIntegration;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.locale.MessageFormatter;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class ConnectionWorkflow {

    private static final long RETRY_BASE_DELAY_MS = 200L;
    private static final long RETRY_MAX_DELAY_MS = 2_000L;

    private ConnectionWorkflow() {
    }

    public static RouteDecision withTargetFirst(RouteDecision decision, String targetServer, String reason) {
        return new RouteDecision(
                decision.sourceServer(),
                decision.requestedGroup(),
                decision.usedGroup(),
                decision.configuredCandidates(),
                decision.onlineCandidates(),
                targetServer,
                decision.fallbackToDefault(),
                reason,
                decision.selectionMode(),
                orderedWithTargetFirst(decision.orderedCandidates(), targetServer)
        );
    }

    public static void connectFromSelection(VelocityNavigator plugin, Player player, Config config,
                                            RouteDecision decision, String targetServer, String reason) {
        boolean stillAvailable = decision.onlineCandidates().stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(targetServer));
        if (!stillAvailable) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", config.language().text("reasons.selection_unavailable"),
                            "player", player.getUsername()), player));
            return;
        }

        boolean sameServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetServer))
                .orElse(false);
        if (sameServer && !config.commands().reconnectIfSameServer()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                    Map.of("server", targetServer, "player", player.getUsername()), player));
            return;
        }

        Optional<RegisteredServer> target = plugin.server().getServer(targetServer);
        if (target.isEmpty()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", config.language().text("reasons.selection_unregistered"),
                            "player", player.getUsername()), player));
            return;
        }

        RouteDecision selectionDecision = withTargetFirst(decision, targetServer, reason);
        player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                Map.of("server", targetServer, "player", player.getUsername()), player));
        connectWithRetry(plugin, player, config, target.get(), selectionDecision, reason);
    }

    public static void connectWithRetry(VelocityNavigator plugin, Player player, Config config, RegisteredServer target,
                                 RouteDecision decision, String initialReason) {
        connectWithRetry(plugin, player, config, target, decision, 0, java.util.concurrent.ConcurrentHashMap.newKeySet(), initialReason);
    }

    private static void connectWithRetry(VelocityNavigator plugin, Player player, Config config, RegisteredServer target,
                                         RouteDecision decision, int attempt, Set<String> triedServers, String initialReason) {
        int maxRetries = config.routing().maxRetries();
        triedServers.add(target.getServerInfo().getName().toLowerCase(Locale.ROOT));

        player.createConnectionRequest(target).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                String reason = attempt > 0 ? "retry" : initialReason;
                String targetName = target.getServerInfo().getName();
                plugin.routingStats().recordRedirect(reason, targetName);
                if (plugin.rateTracker() != null) {
                    plugin.rateTracker().recordConnection(targetName);
                }
                if (plugin.affinityService() != null) {
                    plugin.affinityService().setAffinity(affinityUuid(player, plugin, config), targetName);
                }
                return;
            }

            if (attempt < maxRetries) {
                String nextServer = pickNextCandidate(decision, triedServers);
                if (nextServer != null) {
                    Optional<RegisteredServer> nextTarget = plugin.server().getServer(nextServer);
                    if (nextTarget.isPresent()) {
                        player.sendMessage(MessageFormatter.render(config.messages().retrying(),
                                Map.of("attempt", String.valueOf(attempt + 1),
                                        "max", String.valueOf(maxRetries),
                                        "player", player.getUsername(),
                                        "server", nextServer), player));
                        long delay = retryDelayMs(attempt);
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                                .execute(() -> connectWithRetry(plugin, player, config, nextTarget.get(), decision, attempt + 1, triedServers, initialReason));
                        return;
                    }
                }
            }

            plugin.cooldowns().clear(player.getUniqueId());
            Component reason = result.getReasonComponent()
                    .orElse(MessageFormatter.render(config.language().text("messages.unknown_error"), player));
            if (attempt == 0) {
                player.sendMessage(MessageFormatter.render(
                        config.language().text("messages.connection_failed_prefix"), player).append(reason));
            } else {
                player.sendMessage(MessageFormatter.render(
                        config.language().text("messages.connection_failed_attempts"),
                        Map.of("attempts", String.valueOf(attempt + 1)), player).append(Component.text(" (")).append(reason).append(Component.text(")")));
            }
        }).exceptionally(throwable -> {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.language().text("messages.connection_error"), player));
            plugin.logger().error("[VelocityNavigator] connectWithRetry failed for {}", player.getUsername(), throwable);
            return null;
        });
    }

    private static List<String> orderedWithTargetFirst(List<String> candidates, String targetServer) {
        List<String> ordered = new ArrayList<>();
        ordered.add(targetServer);
        if (candidates != null) {
            for (String candidate : candidates) {
                if (!candidate.equalsIgnoreCase(targetServer)) {
                    ordered.add(candidate);
                }
            }
        }
        return ordered;
    }

    private static String pickNextCandidate(RouteDecision decision, Set<String> triedServers) {
        List<String> candidates = decision.orderedCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (String candidate : candidates) {
            if (!triedServers.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }

        return null;
    }

    private static UUID affinityUuid(Player player, VelocityNavigator plugin, Config config) {
        if (plugin.bedrockHandler() != null && plugin.bedrockHandler().isBedrockSupported(config)
                && config.bedrock().affinityUseJavaUuid()
                && plugin.bedrockHandler().isBedrockPlayer(player, config)) {
            UUID javaUuid = FloodgateIntegration.getJavaUUID(player);
            if (javaUuid != null) {
                return javaUuid;
            }
        }
        return player.getUniqueId();
    }

    public static long retryDelayMs(int attempt) {
        long base = RETRY_BASE_DELAY_MS * (1L << Math.min(attempt, 6));
        long capped = Math.min(base, RETRY_MAX_DELAY_MS);
        long jitter = ThreadLocalRandom.current().nextLong(capped / 2 + 1);
        return capped + jitter;
    }
}
