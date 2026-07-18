/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigatorAdminMenuCommandTest {

    @Test
    void completesMenuRootAndValidateSubcommand() {
        NavigatorAdminCommand command = new NavigatorAdminCommand(null);
        CommandSource source = source(new ArrayList<>());

        assertTrue(command.suggest(invocation(source, "m")).contains("menu"));
        assertEquals(List.of("validate"), command.suggest(invocation(source, "menu", "v")));
        assertEquals(List.of(), command.suggest(invocation(source, "menu", "unknown")));
    }

    @Test
    void showsShortAliasUsageForIncompleteMenuCommand() {
        NavigatorAdminCommand command = new NavigatorAdminCommand(null);
        List<String> messages = new ArrayList<>();

        command.execute(invocation(source(messages), "menu"));

        assertEquals(List.of("Usage: /vn menu validate"), messages);
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

    private static CommandSource source(List<String> messages) {
        return (CommandSource) java.lang.reflect.Proxy.newProxyInstance(
                CommandSource.class.getClassLoader(),
                new Class<?>[]{CommandSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        return true;
                    }
                    if (method.getName().equals("sendMessage") && args != null && args.length == 1
                            && args[0] instanceof Component component) {
                        messages.add(PlainTextComponentSerializer.plainText().serialize(component));
                    }
                    return defaultValue(method.getReturnType());
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
