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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunHandlerTest {

    @TempDir
    Path tempDir;

    private Logger createMockLogger(List<String> infoLogs, List<String> warnLogs) {
        return (Logger) java.lang.reflect.Proxy.newProxyInstance(
                Logger.class.getClassLoader(),
                new Class<?>[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("info")) {
                        if (args.length == 1) {
                            infoLogs.add((String) args[0]);
                        } else if (args.length > 1) {
                            String format = ((String) args[0]).replace("{}", "%s");
                            Object[] formatArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
                            infoLogs.add(String.format(format, formatArgs));
                        }
                    } else if (method.getName().equals("warn")) {
                        if (args.length == 1) {
                            warnLogs.add((String) args[0]);
                        } else if (args.length > 1) {
                            String format = ((String) args[0]).replace("{}", "%s");
                            Object[] formatArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
                            warnLogs.add(String.format(format, formatArgs));
                        }
                    }
                    return null;
                }
        );
    }

    @Test
    void showsWelcomeMessageAndCreatesFileOnFreshInstall() throws Exception {
        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        Logger logger = createMockLogger(infoLogs, warnLogs);

        FirstRunHandler.checkAndShowWelcome(logger, tempDir, "4.1.0", true, "https://wiki.example.com");

        Path versionFile = tempDir.resolve("last_known_version.dat");
        assertTrue(Files.exists(versionFile));
        assertEquals("4.1.0", Files.readString(versionFile, StandardCharsets.UTF_8).trim());

        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Getting Started")));
        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("https://wiki.example.com")));
    }

    @Test
    void isSilentOnSameVersion() throws Exception {
        Path versionFile = tempDir.resolve("last_known_version.dat");
        Files.writeString(versionFile, "4.1.0", StandardCharsets.UTF_8);

        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        Logger logger = createMockLogger(infoLogs, warnLogs);

        FirstRunHandler.checkAndShowWelcome(logger, tempDir, "4.1.0", true, "https://wiki.example.com");

        assertTrue(infoLogs.isEmpty());
        assertTrue(warnLogs.isEmpty());
    }

    @Test
    void showsUpgradeDigestOnUpgrade() throws Exception {
        Path versionFile = tempDir.resolve("last_known_version.dat");
        Files.writeString(versionFile, "4.0.0", StandardCharsets.UTF_8);

        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        Logger logger = createMockLogger(infoLogs, warnLogs);

        FirstRunHandler.checkAndShowWelcome(logger, tempDir, "4.1.0", true, "https://wiki.example.com");

        assertEquals("4.1.0", Files.readString(versionFile, StandardCharsets.UTF_8).trim());

        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Upgraded Successfully")));
        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Universal Velocity/backend inventory selector")));
    }

    @Test
    void recoversFromCorruptFile() throws Exception {
        Path versionFile = tempDir.resolve("last_known_version.dat");
        Files.writeString(versionFile, "", StandardCharsets.UTF_8);

        List<String> infoLogs = new ArrayList<>();
        List<String> warnLogs = new ArrayList<>();
        Logger logger = createMockLogger(infoLogs, warnLogs);

        FirstRunHandler.checkAndShowWelcome(logger, tempDir, "4.1.0", true, "https://wiki.example.com");

        assertEquals("4.1.0", Files.readString(versionFile, StandardCharsets.UTF_8).trim());

        assertTrue(infoLogs.stream().anyMatch(log -> log.contains("Getting Started") || log.contains("Upgraded Successfully")));
    }
}
