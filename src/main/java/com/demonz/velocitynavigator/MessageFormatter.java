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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MessageFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            "server", "server_id", "display_name", "description", "time", "reason", "mode", "player", "attempt", "max",
            "max_players", "status", "status_color", "ping", "command", "attempts",
            "page", "pages", "language", "version", "target", "members", "count",
            "message", "position", "size", "state", "host", "port"
    );
    private static final java.util.concurrent.atomic.AtomicBoolean warned = new java.util.concurrent.atomic.AtomicBoolean(false);

    private MessageFormatter() {
    }

    private static String preprocess(String template) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String formatting = "auto";
        String wikiUrl = "https://github.com/DemonZ-Development/VelocityNavigator/wiki";
        org.slf4j.Logger logger = null;

        NavigatorAPI api = NavigatorAPIProvider.get();
        if (api instanceof VelocityNavigator plugin) {
            Config config = plugin.config();
            if (config != null) {
                formatting = config.messages().formatting();
                wikiUrl = config.startup().wikiUrl();
            }
            logger = plugin.logger();
        }

        if ("minimessage".equalsIgnoreCase(formatting)) {
            return template;
        }

        boolean hasLegacy = LegacyColorConverter.hasLegacyCodes(template);
        if (hasLegacy) {
            if ("auto".equalsIgnoreCase(formatting) || "legacy".equalsIgnoreCase(formatting)) {
                if ("auto".equalsIgnoreCase(formatting) && warned.compareAndSet(false, true) && logger != null) {
                    logger.warn("[VelocityNavigator] Legacy color codes detected in config. Consider using MiniMessage format. See: {}/Configuration-Guide#messages", wikiUrl);
                }
                return LegacyColorConverter.convert(template);
            }
        }

        return template;
    }

    public static Component render(String template) {
        return MINI_MESSAGE.deserialize(preprocess(template));
    }

    public static Component render(String template, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return render(template);
        }
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (!ALLOWED_PLACEHOLDERS.contains(entry.getKey())) {
                continue;
            }
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()));
        }
        if (resolvers.isEmpty()) {
            return render(template);
        }
        return MINI_MESSAGE.deserialize(preprocess(template), TagResolver.resolver(resolvers));
    }

    public static Component render(String template, Player player) {
        Component rendered = render(template);
        if (player == null) {
            return rendered;
        }
        NavigatorAPI api = NavigatorAPIProvider.get();
        if (api instanceof VelocityNavigator plugin) {
            Config config = plugin.config();
            if (plugin.bedrockHandler() != null && plugin.bedrockHandler().isBedrockPlayer(player, config)) {
                if (config != null && config.bedrock() != null && config.bedrock().stripAdvancedFormatting()) {
                    return BedrockHandler.formatForBedrock(rendered);
                }
            }
        }
        return rendered;
    }

    public static Component render(String template, Map<String, String> placeholders, Player player) {
        Component rendered = render(template, placeholders);
        if (player == null) {
            return rendered;
        }
        NavigatorAPI api = NavigatorAPIProvider.get();
        if (api instanceof VelocityNavigator plugin) {
            Config config = plugin.config();
            if (plugin.bedrockHandler() != null && plugin.bedrockHandler().isBedrockPlayer(player, config)) {
                if (config != null && config.bedrock() != null && config.bedrock().stripAdvancedFormatting()) {
                    return BedrockHandler.formatForBedrock(rendered);
                }
            }
        }
        return rendered;
    }
}
