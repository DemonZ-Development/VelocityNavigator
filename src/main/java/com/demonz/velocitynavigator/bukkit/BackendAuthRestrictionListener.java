/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendAuthRestrictionListener implements Listener {

    private final Set<UUID> restricted = ConcurrentHashMap.newKeySet();
    private boolean darknessEffectEnabled = true;

    public void setDarknessEffectEnabled(boolean darknessEffectEnabled) {
        this.darknessEffectEnabled = darknessEffectEnabled;
    }

    public void setRestricted(Player player, boolean isRestricted) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (isRestricted) {
            restricted.add(uuid);
            if (darknessEffectEnabled) {
                try {
                    org.bukkit.potion.PotionEffectType blindness = org.bukkit.potion.PotionEffectType.BLINDNESS;
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(blindness, 12000, 1, false, false));
                } catch (Throwable ignored) {}
            }
        } else {
            restricted.remove(uuid);
            try {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
            } catch (Throwable ignored) {}
        }
    }

    public boolean isRestricted(UUID uuid) {
        return restricted.contains(uuid);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        restricted.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!restricted.contains(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw());
            locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (restricted.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (restricted.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (restricted.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (restricted.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && restricted.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player damager && restricted.contains(damager.getUniqueId())) {
            event.setCancelled(true);
        } else if (event.getEntity() instanceof Player victim && restricted.contains(victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (restricted.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.AQUA + "Please authenticate with /login <password> or /register <password> [repeat].");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!restricted.contains(event.getPlayer().getUniqueId())) return;
        String message = event.getMessage().toLowerCase(java.util.Locale.ROOT).trim();
        if (!message.startsWith("/login") && !message.startsWith("/register") && !message.startsWith("/l ") && !message.startsWith("/reg ")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Please authenticate before using other commands.");
        }
    }
}
