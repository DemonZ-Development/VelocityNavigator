/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.auth;

import com.demonz.velocitynavigator.FloodgateIntegration;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.common.MenuBridgeProtocol;
import com.demonz.velocitynavigator.common.UiBridgeProtocol;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class JavaAuthUi {
    private JavaAuthUi() {}

    public static boolean open(Player player, VelocityNavigator plugin) {
        if (!plugin.isAuthenticationPending(player) || plugin.authService() == null
                || (FloodgateIntegration.isAvailable() && FloodgateIntegration.isBedrockPlayer(player))) return false;
        boolean register = !plugin.authService().isRegistered(player.getUniqueId());
        List<UiBridgeProtocol.Input> inputs = new ArrayList<>();
        inputs.add(new UiBridgeProtocol.Input("password", plugin.uiService().text(player, "auth.form.password_visible"), 128));
        if (register) inputs.add(new UiBridgeProtocol.Input("repeat", plugin.uiService().text(player, "auth.form.repeat_password"), 128));
        String title = plugin.uiService().text(player, register ? "auth.form.register_title" : "auth.form.login_title");
        String actionLabel = plugin.uiService().text(player, register ? "auth.dialog.register" : "auth.dialog.login");
        boolean opened = plugin.uiService().show(player, title, plugin.uiService().text(player, "auth.dialog.visible_warning"),
                List.of(new UiBridgeProtocol.Button("submit", actionLabel, "", "TRIPWIRE_HOOK")), inputs,
                plugin.guiConfig().javaUi().authentication(), true, register, action -> {
                    if (!plugin.isAuthenticationPending(player)) return;
                    plugin.handleBackendAuthRequest(player, new MenuBridgeProtocol.AuthRequest(register,
                            action.inputs().getOrDefault("password", ""), action.inputs().get("repeat")));
                    if (plugin.isAuthenticationPending(player)) {
                        plugin.server().getScheduler().buildTask(plugin, () -> {
                            if (player.isActive() && plugin.isAuthenticationPending(player)) open(player, plugin);
                        }).delay(1, TimeUnit.SECONDS).schedule();
                    }
                });
        if (opened) player.sendMessage(MessageFormatter.render(plugin.config().language().text(
                register ? "auth.register_prompt" : "auth.login_prompt"), player));
        return opened;
    }
}
