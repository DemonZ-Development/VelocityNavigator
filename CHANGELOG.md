# Changelog

All notable changes to VelocityNavigator are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [4.3.0] - 2026-07-14

### Advanced proxy systems

- Added native parties with `/party invite`, `accept`, `deny`, `kick`, `leave`, and `disband`, private `/p` chat, and leader-follow synchronization on `ServerConnectedEvent`.
- Added a virtual lobby-capacity queue with action-bar position updates, asynchronous slot polling, automatic connection, `/queue leave`, and optional initial-join `holding_server` support.
- Added a dependency-free Redis RESP client for `vn:servers:register` dynamic registration and cross-proxy circuit-breaker, health-cache, backend-state, and affinity synchronization.
- Added backend MOTD lifecycle parsing for markers such as `[STATE:IN_GAME]`; routing only uses configured allowed states.
- Geographic-routing integrations and BungeeCord/Waterfall compatibility remain deferred and are not part of v4.3.0.
- Added optional server management: `/vn server add game` writes only to `velocity.toml`, while `/vn server add lobby` also persists lobby metadata in `servers.toml` and activates it immediately without reload.
- Added `/vn server remove` and `/vn server list`, overwrite protection, custom Velocity-config paths, IPv4/hostname/IPv6 parsing, and atomic configuration writes.
- Expanded `gui.toml` Bedrock controls with enable/fallback switches, sorting, button limits, visibility toggles, and title/content/button overrides with color conversion.
- Added backend `config.yml` switches for the bridge, inventory channel, handshake, refresh, delay, title length, and fallback material.

### Added

- **Configurable language packs** — explicit `en`, `ru`, `es`, `fr`, `de`, `pt_br`, and `zh_cn` selection plus arbitrary custom codes. Built-in changes rewrite active text; player locale is never auto-detected.
- **Separate `gui.toml`** — rows, materials, fillers, refresh interval, navigation slots, and per-server fixed slot/material/name/lore overrides.
- **Universal Java inventory selector** — the same 4.3.0 JAR runs in Velocity proxy or Paper/Spigot bridge mode and announces its mode at startup.
- **Pagination, live refresh, and unavailable indicators** for Java inventory menus.
- `/vn bridge status` reports detected backend bridge versions and last-seen times.
- Universal bridge integration tests cover proxy-open, backend-click, navigation, handshake, and proxy-selection flows.
- Exponential backoff with jitter for connection retries — each retry waits progressively longer with a small random jitter to avoid thundering-herd reconnects.
- Player affinity persistence — unexpired sticky-session mappings are now saved to disk and restored across proxy restarts.
- `/vn health` command — consolidated one-shot diagnostics: routing mode, lobby count, circuit-breaker states, drained servers, cache sizes, and affinity entry count in a single screen.
- **HTML operations dashboard** — a separate HTTP server on its own port serving a live lobby table, routing distribution chart, affinity map, config summary, recent routing events, and joins/leaves-since-start counters. Disabled by default and authenticated via a bearer-token login flow.
- `velocity-plugin.json` descriptor — modern Velocity plugin metadata alongside the existing `@Plugin` annotation.
- New reproducible marketplace brand system with a plugin icon, hero banner, social card, nine feature panels, platform-specific listing copy, meaningful alt text, and a visual feature overview in the wiki.
- Built the backend bridge against the Spigot API 1.16.5 baseline without version-specific NMS.

### Changed

- `routing.use_menu_for_lobby` replaces `use_chat_menu_for_lobby`; the old key remains a compatible alias.
- Selector mode/fallback lives in `navigator.toml`, language in `messages.toml`, GUI presentation in `gui.toml`, and managed lobby metadata in `servers.toml`. The plugin/config versions are 4.3.0/v8.
- MiniMessage, classic `&`/`§`, `&#RRGGBB`, and Bungee-style hex colors are accepted in configurable text.
- Server health cache keys are now normalized to lowercase. Mixed-case server names no longer produce duplicate cache entries.
- Consistent hash ring uses a thread-local `MessageDigest` instead of allocating one per lookup. Throughput on `consistent_hash` mode improves by roughly 3–4× under load.
- `RoutePlanner` now builds a `Map<String, LobbyEntry>` once per planning call instead of scanning the lobby list linearly per candidate.
- Config reload now uses a dedicated `ReentrantLock` instead of `synchronized(this)`. Admin commands and event subscribers no longer contend on the same monitor during a reload.
- Bedrock form text stripping now matches legacy color codes case-insensitively. `&C` and `&L` are now stripped alongside `&c` and `&l`.
- Update checks are now silent by default — no startup log line, no periodic console message. `/vn updatecheck` still works for manual checks. Set `update_checker.silent = false` in `navigator.toml` to restore the old behavior.
- **Config version bumped to 8.** Adds the final 4.3.0 advanced-system, managed-server, selector, dashboard, and Redis settings. Older configs are auto-migrated and backed up; network-facing systems remain disabled by default.

### Fixed

- Party commands now expose explicit `/party status` and `/party chat <message>` paths, while `/p <message>` and multiword messages use Velocity raw-command parsing.
- Escaped MiniMessage entities such as `&lt;player&gt;` are no longer mistaken for the legacy `&l` bold color code.
- Dashboard API version data now comes from the plugin metadata instead of a hardcoded prerelease string.
- Redis RESP parsing on the Velocity proxy now enforces line, bulk, array, total-frame, and nesting limits before allocation or recursion.
- Backend Redis lifecycle registration now rejects oversized response lines instead of allowing peer-controlled heap growth.
- Dashboard bearer credentials are accepted only through the `Authorization` header. The browser login keeps the token in memory and never places it in URLs or persistent browser storage.
- `MetricsService.active()` and `statusLine()` are now `volatile`. Reader threads (Prometheus exporter, `/vn status`) no longer risk seeing stale values after a config reload.
- `PrometheusExporter.start()` now clears the `server` reference if `start()` throws. Previously the next `stop()` call would operate on an unstarted server.
- `UpdateChecker` now uses the non-deprecated `JsonParser.parseString(...)` and the `nextAllowedCheck` read-modify-write is guarded by a `synchronized` block to prevent duplicate 429 retries under concurrency.
- `ConfigManager`'s generated `navigator.toml` header box no longer overflows on the bStats URL line.
- Empty `if` branch in `applyLoadedConfiguration` removed — round-robin reset logic is now a single positive check.

### Internal

- Stripped ~110 inline `//` comments across the source tree. Public API javadoc and license headers are preserved.
- Removed unused imports, unused constructors (`Config.UpdateCheckerSettings(UpdateChannel)`, `ServerCandidate` 4-arg, etc.) and `SemanticVersion.raw()`.
- Tightened a handful of synchronized blocks to lock-free alternatives where safe.

---

## [4.2.0] - 2026-05-30

### Added

- **Embedded Prometheus exporter & admin panel** — built-in HTTP server exposing real-time metrics (`/metrics`) on routing distributions, pings, circuit breaker statuses, and connection events.
- **Grafana dashboard setup command** — `/vn setup grafana` generates a pre-configured Grafana telemetry dashboard JSON file.
- **Interactive selector menus** — native Bedrock Form GUI (via Geyser/Floodgate integration) and a clickable Java chat selector menu with hover tooltips showing health and latencies.
- **Ping-based routing strategy (`latency`)** — selects the server with the lowest ping latency.

### Fixed

- Java and Bedrock lobby menu selections now consistently enforce drain mode, circuit breakers, capacity checks, and the configured lobby pool. Stale or manually forged menu choices no longer bypass these checks.
- The Prometheus exporter is now started during initial proxy boot when enabled, not only after `/vn reload`.
- Restored true consecutive-failure behavior for the circuit breaker.
- Update notification settings are now preserved during config rewrites, and admin join notifications are aligned with `[update_checker].notify_admins`.
- The documented `latency` routing mode is now accepted by config validation and migration normalization.
- Contextual group names are normalized consistently so mixed-case mappings continue to route.
- Maven artifact version, Velocity plugin metadata, and user-facing docs are aligned for the 4.2.0 release.

### Changed

- **Config version bumped to 6.** The generated `navigator.toml` is restructured with section banners, grouped documentation, and a more navigable layout. Existing configs are auto-migrated and backed up.

---

## [4.1.0] - 2026-05-26

### Added

- **Bedrock/Geyser player support** — soft-dependency integration with Geyser and Floodgate. Strips advanced Kyori Component formatting (gradients, hover, click actions) so messages render on Bedrock clients, and maps Java UUIDs for player affinity tracking.
- **First-run experience** — console welcome dashboard on fresh installs. On plugin upgrades, a release notes digest is printed.
- **`/vn servers` diagnostics command** — paginated status dashboard for all configured lobbies, showing player count and capacity, circuit breaker state, and drain status.
- **Configurable dashboard colors** — customizable status tags and colors for `/vn servers`, supporting hex, RGB, and MiniMessage styling in `navigator.toml`.
- **Typo auto-correction and Levenshtein validation** — typo detection on config load and reload using Levenshtein distance (e.g. suggesting `"least_players"` for `"leadt_players"`).
- **Self-documenting configuration keys** — `navigator.toml` comments are populated on generation or migration, linking to the relevant section anchor on the wiki.
- **Automatic legacy color code converter** — matches and converts the standard `&` and `§` legacy formatting codes to MiniMessage on load. Supports `"auto"` (with one-time warnings), `"minimessage"`, and `"legacy"` modes.
- **Periodic update checker with backoff** — recurring scheduled update checks with exponential backoff on HTTP 429 errors (scaling up to 4 hours).
- **Empty lobby routing fallbacks** — configurable degradation strategies (`"disconnect"` or `"fallback_server"`) when all primary lobby options are offline or circuit-broken.
- **Permission default change** — the `/lobby` command default permission is now `"none"`, so it works without explicit configuration. Existing configs are preserved on migration.

---

## [4.0.0] - 2026-05-01

### Added

- **Power of Two selection algorithm** (`power_of_two`) — picks two random candidates and selects the one with fewer players. Near-optimal distribution at O(1) cost.
- **Weighted Round Robin selection algorithm** (`weighted_round_robin`) — interleaved WRR that distributes traffic proportionally to server weights.
- **Least Connections selection algorithm** (`least_connections`) — selects the server with the lowest exponential moving average (EMA) of connection load and rate.
- **Consistent Hash selection algorithm** (`consistent_hash`) — deterministic player-to-server mapping using a consistent hash ring with 150 virtual nodes and SHA-256 hashing. Provides session affinity.
- **LobbyEntry format** — servers can be configured as plain strings or inline tables with `max_players` and `weight` fields. Backward compatible with plain strings.
- **Per-lobby max-player cap** — servers at their `max_players` capacity are excluded from routing.
- **Circuit Breaker** — automatic server failure detection with a CLOSED → OPEN → HALF_OPEN state machine. Unhealthy servers are excluded from routing until they recover.
- **Server Drain Mode** — `/vn drain <server>`, `/vn undrain <server>`, `/vn drain status` commands for graceful server maintenance.
- **Connection Retry with Fallback** — automatic retry on connection failure with configurable `max_retries`. Shows a retry message with `<attempt>/<max>` placeholders.
- **Per-Group Selection Mode Override** — contextual routing groups can specify their own `mode`, overriding the global `selection_mode`.
- **Fallback Priority Chain** — ordered fallback groups when a contextual group's servers are all unavailable.
- **Player Affinity Routing** — sticky sessions with configurable `stickiness` probability (0.0–1.0). Players tend to return to their previous lobby.
- **Graceful Degradation** — when all health checks fail, falls back to a configured degradation mode (default: `random`) instead of showing "No lobby found".
- **Geo-Based Routing (experimental)** — stub implementation for geo-based lobby routing using MaxMind GeoLite2 Country database.
- **Routing Metrics API** — new `NavigatorAPI` methods: `getRoutingDistribution()`, `getHealthCheckLatencies()`, `getCircuitBreakerStatuses()`.
- **Connection Rate Tracking** — sliding window (60-second) connection rate tracker used by `least_connections` mode.
- **Server Load Tracking** — EMA-based server load tracker used by `least_connections` mode.
- **Routing Stats** — per-server connection counts with 60-second reset, shown in `/vn status`.
- **Enhanced `/vn status` dashboard** — now shows circuit breaker status, drained servers, and routing distribution.
- **`/vn updatecheck` command** — manually check for updates (replaces the recurring auto-update check).
- **Startup update notification** — one-time update check 5 seconds after proxy start.
- **Admin join update notification** — players with `velocitynavigator.admin` permission are notified in-game when they join if an update is available. Controlled by `notify_admins_on_join` config.
- **`<player>` placeholder** — new placeholder available in all message templates.
- **`<attempt>` and `<max>` placeholders** — available in `messages.retrying`.
- **`messages.retrying` config** — new message template for connection retry notifications.
- **`notify_on_startup` config** — suppress startup update notification.
- **`notify_admins_on_join` config** — enable/disable in-game admin update notification on join.
- **Health check cache purge** — expired cache entries are purged every 60 seconds.
- **`getCachedOnlineServers()` method** — synchronous cached player count access for initial join balancing (replaces blocking `.join()` call).
- **Config version field** — `CURRENT_VERSION` set to 4. Auto-migration from v3 configs with `.bak` backup.

### Changed

- **Removed `.join()` blocking call** in `onPlayerChooseInitialServer` — replaced with synchronous cache lookup. Falls through to Velocity's built-in try list on cold start.
- **Round-robin state only resets when lobby topology changes** — `applyLoadedConfiguration()` compares the previous and current lobby lists before resetting.
- **Contextual groups** — changed from `Map<String, List<LobbyEntry>>` to `Map<String, GroupConfig>` where `GroupConfig` contains `servers` and optional `mode`.
- **UpdateChecker** — removed recurring schedule; now runs a single check on startup. Removed `enabled`, `notifyConsole`, `startupDelaySeconds` fields.
- **ConfigManager** — reads both plain strings and inline tables for lobby entries (backward compatible). Writes inline tables when `max_players` or `weight` is non-default.
- **MessageFormatter** — added `player`, `attempt`, `max` to allowed placeholders.
- **`noLobbyFound` message** — now includes the `(<reason>)` placeholder by default.
- **`ServerCandidate` record** — now includes `effectiveWeight` and `emaLoad` fields.
- **`RouteDecision`** — provides an ordered candidate list for retry fallback.

### Fixed

- **Blocking `.join()` in event handler** — `onPlayerChooseInitialServer` no longer blocks the event loop with `.join()` calls. Uses cached data synchronously instead.
- **Round-robin reset on every reload** — the round-robin counter is now only reset when the lobby topology actually changes, preventing unnecessary redistribution on config reload.
- **Health check cache memory leak** — expired cache entries are now purged every 60 seconds.
- **Permission node inconsistency** — `velocitynavigator.bypasscooldown` now also checks `velocitynavigator.bypass.cooldown` for consistency.

### Deprecated

- **`velocitynavigator.bypasscooldown` permission** — use `velocitynavigator.bypass.cooldown` instead. The legacy name still works as a fallback.

### Removed

- **`update_checker.enabled` config field** — the update checker now always runs on startup.
- **`update_checker.notifyConsole` config field** — update notifications are always logged to console.
- **`update_checker.startupDelaySeconds` config field** — startup delay is fixed at 5 seconds.
- **Recurring update check schedule** — replaced by a one-time startup check and `/vn updatecheck`.

---

## [3.0.0] — 2026-04-10

### Added

- **Initial Join Balancing** — Players are load-balanced the moment they connect to the proxy via `PlayerChooseInitialServerEvent`.
- **Developer API** — `NavigatorAPI` and `NavigatorAPIProvider` for third-party plugin integration.
- **Three Routing Modes** — `least_players`, `round_robin`, and `random` selection algorithms.
- **Contextual Routing** — Route players to game-specific lobbies based on which server they are leaving.
- **Self-Documenting Config** — `navigator.toml` generates with inline comments explaining every setting.

### Changed

- **Async Health Checks** — Ping candidate lobbies before routing with configurable timeout and caching.
- **Ping Coalescing** — Multiple simultaneous `/lobby` requests share the same `CompletableFuture` ping.
- **Pre-Execution Cooldown Locking** — Cooldown is applied before command execution to prevent macro abuse.
- **Graceful Failover** — Falls back to default lobby pool when all contextual lobbies are offline.

### Added (Telemetry & Updates)

- **bStats Integration** — Anonymous usage telemetry (plugin ID: 28341).
- **Modrinth Update Checker** — Automatic version checking with configurable release channel.

### Added (Admin Tools)

- `/vn reload` — Hot-reload `navigator.toml`.
- `/vn status` — View runtime status.
- `/vn version` — Check installed vs. latest version.
- `/vn debug player <name>` — Preview routing decision.
- `/vn debug server <name>` — Inspect server health.
- Full tab-completion for all admin commands.

### Added (Configuration)

- **Automatic Migration** — Migration from v1/v2 configs with backup generation.
- **Field-Level Validation** — Invalid config values are corrected with warnings.
- **MiniMessage Support** — All player-facing messages support MiniMessage rich text formatting.

---

## [2.0.0] — Legacy

Previous version with basic lobby routing. Superseded by v3.0.0.

---

## [1.0.0] — Legacy

Initial release with single-server lobby navigation.

---

*VelocityNavigator is developed and maintained by [DemonZ Development](https://github.com/DemonZ-Development).*
