# Changelog

## 0.1.3

### Features
- Added `/clan info [clan]`, `/clan list [page]` and `/clan members [clan]` so players can browse clans and member rosters
- Added clan descriptions with `/clan description [text]`, persisted per clan
- Added `/cc <message>` clan chat and `/ac <message>` ally chat with configurable MiniMessage formats
- Added optional CarbonChat integration that registers `daclans:clan` and `daclans:ally` channels when CarbonChat is installed
- Added `/clan logs [clan]` with the 20 most recent bank entries, purged after 30 days
- Added configurable last-online tracking, shown in `/clan members` and the settings menu
- Added configurable leader inactivity handling that can transfer leadership or disband the clan
- Added per-type clan leaderboards through `/clan top [page] [type]` and `%daclans_top_<n>_TYPE%`
- Added a public API and cancellable Bukkit events for clan actions, bank changes, diplomacy and chat
- Invites now expire according to `clan.invite-expiry-seconds`; set it to `0` to disable expiry

### Fixes
- Bank balances are stored as integer cents, removing floating-point drift
- `ClanManager.remove()` now clears pending invites and ally requests, and removes the clan from other clans' ally and enemy lists
- Expired invites, ally requests and old bank logs are cleaned up periodically
- `SqlStorage.save()` snapshots clan data before scheduling asynchronous work
- Database migrations are tracked with a `schema_version` table instead of swallowing errors
- Bounty blocking now resolves uncached player names instead of relying only on the offline-player cache
- `/clan kick` now works for offline members

### Configuration
- Added `clan.bank-logs`, `clan.last-seen`, `clan.inactivity`, `clan.cleanup-seconds`, `clan.list-page-size`, `clan.description-max-length`, `chat` and `anti-teaming.per-clan-cooldown-seconds`
- Added `description` to `default-permissions`
- Added `hooks.carbonchat`
- Added `daclans.command.info`, `list`, `members`, `description`, `logs`, `logs.others`, `cc` and `ac` permissions

### Build and release
- Added a GitHub Actions build workflow
- Added `./gradlew bumpVersion` to bump the plugin version and README badge

## 0.1.2

### Features
- Added `/clan color <color>` to set the clan name color used by `%daclans_name%`, with support for named MiniMessage colors and hex colors (`#RRGGBB` or `#RGB`)
- Added `/clan reload` to reload the configuration, ranks, GUI, hooks and leaderboard task without restarting the server
- Clan colors are saved per clan and persist across restarts

### Integrations
- `%daclans_name%` and `%daclans_top_<n>_name%` now include the configured clan color
- The PlaceholderAPI expansion is re-registered when the `placeholderapi` hook is toggled by a reload

### Permissions
- Added `daclans.command.color`, `daclans.command.color.default` and `daclans.command.color.hex`
- Added `daclans.command.reload` (op)

### Configuration
- Removed `nexoId` fields from the default `config.yml` and `settings.yml`
- Clan state labels are now plain text; their color is applied by the surrounding lore line
- Added `color-set`, `invalid-color`, `reload-success` and `reload-failed` messages

### Storage
- Added a `color` column to the clan table, with automatic migration for existing SQLite and MySQL databases

### Fixes
- Stopping the `runServer` Gradle task now stops the server instead of leaving it running in the background

### Development
- Upgraded the Gradle wrapper to 9.8.0
- `runServer` now starts the Minecraft 26.3 test server from `MagicKPvP/run` with Aikar's flags, using Java 26 configured by `serverJavaHome` in `gradle.properties`

## 0.1.1

### Features
- Clans: create, rename, disband, leave, invite, join and kick, with configurable name pattern, member limit and invite expiry
- Configurable rank ladder (Member, Officer, Co-Leader, Leader) with per-clan minimum ranks for invite, kick, rename, settings and warn
- Alliances and enemies, with optional damage reduction between clan members and between allies
- Clan bank with `/clan deposit` and `/clan withdraw` through Vault
- Settings menu for clan access, invite rules, rank permissions, member promotions and paid member-slot upgrades
- Clan leaderboard with `/clan top`, sorted by bank or member count
- Anti-teaming warnings for clans that gang up on neutral players, with staff notifications (`daclans.antiteam.notify`)

### Integrations
- PlaceholderAPI placeholders under the `daclans` identifier
- Nexo custom items for rank and menu icons
- BountyLite support that blocks bounties on teammates
- Vault economy support for the clan bank

### Storage
- SQLite by default, MySQL optional
