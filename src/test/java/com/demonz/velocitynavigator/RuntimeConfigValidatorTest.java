/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigValidatorTest {
    @Test
    void detectsCommandAndHoldingServerConflicts(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("navigator.toml");
        Files.writeString(file, """
                [party]
                enabled = true
                command = "vn"
                chat_command = "p"
                [queue]
                enabled = true
                holding_server = "lobby-1"
                [redis]
                enabled = false
                """);
        RuntimeConfigValidator.Validation validation = RuntimeConfigValidator.validate(Config.defaults(), AdvancedConfig.load(file), Set.of("lobby-1"));
        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(value -> value.contains("/vn")));
        assertTrue(validation.errors().stream().anyMatch(value -> value.contains("must not also be a routed lobby")));
    }

    @Test
    void warnsAboutUnsafeRedisDefaults(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("navigator.toml");
        Files.writeString(file, """
                [redis]
                enabled = true
                host = "redis.internal"
                password = ""
                ssl = false
                registration_secret = ""
                allowed_registration_hosts = []
                """);
        RuntimeConfigValidator.Validation validation = RuntimeConfigValidator.validate(Config.defaults(), AdvancedConfig.load(file), Set.of("lobby-1", "lobby-2"));
        assertTrue(validation.valid());
        assertTrue(validation.warnings().stream().anyMatch(value -> value.contains("without authentication")));
        assertTrue(validation.warnings().stream().anyMatch(value -> value.contains("unsigned")));
        assertTrue(validation.warnings().stream().anyMatch(value -> value.contains("TLS is disabled")));
    }
}
