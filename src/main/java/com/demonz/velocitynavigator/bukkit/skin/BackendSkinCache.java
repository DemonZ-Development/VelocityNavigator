/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.skin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BackendSkinCache {

    private static final long NEGATIVE_TTL_MS = 5 * 60_000L;

    private final Path skinCacheDir;
    private final Logger logger;
    private final ConcurrentMap<String, SkinData> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> recentFailures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Optional<SkinData>>> inFlight = new ConcurrentHashMap<>();

    public BackendSkinCache(Path dataDir, Logger logger) {
        this.logger = logger;
        this.skinCacheDir = dataDir != null ? dataDir.resolve("skins") : null;
        if (skinCacheDir != null && !Files.exists(skinCacheDir)) {
            try {
                Files.createDirectories(skinCacheDir);
            } catch (Exception e) {
                this.logger.log(Level.WARNING, "[VelocityNavigator] Failed to create skin cache directory: " + e.getMessage(), e);
            }
        }
    }

    public CompletableFuture<Optional<SkinData>> fetchSkinByUsername(String username) {
        if (username == null || username.isBlank()) return CompletableFuture.completedFuture(Optional.empty());
        String lower = username.toLowerCase(Locale.ROOT);
        CompletableFuture<Optional<SkinData>> existing = inFlight.get(lower);
        if (existing != null) return existing;

        Long failedAt = recentFailures.get(lower);
        if (failedAt != null && System.currentTimeMillis() - failedAt < NEGATIVE_TTL_MS) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return inFlight.computeIfAbsent(lower, key -> {
            CompletableFuture<Optional<SkinData>> future = CompletableFuture.supplyAsync(() -> doFetch(key));
            future.whenComplete((result, error) -> {
                if (result == null || result.isEmpty()) {
                    recentFailures.put(key, System.currentTimeMillis());
                } else {
                    recentFailures.remove(key);
                }
                inFlight.remove(key, future);
            });
            return future;
        });
    }

    private Optional<SkinData> doFetch(String lower) {
        if (cache.containsKey(lower)) {
            return Optional.of(cache.get(lower));
        }

        if (skinCacheDir != null) {
            Path file = skinCacheDir.resolve(lower + ".skin");
            if (Files.exists(file)) {
                try {
                    String[] lines = Files.readString(file).split("\n");
                    if (lines.length >= 3) {
                        UUID cachedUuid = formatUuid(lines[0].trim());
                        if (cachedUuid == null) return Optional.empty();
                        SkinData data = new SkinData(cachedUuid, lines[1].trim(), lines[2].trim());
                        cache.put(lower, data);
                        return Optional.of(data);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "[VelocityNavigator] Failed to read skin cache for " + lower + ": " + e.getMessage(), e);
                }
            }
        }

        HttpURLConnection conn = null;
        HttpURLConnection pConn = null;
        try {
            URL sessionUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + lower);
            conn = (HttpURLConnection) sessionUrl.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    String jsonStr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    int idIdx = jsonStr.indexOf("\"id\":\"");
                    if (idIdx != -1) {
                        String uuidStr = jsonStr.substring(idIdx + 6, jsonStr.indexOf("\"", idIdx + 6));
                        UUID resolvedUuid = formatUuid(uuidStr);
                        URL profileUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false");
                        pConn = (HttpURLConnection) profileUrl.openConnection();
                        pConn.setConnectTimeout(2000);
                        pConn.setReadTimeout(2000);
                        if (pConn.getResponseCode() == 200) {
                            try (InputStream pIn = pConn.getInputStream()) {
                                String pJson = new String(pIn.readAllBytes(), StandardCharsets.UTF_8);
                                int valIdx = pJson.indexOf("\"value\":\"");
                                int sigIdx = pJson.indexOf("\"signature\":\"");
                                if (valIdx != -1 && sigIdx != -1) {
                                    String val = pJson.substring(valIdx + 9, pJson.indexOf("\"", valIdx + 9));
                                    String sig = pJson.substring(sigIdx + 13, pJson.indexOf("\"", sigIdx + 13));
                                    SkinData data = new SkinData(resolvedUuid, val, sig);
                                    cache.put(lower, data);
                                    if (skinCacheDir != null) {
                                        try {
                                            Files.writeString(skinCacheDir.resolve(lower + ".skin"), uuidStr + "\n" + val + "\n" + sig);
                                        } catch (Exception e) {
                                            logger.log(Level.WARNING, "[VelocityNavigator] Failed to write skin cache for " + lower + ": " + e.getMessage(), e);
                                        }
                                    }
                                    return Optional.of(data);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[VelocityNavigator] Mojang skin lookup failed for " + lower + ": " + e.getMessage(), e);
        } finally {
            if (pConn != null) pConn.disconnect();
            if (conn != null) conn.disconnect();
        }

        return Optional.empty();
    }

    private static UUID formatUuid(String dashed) {
        if (dashed == null || dashed.isBlank()) return null;
        String trimmed = dashed.trim();
        if (trimmed.contains("-")) {
            try {
                return UUID.fromString(trimmed);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (trimmed.length() != 32) return null;
        try {
            return UUID.fromString(trimmed.substring(0, 8) + "-" + trimmed.substring(8, 12) + "-"
                    + trimmed.substring(12, 16) + "-" + trimmed.substring(16, 20) + "-" + trimmed.substring(20));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record SkinData(UUID uuid, String value, String signature) {
    }
}
