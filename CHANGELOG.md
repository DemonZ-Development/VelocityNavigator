# Changelog

This file documents all notable changes to VelocityNavigator.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [4.5.0] - 2026-09-06

### New — Native Packet-Level NPC Server Selectors (/vnavnpc)

A completely new, zero-dependency in-game NPC server selector engine built natively into the backend bridge:

- **Real Player-Model NPCs on 1.20.5+**: Spawns genuine player models via direct packet transmission on Paper, Spigot, and Folia 1.20.5 through 1.21.x — real Mojang skins, full-size interaction hitboxes, held items, and smooth head rotation. Zero external plugins required (no Citizens, no ProtocolLib).
- **Billboarded TextDisplay Holograms**: Multi-line floating text labels billboarded (`CENTER`) so names and server stats face players cleanly from every direction.
- **Scoreboard Team Glowing**: Custom team glowing outline colors (`/vnavnpc glow <id> <on|off> [color]`).
- **Packet-Level Netty Click Interception**: Direct Netty channel handler captures clicks on fake players with built-in interaction range validation.
- **Smart Click Actions & Conditional Routing**: Configure NPCs to route players to servers, open backend YAML menus, run commands, or execute conditional permission routing (`action:cond(perm=velocitynavigator.vip?server:vip-lobby|server:lobby)`).
- **Dynamic Proximity Head Tracking**: NPCs rotate their heads toward the nearest player within 64 blocks.
- **Paper 26.2 Mannequin & Folia Safety**: Built with native support for Paper mannequin entities and Folia's regionized schedulers.
- **Automatic Fallback for Older Servers**: Automatically detects backends older than 1.20.5 and seamlessly uses an optimized armor-stand mannequin with Interaction hitboxes.
- **Full In-Game Management (`/vnavnpc`)**: Comprehensive command tree with tab completion (`create`, `action`, `skin`, `glow`, `hand`, `offhand`, `status`, `respawn`, `tp`, `remove`, `list`, `reload`).

### New — Full GeoIP Distance Routing Engine (`geo.toml`)

Route players to the lowest-latency lobby clusters based on physical geographic location:

- **Geo-Distance Selection Mode (`geo_distance`)**: Calculates mathematical Great-Circle distance (Haversine formula) between player coordinates and server datacenter locations, automatically choosing the physically closest backend.
- **Multiple GeoIP Providers**: Native support for MaxMind GeoLite2 databases (`.mmdb`), real-time HTTP fallback via ip-api.com, and seamless [GeoRestrict](https://modrinth.com/plugin/georestrict) integration.
- **VPN & ASN Detection**: Built-in integration with GeoRestrict as a primary lookup source for proxy, VPN, and autonomous system verification.
- **Country & Continent Affinity Overrides**: Map specific countries or continents directly to designated lobby pools (`geo_routing.affinity_countries`).
- **Thread-Safe Subnet Matching**: High-performance in-memory IP cache with CIDR subnet matching for instantaneous routing decisions with zero main-thread impact.
- **Contextual Initial Join Affinity**: Regional affinity rules apply immediately during initial connection handshakes as well as `/lobby` commands.

### New — Dynamic MOTD Subsystem & Configuration (`motd.toml`)

A standalone, multi-mode server list MOTD engine:

- **Dynamic Server List Rotation**: Configurable rotation engines supporting auto-rotating intervals (`ROTATING`), randomized selections (`RANDOM`), and sequential cycles (`SEQUENTIAL`) via `motd.toml`.
- **Readable Multiline TOML Formatting**: MOTD configuration uses clean, human-readable multiline TOML strings with comment preservation and automated version backups.
- **Emergency Maintenance Overrides**: Dedicated maintenance MOTD templates that automatically take over when global maintenance mode is enabled (`override_motd_on_maintenance = true`).
- **MiniMessage & Gradient Styling**: Rich text styling with native MiniMessage gradients, hex colors, font tags, and legacy color code (`&a`, `&b`) translation.
- **Real-Time Placeholders**: Embed dynamic network information including `{online}`, `{max}`, `{maintenance_reason}`, and `{version}`.
- **Console & In-Game Administration**: Dedicated command suite (`/vn motd reload`, `/vn motd list`, `/vn motd add <text>`, `/vn motd remove <index>`, `/vn motd setmode <mode>`).

### New — Authentication & Defensive Security Engine (`auth.toml`)

Proxy-side authentication engine with military-grade hashing, holding-server quarantine, and native interfaces for both Java and Bedrock clients:

- **Argon2id Password Hashing**: State-of-the-art password security powered by BouncyCastle with configurable memory cost, iterations, and parallelism.
- **Legacy SHA-256 Verification**: Automatic backward-compatible password verification and upgrade path for pre-existing password databases.
- **Interactive Sign-Board GUI for Java**: Java players receive an interactive in-game sign prompt to enter passwords privately without typing in open chat, with automatic command fallback.
- **Native Floodgate Bedrock Forms**: Bedrock players via Floodgate receive native modal dialog forms for registration and password input.
- **Holding Lobby Physical Quarantine**: Unauthenticated players are confined to a holding server with movement, interaction, block breaking/placing, item pickup/drop, and chat locked down until authentication succeeds (with optional blindness effect).
- **Brute-Force Rate Limiting**: Global and per-account failure counters trigger an automatic 5-minute lockout with player notifications upon repeated failed logins.
- **Bridge-Authenticated Sign Sessions**: Sign submissions route directly over the internal proxy-backend bridge channel with token validation to prevent spoofing.
- **Reload-Persistent Sessions**: In-memory and persistent session tokens survive proxy reloads without forcing online players to log in again.

### New — Cross-Server Party & Team Engine (/party)

Complete proxy-wide party management with dual Bedrock & Java interfaces and full backend synchronization:

- **Party Hierarchy & Roles**: `LEADER`, `OFFICER`, and `MEMBER` roles with promotion and demotion (`/party promote`, `/party demote`).
- **Custom Party Names & Formatting**: Rename parties (`/party rename <name>`) with MiniMessage and color support.
- **Leader Follow**: Party members automatically follow the party leader when transferring between lobbies or game servers.
- **Open & Invite-Only Modes**: Public or private join toggles (`/party open`, `/party close`).
- **Dual Platform GUIs**: Dedicated `/party menu` with native Bedrock Cumulus modal forms and Java Edition chest inventories.
- **Complete PlaceholderAPI Expansion**: Real-time team and party values exposed on all Paper/Spigot backends:
  - `%velocitynavigator_party_in_party%` (`true` / `false`)
  - `%velocitynavigator_party_name%` (Party / team display name)
  - `%velocitynavigator_party_leader%` (Leader username)
  - `%velocitynavigator_party_size%` and `%velocitynavigator_party_max_size%`
  - `%velocitynavigator_party_is_leader%` (`true` / `false`)
  - `%velocitynavigator_party_role%` (`LEADER`, `OFFICER`, `MEMBER`)
  - `%velocitynavigator_party_is_open%` (`true` / `false`)
  - `%velocitynavigator_party_members%` (Formatted comma-separated member list for tablists/scoreboards)
  - `%velocitynavigator_ping%`, `%velocitynavigator_lobby%`, and EssentialsX integration placeholders.

### New — Custom Menu System (Backend)

Fully customizable YAML-based menus on Paper/Spigot servers. Create your own server selectors, minigame menus, or any interactive GUI:

- Create unlimited custom menus in `menus/*.yml` files
- Items with configurable slots, materials, custom model data, skull owners, and sounds
- Nested menus — link directly to submenus (`menu:games`)
- Command execution — execute proxy or backend commands on click (`cmd:/command`)
- Server routing — send players to specific lobby clusters
- Live item refresh (`@refresh:N`) and pagination (`@page:N`) for large networks
- Disabled items (`@disabled`) for greyed-out coming-soon placeholders
- Default starter menus (main, games, lobbies) auto-generated on first run
- `/vnavmenu` command with 7 subcommands (`open`, `add`, `remove`, `title`, `rows`, `list`, `reload`)

### New — Folia Support

Full compatibility with Folia's regionized multi-threaded architecture:

- Runtime Folia detection via MethodHandle reflection
- Backend task scheduling uses entity-owned region threads on Folia
- Gracefully falls back to standard Bukkit schedulers on Paper/Spigot
- `plugin.yml` declares native `folia-supported: true`

### New — Multi-Engine Database Storage (`storage.toml`)

Choose where your player affinity, sessions, and credentials live:

- **Embedded SQLite**: Zero-configuration embedded database with native driver bundling
- **MySQL & MariaDB**: High-throughput database storage with HikariCP connection pooling
- **PostgreSQL**: Enterprise SQL database storage for multi-proxy architectures
- **Plain JSON Files**: Lightweight, zero-setup file storage for smaller communities
- **Automatic Schema Migrations**: Tables and column upgrades applied automatically on boot

### New — Maintenance & Graceful Evacuation

Take servers offline without disrupting player gameplay:

- Network-wide or per-server maintenance states (`maintenance global on|off`, `/vn maintenance [server] [on/off]`)
- Safe player evacuation to healthy eligible destination lobbies without kicks
- Maintenance blocks direct backend transfers as well as Navigator routing
- Custom kick reasons and dynamic countdown badges

### New — Backend Update Checker

Independent update checker for Paper/Spigot servers:

- Periodically checks Modrinth API for new releases
- Exponential backoff on HTTP 429 rate limits
- Configurable interval via `update_check_interval_minutes`

### New — Version Mismatch Detection

Proxy continuously monitors backend bridge compatibility:

- Compares backend plugin versions against the proxy version during HELLO handshakes
- `/vn bridge` displays `✓ (up to date)`, `⚠ (outdated)`, or `✗ (not detected)` per backend

### New — Backend bStats Telemetry

Dedicated metrics telemetry for Paper/Spigot backends (plugin ID 32887):

- Custom charts: `folia_enabled`, `server_software`, `inventory_menu_enabled`, `handshake_enabled`, `refresh_enabled`, `redis_registration_enabled`

### New — Modular Configuration Architecture

- **Separated Config Files**: Dedicated `storage.toml`, `geo.toml`, `auth.toml`, and `motd.toml` for clean organization
- **Config Version 9**: Automatic migration from legacy v8 configs
- **Organized Backup Folder**: All version backups stored in a dedicated `backups/` subfolder
- **Automatic Backup Pruning**: Only the latest backup per file is retained — stale backups are cleaned automatically
- **Backend Config Auto-Migration**: `config.yml` auto-migrated from v1 to v2 on first boot

### New — Administrative Commands

| Command | Description |
|---|---|
| `/vnavnpc` | In-game NPC management (create, action, skin, glow, hand, offhand, status, respawn, tp, remove, list, reload) |
| `/vnavmenu` | Custom menu management (open, add, remove, title, rows, list, reload) |
| `/vn config validate` | Runtime validation of navigator.toml and server registry with typo suggestions |
| `/vn server dry-run` | Validate a server add operation without writing to disk |
| `/vn affinity clean` | Purge expired sticky-session entries |

### Updated — Multi-Proxy Synchronization

Extended from v4.3:

- HMAC-SHA256 signature verification on registration payloads
- Timestamp freshness validation to prevent replay attacks
- Signature deduplication within the freshness window
- Host allowlisting with wildcard support

### Updated — NavigatorAPI

Expanded developer API:

- Access `pluginVersion()`, `server()`, `logger()`, `dataDirectory()`, `config()`, `bedrockHandler()` directly
- No need to cast `NavigatorAPIProvider.get()` to internal implementation classes

### Updated — Language Packs (15 Languages)

Expanded from 7 to 15 fully bundled languages:

| Code | Language | Status |
|---|---|---|
| `en` | English | Updated |
| `ru` | Russian | Updated |
| `es` | Spanish | Updated |
| `fr` | French | Updated |
| `de` | German | Updated |
| `pt_br` | Brazilian Portuguese | Updated |
| `zh_cn` | Simplified Chinese | Updated |
| `ja` | Japanese | **New** |
| `it` | Italian | **New** |
| `ko` | Korean | **New** |
| `nl` | Dutch | **New** |
| `pl` | Polish | **New** |
| `tr` | Turkish | **New** |
| `ar` | Arabic | **New** |
| `hi` | Hindi | **New** |

### Fixed

- **Sign Board GUI authentication**: Password entries are forwarded to the proxy over the bridge channel where rate limiting and session validation apply, resolving failures on proxy-only auth setups.
- **Brute-force protection**: Registration and login attempts are rate limited per account and globally; repeated failures lock the account for five minutes with informative lockout messages.
- **`/vn connect` token bypass**: Connecting to a server manually no longer bypasses menu-token validation; backend menu selections use dedicated authenticated selection tokens.
- **Geo routing for contextual groups**: `geo_distance` groups receive country affinity on the initial join, matching `/lobby` behavior.
- **Party ghost invites**: Invites sent by players who leave, are kicked, or whose party is disbanded are invalidated immediately.
- **Circuit breaker half-open recovery**: If half-open probe requests never complete, the breaker trips back open after the cooldown instead of remaining stuck in half-open state.
- **Server health cache invalidation**: `clearCache()` during `/vn reload` no longer reports servers as nonexistent or leaks raw `CancellationException` to callers.
- **Fail-closed dynamic registration**: A blank `registration_secret` rejects all incoming backend registrations instead of trusting unsigned announcements.
- **Redis subscriber timeouts**: The registration subscriber applies `subscriber_timeout_ms` so stalled connections recover automatically.
- **Auth sessions across reload**: In-memory authentication sessions survive `/vn reload` without forcing players to re-authenticate.
- **`{version}` MOTD placeholder**: Dynamically resolves the real plugin version instead of a hardcoded string.
- **Command permissions**: `/vn help` and `/vn version` pass Velocity's outer permission gate without granting admin privileges.
- **Robustness**: Version parts that overflow an `int` no longer crash `SemanticVersion`; cooldown maps purge expired entries; connection logs are capped; skin cache deduplicates in-flight lookups and cleanly closes HTTP connections.

### Improved

- **Uptime in `/vn status`**: Real-time proxy running time display
- **Menu Validation**: Invalid menu files are skipped with actionable warnings instead of breaking menu loading
- **Configurable Menu Token Timeout**: Adjust session timeout (5–3600 seconds, default 60)
- **Theme Uniformity**: Commands consistently use VelocityNavigator's signature aqua accent with clean gray descriptions

## [4.4.0] - 2026-07-20
- Added optional per-server `description`, `menu_order`, and `show_in_menu` values. Descriptions are available through `{description}`, explicit menu order is shared by all selectors, and hidden entries remain eligible for automatic routing.
- Added selector placeholders for presentation and diagnostics: `{server}` and `{display_name}` resolve to the configured alias, `{server_id}` exposes the raw Velocity server ID, and `{description}` resolves to the shared menu description.
- Added `/vn menu validate` to audit selector server IDs, duplicate display names, slots, material-identifier syntax, and curly-brace placeholders before players open a menu.
- Added global Java inventory styles for `[states.full]`, `[states.draining]`, `[states.offline]`, and `[states.in_game]`, each with optional `material`, `name`, and `lore` overrides.

### Changed

- Inventory navigation controls are now kept in the reserved bottom row for each supported `layout.rows` value. This prevents an automatic server item from occupying a custom control slot and silently hiding that control.
- Bedrock `sort_mode = "name"` now sorts by the displayed alias instead of the raw server ID.
- Explicit nonnegative `menu_order` values are the primary order in Java inventory, Java chat, and Bedrock selectors. Unset values keep the existing candidate order; Bedrock uses its configured `sort_mode` to resolve equal ordering values.
- `show_in_menu = false` removes a server from all three selectors without draining it or removing it from automatic routing.
- Blank or missing aliases fall back to the raw server ID. Selector changes are applied by `/vn reload`; no proxy restart or `navigator.toml` schema migration is required.
- Display aliases are presentation-only. Inventory targets, one-time token allowlists, chat callbacks, Bedrock response mapping, health lookups, and connection requests continue to use the raw registered server ID.
- Java inventory state styles are applied after availability is resolved. A per-server `name` or `lore` remains final; otherwise the matching state template is used before the localized default. For unavailable entries, a per-server `unavailable_material` remains final, followed by the state material and global unavailable fallback. Healthy entries keep their per-server/global normal material.
- `gui.toml` now uses `config_version = 2`; `navigator.toml` remains at config version 8.

### Not included

- Localized per-language display names are not part of 4.4.0. Existing language packs still control shared selector templates and status text.

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

- **Configurable language packs**: explicit `en`, `ru`, `es`, `fr`, `de`, `pt_br`, and `zh_cn` selection plus arbitrary custom codes. Built-in changes rewrite active text; the plugin does not auto-detect player locale.
- **Separate `gui.toml`**: rows, materials, fillers, refresh interval, navigation slots, and per-server fixed slot/material/name/lore overrides.
- **Universal Java inventory selector**: the same 4.3.0 JAR runs in Velocity proxy or Paper/Spigot bridge mode and announces its mode at startup.
- **Pagination, live refresh, and unavailable indicators** for Java inventory menus.
- `/vn bridge status` reports detected backend bridge versions and last-seen times.
- Universal bridge integration tests cover proxy-open, backend-click, navigation, handshake, and proxy-selection flows.
- Exponential backoff with jitter for connection retries: each retry waits progressively longer with a small random jitter to avoid thundering-herd reconnects.
- Player affinity persistence: unexpired sticky-session mappings are now saved to disk and restored across proxy restarts.
- `/vn health` command: consolidated one-shot diagnostics: routing mode, lobby count, circuit-breaker states, drained servers, cache sizes, and affinity entry count in a single screen.
- **HTML operations dashboard**: a separate HTTP server on its own port serving a live lobby table, routing distribution chart, affinity map, config summary, recent routing events, and joins/leaves-since-start counters. Disabled by default and authenticated via a bearer-token login flow.
- `velocity-plugin.json` descriptor: modern Velocity plugin metadata alongside the existing `@Plugin` annotation.
- New reproducible marketplace brand system with a plugin icon, hero banner, social card, nine feature panels, platform-specific listing copy, meaningful alt text, and a visual feature overview in the wiki.
- Built the backend bridge against the Spigot API 1.16.5 baseline without version-specific NMS.

### Changed

- Dashboard documentation now distinguishes the universal `127.0.0.1` loopback example from provider addresses and explains allocated ports, container binds, and browser URLs.
- The official wiki URL is now built in. `startup.wiki_url` is no longer written or accepted as a custom documentation target, and legacy entries are removed during configuration normalization.
- Language documentation now explains structural test coverage, custom packs, and how native speakers can contribute new or improved translations.
- `routing.use_menu_for_lobby` replaces `use_chat_menu_for_lobby`; the old key remains a compatible alias.
- Selector mode/fallback lives in `navigator.toml`, language in `messages.toml`, GUI presentation in `gui.toml`, and managed lobby metadata in `servers.toml`. The plugin/config versions are 4.3.0/v8.
- MiniMessage, classic `&`/`§`, `&#RRGGBB`, and Bungee-style hex colors are accepted in configurable text.
- Server health cache keys are now normalized to lowercase. Mixed-case server names no longer produce duplicate cache entries.
- Consistent hash ring uses a thread-local `MessageDigest` instead of allocating one per lookup. Throughput on `consistent_hash` mode improves by roughly 3–4× under load.
- `RoutePlanner` now builds a `Map<String, LobbyEntry>` once per planning call instead of scanning the lobby list linearly per candidate.
- Config reload now uses a dedicated `ReentrantLock` instead of `synchronized(this)`. Admin commands and event subscribers no longer contend on the same monitor during a reload.
- Bedrock form text stripping now matches legacy color codes case-insensitively. `&C` and `&L` are now stripped alongside `&c` and `&l`.
- Update checks are now silent by default: no startup log line, no periodic console message. `/vn updatecheck` still works for manual checks. Set `update_checker.silent = false` in `navigator.toml` to restore the old behavior.
- **Config version bumped to 8.** Adds the final 4.3.0 advanced-system, managed-server, selector, dashboard, and Redis settings. Older configs are auto-migrated and backed up; network-facing systems remain disabled by default.

### Fixed

- Party commands now expose explicit `/party status` and `/party chat <message>` paths, while `/p <message>` and multiword messages use Velocity raw-command parsing.
- Escaped MiniMessage entities such as `&lt;player&gt;` are no longer mistaken for the legacy `&l` bold color code.
- Dashboard API version data now comes from the plugin metadata instead of a hardcoded prerelease string.
- Redis RESP parsing on the Velocity proxy now enforces line, bulk, array, total-frame, and nesting limits before allocation or recursion.
- Backend Redis lifecycle registration now rejects oversized response lines instead of allowing peer-controlled heap growth.
- Dashboard bearer credentials are accepted only through the `Authorization` header. The browser login keeps the token in memory and does not place it in URLs or persistent browser storage.
- `MetricsService.active()` and `statusLine()` are now `volatile`. Reader threads (Prometheus exporter, `/vn status`) no longer risk seeing stale values after a config reload.
- `PrometheusExporter.start()` now clears the `server` reference if `start()` throws. Previously the next `stop()` call would operate on an unstarted server.
- `UpdateChecker` now uses the non-deprecated `JsonParser.parseString(...)` and the `nextAllowedCheck` read-modify-write is guarded by a `synchronized` block to prevent duplicate 429 retries under concurrency.
- `ConfigManager`'s generated `navigator.toml` header box no longer overflows on the bStats URL line.
- Empty `if` branch in `applyLoadedConfiguration` removed: round-robin reset logic is now a single positive check.

### Internal

- Stripped ~110 inline `//` comments across the source tree. Public API javadoc and license headers are preserved.
- Removed unused imports, unused constructors (`Config.UpdateCheckerSettings(UpdateChannel)`, `ServerCandidate` 4-arg, etc.) and `SemanticVersion.raw()`.
- Tightened a handful of synchronized blocks to lock-free alternatives where safe.

---

## [4.2.0] - 2026-05-30

### Added

- **Embedded Prometheus exporter & admin panel**: built-in HTTP server exposing real-time metrics (`/metrics`) on routing distributions, pings, circuit breaker statuses, and connection events.
- **Grafana dashboard setup command**: `/vn setup grafana` generates a pre-configured Grafana telemetry dashboard JSON file.
- **Interactive selector menus**: native Bedrock Form GUI (via Geyser/Floodgate integration) and a clickable Java chat selector menu with hover tooltips showing health and latencies.
- **Ping-based routing strategy (`latency`)**: selects the server with the lowest ping latency.

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

- **Bedrock/Geyser player support**: soft-dependency integration with Geyser and Floodgate. Strips advanced Kyori Component formatting (gradients, hover, click actions) so messages render on Bedrock clients, and maps Java UUIDs for player affinity tracking.
- **First-run experience**: console welcome dashboard on fresh installs. On plugin upgrades, a release notes digest is printed.
- **`/vn servers` diagnostics command**: paginated status dashboard for all configured lobbies, showing player count and capacity, circuit breaker state, and drain status.
- **Configurable dashboard colors**: customizable status tags and colors for `/vn servers`, supporting hex, RGB, and MiniMessage styling in `navigator.toml`.
- **Typo auto-correction and Levenshtein validation**: typo detection on config load and reload using Levenshtein distance (e.g. suggesting `"least_players"` for `"leadt_players"`).
- **Self-documenting configuration keys**: `navigator.toml` comments are populated on generation or migration, linking to the relevant section anchor on the wiki.
- **Automatic legacy color code converter**: matches and converts the standard `&` and `§` legacy formatting codes to MiniMessage on load. Supports `"auto"` (with one-time warnings), `"minimessage"`, and `"legacy"` modes.
- **Periodic update checker with backoff**: recurring scheduled update checks with exponential backoff on HTTP 429 errors (scaling up to 4 hours).
- **Empty lobby routing fallbacks**: configurable degradation strategies (`"disconnect"` or `"fallback_server"`) when all primary lobby options are offline or circuit-broken.
- **Permission default change**: the `/lobby` command default permission is now `"none"`, so it works without explicit configuration. Existing configs are preserved on migration.

---

## [4.0.0] - 2026-05-01

### Added

- **Power of Two selection algorithm** (`power_of_two`): picks two random candidates and selects the one with fewer players. Near-optimal distribution at O(1) cost.
- **Weighted Round Robin selection algorithm** (`weighted_round_robin`): interleaved WRR that distributes traffic proportionally to server weights.
- **Least Connections selection algorithm** (`least_connections`): selects the server with the lowest exponential moving average (EMA) of connection load and rate.
- **Consistent Hash selection algorithm** (`consistent_hash`): deterministic player-to-server mapping using a consistent hash ring with 150 virtual nodes and SHA-256 hashing. Provides session affinity.
- **LobbyEntry format**: servers can be configured as plain strings or inline tables with `max_players` and `weight` fields. Backward compatible with plain strings.
- **Per-lobby max-player cap**: servers at their `max_players` capacity are excluded from routing.
- **Circuit Breaker**: automatic server failure detection with a CLOSED → OPEN → HALF_OPEN state machine. Unhealthy servers are excluded from routing until they recover.
- **Server Drain Mode**: `/vn drain <server>`, `/vn undrain <server>`, `/vn drain status` commands for graceful server maintenance.
- **Connection Retry with Fallback**: automatic retry on connection failure with configurable `max_retries`. Shows a retry message with `<attempt>/<max>` placeholders.
- **Per-Group Selection Mode Override**: contextual routing groups can specify their own `mode`, overriding the global `selection_mode`.
- **Fallback Priority Chain**: ordered fallback groups when a contextual group's servers are all unavailable.
- **Player Affinity Routing**: sticky sessions with configurable `stickiness` probability (0.0–1.0). Players tend to return to their previous lobby.
- **Graceful Degradation**: when all health checks fail, falls back to a configured degradation mode (default: `random`) instead of showing "No lobby found".
- **Geo-Based Routing (experimental)**: stub implementation for geo-based lobby routing using MaxMind GeoLite2 Country database.
- **Routing Metrics API**: new `NavigatorAPI` methods: `getRoutingDistribution()`, `getHealthCheckLatencies()`, `getCircuitBreakerStatuses()`.
- **Connection Rate Tracking**: sliding window (60-second) connection rate tracker used by `least_connections` mode.
- **Server Load Tracking**: EMA-based server load tracker used by `least_connections` mode.
- **Routing Stats**: per-server connection counts with 60-second reset, shown in `/vn status`.
- **Enhanced `/vn status` dashboard**: now shows circuit breaker status, drained servers, and routing distribution.
- **`/vn updatecheck` command**: manually check for updates (replaces the recurring auto-update check).
- **Startup update notification**: one-time update check 5 seconds after proxy start.
- **Admin join update notification**: players with `velocitynavigator.admin` permission are notified in-game when they join if an update is available. Controlled by `notify_admins_on_join` config.
- **`<player>` placeholder**: new placeholder available in all message templates.
- **`<attempt>` and `<max>` placeholders**: available in `messages.retrying`.
- **`messages.retrying` config**: new message template for connection retry notifications.
- **`notify_on_startup` config**: suppress startup update notification.
- **`notify_admins_on_join` config**: enable/disable in-game admin update notification on join.
- **Health check cache purge**: expired cache entries are purged each 60 seconds.
- **`getCachedOnlineServers()` method**: synchronous cached player count access for initial join balancing (replaces blocking `.join()` call).
- **Config version field**: `CURRENT_VERSION` set to 4. Auto-migration from v3 configs with `.bak` backup.

### Changed

- **Removed `.join()` blocking call** in `onPlayerChooseInitialServer`: replaced with synchronous cache lookup. Falls through to Velocity's built-in try list on cold start.
- **Round-robin state only resets when lobby topology changes**: `applyLoadedConfiguration()` compares the previous and current lobby lists before resetting.
- **Contextual groups**: changed from `Map<String, List<LobbyEntry>>` to `Map<String, GroupConfig>` where `GroupConfig` contains `servers` and optional `mode`.
- **UpdateChecker**: removed recurring schedule; now runs a single check on startup. Removed `enabled`, `notifyConsole`, `startupDelaySeconds` fields.
- **ConfigManager**: reads both plain strings and inline tables for lobby entries (backward compatible). Writes inline tables when `max_players` or `weight` is non-default.
- **MessageFormatter**: added `player`, `attempt`, `max` to allowed placeholders.
- **`noLobbyFound` message**: now includes the `(<reason>)` placeholder by default.
- **`ServerCandidate` record**: now includes `effectiveWeight` and `emaLoad` fields.
- **`RouteDecision`**: provides an ordered candidate list for retry fallback.

### Fixed

- **Blocking `.join()` in event handler**: `onPlayerChooseInitialServer` no longer blocks the event loop with `.join()` calls. Uses cached data synchronously instead.
- **Round-robin reset on each reload**: the round-robin counter is now only reset when the lobby topology changes, preventing unnecessary redistribution on config reload.
- **Health check cache memory leak**: expired cache entries are now purged each 60 seconds.
- **Permission node inconsistency**: `velocitynavigator.bypasscooldown` now also checks `velocitynavigator.bypass.cooldown` for consistency.

### Deprecated

- **`velocitynavigator.bypasscooldown` permission**: use `velocitynavigator.bypass.cooldown` instead. The legacy name still works as a fallback.

### Removed

- **`update_checker.enabled` config field**: the update checker runs on startup.
- **`update_checker.notifyConsole` config field**: update notifications are logged to console.
- **`update_checker.startupDelaySeconds` config field**: startup delay is fixed at 5 seconds.
- **Recurring update check schedule**: replaced by a one-time startup check and `/vn updatecheck`.

---

## [3.0.0]: 2026-04-10

### Added

- **Initial Join Balancing**: Players are load-balanced the moment they connect to the proxy via `PlayerChooseInitialServerEvent`.
- **Developer API**: `NavigatorAPI` and `NavigatorAPIProvider` for third-party plugin integration.
- **Three Routing Modes**: `least_players`, `round_robin`, and `random` selection algorithms.
- **Contextual Routing**: Route players to game-specific lobbies based on which server they are leaving.
- **Self-Documenting Config**: `navigator.toml` generates with inline comments explaining each setting.

### Changed

- **Async Health Checks**: Ping candidate lobbies before routing with configurable timeout and caching.
- **Ping Coalescing**: Multiple simultaneous `/lobby` requests share the same `CompletableFuture` ping.
- **Pre-Execution Cooldown Locking**: Cooldown is applied before command execution to prevent macro abuse.
- **Graceful Failover**: Falls back to default lobby pool when all contextual lobbies are offline.

### Added (Telemetry & Updates)

- **bStats Integration**: Anonymous usage telemetry (plugin ID: 28341).
- **Modrinth Update Checker**: Automatic version checking with configurable release channel.

### Added (Admin Tools)

- `/vn reload`: Hot-reload `navigator.toml`.
- `/vn status`: View runtime status.
- `/vn version`: Check installed vs. latest version.
- `/vn debug player <name>`: Preview routing decision.
- `/vn debug server <name>`: Inspect server health.
- Full tab-completion for all admin commands.

### Added (Configuration)

- **Automatic Migration**: Migration from v1/v2 configs with backup generation.
- **Field-Level Validation**: Invalid config values are corrected with warnings.
- **MiniMessage Support**: All player-facing messages support MiniMessage rich text formatting.

---

## [2.0.0]: Legacy

Previous version with basic lobby routing. Superseded by v3.0.0.

---

## [1.0.0]: Legacy

Initial release with single-server lobby navigation.

---

*VelocityNavigator is developed and maintained by [DemonZ Development](https://github.com/DemonZ-Development).*
