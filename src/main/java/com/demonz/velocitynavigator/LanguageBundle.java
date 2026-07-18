/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.demonz.velocitynavigator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LanguageBundle {

    private static final LanguageBundle DEFAULTS = createDefaults();

    private final String language;
    private final String activeLanguage;
    private final Map<String, String> strings;
    private final Map<String, List<String>> lists;

    public LanguageBundle(Map<String, String> strings, Map<String, List<String>> lists) {
        this("en", "en", strings, lists);
    }

    public LanguageBundle(String language, String activeLanguage, Map<String, String> strings, Map<String, List<String>> lists) {
        this.language = normalize(language, "en");
        this.activeLanguage = normalize(activeLanguage, this.language);
        this.strings = Collections.unmodifiableMap(new LinkedHashMap<>(strings == null ? Map.of() : strings));
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (lists != null) {
            lists.forEach((key, value) -> copied.put(key, value == null ? List.of() : List.copyOf(value)));
        }
        this.lists = Collections.unmodifiableMap(copied);
    }

    public String text(String key) {
        return strings.getOrDefault(key, DEFAULTS.strings.getOrDefault(key, ""));
    }

    public List<String> lines(String key) {
        return lists.getOrDefault(key, DEFAULTS.lists.getOrDefault(key, List.of()));
    }

    public Map<String, String> strings() {
        return strings;
    }

    public Map<String, List<String>> lists() {
        return lists;
    }

    public String language() {
        return language;
    }

    public String activeLanguage() {
        return activeLanguage;
    }

    public static LanguageBundle defaults() {
        return DEFAULTS;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static LanguageBundle createDefaults() {
        Map<String, String> strings = new LinkedHashMap<>();
        strings.put("messages.connecting", "<aqua>Sending you to <server>...</aqua>");
        strings.put("messages.already_connected", "<yellow>You are already connected to <server>.</yellow>");
        strings.put("messages.no_lobby_found", "<red>No available lobby could be found. (<reason>)</red>");
        strings.put("messages.player_only", "<gray>This command can only be used by a player.</gray>");
        strings.put("messages.cooldown", "<yellow>Please wait <time> more second(s).</yellow>");
        strings.put("messages.reload_success", "<green>VelocityNavigator, messages.toml, gui.toml, and servers.toml reloaded.</green>");
        strings.put("messages.reload_failed", "<red>Reload failed. Check console for details.</red>");
        strings.put("messages.retrying", "<yellow>Retrying connection... (<attempt>/<max>)</yellow>");
        strings.put("messages.permission_denied", "<red>You do not have permission to use this command.</red>");
        strings.put("messages.route_unavailable", "<red>VelocityNavigator could not resolve a lobby right now.</red>");
        strings.put("messages.menu_expired", "<red>The lobby menu expired. Run /<command> again.</red>");
        strings.put("messages.selection_unavailable", "<red>The selected lobby is no longer available.</red>");
        strings.put("messages.selection_unregistered", "<red>The selected server is no longer registered.</red>");
        strings.put("messages.selection_validation_failed", "<red>VelocityNavigator could not validate that lobby selection right now.</red>");
        strings.put("messages.connection_failed", "<red>Failed to connect: <reason></red>");
        strings.put("messages.connection_failed_prefix", "<red>Failed to connect: </red>");
        strings.put("messages.connection_failed_attempts", "<red>Failed to connect after <attempts> attempt(s).</red>");
        strings.put("messages.connection_error", "<red>An error occurred while connecting to the lobby.</red>");
        strings.put("messages.unknown_error", "<red>Unknown error</red>");
        strings.put("messages.formatting", "auto");
        strings.put("messages.dashboard_healthy", "<green>");
        strings.put("messages.dashboard_draining", "<yellow>");
        strings.put("messages.dashboard_open", "<red>");
        strings.put("messages.dashboard_offline", "<gray>");
        strings.put("party.disabled", "<red>The party system is disabled.</red>");
        strings.put("party.usage", "<yellow>Use /party invite &lt;player&gt;, accept, deny, kick &lt;player&gt;, leave, disband, status, or chat &lt;message&gt;.</yellow>");
        strings.put("party.player_not_found", "<red>Player <target> was not found.</red>");
        strings.put("party.invite_sent", "<green>Invited <target> to your party.</green>");
        strings.put("party.invite_received", "<yellow><player> invited you to a party. Use /party accept or /party deny.</yellow>");
        strings.put("party.no_invite", "<red>You do not have a valid party invitation.</red>");
        strings.put("party.joined", "<green>You joined the party.</green>");
        strings.put("party.member_joined", "<green><player> joined your party.</green>");
        strings.put("party.invite_denied", "<gray>Party invitation denied.</gray>");
        strings.put("party.kicked", "<yellow>Removed <target> from the party.</yellow>");
        strings.put("party.you_were_kicked", "<red>You were removed from the party.</red>");
        strings.put("party.disbanded", "<yellow>The party was disbanded.</yellow>");
        strings.put("party.not_in_party", "<red>You are not in a party.</red>");
        strings.put("party.status", "<aqua>Party (<count>):</aqua> <white><members></white>");
        strings.put("party.self", "<red>You cannot target yourself.</red>");
        strings.put("party.not_leader", "<red>Only the party leader can do that.</red>");
        strings.put("party.already_in_party", "<red>That player is already in a party.</red>");
        strings.put("party.not_member", "<red>That player is not in your party.</red>");
        strings.put("party.leader_must_disband", "<red>The leader must disband the party.</red>");
        strings.put("party.full", "<red>The party has reached its configured member limit.</red>");
        strings.put("party.done", "<green>Party updated.</green>");
        strings.put("party.chat_usage", "<yellow>Use /party chat &lt;message&gt; or /p &lt;message&gt;.</yellow>");
        strings.put("party.chat", "<light_purple>[Party] <player>:</light_purple> <white><message></white>");
        strings.put("queue.connecting", "<green>A slot opened. Connecting you to <server>...</green>");
        strings.put("queue.position", "<yellow>Queue position: <position>/<size></yellow>");
        strings.put("queue.joined", "<green>The lobby pool is full. You joined the queue at position <position>.</green>");
        strings.put("queue.full", "<red>The lobby queue is full.</red>");
        strings.put("queue.left", "<yellow>You left the lobby queue.</yellow>");
        strings.put("queue.not_queued", "<gray>You are not currently queued.</gray>");

        strings.put("menus.status_healthy", "HEALTHY");
        strings.put("menus.status_full", "FULL");
        strings.put("menus.status_draining", "DRAINED");
        strings.put("menus.status_open", "CB_OPEN");
        strings.put("menus.status_offline", "OFFLINE");
        strings.put("menus.status_in_game", "IN GAME");
        strings.put("menus.chat.header", "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient> <gray>(Hover to view status, click to connect)</gray>");
        strings.put("menus.chat.entry", "  <gray>•</gray> <white><bold>{server}</bold></white> <gray>| Click to connect</gray>");
        strings.put("menus.chat.tooltip", "<white><bold>{server}</bold></white>\n<gray>Status:</gray> {status_color}{status}\n<gray>Players:</gray> <white>{players}/{max_players}</white>\n<gray>Ping:</gray> <white>{ping}ms</white>");
        strings.put("menus.inventory.title", "<aqua><bold>Main Lobby Selector</bold></aqua>");
        strings.put("menus.inventory.item_name", "<aqua><bold>{server}</bold></aqua>");
        strings.put("menus.inventory.bridge_unavailable", "<yellow>The inventory selector is unavailable on this server; showing the chat selector instead.</yellow>");
        strings.put("menus.inventory.bridge_required", "<red>The inventory selector bridge is not installed on this backend server.</red>");
        strings.put("menus.inventory.previous", "<yellow>Previous Page</yellow>");
        strings.put("menus.inventory.next", "<yellow>Next Page</yellow>");
        strings.put("menus.inventory.refresh", "<aqua>Refresh</aqua>");
        strings.put("menus.inventory.page", "<gray>Page <page>/<pages></gray>");
        strings.put("menus.bedrock.title", "<gradient:#8EF7FF:#D9F7FF><bold>Lobby Selector</bold></gradient>");
        strings.put("menus.bedrock.content", "<gray>Select a lobby server to connect:</gray>");
        strings.put("menus.bedrock.button", "<white><bold>{server}</bold></white> <gray>({players} Players)</gray>");
        strings.put("menus.bedrock.unavailable", "<red>The Bedrock lobby form is unavailable.</red>");
        strings.put("lobby.no_server_message", "<red>No lobby servers are currently available. Please try again later.</red>");
        strings.put("reasons.no_online_lobbies", "No online lobby servers found.");
        strings.put("reasons.selection_unavailable", "The selected lobby is no longer available.");
        strings.put("reasons.selection_unregistered", "The selected server is no longer registered.");
        strings.put("reasons.selection_validation_failed", "Could not validate the selected lobby.");

        Map<String, List<String>> lists = new LinkedHashMap<>();
        lists.put("menus.inventory.item_lore", List.of(
                "<gray>Status:</gray> {status_color}{status}",
                "<gray>Players:</gray> <white>{players}/{max_players}</white>",
                "<gray>Ping:</gray> <white>{ping}ms</white>",
                "",
                "<yellow>Click to connect</yellow>"
        ));
        lists.put("menus.inventory.unavailable_lore", List.of(
                "<red>This lobby is currently unavailable.</red>",
                "<gray>Use refresh to check again.</gray>"
        ));
        return new LanguageBundle(strings, lists);
    }
}
