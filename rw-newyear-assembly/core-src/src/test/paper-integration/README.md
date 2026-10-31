# Paper 1.21.4 integration probe

Runs a disposable, loopback-only Paper 1.21.4 server and a protocol client. It
checks item-model migration, old-pack migration and the actual resource-pack
packet, inventory/menu packets, asynchronous chat delegation and the delivered
message's colour/style, rank hierarchy, kits, TNT prices/purchases/effects and
cancelled/filtered explosions, new events and scoped entity cleanup, plus fresh
spawn installation and the removal of the old synchronous rebuild path.
With `--prebuilt /path/to/spawn-v73-template.zip`, it additionally checks that
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

`test-spawn-availability.py` connects a protocol player to fresh, existing,
main-world, damaged, symbolic-link, naming-conflict and repeat-restart fixtures.
It checks the prepared map, arrival, four ice walls, solid gate floors and
full-collision two-block guardrails, NPC models, 11 warps, saved `/spawn` and
`/kgspawn`, plus preservation of old worlds. Pass `--kingac` and `--kingclear`
to exercise their real plugins, the menu-star clear/undo and XRay world selection.
`test-spawn-upgrade-v73.py` boots the actual 7.2.2 Core, edits a chest and a
force-loaded chunk, saves a custom spawn, and then upgrades both the primary and
isolated paths; it verifies old world files remain byte-identical and reads a
copy of the old chest on Paper. An unloaded saved-spawn world is preserved in
`data.yml` but its location is not resolved until that world is loaded.
`blender-models/test_spawn_v73.py` checks the geometry and walking routes to all
four guarded gateways; the generator additionally checks every warp and NPC.
`test-spawn-compatibility.py` additionally tests standalone, disabled-Core and
incompatible-Core fallback behavior. These are not rendered client tests.

`test-prebuilt-install.py` runs a separate, multi-start Paper test. It checks
replacement of a previously loaded spawn, restoration of an old chest and a
block changed immediately before unload from the backup, preservation of the old world
UUID, preservation of edits on repeat restart, restoration of the old world,
and a safe server stop when the template is corrupted or the world is a
symbolic link. `--only-symlink` runs just the linked-world case. It accepts the same `--paper`,
`--vanilla`, `--api-classpath`, `--core`, `--java`, `--javac` and
`--accept-eula` options; no network/client is needed. Pass `--prebuilt` with
`--existing-unknown` to verify that an incomplete old world is not replaced.

`test-spawn-deferred.py` tests the embedded template, asynchronous queue/cancel,
restart-only replacement, UUID and chest preservation, an unmarked custom world,
main-world refusal, and simulated interrupted swaps before/after the rename. The
recovery test manipulates isolated fixture files; it is not a power-loss test.

`test-shop-promo.py` uses two protocol clients and real window clicks to check
full inventories, failed YAML saves, promo limits and replay. `--mock-player-points`
adds a PlayerPoints test double, not the real external plugin. `test-coinflip-restart.py`
uses two clients across a full restart, injects storage failures and checks escrow,
refunds, stale GUI slots and pagination. `test-mining-safety.py` uses real
`Player.breakBlock` calls and a late cancellable protection listener to test central
and neighbouring blocks, frost placement, full inventories, spawners and auto-mine.
The spawner cases also cover deterministic vanilla wear, a near-broken old pick,
external durability changes, offhand moves, missing tools, disconnect and plugin
shutdown before confirmation. They are not a power-loss test.
`test-mining-worldguard.py --worldguard /path/to/WorldGuard.jar --worldedit
/path/to/WorldEdit.jar` separately uses the real plugins to check protected central
and neighbouring blocks and frost placement. Neither test guarantees compatibility
with every protection plugin. `test-rtp-noworld.py` also moves the world border
far beyond the saved spawn and verifies that fallback navigation and recall do not
load its distant center or consume an item when no safe fallback is loaded.

`test-event-search.py` runs a dedicated Paper regression for the event search.
It rejects an unloaded forced site without loading it, holds one asynchronous
chunk future open while checking the server heartbeat, checks the timeout and
release of a previously held ticket. It also tests the auto/manual switch,
low-heap and generation-disabled guards, wall-clock timeout, cancellation and
late callbacks, duplicate-JAR diagnosis, and the loaded archive's SHA-256 in
the startup log. It then exercises real Paper async chunk generation, a loaded
site with generation disabled, event restoration and world unload. Its normal
Overworld phase checks that generation starts and can be cancelled; it does not
claim a completed natural-world search. Test fixtures are loaded asynchronously.
The stalled future and heap-pressure simulation are test doubles. Run it with
the same `--paper`, `--vanilla`, `--api-classpath`, `--core`, `--java`, `--javac`
and `--accept-eula` arguments as `test-prebuilt-install.py`.

`test-event-placement.py` tests a nearby safe site within four pinned chunks,
shallow slopes, restoration of natural flowers/leaves, refusal of persistent
leaves, a chest added after its column was scanned, claim and mock WorldGuard
fail-closed behaviour, displaced world spawn/border without synchronously loading
the border-center chunk, all 14 bounded event templates, all five real bosses
receiving damage, ticket release and the server heartbeat. `--natural` runs a
separate ordinary Overworld with no forced coordinates or candidate injection:
a console airship and an automatically selected event must both be constructed
and cleaned up. The search tries nearer sites before expanding, can skip wholly
oceanic first chunks, and is bounded to 32 areas and 64 async chunk requests.
`--world-seed` and `--search-seed` allow additional deterministic worlds/search sequences. `--worldguard path --worldedit path` selects a third,
real-plugin test of WorldGuard region overlap, clear areas and fail-closed
behaviour; provide compatible WorldGuard and WorldEdit JARs separately. The
default placement test uses a mock WG, not the real plugin.

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

`test-full-assembly.py` extracts the actual v7 ZIP into a disposable Paper
server, disables the KingConnect cloud in that isolated test, requires a
placeholder bridge credential, and verifies that the five bundled plugins
enable, the prepared spawn installs and an authenticated protocol client can
enter while declining the optional pack. A probe checks the actual spawn blocks,
NPCs, 11 warps, clear, anti-XRay and navigation commands. It does not test live payment delivery, network
SSO, other unbundled plugins or a visual game client.

`test-winter.py` exercises all 100 runes with actual use-item packets, each
orb's active offhand burst and the shared cooldown, the mega apple's accepted
and cancelled consume event, and catalogue purchase/inventory capacity. The
`--mock-player-points` option supplies a deliberately small PlayerPoints API
double to verify accepted, rejected, altered and throwing writes, a read
failure, and compensation when durable YAML saving is refused. This is **not**
a real PlayerPoints integration, a guarantee across process/power failure,
or a visual Minecraft-client test. The runner also checks the three stacked
sculptures and their hand touching the star in the prepared world.

`test-new-artifacts.py` uses a protocol client to exercise all 50 new items,
then checks the immediate utilities, plugin-cancelled healing/food/potions,
protected interaction, inventory merging, six-page catalog and console giving.
`test-moderation.py` checks the display-model mute, online name/IP ban ordering,
transport as sack passenger, offline/literal IP and unban, portal restoration,
duplicate/cancelled animation, client disconnect and orderly plugin disable.
These tests exercise server-side state and packets, not a rendered Minecraft client.

`test-new-features.py` exercises the airship's protected, non-repeatable scrap,
expiry and terrain restoration; opt-in crown scoring, accepted/cancelled damage
and rewards; trade confirmation, PDC/lore, inventory capacity and drag/hotbar
cancellation; and order reservation, partial sale, mailbox, cancellation and a
simulated `data.yml` write failure. Most clicks are synthetic Bukkit events.
`test-wire-features.py` complements it with actual protocol block-use and window
click packets from two clients, including an administrator clicking the airship
button in the event menu. Both prepare their remote fixtures asynchronously and
check the client/server exit codes and Paper watchdog output.
`test-trade-crash.py` and `test-order-crash.py`
SIGKILL the disposable Paper process between commit steps, restart it, and verify
recovery without repeating the item transfer or payment. The kill points are
set up via the same production journal methods. `test-data-failclosed.py`
separately checks malformed YAML, a dangling data-file symlink, and unidentifiable
corrupt trade/order journals. These tests do not simulate power loss or third-party
plugins directly changing player data.

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
