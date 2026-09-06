/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.logging.Level;

public final class BackendMenuListener implements Listener {

    private final Plugin plugin;
    private final BackendMenuManager menuManager;

    public BackendMenuListener(Plugin plugin, BackendMenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof BackendMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        String target = holder.targetsBySlot().get(slot);
        if (target == null) {
            return;
        }

        String normalized = target.toLowerCase();
        if (normalized.startsWith("menu:")) {
            String subMenu = normalized.substring(5);
            menuManager.openMenu(player, subMenu);
            return;
        }
        if (normalized.startsWith("cmd:") || normalized.startsWith("command:")) {
            String cmd = normalized.startsWith("cmd:") ? normalized.substring(4) : normalized.substring(8);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isBlank()) {
                player.performCommand(cmd);
            }
            return;
        }
        player.closeInventory();
        sendBackendSelection(player, target);
    }

    private void sendBackendSelection(Player player, String targetServer) {
        try {
            player.sendPluginMessage(plugin, MenuBridgeProtocol.CHANNEL,
                    MenuBridgeProtocol.encodeSelection(MenuBridgeProtocol.BACKEND_SELECTION_TOKEN, targetServer));
        } catch (IOException | IllegalArgumentException error) {
            plugin.getLogger().log(Level.WARNING,
                    "[VelocityNavigator] Failed to send backend menu selection for {}: {}",
                    new Object[]{targetServer, error.getMessage()});
        }
    }
}
