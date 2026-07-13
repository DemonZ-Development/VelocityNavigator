/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class MenuServerInfoResolver {

    private MenuServerInfoResolver() {
    }

    static List<MenuServerInfo> resolve(VelocityNavigator plugin, Config config, List<String> candidates) {
        return resolve(plugin, config, candidates, candidates);
    }

    static List<MenuServerInfo> resolve(VelocityNavigator plugin, Config config, List<String> candidates, List<String> onlineCandidates) {
        Set<String> online = onlineCandidates == null ? Set.of() : onlineCandidates.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<MenuServerInfo> result = new ArrayList<>();
        for (String serverName : candidates) {
            String normalized = serverName.toLowerCase(Locale.ROOT);
            boolean available = online.contains(normalized);
            boolean drained = plugin.drainService().isDrained(normalized);
            CircuitBreaker.State circuitState = plugin.circuitBreaker() == null
                    ? CircuitBreaker.State.CLOSED
                    : plugin.circuitBreaker().getState(normalized);

            String status;
            String statusColor;
            if (!available) {
                status = config.language().text("menus.status_offline");
                statusColor = config.messages().dashboardOffline();
            } else if (circuitState == CircuitBreaker.State.OPEN) {
                status = config.language().text("menus.status_open");
                statusColor = config.messages().dashboardOpen();
            } else if (drained) {
                status = config.language().text("menus.status_draining");
                statusColor = config.messages().dashboardDraining();
            } else {
                status = config.language().text("menus.status_healthy");
                statusColor = config.messages().dashboardHealthy();
            }

            Optional<RegisteredServer> registered = plugin.server().getServer(serverName);
            int players = registered.map(value -> value.getPlayersConnected().size()).orElse(0);
            int maxPlayers = findMaxPlayers(config, serverName);
            String maxPlayersText = maxPlayers == Config.LobbyEntry.UNCAPPED ? "-" : String.valueOf(maxPlayers);
            long latency = plugin.healthService() == null
                    ? -1L
                    : plugin.healthService().getLatencies().getOrDefault(normalized, -1L);

            result.add(new MenuServerInfo(
                    serverName,
                    players,
                    maxPlayersText,
                    status,
                    statusColor,
                    latency < 0 ? "?" : String.valueOf(latency),
                    available
            ));
        }
        return List.copyOf(result);
    }

    private static int findMaxPlayers(Config config, String serverName) {
        for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
            if (entry.server().equalsIgnoreCase(serverName)) {
                return entry.maxPlayers();
            }
        }
        for (Config.GroupConfig group : config.routing().contextual().groups().values()) {
            for (Config.LobbyEntry entry : group.servers()) {
                if (entry.server().equalsIgnoreCase(serverName)) {
                    return entry.maxPlayers();
                }
            }
        }
        return Config.LobbyEntry.UNCAPPED;
    }
}
