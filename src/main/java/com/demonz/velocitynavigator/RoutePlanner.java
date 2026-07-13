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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RoutePlanner {

    private final RouteSelectionStrategy selectionStrategy;
    private volatile DrainService drainService;
    private volatile CircuitBreaker circuitBreaker;
    private volatile ServerLoadTracker loadTracker;
    private volatile ConsistentHashRing hashRing;
    private volatile PlayerAffinityService affinityService;
    private volatile ConnectionRateTracker rateTracker;
    private volatile ServerHealthService healthService;
    private final ConcurrentMap<String, DynamicLobby> dynamicLobbies = new ConcurrentHashMap<>();

    public RoutePlanner(RouteSelectionStrategy selectionStrategy) {
        this.selectionStrategy = Objects.requireNonNull(selectionStrategy, "selectionStrategy");
    }

    public void setHealthService(ServerHealthService healthService) {
        this.healthService = healthService;
    }

    public void setDrainService(DrainService drainService) {
        this.drainService = drainService;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void setLoadTracker(ServerLoadTracker loadTracker) {
        this.loadTracker = loadTracker;
    }

    public void setHashRing(ConsistentHashRing hashRing) {
        this.hashRing = hashRing;
    }

    public void setAffinityService(PlayerAffinityService affinityService) {
        this.affinityService = affinityService;
    }

    public void setRateTracker(ConnectionRateTracker rateTracker) {
        this.rateTracker = rateTracker;
    }

    public void registerDynamicServer(String name, String group, int maxPlayers, int weight) {
        if (name == null || name.isBlank()) return;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        String normalizedGroup = group == null || group.isBlank() ? "default" : group.trim().toLowerCase(Locale.ROOT);
        dynamicLobbies.put(normalized, new DynamicLobby(new Config.LobbyEntry(normalized, maxPlayers, weight), normalizedGroup));
    }

    public void unregisterDynamicServer(String name) {
        if (name != null) dynamicLobbies.remove(name.toLowerCase(Locale.ROOT));
    }

    public Optional<Config.LobbyEntry> dynamicServer(String name) {
        if (name == null) return Optional.empty();
        DynamicLobby lobby = dynamicLobbies.get(name.toLowerCase(Locale.ROOT));
        return lobby == null ? Optional.empty() : Optional.of(lobby.entry());
    }

    public Set<String> dynamicServerNames() {
        return Set.copyOf(dynamicLobbies.keySet());
    }

    public RouteDecision plan(String sourceServer, Config config, Map<String, Integer> onlineServers) {
        return plan(sourceServer, config, onlineServers, null);
    }

    public RouteDecision plan(String sourceServer, Config config, Map<String, Integer> onlineServers, UUID playerId) {
        String normalizedSource = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        Map<String, Integer> online = onlineServers == null ? Map.of() : toLowerCaseKeys(onlineServers);
        Config.Contextual contextual = config.routing().contextual();

        String requestedGroup = "default";
        List<Config.LobbyEntry> requestedEntries = config.routing().defaultLobbies();
        Config.SelectionMode groupMode = null;
        boolean contextualMatch = false;
        String reason = "";

        if (contextual.enabled() && !normalizedSource.isBlank()) {
            String mappedGroup = contextual.sources().get(normalizedSource);
            if (mappedGroup != null) {
                Config.GroupConfig groupConfig = contextual.groups().get(mappedGroup);
                requestedGroup = mappedGroup;
                if (groupConfig != null) {
                    requestedEntries = groupConfig.servers();
                    groupMode = groupConfig.mode();
                } else {
                    requestedEntries = List.of();
                }
                contextualMatch = true;
                if (requestedEntries.isEmpty()) {
                    reason = "Contextual group '" + mappedGroup + "' has no configured lobbies.";
                }
            } else {
                reason = "No contextual mapping exists for '" + normalizedSource + "'.";
            }
        }

        requestedEntries = withDynamic(requestedEntries, requestedGroup);
        String usedGroup = requestedGroup;
        List<Config.LobbyEntry> configuredEntries = List.copyOf(requestedEntries);
        Config.SelectionMode effectiveMode = groupMode != null ? groupMode : config.routing().selectionMode();
        List<String> onlineCandidates = filterOnlineCandidates(requestedEntries, online);
        boolean fallbackToDefault = false;

        if (contextualMatch && onlineCandidates.isEmpty() && contextual.fallbackToDefault()) {
            List<String> chain = contextual.fallbackChain().getOrDefault(requestedGroup, List.of());
            for (String fallbackGroup : chain) {
                Config.GroupConfig fallbackConfig = contextual.groups().get(fallbackGroup);
                if (fallbackConfig != null) {
                    List<Config.LobbyEntry> fallbackEntries = withDynamic(fallbackConfig.servers(), fallbackGroup);
                    List<String> fallbackOnline = filterOnlineCandidates(fallbackEntries, online);
                    if (!fallbackOnline.isEmpty()) {
                        configuredEntries = List.copyOf(fallbackEntries);
                        onlineCandidates = fallbackOnline;
                        usedGroup = fallbackGroup;
                        effectiveMode = fallbackConfig.mode() != null ? fallbackConfig.mode() : config.routing().selectionMode();
                        fallbackToDefault = true;
                        reason = "No online servers in contextual group '" + requestedGroup + "'; fell back to '" + fallbackGroup + "'.";
                        break;
                    }
                }
            }

            if (onlineCandidates.isEmpty()) {
                configuredEntries = withDynamic(config.routing().defaultLobbies(), "default");
                onlineCandidates = filterOnlineCandidates(configuredEntries, online);
                usedGroup = "default";
                effectiveMode = config.routing().selectionMode();
                fallbackToDefault = true;
                if (reason.isBlank()) {
                    reason = "No online servers were available in contextual group '" + requestedGroup + "'.";
                }
            }
        }

        List<String> selectableCandidates = new ArrayList<>(onlineCandidates);
        if (config.routing().cycleWhenPossible() && !normalizedSource.isBlank() && selectableCandidates.size() > 1) {
            selectableCandidates.remove(normalizedSource);
        }

        if (configuredEntries.isEmpty()) {
            Optional<String> fallbackServer = selectableFallbackServer(config, online);
            if (fallbackServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        fallbackServer.get(),
                        fallbackToDefault,
                        "Fell back to fallback server: " + fallbackServer.get(),
                        effectiveMode,
                        selectableCandidates
                );
            }
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    lobbyEntryNames(configuredEntries),
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    reason.isBlank() ? (config.lobbyFallback() != null ? config.lobbyFallback().noServerMessage() : "No configured lobbies were available for group '" + usedGroup + "'.") : reason,
                    effectiveMode
            );
        }

        if (selectableCandidates.isEmpty()) {
            Optional<String> fallbackServer = selectableFallbackServer(config, online);
            if (fallbackServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        fallbackServer.get(),
                        fallbackToDefault,
                        "Fell back to fallback server: " + fallbackServer.get(),
                        effectiveMode,
                        selectableCandidates
                );
            }
            String finalReason = reason;
            if (finalReason.isBlank()) {
                finalReason = config.lobbyFallback() != null ? config.lobbyFallback().noServerMessage() : "No online lobbies were available for group '" + usedGroup + "'.";
            }
            return new RouteDecision(
                    normalizedSource,
                    requestedGroup,
                    usedGroup,
                    lobbyEntryNames(configuredEntries),
                    onlineCandidates,
                    null,
                    fallbackToDefault,
                    finalReason,
                    effectiveMode
            );
        }

        if (playerId != null && affinityService != null && effectiveMode != Config.SelectionMode.CONSISTENT_HASH) {
            Optional<String> stickServer = affinityService.shouldStick(playerId, selectableCandidates);
            if (stickServer.isPresent()) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        stickServer.get(),
                        fallbackToDefault,
                        "affinity",
                        effectiveMode,
                        selectableCandidates
                );
            }
        }

        if (effectiveMode == Config.SelectionMode.CONSISTENT_HASH && playerId != null && hashRing != null) {
            hashRing.updateRing(usedGroup, selectableCandidates);
            Optional<String> selected = selectionStrategy.selectConsistentHash(hashRing, usedGroup, playerId.toString());
            if (selected.isPresent() && selectableCandidates.contains(selected.get())) {
                return new RouteDecision(
                        normalizedSource,
                        requestedGroup,
                        usedGroup,
                        lobbyEntryNames(configuredEntries),
                        onlineCandidates,
                        selected.get(),
                        fallbackToDefault,
                        "consistent_hash",
                        effectiveMode,
                        hashRing.getServerOrder(usedGroup, playerId.toString())
                );
            }
        }

        Map<String, Config.LobbyEntry> entryByName = new LinkedHashMap<>();
        for (Config.LobbyEntry entry : configuredEntries) {
            entryByName.putIfAbsent(entry.server().toLowerCase(Locale.ROOT), entry);
        }
        List<ServerCandidate> candidates = selectableCandidates.stream()
                .map(name -> buildCandidate(name, online.getOrDefault(name, 0), entryByName))
                .toList();
        Config.SelectionMode selectMode = effectiveMode == Config.SelectionMode.CONSISTENT_HASH
                ? Config.SelectionMode.LEAST_PLAYERS
                : effectiveMode;
        Optional<ServerCandidate> selected = selectionStrategy.select(candidates, selectMode, usedGroup);
        String finalReason = fallbackToDefault ? reason : selectMode.configValue();
        if (effectiveMode == Config.SelectionMode.CONSISTENT_HASH) {
            finalReason = "Consistent hash selection was unavailable or failed; fell back to LEAST_PLAYERS.";
        }
        return new RouteDecision(
                normalizedSource,
                requestedGroup,
                usedGroup,
                lobbyEntryNames(configuredEntries),
                onlineCandidates,
                selected.map(ServerCandidate::name).orElse(null),
                fallbackToDefault,
                finalReason,
                selectMode,
                selectableCandidates
        );
    }

    public Set<String> inspectionTargets(String sourceServer, Config config) {
        Set<String> targets = new LinkedHashSet<>();
        for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
            targets.add(entry.server());
        }
        if (config.lobbyFallback() != null
                && "fallback_server".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())
                && !config.lobbyFallback().fallbackServer().isBlank()) {
            targets.add(config.lobbyFallback().fallbackServer());
        }
        Config.Contextual contextual = config.routing().contextual();
        String normalized = sourceServer == null ? "" : sourceServer.toLowerCase(Locale.ROOT);
        if (contextual.enabled() && !normalized.isBlank()) {
            String group = contextual.sources().get(normalized);
            if (group != null) {
                Config.GroupConfig groupConfig = contextual.groups().get(group);
                if (groupConfig != null) {
                    for (Config.LobbyEntry entry : groupConfig.servers()) {
                        targets.add(entry.server());
                    }
                }
            }
        }
        dynamicLobbies.values().forEach(value -> targets.add(value.entry().server()));
        return targets;
    }

    private List<Config.LobbyEntry> withDynamic(List<Config.LobbyEntry> configured, String group) {
        Map<String, Config.LobbyEntry> merged = new LinkedHashMap<>();
        if (configured != null) configured.forEach(entry -> merged.put(entry.server().toLowerCase(Locale.ROOT), entry));
        String normalizedGroup = group == null || group.isBlank() ? "default" : group.toLowerCase(Locale.ROOT);
        dynamicLobbies.values().stream().filter(value -> value.group().equals(normalizedGroup)).forEach(value -> merged.put(value.entry().server(), value.entry()));
        return List.copyOf(merged.values());
    }

    private List<String> filterOnlineCandidates(List<Config.LobbyEntry> configuredEntries, Map<String, Integer> onlineServers) {
        List<String> online = new ArrayList<>();
        for (Config.LobbyEntry entry : configuredEntries) {
            String name = entry.server().toLowerCase(Locale.ROOT);
            Integer count = onlineServers.get(name);
            if (count == null) {
                continue;
            }
            if (drainService != null && drainService.isDrained(name)) {
                continue;
            }
            if (circuitBreaker != null && !circuitBreaker.isAvailable(name)) {
                continue;
            }
            if (healthService != null && !healthService.isRoutingStateAllowed(name)) {
                continue;
            }
            if (entry.isFull(count)) {
                continue;
            }
            online.add(entry.server());
        }
        return List.copyOf(online);
    }

    private ServerCandidate buildCandidate(String name, int playerCount, Map<String, Config.LobbyEntry> entryByName) {
        int weight = Config.LobbyEntry.DEFAULT_WEIGHT;
        Config.LobbyEntry entry = entryByName.get(name.toLowerCase(Locale.ROOT));
        if (entry != null) {
            weight = entry.effectiveWeight();
        }
        double emaLoad = playerCount;
        if (loadTracker != null) {
            emaLoad = loadTracker.getEma(name);
        }
        double rateCost = 0.0;
        if (rateTracker != null) {
            rateCost = rateTracker.getRatePerSecond(name);
        }
        double combinedLoad = emaLoad + rateCost;
        long latency = -1L;
        if (healthService != null) {
            Long tracked = healthService.getLatencies().get(name.toLowerCase(Locale.ROOT));
            if (tracked != null) {
                latency = tracked;
            }
        }
        return new ServerCandidate(name, playerCount, weight, combinedLoad, latency);
    }

    private Optional<String> selectableFallbackServer(Config config, Map<String, Integer> onlineServers) {
        if (config.lobbyFallback() == null
                || !"fallback_server".equalsIgnoreCase(config.lobbyFallback().noServerStrategy())
                || config.lobbyFallback().fallbackServer().isBlank()) {
            return Optional.empty();
        }
        String fallbackServer = config.lobbyFallback().fallbackServer();
        String normalized = fallbackServer.toLowerCase(Locale.ROOT);
        if (!onlineServers.containsKey(normalized)) {
            return Optional.empty();
        }
        if (drainService != null && drainService.isDrained(normalized)) {
            return Optional.empty();
        }
        if (circuitBreaker != null && !circuitBreaker.isAvailable(normalized)) {
            return Optional.empty();
        }
        return Optional.of(fallbackServer);
    }

    private List<String> lobbyEntryNames(List<Config.LobbyEntry> entries) {
        return entries.stream().map(Config.LobbyEntry::server).toList();
    }

    private Map<String, Integer> toLowerCaseKeys(Map<String, Integer> original) {
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : original.entrySet()) {
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }

    private record DynamicLobby(Config.LobbyEntry entry, String group) {
    }
}
