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
            if (lower.contains("alpha") || lower.contains("snapshot") || lower.contains("dev") || lower.contains("nightly")) {
                return ALPHA;
            }
            if (lower.contains("beta") || lower.contains("rc") || lower.contains("pre")) {
                return BETA;
            }
            return RELEASE;
        }

        public static VersionType resolve(String explicitType, String version) {
            VersionType parsed = fromString(explicitType);
            if (parsed != UNKNOWN) {
                return parsed;
            }
            return inferFromVersion(version);
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
        SemanticVersion[] best = new SemanticVersion[1];
        String[] bestRaw = new String[1];

        if (root.has("changelog") && root.get("changelog").isJsonObject()) {
            JsonObject changelog = root.getAsJsonObject("changelog");
            if (changelog.has("latest") && changelog.get("latest").isJsonObject()) {
                considerObject(changelog.getAsJsonObject("latest"), isAllowed, best, bestRaw);
            }
            if (changelog.has("recent_history") && changelog.get("recent_history").isJsonArray()) {
                for (JsonElement element : changelog.getAsJsonArray("recent_history")) {
                    if (element != null && element.isJsonObject()) {
                        considerObject(element.getAsJsonObject(), isAllowed, best, bestRaw);
                    }
                }
            }
        }

        if (root.has("all_platforms") && root.get("all_platforms").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("all_platforms")) {
                if (element != null && element.isJsonObject()) {
                    considerObject(element.getAsJsonObject(), isAllowed, best, bestRaw);
                }
            }
        }

        if (root.has("platforms") && root.get("platforms").isJsonObject()) {
            JsonObject platforms = root.getAsJsonObject("platforms");
            for (String key : platforms.keySet()) {
                JsonElement elem = platforms.get(key);
                if (elem != null && elem.isJsonObject()) {
                    considerObject(elem.getAsJsonObject(), isAllowed, best, bestRaw);
                } else if (elem != null && elem.isJsonPrimitive()) {
                    considerCandidate(elem.getAsString(), null, isAllowed, best, bestRaw);
                }
            }
        }

        if (root.has("release") && root.get("release").isJsonObject()) {
            considerObject(root.getAsJsonObject("release"), isAllowed, best, bestRaw);
        }

        String latestVersion = stringField(root, "latest_version");
        if (!latestVersion.isBlank()) {
            considerCandidate(latestVersion, stringField(root, "update_type"), isAllowed, best, bestRaw);
        }

        String rootVersion = stringField(root, "version");
        if (!rootVersion.isBlank()) {
            considerCandidate(rootVersion, stringField(root, "type"), isAllowed, best, bestRaw);
        }

        return bestRaw[0] == null ? Optional.empty() : Optional.of(bestRaw[0]);
    }

    private static void considerObject(JsonObject object, Predicate<DemonzRelease> isAllowed, SemanticVersion[] best, String[] bestRaw) {
        String version = stringField(object, "version");
        if (version.isBlank()) {
            version = stringField(object, "version_number");
        }
        if (version.isBlank()) {
            return;
        }
        String typeStr = stringField(object, "version_type");
        if (typeStr.isBlank()) {
            typeStr = stringField(object, "type");
        }
        if (typeStr.isBlank()) {
            typeStr = stringField(object, "channel");
        }
        considerCandidate(version, typeStr, isAllowed, best, bestRaw);
    }

    private static void considerCandidate(String version, String typeStr, Predicate<DemonzRelease> isAllowed, SemanticVersion[] best, String[] bestRaw) {
        if (version == null || version.isBlank()) {
            return;
        }
        VersionType type = VersionType.resolve(typeStr, version);
        DemonzRelease release = new DemonzRelease(version, type);
        if (!isAllowed.test(release)) {
            return;
        }
        SemanticVersion parsed = SemanticVersion.parse(version.toLowerCase(Locale.ROOT));
        if (best[0] == null || parsed.compareTo(best[0]) > 0) {
            best[0] = parsed;
            bestRaw[0] = version;
        }
    }

    private static String stringField(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }
}
