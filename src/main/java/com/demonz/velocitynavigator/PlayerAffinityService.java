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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerAffinityService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private static final Gson GSON = new Gson();

    private final ConcurrentMap<UUID, AffinityEntry> affinityMap = new ConcurrentHashMap<>();
    private final double stickiness;
    private final Duration ttl;

    public PlayerAffinityService(double stickiness) {
        this(stickiness, DEFAULT_TTL);
    }

    PlayerAffinityService(double stickiness, Duration ttl) {
        this.stickiness = Math.max(0.0, Math.min(1.0, stickiness));
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    public void setAffinity(UUID playerId, String serverName) {
        if (playerId == null || serverName == null || serverName.isBlank()) {
            return;
        }
        affinityMap.put(playerId, new AffinityEntry(serverName, Instant.now()));
    }

    public Optional<String> getAffinity(UUID playerId) {
        AffinityEntry entry = affinityMap.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, Instant.now())) {
            affinityMap.remove(playerId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.serverName());
    }

    public void removeAffinity(UUID playerId) {
        affinityMap.remove(playerId);
    }

    public Optional<String> shouldStick(UUID playerId, java.util.List<String> candidates) {
        AffinityEntry entry = affinityMap.get(playerId);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry, Instant.now())) {
            affinityMap.remove(playerId, entry);
            return Optional.empty();
        }
        String affinity = entry.serverName();
        if (!candidates.contains(affinity)) {
            return Optional.empty();
        }
        if (stickiness >= 1.0) {
            return Optional.of(affinity);
        }
        if (ThreadLocalRandom.current().nextDouble() < stickiness) {
            return Optional.of(affinity);
        }
        return Optional.empty();
    }

    public Map<UUID, String> getAll() {
        purgeExpired();
        Map<UUID, String> snapshot = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, AffinityEntry> entry : affinityMap.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().serverName());
        }
        return Map.copyOf(snapshot);
    }

    public void applyRemoteAffinity(UUID playerId, String serverName) {
        setAffinity(playerId, serverName);
    }

    public void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<UUID, AffinityEntry> entry : affinityMap.entrySet()) {
            if (isExpired(entry.getValue(), now)) {
                affinityMap.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    public void clear() {
        affinityMap.clear();
    }

    private boolean isExpired(AffinityEntry entry, Instant now) {
        return entry.updatedAt().plus(ttl).isBefore(now);
    }

    public void saveTo(Path file, Logger logger) {
        if (file == null) return;
        Instant now = Instant.now();
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, AffinityEntry> entry : affinityMap.entrySet()) {
                AffinityEntry value = entry.getValue();
                if (isExpired(value, now)) continue;
                JsonObject item = new JsonObject();
                item.addProperty("server", value.serverName());
                item.addProperty("updated_at", value.updatedAt().toString());
                root.add(entry.getKey().toString(), item);
            }
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("[VelocityNavigator] Failed to persist affinity store to {}: {}", file, e.getMessage());
            }
        }
    }

    public int loadFrom(Path file, Logger logger) {
        if (file == null || !Files.exists(file)) return 0;
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) return 0;
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            int loaded = 0;
            Instant now = Instant.now();
            for (String key : root.keySet()) {
                try {
                    UUID id = UUID.fromString(key);
                    JsonObject item = root.getAsJsonObject(key);
                    String server = item.get("server").getAsString();
                    Instant updatedAt = Instant.parse(item.get("updated_at").getAsString());
                    AffinityEntry entry = new AffinityEntry(server, updatedAt);
                    if (isExpired(entry, now)) continue;
                    affinityMap.put(id, entry);
                    loaded++;
                } catch (RuntimeException ignored) {
                }
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            if (logger != null) {
                logger.warn("[VelocityNavigator] Failed to load affinity store from {}: {}", file, e.getMessage());
            }
            return 0;
        }
    }

    private record AffinityEntry(String serverName, Instant updatedAt) {
    }
}
