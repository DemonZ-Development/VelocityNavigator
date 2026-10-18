package com.demonz.velocitynavigator.common;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class DemonzClient {

    public static final String API_BASE = "https://demonz.org/api";
    public static final String UPDATES_URL = "https://demonz.org/api/projects/velocitynavigator/updates";
    public static final String PROJECT_URL = "https://demonz.org/projects/velocitynavigator";
    public static final String DOWNLOAD_URL = "https://demonz.org/api/projects/download/velocitynavigator";

    private DemonzClient() {
    }

    public enum VersionType {
        RELEASE, BETA, ALPHA, UNKNOWN;

        public static VersionType fromString(String value) {
            if (value == null) {
                return UNKNOWN;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "RELEASE" -> RELEASE;
                case "BETA" -> BETA;
                case "ALPHA" -> ALPHA;
                default -> UNKNOWN;
            };
        }

        public static VersionType inferFromVersion(String version) {
            if (version == null || version.isBlank()) {
                return UNKNOWN;
            }
            String lower = version.toLowerCase(Locale.ROOT);
            if (lower.contains("alpha")) {
                return ALPHA;
            }
            if (lower.contains("beta")) {
                return BETA;
            }
            return RELEASE;
        }
    }

    public record DemonzRelease(String versionNumber, VersionType type) {
    }

    public static String userAgent(String currentVersion, boolean backend) {
        String label = backend ? "VelocityNavigator-Backend" : "VelocityNavigator";
        return "DemonZDevelopment/" + label + "/" + currentVersion + " (" + PROJECT_URL + ")";
    }

    public static Optional<String> bestVersionIn(JsonObject root, Predicate<DemonzRelease> isAllowed) {
        if (root == null || isAllowed == null) {
            return Optional.empty();
        }
        SemanticVersion best = null;
        String bestRaw = null;

        if (root.has("changelog") && root.get("changelog").isJsonObject()) {
            JsonObject changelog = root.getAsJsonObject("changelog");
            if (changelog.has("recent_history") && changelog.get("recent_history").isJsonArray()) {
                JsonArray history = changelog.getAsJsonArray("recent_history");
                for (JsonElement element : history) {
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = element.getAsJsonObject();
                    String version = stringField(item, "version");
                    if (version.isBlank()) {
                        continue;
                    }
                    VersionType type = VersionType.inferFromVersion(version);
                    DemonzRelease release = new DemonzRelease(version, type);
                    if (!isAllowed.test(release)) {
                        continue;
                    }
                    SemanticVersion parsed = SemanticVersion.parse(version.toLowerCase(Locale.ROOT));
                    if (best == null || parsed.compareTo(best) > 0) {
                        best = parsed;
                        bestRaw = version;
                    }
                }
            }
        }

        String latestVersion = stringField(root, "latest_version");
        if (!latestVersion.isBlank()) {
            VersionType type = VersionType.inferFromVersion(latestVersion);
            DemonzRelease release = new DemonzRelease(latestVersion, type);
            if (isAllowed.test(release)) {
                SemanticVersion parsed = SemanticVersion.parse(latestVersion.toLowerCase(Locale.ROOT));
                if (best == null || parsed.compareTo(best) > 0) {
                    best = parsed;
                    bestRaw = latestVersion;
                }
            }
        }

        if (root.has("release") && root.get("release").isJsonObject()) {
            JsonObject releaseObject = root.getAsJsonObject("release");
            String releaseVersion = stringField(releaseObject, "version");
            if (!releaseVersion.isBlank()) {
                VersionType type = VersionType.inferFromVersion(releaseVersion);
                DemonzRelease release = new DemonzRelease(releaseVersion, type);
                if (isAllowed.test(release)) {
                    SemanticVersion parsed = SemanticVersion.parse(releaseVersion.toLowerCase(Locale.ROOT));
                    if (best == null || parsed.compareTo(best) > 0) {
                        best = parsed;
                        bestRaw = releaseVersion;
                    }
                }
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
