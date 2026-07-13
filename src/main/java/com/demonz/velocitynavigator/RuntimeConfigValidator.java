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
import java.util.Set;

public final class RuntimeConfigValidator {
    private RuntimeConfigValidator() {
    }

    public static Validation validate(Config config, AdvancedConfig advanced, Set<String> registeredServers) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        validateCommands(config, advanced, errors);
        validateRedis(advanced.redis(), warnings);
        validateQueue(config, advanced.queue(), registeredServers, errors, warnings);
        validateNetworkEndpoints(config, errors, warnings);
        if (advanced.backendStates().enabled() && advanced.backendStates().allowed().isEmpty() && !advanced.backendStates().allowUnknown()) errors.add("Backend-state routing rejects every server because allowed is empty and allow_unknown is false.");
        if (advanced.serverManagement().allowOverwrite()) warnings.add("Server-management overwrite protection is disabled.");
        return new Validation(List.copyOf(errors), List.copyOf(warnings));
    }

    private static void validateCommands(Config config, AdvancedConfig advanced, List<String> errors) {
        Map<String, String> owners = new LinkedHashMap<>();
        addCommand(owners, errors, config.commands().primary(), "lobby command");
        config.commands().aliases().forEach(command -> addCommand(owners, errors, command, "lobby command"));
        config.commands().adminAliases().forEach(command -> addCommand(owners, errors, command, "admin command"));
        if (advanced.party().enabled()) {
            addCommand(owners, errors, advanced.party().command(), "party command");
            addCommand(owners, errors, advanced.party().chatCommand(), "party-chat command");
        }
        if (advanced.queue().enabled()) addCommand(owners, errors, advanced.queue().command(), "queue command");
    }

    private static void addCommand(Map<String, String> owners, List<String> errors, String command, String owner) {
        String normalized = command.toLowerCase(Locale.ROOT);
        String existing = owners.putIfAbsent(normalized, owner);
        if (existing != null && !existing.equals(owner)) errors.add("Command /" + normalized + " is assigned to both the " + existing + " and the " + owner + ".");
    }

    private static void validateRedis(AdvancedConfig.Redis redis, List<String> warnings) {
        if (!redis.enabled()) return;
        if (redis.password().isBlank()) warnings.add("Redis is enabled without authentication. Use a Redis ACL username and password in production.");
        if (redis.registrationSecret().isBlank()) warnings.add("Redis dynamic registration is unsigned. Set registration_secret before production use.");
        if (redis.allowedRegistrationHosts().isEmpty()) warnings.add("Redis dynamic registration has no target-host allowlist.");
        if (!redis.ssl() && !loopback(redis.host())) warnings.add("Redis TLS is disabled for a non-loopback host.");
        if (!redis.channelPrefix().matches("[A-Za-z0-9_.:-]{1,64}")) warnings.add("Redis channel_prefix contains unusual characters; use letters, numbers, dots, underscores, colons, or hyphens.");
    }

    private static void validateQueue(Config config, AdvancedConfig.Queue queue, Set<String> registeredServers, List<String> errors, List<String> warnings) {
        if (!queue.enabled()) return;
        String holding = queue.holdingServer();
        if (holding.isBlank()) {
            if (config.routing().balanceInitialJoin()) warnings.add("Queue holding_server is blank, so full initial-join pools still use the configured no-server fallback.");
            return;
        }
        Set<String> registered = normalize(registeredServers);
        if (!registered.contains(holding)) errors.add("Queue holding_server '" + holding + "' is not registered in Velocity.");
        Set<String> routed = new LinkedHashSet<>();
        config.routing().defaultLobbies().forEach(entry -> routed.add(entry.server().toLowerCase(Locale.ROOT)));
        config.routing().contextual().groups().values().forEach(group -> group.servers().forEach(entry -> routed.add(entry.server().toLowerCase(Locale.ROOT))));
        if (routed.contains(holding)) errors.add("Queue holding_server '" + holding + "' must not also be a routed lobby candidate.");
    }

    private static void validateNetworkEndpoints(Config config, List<String> errors, List<String> warnings) {
        Config.PrometheusSettings prometheus = config.metrics().prometheus();
        Config.DashboardSettings dashboard = config.dashboard();
        if (prometheus.enabled() && !loopback(prometheus.bindHost()) && prometheus.bearerToken().isBlank()) warnings.add("Prometheus is exposed on a non-loopback address without a bearer token.");
        if (dashboard.enabled() && !loopback(dashboard.bindHost()) && dashboard.bearerToken().isBlank()) warnings.add("The HTML dashboard is exposed on a non-loopback address without a bearer token.");
        if (prometheus.enabled() && dashboard.enabled() && prometheus.port() == dashboard.port() && prometheus.bindHost().equalsIgnoreCase(dashboard.bindHost())) errors.add("Prometheus and the HTML dashboard cannot use the same bind host and port.");
    }

    private static boolean loopback(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("127.0.0.1") || normalized.equals("localhost") || normalized.equals("::1") || normalized.equals("[::1]");
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) values.stream().filter(value -> value != null && !value.isBlank()).map(value -> value.toLowerCase(Locale.ROOT)).forEach(normalized::add);
        return normalized;
    }

    public record Validation(List<String> errors, List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
