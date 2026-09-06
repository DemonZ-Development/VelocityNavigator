/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BackendNPC {

    private final String id;
    private String targetServer;
    private final List<String> hologramLines;

    private Location location;
    private String worldName;
    private String sneakTargetServer;
    private String skinUsername;
    private String handItem;
    private String offhandItem;
    private boolean lookAtPlayer;
    private boolean enabled;
    private boolean glowing;
    private String glowColor = "GOLD";
    private String originFile;

    private transient Entity displayEntity;
    private transient Entity interactionEntity;
    private transient List<Entity> hologramEntities = new ArrayList<>();

    public BackendNPC(String id, Location location, String targetServer, String skinUsername,
                      List<String> hologramLines, boolean lookAtPlayer, boolean enabled) {
        this.id = id;
        this.location = location != null ? location.clone() : null;
        this.worldName = location != null && location.getWorld() != null
                ? location.getWorld().getName()
                : null;
        this.targetServer = targetServer;
        this.skinUsername = skinUsername;
        this.hologramLines = hologramLines != null ? new ArrayList<>(hologramLines) : new ArrayList<>();
        this.lookAtPlayer = lookAtPlayer;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location != null ? location.clone() : null;
    }

    public void setLocation(Location location) {
        if (location != null) {
            this.location = location.clone();
            if (location.getWorld() != null) {
                this.worldName = location.getWorld().getName();
            }
        }
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public String getSneakTargetServer() {
        return sneakTargetServer;
    }

    public void setSneakTargetServer(String sneakTargetServer) {
        this.sneakTargetServer = sneakTargetServer;
    }

    public String getSkinUsername() {
        return skinUsername;
    }

    public void setSkinUsername(String skinUsername) {
        this.skinUsername = skinUsername;
    }

    public String getHandItem() {
        return handItem;
    }

    public void setHandItem(String handItem) {
        this.handItem = handItem;
    }

    public String getOffhandItem() {
        return offhandItem;
    }

    public void setOffhandItem(String offhandItem) {
        this.offhandItem = offhandItem;
    }

    public List<String> getHologramLines() {
        return new ArrayList<>(hologramLines);
    }

    public void setHologramLines(List<String> lines) {
        this.hologramLines.clear();
        if (lines != null) {
            this.hologramLines.addAll(lines);
        }
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public void setGlowing(boolean glowing) {
        this.glowing = glowing;
    }

    public String getGlowColor() {
        return glowColor;
    }

    public void setGlowColor(String glowColor) {
        this.glowColor = glowColor != null && !glowColor.isBlank() ? glowColor.toUpperCase(java.util.Locale.ROOT) : "GOLD";
    }

    public String getOriginFile() {
        return originFile;
    }

    public void setOriginFile(String originFile) {
        this.originFile = originFile;
    }

    public Entity getDisplayEntity() {
        return displayEntity;
    }

    public void setDisplayEntity(Entity displayEntity) {
        this.displayEntity = displayEntity;
    }

    public Entity getInteractionEntity() {
        return interactionEntity;
    }

    public void setInteractionEntity(Entity interactionEntity) {
        this.interactionEntity = interactionEntity;
    }

    public List<Entity> getHologramEntities() {
        return new ArrayList<>(hologramEntities);
    }

    public void setHologramEntities(List<Entity> hologramEntities) {
        this.hologramEntities = hologramEntities != null ? new ArrayList<>(hologramEntities) : new ArrayList<>();
    }

    public UUID getInteractionEntityUuid() {
        return interactionEntity != null ? interactionEntity.getUniqueId() : null;
    }

    public UUID uuidOf() {
        return UUID.nameUUIDFromBytes(("velocitynavigator:npc:" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public boolean isSpawned() {
        return displayEntity != null && displayEntity.isValid();
    }
}
