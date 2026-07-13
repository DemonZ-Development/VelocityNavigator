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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ConsistentHashRing {

    private static final int DEFAULT_VIRTUAL_NODES = 150;

    private static final ThreadLocal<MessageDigest> DIGESTS = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    });

    private final int virtualNodes;
    private final ConcurrentMap<String, NavigableMap<Long, String>> rings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<String>> previousServerLists = new ConcurrentHashMap<>();

    public ConsistentHashRing() {
        this(DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashRing(int virtualNodes) {
        this.virtualNodes = Math.max(1, virtualNodes);
    }

    public void updateRing(String groupKey, List<String> servers) {
        List<String> sortedServers = new ArrayList<>(servers);
        Collections.sort(sortedServers);
        List<String> previous = previousServerLists.get(groupKey);
        if (previous != null && previous.equals(sortedServers)) {
            return;
        }

        NavigableMap<Long, String> ring = new TreeMap<>();
        for (String server : servers) {
            for (int i = 0; i < virtualNodes; i++) {
                long hash = hash(groupKey + ":" + server + ":" + i);
                ring.put(hash, server);
            }
        }
        rings.put(groupKey, Collections.unmodifiableNavigableMap(ring));
        previousServerLists.put(groupKey, sortedServers);
    }

    public String getServer(String groupKey, String input) {
        NavigableMap<Long, String> ring = rings.get(groupKey);
        if (ring == null || ring.isEmpty()) {
            return null;
        }
        long hash = hash(input);
        NavigableMap<Long, String> tail = ring.tailMap(hash, true);
        Map.Entry<Long, String> entry = tail.isEmpty() ? ring.firstEntry() : tail.firstEntry();
        return entry.getValue();
    }

    public List<String> getServerOrder(String groupKey, String input) {
        NavigableMap<Long, String> ring = rings.get(groupKey);
        if (ring == null || ring.isEmpty()) {
            return List.of();
        }
        long hash = hash(input);
        Set<String> result = new LinkedHashSet<>();
        NavigableMap<Long, String> tail = ring.tailMap(hash, true);
        for (String server : tail.values()) {
            result.add(server);
        }
        for (String server : ring.headMap(hash, false).values()) {
            result.add(server);
        }
        return List.copyOf(result);
    }

    public void removeGroup(String groupKey) {
        rings.remove(groupKey);
    }

    public void clear() {
        rings.clear();
        previousServerLists.clear();
    }

    private long hash(String input) {
        MessageDigest md = DIGESTS.get();
        md.reset();
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return ((long) (digest[0] & 0xFF) << 56)
                | ((long) (digest[1] & 0xFF) << 48)
                | ((long) (digest[2] & 0xFF) << 40)
                | ((long) (digest[3] & 0xFF) << 32)
                | ((long) (digest[4] & 0xFF) << 24)
                | ((long) (digest[5] & 0xFF) << 16)
                | ((long) (digest[6] & 0xFF) << 8)
                | ((long) (digest[7] & 0xFF));
    }
}
