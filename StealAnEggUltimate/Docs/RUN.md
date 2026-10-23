# Running STEAL AN EGG ULTIMATE HUB

## Quick start (no Roblox needed)

1. Double-click `dist\StealAnEggUltimate.exe`.
2. The hub opens in **demo mode** with 26 generated eggs.
3. Click **CONNECT** while Roblox is closed → status becomes *WAITING FOR ROBLOX*.
   Using the demo: go to **EGG**, enable *Egg ESP* (already on), press **SCAN NOW**.
4. Open **COMMANDS**, type `hub.eggs()` and press **EXECUTE** (or Enter tab → EXECUTE).
5. Change a theme in **VISUALS**, close the app, reopen it — settings were auto-saved.

## With Roblox

### A. Overlay only (no test place)
1. Start Roblox, join any experience in **windowed** or **borderless** mode.
2. Start the hub → **CONNECT**.
3. Press **RIGHT SHIFT** → the menu appears over the game; drag the header to move it.
4. The hub will not send any command to a third-party experience. Movement toggles show
   `TEST PLACE` and stay in preview; ESP uses demo data unless a snapshot is loaded.

### B. Full experience (your own test place)
1. Install the Luau modules — see `RobloxStudio\TestPlaceModules\README.md`.
   * Game Settings → Security → **Allow HTTP Requests: ON**
2. In the hub, check **SETTINGS → ROBLOX BRIDGE** shows `http://127.0.0.1:8787` and
   **BRIDGE** is listening (status bar / diagnostics).
3. **Play** in Studio, then **CONNECT** in the hub.
4. The EGG tab data-source badge switches to **OFFICIAL PLACE**, the player card fills in,
   and PLAYER/AUTOMATION toggles become live.

### C. Snapshot file mode (any place, read-only)
1. Put a JSON file in `%AppData%\StealAnEggUltimate\snapshots\` (a sample ships in
   `Config\sample-snapshot.json`).
2. The hub loads it automatically; the badge shows **SNAPSHOT**.
3. ESP, filters, tracker, value/distance display and non-rare notifications work on the
   real coordinates of that snapshot. Movement stays in preview.

## Keyboard

| Key | Action |
| --- | --- |
| RIGHT SHIFT (configurable in SETTINGS → Keybind manager) | open/close the in-game menu |
| ESC | close the menu |
| F / X / T (configurable, applied by the Luau client module) | fly / speed boost / teleport-to-cursor |

The menu key uses a passive low-level hook: the key is observed but never blocked, so
Roblox still receives Right Shift.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| “Roblox not found” | Start Roblox Player (not Studio) and press CONNECT again; the hub auto-attaches when the client appears. |
| Overlay visible but menu does not open | Global hook blocked by security software, or the menu key was changed. Try **SETTINGS → Keybind manager**, then **OPEN MENU**. |
| Bridge offline | Studio blocks loopback HTTP, or “Allow HTTP Requests” is off. Use snapshot mode (section C). |
| Config damaged | The hub backs it up as `settings.corrupt-*.json` and restores defaults automatically. |
| App already running | Single-instance guard — close the existing hub window. |

Logs: **SETTINGS → OPEN LOGS** (`%AppData%\StealAnEggUltimate\logs`).
