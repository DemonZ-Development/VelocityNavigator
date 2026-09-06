/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record BedrockConfigFile(
        boolean enabled,
        String floodgatePrefix,
        boolean stripAdvancedFormatting,
        boolean openFormsAutomatically
) {

    public static BedrockConfigFile defaults() {
        return new BedrockConfigFile(true, ".", false, true);
    }

    public static BedrockConfigFile load(Path bedrockPath, boolean mainEnabled) {
        if (!Files.exists(bedrockPath)) {
            BedrockConfigFile def = new BedrockConfigFile(mainEnabled, ".", false, true);
            save(bedrockPath, def);
            return def;
        }

        try {
            Toml toml = new Toml().read(bedrockPath.toFile());
            boolean enabled = toml.getBoolean("settings.enabled", mainEnabled);
            String prefix = toml.getString("settings.floodgate_prefix", ".");
            boolean strip = toml.getBoolean("settings.strip_advanced_formatting", false);
            boolean openForms = toml.getBoolean("settings.open_forms_automatically", true);

            return new BedrockConfigFile(enabled, prefix, strip, openForms);
        } catch (Exception e) {
            return defaults();
        }
    }

    public static void save(Path bedrockPath, BedrockConfigFile config) {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator Bedrock & Geyser/Floodgate Configuration\n");
        b.append("# DO NOT CHANGE THE VERSION BELOW\n");
        b.append("version = 1\n\n");

        b.append("[settings]\n");
        b.append("enabled = ").append(config.enabled()).append("\n");
        b.append("floodgate_prefix = \"").append(config.floodgatePrefix()).append("\"\n");
        b.append("strip_advanced_formatting = ").append(config.stripAdvancedFormatting()).append("\n");
        b.append("open_forms_automatically = ").append(config.openFormsAutomatically()).append("\n");

        try {
            Files.writeString(bedrockPath, b.toString());
        } catch (IOException ignored) {}
    }
}
