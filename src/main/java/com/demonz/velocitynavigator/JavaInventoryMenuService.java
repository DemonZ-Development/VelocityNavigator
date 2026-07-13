/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaInventoryMenuService {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(MenuBridgeProtocol.CHANNEL);
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private JavaInventoryMenuService() {
    }

    public static boolean showLobbyMenu(Player player, VelocityNavigator plugin, RouteDecision decision) {
        return showLobbyMenu(player, plugin, decision, 0);
    }

    public static boolean showLobbyMenu(Player player, VelocityNavigator plugin, RouteDecision decision, int requestedPage) {
        Config config = plugin.config();
        GuiConfig gui = plugin.guiConfig();
        List<String> configured = decision.configuredCandidates() == null || decision.configuredCandidates().isEmpty()
                ? decision.onlineCandidates()
                : decision.configuredCandidates();
        if (configured == null || configured.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", config.language().text("reasons.no_online_lobbies"), "player", player.getUsername()), player));
            return true;
        }

        List<String> candidates = List.copyOf(new LinkedHashSet<>(configured));
        List<MenuServerInfo> all = MenuServerInfoResolver.resolve(plugin, config, candidates, decision.onlineCandidates());
        int pageSize = gui.serverPageSize();
        int totalPages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        int from = page * pageSize;
        int to = Math.min(all.size(), from + pageSize);
        List<MenuServerInfo> visible = all.subList(from, to);

        List<String> allowedServers = visible.stream().filter(MenuServerInfo::available).map(MenuServerInfo::server).toList();
        String token = plugin.createMenuToken(player, allowedServers);
        List<MenuBridgeProtocol.MenuItem> items = buildServerItems(player, config, gui, visible);
        Set<Integer> occupied = new LinkedHashSet<>();
        items.forEach(item -> occupied.add(item.slot()));
        addControls(player, config, gui, page, totalPages, items, occupied);

        try {
            byte[] payload = MenuBridgeProtocol.encodeOpen(
                    token,
                    gui.rows(),
                    legacy(config.language().text("menus.inventory.title"), player),
                    page,
                    totalPages,
                    gui.refreshSeconds(),
                    gui.fillEmptySlots(),
                    gui.fillerMaterial(),
                    items
            );
            return player.getCurrentServer().map(connection -> connection.sendPluginMessage(CHANNEL, payload)).orElse(false);
        } catch (IOException | IllegalArgumentException exception) {
            plugin.logger().warn("[VelocityNavigator] Could not build the Java inventory selector for {}: {}",
                    player.getUsername(), exception.getMessage());
            return false;
        }
    }

    private static List<MenuBridgeProtocol.MenuItem> buildServerItems(Player player, Config config, GuiConfig gui,
                                                                       List<MenuServerInfo> visible) {
        int automaticLimit = gui.rows() * 9 - 9;
        Set<Integer> used = new LinkedHashSet<>();
        List<MenuBridgeProtocol.MenuItem> result = new ArrayList<>();
        Map<MenuServerInfo, Integer> assigned = new LinkedHashMap<>();

        for (MenuServerInfo info : visible) {
            GuiConfig.ServerItem override = gui.server(info.server());
            if (override != null && override.slot() >= 0 && override.slot() < gui.rows() * 9
                    && override.slot() != gui.previousSlot() && override.slot() != gui.refreshSlot()
                    && override.slot() != gui.nextSlot() && used.add(override.slot())) {
                assigned.put(info, override.slot());
            }
        }
        int cursor = 0;
        for (MenuServerInfo info : visible) {
            if (assigned.containsKey(info)) {
                continue;
            }
            while (cursor < automaticLimit && used.contains(cursor)) {
                cursor++;
            }
            if (cursor >= automaticLimit) {
                break;
            }
            assigned.put(info, cursor);
            used.add(cursor++);
        }

        for (Map.Entry<MenuServerInfo, Integer> entry : assigned.entrySet()) {
            MenuServerInfo info = entry.getKey();
            GuiConfig.ServerItem override = gui.server(info.server());
            String material;
            if (info.available()) {
                material = override != null && !override.material().isBlank() ? override.material() : gui.defaultMaterial();
            } else {
                material = override != null && !override.unavailableMaterial().isBlank()
                        ? override.unavailableMaterial() : gui.unavailableMaterial();
            }
            String nameTemplate = override != null && !override.name().isBlank()
                    ? override.name() : config.language().text("menus.inventory.item_name");
            List<String> loreTemplate = override != null && !override.lore().isEmpty()
                    ? override.lore()
                    : config.language().lines(info.available()
                    ? "menus.inventory.item_lore" : "menus.inventory.unavailable_lore");
            String name = legacy(replace(nameTemplate, info.placeholders()), player);
            List<String> lore = loreTemplate.stream().map(line -> legacy(replace(line, info.placeholders()), player)).toList();
            result.add(new MenuBridgeProtocol.MenuItem(entry.getValue(), info.available() ? info.server() : "@disabled",
                    material, name, lore));
        }
        return result;
    }

    private static void addControls(Player player, Config config, GuiConfig gui, int page, int pages,
                                    List<MenuBridgeProtocol.MenuItem> items, Set<Integer> occupied) {
        Map<String, String> pageValues = Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages));
        if (page > 0 && occupied.add(gui.previousSlot())) {
            items.add(new MenuBridgeProtocol.MenuItem(gui.previousSlot(), "@page:" + (page - 1), gui.previousMaterial(),
                    legacy(config.language().text("menus.inventory.previous"), player),
                    List.of(legacy(replace(config.language().text("menus.inventory.page"), pageValues), player))));
        }
        if (occupied.add(gui.refreshSlot())) {
            items.add(new MenuBridgeProtocol.MenuItem(gui.refreshSlot(), "@refresh:" + page, gui.refreshMaterial(),
                    legacy(config.language().text("menus.inventory.refresh"), player),
                    List.of(legacy(replace(config.language().text("menus.inventory.page"), pageValues), player))));
        }
        if (page + 1 < pages && occupied.add(gui.nextSlot())) {
            items.add(new MenuBridgeProtocol.MenuItem(gui.nextSlot(), "@page:" + (page + 1), gui.nextMaterial(),
                    legacy(config.language().text("menus.inventory.next"), player),
                    List.of(legacy(replace(config.language().text("menus.inventory.page"), pageValues), player))));
        }
    }

    private static String legacy(String text, Player player) {
        return LEGACY.serialize(MessageFormatter.render(text, player));
    }

    private static String replace(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue())
                    .replace("<" + entry.getKey() + ">", entry.getValue());
        }
        return result;
    }
}
