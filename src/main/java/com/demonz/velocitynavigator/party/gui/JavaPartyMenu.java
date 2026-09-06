/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.party.gui;

import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.party.PartyService;
import com.demonz.velocitynavigator.party.Role;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JavaPartyMenu {

    private final VelocityNavigator plugin;
    private final PartyMenuConfig config;

    public JavaPartyMenu(VelocityNavigator plugin) {
        this.plugin = plugin;
        this.config = PartyMenuConfig.defaults();
    }

    public void open(Player player) {
        UUID playerId = player.getUniqueId();
        List<UUID> members = plugin.partyService().members(playerId);
        if (members.isEmpty()) {
            player.sendMessage(MessageFormatter.render("<red>You are not in a party. Type <gold>/party invite <player></gold> to start one!", player));
            return;
        }

        boolean isLeader = plugin.partyService().isLeader(playerId);
        Optional<UUID> leaderId = plugin.partyService().leader(playerId);
        String leaderName = leaderId.flatMap(id -> plugin.server().getPlayer(id)).map(Player::getUsername).orElse("Unknown");
        boolean isOpen = plugin.partyService().isPartyOpen(playerId);

        player.sendMessage(Component.empty());
        player.sendMessage(MessageFormatter.render("<gold><bold>===== PARTY MANAGEMENT MENU =====</bold></gold>", player));
        player.sendMessage(MessageFormatter.render("<gray>Status: " + (isOpen ? "<green>OPEN (Public)</green>" : "<yellow>PRIVATE (Invite-Only)</yellow>") + " | Members: <gold>" + members.size() + "</gold>", player));
        player.sendMessage(MessageFormatter.render("<gray>Leader: <gold>" + leaderName + "</gold>", player));
        player.sendMessage(Component.empty());

        player.sendMessage(MessageFormatter.render("<yellow><bold>Members:</bold></yellow>", player));
        for (UUID mId : members) {
            Optional<Player> memberPlayer = plugin.server().getPlayer(mId);
            String name = memberPlayer.map(Player::getUsername).orElse(mId.toString());
            Role role = plugin.partyService().getRole(mId);
            String icon = role != null ? role.icon() : "👤";
            String title = role != null ? role.displayName() : "Member";

            Component memberLine = MessageFormatter.render("  <gray>" + icon + " <white>" + name + "</white> (" + title + ")</gray>", player);

            if (isLeader && !mId.equals(playerId)) {
                Component promoteOfficerBtn = Component.text(" [Promote Officer]")
                        .clickEvent(ClickEvent.runCommand("/party promote officer " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Promote to Officer")));
                Component promoteLeaderBtn = Component.text(" [Transfer Leader]")
                        .clickEvent(ClickEvent.runCommand("/party promote leader " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Transfer Leadership")));
                Component demoteBtn = Component.text(" [Demote]")
                        .clickEvent(ClickEvent.runCommand("/party demote " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Demote to Member")));
                Component kickBtn = Component.text(" [Kick]")
                        .clickEvent(ClickEvent.runCommand("/party kick " + name))
                        .hoverEvent(HoverEvent.showText(Component.text("Kick from Party")));

                memberLine = memberLine.append(promoteOfficerBtn).append(promoteLeaderBtn).append(demoteBtn).append(kickBtn);
            }
            player.sendMessage(memberLine);
        }

        player.sendMessage(Component.empty());
        player.sendMessage(MessageFormatter.render("<yellow><bold>Quick Actions:</bold></yellow>", player));

        Component actions = Component.empty();
        if (isLeader) {
            actions = actions.append(Component.text("[Warp Party] ")
                    .clickEvent(ClickEvent.runCommand("/party warp"))
                    .hoverEvent(HoverEvent.showText(Component.text("Route all members to your server"))));
            actions = actions.append(Component.text(isOpen ? "[Make Private] " : "[Make Open] ")
                    .clickEvent(ClickEvent.runCommand(isOpen ? "/party close" : "/party open"))
                    .hoverEvent(HoverEvent.showText(Component.text("Toggle party access"))));
            actions = actions.append(Component.text("[Disband Party] ")
                    .clickEvent(ClickEvent.runCommand("/party disband"))
                    .hoverEvent(HoverEvent.showText(Component.text("Disband party"))));
        } else {
            actions = actions.append(Component.text("[Leave Party] ")
                    .clickEvent(ClickEvent.runCommand("/party leave"))
                    .hoverEvent(HoverEvent.showText(Component.text("Leave party"))));
        }
        player.sendMessage(actions);
        player.sendMessage(MessageFormatter.render("<gold><bold>=================================</bold></gold>", player));
    }
}
