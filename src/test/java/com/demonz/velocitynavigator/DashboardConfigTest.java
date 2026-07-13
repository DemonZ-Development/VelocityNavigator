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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardConfigTest {

    @Test
    void disabledFactoryReturnsDisabledSettings() {
        Config.DashboardSettings s = Config.DashboardSettings.disabled();
        assertFalse(s.enabled());
        assertEquals(9226, s.port());
        assertEquals("127.0.0.1", s.bindHost());
        assertTrue(s.bearerToken().isEmpty());
        assertEquals(5, s.refreshSeconds());
    }

    @Test
    void portIsClampedToValidRange() {
        Config.DashboardSettings tooLow = new Config.DashboardSettings(true, -1, "127.0.0.1", "");
        assertEquals(9226, tooLow.port());
        Config.DashboardSettings tooHigh = new Config.DashboardSettings(true, 70000, "127.0.0.1", "");
        assertEquals(9226, tooHigh.port());
        Config.DashboardSettings valid = new Config.DashboardSettings(true, 8080, "127.0.0.1", "");
        assertEquals(8080, valid.port());
    }

    @Test
    void bindHostDefaultsToLoopbackWhenBlank() {
        Config.DashboardSettings blankHost = new Config.DashboardSettings(true, 9226, "", "");
        assertEquals("127.0.0.1", blankHost.bindHost());
        Config.DashboardSettings nullHost = new Config.DashboardSettings(true, 9226, null, "");
        assertEquals("127.0.0.1", nullHost.bindHost());
    }

    @Test
    void refreshSecondsIsClampedToAtLeastTwo() {
        Config.DashboardSettings tooFast = new Config.DashboardSettings(true, 9226, "127.0.0.1", "", 0);
        assertEquals(2, tooFast.refreshSeconds());
        Config.DashboardSettings ok = new Config.DashboardSettings(true, 9226, "127.0.0.1", "", 10);
        assertEquals(10, ok.refreshSeconds());
    }

    @Test
    void bearerTokenDefaultsToEmptyWhenNull() {
        Config.DashboardSettings nullToken = new Config.DashboardSettings(true, 9226, "127.0.0.1", null);
        assertTrue(nullToken.bearerToken().isEmpty());
    }

    @Test
    void fourArgConstructorDefaultsRefreshToFive() {
        Config.DashboardSettings s = new Config.DashboardSettings(true, 9226, "127.0.0.1", "secret");
        assertEquals(5, s.refreshSeconds());
        assertEquals("secret", s.bearerToken());
    }

    @Test
    void configDefaultsIncludeDisabledDashboard() {
        Config defaults = Config.defaults();
        assertFalse(defaults.dashboard().enabled());
        assertEquals(9226, defaults.dashboard().port());
    }

    @Test
    void updateCheckerSilentDefaultsToTrue() {
        Config.UpdateCheckerSettings s = new Config.UpdateCheckerSettings(true, Config.UpdateChannel.RELEASE, 60, true);
        assertTrue(s.silent(), "4-arg constructor should default silent=true");
    }

    @Test
    void dashboardTracksRuntimeLobbiesAlongsideConfiguredLobbies() {
        Set<String> tracked = DashboardServer.trackedServers(
                Config.defaults(),
                Set.of("redis-lobby", "managed-lobby"));

        assertTrue(tracked.contains("lobby-1"));
        assertTrue(tracked.contains("lobby-2"));
        assertTrue(tracked.contains("redis-lobby"));
        assertTrue(tracked.contains("managed-lobby"));
    }
}
