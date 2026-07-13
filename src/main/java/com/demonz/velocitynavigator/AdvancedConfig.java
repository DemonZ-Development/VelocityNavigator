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

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record AdvancedConfig(
        Party party,
        Queue queue,
        Redis redis,
        BackendStates backendStates,
        ServerManagement serverManagement
) {
    public static AdvancedConfig load(Path file) throws IOException {
        Toml toml = new Toml().read(file.toFile());
        return new AdvancedConfig(
                new Party(bool(toml, "party.enabled", true), integer(toml, "party.invite_timeout_seconds", 60), bool(toml, "party.follow_leader", true), integer(toml, "party.max_size", 20), text(toml, "party.command", "party"), text(toml, "party.chat_command", "p"), text(toml, "party.permission", "none")),
                new Queue(bool(toml, "queue.enabled", true), integer(toml, "queue.poll_seconds", 2), integer(toml, "queue.notify_seconds", 5), integer(toml, "queue.max_size", 500), text(toml, "queue.holding_server", ""), text(toml, "queue.command", "queue"), text(toml, "queue.permission", "none")),
                new Redis(bool(toml, "redis.enabled", false), text(toml, "redis.host", "127.0.0.1"), integer(toml, "redis.port", 6379), text(toml, "redis.username", ""), text(toml, "redis.password", ""), bool(toml, "redis.ssl", false), text(toml, "redis.node_id", ""), text(toml, "redis.channel_prefix", "vn"), integer(toml, "redis.sync_seconds", 5), integer(toml, "redis.connect_timeout_ms", 3000), integer(toml, "redis.read_timeout_ms", 10000), integer(toml, "redis.reconnect_min_ms", 1000), integer(toml, "redis.reconnect_max_ms", 30000), text(toml, "redis.registration_secret", ""), integer(toml, "redis.registration_max_age_seconds", 30), strings(toml, "redis.allowed_registration_hosts", List.of())),
                new BackendStates(bool(toml, "backend_states.enabled", true), strings(toml, "backend_states.allowed", List.of("LOBBY", "WAITING", "AVAILABLE")), bool(toml, "backend_states.allow_unknown", true)),
                new ServerManagement(bool(toml, "server_management.enabled", true), text(toml, "server_management.velocity_config", "velocity.toml"), bool(toml, "server_management.allow_overwrite", false))
        );
    }

    public static AdvancedConfig defaults() {
        return new AdvancedConfig(new Party(true, 60, true, 20, "party", "p", "none"), new Queue(true, 2, 5, 500, "", "queue", "none"), new Redis(false, "127.0.0.1", 6379, "", "", false, "", "vn", 5, 3000, 10000, 1000, 30000, "", 30, List.of()), new BackendStates(true, List.of("LOBBY", "WAITING", "AVAILABLE"), true), new ServerManagement(true, "velocity.toml", false));
    }

    private static boolean bool(Toml toml, String key, boolean fallback) {
        Boolean value = toml.getBoolean(key);
        return value == null ? fallback : value;
    }

    private static int integer(Toml toml, String key, int fallback) {
        Long value = toml.getLong(key);
        return value == null ? fallback : Math.toIntExact(value);
    }

    private static String text(Toml toml, String key, String fallback) {
        String value = toml.getString(key);
        return value == null ? fallback : value.trim();
    }

    private static List<String> strings(Toml toml, String key, List<String> fallback) {
        List<String> values = toml.getList(key);
        return values == null ? fallback : values;
    }

    public record Party(boolean enabled, int inviteTimeoutSeconds, boolean followLeader, int maxSize, String command, String chatCommand, String permission) {
        public Party {
            inviteTimeoutSeconds = Math.max(10, inviteTimeoutSeconds);
            maxSize = Math.max(2, Math.min(1000, maxSize));
            command = AdvancedConfig.command(command, "party");
            chatCommand = AdvancedConfig.command(chatCommand, "p");
            permission = permission == null || permission.isBlank() ? "none" : permission.trim();
        }
    }

    public record Queue(boolean enabled, int pollSeconds, int notifySeconds, int maxSize, String holdingServer, String command, String permission) {
        public Queue {
            pollSeconds = Math.max(1, pollSeconds);
            notifySeconds = Math.max(1, notifySeconds);
            maxSize = Math.max(1, maxSize);
            holdingServer = holdingServer == null ? "" : holdingServer.trim().toLowerCase(Locale.ROOT);
            command = AdvancedConfig.command(command, "queue");
            permission = permission == null || permission.isBlank() ? "none" : permission.trim();
        }
    }

    private static String command(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace("/", "");
        return normalized.matches("[a-z0-9_-]{1,32}") ? normalized : fallback;
    }

    public record Redis(boolean enabled, String host, int port, String username, String password, boolean ssl, String nodeId, String channelPrefix, int syncSeconds, int connectTimeoutMs, int readTimeoutMs, int reconnectMinMs, int reconnectMaxMs, String registrationSecret, int registrationMaxAgeSeconds, List<String> allowedRegistrationHosts) {
        public Redis {
            host = host == null || host.isBlank() ? "127.0.0.1" : host.trim();
            port = Math.max(1, Math.min(65535, port));
            username = username == null ? "" : username;
            password = password == null ? "" : password;
            nodeId = nodeId == null || nodeId.isBlank() ? java.util.UUID.randomUUID().toString() : nodeId.trim();
            channelPrefix = channelPrefix == null || channelPrefix.isBlank() ? "vn" : channelPrefix.trim();
            syncSeconds = Math.max(1, syncSeconds);
            connectTimeoutMs = Math.max(250, Math.min(30000, connectTimeoutMs));
            readTimeoutMs = Math.max(1000, Math.min(120000, readTimeoutMs));
            reconnectMinMs = Math.max(250, reconnectMinMs);
            reconnectMaxMs = Math.max(reconnectMinMs, reconnectMaxMs);
            registrationSecret = registrationSecret == null ? "" : registrationSecret;
            registrationMaxAgeSeconds = Math.max(5, Math.min(300, registrationMaxAgeSeconds));
            allowedRegistrationHosts = allowedRegistrationHosts == null ? List.of() : allowedRegistrationHosts.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        }
    }

    public record BackendStates(boolean enabled, List<String> allowed, boolean allowUnknown) {
        public BackendStates {
            Set<String> normalized = new LinkedHashSet<>();
            if (allowed != null) {
                for (String value : allowed) {
                    if (value != null && !value.isBlank()) {
                        normalized.add(value.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
            allowed = List.copyOf(normalized);
        }
    }

    public record ServerManagement(boolean enabled, String velocityConfig, boolean allowOverwrite) {
        public ServerManagement {
            velocityConfig = velocityConfig == null || velocityConfig.isBlank() ? "velocity.toml" : velocityConfig.trim();
        }
    }
}
