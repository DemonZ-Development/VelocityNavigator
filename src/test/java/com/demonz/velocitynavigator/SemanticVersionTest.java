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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void comparesDoubleDigitVersionsCorrectly() {
        SemanticVersion older = SemanticVersion.parse("2.2.0-STABLE");
        SemanticVersion newer = SemanticVersion.parse("2.10.0-STABLE");

        assertTrue(newer.compareTo(older) > 0);
    }

    @Test
    void ordersReleaseTypesCorrectly() {
        SemanticVersion alpha = SemanticVersion.parse("3.0.0-ALPHA");
        SemanticVersion beta = SemanticVersion.parse("3.0.0-BETA");
        SemanticVersion rc = SemanticVersion.parse("3.0.0-RC1");
        SemanticVersion stable = SemanticVersion.parse("3.0.0-STABLE");
        SemanticVersion release = SemanticVersion.parse("3.0.0");

        assertTrue(beta.compareTo(alpha) > 0);
        assertTrue(rc.compareTo(beta) > 0);
        assertTrue(stable.compareTo(rc) > 0);
        assertTrue(release.compareTo(stable) > 0);
        assertTrue(rc.isPrerelease());
    }

    @Test
    void parseRejectsGarbagePrefixedStrings() {
        SemanticVersion v = SemanticVersion.parse("evil3.0.0injection");

        SemanticVersion expected = SemanticVersion.parse("0.0.0-ALPHA");
        assertEquals(0, v.compareTo(expected),
                "Garbage-prefixed string should parse as 0.0.0 ALPHA, not 3.0.0");
        assertTrue(v.isPrerelease(), "Fallback version should be ALPHA (prerelease)");
    }

    @Test
    void parseAcceptsCleanVersionStrings() {
        SemanticVersion v = SemanticVersion.parse("3.1.2");

        SemanticVersion baseline = SemanticVersion.parse("3.1.1");
        assertTrue(v.compareTo(baseline) > 0, "3.1.2 should be greater than 3.1.1");

        SemanticVersion same = SemanticVersion.parse("3.1.2");
        assertEquals(0, v.compareTo(same), "Same version strings should compare as equal");
    }

    @Test
    void parseAnchoredRejectsPartialMatch() {
        String[] garbageInputs = {"", "   ", "abc", "ver2.0.0", "release-1.0.0"};

        SemanticVersion fallback = SemanticVersion.parse("0.0.0-ALPHA");
        for (String input : garbageInputs) {
            SemanticVersion v = SemanticVersion.parse(input);
            assertEquals(0, v.compareTo(fallback),
                     "Input '" + input + "' should parse as 0.0.0 ALPHA");
        }
    }

    @Test
    void parseAcceptsVAndvPrefixedVersions() {
        SemanticVersion lowercaseV = SemanticVersion.parse("v3.0.0");
        SemanticVersion uppercaseV = SemanticVersion.parse("V3.0.0");
        SemanticVersion standard = SemanticVersion.parse("3.0.0");

        assertEquals(0, lowercaseV.compareTo(standard), "v3.0.0 should be parsed as 3.0.0");
        assertEquals(0, uppercaseV.compareTo(standard), "V3.0.0 should be parsed as 3.0.0");
    }
}
