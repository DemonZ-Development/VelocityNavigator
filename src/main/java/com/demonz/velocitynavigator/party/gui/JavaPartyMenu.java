/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.party.gui;

import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.common.JavaUiMode;
import com.demonz.velocitynavigator.common.UiBridgeProtocol;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.party.PartyService;
import com.demonz.velocitynavigator.party.Role;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (openInteractive(player, 0)) return;
        openChat(player);
    }

    private boolean openInteractive(Player player, int requestedPage) {
        UUID playerId = player.getUniqueId();
        List<UUID> members = plugin.partyService().members(playerId);
        if (members.isEmpty()) return false;
        var mode = plugin.guiConfig().javaUi().party();
        if (mode == JavaUiMode.CHAT) return false;
        boolean leader = plugin.partyService().isLeader(playerId);
        int pages = Math.max(1, (members.size() + 4) / 5);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<UiBridgeProtocol.Button> buttons = new ArrayList<>();
        Map<String, String> commands = new LinkedHashMap<>();
        for (UUID memberId : members.subList(page * 5, Math.min(members.size(), (page + 1) * 5))) {
            String name = plugin.server().getPlayer(memberId).map(Player::getUsername).orElse(null);
            Role role = plugin.partyService().getRole(memberId);
            String roleName = role == null ? "Member" : role.displayName();
            if (name == null) {
                buttons.add(new UiBridgeProtocol.Button("", memberId.toString(), "", "PLAYER_HEAD"));
            } else if (leader && !memberId.equals(playerId)) {
                addAction(player, buttons, commands, "promote " + name + " officer", "party.dialog.promote", name, "GOLD_INGOT");
                addAction(player, buttons, commands, "promote " + name + " leader", "party.dialog.leader", name, "GOLD_BLOCK");
                addAction(player, buttons, commands, "demote " + name, "party.dialog.demote", name, "IRON_INGOT");
                addAction(player, buttons, commands, "kick " + name, "party.dialog.kick", name, "BARRIER");
            } else {
                buttons.add(new UiBridgeProtocol.Button("", name, roleName, "PLAYER_HEAD"));
            }
        }
        if (leader) {
            addAction(player, buttons, commands, "warp", "party.dialog.warp", "", "ENDER_PEARL");
            addAction(player, buttons, commands, plugin.partyService().isPartyOpen(playerId) ? "close" : "open",
                    plugin.partyService().isPartyOpen(playerId) ? "party.dialog.make_private" : "party.dialog.make_open", "", "OAK_DOOR");
            addAction(player, buttons, commands, "disband", "party.dialog.disband", "", "TNT");
        } else addAction(player, buttons, commands, "leave", "party.dialog.leave", "", "OAK_DOOR");
        if (page > 0) buttons.add(new UiBridgeProtocol.Button("@page:" + (page - 1), plugin.uiService().text(player, "menus.inventory.previous"), "", "ARROW"));
        if (page + 1 < pages) buttons.add(new UiBridgeProtocol.Button("@page:" + (page + 1), plugin.uiService().text(player, "menus.inventory.next"), "", "ARROW"));
        buttons.add(new UiBridgeProtocol.Button("@page:" + page, plugin.uiService().text(player, "menus.inventory.refresh"), "", "CLOCK"));
        String leaderName = plugin.partyService().leader(playerId).flatMap(id -> plugin.server().getPlayer(id)).map(Player::getUsername).orElse("Unknown");
        String body = plugin.config().language().text("party.dialog.summary")
                .replace("{leader}", leaderName).replace("{size}", Integer.toString(members.size()))
                .replace("{page}", Integer.toString(page + 1)).replace("{pages}", Integer.toString(pages));
        return plugin.uiService().show(player, plugin.uiService().text(player, "party.dialog.title"),
                plugin.uiService().legacy(player, body), buttons, List.of(), mode, false, false, action -> {
                    if (action.action().startsWith("@page:")) {
                        openInteractive(player, Integer.parseInt(action.action().substring(6)));
                    } else {
                        String command = commands.get(action.action());
                        if (command != null) plugin.server().getCommandManager().executeAsync(player, plugin.advancedConfig().party().command() + " " + command);
                    }
                });
    }

    private void addAction(Player player, List<UiBridgeProtocol.Button> buttons,
                           Map<String, String> commands, String command, String key, String name, String material) {
        String action = "action:" + commands.size();
        commands.put(action, command);
        String label = plugin.config().language().text(key).replace("{player}", name);
        buttons.add(new UiBridgeProtocol.Button(action, plugin.uiService().legacy(player, label), "", material));
    }

    private void openChat(Player player) {
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
