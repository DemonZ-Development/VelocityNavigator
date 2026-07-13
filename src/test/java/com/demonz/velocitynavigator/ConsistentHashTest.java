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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistentHashTest {

    @Test
    void sameKeyReturnsSameServer() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        ring.updateRing("default", List.of("server-a", "server-b", "server-c"));

        String key = UUID.randomUUID().toString();
        String first = ring.getServer("default", key);
        String second = ring.getServer("default", key);

        assertNotNull(first);
        assertEquals(first, second, "Same key must always map to the same server");
    }

    @Test
    void distributionIsReasonable() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        List<String> servers = List.of("s1", "s2", "s3", "s4", "s5");
        ring.updateRing("default", servers);

        Map<String, Integer> counts = new HashMap<>();
        for (String s : servers) {
            counts.put(s, 0);
        }

        int keyCount = 1000;
        for (int i = 0; i < keyCount; i++) {
            String server = ring.getServer("default", "player-" + i);
            assertNotNull(server);
            counts.merge(server, 1, Integer::sum);
        }

        int ideal = keyCount / servers.size();
        double maxDeviation = ideal * 0.40;

        for (String server : servers) {
            int count = counts.get(server);
            double deviation = Math.abs(count - ideal);
            assertTrue(deviation < maxDeviation,
                    "Server " + server + " has " + count + " keys, deviation " + deviation
                            + " exceeds max " + maxDeviation);
        }
    }

    @Test
    void addingServerMinimizesRemapping() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        List<String> originalServers = List.of("s1", "s2", "s3", "s4");
        ring.updateRing("default", originalServers);

        Map<String, String> originalMappings = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "player-" + i;
            originalMappings.put(key, ring.getServer("default", key));
        }

        List<String> newServers = List.of("s1", "s2", "s3", "s4", "s5");
        ring.updateRing("default", newServers);

        int remapped = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "player-" + i;
            String newServer = ring.getServer("default", key);
            if (!originalMappings.get(key).equals(newServer)) {
                remapped++;
            }
        }

        double remapRatio = remapped / 1000.0;
        assertTrue(remapRatio < 0.30,
                "Remapping ratio should be < 30% when adding one server; got " + (remapRatio * 100) + "%");
    }

    @Test
    void removingServerMinimizesRemapping() {
        ConsistentHashRing ring = new ConsistentHashRing(150);
        List<String> originalServers = List.of("s1", "s2", "s3", "s4", "s5");
        ring.updateRing("default", originalServers);

        Map<String, String> originalMappings = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String key = "player-" + i;
            originalMappings.put(key, ring.getServer("default", key));
        }

        List<String> newServers = List.of("s1", "s2", "s3", "s4");
        ring.updateRing("default", newServers);

        int remapped = 0;
        for (int i = 0; i < 1000; i++) {
            String key = "player-" + i;
            String newServer = ring.getServer("default", key);
            if (!originalMappings.get(key).equals(newServer)) {
                remapped++;
            }
        }

        double remapRatio = remapped / 1000.0;
        assertTrue(remapRatio < 0.30,
                "Remapping ratio should be < 30% when removing one server; got " + (remapRatio * 100) + "%");
    }

    @Test
    void virtualNodesAffectDistribution() {
        List<String> servers = List.of("s1", "s2", "s3");

        ConsistentHashRing sparseRing = new ConsistentHashRing(10);
        sparseRing.updateRing("default", servers);

        ConsistentHashRing denseRing = new ConsistentHashRing(200);
        denseRing.updateRing("default", servers);

        Map<String, Integer> sparseCounts = new HashMap<>();
        Map<String, Integer> denseCounts = new HashMap<>();
        for (String s : servers) {
            sparseCounts.put(s, 0);
            denseCounts.put(s, 0);
        }

        int keyCount = 1000;
        for (int i = 0; i < keyCount; i++) {
            String key = "player-" + i;
            sparseCounts.merge(sparseRing.getServer("default", key), 1, Integer::sum);
            denseCounts.merge(denseRing.getServer("default", key), 1, Integer::sum);
        }

        double sparseStdDev = stdDev(sparseCounts, keyCount);
        double denseStdDev = stdDev(denseCounts, keyCount);

        assertTrue(denseStdDev <= sparseStdDev,
                "More virtual nodes should produce better distribution. "
                        + "Dense stdDev=" + denseStdDev + ", Sparse stdDev=" + sparseStdDev);
    }

    private double stdDev(Map<String, Integer> counts, int total) {
        double mean = total / (double) counts.size();
        double sumSquares = 0;
        for (int count : counts.values()) {
            sumSquares += Math.pow(count - mean, 2);
        }
        return Math.sqrt(sumSquares / counts.size());
    }
}
