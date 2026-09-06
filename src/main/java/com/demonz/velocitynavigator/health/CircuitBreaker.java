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
package com.demonz.velocitynavigator.health;
import com.demonz.velocitynavigator.CircuitBreakerState;

import com.demonz.velocitynavigator.common.Clock;

import java.time.Instant;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class CircuitBreaker {

    private record BreakerState(CircuitBreakerState state, int failureCount, Instant openSince, int halfOpenTests, int halfOpenSuccesses, Instant halfOpenGrantedAt) {}

    private final ConcurrentMap<String, BreakerState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> tripCounts = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final int cooldownSeconds;
    private final int halfOpenMaxTests;
    private final Clock clock;

    public CircuitBreaker(int failureThreshold, int cooldownSeconds, int halfOpenMaxTests) {
        this(failureThreshold, cooldownSeconds, halfOpenMaxTests, Clock.SYSTEM);
    }

    public CircuitBreaker(int failureThreshold, int cooldownSeconds, int halfOpenMaxTests, Clock clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
        this.halfOpenMaxTests = Math.max(1, halfOpenMaxTests);
        this.clock = clock == null ? Clock.SYSTEM : clock;
    }

    public boolean isAvailable(String serverName) {
        String normalizedServerName = normalize(serverName);
        BreakerState state = states.get(normalizedServerName);
        if (state == null) {
            return true;
        }
        return switch (state.state) {
            case CLOSED -> true;
            case OPEN -> {
                AtomicBoolean available = new AtomicBoolean(false);
                states.compute(normalizedServerName, (key, current) -> {
                    if (current != null && current.state == CircuitBreakerState.OPEN
                            && clock.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                        available.set(true);
                        return new BreakerState(CircuitBreakerState.HALF_OPEN, current.failureCount, current.openSince, 1, 0, clock.now());
                    }
                    return current;
                });
                yield available.get();
            }
            case HALF_OPEN -> {
                AtomicBoolean available = new AtomicBoolean(false);
                states.compute(normalizedServerName, (key, current) -> {
                    if (current != null && current.state == CircuitBreakerState.HALF_OPEN
                            && current.halfOpenTests < halfOpenMaxTests) {
                        available.set(true);
                        return new BreakerState(CircuitBreakerState.HALF_OPEN, current.failureCount,
                                current.openSince, current.halfOpenTests + 1, current.halfOpenSuccesses, clock.now());
                    }
                    if (current != null && current.state == CircuitBreakerState.HALF_OPEN) {
                        Instant grantedAt = current.halfOpenGrantedAt != null ? current.halfOpenGrantedAt : current.openSince;
                        if (grantedAt != null && clock.now().isAfter(grantedAt.plusSeconds(cooldownSeconds))) {
                            return new BreakerState(CircuitBreakerState.OPEN, current.failureCount, clock.now(), 0, 0, null);
                        }
                    }
                    return current;
                });
                yield available.get();
            }
        };
    }

    public void recordSuccess(String serverName) {
        String normalizedServerName = normalize(serverName);
        states.compute(normalizedServerName, (key, current) -> {
            if (current == null) {
                return null;
            }
            return switch (current.state) {
                case CLOSED -> new BreakerState(CircuitBreakerState.CLOSED, 0, null, 0, 0, null);
                case OPEN -> current;
                case HALF_OPEN -> {
                    int successes = current.halfOpenSuccesses + 1;
                    if (successes >= halfOpenMaxTests) {
                        yield new BreakerState(CircuitBreakerState.CLOSED, 0, null, 0, 0, null);
                    }
                    yield new BreakerState(CircuitBreakerState.HALF_OPEN, current.failureCount,
                            current.openSince, current.halfOpenTests, successes, current.halfOpenGrantedAt);
                }
            };
        });
    }

    public void recordFailure(String serverName) {
        String normalizedServerName = normalize(serverName);
        states.compute(normalizedServerName, (key, current) -> {
            if (current == null) {
                current = new BreakerState(CircuitBreakerState.CLOSED, 0, null, 0, 0, null);
            }
            return switch (current.state) {
                case CLOSED -> {
                    int newCount = current.failureCount + 1;
                    if (newCount >= failureThreshold) {
                        tripCounts.computeIfAbsent(normalizedServerName, k -> new AtomicLong(0)).incrementAndGet();
                        yield new BreakerState(CircuitBreakerState.OPEN, newCount, clock.now(), 0, 0, null);
                    }
                    yield new BreakerState(CircuitBreakerState.CLOSED, newCount, null, 0, 0, null);
                }
                case OPEN -> new BreakerState(CircuitBreakerState.OPEN, current.failureCount + 1, current.openSince, 0, 0, null);
                case HALF_OPEN -> {
                    tripCounts.computeIfAbsent(normalizedServerName, k -> new AtomicLong(0)).incrementAndGet();
                    yield new BreakerState(CircuitBreakerState.OPEN, current.failureCount + 1, clock.now(), 0, 0, null);
                }
            };
        });
    }

    public CircuitBreakerState getState(String serverName) {
        String normalizedServerName = normalize(serverName);
        BreakerState state = states.get(normalizedServerName);
        if (state == null) {
            return CircuitBreakerState.CLOSED;
        }
        if (state.state == CircuitBreakerState.OPEN && clock.now().isAfter(state.openSince.plusSeconds(cooldownSeconds))) {
            states.compute(normalizedServerName, (key, current) -> {
                if (current != null && current.state == CircuitBreakerState.OPEN
                        && clock.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                    return new BreakerState(CircuitBreakerState.HALF_OPEN, current.failureCount, current.openSince, 0, 0, null);
                }
                return current;
            });
        }
        BreakerState current = states.get(normalizedServerName);
        return current == null ? CircuitBreakerState.CLOSED : current.state;
    }

    public Map<String, Long> getTripCounts() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, AtomicLong> entry : tripCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    public Map<String, CircuitBreakerState> getStates() {
        Map<String, CircuitBreakerState> snapshot = new LinkedHashMap<>();
        states.keySet().forEach(server -> snapshot.put(server, getState(server)));
        return Map.copyOf(snapshot);
    }

    public void applyRemoteState(String serverName, CircuitBreakerState state) {
        String normalized = normalize(serverName);
        if (state == null || state == CircuitBreakerState.CLOSED) {
            states.remove(normalized);
        } else if (state == CircuitBreakerState.OPEN) {
            states.put(normalized, new BreakerState(CircuitBreakerState.OPEN, failureThreshold, clock.now(), 0, 0, null));
        } else {
            states.put(normalized, new BreakerState(CircuitBreakerState.HALF_OPEN, failureThreshold, clock.now(), 0, 0, null));
        }
    }

    public void reset(String serverName) {
        String normalizedServerName = normalize(serverName);
        states.remove(normalizedServerName);
        tripCounts.remove(normalizedServerName);
    }

    public void resetAll() {
        states.clear();
        tripCounts.clear();
    }

    private String normalize(String serverName) {
        return serverName == null ? "" : serverName.toLowerCase(Locale.ROOT);
    }
}
