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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LegacyColorConverter {

    private static final Pattern LEGACY_PATTERN = Pattern.compile(
            "(?i)(?:&(?!lt;|gt;|amp;|quot;|apos;)|§)([0-9a-fk-or])");
    private static final Pattern INLINE_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile("(?i)(?:&|§)x((?:(?:&|§)[0-9a-f]){6})");

    private static final Map<Character, String> CONVERSIONS = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private LegacyColorConverter() {
    }

    public static boolean hasLegacyCodes(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return LEGACY_PATTERN.matcher(input).find()
                || INLINE_HEX_PATTERN.matcher(input).find()
                || BUNGEE_HEX_PATTERN.matcher(input).find();
    }

    public static String convert(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String converted = replaceBungeeHex(input);
        Matcher inlineHex = INLINE_HEX_PATTERN.matcher(converted);
        StringBuffer hexBuffer = new StringBuffer();
        while (inlineHex.find()) {
            inlineHex.appendReplacement(hexBuffer, Matcher.quoteReplacement("<#" + inlineHex.group(1) + ">"));
        }
        inlineHex.appendTail(hexBuffer);

        Matcher matcher = LEGACY_PATTERN.matcher(hexBuffer.toString());
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = CONVERSIONS.get(code);
            if (replacement != null) {
                matcher.appendReplacement(sb, replacement);
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceBungeeHex(String input) {
        Matcher matcher = BUNGEE_HEX_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1).replace("&", "").replace("§", "");
            matcher.appendReplacement(result, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
