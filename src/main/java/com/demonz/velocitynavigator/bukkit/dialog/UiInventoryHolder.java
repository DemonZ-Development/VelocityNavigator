/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.dialog;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class UiInventoryHolder implements InventoryHolder {
    private final String token;
    private Inventory inventory;
    private final Map<Integer, String> actions = new HashMap<>();
    public UiInventoryHolder(String token) { this.token = token; }
    public String token() { return token; }
    public Map<Integer, String> actions() { return actions; }
    public void bind(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
