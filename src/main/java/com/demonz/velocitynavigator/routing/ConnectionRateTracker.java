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
package com.demonz.velocitynavigator.routing;

import com.demonz.velocitynavigator.common.Clock;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashSet;
import java.util.Set;

public final class ConnectionRateTracker {

    private static final int DEFAULT_MAX_ENTRIES_PER_SERVER = 10_000;

    private final int windowSeconds;
    private final int maxEntriesPerServer;
    private final Clock clock;
    private final ConcurrentMap<String, ServerWindow> connectionTimes = new ConcurrentHashMap<>();

    private static final class ServerWindow {
        final ConcurrentLinkedDeque<Instant> times = new ConcurrentLinkedDeque<>();
        final AtomicInteger count = new AtomicInteger();
    }

    public ConnectionRateTracker(int windowSeconds) {
        this(windowSeconds, DEFAULT_MAX_ENTRIES_PER_SERVER, Clock.SYSTEM);
    }

    public ConnectionRateTracker(int windowSeconds, int maxEntriesPerServer) {
        this(windowSeconds, maxEntriesPerServer, Clock.SYSTEM);
    }

    public ConnectionRateTracker(int windowSeconds, int maxEntriesPerServer, Clock clock) {
        this.windowSeconds = Math.max(1, windowSeconds);
        this.maxEntriesPerServer = Math.max(1, maxEntriesPerServer);
        this.clock = clock == null ? Clock.SYSTEM : clock;
    }

    public void recordConnection(String serverName) {
        String normalizedServerName = normalize(serverName);
        if (normalizedServerName.isBlank()) {
            return;
        }
        ServerWindow window = connectionTimes.computeIfAbsent(normalizedServerName, k -> new ServerWindow());
        window.times.addLast(clock.now());
        window.count.incrementAndGet();
        purgeOld(window);
        trimToLimit(window);
    }

    public double getRatePerSecond(String serverName) {
        ServerWindow window = connectionTimes.get(normalize(serverName));
        if (window == null || window.count.get() == 0) {
            return 0.0;
        }
        purgeOld(window);
        int count = window.count.get();
        if (count <= 1) {
            return 0.0;
        }
        Instant oldest = window.times.peekFirst();
        if (oldest == null) {
            return 0.0;
        }
        Instant now = clock.now();
        double spanSeconds = Duration.between(oldest, now).toMillis() / 1000.0;
        return spanSeconds > 0 ? count / spanSeconds : 0.0;
    }

    public int getConnectionCount(String serverName) {
        ServerWindow window = connectionTimes.get(normalize(serverName));
        if (window == null) {
            return 0;
        }
        purgeOld(window);
        return Math.max(0, window.count.get());
    }

    public void remove(String serverName) {
        connectionTimes.remove(normalize(serverName));
    }

    public void retainServers(Collection<String> serverNames) {
        if (serverNames == null) {
            clear();
            return;
        }
        Set<String> retained = new HashSet<>();
        for (String serverName : serverNames) {
            String normalized = normalize(serverName);
            if (!normalized.isBlank()) {
                retained.add(normalized);
            }
        }
        connectionTimes.keySet().removeIf(key -> !retained.contains(key));
    }

    public void purge() {
        for (Map.Entry<String, ServerWindow> entry : connectionTimes.entrySet()) {
            ServerWindow window = entry.getValue();
            purgeOld(window);
            if (window.count.get() <= 0) {
                connectionTimes.remove(entry.getKey(), window);
            }
        }
    }

    public void clear() {
        connectionTimes.clear();
    }

    private void purgeOld(ServerWindow window) {
        Instant cutoff = clock.now().minusSeconds(windowSeconds);
        while (!window.times.isEmpty()) {
            Instant oldest = window.times.peekFirst();
            if (oldest != null && oldest.isBefore(cutoff)) {
                if (window.times.pollFirst() != null) {
                    window.count.decrementAndGet();
                }
            } else {
                break;
            }
        }
    }

    private void trimToLimit(ServerWindow window) {
        while (window.count.get() > maxEntriesPerServer) {
            if (window.times.pollFirst() != null) {
                window.count.decrementAndGet();
            } else {
                break;
            }
        }
    }

    private String normalize(String serverName) {
        return serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
    }
}
