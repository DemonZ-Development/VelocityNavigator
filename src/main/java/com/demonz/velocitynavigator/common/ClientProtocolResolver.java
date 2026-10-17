/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.common;

import java.util.UUID;

public final class ClientProtocolResolver {
    private ClientProtocolResolver() {}

    public static int resolve(UUID player, int fallback) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Class<?> apiType = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            int protocol = ((Number) apiType.getMethod("getPlayerVersion", UUID.class).invoke(api, player)).intValue();
            return protocol > 0 ? protocol : fallback;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return fallback;
        }
    }
}
