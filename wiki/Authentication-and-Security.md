# Authentication and Security

VelocityNavigator can keep players on a small holding server until they register or log in. It handles password storage and proxy routing; it does not create the holding server or a void world for you.

Get normal lobby routing working before enabling authentication. A bad holding-server name can prevent every new player from joining.

## Setup

1. Create a small Paper/Spigot/Folia holding server. A plain empty world is enough.
2. Add it to Velocity's `velocity.toml`:

   ```toml
   [servers]
   holding = "127.0.0.1:25568"
   lobby-1 = "127.0.0.1:25566"
   ```

3. Do not add `holding` to `routing.default_lobbies`, contextual groups, geo-affinity groups, or queue pools.
4. Edit `plugins/velocitynavigator/auth.toml`:

    ```toml
    [settings]
    enabled = true
    use_sign_gui = true
    algorithm = "Argon2id"
    min_password_length = 6
    session_timeout_minutes = 60
    holding_server = "auth-holding"
    timeout_seconds = 60
    timeout_action = "KICK"

    [restrictions]
    darkness_effect_enabled = true
    restrict_movement = true
    restrict_damage_taken = true
    restrict_damage_dealt = true
    restrict_block_break = true
    restrict_block_place = true
    restrict_item_drop = true
    restrict_item_pickup = true
    restrict_chat = true
    restrict_commands = true
    allowed_commands = ["/login", "/register", "/l", "/reg"]
    ```

5. Run `/vn config validate`. Do not continue until it reports no auth holding-server errors.
6. Run `/vn reload`, then join with a test account.

The test account lands on `auth-holding`. An interactive Sign Board GUI opens automatically on screen for password entry (protecting passwords from being leaked into public chat). Sign submissions are forwarded to the proxy over the backend bridge plugin channel, where the same rate limiting, validation, and session handling apply as to the chat commands. Java players also retain `/register` or `/login` commands as fallbacks. Bedrock players detected through Floodgate receive matching native registration or login forms.

| Field | Default | Description |
|---|---:|---|
| `enabled` | `true` | Enables auth commands and route enforcement. |
| `use_sign_gui` | `true` | Opens an interactive Sign Board GUI on join for private password entry. Submissions are validated on the proxy. |
| `algorithm` | `"Argon2id"` | Hash for new passwords: `Argon2id` or `SHA256`. Argon2id is recommended. |
| `holding_server` | `"auth-holding"` | Registered, non-routed backend used until authentication succeeds. Required when auth is enabled. |
| `session_timeout_minutes` | `60` | Lifetime of a proxy-local authenticated session. |
| `min_password_length` | `6` | Minimum characters required for registration passwords. |
| `darkness_effect_enabled` | `true` | Applies a blindness darkness effect to pending auth players until logged in. |
| `restrict_movement` | `true` | Freezes player location until authentication succeeds. |

## Player commands

| Command | Purpose |
|---|---|
| `/register <password> [repeat]` | Creates credentials; passwords require at least eight characters. |
| `/login <password>` | Opens a session and routes the player from the holding backend. |
| `/logout` | Ends the session and returns the player to the holding backend. |

A useful first test is:

```text
/register a-long-test-password a-long-test-password
/logout
/login a-long-test-password
```

After registration or login, the player should leave the holding server and receive a normal routed lobby. While authentication is pending, lobby, party, queue, party-chat, and VelocityNavigator admin commands are blocked.

New credentials use Argon2id by default with a unique random salt. Existing unprefixed salted SHA-256 records remain compatible. Hash comparisons use constant-time comparison, and passwords, salts, and codes are omitted from logs.

Sessions expire after `session_timeout_minutes`, on logout, or when the proxy restarts. A reconnect to the same proxy keeps the session until that expiry. Credentials persist through the configured [Storage and Databases](Storage-and-Databases) provider; sessions are never synchronized through Redis.

Keep `algorithm = "argon2id"` for new installations. `sha256` exists for compatibility, not as the recommended choice.

## Bedrock authentication form

Install Geyser and Floodgate on the proxy, then keep `bedrock_form_enabled = true`. A new Bedrock player sees a two-field registration form. A registered Bedrock player sees a login form. Successful submission opens the same authenticated session and normal routing flow used by the commands.

The form runs on the Velocity proxy, so the backend bridge JAR does not create it. The holding backend is still required: both Java and Bedrock players stay there until authentication succeeds.

Bedrock CustomForm text fields cannot mask their contents. VelocityNavigator labels the password field as visible, but the characters still appear on screen while the player types. Tell players to enter passwords in private. Set `bedrock_form_enabled = false` if you prefer chat commands instead.

Closing the form does not disconnect the player or bypass authentication. The player remains on the holding backend and can use `/register` or `/login`. Invalid submissions show an error and reopen the appropriate form.

### Change the form language

The form uses the active language bundle. Edit these entries in `messages.toml`, or put them in `plugins/velocitynavigator/languages/<your-code>.properties` for a custom language:

```properties
auth.form.register_title=Create your account
auth.form.login_title=Log in
auth.form.password_visible=Password (visible while typing - use privacy)
auth.form.repeat_password=Repeat password
auth.form.password_placeholder=Enter your password
auth.form.command_fallback=<yellow>Authentication form closed. Use /login or /register in chat.</yellow>
```

Keep the title, field label, and placeholder entries as plain text because Bedrock forms do not render MiniMessage formatting. Chat feedback entries such as `auth.login_success`, `auth.invalid_credentials`, and `auth.password_mismatch` may use MiniMessage.

## Multi-proxy notes

- Use MySQL, MariaDB, or PostgreSQL to share credentials.
- Keep a player pinned to one proxy during a session.
- Configure the same holding backend on every proxy.
- Redis does not transmit credentials, TOTP secrets, or sessions.

## Troubleshooting

- **Player remains in holding:** check `/login`, the configured session timeout, lobby health, and `holding_server`.
- **Auth command missing:** set `enabled = true`, reload, and run `/vn config validate`.
- **Login always rejects:** confirm the proxies share the same database and that the configured algorithm is `argon2id` or `sha256`.
- **Bedrock form does not open:** confirm Floodgate detects the player, `bedrock_form_enabled = true`, and the player has reached the configured holding backend. The chat command prompt is the fallback when a form cannot be sent.
- **Bedrock password is visible:** this is a Bedrock CustomForm limitation, not stored plain text. Disable the form if that interface is unsuitable for your players.
- **Validation rejects `enable_2fa`:** TOTP is reserved but not implemented in 4.5.0; keep it disabled.
- **Everyone is rejected after enabling auth:** disable auth from the proxy console, correct `holding_server`, verify the backend is reachable, then validate again.
