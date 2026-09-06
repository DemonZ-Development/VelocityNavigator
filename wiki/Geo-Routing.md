# Geo Routing

Geo routing looks up a player's country and prefers healthy lobbies assigned to that country. It is useful when your network has backends in more than one region.

It does not measure the player's real ping. If all your servers are in one location, use `least_players` or `power_of_two` instead.

## Which provider should I use?

| Provider | Pick it when |
|---|---|
| `georestrict` | You want the easiest tested integration with the GeoRestrict Velocity plugin |
| `maxmind` | You want local lookups from your own `.mmdb` database |
| `ip_api` | You accept sending public player IPs to an external lookup service |
| `auto` | You want VelocityNavigator to use the available providers in fallback order |

Country lookups happen asynchronously. A failed lookup should not freeze a player's connection; routing continues with your configured fallback.

## Recommended: GeoRestrict

Download GeoRestrict from its official [Modrinth project page](https://modrinth.com/plugin/georestrict). Open the **Versions** tab and choose a version that lists Velocity support.

Install both plugins on the **Velocity proxy**, not just on Paper or another backend:

```text
your-velocity-server/
└── plugins/
    ├── georestrict-2.0.1.jar
    └── VelocityNavigator-4.5.0.jar
```

The GeoRestrict JAR can also run on backend platforms, but VelocityNavigator's geo-routing integration looks for its API on the proxy. If GeoRestrict exists only on Paper, it will not be detected for proxy routing.

Stop and restart Velocity after adding the JARs. `/vn reload` cannot discover a plugin that was not loaded at startup. In the proxy log, look for:

```text
[VelocityNavigator] GeoRestrict detected - using GeoRestrict API for geo-location lookups.
```

### Configure VelocityNavigator

In `navigator.toml`:

```toml
[routing]
selection_mode = "geo_distance"
default_lobbies = ["lobby-us", "lobby-eu"]
```

In `geo.toml`:

```toml
[geo_routing]
enabled = true
provider = "georestrict"
database_path = ""
fallback_enabled = true
fallback_mode = "least_players"

[geo_routing.affinity_countries]
"lobby-us" = ["US", "CA", "MX"]
"lobby-eu" = ["DE", "FR", "NL", "BE", "AT"]
```

Save both files, then run:

```text
/vn config validate
/vn reload
```

The lobby names must match the names under `[servers]` in `velocity.toml`. Countries use two-letter ISO codes such as `US`, `DE`, and `JP`.

### What we tested

VelocityNavigator's GeoRestrict integration was practically tested with the real `georestrict-2.0.1.jar` on Velocity, not with a mock API. The test confirmed plugin detection, a public `8.8.8.8` lookup resolving to `US`, US affinity routing, healthy-server filtering, and least-player fallback.

That gives GeoRestrict 2.0.1 a tested green light for this integration. Future GeoRestrict releases should remain compatible through its public `GeoRestrictAPI`, but check both projects' release notes before changing versions on a production proxy.

## Understanding fallbacks

`fallback_enabled` allows another lookup source where applicable.

`fallback_mode` can be:

- another provider such as `ip_api`; or
- a normal routing algorithm such as `least_players`.

For a GeoRestrict-only setup, `fallback_mode = "least_players"` is a sensible default. If the country lookup fails or no country affinity matches, VelocityNavigator still chooses a healthy lobby instead of blocking the route.

## MaxMind

1. Download a GeoLite2 Country or City `.mmdb` file from MaxMind.
2. Extract it somewhere the Velocity process can read.
3. Prefer an absolute path:

```toml
[geo_routing]
enabled = true
provider = "maxmind"
database_path = "/srv/minecraft/data/GeoLite2-Country.mmdb"
fallback_enabled = false
fallback_mode = "least_players"
```

On Windows, use forward slashes or escaped backslashes:

```toml
database_path = "C:/minecraft/data/GeoLite2-Country.mmdb"
```

Reload, then check the proxy log for database or permission errors.

## IP-API

`provider = "ip_api"` sends the player's public IP to the configured IP-API endpoint and caches successful country results in memory.

Before enabling it:

- check the service's current usage policy and rate limits;
- allow outbound HTTP from the proxy;
- mention the lookup in your network's privacy notice where required;
- remember that the cache is cleared when Velocity restarts.

Loopback and private addresses such as `127.0.0.1` do not represent a real player's country. Test through the same public-facing path your players use.

## How a route is chosen

1. VelocityNavigator removes offline, full, drained, maintained, circuit-broken, and disallowed lifecycle candidates.
2. It resolves the player's country.
3. It keeps the eligible lobbies whose affinity list contains that country.
4. It applies `fallback_mode` to that smaller pool.
5. If no affinity matches, it applies the fallback to all remaining eligible lobbies.

Geo affinity never makes an unhealthy or unavailable server eligible.

Geo routing applies to both initial joins and `/lobby`. A contextual group with `mode = "geo_distance"` also uses country affinity for players joining for the first time, even when the global `selection_mode` is different.

## Verify your setup

Enable verbose logging temporarily:

```toml
[debug]
verbose_logging = true
```

Reconnect through Velocity and look for a route reason similar to:

```text
country=US, reason=geo_affinity:US:least_players, candidates=[lobby-us]
```

These commands help:

```text
/vn config validate
/vn health
/vn servers
/vn debug player YourName
```

Turn verbose logging off after testing on a busy network.

## Troubleshooting

**The log says `GeoRestrict not detected`.**

Make sure the GeoRestrict JAR is in the Velocity proxy's `plugins/` folder, not only a backend. Fully restart Velocity.

**Everyone uses the fallback route.**

Check `routing.selection_mode = "geo_distance"`, `geo_routing.enabled = true`, and exact server-name matches. A localhost connection is not a useful public-IP test.

**MaxMind says the database is missing.**

Use an absolute path, extract the `.mmdb` from its archive, and confirm the Velocity process can read it.

**IP-API lookups fail.**

Check outbound access and the provider's current limits. A failed lookup should use fallback routing.

**A correctly matched server is skipped.**

Run `/vn servers`. Health, capacity, maintenance, drain, circuit-breaker, and lifecycle rules take priority over country affinity.
