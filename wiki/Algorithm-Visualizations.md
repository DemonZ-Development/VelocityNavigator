# Algorithm Visualizations

![Routing algorithm examples](headers/algorithm-visualizations.png)

The examples below show the personality of each routing mode. They are not benchmarks; they are a quick way to see whether an algorithm behaves the way you expect.

## Legend

Each bar represents the number of players on a server. The goal is to see how evenly (or intentionally unevenly) each algorithm spreads the load.

```
█ = ~5 players
▌ = ~2-3 players
```

---

## Low Load — 5 Players, 3 Servers

### `least_players`
```
lobby-1: ██   (2)
lobby-2: ██   (2)
lobby-3: █    (1)
```

### `power_of_two`
```
lobby-1: ██   (2)
lobby-2: ██   (2)
lobby-3: █    (1)
```

### `round_robin`
```
lobby-1: ██   (2)
lobby-2: ██   (2)
lobby-3: █    (1)
```

### `random`
```
lobby-1: ███  (3)
lobby-2: █    (1)
lobby-3: █    (1)
```
* Higher variance at low counts — expected behavior.

### `weighted_round_robin` (weights: 3, 2, 1)
```
lobby-1: ███  (3)
lobby-2: █    (1)
lobby-3: █    (1)
```

### `least_connections`
```
lobby-1: ██   (2)
lobby-2: ██   (2)
lobby-3: █    (1)
```

### `consistent_hash`
```
lobby-1: ██   (2)
lobby-2: ██   (2)
lobby-3: █    (1)
```
* Depends on UUID hash; shown here as a typical distribution.

---

## Medium Load — 30 Players, 5 Servers

### `least_players`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
* Near-perfect even distribution.

### `power_of_two`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
* Very close to `least_players` — at this scale, practically identical.

### `round_robin`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
* Strictly even by design.

### `random`
```
lobby-1: ████████ (8)
lobby-2: █████▌   (5)
lobby-3: ██████   (6)
lobby-4: ████▌    (4)
lobby-5: █████▌   (7)
```
* Some variance — evens out further as the player count grows.

### `weighted_round_robin` (weights: 5, 4, 3, 2, 1)
```
lobby-1: ██████████   (10)
lobby-2: ████████     (8)
lobby-3: ██████       (6)
lobby-4: ████         (4)
lobby-5: ██           (2)
```
* Proportional to weight.

### `least_connections`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
* EMA smoothing produces an even distribution under steady load.

### `consistent_hash`
```
lobby-1: ██████   (7)
lobby-2: ██████   (6)
lobby-3: █████    (5)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
* Minor variance from hash ring distribution.

---

## High Load — 100 Players, 10 Servers

### `least_players`
```
srv-01: ██████████ (10)
srv-02: ██████████ (10)
srv-03: ██████████ (10)
srv-04: ██████████ (10)
srv-05: ██████████ (10)
srv-06: ██████████ (10)
srv-07: ██████████ (10)
srv-08: ██████████ (10)
srv-09: ██████████ (10)
srv-10: ██████████ (10)
```

### `power_of_two`
```
srv-01: ██████████ (10)
srv-02: ██████████ (10)
srv-03: █████████▌ (11)
srv-04: █████████  (9)
srv-05: ██████████ (10)
srv-06: ██████████ (10)
srv-07: █████████▌ (11)
srv-08: █████████  (9)
srv-09: ██████████ (10)
srv-10: ██████████ (10)
```
* ±1 deviation — performs well at scale.

### `round_robin`
```
srv-01: ██████████ (10)
srv-02: ██████████ (10)
srv-03: ██████████ (10)
srv-04: ██████████ (10)
srv-05: ██████████ (10)
srv-06: ██████████ (10)
srv-07: ██████████ (10)
srv-08: ██████████ (10)
srv-09: ██████████ (10)
srv-10: ██████████ (10)
```

### `random`
```
srv-01: ███████████ (12)
srv-02: █████████▌  (9)
srv-03: ██████████  (10)
srv-04: ████████▌   (8)
srv-05: ███████████ (11)
srv-06: ██████████  (10)
srv-07: █████████▌  (9)
srv-08: ██████████  (10)
srv-09: ███████████ (11)
srv-10: ████████▌   (10)
```
* Variance shrinks with scale — law of large numbers.

### `weighted_round_robin` (weights: 5, 5, 3, 3, 3, 2, 2, 2, 1, 1)
```
srv-01: ██████████████████  (19)
srv-02: ██████████████████  (19)
srv-03: ███████████         (11)
srv-04: ███████████         (11)
srv-05: ███████████         (11)
srv-06: ████████            (8)
srv-07: ████████            (8)
srv-08: ████████            (8)
srv-09: ████                (4)
srv-10: ████                (4)
```

### `least_connections`
```
srv-01: ██████████ (10)
srv-02: ██████████ (10)
srv-03: ██████████ (10)
srv-04: ██████████ (10)
srv-05: ██████████ (10)
srv-06: ██████████ (10)
srv-07: ██████████ (10)
srv-08: ██████████ (10)
srv-09: ██████████ (10)
srv-10: ██████████ (10)
```

### `consistent_hash`
```
srv-01: █████████▌ (11)
srv-02: ██████████ (10)
srv-03: █████████  (9)
srv-04: ██████████ (10)
srv-05: █████████▌ (11)
srv-06: █████████  (9)
srv-07: ██████████ (10)
srv-08: ██████████ (10)
srv-09: █████████▌ (10)
srv-10: █████████  (10)
```
* Hash ring produces ±1 deviation across 10 servers.

---

## Latency Mode — Proxy-to-Backend Measurement

`latency` does not produce an even distribution chart. It ranks the healthy candidates by the ping measured from the Velocity proxy:

```
lobby-east:  25 ms  ← selected
lobby-west:  70 ms
lobby-eu:   110 ms
```

Every player routed by that proxy sees the same current ranking. This is useful when backend network delay differs, but it is not player-specific geographic routing. If the 25 ms server becomes full, drained, unhealthy, circuit-open, or lifecycle-disallowed, the next eligible candidate can be selected.

---

## Key Takeaways

| Scenario | Best Algorithm |
|----------|---------------|
| Want perfect balance, don't care about cost | `least_players` |
| Want near-perfect balance, low cost | `power_of_two` |
| Servers have different capacities | `weighted_round_robin` |
| Need sticky sessions | `consistent_hash` |
| Bursty traffic patterns | `least_connections` |
| Prefer the lowest proxy-to-backend ping | `latency` |
| Just testing / strict fairness | `round_robin` |
| Very large server pool (50+) | `random` |

See [Routing Algorithms](Routing-Algorithms) for detailed explanations of each mode.
