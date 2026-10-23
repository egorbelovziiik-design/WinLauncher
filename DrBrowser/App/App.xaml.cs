using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace DrBrowser;

public partial class App : Application
{
    private readonly List<MainWindow> _windows = new();
    private Logger? _log;
    private bool _verificationRun;

    /// <summary>True for --selftest / --smoke: no modal dialogs, they would block the headless run.</summary>
    public static bool IsTestMode { get; private set; }

    public static new App Current => (App)Application.Current;

    public Logger Log => _log ??= new Logger();

    public IReadOnlyList<MainWindow> OpenWindows => _windows;

    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        AppPaths.EnsureCreated();

        IsTestMode = e.Args.Any(a => a.Equals("--selftest", StringComparison.OrdinalIgnoreCase)
                                     || a.Equals("--smoke", StringComparison.OrdinalIgnoreCase));

        _log = new Logger();
        _log.Info($"=== DrBrowser {AppSettings.Version} starting · {Environment.OSVersion} · {string.Join(' ', e.Args)} ===");

        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            _log?.Error("Fatal unhandled exception", args.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, args) =>
        {
            _log?.Error("Unobserved task exception", args.Exception);
            args.SetObserved();
        };

        if (e.Args.Any(a => a.Equals("--selftest", StringComparison.OrdinalIgnoreCase)))
        {
            _verificationRun = true;
            Dispatcher.InvokeAsync(async () =>
            {
                var code = await SelfTest.RunAsync(Log);
                Shutdown(code);
            }, DispatcherPriority.ApplicationIdle);
            return;
        }

        if (e.Args.Any(a => a.Equals("--smoke", StringComparison.OrdinalIgnoreCase)))
        {
            _verificationRun = true;
            Dispatcher.InvokeAsync(async () =>
            {
                var code = await SelfTest.RunSmokeAsync(Log);
                Shutdown(code);
            }, DispatcherPriority.ApplicationIdle);
            return;
        }

        var startPrivate = e.Args.Any(a => a.Equals("--private", StringComparison.OrdinalIgnoreCase));
        OpenWindow(startPrivate);
    }

    public MainWindow OpenWindow(bool privateMode, string? initialUrl = null)
    {
        var vm = new MainViewModel(Log, privateMode);
        var window = new MainWindow(vm);
        _windows.Add(window);

        window.Closed += (_, _) =>
        {
            _windows.Remove(window);
            // Verification closes its window before computing the report's final exit
            // code. Let the harness call Shutdown(code), otherwise WPF locks in code 0.
            if (_windows.Count == 0 && !_verificationRun) Shutdown();
        };

        if (privateMode) window.ShowInTaskbar = true;
        window.Show();

        _ = InitializeAsync(vm, window, initialUrl);
        return window;
    }

    private async Task InitializeAsync(MainViewModel vm, MainWindow window, string? initialUrl)
    {
        try
        {
            await vm.InitializeAsync(window);
            if (!string.IsNullOrWhiteSpace(initialUrl)) vm.NavigateActiveTab(initialUrl);
        }
        catch (Exception ex)
        {
            Log.Error("window initialisation failed", ex);
            if (IsTestMode)
            {
                SelfTest.RecordStartupFailure(ex);
                return;
            }

            window.ShowStartupError(ex.Message);
        }
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        Log.Error("Unhandled UI exception", e.Exception);
        e.Handled = true;

        try
        {
            MessageBox.Show(
                "DrBrowser перехватил внутреннюю ошибку и продолжил работу.\n\n" + e.Exception.Message +
                "\n\nЖурнал: " + AppPaths.LogDir,
                "DrBrowser", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        catch
        {
            // ignore
        }
    }

    protected override void OnExit(ExitEventArgs e)
    {
        _log?.Info($"exit code {e.ApplicationExitCode}");
        base.OnExit(e);
    }
}
