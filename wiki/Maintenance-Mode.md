# Maintenance Mode

VelocityNavigator has two ways to stop new routes:

- **Maintenance mode** can cover the whole network or one server and can include a player-facing reason.
- **Drain mode** quietly removes one server from routing while its current players finish normally.

Both are temporary, in-memory states. A proxy restart clears them.

## Overview

| Feature | Maintenance Mode | Drain Mode |
|---|---|---|
| Scope | Network-wide or per-server | Per-server only |
| Custom reason message | Yes | No |
| Managed via | `/vn maintenance ...` or API | `/vn drain`, `/vn undrain` |
| Players already on server | Global: disconnected; per-server: evacuated | Not moved or disconnected |
| Persists across proxy restarts | No (in-memory only) | No (in-memory only) |

Use drain to stop routing new players while current players finish. Use per-server maintenance to evacuate a backend before restarting it. Use global maintenance to disconnect players and close the whole network to new connections.

## Operator Commands

All commands below require `velocitynavigator.admin`.

| Command | What it does |
|---|---|
| `/vn maintenance status` | Shows global and per-server maintenance state |
| `/vn maintenance global on [reason...]` | Enables global maintenance; `/vn maintenance on` is accepted as shorthand |
| `/vn maintenance global off` | Disables global maintenance; `/vn maintenance off` is shorthand |
| `/vn maintenance <server> on [reason...]` | Blocks entry and evacuates one server |
| `/vn maintenance <server> off` | Clears maintenance for one server |

Examples:

```text
/vn maintenance lobby-2 on Updating plugins
/vn maintenance status
/vn maintenance lobby-2 off
```

For a whole-network window:

```text
/vn maintenance on Network upgrade
/vn maintenance off
```

Global maintenance reloads the MOTD configuration, shows the maintenance MOTD, rejects new connections with the configured reason, and disconnects players already online. Per-server maintenance leaves the rest of the lobby pool available and evacuates players from the affected backend through the normal eligibility and routing strategy. Direct server commands and plugin transfers cannot enter a maintained backend either.

Evacuations from a command are processed sequentially, with a fresh routing/health/capacity check per attempt. Failed connections try another eligible destination, bounded by `routing.max_retries` plus the first attempt (at most 16 attempts); each health/transfer wait has a 10-second timeout. A player is disconnected if no eligible destination remains or the attempt budget is exhausted. Evacuation stops if maintenance is cleared, the player disconnects, or the player has already moved elsewhere. Players still awaiting authentication are disconnected instead of being moved past the authentication holding server.

After changing a server state, run `/vn servers` or `/vn maintenance status` to confirm it. Because state is not saved, repeat the command on every proxy in a multi-proxy network.

## Plugin API

`NavigatorAPI` exposes maintenance state for other Velocity plugins to read. Changing maintenance state is intentionally done through the administrator commands.

### Global maintenance

Global maintenance flags the network as under maintenance. All servers are treated as maintained while it is active.

```java
NavigatorAPI api = NavigatorAPIProvider.get();
boolean active = api != null && api.isGlobalMaintenance();
```

When global maintenance is active, `isServerInMaintenance(anyServerName)` returns `true` for all servers.

### Per-server maintenance

You can inspect individual server state without affecting other servers.

```java
boolean inMaintenance = api.isServerInMaintenance("survival-1");
Set<String> servers = api.getMaintenanceServers();
```

Server IDs are normalized to lowercase. The public API currently reports whether maintenance is active and which per-server flags are set; it does not expose reason strings or mutation methods.

### Reason Messages

Operator commands can attach a reason. Reasons are resolved in this order:

1. If global maintenance is active, the global reason is returned for all servers.
2. If a per-server reason was set, that reason is returned.
3. If the server is in maintenance but has no custom reason, the default `"Server is under maintenance"` is returned.

### NavigatorAPI reference

| Method | Return type | Description |
|---|---|---|
| `isGlobalMaintenance()` | `boolean` | Whether global maintenance is active |
| `isServerInMaintenance(String)` | `boolean` | Whether a server is in maintenance (global or per-server) |
| `getMaintenanceServers()` | `Set<String>` | Set of server IDs currently in per-server maintenance |

## Drain mode

Drain mode is a routing-level exclusion managed through admin commands. `RoutePlanner` skips drained servers during route selection. Drain mode leaves the server's online status and connected players unaffected.

### Commands

| Command | Description |
|---|---|
| `/vn drain <server>` | Drains the specified server. No new players will be routed to it. |
| `/vn undrain <server>` | Removes drain state. The server becomes eligible for routing again. |
| `/vn drain status` | Lists all currently drained servers. |

All drain commands require the `velocitynavigator.admin` permission.

### How draining works

When a server is drained:

1. `DrainService.drain(serverName)` sets the drain flag.
2. `RoutePlanner.filterOnlineCandidates()` checks `DrainService.isDrained()` for each candidate server.
3. Drained servers are excluded from the candidate list before the selection algorithm runs.
4. Players already connected to the drained server are not moved.
5. Running `/vn servers` shows the drain state alongside health and capacity information.

```java
// Programmatic drain
plugin.drainService().drain("lobby-1");

// Check drain state
boolean drained = plugin.drainService().isDrained("lobby-1");

// List all drained servers
Map<String, Boolean> state = plugin.drainService().drainState();

// Remove drain
plugin.drainService().undrain("lobby-1");

// Clear all drain states
plugin.drainService().clear();
```

Server names are normalized to lowercase for consistent matching.

## Drain Mode vs Maintenance Mode

Both systems prevent routing to a server. They differ in purpose and interface:

- Drain mode handles planned server maintenance, deployments, and temporary removal. You manage it via `/vn drain` and `/vn undrain` commands. It appears in `/vn servers` and `/vn status`.
- Maintenance mode provides operator commands and API access, supports reasons, and can cover one server or the whole network.

A server can be drained and in maintenance simultaneously. Either condition prevents routing.

## Backend lifecycle states

Backend lifecycle states form a routing filter configured in `navigator.toml`. They control which server states permit routing.

```toml
[backend_states]
enabled = true
allowed = ["LOBBY", "WAITING", "AVAILABLE"]
allow_unknown = true
```

| Setting | Default | Description |
|---|---|---|
| `enabled` | `true` | Whether state-based filtering is active |
| `allowed` | `["LOBBY", "WAITING", "AVAILABLE"]` | States that permit routing |
| `allow_unknown` | `true` | Whether servers with an unreported state receive traffic |

Lifecycle states are checked in `RoutePlanner.filterOnlineCandidates()` and in `ServerHealthService.isRoutingStateAllowed()`. A server reporting a state absent from the `allowed` list (such as `MAINTENANCE` or `SHUTTING_DOWN`) is excluded from routing, independent of drain or maintenance mode.

Set the server's state to `MAINTENANCE` in your backend plugin to signal maintenance through the backend bridge. If `MAINTENANCE` is missing from the `allowed` list, the server is excluded from routing.

## Player experience

Player behavior depends on the mode:

- **Drain leaves current players online; per-server maintenance evacuates them.**
- **Drained servers receive no new Navigator routes; maintained servers reject direct transfers too.**
- **Global maintenance disconnects online players and rejects new connections with the configured reason.**
- **Selector visibility.** Drained or maintained servers appear in inventory and chat selectors based on selector configuration. `/vn servers` displays their status.
- **Fallback behavior.** The `lobby.no_server_strategy` setting defines behavior when all servers are drained or in maintenance. Players receive a disconnect message by default.

```toml
[lobby]
no_server_strategy = "disconnect"
no_server_message = "<red>No lobby servers are currently available. Please try again later.</red>"
```

## Multi-proxy considerations

Maintenance and drain states reside in memory on each proxy instance. When running multiple Velocity proxies:

- **You must run drain and maintenance commands on each proxy** unless Redis synchronization is enabled.
- With **Redis enabled**, drain state and health data synchronize across proxies. Maintenance state (`MaintenanceService`) remains local to each proxy.
- Backend bridge state updates propagate to all proxies through the backend bridge channel.

For Redis networks, draining a server on one proxy propagates the state. For maintenance mode, set the state on each proxy individually or use backend bridge lifecycle states.

## Troubleshooting

**Server shows as available but players are not routed to it.**

Run `/vn servers` and check the drain and health columns. If the server is drained, run `/vn undrain <server>`. If health shows offline or circuit-open, check `/vn health` for diagnostics.

**Global maintenance is active but should not be.**

Run `/vn maintenance off`. Maintenance state is in-memory only, so restarting the proxy also clears it. If an external plugin uses the API, make sure it is not enabling maintenance again during startup.

**Per-server maintenance reason is wrong.**

Run `/vn maintenance <server> on <new reason>`. API users can call `setServerMaintenance()` again instead.

**Drained server still appears in the selector.**

The selector reads health and player data, not drain state. Drain state affects routing. Use `/vn servers` to confirm drain status.

**Backend lifecycle state excludes a server unexpectedly.**

Check the `allowed` list in `[backend_states]` in `navigator.toml`. If the server reports a missing state, it is excluded. Add the state to the list or set `allow_unknown = true`.

## Related pages

- [Health Checks and Circuit Breakers](Health-Checks-and-Circuit-Breakers)
- [Backend Lifecycle States](Backend-Lifecycle-States)
- [Commands and Permissions](Commands-and-Permissions)
- [Redis and Multi-Proxy](Redis-and-Multi-Proxy)
- [Operations Runbook](Operations-Runbook)
