# Server-List MOTD

VelocityNavigator can replace the description players see in Minecraft's server list. Its settings live in:

```text
plugins/velocitynavigator/motd.toml
```

This is separate from chat messages in `messages.toml`.

## Quick Setup

Start with one MOTD so the result is easy to recognize:

```toml
enabled = true
mode = "ROTATING"
rotation_interval_seconds = 30
motds = [
    """\
    <aqua><bold>My Network</bold></aqua>\n\
    <gray>{online}/{max} players online</gray>\
    """
]

[maintenance]
override_motd_on_maintenance = true
motds = [
    """\
    <red><bold>MAINTENANCE</bold></red>\n\
    <gray>{maintenance_reason}</gray>\
    """
]
```

Save the file and run:

```text
/vn motd reload
```

Refresh the server entry in Minecraft's multiplayer screen. If the old description remains, make sure the player is pinging the Velocity address rather than a backend address.

## Readable files and existing configurations

Generated MOTD values use wrapped, triple-quoted TOML strings instead of long horizontal lines. `\n` adds a line break in Minecraft; a backslash at the end of a file line only continues the value on the next file line. Wrapping does not add spaces or change your displayed text.

After installing the updated JAR, restart the proxy or run `/vn motd reload`. Existing long MOTD entries are automatically reformatted. Your normal and maintenance messages, comments, and extra settings are preserved, and the original is backed up to `motd.toml.pre-format.bak`. `/vn reload` also reloads and formats this file. You do not need to delete it or replace your custom text with the defaults.

Saves use a temporary file and replacement so an interrupted write cannot leave half a configuration. Invalid TOML, invalid modes, empty message lists, and non-positive rotation intervals produce an error and keep the last working in-memory configuration. A command reports success only after the change was saved. At least one normal MOTD must remain.

MOTD parsing uses [TomlJ](https://github.com/tomlj/tomlj) to preserve backslash escapes and [TOML multiline-string continuation](https://toml.io/en/v1.0.0#string) correctly. Other configuration loaders are unchanged.

## Modes

| Mode | Behavior |
|---|---|
| `ROTATING` | Keeps one MOTD for `rotation_interval_seconds`, then moves through the list |
| `SEQUENTIAL` | Moves to the next MOTD on each server-list ping |
| `RANDOM` | Chooses a random MOTD for each ping |

Use uppercase mode names in the file. `ROTATING` is the friendliest choice for a normal network.

## Placeholders

| Placeholder | Example |
|---|---|
| `{online}` | `42` |
| `{max}` | `500` |
| `{version}` | `v4.5.0` |
| `{maintenance_reason}` | `Updating plugins` |

`{maintenance_reason}` is useful in the maintenance list. The others work in normal and maintenance MOTDs.

Text accepts MiniMessage, classic `&` color codes, and `\n` for a second line.

## Maintenance MOTD

When `override_motd_on_maintenance = true`, global maintenance uses the list under `[maintenance]`, even if the normal MOTD feature has `enabled = false`. Set the override to `false` if a different plugin should control maintenance pings. Maintenance reasons are inserted as plain text, not interpreted as MiniMessage tags.

Try it from the proxy console:

```text
/vn maintenance global on Updating plugins
```

Refresh the multiplayer screen. You should see the maintenance MOTD and the reason. When finished:

```text
/vn maintenance global off
```

Per-server maintenance does not replace the network MOTD because the rest of the network remains available.

## Commands

All commands require `velocitynavigator.admin`.

| Command | Purpose |
|---|---|
| `/vn motd reload` | Reloads and formats `motd.toml` |
| `/vn motd list` | Shows the configured normal MOTDs and their indexes |
| `/vn motd add <text...>` | Adds a normal MOTD |
| `/vn motd remove <index>` | Removes a normal MOTD |
| `/vn motd setmode <ROTATING\|SEQUENTIAL\|RANDOM>` | Changes and saves the mode |

Editing `motd.toml` directly is usually easier for multiline text. Use the commands for quick operational changes.

## Troubleshooting

- Run `/vn motd list` to confirm the proxy loaded the file you edited.
- Keep the TOML commas and quotes around every list item.
- Use `\n` inside a quoted value for a second line.
- Check that `enabled = true`.
- If reload fails, fix the reported line in the file; the last working MOTD remains active. The file is not replaced with defaults.
- Global maintenance must be enabled with `/vn maintenance on ...`; `/vn maintenance <server> on ...` affects only that backend.
