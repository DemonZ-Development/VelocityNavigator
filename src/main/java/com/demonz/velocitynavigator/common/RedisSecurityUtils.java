/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

public final class RedisSecurityUtils {

    private RedisSecurityUtils() {
    }

    public static String sign(JsonObject payload, String secret) {
        return RedisRegistrationSigner.sign(payload, secret);
    }

    public static boolean fresh(long timestamp, long now, int maxAgeSeconds) {
        long maximumSkew = Math.max(5, maxAgeSeconds) * 1000L;
        return timestamp > 0 && timestamp >= now - maximumSkew && timestamp <= now + maximumSkew;
    }

    public static boolean hostAllowed(String host, List<String> rules) {
        if (rules == null || rules.isEmpty()) return true;
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            String candidate = rule.toLowerCase(Locale.ROOT);
            if (candidate.equals(normalized)) return true;
            if (candidate.startsWith("*.") && normalized.endsWith(candidate.substring(1))) return true;
        }
        return false;
    }
}
