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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyEntryTest {

    @Test
    void uncappedByDefault() {
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", -5, 1);
        assertEquals(Config.LobbyEntry.UNCAPPED, entry.maxPlayers(),
                "Negative maxPlayers should be normalized to UNCAPPED");
    }

    @Test
    void isFullReturnsTrueWhenAtCap() {
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", 50, 1);

        assertTrue(entry.isFull(50), "Should be full when currentPlayers == maxPlayers");
        assertTrue(entry.isFull(60), "Should be full when currentPlayers > maxPlayers");
        assertFalse(entry.isFull(49), "Should not be full when currentPlayers < maxPlayers");
    }

    @Test
    void isFullReturnsFalseWhenUncapped() {
        Config.LobbyEntry entry = new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, 1);

        assertFalse(entry.isFull(0), "UNCAPPED should never be full");
        assertFalse(entry.isFull(1000), "UNCAPPED should never be full, even with many players");
    }

    @Test
    void defaultWeightIsOne() {
        Config.LobbyEntry entryZero = new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, 0);
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entryZero.weight(),
                "Zero weight should default to DEFAULT_WEIGHT (1)");

        Config.LobbyEntry entryNeg = new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, -5);
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entryNeg.weight(),
                "Negative weight should default to DEFAULT_WEIGHT (1)");
    }

    @Test
    void negativeValuesDefaultToConstants() {
        Config.LobbyEntry entry = new Config.LobbyEntry("test", -1, 0);

        assertEquals(Config.LobbyEntry.UNCAPPED, entry.maxPlayers(),
                "Negative max_players should be UNCAPPED (-1)");
        assertEquals(Config.LobbyEntry.DEFAULT_WEIGHT, entry.weight(),
                "Zero/negative weight should be DEFAULT_WEIGHT (1)");
    }
}
