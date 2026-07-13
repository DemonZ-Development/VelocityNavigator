/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Collections;
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
        BedrockMenu bedrock
) {
    public GuiConfig(int rows, String defaultMaterial, String unavailableMaterial, boolean fillEmptySlots, String fillerMaterial, int refreshSeconds, int previousSlot, int refreshSlot, int nextSlot, String previousMaterial, String refreshMaterial, String nextMaterial, Map<String, ServerItem> servers) {
        this(rows, defaultMaterial, unavailableMaterial, fillEmptySlots, fillerMaterial, refreshSeconds, previousSlot, refreshSlot, nextSlot, previousMaterial, refreshMaterial, nextMaterial, servers, BedrockMenu.defaults());
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

    private static int slot(int value, int size, int fallback) {
        return value >= 0 && value < size ? value : fallback;
    }

    private static int uniqueSlot(int value, int size, int fallback, java.util.Set<Integer> used) {
        int selected = slot(value, size, fallback);
        if (!used.add(selected)) {
            for (int candidate = size - 9; candidate < size; candidate++) {
                if (used.add(candidate)) {
                    return candidate;
                }
            }
        }
        return selected;
    }

    private static String material(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    public record ServerItem(int slot, String material, String unavailableMaterial, String name, List<String> lore) {
        private ServerItem normalized(int inventorySize) {
            return new ServerItem(
                    slot >= 0 && slot < inventorySize ? slot : -1,
                    material == null ? "" : material.trim().toUpperCase(Locale.ROOT),
                    unavailableMaterial == null ? "" : unavailableMaterial.trim().toUpperCase(Locale.ROOT),
                    name == null ? "" : name,
                    lore == null ? List.of() : List.copyOf(lore)
            );
        }
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
