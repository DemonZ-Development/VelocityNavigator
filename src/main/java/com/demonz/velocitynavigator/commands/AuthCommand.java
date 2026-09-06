/*
 * Copyright 2026 DemonZ Development
 * Licensed under the Apache License, Version 2.0.
 */
package com.demonz.velocitynavigator.commands;

import com.demonz.velocitynavigator.VelocityNavigator;
import com.demonz.velocitynavigator.auth.AuthAttemptService;
import com.demonz.velocitynavigator.auth.AuthService;
import com.demonz.velocitynavigator.locale.MessageFormatter;
import com.velocitypowered.api.command.RawCommand;
import com.velocitypowered.api.proxy.Player;

import java.util.Arrays;
import java.util.Map;

public final class AuthCommand implements RawCommand {

    public enum Action {
        REGISTER,
        LOGIN,
        LOGOUT
    }

    private final VelocityNavigator plugin;
    private final Action action;

    public AuthCommand(VelocityNavigator plugin, Action action) {
        this.plugin = plugin;
        this.action = action;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(MessageFormatter.render(plugin.config().messages().playerOnly()));
            return;
        }
        if (!plugin.config().auth().enabled() || plugin.authService() == null) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.disabled"), player));
            return;
        }

        String[] args = splitArguments(invocation.arguments());
        switch (action) {
            case REGISTER -> register(player, args);
            case LOGIN -> login(player, args);
            case LOGOUT -> logout(player);
        }
    }

    private void register(Player player, String[] args) {
        AuthService auth = plugin.authService();
        if (auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.already_registered"), player));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.register_usage"), player));
            return;
        }
        AuthAttemptService.Result result = AuthAttemptService.register(
                auth,
                player.getUniqueId(),
                args[0],
                args.length > 1 ? args[1] : null
        );
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.register_success"), player));
                plugin.completeAuthentication(player);
            }
            case PASSWORD_MISMATCH ->
                    player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.password_mismatch"), player));
            case INVALID_PASSWORD -> player.sendMessage(MessageFormatter.render(
                    plugin.config().language().text("auth.invalid_registration_password"),
                    Map.of("min", Integer.toString(plugin.config().auth().minPasswordLength())),
                    player
            ));
            case ALREADY_REGISTERED ->
                    player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.already_registered"), player));
            case TOO_MANY_ATTEMPTS -> sendTooManyAttempts(player);
            default -> {
            }
        }
    }

    private void login(Player player, String[] args) {
        AuthService auth = plugin.authService();
        if (!auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.not_registered"), player));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.login_usage"), player));
            return;
        }
        AuthAttemptService.Result result = AuthAttemptService.login(auth, player.getUniqueId(), args[0]);
        if (result == AuthAttemptService.Result.SUCCESS) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.login_success"), player));
            plugin.completeAuthentication(player);
        } else if (result == AuthAttemptService.Result.NOT_REGISTERED) {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.not_registered"), player));
        } else if (result == AuthAttemptService.Result.TOO_MANY_ATTEMPTS) {
            sendTooManyAttempts(player);
        } else {
            player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.invalid_credentials"), player));
        }
    }

    private void sendTooManyAttempts(Player player) {
        long seconds = AuthAttemptService.lockoutRemaining(player.getUniqueId()).toSeconds();
        player.sendMessage(MessageFormatter.render(
                plugin.config().language().text("auth.too_many_attempts"),
                Map.of("time", Long.toString(Math.max(1, seconds))),
                player
        ));
    }

    private void logout(Player player) {
        plugin.authService().deauthenticate(player.getUniqueId());
        player.sendMessage(MessageFormatter.render(plugin.config().language().text("auth.logout_success"), player));
        plugin.moveToAuthHolding(player);
    }

    private static String[] splitArguments(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        return Arrays.stream(raw.trim().split("\\s+"))
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }
}
