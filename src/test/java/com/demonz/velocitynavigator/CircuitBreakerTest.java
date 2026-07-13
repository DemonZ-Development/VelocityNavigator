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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void startsInClosedState() {
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));
        assertTrue(breaker.isAvailable("server-1"));
    }

    @Test
    void transitionsToOpenAfterThresholdFailures() {
        CircuitBreaker breaker = new CircuitBreaker(3, 30, 1);

        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));

        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));

        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));
        assertFalse(breaker.isAvailable("server-1"));
    }

    @Test
    void successResetsClosedFailureCount() {
        CircuitBreaker breaker = new CircuitBreaker(2, 30, 1);

        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));

        breaker.recordSuccess("server-1");
        breaker.recordFailure("server-1");

        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"),
                "A success between failures should reset the consecutive failure count");
    }

    @Test
    void serverNamesAreCaseInsensitive() {
        CircuitBreaker breaker = new CircuitBreaker(2, 30, 1);

        breaker.recordFailure("Lobby-1");
        breaker.recordFailure("lobby-1");

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("LOBBY-1"));
        assertFalse(breaker.isAvailable("lobby-1"));
    }

    @Test
    void halfOpenAfterCooldown() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));

        Thread.sleep(1100);

        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));
    }

    @Test
    void closesAfterSuccessInHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");

        Thread.sleep(1100);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));

        breaker.recordSuccess("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));
        assertTrue(breaker.isAvailable("server-1"));
    }

    @Test
    void halfOpenRequiresAllConfiguredTestSuccessesBeforeClosing() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(1, 1, 2);

        breaker.recordFailure("server-1");
        Thread.sleep(1100);

        assertTrue(breaker.isAvailable("server-1"));
        assertTrue(breaker.isAvailable("server-1"));
        assertFalse(breaker.isAvailable("server-1"),
                "HALF_OPEN should only admit the configured number of test requests");

        breaker.recordSuccess("server-1");
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));

        breaker.recordSuccess("server-1");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState("server-1"));
    }

    @Test
    void halfOpenFailureAfterPriorSuccessReopensCircuit() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(1, 1, 2);

        breaker.recordFailure("server-1");
        Thread.sleep(1100);
        assertTrue(breaker.isAvailable("server-1"));
        assertTrue(breaker.isAvailable("server-1"));

        breaker.recordSuccess("server-1");
        breaker.recordFailure("server-1");

        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));
        assertFalse(breaker.isAvailable("server-1"));
    }

    @Test
    void staysOpenAfterFailureInHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");

        Thread.sleep(1100);
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.getState("server-1"));

        breaker.recordFailure("server-1");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState("server-1"));
        assertFalse(breaker.isAvailable("server-1"));
    }

    @Test
    void isAvailableReturnsTrueForClosedAndHalfOpen() throws InterruptedException {
        CircuitBreaker breaker = new CircuitBreaker(3, 1, 1);

        assertTrue(breaker.isAvailable("server-1"));

        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        breaker.recordFailure("server-1");
        assertFalse(breaker.isAvailable("server-1"));

        Thread.sleep(1100);
        assertTrue(breaker.isAvailable("server-1"),
                "HALF_OPEN state should report available for test requests");
    }
}
