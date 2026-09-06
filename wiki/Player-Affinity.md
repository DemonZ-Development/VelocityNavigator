# Player Affinity

![Player affinity](headers/player-affinity.png)

You configure player affinity to prefer the healthy lobby a player used recently. You use it when returning to the same backend is desirable.

## Configuration

```toml
[routing.affinity]
enabled = true
stickiness = 0.7
```

`stickiness` ranges from `0.0` to `1.0`:

| Value | Behaviour |
|---:|---|
| `0.0` | You always use normal routing |
| `0.7` | You prefer the recent lobby 70% of the time |
| `1.0` | You always return when the lobby remains eligible |

You require the remembered lobby to be registered, healthy, below capacity, allowed by routing context, and out of [maintenance mode](Maintenance-Mode) or drain. You choose another candidate and update the affinity record when any filter rejects the lobby.

## How long it lasts

You expire affinity records after ten minutes. The configured [storage backend](Storage-and-Databases) persists unexpired records; file storage uses `player_affinity.json`.

You share affinity updates between Velocity proxies when you enable Redis. You review [Redis and Multi-Proxy](Redis-and-Multi-Proxy) for setup.

## Consistent hashing

You map a player UUID to a stable backend with `consistent_hash`, skipping the affinity preference. You pair `consistent_hash` with `stickiness = 1.0` to use affinity as a backup when the hashed backend goes offline.

You balance familiar routing and load using `power_of_two` with `stickiness = 0.7`.

## Privacy and storage

You store only `UUID` and `lobbyId` in affinity records. You do not store IP addresses, session tokens, or chat history. You keep affinity in the `affinity` SQL table for other plugins to read.
