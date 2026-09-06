# Algorithm Visualizations

![Routing algorithm examples](headers/algorithm-visualizations.png)

You view the behavior of each routing mode in the examples below. You confirm an algorithm behaves as expected.

## Legend

You represent the number of players on a server with each bar. You observe how evenly each algorithm spreads load.

```
█ = ~5 players
▌ = ~2-3 players
```

---

## Low Load: 5 Players, 3 Servers

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
You observe higher variance at low counts.

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
You generate a typical distribution dependent on UUID hash.

---

## Medium Load: 30 Players, 5 Servers

### `least_players`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
You observe near-perfect even distribution.

### `power_of_two`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
You observe performance close to `least_players` at this scale.

### `round_robin`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
You observe strictly even distribution.

### `random`
```
lobby-1: ████████ (8)
lobby-2: █████▌   (5)
lobby-3: ██████   (6)
lobby-4: ████▌    (4)
lobby-5: █████▌   (7)
```
You observe variance that evens out as player count grows.

### `weighted_round_robin` (weights: 5, 4, 3, 2, 1)
```
lobby-1: ██████████   (10)
lobby-2: ████████     (8)
lobby-3: ██████       (6)
lobby-4: ████         (4)
lobby-5: ██           (2)
```
You observe distribution proportional to weight.

### `least_connections`
```
lobby-1: ██████   (6)
lobby-2: ██████   (6)
lobby-3: ██████   (6)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
You observe even distribution under steady load with EMA smoothing.

### `consistent_hash`
```
lobby-1: ██████   (7)
lobby-2: ██████   (6)
lobby-3: █████    (5)
lobby-4: ██████   (6)
lobby-5: ██████   (6)
```
You observe minor variance from hash ring distribution.

---

## High Load: 100 Players, 10 Servers

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
You observe ±1 deviation at scale.

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
You observe variance shrink with scale.

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
You observe ±1 deviation across 10 servers with the hash ring.

---

## Latency Mode: Proxy-to-Backend Measurement

You observe `latency` rank healthy candidates by ping rather than producing an even distribution chart:

```
lobby-east:  25 ms  ← selected
lobby-west:  70 ms
lobby-eu:   110 ms
```

You route players using the same current ranking. You use this when backend network delay differs. You select the next eligible candidate when the 25 ms server becomes full, drained, unhealthy, circuit-open, or lifecycle-disallowed.

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
| Testing or strict fairness | `round_robin` |
| Very large server pool (50+) | `random` |

See [Routing Algorithms](Routing-Algorithms) for detailed explanations of each mode.
