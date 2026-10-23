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
    private OverlayWindow? _probeOverlay;

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
                RunOverlayProbe();
                _log.Info("Smoke mode: closing automatically in 6 seconds.");
                var timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4) };
                timer.Tick += (_, _) =>
                {
                    timer.Stop();
                    if (_uiExceptionCount > 0)
                    {
                        _log.Error($"Smoke mode: {_uiExceptionCount} UI exception(s) were raised while rendering the hub and overlay.");
                    }

                    CloseOverlayProbe();
                    _log.Info("Smoke mode: clean shutdown requested.");
                    _viewModel.Shutdown();
                };
                timer.Start();
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
    /// Renders the overlay window once during a smoke run so that XAML / binding
    /// problems in OverlayWindow surface in CI instead of on a user's machine.
    /// </summary>
    private void RunOverlayProbe()
    {
        try
        {
            if (_viewModel is null) return;
            _probeOverlay = new OverlayWindow(new OverlayViewModel(_viewModel), _log)
            {
                WindowStartupLocation = WindowStartupLocation.Manual,
                Left = 0,
                Top = 0
            };
            _probeOverlay.Show();
            _probeOverlay.UpdateLayout();
            _log.Info("Smoke mode: overlay window rendered without XAML errors.");
        }
        catch (Exception ex)
        {
            _probeOverlay = null;
            _uiExceptionCount++;
            _log.Error("Smoke mode: overlay window failed to render.", ex);
        }
    }

    private void CloseOverlayProbe()
    {
        if (_probeOverlay is null) return;
        try
        {
            _probeOverlay.CloseFromManager();
        }
        catch (Exception ex)
        {
            _log?.Error("Smoke mode: overlay probe cleanup failed.", ex);
        }
        finally
        {
            _probeOverlay = null;
        }
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
                    $"smoke {(e.ApplicationExitCode == 0 && _uiExceptionCount == 0 ? "ok" : "FAILED")} · {DateTime.UtcNow:O} · exitCode={e.ApplicationExitCode} · uiExceptions={_uiExceptionCount} · overlay={_viewModel?.IsConnected} · version={AppInfo.Version}");
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
