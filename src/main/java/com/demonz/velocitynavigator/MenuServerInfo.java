/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Map;

public record MenuServerInfo(
        String server,
        int players,
        String maxPlayers,
        String status,
        String statusColor,
        String ping,
        boolean available
) {
    public Map<String, String> placeholders() {
        return Map.of(
                "server", server,
                "players", String.valueOf(players),
                "max_players", maxPlayers,
                "status", status,
                "status_color", statusColor,
                "ping", ping
        );
    }
}
