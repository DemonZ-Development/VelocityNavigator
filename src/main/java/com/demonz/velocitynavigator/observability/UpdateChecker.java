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
package com.demonz.velocitynavigator.observability;

import com.demonz.velocitynavigator.common.DemonzClient;
import com.demonz.velocitynavigator.common.ModrinthClient;
import com.demonz.velocitynavigator.common.SemanticVersion;
import com.demonz.velocitynavigator.config.Config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpdateChecker {

    private final Logger logger;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final UpdateStatus updateStatus = new UpdateStatus();

    private final AtomicInteger backoffMultiplier = new AtomicInteger(1);
    private volatile Instant nextAllowedCheck = Instant.MIN;
    private final Object backoffLock = new Object();

    public UpdateChecker(Logger logger, String currentVersion) {
        this(logger, currentVersion, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    UpdateChecker(Logger logger, String currentVersion, HttpClient httpClient) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    public UpdateStatus status() {
        return updateStatus;
    }

    public CompletableFuture<Void> checkAsync(Config.UpdateCheckerSettings settings) {
        if (!settings.enabled()) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (backoffLock) {
            Instant now = Instant.now();
            if (now.isBefore(nextAllowedCheck)) {
                logger.debug("[VelocityNavigator] Update check skipped due to active 429 backoff.");
                return CompletableFuture.completedFuture(null);
            }
        }

        HttpRequest demonzRequest = HttpRequest.newBuilder()
                .uri(URI.create(DemonzClient.UPDATES_URL))
                .header("User-Agent", DemonzClient.userAgent(currentVersion, false))
                .GET()
                .build();

        HttpRequest modrinthRequest = HttpRequest.newBuilder()
                .uri(URI.create(ModrinthClient.API_URL))
                .header("User-Agent", ModrinthClient.userAgent(currentVersion, false))
                .GET()
                .build();

        CompletableFuture<FetchResult> demonzFuture = httpClient.sendAsync(demonzRequest, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return new FetchResult("DemonZ", 0, null, throwable.getMessage());
                    }
                    return new FetchResult("DemonZ", response.statusCode(), response.body(), null);
                });

        CompletableFuture<FetchResult> modrinthFuture = httpClient.sendAsync(modrinthRequest, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return new FetchResult("Modrinth", 0, null, throwable.getMessage());
                    }
                    return new FetchResult("Modrinth", response.statusCode(), response.body(), null);
                });

        return demonzFuture.thenAcceptBoth(modrinthFuture, (demonzRes, modrinthRes) -> {
            processResponses(demonzRes, modrinthRes, settings);
        }).exceptionally(throwable -> {
            String message = "Failed to check for updates: " + throwable.getMessage();
            updateStatus.recordFailure(message);
            logger.warn(message);
            return null;
        });
    }

    private void processResponses(FetchResult demonzRes, FetchResult modrinthRes, Config.UpdateCheckerSettings settings) {
        if (demonzRes.isRateLimited() || modrinthRes.isRateLimited()) {
            synchronized (backoffLock) {
                int interval = Math.max(30, settings.checkIntervalMinutes());
                int maxMultiplier = (int) Math.ceil(240.0 / interval);
                int currentMultiplier = backoffMultiplier.updateAndGet(v -> Math.min(v * 2, maxMultiplier));
                int backoffMinutes = Math.min(240, interval * currentMultiplier);
                nextAllowedCheck = Instant.now().plus(Duration.ofMinutes(backoffMinutes));
                String rateLimitedSource = demonzRes.isRateLimited() && modrinthRes.isRateLimited()
                        ? "DemonZ and Modrinth"
                        : (demonzRes.isRateLimited() ? "DemonZ" : "Modrinth");
                logger.warn("[VelocityNavigator] {} returned 429 Too Many Requests. Applying update check backoff for {} minutes.", rateLimitedSource, backoffMinutes);
            }
        }

        SemanticVersion installed = SemanticVersion.parse(currentVersion);
        String demonzVersion = null;
        String modrinthVersion = null;
        List<String> errors = new ArrayList<>();

        if (demonzRes.isSuccess()) {
            try {
                JsonObject root = JsonParser.parseString(demonzRes.body()).getAsJsonObject();
                Optional<String> best = DemonzClient.bestVersionIn(root, release -> isAllowed(
                        settings.channel(),
                        installed.isPrerelease(),
                        Config.RemoteVersionType.fromString(release.type().name())));
                if (best.isPresent()) {
                    demonzVersion = best.get();
                }
            } catch (RuntimeException exception) {
                errors.add("Unable to parse DemonZ update response: " + exception.getMessage());
            }
        } else if (!demonzRes.isRateLimited()) {
            String err = demonzRes.error() != null ? demonzRes.error() : "HTTP " + demonzRes.statusCode();
            errors.add("DemonZ update check failed: " + err);
        }

        if (modrinthRes.isSuccess()) {
            try {
                JsonArray versions = JsonParser.parseString(modrinthRes.body()).getAsJsonArray();
                Optional<String> best = ModrinthClient.bestVersionIn(versions, release -> isAllowed(
                        settings.channel(),
                        installed.isPrerelease(),
                        Config.RemoteVersionType.fromString(release.type().name())));
                if (best.isPresent()) {
                    modrinthVersion = best.get();
                }
            } catch (RuntimeException exception) {
                errors.add("Unable to parse Modrinth update response: " + exception.getMessage());
            }
        } else if (!modrinthRes.isRateLimited()) {
            String err = modrinthRes.error() != null ? modrinthRes.error() : "HTTP " + modrinthRes.statusCode();
            errors.add("Modrinth update check failed: " + err);
        }

        String bestRaw = null;
        if (demonzVersion != null && modrinthVersion != null) {
            SemanticVersion v1 = SemanticVersion.parse(demonzVersion);
            SemanticVersion v2 = SemanticVersion.parse(modrinthVersion);
            bestRaw = v1.compareTo(v2) >= 0 ? demonzVersion : modrinthVersion;
        } else if (demonzVersion != null) {
            bestRaw = demonzVersion;
        } else if (modrinthVersion != null) {
            bestRaw = modrinthVersion;
        }

        if (bestRaw == null) {
            String combinedError;
            if (demonzRes.isRateLimited() && modrinthRes.isRateLimited()) {
                combinedError = "DemonZ and Modrinth returned 429 Too Many Requests.";
            } else if (!errors.isEmpty()) {
                combinedError = String.join("; ", errors);
            } else {
                combinedError = "No update sources returned a valid version.";
            }
            updateStatus.recordFailure(combinedError);
            logger.warn(combinedError);
            return;
        }

        if (!demonzRes.isRateLimited() && !modrinthRes.isRateLimited()) {
            synchronized (backoffLock) {
                backoffMultiplier.set(1);
                nextAllowedCheck = Instant.MIN;
            }
        }

        SemanticVersion latestAllowed = SemanticVersion.parse(bestRaw);
        boolean updateAvailable = latestAllowed.compareTo(installed) > 0;
        updateStatus.recordSuccess(bestRaw, updateAvailable);

        if (updateAvailable && !settings.silent()) {
            logger.info("VelocityNavigator update available: {} -> {}", currentVersion, bestRaw);
            logger.info("Website: {}", DemonzClient.PROJECT_URL);
            logger.info("Modrinth: {}", ModrinthClient.DOWNLOAD_URL);
        } else if (!updateAvailable && !settings.silent()) {
            logger.info("VelocityNavigator is up to date ({}).", currentVersion);
        }
    }

    public static boolean isAllowed(Config.UpdateChannel channel, boolean installedIsPrerelease, Config.RemoteVersionType remoteType) {
        return switch (channel) {
            case RELEASE -> remoteType == Config.RemoteVersionType.RELEASE;
            case BETA -> remoteType == Config.RemoteVersionType.RELEASE || remoteType == Config.RemoteVersionType.BETA;
            case ALPHA -> true;
        };
    }

    private record FetchResult(String source, int statusCode, String body, String error) {
        boolean isSuccess() {
            return error == null && statusCode == 200 && body != null;
        }

        boolean isRateLimited() {
            return statusCode == 429;
        }
    }
}
