/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class BackupUtils {

    private static final Pattern VERSION_BACKUP_PATTERN =
            Pattern.compile("^(.+)\\.v(\\d+)\\.bak$");

    private BackupUtils() {
    }

    static void cleanObsoleteBackups(Path rootDir, Path backupsDir) {
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
        } catch (IOException ignored) {
            return;
        }
        Comparator<Path> byNewest = Comparator
                .comparingLong(BackupUtils::lastModifiedMillis).reversed();
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
        } catch (IOException e) {
            return 0L;
        }
    }
}
