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
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

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
            showStatus(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "kick" -> kick(player, args);
            case "leave" -> result(player, plugin.partyService().leave(player.getUniqueId()));
            case "disband" -> disband(player);
            case "status", "list" -> showStatus(player);
            case "chat" -> PartyChatCommand.sendPartyMessage(plugin, player,
                    Arrays.copyOfRange(args, 1, args.length));
            default -> send(player, "party.usage", Map.of());
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
        String names = members.stream().map(id -> plugin.server().getPlayer(id).map(Player::getUsername).orElse(id.toString())).reduce((a, b) -> a + ", " + b).orElse("");
        send(player, "party.status", Map.of("members", names, "count", String.valueOf(members.size())));
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
            default -> "party.done";
        };
        send(player, key, Map.of());
    }

    private void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(MessageFormatter.render(plugin.config().language().text(key), placeholders, player));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] arguments = splitArguments(invocation.arguments());
        if (arguments.length <= 1) return List.of("invite", "accept", "deny", "kick", "leave", "disband", "status", "chat");
        if (List.of("invite", "kick").contains(arguments[0].toLowerCase(Locale.ROOT))) return plugin.server().getAllPlayers().stream().map(Player::getUsername).toList();
        return List.of();
    }

    private static String[] splitArguments(String raw) {
        return raw == null || raw.isBlank() ? new String[0] : raw.trim().split("\\s+");
    }
}
