# NavigatorAPI

VelocityNavigator exposes a public API so other Velocity plugins can inspect routing decisions, health status, and plugin metadata without depending on internal implementation classes.

## Getting the API

```java
import com.demonz.velocitynavigator.NavigatorAPI;
import com.demonz.velocitynavigator.NavigatorAPIProvider;

NavigatorAPI api = NavigatorAPIProvider.get();
if (api == null) {
    // VelocityNavigator is not loaded
    return;
}
```

The provider returns `null` when VelocityNavigator is absent. Register your plugin as a `@Dependency` in your `@Plugin` annotation to ensure it loads after VelocityNavigator.

## Accessor methods

These methods provide direct access to plugin state without casting to `VelocityNavigator`.

| Method | Returns | Description |
|---|---|---|
| `pluginVersion()` | `String` | VelocityNavigator version string (e.g. `4.5.0`) |
| `server()` | `ProxyServer` | The Velocity proxy instance |
| `logger()` | `Logger` | SLF4J logger for the plugin |
| `dataDirectory()` | `Path` | Plugin data directory (`plugins/velocitynavigator/`) |
| `config()` | `Config` | Currently active parsed configuration (never null after startup) |
| `bedrockHandler()` | `BedrockHandler` | Geyser/Floodgate integration handler |

## Routing inspection

| Method | Returns | Description |
|---|---|---|
| `previewRoute(Player)` | `CompletableFuture<RouteDecision>` | Simulates the routing decision for a player without moving them |
| `getRoutingConfig()` | `Config.Routing` | Current routing configuration section |
| `getSelectionMode()` | `Config.SelectionMode` | Active selection mode (`least_players`, `round_robin`, `random`, `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash`, `latency`, `geo`) |
| `getRoutingDistribution()` | `Map<String, Long>` | Per-server routing counts (resets every 60 seconds) |

## Health & circuit breakers

| Method | Returns | Description |
|---|---|---|
| `getHealthCheckLatencies()` | `Map<String, Long>` | Last known ping latency per server (milliseconds) |
| `getCircuitBreakerStatuses()` | `Map<String, CircuitBreakerState>` | Current circuit breaker state per server (`CLOSED`, `OPEN`, `HALF_OPEN`) |
| `inspectServer(String)` | `CompletableFuture<ServerHealthStatus>` | Detailed health snapshot for one server |

## Maintenance mode

| Method | Returns | Description |
|---|---|---|
| `isGlobalMaintenance()` | `boolean` | Whether network-wide maintenance is active |
| `isServerInMaintenance(String)` | `boolean` | Whether a specific server is in maintenance |
| `getMaintenanceServers()` | `Set<String>` | Set of server names currently in maintenance |

## Example: Reading active configuration

```java
NavigatorAPI api = NavigatorAPIProvider.get();
if (api != null) {
    Config cfg = api.config();
    int maxRetries = cfg.routing().maxRetries();
    List<String> lobbies = cfg.routing().defaultLobbies();
    getLogger().info("Lobby pool has {} servers, {} retries max",
        lobbies.size(), maxRetries);
}
```

## Example: Inspecting routing for a player

```java
NavigatorAPI api = NavigatorAPIProvider.get();
api.previewRoute(player).thenAccept(decision -> {
    getLogger().info("Would route {} to {} (reason: {})",
        player.getUsername(),
        decision.selectedServer(),
        decision.reason());
});
```

## Thread safety

All accessor methods are safe to call from any thread. `previewRoute()` and `inspectServer()` return `CompletableFuture` and do not block the calling thread.

## Version compatibility

The API was expanded in 4.5.0 with the six accessor methods. Existing methods from 4.0.0+ remain unchanged. Plugins compiled against the 4.5.0 interface will work with any 4.5.x release.
