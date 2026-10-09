/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.common.HalloweenEasterEgg;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class BackendHalloweenEasterEgg {

    private final Plugin plugin;
    private final HalloweenEasterEgg calendar;
    private volatile Object session;
    private Object task;

    public BackendHalloweenEasterEgg(Plugin plugin) {
        this(plugin, new HalloweenEasterEgg());
    }

    public BackendHalloweenEasterEgg(Plugin plugin, HalloweenEasterEgg calendar) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public synchronized void start() {
        stop();
        if (!plugin.isEnabled()) return;
        Object currentSession = new Object();
        session = currentSession;
        long intervalTicks = HalloweenEasterEgg.CHECK_INTERVAL.toSeconds() * 20L;
        task = FoliaSchedulerCompat.runGlobalTaskTimer(plugin, () -> announce(currentSession), intervalTicks, intervalTicks);
        announce(currentSession);
    }

    private void announce(Object currentSession) {
        if (!isActive(currentSession)) return;
        calendar.currentNotice().ifPresent(notice -> {
            if (!isActive(currentSession) || !calendar.isCurrent(notice)) return;
            plugin.getLogger().info("[Halloween] " + notice.message());
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                notifyOperator(player, notice, currentSession);
            }
        });
    }

    public void onPlayerJoin(Player player) {
        Object currentSession = session;
        if (!isActive(currentSession)) return;
        calendar.currentNotice().filter(HalloweenEasterEgg.Notice::halloween)
                .ifPresent(notice -> notifyOperator(player, notice, currentSession));
    }

    private void notifyOperator(Player player, HalloweenEasterEgg.Notice notice, Object currentSession) {
        if (!isActive(currentSession) || player == null || !player.isOnline() || !player.isOp()) return;
        FoliaSchedulerCompat.runTask(plugin, player, () -> {
            if (isActive(currentSession) && player.isOnline() && player.isOp() && calendar.isCurrent(notice)) {
                player.sendMessage("[VelocityNavigator] " + notice.message());
            }
        });
    }

    private boolean isActive(Object currentSession) {
        return currentSession != null && session == currentSession && plugin.isEnabled();
    }

    public synchronized void stop() {
        session = null;
        FoliaSchedulerCompat.cancelTask(plugin, task);
        task = null;
    }
}
