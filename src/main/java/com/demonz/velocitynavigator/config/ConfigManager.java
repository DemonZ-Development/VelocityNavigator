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
package com.demonz.velocitynavigator.config;
import com.demonz.velocitynavigator.locale.LanguageBundle;
import com.demonz.velocitynavigator.locale.LanguagePacks;
import com.demonz.velocitynavigator.menu.MenuServerState;

import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.EnumMap;

public final class ConfigManager {

    private static final int GUI_CONFIG_VERSION = 2;

    private final Path dataDirectory;
    private final Path configPath;
    private final Path messagesPath;
    private final Path guiPath;
    private final Logger logger;
    private volatile GuiConfig guiConfig = GuiConfig.defaults();

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.configPath = dataDirectory.resolve("navigator.toml");
        this.messagesPath = dataDirectory.resolve("messages.toml");
        this.guiPath = dataDirectory.resolve("gui.toml");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Path configPath() {
        return configPath;
    }

    public Path messagesPath() {
        return messagesPath;
    }

    public Path guiPath() {
        return guiPath;
    }

    public GuiConfig guiConfig() {
        return guiConfig;
    }

    public ConfigLoadResult load() throws IOException {
        Files.createDirectories(dataDirectory);
        if (!Files.exists(configPath)) {
            Config defaults = Config.defaults();
            ConfigWriter.writeConfig(configPath, defaults);
            writeMessages(LanguagePacks.bundle("en", dataDirectory));
            this.guiConfig = GuiConfig.defaults();
            writeGui(this.guiConfig);
            return new ConfigLoadResult(
                    defaults,
                    List.of("Created navigator.toml, messages.toml, and gui.toml with the v4.4 default layout; servers.toml is initialized by server management."),
                    true,
                    false,
                    null,
                    null,
                    false
            );
        }

        Toml toml;
        try {
            toml = new Toml().read(configPath.toFile());
            if (ensureAdvancedSections(toml)) {
                toml = new Toml().read(configPath.toFile());
            }
        } catch (RuntimeException exception) {
            Path backupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupsDir);
            Path backupPath = backupsDir.resolve("navigator.toml.invalid.bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Config defaults = Config.defaults();
            ConfigWriter.writeConfig(configPath, defaults);
            if (!Files.exists(messagesPath)) {
                writeMessages(LanguagePacks.bundle("en", dataDirectory));
            }
            if (!Files.exists(guiPath)) {
                this.guiConfig = GuiConfig.defaults();
                writeGui(this.guiConfig);
            }
            return new ConfigLoadResult(
                    defaults,
                    List.of("navigator.toml could not be parsed, so the broken file was backed up and defaults were regenerated."),
                    false,
                    false,
                    null,
                    backupPath,
                    true
            );
        }

        ParseState state = new ParseState();
        int sourceVersion = TomlReaderUtils.readInt(toml, state, "config_version", 1, "config_version");
        boolean migrated = sourceVersion < Config.CURRENT_VERSION;
        Path backupPath = null;
        if (migrated) {
            Path backupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupsDir);
            backupPath = backupsDir.resolve("navigator.toml.v" + sourceVersion + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            BackupUtils.cleanObsoleteBackups(dataDirectory, backupsDir);
        }

        LanguageBundle language = loadLanguage(toml, state);
        this.guiConfig = loadGui(toml, state);
        Config config = buildConfig(toml, state, sourceVersion, language);
        if (migrated || state.normalized) {
            ConfigWriter.writeConfig(configPath, config);
        }
        if (config.routing().defaultLobbies().isEmpty()) {
            state.warnings.add("No valid default lobbies are configured. /lobby will stay online but fail gracefully until servers are added.");
        }
        if (migrated) {
            state.warnings.add(0, "Migrated navigator.toml from v" + sourceVersion + " to v" + Config.CURRENT_VERSION + ".");
        }

        state.warnings.addAll(ConfigValidator.validate(config, toml));

        return new ConfigLoadResult(
                config,
                List.copyOf(state.warnings),
                false,
                migrated,
                migrated ? sourceVersion : null,
                backupPath,
                state.normalized
        );
    }

    private boolean ensureAdvancedSections(Toml toml) throws IOException {
        StringBuilder additions = new StringBuilder();
        if (toml.getTable("party") == null) additions.append("\n[party]\nenabled = true\ninvite_timeout_seconds = 60\nfollow_leader = true\nmax_size = 20\ncommand = \"party\"\nchat_command = \"p\"\npermission = \"none\"\n");
        if (toml.getTable("queue") == null) additions.append("\n# VN does not create this backend or its world. Register it in velocity.toml,\n# keep it outside lobby pools, and size it for the expected queue.\n[queue]\nenabled = true\npoll_seconds = 2\nnotify_seconds = 5\nmax_size = 500\nholding_server = \"\"\ncommand = \"queue\"\npermission = \"none\"\n");
        if (toml.getTable("redis") == null) additions.append("\n[redis]\nenabled = false\nhost = \"127.0.0.1\"\nport = 6379\nusername = \"\"\npassword = \"\"\nssl = false\nnode_id = \"\"\nchannel_prefix = \"vn\"\nsync_seconds = 5\nconnect_timeout_ms = 3000\nread_timeout_ms = 10000\nreconnect_min_ms = 1000\nreconnect_max_ms = 30000\nregistration_secret = \"\"\nregistration_max_age_seconds = 30\nallowed_registration_hosts = []\n");
        if (toml.getTable("backend_states") == null) additions.append("\n[backend_states]\nenabled = true\nallowed = [\"LOBBY\", \"WAITING\", \"AVAILABLE\"]\nallow_unknown = true\n");
        if (toml.getTable("server_management") == null) additions.append("\n[server_management]\nenabled = true\nvelocity_config = \"velocity.toml\"\nallow_overwrite = false\n");
        if (additions.isEmpty()) return false;
        Files.writeString(configPath, additions.toString(), StandardOpenOption.APPEND);
        return true;
    }

    public void logWarnings(ConfigLoadResult result) {
        for (String warning : result.warnings()) {
            logger.warn("[VelocityNavigator] {}", warning);
        }
    }

    private Config buildConfig(Toml toml, ParseState state, int sourceVersion, LanguageBundle language) {
        return ConfigBuilder.buildConfig(toml, state, sourceVersion, language, dataDirectory);
    }

    private LanguageBundle loadLanguage(Toml legacyToml, ParseState state) throws IOException {
        LanguageBundle defaults = LanguageBundle.defaults();
        Map<String, String> strings = new LinkedHashMap<>(defaults.strings());
        Map<String, List<String>> lists = new LinkedHashMap<>(defaults.lists());

        if (!Files.exists(messagesPath)) {
            Path backupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupsDir);
            Path languageMigrationBackup = backupsDir.resolve("navigator.toml.pre-messages.bak");
            if (Files.exists(configPath) && !Files.exists(languageMigrationBackup)) {
                Files.copy(configPath, languageMigrationBackup, StandardCopyOption.REPLACE_EXISTING);
            }
            Map<String, String> legacyPaths = new LinkedHashMap<>();
            for (String key : defaults.strings().keySet()) {
                if (key.startsWith("messages.")) {
                    legacyPaths.put(key, key);
                }
            }
            legacyPaths.put("menus.chat.header", "routing.chat_menu_header");
            legacyPaths.put("menus.chat.entry", "routing.chat_menu_format");
            legacyPaths.put("menus.chat.tooltip", "routing.chat_menu_tooltip");
            legacyPaths.put("menus.bedrock.title", "bedrock.gui_title");
            legacyPaths.put("menus.bedrock.content", "bedrock.gui_content");
            legacyPaths.put("menus.bedrock.button", "bedrock.gui_button_format");
            legacyPaths.put("lobby.no_server_message", "lobby.no_server_message");

            for (Map.Entry<String, String> entry : legacyPaths.entrySet()) {
                Object value = TomlReaderUtils.rawValue(legacyToml, entry.getValue());
                if (value instanceof String text && !text.isBlank()) {
                    strings.put(entry.getKey(), text);
                }
            }
            LanguageBundle migrated = new LanguageBundle("en", "en", strings, lists);
            writeMessages(migrated);
            state.warnings.add("Created messages.toml, migrated existing message/menu text, and saved backup to backups/navigator.toml.pre-messages.bak.");
            state.normalized = true;
            return migrated;
        }

        Toml messageToml;
        try {
            messageToml = new Toml().read(messagesPath.toFile());
        } catch (RuntimeException exception) {
            Path backupPath = dataDirectory.resolve("messages.toml.invalid.bak");
            Files.copy(messagesPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            writeMessages(defaults);
            state.warnings.add("messages.toml could not be parsed; it was backed up and regenerated with defaults.");
            return defaults;
        }

        String selectedLanguage = TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(messageToml, "language"), "en")
                .trim().toLowerCase(Locale.ROOT).replace('-', '_');
        String activeLanguage = TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(messageToml, "active_language"), selectedLanguage)
                .trim().toLowerCase(Locale.ROOT).replace('-', '_');
        Path externalLanguagePath = dataDirectory.resolve("languages").resolve(selectedLanguage + ".properties");
        if (Files.isRegularFile(externalLanguagePath)) {
            LanguageBundle external = LanguagePacks.bundle(selectedLanguage, dataDirectory);
            writeMessages(external);
            if (!selectedLanguage.equals(activeLanguage)) {
                state.warnings.add("Switched messages.toml to external language '" + selectedLanguage + "'.");
            }
            return external;
        }
        if (LanguagePacks.isSupported(selectedLanguage) && !selectedLanguage.equals(activeLanguage)) {
            LanguageBundle switched = LanguagePacks.bundle(selectedLanguage, dataDirectory);
            writeMessages(switched);
            state.warnings.add("Switched messages.toml to built-in language '" + selectedLanguage + "'.");
            return switched;
        }

        LanguageBundle languageDefaults = LanguagePacks.isSupported(selectedLanguage)
                ? LanguagePacks.bundle(selectedLanguage, dataDirectory)
                : defaults;
        strings.clear();
        strings.putAll(languageDefaults.strings());
        lists.clear();
        lists.putAll(languageDefaults.lists());

        for (String key : defaults.strings().keySet()) {
            Object value = TomlReaderUtils.rawValue(messageToml, key);
            if (value instanceof String text && !text.isBlank()) {
                strings.put(key, text);
            } else if (value != null) {
                state.warnings.add("messages.toml key '" + key + "' must be a non-empty string; the default was used.");
            }
        }
        for (String key : defaults.lists().keySet()) {
            Object value = TomlReaderUtils.rawValue(messageToml, key);
            if (value instanceof List<?> rawList) {
                List<String> parsed = new ArrayList<>();
                boolean valid = true;
                for (Object item : rawList) {
                    if (item instanceof String text) {
                        parsed.add(text);
                    } else {
                        valid = false;
                        break;
                    }
                }
                if (valid && !parsed.isEmpty()) {
                    lists.put(key, List.copyOf(parsed));
                } else {
                    state.warnings.add("messages.toml key '" + key + "' must be a non-empty string list; the default was used.");
                }
            } else if (value != null) {
                state.warnings.add("messages.toml key '" + key + "' must be a string list; the default was used.");
            }
        }
        String formatting = strings.get("messages.formatting").trim().toLowerCase(Locale.ROOT);
        if (!List.of("auto", "minimessage", "legacy").contains(formatting)) {
            strings.put("messages.formatting", languageDefaults.text("messages.formatting"));
            state.warnings.add("messages.formatting must be auto, minimessage, or legacy; the default was used.");
        }
        LanguageBundle loaded = new LanguageBundle(selectedLanguage, selectedLanguage, strings, lists);
        if (!LanguagePacks.isSupported(selectedLanguage) && !selectedLanguage.equals(activeLanguage)) {
            writeMessages(loaded);
            state.warnings.add("Language '" + selectedLanguage + "' is custom; existing values were preserved for editing.");
        }
        return loaded;
    }

    private void writeMessages(LanguageBundle language) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator 4.5.0 language and menu text\n");
        b.append("# MiniMessage formatting and documented placeholders are supported.\n");
        b.append("# Built-ins: ").append(String.join(", ", LanguagePacks.supportedCodes())).append(". Any other code is treated as a custom language.\n");
        b.append("# Change language, restart or /vn reload, and built-in text will be replaced automatically.\n\n");
        b.append("language = ").append(ConfigWriter.quoted(language.language())).append("\n");
        b.append("active_language = ").append(ConfigWriter.quoted(language.activeLanguage())).append("\n\n");

        List<String> sections = new ArrayList<>();
        for (String key : language.strings().keySet()) {
            String section = sectionOf(key);
            if (!sections.contains(section)) {
                sections.add(section);
            }
        }
        for (String key : language.lists().keySet()) {
            String section = sectionOf(key);
            if (!sections.contains(section)) {
                sections.add(section);
            }
        }
        for (String section : sections) {
            b.append('[').append(section).append("]\n");
            for (Map.Entry<String, String> entry : language.strings().entrySet()) {
                if (sectionOf(entry.getKey()).equals(section)) {
                    b.append(nameOf(entry.getKey())).append(" = ").append(ConfigWriter.quoted(entry.getValue())).append('\n');
                }
            }
            for (Map.Entry<String, List<String>> entry : language.lists().entrySet()) {
                if (sectionOf(entry.getKey()).equals(section)) {
                    b.append(nameOf(entry.getKey())).append(" = ").append(ConfigWriter.formatList(entry.getValue())).append('\n');
                }
            }
            b.append('\n');
        }
        Files.writeString(messagesPath, b.toString());
    }

    private String sectionOf(String key) {
        int split = key.lastIndexOf('.');
        return split < 0 ? "language" : key.substring(0, split);
    }

    private String nameOf(String key) {
        int split = key.lastIndexOf('.');
        return split < 0 ? key : key.substring(split + 1);
    }

    private GuiConfig loadGui(Toml navigatorToml, ParseState state) throws IOException {
        GuiConfig defaults = GuiConfig.defaults();
        if (!Files.exists(guiPath)) {
            Path backupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(backupsDir);
            Path guiMigrationBackup = backupsDir.resolve("navigator.toml.pre-gui.bak");
            if (Files.exists(configPath) && !Files.exists(guiMigrationBackup)) {
                Files.copy(configPath, guiMigrationBackup, StandardCopyOption.REPLACE_EXISTING);
            }
            int legacyRows = TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(navigatorToml, "routing.java_menu.rows"), defaults.rows());
            String legacyMaterial = TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(navigatorToml, "routing.java_menu.material"), defaults.defaultMaterial());
            GuiConfig migrated = new GuiConfig(
                    legacyRows,
                    legacyMaterial,
                    defaults.unavailableMaterial(),
                    defaults.fillEmptySlots(),
                    defaults.fillerMaterial(),
                    defaults.refreshSeconds(),
                    defaults.previousSlot(),
                    defaults.refreshSlot(),
                    defaults.nextSlot(),
                    defaults.previousMaterial(),
                    defaults.refreshMaterial(),
                    defaults.nextMaterial(),
                    Map.of()
            );
            writeGui(migrated);
            state.normalized = true;
            state.warnings.add("Created gui.toml, migrated legacy Java-menu layout settings, and saved backup to backups/navigator.toml.pre-gui.bak.");
            return migrated;
        }

        Toml guiToml;
        try {
            guiToml = new Toml().read(guiPath.toFile());
        } catch (RuntimeException exception) {
            Path guiBackupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(guiBackupsDir);
            Path backup = guiBackupsDir.resolve("gui.toml.invalid.bak");
            Files.copy(guiPath, backup, StandardCopyOption.REPLACE_EXISTING);
            writeGui(defaults);
            state.warnings.add("gui.toml could not be parsed; it was backed up and regenerated with defaults.");
            return defaults;
        }

        int sourceGuiVersion = TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "config_version"), 1);
        boolean migrateGui = sourceGuiVersion < GUI_CONFIG_VERSION;
        Path guiMigrationBackup = null;
        if (migrateGui) {
            Path guiBackupsDir = dataDirectory.resolve("backups");
            Files.createDirectories(guiBackupsDir);
            guiMigrationBackup = guiBackupsDir.resolve("gui.toml.v" + sourceGuiVersion + ".bak");
            Files.copy(guiPath, guiMigrationBackup, StandardCopyOption.REPLACE_EXISTING);
            BackupUtils.cleanObsoleteBackups(dataDirectory, guiBackupsDir);
        } else if (sourceGuiVersion > GUI_CONFIG_VERSION) {
            state.warnings.add("gui.toml uses newer config_version " + sourceGuiVersion
                    + "; known settings were loaded without rewriting the file.");
        }

        Map<String, GuiConfig.ServerItem> servers = new LinkedHashMap<>();
        Object rawServers = TomlReaderUtils.rawValue(guiToml, "servers");
        if (rawServers instanceof Map<?, ?> serverMap) {
            for (Map.Entry<?, ?> entry : serverMap.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> values)) {
                    state.warnings.add("gui.toml server override '" + entry.getKey() + "' must be an inline table and was ignored.");
                    continue;
                }
                String name = TomlReaderUtils.sanitizeMapKey(String.valueOf(entry.getKey()));
                int slot = TomlReaderUtils.numberValue(values.get("slot"), -1);
                String material = TomlReaderUtils.stringValue(values.get("material"), "");
                String unavailable = TomlReaderUtils.stringValue(values.get("unavailable_material"), "");
                Object rawDisplayName = values.get("display_name");
                if (rawDisplayName != null && !(rawDisplayName instanceof String)) {
                    state.warnings.add("gui.toml server override '" + name + "' display_name must be a string and was ignored.");
                }
                String displayName = TomlReaderUtils.stringValue(rawDisplayName, "");
                Object rawDescription = values.get("description");
                if (rawDescription != null && !(rawDescription instanceof String)) {
                    state.warnings.add("gui.toml server override '" + name + "' description must be a string and was ignored.");
                }
                String description = TomlReaderUtils.stringValue(rawDescription, "");
                int menuOrder = TomlReaderUtils.numberValue(values.get("menu_order"), -1);
                boolean showInMenu = TomlReaderUtils.booleanValue(values.get("show_in_menu"), true);
                String itemName = TomlReaderUtils.stringValue(values.get("name"), "");
                List<String> lore = TomlReaderUtils.stringListValue(values.get("lore"));
                servers.put(name, new GuiConfig.ServerItem(
                        slot, material, unavailable, displayName, description, menuOrder, showInMenu, itemName, lore));
            }
        }
        Map<String, String> displayNameOwners = new LinkedHashMap<>();
        for (Map.Entry<String, GuiConfig.ServerItem> entry : servers.entrySet()) {
            String displayName = entry.getValue().displayName().trim();
            if (displayName.isEmpty()) {
                continue;
            }
            String previous = displayNameOwners.putIfAbsent(displayName.toLowerCase(Locale.ROOT), entry.getKey());
            if (previous != null) {
                state.warnings.add("gui.toml servers '" + previous + "' and '" + entry.getKey()
                        + "' share display_name '" + displayName + "'; selections remain safe, but the menu labels may be ambiguous.");
            }
        }

        GuiConfig.BedrockMenu bedrockMenu = new GuiConfig.BedrockMenu(
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.enabled"), defaults.bedrock().enabled()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.fallback_to_chat"), defaults.bedrock().fallbackToChat()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "bedrock.sort_mode"), defaults.bedrock().sortMode()),
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "bedrock.max_buttons"), defaults.bedrock().maxButtons()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.show_players"), defaults.bedrock().showPlayers()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.show_max_players"), defaults.bedrock().showMaxPlayers()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.show_ping"), defaults.bedrock().showPing()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "bedrock.show_status"), defaults.bedrock().showStatus()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "bedrock.title"), defaults.bedrock().title()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "bedrock.content"), defaults.bedrock().content()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "bedrock.button_format"), defaults.bedrock().buttonFormat())
        );

        Map<MenuServerState, GuiConfig.StateStyle> stateStyles = new EnumMap<>(MenuServerState.class);
        stateStyles.put(MenuServerState.HEALTHY, GuiConfig.StateStyle.empty());
        for (MenuServerState menuState : List.of(
                MenuServerState.FULL,
                MenuServerState.DRAINING,
                MenuServerState.OFFLINE,
                MenuServerState.IN_GAME)) {
            GuiConfig.StateStyle fallback = defaults.stateStyle(menuState);
            String path = "states." + menuState.configKey();
            Object rawLore = TomlReaderUtils.rawValue(guiToml, path + ".lore");
            if (rawLore != null && !(rawLore instanceof List<?>)) {
                state.warnings.add("gui.toml " + path + ".lore must be a string list; the default was used.");
            }
            stateStyles.put(menuState, new GuiConfig.StateStyle(
                    TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, path + ".material"), fallback.material()),
                    TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, path + ".name"), fallback.name()),
                    rawLore == null ? fallback.lore() : TomlReaderUtils.stringListValue(rawLore)
            ));
        }

        GuiConfig loaded = new GuiConfig(
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "layout.rows"), defaults.rows()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "layout.default_material"), defaults.defaultMaterial()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "layout.unavailable_material"), defaults.unavailableMaterial()),
                TomlReaderUtils.booleanValue(TomlReaderUtils.rawValue(guiToml, "layout.fill_empty_slots"), defaults.fillEmptySlots()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "layout.filler_material"), defaults.fillerMaterial()),
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "layout.refresh_seconds"), defaults.refreshSeconds()),
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "controls.previous_slot"), defaults.previousSlot()),
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "controls.refresh_slot"), defaults.refreshSlot()),
                TomlReaderUtils.numberValue(TomlReaderUtils.rawValue(guiToml, "controls.next_slot"), defaults.nextSlot()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "controls.previous_material"), defaults.previousMaterial()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "controls.refresh_material"), defaults.refreshMaterial()),
                TomlReaderUtils.stringValue(TomlReaderUtils.rawValue(guiToml, "controls.next_material"), defaults.nextMaterial()),
                servers,
                bedrockMenu,
                stateStyles
        );
        if (migrateGui) {
            writeGui(loaded);
            state.warnings.add("Migrated gui.toml from v" + sourceGuiVersion + " to v"
                    + GUI_CONFIG_VERSION + " and saved " + guiMigrationBackup.getFileName() + ".");
        }
        return loaded;
    }

    private void writeGui(GuiConfig gui) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("# VelocityNavigator 4.5.0 Java inventory and Bedrock form layout\n");
        b.append("# Text defaults live in messages.toml; per-server names/lore may use MiniMessage, & codes, § codes, or hex colors.\n\n");
        b.append("config_version = ").append(GUI_CONFIG_VERSION).append("\n\n");
        b.append("[layout]\n");
        b.append("# Java inventories have nine fixed columns. Choose 2-6 rows (18-54 slots);\n");
        b.append("# the bottom row is reserved for controls by default.\n");
        b.append("rows = ").append(gui.rows()).append("\n");
        b.append("default_material = ").append(ConfigWriter.quoted(gui.defaultMaterial())).append("\n");
        b.append("unavailable_material = ").append(ConfigWriter.quoted(gui.unavailableMaterial())).append("\n");
        b.append("fill_empty_slots = ").append(gui.fillEmptySlots()).append("\n");
        b.append("filler_material = ").append(ConfigWriter.quoted(gui.fillerMaterial())).append("\n");
        b.append("refresh_seconds = ").append(gui.refreshSeconds()).append("\n\n");
        b.append("[controls]\n");
        b.append("previous_slot = ").append(gui.previousSlot()).append("\n");
        b.append("refresh_slot = ").append(gui.refreshSlot()).append("\n");
        b.append("next_slot = ").append(gui.nextSlot()).append("\n");
        b.append("previous_material = ").append(ConfigWriter.quoted(gui.previousMaterial())).append("\n");
        b.append("refresh_material = ").append(ConfigWriter.quoted(gui.refreshMaterial())).append("\n");
        b.append("next_material = ").append(ConfigWriter.quoted(gui.nextMaterial())).append("\n\n");
        b.append("[bedrock]\n");
        b.append("# Bedrock chooses the form layout; max_buttons only limits its choices.\n");
        b.append("enabled = ").append(gui.bedrock().enabled()).append("\n");
        b.append("fallback_to_chat = ").append(gui.bedrock().fallbackToChat()).append("\n");
        b.append("sort_mode = ").append(ConfigWriter.quoted(gui.bedrock().sortMode())).append("\n");
        b.append("max_buttons = ").append(gui.bedrock().maxButtons()).append("\n");
        b.append("show_players = ").append(gui.bedrock().showPlayers()).append("\n");
        b.append("show_max_players = ").append(gui.bedrock().showMaxPlayers()).append("\n");
        b.append("show_ping = ").append(gui.bedrock().showPing()).append("\n");
        b.append("show_status = ").append(gui.bedrock().showStatus()).append("\n");
        b.append("title = ").append(ConfigWriter.quoted(gui.bedrock().title())).append("\n");
        b.append("content = ").append(ConfigWriter.quoted(gui.bedrock().content())).append("\n");
        b.append("button_format = ").append(ConfigWriter.quoted(gui.bedrock().buttonFormat())).append("\n\n");
        b.append("# State presentation is used by the Java inventory selector. Names and lore support every server placeholder.\n");
        for (MenuServerState menuState : List.of(
                MenuServerState.FULL,
                MenuServerState.DRAINING,
                MenuServerState.OFFLINE,
                MenuServerState.IN_GAME)) {
            GuiConfig.StateStyle style = gui.stateStyle(menuState);
            b.append("[states.").append(menuState.configKey()).append("]\n");
            b.append("material = ").append(ConfigWriter.quoted(style.material())).append("\n");
            b.append("name = ").append(ConfigWriter.quoted(style.name())).append("\n");
            b.append("lore = ").append(ConfigWriter.formatList(style.lore())).append("\n\n");
        }
        b.append("# Optional per-server overrides. display_name and description are shared by every selector; name/lore customize Java items.\n");
        b.append("# menu_order = -1 keeps routing order, show_in_menu only affects selectors, and slot = -1 enables automatic placement.\n");
        b.append("# [servers]\n");
        b.append("# \"lobby-1\" = { display_name = \"Main Lobby 1\", description = \"Classic survival lobby\", menu_order = 10, show_in_menu = true, slot = 10, material = \"NETHER_STAR\", unavailable_material = \"BARRIER\", name = \"&#55FFFF&l{server}\", lore = [\"&7{description}\", \"&7Players: &f{players}/{max_players}\", \"&eClick to connect\"] }\n");
        if (!gui.servers().isEmpty()) {
            b.append("[servers]\n");
            for (Map.Entry<String, GuiConfig.ServerItem> entry : gui.servers().entrySet()) {
                GuiConfig.ServerItem item = entry.getValue();
                b.append(ConfigWriter.quoted(entry.getKey())).append(" = { display_name = ").append(ConfigWriter.quoted(item.displayName()))
                        .append(", description = ").append(ConfigWriter.quoted(item.description()))
                        .append(", menu_order = ").append(item.menuOrder())
                        .append(", show_in_menu = ").append(item.showInMenu())
                        .append(", slot = ").append(item.slot())
                        .append(", material = ").append(ConfigWriter.quoted(item.material()))
                        .append(", unavailable_material = ").append(ConfigWriter.quoted(item.unavailableMaterial()))
                        .append(", name = ").append(ConfigWriter.quoted(item.name()))
                        .append(", lore = ").append(ConfigWriter.formatList(item.lore())).append(" }\n");
            }
        }
        Files.writeString(guiPath, b.toString());
    }

    public static void cleanObsoleteBackups(Path rootDir, Path backupsDir) {
        BackupUtils.cleanObsoleteBackups(rootDir, backupsDir);
    }

}
