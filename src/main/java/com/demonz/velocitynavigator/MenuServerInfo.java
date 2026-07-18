/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Map;

public record MenuServerInfo(
        String server,
        String displayName,
        String description,
        int players,
        String maxPlayers,
        String status,
        String statusColor,
        String ping,
        boolean available,
        MenuServerState state
) {
    public MenuServerInfo {
        displayName = displayName == null || displayName.isBlank() ? server : displayName;
        description = description == null ? "" : description;
        state = state == null ? MenuServerState.HEALTHY : state;
    }

    public MenuServerInfo(String server, String displayName, int players, String maxPlayers,
                          String status, String statusColor, String ping, boolean available) {
        this(server, displayName, "", players, maxPlayers, status, statusColor, ping, available,
                MenuServerState.HEALTHY);
    }

    public MenuServerInfo(String server, int players, String maxPlayers, String status,
                          String statusColor, String ping, boolean available) {
        this(server, server, "", players, maxPlayers, status, statusColor, ping, available,
                MenuServerState.HEALTHY);
    }

    public Map<String, String> placeholders() {
        return Map.ofEntries(
                Map.entry("server", displayName),
                Map.entry("display_name", displayName),
                Map.entry("server_id", server),
                Map.entry("description", description),
                Map.entry("players", String.valueOf(players)),
                Map.entry("max_players", maxPlayers),
                Map.entry("status", status),
                Map.entry("status_color", statusColor),
                Map.entry("ping", ping)
        );
    }
}
