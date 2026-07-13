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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyAdminsOnJoinConfigTest {

    @Test
    void defaultsHaveNotifyAdminsOnJoinEnabled() {
        Config defaults = Config.defaults();
        assertTrue(defaults.notifyAdminsOnJoin(),
                "notifyAdminsOnJoin should default to true");
    }

    @Test
    void notifyAdminsOnJoinCanBeDisabled() {
        Config d = Config.defaults();
        Config disabled = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                d.notifyOnStartup(),
                false
        );
        assertFalse(disabled.notifyAdminsOnJoin(),
                "notifyAdminsOnJoin should be false when explicitly set");
        assertTrue(disabled.notifyOnStartup(),
                "notifyOnStartup should remain unchanged");
    }

    @Test
    void notifyAdminsOnJoinIndependentOfNotifyOnStartup() {
        Config d = Config.defaults();
        Config bothOff = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                false,
                false
        );
        assertFalse(bothOff.notifyOnStartup());
        assertFalse(bothOff.notifyAdminsOnJoin());

        Config onlyAdmin = new Config(
                d.configVersion(),
                d.commands(),
                d.routing(),
                d.healthChecks(),
                d.messages(),
                d.updateChecker(),
                d.metrics(),
                d.debug(),
                d.circuitBreaker(),
                d.degradation(),
                d.geoRouting(),
                false,
                true
        );
        assertFalse(onlyAdmin.notifyOnStartup());
        assertTrue(onlyAdmin.notifyAdminsOnJoin());
    }
}
