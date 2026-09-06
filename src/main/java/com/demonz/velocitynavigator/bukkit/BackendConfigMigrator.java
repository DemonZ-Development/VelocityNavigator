/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class BackendConfigMigrator {

    private static final int CURRENT_BACKEND_VERSION = 2;
    private static final Pattern VERSION_BACKUP_PATTERN = Pattern.compile("^(.+)\\.v(\\d+)\\.bak$");

    private BackendConfigMigrator() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void migrate(JavaPlugin plugin) {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            return;
        }

        try {
            Path configPath = configFile.toPath();
            List<String> lines = Files.readAllLines(configPath, StandardCharsets.UTF_8);

            int sourceVersion = readConfigVersion(lines);
            if (sourceVersion >= CURRENT_BACKEND_VERSION) {
                return;
            }

            Path backupsDir = plugin.getDataFolder().toPath().resolve("backups");
            Files.createDirectories(backupsDir);
            Path backupPath = backupsDir.resolve("config.yml.v" + sourceVersion + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);

            List<String> newLines = new ArrayList<>();
            newLines.add("config_version: " + CURRENT_BACKEND_VERSION);

            for (String line : lines) {
                if (line.trim().startsWith("config_version:")) {
                    continue;
                }
                if (line.trim().startsWith("bstats_plugin_id:")) {
                    continue;
                }
                newLines.add(line);

                if (line.trim().startsWith("bstats_enabled:")) {
                    newLines.add("update_check_enabled: true");
                    newLines.add("update_check_interval_minutes: 120");
                }
            }

            Files.write(configPath, newLines, StandardCharsets.UTF_8);
            plugin.getLogger().info("[VelocityNavigator] Backend config migrated from v" + sourceVersion
                    + " to v" + CURRENT_BACKEND_VERSION + ". Backup: " + backupsDir.relativize(backupPath));
            cleanObsoleteBackups(plugin.getDataFolder().toPath(), backupsDir);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[VelocityNavigator] Backend config migration failed: " + e.getMessage(), e);
        }
    }

    static int readConfigVersion(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("config_version:")) {
                String value = trimmed.substring("config_version:".length()).trim();
                if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    public static void cleanObsoleteBackups(Path rootDir, Path backupsDir) {
        if (rootDir == null) return;
        try {
            if (Files.exists(rootDir)) {
                try (Stream<Path> stream = Files.list(rootDir)) {
                    List<Path> legacyBackups = stream
                            .filter(p -> p.getFileName().toString().endsWith(".bak"))
                            .toList();

                    Files.createDirectories(backupsDir);
                    for (Path legacy : legacyBackups) {
                        Files.move(legacy, backupsDir.resolve(legacy.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (Files.exists(backupsDir)) {
                pruneObsoleteVersionBackups(backupsDir);
            }
        } catch (Exception ignored) {
        }
    }

    private static void pruneObsoleteVersionBackups(Path backupsDir) {
        Map<String, List<Path>> byBaseName = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(backupsDir)) {
            stream
                    .filter(p -> VERSION_BACKUP_PATTERN.matcher(p.getFileName().toString()).matches())
                    .forEach(p -> byBaseName
                            .computeIfAbsent(baseConfigName(p.getFileName().toString()), k -> new ArrayList<>())
                            .add(p));
        } catch (Exception ignored) {
            return;
        }
        Comparator<Path> byNewest = Comparator
                .comparingLong(BackendConfigMigrator::lastModifiedMillis).reversed();
        for (List<Path> group : byBaseName.values()) {
            group.sort(byNewest);
            for (int i = 1; i < group.size(); i++) {
                try {
                    Files.deleteIfExists(group.get(i));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String baseConfigName(String backupFileName) {
        Matcher m = VERSION_BACKUP_PATTERN.matcher(backupFileName);
        return m.matches() ? m.group(1) : backupFileName;
    }

    private static long lastModifiedMillis(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
