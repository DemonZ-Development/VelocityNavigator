# Contextual Routing Guide

![Contextual lobby routing](headers/contextual-routing-guide.png)

Contextual routing keeps each game mode connected to its own lobby pool. A player leaving BedWars can return to a BedWars lobby, while a SkyWars player goes back to SkyWars, without needing separate lobby commands.

## What Is Contextual Routing?

By default, all players who type `/lobby` are routed from the same global pool (`default_lobbies`). On a network with multiple game modes, you often want players to return to a **game-specific lobby** rather than a generic hub.

Contextual routing lets you define groups of lobbies and map **source servers** to those groups. When a player leaves `bedwars-1` and types `/lobby`, they are routed to the BedWars lobby pool instead of the generic one.

---

## When to Use It

- You have game-mode-specific lobbies (BedWars hub, SkyWars hub, etc.).
- You want players to stay in a game-mode ecosystem when they finish a match.
- Different game modes need different routing algorithms.
- You want fallback chains so players always have somewhere to go.

---

## Basic Setup

### Step 1: Define Your Groups

Each group is a named collection of lobby servers:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-hub-1", "bw-hub-2"]

[routing.contextual.groups.skywars_lobbies]
servers = ["sw-hub-1", "sw-hub-2"]

[routing.contextual.groups.main_hubs]
servers = ["hub-1", "hub-2", "hub-3"]
```

### Step 2: Map Source Servers to Groups

When a player leaves a source server and uses `/lobby`, they are routed to the mapped group:

```toml
[routing.contextual.sources]
"bedwars-1" = "bedwars_lobbies"
"bedwars-2" = "bedwars_lobbies"
"skywars-1" = "skywars_lobbies"
"skywars-2" = "skywars_lobbies"
```

### Step 3: Enable It

```toml
[routing.contextual]
enabled = true
fallback_to_default = true
```

That is the basics. Players leaving BedWars servers are now routed to BedWars lobbies.

---

## Per-Group Selection Mode Override

Each group can override the global `selection_mode`. For example, you might want BedWars lobbies to use `consistent_hash` (so players return to the same lobby they were on) while other groups use the global mode:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-hub-1", "bw-hub-2"]
mode = "consistent_hash"

[routing.contextual.groups.skywars_lobbies]
servers = ["sw-hub-1", "sw-hub-2"]
mode = "power_of_two"
# If mode is omitted, the global selection_mode is used
```

Useful pairings:

- **`consistent_hash`** for groups where you want players to return to the same lobby (inventory cache, party reconnection).
- **`power_of_two`** for high-traffic groups that need fast, even distribution.
- **`weighted_round_robin`** for groups with servers of different capacities.

---

## Fallback Chain Configuration

When all servers in a group are down, the fallback chain determines the ordered list of groups to try next:

```toml
[routing.contextual.fallback_chain]
bedwars_lobbies = ["main_hubs", "skywars_lobbies"]
skywars_lobbies = ["main_hubs"]
```

**How it works**:

1. Player leaves `bedwars-1` → maps to `bedwars_lobbies`.
2. All BedWars lobbies are down → check the fallback chain.
3. Try `main_hubs` → `hub-2` is healthy → route there.

If no fallback group has available servers and `fallback_to_default = true`, the default lobby pool is used as a last resort.

---

## Using LobbyEntry with Groups

Groups support the same LobbyEntry format as `default_lobbies`:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = [
  { server = "bw-hub-1", max_players = 80, weight = 3 },
  { server = "bw-hub-2", max_players = 40, weight = 1 },
]
mode = "weighted_round_robin"
```

This lets you:

- Set **max_players** per server in a group (smaller servers receive fewer players).
- Set **weight** for weighted round-robin distribution.
- Mix plain strings and inline tables.

---

## Real-World Examples

### Example 1: PvP Network

A network with duels, FFA, and a main hub:

```toml
[routing.contextual]
enabled = true
fallback_to_default = true

[routing.contextual.groups.duel_lobbies]
servers = ["duel-hub-1", "duel-hub-2"]
mode = "consistent_hash"  # Players return to their duel hub

[routing.contextual.groups.ffa_lobbies]
servers = [
  { server = "ffa-hub-1", max_players = 200 },
  { server = "ffa-hub-2", max_players = 200 },
]
mode = "power_of_two"

[routing.contextual.sources]
"duel-1" = "duel_lobbies"
"duel-2" = "duel_lobbies"
"ffa-1" = "ffa_lobbies"
"ffa-2" = "ffa_lobbies"

[routing.contextual.fallback_chain]
duel_lobbies = ["ffa_lobbies"]
ffa_lobbies = ["duel_lobbies"]
```

### Example 2: Event Network

A network that runs events with a special event lobby:

```toml
[routing.contextual]
enabled = true
fallback_to_default = true

[routing.contextual.groups.event_lobbies]
servers = ["event-hub"]
mode = "round_robin"  # Only one server, doesn't matter

[routing.contextual.groups.main_hubs]
servers = ["hub-1", "hub-2", "hub-3"]
mode = "least_players"

[routing.contextual.sources]
"event-1" = "event_lobbies"
"event-2" = "event_lobbies"

[routing.contextual.fallback_chain]
event_lobbies = ["main_hubs"]
```

### Example 3: Mixed Network with Weighted Servers

A network where some lobby servers are larger than others:

```toml
[routing.contextual]
enabled = true
fallback_to_default = true

[routing.contextual.groups.priority_lobbies]
servers = [
  { server = "priority-hub-1", max_players = 500, weight = 5 },
  { server = "priority-hub-2", max_players = 300, weight = 3 },
]
mode = "weighted_round_robin"

[routing.contextual.groups.standard_lobbies]
servers = [
  { server = "std-hub-1", max_players = 100, weight = 2 },
  { server = "std-hub-2", max_players = 100, weight = 2 },
  { server = "std-hub-3", max_players = 100, weight = 1 },
]
mode = "power_of_two"

[routing.contextual.sources]
"priority-game-1" = "priority_lobbies"
"standard-game-1" = "standard_lobbies"
"standard-game-2" = "standard_lobbies"
```

---

## Troubleshooting Contextual Routing

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Players always go to default lobbies | `enabled = false` | Set `enabled = true` in `[routing.contextual]` |
| Player from game server goes to wrong lobby | Source server not in `sources` map | Add the source server name (must match `velocity.toml` exactly) |
| "No lobby found" error | All group lobbies offline, `fallback_to_default = false` | Set `fallback_to_default = true` or add a fallback chain |
| Fallback chain not working | Chain references group name that doesn't exist | Verify group names match exactly |

See also: [Configuration Guide](Configuration-Guide) | [Routing Algorithms](Routing-Algorithms) | [Troubleshooting Guide](Troubleshooting-Guide)
