/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.placeholder;

import com.demonz.velocitynavigator.bukkit.VelocityNavigatorBridge;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VelocityNavigatorExpansion extends PlaceholderExpansion {

    private final VelocityNavigatorBridge bridge;

    public VelocityNavigatorExpansion(VelocityNavigatorBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "velocitynavigator";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DemonZ Development";
    }

    @Override
    public @NotNull String getVersion() {
        return bridge.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String lower = params.toLowerCase();

        if (lower.equals("ping")) {
            return String.valueOf(player.getPing());
        }

        if (lower.equals("lobby")) {
            return player.getWorld().getName();
        }

        if (lower.startsWith("party_")) {
            return bridge.partyPlaceholder(player, lower.substring(6));
        }

        if (lower.equals("essentials_afk")) {
            return String.valueOf(com.demonz.velocitynavigator.bukkit.hook.EssentialsHook.isAfk(player));
        }

        if (lower.equals("essentials_muted")) {
            return String.valueOf(com.demonz.velocitynavigator.bukkit.hook.EssentialsHook.isMuted(player));
        }

        if (lower.equals("essentials_nick")) {
            return com.demonz.velocitynavigator.bukkit.hook.EssentialsHook.getNickname(player);
        }

        if (lower.startsWith("server_status_")) {
            return "ONLINE";
        }

        if (lower.startsWith("server_players_")) {
            return String.valueOf(bridge.getServer().getOnlinePlayers().size());
        }

        return null;
    }
}
