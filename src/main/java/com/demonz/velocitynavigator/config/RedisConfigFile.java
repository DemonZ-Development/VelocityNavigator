/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record RedisConfigFile(
        boolean enabled,
        String host,
        int port,
        String username,
        String password,
        boolean ssl,
        String channelPrefix,
        int connectTimeoutMs,
        int readTimeoutMs,
        int pollIntervalSeconds
) {

    public static RedisConfigFile defaults() {
        return new RedisConfigFile(false, "127.0.0.1", 6379, "", "", false, "velocitynavigator", 3000, 3000, 5);
    }

    public static RedisConfigFile load(Path redisPath, boolean mainEnabled) {
        if (!Files.exists(redisPath)) {
            RedisConfigFile def = new RedisConfigFile(mainEnabled, "127.0.0.1", 6379, "", "", false, "velocitynavigator", 3000, 3000, 5);
            save(redisPath, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(redisPath.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            String host = toml.getString("settings.host", "127.0.0.1");
            int port = Math.toIntExact(toml.getLong("settings.port", 6379L));
            String user = toml.getString("settings.username", "");
            String pass = toml.getString("settings.password", "");
            boolean ssl = toml.getBoolean("settings.ssl", false);
            String prefix = toml.getString("settings.channel_prefix", "velocitynavigator");
            int connTimeout = Math.toIntExact(toml.getLong("settings.connect_timeout_ms", 3000L));
            int readTimeout = Math.toIntExact(toml.getLong("settings.read_timeout_ms", 3000L));
            int pollSec = Math.toIntExact(toml.getLong("settings.poll_interval_seconds", 5L));

            return new RedisConfigFile(enabled, host, port, user, pass, ssl, prefix, connTimeout, readTimeout, pollSec);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path redisPath, RedisConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator Multi-Proxy Redis Synchronization Configuration\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("host = \"").append(config.host()).append("\"\n");
        b.append("port = ").append(config.port()).append("\n");
        b.append("username = \"").append(config.username()).append("\"\n");
        b.append("password = \"").append(config.password()).append("\"\n");
        b.append("ssl = ").append(config.ssl()).append("\n");
        b.append("channel_prefix = \"").append(config.channelPrefix()).append("\"\n");
        b.append("connect_timeout_ms = ").append(config.connectTimeoutMs()).append("\n");
        b.append("read_timeout_ms = ").append(config.readTimeoutMs()).append("\n");
        b.append("poll_interval_seconds = ").append(config.pollIntervalSeconds()).append("\n");

        try {
            Files.writeString(redisPath, b.toString());
        } catch (IOException ignored) {}
    }
}
