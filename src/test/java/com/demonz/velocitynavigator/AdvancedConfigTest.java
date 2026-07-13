/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedConfigTest {
    @Test
    void readsAdvancedRuntimeSections(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("navigator.toml");
        Files.writeString(file, """
                [party]
                enabled = false
                invite_timeout_seconds = 90
                follow_leader = false
                [queue]
                enabled = true
                holding_server = "Holding"
                [redis]
                enabled = true
                host = "redis.internal"
                port = 6380
                username = "navigator"
                password = "secret"
                ssl = true
                channel_prefix = "network"
                registration_secret = "registration-secret"
                registration_max_age_seconds = 45
                allowed_registration_hosts = ["10.0.0.10", "*.internal"]
                [backend_states]
                enabled = true
                allowed = ["WAITING", "LOBBY"]
                allow_unknown = false
                """);
        AdvancedConfig config = AdvancedConfig.load(file);
        assertFalse(config.party().enabled());
        assertEquals(90, config.party().inviteTimeoutSeconds());
        assertEquals("holding", config.queue().holdingServer());
        assertTrue(config.redis().enabled());
        assertEquals("redis.internal", config.redis().host());
        assertEquals("network", config.redis().channelPrefix());
        assertEquals("navigator", config.redis().username());
        assertTrue(config.redis().ssl());
        assertEquals(45, config.redis().registrationMaxAgeSeconds());
        assertEquals(2, config.redis().allowedRegistrationHosts().size());
        assertFalse(config.backendStates().allowUnknown());
        assertEquals(2, config.backendStates().allowed().size());
    }
}
