/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record GeoConfigFile(
        boolean enabled,
        String databasePath,
        String provider,
        boolean fallbackEnabled,
        String fallbackMode
) {

    public static GeoConfigFile defaults() {
        return new GeoConfigFile(false, "GeoLite2-Country.mmdb", "MAXMIND", true, "DEFAULT_LOBBY");
    }

    public static GeoConfigFile load(Path path, boolean mainEnabled) {
        if (!Files.exists(path)) {
            GeoConfigFile def = new GeoConfigFile(mainEnabled, "GeoLite2-Country.mmdb", "MAXMIND", true, "DEFAULT_LOBBY");
            save(path, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(path.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            String db = toml.getString("settings.database_path", "GeoLite2-Country.mmdb");
            String provider = toml.getString("settings.provider", "MAXMIND");
            boolean fallbackEnabled = toml.getBoolean("settings.fallback_enabled", true);
            String fallbackMode = toml.getString("settings.fallback_mode", "DEFAULT_LOBBY");

            return new GeoConfigFile(enabled, db, provider, fallbackEnabled, fallbackMode);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path path, GeoConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator Geo-IP Location Routing Configuration\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("database_path = \"").append(config.databasePath()).append("\"\n");
        b.append("provider = \"").append(config.provider()).append("\"\n");
        b.append("fallback_enabled = ").append(config.fallbackEnabled()).append("\n");
        b.append("fallback_mode = \"").append(config.fallbackMode()).append("\"\n");

        try {
            Files.writeString(path, b.toString());
        } catch (IOException ignored) {}
    }
}
