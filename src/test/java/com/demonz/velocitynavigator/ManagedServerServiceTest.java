/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedServerServiceTest {
    @Test
    void addsUpdatesAndRemovesOnlyInsideVelocityServersSection(@TempDir Path directory) throws Exception {
        Path velocity = directory.resolve("velocity.toml");
        Files.writeString(velocity, """
                bind = "0.0.0.0:25577"
                [servers]
                lobby-old = "127.0.0.1:25565"
                [forced-hosts]
                "play.example.com" = ["lobby-old"]
                """);
        assertTrue(ManagedServerService.editVelocityConfig(velocity, "game-1", "10.0.0.10", 25566, false, false).success());
        String added = Files.readString(velocity);
        assertTrue(added.contains("\"game-1\" = \"10.0.0.10:25566\""));
        assertTrue(added.contains("[forced-hosts]"));
        assertFalse(ManagedServerService.editVelocityConfig(velocity, "game-1", "10.0.0.11", 25567, false, false).success());
        assertTrue(ManagedServerService.editVelocityConfig(velocity, "game-1", "10.0.0.11", 25567, true, false).success());
        assertTrue(Files.readString(velocity).contains("\"game-1\" = \"10.0.0.11:25567\""));
        assertTrue(ManagedServerService.editVelocityConfig(velocity, "game-1", "", 25565, true, true).success());
        assertFalse(Files.readString(velocity).contains("\"game-1\""));
    }

    @Test
    void adminCommandsDryRunAddListProtectAndRemoveGameAndLobby(@TempDir Path directory) throws Exception {
        Path proxyRoot = directory.resolve("proxy");
        Path dataDirectory = proxyRoot.resolve("plugins/velocitynavigator");
        Files.createDirectories(dataDirectory);
        Path velocity = proxyRoot.resolve("velocity.toml");
        Files.writeString(velocity, """
                bind = "0.0.0.0:25577"
                [servers]
                existing = "127.0.0.1:25565"
                [forced-hosts]
                "play.example.com" = ["game-live"]
                """
        );

        Map<String, RegisteredServer> runtimeServers = new LinkedHashMap<>();
        ProxyServer proxy = createProxy(runtimeServers);
        VelocityNavigator plugin = new VelocityNavigator(
                proxy,
                LoggerFactory.getLogger("managed-server-command-test"),
                dataDirectory,
                null
        );
        plugin.onProxyInitialization(null);
        ManagedServerService service = plugin.managedServerService();
        assertNotNull(service);
        service.configure(new AdvancedConfig.ServerManagement(true, "velocity.toml", false));

        NavigatorAdminCommand command = new NavigatorAdminCommand(plugin);
        List<String> messages = new ArrayList<>();
        CommandSource source = createSource(messages);
        Path registry = dataDirectory.resolve("servers.toml");

        String velocityBefore = Files.readString(velocity);
        String registryBefore = Files.readString(registry);
        command.execute(invocation(source, "server", "dry-run", "lobby", "lobby-live", "10.0.0.20:25567", "minigames", "120", "3"));
        assertEquals(velocityBefore, Files.readString(velocity));
        assertEquals(registryBefore, Files.readString(registry));
        assertTrue(messages.stream().anyMatch(message -> message.contains("Dry run passed")));

        command.execute(invocation(source, "server", "add", "game", "game-live", "10.0.0.10:25566"));
        assertTrue(Files.readString(velocity).contains("\"game-live\" = \"10.0.0.10:25566\""));
        assertTrue(runtimeServers.containsKey("game-live"));
        assertFalse(Files.readString(registry).contains("game-live"));

        command.execute(invocation(source, "server", "add", "lobby", "lobby-live", "10.0.0.20:25567", "minigames", "120", "3"));
        assertTrue(Files.readString(velocity).contains("\"lobby-live\" = \"10.0.0.20:25567\""));
        assertTrue(Files.readString(registry).contains("name = \"lobby-live\""));
        assertTrue(runtimeServers.containsKey("lobby-live"));
        Config.LobbyEntry dynamic = plugin.dynamicLobby("lobby-live").orElseThrow();
        assertEquals(120, dynamic.maxPlayers());
        assertEquals(3, dynamic.weight());

        messages.clear();
        command.execute(invocation(source, "server", "list"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("lobby-live@minigames")));

        messages.clear();
        command.execute(invocation(source, "server", "add", "lobby", "lobby-live", "10.0.0.99:25568", "minigames", "120", "3"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("allow_overwrite")), messages.toString());
        assertTrue(Files.readString(velocity).contains("\"lobby-live\" = \"10.0.0.20:25567\""));

        messages.clear();
        command.execute(invocation(source, "server", "remove", "game-live"));
        assertFalse(Files.readString(velocity).contains("\"game-live\" ="));
        assertFalse(runtimeServers.containsKey("game-live"));
        assertTrue(messages.stream().anyMatch(message -> message.contains("play.example.com")), messages.toString());

        command.execute(invocation(source, "server", "remove", "lobby-live"));
        assertFalse(Files.readString(velocity).contains("\"lobby-live\""));
        assertFalse(Files.readString(registry).contains("lobby-live"));
        assertFalse(runtimeServers.containsKey("lobby-live"));
        assertTrue(plugin.dynamicLobby("lobby-live").isEmpty());
        try (var backups = Files.list(dataDirectory.resolve("backups"))) {
            assertTrue(backups.findAny().isPresent());
        }
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... arguments) {
        return (SimpleCommand.Invocation) java.lang.reflect.Proxy.newProxyInstance(
                SimpleCommand.Invocation.class.getClassLoader(),
                new Class<?>[]{SimpleCommand.Invocation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "source" -> source;
                    case "alias" -> "vn";
                    case "arguments" -> arguments;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static CommandSource createSource(List<String> messages) {
        return (CommandSource) java.lang.reflect.Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) return true;
                    if (method.getName().equals("sendMessage") && args != null && args.length == 1 && args[0] instanceof Component component) {
                        messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static ProxyServer createProxy(Map<String, RegisteredServer> servers) {
        return (ProxyServer) java.lang.reflect.Proxy.newProxyInstance(
                ProxyServer.class.getClassLoader(),
                new Class<?>[]{ProxyServer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getServer") && args != null && args.length == 1) {
                        return Optional.ofNullable(servers.get(String.valueOf(args[0]).toLowerCase(Locale.ROOT)));
                    }
                    if (method.getName().equals("getAllServers")) return List.copyOf(servers.values());
                    if (method.getName().equals("getAllPlayers")) return List.of();
                    if (method.getName().equals("registerServer") && args != null && args.length == 1 && args[0] instanceof ServerInfo info) {
                        RegisteredServer registered = registeredServer(info);
                        servers.put(info.getName().toLowerCase(Locale.ROOT), registered);
                        return registered;
                    }
                    if (method.getName().equals("unregisterServer") && args != null && args.length == 1 && args[0] instanceof ServerInfo info) {
                        servers.remove(info.getName().toLowerCase(Locale.ROOT));
                        return null;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
    }

    private static RegisteredServer registeredServer(ServerInfo info) {
        return (RegisteredServer) java.lang.reflect.Proxy.newProxyInstance(
                RegisteredServer.class.getClassLoader(),
                new Class<?>[]{RegisteredServer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getServerInfo" -> info;
                    case "getPlayersConnected" -> List.of();
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
