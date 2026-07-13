/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisSecurityTest {
    @Test
    void registrationSignaturesAreDeterministicAndPayloadBound() {
        JsonObject payload = new JsonObject();
        payload.addProperty("name", "lobby-1");
        payload.addProperty("host", "10.0.0.4");
        payload.addProperty("port", 25565);
        payload.addProperty("timestamp", 123456789L);
        String first = RedisSyncService.registrationSignature(payload, "shared-secret");
        assertEquals(first, RedisSyncService.registrationSignature(payload, "shared-secret"));
        payload.addProperty("port", 25566);
        assertNotEquals(first, RedisSyncService.registrationSignature(payload, "shared-secret"));
    }

    @Test
    void registrationHostAllowlistSupportsExactAndWildcardRules() {
        List<String> rules = List.of("10.0.0.4", "*.internal.example");
        assertTrue(RedisSyncService.hostAllowed("10.0.0.4", rules));
        assertTrue(RedisSyncService.hostAllowed("lobby.internal.example", rules));
        assertFalse(RedisSyncService.hostAllowed("internal.example", rules));
        assertFalse(RedisSyncService.hostAllowed("attacker.example", rules));
        assertTrue(RedisSyncService.hostAllowed("anything", List.of()));
    }

    @Test
    void signedRegistrationsRejectExpiredAndFarFutureTimestamps() {
        long now = 1_000_000L;
        assertTrue(RedisSyncService.registrationFresh(now - 30_000L, now, 30));
        assertTrue(RedisSyncService.registrationFresh(now + 30_000L, now, 30));
        assertFalse(RedisSyncService.registrationFresh(now - 30_001L, now, 30));
        assertFalse(RedisSyncService.registrationFresh(now + 30_001L, now, 30));
        assertFalse(RedisSyncService.registrationFresh(Long.MIN_VALUE, now, 30));
    }
}
