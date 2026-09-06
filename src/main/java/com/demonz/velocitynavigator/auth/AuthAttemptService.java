/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class AuthAttemptService {

    public enum Result {
        SUCCESS,
        ALREADY_REGISTERED,
        PASSWORD_MISMATCH,
        INVALID_PASSWORD,
        NOT_REGISTERED,
        INVALID_CREDENTIALS,
        TOO_MANY_ATTEMPTS
    }

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(1);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);
    private static final int MAX_GLOBAL_FAILED_ATTEMPTS = 60;
    private static final Duration GLOBAL_WINDOW = Duration.ofMinutes(1);

    private static final Map<UUID, Tracker> TRACKERS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedDeque<Instant> GLOBAL_FAILURES = new ConcurrentLinkedDeque<>();

    private AuthAttemptService() {
    }

    public static Result register(AuthService auth, UUID player, String password, String repeatedPassword) {
        if (isLockedOut(player) || isGloballyLimited()) {
            return Result.TOO_MANY_ATTEMPTS;
        }
        Result result;
        if (auth.isRegistered(player)) {
            result = Result.ALREADY_REGISTERED;
        } else if (repeatedPassword != null && !Objects.equals(password, repeatedPassword)) {
            result = Result.PASSWORD_MISMATCH;
        } else {
            result = auth.register(player, password) ? Result.SUCCESS : Result.INVALID_PASSWORD;
        }
        updateTracking(player, result);
        return result;
    }

    public static Result login(AuthService auth, UUID player, String password) {
        if (isLockedOut(player) || isGloballyLimited()) {
            return Result.TOO_MANY_ATTEMPTS;
        }
        Result result;
        if (!auth.isRegistered(player)) {
            result = Result.NOT_REGISTERED;
        } else {
            result = auth.authenticate(player, password) ? Result.SUCCESS : Result.INVALID_CREDENTIALS;
        }
        updateTracking(player, result);
        return result;
    }

    public static boolean isLockedOut(UUID player) {
        Tracker tracker = TRACKERS.get(player);
        return tracker != null && tracker.isLockedOut(Instant.now());
    }

    public static Duration lockoutRemaining(UUID player) {
        Tracker tracker = TRACKERS.get(player);
        return tracker == null ? Duration.ZERO : tracker.remainingLockout(Instant.now());
    }

    public static void reset(UUID player) {
        TRACKERS.remove(player);
    }

    public static void purgeExpired() {
        Instant now = Instant.now();
        TRACKERS.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        while (!GLOBAL_FAILURES.isEmpty()
                && GLOBAL_FAILURES.peekFirst().isBefore(now.minus(GLOBAL_WINDOW))) {
            GLOBAL_FAILURES.pollFirst();
        }
    }

    private static void updateTracking(UUID player, Result result) {
        if (result == Result.SUCCESS) {
            reset(player);
            return;
        }
        recordFailure(player);
    }

    private static void recordFailure(UUID player) {
        Tracker tracker = TRACKERS.computeIfAbsent(player, key -> new Tracker());
        tracker.recordFailure(Instant.now());
        GLOBAL_FAILURES.addLast(Instant.now());
    }

    private static boolean isGloballyLimited() {
        Instant now = Instant.now();
        while (!GLOBAL_FAILURES.isEmpty()
                && GLOBAL_FAILURES.peekFirst().isBefore(now.minus(GLOBAL_WINDOW))) {
            GLOBAL_FAILURES.pollFirst();
        }
        return GLOBAL_FAILURES.size() >= MAX_GLOBAL_FAILED_ATTEMPTS;
    }

    private static final class Tracker {
        private final ArrayDeque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil;

        synchronized boolean isLockedOut(Instant now) {
            if (lockedUntil != null) {
                if (now.isBefore(lockedUntil)) {
                    return true;
                }
                lockedUntil = null;
                failures.clear();
            }
            prune(now);
            return false;
        }

        synchronized Duration remainingLockout(Instant now) {
            if (lockedUntil != null && now.isBefore(lockedUntil)) {
                return Duration.between(now, lockedUntil);
            }
            return Duration.ZERO;
        }

        synchronized void recordFailure(Instant now) {
            if (lockedUntil != null) {
                return;
            }
            prune(now);
            failures.addLast(now);
            if (failures.size() >= MAX_FAILED_ATTEMPTS) {
                lockedUntil = now.plus(LOCKOUT_DURATION);
                failures.clear();
            }
        }

        synchronized boolean isExpired(Instant now) {
            if (lockedUntil != null) {
                return !now.isBefore(lockedUntil);
            }
            prune(now);
            return failures.isEmpty();
        }

        private void prune(Instant now) {
            Instant cutoff = now.minus(ATTEMPT_WINDOW);
            while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
                failures.pollFirst();
            }
        }
    }
}
