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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerRoundTripTest {

    @TempDir
    Path tempDir;

    @Test
    void configRoundTripPreservesAllFields() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));

        ConfigLoadResult firstLoad = manager.load();
        assertTrue(firstLoad.createdDefault());

        ConfigLoadResult secondLoad = manager.load();
        Config config = secondLoad.config();

        Config defaults = Config.defaults();
        assertEquals(defaults.configVersion(), config.configVersion());
        assertEquals(defaults.commands().primary(), config.commands().primary());
        assertEquals(defaults.commands().aliases(), config.commands().aliases());
        assertEquals(defaults.commands().cooldownSeconds(), config.commands().cooldownSeconds());
        assertEquals(defaults.routing().selectionMode(), config.routing().selectionMode());
        assertEquals(defaults.routing().cycleWhenPossible(), config.routing().cycleWhenPossible());
        assertEquals(defaults.routing().maxRetries(), config.routing().maxRetries());
        assertEquals(defaults.routing().affinity().enabled(), config.routing().affinity().enabled());
        assertEquals(defaults.routing().affinity().stickiness(), config.routing().affinity().stickiness());
        assertEquals(defaults.routing().javaMenuType(), config.routing().javaMenuType());
        assertEquals(defaults.routing().inventoryMenu(), config.routing().inventoryMenu());
        assertEquals(defaults.healthChecks().enabled(), config.healthChecks().enabled());
        assertEquals(defaults.healthChecks().timeoutMs(), config.healthChecks().timeoutMs());
        assertEquals(defaults.healthChecks().cacheSeconds(), config.healthChecks().cacheSeconds());
        assertEquals(defaults.updateChecker().channel(), config.updateChecker().channel());
        assertEquals(defaults.updateChecker().enabled(), config.updateChecker().enabled());
        assertEquals(defaults.updateChecker().checkIntervalMinutes(), config.updateChecker().checkIntervalMinutes());
        assertEquals(defaults.updateChecker().notifyAdmins(), config.updateChecker().notifyAdmins());
        assertEquals(defaults.circuitBreaker().enabled(), config.circuitBreaker().enabled());
        assertEquals(defaults.circuitBreaker().failureThreshold(), config.circuitBreaker().failureThreshold());
        assertEquals(defaults.circuitBreaker().cooldownSeconds(), config.circuitBreaker().cooldownSeconds());
        assertEquals(defaults.circuitBreaker().halfOpenMaxTests(), config.circuitBreaker().halfOpenMaxTests());
        assertEquals(defaults.degradation().enabled(), config.degradation().enabled());
        assertEquals(defaults.degradation().mode(), config.degradation().mode());
        assertEquals(defaults.notifyOnStartup(), config.notifyOnStartup());
        assertEquals(defaults.notifyAdminsOnJoin(), config.notifyAdminsOnJoin());
        assertEquals(defaults.startup().welcomeEnabled(), config.startup().welcomeEnabled());
        assertEquals(defaults.startup().wikiUrl(), config.startup().wikiUrl());
        assertEquals(defaults.lobbyFallback().noServerStrategy(), config.lobbyFallback().noServerStrategy());
        assertEquals(defaults.lobbyFallback().noServerMessage(), config.lobbyFallback().noServerMessage());
        assertEquals(defaults.lobbyFallback().fallbackServer(), config.lobbyFallback().fallbackServer());
        assertEquals(defaults.bedrock().enabled(), config.bedrock().enabled());
        assertEquals(defaults.bedrock().autoDetect(), config.bedrock().autoDetect());
        assertEquals(defaults.bedrock().stripAdvancedFormatting(), config.bedrock().stripAdvancedFormatting());
        assertEquals(defaults.bedrock().affinityUseJavaUuid(), config.bedrock().affinityUseJavaUuid());

        String written = Files.readString(tempDir.resolve("navigator.toml"));
        String messages = Files.readString(tempDir.resolve("messages.toml"));
        String gui = Files.readString(tempDir.resolve("gui.toml"));
        assertTrue(written.contains("notify_on_startup = true"));
        assertTrue(written.contains("notify_admins_on_join = true"));
        assertTrue(written.contains("[routing.java_menu]"));
        assertFalse(written.contains("[messages]"));
        assertTrue(messages.contains("[menus.chat]"));
        assertTrue(messages.contains("tooltip = \"<white><bold>{server}</bold></white>\\n"));
        assertTrue(gui.contains("[layout]"));
        assertTrue(gui.contains("refresh_seconds = 5"));
    }

    @Test
    void contextualGroupKeyWithSpaceIsQuotedCorrectly() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = ["lobby-1"]

                [routing.contextual]
                enabled = true
                fallback_to_default = true

                [routing.contextual.groups]
                "bed wars" = ["bw-lobby-1"]

                [routing.contextual.sources]
                "bw-1" = "bed wars"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        assertTrue(config.routing().contextual().groups().containsKey("bed wars"),
                "Group key with space should survive round-trip");
        assertEquals("bed wars", config.routing().contextual().sources().get("bw-1"));

        ConfigLoadResult reRead = manager.load();
        assertTrue(reRead.config().routing().contextual().groups().containsKey("bed wars"),
                "Group key with space should survive write + read round-trip");
    }

    @Test
    void contextualGroupLookupIsCaseInsensitiveAfterLoad() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 6

                [routing]
                default_lobbies = ["lobby-1"]

                [routing.contextual]
                enabled = true
                fallback_to_default = true

                [routing.contextual.groups]
                BedWars = ["bw-lobby-1"]

                [routing.contextual.sources]
                "bw-1" = "BedWars"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        Config config = manager.load().config();
        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());

        RouteDecision decision = planner.plan("bw-1", config, java.util.Map.of("bw-lobby-1", 0));

        assertTrue(decision.hasSelection());
        assertEquals("bw-lobby-1", decision.selectedServer());
        assertEquals("bedwars", decision.usedGroup());
    }

    @Test
    void migrationFromV3PreservesSettings() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 3

                [commands]
                primary = "lobby"
                aliases = ["hub", "spawn"]
                permission = "my.custom.perm"
                admin_aliases = ["vn"]
                cooldown_seconds = 5
                reconnect_if_same_server = true

                [routing]
                selection_mode = "round_robin"
                cycle_when_possible = true
                balance_initial_join = true
                default_lobbies = ["lobby-a", "lobby-b"]

                [routing.contextual]
                enabled = true
                fallback_to_default = true

                [routing.contextual.groups]
                bedwars = ["bw-1", "bw-2"]

                [routing.contextual.sources]
                "bw-spawn" = "bedwars"

                [health_checks]
                enabled = true
                timeout_ms = 3000
                cache_seconds = 45

                [messages]
                connecting = "<gold>Sending...</gold>"
                already_connected = "<red>Already there!</red>"
                no_lobby_found = "<red>Full!</red>"
                player_only = "<gray>Players only.</gray>"
                cooldown = "<yellow>Wait!</yellow>"
                reload_success = "<green>Done!</green>"
                reload_failed = "<red>Oops!</red>"

                [update_checker]
                channel = "beta"

                [metrics]
                enabled = false
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();

        assertTrue(result.migrated(), "v3 config should be detected as migrated");
        Config config = result.config();

        assertEquals("my.custom.perm", config.commands().permission());
        assertEquals(5, config.commands().cooldownSeconds());
        assertEquals(Config.SelectionMode.ROUND_ROBIN, config.routing().selectionMode());
        assertEquals("lobby-a", config.routing().defaultLobbies().get(0).server());
        assertEquals("lobby-b", config.routing().defaultLobbies().get(1).server());
        assertTrue(config.routing().contextual().enabled());
        assertEquals("bedwars", config.routing().contextual().sources().get("bw-spawn"));
        assertEquals(3000, config.healthChecks().timeoutMs());
        assertEquals(45, config.healthChecks().cacheSeconds());
        assertEquals("<gold>Sending...</gold>", config.messages().connecting());
        assertEquals(Config.UpdateChannel.BETA, config.updateChecker().channel());
        assertFalse(config.metrics().enabled());
    }

    @Test
    void lobbyEntryReadsPlainStringFormat() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = ["lobby-plain-a", "lobby-plain-b"]

                [routing.contextual]
                enabled = false
                fallback_to_default = true

                [health_checks]
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        var lobbies = config.routing().defaultLobbies();
        assertEquals(2, lobbies.size());

        assertEquals("lobby-plain-a", lobbies.get(0).server());
        assertEquals(Config.LobbyEntry.UNCAPPED, lobbies.get(0).maxPlayers());
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, lobbies.get(0).weight());

        assertEquals("lobby-plain-b", lobbies.get(1).server());
        assertEquals(Config.LobbyEntry.UNCAPPED, lobbies.get(1).maxPlayers());
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, lobbies.get(1).weight());
    }

    @Test
    void lobbyEntryReadsInlineTableFormat() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = [{ server = "lobby-table", max_players = 50, weight = 3 }]

                [routing.contextual]
                enabled = false
                fallback_to_default = true

                [health_checks]
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        var lobbies = config.routing().defaultLobbies();
        assertEquals(1, lobbies.size());

        assertEquals("lobby-table", lobbies.get(0).server());
        assertEquals(50, lobbies.get(0).maxPlayers());
        assertEquals(3, lobbies.get(0).weight());
    }

    @Test
    void playerAffinityReadsSettingsCorrectly() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 4

                [commands]
                aliases = ["hub"]

                [routing]
                default_lobbies = ["lobby-1"]

                [routing.affinity]
                enabled = true
                stickiness = 0.85
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("roundtrip-test"));
        ConfigLoadResult result = manager.load();
        Config config = result.config();

        assertTrue(config.routing().affinity().enabled());
        assertEquals(0.85, config.routing().affinity().stickiness(), 0.001);
    }
}
