/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.party;

public enum Role {
    LEADER("Leader", "👑", 3),
    OFFICER("Officer", "🛡️", 2),
    MEMBER("Member", "👤", 1);

    private final String displayName;
    private final String icon;
    private final int weight;

    Role(String displayName, String icon, int weight) {
        this.displayName = displayName;
        this.icon = icon;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public String icon() {
        return icon;
    }

    public int weight() {
        return weight;
    }

    public boolean isAtLeast(Role role) {
        return this.weight >= role.weight;
    }
}
