/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.storage;

import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteStorageProvider implements StorageProvider {
    private final Path dbFile;
    private final Logger logger;
    private Connection connection;

    public SqliteStorageProvider(Path dbFile, Logger logger) {
        this.dbFile = dbFile;
        this.logger = logger;
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                if (dbFile.getParent() != null) {
                    Files.createDirectories(dbFile.getParent());
                }
            } catch (Exception ignored) {}
            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    @Override
    public synchronized void initialize() throws Exception {
        getConnection();
        createTables();
    }

    @Override
    public synchronized void shutdown() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.warn("Failed to close SQLite connection", e);
            } finally {
                connection = null;
            }
        }
    }

    @Override
    public synchronized void saveAffinity(UUID player, String server, Instant expiry) {
        if (player == null || server == null) return;
        String sql = "INSERT INTO vn_player_affinity (player_uuid, server_id, expires_at, updated_at) VALUES (?, ?, ?, strftime('%s', 'now')) " +
                     "ON CONFLICT(player_uuid) DO UPDATE SET server_id = excluded.server_id, expires_at = excluded.expires_at, updated_at = strftime('%s', 'now')";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, server);
            if (expiry != null) ps.setLong(3, expiry.toEpochMilli());
            else ps.setNull(3, java.sql.Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save affinity to SQLite database", e);
        }
    }

    @Override
    public synchronized Optional<String> loadAffinity(UUID player) {
        if (player == null) return Optional.empty();
        String sql = "SELECT server_id, expires_at FROM vn_player_affinity WHERE player_uuid = ?";
        boolean expired = false;
        String resultServer = null;
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long exp = rs.getLong("expires_at");
                    if (!rs.wasNull() && Instant.now().isAfter(Instant.ofEpochMilli(exp))) {
                        expired = true;
                    } else {
                        resultServer = rs.getString("server_id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load affinity from SQLite database", e);
        }
        if (expired) {
            clearAffinity(player);
            return Optional.empty();
        }
        return Optional.ofNullable(resultServer);
    }

    @Override
    public synchronized void clearAffinity(UUID player) {
        if (player == null) return;
        String sql = "DELETE FROM vn_player_affinity WHERE player_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to clear affinity in SQLite database", e);
        }
    }

    @Override
    public synchronized void saveCredentials(UUID player, String passwordHash, String salt) {
        if (player == null) return;
        String sql = "INSERT INTO vn_auth_credentials (player_uuid, password_hash, salt, updated_at) VALUES (?, ?, ?, strftime('%s', 'now')) " +
                     "ON CONFLICT(player_uuid) DO UPDATE SET password_hash = excluded.password_hash, salt = excluded.salt, updated_at = strftime('%s', 'now')";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, passwordHash);
            ps.setString(3, salt);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save credentials to SQLite database", e);
        }
    }

    @Override
    public synchronized Optional<AuthRecord> loadCredentials(UUID player) {
        if (player == null) return Optional.empty();
        String sql = "SELECT player_uuid, password_hash, salt, totp_secret, last_known_ip, updated_at FROM vn_auth_credentials WHERE player_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long updated = rs.getLong("updated_at");
                    return Optional.of(new AuthRecord(
                            player,
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("totp_secret"),
                            rs.getString("last_known_ip"),
                            Instant.ofEpochSecond(updated)
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load credentials from SQLite database", e);
        }
        return Optional.empty();
    }

    @Override
    public synchronized void saveTwoFactorSecret(UUID player, String encryptedSecret) {
        if (player == null) return;
        String sql = "UPDATE vn_auth_credentials SET totp_secret = ?, updated_at = strftime('%s', 'now') WHERE player_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, encryptedSecret);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save 2FA secret to SQLite database", e);
        }
    }

    @Override
    public synchronized void saveLastKnownIp(UUID player, String ip) {
        if (player == null) return;
        String sql = "UPDATE vn_auth_credentials SET last_known_ip = ?, updated_at = strftime('%s', 'now') WHERE player_uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, ip);
            ps.setString(2, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to save last known IP to SQLite database", e);
        }
    }

    @Override
    public synchronized Optional<String> loadLastKnownIp(UUID player) {
        return loadCredentials(player).map(AuthRecord::lastKnownIp);
    }

    @Override
    public synchronized void recordConnection(UUID player, String fromServer, String toServer, Instant timestamp) {
        String sql = "INSERT INTO vn_connection_log (player_uuid, from_server, to_server, timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, fromServer);
            ps.setString(3, toServer);
            ps.setLong(4, (timestamp != null ? timestamp : Instant.now()).toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record connection log in SQLite database", e);
        }
    }

    @Override
    public synchronized void recordRouting(String server, String algorithm, int candidateCount, long latencyMs) {
        String sql = "INSERT INTO vn_routing_stats (server_id, algorithm, candidate_count, latency_ms, timestamp) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, server);
            ps.setString(2, algorithm);
            ps.setInt(3, candidateCount);
            ps.setLong(4, latencyMs);
            ps.setLong(5, Instant.now().toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to record routing stat in SQLite database", e);
        }
    }

    @Override
    public synchronized List<RoutingStat> getRoutingStats(String server, Instant from, Instant to) {
        List<RoutingStat> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT server_id, algorithm, candidate_count, latency_ms, timestamp FROM vn_routing_stats WHERE 1=1");
        if (server != null) sql.append(" AND server_id = ?");
        if (from != null) sql.append(" AND timestamp >= ?");
        if (to != null) sql.append(" AND timestamp <= ?");
        sql.append(" ORDER BY timestamp DESC LIMIT 500");

        try (PreparedStatement ps = getConnection().prepareStatement(sql.toString())) {
            int idx = 1;
            if (server != null) ps.setString(idx++, server);
            if (from != null) ps.setLong(idx++, from.toEpochMilli());
            if (to != null) ps.setLong(idx++, to.toEpochMilli());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new RoutingStat(
                            rs.getString("server_id"),
                            rs.getString("algorithm"),
                            rs.getInt("candidate_count"),
                            rs.getLong("latency_ms"),
                            Instant.ofEpochMilli(rs.getLong("timestamp"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to query routing stats from SQLite database", e);
        }
        return list;
    }

    @Override
    public synchronized long getTotalConnections(Instant from, Instant to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM vn_connection_log WHERE 1=1");
        if (from != null) sql.append(" AND timestamp >= ?");
        if (to != null) sql.append(" AND timestamp <= ?");

        try (PreparedStatement ps = getConnection().prepareStatement(sql.toString())) {
            int idx = 1;
            if (from != null) ps.setLong(idx++, from.toEpochMilli());
            if (to != null) ps.setLong(idx++, to.toEpochMilli());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.warn("Failed to get total connections count from SQLite: {}", e.getMessage());
        }
        return 0;
    }

    private void createTables() throws SQLException {
        try (var s = connection.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_player_affinity (
                    player_uuid  TEXT PRIMARY KEY,
                    server_id    TEXT NOT NULL,
                    expires_at   INTEGER NULL,
                    updated_at   INTEGER DEFAULT (strftime('%s', 'now'))
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_auth_credentials (
                    player_uuid    TEXT PRIMARY KEY,
                    password_hash  TEXT NOT NULL,
                    salt           TEXT NOT NULL,
                    totp_secret    TEXT NULL,
                    last_known_ip  TEXT NULL,
                    created_at     INTEGER DEFAULT (strftime('%s', 'now')),
                    updated_at     INTEGER DEFAULT (strftime('%s', 'now'))
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_connection_log (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid  TEXT NOT NULL,
                    from_server  TEXT,
                    to_server    TEXT NOT NULL,
                    timestamp    INTEGER DEFAULT (strftime('%s', 'now'))
                )
            """);

            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS vn_routing_stats (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    server_id       TEXT NOT NULL,
                    algorithm       TEXT NOT NULL,
                    candidate_count INTEGER NOT NULL,
                    latency_ms      INTEGER NOT NULL,
                    timestamp       INTEGER DEFAULT (strftime('%s', 'now'))
                )
            """);
        }
    }
}
