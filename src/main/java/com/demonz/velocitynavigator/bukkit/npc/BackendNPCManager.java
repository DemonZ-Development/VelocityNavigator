/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import com.demonz.velocitynavigator.bukkit.npc.packet.NpcPackets;
import com.demonz.velocitynavigator.bukkit.npc.packet.PacketNpcRuntime;
import com.demonz.velocitynavigator.bukkit.skin.BackendSkinCache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BackendNPCManager {

    public static final String DEFAULT_FILE = "default.yml";
    private static final String ID_PATTERN = "[a-z0-9_-]{1,32}";

    private static final boolean HAS_TEXT_DISPLAY;
    private static final EntityType TEXT_DISPLAY_TYPE;
    private static final EntityType INTERACTION_TYPE;
    private static final EntityType MANNEQUIN_TYPE;

    static {
        TEXT_DISPLAY_TYPE = resolveType("TEXT_DISPLAY");
        HAS_TEXT_DISPLAY = TEXT_DISPLAY_TYPE != null;
        INTERACTION_TYPE = resolveType("INTERACTION");
        MANNEQUIN_TYPE = resolveType("MANNEQUIN");
    }

    private final Plugin plugin;
    private final File npcsDir;
    private final Map<String, BackendNPC> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, BackendNPC> entityUuidToNpc = new ConcurrentHashMap<>();
    private final Set<File> managedFiles = ConcurrentHashMap.newKeySet();
    private final Set<String> spawning = ConcurrentHashMap.newKeySet();
    private final BackendSkinCache skinCache;
    private final boolean mannequinMode;
    private final boolean packetMode;
    private final PacketNpcRuntime packetRuntime;
    private volatile boolean shuttingDown;

    public BackendNPCManager(Plugin plugin) {
        this.plugin = plugin;
        this.npcsDir = new File(plugin.getDataFolder(), "npcs");
        this.skinCache = new BackendSkinCache(plugin.getDataFolder().toPath(), plugin.getLogger());
        this.mannequinMode = MANNEQUIN_TYPE != null;
        this.packetMode = !mannequinMode && NpcPackets.initialize();
        this.packetRuntime = packetMode ? new PacketNpcRuntime(plugin) : null;
    }

    public boolean isPacketMode() {
        return packetMode;
    }

    public PacketNpcRuntime packetRuntime() {
        return packetRuntime;
    }

    Plugin plugin() {
        return plugin;
    }

    public String rendererName() {
        if (mannequinMode) {
            return "native mannequin";
        }
        return packetMode ? "native player packets" : "armor stand fallback";
    }

    public int viewerCount(String id) {
        return packetMode && id != null ? packetRuntime.viewerCount(id.toLowerCase(Locale.ROOT)) : 0;
    }

    public int loadAndSpawnAll() {
        despawnAll();
        npcs.clear();
        entityUuidToNpc.clear();
        managedFiles.clear();

        if (!npcsDir.exists()) {
            npcsDir.mkdirs();
        }

        List<File> files = listNpcFiles();
        if (files.isEmpty()) {
            createDefaultNpcTemplate();
            files = listNpcFiles();
        }
        int total = 0;
        for (File file : files) {
            total += loadNPCFile(file);
        }

        File legacy = new File(plugin.getDataFolder(), "npcs.yml");
        if (legacy.exists()) {
            total += loadNPCFile(legacy);
        }

        long enabledCount = npcs.values().stream().filter(BackendNPC::isEnabled).count();
        plugin.getLogger().info("Loaded " + total + " NPC definition(s), " + enabledCount + " enabled, "
                + rendererName() + " renderer active.");
        return total;
    }

    private List<File> listNpcFiles() {
        File[] files = npcsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) {
            return new ArrayList<>();
        }
        List<File> result = new ArrayList<>(List.of(files));
        result.sort((left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return result;
    }

    private static EntityType resolveType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int loadNPCFile(File file) {
        File sourceFile = file.getAbsoluteFile();
        managedFiles.add(sourceFile);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.isConfigurationSection("npcs")) {
            return 0;
        }
        int loaded = 0;
        for (String id : config.getConfigurationSection("npcs").getKeys(false)) {
            String path = "npcs." + id + ".";
            String worldName = config.getString(path + "world");
            if (worldName == null) {
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("NPC '" + id + "' references world '" + worldName
                        + "' which is not loaded yet; it will spawn once the world loads.");
            }

            Location loc = new Location(world,
                    config.getDouble(path + "x"),
                    config.getDouble(path + "y"),
                    config.getDouble(path + "z"),
                    (float) config.getDouble(path + "yaw"),
                    (float) config.getDouble(path + "pitch"));

            String key = id.toLowerCase(Locale.ROOT);
            List<String> lines = config.getStringList(path + "lines");
            if (lines.isEmpty()) {
                lines = List.of(config.getString(path + "title", "&b&l" + id.toUpperCase(Locale.ROOT)));
            }

            BackendNPC npc = new BackendNPC(key, loc,
                    config.getString(path + "target_server", "lobby"),
                    config.getString(path + "skin", ""),
                    lines,
                    config.getBoolean(path + "look_at_player", true),
                    config.getBoolean(path + "enabled", true));
            npc.setWorldName(worldName);
            npc.setSneakTargetServer(config.getString(path + "sneak_target_server"));
            npc.setHandItem(config.getString(path + "hand_item"));
            npc.setOffhandItem(config.getString(path + "offhand_item"));
            npc.setGlowing(config.getBoolean(path + "glowing", false));
            npc.setGlowColor(config.getString(path + "glow_color", "GOLD"));
            npc.setOriginFile(sourceFile.getAbsolutePath());
            BackendNPC duplicate = npcs.putIfAbsent(npc.getId(), npc);
            if (duplicate != null) {
                plugin.getLogger().warning("Ignoring duplicate NPC id '" + npc.getId() + "' in "
                        + sourceFile.getName() + "; it was already loaded from "
                        + new File(duplicate.getOriginFile()).getName() + ".");
                continue;
            }
            loaded++;

            if (world != null && npc.isEnabled()) {
                spawnNPC(npc);
            }
        }
        return loaded;
    }

    private void createDefaultNpcTemplate() {
        File file = new File(npcsDir, DEFAULT_FILE);
        if (file.exists()) {
            return;
        }
        YamlConfiguration config = new YamlConfiguration();
        writeNpcDefaults(config, "main_hub", "&b&lMAIN SERVER SELECTOR\n&7Click to open main menu", "menu:main", "Steve", 0.5);
        writeNpcDefaults(config, "games_hub", "&e&lMINIGAMES SELECTOR\n&7Click to browse minigames", "menu:games", "Alex", 5.5);
        try {
            config.save(file);
            managedFiles.add(file.getAbsoluteFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default npcs template", e);
        }
    }

    private void writeNpcDefaults(YamlConfiguration config, String id, String lines, String target, String skin, double x) {
        String path = "npcs." + id + ".";
        config.set(path + "world", "world");
        config.set(path + "x", x);
        config.set(path + "y", 64.0);
        config.set(path + "z", 0.5);
        config.set(path + "yaw", 0.0);
        config.set(path + "pitch", 0.0);
        config.set(path + "target_server", target);
        config.set(path + "skin", skin);
        config.set(path + "lines", List.of(lines.split("\n")));
        config.set(path + "look_at_player", true);
        config.set(path + "enabled", false);
    }

    public void spawnNPC(BackendNPC npc) {
        if (shuttingDown || npc == null || !npc.isEnabled()) {
            return;
        }
        Location loc = npc.getLocation();
        World world = loc != null ? loc.getWorld() : null;
        if (loc == null || world == null) {
            return;
        }
        if (!spawning.add(npc.getId())) {
            return;
        }

        FoliaSchedulerCompat.runTask(plugin, loc, () -> {
            try {
                if (shuttingDown || !npc.isEnabled() || npcs.get(npc.getId()) != npc) {
                    return;
                }
                despawnNPCNow(npc);
                if (mannequinMode) {
                    spawnMannequinBody(npc, loc);
                } else if (packetMode) {
                    packetRuntime.register(npc, materialItem(npc.getHandItem()), materialItem(npc.getOffhandItem()));
                    if (npc.isGlowing()) {
                        ChatColor color = glowColorFor(npc);
                        if (color != null) {
                            addGlowEntry(color, packetRuntime.scoreboardEntry(npc.getId()));
                        }
                    }
                } else {
                    spawnArmorStandBody(npc, loc);
                }
                spawnHolograms(npc);
                fetchAndApplySkin(npc);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Failed to spawn NPC " + npc.getId(), t);
            } finally {
                spawning.remove(npc.getId());
            }
        });
    }

    private void spawnArmorStandBody(BackendNPC npc, Location loc) {
        World world = loc.getWorld();
        Entity interaction = null;
        if (INTERACTION_TYPE != null) {
            interaction = world.spawnEntity(loc, INTERACTION_TYPE);
            interaction.setPersistent(false);
            callIfPresent(interaction, "setInteractionWidth", float.class, 0.8f);
            callIfPresent(interaction, "setInteractionHeight", float.class, 1.9f);
            callIfPresent(interaction, "setResponsive", boolean.class, true);
            npc.setInteractionEntity(interaction);
            entityUuidToNpc.put(interaction.getUniqueId(), npc);
        }
        boolean hasInteraction = interaction != null;

        ArmorStand body = world.spawn(loc, ArmorStand.class, stand -> {
            stand.setPersistent(false);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setArms(false);
            stand.setBasePlate(false);
            stand.setMarker(hasInteraction);
        });
        npc.setDisplayEntity(body);
        entityUuidToNpc.put(body.getUniqueId(), npc);

        body.setGlowing(npc.isGlowing());
        ChatColor color = npc.isGlowing() ? glowColorFor(npc) : ChatColor.WHITE;
        if (color != null) {
            addGlowEntry(color, body.getUniqueId().toString());
        }
        applyEquipment(body, npc);
    }

    private void spawnMannequinBody(BackendNPC npc, Location loc) {
        Entity body = loc.getWorld().spawnEntity(loc, MANNEQUIN_TYPE);
        body.setPersistent(false);
        body.setGravity(false);
        body.setInvulnerable(true);
        body.setSilent(true);
        callIfPresent(body, "setAI", boolean.class, false);
        callIfPresent(body, "setCollidable", boolean.class, false);
        callIfPresent(body, "setImmovable", boolean.class, true);
        npc.setDisplayEntity(body);
        entityUuidToNpc.put(body.getUniqueId(), npc);
        if (npc.isGlowing()) {
            body.setGlowing(true);
            ChatColor color = glowColorFor(npc);
            if (color != null) {
                addGlowEntry(color, body.getUniqueId().toString());
            }
        }
        applyEquipment(body, npc);
        applyMannequinProfile(body, npc.getSkinUsername());
    }

    private void applyMannequinProfile(Entity body, String username) {
        if (!mannequinMode || body == null || username == null || username.isBlank()) {
            return;
        }
        try {
            Object playerProfile;
            Player online = Bukkit.getPlayerExact(username);
            if (online != null) {
                playerProfile = online.getClass().getMethod("getPlayerProfile").invoke(online);
            } else {
                playerProfile = Bukkit.class.getMethod("createPlayerProfile", String.class)
                        .invoke(null, username);
            }
            Class<?> resolvableProfileClass = Class.forName(
                    "io.papermc.paper.datacomponent.item.ResolvableProfile");
            Object resolvable = buildResolvableProfile(
                    resolvableProfileClass, playerProfile, online, username);
            body.getClass().getMethod("setProfile", resolvableProfileClass).invoke(body, resolvable);
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not apply native profile '" + username + "' to NPC: "
                    + t.getMessage());
        }
    }

    private Object buildResolvableProfile(Class<?> resolvableProfileClass, Object playerProfile,
                                          Player online, String username) throws Exception {
        try {
            Object builder = resolvableProfileClass.getMethod("resolvableProfile").invoke(null);
            Class<?> builderClass = Class.forName(resolvableProfileClass.getName() + "$Builder");
            builderClass.getMethod("name", String.class).invoke(builder, username);
            if (online != null) {
                builderClass.getMethod("uuid", UUID.class).invoke(builder, online.getUniqueId());
            }
            List<Object> properties = playerProfileProperties(playerProfile);
            SkinTexture restored = skinFromSkinsRestorer(online);
            if (restored != null) {
                properties.removeIf(this::isTextureProperty);
                Class<?> propertyClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
                properties.add(propertyClass.getConstructor(String.class, String.class, String.class)
                        .newInstance("textures", restored.value(), restored.signature()));
            }
            if (!properties.isEmpty()) {
                builderClass.getMethod("addProperties", Collection.class).invoke(builder, properties);
            }
            return builderClass.getMethod("build").invoke(builder);
        } catch (NoSuchMethodException unavailableBuilder) {
            for (Method method : resolvableProfileClass.getMethods()) {
                if (method.getName().equals("resolvableProfile") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].isInstance(playerProfile)) {
                    return method.invoke(null, playerProfile);
                }
            }
            throw unavailableBuilder;
        }
    }

    private List<Object> playerProfileProperties(Object playerProfile) {
        List<Object> result = new ArrayList<>();
        try {
            Class<?> paperProfileClass = Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            if (paperProfileClass.isInstance(playerProfile)) {
                Object properties = paperProfileClass.getMethod("getProperties").invoke(playerProfile);
                if (properties instanceof Collection<?> collection) {
                    result.addAll(collection);
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return result;
    }

    private boolean isTextureProperty(Object property) {
        try {
            return "textures".equalsIgnoreCase(String.valueOf(
                    property.getClass().getMethod("getName").invoke(property)));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private SkinTexture skinFromSkinsRestorer(Player player) {
        if (player == null || plugin.getServer().getPluginManager().getPlugin("SkinsRestorer") == null) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = providerClass.getMethod("get").invoke(null);
            Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorer");
            Object storage = apiClass.getMethod("getPlayerStorage").invoke(api);
            Class<?> storageClass = Class.forName("net.skinsrestorer.api.storage.PlayerStorage");
            Object optional = storageClass.getMethod("getSkinForPlayer", UUID.class, String.class)
                    .invoke(storage, player.getUniqueId(), player.getName());
            if (!(optional instanceof Optional<?> value) || value.isEmpty()) {
                return null;
            }
            Object property = value.get();
            String textureValue = String.valueOf(property.getClass().getMethod("getValue").invoke(property));
            Object signatureValue = property.getClass().getMethod("getSignature").invoke(property);
            return new SkinTexture(textureValue, signatureValue != null ? signatureValue.toString() : null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record SkinTexture(String value, String signature) {
    }

    private void fetchAndApplySkin(BackendNPC npc) {
        String username = npc.getSkinUsername();
        if (mannequinMode) {
            return;
        }
        if (username == null || username.isBlank()) {
            if (!packetMode && npc.getDisplayEntity() instanceof ArmorStand stand) {
                stand.setVisible(true);
            }
            return;
        }
        skinCache.fetchSkinByUsername(username).thenAccept(skin -> {
            if (shuttingDown || !npc.isEnabled() || skin.isEmpty()) {
                if (!packetMode && skin.isEmpty() && npc.getDisplayEntity() instanceof ArmorStand stand) {
                    FoliaSchedulerCompat.runTask(plugin, stand, () -> {
                        if (npc.isEnabled() && stand.isValid()) {
                            stand.setVisible(true);
                            plugin.getLogger().warning("No Mojang skin found for NPC '" + npc.getId()
                                    + "' (" + username + "); showing the fallback body without a skin.");
                        }
                    });
                }
                return;
            }
            BackendSkinCache.SkinData data = skin.get();
            if (!username.equalsIgnoreCase(npc.getSkinUsername()) || npcs.get(npc.getId()) != npc) {
                return;
            }
            if (packetMode) {
                FoliaSchedulerCompat.runGlobalTask(plugin, () -> {
                    if (npc.isEnabled()) {
                        packetRuntime.refreshProfile(npc.getId(), data.value(), data.signature());
                    }
                });
            } else if (npc.getDisplayEntity() instanceof ArmorStand stand) {
                FoliaSchedulerCompat.runTask(plugin, stand, () -> {
                    if (npc.isEnabled() && stand.isValid()) {
                        stand.getEquipment().setHelmet(skullFor(data));
                        stand.setVisible(true);
                    }
                });
            }
        });
    }

    private ItemStack skullFor(BackendSkinCache.SkinData skin) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(skin.uuid()));
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack materialItem(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material mat = Material.matchMaterial(materialName);
        return mat != null && mat.isItem() ? new ItemStack(mat) : null;
    }

    private void applyEquipment(Entity body, BackendNPC npc) {
        if (!(body instanceof LivingEntity living) || living.getEquipment() == null) {
            return;
        }
        ItemStack mainHand = materialItem(npc.getHandItem());
        if (mainHand != null) {
            living.getEquipment().setItemInMainHand(mainHand);
        }
        ItemStack offHand = materialItem(npc.getOffhandItem());
        if (offHand != null) {
            living.getEquipment().setItemInOffHand(offHand);
        }
    }

    private void spawnHolograms(BackendNPC npc) {
        Location loc = npc.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        FoliaSchedulerCompat.runTask(plugin, loc, () -> {
            if (!isSpawnedNow(npc)) {
                return;
            }
            npc.setHologramEntities(buildHolograms(npc, loc));
        });
    }

    private List<Entity> buildHolograms(BackendNPC npc, Location loc) {
        World world = loc.getWorld();
        List<Entity> holograms = new ArrayList<>();
        if (world == null) {
            return holograms;
        }
        double base = packetMode || mannequinMode ? 2.2 : 2.0;
        List<String> lines = npc.getHologramLines();
        for (int i = 0; i < lines.size(); i++) {
            String text = ChatColor.translateAlternateColorCodes('&', lines.get(i));
            if (text.isBlank()) {
                continue;
            }
            Location lineLoc = loc.clone().add(0, base + (i * 0.28), 0);
            if (HAS_TEXT_DISPLAY) {
                Entity display = world.spawnEntity(lineLoc, TEXT_DISPLAY_TYPE);
                display.setPersistent(false);
                callIfPresent(display, "setText", String.class, text);
                callEnumIfPresent(display, "setBillboard",
                        "org.bukkit.entity.Display$Billboard", "CENTER");
                holograms.add(display);
                entityUuidToNpc.put(display.getUniqueId(), npc);
            } else {
                ArmorStand stand = world.spawn(lineLoc, ArmorStand.class);
                stand.setPersistent(false);
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setCustomName(text);
                stand.setCustomNameVisible(true);
                holograms.add(stand);
                entityUuidToNpc.put(stand.getUniqueId(), npc);
            }
        }
        return holograms;
    }

    private void respawnHolograms(BackendNPC npc) {
        Location loc = npc.getLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        FoliaSchedulerCompat.runTask(plugin, loc, () -> {
            for (Entity holo : npc.getHologramEntities()) {
                if (holo != null) {
                    entityUuidToNpc.remove(holo.getUniqueId());
                }
                removeEntitySafely(holo);
            }
            npc.setHologramEntities(List.of());
            if (npc.isEnabled() && isSpawnedNow(npc)) {
                npc.setHologramEntities(buildHolograms(npc, loc));
            }
        });
    }

    public void despawnAll() {
        for (BackendNPC npc : npcs.values()) {
            despawnNPC(npc);
        }
        entityUuidToNpc.clear();
    }

    public void despawnNPC(BackendNPC npc) {
        if (npc == null) {
            return;
        }
        deregisterPacketNpc(npc);
        Location loc = npc.getLocation();
        if (loc != null && loc.getWorld() != null) {
            FoliaSchedulerCompat.runTask(plugin, loc, () -> despawnEntitiesNow(npc));
        } else {
            despawnEntitiesNow(npc);
        }
    }

    private void despawnNPCNow(BackendNPC npc) {
        deregisterPacketNpc(npc);
        despawnEntitiesNow(npc);
    }

    private void deregisterPacketNpc(BackendNPC npc) {
        if (!packetMode) {
            return;
        }
        packetRuntime.deregister(npc.getId());
        removeGlowEntry(packetRuntime.scoreboardEntry(npc.getId()));
    }

    private void despawnEntitiesNow(BackendNPC npc) {
        if (npc.getInteractionEntity() != null) {
            entityUuidToNpc.remove(npc.getInteractionEntity().getUniqueId());
        }
        if (npc.getDisplayEntity() != null) {
            entityUuidToNpc.remove(npc.getDisplayEntity().getUniqueId());
            removeGlowFromBody(npc);
        }
        for (Entity holo : npc.getHologramEntities()) {
            if (holo != null) {
                entityUuidToNpc.remove(holo.getUniqueId());
            }
            removeEntitySafely(holo);
        }
        removeEntitySafely(npc.getInteractionEntity());
        removeEntitySafely(npc.getDisplayEntity());
        npc.setInteractionEntity(null);
        npc.setHologramEntities(List.of());
        npc.setDisplayEntity(null);
    }

    public void shutdown() {
        shuttingDown = true;
        if (packetMode) {
            packetRuntime.shutdown();
        }
        for (BackendNPC npc : npcs.values()) {
            despawnEntitiesOnShutdown(npc);
        }
        entityUuidToNpc.clear();
    }

    private void despawnEntitiesOnShutdown(BackendNPC npc) {
        Entity interaction = npc.getInteractionEntity();
        Entity body = npc.getDisplayEntity();
        removeEntityImmediately(interaction);
        removeEntityImmediately(body);
        for (Entity hologram : npc.getHologramEntities()) {
            removeEntityImmediately(hologram);
        }
        if (body != null) {
            removeGlowEntryNow(body.getUniqueId().toString());
        }
        npc.setInteractionEntity(null);
        npc.setDisplayEntity(null);
        npc.setHologramEntities(List.of());
    }

    private static void removeEntityImmediately(Entity entity) {
        if (entity == null) {
            return;
        }
        try {
            if (entity.isValid()) {
                entity.remove();
            }
        } catch (RuntimeException ignored) {
        }
    }

    public boolean isSpawnedNow(BackendNPC npc) {
        if (packetMode) {
            return packetRuntime.isTracked(npc.getId());
        }
        Entity body = npc.getDisplayEntity();
        return body != null && body.isValid();
    }

    public boolean createNPC(String id, Location location, String targetServer, String skinUsername, String title) {
        String key = id.toLowerCase(Locale.ROOT);
        if (!key.matches(ID_PATTERN)) {
            throw new IllegalArgumentException("NPC id may only contain letters, numbers, dashes and underscores (max 32 chars).");
        }
        if (npcs.containsKey(key)) {
            return false;
        }
        List<String> lines = title.isBlank() ? List.of("&b&l" + key.toUpperCase(Locale.ROOT)) : List.of(title);
        BackendNPC npc = new BackendNPC(key, location, targetServer, skinUsername, lines, true, true);
        File defaultFile = new File(npcsDir, DEFAULT_FILE).getAbsoluteFile();
        npc.setOriginFile(defaultFile.getAbsolutePath());
        managedFiles.add(defaultFile);
        npcs.put(key, npc);
        spawnNPC(npc);
        saveAll();
        return true;
    }

    public boolean removeNPC(String id) {
        BackendNPC npc = npcs.remove(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        despawnNPC(npc);
        saveAll();
        return true;
    }

    public boolean respawnNPC(String id) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        if (npc.isEnabled()) {
            spawnNPC(npc);
        }
        return true;
    }

    public boolean moveNPC(String id, Location location) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null || location == null) {
            return false;
        }
        despawnNPC(npc);
        npc.setLocation(location);
        spawnNPC(npc);
        saveAll();
        return true;
    }

    public BackendNPC findNearestNPC(Location loc, double maxDistance) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        BackendNPC nearest = null;
        double minDistanceSq = maxDistance * maxDistance;
        for (BackendNPC npc : npcs.values()) {
            Location npcLoc = npc.getLocation();
            if (npcLoc != null && loc.getWorld().equals(npcLoc.getWorld())) {
                double distSq = loc.distanceSquared(npcLoc);
                if (distSq <= minDistanceSq) {
                    minDistanceSq = distSq;
                    nearest = npc;
                }
            }
        }
        return nearest;
    }

    public boolean setSkin(String id, String skinUsername) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setSkinUsername(skinUsername);
        spawnNPC(npc);
        saveAll();
        return true;
    }

    public boolean setTitleLine(String id, int lineIndex, String text) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null || lineIndex < 0 || lineIndex > 50) {
            return false;
        }
        List<String> lines = npc.getHologramLines();
        while (lines.size() <= lineIndex) {
            lines.add("");
        }
        lines.set(lineIndex, text);
        npc.setHologramLines(lines);
        respawnHolograms(npc);
        saveAll();
        return true;
    }

    public boolean addTitleLine(String id, String text) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        List<String> lines = npc.getHologramLines();
        lines.add(text);
        npc.setHologramLines(lines);
        respawnHolograms(npc);
        saveAll();
        return true;
    }

    public boolean removeTitleLine(String id, int lineIndex) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null || lineIndex < 0 || lineIndex >= npc.getHologramLines().size()) {
            return false;
        }
        List<String> lines = npc.getHologramLines();
        lines.remove(lineIndex);
        npc.setHologramLines(lines);
        respawnHolograms(npc);
        saveAll();
        return true;
    }

    public boolean clearTitleLines(String id) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setHologramLines(List.of());
        respawnHolograms(npc);
        saveAll();
        return true;
    }

    public boolean setLookAtPlayer(String id, boolean lookAtPlayer) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setLookAtPlayer(lookAtPlayer);
        saveAll();
        return true;
    }

    public boolean setGlowing(String id, boolean glowing, String color) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setGlowing(glowing);
        if (color != null && !color.isBlank()) {
            npc.setGlowColor(color);
        }
        spawnNPC(npc);
        saveAll();
        return true;
    }

    public boolean setSneakTarget(String id, String sneakTargetServer) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setSneakTargetServer(sneakTargetServer);
        saveAll();
        return true;
    }

    public boolean setTarget(String id, String target) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null || target == null || target.isBlank()) {
            return false;
        }
        npc.setTargetServer(target);
        saveAll();
        return true;
    }

    public boolean setHandItem(String id, String materialName) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setHandItem(normalizeEquipment(materialName));
        spawnNPC(npc);
        saveAll();
        return true;
    }

    public boolean setOffhandItem(String id, String materialName) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setOffhandItem(normalizeEquipment(materialName));
        spawnNPC(npc);
        saveAll();
        return true;
    }

    private static String normalizeEquipment(String materialName) {
        if (materialName == null || materialName.isBlank()
                || "none".equalsIgnoreCase(materialName) || "air".equalsIgnoreCase(materialName)) {
            return null;
        }
        Material material = Material.matchMaterial(materialName);
        return material != null && material.isItem() ? material.name() : materialName;
    }

    public boolean setEnabled(String id, boolean enabled) {
        BackendNPC npc = npcs.get(id.toLowerCase(Locale.ROOT));
        if (npc == null) {
            return false;
        }
        npc.setEnabled(enabled);
        if (enabled) {
            spawnNPC(npc);
        } else {
            despawnNPC(npc);
        }
        saveAll();
        return true;
    }

    public void spawnPendingInWorld(World world) {
        String worldName = world.getName();
        for (BackendNPC npc : npcs.values()) {
            Location loc = npc.getLocation();
            if (!npc.isEnabled() || isSpawnedNow(npc) || loc == null
                    || npc.getWorldName() == null || !npc.getWorldName().equals(worldName)) {
                continue;
            }
            if (loc.getWorld() == null) {
                npc.setLocation(new Location(world, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
            }
            spawnNPC(npc);
        }
    }

    public Optional<BackendNPC> getNPCByEntityUuid(UUID uuid) {
        return uuid == null ? Optional.empty() : Optional.ofNullable(entityUuidToNpc.get(uuid));
    }

    public Optional<BackendNPC> getNPC(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(npcs.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<BackendNPC> getAllNPCs() {
        return new ArrayList<>(npcs.values());
    }

    public void refreshNativeProfilesFor(Player player) {
        if (shuttingDown || !mannequinMode || player == null) {
            return;
        }
        for (BackendNPC npc : npcs.values()) {
            if (!npc.isEnabled()) {
                continue;
            }
            Location loc = npc.getLocation();
            if (!isSpawnedNow(npc) && loc != null && loc.getWorld() == player.getWorld()
                    && loc.distanceSquared(player.getLocation()) <= 64d * 64d) {
                spawnNPC(npc);
                continue;
            }
            if (npc.getSkinUsername() == null
                    || !npc.getSkinUsername().equalsIgnoreCase(player.getName())) {
                continue;
            }
            Entity body = npc.getDisplayEntity();
            if (body != null && body.isValid()) {
                FoliaSchedulerCompat.runTask(plugin, body, () -> {
                    if (!shuttingDown && body.isValid()) {
                        applyMannequinProfile(body, player.getName());
                    }
                });
            }
        }
    }

    public void tickViewers() {
        if (shuttingDown || !packetMode) {
            return;
        }
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            FoliaSchedulerCompat.runTask(plugin, player, () -> packetRuntime.tickViewer(player));
        }
    }

    public void rotateNpcTowards(BackendNPC npc, float headYawDegrees) {
        packetRuntime.rotateTowards(npc.getId(), headYawDegrees);
    }

    public void saveAll() {
        Map<File, YamlConfiguration> byFile = new HashMap<>();
        for (File file : managedFiles) {
            byFile.put(file.getAbsoluteFile(), loadForRewrite(file));
        }
        for (BackendNPC npc : npcs.values()) {
            File file = npc.getOriginFile() != null
                    ? new File(npc.getOriginFile()).getAbsoluteFile()
                    : new File(npcsDir, DEFAULT_FILE).getAbsoluteFile();
            managedFiles.add(file);
            YamlConfiguration config = byFile.computeIfAbsent(file, ignored -> loadForRewrite(file));
            writeTo(config, npc);
        }
        for (Map.Entry<File, YamlConfiguration> entry : byFile.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save NPC file " + entry.getKey(), e);
            }
        }
    }

    static YamlConfiguration loadForRewrite(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("npcs", null);
        return config;
    }

    private void writeTo(YamlConfiguration config, BackendNPC npc) {
        String path = "npcs." + npc.getId() + ".";
        Location loc = npc.getLocation();
        config.set(path + "world", npc.getWorldName());
        config.set(path + "x", loc != null ? loc.getX() : 0.5);
        config.set(path + "y", loc != null ? loc.getY() : 64.0);
        config.set(path + "z", loc != null ? loc.getZ() : 0.5);
        config.set(path + "yaw", loc != null ? loc.getYaw() : 0.0f);
        config.set(path + "pitch", loc != null ? loc.getPitch() : 0.0f);
        config.set(path + "target_server", npc.getTargetServer());
        config.set(path + "skin", npc.getSkinUsername() == null ? "" : npc.getSkinUsername());
        config.set(path + "lines", npc.getHologramLines());
        config.set(path + "look_at_player", npc.isLookAtPlayer());
        config.set(path + "glowing", npc.isGlowing());
        config.set(path + "glow_color", npc.isGlowing() ? npc.getGlowColor() : null);
        config.set(path + "enabled", npc.isEnabled());
        config.set(path + "sneak_target_server",
                npc.getSneakTargetServer() != null && !npc.getSneakTargetServer().isBlank()
                        ? npc.getSneakTargetServer() : null);
        config.set(path + "hand_item",
                npc.getHandItem() != null && !npc.getHandItem().isBlank() ? npc.getHandItem() : null);
        config.set(path + "offhand_item",
                npc.getOffhandItem() != null && !npc.getOffhandItem().isBlank() ? npc.getOffhandItem() : null);
    }

    private ChatColor glowColorFor(BackendNPC npc) {
        try {
            ChatColor color = ChatColor.valueOf(npc.getGlowColor());
            return color.isColor() ? color : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Team glowTeam(ChatColor color) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = "vn_" + color.name().toLowerCase(Locale.ROOT);
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
            team.setColor(color);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return team;
    }

    private void addGlowEntry(ChatColor color, String scoreboardEntry) {
        FoliaSchedulerCompat.runGlobalTask(plugin, () -> glowTeam(color).addEntry(scoreboardEntry));
    }

    private void removeGlowEntry(String scoreboardEntry) {
        FoliaSchedulerCompat.runGlobalTask(plugin, () -> removeGlowEntryNow(scoreboardEntry));
    }

    private void removeGlowEntryNow(String scoreboardEntry) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (ChatColor color : ChatColor.values()) {
            if (!color.isColor()) {
                continue;
            }
            Team team = board.getTeam("vn_" + color.name().toLowerCase(Locale.ROOT));
            if (team != null) {
                team.removeEntry(scoreboardEntry);
            }
        }
    }

    private void removeGlowFromBody(BackendNPC npc) {
        Entity body = npc.getDisplayEntity();
        if (body == null) {
            return;
        }
        String scoreboardEntry = body.getUniqueId().toString();
        FoliaSchedulerCompat.runGlobalTask(plugin, () -> removeGlowEntryNow(scoreboardEntry));
    }

    private void removeEntitySafely(Entity entity) {
        if (entity != null && entity.isValid()) {
            FoliaSchedulerCompat.runTask(plugin, entity, entity::remove);
        }
    }

    private static void callIfPresent(Entity entity, String method, Class<?> param, Object value) {
        try {
            Method handle = entity.getClass().getMethod(method, param);
            handle.invoke(entity, value);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void callEnumIfPresent(Entity entity, String method, String enumClassName, String enumValue) {
        try {
            Class<?> enumClass = Class.forName(enumClassName);
            Object value = Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), enumValue);
            Method handle = entity.getClass().getMethod(method, enumClass);
            handle.invoke(entity, value);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
        }
    }
}
