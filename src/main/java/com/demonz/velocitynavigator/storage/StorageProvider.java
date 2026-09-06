/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.storage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StorageProvider {
    void initialize() throws Exception;
    void shutdown();

    void saveAffinity(UUID player, String server, Instant expiry);
    Optional<String> loadAffinity(UUID player);
    void clearAffinity(UUID player);

    void saveCredentials(UUID player, String passwordHash, String salt);
    Optional<AuthRecord> loadCredentials(UUID player);
    void saveTwoFactorSecret(UUID player, String encryptedSecret);
    void saveLastKnownIp(UUID player, String ip);
    Optional<String> loadLastKnownIp(UUID player);

    void recordConnection(UUID player, String fromServer, String toServer, Instant timestamp);
    void recordRouting(String server, String algorithm, int candidateCount, long latencyMs);
    List<RoutingStat> getRoutingStats(String server, Instant from, Instant to);
    long getTotalConnections(Instant from, Instant to);

    record AuthRecord(UUID player, String passwordHash, String salt, String totpSecret, String lastKnownIp, Instant updatedAt) {}
    record RoutingStat(String server, String algorithm, int candidateCount, long latencyMs, Instant timestamp) {}
}
