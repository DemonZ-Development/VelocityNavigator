/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc.packet;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class PacketNpcRuntime {

    public static final int VIEW_DISTANCE_SQ = 48 * 48;
    public static final int INTERACTION_DISTANCE_SQ = 6 * 6;
    private static final long PROFILE_RESPAWN_DELAY_TICKS = 2L;

    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(2_000_000_000);

    private final Plugin plugin;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<Integer, String> entityIdToNpc = new ConcurrentHashMap<>();
    private final NpcClickInterceptor interceptor;
    private volatile BiConsumer<Player, String> clickHandler;

    public PacketNpcRuntime(Plugin plugin) {
        this.plugin = plugin;
        this.interceptor = new NpcClickInterceptor(plugin,
                (player, entityId) -> {
                    String npcId = entityIdToNpc.get(entityId);
                    State state = npcId != null ? states.get(npcId) : null;
                    return state != null && state.entityId == entityId
                            && state.viewers.contains(player.getUniqueId());
                },
                (player, entityId) -> {
                    String npcId = entityIdToNpc.get(entityId);
                    State state = npcId != null ? states.get(npcId) : null;
                    if (state != null && state.entityId == entityId
                            && state.viewers.contains(player.getUniqueId())
                            && inRange(player, state.location, INTERACTION_DISTANCE_SQ)
                            && clickHandler != null) {
                        clickHandler.accept(player, npcId);
                    }
                });
    }

    public void setClickHandler(BiConsumer<Player, String> handler) {
        this.clickHandler = handler;
    }

    public NpcClickInterceptor interceptor() {
        return interceptor;
    }

    public boolean isTracked(String npcId) {
        return states.containsKey(npcId);
    }

    public int viewerCount(String npcId) {
        State state = states.get(npcId);
        return state == null ? 0 : state.viewers.size();
    }

    public String scoreboardEntry(String npcId) {
        return profileNameFor(npcId);
    }

    public void register(com.demonz.velocitynavigator.bukkit.npc.BackendNPC npc, ItemStack mainHand, ItemStack offHand) {
        deregister(npc.getId());
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        State state = new State(
                NEXT_ENTITY_ID.getAndDecrement(),
                UUID.nameUUIDFromBytes(("velocitynavigator:npc:" + npc.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                profileNameFor(npc.getId()),
                loc,
                mainHand,
                offHand);
        state.glowing = npc.isGlowing();
        states.put(npc.getId(), state);
        entityIdToNpc.put(state.entityId, npc.getId());

    }

    public void refreshProfile(String npcId, String textureValue, String textureSignature) {
        State state = states.get(npcId);
        if (state == null) {
            return;
        }
        try {
            state.profile = NpcPackets.createGameProfile(state.uuid, state.profileName, textureValue, textureSignature);
        } catch (Exception e) {
            plugin.getLogger().warning("[VelocityNavigator] Could not apply the skin profile for NPC '"
                    + npcId + "': " + e.getMessage());
            return;
        }
        for (UUID viewerId : state.viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null) {
                continue;
            }
            FoliaSchedulerCompat.runTask(plugin, viewer, () -> {
                if (states.get(npcId) != state) {
                    return;
                }
                if (state.viewers.remove(viewerId)) {
                    state.refreshingViewers.add(viewerId);
                    sendDestroy(state, List.of(viewer));
                }
                FoliaSchedulerCompat.runTaskLater(plugin, viewer, () -> {
                    state.refreshingViewers.remove(viewerId);
                    if (viewer.isOnline() && states.get(npcId) == state
                            && inRange(viewer, state.location, VIEW_DISTANCE_SQ)) {
                        show(state, viewer);
                    }
                }, PROFILE_RESPAWN_DELAY_TICKS);
            });
        }
    }

    public void deregister(String npcId) {
        State state = states.remove(npcId);
        if (state == null) {
            return;
        }
        entityIdToNpc.remove(state.entityId);
        List<UUID> viewers = List.copyOf(state.viewers);
        state.viewers.clear();
        state.refreshingViewers.clear();
        for (UUID viewerId : viewers) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null && viewer.isOnline()) {
                FoliaSchedulerCompat.runTask(plugin, viewer, () -> sendDestroy(state, List.of(viewer)));
            }
        }
    }

    public void deregisterAll() {
        for (String npcId : List.copyOf(states.keySet())) {
            deregister(npcId);
        }
        interceptor.removeAll();
    }

    public void shutdown() {
        states.clear();
        entityIdToNpc.clear();
        interceptor.removeAll();
    }

    public void removeViewer(Player player) {
        resetViewer(player);
        interceptor.remove(player);
    }

    public void resetViewer(Player player) {
        UUID playerId = player.getUniqueId();
        for (State state : states.values()) {
            state.viewers.remove(playerId);
            state.refreshingViewers.remove(playerId);
        }
    }

    public void tickViewer(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        for (Map.Entry<String, State> entry : states.entrySet()) {
            State state = entry.getValue();
            boolean visible = state.viewers.contains(viewerId);
            boolean shouldSee = viewer.isOnline() && inRange(viewer, state.location, VIEW_DISTANCE_SQ);
            if (shouldSee && !visible && !state.refreshingViewers.contains(viewerId)
                    && states.get(entry.getKey()) == state) {
                show(state, viewer);
            } else if (!shouldSee && visible && state.viewers.remove(viewerId)) {
                sendDestroy(state, List.of(viewer));
            }
        }
    }

    public void rotateTowards(String npcId, float headYawDegrees) {
        State state = states.get(npcId);
        if (state == null) {
            return;
        }
        try {
            Location rotated = state.location.clone();
            rotated.setYaw(headYawDegrees);
            state.location = rotated;
            Object bodyPacket = NpcPackets.buildRotateEntity(state.entityId, headYawDegrees, rotated.getPitch());
            Object headPacket = NpcPackets.buildRotateHead(state.entityId, headYawDegrees);
            for (UUID viewerId : state.viewers) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null) {
                    FoliaSchedulerCompat.runTask(plugin, viewer, () -> {
                        if (states.get(npcId) == state && state.viewers.contains(viewerId)) {
                            NpcPackets.send(viewer, bodyPacket);
                            NpcPackets.send(viewer, headPacket);
                        }
                    });
                }
            }
        } catch (Exception e) {
            warnOnce(state, "rotation", "Could not rotate NPC '" + npcId + "': " + e.getMessage());
        }
    }

    private void show(State state, Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }
        boolean profileSent = false;
        try {
            Object profile = state.profile != null
                    ? state.profile
                    : NpcPackets.createGameProfile(state.uuid, state.profileName, null, null);
            boolean clicksReady = interceptor.inject(viewer);
            profileSent = NpcPackets.send(viewer, NpcPackets.buildPlayerInfoAdd(state.uuid, profile));
            boolean entitySent = profileSent
                    && NpcPackets.send(viewer, NpcPackets.buildAddEntity(state.entityId, state.uuid, toLoc(state.location)));
            if (!entitySent) {
                throw new IllegalStateException("the player profile or entity spawn packet was rejected");
            }
            Object cosmetics = NpcPackets.buildCosmetics(state.entityId, state.glowing);
            if (cosmetics != null) {
                if (!NpcPackets.send(viewer, cosmetics)) {
                    warnOnce(state, viewer.getUniqueId() + ":cosmetics",
                            "NPC '" + state.profileName + "' spawned, but its glow/skin-layer metadata failed for " + viewer.getName() + ".");
                }
            } else {
                warnOnce(state, viewer.getUniqueId() + ":cosmetics",
                        "NPC '" + state.profileName + "' spawned, but glow/skin-layer metadata is unavailable on this server build.");
            }
            Object equipment = NpcPackets.buildEquipment(state.entityId, state.mainHand, state.offHand);
            if (equipment != null) {
                if (!NpcPackets.send(viewer, equipment)) {
                    warnOnce(state, viewer.getUniqueId() + ":equipment",
                            "NPC '" + state.profileName + "' spawned, but its equipment packet failed for " + viewer.getName() + ".");
                }
            } else if (hasEquipment(state)) {
                warnOnce(state, viewer.getUniqueId() + ":equipment",
                        "NPC '" + state.profileName + "' spawned, but its configured equipment could not be converted.");
            }
            if (!clicksReady) {
                warnOnce(state, viewer.getUniqueId() + ":clicks",
                        "NPC '" + state.profileName + "' is visible to " + viewer.getName() + ", but click interception is unavailable.");
            }
            state.viewers.add(viewer.getUniqueId());
            state.warnings.remove(viewer.getUniqueId() + ":render");
        } catch (Throwable t) {
            if (profileSent) {
                try {
                    NpcPackets.send(viewer, NpcPackets.buildPlayerInfoRemove(List.of(state.uuid)));
                } catch (Exception ignored) {
                }
            }
            warnOnce(state, viewer.getUniqueId() + ":render", "Failed to render NPC '"
                    + state.profileName + "' for " + viewer.getName() + ": " + t.getMessage());
        }
    }

    private static boolean hasEquipment(State state) {
        return state.mainHand != null && !state.mainHand.getType().isAir()
                || state.offHand != null && !state.offHand.getType().isAir();
    }

    private void warnOnce(State state, String key, String message) {
        if (state.warnings.add(key)) {
            plugin.getLogger().warning("[VelocityNavigator] " + message);
        }
    }

    private void sendDestroy(State state, List<Player> viewers) {
        try {
            Object packet = NpcPackets.buildRemoveEntities(List.of(state.entityId));
            Object infoPacket = NpcPackets.buildPlayerInfoRemove(List.of(state.uuid));
            for (Player viewer : viewers) {
                NpcPackets.send(viewer, packet);
                NpcPackets.send(viewer, infoPacket);
            }
        } catch (Exception e) {
            warnOnce(state, "destroy", "Failed to remove NPC '" + state.profileName
                    + "' from one or more viewers: " + e.getMessage());
        }
    }

    private static boolean inRange(Player player, Location loc, int maxDistanceSq) {
        Location pLoc = player.getLocation();
        if (pLoc.getWorld() == null || loc.getWorld() == null
                || !pLoc.getWorld().getName().equals(loc.getWorld().getName())) {
            return false;
        }
        double dx = pLoc.getX() - loc.getX();
        double dy = pLoc.getY() - loc.getY();
        double dz = pLoc.getZ() - loc.getZ();
        return dx * dx + dy * dy + dz * dz <= maxDistanceSq;
    }

    private static NpcPackets.WorldLocation toLoc(Location loc) {
        return new NpcPackets.WorldLocation(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    static String profileNameFor(String npcId) {
        String cleaned = npcId.replaceAll("[^A-Za-z0-9_]", "");
        String base = cleaned.isEmpty() ? "npc" : cleaned;
        String hash = UUID.nameUUIDFromBytes(npcId.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "").substring(0, 6);
        return base.substring(0, Math.min(9, base.length())) + "_" + hash;
    }

    private static final class State {
        final int entityId;
        final UUID uuid;
        final String profileName;
        volatile Location location;
        volatile Object profile;
        final ItemStack mainHand;
        final ItemStack offHand;
        boolean glowing;
        final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        final Set<UUID> refreshingViewers = ConcurrentHashMap.newKeySet();
        final Set<String> warnings = ConcurrentHashMap.newKeySet();

        State(int entityId, UUID uuid, String profileName, Location location, ItemStack mainHand, ItemStack offHand) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.profileName = profileName;
            this.location = location.clone();
            this.mainHand = mainHand;
            this.offHand = offHand;
        }
    }
}
