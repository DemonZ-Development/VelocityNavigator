/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc.packet;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NpcPackets {
    private static final Logger LOG = Logger.getLogger("VelocityNavigator");
    private static final Map<Class<?>, Map<String, Field>> FIELDS = new ConcurrentHashMap<>();
    private static final Set<String> SEND_FAILURES_LOGGED = ConcurrentHashMap.newKeySet();

    private static boolean initialized;
    private static boolean available;

    private static Constructor<?> addEntityCtor;
    private static Constructor<?> removeEntitiesCtor;
    private static Constructor<?> removePlayerInfoCtor;
    private static Constructor<?> setEquipmentCtor;
    private static Constructor<?> setEntityDataCtor;
    private static Constructor<?> rotateEntityCtor;
    private static Constructor<?> infoEntryCtor;
    private static Constructor<?> vec3Ctor;
    private static Constructor<?> pairCtor;
    private static Constructor<?> gameProfileCtor;
    private static Constructor<?> profilePropertyCtor;

    private static Class<?> infoUpdateClass;
    private static Class<?> infoActionClass;
    private static Class<?> packetBaseClass;
    private static Class<?> entityTypeClass;
    private static Class<?> dataValueClass;
    private static Class<?> equipmentSlotClass;
    private static Class<?> gameTypeClass;
    private static Class<?> propertyClass;
    private static Object entityTypePlayer;
    private static Object sharedFlagsAccessor;
    private static Object skinLayersAccessor;
    private static Object gameTypeSurvival;

    private static sun.misc.Unsafe unsafe;

    private NpcPackets() {
    }

    public static synchronized boolean initialize() {
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            packetBaseClass = Class.forName("net.minecraft.network.protocol.Packet");
            infoUpdateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            infoActionClass = Class.forName(infoUpdateClass.getName() + "$Action");
            Class<?> addEntityClass = Class.forName("net.minecraft.network.protocol.game.ClientboundAddEntityPacket");
            Class<?> removeEntitiesClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket");
            Class<?> removePlayerInfoClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            Class<?> rotateHeadClass = Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
            Class<?> rotateEntityClass = Class.forName("net.minecraft.network.protocol.game.ClientboundMoveEntityPacket$Rot");
            Class<?> setEquipmentClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket");
            Class<?> setEntityDataClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket");

            entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            entityTypePlayer = staticField(entityTypeClass, "PLAYER");

            vec3Ctor = Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class, double.class, double.class);
            addEntityCtor = findAddEntityCtor(addEntityClass, entityTypeClass, vec3Ctor.getDeclaringClass());
            removeEntitiesCtor = findRemoveEntitiesCtor(removeEntitiesClass);
            removePlayerInfoCtor = removePlayerInfoClass.getConstructor(List.class);

            setEntityDataCtor = setEntityDataClass.getConstructor(int.class, List.class);
            rotateEntityCtor = findRotateEntityCtor(rotateEntityClass);
            pairCtor = Class.forName("com.mojang.datafixers.util.Pair").getConstructor(Object.class, Object.class);
            equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            setEquipmentCtor = setEquipmentClass.getConstructor(int.class, List.class);

            dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            sharedFlagsAccessor = findSharedFlagsAccessor(Class.forName("net.minecraft.world.entity.Entity"));
            skinLayersAccessor = findSkinLayersAccessor(Class.forName("net.minecraft.world.entity.player.Player"));

            propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            gameProfileCtor = Class.forName("com.mojang.authlib.GameProfile").getConstructor(UUID.class, String.class);
            profilePropertyCtor = propertyClass.getConstructor(String.class, String.class, String.class);

            gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            gameTypeSurvival = staticField(gameTypeClass, "SURVIVAL");
            infoEntryCtor = findInfoEntryCtor();

            unsafe = theUnsafe();
            available = addEntityCtor != null && removeEntitiesCtor != null && infoEntryCtor != null && unsafe != null;

            if (available) {
                Object testProfile = createGameProfile(UUID.randomUUID(), "selftest", null, null);
                Object add = buildPlayerInfoAdd(UUID.randomUUID(), testProfile);
                Object remove = buildRemoveEntities(List.of(-1));
                Object removeInfo = buildPlayerInfoRemove(List.of(UUID.randomUUID()));
                Object head = buildRotateHead(-1, 0f);
                Object rotate = buildRotateEntity(-1, 0f, 0f);
                Object spawn = buildAddEntity(-1, UUID.randomUUID(),
                        new WorldLocation(null, 0d, 0d, 0d, 0f, 0f));
                available = add != null && remove != null && removeInfo != null && head != null
                        && rotate != null && spawn != null;
            }
            if (!available) {
                LOG.warning("[VelocityNavigator] Packet layout changed in this server version, falling back to armor stand NPCs.");
            }
        } catch (Throwable t) {
            available = false;
            LOG.log(Level.INFO, "[VelocityNavigator] Player-model NPCs unavailable here (" + t.getMessage() + "), using armor stand NPCs.", t);
        }
        return available;
    }

    public static boolean isAvailable() {
        initialize();
        return available;
    }

    private static sun.misc.Unsafe theUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static java.util.EnumSet<?> enumSetOf(Object first, Object... rest) {
        java.util.EnumSet values = java.util.EnumSet.of((Enum) first);
        for (Object value : rest) {
            values.add((Enum) value);
        }
        return values;
    }

    private static Constructor<?> findCtor(Class<?> type, Class<?>... params) {
        try {
            return type.getConstructor(params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static Constructor<?> findAddEntityCtor(Class<?> addEntityClass, Class<?> entityTypeClass, Class<?> vec3Class) {
        Constructor<?> ctor = findCtor(addEntityClass, int.class, UUID.class, double.class, double.class,
                double.class, float.class, float.class, entityTypeClass, int.class, vec3Class, double.class);
        if (ctor != null) {
            return ctor;
        }
        ctor = findCtor(addEntityClass, int.class, UUID.class, double.class, double.class,
                double.class, float.class, float.class, entityTypeClass, int.class, vec3Class, float.class);
        if (ctor != null) {
            return ctor;
        }
        for (Constructor<?> c : addEntityClass.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 7 && p[0] == int.class && p[1] == UUID.class
                    && p[2] == double.class && p[3] == double.class && p[4] == double.class
                    && p[5] == float.class && p[6] == float.class) {
                return c;
            }
        }
        return null;
    }

    private static Constructor<?> findRemoveEntitiesCtor(Class<?> removeEntitiesClass) {
        Constructor<?> ctor = findCtor(removeEntitiesClass, int[].class);
        if (ctor != null) {
            return ctor;
        }
        for (Constructor<?> c : removeEntitiesClass.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1) {
                return c;
            }
        }
        return null;
    }

    static Constructor<?> findRotateEntityCtor(Class<?> rotateClass) {
        Constructor<?> ctor = findCtor(rotateClass, int.class, byte.class, byte.class, boolean.class);
        if (ctor != null) {
            return ctor;
        }
        for (Constructor<?> c : rotateClass.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 4 && p[0] == int.class && p[3] == boolean.class) {
                return c;
            }
        }
        return null;
    }

    private static Object findSharedFlagsAccessor(Class<?> entityClass) {
        for (String name : List.of("DATA_SHARED_FLAGS_ID", "DATA_SHARED_FLAGS")) {
            try {
                return staticField(entityClass, name);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Object findSkinLayersAccessor(Class<?> playerClass) {
        for (String name : List.of("DATA_PLAYER_MODE_CUSTOMISATION", "DATA_PLAYER_MODE_CUSTOMIZATION", "DATA_PLAYER_MODEL_CUSTOMISATION")) {
            try {
                return staticField(playerClass, name);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Constructor<?> findInfoEntryCtor() throws ClassNotFoundException {
        Class<?> entryClass = Class.forName(infoUpdateClass.getName() + "$Entry");
        Class<?> gameProfileClass = gameProfileCtor.getDeclaringClass();
        for (Constructor<?> ctor : entryClass.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length >= 4 && params[0] == UUID.class && params[1] == gameProfileClass) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        return null;
    }

    private static Object staticField(Class<?> owner, String name) throws Exception {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getName().equals(name) && Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                return field.get(null);
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        try {
            return FIELDS.computeIfAbsent(type, c -> new ConcurrentHashMap<>())
                    .computeIfAbsent(name, n -> {
                        try {
                            Field f = locate(type, n);
                            f.setAccessible(true);
                            return f;
                        } catch (NoSuchFieldException e) {
                            throw new IllegalStateException(e.getMessage());
                        }
                    });
        } catch (IllegalStateException e) {
            throw (NoSuchFieldException) new NoSuchFieldException(e.getMessage()).initCause(e.getCause());
        }
    }

    private static Field locate(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        findField(target.getClass(), name).set(target, value);
    }

    static void setFieldFlexible(Object target, Class<?> expectedType, Object value, String... names) throws Exception {
        for (String name : names) {
            try {
                Field f = locate(target.getClass(), name);
                f.setAccessible(true);
                if (isTypeCompatible(expectedType, f.getType())) {
                    f.set(target, value);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
        List<Field> matching = new ArrayList<>();
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && isTypeCompatible(expectedType, f.getType())) {
                    matching.add(f);
                }
            }
        }
        if (matching.size() == 1) {
            Field f = matching.get(0);
            f.setAccessible(true);
            f.set(target, value);
            return;
        }
        setField(target, names[0], value);
    }

    private static boolean isTypeCompatible(Class<?> expectedType, Class<?> actualType) {
        if (expectedType == null) {
            return true;
        }
        if (expectedType == actualType) {
            return true;
        }
        if (!expectedType.isPrimitive() && !actualType.isPrimitive()) {
            return expectedType.isAssignableFrom(actualType);
        }
        return false;
    }

    public static int getIntField(Object packet, String name) {
        try {
            return findField(packet.getClass(), name).getInt(packet);
        } catch (Throwable t) {
            for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == int.class && !Modifier.isStatic(f.getModifiers())) {
                        try {
                            f.setAccessible(true);
                            return f.getInt(packet);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            return -1;
        }
    }

    public static Object readField(Object target, String name) throws Exception {
        return findField(target.getClass(), name).get(target);
    }

    public static Object createGameProfile(UUID uuid, String name, String textureValue, String textureSignature) throws Exception {
        Object profile = gameProfileCtor.newInstance(uuid, name);
        if (textureValue != null && !textureValue.isBlank()) {
            Object property = profilePropertyCtor.newInstance("textures", textureValue, textureSignature);
            Object properties = gameProfileCtor.getDeclaringClass().getMethod("getProperties").invoke(profile);
            properties.getClass().getMethod("put", String.class, propertyClass)
                    .invoke(properties, "textures", property);
        }
        return profile;
    }

    public static Object buildPlayerInfoAdd(UUID npcUuid, Object profile) throws Exception {
        Object packet = unsafe.allocateInstance(infoUpdateClass);
        setFieldFlexible(packet, java.util.EnumSet.class, enumSetOf(
                infoActionClass.getField("ADD_PLAYER").get(null),
                infoActionClass.getField("UPDATE_LISTED").get(null)),
                "actions", "a");

        Class<?>[] paramTypes = infoEntryCtor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];
        args[0] = npcUuid;
        args[1] = profile;
        args[2] = Boolean.FALSE;
        args[3] = 0;
        for (int i = 4; i < paramTypes.length; i++) {
            Class<?> p = paramTypes[i];
            if (p == gameTypeClass) {
                args[i] = gameTypeSurvival;
            } else if (p == long.class) {
                args[i] = 0L;
            } else if (p == float.class) {
                args[i] = 0f;
            } else if (p == double.class) {
                args[i] = 0d;
            } else if (p == boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (p == char.class) {
                args[i] = (char) 0;
            } else if (p.isPrimitive()) {
                args[i] = 0;
            } else {
                args[i] = null;
            }
        }
        setFieldFlexible(packet, List.class, List.of(infoEntryCtor.newInstance(args)), "entries", "b");
        return packet;
    }

    public static Object buildAddEntity(int entityId, UUID npcUuid, WorldLocation loc) throws Exception {
        Object velocity = vec3Ctor.newInstance(0d, 0d, 0d);
        Class<?>[] params = addEntityCtor.getParameterTypes();
        if (params.length == 11 && params[7] == entityTypeClass && params[8] == int.class
                && params[9] == vec3Ctor.getDeclaringClass() && (params[10] == double.class || params[10] == float.class)) {
            Object headYaw = params[10] == float.class ? loc.yawDegrees() : (double) loc.yawDegrees();
            return addEntityCtor.newInstance(entityId, npcUuid,
                    loc.x(), loc.y(), loc.z(),
                    loc.pitchDegrees(), loc.yawDegrees(),
                    entityTypePlayer, 0, velocity, headYaw);
        }
        Object[] args = adaptAddEntityArgs(params, entityId, npcUuid, loc, entityTypeClass, entityTypePlayer, velocity);
        return addEntityCtor.newInstance(args);
    }

    static Object[] adaptAddEntityArgs(Class<?>[] params, int entityId, UUID npcUuid, WorldLocation loc,
                                       Class<?> entityTypeClass, Object entityTypePlayer, Object velocity) {
        Object[] args = new Object[params.length];
        if (params.length > 0) args[0] = entityId;
        if (params.length > 1) args[1] = npcUuid;
        if (params.length > 2) args[2] = params[2] == float.class ? (float) loc.x() : loc.x();
        if (params.length > 3) args[3] = params[3] == float.class ? (float) loc.y() : loc.y();
        if (params.length > 4) args[4] = params[4] == float.class ? (float) loc.z() : loc.z();
        if (params.length > 5) args[5] = params[5] == byte.class ? (byte) Math.round(loc.pitchDegrees() * 256f / 360f) : loc.pitchDegrees();
        if (params.length > 6) args[6] = params[6] == byte.class ? (byte) Math.round(loc.yawDegrees() * 256f / 360f) : loc.yawDegrees();
        for (int i = 7; i < params.length; i++) {
            Class<?> p = params[i];
            if (p == entityTypeClass) {
                args[i] = entityTypePlayer;
            } else if (p == int.class) {
                args[i] = 0;
            } else if (velocity != null && p == velocity.getClass()) {
                args[i] = velocity;
            } else if (p == float.class) {
                args[i] = loc.yawDegrees();
            } else if (p == double.class) {
                args[i] = (double) loc.yawDegrees();
            } else if (p == byte.class) {
                args[i] = (byte) Math.round(loc.yawDegrees() * 256f / 360f);
            } else if (p == boolean.class) {
                args[i] = Boolean.FALSE;
            } else if (p.isPrimitive()) {
                args[i] = 0;
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    public static Object buildRotateHead(int entityId, float headYawDegrees) throws Exception {
        Object packet = unsafe.allocateInstance(rotateHeadCtorClass());
        setFieldFlexible(packet, int.class, entityId, "entityId", "id", "a");
        byte yawByte = (byte) Math.round(headYawDegrees * 256f / 360f);
        setFieldFlexible(packet, byte.class, yawByte, "yHeadRot", "headYaw", "yRot", "b");
        return packet;
    }

    public static Object buildRotateEntity(int entityId, float yawDegrees, float pitchDegrees) throws Exception {
        byte yaw = (byte) Math.round(yawDegrees * 256f / 360f);
        byte pitch = (byte) Math.round(pitchDegrees * 256f / 360f);
        Class<?>[] p = rotateEntityCtor.getParameterTypes();
        Object p1 = p.length > 1 && p[1] == float.class ? yawDegrees : yaw;
        Object p2 = p.length > 2 && p[2] == float.class ? pitchDegrees : pitch;
        return rotateEntityCtor.newInstance(entityId, p1, p2, true);
    }

    private static Class<?> rotateHeadCtorClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
    }

    public static Object buildRemoveEntities(List<Integer> entityIds) throws Exception {
        int[] ids = new int[entityIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = entityIds.get(i);
        }
        Class<?> paramType = removeEntitiesCtor.getParameterTypes()[0];
        if (paramType == int[].class) {
            return removeEntitiesCtor.newInstance((Object) ids);
        }
        if (paramType.isAssignableFrom(List.class)) {
            return removeEntitiesCtor.newInstance(entityIds);
        }
        try {
            Class<?> intArrayListClass = Class.forName("it.unimi.dsi.fastutil.ints.IntArrayList");
            Constructor<?> listCtor = intArrayListClass.getConstructor(int[].class);
            Object fastList = listCtor.newInstance((Object) ids);
            if (paramType.isInstance(fastList)) {
                return removeEntitiesCtor.newInstance(fastList);
            }
        } catch (Throwable ignored) {
        }
        return removeEntitiesCtor.newInstance((Object) ids);
    }

    public static Object buildPlayerInfoRemove(List<UUID> profileIds) throws Exception {
        return removePlayerInfoCtor.newInstance(List.copyOf(profileIds));
    }

    public static Object buildEquipment(int entityId, ItemStack mainHand, ItemStack offHand) {
        try {
            List<Object> slots = new ArrayList<>();
            addSlot(slots, "MAINHAND", mainHand);
            addSlot(slots, "OFFHAND", offHand);
            return slots.isEmpty() ? null : setEquipmentCtor.newInstance(entityId, slots);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void addSlot(List<Object> slots, String slotName, ItemStack stack) throws Exception {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        Object nmsStack = asNmsCopy(stack);
        if (nmsStack != null) {
            slots.add(pairCtor.newInstance(equipmentSlotClass.getField(slotName).get(null), nmsStack));
        }
    }

    private static Object asNmsCopy(ItemStack stack) {
        try {
            Class<?> craftItemStack = Class.forName(
                    Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack");
            return craftItemStack.getMethod("asNMSCopy", ItemStack.class).invoke(null, stack);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object buildCosmetics(int entityId, boolean glowing) {
        try {
            List<Object> values = new ArrayList<>();
            if (sharedFlagsAccessor != null) {
                values.add(createDataValue(sharedFlagsAccessor, glowing ? (byte) 0x40 : (byte) 0x00));
            }
            if (skinLayersAccessor != null) {
                values.add(createDataValue(skinLayersAccessor, (byte) 0x7F));
            }
            return values.isEmpty() ? null : setEntityDataCtor.newInstance(entityId, values);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object createDataValue(Object accessor, Object value) throws Exception {
        Method create = dataValueClass.getMethod("create",
                Class.forName("net.minecraft.network.syncher.EntityDataAccessor"), Object.class);
        return create.invoke(null, accessor, value);
    }

    public static boolean send(Player viewer, Object packet) {
        if (packet == null || !viewer.isOnline()) {
            return false;
        }
        try {
            Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = getConnection(handle);
            Method send = findSend(connection.getClass());
            if (send != null) {
                send.invoke(connection, packet);
                return true;
            }
            logSendFailure("No compatible packet send method exists on " + connection.getClass().getName(), null);
        } catch (Throwable t) {
            logSendFailure(t.getClass().getName() + ": " + String.valueOf(t.getMessage()), t);
        }
        return false;
    }

    private static Object getConnection(Object handle) throws Exception {
        try {
            return locate(handle.getClass(), "connection").get(handle);
        } catch (NoSuchFieldException e) {
            for (Class<?> c = handle.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.getType().getName().contains("PacketListener")) {
                        f.setAccessible(true);
                        return f.get(handle);
                    }
                }
            }
            throw e;
        }
    }

    private static void logSendFailure(String message, Throwable cause) {
        if (!SEND_FAILURES_LOGGED.add(message)) {
            return;
        }
        if (cause == null) {
            LOG.warning("[VelocityNavigator] NPC packet delivery failed: " + message);
        } else {
            LOG.log(Level.WARNING, "[VelocityNavigator] NPC packet delivery failed: " + message, cause);
        }
    }

    private static Method findSend(Class<?> connectionClass) {
        for (Class<?> c = connectionClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1
                        && packetBaseClass.isAssignableFrom(m.getParameterTypes()[0])) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    public record WorldLocation(World world, double x, double y, double z,
                                float yawDegrees, float pitchDegrees) {
    }
}
