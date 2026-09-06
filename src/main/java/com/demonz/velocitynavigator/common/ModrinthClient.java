/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class ModrinthClient {

    public static final String API_URL = "https://api.modrinth.com/v2/project/velocitynavigator/version";
    public static final String DOWNLOAD_URL = "https://modrinth.com/plugin/velocitynavigator";

    private ModrinthClient() {
    }

    public enum VersionType {
        RELEASE, BETA, ALPHA, UNKNOWN;

        public static VersionType fromString(String value) {
            if (value == null) return UNKNOWN;
            String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
            switch (normalized) {
                case "RELEASE":
                    return RELEASE;
                case "BETA":
                    return BETA;
                case "ALPHA":
                    return ALPHA;
                default:
                    return UNKNOWN;
            }
        }
    }

    public record ModrinthRelease(String versionNumber, VersionType type) {
    }

    public static String userAgent(String currentVersion, boolean backend) {
        String label = backend ? "VelocityNavigator-Backend" : "VelocityNavigator";
        return "DemonZDevelopment/" + label + "/" + currentVersion + " (" + DOWNLOAD_URL + ")";
    }

    public static ModrinthRelease parseRelease(JsonObject object) {
        String versionNumber = stringField(object, "version_number");
        VersionType type = VersionType.fromString(stringField(object, "version_type"));
        return new ModrinthRelease(versionNumber, type);
    }

    public static Optional<String> bestVersionIn(JsonArray versions, Predicate<ModrinthRelease> isAllowed) {
        if (versions == null || isAllowed == null) return Optional.empty();
        SemanticVersion best = null;
        String bestRaw = null;
        for (JsonElement element : versions) {
            if (element == null || !element.isJsonObject()) continue;
            ModrinthRelease release = parseRelease(element.getAsJsonObject());
            if (!isAllowed.test(release)) continue;
            if (release.versionNumber() == null || release.versionNumber().isBlank()) continue;
            SemanticVersion parsed = SemanticVersion.parse(release.versionNumber().toLowerCase(Locale.ROOT));
            if (best == null || parsed.compareTo(best) > 0) {
                best = parsed;
                bestRaw = release.versionNumber();
            }
        }
        return bestRaw == null ? Optional.empty() : Optional.of(bestRaw);
    }

    private static String stringField(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }
}
