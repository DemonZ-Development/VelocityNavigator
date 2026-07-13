/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.List;
import java.util.Map;

public final class QueueCommand implements RawCommand {
    private final VelocityNavigator plugin;

    public QueueCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;
        String permission = plugin.advancedConfig().queue().permission();
        if (!"none".equalsIgnoreCase(permission) && !player.hasPermission(permission)) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("messages.permission_denied"), player));
            return;
        }
        String[] arguments = splitArguments(invocation.arguments());
        if (arguments.length > 0 && "leave".equalsIgnoreCase(arguments[0])) {
            boolean removed = plugin.queueService().remove(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(plugin.config().language().text(removed ? "queue.left" : "queue.not_queued"), player));
            return;
        }
        int position = plugin.queueService().position(player.getUniqueId());
        if (position == 0) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("queue.not_queued"), player));
            return;
        }
        player.sendMessage(MessageFormatter.render(plugin.config().language().text("queue.position"), Map.of(
                "position", String.valueOf(position),
                "size", String.valueOf(plugin.queueService().size())), player));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] arguments = splitArguments(invocation.arguments());
        return arguments.length <= 1 ? List.of("leave") : List.of();
    }

    private static String[] splitArguments(String raw) {
        return raw == null || raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
    }
}
