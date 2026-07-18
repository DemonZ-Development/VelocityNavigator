/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiConfigInventoryLayoutTest {

    @ParameterizedTest
    @CsvSource({
            "2, 9, 9, 13, 17",
            "3, 18, 18, 22, 26",
            "4, 27, 27, 31, 35",
            "5, 36, 36, 40, 44",
            "6, 45, 45, 49, 53"
    })
    void supportsEveryChestInventorySizeAndRelocatesDefaultControls(
            int rows, int pageSize, int previous, int refresh, int next) {
        GuiConfig gui = config(rows, 45, 49, 53);

        assertEquals(rows, gui.rows());
        assertEquals(pageSize, gui.serverPageSize());
        assertEquals(previous, gui.previousSlot());
        assertEquals(refresh, gui.refreshSlot());
        assertEquals(next, gui.nextSlot());
    }

    @ParameterizedTest
    @CsvSource({
            "2, 9, 13, 17",
            "3, 18, 22, 26",
            "4, 27, 31, 35",
            "5, 36, 40, 44",
            "6, 45, 49, 53"
    })
    void movesControlsOutOfAutomaticServerSlots(int rows, int previous, int refresh, int next) {
        GuiConfig gui = config(rows, 0, 1, 2);

        assertEquals(previous, gui.previousSlot());
        assertEquals(refresh, gui.refreshSlot());
        assertEquals(next, gui.nextSlot());
    }

    @Test
    void preservesResolvableMinecraftNamespaceAcrossEveryMaterialOverride() {
        GuiConfig gui = new GuiConfig(
                4, "MINECRAFT:compass", "Minecraft:barrier", true,
                "minecraft:gray_stained_glass_pane", 5,
                27, 31, 35,
                "MINECRAFT:arrow", "minecraft:clock", "Minecraft:arrow",
                Map.of("lobby", new GuiConfig.ServerItem(
                        -1, "MINECRAFT:nether_star", "Minecraft:red_concrete",
                        "", "", -1, true, "", List.of())),
                GuiConfig.BedrockMenu.defaults(),
                Map.of(MenuServerState.FULL,
                        new GuiConfig.StateStyle("MINECRAFT:redstone_block", "", List.of()))
        );

        assertEquals(Material.COMPASS, Material.matchMaterial(gui.defaultMaterial()));
        assertEquals(Material.BARRIER, Material.matchMaterial(gui.unavailableMaterial()));
        assertEquals(Material.GRAY_STAINED_GLASS_PANE, Material.matchMaterial(gui.fillerMaterial()));
        assertEquals(Material.ARROW, Material.matchMaterial(gui.previousMaterial()));
        assertEquals(Material.CLOCK, Material.matchMaterial(gui.refreshMaterial()));
        assertEquals(Material.ARROW, Material.matchMaterial(gui.nextMaterial()));
        assertEquals(Material.NETHER_STAR, Material.matchMaterial(gui.server("lobby").material()));
        assertEquals(Material.RED_CONCRETE, Material.matchMaterial(gui.server("lobby").unavailableMaterial()));
        assertEquals(Material.REDSTONE_BLOCK,
                Material.matchMaterial(gui.stateStyle(MenuServerState.FULL).material()));
    }

    private static GuiConfig config(int rows, int previous, int refresh, int next) {
        return new GuiConfig(
                rows, "COMPASS", "BARRIER", true, "GRAY_STAINED_GLASS_PANE", 5,
                previous, refresh, next, "ARROW", "CLOCK", "ARROW", Map.of()
        );
    }
}
