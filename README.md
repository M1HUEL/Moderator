# Moderator

A moderation plugin for **Paper**: sanctions with a history, warnings, player
reports, freezes, and the tools a moderator reaches for during a session, all
behind a single `/mod` command with granular permissions.

- No runtime dependencies. The Paper API is all it needs.
- Every line of text is configurable and uses MiniMessage.
- History is kept in `data.yml` and survives restarts.
- Temporary sanctions expire on their own.

## Requirements

| | |
|---|---|
| Server | Paper 26.2 or newer (`api-version: '26.2'`) |
| Java | 25 |
| Dependencies | None |

Gradle provisions the Java 25 toolchain through the foojay resolver declared in
`settings.gradle`, so it does not have to be installed locally.

## Building

```bash
./gradlew build          # compile, test and produce the JAR
./gradlew test           # tests only
```

The artifact lands in `build/libs/Moderator-1.0.0.jar`. The project version is
injected into `plugin.yml` while packaging, so the file never carries a
hand-written version.

## Installing

1. Copy the JAR into `plugins/`.
2. Start the server once to generate `plugins/Moderator/config.yml`.
3. Adjust what you need, then use `/mod reload`.

## Commands

`/mod` (aliases `moderation`, `moderate`) groups the staff tools. `/report`
(alias `rep`) is the only command meant for players.

### Sanctions

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/mod warn` | | `<player> [reason] [-s]` | `moderator.warn` |
| `/mod note` | | `<player> [reason] [-s]` | `moderator.note` |
| `/mod kick` | | `<player> [reason]` | `moderator.kick` |
| `/mod mute` | `tempmute` | `<player> <duration\|perm> [reason] [-s]` | `moderator.mute` |
| `/mod ban` | `tempban` | `<player> <duration\|perm> [reason] [-s]` | `moderator.ban` |
| `/mod banip` | `tempbanip` | `<player\|ip> <duration\|perm> [reason] [-s]` | `moderator.banip` |

The `-s` flag applies the sanction without announcing it to the staff team. It
still shows up in the history and in the confirmation for whoever typed it.

The `<reason>` is either a short id from the `reasons` section (`spam`, `hacking`,
`griefing`, ...), which is stored with its readable label, or any free text. An
id can be limited to certain types with `applies`; if it does not apply to the
type in use it is stored as text, and if no reason was typed at all the
`fallback-reason` is used.

### Lifting sanctions

| Command | Usage | Permission |
|---|---|---|
| `/mod unmute` | `<player> [note]` | `moderator.unmute` |
| `/mod unban` | `<player> [note]` | `moderator.unban` |
| `/mod unbanip` | `<player\|ip> [note]` | `moderator.banip` |

Each lifts the most recent active sanction of that type and reports what it
lifted. The `note` is stored on the entry, so the reasoning behind the lift is
kept and not just the fact that it happened.

### History and profiles

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/mod history` | `hist`, `punishments` | `<player> [type]` | `moderator.history` |
| `/mod profile` | `info`, `user` | `<player>` | `moderator.history` |

`/mod history` shows the newest entry first and includes revoked and expired
ones, labelled as such: a history showing only what is in force could not answer
"was this player muted before", which is usually the real question. The filter
accepts `warn`, `mute`, `ban`, `banip`, `kick` and `note`.

`/mod profile` is the read-only screen: identity, last known address, first and
last seen, active sanctions, totals, open reports against the player, whether
they are muted, and the most recent notes. It changes nothing.

### Reports

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/report` | `rep` | `<player> <reason> [details]` | `moderator.report` |
| `/mod reports` | `queue` | `[open\|all]` | `moderator.reports` |
| `/mod resolve` | `report` | `<id> [resolved\|dismissed] [note]` | `moderator.reports` |

`/report` is the player facing command and is rate limited. The queue is ordered
oldest first, because the oldest report is the one about to go stale. Called with
only an id, `/mod resolve` prints the report without closing it, so it can be read
before a decision is made. `resolved` means staff acted; `dismissed` means the
report did not hold up. Reopening is deliberately unsupported: a report closed
wrongly is closed again with the right outcome, which keeps one line of history
per report instead of a trail of flip flops.

The player who filed the report is told when it is closed, if they are still
connected.

### Freezing

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/mod freeze` | `freezeplayer` | `<player> [reason] -self` | `moderator.freeze` |
| `/mod unfreeze` | | `<player>` | `moderator.unfreeze` |

A frozen player cannot move, run commands, break or place blocks, use buckets,
drop items or pick them up. Looking around still works, because a frozen player
who cannot turn their head looks like a bug rather than a restraint.

The `-self` flag freezes whoever runs the command.

A freeze is stored in `data.yml` together with the reason, the staff member who
issued it and a snapshot of the player's inventory, game mode, position and vitals.
The snapshot is written to disk *before* the avatar is touched, which is what makes
a crash survivable: Minecraft saves the emptied inventory with the rest of the
world, so without a copy on disk a crash while frozen would destroy the items
permanently. It is never overwritten, so a player who reconnects and is frozen
again still gets their original stuff back.

The avatar is handed back on disconnect, and the freeze itself survives to the next
join, along with a fresh screen saying who froze the player and why.
`/mod unfreeze` also works on a player who is offline: the release is recorded and
completed on their next join, because the inventory cannot be restored to somebody
who is not there.

### Session tools

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/mod tp` | `teleport` | `<player>` | `moderator.tp` |
| `/mod heal` | `h` | `[player]` | `moderator.heal` |
| `/mod feed` | | `[player]` | `moderator.feed` |
| `/mod clear` | | `[player] [inventory\|armor\|all]` | `moderator.clear` |
| `/mod gamemode` | | `<mode> [player]` | `moderator.gamemode` |
| `/mod kill` | | `[player]` | `moderator.kill` |
| `/mod invsee` | | `<player>` | `moderator.invsee` |
| `/mod help` | `?` | `[tool]` | `moderator.use` |

With no `[player]`, every tool applies to whoever typed it. `/mod tp` is the
exception: it always needs a name, and it brings that player to you, up to 32
blocks away and within the same world. `/mod kill` is a tool and not a sanction:
nothing is written to the history, and the death reads as an ordinary one.

`/mod invsee` opens the target's inventory as a normal container that can be
edited directly. Changes are written back when the window closes, not on every
click, so a half finished change never lands. The filler slots cannot be used to
smuggle items.

### Administration

| Command | Alias | Usage | Permission |
|---|---|---|---|
| `/mod reload` | `rl` | | `moderator.admin` |

Reloads `config.yml` and rebuilds the mute cache. Anything that had to be ignored
while reading the file is listed in chat, not only on the console. `data.yml` is
not reloaded: it is history, not configuration.

## Permissions

`plugin.yml` declares one permission per action, all with `default: op`, plus:

- `moderator.use`: access to `/mod`, and the permission behind `/mod help`.
- `moderator.notify`: receives the sanction and report broadcasts.
- `moderator.report`: files a report. `default: true`.

Two grouped permissions:

- `moderator.*`: every staff tool.
- `moderator.player`: only `moderator.report`, so it can be handed to everyone.

The auto-punish rules are a permission gate too, through
`auto-punish[].permission`: a rule with `permission: moderator.admin` does not
fire for whoever lacks it.

## Configuration

`config.yml` has four sections.

**`settings`**: overall behaviour.

| Key | What it does |
|---|---|
| `broadcast-punishments` | Announce sanctions to whoever holds `moderator.notify`. |
| `broadcast-reports` | Announce reports to whoever holds `moderator.notify`. |
| `auto-unban-on-expiry` | Lift bans and IP bans automatically when they run out. |
| `notify-on-login` | Tell a player on login which sanctions are still running. |
| `muted-blocks-commands` | Refuse commands while muted. |
| `muted-allowed-commands` | Exceptions to that, for authentication plugins. |
| `freeze-blocks-interactions` | Refuse breaking, placing, using and picking up while frozen. |
| `history-limit` | How many entries `/mod history` prints. |
| `retention-days` | Days to keep a sanction after it is over. `0`, the default, keeps everything. |
| `cooldowns` | Seconds between two actions by the same staff member. `0` disables. |

**`messages`**: every line of text, in MiniMessage. Names, reasons and notes are
inserted literally, so text typed by a player can never be read as colour tags.
`vanilla-reason` is plain text, because that is what the vanilla ban screen shows.

**`reasons`**: the ids staff type after a sanction. Each has a `label` to display
and, optionally, an `applies` list of the sanction types it may be used for.

**`auto-punish`**: automatic chains. Once a player collects `threshold` active
entries of any of `count-types`, `action` is applied. The highest matching
threshold wins, so the list does not have to be ordered.

```yaml
auto-punish:
  - threshold: 3
    count-types: [warn]
    action: mute
    duration: 30m
    reason: "Repeated warnings"
  - threshold: 5
    count-types: [warn, mute]
    action: ban
    duration: 7d
    reason: "Repeated warnings and mutes"
    permission: "moderator.admin"
```

### Durations

`30m`, `2h30m`, `7d`, `1d12h`. With no unit it is read as seconds. `perm` means
permanent, and is what you get when no duration is typed.

## Persistence

`data.yml` is created in the plugin folder. It has four sections:

```yaml
players:
  <uuid>:
    name: Steve
    last-ip: 1.2.3.4
    first-seen: 1767225600000
    last-seen: 1767312000000
freezes:
  <uuid>:
    name: Steve
    reason: Griefing
    staff-name: Admin
    created: 1767225600000
    released: false
    game-mode: SURVIVAL
    health: 17.5
    world: world
    x: 128.5
    y: 64.0
    z: -256.0
    yaw: 90.0
    pitch: 12.0
    contents: '<base64 of the inventory>'
    armor: '<base64 of the armour>'
    off-hand: '<base64 of the off hand>'
punishments:
  - id: ...
    type: mute
    target: <uuid>
    ...
reports:
  - id: ...
    ...
```

Timestamps are epoch milliseconds. The `freezes` section is keyed by UUID and holds
at most one entry per player; `released: true` means staff lifted the freeze while
the player was offline and the inventory still has to be handed back on their next
join. The three item fields are Paper's binary item encoding, stored as base64 so
the plugin never has to know an item's format.

The file belongs to the plugin and is not meant to be edited by hand, but it is
fine to version it or back it up alongside the server.

Saving happens when a sanction is applied, when a report is closed, when a player
quits, when a freeze starts or ends, periodically in the background, and once more
on shutdown. A freeze is flushed the moment it is taken rather than waiting for
the periodic save, because the inventory it protects is emptied right after.

Every write goes to a temporary file that is then moved into place, so a crash
part way through a save leaves the previous file readable rather than a truncated
one. A save whose contents would be identical to what is already on disk is
skipped.

`settings.retention-days` drops sanctions once they are over, which stops
`data.yml` growing without bound on a long lived server. It is `0` by default and
only ever removes entries that are already closed: anything still in force stays,
and so does every note, since those are the records an appeal or a complaint about
a staff member is settled with. It runs once on enable and logs what it removed. Entries that
cannot be read are skipped with a console warning instead of failing the whole
load.

## Architecture

```
command/    Subcommands, the /mod dispatcher, and ModContext with what they need
config/     ModerationConfig and Messages, defensive config loading
listener/   Mute, Session, Freeze, Invsee
model/      Punishment, Report, FreezeRecord, PlayerRecord, Target, PunishmentType,
            ReportStatus
service/    ModerationService (core), TargetResolver, FreezeManager, MuteRegistry,
            CooldownService, PlayerRegistry, InvseeService
storage/    ModerationStore (contract) and YamlStore
util/       Text, Durations, Addresses, Ids
```

Two decisions explain the rest:

- **Commands carry no wiring.** Everything goes through `ModContext`, so adding a
  tool is one new class with no constructor plumbing.
- **Configuration is read in one place.** `ModerationConfig` does not throw on a
  bad value: it ignores it, falls back to a default and records the problem, which
  `/mod reload` then prints in chat.

## Tests

```bash
./gradlew test
```

102 tests, none of which need a server: duration parsing, IPv4 and IPv6
addresses, UUIDs with and without dashes, the state of a sanction over time, the
auto-punish rules, defensive `config.yml` loading, and the `data.yml` round trip.

What the tests do not cover: everything that needs a live server. The async chat
listeners, freezing, invsee and the expiry timers are verified by reading, not by
running.
