/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.bukkit.dialog;

import com.demonz.velocitynavigator.bukkit.FoliaSchedulerCompat;
import com.demonz.velocitynavigator.common.UiBridgeProtocol;
import com.demonz.velocitynavigator.common.UiSessionRegistry;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class BackendDialogService {
    private static final String API = "io.papermc.paper.registry.data.dialog.";
    private final Plugin plugin;
    private final UiSessionRegistry sessions = new UiSessionRegistry();
    private final boolean available;
    private boolean failureReported;

    public BackendDialogService(Plugin plugin) {
        this.plugin = plugin;
        boolean supported;
        try {
            Class.forName("io.papermc.paper.dialog.Dialog", false, plugin.getClass().getClassLoader());
            Class.forName(API + "action.DialogActionCallback", false, plugin.getClass().getClassLoader());
            Class.forName("net.kyori.adventure.dialog.DialogLike", false, plugin.getClass().getClassLoader());
            supported = true;
        } catch (ClassNotFoundException | LinkageError exception) {
            supported = false;
        }
        available = supported;
    }

    public boolean available() { return available; }

    public void clear(Player player) { sessions.clear(player.getUniqueId()); }

    public boolean show(Player player, UiBridgeProtocol.Open open, Consumer<UiBridgeProtocol.Action> callback) {
        if (!available || !player.isOnline()) return false;
        Set<String> actions = open.buttons().stream().map(UiBridgeProtocol.Button::action)
                .filter(action -> !action.isBlank()).collect(Collectors.toSet());
        actions.add("@close");
        String localToken = sessions.create(player.getUniqueId(), "local", actions, open.lifetimeSeconds());
        try {
            List<Object> buttons = new ArrayList<>();
            List<Object> bodies = new ArrayList<>();
            if (!open.body().isBlank()) bodies.add(ReflectiveApi.api(API + "body.DialogBody", "plainMessage", text(open.body())));
            for (UiBridgeProtocol.Button button : open.buttons()) {
                if (button.action().isBlank()) {
                    bodies.add(ReflectiveApi.api(API + "body.DialogBody", "plainMessage", text(button.label()
                            + (button.tooltip().isBlank() ? "" : "\n" + button.tooltip()))));
                    continue;
                }
                Object action = action(player, open, localToken, button.action(), callback);
                buttons.add(ReflectiveApi.api(API + "ActionButton", "create", text(button.label()),
                        button.tooltip().isBlank() ? null : text(button.tooltip()), 180, action));
            }
            Object close = ReflectiveApi.api(API + "ActionButton", "create", text(open.closeLabel()), null, 180,
                    action(player, open, localToken, "@close", callback));
            Object base = ReflectiveApi.api(API + "DialogBase", "builder", text(open.title()));
            ReflectiveApi.call(base, "canCloseWithEscape", true);
            ReflectiveApi.call(base, "body", bodies);
            List<Object> inputs = new ArrayList<>();
            for (UiBridgeProtocol.Input field : open.inputs()) {
                Object builder = ReflectiveApi.api(API + "input.DialogInput", "text", field.key(), text(field.label()));
                ReflectiveApi.call(builder, "maxLength", field.maxLength());
                inputs.add(ReflectiveApi.call(builder, "build"));
            }
            ReflectiveApi.call(base, "inputs", inputs);
            Object builtBase = ReflectiveApi.call(base, "build");
            Object type = ReflectiveApi.api(API + "type.DialogType", "multiAction", buttons, close, open.authentication() ? 1 : 2);
            Object dialog = ReflectiveApi.api("io.papermc.paper.dialog.Dialog", "create", (Consumer<Object>) factory -> {
                try {
                    Object builder = ReflectiveApi.call(factory, "empty");
                    ReflectiveApi.call(builder, "base", builtBase);
                    ReflectiveApi.call(builder, "type", type);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Dialog builder is unavailable", exception);
                }
            });
            player.closeInventory();
            ReflectiveApi.call(player, "showDialog", dialog);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            sessions.clear(player.getUniqueId());
            if (!failureReported) {
                plugin.getLogger().warning("Native Java dialogs are unavailable on this runtime; using the configured fallback.");
                failureReported = true;
            }
            return false;
        }
    }

    private Object action(Player owner, UiBridgeProtocol.Open open, String localToken, String action,
                          Consumer<UiBridgeProtocol.Action> handler) throws ReflectiveOperationException {
        Class<?> callbackType = Class.forName(API + "action.DialogActionCallback");
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[]{callbackType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "VelocityNavigator dialog callback";
                };
            }
            if (args == null || args.length != 2 || !(args[1] instanceof Player clicked)
                    || !clicked.getUniqueId().equals(owner.getUniqueId())
                    || !sessions.consume(owner.getUniqueId(), "local", localToken, action)) return null;
            Map<String, String> values = new LinkedHashMap<>();
            if (!"@close".equals(action)) {
                for (UiBridgeProtocol.Input field : open.inputs()) {
                    Object value = ReflectiveApi.call(args[0], "getText", field.key());
                    if (!(value instanceof String text) || text.length() > field.maxLength()) return null;
                    values.put(field.key(), text);
                }
            }
            FoliaSchedulerCompat.runTask(plugin, clicked, () -> {
                if (clicked.isOnline()) handler.accept(new UiBridgeProtocol.Action(open.token(), action, values));
            });
            return null;
        });
        Object options = ReflectiveApi.api("net.kyori.adventure.text.event.ClickCallback$Options", "builder");
        ReflectiveApi.call(options, "uses", 1);
        ReflectiveApi.call(options, "lifetime", Duration.ofSeconds(open.lifetimeSeconds()));
        return ReflectiveApi.api(API + "action.DialogAction", "customClick", callback, ReflectiveApi.call(options, "build"));
    }

    private Object text(String value) throws ReflectiveOperationException {
        Object serializer = ReflectiveApi.api("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer", "legacySection");
        return ReflectiveApi.call(serializer, "deserialize", ChatColor.translateAlternateColorCodes('&', value));
    }
}
