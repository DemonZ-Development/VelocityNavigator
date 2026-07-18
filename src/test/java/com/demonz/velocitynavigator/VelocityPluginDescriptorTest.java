/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VelocityPluginDescriptorTest {

    @Test
    void declaresTheVelocityMainClass() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("velocity-plugin.json")) {
            assertNotNull(stream, "velocity-plugin.json must be packaged with the proxy plugin");
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject descriptor = JsonParser.parseString(json).getAsJsonObject();

            assertEquals("com.demonz.velocitynavigator.VelocityNavigator",
                    descriptor.get("main").getAsString(),
                    "Velocity 3.x and 4.x require the descriptor to declare its main class");
            assertEquals("4.4.0", descriptor.get("version").getAsString());
        }
    }
}
