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

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerHealthService {

    private final ProxyServer server;
    private final Logger logger;
    private final HealthCheckCache cache = new HealthCheckCache();
    private final Clock clock;
    private CircuitBreaker circuitBreaker;
    private ServerLoadTracker loadTracker;

    private final ConcurrentMap<String, CompletableFuture<ServerStatus>> activePings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> latencies = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> backendStates = new ConcurrentHashMap<>();
    private volatile AdvancedConfig.BackendStates backendStateSettings = AdvancedConfig.defaults().backendStates();
    private static final Pattern STATE_MARKER = Pattern.compile("\\[STATE:([A-Za-z0-9_-]+)]", Pattern.CASE_INSENSITIVE);

    public Map<String, Long> getLatencies() {
        return java.util.Collections.unmodifiableMap(latencies);
    }

    public ServerHealthService(ProxyServer server, Logger logger) {
        this(server, logger, Clock.systemUTC());
    }

    ServerHealthService(ProxyServer server, Logger logger, Clock clock) {
        this.server = server;
        this.logger = logger;
        this.clock = clock;
    }

    public void setCircuitBreaker(CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public void setLoadTracker(ServerLoadTracker loadTracker) {
        this.loadTracker = loadTracker;
    }

    public void setBackendStateSettings(AdvancedConfig.BackendStates settings) {
        backendStateSettings = settings == null ? AdvancedConfig.defaults().backendStates() : settings;
    }

    public Map<String, String> getBackendStates() {
        return Map.copyOf(backendStates);
    }

    public boolean isRoutingStateAllowed(String serverName) {
        AdvancedConfig.BackendStates settings = backendStateSettings;
        if (!settings.enabled()) return true;
        String state = backendStates.get(serverName.toLowerCase(Locale.ROOT));
        return state == null ? settings.allowUnknown() : settings.allowed().contains(state);
    }

    public void mergeRemoteHealth(String serverName, boolean online, long checkedAtEpochMilli, long latency, String state) {
        String normalized = serverName.toLowerCase(Locale.ROOT);
        Instant checkedAt = Instant.ofEpochMilli(checkedAtEpochMilli);
        HealthCheckCache.Entry current = cache.getCached(normalized);
        if (current == null || current.checkedAt().isBefore(checkedAt)) cache.put(normalized, online, checkedAt);
        if (latency >= 0) latencies.put(normalized, latency);
        if (state != null && !state.isBlank()) backendStates.put(normalized, state.toUpperCase(Locale.ROOT));
    }

    public Map<String, HealthSnapshot> getHealthSnapshots() {
        Map<String, HealthSnapshot> snapshots = new LinkedHashMap<>();
        cache.entries().forEach((serverName, entry) -> snapshots.put(serverName, new HealthSnapshot(entry.online(), entry.checkedAt().toEpochMilli(), latencies.getOrDefault(serverName, -1L), backendStates.getOrDefault(serverName, ""))));
        return Map.copyOf(snapshots);
    }

    public Map<String, Integer> getCachedOnlineServers() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, HealthCheckCache.Entry> entry : cache.entries().entrySet()) {
            String serverName = entry.getKey();
            HealthCheckCache.Entry cached = entry.getValue();
            if (cached == null || !cached.online()) {
                continue;
            }
            Optional<RegisteredServer> registered = server.getServer(serverName);
            if (registered.isEmpty()) {
                continue;
            }
            int playerCount = registered.get().getPlayersConnected().size();
            result.put(serverName, playerCount);
            if (loadTracker != null) {
                loadTracker.update(serverName, playerCount);
            }
        }
        return result;
    }

    public Map<String, Integer> getRegisteredOnlineServers(Collection<String> serverNames) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (serverNames == null) {
            return result;
        }
        for (String serverName : serverNames) {
            if (serverName == null || serverName.isBlank()) {
                continue;
            }
            Optional<RegisteredServer> registered = server.getServer(serverName);
            if (registered.isEmpty()) {
                continue;
            }
            int playerCount = registered.get().getPlayersConnected().size();
            String normalized = serverName.toLowerCase(Locale.ROOT);
            result.put(normalized, playerCount);
            if (loadTracker != null) {
                loadTracker.update(normalized, playerCount);
            }
        }
        return result;
    }

    public CompletableFuture<Map<String, ServerStatus>> inspectServers(Collection<String> serverNames, Config.HealthChecks settings) {
        Map<String, CompletableFuture<ServerStatus>> futures = new LinkedHashMap<>();
        for (String serverName : serverNames) {
            if (serverName == null || serverName.isBlank() || futures.containsKey(serverName)) {
                continue;
            }
            futures.put(serverName, inspectServer(serverName, settings));
        }
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Map<String, ServerStatus> results = new LinkedHashMap<>();
                    for (Map.Entry<String, CompletableFuture<ServerStatus>> entry : futures.entrySet()) {
                        results.put(entry.getKey(), entry.getValue().join());
                    }
                    return results;
                });
    }

    public CompletableFuture<ServerStatus> inspectServer(String serverName, Config.HealthChecks settings) {
        String normalized = serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
        Optional<RegisteredServer> optionalServer = server.getServer(normalized);
        if (optionalServer.isEmpty()) {
            return CompletableFuture.completedFuture(new ServerStatus(normalized, false, false, false, null, 0));
        }

        RegisteredServer registeredServer = optionalServer.get();
        int players = registeredServer.getPlayersConnected().size();
        Instant now = clock.instant();
        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(new ServerStatus(normalized, true, true, false, now, players));
        }

        HealthCheckCache.Entry cachedEntry = cache.getIfFresh(normalized, now, Duration.ofSeconds(settings.cacheSeconds()));
        if (cachedEntry != null) {
            return CompletableFuture.completedFuture(new ServerStatus(normalized, true, cachedEntry.online(), true, cachedEntry.checkedAt(), players));
        }

        long startTime = System.currentTimeMillis();

        return activePings.computeIfAbsent(normalized, name -> {
            CompletableFuture<ServerStatus> pingFuture = registeredServer.ping()
                    .orTimeout(settings.timeoutMs(), TimeUnit.MILLISECONDS)
                    .thenApply(ping -> {
                        long latency = System.currentTimeMillis() - startTime;
                        latencies.put(name, latency);
                        String motd = PlainTextComponentSerializer.plainText().serialize(ping.getDescriptionComponent());
                        Matcher marker = STATE_MARKER.matcher(motd);
                        if (marker.find()) backendStates.put(name, marker.group(1).toUpperCase(Locale.ROOT));
                        else backendStates.remove(name);
                        Instant checkedAt = clock.instant();
                        cache.put(name, true, checkedAt);
                        int currentPlayers = registeredServer.getPlayersConnected().size();
                        if (circuitBreaker != null) {
                            circuitBreaker.recordSuccess(name);
                        }
                        if (loadTracker != null) {
                            loadTracker.update(name, currentPlayers);
                        }
                        return new ServerStatus(name, true, true, false, checkedAt, currentPlayers);
                    })
                    .exceptionally(throwable -> {
                        latencies.remove(name);
                        Instant checkedAt = clock.instant();
                        cache.put(name, false, checkedAt);
                        if (circuitBreaker != null) {
                            circuitBreaker.recordFailure(name);
                        }
                        if (loadTracker != null) {
                            loadTracker.update(name, 0);
                        }
                        logger.debug("VelocityNavigator health check marked {} offline: {}", name, throwable.getMessage());
                        return new ServerStatus(name, true, false, false, checkedAt, registeredServer.getPlayersConnected().size());
                    });

            pingFuture.whenComplete((result, error) -> activePings.remove(name));
            return pingFuture;
        });
    }

    public void clearCache() {
        cache.clear();
        activePings.clear();
        backendStates.clear();
    }

    public void purgeExpiredCache(Duration ttl) {
        cache.purgeExpired(ttl);
    }

    public int cacheSize() { return cache.entries().size(); }
    public int activePingCount() { return activePings.size(); }
    public int latencyCount() { return latencies.size(); }

    public record ServerStatus(
            String serverName,
            boolean exists,
            boolean online,
            boolean cached,
            Instant checkedAt,
            int playersConnected
    ) {
    }

    public record HealthSnapshot(boolean online, long checkedAtEpochMilli, long latency, String state) {
    }
}
