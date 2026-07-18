/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record GuiConfig(
        int rows,
        String defaultMaterial,
        String unavailableMaterial,
        boolean fillEmptySlots,
        String fillerMaterial,
        int refreshSeconds,
        int previousSlot,
        int refreshSlot,
        int nextSlot,
        String previousMaterial,
        String refreshMaterial,
        String nextMaterial,
        Map<String, ServerItem> servers,
        BedrockMenu bedrock,
        Map<MenuServerState, StateStyle> states
) {
    public GuiConfig(int rows, String defaultMaterial, String unavailableMaterial, boolean fillEmptySlots, String fillerMaterial, int refreshSeconds, int previousSlot, int refreshSlot, int nextSlot, String previousMaterial, String refreshMaterial, String nextMaterial, Map<String, ServerItem> servers) {
        this(rows, defaultMaterial, unavailableMaterial, fillEmptySlots, fillerMaterial, refreshSeconds, previousSlot, refreshSlot, nextSlot, previousMaterial, refreshMaterial, nextMaterial, servers, BedrockMenu.defaults(), defaultStateStyles());
    }

    public GuiConfig(int rows, String defaultMaterial, String unavailableMaterial, boolean fillEmptySlots, String fillerMaterial, int refreshSeconds, int previousSlot, int refreshSlot, int nextSlot, String previousMaterial, String refreshMaterial, String nextMaterial, Map<String, ServerItem> servers, BedrockMenu bedrock) {
        this(rows, defaultMaterial, unavailableMaterial, fillEmptySlots, fillerMaterial, refreshSeconds, previousSlot, refreshSlot, nextSlot, previousMaterial, refreshMaterial, nextMaterial, servers, bedrock, defaultStateStyles());
    }

    public GuiConfig {
        rows = Math.max(2, Math.min(6, rows));
        int size = rows * 9;
        defaultMaterial = material(defaultMaterial, "COMPASS");
        unavailableMaterial = material(unavailableMaterial, "BARRIER");
        fillerMaterial = material(fillerMaterial, "GRAY_STAINED_GLASS_PANE");
        refreshSeconds = Math.max(0, Math.min(300, refreshSeconds));
        java.util.Set<Integer> controlSlots = new java.util.LinkedHashSet<>();
        previousSlot = uniqueSlot(previousSlot, size, size - 9, controlSlots);
        refreshSlot = uniqueSlot(refreshSlot, size, size - 5, controlSlots);
        nextSlot = uniqueSlot(nextSlot, size, size - 1, controlSlots);
        previousMaterial = material(previousMaterial, "ARROW");
        refreshMaterial = material(refreshMaterial, "CLOCK");
        nextMaterial = material(nextMaterial, "ARROW");
        Map<String, ServerItem> normalized = new LinkedHashMap<>();
        if (servers != null) {
            servers.forEach((name, item) -> {
                if (name != null && !name.isBlank() && item != null) {
                    normalized.put(name.trim().toLowerCase(Locale.ROOT), item.normalized(size));
                }
            });
        }
        servers = Collections.unmodifiableMap(normalized);
        bedrock = bedrock == null ? BedrockMenu.defaults() : bedrock;
        Map<MenuServerState, StateStyle> normalizedStates = new EnumMap<>(MenuServerState.class);
        normalizedStates.putAll(defaultStateStyles());
        if (states != null) {
            states.forEach((state, style) -> {
                if (state != null && style != null) {
                    normalizedStates.put(state, style.normalized());
                }
            });
        }
        states = Collections.unmodifiableMap(normalizedStates);
    }

    public static GuiConfig defaults() {
        return new GuiConfig(
                6, "COMPASS", "BARRIER", true, "GRAY_STAINED_GLASS_PANE", 5,
                45, 49, 53, "ARROW", "CLOCK", "ARROW", Map.of()
        );
    }

    public int serverPageSize() {
        return Math.max(1, rows * 9 - 9);
    }

    public ServerItem server(String name) {
        return name == null ? null : servers.get(name.toLowerCase(Locale.ROOT));
    }

    public String displayName(String serverName) {
        if (serverName == null) {
            return "";
        }
        ServerItem item = server(serverName);
        return item == null || item.displayName().isBlank() ? serverName : item.displayName();
    }

    public String description(String serverName) {
        ServerItem item = server(serverName);
        return item == null ? "" : item.description();
    }

    public StateStyle stateStyle(MenuServerState state) {
        return states.getOrDefault(state == null ? MenuServerState.HEALTHY : state, StateStyle.empty());
    }

    public List<String> visibleServers(List<String> serverNames) {
        if (serverNames == null || serverNames.isEmpty()) {
            return List.of();
        }
        List<String> visible = serverNames.stream()
                .filter(name -> {
                    ServerItem item = server(name);
                    return item == null || item.showInMenu();
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        visible.sort(Comparator
                .comparingInt((String name) -> menuOrder(name) >= 0 ? 0 : 1)
                .thenComparingInt(name -> Math.max(0, menuOrder(name))));
        return List.copyOf(visible);
    }

    private int menuOrder(String serverName) {
        ServerItem item = server(serverName);
        return item == null ? -1 : item.menuOrder();
    }

    private static int uniqueSlot(int value, int size, int fallback, java.util.Set<Integer> used) {
        int firstControlSlot = size - 9;
        int selected = value >= firstControlSlot && value < size ? value : fallback;
        if (!used.add(selected)) {
            for (int candidate = firstControlSlot; candidate < size; candidate++) {
                if (used.add(candidate)) {
                    return candidate;
                }
            }
        }
        return selected;
    }

    private static String material(String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value.trim();
        if (selected == null || selected.isBlank()) {
            return "";
        }
        int namespaceSeparator = selected.indexOf(':');
        if (namespaceSeparator > 0) {
            String namespace = selected.substring(0, namespaceSeparator).toLowerCase(Locale.ROOT);
            String key = selected.substring(namespaceSeparator + 1).toUpperCase(Locale.ROOT);
            return namespace + ":" + key;
        }
        return selected.toUpperCase(Locale.ROOT);
    }

    public record ServerItem(
            int slot,
            String material,
            String unavailableMaterial,
            String displayName,
            String description,
            int menuOrder,
            boolean showInMenu,
            String name,
            List<String> lore
    ) {
        public ServerItem(int slot, String material, String unavailableMaterial, String name, List<String> lore) {
            this(slot, material, unavailableMaterial, "", "", -1, true, name, lore);
        }

        public ServerItem(int slot, String material, String unavailableMaterial, String displayName, String name, List<String> lore) {
            this(slot, material, unavailableMaterial, displayName, "", -1, true, name, lore);
        }

        private ServerItem normalized(int inventorySize) {
            return new ServerItem(
                    slot >= 0 && slot < inventorySize ? slot : -1,
                    GuiConfig.material(material, ""),
                    GuiConfig.material(unavailableMaterial, ""),
                    displayName == null ? "" : displayName.trim(),
                    description == null ? "" : description.trim(),
                    Math.max(-1, menuOrder),
                    showInMenu,
                    name == null ? "" : name,
                    lore == null ? List.of() : List.copyOf(lore)
            );
        }
    }

    public record StateStyle(String material, String name, List<String> lore) {
        public StateStyle {
            material = GuiConfig.material(material, "");
            name = name == null ? "" : name;
            lore = lore == null ? List.of() : List.copyOf(lore);
        }

        private StateStyle normalized() {
            return new StateStyle(material, name, lore);
        }

        public static StateStyle empty() {
            return new StateStyle("", "", List.of());
        }
    }

    public static Map<MenuServerState, StateStyle> defaultStateStyles() {
        Map<MenuServerState, StateStyle> defaults = new EnumMap<>(MenuServerState.class);
        defaults.put(MenuServerState.HEALTHY, StateStyle.empty());
        defaults.put(MenuServerState.FULL, new StateStyle(
                "RED_CONCRETE", "<red><bold>{server}</bold></red> <gray>({status})</gray>", List.of()));
        defaults.put(MenuServerState.DRAINING, new StateStyle(
                "YELLOW_CONCRETE", "<yellow><bold>{server}</bold></yellow> <gray>({status})</gray>", List.of()));
        defaults.put(MenuServerState.OFFLINE, new StateStyle(
                "BARRIER", "<gray><bold>{server}</bold> ({status})</gray>", List.of()));
        defaults.put(MenuServerState.IN_GAME, new StateStyle(
                "ENDER_EYE", "<light_purple><bold>{server}</bold></light_purple> <gray>({status})</gray>", List.of()));
        return Collections.unmodifiableMap(defaults);
    }

    public record BedrockMenu(boolean enabled, boolean fallbackToChat, String sortMode, int maxButtons, boolean showPlayers, boolean showMaxPlayers, boolean showPing, boolean showStatus, String title, String content, String buttonFormat) {
        public BedrockMenu {
            sortMode = sortMode == null ? "routing" : sortMode.trim().toLowerCase(Locale.ROOT);
            if (!List.of("routing", "name", "players").contains(sortMode)) sortMode = "routing";
            maxButtons = Math.max(1, Math.min(500, maxButtons));
            title = title == null ? "" : title;
            content = content == null ? "" : content;
            buttonFormat = buttonFormat == null ? "" : buttonFormat;
        }

        public static BedrockMenu defaults() {
            return new BedrockMenu(true, true, "routing", 100, true, true, true, true, "", "", "");
        }
    }
}
