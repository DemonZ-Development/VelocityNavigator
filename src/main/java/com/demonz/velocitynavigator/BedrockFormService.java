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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
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
        List<String> candidates = decision.onlineCandidates() == null
                ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(decision.onlineCandidates()));
        if (candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", config.language().text("reasons.no_online_lobbies"), "player", player.getUsername()), player));
            return;
        }
        candidates = new ArrayList<>(orderedCandidates(
                candidates,
                plugin.guiConfig(),
                menu,
                name -> plugin.server().getServer(name)
                        .map(server -> server.getPlayersConnected().size())
                        .orElse(Integer.MAX_VALUE)
        ));
        if (candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", config.language().text("reasons.no_online_lobbies"), "player", player.getUsername()), player));
            return;
        }
        if (candidates.size() > menu.maxButtons()) candidates = new ArrayList<>(candidates.subList(0, menu.maxButtons()));
        List<String> formCandidates = List.copyOf(candidates);
        List<MenuServerInfo> formServers = MenuServerInfoResolver.resolve(plugin, config, formCandidates);

        try {
            Object builder = createSimpleFormBuilder(player, plugin, config, formServers);
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

    private static Object createSimpleFormBuilder(Player player, VelocityNavigator plugin, Config config, List<MenuServerInfo> servers)
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

        for (MenuServerInfo info : servers) {
            String buttonText = formatButtonText(
                    buttonFormat,
                    info.server(),
                    info.displayName(),
                    info.description(),
                    menu.showPlayers() ? String.valueOf(info.players()) : "",
                    menu.showMaxPlayers() ? ("-".equals(info.maxPlayers()) ? "∞" : info.maxPlayers()) : "",
                    menu.showPing() ? info.ping() : "",
                    menu.showStatus() ? info.status() : "",
                    menu.showStatus() ? info.statusColor() : ""
            );

            simpleFormBuilderClass.getMethod("button", String.class).invoke(builder,
                    stripFormattingCodesIfRequested(buttonText, config));
        }
        return builder;
    }

    static String formatButtonText(String template, String serverId, String displayName, String players,
                                   String maxPlayers, String ping, String status) {
        return formatButtonText(template, serverId, displayName, "", players, maxPlayers, ping, status);
    }

    static String formatButtonText(String template, String serverId, String displayName, String description,
                                   String players, String maxPlayers, String ping, String status) {
        return formatButtonText(template, serverId, displayName, description, players, maxPlayers, ping, status, "");
    }

    static String formatButtonText(String template, String serverId, String displayName, String description,
                                   String players, String maxPlayers, String ping, String status,
                                   String statusColor) {
        return MenuTemplateFormatter.replace(template, Map.of(
                "server", displayName,
                "display_name", displayName,
                "server_id", serverId,
                "description", description,
                "players", players,
                "max_players", maxPlayers,
                "ping", ping,
                "status", status,
                "status_color", statusColor
        ));
    }

    static List<String> orderedCandidates(List<String> candidates, GuiConfig gui, GuiConfig.BedrockMenu menu,
                                          ToIntFunction<String> playerCount) {
        List<String> ordered = candidates == null
                ? new ArrayList<>()
                : new ArrayList<>(new LinkedHashSet<>(candidates));
        if ("name".equals(menu.sortMode())) {
            ordered.sort(displayNameComparator(gui));
        } else if ("players".equals(menu.sortMode())) {
            ordered.sort(Comparator.comparingInt(playerCount));
        }
        return gui.visibleServers(ordered);
    }

    static Comparator<String> displayNameComparator(GuiConfig gui) {
        return Comparator.comparing(
                (String name) -> gui.displayName(name),
                String.CASE_INSENSITIVE_ORDER
        ).thenComparing(String.CASE_INSENSITIVE_ORDER);
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
