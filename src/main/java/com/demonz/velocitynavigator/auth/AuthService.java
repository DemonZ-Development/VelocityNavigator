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
package com.demonz.velocitynavigator.auth;

import com.demonz.velocitynavigator.storage.StorageProvider;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final int ARGON2_MEMORY = 65536;
    private static final int ARGON2_ITERATIONS = 3;
    private static final int ARGON2_PARALLELISM = 4;
    private static final int ARGON2_HASH_LENGTH = 32;

    private final StorageProvider storage;
    private final Logger logger;
    private final String algorithm;
    private final int minPasswordLength;
    private final Duration sessionTimeout;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<UUID, Instant> authenticatedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingPins = new ConcurrentHashMap<>();

    public AuthService(StorageProvider storage, String algorithm, int minPasswordLength) {
        this(storage, NOPLogger.NOP_LOGGER, algorithm, minPasswordLength);
    }

    public AuthService(StorageProvider storage, Logger logger, String algorithm, int minPasswordLength) {
        this(storage, logger, algorithm, minPasswordLength, 60);
    }

    public AuthService(
            StorageProvider storage,
            Logger logger,
            String algorithm,
            int minPasswordLength,
            int sessionTimeoutMinutes
    ) {
        this.storage = storage;
        this.logger = logger != null ? logger : NOPLogger.NOP_LOGGER;
        this.algorithm = algorithm == null || algorithm.isBlank() ? "argon2id" : algorithm.trim().toLowerCase(java.util.Locale.ROOT);
        this.minPasswordLength = Math.max(4, minPasswordLength);
        this.sessionTimeout = Duration.ofMinutes(Math.max(1, sessionTimeoutMinutes));
    }

    public boolean isEnabled() {
        return storage != null;
    }

    public boolean isRegistered(UUID player) {
        if (storage == null) return false;
        return storage.loadCredentials(player).isPresent();
    }

    public boolean isAuthenticated(UUID player) {
        Instant expiresAt = authenticatedPlayers.get(player);
        if (expiresAt == null) return false;
        if (Instant.now().isAfter(expiresAt)) {
            authenticatedPlayers.remove(player, expiresAt);
            pendingPins.remove(player);
            return false;
        }
        return true;
    }

    public boolean register(UUID player, String rawPassword) {
        if (storage == null || isRegistered(player) || rawPassword == null
                || rawPassword.length() < minPasswordLength || rawPassword.length() > 128) {
            return false;
        }
        storePassword(player, rawPassword);
        openSession(player);
        return true;
    }

    public boolean authenticate(UUID player, String rawPassword) {
        if (storage == null || rawPassword == null || rawPassword.length() > 128) {
            return false;
        }
        Optional<StorageProvider.AuthRecord> record = storage.loadCredentials(player);
        if (record.isEmpty()) {
            return false;
        }
        StorageProvider.AuthRecord auth = record.get();
        String storedHash = auth.passwordHash();
        String salt = auth.salt();
        if (storedHash == null || salt == null) return false;
        if (storedHash.startsWith("$argon2")) {
            if (!verifyArgon2(rawPassword, salt, storedHash)) {
                return false;
            }
        } else {
            String computed = hashSha256(rawPassword, salt);
            if (!MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
            if ("argon2id".equals(algorithm)) storePassword(player, rawPassword);
        }
        openSession(player);
        return true;
    }

    public void deauthenticate(UUID player) {
        authenticatedPlayers.remove(player);
        pendingPins.remove(player);
    }

    public void transferSessionsFrom(AuthService previous) {
        if (previous == null) {
            return;
        }
        authenticatedPlayers.putAll(previous.authenticatedPlayers);
        pendingPins.putAll(previous.pendingPins);
    }

    private void openSession(UUID player) {
        authenticatedPlayers.put(player, Instant.now().plus(sessionTimeout));
    }

    private String hashPassword(String rawPassword, String saltBase64) {
        if ("argon2id".equals(algorithm)) {
            return hashArgon2(rawPassword, saltBase64);
        }
        return hashSha256(rawPassword, saltBase64);
    }

    private void storePassword(UUID player, String rawPassword) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        storage.saveCredentials(player, hashPassword(rawPassword, saltBase64), saltBase64);
    }

    private String hashArgon2(String rawPassword, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            byte[] hash = argon2Bytes(rawPassword, salt, ARGON2_MEMORY, ARGON2_ITERATIONS, ARGON2_PARALLELISM, ARGON2_HASH_LENGTH);
            return "$argon2id$v=19$m=" + ARGON2_MEMORY + ",t=" + ARGON2_ITERATIONS + ",p=" + ARGON2_PARALLELISM + "$" + saltBase64 + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Failed to hash password with Argon2id", e);
            throw new RuntimeException("Failed to hash password with Argon2id", e);
        }
    }

    private boolean verifyArgon2(String rawPassword, String saltBase64, String storedHash) {
        try {
            if (storedHash.length() > 1024 || saltBase64.length() > 128) return false;
            String[] fields = storedHash.split("\\$", -1);
            if (fields.length != 6 || !fields[0].isEmpty() || !"argon2id".equals(fields[1]) || !"v=19".equals(fields[2])) return false;
            Map<String, Integer> costs = new HashMap<>();
            for (String parameter : fields[3].split(",", -1)) {
                String[] value = parameter.split("=", -1);
                if (value.length != 2 || costs.putIfAbsent(value[0], Integer.parseInt(value[1])) != null) return false;
            }
            if (!costs.keySet().equals(Set.of("m", "t", "p"))) return false;
            int memory = costs.get("m");
            int iterations = costs.get("t");
            int parallelism = costs.get("p");
            if (parallelism < 1 || parallelism > 16 || memory < 8 * parallelism || memory > 262144
                    || iterations < 1 || iterations > 10) return false;
            byte[] salt = Base64.getDecoder().decode(fields[4]);
            byte[] expected = Base64.getDecoder().decode(fields[5]);
            if (salt.length < 8 || salt.length > 64 || expected.length < 16 || expected.length > 64
                    || !MessageDigest.isEqual(salt, Base64.getDecoder().decode(saltBase64))) return false;
            return MessageDigest.isEqual(expected, argon2Bytes(rawPassword, salt, memory, iterations, parallelism, expected.length));
        } catch (RuntimeException e) {
            logger.debug("Invalid Argon2id credential record", e);
            return false;
        }
    }

    private byte[] argon2Bytes(String rawPassword, byte[] salt, int memory, int iterations, int parallelism, int length) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] hash = new byte[length];
        generator.generateBytes(rawPassword.getBytes(StandardCharsets.UTF_8), hash);
        return hash;
    }

    private String hashSha256(String rawPassword, String saltBase64) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = (saltBase64 + ":" + rawPassword).getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 10000; i++) {
                digest.reset();
                hashed = digest.digest(hashed);
            }
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            logger.error("Failed to hash password", e);
            throw new RuntimeException("Failed to hash password", e);
        }
    }
}
