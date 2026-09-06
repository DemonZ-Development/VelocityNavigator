/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProxyMenuHolder implements InventoryHolder {

    private final String token;
    private final int page;
    private final Map<Integer, String> targetsBySlot = new LinkedHashMap<>();
    private Inventory inventory;

    public ProxyMenuHolder(String token, int page) {
        this.token = token;
        this.page = page;
    }

    public String token() {
        return token;
    }

    public int page() {
        return page;
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
