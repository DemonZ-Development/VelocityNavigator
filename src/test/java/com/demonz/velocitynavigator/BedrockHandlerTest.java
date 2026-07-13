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

import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BedrockHandlerTest {

    private ProxyServer createMockProxyServer(boolean hasGeyser, boolean hasFloodgate) {
        PluginManager mockPM = (PluginManager) java.lang.reflect.Proxy.newProxyInstance(
                PluginManager.class.getClassLoader(),
                new Class<?>[]{PluginManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlugin") && args.length == 1) {
                        String name = (String) args[0];
                        if (name.equalsIgnoreCase("geyser") || name.equalsIgnoreCase("geyser-velocity")) {
                            return hasGeyser ? Optional.of(new Object()) : Optional.empty();
                        }
                        if (name.equalsIgnoreCase("floodgate")) {
                            return hasFloodgate ? Optional.of(new Object()) : Optional.empty();
                        }
                        return Optional.empty();
                    }
                    return null;
                }
        );

        return (ProxyServer) java.lang.reflect.Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPluginManager")) {
                        return mockPM;
                    }
                    return null;
                }
        );
    }

    @Test
    void bedrockSupportCheckRespectsConfig() {
        ProxyServer mockProxy = createMockProxyServer(false, false);
        BedrockHandler handler = new BedrockHandler(mockProxy);

        assertFalse(handler.isBedrockSupported(null));

        Config defaults = Config.defaults();
        Config configEnabled = new Config(
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
                new Config.BedrockSettings(true, false, true, true)
        );
        assertTrue(handler.isBedrockSupported(configEnabled));

        Config configAutoNoPlugins = new Config(
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
                new Config.BedrockSettings(false, true, true, true)
        );
        assertEquals(FloodgateIntegration.isAvailable(), handler.isBedrockSupported(configAutoNoPlugins));
    }

    @Test
    void bedrockSupportDetectsGeyserAndFloodgatePlugins() {
        ProxyServer mockProxyWithGeyser = createMockProxyServer(true, false);
        BedrockHandler handlerGeyser = new BedrockHandler(mockProxyWithGeyser);

        Config defaults = Config.defaults();
        Config configAuto = new Config(
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
                new Config.BedrockSettings(false, true, true, true)
        );
        assertTrue(handlerGeyser.isBedrockSupported(configAuto));

        ProxyServer mockProxyWithFloodgate = createMockProxyServer(false, true);
        BedrockHandler handlerFloodgate = new BedrockHandler(mockProxyWithFloodgate);
        assertTrue(handlerFloodgate.isBedrockSupported(configAuto));
    }

    @Test
    void formatForBedrockStripsAdvancedFormattingAndHandlesNulls() {
        Component nullResult = BedrockHandler.formatForBedrock(null);
        assertNotNull(nullResult);
        assertEquals(Component.empty(), nullResult);

        Component original = Component.text("Click Me", NamedTextColor.RED, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Hover text")))
                .clickEvent(ClickEvent.runCommand("/lobby"))
                .font(Key.key("minecraft:uniform"))
                .append(Component.text(" Child text", NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("Child hover"))));

        Component stripped = BedrockHandler.formatForBedrock(original);

        assertEquals(NamedTextColor.RED, stripped.color());
        assertTrue(stripped.hasDecoration(TextDecoration.BOLD));

        assertNull(stripped.hoverEvent());
        assertNull(stripped.clickEvent());
        assertNull(stripped.font());

        assertEquals(1, stripped.children().size());
        Component child = stripped.children().get(0);
        assertEquals(" Child text", ((net.kyori.adventure.text.TextComponent) child).content());
        assertEquals(NamedTextColor.GREEN, child.color());
        assertNull(child.hoverEvent());
    }
}
