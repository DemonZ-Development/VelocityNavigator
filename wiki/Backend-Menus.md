# Backend YAML Menus

VelocityNavigator has two different inventory systems:

| Menu | Opened with | Configured in |
|---|---|---|
| Proxy lobby selector | `/lobby menu` | Proxy `gui.toml` and `messages.toml` |
| Backend custom menu | `/vnavmenu open` or an NPC `menu:<name>` target | Backend `plugins/VelocityNavigator/menus/*.yml` |

Use the proxy selector when you want live health, player counts, pagination, and the normal routing pool. Use a backend YAML menu when you want a hand-built hub menu with submenus or player commands.

## Install It

1. Put the same VelocityNavigator JAR on Velocity and the Paper/Spigot/Folia backend where the menu will open.
2. Restart both processes.
3. Confirm the backend log says `BACKEND GUI BRIDGE mode`.
4. Check this backend setting:

   ```yaml
   menus_enabled: true
   ```

5. Join the backend and run:

   ```text
   /vnavmenu list
   /vnavmenu open main
   ```

The first startup creates example `main.yml`, `games.yml`, and `lobbies.yml` files. Their example server names may not exist on your network; replace them before giving the menu to players.

## Make a Menu

Create `plugins/VelocityNavigator/menus/network.yml`:

```yaml
title: "&b&lNETWORK MENU"
rows: 3

items:
  main_lobby:
    slot: 11
    target: "lobby-1"
    material: "NETHER_STAR"
    name: "&a&lMain Lobby"
    lore:
      - "&7Return to the main lobby"
      - "&eClick to connect"

  games:
    slot: 13
    target: "menu:games"
    material: "DIAMOND_SWORD"
    name: "&e&lGames"
    lore:
      - "&7Open another YAML menu"

  spawn:
    slot: 15
    target: "cmd:/spawn"
    material: "COMPASS"
    name: "&bSpawn"
    lore: []
```

Run:

```text
/vnavmenu reload
/vnavmenu open network
```

You should see a three-row menu titled `NETWORK MENU`.

## Targets

| Target | Result |
|---|---|
| `lobby-1` | Sends the player to that exact Velocity server |
| `menu:games` | Opens `menus/games.yml` |
| `cmd:/spawn` | Makes the player run `/spawn` |

Server targets must match Velocity's `[servers]` names. A server click travels through the plugin-message bridge and is validated by Velocity before connection.

## Slots and Rows

- `rows` must be from `2` to `6`.
- Slots start at `0`.
- A three-row menu has slots `0` through `26`.
- Each item needs a unique slot, target, material, and nonblank name.
- Lore is optional and supports up to 16 lines.
- Materials use Bukkit names such as `PAPER`, `COMPASS`, or `NETHER_STAR`.

## Commands

Backend administrators need `velocitynavigator.admin`.

| Command | Purpose |
|---|---|
| `/vnavmenu open <menu>` | Opens a menu for yourself |
| `/vnavmenu open <player> <menu>` | Opens a menu for another player |
| `/vnavmenu list` | Lists loaded menus |
| `/vnavmenu reload` | Reloads all YAML menu files |
| `/vnavmenu add <menu> <slot> <target> <material> <name> [lore...]` | Adds or replaces an item |
| `/vnavmenu remove <menu> <slot>` | Removes an item |
| `/vnavmenu title <menu> <title...>` | Changes the title |
| `/vnavmenu rows <menu> <2-6>` | Changes the row count |

In the `add` command, separate lore lines with `|`.

## Open a Menu from an NPC

Create an NPC whose target is `menu:network`:

```text
/vnavnpc create selector menu:network &b&lSERVER SELECTOR
```

Clicking it opens `network.yml` on that backend.

## Troubleshooting

**The menu is not listed.**

Check the backend log after `/vnavmenu reload`. A file is rejected when rows, slots, required values, or lore limits are invalid.

**The inventory opens but a server click does nothing.**

Confirm the player has `velocitynavigator.use`, the server exists in Velocity, the proxy also has the same plugin version, and the player connected through Velocity.

**I expected live health and player counts.**

Those belong to the proxy lobby selector configured in `gui.toml`. Backend YAML menus are intentionally static.
