/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class EssentialsHook {

    private static boolean available;
    private static Object essentialsInstance;

    static {
        try {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
            if (plugin != null && plugin.isEnabled()) {
                available = true;
                essentialsInstance = plugin;
            }
        } catch (Throwable ignored) {
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isAfk(Player player) {
        if (!available || player == null) return false;
        try {
            Method getUserMethod = essentialsInstance.getClass().getMethod("getUser", Player.class);
            Object user = getUserMethod.invoke(essentialsInstance, player);
            if (user != null) {
                Method isAfkMethod = user.getClass().getMethod("isAfk");
                return (boolean) isAfkMethod.invoke(user);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isMuted(Player player) {
        if (!available || player == null) return false;
        try {
            Method getUserMethod = essentialsInstance.getClass().getMethod("getUser", Player.class);
            Object user = getUserMethod.invoke(essentialsInstance, player);
            if (user != null) {
                Method isMutedMethod = user.getClass().getMethod("isMuted");
                return (boolean) isMutedMethod.invoke(user);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String getNickname(Player player) {
        if (!available || player == null) return player.getName();
        try {
            Method getUserMethod = essentialsInstance.getClass().getMethod("getUser", Player.class);
            Object user = getUserMethod.invoke(essentialsInstance, player);
            if (user != null) {
                Method getNickMethod = user.getClass().getMethod("getNickname");
                Object nick = getNickMethod.invoke(user);
                if (nick != null && !nick.toString().isBlank()) {
                    return nick.toString();
                }
            }
        } catch (Throwable ignored) {}
        return player.getName();
    }
}
