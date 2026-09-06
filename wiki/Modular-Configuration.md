# Modular Configuration

VelocityNavigator uses a modular, multi-file TOML configuration system. Each concern lives in its own dedicated file. `ConfigManager` parses and manages them.

---

## File Inventory

| File | Purpose |
|------|---------|
| `navigator.toml` | Main routing rules, commands, health checks, metrics, core settings |
| `messages.toml` | Language packs and all player-facing text |
| `gui.toml` | Java inventory layout and Bedrock form definitions, state styles |
| `servers.toml` | Lobby entries created by server-management commands |
| `storage.toml` or `db.toml` | Database provider, credentials, JDBC URL, connection pool limits, migration settings |
| `auth.toml` | Player authentication, password hashing, holding server, and sessions; 2FA fields are reserved |
| `geo.toml` | GeoIP reader settings (MaxMind GeoLite2 database path, IP-API fallback) |
| `player_affinity.json` | Unexpired sticky-lobby records when the file storage provider is active |

---

## File Purposes and Relationships

`navigator.toml` acts as the entry point. It references server lists, commands, and routing rules that other files refine:

- **`navigator.toml`** defines the server list and routing paths. Server entries created or modified by `/vn` management commands persist to `servers.toml`. `navigator.toml` loads this file at runtime.
- **`messages.toml`** supplies player-facing text. GUI layouts in `gui.toml` reference message keys instead of hardcoding text. Changing a language file updates UIs without altering layout definitions.
- **`gui.toml`** describes inventory slot mappings, item models, click actions, and Bedrock form JSON. It references message keys for display names and lore. It uses server keys from `navigator.toml` / `servers.toml` for connect actions.
- **`storage.toml`** (or `db.toml`) supplies the storage layer. If set to `FILE`, persistence uses local JSON files. If set to `MYSQL` or `POSTGRESQL`, the plugin uses the JDBC settings.
- **`auth.toml`** and **`geo.toml`** are independent modules. A flag in `navigator.toml` enables the authentication subsystem. The plugin uses GeoIP for region-based routing when configured.
- **`player_affinity.json`** is a runtime file. Administrators do not edit it. It stores unexpired sticky-lobby assignments when file storage is active.

---

## Configuration Migration

When VelocityNavigator starts, `ConfigManager.load()` reads each file's `config-version` key. If the stored version is older than the current plugin version, the plugin migrates it:

1. **Backup** - The plugin copies the file to a timestamped backup before modifying it.
2. **Patch** - The plugin adds missing keys with documented defaults. Migration rules rename or remove older keys.
3. **Version bump** - The plugin updates the `config-version` key.
4. **Normalization** - The plugin corrects structural inconsistencies like duplicate keys or incorrect nesting.

The `ConfigLoadResult` record returned by `load()` exposes the outcome:

```java
public record ConfigLoadResult(
    Config config,
    List<String> warnings,
    boolean createdDefault,
    boolean migrated,
    Integer previousVersion,
    Path backupPath,
    boolean normalized
) {}
```

- `migrated` is `true` when the plugin upgrades a file.
- `previousVersion` records the old version number.
- `backupPath` points to the backup directory.
- `warnings` contains non-fatal issues like deprecated keys or approximate matches.

---

## Self-Documenting Config

TOML files include inline comments explaining key purposes, values, and defaults. When `ConfigManager` creates a default file or adds keys during migration, it writes these comments inline. Administrators do not need external documentation for basic configuration.

Keys added by `ensureAdvancedSections()` receive comments describing their role in party systems, queue management, Redis integration, backend state tracking, and server management.

---

## Typo Detection and Correction

`ConfigValidator` checks every TOML key against the known schema. When it finds an unrecognized key, the validator calculates the [Levenshtein distance](https://en.wikipedia.org/wiki/Levenshtein_distance) between the unknown key and valid keys.

- If it finds a close match, it emits a warning with a suggestion via `getSuggestion()`.
- If no close match exists, it flags the key as completely unknown.

`ConfigLoadResult.warnings` collects these warnings and logs them at startup. Administrators catch typos before they cause errors.

---

## Reload Behavior

The `/vn reload` command reloads all configuration files:

1. Re-reads every TOML file from disk.
2. Re-runs validation and migration checks.
3. Applies changes to the running instance without a server restart.
4. Reports warnings or errors to the executing player.

The active storage provider remains the source of truth for sticky-lobby records during reload.

---

## Backup System

Before migration or destructive write operations, `ConfigManager` creates a backup:

- Backups sit in a timestamped subdirectory under the plugin's data folder.
- Backups preserve the file state before modification.
- The `backupPath` field in `ConfigLoadResult` identifies the backup location.

Administrators can restore previous configurations manually or by copying the backup file back into place.

---

## Configuration Validation

Validation occurs in two phases:

1. **Structural validation** - Ensures required sections and keys exist, types are correct (string, integer, boolean, list), and nested structures match the schema.
2. **Semantic validation** - Checks value ranges (e.g., pool sizes are positive, ports are valid) and cross-references between files (e.g., a server key in `gui.toml` exists in `servers.toml`).

Validation errors block configuration loading and log context to identify the file, line, and key. Warnings (like typo suggestions) are non-fatal and allow loading to proceed.

---

## Best Practices

- **Edit files via `/vn` commands when possible.** Management commands (`/vn addserver`, `/vn setmessage`) write correctly formatted TOML with comments, avoiding manual errors.
- **Run `/vn reload` after manual edits.** This validates changes and applies them immediately.
- **Check startup logs for warnings.** Typo detection warnings and migration notices appear in the log. Address them early.
- **Keep backups.** The migration system creates backups automatically. Consider making a manual copy before major upgrades.
- **Do not edit `player_affinity.json` directly.** The file storage provider manages and overwrites it.
- **Use one storage backend.** Do not mix `storage.toml` and `db.toml` configurations. Choose one and configure it fully.
- **Review migration output.** Check `ConfigLoadResult` warnings after version upgrades to confirm settings migrated correctly.
