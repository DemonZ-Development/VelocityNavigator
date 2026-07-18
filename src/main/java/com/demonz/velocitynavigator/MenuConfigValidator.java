/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.moandjiezana.toml.Toml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MenuConfigValidator {

    private static final Pattern MATERIAL_IDENTIFIER = Pattern.compile(
            "[A-Za-z0-9_.-]+(?::[A-Za-z0-9_./-]+)?"
    );
    private static final Pattern CURLY_PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> SELECTOR_PLACEHOLDERS = Set.of(
            "server", "display_name", "server_id", "description", "players",
            "max_players", "status", "status_color", "ping"
    );
    private static final Set<String> SUPPORTED_STATE_KEYS = Set.of(
            "full", "draining", "offline", "in_game"
    );
    private static final String SUPPORTED_PLACEHOLDERS =
            "{server}, {display_name}, {server_id}, {description}, {players}, "
                    + "{max_players}, {status}, {status_color}, {ping}";

    private MenuConfigValidator() {
    }

    public static Validation validate(Path guiPath, GuiConfig normalizedConfig, Set<String> registeredServers) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        GuiConfig gui = normalizedConfig == null ? GuiConfig.defaults() : normalizedConfig;

        if (guiPath == null || !Files.isRegularFile(guiPath)) {
            errors.add("gui.toml is missing.");
            return new Validation(errors, warnings);
        }

        Map<String, Object> root;
        try {
            root = new Toml().read(guiPath.toFile()).toMap();
        } catch (RuntimeException exception) {
            String detail = exception.getMessage();
            errors.add("gui.toml could not be parsed"
                    + (detail == null || detail.isBlank() ? "." : ": " + singleLine(detail)));
            return new Validation(errors, warnings);
        }

        int rows = rawRows(root, gui.rows(), errors);
        int inventorySize = rows * 9;
        validateMaterials(root, errors);

        Map<String, Integer> controlSlots = controlSlots(root, gui, inventorySize, errors);
        validateControlCollisions(controlSlots, errors);

        Set<String> registered = normalize(registeredServers);
        List<ServerOverride> servers = serverOverrides(value(root, "servers"), errors);
        validateServerIds(servers, registered, errors);
        validateDisplayNames(servers, warnings);
        validateServerSlots(servers, controlSlots, inventorySize, rows, errors);
        validateServerFields(servers, errors);
        validateStateFields(value(root, "states"), errors);

        return new Validation(errors, warnings);
    }

    private static int rawRows(Map<String, Object> root, int fallback, List<String> errors) {
        Object raw = value(root, "layout", "rows");
        Integer parsed = integer(raw, "layout.rows", null, errors);
        if (parsed == null) {
            return Math.max(2, Math.min(6, fallback));
        }
        if (parsed < 2 || parsed > 6) {
            errors.add("layout.rows must be between 2 and 6; found " + parsed + ".");
        }
        return Math.max(2, Math.min(6, parsed));
    }

    private static void validateMaterials(Map<String, Object> root, List<String> errors) {
        validateMaterial(value(root, "layout", "default_material"), "layout.default_material", errors);
        validateMaterial(value(root, "layout", "unavailable_material"), "layout.unavailable_material", errors);
        validateMaterial(value(root, "layout", "filler_material"), "layout.filler_material", errors);
        validateMaterial(value(root, "controls", "previous_material"), "controls.previous_material", errors);
        validateMaterial(value(root, "controls", "refresh_material"), "controls.refresh_material", errors);
        validateMaterial(value(root, "controls", "next_material"), "controls.next_material", errors);
    }

    private static Map<String, Integer> controlSlots(Map<String, Object> root, GuiConfig gui,
                                                      int inventorySize, List<String> errors) {
        Map<String, Integer> controls = new LinkedHashMap<>();
        addControlSlot(controls, root, "previous_slot", gui.previousSlot(), inventorySize, errors);
        addControlSlot(controls, root, "refresh_slot", gui.refreshSlot(), inventorySize, errors);
        addControlSlot(controls, root, "next_slot", gui.nextSlot(), inventorySize, errors);
        return controls;
    }

    private static void addControlSlot(Map<String, Integer> controls, Map<String, Object> root, String name,
                                       int fallback, int inventorySize, List<String> errors) {
        String location = "controls." + name;
        Integer slot = integer(value(root, "controls", name), location, fallback, errors);
        if (slot == null) {
            return;
        }
        if (slot < 0 || slot >= inventorySize) {
            errors.add(location + " must be between 0 and " + (inventorySize - 1) + "; found " + slot + ".");
            return;
        }
        int firstControlSlot = inventorySize - 9;
        if (slot < firstControlSlot) {
            errors.add(location + " must be in the reserved bottom row between " + firstControlSlot
                    + " and " + (inventorySize - 1) + "; found " + slot + ".");
            return;
        }
        controls.put(location, slot);
    }

    private static void validateControlCollisions(Map<String, Integer> controls, List<String> errors) {
        Map<Integer, String> owners = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : controls.entrySet()) {
            String previous = owners.putIfAbsent(entry.getValue(), entry.getKey());
            if (previous != null) {
                errors.add(previous + " and " + entry.getKey() + " both use slot " + entry.getValue() + ".");
            }
        }
    }

    private static void validateServerIds(List<ServerOverride> servers, Set<String> registered,
                                          List<String> errors) {
        for (ServerOverride server : servers) {
            if (!registered.contains(server.normalizedName())) {
                errors.add(server.location() + " does not match a registered Velocity server ID.");
            }
        }
    }

    private static void validateDisplayNames(List<ServerOverride> servers, List<String> warnings) {
        Map<String, ServerOverride> owners = new LinkedHashMap<>();
        for (ServerOverride server : servers) {
            Object raw = server.values().get("display_name");
            if (!(raw instanceof String displayName) || displayName.isBlank()) {
                continue;
            }
            String alias = displayName.trim();
            ServerOverride previous = owners.putIfAbsent(alias.toLowerCase(Locale.ROOT), server);
            if (previous != null) {
                warnings.add(previous.location() + " and " + server.location()
                        + " share display_name '" + singleLine(alias)
                        + "'; selections remain safe, but labels are ambiguous.");
            }
        }
    }

    private static void validateServerSlots(List<ServerOverride> servers, Map<String, Integer> controls,
                                            int inventorySize, int rows, List<String> errors) {
        Map<Integer, String> serverOwners = new LinkedHashMap<>();
        Map<Integer, String> controlOwners = new LinkedHashMap<>();
        controls.forEach((name, slot) -> controlOwners.putIfAbsent(slot, name));

        for (ServerOverride server : servers) {
            String location = server.location() + ".slot";
            Integer slot = integer(server.values().get("slot"), location, -1, errors);
            if (slot == null || slot == -1) {
                continue;
            }
            if (slot < 0 || slot >= inventorySize) {
                errors.add(location + " must be -1 or between 0 and " + (inventorySize - 1)
                        + " for layout.rows = " + rows + "; found " + slot + ".");
                continue;
            }

            String control = controlOwners.get(slot);
            if (control != null) {
                errors.add(location + " " + slot + " collides with " + control + ".");
            }
            String previous = serverOwners.putIfAbsent(slot, location);
            if (previous != null) {
                errors.add(previous + " and " + location + " both use slot " + slot + ".");
            }
        }
    }

    private static void validateServerFields(List<ServerOverride> servers, List<String> errors) {
        for (ServerOverride server : servers) {
            validateString(server.values().get("display_name"), server.location() + ".display_name", errors);
            validateString(server.values().get("description"), server.location() + ".description", errors);
            validateMenuOrder(server.values().get("menu_order"), server.location() + ".menu_order", errors);
            validateBoolean(server.values().get("show_in_menu"), server.location() + ".show_in_menu", errors);
            validateMaterial(server.values().get("material"), server.location() + ".material", errors);
            validateMaterial(server.values().get("unavailable_material"),
                    server.location() + ".unavailable_material", errors);
            validateTemplateField(server.values().get("name"), server.location() + ".name", errors);
            validateLore(server.values().get("lore"), server.location() + ".lore", errors);
        }
    }

    private static void validateStateFields(Object rawStates, List<String> errors) {
        if (rawStates == null) {
            return;
        }
        if (!(rawStates instanceof Map<?, ?> states)) {
            errors.add("states must be a table.");
            return;
        }
        List<Map.Entry<?, ?>> entries = new ArrayList<>(states.entrySet());
        entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey()), String.CASE_INSENSITIVE_ORDER));
        for (Map.Entry<?, ?> entry : entries) {
            String state = sanitizeKey(String.valueOf(entry.getKey()));
            String location = "states.'" + escapedKey(state) + "'";
            if (!SUPPORTED_STATE_KEYS.contains(state)) {
                errors.add(location + " is not a supported menu state. Supported states: full, draining, offline, in_game.");
            }
            if (!(entry.getValue() instanceof Map<?, ?> rawValues)) {
                errors.add(location + " must be a table.");
                continue;
            }
            Map<String, Object> values = stringKeyMap(rawValues);
            validateMaterial(values.get("material"), location + ".material", errors);
            validateTemplateField(values.get("name"), location + ".name", errors);
            validateLore(values.get("lore"), location + ".lore", errors);
        }
    }

    private static void validateString(Object raw, String location, List<String> errors) {
        if (raw != null && !(raw instanceof String)) {
            errors.add(location + " must be a string.");
        }
    }

    private static void validateMenuOrder(Object raw, String location, List<String> errors) {
        if (raw == null) {
            return;
        }
        Integer order = integer(raw, location, null, errors);
        if (order != null && order < -1) {
            errors.add(location + " must be -1 or greater; found " + order + ".");
        }
    }

    private static void validateBoolean(Object raw, String location, List<String> errors) {
        if (raw != null && !(raw instanceof Boolean)) {
            errors.add(location + " must be true or false.");
        }
    }

    private static void validateMaterial(Object raw, String location, List<String> errors) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof String material)) {
            errors.add(location + " must be a string material identifier.");
            return;
        }
        String normalized = material.trim();
        if (!normalized.isEmpty() && !MATERIAL_IDENTIFIER.matcher(normalized).matches()) {
            errors.add(location + " has invalid material identifier '" + singleLine(normalized)
                    + "'. Use a Bukkit identifier such as COMPASS or minecraft:compass.");
        }
    }

    private static void validateLore(Object raw, String location, List<String> errors) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof List<?> lore)) {
            errors.add(location + " must be a list of strings.");
            return;
        }
        for (int index = 0; index < lore.size(); index++) {
            Object line = lore.get(index);
            if (!(line instanceof String)) {
                errors.add(location + "[" + index + "] must be a string.");
                continue;
            }
            validateTemplate((String) line, location + "[" + index + "]", errors);
        }
    }

    private static void validateTemplateField(Object raw, String location, List<String> errors) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof String template)) {
            errors.add(location + " must be a string.");
            return;
        }
        validateTemplate(template, location, errors);
    }

    private static void validateTemplate(String template, String location, List<String> errors) {
        if (template.isEmpty()) {
            return;
        }
        Matcher matcher = CURLY_PLACEHOLDER.matcher(template);
        Set<String> reported = new LinkedHashSet<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (SELECTOR_PLACEHOLDERS.contains(placeholder) || !reported.add(placeholder)) {
                continue;
            }
            errors.add(location + " uses unsupported selector placeholder '{" + singleLine(placeholder)
                    + "}'. Supported placeholders: " + SUPPORTED_PLACEHOLDERS + ".");
        }
    }

    private static Integer integer(Object raw, String location, Integer fallback, List<String> errors) {
        if (raw == null) {
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            errors.add(location + " must be a whole number.");
            return null;
        }
        double decimal = number.doubleValue();
        long integral = number.longValue();
        if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                || integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
            errors.add(location + " must be a whole number.");
            return null;
        }
        return (int) integral;
    }

    private static List<ServerOverride> serverOverrides(Object rawServers, List<String> errors) {
        if (rawServers == null) {
            return List.of();
        }
        if (!(rawServers instanceof Map<?, ?> servers)) {
            errors.add("servers must be a table of server overrides.");
            return List.of();
        }
        List<ServerOverride> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : servers.entrySet()) {
            String name = sanitizeKey(String.valueOf(entry.getKey()));
            ServerOverride server;
            if (entry.getValue() instanceof Map<?, ?> rawValues) {
                server = new ServerOverride(name, stringKeyMap(rawValues));
            } else {
                server = new ServerOverride(name, Map.of());
                errors.add(server.location() + " must be a table of fields.");
            }
            result.add(server);
        }
        result.sort(Comparator.comparing(ServerOverride::normalizedName)
                .thenComparing(ServerOverride::name));
        return result;
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .sorted()
                    .forEach(result::add);
        }
        return result;
    }

    private static Object value(Map<String, Object> root, String... path) {
        Object current = root;
        for (String part : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static String sanitizeKey(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String escapedKey(String value) {
        return singleLine(value).replace("'", "\\'");
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private record ServerOverride(String name, Map<String, Object> values) {
        String normalizedName() {
            return name.trim().toLowerCase(Locale.ROOT);
        }

        String location() {
            return "servers.'" + escapedKey(name) + "'";
        }
    }

    public record Validation(List<String> errors, List<String> warnings) {
        public Validation {
            errors = sortedCopy(errors);
            warnings = sortedCopy(warnings);
        }

        public boolean valid() {
            return errors.isEmpty();
        }

        public String summary() {
            if (valid()) {
                return "Menu configuration validation passed with " + warnings.size() + " warning(s).";
            }
            return "Menu configuration validation found " + errors.size() + " error(s) and "
                    + warnings.size() + " warning(s).";
        }

        private static List<String> sortedCopy(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream().sorted().toList();
        }
    }
}
