/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.dialog;

import com.demonz.velocitynavigator.bukkit.BackendAuthSignGuiListener;
import com.demonz.velocitynavigator.common.ClientProtocolResolver;
import com.demonz.velocitynavigator.common.JavaUiMode;
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import com.demonz.velocitynavigator.common.UiBridgeProtocol;
import com.demonz.velocitynavigator.common.UiSessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackendUiService {
    private final Plugin plugin;
    private final BackendDialogService dialogs;
    private final BackendAuthSignGuiListener signs;
    private final Map<UUID, UiBridgeProtocol.ClientContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, String> tokens = new ConcurrentHashMap<>();
    private final UiSessionRegistry sessions = new UiSessionRegistry();

    public BackendUiService(Plugin plugin, BackendAuthSignGuiListener signs) {
        this.plugin = plugin;
        this.signs = signs;
        this.dialogs = new BackendDialogService(plugin);
        signs.setUiSubmitter(this::respond);
    }

    public BackendDialogService dialogs() { return dialogs; }
    public boolean hasPrompt(Player player) { return tokens.containsKey(player.getUniqueId()); }
    public void context(Player player, UiBridgeProtocol.ClientContext context) { contexts.put(player.getUniqueId(), context); }

    public String language(Player player) {
        if (player == null) return null;
        UiBridgeProtocol.ClientContext context = contexts.get(player.getUniqueId());
        return context != null && context.language() != null && !context.language().isBlank()
                ? context.language()
                : null;
    }

    public int protocol(Player player) {
        UiBridgeProtocol.ClientContext context = contexts.get(player.getUniqueId());
        int protocol = context == null ? -1 : context.protocol();
        return ClientProtocolResolver.resolve(player.getUniqueId(), protocol);
    }

    public boolean bedrock(Player player) {
        UiBridgeProtocol.ClientContext context = contexts.get(player.getUniqueId());
        if (context != null && context.bedrock()) return true;
        try {
            Object api = ReflectiveApi.api("org.geysermc.floodgate.api.FloodgateApi", "getInstance");
            return Boolean.TRUE.equals(ReflectiveApi.call(api, "isFloodgatePlayer", player.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) { return false; }
    }

    public void clear(Player player) {
        contexts.remove(player.getUniqueId());
        cancel(player);
    }

    public void cancel(Player player) {
        tokens.remove(player.getUniqueId());
        sessions.clear(player.getUniqueId());
        dialogs.clear(player);
        signs.clear(player);
    }

    public void open(Player player, UiBridgeProtocol.Open open) {
        String lang = language(player);
        cancel(player);
        contexts.put(player.getUniqueId(), new UiBridgeProtocol.ClientContext(open.protocol(), open.bedrock(), lang == null ? "" : lang));
        HashSet<String> actions = new HashSet<>();
        open.buttons().forEach(button -> { if (!button.action().isBlank()) actions.add(button.action()); });
        actions.add("@close");
        String localToken = sessions.create(player.getUniqueId(), open.token(), actions, open.lifetimeSeconds());
        tokens.put(player.getUniqueId(), localToken);
        if (open.mode().prefersDialog(protocol(player), dialogs.available(), bedrock(player))
                && dialogs.show(player, open, action -> respond(player, action))) return;
        if (open.authentication()) {
            if (open.mode() != JavaUiMode.COMMAND) signs.openAuthSign(player, open.register(), null, open.token());
        } else {
            openInventory(player, open);
        }
    }

    public void respond(Player player, UiBridgeProtocol.Action action) {
        String token = tokens.get(player.getUniqueId());
        if (token == null || !sessions.consume(player.getUniqueId(), action.token(), token, action.action())) return;
        tokens.remove(player.getUniqueId(), token);
        try {
            player.sendPluginMessage(plugin, MenuBridgeProtocol.CHANNEL, UiBridgeProtocol.action(action));
        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + "This menu has expired. Please open it again.");
        }
    }

    public void select(Player player, UiInventoryHolder holder, String action) {
        if (action == null) return;
        player.closeInventory();
        respond(player, new UiBridgeProtocol.Action(holder.token(), action, Map.of()));
    }

    private void openInventory(Player player, UiBridgeProtocol.Open open) {
        UiInventoryHolder holder = new UiInventoryHolder(open.token());
        String title = open.title().length() > 32 ? open.title().substring(0, 32) : open.title();
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.bind(inventory);
        int slot = 0;
        for (UiBridgeProtocol.Button button : open.buttons()) {
            if (slot >= 52) break;
            Material material = Material.matchMaterial(button.material());
            if (material == null || !material.isItem()) material = Material.PAPER;
            inventory.setItem(slot, item(material, button.label(), button.tooltip()));
            if (!button.action().isBlank()) holder.actions().put(slot, button.action());
            slot++;
        }
        inventory.setItem(52, item(Material.BOOK, open.title(), open.body()));
        inventory.setItem(53, item(Material.BARRIER, open.closeLabel(), ""));
        holder.actions().put(53, "@close");
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isBlank()) meta.setLore(Arrays.stream(lore.split("\n")).limit(16).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
