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
package com.demonz.velocitynavigator.redis;

import com.demonz.velocitynavigator.CircuitBreakerState;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.common.RedisSecurityUtils;
import com.demonz.velocitynavigator.common.RedisTransport;
import com.demonz.velocitynavigator.config.AdvancedConfig;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.health.CircuitBreaker;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class RedisSyncService implements Closeable {
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private final VelocityNavigator plugin;
    private volatile AdvancedConfig.Redis settings = AdvancedConfig.defaults().redis();
    private volatile boolean running;
    private Socket subscriber;
    private ExecutorService executor;
    private ScheduledTask syncTask;
    private final AtomicLong reconnects = new AtomicLong();
    private final AtomicLong publishedMessages = new AtomicLong();
    private final AtomicLong receivedMessages = new AtomicLong();
    private final AtomicLong rejectedRegistrations = new AtomicLong();
    private final Map<String, Long> acceptedRegistrationSignatures = new ConcurrentHashMap<>();
    private volatile boolean connected;
    private volatile String lastError = "";
    private volatile long lastMessageAt;

    public RedisSyncService(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    public synchronized void configure(AdvancedConfig.Redis next) {
        close();
        settings = next;
        if (!next.enabled()) return;
        running = true;
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "velocitynavigator-redis");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::subscriptionLoop);
        syncTask = plugin.server().getScheduler().buildTask(plugin, this::publishState)
                .delay(1, TimeUnit.SECONDS)
                .repeat(next.syncSeconds(), TimeUnit.SECONDS)
                .schedule();
        plugin.logger().info("[VelocityNavigator] Redis multi-proxy sync enabled as node {} on {}:{}.", next.nodeId(), next.host(), next.port());
        if (next.registrationSecret().isBlank()) plugin.logger().warn("[VelocityNavigator] Redis dynamic registration is disabled because registration_secret is not set. Set the same registration_secret on the proxy and all backend servers to enable dynamic registration.");
    }

    public void publishRegistration(String name, String host, int port) {
        JsonObject payload = envelope("register");
        payload.addProperty("name", name);
        payload.addProperty("host", host);
        payload.addProperty("port", port);
        addSignature(payload);
        publish(channel("servers:register"), payload.toString());
    }

    private void subscriptionLoop() {
        int failures = 0;
        while (running) {
            RedisTransport.ConnectionSettings connection = currentConnectionSettings();
            try {
                Socket newSub = RedisTransport.connect(connection, true);
                newSub.setSoTimeout(subscriberTimeoutMs());
                synchronized (this) {
                    if (!running) {
                        try { newSub.close(); } catch (IOException ignored) {}
                        return;
                    }
                    subscriber = newSub;
                }
                BufferedInputStream input = new BufferedInputStream(subscriber.getInputStream());
                BufferedOutputStream output = new BufferedOutputStream(subscriber.getOutputStream());
                RedisTransport.authenticate(connection, input, output);
                RedisTransport.writeCommand(output, "SUBSCRIBE", channel("servers:register"), channel("state"));
                connected = true;
                lastError = "";
                failures = 0;
                while (running) {
                    Object response = RedisTransport.readResponse(input);
                    if (response instanceof List<?> values && values.size() >= 3 && "message".equals(values.get(0))) {
                        receivedMessages.incrementAndGet();
                        lastMessageAt = System.currentTimeMillis();
                        handle(String.valueOf(values.get(1)), String.valueOf(values.get(2)));
                    }
                }
            } catch (SocketTimeoutException timeout) {
                connected = false;
                synchronized (this) {
                    if (subscriber != null) {
                        try {
                            subscriber.close();
                        } catch (IOException ignored) {
                        }
                        subscriber = null;
                    }
                }
                if (running) {
                    lastError = "subscription read timed out";
                    if (plugin.config().debug().verboseLogging()) {
                        plugin.logger().debug("[VelocityNavigator] Redis subscription read timed out; reconnecting.");
                    }
                    try {
                        Thread.sleep(Math.min(settings.reconnectMaxMs(), settings.reconnectMinMs()));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (Exception error) {
                connected = false;
                synchronized (this) {
                    if (subscriber != null) {
                        try {
                            subscriber.close();
                        } catch (IOException ignored) {
                        }
                        subscriber = null;
                    }
                }
                if (running) {
                    lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                    reconnects.incrementAndGet();
                    plugin.logger().warn("[VelocityNavigator] Redis subscription interrupted: {}", error.getMessage());
                    try {
                        long exponential = Math.min(settings.reconnectMaxMs(), settings.reconnectMinMs() * (1L << Math.min(20, failures++)));
                        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, exponential / 4L));
                        Thread.sleep(Math.min(settings.reconnectMaxMs(), exponential + jitter));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    private int subscriberTimeoutMs() {
        long syncSlack = 2L * settings.syncSeconds() * 1000L + 5000L;
        return (int) Math.min(120_000L, Math.max(settings.readTimeoutMs(), syncSlack));
    }

    private void publishState() {
        try {
            List<Map.Entry<String, String>> messages = new ArrayList<>();
            JsonObject circuit = envelope("circuit");
            JsonObject circuitData = new JsonObject();
            if (plugin.circuitBreaker() != null) plugin.circuitBreaker().getStates().forEach((name, state) -> circuitData.addProperty(name, state.name()));
            circuit.add("data", circuitData);
            messages.add(Map.entry(channel("state"), circuit.toString()));

            JsonObject health = envelope("health");
            JsonObject healthData = new JsonObject();
            plugin.healthService().getHealthSnapshots().forEach((name, snapshot) -> {
                JsonObject item = new JsonObject();
                item.addProperty("online", snapshot.online());
                item.addProperty("checked_at", snapshot.checkedAtEpochMilli());
                item.addProperty("latency", snapshot.latency());
                item.addProperty("state", snapshot.state());
                healthData.add(name, item);
            });
            health.add("data", healthData);
            messages.add(Map.entry(channel("state"), health.toString()));

            JsonObject affinity = envelope("affinity");
            JsonObject affinityData = new JsonObject();
            if (plugin.affinityService() != null) plugin.affinityService().getAll().forEach((id, server) -> affinityData.addProperty(id.toString(), server));
            affinity.add("data", affinityData);
            messages.add(Map.entry(channel("state"), affinity.toString()));
            publishBatch(messages);
        } catch (RuntimeException error) {
            plugin.logger().debug("[VelocityNavigator] Redis state publish failed: {}", error.getMessage());
        }
    }

    private void handle(String channel, String raw) {
        try {
            JsonObject payload = JsonParser.parseString(raw).getAsJsonObject();
            if (settings.nodeId().equals(string(payload, "node"))) return;
            if (channel.equals(channel("servers:register"))) {
                if (!trustedRegistration(payload)) {
                    rejectedRegistrations.incrementAndGet();
                    plugin.logger().warn("[VelocityNavigator] Rejected untrusted Redis registration for {}.", string(payload, "name"));
                    return;
                }
                JsonObject safePayload = payload.deepCopy();
                plugin.server().getScheduler().buildTask(plugin, () -> {
                    try {
                        register(safePayload);
                    } catch (RuntimeException error) {
                        rejectedRegistrations.incrementAndGet();
                        plugin.logger().warn("[VelocityNavigator] Rejected invalid Redis registration: {}", error.getMessage());
                    }
                }).schedule();
                return;
            }
            String type = string(payload, "type");
            JsonObject data = payload.has("data") && payload.get("data").isJsonObject() ? payload.getAsJsonObject("data") : new JsonObject();
            switch (type) {
                case "circuit" -> mergeCircuit(data);
                case "health" -> mergeHealth(data);
                case "affinity" -> mergeAffinity(data);
                default -> {
                }
            }
        } catch (RuntimeException error) {
            plugin.logger().warn("[VelocityNavigator] Ignored invalid Redis payload on {}: {}", channel, error.getMessage());
        }
    }

    private void register(JsonObject payload) {
        String name = string(payload, "name");
        String host = string(payload, "host");
        int port = payload.has("port") && payload.get("port").isJsonPrimitive() ? payload.get("port").getAsInt() : 25565;
        String action = string(payload, "action");
        if (!SERVER_NAME.matcher(name).matches() || port < 1 || port > 65535) return;
        if (!allowedRegistrationHost(host)) return;
        if ("unregister".equalsIgnoreCase(action)) {
            plugin.server().getServer(name).ifPresent(server -> plugin.server().unregisterServer(server.getServerInfo()));
            plugin.unregisterDynamicLobby(name);
            return;
        }
        if (host.isBlank()) return;
        plugin.server().getServer(name).ifPresent(server -> {
            InetSocketAddress current = server.getServerInfo().getAddress();
            if (current.getPort() != port || !current.getHostString().equalsIgnoreCase(host)) plugin.server().unregisterServer(server.getServerInfo());
        });
        if (plugin.server().getServer(name).isEmpty()) plugin.server().registerServer(new ServerInfo(name, InetSocketAddress.createUnresolved(host, port)));
        String group = string(payload, "group");
        int maxPlayers = payload.has("max_players") ? payload.get("max_players").getAsInt() : Config.LobbyEntry.UNCAPPED;
        int weight = payload.has("weight") ? payload.get("weight").getAsInt() : Config.LobbyEntry.DEFAULT_WEIGHT;
        plugin.registerDynamicLobby(name, group, maxPlayers, weight);
        plugin.logger().info("[VelocityNavigator] Dynamically registered backend {} at {}:{} from Redis.", name, host, port);
    }

    private void mergeCircuit(JsonObject data) {
        if (plugin.circuitBreaker() == null) return;
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            try {
                plugin.circuitBreaker().applyRemoteState(entry.getKey(), CircuitBreakerState.valueOf(entry.getValue().getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void mergeHealth(JsonObject data) {
        data.entrySet().forEach(entry -> {
            JsonObject item = entry.getValue().getAsJsonObject();
            plugin.healthService().mergeRemoteHealth(entry.getKey(), item.get("online").getAsBoolean(), item.get("checked_at").getAsLong(), item.get("latency").getAsLong(), string(item, "state"));
        });
    }

    private void mergeAffinity(JsonObject data) {
        if (plugin.affinityService() == null) return;
        data.entrySet().forEach(entry -> {
            try {
                plugin.affinityService().applyRemoteAffinity(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        });
    }

    private JsonObject envelope(String type) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("node", settings.nodeId());
        object.addProperty("timestamp", System.currentTimeMillis());
        return object;
    }

    private String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private String channel(String suffix) {
        return settings.channelPrefix() + ":" + suffix;
    }

    private void publish(String channel, String message) {
        publishBatch(List.of(Map.entry(channel, message)));
    }

    private Socket publisherSocket;
    private BufferedInputStream publisherInput;
    private BufferedOutputStream publisherOutput;

    private synchronized void closePublisher() {
        if (publisherSocket != null) {
            try { publisherSocket.close(); } catch (Exception ignored) {}
            publisherSocket = null;
            publisherInput = null;
            publisherOutput = null;
        }
    }

    private synchronized void publishBatch(List<Map.Entry<String, String>> messages) {
        if (!running) return;
        RedisTransport.ConnectionSettings connection = currentConnectionSettings();
        try {
            if (publisherSocket == null || publisherSocket.isClosed() || !publisherSocket.isConnected()) {
                closePublisher();
                publisherSocket = RedisTransport.connect(connection, false);
                publisherInput = new BufferedInputStream(publisherSocket.getInputStream());
                publisherOutput = new BufferedOutputStream(publisherSocket.getOutputStream());
                RedisTransport.authenticate(connection, publisherInput, publisherOutput);
            }
            for (Map.Entry<String, String> message : messages) {
                RedisTransport.writeCommand(publisherOutput, "PUBLISH", message.getKey(), message.getValue());
                RedisTransport.readResponse(publisherInput);
                publishedMessages.incrementAndGet();
            }
        } catch (IOException error) {
            closePublisher();
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            plugin.logger().debug("[VelocityNavigator] Redis publish failed: {}", error.getMessage());
        }
    }

    private RedisTransport.ConnectionSettings currentConnectionSettings() {
        return new RedisTransport.ConnectionSettings(
                settings.host(),
                settings.port(),
                settings.username(),
                settings.password(),
                settings.ssl(),
                settings.connectTimeoutMs(),
                settings.readTimeoutMs());
    }

    private void addSignature(JsonObject payload) {
        if (!settings.registrationSecret().isBlank()) payload.addProperty("signature", RedisSecurityUtils.sign(payload, settings.registrationSecret()));
    }

    private boolean trustedRegistration(JsonObject payload) {
        if (settings.registrationSecret().isBlank()) {
            return false;
        }
        String supplied = string(payload, "signature");
        if (supplied.isBlank()) return false;
        long now = System.currentTimeMillis();
        long timestamp;
        try {
            timestamp = payload.get("timestamp").getAsLong();
        } catch (RuntimeException error) {
            return false;
        }
        if (!RedisSecurityUtils.fresh(timestamp, now, settings.registrationMaxAgeSeconds())) return false;
        String expected = RedisSecurityUtils.sign(payload, settings.registrationSecret());
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) return false;
        acceptedRegistrationSignatures.entrySet().removeIf(entry -> entry.getValue() < now);
        long expiresAt = now + settings.registrationMaxAgeSeconds() * 1000L;
        return acceptedRegistrationSignatures.putIfAbsent(supplied, expiresAt) == null;
    }

    private boolean allowedRegistrationHost(String host) {
        List<String> rules = settings.allowedRegistrationHosts();
        if (RedisSecurityUtils.hostAllowed(host, rules)) return true;
        rejectedRegistrations.incrementAndGet();
        plugin.logger().warn("[VelocityNavigator] Rejected Redis registration host {} because it is not allowlisted.", host);
        return false;
    }

    public Status status() {
        return new Status(settings.enabled(), connected, reconnects.get(), publishedMessages.get(), receivedMessages.get(), rejectedRegistrations.get(), lastMessageAt, lastError);
    }

    public CompletableFuture<TestResult> testConnection() {
        if (!settings.enabled()) return CompletableFuture.completedFuture(new TestResult(false, "Redis is disabled in navigator.toml."));
        RedisTransport.ConnectionSettings connection = currentConnectionSettings();
        return CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            try (Socket socket = RedisTransport.connect(connection, false)) {
                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                RedisTransport.authenticate(connection, input, output);
                RedisTransport.writeCommand(output, "PING");
                if (!"PONG".equals(RedisTransport.readResponse(input))) {
                    return new TestResult(false, "Redis returned an unexpected PING response.");
                }
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                return new TestResult(true, "Redis connection, TLS, authentication, and PING succeeded in " + millis + "ms.");
            } catch (IOException | RuntimeException error) {
                return new TestResult(false, "Redis test failed: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            }
        });
    }

    public record Status(boolean enabled, boolean connected, long reconnects, long publishedMessages, long receivedMessages, long rejectedRegistrations, long lastMessageAt, String lastError) {
    }

    public record TestResult(boolean success, String message) {
    }

    @Override
    public synchronized void close() {
        running = false;
        connected = false;
        acceptedRegistrationSignatures.clear();
        closePublisher();
        if (syncTask != null) syncTask.cancel();
        syncTask = null;
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (IOException ignored) {
            }
        }
        subscriber = null;
        if (executor != null) executor.shutdownNow();
        executor = null;
    }
}
