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
package com.demonz.velocitynavigator;

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
            writeConfig(defaults);
            writeMessages(LanguagePacks.bundle("en"));
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
            Path backupPath = dataDirectory.resolve("navigator.toml.invalid.bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            Config defaults = Config.defaults();
            writeConfig(defaults);
            if (!Files.exists(messagesPath)) {
                writeMessages(LanguagePacks.bundle("en"));
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
        int sourceVersion = readInt(toml, state, "config_version", 1, "config_version");
        boolean migrated = sourceVersion < Config.CURRENT_VERSION;
        Path backupPath = null;
        if (migrated) {
            backupPath = dataDirectory.resolve("navigator.toml.v" + sourceVersion + ".bak");
            Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        }

        LanguageBundle language = loadLanguage(toml, state);
        this.guiConfig = loadGui(toml, state);
        Config config = buildConfig(toml, state, sourceVersion, language);
        if (migrated || state.normalized) {
            writeConfig(config);
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

    @SuppressWarnings("unchecked")
    private Config buildConfig(Toml toml, ParseState state, int sourceVersion, LanguageBundle language) {
        Config defaults = Config.defaults();

        String rawSelectionMode = readString(
                toml,
                state,
                "routing.selection_mode",
                defaults.routing().selectionMode().configValue(),
                "routing.selection_mode",
                "settings.selection_mode",
                "selection_mode"
        );
        Config.SelectionMode selectionMode = Config.SelectionMode.fromString(rawSelectionMode);
        if (!selectionMode.configValue().equals(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
            state.normalized = true;
        }
        List<String> validModes = List.of("least_players", "random", "round_robin",
                "power_of_two", "weighted_round_robin", "least_connections", "consistent_hash", "latency");
        if (!validModes.contains(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
            state.warnings.add("routing.selection_mode was invalid, so it was reset to " + selectionMode.configValue() + ".");
            state.normalized = true;
        }

        Config.Commands commands = new Config.Commands(
                readString(toml, state, "commands.primary", defaults.commands().primary(), "commands.primary"),
                readStringList(toml, state, "commands.aliases", defaults.commands().aliases(), "commands.aliases", "command_aliases"),
                readString(toml, state, "commands.permission", defaults.commands().permission(), "commands.permission"),
                readStringList(toml, state, "commands.admin_aliases", defaults.commands().adminAliases(), "commands.admin_aliases"),
                readInt(toml, state, "commands.cooldown_seconds", defaults.commands().cooldownSeconds(), "commands.cooldown_seconds", "command_cooldown"),
                readBoolean(toml, state, "commands.reconnect_if_same_server", defaults.commands().reconnectIfSameServer(), "commands.reconnect_if_same_server", "reconnect_on_lobby_command")
        );

        Map<String, Config.GroupConfig> groupConfigs = readGroupConfigMap(toml, state, "routing.contextual.groups", "routing.contextual.groups", "contextual_lobbies.groups");

        Map<String, List<String>> fallbackChain = readStringListMap(toml, state, "routing.contextual.fallback_chain", "routing.contextual.fallback_chain");

        Config.Contextual contextual = new Config.Contextual(
                readBoolean(toml, state, "routing.contextual.enabled", defaults.routing().contextual().enabled(), "routing.contextual.enabled", "advanced_settings.use_contextual_lobbies"),
                readBoolean(toml, state, "routing.contextual.fallback_to_default", defaults.routing().contextual().fallbackToDefault(), "routing.contextual.fallback_to_default"),
                groupConfigs,
                readStringMap(toml, state, "routing.contextual.sources", "routing.contextual.sources", "contextual_lobbies.mappings"),
                fallbackChain
        );

        List<Config.LobbyEntry> defaultLobbies = readLobbyEntryList(
                toml,
                state,
                "routing.default_lobbies",
                defaults.routing().defaultLobbies(),
                "routing.default_lobbies",
                "settings.lobby_servers",
                "lobby_servers"
        );

        int maxRetries = readInt(toml, state, "routing.max_retries", defaults.routing().maxRetries(), "routing.max_retries");

        Config.AffinitySettings affinity = new Config.AffinitySettings(
                readBoolean(toml, state, "routing.affinity.enabled", defaults.routing().affinity().enabled(), "routing.affinity.enabled"),
                readDouble(toml, state, "routing.affinity.stickiness", defaults.routing().affinity().stickiness(), "routing.affinity.stickiness")
        );

        Config.Routing routing = new Config.Routing(
                selectionMode,
                readBoolean(toml, state, "routing.cycle_when_possible", defaults.routing().cycleWhenPossible(), "routing.cycle_when_possible", "cycle_lobbies"),
                readBoolean(toml, state, "routing.balance_initial_join", defaults.routing().balanceInitialJoin(), "routing.balance_initial_join"),
                defaultLobbies,
                contextual,
                maxRetries,
                affinity,
                readBoolean(toml, state, "routing.use_menu_for_lobby", defaults.routing().useChatMenuForLobby(), "routing.use_menu_for_lobby", "routing.use_chat_menu_for_lobby"),
                language.text("menus.chat.header"),
                language.text("menus.chat.entry"),
                language.text("menus.chat.tooltip"),
                Config.JavaMenuType.fromString(readString(toml, state, "routing.java_menu.type", defaults.routing().javaMenuType().configValue(), "routing.java_menu.type")),
                new Config.InventoryMenuSettings(
                        readInt(toml, state, "routing.java_menu.rows", defaults.routing().inventoryMenu().rows(), "routing.java_menu.rows"),
                        readString(toml, state, "routing.java_menu.material", defaults.routing().inventoryMenu().material(), "routing.java_menu.material"),
                        readBoolean(toml, state, "routing.java_menu.fallback_to_chat", defaults.routing().inventoryMenu().fallbackToChat(), "routing.java_menu.fallback_to_chat")
                )
        );

        Config.HealthChecks healthChecks = new Config.HealthChecks(
                readBoolean(toml, state, "health_checks.enabled", defaults.healthChecks().enabled(), "health_checks.enabled", "ping_before_connect"),
                readInt(toml, state, "health_checks.timeout_ms", defaults.healthChecks().timeoutMs(), "health_checks.timeout_ms"),
                readInt(toml, state, "health_checks.cache_seconds", defaults.healthChecks().cacheSeconds(), "health_checks.cache_seconds", "ping_cache_duration")
        );

        Config.Messages messages = new Config.Messages(
                language.text("messages.connecting"),
                language.text("messages.already_connected"),
                language.text("messages.no_lobby_found"),
                language.text("messages.player_only"),
                language.text("messages.cooldown"),
                language.text("messages.reload_success"),
                language.text("messages.reload_failed"),
                language.text("messages.retrying"),
                language.text("messages.formatting"),
                language.text("messages.dashboard_healthy"),
                language.text("messages.dashboard_draining"),
                language.text("messages.dashboard_open"),
                language.text("messages.dashboard_offline")
        );

        Config.UpdateCheckerSettings updateChecker;
        if (sourceVersion < 5) {
            boolean enabled = true;
            Object oldEnabled = rawValue(toml, "update_checker.enabled");
            if (oldEnabled instanceof Boolean && !(Boolean) oldEnabled) {
                enabled = false;
            }
            Config.UpdateChannel channel = Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel"));
            int checkInterval = readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval");
            boolean notifyAdmins = readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins");
            boolean silent = readBoolean(toml, state, "update_checker.silent", defaults.updateChecker().silent(), "update_checker.silent");
            updateChecker = new Config.UpdateCheckerSettings(enabled, channel, checkInterval, notifyAdmins, silent);
        } else {
            updateChecker = new Config.UpdateCheckerSettings(
                    readBoolean(toml, state, "update_checker.enabled", defaults.updateChecker().enabled(), "update_checker.enabled"),
                    Config.UpdateChannel.fromString(readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel")),
                    readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval"),
                    readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins"),
                    readBoolean(toml, state, "update_checker.silent", defaults.updateChecker().silent(), "update_checker.silent")
            );
        }

        Config.MetricsSettings metrics = new Config.MetricsSettings(
                readBoolean(toml, state, "metrics.enabled", defaults.metrics().enabled(), "metrics.enabled"),
                new Config.PrometheusSettings(
                        readBoolean(toml, state, "metrics.prometheus.enabled", defaults.metrics().prometheus().enabled(), "metrics.prometheus.enabled"),
                        readInt(toml, state, "metrics.prometheus.port", defaults.metrics().prometheus().port(), "metrics.prometheus.port"),
                        readString(toml, state, "metrics.prometheus.bind_host", defaults.metrics().prometheus().bindHost(), "metrics.prometheus.bind_host", "metrics.prometheus.bindHost"),
                        readString(toml, state, "metrics.prometheus.bearer_token", defaults.metrics().prometheus().bearerToken(), "metrics.prometheus.bearer_token", "metrics.prometheus.bearerToken")
                )
        );

        Config.DebugSettings debug = new Config.DebugSettings(
                readBoolean(toml, state, "debug.verbose_logging", defaults.debug().verboseLogging(), "debug.verbose_logging")
        );

        Config.CircuitBreakerSettings circuitBreakerSettings = new Config.CircuitBreakerSettings(
                readBoolean(toml, state, "circuit_breaker.enabled", defaults.circuitBreaker().enabled(), "circuit_breaker.enabled"),
                readInt(toml, state, "circuit_breaker.failure_threshold", defaults.circuitBreaker().failureThreshold(), "circuit_breaker.failure_threshold"),
                readInt(toml, state, "circuit_breaker.cooldown_seconds", defaults.circuitBreaker().cooldownSeconds(), "circuit_breaker.cooldown_seconds"),
                readInt(toml, state, "circuit_breaker.half_open_max_tests", defaults.circuitBreaker().halfOpenMaxTests(), "circuit_breaker.half_open_max_tests")
        );

        Config.DegradationSettings degradationSettings = new Config.DegradationSettings(
                readBoolean(toml, state, "degradation.enabled", defaults.degradation().enabled(), "degradation.enabled"),
                readString(toml, state, "degradation.mode", defaults.degradation().mode(), "degradation.mode")
        );

        Config.GeoRoutingSettings geoRoutingSettings = new Config.GeoRoutingSettings(
                readBoolean(toml, state, "geo_routing.enabled", defaults.geoRouting().enabled(), "geo_routing.enabled"),
                readString(toml, state, "geo_routing.database_path", defaults.geoRouting().databasePath(), "geo_routing.database_path")
        );

        boolean notifyOnStartup = readBoolean(toml, state, "notify_on_startup", defaults.notifyOnStartup(), "notify_on_startup");
        boolean notifyAdminsOnJoin = readBoolean(toml, state, "notify_admins_on_join", defaults.notifyAdminsOnJoin(), "notify_admins_on_join");

        if (rawValue(toml, "startup.wiki_url") != null) {
            state.normalized = true;
            state.warnings.add("Removed legacy startup.wiki_url; documentation links now always use the official VelocityNavigator wiki.");
        }
        Config.StartupSettings startup = new Config.StartupSettings(
                readBoolean(toml, state, "startup.welcome_enabled", defaults.startup().welcomeEnabled(), "startup.welcome_enabled"),
                Config.OFFICIAL_WIKI_URL
        );

        Config.LobbyFallbackSettings lobbyFallback = new Config.LobbyFallbackSettings(
                readString(toml, state, "lobby.no_server_strategy", defaults.lobbyFallback().noServerStrategy(), "lobby.no_server_strategy"),
                language.text("lobby.no_server_message"),
                readString(toml, state, "lobby.fallback_server", defaults.lobbyFallback().fallbackServer(), "lobby.fallback_server")
        );

        Config.BedrockSettings bedrock = new Config.BedrockSettings(
                readBoolean(toml, state, "bedrock.enabled", defaults.bedrock().enabled(), "bedrock.enabled"),
                readBoolean(toml, state, "bedrock.auto_detect", defaults.bedrock().autoDetect(), "bedrock.auto_detect"),
                readBoolean(toml, state, "bedrock.strip_advanced_formatting", defaults.bedrock().stripAdvancedFormatting(), "bedrock.strip_advanced_formatting"),
                readBoolean(toml, state, "bedrock.affinity_use_java_uuid", defaults.bedrock().affinityUseJavaUuid(), "bedrock.affinity_use_java_uuid"),
                readBoolean(toml, state, "bedrock.use_gui_for_lobby", defaults.bedrock().useGuiForLobby(), "bedrock.use_gui_for_lobby"),
                language.text("menus.bedrock.title"),
                language.text("menus.bedrock.content"),
                language.text("menus.bedrock.button")
        );

        Config.DashboardSettings dashboard = new Config.DashboardSettings(
                readBoolean(toml, state, "dashboard.enabled", defaults.dashboard().enabled(), "dashboard.enabled"),
                readInt(toml, state, "dashboard.port", defaults.dashboard().port(), "dashboard.port"),
                readString(toml, state, "dashboard.bind_host", defaults.dashboard().bindHost(), "dashboard.bind_host"),
                readString(toml, state, "dashboard.bearer_token", defaults.dashboard().bearerToken(), "dashboard.bearer_token"),
                readInt(toml, state, "dashboard.refresh_seconds", defaults.dashboard().refreshSeconds(), "dashboard.refresh_seconds")
        );

        return new Config(
                Config.CURRENT_VERSION,
                commands,
                routing,
                healthChecks,
                messages,
                updateChecker,
                metrics,
                debug,
                circuitBreakerSettings,
                degradationSettings,
                geoRoutingSettings,
                notifyOnStartup,
                notifyAdminsOnJoin,
                startup,
                lobbyFallback,
                bedrock,
                dashboard,
                language
        );
    }

    private LanguageBundle loadLanguage(Toml legacyToml, ParseState state) throws IOException {
        LanguageBundle defaults = LanguageBundle.defaults();
        Map<String, String> strings = new LinkedHashMap<>(defaults.strings());
        Map<String, List<String>> lists = new LinkedHashMap<>(defaults.lists());

        if (!Files.exists(messagesPath)) {
            Path languageMigrationBackup = dataDirectory.resolve("navigator.toml.pre-messages.bak");
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
                Object value = rawValue(legacyToml, entry.getValue());
                if (value instanceof String text && !text.isBlank()) {
                    strings.put(entry.getKey(), text);
                }
            }
            LanguageBundle migrated = new LanguageBundle("en", "en", strings, lists);
            writeMessages(migrated);
            state.warnings.add("Created messages.toml, migrated existing message/menu text, and saved navigator.toml.pre-messages.bak.");
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

        String selectedLanguage = stringValue(rawValue(messageToml, "language"), "en")
                .trim().toLowerCase(Locale.ROOT).replace('-', '_');
        String activeLanguage = stringValue(rawValue(messageToml, "active_language"), selectedLanguage)
                .trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (LanguagePacks.isSupported(selectedLanguage) && !selectedLanguage.equals(activeLanguage)) {
            LanguageBundle switched = LanguagePacks.bundle(selectedLanguage);
            writeMessages(switched);
            state.warnings.add("Switched messages.toml to built-in language '" + selectedLanguage + "'.");
            return switched;
        }

        LanguageBundle languageDefaults = LanguagePacks.isSupported(selectedLanguage)
                ? LanguagePacks.bundle(selectedLanguage)
                : defaults;
        strings.clear();
        strings.putAll(languageDefaults.strings());
        lists.clear();
        lists.putAll(languageDefaults.lists());

        for (String key : defaults.strings().keySet()) {
            Object value = rawValue(messageToml, key);
            if (value instanceof String text && !text.isBlank()) {
                strings.put(key, text);
            } else if (value != null) {
                state.warnings.add("messages.toml key '" + key + "' must be a non-empty string; the default was used.");
            }
        }
        for (String key : defaults.lists().keySet()) {
            Object value = rawValue(messageToml, key);
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
        b.append("# VelocityNavigator 4.4.0 language and menu text\n");
        b.append("# MiniMessage formatting and documented placeholders are supported.\n");
        b.append("# Built-ins: ").append(String.join(", ", LanguagePacks.supportedCodes())).append(". Any other code is treated as a custom language.\n");
        b.append("# Change language, restart or /vn reload, and built-in text will be replaced automatically.\n\n");
        b.append("language = ").append(quoted(language.language())).append("\n");
        b.append("active_language = ").append(quoted(language.activeLanguage())).append("\n\n");

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
                    b.append(nameOf(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append('\n');
                }
            }
            for (Map.Entry<String, List<String>> entry : language.lists().entrySet()) {
                if (sectionOf(entry.getKey()).equals(section)) {
                    b.append(nameOf(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append('\n');
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
            Path guiMigrationBackup = dataDirectory.resolve("navigator.toml.pre-gui.bak");
            if (Files.exists(configPath) && !Files.exists(guiMigrationBackup)) {
                Files.copy(configPath, guiMigrationBackup, StandardCopyOption.REPLACE_EXISTING);
            }
            int legacyRows = numberValue(rawValue(navigatorToml, "routing.java_menu.rows"), defaults.rows());
            String legacyMaterial = stringValue(rawValue(navigatorToml, "routing.java_menu.material"), defaults.defaultMaterial());
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
            state.warnings.add("Created gui.toml, migrated legacy Java-menu layout settings, and saved navigator.toml.pre-gui.bak.");
            return migrated;
        }

        Toml guiToml;
        try {
            guiToml = new Toml().read(guiPath.toFile());
        } catch (RuntimeException exception) {
            Path backup = dataDirectory.resolve("gui.toml.invalid.bak");
            Files.copy(guiPath, backup, StandardCopyOption.REPLACE_EXISTING);
            writeGui(defaults);
            state.warnings.add("gui.toml could not be parsed; it was backed up and regenerated with defaults.");
            return defaults;
        }

        int sourceGuiVersion = numberValue(rawValue(guiToml, "config_version"), 1);
        boolean migrateGui = sourceGuiVersion < GUI_CONFIG_VERSION;
        Path guiMigrationBackup = null;
        if (migrateGui) {
            guiMigrationBackup = dataDirectory.resolve("gui.toml.v" + sourceGuiVersion + ".bak");
            Files.copy(guiPath, guiMigrationBackup, StandardCopyOption.REPLACE_EXISTING);
        } else if (sourceGuiVersion > GUI_CONFIG_VERSION) {
            state.warnings.add("gui.toml uses newer config_version " + sourceGuiVersion
                    + "; known settings were loaded without rewriting the file.");
        }

        Map<String, GuiConfig.ServerItem> servers = new LinkedHashMap<>();
        Object rawServers = rawValue(guiToml, "servers");
        if (rawServers instanceof Map<?, ?> serverMap) {
            for (Map.Entry<?, ?> entry : serverMap.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> values)) {
                    state.warnings.add("gui.toml server override '" + entry.getKey() + "' must be an inline table and was ignored.");
                    continue;
                }
                String name = sanitizeMapKey(String.valueOf(entry.getKey()));
                int slot = numberValue(values.get("slot"), -1);
                String material = stringValue(values.get("material"), "");
                String unavailable = stringValue(values.get("unavailable_material"), "");
                Object rawDisplayName = values.get("display_name");
                if (rawDisplayName != null && !(rawDisplayName instanceof String)) {
                    state.warnings.add("gui.toml server override '" + name + "' display_name must be a string and was ignored.");
                }
                String displayName = stringValue(rawDisplayName, "");
                Object rawDescription = values.get("description");
                if (rawDescription != null && !(rawDescription instanceof String)) {
                    state.warnings.add("gui.toml server override '" + name + "' description must be a string and was ignored.");
                }
                String description = stringValue(rawDescription, "");
                int menuOrder = numberValue(values.get("menu_order"), -1);
                boolean showInMenu = booleanValue(values.get("show_in_menu"), true);
                String itemName = stringValue(values.get("name"), "");
                List<String> lore = stringListValue(values.get("lore"));
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
                booleanValue(rawValue(guiToml, "bedrock.enabled"), defaults.bedrock().enabled()),
                booleanValue(rawValue(guiToml, "bedrock.fallback_to_chat"), defaults.bedrock().fallbackToChat()),
                stringValue(rawValue(guiToml, "bedrock.sort_mode"), defaults.bedrock().sortMode()),
                numberValue(rawValue(guiToml, "bedrock.max_buttons"), defaults.bedrock().maxButtons()),
                booleanValue(rawValue(guiToml, "bedrock.show_players"), defaults.bedrock().showPlayers()),
                booleanValue(rawValue(guiToml, "bedrock.show_max_players"), defaults.bedrock().showMaxPlayers()),
                booleanValue(rawValue(guiToml, "bedrock.show_ping"), defaults.bedrock().showPing()),
                booleanValue(rawValue(guiToml, "bedrock.show_status"), defaults.bedrock().showStatus()),
                stringValue(rawValue(guiToml, "bedrock.title"), defaults.bedrock().title()),
                stringValue(rawValue(guiToml, "bedrock.content"), defaults.bedrock().content()),
                stringValue(rawValue(guiToml, "bedrock.button_format"), defaults.bedrock().buttonFormat())
        );

        Map<MenuServerState, GuiConfig.StateStyle> stateStyles = new java.util.EnumMap<>(MenuServerState.class);
        stateStyles.put(MenuServerState.HEALTHY, GuiConfig.StateStyle.empty());
        for (MenuServerState menuState : List.of(
                MenuServerState.FULL,
                MenuServerState.DRAINING,
                MenuServerState.OFFLINE,
                MenuServerState.IN_GAME)) {
            GuiConfig.StateStyle fallback = defaults.stateStyle(menuState);
            String path = "states." + menuState.configKey();
            Object rawLore = rawValue(guiToml, path + ".lore");
            if (rawLore != null && !(rawLore instanceof List<?>)) {
                state.warnings.add("gui.toml " + path + ".lore must be a string list; the default was used.");
            }
            stateStyles.put(menuState, new GuiConfig.StateStyle(
                    stringValue(rawValue(guiToml, path + ".material"), fallback.material()),
                    stringValue(rawValue(guiToml, path + ".name"), fallback.name()),
                    rawLore == null ? fallback.lore() : stringListValue(rawLore)
            ));
        }

        GuiConfig loaded = new GuiConfig(
                numberValue(rawValue(guiToml, "layout.rows"), defaults.rows()),
                stringValue(rawValue(guiToml, "layout.default_material"), defaults.defaultMaterial()),
                stringValue(rawValue(guiToml, "layout.unavailable_material"), defaults.unavailableMaterial()),
                booleanValue(rawValue(guiToml, "layout.fill_empty_slots"), defaults.fillEmptySlots()),
                stringValue(rawValue(guiToml, "layout.filler_material"), defaults.fillerMaterial()),
                numberValue(rawValue(guiToml, "layout.refresh_seconds"), defaults.refreshSeconds()),
                numberValue(rawValue(guiToml, "controls.previous_slot"), defaults.previousSlot()),
                numberValue(rawValue(guiToml, "controls.refresh_slot"), defaults.refreshSlot()),
                numberValue(rawValue(guiToml, "controls.next_slot"), defaults.nextSlot()),
                stringValue(rawValue(guiToml, "controls.previous_material"), defaults.previousMaterial()),
                stringValue(rawValue(guiToml, "controls.refresh_material"), defaults.refreshMaterial()),
                stringValue(rawValue(guiToml, "controls.next_material"), defaults.nextMaterial()),
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
        b.append("# VelocityNavigator 4.4.0 Java inventory and Bedrock form layout\n");
        b.append("# Text defaults live in messages.toml; per-server names/lore may use MiniMessage, & codes, § codes, or hex colors.\n\n");
        b.append("config_version = ").append(GUI_CONFIG_VERSION).append("\n\n");
        b.append("[layout]\n");
        b.append("# Java inventories have nine fixed columns. Choose 2-6 rows (18-54 slots);\n");
        b.append("# the bottom row is reserved for controls by default.\n");
        b.append("rows = ").append(gui.rows()).append("\n");
        b.append("default_material = ").append(quoted(gui.defaultMaterial())).append("\n");
        b.append("unavailable_material = ").append(quoted(gui.unavailableMaterial())).append("\n");
        b.append("fill_empty_slots = ").append(gui.fillEmptySlots()).append("\n");
        b.append("filler_material = ").append(quoted(gui.fillerMaterial())).append("\n");
        b.append("refresh_seconds = ").append(gui.refreshSeconds()).append("\n\n");
        b.append("[controls]\n");
        b.append("previous_slot = ").append(gui.previousSlot()).append("\n");
        b.append("refresh_slot = ").append(gui.refreshSlot()).append("\n");
        b.append("next_slot = ").append(gui.nextSlot()).append("\n");
        b.append("previous_material = ").append(quoted(gui.previousMaterial())).append("\n");
        b.append("refresh_material = ").append(quoted(gui.refreshMaterial())).append("\n");
        b.append("next_material = ").append(quoted(gui.nextMaterial())).append("\n\n");
        b.append("[bedrock]\n");
        b.append("# Bedrock chooses the form layout; max_buttons only limits its choices.\n");
        b.append("enabled = ").append(gui.bedrock().enabled()).append("\n");
        b.append("fallback_to_chat = ").append(gui.bedrock().fallbackToChat()).append("\n");
        b.append("sort_mode = ").append(quoted(gui.bedrock().sortMode())).append("\n");
        b.append("max_buttons = ").append(gui.bedrock().maxButtons()).append("\n");
        b.append("show_players = ").append(gui.bedrock().showPlayers()).append("\n");
        b.append("show_max_players = ").append(gui.bedrock().showMaxPlayers()).append("\n");
        b.append("show_ping = ").append(gui.bedrock().showPing()).append("\n");
        b.append("show_status = ").append(gui.bedrock().showStatus()).append("\n");
        b.append("title = ").append(quoted(gui.bedrock().title())).append("\n");
        b.append("content = ").append(quoted(gui.bedrock().content())).append("\n");
        b.append("button_format = ").append(quoted(gui.bedrock().buttonFormat())).append("\n\n");
        b.append("# State presentation is used by the Java inventory selector. Names and lore support every server placeholder.\n");
        for (MenuServerState menuState : List.of(
                MenuServerState.FULL,
                MenuServerState.DRAINING,
                MenuServerState.OFFLINE,
                MenuServerState.IN_GAME)) {
            GuiConfig.StateStyle style = gui.stateStyle(menuState);
            b.append("[states.").append(menuState.configKey()).append("]\n");
            b.append("material = ").append(quoted(style.material())).append("\n");
            b.append("name = ").append(quoted(style.name())).append("\n");
            b.append("lore = ").append(formatList(style.lore())).append("\n\n");
        }
        b.append("# Optional per-server overrides. display_name and description are shared by every selector; name/lore customize Java items.\n");
        b.append("# menu_order = -1 keeps routing order, show_in_menu only affects selectors, and slot = -1 enables automatic placement.\n");
        b.append("# [servers]\n");
        b.append("# \"lobby-1\" = { display_name = \"Main Lobby 1\", description = \"Classic survival lobby\", menu_order = 10, show_in_menu = true, slot = 10, material = \"NETHER_STAR\", unavailable_material = \"BARRIER\", name = \"&#55FFFF&l{server}\", lore = [\"&7{description}\", \"&7Players: &f{players}/{max_players}\", \"&eClick to connect\"] }\n");
        if (!gui.servers().isEmpty()) {
            b.append("[servers]\n");
            for (Map.Entry<String, GuiConfig.ServerItem> entry : gui.servers().entrySet()) {
                GuiConfig.ServerItem item = entry.getValue();
                b.append(quoted(entry.getKey())).append(" = { display_name = ").append(quoted(item.displayName()))
                        .append(", description = ").append(quoted(item.description()))
                        .append(", menu_order = ").append(item.menuOrder())
                        .append(", show_in_menu = ").append(item.showInMenu())
                        .append(", slot = ").append(item.slot())
                        .append(", material = ").append(quoted(item.material()))
                        .append(", unavailable_material = ").append(quoted(item.unavailableMaterial()))
                        .append(", name = ").append(quoted(item.name()))
                        .append(", lore = ").append(formatList(item.lore())).append(" }\n");
            }
        }
        Files.writeString(guiPath, b.toString());
    }

    private int numberValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private String stringValue(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private List<Config.LobbyEntry> readLobbyEntryList(Toml toml, ParseState state, String label, List<Config.LobbyEntry> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<Config.LobbyEntry> entries = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String text && !text.isBlank()) {
                        entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                    } else if (item instanceof Map<?, ?> map) {
                        entries.add(parseLobbyEntryFromMap(map, label, state));
                    } else {
                        state.warnings.add(label + " contained an unrecognized entry format that was ignored.");
                        state.normalized = true;
                    }
                }
                return entries;
            }
            state.warnings.add(label + " expected a list. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Config.LobbyEntry parseLobbyEntryFromMap(Map<?, ?> map, String label, ParseState state) {
        String server = "";
        int maxPlayers = Config.LobbyEntry.UNCAPPED;
        int weight = Config.LobbyEntry.DEFAULT_WEIGHT;

        Object serverObj = map.get("server");
        if (serverObj instanceof String s && !s.isBlank()) {
            server = s.trim();
        } else {
            state.warnings.add(label + " contained a lobby entry without a valid 'server' field.");
            state.normalized = true;
        }

        Object maxObj = map.get("max_players");
        if (maxObj instanceof Number n) {
            maxPlayers = n.intValue();
        }

        Object weightObj = map.get("weight");
        if (weightObj instanceof Number n) {
            weight = n.intValue();
        }

        return new Config.LobbyEntry(server, maxPlayers, weight);
    }

    private Map<String, Config.GroupConfig> readGroupConfigMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Config.GroupConfig> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (key.isBlank()) {
                    continue;
                }

                Object groupValue = entry.getValue();

                if (groupValue instanceof List<?> rawList) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof String text && !text.isBlank()) {
                            entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                        } else if (item instanceof Map<?, ?> map) {
                            entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                        }
                    }
                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, null));
                    }
                    continue;
                }

                if (groupValue instanceof Map<?, ?> groupMap) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    Object serversObj = groupMap.get("servers");
                    if (serversObj instanceof List<?> serversList) {
                        for (Object item : serversList) {
                            if (item instanceof String text && !text.isBlank()) {
                                entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                            } else if (item instanceof Map<?, ?> map) {
                                entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                            }
                        }
                    }

                    Config.SelectionMode mode = null;
                    Object modeObj = groupMap.get("mode");
                    if (modeObj instanceof String modeStr && !modeStr.isBlank()) {
                        mode = Config.SelectionMode.fromString(modeStr);
                    }

                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, mode));
                    }
                    continue;
                }

                state.warnings.add(label + "." + key + " expected a list or table and was ignored.");
                state.normalized = true;
            }
            return result;
        }
        return Map.of();
    }

    private String readString(Toml toml, ParseState state, String label, String fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof String text) {
                return text;
            }
            state.warnings.add(label + " expected a string. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private boolean readBoolean(Toml toml, ParseState state, String label, boolean fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            state.warnings.add(label + " expected true/false. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private int readInt(Toml toml, ParseState state, String label, int fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private double readDouble(Toml toml, ParseState state, String label, double fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private List<String> readStringList(Toml toml, ParseState state, String label, List<String> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<String> cleaned = new ArrayList<>();
                for (Object entry : rawList) {
                    if (entry instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                return cleaned;
            }
            state.warnings.add(label + " expected a list of strings. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    private Map<String, List<String>> readStringListMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (!(entry.getValue() instanceof List<?> rawList)) {
                    state.warnings.add(label + "." + key + " expected a list of strings and was ignored.");
                    state.normalized = true;
                    continue;
                }
                List<String> cleaned = new ArrayList<>();
                for (Object rawItem : rawList) {
                    if (rawItem instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + "." + key + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                values.put(key, cleaned);
            }
            return values;
        }
        return Map.of();
    }

    private Map<String, String> readStringMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (entry.getValue() instanceof String text && !text.isBlank()) {
                    values.put(key, text.trim().toLowerCase(Locale.ROOT));
                } else {
                    state.warnings.add(label + "." + key + " expected a string and was ignored.");
                    state.normalized = true;
                }
            }
            return values;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Object rawValue(Toml toml, String path) {
        Object current = toml.toMap();
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String sanitizeMapKey(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private void writeConfig(Config config) throws IOException {
        String wiki = Config.OFFICIAL_WIKI_URL;
        AdvancedConfig advanced = AdvancedConfig.defaults();
        if (Files.exists(configPath)) {
            try {
                advanced = AdvancedConfig.load(configPath);
            } catch (RuntimeException ignored) {
            }
        }
        if (wiki.endsWith("/")) {
            wiki = wiki.substring(0, wiki.length() - 1);
        }

        StringBuilder b = new StringBuilder();

        b.append("# ╔══════════════════════════════════════════════════════════════════╗\n");
        b.append("# ║           VelocityNavigator — Configuration File               ║\n");
        b.append("# ║     Lobby routing and load balancing for Velocity proxies       ║\n");
        b.append("# ╠══════════════════════════════════════════════════════════════════╣\n");
        b.append("# ║  Docs & Wiki : ").append(padRight(wiki, 49)).append("║\n");
        b.append("# ║  Support     : https://discord.com/invite/GYsTt96ypf            ║\n");
        b.append("# ║  Telemetry   : https://bstats.org/plugin/velocity/28341         ║\n");
        b.append("# ╚══════════════════════════════════════════════════════════════════╝\n");
        b.append("#   Full bStats page: https://bstats.org/plugin/velocity/Velocity%20Navigator/28341\n");
        b.append("#\n");
        b.append("# This file is auto-generated and self-documenting. Every key has\n");
        b.append("# a description and a link to the relevant wiki section. Feel free\n");
        b.append("# to edit it — your changes are preserved across upgrades.\n");
        b.append("#\n");
        b.append("# Tip: Run /vn reload after saving changes. No proxy restart needed!\n");
        b.append("\n");

        b.append("# Internal config schema version. Do NOT change this manually.\n");
        b.append("# VelocityNavigator uses it to auto-migrate your settings on upgrade.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug-and-top-level-settings\n");
        b.append("config_version = ").append(Config.CURRENT_VERSION).append("\n\n");

        b.append("# Check for plugin updates when the proxy starts.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug-and-top-level-settings\n");
        b.append("notify_on_startup = ").append(config.notifyOnStartup()).append("\n\n");
        b.append("# Show an in-game update notification to admins when they join.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug-and-top-level-settings\n");
        b.append("notify_admins_on_join = ").append(config.notifyAdminsOnJoin()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  STARTUP — First-run welcome & upgrade digest                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[startup]\n\n");
        b.append("# Display a welcome banner on fresh installs and a changelog digest\n");
        b.append("# when upgrading from a previous version.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup-first-run-experience\n");
        b.append("welcome_enabled = ").append(config.startup().welcomeEnabled()).append("\n\n");
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  COMMANDS — Player-facing & admin commands                      │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[commands]\n\n");
        b.append("# Primary lobby command name. Players type /<primary> to navigate.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#primary\n");
        b.append("primary = ").append(quoted(config.commands().primary())).append("\n\n");
        b.append("# Additional command aliases (e.g. /hub, /spawn) that behave like /<primary>.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#aliases\n");
        b.append("aliases = ").append(formatList(config.commands().aliases())).append("\n\n");
        b.append("# Permission node required to use the lobby command.\n");
        b.append("# Set to \"none\" for no permission check (recommended for public servers).\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#permission\n");
        if ("velocitynavigator.use".equalsIgnoreCase(config.commands().permission())) {
            b.append("# NOTE: Default changed to \"none\" in v4.1.0. Review this setting.\n");
        }
        b.append("permission = ").append(quoted(config.commands().permission())).append("\n\n");
        b.append("# Admin command labels. First entry is primary (e.g. /velocitynavigator).\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#admin_aliases\n");
        b.append("admin_aliases = ").append(formatList(config.commands().adminAliases())).append("\n\n");
        b.append("# Cooldown between lobby commands per player (seconds). 0 = disabled.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#cooldown_seconds\n");
        b.append("cooldown_seconds = ").append(config.commands().cooldownSeconds()).append("\n\n");
        b.append("# Allow /lobby to reconnect the player even if they're already on the\n");
        b.append("# selected server. Set to false to show \"already connected\" instead.\n");
        b.append("# Wiki: ").append(wiki).append("/Commands#reconnect_if_same_server\n");
        b.append("reconnect_if_same_server = ").append(config.commands().reconnectIfSameServer()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  ROUTING — The brain of VelocityNavigator                      │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  How players are matched to lobby servers. Choose an algorithm, │\n");
        b.append("# │  list your lobbies, and tune retry/affinity behavior.           │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[routing]\n\n");
        b.append("# Routing algorithm that decides which lobby a player is sent to.\n");
        b.append("#\n");
        b.append("#   least_players       — Fewest players (default, great all-rounder)\n");
        b.append("#   round_robin         — Even rotation across lobbies\n");
        b.append("#   random              — Random pick (simple, fast)\n");
        b.append("#   power_of_two        — Pick 2 random, choose the lighter one (O(1) optimal)\n");
        b.append("#   weighted_round_robin — Proportional traffic based on server weights\n");
        b.append("#   least_connections   — Lowest EMA connection load\n");
        b.append("#   consistent_hash     — Deterministic player-to-server mapping\n");
        b.append("#   latency             — Lowest health-check ping time\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Routing-Algorithms\n");
        b.append("selection_mode = ").append(quoted(config.routing().selectionMode().configValue())).append("\n\n");
        b.append("# Prefer sending players to a DIFFERENT lobby than the one they are on.\n");
        b.append("# Only applies when multiple candidates are available.\n");
        b.append("# Wiki: ").append(wiki).append("/Routing-Algorithms#cycle_when_possible\n");
        b.append("cycle_when_possible = ").append(config.routing().cycleWhenPossible()).append("\n\n");
        b.append("# Load-balance players the moment they connect to the proxy, not only\n");
        b.append("# when they type /lobby. Highly recommended for large networks.\n");
        b.append("# Wiki: ").append(wiki).append("/Initial-Join-Balancing\n");
        b.append("balance_initial_join = ").append(config.routing().balanceInitialJoin()).append("\n\n");
        b.append("# Your lobby servers. Entries can be plain strings or inline tables:\n");
        b.append("#\n");
        b.append("#   \"lobby-1\"                                          — simple\n");
        b.append("#   { server = \"lobby-1\", max_players = 100 }          — with cap\n");
        b.append("#   { server = \"lobby-1\", max_players = 100, weight = 2 } — with cap + weight\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routing-core\n");
        b.append("default_lobbies = ").append(formatLobbyEntryList(config.routing().defaultLobbies())).append("\n\n");
        b.append("# How many times to retry a different lobby if the first connection fails.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routing-core\n");
        b.append("max_retries = ").append(config.routing().maxRetries()).append("\n\n");
        b.append("# Show the configured interactive menu instead of auto-routing.\n");
        b.append("# Players can also type /lobby menu to trigger it manually.\n");
        b.append("use_menu_for_lobby = ").append(config.routing().useMenuForLobby()).append("\n\n");

        b.append("# Java selector: \"inventory\" uses the backend bridge; \"chat\" uses hover/click text.\n");
        b.append("# Install this same JAR on backend Paper/Spigot servers for inventory mode.\n");
        b.append("[routing.java_menu]\n");
        b.append("type = ").append(quoted(config.routing().javaMenuType().configValue())).append("\n");
        b.append("fallback_to_chat = ").append(config.routing().inventoryMenu().fallbackToChat()).append("\n\n");

        b.append("# ── Player Affinity (Sticky Sessions) ──────────────────────────────\n");
        b.append("[routing.affinity]\n\n");
        b.append("# Remember which lobby a player was on and try to send them back.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routingaffinity\n");
        b.append("enabled = ").append(config.routing().affinity().enabled()).append("\n\n");
        b.append("# Probability (0.0–1.0) of returning the player to their last lobby.\n");
        b.append("# 0.0 = never sticky, 1.0 = always sticky.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routingaffinity\n");
        b.append("stickiness = ").append(config.routing().affinity().stickiness()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  CONTEXTUAL ROUTING — Game-mode-aware lobby selection           │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  Route players to specific lobbies depending on which server    │\n");
        b.append("# │  they are leaving. Great for game modes with dedicated lobbies.  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[routing.contextual]\n\n");
        b.append("# Enable source-server-aware routing.\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide\n");
        b.append("enabled = ").append(config.routing().contextual().enabled()).append("\n\n");
        b.append("# If no servers in the matched group are online, fall back to default lobbies.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routingcontextual\n");
        b.append("fallback_to_default = ").append(config.routing().contextual().fallbackToDefault()).append("\n\n");

        b.append("# ── Contextual Groups ───────────────────────────────────────────────\n");
        b.append("# Define named groups of lobby servers. Each group can optionally\n");
        b.append("# override the global selection_mode with its own \"mode\" field.\n");
        b.append("#\n");
        b.append("# Example:\n");
        b.append("#   [routing.contextual.groups]\n");
        b.append("#   \"bedwars\"  = { servers = [\"bw-lobby-1\", \"bw-lobby-2\"], mode = \"round_robin\" }\n");
        b.append("#   \"skyblock\" = [\"sb-lobby-1\"]\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide#groups\n");
        b.append("[routing.contextual.groups]\n");
        for (Map.Entry<String, Config.GroupConfig> entry : config.routing().contextual().groups().entrySet()) {
            if (entry.getValue().mode() != null) {
                b.append(quoted(entry.getKey())).append(" = { servers = ").append(formatLobbyEntryList(entry.getValue().servers())).append(", mode = ").append(quoted(entry.getValue().mode().configValue())).append(" }\n");
            } else {
                b.append(quoted(entry.getKey())).append(" = ").append(formatLobbyEntryList(entry.getValue().servers())).append("\n");
            }
        }
        b.append("\n");

        b.append("# ── Source Mappings ────────────────────────────────────────────────\n");
        b.append("# Map a source server name → contextual group name.\n");
        b.append("# When a player leaves \"bedwars-1\", they'll be routed to the \"bedwars\" group.\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide#sources\n");
        b.append("[routing.contextual.sources]\n");
        for (Map.Entry<String, String> entry : config.routing().contextual().sources().entrySet()) {
            b.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append("\n");
        }
        b.append("\n");

        if (!config.routing().contextual().fallbackChain().isEmpty()) {
            b.append("# ── Fallback Chain ────────────────────────────────────────────────\n");
            b.append("# Ordered list of fallback groups to try before using default lobbies.\n");
            b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#fallback-chain\n");
            b.append("[routing.contextual.fallback_chain]\n");
            for (Map.Entry<String, List<String>> entry : config.routing().contextual().fallbackChain().entrySet()) {
                b.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append("\n");
            }
            b.append("\n");
        }

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  LOBBY FALLBACK — What happens when no lobbies are available    │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[lobby]\n\n");
        b.append("# What to do when every lobby is offline or circuit-broken.\n");
        b.append("#   \"disconnect\"      — Disconnect the player with a message\n");
        b.append("#   \"fallback_server\" — Route to a backup fallback server instead\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby-empty-lobby-strategy\n");
        b.append("no_server_strategy = ").append(quoted(config.lobbyFallback().noServerStrategy())).append("\n\n");
        b.append("# The disconnect text is lobby.no_server_message in messages.toml.\n");
        b.append("# Backup server name used when strategy = \"fallback_server\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#lobby-empty-lobby-strategy\n");
        b.append("fallback_server = ").append(quoted(config.lobbyFallback().fallbackServer())).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  HEALTH CHECKS — Verify servers are alive before routing        │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[health_checks]\n\n");
        b.append("# Ping candidate lobbies before sending a player. Prevents routing\n");
        b.append("# to offline servers. Strongly recommended.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks\n");
        b.append("enabled = ").append(config.healthChecks().enabled()).append("\n\n");
        b.append("# Timeout in milliseconds before a health-check ping is considered failed.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks\n");
        b.append("timeout_ms = ").append(config.healthChecks().timeoutMs()).append("\n\n");
        b.append("# Cache health results for this many seconds. Reduces network load.\n");
        b.append("# Set to 0 to ping on every request (not recommended for large networks).\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#health_checks\n");
        b.append("cache_seconds = ").append(config.healthChecks().cacheSeconds()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  CIRCUIT BREAKER — Automatic failure detection & recovery       │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  State machine: CLOSED → OPEN → HALF_OPEN → CLOSED             │\n");
        b.append("# │  Unhealthy servers are skipped until they prove they've recovered. │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[circuit_breaker]\n\n");
        b.append("# Enable the circuit breaker. When enabled, servers that fail\n");
        b.append("# health checks repeatedly are temporarily removed from routing.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker\n");
        b.append("enabled = ").append(config.circuitBreaker().enabled()).append("\n\n");
        b.append("# Number of consecutive failures before the circuit trips OPEN.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker\n");
        b.append("failure_threshold = ").append(config.circuitBreaker().failureThreshold()).append("\n\n");
        b.append("# Seconds to wait in OPEN state before allowing a test in HALF_OPEN.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker\n");
        b.append("cooldown_seconds = ").append(config.circuitBreaker().cooldownSeconds()).append("\n\n");
        b.append("# Successful test connections needed in HALF_OPEN to close the circuit.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#circuit_breaker\n");
        b.append("half_open_max_tests = ").append(config.circuitBreaker().halfOpenMaxTests()).append("\n\n");

        b.append("# Player-facing text and menu labels are stored in messages.toml.\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  UPDATE CHECKER — Automatic Modrinth version checking           │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[update_checker]\n\n");
        b.append("# Enable periodic update checking via the Modrinth API.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker\n");
        b.append("enabled = ").append(config.updateChecker().enabled()).append("\n\n");
        b.append("# Which release channel to follow: \"release\", \"beta\", or \"alpha\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker\n");
        b.append("channel = ").append(quoted(config.updateChecker().channel().configValue())).append("\n\n");
        b.append("# Minutes between update checks (minimum: 30). Backoff is applied\n");
        b.append("# automatically if the API returns 429 Too Many Requests.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker\n");
        b.append("check_interval = ").append(config.updateChecker().checkIntervalMinutes()).append("\n\n");
        b.append("# Notify online admins (velocitynavigator.admin) when they join.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker\n");
        b.append("notify_admins = ").append(config.updateChecker().notifyAdmins()).append("\n\n");
        b.append("# Suppress console log output for update checks. The /vn updatecheck\n");
        b.append("# command still works regardless of this setting.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#update_checker\n");
        b.append("silent = ").append(config.updateChecker().silent()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  BEDROCK — Geyser/Floodgate integration for Bedrock players    │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[bedrock]\n\n");
        b.append("# Manually enable Bedrock support. When false, auto_detect takes over.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock-bedrockgeyser-support\n");
        b.append("enabled = ").append(config.bedrock().enabled()).append("\n\n");
        b.append("# Automatically enable Bedrock features if Geyser/Floodgate is detected.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock-bedrockgeyser-support\n");
        b.append("auto_detect = ").append(config.bedrock().autoDetect()).append("\n\n");
        b.append("# Strip gradients, hover events, and click actions for Bedrock clients.\n");
        b.append("# Bedrock doesn't support advanced MiniMessage formatting natively.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock-bedrockgeyser-support\n");
        b.append("strip_advanced_formatting = ").append(config.bedrock().stripAdvancedFormatting()).append("\n\n");
        b.append("# Use the Java UUID (mapped by Floodgate) for player affinity tracking.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#bedrock-bedrockgeyser-support\n");
        b.append("affinity_use_java_uuid = ").append(config.bedrock().affinityUseJavaUuid()).append("\n\n");
        b.append("# Show a native Bedrock SimpleForm GUI instead of chat-based menu.\n");
        b.append("# Requires Floodgate to be installed alongside Geyser.\n");
        b.append("use_gui_for_lobby = ").append(config.bedrock().useGuiForLobby()).append("\n\n");
        b.append("# Bedrock form title/content/button text is stored in messages.toml.\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  METRICS — Telemetry & monitoring                               │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[metrics]\n\n");
        b.append("# Enable anonymous bStats telemetry. Helps us understand plugin usage.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#metrics\n");
        b.append("enabled = ").append(config.metrics().enabled()).append("\n\n");

        b.append("# ── Prometheus Exporter ─────────────────────────────────────────────\n");
        b.append("# Exposes real-time metrics at http://<bind_host>:<port>/metrics\n");
        b.append("# Compatible with Prometheus, Grafana, and any OpenMetrics scraper.\n");
        b.append("# Tip: Run /vn setup grafana to generate a ready-made dashboard!\n");
        b.append("[metrics.prometheus]\n");
        b.append("enabled = ").append(config.metrics().prometheus().enabled()).append("\n");
        b.append("port = ").append(config.metrics().prometheus().port()).append("\n");
        b.append("bind_host = ").append(quoted(config.metrics().prometheus().bindHost())).append("\n\n");
        b.append("# Optional bearer token for authentication. Strongly recommended if\n");
        b.append("# bind_host is not 127.0.0.1/localhost. Leave empty to disable.\n");
        b.append("bearer_token = ").append(quoted(config.metrics().prometheus().bearerToken())).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DASHBOARD — HTML operations panel                              │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("# Serves a live HTML dashboard on a separate port. Shows lobby table,\n");
        b.append("# routing distribution, affinity count, config summary, and live counters.\n");
        b.append("# Optional operations dashboard. Disabled by default.\n");
        b.append("[dashboard]\n\n");
        b.append("# Enable the HTML dashboard. Default: false.\n");
        b.append("enabled = ").append(config.dashboard().enabled()).append("\n\n");
        b.append("# Port for the dashboard HTTP server. Default: 9226.\n");
        b.append("port = ").append(config.dashboard().port()).append("\n\n");
        b.append("# Bind address. Use 127.0.0.1 for local-only access, or 0.0.0.0 for\n");
        b.append("# network exposure (only if firewalled or behind a token).\n");
        b.append("bind_host = ").append(quoted(config.dashboard().bindHost())).append("\n\n");
        b.append("# Bearer token for authentication. If set, requests must include\n");
        b.append("# \"Authorization: Bearer <token>\". The browser login uses this header.\n");
        b.append("# Never place bearer tokens in URLs. Leave empty\n");
        b.append("# to allow unauthenticated access (only safe on loopback).\n");
        b.append("bearer_token = ").append(quoted(config.dashboard().bearerToken())).append("\n\n");
        b.append("# Auto-refresh interval in seconds. Default: 5.\n");
        b.append("refresh_seconds = ").append(config.dashboard().refreshSeconds()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DEGRADATION — Graceful fallback when everything is down        │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[degradation]\n\n");
        b.append("# When all health checks fail, use this fallback routing mode instead\n");
        b.append("# of showing \"no lobby found\". Useful for keeping players connected.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation\n");
        b.append("enabled = ").append(config.degradation().enabled()).append("\n\n");
        b.append("# Fallback algorithm: \"random\", \"round_robin\", or \"least_players\".\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#degradation\n");
        b.append("mode = ").append(quoted(config.degradation().mode())).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  GEO ROUTING — Reserved compatibility settings                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[geo_routing]\n\n");
        b.append("# Retained so older configuration files continue to load.\n");
        b.append("# Geographic routing is not active in 4.4.0; keep this disabled.\n");
        b.append("enabled = ").append(config.geoRouting().enabled()).append("\n\n");
        b.append("# Absolute path to the GeoLite2-Country.mmdb or GeoLite2-City.mmdb file.\n");
        b.append("database_path = ").append(quoted(config.geoRouting().databasePath())).append("\n\n");

        b.append("# Native proxy party system and leader-follow behavior.\n");
        b.append("[party]\n");
        b.append("enabled = ").append(advanced.party().enabled()).append("\n");
        b.append("invite_timeout_seconds = ").append(advanced.party().inviteTimeoutSeconds()).append("\n");
        b.append("follow_leader = ").append(advanced.party().followLeader()).append("\n\n");
        b.append("max_size = ").append(advanced.party().maxSize()).append("\n");
        b.append("command = ").append(quoted(advanced.party().command())).append("\n");
        b.append("chat_command = ").append(quoted(advanced.party().chatCommand())).append("\n");
        b.append("permission = ").append(quoted(advanced.party().permission())).append("\n\n");

        b.append("# Capacity queue. holding_server names an existing backend in velocity.toml.\n");
        b.append("# VN does not create that server or its world. Keep it outside lobby pools,\n");
        b.append("# design its waiting world freely, and size it for the expected queue.\n");
        b.append("[queue]\n");
        b.append("enabled = ").append(advanced.queue().enabled()).append("\n");
        b.append("poll_seconds = ").append(advanced.queue().pollSeconds()).append("\n");
        b.append("notify_seconds = ").append(advanced.queue().notifySeconds()).append("\n");
        b.append("max_size = ").append(advanced.queue().maxSize()).append("\n");
        b.append("holding_server = ").append(quoted(advanced.queue().holdingServer())).append("\n\n");
        b.append("command = ").append(quoted(advanced.queue().command())).append("\n");
        b.append("permission = ").append(quoted(advanced.queue().permission())).append("\n\n");

        b.append("# Redis enables multi-proxy dynamic registration and state synchronization.\n");
        b.append("# Backends publish JSON registration events to vn:servers:register.\n");
        b.append("[redis]\n");
        b.append("enabled = ").append(advanced.redis().enabled()).append("\n");
        b.append("host = ").append(quoted(advanced.redis().host())).append("\n");
        b.append("port = ").append(advanced.redis().port()).append("\n");
        b.append("username = ").append(quoted(advanced.redis().username())).append("\n");
        b.append("password = ").append(quoted(advanced.redis().password())).append("\n");
        b.append("ssl = ").append(advanced.redis().ssl()).append("\n");
        b.append("node_id = ").append(quoted(advanced.redis().nodeId())).append("\n");
        b.append("channel_prefix = ").append(quoted(advanced.redis().channelPrefix())).append("\n");
        b.append("sync_seconds = ").append(advanced.redis().syncSeconds()).append("\n\n");
        b.append("connect_timeout_ms = ").append(advanced.redis().connectTimeoutMs()).append("\n");
        b.append("read_timeout_ms = ").append(advanced.redis().readTimeoutMs()).append("\n");
        b.append("reconnect_min_ms = ").append(advanced.redis().reconnectMinMs()).append("\n");
        b.append("reconnect_max_ms = ").append(advanced.redis().reconnectMaxMs()).append("\n");
        b.append("registration_secret = ").append(quoted(advanced.redis().registrationSecret())).append("\n");
        b.append("registration_max_age_seconds = ").append(advanced.redis().registrationMaxAgeSeconds()).append("\n");
        b.append("allowed_registration_hosts = ").append(formatList(advanced.redis().allowedRegistrationHosts())).append("\n\n");

        b.append("# MOTD markers such as [STATE:IN_GAME] can remove backends from routing.\n");
        b.append("[backend_states]\n");
        b.append("enabled = ").append(advanced.backendStates().enabled()).append("\n");
        b.append("allowed = ").append(formatList(advanced.backendStates().allowed())).append("\n");
        b.append("allow_unknown = ").append(advanced.backendStates().allowUnknown()).append("\n\n");

        b.append("# Optional /vn server persistence into Velocity and the lobby registry.\n");
        b.append("[server_management]\n");
        b.append("enabled = ").append(advanced.serverManagement().enabled()).append("\n");
        b.append("velocity_config = ").append(quoted(advanced.serverManagement().velocityConfig())).append("\n");
        b.append("allow_overwrite = ").append(advanced.serverManagement().allowOverwrite()).append("\n\n");

        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DEBUG — Diagnostic logging                                     │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[debug]\n\n");
        b.append("# Print detailed routing decisions, health check results, and cache\n");
        b.append("# events to the proxy console. Useful for troubleshooting.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug-and-top-level-settings\n");
        b.append("verbose_logging = ").append(config.debug().verboseLogging()).append("\n\n");

        b.append("# ╔══════════════════════════════════════════════════════════════════╗\n");
        b.append("# ║  Thank you for using VelocityNavigator!                        ║\n");
        b.append("# ║  Built with ❤ by DemonZ Development                            ║\n");
        b.append("# ║                                                                 ║\n");
        b.append("# ║  Questions? → https://discord.com/invite/GYsTt96ypf             ║\n");
        b.append("# ║  Found a bug? → https://github.com/DemonZ-Development/           ║\n");
        b.append("# ║                 VelocityNavigator/issues                        ║\n");
        b.append("# ╚══════════════════════════════════════════════════════════════════╝\n");

        java.nio.file.Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tempPath, b.toString());
        try {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String padRight(String text, int length) {
        if (text == null) text = "";
        if (text.length() >= length) return text;
        return text + " ".repeat(length - text.length());
    }

    private String formatLobbyEntryList(List<Config.LobbyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }
        List<String> items = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            boolean needsTable = entry.maxPlayers() != Config.LobbyEntry.UNCAPPED || entry.weight() != Config.LobbyEntry.DEFAULT_WEIGHT;
            if (needsTable) {
                StringBuilder sb = new StringBuilder("{ server = ").append(quoted(entry.server()));
                if (entry.maxPlayers() != Config.LobbyEntry.UNCAPPED) {
                    sb.append(", max_players = ").append(entry.maxPlayers());
                }
                if (entry.weight() != Config.LobbyEntry.DEFAULT_WEIGHT) {
                    sb.append(", weight = ").append(entry.weight());
                }
                sb.append(" }");
                items.add(sb.toString());
            } else {
                items.add(quoted(entry.server()));
            }
        }
        return "[" + String.join(", ", items) + "]";
    }

    private String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(quoted(value));
        }
        return "[" + String.join(", ", escaped) + "]";
    }

    private String quoted(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }

    private static final class ParseState {
        private final List<String> warnings = new ArrayList<>();
        private boolean normalized;
    }
}
