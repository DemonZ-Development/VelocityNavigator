/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit;

import com.demonz.velocitynavigator.bukkit.menu.BackendMenuCommand;
import com.demonz.velocitynavigator.bukkit.npc.BackendNPCCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BackendVNCommand implements TabExecutor {

    private final VelocityNavigatorBridge bridge;
    private final BackendNPCCommand npcCommand;
    private final BackendMenuCommand menuCommand;

    public BackendVNCommand(VelocityNavigatorBridge bridge, BackendNPCCommand npcCommand, BackendMenuCommand menuCommand) {
        this.bridge = bridge;
        this.npcCommand = npcCommand;
        this.menuCommand = menuCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("velocitynavigator.admin") && !sender.hasPermission("velocitynavigator.use")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute VelocityNavigator commands.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "npc":
            case "npcs":
                if (npcCommand != null) {
                    return npcCommand.onCommand(sender, command, label, subArgs);
                } else {
                    sender.sendMessage(ChatColor.RED + "NPC feature is disabled in config.yml.");
                    return true;
                }
            case "menu":
            case "menus":
            case "gui":
                if (menuCommand != null) {
                    return menuCommand.onCommand(sender, command, label, subArgs);
                } else {
                    sender.sendMessage(ChatColor.RED + "Backend menu feature is disabled in config.yml.");
                    return true;
                }
            case "version":
            case "ver":
                sender.sendMessage(ChatColor.AQUA + "VelocityNavigator Universal JAR v" + bridge.getDescription().getVersion() + " (Backend Bridge Mode)");
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "=== VelocityNavigator Commands ===");
        sender.sendMessage(ChatColor.AQUA + "/vnav help " + ChatColor.GRAY + "- Show backend command help");
        sender.sendMessage(ChatColor.AQUA + "/vnav npc [create|skin|target|title|glow|status|respawn|remove|list] " + ChatColor.GRAY + "- Manage server selector NPCs");
        sender.sendMessage(ChatColor.AQUA + "/vnav menu [open|list|reload] " + ChatColor.GRAY + "- Manage backend inventory GUI menus");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc help " + ChatColor.GRAY + "- Complete NPC command reference");
        sender.sendMessage(ChatColor.AQUA + "/vnavmenu help " + ChatColor.GRAY + "- Complete backend menu reference");
        sender.sendMessage(ChatColor.AQUA + "/vnav version " + ChatColor.GRAY + "- Display backend plugin version");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], List.of("npc", "menu", "version"), new ArrayList<>());
        }
        if (args.length > 1 && ("npc".equalsIgnoreCase(args[0]) || "npcs".equalsIgnoreCase(args[0]))) {
            if (npcCommand == null) {
                return List.of();
            }
            return npcCommand.onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && ("menu".equalsIgnoreCase(args[0])
                || "menus".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0]))) {
            return StringUtil.copyPartialMatches(args[1], List.of("open", "add", "remove", "title", "rows", "list", "reload"), new ArrayList<>());
        }
        return List.of();
    }
}
