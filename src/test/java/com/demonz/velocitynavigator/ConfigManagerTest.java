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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigWhenMissing() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));

        ConfigLoadResult result = manager.load();

        assertTrue(result.createdDefault());
        assertEquals(Config.CURRENT_VERSION, result.config().configVersion());
        assertTrue(Files.exists(tempDir.resolve("navigator.toml")));
        assertTrue(Files.exists(tempDir.resolve("messages.toml")));
        assertTrue(Files.exists(tempDir.resolve("gui.toml")));
        String navigator = Files.readString(tempDir.resolve("navigator.toml"));
        assertTrue(navigator.contains("Authorization: Bearer <token>"));
        assertFalse(navigator.contains("?token"));
        assertFalse(navigator.contains("wiki_url"));
        assertTrue(navigator.contains(Config.OFFICIAL_WIKI_URL));
        String gui = Files.readString(tempDir.resolve("gui.toml"));
        assertTrue(gui.contains("config_version = 2"));
        assertTrue(gui.contains("display_name = \"Main Lobby 1\""));
        assertTrue(gui.contains("[states.full]"));
    }

    @Test
    void removesLegacyCustomWikiUrl() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 8

                [startup]
                welcome_enabled = true
                wiki_url = "https://example.invalid/custom-wiki"
                """);

        ConfigLoadResult result = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test")).load();
        String rewritten = Files.readString(configPath);

        assertTrue(result.normalized());
        assertEquals(Config.OFFICIAL_WIKI_URL, result.config().startup().wikiUrl());
        assertFalse(rewritten.contains("wiki_url"));
        assertFalse(rewritten.contains("example.invalid"));
    }

    @Test
    void migratesLegacyMessagesIntoSeparateFile() throws Exception {
        Files.writeString(tempDir.resolve("navigator.toml"), """
                config_version = 7

                [routing]
                default_lobbies = ["lobby-1"]
                chat_menu_header = "<gold>Choose a lobby</gold>"

                [messages]
                connecting = "<green>Viaje a <server></green>"
                formatting = "minimessage"
                """);

        Config config = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test")).load().config();

        assertEquals("<green>Viaje a <server></green>", config.messages().connecting());
        assertEquals("<gold>Choose a lobby</gold>", config.routing().chatMenuHeader());
        assertTrue(Files.readString(tempDir.resolve("messages.toml")).contains("Viaje a <server>"));
        assertFalse(Files.readString(tempDir.resolve("navigator.toml")).contains("[messages]"));
        assertTrue(Files.exists(tempDir.resolve("navigator.toml.pre-messages.bak")));
    }

    @Test
    void reloadsCustomizedMessagesFile() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Path messages = tempDir.resolve("messages.toml");
        String text = Files.readString(messages).replace(
                "Sending you to <server>...",
                "Enviándote a <server>..."
        );
        Files.writeString(messages, text);

        assertEquals("<aqua>Enviándote a <server>...</aqua>", manager.load().config().messages().connecting());
    }

    @Test
    void switchesBuiltInLanguageAndSupportsCustomCodes() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Path messages = tempDir.resolve("messages.toml");
        Files.writeString(messages, Files.readString(messages).replaceFirst("(?m)^language = \"en\"$", "language = \"ru\""));

        Config russian = manager.load().config();
        assertTrue(russian.messages().connecting().contains("Подключаем"));
        assertTrue(Files.readString(messages).contains("active_language = \"ru\""));

        String custom = Files.readString(messages)
                .replaceFirst("(?m)^language = \"ru\"$", "language = \"pirate\"")
                .replace("Подключаем вас к", "Плывём к");
        Files.writeString(messages, custom);
        Config pirate = manager.load().config();
        assertEquals("pirate", pirate.language().language());
        assertTrue(pirate.messages().connecting().contains("Плывём"));
    }

    @Test
    void loadsSeparateGuiConfigurationAndServerOverrides() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Files.writeString(tempDir.resolve("gui.toml"), """
                config_version = 1
                [layout]
                rows = 5
                default_material = "ENDER_PEARL"
                unavailable_material = "BARRIER"
                fill_empty_slots = false
                filler_material = "GRAY_STAINED_GLASS_PANE"
                refresh_seconds = 9
                [controls]
                previous_slot = 36
                refresh_slot = 40
                next_slot = 44
                previous_material = "ARROW"
                refresh_material = "CLOCK"
                next_material = "ARROW"
                [servers]
                "lobby-1" = { display_name = "  Main Lobby 一  ", description = "  Classic lobby  ", menu_order = 7, show_in_menu = false, slot = 10, material = "NETHER_STAR", name = "&#55FFFFLobby One", lore = ["&7Custom"] }
                """);

        String navigatorBeforeGuiMigration = Files.readString(tempDir.resolve("navigator.toml"));
        manager.load();
        GuiConfig gui = manager.guiConfig();
        assertEquals(5, gui.rows());
        assertEquals("ENDER_PEARL", gui.defaultMaterial());
        assertEquals(10, gui.server("LOBBY-1").slot());
        assertEquals("NETHER_STAR", gui.server("lobby-1").material());
        assertEquals("Main Lobby 一", gui.server("lobby-1").displayName());
        assertEquals("Main Lobby 一", gui.displayName("LOBBY-1"));
        assertEquals("Classic lobby", gui.description("LOBBY-1"));
        assertEquals(7, gui.server("lobby-1").menuOrder());
        assertFalse(gui.server("lobby-1").showInMenu());
        assertEquals("&#55FFFFLobby One", gui.server("lobby-1").name());
        assertTrue(Files.exists(tempDir.resolve("gui.toml.v1.bak")));
        assertTrue(Files.readString(tempDir.resolve("gui.toml.v1.bak")).contains("config_version = 1"));
        assertTrue(Files.readString(tempDir.resolve("gui.toml")).contains("config_version = 2"));
        assertEquals(navigatorBeforeGuiMigration, Files.readString(tempDir.resolve("navigator.toml")));
    }

    @Test
    void existingBuiltInLanguageUsesTranslatedFallbacksForNewMessageKeys() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Path messages = tempDir.resolve("messages.toml");
        Files.writeString(messages, Files.readString(messages)
                .replaceFirst("(?m)^language = \"en\"$", "language = \"es\""));
        manager.load();

        String withoutNewStatuses = Files.readString(messages)
                .replaceAll("(?m)^status_full = .*\\R?", "")
                .replaceAll("(?m)^status_in_game = .*\\R?", "");
        Files.writeString(messages, withoutNewStatuses);

        LanguageBundle loaded = manager.load().config().language();
        LanguageBundle spanish = LanguagePacks.bundle("es");
        assertEquals(spanish.text("menus.status_full"), loaded.text("menus.status_full"));
        assertEquals(spanish.text("menus.status_in_game"), loaded.text("menus.status_in_game"));
    }

    @Test
    void loadsVersionTwoStatePresentationAndServerMetadata() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Files.writeString(tempDir.resolve("gui.toml"), """
                config_version = 2

                [states.full]
                material = "REDSTONE_BLOCK"
                name = "<red>{server}: {status}</red>"
                lore = ["<gray>{description}</gray>"]

                [states.in_game]
                material = "ENDER_CHEST"
                name = "<light_purple>{status}</light_purple>"
                lore = []

                [servers]
                "lobby-1" = { display_name = "Main Lobby", description = "Survival", menu_order = 3, show_in_menu = true }
                "internal" = { show_in_menu = false }
                """);

        ConfigLoadResult result = manager.load();
        GuiConfig gui = manager.guiConfig();

        assertFalse(result.warnings().stream().anyMatch(warning -> warning.contains("Migrated gui.toml")));
        assertEquals("REDSTONE_BLOCK", gui.stateStyle(MenuServerState.FULL).material());
        assertEquals("<red>{server}: {status}</red>", gui.stateStyle(MenuServerState.FULL).name());
        assertEquals(List.of("<gray>{description}</gray>"), gui.stateStyle(MenuServerState.FULL).lore());
        assertEquals("ENDER_CHEST", gui.stateStyle(MenuServerState.IN_GAME).material());
        assertEquals("Survival", gui.description("lobby-1"));
        assertEquals(3, gui.server("lobby-1").menuOrder());
        assertFalse(gui.server("internal").showInMenu());
        assertFalse(Files.exists(tempDir.resolve("gui.toml.v1.bak")));
    }

    @Test
    void warnsAndFallsBackWhenGuiDisplayNameIsNotAString() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Files.writeString(tempDir.resolve("gui.toml"), """
                config_version = 1
                [servers]
                "lobby-1" = { display_name = 42, name = "<aqua>{server}</aqua>" }
                """);

        ConfigLoadResult result = manager.load();

        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.contains("lobby-1") && warning.contains("display_name must be a string")));
        assertEquals("", manager.guiConfig().server("lobby-1").displayName());
        assertEquals("lobby-1", manager.guiConfig().displayName("lobby-1"));
        assertEquals("<aqua>{server}</aqua>", manager.guiConfig().server("lobby-1").name());
    }

    @Test
    void warnsButKeepsDistinctTargetsWhenGuiDisplayNamesMatch() throws Exception {
        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        manager.load();
        Files.writeString(tempDir.resolve("gui.toml"), """
                config_version = 1
                [servers]
                "lobby-1" = { display_name = "Main Lobby" }
                "lobby-2" = { display_name = "main lobby" }
                """);

        ConfigLoadResult result = manager.load();

        assertTrue(result.warnings().stream().anyMatch(warning ->
                warning.contains("lobby-1") && warning.contains("lobby-2") && warning.contains("ambiguous")));
        assertEquals("Main Lobby", manager.guiConfig().displayName("lobby-1"));
        assertEquals("main lobby", manager.guiConfig().displayName("lobby-2"));
    }

    @Test
    void migratesLegacyFlatConfigAndCreatesBackup() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 2
                lobby_servers = ["lobby-a", "lobby-b"]
                selection_mode = "ROUND_ROBIN"
                command_aliases = ["hub", "spawn", "lobby"]
                reconnect_on_lobby_command = true
                command_cooldown = 9
                ping_before_connect = false
                ping_cache_duration = 10

                [advanced_settings]
                use_contextual_lobbies = true

                [contextual_lobbies.groups]
                bedwars = ["bw-lobby-1", "bw-lobby-2"]

                [contextual_lobbies.mappings]
                "bedwars-1" = "bedwars"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertTrue(result.migrated());
        assertTrue(Files.exists(tempDir.resolve("navigator.toml.v2.bak")));
        assertEquals(Config.SelectionMode.ROUND_ROBIN, result.config().routing().selectionMode());
        assertTrue(result.config().commands().reconnectIfSameServer());
        assertTrue(result.config().routing().contextual().enabled());
        assertEquals("bedwars", result.config().routing().contextual().sources().get("bedwars-1"));
    }

    @Test
    void fallsBackFieldByFieldWhenValuesAreInvalid() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 3

                [commands]
                aliases = ["hub"]
                permission = 4
                cooldown_seconds = "fast"

                [routing]
                selection_mode = "chaos"
                default_lobbies = "not-a-list"

                [health_checks]
                enabled = "sometimes"
                timeout_ms = "slow"
                cache_seconds = 30
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertFalse(result.warnings().isEmpty());
        assertEquals(Config.SelectionMode.LEAST_PLAYERS, result.config().routing().selectionMode());
        assertEquals(3, result.config().commands().cooldownSeconds());
        assertEquals("none", result.config().commands().permission());
        assertTrue(result.config().healthChecks().enabled());
        assertEquals(30, result.config().healthChecks().cacheSeconds());
        assertEquals("hub", result.config().commands().aliases().get(0));
        assertEquals(Config.defaults().routing().defaultLobbies(), result.config().routing().defaultLobbies());
    }

    @Test
    void prometheusPortOutsideTcpRangeFallsBackToDefault() {
        Config.PrometheusSettings tooHigh = new Config.PrometheusSettings(true, 70000, "127.0.0.1");
        Config.PrometheusSettings negative = new Config.PrometheusSettings(true, -1, "127.0.0.1");

        assertEquals(9225, tooHigh.port());
        assertEquals(9225, negative.port());
    }

    @Test
    void readsPrometheusBearerToken() throws Exception {
        Path configPath = tempDir.resolve("navigator.toml");
        Files.writeString(configPath, """
                config_version = 6

                [metrics.prometheus]
                enabled = true
                port = 9225
                bind_host = "0.0.0.0"
                bearer_token = "secret-token"
                """);

        ConfigManager manager = new ConfigManager(tempDir, LoggerFactory.getLogger("config-test"));
        ConfigLoadResult result = manager.load();

        assertEquals("secret-token", result.config().metrics().prometheus().bearerToken());
    }
}
