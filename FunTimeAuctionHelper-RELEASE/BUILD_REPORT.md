# Build report — FunTime Auction Helper Ultra Pro 1.0.0

Everything below was produced by the real CI runs listed here. Nothing in this file is
estimated or copied from a template; the numbers are the ones the build printed.

| | |
|---|---|
| Repository | `egorbelovziiik-design/WinLauncher` |
| Branch | `arena/9916b374-winlauncher` |
| Workflow | `FunTime Auction Helper - Build & Verify` (`.github/workflows/funtime-auctionhelper-build.yml`) |
| Green run | run **41196632960** (all jobs `success`, commit `9fdfe0a`); the JARs in this folder were built by that run |
| Toolchain | Gradle 9.8.2, Fabric Loom 1.19.3, Fabric Loader 0.20.1, Temurin JDK 8/21/25, official Mojang mappings |
| Runner | `ubuntu-latest` (Ubuntu 24.04 image), Xvfb + Mesa software OpenGL for the client runs |

## 1. Job results of the green run

| Job | Result |
|---|---|
| `Core unit tests + 3 Fabric JARs (1.16.5 / 1.21.4 / 1.21.11)` | success |
| `Real Minecraft client self test (MC 1.21.4)` | success |
| `Real Minecraft client self test (MC 1.21.11)` | success |

## 2. Unit tests (version independent core)

```
:test  →  21 tests completed, 0 failed, 0 skipped  (tests="21" skipped="0" failures="0" errors="0")
BUILD SUCCESSFUL
```

Suite: `FunTimeAuctionHelper/client-common/src/test/java/dev/funtime/auctionhelper/CoreSafetyTest.java`
(JUnit 5.10.2, runs on the plain JVM, no Minecraft needed).

Covered behaviour:

1. `automationStartsOffAndCannotBeEnabledWithoutAuthorizedApi`
2. `autoSwitchesRequireMasterAndMode`
3. `configSafetyResetAlwaysDisablesAutomation`
4. `emergencyStopClearsQueueAndIsRecorded`
5. `analysisOnlyModeNeverQueuesAnything`
6. `incompleteOfferIsNeverTraded`
7. `priceChangeAbortsTheOperation` (STALE, integration never called)
8. `networkErrorLeavesOperationUnverifiedAndBlocksBlindRetry`
9. `automatedBuyNeedsAKnownBalance`
10. `dailySpendingLimitIsEnforced`
11. `confirmedTradesOnlyCountOnceAndOnlyWhenConfirmed`
12. `priceStatisticsUseMedianNotAverageOfOutliers`
13. `flipAnalysisSubtractsFeesAndWarnsAboutWeakData`
14. `insufficientDataIsReportedInsteadOfGuessed`
15. `priceAmountParserHandlesServerFormats`
16. `filterEngineRespectsBoundsAndLists`
17. `strategiesOnlyProduceSignals`
18. `priceHistoryCsvRoundTripAndCleanup`
19. `configManagerRoundTripForcesAutomationOff`
20. `adapterParsesConfigurablePatternsAndFlagsUnverifiedProfiles`
21. `unknownScreenNeverThrowsAndIsCounted`

## 3. JARs

Built with `gradle remapJar` and inspected inside CI (content, entrypoint, bytecode level):

| File | Size | Classes under `dev/funtime/auctionhelper` | Entrypoint | Bytecode |
|---|---|---|---|---|
| `FunTimeAuctionHelper-1.16.5.jar` | 196 577 bytes | 97 | `mc1165.FunTimeAuctionHelper1165Mod` | major 52 (Java 8) |
| `FunTimeAuctionHelper-1.21.4.jar` | 198 179 bytes | 94 | `mc1214.FunTimeAuctionHelper1214Mod` | major 65 (Java 21) |
| `FunTimeAuctionHelper-1.21.11.jar` | 199 399 bytes | 94 | `mc12111.FunTimeAuctionHelper12111Mod` | major 65 (Java 21) |

Every JAR passed these automated assertions:

* `fabric.mod.json` present and `jq -e '.id == "funtimeauctionhelper" and .environment == "client"'`;
* at least 40 classes of the mod inside the archive;
* `dev/funtime/auctionhelper/HelperCore.class` present (the shared core is really inside);
* the version specific entrypoint class named by `fabric.mod.json` is inside the archive;
* `javap -verbose` reports the expected bytecode major version (52 / 65 / 65).

The JARs in this folder were produced by that green run and committed by the workflow step
"6. Commit the verified JARs into FunTimeAuctionHelper-RELEASE". They were additionally
re-checked outside CI: every archive opens without a corrupt entry, contains 105 / 102 / 102
entries, 97 / 94 / 94 mod classes, `fabric.mod.json` with `id = funtimeauctionhelper`,
`environment = client`, `version = 1.0.0` and the expected client entrypoint.

## 4. Real Minecraft client runs

The workflow starts a real Minecraft client with `xvfb-run gradle runClient -PftahSelftest=true`
(`LIBGL_ALWAYS_SOFTWARE=1`) and the mod writes `funtimeauctionhelper-selftest-<version>.json`
into `run/`; the job fails if the file is missing or if `status != "PASS"`.

Actual reports:

**Minecraft 1.21.4**

```json
{"status":"PASS","checksPassed":13,"details":{"minecraftVersion":"1.21.4","javaRuntime":"25.0.5","mode":"MANUAL_ASSISTANT","priceHistoryPoints":"1","adapterProfiles":"2"}}
```

**Minecraft 1.21.11**

```json
{"status":"PASS","checksPassed":13,"details":{"minecraftVersion":"1.21.11","javaRuntime":"25.0.5","mode":"MANUAL_ASSISTANT","priceHistoryPoints":"1","adapterProfiles":"2"}}
```

`javaRuntime` is the JVM the CI development client runs on; the distributed JARs keep the
bytecode levels from the table above.

The 13 in-game checks, executed in the running client:

1. master automation switch is OFF right after the client started;
2. the GUI opens through the same code path the Ё key uses;
3. the GUI closes with the open key;
4. the GUI reopens;
5. the GUI closes with `ESCAPE`;
6. `AUTHORIZED_AUTOMATION` is refused without a server API;
7. `MANUAL_ASSISTANT` is selectable;
8. the master switch is refused while no API is available;
9. the emergency stop is counted;
10. the emergency stop leaves automation OFF;
11. the price parser reads one offer from a sample lore (`Цена: 1 250`, `Кол-во: 64`);
12. the price history records the parsed offer locally;
13. automation stays OFF after the config is reloaded.

## 5. What was NOT verified

* **Minecraft 1.16.5 in-game run.** The JAR compiles against the real 1.16.5 official
  mappings, is remapped, and passes the content/entrypoint/bytecode (52) inspection, but
  the client is not started in CI: Loom 1.19 development launches use
  `net.fabricmc.devlaunchinjector`, which requires a Java 9+ runtime, while Minecraft
  1.16.5 only runs on Java 8. An earlier attempt (forcing a Java 8 launcher) failed with
  `Could not find or load main class net.fabricmc.devlaunchinjector.Main` and was removed
  instead of being reported as a success. Launching the 1.16.5 production JAR through a
  real launcher is the correct way to verify it and is not implemented in this repository.
* **FunTime server behaviour.** No FunTime account, server connection or server-side
  integration exists in CI. The adapter is exercised with synthetic screen snapshots only.
* **Automatic buying/selling.** No authorized server trading API is implemented, so
  `AUTHORIZED_AUTOMATION` cannot be enabled and no automated trade has ever been executed.
* **Visual/manual QA.** The GUI is rendered in the real client (that is how checks 2–5 run),
  but no screenshot comparison or human review was performed.

## 6. Reproduce

```bash
# core tests
cd FunTimeAuctionHelper/client-common && gradle test

# one JAR per version
cd FunTimeAuctionHelper/client-1.16.5  && gradle remapJar
cd FunTimeAuctionHelper/client-1.21.4  && gradle remapJar
cd FunTimeAuctionHelper/client-1.21.11 && gradle remapJar

# in-game self test of a single version
cd FunTimeAuctionHelper/client-1.21.11 && xvfb-run -a gradle runClient -PftahSelftest=true
cat run/funtimeauctionhelper-selftest-1.21.11.json
```
