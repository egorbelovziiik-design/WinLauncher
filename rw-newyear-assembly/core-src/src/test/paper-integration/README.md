# Paper 1.21.4 integration probe

Runs a disposable, loopback-only Paper 1.21.4 server and a protocol client. It
checks item-model migration, old-pack migration and the actual resource-pack
packet, inventory/menu packets, asynchronous chat delegation and the delivered
message's colour/style, rank hierarchy, kits, TNT prices/purchases/effects and
cancelled/filtered explosions, new events and scoped entity cleanup, plus fresh
spawn creation, fail-closed backups and a chest restored from a ZIP backup.
With `--prebuilt /path/to/spawn-v66-template.zip`, it additionally checks that
an actual pre-generated world is loaded directly, rather than rebuilt at startup.
It does **not** render a Minecraft client or verify visuals.

Requirements: Java 21, `javac`, Node.js and `minecraft-protocol@1.62.0` in a
local `node_modules`, a Paper 1.21.4 paperclip JAR, its matching Mojang vanilla
server JAR, and a Spigot 1.21 API JAR with compile dependencies. The runner
verifies the vanilla JAR's SHA-256 against the Paper download context and never
downloads or changes the supplied JARs. It creates server data in a temporary
directory and removes it on exit. Review the Minecraft EULA before explicitly
accepting it for this temporary server.

```sh
python3 src/test/paper-integration/run.py \
  --paper /path/to/paper-1.21.4.jar --vanilla /path/to/mojang-1.21.4.jar \
  --api-classpath '/path/to/spigot-api.jar:/path/to/dependencies/*' \
  --node-modules /path/to/node_modules --accept-eula
```

`test-prebuilt-install.py` runs a separate, multi-start Paper test. It checks
replacement of a previously loaded spawn, restoration of an old chest and a
block changed immediately before unload from the backup, preservation of the old world
UUID, preservation of edits on repeat restart, restoration of the old world,
and a safe server stop when the template is corrupted or the world is a
symbolic link. `--only-symlink` runs just the linked-world case. It accepts the same `--paper`,
`--vanilla`, `--api-classpath`, `--core`, `--java`, `--javac` and
`--accept-eula` options; no network/client is needed. Pass `--prebuilt` with
`--existing-unknown` to verify that an incomplete old world is not replaced.

`test-event-search.py` runs a dedicated Paper regression for the event search.
It rejects an unloaded forced site without loading it, holds one asynchronous
chunk future open while checking the server heartbeat, checks the timeout and
release of a previously held ticket. It also tests the auto/manual switch,
low-heap and generation-disabled guards, wall-clock timeout, cancellation and
late callbacks, duplicate-JAR diagnosis, and the loaded archive's SHA-256 in
the startup log. It then exercises real Paper async chunk generation, a loaded
site with generation disabled, event restoration and world unload. The stalled
future and heap-pressure simulation are test doubles; actual chunk loading and
event construction in the final phase use Paper. Run it with the same
`--paper`, `--vanilla`, `--api-classpath`, `--core`, `--java`, `--javac` and
`--accept-eula` arguments as `test-prebuilt-install.py`.

`test-orbs.py` uses **two real protocol clients** on Paper to buy all 12
orbs through `/shars`, then exercises each offhand effect and lethal totem
consumption, exact max-health stacking, real healing cadence, removal and
spectator mode. It also covers teammate/safe-zone/world-PvP/radius checks for
frost aura, vanilla main-hand precedence, cancellation, six multi-charge totems
and their metadata, an occupied-hand race, final-charge consumption, the
inventory magnet, and accepted versus cancelled/recursive scroll hits. Real
bow and crossbow shots verify actual consumed ammunition (ordinary, custom,
storage and offhand), all nine custom arrow abilities, and effects on an
event-tagged mob. A protection listener
cancels real ice and explosive arrow hits; the tests check that no effects,
explosion or combat penalty leak through, and that an accepted explosive hit
still damages a nearby mob once. Real snowballs and tridents exercise both hands,
identity and impact. `--arrows-only` runs the projectile tests; `--boss-only`,
`--crossbows-only`, `--throws-only`, `--protect-only` and `--abilities-only`
select subsets. It needs
the same `--node-modules` as `run.py`; it is not a visual Minecraft-client test.

The template itself is generated with `build-spawn-template.py` in the
assembly root. Pass `--update-checksum` when regenerating it, then rebuild Core
with `build-core-hub.py` before running `build-assembly.py`. The template does
not contain `uid.dat`, player data, a session lock or runtime NPC entities.

Pass `--core`, `--java`, `--javac` or `--node` to override the defaults. The
core JAR under test must already have been built from the intended sources.
`--existing-unknown` separately checks preservation of a nonempty existing
spawn directory without a `level.dat`. `--mock-lp` adds a deliberately minimal
LuckPerms test double to check namespaced commands, async hierarchy checking,
and re-authorization; it is **not** a real LuckPerms integration test.
`--mock-wg` checks event placement against a minimal WorldGuard API double,
not the real WorldGuard plugin. Both mock modes may be combined. The optional `--luckperms /path/to/LuckPerms-Bukkit.jar` mode needs that plugin and
its runtime dependencies to be available, including network access on first
startup. The isolated server uses offline mode exclusively for this local test;
do not copy its generated settings to a live server.
