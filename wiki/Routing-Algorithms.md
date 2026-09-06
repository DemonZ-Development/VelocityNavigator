# Routing Algorithms

![VelocityNavigator routing algorithms](headers/routing-algorithms.png)

You select an algorithm for your network. You configure health checks, drain mode, capacity limits, and fallback rules to filter unsuitable servers before selection.

## Comparison table

| Algorithm | Distribution Quality | CPU Cost | Requires Health Data | Sticky Sessions | Best For |
|-----------|---------------------|----------|---------------------|-----------------|----------|
| `least_players` | ★★★★★ | Medium | Yes | No | Small-to-medium networks |
| `power_of_two` | ★★★★☆ | Low | Yes | No | Medium networks, recommended default |
| `round_robin` | ★★★☆☆ | Very Low | No | No | Testing, strict fairness |
| `random` | ★★★☆☆ | Very Low | No | No | Large-scale networks |
| `weighted_round_robin` | ★★★★☆ | Low | No | No | Unequal server capacity |
| `least_connections` | ★★★★★ | Medium | Yes | No | Bursty traffic, large networks |
| `consistent_hash` | ★★★☆☆ | Low | No | Yes | Session affinity, party routing |
| `latency` | ★★★★★ | Medium | Yes | No | Lowest proxy-to-backend ping |
| `geo_distance` | ★★★★☆ | Medium | Yes | No | Geographic closest lobby |

---

## 1. Least Players (`least_players`)

You route players to the server with the fewest connected players.

**Complexity**: O(n). You scan all candidates each selection.

**When to use**: Use for most networks. You configure `least_players` by default. You configure `power_of_two` for production (see below).

**When not to use**: Avoid for server pools larger than 50 where scanning adds latency. Avoid when you need deterministic player-to-server mapping.

**Example (10 players over 3 servers)**:
```
lobby-1: ████ (4 players)
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```

---

## 2. Power of Two Choices (`power_of_two`)

You pick two random candidates, then route the player to the one with fewer players.

**Complexity**: O(1). You examine two servers.

**When to use**: Use for medium networks (4-10 servers). You get optimal distribution with lower cost than `least_players`.

**When not to use**: Avoid for two-server networks. Avoid when you require exact distribution.

**Example (10 players over 3 servers)**:
```
lobby-1: ████ (4 players)
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```
You achieve better scaling than `least_players`.

---

## 3. Round Robin (`round_robin`)

You cycle through servers in strict order using an atomic counter.

**Complexity**: O(1). You increment an atomic counter.

**When to use**: Use for testing, benchmarking, or deterministic rotation with identical servers.

**When not to use**: Avoid for production networks where servers have different capacities. Avoid for burst traffic that causes imbalance.

**Example (10 players over 3 servers)**:
```
lobby-1: ████ (4 players)  ← players 1, 4, 7, 10
lobby-2: ███  (3 players)  ← players 2, 5, 8
lobby-3: ███  (3 players)  ← players 3, 6, 9
```

---

## 4. Random (`random`)

You assign each player a random lobby.

**Complexity**: O(1). You make one random selection.

**When to use**: Use for networks with over 50 servers. You see variance even out at scale. You avoid coordination overhead between proxies.

**When not to use**: Avoid for small networks where random variance produces imbalance. Avoid when you need deterministic routing.

**Example (10 players over 3 servers)**:
```
lobby-1: █████ (5 players)  ← random variance
lobby-2: ███  (3 players)
lobby-3: ██   (2 players)
```
Variance evens out as player count grows.

---

## 5. Weighted Round Robin (`weighted_round_robin`)

You cycle through servers using proportional weights. You configure higher weights to route more players to larger servers.

**Complexity**: O(n) per round cycle, O(1) amortized per selection.

**When to use**: Use for servers with different capacities. You set `weight` higher on larger servers.

**When not to use**: Avoid for identical servers (use `round_robin` or `power_of_two` instead).

**Example (10 players over 3 servers, weights: lobby-1=3, lobby-2=2, lobby-3=1)**:
```
lobby-1: █████ (5 players)  ← weight 3
lobby-2: ███  (3 players)  ← weight 2
lobby-3: ██   (2 players)  ← weight 1
```

Configure weights with the inline table format:

```toml
default_lobbies = [
  { server = "lobby-1", weight = 3 },
  { server = "lobby-2", weight = 2 },
  { server = "lobby-3", weight = 1 },
]
```

---

## 6. Least Connections (`least_connections`)

You select the server with the lowest EMA of active connections and connection rate.

**Complexity**: O(n). You scan all candidates and compute EMA.

**When to use**: Use for networks with burst traffic. You smooth momentary spikes with EMA. You get more stability than `least_players` during traffic surges.

**When not to use**: Avoid for stable networks where `least_players` or `power_of_two` perform well.

**Example (10 players over 3 servers, with burst traffic)**:
```
lobby-1: ███  (3 players)  ← EMA low, receives next player
lobby-2: ████ (4 players)  ← EMA elevated from recent burst
lobby-3: ███  (3 players)  ← EMA low
```

---

## 7. Consistent Hash (`consistent_hash`)

You hash the player UUID onto a consistent hash ring. Players return to the same server unless you remove it.

**Complexity**: O(log n). You look up the ring.

**When to use**: Use for sticky sessions. You configure this for party routing, inventory caching, or any system requiring player-server affinity.

**When not to use**: Avoid when you require even distribution. Avoid when you do not need sticky sessions.

**Example (10 players over 3 servers)**:
```
lobby-1: ████ (4 players)  ← hash ring assignment
lobby-2: ███  (3 players)
lobby-3: ███  (3 players)
```
The same player returns to the same server. Adding or removing a server only remaps a fraction of players.

**v4.3+ performance note**: You configure the consistent hash ring with a thread-local `MessageDigest`. You eliminate per-lookup allocation and improve throughput.

---

## 8. Latency (`latency`)

You select the server with the lowest ping latency recorded during the health check.

**Complexity**: O(n). You scan all candidates for the minimum ping.

**When to use**: Use when one Velocity proxy reaches some backends faster than others. You get the lowest measured proxy-to-backend network delay.

**When not to use**: Avoid when you need even player distribution. Avoid when you need per-player geographic routing. You measure latency from the proxy.

**Example (10 players over 3 servers, pings: lobby-east=25ms, lobby-west=70ms, lobby-eu=110ms)**:

You route selections to `lobby-east` while its latency stays at 25 ms and it passes route filters. You still remove it with capacity, drain, circuit, and health rules.

You do not use GeoIP with `latency`. You do not inspect player IP addresses.

---

## 9. Geo Distance (`geo_distance`)

You route players to the lobby in the closest country or continent. You resolve countries using [MaxMind GeoLite2](Geo-Routing) with IP-API HTTP fallback.

**Complexity**: O(n + k). You perform one IP lookup and scan candidate country affinities.

**When to use**: Use for global networks with regional lobbies. You choose this when proxy-to-backend latency differs by region.

**When not to use**: Avoid for single-region networks. You fall back to the global `selection_mode` when you cannot resolve the country.

Configure the country affinity list per lobby:

```toml
[geo_routing]
enabled = true
fallback_mode = "least_players"

[geo_routing.affinity_countries]
"lobby-eu" = ["DE", "FR", "NL"]
"lobby-us" = ["US", "CA", "MX"]
```

See [Geo Routing](Geo-Routing) for the full setup, including the file-database path and the IP-API cache lifetime.

---

## Health check integration

You configure algorithms to use live player counts from `RegisteredServer.getPlayersConnected()`. You use the most recent proxy-to-backend health-check latency in `latency` mode.

You filter online and offline servers with the health check cache. You exclude offline servers from the pool. You configure health checks to run on an interval.

You exclude a server when its circuit breaker opens after failures, even with an unexpired health-check cache.

---

## Graceful degradation

You can fall back to a degradation mode when candidate servers fail health checks. You ignore health status and select from configured lobbies to prevent errors.

```toml
[degradation]
enabled = true
mode = "random"
```

See [Configuration Guide](Configuration-Guide) for details.

---

## Per-group overrides

You override the global selection mode with contextual routing groups. You can configure main lobbies with `power_of_two` and BedWars lobbies with `consistent_hash`:

```toml
[routing.contextual.groups.bedwars_lobbies]
servers = ["bw-1", "bw-2"]
mode = "consistent_hash"
```

See [Contextual Routing Guide](Contextual-Routing-Guide) for the full tutorial.
