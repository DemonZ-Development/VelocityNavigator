/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import com.demonz.velocitynavigator.bukkit.menu.BackendMenuCommand;
import com.demonz.velocitynavigator.bukkit.menu.BackendMenuListener;
import com.demonz.velocitynavigator.bukkit.menu.BackendMenuManager;
import com.demonz.velocitynavigator.bukkit.menu.ProxyMenuHolder;
import com.demonz.velocitynavigator.bukkit.npc.BackendNPCCommand;
import com.demonz.velocitynavigator.bukkit.npc.BackendNPCListener;
import com.demonz.velocitynavigator.bukkit.npc.BackendNPCManager;
import com.demonz.velocitynavigator.bukkit.npc.NPCProximityLookTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class VelocityNavigatorBridge extends JavaPlugin implements PluginMessageListener, Listener {

    private static final int BACKEND_BSTATS_ID = 32887;

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
    private BackendUpdateChecker updateChecker;
    private final Map<UUID, MenuBridgeProtocol.PartyState> partyStates = new ConcurrentHashMap<>();
    private final BackendAuthRestrictionListener authRestrictionListener = new BackendAuthRestrictionListener();
    private final BackendAuthSignGuiListener authSignGuiListener = new BackendAuthSignGuiListener(this);

    private BackendMenuManager menuManager;
    private BackendNPCManager npcManager;
    private Object proximityTaskHandle;
    private Object viewerTaskHandle;

    @Override
    public void onEnable() {
        BackendConfigMigrator.migrate(this);
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
        getServer().getMessenger().registerIncomingPluginChannel(this, MenuBridgeProtocol.CHANNEL, this);
        if (handshakeEnabled || getConfig().getBoolean("menus_enabled", true) || getConfig().getBoolean("npcs_enabled", true)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, MenuBridgeProtocol.CHANNEL);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(authRestrictionListener, this);
        getServer().getPluginManager().registerEvents(authSignGuiListener, this);
        configureBackendMenusAndNpcs();
        configureRedisRegistration();
        configureBStats();
        configureUpdateChecker();
        registerPlaceholderAPI();
        if (FoliaSchedulerCompat.isFolia()) {
            getLogger().info("[Folia] Folia detected — using region scheduler for all tasks.");
        }
        getLogger().info("VelocityNavigator universal JAR is running in BACKEND GUI BRIDGE mode.");
    }

    private void registerPlaceholderAPI() {
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new com.demonz.velocitynavigator.bukkit.placeholder.VelocityNavigatorExpansion(this).register();
                getLogger().info("PlaceholderAPI expansion successfully registered!");
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (!active) return;
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (proximityTaskHandle != null) {
            FoliaSchedulerCompat.cancelTask(this, proximityTaskHandle);
            proximityTaskHandle = null;
        }
        if (viewerTaskHandle != null) {
            FoliaSchedulerCompat.cancelTask(this, viewerTaskHandle);
            viewerTaskHandle = null;
        }
        if (npcManager != null) {
            npcManager.shutdown();
        }
        closeRedisRegistration();
        getServer().getMessenger().unregisterIncomingPluginChannel(this, MenuBridgeProtocol.CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, MenuBridgeProtocol.CHANNEL);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!active || !MenuBridgeProtocol.CHANNEL.equals(channel)) {
            return;
        }
        try {
            MenuBridgeProtocol.PacketType type = MenuBridgeProtocol.packetType(message);
            if (type == MenuBridgeProtocol.PacketType.AUTH_STATUS) {
                boolean auth = MenuBridgeProtocol.decodeAuthStatus(message);
                authRestrictionListener.setRestricted(player, !auth);
                if (!auth) {
                    FoliaSchedulerCompat.runTaskLater(this, player, () -> authSignGuiListener.openAuthSign(player, false), 10L);
                }
                return;
            }
            if (type == MenuBridgeProtocol.PacketType.PARTY_STATE) {
                partyStates.put(player.getUniqueId(), MenuBridgeProtocol.decodePartyState(message));
                return;
            }
            if (type != MenuBridgeProtocol.PacketType.OPEN || !inventoryMenuEnabled) {
                return;
            }
            MenuBridgeProtocol.OpenMenu open = MenuBridgeProtocol.decodeOpen(message);
            FoliaSchedulerCompat.runTask(this, player, () -> openInventory(player, open));
        } catch (IOException exception) {
            return;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        partyStates.remove(event.getPlayer().getUniqueId());
        if (npcManager != null && npcManager.isPacketMode()) {
            npcManager.packetRuntime().removeViewer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        resetNpcViewer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        resetNpcViewer(event.getPlayer());
    }

    private void resetNpcViewer(Player player) {
        if (npcManager == null || !npcManager.isPacketMode()) {
            return;
        }
        npcManager.packetRuntime().resetViewer(player);
        FoliaSchedulerCompat.runTaskLater(this, player, () -> npcManager.packetRuntime().tickViewer(player), 2L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (npcManager != null) {
            npcManager.spawnPendingInWorld(event.getWorld());
        }
    }

    public String partyPlaceholder(Player player, String parameter) {
        MenuBridgeProtocol.PartyState state = partyStates.get(player.getUniqueId());
        if (state == null) {
            return switch (parameter) {
                case "in_party", "is_leader", "is_open" -> "false";
                case "name", "leader", "role", "members" -> "None";
                case "size" -> "0";
                case "max_size" -> "20";
                default -> null;
            };
        }
        return switch (parameter) {
            case "in_party" -> Boolean.toString(state.inParty());
            case "name" -> state.name();
            case "leader" -> state.leader();
            case "size" -> Integer.toString(state.size());
            case "max_size" -> Integer.toString(state.maxSize());
            case "is_leader" -> Boolean.toString(state.isLeader());
            case "role" -> state.role();
            case "is_open" -> Boolean.toString(state.isOpen());
            case "members" -> String.join(", ", state.members());
            default -> null;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!active || !inventoryMenuEnabled) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof ProxyMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String target = holder.targetsBySlot().get(event.getRawSlot());
        if (target == null || "@disabled".equals(target)) {
            return;
        }
        player.closeInventory();
        sendSelection(player, holder.token(), target);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!active || !handshakeEnabled) return;
        FoliaSchedulerCompat.runTaskLater(this, event.getPlayer(), () -> {
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
        ProxyMenuHolder holder = new ProxyMenuHolder(open.token(), open.page());
        Inventory inventory = Bukkit.createInventory(holder, open.rows() * 9, safeTitle(open.title()));
        holder.bindInventory(inventory);
        if (open.fillEmpty()) {
            ItemStack filler = createItem(new MenuBridgeProtocol.MenuItem(
                    0, "@disabled", open.fillerMaterial(), " ", List.of()));
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        for (MenuBridgeProtocol.MenuItem item : open.items()) {
            inventory.setItem(item.slot(), createItem(item));
            holder.targetsBySlot().put(item.slot(), item.target());
        }
        player.openInventory(inventory);
        if (open.refreshSeconds() > 0) {
            FoliaSchedulerCompat.runTaskLater(this, player,
                    () -> refreshIfStillOpen(player, holder), open.refreshSeconds() * 20L);
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
        return shortened.endsWith("\u00a7") ? shortened.substring(0, Math.max(0, maxTitleLength - 1)) : shortened;
    }

    private void refreshIfStillOpen(Player player, ProxyMenuHolder expected) {
        if (!player.isOnline()) {
            return;
        }
        Inventory top = player.getOpenInventory().getTopInventory();
        if (refreshEnabled && top.getHolder() == expected) {
            sendSelection(player, expected.token(), "@refresh:" + expected.page());
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
        Metrics metrics = new Metrics(this, BACKEND_BSTATS_ID);
        metrics.addCustomChart(new SimplePie("inventory_menu_enabled", () -> Boolean.toString(inventoryMenuEnabled)));
        metrics.addCustomChart(new SimplePie("handshake_enabled", () -> Boolean.toString(handshakeEnabled)));
        metrics.addCustomChart(new SimplePie("refresh_enabled", () -> Boolean.toString(refreshEnabled)));
        metrics.addCustomChart(new SimplePie("redis_registration_enabled", () -> Boolean.toString(redisRegistrationEnabled)));
        metrics.addCustomChart(new SimplePie("folia_enabled", () -> Boolean.toString(FoliaSchedulerCompat.isFolia())));
        metrics.addCustomChart(new SimplePie("server_software", () -> Bukkit.getName()));
    }

    private void configureBackendMenusAndNpcs() {
        boolean menusEnabled = getConfig().getBoolean("menus_enabled", true);
        boolean npcsEnabled = getConfig().getBoolean("npcs_enabled", true);

        BackendMenuCommand menuCmd = null;
        BackendNPCCommand npcCmd = null;

        if (menusEnabled) {
            menuManager = new BackendMenuManager(this);
            BackendMenuListener menuListener = new BackendMenuListener(this, menuManager);
            getServer().getPluginManager().registerEvents(menuListener, this);
            menuCmd = new BackendMenuCommand(menuManager);
            registerBackendCommand("vnavmenu", menuCmd);
        }

        if (npcsEnabled) {
            npcManager = new BackendNPCManager(this);
            npcManager.loadAndSpawnAll();
            BackendNPCListener npcListener = new BackendNPCListener(this, npcManager, menuManager);
            getServer().getPluginManager().registerEvents(npcListener, this);
            npcCmd = new BackendNPCCommand(npcManager);
            registerBackendCommand("vnavnpc", npcCmd);

            if (npcManager.isPacketMode()) {
                npcManager.packetRuntime().setClickHandler(npcListener::routeClick);
                viewerTaskHandle = FoliaSchedulerCompat.runGlobalTaskTimer(this,
                        () -> npcManager.tickViewers(), 10L, 10L);
            }

            long lookIntervalTicks = Math.max(1L, getConfig().getLong("npc_look_interval_ticks", 20L));
            NPCProximityLookTask lookTask = new NPCProximityLookTask(this, npcManager);
            proximityTaskHandle = FoliaSchedulerCompat.runGlobalTaskTimer(this, lookTask, lookIntervalTicks, lookIntervalTicks);
        }

        BackendVNCommand vnCmd = new BackendVNCommand(this, npcCmd, menuCmd);
        registerBackendCommand("vnav", vnCmd);
    }

    private void registerBackendCommand(String name, CommandExecutor executor) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(executor);
        } else {
            getLogger().warning("[VelocityNavigator] Command /" + name + " is not declared in plugin.yml and was not registered.");
        }
    }

    private void configureUpdateChecker() {
        if (!getConfig().getBoolean("update_check_enabled", true)) return;
        int intervalMinutes = Math.max(30, getConfig().getInt("update_check_interval_minutes", 120));
        updateChecker = new BackendUpdateChecker(getLogger(), getDescription().getVersion());
        updateChecker.schedulePeriodicCheck(intervalMinutes);
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

}
