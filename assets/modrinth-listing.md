# VelocityNavigator

![VelocityNavigator banner](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/hero-banner.png?v=7)

Velocity's normal `try` list is a fallback list, not a load balancer. If the first lobby is online, nearly everyone lands there. VelocityNavigator fixes that by choosing a suitable lobby for every initial join and every `/lobby` request.

It works well on a small network with two lobbies, but it also has the controls needed for larger setups: health checks, drain mode, capacity limits, fallback pools, sticky routing, parties, queues, Redis sync, and clear admin commands.

> Get the current JAR with the **Download** button on this Modrinth page. The same JAR runs on Velocity and, when needed, on Paper or Spigot.

## Better lobby routing

![A player being routed to a healthy lobby](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/marketplace/01-smart-routing.png?v=2)

Choose the routing style that fits your network:

- `least_players` for a simple, even spread
- `power_of_two` for a fast all-round choice on busy networks
- `weighted_round_robin` when some lobbies are larger than others
- `consistent_hash` when players should usually return to the same lobby
- `latency` when proxy-to-backend ping matters
- `round_robin`, `random`, and `least_connections` for more specific setups

Before a server is selected, VelocityNavigator can skip lobbies that are offline, full, drained for maintenance, temporarily failing, or in the wrong lifecycle state. Contextual groups can also send BedWars players back to a BedWars lobby while SkyWars players return to their own hub.

Initial joins use the same routing rules, so balancing starts before a player types a command.

## Real menus for Java and Bedrock

### Java Edition inventory

![VelocityNavigator Java inventory running in Minecraft](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/java-inventory-selector.png?v=1)

Java players can use a paginated chest-style inventory rendered by the optional Paper/Spigot backend bridge.

### Bedrock form

![VelocityNavigator Bedrock lobby form running in Minecraft](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/bedrock-selector.png?v=2)

Geyser/Floodgate players can use a native Bedrock form. Networks without either GUI can keep the clickable chat selector.

Both screenshots come from running Minecraft clients. The layout lives in `gui.toml`; you can change rows, materials, filler items, server icons, fixed slots, titles, lore, navigation buttons, refresh timing, and unavailable-server styling. Menu text supports MiniMessage, classic color codes, and RGB colors.

## One JAR, optional backend install

![Velocity proxy and optional backend bridge](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/marketplace/02-universal-jar.png?v=2)

Install VelocityNavigator on the Velocity proxy for routing, commands, health checks, parties, queues, and metrics.

Put the same JAR on a Paper or Spigot backend only if that server needs to open the Java inventory menu or announce itself through Redis. A proxy-only installation is completely valid, and Bedrock forms do not require the backend bridge.

Startup logs clearly say whether the JAR is running in **VELOCITY PROXY** mode or **BACKEND GUI BRIDGE** mode.

## Useful extras when your network needs them

![Optional parties, queues, and Redis](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/marketplace/05-optional-systems.png?v=2)

Every larger feature has its own enable switch.

### Parties

Players can invite, accept, deny, kick, leave, disband, view party members, and use private party chat. When leader follow is enabled, online members follow the leader to another server.

### Capacity queue

If every lobby in a pool is full, players can wait in a lightweight queue with live position updates. They are connected automatically when a slot opens. An optional holding server can be used during the wait.

### Redis sync

Multiple Velocity proxies can share health, circuit-breaker, affinity, and backend-state information through Redis. Backends can also register and unregister themselves with a shared secret and an allowed-host list.

Party membership and queue positions stay on the proxy where they were created, so multi-proxy networks should keep those players pinned to the same proxy.

## Easier day-to-day administration

![VelocityNavigator operations view](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/marketplace/06-operations.png?v=2)

- Drain a lobby before maintenance without kicking players already there
- Check health, player counts, latency, capacity, and circuit state
- See why a player or server was excluded from a route
- Add or remove Velocity servers with validation and dry-run support
- Export Prometheus metrics and create the included Grafana dashboard
- Use the optional local HTML dashboard for a quick browser view
- Keep player affinity across proxy restarts

The most useful first checks are `/vn health`, `/vn servers`, `/vn bridge status`, and `/vn config validate`.

## Quick setup

1. Download `VelocityNavigator-4.3.0.jar` with this page's **Download** button.
2. Put it in the Velocity proxy's `plugins` folder.
3. Start the proxy once to create the configuration files.
4. Add your lobby names to `navigator.toml` or use `/vn server add lobby`.
5. Run `/vn config validate`, then `/vn reload`.
6. Optionally install the same JAR on Paper/Spigot backends for the Java inventory selector.

A small setup can be as short as:

```toml
[routing]
selection_mode = "power_of_two"
balance_initial_join = true
default_lobbies = ["lobby-1", "lobby-2"]
```

The server names must already exist in Velocity's `velocity.toml`.

## Main commands

| Command | What it does |
|---|---|
| `/lobby`, `/hub`, `/spawn` | Route the player or open the configured selector |
| `/party ...`, `/p ...` | Party management and private party chat |
| `/queue`, `/queue leave` | View or leave the capacity queue |
| `/vn health` | Show a quick network health summary |
| `/vn servers` | View each lobby's current state |
| `/vn bridge status` | Check which backends have the optional bridge |
| `/vn redis status`, `/vn redis test` | Check Redis configuration and connectivity |
| `/vn debug player <name>` | Explain a player's current routing result |
| `/vn drain <server>`, `/vn undrain <server>` | Take a server out of or back into rotation |
| `/vn server add game|lobby ...` | Add a normal game backend or an active routed lobby |
| `/vn server dry-run ...`, `/vn server list`, `/vn server remove <name>` | Preview, inspect, or remove managed server entries |
| `/vn config validate` | Check the active configuration for common mistakes |
| `/vn reload` | Reload proxy-side configuration files |

`velocitynavigator.use` controls the player lobby command. `velocitynavigator.admin` controls the admin commands.

## Compatibility

- Velocity 3.x
- Java 17 or newer
- Minecraft versions supported by your Velocity build
- Optional Paper/Spigot bridge built against the 1.16.5 API without version-specific NMS
- Geyser and Floodgate for native Bedrock forms

VelocityNavigator does not run on BungeeCord or Waterfall. Redis Cluster and Sentinel discovery are not included. GeoIP routing is not available in 4.3.0.

## Updating from 4.2

Keep a copy of your current JAR and plugin folder, replace the JAR, then start one proxy first. Version 4.3 updates the configuration layout and keeps a backup of the previous file. Run `/vn config validate` and try `/lobby` before updating the rest of the network.

## Help

Use this Modrinth page for downloads and updates. For setup help or bug reports, join [DemonZ Development on Discord](https://discord.com/invite/GYsTt96ypf).

Velocity-side anonymous usage statistics are provided through [bStats](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341) and can be disabled in the configuration. Backend metrics are configured separately.

---

[Nexeu Hosting](https://nexeu.zip/) supports the project. VelocityNavigator does not require a specific hosting provider.
