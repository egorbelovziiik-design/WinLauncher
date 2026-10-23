using System.IO;
using System.Threading;
using System.Windows;
using System.Windows.Threading;

namespace StealAnEggUltimate;

public partial class App : Application
{
    private const string MutexName = @"Local\StealAnEggUltimateHub_SingleInstance";

    private Mutex? _instanceMutex;
    private bool _ownsMutex;
    private Logger? _log;
    private MainViewModel? _viewModel;
    private MainWindow? _window;
    private bool _smokeMode;
    private bool _selfTestMode;
    private int _uiExceptionCount;
    private string _smokeDetail = string.Empty;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // --- Single instance guard -------------------------------------------------
        _instanceMutex = new Mutex(initiallyOwned: true, MutexName, out var isFirstInstance);
        _ownsMutex = isFirstInstance;
        if (!isFirstInstance)
        {
            MessageBox.Show(
                $"{AppInfo.ProductName} is already running.\n\nClose the other window (or its tray process) and try again.",
                AppInfo.ProductName, MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown(0);
            return;
        }

        // --- Platform check --------------------------------------------------------
        if (!OperatingSystem.IsWindows())
        {
            MessageBox.Show(
                "This build targets Windows 10/11 x64.\n\nThe overlay, global hotkey hook and Roblox window detection need Windows APIs.",
                AppInfo.ProductName, MessageBoxButton.OK, MessageBoxImage.Warning);
            Shutdown(1);
            return;
        }

        _selfTestMode = e.Args.Any(a => a.Equals("--selftest", StringComparison.OrdinalIgnoreCase));
        _smokeMode = e.Args.Any(a => a.Equals("--smoke", StringComparison.OrdinalIgnoreCase));

        AppPaths.EnsureCreated();
        _log = new Logger();
        _log.Info($"Application start · v{AppInfo.Version} · {Environment.OSVersion} · args: {string.Join(' ', e.Args)}");

        if (_selfTestMode)
        {
            // Run the suite on a dedicated STA thread: the WPF dispatcher loop is not
            // pumping yet, so awaiting I/O on the UI thread would deadlock.
            var exitCode = 1;
            var thread = new Thread(() => exitCode = SelfTest.Run(_log))
            {
                IsBackground = true,
                Name = "SelfTest"
            };
            thread.SetApartmentState(ApartmentState.STA);
            thread.Start();

            if (!thread.Join(TimeSpan.FromMinutes(6)))
            {
                _log.Error("Self-test thread timed out after 6 minutes.");
                exitCode = 3;
            }

            _log.Info($"Self-test exit code: {exitCode}");
            Shutdown(exitCode);
            return;
        }

        // --- Global error handling -------------------------------------------------
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            _log?.Error("Fatal unhandled exception.", args.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, args) =>
        {
            _log?.Error("Unobserved task exception.", args.Exception);
            args.SetObserved();
        };

        // --- Boot ------------------------------------------------------------------
        try
        {
            _viewModel = new MainViewModel(_log);
            _window = new MainWindow(_viewModel);
            MainWindow = _window;

            _viewModel.RequestExit += (_, _) => _window?.ForceClose();
            _viewModel.ActivateHubRequested += (_, _) => RestoreHub();

            _window.Show();
            _viewModel.Start();

            _log.Info("Main window shown.");

            if (_smokeMode)
            {
                RunSmokeSequence();
            }
        }
        catch (Exception ex)
        {
            _log.Error("Startup failed.", ex);

            if (_smokeMode || _selfTestMode)
            {
                _log.Error("Startup failed in headless mode; exiting with code 2.");
                Shutdown(2);
                return;
            }

            MessageBox.Show(
                $"The hub could not start.\n\n{ex.Message}\n\nA log was written to:\n{AppPaths.LogDir}",
                AppInfo.ProductName, MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    /// <summary>
    /// End-to-end smoke run. It drives the real feature path — CONNECT (which attaches
    /// the preview overlay when no Roblox client exists), the demo ESP, opening the
    /// in-game menu with the configured key — then renders both windows to PNG. A
    /// missing overlay, an empty ESP layer, a menu that refuses to open or a black
    /// capture all fail the run instead of silently "passing".
    /// </summary>
    private void RunSmokeSequence()
    {
        if (_viewModel is null || _log is null) return;

        var stats = new List<string>();
        var step = 0;

        _log.Info("Smoke mode: driving the real CONNECT path.");
        try
        {
            _viewModel.ConnectCommand.Execute(null);
        }
        catch (Exception ex)
        {
            Fault($"CONNECT threw {ex.GetType().Name}: {ex.Message}");
        }

        var timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(900) };
        timer.Tick += (_, _) =>
        {
            try
            {
                if (step++ == 0)
                {
                    stats.Add($"connected={_viewModel.IsConnected}");
                    stats.Add($"preview={_viewModel.IsPreviewOverlay}");
                    stats.Add($"demoEggs={_viewModel.Eggs.Eggs.Count}");
                    stats.Add($"espEggs={_viewModel.EspEggs.Count}");

                    if (!_viewModel.IsConnected) Fault("CONNECT did not attach an overlay (preview or Roblox)");
                    if (_viewModel.IsConnected && !_viewModel.IsPreviewOverlay)
                        _log.Info("Smoke mode: a real Roblox client is attached, using it for the capture.");

                    _viewModel.ShowMenu();
                    return;
                }

                timer.Stop();
                CaptureSmokeArtifacts(stats);
                _smokeDetail = string.Join(" · ", stats);
                _log.Info($"Smoke detail: {_smokeDetail}");
                _log.Info("Smoke mode: clean shutdown requested.");
                _viewModel.Shutdown();
            }
            catch (Exception ex)
            {
                timer.Stop();
                Fault($"smoke sequence failed: {ex.GetType().Name}: {ex.Message}");
                _smokeDetail = string.Join(" · ", stats);
                _viewModel?.Shutdown();
            }
        };
        timer.Start();
    }

    private void CaptureSmokeArtifacts(List<string> stats)
    {
        if (_viewModel is null) return;

        stats.Add($"menuOpen={_viewModel.IsMenuOpen}");
        stats.Add($"overlay={_viewModel.DiagnosticOverlayWindow is not null}");

        var dir = Path.Combine(AppPaths.DataRoot, "ui");
        Directory.CreateDirectory(dir);

        if (_window is not null)
        {
            var hub = UiSnapshot.Capture(_window, Path.Combine(dir, "hub-window.png"));
            stats.Add($"hubShot={(hub.Saved ? $"{hub.Width}x{hub.Height}" : "FAILED")}");
            stats.Add($"hubLuma={hub.MeanLuma:0.0}");
            stats.Add($"hubOpaque={hub.OpaqueRatio:P0}");
            if (!hub.Saved) Fault($"the hub window could not be rendered ({hub.Error})");
            else if (hub.MeanLuma < 2.0) Fault("the hub window rendered as a black/empty surface");
        }

        var overlay = _viewModel.DiagnosticOverlayWindow;
        if (overlay is null)
        {
            Fault("no overlay window exists after CONNECT");
        }
        else
        {
            var shot = UiSnapshot.Capture(overlay, Path.Combine(dir, "overlay-menu.png"));
            stats.Add($"overlayShot={(shot.Saved ? $"{shot.Width}x{shot.Height}" : "FAILED")}");
            stats.Add($"overlayLuma={shot.MeanLuma:0.0}");
            stats.Add($"overlayOpaque={shot.OpaqueRatio:P1}");
            if (!shot.Saved) Fault($"the overlay could not be rendered ({shot.Error})");
            else if (shot.MeanLuma < 2.0) Fault("the overlay rendered as an empty surface (menu did not draw)");
        }

        if (!_viewModel.IsMenuOpen) Fault("the in-game menu did not open");
        if (_viewModel.EspEggs.Count == 0) Fault("the demo ESP projected no eggs onto the overlay");
    }

    private void Fault(string message)
    {
        _uiExceptionCount++;
        _log?.Error($"Smoke check failed: {message}");
    }

    private void RestoreHub()
    {
        if (_window is null) return;
        try
        {
            if (!_window.IsVisible) _window.Show();
            if (_window.WindowState == WindowState.Minimized) _window.WindowState = WindowState.Normal;
            _window.Activate();
        }
        catch (Exception ex)
        {
            _log?.Error("Could not restore the hub window.", ex);
        }
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        _log?.Error("Unhandled UI exception.", e.Exception);

        if (_smokeMode || _selfTestMode)
        {
            _uiExceptionCount++;
            e.Handled = true;
            return;
        }

        // Recover instead of crashing: report once and keep the hub usable.
        MessageBox.Show(
            $"An unexpected error occurred and the hub recovered.\n\n{e.Exception.GetType().Name}: {e.Exception.Message}\n\nDetails: {Path.Combine(AppPaths.LogDir, "hub-*.log")}",
            AppInfo.ProductName, MessageBoxButton.OK, MessageBoxImage.Warning);

        e.Handled = true;
    }

    protected override void OnExit(ExitEventArgs e)
    {
        try
        {
            _viewModel?.Dispose();
        }
        catch
        {
            // never fail while exiting
        }

        if (_smokeMode)
        {
            if (_uiExceptionCount > 0)
            {
                e.ApplicationExitCode = 4;
                Environment.ExitCode = 4;
            }

            try
            {
                AppPaths.EnsureCreated();
                File.WriteAllText(
                    Path.Combine(AppPaths.DataRoot, "smoke-result.txt"),
                    $"smoke {(e.ApplicationExitCode == 0 && _uiExceptionCount == 0 ? "ok" : "FAILED")} · {DateTime.UtcNow:O} · exitCode={e.ApplicationExitCode} · uiExceptions={_uiExceptionCount} · overlay={_viewModel?.IsConnected} · version={AppInfo.Version}" +
                    (string.IsNullOrEmpty(_smokeDetail) ? string.Empty : $" · {_smokeDetail}"));
            }
            catch
            {
                // diagnostic only
            }
        }

        if (_ownsMutex)
        {
            try
            {
                _instanceMutex?.ReleaseMutex();
            }
            catch
            {
                // not owned in this thread
            }
        }

        _instanceMutex?.Dispose();
        RunCleanup();

        base.OnExit(e);
    }

    private static void RunCleanup()
    {
        try
        {
            var temp = Path.Combine(Path.GetTempPath(), "StealAnEggUltimate");
            if (Directory.Exists(temp))
                Directory.Delete(temp, recursive: true);
        }
        catch
        {
            // best effort
        }
    }
}
