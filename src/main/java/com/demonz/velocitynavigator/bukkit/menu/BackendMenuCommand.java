/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.menu;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class BackendMenuCommand implements CommandExecutor {

    private final BackendMenuManager menuManager;

    public BackendMenuCommand(BackendMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "open":
                if (!sender.hasPermission("velocitynavigator.use") && !sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to use selector menus.");
                    return true;
                }
                if (args.length < 2) {
                    if (sender instanceof Player) {
                        menuManager.openMenu((Player) sender, "main");
                    } else {
                        sender.sendMessage(ChatColor.AQUA + "Usage: /vnavmenu open <player> [menu_name]");
                    }
                    return true;
                }
                Player targetPlayer = Bukkit.getPlayerExact(args[1]);
                String menuToOpen = args.length > 2 ? args[2] : "main";
                if (targetPlayer == null && sender instanceof Player) {
                    menuToOpen = args[1];
                    targetPlayer = (Player) sender;
                }
                if (targetPlayer != sender && !sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "You do not have permission to open menus for other players.");
                    return true;
                }
                if (targetPlayer == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }
                boolean opened = menuManager.openMenu(targetPlayer, menuToOpen);
                if (!opened) {
                    sender.sendMessage(ChatColor.RED + "Menu '" + menuToOpen + "' not found.");
                }
                return true;

            case "add":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 6) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavmenu add <menu_name> <slot> <target> <material> <name> [lore...]");
                    return true;
                }
                try {
                    String menuName = args[1];
                    int slot = Integer.parseInt(args[2]);
                    String target = args[3];
                    String material = args[4];
                    String name = args[5];
                    List<String> lore = args.length > 6 ? Arrays.asList(String.join(" ", Arrays.copyOfRange(args, 6, args.length)).split("\\|")) : List.of();

                    boolean added = menuManager.addMenuItem(menuName, slot, target, material, name, lore);
                    if (added) {
                        sender.sendMessage(ChatColor.GREEN + "Added item to menu '" + menuName + "' at slot " + slot + ".");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Menu '" + menuName + "' not found.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Slot must be an integer.");
                }
                return true;

            case "remove":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavmenu remove <menu_name> <slot>");
                    return true;
                }
                try {
                    String menuName = args[1];
                    int slot = Integer.parseInt(args[2]);
                    boolean removed = menuManager.removeMenuItem(menuName, slot);
                    if (removed) {
                        sender.sendMessage(ChatColor.GREEN + "Removed item from slot " + slot + " in menu '" + menuName + "'.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to remove item at slot " + slot + ".");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Slot must be an integer.");
                }
                return true;

            case "title":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavmenu title <menu_name> <title...>");
                    return true;
                }
                String titleMenu = args[1];
                String newTitle = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                boolean titleUpdated = menuManager.setMenuTitle(titleMenu, newTitle);
                if (titleUpdated) {
                    sender.sendMessage(ChatColor.GREEN + "Updated title of menu '" + titleMenu + "'.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Menu '" + titleMenu + "' not found.");
                }
                return true;

            case "rows":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavmenu rows <menu_name> <2-6>");
                    return true;
                }
                try {
                    String rowsMenu = args[1];
                    int rows = Integer.parseInt(args[2]);
                    boolean rowsUpdated = menuManager.setMenuRows(rowsMenu, rows);
                    if (rowsUpdated) {
                        sender.sendMessage(ChatColor.GREEN + "Updated rows of menu '" + rowsMenu + "' to " + rows + ".");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Menu '" + rowsMenu + "' not found.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Rows must be an integer between 2 and 6.");
                }
                return true;

            case "list":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                List<BackendMenu> menus = menuManager.getAllMenus();
                sender.sendMessage(ChatColor.GOLD + "=== Registered Menus (" + menus.size() + ") ===");
                for (BackendMenu m : menus) {
                    sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + m.getName() +
                            ChatColor.GRAY + " (Title: " + ChatColor.translateAlternateColorCodes('&', m.getTitle()) +
                            ChatColor.GRAY + ", Rows: " + m.getRows() + ", Items: " + m.getItems().size() + ")");
                }
                return true;

            case "reload":
                if (!sender.hasPermission("velocitynavigator.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                menuManager.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "All backend YAML menus reloaded from menus/.");
                return true;

            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "=== VelocityNavigator Menu Commands ===");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu open [player] [menu_name]");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu add <menu_name> <slot> <target> <material> <name> [lore]");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu remove <menu_name> <slot>");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu title <menu_name> <title>");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu rows <menu_name> <2-6>");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu list | reload");
    }
}
