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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRateTrackerTest {

    @Test
    void tracksConnectionRate() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60);

        for (int i = 0; i < 5; i++) {
            tracker.recordConnection("server-1");
        }

        double rate = tracker.getRatePerSecond("server-1");
        assertTrue(rate >= 0, "Rate should be non-negative");

        int count = tracker.getConnectionCount("server-1");
        assertTrue(count > 0, "Connection count should be positive after recording");
        assertTrue(count <= 5, "Connection count should not exceed recorded connections");
    }

    @Test
    void rateDecaysOverTime() throws InterruptedException {
        ConnectionRateTracker tracker = new ConnectionRateTracker(1);

        for (int i = 0; i < 5; i++) {
            tracker.recordConnection("server-1");
        }

        int countBefore = tracker.getConnectionCount("server-1");
        assertTrue(countBefore > 0, "Should have connections before decay");

        Thread.sleep(1100);

        int countAfter = tracker.getConnectionCount("server-1");
        assertTrue(countAfter < countBefore,
                "Connection count should decrease after window expires; before=" + countBefore + ", after=" + countAfter);
    }

    @Test
    void multipleServersTrackedIndependently() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60);

        tracker.recordConnection("server-1");
        tracker.recordConnection("server-1");
        tracker.recordConnection("server-1");

        tracker.recordConnection("server-2");
        tracker.recordConnection("server-2");

        int count1 = tracker.getConnectionCount("server-1");
        int count2 = tracker.getConnectionCount("server-2");

        assertTrue(count1 > count2,
                "server-1 should have more recorded connections than server-2");
        assertEquals(0, tracker.getConnectionCount("server-3"),
                "Untracked server should have zero connections");
    }

    @Test
    void retainServersRemovesDeletedServerEntries() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60);

        tracker.recordConnection("server-1");
        tracker.recordConnection("server-2");

        tracker.retainServers(java.util.List.of("server-2"));

        assertEquals(0, tracker.getConnectionCount("server-1"));
        assertEquals(1, tracker.getConnectionCount("server-2"));
    }

    @Test
    void purgeRemovesExpiredEmptyEntries() throws InterruptedException {
        ConnectionRateTracker tracker = new ConnectionRateTracker(1);

        tracker.recordConnection("server-1");
        Thread.sleep(1100);
        tracker.purge();

        assertEquals(0, tracker.getConnectionCount("server-1"));
    }

    @Test
    void capsPerServerEntries() {
        ConnectionRateTracker tracker = new ConnectionRateTracker(60, 3);

        for (int i = 0; i < 10; i++) {
            tracker.recordConnection("server-1");
        }

        assertEquals(3, tracker.getConnectionCount("server-1"));
    }
}
