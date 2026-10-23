namespace DrBrowser;

/// <summary>Everything the browser persists between releases (JSON, %AppData%\DrBrowser\settings.json).</summary>
public sealed class AppSettings : ObservableObject
{
    public const string AppName = "DrBrowser";
    public const string Version = "2.0.0";
    public const string AppVersionText = "DrBrowser 2.0.0";

    public static readonly (string Name, string Template)[] SearchEngines =
    {
        ("Google", "https://www.google.com/search?q={0}"),
        ("Яндекс", "https://yandex.ru/search/?text={0}"),
        ("Bing", "https://www.bing.com/search?q={0}"),
        ("DuckDuckGo", "https://duckduckgo.com/?q={0}"),
        ("DrSearch", "drbrowser://search?q={0}")
    };

    private string _theme = "Dark";
    private string _accentHex = "#7C5CFF";
    private string _searchEngine = "DrSearch";
    private string _homePage = "https://www.google.com";
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
    private string _aiPersona = "assistant";
    private bool _aiIncludePage = true;
    private List<string> _drSearchRecent = new();

    public string Theme { get => _theme; set { if (Set(ref _theme, value)) OnPropertyChanged(nameof(IsDarkTheme)); } }
    public bool IsDarkTheme => !string.Equals(Theme, "Light", StringComparison.OrdinalIgnoreCase);
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

    /// <summary>DrAI persona: assistant / coder / translator / brief.</summary>
    public string AiPersona { get => _aiPersona; set => Set(ref _aiPersona, string.IsNullOrWhiteSpace(value) ? "assistant" : value); }

    /// <summary>When true DrAI may read the active page text as extra context.</summary>
    public bool AiIncludePage { get => _aiIncludePage; set => Set(ref _aiIncludePage, value); }

    /// <summary>Last DrSearch queries (kept locally, never uploaded).</summary>
    public List<string> DrSearchRecent { get => _drSearchRecent; set => Set(ref _drSearchRecent, value ?? new List<string>()); }

    /// <summary>Normalises values that may come from an old or hand-edited config file.</summary>
    public void Normalize()
    {
        if (string.IsNullOrWhiteSpace(Theme)) Theme = "Dark";
        if (string.IsNullOrWhiteSpace(SearchEngine) || !SearchEngines.Any(s => s.Name == SearchEngine)) SearchEngine = "DrSearch";
        if (string.IsNullOrWhiteSpace(HomePage)) HomePage = "https://www.google.com";
        if (string.IsNullOrWhiteSpace(AiBaseUrl)) AiBaseUrl = "https://api.openai.com/v1";
        if (string.IsNullOrWhiteSpace(AiModel)) AiModel = "gpt-4o-mini";
        if (string.IsNullOrWhiteSpace(DownloadsFolder)) DownloadsFolder = AppPaths.DefaultDownloadsFolder;
        if (string.IsNullOrWhiteSpace(ProfileName)) ProfileName = "Default";
        _adBlockWhitelist ??= new List<string>();
        _drSearchRecent ??= new List<string>();
        if (DrSearchRecent.Count > 12) DrSearchRecent = DrSearchRecent.Take(12).ToList();
        if (string.IsNullOrWhiteSpace(AvatarStyle)) AvatarStyle = "Aurora";
        if (string.IsNullOrWhiteSpace(AiPersona)) AiPersona = "assistant";
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
