/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.common.MenuBridgeProtocol;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendAuthSignGuiListener implements Listener {

    private record SignState(boolean isRegister, String firstPassword, Location signLoc) {}

    private final Plugin plugin;
    private final Map<UUID, SignState> activeSessions = new ConcurrentHashMap<>();

    public BackendAuthSignGuiListener(Plugin plugin) {
        this.plugin = plugin;
    }

    public void openAuthSign(Player player, boolean isRegister) {
        openAuthSign(player, isRegister, null);
    }

    public void openAuthSign(Player player, boolean isRegister, String firstPassword) {
        if (player == null || !player.isOnline()) return;
        Location signLoc = player.getLocation().clone().add(0, 3, 0);

        try {
            player.sendBlockChange(signLoc, Material.OAK_SIGN.createBlockData());
            activeSessions.put(player.getUniqueId(), new SignState(isRegister, firstPassword, signLoc));

            FoliaSchedulerCompat.runTaskLater(
                    Bukkit.getPluginManager().getPlugin("VelocityNavigator"),
                    player,
                    () -> {
                        if (!player.isOnline()) return;
                        try {
                            Block block = signLoc.getBlock();
                            if (block.getState() instanceof Sign sign) {
                                sign.setLine(0, "^^^^^^^^^^^^^^^");
                                sign.setLine(1, isRegister ? (firstPassword == null ? "Type Password" : "Repeat Password") : "Type Password");
                                sign.setLine(2, "VelocityNav Auth");
                                sign.update(true, false);
                                openSign(player, sign);
                            }
                        } catch (Throwable ignored) {}
                    },
                    2L
            );
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void openSign(Player player, Sign sign) {
        try {
            try {
                java.lang.reflect.Method m = Player.class.getMethod("openSign", Sign.class);
                m.invoke(player, sign);
                return;
            } catch (NoSuchMethodException ignored) {}

            try {
                Class<?> sideEnum = Class.forName("org.bukkit.block.sign.Side");
                Object frontSide = Enum.valueOf((Class<Enum>) sideEnum, "FRONT");
                java.lang.reflect.Method m = Player.class.getMethod("openSign", Sign.class, sideEnum);
                m.invoke(player, sign, frontSide);
                return;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        SignState state = activeSessions.remove(player.getUniqueId());
        if (state == null) return;

        event.setCancelled(true);

        try {
            player.sendBlockChange(state.signLoc(), state.signLoc().getBlock().getBlockData());
        } catch (Throwable ignored) {}

        String entered = null;
        for (String line : event.getLines()) {
            if (line != null && !line.isBlank() && !line.contains("^^^^^") && !line.contains("Password") && !line.contains("Auth")) {
                entered = line.trim();
                break;
            }
        }

        if (entered == null || entered.isBlank()) {
            player.sendMessage(ChatColor.RED + "Password cannot be empty. Please try again.");
            openAuthSign(player, state.isRegister(), state.firstPassword());
            return;
        }

        if (state.isRegister()) {
            if (state.firstPassword() == null) {
                player.sendMessage(ChatColor.YELLOW + "Please repeat your password on the sign to confirm.");
                openAuthSign(player, true, entered);
            } else {
                if (state.firstPassword().equals(entered)) {
                    sendAuthRequest(player, true, entered, entered);
                } else {
                    player.sendMessage(ChatColor.RED + "Passwords did not match! Please try registering again.");
                    openAuthSign(player, true, null);
                }
            }
        } else {
            sendAuthRequest(player, false, entered, null);
        }
    }

    private void sendAuthRequest(Player player, boolean register, String password, String repeatPassword) {
        try {
            player.sendPluginMessage(plugin, MenuBridgeProtocol.CHANNEL,
                    MenuBridgeProtocol.encodeAuthRequest(register, password, repeatPassword));
        } catch (IOException | IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Authentication failed. Please try again.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeSessions.remove(event.getPlayer().getUniqueId());
    }
}
