/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BackendMenuHolder implements InventoryHolder {

    private final String menuName;
    private final Map<Integer, String> targetsBySlot = new LinkedHashMap<>();
    private Inventory inventory;

    public BackendMenuHolder(String menuName) {
        this.menuName = menuName == null ? "main" : menuName.toLowerCase();
    }

    public String menuName() {
        return menuName;
    }

    public Map<Integer, String> targetsBySlot() {
        return targetsBySlot;
    }

    public void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
