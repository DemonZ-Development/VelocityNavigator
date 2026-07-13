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

import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FirstRunHandler {

    private static final String VERSION_FILE = "last_known_version.dat";

    private FirstRunHandler() {
    }

    public static void checkAndShowWelcome(Logger logger, Path dataDir, String currentVersion, boolean welcomeEnabled, String wikiUrl) {
        if (!welcomeEnabled) {
            return;
        }

        Path versionFilePath = dataDir.resolve(VERSION_FILE);
        boolean isFreshInstall = !Files.exists(versionFilePath);
        boolean showWelcome = false;
        boolean showUpgrade = false;

        if (isFreshInstall) {
            showWelcome = true;
            try {
                Files.createDirectories(dataDir);
                Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("[VelocityNavigator] Failed to write version marker file: {}", e.getMessage());
            }
        } else {
            try {
                String lastKnownVersion = Files.readString(versionFilePath, StandardCharsets.UTF_8).trim();
                if (lastKnownVersion.isEmpty()) {
                    showWelcome = true;
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                } else if (!lastKnownVersion.equals(currentVersion)) {
                    showUpgrade = true;
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                logger.warn("[VelocityNavigator] Failed to read or update version marker file. Re-creating it... Error: {}", e.getMessage());
                showWelcome = true;
                try {
                    Files.writeString(versionFilePath, currentVersion, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                }
            }
        }

        if (showWelcome) {
            logger.info(" ");
            logger.info("=================================================================================");
            logger.info("              VelocityNavigator v{} — Getting Started", currentVersion);
            logger.info("=================================================================================");
            logger.info("  VelocityNavigator v{} is installed. Lobby routing and load", currentVersion);
            logger.info("  balancing for Velocity proxies.");
            logger.info("  ");
            logger.info("  To get started: ");
            logger.info("  1. Configure routing in navigator.toml");
            logger.info("  2. Select language in messages.toml, customize gui.toml, and manage lobbies in servers.toml");
            logger.info("  3. Reload configuration using: /vn reload");
            logger.info("  ");
            logger.info("  For detailed documentation, configuration options, and commands,");
            logger.info("  please visit our official wiki:");
            logger.info("  {}", wikiUrl);
            logger.info("  ");
            logger.info("  Feedback and support — Discord:");
            logger.info("  https://discord.com/invite/GYsTt96ypf");
            logger.info("=================================================================================");
            logger.info(" ");
        } else if (showUpgrade) {
            logger.info(" ");
            logger.info("=================================================================================");
            logger.info("              VelocityNavigator v{} — Upgraded Successfully", currentVersion);
            logger.info("=================================================================================");
            logger.info("  VelocityNavigator has been updated. Notable changes in this release:");
            logger.info("  ");
            logger.info("  • Universal Velocity/backend inventory selector with startup mode identification.");
            logger.info("  • Configurable language packs and custom-language workflow in messages.toml.");
            logger.info("  • Separate gui.toml for Java/Bedrock menus and servers.toml for command-managed lobbies.");
            logger.info("  • /vn bridge status reports detected backend bridge versions.");
            logger.info("  • Persistent player affinity and the optional HTML operations dashboard.");
            logger.info("  ");
            logger.info("  Read the v{} release notes and upgrade guide at:", currentVersion);
            logger.info("  {}", wikiUrl);
            logger.info("  ");
            logger.info("  Feedback and support — Discord:");
            logger.info("  https://discord.com/invite/GYsTt96ypf");
            logger.info("=================================================================================");
            logger.info(" ");
        }
    }
}
