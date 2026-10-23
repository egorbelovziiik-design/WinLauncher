using System.Collections.ObjectModel;
using System.IO;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;

namespace DrBrowser;

public sealed partial class MainViewModel : ObservableObject, IDisposable
{
    private readonly Logger _log;
    private readonly SettingsService _settingsService;
    private readonly Db _db;
    private readonly SessionService _session;

    private IBrowserShell? _shell;
    private DispatcherTimer? _suspendTimer;
    private readonly Dictionary<string, string> _lastRecorded = new();

    private string _addressText = string.Empty;
    private string _statusMessage = string.Empty;
    private bool _isAddressEditing;
    private bool _isAiOpen;
    private bool _isFindVisible;
    private string _findText = string.Empty;
    private string _currentInternalKey = "newtab";
    private bool _runtimeMissing;
    private string _runtimeStatus = string.Empty;
    private int _aiBadge;
    private bool _disposed;

    public MainViewModel(Logger log, bool privateMode = false)
    {
        _log = log;
        IsPrivate = privateMode;

        _settingsService = new SettingsService(log);
        Settings = _settingsService.Load();

        _db = new Db(IsPrivate ? Path.Combine(Path.GetTempPath(), "DrBrowser", "incognito-" + Guid.NewGuid().ToString("N") + ".db") : null);
        _session = new SessionService(log);

        History = new HistoryService(_db, log);
        Bookmarks = new BookmarkService(_db, log);
        Passwords = new PasswordService(_db, log);
        AdBlock = new AdBlockService(log);
        AI = new AIService(log);
        Downloads = new DownloadService(log, _db, () => Settings);
        TabManager = new TabManager();
        NewTab = new NewTabViewModel(log, _db, () => Settings);

        WebProfileFolder = string.Empty;
        var profile = IsPrivate
            ? AppPaths.NewIncognitoFolder()
            : string.IsNullOrWhiteSpace(Settings.UserDataFolder) ? AppPaths.DefaultWebProfile : Settings.UserDataFolder;

        WebViews = new WebViewManager(
            log,
            () => Settings,
            AdBlock,
            Downloads,
            _db,
            (host, kind) => _shell?.AskPermissionAsync(host, kind) ?? Task.FromResult(false),
            (filter, name, dir) => _shell?.PickSaveFile(filter, name, dir),
            profile);
        WebProfileFolder = profile;

        TabManager.PropertyChanged += (_, e) =>
        {
            if (e.PropertyName == nameof(TabManager.ActiveTab)) OnActiveTabChanged();
        };

        WebViews.TabStateChanged += OnTabStateChanged;
        WebViews.NavigationFinished += OnNavigationFinished;
        WebViews.NewWindowRequested += (_, url, userInitiated) => CreateWebTab(url, activate: userInitiated);
        WebViews.TabCrashed += (tab, reason) => StatusMessage = $"Вкладка «{tab.DisplayTitle}» аварийно завершилась ({reason}).";
        WebViews.FullScreenChanged += enabled => _shell?.RequestFullscreen(enabled);
        WebViews.StatusTextChanged += text => StatusMessage = text;
        WebViews.BlockedRequest += () => { _aiBadge++; Raise(nameof(AdBlockText)); };

        Downloads.Changed += (_, _) => { Raise(nameof(DownloadsText)); Raise(nameof(HasDownloads)); };

        Settings.PropertyChanged += (_, _) => OnSettingsChanged();

        _log.Info($"MainViewModel created (private={IsPrivate})");
    }

    // ------------------------------------------------------------------
    // Services
    // ------------------------------------------------------------------

    public AppSettings Settings { get; private set; }
    public HistoryService History { get; }
    public BookmarkService Bookmarks { get; }
    public PasswordService Passwords { get; }
    public DownloadService Downloads { get; }
    public AdBlockService AdBlock { get; }
    public AIService AI { get; }
    public TabManager TabManager { get; }
    public WebViewManager WebViews { get; }
    public NewTabViewModel NewTab { get; }
    public bool IsPrivate { get; }

    /// <summary>WebView2 user-data folder for this window (deleted after an incognito session).</summary>
    public string WebProfileFolder { get; private set; } = string.Empty;

    public ObservableCollection<BrowserTab> Tabs => TabManager.Tabs;
    public BrowserTab? ActiveTab => TabManager.ActiveTab;

    // ------------------------------------------------------------------
    // Shell state
    // ------------------------------------------------------------------

    public string CurrentUrl => ActiveTab?.Url ?? string.Empty;
    public string PageTitle => ActiveTab?.DisplayTitle ?? "DrBrowser";
    public string WindowTitle => IsPrivate ? $"{PageTitle} — DrBrowser (Инкогнито)" : $"{PageTitle} — DrBrowser";

    public bool CanGoBack => ActiveTab?.CanGoBack ?? false;
    public bool CanGoForward => ActiveTab?.CanGoForward ?? false;
    public bool IsLoading => ActiveTab?.IsLoading ?? false;
    public bool HasError => ActiveTab?.HasError ?? false;
    public string LastError => ActiveTab?.LastError ?? string.Empty;

    public bool IsHttps => UrlParser.IsHttps(CurrentUrl);
    public bool IsInternalPage => ActiveTab?.IsInternal ?? true;

    public string SecurityGlyph => UrlParser.IsInternal(CurrentUrl) ? "🏠" : IsHttps ? "🔒" : "⚠";
    public string SecurityText => UrlParser.IsInternal(CurrentUrl)
        ? "внутренняя страница"
        : IsHttps ? "HTTPS — защищено" : "HTTP — не защищено";

    public string AddressText
    {
        get => _addressText;
        set
        {
            if (!Set(ref _addressText, value)) return;
            if (_isAddressEditing) UpdateSuggestions(value);
        }
    }

    public bool IsAddressEditing
    {
        get => _isAddressEditing;
        set
        {
            if (!Set(ref _isAddressEditing, value)) return;
            if (!value) Suggestions.Clear();
            else UpdateSuggestions(AddressText);
            Raise(nameof(ShowSuggestions));
        }
    }

    public ObservableCollection<SuggestionItem> Suggestions { get; } = new();
    public bool HasSuggestions => Suggestions.Count > 0;
    public bool ShowSuggestions => IsAddressEditing && HasSuggestions;

    public string StatusMessage { get => _statusMessage; set => Set(ref _statusMessage, value); }

    public bool IsAiOpen
    {
        get => _isAiOpen;
        set
        {
            if (!Set(ref _isAiOpen, value)) return;
            _shell?.SetAiPanelVisible(value);
            Raise(nameof(AiToggleGlyph));
        }
    }

    public string AiToggleGlyph => IsAiOpen ? "◀" : "✦";

    public bool IsFindVisible
    {
        get => _isFindVisible;
        set => Set(ref _isFindVisible, value);
    }

    public string FindText
    {
        get => _findText;
        set => Set(ref _findText, value);
    }

    public string CurrentInternalKey
    {
        get => _currentInternalKey;
        private set => Set(ref _currentInternalKey, value);
    }

    public bool RuntimeMissing
    {
        get => _runtimeMissing;
        private set => Set(ref _runtimeMissing, value);
    }

    public string RuntimeStatus
    {
        get => _runtimeStatus;
        private set => Set(ref _runtimeStatus, value);
    }

    public string RuntimeVersion => WebViews.RuntimeVersion;

    public string AdBlockText
    {
        get
        {
            if (!Settings.AdBlockEnabled) return "AdBlock выключен";
            return $"AdBlock: {AdBlock.BlockedCount} заблокировано";
        }
    }

    public bool HasDownloads => Downloads.Items.Count > 0;
    public string DownloadsText => Downloads.Items.Any(d => d.IsActive)
        ? $"Загрузки: {Downloads.Items.Count(d => d.IsActive)} активных"
        : "Загрузки";

    public string TabsSummary => Tabs.Count == 1 ? "1 вкладка" : $"{Tabs.Count} вкладок";
    public bool ShowBookmarksBar => Settings.ShowBookmarksBar;
    public string ProfileGlyph => Settings.ProfileName.Length > 0 ? Settings.ProfileName[..1].ToUpperInvariant() : "D";
    public string ProfileName => Settings.ProfileName;
    public string ZoomText => $"{Settings.ZoomLevel * 100:0}%";

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    public async Task InitializeAsync(IBrowserShell shell)
    {
        _shell = shell;

        await History.LoadAsync().ConfigureAwait(true);
        await Bookmarks.LoadAsync().ConfigureAwait(true);
        await Passwords.LoadAsync().ConfigureAwait(true);
        await AdBlock.LoadAsync(Settings.AdBlockCustomFilters).ConfigureAwait(true);
        AdBlock.Configure(Settings.AdBlockEnabled, Settings.AdBlockWhitelist);
        await NewTab.LoadAsync().ConfigureAwait(true);

        var ok = await WebViews.InitializeAsync().ConfigureAwait(true);
        RuntimeMissing = !ok;
        RuntimeStatus = ok
            ? $"WebView2 Runtime {WebViews.RuntimeVersion} · Chromium-движок готов"
            : "Microsoft Edge WebView2 Runtime не найден. Установите его: https://go.microsoft.com/fwlink/p/?LinkId=2124703";

        RestoreOrCreateTabs();
        StartSuspendTimer();

        Raise(nameof(TabsSummary), nameof(AdBlockText), nameof(RuntimeStatus), nameof(RuntimeVersion));
        _log.Info("Initialisation complete");
    }

    private void RestoreOrCreateTabs()
    {
        if (!IsPrivate && Settings.RestoreSession)
        {
            var session = _session.Load();
            if (session is { Tabs.Count: > 0 })
            {
                foreach (var saved in session.Tabs)
                {
                    if (string.IsNullOrWhiteSpace(saved.Url)) continue;
                    var kind = UrlParser.IsInternal(saved.Url)
                        ? KindFromUrl(saved.Url)
                        : TabKind.Web;

                    var tab = TabManager.Create(saved.Url, kind, activate: false, title: saved.Title);
                    tab.IsPinned = saved.IsPinned;
                }

                var active = Math.Clamp(session.ActiveIndex, 0, Math.Max(0, Tabs.Count - 1));
                TabManager.ActiveTab = Tabs.Count > 0 ? Tabs[active] : null;
                if (Tabs.Count > 0)
                {
                    OnActiveTabChanged();
                    return;
                }
            }
        }

        TabManager.CreateNewTabPage();
        OnActiveTabChanged();
    }

    private static TabKind KindFromUrl(string url) => UrlParser.InternalPageKey(url) switch
    {
        "settings" => TabKind.Settings,
        "history" => TabKind.History,
        "downloads" => TabKind.Downloads,
        "bookmarks" => TabKind.Bookmarks,
        "passwords" => TabKind.Passwords,
        _ => TabKind.NewTab
    };

    private void StartSuspendTimer()
    {
        _suspendTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
        _suspendTimer.Tick += async (_, _) =>
        {
            if (Settings.TabSuspendMinutes <= 0) return;
            foreach (var tab in Tabs.Where(t => t.IsWeb && !ReferenceEquals(t, ActiveTab) && !t.IsSuspended).ToList())
            {
                if (DateTime.Now - tab.LastActivatedAt < TimeSpan.FromMinutes(Settings.TabSuspendMinutes)) continue;
                if (await tab.TrySuspendAsync().ConfigureAwait(true))
                    _log.Info($"tab suspended (background): {tab.DisplayTitle}");
            }
        };
        _suspendTimer.Start();
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    public BrowserTab CreateWebTab(string url, bool activate = true)
    {
        var kind = UrlParser.IsInternal(url) ? KindFromUrl(url) : TabKind.Web;
        var tab = TabManager.Create(url, kind, activate, kind == TabKind.Web ? null : UrlParser.InternalPageTitle(url));
        Raise(nameof(TabsSummary));

        if (activate) NavigateActiveTab(url);
        return tab;
    }

    public BrowserTab CreateNewTabPage()
    {
        var tab = TabManager.CreateNewTabPage();
        Raise(nameof(TabsSummary));
        OnActiveTabChanged();
        return tab;
    }

    public void CloseTab(BrowserTab? tab)
    {
        tab ??= ActiveTab;
        if (tab is null) return;

        var wasActive = ReferenceEquals(tab, ActiveTab);
        TabManager.Close(tab);
        Raise(nameof(TabsSummary));

        if (Tabs.Count == 0)
        {
            TabManager.CreateNewTabPage();
        }

        if (wasActive) OnActiveTabChanged();
        else Raise(nameof(TabsSummary));
    }

    public void ReopenClosedTab()
    {
        var tab = TabManager.ReopenClosed();
        if (tab is null)
        {
            StatusMessage = "Нет закрытых вкладок для восстановления.";
            return;
        }

        Raise(nameof(TabsSummary));
        if (tab.Kind == TabKind.Web) NavigateActiveTab(tab.Url);
        else OnActiveTabChanged();
    }

    public void DuplicateTab(BrowserTab? tab)
    {
        tab ??= ActiveTab;
        if (tab is null) return;
        CreateWebTab(tab.Url);
    }

    public void TogglePin(BrowserTab? tab)
    {
        tab ??= ActiveTab;
        if (tab is null) return;
        TabManager.TogglePin(tab);
        StatusMessage = tab.IsPinned ? $"«{tab.DisplayTitle}» закреплена" : $"«{tab.DisplayTitle}» откреплена";
    }

    public void ToggleMute(BrowserTab? tab)
    {
        tab ??= ActiveTab;
        if (tab is null) return;

        try
        {
            if (tab.Core is not null)
            {
                tab.Core.IsMuted = !tab.Core.IsMuted;
                tab.IsMuted = tab.Core.IsMuted;
                StatusMessage = tab.IsMuted ? "Звук вкладки выключен" : "Звук вкладки включён";
            }
        }
        catch (Exception ex)
        {
            _log.Error("mute failed", ex);
        }
    }

    public void SelectTab(BrowserTab tab)
    {
        if (tab is null) return;
        TabManager.ActiveTab = tab;
    }

    public void MoveTab(int from, int to)
    {
        if (from < 0 || from >= Tabs.Count || to < 0 || to >= Tabs.Count || from == to) return;
        Tabs.Move(from, to);
        _log.Info($"tab moved {from} -> {to}");
    }

    public void NextTab(int direction)
    {
        var next = TabManager.NextOf(ActiveTab ?? Tabs.FirstOrDefault(), direction);
        if (next is not null) TabManager.ActiveTab = next;
    }

    private void OnActiveTabChanged()
    {
        var tab = ActiveTab;
        if (tab is null) return;

        tab.LastActivatedAt = DateTime.Now;
        _ = tab.ResumeAsync();

        if (tab.IsInternal)
        {
            CurrentInternalKey = UrlParser.InternalPageKey(tab.Url);
            _shell?.ShowInternalPage(CurrentInternalKey);
        }
        else
        {
            _shell?.ShowWebContent();
            if (!tab.IsWebViewReady && _shell is not null && !RuntimeMissing)
                _ = AttachTabViewAsync(tab);
        }

        AddressText = tab.Url;
        Raise(nameof(WindowTitle), nameof(CurrentUrl), nameof(PageTitle), nameof(CanGoBack), nameof(CanGoForward),
            nameof(IsLoading), nameof(IsHttps), nameof(IsInternalPage), nameof(SecurityGlyph), nameof(SecurityText),
            nameof(HasError), nameof(LastError), nameof(TabsSummary));
    }

    private async Task AttachTabViewAsync(BrowserTab tab)
    {
        if (_shell is null) return;
        try
        {
            await WebViews.AttachAsync(tab, _shell.WebHost).ConfigureAwait(true);
            if (ReferenceEquals(tab, ActiveTab))
            {
                ApplyZoom();
                _shell.ShowWebContent();
            }
        }
        catch (Exception ex)
        {
            _log.Error("attach tab view failed", ex);
            tab.LastError = "Не удалось создать WebView2: " + ex.Message;
            Raise(nameof(HasError), nameof(LastError));
        }
    }

    private void OnTabStateChanged(BrowserTab tab)
    {
        if (tab.IsWeb && !tab.IsWebViewReady && _shell is not null && !RuntimeMissing && string.IsNullOrEmpty(tab.LastError))
            _ = AttachTabViewAsync(tab);

        if (!ReferenceEquals(tab, ActiveTab)) return;

        AddressText = tab.Url;
        Raise(nameof(WindowTitle), nameof(CurrentUrl), nameof(PageTitle), nameof(CanGoBack), nameof(CanGoForward),
            nameof(IsLoading), nameof(IsHttps), nameof(IsInternalPage), nameof(SecurityGlyph), nameof(SecurityText),
            nameof(HasError), nameof(LastError));
    }

    private void OnNavigationFinished(BrowserTab tab)
    {
        if (tab.IsInternal) return;

        var key = tab.Id + "|" + tab.Url;
        if (_lastRecorded.TryGetValue(tab.Id, out var last) && last == tab.Url) return;

        _lastRecorded[tab.Id] = tab.Url;
        _ = History.RecordAsync(tab.Url, tab.DisplayTitle, Settings.SaveHistory && !IsPrivate);
        _ = NewTab.RefreshRecentAsync();
        Raise(nameof(AdBlockText));
        _ = key;
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    public async void NavigateActiveTab(string input)
    {
        var tab = ActiveTab ?? CreateNewTabPage();
        await NavigateTabAsync(tab, input).ConfigureAwait(true);
    }

    public async Task NavigateTabAsync(BrowserTab tab, string input)
    {
        var url = UrlParser.Resolve(input, Settings);
        AddressText = url;
        IsAddressEditing = false;

        if (UrlParser.IsInternal(url))
        {
            OpenInternalInTab(tab, url);
            return;
        }

        tab.Kind = TabKind.Web;
        tab.Url = url;
        tab.PendingUrl = url;
        _shell?.ShowWebContent();

        if (RuntimeMissing)
        {
            StatusMessage = "WebView2 Runtime не установлен — сайты недоступны. Установите Runtime и перезапустите DrBrowser.";
            return;
        }

        if (tab.View is null)
        {
            await AttachTabViewAsync(tab).ConfigureAwait(true);
        }
        else
        {
            try
            {
                await tab.ResumeAsync().ConfigureAwait(true);
                tab.Core?.Navigate(url);
            }
            catch (Exception ex)
            {
                _log.Error("navigate failed", ex);
                tab.LastError = "Не удалось перейти по адресу: " + ex.Message;
            }
        }

        Raise(nameof(CurrentUrl), nameof(WindowTitle), nameof(IsInternalPage), nameof(SecurityGlyph), nameof(SecurityText),
            nameof(IsHttps), nameof(CanGoBack));
    }

    public void OpenInternalInTab(BrowserTab tab, string url)
    {
        var key = UrlParser.InternalPageKey(url);
        if (string.IsNullOrEmpty(key)) key = "newtab";
        url = "drbrowser://" + key;

        tab.Kind = KindFromUrl(url);
        tab.Url = url;
        tab.Title = UrlParser.InternalPageTitle(url);
        tab.PendingUrl = null;
        tab.LastError = string.Empty;

        CurrentInternalKey = key;
        AddressText = url;
        _shell?.ShowInternalPage(key);
        Raise(nameof(CurrentUrl), nameof(WindowTitle), nameof(IsInternalPage), nameof(SecurityGlyph), nameof(SecurityText));
    }

    public void GoBack()
    {
        try
        {
            if (ActiveTab?.Core?.CanGoBack == true) ActiveTab.Core.GoBack();
        }
        catch (Exception ex)
        {
            _log.Error("go back failed", ex);
        }
    }

    public void GoForward()
    {
        try
        {
            if (ActiveTab?.Core?.CanGoForward == true) ActiveTab.Core.GoForward();
        }
        catch (Exception ex)
        {
            _log.Error("go forward failed", ex);
        }
    }

    public void Reload()
    {
        try
        {
            if (ActiveTab is not { } tab) return;
            if (tab.IsInternal)
            {
                OpenInternalInTab(tab, tab.Url);
                return;
            }

            if (tab.Core is null) _ = NavigateTabAsync(tab, tab.Url);
            else tab.Core.Reload();
        }
        catch (Exception ex)
        {
            _log.Error("reload failed", ex);
        }
    }

    public void StopLoading()
    {
        try
        {
            ActiveTab?.Core?.Stop();
        }
        catch
        {
            // ignore
        }
    }

    public void GoHome() => NavigateActiveTab(Settings.HomePage);

    public void FocusAddressBar()
    {
        IsAddressEditing = true;
        _shell?.FocusAddressBar();
        _shell?.SelectAddressBarText();
    }

    private void UpdateSuggestions(string text)
    {
        Suggestions.Clear();
        if (string.IsNullOrWhiteSpace(text) || text.Length < 2)
        {
            Raise(nameof(HasSuggestions));
            return;
        }

        foreach (var bookmark in Bookmarks.Suggest(text))
            Suggestions.Add(new SuggestionItem(bookmark, "bookmark"));

        foreach (var history in History.Suggest(text))
        {
            if (Suggestions.Count >= 8) break;
            Suggestions.Add(new SuggestionItem(history, "history"));
        }

        if (UrlParser.LooksLikeUrl(text))
            Suggestions.Add(new SuggestionItem(text, "url"));

        if (Suggestions.Count < 8)
            Suggestions.Add(new SuggestionItem($"{Settings.SearchEngine}: {text}", "search"));

        Raise(nameof(HasSuggestions), nameof(ShowSuggestions));
    }

    public void UseSuggestion(SuggestionItem item)
    {
        var target = item.Url;
        if (item.Kind == "bookmark" || item.Kind == "history")
        {
            var parts = item.Url.Split(" — ", 2);
            if (parts.Length == 2 && Uri.TryCreate(parts[1], UriKind.Absolute, out _)) target = parts[1];
        }

        NavigateActiveTab(target);
    }

    // ------------------------------------------------------------------
    // Settings side effects
    // ------------------------------------------------------------------

    private void OnSettingsChanged()
    {
        AdBlock.Configure(Settings.AdBlockEnabled, Settings.AdBlockWhitelist);
        ApplyZoom();
        ApplyTheme();
        Raise(nameof(AdBlockText), nameof(ShowBookmarksBar), nameof(ZoomText), nameof(ProfileGlyph), nameof(ProfileName));
        ScheduleSettingsSave();
    }

    private DateTime _saveScheduledAt;
    private bool _saveScheduled;
    private DispatcherTimer? _saveTimer;

    private void ScheduleSettingsSave()
    {
        _saveScheduled = true;
        _saveScheduledAt = DateTime.UtcNow;

        if (_saveTimer is not null) return;
        _saveTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(900) };
        _saveTimer.Tick += (_, _) =>
        {
            if (!_saveScheduled) return;
            if ((DateTime.UtcNow - _saveScheduledAt).TotalMilliseconds < 800) return;
            FlushSettings();
        };
        _saveTimer.Start();
    }

    public void FlushSettings()
    {
        if (_saveScheduled) _settingsService.Save(Settings);
        _saveScheduled = false;
    }

    public void ApplyZoom()
    {
        try
        {
            if (ActiveTab?.View is not null) ActiveTab.View.ZoomFactor = Settings.ZoomLevel;
        }
        catch
        {
            // ignore
        }

        Raise(nameof(ZoomText));
    }

    public void ZoomBy(double delta)
    {
        Settings.ZoomLevel = Math.Clamp(Settings.ZoomLevel + delta, 0.25, 3.0);
        ApplyZoom();
        FlushSettings();
    }

    public void ResetZoom()
    {
        Settings.ZoomLevel = 1.0;
        ApplyZoom();
        FlushSettings();
    }

    private void ApplyTheme() => ThemeManager.Apply(Settings);

    // ------------------------------------------------------------------
    // Shutdown
    // ------------------------------------------------------------------

    public void SaveSession()
    {
        if (IsPrivate) return;

        try
        {
            var tabs = Tabs
                .Where(t => !string.IsNullOrWhiteSpace(t.Url))
                .Select(t => new SessionTab { Url = t.Url, Title = t.DisplayTitle, IsPinned = t.IsPinned })
                .ToList();

            var index = ActiveTab is null ? 0 : Math.Max(0, Tabs.IndexOf(ActiveTab));
            _session.Save(tabs, index);
        }
        catch (Exception ex)
        {
            _log.Error("session save failed", ex);
        }
    }

    public void Shutdown()
    {
        if (_disposed) return;
        _disposed = true;

        try
        {
            _suspendTimer?.Stop();
            _saveTimer?.Stop();
            if (!IsPrivate) SaveSession();
            FlushSettings();
            Downloads.Shutdown();
            TabManager.DisposeAll();
            WebViews.Dispose();
            _log.Info("shutdown complete");
        }
        catch (Exception ex)
        {
            _log.Error("shutdown failed", ex);
        }
    }

    public void Dispose() => Shutdown();
}

public sealed class SuggestionItem
{
    public SuggestionItem(string url, string kind)
    {
        Url = url;
        Kind = kind;
        Title = kind switch
        {
            "bookmark" => "★ " + url,
            "history" => "🕘 " + url,
            "search" => "🔍 " + url,
            _ => "🌐 " + url
        };
    }

    public string Url { get; }
    public string Kind { get; }
    public string Title { get; }
}
