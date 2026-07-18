/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import java.util.Locale;

public enum MenuServerState {
    HEALTHY("healthy", "menus.status_healthy"),
    FULL("full", "menus.status_full"),
    DRAINING("draining", "menus.status_draining"),
    OFFLINE("offline", "menus.status_offline"),
    IN_GAME("in_game", "menus.status_in_game");

    private final String configKey;
    private final String languageKey;

    MenuServerState(String configKey, String languageKey) {
        this.configKey = configKey;
        this.languageKey = languageKey;
    }

    public String configKey() {
        return configKey;
    }

    public String languageKey() {
        return languageKey;
    }

    public static MenuServerState fromConfigKey(String value) {
        if (value == null) {
            return HEALTHY;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (MenuServerState state : values()) {
            if (state.configKey.equals(normalized)) {
                return state;
            }
        }
        return HEALTHY;
    }
}
