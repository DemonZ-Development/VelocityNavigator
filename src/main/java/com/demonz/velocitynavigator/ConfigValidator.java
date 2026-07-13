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
package com.demonz.velocitynavigator;

import com.moandjiezana.toml.Toml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static List<String> validate(Config config, Toml toml) {
        List<String> warnings = new ArrayList<>();
        if (toml == null) {
            return warnings;
        }

        List<String> selectionModes = Config.SelectionMode.allConfigValues();
        validateKey(toml, "routing.selection_mode", selectionModes, warnings);
        validateKey(toml, "routing.java_menu.type", List.of("inventory", "chat"), warnings);

        List<String> channels = Config.UpdateChannel.allConfigValues();
        validateKey(toml, "update_checker.channel", channels, warnings);

        List<String> degradationModes = List.of("random", "round_robin", "least_players");
        validateKey(toml, "degradation.mode", degradationModes, warnings);

        List<String> strategies = List.of("disconnect", "fallback_server");
        validateKey(toml, "lobby.no_server_strategy", strategies, warnings);

        List<String> formattingOptions = List.of("auto", "minimessage", "legacy");
        validateKey(toml, "messages.formatting", formattingOptions, warnings);

        return warnings;
    }

    private static void validateKey(Toml toml, String path, List<String> validOptions, List<String> warnings) {
        Object rawVal = rawValue(toml, path);
        if (rawVal == null) {
            return;
        }
        if (!(rawVal instanceof String raw)) {
            warnings.add(String.format("Value at '%s' is not a string.", path));
            return;
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT);
        if (!validOptions.contains(cleaned)) {
            String suggestion = getSuggestion(cleaned, validOptions);
            warnings.add(String.format("Unknown value '%s' for key '%s'. %s", raw, path, suggestion));
        }
    }

    @SuppressWarnings("unchecked")
    private static Object rawValue(Toml toml, String path) {
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

    static String getSuggestion(String input, List<String> validOptions) {
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String option : validOptions) {
            int distance = levenshtein(input, option);
            if (distance < minDistance) {
                minDistance = distance;
                closest = option;
            }
        }
        if (minDistance <= 2 && closest != null) {
            return "Did you mean '" + closest + "'?";
        } else {
            return "Valid options are: " + String.join(", ", validOptions);
        }
    }

    static int levenshtein(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return Integer.MAX_VALUE;
        }
        int[] dp = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) {
            dp[j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            int prev = dp[0];
            dp[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int temp = dp[j];
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[j] = prev;
                } else {
                    dp[j] = Math.min(Math.min(dp[j - 1], dp[j]), prev) + 1;
                }
                prev = temp;
            }
        }
        return dp[s2.length()];
    }
}
