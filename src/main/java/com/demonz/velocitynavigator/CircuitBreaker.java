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
import java.util.concurrent.atomic.AtomicBoolean;

public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private record BreakerState(State state, int failureCount, Instant openSince, int halfOpenTests, int halfOpenSuccesses) {}

    private final ConcurrentMap<String, BreakerState> states = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, java.util.concurrent.atomic.AtomicLong> tripCounts = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final int cooldownSeconds;
    private final int halfOpenMaxTests;

    public CircuitBreaker(int failureThreshold, int cooldownSeconds, int halfOpenMaxTests) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownSeconds = Math.max(1, cooldownSeconds);
        this.halfOpenMaxTests = Math.max(1, halfOpenMaxTests);
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
                    if (current != null && current.state == State.OPEN
                            && Instant.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                        available.set(true);
                        return new BreakerState(State.HALF_OPEN, current.failureCount, current.openSince, 1, 0);
                    }
                    return current;
                });
                yield available.get();
            }
            case HALF_OPEN -> {
                AtomicBoolean available = new AtomicBoolean(false);
                states.compute(normalizedServerName, (key, current) -> {
                    if (current != null && current.state == State.HALF_OPEN
                            && current.halfOpenTests < halfOpenMaxTests) {
                        available.set(true);
                        return new BreakerState(State.HALF_OPEN, current.failureCount,
                                current.openSince, current.halfOpenTests + 1, current.halfOpenSuccesses);
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
                case CLOSED -> new BreakerState(State.CLOSED, 0, null, 0, 0);
                case OPEN -> current;
                case HALF_OPEN -> {
                    int successes = current.halfOpenSuccesses + 1;
                    if (successes >= halfOpenMaxTests) {
                        yield new BreakerState(State.CLOSED, 0, null, 0, 0);
                    }
                    yield new BreakerState(State.HALF_OPEN, current.failureCount,
                            current.openSince, current.halfOpenTests, successes);
                }
            };
        });
    }

    public void recordFailure(String serverName) {
        String normalizedServerName = normalize(serverName);
        states.compute(normalizedServerName, (key, current) -> {
            if (current == null) {
                current = new BreakerState(State.CLOSED, 0, null, 0, 0);
            }
            return switch (current.state) {
                case CLOSED -> {
                    int newCount = current.failureCount + 1;
                    if (newCount >= failureThreshold) {
                        tripCounts.computeIfAbsent(normalizedServerName, k -> new java.util.concurrent.atomic.AtomicLong(0)).incrementAndGet();
                        yield new BreakerState(State.OPEN, newCount, Instant.now(), 0, 0);
                    }
                    yield new BreakerState(State.CLOSED, newCount, null, 0, 0);
                }
                case OPEN -> new BreakerState(State.OPEN, current.failureCount + 1, current.openSince, 0, 0);
                case HALF_OPEN -> {
                    tripCounts.computeIfAbsent(normalizedServerName, k -> new java.util.concurrent.atomic.AtomicLong(0)).incrementAndGet();
                    yield new BreakerState(State.OPEN, current.failureCount + 1, Instant.now(), 0, 0);
                }
            };
        });
    }

    public State getState(String serverName) {
        String normalizedServerName = normalize(serverName);
        BreakerState state = states.get(normalizedServerName);
        if (state == null) {
            return State.CLOSED;
        }
        if (state.state == State.OPEN && Instant.now().isAfter(state.openSince.plusSeconds(cooldownSeconds))) {
            states.compute(normalizedServerName, (key, current) -> {
                if (current != null && current.state == State.OPEN
                        && Instant.now().isAfter(current.openSince.plusSeconds(cooldownSeconds))) {
                    return new BreakerState(State.HALF_OPEN, current.failureCount, current.openSince, 0, 0);
                }
                return current;
            });
        }
        BreakerState current = states.get(normalizedServerName);
        return current == null ? State.CLOSED : current.state;
    }

    public Map<String, Long> getTripCounts() {
        Map<String, Long> snapshot = new ConcurrentHashMap<>();
        for (Map.Entry<String, java.util.concurrent.atomic.AtomicLong> entry : tripCounts.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
    }

    public Map<String, State> getStates() {
        Map<String, State> snapshot = new java.util.LinkedHashMap<>();
        states.keySet().forEach(server -> snapshot.put(server, getState(server)));
        return Map.copyOf(snapshot);
    }

    public void applyRemoteState(String serverName, State state) {
        String normalized = normalize(serverName);
        if (state == null || state == State.CLOSED) {
            states.remove(normalized);
        } else if (state == State.OPEN) {
            states.put(normalized, new BreakerState(State.OPEN, failureThreshold, Instant.now(), 0, 0));
        } else {
            states.put(normalized, new BreakerState(State.HALF_OPEN, failureThreshold, Instant.now(), 0, 0));
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
