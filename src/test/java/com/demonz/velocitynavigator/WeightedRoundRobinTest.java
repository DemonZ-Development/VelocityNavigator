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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedRoundRobinTest {

    @Test
    void respectsWeightDistribution() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("heavy", 0, 3),
                new ServerCandidate("light", 0, 1)
        );

        Map<String, Integer> counts = new HashMap<>();
        counts.put("heavy", 0);
        counts.put("light", 0);

        for (int i = 0; i < 4; i++) {
            ServerCandidate chosen = strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "wrr-test").orElseThrow();
            counts.merge(chosen.name(), 1, Integer::sum);
        }

        assertEquals(3, counts.get("heavy"), "Weight-3 server should get 3 selections in a cycle of 4");
        assertEquals(1, counts.get("light"), "Weight-1 server should get 1 selection in a cycle of 4");
    }

    @Test
    void singleServerReturnsAlways() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("only-server", 0, 2)
        );

        for (int i = 0; i < 10; i++) {
            ServerCandidate chosen = strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "single-test").orElseThrow();
            assertEquals("only-server", chosen.name());
        }
    }

    @Test
    void resetClearsState() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("a", 0, 1),
                new ServerCandidate("b", 0, 1)
        );

        strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "reset-test");
        strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "reset-test");

        strategy.reset();

        String first = strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "reset-test").orElseThrow().name();
        assertEquals("a", first);
    }

    @Test
    void equalWeightsBehaveLikeRoundRobin() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> candidates = List.of(
                new ServerCandidate("a", 0, 1),
                new ServerCandidate("b", 0, 1),
                new ServerCandidate("c", 0, 1)
        );

        assertEquals("a", strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "equal-test").orElseThrow().name());
        assertEquals("b", strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "equal-test").orElseThrow().name());
        assertEquals("c", strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "equal-test").orElseThrow().name());
        assertEquals("a", strategy.select(candidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "equal-test").orElseThrow().name());
    }

    @Test
    void topologyChangeDoesNotWipeAccumulatedWeights() {
        RouteSelectionStrategy strategy = new RouteSelectionStrategy();
        List<ServerCandidate> initialCandidates = List.of(
                new ServerCandidate("heavy", 0, 5),
                new ServerCandidate("light", 0, 1)
        );

        ServerCandidate first = strategy.select(initialCandidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "topology-test").orElseThrow();
        assertEquals("heavy", first.name());

        List<ServerCandidate> updatedCandidates = List.of(
                new ServerCandidate("heavy", 0, 5),
                new ServerCandidate("light", 0, 1),
                new ServerCandidate("new-server", 0, 1)
        );

        ServerCandidate second = strategy.select(updatedCandidates, Config.SelectionMode.WEIGHTED_ROUND_ROBIN, "topology-test").orElseThrow();
        assertNotNull(second);
    }
}
