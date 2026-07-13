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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Config {

    public static final int CURRENT_VERSION = 8;
    public static final String OFFICIAL_WIKI_URL = "https://github.com/DemonZ-Development/VelocityNavigator/wiki";

    private final int configVersion;
    private final Commands commands;
    private final Routing routing;
    private final HealthChecks healthChecks;
    private final Messages messages;
    private final UpdateCheckerSettings updateChecker;
    private final MetricsSettings metrics;
    private final DebugSettings debug;
    private final CircuitBreakerSettings circuitBreaker;
    private final DegradationSettings degradation;
    private final GeoRoutingSettings geoRouting;
    private final boolean notifyOnStartup;
    private final boolean notifyAdminsOnJoin;
    private final StartupSettings startup;
    private final LobbyFallbackSettings lobbyFallback;
    private final BedrockSettings bedrock;
    private final DashboardSettings dashboard;
    private final LanguageBundle language;

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug,
            CircuitBreakerSettings circuitBreaker,
            DegradationSettings degradation,
            GeoRoutingSettings geoRouting,
            boolean notifyOnStartup,
            boolean notifyAdminsOnJoin
    ) {
        this(
                configVersion,
                commands,
                routing,
                healthChecks,
                messages,
                updateChecker,
                metrics,
                debug,
                circuitBreaker,
                degradation,
                geoRouting,
                notifyOnStartup,
                notifyAdminsOnJoin,
                new StartupSettings(true, OFFICIAL_WIKI_URL),
                new LobbyFallbackSettings("disconnect", "<red>No lobby servers are currently available. Please try again later.</red>", ""),
                new BedrockSettings(false, true, true, true, true, "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>", "<gray>Select a lobby server to connect:</gray>", "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>")
        );
    }

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug,
            CircuitBreakerSettings circuitBreaker,
            DegradationSettings degradation,
            GeoRoutingSettings geoRouting,
            boolean notifyOnStartup,
            boolean notifyAdminsOnJoin,
            StartupSettings startup,
            LobbyFallbackSettings lobbyFallback,
            BedrockSettings bedrock
    ) {
        this(
                configVersion,
                commands,
                routing,
                healthChecks,
                messages,
                updateChecker,
                metrics,
                debug,
                circuitBreaker,
                degradation,
                geoRouting,
                notifyOnStartup,
                notifyAdminsOnJoin,
                startup,
                lobbyFallback,
                bedrock,
                DashboardSettings.disabled()
        );
    }

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug,
            CircuitBreakerSettings circuitBreaker,
            DegradationSettings degradation,
            GeoRoutingSettings geoRouting,
            boolean notifyOnStartup,
            boolean notifyAdminsOnJoin,
            StartupSettings startup,
            LobbyFallbackSettings lobbyFallback,
            BedrockSettings bedrock,
            DashboardSettings dashboard
    ) {
        this(configVersion, commands, routing, healthChecks, messages, updateChecker, metrics, debug,
                circuitBreaker, degradation, geoRouting, notifyOnStartup, notifyAdminsOnJoin,
                startup, lobbyFallback, bedrock, dashboard, LanguageBundle.defaults());
    }

    public Config(
            int configVersion,
            Commands commands,
            Routing routing,
            HealthChecks healthChecks,
            Messages messages,
            UpdateCheckerSettings updateChecker,
            MetricsSettings metrics,
            DebugSettings debug,
            CircuitBreakerSettings circuitBreaker,
            DegradationSettings degradation,
            GeoRoutingSettings geoRouting,
            boolean notifyOnStartup,
            boolean notifyAdminsOnJoin,
            StartupSettings startup,
            LobbyFallbackSettings lobbyFallback,
            BedrockSettings bedrock,
            DashboardSettings dashboard,
            LanguageBundle language
    ) {
        this.configVersion = configVersion;
        this.commands = commands;
        this.routing = routing;
        this.healthChecks = healthChecks;
        this.messages = messages;
        this.updateChecker = updateChecker;
        this.metrics = metrics;
        this.debug = debug;
        this.circuitBreaker = circuitBreaker;
        this.degradation = degradation;
        this.geoRouting = geoRouting;
        this.notifyOnStartup = notifyOnStartup;
        this.notifyAdminsOnJoin = notifyAdminsOnJoin;
        this.startup = startup == null ? new StartupSettings(true, OFFICIAL_WIKI_URL) : startup;
        this.lobbyFallback = lobbyFallback == null ? new LobbyFallbackSettings("disconnect", "<red>No lobby servers are currently available. Please try again later.</red>", "") : lobbyFallback;
        this.bedrock = bedrock == null ? new BedrockSettings(false, true, true, true, true, "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>", "<gray>Select a lobby server to connect:</gray>", "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>") : bedrock;
        this.dashboard = dashboard == null ? DashboardSettings.disabled() : dashboard;
        this.language = language == null ? LanguageBundle.defaults() : language;
    }

    public static Config defaults() {
        return new Config(
                CURRENT_VERSION,
                new Commands(
                        "lobby",
                        List.of("hub", "spawn"),
                        "none",
                        List.of("velocitynavigator", "vn"),
                        3,
                        false
                ),
                new Routing(
                        SelectionMode.LEAST_PLAYERS,
                        true,
                        true,
                        List.of(new LobbyEntry("lobby-1", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT),
                                new LobbyEntry("lobby-2", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT)),
                        new Contextual(
                                false,
                                true,
                                Map.of("bedwars", new GroupConfig(
                                        List.of(new LobbyEntry("bedwars-lobby-1", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT),
                                                new LobbyEntry("bedwars-lobby-2", LobbyEntry.UNCAPPED, LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                )),
                                Map.of("bedwars-1", "bedwars"),
                                Map.of()
                        ),
                        2,
                        new AffinitySettings(true, 0.7),
                        false,
                        "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient> <gray>(Hover to view status, click to connect)</gray>",
                        "  <gray>•</gray> <white><bold>{server}</bold></white> <gray>| Click to connect</gray>",
                        "<white><bold>{server}</bold></white>\n<gray>Status:</gray> {status_color}{status}\n<gray>Players:</gray> <white>{players}/{max_players}</white>\n<gray>Ping:</gray> <white>{ping}ms</white>",
                        JavaMenuType.INVENTORY,
                        new InventoryMenuSettings(3, "COMPASS", true)
                ),
                new HealthChecks(true, 2500, 60),
                new Messages(
                        "<aqua>Sending you to <server>...</aqua>",
                        "<yellow>You are already connected to <server>.</yellow>",
                        "<red>No available lobby could be found. (<reason>)</red>",
                        "<gray>This command can only be used by a player.</gray>",
                        "<yellow>Please wait <time> more second(s).</yellow>",
                        "<green>VelocityNavigator reloaded.</green>",
                        "<red>Reload failed. Check console for details.</red>",
                        "<yellow>Retrying connection... (<attempt>/<max>)</yellow>",
                        "auto",
                        "<green>",
                        "<yellow>",
                        "<red>",
                        "<gray>"
                ),
                new UpdateCheckerSettings(true, UpdateChannel.RELEASE, 60, true),
                new MetricsSettings(true, new PrometheusSettings(false, 9225, "127.0.0.1")),
                new DebugSettings(false),
                new CircuitBreakerSettings(true, 3, 30, 1),
                new DegradationSettings(true, "random"),
                new GeoRoutingSettings(false, ""),
                true,
                true,
                new StartupSettings(true, OFFICIAL_WIKI_URL),
                new LobbyFallbackSettings("disconnect", "<red>No lobby servers are currently available. Please try again later.</red>", ""),
                new BedrockSettings(false, true, true, true, true, "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>", "<gray>Select a lobby server to connect:</gray>", "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>")
        );
    }

    public int configVersion() {
        return configVersion;
    }

    public Commands commands() {
        return commands;
    }

    public Routing routing() {
        return routing;
    }

    public HealthChecks healthChecks() {
        return healthChecks;
    }

    public Messages messages() {
        return messages;
    }

    public UpdateCheckerSettings updateChecker() {
        return updateChecker;
    }

    public MetricsSettings metrics() {
        return metrics;
    }

    public DebugSettings debug() {
        return debug;
    }

    public CircuitBreakerSettings circuitBreaker() {
        return circuitBreaker;
    }

    public DegradationSettings degradation() {
        return degradation;
    }

    public GeoRoutingSettings geoRouting() {
        return geoRouting;
    }

    public boolean notifyOnStartup() {
        return notifyOnStartup;
    }

    public boolean notifyAdminsOnJoin() {
        return notifyAdminsOnJoin;
    }

    public StartupSettings startup() {
        return startup;
    }

    public LobbyFallbackSettings lobbyFallback() {
        return lobbyFallback;
    }

    public BedrockSettings bedrock() {
        return bedrock;
    }

    public DashboardSettings dashboard() {
        return dashboard;
    }

    public LanguageBundle language() {
        return language;
    }

    public enum SelectionMode {
        LEAST_PLAYERS,
        RANDOM,
        ROUND_ROBIN,
        POWER_OF_TWO,
        WEIGHTED_ROUND_ROBIN,
        LEAST_CONNECTIONS,
        CONSISTENT_HASH,
        LATENCY;

        public static SelectionMode fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return LEAST_PLAYERS;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "least_players" -> LEAST_PLAYERS;
                case "random" -> RANDOM;
                case "round_robin" -> ROUND_ROBIN;
                case "power_of_two" -> POWER_OF_TWO;
                case "weighted_round_robin" -> WEIGHTED_ROUND_ROBIN;
                case "least_connections" -> LEAST_CONNECTIONS;
                case "consistent_hash" -> CONSISTENT_HASH;
                case "latency" -> LATENCY;
                default -> LEAST_PLAYERS;
            };
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static List<String> allConfigValues() {
            return Arrays.stream(values()).map(SelectionMode::configValue).toList();
        }
    }

    public enum UpdateChannel {
        RELEASE,
        BETA,
        ALPHA;

        public static UpdateChannel fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEASE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "beta" -> BETA;
                case "alpha" -> ALPHA;
                default -> RELEASE;
            };
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static List<String> allConfigValues() {
            return Arrays.stream(values()).map(UpdateChannel::configValue).toList();
        }
    }

    public enum RemoteVersionType {
        RELEASE,
        BETA,
        ALPHA;

        public static RemoteVersionType fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEASE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "beta" -> BETA;
                case "alpha" -> ALPHA;
                default -> RELEASE;
            };
        }
    }

    public record LobbyEntry(String server, int maxPlayers, int weight) {
        public static final int UNCAPPED = -1;
        public static final int DEFAULT_WEIGHT = 1;

        public LobbyEntry {
            server = server == null ? "" : server.trim().toLowerCase(Locale.ROOT);
            if (server.isBlank()) {
                server = "";
            }
            if (maxPlayers < 0) {
                maxPlayers = UNCAPPED;
            }
            if (weight <= 0) {
                weight = DEFAULT_WEIGHT;
            }
        }

        public boolean isFull(int currentPlayers) {
            if (maxPlayers == UNCAPPED) {
                return false;
            }
            return currentPlayers >= maxPlayers;
        }

        public int effectiveWeight() {
            return weight;
        }
    }

    public record GroupConfig(List<LobbyEntry> servers, SelectionMode mode) {
        public GroupConfig {
            servers = servers == null ? List.of() : List.copyOf(servers);
        }
    }

    public record Commands(
            String primary,
            List<String> aliases,
            String permission,
            List<String> adminAliases,
            int cooldownSeconds,
            boolean reconnectIfSameServer
    ) {
        public Commands {
            primary = sanitizeCommand(primary, "lobby");
            aliases = immutableNames(aliases, primary);
            permission = sanitizeText(permission, "none");
            adminAliases = immutableNames(adminAliases, null);
            if (adminAliases.isEmpty()) {
                adminAliases = List.of("velocitynavigator", "vn");
            }
            cooldownSeconds = Math.max(0, cooldownSeconds);
        }
    }

    public record Routing(
            SelectionMode selectionMode,
            boolean cycleWhenPossible,
            boolean balanceInitialJoin,
            List<LobbyEntry> defaultLobbies,
            Contextual contextual,
            int maxRetries,
            AffinitySettings affinity,
            boolean useChatMenuForLobby,
            String chatMenuHeader,
            String chatMenuFormat,
            String chatMenuTooltip,
            JavaMenuType javaMenuType,
            InventoryMenuSettings inventoryMenu
    ) {
        public Routing(
                SelectionMode selectionMode,
                boolean cycleWhenPossible,
                boolean balanceInitialJoin,
                List<LobbyEntry> defaultLobbies,
                Contextual contextual,
                int maxRetries,
                AffinitySettings affinity,
                boolean useChatMenuForLobby,
                String chatMenuHeader,
                String chatMenuFormat,
                String chatMenuTooltip
        ) {
            this(selectionMode, cycleWhenPossible, balanceInitialJoin, defaultLobbies, contextual,
                    maxRetries, affinity, useChatMenuForLobby, chatMenuHeader, chatMenuFormat,
                    chatMenuTooltip, JavaMenuType.CHAT, new InventoryMenuSettings(3, "COMPASS", true));
        }

        public Routing(
                SelectionMode selectionMode,
                boolean cycleWhenPossible,
                boolean balanceInitialJoin,
                List<LobbyEntry> defaultLobbies,
                Contextual contextual,
                int maxRetries,
                AffinitySettings affinity
        ) {
            this(
                    selectionMode,
                    cycleWhenPossible,
                    balanceInitialJoin,
                    defaultLobbies,
                    contextual,
                    maxRetries,
                    affinity,
                    false,
                    null,
                    null,
                    null
            );
        }

        public Routing {
            selectionMode = selectionMode == null ? SelectionMode.LEAST_PLAYERS : selectionMode;
            defaultLobbies = immutableLobbyEntries(defaultLobbies);
            contextual = contextual == null ? new Contextual(false, true, Map.of(), Map.of(), Map.of()) : contextual;
            maxRetries = Math.max(0, maxRetries);
            affinity = affinity == null ? new AffinitySettings(true, 0.7) : affinity;
            javaMenuType = javaMenuType == null ? JavaMenuType.CHAT : javaMenuType;
            inventoryMenu = inventoryMenu == null ? new InventoryMenuSettings(3, "COMPASS", true) : inventoryMenu;
            chatMenuHeader = chatMenuHeader == null || chatMenuHeader.isBlank() ? "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient> <gray>(Hover to view status, click to connect)</gray>" : chatMenuHeader;
            chatMenuFormat = chatMenuFormat == null || chatMenuFormat.isBlank() ? "  <gray>•</gray> <white><bold>{server}</bold></white> <gray>| Click to connect</gray>" : chatMenuFormat;
            chatMenuTooltip = chatMenuTooltip == null || chatMenuTooltip.isBlank() ? "<white><bold>{server}</bold></white>\n<gray>Status:</gray> {status_color}{status}\n<gray>Players:</gray> <white>{players}/{max_players}</white>\n<gray>Ping:</gray> <white>{ping}ms</white>" : chatMenuTooltip;
        }

        public boolean useMenuForLobby() {
            return useChatMenuForLobby;
        }
    }

    public enum JavaMenuType {
        CHAT,
        INVENTORY;

        public static JavaMenuType fromString(String value) {
            return "inventory".equalsIgnoreCase(value) ? INVENTORY : CHAT;
        }

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record InventoryMenuSettings(int rows, String material, boolean fallbackToChat) {
        public InventoryMenuSettings {
            rows = Math.max(1, Math.min(6, rows));
            material = sanitizeText(material, "COMPASS").toUpperCase(Locale.ROOT);
        }
    }

    public record Contextual(
            boolean enabled,
            boolean fallbackToDefault,
            Map<String, GroupConfig> groups,
            Map<String, String> sources,
            Map<String, List<String>> fallbackChain
    ) {
        public Contextual {
            groups = immutableGroupConfigMap(groups);
            sources = immutableStringMap(sources);
            fallbackChain = immutableStringListMap(fallbackChain);
        }
    }

    public record HealthChecks(boolean enabled, int timeoutMs, int cacheSeconds) {
        public HealthChecks {
            timeoutMs = Math.max(250, timeoutMs);
            cacheSeconds = Math.max(0, cacheSeconds);
        }
    }

    public record Messages(
            String connecting,
            String alreadyConnected,
            String noLobbyFound,
            String playerOnly,
            String cooldown,
            String reloadSuccess,
            String reloadFailed,
            String retrying,
            String formatting,
            String dashboardHealthy,
            String dashboardDraining,
            String dashboardOpen,
            String dashboardOffline
    ) {
        public Messages {
            connecting = sanitizeText(connecting, "<aqua>Sending you to <server>...</aqua>");
            alreadyConnected = sanitizeText(alreadyConnected, "<yellow>You are already connected to <server>.</yellow>");
            noLobbyFound = sanitizeText(noLobbyFound, "<red>No available lobby could be found. (<reason>)</red>");
            playerOnly = sanitizeText(playerOnly, "<gray>This command can only be used by a player.</gray>");
            cooldown = sanitizeText(cooldown, "<yellow>Please wait <time> more second(s).</yellow>");
            reloadSuccess = sanitizeText(reloadSuccess, "<green>VelocityNavigator reloaded.</green>");
            reloadFailed = sanitizeText(reloadFailed, "<red>Reload failed. Check console for details.</red>");
            retrying = sanitizeText(retrying, "<yellow>Retrying connection... (<attempt>/<max>)</yellow>");
            formatting = sanitizeText(formatting, "auto");
            dashboardHealthy = sanitizeText(dashboardHealthy, "<green>");
            dashboardDraining = sanitizeText(dashboardDraining, "<yellow>");
            dashboardOpen = sanitizeText(dashboardOpen, "<red>");
            dashboardOffline = sanitizeText(dashboardOffline, "<gray>");
        }

        public Messages(
                String connecting,
                String alreadyConnected,
                String noLobbyFound,
                String playerOnly,
                String cooldown,
                String reloadSuccess,
                String reloadFailed,
                String retrying,
                String formatting
        ) {
            this(connecting, alreadyConnected, noLobbyFound, playerOnly, cooldown, reloadSuccess, reloadFailed, retrying, formatting, "<green>", "<yellow>", "<red>", "<gray>");
        }

        public Messages(
                String connecting,
                String alreadyConnected,
                String noLobbyFound,
                String playerOnly,
                String cooldown,
                String reloadSuccess,
                String reloadFailed,
                String retrying
        ) {
            this(connecting, alreadyConnected, noLobbyFound, playerOnly, cooldown, reloadSuccess, reloadFailed, retrying, "auto", "<green>", "<yellow>", "<red>", "<gray>");
        }
    }

    public record UpdateCheckerSettings(
            boolean enabled,
            UpdateChannel channel,
            int checkIntervalMinutes,
            boolean notifyAdmins,
            boolean silent
    ) {
        public UpdateCheckerSettings {
            channel = channel == null ? UpdateChannel.RELEASE : channel;
            checkIntervalMinutes = Math.max(30, checkIntervalMinutes);
        }

        public UpdateCheckerSettings(boolean enabled, UpdateChannel channel, int checkIntervalMinutes, boolean notifyAdmins) {
            this(enabled, channel, checkIntervalMinutes, notifyAdmins, true);
        }
    }

    public record MetricsSettings(boolean enabled, PrometheusSettings prometheus) {
        public MetricsSettings {
            prometheus = prometheus == null ? new PrometheusSettings(false, 9225, "127.0.0.1", "") : prometheus;
        }
    }

    public record PrometheusSettings(boolean enabled, int port, String bindHost, String bearerToken) {
        public PrometheusSettings(boolean enabled, int port, String bindHost) {
            this(enabled, port, bindHost, "");
        }

        public PrometheusSettings {
            port = port <= 0 || port > 65535 ? 9225 : port;
            bindHost = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost.trim();
            bearerToken = bearerToken == null ? "" : bearerToken.trim();
        }
    }

    public record DebugSettings(boolean verboseLogging) {
    }

    public record CircuitBreakerSettings(boolean enabled, int failureThreshold, int cooldownSeconds, int halfOpenMaxTests) {
        public CircuitBreakerSettings {
            failureThreshold = Math.max(1, failureThreshold);
            cooldownSeconds = Math.max(1, cooldownSeconds);
            halfOpenMaxTests = Math.max(1, halfOpenMaxTests);
        }
    }

    public record DegradationSettings(boolean enabled, String mode) {
        public DegradationSettings {
            mode = sanitizeText(mode, "random");
        }
    }

    public record GeoRoutingSettings(boolean enabled, String databasePath) {
        public GeoRoutingSettings {
            databasePath = databasePath == null ? "" : databasePath;
        }
    }

    public record AffinitySettings(boolean enabled, double stickiness) {
        public AffinitySettings {
            stickiness = Math.max(0.0, Math.min(1.0, stickiness));
        }
    }

    public record StartupSettings(boolean welcomeEnabled, String wikiUrl) {
        public StartupSettings {
            wikiUrl = OFFICIAL_WIKI_URL;
        }
    }

    public record LobbyFallbackSettings(String noServerStrategy, String noServerMessage, String fallbackServer) {
        public LobbyFallbackSettings {
            noServerStrategy = sanitizeText(noServerStrategy, "disconnect");
            noServerMessage = sanitizeText(noServerMessage, "<red>No lobby servers are currently available. Please try again later.</red>");
            fallbackServer = fallbackServer == null ? "" : fallbackServer.trim();
        }
    }

    public record BedrockSettings(
            boolean enabled,
            boolean autoDetect,
            boolean stripAdvancedFormatting,
            boolean affinityUseJavaUuid,
            boolean useGuiForLobby,
            String guiTitle,
            String guiContent,
            String guiButtonFormat
    ) {
        public BedrockSettings(
                boolean enabled,
                boolean autoDetect,
                boolean stripAdvancedFormatting,
                boolean affinityUseJavaUuid
        ) {
            this(
                    enabled,
                    autoDetect,
                    stripAdvancedFormatting,
                    affinityUseJavaUuid,
                    false,
                    null,
                    null,
                    null
            );
        }

        public BedrockSettings {
            guiTitle = guiTitle == null || guiTitle.isBlank() ? "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>" : guiTitle;
            guiContent = guiContent == null || guiContent.isBlank() ? "<gray>Select a lobby server to connect:</gray>" : guiContent;
            guiButtonFormat = guiButtonFormat == null || guiButtonFormat.isBlank() ? "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>" : guiButtonFormat;
        }
    }

    public record DashboardSettings(
            boolean enabled,
            int port,
            String bindHost,
            String bearerToken,
            int refreshSeconds
    ) {
        public DashboardSettings(boolean enabled, int port, String bindHost, String bearerToken) {
            this(enabled, port, bindHost, bearerToken, 5);
        }

        public DashboardSettings {
            port = port <= 0 || port > 65535 ? 9226 : port;
            bindHost = bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost.trim();
            bearerToken = bearerToken == null ? "" : bearerToken;
            refreshSeconds = Math.max(2, refreshSeconds);
        }

        public static DashboardSettings disabled() {
            return new DashboardSettings(false, 9226, "127.0.0.1", "", 5);
        }
    }

    private static String sanitizeCommand(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replace("/", "");
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private static String sanitizeText(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private static List<String> immutableNames(List<String> names, String excludedName) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalized = sanitizeCommand(name, "");
            if (normalized.isBlank()) {
                continue;
            }
            if (excludedName != null && normalized.equalsIgnoreCase(excludedName)) {
                continue;
            }
            if (!cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
        }
        return Collections.unmodifiableList(cleaned);
    }

    private static List<LobbyEntry> immutableLobbyEntries(List<LobbyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<LobbyEntry> cleaned = new ArrayList<>();
        for (LobbyEntry entry : entries) {
            if (entry != null && !entry.server().isBlank()) {
                cleaned.add(entry);
            }
        }
        return Collections.unmodifiableList(cleaned);
    }

    private static Map<String, GroupConfig> immutableGroupConfigMap(Map<String, GroupConfig> groups) {
        if (groups == null || groups.isEmpty()) {
            return Map.of();
        }
        Map<String, GroupConfig> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, GroupConfig> entry : groups.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase(Locale.ROOT);
            GroupConfig value = entry.getValue();
            if (value != null && !value.servers().isEmpty()) {
                cleaned.put(key, value);
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    private static Map<String, List<String>> immutableStringListMap(Map<String, List<String>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            List<String> val = immutableNames(entry.getValue(), null);
            if (!val.isEmpty()) {
                cleaned.put(entry.getKey().trim().toLowerCase(Locale.ROOT), val);
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    private static Map<String, String> immutableStringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            cleaned.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue().trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableMap(cleaned);
    }
}
