/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.party.gui;

public record PartyMenuConfig(
        String title,
        String bedrockTitle,
        String bedrockContent,
        String memberListLabel,
        String settingsLabel,
        String renameLabel,
        String warpLabel,
        String leaveLabel,
        String promoteOfficerLabel,
        String promoteLeaderLabel,
        String demoteLabel,
        String kickLabel
) {
    public static PartyMenuConfig defaults() {
        return new PartyMenuConfig(
                "<aqua><bold>Party Management</bold></aqua>",
                "Party Management",
                "Select an option to manage your party:",
                "👥 Manage Members",
                "⚙️ Party Settings",
                "✏️ Rename Party",
                "🚀 Warp Party to Server",
                "🚪 Leave / Disband Party",
                "🛡️ Promote to Officer",
                "👑 Transfer Leadership",
                "👤 Demote to Member",
                "❌ Kick Member"
        );
    }
}
