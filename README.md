<div align="center">

# DaClans

**A simple, configurable clans plugin for Paper servers, with ranks, alliances, a clan bank and anti-teaming.**

[![Version](https://img.shields.io/badge/version-0.1.3-blue)](https://modrinth.com/plugin/daclans)
[![Platform](https://img.shields.io/badge/platform-Paper-lightgrey)](https://papermc.io/)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21.11%20%2B-brightgreen)](https://papermc.io/)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/java-21%2B-orange)](https://adoptium.net/)

DaClans lets players form clans, invite friends, climb a rank ladder, ally with other clans and pool money in a shared bank. Server owners get a fully configurable rank system, damage rules between teammates and an anti-teaming check against neutral players.

</div>

> Built on the Paper Brigadier command API and Paper plugin descriptor (`paper-plugin.yml`).

## Features

**Clans**
- Create, rename, disband and leave clans
- Browse every clan with `/clan list`, inspect one with `/clan info [clan]` and see members with `/clan members [clan]`
- Add a clan description with `/clan description <text>`
- Invite players (invites expire) or leave the clan open so anyone can join
- Configurable name pattern and member limit
- Transfer ownership with `/clan setowner`
- Set the clan name color with `/clan color <color>`, using named colors or hex values
- Optional automatic leadership transfer or disbanding when a leader has been inactive for too long

**Chat**
- Clan chat with `/cc <message>` and ally chat with `/ac <message>`
- Works standalone with fully configurable MiniMessage formats
- Registers `daclans:clan` and `daclans:ally` channels when CarbonChat is installed

**Ranks and permissions**
- Four default ranks: Member, Officer, Co-Leader and Leader
- Add more ranks or change the default ones in `config.yml`, ordered by level
- Per-clan minimum rank for inviting, kicking, renaming, settings and warning, set by the leader in-game
- Custom display name, material, lore and glow for each rank

**Diplomacy**
- Alliances with expiring requests, accepted by running the command from the other clan
- Mark other clans as enemies
- Optional damage reduction between clan members and between allies

**Economy**
- Clan bank with `/clan deposit` and `/clan withdraw`
- Money is stored as integer cents, so balances never drift
- Deposit, withdraw and upgrade history with `/clan logs`, capped at 20 entries per clan and purged after 30 days
- Buy extra member slots from the settings menu
- Uses Vault, so it works with any Vault-compatible economy

**Tracking and API**
- Optional last-online tracking shown in `/clan members` and the settings menu
- Clan leaderboards by bank, member count or allies, exposed through `/clan top` and PlaceholderAPI
- Public API and cancellable Bukkit events for create, disband, rename, join, leave, kick, leadership, color, description, bank, diplomacy and chat
- Periodic cleanup of expired invites and ally requests

**Moderation**
- Anti-teaming: warns clans and allies that gang up on a player with no clan
- Staff notifications through a permission node
- Optional BountyLite integration that blocks bounties on teammates

**Interface and extras**
- Chest GUI for clan settings, rank permissions and member promotions
- Clan leaderboard sorted by bank, member count or allies
- PlaceholderAPI placeholders
- Nexo custom items for GUI and rank icons
- MiniMessage formatting for every message and menu item
- `/clan reload` applies configuration, rank, GUI, hook and leaderboard changes without a restart
- SQLite by default, MySQL optional

## Requirements

| Requirement | Details |
|---|---|
| Server | Paper or Purpur 1.21.11 or newer |
| Java | 21 or newer, or 25 or newer on Minecraft 26.1+ |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional, needed for `/clan deposit` and `/clan withdraw` |
| Economy plugin | Optional, any Vault-compatible provider |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional, enables the placeholders below |
| [CarbonChat](https://github.com/Hexaoxide/Carbon) | Optional, exposes clan and ally channels inside CarbonChat |
| Nexo | Optional, enables custom item icons |
| BountyLite | Optional, enables teammate bounty blocking |

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/daclans).
2. Drop the jar into your server's `plugins` folder.
3. Start the server once to generate the default files, then edit them in `plugins/DaClans/`.
4. Run `/clan reload` or restart the server to apply configuration changes.

`config.yml` and `settings.yml` are generated on first start and are never overwritten by updates.

## Commands

The main command is `/clan`, with the aliases `/clans` and `/daclans`.

| Command | Description | Who can use it |
|---|---|---|
| `/clan create <name>` | Create a clan | Anyone not in a clan |
| `/clan disband` | Delete your clan | Leader |
| `/clan rename <name>` | Rename your clan | Minimum rank set for `rename` |
| `/clan color <color>` | Set the color used for the clan name in `%daclans_name%` | Leader |
| `/clan invite <player>` | Invite an online player | Leader, or the minimum rank set for `invite` if invites are open to ranks |
| `/clan join [clan]` | Join a clan you were invited to, or any open clan | Anyone not in a clan |
| `/clan leave` | Leave your clan | Members (leaders cannot leave) |
| `/clan promote <player>` | Raise a member one rank | Leader |
| `/clan demote <player>` | Lower a member one rank | Leader |
| `/clan setowner <player>` | Hand leadership to another member | Leader |
| `/clan kick <player>` | Remove a member of a lower rank | Minimum rank set for `kick` |
| `/clan settings` | Open the settings menu | Minimum rank set for `settings` |
| `/clan ally <clan>` | Send, accept or end an alliance | Leader |
| `/clan enemy <clan>` | Toggle a clan as your enemy | Leader |
| `/clan warn <player> <message>` | Send a warning to a member | Minimum rank set for `warn` |
| `/clan deposit <amount>` | Add money to the clan bank | Members |
| `/clan withdraw [amount]` | Take money from the bank, or everything if no amount is given | Leader |
| `/clan top [page] [type]` | Show the leaderboard by `bank`, `members` or `allies` | Anyone |
| `/clan info [clan]` | Show a clan summary | Anyone |
| `/clan list [page]` | List every clan | Anyone |
| `/clan members [clan]` | List members, ranks and last seen times | Anyone |
| `/clan description [text]` | Show or set the clan description | Minimum rank set for `description` |
| `/clan logs [clan]` | Show the clan bank history | Members; other clans need `daclans.command.logs.others` |
| `/cc <message>` | Send a message to your clan | Anyone in a clan |
| `/ac <message>` | Send a message to your clan and its allies | Anyone in a clan |
| `/clan reload` | Reload the configuration, ranks, GUI, hooks, chat and tasks | Op |

<details>
<summary><b>Permissions</b></summary>

All `daclans.command.*` permissions are granted to players by default, except `daclans.command.reload`, which defaults to op. In-game rank checks apply on top of them.

`/clan color` checks the color type separately: named colors need `daclans.command.color.default`, and hex colors need `daclans.command.color.hex`. The base `daclans.command.color` permission is required for both.

| Command | Permission |
|---|---|
| `/clan create <name>` | `daclans.command.create` |
| `/clan disband` | `daclans.command.disband` |
| `/clan rename <name>` | `daclans.command.rename` |
| `/clan color <color>` | `daclans.command.color` plus `daclans.command.color.default` or `daclans.command.color.hex` |
| `/clan invite <player>` | `daclans.command.invite` |
| `/clan join [clan]` | `daclans.command.join` |
| `/clan leave` | `daclans.command.leave` |
| `/clan promote <player>` | `daclans.command.promote` |
| `/clan demote <player>` | `daclans.command.demote` |
| `/clan setowner <player>` | `daclans.command.setowner` |
| `/clan kick <player>` | `daclans.command.kick` |
| `/clan settings` | `daclans.command.settings` |
| `/clan ally <clan>` | `daclans.command.ally` |
| `/clan enemy <clan>` | `daclans.command.enemy` |
| `/clan warn <player> <message>` | `daclans.command.warn` |
| `/clan deposit <amount>` | `daclans.command.deposit` |
| `/clan withdraw [amount]` | `daclans.command.withdraw` |
| `/clan top [page] [type]` | `daclans.command.top` |
| `/clan info [clan]` | `daclans.command.info` |
| `/clan list [page]` | `daclans.command.list` |
| `/clan members [clan]` | `daclans.command.members` |
| `/clan description [text]` | `daclans.command.description` |
| `/clan logs [clan]` | `daclans.command.logs`, plus `daclans.command.logs.others` for other clans |
| `/cc <message>` | `daclans.command.cc` |
| `/ac <message>` | `daclans.command.ac` |
| `/clan reload` | `daclans.command.reload` |

| Permission | Description | Default |
|---|---|---|
| `daclans.command.reload` | Reload the plugin configuration | op |
| `daclans.antiteam.notify` | Receive a staff notice when clan members are teaming on a player | op |

</details>

## Settings menu

`/clan settings` opens a chest menu. Anyone who meets the `settings` rank can open it and toggle clan access. Everything else is leader only.

| Button | Action | Who |
|---|---|---|
| Clan access | Switch between open and invite only | Settings rank or higher |
| Who can invite | Switch between leader only and rank based invites | Leader |
| Rank permissions | Left click raises, right click lowers the minimum rank for each action | Leader |
| Promote / Demote | Left click promotes, right click demotes a member | Leader |
| Upgrade clan size | Spend bank money to add member slots | Leader |

New clans start invite only, and only the leader can invite until that setting is changed.

## Placeholders

Requires PlaceholderAPI. The identifier is `daclans`.

<details>
<summary><b>Placeholder list</b></summary>

| Placeholder | Description |
|---|---|
| `%daclans_name%` | The player's clan name, prefixed with the MiniMessage color set by `/clan color` |
| `%daclans_plain_name%` | The player's clan name without formatting |
| `%daclans_tag%` | The configured clan color tag |
| `%daclans_leader%` | Name of the clan leader |
| `%daclans_members%` | Current member count |
| `%daclans_max_members%` | Member limit |
| `%daclans_online_members%` | Number of clan members currently online |
| `%daclans_bank%` | Bank balance formatted from integer cents |
| `%daclans_bank_cents%` | Bank balance in cents |
| `%daclans_rank%` | The player's rank display name |
| `%daclans_allies%` | Number of allied clans |
| `%daclans_enemies%` | Number of enemy clans |
| `%daclans_description%` | Clan description |
| `%daclans_created%` | Clan creation date |
| `%daclans_last_seen%` | The player's last seen date |
| `%daclans_last_seen_ago%` | The player's last seen time as a relative value |
| `%daclans_position%` | The clan's position on the configured leaderboard |
| `%daclans_top_<n>_TYPE%` | Value for position `n` on the `TYPE` leaderboard (`bank`, `members` or `allies`) |
| `%daclans_top_<n>_TYPE_FIELD%` | A field for position `n` and type: `name`, `value`, `members`, `allies`, `enemies`, `bank`, `leader` or `description` |

`%daclans_top_<n>_name%`, `%daclans_top_<n>_value%` and the other field names use the configured leaderboard when no type is given. For example, `%daclans_top_1_members_name%` returns the name of the clan with the most members.

Placeholders return an empty string when the player has no clan, or when there is no clan at the requested leaderboard position.

</details>

## Configuration

| File | Contents |
|---|---|
| `config.yml` | Storage, hooks, clan limits, ranks, default rank permissions, damage rules, anti-teaming, leaderboard, bounty blocking and every message |
| `settings.yml` | Size, title and items of the settings menu, including slots, materials, upgrade cost and size |

<details>
<summary><b>Configuration options</b></summary>

Key options in `config.yml`:

```yaml
clan:
  name-regex: "[A-Za-z0-9_]{3,16}"
  max-members: 10
  invite-expiry-seconds: 60      # 0 disables expiry
  ally-request-expiry-seconds: 120
  cleanup-seconds: 60
  list-page-size: 10
  description-max-length: 120
  bank-logs:
    max: 20
    retention-days: 30
  last-seen:
    enabled: true
  inactivity:
    enabled: false
    days: 30
    check-minutes: 30
    action: transfer              # transfer or disband

chat:
  enabled: true
  clan-format: "<gray>[Clan] <white><player>: <message>"
  ally-format: "<gray>[Ally] <white><player>: <message>"
  carbon:
    enabled: true

# When CarbonChat is installed, DaClans registers daclans:clan and daclans:ally.
# Add plugins/CarbonChat/channels/clanchat.conf and allychat.conf with those keys to
# customize the channel formats and commands. DaClans wraps them so only clan or
# ally members receive messages. Without those files the channels use the formats above.

default-permissions:     # minimum rank required for each action
  invite: member
  kick: officer
  rename: leader
  settings: coleader
  warn: officer
  description: leader

damage-reduction:        # percent, 0 to 100
  clan: 0
  allies: 0

anti-teaming:
  enabled: true
  threshold: 2           # attackers from one clan group within the window
  window-seconds: 15
  per-clan-cooldown-seconds: 60

leaderboard:
  enabled: true
  sort: bank             # bank, members or allies
  page-size: 10
  refresh-seconds: 60
```

Ranks are ordered by `level`. The lowest rank is given to new members, and the rank with the key `leader` is reserved for the clan owner:

```yaml
ranks:
  officer:
    level: 2
    displayname: "<yellow>Officer"
    material: GOLD_INGOT
    glowing: false
    lore:
      - "<dark_gray>Trusted clan rank"
```

Each integration can be switched off under `hooks`:

```yaml
hooks:
  placeholderapi: true
  nexo: true
  bountylite: true
  vault: true
  carbonchat: true
```

**BountyLite**

When BountyLite is installed and `hooks.bountylite` is on, players cannot place bounties on clan members or allies. The commands and subcommands that are checked are configurable:

```yaml
bounty:
  allow-on-teammates: false
  commands:
    - bounty
  blocked-subcommands:
    - set
    - bet
```

</details>

## Developer API

Other plugins can read clan data through `DaClansProvider.api()` and listen for cancellable Bukkit events in `net.malusic.daclans.api`:

`ClanCreateEvent`, `ClanDisbandEvent`, `ClanRenameEvent`, `ClanJoinEvent`, `ClanLeaveEvent`, `ClanKickEvent`, `ClanLeaderChangeEvent`, `ClanColorEvent`, `ClanDescriptionEvent`, `ClanBankDepositEvent`, `ClanBankWithdrawEvent`, `ClanAllyEvent`, `ClanEnemyEvent` and `ClanChatEvent`.

```java
DaClansApi api = DaClansProvider.api();
Clan clan = api == null ? null : api.clan("Alpha");
```

The API exposes clan lookup, leaderboards, balances in cents, formatted balances and online member counts.

## Storage

Clans are stored in SQLite by default, in `plugins/DaClans/clans.db`. To use MySQL, change the storage section in `config.yml` and restart:

```yaml
storage:
  type: mysql
  mysql:
    host: localhost
    port: 3306
    database: daclans
    username: root
    password: ""
```

The `clans`, `members`, `clan_bank_logs` and `schema_version` tables are created automatically on first start, and older databases are upgraded in place. The database itself must already exist when using MySQL.

## Building from source

The project uses the Gradle 9.8.0 wrapper and targets Java 21 bytecode, so a JDK 21 or newer is required to build:

```bash
git clone https://github.com/malusic23354/DaClans.git
cd DaClans
./gradlew build
```

The plugin jar is written to `build/libs/DaClans-<version>.jar`.

GitHub Actions runs the full Gradle build on every push and pull request.

Release automation bumps the patch version in `paper-plugin.yml` and the README badge:

```bash
./gradlew bumpVersion
```

### Testing on a local server

`./gradlew runServer` builds the plugin, copies it to the test server's `plugins` folder and starts the server with Aikar's flags. Stopping the Gradle task stops the server.

The included test-server setup runs from `MagicKPvP/run` and launches Minecraft 26.3 with Java 26, because Minecraft 26.1 and newer requires Java 25 or above. The server JDK is configured in `gradle.properties`:

```properties
serverJavaHome=C:/Program Files/Java/jdk-26.0.1
```

If your test server lives elsewhere or uses a different JDK, update `serverJavaHome`, and the `copyPlugin` and `runServer` paths in `build.gradle.kts`, accordingly.

## Support

Found a bug or have a suggestion? Open an issue on the [issue tracker](https://github.com/malusic23354/DaClans/issues). Please include your server software and version, the DaClans version and any relevant console log output.

## Links

- Download: [Modrinth](https://modrinth.com/plugin/daclans)
- Source: [GitHub](https://github.com/malusic23354/DaClans)
- Author: [Malusic](https://github.com/malusic23354)