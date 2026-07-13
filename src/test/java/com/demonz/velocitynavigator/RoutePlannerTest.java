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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {

    private static Config.Routing defaultRouting() {
        return Config.defaults().routing();
    }

    private static Config baseConfig(Config.Routing routing) {
        Config d = Config.defaults();
        return new Config(
                Config.CURRENT_VERSION,
                d.commands(),
                routing,
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                d.notifyOnStartup(),
                d.notifyAdminsOnJoin()
        );
    }

    private static Config baseConfig(Config.Routing routing, Config.LobbyFallbackSettings lobbyFallback) {
        Config d = Config.defaults();
        return new Config(
                Config.CURRENT_VERSION,
                d.commands(),
                routing,
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                d.notifyOnStartup(),
                d.notifyAdminsOnJoin(),
                d.startup(),
                lobbyFallback,
                d.bedrock()
        );
    }

    @Test
    void fallsBackToDefaultLobbiesWhenContextualGroupIsOffline() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(new Config.LobbyEntry("bedwars-lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                null
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("lobby-1", 4));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("default", decision.usedGroup());
        assertEquals("lobby-1", decision.selectedServer());
        assertTrue(decision.fallbackToDefault());
    }

    @Test
    void routesCorrectlyWithMixedCaseSourceServer() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("Lobby-1", config, Map.of("Lobby-1", 10, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
    }

    @Test
    void contextualRoutingMatchesWithMixedCaseSourceServer() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(
                                        new Config.LobbyEntry("bw-lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                                        new Config.LobbyEntry("bw-lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                                ),
                                null
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("Bedwars-1", config, Map.of("bw-lobby-1", 3, "bw-lobby-2", 7));

        assertEquals("bedwars", decision.requestedGroup());
        assertEquals("bedwars", decision.usedGroup());
        assertTrue(decision.hasSelection());
        assertEquals("bw-lobby-1", decision.selectedServer());
        assertFalse(decision.fallbackToDefault());
    }

    @Test
    void cycleWhenPossibleWorksWithSingleLobby() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                true,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("lobby-1", config, Map.of("lobby-1", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-1", decision.selectedServer());
    }


    @Test
    void drainedServersExcludedFromCandidates() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        DrainService drainService = new DrainService();
        drainService.drain("lobby-1");
        planner.setDrainService(drainService);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 5, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void circuitBreakerOpenServersExcluded() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);
        breaker.recordFailure("lobby-1");
        breaker.recordFailure("lobby-1");
        breaker.recordFailure("lobby-1");
        planner.setCircuitBreaker(breaker);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 5, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void perGroupSelectionModeOverride() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        false,
                        Map.of("bedwars", new Config.GroupConfig(
                                List.of(
                                        new Config.LobbyEntry("bw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                                        new Config.LobbyEntry("bw-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                                ),
                                Config.SelectionMode.ROUND_ROBIN
                        )),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of()
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("bw-1", 10, "bw-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals(Config.SelectionMode.ROUND_ROBIN, decision.selectionMode());
        assertEquals("bedwars", decision.usedGroup());
    }

    @Test
    void fallbackChainWalksCorrectly() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                new Config.Contextual(
                        true,
                        true,
                        Map.of(
                                "bedwars", new Config.GroupConfig(
                                        List.of(new Config.LobbyEntry("bw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                ),
                                "skywars", new Config.GroupConfig(
                                        List.of(new Config.LobbyEntry("sw-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                                        null
                                )
                        ),
                        Map.of("bedwars-1", "bedwars"),
                        Map.of("bedwars", List.of("skywars"))
                ),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("bedwars-1", config, Map.of("sw-1", 5));

        assertTrue(decision.hasSelection());
        assertEquals("sw-1", decision.selectedServer());
        assertTrue(decision.fallbackToDefault());
    }

    @Test
    void playerAffinityPreferredWhenAvailable() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        PlayerAffinityService affinityService = new PlayerAffinityService(1.0);
        UUID playerId = UUID.randomUUID();
        affinityService.setAffinity(playerId, "lobby-2");
        planner.setAffinityService(affinityService);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 0, "lobby-2", 50), playerId);

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
    }

    @Test
    void lobbyEntryCapExcludesFullServers() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", 10, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 10, "lobby-2", 5));

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertFalse(decision.onlineCandidates().contains("lobby-1"));
    }

    @Test
    void consistentHashFallsBackToLeastPlayersWhenPlayerIdIsNull() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.CONSISTENT_HASH,
                false,
                true,
                List.of(
                        new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT),
                        new Config.LobbyEntry("lobby-2", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)
                ),
                defaultRouting().contextual(),
                2,
                null
        ));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        ConsistentHashRing hashRing = new ConsistentHashRing();
        planner.setHashRing(hashRing);

        RouteDecision decision = planner.plan("", config, Map.of("lobby-1", 10, "lobby-2", 5), null);

        assertTrue(decision.hasSelection());
        assertEquals("lobby-2", decision.selectedServer());
        assertTrue(decision.reason().contains("Consistent hash selection was unavailable or failed; fell back to LEAST_PLAYERS"));
    }

    @Test
    void fallbackServerIsSelectedOnlyWhenOnline() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                defaultRouting().contextual(),
                2,
                null
        ), new Config.LobbyFallbackSettings("fallback_server", "<red>No lobby available.</red>", "backup-lobby"));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("", config, Map.of("backup-lobby", 0));

        assertTrue(decision.hasSelection());
        assertEquals("backup-lobby", decision.selectedServer());
    }

    @Test
    void fallbackServerIsNotSelectedWhenUnavailable() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                defaultRouting().contextual(),
                2,
                null
        ), new Config.LobbyFallbackSettings("fallback_server", "<red>No lobby available.</red>", "backup-lobby"));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        RouteDecision decision = planner.plan("", config, Map.of());

        assertFalse(decision.hasSelection());
    }

    @Test
    void fallbackServerIsIncludedInInspectionTargets() {
        Config config = baseConfig(new Config.Routing(
                Config.SelectionMode.LEAST_PLAYERS,
                false,
                true,
                List.of(new Config.LobbyEntry("lobby-1", Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT)),
                defaultRouting().contextual(),
                2,
                null
        ), new Config.LobbyFallbackSettings("fallback_server", "<red>No lobby available.</red>", "backup-lobby"));

        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());

        assertTrue(planner.inspectionTargets("", config).contains("backup-lobby"));
    }

    @Test
    void redisRegisteredServerJoinsLiveDefaultPool() {
        Config config = Config.defaults();
        RoutePlanner planner = new RoutePlanner(new RouteSelectionStrategy());
        planner.registerDynamicServer("lobby-dynamic", "default", 100, 1);
        RouteDecision decision = planner.plan("", config, Map.of("lobby-dynamic", 4));
        assertEquals("lobby-dynamic", decision.selectedServer());
        assertTrue(planner.inspectionTargets("", config).contains("lobby-dynamic"));
        planner.unregisterDynamicServer("lobby-dynamic");
        assertFalse(planner.inspectionTargets("", config).contains("lobby-dynamic"));
    }
}
