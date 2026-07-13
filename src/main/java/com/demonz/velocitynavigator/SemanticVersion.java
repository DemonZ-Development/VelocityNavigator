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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemanticVersion implements Comparable<SemanticVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(.*)?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final Qualifier qualifier;

    private SemanticVersion(int major, int minor, int patch, Qualifier qualifier) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
    }

    public static SemanticVersion parse(String raw) {
        String input = raw == null ? "" : raw.trim();
        if (input.startsWith("v") || input.startsWith("V")) {
            input = input.substring(1).trim();
        }
        Matcher matcher = VERSION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            return new SemanticVersion(0, 0, 0, Qualifier.ALPHA);
        }

        int major = parsePart(matcher.group(1));
        int minor = parsePart(matcher.group(2));
        int patch = parsePart(matcher.group(3));
        String suffix = matcher.group(4) == null ? "" : matcher.group(4).trim();
        return new SemanticVersion(major, minor, patch, Qualifier.fromSuffix(suffix));
    }

    public boolean isPrerelease() {
        return qualifier == Qualifier.RC || qualifier == Qualifier.BETA || qualifier == Qualifier.ALPHA;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }
        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }
        int patchCompare = Integer.compare(patch, other.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }
        return Integer.compare(qualifier.rank, other.qualifier.rank);
    }

    private static int parsePart(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        return Integer.parseInt(raw);
    }

    private enum Qualifier {
        ALPHA(0),
        BETA(1),
        RC(2),
        STABLE(3),
        RELEASE(4);

        private final int rank;

        Qualifier(int rank) {
            this.rank = rank;
        }

        private static Qualifier fromSuffix(String suffix) {
            if (suffix == null || suffix.isBlank()) {
                return RELEASE;
            }
            String normalized = suffix.toLowerCase(Locale.ROOT);
            if (normalized.contains("alpha")) {
                return ALPHA;
            }
            if (normalized.contains("beta")) {
                return BETA;
            }
            if (normalized.matches(".*(?:^|[-_.])rc\\d*.*")) {
                return RC;
            }
            if (normalized.contains("stable")) {
                return STABLE;
            }
            return RELEASE;
        }
    }
}

