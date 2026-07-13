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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PrometheusExporter {

    private final VelocityNavigator plugin;
    private final Logger logger;
    private HttpServer server;
    private volatile String bearerToken = "";

    public PrometheusExporter(VelocityNavigator plugin) {
        this.plugin = plugin;
        this.logger = plugin.logger();
    }

    public synchronized void start(Config.PrometheusSettings settings) {
        if (server != null) {
            stop();
        }
        if (!settings.enabled()) {
            return;
        }
        bearerToken = settings.bearerToken();

        try {
            server = HttpServer.create(new InetSocketAddress(settings.bindHost(), settings.port()), 0);
            server.createContext("/metrics", new MetricsHandler());
            server.setExecutor(null);
            server.start();
            if (!isLoopbackBindHost(settings.bindHost()) && bearerToken.isBlank()) {
                logger.warn("[VelocityNavigator] Prometheus metrics exporter is bound to {} without authentication. Use 127.0.0.1 or configure metrics.prometheus.bearer_token unless this endpoint is firewalled.",
                        settings.bindHost());
            }
            logger.info("[VelocityNavigator] Started Prometheus metrics exporter on {}:{}", settings.bindHost(), settings.port());
        } catch (IOException | RuntimeException e) {
            if (server != null) {
                try {
                    server.stop(0);
                } catch (RuntimeException ignored) {
                }
                server = null;
            }
            logger.error("[VelocityNavigator] Failed to start Prometheus exporter on port {}: {}", settings.port(), e.getMessage());
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(2);
            server = null;
            logger.info("[VelocityNavigator] Stopped Prometheus metrics exporter.");
        }
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!isAuthorized(exchange)) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                exchange.sendResponseHeaders(401, -1);
                return;
            }

            StringBuilder sb = new StringBuilder();
            Config config = plugin.config();
            ProxyServer proxy = plugin.server();

            sb.append("# HELP velocitynavigator_player_joins_total Total player joins to the proxy\n");
            sb.append("# TYPE velocitynavigator_player_joins_total counter\n");
            sb.append("velocitynavigator_player_joins_total ").append(plugin.getPlayerJoins()).append(".0\n");

            sb.append("# HELP velocitynavigator_player_leaves_total Total player leaves from the proxy\n");
            sb.append("# TYPE velocitynavigator_player_leaves_total counter\n");
            sb.append("velocitynavigator_player_leaves_total ").append(plugin.getPlayerLeaves()).append(".0\n");

            RedisSyncService.Status redis = plugin.redisStatus();
            sb.append("# HELP velocitynavigator_party_count Active local parties\n");
            sb.append("# TYPE velocitynavigator_party_count gauge\n");
            sb.append("velocitynavigator_party_count ").append(plugin.partyService().partyCount()).append(".0\n");
            sb.append("# HELP velocitynavigator_queue_size Players in the local capacity queue\n");
            sb.append("# TYPE velocitynavigator_queue_size gauge\n");
            sb.append("velocitynavigator_queue_size ").append(plugin.queueService() == null ? 0 : plugin.queueService().size()).append(".0\n");
            sb.append("# HELP velocitynavigator_redis_connected Redis subscription connection state\n");
            sb.append("# TYPE velocitynavigator_redis_connected gauge\n");
            sb.append("velocitynavigator_redis_connected ").append(redis.connected() ? "1.0" : "0.0").append("\n");
            sb.append("# HELP velocitynavigator_redis_reconnects_total Redis subscription reconnect attempts\n");
            sb.append("# TYPE velocitynavigator_redis_reconnects_total counter\n");
            sb.append("velocitynavigator_redis_reconnects_total ").append(redis.reconnects()).append(".0\n");
            sb.append("# HELP velocitynavigator_redis_rejected_registrations_total Rejected dynamic registration events\n");
            sb.append("# TYPE velocitynavigator_redis_rejected_registrations_total counter\n");
            sb.append("velocitynavigator_redis_rejected_registrations_total ").append(redis.rejectedRegistrations()).append(".0\n");

            sb.append("# HELP velocitynavigator_server_online Health status of tracked lobby servers (1 = online, 0 = offline)\n");
            sb.append("# TYPE velocitynavigator_server_online gauge\n");

            sb.append("# HELP velocitynavigator_server_players Number of players currently connected to the server\n");
            sb.append("# TYPE velocitynavigator_server_players gauge\n");

            Set<String> trackedServers = trackedServers(config);
            Set<String> trackedServerKeys = new LinkedHashSet<>();
            for (String trackedServer : trackedServers) {
                trackedServerKeys.add(trackedServer.toLowerCase(Locale.ROOT));
            }
            Map<String, Integer> cachedOnlineServers = plugin.healthService() == null
                    ? Map.of()
                    : plugin.healthService().getCachedOnlineServers();
            boolean healthFilteringDisabled = plugin.healthService() == null
                    || config == null
                    || !config.healthChecks().enabled();

            for (RegisteredServer regServer : proxy.getAllServers()) {
                String name = regServer.getServerInfo().getName();
                String label = "{server=\"" + escapeLabelValue(name) + "\"}";
                String lowerName = name.toLowerCase(Locale.ROOT);
                int players = regServer.getPlayersConnected().size();
                boolean tracked = trackedServerKeys.contains(lowerName);
                if (tracked) {
                    boolean online = shouldReportServerOnline(healthFilteringDisabled, true, cachedOnlineServers.containsKey(lowerName));
                    sb.append("velocitynavigator_server_online").append(label).append(" ").append(online ? "1.0" : "0.0").append("\n");
                }
                sb.append("velocitynavigator_server_players").append(label).append(" ").append(players).append(".0\n");
            }

            sb.append("# HELP velocitynavigator_server_latency_ms Health check ping latency in milliseconds\n");
            sb.append("# TYPE velocitynavigator_server_latency_ms gauge\n");

            sb.append("# HELP velocitynavigator_server_circuit_breaker Circuit breaker state (0 = CLOSED, 1 = HALF_OPEN, 2 = OPEN)\n");
            sb.append("# TYPE velocitynavigator_server_circuit_breaker gauge\n");

            sb.append("# HELP velocitynavigator_server_drained Whether the lobby server is drained (1 = drained, 0 = active)\n");
            sb.append("# TYPE velocitynavigator_server_drained gauge\n");

            sb.append("# HELP velocitynavigator_routed_connections_total Total connection attempts routed through the plugin per server\n");
            sb.append("# TYPE velocitynavigator_routed_connections_total counter\n");

            Map<String, Long> latencies = plugin.getHealthCheckLatencies();
            Map<String, Long> distribution = plugin.routingStats().getCumulativeDistribution();
            Map<String, CircuitBreaker.State> cbStatuses = plugin.getCircuitBreakerStatuses();

            for (String serverName : trackedServers) {
                String label = "{server=\"" + escapeLabelValue(serverName) + "\"}";
                String lowerName = serverName.toLowerCase(Locale.ROOT);

                long latency = latencies.getOrDefault(lowerName, -1L);
                sb.append("velocitynavigator_server_latency_ms").append(label).append(" ").append(latency).append(".0\n");

                CircuitBreaker.State state = cbStatuses.getOrDefault(serverName,
                        cbStatuses.getOrDefault(lowerName, CircuitBreaker.State.CLOSED));
                int cbValue = switch (state) {
                    case CLOSED -> 0;
                    case HALF_OPEN -> 1;
                    case OPEN -> 2;
                };
                sb.append("velocitynavigator_server_circuit_breaker").append(label).append(" ").append(cbValue).append(".0\n");

                boolean isDrained = plugin.drainService().isDrained(lowerName);
                sb.append("velocitynavigator_server_drained").append(label).append(" ").append(isDrained ? "1.0" : "0.0").append("\n");

                long routed = distribution.getOrDefault(serverName, 0L);
                sb.append("velocitynavigator_routed_connections_total").append(label).append(" ").append(routed).append(".0\n");
            }

            sb.append("# HELP velocitynavigator_redirects_total Total connections redirected/routed by reason and target server\n");
            sb.append("# TYPE velocitynavigator_redirects_total counter\n");
            Map<String, Map<String, Long>> redirects = plugin.routingStats().getCumulativeRedirectCounts();
            for (Map.Entry<String, Map<String, Long>> reasonEntry : redirects.entrySet()) {
                String reason = reasonEntry.getKey();
                for (Map.Entry<String, Long> serverEntry : reasonEntry.getValue().entrySet()) {
                    sb.append("velocitynavigator_redirects_total{reason=\"").append(escapeLabelValue(reason))
                      .append("\",target=\"").append(escapeLabelValue(serverEntry.getKey())).append("\"} ")
                      .append(serverEntry.getValue()).append(".0\n");
                }
            }

            sb.append("# HELP velocitynavigator_circuit_breaker_trips_total Total times circuit breaker tripped per server\n");
            sb.append("# TYPE velocitynavigator_circuit_breaker_trips_total counter\n");
            if (plugin.circuitBreaker() != null) {
                for (Map.Entry<String, Long> entry : plugin.circuitBreaker().getTripCounts().entrySet()) {
                    sb.append("velocitynavigator_circuit_breaker_trips_total{server=\"").append(escapeLabelValue(entry.getKey())).append("\"} ")
                      .append(entry.getValue()).append(".0\n");
                }
            }

            sb.append("# HELP velocitynavigator_fallback_events_total Total fallback events by type\n");
            sb.append("# TYPE velocitynavigator_fallback_events_total counter\n");
            long degradationFallbacks = 0;
            long retryFallbacks = 0;
            long contextualFallbacks = 0;
            for (Map.Entry<String, Map<String, Long>> reasonEntry : redirects.entrySet()) {
                String r = reasonEntry.getKey();
                long totalForReason = reasonEntry.getValue().values().stream().mapToLong(Long::longValue).sum();
                if ("degradation".equalsIgnoreCase(r)) {
                    degradationFallbacks += totalForReason;
                } else if ("retry".equalsIgnoreCase(r)) {
                    retryFallbacks += totalForReason;
                } else if (r.contains("fell back to") || r.contains("fallback")) {
                    contextualFallbacks += totalForReason;
                }
            }
            sb.append("velocitynavigator_fallback_events_total{type=\"degradation\"} ").append(degradationFallbacks).append(".0\n");
            sb.append("velocitynavigator_fallback_events_total{type=\"retry\"} ").append(retryFallbacks).append(".0\n");
            sb.append("velocitynavigator_fallback_events_total{type=\"contextual\"} ").append(contextualFallbacks).append(".0\n");

            sb.append("# HELP velocitynavigator_routing_retries_total Total connection retries across all servers\n");
            sb.append("# TYPE velocitynavigator_routing_retries_total counter\n");
            sb.append("velocitynavigator_routing_retries_total ").append(retryFallbacks).append(".0\n");

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static String escapeLabelValue(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\"", "\\\"");
    }

    static boolean isLoopbackBindHost(String bindHost) {
        if (bindHost == null || bindHost.isBlank()) {
            return true;
        }
        String host = bindHost.trim();
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static boolean shouldReportServerOnline(boolean healthFilteringDisabled, boolean tracked, boolean cachedOnline) {
        return tracked && (healthFilteringDisabled || cachedOnline);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String expectedToken = bearerToken;
        if (expectedToken == null || expectedToken.isBlank()) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String prefix = "Bearer ";
        if (authorization == null || !authorization.startsWith(prefix)) {
            return false;
        }
        byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = authorization.substring(prefix.length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private Set<String> trackedServers(Config config) {
        Set<String> trackedServers = new LinkedHashSet<>();
        if (config.routing().defaultLobbies() != null) {
            for (Config.LobbyEntry entry : config.routing().defaultLobbies()) {
                trackedServers.add(entry.server());
            }
        }
        if (config.routing().contextual() != null && config.routing().contextual().groups() != null) {
            for (Config.GroupConfig groupConfig : config.routing().contextual().groups().values()) {
                if (groupConfig.servers() == null) {
                    continue;
                }
                for (Config.LobbyEntry entry : groupConfig.servers()) {
                    trackedServers.add(entry.server());
                }
            }
        }
        return trackedServers;
    }
}
