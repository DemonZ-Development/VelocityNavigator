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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LobbyRouter {

    private final ServerHealthService healthService;
    private final RoutePlanner routePlanner;

    public LobbyRouter(ServerHealthService healthService, RoutePlanner routePlanner) {
        this.healthService = healthService;
        this.routePlanner = routePlanner;
    }

    public CompletableFuture<RouteDecision> preview(Player player, Config config) {
        String sourceServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName())
                .orElse("");
        return preview(sourceServer, config, player.getUniqueId());
    }

    public CompletableFuture<RouteDecision> preview(String sourceServer, Config config) {
        return preview(sourceServer, config, null);
    }

    public CompletableFuture<RouteDecision> preview(String sourceServer, Config config, UUID playerId) {
        Set<String> targets = routePlanner.inspectionTargets(sourceServer, config);
        return healthService.inspectServers(targets, config.healthChecks())
                .thenApply(statuses -> routePlanner.plan(sourceServer, config, onlinePlayers(statuses), playerId));
    }

    private Map<String, Integer> onlinePlayers(Map<String, ServerHealthService.ServerStatus> statuses) {
        Map<String, Integer> online = new LinkedHashMap<>();
        for (Map.Entry<String, ServerHealthService.ServerStatus> entry : statuses.entrySet()) {
            if (entry.getValue().exists() && entry.getValue().online()) {
                online.put(entry.getKey(), entry.getValue().playersConnected());
            }
        }
        return online;
    }
}
