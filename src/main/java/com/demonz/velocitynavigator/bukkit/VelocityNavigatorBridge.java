/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.MenuBridgeProtocol;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class VelocityNavigatorBridge extends JavaPlugin implements PluginMessageListener, Listener {
    private boolean active;
    private boolean inventoryMenuEnabled;
    private boolean handshakeEnabled;
    private boolean refreshEnabled;
    private long handshakeDelayTicks;
    private int maxTitleLength;
    private Material fallbackMaterial;
    private ExecutorService redisExecutor;
    private BackendRedisRegistration.Settings redisSettings;
    private boolean redisUnregisterOnShutdown;
    private boolean redisRegistrationEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("VelocityNavigator backend bridge is disabled in config.yml.");
            return;
        }
        active = true;
        inventoryMenuEnabled = getConfig().getBoolean("inventory_menu_enabled", true);
        handshakeEnabled = getConfig().getBoolean("handshake_enabled", true);
        refreshEnabled = getConfig().getBoolean("refresh_enabled", true);
        handshakeDelayTicks = Math.max(1L, getConfig().getLong("handshake_delay_ticks", 20L));
        maxTitleLength = Math.max(1, Math.min(32, getConfig().getInt("max_title_length", 32)));
        fallbackMaterial = Material.matchMaterial(getConfig().getString("fallback_material", "COMPASS"));
        if (fallbackMaterial == null || !fallbackMaterial.isItem()) fallbackMaterial = Material.COMPASS;
        if (inventoryMenuEnabled) getServer().getMessenger().registerIncomingPluginChannel(this, MenuBridgeProtocol.CHANNEL, this);
        if (handshakeEnabled) getServer().getMessenger().registerOutgoingPluginChannel(this, MenuBridgeProtocol.CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        configureRedisRegistration();
        configureBStats();
        getLogger().info("VelocityNavigator universal JAR is running in BACKEND GUI BRIDGE mode.");
    }

    @Override
    public void onDisable() {
        if (!active) return;
        closeRedisRegistration();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, MenuBridgeProtocol.CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, MenuBridgeProtocol.CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!active || !inventoryMenuEnabled || !MenuBridgeProtocol.CHANNEL.equals(channel)) {
            return;
        }
        final MenuBridgeProtocol.OpenMenu open;
        try {
            open = MenuBridgeProtocol.decodeOpen(message);
        } catch (IOException exception) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> openInventory(player, open));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!active || !inventoryMenuEnabled) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String target = holder.targetsBySlot.get(event.getRawSlot());
        if (target == null || "@disabled".equals(target)) {
            return;
        }
        player.closeInventory();
        sendSelection(player, holder.token, target);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!active || !handshakeEnabled) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                try {
                    event.getPlayer().sendPluginMessage(this, MenuBridgeProtocol.CHANNEL,
                            MenuBridgeProtocol.encodeHello(getDescription().getVersion()));
                } catch (IOException | IllegalArgumentException ignored) {
                }
            }
        }, handshakeDelayTicks);
    }

    private void openInventory(Player player, MenuBridgeProtocol.OpenMenu open) {
        if (!player.isOnline()) {
            return;
        }
        MenuHolder holder = new MenuHolder(open.token(), open.page());
        Inventory inventory = Bukkit.createInventory(holder, open.rows() * 9, safeTitle(open.title()));
        holder.inventory = inventory;
        if (open.fillEmpty()) {
            ItemStack filler = createItem(new MenuBridgeProtocol.MenuItem(
                    0, "@disabled", open.fillerMaterial(), " ", List.of()));
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        for (MenuBridgeProtocol.MenuItem item : open.items()) {
            inventory.setItem(item.slot(), createItem(item));
            holder.targetsBySlot.put(item.slot(), item.target());
        }
        player.openInventory(inventory);
        if (open.refreshSeconds() > 0) {
            Bukkit.getScheduler().runTaskLater(this, () -> refreshIfStillOpen(player, holder), open.refreshSeconds() * 20L);
        }
    }

    private ItemStack createItem(MenuBridgeProtocol.MenuItem item) {
        Material material = Material.matchMaterial(item.material());
        if (material == null || !material.isItem()) {
            material = fallbackMaterial;
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(item.name());
            meta.setLore(item.lore());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String safeTitle(String title) {
        String value = title == null || title.isBlank() ? "Lobby Selector" : title;
        if (value.length() <= maxTitleLength) {
            return value;
        }
        String shortened = value.substring(0, maxTitleLength);
        return shortened.endsWith("§") ? shortened.substring(0, Math.max(0, maxTitleLength - 1)) : shortened;
    }

    private void refreshIfStillOpen(Player player, MenuHolder expected) {
        if (!player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (refreshEnabled && top.getHolder() == expected) {
            sendSelection(player, expected.token, "@refresh:" + expected.page);
        }
    }

    private void sendSelection(Player player, String token, String target) {
        try {
            player.sendPluginMessage(this, MenuBridgeProtocol.CHANNEL,
                    MenuBridgeProtocol.encodeSelection(token, target));
        } catch (IOException | IllegalArgumentException ignored) {
        }
    }

    private void configureBStats() {
        if (!getConfig().getBoolean("bstats_enabled", true)) return;
        int pluginId = getConfig().getInt("bstats_plugin_id", 0);
        if (pluginId <= 0) {
            getLogger().warning("Backend bStats is ready but disabled until a Bukkit/Spigot bStats project ID is set in config.yml. Backend installs are never reported to the Velocity project.");
            return;
        }
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SimplePie("inventory_menu_enabled", () -> Boolean.toString(inventoryMenuEnabled)));
        metrics.addCustomChart(new SimplePie("handshake_enabled", () -> Boolean.toString(handshakeEnabled)));
        metrics.addCustomChart(new SimplePie("refresh_enabled", () -> Boolean.toString(refreshEnabled)));
        metrics.addCustomChart(new SimplePie("redis_registration_enabled", () -> Boolean.toString(redisRegistrationEnabled)));
    }

    private void configureRedisRegistration() {
        if (!getConfig().getBoolean("redis.enabled", false)) return;
        try {
            int advertisedPort = getConfig().getInt("redis.advertised_port", 0);
            if (advertisedPort <= 0) advertisedPort = getServer().getPort();
            redisSettings = new BackendRedisRegistration.Settings(
                    getConfig().getString("redis.host", "127.0.0.1"),
                    getConfig().getInt("redis.port", 6379),
                    getConfig().getString("redis.username", ""),
                    getConfig().getString("redis.password", ""),
                    getConfig().getBoolean("redis.ssl", false),
                    getConfig().getString("redis.channel_prefix", "vn"),
                    getConfig().getInt("redis.connect_timeout_ms", 3000),
                    getConfig().getInt("redis.read_timeout_ms", 10000),
                    getConfig().getString("redis.registration_secret", ""),
                    getConfig().getString("redis.server_name", ""),
                    getConfig().getString("redis.advertised_host", ""),
                    advertisedPort,
                    getConfig().getString("redis.group", "default"),
                    getConfig().getInt("redis.max_players", -1),
                    getConfig().getInt("redis.weight", 1)
            );
            redisUnregisterOnShutdown = getConfig().getBoolean("redis.unregister_on_shutdown", true);
            redisExecutor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "velocitynavigator-backend-redis");
                thread.setDaemon(true);
                return thread;
            });
            redisRegistrationEnabled = true;
            redisExecutor.submit(() -> logRedisResult(BackendRedisRegistration.publish(redisSettings, false)));
        } catch (RuntimeException error) {
            getLogger().warning("Backend Redis registration is disabled: " + error.getMessage());
        }
    }

    private void closeRedisRegistration() {
        if (redisExecutor == null) return;
        if (redisUnregisterOnShutdown && redisSettings != null) {
            try {
                Future<?> future = redisExecutor.submit(() -> logRedisResult(BackendRedisRegistration.publish(redisSettings, true)));
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception error) {
                getLogger().warning("Backend Redis unregister failed: " + error.getMessage());
            }
        }
        redisExecutor.shutdownNow();
        redisExecutor = null;
    }

    private void logRedisResult(BackendRedisRegistration.Result result) {
        if (result.success()) getLogger().info("Backend Redis registration " + result.message() + ".");
        else getLogger().warning("Backend Redis registration failed: " + result.message());
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String token;
        private final int page;
        private final Map<Integer, String> targetsBySlot = new LinkedHashMap<>();
        private Inventory inventory;

        private MenuHolder(String token, int page) {
            this.token = token;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
