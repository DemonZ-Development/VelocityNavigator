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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;

import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class RedisSyncService implements Closeable {
    private static final Pattern SERVER_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final int MAX_RESP_LINE_BYTES = 65_536;
    private static final int MAX_RESP_BULK_BYTES = 4 * 1024 * 1024;
    private static final int MAX_RESP_ARRAY_LENGTH = 16_384;
    private static final int MAX_RESP_NESTING_DEPTH = 64;
    private static final long MAX_RESP_FRAME_BYTES = 8L * 1024L * 1024L;
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
        if (next.registrationSecret().isBlank()) plugin.logger().warn("[VelocityNavigator] Redis dynamic registration is unsigned. An allowlist limits target hosts but does not authenticate publishers; configure registration_secret before production use.");
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
            try {
                subscriber = connect(true);
                BufferedInputStream input = new BufferedInputStream(subscriber.getInputStream());
                BufferedOutputStream output = new BufferedOutputStream(subscriber.getOutputStream());
                authenticate(input, output);
                command(output, "SUBSCRIBE", channel("servers:register"), channel("state"));
                connected = true;
                lastError = "";
                failures = 0;
                while (running) {
                    Object response = read(input);
                    if (response instanceof List<?> values && values.size() >= 3 && "message".equals(values.get(0))) {
                        receivedMessages.incrementAndGet();
                        lastMessageAt = System.currentTimeMillis();
                        handle(String.valueOf(values.get(1)), String.valueOf(values.get(2)));
                    }
                }
            } catch (Exception error) {
                connected = false;
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
        int port = payload.has("port") ? payload.get("port").getAsInt() : 25565;
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
                plugin.circuitBreaker().applyRemoteState(entry.getKey(), CircuitBreaker.State.valueOf(entry.getValue().getAsString()));
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

    private void publishBatch(List<Map.Entry<String, String>> messages) {
        if (!running) return;
        try (Socket socket = connect(false)) {
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
            authenticate(input, output);
            for (Map.Entry<String, String> message : messages) {
                command(output, "PUBLISH", message.getKey(), message.getValue());
                read(input);
                publishedMessages.incrementAndGet();
            }
        } catch (IOException error) {
            lastError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            plugin.logger().debug("[VelocityNavigator] Redis publish failed: {}", error.getMessage());
        }
    }

    private Socket connect(boolean subscription) throws IOException {
        SocketFactory factory = settings.ssl() ? SSLSocketFactory.getDefault() : SocketFactory.getDefault();
        Socket socket = factory.createSocket();
        if (socket instanceof SSLSocket sslSocket) {
            SSLParameters parameters = sslSocket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslSocket.setSSLParameters(parameters);
        }
        socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMs());
        socket.setSoTimeout(subscription ? 0 : settings.readTimeoutMs());
        return socket;
    }

    private void authenticate(BufferedInputStream input, BufferedOutputStream output) throws IOException {
        if (settings.password().isBlank()) return;
        if (settings.username().isBlank()) command(output, "AUTH", settings.password());
        else command(output, "AUTH", settings.username(), settings.password());
        Object result = read(input);
        if (!"OK".equals(result)) throw new IOException("Redis authentication failed");
    }

    static void command(BufferedOutputStream output, String... values) throws IOException {
        output.write(("*" + values.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.flush();
    }

    static Object read(BufferedInputStream input) throws IOException {
        return read(input, 0, new RespBudget(MAX_RESP_FRAME_BYTES));
    }

    private static Object read(BufferedInputStream input, int depth, RespBudget budget) throws IOException {
        if (depth > MAX_RESP_NESTING_DEPTH) throw new IOException("Redis nesting depth limit exceeded");
        int type = input.read();
        if (type < 0) throw new EOFException();
        budget.consume(1);
        String line = line(input, budget);
        return switch (type) {
            case '+' -> line;
            case '-' -> throw new IOException(line);
            case ':' -> number(line);
            case '$' -> bulk(input, length(line, "bulk"), budget);
            case '*' -> array(input, length(line, "array"), depth, budget);
            default -> throw new IOException("Unknown Redis response type");
        };
    }

    private static List<Object> array(BufferedInputStream input, int length, int depth, RespBudget budget) throws IOException {
        if (length < -1) throw new IOException("Invalid Redis array length");
        if (length > MAX_RESP_ARRAY_LENGTH) throw new IOException("Redis array length limit exceeded");
        if (length < 0) return new ArrayList<>();
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) values.add(read(input, depth + 1, budget));
        return values;
    }

    private static String bulk(BufferedInputStream input, int length, RespBudget budget) throws IOException {
        if (length < -1) throw new IOException("Invalid Redis bulk length");
        if (length > MAX_RESP_BULK_BYTES) throw new IOException("Redis bulk length limit exceeded");
        if (length < 0) return "";
        budget.consume((long) length + 2L);
        byte[] bytes = input.readNBytes(length);
        if (bytes.length != length) throw new EOFException();
        int carriageReturn = input.read();
        int lineFeed = input.read();
        if (carriageReturn != '\r' || lineFeed != '\n') throw new IOException("Invalid Redis bulk terminator");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String line(BufferedInputStream input, RespBudget budget) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int previous = -1;
        while (true) {
            int current = input.read();
            if (current < 0) throw new EOFException();
            budget.consume(1);
            if (previous == '\r' && current == '\n') break;
            if (previous >= 0) {
                if (output.size() >= MAX_RESP_LINE_BYTES) throw new IOException("Redis response line limit exceeded");
                output.write(previous);
            }
            previous = current;
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static int length(String value, String type) throws IOException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) throw new IOException("Invalid Redis " + type + " length");
            return (int) parsed;
        } catch (NumberFormatException error) {
            throw new IOException("Invalid Redis " + type + " length", error);
        }
    }

    private static long number(String value) throws IOException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid Redis integer response", error);
        }
    }

    private static final class RespBudget {
        private long remaining;

        private RespBudget(long remaining) {
            this.remaining = remaining;
        }

        private void consume(long bytes) throws IOException {
            if (bytes < 0 || bytes > remaining) throw new IOException("Redis frame byte limit exceeded");
            remaining -= bytes;
        }
    }

    private void addSignature(JsonObject payload) {
        if (!settings.registrationSecret().isBlank()) payload.addProperty("signature", registrationSignature(payload, settings.registrationSecret()));
    }

    private boolean trustedRegistration(JsonObject payload) {
        if (settings.registrationSecret().isBlank()) return true;
        String supplied = string(payload, "signature");
        if (supplied.isBlank()) return false;
        long now = System.currentTimeMillis();
        long timestamp;
        try {
            timestamp = payload.get("timestamp").getAsLong();
        } catch (RuntimeException error) {
            return false;
        }
        if (!registrationFresh(timestamp, now, settings.registrationMaxAgeSeconds())) return false;
        String expected = registrationSignature(payload, settings.registrationSecret());
        if (!MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8))) return false;
        acceptedRegistrationSignatures.entrySet().removeIf(entry -> entry.getValue() < now);
        long expiresAt = now + settings.registrationMaxAgeSeconds() * 1000L;
        return acceptedRegistrationSignatures.putIfAbsent(supplied, expiresAt) == null;
    }

    static String registrationSignature(JsonObject payload, String secret) {
        return RedisRegistrationSigner.sign(payload, secret);
    }

    static boolean registrationFresh(long timestamp, long now, int maxAgeSeconds) {
        long maximumSkew = Math.max(5, maxAgeSeconds) * 1000L;
        return timestamp > 0 && timestamp >= now - maximumSkew && timestamp <= now + maximumSkew;
    }

    private boolean allowedRegistrationHost(String host) {
        List<String> rules = settings.allowedRegistrationHosts();
        if (hostAllowed(host, rules)) return true;
        rejectedRegistrations.incrementAndGet();
        plugin.logger().warn("[VelocityNavigator] Rejected Redis registration host {} because it is not allowlisted.", host);
        return false;
    }

    static boolean hostAllowed(String host, List<String> rules) {
        if (rules == null || rules.isEmpty()) return true;
        String normalized = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
        for (String rule : rules) {
            String candidate = rule.toLowerCase(java.util.Locale.ROOT);
            if (candidate.equals(normalized)) return true;
            if (candidate.startsWith("*.") && normalized.endsWith(candidate.substring(1))) return true;
        }
        return false;
    }

    public Status status() {
        return new Status(settings.enabled(), connected, reconnects.get(), publishedMessages.get(), receivedMessages.get(), rejectedRegistrations.get(), lastMessageAt, lastError);
    }

    public CompletableFuture<TestResult> testConnection() {
        if (!settings.enabled()) return CompletableFuture.completedFuture(new TestResult(false, "Redis is disabled in navigator.toml."));
        return CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            try (Socket socket = connect(false)) {
                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                authenticate(input, output);
                command(output, "PING");
                if (!"PONG".equals(read(input))) return new TestResult(false, "Redis returned an unexpected PING response.");
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
