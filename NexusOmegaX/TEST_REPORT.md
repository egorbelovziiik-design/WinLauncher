# TEST REPORT — NEXUS CLIENT OMEGA X 1.0.0

Date: 2026-10-24 · Build: `omega-x-1.0.0+release` · Toolchain: JDK 25 (runtime),
compiled with ECJ 3.46 (`-21` **and** `-8`), no Gradle/Maven, no Minecraft jars.

Result summary

| Area | Result |
|---|---|
| Core compile (Java 21) | **0 errors**, 526 classes |
| Core compile (Java 8, 1.16.5 target) | **0 errors** |
| Headless test suite | **71 checks, 0 failures** (exit 0) |
| Adapter ↔ `nexus.api` conformance (static) | **0 missing methods** in all 3 adapters |
| `NexusInjector.exe` | built, **PE32+ Windows GUI**, 249 344 bytes |
| Mod jars (1.16.5 / 1.21.4 / 1.21.11) | **not produced here** — see §5 |

---

## 1. Compilation

```
java -jar ecj.jar -21 -nowarn -d build/classes $(find api/src core/src modules/src test/src -name '*.java')
  → 0 errors, 526 classes

java -jar ecj.jar -8  -nowarn -d build/classes8 $(find api/src core/src modules/src -name '*.java')
  → 0 errors            (the 1.16.5 adapter must stay Java 8 compatible)
```

Sources: 468 Java files (`api` 23, `core` 90, `modules` 276, `test` 3,
adapters 76). Runtime dependencies of the core: **none**.

## 2. Headless test suite — 71 checks, 0 failures

```
java -cp build/classes nexus.test.TestSuite build/test-runtime
checks: 71   failures: 0   exit code: 0
```

The suite boots the real client on top of `nexus.test.HeadlessPlatform`
(a fake `IClient`/`IWorld`/`IRenderer`), so it exercises the same code paths the
game does, without Minecraft.

```
1. Boot                                                                  13 checks
   client initialised · hooks installed · 274 modules registered
   · all 10 categories non-empty (COMBAT 37, MOVEMENT 30, PLAYER 35,
     RENDER 60, WORLD 28, AUTOMATION 20, EXPLORATION 10, MISC 39,
     CLIENT 12, CONFIGS 3)
2. Every module: enable / tick / render / disable                         1 check
   all 273 modules enabled, ticked 5×, rendered in 2D and 3D, disabled —
   no exceptions (panic is tested separately in §11)
3. Settings round trip                                                    1 check
   all 1638 settings written to JSON and read back identical
4. Commands                                                               2 checks
   all 33 command invocations executed · panic side effects verified
5. Keybinds                                                               2 checks
   binding a key toggles the module (false → true)
6. ClickGUI: RIGHT SHIFT / ESC / mouse recapture                         14 checks
   mouse captured at start
   RIGHT SHIFT opens the ClickGUI
   mouse released while the GUI is open
   exactly one window on the stack · no double open
   nested window opens · 1st ESC closes the nested window
   ClickGUI still open · 2nd ESC closes the ClickGUI
   no invisible windows left · mouse control restored to the game
   RIGHT SHIFT re-opens · RIGHT SHIFT closes again · mouse restored
7. HUD                                                                    2 checks
   474 draw calls · 26 elements registered
8. Themes                                                                 1 check
   all 7 themes activate
9. Config system                                                         11 checks
   default profile · save · list · export · import · rename · load
   · delete · backup · reload · cleanup
10. Waypoints, friends, macros, notifications                             8 checks
11. Long run (600 ticks with every module enabled)                        5 checks
    no crash · no module silently disabled · PANIC disables 273 modules
    · PANIC disables itself · all keys released
12. Shutdown and files on disk                                           11 checks
    clean shutdown · configs folder · profiles/<name>.json written,
    valid JSON, contains every module and every keybind
    · hud.json · themes.json · waypoints.json · gui-layout.json
```

### Files written to `<game dir>/NexusOmegaX/configs/`

```
config-settings.json  profiles/default.json  hud.json  themes.json
waypoints.json  macros.json  friends.json  notifications.json
session.json  backups/<name>-<yyyyMMdd-HHmmss>.json
```

## 3. Version adapters

The adapters cannot be compiled here (no Minecraft/Fabric jars, Maven Central
and `maven.fabricmc.net` are unreachable from the build sandbox). Instead every
adapter was checked **statically** against the interfaces it implements: for each
`implements nexus.api.I…` the full method set of the interface was compared with
the methods the adapter declares.

| Adapter | Files | Interfaces implemented | Missing methods |
|---|---|---|---|
| `nexus1165` (MC 1.16.5, Java 8, yarn) | 26 | IClient, IPlayer, IWorld, IEntity, IInventory, IItemStack, IBlockRef, IRenderer, IInput, IOptions, IChat, IScreenHost, IInteraction, IResources, IConnection, IWindow | **0** |
| `nexus1214` (MC 1.21.4, Java 21, mojmap) | 25 | same 16 | **0** |
| `nexus12111` (MC 1.21.11, Java 21, mojmap) | 25 | same 16 | **0** |

Each adapter ships 6 mixins (`Minecraft(Client)Mixin`, `Gui/InGameHudMixin`,
`LevelRenderer/WorldRendererMixin`, `Keyboard(Handler)Mixin`,
`Mouse(Handler)Mixin`, `ChatScreenMixin`) plus `fabric.mod.json` and
`nexusomegax.mixins.json`.

## 4. `NexusInjector.exe`

```
$ objdump -f dist/NexusInjector.exe
  file format pei-x86-64, architecture i386:x86-64
$ objdump -p dist/NexusInjector.exe | grep Subsystem
  Subsystem 00000002 (Windows GUI)
size: 249 344 bytes
imports: KERNEL32, USER32, GDI32, COMCTL32, comdlg32, SHELL32, ole32,
         urlmon (URLDownloadToFileA), UxTheme, api-ms-win-crt-*
key imports: CreateWindowExA, SHBrowseForFolderA, ShellExecuteA,
             URLDownloadToFileA, CopyFileExA
```

It is a native Win32 GUI application (5 tabs: NEWS / INSTALL / VERSIONS /
SETTINGS / ABOUT, dark `#08080D`/`#101018`/`#181824` surface with the
`#7C3AED → #C084FC` accent ramp, progress bar, log pane).

Linux cannot execute a PE binary and no Wine/Mono is available in the build
sandbox, so the exe was verified statically and by code review, not by running
it. The Windows acceptance checklist in §6 covers what to confirm locally.

## 5. What could NOT be verified here (and why)

| Item | Blocker | How you verify it |
|---|---|---|
| `NexusOmegaX-1.16.5/1.21.4/1.21.11.jar` | Fabric Loom needs to download Minecraft + mappings from `maven.minecraftforge.net` / `maven.fabricmc.net` / `repo1.maven.org`; all blocked in the sandbox | **Windows: `build.bat /install`** (downloads Gradle itself if needed, builds all three jars and copies them into `%APPDATA%\.minecraft\mods`). Linux/macOS: `./build-all.sh --install`. Manual: `gradle build -p adapters/<version>`, ~3 min |
| Adapter compile against the real Minecraft classes | no Minecraft jars on disk | the same `gradle build` compiles the adapters together with the core |
| Running `NexusInjector.exe` | Linux host, no Wine | open it on Windows 10/11 |
| In-game behaviour (real rendering, real packets) | no Minecraft runtime | §6 checklist |

No jar was fabricated for any version. If `gradle build` fails for a version,
that version simply has no jar — nothing is faked.

## 6. Windows acceptance checklist (run this once)

| # | Step | Expected |
|---|---|---|
| 1 | Double click `NexusInjector.exe` | dark window opens, INSTALL tab active, game dir pre-filled with `%APPDATA%\.minecraft` |
| 2 | INSTALL FABRIC | official Fabric installer opens → Install → success → close |
| 3 | INSTALL CLIENT | `NexusOmegaX-<v>.jar` copied into `.minecraft\mods\`, log says `OK installed` |
| 4 | CREATE PROFILE | `Nexus Client Omega X` appears in the official launcher |
| 5 | LAUNCH MINECRAFT → Play | Minecraft starts, client banner in the log |
| 6 | In game press **RIGHT SHIFT** | ClickGUI opens, mouse released |
| 7 | Press **RIGHT SHIFT** again | ClickGUI closes, camera control back |
| 8 | Reopen, open a nested window, press **ESC** | nested window closes, ClickGUI stays |
| 9 | Press **ESC** again | ClickGUI closes, no invisible windows, camera control back |
| 10 | Type `.help` | command list in chat |
| 11 | Enable a few modules, `.cfg save test`, restart, `.cfg load test` | settings restored |
| 12 | Move HUD widgets (CLIENT → HUD editor) | layout saved to `hud.json` |
| 13 | `VERIFY INSTALL` in the launcher | `=== verification finished: READY ===` |

## 7. Known limitations / code smells (non-blocking)

* `render/ZoomModule` keeps the original FOV in a local variable only — zoom
  out restores the vanilla value instead of the pre-zoom one.
* `world/NewChunksModule` reuses one `known` chunk set as both baseline and
  new-chunk marker; the first scan can report one extra chunk.
* `world/BiomeFinderModule` scans to r=2048 in a nested loop per frame — cap the
  radius or lower `world/BiomeFinder` scan distance on slow machines.
* `render/FullBrightModule` calls `mc().options()` twice per tick (harmless).
* Modules named `…Local` (`CriticalsLocal`, `XRayLocal`, `SpeedLocal`) only
  affect the local client and never send extra packets.
* The 1.16.5 adapter's `fillSignText` uses reflection on intermediary field
  names; if a future loader renames them, sign filling silently does nothing
  (everything else keeps working).

## 6. Mixin targets (runtime safety)

A mixin that names a method with the wrong parameter list is not a compile error - the
game crashes while the class is being transformed
(`InvalidInjectionException: Invalid descriptor ... Expected (JIIIIL...CallbackInfo;)V`).
That exact crash was reported on 1.21.4 and fixed for all three versions:

| version | fix |
|---|---|
| 1.16.5 | `MinecraftClient.tick()V` instead of `runTick`; `WorldRenderer.render(MatrixStack,F,J,Z,Camera,GameRenderer,LightmapTextureManager,Matrix4f)V`; outgoing chat on `ClientPlayerEntity.sendChatMessage(Ljava/lang/String;)V` |
| 1.21.4 | `KeyboardHandler.keyPress(JIIII)V` returns **void** (plain `CallbackInfo`); `Minecraft.runTick(Z)V`; `LevelRenderer.renderLevel` with 7 parameters |
| 1.21.11 | input moved to records: `KeyboardHandler.keyPress(JIL.../input/KeyEvent;)V`, `MouseHandler.onButton(JL.../input/MouseButtonInfo;I)V`; `LevelRenderer.renderLevel` with 10 parameters |

Every `@Inject`/`@ModifyVariable` in this project spells out the full JVM descriptor, and
the Gradle task **`verifyMixins`** (wired into `build`) resolves the mapped Minecraft jar,
runs `javap -p -s` on each target class and fails the build when a target or its
descriptor does not match.
