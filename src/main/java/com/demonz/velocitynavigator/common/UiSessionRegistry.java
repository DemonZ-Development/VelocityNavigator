/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UiSessionRegistry {
    private record Session(String token, String backend, Set<String> actions, long expiresAt) {}
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public UiSessionRegistry() {
        this(Clock.SYSTEM);
    }

    public UiSessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public String create(UUID player, String backend, Set<String> actions, int seconds) {
        String token = UUID.randomUUID().toString();
        sessions.put(player, new Session(token, backend, Set.copyOf(actions), clock.millis() + seconds * 1000L));
        return token;
    }

    public boolean consume(UUID player, String backend, String token, String action) {
        AtomicBoolean accepted = new AtomicBoolean();
        sessions.computeIfPresent(player, (id, session) -> {
            if (clock.millis() >= session.expiresAt()) return null;
            if (!session.backend().equals(backend) || !session.token().equals(token)
                    || !session.actions().contains(action)) return session;
            accepted.set(true);
            return null;
        });
        return accepted.get();
    }

    public void clear(UUID player) {
        sessions.remove(player);
    }

    public void clear() {
        sessions.clear();
    }
}
