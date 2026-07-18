# Java and Bedrock Selectors

![VelocityNavigator player selectors](headers/java-and-bedrock-selectors.png)

VelocityNavigator can show a Java inventory, a native Bedrock form, or a clickable chat list. All three use the same lobby availability rules, so an offline, full, or drained server cannot be selected just because it appears in a menu.

## What players see

### Java Edition

![VelocityNavigator Java inventory running in Minecraft](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/java-inventory-selector.png)

The inventory above is a real Java Edition capture from the Paper backend bridge.

### Bedrock Edition

![VelocityNavigator Bedrock lobby form running in Minecraft](https://raw.githubusercontent.com/DemonZ-Development/VelocityNavigator/main/assets/bedrock-selector.png)

The form above is a real Bedrock capture through Geyser and Floodgate.

## Choose the Java selector

In `navigator.toml`:

```toml
[routing]
use_menu_for_lobby = true

[routing.java_menu]
type = "inventory"
fallback_to_chat = true
```

`inventory` uses the optional backend bridge. `chat` always uses clickable text. Players can also use `/lobby menu` to open the selector directly.

## Java inventory setup

1. Put the same VelocityNavigator JAR on the Velocity proxy and the Paper/Spigot backend.
2. Start both servers.
3. Join the backend through Velocity once.
4. Run `/vn bridge status` on the proxy.
5. Run `/lobby menu` while connected to that backend.

If the bridge is missing and `fallback_to_chat = true`, the player receives the chat selector instead.

## Inventory layout

`gui.toml` controls the inventory:

```toml
[layout]
rows = 6
default_material = "COMPASS"
unavailable_material = "BARRIER"
fill_empty_slots = true
filler_material = "GRAY_STAINED_GLASS_PANE"
refresh_seconds = 5

[controls]
previous_slot = 45
refresh_slot = 49
next_slot = 53
previous_material = "ARROW"
refresh_material = "CLOCK"
next_material = "ARROW"
```

`rows` accepts `2` through `6`. Minecraft chest inventories always have nine columns, so the available sizes are 18, 27, 36, 45, or 54 slots. VelocityNavigator reserves the bottom row for previous, refresh, and next controls; the other rows hold server entries. For example, `rows = 4` creates a 36-slot menu with 27 automatic server slots per page.

| `rows` | Total slots | Automatic servers per page | Previous | Refresh | Next |
|---:|---:|---:|---:|---:|---:|
| 2 | 18 | 9 | 9 | 13 | 17 |
| 3 | 27 | 18 | 18 | 22 | 26 |
| 4 | 36 | 27 | 27 | 31 | 35 |
| 5 | 45 | 36 | 36 | 40 | 44 |
| 6 | 54 | 45 | 45 | 49 | 53 |

When changing `rows`, use the matching control slots from this table. Save `gui.toml`, run `/vn reload`, and confirm the result with `/vn menu validate`. Controls configured outside the reserved bottom row are moved to these safe defaults and reported by the validator.

Control and fixed server slots use zero-based slot numbers. Keep every configured slot below `rows × 9`. Invalid, duplicate, or non-bottom-row control slots are moved to safe positions in the bottom row, while invalid server slots fall back to automatic placement.

Per-server overrides can set shared selector metadata plus Java-inventory presentation. The table key must remain the real server ID from `velocity.toml`, but `display_name` may contain spaces and `description` is available to all selector templates. `menu_order` and `show_in_menu` also apply to Java inventory, Java chat, and Bedrock.

```toml
config_version = 2

[servers]
"lobby1" = { display_name = "Main Lobby 1", description = "Events, portals, and network help", menu_order = 10, show_in_menu = true, slot = 10, material = "NETHER_STAR", unavailable_material = "", name = "<gradient:#55FFFF:#FFFFFF><bold>{server}</bold></gradient>", lore = ["<gray>{description}</gray>", "<gray>Players:</gray> <white>{players}/{max_players}</white>", "<dark_gray>Target: {server_id}</dark_gray>", "&#55FF88Click to connect"] }
"holding" = { display_name = "Holding Server", description = "", menu_order = -1, show_in_menu = false, slot = -1, material = "", unavailable_material = "", name = "", lore = [] }
```

These fields are presentation-only. The inventory target, one-time token allowlist, chat callback, Bedrock button response, and final connection request still use the raw `lobby1` ID. A blank or omitted `display_name` falls back to that raw ID; an omitted description is empty. Save `gui.toml` and run `/vn reload` to apply changes.

Nonnegative `menu_order` values are the primary selector order, lowest first. `-1` leaves the value unset. Equal/unset Java and chat entries retain candidate order, while Bedrock uses `sort_mode` for ties. A fixed Java `slot` still controls the item's physical cell; leave `slot = -1` for automatic placement. `show_in_menu = false` hides an entry from all three selectors without changing automatic lobby routing.

The Java-only `name` field is the final inventory item-name template and takes precedence over `display_name`. Leave `name = ""` to use the localized `[menus.inventory].item_name` template with the alias. A nonblank `name` can still use `{server}`, `{display_name}`, and `{server_id}`.

Display aliases do not have to be unique because they are never used as connection targets. Avoid duplicates anyway: two identical labels are difficult for players to distinguish even though each button still routes safely to its own raw ID. `/vn menu validate` reports ambiguous labels along with unknown IDs, slot mistakes, malformed material identifiers, and unsupported curly-brace placeholders.

## State presentation in the Java inventory

Java inventory items can change when a backend is full, draining, offline, or reports the `IN_GAME` lifecycle marker:

```toml
[states.full]
material = "RED_CONCRETE"
name = "<red><bold>{server}</bold> · {status}</red>"
lore = ["<gray>{description}</gray>", "<red>Try another lobby.</red>"]

[states.draining]
material = "YELLOW_CONCRETE"
name = "<yellow><bold>{server}</bold> · {status}</yellow>"
lore = ["<gray>{description}</gray>", "<yellow>Closed for maintenance.</yellow>"]

[states.offline]
material = "BARRIER"
name = "<gray><bold>{server}</bold> · {status}</gray>"
lore = ["<gray>{description}</gray>", "<red>Currently offline.</red>"]

[states.in_game]
material = "ENDER_EYE"
name = "<gold><bold>{server}</bold> · {status}</gold>"
lore = ["<gray>{description}</gray>", "<gold>Game in progress.</gold>"]
```

These are the default materials; names and lore may be customized. A per-server `name` or `lore` is final. Otherwise the matching state template is used, then the localized `[menus.inventory]` default. An unavailable entry first uses its per-server `unavailable_material`, then the state material, then the global unavailable material. Healthy entries keep their per-server/global normal material. A routable `IN_GAME` entry uses its state material before its normal-material fallback. `IN_GAME` styling follows the reported marker even when routing rules allow joining that state.

If conditions overlap, the effective state is chosen in this order: offline/unhealthy circuit, draining, `IN_GAME`, full, then healthy.

State materials are Java-inventory presentation only. Chat and Bedrock can show the same effective state through `{status}` and use `{description}` in their own templates.

For focused examples, placeholder behavior, precedence rules, and troubleshooting, see [Selector Customization](Server-Display-Names).

## Bedrock form

Native Bedrock forms require Geyser and Floodgate on the network. In `navigator.toml`:

```toml
[bedrock]
enabled = true
auto_detect = true
strip_advanced_formatting = true
affinity_use_java_uuid = true
use_gui_for_lobby = true
```

Then adjust the form in `gui.toml`:

```toml
[bedrock]
enabled = true
fallback_to_chat = true
sort_mode = "routing"
max_buttons = 100
show_players = true
show_max_players = true
show_ping = true
show_status = true
title = ""
content = ""
button_format = ""
```

Blank text values use the active language pack. `sort_mode` accepts `routing`, `name`, or `players`. Name sorting uses `display_name`, with the raw server ID as the fallback. An explicit `menu_order` is always primary; `sort_mode` resolves equal or unset ordering values.

Bedrock forms do not use Java inventory rows. `max_buttons` limits how many server buttons can be included, while the Bedrock client chooses the form's physical size and layout.

## Text and colors

Default chat, inventory, and Bedrock text lives in `messages.toml`. Java inventory titles, item names, lore, and controls are under `[menus.inventory]`:

```toml
[menus.inventory]
title = "<gradient:#55FFFF:#FFFFFF><bold>Choose a Lobby</bold></gradient>"
item_name = "&#55FFFF&l{server}"
previous = "<yellow>Previous Page</yellow>"
next = "<yellow>Next Page</yellow>"
refresh = "<aqua>Refresh</aqua>"
item_lore = ["&7Players: &f{players}/{max_players}", "&eClick to connect"]
```

MiniMessage tags, named colors, gradients, `&` and `§` codes, `&#RRGGBB`, and Bungee-style hex codes are supported. Per-server `name` and `lore` values in `gui.toml` use the same formatting.

In selector text, `{server}` and `{display_name}` both mean the player-facing alias, `{server_id}` means the exact registered Velocity ID, and `{description}` is the shared per-server description. Player count, capacity, status, and latency remain available through `{players}`, `{max_players}`, `{status}`, `{status_color}`, and `{ping}`. These menu-specific meanings do not change connection-message placeholders outside the selectors.

Bedrock text can also be changed, but native forms do not render every advanced Java style. With `strip_advanced_formatting = true`, unsupported MiniMessage formatting is removed before the form is sent. See [Language Packs](Language-Packs) for translations, placeholders, and formatting.

## Common problems

- **Inventory falls back to chat:** join the backend once and check `/vn bridge status`.
- **Bedrock player sees chat:** confirm Geyser/Floodgate detection and both Bedrock enable switches.
- **An icon is invalid:** use a material available on the backend version; the bridge uses the configured fallback material when needed.
- **Too many servers:** the Java menu adds pages automatically; Bedrock uses `max_buttons`.
- **A hidden server still receives automatic routes:** `show_in_menu` affects selectors only; remove the server from its pool or drain it to stop routing.
- **A state style does not appear:** clear any per-server `name`/`lore` override, compare `/vn servers` health, drain, circuit, and capacity signals, verify the backend lifecycle marker for `IN_GAME`, and run `/vn menu validate`.
