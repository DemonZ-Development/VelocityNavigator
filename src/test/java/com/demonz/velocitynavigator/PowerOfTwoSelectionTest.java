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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerOfTwoSelectionTest {

    @Test
    void selectsLessLoadedOfTwoRandom() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("heavy", 50),
                new ServerCandidate("light", 5)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("light", chosen.get().name(),
                "With 2 candidates, should always pick the less loaded");
    }

    @Test
    void fallsBackForSingleCandidate() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("only-one", 42)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("only-one", chosen.get().name());
    }

    @Test
    void fallsBackForTwoCandidates() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("lobby-1", 10),
                new ServerCandidate("lobby-2", 5)
        );

        Optional<ServerCandidate> chosen = strategy.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isPresent());
        assertEquals("lobby-2", chosen.get().name(),
                "With 2 candidates, should pick the less loaded");
    }

    @Test
    void distributionIsBetterThanRandom() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("server-a", 5),
                new ServerCandidate("server-b", 10),
                new ServerCandidate("server-c", 20),
                new ServerCandidate("server-d", 40)
        );

        Map<String, Integer> counts = new HashMap<>();
        for (String name : List.of("server-a", "server-b", "server-c", "server-d")) {
            counts.put(name, 0);
        }

        int iterations = 1000;
        for (int i = 0; i < iterations; i++) {
            RouteSelectionStrategy fresh = new RouteSelectionStrategy();
            Optional<ServerCandidate> chosen = fresh.select(candidates, Config.SelectionMode.POWER_OF_TWO, "test-" + i);
            assertTrue(chosen.isPresent());
            counts.merge(chosen.get().name(), 1, Integer::sum);
        }

        int lightestCount = counts.get("server-a");
        int heaviestCount = counts.get("server-d");

        double idealPerServer = iterations / 4.0;
        double maxAllowedDeviation = idealPerServer * 0.50;

        assertTrue(heaviestCount < idealPerServer,
                "Heaviest server should get less than even distribution; got " + heaviestCount);
        assertTrue(lightestCount > idealPerServer,
                "Lightest server should get more than even distribution; got " + lightestCount);
    }

    @Test
    void handlesEmptyCandidates() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        Optional<ServerCandidate> chosen = strategy.select(List.of(), Config.SelectionMode.POWER_OF_TWO, "test");
        assertTrue(chosen.isEmpty());
    }
}
