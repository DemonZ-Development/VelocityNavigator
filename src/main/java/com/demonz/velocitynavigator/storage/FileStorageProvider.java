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
package com.demonz.velocitynavigator.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FileStorageProvider implements StorageProvider {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final Path dataDir;
    private final Path affinityFile;
    private final Path authFile;
    private final Logger logger;

    private final ConcurrentMap<UUID, FileAffinityRecord> affinityMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, AuthRecord> authMap = new ConcurrentHashMap<>();
    private final List<RoutingStat> routingStats = Collections.synchronizedList(new ArrayList<>());
    private final List<ConnectionRecord> connectionsLog = Collections.synchronizedList(new ArrayList<>());
    private long totalConnections = 0;

    public FileStorageProvider(Path dataDir, Logger logger) {
        this.dataDir = dataDir;
        this.affinityFile = dataDir.resolve("player_affinity.json");
        this.authFile = dataDir.resolve("player_auth.json");
        this.logger = logger;
    }

    @Override
    public void initialize() throws Exception {
        Files.createDirectories(dataDir);
        loadAffinityFile();
        loadAuthFile();
    }

    @Override
    public void shutdown() {
        saveAffinityFile();
        saveAuthFile();
    }

    @Override
    public void saveAffinity(UUID player, String server, Instant expiry) {
        if (player == null || server == null || server.isBlank()) return;
        affinityMap.put(player, new FileAffinityRecord(server, expiry));
        saveAffinityFile();
    }

    @Override
    public Optional<String> loadAffinity(UUID player) {
        if (player == null) return Optional.empty();
        FileAffinityRecord record = affinityMap.get(player);
        if (record == null) return Optional.empty();
        if (record.expiry != null && Instant.now().isAfter(record.expiry)) {
            affinityMap.remove(player);
            return Optional.empty();
        }
        return Optional.ofNullable(record.server);
    }

    @Override
    public void clearAffinity(UUID player) {
        if (player != null) {
            affinityMap.remove(player);
            saveAffinityFile();
        }
    }

    @Override
    public void saveCredentials(UUID player, String passwordHash, String salt) {
        if (player == null) return;
        AuthRecord existing = authMap.get(player);
        String totp = existing != null ? existing.totpSecret() : null;
        String ip = existing != null ? existing.lastKnownIp() : null;
        AuthRecord updated = new AuthRecord(player, passwordHash, salt, totp, ip, Instant.now());
        authMap.put(player, updated);
        saveAuthFile();
    }

    @Override
    public Optional<AuthRecord> loadCredentials(UUID player) {
        if (player == null) return Optional.empty();
        return Optional.ofNullable(authMap.get(player));
    }

    @Override
    public void saveTwoFactorSecret(UUID player, String encryptedSecret) {
        if (player == null) return;
        AuthRecord existing = authMap.get(player);
        if (existing == null) return;
        AuthRecord updated = new AuthRecord(player, existing.passwordHash(), existing.salt(), encryptedSecret, existing.lastKnownIp(), Instant.now());
        authMap.put(player, updated);
        saveAuthFile();
    }

    @Override
    public void saveLastKnownIp(UUID player, String ip) {
        if (player == null) return;
        AuthRecord existing = authMap.get(player);
        if (existing == null) return;
        AuthRecord updated = new AuthRecord(player, existing.passwordHash(), existing.salt(), existing.totpSecret(), ip, Instant.now());
        authMap.put(player, updated);
        saveAuthFile();
    }

    @Override
    public Optional<String> loadLastKnownIp(UUID player) {
        if (player == null) return Optional.empty();
        AuthRecord record = authMap.get(player);
        return record != null ? Optional.ofNullable(record.lastKnownIp()) : Optional.empty();
    }

    @Override
    public synchronized void recordConnection(UUID player, String fromServer, String toServer, Instant timestamp) {
        connectionsLog.add(new ConnectionRecord(player, fromServer, toServer, timestamp));
        totalConnections++;
        if (connectionsLog.size() > 5000) {
            connectionsLog.subList(0, connectionsLog.size() - 4000).clear();
        }
    }

    @Override
    public synchronized void recordRouting(String server, String algorithm, int candidateCount, long latencyMs) {
        routingStats.add(new RoutingStat(server, algorithm, candidateCount, latencyMs, Instant.now()));
        if (routingStats.size() > 5000) {
            routingStats.subList(0, 1000).clear();
        }
    }

    @Override
    public List<RoutingStat> getRoutingStats(String server, Instant from, Instant to) {
        synchronized (routingStats) {
            return routingStats.stream()
                    .filter(s -> server == null || s.server().equalsIgnoreCase(server))
                    .filter(s -> from == null || !s.timestamp().isBefore(from))
                    .filter(s -> to == null || !s.timestamp().isAfter(to))
                    .toList();
        }
    }

    @Override
    public synchronized long getTotalConnections(Instant from, Instant to) {
        if (from == null && to == null) return totalConnections;
        synchronized (connectionsLog) {
            return connectionsLog.stream()
                    .filter(record -> from == null || !record.timestamp().isBefore(from))
                    .filter(record -> to == null || !record.timestamp().isAfter(to))
                    .count();
        }
    }

    private void loadAffinityFile() {
        if (!Files.exists(affinityFile)) return;
        try {
            String json = Files.readString(affinityFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (String key : obj.keySet()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    JsonObject entry = obj.getAsJsonObject(key);
                    String server = entry.get("server").getAsString();
                    Instant expiry = entry.has("expires_at") ? Instant.ofEpochMilli(entry.get("expires_at").getAsLong()) : null;
                    if (expiry == null || Instant.now().isBefore(expiry)) {
                        affinityMap.put(uuid, new FileAffinityRecord(server, expiry));
                    }
                } catch (RuntimeException e) {
                    logger.warn("Failed to parse affinity entry for {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load affinity file: {}", e.getMessage());
        }
    }

    private synchronized void saveAffinityFile() {
        try {
            JsonObject root = new JsonObject();
            Instant now = Instant.now();
            for (Map.Entry<UUID, FileAffinityRecord> entry : affinityMap.entrySet()) {
                if (entry.getValue().expiry != null && now.isAfter(entry.getValue().expiry)) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("server", entry.getValue().server);
                if (entry.getValue().expiry != null) {
                    obj.addProperty("expires_at", entry.getValue().expiry.toEpochMilli());
                }
                root.add(entry.getKey().toString(), obj);
            }
            Files.writeString(affinityFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to save affinity file: {}", e.getMessage());
        }
    }

    private void loadAuthFile() {
        if (!Files.exists(authFile)) return;
        try {
            String json = Files.readString(authFile, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (String key : obj.keySet()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    JsonObject entry = obj.getAsJsonObject(key);
                    String hash = entry.get("hash").getAsString();
                    String salt = entry.get("salt").getAsString();
                    String totp = entry.has("totp") ? entry.get("totp").getAsString() : null;
                    String ip = entry.has("ip") ? entry.get("ip").getAsString() : null;
                    authMap.put(uuid, new AuthRecord(uuid, hash, salt, totp, ip, Instant.now()));
                } catch (RuntimeException e) {
                    logger.warn("Failed to parse auth entry for {}: {}", key, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load auth file: {}", e.getMessage());
        }
    }

    private synchronized void saveAuthFile() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, AuthRecord> entry : authMap.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("hash", entry.getValue().passwordHash());
                obj.addProperty("salt", entry.getValue().salt());
                if (entry.getValue().totpSecret() != null) obj.addProperty("totp", entry.getValue().totpSecret());
                if (entry.getValue().lastKnownIp() != null) obj.addProperty("ip", entry.getValue().lastKnownIp());
                root.add(entry.getKey().toString(), obj);
            }
            Files.writeString(authFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Failed to save auth file: {}", e.getMessage());
        }
    }

    private record FileAffinityRecord(String server, Instant expiry) {}
    private record ConnectionRecord(UUID player, String fromServer, String toServer, Instant timestamp) {}
}
