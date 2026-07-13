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

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServersSubCommandTest {

    @TempDir
    Path tempDir;

    private CommandSource createMockSource(List<String> messages) {
        return (CommandSource) java.lang.reflect.Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendMessage") && args.length == 1 && args[0] instanceof Component component) {
                        messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                    }
                    return null;
                }
        );
    }

    private ProxyServer createMockProxyServer() {
        return (ProxyServer) java.lang.reflect.Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer") && args.length == 1) {
                        String name = (String) args[0];
                        RegisteredServer registered = (RegisteredServer) java.lang.reflect.Proxy.newProxyInstance(
                                RegisteredServer.class.getClassLoader(),
                                new Class<?>[]{RegisteredServer.class},
                                (rsProxy, rsMethod, rsArgs) -> {
                                    if (rsMethod.getName().equals("getServerInfo")) {
                                        return new ServerInfo(name, new InetSocketAddress("localhost", 25565));
                                    } else if (rsMethod.getName().equals("getPlayersConnected")) {
                                        return Collections.emptyList();
                                    } else if (rsMethod.getName().equals("ping")) {
                                        return CompletableFuture.failedFuture(new RuntimeException("Offline"));
                                    }
                                    return null;
                                }
                        );
                        return Optional.of(registered);
                    }
                    return null;
                }
        );
    }

    @Test
    void executeRendersDashboardCorrectly() throws Exception {
        ProxyServer mockProxy = createMockProxyServer();
        VelocityNavigator plugin = new VelocityNavigator(
                mockProxy,
                LoggerFactory.getLogger("servers-subcommand-test"),
                tempDir,
                null
        );

        plugin.onProxyInitialization(null);
        plugin.registerDynamicLobby("managed-lobby", "default", 80, 2);

        List<String> messages = new ArrayList<>();
        CommandSource source = createMockSource(messages);

        ServersSubCommand.execute(source, new String[]{"servers"}, plugin).join();

        assertTrue(messages.stream().anyMatch(msg -> msg.contains("Lobby Status")));
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("lobby-1")));
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("lobby-2")));
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("managed-lobby")));
        assertTrue(messages.stream().anyMatch(msg -> msg.contains("0/80")));
    }
}
