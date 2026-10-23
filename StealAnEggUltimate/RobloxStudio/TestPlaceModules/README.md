# Official STEAL AN EGG test place — Luau modules

These files are the **only** half of the hub that runs inside Roblox, and they run
**inside your own place** — not inside anybody else's experience.

| File | Put it in | Type |
| --- | --- | --- |
| `EggWorld.lua` | `ServerScriptService` → rename to `EggWorld` | ModuleScript |
| `SAEUltimateBridge.server.lua` | `ServerScriptService` → rename to `SAEUltimateBridge` | Script (server) |
| `SAEUltimateClient.client.lua` | `StarterPlayer` → `StarterPlayerScripts` | LocalScript |

## Install

1. Open **Roblox Studio** and create (or open) a baseplate place.
2. **Game Settings → Security → Allow HTTP Requests: ON** (required for the bridge).
3. Insert the three files above into the locations from the table.
   * `EggWorld` must be named exactly `EggWorld` — the bridge does `WaitForChild("EggWorld")`.
4. Press **Play**. The bridge script builds an egg island (24 eggs, 6 rarities,
   ProximityPrompts, respawn timers, coins leaderstat).
5. Start **StealAnEggUltimate.exe** on the same PC and click **CONNECT**, then press
   **Right Shift**.

The hub listens on `http://127.0.0.1:8787` (loopback only). The bridge polls
`GET /state` every 0.2 s and posts `POST /snapshot` every 0.5 s, plus `POST /event`
for collected eggs / logs.

## If the bridge cannot connect

Some Roblox Studio builds block requests to loopback addresses. The place will
print `[SAEU-Bridge] bridge unreachable ...` in the Output window; everything in
the place still works by hand (eggs, prompts, coins). In that case the hub stays in
demo/snapshot mode and clearly says so in the **DATA SOURCE** card.

You can also use the read-only snapshot workflow:

1. In Studio, call `print(game:GetService("HttpService"):JSONEncode(require(game.ServerScriptService.EggWorld).Snapshot(game.Players:GetPlayers()[1])))`
   from the Command Bar.
2. Copy the JSON into `%AppData%\StealAnEggUltimate\snapshots\my-place.json`.
3. The hub picks the file up automatically (or press **RELOAD SNAPSHOT**).

## What each module does

**EggWorld** (server, authoritative)
* spawns the island, 24 eggs with rarity/value/zone, neon rare eggs, labels
* collection by ProximityPrompt, respawn after 12 s, coin leaderstats
* helper API for the bridge: `Nearest`, `CollectNearest`, `Collect`, `SpawnBonus`,
  `SpendCoins`, `Snapshot`, `SetHeading`

**SAEUltimateBridge** (server)
* pulls feature flags/commands from the hub, applies them:
  * Speed / JumpPower → Humanoid properties
  * Teleport → `HumanoidRootPart.CFrame`
  * Auto Collect / Farm / Steal / Return / Buy / Sell → server-side movement + collection
  * Noclip → collision toggles
* posts snapshots (eggs + player + camera heading) back to the hub
* reports `collected` / `sold` / `notice` events so the hub can toast them
* rebroadcasts movement flags to clients

**SAEUltimateClient** (LocalScript)
* Fly (WASD + Space/Ctrl, camera-relative `BodyVelocity`)
* Noclip, Infinite Jump
* Teleport-to-cursor key
* reports camera yaw so the hub ESP projection matches the real view
* keybinds come from the hub Settings → Player tab (`FlyKey`, `SpeedBoostKey`, `TeleportKey`)

Every flag defaults to `false`. Disconnect the hub (or don't start it) and the place
behaves like a normal Roblox experience — nothing is forced on.

## Honest scope

* These modules are for **your own** place. The hub cannot and does not run code in
  third-party experiences; there is no injection, memory editing or anti-cheat bypass.
* Server-side automation applies to the first player in the place (the test place is
  meant for a single tester).
* Loopback HTTP depends on Studio's HttpService policy — see above for the fallback.
