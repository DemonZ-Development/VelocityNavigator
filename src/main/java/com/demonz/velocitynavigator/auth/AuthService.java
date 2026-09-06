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
import java.util.Map;
import java.util.Optional;
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
        this.algorithm = algorithm == null || algorithm.isBlank() ? "argon2id" : algorithm.toLowerCase(java.util.Locale.ROOT);
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
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        String saltBase64 = Base64.getEncoder().encodeToString(salt);
        String hash = hashPassword(rawPassword, saltBase64);
        storage.saveCredentials(player, hash, saltBase64);
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
        if (storedHash.startsWith("$argon2")) {
            if (!verifyArgon2(rawPassword, salt, storedHash)) {
                return false;
            }
        } else {
            String computed = hashPassword(rawPassword, salt);
            if (!MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
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

    private String hashArgon2(String rawPassword, String saltBase64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withMemoryAsKB(ARGON2_MEMORY)
                    .withIterations(ARGON2_ITERATIONS)
                    .withParallelism(ARGON2_PARALLELISM)
                    .withSalt(salt)
                    .build();
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            byte[] hash = new byte[ARGON2_HASH_LENGTH];
            generator.generateBytes(rawPassword.getBytes(StandardCharsets.UTF_8), hash);
            return "$argon2id$v=19$m=" + ARGON2_MEMORY + ",t=" + ARGON2_ITERATIONS + ",p=" + ARGON2_PARALLELISM + "$" + saltBase64 + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("Failed to hash password with Argon2id", e);
            throw new RuntimeException("Failed to hash password with Argon2id", e);
        }
    }

    private boolean verifyArgon2(String rawPassword, String saltBase64, String storedHash) {
        try {
            String recomputed = hashArgon2(rawPassword, saltBase64);
            return MessageDigest.isEqual(recomputed.getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Failed to verify Argon2id password", e);
            return false;
        }
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
