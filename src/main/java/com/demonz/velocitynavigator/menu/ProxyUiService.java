/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.menu;

import com.demonz.velocitynavigator.FloodgateIntegration;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.common.ClientProtocolResolver;
import com.demonz.velocitynavigator.common.JavaUiMode;
import com.demonz.velocitynavigator.common.UiBridgeProtocol;
import com.demonz.velocitynavigator.common.UiSessionRegistry;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class ProxyUiService {
    private record Capability(String backend, boolean dialogs) {}
    private record Handler(String token, Set<String> inputs, Consumer<UiBridgeProtocol.Action> callback) {}
    private final VelocityNavigator plugin;
    private final Map<UUID, Capability> capabilities = new ConcurrentHashMap<>();
    private final Map<UUID, Handler> handlers = new ConcurrentHashMap<>();
    private final UiSessionRegistry sessions = new UiSessionRegistry();

    public ProxyUiService(VelocityNavigator plugin) { this.plugin = plugin; }

    public void clear(Player player) {
        capabilities.remove(player.getUniqueId());
        handlers.remove(player.getUniqueId());
        sessions.clear(player.getUniqueId());
    }

    public int protocol(Player player) {
        return ClientProtocolResolver.resolve(player.getUniqueId(), player.getProtocolVersion().getProtocol());
    }

    public boolean canDialog(Player player, JavaUiMode mode) {
        Capability capability = capability(player);
        return mode.prefersDialog(protocol(player), capability != null && capability.dialogs(), bedrock(player));
    }

    private Capability capability(Player player) {
        Capability capability = capabilities.get(player.getUniqueId());
        return capability != null && player.getCurrentServer().map(connection ->
                connection.getServerInfo().getName().equals(capability.backend())).orElse(false) ? capability : null;
    }

    public boolean show(Player player, String title, String body, List<UiBridgeProtocol.Button> buttons,
                        List<UiBridgeProtocol.Input> inputs, JavaUiMode mode, boolean authentication,
                        boolean register, Consumer<UiBridgeProtocol.Action> callback) {
        Capability capability = capability(player);
        if (capability == null || mode == JavaUiMode.CHAT || (mode == JavaUiMode.COMMAND && !authentication) || bedrock(player)) return false;
        HashSet<String> actions = new HashSet<>();
        buttons.forEach(button -> { if (!button.action().isBlank()) actions.add(button.action()); });
        actions.add("@close");
        int seconds = plugin.config().menuToken().sessionSeconds();
        String token = sessions.create(player.getUniqueId(), capability.backend(), actions, seconds);
        handlers.put(player.getUniqueId(), new Handler(token, inputs.stream().map(UiBridgeProtocol.Input::key).collect(Collectors.toSet()), callback));
        try {
            UiBridgeProtocol.Open open = new UiBridgeProtocol.Open(token, title, body,
                    text(player, "menus.dialog.close"), mode, protocol(player), false,
                    authentication, register, seconds, buttons, inputs);
            boolean sent = player.getCurrentServer().map(connection -> {
                try { return connection.sendPluginMessage(JavaInventoryMenuService.CHANNEL, UiBridgeProtocol.open(open)); }
                catch (IOException | IllegalArgumentException exception) { return false; }
            }).orElse(false);
            if (sent) return true;
        } catch (RuntimeException exception) {
            plugin.logger().warn("Could not prepare Java UI for {}.", player.getUsername());
        }
        handlers.remove(player.getUniqueId());
        sessions.clear(player.getUniqueId());
        return false;
    }

    public void handle(Player player, ServerConnection backend, byte[] payload) throws IOException {
        if (UiBridgeProtocol.type(payload) == UiBridgeProtocol.Type.CAPABILITIES) {
            UiBridgeProtocol.Capabilities capability = UiBridgeProtocol.decodeCapabilities(payload);
            capabilities.put(player.getUniqueId(), new Capability(backend.getServerInfo().getName(), capability.dialogs()));
            String lang = plugin.config() != null && plugin.config().language() != null ? plugin.config().language().language() : "";
            backend.sendPluginMessage(JavaInventoryMenuService.CHANNEL, UiBridgeProtocol.context(protocol(player), bedrock(player), lang));
            if (plugin.isAuthenticationPending(player)) plugin.showAuthenticationPrompt(player);
        } else if (UiBridgeProtocol.type(payload) == UiBridgeProtocol.Type.ACTION) {
            UiBridgeProtocol.Action action = UiBridgeProtocol.decodeAction(payload);
            Handler handler = handlers.get(player.getUniqueId());
            if (handler == null || !handler.token().equals(action.token())
                    || !action.inputs().keySet().equals("@close".equals(action.action()) ? Set.of() : handler.inputs())
                    || !sessions.consume(player.getUniqueId(), backend.getServerInfo().getName(), action.token(), action.action())) return;
            handlers.remove(player.getUniqueId(), handler);
            if (!"@close".equals(action.action())) handler.callback().accept(action);
        }
    }

    public String text(Player player, String key) {
        return legacy(player, plugin.config().language().text(key));
    }

    public String legacy(Player player, String text) {
        return LegacyComponentSerializer.legacySection().serialize(MessageFormatter.render(text, player));
    }

    private boolean bedrock(Player player) {
        return FloodgateIntegration.isAvailable() && FloodgateIntegration.isBedrockPlayer(player);
    }
}
