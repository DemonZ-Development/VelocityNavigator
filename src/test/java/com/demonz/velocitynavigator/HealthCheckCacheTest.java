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

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckCacheTest {

    @Test
    void returnsFreshEntriesBeforeExpiry() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant checkedAt = Instant.parse("2026-01-01T00:00:00Z");
        cache.put("lobby-1", true, checkedAt);

        HealthCheckCache.Entry entry = cache.getIfFresh("lobby-1", checkedAt.plusSeconds(5), Duration.ofSeconds(10));

        assertNotNull(entry);
    }

    @Test
    void expiresEntriesAfterTheTtl() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant checkedAt = Instant.parse("2026-01-01T00:00:00Z");
        cache.put("lobby-1", true, checkedAt);

        HealthCheckCache.Entry entry = cache.getIfFresh("lobby-1", checkedAt.plusSeconds(30), Duration.ofSeconds(10));

        assertNull(entry);
    }

    @Test
    void purgeExpiredRemovesStaleEntries() {
        HealthCheckCache cache = new HealthCheckCache();
        Instant now = Instant.now();
        cache.put("old-server", true, now.minusSeconds(7200));
        cache.put("fresh-server", true, now.minusSeconds(10));

        cache.purgeExpired(Duration.ofMinutes(60));

        assertNull(cache.getCached("old-server"),
                "Entry older than TTL should be purged");
        assertNotNull(cache.getCached("fresh-server"),
                "Entry newer than TTL should remain");
    }

    @Test
    void purgeExpiredWithZeroTtlDoesNothing() {
        HealthCheckCache cache = new HealthCheckCache();
        cache.put("server-1", true, Instant.now());

        cache.purgeExpired(Duration.ZERO);

        assertNotNull(cache.getCached("server-1"),
                "Zero TTL should not remove entries");
    }
}
