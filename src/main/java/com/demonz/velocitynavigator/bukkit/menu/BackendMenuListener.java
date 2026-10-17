/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.Plugin;


public final class BackendMenuListener implements Listener {

    private final BackendMenuManager menuManager;

    public BackendMenuListener(Plugin plugin, BackendMenuManager menuManager) {
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

        menuManager.executeAction(player, target);
    }

}
