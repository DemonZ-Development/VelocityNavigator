/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class StorageMigrator {
    private final Logger logger;

    public StorageMigrator(Logger logger) {
        this.logger = logger;
    }

    public void migrate(StorageProvider source, StorageProvider target) {
        if (!(source instanceof FileStorageProvider fileSource)) {
            logger.warn("[VelocityNavigator] Automatic migration is only supported from file-based storage.");
            return;
        }
        Path dataDir = fileSource.dataDir;
        boolean hasAffinity = Files.exists(dataDir.resolve("player_affinity.json"));
        boolean hasAuth = Files.exists(dataDir.resolve("player_auth.json"));
        if (!hasAffinity && !hasAuth) {
            return;
        }
        logger.info("[VelocityNavigator] Storage migration started (file -> {}).", describeTarget(target));
        migrateFileData(fileSource, target);
        logger.info("[VelocityNavigator] Storage migration completed successfully.");
    }

    public static boolean hasMigratableFileData(FileStorageProvider source) {
        Path dataDir = source.dataDir;
        return Files.exists(dataDir.resolve("player_affinity.json"))
                || Files.exists(dataDir.resolve("player_auth.json"));
    }

    private static String describeTarget(StorageProvider target) {
        if (target instanceof SqliteStorageProvider) return "sqlite";
        if (target instanceof SqlStorageProvider) return "sql";
        return target.getClass().getSimpleName();
    }

    private void migrateFileData(FileStorageProvider source, StorageProvider target) {
        Path dataDir = source.dataDir;
        Path affinityFile = dataDir.resolve("player_affinity.json");
        Path authFile = dataDir.resolve("player_auth.json");

        int affinityCount = 0;
        int authCount = 0;

        if (Files.exists(affinityFile)) {
            try {
                String json = Files.readString(affinityFile, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                for (String key : obj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject entry = obj.getAsJsonObject(key);
                        String server = entry.get("server").getAsString();
                        Instant expiry = entry.has("expires_at") ? Instant.ofEpochMilli(entry.get("expires_at").getAsLong()) : null;
                        
                        target.saveAffinity(uuid, server, expiry);
                        affinityCount++;
                    } catch (Exception e) {
                        logger.error("Failed to migrate affinity for {}: {}", key, e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to read affinity file during migration: {}", e.getMessage());
            }
        }
        
        if (Files.exists(authFile)) {
            try {
                String json = Files.readString(authFile, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                for (String key : obj.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        JsonObject entry = obj.getAsJsonObject(key);
                        String hash = entry.get("hash").getAsString();
                        String salt = entry.get("salt").getAsString();
                        
                        target.saveCredentials(uuid, hash, salt);
                        if (entry.has("totp")) {
                            target.saveTwoFactorSecret(uuid, entry.get("totp").getAsString());
                        }
                        if (entry.has("ip")) {
                            target.saveLastKnownIp(uuid, entry.get("ip").getAsString());
                        }
                        authCount++;
                    } catch (Exception e) {
                        logger.error("Failed to migrate auth for {}: {}", key, e.getMessage());
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to read auth file during migration: {}", e.getMessage());
            }
        }
        
        logger.info("[VelocityNavigator] Migrated {} affinity records and {} auth records.", affinityCount, authCount);
    }
}
