/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.demonz.velocitynavigator.config;
import com.moandjiezana.toml.Toml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TomlReaderUtils {

    private TomlReaderUtils() {
    }

    static int numberValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    static String stringValue(Object value, String fallback) {
        return value instanceof String text ? text : fallback;
    }

    static List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof String text) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    static List<Config.LobbyEntry> readLobbyEntryList(Toml toml, ParseState state, String label, List<Config.LobbyEntry> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<Config.LobbyEntry> entries = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof String text && !text.isBlank()) {
                        entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                    } else if (item instanceof Map<?, ?> map) {
                        entries.add(parseLobbyEntryFromMap(map, label, state));
                    } else {
                        state.warnings.add(label + " contained an unrecognized entry format that was ignored.");
                        state.normalized = true;
                    }
                }
                return entries;
            }
            state.warnings.add(label + " expected a list. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    static Config.LobbyEntry parseLobbyEntryFromMap(Map<?, ?> map, String label, ParseState state) {
        String server = "";
        int maxPlayers = Config.LobbyEntry.UNCAPPED;
        int weight = Config.LobbyEntry.DEFAULT_WEIGHT;

        Object serverObj = map.get("server");
        if (serverObj instanceof String s && !s.isBlank()) {
            server = s.trim();
        } else {
            state.warnings.add(label + " contained a lobby entry without a valid 'server' field.");
            state.normalized = true;
        }

        Object maxObj = map.get("max_players");
        if (maxObj instanceof Number n) {
            maxPlayers = n.intValue();
        }

        Object weightObj = map.get("weight");
        if (weightObj instanceof Number n) {
            weight = n.intValue();
        }

        return new Config.LobbyEntry(server, maxPlayers, weight);
    }

    static Map<String, Config.GroupConfig> readGroupConfigMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Config.GroupConfig> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (key.isBlank()) {
                    continue;
                }

                Object groupValue = entry.getValue();

                if (groupValue instanceof List<?> rawList) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    for (Object item : rawList) {
                        if (item instanceof String text && !text.isBlank()) {
                            entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                        } else if (item instanceof Map<?, ?> map) {
                            entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                        }
                    }
                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, null));
                    }
                    continue;
                }

                if (groupValue instanceof Map<?, ?> groupMap) {
                    List<Config.LobbyEntry> entries = new ArrayList<>();
                    Object serversObj = groupMap.get("servers");
                    if (serversObj instanceof List<?> serversList) {
                        for (Object item : serversList) {
                            if (item instanceof String text && !text.isBlank()) {
                                entries.add(new Config.LobbyEntry(text.trim(), Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
                            } else if (item instanceof Map<?, ?> map) {
                                entries.add(parseLobbyEntryFromMap(map, label + "." + key, state));
                            }
                        }
                    }

                    Config.SelectionMode mode = null;
                    Object modeObj = groupMap.get("mode");
                    if (modeObj instanceof String modeStr && !modeStr.isBlank()) {
                        mode = Config.SelectionMode.fromString(modeStr);
                    }

                    if (!entries.isEmpty()) {
                        result.put(key, new Config.GroupConfig(entries, mode));
                    }
                    continue;
                }

                state.warnings.add(label + "." + key + " expected a list or table and was ignored.");
                state.normalized = true;
            }
            return result;
        }
        return Map.of();
    }

    static String readString(Toml toml, ParseState state, String label, String fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof String text) {
                return text;
            }
            state.warnings.add(label + " expected a string. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    static boolean readBoolean(Toml toml, ParseState state, String label, boolean fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean bool) {
                return bool;
            }
            state.warnings.add(label + " expected true/false. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    static int readInt(Toml toml, ParseState state, String label, int fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    static double readDouble(Toml toml, ParseState state, String label, double fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            state.warnings.add(label + " expected a number. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    static List<String> readStringList(Toml toml, ParseState state, String label, List<String> fallback, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (value == null) {
                continue;
            }
            if (value instanceof List<?> rawList) {
                List<String> cleaned = new ArrayList<>();
                for (Object entry : rawList) {
                    if (entry instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                return cleaned;
            }
            state.warnings.add(label + " expected a list of strings. Using default value.");
            state.normalized = true;
            return fallback;
        }
        return fallback;
    }

    static Map<String, List<String>> readStringListMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (!(entry.getValue() instanceof List<?> rawList)) {
                    state.warnings.add(label + "." + key + " expected a list of strings and was ignored.");
                    state.normalized = true;
                    continue;
                }
                List<String> cleaned = new ArrayList<>();
                for (Object rawItem : rawList) {
                    if (rawItem instanceof String text && !text.isBlank()) {
                        cleaned.add(text.trim());
                    } else {
                        state.warnings.add(label + "." + key + " contained a non-string entry that was ignored.");
                        state.normalized = true;
                    }
                }
                values.put(key, cleaned);
            }
            return values;
        }
        return Map.of();
    }

    static Map<String, String> readStringMap(Toml toml, ParseState state, String label, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                if (entry.getValue() instanceof String text && !text.isBlank()) {
                    values.put(key, text.trim().toLowerCase(Locale.ROOT));
                } else {
                    state.warnings.add(label + "." + key + " expected a string and was ignored.");
                    state.normalized = true;
                }
            }
            return values;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> readStringMapOfLists(Toml toml, ParseState state, String label, Map<String, List<String>> defaultValue, String... paths) {
        for (String path : paths) {
            Object value = rawValue(toml, path);
            if (!(value instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, List<String>> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = sanitizeMapKey(String.valueOf(entry.getKey())).toLowerCase(Locale.ROOT);
                Object val = entry.getValue();
                if (val instanceof List<?> list) {
                    List<String> stringList = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof String s && !s.isBlank()) {
                            stringList.add(s.trim().toLowerCase(Locale.ROOT));
                        }
                    }
                    values.put(key, stringList);
                } else if (val instanceof String s && !s.isBlank()) {
                    values.put(key, List.of(s.trim().toLowerCase(Locale.ROOT)));
                } else {
                    state.warnings.add(label + "." + key + " expected a list of strings and was ignored.");
                    state.normalized = true;
                }
            }
            return values;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    static Object rawValue(Toml toml, String path) {
        Object current = toml.toMap();
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    static String sanitizeMapKey(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
