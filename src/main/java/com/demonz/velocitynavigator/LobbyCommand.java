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
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.ToIntFunction;

public final class LobbyCommand implements SimpleCommand {

    private final VelocityNavigator plugin;
    private final java.util.concurrent.atomic.AtomicLong degradedCounter = new java.util.concurrent.atomic.AtomicLong(0);

    public LobbyCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        Config config = plugin.config();
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MessageFormatter.render(config.messages().playerOnly()));
            return;
        }

        if (requiresPermission(config.commands().permission()) && !player.hasPermission(config.commands().permission())) {
            player.sendMessage(MessageFormatter.render(config.language().text("messages.permission_denied"), player));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length >= 2 && "connect".equalsIgnoreCase(args[0])) {
            String targetServer = args[1];
            String token = args.length >= 3 ? args[2] : "";
            if (!plugin.consumeMenuToken(player, targetServer, token)) {
                player.sendMessage(MessageFormatter.render(config.language().text("messages.menu_expired"),
                        Map.of("command", config.commands().primary(), "player", player.getUsername()), player));
                return;
            }
            connectFromMenuSelection(player, config, targetServer);
            return;
        }

        if (!hasBypassCooldown(player)) {
            OptionalLong secondsRemaining = plugin.cooldowns().secondsRemaining(player.getUniqueId());
            if (secondsRemaining.isPresent()) {
                player.sendMessage(MessageFormatter.render(config.messages().cooldown(), Map.of("time", String.valueOf(secondsRemaining.getAsLong())), player));
                return;
            }
        }

        plugin.cooldowns().apply(player.getUniqueId(), config.commands().cooldownSeconds());

        plugin.previewRoute(player)
                .thenAccept(decision -> handleDecision(player, config, decision, args))
                .exceptionally(throwable -> {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(MessageFormatter.render(config.language().text("messages.route_unavailable"), player));
                    plugin.logger().error("Failed to resolve /lobby for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    private void connectFromMenuSelection(Player player, Config config, String targetServer) {
        plugin.previewRoute(player)
                .thenAccept(decision -> {
                    boolean stillAvailable = decision.onlineCandidates().stream()
                            .anyMatch(candidate -> candidate.equalsIgnoreCase(targetServer));
                    if (!stillAvailable) {
                        plugin.cooldowns().clear(player.getUniqueId());
                        player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                                Map.of("reason", config.language().text("reasons.selection_unavailable"), "player", player.getUsername()), player));
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
                                Map.of("reason", config.language().text("reasons.selection_unregistered"), "player", player.getUsername()), player));
                        return;
                    }

                    RouteDecision menuDecision = ConnectionWorkflow.withTargetFirst(decision, targetServer, "chat_menu");
                    player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                            Map.of("server", targetServer, "player", player.getUsername()), player));
                    ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), menuDecision, "chat_menu");
                })
                .exceptionally(throwable -> {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(MessageFormatter.render(config.language().text("messages.selection_validation_failed"), player));
                    plugin.logger().error("Failed to validate /lobby menu selection for {}", player.getUsername(), throwable);
                    return null;
                });
    }

    private void handleDecision(Player player, Config config, RouteDecision decision, String[] args) {
        if (plugin.bedrockHandler() != null && plugin.bedrockHandler().isBedrockPlayer(player, config)) {
            if (config.bedrock().useGuiForLobby() && FloodgateIntegration.isAvailable()) {
                BedrockFormService.showLobbySelectionForm(player, plugin, decision);
                return;
            }
        }

        boolean forceMenu = args.length >= 1 && "menu".equalsIgnoreCase(args[0]);
        if (forceMenu || config.routing().useMenuForLobby()) {
            if (config.routing().javaMenuType() == Config.JavaMenuType.INVENTORY) {
                if (JavaInventoryMenuService.showLobbyMenu(player, plugin, decision)) {
                    return;
                }
                if (config.routing().inventoryMenu().fallbackToChat()) {
                    player.sendMessage(MessageFormatter.render(
                            config.language().text("menus.inventory.bridge_unavailable"), player));
                    JavaMenuService.showLobbyMenu(player, plugin, decision);
                } else {
                    plugin.cooldowns().clear(player.getUniqueId());
                    player.sendMessage(MessageFormatter.render(
                            config.language().text("menus.inventory.bridge_required"), player));
                }
                return;
            }
            JavaMenuService.showLobbyMenu(player, plugin, decision);
            return;
        }

        if (!decision.hasSelection()) {
            if (plugin.queueService() != null && plugin.queueService().canQueue(decision, config)) {
                if (plugin.queueService().enqueue(player, decision)) {
                    player.sendMessage(MessageFormatter.render(config.language().text("queue.joined"),
                            Map.of("position", String.valueOf(plugin.queueService().position(player.getUniqueId())), "size", String.valueOf(plugin.queueService().size())), player));
                    return;
                }
                player.sendMessage(MessageFormatter.render(config.language().text("queue.full"), player));
            }
            if (config.degradation().enabled()) {
                Config.SelectionMode degradationMode = Config.SelectionMode.fromString(config.degradation().mode());
                List<String> allLobbies = decision.orderedCandidates();
                if (allLobbies == null || allLobbies.isEmpty()) {
                    allLobbies = decision.onlineCandidates();
                }
                if (allLobbies != null && !allLobbies.isEmpty()) {
                    String degraded = pickDegraded(allLobbies, degradationMode);
                    if (degraded != null) {
                        Optional<RegisteredServer> target = plugin.server().getServer(degraded);
                        if (target.isPresent()) {
                            player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                                    Map.of("server", degraded, "player", player.getUsername()), player));
                            ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), decision, "degradation");
                            return;
                        }
                    }
                }
            }

            plugin.cooldowns().clear(player.getUniqueId());
            String noLobbyMsg = config.messages().noLobbyFound();
            if (config.lobbyFallback() != null && "disconnect".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())) {
                noLobbyMsg = config.lobbyFallback().noServerMessage();
            }
            player.sendMessage(MessageFormatter.render(
                    noLobbyMsg,
                    Map.of(
                            "reason", decision.reason(),
                            "mode", decision.selectionMode().configValue(),
                            "player", player.getUsername()
                    ),
                    player
            ));
            return;
        }

        String targetName = decision.selectedServer();
        boolean sameServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetName))
                .orElse(false);
        if (sameServer && !config.commands().reconnectIfSameServer()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                    Map.of("server", targetName, "player", player.getUsername()), player));
            return;
        }

        Optional<RegisteredServer> target = plugin.server().getServer(targetName);
        if (target.isEmpty()) {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", config.language().text("reasons.selection_unregistered"), "player", player.getUsername()), player));
            return;
        }

        player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                Map.of("server", targetName, "player", player.getUsername()), player));
        ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), decision, decision.reason());
    }

    private String pickDegraded(List<String> candidates, Config.SelectionMode mode) {
        if (candidates.isEmpty()) {
            return null;
        }
        return switch (mode) {
            case RANDOM -> candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
            case ROUND_ROBIN -> {
                long idx = degradedCounter.getAndIncrement();
                yield candidates.get((int) (idx % candidates.size()));
            }
            case LEAST_PLAYERS -> pickLeastPlayers(candidates, this::connectedPlayers);
            case POWER_OF_TWO, LEAST_CONNECTIONS, WEIGHTED_ROUND_ROBIN, CONSISTENT_HASH, LATENCY ->
                candidates.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(candidates.size()));
        };
    }

    static String pickLeastPlayers(List<String> candidates, ToIntFunction<String> playerCount) {
        return candidates.stream()
                .min(Comparator.comparingInt((String name) -> playerCount.applyAsInt(name))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER))
                .orElse(null);
    }

    private int connectedPlayers(String serverName) {
        return plugin.server().getServer(serverName)
                .map(server -> server.getPlayersConnected().size())
                .orElse(Integer.MAX_VALUE);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        Config config = plugin.config();
        if (!requiresPermission(config.commands().permission())) {
            return true;
        }
        return invocation.source().hasPermission(config.commands().permission());
    }

    private boolean hasBypassCooldown(Player player) {
        return player.hasPermission("velocitynavigator.bypass.cooldown")
                || player.hasPermission("velocitynavigator.bypasscooldown");
    }

    private boolean requiresPermission(String permission) {
        return permission != null
                && !permission.isBlank()
                && !"none".equalsIgnoreCase(permission.trim());
    }
}
