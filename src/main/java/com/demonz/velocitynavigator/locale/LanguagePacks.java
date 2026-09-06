/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.locale;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LanguagePacks {

    private static final String RESOURCE_DIR = "languages/";
    private static final String EXTENSION = ".properties";

    private static final List<String> SUPPORTED = List.of(
            "en", "ru", "es", "fr", "de", "pt_br", "zh_cn",
            "ja", "it", "ko", "nl", "pl", "tr", "ar", "hi"
    );

    private static final Set<String> SUPPORTED_SET = new LinkedHashSet<>(SUPPORTED);
    private static final Map<String, LanguageBundle> CACHE = new ConcurrentHashMap<>();

    private LanguagePacks() {
    }

    public static boolean isSupported(String code) {
        return code != null && SUPPORTED_SET.contains(normalize(code));
    }

    public static Set<String> supportedCodes() {
        return new LinkedHashSet<>(SUPPORTED);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static LanguageBundle bundle(String code) {
        return bundle(code, null);
    }

    public static LanguageBundle bundle(String code, java.nio.file.Path externalDir) {
        String normalized = normalize(code);
        if (externalDir != null) {
            return loadFromResource(normalized, externalDir);
        }
        return CACHE.computeIfAbsent(normalized, k -> loadFromResource(k, externalDir));
    }

    private static LanguageBundle loadFromResource(String code, java.nio.file.Path externalDir) {
        LanguageBundle english = LanguageBundle.defaults();
        String resourcePath = RESOURCE_DIR + code + EXTENSION;

        Map<String, String> strings = new LinkedHashMap<>(english.strings());
        Map<String, List<String>> lists = new LinkedHashMap<>();
        Set<String> overriddenLists = new LinkedHashSet<>();
        english.lists().forEach((k, v) -> lists.put(k, new ArrayList<>(v)));

        InputStream in = null;
        try {
            if (externalDir != null) {
                java.nio.file.Path extPath = externalDir.resolve("languages").resolve(code + EXTENSION);
                if (java.nio.file.Files.exists(extPath)) {
                    in = java.nio.file.Files.newInputStream(extPath);
                }
            }
            if (in == null) {
                in = LanguagePacks.class.getClassLoader().getResourceAsStream(resourcePath);
            }
            if (in == null) {
                return new LanguageBundle(code, code, strings, lists);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.trim().startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1);

                int lastDot = key.lastIndexOf('.');
                if (lastDot > 0) {
                    String suffix = key.substring(lastDot + 1);
                    String listKey = key.substring(0, lastDot);
                    boolean isNumericSuffix = suffix.chars().allMatch(Character::isDigit);
                    if (isNumericSuffix && english.lists().containsKey(listKey)) {
                        if (overriddenLists.add(listKey)) {
                            lists.put(listKey, new ArrayList<>());
                        }
                        lists.computeIfAbsent(listKey, k -> new ArrayList<>()).add(value);
                        continue;
                    }
                }

                strings.put(key, value.trim());
            }
        } catch (IOException ignored) {
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }

        Map<String, List<String>> finalLists = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            finalLists.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return new LanguageBundle(code, code, strings, finalLists);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
