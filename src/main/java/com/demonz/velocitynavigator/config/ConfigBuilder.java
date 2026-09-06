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
import com.moandjiezana.toml.Toml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ConfigBuilder {

    private ConfigBuilder() {
    }

    static Config buildConfig(Toml toml, ParseState state, int sourceVersion, LanguageBundle language, Path dataDirectory) {
        Config defaults = Config.defaults();

        String rawSelectionMode = TomlReaderUtils.readString(
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
                "power_of_two", "weighted_round_robin", "least_connections", "consistent_hash", "latency", "geo_distance");
        if (!validModes.contains(rawSelectionMode.trim().toLowerCase(Locale.ROOT))) {
            state.warnings.add("routing.selection_mode was invalid, so it was reset to " + selectionMode.configValue() + ".");
            state.normalized = true;
        }

        Config.Commands commands = new Config.Commands(
                TomlReaderUtils.readString(toml, state, "commands.primary", defaults.commands().primary(), "commands.primary"),
                TomlReaderUtils.readStringList(toml, state, "commands.aliases", defaults.commands().aliases(), "commands.aliases", "command_aliases"),
                TomlReaderUtils.readString(toml, state, "commands.permission", defaults.commands().permission(), "commands.permission"),
                TomlReaderUtils.readStringList(toml, state, "commands.admin_aliases", defaults.commands().adminAliases(), "commands.admin_aliases"),
                TomlReaderUtils.readInt(toml, state, "commands.cooldown_seconds", defaults.commands().cooldownSeconds(), "commands.cooldown_seconds", "command_cooldown"),
                TomlReaderUtils.readBoolean(toml, state, "commands.reconnect_if_same_server", defaults.commands().reconnectIfSameServer(), "commands.reconnect_if_same_server", "reconnect_on_lobby_command")
        );

        Map<String, Config.GroupConfig> groupConfigs = TomlReaderUtils.readGroupConfigMap(toml, state, "routing.contextual.groups", "routing.contextual.groups", "contextual_lobbies.groups");

        Map<String, List<String>> fallbackChain = TomlReaderUtils.readStringListMap(toml, state, "routing.contextual.fallback_chain", "routing.contextual.fallback_chain");

        Config.Contextual contextual = new Config.Contextual(
                TomlReaderUtils.readBoolean(toml, state, "routing.contextual.enabled", defaults.routing().contextual().enabled(), "routing.contextual.enabled", "advanced_settings.use_contextual_lobbies"),
                TomlReaderUtils.readBoolean(toml, state, "routing.contextual.fallback_to_default", defaults.routing().contextual().fallbackToDefault(), "routing.contextual.fallback_to_default"),
                groupConfigs,
                TomlReaderUtils.readStringMap(toml, state, "routing.contextual.sources", "routing.contextual.sources", "contextual_lobbies.mappings"),
                fallbackChain
        );

        List<Config.LobbyEntry> defaultLobbies = TomlReaderUtils.readLobbyEntryList(
                toml,
                state,
                "routing.default_lobbies",
                defaults.routing().defaultLobbies(),
                "routing.default_lobbies",
                "settings.lobby_servers",
                "lobby_servers"
        );

        int maxRetries = TomlReaderUtils.readInt(toml, state, "routing.max_retries", defaults.routing().maxRetries(), "routing.max_retries");

        Config.AffinitySettings affinity = new Config.AffinitySettings(
                TomlReaderUtils.readBoolean(toml, state, "routing.affinity.enabled", defaults.routing().affinity().enabled(), "routing.affinity.enabled"),
                TomlReaderUtils.readDouble(toml, state, "routing.affinity.stickiness", defaults.routing().affinity().stickiness(), "routing.affinity.stickiness")
        );

        Config.Routing routing = new Config.Routing(
                selectionMode,
                TomlReaderUtils.readBoolean(toml, state, "routing.cycle_when_possible", defaults.routing().cycleWhenPossible(), "routing.cycle_when_possible", "cycle_lobbies"),
                TomlReaderUtils.readBoolean(toml, state, "routing.balance_initial_join", defaults.routing().balanceInitialJoin(), "routing.balance_initial_join"),
                defaultLobbies,
                contextual,
                maxRetries,
                affinity,
                TomlReaderUtils.readBoolean(toml, state, "routing.use_menu_for_lobby", defaults.routing().useChatMenuForLobby(), "routing.use_menu_for_lobby", "routing.use_chat_menu_for_lobby"),
                language.text("menus.chat.header"),
                language.text("menus.chat.entry"),
                language.text("menus.chat.tooltip"),
                Config.JavaMenuType.fromString(TomlReaderUtils.readString(toml, state, "routing.java_menu.type", defaults.routing().javaMenuType().configValue(), "routing.java_menu.type")),
                new Config.InventoryMenuSettings(
                        TomlReaderUtils.readInt(toml, state, "routing.java_menu.rows", defaults.routing().inventoryMenu().rows(), "routing.java_menu.rows"),
                        TomlReaderUtils.readString(toml, state, "routing.java_menu.material", defaults.routing().inventoryMenu().material(), "routing.java_menu.material"),
                        TomlReaderUtils.readBoolean(toml, state, "routing.java_menu.fallback_to_chat", defaults.routing().inventoryMenu().fallbackToChat(), "routing.java_menu.fallback_to_chat")
                )
        );

        Config.HealthChecks healthChecks = new Config.HealthChecks(
                TomlReaderUtils.readBoolean(toml, state, "health_checks.enabled", defaults.healthChecks().enabled(), "health_checks.enabled", "ping_before_connect"),
                TomlReaderUtils.readInt(toml, state, "health_checks.timeout_ms", defaults.healthChecks().timeoutMs(), "health_checks.timeout_ms"),
                TomlReaderUtils.readInt(toml, state, "health_checks.cache_seconds", defaults.healthChecks().cacheSeconds(), "health_checks.cache_seconds", "ping_cache_duration")
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
            Object oldEnabled = TomlReaderUtils.rawValue(toml, "update_checker.enabled");
            if (oldEnabled instanceof Boolean && !(Boolean) oldEnabled) {
                enabled = false;
            }
            Config.UpdateChannel channel = Config.UpdateChannel.fromString(TomlReaderUtils.readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel"));
            int checkInterval = TomlReaderUtils.readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval");
            boolean notifyAdmins = TomlReaderUtils.readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins");
            boolean silent = TomlReaderUtils.readBoolean(toml, state, "update_checker.silent", defaults.updateChecker().silent(), "update_checker.silent");
            updateChecker = new Config.UpdateCheckerSettings(enabled, channel, checkInterval, notifyAdmins, silent);
        } else {
            updateChecker = new Config.UpdateCheckerSettings(
                    TomlReaderUtils.readBoolean(toml, state, "update_checker.enabled", defaults.updateChecker().enabled(), "update_checker.enabled"),
                    Config.UpdateChannel.fromString(TomlReaderUtils.readString(toml, state, "update_checker.channel", defaults.updateChecker().channel().configValue(), "update_checker.channel")),
                    TomlReaderUtils.readInt(toml, state, "update_checker.check_interval", defaults.updateChecker().checkIntervalMinutes(), "update_checker.check_interval"),
                    TomlReaderUtils.readBoolean(toml, state, "update_checker.notify_admins", defaults.updateChecker().notifyAdmins(), "update_checker.notify_admins"),
                    TomlReaderUtils.readBoolean(toml, state, "update_checker.silent", defaults.updateChecker().silent(), "update_checker.silent")
            );
        }

        Config.MetricsSettings metrics = new Config.MetricsSettings(
                TomlReaderUtils.readBoolean(toml, state, "metrics.enabled", defaults.metrics().enabled(), "metrics.enabled"),
                new Config.PrometheusSettings(
                        TomlReaderUtils.readBoolean(toml, state, "metrics.prometheus.enabled", defaults.metrics().prometheus().enabled(), "metrics.prometheus.enabled"),
                        TomlReaderUtils.readInt(toml, state, "metrics.prometheus.port", defaults.metrics().prometheus().port(), "metrics.prometheus.port"),
                        TomlReaderUtils.readString(toml, state, "metrics.prometheus.bind_host", defaults.metrics().prometheus().bindHost(), "metrics.prometheus.bind_host", "metrics.prometheus.bindHost"),
                        TomlReaderUtils.readString(toml, state, "metrics.prometheus.bearer_token", defaults.metrics().prometheus().bearerToken(), "metrics.prometheus.bearer_token", "metrics.prometheus.bearerToken")
                )
        );

        Config.DebugSettings debug = new Config.DebugSettings(
                TomlReaderUtils.readBoolean(toml, state, "debug.verbose_logging", defaults.debug().verboseLogging(), "debug.verbose_logging")
        );

        Config.CircuitBreakerSettings circuitBreakerSettings = new Config.CircuitBreakerSettings(
                TomlReaderUtils.readBoolean(toml, state, "circuit_breaker.enabled", defaults.circuitBreaker().enabled(), "circuit_breaker.enabled"),
                TomlReaderUtils.readInt(toml, state, "circuit_breaker.failure_threshold", defaults.circuitBreaker().failureThreshold(), "circuit_breaker.failure_threshold"),
                TomlReaderUtils.readInt(toml, state, "circuit_breaker.cooldown_seconds", defaults.circuitBreaker().cooldownSeconds(), "circuit_breaker.cooldown_seconds"),
                TomlReaderUtils.readInt(toml, state, "circuit_breaker.half_open_max_tests", defaults.circuitBreaker().halfOpenMaxTests(), "circuit_breaker.half_open_max_tests")
        );

        Config.DegradationSettings degradationSettings = new Config.DegradationSettings(
                TomlReaderUtils.readBoolean(toml, state, "degradation.enabled", defaults.degradation().enabled(), "degradation.enabled"),
                TomlReaderUtils.readString(toml, state, "degradation.mode", defaults.degradation().mode(), "degradation.mode")
        );

        Toml geoToml = toml;
        Path geoFile = dataDirectory.resolve("geo.toml");
        if (Files.exists(geoFile)) {
            try { geoToml = new Toml().read(geoFile.toFile()); } catch (Exception e) {
                state.warnings.add("Failed to parse geo.toml: " + e.getMessage() + ". Using navigator.toml values.");
            }
        }

        Config.GeoRoutingSettings geoRoutingSettings = new Config.GeoRoutingSettings(
                TomlReaderUtils.readBoolean(geoToml, state, "geo_routing.enabled", TomlReaderUtils.readBoolean(toml, state, "geo_routing.enabled", defaults.geoRouting().enabled(), "geo_routing.enabled"), "geo_routing.enabled"),
                TomlReaderUtils.readString(geoToml, state, "geo_routing.database_path", TomlReaderUtils.readString(toml, state, "geo_routing.database_path", defaults.geoRouting().databasePath(), "geo_routing.database_path"), "geo_routing.database_path"),
                TomlReaderUtils.readString(geoToml, state, "geo_routing.provider", TomlReaderUtils.readString(toml, state, "geo_routing.provider", defaults.geoRouting().provider(), "geo_routing.provider"), "geo_routing.provider"),
                TomlReaderUtils.readBoolean(geoToml, state, "geo_routing.fallback_enabled", TomlReaderUtils.readBoolean(toml, state, "geo_routing.fallback_enabled", defaults.geoRouting().fallbackEnabled(), "geo_routing.fallback_enabled"), "geo_routing.fallback_enabled"),
                TomlReaderUtils.readString(geoToml, state, "geo_routing.fallback_mode", TomlReaderUtils.readString(toml, state, "geo_routing.fallback_mode", defaults.geoRouting().fallbackMode(), "geo_routing.fallback_mode"), "geo_routing.fallback_mode"),
                TomlReaderUtils.readStringMapOfLists(geoToml, state, "geo_routing.affinity_countries", defaults.geoRouting().affinityCountries(), "geo_routing.affinity_countries")
        );

        boolean notifyOnStartup = TomlReaderUtils.readBoolean(toml, state, "notify_on_startup", defaults.notifyOnStartup(), "notify_on_startup");
        boolean notifyAdminsOnJoin = TomlReaderUtils.readBoolean(toml, state, "notify_admins_on_join", defaults.notifyAdminsOnJoin(), "notify_admins_on_join");

        if (TomlReaderUtils.rawValue(toml, "startup.wiki_url") != null) {
            state.normalized = true;
            state.warnings.add("Removed legacy startup.wiki_url; documentation links now always use the official VelocityNavigator wiki.");
        }
        Config.StartupSettings startup = new Config.StartupSettings(
                TomlReaderUtils.readBoolean(toml, state, "startup.welcome_enabled", defaults.startup().welcomeEnabled(), "startup.welcome_enabled"),
                Config.OFFICIAL_WIKI_URL
        );

        Config.LobbyFallbackSettings lobbyFallback = new Config.LobbyFallbackSettings(
                TomlReaderUtils.readString(toml, state, "lobby.no_server_strategy", defaults.lobbyFallback().noServerStrategy(), "lobby.no_server_strategy"),
                language.text("lobby.no_server_message"),
                TomlReaderUtils.readString(toml, state, "lobby.fallback_server", defaults.lobbyFallback().fallbackServer(), "lobby.fallback_server")
        );

        Config.BedrockSettings bedrock = new Config.BedrockSettings(
                TomlReaderUtils.readBoolean(toml, state, "bedrock.enabled", defaults.bedrock().enabled(), "bedrock.enabled"),
                TomlReaderUtils.readBoolean(toml, state, "bedrock.auto_detect", defaults.bedrock().autoDetect(), "bedrock.auto_detect"),
                TomlReaderUtils.readBoolean(toml, state, "bedrock.strip_advanced_formatting", defaults.bedrock().stripAdvancedFormatting(), "bedrock.strip_advanced_formatting"),
                TomlReaderUtils.readBoolean(toml, state, "bedrock.affinity_use_java_uuid", defaults.bedrock().affinityUseJavaUuid(), "bedrock.affinity_use_java_uuid"),
                TomlReaderUtils.readBoolean(toml, state, "bedrock.use_gui_for_lobby", defaults.bedrock().useGuiForLobby(), "bedrock.use_gui_for_lobby"),
                language.text("menus.bedrock.title"),
                language.text("menus.bedrock.content"),
                language.text("menus.bedrock.button")
        );

        Config.DashboardSettings dashboard = new Config.DashboardSettings(
                TomlReaderUtils.readBoolean(toml, state, "dashboard.enabled", defaults.dashboard().enabled(), "dashboard.enabled"),
                TomlReaderUtils.readInt(toml, state, "dashboard.port", defaults.dashboard().port(), "dashboard.port"),
                TomlReaderUtils.readString(toml, state, "dashboard.bind_host", defaults.dashboard().bindHost(), "dashboard.bind_host"),
                TomlReaderUtils.readString(toml, state, "dashboard.bearer_token", defaults.dashboard().bearerToken(), "dashboard.bearer_token"),
                TomlReaderUtils.readInt(toml, state, "dashboard.refresh_seconds", defaults.dashboard().refreshSeconds(), "dashboard.refresh_seconds")
        );

        Toml storageToml = toml;
        Path storageFile = dataDirectory.resolve("storage.toml");
        Path dbFile = dataDirectory.resolve("db.toml");
        if (Files.exists(storageFile)) {
            try { storageToml = new Toml().read(storageFile.toFile()); } catch (Exception e) {
                state.warnings.add("Failed to parse storage.toml: " + e.getMessage() + ". Using navigator.toml values.");
            }
        } else if (Files.exists(dbFile)) {
            try { storageToml = new Toml().read(dbFile.toFile()); } catch (Exception e) {
                state.warnings.add("Failed to parse db.toml: " + e.getMessage() + ". Using navigator.toml values.");
            }
        }

        Config.StorageConfig storage = new Config.StorageConfig(
                TomlReaderUtils.readString(storageToml, state, "storage.type", TomlReaderUtils.readString(toml, state, "storage.type", defaults.storage().type(), "storage.type"), "storage.type"),
                TomlReaderUtils.readString(storageToml, state, "storage.host", TomlReaderUtils.readString(toml, state, "storage.host", defaults.storage().host(), "storage.host"), "storage.host"),
                TomlReaderUtils.readInt(storageToml, state, "storage.port", TomlReaderUtils.readInt(toml, state, "storage.port", defaults.storage().port(), "storage.port"), "storage.port"),
                TomlReaderUtils.readString(storageToml, state, "storage.database", TomlReaderUtils.readString(toml, state, "storage.database", defaults.storage().database(), "storage.database"), "storage.database"),
                TomlReaderUtils.readString(storageToml, state, "storage.username", TomlReaderUtils.readString(toml, state, "storage.username", defaults.storage().username(), "storage.username"), "storage.username"),
                TomlReaderUtils.readString(storageToml, state, "storage.password", TomlReaderUtils.readString(toml, state, "storage.password", defaults.storage().password(), "storage.password"), "storage.password"),
                TomlReaderUtils.readString(storageToml, state, "storage.sqlite_file", TomlReaderUtils.readString(toml, state, "storage.sqlite_file", defaults.storage().sqliteFile(), "storage.sqlite_file"), "storage.sqlite_file"),
                TomlReaderUtils.readInt(storageToml, state, "storage.pool_size", TomlReaderUtils.readInt(toml, state, "storage.pool_size", defaults.storage().poolSize(), "storage.pool_size"), "storage.pool_size"),
                (long) TomlReaderUtils.readInt(storageToml, state, "storage.connection_timeout_ms", TomlReaderUtils.readInt(toml, state, "storage.connection_timeout_ms", (int) defaults.storage().connectionTimeoutMs(), "storage.connection_timeout_ms"), "storage.connection_timeout_ms")
        );

        Toml authToml = toml;
        Path authFile = dataDirectory.resolve("auth.toml");
        if (Files.exists(authFile)) {
            try { authToml = new Toml().read(authFile.toFile()); } catch (Exception e) {
                state.warnings.add("Failed to parse auth.toml: " + e.getMessage() + ". Using navigator.toml values.");
            }
        }

        Object authEnabledVal = TomlReaderUtils.rawValue(authToml, "auth.enabled");
        if (authEnabledVal == null) authEnabledVal = TomlReaderUtils.rawValue(authToml, "enabled");
        if (authEnabledVal == null) authEnabledVal = TomlReaderUtils.rawValue(toml, "auth.enabled");
        boolean authEnabled = authEnabledVal instanceof Boolean b ? b : defaults.auth().enabled();

        Object authAlgVal = TomlReaderUtils.rawValue(authToml, "auth.algorithm");
        if (authAlgVal == null) authAlgVal = TomlReaderUtils.rawValue(authToml, "algorithm");
        if (authAlgVal == null) authAlgVal = TomlReaderUtils.rawValue(toml, "auth.algorithm");
        String authAlg = authAlgVal instanceof String s ? s : defaults.auth().algorithm();

        Object auth2faVal = TomlReaderUtils.rawValue(authToml, "auth.enable_2fa");
        if (auth2faVal == null) auth2faVal = TomlReaderUtils.rawValue(authToml, "enable_2fa");
        if (auth2faVal == null) auth2faVal = TomlReaderUtils.rawValue(toml, "auth.enable_2fa");
        boolean auth2fa = auth2faVal instanceof Boolean b ? b : defaults.auth().enable2fa();

        Object pinLenVal = TomlReaderUtils.rawValue(authToml, "auth.pin_length");
        if (pinLenVal == null) pinLenVal = TomlReaderUtils.rawValue(authToml, "pin_length");
        if (pinLenVal == null) pinLenVal = TomlReaderUtils.rawValue(toml, "auth.pin_length");
        int authPinLen = pinLenVal instanceof Number n ? n.intValue() : defaults.auth().pinLength();

        Object voidVal = TomlReaderUtils.rawValue(authToml, "auth.void_world_holding");
        if (voidVal == null) voidVal = TomlReaderUtils.rawValue(authToml, "void_world_holding");
        if (voidVal == null) voidVal = TomlReaderUtils.rawValue(toml, "auth.void_world_holding");
        boolean authVoidHolding = voidVal instanceof Boolean b ? b : defaults.auth().voidWorldHolding();

        Object holdingVal = TomlReaderUtils.rawValue(authToml, "auth.holding_server");
        if (holdingVal == null) holdingVal = TomlReaderUtils.rawValue(authToml, "holding_server");
        if (holdingVal == null) holdingVal = TomlReaderUtils.rawValue(toml, "auth.holding_server");
        String authHoldingServer = holdingVal instanceof String s ? s : defaults.auth().holdingServer();

        Object timeoutVal = TomlReaderUtils.rawValue(authToml, "auth.session_timeout_minutes");
        if (timeoutVal == null) timeoutVal = TomlReaderUtils.rawValue(authToml, "session_timeout_minutes");
        if (timeoutVal == null) timeoutVal = TomlReaderUtils.rawValue(toml, "auth.session_timeout_minutes");
        int authSessionTimeout = timeoutVal instanceof Number n ? n.intValue() : defaults.auth().sessionTimeoutMinutes();

        Object minPwdLenVal = TomlReaderUtils.rawValue(authToml, "auth.min_password_length");
        if (minPwdLenVal == null) minPwdLenVal = TomlReaderUtils.rawValue(authToml, "min_password_length");
        if (minPwdLenVal == null) minPwdLenVal = TomlReaderUtils.rawValue(toml, "auth.min_password_length");
        int authMinPasswordLength = minPwdLenVal instanceof Number n ? n.intValue() : defaults.auth().minPasswordLength();

        Object bedrockFormVal = TomlReaderUtils.rawValue(authToml, "auth.bedrock_form_enabled");
        if (bedrockFormVal == null) bedrockFormVal = TomlReaderUtils.rawValue(authToml, "bedrock_form_enabled");
        if (bedrockFormVal == null) bedrockFormVal = TomlReaderUtils.rawValue(toml, "auth.bedrock_form_enabled");
        boolean authBedrockFormEnabled = bedrockFormVal instanceof Boolean b
                ? b
                : defaults.auth().bedrockFormEnabled();

        Config.AuthConfig auth = new Config.AuthConfig(
                authEnabled,
                authAlg,
                auth2fa,
                authPinLen,
                authVoidHolding,
                authHoldingServer,
                authSessionTimeout,
                authMinPasswordLength,
                authBedrockFormEnabled
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
                language,
                storage,
                auth,
                new Config.MenuTokenSettings(60)
        );
    }


}
