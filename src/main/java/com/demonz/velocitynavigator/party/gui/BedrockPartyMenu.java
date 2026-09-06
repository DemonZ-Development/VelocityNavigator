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
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BedrockPartyMenu {

    private final VelocityNavigator plugin;
    private final PartyMenuConfig menuConfig;

    public BedrockPartyMenu(VelocityNavigator plugin) {
        this.plugin = plugin;
        this.menuConfig = PartyMenuConfig.defaults();
    }

    public void open(Player player) {
        if (player == null || !FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId())) {
            return;
        }

        UUID playerId = player.getUniqueId();
        List<UUID> members = plugin.partyService().members(playerId);
        if (members.isEmpty()) {
            player.sendMessage(MessageFormatter.render("<red>You are not in a party. Use /party invite <player> to start one!", player));
            return;
        }

        boolean isLeader = plugin.partyService().isLeader(playerId);
        Role role = plugin.partyService().getRole(playerId);

        SimpleForm.Builder form = SimpleForm.builder()
                .title(menuConfig.bedrockTitle())
                .content(menuConfig.bedrockContent());

        form.button(menuConfig.memberListLabel());
        if (isLeader) {
            form.button(menuConfig.settingsLabel());
            form.button(menuConfig.renameLabel());
            form.button(menuConfig.warpLabel());
        }
        form.button(menuConfig.leaveLabel());

        form.validResultHandler(response -> {
            int buttonIndex = response.clickedButtonId();
            if (buttonIndex == 0) {
                openMemberList(player, members);
            } else if (isLeader) {
                switch (buttonIndex) {
                    case 1 -> openSettings(player);
                    case 2 -> openRenameInput(player);
                    case 3 -> warpParty(player);
                    case 4 -> leaveOrDisband(player);
                }
            } else {
                leaveOrDisband(player);
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    private void openMemberList(Player player, List<UUID> members) {
        SimpleForm.Builder form = SimpleForm.builder()
                .title("Party Members (" + members.size() + ")")
                .content("Select a member to view options:");

        for (UUID memberId : members) {
            Optional<Player> target = plugin.server().getPlayer(memberId);
            String name = target.map(Player::getUsername).orElse(memberId.toString());
            Role role = plugin.partyService().getRole(memberId);
            String prefix = role != null ? role.icon() + " " : "";
            form.button(prefix + name);
        }

        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < members.size()) {
                UUID selectedId = members.get(index);
                openMemberActions(player, selectedId);
            }
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    private void openMemberActions(Player player, UUID targetId) {
        boolean isLeader = plugin.partyService().isLeader(player.getUniqueId());
        Optional<Player> targetPlayer = plugin.server().getPlayer(targetId);
        String name = targetPlayer.map(Player::getUsername).orElse(targetId.toString());
        Role targetRole = plugin.partyService().getRole(targetId);

        SimpleForm.Builder form = SimpleForm.builder()
                .title("Member: " + name)
                .content("Current Role: " + (targetRole != null ? targetRole.displayName() : "Member"));

        if (isLeader && !player.getUniqueId().equals(targetId)) {
            form.button(menuConfig.promoteOfficerLabel());
            form.button(menuConfig.promoteLeaderLabel());
            form.button(menuConfig.demoteLabel());
            form.button(menuConfig.kickLabel());
        }

        form.validResultHandler(response -> {
            if (!isLeader || player.getUniqueId().equals(targetId)) return;
            int idx = response.clickedButtonId();
            switch (idx) {
                case 0 -> {
                    plugin.partyService().promote(player.getUniqueId(), targetId, Role.OFFICER);
                    player.sendMessage(MessageFormatter.render("<green>Promoted " + name + " to Officer.", player));
                }
                case 1 -> {
                    plugin.partyService().promote(player.getUniqueId(), targetId, Role.LEADER);
                    player.sendMessage(MessageFormatter.render("<green>Transferred leadership to " + name + ".", player));
                }
                case 2 -> {
                    plugin.partyService().demote(player.getUniqueId(), targetId);
                    player.sendMessage(MessageFormatter.render("<yellow>Demoted " + name + " to Member.", player));
                }
                case 3 -> {
                    plugin.partyService().kick(player.getUniqueId(), targetId);
                    player.sendMessage(MessageFormatter.render("<red>Kicked " + name + " from party.", player));
                }
            }
            plugin.syncPartyPlaceholdersForAll();
        });

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form.build());
    }

    private void openSettings(Player player) {
        boolean open = plugin.partyService().isPartyOpen(player.getUniqueId());
        ModalForm form = ModalForm.builder()
                .title("Party Settings")
                .content("Toggle party access:")
                .button1("Make Open (Public)")
                .button2("Make Invite-Only")
                .validResultHandler(response -> {
                    boolean makeOpen = response.clickedFirst();
                    plugin.partyService().setOpen(player.getUniqueId(), makeOpen);
                    plugin.syncPartyPlaceholdersForAll();
                    player.sendMessage(MessageFormatter.render(makeOpen ? "<green>Party is now open!" : "<yellow>Party is now private.", player));
                }).build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void openRenameInput(Player player) {
        CustomForm form = CustomForm.builder()
                .title("Rename Party")
                .input("New Party Name (supports &a color codes):", "e.g. &bThe Champions")
                .validResultHandler(response -> {
                    String name = response.asInput(0);
                    if (name != null && !name.isBlank()) {
                        plugin.partyService().rename(player.getUniqueId(), name);
                        plugin.syncPartyPlaceholdersForAll();
                        player.sendMessage(MessageFormatter.render("<green>Party renamed to: " + PartyService.translateColorCodes(name), player));
                    }
                }).build();

        FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private void warpParty(Player player) {
        player.spoofChatInput("/party warp");
    }

    private void leaveOrDisband(Player player) {
        if (plugin.partyService().isLeader(player.getUniqueId())) {
            player.spoofChatInput("/party disband");
        } else {
            player.spoofChatInput("/party leave");
        }
    }
}
