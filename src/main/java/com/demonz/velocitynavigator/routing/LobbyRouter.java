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
package com.demonz.velocitynavigator.routing;
import com.demonz.velocitynavigator.ServerHealthStatus;
import com.demonz.velocitynavigator.config.Config;
import com.demonz.velocitynavigator.health.ServerHealthService;

import com.velocitypowered.api.proxy.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.net.InetAddress;

public final class LobbyRouter {

    private final ServerHealthService healthService;
    private final RoutePlanner routePlanner;
    private volatile GeoRoutingService geoRoutingService;

    public LobbyRouter(ServerHealthService healthService, RoutePlanner routePlanner) {
        this.healthService = healthService;
        this.routePlanner = routePlanner;
    }

    public void setGeoRoutingService(GeoRoutingService geoRoutingService) {
        this.geoRoutingService = geoRoutingService;
    }

    public CompletableFuture<RouteDecision> preview(Player player, Config config) {
        String sourceServer = player.getCurrentServer()
                .map(current -> current.getServerInfo().getName())
                .orElse("");
        CompletableFuture<String> countryFuture = CompletableFuture.completedFuture(null);
        GeoRoutingService geo = geoRoutingService;
        if (geo != null && config.geoRouting().enabled() && mayUseGeoRouting(config)) {
            InetAddress address = player.getRemoteAddress().getAddress();
            countryFuture = geo.lookupCountryAsync(address);
        }
        Set<String> targets = routePlanner.inspectionTargets(sourceServer, config);
        CompletableFuture<Map<String, ServerHealthStatus>> healthFuture =
                healthService.inspectServers(targets, config.healthChecks());
        return healthFuture.thenCombine(countryFuture, (statuses, country) ->
                routePlanner.plan(sourceServer, config, onlinePlayers(statuses), player.getUniqueId(), country));
    }

    public CompletableFuture<RouteDecision> preview(String sourceServer, Config config) {
        return preview(sourceServer, config, null);
    }

    public CompletableFuture<RouteDecision> preview(String sourceServer, Config config, UUID playerId) {
        Set<String> targets = routePlanner.inspectionTargets(sourceServer, config);
        return healthService.inspectServers(targets, config.healthChecks())
                .thenApply(statuses -> routePlanner.plan(sourceServer, config, onlinePlayers(statuses), playerId));
    }

    private boolean mayUseGeoRouting(Config config) {
        return RoutePlanner.mayUseGeoRouting(config);
    }

    private Map<String, Integer> onlinePlayers(Map<String, ServerHealthStatus> statuses) {
        Map<String, Integer> online = new LinkedHashMap<>();
        for (Map.Entry<String, ServerHealthStatus> entry : statuses.entrySet()) {
            if (entry.getValue().exists() && entry.getValue().online()) {
                online.put(entry.getKey(), entry.getValue().playersConnected());
            }
        }
        return online;
    }
}
