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

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NavigatorAPI {

    CompletableFuture<RouteDecision> previewRoute(Player player);

    CompletableFuture<ServerHealthService.ServerStatus> inspectServer(String serverName);

    Config.Routing getRoutingConfig();

    Config.SelectionMode getSelectionMode();

    Map<String, Long> getRoutingDistribution();

    Map<String, Long> getHealthCheckLatencies();

    Map<String, CircuitBreaker.State> getCircuitBreakerStatuses();
}
