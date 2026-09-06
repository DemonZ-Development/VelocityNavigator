/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record AuthConfigFile(
        boolean enabled,
        boolean useSignGui,
        String algorithm,
        int minPasswordLength,
        int sessionTimeoutMinutes,
        String holdingServer,
        int timeoutSeconds,
        String timeoutAction,
        boolean darknessEffectEnabled,
        boolean restrictMovement,
        boolean restrictDamageTaken,
        boolean restrictDamageDealt,
        boolean restrictBlockBreak,
        boolean restrictBlockPlace,
        boolean restrictItemDrop,
        boolean restrictItemPickup,
        boolean restrictChat,
        boolean restrictCommands,
        List<String> allowedCommands
) {

    public static AuthConfigFile defaults() {
        return new AuthConfigFile(
                true,
                true,
                "Argon2id",
                6,
                60,
                "auth-holding",
                60,
                "KICK",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of("/login", "/register", "/l", "/reg")
        );
    }

    public static AuthConfigFile load(Path authPath, boolean mainEnabled) {
        if (!Files.exists(authPath)) {
            AuthConfigFile def = new AuthConfigFile(
                    mainEnabled,
                    true,
                    "Argon2id", 6, 60, "auth-holding", 60, "KICK",
                    true, true, true, true, true, true, true, true, true, true,
                    List.of("/login", "/register", "/l", "/reg")
            );
            save(authPath, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(authPath.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            boolean useSignGui = toml.getBoolean("settings.use_sign_gui", true);
            String algorithm = toml.getString("settings.algorithm", "Argon2id");
            int minPass = Math.toIntExact(toml.getLong("settings.min_password_length", 6L));
            int sessionTimeout = Math.toIntExact(toml.getLong("settings.session_timeout_minutes", 60L));
            String holdingServer = toml.getString("settings.holding_server", "auth-holding");
            int timeoutSec = Math.toIntExact(toml.getLong("settings.timeout_seconds", 60L));
            String timeoutAction = toml.getString("settings.timeout_action", "KICK");

            boolean darkness = toml.getBoolean("restrictions.darkness_effect_enabled", true);
            boolean movement = toml.getBoolean("restrictions.restrict_movement", true);
            boolean damageTaken = toml.getBoolean("restrictions.restrict_damage_taken", true);
            boolean damageDealt = toml.getBoolean("restrictions.restrict_damage_dealt", true);
            boolean blockBreak = toml.getBoolean("restrictions.restrict_block_break", true);
            boolean blockPlace = toml.getBoolean("restrictions.restrict_block_place", true);
            boolean itemDrop = toml.getBoolean("restrictions.restrict_item_drop", true);
            boolean itemPickup = toml.getBoolean("restrictions.restrict_item_pickup", true);
            boolean chat = toml.getBoolean("restrictions.restrict_chat", true);
            boolean commands = toml.getBoolean("restrictions.restrict_commands", true);
            List<String> allowedCmds = toml.getList("restrictions.allowed_commands", List.of("/login", "/register", "/l", "/reg"));

            return new AuthConfigFile(
                    enabled, useSignGui, algorithm, minPass, sessionTimeout, holdingServer, timeoutSec, timeoutAction,
                    darkness, movement, damageTaken, damageDealt, blockBreak, blockPlace, itemDrop, itemPickup, chat, commands, allowedCmds
            );
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path authPath, AuthConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# ==========================================================================\n");
        b.append("# VelocityNavigator Authentication Engine Configuration\n");
        b.append("# ==========================================================================\n");
        b.append("# TUTORIAL GUIDE:\n");
        b.append("# - enabled: Set to true to require authentication before entering lobby routing.\n");
        b.append("# - use_sign_gui: When true, opens an interactive Sign Board GUI on join for\n");
        b.append("#   typing passwords privately. Standard /login and /register chat commands remain available as fallbacks.\n");
        b.append("# - algorithm: Password hashing algorithm (\"Argon2id\" or \"SHA256\").\n");
        b.append("# - holding_server: Backend server name where unauthenticated players are held.\n");
        b.append("# - darkness_effect_enabled: Applies blindness potion effect while unauthenticated.\n");
        b.append("# - restrictions: Granular locks for movement, damage, blocks, chat, and commands.\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("# Enables or disables player authentication.\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("# Automatically opens interactive Sign GUI for private password entry.\n");
        b.append("use_sign_gui = ").append(config.useSignGui()).append("\n");
        b.append("# Hashing algorithm: Argon2id (recommended) or SHA256.\n");
        b.append("algorithm = \"").append(config.algorithm()).append("\"\n");
        b.append("# Minimum characters required for new passwords.\n");
        b.append("min_password_length = ").append(config.minPasswordLength()).append("\n");
        b.append("# Session duration in minutes before re-authentication is required.\n");
        b.append("session_timeout_minutes = ").append(config.sessionTimeoutMinutes()).append("\n");
        b.append("# Name of the backend server registered in Velocity used as holding area.\n");
        b.append("holding_server = \"").append(config.holdingServer()).append("\"\n");
        b.append("# Maximum time in seconds allowed to authenticate before timeout action is triggered.\n");
        b.append("timeout_seconds = ").append(config.timeoutSeconds()).append("\n");
        b.append("# Timeout action: KICK or DISCONNECT.\n");
        b.append("timeout_action = \"").append(config.timeoutAction()).append("\"\n\n");

        b.append("[restrictions]\n");
        b.append("# Applies blindness darkness potion effect to pending auth players.\n");
        b.append("darkness_effect_enabled = ").append(config.darknessEffectEnabled()).append("\n");
        b.append("# Locks player location until authenticated.\n");
        b.append("restrict_movement = ").append(config.restrictMovement()).append("\n");
        b.append("# Prevents player from taking damage while pending auth.\n");
        b.append("restrict_damage_taken = ").append(config.restrictDamageTaken()).append("\n");
        b.append("# Prevents player from dealing damage while pending auth.\n");
        b.append("restrict_damage_dealt = ").append(config.restrictDamageDealt()).append("\n");
        b.append("# Prevents block breaking while pending auth.\n");
        b.append("restrict_block_break = ").append(config.restrictBlockBreak()).append("\n");
        b.append("# Prevents block placing while pending auth.\n");
        b.append("restrict_block_place = ").append(config.restrictBlockPlace()).append("\n");
        b.append("# Prevents item dropping while pending auth.\n");
        b.append("restrict_item_drop = ").append(config.restrictItemDrop()).append("\n");
        b.append("# Prevents item pickup while pending auth.\n");
        b.append("restrict_item_pickup = ").append(config.restrictItemPickup()).append("\n");
        b.append("# Restricts public chat messages while pending auth.\n");
        b.append("restrict_chat = ").append(config.restrictChat()).append("\n");
        b.append("# Restricts non-whitelisted commands while pending auth.\n");
        b.append("restrict_commands = ").append(config.restrictCommands()).append("\n");
        b.append("# Whitelisted commands allowed during pending auth.\n");
        b.append("allowed_commands = [");
        for (int i = 0; i < config.allowedCommands().size(); i++) {
            b.append("\"").append(config.allowedCommands().get(i)).append("\"");
            if (i < config.allowedCommands().size() - 1) b.append(", ");
        }
        b.append("]\n");

        try {
            Files.writeString(authPath, b.toString());
        } catch (IOException ignored) {}
    }
}
