/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc;

import com.demonz.velocitynavigator.bukkit.menu.BackendMenuManager;
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BackendNPCListener implements Listener {

    private static final long INTERACTION_COOLDOWN_MS = 250L;

    private final Plugin plugin;
    private final BackendNPCManager npcManager;
    private final BackendMenuManager menuManager;
    private final Map<UUID, Map<String, Long>> lastInteraction = new ConcurrentHashMap<>();

    public BackendNPCListener(Plugin plugin, BackendNPCManager npcManager, BackendMenuManager menuManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastInteraction.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        npcManager.refreshNativeProfilesFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            handleClick(player, event.getEntity(), event);
        }
    }

    private void handleClick(Player player, Entity clicked, Cancellable event) {
        BackendNPC npc = npcManager.getNPCByEntityUuid(clicked.getUniqueId()).orElse(null);
        if (npc == null || !npc.isEnabled()) {
            return;
        }
        event.setCancelled(true);

        UUID interactionUuid = npc.getInteractionEntityUuid();
        UUID displayUuid = npc.getDisplayEntity() != null ? npc.getDisplayEntity().getUniqueId() : null;
        if (!clicked.getUniqueId().equals(interactionUuid) && !clicked.getUniqueId().equals(displayUuid)) {
            return;
        }
        routeClick(player, npc.getId());
    }

    public void routeClick(Player player, String npcId) {
        BackendNPC npc = npcManager.getNPC(npcId).orElse(null);
        if (npc == null || !npc.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        Map<String, Long> perNpc = lastInteraction.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Long last = perNpc.get(npcId);
        if (last != null && now - last < INTERACTION_COOLDOWN_MS) {
            return;
        }
        perNpc.put(npcId, now);

        if (!player.hasPermission("velocitynavigator.use")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this server selector NPC.");
            return;
        }

        String target = resolveTarget(player, npc);
        String normalized = target.toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || "none".equals(normalized) || "noaction".equals(normalized)) {
            player.sendMessage(ChatColor.YELLOW + "This NPC does not have a click action yet.");
            return;
        }
        if (normalized.startsWith("menu:") || "menu".equals(normalized)
                || "gui".equals(normalized) || "selector".equals(normalized)) {
            String menuName = normalized.startsWith("menu:") ? target.substring(5).trim() : "main";
            if (menuName.isBlank()) {
                player.sendMessage(ChatColor.RED + "This NPC has an empty menu target.");
                return;
            }
            if (menuManager != null) {
                menuManager.openMenu(player, menuName);
            } else {
                player.sendMessage(ChatColor.RED + "The server selector is not available on this backend.");
            }
            return;
        }
        if (normalized.startsWith("cmd:") || normalized.startsWith("command:")) {
            String cmd = normalized.startsWith("cmd:") ? target.substring(4) : target.substring(8);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (!cmd.isBlank()) {
                player.performCommand(cmd);
            } else {
                player.sendMessage(ChatColor.RED + "This NPC has an empty command target.");
            }
            return;
        }
        if (normalized.startsWith("server:")) {
            target = target.substring("server:".length());
        }
        if (target.isBlank()) {
            player.sendMessage(ChatColor.RED + "This NPC does not have a valid target server.");
            return;
        }
        sendBackendSelection(player, target);
    }

    private String resolveTarget(Player player, BackendNPC npc) {
        String raw = (player.isSneaking() && npc.getSneakTargetServer() != null && !npc.getSneakTargetServer().isBlank())
                ? npc.getSneakTargetServer()
                : npc.getTargetServer();
        if (raw == null) {
            return "lobby";
        }
        if (raw.startsWith("action:cond(") && raw.endsWith(")")) {
            String expr = raw.substring(12, raw.length() - 1);
            int qIndex = expr.indexOf('?');
            int cIndex = expr.indexOf('|');
            if (qIndex > 0 && cIndex > qIndex) {
                String condition = expr.substring(0, qIndex).trim();
                boolean matches = false;
                if (condition.startsWith("perm=")) {
                    matches = player.hasPermission(condition.substring(5).trim());
                } else if ("bedrock".equalsIgnoreCase(condition)) {
                    matches = isBedrockPlayer(player);
                }
                return matches
                        ? expr.substring(qIndex + 1, cIndex).trim()
                        : expr.substring(cIndex + 1).trim();
            }
        }
        return raw;
    }

    private boolean isBedrockPlayer(Player player) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("floodgate")) {
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            return (Boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private void sendBackendSelection(Player player, String targetServer) {
        try {
            player.sendPluginMessage(plugin, MenuBridgeProtocol.CHANNEL,
                    MenuBridgeProtocol.encodeSelection(MenuBridgeProtocol.BACKEND_SELECTION_TOKEN, targetServer));
        } catch (IOException | IllegalArgumentException error) {
            player.sendMessage(ChatColor.RED + "Could not send you to server '" + targetServer + "'. Check the proxy connection.");
            plugin.getLogger().log(Level.WARNING,
                    "[VelocityNavigator] Failed to send backend NPC selection for {}: {}",
                    new Object[]{targetServer, error.getMessage()});
        }
    }
}
