/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.demonz.velocitynavigator.bukkit.VelocityNavigatorBridge;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendBridgeCompatibilityTest {
    @Test
    void backendEntrypointImplementsRequiredSpigotContracts() {
        assertTrue(JavaPlugin.class.isAssignableFrom(VelocityNavigatorBridge.class));
        assertTrue(PluginMessageListener.class.isAssignableFrom(VelocityNavigatorBridge.class));
        assertTrue(Listener.class.isAssignableFrom(VelocityNavigatorBridge.class));
    }

    @Test
    void backendDescriptorAndDefaultsArePackaged() throws Exception {
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(plugin.contains("main: com.demonz.velocitynavigator.bukkit.VelocityNavigatorBridge"));
        assertTrue(plugin.contains("version: '4.3.0'"));
        assertTrue(plugin.contains("api-version: '1.16'"));
        assertTrue(config.contains("enabled: true"));
        assertTrue(config.contains("inventory_menu_enabled: true"));
        assertTrue(config.contains("handshake_enabled: true"));
        assertTrue(config.contains("fallback_material: COMPASS"));
        assertTrue(config.contains("bstats_enabled: true"));
        assertTrue(config.contains("bstats_plugin_id: 0"));
        assertTrue(config.contains("redis:\n  enabled: false"));
        assertTrue(config.contains("registration_secret: ''"));
        assertTrue(org.bstats.bukkit.Metrics.class.getName().startsWith("org.bstats.bukkit"));
        assertTrue(org.bstats.velocity.Metrics.class.getName().startsWith("org.bstats.velocity"));
    }

    @Test
    void sixRowBackendPayloadRoundTrips() throws Exception {
        MenuBridgeProtocol.OpenMenu decoded = MenuBridgeProtocol.decodeOpen(MenuBridgeProtocol.encodeOpen(
                "backend-test", 6, "§bSelector", 45, 49, 53, true, "BLACK_STAINED_GLASS_PANE",
                java.util.List.of(new MenuBridgeProtocol.MenuItem(44, "lobby-last", "NETHER_STAR", "§aLast lobby", java.util.List.of("§7Online")))
        ));
        assertEquals(6, decoded.rows());
        assertEquals(44, decoded.items().get(0).slot());
        assertEquals("lobby-last", decoded.items().get(0).target());
    }
}
