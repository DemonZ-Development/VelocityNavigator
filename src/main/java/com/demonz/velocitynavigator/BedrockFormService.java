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
package com.demonz.velocitynavigator;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class BedrockFormService {

    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern AMP_COLOR_CODE = Pattern.compile("(?i)&[0-9a-fk-or]");
    private static final Pattern SECTION_COLOR_CODE = Pattern.compile("(?i)§[0-9a-fk-or]");

    private BedrockFormService() {
    }

    public static void showLobbySelectionForm(Player player, VelocityNavigator plugin, RouteDecision decision) {
        Config config = plugin.config();
        GuiConfig.BedrockMenu menu = plugin.guiConfig().bedrock();
        if (!menu.enabled()) {
            JavaMenuService.showLobbyMenu(player, plugin, decision);
            return;
        }
        List<String> candidates = decision.onlineCandidates() == null ? new ArrayList<>() : new ArrayList<>(decision.onlineCandidates());
        if (candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", config.language().text("reasons.no_online_lobbies"), "player", player.getUsername()), player));
            return;
        }
        if ("name".equals(menu.sortMode())) candidates.sort(String.CASE_INSENSITIVE_ORDER);
        if ("players".equals(menu.sortMode())) candidates.sort(Comparator.comparingInt(name -> plugin.server().getServer(name).map(server -> server.getPlayersConnected().size()).orElse(Integer.MAX_VALUE)));
        if (candidates.size() > menu.maxButtons()) candidates = new ArrayList<>(candidates.subList(0, menu.maxButtons()));
        List<String> formCandidates = List.copyOf(candidates);

        try {
            Object builder = createSimpleFormBuilder(player, plugin, config, formCandidates);
            Class<?> formBuilderClass = Class.forName("org.geysermc.cumulus.form.util.FormBuilder");
            Class<?> responseClass = Class.forName("org.geysermc.cumulus.response.SimpleFormResponse");
            Method clickedButtonIdMethod = responseClass.getMethod("clickedButtonId");
            formBuilderClass.getMethod("validResultHandler", Consumer.class).invoke(builder, (Consumer<Object>) response -> {
                try {
                    Object clicked = clickedButtonIdMethod.invoke(response);
                    if (clicked instanceof Number clickedIndex) {
                        int idx = clickedIndex.intValue();
                        if (idx >= 0 && idx < formCandidates.size()) {
                            String targetServer = formCandidates.get(idx);
                            connectValidatedSelection(player, plugin, config, targetServer);
                        }
                    }
                } catch (ReflectiveOperationException exception) {
                    plugin.logger().warn("[VelocityNavigator] Could not read Bedrock form response for {}: {}",
                            player.getUsername(), exception.getMessage());
                }
            });

            sendFloodgateForm(player.getUniqueId(), builder, formBuilderClass);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.logger().warn("[VelocityNavigator] Bedrock form GUI is unavailable. Falling back to chat menu for {}: {}",
                    player.getUsername(), exception.getMessage());
            if (menu.fallbackToChat()) JavaMenuService.showLobbyMenu(player, plugin, decision);
            else player.sendMessage(MessageFormatter.render(config.language().text("menus.bedrock.unavailable"), player));
        }
    }

    private static Object createSimpleFormBuilder(Player player, VelocityNavigator plugin, Config config, List<String> candidates)
            throws ReflectiveOperationException {
        Class<?> simpleFormClass = Class.forName("org.geysermc.cumulus.form.SimpleForm");
        Class<?> simpleFormBuilderClass = Class.forName("org.geysermc.cumulus.form.SimpleForm$Builder");
        Class<?> formBuilderClass = Class.forName("org.geysermc.cumulus.form.util.FormBuilder");

        Object builder = simpleFormClass.getMethod("builder").invoke(null);
        GuiConfig.BedrockMenu menu = plugin.guiConfig().bedrock();
        String title = menu.title().isBlank() ? config.bedrock().guiTitle() : menu.title();
        String content = menu.content().isBlank() ? config.bedrock().guiContent() : menu.content();
        String buttonFormat = menu.buttonFormat().isBlank() ? config.bedrock().guiButtonFormat() : menu.buttonFormat();
        formBuilderClass.getMethod("title", String.class).invoke(builder,
                stripFormattingCodesIfRequested(title, config));
        simpleFormBuilderClass.getMethod("content", String.class).invoke(builder,
                stripFormattingCodesIfRequested(content, config));

        for (String serverName : candidates) {
            int currentPlayers = 0;
            Optional<RegisteredServer> registered = plugin.server().getServer(serverName);
            if (registered.isPresent()) {
                currentPlayers = registered.get().getPlayersConnected().size();
            }
            Config.LobbyEntry lobby = plugin.lobbyEntry(serverName).orElse(new Config.LobbyEntry(serverName, Config.LobbyEntry.UNCAPPED, Config.LobbyEntry.DEFAULT_WEIGHT));
            String maxPlayers = lobby.maxPlayers() == Config.LobbyEntry.UNCAPPED ? "∞" : String.valueOf(lobby.maxPlayers());
            String ping = String.valueOf(plugin.healthService().getLatencies().getOrDefault(serverName.toLowerCase(java.util.Locale.ROOT), -1L));
            String status = plugin.healthService().getBackendStates().getOrDefault(serverName.toLowerCase(java.util.Locale.ROOT), "HEALTHY");
            String buttonText = buttonFormat
                    .replace("{server}", serverName)
                    .replace("{players}", menu.showPlayers() ? String.valueOf(currentPlayers) : "")
                    .replace("{max_players}", menu.showMaxPlayers() ? maxPlayers : "")
                    .replace("{ping}", menu.showPing() ? ping : "")
                    .replace("{status}", menu.showStatus() ? status : "");

            simpleFormBuilderClass.getMethod("button", String.class).invoke(builder,
                    stripFormattingCodesIfRequested(buttonText, config));
        }
        return builder;
    }

    private static void sendFloodgateForm(UUID playerId, Object builder, Class<?> formBuilderClass)
            throws ReflectiveOperationException {
        Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        Object floodgateApi = floodgateApiClass.getMethod("getInstance").invoke(null);
        if (floodgateApi == null) {
            throw new IllegalStateException("Floodgate API is not initialized");
        }
        floodgateApiClass.getMethod("sendForm", UUID.class, formBuilderClass).invoke(floodgateApi, playerId, builder);
    }

    private static void connectValidatedSelection(Player player, VelocityNavigator plugin, Config config, String targetServer) {
        plugin.previewRoute(player).thenAccept(decision -> {
            boolean stillAvailable = decision.onlineCandidates().stream()
                    .anyMatch(candidate -> candidate.equalsIgnoreCase(targetServer));
            if (!stillAvailable) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                        Map.of("reason", config.language().text("reasons.selection_unavailable"), "player", player.getUsername()), player));
                return;
            }

            boolean sameServer = player.getCurrentServer()
                    .map(current -> current.getServerInfo().getName().equalsIgnoreCase(targetServer))
                    .orElse(false);
            if (sameServer && !config.commands().reconnectIfSameServer()) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().alreadyConnected(),
                        Map.of("server", targetServer, "player", player.getUsername()), player));
                return;
            }

            Optional<RegisteredServer> target = plugin.server().getServer(targetServer);
            if (target.isEmpty()) {
                plugin.cooldowns().clear(player.getUniqueId());
                player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                        Map.of("reason", config.language().text("reasons.selection_unregistered"), "player", player.getUsername()), player));
                return;
            }

            player.sendMessage(MessageFormatter.render(config.messages().connecting(),
                    Map.of("server", targetServer, "player", player.getUsername()), player));

            RouteDecision formDecision = ConnectionWorkflow.withTargetFirst(decision, targetServer, "bedrock_gui");
            ConnectionWorkflow.connectWithRetry(plugin, player, config, target.get(), formDecision, "bedrock_gui");
        }).exceptionally(throwable -> {
            plugin.cooldowns().clear(player.getUniqueId());
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(),
                    Map.of("reason", config.language().text("reasons.selection_validation_failed"), "player", player.getUsername()), player));
            plugin.logger().error("[VelocityNavigator] Failed to validate Bedrock lobby selection for {}", player.getUsername(), throwable);
            return null;
        });
    }

    private static String stripFormattingCodesIfRequested(String text, Config config) {
        if (text == null) {
            return "";
        }
        if (config.bedrock().stripAdvancedFormatting()) {
            String stripped = MINI_MESSAGE_TAG.matcher(text).replaceAll("");
            stripped = AMP_COLOR_CODE.matcher(stripped).replaceAll("");
            stripped = SECTION_COLOR_CODE.matcher(stripped).replaceAll("");
            return stripped;
        }
        try {
            String normalized = LegacyColorConverter.hasLegacyCodes(text) ? LegacyColorConverter.convert(text) : text;
            return LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(normalized));
        } catch (RuntimeException error) {
            return text;
        }
    }
}
