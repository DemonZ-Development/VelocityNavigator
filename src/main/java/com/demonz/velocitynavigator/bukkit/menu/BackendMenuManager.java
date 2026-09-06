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
package com.demonz.velocitynavigator.bukkit.menu;

import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BackendMenuManager {

    public record ValidationResult(List<MenuBridgeProtocol.MenuItem> items, List<String> problems) {
    }

    private final Plugin plugin;
    private final File menusDir;
    private final Map<String, BackendMenu> menus = new ConcurrentHashMap<>();

    public BackendMenuManager(Plugin plugin) {
        this.plugin = plugin;
        this.menusDir = new File(plugin.getDataFolder(), "menus");
        reloadAll();
    }

    public void reloadAll() {
        menus.clear();
        if (!menusDir.exists()) {
            menusDir.mkdirs();
            createDefaultStarterMenus();
        }

        File[] files = menusDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            createDefaultStarterMenus();
            files = menusDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        }

        if (files != null) {
            for (File file : files) {
                loadMenuFile(file);
            }
        }
    }

    private void loadMenuFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            String menuName = file.getName().substring(0, file.getName().lastIndexOf('.')).toLowerCase();

            ValidationResult result = validate(config);
            if (!result.problems().isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        "Skipped menu file " + file.getName() + " (rejected): " + String.join("; ", result.problems()));
                return;
            }

            String title = config.getString("title", "&b&l" + menuName.toUpperCase());
            int rows = config.getInt("rows", 3);
            menus.put(menuName, new BackendMenu(menuName, title, rows, result.items()));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load menu file " + file.getName(), e);
        }
    }

    public static ValidationResult validate(YamlConfiguration config) {
        List<MenuBridgeProtocol.MenuItem> items = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (config == null) {
            problems.add("configuration is null");
            return new ValidationResult(items, problems);
        }
        int rows = config.getInt("rows", 3);
        if (rows < 2 || rows > 6) {
            problems.add("rows=" + rows + " is outside [2,6]");
            return new ValidationResult(items, problems);
        }
        int slotCap = rows * 9;
        if (!config.isConfigurationSection("items")) {
            return new ValidationResult(items, problems);
        }
        for (String key : config.getConfigurationSection("items").getKeys(false)) {
            String path = "items." + key + ".";
            int slot = config.getInt(path + "slot", 0);
            String target = config.getString(path + "target", "");
            String material = config.getString(path + "material", "");
            String name = config.getString(path + "name", "");
            List<String> lore = config.getStringList(path + "lore");

            if (slot < 0 || slot >= slotCap) {
                problems.add("item '" + key + "' slot=" + slot + " is outside [0," + (slotCap - 1) + "]");
                continue;
            }
            if (target.isBlank() || material.isBlank() || name.isBlank()) {
                problems.add("item '" + key + "' is missing target/material/name");
                continue;
            }
            if (target.startsWith("@page:") || target.startsWith("@refresh:")) {
                problems.add("item '" + key + "' uses reserved target '" + target + "'");
                continue;
            }
            if (!lore.isEmpty() && lore.size() > 16) {
                problems.add("item '" + key + "' lore size=" + lore.size() + " exceeds 16 lines");
                continue;
            }
            items.add(new MenuBridgeProtocol.MenuItem(slot, target, material, name, lore));
        }
        return new ValidationResult(items, problems);
    }

    public static List<String> validateItems(YamlConfiguration config) {
        return validate(config).problems();
    }

    private void createDefaultStarterMenus() {
        createMenuTemplate("main.yml", "&b&lMAIN SERVER SELECTOR", 3, List.of(
                new MenuBridgeProtocol.MenuItem(11, "lobby-1", "NETHER_STAR", "&a&lMAIN LOBBY 1", List.of("&7Connect to main hub 1", "&eClick to join!")),
                new MenuBridgeProtocol.MenuItem(13, "menu:games", "DIAMOND_SWORD", "&e&lMINIGAMES", List.of("&7Open minigame selector", "&eClick to browse!")),
                new MenuBridgeProtocol.MenuItem(15, "menu:lobbies", "COMPASS", "&c&lLOBBY INSPECTOR", List.of("&7Check all lobby servers", "&eClick to inspect!"))
        ));

        createMenuTemplate("games.yml", "&e&lMINIGAMES SELECTOR", 3, List.of(
                new MenuBridgeProtocol.MenuItem(11, "bedwars-1", "RED_BED", "&c&lBEDWARS", List.of("&7Play Bedwars solo/teams", "&eClick to queue!")),
                new MenuBridgeProtocol.MenuItem(13, "skywars-1", "FEATHER", "&b&lSKYWARS", List.of("&7Play Skywars solo/teams", "&eClick to queue!")),
                new MenuBridgeProtocol.MenuItem(15, "survival-1", "GRASS_BLOCK", "&a&lSURVIVAL", List.of("&7Play SMP Survival", "&eClick to join!"))
        ));

        createMenuTemplate("lobbies.yml", "&c&lLOBBY SERVERS STATUS", 3, List.of(
                new MenuBridgeProtocol.MenuItem(10, "lobby-1", "GREEN_WOOL", "&a&lLOBBY 1", List.of("&7Status: &aONLINE", "&eClick to join!")),
                new MenuBridgeProtocol.MenuItem(12, "lobby-2", "GREEN_WOOL", "&a&lLOBBY 2", List.of("&7Status: &aONLINE", "&eClick to join!")),
                new MenuBridgeProtocol.MenuItem(14, "lobby-3", "YELLOW_WOOL", "&e&lLOBBY 3 (DRAINING)", List.of("&7Status: &eMAINTENANCE", "&eClick to join!")),
                new MenuBridgeProtocol.MenuItem(16, "menu:main", "BARRIER", "&c&lBACK TO MAIN", List.of("&7Return to main selector"))
        ));
    }

    private void createMenuTemplate(String fileName, String title, int rows, List<MenuBridgeProtocol.MenuItem> items) {
        File file = new File(menusDir, fileName);
        if (file.exists()) return;

        YamlConfiguration config = new YamlConfiguration();
        config.set("title", title);
        config.set("rows", rows);
        for (int i = 0; i < items.size(); i++) {
            MenuBridgeProtocol.MenuItem item = items.get(i);
            String path = "items.item_" + i + ".";
            config.set(path + "slot", item.slot());
            config.set(path + "target", item.target());
            config.set(path + "material", item.material());
            config.set(path + "name", item.name());
            config.set(path + "lore", item.lore());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save menu template " + fileName, e);
        }
    }

    public boolean openMenu(Player player, String menuName) {
        String key = (menuName == null || menuName.isBlank()) ? "main" : menuName.toLowerCase();
        BackendMenu menu = menus.get(key);
        if (menu == null) {
            menu = menus.get("main");
        }
        if (menu == null) {
            return false;
        }

        int slots = menu.getRows() * 9;
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', menu.getTitle());
        BackendMenuHolder holder = new BackendMenuHolder(menu.getName());
        Inventory inv = Bukkit.createInventory(holder, slots, coloredTitle);
        holder.bindInventory(inv);

        for (MenuBridgeProtocol.MenuItem item : menu.getItems()) {
            if (item.slot() >= 0 && item.slot() < slots) {
                Material mat = Material.matchMaterial(item.material());
                if (mat == null) mat = Material.COMPASS;

                ItemStack is = new ItemStack(mat);
                ItemMeta meta = is.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', item.name()));
                    List<String> lore = new ArrayList<>();
                    for (String line : item.lore()) {
                        lore.add(ChatColor.translateAlternateColorCodes('&', line));
                    }
                    meta.setLore(lore);
                    is.setItemMeta(meta);
                }
                inv.setItem(item.slot(), is);
                holder.targetsBySlot().put(item.slot(), item.target());
            }
        }

        player.openInventory(inv);
        return true;
    }

    public boolean addMenuItem(String menuName, int slot, String target, String material, String name, List<String> lore) {
        String key = menuName.toLowerCase();
        BackendMenu menu = menus.get(key);
        if (menu == null) return false;

        List<MenuBridgeProtocol.MenuItem> items = menu.getItems();
        items.removeIf(item -> item.slot() == slot);
        items.add(new MenuBridgeProtocol.MenuItem(slot, target, material, name, lore));
        menu.setItems(items);

        saveMenu(menu);
        return true;
    }

    public boolean removeMenuItem(String menuName, int slot) {
        String key = menuName.toLowerCase();
        BackendMenu menu = menus.get(key);
        if (menu == null) return false;

        List<MenuBridgeProtocol.MenuItem> items = menu.getItems();
        boolean removed = items.removeIf(item -> item.slot() == slot);
        if (removed) {
            menu.setItems(items);
            saveMenu(menu);
        }
        return removed;
    }

    public boolean setMenuTitle(String menuName, String title) {
        String key = menuName.toLowerCase();
        BackendMenu menu = menus.get(key);
        if (menu == null) return false;

        menu.setTitle(title);
        saveMenu(menu);
        return true;
    }

    public boolean setMenuRows(String menuName, int rows) {
        if (rows < 2 || rows > 6) return false;
        String key = menuName.toLowerCase();
        BackendMenu menu = menus.get(key);
        if (menu == null) return false;

        menu.setRows(rows);
        saveMenu(menu);
        return true;
    }

    public Optional<BackendMenu> getMenu(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(menus.get(name.toLowerCase()));
    }

    public List<BackendMenu> getAllMenus() {
        return new ArrayList<>(menus.values());
    }

    public void saveMenu(BackendMenu menu) {
        File file = new File(menusDir, menu.getName() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("title", menu.getTitle());
        config.set("rows", menu.getRows());

        List<MenuBridgeProtocol.MenuItem> items = menu.getItems();
        for (int i = 0; i < items.size(); i++) {
            MenuBridgeProtocol.MenuItem item = items.get(i);
            String path = "items.item_" + i + ".";
            config.set(path + "slot", item.slot());
            config.set(path + "target", item.target());
            config.set(path + "material", item.material());
            config.set(path + "name", item.name());
            config.set(path + "lore", item.lore());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save menu file " + file.getName(), e);
        }
    }
}
