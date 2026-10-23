using System.IO;

namespace DrBrowser;

/// <summary>All on-disk locations used by the browser.</summary>
public static class AppPaths
{
    public static string DataRoot { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "DrBrowser");

    /// <summary>Alias used by services that only need "the data directory".</summary>
    public static string DataFolder => DataRoot;

    public static string SettingsFile => Path.Combine(DataRoot, "settings.json");
    public static string SessionFile => Path.Combine(DataRoot, "session.json");
    public static string DatabaseFile => Path.Combine(DataRoot, "drbrowser.db");
    public static string SecretsDir => Path.Combine(DataRoot, "secrets");
    public static string FiltersDir => Path.Combine(DataRoot, "filters");
    public static string WallpapersDir => Path.Combine(DataRoot, "wallpapers");
    public static string LogDir => Path.Combine(DataRoot, "logs");
    public static string ScreenshotsDir => Path.Combine(DataRoot, "screenshots");
    public static string DefaultWebProfile => Path.Combine(DataRoot, "profile", "default");
    public static string IncognitoRoot => Path.Combine(DataRoot, "profile", "incognito");

    public static string DefaultDownloadsFolder =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");

    public static void EnsureCreated()
    {
        foreach (var dir in new[] { DataRoot, SecretsDir, FiltersDir, WallpapersDir, LogDir, ScreenshotsDir })
        {
            Directory.CreateDirectory(dir);
        }
    }

    public static string NewIncognitoFolder()
    {
        var path = Path.Combine(IncognitoRoot, Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(path);
        return path;
    }

    /// <summary>Best-effort recursive delete (WebView2 keeps some files locked briefly).</summary>
    public static void TryDeleteDirectory(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || !Directory.Exists(path)) return;

        for (var attempt = 0; attempt < 4; attempt++)
        {
            try
            {
                Directory.Delete(path, recursive: true);
                return;
            }
            catch (IOException)
            {
                Thread.Sleep(250);
            }
            catch (UnauthorizedAccessException)
            {
                Thread.Sleep(250);
            }
        }
    }
}
