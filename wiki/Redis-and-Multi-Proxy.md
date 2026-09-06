# Redis and Multi-Proxy Networks

![VelocityNavigator Redis setup](headers/redis-and-multi-proxy.png)

Redis is optional. A single Velocity proxy operates without it. Add Redis when multiple proxies share routing health and affinity, or when backends announce themselves automatically.

## What Redis shares

- Circuit-breaker state
- Health snapshots and measured latency
- Backend lifecycle states
- Player affinity
- Dynamic backend registration events

Redis does not share maintenance/drain state, storage health, party membership, or queue positions. Apply operational flags to every proxy and pin party/queue players to the same proxy with your external load balancer.

## Proxy configuration

Each proxy requires a unique `node_id` but shares the endpoint, channel prefix, and registration secret:

```toml
[redis]
enabled = true
host = "redis.example.net"
port = 6379
username = "velocitynavigator"
password = "replace-this"
ssl = true
node_id = "proxy-eu-1"
channel_prefix = "vn"
sync_seconds = 5
connect_timeout_ms = 3000
read_timeout_ms = 10000
reconnect_min_ms = 1000
reconnect_max_ms = 30000
registration_secret = "use-a-long-random-secret"
registration_max_age_seconds = 30
allowed_registration_hosts = ["10.20.0.13", "*.backend.example.net"]
```

Assign a stable `node_id` to each proxy, such as `proxy-eu-1` and `proxy-eu-2`.

The ID describes the proxy. Avoid temporary process IDs. Proxies ignore messages carrying their own `node_id`. Duplicated IDs prevent proxies from accepting each other's state. All participating proxies and backends must use the same `channel_prefix`.

## Check the connection

After `/vn reload`, run:

```text
/vn redis test
/vn redis status
```

The test checks the endpoint and `PING`. Status shows the subscription connection, message counts, reconnects, rejected registrations, and recent errors.

## Automatic backend registration

Install the universal JAR on the Paper or Spigot backend, then edit `plugins/VelocityNavigator/config.yml`:

```yaml
redis:
  enabled: true
  host: redis.example.net
  port: 6379
  username: velocitynavigator
  password: replace-this
  ssl: true
  channel_prefix: vn
  registration_secret: use-a-long-random-secret
  server_name: lobby-3
  advertised_host: 10.20.0.13
  advertised_port: 25565
  group: default
  max_players: 100
  weight: 1
  unregister_on_shutdown: true
```

The `registration_secret` must match the proxy. `advertised_host` must reach every proxy and appear in `allowed_registration_hosts`. Set a unique `server_name` safe for Velocity registration.

Dynamic registration updates the running proxy. It does not edit `velocity.toml`. Use [Server Management](Server-Management) for permanent file entries.

## Outages and recovery

Redis acts as a synchronization layer. It is not required for local routing. If Redis drops, proxies continue using local health checks, routing configuration, circuit breakers, and saved affinity data. Remote state and dynamic registration events stall until the connection restores.

The Velocity subscriber reconnects automatically using `reconnect_min_ms` and `reconnect_max_ms`. The state broadcast resumes upon connection. Check the live state:

```text
/vn redis status
/vn redis test
```

Backend registration operates on events. A backend announces itself when the bridge starts. When `unregister_on_shutdown` is active, it announces removal during a clean shutdown. If Redis is unavailable during startup, restart the backend after Redis recovers to announce it again. Missed shutdown announcements leave old entries visible as offline. Restart the proxy to clear runtime-only registrations.

Redis Pub/Sub skips missed messages. Maintain permanent servers in `velocity.toml` or manage them with `/vn server add` instead of relying entirely on dynamic announcements.

## Security basics

- Keep Redis on a private network.
- Use Redis authentication.
- Enable TLS when traffic crosses untrusted networks.
- Generate a long, unique `registration_secret`.
- Restrict `allowed_registration_hosts`.
- Never reuse a public website password for Redis or registration secrets.

Dynamic registration is **fail-closed**: when `registration_secret` is blank on the proxy, every incoming registration is rejected and counted under rejected registrations in `/vn redis status`. Backend publishers already refuse to start without a secret, so a blank secret simply disables dynamic registration rather than accepting unsigned announcements. `rejected_registrations` also counts mismatched signatures, expired timestamps, replayed signatures, and non-allowlisted hosts.

The signature algorithm is HMAC-SHA256 over a canonical form of the registration payload (see `common/RedisRegistrationSigner`). Proxy subscribers validate the supplied signature against the shared `registration_secret`, then check the timestamp falls within `registration_max_age_seconds`. Replay protection uses an in-memory TTL map; the same signature cannot be accepted twice within its age window. Backend publishers MUST reuse `common/RedisRegistrationSigner.sign(...)` so the proxy accepts the announcement.

## Supported Redis setups

VelocityNavigator connects to a standalone-compatible endpoint. It supports Redis ACL authentication and TLS. It excludes Redis Cluster discovery and Sentinel failover discovery. A managed Redis service works when providing a compatible endpoint.

## Common core: how RedisTransport, RedisSecurityUtils, and ModrithClient work together

The proxy subscriber and the backend publisher both go through `common/RedisTransport`. That class owns wire bytes only: RESP `connect`, `authenticate`, `writeCommand`, and `readResponse`. It enforces a 65,536-byte line cap, a 4 MB bulk cap, a 16,384 array cap, a 64-deep nesting cap, and an 8 MB total-frame cap so a malicious Redis cannot drive proxy memory. Lifecycle (the subscriber executor, reconnect backoff, the state-publish schedule) stays in `redis/RedisSyncService`. The subscription socket runs a read timeout scaled to `read_timeout_ms` and `sync_seconds`, so a silent half-open connection is detected and re-established instead of blocking the subscriber forever.

Signature verification, replay-cache eviction, the timestamp freshness window, and the host allowlist matcher all live in `common/RedisSecurityUtils`. The proxy's `trustedRegistration` and `allowedRegistrationHost` call these helpers directly; the security-policy tests rely on them as static methods.

[common/ModrithClient](Common-Core-Architecture#what-lives-in-common) is the same pattern applied to the Modrinth update endpoint. Both proxy and backend use ModrithClient.userAgent, ModrithClient.parseRelease, and ModrithClient.bestVersionIn. Each side brings its own HTTP library, logger, and backoff strategy.

Read [Storage and Databases](Storage-and-Databases) for details on local storage versus Redis data.
