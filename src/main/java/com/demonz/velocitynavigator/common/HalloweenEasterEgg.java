/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

public final class HalloweenEasterEgg {

    public static final Duration CHECK_INTERVAL = Duration.ofHours(6);
    public static final String GREETING = "Happy Halloween from DemonZDevelopment! Thank you for using our services.";

    public record Notice(LocalDate date, String message, boolean halloween) {}

    private final Clock clock;

    public HalloweenEasterEgg() {
        this(Clock.systemDefaultZone());
    }

    public HalloweenEasterEgg(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Optional<Notice> currentNotice() {
        LocalDate today = LocalDate.now(clock);
        if (today.getMonth() != Month.OCTOBER) return Optional.empty();
        long days = ChronoUnit.DAYS.between(today, today.withDayOfMonth(31));
        String message = days == 0 ? GREETING : days + (days == 1 ? " day" : " days") + " until Halloween!";
        return Optional.of(new Notice(today, message, days == 0));
    }

    public boolean isCurrent(Notice notice) {
        return notice != null && currentNotice().filter(notice::equals).isPresent();
    }
}
