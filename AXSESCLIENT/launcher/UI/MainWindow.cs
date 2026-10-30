using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using Avalonia.Threading;
using AxsesClient.Launcher.Models;
using AxsesClient.Launcher.Services;

namespace AxsesClient.Launcher.UI;

public sealed class MainWindow : Window
{
    private readonly LauncherStateController _state;
    private CancellationTokenSource? _authCts;

    private string _moduleSearchQuery = "";
    private string _selectedModuleCategory = "All";

    private Border _rootShell = null!;
    private StackPanel _navStack = null!;
    private ContentControl _pageHost = null!;
    private TextBlock _statusText = null!;
    private ProgressBar _footerProgress = null!;
    private TextBlock _headerVersionPillText = null!;
    private TextBlock _headerAccountText = null!;

    public MainWindow() : this(new LauncherStateController())
    {
    }

    public MainWindow(LauncherStateController state)
    {
        _state = state;
        Title = "AXSESCLIENT — GENESIS X v1.0.0";
        Width = 1220;
        Height = 760;
        MinWidth = 980;
        MinHeight = 640;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;
        ExtendClientAreaToDecorationsHint = true;
        ExtendClientAreaChromeHints = ExtendClientAreaChromeHints.NoChrome;
        ExtendClientAreaTitleBarHeightHint = -1;

        TrySetWindowIcon();
        BuildShell();
        _state.StateChanged += () => Dispatcher.UIThread.Post(RefreshUi);
        RefreshUi();
    }

    public LauncherStateController Controller => _state;

    public void NavigateTo(LauncherPage page)
    {
        _state.CurrentPage = page;
        RefreshUi();
    }

    private void TrySetWindowIcon()
    {
        try
        {
            using var stream = AssetLoader.Open(new Uri("avares://AXSESCLIENT/Assets/icon-128.png"));
            Icon = new WindowIcon(stream);
        }
        catch { }
    }

    private void BuildShell()
    {
        var theme = _state.ActiveTheme;
        Background = BrushOf(theme.BgPrimary);

        var mainGrid = new Grid
        {
            RowDefinitions = new RowDefinitions("58,*,36"),
            ColumnDefinitions = new ColumnDefinitions("228,*")
        };

        // Top Header Bar
        var header = BuildHeaderBar();
        Grid.SetRow(header, 0);
        Grid.SetColumnSpan(header, 2);
        mainGrid.Children.Add(header);

        // Left Sidebar Navigation
        var sidebar = new Border
        {
            Background = BrushOf(theme.BgSecondary),
            BorderBrush = BrushOf("#22FFFFFF"),
            BorderThickness = new Thickness(0, 0, 1, 0),
            Padding = new Thickness(12, 14, 12, 14)
        };
        _navStack = new StackPanel { Spacing = 4 };
        sidebar.Child = new ScrollViewer { Content = _navStack };
        Grid.SetRow(sidebar, 1);
        Grid.SetColumn(sidebar, 0);
        mainGrid.Children.Add(sidebar);

        // Main Content Host
        _pageHost = new ContentControl
        {
            Padding = new Thickness(24, 20, 24, 20)
        };
        var contentScroll = new ScrollViewer
        {
            Content = _pageHost,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto
        };
        Grid.SetRow(contentScroll, 1);
        Grid.SetColumn(contentScroll, 1);
        mainGrid.Children.Add(contentScroll);

        // Footer Status & Telemetry Bar
        var footer = BuildFooterBar();
        Grid.SetRow(footer, 2);
        Grid.SetColumnSpan(footer, 2);
        mainGrid.Children.Add(footer);

        _rootShell = new Border
        {
            Background = BrushOf(theme.BgPrimary),
            BorderBrush = BrushOf("#289060FF"),
            BorderThickness = new Thickness(1),
            Child = mainGrid
        };
        Content = _rootShell;
    }

    private Control BuildHeaderBar()
    {
        var theme = _state.ActiveTheme;
        var grid = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto,Auto,Auto")
        };

        var brandRow = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 10,
            VerticalAlignment = VerticalAlignment.Center
        };

        try
        {
            using var iconStream = AssetLoader.Open(new Uri("avares://AXSESCLIENT/Assets/icon-128.png"));
            brandRow.Children.Add(new Image
            {
                Source = new Bitmap(iconStream),
                Width = 28,
                Height = 28
            });
        }
        catch { }

        var titleCol = new StackPanel { Spacing = 0, VerticalAlignment = VerticalAlignment.Center };
        titleCol.Children.Add(new TextBlock
        {
            Text = "AXSESCLIENT",
            FontWeight = FontWeight.Bold,
            FontSize = 15,
            Foreground = BrushOf(theme.TextPrimary)
        });
        titleCol.Children.Add(new TextBlock
        {
            Text = "GENESIS X • v1.0.0",
            FontSize = 10,
            Foreground = BrushOf(theme.AccentCyan)
        });
        brandRow.Children.Add(titleCol);
        Grid.SetColumn(brandRow, 0);
        grid.Children.Add(brandRow);

        // Drag region
        var dragSpacer = new Border { Background = Brushes.Transparent };
        dragSpacer.PointerPressed += (_, e) =>
        {
            if (e.GetCurrentPoint(this).Properties.IsLeftButtonPressed)
                BeginMoveDrag(e);
        };
        Grid.SetColumn(dragSpacer, 1);
        grid.Children.Add(dragSpacer);

        // Version Pill
        _headerVersionPillText = new TextBlock
        {
            Text = $"MC {_state.SelectedVersionSpec.MinecraftVersion}",
            FontSize = 12,
            FontWeight = FontWeight.SemiBold,
            Foreground = BrushOf(theme.AccentCyan)
        };
        var versionPill = new Border
        {
            Background = BrushOf(theme.BgSurface),
            CornerRadius = new CornerRadius(6),
            Padding = new Thickness(10, 5),
            Margin = new Thickness(0, 0, 10, 0),
            VerticalAlignment = VerticalAlignment.Center,
            Child = _headerVersionPillText
        };
        Grid.SetColumn(versionPill, 2);
        grid.Children.Add(versionPill);

        // Account Pill
        _headerAccountText = new TextBlock
        {
            Text = _state.SelectedAccount?.Username ?? "No Account",
            FontSize = 12,
            Foreground = BrushOf(theme.TextSecondary),
            VerticalAlignment = VerticalAlignment.Center
        };
        var accountBtn = CreateSecondaryButton("Accounts", () => NavigateTo(LauncherPage.Accounts));
        var accountWrap = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 8,
            Margin = new Thickness(0, 0, 12, 0),
            VerticalAlignment = VerticalAlignment.Center,
            Children = { _headerAccountText, accountBtn }
        };
        Grid.SetColumn(accountWrap, 3);
        grid.Children.Add(accountWrap);

        // Window Controls
        var winBtns = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 6,
            VerticalAlignment = VerticalAlignment.Center
        };
        winBtns.Children.Add(CreateChromeButton("—", () => WindowState = WindowState.Minimized));
        winBtns.Children.Add(CreateChromeButton("□", () =>
            WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized));
        winBtns.Children.Add(CreateChromeButton("✕", Close, isDanger: true));
        Grid.SetColumn(winBtns, 4);
        grid.Children.Add(winBtns);

        return new Border
        {
            Background = BrushOf(theme.BgSecondary),
            BorderBrush = BrushOf("#22FFFFFF"),
            BorderThickness = new Thickness(0, 0, 0, 1),
            Padding = new Thickness(16, 0, 14, 0),
            Child = grid
        };
    }

    private Control BuildFooterBar()
    {
        var theme = _state.ActiveTheme;
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,220,Auto") };

        _statusText = new TextBlock
        {
            Text = _state.StatusBanner,
            FontSize = 11.5,
            Foreground = BrushOf(theme.TextSecondary),
            VerticalAlignment = VerticalAlignment.Center
        };
        Grid.SetColumn(_statusText, 0);
        grid.Children.Add(_statusText);

        _footerProgress = new ProgressBar
        {
            Minimum = 0,
            Maximum = 1,
            Value = _state.ActiveOperationProgress,
            Height = 6,
            Foreground = BrushOf(theme.AccentPrimary),
            Background = BrushOf(theme.BgPrimary),
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(12, 0)
        };
        Grid.SetColumn(_footerProgress, 1);
        grid.Children.Add(_footerProgress);

        var badge = new TextBlock
        {
            Text = "FAIR-PLAY VERIFIED • ZERO TELEMETRY",
            FontSize = 10.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = BrushOf(theme.Success),
            VerticalAlignment = VerticalAlignment.Center
        };
        Grid.SetColumn(badge, 2);
        grid.Children.Add(badge);

        return new Border
        {
            Background = BrushOf(theme.BgSecondary),
            BorderBrush = BrushOf("#22FFFFFF"),
            BorderThickness = new Thickness(0, 1, 0, 0),
            Padding = new Thickness(16, 0),
            Child = grid
        };
    }

    public void RefreshUi()
    {
        var theme = _state.ActiveTheme;
        _rootShell.Background = BrushOf(theme.BgPrimary);
        _statusText.Text = _state.StatusBanner;
        _footerProgress.Value = _state.ActiveOperationProgress;
        _footerProgress.Foreground = BrushOf(theme.AccentPrimary);
        _headerVersionPillText.Text = $"MC {_state.SelectedVersionSpec.MinecraftVersion} • {_state.SelectedVersionSpec.State.ToDisplayBadge()}";
        _headerAccountText.Text = _state.SelectedAccount is { } acc
            ? $"{acc.Username} ({(acc.OwnsMinecraftJava ? "Verified" : "Sandbox")})"
            : "Not Signed In";

        RebuildSidebar(theme);
        _pageHost.Content = _state.CurrentPage switch
        {
            LauncherPage.Dashboard => BuildDashboardPage(theme),
            LauncherPage.Play => BuildPlayPage(theme),
            LauncherPage.Versions => BuildVersionsPage(theme),
            LauncherPage.Accounts => BuildAccountsPage(theme),
            LauncherPage.Profiles => BuildProfilesPage(theme),
            LauncherPage.Modules => BuildModulesPage(theme),
            LauncherPage.Appearance => BuildAppearancePage(theme),
            LauncherPage.Settings => BuildSettingsPage(theme),
            LauncherPage.Downloads => BuildDownloadsPage(theme),
            LauncherPage.Diagnostics => BuildDiagnosticsPage(theme),
            LauncherPage.About => BuildAboutPage(theme),
            _ => BuildDashboardPage(theme)
        };
    }

    private void RebuildSidebar(LauncherThemePalette theme)
    {
        _navStack.Children.Clear();
        (LauncherPage Page, string Label, string Glyph)[] items =
        [
            (LauncherPage.Dashboard, "Dashboard", "◈"),
            (LauncherPage.Play, "Play", "▶"),
            (LauncherPage.Versions, "Versions", "❖"),
            (LauncherPage.Accounts, "Accounts", "◉"),
            (LauncherPage.Profiles, "Profiles", "☰"),
            (LauncherPage.Modules, "Modules (90)", "✦"),
            (LauncherPage.Appearance, "Appearance", "◐"),
            (LauncherPage.Settings, "Settings", "⚙"),
            (LauncherPage.Downloads, "Downloads", "⬇"),
            (LauncherPage.Diagnostics, "Diagnostics", "✚"),
            (LauncherPage.About, "About", "ⓘ")
        ];

        foreach (var (page, label, glyph) in items)
        {
            bool active = _state.CurrentPage == page;
            var row = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 10,
                Children =
                {
                    new TextBlock
                    {
                        Text = glyph,
                        Width = 18,
                        Foreground = BrushOf(active ? theme.AccentCyan : theme.TextSecondary),
                        FontWeight = FontWeight.Bold
                    },
                    new TextBlock
                    {
                        Text = label,
                        FontSize = 13,
                        FontWeight = active ? FontWeight.Bold : FontWeight.Medium,
                        Foreground = BrushOf(active ? theme.TextPrimary : theme.TextSecondary)
                    }
                }
            };

            var btn = new Button
            {
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Left,
                Background = BrushOf(active ? theme.BgSurface : "#00000000"),
                BorderBrush = BrushOf(active ? theme.AccentPrimary : "#00000000"),
                BorderThickness = new Thickness(active ? 2 : 0, 0, 0, 0),
                CornerRadius = new CornerRadius(7),
                Padding = new Thickness(12, 9),
                Content = row
            };
            var captured = page;
            btn.Click += (_, _) => NavigateTo(captured);
            _navStack.Children.Add(btn);
        }
    }

    // =====================================================================
    // 01. DASHBOARD PAGE
    // =====================================================================
    private Control BuildDashboardPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 18 };
        var spec = _state.SelectedVersionSpec;

        // Hero Banner Card
        var heroGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        var heroLeft = new StackPanel { Spacing = 8 };
        heroLeft.Children.Add(new TextBlock
        {
            Text = "AXSESCLIENT • GENESIS X",
            FontSize = 12,
            FontWeight = FontWeight.Bold,
            Foreground = BrushOf(theme.AccentCyan)
        });
        heroLeft.Children.Add(new TextBlock
        {
            Text = $"Ready to Launch {spec.DisplayTitle}",
            FontSize = 26,
            FontWeight = FontWeight.Bold,
            Foreground = BrushOf(theme.TextPrimary)
        });
        heroLeft.Children.Add(new TextBlock
        {
            Text = $"{spec.Subtitle} • Status: {spec.State.ToDisplayBadge()}",
            FontSize = 13,
            Foreground = BrushOf(theme.TextSecondary)
        });

        var versionSwitchRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 6, 0, 0) };
        foreach (var v in _state.Versions)
        {
            bool sel = v.MinecraftVersion == spec.MinecraftVersion;
            var capturedVer = v.MinecraftVersion;
            versionSwitchRow.Children.Add(sel
                ? CreatePrimaryButton($"✓ {v.MinecraftVersion}", () => _state.SelectVersion(capturedVer))
                : CreateSecondaryButton(v.MinecraftVersion, () => _state.SelectVersion(capturedVer)));
        }
        heroLeft.Children.Add(versionSwitchRow);
        Grid.SetColumn(heroLeft, 0);
        heroGrid.Children.Add(heroLeft);

        var heroRight = new StackPanel
        {
            Spacing = 8,
            VerticalAlignment = VerticalAlignment.Center
        };
        string ctaLabel = spec.State == VersionInstallState.Installed
            ? $"▶ LAUNCH {spec.MinecraftVersion}"
            : $"⬇ INSTALL & PLAY {spec.MinecraftVersion}";
        var launchCta = CreatePrimaryButton(ctaLabel, () => TriggerPlayAsync(spec));
        launchCta.FontSize = 15;
        launchCta.Padding = new Thickness(24, 14);
        heroRight.Children.Add(launchCta);
        heroRight.Children.Add(CreateSecondaryButton("Configure Launch Options →", () => NavigateTo(LauncherPage.Play)));
        Grid.SetColumn(heroRight, 1);
        heroGrid.Children.Add(heroRight);

        root.Children.Add(WrapSurfaceCard(theme, heroGrid, accentBorder: true));

        // 4 Stat Cards
        var statsGrid = new UniformGrid { Columns = 4 };
        statsGrid.Children.Add(BuildStatTile(theme, "MODULES ENABLED", $"{_state.Modules.Count(m => m.Enabled)} / {_state.Modules.Count}", "Synced to ClickGUI (Right Shift)"));
        statsGrid.Children.Add(BuildStatTile(theme, "ACTIVE THEME", theme.Name, "Live AXSES NEO-DARK Engine"));
        statsGrid.Children.Add(BuildStatTile(theme, "MEMORY ALLOCATION", $"{_state.SelectedProfile.RamMb} MB", $"Profile: {_state.SelectedProfile.Name}"));
        statsGrid.Children.Add(BuildStatTile(theme, "SUPPORTED ENGINES", "1.16.5 • 1.21.4 • 1.21.11", "Isolated Per-Version Fabric JARs"));
        root.Children.Add(statsGrid);

        // Quick Actions + Release Highlights
        var lowerGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,*") };
        var notesStack = new StackPanel { Spacing = 6 };
        notesStack.Children.Add(SectionHeader(theme, "GENESIS X v1.0.0 Highlights"));
        notesStack.Children.Add(BodyText(theme, "• Separate native Fabric mods: AXSESCLIENT-1.16.5.jar, AXSESCLIENT-1.21.4.jar, AXSESCLIENT-1.21.11.jar."));
        notesStack.Children.Add(BodyText(theme, "• Press RIGHT SHIFT in-game or on Main Menu to open AXSES PREMIUM CLICKGUI; close with RIGHT SHIFT or ESCAPE."));
        notesStack.Children.Add(BodyText(theme, "• Full HUD Editor with drag-and-drop, snapping, grouping, multi-resolution preview, and 4 layout presets."));
        notesStack.Children.Add(BodyText(theme, "• 100% Fair-Play compliant: zero packet cheats, singleplayer-gated training targets, DPAPI token vault."));
        var leftCard = WrapSurfaceCard(theme, notesStack);
        leftCard.Margin = new Thickness(0, 0, 8, 0);
        Grid.SetColumn(leftCard, 0);
        lowerGrid.Children.Add(leftCard);

        var quickStack = new StackPanel { Spacing = 8 };
        quickStack.Children.Add(SectionHeader(theme, "Quick Navigation"));
        quickStack.Children.Add(CreateSecondaryButton("❖ Open Version Manager (Install / Repair / Update)", () => NavigateTo(LauncherPage.Versions)));
        quickStack.Children.Add(CreateSecondaryButton("✦ Customize 90 Client Modules Before Launch", () => NavigateTo(LauncherPage.Modules)));
        quickStack.Children.Add(CreateSecondaryButton("◐ Switch AXSES NEO-DARK Theme Palette", () => NavigateTo(LauncherPage.Appearance)));
        quickStack.Children.Add(CreateSecondaryButton("✚ Run System & Java Diagnostics", () => NavigateTo(LauncherPage.Diagnostics)));
        var rightCard = WrapSurfaceCard(theme, quickStack);
        rightCard.Margin = new Thickness(8, 0, 0, 0);
        Grid.SetColumn(rightCard, 1);
        lowerGrid.Children.Add(rightCard);

        root.Children.Add(lowerGrid);
        return root;
    }

    // =====================================================================
    // 02. PLAY PAGE
    // =====================================================================
    private Control BuildPlayPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 16 };
        root.Children.Add(PageHeader(theme, "Launch Deck", "Direct 13-stage JVM launcher — launches Minecraft + Fabric + AXSESCLIENT directly without external launchers."));

        var profile = _state.SelectedProfile;
        var spec = _state.SelectedVersionSpec;

        var configStack = new StackPanel { Spacing = 12 };

        // Version & Profile Row
        var verRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        verRow.Children.Add(LabelText(theme, "Target Version:"));
        foreach (var v in _state.Versions)
        {
            string verId = v.MinecraftVersion;
            verRow.Children.Add(verId == spec.MinecraftVersion
                ? CreatePrimaryButton($"{verId} ({v.State.ToDisplayBadge()})", () => _state.SelectVersion(verId))
                : CreateSecondaryButton($"{verId} ({v.State.ToDisplayBadge()})", () => _state.SelectVersion(verId)));
        }
        configStack.Children.Add(verRow);

        // RAM Slider
        var ramLabel = LabelText(theme, $"Allocated JVM Heap Memory: {profile.RamMb} MB");
        var ramSlider = new Slider
        {
            Minimum = 1024,
            Maximum = 16384,
            TickFrequency = 512,
            IsSnapToTickEnabled = true,
            Value = profile.RamMb,
            Width = 420,
            HorizontalAlignment = HorizontalAlignment.Left
        };
        ramSlider.PropertyChanged += (_, e) =>
        {
            if (e.Property == RangeBase.ValueProperty)
            {
                profile.RamMb = (int)Math.Round(ramSlider.Value);
                ramLabel.Text = $"Allocated JVM Heap Memory: {profile.RamMb} MB";
                _state.SaveProfiles();
            }
        };
        configStack.Children.Add(ramLabel);
        configStack.Children.Add(ramSlider);

        // Resolution presets + Fullscreen
        var resRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        resRow.Children.Add(LabelText(theme, $"Window Size ({profile.ResolutionWidth}x{profile.ResolutionHeight}):"));
        (int W, int H)[] resolutions = [(854, 480), (1280, 720), (1600, 900), (1920, 1080), (2560, 1440)];
        foreach (var (w, h) in resolutions)
        {
            int cw = w, ch = h;
            resRow.Children.Add(profile.ResolutionWidth == cw && profile.ResolutionHeight == ch
                ? CreatePrimaryButton($"{cw}x{ch}", () => { profile.ResolutionWidth = cw; profile.ResolutionHeight = ch; _state.SaveProfiles(); RefreshUi(); })
                : CreateSecondaryButton($"{cw}x{ch}", () => { profile.ResolutionWidth = cw; profile.ResolutionHeight = ch; _state.SaveProfiles(); RefreshUi(); }));
        }
        var fsCheck = new CheckBox
        {
            Content = "Start Fullscreen",
            IsChecked = profile.Fullscreen,
            Foreground = BrushOf(theme.TextPrimary),
            Margin = new Thickness(12, 0, 0, 0)
        };
        fsCheck.IsCheckedChanged += (_, _) =>
        {
            profile.Fullscreen = fsCheck.IsChecked == true;
            _state.SaveProfiles();
        };
        resRow.Children.Add(fsCheck);
        configStack.Children.Add(resRow);

        // JVM Flags
        configStack.Children.Add(LabelText(theme, "Custom JVM Arguments:"));
        var jvmBox = new TextBox
        {
            Text = profile.CustomJvmArgs,
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        jvmBox.LostFocus += (_, _) =>
        {
            profile.CustomJvmArgs = jvmBox.Text ?? "";
            _state.SaveProfiles();
        };
        configStack.Children.Add(jvmBox);

        // Launch Action Row
        var actionRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, Margin = new Thickness(0, 6, 0, 0) };
        var playBtn = CreatePrimaryButton($"▶ PLAY MINECRAFT {spec.MinecraftVersion}", () => TriggerPlayAsync(spec));
        playBtn.Padding = new Thickness(24, 12);
        actionRow.Children.Add(playBtn);
        actionRow.Children.Add(CreateSecondaryButton("Open Instance Folder", () =>
            _state.OpenDirectoryInExplorer(_state.LaunchEngine.GetInstanceDir(spec.MinecraftVersion))));
        configStack.Children.Add(actionRow);

        root.Children.Add(WrapSurfaceCard(theme, configStack, accentBorder: true));

        // Live Game & Engine Console
        var logCard = new StackPanel { Spacing = 8 };
        logCard.Children.Add(SectionHeader(theme, "Live Launch Engine & Game Console (Tokens Auto-Redacted)"));
        string logText;
        lock (_state.Logs)
        {
            logText = string.Join(Environment.NewLine, _state.Logs.TakeLast(30));
        }
        logCard.Children.Add(new TextBox
        {
            Text = logText,
            IsReadOnly = true,
            AcceptsReturn = true,
            Height = 200,
            FontFamily = new FontFamily("Consolas, Monospace"),
            FontSize = 11.5,
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextSecondary)
        });
        root.Children.Add(WrapSurfaceCard(theme, logCard));

        return root;
    }

    // =====================================================================
    // 03. VERSIONS PAGE (VERSION MANAGER)
    // =====================================================================
    private Control BuildVersionsPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 16 };
        var topRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        var hdr = PageHeader(theme, "Version Manager", "Independent isolated installations for Minecraft 1.16.5 (Java 8), 1.21.4 (Java 21), and 1.21.11 (Java 21).");
        Grid.SetColumn(hdr, 0);
        topRow.Children.Add(hdr);
        var refreshBtn = CreateSecondaryButton("↻ Re-Verify All Installations", () => _state.RefreshAllVersions());
        refreshBtn.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(refreshBtn, 1);
        topRow.Children.Add(refreshBtn);
        root.Children.Add(topRow);

        foreach (var spec in _state.Versions)
        {
            var cardGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var left = new StackPanel { Spacing = 6 };

            var titleLine = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
            titleLine.Children.Add(new TextBlock
            {
                Text = spec.DisplayTitle,
                FontSize = 18,
                FontWeight = FontWeight.Bold,
                Foreground = BrushOf(theme.TextPrimary)
            });
            titleLine.Children.Add(BuildStateBadge(theme, spec.State));
            left.Children.Add(titleLine);

            left.Children.Add(BodyText(theme, $"{spec.Subtitle} • Mod Artifact: {spec.ModJarFileName}"));
            left.Children.Add(BodyText(theme, spec.StatusDetail));

            if (spec.State == VersionInstallState.Installing || spec.Progress > 0 && spec.Progress < 1.0)
            {
                left.Children.Add(new ProgressBar
                {
                    Minimum = 0,
                    Maximum = 1,
                    Value = spec.Progress,
                    Height = 6,
                    Foreground = BrushOf(theme.AccentCyan),
                    Width = 420,
                    HorizontalAlignment = HorizontalAlignment.Left
                });
            }

            Grid.SetColumn(left, 0);
            cardGrid.Children.Add(left);

            // All 5 Required Version Actions: INSTALL, PLAY, UPDATE, REPAIR, OPEN FOLDER
            var captured = spec;
            var actionsWrap = new WrapPanel
            {
                Orientation = Orientation.Horizontal,
                VerticalAlignment = VerticalAlignment.Center
            };
            actionsWrap.Children.Add(WrapMargin(CreatePrimaryButton("PLAY", () => TriggerPlayAsync(captured))));
            actionsWrap.Children.Add(WrapMargin(CreateSecondaryButton("INSTALL", () => TriggerInstallOrRepairAsync(captured))));
            actionsWrap.Children.Add(WrapMargin(CreateSecondaryButton("UPDATE", () =>
            {
                _state.LaunchEngine.UpdateModJarOnly(captured, _state.AppendLog);
                _state.StatusBanner = $"Updated {captured.ModJarFileName} in {captured.MinecraftVersion} instance.";
                RefreshUi();
            })));
            actionsWrap.Children.Add(WrapMargin(CreateSecondaryButton("REPAIR", () => TriggerInstallOrRepairAsync(captured))));
            actionsWrap.Children.Add(WrapMargin(CreateSecondaryButton("OPEN FOLDER", () =>
                _state.OpenDirectoryInExplorer(_state.LaunchEngine.GetInstanceDir(captured.MinecraftVersion)))));

            Grid.SetColumn(actionsWrap, 1);
            cardGrid.Children.Add(actionsWrap);

            root.Children.Add(WrapSurfaceCard(theme, cardGrid, accentBorder: spec.MinecraftVersion == _state.SelectedVersionSpec.MinecraftVersion));
        }

        return root;
    }

    // =====================================================================
    // 04. ACCOUNTS PAGE
    // =====================================================================
    private Control BuildAccountsPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 16 };
        root.Children.Add(PageHeader(theme, "Account Vault", "Official browser-based Microsoft OAuth2 + PKCE authentication with Windows DPAPI encryption & Java Edition ownership verification."));

        // Microsoft OAuth Card
        var oauthStack = new StackPanel { Spacing = 10 };
        oauthStack.Children.Add(SectionHeader(theme, "Official Microsoft Sign-In (Recommended for Online & Singleplayer)"));
        oauthStack.Children.Add(BodyText(theme, "AXSESCLIENT never asks for your Microsoft password inside the launcher. Clicking Sign In opens login.microsoftonline.com in your default browser and verifies Minecraft: Java Edition ownership."));

        var oauthBtns = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        oauthBtns.Children.Add(CreatePrimaryButton("🔐 Sign in with Microsoft (Browser OAuth2)", TriggerBrowserOAuthAsync));
        if (_authCts is not null)
        {
            oauthBtns.Children.Add(CreateSecondaryButton("Cancel Pending Browser Login", () =>
            {
                _authCts?.Cancel();
                _authCts = null;
                _state.StatusBanner = "Browser OAuth sign-in cancelled.";
                RefreshUi();
            }));
        }
        oauthStack.Children.Add(oauthBtns);
        root.Children.Add(WrapSurfaceCard(theme, oauthStack, accentBorder: true));

        // Local Singleplayer Test Sandbox Card
        var sandboxStack = new StackPanel { Spacing = 8 };
        sandboxStack.Children.Add(SectionHeader(theme, "Local Singleplayer Test Sandbox Profile"));
        sandboxStack.Children.Add(BodyText(theme, "For offline local singleplayer & HUD/ClickGUI testing only. Does not bypass online server authentication or Java Edition ownership checks."));
        var nameInput = new TextBox
        {
            Watermark = "Sandbox Player Name (3-16 chars, e.g. AxsesTester)",
            Text = "AxsesTester",
            Width = 300,
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        var sbRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        sbRow.Children.Add(nameInput);
        sbRow.Children.Add(CreateSecondaryButton("+ Add Local Test Profile", () =>
        {
            var rec = _state.AuthService.CreateLocalSandboxAccount(nameInput.Text ?? "AxsesTester");
            _state.Settings.SelectedAccountUuid = rec.Uuid;
            _state.SaveSettings();
            _state.StatusBanner = $"Selected local singleplayer test profile '{rec.Username}'.";
            RefreshUi();
        }));
        sandboxStack.Children.Add(sbRow);
        root.Children.Add(WrapSurfaceCard(theme, sandboxStack));

        // Saved Multi-Account List
        var listStack = new StackPanel { Spacing = 10 };
        listStack.Children.Add(SectionHeader(theme, $"Saved Accounts ({_state.AuthService.Accounts.Count})"));
        if (_state.AuthService.Accounts.Count == 0)
        {
            listStack.Children.Add(BodyText(theme, "No accounts added yet. Sign in with Microsoft above to play online, or create a Local Singleplayer Test profile."));
        }
        else
        {
            foreach (var acc in _state.AuthService.Accounts)
            {
                bool isSel = _state.SelectedAccount?.Uuid == acc.Uuid;
                var row = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
                var info = new StackPanel { Spacing = 2 };
                info.Children.Add(new TextBlock
                {
                    Text = $"{acc.Username} {(isSel ? "• [ACTIVE]" : "")}",
                    FontWeight = FontWeight.Bold,
                    FontSize = 14,
                    Foreground = BrushOf(isSel ? theme.AccentCyan : theme.TextPrimary)
                });
                info.Children.Add(BodyText(theme,
                    $"Type: {acc.AccountType} • Java Ownership: {(acc.OwnsMinecraftJava ? "VERIFIED" : "Unverified (Singleplayer Sandbox Only)")} • UUID: {acc.Uuid}"));
                Grid.SetColumn(info, 0);
                row.Children.Add(info);

                var capturedAcc = acc;
                var btns = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
                if (!isSel)
                {
                    btns.Children.Add(CreatePrimaryButton("Switch Active", () =>
                    {
                        _state.Settings.SelectedAccountUuid = capturedAcc.Uuid;
                        _state.SaveSettings();
                        RefreshUi();
                    }));
                }
                if (acc.AccountType == "Microsoft")
                {
                    btns.Children.Add(CreateSecondaryButton("Refresh Session", async () =>
                    {
                        try
                        {
                            await _state.AuthService.RefreshSessionAsync(capturedAcc, _state.AppendLog);
                            _state.StatusBanner = $"Refreshed session for {capturedAcc.Username}.";
                        }
                        catch (Exception ex)
                        {
                            _state.StatusBanner = ex.Message;
                        }
                        RefreshUi();
                    }));
                }
                btns.Children.Add(CreateSecondaryButton("Logout / Remove", () =>
                {
                    _state.AuthService.RemoveAccount(capturedAcc.Uuid);
                    _state.StatusBanner = $"Removed account {capturedAcc.Username}.";
                    RefreshUi();
                }));
                Grid.SetColumn(btns, 1);
                row.Children.Add(btns);
                listStack.Children.Add(WrapSurfaceCard(theme, row, accentBorder: isSel));
            }
        }
        root.Children.Add(listStack);
        return root;
    }

    // =====================================================================
    // 05. PROFILES PAGE
    // =====================================================================
    private Control BuildProfilesPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 16 };
        root.Children.Add(PageHeader(theme, "Launch Profiles", "Create and switch between custom RAM, JVM flag, and version presets."));

        var createRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        var nameBox = new TextBox
        {
            Watermark = "New Profile Name (e.g. PvP Practice)",
            Width = 240,
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        createRow.Children.Add(nameBox);
        foreach (var v in _state.Versions)
        {
            string ver = v.MinecraftVersion;
            createRow.Children.Add(CreatePrimaryButton($"+ Create {ver} Profile", () =>
            {
                _state.CreateProfile(string.IsNullOrWhiteSpace(nameBox.Text) ? $"Custom {ver}" : nameBox.Text, ver, 4096);
                RefreshUi();
            }));
        }
        root.Children.Add(WrapSurfaceCard(theme, createRow));

        foreach (var p in _state.Profiles)
        {
            bool active = p.Id == _state.SelectedProfile.Id;
            var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var info = new StackPanel { Spacing = 4 };
            info.Children.Add(new TextBlock
            {
                Text = $"{p.Name} {(active ? "• [ACTIVE]" : "")}",
                FontSize = 15,
                FontWeight = FontWeight.Bold,
                Foreground = BrushOf(active ? theme.AccentCyan : theme.TextPrimary)
            });
            info.Children.Add(BodyText(theme, $"Minecraft {p.MinecraftVersion} • {p.RamMb} MB Heap • {p.ResolutionWidth}x{p.ResolutionHeight}"));
            Grid.SetColumn(info, 0);
            grid.Children.Add(info);

            var captured = p;
            var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            if (!active)
            {
                actions.Children.Add(CreatePrimaryButton("Activate", () =>
                {
                    _state.Settings.SelectedProfileId = captured.Id;
                    _state.Settings.SelectedVersion = captured.MinecraftVersion;
                    _state.SaveSettings();
                    RefreshUi();
                }));
            }
            if (_state.Profiles.Count > 1)
            {
                actions.Children.Add(CreateSecondaryButton("Delete", () => _state.DeleteProfile(captured.Id)));
            }
            Grid.SetColumn(actions, 1);
            grid.Children.Add(actions);

            root.Children.Add(WrapSurfaceCard(theme, grid, accentBorder: active));
        }

        return root;
    }

    // =====================================================================
    // 06. MODULES PAGE (ALL 90 MODULES SYNCED TO CLIENT)
    // =====================================================================
    private Control BuildModulesPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 14 };
        root.Children.Add(PageHeader(theme, $"AXSES Module Pre-Configurator ({_state.Modules.Count} Modules)",
            $"Changes sync directly to {_state.SelectedVersionSpec.DisplayTitle} (axsesclient/configs/default.json). Press RIGHT SHIFT in-game for full per-setting sliders & color pickers."));

        // Search + Category Filter Bar
        var filterStack = new StackPanel { Spacing = 10 };
        var searchBox = new TextBox
        {
            Text = _moduleSearchQuery,
            Watermark = "Search all 90 modules by name or description...",
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        searchBox.KeyUp += (_, _) =>
        {
            _moduleSearchQuery = searchBox.Text ?? "";
            RefreshUi();
        };
        filterStack.Children.Add(searchBox);

        string[] cats = ["All", "Favorites", "Combat Training", "Movement", "Render", "Player", "World", "Utility", "HUD", "Settings"];
        var catWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        foreach (string c in cats)
        {
            string capturedCat = c;
            catWrap.Children.Add(WrapMargin(_selectedModuleCategory == capturedCat
                ? CreatePrimaryButton(capturedCat, () => { _selectedModuleCategory = capturedCat; RefreshUi(); })
                : CreateSecondaryButton(capturedCat, () => { _selectedModuleCategory = capturedCat; RefreshUi(); })));
        }
        filterStack.Children.Add(catWrap);
        root.Children.Add(WrapSurfaceCard(theme, filterStack));

        // Filtered Module Grid
        var filtered = _state.Modules.Where(m =>
        {
            if (_selectedModuleCategory == "Favorites" && !m.Favorite) return false;
            if (_selectedModuleCategory != "All" && _selectedModuleCategory != "Favorites" && m.Category != _selectedModuleCategory) return false;
            if (!string.IsNullOrWhiteSpace(_moduleSearchQuery))
            {
                return m.Name.Contains(_moduleSearchQuery, StringComparison.OrdinalIgnoreCase)
                       || m.Description.Contains(_moduleSearchQuery, StringComparison.OrdinalIgnoreCase);
            }
            return true;
        }).ToList();

        var grid = new UniformGrid { Columns = 2 };
        foreach (var mod in filtered)
        {
            var capturedMod = mod;
            var cardRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto") };
            var txt = new StackPanel { Spacing = 3, Margin = new Thickness(0, 0, 10, 0) };
            txt.Children.Add(new TextBlock
            {
                Text = $"{mod.Name}  [{mod.Category}]",
                FontWeight = FontWeight.Bold,
                FontSize = 13.5,
                Foreground = BrushOf(mod.Enabled ? theme.TextPrimary : theme.TextSecondary)
            });
            txt.Children.Add(new TextBlock
            {
                Text = mod.Description,
                FontSize = 11.5,
                TextWrapping = TextWrapping.Wrap,
                Foreground = BrushOf(theme.TextSecondary)
            });
            Grid.SetColumn(txt, 0);
            cardRow.Children.Add(txt);

            var favBtn = CreateSecondaryButton(mod.Favorite ? "★" : "☆", () =>
            {
                capturedMod.Favorite = !capturedMod.Favorite;
                _state.SyncModulesAndThemeToSelectedInstance();
                RefreshUi();
            });
            favBtn.Margin = new Thickness(0, 0, 6, 0);
            Grid.SetColumn(favBtn, 1);
            cardRow.Children.Add(favBtn);

            var toggleBtn = mod.Enabled
                ? CreatePrimaryButton("ENABLED", () =>
                {
                    capturedMod.Enabled = false;
                    _state.SyncModulesAndThemeToSelectedInstance();
                    RefreshUi();
                })
                : CreateSecondaryButton("OFF", () =>
                {
                    capturedMod.Enabled = true;
                    _state.SyncModulesAndThemeToSelectedInstance();
                    RefreshUi();
                });
            Grid.SetColumn(toggleBtn, 2);
            cardRow.Children.Add(toggleBtn);

            var card = WrapSurfaceCard(theme, cardRow, accentBorder: mod.Enabled);
            card.Margin = new Thickness(4);
            grid.Children.Add(card);
        }
        root.Children.Add(grid);
        return root;
    }

    // =====================================================================
    // 07. APPEARANCE PAGE (8 OFFICIAL AXSES THEMES)
    // =====================================================================
    private Control BuildAppearancePage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 16 };
        root.Children.Add(PageHeader(theme, "AXSES NEO-DARK Theme Engine", "8 curated precision dark themes synchronized seamlessly across AXSESCLIENT.exe and all 3 Minecraft versions."));

        var grid = new UniformGrid { Columns = 2 };
        foreach (var palette in _state.Themes)
        {
            bool active = palette.Id == theme.Id;
            var stack = new StackPanel { Spacing = 10 };
            stack.Children.Add(new TextBlock
            {
                Text = $"{palette.Name} {(active ? "• ACTIVE" : "")}",
                FontSize = 16,
                FontWeight = FontWeight.Bold,
                Foreground = BrushOf(active ? palette.AccentCyan : theme.TextPrimary)
            });

            var swatches = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            foreach (string hex in new[] { palette.BgPrimary, palette.BgSurface, palette.AccentPrimary, palette.AccentSecondary, palette.AccentCyan, palette.Success })
            {
                swatches.Children.Add(new Border
                {
                    Width = 32,
                    Height = 22,
                    CornerRadius = new CornerRadius(5),
                    Background = BrushOf(hex),
                    BorderBrush = BrushOf("#44FFFFFF"),
                    BorderThickness = new Thickness(1)
                });
            }
            stack.Children.Add(swatches);

            string capturedId = palette.Id;
            stack.Children.Add(active
                ? CreatePrimaryButton("✓ Active Theme", () => { })
                : CreateSecondaryButton($"Apply {palette.Name}", () => _state.SetActiveTheme(capturedId)));

            var card = WrapSurfaceCard(theme, stack, accentBorder: active);
            card.Margin = new Thickness(6);
            grid.Children.Add(card);
        }
        root.Children.Add(grid);
        return root;
    }

    // =====================================================================
    // 08. SETTINGS PAGE
    // =====================================================================
    private Control BuildSettingsPage(LauncherThemePalette theme)
    {
        var s = _state.Settings;
        var root = new StackPanel { Spacing = 14 };
        root.Children.Add(PageHeader(theme, "Launcher & Runtime Settings", "Configure isolated Java runtimes, download concurrency, accessibility, and localization."));

        var form = new StackPanel { Spacing = 12 };
        form.Children.Add(LabelText(theme, $"AXSESCLIENT Data Directory: {s.RootDataDir}"));
        form.Children.Add(CreateSecondaryButton("Open Data Directory in File Explorer", () => _state.OpenDirectoryInExplorer(s.RootDataDir)));

        var j8Box = new TextBox
        {
            Text = s.Java8Path,
            Watermark = "Auto-detect or auto-download Eclipse Temurin Java 8 (for Minecraft 1.16.5)",
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        form.Children.Add(LabelText(theme, "Java 8 Executable Override (1.16.5):"));
        form.Children.Add(j8Box);

        var j21Box = new TextBox
        {
            Text = s.Java21Path,
            Watermark = "Auto-detect or auto-download Eclipse Temurin Java 21 (for Minecraft 1.21.4 & 1.21.11)",
            Background = BrushOf(theme.BgPrimary),
            Foreground = BrushOf(theme.TextPrimary)
        };
        form.Children.Add(LabelText(theme, "Java 21 Executable Override (1.21.4 & 1.21.11):"));
        form.Children.Add(j21Box);

        var threadsLabel = LabelText(theme, $"Parallel Download Streams: {s.ConcurrentDownloads}");
        var threadsSlider = new Slider
        {
            Minimum = 2,
            Maximum = 16,
            Value = s.ConcurrentDownloads,
            Width = 320,
            HorizontalAlignment = HorizontalAlignment.Left
        };
        threadsSlider.PropertyChanged += (_, e) =>
        {
            if (e.Property == RangeBase.ValueProperty)
            {
                s.ConcurrentDownloads = (int)Math.Round(threadsSlider.Value);
                threadsLabel.Text = $"Parallel Download Streams: {s.ConcurrentDownloads}";
            }
        };
        form.Children.Add(threadsLabel);
        form.Children.Add(threadsSlider);

        var rmCheck = new CheckBox
        {
            Content = "Reduced Motion Mode (instant transitions in Launcher & ClickGUI)",
            IsChecked = s.ReducedMotion,
            Foreground = BrushOf(theme.TextPrimary)
        };
        form.Children.Add(rmCheck);

        var langRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        langRow.Children.Add(LabelText(theme, "Client & HUD Language:"));
        langRow.Children.Add(s.Language == "en_US"
            ? CreatePrimaryButton("English (en_US)", () => { s.Language = "en_US"; _state.SaveSettings(); _state.SyncModulesAndThemeToSelectedInstance(); RefreshUi(); })
            : CreateSecondaryButton("English (en_US)", () => { s.Language = "en_US"; _state.SaveSettings(); _state.SyncModulesAndThemeToSelectedInstance(); RefreshUi(); }));
        langRow.Children.Add(s.Language == "ru_RU"
            ? CreatePrimaryButton("Русский (ru_RU)", () => { s.Language = "ru_RU"; _state.SaveSettings(); _state.SyncModulesAndThemeToSelectedInstance(); RefreshUi(); })
            : CreateSecondaryButton("Русский (ru_RU)", () => { s.Language = "ru_RU"; _state.SaveSettings(); _state.SyncModulesAndThemeToSelectedInstance(); RefreshUi(); }));
        form.Children.Add(langRow);

        form.Children.Add(CreatePrimaryButton("💾 Save Settings", () =>
        {
            s.Java8Path = j8Box.Text?.Trim() ?? "";
            s.Java21Path = j21Box.Text?.Trim() ?? "";
            s.ReducedMotion = rmCheck.IsChecked == true;
            _state.SaveSettings();
            _state.SyncModulesAndThemeToSelectedInstance();
            _state.StatusBanner = "Settings saved and synced to client instance.";
            RefreshUi();
        }));

        root.Children.Add(WrapSurfaceCard(theme, form));
        return root;
    }

    // =====================================================================
    // 09. DOWNLOADS PAGE
    // =====================================================================
    private Control BuildDownloadsPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 14 };
        var top = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        var dlHdr = PageHeader(theme, "Download & Integrity Queue", "Every Mojang JAR, Maven library, and asset object is verified against official SHA-1/SHA-256 digests.");
        Grid.SetColumn(dlHdr, 0);
        top.Children.Add(dlHdr);
        var clearBtn = CreateSecondaryButton("Clear Completed", () =>
        {
            lock (_state.DownloadTasks) { _state.DownloadTasks.RemoveAll(t => t.Status == "Verified"); }
            RefreshUi();
        });
        clearBtn.VerticalAlignment = VerticalAlignment.Center;
        Grid.SetColumn(clearBtn, 1);
        top.Children.Add(clearBtn);
        root.Children.Add(top);

        List<DownloadTaskItem> snapshot;
        lock (_state.DownloadTasks) { snapshot = _state.DownloadTasks.Take(40).ToList(); }

        if (snapshot.Count == 0)
        {
            root.Children.Add(WrapSurfaceCard(theme, BodyText(theme, "No active downloads. Install or launch a Minecraft version from Play or Versions to monitor live verified transfers.")));
        }
        else
        {
            foreach (var d in snapshot)
            {
                var row = new Grid { ColumnDefinitions = new ColumnDefinitions("*,160,120") };
                row.Children.Add(new TextBlock
                {
                    Text = $"[{d.Stage}] {d.FileName}",
                    Foreground = BrushOf(theme.TextPrimary),
                    FontSize = 12.5
                });
                var pb = new ProgressBar { Minimum = 0, Maximum = 1, Value = d.Progress, Height = 6, Foreground = BrushOf(theme.AccentCyan) };
                Grid.SetColumn(pb, 1);
                row.Children.Add(pb);
                var status = new TextBlock
                {
                    Text = d.ShaVerified ? $"✓ SHA1 ({d.SpeedMbps} MB/s)" : d.Status,
                    Foreground = BrushOf(d.ShaVerified ? theme.Success : theme.Warning),
                    HorizontalAlignment = HorizontalAlignment.Right,
                    FontSize = 11.5
                };
                Grid.SetColumn(status, 2);
                row.Children.Add(status);
                root.Children.Add(WrapSurfaceCard(theme, row));
            }
        }
        return root;
    }

    // =====================================================================
    // 10. DIAGNOSTICS PAGE
    // =====================================================================
    private Control BuildDiagnosticsPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 14 };
        root.Children.Add(PageHeader(theme, "System & Installation Diagnostics", "Hardware, Java 8/21 runtimes, Mojang/Fabric API reachability, and SHA-256 client artifact checks."));

        var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        actions.Children.Add(CreatePrimaryButton("✚ Run Full Diagnostic Scan", async () => await _state.RunDiagnosticsAsync()));
        actions.Children.Add(CreateSecondaryButton("📋 Copy Scrubbed Diagnostic Report", async () =>
        {
            List<string> logsSnap;
            lock (_state.Logs) { logsSnap = _state.Logs.ToList(); }
            string report = DiagnosticsService.FormatReport(_state.LastDiagnostics, logsSnap);
            if (Clipboard is not null)
                await Clipboard.SetTextAsync(report);
            _state.StatusBanner = "Copied token-scrubbed diagnostic report to clipboard.";
            RefreshUi();
        }));
        root.Children.Add(actions);

        if (_state.LastDiagnostics.Count == 0)
        {
            root.Children.Add(WrapSurfaceCard(theme, BodyText(theme, "Click 'Run Full Diagnostic Scan' above to inspect Java runtimes, network endpoints, and client JARs.")));
        }
        else
        {
            foreach (var item in _state.LastDiagnostics)
            {
                var row = new Grid { ColumnDefinitions = new ColumnDefinitions("90,260,*") };
                row.Children.Add(new TextBlock
                {
                    Text = item.Passed ? "● PASS" : "▲ INFO",
                    FontWeight = FontWeight.Bold,
                    Foreground = BrushOf(item.Passed ? theme.Success : theme.Warning)
                });
                var nameTb = new TextBlock { Text = item.Name, FontWeight = FontWeight.SemiBold, Foreground = BrushOf(theme.TextPrimary) };
                Grid.SetColumn(nameTb, 1);
                row.Children.Add(nameTb);
                var detTb = new TextBlock { Text = item.Detail, Foreground = BrushOf(theme.TextSecondary), TextWrapping = TextWrapping.Wrap };
                Grid.SetColumn(detTb, 2);
                row.Children.Add(detTb);
                root.Children.Add(WrapSurfaceCard(theme, row));
            }
        }
        return root;
    }

    // =====================================================================
    // 11. ABOUT PAGE
    // =====================================================================
    private Control BuildAboutPage(LauncherThemePalette theme)
    {
        var root = new StackPanel { Spacing = 14 };
        root.Children.Add(PageHeader(theme, "About AXSESCLIENT", "Version 1.0.0 • Codename: GENESIS X • Built for Windows 10/11 x64"));

        var info = new StackPanel { Spacing = 8 };
        info.Children.Add(SectionHeader(theme, "Architecture & Fair-Play Pledge"));
        info.Children.Add(BodyText(theme, "• Launcher: Native C# / .NET 8 / Avalonia UI 11 Desktop Application with 13-stage direct JVM launch engine."));
        info.Children.Add(BodyText(theme, "• Client Core: Shared AXSES CORE engine + version-isolated Fabric mods for Minecraft 1.16.5, 1.21.4, and 1.21.11."));
        info.Children.Add(BodyText(theme, "• Fair-Play Compliance: Strictly visual, HUD, utility, and singleplayer-gated PvP training tools. Zero anti-cheat bypasses, zero reach/aim/packet cheats, zero client brand spoofing."));
        info.Children.Add(BodyText(theme, "• Security & Privacy: Official browser-based Microsoft OAuth2 + PKCE, Windows DPAPI encrypted vault, zero telemetry, automatic log token redaction."));

        var updBtn = CreatePrimaryButton("🔄 Check for AXSESCLIENT Updates", async () =>
        {
            _state.StatusBanner = "Checking update manifest...";
            RefreshUi();
            var res = await _state.UpdaterService.CheckForUpdatesAsync("1.0.0", _state.Settings.UpdateManifestUrl);
            _state.StatusBanner = res.StatusMessage;
            _state.AppendLog($"[Updater] {res.StatusMessage}");
            RefreshUi();
        });
        updBtn.Margin = new Thickness(0, 8, 0, 0);
        info.Children.Add(updBtn);

        root.Children.Add(WrapSurfaceCard(theme, info, accentBorder: true));
        return root;
    }

    // =====================================================================
    // ASYNC OPERATIONS (PLAY, INSTALL, OAUTH)
    // =====================================================================
    private async void TriggerInstallOrRepairAsync(SupportedVersionSpec spec)
    {
        if (_state.IsBusy) return;
        _state.IsBusy = true;
        try
        {
            await _state.LaunchEngine.InstallOrRepairVersionAsync(
                spec,
                _state.Settings,
                stage => Dispatcher.UIThread.Post(() =>
                {
                    _state.StatusBanner = $"[{spec.MinecraftVersion}] Stage {stage.StageNumber}/{stage.TotalStages}: {stage.StageName}";
                    _state.ActiveOperationProgress = stage.OverallProgress;
                    RefreshUi();
                }),
                dl => Dispatcher.UIThread.Post(() => _state.TrackDownloadTask(dl)),
                log => _state.AppendLog(log));

            _state.StatusBanner = $"Minecraft {spec.MinecraftVersion} + {spec.ModJarFileName} installed and SHA-verified.";
            _state.ActiveOperationProgress = 1.0;
        }
        catch (Exception ex)
        {
            _state.StatusBanner = $"Install error ({spec.MinecraftVersion}): {ex.Message}";
        }
        finally
        {
            _state.IsBusy = false;
            RefreshUi();
        }
    }

    private async void TriggerPlayAsync(SupportedVersionSpec spec)
    {
        if (_state.IsBusy) return;
        var account = _state.SelectedAccount;
        if (account is null)
        {
            _state.StatusBanner = "Select or sign in to an account first (opening Accounts page)...";
            NavigateTo(LauncherPage.Accounts);
            return;
        }

        _state.IsBusy = true;
        try
        {
            _state.SelectVersion(spec.MinecraftVersion);
            await _state.LaunchEngine.LaunchMinecraftAsync(
                spec,
                _state.SelectedProfile,
                account,
                _state.Settings,
                _state.Modules,
                stage => Dispatcher.UIThread.Post(() =>
                {
                    _state.StatusBanner = $"[{spec.MinecraftVersion}] {stage.StageName}";
                    _state.ActiveOperationProgress = stage.OverallProgress;
                    RefreshUi();
                }),
                log =>
                {
                    _state.AppendLog(log);
                    if (_state.CurrentPage == LauncherPage.Play)
                        Dispatcher.UIThread.Post(RefreshUi);
                },
                exitCode => Dispatcher.UIThread.Post(() =>
                {
                    _state.StatusBanner = $"Minecraft {spec.MinecraftVersion} session ended (exit code {exitCode}).";
                    _state.ActiveOperationProgress = 0;
                    RefreshUi();
                }));

            _state.StatusBanner = $"Minecraft {spec.MinecraftVersion} is RUNNING.";
        }
        catch (Exception ex)
        {
            _state.StatusBanner = $"Launch aborted: {ex.Message}";
        }
        finally
        {
            _state.IsBusy = false;
            RefreshUi();
        }
    }

    private async void TriggerBrowserOAuthAsync()
    {
        _authCts?.Cancel();
        _authCts = new CancellationTokenSource(TimeSpan.FromMinutes(4));
        RefreshUi();
        try
        {
            var account = await _state.AuthService.SignInWithBrowserAsync(
                msg => Dispatcher.UIThread.Post(() =>
                {
                    _state.StatusBanner = msg;
                    _state.AppendLog($"[OAuth] {msg}");
                    RefreshUi();
                }),
                _authCts.Token);
            _state.Settings.SelectedAccountUuid = account.Uuid;
            _state.SaveSettings();
            _state.StatusBanner = $"Authenticated as {account.Username} (Java Edition Verified).";
        }
        catch (Exception ex)
        {
            _state.StatusBanner = $"Microsoft Sign-In not completed: {ex.Message}";
            _state.AppendLog($"[OAuth] {ex.Message}");
        }
        finally
        {
            _authCts = null;
            RefreshUi();
        }
    }

    // =====================================================================
    // STYLED WIDGET HELPERS
    // =====================================================================
    private Border BuildStatTile(LauncherThemePalette theme, string label, string value, string sub)
    {
        var st = new StackPanel { Spacing = 4 };
        st.Children.Add(new TextBlock { Text = label, FontSize = 10.5, FontWeight = FontWeight.Bold, Foreground = BrushOf(theme.AccentCyan) });
        st.Children.Add(new TextBlock { Text = value, FontSize = 16, FontWeight = FontWeight.Bold, Foreground = BrushOf(theme.TextPrimary) });
        st.Children.Add(new TextBlock { Text = sub, FontSize = 11, Foreground = BrushOf(theme.TextSecondary) });
        var b = WrapSurfaceCard(theme, st);
        b.Margin = new Thickness(4);
        return b;
    }

    private static Border BuildStateBadge(LauncherThemePalette theme, VersionInstallState state)
    {
        string color = state switch
        {
            VersionInstallState.Installed or VersionInstallState.Running => theme.Success,
            VersionInstallState.Installing or VersionInstallState.Launching or VersionInstallState.UpdateAvailable => theme.AccentCyan,
            VersionInstallState.RepairRequired => theme.Danger,
            _ => theme.Warning
        };
        return new Border
        {
            Background = BrushOf("#22" + color.TrimStart('#')),
            BorderBrush = BrushOf(color),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(5),
            Padding = new Thickness(8, 2),
            VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock
            {
                Text = state.ToDisplayBadge(),
                FontSize = 10.5,
                FontWeight = FontWeight.Bold,
                Foreground = BrushOf(color)
            }
        };
    }

    private static Border WrapSurfaceCard(LauncherThemePalette theme, Control child, bool accentBorder = false) =>
        new()
        {
            Background = BrushOf(theme.BgSurface),
            BorderBrush = BrushOf(accentBorder ? theme.AccentPrimary : "#24FFFFFF"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(16, 14),
            Child = child
        };

    private static StackPanel PageHeader(LauncherThemePalette theme, string title, string subtitle) =>
        new()
        {
            Spacing = 4,
            Children =
            {
                new TextBlock { Text = title, FontSize = 22, FontWeight = FontWeight.Bold, Foreground = BrushOf(theme.TextPrimary) },
                new TextBlock { Text = subtitle, FontSize = 12.5, Foreground = BrushOf(theme.TextSecondary), TextWrapping = TextWrapping.Wrap }
            }
        };

    private static TextBlock SectionHeader(LauncherThemePalette theme, string text) =>
        new() { Text = text, FontSize = 15, FontWeight = FontWeight.Bold, Foreground = BrushOf(theme.TextPrimary) };

    private static TextBlock LabelText(LauncherThemePalette theme, string text) =>
        new() { Text = text, FontSize = 12.5, FontWeight = FontWeight.SemiBold, Foreground = BrushOf(theme.TextPrimary), VerticalAlignment = VerticalAlignment.Center };

    private static TextBlock BodyText(LauncherThemePalette theme, string text) =>
        new() { Text = text, FontSize = 12.5, Foreground = BrushOf(theme.TextSecondary), TextWrapping = TextWrapping.Wrap };

    private Button CreatePrimaryButton(string label, Action onClick)
    {
        var theme = _state.ActiveTheme;
        var btn = new Button
        {
            Content = label,
            Background = BrushOf(theme.AccentPrimary),
            Foreground = Brushes.White,
            FontWeight = FontWeight.Bold,
            FontSize = 12.5,
            CornerRadius = new CornerRadius(7),
            Padding = new Thickness(14, 8),
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        btn.Click += (_, _) => onClick();
        return btn;
    }

    private Button CreateSecondaryButton(string label, Action onClick)
    {
        var theme = _state.ActiveTheme;
        var btn = new Button
        {
            Content = label,
            Background = BrushOf(theme.BgSecondary),
            BorderBrush = BrushOf("#33FFFFFF"),
            BorderThickness = new Thickness(1),
            Foreground = BrushOf(theme.TextPrimary),
            FontWeight = FontWeight.SemiBold,
            FontSize = 12,
            CornerRadius = new CornerRadius(7),
            Padding = new Thickness(12, 7),
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        btn.Click += (_, _) => onClick();
        return btn;
    }

    private Button CreateChromeButton(string glyph, Action onClick, bool isDanger = false)
    {
        var theme = _state.ActiveTheme;
        var btn = new Button
        {
            Content = glyph,
            Width = 30,
            Height = 28,
            Padding = new Thickness(0),
            HorizontalContentAlignment = HorizontalAlignment.Center,
            VerticalContentAlignment = VerticalAlignment.Center,
            Background = BrushOf(isDanger ? "#33FF5B79" : theme.BgSurface),
            Foreground = BrushOf(isDanger ? theme.Danger : theme.TextPrimary),
            CornerRadius = new CornerRadius(6)
        };
        btn.Click += (_, _) => onClick();
        return btn;
    }

    private static Control WrapMargin(Control c)
    {
        c.Margin = new Thickness(3);
        return c;
    }

    private static IBrush BrushOf(string hex) => new SolidColorBrush(Color.Parse(hex));
}
