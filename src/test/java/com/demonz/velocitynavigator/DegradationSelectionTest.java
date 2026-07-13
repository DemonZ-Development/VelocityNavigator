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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DegradationSelectionTest {

    @Test
    void leastPlayersDegradationUsesLivePlayerCounts() {
        Map<String, Integer> players = Map.of("lobby-a", 18, "lobby-b", 3, "lobby-c", 11);

        assertEquals("lobby-b", LobbyCommand.pickLeastPlayers(
                List.of("lobby-a", "lobby-b", "lobby-c"),
                name -> players.getOrDefault(name, Integer.MAX_VALUE)));
    }

    @Test
    void leastPlayersDegradationBreaksTiesConsistently() {
        assertEquals("lobby-a", LobbyCommand.pickLeastPlayers(
                List.of("lobby-b", "lobby-a"),
                ignored -> 4));
        assertNull(LobbyCommand.pickLeastPlayers(List.of(), ignored -> 0));
    }
}
