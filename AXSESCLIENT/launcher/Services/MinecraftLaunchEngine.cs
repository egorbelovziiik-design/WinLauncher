using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using AxsesClient.Launcher.Models;

namespace AxsesClient.Launcher.Services;

public sealed record LaunchStageProgress(int StageNumber, int TotalStages, string StageName, double OverallProgress);

public sealed class MinecraftLaunchEngine
{
    public const string MojangVersionManifestUrl = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public const string MojangResourcesBaseUrl = "https://resources.download.minecraft.net";
    public const string FabricMetaBaseUrl = "https://meta.fabricmc.net/v2/versions/loader";

    private readonly string _rootDataDir;
    private readonly JavaRuntimeLocator _javaLocator;
    private readonly MicrosoftAuthService _authService;
    private readonly HttpClient _http;

    public MinecraftLaunchEngine(
        string rootDataDir,
        JavaRuntimeLocator javaLocator,
        MicrosoftAuthService authService,
        HttpClient? httpClient = null)
    {
        _rootDataDir = Path.GetFullPath(rootDataDir);
        _javaLocator = javaLocator;
        _authService = authService;
        _http = httpClient ?? CreateHttpClient();
    }

    private static HttpClient CreateHttpClient()
    {
        var handler = new SocketsHttpHandler
        {
            MaxConnectionsPerServer = 24,
            PooledConnectionLifetime = TimeSpan.FromMinutes(5)
        };
        var client = new HttpClient(handler) { Timeout = TimeSpan.FromMinutes(5) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("AXSESCLIENT-Launcher/1.0.0");
        return client;
    }

    public string GetInstanceDir(string mcVersion) => Path.Combine(_rootDataDir, "instances", mcVersion);
    public string GetVersionMetaDir(string mcVersion) => Path.Combine(_rootDataDir, "versions", mcVersion);
    public string GetLibrariesDir() => Path.Combine(_rootDataDir, "libraries");
    public string GetAssetsDir() => Path.Combine(_rootDataDir, "assets");

    public string? LocateBundledModJar(string modJarFileName)
    {
        string baseDir = AppContext.BaseDirectory;
        string versionTag = modJarFileName.Replace("AXSESCLIENT-", "").Replace(".jar", "");

        string[] candidates =
        [
            Path.Combine(baseDir, "mods", modJarFileName),
            Path.Combine(baseDir, modJarFileName),
            Path.Combine(baseDir, "..", "AXSESCLIENT-RELEASE", modJarFileName),
            Path.Combine(Directory.GetCurrentDirectory(), "AXSESCLIENT-RELEASE", modJarFileName),
            Path.Combine(Directory.GetCurrentDirectory(), "AXSESCLIENT", $"client-{versionTag}", "build", "libs", modJarFileName),
            Path.Combine(baseDir, "..", "..", "..", "..", $"client-{versionTag}", "build", "libs", modJarFileName),
            Path.Combine(baseDir, "..", "..", "..", "..", "..", "AXSESCLIENT-RELEASE", modJarFileName)
        ];

        foreach (string c in candidates)
        {
            try
            {
                string full = Path.GetFullPath(c);
                if (File.Exists(full)) return full;
            }
            catch { }
        }
        return null;
    }

    public void RefreshVersionState(SupportedVersionSpec spec)
    {
        if (spec.State is VersionInstallState.Installing or VersionInstallState.Launching or VersionInstallState.Running)
            return;

        string verDir = GetVersionMetaDir(spec.MinecraftVersion);
        string instDir = GetInstanceDir(spec.MinecraftVersion);
        string receiptPath = Path.Combine(verDir, "install-receipt.json");
        string clientJar = Path.Combine(verDir, $"{spec.MinecraftVersion}.jar");
        string fabricJson = Path.Combine(verDir, $"fabric-{spec.MinecraftVersion}.json");
        string deployedMod = Path.Combine(instDir, "mods", spec.ModJarFileName);

        bool anyFilesExist = Directory.Exists(verDir) && Directory.EnumerateFiles(verDir).Any();
        if (!anyFilesExist && !File.Exists(receiptPath))
        {
            spec.State = VersionInstallState.NotInstalled;
            spec.StatusDetail = "Not installed • Click INSTALL to download Mojang + Fabric + AXSESCLIENT";
            spec.DiskUsageBytes = 0;
            return;
        }

        if (!File.Exists(receiptPath) || !File.Exists(clientJar) || !File.Exists(fabricJson) || new FileInfo(clientJar).Length < 1024)
        {
            spec.State = VersionInstallState.RepairRequired;
            spec.StatusDetail = "Missing or incomplete installation files • Click REPAIR";
            spec.DiskUsageBytes = ComputeDirSizeSafe(verDir) + ComputeDirSizeSafe(instDir);
            return;
        }

        string? bundledJar = LocateBundledModJar(spec.ModJarFileName);
        if (bundledJar is not null)
        {
            if (!File.Exists(deployedMod))
            {
                spec.State = VersionInstallState.RepairRequired;
                spec.StatusDetail = $"{spec.ModJarFileName} missing from instance mods folder • Click REPAIR";
                return;
            }

            string bundledHash = ComputeSha256Hex(bundledJar);
            string deployedHash = ComputeSha256Hex(deployedMod);
            if (!string.Equals(bundledHash, deployedHash, StringComparison.OrdinalIgnoreCase))
            {
                spec.State = VersionInstallState.UpdateAvailable;
                spec.StatusDetail = $"Newer {spec.ModJarFileName} build ready • Click UPDATE";
                spec.DiskUsageBytes = ComputeDirSizeSafe(verDir) + ComputeDirSizeSafe(instDir);
                return;
            }
        }

        spec.State = VersionInstallState.Installed;
        spec.DiskUsageBytes = ComputeDirSizeSafe(verDir) + ComputeDirSizeSafe(instDir);
        spec.StatusDetail = $"Installed & Verified • Fabric {spec.FabricLoaderVersion} • {(spec.DiskUsageBytes / (1024 * 1024))} MB";
    }

    public async Task InstallOrRepairVersionAsync(
        SupportedVersionSpec spec,
        LauncherSettings settings,
        Action<LaunchStageProgress>? onStage = null,
        Action<DownloadTaskItem>? onDownloadItem = null,
        Action<string>? onLog = null,
        CancellationToken ct = default)
    {
        spec.State = VersionInstallState.Installing;
        spec.Progress = 0.02;
        try
        {
            string verDir = GetVersionMetaDir(spec.MinecraftVersion);
            string instDir = GetInstanceDir(spec.MinecraftVersion);
            string libsDir = GetLibrariesDir();
            string assetsDir = GetAssetsDir();
            Directory.CreateDirectory(verDir);
            Directory.CreateDirectory(instDir);
            Directory.CreateDirectory(libsDir);
            Directory.CreateDirectory(assetsDir);

            // Stage 01: Check / Provision Java Runtime
            ReportStage(spec, onStage, 1, 9, $"Checking Java {spec.RequiredJavaMajor} Runtime...", 0.05);
            string? javaOverride = spec.RequiredJavaMajor == 8 ? settings.Java8Path : settings.Java21Path;
            var javaRt = await _javaLocator.EnsureJavaRuntimeAsync(
                spec.RequiredJavaMajor,
                javaOverride,
                (msg, p) => onLog?.Invoke($"[Java] {msg}"),
                ct).ConfigureAwait(false);
            onLog?.Invoke($"[Stage 1] Verified Java {javaRt.MajorVersion} ({javaRt.RawVersion}) at {javaRt.ExecutablePath}");

            // Stage 02: Fetch Mojang Version Manifest & Version JSON
            ReportStage(spec, onStage, 2, 9, $"Fetching official Mojang metadata for {spec.MinecraftVersion}...", 0.12);
            string versionJsonPath = await FetchMojangVersionJsonAsync(spec.MinecraftVersion, verDir, ct).ConfigureAwait(false);
            using var vanillaDoc = JsonDocument.Parse(await File.ReadAllTextAsync(versionJsonPath, ct).ConfigureAwait(false));

            // Stage 03: Fetch Fabric Meta Profile JSON
            ReportStage(spec, onStage, 3, 9, $"Fetching Fabric Loader {spec.FabricLoaderVersion} profile...", 0.18);
            string fabricJsonPath = Path.Combine(verDir, $"fabric-{spec.MinecraftVersion}.json");
            string fabricUrl = $"{FabricMetaBaseUrl}/{Uri.EscapeDataString(spec.MinecraftVersion)}/{Uri.EscapeDataString(spec.FabricLoaderVersion)}/profile/json";
            await DownloadStringToFileAsync(fabricUrl, fabricJsonPath, ct).ConfigureAwait(false);
            using var fabricDoc = JsonDocument.Parse(await File.ReadAllTextAsync(fabricJsonPath, ct).ConfigureAwait(false));

            // Stage 04: Download & SHA-1 Verify Minecraft client.jar
            ReportStage(spec, onStage, 4, 9, $"Downloading Minecraft {spec.MinecraftVersion} client.jar...", 0.25);
            var clientDl = vanillaDoc.RootElement.GetProperty("downloads").GetProperty("client");
            string clientUrl = clientDl.GetProperty("url").GetString()!;
            string clientSha1 = clientDl.GetProperty("sha1").GetString()!;
            string clientJarPath = Path.Combine(verDir, $"{spec.MinecraftVersion}.jar");
            await DownloadAndVerifyFileAsync(
                clientUrl, clientJarPath, clientSha1, $"{spec.MinecraftVersion}.jar", "Client JAR", onDownloadItem, ct).ConfigureAwait(false);

            // Stage 05: Download & Verify Mojang + Fabric Libraries
            ReportStage(spec, onStage, 5, 9, "Downloading & verifying Maven libraries...", 0.35);
            var libraries = ResolveRequiredLibraries(vanillaDoc.RootElement, fabricDoc.RootElement, libsDir);
            await DownloadBatchAsync(libraries, Math.Clamp(settings.ConcurrentDownloads, 2, 16), "Libraries",
                p => ReportStage(spec, onStage, 5, 9, $"Verifying {libraries.Count} libraries ({(int)(p * 100)}%)...", 0.35 + p * 0.22),
                onDownloadItem, ct).ConfigureAwait(false);
            spec.VerifiedLibraryCount = libraries.Count;

            // Stage 06: Download Asset Index & Verify Asset Objects
            ReportStage(spec, onStage, 6, 9, "Downloading Minecraft asset index & audio/texture objects...", 0.58);
            var assetIndexEl = vanillaDoc.RootElement.GetProperty("assetIndex");
            string assetId = assetIndexEl.GetProperty("id").GetString()!;
            string assetUrl = assetIndexEl.GetProperty("url").GetString()!;
            string assetSha1 = assetIndexEl.GetProperty("sha1").GetString()!;
            string assetIndexPath = Path.Combine(assetsDir, "indexes", $"{assetId}.json");
            await DownloadAndVerifyFileAsync(assetUrl, assetIndexPath, assetSha1, $"{assetId}.json", "Asset Index", onDownloadItem, ct).ConfigureAwait(false);

            var assetDownloads = ParseAssetObjectDownloads(await File.ReadAllTextAsync(assetIndexPath, ct).ConfigureAwait(false), assetsDir);
            await DownloadBatchAsync(assetDownloads, Math.Clamp(settings.ConcurrentDownloads, 8, 24), "Assets",
                p => ReportStage(spec, onStage, 6, 9, $"Downloading {assetDownloads.Count} assets ({(int)(p * 100)}%)...", 0.60 + p * 0.25),
                null, ct).ConfigureAwait(false);

            // Stage 07: Extract Native Libraries
            ReportStage(spec, onStage, 7, 9, "Extracting LWJGL native libraries...", 0.88);
            string nativesDir = Path.Combine(verDir, "natives");
            ExtractNativeBinaries(libraries, nativesDir);

            // Stage 08: Deploy Version-Matched AXSESCLIENT Mod JAR
            ReportStage(spec, onStage, 8, 9, $"Deploying {spec.ModJarFileName}...", 0.94);
            DeployBundledModJar(spec, instDir, onLog);

            // Stage 09: Write Verified Install Receipt (preserving worlds/configs)
            ReportStage(spec, onStage, 9, 9, "Finalizing installation receipt...", 0.99);
            var receipt = new
            {
                minecraftVersion = spec.MinecraftVersion,
                fabricLoader = spec.FabricLoaderVersion,
                installedAtUtc = DateTime.UtcNow.ToString("O"),
                verifiedLibraries = libraries.Count,
                verifiedAssets = assetDownloads.Count
            };
            await File.WriteAllTextAsync(
                Path.Combine(verDir, "install-receipt.json"),
                JsonSerializer.Serialize(receipt, new JsonSerializerOptions { WriteIndented = true }),
                ct).ConfigureAwait(false);

            spec.Progress = 1.0;
            spec.State = VersionInstallState.Installed;
            RefreshVersionState(spec);
            onLog?.Invoke($"[Install] Minecraft {spec.MinecraftVersion} + AXSESCLIENT ready.");
        }
        catch (Exception ex)
        {
            spec.State = VersionInstallState.RepairRequired;
            spec.StatusDetail = $"Install interrupted: {ex.Message}";
            onLog?.Invoke($"[ERROR] Install failed for {spec.MinecraftVersion}: {SecureTokenVault.ScrubSecrets(ex.Message)}");
            throw;
        }
    }

    public void UpdateModJarOnly(SupportedVersionSpec spec, Action<string>? onLog = null)
    {
        DeployBundledModJar(spec, GetInstanceDir(spec.MinecraftVersion), onLog);
        spec.State = VersionInstallState.Installed;
        RefreshVersionState(spec);
    }

    public async Task<Process> LaunchMinecraftAsync(
        SupportedVersionSpec spec,
        LaunchProfile profile,
        MinecraftAccountRecord account,
        LauncherSettings settings,
        IReadOnlyList<ModuleCatalogItem> launcherModules,
        Action<LaunchStageProgress>? onStage = null,
        Action<string>? onGameLog = null,
        Action<int>? onExited = null,
        CancellationToken ct = default)
    {
        RefreshVersionState(spec);
        if (spec.State is VersionInstallState.NotInstalled or VersionInstallState.RepairRequired)
        {
            await InstallOrRepairVersionAsync(spec, settings, onStage, null, onGameLog, ct).ConfigureAwait(false);
        }
        else if (spec.State == VersionInstallState.UpdateAvailable)
        {
            UpdateModJarOnly(spec, onGameLog);
        }

        spec.State = VersionInstallState.Launching;

        // Stage 10: Validate / Refresh Account Session
        onStage?.Invoke(new LaunchStageProgress(10, 13, "Validating Minecraft session token...", 0.80));
        MinecraftAccountRecord activeAccount = account;
        if (account.AccountType == "Microsoft" && DateTime.UtcNow >= account.ExpiresAtUtc)
        {
            activeAccount = await _authService.RefreshSessionAsync(account, onGameLog, ct).ConfigureAwait(false);
        }
        if (activeAccount.AccountType == "Microsoft" && !activeAccount.OwnsMinecraftJava)
        {
            spec.State = VersionInstallState.Installed;
            throw new InvalidOperationException("Selected Microsoft account does not own Minecraft: Java Edition.");
        }

        // Stage 11: Sync Launcher Theme & Module Toggles into Instance Config (non-destructive)
        onStage?.Invoke(new LaunchStageProgress(11, 13, "Syncing AXSESCLIENT configuration & theme...", 0.86));
        string instDir = GetInstanceDir(spec.MinecraftVersion);
        DeployBundledModJar(spec, instDir, onGameLog);
        SyncInstanceClientConfig(instDir, settings.ActiveThemeId, settings.Language, settings.ReducedMotion, launcherModules);

        // Stage 12: Build Complete JVM + Game Launch Command
        onStage?.Invoke(new LaunchStageProgress(12, 13, "Constructing JVM classpath & KnotClient arguments...", 0.92));
        string? javaOverride = spec.RequiredJavaMajor == 8 ? settings.Java8Path : settings.Java21Path;
        var javaRt = await _javaLocator.EnsureJavaRuntimeAsync(spec.RequiredJavaMajor, javaOverride, null, ct).ConfigureAwait(false);

        var args = BuildLaunchArguments(spec, profile, activeAccount, instDir);

        var psi = new ProcessStartInfo
        {
            FileName = javaRt.ExecutablePath,
            WorkingDirectory = instDir,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        };
        foreach (string a in args)
            psi.ArgumentList.Add(a);

        string safePreview = SecureTokenVault.ScrubSecrets($"{psi.FileName} {string.Join(' ', args.Take(12))} ...");
        onGameLog?.Invoke($"[Launch] Spawning JVM: {safePreview}");

        // Stage 13: Start Process & Attach Secret-Scrubbed Log Monitors
        onStage?.Invoke(new LaunchStageProgress(13, 13, $"Launching Minecraft {spec.MinecraftVersion}...", 1.0));
        var proc = new Process { StartInfo = psi, EnableRaisingEvents = true };
        proc.OutputDataReceived += (_, e) =>
        {
            if (e.Data is not null)
                onGameLog?.Invoke(SecureTokenVault.ScrubSecrets(e.Data));
        };
        proc.ErrorDataReceived += (_, e) =>
        {
            if (e.Data is not null)
                onGameLog?.Invoke("[STDERR] " + SecureTokenVault.ScrubSecrets(e.Data));
        };
        proc.Exited += (_, _) =>
        {
            int code = 0;
            try { code = proc.ExitCode; } catch { }
            spec.State = VersionInstallState.Installed;
            RefreshVersionState(spec);
            onGameLog?.Invoke($"[Lifecycle] Minecraft {spec.MinecraftVersion} exited with code {code}.");
            onExited?.Invoke(code);
        };

        if (!proc.Start())
        {
            spec.State = VersionInstallState.Installed;
            throw new InvalidOperationException("Failed to start Java process.");
        }

        proc.BeginOutputReadLine();
        proc.BeginErrorReadLine();
        spec.State = VersionInstallState.Running;
        spec.StatusDetail = $"Running (PID {proc.Id}) • {spec.DisplayTitle}";
        return proc;
    }

    public List<string> BuildLaunchArguments(
        SupportedVersionSpec spec,
        LaunchProfile profile,
        MinecraftAccountRecord account,
        string instanceDir)
    {
        string verDir = GetVersionMetaDir(spec.MinecraftVersion);
        string libsDir = GetLibrariesDir();
        string assetsDir = GetAssetsDir();
        string nativesDir = Path.Combine(verDir, "natives");

        using var vanillaDoc = JsonDocument.Parse(File.ReadAllText(Path.Combine(verDir, $"{spec.MinecraftVersion}.json")));
        using var fabricDoc = JsonDocument.Parse(File.ReadAllText(Path.Combine(verDir, $"fabric-{spec.MinecraftVersion}.json")));

        var libs = ResolveRequiredLibraries(vanillaDoc.RootElement, fabricDoc.RootElement, libsDir);
        var cpEntries = new List<string>();
        foreach (var lib in libs.Where(l => !l.IsNativeClassifierOnly))
        {
            if (!cpEntries.Contains(lib.LocalPath, StringComparer.OrdinalIgnoreCase))
                cpEntries.Add(lib.LocalPath);
        }
        cpEntries.Add(Path.Combine(verDir, $"{spec.MinecraftVersion}.jar"));
        string classpath = string.Join(Path.PathSeparator, cpEntries);

        string assetIndexId = vanillaDoc.RootElement.GetProperty("assetIndex").GetProperty("id").GetString() ?? spec.MinecraftVersion;
        string mainClass = fabricDoc.RootElement.TryGetProperty("mainClass", out var mcEl)
            ? (mcEl.GetString() ?? "net.fabricmc.loader.impl.launch.knot.KnotClient")
            : "net.fabricmc.loader.impl.launch.knot.KnotClient";

        string rawAccessToken = _authService.GetDecryptedAccessToken(account);
        if (string.IsNullOrWhiteSpace(rawAccessToken)) rawAccessToken = "0";
        SecureTokenVault.RegisterSensitiveToken(rawAccessToken);

        int ramMb = Math.Clamp(profile.RamMb, 1024, 32768);
        int minRamMb = Math.Min(1024, ramMb / 2);

        var jvmArgs = new List<string>
        {
            $"-Xms{minRamMb}M",
            $"-Xmx{ramMb}M",
            $"-Djava.library.path={nativesDir}",
            "-Dminecraft.launcher.brand=AXSESCLIENT",
            "-Dminecraft.launcher.version=1.0.0",
            "-DFabricMcEmu=net.minecraft.client.main.Main"
        };

        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            jvmArgs.Add("-XstartOnFirstThread");

        if (!string.IsNullOrWhiteSpace(profile.CustomJvmArgs))
        {
            foreach (string token in profile.CustomJvmArgs.Split(' ', StringSplitOptions.RemoveEmptyEntries))
                jvmArgs.Add(token);
        }

        jvmArgs.Add("-cp");
        jvmArgs.Add(classpath);
        jvmArgs.Add(mainClass);

        var gameArgs = new List<string>
        {
            "--username", account.Username,
            "--version", $"fabric-loader-{spec.FabricLoaderVersion}-{spec.MinecraftVersion}",
            "--gameDir", instanceDir,
            "--assetsDir", assetsDir,
            "--assetIndex", assetIndexId,
            "--uuid", string.IsNullOrWhiteSpace(account.Uuid) ? "00000000000000000000000000000000" : account.Uuid,
            "--accessToken", rawAccessToken,
            "--userType", account.AccountType == "Microsoft" ? "msa" : "legacy",
            "--versionType", "AXSESCLIENT-1.0.0",
            "--width", Math.Max(640, profile.ResolutionWidth).ToString(),
            "--height", Math.Max(480, profile.ResolutionHeight).ToString()
        };

        if (profile.Fullscreen)
            gameArgs.Add("--fullscreen");

        jvmArgs.AddRange(gameArgs);
        return jvmArgs;
    }

    private void DeployBundledModJar(SupportedVersionSpec spec, string instanceDir, Action<string>? onLog)
    {
        string modsDir = Path.Combine(instanceDir, "mods");
        Directory.CreateDirectory(modsDir);

        // Remove any mismatched AXSESCLIENT-*.jar from this version's isolated mods directory
        foreach (string existing in Directory.EnumerateFiles(modsDir, "AXSESCLIENT-*.jar"))
        {
            if (!string.Equals(Path.GetFileName(existing), spec.ModJarFileName, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(existing);
            }
        }

        string? sourceJar = LocateBundledModJar(spec.ModJarFileName);
        if (sourceJar is not null && File.Exists(sourceJar))
        {
            string destJar = Path.Combine(modsDir, spec.ModJarFileName);
            File.Copy(sourceJar, destJar, overwrite: true);
            onLog?.Invoke($"[Mods] Deployed {spec.ModJarFileName} -> {destJar}");
        }
        else
        {
            onLog?.Invoke($"[Mods] Notice: Bundled {spec.ModJarFileName} not found in launcher directory.");
        }
    }

    public static void SyncInstanceClientConfig(
        string instanceDir,
        string activeThemeId,
        string language,
        bool reducedMotion,
        IReadOnlyList<ModuleCatalogItem> modules)
    {
        string cfgDir = Path.Combine(instanceDir, "axsesclient", "configs");
        Directory.CreateDirectory(cfgDir);
        string defaultJsonPath = Path.Combine(cfgDir, "default.json");

        Dictionary<string, object?> rootDict = new();
        Dictionary<string, Dictionary<string, object?>> modulesDict = new();

        if (File.Exists(defaultJsonPath))
        {
            try
            {
                using var doc = JsonDocument.Parse(File.ReadAllText(defaultJsonPath));
                if (doc.RootElement.TryGetProperty("modules", out var existingMods) && existingMods.ValueKind == JsonValueKind.Object)
                {
                    foreach (var prop in existingMods.EnumerateObject())
                    {
                        var map = JsonSerializer.Deserialize<Dictionary<string, object?>>(prop.Value.GetRawText()) ?? new();
                        modulesDict[prop.Name] = map;
                    }
                }
                if (doc.RootElement.TryGetProperty("hud", out var existingHud))
                    rootDict["hud"] = JsonSerializer.Deserialize<object>(existingHud.GetRawText());
                if (doc.RootElement.TryGetProperty("guiScale", out var gs))
                    rootDict["guiScale"] = gs.GetDouble();
            }
            catch
            {
                // Recover cleanly if corrupt
            }
        }

        foreach (var item in modules)
        {
            if (!modulesDict.TryGetValue(item.Id, out var mObj))
            {
                mObj = new Dictionary<string, object?> { ["keybind"] = 0, ["settings"] = new Dictionary<string, object?>() };
                modulesDict[item.Id] = mObj;
            }
            mObj["enabled"] = item.Enabled;
            mObj["favorite"] = item.Favorite;
        }

        rootDict["schemaVersion"] = 1;
        rootDict["clientVersion"] = "1.0.0";
        rootDict["theme"] = activeThemeId;
        rootDict["language"] = language;
        rootDict["reducedMotion"] = reducedMotion;
        rootDict["modules"] = modulesDict;

        string tmp = defaultJsonPath + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(rootDict, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(tmp, defaultJsonPath, overwrite: true);
    }

    public sealed record ResolvedLibrary(
        string ArtifactKey,
        string Url,
        string LocalPath,
        string? Sha1,
        bool ExtractNatives,
        bool IsNativeClassifierOnly);

    public static List<ResolvedLibrary> ResolveRequiredLibraries(
        JsonElement vanillaRoot,
        JsonElement fabricRoot,
        string librariesDir)
    {
        var byKey = new Dictionary<string, ResolvedLibrary>(StringComparer.OrdinalIgnoreCase);

        // 1. Add Fabric libraries FIRST so newer ASM/Mixin overrides older vanilla libraries
        if (fabricRoot.TryGetProperty("libraries", out var fabLibs) && fabLibs.ValueKind == JsonValueKind.Array)
        {
            foreach (var lib in fabLibs.EnumerateArray())
            {
                string name = lib.GetProperty("name").GetString() ?? "";
                string baseUrl = lib.TryGetProperty("url", out var u) ? (u.GetString() ?? "https://maven.fabricmc.net/") : "https://maven.fabricmc.net/";
                if (!baseUrl.EndsWith('/')) baseUrl += "/";
                string? sha1 = lib.TryGetProperty("sha1", out var s) ? s.GetString() : null;

                var (groupArtifactKey, relPath) = MavenCoordinateToPath(name);
                string url = baseUrl + relPath.Replace('\\', '/');
                string local = Path.Combine(librariesDir, relPath);
                byKey[groupArtifactKey] = new ResolvedLibrary(groupArtifactKey, url, local, sha1, false, false);
            }
        }

        // 2. Add Mojang libraries obeying OS rules
        string currentOs = GetMojangOsName();
        if (vanillaRoot.TryGetProperty("libraries", out var mcLibs) && mcLibs.ValueKind == JsonValueKind.Array)
        {
            foreach (var lib in mcLibs.EnumerateArray())
            {
                if (!IsLibraryAllowedByRules(lib, currentOs))
                    continue;

                string name = lib.GetProperty("name").GetString() ?? "";
                var (groupArtifactKey, relPath) = MavenCoordinateToPath(name);

                if (lib.TryGetProperty("downloads", out var dls))
                {
                    if (dls.TryGetProperty("artifact", out var art) && !byKey.ContainsKey(groupArtifactKey))
                    {
                        string p = art.TryGetProperty("path", out var pEl) ? (pEl.GetString() ?? relPath) : relPath;
                        if (RuntimeInformation.OSArchitecture == Architecture.X64 &&
                            (p.Contains("-arm64.jar", StringComparison.OrdinalIgnoreCase) ||
                             p.Contains("-aarch64.jar", StringComparison.OrdinalIgnoreCase) ||
                             p.Contains("-x86.jar", StringComparison.OrdinalIgnoreCase)))
                        {
                            continue;
                        }
                        string url = art.GetProperty("url").GetString() ?? "";
                        string? sha1 = art.TryGetProperty("sha1", out var sEl) ? sEl.GetString() : null;
                        string local = Path.Combine(librariesDir, p.Replace('/', Path.DirectorySeparatorChar));
                        if (!string.IsNullOrEmpty(url))
                        {
                            // LWJGL 3.3+ (1.19+) loads natives directly from classpath JARs; only 1.16.5 classifiers need flat extraction
                            byKey[groupArtifactKey] = new ResolvedLibrary(groupArtifactKey, url, local, sha1, false, false);
                        }
                    }

                    // 1.16.5-style classifiers
                    if (lib.TryGetProperty("natives", out var nativesMap) &&
                        nativesMap.TryGetProperty(currentOs, out var classifierTemplate) &&
                        dls.TryGetProperty("classifiers", out var classifiers))
                    {
                        string archBits = Environment.Is64BitOperatingSystem ? "64" : "32";
                        string classifierKey = (classifierTemplate.GetString() ?? "").Replace("${arch}", archBits);
                        if (classifiers.TryGetProperty(classifierKey, out var clf))
                        {
                            string p = clf.GetProperty("path").GetString()!;
                            string url = clf.GetProperty("url").GetString()!;
                            string? sha1 = clf.TryGetProperty("sha1", out var sEl) ? sEl.GetString() : null;
                            string local = Path.Combine(librariesDir, p.Replace('/', Path.DirectorySeparatorChar));
                            string natKey = $"{groupArtifactKey}:{classifierKey}";
                            byKey[natKey] = new ResolvedLibrary(natKey, url, local, sha1, true, true);
                        }
                    }
                }
            }
        }

        return byKey.Values.ToList();
    }

    public static bool IsLibraryAllowedByRules(JsonElement libElement, string currentOs)
    {
        if (!libElement.TryGetProperty("rules", out var rules) || rules.ValueKind != JsonValueKind.Array)
            return true;

        bool allowed = false;
        foreach (var rule in rules.EnumerateArray())
        {
            string action = rule.GetProperty("action").GetString() ?? "allow";
            bool matches = true;

            if (rule.TryGetProperty("os", out var osObj))
            {
                if (osObj.TryGetProperty("name", out var osName) &&
                    !string.Equals(osName.GetString(), currentOs, StringComparison.OrdinalIgnoreCase))
                {
                    matches = false;
                }
                if (matches && osObj.TryGetProperty("arch", out var archEl))
                {
                    string reqArch = archEl.GetString() ?? "";
                    if (reqArch == "x86" && Environment.Is64BitOperatingSystem)
                        matches = false;
                }
            }

            if (matches)
                allowed = string.Equals(action, "allow", StringComparison.OrdinalIgnoreCase);
        }
        return allowed;
    }

    public static (string GroupArtifactKey, string RelativePath) MavenCoordinateToPath(string coordinate)
    {
        // group:artifact:version[:classifier]
        string[] parts = coordinate.Split(':');
        if (parts.Length < 3)
            throw new FormatException($"Invalid Maven coordinate: {coordinate}");

        string group = parts[0];
        string artifact = parts[1];
        string version = parts[2];
        string? classifier = parts.Length >= 4 ? parts[3] : null;

        string fileName = classifier is null
            ? $"{artifact}-{version}.jar"
            : $"{artifact}-{version}-{classifier}.jar";

        string groupPath = group.Replace('.', Path.DirectorySeparatorChar);
        string relPath = Path.Combine(groupPath, artifact, version, fileName);
        string dedupKey = classifier is null ? $"{group}:{artifact}" : $"{group}:{artifact}:{classifier}";
        return (dedupKey, relPath);
    }

    private async Task<string> FetchMojangVersionJsonAsync(string mcVersion, string verDir, CancellationToken ct)
    {
        string targetPath = Path.Combine(verDir, $"{mcVersion}.json");
        string manifestJson = await _http.GetStringAsync(MojangVersionManifestUrl, ct).ConfigureAwait(false);
        using var doc = JsonDocument.Parse(manifestJson);
        foreach (var v in doc.RootElement.GetProperty("versions").EnumerateArray())
        {
            if (string.Equals(v.GetProperty("id").GetString(), mcVersion, StringComparison.OrdinalIgnoreCase))
            {
                string url = v.GetProperty("url").GetString()!;
                string? sha1 = v.TryGetProperty("sha1", out var s) ? s.GetString() : null;
                await DownloadAndVerifyFileAsync(url, targetPath, sha1, $"{mcVersion}.json", "Version Metadata", null, ct).ConfigureAwait(false);
                return targetPath;
            }
        }
        throw new InvalidOperationException($"Minecraft version '{mcVersion}' not found in official Mojang manifest.");
    }

    private static List<ResolvedLibrary> ParseAssetObjectDownloads(string assetIndexJson, string assetsDir)
    {
        bool skipAudioForFastCi = Environment.GetEnvironmentVariable("AXSES_FAST_CI_ASSETS") == "1";
        var results = new Dictionary<string, ResolvedLibrary>(StringComparer.OrdinalIgnoreCase);
        using var doc = JsonDocument.Parse(assetIndexJson);
        if (!doc.RootElement.TryGetProperty("objects", out var objects)) return [];

        foreach (var prop in objects.EnumerateObject())
        {
            if (skipAudioForFastCi && prop.Name.EndsWith(".ogg", StringComparison.OrdinalIgnoreCase))
                continue;

            string hash = prop.Value.GetProperty("hash").GetString()!;
            string prefix = hash[..2];
            string url = $"{MojangResourcesBaseUrl}/{prefix}/{hash}";
            string local = Path.Combine(assetsDir, "objects", prefix, hash);
            if (!results.ContainsKey(hash))
                results[hash] = new ResolvedLibrary(hash, url, local, hash, false, false);
        }
        return results.Values.ToList();
    }

    private async Task DownloadBatchAsync(
        IReadOnlyList<ResolvedLibrary> items,
        int maxConcurrency,
        string stageName,
        Action<double>? onBatchProgress,
        Action<DownloadTaskItem>? onTaskCreated,
        CancellationToken ct)
    {
        if (items.Count == 0) return;
        int completed = 0;
        using var sem = new SemaphoreSlim(maxConcurrency);
        var tasks = items.Select(async item =>
        {
            await sem.WaitAsync(ct).ConfigureAwait(false);
            try
            {
                await DownloadAndVerifyFileAsync(
                    item.Url, item.LocalPath, item.Sha1, Path.GetFileName(item.LocalPath), stageName, onTaskCreated, ct).ConfigureAwait(false);
                int done = Interlocked.Increment(ref completed);
                if (done % 10 == 0 || done == items.Count)
                    onBatchProgress?.Invoke((double)done / items.Count);
            }
            finally
            {
                sem.Release();
            }
        });
        await Task.WhenAll(tasks).ConfigureAwait(false);
    }

    private async Task DownloadAndVerifyFileAsync(
        string url,
        string localPath,
        string? expectedSha1,
        string displayName,
        string stageName,
        Action<DownloadTaskItem>? onTaskUpdate,
        CancellationToken ct)
    {
        if (File.Exists(localPath))
        {
            if (string.IsNullOrWhiteSpace(expectedSha1))
            {
                if (new FileInfo(localPath).Length > 0) return;
            }
            else if (string.Equals(ComputeSha1Hex(localPath), expectedSha1, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }
        }

        Directory.CreateDirectory(Path.GetDirectoryName(localPath)!);
        var taskItem = new DownloadTaskItem { FileName = displayName, Stage = stageName, Status = "Downloading" };
        onTaskUpdate?.Invoke(taskItem);

        Exception? lastErr = null;
        for (int attempt = 1; attempt <= 3; attempt++)
        {
            string tmpPath = $"{localPath}.tmp.{Guid.NewGuid():N}";
            try
            {
                var sw = Stopwatch.StartNew();
                using var resp = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
                resp.EnsureSuccessStatusCode();
                long total = resp.Content.Headers.ContentLength ?? 0;
                await using (var net = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false))
                await using (var fs = new FileStream(tmpPath, FileMode.Create, FileAccess.Write, FileShare.None, 65536, true))
                {
                    byte[] buf = new byte[65536];
                    long sum = 0;
                    int n;
                    while ((n = await net.ReadAsync(buf, ct).ConfigureAwait(false)) > 0)
                    {
                        await fs.WriteAsync(buf.AsMemory(0, n), ct).ConfigureAwait(false);
                        sum += n;
                        if (total > 0) taskItem.Progress = (double)sum / total;
                    }
                    double sec = Math.Max(0.05, sw.Elapsed.TotalSeconds);
                    taskItem.SpeedMbps = Math.Round((sum / (1024.0 * 1024.0)) / sec, 2);
                }

                if (!string.IsNullOrWhiteSpace(expectedSha1))
                {
                    string actualSha1 = ComputeSha1Hex(tmpPath);
                    if (!string.Equals(actualSha1, expectedSha1, StringComparison.OrdinalIgnoreCase))
                        throw new CryptographicException($"SHA-1 mismatch for {displayName}: expected {expectedSha1}, got {actualSha1}");
                }

                File.Move(tmpPath, localPath, overwrite: true);
                taskItem.Progress = 1.0;
                taskItem.ShaVerified = true;
                taskItem.Status = "Verified";
                return;
            }
            catch (Exception ex) when (ex is not OperationCanceledException)
            {
                lastErr = ex;
                try { if (File.Exists(tmpPath)) File.Delete(tmpPath); } catch { }
                if (attempt < 3)
                    await Task.Delay(TimeSpan.FromMilliseconds(350 * attempt), ct).ConfigureAwait(false);
            }
        }

        taskItem.Status = "Failed";
        throw new IOException($"Failed to download {displayName} after 3 attempts: {lastErr?.Message}", lastErr);
    }

    private async Task DownloadStringToFileAsync(string url, string destPath, CancellationToken ct)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(destPath)!);
        string content = await _http.GetStringAsync(url, ct).ConfigureAwait(false);
        string tmp = destPath + ".tmp";
        await File.WriteAllTextAsync(tmp, content, Encoding.UTF8, ct).ConfigureAwait(false);
        File.Move(tmp, destPath, overwrite: true);
    }

    private static void ExtractNativeBinaries(IEnumerable<ResolvedLibrary> libraries, string nativesDir)
    {
        Directory.CreateDirectory(nativesDir);
        foreach (var lib in libraries.Where(l => l.ExtractNatives && File.Exists(l.LocalPath)))
        {
            using var archive = ZipFile.OpenRead(lib.LocalPath);
            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name)) continue;
                if (entry.FullName.StartsWith("META-INF/", StringComparison.OrdinalIgnoreCase)) continue;
                string ext = Path.GetExtension(entry.Name).ToLowerInvariant();
                if (ext is ".dll" or ".so" or ".dylib" or ".jnilib")
                {
                    string outPath = Path.Combine(nativesDir, entry.Name);
                    entry.ExtractToFile(outPath, overwrite: true);
                }
            }
        }
    }

    private static void ReportStage(
        SupportedVersionSpec spec,
        Action<LaunchStageProgress>? cb,
        int stage,
        int total,
        string message,
        double progress)
    {
        spec.Progress = progress;
        spec.StatusDetail = message;
        cb?.Invoke(new LaunchStageProgress(stage, total, message, progress));
    }

    private static string GetMojangOsName()
    {
        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows)) return "windows";
        if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX)) return "osx";
        return "linux";
    }

    public static string ComputeSha1Hex(string filePath)
    {
        using var fs = File.OpenRead(filePath);
        return Convert.ToHexString(SHA1.HashData(fs)).ToLowerInvariant();
    }

    public static string ComputeSha256Hex(string filePath)
    {
        using var fs = File.OpenRead(filePath);
        return Convert.ToHexString(SHA256.HashData(fs)).ToLowerInvariant();
    }

    private static long ComputeDirSizeSafe(string dir)
    {
        if (!Directory.Exists(dir)) return 0L;
        long sum = 0L;
        try
        {
            foreach (string f in Directory.EnumerateFiles(dir, "*", SearchOption.AllDirectories))
            {
                try { sum += new FileInfo(f).Length; } catch { }
            }
        }
        catch { }
        return sum;
    }
}
