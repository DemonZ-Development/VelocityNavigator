/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuConfigValidatorTest {

    @Test
    void acceptsValidAliasesNamespacedMaterialsAndEverySelectorPlaceholder(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [layout]
                rows = 2
                default_material = "COMPASS"
                unavailable_material = "minecraft:barrier"
                filler_material = "custom.items:filler/pane"

                [controls]
                previous_slot = 15
                refresh_slot = 16
                next_slot = 17
                previous_material = "minecraft:arrow"
                refresh_material = "CLOCK"
                next_material = "namespace.with-dots:next/item"

                [servers]
                "lobby-1" = { display_name = "Main Lobby", description = "Classic lobby", menu_order = 0, show_in_menu = true, slot = 0, material = "minecraft:nether_star", unavailable_material = "BARRIER", name = "{server} {display_name} {server_id}", lore = ["{description} {players}/{max_players} {status} {status_color} {ping}"] }

                [states.full]
                material = "minecraft:red_concrete"
                name = "{server} ({status})"
                lore = ["{description}"]
                """);

        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of("LOBBY-1")
        );

        assertTrue(validation.valid(), validation.errors().toString());
        assertTrue(validation.warnings().isEmpty());
        assertEquals("Menu configuration validation passed with 0 warning(s).", validation.summary());
    }

    @Test
    void reportsAllActionableRawMenuProblemsDeterministically(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [layout]
                rows = 2
                default_material = "bad material"

                [controls]
                previous_slot = 16
                refresh_slot = 16
                next_slot = 17

                [servers]
                alpha = { display_name = " Main Lobby ", description = 42, menu_order = -2, show_in_menu = "yes", slot = 3, material = "minecraft:valid/path", unavailable_material = "minecraft:bad:value", name = "{server} {unknown}", lore = [42] }
                beta = { display_name = "main lobby", slot = 3, name = 99, lore = "not a list" }
                missing = { display_name = 7, menu_order = 1.5, slot = 16, material = 123 }

                [states.full]
                material = "bad material"
                name = 12
                lore = "not a list"

                [states.offline]
                name = "{server_id} {mystery}"
                lore = ["{status}"]
                """);

        MenuConfigValidator.Validation first = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of("alpha", "beta")
        );
        MenuConfigValidator.Validation second = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of("beta", "alpha")
        );

        assertFalse(first.valid());
        assertEquals(first, second);
        assertEquals(first.errors().stream().sorted().toList(), first.errors());
        assertEquals(first.warnings().stream().sorted().toList(), first.warnings());
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("missing") && value.contains("registered Velocity server ID")));
        assertTrue(first.warnings().stream().anyMatch(value -> value.contains("alpha") && value.contains("beta") && value.contains("display_name")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("previous_slot") && value.contains("refresh_slot") && value.contains("slot 16")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("alpha'.slot") && value.contains("beta'.slot") && value.contains("slot 3")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("missing'.slot 16") && value.contains("previous_slot")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("default_material") && value.contains("invalid material")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("unavailable_material") && value.contains("invalid material")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("states.'full'.material") && value.contains("invalid material")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("{unknown}")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("{mystery}")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("description must be a string")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("menu_order must be -1 or greater")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("show_in_menu must be true or false")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("alpha'.lore[0] must be a string")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("beta'.lore must be a list of strings")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("states.'full'.name must be a string")));
        assertTrue(first.errors().stream().anyMatch(value -> value.contains("states.'full'.lore must be a list of strings")));
        assertTrue(first.summary().startsWith("Menu configuration validation found "));
        assertTrue(first.summary().endsWith(" error(s) and 1 warning(s)."));
    }

    @Test
    void derivesSlotRangeFromClampedRawRows(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [layout]
                rows = 7

                [controls]
                previous_slot = 51
                refresh_slot = 52
                next_slot = 53

                [servers]
                valid = { slot = 50 }
                invalid = { slot = 54 }
                """);

        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of("valid", "invalid")
        );

        assertTrue(validation.errors().contains("layout.rows must be between 2 and 6; found 7."));
        assertTrue(validation.errors().stream().anyMatch(value -> value.contains("invalid'.slot")
                && value.contains("between 0 and 53") && value.contains("layout.rows = 6")));
        assertFalse(validation.errors().stream().anyMatch(value -> value.contains("servers.'valid'.slot must be")));
    }

    @Test
    void requiresControlsToStayInTheReservedBottomRow(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [layout]
                rows = 4

                [controls]
                previous_slot = 0
                refresh_slot = 31
                next_slot = 35
                """);

        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of()
        );

        assertFalse(validation.valid());
        assertTrue(validation.errors().contains(
                "controls.previous_slot must be in the reserved bottom row between 27 and 35; found 0."));
    }

    @Test
    void reportsMissingGuiFileWithCommandReadySummary(@TempDir Path directory) {
        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                directory.resolve("gui.toml"), GuiConfig.defaults(), Set.of()
        );

        assertEquals(List.of("gui.toml is missing."), validation.errors());
        assertEquals("Menu configuration validation found 1 error(s) and 0 warning(s).", validation.summary());
    }

    @Test
    void rejectsMalformedServerAndStateEntries(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [servers]
                lobby1 = "invalid"

                [states]
                full = "invalid"
                """);

        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of("lobby1")
        );

        assertTrue(validation.errors().contains("servers.'lobby1' must be a table of fields."));
        assertTrue(validation.errors().contains("states.'full' must be a table."));
    }

    @Test
    void rejectsUnsupportedAndMisspelledStateNames(@TempDir Path directory) throws Exception {
        Path gui = directory.resolve("gui.toml");
        Files.writeString(gui, """
                [states.healthy]
                material = "COMPASS"

                [states.fll]
                material = "RED_CONCRETE"
                """);

        MenuConfigValidator.Validation validation = MenuConfigValidator.validate(
                gui, GuiConfig.defaults(), Set.of()
        );

        assertFalse(validation.valid());
        assertTrue(validation.errors().contains(
                "states.'healthy' is not a supported menu state. Supported states: full, draining, offline, in_game."));
        assertTrue(validation.errors().contains(
                "states.'fll' is not a supported menu state. Supported states: full, draining, offline, in_game."));
    }
}
