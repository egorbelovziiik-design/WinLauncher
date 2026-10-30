using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using AxsesClient.Launcher.Models;
using AxsesClient.Launcher.Services;

namespace AxsesClient.Launcher.UI;

public sealed class MainWindow : Window
{
    private readonly LauncherStateController _state;
    private LauncherPage _currentPage = LauncherPage.Dashboard;
    private string _moduleSearch = "";
    private string _moduleCategoryFilter = "ALL";

    private readonly Border _rootShell = new();
    private readonly Border _sidebarBorder = new();
    private readonly Border _topBarBorder = new();
    private readonly Border _footerBarBorder = new();
    private readonly ScrollViewer _pageScroller = new();
    private readonly TextBlock _footerStatusText = new() { FontSize = 12.5, FontWeight = FontWeight.Medium, VerticalAlignment = VerticalAlignment.Center };
    private readonly ProgressBar _footerProgress = new() { Height = 6, Minimum = 0, Maximum = 1, CornerRadius = new CornerRadius(3), VerticalAlignment = VerticalAlignment.Center };

    public MainWindow() : this(new LauncherStateController()) { }

    public MainWindow(LauncherStateController state)
    {
        _state = state;
        Title = "AXSESCLIENT v2.0 • OBSIDIAN AURORA";
        Width = 1220;
        Height = 780;
        MinWidth = 1000;
        MinHeight = 660;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;

        _state.StateChanged += () => Dispatcher.UIThread.Post(RebuildDynamicRegions);
        _state.ProgressUpdated += (txt, prog) => Dispatcher.UIThread.Post(() =>
        {
            _footerStatusText.Text = txt;
            _footerProgress.Value = prog;
        });

        BuildRootShell();
        RebuildDynamicRegions();
    }

    public void NavigateTo(LauncherPage page)
    {
        _currentPage = page;
        RebuildDynamicRegions();
        _pageScroller.Offset = Vector.Zero;
    }

    private void BuildRootShell()
    {
        var outerGrid = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("244,*"),
            RowDefinitions = new RowDefinitions("62,*,46")
        };

        // Sidebar spanning full height on left
        Grid.SetColumn(_sidebarBorder, 0);
        Grid.SetRow(_sidebarBorder, 0);
        Grid.SetRowSpan(_sidebarBorder, 3);
        outerGrid.Children.Add(_sidebarBorder);

        // Top Command Header
        Grid.SetColumn(_topBarBorder, 1);
        Grid.SetRow(_topBarBorder, 0);
        outerGrid.Children.Add(_topBarBorder);

        // Main Scrollable Workspace
        _pageScroller.Padding = new Thickness(26, 20, 26, 24);
        _pageScroller.HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled;
        Grid.SetColumn(_pageScroller, 1);
        Grid.SetRow(_pageScroller, 1);
        outerGrid.Children.Add(_pageScroller);

        // Sleek Live Progress & Telemetry Footer
        var footerGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,280"), VerticalAlignment = VerticalAlignment.Center };
        footerGrid.Children.Add(_footerStatusText);
        Grid.SetColumn(_footerProgress, 1);
        footerGrid.Children.Add(_footerProgress);
        _footerBarBorder.Child = footerGrid;

        Grid.SetColumn(_footerBarBorder, 1);
        Grid.SetRow(_footerBarBorder, 2);
        outerGrid.Children.Add(_footerBarBorder);

        _rootShell.Child = outerGrid;
        Content = _rootShell;
    }

    private void RebuildDynamicRegions()
    {
        var t = _state.ActiveTheme;
        bool ru = _state.IsRu;

        Background = BrushOf(t.BgPrimary);
        _rootShell.Background = BrushOf(t.BgPrimary);

        BuildSidebar(t, ru);
        BuildTopBar(t, ru);
        BuildFooterBar(t);

        _pageScroller.Content = _currentPage switch
        {
            LauncherPage.Dashboard => BuildDashboardPage(t, ru),
            LauncherPage.Play => BuildPlayPage(t, ru),
            LauncherPage.Versions => BuildVersionsPage(t, ru),
            LauncherPage.Accounts => BuildAccountsPage(t, ru),
            LauncherPage.Profiles => BuildProfilesPage(t, ru),
            LauncherPage.Modules => BuildModulesPage(t, ru),
            LauncherPage.Appearance => BuildAppearancePage(t, ru),
            LauncherPage.Settings => BuildSettingsPage(t, ru),
            LauncherPage.Downloads => BuildDownloadsPage(t, ru),
            LauncherPage.Diagnostics => BuildDiagnosticsPage(t, ru),
            LauncherPage.About => BuildAboutPage(t, ru),
            _ => BuildDashboardPage(t, ru)
        };
    }

    private void BuildSidebar(LauncherThemePalette t, bool ru)
    {
        _sidebarBorder.Background = BrushOf(t.BgSecondary);
        _sidebarBorder.BorderBrush = BrushOf("#1EFFFFFF");
        _sidebarBorder.BorderThickness = new Thickness(0, 0, 1, 0);
        _sidebarBorder.Padding = new Thickness(16, 18, 16, 16);

        var dock = new DockPanel { LastChildFill = true };

        // Brand Crest Header
        var brandCard = new Border
        {
            Background = AuroraGradient(t, 0.22),
            BorderBrush = BrushOf(t.AccentPrimary, 0.45),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Padding = new Thickness(14, 12),
            Margin = new Thickness(0, 0, 0, 18)
        };
        var brandRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 11 };
        brandRow.Children.Add(new Border
        {
            Width = 36,
            Height = 36,
            CornerRadius = new CornerRadius(10),
            Background = AuroraGradient(t, 1.0),
            Child = new TextBlock
            {
                Text = "AX",
                Foreground = Brushes.White,
                FontWeight = FontWeight.Black,
                FontSize = 14,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        });
        var brandTextStack = new StackPanel { Spacing = 1, VerticalAlignment = VerticalAlignment.Center };
        brandTextStack.Children.Add(new TextBlock
        {
            Text = "AXSESCLIENT",
            Foreground = BrushOf(t.TextPrimary),
            FontWeight = FontWeight.ExtraBold,
            FontSize = 15
        });
        brandTextStack.Children.Add(new TextBlock
        {
            Text = "v2.0 • OBSIDIAN AURORA",
            Foreground = BrushOf(t.AccentCyan),
            FontWeight = FontWeight.SemiBold,
            FontSize = 10.5
        });
        brandRow.Children.Add(brandTextStack);
        brandCard.Child = brandRow;
        DockPanel.SetDock(brandCard, Dock.Top);
        dock.Children.Add(brandCard);

        // Quick Hardware Status Card at Bottom of Sidebar
        var sysCard = new Border
        {
            Background = BrushOf(t.BgSurface, 0.72),
            BorderBrush = BrushOf("#1AFFFFFF"),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(12, 10),
            Margin = new Thickness(0, 12, 0, 0)
        };
        var sysStack = new StackPanel { Spacing = 5 };
        bool jReady = _state.DetectedJava8 is not null && _state.DetectedJava21 is not null;
        sysStack.Children.Add(new TextBlock
        {
            Text = ru ? $"⚡ ОЗУ игры: {_state.Settings.AllocatedRamMb} МБ" : $"⚡ JVM RAM: {_state.Settings.AllocatedRamMb} MB",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 12,
            FontWeight = FontWeight.SemiBold
        });
        sysStack.Children.Add(new TextBlock
        {
            Text = jReady
                ? (ru ? "✓ Java 8 и Java 21 активны" : "✓ Java 8 & Java 21 Ready")
                : (ru ? "⬇ Авто-установка Java включена" : "⬇ Auto-Java Provisioning Active"),
            Foreground = BrushOf(jReady ? t.Success : t.AccentCyan),
            FontSize = 11.2
        });
        sysCard.Child = sysStack;
        DockPanel.SetDock(sysCard, Dock.Bottom);
        dock.Children.Add(sysCard);

        // Navigation Items
        var navStack = new StackPanel { Spacing = 4 };
        (LauncherPage Page, string Icon, string LabelRu, string LabelEn)[] items =
        [
            (LauncherPage.Dashboard, "◈", "Главная", "Dashboard"),
            (LauncherPage.Play, "▶", "Запуск и ОЗУ", "Play & RAM"),
            (LauncherPage.Modules, "✦", "Модули (93)", "Modules (93)"),
            (LauncherPage.Versions, "❖", "Версии игры", "Versions"),
            (LauncherPage.Accounts, "◉", "Аккаунты", "Accounts"),
            (LauncherPage.Profiles, "▤", "Профили", "Profiles"),
            (LauncherPage.Appearance, "◐", "Темы и стиль", "Appearance"),
            (LauncherPage.Settings, "⚙", "Настройки и Java", "Settings & Java"),
            (LauncherPage.Downloads, "⬇", "Загрузки", "Downloads"),
            (LauncherPage.Diagnostics, " pulse", "Диагностика", "Diagnostics"),
            (LauncherPage.About, "ⓘ", "О клиенте", "About")
        ];

        foreach (var item in items)
        {
            bool active = _currentPage == item.Page;
            string iconText = item.Icon == " pulse" ? " pulse"[0..1] : item.Icon;
            if (item.Page == LauncherPage.Diagnostics) iconText = "⚡";

            var btnRow = new Grid { ColumnDefinitions = new ColumnDefinitions("24,*") };
            btnRow.Children.Add(new TextBlock
            {
                Text = iconText,
                Foreground = BrushOf(active ? t.AccentCyan : t.TextSecondary),
                FontSize = 13.5,
                VerticalAlignment = VerticalAlignment.Center
            });
            var lbl = new TextBlock
            {
                Text = ru ? item.LabelRu : item.LabelEn,
                Foreground = BrushOf(active ? t.TextPrimary : t.TextSecondary),
                FontWeight = active ? FontWeight.Bold : FontWeight.Medium,
                FontSize = 13,
                VerticalAlignment = VerticalAlignment.Center
            };
            Grid.SetColumn(lbl, 1);
            btnRow.Children.Add(lbl);

            var navBtn = new Button
            {
                HorizontalAlignment = HorizontalAlignment.Stretch,
                HorizontalContentAlignment = HorizontalAlignment.Stretch,
                Background = active ? AuroraGradient(t, 0.26) : Brushes.Transparent,
                BorderBrush = active ? BrushOf(t.AccentPrimary, 0.65) : Brushes.Transparent,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(12, 8.5),
                Cursor = new Cursor(StandardCursorType.Hand),
                Content = btnRow
            };
            var targetPage = item.Page;
            navBtn.Click += (_, _) => NavigateTo(targetPage);
            navStack.Children.Add(navBtn);
        }

        dock.Children.Add(new ScrollViewer
        {
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Content = navStack
        });
        _sidebarBorder.Child = dock;
    }

    private void BuildTopBar(LauncherThemePalette t, bool ru)
    {
        _topBarBorder.Background = BrushOf(t.BgSecondary, 0.92);
        _topBarBorder.BorderBrush = BrushOf("#1AFFFFFF");
        _topBarBorder.BorderThickness = new Thickness(0, 0, 0, 1);
        _topBarBorder.Padding = new Thickness(24, 0);

        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };

        var titleStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12, VerticalAlignment = VerticalAlignment.Center };
        titleStack.Children.Add(new TextBlock
        {
            Text = GetPageTitle(_currentPage, ru),
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 18,
            FontWeight = FontWeight.ExtraBold,
            VerticalAlignment = VerticalAlignment.Center
        });
        titleStack.Children.Add(CreatePillBadge(
            $"MC {_state.SelectedVersionSpec.MinecraftVersion} • Fabric {_state.SelectedVersionSpec.FabricLoaderVersion}",
            t.AccentCyan, t.BgSurface));
        grid.Children.Add(titleStack);

        var rightRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, VerticalAlignment = VerticalAlignment.Center };

        // Language Toggle Pill (RU | EN)
        var langBtn = CreateGhostButton(ru ? "🌐 RU" : "🌐 EN", t);
        langBtn.Click += (_, _) => _state.ToggleLanguage();
        rightRow.Children.Add(langBtn);

        // Active Account Pill
        var accBtn = CreateGhostButton($"👤 {_state.SelectedAccount.Username}", t);
        accBtn.Click += (_, _) => NavigateTo(LauncherPage.Accounts);
        rightRow.Children.Add(accBtn);

        // Quick Launch CTA
        var spec = _state.SelectedVersionSpec;
        string ctaLabel = spec.State == VersionInstallState.Installed
            ? (ru ? $"▶ ИГРАТЬ {spec.MinecraftVersion}" : $"▶ PLAY {spec.MinecraftVersion}")
            : (ru ? $"⬇ УСТАНОВИТЬ {spec.MinecraftVersion}" : $"⬇ INSTALL {spec.MinecraftVersion}");
        var quickPlay = CreatePrimaryButton(ctaLabel, t, fontSize: 12.5, padH: 16, padV: 8);
        quickPlay.IsEnabled = !_state.IsBusy && spec.State != VersionInstallState.Running;
        quickPlay.Click += async (_, _) => await _state.InstallOrPlaySelectedAsync();
        rightRow.Children.Add(quickPlay);

        Grid.SetColumn(rightRow, 1);
        grid.Children.Add(rightRow);
        _topBarBorder.Child = grid;
    }

    private void BuildFooterBar(LauncherThemePalette t)
    {
        _footerBarBorder.Background = BrushOf(t.BgSecondary);
        _footerBarBorder.BorderBrush = BrushOf("#1AFFFFFF");
        _footerBarBorder.BorderThickness = new Thickness(0, 1, 0, 0);
        _footerBarBorder.Padding = new Thickness(24, 0);

        _footerStatusText.Foreground = BrushOf(t.TextSecondary);
        _footerStatusText.Text = _state.CurrentStatusText;
        _footerProgress.Foreground = AuroraGradient(t, 1.0);
        _footerProgress.Background = BrushOf(t.BgSurface);
        _footerProgress.Value = _state.CurrentProgress;
    }

    private Control BuildDashboardPage(LauncherThemePalette t, bool ru)
    {
        var root = new StackPanel { Spacing = 18 };
        var spec = _state.SelectedVersionSpec;

        // 1. AURORA HERO LAUNCH CARD
        var hero = new Border
        {
            Background = AuroraGradient(t, 0.20),
            BorderBrush = BrushOf(t.AccentPrimary, 0.55),
            BorderThickness = new Thickness(1.2),
            CornerRadius = new CornerRadius(18),
            Padding = new Thickness(26, 22)
        };
        var heroGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };

        var heroLeft = new StackPanel { Spacing = 10 };
        var chipRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        chipRow.Children.Add(CreatePillBadge("OBSIDIAN AURORA v2.0", t.AccentPrimary, t.BgPrimary));
        chipRow.Children.Add(CreatePillBadge(spec.State.ToDisplayBadge(ru),
            spec.State == VersionInstallState.Installed ? t.Success : t.AccentCyan, t.BgPrimary));
        chipRow.Children.Add(CreatePillBadge(ru ? "КиллАура + Таргет HUD" : "KillAura + Target HUD", t.AccentCyan, t.BgPrimary));
        heroLeft.Children.Add(chipRow);

        heroLeft.Children.Add(new TextBlock
        {
            Text = spec.DisplayTitle,
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 28,
            FontWeight = FontWeight.Black
        });
        heroLeft.Children.Add(new TextBlock
        {
            Text = ru && !string.IsNullOrWhiteSpace(spec.SubtitleRu) ? spec.SubtitleRu : spec.Subtitle,
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 13.5
        });

        // Version Quick Switcher Pills inside Hero
        var verSwitchRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 6, 0, 0) };
        foreach (var v in _state.Versions)
        {
            bool sel = v.MinecraftVersion == spec.MinecraftVersion;
            var vBtn = new Button
            {
                Content = $"Minecraft {v.MinecraftVersion}",
                Background = sel ? AuroraGradient(t, 0.85) : BrushOf(t.BgPrimary, 0.78),
                Foreground = BrushOf(sel ? "#FFFFFF" : t.TextSecondary),
                BorderBrush = BrushOf(sel ? t.AccentCyan : "#28FFFFFF"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(10),
                Padding = new Thickness(14, 7),
                FontWeight = sel ? FontWeight.Bold : FontWeight.Medium,
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            string verId = v.MinecraftVersion;
            vBtn.Click += (_, _) => _state.SelectVersion(verId);
            verSwitchRow.Children.Add(vBtn);
        }
        heroLeft.Children.Add(verSwitchRow);
        heroGrid.Children.Add(heroLeft);

        // Big Hero Launch CTA Column
        var heroRight = new StackPanel { Spacing = 10, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Right };
        string bigLabel = spec.State switch
        {
            VersionInstallState.Installed => ru ? "▶  ЗАПУСТИТЬ ИГРУ" : "▶  LAUNCH GAME",
            VersionInstallState.Running => ru ? "●  ИГРА АКТИВНА" : "●  GAME RUNNING",
            _ => ru ? "⬇  УСТАНОВИТЬ И ИГРАТЬ" : "⬇  INSTALL & PLAY"
        };
        var bigBtn = CreatePrimaryButton(bigLabel, t, fontSize: 15.5, padH: 28, padV: 15);
        bigBtn.IsEnabled = !_state.IsBusy && spec.State != VersionInstallState.Running;
        bigBtn.Click += async (_, _) => await _state.InstallOrPlaySelectedAsync();
        heroRight.Children.Add(bigBtn);
        heroRight.Children.Add(new TextBlock
        {
            Text = ru
                ? $"ОЗУ: {_state.Settings.AllocatedRamMb} МБ • ClickGUI: Правый Shift"
                : $"RAM: {_state.Settings.AllocatedRamMb} MB • ClickGUI: Right Shift",
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 11.5,
            HorizontalAlignment = HorizontalAlignment.Center
        });
        Grid.SetColumn(heroRight, 1);
        heroGrid.Children.Add(heroRight);
        hero.Child = heroGrid;
        root.Children.Add(hero);

        // 2. TWO-COLUMN INTERACTIVE RAM ALLOCATOR + AUTO-JAVA PROVISIONER
        var midGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("1.15*,16,0.85*") };
        midGrid.Children.Add(BuildInteractiveRamCard(t, ru));
        var javaCard = BuildAutoJavaCard(t, ru);
        Grid.SetColumn(javaCard, 2);
        midGrid.Children.Add(javaCard);
        root.Children.Add(midGrid);

        // 3. FEATURED COMBAT & HUD MODULES QUICK TOGGLES
        var quickCard = CreateSurfaceCard(t);
        var quickStack = new StackPanel { Spacing = 12 };
        var qHeader = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        qHeader.Children.Add(new TextBlock
        {
            Text = ru ? "✦ Быстрое управление ключевыми функциями" : "✦ Flagship In-Game Modules",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15.5,
            FontWeight = FontWeight.Bold,
            VerticalAlignment = VerticalAlignment.Center
        });
        var allModsBtn = CreateGhostButton(ru ? "Открыть все 93 модуля →" : "All 93 Modules →", t);
        allModsBtn.Click += (_, _) => NavigateTo(LauncherPage.Modules);
        Grid.SetColumn(allModsBtn, 1);
        qHeader.Children.Add(allModsBtn);
        quickStack.Children.Add(qHeader);

        var featuredGrid = new UniformGrid { Columns = 3 };
        string[] featuredIds = ["kill_aura", "target_hud", "trigger_bot", "toggle_sprint", "fullbright", "memory_display"];
        foreach (string fid in featuredIds)
        {
            var mod = _state.Modules.FirstOrDefault(m => m.Id == fid);
            if (mod is null) continue;
            featuredGrid.Children.Add(BuildCompactModuleTile(mod, t, ru));
        }
        quickStack.Children.Add(featuredGrid);
        quickCard.Child = quickStack;
        root.Children.Add(quickCard);

        return root;
    }

    private Control BuildInteractiveRamCard(LauncherThemePalette t, bool ru)
    {
        var card = CreateSurfaceCard(t);
        var stack = new StackPanel { Spacing = 12 };
        var mem = _state.MemorySnapshot;
        int currentRam = _state.Settings.AllocatedRamMb;
        int maxSlider = Math.Max(2048, mem.MaxRecommendedAllocationMb);

        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        header.Children.Add(new TextBlock
        {
            Text = ru ? "⚡ Реальное выделение ОЗУ (JVM -Xmx)" : "⚡ Real JVM Memory Allocation (-Xmx)",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15,
            FontWeight = FontWeight.Bold,
            VerticalAlignment = VerticalAlignment.Center
        });

        var ramValueBadge = new TextBlock
        {
            Text = $"{currentRam} МБ ({currentRam / 1024.0:0.0} ГБ)",
            Foreground = BrushOf(t.AccentCyan),
            FontSize = 14.5,
            FontWeight = FontWeight.ExtraBold,
            VerticalAlignment = VerticalAlignment.Center
        };
        if (!ru) ramValueBadge.Text = $"{currentRam} MB ({currentRam / 1024.0:0.0} GB)";
        Grid.SetColumn(ramValueBadge, 1);
        header.Children.Add(ramValueBadge);
        stack.Children.Add(header);

        stack.Children.Add(new TextBlock
        {
            Text = ru
                ? $"Физическая память ПК: {mem.TotalPhysicalMb:N0} МБ • Свободно сейчас: {mem.AvailablePhysicalMb:N0} МБ"
                : $"System Physical RAM: {mem.TotalPhysicalMb:N0} MB • Currently Free: {mem.AvailablePhysicalMb:N0} MB",
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 12
        });

        var slider = new Slider
        {
            Minimum = 1024,
            Maximum = maxSlider,
            Value = Math.Clamp(currentRam, 1024, maxSlider),
            TickFrequency = 256,
            IsSnapToTickEnabled = true,
            Foreground = BrushOf(t.AccentPrimary)
        };
        slider.PropertyChanged += (_, e) =>
        {
            if (e.Property == RangeBase.ValueProperty)
            {
                int val = ((int)Math.Round(slider.Value / 128.0)) * 128;
                ramValueBadge.Text = ru
                    ? $"{val} МБ ({val / 1024.0:0.0} ГБ)"
                    : $"{val} MB ({val / 1024.0:0.0} GB)";
            }
        };
        slider.PointerCaptureLost += (_, _) =>
        {
            int val = ((int)Math.Round(slider.Value / 128.0)) * 128;
            _state.SetAllocatedRamMb(val);
        };
        stack.Children.Add(slider);

        // Quick Preset Pills (2 GB, 4 GB, 6 GB, 8 GB, 12 GB, 16 GB)
        var presetsRow = new WrapPanel { Orientation = Orientation.Horizontal };
        int[] presetsMb = [2048, 4096, 6144, 8192, 12288, 16384];
        foreach (int pMb in presetsMb)
        {
            if (pMb > maxSlider && pMb > 4096) continue;
            bool isSel = Math.Abs(currentRam - pMb) < 128;
            var pBtn = new Button
            {
                Content = ru ? $"{pMb / 1024} ГБ" : $"{pMb / 1024} GB",
                Background = isSel ? AuroraGradient(t, 0.85) : BrushOf(t.BgPrimary),
                Foreground = BrushOf(isSel ? "#FFFFFF" : t.TextSecondary),
                BorderBrush = BrushOf(isSel ? t.AccentCyan : "#24FFFFFF"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(11, 5),
                Margin = new Thickness(0, 0, 6, 6),
                FontSize = 12,
                FontWeight = FontWeight.SemiBold,
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            int targetMb = Math.Min(pMb, maxSlider);
            pBtn.Click += (_, _) => _state.SetAllocatedRamMb(targetMb);
            presetsRow.Children.Add(pBtn);
        }
        stack.Children.Add(presetsRow);

        card.Child = stack;
        return card;
    }

    private Control BuildAutoJavaCard(LauncherThemePalette t, bool ru)
    {
        var card = CreateSurfaceCard(t);
        var stack = new StackPanel { Spacing = 10 };

        stack.Children.Add(new TextBlock
        {
            Text = ru ? "☕ Автоматическая Java 8 и 21" : "☕ Automatic Java 8 & 21",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15,
            FontWeight = FontWeight.Bold
        });

        stack.Children.Add(BuildJavaStatusRow("Java 8 (MC 1.16.5)", _state.DetectedJava8, t, ru));
        stack.Children.Add(BuildJavaStatusRow("Java 21 (MC 1.21.x)", _state.DetectedJava21, t, ru));

        bool bothReady = _state.DetectedJava8 is not null && _state.DetectedJava21 is not null;
        var autoBtn = CreatePrimaryButton(
            _state.IsJavaProvisioning
                ? (ru ? "⏳ Скачивание и распаковка Java..." : "⏳ Installing Java...")
                : bothReady
                    ? (ru ? "✓ Обе версии Java установлены" : "✓ Both Java Runtimes Ready")
                    : (ru ? "⬇ Авто-установить Java 8 + Java 21" : "⬇ Auto-Install Java 8 + 21"),
            t, fontSize: 12.5, padH: 14, padV: 9);
        autoBtn.HorizontalAlignment = HorizontalAlignment.Stretch;
        autoBtn.HorizontalContentAlignment = HorizontalAlignment.Center;
        autoBtn.IsEnabled = !_state.IsJavaProvisioning;
        autoBtn.Click += async (_, _) => await _state.AutoInstallMissingJavaSilentOrInteractiveAsync(interactiveError: true);
        stack.Children.Add(autoBtn);

        card.Child = stack;
        return card;
    }

    private static Control BuildJavaStatusRow(string label, DetectedJavaRuntime? rt, LauncherThemePalette t, bool ru)
    {
        var border = new Border
        {
            Background = BrushOf(t.BgPrimary, 0.75),
            BorderBrush = BrushOf(rt is not null ? t.Success : t.AccentCyan, 0.35),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(9),
            Padding = new Thickness(10, 7)
        };
        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        grid.Children.Add(new TextBlock
        {
            Text = label,
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 12.5,
            FontWeight = FontWeight.SemiBold,
            VerticalAlignment = VerticalAlignment.Center
        });
        var badge = new TextBlock
        {
            Text = rt is not null
                ? $"✓ v{rt.RawVersion}"
                : (ru ? "Авто при старте" : "Auto on Play"),
            Foreground = BrushOf(rt is not null ? t.Success : t.AccentCyan),
            FontSize = 11.5,
            FontWeight = FontWeight.Bold,
            VerticalAlignment = VerticalAlignment.Center
        };
        Grid.SetColumn(badge, 1);
        grid.Children.Add(badge);
        border.Child = grid;
        return border;
    }

    private Control BuildCompactModuleTile(ModuleCatalogItem mod, LauncherThemePalette t, bool ru)
    {
        var border = new Border
        {
            Background = mod.Enabled ? AuroraGradient(t, 0.16) : BrushOf(t.BgPrimary, 0.72),
            BorderBrush = BrushOf(mod.Enabled ? t.AccentPrimary : "#20FFFFFF", mod.Enabled ? 0.65 : 1.0),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(12, 10),
            Margin = new Thickness(4)
        };
        var stack = new StackPanel { Spacing = 5 };
        var top = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        top.Children.Add(new TextBlock
        {
            Text = mod.DisplayName(ru),
            Foreground = BrushOf(t.TextPrimary),
            FontWeight = FontWeight.Bold,
            FontSize = 13,
            VerticalAlignment = VerticalAlignment.Center
        });
        var toggle = new CheckBox { IsChecked = mod.Enabled, VerticalAlignment = VerticalAlignment.Center };
        toggle.IsCheckedChanged += (_, _) =>
        {
            mod.Enabled = toggle.IsChecked == true;
            _state.SyncModulesAndThemeToSelectedInstance();
            RebuildDynamicRegions();
        };
        Grid.SetColumn(toggle, 1);
        top.Children.Add(toggle);
        stack.Children.Add(top);

        stack.Children.Add(new TextBlock
        {
            Text = mod.DisplayDescription(ru),
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 11.2,
            TextWrapping = TextWrapping.Wrap,
            MaxLines = 2
        });
        border.Child = stack;
        return border;
    }

    private Control BuildPlayPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 18 };
        stack.Children.Add(BuildInteractiveRamCard(t, ru));

        var resCard = CreateSurfaceCard(t);
        var resStack = new StackPanel { Spacing = 12 };
        resStack.Children.Add(new TextBlock
        {
            Text = ru ? "🖥 Параметры окна игры и JVM" : "🖥 Display & JVM Launch Parameters",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15.5,
            FontWeight = FontWeight.Bold
        });

        var p = _state.SelectedProfile;
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        var wBox = new TextBox { Width = 110, Text = p.ResolutionWidth.ToString(), Watermark = "Width" };
        var hBox = new TextBox { Width = 110, Text = p.ResolutionHeight.ToString(), Watermark = "Height" };
        var fsChk = new CheckBox
        {
            Content = ru ? "Полный экран (Fullscreen)" : "Start Fullscreen",
            IsChecked = p.Fullscreen,
            Foreground = BrushOf(t.TextPrimary)
        };
        var applyBtn = CreateGhostButton(ru ? "Сохранить разрешение" : "Save Resolution", t);
        applyBtn.Click += (_, _) =>
        {
            if (int.TryParse(wBox.Text, out int w)) p.ResolutionWidth = Math.Clamp(w, 640, 7680);
            if (int.TryParse(hBox.Text, out int h)) p.ResolutionHeight = Math.Clamp(h, 480, 4320);
            p.Fullscreen = fsChk.IsChecked == true;
            _state.SaveAllState();
            _state.NotifyChanged();
        };
        row.Children.Add(wBox);
        row.Children.Add(new TextBlock { Text = "×", Foreground = BrushOf(t.TextSecondary), VerticalAlignment = VerticalAlignment.Center });
        row.Children.Add(hBox);
        row.Children.Add(fsChk);
        row.Children.Add(applyBtn);
        resStack.Children.Add(row);

        var jvmBox = new TextBox { Text = p.CustomJvmArgs, Watermark = "-XX:+UseG1GC ..." };
        jvmBox.LostFocus += (_, _) =>
        {
            p.CustomJvmArgs = jvmBox.Text ?? "";
            _state.SaveAllState();
        };
        resStack.Children.Add(jvmBox);
        resCard.Child = resStack;
        stack.Children.Add(resCard);

        return stack;
    }

    private Control BuildVersionsPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 14 };
        foreach (var v in _state.Versions)
        {
            var card = CreateSurfaceCard(t);
            var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var left = new StackPanel { Spacing = 6 };

            var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
            titleRow.Children.Add(new TextBlock
            {
                Text = v.DisplayTitle,
                Foreground = BrushOf(t.TextPrimary),
                FontSize = 17,
                FontWeight = FontWeight.ExtraBold
            });
            titleRow.Children.Add(CreatePillBadge(v.State.ToDisplayBadge(ru),
                v.State == VersionInstallState.Installed ? t.Success : t.AccentCyan, t.BgPrimary));
            titleRow.Children.Add(CreatePillBadge($"Java {v.RequiredJavaMajor}", t.AccentPrimary, t.BgPrimary));
            left.Children.Add(titleRow);

            left.Children.Add(new TextBlock
            {
                Text = ru && !string.IsNullOrWhiteSpace(v.SubtitleRu) ? v.SubtitleRu : v.Subtitle,
                Foreground = BrushOf(t.TextSecondary),
                FontSize = 12.5
            });
            left.Children.Add(new TextBlock
            {
                Text = v.StatusDetail,
                Foreground = BrushOf(t.AccentCyan),
                FontSize = 11.8
            });
            grid.Children.Add(left);

            var actions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
            var specRef = v;

            var playBtn = CreatePrimaryButton(
                v.State == VersionInstallState.Installed
                    ? (ru ? "▶ ИГРАТЬ" : "▶ PLAY")
                    : (ru ? "⬇ УСТАНОВИТЬ" : "⬇ INSTALL"),
                t, fontSize: 12.5, padH: 16, padV: 9);
            playBtn.Click += async (_, _) =>
            {
                _state.SelectVersion(specRef.MinecraftVersion);
                await _state.InstallOrPlaySelectedAsync();
            };
            actions.Children.Add(playBtn);

            var repairBtn = CreateGhostButton(ru ? "Починить" : "Repair", t);
            repairBtn.Click += async (_, _) => await _state.RepairVersionAsync(specRef);
            actions.Children.Add(repairBtn);

            var folderBtn = CreateGhostButton(ru ? "Папка" : "Folder", t);
            folderBtn.Click += (_, _) => _state.OpenInstanceFolder(specRef);
            actions.Children.Add(folderBtn);

            Grid.SetColumn(actions, 1);
            grid.Children.Add(actions);
            card.Child = grid;
            stack.Children.Add(card);
        }
        return stack;
    }

    private Control BuildModulesPage(LauncherThemePalette t, bool ru)
    {
        var root = new StackPanel { Spacing = 14 };

        var filterCard = CreateSurfaceCard(t);
        var filterStack = new StackPanel { Spacing = 10 };

        var searchBox = new TextBox
        {
            Text = _moduleSearch,
            Watermark = ru
                ? "🔎 Поиск по 93 модулям (например: КиллАура, Таргет HUD, Авто-спринт, FPS)..."
                : "🔎 Search 93 modules (e.g. KillAura, Target HUD, Toggle Sprint, FPS)..."
        };
        searchBox.TextChanged += (_, _) =>
        {
            _moduleSearch = searchBox.Text ?? "";
            RebuildDynamicRegions();
        };
        filterStack.Children.Add(searchBox);

        var catRow = new WrapPanel { Orientation = Orientation.Horizontal };
        (string Key, string Ru, string En)[] cats =
        [
            ("ALL", "Все (93)", "All (93)"),
            ("Combat", "Бой (Combat)", "Combat"),
            ("HUD", "HUD Виджеты", "HUD"),
            ("Render", "Визуал", "Render"),
            ("Movement", "Движение", "Movement"),
            ("Player", "Игрок", "Player"),
            ("World", "Мир", "World"),
            ("Utility", "Утилиты", "Utility"),
            ("Settings", "Интерфейс", "Settings")
        ];
        foreach (var c in cats)
        {
            bool active = _moduleCategoryFilter == c.Key;
            var btn = new Button
            {
                Content = ru ? c.Ru : c.En,
                Background = active ? AuroraGradient(t, 0.85) : BrushOf(t.BgPrimary),
                Foreground = BrushOf(active ? "#FFFFFF" : t.TextSecondary),
                BorderBrush = BrushOf(active ? t.AccentCyan : "#24FFFFFF"),
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 6),
                Margin = new Thickness(0, 0, 6, 6),
                FontSize = 12,
                FontWeight = FontWeight.SemiBold,
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            string key = c.Key;
            btn.Click += (_, _) =>
            {
                _moduleCategoryFilter = key;
                RebuildDynamicRegions();
            };
            catRow.Children.Add(btn);
        }
        filterStack.Children.Add(catRow);
        filterCard.Child = filterStack;
        root.Children.Add(filterCard);

        var filtered = _state.Modules.Where(m =>
        {
            if (_moduleCategoryFilter != "ALL" && !string.Equals(m.Category, _moduleCategoryFilter, StringComparison.OrdinalIgnoreCase))
                return false;
            if (string.IsNullOrWhiteSpace(_moduleSearch)) return true;
            string q = _moduleSearch.Trim();
            return m.Name.Contains(q, StringComparison.OrdinalIgnoreCase)
                   || m.NameRu.Contains(q, StringComparison.OrdinalIgnoreCase)
                   || m.Description.Contains(q, StringComparison.OrdinalIgnoreCase)
                   || m.DescriptionRu.Contains(q, StringComparison.OrdinalIgnoreCase)
                   || m.Id.Contains(q, StringComparison.OrdinalIgnoreCase);
        }).ToList();

        var grid = new UniformGrid { Columns = 2 };
        foreach (var mod in filtered)
            grid.Children.Add(BuildCompactModuleTile(mod, t, ru));

        root.Children.Add(grid);
        return root;
    }

    private Control BuildAccountsPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 16 };

        var addCard = CreateSurfaceCard(t);
        var addStack = new StackPanel { Spacing = 12 };
        addStack.Children.Add(new TextBlock
        {
            Text = ru ? "🔐 Авторизация Microsoft OAuth2 и Локальный никнейм" : "🔐 Microsoft OAuth2 & Local Nickname",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15.5,
            FontWeight = FontWeight.Bold
        });
        addStack.Children.Add(new TextBlock
        {
            Text = ru
                ? "Лицензионный вход открывается в системном браузере (OAuth2 + PKCE) и шифруется через Windows DPAPI. Мы никогда не запрашиваем пароль в лаунчере."
                : "Official Microsoft sign-in uses your system browser (OAuth2 + PKCE) encrypted with Windows DPAPI.",
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 12,
            TextWrapping = TextWrapping.Wrap
        });

        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        var msBtn = CreatePrimaryButton(ru ? "🌐 Войти через Microsoft (Браузер)" : "🌐 Sign In with Microsoft", t, 12.5, 16, 9);
        msBtn.Click += async (_, _) =>
        {
            try
            {
                var acc = await _state.AuthService.SignInWithBrowserAsync(msg => _state.AppendConsole(msg));
                _state.Settings.SelectedAccountUuid = acc.Uuid;
                _state.SaveAllState();
                _state.NotifyChanged();
            }
            catch (Exception ex)
            {
                _state.AppendConsole($"[OAuth] {ex.Message}");
            }
        };
        row.Children.Add(msBtn);

        var nickBox = new TextBox { Width = 200, Watermark = ru ? "Ваш никнейм (3-16 симв.)" : "Player Nickname" };
        var localBtn = CreateGhostButton(ru ? "+ Добавить локальный ник" : "+ Add Local Profile", t);
        localBtn.Click += (_, _) =>
        {
            var created = _state.AuthService.CreateLocalSandboxAccount(nickBox.Text ?? "AxsesPlayer");
            _state.Settings.SelectedAccountUuid = created.Uuid;
            _state.SaveAllState();
            _state.NotifyChanged();
        };
        row.Children.Add(nickBox);
        row.Children.Add(localBtn);
        addStack.Children.Add(row);
        addCard.Child = addStack;
        stack.Children.Add(addCard);

        foreach (var acc in _state.Accounts)
        {
            bool active = acc.Uuid == _state.Settings.SelectedAccountUuid;
            var card = CreateSurfaceCard(t);
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var info = new StackPanel { Spacing = 3 };
            info.Children.Add(new TextBlock
            {
                Text = $"👤 {acc.Username}",
                Foreground = BrushOf(t.TextPrimary),
                FontSize = 15,
                FontWeight = FontWeight.Bold
            });
            info.Children.Add(new TextBlock
            {
                Text = $"{acc.AccountType} • UUID: {acc.Uuid}",
                Foreground = BrushOf(t.TextSecondary),
                FontSize = 11.5
            });
            g.Children.Add(info);

            var act = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
            string uid = acc.Uuid;
            var selBtn = active
                ? CreatePrimaryButton(ru ? "✓ Активен" : "✓ Active", t, 12, 14, 7)
                : CreateGhostButton(ru ? "Выбрать" : "Select", t);
            selBtn.Click += (_, _) =>
            {
                _state.Settings.SelectedAccountUuid = uid;
                _state.SaveAllState();
                _state.NotifyChanged();
            };
            act.Children.Add(selBtn);
            Grid.SetColumn(act, 1);
            g.Children.Add(act);
            card.Child = g;
            stack.Children.Add(card);
        }

        return stack;
    }

    private Control BuildProfilesPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 14 };
        foreach (var prof in _state.Profiles)
        {
            bool sel = prof.Id == _state.Settings.SelectedProfileId;
            var card = CreateSurfaceCard(t);
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var info = new StackPanel { Spacing = 4 };
            info.Children.Add(new TextBlock
            {
                Text = prof.Name,
                Foreground = BrushOf(t.TextPrimary),
                FontSize = 15.5,
                FontWeight = FontWeight.Bold
            });
            info.Children.Add(new TextBlock
            {
                Text = $"Minecraft {prof.MinecraftVersion} • ОЗУ: {prof.RamMb} МБ • {prof.ResolutionWidth}×{prof.ResolutionHeight}",
                Foreground = BrushOf(t.TextSecondary),
                FontSize = 12
            });
            g.Children.Add(info);

            string pid = prof.Id;
            string pVer = prof.MinecraftVersion;
            var btn = sel
                ? CreatePrimaryButton(ru ? "✓ Выбран" : "✓ Active", t, 12, 14, 7)
                : CreateGhostButton(ru ? "Активировать" : "Activate", t);
            btn.Click += (_, _) =>
            {
                _state.Settings.SelectedProfileId = pid;
                _state.Settings.SelectedVersion = pVer;
                _state.SaveAllState();
                _state.NotifyChanged();
            };
            Grid.SetColumn(btn, 1);
            g.Children.Add(btn);
            card.Child = g;
            stack.Children.Add(card);
        }
        return stack;
    }

    private Control BuildAppearancePage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 14 };
        stack.Children.Add(new TextBlock
        {
            Text = ru
                ? "Выберите фирменную тему оформления (применяется одновременно к лаунчеру и внутриигровому ClickGUI / HUD):"
                : "Select synchronized theme for both Launcher and in-game ClickGUI / HUD:",
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 13
        });

        var grid = new UniformGrid { Columns = 2 };
        foreach (var th in _state.Themes)
        {
            bool active = th.Id == t.Id;
            var card = new Border
            {
                Background = BrushOf(th.BgSecondary),
                BorderBrush = BrushOf(active ? th.AccentCyan : th.AccentPrimary, active ? 0.95 : 0.35),
                BorderThickness = new Thickness(active ? 2 : 1),
                CornerRadius = new CornerRadius(14),
                Padding = new Thickness(16, 14),
                Margin = new Thickness(6)
            };
            var s = new StackPanel { Spacing = 10 };
            var swatches = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
            foreach (string hex in new[] { th.AccentPrimary, th.AccentSecondary, th.AccentCyan, th.Success })
            {
                swatches.Children.Add(new Border
                {
                    Width = 22, Height = 22, CornerRadius = new CornerRadius(6), Background = BrushOf(hex)
                });
            }
            s.Children.Add(swatches);
            s.Children.Add(new TextBlock
            {
                Text = th.Name,
                Foreground = BrushOf(th.TextPrimary),
                FontWeight = FontWeight.ExtraBold,
                FontSize = 15
            });
            string tid = th.Id;
            var apply = CreatePrimaryButton(active ? (ru ? "✓ Активная тема" : "✓ Active Theme") : (ru ? "Применить" : "Apply"), th, 12, 14, 7);
            apply.Click += (_, _) =>
            {
                _state.SetActiveTheme(tid);
                _state.SyncModulesAndThemeToSelectedInstance();
            };
            s.Children.Add(apply);
            card.Child = s;
            grid.Children.Add(card);
        }
        stack.Children.Add(grid);
        return stack;
    }

    private Control BuildSettingsPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 16 };
        stack.Children.Add(BuildInteractiveRamCard(t, ru));
        stack.Children.Add(BuildAutoJavaCard(t, ru));

        var pathCard = CreateSurfaceCard(t);
        var pStack = new StackPanel { Spacing = 10 };
        pStack.Children.Add(new TextBlock
        {
            Text = ru ? "⚙ Пути к Java и Автоматизация" : "⚙ Java Paths & Automation",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15,
            FontWeight = FontWeight.Bold
        });

        var autoJavaChk = new CheckBox
        {
            Content = ru
                ? "Автоматически скачивать и настраивать Java 8 и Java 21 без ручной установки"
                : "Automatically download & configure Java 8 and Java 21 runtimes",
            IsChecked = _state.Settings.AutoInstallJava,
            Foreground = BrushOf(t.TextPrimary)
        };
        autoJavaChk.IsCheckedChanged += (_, _) =>
        {
            _state.Settings.AutoInstallJava = autoJavaChk.IsChecked == true;
            _state.SaveAllState();
        };
        pStack.Children.Add(autoJavaChk);

        var j8Box = new TextBox { Text = _state.Settings.Java8Path, Watermark = "Java 8 executable (auto-detected)" };
        var j21Box = new TextBox { Text = _state.Settings.Java21Path, Watermark = "Java 21 executable (auto-detected)" };
        var savePaths = CreateGhostButton(ru ? "Сохранить настройки путей" : "Save Paths", t);
        savePaths.Click += (_, _) =>
        {
            _state.Settings.Java8Path = j8Box.Text ?? "";
            _state.Settings.Java21Path = j21Box.Text ?? "";
            _state.RefreshJavaStatus();
            _state.SaveAllState();
            _state.NotifyChanged();
        };
        pStack.Children.Add(j8Box);
        pStack.Children.Add(j21Box);
        pStack.Children.Add(savePaths);
        pathCard.Child = pStack;
        stack.Children.Add(pathCard);

        return stack;
    }

    private Control BuildDownloadsPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 10 };
        var card = CreateSurfaceCard(t);
        var inner = new StackPanel { Spacing = 8 };
        inner.Children.Add(new TextBlock
        {
            Text = ru ? "⬇ Очередь загрузок и SHA-1 верификация" : "⬇ Verified Asset & Library Downloads",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 15.5,
            FontWeight = FontWeight.Bold
        });

        List<DownloadTaskItem> items;
        lock (_state.Downloads) items = _state.Downloads.Take(40).ToList();

        if (items.Count == 0)
        {
            inner.Children.Add(new TextBlock
            {
                Text = ru ? "Нет активных загрузок. Все файлы проверены или ожидают старта." : "No active downloads in queue.",
                Foreground = BrushOf(t.TextSecondary),
                FontSize = 12.5
            });
        }
        else
        {
            foreach (var d in items)
            {
                inner.Children.Add(new TextBlock
                {
                    Text = $"• [{d.Stage}] {d.FileName} — {d.Status} ({d.SpeedMbps:0.0} MB/s)",
                    Foreground = BrushOf(d.ShaVerified ? t.Success : t.TextSecondary),
                    FontSize = 12
                });
            }
        }
        card.Child = inner;
        stack.Children.Add(card);
        return stack;
    }

    private Control BuildDiagnosticsPage(LauncherThemePalette t, bool ru)
    {
        var stack = new StackPanel { Spacing = 14 };
        var topCard = CreateSurfaceCard(t);
        var topStack = new StackPanel { Spacing = 10 };
        var runBtn = CreatePrimaryButton(ru ? "⚡ Запустить полную диагностику ПК и Java" : "⚡ Run Full Diagnostics", t, 12.5, 16, 9);
        runBtn.Click += async (_, _) => await _state.RunDiagnosticsAsync();
        topStack.Children.Add(runBtn);

        foreach (var check in _state.LastDiagnostics)
        {
            topStack.Children.Add(new TextBlock
            {
                Text = $"{(check.Passed ? "✓" : "!")} [{check.Category}] {check.Name}: {check.Detail}",
                Foreground = BrushOf(check.Passed ? t.Success : t.Warning),
                FontSize = 12,
                TextWrapping = TextWrapping.Wrap
            });
        }
        topCard.Child = topStack;
        stack.Children.Add(topCard);

        var logCard = CreateSurfaceCard(t);
        var logStack = new StackPanel { Spacing = 8 };
        logStack.Children.Add(new TextBlock
        {
            Text = ru ? "Журнал запуска (секреты и токены скрыты)" : "Live Scrubbed Console",
            Foreground = BrushOf(t.TextPrimary),
            FontWeight = FontWeight.Bold,
            FontSize = 14
        });
        string joined;
        lock (_state.LiveConsoleLines)
            joined = string.Join(Environment.NewLine, _state.LiveConsoleLines.TakeLast(35));
        logStack.Children.Add(new TextBlock
        {
            Text = joined,
            Foreground = BrushOf(t.TextSecondary),
            FontFamily = new FontFamily("Consolas, Monospace"),
            FontSize = 11.5,
            TextWrapping = TextWrapping.Wrap
        });
        logCard.Child = logStack;
        stack.Children.Add(logCard);

        return stack;
    }

    private Control BuildAboutPage(LauncherThemePalette t, bool ru)
    {
        var card = CreateSurfaceCard(t);
        var s = new StackPanel { Spacing = 10 };
        s.Children.Add(new TextBlock
        {
            Text = "AXSESCLIENT v2.0.0 • OBSIDIAN AURORA",
            Foreground = BrushOf(t.TextPrimary),
            FontSize = 22,
            FontWeight = FontWeight.Black
        });
        s.Children.Add(new TextBlock
        {
            Text = ru
                ? "Нативный лаунчер и игровой клиент нового поколения для Windows 10/11 x64.\n" +
                  "• Поддержка Minecraft 1.16.5, 1.21.4 и 1.21.11 (отдельные оптимизированные Fabric-моды)\n" +
                  "• 93 рабочие функции: КиллАура, Таргет HUD, ТриггерБот, Авто-спринт, Кейстрокс и кастомный HUD\n" +
                  "• Честное выделение ОЗУ (-Xmx) на базе физической памяти ПК + Автоматическая установка Java 8 и Java 21"
                : "Next-generation native Windows x64 Launcher & Fabric Client for Minecraft 1.16.5, 1.21.4 & 1.21.11.\n" +
                  "• 93 real modules including KillAura, animated Target HUD, TriggerBot, Keystrokes & HUD Editor\n" +
                  "• Hardware-calibrated JVM RAM allocation (-Xmx) & zero-touch Java 8 / Java 21 auto-installer",
            Foreground = BrushOf(t.TextSecondary),
            FontSize = 13,
            LineHeight = 21,
            TextWrapping = TextWrapping.Wrap
        });
        card.Child = s;
        return card;
    }

    private static string GetPageTitle(LauncherPage page, bool ru) => page switch
    {
        LauncherPage.Dashboard => ru ? "Панель управления" : "Dashboard",
        LauncherPage.Play => ru ? "Параметры запуска и ОЗУ" : "Launch & Memory",
        LauncherPage.Modules => ru ? "Модули клиента (93)" : "Client Modules (93)",
        LauncherPage.Versions => ru ? "Версии Minecraft" : "Minecraft Versions",
        LauncherPage.Accounts => ru ? "Аккаунты игроков" : "Accounts",
        LauncherPage.Profiles => ru ? "Профили сборок" : "Launch Profiles",
        LauncherPage.Appearance => ru ? "Темы оформления" : "Themes & Style",
        LauncherPage.Settings => ru ? "Настройки и Авто-Java" : "Settings & Auto-Java",
        LauncherPage.Downloads => ru ? "Менеджер загрузок" : "Downloads",
        LauncherPage.Diagnostics => ru ? "Диагностика и консоль" : "Diagnostics",
        LauncherPage.About => ru ? "О проекте AXSESCLIENT" : "About AXSESCLIENT",
        _ => "AXSESCLIENT"
    };

    private static Border CreateSurfaceCard(LauncherThemePalette t) => new()
    {
        Background = BrushOf(t.BgSecondary, 0.94),
        BorderBrush = BrushOf("#1EFFFFFF"),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(16),
        Padding = new Thickness(20, 18)
    };

    private static Border CreatePillBadge(string text, string fgHex, string bgHex) => new()
    {
        Background = BrushOf(bgHex, 0.85),
        BorderBrush = BrushOf(fgHex, 0.45),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(999),
        Padding = new Thickness(10, 3.5),
        VerticalAlignment = VerticalAlignment.Center,
        Child = new TextBlock
        {
            Text = text,
            Foreground = BrushOf(fgHex),
            FontSize = 11,
            FontWeight = FontWeight.Bold
        }
    };

    private static Button CreatePrimaryButton(string text, LauncherThemePalette t, double fontSize = 13, double padH = 18, double padV = 10) => new()
    {
        Content = text,
        Background = AuroraGradient(t, 1.0),
        Foreground = Brushes.White,
        BorderBrush = BrushOf(t.AccentCyan, 0.65),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(11),
        Padding = new Thickness(padH, padV),
        FontSize = fontSize,
        FontWeight = FontWeight.ExtraBold,
        Cursor = new Cursor(StandardCursorType.Hand)
    };

    private static Button CreateGhostButton(string text, LauncherThemePalette t) => new()
    {
        Content = text,
        Background = BrushOf(t.BgSurface, 0.80),
        Foreground = BrushOf(t.TextPrimary),
        BorderBrush = BrushOf("#26FFFFFF"),
        BorderThickness = new Thickness(1),
        CornerRadius = new CornerRadius(10),
        Padding = new Thickness(13, 7),
        FontSize = 12,
        FontWeight = FontWeight.SemiBold,
        Cursor = new Cursor(StandardCursorType.Hand)
    };

    private static LinearGradientBrush AuroraGradient(LauncherThemePalette t, double opacity) => new()
    {
        StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
        EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
        Opacity = opacity,
        GradientStops =
        [
            new GradientStop(Color.Parse(t.AccentPrimary), 0.0),
            new GradientStop(Color.Parse(t.AccentSecondary), 0.55),
            new GradientStop(Color.Parse(t.AccentCyan), 1.0)
        ]
    };

    private static SolidColorBrush BrushOf(string hex, double opacity = 1.0) =>
        new(Color.Parse(hex), opacity);
}
