# Language Packs

![VelocityNavigator language packs](headers/language-packs.png)

VelocityNavigator keeps player-facing text in `messages.toml`. You can use one of the included translations, adjust a few lines for your network, or maintain a completely custom language.

## Included languages

| Code | Language |
|---|---|
| `en` | English |
| `ru` | Russian |
| `es` | Spanish |
| `fr` | French |
| `de` | German |
| `pt_br` | Brazilian Portuguese |
| `zh_cn` | Simplified Chinese |

Set the language at the top of `messages.toml`:

```toml
language = "de"
```

Restart the proxy or run `/vn reload`. Choosing an included language loads the complete pack, so make a copy first if you have edited the current messages.

## Custom translations

Use your own short code when you want to keep custom text:

```toml
language = "nl"
```

Unknown codes are treated as custom packs and your current values are preserved. Leave `active_language` alone; VelocityNavigator updates it to remember which built-in pack is currently written to the file.

VelocityNavigator uses one language for the whole proxy. It does not switch messages automatically from each player's client locale.

## Placeholders

Messages may contain placeholders such as `<player>`, `<server>`, `<time>`, `<reason>`, `<attempt>`, and `<max>`. Keep the placeholders that matter to the message even when you rewrite the surrounding sentence.

For example:

```toml
connecting = "<green>Sending you to <server>...</green>"
cooldown = "<yellow>Please wait <time> more second(s).</yellow>"
```

Party, queue, menu, and admin messages have their own placeholders. The comments written above each setting show the values available there.

## Colors and formatting

The text fields accept:

- MiniMessage tags such as `<green>` and `<bold>`
- classic codes such as `&a` and `&l`
- hex colors such as `&#55FFFF`
- Bungee-style hex color sequences

If you want angle brackets to appear as text instead of a placeholder, use `&lt;` and `&gt;`.

## Menu text

The same file also contains the default text used by selectors:

- `[menus.chat]` for the clickable Java chat menu
- `[menus.inventory]` for the Java inventory title, item names, and lore
- `[menus.bedrock]` for the Bedrock form title, content, and buttons

Per-server icons, slots, names, and lore overrides belong in `gui.toml`; see [Java and Bedrock Selectors](Java-and-Bedrock-Selectors).

## After editing

Run:

```text
/vn reload
```

If a line does not render as expected, check for an unclosed MiniMessage tag or a placeholder that was accidentally changed. The [Troubleshooting Guide](Troubleshooting-Guide) has more formatting checks.
