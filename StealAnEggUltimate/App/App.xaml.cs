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

        AppPaths.EnsureCreated();
        _log = new Logger();
        _log.Info($"Application start · v{AppInfo.Version} · {Environment.OSVersion}");

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
        }
        catch (Exception ex)
        {
            _log.Error("Startup failed.", ex);
            MessageBox.Show(
                $"The hub could not start.\n\n{ex.Message}\n\nA log was written to:\n{AppPaths.LogDir}",
                AppInfo.ProductName, MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
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
