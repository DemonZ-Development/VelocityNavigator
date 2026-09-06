/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record PartyConfigFile(
        boolean enabled,
        int maxSize,
        int inviteTimeoutSeconds,
        boolean followLeader,
        String prefix
) {

    public static PartyConfigFile defaults() {
        return new PartyConfigFile(true, 20, 60, true, "[Party]");
    }

    public static PartyConfigFile load(Path partyPath, boolean mainEnabled) {
        if (!Files.exists(partyPath)) {
            PartyConfigFile def = new PartyConfigFile(mainEnabled, 20, 60, true, "[Party]");
            save(partyPath, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(partyPath.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            int maxSize = Math.toIntExact(toml.getLong("settings.max_size", 20L));
            int inviteTimeout = Math.toIntExact(toml.getLong("settings.invite_timeout_seconds", 60L));
            boolean followLeader = toml.getBoolean("settings.follow_leader", true);
            String prefix = toml.getString("placeholders.prefix", "[Party]");

            return new PartyConfigFile(enabled, maxSize, inviteTimeout, followLeader, prefix);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path partyPath, PartyConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator Party & Team System Configuration\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("max_size = ").append(config.maxSize()).append("\n");
        b.append("invite_timeout_seconds = ").append(config.inviteTimeoutSeconds()).append("\n");
        b.append("follow_leader = ").append(config.followLeader()).append("\n\n");

        b.append("[placeholders]\n");
        b.append("prefix = \"").append(config.prefix()).append("\"\n");

        try {
            Files.writeString(partyPath, b.toString());
        } catch (IOException ignored) {}
    }
}
