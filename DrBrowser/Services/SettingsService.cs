using System.IO;
using System.Text.Json;

namespace DrBrowser;

/// <summary>Loads/saves AppSettings as JSON with atomic writes and corrupt-file recovery.</summary>
public sealed class SettingsService
{
    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    private readonly Logger _log;
    private readonly object _sync = new();

    public SettingsService(Logger log) => _log = log;

    public string? LastStatus { get; private set; }

    public AppSettings Load()
    {
        AppPaths.EnsureCreated();

        try
        {
            if (!File.Exists(AppPaths.SettingsFile)) return CreateDefaults();

            var json = File.ReadAllText(AppPaths.SettingsFile);
            var settings = JsonSerializer.Deserialize<AppSettings>(json, Options) ?? throw new InvalidDataException("settings.json is empty");
            settings.Normalize();
            return settings;
        }
        catch (Exception ex)
        {
            _log.Error("settings load failed, defaults restored", ex);
            try
            {
                if (File.Exists(AppPaths.SettingsFile))
                {
                    File.Copy(AppPaths.SettingsFile,
                        Path.Combine(AppPaths.DataRoot, $"settings.corrupt-{DateTime.Now:yyyyMMdd-HHmmss}.json"),
                        overwrite: true);
                }
            }
            catch
            {
                // diagnostics only
            }

            LastStatus = "Настройки были повреждены и сброшены к значениям по умолчанию.";
            var defaults = CreateDefaults();
            Save(defaults);
            return defaults;
        }
    }

    public void Save(AppSettings settings)
    {
        try
        {
            lock (_sync)
            {
                AppPaths.EnsureCreated();
                var json = JsonSerializer.Serialize(settings, Options);
                var tmp = AppPaths.SettingsFile + ".tmp";
                File.WriteAllText(tmp, json);
                File.Move(tmp, AppPaths.SettingsFile, overwrite: true);
            }
        }
        catch (Exception ex)
        {
            _log.Error("settings save failed", ex);
        }
    }

    public static AppSettings CreateDefaults()
    {
        var settings = new AppSettings();
        settings.Normalize();
        return settings;
    }
}
