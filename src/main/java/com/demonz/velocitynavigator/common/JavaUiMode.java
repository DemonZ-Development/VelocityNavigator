/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import java.util.Locale;

public enum JavaUiMode {
    AUTO, DIALOG, INVENTORY, CHAT, SIGN, COMMAND;

    public static final int DIALOG_PROTOCOL = 771;

    public static JavaUiMode parse(String value) {
        if (value == null) return AUTO;
        if ("chest".equalsIgnoreCase(value.trim())) return INVENTORY;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return AUTO;
        }
    }

    public boolean prefersDialog(int protocol, boolean available, boolean bedrock) {
        return (this == AUTO || this == DIALOG) && available && !bedrock && protocol >= DIALOG_PROTOCOL;
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
