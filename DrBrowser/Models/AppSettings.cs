namespace DrBrowser;

/// <summary>Everything the browser persists between releases (JSON, %AppData%\DrBrowser\settings.json).</summary>
public sealed class AppSettings : ObservableObject
{
    public const string AppName = "DrBrowser";
    public const string Version = "3.0.0";
    public const string AppVersionText = "DrBrowser OMEGA 3.0.0";

    public static readonly (string Name, string Template)[] SearchEngines =
    {
        ("Google", "https://www.google.com/search?q={0}"),
        ("Яндекс", "https://yandex.ru/search/?text={0}"),
        ("Bing", "https://www.bing.com/search?q={0}"),
        ("DuckDuckGo", "https://duckduckgo.com/?q={0}"),
        ("DrSearch", "drbrowser://search?q={0}")
    };

    private string _theme = "Omega Dark";
    private string _accentHex = "#8B5CF6";
    private string _searchEngine = "DrSearch";
    private string _homePage = "drbrowser://search";
    private bool _restoreSession = true;
    private bool _saveHistory = true;
    private bool _adBlockEnabled = true;
    private string _adBlockCustomFilters = string.Empty;
    private string _adBlockUpdateUrl = "https://easylist.to/easylist/easylist.txt";
    private List<string> _adBlockWhitelist = new();
    private string _aiBaseUrl = "https://api.openai.com/v1";
    private string _aiModel = "gpt-4o-mini";
    private string _downloadsFolder = string.Empty;
    private bool _askForDownloadLocation;
    private double _zoomLevel = 1.0;
    private int _tabSuspendMinutes = 10;
    private string _wallpaperPath = string.Empty;
    private bool _showBookmarksBar = true;
    private string _profileName = "Default";
    private string _userDataFolder = string.Empty;
    private bool _readerModeDark = true;
    private bool _notificationsEnabled = true;
    private double _weatherLatitude = 50.45;
    private double _weatherLongitude = 30.52;
    private string _weatherCity = "Киев";
    private bool _showWeather = true;
    private string _newTabGreeting = "Добро пожаловать в DrBrowser";
    private string _musicFolder = string.Empty;
    private double _musicVolume = 0.8;
    private bool _drSearchLocalOnly;
    private string _avatarPath = string.Empty;
    private string _avatarStyle = "Aurora";
    private string _aiPersona = "chat";
    private bool _aiIncludePage = true;
    private List<string> _drSearchRecent = new();
    private bool _drSearchWeb = true;
    private bool _drGuardEnabled = true;
    private bool _drGuardScanDownloads = true;
    private bool _drGuardWarnOnHttp = true;
    private List<string> _drGuardBlockedHosts = new();
    private List<string> _drGuardExemptions = new();
    private string _musicSource = "iTunes";
    private string _aiProvider = "Local";
    private string _ollamaUrl = "http://127.0.0.1:11434";
    private string _ollamaModel = "llama3.2";
    private double _aiTemperature = 0.4;
    private int _aiContextSize = 8192;
    private bool _aiUseSearch;
    private string _tabSleepMode = "Balanced";
    private int _searchCrawlerMaxPages = 25;
    private int _searchCrawlerMaxDepth = 2;
    private int _searchCrawlerDelayMs = 1200;
    private List<string> _searchAllowedDomains = new();
    private List<string> _searchBlockedDomains = new();
    private bool _reducedAnimations;

    public string Theme { get => _theme; set { if (Set(ref _theme, value)) OnPropertyChanged(nameof(IsDarkTheme)); } }
    public bool IsDarkTheme => !string.Equals(Theme, "Omega Light", StringComparison.OrdinalIgnoreCase);
    public string AccentHex { get => _accentHex; set => Set(ref _accentHex, value); }
    public string SearchEngine { get => _searchEngine; set => Set(ref _searchEngine, value); }
    public string HomePage { get => _homePage; set => Set(ref _homePage, value); }
    public bool RestoreSession { get => _restoreSession; set => Set(ref _restoreSession, value); }
    public bool SaveHistory { get => _saveHistory; set => Set(ref _saveHistory, value); }
    public bool AdBlockEnabled { get => _adBlockEnabled; set => Set(ref _adBlockEnabled, value); }
    public string AdBlockCustomFilters { get => _adBlockCustomFilters; set => Set(ref _adBlockCustomFilters, value); }
    public string AdBlockUpdateUrl { get => _adBlockUpdateUrl; set => Set(ref _adBlockUpdateUrl, value); }
    public List<string> AdBlockWhitelist { get => _adBlockWhitelist; set => Set(ref _adBlockWhitelist, value ?? new()); }
    public string AiBaseUrl { get => _aiBaseUrl; set => Set(ref _aiBaseUrl, value); }
    public string AiModel { get => _aiModel; set => Set(ref _aiModel, value); }
    public string DownloadsFolder { get => _downloadsFolder; set => Set(ref _downloadsFolder, value); }
    public bool AskForDownloadLocation { get => _askForDownloadLocation; set => Set(ref _askForDownloadLocation, value); }
    public double ZoomLevel { get => _zoomLevel; set => Set(ref _zoomLevel, Math.Clamp(value, 0.25, 3.0)); }
    public int TabSuspendMinutes { get => _tabSuspendMinutes; set => Set(ref _tabSuspendMinutes, Math.Clamp(value, 1, 240)); }
    public string WallpaperPath { get => _wallpaperPath; set => Set(ref _wallpaperPath, value ?? string.Empty); }
    public bool ShowBookmarksBar { get => _showBookmarksBar; set => Set(ref _showBookmarksBar, value); }
    public string ProfileName { get => _profileName; set => Set(ref _profileName, value); }
    public string UserDataFolder { get => _userDataFolder; set => Set(ref _userDataFolder, value ?? string.Empty); }
    public bool ReaderModeDark { get => _readerModeDark; set => Set(ref _readerModeDark, value); }
    public bool NotificationsEnabled { get => _notificationsEnabled; set => Set(ref _notificationsEnabled, value); }
    public double WeatherLatitude { get => _weatherLatitude; set => Set(ref _weatherLatitude, value); }
    public double WeatherLongitude { get => _weatherLongitude; set => Set(ref _weatherLongitude, value); }
    public string WeatherCity { get => _weatherCity; set => Set(ref _weatherCity, value); }
    public bool ShowWeather { get => _showWeather; set => Set(ref _showWeather, value); }
    public string NewTabGreeting { get => _newTabGreeting; set => Set(ref _newTabGreeting, value); }

    /// <summary>Local folder the Music section reads (never uploaded anywhere).</summary>
    public string MusicFolder { get => _musicFolder; set => Set(ref _musicFolder, value ?? string.Empty); }
    public double MusicVolume { get => _musicVolume; set => Set(ref _musicVolume, Math.Clamp(value, 0, 1)); }

    /// <summary>DrSearch: when true the query is only matched locally and never sent to the web.</summary>
    public bool DrSearchLocalOnly { get => _drSearchLocalOnly; set => Set(ref _drSearchLocalOnly, value); }

    /// <summary>Optional custom profile picture (absolute path to an image).</summary>
    public string AvatarPath { get => _avatarPath; set => Set(ref _avatarPath, value ?? string.Empty); }

    /// <summary>Gradient preset for the built-in avatar: Aurora / Nebula / Sunset / Ember / Matrix / Mono.</summary>
    public string AvatarStyle { get => _avatarStyle; set => Set(ref _avatarStyle, string.IsNullOrWhiteSpace(value) ? "Aurora" : value); }

    /// <summary>DrMind mode: chat/search/research/coder/study/writing/translator/browser/music.</summary>
    public string AiPersona { get => _aiPersona; set => Set(ref _aiPersona, string.IsNullOrWhiteSpace(value) ? "chat" : value); }

    /// <summary>When true DrAI may read the active page text as extra context.</summary>
    public bool AiIncludePage { get => _aiIncludePage; set => Set(ref _aiIncludePage, value); }

    /// <summary>Last DrSearch queries (kept locally, never uploaded).</summary>
    public List<string> DrSearchRecent { get => _drSearchRecent; set => Set(ref _drSearchRecent, value ?? new List<string>()); }

    /// <summary>DrSearch also asks the public web endpoint for results (own results page).</summary>
    public bool DrSearchWebEnabled { get => _drSearchWeb; set => Set(ref _drSearchWeb, value); }

    /// <summary>DrGuard: URL reputation checks + download scanning.</summary>
    public bool DrGuardEnabled { get => _drGuardEnabled; set => Set(ref _drGuardEnabled, value); }
    public bool DrGuardScanDownloads { get => _drGuardScanDownloads; set => Set(ref _drGuardScanDownloads, value); }
    public bool DrGuardWarnOnHttp { get => _drGuardWarnOnHttp; set => Set(ref _drGuardWarnOnHttp, value); }
    public List<string> DrGuardBlockedHosts { get => _drGuardBlockedHosts; set => Set(ref _drGuardBlockedHosts, value ?? new List<string>()); }
    public List<string> DrGuardExemptions { get => _drGuardExemptions; set => Set(ref _drGuardExemptions, value ?? new List<string>()); }

    /// <summary>Source used by the online music search: iTunes or Deezer.</summary>
    public string MusicSource { get => _musicSource; set => Set(ref _musicSource, string.IsNullOrWhiteSpace(value) ? "iTunes" : value); }

    /// <summary>DrMind provider: Local, Ollama or OpenAI-compatible.</summary>
    public string AiProvider { get => _aiProvider; set => Set(ref _aiProvider, string.IsNullOrWhiteSpace(value) ? "Local" : value); }
    public string OllamaUrl { get => _ollamaUrl; set => Set(ref _ollamaUrl, value?.Trim() ?? string.Empty); }
    public string OllamaModel { get => _ollamaModel; set => Set(ref _ollamaModel, value?.Trim() ?? string.Empty); }
    public double AiTemperature { get => _aiTemperature; set => Set(ref _aiTemperature, Math.Clamp(value, 0, 2)); }
    public int AiContextSize { get => _aiContextSize; set => Set(ref _aiContextSize, Math.Clamp(value, 1024, 131072)); }
    public bool AiUseSearch { get => _aiUseSearch; set => Set(ref _aiUseSearch, value); }

    /// <summary>Tab Sleep preset: Off, Balanced or Aggressive.</summary>
    public string TabSleepMode { get => _tabSleepMode; set => Set(ref _tabSleepMode, string.IsNullOrWhiteSpace(value) ? "Balanced" : value); }

    public int SearchCrawlerMaxPages { get => _searchCrawlerMaxPages; set => Set(ref _searchCrawlerMaxPages, Math.Clamp(value, 1, 500)); }
    public int SearchCrawlerMaxDepth { get => _searchCrawlerMaxDepth; set => Set(ref _searchCrawlerMaxDepth, Math.Clamp(value, 0, 5)); }
    public int SearchCrawlerDelayMs { get => _searchCrawlerDelayMs; set => Set(ref _searchCrawlerDelayMs, Math.Clamp(value, 500, 60000)); }
    public List<string> SearchAllowedDomains { get => _searchAllowedDomains; set => Set(ref _searchAllowedDomains, value ?? new List<string>()); }
    public List<string> SearchBlockedDomains { get => _searchBlockedDomains; set => Set(ref _searchBlockedDomains, value ?? new List<string>()); }
    public bool ReducedAnimations { get => _reducedAnimations; set => Set(ref _reducedAnimations, value); }

    /// <summary>Normalises values that may come from an old or hand-edited config file.</summary>
    public void Normalize()
    {
        if (string.Equals(Theme, "Dark", StringComparison.OrdinalIgnoreCase)) Theme = "Omega Dark";
        if (string.Equals(Theme, "Light", StringComparison.OrdinalIgnoreCase)) Theme = "Omega Light";
        if (string.IsNullOrWhiteSpace(Theme)) Theme = "Omega Dark";
        if (!ThemeManager.ThemeNames.Contains(Theme, StringComparer.OrdinalIgnoreCase)) Theme = "Omega Dark";
        if (string.IsNullOrWhiteSpace(SearchEngine) || !SearchEngines.Any(s => s.Name == SearchEngine)) SearchEngine = "DrSearch";
        if (string.IsNullOrWhiteSpace(HomePage)) HomePage = "drbrowser://search";
        if (string.IsNullOrWhiteSpace(AiBaseUrl)) AiBaseUrl = "https://api.openai.com/v1";
        if (string.IsNullOrWhiteSpace(AiModel)) AiModel = "gpt-4o-mini";
        if (string.IsNullOrWhiteSpace(DownloadsFolder)) DownloadsFolder = AppPaths.DefaultDownloadsFolder;
        if (string.IsNullOrWhiteSpace(ProfileName)) ProfileName = "Default";
        _adBlockWhitelist ??= new List<string>();
        _drSearchRecent ??= new List<string>();
        if (DrSearchRecent.Count > 12) DrSearchRecent = DrSearchRecent.Take(12).ToList();
        DrGuardBlockedHosts ??= new List<string>();
        DrGuardExemptions ??= new List<string>();
        if (string.IsNullOrWhiteSpace(MusicSource)) MusicSource = "iTunes";
        if (string.IsNullOrWhiteSpace(AvatarStyle)) AvatarStyle = "Aurora";
        if (string.IsNullOrWhiteSpace(AiPersona)) AiPersona = "chat";
        if (AiProvider is not ("Local" or "Ollama" or "OpenAI")) AiProvider = "Local";
        if (string.IsNullOrWhiteSpace(OllamaUrl)) OllamaUrl = "http://127.0.0.1:11434";
        if (string.IsNullOrWhiteSpace(OllamaModel)) OllamaModel = "llama3.2";
        if (TabSleepMode is not ("Off" or "Balanced" or "Aggressive")) TabSleepMode = "Balanced";
        SearchAllowedDomains ??= new List<string>();
        SearchBlockedDomains ??= new List<string>();
        AiTemperature = Math.Clamp(AiTemperature, 0, 2);
        AiContextSize = Math.Clamp(AiContextSize, 1024, 131072);
        SearchCrawlerMaxPages = Math.Clamp(SearchCrawlerMaxPages, 1, 500);
        SearchCrawlerMaxDepth = Math.Clamp(SearchCrawlerMaxDepth, 0, 5);
        SearchCrawlerDelayMs = Math.Clamp(SearchCrawlerDelayMs, 500, 60000);
        ZoomLevel = Math.Clamp(ZoomLevel, 0.25, 3.0);
        TabSuspendMinutes = Math.Clamp(TabSuspendMinutes, 1, 240);
    }

    /// <summary>Search engine used for the toolbar/address bar (DrSearch renders its own page).</summary>
    public string SearchUrl(string query)
    {
        var template = SearchEngines.FirstOrDefault(s => s.Name == SearchEngine).Template
                       ?? SearchEngines[0].Template;
        return string.Format(template, Uri.EscapeDataString(query));
    }
}
