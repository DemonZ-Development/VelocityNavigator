/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;

public final class PartyChatCommand implements RawCommand {
    private final VelocityNavigator plugin;

    public PartyChatCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) return;
        String permission = plugin.advancedConfig().party().permission();
        if (!"none".equalsIgnoreCase(permission) && !player.hasPermission(permission)) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("messages.permission_denied"), player));
            return;
        }
        if (!plugin.advancedConfig().party().enabled()) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("party.disabled"), player));
            return;
        }
        String raw = invocation.arguments();
        sendPartyMessage(plugin, player, raw == null || raw.isBlank()
                ? new String[0]
                : new String[]{raw.trim()});
    }

    static void sendPartyMessage(VelocityNavigator plugin, Player player, String[] arguments) {
        if (arguments.length == 0) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("party.chat_usage"), player));
            return;
        }
        var members = plugin.partyService().members(player.getUniqueId());
        if (members.isEmpty()) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("party.not_in_party"), player));
            return;
        }
        String message = String.join(" ", arguments);
        for (var id : members) {
            plugin.server().getPlayer(id).ifPresent(member -> member.sendMessage(MessageFormatter.render(plugin.config().language().text("party.chat"), Map.of("player", player.getUsername(), "message", message), member)));
        }
    }
}
