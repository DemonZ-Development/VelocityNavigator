/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MenuBridgeProtocolTest {

    @Test
    void openMenuRoundTripsIncludingBlankLoreLines() throws Exception {
        MenuBridgeProtocol.OpenMenu decoded = MenuBridgeProtocol.decodeOpen(MenuBridgeProtocol.encodeOpen(
                "abc123",
                6,
                "§bMain Lobby Selector",
                1,
                3,
                5,
                true,
                "GRAY_STAINED_GLASS_PANE",
                List.of(new MenuBridgeProtocol.MenuItem(
                        10, "lobby-1", "COMPASS", "§bLobby 1", List.of("§7Healthy", "", "§eClick")
                ))
        ));

        assertEquals("abc123", decoded.token());
        assertEquals(6, decoded.rows());
        assertEquals(1, decoded.page());
        assertEquals(3, decoded.totalPages());
        assertEquals("lobby-1", decoded.items().get(0).target());
        assertEquals("", decoded.items().get(0).lore().get(1));
    }

    @Test
    void selectionRoundTrips() throws Exception {
        MenuBridgeProtocol.Selection decoded = MenuBridgeProtocol.decodeSelection(
                MenuBridgeProtocol.encodeSelection("token", "lobby-2")
        );
        assertEquals("token", decoded.token());
        assertEquals("lobby-2", decoded.target());
    }

    @Test
    void helloRoundTripsAndIdentifiesPacketType() throws Exception {
        byte[] hello = MenuBridgeProtocol.encodeHello("4.4.0");
        assertEquals(MenuBridgeProtocol.PacketType.HELLO, MenuBridgeProtocol.packetType(hello));
        assertEquals("4.4.0", MenuBridgeProtocol.decodeHello(hello).version());
    }

    @Test
    void rejectsTrailingOrOversizedData() throws Exception {
        byte[] valid = MenuBridgeProtocol.encodeSelection("token", "lobby-2");
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IOException.class, () -> MenuBridgeProtocol.decodeSelection(trailing));
        assertThrows(IOException.class, () -> MenuBridgeProtocol.decodeSelection(
                new byte[MenuBridgeProtocol.MAX_PAYLOAD_BYTES + 1]
        ));
    }
}
