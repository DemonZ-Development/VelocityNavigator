/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.npc;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionStage;

public final class BackendNPCCommand implements TabExecutor {

    private static final int NEAREST_RADIUS = 5;

    private final BackendNPCManager npcManager;

    public BackendNPCCommand(BackendNPCManager npcManager) {
        this.npcManager = npcManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("velocitynavigator.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to execute NPC commands.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                return handleCreate(sender, args);
            case "skin":
                return handleSkin(sender, args);
            case "move":
                return handleMove(sender, args);
            case "title":
                return handleTitle(sender, args);
            case "toggle":
                return handleToggle(sender, args);
            case "glow":
                return handleGlow(sender, args);
            case "enable":
            case "disable":
                return handleEnable(sender, args);
            case "sneak":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc sneak <id> <target|none>");
                    return true;
                }
                String sneakTarget = joinArguments(args, 2);
                if (npcManager.setSneakTarget(args[1], "none".equalsIgnoreCase(sneakTarget) ? null : sneakTarget)) {
                    sender.sendMessage(ChatColor.GREEN + "Sneak-click target for NPC '" + args[1] + "' "
                            + ("none".equalsIgnoreCase(sneakTarget) ? "cleared." : "set to '" + sneakTarget + "'."));
                } else {
                    unknownNpc(sender, args[1]);
                }
                return true;
            case "target":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc target <id> <server:name|menu:name|cmd:command>");
                    return true;
                }
                String target = joinArguments(args, 2);
                if (npcManager.setTarget(args[1], target)) {
                    sender.sendMessage(ChatColor.GREEN + "Target for NPC '" + args[1] + "' set to '" + target + "'.");
                } else {
                    unknownNpc(sender, args[1]);
                }
                return true;
            case "action":
                return handleAction(sender, args);
            case "hand":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc hand <id> <material|none>");
                    return true;
                }
                if (!validEquipment(args[2])) {
                    sender.sendMessage(ChatColor.RED + "Unknown item '" + args[2] + "'. Use a material name such as DIAMOND_SWORD, or none to clear it.");
                    return true;
                }
                if (npcManager.setHandItem(args[1], args[2])) {
                    sender.sendMessage(ChatColor.GREEN + "Hand item for NPC '" + args[1] + "' " + equipmentResult(args[2]) + ".");
                } else {
                    unknownNpc(sender, args[1]);
                }
                return true;
            case "offhand":
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc offhand <id> <material|none>");
                    return true;
                }
                if (!validEquipment(args[2])) {
                    sender.sendMessage(ChatColor.RED + "Unknown item '" + args[2] + "'. Use a material name such as SHIELD, or none to clear it.");
                    return true;
                }
                if (npcManager.setOffhandItem(args[1], args[2])) {
                    sender.sendMessage(ChatColor.GREEN + "Offhand item for NPC '" + args[1] + "' " + equipmentResult(args[2]) + ".");
                } else {
                    unknownNpc(sender, args[1]);
                }
                return true;
            case "remove":
            case "delete":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "status":
            case "info":
                return handleStatus(sender, args);
            case "respawn":
                return handleRespawn(sender, args);
            case "tp":
            case "teleport":
                return handleTeleport(sender, args);
            case "reload":
                int loaded = npcManager.loadAndSpawnAll();
                long enabled = npcManager.getAllNPCs().stream().filter(BackendNPC::isEnabled).count();
                sender.sendMessage(ChatColor.GREEN + "Reloaded " + loaded + " NPC(s), " + enabled + " currently enabled.");
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can create NPCs.");
            return true;
        }
        if (args.length < 2) {
            sendCreateHelp(sender);
            return true;
        }
        Player player = (Player) sender;
        CreateArguments create = parseCreateArguments(args);
        String id = create.id();
        String targetServer = create.targetServer();
        String title = create.title().isBlank() ? "&b&l" + id.toUpperCase(Locale.ROOT) : create.title();

        String defaultSkin = validSkinUsername(player.getName()) ? player.getName() : "Steve";
        try {
            if (npcManager.createNPC(id, player.getLocation(), targetServer, defaultSkin, title)) {
                sender.sendMessage(ChatColor.GREEN + "NPC '" + id + "' created with skin '" + defaultSkin + "'.");
                if ("none".equalsIgnoreCase(targetServer)) {
                    sender.sendMessage(ChatColor.AQUA + "Next: /vnavnpc action " + id + " <server|menu|command> ...");
                }
                sender.sendMessage(ChatColor.GRAY + "Customize it with skin, title, hand, glow, move or action.");
            } else {
                sender.sendMessage(ChatColor.RED + "An NPC with ID '" + id + "' already exists.");
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        }
        return true;
    }

    static CreateArguments parseCreateArguments(String[] args) {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException("Usage: /vnavnpc create <id> [display name...]");
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        boolean legacyTarget = args.length > 2 && isTargetSyntax(args[2]);
        String targetServer = legacyTarget ? args[2] : "none";
        int titleStart = legacyTarget ? 3 : 2;
        String title = args.length > titleStart ? String.join(" ", Arrays.copyOfRange(args, titleStart, args.length)) : "";
        return new CreateArguments(id, targetServer, title);
    }

    private static boolean isTargetSyntax(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("server:") || normalized.startsWith("menu:")
                || normalized.startsWith("cmd:") || normalized.startsWith("command:")
                || normalized.startsWith("action:cond(");
    }

    record CreateArguments(String id, String targetServer, String title) {
    }

    private boolean handleSkin(CommandSender sender, String[] args) {
        boolean fromConsole = !(sender instanceof Player);
        String id;
        String skinUser;
        if (args.length >= 3) {
            id = args[1];
            skinUser = args[2];
        } else if (!fromConsole && args.length == 2 && npcManager.getNPC(args[1]).isPresent()) {
            id = args[1];
            skinUser = ((Player) sender).getName();
        } else if (!fromConsole) {
            BackendNPC nearest = npcManager.findNearestNPC(((Player) sender).getLocation(), NEAREST_RADIUS);
            if (nearest == null) {
                sender.sendMessage(ChatColor.RED + "No NPC found nearby. Usage: /vnavnpc skin [id] <skinUsername>");
                return true;
            }
            id = nearest.getId();
            skinUser = args.length == 2 ? args[1] : ((Player) sender).getName();
        } else {
            sender.sendMessage(ChatColor.RED + "Console must provide both arguments: /vnavnpc skin <id> <skinUsername>");
            return true;
        }

        if (!validSkinUsername(skinUser)) {
            sender.sendMessage(ChatColor.RED + "Invalid Minecraft username '" + skinUser
                    + "'. Use 3-16 letters, numbers or underscores.");
            return true;
        }
        if (npcManager.setSkin(id, skinUser)) {
            sender.sendMessage(ChatColor.GREEN + "Skin request for NPC '" + id + "' saved as '" + skinUser
                    + "'. It will apply after Mojang resolves it.");
        } else {
            unknownNpc(sender, id);
        }
        return true;
    }

    private boolean handleAction(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc action <id> <server|menu|command|none> [value...]");
            return true;
        }
        String id = args[1];
        if (npcManager.getNPC(id).isEmpty()) {
            unknownNpc(sender, id);
            return true;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        String target;
        if ("none".equals(type)) {
            target = "none";
        } else {
            if (args.length < 4) {
                sender.sendMessage(ChatColor.RED + "Provide the " + type + " value after the action type.");
                return true;
            }
            String value = joinArguments(args, 3);
            target = switch (type) {
                case "server" -> "server:" + value;
                case "menu" -> "menu:" + value;
                case "command", "cmd" -> "cmd:" + value;
                default -> null;
            };
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Unknown action type '" + args[2]
                        + "'. Use server, menu, command or none.");
                return true;
            }
        }
        npcManager.setTarget(id, target);
        sender.sendMessage(ChatColor.GREEN + "Action for NPC '" + id + "' set to " + target + ".");
        return true;
    }

    private boolean handleMove(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can move NPCs.");
            return true;
        }
        Player mover = (Player) sender;
        String id = resolveIdOrNearest(mover, args.length > 1 ? args[1] : null);
        if (id == null) {
            sender.sendMessage(ChatColor.RED + "No NPC found nearby. Usage: /vnavnpc move [id]");
            return true;
        }
        if (npcManager.moveNPC(id, mover.getLocation())) {
            sender.sendMessage(ChatColor.GREEN + "Moved NPC '" + id + "' to your current location.");
        } else {
            unknownNpc(sender, id);
        }
        return true;
    }

    private boolean handleTitle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc title <id> <set|add|remove|clear> [args...]");
            return true;
        }
        String id = args[1];
        String action = args[2].toLowerCase(Locale.ROOT);

        switch (action) {
            case "set": {
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc title <id> set <lineIndex> <text...>");
                    return true;
                }
                try {
                    int lineIdx = Integer.parseInt(args[3]);
                    String text = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                    if (npcManager.setTitleLine(id, lineIdx, text)) {
                        sender.sendMessage(ChatColor.GREEN + "Set title line " + lineIdx + " for NPC '" + id + "'.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to set title line for NPC '" + id + "'.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid line index integer.");
                }
                return true;
            }
            case "add": {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc title <id> add <text...>");
                    return true;
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (npcManager.addTitleLine(id, text)) {
                    sender.sendMessage(ChatColor.GREEN + "Appended title line to NPC '" + id + "'.");
                } else {
                    unknownNpc(sender, id);
                }
                return true;
            }
            case "remove": {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc title <id> remove <lineIndex>");
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[3]);
                    if (npcManager.removeTitleLine(id, idx)) {
                        sender.sendMessage(ChatColor.GREEN + "Removed title line " + idx + " from NPC '" + id + "'.");
                    } else {
                        sender.sendMessage(ChatColor.RED + "Failed to remove title line from NPC '" + id + "'.");
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid line index integer.");
                }
                return true;
            }
            case "clear": {
                if (npcManager.clearTitleLines(id)) {
                    sender.sendMessage(ChatColor.GREEN + "Cleared all title lines for NPC '" + id + "'.");
                } else {
                    unknownNpc(sender, id);
                }
                return true;
            }
            default:
                sender.sendMessage(ChatColor.RED + "Unknown title action. Options: set, add, remove, clear.");
                return true;
        }
    }

    private boolean handleToggle(CommandSender sender, String[] args) {
        if (args.length < 3 || !"lookatplayer".equalsIgnoreCase(args[1])) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc toggle lookatplayer <id> [true|false]");
            return true;
        }
        String id = args[2];
        BackendNPC npc = npcManager.getNPC(id).orElse(null);
        if (npc == null) {
            unknownNpc(sender, id);
            return true;
        }
        if (args.length > 3 && !"true".equalsIgnoreCase(args[3]) && !"false".equalsIgnoreCase(args[3])) {
            sender.sendMessage(ChatColor.RED + "Expected true or false, got '" + args[3] + "'.");
            return true;
        }
        boolean newValue = args.length > 3 ? Boolean.parseBoolean(args[3]) : !npc.isLookAtPlayer();
        npcManager.setLookAtPlayer(id, newValue);
        sender.sendMessage(ChatColor.GREEN + "Look-at-player for NPC '" + id + "' set to " + newValue + ".");
        return true;
    }

    private boolean handleGlow(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc glow <id> <on|off> [color]");
            return true;
        }
        String id = args[1];
        String state = args[2].toLowerCase(Locale.ROOT);
        if (!"on".equals(state) && !"off".equals(state)) {
            sender.sendMessage(ChatColor.RED + "Expected on|off, got '" + args[2] + "'.");
            return true;
        }
        String color = args.length > 3 ? args[3] : null;
        if (color != null && !validGlowColor(color)) {
            sender.sendMessage(ChatColor.RED + "Unknown glow color '" + color + "'. Press Tab to see valid colors.");
            return true;
        }
        if (npcManager.setGlowing(id, "on".equals(state), color)) {
            sender.sendMessage(ChatColor.GREEN + "Glow for NPC '" + id + "' turned "
                    + state + (color != null ? " (color " + color.toUpperCase(Locale.ROOT) + ")" : "") + ".");
        } else {
            unknownNpc(sender, id);
        }
        return true;
    }

    private boolean handleEnable(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc enable|disable <id>");
            return true;
        }
        boolean enabling = args[0].equalsIgnoreCase("enable");
        String id = args[1];
        if (npcManager.setEnabled(id, enabling)) {
            sender.sendMessage(ChatColor.GREEN + "NPC '" + id + "' is now " + (enabling ? "enabled" : "disabled") + ".");
        } else {
            unknownNpc(sender, id);
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        Player player = sender instanceof Player ? (Player) sender : null;
        String id = player != null ? resolveIdOrNearest(player, args.length > 1 ? args[1] : null) : (args.length > 1 ? args[1] : null);
        if (id == null) {
            sender.sendMessage(ChatColor.RED + "No NPC found nearby. Usage: /vnavnpc remove <id>");
            return true;
        }
        if (npcManager.removeNPC(id)) {
            sender.sendMessage(ChatColor.GREEN + "Successfully removed NPC '" + id + "'.");
        } else {
            unknownNpc(sender, id);
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<BackendNPC> npcs = npcManager.getAllNPCs();
        if (npcs.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "No VelocityNavigator NPCs registered.");
            return true;
        }
        sender.sendMessage(ChatColor.GOLD + "=== VelocityNavigator NPCs (" + npcs.size() + ") ===");
        for (BackendNPC npc : npcs) {
            Location loc = npc.getLocation();
            String where = loc != null && loc.getWorld() != null
                    ? loc.getWorld().getName() + " @ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                    : "no location";
            sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.WHITE + npc.getId()
                    + ChatColor.GRAY + " -> " + ChatColor.GREEN + npc.getTargetServer()
                    + (npc.getSneakTargetServer() != null && !npc.getSneakTargetServer().isBlank()
                            ? ChatColor.GRAY + " [sneak: " + ChatColor.AQUA + npc.getSneakTargetServer() + ChatColor.GRAY + "]" : "")
                    + (npc.isGlowing() ? ChatColor.AQUA + " [glow]" : "")
                    + (npc.isEnabled() ? "" : ChatColor.RED + " [disabled]")
                    + ChatColor.GRAY + " (" + where + ")");
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc status <id>");
            return true;
        }
        BackendNPC npc = npcManager.getNPC(args[1]).orElse(null);
        if (npc == null) {
            unknownNpc(sender, args[1]);
            return true;
        }
        Location loc = npc.getLocation();
        String location = loc == null ? "unavailable" : npc.getWorldName() + " @ "
                + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
        sender.sendMessage(ChatColor.GOLD + "=== NPC " + npc.getId() + " ===");
        sender.sendMessage(ChatColor.GRAY + "Renderer: " + ChatColor.AQUA + npcManager.rendererName()
                + ChatColor.GRAY + " | Spawned: " + stateColor(npcManager.isSpawnedNow(npc))
                + ChatColor.GRAY + " | Viewers: " + ChatColor.WHITE + npcManager.viewerCount(npc.getId()));
        sender.sendMessage(ChatColor.GRAY + "Enabled: " + stateColor(npc.isEnabled())
                + ChatColor.GRAY + " | Glow: " + stateColor(npc.isGlowing())
                + (npc.isGlowing() ? ChatColor.GRAY + " (" + npc.getGlowColor() + ")" : "")
                + ChatColor.GRAY + " | Look: " + stateColor(npc.isLookAtPlayer()));
        sender.sendMessage(ChatColor.GRAY + "Target: " + ChatColor.GREEN + npc.getTargetServer()
                + ChatColor.GRAY + " | Skin: " + ChatColor.WHITE
                + (npc.getSkinUsername() == null || npc.getSkinUsername().isBlank() ? "default" : npc.getSkinUsername()));
        sender.sendMessage(ChatColor.GRAY + "Location: " + ChatColor.WHITE + location);
        return true;
    }

    private boolean handleRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.AQUA + "Usage: /vnavnpc respawn <id>");
            return true;
        }
        BackendNPC npc = npcManager.getNPC(args[1]).orElse(null);
        if (npc == null) {
            unknownNpc(sender, args[1]);
            return true;
        }
        if (!npc.isEnabled()) {
            sender.sendMessage(ChatColor.RED + "NPC '" + npc.getId() + "' is disabled. Enable it first.");
            return true;
        }
        npcManager.respawnNPC(npc.getId());
        sender.sendMessage(ChatColor.GREEN + "Respawn requested for NPC '" + npc.getId() + "'.");
        return true;
    }

    private static String stateColor(boolean state) {
        return (state ? ChatColor.GREEN : ChatColor.RED) + Boolean.toString(state);
    }

    private boolean handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can teleport to NPCs.");
            return true;
        }
        Player player = (Player) sender;
        String id = resolveIdOrNearest(player, args.length > 1 ? args[1] : null);
        if (id == null) {
            sender.sendMessage(ChatColor.RED + "No NPC found nearby. Usage: /vnavnpc tp [id]");
            return true;
        }
        BackendNPC npc = npcManager.getNPC(id).orElse(null);
        if (npc == null || npc.getLocation() == null) {
            unknownNpc(sender, id);
            return true;
        }
        Location destination = npc.getLocation();
        if (destination.getWorld() == null) {
            sender.sendMessage(ChatColor.RED + "NPC '" + id + "' is in an unloaded world ('" + npc.getWorldName() + "').");
            return true;
        }
        teleportPlayer(player, destination, id);
        return true;
    }

    private void teleportPlayer(Player player, Location destination, String npcId) {
        if (!FoliaSchedulerCompat.isFolia()) {
            if (player.teleport(destination)) {
                player.sendMessage(ChatColor.GREEN + "Teleported to NPC '" + npcId + "'.");
            } else {
                player.sendMessage(ChatColor.RED + "Teleport to NPC '" + npcId + "' was rejected by the server.");
            }
            return;
        }
        try {
            Object result = player.getClass().getMethod("teleportAsync", Location.class).invoke(player, destination);
            if (result instanceof CompletionStage<?> stage) {
                stage.whenComplete((success, error) -> FoliaSchedulerCompat.runTask(
                        npcManager.plugin(), player,
                        () -> player.sendMessage(error == null && Boolean.TRUE.equals(success)
                                ? ChatColor.GREEN + "Teleported to NPC '" + npcId + "'."
                                : ChatColor.RED + "Teleport to NPC '" + npcId + "' failed.")));
            } else {
                player.sendMessage(ChatColor.RED + "This Folia build does not expose a compatible teleport result.");
            }
        } catch (ReflectiveOperationException e) {
            player.sendMessage(ChatColor.RED + "This Folia build does not support asynchronous NPC teleporting.");
        }
    }

    private String resolveIdOrNearest(Player player, String explicitId) {
        if (explicitId != null) {
            return explicitId;
        }
        BackendNPC nearest = npcManager.findNearestNPC(player.getLocation(), NEAREST_RADIUS);
        return nearest != null ? nearest.getId() : null;
    }

    private void unknownNpc(CommandSender sender, String id) {
        sender.sendMessage(ChatColor.RED + "No NPC found with ID '" + id + "'.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "=== VelocityNavigator NPC Commands ===");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc create <id> [display name...]" + ChatColor.GRAY + " - Create a player NPC");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc action <id> <server|menu|command|none> [value...]" + ChatColor.GRAY + " - Set click behavior");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc skin [id] [skinUsername]" + ChatColor.GRAY + " - Set skin (defaults to your skin & nearest NPC)");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc move [id]" + ChatColor.GRAY + " - Move nearest/specified NPC to you");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc remove [id]" + ChatColor.GRAY + " - Remove nearest/specified NPC");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc tp [id]" + ChatColor.GRAY + " - Teleport to nearest/specified NPC");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc sneak <id> <target|none>" + ChatColor.GRAY + " - Set or clear sneak-click target");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc hand <id> <material|none>" + ChatColor.GRAY + " - Set or clear hand item");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc offhand <id> <material|none>" + ChatColor.GRAY + " - Set or clear offhand item");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc glow <id> <on|off> [color]" + ChatColor.GRAY + " - Glow outline & team color");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc enable|disable <id>" + ChatColor.GRAY + " - Spawn control without deleting");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc title <id> set|add|remove|clear" + ChatColor.GRAY + " - Line 0 is the first line");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc list | reload");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc status|respawn <id>" + ChatColor.GRAY + " - Inspect or rebuild one NPC");
    }

    private void sendCreateHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "=== Create an NPC ===");
        sender.sendMessage(ChatColor.AQUA + "/vnavnpc create <id> [display name...]");
        sender.sendMessage(ChatColor.WHITE + "id" + ChatColor.GRAY + " = a unique name, for example " + ChatColor.AQUA + "selector");
        sender.sendMessage(ChatColor.AQUA + "Example: /vnavnpc create selector &b&lSERVER SELECTOR");
        sender.sendMessage(ChatColor.AQUA + "Then: /vnavnpc action selector menu main");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("velocitynavigator.admin")) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (BackendNPC npc : npcManager.getAllNPCs()) {
            ids.add(npc.getId());
        }

        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0],
                    Arrays.asList("create", "skin", "move", "title", "toggle", "glow", "enable", "disable",
                            "action", "sneak", "hand", "offhand", "remove", "delete", "list", "status", "respawn", "tp", "reload"),
                    new ArrayList<>());
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create":
                    return StringUtil.copyPartialMatches(args[1], List.of("selector", "lobby_npc", "games_npc"), new ArrayList<>());
                case "skin":
                case "move":
                case "remove":
                case "delete":
                case "tp":
                case "teleport":
                case "status":
                case "info":
                case "respawn":
                    return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
                case "toggle":
                    return StringUtil.copyPartialMatches(args[1], List.of("lookatplayer"), new ArrayList<>());
                case "enable":
                case "disable":
                case "sneak":
                case "target":
                case "action":
                case "hand":
                case "offhand":
                case "title":
                case "glow":
                    return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
                default:
                    return List.of();
            }
        }
        if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create": {
                    return StringUtil.copyPartialMatches(args[2], List.of("&b&lNPC_NAME"), new ArrayList<>());
                }
                case "action":
                    return StringUtil.copyPartialMatches(args[2], List.of("server", "menu", "command", "none"), new ArrayList<>());
                case "toggle":
                    return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
                case "title":
                    return StringUtil.copyPartialMatches(args[2], List.of("set", "add", "remove", "clear"), new ArrayList<>());
                case "glow":
                    return StringUtil.copyPartialMatches(args[2], List.of("on", "off"), new ArrayList<>());
                case "skin": {
                    List<String> skins = new ArrayList<>(List.of("Steve", "Alex"));
                    if (sender instanceof Player player && validSkinUsername(player.getName())) {
                        skins.add(0, player.getName());
                    }
                    return StringUtil.copyPartialMatches(args[2], skins, new ArrayList<>());
                }
                case "hand":
                case "offhand":
                    return suggestMaterials(args[2]);
                case "target":
                case "sneak":
                    List<String> targets = new ArrayList<>();
                    if ("sneak".equalsIgnoreCase(args[0])) {
                        targets.add("none");
                    }
                    targets.addAll(targetSuggestions());
                    return StringUtil.copyPartialMatches(args[2], targets, new ArrayList<>());
                default:
                    return List.of();
            }
        }
        if (args.length == 4 && "glow".equalsIgnoreCase(args[0])) {
            List<String> colors = new ArrayList<>();
            for (ChatColor color : ChatColor.values()) {
                if (color.isColor()) {
                    colors.add(color.name());
                }
            }
            return StringUtil.copyPartialMatches(args[3], colors, new ArrayList<>());
        }
        if (args.length == 4 && "toggle".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[3], List.of("true", "false"), new ArrayList<>());
        }
        if (args.length == 4 && "action".equalsIgnoreCase(args[0])) {
            if ("menu".equalsIgnoreCase(args[2])) {
                return StringUtil.copyPartialMatches(args[3], List.of("main", "games"), new ArrayList<>());
            }
            if ("server".equalsIgnoreCase(args[2])) {
                return existingServerTargets(args[3]);
            }
            return List.of();
        }
        if (args.length == 4 && "title".equalsIgnoreCase(args[0])) {
            if ("set".equalsIgnoreCase(args[2]) || "remove".equalsIgnoreCase(args[2])) {
                BackendNPC npc = npcManager.getNPC(args[1]).orElse(null);
                int lines = npc == null ? 1 : npc.getHologramLines().size();
                List<String> indexes = new ArrayList<>();
                int limit = "set".equalsIgnoreCase(args[2]) ? lines + 1 : lines;
                for (int i = 0; i < Math.max(1, limit); i++) {
                    indexes.add(Integer.toString(i));
                }
                return StringUtil.copyPartialMatches(args[3], indexes, new ArrayList<>());
            }
            if ("add".equalsIgnoreCase(args[2])) {
                return StringUtil.copyPartialMatches(args[3], List.of("&b&lTITLE"), new ArrayList<>());
            }
        }
        if (args.length == 5 && "title".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[2])) {
            return StringUtil.copyPartialMatches(args[4], List.of("&b&lTITLE"), new ArrayList<>());
        }
        return List.of();
    }

    private List<String> targetSuggestions() {
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        for (BackendNPC npc : npcManager.getAllNPCs()) {
            if (npc.getTargetServer() != null && !npc.getTargetServer().isBlank()) {
                targets.add(npc.getTargetServer());
            }
        }
        return new ArrayList<>(targets);
    }

    private List<String> existingServerTargets(String prefix) {
        LinkedHashSet<String> servers = new LinkedHashSet<>();
        for (BackendNPC npc : npcManager.getAllNPCs()) {
            String target = npc.getTargetServer();
            if (target == null || target.isBlank()) {
                continue;
            }
            if (target.toLowerCase(Locale.ROOT).startsWith("server:")) {
                servers.add(target.substring(7));
            } else if (!target.contains(":")) {
                servers.add(target);
            }
        }
        return StringUtil.copyPartialMatches(prefix, servers, new ArrayList<>());
    }

    private List<String> suggestMaterials(String prefix) {
        List<String> names = new ArrayList<>();
        names.add("none");
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isLegacy()) {
                names.add(material.name());
            }
        }
        List<String> matches = StringUtil.copyPartialMatches(prefix, names, new ArrayList<>());
        return matches.size() > 60 ? matches.subList(0, 60) : matches;
    }

    private static boolean validEquipment(String value) {
        if ("none".equalsIgnoreCase(value) || "air".equalsIgnoreCase(value)) {
            return true;
        }
        Material material = Material.matchMaterial(value);
        return material != null && material.isItem();
    }

    private static String equipmentResult(String value) {
        return "none".equalsIgnoreCase(value) || "air".equalsIgnoreCase(value)
                ? "cleared"
                : "set to '" + value.toUpperCase(Locale.ROOT) + "'";
    }

    private static boolean validGlowColor(String value) {
        try {
            return ChatColor.valueOf(value.toUpperCase(Locale.ROOT)).isColor();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean validSkinUsername(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{3,16}");
    }

    private static String joinArguments(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
    }
}
