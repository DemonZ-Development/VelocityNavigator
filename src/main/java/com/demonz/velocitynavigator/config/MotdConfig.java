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
package com.demonz.velocitynavigator.config;

import java.util.List;

public record MotdConfig(
    boolean enabled,
    String mode,
    long rotationIntervalSeconds,
    List<String> motds,
    MaintenanceSection maintenance
) {
    public record MaintenanceSection(
        boolean overrideMotdOnMaintenance,
        List<String> motds
    ) {}

    public static MotdConfig defaults() {
        return new MotdConfig(
            true,
            "ROTATING",
            5L,
            List.of(
                "<gradient:#8EF7FF:#D9F7FF><bold>VelocityNavigator Network</bold></gradient>\n<gray>High-performance proxy routing system</gray>",
                "<aqua>Welcome to our Network!</aqua> <gray>[<white>{online}/{max}</white> Players]</gray>\n<yellow>Join now for low-latency gameplay!</yellow>"
            ),
            new MaintenanceSection(
                true,
                List.of(
                    "<red><bold>NETWORK MAINTENANCE</bold></red>\n<gray>{maintenance_reason}</gray>"
                )
            )
        );
    }
}
