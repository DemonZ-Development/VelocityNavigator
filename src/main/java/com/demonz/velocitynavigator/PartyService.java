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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyService {
    private final Map<UUID, Party> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private volatile int inviteTimeoutSeconds = 60;
    private volatile int maxSize = 20;

    public void configure(AdvancedConfig.Party settings) {
        inviteTimeoutSeconds = settings.inviteTimeoutSeconds();
        maxSize = settings.maxSize();
        purgeInvites();
    }

    public synchronized Result invite(UUID sender, UUID target) {
        if (sender.equals(target)) return Result.SELF;
        Party party = byMember.get(sender);
        if (party != null && !party.leader.equals(sender)) return Result.NOT_LEADER;
        if (party != null && party.members.size() >= maxSize) return Result.PARTY_FULL;
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
        if (invite == null || byMember.containsKey(player)) return new AcceptResult(Result.NOT_MEMBER, null);
        Party party = byMember.get(invite.sender);
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
        byMember.remove(target, party);
        return Result.OK;
    }

    public synchronized Result leave(UUID player) {
        Party party = byMember.get(player);
        if (party == null) return Result.NOT_IN_PARTY;
        if (party.leader.equals(player)) return Result.LEADER_MUST_DISBAND;
        party.members.remove(player);
        byMember.remove(player, party);
        return Result.OK;
    }

    public synchronized List<UUID> disband(UUID player) {
        Party party = byMember.get(player);
        if (party == null || !party.leader.equals(player)) return List.of();
        List<UUID> members = List.copyOf(party.members);
        members.forEach(id -> byMember.remove(id, party));
        return members;
    }

    public synchronized void disconnect(UUID player) {
        invites.remove(player);
        Party party = byMember.get(player);
        if (party == null) return;
        if (party.leader.equals(player)) {
            disband(player);
        } else {
            party.members.remove(player);
            byMember.remove(player, party);
        }
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

    public int partyCount() {
        return (int) byMember.values().stream().distinct().count();
    }

    private Invite validInvite(UUID player) {
        Invite invite = invites.get(player);
        if (invite != null && Instant.now().isAfter(invite.expiresAt)) {
            invites.remove(player, invite);
            return null;
        }
        return invite;
    }

    private void purgeInvites() {
        Instant now = Instant.now();
        invites.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt));
    }

    public enum Result { OK, SELF, NOT_LEADER, ALREADY_IN_PARTY, NOT_IN_PARTY, NOT_MEMBER, LEADER_MUST_DISBAND, PARTY_FULL }

    public record AcceptResult(Result result, UUID leader) {
    }

    private static final class Party {
        private final UUID leader;
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        private Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    private record Invite(UUID sender, Instant expiresAt) {
    }
}
