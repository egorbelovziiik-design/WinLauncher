using System.IO;
using System.Net.Http;
using System.Text;
using System.Windows.Input;

namespace StealAnEggUltimate;

/// <summary>
/// Headless functional self-test (`StealAnEggUltimate.exe --selftest`).
///
/// It exercises the real classes the UI uses: settings persistence + corruption
/// recovery, the command interpreter, the egg world / ESP projection / filters,
/// snapshot ingestion, the loopback bridge HTTP round-trip, demo automation and
/// asset presence. UI-only pieces that need a live Roblox client are reported as
/// SKIPPED instead of being claimed as passing.
/// </summary>
public static class SelfTest
{
    private const string ReportFileName = "selftest-report.txt";

    private static readonly List<string> Lines = new();
    private static int _passed;
    private static int _failed;
    private static int _skipped;

    public static int Run(Logger log)
    {
        Lines.Clear();
        _passed = _failed = _skipped = 0;

        var originalConfig = File.Exists(AppPaths.ConfigFile) ? File.ReadAllText(AppPaths.ConfigFile) : null;
        var startedAt = DateTime.UtcNow;

        Lines.Add($"STEAL AN EGG ULTIMATE HUB — self-test");
        Lines.Add($"Version      : {AppInfo.Version}");
        Lines.Add($"Started (UTC): {startedAt:O}");
        Lines.Add($"OS           : {Environment.OSVersion} ({(Environment.Is64BitOperatingSystem ? "x64" : "x86")})");
        Lines.Add($"Runtime      : .NET {Environment.Version}");
        Lines.Add($"Base dir     : {AppContext.BaseDirectory}");
        Lines.Add($"Data dir     : {AppPaths.DataRoot}");
        Lines.Add(new string('-', 72));

        try
        {
            RunSettingsTests();
            RunCommandTests();
            RunEggWorldTests();
            RunSnapshotTests();
            RunBridgeTests().GetAwaiter().GetResult();
            RunAutomationTests();
            RunAssetTests();
            RunPlatformTests();
        }
        catch (Exception ex)
        {
            Fail("self-test harness", ex.Message);
            log.Error("Self-test harness crashed.", ex);
        }
        finally
        {
            try
            {
                if (originalConfig is null)
                {
                    if (File.Exists(AppPaths.ConfigFile)) File.Delete(AppPaths.ConfigFile);
                }
                else
                {
                    File.WriteAllText(AppPaths.ConfigFile, originalConfig);
                }
            }
            catch (Exception ex)
            {
                log.Warn($"Could not restore the config after self-test: {ex.Message}");
            }
        }

        Lines.Add(new string('-', 72));
        Lines.Add($"SUMMARY: {_passed} passed, {_failed} failed, {_skipped} skipped · duration {(DateTime.UtcNow - startedAt).TotalSeconds:0.00}s");
        Lines.Add(_failed == 0 ? "RESULT: PASS" : "RESULT: FAIL");

        var text = string.Join(Environment.NewLine, Lines) + Environment.NewLine;
        try
        {
            AppPaths.EnsureCreated();
            File.WriteAllText(Path.Combine(AppPaths.DataRoot, ReportFileName), text);
        }
        catch (Exception ex)
        {
            log.Error("Could not write the self-test report.", ex);
        }

        try
        {
            Console.WriteLine(text);
        }
        catch
        {
            // GUI subsystem apps have no console.
        }

        log.Info($"Self-test finished: {_passed} passed, {_failed} failed, {_skipped} skipped.");
        return _failed == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private static void RunSettingsTests()
    {
        var manager = new SettingsManager(new Logger());

        try
        {
            var settings = SettingsManager.CreateDefaults();
            settings.Player.WalkSpeed = 77.5;
            settings.Visual.Theme = "Cyber";
            settings.Overlay.MenuKey = "Insert";
            settings.Features[FeatureIds.EggEsp] = false;

            var saved = manager.Save(settings);
            var loaded = manager.Load();

            Check("Settings save + load (JSON round-trip)",
                saved && loaded.Player.WalkSpeed == 77.5 && loaded.Visual.Theme == "Cyber"
                && loaded.Overlay.MenuKey == "Insert" && loaded.Features[FeatureIds.EggEsp] == false,
                $"walkSpeed={loaded.Player.WalkSpeed}, theme={loaded.Visual.Theme}, menuKey={loaded.Overlay.MenuKey}");
        }
        catch (Exception ex)
        {
            Fail("Settings save + load (JSON round-trip)", ex.Message);
        }

        try
        {
            File.WriteAllText(AppPaths.ConfigFile, "{ this is not valid json ");
            var recovered = manager.Load();
            var backupExists = Directory.GetFiles(AppPaths.ConfigDir, "settings.corrupt-*.json").Length > 0;

            Check("Corrupt config recovery", recovered is not null && backupExists,
                $"backup={backupExists}, eggs.esp={recovered?.Features.GetValueOrDefault(FeatureIds.EggEsp)}");
        }
        catch (Exception ex)
        {
            Fail("Corrupt config recovery", ex.Message);
        }

        try
        {
            var live = SettingsManager.CreateDefaults();
            live.Player.WalkSpeed = 150;
            live.Visual.Theme = "Ember";
            live.Features[FeatureIds.Fly] = true;

            SettingsCloner.CopyInto(live, SettingsManager.CreateDefaults());
            Check("Config reset (SettingsCloner)", Math.Abs(live.Player.WalkSpeed - 16) < 0.01
                                                 && live.Visual.Theme == "Violet"
                                                 && live.Features[FeatureIds.Fly] == false,
                $"walkSpeed={live.Player.WalkSpeed}, theme={live.Visual.Theme}");
        }
        catch (Exception ex)
        {
            Fail("Config reset (SettingsCloner)", ex.Message);
        }

        try
        {
            var defaults = SettingsManager.CreateDefaults();
            Check("Feature registry defaults",
                defaults.Features.TryGetValue(FeatureIds.EggEsp, out var esp) && esp
                && defaults.Features.TryGetValue(FeatureIds.AutoFarm, out var farm) && !farm,
                $"{defaults.Features.Count} feature flags seeded");
        }
        catch (Exception ex)
        {
            Fail("Feature registry defaults", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Command interpreter
    // ------------------------------------------------------------------

    private static void RunCommandTests()
    {
        var manager = new CommandManager(new Logger());
        var calls = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);

        void Hit(string name) => calls[name] = calls.GetValueOrDefault(name) + 1;

        manager.Attach(new CommandContext
        {
            IsOverlayVisible = () => true,
            ShowOverlay = () => Hit("show"),
            HideOverlay = () => Hit("hide"),
            ToggleOverlay = () => Hit("toggle"),
            ScanEggs = () => Hit("scan"),
            SetRareNotifications = flag => { Hit("notify"); calls["notify.value"] = flag ? 1 : 0; },
            SetAutomation = start => Hit(start ? "auto.start" : "auto.stop"),
            SaveConfig = () => { Hit("save"); return true; },
            LoadConfig = () => { Hit("load"); return true; },
            Print = _ => { },
            Notify = _ => { },
            Teleport = (_, _, _) => Hit("teleport"),
            Eggs = () => Array.Empty<Egg>(),
            SetInterval = ms => { Hit("interval"); calls["interval.value"] = ms; },
            SetEspColor = _ => Hit("color"),
            TrackBestEgg = () => Hit("trackbest"),
            ClearConsole = () => Hit("clear"),
            IsConnected = () => true
        });

        try
        {
            var script = string.Join("\n", new[]
            {
                "-- full supported-command sweep",
                "overlay.show()",
                "overlay.hide()",
                "overlay.toggle()",
                "egg.scan()",
                "egg.trackbest()",
                "egg.notify(true)",
                "esp.color(\"#22D3EE\")",
                "player.teleport(12, 34.5, -6)",
                "automation.interval(900)",
                "automation.start()",
                "automation.stop()",
                "settings.save()",
                "settings.load()",
                "hub.status()",
                "console.clear()",
                "help()"
            });

            var result = manager.Execute(script);

            var errors = result.Lines.Where(l => l.StartsWith("error", StringComparison.OrdinalIgnoreCase)).ToList();

            Check("Command interpreter: supported commands",
                result.Ok
                && calls.GetValueOrDefault("show") == 1
                && calls.GetValueOrDefault("hide") == 1
                && calls.GetValueOrDefault("toggle") == 1
                && calls.GetValueOrDefault("scan") == 1
                && calls.GetValueOrDefault("teleport") == 1
                && calls.GetValueOrDefault("interval.value") == 900
                && calls.GetValueOrDefault("notify.value") == 1
                && calls.GetValueOrDefault("auto.start") == 1
                && calls.GetValueOrDefault("auto.stop") == 1,
                $"ok={result.Ok}, calls={calls.Count}, errors={(errors.Count == 0 ? "none" : string.Join(" | ", errors))}");
        }
        catch (Exception ex)
        {
            Fail("Command interpreter: supported commands", ex.Message);
        }

        try
        {
            var bad = manager.Execute("bad.command()\negg.notify(\"yes\")\nplayer.teleport(1, 2)\nthis is not a call");
            Check("Command interpreter: rejects invalid input",
                !bad.Ok && bad.Lines.Count(l => l.Contains("error", StringComparison.OrdinalIgnoreCase)) >= 3,
                string.Join(" | ", bad.Lines));
        }
        catch (Exception ex)
        {
            Fail("Command interpreter: rejects invalid input", ex.Message);
        }

        try
        {
            var empty = manager.Execute("   ");
            Check("Command interpreter: empty input handled", !empty.Ok && empty.Lines.Count > 0, string.Join(" | ", empty.Lines));
        }
        catch (Exception ex)
        {
            Fail("Command interpreter: empty input handled", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Egg world / ESP / filters
    // ------------------------------------------------------------------

    private static void RunEggWorldTests()
    {
        var settings = SettingsManager.CreateDefaults();
        var eggs = new EggFeatures(new Logger());

        try
        {
            Check("Demo egg world generated", eggs.Eggs.Count == 26 && eggs.IsDemoMode,
                $"{eggs.Eggs.Count} eggs, mode={eggs.SourceModeText}");
        }
        catch (Exception ex)
        {
            Fail("Demo egg world generated", ex.Message);
        }

        try
        {
            for (var i = 0; i < 10; i++)
                eggs.Update(TimeSpan.FromMilliseconds(33), settings, 1280, 720);

            var visible = eggs.GetVisibleEggs(settings);
            Check("ESP projection + visibility",
                eggs.Eggs.All(e => e.Distance >= 0) && visible.Count > 0,
                $"{visible.Count} visible, {eggs.RareVisible} rare");
        }
        catch (Exception ex)
        {
            Fail("ESP projection + visibility", ex.Message);
        }

        try
        {
            var target = eggs.Eggs.OrderBy(e => e.Distance).First();
            eggs.DemoWalkTowards(target.Position, 120);
            eggs.Update(TimeSpan.FromMilliseconds(33), settings, 1280, 720);

            var collected = eggs.CollectNearest();
            Check("Demo collection + pending value",
                collected && eggs.PendingValue > 0 && eggs.CollectedCount > 0,
                $"pending={eggs.PendingValue}, collected={eggs.CollectedCount}");

            var sold = eggs.SellAll();
            Check("Sell all moves pending value into the balance",
                sold > 0 && eggs.PendingValue == 0 && eggs.SessionCoins == sold,
                $"sold={sold}, balance={eggs.SessionCoins}");
        }
        catch (Exception ex)
        {
            Fail("Demo collection + pending value", ex.Message);
        }

        try
        {
            settings.Eggs.ShowRare = false;
            var filtered = eggs.GetVisibleEggs(settings);
            var hasRare = filtered.Any(e => e.Rarity == EggRarity.Rare);

            settings.Eggs.ShowRare = true;
            settings.Eggs.MaxDistance = 20;
            var nearOnly = eggs.GetVisibleEggs(settings);

            Check("Egg rarity + distance filters", !hasRare && nearOnly.All(e => e.Distance <= 20),
                $"rareVisible={hasRare}, nearOnly={nearOnly.Count}");
        }
        catch (Exception ex)
        {
            Fail("Egg rarity + distance filters", ex.Message);
        }

        try
        {
            settings.Features[FeatureIds.BestEggFinder] = true;
            eggs.Update(TimeSpan.FromMilliseconds(33), settings, 1280, 720);

            var tracked = eggs.TrackBest();
            var best = eggs.BestEgg;
            Check("Egg tracker + best egg finder",
                tracked && eggs.TrackedTarget is not null && best is not null
                && eggs.TrackedTarget.Value >= (eggs.Eggs.Count == 0 ? 0 : 0),
                $"tracked={(eggs.TrackedTarget is null ? "none" : eggs.TrackedTarget.Name)}, best={(best is null ? "none" : best.Name)}");
        }
        catch (Exception ex)
        {
            Fail("Egg tracker + best egg finder", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Snapshots
    // ------------------------------------------------------------------

    private static void RunSnapshotTests()
    {
        var samplePath = Path.Combine(AppContext.BaseDirectory, "Config", "sample-snapshot.json");

        try
        {
            Check("Sample snapshot ships with the app", File.Exists(samplePath), samplePath);
            if (!File.Exists(samplePath)) return;

            var json = File.ReadAllText(samplePath);
            var snapshot = SnapshotService.Parse(json, "selftest");
            Check("Snapshot JSON parsed", snapshot is not null && snapshot.Eggs.Count == 6,
                $"{snapshot?.Eggs.Count ?? 0} eggs");

            var eggs = new EggFeatures(new Logger());
            eggs.ApplySnapshot(snapshot!, EggOrigin.External);

            Check("Snapshot applied as read-only source",
                eggs.Eggs.Count == 6 && eggs.SourceMode == EggOrigin.External
                && eggs.PlayerName == "SnapshotTester" && Math.Abs(eggs.Player.Coins - 480) < 0.01,
                $"{eggs.Eggs.Count} eggs, mode={eggs.SourceModeText}, player={eggs.PlayerName}");

            var array = SnapshotService.Parse("[{\"name\":\"Test Egg\",\"rarity\":\"Epic\",\"value\":500,\"x\":1,\"y\":2,\"z\":3}]", "array");
            Check("Snapshot array form parsed", array is not null && array.Eggs.Count == 1 && array.Eggs[0].ParseRarity() == EggRarity.Epic,
                $"{array?.Eggs.Count ?? 0} eggs");
        }
        catch (Exception ex)
        {
            Fail("Snapshot ingestion", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Bridge
    // ------------------------------------------------------------------

    private static async Task RunBridgeTests()
    {
        var port = 18987;
        var bridge = new BridgeServer(new Logger());
        var received = new ManualResetEventSlim(false);
        BridgeSnapshotResult? last = null;

        bridge.SnapshotReceived += (_, result) =>
        {
            last = result;
            received.Set();
        };

        try
        {
            bridge.Start(port);
            if (!bridge.IsRunning)
            {
                Skip("Bridge HTTP round-trip", $"port {port} unavailable: {bridge.LastError}");
                return;
            }

            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
            var baseUrl = $"http://127.0.0.1:{port}";

            var ping = await http.GetAsync($"{baseUrl}/ping").ConfigureAwait(false);
            Check("Bridge GET /ping", ping.IsSuccessStatusCode, $"status={(int)ping.StatusCode}");

            var stateResponse = await http.GetAsync($"{baseUrl}/state").ConfigureAwait(false);
            var stateBody = await stateResponse.Content.ReadAsStringAsync().ConfigureAwait(false);
            Check("Bridge GET /state", stateResponse.IsSuccessStatusCode && stateBody.Contains("\"ok\"", StringComparison.OrdinalIgnoreCase),
                $"status={(int)stateResponse.StatusCode}, {stateBody.Length} bytes");

            bridge.PublishState(new BridgeStateDoc { Speed = true, WalkSpeed = 42, Player = "SelfTest" });
            var published = await http.GetStringAsync($"{baseUrl}/state").ConfigureAwait(false);
            Check("Bridge state publishing", published.Contains("\"walkSpeed\": 42") || published.Contains("\"walkSpeed\":42"),
                published.Length > 200 ? published[..200] : published);

            var command = bridge.Enqueue("teleport", 1, 2, 3);
            Check("Bridge command queue", bridge.State.Commands.Any(c => c.Id == command.Id && c.Action == "teleport"),
                $"{bridge.State.Commands.Count} pending command(s)");

            var sample = File.Exists(Path.Combine(AppContext.BaseDirectory, "Config", "sample-snapshot.json"))
                ? await File.ReadAllTextAsync(Path.Combine(AppContext.BaseDirectory, "Config", "sample-snapshot.json")).ConfigureAwait(false)
                : "{\"player\":\"x\",\"eggs\":[]}";

            using var content = new StringContent(sample, Encoding.UTF8, "application/json");
            var post = await http.PostAsync($"{baseUrl}/snapshot", content).ConfigureAwait(false);
            var got = received.Wait(TimeSpan.FromSeconds(5));

            Check("Bridge POST /snapshot", post.IsSuccessStatusCode && got && last is not null && last.Snapshot.Eggs.Count == 6,
                $"status={(int)post.StatusCode}, eggs={last?.Snapshot.Eggs.Count ?? 0}");

            var eventContent = new StringContent("{\"name\":\"log\",\"message\":\"self-test\"}", Encoding.UTF8, "application/json");
            var eventResponse = await http.PostAsync($"{baseUrl}/event", eventContent).ConfigureAwait(false);
            Check("Bridge POST /event", eventResponse.IsSuccessStatusCode, $"status={(int)eventResponse.StatusCode}");

            var notFound = await http.GetAsync($"{baseUrl}/nope").ConfigureAwait(false);
            Check("Bridge unknown route returns 404", (int)notFound.StatusCode == 404, $"status={(int)notFound.StatusCode}");
        }
        catch (Exception ex)
        {
            Fail("Bridge HTTP round-trip", ex.Message);
        }
        finally
        {
            bridge.Dispose();
        }
    }

    // ------------------------------------------------------------------
    // Automation (demo simulation)
    // ------------------------------------------------------------------

    private static void RunAutomationTests()
    {
        var log = new Logger();
        var settings = SettingsManager.CreateDefaults();
        settings.Features[FeatureIds.AutoCollect] = true;
        settings.Features[FeatureIds.AutoFarm] = true;
        settings.Features[FeatureIds.AutoSell] = true;
        settings.Automation.IntervalMs = 200;

        var bridge = new BridgeServer(log);
        var eggs = new EggFeatures(log);
        var player = new PlayerFeatures(log, bridge, eggs);
        var automation = new AutomationFeatures(log, eggs, player);

        try
        {
            player.RefreshMode(settings);
            var started = automation.Start(settings);
            Check("Automation starts in demo mode", started && automation.IsRunning && !player.IsOfficial,
                $"running={automation.IsRunning}, mode={player.ModeText}");

            for (var i = 0; i < 600 && eggs.CollectedCount == 0; i++)
            {
                eggs.Update(TimeSpan.FromMilliseconds(100), settings, 1280, 720);
                automation.Update(TimeSpan.FromMilliseconds(100), settings);
            }

            Check("Automation collects on the demo island",
                eggs.CollectedCount > 0 && automation.Actions > 0 && automation.Cycles > 0,
                $"collected={eggs.CollectedCount}, actions={automation.Actions}, cycles={automation.Cycles}");

            automation.Stop("self-test");
            Check("Automation stops", !automation.IsRunning, automation.LastAction);
        }
        catch (Exception ex)
        {
            Fail("Automation demo loop", ex.Message);
        }
        finally
        {
            bridge.Dispose();
        }

        try
        {
            var emptySettings = SettingsManager.CreateDefaults();
            foreach (var id in FeatureIds.Automation) emptySettings.Features[id] = false;

            var secondBridge = new BridgeServer(log);
            var secondEggs = new EggFeatures(log);
            var secondPlayer = new PlayerFeatures(log, secondBridge, secondEggs);
            var secondAutomation = new AutomationFeatures(log, secondEggs, secondPlayer);

            var started = secondAutomation.Start(emptySettings);
            Check("Automation refuses to start without features", !started && !secondAutomation.IsRunning,
                $"running={secondAutomation.IsRunning}");
            secondBridge.Dispose();
        }
        catch (Exception ex)
        {
            Fail("Automation refuses to start without features", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Assets / platform
    // ------------------------------------------------------------------

    private static void RunAssetTests()
    {
        try
        {
            var dir = AppPaths.BundledLuauDir;
            var required = new[]
            {
                "EggWorld.lua",
                "SAEUltimateBridge.server.lua",
                "SAEUltimateClient.client.lua",
                "README.md"
            };

            var missing = required.Where(f => !File.Exists(Path.Combine(dir, f))).ToList();
            var bridgeText = File.Exists(Path.Combine(dir, "SAEUltimateBridge.server.lua"))
                ? File.ReadAllText(Path.Combine(dir, "SAEUltimateBridge.server.lua"))
                : string.Empty;

            Check("Bundled Luau modules present",
                missing.Count == 0 && bridgeText.Contains("/snapshot") && bridgeText.Contains("/state"),
                missing.Count == 0 ? $"{required.Length} files verified in {dir}" : "missing: " + string.Join(", ", missing));
        }
        catch (Exception ex)
        {
            Fail("Bundled Luau modules present", ex.Message);
        }

        try
        {
            using var detector = new RobloxDetector(new Logger());
            var window = detector.ScanNow();
            Pass("Roblox window enumeration (no crash)",
                window is null ? "no Roblox client in this session (expected for CI)" : $"found {window.ResolutionText}");
        }
        catch (Exception ex)
        {
            Fail("Roblox window enumeration (no crash)", ex.Message);
        }

        try
        {
            using var hotkeys = new HotkeyManager(new Logger());
            var started = hotkeys.Start();
            if (started)
            {
                Pass("Global keyboard hook (passive)");
                hotkeys.Stop();
            }
            else
            {
                Skip("Global keyboard hook (passive)", "hook unavailable in this session");
            }
        }
        catch (Exception ex)
        {
            Fail("Global keyboard hook (passive)", ex.Message);
        }

        try
        {
            var windowsOk = OperatingSystem.IsWindows();
            Check("Windows compatibility check", windowsOk, Environment.OSVersion.ToString());

            var data = AppPaths.DataRoot;
            AppPaths.EnsureCreated();
            Check("Data folders created", Directory.Exists(data) && Directory.Exists(AppPaths.SnapshotsDir)
                                                       && Directory.Exists(AppPaths.LogDir),
                data);

            var log = new Logger();
            log.Info("self-test log line");
            Check("Logger writes to disk", Directory.GetFiles(log.DirectoryPath, "hub-*.log").Length > 0, log.DirectoryPath);
        }
        catch (Exception ex)
        {
            Fail("Windows compatibility check", ex.Message);
        }

        try
        {
            var players = RobloxDetector.KnownPlayerProcesses;
            var studios = RobloxDetector.KnownStudioProcesses;
            Check("Roblox process name coverage",
                players.Contains("RobloxPlayerBeta") && players.Contains("Windows10Universal")
                && studios.Contains("RobloxStudioBeta"),
                $"players={string.Join("/", players)} · studio={string.Join("/", studios)}");
        }
        catch (Exception ex)
        {
            Fail("Roblox process name coverage", ex.Message);
        }

        try
        {
            // The preview overlay is what makes CONNECT usable without a game client.
            // Attaching a real WPF window needs a desktop session, so verify the exact
            // monitor geometry it uses instead of pretending the window was rendered
            // (the --smoke run renders it and fails on an empty capture).
            var area = Win32.GetMonitorWorkArea(IntPtr.Zero);
            Check("Preview overlay geometry available",
                area.Width >= 320 && area.Height >= 240,
                $"work area {area.Width}x{area.Height}");
        }
        catch (Exception ex)
        {
            Fail("Preview overlay geometry available", ex.Message);
        }

        try
        {
            var settings = SettingsManager.CreateDefaults();
            var menuKeyOk = Enum.TryParse<Key>(settings.Overlay.MenuKey, true, out var menuKey) && menuKey == Key.RightShift;
            var allowed = HotkeyManager.AllowedKeys.Contains(settings.Overlay.MenuKey);
            Check("Menu key is a supported global hotkey", menuKeyOk && allowed, settings.Overlay.MenuKey);
        }
        catch (Exception ex)
        {
            Fail("Menu key is a supported global hotkey", ex.Message);
        }

        Skip("Overlay attached to a real Roblox window",
            "needs the Roblox Player client running (the desktop PREVIEW overlay and its menu are rendered by --smoke)");
    }

    private static void RunPlatformTests()
    {
        try
        {
            var settings = SettingsManager.CreateDefaults();
            var colors = ThemeCatalog.Themes.Select(t => t.Name).ToList();
            var accent = ColorUtil.Parse(settings.Visual.EspColor, System.Windows.Media.Colors.White);

            Check("Theme catalog + colour parsing",
                colors.Count == 4 && accent.B > 200,
                $"themes={string.Join("/", colors)}, esp={ColorUtil.ToHex(accent)}");

            var key = Enum.TryParse<Key>(settings.Overlay.MenuKey, true, out var parsedKey);
            Check("Menu keybinding resolves to Right Shift", key && parsedKey == Key.RightShift, settings.Overlay.MenuKey);
        }
        catch (Exception ex)
        {
            Fail("Theme catalog + colour parsing", ex.Message);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void Check(string name, bool condition, string detail = "")
    {
        if (condition) Pass(name, detail);
        else Fail(name, detail);
    }

    private static void Pass(string name, string detail = "")
    {
        _passed++;
        Lines.Add($"[PASS] {name}{(string.IsNullOrWhiteSpace(detail) ? string.Empty : " — " + detail)}");
    }

    private static void Fail(string name, string detail = "")
    {
        _failed++;
        Lines.Add($"[FAIL] {name}{(string.IsNullOrWhiteSpace(detail) ? string.Empty : " — " + detail)}");
    }

    private static void Skip(string name, string reason)
    {
        _skipped++;
        Lines.Add($"[SKIP] {name} — {reason}");
    }

}
