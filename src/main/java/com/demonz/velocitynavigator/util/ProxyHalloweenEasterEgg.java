/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.util;

import com.demonz.velocitynavigator.common.HalloweenEasterEgg;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.util.Objects;

public final class ProxyHalloweenEasterEgg {

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final HalloweenEasterEgg calendar;
    private volatile Object session;
    private ScheduledTask task;

    public ProxyHalloweenEasterEgg(Object plugin, ProxyServer server, Logger logger) {
        this(plugin, server, logger, new HalloweenEasterEgg());
    }

    public ProxyHalloweenEasterEgg(Object plugin, ProxyServer server, Logger logger, HalloweenEasterEgg calendar) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.calendar = Objects.requireNonNull(calendar, "calendar");
    }

    public synchronized void start() {
        stop();
        Object currentSession = new Object();
        session = currentSession;
        task = server.getScheduler().buildTask(plugin, () -> announce(currentSession))
                .delay(HalloweenEasterEgg.CHECK_INTERVAL)
                .repeat(HalloweenEasterEgg.CHECK_INTERVAL)
                .schedule();
        announce(currentSession);
    }

    private void announce(Object currentSession) {
        if (session != currentSession) return;
        calendar.currentNotice().ifPresent(notice -> {
            if (session == currentSession && calendar.isCurrent(notice)) {
                logger.info("[Halloween] {}", notice.message());
            }
        });
    }

    public synchronized void stop() {
        session = null;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
