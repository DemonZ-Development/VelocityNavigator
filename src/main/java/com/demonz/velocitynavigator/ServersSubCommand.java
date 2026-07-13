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

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class ServersSubCommand {

    private ServersSubCommand() {
    }

    public static java.util.concurrent.CompletableFuture<Void> execute(CommandSource source, String[] args, VelocityNavigator plugin) {
        Config config = plugin.config();
        Set<String> uniqueLobbies = new LinkedHashSet<>();
        if (config.routing().defaultLobbies() != null) {
            for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
                uniqueLobbies.add(entry.server());
            }
        }
        if (config.routing().contextual() != null && config.routing().contextual().groups() != null) {
            for (Config.GroupConfig groupConfig : config.routing().contextual().groups().values()) {
                if (groupConfig.servers() != null) {
                    for (Config.LobbyEntry entry : groupConfig.servers()) {
                        uniqueLobbies.add(entry.server());
                    }
                }
            }
        }
        uniqueLobbies.addAll(plugin.dynamicLobbyNames());
        List<String> sortedLobbies = new ArrayList<>(uniqueLobbies);
        Collections.sort(sortedLobbies);

        if (sortedLobbies.isEmpty()) {
            source.sendMessage(MessageFormatter.render("<red>No lobby servers configured in navigator.toml.</red>"));
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) {
                    page = 1;
                }
            } catch (NumberFormatException e) {
                source.sendMessage(Component.text("Invalid page number. Showing page 1.", net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            }
        }

        final int finalPage = page;
        source.sendMessage(MessageFormatter.render("<gray>Retrieving lobby server statuses...</gray>"));
        return plugin.healthService().inspectServers(sortedLobbies, config.healthChecks())
                .thenAccept(statusMap -> {
                    int totalLobbies = sortedLobbies.size();
                    int pageSize = 8;
                    int totalPages = (int) Math.ceil((double) totalLobbies / (double) pageSize);
                    int currentPage = Math.min(finalPage, totalPages);
                    if (currentPage < 1) {
                        currentPage = 1;
                    }
                    int startIndex = (currentPage - 1) * pageSize;
                    int endIndex = Math.min(startIndex + pageSize, totalLobbies);

                    List<String> pageLobbies = sortedLobbies.subList(startIndex, endIndex);

                    source.sendMessage(MessageFormatter.render(" "));
                    source.sendMessage(MessageFormatter.render(
                            "<gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Lobby Status</bold> <gray>(Page " + currentPage + "/" + totalPages + ")</gray></gradient>"
                    ));
                    source.sendMessage(MessageFormatter.render("<gray>--------------------------------------------------</gray>"));

                    for (String lobbyName : pageLobbies) {
                        ServerHealthService.ServerStatus status = statusMap.get(lobbyName);
                        boolean online = status != null && status.online();
                        boolean isDrained = plugin.drainService().isDrained(lobbyName.toLowerCase(Locale.ROOT));

                        CircuitBreaker.State cbStateEnum = CircuitBreaker.State.CLOSED;
                        if (plugin.circuitBreaker() != null) {
                            cbStateEnum = plugin.circuitBreaker().getState(lobbyName.toLowerCase(Locale.ROOT));
                        }
                        boolean isCbOpen = cbStateEnum == CircuitBreaker.State.OPEN;
                        String cbState = cbStateEnum.name();

                        String statusText;
                        String colorTag;

                        if (!online) {
                            statusText = "OFFLINE";
                            colorTag = config.messages().dashboardOffline();
                        } else if (isCbOpen) {
                            statusText = "CB_OPEN";
                            colorTag = config.messages().dashboardOpen();
                        } else if (isDrained) {
                            statusText = "DRAINED";
                            colorTag = config.messages().dashboardDraining();
                        } else {
                            statusText = "HEALTHY";
                            colorTag = config.messages().dashboardHealthy();
                        }

                        int currentPlayers = 0;
                        String maxPlayersText = "-";
                        Optional<RegisteredServer> registered = plugin.server().getServer(lobbyName);
                        if (registered.isPresent()) {
                            currentPlayers = registered.get().getPlayersConnected().size();
                        }

                        Optional<Config.LobbyEntry> lobbyEntry = plugin.lobbyEntry(lobbyName);
                        if (lobbyEntry.isPresent() && lobbyEntry.get().maxPlayers() != Config.LobbyEntry.UNCAPPED) {
                            maxPlayersText = String.valueOf(lobbyEntry.get().maxPlayers());
                        }

                        source.sendMessage(MessageFormatter.render(
                                "  <gray>•</gray> <white><bold>" + lobbyName + "</bold></white> <gray>|</gray> " +
                                "<gray>Players:</gray> <white>" + currentPlayers + "/" + maxPlayersText + "</white> <gray>|</gray> " +
                                "<gray>CB:</gray> <white>" + cbState + "</white> <gray>|</gray> " +
                                "<gray>Status:</gray> " + colorTag + statusText
                        ));
                    }
                    source.sendMessage(MessageFormatter.render("<gray>--------------------------------------------------</gray>"));
                    if (currentPage < totalPages) {
                        source.sendMessage(MessageFormatter.render("<gray>Use </gray><yellow>/vn servers " + (currentPage + 1) + "</yellow><gray> to view the next page.</gray>"));
                    }
                    source.sendMessage(MessageFormatter.render(" "));
                })
                .exceptionally(throwable -> {
                    source.sendMessage(Component.text("Failed to query lobby statuses: " + throwable.getMessage(), net.kyori.adventure.text.format.NamedTextColor.RED));
                    plugin.logger().error("Failed to query lobby statuses", throwable);
                    return null;
                });
    }
}
