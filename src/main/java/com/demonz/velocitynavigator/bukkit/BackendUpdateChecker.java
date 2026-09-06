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
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.common.ModrinthClient;
import com.demonz.velocitynavigator.common.SemanticVersion;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class BackendUpdateChecker {

    private final Logger logger;
    private final String currentVersion;
    private final String userAgent;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = null;
    private final AtomicInteger backoffMinutes = new AtomicInteger(0);

    private ScheduledExecutorService executorService;

    public BackendUpdateChecker(Logger logger, String currentVersion) {
        this.logger = logger;
        this.currentVersion = currentVersion;
        this.userAgent = ModrinthClient.userAgent(currentVersion, true);
    }

    public void checkAsync() {
        Thread thread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ModrinthClient.API_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode == 429) {
                    int backoff = backoffMinutes.updateAndGet(v -> v == 0 ? 5 : Math.min(1440, v * 2));
                    logger.warning("[VelocityNavigator] Modrinth rate limit hit. Backing off for " + backoff + " minutes.");
                    return;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    logger.warning("[VelocityNavigator] Backend update check failed: HTTP " + responseCode);
                    return;
                }

                backoffMinutes.set(0);

                try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                    JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
                    Optional<String> latestRelease = ModrinthClient.bestVersionIn(versions, r -> r.type() == ModrinthClient.VersionType.RELEASE);
                    SemanticVersion current = SemanticVersion.parse(currentVersion);
                    if (latestRelease.isPresent()) {
                        SemanticVersion latest = SemanticVersion.parse(latestRelease.get());
                        if (current.compareTo(latest) < 0) {
                            this.updateAvailable = true;
                            this.latestVersion = latestRelease.get();
                            logger.info("[VelocityNavigator] Update available: " + currentVersion + " \u2192 " + latestVersion + ". Download: " + ModrinthClient.DOWNLOAD_URL);
                        } else {
                            logger.info("[VelocityNavigator] Backend plugin is up to date (" + currentVersion + ").");
                        }
                    }
                }

            } catch (Exception e) {
                logger.warning("[VelocityNavigator] Backend update check failed: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void schedulePeriodicCheck(int intervalMinutes) {
        schedulePeriodicCheck(null, intervalMinutes);
    }

    public void schedulePeriodicCheck(Plugin plugin, int intervalMinutes) {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "VelocityNavigator-UpdateChecker");
                t.setDaemon(true);
                return t;
            });
        }

        executorService.scheduleAtFixedRate(() -> {
            if (backoffMinutes.get() > 0) {
                int remaining = backoffMinutes.updateAndGet(v -> Math.max(0, v - intervalMinutes));
                if (remaining > 0) {
                    return;
                }
            }
            checkAsync();
        }, 0, intervalMinutes, TimeUnit.MINUTES);
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }
}
