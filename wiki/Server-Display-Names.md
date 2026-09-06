# Selector Customization

You configure each backend to keep a stable technical ID while using player-friendly metadata for Java inventory, Java chat, and Bedrock entries. You add a display name and description, choose menu order, hide internal servers from selectors, and style Java inventory items by backend state.

You change menus only with these settings. You configure routing, health checks, secure inventory tokens, callbacks, and connection requests to use the raw server ID.

## Complete example

Keep the real IDs in Velocity's `velocity.toml`:

```toml
[servers]
lobby1 = "127.0.0.1:25566"
lobby2 = "127.0.0.1:25567"
staff-lobby = "127.0.0.1:25568"
```

Configure their selector presentation in the proxy's `plugins/velocitynavigator/gui.toml`:

```toml
config_version = 2

[servers]
"lobby1" = { display_name = "Main Lobby 1", description = "Events, portals, and network help", menu_order = 10, show_in_menu = true, slot = -1, material = "NETHER_STAR", unavailable_material = "", name = "", lore = [] }
"lobby2" = { display_name = "Main Lobby 2", description = "A quieter starting lobby", menu_order = 20, show_in_menu = true, slot = -1, material = "COMPASS", unavailable_material = "", name = "", lore = [] }
"staff-lobby" = { display_name = "Staff Lobby", description = "", menu_order = -1, show_in_menu = false, slot = -1, material = "", unavailable_material = "", name = "", lore = [] }

[states.full]
material = "REDSTONE_BLOCK"
name = "<red><bold>{server}</bold></red>"
lore = ["<gray>{description}</gray>", "<red>This lobby is full.</red>"]

[states.draining]
material = "YELLOW_CONCRETE"
name = "<yellow><bold>{server}</bold></yellow>"
lore = ["<gray>{description}</gray>", "<yellow>Temporarily closed for maintenance.</yellow>"]

[states.offline]
material = "BARRIER"
name = "<gray><bold>{server}</bold></gray>"
lore = ["<gray>{description}</gray>", "<red>This lobby is offline.</red>"]

[states.in_game]
material = "CLOCK"
name = "<gold><bold>{server}</bold></gold>"
lore = ["<gray>{description}</gray>", "<gold>A game is already in progress.</gold>"]
```

You configure state values with materials supported by the Minecraft version running the backend bridge.

After saving, run:

```text
/vn reload
/vn menu validate
```

`navigator.toml` uses config version 9. The menu-only `gui.toml` schema is version 2.

## Per-server fields

You match each key under `[servers]` to a registered server in `velocity.toml`.

| Field | Default | Applies to | Purpose |
|---|---:|---|---|
| `display_name` | Raw server ID | All selectors | Friendly label; spaces and capitalization are allowed |
| `description` | Empty | All selector templates | Shared text exposed through `{description}` |
| `menu_order` | `-1` | All selectors | Nonnegative values sort first, from lowest to highest; `-1` leaves the order unset |
| `show_in_menu` | `true` | All selectors | `false` hides the entry from Java inventory, Java chat, and Bedrock forms only |
| `slot` | `-1` | Java inventory | Fixed zero-based inventory slot; `-1` uses automatic placement |
| `material` | Empty | Java inventory | Healthy item material override |
| `unavailable_material` | Empty | Java inventory | Final per-server material for an unavailable entry; leave empty to inherit state styles |
| `name` | Empty | Java inventory | Final per-server item-name template |
| `lore` | Empty list | Java inventory | Final per-server lore template |

You fall back to the raw ID for missing or blank `display_name`s. You remove leading and trailing whitespace while retaining spaces inside the label. You resolve missing descriptions to an empty value.

## Menu order and fixed slots

You set the primary order with nonnegative `menu_order`. You display lower values first. You keep existing candidate order with `-1`.

You control the physical cell in Java inventories with nonnegative `slot`s. You use `menu_order` for automatic placement, pagination, and consistency. You use `slot` when an item must occupy a specific cell.

You resolve equal or unset `menu_order`s in Bedrock using `bedrock.sort_mode`. You retain candidate order for ties in Java and chat.

## Hiding an entry from menus

You set `show_in_menu = false` for servers that players reach automatically but should not choose directly.

You do not drain, unregister, disable, or remove the server from a routing pool with this flag. You use drain mode or routing configuration to stop the server from receiving automatic traffic.

## Placeholders

Selector templates accept both `{placeholder}` and `<placeholder>` forms:

| Placeholder | Example value |
|---|---|
| `{server}` | `Main Lobby 1` |
| `{display_name}` | `Main Lobby 1` |
| `{server_id}` | `lobby1` |
| `{description}` | `Events, portals, and network help` |
| `{players}` | Current player count |
| `{max_players}` | Configured capacity or `∞` |
| `{status}` | Current availability/lifecycle state |
| `{status_color}` | Configured status color |
| `{ping}` | Cached latency |

You include descriptions in Java item lore, Java hover text, and Bedrock button templates. You treat placeholders in display names or descriptions as ordinary text without expanding them recursively.

## State-aware Java inventory items

You configure optional state tables that accept `material`, `name`, and `lore`. You use them to centralize unavailable and lifecycle presentation.

You configure the proxy to choose one effective menu state in this order: offline, draining, `IN_GAME`, full, then healthy.

Template precedence is:

1. A nonblank per-server `name` or nonempty per-server `lore` is final.
2. Otherwise, the matching state `name` or `lore` is used.
3. Otherwise, the localized `[menus.inventory]` default is used.

Material precedence is different because healthy, unavailable, and routable lifecycle entries have different roles:

- An unavailable entry uses its per-server `unavailable_material`, then the matching state material, then the global unavailable material.
- A healthy entry uses its per-server `material`, then the global normal material.
- A routable `IN_GAME` entry uses the state material, then its per-server/global normal-material fallback.

`in_game` styling follows the backend's lifecycle marker whenever it reports `IN_GAME`, even on a network that permits routing to in-progress servers. State styling is presentation only; availability and click validation still come from the router.

## Validate before opening the menu

`/vn menu validate` performs a focused selector audit. It reports problems such as:

- `[servers]` keys that do not match a registered Velocity server
- duplicate display names that would look ambiguous to players
- invalid, conflicting, or out-of-range inventory slots
- malformed material identifiers (the Velocity proxy cannot verify whether every backend Minecraft version provides that material)
- unsupported or misspelled curly-brace selector placeholders

The command does not change configuration. Correct reported issues, run `/vn reload`, and validate again. A syntactically valid material can still be unavailable on an older backend, where the bridge uses its configured fallback material. `/vn config validate` remains the broader network/configuration check; use both before a production rollout.

## Java item-name precedence

You assign different jobs to `display_name` and Java's per-server `name`. You use `display_name` as reusable metadata and `name` as a complete item-name template. You leave `name = ""` to let the default template render the alias.

The same rule applies to lore: a nonempty per-server `lore` intentionally overrides state and localized lore. Include `{description}` wherever the shared description should appear.

## Migration and reload behavior

You retain usability of existing v4.3 `gui.toml` files. You migrate a v1 GUI file to v2 and save `gui.toml.v1.bak`. You provide backward-compatible behavior for existing entries:

- no `display_name`: show the raw ID
- no `description`: use an empty description
- no `menu_order`: preserve existing ordering
- no `show_in_menu`: show the server
- no state tables: migration writes the state defaults; existing per-server `name`, `lore`, and material overrides remain authoritative

Run `/vn reload` after editing. No proxy restart and no routing-ID rename are required.

## What is not included

You omit localized per-language display names in this release. You use the selected language pack for shared templates while maintaining one display name and description for all players.

## Troubleshooting

### The selector still shows `lobby1`

- Confirm that you edited the proxy's `gui.toml`, not the backend bridge's `config.yml`.
- Confirm that the `[servers]` key exactly matches the ID in `velocity.toml`.
- Run `/vn reload`, then `/vn menu validate`.
- Check the reload output for a TOML warning.

### A hidden server still receives players

That is expected. `show_in_menu` only controls selector visibility. Remove it from the relevant routing pool or use `/vn drain <server>` to stop new automatic routes.

### The Java item ignores a state style

A nonblank per-server `name` and nonempty `lore` intentionally take precedence. Clear those fields to inherit the matching state style. Compare `/vn servers` health, drain, circuit, and capacity signals, and verify the backend lifecycle marker separately for `IN_GAME`.

### Two buttons have the same label

Give each server a distinct `display_name`. Selection remains safe because the hidden targets are different raw IDs, but identical labels are confusing. `/vn menu validate` reports this ambiguity.

### A server stopped routing after I renamed it

Restore the original ID in `velocity.toml`, `navigator.toml`, and the left side of the `gui.toml` entry. Change only `display_name`; never rename a route target to add spaces to its label.

## Related guides

- [Java and Bedrock Selectors](Java-and-Bedrock-Selectors)
- [Configuration Guide](Configuration-Guide)
- [Commands and Permissions](Commands-and-Permissions)
- [Backend Lifecycle States](Backend-Lifecycle-States)
- [Migration Guide](Migration-Guide-v3-to-v4)
