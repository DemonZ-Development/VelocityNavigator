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

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RoutingStats {

    private final ConcurrentMap<String, AtomicLong> connectionCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> redirectCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> cumulativeConnectionCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> cumulativeRedirectCounts = new ConcurrentHashMap<>();
    private volatile Instant lastReset = Instant.now();

    public void recordConnection(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            return;
        }
        connectionCounts.computeIfAbsent(serverName, k -> new AtomicLong(0)).incrementAndGet();
        cumulativeConnectionCounts.computeIfAbsent(serverName, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordRedirect(String reason, String serverName) {
        if (reason == null || reason.isBlank()) {
            reason = "balancing";
        }
        if (serverName == null || serverName.isBlank()) {
            return;
        }
        String normalizedReason = reason.toLowerCase(Locale.ROOT);

        redirectCounts.computeIfAbsent(normalizedReason, r -> new ConcurrentHashMap<>())
                .computeIfAbsent(serverName, s -> new AtomicLong(0))
                .incrementAndGet();

        cumulativeRedirectCounts.computeIfAbsent(normalizedReason, r -> new ConcurrentHashMap<>())
                .computeIfAbsent(serverName, s -> new AtomicLong(0))
                .incrementAndGet();

        recordConnection(serverName);
    }

    public Map<String, Map<String, Long>> getRedirectCounts() {
        Map<String, Map<String, Long>> snapshot = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ConcurrentMap<String, AtomicLong>> reasonEntry : redirectCounts.entrySet()) {
            Map<String, Long> serverMap = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, AtomicLong> serverEntry : reasonEntry.getValue().entrySet()) {
                serverMap.put(serverEntry.getKey(), serverEntry.getValue().get());
            }
            snapshot.put(reasonEntry.getKey(), serverMap);
        }
        return snapshot;
    }

    public Map<String, Map<String, Long>> getCumulativeRedirectCounts() {
        Map<String, Map<String, Long>> snapshot = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, ConcurrentMap<String, AtomicLong>> reasonEntry : cumulativeRedirectCounts.entrySet()) {
            Map<String, Long> serverMap = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, AtomicLong> serverEntry : reasonEntry.getValue().entrySet()) {
                serverMap.put(serverEntry.getKey(), serverEntry.getValue().get());
            }
            snapshot.put(reasonEntry.getKey(), serverMap);
        }
        return snapshot;
    }

    public Map<String, Long> getDistribution() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : connectionCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    public Map<String, Long> getCumulativeDistribution() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : cumulativeConnectionCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    public long totalConnections() {
        long total = 0;
        for (AtomicLong count : connectionCounts.values()) {
            total += count.get();
        }
        return total;
    }

    public Instant lastReset() {
        return lastReset;
    }

    public void reset() {
        connectionCounts.clear();
        redirectCounts.clear();
        lastReset = Instant.now();
    }
}
