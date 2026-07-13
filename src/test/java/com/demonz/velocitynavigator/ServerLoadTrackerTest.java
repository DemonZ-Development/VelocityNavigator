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

class ServerLoadTrackerTest {

    @Test
    void emaCalculationIsCorrect() {
        ServerLoadTracker tracker = new ServerLoadTracker(0.3);

        tracker.update("server-1", 10);
        assertEquals(10.0, tracker.getEma("server-1"), 0.001);

        tracker.update("server-1", 20);
        assertEquals(13.0, tracker.getEma("server-1"), 0.001);

        tracker.update("server-1", 30);
        assertEquals(18.1, tracker.getEma("server-1"), 0.001);
    }

    @Test
    void emaRespondsToTrends() {
        ServerLoadTracker tracker = new ServerLoadTracker(0.3);

        tracker.update("rising", 10);
        tracker.update("rising", 20);
        tracker.update("rising", 40);
        tracker.update("rising", 60);

        tracker.update("steady", 25);
        tracker.update("steady", 25);
        tracker.update("steady", 25);
        tracker.update("steady", 25);

        double risingEma = tracker.getEma("rising");
        double steadyEma = tracker.getEma("steady");

        assertTrue(risingEma > steadyEma,
                "Rising load EMA (" + risingEma + ") should be higher than steady EMA (" + steadyEma + ")");
    }

    @Test
    void emaWithAlphaOneIsLatestValue() {
        ServerLoadTracker tracker = new ServerLoadTracker(1.0);

        tracker.update("server-1", 10);
        assertEquals(10.0, tracker.getEma("server-1"), 0.001);

        tracker.update("server-1", 50);
        assertEquals(50.0, tracker.getEma("server-1"), 0.001);

        tracker.update("server-1", 7);
        assertEquals(7.0, tracker.getEma("server-1"), 0.001);
    }
}
