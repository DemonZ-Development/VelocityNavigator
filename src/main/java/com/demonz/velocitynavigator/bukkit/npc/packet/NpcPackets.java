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
            addEntityCtor = findCtor(addEntityClass, int.class, UUID.class, double.class, double.class,
                    double.class, float.class, float.class, entityTypeClass, int.class, vec3Ctor.getDeclaringClass(), double.class);
            removeEntitiesCtor = removeEntitiesClass.getConstructor(int[].class);
            removePlayerInfoCtor = removePlayerInfoClass.getConstructor(List.class);

            setEntityDataCtor = setEntityDataClass.getConstructor(int.class, List.class);
            rotateEntityCtor = rotateEntityClass.getConstructor(int.class, byte.class, byte.class, boolean.class);
            pairCtor = Class.forName("com.mojang.datafixers.util.Pair").getConstructor(Object.class, Object.class);
            equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot");
            setEquipmentCtor = setEquipmentClass.getConstructor(int.class, List.class);

            dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            sharedFlagsAccessor = staticField(Class.forName("net.minecraft.world.entity.Entity"), "DATA_SHARED_FLAGS_ID");
            skinLayersAccessor = staticField(Class.forName("net.minecraft.world.entity.player.Player"), "DATA_PLAYER_MODE_CUSTOMISATION");

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

    public static int getIntField(Object packet, String name) {
        try {
            return findField(packet.getClass(), name).getInt(packet);
        } catch (Throwable t) {
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
        setField(packet, "actions", enumSetOf(
                infoActionClass.getField("ADD_PLAYER").get(null),
                infoActionClass.getField("UPDATE_LISTED").get(null)));

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
        setField(packet, "entries", List.of(infoEntryCtor.newInstance(args)));
        return packet;
    }

    public static Object buildAddEntity(int entityId, UUID npcUuid, WorldLocation loc) throws Exception {
        Object velocity = vec3Ctor.newInstance(0d, 0d, 0d);
        return addEntityCtor.newInstance(entityId, npcUuid,
                loc.x(), loc.y(), loc.z(),
                loc.pitchDegrees(), loc.yawDegrees(),
                entityTypePlayer, 0, velocity, (double) loc.yawDegrees());
    }

    public static Object buildRotateHead(int entityId, float headYawDegrees) throws Exception {
        Object packet = unsafe.allocateInstance(rotateHeadCtorClass());
        setField(packet, "entityId", entityId);
        setField(packet, "headYaw", (byte) Math.round(headYawDegrees * 256f / 360f));
        return packet;
    }

    public static Object buildRotateEntity(int entityId, float yawDegrees, float pitchDegrees) throws Exception {
        byte yaw = (byte) Math.round(yawDegrees * 256f / 360f);
        byte pitch = (byte) Math.round(pitchDegrees * 256f / 360f);
        return rotateEntityCtor.newInstance(entityId, yaw, pitch, true);
    }

    private static Class<?> rotateHeadCtorClass() throws ClassNotFoundException {
        return Class.forName("net.minecraft.network.protocol.game.ClientboundRotateHeadPacket");
    }

    public static Object buildRemoveEntities(List<Integer> entityIds) throws Exception {
        int[] ids = new int[entityIds.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = entityIds.get(i);
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
            values.add(createDataValue(sharedFlagsAccessor, glowing ? (byte) 0x40 : (byte) 0x00));
            values.add(createDataValue(skinLayersAccessor, (byte) 0x7F));
            return setEntityDataCtor.newInstance(entityId, values);
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
            Object connection = locate(handle.getClass(), "connection").get(handle);
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
