using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;
using AxsesClient.Launcher.Models;
using AxsesClient.Updater;

namespace AxsesClient.Launcher.Services;

public sealed class LauncherStateController
{
    public LauncherSettings Settings { get; private set; }
    public List<SupportedVersionSpec> Versions { get; } = SupportedVersionSpec.CreateDefaults();
    public List<LaunchProfile> Profiles { get; } = [];
    public List<LauncherThemePalette> Themes { get; } = LauncherThemePalette.CreateOfficialThemes();
    public List<ModuleCatalogItem> Modules { get; } = ModuleCatalogItem.CreateAll90Modules();
    public List<DownloadTaskItem> DownloadTasks { get; } = [];
    public List<string> Logs { get; } = [];
    public List<DiagnosticCheckItem> LastDiagnostics { get; private set; } = [];

    public MicrosoftAuthService AuthService { get; }
    public JavaRuntimeLocator JavaLocator { get; }
    public MinecraftLaunchEngine LaunchEngine { get; }
    public DiagnosticsService DiagnosticsService { get; }
    public AutoUpdaterService UpdaterService { get; }

    public LauncherPage CurrentPage { get; set; } = LauncherPage.Dashboard;
    public string StatusBanner { get; set; } = "AXSESCLIENT v1.0.0 (GENESIS X) Ready";
    public double ActiveOperationProgress { get; set; }
    public bool IsBusy { get; set; }

    public event Action? StateChanged;

    private readonly string _settingsPath;
    private readonly string _profilesPath;

    public LauncherStateController(string? customDataRoot = null)
    {
        string root = string.IsNullOrWhiteSpace(customDataRoot)
            ? LauncherSettings.GetDefaultDataDir()
            : Path.GetFullPath(customDataRoot);
        Directory.CreateDirectory(root);

        _settingsPath = Path.Combine(root, "launcher-settings.json");
        _profilesPath = Path.Combine(root, "profiles.json");

        Settings = LoadSettings(root);
        Settings.RootDataDir = root;

        AuthService = new MicrosoftAuthService(root);
        JavaLocator = new JavaRuntimeLocator(root);
        LaunchEngine = new MinecraftLaunchEngine(root, JavaLocator, AuthService);
        DiagnosticsService = new DiagnosticsService(JavaLocator, LaunchEngine);
        UpdaterService = new AutoUpdaterService(AppContext.BaseDirectory);

        LoadProfiles();
        RefreshAllVersions();
        LoadModulesFromSelectedInstance();
        AppendLog($"AXSESCLIENT Launcher v1.0.0 initialized at {root}");
    }

    public SupportedVersionSpec SelectedVersionSpec =>
        Versions.FirstOrDefault(v => v.MinecraftVersion == Settings.SelectedVersion) ?? Versions[1];

    public LaunchProfile SelectedProfile =>
        Profiles.FirstOrDefault(p => p.Id == Settings.SelectedProfileId) ?? Profiles[0];

    public MinecraftAccountRecord? SelectedAccount =>
        AuthService.Accounts.FirstOrDefault(a => a.Uuid == Settings.SelectedAccountUuid)
        ?? AuthService.Accounts.FirstOrDefault();

    public LauncherThemePalette ActiveTheme =>
        Themes.FirstOrDefault(t => t.Id == Settings.ActiveThemeId) ?? Themes[0];

    public void SelectVersion(string mcVersion)
    {
        Settings.SelectedVersion = mcVersion;
        SelectedProfile.MinecraftVersion = mcVersion;
        SaveSettings();
        SaveProfiles();
        LoadModulesFromSelectedInstance();
        NotifyChanged();
    }

    public void SetActiveTheme(string themeId)
    {
        if (Themes.All(t => t.Id != themeId)) return;
        Settings.ActiveThemeId = themeId;
        SaveSettings();
        SyncModulesAndThemeToSelectedInstance();
        AppendLog($"Applied theme '{ActiveTheme.Name}' across Launcher & Client.");
        NotifyChanged();
    }

    public LaunchProfile CreateProfile(string name, string mcVersion, int ramMb)
    {
        var profile = new LaunchProfile
        {
            Name = string.IsNullOrWhiteSpace(name) ? $"Profile {Profiles.Count + 1}" : name.Trim(),
            MinecraftVersion = mcVersion,
            RamMb = Math.Clamp(ramMb, 1024, 32768),
            ThemeId = Settings.ActiveThemeId
        };
        Profiles.Add(profile);
        Settings.SelectedProfileId = profile.Id;
        Settings.SelectedVersion = profile.MinecraftVersion;
        SaveProfiles();
        SaveSettings();
        AppendLog($"Created launch profile '{profile.Name}' ({profile.MinecraftVersion}, {profile.RamMb} MB).");
        NotifyChanged();
        return profile;
    }

    public void DeleteProfile(string id)
    {
        if (Profiles.Count <= 1) return;
        Profiles.RemoveAll(p => p.Id == id);
        if (Settings.SelectedProfileId == id)
            Settings.SelectedProfileId = Profiles[0].Id;
        SaveProfiles();
        SaveSettings();
        NotifyChanged();
    }

    public void RefreshAllVersions()
    {
        foreach (var v in Versions)
            LaunchEngine.RefreshVersionState(v);
        NotifyChanged();
    }

    public void LoadModulesFromSelectedInstance()
    {
        string cfgPath = Path.Combine(LaunchEngine.GetInstanceDir(SelectedVersionSpec.MinecraftVersion), "axsesclient", "configs", "default.json");
        if (!File.Exists(cfgPath)) return;

        try
        {
            using var doc = JsonDocument.Parse(File.ReadAllText(cfgPath));
            if (doc.RootElement.TryGetProperty("modules", out var modsObj) && modsObj.ValueKind == JsonValueKind.Object)
            {
                foreach (var m in Modules)
                {
                    if (modsObj.TryGetProperty(m.Id, out var entry))
                    {
                        if (entry.TryGetProperty("enabled", out var en) && en.ValueKind is JsonValueKind.True or JsonValueKind.False)
                            m.Enabled = en.GetBoolean();
                        if (entry.TryGetProperty("favorite", out var fav) && fav.ValueKind is JsonValueKind.True or JsonValueKind.False)
                            m.Favorite = fav.GetBoolean();
                    }
                }
            }
        }
        catch
        {
            AppendLog($"Recovered safely from malformed config at {cfgPath}");
        }
    }

    public void SyncModulesAndThemeToSelectedInstance()
    {
        string instDir = LaunchEngine.GetInstanceDir(SelectedVersionSpec.MinecraftVersion);
        MinecraftLaunchEngine.SyncInstanceClientConfig(
            instDir, Settings.ActiveThemeId, Settings.Language, Settings.ReducedMotion, Modules);
    }

    public void OpenDirectoryInExplorer(string directoryPath)
    {
        Directory.CreateDirectory(directoryPath);
        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
                Process.Start(new ProcessStartInfo("explorer.exe", $"\"{directoryPath}\"") { UseShellExecute = true });
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
                Process.Start("open", directoryPath);
            else
                Process.Start("xdg-open", directoryPath);
        }
        catch (Exception ex)
        {
            AppendLog($"Folder path: {directoryPath} ({ex.Message})");
        }
    }

    public async Task RunDiagnosticsAsync(CancellationToken ct = default)
    {
        StatusBanner = "Running system & installation diagnostics...";
        NotifyChanged();
        LastDiagnostics = await DiagnosticsService.RunFullDiagnosticsAsync(Settings, Versions, ct).ConfigureAwait(false);
        int passed = LastDiagnostics.Count(d => d.Passed);
        StatusBanner = $"Diagnostics complete: {passed}/{LastDiagnostics.Count} checks passed.";
        AppendLog(StatusBanner);
        NotifyChanged();
    }

    public void AppendLog(string message)
    {
        string line = $"[{DateTime.Now:HH:mm:ss}] {SecureTokenVault.ScrubSecrets(message)}";
        lock (Logs)
        {
            Logs.Add(line);
            if (Logs.Count > 600)
                Logs.RemoveRange(0, Logs.Count - 600);
        }
    }

    public void TrackDownloadTask(DownloadTaskItem item)
    {
        lock (DownloadTasks)
        {
            DownloadTasks.Insert(0, item);
            if (DownloadTasks.Count > 150)
                DownloadTasks.RemoveRange(150, DownloadTasks.Count - 150);
        }
        NotifyChanged();
    }

    public void SaveSettings()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_settingsPath)!);
        string tmp = _settingsPath + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(Settings, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(tmp, _settingsPath, overwrite: true);
    }

    public void SaveProfiles()
    {
        Directory.CreateDirectory(Path.GetDirectoryName(_profilesPath)!);
        string tmp = _profilesPath + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(Profiles, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(tmp, _profilesPath, overwrite: true);
    }

    public void NotifyChanged() => StateChanged?.Invoke();

    private LauncherSettings LoadSettings(string root)
    {
        try
        {
            if (File.Exists(_settingsPath))
            {
                var loaded = JsonSerializer.Deserialize<LauncherSettings>(File.ReadAllText(_settingsPath));
                if (loaded is not null) return loaded;
            }
        }
        catch
        {
            // Safe fallback on corrupt settings file
        }
        return new LauncherSettings { RootDataDir = root };
    }

    private void LoadProfiles()
    {
        Profiles.Clear();
        try
        {
            if (File.Exists(_profilesPath))
            {
                var list = JsonSerializer.Deserialize<List<LaunchProfile>>(File.ReadAllText(_profilesPath));
                if (list is { Count: > 0 })
                    Profiles.AddRange(list);
            }
        }
        catch { }

        if (Profiles.Count == 0)
        {
            Profiles.Add(new LaunchProfile { Name = "Competitive 1.21.4", MinecraftVersion = "1.21.4", RamMb = 4096 });
            Profiles.Add(new LaunchProfile { Name = "Flagship 1.21.11", MinecraftVersion = "1.21.11", RamMb = 4096 });
            Profiles.Add(new LaunchProfile { Name = "Classic 1.16.5", MinecraftVersion = "1.16.5", RamMb = 3072 });
            SaveProfiles();
        }

        if (string.IsNullOrWhiteSpace(Settings.SelectedProfileId) || Profiles.All(p => p.Id != Settings.SelectedProfileId))
        {
            Settings.SelectedProfileId = Profiles[0].Id;
            SaveSettings();
        }
    }
}
