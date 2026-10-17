/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.dialog;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Set;

final class ReflectiveApi {
    private ReflectiveApi() {}

    static Object call(Object target, String name, Object... arguments) throws ReflectiveOperationException {
        Set<Class<?>> types = new LinkedHashSet<>();
        collect(target instanceof Class<?> type ? type : target.getClass(), types);
        for (Class<?> type : types) {
            if (!Modifier.isPublic(type.getModifiers())) continue;
            for (Method method : type.getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != arguments.length
                        || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) continue;
                if ((target instanceof Class<?>) != Modifier.isStatic(method.getModifiers())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (arguments[i] == null ? parameters[i].isPrimitive() : !boxed(parameters[i]).isInstance(arguments[i])) matches = false;
                }
                if (matches) return method.invoke(target instanceof Class<?> ? null : target, arguments);
            }
        }
        throw new NoSuchMethodException(name);
    }

    static Object api(String name, String method, Object... arguments) throws ReflectiveOperationException {
        return call(Class.forName(name), method, arguments);
    }

    private static void collect(Class<?> type, Set<Class<?>> types) {
        if (type == null || !types.add(type)) return;
        for (Class<?> parent : type.getInterfaces()) collect(parent, types);
        collect(type.getSuperclass(), types);
    }

    private static Class<?> boxed(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        return type;
    }
}
