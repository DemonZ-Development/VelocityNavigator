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
            Channel channel = extractChannel(handle);
            if (channel == null) {
                injected.remove(player.getUniqueId());
                LOG.warning("[VelocityNavigator] Could not locate Netty channel for " + player.getName());
                return false;
            }
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
            Channel channel = extractChannel(handle);
            if (channel != null) {
                ChannelDuplexHandler handler = (ChannelDuplexHandler) channel.pipeline().get(HANDLER_NAME);
                if (handler != null) {
                    channel.eventLoop().submit(() -> channel.pipeline().remove(HANDLER_NAME));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Channel extractChannel(Object handle) {
        try {
            Object gameConnection = findFieldFlexible(handle, "connection");
            if (gameConnection == null) return null;
            Object nettyConnection = findFieldFlexible(gameConnection, "connection");
            if (nettyConnection == null) return null;
            return findChannel(nettyConnection);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object findFieldFlexible(Object target, String name) {
        try {
            return findField(target.getClass(), name).get(target);
        } catch (Throwable ignored) {
        }
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && (f.getType().getName().contains("Connection") || f.getType().getName().contains("PacketListener"))) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(target);
                        if (val != null) return val;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
    }

    private static Channel findChannel(Object nettyConnection) {
        try {
            Field f = findField(nettyConnection.getClass(), "channel");
            Object val = f.get(nettyConnection);
            if (val instanceof Channel ch) return ch;
        } catch (Throwable ignored) {
        }
        for (Class<?> c = nettyConnection.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(f.getModifiers()) && Channel.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(nettyConnection);
                        if (val instanceof Channel ch) return ch;
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        return null;
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
