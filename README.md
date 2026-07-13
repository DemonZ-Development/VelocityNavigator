<p align="center">
  <img src="assets/hero-banner.png?v=6" alt="VelocityNavigator health-aware lobby routing banner" width="800">
</p>

<h1 align="center">VelocityNavigator</h1>

<p align="center">
  <strong>Lobby routing, without the guesswork.</strong>
  <br>
  <em>Built by <a href="https://github.com/DemonZ-Development">DemonZ Development</a></em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-4.3.0-cyan?style=for-the-badge" alt="Version">
  <img src="https://img.shields.io/badge/channel-stable-38d6e0?style=for-the-badge" alt="Stable release channel">
  <img src="https://img.shields.io/badge/platform-Velocity_3.x-blue?style=for-the-badge" alt="Platform">
  <img src="https://img.shields.io/badge/java-17+-orange?style=for-the-badge" alt="Java">
  <img src="https://img.shields.io/badge/license-Apache_2.0-green?style=for-the-badge" alt="License">
</p>

VelocityNavigator balances initial joins and lobby commands across healthy Velocity backends. It combines eight routing modes with capacity checks, circuit breakers, drain state, contextual pools, persistent affinity, Java and Bedrock selectors, and operator-focused diagnostics.

<p align="center">
  <img src="assets/marketplace/01-smart-routing.png?v=1" alt="Player routed to a healthy lobby" width="800">
</p>

The JAR always runs on Velocity. Install the same JAR on Paper or Spigot only when a backend needs to render the Java inventory selector or publish dynamic registration events.

---

## What's New in v4.3

| Feature | Description |
|---------|-------------|
| **Configurable language packs** | Explicit `en`, `ru`, `es`, `fr`, `de`, `pt_br`, and `zh_cn` packs plus arbitrary custom language codes; no automatic locale detection |
| **Separate `gui.toml`** | Inventory rows, materials, controls, refresh timing, fillers, fixed slots, and per-server name/lore overrides |
| **Java inventory lobby selector** | Paginated chest-style selector with unavailable indicators, automatic refresh, secure tokens, and chat fallback |
| **Exponential backoff with jitter** | Connection retries now wait progressively longer with a small random jitter to avoid thundering-herd reconnects |
| **Persistent player affinity** | Unexpired sticky-session mappings are saved to disk and restored across proxy restarts |
| **`/vn health` command** | Consolidated one-shot diagnostics: routing mode, lobby count, circuit-breaker states, drained servers, cache sizes, and affinity entry count in a single screen |
| **HTML operations dashboard** | Separate HTTP server on its own port serving a live lobby table, routing distribution chart, affinity count, config summary, and join/leave-since-start counters. Disabled by default; authenticated via bearer token |
| **Modern plugin descriptor** | `velocity-plugin.json` descriptor added alongside the existing `@Plugin` annotation |
| **Advanced proxy systems** | Configurable parties, full-pool queues, secure Redis state sync/dynamic registration, and MOTD lifecycle-state routing |
| **Managed server operations** | Transactional `/vn server add game|lobby`, dry-run, removal, backups, and validation without manual TOML editing |
| **Universal backend bridge** | One JAR for Velocity and the Paper/Spigot inventory bridge, with separate backend configuration and separate Bukkit bStats wiring |

See the [CHANGELOG](CHANGELOG.md) for the full list of changes.

---

## What's New in v4.2

| Feature | Description |
|---------|-------------|
| **Bedrock/Geyser support** | Routing for Bedrock players with Floodgate UUID mapping and format stripping |
| **`/vn servers` dashboard** | Paginated diagnostics view with circuit breaker, drain, and player capacity per lobby |
| **Configurable dashboard colors** | Custom MiniMessage/RGB status colors for healthy, draining, open, and offline states |
| **Legacy color code converter** | Auto-detects and converts `&`/`§` codes to MiniMessage with `auto`, `legacy`, or `minimessage` modes |
| **Levenshtein config validation** | Typo auto-correction with distance-based suggestions for all enum-styled TOML keys |
| **Self-documenting config** | Every TOML key gets inline comments and wiki anchor URLs on write or migration |
| **First-run welcome and upgrades digest** | Console welcome dashboard on fresh install, release notes digest on upgrades |
| **Periodic update checker** | Scheduled update checks with exponential 429 backoff (scales up to 4 hours) |
| **Empty lobby fallbacks** | Configurable `disconnect` or `fallback_server` strategy when all lobbies are unreachable |
| **Permission default changed** | `/lobby` command now defaults to `"none"` for immediate out-of-the-box adoption |

### v4.0 Features Included

| Feature | Description |
|---------|-------------|
| **4 new selection algorithms** | `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash` — 7 total |
| **Circuit breaker** | Automatic server failure detection with CLOSED → OPEN → HALF_OPEN state machine |
| **Player affinity** | Sticky sessions with configurable stickiness probability |
| **Server drain mode** | Take servers offline for maintenance with `/vn drain` |
| **Connection retry with fallback** | Automatically retry on connection failure with configurable attempts |
| **Routing metrics API** | Monitor distribution, health check latencies, and circuit breaker states |
| **Per-group selection mode** | Contextual groups can override the global selection algorithm |
| **Fallback priority chain** | Ordered fallback groups when a group's servers are unavailable |
| **Graceful degradation** | Fall back to random selection when all health checks fail |
| **Geo routing placeholder** | Compatibility config remains, but MaxMind/GeoIP routing is intentionally deferred beyond 4.3.0 |
| **Admin update notifications** | In-game notification for admins when updates are available |

  See [Migration Guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Migration-Guide-v3-to-v4) for upgrade instructions.

---

## Feature Highlights

| Feature | Description |
|---------|-------------|
| **8 routing algorithms** | `least_players` \| `round_robin` \| `random` \| `power_of_two` \| `weighted_round_robin` \| `least_connections` \| `consistent_hash` \| `latency` |
| **Initial join balancing** | Players are load-balanced the moment they connect, not only when they run `/lobby` |
| **Contextual routing** | Route players to game-specific lobbies based on the server they are leaving |
| **Circuit breaker** | Automatic failure detection — unhealthy servers are skipped until they recover |
| **Async health checks** | Ping candidate lobbies before routing with configurable timeout and caching |
| **Ping coalescing** | Multiple simultaneous requests share one ping — no network storms |
| **Player affinity** | Sticky sessions so players tend to return to the same lobby |
| **Server drain mode** | `/vn drain` and `/vn undrain` for maintenance |
| **bStats telemetry** | Anonymous usage metrics via [bStats](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341) |
| **Self-documenting config** | `navigator.toml` generates with inline docs explaining every setting |
| **Admin suite** | `/vn status`, `/vn health`, `/vn bridge status`, `/vn reload`, `/vn debug`, `/vn drain`, `/vn updatecheck` with tab-completion |
| **Native parties** | `/party` lifecycle commands, `/p` chat, and automatic online-member follow when the leader changes server |
| **Virtual capacity queue** | Full pools place players in a live position queue and connect them as soon as slots open |
| **Multi-proxy Redis sync** | Dynamic backend registration plus circuit-breaker, health-cache, and affinity synchronization |
| **Backend lifecycle states** | MOTD markers such as `[STATE:IN_GAME]` prevent routing to backends in disallowed states |

---

## Installation

1. Download `VelocityNavigator-4.3.0.jar` from the [VelocityNavigator Modrinth page](https://modrinth.com/plugin/velocitynavigator)
2. Place it in your Velocity proxy's `plugins/` folder
3. Start (or restart) the proxy
4. Edit `navigator.toml` for systems, `messages.toml` for language, `gui.toml` for Java/Bedrock menus, and `servers.toml` for command-managed lobbies
5. Optional: place the same JAR in each backend Paper/Spigot server's `plugins/` folder to enable the Java inventory selector

The universal JAR prints `VELOCITY PROXY mode` or `BACKEND GUI BRIDGE mode` at startup.

**Requirements**: Velocity 3.x • Java 17+ • Minecraft 1.7.2 through 26.2 via Velocity • Paper/Spigot 1.16.5+ for the optional backend inventory bridge

The optional backend bridge is built against Spigot API 1.16.5 and uses no version-specific NMS. Proxy-only installations do not need the JAR on game or lobby backends.

---

## Quick Configuration

```toml
[routing]
# 8 modes: least_players, round_robin, random, power_of_two,
#          weighted_round_robin, least_connections, consistent_hash, latency
selection_mode = "power_of_two"

# Balance players when they first connect (not just /lobby)
balance_initial_join = true

# Your lobby servers (plain strings or inline tables)
default_lobbies = [
  { server = "lobby-1", max_players = 100, weight = 2 },
  { server = "lobby-2", max_players = 100, weight = 2 },
  "lobby-3",
]

# Set true to show a selector instead of immediately auto-routing.
use_menu_for_lobby = true

[routing.java_menu]
type = "inventory"       # inventory or chat
fallback_to_chat = true  # safe fallback if a backend lacks the bridge

# Circuit breaker: skip unhealthy servers automatically
[circuit_breaker]
enabled = true
failure_threshold = 3
cooldown_seconds = 30

[party]
enabled = true
follow_leader = true

[queue]
enabled = true
# Existing backend created and designed by you; VN does not generate its world.
holding_server = "holding"

[redis]
enabled = false
host = "127.0.0.1"

[backend_states]
enabled = true
allowed = ["LOBBY", "WAITING", "AVAILABLE"]
allow_unknown = true
```

The queue holding server is a separate backend that you register in `velocity.toml`. VelocityNavigator routes waiting players to it but does not create its world, so you can use a void room, parkour map, NPC lobby, or any other waiting experience. Keep it outside every routed lobby pool and size its own player limit for the expected queue.

See the [Configuration Guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Configuration-Guide) for all settings.

### Language selection

Set `language` at the top of `messages.toml`, then restart or run `/vn reload`:

```toml
language = "ru"
```

Built-ins are `en`, `ru`, `es`, `fr`, `de`, `pt_br`, and `zh_cn`. Selecting a built-in replaces the active message file. Any other code is treated as a custom language and preserves values for editing. Player locale is never detected automatically.

Native speakers are warmly invited to improve existing translations or contribute new languages. See the [Language Packs guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Language-Packs) for custom-pack instructions and contribution details.

Configurable text supports MiniMessage, classic `&`/`§` codes, `&#RRGGBB`, and Bungee-style hex colors.

---

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/lobby` | `velocitynavigator.use` | Send to the best available lobby |
| `/hub`, `/spawn` | `velocitynavigator.use` | Aliases for `/lobby` |
| `/party invite|accept|deny|kick|leave|disband|status` | none | Manage or inspect a party on the current proxy node |
| `/party chat <message>` or `/p <message>` | none | Send a private party chat message |
| `/queue [leave]` | none | Show or leave the current capacity queue |
| `/vn server add game <name> <host:port>` | `velocitynavigator.admin` | Register and persist a game backend in `velocity.toml` only |
| `/vn server add lobby <name> <host:port> [group] [max_players] [weight]` | `velocitynavigator.admin` | Persist in Velocity and add to the plugin lobby pool |
| `/vn server dry-run <game\|lobby> <name> <host:port> ...` | `velocitynavigator.admin` | Validate a managed server operation without writing files |
| `/vn server remove <name>` | `velocitynavigator.admin` | Remove a server from managed Velocity/plugin configuration |
| `/vn server list` | `velocitynavigator.admin` | List plugin-managed lobby entries and the Velocity config path |
| `/vn config validate` | `velocitynavigator.admin` | Check command collisions, Redis/HTTP safety, queue holding server, and managed files |
| `/vn reload` | `velocitynavigator.admin` | Hot-reload navigator.toml, messages.toml, gui.toml, and servers.toml |
| `/vn status` | `velocitynavigator.admin` | View runtime status, distribution, circuit breakers |
| `/vn health` | `velocitynavigator.admin` | Consolidated one-shot diagnostics screen |
| `/vn bridge status` | `velocitynavigator.admin` | Show detected backend GUI bridges |
| `/vn redis status\|test` | `velocitynavigator.admin` | Inspect Redis counters or test endpoint, TLS, authentication, and PING |
| `/vn debug player <name>` | `velocitynavigator.admin` | Preview routing decision |
| `/vn debug server <name>` | `velocitynavigator.admin` | Inspect server health and circuit breaker |
| `/vn drain <server>` | `velocitynavigator.admin` | Drain a server (no new players) |
| `/vn undrain <server>` | `velocitynavigator.admin` | Remove drain flag |
| `/vn drain status` | `velocitynavigator.admin` | List drained servers |
| `/vn servers` | `velocitynavigator.admin` | Show paginated lobby server status dashboard |
| `/vn setup grafana` | `velocitynavigator.admin` | Generate the bundled Grafana dashboard JSON |
| `/vn version` | `velocitynavigator.admin` | Show the installed plugin and runtime version |
| `/vn help` | `velocitynavigator.admin` | Show the admin command reference |
| `/vn updatecheck` | `velocitynavigator.admin` | Manually check for updates |

---

## Permissions

| Permission | Default | Description |
|-----------|---------|-------------|
| `velocitynavigator.use` | `none*` | Use the lobby command — default changed to `"none"` in v4.1.0 |
| `velocitynavigator.admin` | `false` | Use all `/vn` admin commands |
| `velocitynavigator.bypass.cooldown` | `false` | Bypass command cooldown |
| `velocitynavigator.bypasscooldown` | `false` | Legacy — still works, use `bypass.cooldown` instead |

---

## HTML Operations Dashboard

VelocityNavigator 4.3 ships an optional HTML dashboard for live operational visibility. It runs on a separate HTTP port from the Prometheus exporter and is disabled by default.

When enabled, the dashboard serves:

- A live lobby table with player counts, capacity, and circuit-breaker state
- A routing distribution chart
- The current number of affinity records
- A summary of the active `navigator.toml` settings
- Live join and leave counters

When `bearer_token` is set, the page prompts for it and authenticates API requests through the `Authorization: Bearer <token>` header. A blank token disables authentication and is suitable only for a loopback listener.

See the [HTML Dashboard guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/HTML-Dashboard) for the `[dashboard]` block. `127.0.0.1` in the example is universal loopback, not a public IP. Hosting-panel users should use their own allocated dashboard port and the bind address required by their provider or container.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Quick Start Guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Quick-Start-Guide) | Get running in under 10 minutes |
| [Configuration Guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Configuration-Guide) | Every `navigator.toml`, `messages.toml`, `gui.toml`, and `servers.toml` setting explained |
| [Commands and Permissions](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Commands-and-Permissions) | Complete player, admin, party, queue, managed-server, and integration command reference |
| [Routing Algorithms](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Routing-Algorithms) | Deep dive into all 8 routing modes |
| [Algorithm Visualizations](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Algorithm-Visualizations) | Distribution patterns at different load levels |
| [Contextual Routing Guide](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Contextual-Routing-Guide) | Per-game-mode lobby routing |
| [Retries and Fallbacks](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Retries-and-Fallbacks) | Connection retries, degradation, queues, and last-resort routing |
| [Operations Runbook](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Operations-Runbook) | Drain, circuit breaker, troubleshooting |
| [Advanced Proxy Systems](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Advanced-Proxy-Systems) | Parties, queue, Redis, managed servers, lifecycle states, and backend bridge |
| [Migration v3 → v4](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Migration-Guide-v3-to-v4) | Step-by-step upgrade guide |
| [Troubleshooting](https://github.com/DemonZ-Development/VelocityNavigator/wiki/Troubleshooting-Guide) | Symptom-based debugging |
| [FAQ](https://github.com/DemonZ-Development/VelocityNavigator/wiki/FAQ) | Common questions answered |
| [Changelog](CHANGELOG.md) | Full release history |
| [Contributing](CONTRIBUTING.md) | How to contribute |

---

## Building from Source

```bash
git clone https://github.com/DemonZ-Development/VelocityNavigator.git
cd VelocityNavigator
mvn clean verify
# JAR output: target/VelocityNavigator-4.3.0.jar
```

---

## Stats

[![bStats](https://bstats.org/signatures/velocity/Velocity%20Navigator.svg)](https://bstats.org/plugin/velocity/Velocity%20Navigator/28341)

---

<p align="center">
  <img src="assets/plugin-icon.png?v=6" alt="VelocityNavigator Icon" width="64">
  <br>
  <strong>Built by <a href="https://github.com/DemonZ-Development">DemonZ Development</a></strong>
</p>
