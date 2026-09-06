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
import java.util.List;
import java.util.Map;

final class ConfigWriter {

    private ConfigWriter() {
    }

    static void writeConfig(Path configPath, Config config) throws IOException {
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
        writeHeader(b, wiki, config);
        writeStartup(b, wiki, config);
        writeCommands(b, wiki, config);
        writeRouting(b, wiki, config);
        writeContextual(b, wiki, config);
        writeLobbyFallback(b, wiki, config);
        writeHealthChecks(b, wiki, config);
        writeCircuitBreaker(b, wiki, config);
        b.append("# Player-facing text and menu labels are stored in messages.toml.\n\n");
        writeUpdateChecker(b, wiki, config);
        writeBedrock(b, wiki, config);
        writeMetrics(b, wiki, config);
        writeDashboard(b, wiki, config);
        writeStorage(b, wiki, config);
        writeDegradation(b, wiki, config);
        writeGeoRouting(b, wiki, config);
        writeAuth(b, wiki, config);
        writeAdvancedSections(b, advanced);
        writeDebug(b, wiki, config);
        writeFooter(b);

        Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(tempPath, b.toString());
        try {
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeHeader(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeStartup(StringBuilder b, String wiki, Config config) {
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  STARTUP — First-run welcome & upgrade digest                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[startup]\n\n");
        b.append("# Display a welcome banner on fresh installs and a changelog digest\n");
        b.append("# when upgrading from a previous version.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#startup-first-run-experience\n");
        b.append("welcome_enabled = ").append(config.startup().welcomeEnabled()).append("\n\n");
    }

    private static void writeCommands(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeRouting(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeContextual(StringBuilder b, String wiki, Config config) {
        var contextual = config.routing().contextual();
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  CONTEXTUAL ROUTING — Game-mode-aware lobby selection           │\n");
        b.append("# │                                                                 │\n");
        b.append("# │  Route players to specific lobbies depending on which server    │\n");
        b.append("# │  they are leaving. Great for game modes with dedicated lobbies.  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[routing.contextual]\n\n");
        b.append("# Enable source-server-aware routing.\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide\n");
        b.append("enabled = ").append(contextual.enabled()).append("\n\n");
        b.append("# If no servers in the matched group are online, fall back to default lobbies.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#routingcontextual\n");
        b.append("fallback_to_default = ").append(contextual.fallbackToDefault()).append("\n\n");

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
        for (Map.Entry<String, Config.GroupConfig> entry : contextual.groups().entrySet()) {
            if (entry.getValue().mode() != null) {
                b.append(quoted(entry.getKey())).append(" = { servers = ")
                        .append(formatLobbyEntryList(entry.getValue().servers()))
                        .append(", mode = ").append(quoted(entry.getValue().mode().configValue())).append(" }\n");
            } else {
                b.append(quoted(entry.getKey())).append(" = ")
                        .append(formatLobbyEntryList(entry.getValue().servers())).append("\n");
            }
        }
        b.append("\n");

        b.append("# ── Source Mappings ────────────────────────────────────────────────\n");
        b.append("# Map a source server name → contextual group name.\n");
        b.append("# When a player leaves \"bedwars-1\", they'll be routed to the \"bedwars\" group.\n");
        b.append("#\n");
        b.append("# Wiki: ").append(wiki).append("/Contextual-Routing-Guide#sources\n");
        b.append("[routing.contextual.sources]\n");
        for (Map.Entry<String, String> entry : contextual.sources().entrySet()) {
            b.append(quoted(entry.getKey())).append(" = ").append(quoted(entry.getValue())).append("\n");
        }
        b.append("\n");

        if (!contextual.fallbackChain().isEmpty()) {
            b.append("# ── Fallback Chain ────────────────────────────────────────────────\n");
            b.append("# Ordered list of fallback groups to try before using default lobbies.\n");
            b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#fallback-chain\n");
            b.append("[routing.contextual.fallback_chain]\n");
            for (Map.Entry<String, List<String>> entry : contextual.fallbackChain().entrySet()) {
                b.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append("\n");
            }
            b.append("\n");
        }
    }

    private static void writeLobbyFallback(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeHealthChecks(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeCircuitBreaker(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeUpdateChecker(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeBedrock(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeMetrics(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeDashboard(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeStorage(StringBuilder b, String wiki, Config config) {
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  STORAGE — Credentials and session persistence                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("# Authentication credentials, sessions, and other persisted data.\n");
        b.append("# \"file\" stores data in the SQLite file below; \"mysql\" uses the\n");
        b.append("# connection settings. A storage.toml file, when present, takes\n");
        b.append("# precedence over this section.\n");
        b.append("# Wiki: ").append(wiki).append("/Storage-and-Databases\n");
        b.append("[storage]\n\n");
        b.append("# Storage backend: \"file\" (SQLite) or \"mysql\".\n");
        b.append("type = ").append(quoted(config.storage().type())).append("\n\n");
        b.append("# MySQL host, port, database, and credentials. Ignored for \"file\".\n");
        b.append("host = ").append(quoted(config.storage().host())).append("\n");
        b.append("port = ").append(config.storage().port()).append("\n");
        b.append("database = ").append(quoted(config.storage().database())).append("\n");
        b.append("username = ").append(quoted(config.storage().username())).append("\n");
        b.append("password = ").append(quoted(config.storage().password())).append("\n\n");
        b.append("# SQLite database file, used when type is \"file\".\n");
        b.append("sqlite_file = ").append(quoted(config.storage().sqliteFile())).append("\n\n");
        b.append("# Connection pool size and timeout in milliseconds.\n");
        b.append("pool_size = ").append(config.storage().poolSize()).append("\n");
        b.append("connection_timeout_ms = ").append(config.storage().connectionTimeoutMs()).append("\n\n");
    }

    private static void writeDegradation(StringBuilder b, String wiki, Config config) {
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
    }

    private static void writeGeoRouting(StringBuilder b, String wiki, Config config) {
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  GEO ROUTING — Reserved compatibility settings                  │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[geo_routing]\n\n");
        b.append("# Retained so older configuration files continue to load.\n");
        b.append("# Geographic routing is opt-in; keep this disabled unless a geo.toml database is configured.\n");
        b.append("enabled = ").append(config.geoRouting().enabled()).append("\n\n");
        b.append("# Absolute path to the GeoLite2-Country.mmdb or GeoLite2-City.mmdb file.\n");
        b.append("database_path = ").append(quoted(config.geoRouting().databasePath())).append("\n\n");
        b.append("# Provider: \"auto\", \"georestrict\", \"maxmind\", or \"ip_api\".\n");
        b.append("provider = ").append(quoted(config.geoRouting().provider())).append("\n");
        b.append("fallback_enabled = ").append(config.geoRouting().fallbackEnabled()).append("\n");
        b.append("# Fallback provider or routing algorithm when no country affinity matches.\n");
        b.append("fallback_mode = ").append(quoted(config.geoRouting().fallbackMode())).append("\n\n");
        b.append("# Map backend server names to ISO 3166-1 alpha-2 country codes.\n");
        b.append("[geo_routing.affinity_countries]\n");
        for (Map.Entry<String, List<String>> entry : config.geoRouting().affinityCountries().entrySet()) {
            b.append(quoted(entry.getKey())).append(" = ").append(formatList(entry.getValue())).append("\n");
        }
        b.append("\n");
    }

    private static void writeAuth(StringBuilder b, String wiki, Config config) {
        b.append("# Authentication is disabled by default. Configure a dedicated holding backend before enabling it.\n");
        b.append("# Wiki: ").append(wiki).append("/Authentication-and-Security\n");
        b.append("[auth]\n");
        b.append("enabled = ").append(config.auth().enabled()).append("\n");
        b.append("algorithm = ").append(quoted(config.auth().algorithm())).append("\n");
        b.append("enable_2fa = ").append(config.auth().enable2fa()).append("\n");
        b.append("pin_length = ").append(config.auth().pinLength()).append("\n");
        b.append("void_world_holding = ").append(config.auth().voidWorldHolding()).append("\n");
        b.append("holding_server = ").append(quoted(config.auth().holdingServer())).append("\n");
        b.append("session_timeout_minutes = ").append(config.auth().sessionTimeoutMinutes()).append("\n");
        b.append("min_password_length = ").append(config.auth().minPasswordLength()).append("\n");
        b.append("bedrock_form_enabled = ").append(config.auth().bedrockFormEnabled()).append("\n\n");
    }

    private static void writeAdvancedSections(StringBuilder b, AdvancedConfig advanced) {
        writePartySection(b, advanced);
        writeQueueSection(b, advanced);
        writeRedisSection(b, advanced);
        writeBackendStatesSection(b, advanced);
        writeServerManagementSection(b, advanced);
    }

    private static void writePartySection(StringBuilder b, AdvancedConfig advanced) {
        b.append("# Native proxy party system and leader-follow behavior.\n");
        b.append("[party]\n");
        b.append("enabled = ").append(advanced.party().enabled()).append("\n");
        b.append("invite_timeout_seconds = ").append(advanced.party().inviteTimeoutSeconds()).append("\n");
        b.append("follow_leader = ").append(advanced.party().followLeader()).append("\n");
        b.append("join_delay_ms = ").append(advanced.party().joinDelayMs()).append("\n\n");
        b.append("max_size = ").append(advanced.party().maxSize()).append("\n");
        b.append("command = ").append(quoted(advanced.party().command())).append("\n");
        b.append("chat_command = ").append(quoted(advanced.party().chatCommand())).append("\n");
        b.append("permission = ").append(quoted(advanced.party().permission())).append("\n\n");
    }

    private static void writeQueueSection(StringBuilder b, AdvancedConfig advanced) {
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
    }

    private static void writeRedisSection(StringBuilder b, AdvancedConfig advanced) {
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
    }

    private static void writeBackendStatesSection(StringBuilder b, AdvancedConfig advanced) {
        b.append("# MOTD markers such as [STATE:IN_GAME] can remove backends from routing.\n");
        b.append("[backend_states]\n");
        b.append("enabled = ").append(advanced.backendStates().enabled()).append("\n");
        b.append("allowed = ").append(formatList(advanced.backendStates().allowed())).append("\n");
        b.append("allow_unknown = ").append(advanced.backendStates().allowUnknown()).append("\n\n");
    }

    private static void writeServerManagementSection(StringBuilder b, AdvancedConfig advanced) {
        b.append("# Optional /vn server persistence into Velocity and the lobby registry.\n");
        b.append("[server_management]\n");
        b.append("enabled = ").append(advanced.serverManagement().enabled()).append("\n");
        b.append("velocity_config = ").append(quoted(advanced.serverManagement().velocityConfig())).append("\n");
        b.append("allow_overwrite = ").append(advanced.serverManagement().allowOverwrite()).append("\n\n");
    }

    private static void writeDebug(StringBuilder b, String wiki, Config config) {
        b.append("# ┌─────────────────────────────────────────────────────────────────┐\n");
        b.append("# │  DEBUG — Diagnostic logging                                     │\n");
        b.append("# └─────────────────────────────────────────────────────────────────┘\n");
        b.append("[debug]\n\n");
        b.append("# Print detailed routing decisions, health check results, and cache\n");
        b.append("# events to the proxy console. Useful for troubleshooting.\n");
        b.append("# Wiki: ").append(wiki).append("/Configuration-Guide#debug-and-top-level-settings\n");
        b.append("verbose_logging = ").append(config.debug().verboseLogging()).append("\n\n");
    }

    private static void writeFooter(StringBuilder b) {
        b.append("# ╔══════════════════════════════════════════════════════════════════╗\n");
        b.append("# ║  Thank you for using VelocityNavigator!                        ║\n");
        b.append("# ║  Built with ❤ by DemonZ Development                            ║\n");
        b.append("# ║                                                                 ║\n");
        b.append("# ║  Questions? → https://discord.com/invite/GYsTt96ypf             ║\n");
        b.append("# ║  Found a bug? → https://github.com/DemonZ-Development/           ║\n");
        b.append("# ║                 VelocityNavigator/issues                        ║\n");
        b.append("# ╚══════════════════════════════════════════════════════════════════╝\n");
    }

    static String padRight(String text, int length) {
        if (text == null) text = "";
        if (text.length() >= length) return text;
        return text + " ".repeat(length - text.length());
    }

    static String formatLobbyEntryList(List<Config.LobbyEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }
        List<String> items = new ArrayList<>();
        for (Config.LobbyEntry entry : entries) {
            boolean needsTable = entry.maxPlayers() != Config.LobbyEntry.UNCAPPED
                    || entry.weight() != Config.LobbyEntry.DEFAULT_WEIGHT;
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

    static String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        List<String> escaped = new ArrayList<>();
        for (String value : values) {
            escaped.add(quoted(value));
        }
        return "[" + String.join(", ", escaped) + "]";
    }

    static String quoted(String value) {
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

}
