using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;
using AxsesClient.Launcher.Models;
using AxsesClient.Updater;

namespace AxsesClient.Launcher.Services;

public sealed class LauncherStateController
{
    private static readonly JsonSerializerOptions JsonOpts = new() { WriteIndented = true };

    public LauncherSettings Settings { get; private set; }
    public List<SupportedVersionSpec> Versions { get; }
    public List<LaunchProfile> Profiles { get; private set; }
    public IReadOnlyList<MinecraftAccountRecord> Accounts => AuthService.Accounts;
    public List<ModuleCatalogItem> Modules { get; private set; }
    public List<LauncherThemePalette> Themes { get; }
    public List<DownloadTaskItem> Downloads { get; } = [];
    public List<string> LiveConsoleLines { get; } = [];
    public List<DiagnosticCheckItem> LastDiagnostics { get; private set; } = [];

    public JavaRuntimeLocator JavaLocator { get; }
    public MicrosoftAuthService AuthService { get; }
    public MinecraftLaunchEngine LaunchEngine { get; }
    public DiagnosticsService Diagnostics { get; }
    public AutoUpdaterService Updater { get; }

    public SystemMemorySnapshot MemorySnapshot { get; private set; }
    public DetectedJavaRuntime? DetectedJava8 { get; private set; }
    public DetectedJavaRuntime? DetectedJava21 { get; private set; }
    public bool IsJavaProvisioning { get; private set; }

    public event Action? StateChanged;
    public event Action<string>? ConsoleLineAdded;
    public event Action<string, double>? ProgressUpdated;

    public string CurrentStatusText { get; private set; } = "Готов к запуску";
    public double CurrentProgress { get; private set; }
    public bool IsBusy { get; private set; }
    public bool IsRu => !string.Equals(Settings.Language, "en_US", StringComparison.OrdinalIgnoreCase);

    public LauncherStateController(string? customDataDir = null)
    {
        MemorySnapshot = SystemMemoryDetector.Query();
        string dataRoot = customDataDir ?? LauncherSettings.GetDefaultDataDir();
        Directory.CreateDirectory(dataRoot);
        Directory.CreateDirectory(Path.Combine(dataRoot, "profiles"));
        Directory.CreateDirectory(Path.Combine(dataRoot, "configs"));
        Directory.CreateDirectory(Path.Combine(dataRoot, "logs"));

        Settings = LoadJsonOrDefault(Path.Combine(dataRoot, "launcher-settings.json"), () => new LauncherSettings { RootDataDir = dataRoot });
        Settings.RootDataDir = dataRoot;
        if (Settings.AllocatedRamMb < 1024)
            Settings.AllocatedRamMb = MemorySnapshot.OptimalDefaultAllocationMb;
        Settings.AllocatedRamMb = Math.Clamp(Settings.AllocatedRamMb, 1024, Math.Max(2048, MemorySnapshot.MaxRecommendedAllocationMb));

        Versions = SupportedVersionSpec.CreateDefaults();
        Themes = LauncherThemePalette.CreateOfficialThemes();
        Profiles = LoadJsonOrDefault(Path.Combine(dataRoot, "profiles", "profiles.json"), CreateDefaultProfiles);

        var defaultCatalog = ModuleCatalogItem.CreateAll96Modules();
        var loadedModules = LoadJsonOrDefault(Path.Combine(dataRoot, "configs", "launcher-modules.json"), () => defaultCatalog);
        Modules = MergeModuleCatalog(defaultCatalog, loadedModules);

        JavaLocator = new JavaRuntimeLocator(dataRoot);
        AuthService = new MicrosoftAuthService(dataRoot);
        if (AuthService.Accounts.Count == 0)
        {
            var local = AuthService.CreateLocalSandboxAccount("AxsesPlayer");
            Settings.SelectedAccountUuid = local.Uuid;
        }

        if (string.IsNullOrWhiteSpace(Settings.SelectedProfileId) || Profiles.All(p => p.Id != Settings.SelectedProfileId))
            Settings.SelectedProfileId = Profiles[0].Id;

        foreach (var p in Profiles)
        {
            if (p.RamMb < 1024)
                p.RamMb = Settings.AllocatedRamMb;
        }
        SelectedProfile.RamMb = Settings.AllocatedRamMb;

        if (string.IsNullOrWhiteSpace(Settings.SelectedAccountUuid) || AuthService.Accounts.All(a => a.Uuid != Settings.SelectedAccountUuid))
            Settings.SelectedAccountUuid = AuthService.Accounts[0].Uuid;

        LaunchEngine = new MinecraftLaunchEngine(dataRoot, JavaLocator, AuthService);
        Diagnostics = new DiagnosticsService(JavaLocator, LaunchEngine);
        Updater = new AutoUpdaterService(AppContext.BaseDirectory);

        foreach (var v in Versions)
            LaunchEngine.RefreshVersionState(v);

        RefreshJavaStatus();

        AppendConsole($"[AXSES v2.5] Система готова • ОЗУ ПК: {MemorySnapshot.TotalPhysicalMb} МБ (выделено {Settings.AllocatedRamMb} МБ) • Модулей: {Modules.Count}");
        SaveAllState();

        // Automatically download & install missing Java 8 & Java 21 on Windows when AutoInstallJava is active
        if (Settings.AutoInstallJava && RuntimeInformation.IsOSPlatform(OSPlatform.Windows) &&
            customDataDir is null && (DetectedJava8 is null || DetectedJava21 is null))
        {
            _ = Task.Run(async () =>
            {
                await Task.Delay(800).ConfigureAwait(false);
                await AutoInstallMissingJavaSilentOrInteractiveAsync(interactiveError: false).ConfigureAwait(false);
            });
        }
    }

    private static List<ModuleCatalogItem> MergeModuleCatalog(List<ModuleCatalogItem> canonical, List<ModuleCatalogItem> saved)
    {
        var savedById = saved
            .GroupBy(m => m.Id, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(g => g.Key, g => g.First(), StringComparer.OrdinalIgnoreCase);

        foreach (var item in canonical)
        {
            if (savedById.TryGetValue(item.Id, out var prev))
            {
                item.Enabled = prev.Enabled;
                item.Favorite = prev.Favorite;
            }
        }
        return canonical;
    }

    public LauncherThemePalette ActiveTheme =>
        Themes.FirstOrDefault(t => t.Id == Settings.ActiveThemeId) ?? Themes[0];

    public SupportedVersionSpec SelectedVersionSpec =>
        Versions.FirstOrDefault(v => v.MinecraftVersion == Settings.SelectedVersion) ?? Versions[^1];

    public LaunchProfile SelectedProfile =>
        Profiles.FirstOrDefault(p => p.Id == Settings.SelectedProfileId) ?? Profiles[0];

    public MinecraftAccountRecord SelectedAccount =>
        AuthService.Accounts.FirstOrDefault(a => a.Uuid == Settings.SelectedAccountUuid) ?? AuthService.Accounts[0];

    public void RefreshMemorySnapshot()
    {
        MemorySnapshot = SystemMemoryDetector.Query();
    }

    public void SetAllocatedRamMb(int ramMb)
    {
        RefreshMemorySnapshot();
        int maxAllowed = Math.Max(2048, MemorySnapshot.MaxRecommendedAllocationMb);
        int clamped = Math.Clamp((ramMb / 128) * 128, 1024, maxAllowed);
        Settings.AllocatedRamMb = clamped;
        SelectedProfile.RamMb = clamped;
        SaveAllState();
        NotifyChanged();
    }

    public void RefreshJavaStatus()
    {
        try
        {
            var runtimes = JavaLocator.ScanSystemRuntimes(Settings.Java8Path, Settings.Java21Path);
            DetectedJava8 = JavaLocator.FindMatchingRuntime(8, Settings.Java8Path)
                            ?? runtimes.FirstOrDefault(r => r.MajorVersion == 8);
            DetectedJava21 = JavaLocator.FindMatchingRuntime(21, Settings.Java21Path)
                             ?? runtimes.FirstOrDefault(r => r.MajorVersion >= 21);

            if (DetectedJava8 is not null && string.IsNullOrWhiteSpace(Settings.Java8Path))
                Settings.Java8Path = DetectedJava8.ExecutablePath;
            if (DetectedJava21 is not null && string.IsNullOrWhiteSpace(Settings.Java21Path))
                Settings.Java21Path = DetectedJava21.ExecutablePath;
        }
        catch
        {
        }
    }

    public async Task AutoInstallMissingJavaSilentOrInteractiveAsync(bool interactiveError = true)
    {
        if (IsJavaProvisioning) return;
        IsJavaProvisioning = true;
        NotifyChanged();
        try
        {
            RefreshJavaStatus();
            if (DetectedJava8 is null)
            {
                AppendConsole("[Java Auto] Автоматическая установка Java 8 (для Minecraft 1.16.5)...");
                var j8 = await JavaLocator.EnsureJavaRuntimeAsync(8, Settings.Java8Path, UpdateProgress).ConfigureAwait(false);
                Settings.Java8Path = j8.ExecutablePath;
                DetectedJava8 = j8;
                AppendConsole($"[Java Auto] Java 8 готова: {j8.RawVersion} ({j8.ExecutablePath})");
            }
            if (DetectedJava21 is null)
            {
                AppendConsole("[Java Auto] Автоматическая установка Java 21 (для Minecraft 1.21.4 / 1.21.11)...");
                var j21 = await JavaLocator.EnsureJavaRuntimeAsync(21, Settings.Java21Path, UpdateProgress).ConfigureAwait(false);
                Settings.Java21Path = j21.ExecutablePath;
                DetectedJava21 = j21;
                AppendConsole($"[Java Auto] Java 21 готова: {j21.RawVersion} ({j21.ExecutablePath})");
            }
            SaveAllState();
            UpdateProgress(IsRu ? "Java 8 и Java 21 установлены и готовы к запуску!" : "Java 8 & Java 21 ready!", 1.0);
        }
        catch (Exception ex)
        {
            AppendConsole($"[Java Auto] {ex.Message}");
            if (interactiveError)
                UpdateProgress($"Авто-Java: {ex.Message}", 0);
        }
        finally
        {
            IsJavaProvisioning = false;
            RefreshJavaStatus();
            NotifyChanged();
        }
    }

    public void SelectVersion(string mcVersion)
    {
        if (Versions.Any(v => v.MinecraftVersion == mcVersion))
        {
            Settings.SelectedVersion = mcVersion;
            SelectedProfile.MinecraftVersion = mcVersion;
            SaveAllState();
            NotifyChanged();
        }
    }

    public void SetTheme(string themeId) => SetActiveTheme(themeId);

    public void SetActiveTheme(string themeId)
    {
        if (Themes.Any(t => t.Id == themeId))
        {
            Settings.ActiveThemeId = themeId;
            SelectedProfile.ThemeId = themeId;
            SaveAllState();
            NotifyChanged();
        }
    }

    private int _syncEpoch;

    public void SyncModulesAndThemeToSelectedInstance()
    {
        string instDir = LaunchEngine.GetInstanceDir(SelectedVersionSpec.MinecraftVersion);
        MinecraftLaunchEngine.SyncInstanceClientConfig(
            instDir, Settings.ActiveThemeId, Settings.Language, Settings.ReducedMotion, Modules, Settings.SyncGameLanguage);
        SaveAllState();
    }

    public void SyncModulesAndThemeToSelectedInstanceAsync()
    {
        int epoch = Interlocked.Increment(ref _syncEpoch);
        _ = Task.Run(async () =>
        {
            await Task.Delay(160).ConfigureAwait(false);
            if (epoch != Volatile.Read(ref _syncEpoch)) return;
            try
            {
                SyncModulesAndThemeToSelectedInstance();
            }
            catch
            {
                // Ignore transient file contention during rapid toggles
            }
        });
    }

    public async Task<List<DiagnosticCheckItem>> RunDiagnosticsAsync(CancellationToken ct = default)
    {
        LastDiagnostics = await Diagnostics.RunFullDiagnosticsAsync(Settings, Versions, ct).ConfigureAwait(false);
        RefreshJavaStatus();
        RefreshMemorySnapshot();
        NotifyChanged();
        return LastDiagnostics;
    }

    public void SetLanguage(string localeCode)
    {
        string normalized = string.Equals(localeCode, "en_US", StringComparison.OrdinalIgnoreCase) ? "en_US" : "ru_RU";
        Settings.Language = normalized;
        foreach (var v in Versions)
        {
            string dir = LaunchEngine.GetInstanceDir(v.MinecraftVersion);
            if (Directory.Exists(dir) || v.MinecraftVersion == SelectedVersionSpec.MinecraftVersion)
            {
                MinecraftLaunchEngine.SyncInstanceClientConfig(
                    dir, Settings.ActiveThemeId, Settings.Language, Settings.ReducedMotion, Modules, Settings.SyncGameLanguage);
            }
        }
        SaveAllState();
        NotifyChanged();
    }

    public void ToggleLanguage()
    {
        SetLanguage(IsRu ? "en_US" : "ru_RU");
    }

    public void SaveAllState()
    {
        SaveJson(Path.Combine(Settings.RootDataDir, "launcher-settings.json"), Settings);
        SaveJson(Path.Combine(Settings.RootDataDir, "profiles", "profiles.json"), Profiles);
        SaveJson(Path.Combine(Settings.RootDataDir, "configs", "launcher-modules.json"), Modules);
    }

    public async Task InstallOrPlaySelectedAsync(CancellationToken ct = default)
    {
        var spec = SelectedVersionSpec;
        if (IsBusy || spec.State == VersionInstallState.Running)
            return;

        IsBusy = true;
        NotifyChanged();
        try
        {
            SelectedProfile.RamMb = Settings.AllocatedRamMb;
            SaveAllState();

            if (spec.State is VersionInstallState.NotInstalled or VersionInstallState.RepairRequired or VersionInstallState.UpdateAvailable)
            {
                AppendConsole($"[Установка] Подготовка {spec.DisplayTitle} + Fabric {spec.FabricLoaderVersion}...");
                await LaunchEngine.InstallOrRepairVersionAsync(
                    spec,
                    Settings,
                    onStage: st => UpdateProgress(st.StageName, st.OverallProgress),
                    onDownloadItem: RecordDownloadItem,
                    onLog: AppendConsole,
                    ct: ct);
                RefreshJavaStatus();
                SaveAllState();
            }

            AppendConsole($"[Запуск] Старт {spec.DisplayTitle} • Профиль '{SelectedProfile.Name}' • ОЗУ: {Settings.AllocatedRamMb} МБ • Аккаунт '{SelectedAccount.Username}'...");
            await LaunchEngine.LaunchMinecraftAsync(
                spec,
                SelectedProfile,
                SelectedAccount,
                Settings,
                Modules,
                onStage: st => UpdateProgress(st.StageName, st.OverallProgress),
                onGameLog: AppendConsole,
                onExited: code =>
                {
                    UpdateProgress(
                        IsRu ? $"Minecraft {spec.MinecraftVersion} завершён (код {code})" : $"Minecraft {spec.MinecraftVersion} exited (code {code})",
                        0);
                    NotifyChanged();
                },
                ct: ct);

            RefreshJavaStatus();
            SaveAllState();
            UpdateProgress(IsRu ? $"В игре: {spec.DisplayTitle} ({Settings.AllocatedRamMb} МБ ОЗУ)" : $"Running {spec.DisplayTitle}", 1.0);
        }
        catch (Exception ex)
        {
            AppendConsole($"[ОШИБКА] {ex.Message}");
            AppendFileLog($"Launch/Install error: {ex}");
            UpdateProgress($"Ошибка: {ex.Message}", 0);
        }
        finally
        {
            IsBusy = false;
            NotifyChanged();
        }
    }

    public async Task RepairVersionAsync(SupportedVersionSpec spec, CancellationToken ct = default)
    {
        if (IsBusy) return;
        IsBusy = true;
        NotifyChanged();
        try
        {
            AppendConsole($"[Починка] Полная SHA-1 проверка файлов {spec.DisplayTitle}...");
            await LaunchEngine.InstallOrRepairVersionAsync(
                spec,
                Settings,
                onStage: st => UpdateProgress(st.StageName, st.OverallProgress),
                onDownloadItem: RecordDownloadItem,
                onLog: AppendConsole,
                ct: ct);
            RefreshJavaStatus();
            SaveAllState();
            UpdateProgress(IsRu ? $"{spec.DisplayTitle} проверен и готов" : $"{spec.DisplayTitle} verified & ready", 1.0);
        }
        catch (Exception ex)
        {
            AppendConsole($"[Ошибка починки] {ex.Message}");
            UpdateProgress($"Ошибка починки: {ex.Message}", 0);
        }
        finally
        {
            IsBusy = false;
            NotifyChanged();
        }
    }

    public void OpenInstanceFolder(SupportedVersionSpec spec)
    {
        string dir = LaunchEngine.GetInstanceDir(spec.MinecraftVersion);
        Directory.CreateDirectory(dir);
        try
        {
            Process.Start(new ProcessStartInfo { FileName = dir, UseShellExecute = true });
        }
        catch (Exception ex)
        {
            AppendConsole($"[Папка] {dir} ({ex.Message})");
        }
    }

    public void AppendConsole(string line)
    {
        string scrubbed = SecureTokenVault.ScrubSecrets(line);
        string stamped = $"[{DateTime.Now:HH:mm:ss}] {scrubbed}";
        lock (LiveConsoleLines)
        {
            LiveConsoleLines.Add(stamped);
            if (LiveConsoleLines.Count > 500)
                LiveConsoleLines.RemoveAt(0);
        }
        ConsoleLineAdded?.Invoke(stamped);
    }

    private void AppendFileLog(string text)
    {
        try
        {
            string logPath = Path.Combine(Settings.RootDataDir, "logs", "launcher.log");
            File.AppendAllText(logPath, $"[{DateTime.UtcNow:O}] {SecureTokenVault.ScrubSecrets(text)}{Environment.NewLine}");
        }
        catch { }
    }

    private void UpdateProgress(string text, double progress)
    {
        CurrentStatusText = text;
        CurrentProgress = Math.Clamp(progress, 0.0, 1.0);
        ProgressUpdated?.Invoke(CurrentStatusText, CurrentProgress);
        NotifyChanged();
    }

    private void RecordDownloadItem(DownloadTaskItem item)
    {
        lock (Downloads)
        {
            Downloads.Insert(0, item);
            if (Downloads.Count > 120)
                Downloads.RemoveAt(Downloads.Count - 1);
        }
    }

    public void NotifyChanged() => StateChanged?.Invoke();

    private static List<LaunchProfile> CreateDefaultProfiles() =>
    [
        new() { Name = "PvP & Combat (1.21.11)", MinecraftVersion = "1.21.11", RamMb = 4096, ClientConfigPreset = "competitive", ThemeId = "axses-dark" },
        new() { Name = "Modern Survival (1.21.4)", MinecraftVersion = "1.21.4", RamMb = 4096, ClientConfigPreset = "default", ThemeId = "axses-cyber" },
        new() { Name = "Classic 1.16.5 PvP", MinecraftVersion = "1.16.5", RamMb = 3072, ClientConfigPreset = "competitive", ThemeId = "axses-purple" }
    ];

    private static T LoadJsonOrDefault<T>(string path, Func<T> fallback)
    {
        try
        {
            if (File.Exists(path))
            {
                var loaded = JsonSerializer.Deserialize<T>(File.ReadAllText(path));
                if (loaded is not null) return loaded;
            }
        }
        catch
        {
            try
            {
                if (File.Exists(path))
                    File.Copy(path, path + $".corrupt-{DateTimeOffset.UtcNow.ToUnixTimeSeconds()}.bak", true);
            }
            catch { }
        }
        return fallback();
    }

    private static void SaveJson<T>(string path, T value)
    {
        string? dir = Path.GetDirectoryName(path);
        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
        string tmp = path + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(value, JsonOpts));
        File.Move(tmp, path, overwrite: true);
    }
}
