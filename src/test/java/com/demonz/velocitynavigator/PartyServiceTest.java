/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {
    @Test
    void inviteAcceptFollowKickAndDisbandLifecycle() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        assertEquals(PartyService.Result.OK, service.invite(leader, member));
        assertEquals(leader, service.accept(member).orElseThrow());
        assertEquals(1, service.partyCount());
        assertTrue(service.followers(leader).contains(member));
        assertEquals(PartyService.Result.OK, service.kick(leader, member));
        assertFalse(service.members(leader).contains(member));
        assertEquals(1, service.disband(leader).size());
        assertEquals(0, service.partyCount());
    }

    @Test
    void memberCannotPerformLeaderActions() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        service.invite(leader, member);
        service.accept(member);
        assertEquals(PartyService.Result.NOT_LEADER, service.invite(member, target));
        assertEquals(PartyService.Result.NOT_LEADER, service.kick(member, leader));
        assertEquals(PartyService.Result.LEADER_MUST_DISBAND, service.leave(leader));
    }

    @Test
    void acceptReportsWhenPartyFilledAfterInvite() {
        PartyService service = new PartyService();
        service.configure(new AdvancedConfig.Party(true, 60, true, 2, "party", "p", "none"));
        UUID leader = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        service.invite(leader, first);
        service.invite(leader, second);
        assertEquals(PartyService.Result.OK, service.acceptDetailed(first).result());
        assertEquals(PartyService.Result.PARTY_FULL, service.acceptDetailed(second).result());
    }
}
