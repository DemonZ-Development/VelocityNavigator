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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteSelectionStrategyTest {

    @Test
    void leastPlayersPrefersTheSmallestServer() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        Optional<ServerCandidate> chosen = strategy.select(
                List.of(
                        new ServerCandidate("lobby-1", 30),
                        new ServerCandidate("lobby-2", 10),
                        new ServerCandidate("lobby-3", 20)
                ),
                Config.SelectionMode.LEAST_PLAYERS,
                "default"
        );

        assertTrue(chosen.isPresent());
        assertEquals("lobby-2", chosen.get().name());
    }

    @Test
    void roundRobinCyclesPerGroup() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("lobby-1", 10),
                new ServerCandidate("lobby-2", 10),
                new ServerCandidate("lobby-3", 10)
        );

        assertEquals("lobby-1", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-3", strategy.select(candidates, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
    }

    @Test
    void userScenarioTest() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();

        List<ServerCandidate> servers = List.of(
                new ServerCandidate("lobby-1", 0),
                new ServerCandidate("lobby-2", 0)
        );

        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-1", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());
        assertEquals("lobby-2", strategy.select(servers, Config.SelectionMode.ROUND_ROBIN, "default").orElseThrow().name());

        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 0), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 0)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        assertEquals("lobby-1", strategy.select(List.of(
            new ServerCandidate("lobby-1", 1), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());

        assertEquals("lobby-2", strategy.select(List.of(
            new ServerCandidate("lobby-1", 2), new ServerCandidate("lobby-2", 1)
        ), Config.SelectionMode.LEAST_PLAYERS, "default").orElseThrow().name());
    }
}
