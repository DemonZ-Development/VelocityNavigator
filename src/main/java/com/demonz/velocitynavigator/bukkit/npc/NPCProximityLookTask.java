/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class NPCProximityLookTask implements Runnable {

    private static final double RANGE_SQ = 8.0 * 8.0;
    private static final float MIN_DELTA_DEGREES = 2.0f;

    private final Plugin plugin;
    private final BackendNPCManager npcManager;

    public NPCProximityLookTask(Plugin plugin, BackendNPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @Override
    public void run() {
        for (BackendNPC npc : npcManager.getAllNPCs()) {
            if (!npc.isEnabled()) {
                continue;
            }
            Location loc = npc.getLocation();
            if (loc == null || loc.getWorld() == null) {
                continue;
            }
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            if (!npcManager.isSpawnedNow(npc)) {
                npcManager.spawnNPC(npc);
                continue;
            }
            if (!npc.isLookAtPlayer()) {
                continue;
            }
            FoliaSchedulerCompat.runTask(plugin, loc, () -> updateNpc(npc));
        }
    }

    private void updateNpc(BackendNPC npc) {
        if (!npc.isEnabled() || !npc.isLookAtPlayer() || !npcManager.isSpawnedNow(npc)) {
            return;
        }
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        Player nearest = null;
        double bestSq = RANGE_SQ;
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 8.0, 8.0, 8.0,
                candidate -> candidate instanceof Player)) {
            Player player = (Player) entity;
            double distSq = player.getLocation().distanceSquared(loc);
            if (distSq <= bestSq) {
                bestSq = distSq;
                nearest = player;
            }
        }
        if (nearest == null) {
            return;
        }

        float yaw = yawTowards(loc, nearest.getEyeLocation());
        if (npcManager.isPacketMode()) {
            npcManager.rotateNpcTowards(npc, yaw);
        } else {
            rotateBody(plugin, npc, yaw);
        }
    }

    private void rotateBody(Plugin plugin, BackendNPC npc, float targetYaw) {
        Entity body = npc.getDisplayEntity();
        if (body == null || !body.isValid()) {
            return;
        }
        FoliaSchedulerCompat.runTask(plugin, body, () -> {
            if (!body.isValid()) {
                return;
            }
            Location current = body.getLocation();
            if (Math.abs(targetYaw - current.getYaw()) < MIN_DELTA_DEGREES) {
                return;
            }
            body.setRotation(targetYaw, current.getPitch());
        });
    }

    private float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
