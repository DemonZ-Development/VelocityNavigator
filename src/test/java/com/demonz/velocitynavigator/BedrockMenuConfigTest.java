/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BedrockMenuConfigTest {
    @Test
    void normalizesBedrockMenuControls() {
        GuiConfig.BedrockMenu menu = new GuiConfig.BedrockMenu(false, false, "PLAYERS", 900, false, true, false, true, "Title", "Content", "{server} {status}");
        assertFalse(menu.enabled());
        assertFalse(menu.fallbackToChat());
        assertEquals("players", menu.sortMode());
        assertEquals(500, menu.maxButtons());
        assertEquals("{server} {status}", menu.buttonFormat());
    }

    @Test
    void rejectsUnknownSortMode() {
        GuiConfig.BedrockMenu menu = new GuiConfig.BedrockMenu(true, true, "latency_magic", 0, true, true, true, true, "", "", "");
        assertEquals("routing", menu.sortMode());
        assertEquals(1, menu.maxButtons());
    }
}
