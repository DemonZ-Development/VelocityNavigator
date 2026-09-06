/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.storage;

import org.slf4j.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlStorageProvider implements StorageProvider {
    private final HikariConnectionPool pool;
    private final Logger logger;
    private final boolean isPostgres;

    public SqlStorageProvider(HikariConnectionPool pool, Logger logger, boolean isPostgres) {
        this.pool = pool;
        this.logger = logger;
        this.isPostgres = isPostgres;
    }

    @Override
    public void initialize() throws Exception {
        try (Connection conn = pool.getConnection()) {
            createTables(conn);
        }
    }

    @Override
    public void shutdown() {
        pool.close();
    }

    @Override
    public void saveAffinity(UUID player, String server, Instant expiry) {
        if (player == null || server == null) return;
        String sql = isPostgres
                ? "INSERT INTO vn_player_affinity (player_uuid, server_id, expires_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                  "ON CONFLICT (player_uuid) DO UPDATE SET server_id = EXCLUDED.server_id, expires_at = EXCLUDED.expires_at, updated_at = CURRENT_TIMESTAMP"
                : "REPLACE INTO vn_player_affinity (player_uuid, server_id, expires_at, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, server);
            ps.setTimestamp(3, expiry != null ? Timestamp.from(expiry) : null);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save affinity to SQL database", e);
        }
    }

    @Override
    public Optional<String> loadAffinity(UUID player) {
        if (player == null) return Optional.empty();
        String sql = "SELECT server_id, expires_at FROM vn_player_affinity WHERE player_uuid = ?";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp exp = rs.getTimestamp("expires_at");
                    if (exp != null && Instant.now().isAfter(exp.toInstant())) {
                        clearAffinity(player);
                        return Optional.empty();
                    }
                    return Optional.of(rs.getString("server_id"));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load affinity from SQL database", e);
        }
        return Optional.empty();
    }

    @Override
    public void clearAffinity(UUID player) {
        if (player == null) return;
        String sql = "DELETE FROM vn_player_affinity WHERE player_uuid = ?";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to clear affinity in SQL database", e);
        }
    }

    @Override
    public void saveCredentials(UUID player, String passwordHash, String salt) {
        if (player == null) return;
        String sql = isPostgres
                ? "INSERT INTO vn_auth_credentials (player_uuid, password_hash, salt, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                  "ON CONFLICT (player_uuid) DO UPDATE SET password_hash = EXCLUDED.password_hash, salt = EXCLUDED.salt, updated_at = CURRENT_TIMESTAMP"
                : "INSERT INTO vn_auth_credentials (player_uuid, password_hash, salt, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                  "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), salt = VALUES(salt), updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save credentials to SQL database", e);
        }
    }

    @Override
    public Optional<AuthRecord> loadCredentials(UUID player) {
        if (player == null) return Optional.empty();
        String sql = "SELECT player_uuid, password_hash, salt, totp_secret, last_known_ip, updated_at FROM vn_auth_credentials WHERE player_uuid = ?";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return Optional.of(new AuthRecord(
                            player,
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("totp_secret"),
                            rs.getString("last_known_ip"),
                            updated != null ? updated.toInstant() : Instant.now()
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load credentials from SQL database", e);
        }
        return Optional.empty();
    }

    @Override
    public void saveTwoFactorSecret(UUID player, String encryptedSecret) {
        if (player == null) return;
        String sql = "UPDATE vn_auth_credentials SET totp_secret = ?, updated_at = CURRENT_TIMESTAMP WHERE player_uuid = ?";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, encryptedSecret);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save 2FA secret to SQL database", e);
        }
    }

    @Override
    public void saveLastKnownIp(UUID player, String ip) {
        if (player == null) return;
        String sql = "UPDATE vn_auth_credentials SET last_known_ip = ?, updated_at = CURRENT_TIMESTAMP WHERE player_uuid = ?";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save last known IP to SQL database", e);
        }
    }

    @Override
    public Optional<String> loadLastKnownIp(UUID player) {
        return loadCredentials(player).map(AuthRecord::lastKnownIp);
    }

    @Override
    public void recordConnection(UUID player, String fromServer, String toServer, Instant timestamp) {
        String sql = "INSERT INTO vn_connection_log (player_uuid, from_server, to_server, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, fromServer);
            ps.setString(3, toServer);
            ps.setTimestamp(4, Timestamp.from(timestamp != null ? timestamp : Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record connection log in SQL database", e);
        }
    }

    @Override
    public void recordRouting(String server, String algorithm, int candidateCount, long latencyMs) {
        String sql = "INSERT INTO vn_routing_stats (server_id, algorithm, candidate_count, latency_ms, timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, server);
            ps.setString(2, algorithm);
            ps.setInt(3, candidateCount);
            ps.setLong(4, latencyMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record routing stat in SQL database", e);
        }
    }

    @Override
    public List<RoutingStat> getRoutingStats(String server, Instant from, Instant to) {
        List<RoutingStat> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT server_id, algorithm, candidate_count, latency_ms, timestamp FROM vn_routing_stats WHERE 1=1");
        if (server != null) sql.append(" AND server_id = ?");
        if (from != null) sql.append(" AND timestamp >= ?");
        if (to != null) sql.append(" AND timestamp <= ?");
        sql.append(" ORDER BY timestamp DESC LIMIT 500");

        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (server != null) ps.setString(idx++, server);
            if (from != null) ps.setTimestamp(idx++, Timestamp.from(from));
            if (to != null) ps.setTimestamp(idx++, Timestamp.from(to));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RoutingStat(
                            rs.getString("server_id"),
                            rs.getString("algorithm"),
                            rs.getInt("candidate_count"),
                            rs.getLong("latency_ms"),
                            rs.getTimestamp("timestamp").toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to query routing stats from SQL database", e);
        }
        return list;
    }

    @Override
    public long getTotalConnections(Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM vn_connection_log WHERE 1=1");
        if (from != null) sql.append(" AND timestamp >= ?");
        if (to != null) sql.append(" AND timestamp <= ?");

        try (Connection conn = pool.getConnection(); PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (from != null) ps.setTimestamp(idx++, Timestamp.from(from));
            if (to != null) ps.setTimestamp(idx++, Timestamp.from(to));

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warn("Failed to get total connections count", e);
        }
        return 0;
    }

    private void createTables(Connection conn) throws SQLException {
        try (var s = conn.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_player_affinity (
                    player_uuid  VARCHAR(36) PRIMARY KEY,
                    server_id    VARCHAR(64) NOT NULL,
                    expires_at   TIMESTAMP NULL,
                    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_auth_credentials (
                    player_uuid    VARCHAR(36) PRIMARY KEY,
                    password_hash  VARCHAR(128) NOT NULL,
                    salt           VARCHAR(64) NOT NULL,
                    totp_secret    VARCHAR(256) NULL,
                    last_known_ip  VARCHAR(45) NULL,
                    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            String autoInc = isPostgres ? "BIGSERIAL PRIMARY KEY" : "BIGINT AUTO_INCREMENT PRIMARY KEY";
            s.executeUpdate("CREATE TABLE IF NOT EXISTS vn_connection_log ("
                    + "id " + autoInc + ", "
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "from_server VARCHAR(64), "
                    + "to_server VARCHAR(64) NOT NULL, "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS vn_routing_stats ("
                    + "id " + autoInc + ", "
                    + "server_id VARCHAR(64) NOT NULL, "
                    + "algorithm VARCHAR(32) NOT NULL, "
                    + "candidate_count INT NOT NULL, "
                    + "latency_ms BIGINT NOT NULL, "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }
    }
}
