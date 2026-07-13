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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockFormServiceFormattingTest {

    private String strip(String input, boolean stripAdvanced) throws Exception {
        Config defaults = Config.defaults();
        Config config = new Config(
                defaults.configVersion(),
                defaults.commands(),
                defaults.routing(),
                defaults.healthChecks(),
                defaults.messages(),
                defaults.updateChecker(),
                defaults.metrics(),
                defaults.debug(),
                defaults.circuitBreaker(),
                defaults.degradation(),
                defaults.geoRouting(),
                defaults.notifyOnStartup(),
                defaults.notifyAdminsOnJoin(),
                defaults.startup(),
                defaults.lobbyFallback(),
                new Config.BedrockSettings(true, true, stripAdvanced, true)
        );
        Method method = BedrockFormService.class.getDeclaredMethod(
                "stripFormattingCodesIfRequested", String.class, Config.class
        );
        method.setAccessible(true);
        return (String) method.invoke(null, input, config);
    }

    @Test
    void stripsLowercaseColorCodes() throws Exception {
        assertEquals("Hello", strip("&aHello", true));
        assertEquals("Hello", strip("§aHello", true));
    }

    @Test
    void stripsUppercaseColorCodes() throws Exception {
        assertEquals("Hello", strip("&AHello", true));
        assertEquals("Hello", strip("§AHello", true));
        assertEquals("Bold", strip("&LBold", true));
        assertEquals("Bold", strip("§LBold", true));
    }

    @Test
    void stripsMixedCaseCodes() throws Exception {
        assertEquals("text", strip("&C&L&Ktext", true));
        assertEquals("text", strip("§C§L§Ktext", true));
    }

    @Test
    void stripsMiniMessageTags() throws Exception {
        assertEquals("Hello", strip("<red>Hello</red>", true));
        assertEquals("World", strip("<gradient:#fff:#000>World", true));
    }

    @Test
    void convertsColorsWhenStrippingDisabled() throws Exception {
        assertEquals("§aHello", strip("&aHello", false));
    }

    @Test
    void returnsEmptyForNullInput() throws Exception {
        assertEquals("", strip(null, true));
    }
}
