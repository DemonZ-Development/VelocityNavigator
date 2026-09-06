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

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class GeoRoutingService implements Closeable {

    @FunctionalInterface
    public interface IpApiFetcher {
        String fetch(String ip);
    }

    private static final IpApiFetcher DEFAULT_IP_API_FETCHER = GeoRoutingService::defaultIpApiFetch;

    private static String defaultIpApiFetch(String ip) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://ip-api.com/line/" + ip + "?fields=countryCode");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(1500);
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().toUpperCase();
            }
        } catch (IOException e) {
            throw new RuntimeException("IP-API request failed", e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private final boolean enabled;
    private final String databasePath;
    private final boolean fallbackIpApi;
    private final String provider;
    private final String fallbackMode;
    private final Logger logger;
    private final IpApiFetcher ipApiFetcher;
    private DatabaseReader reader;
    private final Map<String, String> ipApiCache = new ConcurrentHashMap<>();
    private final GeoRestrictIntegration geoRestrictIntegration;

    public GeoRoutingService(boolean enabled, String databasePath) {
        this(enabled, databasePath, "auto", true, "ip_api", NOPLogger.NOP_LOGGER);
    }

    public GeoRoutingService(boolean enabled, String databasePath, boolean fallbackIpApi, Logger logger) {
        this(enabled, databasePath, "auto", fallbackIpApi, "ip_api", logger);
    }

    public GeoRoutingService(boolean enabled, String databasePath, boolean fallbackIpApi, IpApiFetcher fetcher) {
        this(enabled, databasePath, "auto", fallbackIpApi, "ip_api", NOPLogger.NOP_LOGGER,
                fetcher, null);
    }

    public GeoRoutingService(boolean enabled, String databasePath, boolean fallbackIpApi, Logger logger, IpApiFetcher fetcher) {
        this(enabled, databasePath, "auto", fallbackIpApi, "ip_api", logger, fetcher, null);
    }

    public GeoRoutingService(
            boolean enabled,
            String databasePath,
            String provider,
            boolean fallbackIpApi,
            String fallbackMode,
            Logger logger
    ) {
        this(enabled, databasePath, provider, fallbackIpApi, fallbackMode, logger,
                DEFAULT_IP_API_FETCHER, null);
    }

    GeoRoutingService(
            boolean enabled,
            String databasePath,
            String provider,
            boolean fallbackIpApi,
            String fallbackMode,
            Logger logger,
            IpApiFetcher fetcher,
            GeoRestrictIntegration geoRestrictIntegration
    ) {
        this.enabled = enabled;
        this.databasePath = databasePath;
        this.fallbackIpApi = fallbackIpApi;
        this.provider = normalizeProvider(provider);
        this.fallbackMode = fallbackMode == null ? "ip_api" : fallbackMode.trim().toLowerCase(Locale.ROOT);
        this.logger = logger != null ? logger : NOPLogger.NOP_LOGGER;
        this.ipApiFetcher = fetcher != null ? fetcher : DEFAULT_IP_API_FETCHER;
        this.geoRestrictIntegration = geoRestrictIntegration != null
                ? geoRestrictIntegration
                : new GeoRestrictIntegration(this.logger);
        initializeReader();
    }

    private static String normalizeProvider(String provider) {
        if (provider == null) return "auto";
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "georestrict", "maxmind", "ip_api", "auto" -> provider.trim().toLowerCase(Locale.ROOT);
            default -> "auto";
        };
    }

    private void initializeReader() {
        if (!enabled || databasePath == null || databasePath.isBlank()) {
            return;
        }
        try {
            File dbFile = new File(databasePath);
            if (dbFile.exists()) {
                this.reader = new DatabaseReader.Builder(dbFile).build();
            } else {
                logger.warn("[VelocityNavigator] GeoIP database file not found at: {}", databasePath);
            }
        } catch (Exception e) {
            logger.error("[VelocityNavigator] Failed to initialize GeoIP2 reader: {}", e.getMessage());
        }
    }

    public Optional<String> lookupCountry(InetAddress address) {
        if (!enabled || address == null) {
            return Optional.empty();
        }

        for (String candidateProvider : providerOrder()) {
            Optional<String> result = switch (candidateProvider) {
                case "georestrict" -> lookupViaGeoRestrict(address);
                case "maxmind" -> lookupViaMaxMind(address);
                case "ip_api" -> lookupViaIpApi(address);
                default -> Optional.empty();
            };
            if (result.isPresent()) return result;
        }

        return Optional.empty();
    }

    public CompletableFuture<String> lookupCountryAsync(InetAddress address) {
        if (!enabled || address == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> lookupCountry(address).orElse(null));
    }

    private List<String> providerOrder() {
        if ("auto".equals(provider)) {
            return fallbackIpApi
                    ? List.of("georestrict", "maxmind", "ip_api")
                    : List.of("georestrict", "maxmind");
        }
        if (!fallbackIpApi) {
            return List.of(provider);
        }
        if (isProviderName(fallbackMode)) {
            String configuredFallback = normalizeProvider(fallbackMode);
            if (!"auto".equals(configuredFallback) && !provider.equals(configuredFallback)) {
                return List.of(provider, configuredFallback);
            }
            if (!"ip_api".equals(provider)) {
                return List.of(provider, "ip_api");
            }
        }
        return List.of(provider);
    }

    private static boolean isProviderName(String value) {
        if (value == null) return false;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "georestrict", "maxmind", "ip_api", "auto" -> true;
            default -> false;
        };
    }

    private Optional<String> lookupViaGeoRestrict(InetAddress address) {
        if (!geoRestrictIntegration.isAvailable()) return Optional.empty();
        try {
            Optional<String> result = geoRestrictIntegration.lookupCountryCode(address.getHostAddress()).join();
            if (result.isPresent()) {
                String normalized = normalizeCountry(result.get());
                if (normalized != null) {
                    logger.debug("[VelocityNavigator] Geo lookup via GeoRestrict: {} -> {}",
                            address.getHostAddress(), normalized);
                    return Optional.of(normalized);
                }
            }
        } catch (Exception e) {
            logger.debug("[VelocityNavigator] GeoRestrict lookup failed, falling back: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> lookupViaMaxMind(InetAddress address) {
        if (reader == null) return Optional.empty();
        try {
            CountryResponse resp = reader.country(address);
            if (resp != null && resp.getCountry() != null) {
                String normalized = normalizeCountry(resp.getCountry().getIsoCode());
                if (normalized != null) {
                    logger.debug("[VelocityNavigator] Geo lookup via MaxMind: {} -> {}",
                            address.getHostAddress(), normalized);
                    return Optional.of(normalized);
                }
            }
        } catch (Exception e) {
            logger.warn("[VelocityNavigator] GeoIP country lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<String> lookupViaIpApi(InetAddress address) {
        String ip = address.getHostAddress();
        String cached = ipApiCache.get(ip);
        if (cached != null) return Optional.of(cached);
        try {
            String normalized = normalizeCountry(ipApiFetcher.fetch(ip));
            if (normalized != null) {
                ipApiCache.put(ip, normalized);
                logger.debug("[VelocityNavigator] Geo lookup via ip-api: {} -> {}", ip, normalized);
                return Optional.of(normalized);
            }
        } catch (Exception e) {
            logger.warn("[VelocityNavigator] IP-API lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private static String normalizeCountry(String country) {
        if (country == null) return null;
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        return normalized.length() == 2 ? normalized : null;
    }

    public Optional<String> lookupContinent(InetAddress address) {
        if (!enabled || address == null || reader == null) {
            return Optional.empty();
        }
        try {
            CountryResponse resp = reader.country(address);
            if (resp != null && resp.getContinent() != null && resp.getContinent().getCode() != null) {
                return Optional.of(resp.getContinent().getCode().toUpperCase());
            }
        } catch (Exception e) {
            logger.warn("[VelocityNavigator] GeoIP continent lookup failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public CompletableFuture<Boolean> isVpnAsync(InetAddress address) {
        if (!enabled || address == null) {
            return CompletableFuture.completedFuture(false);
        }
        return geoRestrictIntegration.isVpn(address.getHostAddress());
    }

    public CompletableFuture<Optional<String>> getAsnAsync(InetAddress address) {
        if (!enabled || address == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return geoRestrictIntegration.getAsn(address.getHostAddress());
    }

    public CompletableFuture<Optional<String>> getIspAsync(InetAddress address) {
        if (!enabled || address == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return geoRestrictIntegration.getIsp(address.getHostAddress());
    }

    public GeoRestrictIntegration geoRestrictIntegration() {
        return geoRestrictIntegration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String databasePath() {
        return databasePath;
    }

    public String provider() {
        return provider;
    }

    @Override
    public void close() {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {
            }
            reader = null;
        }
        ipApiCache.clear();
    }
}
