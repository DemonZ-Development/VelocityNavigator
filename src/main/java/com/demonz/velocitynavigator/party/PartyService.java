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
package com.demonz.velocitynavigator.party;
import com.demonz.velocitynavigator.config.AdvancedConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService {
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private volatile int inviteTimeoutSeconds = 60;
    private volatile int maxSize = 20;

    public synchronized void configure(AdvancedConfig.Party settings) {
        inviteTimeoutSeconds = settings.inviteTimeoutSeconds();
        maxSize = settings.maxSize();
        purgeInvites();
    }

    public synchronized Result create(UUID leader, String name) {
        if (byMember.containsKey(leader)) return Result.ALREADY_IN_PARTY;
        Party party = new Party(leader);
        if (name != null && !name.isBlank()) {
            party.name = translateColorCodes(name.trim());
        }
        byMember.put(leader, party);
        return Result.OK;
    }

    public synchronized Result invite(UUID sender, UUID target) {
        if (sender.equals(target)) return Result.SELF;
        Party party = byMember.get(sender);
        if (party != null && !party.leader.equals(sender)) return Result.NOT_LEADER;
        int currentSize = party != null ? party.members.size() : 1;
        if (currentSize >= maxSize) return Result.PARTY_FULL;
        if (byMember.containsKey(target)) return Result.ALREADY_IN_PARTY;
        invites.put(target, new Invite(sender, Instant.now().plusSeconds(inviteTimeoutSeconds)));
        return Result.OK;
    }

    public synchronized Optional<UUID> accept(UUID player) {
        AcceptResult result = acceptDetailed(player);
        return result.result() == Result.OK ? Optional.of(result.leader()) : Optional.empty();
    }

    public synchronized AcceptResult acceptDetailed(UUID player) {
        Invite invite = validInvite(player);
        if (invite == null) return new AcceptResult(Result.NOT_MEMBER, null);
        if (byMember.containsKey(player)) return new AcceptResult(Result.ALREADY_IN_PARTY, null);
        Party party = byMember.get(invite.sender);
        if (party != null && !party.leader.equals(invite.sender)) {
            return new AcceptResult(Result.NOT_LEADER, invite.sender);
        }
        if (party == null) {
            party = new Party(invite.sender);
            byMember.put(invite.sender, party);
        }
        if (party.members.size() >= maxSize) return new AcceptResult(Result.PARTY_FULL, invite.sender);
        party.members.add(player);
        byMember.put(player, party);
        invites.remove(player);
        return new AcceptResult(Result.OK, invite.sender);
    }

    public synchronized Optional<UUID> deny(UUID player) {
        Invite invite = validInvite(player);
        invites.remove(player);
        return invite == null ? Optional.empty() : Optional.of(invite.sender);
    }

    public synchronized Result kick(UUID actor, UUID target) {
        Party party = byMember.get(actor);
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return Result.NOT_LEADER;
        if (actor.equals(target)) return Result.SELF;
        if (!party.members.remove(target)) return Result.NOT_MEMBER;
        party.memberRoles.remove(target);
        byMember.remove(target, party);
        removeInvitesFrom(target);
        return Result.OK;
    }

    public synchronized Result leave(UUID player) {
        Party party = byMember.get(player);
        if (party == null) return Result.NOT_IN_PARTY;
        if (party.leader.equals(player)) return Result.LEADER_MUST_DISBAND;
        party.members.remove(player);
        party.memberRoles.remove(player);
        byMember.remove(player, party);
        removeInvitesFrom(player);
        return Result.OK;
    }

    public synchronized List<UUID> disband(UUID player) {
        Party party = byMember.get(player);
        if (party == null || !party.leader.equals(player)) return List.of();
        List<UUID> members = List.copyOf(party.members);
        party.memberRoles.clear();
        members.forEach(id -> byMember.remove(id, party));
        removeInvitesFrom(members);
        return members;
    }

    public synchronized void disconnect(UUID player) {
        invites.remove(player);
        Party party = byMember.get(player);
        if (party == null) return;
        Role role = party.memberRoles.getOrDefault(player, Role.MEMBER);
        disconnectedReservations.put(player, new DisconnectedReservation(party, role, Instant.now().plusSeconds(60)));
    }

    public synchronized boolean reconnect(UUID player) {
        purgeReservations();
        DisconnectedReservation res = disconnectedReservations.remove(player);
        if (res != null && Instant.now().isBefore(res.expiresAt())) {
            Party party = res.party();
            if (byMember.containsValue(party)) {
                party.members.add(player);
                party.memberRoles.put(player, res.role());
                byMember.put(player, party);
                return true;
            }
        }
        return false;
    }

    public synchronized List<UUID> followers(UUID leader) {
        Party party = byMember.get(leader);
        if (party == null || !party.leader.equals(leader)) return List.of();
        List<UUID> followers = new ArrayList<>(party.members);
        followers.remove(leader);
        return List.copyOf(followers);
    }

    public synchronized List<UUID> members(UUID player) {
        Party party = byMember.get(player);
        return party == null ? List.of() : List.copyOf(party.members);
    }

    public synchronized boolean isLeader(UUID player) {
        Party party = byMember.get(player);
        return party != null && party.leader.equals(player);
    }

    public synchronized Optional<UUID> leader(UUID player) {
        Party party = byMember.get(player);
        return party == null ? Optional.empty() : Optional.of(party.leader);
    }

    public synchronized int partyCount() {
        Set<Party> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(byMember.values());
        return set.size();
    }

    private Invite validInvite(UUID player) {
        Invite invite = invites.get(player);
        if (invite != null && Instant.now().isAfter(invite.expiresAt)) {
            invites.remove(player, invite);
            return null;
        }
        return invite;
    }

    public synchronized Result rename(UUID actor, String newName) {
        Party party = byMember.get(actor);
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return Result.NOT_LEADER;
        if (newName == null || newName.isBlank()) return Result.INVALID_NAME;
        party.name = translateColorCodes(newName.trim());
        return Result.OK;
    }

    public synchronized Result setOpen(UUID actor, boolean open) {
        Party party = byMember.get(actor);
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return Result.NOT_LEADER;
        party.open = open;
        return Result.OK;
    }

    public synchronized Result promote(UUID actor, UUID target, Role targetRole) {
        Party party = byMember.get(actor);
        if (party == null) return Result.NOT_IN_PARTY;
        Role actorRole = party.memberRoles.getOrDefault(actor, Role.MEMBER);
        if (!actorRole.isAtLeast(Role.LEADER)) return Result.NOT_LEADER;
        if (!party.members.contains(target)) return Result.NOT_MEMBER;
        if (actor.equals(target)) return Result.SELF;
        if (targetRole == Role.LEADER) {
            party.memberRoles.put(party.leader, Role.OFFICER);
            party.leader = target;
            party.memberRoles.put(target, Role.LEADER);
        } else {
            party.memberRoles.put(target, targetRole);
        }
        return Result.OK;
    }

    public synchronized Result demote(UUID actor, UUID target) {
        Party party = byMember.get(actor);
        if (party == null) return Result.NOT_IN_PARTY;
        if (!party.leader.equals(actor)) return Result.NOT_LEADER;
        if (!party.members.contains(target)) return Result.NOT_MEMBER;
        if (actor.equals(target)) return Result.SELF;
        party.memberRoles.put(target, Role.MEMBER);
        return Result.OK;
    }

    public synchronized Role getRole(UUID player) {
        Party party = byMember.get(player);
        if (party == null) return null;
        return party.memberRoles.getOrDefault(player, Role.MEMBER);
    }

    public synchronized AcceptResult joinOpen(UUID player, UUID leader) {
        if (byMember.containsKey(player)) return new AcceptResult(Result.ALREADY_IN_PARTY, null);
        Party party = byMember.get(leader);
        if (party == null || !party.leader.equals(leader)) return new AcceptResult(Result.NOT_IN_PARTY, null);
        if (!party.open) return new AcceptResult(Result.NOT_OPEN, leader);
        if (party.members.size() >= maxSize) return new AcceptResult(Result.PARTY_FULL, leader);
        party.members.add(player);
        byMember.put(player, party);
        invites.remove(player);
        return new AcceptResult(Result.OK, leader);
    }

    public synchronized String partyName(UUID player, String defaultLeaderName) {
        Party party = byMember.get(player);
        if (party == null) return "None";
        return party.name != null && !party.name.isBlank() ? party.name : defaultLeaderName + "'s Party";
    }

    public synchronized boolean isPartyOpen(UUID player) {
        Party party = byMember.get(player);
        return party != null && party.open;
    }

    public int maxSize() {
        return maxSize;
    }

    public static String translateColorCodes(String text) {
        if (text == null) return "";
        return text.replace("&0", "<black>")
                   .replace("&1", "<dark_blue>")
                   .replace("&2", "<dark_green>")
                   .replace("&3", "<dark_aqua>")
                   .replace("&4", "<dark_red>")
                   .replace("&5", "<dark_purple>")
                   .replace("&6", "<gold>")
                   .replace("&7", "<gray>")
                   .replace("&8", "<dark_gray>")
                   .replace("&9", "<blue>")
                   .replace("&a", "<green>")
                   .replace("&b", "<aqua>")
                   .replace("&c", "<red>")
                   .replace("&d", "<light_purple>")
                   .replace("&e", "<yellow>")
                   .replace("&f", "<white>")
                   .replace("&l", "<bold>")
                   .replace("&o", "<italic>")
                   .replace("&n", "<underlined>")
                   .replace("&m", "<strikethrough>")
                   .replace("&r", "<reset>");
    }

    private final Map<UUID, DisconnectedReservation> disconnectedReservations = new ConcurrentHashMap<>();

    public synchronized void purgeStaleData() {
        purgeInvites();
    }

    private void purgeInvites() {
        Instant now = Instant.now();
        invites.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));
        purgeReservations();
    }

    private void removeInvitesFrom(List<UUID> senders) {
        invites.entrySet().removeIf(entry -> senders.contains(entry.getValue().sender));
    }

    private void removeInvitesFrom(UUID sender) {
        invites.entrySet().removeIf(entry -> entry.getValue().sender.equals(sender));
    }

    private void purgeReservations() {
        Instant now = Instant.now();
        disconnectedReservations.entrySet().removeIf(entry -> {
            boolean expired = now.isAfter(entry.getValue().expiresAt());
            if (expired) {
                UUID player = entry.getKey();
                Party party = entry.getValue().party();
                party.members.remove(player);
                party.memberRoles.remove(player);
                byMember.remove(player, party);
                removeInvitesFrom(player);
                if (party.leader.equals(player)) {
                    if (party.members.isEmpty()) {
                        byMember.values().removeIf(p -> p.equals(party));
                    } else {
                        UUID nextLeader = party.members.iterator().next();
                        party.leader = nextLeader;
                        party.memberRoles.put(nextLeader, Role.LEADER);
                    }
                }
            }
            return expired;
        });
    }

    private record DisconnectedReservation(Party party, Role role, Instant expiresAt) {}

    public enum Result { OK, SELF, NOT_LEADER, ALREADY_IN_PARTY, NOT_IN_PARTY, NOT_MEMBER, LEADER_MUST_DISBAND, PARTY_FULL, INVALID_NAME, NOT_OPEN }

    public record AcceptResult(Result result, UUID leader) {
    }

    public static final class Party {
        private UUID leader;
        private String name;
        private boolean open;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final ConcurrentHashMap<UUID, Role> memberRoles = new ConcurrentHashMap<>();
        private final Instant createdAt = Instant.now();

        private Party(UUID leader) {
            this.leader = leader;
            this.members.add(leader);
            this.memberRoles.put(leader, Role.LEADER);
        }

        public UUID leader() { return leader; }
        public String name() { return name; }
        public boolean isOpen() { return open; }
        public Set<UUID> members() { return Set.copyOf(members); }
        public Map<UUID, Role> memberRoles() { return Map.copyOf(memberRoles); }
        public Instant createdAt() { return createdAt; }
    }

    private record Invite(UUID sender, Instant expiresAt) {
    }
}
