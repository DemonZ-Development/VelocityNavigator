/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator.commands;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.demonz.velocitynavigator.party.PartyService;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Arrays;
import java.util.UUID;

public final class PartyCommand implements RawCommand {
    private final VelocityNavigator plugin;

    public PartyCommand(VelocityNavigator plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MessageFormatter.render(plugin.config().messages().playerOnly()));
            return;
        }
        if (plugin.isAuthenticationPending(player)) {
            player.sendMessage(MessageFormatter.render(
                    "<yellow>Please authenticate before using parties.</yellow>", player));
            return;
        }
        String permission = plugin.advancedConfig().party().permission();
        if (!"none".equalsIgnoreCase(permission) && !player.hasPermission(permission)) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("messages.permission_denied"), player));
            return;
        }
        if (!plugin.advancedConfig().party().enabled()) {
            send(player, "party.disabled", Map.of());
            return;
        }
        String[] args = splitArguments(invocation.arguments());
        if (args.length == 0) {
            showHelp(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> showHelp(player);
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "kick" -> kick(player, args);
            case "leave" -> result(player, plugin.partyService().leave(player.getUniqueId()));
            case "disband" -> disband(player);
            case "rename" -> rename(player, args);
            case "open" -> toggleOpen(player, true);
            case "close" -> toggleOpen(player, false);
            case "promote" -> promote(player, args);
            case "demote" -> demote(player, args);
            case "warp" -> warp(player);
            case "join" -> joinOpen(player, args);
            case "menu", "gui" -> openMenu(player);
            case "status", "list" -> showStatus(player);
            case "chat" -> PartyChatCommand.sendPartyMessage(plugin, player,
                    Arrays.copyOfRange(args, 1, args.length));
            default -> send(player, "party.usage", Map.of());
        }
        plugin.syncPartyPlaceholdersForAll();
    }

    private void showHelp(Player player) {
        player.sendMessage(MessageFormatter.render("""
                <aqua><bold>=== VelocityNavigator Party Commands ===</bold></aqua>
                <aqua>/party create [name...]</aqua> <gray>- Create a party</gray>
                <aqua>/party invite <player></aqua> <gray>- Invite a player</gray>
                <aqua>/party accept|deny</aqua> <gray>- Answer an invitation</gray>
                <aqua>/party status</aqua> <gray>- Show party details</gray>
                <aqua>/party chat <message></aqua> <gray>- Send party chat</gray>
                <aqua>/party open|close</aqua> <gray>- Change join access</gray>
                <aqua>/party join <leader></aqua> <gray>- Join an open party</gray>
                <aqua>/party rename <name...></aqua> <gray>- Rename your party</gray>
                <aqua>/party promote|demote|kick <player></aqua> <gray>- Manage members</gray>
                <aqua>/party warp</aqua> <gray>- Bring members to your server</gray>
                <aqua>/party menu</aqua> <gray>- Open the party interface</gray>
                <aqua>/party leave|disband</aqua> <gray>- Leave or remove the party</gray>
                """, player));
    }

    private void create(Player player, String[] args) {
        String name = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "";
        PartyService.Result created = plugin.partyService().create(player.getUniqueId(), name);
        if (created == PartyService.Result.OK) {
            String displayName = name.isBlank() ? player.getUsername() + "'s Party" : PartyService.translateColorCodes(name);
            player.sendMessage(MessageFormatter.render("<green>Created party <aqua>" + displayName + "</aqua>.</green>", player));
        } else {
            result(player, created);
        }
    }

    private void openMenu(Player player) {
        if (com.demonz.velocitynavigator.FloodgateIntegration.isBedrockPlayer(player)) {
            try {
                new com.demonz.velocitynavigator.party.gui.BedrockPartyMenu(plugin).open(player);
                return;
            } catch (Throwable t) {
                plugin.logger().warn("[VelocityNavigator] Failed to open Bedrock party menu for {}: {}", player.getUsername(), t.getMessage());
            }
        }
        new com.demonz.velocitynavigator.party.gui.JavaPartyMenu(plugin).open(player);
    }

    private void demote(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        Optional<Player> target = plugin.server().getPlayer(args[1]);
        if (target.isEmpty()) {
            send(player, "party.player_not_found", Map.of("target", args[1]));
            return;
        }
        PartyService.Result res = plugin.partyService().demote(player.getUniqueId(), target.get().getUniqueId());
        if (res == PartyService.Result.OK) {
            player.sendMessage(MessageFormatter.render("<yellow>Demoted <gold>" + target.get().getUsername() + "</gold> to Member.", player));
            target.get().sendMessage(MessageFormatter.render("<yellow>You were demoted to Member.", target.get()));
        } else {
            result(player, res);
        }
    }

    private void rename(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        PartyService.Result res = plugin.partyService().rename(player.getUniqueId(), newName);
        if (res == PartyService.Result.OK) {
            player.sendMessage(MessageFormatter.render("<green>Party renamed to: " + PartyService.translateColorCodes(newName), player));
        } else {
            result(player, res);
        }
    }

    private void toggleOpen(Player player, boolean open) {
        PartyService.Result res = plugin.partyService().setOpen(player.getUniqueId(), open);
        if (res == PartyService.Result.OK) {
            player.sendMessage(MessageFormatter.render(open ? "<green>Party is now open to join!" : "<yellow>Party is now invite-only.", player));
        } else {
            result(player, res);
        }
    }

    private void promote(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        com.demonz.velocitynavigator.party.Role targetRole = com.demonz.velocitynavigator.party.Role.OFFICER;
        String targetName = args[1];
        if (args.length >= 3) {
            if ("leader".equalsIgnoreCase(args[1]) || "officer".equalsIgnoreCase(args[1])) {
                targetRole = "leader".equalsIgnoreCase(args[1]) ? com.demonz.velocitynavigator.party.Role.LEADER : com.demonz.velocitynavigator.party.Role.OFFICER;
                targetName = args[2];
            } else if ("leader".equalsIgnoreCase(args[2]) || "officer".equalsIgnoreCase(args[2])) {
                targetRole = "leader".equalsIgnoreCase(args[2]) ? com.demonz.velocitynavigator.party.Role.LEADER : com.demonz.velocitynavigator.party.Role.OFFICER;
                targetName = args[1];
            }
        }
        Optional<Player> target = plugin.server().getPlayer(targetName);
        if (target.isEmpty()) {
            send(player, "party.player_not_found", Map.of("target", targetName));
            return;
        }
        PartyService.Result res = plugin.partyService().promote(player.getUniqueId(), target.get().getUniqueId(), targetRole);
        if (res == PartyService.Result.OK) {
            player.sendMessage(MessageFormatter.render("<green>Promoted <gold>" + target.get().getUsername() + "</gold> to " + targetRole.displayName() + ".", player));
            target.get().sendMessage(MessageFormatter.render("<green>You were promoted to " + targetRole.displayName() + "!", target.get()));
        } else {
            result(player, res);
        }
    }

    private void warp(Player player) {
        if (!plugin.partyService().isLeader(player.getUniqueId())) {
            result(player, PartyService.Result.NOT_LEADER);
            return;
        }
        Optional<com.velocitypowered.api.proxy.ServerConnection> conn = player.getCurrentServer();
        if (conn.isEmpty()) return;
        com.velocitypowered.api.proxy.server.RegisteredServer targetServer = conn.get().getServer();
        List<UUID> followers = plugin.partyService().followers(player.getUniqueId());
        int count = 0;
        for (UUID memberId : List.copyOf(followers)) {
            Optional<Player> member = plugin.server().getPlayer(memberId);
            if (member.isPresent() && member.get().isActive()) {
                member.get().createConnectionRequest(targetServer).fireAndForget();
                count++;
            }
        }
        player.sendMessage(MessageFormatter.render("<green>Warped <gold>" + count + "</gold> party members to your server.", player));
    }

    private void joinOpen(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        Optional<Player> targetLeader = plugin.server().getPlayer(args[1]);
        if (targetLeader.isEmpty()) {
            send(player, "party.player_not_found", Map.of("target", args[1]));
            return;
        }
        PartyService.AcceptResult res = plugin.partyService().joinOpen(player.getUniqueId(), targetLeader.get().getUniqueId());
        if (res.result() == PartyService.Result.OK) {
            send(player, "party.joined", Map.of());
            targetLeader.get().sendMessage(MessageFormatter.render("<green>" + player.getUsername() + " joined your party!", targetLeader.get()));
        } else if (res.result() == PartyService.Result.NOT_OPEN) {
            player.sendMessage(MessageFormatter.render("<red>This party is private. Ask for an invite!", player));
        } else {
            result(player, res.result());
        }
    }

    private void invite(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        Optional<Player> target = plugin.server().getPlayer(args[1]);
        if (target.isEmpty()) {
            send(player, "party.player_not_found", Map.of("target", args[1]));
            return;
        }
        PartyService.Result result = plugin.partyService().invite(player.getUniqueId(), target.get().getUniqueId());
        if (result != PartyService.Result.OK) {
            result(player, result);
            return;
        }
        send(player, "party.invite_sent", Map.of("target", target.get().getUsername()));
        send(target.get(), "party.invite_received", Map.of("player", player.getUsername()));
    }

    private void accept(Player player) {
        PartyService.AcceptResult accepted = plugin.partyService().acceptDetailed(player.getUniqueId());
        if (accepted.result() == PartyService.Result.PARTY_FULL) {
            send(player, "party.full", Map.of());
            return;
        }
        if (accepted.result() != PartyService.Result.OK) {
            send(player, "party.no_invite", Map.of());
            return;
        }
        send(player, "party.joined", Map.of());
        plugin.server().getPlayer(accepted.leader()).ifPresent(leader -> send(leader, "party.member_joined", Map.of("player", player.getUsername())));
    }

    private void deny(Player player) {
        Optional<UUID> inviter = plugin.partyService().deny(player.getUniqueId());
        if (inviter.isEmpty()) {
            send(player, "party.no_invite", Map.of());
            return;
        }
        send(player, "party.invite_denied", Map.of());
    }

    private void kick(Player player, String[] args) {
        if (args.length < 2) {
            send(player, "party.usage", Map.of());
            return;
        }
        Optional<Player> target = plugin.server().getPlayer(args[1]);
        if (target.isEmpty()) {
            send(player, "party.player_not_found", Map.of("target", args[1]));
            return;
        }
        PartyService.Result result = plugin.partyService().kick(player.getUniqueId(), target.get().getUniqueId());
        if (result == PartyService.Result.OK) {
            send(player, "party.kicked", Map.of("target", target.get().getUsername()));
            send(target.get(), "party.you_were_kicked", Map.of());
        } else {
            result(player, result);
        }
    }

    private void disband(Player player) {
        List<UUID> members = plugin.partyService().disband(player.getUniqueId());
        if (members.isEmpty()) {
            result(player, PartyService.Result.NOT_LEADER);
            return;
        }
        for (UUID id : members) {
            plugin.server().getPlayer(id).ifPresent(member -> send(member, "party.disbanded", Map.of()));
        }
    }

    private void showStatus(Player player) {
        List<UUID> members = plugin.partyService().members(player.getUniqueId());
        if (members.isEmpty()) {
            send(player, "party.not_in_party", Map.of());
            return;
        }
        Optional<UUID> leaderId = plugin.partyService().leader(player.getUniqueId());
        String leaderName = leaderId.flatMap(id -> plugin.server().getPlayer(id)).map(Player::getUsername).orElse("Unknown");
        String partyName = plugin.partyService().partyName(player.getUniqueId(), leaderName);
        boolean open = plugin.partyService().isPartyOpen(player.getUniqueId());
        String names = members.stream().map(id -> plugin.server().getPlayer(id).map(Player::getUsername).orElse(id.toString())).reduce((a, b) -> a + ", " + b).orElse("");
        player.sendMessage(MessageFormatter.render("<aqua>--- Party: " + partyName + " <aqua>---</aqua>\n" +
                "<gray>Leader: <gold>" + leaderName + "</gold> | Status: " + (open ? "<green>Open" : "<red>Private") + "</gray>\n" +
                "<gray>Members (" + members.size() + "): <white>" + names + "</white></gray>", player));
    }

    private void result(Player player, PartyService.Result result) {
        String key = switch (result) {
            case SELF -> "party.self";
            case NOT_LEADER -> "party.not_leader";
            case ALREADY_IN_PARTY -> "party.already_in_party";
            case NOT_IN_PARTY -> "party.not_in_party";
            case NOT_MEMBER -> "party.not_member";
            case LEADER_MUST_DISBAND -> "party.leader_must_disband";
            case PARTY_FULL -> "party.full";
            case INVALID_NAME -> "party.invalid_name";
            case NOT_OPEN -> "party.not_open";
            case OK -> "";
        };
        if (!key.isEmpty()) {
            send(player, key, Map.of());
        }
    }

    private void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(MessageFormatter.render(plugin.config().language().text(key), placeholders, player));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] arguments = splitArguments(invocation.arguments());
        if (arguments.length <= 1) {
            return List.of("help", "create", "invite", "accept", "deny", "kick", "leave", "disband", "rename", "open", "close", "promote", "demote", "warp", "join", "menu", "gui", "status", "list", "chat");
        }
        String sub = arguments[0].toLowerCase(Locale.ROOT);
        if (arguments.length == 2) {
            if (List.of("invite", "kick", "demote", "join").contains(sub)) {
                return plugin.server().getAllPlayers().stream().map(Player::getUsername).toList();
            }
            if ("promote".equals(sub)) {
                List<String> suggestions = new ArrayList<>(List.of("officer", "leader"));
                suggestions.addAll(plugin.server().getAllPlayers().stream().map(Player::getUsername).toList());
                return suggestions;
            }
        }
        if (arguments.length == 3 && "promote".equals(sub)) {
            return plugin.server().getAllPlayers().stream().map(Player::getUsername).toList();
        }
        return List.of();
    }

    private static String[] splitArguments(String raw) {
        return raw == null || raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
    }
}
