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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UpdateCheckerChannelTest {

    private UpdateChecker checker;

    @BeforeEach
    void setUp() {
        checker = new UpdateChecker(
                LoggerFactory.getLogger("update-checker-test"),
                "4.1.0"
        );
    }

    @ParameterizedTest(name = "RELEASE channel, installedIsPrerelease={1}, remoteType={2} → {3}")
    @CsvSource({
            "RELEASE, false, RELEASE, true",
            "RELEASE, false, BETA,   false",
            "RELEASE, false, ALPHA,  false",
            "RELEASE, true,  RELEASE, true",
            "RELEASE, true,  BETA,   false",
            "RELEASE, true,  ALPHA,  false"
    })
    void releaseChannelNeverAllowsPrereleaseRemotes(
            String channel, boolean installedIsPrerelease, String remoteType, boolean expected) {
        boolean result = checker.isAllowed(
                Config.UpdateChannel.valueOf(channel),
                installedIsPrerelease,
                Config.RemoteVersionType.valueOf(remoteType)
        );
        assertEquals(expected, result);
        if (remoteType.equals("BETA") || remoteType.equals("ALPHA")) {
            assertFalse(result, "RELEASE channel must NEVER allow " + remoteType + " remote");
        }
    }

    @ParameterizedTest(name = "BETA channel, installedIsPrerelease={1}, remoteType={2} → {3}")
    @CsvSource({
            "BETA, false, RELEASE, true",
            "BETA, false, BETA,   true",
            "BETA, false, ALPHA,  false",
            "BETA, true,  RELEASE, true",
            "BETA, true,  BETA,   true",
            "BETA, true,  ALPHA,  false"
    })
    void betaChannelAllowsReleaseAndBeta(
            String channel, boolean installedIsPrerelease, String remoteType, boolean expected) {
        boolean result = checker.isAllowed(
                Config.UpdateChannel.valueOf(channel),
                installedIsPrerelease,
                Config.RemoteVersionType.valueOf(remoteType)
        );
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "ALPHA channel, installedIsPrerelease={1}, remoteType={2} → {3}")
    @CsvSource({
            "ALPHA, false, RELEASE, true",
            "ALPHA, false, BETA,   true",
            "ALPHA, false, ALPHA,  true",
            "ALPHA, true,  RELEASE, true",
            "ALPHA, true,  BETA,   true",
            "ALPHA, true,  ALPHA,  true"
    })
    void alphaChannelAllowsEverything(
            String channel, boolean installedIsPrerelease, String remoteType, boolean expected) {
        boolean result = checker.isAllowed(
                Config.UpdateChannel.valueOf(channel),
                installedIsPrerelease,
                Config.RemoteVersionType.valueOf(remoteType)
        );
        assertEquals(expected, result);
    }
}
