using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace StealAnEggUltimate;

public static class SettingsJson
{
    public static readonly JsonSerializerOptions Options = Create();

    private static JsonSerializerOptions Create()
    {
        var options = new JsonSerializerOptions
        {
            WriteIndented = true,
            PropertyNameCaseInsensitive = true,
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DictionaryKeyPolicy = null,
            ReadCommentHandling = JsonCommentHandling.Skip,
            AllowTrailingCommas = true,
            NumberHandling = JsonNumberHandling.AllowReadingFromString,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        };
        options.Converters.Add(new JsonStringEnumConverter());
        return options;
    }

    public static string Serialize<T>(T value) => JsonSerializer.Serialize(value, Options);

    public static T? Deserialize<T>(string json) => JsonSerializer.Deserialize<T>(json, Options);

    public static T Clone<T>(T value) where T : class
        => Deserialize<T>(Serialize(value)) ?? throw new InvalidOperationException("Clone failed");
}

/// <summary>
/// Copies a loaded/default settings tree into the live instance so existing WPF
/// bindings keep pointing at the same objects.
/// </summary>
public static class SettingsCloner
{
    public static void CopyInto(object target, object source)
    {
        if (target is null || source is null || target.GetType() != source.GetType()) return;

        foreach (var property in target.GetType().GetProperties(System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Instance))
        {
            if (!property.CanRead || !property.CanWrite) continue;
            if (property.GetIndexParameters().Length > 0) continue;

            var sourceValue = property.GetValue(source);
            var currentValue = property.GetValue(target);

            if (currentValue is ObservableObject && sourceValue is not null)
            {
                CopyInto(currentValue, sourceValue);
                continue;
            }

            if (property.PropertyType == typeof(Dictionary<string, bool>))
            {
                property.SetValue(target, sourceValue is Dictionary<string, bool> map
                    ? new Dictionary<string, bool>(map, StringComparer.OrdinalIgnoreCase)
                    : new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase));
                continue;
            }

            if (property.PropertyType == typeof(Dictionary<string, string>))
            {
                property.SetValue(target, sourceValue is Dictionary<string, string> map
                    ? new Dictionary<string, string>(map, StringComparer.OrdinalIgnoreCase)
                    : new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase));
                continue;
            }

            try
            {
                property.SetValue(target, sourceValue);
            }
            catch
            {
                // skip read-only-ish values
            }
        }
    }
}

/// <summary>
/// Loads / saves the JSON configuration, keeps a last-known Roblox snapshot and
/// provides atomic writes so a crash cannot leave a half-written config behind.
/// </summary>
public sealed class SettingsManager
{
    private readonly Logger _log;
    private readonly object _writeLock = new();

    public SettingsManager(Logger log)
    {
        _log = log;
    }

    public string ConfigPath => AppPaths.ConfigFile;

    public event EventHandler<string>? StatusMessage;

    public AppSettings Load()
    {
        AppPaths.EnsureCreated();

        try
        {
            if (!File.Exists(AppPaths.ConfigFile))
            {
                _log.Info("No config found, creating defaults.");
                var fresh = new AppSettings();
                fresh.Normalize();
                SeedDefaultFeatures(fresh);
                Save(fresh);
                return fresh;
            }

            var json = File.ReadAllText(AppPaths.ConfigFile);
            var settings = SettingsJson.Deserialize<AppSettings>(json);

            if (settings is null)
                throw new InvalidDataException("settings.json deserialized to null");

            settings.Normalize();
            SeedDefaultFeatures(settings);
            _log.Info($"Config loaded from {AppPaths.ConfigFile}");
            return settings;
        }
        catch (Exception ex)
        {
            _log.Error("Config load failed, falling back to defaults.", ex);

            try
            {
                if (File.Exists(AppPaths.ConfigFile))
                {
                    var backup = Path.Combine(AppPaths.ConfigDir, $"settings.corrupt-{DateTime.Now:yyyyMMdd-HHmmss}.json");
                    File.Copy(AppPaths.ConfigFile, backup, overwrite: true);
                    _log.Warn($"Corrupted config backed up to {backup}");
                }
            }
            catch (Exception backupEx)
            {
                _log.Error("Could not back up corrupted config.", backupEx);
            }

            var defaults = new AppSettings();
            defaults.Normalize();
            SeedDefaultFeatures(defaults);
            StatusMessage?.Invoke(this, "Config was damaged and has been reset to defaults.");
            return defaults;
        }
    }

    /// <summary>Writes the config atomically (temp file + replace).</summary>
    public bool Save(AppSettings settings)
    {
        lock (_writeLock)
        {
            try
            {
                AppPaths.EnsureCreated();
                var json = SettingsJson.Serialize(settings);
                var temp = AppPaths.ConfigFile + ".tmp";
                File.WriteAllText(temp, json);

                if (File.Exists(AppPaths.ConfigFile))
                    File.Replace(temp, AppPaths.ConfigFile, null, ignoreMetadataErrors: true);
                else
                    File.Move(temp, AppPaths.ConfigFile);

                return true;
            }
            catch (Exception ex)
            {
                _log.Error("Config save failed.", ex);
                return false;
            }
        }
    }

    public bool SaveTo(AppSettings settings, string path)
    {
        try
        {
            var dir = Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            File.WriteAllText(path, SettingsJson.Serialize(settings));
            _log.Info($"Config exported to {path}");
            return true;
        }
        catch (Exception ex)
        {
            _log.Error($"Config export failed for {path}.", ex);
            return false;
        }
    }

    public AppSettings? LoadFrom(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            var loaded = SettingsJson.Deserialize<AppSettings>(File.ReadAllText(path));
            if (loaded is null) return null;
            loaded.Normalize();
            _log.Info($"Config imported from {path}");
            return loaded;
        }
        catch (Exception ex)
        {
            _log.Error($"Config import failed for {path}.", ex);
            return null;
        }
    }

    public void SaveLastKnownSnapshot(EggSnapshot snapshot)
    {
        try
        {
            AppPaths.EnsureCreated();
            File.WriteAllText(AppPaths.LastKnownSnapshot, SettingsJson.Serialize(snapshot));
        }
        catch (Exception ex)
        {
            _log.Error("Could not persist the last-known Roblox snapshot.", ex);
        }
    }

    public EggSnapshot? LoadLastKnownSnapshot()
    {
        try
        {
            if (!File.Exists(AppPaths.LastKnownSnapshot)) return null;
            return SettingsJson.Deserialize<EggSnapshot>(File.ReadAllText(AppPaths.LastKnownSnapshot));
        }
        catch (Exception ex)
        {
            _log.Error("Could not read the last-known Roblox snapshot.", ex);
            return null;
        }
    }

    public static AppSettings CreateDefaults()
    {
        var settings = new AppSettings();
        settings.Normalize();
        SeedDefaultFeatures(settings);
        return settings;
    }

    /// <summary>
    /// Sensible toggle defaults on first run. Automation stays OFF; purely visual
    /// helpers start enabled so the demo world is visible immediately.
    /// </summary>
    private static void SeedDefaultFeatures(AppSettings settings)
    {
        void Add(string id, bool value)
        {
            if (!settings.Features.ContainsKey(id)) settings.Features[id] = value;
        }

        Add(FeatureIds.EggEsp, true);
        Add(FeatureIds.RareEggEsp, true);
        Add(FeatureIds.BestEggFinder, false);
        Add(FeatureIds.EggScanner, true);
        Add(FeatureIds.EggValueDisplay, true);
        Add(FeatureIds.RareEggNotifications, true);
        Add(FeatureIds.EggDistanceDisplay, true);
        Add(FeatureIds.EggRarityFilter, true);
        Add(FeatureIds.EggTracker, false);
        Add(FeatureIds.EggList, true);

        Add(FeatureIds.Speed, false);
        Add(FeatureIds.Fly, false);
        Add(FeatureIds.Noclip, false);
        Add(FeatureIds.InfiniteJump, false);
        Add(FeatureIds.Teleport, false);
        Add(FeatureIds.JumpPower, false);
        Add(FeatureIds.MovementSettings, false);
        Add(FeatureIds.PlayerInfo, true);

        foreach (var id in FeatureIds.Automation)
            Add(id, false);
    }
}
