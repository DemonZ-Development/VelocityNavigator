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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class UpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/velocitynavigator/version";

    private final Logger logger;
    private final String currentVersion;
    private final HttpClient httpClient;
    private final UpdateStatus updateStatus = new UpdateStatus();

    private final AtomicInteger backoffMultiplier = new AtomicInteger(1);
    private volatile Instant nextAllowedCheck = Instant.MIN;
    private final Object backoffLock = new Object();

    public UpdateChecker(Logger logger, String currentVersion) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.currentVersion = Objects.requireNonNull(currentVersion, "currentVersion");
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "DemonZDevelopment/VelocityNavigator/" + currentVersion + " (https://modrinth.com/plugin/velocitynavigator)")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> processResponse(response, settings))
                .exceptionally(throwable -> {
                    String message = "Failed to check Modrinth for updates: " + throwable.getMessage();
                    updateStatus.recordFailure(message);
                    logger.warn(message);
                    return null;
                });
    }

    private void processResponse(HttpResponse<String> response, Config.UpdateCheckerSettings settings) {
        if (response.statusCode() == 429) {
            synchronized (backoffLock) {
                int interval = Math.max(30, settings.checkIntervalMinutes());
                int maxMultiplier = (int) Math.ceil(240.0 / interval);
                int currentMultiplier = backoffMultiplier.updateAndGet(v -> Math.min(v * 2, maxMultiplier));
                int backoffMinutes = Math.min(240, interval * currentMultiplier);
                nextAllowedCheck = Instant.now().plus(Duration.ofMinutes(backoffMinutes));
                String message = "Modrinth returned 429 Too Many Requests. Applying update check backoff for " + backoffMinutes + " minutes.";
                updateStatus.recordFailure(message);
                logger.warn("[VelocityNavigator] {}", message);
            }
            return;
        }

        if (response.statusCode() != 200) {
            String message = "Modrinth update check returned HTTP " + response.statusCode() + ".";
            updateStatus.recordFailure(message);
            logger.warn(message);
            return;
        }

        synchronized (backoffLock) {
            backoffMultiplier.set(1);
            nextAllowedCheck = Instant.MIN;
        }

        try {
            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            SemanticVersion installed = SemanticVersion.parse(currentVersion);
            SemanticVersion latestAllowed = installed;
            String latestAllowedRaw = currentVersion;

            for (JsonElement element : versions) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject version = element.getAsJsonObject();
                String remoteVersion = stringField(version, "version_number");
                Config.RemoteVersionType remoteType = Config.RemoteVersionType.fromString(stringField(version, "version_type"));
                if (!isAllowed(settings.channel(), installed.isPrerelease(), remoteType)) {
                    continue;
                }
                SemanticVersion parsed = SemanticVersion.parse(remoteVersion);
                if (parsed.compareTo(latestAllowed) > 0) {
                    latestAllowed = parsed;
                    latestAllowedRaw = remoteVersion;
                }
            }

            boolean updateAvailable = latestAllowed.compareTo(installed) > 0;
            updateStatus.recordSuccess(latestAllowedRaw, updateAvailable);
            if (updateAvailable && !settings.silent()) {
                logger.info("VelocityNavigator update available: {} -> {}", currentVersion, latestAllowedRaw);
                logger.info("Download: https://modrinth.com/plugin/velocitynavigator");
            } else if (!updateAvailable && !settings.silent()) {
                logger.info("VelocityNavigator is up to date ({}).", currentVersion);
            }
        } catch (RuntimeException exception) {
            String message = "Unable to parse Modrinth update response: " + exception.getMessage();
            updateStatus.recordFailure(message);
            logger.warn(message);
        }
    }

    boolean isAllowed(Config.UpdateChannel channel, boolean installedIsPrerelease, Config.RemoteVersionType remoteType) {
        return switch (channel) {
            case RELEASE -> remoteType == Config.RemoteVersionType.RELEASE;
            case BETA -> remoteType == Config.RemoteVersionType.RELEASE || remoteType == Config.RemoteVersionType.BETA;
            case ALPHA -> true;
        };
    }

    private String stringField(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null ? "" : value.getAsString();
    }
}
