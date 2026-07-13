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

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public final class RouteSelectionStrategy {

    private final ConcurrentMap<String, AtomicInteger> roundRobinState = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, WeightedRoundRobinState> wrrState = new ConcurrentHashMap<>();

    public RouteSelectionStrategy() {
    }

    public Optional<ServerCandidate> select(List<ServerCandidate> candidates, Config.SelectionMode mode, String groupKey) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return switch (mode) {
            case LEAST_PLAYERS -> candidates.stream()
                    .min(Comparator.comparingInt(ServerCandidate::playerCount).thenComparing(ServerCandidate::name));
            case RANDOM -> Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
            case ROUND_ROBIN -> Optional.of(selectRoundRobin(candidates, groupKey));
            case POWER_OF_TWO -> selectPowerOfTwo(candidates);
            case WEIGHTED_ROUND_ROBIN -> Optional.of(selectWeightedRoundRobin(candidates, groupKey));
            case LEAST_CONNECTIONS -> selectLeastConnections(candidates);
            case CONSISTENT_HASH -> Optional.empty();
            case LATENCY -> selectLatency(candidates);
        };
    }

    public Optional<String> selectConsistentHash(ConsistentHashRing ring, String groupKey, String playerId) {
        String server = ring.getServer(groupKey, playerId);
        return Optional.ofNullable(server);
    }

    public void reset() {
        roundRobinState.clear();
        wrrState.clear();
    }

    private ServerCandidate selectRoundRobin(List<ServerCandidate> candidates, String groupKey) {
        List<ServerCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(ServerCandidate::name))
                .toList();
        AtomicInteger cursor = roundRobinState.computeIfAbsent(groupKey == null ? "default" : groupKey, ignored -> new AtomicInteger(0));
        int index = Math.floorMod(cursor.getAndIncrement(), sorted.size());
        return sorted.get(index);
    }

    private Optional<ServerCandidate> selectPowerOfTwo(List<ServerCandidate> candidates) {
        if (candidates.size() <= 2) {
            return candidates.stream()
                    .min(Comparator.comparingInt(ServerCandidate::playerCount).thenComparing(ServerCandidate::name));
        }
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int i = rng.nextInt(candidates.size());
        int j = (i + 1 + rng.nextInt(candidates.size() - 1)) % candidates.size();
        ServerCandidate a = candidates.get(i);
        ServerCandidate b = candidates.get(j);
        return Optional.of(a.playerCount() <= b.playerCount() ? a : b);
    }

    private ServerCandidate selectWeightedRoundRobin(List<ServerCandidate> candidates, String groupKey) {
        String key = groupKey == null ? "default" : groupKey;
        WeightedRoundRobinState state = wrrState.computeIfAbsent(key, k -> new WeightedRoundRobinState());

        synchronized (state) {
            state.pruneStaleEntries(candidates);
            int totalWeight = 0;
            for (ServerCandidate c : candidates) {
                totalWeight += c.effectiveWeight();
            }
            if (totalWeight <= 0) {
                return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            }

            ServerCandidate best = null;
            int bestCurrentWeight = Integer.MIN_VALUE;

            for (ServerCandidate c : candidates) {
                int weight = c.effectiveWeight();
                int currentWeight = state.addWeight(c.name(), weight);
                if (currentWeight > bestCurrentWeight || (currentWeight == bestCurrentWeight && best != null && c.name().compareTo(best.name()) < 0)) {
                    bestCurrentWeight = currentWeight;
                    best = c;
                }
            }

            if (best != null) {
                state.subtractWeight(best.name(), totalWeight);
            }

            return best != null ? best : candidates.get(0);
        }
    }

    private Optional<ServerCandidate> selectLatency(List<ServerCandidate> candidates) {
        return candidates.stream()
                .min((a, b) -> {
                    long la = a.latency() < 0 ? Long.MAX_VALUE : a.latency();
                    long lb = b.latency() < 0 ? Long.MAX_VALUE : b.latency();
                    int cmp = Long.compare(la, lb);
                    if (cmp != 0) {
                        return cmp;
                    }
                    cmp = Integer.compare(a.playerCount(), b.playerCount());
                    if (cmp != 0) {
                        return cmp;
                    }
                    return a.name().compareTo(b.name());
                });
    }

    private Optional<ServerCandidate> selectLeastConnections(List<ServerCandidate> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(ServerCandidate::emaLoad).thenComparing(ServerCandidate::name));
    }

    static final class WeightedRoundRobinState {
        private final Map<String, Integer> currentWeights = new HashMap<>();

        int addWeight(String serverName, int weight) {
            return currentWeights.compute(serverName, (k, v) -> (v == null ? 0 : v) + weight);
        }

        void subtractWeight(String serverName, int totalWeight) {
            currentWeights.compute(serverName, (k, v) -> (v == null ? 0 : v) - totalWeight);
        }

        void pruneStaleEntries(List<ServerCandidate> activeCandidates) {
            Set<String> activeNames = new HashSet<>();
            for (ServerCandidate c : activeCandidates) {
                activeNames.add(c.name());
            }
            currentWeights.keySet().removeIf(name -> !activeNames.contains(name));
        }
    }
}
