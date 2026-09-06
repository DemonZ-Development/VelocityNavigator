/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator.motd;

import com.demonz.velocitynavigator.config.MotdConfig;
import com.demonz.velocitynavigator.config.MotdConfigFile;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import net.kyori.adventure.text.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MotdManager {

    private final Path motdPath;
    private final String pluginVersion;
    private final Logger logger;
    private volatile MotdConfig config = MotdConfig.defaults();
    private volatile String lastError = "";
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    public MotdManager(Path dataDirectory) {
        this(dataDirectory, "unknown");
    }

    public MotdManager(Path dataDirectory, String pluginVersion) {
        this(dataDirectory, pluginVersion, LoggerFactory.getLogger(MotdManager.class));
    }

    public MotdManager(Path dataDirectory, String pluginVersion, Logger logger) {
        this.motdPath = dataDirectory.resolve("motd.toml");
        this.pluginVersion = pluginVersion == null || pluginVersion.isBlank() ? "unknown" : pluginVersion;
        this.logger = logger;
        reload();
    }

    public synchronized boolean reload() {
        try {
            if (!Files.exists(motdPath)) {
                return persist(MotdConfig.defaults(), null);
            }
            String source = Files.readString(motdPath);
            MotdConfig loaded = read(source);
            String formatted = MotdConfigFile.reformat(source);
            if (!formatted.equals(source)) {
                if (!read(formatted).equals(loaded)) throw new IOException("Formatting changed MOTD values");
                Path backup = motdPath.resolveSibling("motd.toml.pre-format.bak");
                if (!Files.exists(backup)) Files.copy(motdPath, backup);
                MotdConfigFile.writeAtomic(motdPath, formatted);
                logger.info("Reformatted motd.toml for narrow editors; original saved as {}", backup.getFileName());
            }
            config = loaded;
            lastError = "";
            return true;
        } catch (Exception error) {
            return fail("Could not reload motd.toml; keeping the last working MOTD", error);
        }
    }

    public synchronized boolean save() {
        try {
            String source = Files.exists(motdPath) ? Files.readString(motdPath) : null;
            if (source != null) read(source);
            return persist(config, source);
        } catch (Exception error) {
            return fail("Could not save motd.toml", error);
        }
    }

    private static MotdConfig read(String source) {
        TomlParseResult toml = Toml.parse(source);
        if (toml.hasErrors()) throw new IllegalArgumentException(toml.errors().get(0).toString());
        MotdConfig defaults = MotdConfig.defaults();
        String mode = toml.getString("mode", () -> "ROTATING").toUpperCase(Locale.ROOT);
        if (!List.of("ROTATING", "RANDOM", "SEQUENTIAL").contains(mode)) {
            throw new IllegalArgumentException("mode must be ROTATING, RANDOM, or SEQUENTIAL");
        }
        long interval = toml.getLong("rotation_interval_seconds", () -> 5L);
        if (interval < 1) throw new IllegalArgumentException("rotation_interval_seconds must be positive");
        return new MotdConfig(toml.getBoolean("enabled", () -> true), mode, interval,
                readMotds(toml, "motds", defaults.motds()),
                new MotdConfig.MaintenanceSection(toml.getBoolean("maintenance.override_motd_on_maintenance", () -> true),
                        readMotds(toml, "maintenance.motds", defaults.maintenance().motds())));
    }

    private static List<String> readMotds(TomlParseResult toml, String key, List<String> defaults) {
        List<?> values = toml.contains(key) ? toml.getArray(key).toList() : defaults;
        if (values.isEmpty() || values.stream().anyMatch(value -> !(value instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException(key + " must contain at least one non-blank text entry");
        }
        return values.stream().map(String.class::cast).toList();
    }

    private boolean persist(MotdConfig candidate, String source) throws IOException {
        String formatted = source == null ? MotdConfigFile.create(candidate) : MotdConfigFile.update(source, candidate);
        if (!read(formatted).equals(candidate)) throw new IOException("MOTD save verification failed");
        MotdConfigFile.writeAtomic(motdPath, formatted);
        config = candidate;
        lastError = "";
        return true;
    }

    private synchronized boolean change(UnaryOperator<MotdConfig> operation) {
        try {
            String source = Files.exists(motdPath) ? Files.readString(motdPath) : null;
            MotdConfig current = source == null ? config : read(source);
            return persist(operation.apply(current), source);
        } catch (Exception error) {
            return fail("MOTD change was not saved", error);
        }
    }

    private boolean fail(String message, Exception error) {
        lastError = message + ": " + error.getMessage();
        logger.warn(lastError);
        return false;
    }

    public String lastError() {
        return lastError;
    }

    public MotdConfig config() {
        return config;
    }

    public Component resolveMotd(boolean isMaintenance, String maintenanceReason, int onlinePlayers, int maxPlayers) {
        MotdConfig snapshot = config;
        boolean maintenanceOverride = isMaintenance && snapshot.maintenance().overrideMotdOnMaintenance();
        if (!snapshot.enabled() && !maintenanceOverride) {
            return null;
        }

        List<String> list = maintenanceOverride ? snapshot.maintenance().motds() : snapshot.motds();

        if (list.isEmpty()) {
            return null;
        }

        String raw;
        String mode = snapshot.mode();
        if ("RANDOM".equals(mode)) {
            raw = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        } else if ("ROTATING".equals(mode)) {
            int idx = (int) Math.floorMod((System.currentTimeMillis() / 1000L) / snapshot.rotationIntervalSeconds(), list.size());
            raw = list.get(idx);
        } else if ("SEQUENTIAL".equals(mode)) {
            int idx = Math.floorMod(currentIndex.getAndIncrement(), list.size());
            raw = list.get(idx);
        } else {
            raw = list.get(0);
        }

        String formatted = raw
            .replace("{online}", String.valueOf(onlinePlayers))
            .replace("{max}", String.valueOf(maxPlayers))
            .replace("{maintenance_reason}", "<reason>")
            .replace("{version}", "<version>");

        return MessageFormatter.render(formatted, Map.of(
                "reason", maintenanceReason == null || maintenanceReason.isBlank() ? "Maintenance in progress" : maintenanceReason,
                "version", "v" + pluginVersion));
    }

    public boolean addMotd(String newMotd) {
        return change(current -> {
            if (newMotd == null || newMotd.isBlank()) throw new IllegalArgumentException("MOTD text cannot be blank");
            List<String> updated = new ArrayList<>(current.motds());
            updated.add(newMotd);
            return new MotdConfig(current.enabled(), current.mode(), current.rotationIntervalSeconds(), List.copyOf(updated), current.maintenance());
        });
    }

    public boolean removeMotd(int index) {
        return change(current -> {
            if (index < 0 || index >= current.motds().size()) throw new IllegalArgumentException("Invalid MOTD index");
            if (current.motds().size() == 1) throw new IllegalArgumentException("Keep at least one MOTD entry");
            List<String> updated = new ArrayList<>(current.motds());
            updated.remove(index);
            return new MotdConfig(current.enabled(), current.mode(), current.rotationIntervalSeconds(), List.copyOf(updated), current.maintenance());
        });
    }

    public boolean setMode(String newMode) {
        return change(current -> {
            String mode = newMode == null ? "" : newMode.toUpperCase(Locale.ROOT);
            if (!List.of("ROTATING", "RANDOM", "SEQUENTIAL").contains(mode)) {
                throw new IllegalArgumentException("mode must be ROTATING, RANDOM, or SEQUENTIAL");
            }
            return new MotdConfig(current.enabled(), mode, current.rotationIntervalSeconds(), current.motds(), current.maintenance());
        });
    }
}
