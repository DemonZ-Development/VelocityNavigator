/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc.packet;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NpcClickInterceptor {

    private static final Logger LOG = Logger.getLogger("VelocityNavigator");
    private static final String HANDLER_NAME = "velocitynavigator_npc_clicks";

    public interface ClickResolver {
        boolean canInteract(Player player, int entityId);
    }

    private final Plugin plugin;
    private final ClickResolver resolver;
    private final BiConsumer<Player, Integer> clickHandler;
    private final Set<UUID> injected = ConcurrentHashMap.newKeySet();
    private volatile boolean decodeFailureLogged;

    public NpcClickInterceptor(Plugin plugin, ClickResolver resolver, BiConsumer<Player, Integer> clickHandler) {
        this.plugin = plugin;
        this.resolver = resolver;
        this.clickHandler = clickHandler;
    }

    public boolean inject(Player player) {
        if (!injected.add(player.getUniqueId())) {
            return true;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object gameConnection = findField(handle.getClass(), "connection").get(handle);
            Object nettyConnection = findField(gameConnection.getClass(), "connection").get(gameConnection);
            Channel channel = (Channel) findField(nettyConnection.getClass(), "channel").get(nettyConnection);
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                return true;
            }
            channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new InterceptHandler(player));
            return true;
        } catch (Throwable t) {
            injected.remove(player.getUniqueId());
            LOG.log(Level.WARNING, "[VelocityNavigator] Could not attach NPC click handler for " + player.getName(), t);
            return false;
        }
    }

    public void remove(Player player) {
        if (!injected.remove(player.getUniqueId())) {
            return;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object gameConnection = findField(handle.getClass(), "connection").get(handle);
            Object nettyConnection = findField(gameConnection.getClass(), "connection").get(gameConnection);
            Channel channel = (Channel) findField(nettyConnection.getClass(), "channel").get(nettyConnection);
            ChannelDuplexHandler handler = (ChannelDuplexHandler) channel.pipeline().get(HANDLER_NAME);
            if (handler != null) {
                channel.eventLoop().submit(() -> channel.pipeline().remove(HANDLER_NAME));
            }
        } catch (Throwable ignored) {
        }
    }

    public void removeAll() {
        for (UUID uuid : new HashSet<>(injected)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                remove(player);
            }
        }
        injected.clear();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private final class InterceptHandler extends ChannelDuplexHandler {

        private final Player player;

        InterceptHandler(Player player) {
            this.player = player;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!handleInteract(msg)) {
                super.channelRead(ctx, msg);
            }
        }

        private boolean handleInteract(Object msg) {
            if (msg == null || !msg.getClass().getName().endsWith("ServerboundInteractPacket")) {
                return false;
            }
            int entityId = NpcPackets.getIntField(msg, "entityId");
            if (entityId < 0) {
                if (!decodeFailureLogged) {
                    decodeFailureLogged = true;
                    LOG.warning("[VelocityNavigator] NPC clicks cannot be decoded on this server build: entityId field not found.");
                }
                return false;
            }
            if (entityId == 0 || !resolver.canInteract(player, entityId)) {
                return false;
            }
            FoliaSchedulerCompat.runTask(plugin, player, () -> clickHandler.accept(player, entityId));
            return true;
        }
    }
}
