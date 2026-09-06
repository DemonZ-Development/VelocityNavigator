# Backend NPC Server Selectors

VelocityNavigator includes a native NPC server selector engine that runs on **Paper, Spigot, and Folia** backend servers. Place the same universal JAR on your lobby backends to spawn interactive NPCs that route players, open menus, or run commands.

## How It Works

The renderer is picked automatically at startup:

- **Player-model NPCs** (Mojang-mapped Paper 1.20.5+ runtimes, including Folia): the backend sends fake-player spawn packets, so every player sees a real player model with a working skin, held items, and a full-size click hitbox — no armor stands involved. Clicks are captured through a small Netty pipeline handler and validated against the player's current view and interaction range. The backend log confirms this mode with `player-model renderer active`.
- **Armor stand NPCs** (older or versioned CraftBukkit/Spigot runtimes): an arms-free, base-plate-free armor stand wearing the resolved player head plus an Interaction entity hitbox. The log says `armor stand renderer active`.

Both modes share the same hologram system:

- **Hologram lines** — `TextDisplay` entities (1.19.4+, billboarded so they face every player) or ArmorStand name tags (1.17–1.19.3) floating above the NPC

NPCs are defined in YAML files under `plugins/VelocityNavigator/npcs/`. Each file can contain multiple NPC definitions under a top-level `npcs:` key. NPCs whose world is not loaded yet are kept in memory and spawn automatically once the world loads.

## Quick Start

1. Place the same VelocityNavigator JAR on Velocity and the lobby backend.
2. Restart both processes. Confirm that the backend log says `BACKEND GUI BRIDGE mode`.
3. Ensure `npcs_enabled: true` in `plugins/VelocityNavigator/config.yml`.
4. Give yourself `velocitynavigator.admin` on the backend.
5. Stand where you want the NPC and run:
   ```
   /vnavnpc create main_hub &b&lSERVER SELECTOR
   /vnavnpc action main_hub menu main
   ```
6. Optionally set the displayed player head:
   ```
   /vnavnpc skin main_hub Steve
   ```

Run `/vnavnpc list` to confirm it was saved, then click it as a normal player. If a server action does nothing, confirm the same JAR is running on Velocity and that the player has `velocitynavigator.use`.

> **Command forms:** Use `/vnavnpc <subcommand> ...` directly or `/vnav npc <subcommand> ...` through the backend command root. `/vn` is reserved for Velocity proxy administration.

## YAML File Structure

By default, NPCs are saved to `plugins/VelocityNavigator/npcs/default.yml`:

```yaml
npcs:
  main_hub:
    world: world
    x: 0.5
    y: 64.0
    z: 0.5
    yaw: 0.0
    pitch: 0.0
    target_server: "menu:main"
    sneak_target_server: "lobby-1"    # optional — triggered on sneak+click
    skin: "Steve"
    hand_item: "COMPASS"              # optional — item in main hand
    offhand_item: "SHIELD"            # optional — item in offhand
    lines:
      - "&b&lSERVER SELECTOR"
      - "&7Click to browse servers"
      - "&8Sneak+click to join lobby"
    look_at_player: true
    enabled: true
```

Fields:

| Field | Default | Description |
|---|---|---|
| `world` | (required) | World name where the NPC spawns |
| `x`, `y`, `z` | (required) | Spawn coordinates |
| `yaw`, `pitch` | `0.0` | Facing direction |
| `target_server` | `"lobby"` | Primary click target |
| `sneak_target_server` | (none) | Alternative target when player sneaks while clicking |
| `skin` | `""` | Mojang username for skin texture |
| `hand_item` | (none) | Material name for item held in main hand |
| `offhand_item` | (none) | Material name for item held in offhand |
| `lines` | (auto-generated) | Hologram text lines (supports `&` color codes) |
| `look_at_player` | `true` | Whether the NPC rotates to face nearby players |
| `glowing` | `false` | Enables the glowing outline |
| `glow_color` | `"GOLD"` | Team color used for the outline when `glowing: true` |
| `enabled` | `true` | Whether the NPC spawns on server start |

Older files may contain a `pose` key. Poses were never rendered and the key is now ignored on load and dropped on save.

## Permissions

- `velocitynavigator.use` — Required for players to click/interact with backend NPCs (granted by default to all players if permissions manager is not restricting it).
- `velocitynavigator.admin` — Required to execute `/vnavnpc` (or `/vnavnpc`) management commands.

## Click Actions

The `target_server` and `sneak_target_server` fields accept four action formats:

| Format | Example | Behavior |
|---|---|---|
| **Server name** | `lobby-1` | Routes the player to that server |
| **Menu reference** | `menu:main` | Opens the named backend YAML menu |
| **Player command** | `cmd:/spawn` | Makes the clicking player run the command |
| **Conditional Routing** | `action:cond(perm=velocitynavigator.vip?server:vip-lobby|server:lobby)` | Evaluates condition and routes accordingly (`perm=...` or `bedrock`) |

Sneak+click uses `sneak_target_server` when set; otherwise, it falls back to `target_server`.

### Cooldown

A 250ms per-NPC cooldown prevents accidental double-clicks. Extra clicks inside the cooldown window are ignored silently.

## NPC Equipment

NPCs can hold items in their main hand and offhand. Set them with commands or in YAML:

```
/vnavnpc hand main_hub COMPASS
/vnavnpc offhand main_hub SHIELD
```

Any valid Bukkit material name works. To clear an item, set `hand_item: ""` or `offhand_item: ""` in YAML and run `/vnavnpc reload`.

## Commands

All NPC commands require the backend `velocitynavigator.admin` permission. The same operations are available under `/vnav npc`, `delete` is accepted as an alias for `remove`, and all commands support tab completion for subcommands, NPC IDs, materials, glow colors, and boolean flags.

| Command | Description |
|---|---|
| `/vnavnpc create <id> [display name...]` | Spawns a visual NPC at your location |
| `/vnavnpc action <id> <server\|menu\|command\|none> [value...]` | Sets or clears the normal click action |
| `/vnavnpc remove [id]` | Despawns and deletes an NPC (uses the nearest NPC within 5 blocks when omitted) |
| `/vnavnpc move [id]` | Moves the NPC to your current location (uses the nearest NPC when omitted) |
| `/vnavnpc skin [id] [skinUsername]` | Sets the Mojang skin (omitted arguments default to your skin and the nearest NPC; console must pass both arguments) |
| `/vnavnpc sneak <id> <target\|none>` | Sets or clears the sneak-click alternative action |
| `/vnavnpc hand <id> <material\|none>` | Sets or clears the item in the main hand |
| `/vnavnpc offhand <id> <material\|none>` | Sets or clears the item in the offhand |
| `/vnavnpc glow <id> <on\|off> [color]` | Toggles the glowing outline with an optional team color |
| `/vnavnpc enable <id>` / `/vnavnpc disable <id>` | Spawns or despawns without deleting the definition |
| `/vnavnpc title <id> set <index> <text...>` | Sets a hologram line at a specific index |
| `/vnavnpc title <id> add <text...>` | Appends a hologram line |
| `/vnavnpc title <id> remove <index>` | Removes a hologram line |
| `/vnavnpc title <id> clear` | Removes all hologram lines |
| `/vnavnpc toggle lookatplayer <id> [true\|false]` | Toggles proximity head tracking |
| `/vnavnpc tp [id]` | Teleports you to the NPC (uses the nearest NPC when omitted) |
| `/vnavnpc list` | Lists all NPCs with targets, state, and location |
| `/vnavnpc reload` | Reloads all NPCs from disk |

NPC IDs may contain letters, numbers, dashes, and underscores only (32 characters max). Sneak targets, equipment items, and glow settings are saved back to the YAML file each NPC was loaded from.

## Skins

Skins are fetched from the Mojang API once per username and cached to `plugins/VelocityNavigator/skins/`. The cache is permanent until the file is deleted, and failed lookups are retried only after a short cooldown so the API is never hammered.

In player-model mode the NPC always renders as a player: it appears with the default skin and swaps to the resolved skin as soon as Mojang answers. In armor stand mode there is nothing sensible to display without textures, so the body stays hidden until a skin resolves.

## Proximity Head Tracking

When `look_at_player` is enabled, the NPC rotates to face the nearest player within 8 blocks. The polling interval is configured via `npc_look_interval_ticks` in `config.yml` (default: `20` ticks, once per second).

## Using Other NPC Plugins

VelocityNavigator can run beside Citizens, ZNPCsPlus, FancyNpcs, DeluxeMenus, and similar plugins, but it does not register a `/vnav connect` command for them.

Use `/lobby` or `/lobby menu` as a third-party click action when normal lobby selection is enough. Use the native `/vnavnpc` target when the click must send the player to one exact backend.

## Folia Support

All NPC spawning, despawning, proximity queries, and hologram changes use Folia's location or entity schedulers when Folia is detected. Viewer reconciliation is dispatched to each player's entity scheduler before reading location or connection state. These schedulers fall back to `Bukkit.getScheduler()` on standard Paper/Spigot servers.
