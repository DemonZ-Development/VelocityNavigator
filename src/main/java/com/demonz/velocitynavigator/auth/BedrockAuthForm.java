/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.auth;

import com.demonz.velocitynavigator.FloodgateIntegration;
import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.velocitypowered.api.proxy.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BedrockAuthForm {

    private BedrockAuthForm() {
    }

    public static boolean open(Player player, VelocityNavigator plugin) {
        if (player == null || plugin == null || plugin.config() == null || plugin.authService() == null
                || !plugin.config().auth().enabled() || !plugin.config().auth().bedrockFormEnabled()
                || !plugin.isAuthenticationPending(player) || !FloodgateIntegration.isAvailable()
                || !FloodgateIntegration.isBedrockPlayer(player)) {
            return false;
        }
        try {
            return plugin.authService().isRegistered(player.getUniqueId())
                    ? openLogin(player, plugin)
                    : openRegistration(player, plugin);
        } catch (LinkageError | RuntimeException error) {
            plugin.logger().warn("[VelocityNavigator] Could not open the Bedrock authentication form for {}: {}",
                    player.getUsername(), error.getMessage());
            return false;
        }
    }

    private static boolean openRegistration(Player player, VelocityNavigator plugin) {
        CustomForm form = CustomForm.builder()
                .title(text(plugin, "auth.form.register_title"))
                .input(text(plugin, "auth.form.password_visible"), text(plugin, "auth.form.password_placeholder"))
                .input(text(plugin, "auth.form.repeat_password"), text(plugin, "auth.form.password_placeholder"))
                .validResultHandler(response -> {
                    AuthAttemptService.Result result = AuthAttemptService.register(
                            plugin.authService(),
                            player.getUniqueId(),
                            response.asInput(0),
                            response.asInput(1)
                    );
                    handleRegistrationResult(player, plugin, result);
                })
                .closedOrInvalidResultHandler(() -> sendFallback(player, plugin))
                .build();
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private static boolean openLogin(Player player, VelocityNavigator plugin) {
        CustomForm form = CustomForm.builder()
                .title(text(plugin, "auth.form.login_title"))
                .input(text(plugin, "auth.form.password_visible"), text(plugin, "auth.form.password_placeholder"))
                .validResultHandler(response -> {
                    AuthAttemptService.Result result = AuthAttemptService.login(
                            plugin.authService(),
                            player.getUniqueId(),
                            response.asInput(0)
                    );
                    handleLoginResult(player, plugin, result);
                })
                .closedOrInvalidResultHandler(() -> sendFallback(player, plugin))
                .build();
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), form);
    }

    private static void handleRegistrationResult(
            Player player,
            VelocityNavigator plugin,
            AuthAttemptService.Result result
    ) {
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.register_success"), player));
                plugin.completeAuthentication(player);
            }
            case PASSWORD_MISMATCH -> retry(player, plugin, "auth.password_mismatch");
            case INVALID_PASSWORD -> retry(
                    player,
                    plugin,
                    "auth.invalid_registration_password",
                    Map.of("min", Integer.toString(plugin.config().auth().minPasswordLength()))
            );
            case ALREADY_REGISTERED -> retry(player, plugin, "auth.already_registered");
            case TOO_MANY_ATTEMPTS -> sendTooManyAttempts(player, plugin);
            default -> retry(player, plugin, "auth.form.try_again");
        }
    }

    private static void handleLoginResult(
            Player player,
            VelocityNavigator plugin,
            AuthAttemptService.Result result
    ) {
        if (result == AuthAttemptService.Result.SUCCESS) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.login_success"), player));
            plugin.completeAuthentication(player);
        } else if (result == AuthAttemptService.Result.NOT_REGISTERED) {
            retry(player, plugin, "auth.not_registered");
        } else if (result == AuthAttemptService.Result.TOO_MANY_ATTEMPTS) {
            sendTooManyAttempts(player, plugin);
        } else {
            retry(player, plugin, "auth.invalid_credentials");
        }
    }

    private static void sendTooManyAttempts(Player player, VelocityNavigator plugin) {
        if (!player.isActive()) {
            return;
        }
        long seconds = AuthAttemptService.lockoutRemaining(player.getUniqueId()).toSeconds();
        player.sendMessage(MessageFormatter.render(
                plugin.config().language().text("auth.too_many_attempts"),
                Map.of("time", Long.toString(Math.max(1, seconds))),
                player
        ));
    }

    private static void retry(Player player, VelocityNavigator plugin, String messageKey) {
        retry(player, plugin, messageKey, Map.of());
    }

    private static void retry(
            Player player,
            VelocityNavigator plugin,
            String messageKey,
            Map<String, String> placeholders
    ) {
        if (!player.isActive()) {
            return;
        }
        player.sendMessage(MessageFormatter.render(
                plugin.config().language().text(messageKey), placeholders, player));
        plugin.server().getScheduler().buildTask(plugin, () -> open(player, plugin))
                .delay(1, TimeUnit.SECONDS)
                .schedule();
    }

    private static void sendFallback(Player player, VelocityNavigator plugin) {
        if (player.isActive() && plugin.isAuthenticationPending(player)) {
            player.sendMessage(MessageFormatter.render(
                    plugin.config().language().text("auth.form.command_fallback"), player));
        }
    }

    private static String text(VelocityNavigator plugin, String key) {
        return plugin.config().language().text(key);
    }
}
