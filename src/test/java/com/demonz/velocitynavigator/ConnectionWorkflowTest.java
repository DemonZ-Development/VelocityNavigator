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

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionWorkflowTest {

    @Test
    void retryDelayGrowsExponentially() {
        long d0 = ConnectionWorkflow.retryDelayMs(0);
        long d1 = ConnectionWorkflow.retryDelayMs(1);
        long d2 = ConnectionWorkflow.retryDelayMs(2);
        long d3 = ConnectionWorkflow.retryDelayMs(3);

        assertTrue(d0 >= 200L && d0 <= 300L, "attempt 0 delay should be 200-300ms, got " + d0);
        assertTrue(d1 >= 400L && d1 <= 600L, "attempt 1 delay should be 400-600ms, got " + d1);
        assertTrue(d2 >= 800L && d2 <= 1200L, "attempt 2 delay should be 800-1200ms, got " + d2);
        assertTrue(d3 >= 1600L && d3 <= 2400L, "attempt 3 delay should be 1600-2400ms, got " + d3);
    }

    @Test
    void retryDelayCapsAtTwoSecondsBase() {
        for (int i = 5; i < 20; i++) {
            long delay = ConnectionWorkflow.retryDelayMs(i);
            assertTrue(delay >= 2000L && delay <= 3000L,
                    "attempt " + i + " delay should be capped at 2000ms base + jitter, got " + delay);
        }
    }

    @Test
    void retryDelayIsNonNegative() {
        for (int i = 0; i < 32; i++) {
            long delay = ConnectionWorkflow.retryDelayMs(i);
            assertTrue(delay > 0L, "delay must be positive at attempt " + i);
        }
    }

    @Test
    void retryDelayHasJitter() {
        long first = ConnectionWorkflow.retryDelayMs(2);
        boolean sawDifferent = false;
        for (int i = 0; i < 20; i++) {
            long next = ConnectionWorkflow.retryDelayMs(2);
            if (next != first) {
                sawDifferent = true;
                break;
            }
        }
        assertTrue(sawDifferent, "retryDelayMs should produce different values across calls due to jitter");
    }
}
