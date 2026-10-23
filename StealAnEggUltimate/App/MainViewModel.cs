using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;

namespace StealAnEggUltimate;

public sealed class MainViewModel : ObservableObject, IDisposable
{
    private readonly Logger _log;
    private readonly SettingsManager _settingsManager;
    private readonly RobloxDetector _detector;
    private readonly BridgeServer _bridge;
    private readonly SnapshotService _snapshots;
    private readonly HotkeyManager _hotkeys;
    private readonly CommandManager _commands;
    private readonly DispatcherTimer _timer;
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private readonly FpsCounter _fps = new();

    private AppSettings _settings;
    private OverlayManager? _overlay;
    private DateTime _lastTick;
    private DateTime _saveRequestedAt = DateTime.MinValue;
    private bool _savePending;
    private bool _bridgeDirty = true;
    private DateTime _lastBridgePush = DateTime.MinValue;
    private bool _pendingConnect;
    private bool _isConnecting;
    private IntPtr _overlayTargetHandle = IntPtr.Zero;
    private bool _isConnected;
    private bool _shutdown;
    private string _selectedTab = "Egg";
    private string _overlayTab = "Egg";
    private string _commandInput = string.Empty;
    private string _lastCommand = "Type help() to list the supported commands.";
    private string _noticeBanner = string.Empty;
    private string _assetsStatus = string.Empty;
    private string _windowsStatus = string.Empty;
    private string _updateStatus = "Not checked yet.";
    private string _connectionState = "DISCONNECTED";
    private DateTime _lastEspSync = DateTime.MinValue;
    private DateTime _lastExpiryCheck = DateTime.MinValue;

    public MainViewModel(Logger? logger = null)
    {
        _log = logger ?? new Logger();
        _log.Info("==== STEAL AN EGG ULTIMATE HUB starting ====");
        _log.Info($"Runtime: {Environment.OSVersion} · {(Environment.Is64BitOperatingSystem ? "x64" : "x86")} · .NET {Environment.Version}");
        _log.Info($"Base directory: {AppContext.BaseDirectory}");

        AppPaths.EnsureCreated();
        _settingsManager = new SettingsManager(_log);
        _settingsManager.StatusMessage += (_, message) => AddNotice(message, NoticeKind.Error);
        _settings = _settingsManager.Load();

        Visuals = new VisualFeatures();
        Visuals.Apply(_settings);

        _bridge = new BridgeServer(_log);
        _detector = new RobloxDetector(_log);
        _snapshots = new SnapshotService(_log);
        Eggs = new EggFeatures(_log);
        Player = new PlayerFeatures(_log, _bridge, Eggs);
        Automation = new AutomationFeatures(_log, Eggs, Player);
        _hotkeys = new HotkeyManager(_log);
        _commands = new CommandManager(_log);

        EggRows = CreateRows(FeatureRegistry.Egg);
        PlayerRows = CreateRows(FeatureRegistry.Player);
        AutomationRows = CreateRows(FeatureRegistry.Automation);

        _timer = new DispatcherTimer(DispatcherPriority.Render)
        {
            Interval = TimeSpan.FromMilliseconds(33)
        };
        _timer.Tick += OnTick;

        HookSettings();
        WireEvents();
        BuildCommandContext();
        VerifyApplicationFiles();
        WindowsStatus = CheckWindowsCompatibility();

        AddConsole($"Welcome to {AppInfo.ProductName} v{AppInfo.Version}.", NoticeKind.Info);
        AddConsole(AppInfo.BridgeNote, NoticeKind.Info);
    }

    // ------------------------------------------------------------------
    // Services / features
    // ------------------------------------------------------------------

    public AppSettings Settings => _settings;
    public EggFeatures Eggs { get; }
    public PlayerFeatures Player { get; }
    public AutomationFeatures Automation { get; }
    public VisualFeatures Visuals { get; }

    public ObservableCollection<FeatureRowViewModel> EggRows { get; }
    public ObservableCollection<FeatureRowViewModel> PlayerRows { get; }
    public ObservableCollection<FeatureRowViewModel> AutomationRows { get; }

    public ObservableCollection<Egg> EspEggs { get; } = new();
    public ObservableCollection<HubNotice> Notifications { get; } = new();
    public ObservableCollection<ConsoleLine> ConsoleLines { get; } = new();

    public event EventHandler? RequestExit;
    public event EventHandler? MenuStateChanged;
    public event EventHandler? ActivateHubRequested;

    // ------------------------------------------------------------------
    // Presentation state
    // ------------------------------------------------------------------

    public string ProductName => AppInfo.ProductName;
    public string VersionText => AppInfo.VersionText;
    public string ShortVersion => $"v{AppInfo.Version}";
    public string AppDescription => AppInfo.Description;
    public string BridgeNote => AppInfo.BridgeNote;
    public string DataFolderPath => AppPaths.DataRoot;
    public string ConfigPathText => AppPaths.ConfigFile;
    public string LogFolderPath => _log.DirectoryPath;
    public string BridgeEndpoint => _bridge.Endpoint;
    public string SnapshotFolderPath => AppPaths.SnapshotsDir;
    public string LuauFolderPath => AppPaths.BundledLuauDir;

    public string WindowsStatus
    {
        get => _windowsStatus;
        private set => Set(ref _windowsStatus, value);
    }

    public string AssetsStatus
    {
        get => _assetsStatus;
        private set => Set(ref _assetsStatus, value);
    }

    public string UpdateStatus
    {
        get => _updateStatus;
        private set => Set(ref _updateStatus, value);
    }

    public string ConnectionState
    {
        get => _connectionState;
        private set => Set(ref _connectionState, value);
    }

    public bool IsConnecting
    {
        get => _isConnecting;
        private set
        {
            if (Set(ref _isConnecting, value)) Raise(nameof(ConnectButtonText));
        }
    }

    public string ConnectButtonText => IsConnecting ? "CONNECTING…" : "CONNECT";

    public bool IsConnected
    {
        get => _isConnected;
        private set
        {
            if (!Set(ref _isConnected, value)) return;
            Raise(nameof(OverlayStatusText), nameof(OverlayStateLabel), nameof(IsMenuOpen), nameof(OverlaySummary),
                nameof(OverlayHint), nameof(StatusLine), nameof(IsPreviewOverlay));
        }
    }

    /// <summary>True when the overlay is attached to the desktop because no Roblox client exists.</summary>
    public bool IsPreviewOverlay => IsConnected && (_overlay?.IsPreview ?? false);

    public string OverlayStatusText => IsConnected
        ? IsPreviewOverlay ? "Overlay Connected · PREVIEW" : "Overlay Connected"
        : "Overlay Disconnected";

    public string OverlayStateLabel => IsConnected
        ? IsPreviewOverlay ? "OVERLAY CONNECTED · PREVIEW" : "OVERLAY CONNECTED"
        : "OVERLAY DISCONNECTED";

    public bool IsMenuOpen => _overlay?.IsMenuOpen ?? false;

    public string StatusLine => IsConnected
        ? IsPreviewOverlay
            ? "PREVIEW OVERLAY · RIGHT SHIFT OPENS THE MENU · ROBLOX NOT RUNNING"
            : "OVERLAY CONNECTED · RIGHT SHIFT OPENS THE MENU"
        : _pendingConnect
            ? "WAITING FOR ROBLOX CLIENT"
            : "READY · CONNECT TO ATTACH THE OVERLAY";

    public string RobloxStatusText => _detector.StatusText;
    public bool RobloxDetected => _detector.IsDetected;
    public bool StudioDetected => _detector.IsStudioDetected;

    public string RobloxSummary
    {
        get
        {
            var window = _detector.CurrentWindow;
            if (window is null) return "No Roblox client window found. Start Roblox, then press CONNECT.";
            return $"{window.ResolutionText}  ·  pid {window.ProcessId}  ·  hwnd {window.HandleText}  ·  {window.Title}";
        }
    }

    public string OverlaySummary => _overlay?.Summary ?? "Overlay is not attached.";

    /// <summary>Live overlay window for the smoke run's renders (null when detached).</summary>
    public OverlayWindow? DiagnosticOverlayWindow => _overlay?.Window;

    public string BridgeStatusText => $"{Player.BridgeStatus}  ·  snapshots {_bridge.SnapshotCount}";
    public string BridgeHint => $"Point the bundled Luau bridge at {_bridge.Endpoint} (loopback only).";

    public string SelectedTab
    {
        get => _selectedTab;
        set
        {
            if (!Set(ref _selectedTab, value)) return;
            Raise(nameof(IsEggTab), nameof(IsPlayerTab), nameof(IsAutomationTab), nameof(IsVisualsTab),
                nameof(IsSettingsTab), nameof(IsConsoleTab), nameof(PageTitle), nameof(PageSubtitle));
        }
    }

    public bool IsEggTab
    {
        get => SelectedTab == "Egg";
        set => SetTab(value, "Egg");
    }

    public bool IsPlayerTab
    {
        get => SelectedTab == "Player";
        set => SetTab(value, "Player");
    }

    public bool IsAutomationTab
    {
        get => SelectedTab == "Automation";
        set => SetTab(value, "Automation");
    }

    public bool IsVisualsTab
    {
        get => SelectedTab == "Visuals";
        set => SetTab(value, "Visuals");
    }

    public bool IsSettingsTab
    {
        get => SelectedTab == "Settings";
        set => SetTab(value, "Settings");
    }

    public bool IsConsoleTab
    {
        get => SelectedTab == "Console";
        set => SetTab(value, "Console");
    }

    private void SetTab(bool selected, string tab)
    {
        if (!selected) return;
        SelectedTab = tab;
    }

    public string PageTitle => SelectedTab switch
    {
        "Egg" => "EGG INTELLIGENCE",
        "Player" => "PLAYER CONTROL",
        "Automation" => "AUTOMATION",
        "Visuals" => "VISUALS",
        "Settings" => "SETTINGS",
        _ => "COMMAND EDITOR"
    };

    public string PageSubtitle => SelectedTab switch
    {
        "Egg" => "ESP, scanner, rarity filters and the discovered egg index.",
        "Player" => "Movement modules for the official test place, with live keybinds.",
        "Automation" => "Loop automation for the official test place and the demo island.",
        "Visuals" => "Colours, theme, opacity, scaling, crosshair and FPS.",
        "Settings" => "Config files, keybinds, overlay lifecycle and diagnostics.",
        _ => "Safe command interpreter — no arbitrary code execution."
    };

    public string OverlayTab
    {
        get => _overlayTab;
        set
        {
            if (!Set(ref _overlayTab, value)) return;
            Raise(nameof(IsOverlayEggTab), nameof(IsOverlayPlayerTab), nameof(IsOverlayAutoTab),
                nameof(IsOverlayVisualTab), nameof(IsOverlayCommandTab));
        }
    }

    public bool IsOverlayEggTab => OverlayTab == "Egg";
    public bool IsOverlayPlayerTab => OverlayTab == "Player";
    public bool IsOverlayAutoTab => OverlayTab == "Auto";
    public bool IsOverlayVisualTab => OverlayTab == "Visual";
    public bool IsOverlayCommandTab => OverlayTab == "Cmd";

    public string FpsText => Visuals.FpsText;
    public bool ShowFps => _settings.Visual.ShowFps;
    public bool ShowNotifications => _settings.Visual.ShowNotifications;
    public bool CrosshairVisible => _settings.Visual.Crosshair;
    public bool CrosshairCrossVisible => CrosshairVisible && _settings.Visual.CrosshairStyle == "Cross";
    public bool CrosshairCircleVisible => CrosshairVisible && _settings.Visual.CrosshairStyle == "Circle";
    public bool CrosshairDotVisible => CrosshairVisible && _settings.Visual.CrosshairStyle == "Dot";
    public double CrosshairSize => _settings.Visual.CrosshairSize;

    public bool ShowEggValue => _settings.Visual.EspShowValue;
    public bool ShowEggDistance => _settings.Visual.EspShowDistance;

    public bool TrackerEnabled => _settings.Features.TryGetValue(FeatureIds.EggTracker, out var on) && on;
    public bool BestFinderEnabled => _settings.Features.TryGetValue(FeatureIds.BestEggFinder, out var on) && on;
    public bool NotificationsEnabled => _settings.Features.TryGetValue(FeatureIds.RareEggNotifications, out var on) && on;

    public double MenuWidth => _settings.Overlay.MenuWidth;
    public double UiScale => _settings.Overlay.UiScale;
    public double MenuOffsetX => _settings.Overlay.MenuOffsetX;
    public double MenuOffsetY => _settings.Overlay.MenuOffsetY;

    public string AccentHex => Visuals.AccentHex;
    public Color AccentColor => Visuals.Accent;

    public IReadOnlyList<string> MenuKeyOptions => HotkeyManager.AllowedKeys;
    public IReadOnlyList<string> MovementKeyOptions { get; } =
        "F,G,H,J,K,L,N,P,Q,R,U,V,X,Y,Z,B,C".Split(',');

    public IReadOnlyList<string> EspColorPresets { get; } =
        new[] { "#C084FC", "#22D3EE", "#F472B6", "#FBBF24", "#34D399", "#F87171", "#FFFFFF" };

    public IReadOnlyList<ThemeDefinition> Themes => ThemeCatalog.Themes;

    public IReadOnlyList<string> AutomationTargets { get; } = new[] { "Nearest", "Rarest", "Highest value", "All" };
    public IReadOnlyList<string> RarityPriorities { get; } = new[] { "Rare first", "Value first", "Distance first" };
    public IReadOnlyList<string> CrosshairStyles { get; } = new[] { "Cross", "Circle", "Dot" };

    public string CommandInput
    {
        get => _commandInput;
        set => Set(ref _commandInput, value);
    }

    public string LastCommand
    {
        get => _lastCommand;
        private set => Set(ref _lastCommand, value);
    }

    public string NoticeBanner
    {
        get => _noticeBanner;
        private set
        {
            if (Set(ref _noticeBanner, value)) OnPropertyChanged(nameof(HasNoticeBanner));
        }
    }

    public bool HasNoticeBanner => !string.IsNullOrWhiteSpace(NoticeBanner);
    public string ConsoleSummary => $"{ConsoleLines.Count} lines · {AppInfo.ProductName} v{AppInfo.Version}";

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public ICommand ConnectCommand => _connectCommand ??= new RelayCommand(() => _ = ConnectAsync());
    private RelayCommand? _connectCommand;

    public ICommand DisconnectCommand => _disconnectCommand ??= new RelayCommand(Disconnect);
    private RelayCommand? _disconnectCommand;

    public ICommand ToggleMenuCommand => _toggleMenuCommand ??= new RelayCommand(ToggleMenu);
    private RelayCommand? _toggleMenuCommand;

    public ICommand ActivateHubCommand => _activateHubCommand ??= new RelayCommand(() =>
        ActivateHubRequested?.Invoke(this, EventArgs.Empty));
    private RelayCommand? _activateHubCommand;

    public ICommand ExitCommand => _exitCommand ??= new RelayCommand(() => Shutdown());
    private RelayCommand? _exitCommand;

    public ICommand ExecuteCommand => _executeCommand ??= new RelayCommand(ExecuteCommandInput);
    private RelayCommand? _executeCommand;

    public ICommand ClearCommand => _clearCommand ??= new RelayCommand(ClearConsole);
    private RelayCommand? _clearCommand;

    public ICommand SelectTabCommand => _selectTabCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is string tab) SelectedTab = tab;
    });
    private RelayCommand? _selectTabCommand;

    public ICommand SelectOverlayTabCommand => _selectOverlayTabCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is string tab) OverlayTab = tab;
    });
    private RelayCommand? _selectOverlayTabCommand;

    public ICommand SaveConfigCommand => _saveConfigCommand ??= new RelayCommand(() =>
    {
        if (SaveSettings())
            AddNotice($"Config saved to {AppPaths.ConfigFile}", NoticeKind.Success);
    });
    private RelayCommand? _saveConfigCommand;

    public ICommand LoadConfigCommand => _loadConfigCommand ??= new RelayCommand(() =>
    {
        var loaded = _settingsManager.LoadFrom(AppPaths.ConfigFile);
        if (loaded is null)
        {
            AddNotice("Config file could not be loaded.", NoticeKind.Error);
            return;
        }

        SettingsCloner.CopyInto(_settings, loaded);
        AfterSettingsLoaded("Config loaded from disk.");
    });
    private RelayCommand? _loadConfigCommand;

    public ICommand ResetConfigCommand => _resetConfigCommand ??= new RelayCommand(() =>
    {
        var defaults = SettingsManager.CreateDefaults();
        SettingsCloner.CopyInto(_settings, defaults);
        AfterSettingsLoaded("Config reset to defaults.");
        AddNotice("Config reset to defaults.", NoticeKind.Success);
    });
    private RelayCommand? _resetConfigCommand;

    public ICommand ExportConfigCommand => _exportConfigCommand ??= new RelayCommand(() =>
    {
        if (_settingsManager.SaveTo(_settings, AppPaths.ExportedConfigFile))
            AddNotice($"Exported to {AppPaths.ExportedConfigFile}", NoticeKind.Success);
        else
            AddNotice("Config export failed.", NoticeKind.Error);
    });
    private RelayCommand? _exportConfigCommand;

    public ICommand ScanCommand => _scanCommand ??= new RelayCommand(ScanEggs);
    private RelayCommand? _scanCommand;

    public ICommand TrackBestCommand => _trackBestCommand ??= new RelayCommand(() =>
    {
        if (Eggs.TrackBest())
            AddNotice($"Tracking {Eggs.TrackedTarget?.Name}.", NoticeKind.Info);
        else
            AddNotice("No egg available to track.", NoticeKind.Error);
    });
    private RelayCommand? _trackBestCommand;

    public ICommand TrackEggCommand => _trackEggCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is Egg egg && Eggs.Track(egg))
            AddNotice($"Tracking {egg.Name} · {egg.DistanceText}.", NoticeKind.Info);
    });
    private RelayCommand? _trackEggCommand;

    public ICommand SellAllCommand => _sellAllCommand ??= new RelayCommand(() =>
    {
        var sold = Eggs.SellAll();
        AddNotice(sold > 0 ? $"Sold eggs for {sold:N0} coins." : "Nothing to sell yet.", sold > 0 ? NoticeKind.Success : NoticeKind.Info);
    });
    private RelayCommand? _sellAllCommand;

    public ICommand CollectCommand => _collectCommand ??= new RelayCommand(() => Player.SendCollect());
    private RelayCommand? _collectCommand;

    public ICommand TeleportCommand => _teleportCommand ??= new RelayCommand(() =>
    {
        if (Player.Teleport(_settings.Player.TeleportX, _settings.Player.TeleportY, _settings.Player.TeleportZ))
            AddNotice(Player.LastAction, NoticeKind.Success);
    });
    private RelayCommand? _teleportCommand;

    public ICommand StartAutomationCommand => _startAutomationCommand ??= new RelayCommand(() =>
    {
        if (!Automation.Start(_settings)) return;
        if (Player.IsOfficial) Player.SendAutomationControl(true);
        PushBridgeState(force: true);
        RaiseAutomationState();
    });
    private RelayCommand? _startAutomationCommand;

    public ICommand StopAutomationCommand => _stopAutomationCommand ??= new RelayCommand(() =>
    {
        Automation.Stop("manual stop");
        if (Player.IsOfficial) Player.SendAutomationControl(false);
        PushBridgeState(force: true);
        RaiseAutomationState();
    });
    private RelayCommand? _stopAutomationCommand;

    public ICommand ToggleAutomationCommand => _toggleAutomationCommand ??= new RelayCommand(() =>
    {
        if (Automation.IsRunning) StopAutomationCommand.Execute(null);
        else StartAutomationCommand.Execute(null);
    });
    private RelayCommand? _toggleAutomationCommand;

    public ICommand ApplyThemeCommand => _applyThemeCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is string theme) _settings.Visual.Theme = theme;
        ApplyTheme();
        MarkSettingsDirty();
    });
    private RelayCommand? _applyThemeCommand;

    public ICommand SelectEspColorCommand => _selectEspColorCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is not string hex) return;
        _settings.Visual.EspColor = hex;
        MarkSettingsDirty();
        RefreshVisualBindings();
    });
    private RelayCommand? _selectEspColorCommand;

    public ICommand SelectRareColorCommand => _selectRareColorCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is not string hex) return;
        _settings.Visual.RareEspColor = hex;
        MarkSettingsDirty();
        RefreshVisualBindings();
    });
    private RelayCommand? _selectRareColorCommand;

    public ICommand CheckUpdatesCommand => _checkUpdatesCommand ??= new RelayCommand(() => _ = CheckUpdatesAsync());
    private RelayCommand? _checkUpdatesCommand;

    public ICommand OpenLogsCommand => _openLogsCommand ??= new RelayCommand(() => OpenFolder(_log.DirectoryPath));
    private RelayCommand? _openLogsCommand;

    public ICommand OpenDataFolderCommand => _openDataFolderCommand ??= new RelayCommand(() => OpenFolder(AppPaths.DataRoot));
    private RelayCommand? _openDataFolderCommand;

    public ICommand OpenLuauFolderCommand => _openLuauFolderCommand ??= new RelayCommand(() => OpenFolder(AppPaths.BundledLuauDir));
    private RelayCommand? _openLuauFolderCommand;

    public ICommand CopyBridgeEndpointCommand => _copyBridgeEndpointCommand ??= new RelayCommand(() =>
    {
        try
        {
            Clipboard.SetText(_bridge.Endpoint);
            AddNotice("Bridge endpoint copied to clipboard.", NoticeKind.Success);
        }
        catch (Exception ex)
        {
            _log.Error("Clipboard unavailable.", ex);
        }
    });
    private RelayCommand? _copyBridgeEndpointCommand;

    public ICommand RefreshRobloxCommand => _refreshRobloxCommand ??= new RelayCommand(() =>
    {
        _detector.ScanNow();
        RefreshDetectorBindings();
        AddConsole("Roblox window enumeration refreshed.", NoticeKind.Info);
    });
    private RelayCommand? _refreshRobloxCommand;

    public ICommand ReloadSnapshotCommand => _reloadSnapshotCommand ??= new RelayCommand(() =>
    {
        var snapshot = _snapshots.LoadNewest();
        if (snapshot is null)
        {
            AddNotice("No snapshot files found in the snapshots folder.", NoticeKind.Error);
            return;
        }

        Eggs.ApplySnapshot(snapshot, EggOrigin.External);
        Eggs.MarkSnapshotFresh();
        AddNotice($"Snapshot loaded: {snapshot.Eggs.Count} eggs.", NoticeKind.Success);
    });
    private RelayCommand? _reloadSnapshotCommand;

    public ICommand UnloadOverlayCommand => _unloadOverlayCommand ??= new RelayCommand(() =>
    {
        Disconnect();
        AddNotice("Overlay unloaded.", NoticeKind.Info);
    });
    private RelayCommand? _unloadOverlayCommand;

    public ICommand ResetMenuPositionCommand => _resetMenuPositionCommand ??= new RelayCommand(() =>
    {
        _settings.Overlay.MenuOffsetX = 56;
        _settings.Overlay.MenuOffsetY = 56;
        Raise(nameof(MenuOffsetX), nameof(MenuOffsetY));
        MarkSettingsDirty();
    });
    private RelayCommand? _resetMenuPositionCommand;

    public ICommand DismissNoticeCommand => _dismissNoticeCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is HubNotice notice) Notifications.Remove(notice);
        else if (Notifications.Count > 0) Notifications.RemoveAt(0);
    });
    private RelayCommand? _dismissNoticeCommand;

    public ICommand InsertCommandSnippetCommand => _insertSnippetCommand ??= new RelayCommand(parameter =>
    {
        if (parameter is not string snippet) return;
        CommandInput = string.IsNullOrWhiteSpace(CommandInput) ? snippet : CommandInput + Environment.NewLine + snippet;
    });
    private RelayCommand? _insertSnippetCommand;

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void Start()
    {
        _hotkeys.Start();
        _hotkeys.KeyPressed += OnGlobalKey;
        _detector.Start();
        _bridge.Start(_settings.Bridge.Port);
        _snapshots.StartWatching();

        _lastTick = DateTime.UtcNow;
        _timer.Start();

        _bridge.PublishState(Player.BuildState(_settings));
        PushBridgeState(force: true);

        AddConsole($"Bridge listening on {_bridge.Endpoint}.", NoticeKind.Info);
        AddConsole($"Config: {AppPaths.ConfigFile}", NoticeKind.Info);
        AddConsole($"Logs: {_log.DirectoryPath}", NoticeKind.Info);
        AddConsole($"Detector: {_detector.StatusText}.", NoticeKind.Info);

        if (_settings.Overlay.AutoStartOverlay)
        {
            _log.Info("Auto start overlay is enabled; waiting for a Roblox client.");
            _pendingConnect = true;
            Raise(nameof(StatusLine));
            if (_detector.IsDetected) _ = ConnectAsync();
        }

        if (_settings.Automation.AutoStart && AutomationFeatures.AnyFeatureEnabled(_settings))
        {
            Automation.Start(_settings);
            RaiseAutomationState();
        }
    }

    private async Task ConnectAsync()
    {
        if (IsConnecting) return;

        // Already attached to a real Roblox client: nothing to do. A preview attachment
        // is upgraded to the real client window as soon as one appears.
        if (IsConnected && !IsPreviewOverlay) return;

        IsConnecting = true;
        ConnectionState = "CONNECTING";
        Raise(nameof(StatusLine));
        AddConsole("CONNECT: enumerating Roblox client windows…", NoticeKind.Info);
        _log.Info("CONNECT requested.");

        RobloxWindowInfo? window = null;
        try
        {
            window = _detector.ScanNow();
            await Task.Delay(450); // let the connect animation play
        }
        catch (Exception ex)
        {
            _log.Error("Roblox detection failed during connect.", ex);
        }

        if (window is null)
        {
            // No Roblox client: attach the same overlay to the desktop in PREVIEW mode so
            // RIGHT SHIFT, the menu, dragging, ESP and every tab are actually usable now.
            // The attach is upgraded automatically the moment a client is detected.
            try
            {
                AttachPreviewOverlay();
                _pendingConnect = true;
                IsConnecting = false;
                ConnectionState = "CONNECTED · PREVIEW";
                AddConsole("Roblox was not found — the overlay is attached to the desktop in PREVIEW mode.", NoticeKind.Info);
                AddNotice("PREVIEW overlay attached. RIGHT SHIFT opens the menu; it switches to Roblox automatically when the client starts.", NoticeKind.Automation);
                Raise(nameof(StatusLine), nameof(RobloxSummary), nameof(OverlaySummary));
                return;
            }
            catch (Exception ex)
            {
                _log.Error("Preview overlay attach failed.", ex);
            }

            _pendingConnect = true;
            IsConnecting = false;
            ConnectionState = "WAITING FOR ROBLOX";
            AddConsole("Roblox was not found and the preview overlay could not attach. Press CONNECT again once Roblox is running.", NoticeKind.Error);
            AddNotice("Roblox not found — press CONNECT again once the client is running.", NoticeKind.Error);
            Raise(nameof(StatusLine), nameof(RobloxSummary));
            return;
        }

        try
        {
            AttachOverlay(window);
            ConnectionState = "CONNECTED";
            AddConsole($"Overlay attached to the Roblox client ({window.ResolutionText}).", NoticeKind.Success);
            AddNotice("Overlay connected. Press RIGHT SHIFT for the in-game menu.", NoticeKind.Success);
            if (_settings.Overlay.StartMenuOpen) ShowMenu();
        }
        catch (Exception ex)
        {
            _log.Error("Overlay attach failed.", ex);
            AddNotice($"Overlay failed: {ex.Message}", NoticeKind.Error);
            ConnectionState = "ERROR";
        }
        finally
        {
            _pendingConnect = false;
            IsConnecting = false;
            Raise(nameof(StatusLine), nameof(OverlaySummary));
        }
    }

    private void AttachPreviewOverlay()
    {
        EnsureOverlayManager();
        _overlay!.AttachPreview();
        IsConnected = true;
        Raise(nameof(OverlaySummary), nameof(StatusLine), nameof(OverlayStatusText), nameof(OverlayStateLabel), nameof(IsPreviewOverlay));
    }

    private void EnsureOverlayManager()
    {
        if (_overlay is not null) return;

        _overlay = new OverlayManager(this, _log, _detector);
        _overlay.MenuStateChanged += (_, _) =>
        {
            Raise(nameof(IsMenuOpen), nameof(StatusLine));
            MenuStateChanged?.Invoke(this, EventArgs.Empty);
        };
    }

    private void AttachOverlay(RobloxWindowInfo window)
    {
        EnsureOverlayManager();
        _overlay!.Attach(window);
        _overlayTargetHandle = window.Handle;
        IsConnected = true;
        Raise(nameof(OverlaySummary), nameof(StatusLine), nameof(OverlayStatusText), nameof(OverlayStateLabel), nameof(IsPreviewOverlay));
    }

    private void Disconnect()
    {
        if (_overlay is not null)
        {
            _overlay.Detach();
            _overlay.Dispose();
            _overlay = null;
        }

        _pendingConnect = false;
        IsConnected = false;
        _overlayTargetHandle = IntPtr.Zero;
        ConnectionState = "DISCONNECTED";
        Raise(nameof(OverlaySummary), nameof(StatusLine), nameof(IsMenuOpen), nameof(ConnectionState));
        AddConsole("Overlay disconnected.", NoticeKind.Info);
        _log.Info("DISCONNECT requested; overlay closed.");
    }

    public void ShowMenu()
    {
        if (_overlay is null || !IsConnected)
        {
            AddNotice("Connect the overlay first (CONNECT).", NoticeKind.Error);
            return;
        }

        _overlay.ShowMenu();
    }

    public void HideMenu() => _overlay?.HideMenu();

    public void ToggleMenu()
    {
        if (_overlay is null || !IsConnected)
        {
            AddNotice("Connect the overlay first (CONNECT).", NoticeKind.Error);
            return;
        }

        _overlay.ToggleMenu();
    }

    public void Shutdown()
    {
        if (_shutdown) return;
        _shutdown = true;
        _log.Info("Shutdown requested.");

        try
        {
            _timer.Stop();
            SaveSettings(force: true);
            _overlay?.Dispose();
            _overlay = null;
            _hotkeys.KeyPressed -= OnGlobalKey;
            _hotkeys.Dispose();
            _detector.Dispose();
            _bridge.Dispose();
            _snapshots.Dispose();
            _log.Info("Shutdown complete.");
        }
        catch (Exception ex)
        {
            _log.Error("Error during shutdown.", ex);
        }

        RequestExit?.Invoke(this, EventArgs.Empty);
    }

    public void Dispose()
    {
        _timer.Stop();
        _hotkeys.Dispose();
        _detector.Dispose();
        _bridge.Dispose();
        _snapshots.Dispose();
        _overlay?.Dispose();
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    private ObservableCollection<FeatureRowViewModel> CreateRows(IReadOnlyList<FeatureDefinition> definitions)
        => new(definitions.Select(def => new FeatureRowViewModel(def, _settings, OnFeatureToggled)));

    private void WireEvents()
    {
        _detector.StateChanged += (_, _) => OnDispatcher(() =>
        {
            RefreshDetectorBindings();

            var attachedToClient = IsConnected && !IsPreviewOverlay;

            if (_detector.IsDetected && _pendingConnect && !IsConnecting && !attachedToClient)
            {
                _log.Info("Roblox client available; attaching the overlay to it.");
                _ = ConnectAsync();
            }
            else if (!_detector.IsDetected && attachedToClient)
            {
                _log.Warn("Roblox closed while the overlay was attached; switching to preview.");
                Disconnect();
                AddNotice("Roblox closed — the overlay was disconnected automatically.", NoticeKind.Error);
            }
        });

        _detector.WindowUpdated += (_, window) => OnDispatcher(() =>
        {
            _overlay?.UpdateTargetWindow(window);
            Raise(nameof(RobloxSummary));
        });

        _bridge.SnapshotReceived += (_, result) => OnDispatcher(() =>
        {
            var origin = (result.Snapshot.Source ?? string.Empty).Contains("external", StringComparison.OrdinalIgnoreCase)
                ? EggOrigin.External
                : EggOrigin.Official;

            Eggs.ApplySnapshot(result.Snapshot, origin);
            Eggs.MarkSnapshotFresh();
            _settingsManager.SaveLastKnownSnapshot(result.Snapshot);
            RefreshEspList(force: true);
            Raise(nameof(BridgeStatusText));
        });

        _bridge.EventReceived += (_, payload) => OnDispatcher(() => HandleBridgeEvent(payload.Name, payload.Payload));

        _snapshots.SnapshotLoaded += (_, snapshot) => OnDispatcher(() =>
        {
            if (_bridge.LastSnapshotUtc is { } last && DateTime.UtcNow - last < TimeSpan.FromSeconds(6)) return;
            Eggs.ApplySnapshot(snapshot, EggOrigin.External);
            Eggs.MarkSnapshotFresh();
            AddNotice($"{_snapshots.LastStatus}", NoticeKind.Info);
        });

        Eggs.EggDiscovered += (_, egg) => OnDispatcher(() =>
        {
            if (_settings.Features.TryGetValue(FeatureIds.EggList, out var show) && show)
                RefreshEspList(force: true);
            _log.Debug($"Scanner discovered {egg.Name} at {egg.Distance:0} studs.");
        });

        Eggs.RareEggDiscovered += (_, egg) => OnDispatcher(() =>
            AddNotice($"Rare egg detected: {egg.Name} · {egg.RarityText} · {egg.ValueText}", NoticeKind.Rare));

        Eggs.EggCollected += (_, egg) => OnDispatcher(() =>
        {
            AddConsole($"Collected {egg.Name} ({egg.RarityText}, {egg.Value} coins).", NoticeKind.Success);
            if (Eggs.IsDemoMode)
                AddNotice($"Collected {egg.Name} · +{egg.Value} coins pending", NoticeKind.Success);
        });

        Automation.Notice += (_, notice) => OnDispatcher(() => AddNotice(notice.Text, notice.Kind));
        Automation.StateChanged += (_, _) => OnDispatcher(RaiseAutomationState);

        Player.Notice += (_, message) => OnDispatcher(() => AddNotice(message, NoticeKind.Info));
        Visuals.AccentChanged += (_, _) => OnDispatcher(ApplyAccentBrushes);
    }

    private void HandleBridgeEvent(string name, JsonObject? payload)
    {
        switch (name.ToLowerInvariant())
        {
            case "ready":
                AddConsole($"Official test place bridge connected{DescribePlace(payload)}.", NoticeKind.Success);
                AddNotice("Test place bridge connected. Movement features are live.", NoticeKind.Success);
                break;

            case "collected":
            {
                var eggName = payload?["egg"]?.GetValue<string>() ?? "egg";
                var value = payload?["value"]?.GetValue<int>() ?? 0;
                AddConsole($"Test place collected {eggName} (+{value} coins).", NoticeKind.Success);
                AddNotice($"Collected {eggName} · +{value} coins", NoticeKind.Success);
                break;
            }

            case "sold":
            {
                var coins = payload?["coins"]?.GetValue<int>() ?? 0;
                AddConsole($"Test place sold collected eggs for {coins} coins.", NoticeKind.Success);
                break;
            }

            case "automation":
            {
                var state = payload?["state"]?.GetValue<string>() ?? "unknown";
                AddConsole($"Bridge automation state: {state}.", NoticeKind.Automation);
                break;
            }

            case "notice":
            case "log":
            {
                var message = payload?["message"]?.GetValue<string>();
                if (!string.IsNullOrWhiteSpace(message))
                {
                    AddConsole($"Bridge: {message}", NoticeKind.Info);
                    AddNotice(message, NoticeKind.Info);
                }

                break;
            }

            default:
                AddConsole($"Bridge event: {name}", NoticeKind.Info);
                break;
        }

        Raise(nameof(BridgeStatusText));
    }

    private static string DescribePlace(JsonObject? payload)
    {
        var place = payload?["place"]?.GetValue<string>();
        return string.IsNullOrWhiteSpace(place) ? string.Empty : $" ({place})";
    }

    private void RefreshDetectorBindings()
    {
        Raise(nameof(RobloxStatusText), nameof(RobloxDetected), nameof(StudioDetected),
            nameof(RobloxSummary), nameof(StatusLine));
    }

    private void RaiseAutomationState()
    {
        Raise(nameof(AutomationStartStopText), nameof(AutomationStatsText), nameof(AutomationLastAction));
        OnPropertyChanged(nameof(Automation.IsRunning));
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    private void OnTick(object? sender, EventArgs e)
    {
        if (_shutdown) return;

        var now = _clock.Elapsed;
        var last = _lastTick;
        _lastTick = DateTime.UtcNow;
        var delta = _lastTick - last;
        if (delta <= TimeSpan.Zero || delta > TimeSpan.FromSeconds(1)) delta = TimeSpan.FromMilliseconds(33);

        try
        {
            _fps.Update(delta.TotalSeconds);
            Visuals.Tick(delta, _settings, _settings.Visual.ShowFps);
            if (_settings.Visual.ShowFps) Raise(nameof(FpsText));

            Player.RefreshMode(_settings);

            var (width, height) = _overlay?.ViewSize ?? (1280.0, 720.0);
            Eggs.Update(delta, _settings, width, height);
            Automation.Update(delta, _settings);

            if (!Eggs.IsDemoMode && (DateTime.UtcNow - _lastExpiryCheck).TotalSeconds > 2)
            {
                _lastExpiryCheck = DateTime.UtcNow;
                Eggs.ExpireLiveSource(TimeSpan.FromSeconds(20));
                Raise(nameof(BridgeStatusText));
            }

            _overlay?.Tick(delta);

            if (_bridgeDirty && (DateTime.UtcNow - _lastBridgePush).TotalMilliseconds > 250)
                PushBridgeState(force: false);

            if ((DateTime.UtcNow - _lastEspSync).TotalMilliseconds > 60)
                RefreshEspList(force: false);

            PruneNotifications();

            if (_savePending && (DateTime.UtcNow - _saveRequestedAt).TotalMilliseconds > 1200)
                SaveSettings(force: true);

            // Cheap periodic refresh of status lines.
            if (_fps.Fps > 0 && now.Milliseconds < 40)
                Raise(nameof(BridgeStatusText), nameof(OverlaySummary), nameof(RobloxSummary));
        }
        catch (Exception ex)
        {
            _log.Error("UI tick failed.", ex);
        }

        _ = sender;
    }

    // ------------------------------------------------------------------
    // ESP / overlay sync
    // ------------------------------------------------------------------

    private void RefreshEspList(bool force)
    {
        var visible = Eggs.GetVisibleEggs(_settings);

        if (!force && visible.Count == EspEggs.Count)
        {
            var same = true;
            for (var i = 0; i < visible.Count; i++)
            {
                if (!ReferenceEquals(visible[i], EspEggs[i]))
                {
                    same = false;
                    break;
                }
            }

            if (same) return;
        }

        EspEggs.Clear();
        foreach (var egg in visible) EspEggs.Add(egg);
        _lastEspSync = DateTime.UtcNow;
        Raise(nameof(EspSummary));
    }

    public string EspSummary => EspEggs.Count == 0
        ? "ESP idle — enable Egg ESP or Rare Egg ESP."
        : $"{EspEggs.Count} eggs drawn · {EspEggs.Count(e => e.IsRare)} rare";

    // ------------------------------------------------------------------
    // Settings plumbing
    // ------------------------------------------------------------------

    private void HookSettings()
    {
        _settings.PropertyChanged += OnSettingsPropertyChanged;
        foreach (var child in _settings.ChildSettings) child.PropertyChanged += OnSettingsPropertyChanged;
    }

    private void OnSettingsPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (_shutdown) return;

        if (e.PropertyName is nameof(OverlaySettings.MenuKey) or nameof(OverlaySettings.Opacity)
            or nameof(OverlaySettings.UiScale) or nameof(OverlaySettings.MenuWidth))
        {
            _overlay?.ApplySettings();
            Raise(nameof(MenuWidth), nameof(UiScale), nameof(MenuOffsetX), nameof(MenuOffsetY));
        }

        if (e.PropertyName is nameof(VisualSettings.Theme) or nameof(VisualSettings.RainbowMode))
            ApplyTheme();

        if (e.PropertyName is nameof(VisualSettings.EspColor) or nameof(VisualSettings.RareEspColor))
            RefreshEspList(force: true);

        RefreshVisualBindings();
        MarkSettingsDirty();
    }

    private void MarkSettingsDirty()
    {
        _savePending = true;
        _saveRequestedAt = DateTime.UtcNow;
        _bridgeDirty = true;
        Raise(nameof(BridgeStatusText));
    }

    private bool SaveSettings(bool force = false)
    {
        if (!force && !_savePending) return true;

        var ok = _settingsManager.Save(_settings);
        _savePending = false;
        if (ok) _log.Debug("Settings auto-saved.");
        return ok;
    }

    private void AfterSettingsLoaded(string message)
    {
        foreach (var row in EggRows.Concat(PlayerRows).Concat(AutomationRows))
            row.SyncFromSettings();

        Visuals.Apply(_settings);
        ApplyTheme();
        RefreshVisualBindings();
        RefreshEspList(force: true);
        _overlay?.ApplySettings();
        PushBridgeState(force: true);
        SaveSettings(force: true);
        AddConsole(message, NoticeKind.Success);
        Raise(nameof(MenuOffsetX), nameof(MenuOffsetY));
    }

    private void OnFeatureToggled(string id, bool enabled)
    {
        var definition = FeatureRegistry.Find(id);
        AddConsole($"{(enabled ? "Enabled" : "Disabled")} {definition?.Title ?? id}.", enabled ? NoticeKind.Success : NoticeKind.Info);

        if (enabled && definition is { Scope: FeatureScope.OfficialPlaceOnly } && !Player.IsOfficial)
            AddNotice($"{definition.Title}: test-place only — currently previewing on the demo island.", NoticeKind.Automation);

        if (id is FeatureIds.EggEsp or FeatureIds.RareEggEsp or FeatureIds.EggRarityFilter)
            RefreshEspList(force: true);

        if (!Automation.IsRunning && FeatureIds.Automation.Contains(id) && enabled
            && _settings.Automation.AutoStart)
        {
            Automation.Start(_settings);
            RaiseAutomationState();
        }

        PushBridgeState(force: true);
        MarkSettingsDirty();
        RefreshCaptureState();
    }

    private void RefreshCaptureState()
    {
        Raise(nameof(AutomationStartStopText), nameof(AutomationStatsText), nameof(AutomationLastAction), nameof(EspSummary));
    }

    // ------------------------------------------------------------------
    // Bridge / visual helpers
    // ------------------------------------------------------------------

    private void PushBridgeState(bool force)
    {
        if (!force && !_bridgeDirty) return;
        _bridge.PublishState(Player.BuildState(_settings));
        _lastBridgePush = DateTime.UtcNow;
        _bridgeDirty = false;
        Raise(nameof(BridgeStatusText));
    }

    private void ApplyTheme()
    {
        Visuals.Apply(_settings);
        ApplyAccentBrushes();

        var theme = ThemeCatalog.Get(_settings.Visual.Theme);
        SetBrush("WindowBackgroundBrush", theme.Background);
        SetBrush("SurfaceBrush", theme.Panel);
        SetBrush("SurfaceAltBrush", Darken(theme.Panel, 0.12));
        SetBrush("SurfaceHoverBrush", Lighten(theme.Panel, 0.10));
        SetBrush("BorderBrush", theme.Border);
        SetBrush("BorderSoftBrush", Darken(theme.Border, 0.25));
        SetBrush("TextPrimaryBrush", theme.TextPrimary);
        SetBrush("TextSecondaryBrush", theme.TextMuted);
        SetBrush("TextMutedBrush", theme.TextMuted);
        SetBrush("AccentAltBrush", theme.AccentAlt);
        SetBrush("AccentDeepBrush", Darken(theme.Accent, 0.25));
    }

    private void ApplyAccentBrushes()
    {
        var accent = Visuals.Accent;
        var alt = Visuals.AccentAlt;
        SetBrush("AccentBrush", accent);
        SetBrush("AccentSoftBrush", Color.FromArgb(0x33, accent.R, accent.G, accent.B));
        SetBrush("AccentAltBrush", alt);

        var gradient = new LinearGradientBrush(accent, alt, new Point(0, 0), new Point(1, 1));
        gradient.Freeze();
        if (Application.Current is not null)
            Application.Current.Resources["AccentGradientBrush"] = gradient;

        OnPropertyChanged(nameof(AccentHex));
        OnPropertyChanged(nameof(AccentColor));
    }

    private static void SetBrush(string key, string hex) => SetBrush(key, ColorUtil.Parse(hex, Colors.MediumPurple));

    private static void SetBrush(string key, Color color)
    {
        if (Application.Current is null) return;
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        Application.Current.Resources[key] = brush;
    }

    private static string Lighten(string hex, double amount)
    {
        var c = ColorUtil.Parse(hex, Colors.Black);
        return ColorUtil.ToHex(Color.FromRgb(
            (byte)Math.Min(255, c.R + 255 * amount),
            (byte)Math.Min(255, c.G + 255 * amount),
            (byte)Math.Min(255, c.B + 255 * amount)));
    }

    private static string Darken(string hex, double amount)
    {
        var c = ColorUtil.Parse(hex, Colors.Black);
        return ColorUtil.ToHex(Color.FromRgb(
            (byte)Math.Max(0, c.R - 255 * amount),
            (byte)Math.Max(0, c.G - 255 * amount),
            (byte)Math.Max(0, c.B - 255 * amount)));
    }

    private void RefreshVisualBindings()
    {
        Raise(nameof(ShowFps), nameof(ShowNotifications), nameof(CrosshairVisible), nameof(CrosshairCrossVisible),
            nameof(CrosshairCircleVisible), nameof(CrosshairDotVisible), nameof(CrosshairSize),
            nameof(ShowEggValue), nameof(ShowEggDistance), nameof(MenuWidth), nameof(UiScale),
            nameof(MenuOffsetX), nameof(MenuOffsetY), nameof(EspSummary), nameof(AccentHex),
            nameof(TrackerEnabled), nameof(BestFinderEnabled), nameof(NotificationsEnabled));
    }

    // ------------------------------------------------------------------
    // Menu position (dragging)
    // ------------------------------------------------------------------

    public void SetMenuOffset(double x, double y)
    {
        if (!_settings.Overlay.RememberPosition) return;

        var width = _overlay?.ViewSize.Width ?? 1280;
        var height = _overlay?.ViewSize.Height ?? 720;
        var menuWidth = _settings.Overlay.MenuWidth * _settings.Overlay.UiScale;

        _settings.Overlay.MenuOffsetX = Math.Clamp(x, 0, Math.Max(0, width - menuWidth));
        _settings.Overlay.MenuOffsetY = Math.Clamp(y, 0, Math.Max(0, height - 80));
        Raise(nameof(MenuOffsetX), nameof(MenuOffsetY));
        MarkSettingsDirty();
    }

    // ------------------------------------------------------------------
    // Commands / console / notifications
    // ------------------------------------------------------------------

    private void BuildCommandContext()
    {
        _commands.Attach(new CommandContext
        {
            IsOverlayVisible = () => IsMenuOpen,
            ShowOverlay = ShowMenu,
            HideOverlay = HideMenu,
            ToggleOverlay = ToggleMenu,
            ScanEggs = ScanEggs,
            SetRareNotifications = flag =>
            {
                _settings.Features[FeatureIds.RareEggNotifications] = flag;
                SyncRow(FeatureIds.RareEggNotifications);
                MarkSettingsDirty();
            },
            SetAutomation = start =>
            {
                if (start) StartAutomationCommand.Execute(null);
                else StopAutomationCommand.Execute(null);
            },
            SaveConfig = () => SaveSettings(force: true),
            LoadConfig = () =>
            {
                var loaded = _settingsManager.LoadFrom(AppPaths.ConfigFile);
                if (loaded is null) return false;
                SettingsCloner.CopyInto(_settings, loaded);
                AfterSettingsLoaded("Config reloaded by command.");
                return true;
            },
            Print = text => AddConsole(text, NoticeKind.Info),
            Notify = text => AddNotice(text, NoticeKind.Info),
            Teleport = (x, y, z) => Player.Teleport(x, y, z),
            Eggs = () => Eggs.Eggs.ToList(),
            SetInterval = ms =>
            {
                _settings.Automation.IntervalMs = ms;
                MarkSettingsDirty();
            },
            SetEspColor = hex =>
            {
                _settings.Visual.EspColor = hex;
                RefreshEspList(force: true);
                MarkSettingsDirty();
            },
            TrackBestEgg = () => Eggs.TrackBest(),
            ClearConsole = ClearConsole,
            IsConnected = () => IsConnected
        });
    }

    private void ExecuteCommandInput()
    {
        var script = CommandInput;
        if (string.IsNullOrWhiteSpace(script))
        {
            AddConsole("Nothing to execute. Type help() to see commands.", NoticeKind.Info);
            return;
        }

        var firstLine = script.Replace("\r", string.Empty).Split('\n')[0].Trim();
        LastCommand = firstLine.Length > 80 ? firstLine[..80] + "…" : firstLine;
        AddConsole($"> {script.Replace("\r", string.Empty).Replace("\n", " ; ")}", NoticeKind.Info);

        var result = _commands.Execute(script);
        foreach (var line in result.Lines)
            AddConsole(line, result.Ok ? NoticeKind.Success : NoticeKind.Error);

        if (!result.Ok) AddNotice("Command failed — see the console.", NoticeKind.Error);

        CommandInput = string.Empty;
    }

    public void AddConsole(string text, NoticeKind kind = NoticeKind.Info)
    {
        var line = new ConsoleLine(DateTime.Now, kind, text);
        ConsoleLines.Add(line);
        while (ConsoleLines.Count > 400) ConsoleLines.RemoveAt(0);
        Raise(nameof(ConsoleSummary));

        if (kind == NoticeKind.Error) _log.Warn(text);
    }

    private void ClearConsole()
    {
        ConsoleLines.Clear();
        CommandInput = string.Empty;
        Raise(nameof(ConsoleSummary));
        AddConsole("Console cleared.", NoticeKind.Info);
    }

    public void AddNotice(string text, NoticeKind kind = NoticeKind.Info)
    {
        if (string.IsNullOrWhiteSpace(text)) return;

        var notice = new HubNotice(text, kind, DateTime.Now);
        Notifications.Insert(0, notice);
        while (Notifications.Count > 4) Notifications.RemoveAt(Notifications.Count - 1);
        NoticeBanner = text;

        switch (kind)
        {
            case NoticeKind.Error:
                _log.Warn(text);
                break;
            case NoticeKind.Rare:
            case NoticeKind.Success:
            case NoticeKind.Automation:
                _log.Info(text);
                break;
        }

        Raise(nameof(HasNoticeBanner));
    }

    public void PruneNotifications()
    {
        if (Notifications.Count == 0) return;

        var expired = Notifications.Where(n => (DateTime.Now - n.Time).TotalSeconds > 9).ToList();
        foreach (var notice in expired) Notifications.Remove(notice);

        if (Notifications.Count == 0 && HasNoticeBanner)
            NoticeBanner = string.Empty;

        Raise(nameof(HasNoticeBanner));
    }

    private void DismissNotice(HubNotice notice) => Notifications.Remove(notice);

    // ------------------------------------------------------------------
    // Hotkeys
    // ------------------------------------------------------------------

    private void OnGlobalKey(object? sender, Key key)
    {
        try
        {
            // Do not react while the user is typing into the hub.
            if (Keyboard.FocusedElement is System.Windows.Controls.TextBox) return;

            var menuKey = ParseKey(_settings.Overlay.MenuKey);
            if (key == menuKey)
            {
                if (!IsConnected)
                    _ = ConnectAsync();
                else
                    ToggleMenu();
            }
        }
        catch (Exception ex)
        {
            _log.Error("Hotkey handling failed.", ex);
        }
    }

    private static Key ParseKey(string name)
        => Enum.TryParse<Key>(name, ignoreCase: true, out var key) ? key : Key.RightShift;

    // ------------------------------------------------------------------
    // Misc operations
    // ------------------------------------------------------------------

    private void ScanEggs()
    {
        if (!Player.IsOfficial)
        {
            Eggs.ResetDemo("manual scan");
            AddNotice("Demo egg index rebuilt with fresh eggs.", NoticeKind.Success);
        }
        else
        {
            Player.SendScan();
        }

        Player.RefreshMode(_settings);
        RefreshEspList(force: true);
        AddConsole($"Scan complete — {Eggs.Eggs.Count} eggs indexed ({Eggs.SourceModeText}).", NoticeKind.Success);
    }

    private void VerifyApplicationFiles()
    {
        try
        {
            var luau = Directory.Exists(AppPaths.BundledLuauDir)
                ? Directory.GetFiles(AppPaths.BundledLuauDir, "*.lua", SearchOption.AllDirectories).Length
                : 0;

            AssetsStatus = luau > 0
                ? $"Luau modules: {luau} file(s) in {AppPaths.BundledLuauDir}"
                : $"Luau modules not found in {AppPaths.BundledLuauDir}";

            _log.Info(AssetsStatus);
        }
        catch (Exception ex)
        {
            AssetsStatus = "File verification failed: " + ex.Message;
            _log.Error("File verification failed.", ex);
        }
    }

    private static string CheckWindowsCompatibility()
    {
        if (!OperatingSystem.IsWindows())
            return "Windows is required for the overlay and hotkeys. UI-only mode.";

        if (!Environment.Is64BitOperatingSystem)
            return "64-bit Windows is recommended; running on a 32-bit OS.";

        if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 17763))
            return "Windows 10 1809 or newer is recommended for per-monitor DPI support.";

        return "Windows 10/11 x64 detected — overlay APIs available.";
    }

    private static void OpenFolder(string path)
    {
        try
        {
            if (!Directory.Exists(path)) Directory.CreateDirectory(path);
            Process.Start(new ProcessStartInfo
            {
                FileName = "explorer.exe",
                Arguments = $"\"{path}\"",
                UseShellExecute = true
            });
        }
        catch
        {
            // Explorer may be unavailable in restricted sessions.
        }
    }

    private async Task CheckUpdatesAsync()
    {
        UpdateStatus = "Checking GitHub releases…";
        AddConsole($"Checking for updates: {AppInfo.RepositoryUrl}", NoticeKind.Info);

        try
        {
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(10) };
            http.DefaultRequestHeaders.UserAgent.ParseAdd($"StealAnEggUltimate/{AppInfo.Version}");
            var json = await http.GetStringAsync($"{AppInfo.RepositoryUrl.Replace(".git", string.Empty)}/releases/latest");
            var node = JsonNode.Parse(json);
            var tag = node?["tag_name"]?.GetValue<string>() ?? "unknown";
            var url = node?["html_url"]?.GetValue<string>() ?? AppInfo.RepositoryUrl;

            UpdateStatus = $"Latest published release: {tag} · installed v{AppInfo.Version}";
            AddConsole(UpdateStatus, NoticeKind.Success);
            AddConsole($"Release page: {url}", NoticeKind.Info);
            AddNotice($"Update check OK — latest release {tag}.", NoticeKind.Success);
        }
        catch (Exception ex)
        {
            UpdateStatus = $"Update check failed: {ex.Message}";
            AddConsole(UpdateStatus, NoticeKind.Error);
            AddNotice("Update check failed (offline or no published release).", NoticeKind.Error);
        }
    }

    public void LoadSnapshotFromPath(string path)
    {
        var snapshot = _snapshots.LoadFile(path, raiseEvent: false);
        if (snapshot is null)
        {
            AddNotice(_snapshots.LastStatus, NoticeKind.Error);
            return;
        }

        Eggs.ApplySnapshot(snapshot, EggOrigin.External);
        Eggs.MarkSnapshotFresh();
        AddNotice(_snapshots.LastStatus, NoticeKind.Success);
        AddConsole(_snapshots.LastStatus, NoticeKind.Success);
    }

    // ------------------------------------------------------------------
    // Bindings used by the overlay menu
    // ------------------------------------------------------------------

    public string AutomationStartStopText => Automation.StartStopText;
    public string AutomationStatsText => Automation.StatsText;
    public string AutomationLastAction => Automation.LastAction;

    public string TrackedEggText => Eggs.TrackedTargetText;
    public string BestEggText => Eggs.BestEggText;
    public string EggStatsText => Eggs.StatsText;

    public string PlayerModeText => Player.ModeText;
    public string PlayerModeDetail => Player.ModeDetail;
    public string PlayerBridgeStatus => Player.BridgeStatus;

    public string OverlayHint => IsConnected
        ? IsPreviewOverlay
            ? "PREVIEW over the desktop · RIGHT SHIFT toggles the menu · drag the header to move it"
            : "RIGHT SHIFT closes the menu · drag the header to move it"
        : "Not connected · press CONNECT in the hub";

    private void OnDispatcher(Action action)
    {
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null)
        {
            action();
            return;
        }

        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(action, DispatcherPriority.Background);
    }

    private void SyncRow(string id)
    {
        foreach (var row in EggRows.Concat(PlayerRows).Concat(AutomationRows))
        {
            if (row.Id == id) row.SyncFromSettings();
        }
    }
}
