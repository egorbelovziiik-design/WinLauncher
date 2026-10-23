using System.IO;
using System.Windows.Input;

namespace DrBrowser;

public sealed partial class MainViewModel
{
    /// <summary>Loads whatever the requested internal page needs before it is shown.</summary>
    public async Task LoadPageDataAsync(string key)
    {
        try
        {
            switch (key)
            {
                case "history":
                    await History.LoadAsync();
                    break;
                case "bookmarks":
                    await Bookmarks.LoadAsync();
                    break;
                case "passwords":
                    await Passwords.LoadAsync();
                    break;
                case "newtab":
                    await NewTab.LoadAsync();
                    break;
                case "music":
                case "music/search":
                case "music/library":
                case "music/playlists":
                    await Music.EnsureLoadedAsync();
                    break;
                case "search":
                    Search.RefreshIndexStats();
                    if (string.IsNullOrWhiteSpace(Search.LastQuery)) Search.Run(Search.Query);
                    break;
                case "workspaces":
                case "notes":
                case "tasks":
                case "tools":
                case "performance":
                case "gaming":
                    Omega.SelectSection(key);
                    await Omega.LoadAsync();
                    break;
                case "settings":
                case "privacy":
                case "about":
                    Raise(nameof(AiKeyStatus), nameof(AdBlockRuleText), nameof(WhitelistText), nameof(RuntimeStatus));
                    break;
            }
        }
        catch (Exception ex)
        {
            _log.Error($"page data load failed ({key})", ex);
            StatusMessage = "Не удалось загрузить данные страницы: " + ex.Message;
        }
    }

    // ---------------- new tab page ----------------

    public ICommand AddQuickLinkCommand => Cmd("addquicklink", async () =>
    {
        var title = NewTab.NewQuickLinkTitle;
        var url = NewTab.NewQuickLinkUrl;

        if (string.IsNullOrWhiteSpace(url))
        {
            StatusMessage = "Укажите адрес сайта для быстрой ссылки.";
            return;
        }

        if (!url.Contains("://")) url = "https://" + url;
        await NewTab.AddQuickLinkAsync(title, url);
        NewTab.NewQuickLinkTitle = string.Empty;
        NewTab.NewQuickLinkUrl = string.Empty;
        StatusMessage = "Быстрая ссылка добавлена.";
    });

    public ICommand RemoveQuickLinkCommand => Cmd("removequicklink", async p =>
    {
        if (p is QuickLink link)
        {
            await NewTab.RemoveQuickLinkAsync(link);
            StatusMessage = "Быстрая ссылка удалена.";
        }
    });

    public ICommand OpenNewTabSearchCommand => Cmd("newtabsearch", p =>
    {
        if (p is string query) NavigateActiveTab(query);
    });

    // ---------------- wallpaper ----------------

    public ICommand SetWallpaperCommand => Cmd("setwallpaper", () =>
    {
        if (_shell is null) return;
        var path = _shell.PickOpenFile("Изображения|*.png;*.jpg;*.jpeg;*.webp;*.bmp");
        if (string.IsNullOrWhiteSpace(path)) return;

        try
        {
            Directory.CreateDirectory(AppPaths.WallpapersDir);
            var target = Path.Combine(AppPaths.WallpapersDir, "wallpaper" + Path.GetExtension(path));
            File.Copy(path, target, overwrite: true);
            Settings.WallpaperPath = target;
            NewTab.LoadWallpaper();
            FlushSettings();
            StatusMessage = "Обои установлены.";
        }
        catch (Exception ex)
        {
            _log.Error("wallpaper set failed", ex);
            StatusMessage = "Не удалось установить обои: " + ex.Message;
        }
    });

    public ICommand ResetWallpaperCommand => Cmd("resetwallpaper", () =>
    {
        Settings.WallpaperPath = string.Empty;
        NewTab.LoadWallpaper();
        FlushSettings();
        StatusMessage = "Обои сброшены.";
    });

    // ---------------- misc ----------------

    public ICommand OpenFileCommand => Cmd("openfile", p =>
    {
        if (p is string path) _shell?.OpenExternal(path);
    });

    public ICommand ShowRuntimeHelpCommand => Cmd("runtimehelp", () =>
    {
        _shell?.ShowMessage("Microsoft Edge WebView2 Runtime",
            "DrBrowser использует движок Microsoft Edge WebView2.\n\n" +
            "Если сайты не открываются, установите WebView2 Runtime:\n" +
            "https://go.microsoft.com/fwlink/p/?LinkId=2124703\n\n" +
            "После установки перезапустите DrBrowser. На Windows 11 Runtime обычно уже установлен.");
    });

    public ICommand OpenRuntimeDownloadCommand => Cmd("runtimedownload", () =>
        _shell?.OpenExternal("https://go.microsoft.com/fwlink/p/?LinkId=2124703"));

    public string AppVersionText => AppSettings.AppVersionText;
    public string EngineVersionText => string.IsNullOrWhiteSpace(WebViews.RuntimeVersion)
        ? "WebView2 Runtime не обнаружен"
        : $"WebView2 / Chromium {WebViews.RuntimeVersion}";

    public string ProfileFolder => string.IsNullOrWhiteSpace(Settings.UserDataFolder)
        ? AppPaths.DefaultWebProfile
        : Settings.UserDataFolder;

    public ICommand OpenProfileFolderCommand => Cmd("openprofilefolder", () => _shell?.OpenExternal(ProfileFolder));
    public ICommand OpenDataFolderCommand => Cmd("opendatafolder", () => _shell?.OpenExternal(AppPaths.DataRoot));
    public ICommand OpenLogsFolderCommand => Cmd("openlogsfolder", () => _shell?.OpenExternal(AppPaths.LogDir));
    public ICommand OpenScreenshotsFolderCommand => Cmd("openscreenshots", () => _shell?.OpenExternal(AppPaths.ScreenshotsDir));

    public ICommand ChangeDownloadsFolderCommand => Cmd("changedownloadsfolder", () =>
    {
        var folder = _shell?.PickFolder(Settings.DownloadsFolder);
        if (string.IsNullOrWhiteSpace(folder)) return;
        Settings.DownloadsFolder = folder;
        FlushSettings();
        StatusMessage = "Папка загрузок: " + folder;
    });

    public ICommand ResetSettingsCommand => Cmd("resetsettings", () =>
    {
        var defaults = SettingsService.CreateDefaults();
        var json = System.Text.Json.JsonSerializer.Serialize(defaults);
        var fresh = System.Text.Json.JsonSerializer.Deserialize<AppSettings>(json)!;
        fresh.Normalize();

        Settings.Theme = fresh.Theme;
        Settings.AccentHex = fresh.AccentHex;
        Settings.SearchEngine = fresh.SearchEngine;
        Settings.HomePage = fresh.HomePage;
        Settings.ShowBookmarksBar = fresh.ShowBookmarksBar;
        Settings.ShowWeather = fresh.ShowWeather;
        Settings.ZoomLevel = 1.0;
        Settings.AdBlockEnabled = true;

        ThemeManager.Apply(Settings);
        ApplyZoom();
        FlushSettings();
        Raise(nameof(ShowBookmarksBar), nameof(ZoomText));
        StatusMessage = "Настройки сброшены к значениям по умолчанию.";
    });

    public string SelectedSearchEngine
    {
        get => Settings.SearchEngine;
        set
        {
            if (string.IsNullOrWhiteSpace(value) || Settings.SearchEngine == value) return;
            Settings.SearchEngine = value;
            Raise(nameof(SelectedSearchEngine), nameof(NewTab));
            FlushSettings();
        }
    }

    public IReadOnlyList<string> SearchEngineNames => AppSettings.SearchEngines.Select(s => s.Name).ToList();
}
