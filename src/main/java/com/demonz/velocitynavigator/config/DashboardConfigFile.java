/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record DashboardConfigFile(
        boolean enabled,
        String bindAddress,
        int port,
        String secretToken,
        boolean prometheusEnabled,
        int prometheusPort
) {

    public static DashboardConfigFile defaults() {
        return new DashboardConfigFile(true, "0.0.0.0", 8080, "change-me-secret-token", true, 9100);
    }

    public static DashboardConfigFile load(Path path, boolean mainEnabled) {
        if (!Files.exists(path)) {
            DashboardConfigFile def = new DashboardConfigFile(mainEnabled, "0.0.0.0", 8080, "change-me-secret-token", true, 9100);
            save(path, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(path.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            String bind = toml.getString("settings.bind_address", "0.0.0.0");
            int port = Math.toIntExact(toml.getLong("settings.port", 8080L));
            String secret = toml.getString("settings.secret_token", "change-me-secret-token");
            boolean promEnabled = toml.getBoolean("prometheus.enabled", true);
            int promPort = Math.toIntExact(toml.getLong("prometheus.port", 9100L));

            return new DashboardConfigFile(enabled, bind, port, secret, promEnabled, promPort);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path path, DashboardConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator Web Observability Dashboard & Metrics Configuration\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("bind_address = \"").append(config.bindAddress()).append("\"\n");
        b.append("port = ").append(config.port()).append("\n");
        b.append("secret_token = \"").append(config.secretToken()).append("\"\n\n");

        b.append("[prometheus]\n");
        b.append("enabled = ").append(config.prometheusEnabled()).append("\n");
        b.append("port = ").append(config.prometheusPort()).append("\n");

        try {
            Files.writeString(path, b.toString());
        } catch (IOException ignored) {}
    }
}
