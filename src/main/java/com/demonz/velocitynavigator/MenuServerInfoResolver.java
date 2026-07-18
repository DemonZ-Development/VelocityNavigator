/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        ServerHealthService healthService = plugin.healthService();
        Map<String, ServerHealthService.HealthSnapshot> snapshots = healthService == null
                ? Map.of() : healthService.getHealthSnapshots();
        Map<String, String> backendStates = healthService == null
                ? Map.of() : healthService.getBackendStates();
        List<MenuServerInfo> result = new ArrayList<>();
        for (String serverName : candidates) {
            String normalized = serverName.toLowerCase(Locale.ROOT);
            boolean available = online.contains(normalized);
            boolean drained = plugin.drainService().isDrained(normalized);
            CircuitBreaker.State circuitState = plugin.circuitBreaker() == null
                    ? CircuitBreaker.State.CLOSED
                    : plugin.circuitBreaker().getState(normalized);

            Optional<RegisteredServer> registered = plugin.server().getServer(serverName);
            int players = registered.map(value -> value.getPlayersConnected().size()).orElse(0);
            int maxPlayers = plugin.lobbyEntry(serverName)
                    .map(Config.LobbyEntry::maxPlayers)
                    .orElse(Config.LobbyEntry.UNCAPPED);
            String maxPlayersText = maxPlayers == Config.LobbyEntry.UNCAPPED ? "-" : String.valueOf(maxPlayers);
            ServerHealthService.HealthSnapshot snapshot = snapshots.get(normalized);
            String backendState = snapshot != null && snapshot.state() != null && !snapshot.state().isBlank()
                    ? snapshot.state()
                    : backendStates.getOrDefault(normalized, "");
            MenuServerState menuState = resolveState(
                    available,
                    snapshot == null ? null : snapshot.online(),
                    drained,
                    circuitState == CircuitBreaker.State.CLOSED,
                    maxPlayers != Config.LobbyEntry.UNCAPPED && players >= maxPlayers,
                    backendState,
                    healthService == null || healthService.isRoutingStateAllowed(serverName)
            );
            String status = config.language().text(menuState.languageKey());
            String statusColor = switch (menuState) {
                case HEALTHY -> config.messages().dashboardHealthy();
                case DRAINING -> config.messages().dashboardDraining();
                case OFFLINE -> config.messages().dashboardOffline();
                case FULL -> "<red>";
                case IN_GAME -> "<light_purple>";
            };
            long latency = healthService == null
                    ? -1L
                    : healthService.getLatencies().getOrDefault(normalized, -1L);

            result.add(new MenuServerInfo(
                    serverName,
                    plugin.guiConfig().displayName(serverName),
                    plugin.guiConfig().description(serverName),
                    players,
                    maxPlayersText,
                    status,
                    statusColor,
                    latency < 0 ? "?" : String.valueOf(latency),
                    available,
                    menuState
            ));
        }
        return List.copyOf(result);
    }

    static MenuServerState resolveState(boolean available, Boolean healthOnline, boolean drained,
                                        boolean circuitHealthy, boolean full, String backendState) {
        return resolveState(available, healthOnline, drained, circuitHealthy, full, backendState, true);
    }

    static MenuServerState resolveState(boolean available, Boolean healthOnline, boolean drained,
                                        boolean circuitHealthy, boolean full, String backendState,
                                        boolean backendStateAllowed) {
        if (Boolean.FALSE.equals(healthOnline) || !circuitHealthy) {
            return MenuServerState.OFFLINE;
        }
        if (drained) {
            return MenuServerState.DRAINING;
        }
        if (backendState != null && "IN_GAME".equalsIgnoreCase(backendState.trim())) {
            return MenuServerState.IN_GAME;
        }
        if (!backendStateAllowed) {
            return MenuServerState.OFFLINE;
        }
        if (full) {
            return MenuServerState.FULL;
        }
        if (available || Boolean.TRUE.equals(healthOnline)) {
            return MenuServerState.HEALTHY;
        }
        return MenuServerState.OFFLINE;
    }

}
