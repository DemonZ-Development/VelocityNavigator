/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UniversalBridgeIntegrationTest {

    @Test
    void proxyOpenBackendClickAndProxySelectionRoundTrip() throws Exception {
        byte[] proxyOpen = MenuBridgeProtocol.encodeOpen(
                "one-time-token", 6, "§bLobby Selector", 0, 2, 5, true,
                "GRAY_STAINED_GLASS_PANE",
                List.of(
                        new MenuBridgeProtocol.MenuItem(10, "lobby-1", "COMPASS", "§bLobby 1", List.of("§eClick")),
                        new MenuBridgeProtocol.MenuItem(53, "@page:1", "ARROW", "§eNext", List.of())
                )
        );
        MenuBridgeProtocol.OpenMenu backendView = MenuBridgeProtocol.decodeOpen(proxyOpen);
        MenuBridgeProtocol.MenuItem clicked = backendView.items().get(0);
        byte[] backendSelection = MenuBridgeProtocol.encodeSelection(backendView.token(), clicked.target());
        MenuBridgeProtocol.Selection proxyResult = MenuBridgeProtocol.decodeSelection(backendSelection);

        assertEquals("one-time-token", proxyResult.token());
        assertEquals("lobby-1", proxyResult.target());
    }

    @Test
    void paginationControlRoundTripsWithoutBecomingServerSelection() throws Exception {
        MenuBridgeProtocol.Selection selection = MenuBridgeProtocol.decodeSelection(
                MenuBridgeProtocol.encodeSelection("token", "@refresh:2")
        );
        assertEquals("@refresh:2", selection.target());
    }
}
