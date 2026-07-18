/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerDisplayNameTest {

    @Test
    void guiDisplayNamesAreCaseInsensitiveTrimmedAndFallBackToServerId() {
        GuiConfig gui = gui(Map.of(
                "Lobby-One", new GuiConfig.ServerItem(-1, "", "", "  Main Lobby 一  ", "", List.of()),
                "blank-name", new GuiConfig.ServerItem(-1, "", "", "   ", "", List.of())
        ));

        assertEquals("Main Lobby 一", gui.displayName("LOBBY-ONE"));
        assertEquals("unknown-server", gui.displayName("unknown-server"));
        assertEquals("Blank-Name", gui.displayName("Blank-Name"));
        assertEquals("", gui.displayName(null));
    }

    @Test
    void menuPlaceholdersExposeAliasAndCanonicalServerIdSeparately() {
        MenuServerInfo info = new MenuServerInfo(
                "lobby1", "Main Lobby 1", 12, "100", "HEALTHY", "<green>", "24", true
        );

        assertEquals("Main Lobby 1", info.placeholders().get("server"));
        assertEquals("Main Lobby 1", info.placeholders().get("display_name"));
        assertEquals("lobby1", info.placeholders().get("server_id"));
    }

    @Test
    void descriptionIsAvailableToEveryMenuTemplate() {
        MenuServerInfo info = new MenuServerInfo(
                "lobby1", "Main Lobby 1", "Classic survival lobby", 12, "100",
                "HEALTHY", "<green>", "24", true, MenuServerState.HEALTHY
        );

        assertEquals("Classic survival lobby", info.placeholders().get("description"));
        assertEquals("Main Lobby 1 — Classic survival lobby",
                MenuTemplateFormatter.replace("{server} — {description}", info.placeholders()));
    }

    @Test
    void menuPlaceholderValuesAreInsertedOnceWithoutRecursiveExpansion() {
        MenuServerInfo info = new MenuServerInfo(
                "lobby1", "Main {server_id}", 12, "100", "HEALTHY", "<green>", "24", true
        );

        String rendered = MenuTemplateFormatter.replace(
                "<aqua>{server}</aqua>|<display_name>|{server_id}|<bold>kept</bold>",
                info.placeholders()
        );

        assertEquals("<aqua>Main {server_id}</aqua>|Main {server_id}|lobby1|<bold>kept</bold>", rendered);
    }

    @Test
    void javaInventoryUsesDisplayNameButKeepsCanonicalClickTarget() {
        GuiConfig gui = gui(Map.of(
                "lobby1", new GuiConfig.ServerItem(-1, "", "", "Main Lobby 1", "", List.of())
        ));
        MenuServerInfo info = new MenuServerInfo(
                "lobby1", gui.displayName("lobby1"), 3, "100", "HEALTHY", "<green>", "18", true
        );

        List<MenuBridgeProtocol.MenuItem> items = JavaInventoryMenuService.buildServerItems(
                null, Config.defaults(), gui, List.of(info)
        );

        assertEquals(1, items.size());
        MenuBridgeProtocol.MenuItem item = items.get(0);
        assertEquals("lobby1", item.target());
        assertTrue(item.name().contains("Main Lobby 1"));
        assertFalse(item.name().contains("lobby1"));
    }

    @Test
    void bedrockButtonSupportsAliasAndRawServerPlaceholders() {
        String button = BedrockFormService.formatButtonText(
                "{server}|{display_name}|{server_id}|{description}|{players}/{max_players}|{ping}|{status_color}{status}",
                "lobby1", "Main Lobby 1", "Survival", "12", "100", "24", "HEALTHY", "<green>"
        );

        assertEquals("Main Lobby 1|Main Lobby 1|lobby1|Survival|12/100|24|<green>HEALTHY", button);
    }

    @Test
    void bedrockNameSortUsesDisplayNameWithServerIdAsTieBreaker() {
        GuiConfig gui = gui(Map.of(
                "lobby-a", new GuiConfig.ServerItem(-1, "", "", "Zulu", "", List.of()),
                "lobby-b", new GuiConfig.ServerItem(-1, "", "", "Alpha", "", List.of()),
                "lobby-c", new GuiConfig.ServerItem(-1, "", "", "Alpha", "", List.of())
        ));
        List<String> candidates = new ArrayList<>(List.of("lobby-c", "lobby-a", "lobby-b"));

        candidates.sort(BedrockFormService.displayNameComparator(gui));

        assertEquals(List.of("lobby-b", "lobby-c", "lobby-a"), candidates);
    }

    @Test
    void selectorVisibilityAndMenuOrderAreStableAndDoNotRewriteTargets() {
        Map<String, GuiConfig.ServerItem> overrides = new LinkedHashMap<>();
        overrides.put("late", serverItem("Late", "", 20, true, ""));
        overrides.put("tie-b", serverItem("Tie B", "", 10, true, ""));
        overrides.put("hidden", serverItem("Hidden", "", 0, false, ""));
        overrides.put("tie-a", serverItem("Tie A", "", 10, true, ""));
        overrides.put("unset", serverItem("Unset", "", -1, true, ""));
        GuiConfig gui = gui(overrides);

        List<String> ordered = gui.visibleServers(
                List.of("unset", "tie-b", "hidden", "late", "unknown", "tie-a"));

        assertEquals(List.of("tie-b", "tie-a", "late", "unset", "unknown"), ordered);
    }

    @Test
    void bedrockSecondarySortRunsBeforeExplicitMenuOrderAndKeepsPlayerTiesStable() {
        GuiConfig gui = gui(Map.of(
                "lobby-a", serverItem("Zulu", "", -1, true, ""),
                "lobby-b", serverItem("Alpha", "", 1, true, ""),
                "lobby-c", serverItem("Alpha", "", -1, true, ""),
                "hidden", serverItem("Aardvark", "", 0, false, "")
        ));
        GuiConfig.BedrockMenu nameMenu = new GuiConfig.BedrockMenu(
                true, true, "name", 100, true, true, true, true, "", "", "");
        GuiConfig.BedrockMenu playersMenu = new GuiConfig.BedrockMenu(
                true, true, "players", 100, true, true, true, true, "", "", "");

        assertEquals(List.of("lobby-b", "lobby-c", "lobby-a"),
                BedrockFormService.orderedCandidates(
                        List.of("lobby-c", "hidden", "lobby-a", "lobby-b"), gui, nameMenu, ignored -> 0));
        assertEquals(List.of("lobby-b", "lobby-a", "lobby-c"),
                BedrockFormService.orderedCandidates(
                        List.of("lobby-a", "lobby-c", "lobby-b"), gui, playersMenu,
                        name -> "lobby-b".equals(name) ? 50 : 2));
    }

    @Test
    void stateResolutionUsesHealthDrainBackendCapacityAndCircuitSignals() {
        assertEquals(MenuServerState.OFFLINE,
                MenuServerInfoResolver.resolveState(true, false, true, true, true, "IN_GAME"));
        assertEquals(MenuServerState.OFFLINE,
                MenuServerInfoResolver.resolveState(true, true, false, false, false, ""));
        assertEquals(MenuServerState.DRAINING,
                MenuServerInfoResolver.resolveState(false, true, true, true, true, "IN_GAME"));
        assertEquals(MenuServerState.IN_GAME,
                MenuServerInfoResolver.resolveState(false, true, false, true, true, "in_game"));
        assertEquals(MenuServerState.FULL,
                MenuServerInfoResolver.resolveState(false, true, false, true, true, "LOBBY"));
        assertEquals(MenuServerState.HEALTHY,
                MenuServerInfoResolver.resolveState(true, null, false, true, false, "LOBBY"));
        assertEquals(MenuServerState.OFFLINE,
                MenuServerInfoResolver.resolveState(false, null, false, true, false, ""));
        assertEquals(MenuServerState.OFFLINE,
                MenuServerInfoResolver.resolveState(false, true, false, true, false, "MAINTENANCE", false));
        assertEquals(MenuServerState.IN_GAME,
                MenuServerInfoResolver.resolveState(false, true, false, true, false, "IN_GAME", false));
    }

    @Test
    void unavailableMaterialOverrideWinsOverStateStyleAndStateWinsOverGlobalFallback() {
        GuiConfig gui = gui(Map.of(
                "custom", serverItem("Custom", "", -1, true, "BLACK_CONCRETE"),
                "state-default", serverItem("State Default", "", -1, true, "")
        ));
        MenuServerInfo custom = new MenuServerInfo(
                "custom", "Custom", "", 100, "100", "FULL", "<red>", "10", false, MenuServerState.FULL);
        MenuServerInfo stateDefault = new MenuServerInfo(
                "state-default", "State Default", "", 100, "100", "FULL", "<red>", "10", false, MenuServerState.FULL);

        List<MenuBridgeProtocol.MenuItem> items = JavaInventoryMenuService.buildServerItems(
                null, Config.defaults(), gui, List.of(custom, stateDefault));

        assertEquals("BLACK_CONCRETE", items.get(0).material());
        assertEquals("RED_CONCRETE", items.get(1).material());
        assertTrue(items.get(1).name().contains("FULL"));
        assertEquals("@disabled", items.get(0).target());
    }

    @Test
    void perServerNameAndLoreRemainFinalStatePresentationOverrides() {
        GuiConfig gui = gui(Map.of(
                "lobby1", new GuiConfig.ServerItem(
                        -1, "", "", "Main Lobby", "Description", -1, true,
                        "<gold>{server}</gold>", List.of("<gray>{description}</gray>"))
        ));
        MenuServerInfo info = new MenuServerInfo(
                "lobby1", "Main Lobby", "Description", 100, "100", "FULL", "<red>", "10", false,
                MenuServerState.FULL);

        MenuBridgeProtocol.MenuItem item = JavaInventoryMenuService.buildServerItems(
                null, Config.defaults(), gui, List.of(info)).get(0);

        assertTrue(item.name().contains("Main Lobby"));
        assertFalse(item.name().contains("FULL"));
        assertEquals(1, item.lore().size());
        assertTrue(item.lore().get(0).contains("Description"));
    }

    private static GuiConfig gui(Map<String, GuiConfig.ServerItem> servers) {
        return new GuiConfig(
                6,
                "COMPASS",
                "BARRIER",
                true,
                "GRAY_STAINED_GLASS_PANE",
                5,
                45,
                49,
                53,
                "ARROW",
                "CLOCK",
                "ARROW",
                servers
        );
    }

    private static GuiConfig.ServerItem serverItem(String displayName, String description, int order,
                                                    boolean visible, String unavailableMaterial) {
        return new GuiConfig.ServerItem(
                -1, "", unavailableMaterial, displayName, description, order, visible, "", List.of());
    }
}
