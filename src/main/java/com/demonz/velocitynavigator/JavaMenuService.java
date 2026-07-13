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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;

import java.util.List;
import java.util.Map;

public final class JavaMenuService {

    private JavaMenuService() {
    }

    public static void showLobbyMenu(Player player, VelocityNavigator plugin, RouteDecision decision) {
        Config config = plugin.config();
        List<String> candidates = decision.onlineCandidates();
        if (candidates == null || candidates.isEmpty()) {
            player.sendMessage(MessageFormatter.render(config.messages().noLobbyFound(), Map.of("reason", config.language().text("reasons.no_online_lobbies"), "player", player.getUsername()), player));
            return;
        }

        player.sendMessage(MessageFormatter.render(config.routing().chatMenuHeader()));
        String token = plugin.createMenuToken(player, candidates);

        for (MenuServerInfo info : MenuServerInfoResolver.resolve(plugin, config, candidates)) {
            String serverName = info.server();
            String lineText = replacePlaceholders(config.routing().chatMenuFormat(), info);
            String tooltipText = replacePlaceholders(config.routing().chatMenuTooltip(), info);

            Component lineComponent = MessageFormatter.render(lineText, player)
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player clickedPlayer
                                && clickedPlayer.getUniqueId().equals(player.getUniqueId())) {
                            plugin.server().getCommandManager().executeAsync(clickedPlayer,
                                    config.commands().primary() + " connect " + serverName + " " + token);
                        }
                    }))
                    .hoverEvent(HoverEvent.showText(MessageFormatter.render(tooltipText, player)));

            player.sendMessage(lineComponent);
        }
    }

    private static String replacePlaceholders(String template, MenuServerInfo info) {
        String result = template;
        for (Map.Entry<String, String> entry : info.placeholders().entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
