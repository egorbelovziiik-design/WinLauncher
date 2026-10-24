using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text.Json.Nodes;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;

namespace DrBrowser;

/// <summary>Backs the new-tab page: clock, date, weather, quick links, recents, wallpaper.</summary>
public sealed class NewTabViewModel : ObservableObject
{
    private static readonly HttpClient WeatherClient = new() { Timeout = TimeSpan.FromSeconds(12) };

    private readonly Logger _log;
    private readonly Db _db;
    private readonly Func<AppSettings> _settings;
    private readonly DispatcherTimer _clockTimer;
    private readonly DispatcherTimer _weatherTimer;

    private string _clock = DateTime.Now.ToString("HH:mm");
    private string _date = string.Empty;
    private string _weatherText = "Погода загружается…";
    private string _weatherIcon = "🌤";
    private string _weatherDetail = string.Empty;
    private ImageSource? _wallpaper;

    private static readonly (string Title, string Url)[] Defaults =
    {
        ("DrSearch", "drbrowser://search"),
        ("DrBrowser", "https://github.com/egorbelovziiik-design/WinLauncher"),
        ("YouTube", "https://www.youtube.com"),
        ("GitHub", "https://github.com"),
        ("Википедия", "https://ru.wikipedia.org"),
        ("MDN Web Docs", "https://developer.mozilla.org"),
        ("Microsoft Learn", "https://learn.microsoft.com")
    };

    public NewTabViewModel(Logger log, Db db, Func<AppSettings> settings)
    {
        _log = log;
        _db = db;
        _settings = settings;

        _clockTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _clockTimer.Tick += (_, _) => UpdateClock();
        _clockTimer.Start();
        UpdateClock();

        _weatherTimer = new DispatcherTimer { Interval = TimeSpan.FromMinutes(15) };
        _weatherTimer.Tick += async (_, _) => await RefreshWeatherAsync().ConfigureAwait(true);
        _weatherTimer.Start();
    }

    public ObservableCollection<QuickLink> QuickLinks { get; } = new();
    public ObservableCollection<VisitedSite> Recent { get; } = new();

    public string Clock { get => _clock; private set => Set(ref _clock, value); }
    public string DateLine { get => _date; private set => Set(ref _date, value); }
    public string Greeting => _settings().NewTabGreeting;
    public string SearchEngineName => _settings().SearchEngine;

    public string WeatherText { get => _weatherText; private set => Set(ref _weatherText, value); }
    public string WeatherIcon { get => _weatherIcon; private set => Set(ref _weatherIcon, value); }
    public string WeatherDetail { get => _weatherDetail; private set => Set(ref _weatherDetail, value); }
    public bool ShowWeather => _settings().ShowWeather;

    public ImageSource? Wallpaper
    {
        get => _wallpaper;
        private set { if (Set(ref _wallpaper, value)) OnPropertyChanged(nameof(HasWallpaper)); }
    }

    public bool HasWallpaper => _wallpaper is not null;

    private string _newLinkTitle = string.Empty;
    private string _newLinkUrl = string.Empty;

    public string NewQuickLinkTitle { get => _newLinkTitle; set => Set(ref _newLinkTitle, value); }
    public string NewQuickLinkUrl { get => _newLinkUrl; set => Set(ref _newLinkUrl, value); }

    private void UpdateClock()
    {
        Clock = DateTime.Now.ToString("HH:mm");
        DateLine = DateTime.Now.ToString("dddd, d MMMM yyyy",
            new System.Globalization.CultureInfo("ru-RU"));
    }

    public async Task LoadAsync()
    {
        await LoadQuickLinksAsync().ConfigureAwait(true);
        await RefreshRecentAsync().ConfigureAwait(true);
        LoadWallpaper();
        if (ShowWeather) await RefreshWeatherAsync().ConfigureAwait(true);
        else WeatherText = "Погода отключена в настройках";
    }

    public async Task LoadQuickLinksAsync()
    {
        List<QuickLink> links;
        try
        {
            links = await Task.Run(_db.QueryQuickLinks).ConfigureAwait(true);
        }
        catch (Exception ex)
        {
            _log.Error("quick links load failed", ex);
            links = new List<QuickLink>();
        }

        if (links.Count == 0)
        {
            for (var i = 0; i < Defaults.Length; i++)
            {
                var id = _db.AddQuickLink(Defaults[i].Title, Defaults[i].Url, i);
                links.Add(new QuickLink { Id = id, Title = Defaults[i].Title, Url = Defaults[i].Url, SortOrder = i });
            }
        }

        QuickLinks.Clear();
        foreach (var link in links) QuickLinks.Add(link);
    }

    public async Task RefreshRecentAsync()
    {
        List<VisitedSite> recent;
        try
        {
            recent = await Task.Run(() => _db.QueryMostVisited(40)
                .OrderByDescending(v => v.LastVisit)
                .Take(8)
                .ToList()).ConfigureAwait(true);
        }
        catch
        {
            recent = new List<VisitedSite>();
        }

        Recent.Clear();
        foreach (var site in recent) Recent.Add(site);
    }

    public async Task AddQuickLinkAsync(string title, string url)
    {
        if (string.IsNullOrWhiteSpace(url)) return;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return;
        if (uri.Scheme is not ("http" or "https")) return;

        var name = string.IsNullOrWhiteSpace(title) ? uri.Host : title.Trim();
        var id = await Task.Run(() => _db.AddQuickLink(name, uri.ToString(), QuickLinks.Count)).ConfigureAwait(true);
        QuickLinks.Add(new QuickLink { Id = id, Title = name, Url = uri.ToString(), SortOrder = QuickLinks.Count });
    }

    public async Task RemoveQuickLinkAsync(QuickLink link)
    {
        if (link is null) return;
        await Task.Run(() => _db.DeleteQuickLink(link.Id)).ConfigureAwait(true);
        QuickLinks.Remove(link);
    }

    public void LoadWallpaper()
    {
        var path = _settings().WallpaperPath;
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
        {
            Wallpaper = null;
            return;
        }

        try
        {
            var image = new BitmapImage();
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.UriSource = new Uri(path, UriKind.Absolute);
            image.EndInit();
            image.Freeze();
            Wallpaper = image;
        }
        catch (Exception ex)
        {
            _log.Error("wallpaper load failed", ex);
            Wallpaper = null;
        }
    }

    public async Task RefreshWeatherAsync()
    {
        var settings = _settings();
        if (!settings.ShowWeather)
        {
            WeatherText = "Погода отключена в настройках";
            WeatherDetail = string.Empty;
            return;
        }

        try
        {
            var lat = settings.WeatherLatitude.ToString(System.Globalization.CultureInfo.InvariantCulture);
            var lon = settings.WeatherLongitude.ToString(System.Globalization.CultureInfo.InvariantCulture);
            var url = $"https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,weather_code&timezone=auto";
            var json = await WeatherClient.GetStringAsync(url).ConfigureAwait(true);
            var node = JsonNode.Parse(json)?["current"];
            if (node is null)
            {
                WeatherText = "Погода недоступна";
                return;
            }

            var temp = node["temperature_2m"]?.GetValue<double>() ?? 0;
            var code = node["weather_code"]?.GetValue<int>() ?? 0;
            var (icon, text) = DescribeWeather(code);

            WeatherIcon = icon;
            WeatherText = $"{temp:0}°C · {text}";
            WeatherDetail = settings.WeatherCity;
        }
        catch (Exception ex)
        {
            _log.Warn("weather fetch failed: " + ex.Message);
            WeatherText = "Погода недоступна (нет сети)";
            WeatherIcon = "🌐";
            WeatherDetail = settings.WeatherCity;
        }
    }

    private static (string Icon, string Text) DescribeWeather(int code) => code switch
    {
        0 => ("☀️", "ясно"),
        1 or 2 => ("🌤", "малооблачно"),
        3 => ("☁️", "облачно"),
        45 or 48 => ("🌫", "туман"),
        >= 51 and <= 57 => ("🌦", "морось"),
        >= 61 and <= 67 => ("🌧", "дождь"),
        >= 71 and <= 77 => ("❄️", "снег"),
        >= 80 and <= 82 => ("🌧", "ливень"),
        85 or 86 => ("🌨", "снегопад"),
        >= 95 => ("⛈", "гроза"),
        _ => ("🌡", "погода")
    };

    public void Shutdown()
    {
        _clockTimer.Stop();
        _weatherTimer.Stop();
    }
}
