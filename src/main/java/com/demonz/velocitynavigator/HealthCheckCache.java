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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HealthCheckCache {

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public Entry getIfFresh(String key, Instant now, Duration ttl) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        if (entry.checkedAt().plus(ttl).isBefore(now)) {
            entries.remove(key, entry);
            return null;
        }
        return entry;
    }

    public void put(String key, boolean online, Instant checkedAt) {
        entries.put(key, new Entry(online, checkedAt));
    }

    public Entry getCached(String key) {
        return entries.get(key);
    }

    public void purgeExpired(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        Instant cutoff = Instant.now().minus(ttl);
        entries.entrySet().removeIf(entry -> entry.getValue().checkedAt().isBefore(cutoff));
    }

    public void clear() {
        entries.clear();
    }

    public ConcurrentMap<String, Entry> entries() {
        return entries;
    }

    public record Entry(boolean online, Instant checkedAt) {
    }
}
