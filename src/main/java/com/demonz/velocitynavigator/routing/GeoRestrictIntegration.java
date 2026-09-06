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

package com.demonz.velocitynavigator.routing;

import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class GeoRestrictIntegration {
    private static final String[] API_CLASS_NAMES = {
        "GeoRestrictAPI",
        "com.demonz.georestrict.GeoRestrictAPI",
        "com.demonz.georestrict.api.GeoRestrictAPI",
        "zip.linuxaddict.georestrict.api.GeoRestrictAPI"
    };

    private final boolean available;
    private final Logger logger;
    
    private final MethodHandle getCountryCodeHandle;
    private final MethodHandle isVpnHandle;
    private final MethodHandle getAsnHandle;
    private final MethodHandle getIspHandle;
    private final MethodHandle lookupHandle;

    public GeoRestrictIntegration(Logger logger) {
        this(logger, null);
    }

    GeoRestrictIntegration(Logger logger, Class<?> suppliedApiClass) {
        this.logger = logger != null ? logger : NOPLogger.NOP_LOGGER;

        boolean found = false;
        MethodHandle getCountryCodeTmp = null;
        MethodHandle isVpnTmp = null;
        MethodHandle getAsnTmp = null;
        MethodHandle getIspTmp = null;
        MethodHandle lookupTmp = null;
        
        try {
            Class<?> apiClass = suppliedApiClass != null ? suppliedApiClass : findApiClass();
            if (apiClass != null) {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                getCountryCodeTmp = lookup.findStatic(apiClass, "getCountryCode", MethodType.methodType(CompletableFuture.class, String.class));
                isVpnTmp = findOptional(lookup, apiClass, "isVpn");
                getAsnTmp = findOptional(lookup, apiClass, "getAsn");
                getIspTmp = findOptional(lookup, apiClass, "getIsp");
                lookupTmp = findOptional(lookup, apiClass, "lookup");

                found = true;
                this.logger.info("[VelocityNavigator] GeoRestrict detected - using GeoRestrict API for geo-location lookups.");
            }
        } catch (Exception e) {
            this.logger.debug("Failed to initialize GeoRestrict integration via reflection", e);
        }

        if (!found) {
            this.logger.info("[VelocityNavigator] GeoRestrict not detected - using built-in geo-location providers.");
        }

        this.available = found;
        this.getCountryCodeHandle = getCountryCodeTmp;
        this.isVpnHandle = isVpnTmp;
        this.getAsnHandle = getAsnTmp;
        this.getIspHandle = getIspTmp;
        this.lookupHandle = lookupTmp;
    }

    private static MethodHandle findOptional(MethodHandles.Lookup lookup, Class<?> apiClass, String name) {
        try {
            return lookup.findStatic(apiClass, name, MethodType.methodType(CompletableFuture.class, String.class));
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            return null;
        }
    }
    
    private Class<?> findApiClass() {
        for (String name : API_CLASS_NAMES) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    static boolean supportsApiClassName(String className) {
        return java.util.Arrays.asList(API_CLASS_NAMES).contains(className);
    }

    public boolean isAvailable() {
        return available;
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Optional<String>> lookupCountryCode(String ipAddress) {
        if (!available) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            CompletableFuture<String> future = (CompletableFuture<String>) getCountryCodeHandle.invoke(ipAddress);
            return future.thenApply(Optional::ofNullable).exceptionally(ex -> {
                logger.debug("Error looking up country code for {}", ipAddress, ex);
                return Optional.empty();
            });
        } catch (Throwable t) {
            logger.debug("Reflection error invoking getCountryCode", t);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Boolean> isVpn(String ipAddress) {
        if (!available || isVpnHandle == null) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) isVpnHandle.invoke(ipAddress);
            return future.exceptionally(ex -> {
                logger.debug("Error looking up VPN status for {}", ipAddress, ex);
                return false;
            });
        } catch (Throwable t) {
            logger.debug("Reflection error invoking isVpn", t);
            return CompletableFuture.completedFuture(false);
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Optional<String>> getAsn(String ipAddress) {
        if (!available || getAsnHandle == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            CompletableFuture<String> future = (CompletableFuture<String>) getAsnHandle.invoke(ipAddress);
            return future.thenApply(Optional::ofNullable).exceptionally(ex -> {
                logger.debug("Error looking up ASN for {}", ipAddress, ex);
                return Optional.empty();
            });
        } catch (Throwable t) {
            logger.debug("Reflection error invoking getAsn", t);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Optional<String>> getIsp(String ipAddress) {
        if (!available || getIspHandle == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            CompletableFuture<String> future = (CompletableFuture<String>) getIspHandle.invoke(ipAddress);
            return future.thenApply(Optional::ofNullable).exceptionally(ex -> {
                logger.debug("Error looking up ISP for {}", ipAddress, ex);
                return Optional.empty();
            });
        } catch (Throwable t) {
            logger.debug("Reflection error invoking getIsp", t);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @SuppressWarnings("unchecked")
    public CompletableFuture<Optional<Object>> lookup(String ipAddress) {
        if (!available || lookupHandle == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            CompletableFuture<Object> future = (CompletableFuture<Object>) lookupHandle.invoke(ipAddress);
            return future.thenApply(Optional::ofNullable).exceptionally(ex -> {
                logger.debug("Error performing raw GeoRestrict lookup for {}", ipAddress, ex);
                return Optional.empty();
            });
        } catch (Throwable t) {
            logger.debug("Reflection error invoking lookup", t);
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
