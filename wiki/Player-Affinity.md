# Player Affinity

![Player affinity](headers/player-affinity.png)

Player affinity makes lobby changes feel less random by preferring the healthy lobby a player used recently. It is useful when short-lived lobby state, friends, or cached data make returning to the same server desirable.

## Configuration

```toml
[routing.affinity]
enabled = true
stickiness = 0.7
```

`stickiness` is a value from `0.0` to `1.0`:

| Value | Behaviour |
|---:|---|
| `0.0` | Always use normal routing |
| `0.7` | Prefer the recent lobby about 70% of the time |
| `1.0` | Always return when that lobby is still eligible |

The remembered lobby must still be registered, healthy, below capacity, and allowed by the current routing context. If it is unavailable, VelocityNavigator simply chooses another candidate.

## How long it lasts

Affinity records expire after ten minutes. Unexpired records are saved in `plugins/velocitynavigator/affinity-store.json`, so a normal proxy restart does not immediately forget everyone.

With Redis enabled, affinity updates are shared between Velocity proxies. See [Redis and Multi-Proxy](Redis-and-Multi-Proxy) for the network setup.

## Consistent hashing

The `consistent_hash` routing mode already maps a player's UUID to a stable server, so the separate affinity preference is skipped in that mode.

For most networks, `power_of_two` with `stickiness = 0.7` is a good balance between familiar routing and even load.
