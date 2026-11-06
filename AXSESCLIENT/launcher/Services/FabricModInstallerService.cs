using System.Security.Cryptography;
using System.Text.Json;
using AxsesClient.Launcher.Models;

namespace AxsesClient.Launcher.Services;

/// <summary>
/// Real Modrinth v2 &amp; GitHub Releases Fabric mod manager for Minecraft 1.16.5, 1.21.4 &amp; 1.21.11.
/// Installs popular optimization mods (Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling,
/// Iris Shaders, ModernFix, Dynamic FPS, Krypton, ModMenu, Fabric API) and Baritone AI pathfinder
/// directly into the selected instance's mods/ folder with cryptographic SHA verification.
/// </summary>
public sealed class FabricModInstallerService
{
    public const string ModrinthApiBase = "https://api.modrinth.com/v2";
    public const string BaritoneGitHubReleasesUrl = "https://api.github.com/repos/cabaletta/baritone/releases?per_page=15";

    private readonly MinecraftLaunchEngine _launchEngine;
    private readonly HttpClient _http;

    public FabricModInstallerService(MinecraftLaunchEngine launchEngine, HttpClient? httpClient = null)
    {
        _launchEngine = launchEngine;
        _http = httpClient ?? CreateHttpClient();
    }

    private static HttpClient CreateHttpClient()
    {
        var handler = new SocketsHttpHandler
        {
            MaxConnectionsPerServer = 12,
            PooledConnectionLifetime = TimeSpan.FromMinutes(5)
        };
        var client = new HttpClient(handler) { Timeout = TimeSpan.FromMinutes(3) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("AXSESCLIENT-Launcher/4.0.0 (egorbelovziiik-design/WinLauncher)");
        return client;
    }

    public string GetModsFolder(string mcVersion)
    {
        string dir = Path.Combine(_launchEngine.GetInstanceDir(mcVersion), "mods");
        Directory.CreateDirectory(dir);
        return dir;
    }

    public void RefreshCatalogState(string mcVersion, IReadOnlyList<CuratedFabricModSpec> catalog)
    {
        string modsDir = GetModsFolder(mcVersion);
        var files = Directory.Exists(modsDir)
            ? Directory.GetFiles(modsDir).Select(Path.GetFileName).Where(f => f is not null).Cast<string>().ToList()
            : [];

        foreach (var mod in catalog)
        {
            string? matchedFile = files.FirstOrDefault(f =>
                !f.StartsWith("AXSESCLIENT-", StringComparison.OrdinalIgnoreCase) &&
                mod.FilePrefixes.Any(p => f.StartsWith(p, StringComparison.OrdinalIgnoreCase)) &&
                (f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || f.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)));

            if (matchedFile is not null)
            {
                mod.IsInstalled = true;
                mod.IsEnabled = matchedFile.EndsWith(".jar", StringComparison.OrdinalIgnoreCase);
                mod.InstalledFileName = matchedFile;
            }
            else
            {
                mod.IsInstalled = false;
                mod.IsEnabled = false;
                mod.InstalledFileName = "";
            }
        }
    }

    public bool ToggleModEnabled(CuratedFabricModSpec mod, string mcVersion)
    {
        string modsDir = GetModsFolder(mcVersion);
        if (!mod.IsInstalled || string.IsNullOrWhiteSpace(mod.InstalledFileName))
            return false;

        string currentPath = Path.Combine(modsDir, mod.InstalledFileName);
        if (!File.Exists(currentPath))
            return false;

        string nextName = mod.InstalledFileName.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase)
            ? mod.InstalledFileName[..^".disabled".Length]
            : mod.InstalledFileName + ".disabled";

        string nextPath = Path.Combine(modsDir, nextName);
        File.Move(currentPath, nextPath, overwrite: true);
        mod.InstalledFileName = nextName;
        mod.IsEnabled = nextName.EndsWith(".jar", StringComparison.OrdinalIgnoreCase);
        return true;
    }

    public bool UninstallMod(CuratedFabricModSpec mod, string mcVersion)
    {
        string modsDir = GetModsFolder(mcVersion);
        if (string.IsNullOrWhiteSpace(mod.InstalledFileName))
            return false;

        string path = Path.Combine(modsDir, mod.InstalledFileName);
        if (File.Exists(path))
            File.Delete(path);

        mod.IsInstalled = false;
        mod.IsEnabled = false;
        mod.InstalledFileName = "";
        return true;
    }

    public async Task<bool> InstallModAsync(
        CuratedFabricModSpec mod,
        string mcVersion,
        Action<string>? onLog = null,
        CancellationToken ct = default)
    {
        string modsDir = GetModsFolder(mcVersion);
        onLog?.Invoke($"[ModsHub] Поиск совместимой сборки {mod.Name} для Fabric {mcVersion}...");

        ResolvedModDownload? resolved = await ResolveModrinthDownloadAsync(mod.ModrinthSlug, mcVersion, ct).ConfigureAwait(false);
        if (resolved is null && !string.IsNullOrWhiteSpace(mod.FallbackModrinthSlug))
        {
            resolved = await ResolveModrinthDownloadAsync(mod.FallbackModrinthSlug, mcVersion, ct).ConfigureAwait(false);
        }
        if (resolved is null && mod.Id == "baritone")
        {
            resolved = await ResolveBaritoneGitHubReleaseAsync(mcVersion, ct).ConfigureAwait(false);
        }

        if (resolved is null)
        {
            onLog?.Invoke($"[ModsHub] Официальный билд {mod.Name} для {mcVersion} пока не опубликован автором мода.");
            return false;
        }

        string tempFile = Path.Combine(modsDir, resolved.FileName + ".part");
        byte[] bytes = await _http.GetByteArrayAsync(resolved.DownloadUrl, ct).ConfigureAwait(false);
        if (bytes.Length < 512 || bytes[0] != (byte)'P' || bytes[1] != (byte)'K')
            throw new InvalidDataException($"Downloaded archive for {mod.Name} is not a valid JAR.");

        if (!string.IsNullOrWhiteSpace(resolved.Sha512))
        {
            string actual512 = Convert.ToHexString(SHA512.HashData(bytes)).ToLowerInvariant();
            if (!string.Equals(actual512, resolved.Sha512, StringComparison.OrdinalIgnoreCase))
                throw new CryptographicException($"SHA-512 mismatch for {resolved.FileName}");
        }
        else if (!string.IsNullOrWhiteSpace(resolved.Sha1))
        {
            string actual1 = Convert.ToHexString(SHA1.HashData(bytes)).ToLowerInvariant();
            if (!string.Equals(actual1, resolved.Sha1, StringComparison.OrdinalIgnoreCase))
                throw new CryptographicException($"SHA-1 mismatch for {resolved.FileName}");
        }

        // Remove any older version of the same mod before placing the new JAR
        RefreshCatalogState(mcVersion, [mod]);
        if (mod.IsInstalled && !string.IsNullOrWhiteSpace(mod.InstalledFileName))
        {
            string oldPath = Path.Combine(modsDir, mod.InstalledFileName);
            if (File.Exists(oldPath)) File.Delete(oldPath);
        }

        await File.WriteAllBytesAsync(tempFile, bytes, ct).ConfigureAwait(false);
        string finalPath = Path.Combine(modsDir, resolved.FileName);
        File.Move(tempFile, finalPath, overwrite: true);

        mod.IsInstalled = true;
        mod.IsEnabled = true;
        mod.InstalledFileName = resolved.FileName;
        onLog?.Invoke($"[ModsHub] ✓ Установлен {mod.Name} ({resolved.FileName}, {bytes.Length / 1024} КБ)");
        return true;
    }

    public async Task<int> InstallOptimizationAndBaritoneBundleAsync(
        string mcVersion,
        IReadOnlyList<CuratedFabricModSpec> catalog,
        Action<string, double>? onProgress = null,
        Action<string>? onLog = null,
        CancellationToken ct = default)
    {
        var recommended = catalog.Where(m => m.IncludedInRecommendedPack).ToList();
        int installedCount = 0;
        for (int i = 0; i < recommended.Count; i++)
        {
            var m = recommended[i];
            double prog = (double)i / Math.Max(1, recommended.Count);
            onProgress?.Invoke($"Установка {m.Name} ({i + 1}/{recommended.Count})...", prog);
            try
            {
                if (await InstallModAsync(m, mcVersion, onLog, ct).ConfigureAwait(false))
                    installedCount++;
            }
            catch (Exception ex)
            {
                onLog?.Invoke($"[ModsHub] Пропуск {m.Name}: {ex.Message}");
            }
        }
        onProgress?.Invoke($"Готово: установлено {installedCount} оптимизирующих модов", 1.0);
        return installedCount;
    }

    private async Task<ResolvedModDownload?> ResolveModrinthDownloadAsync(string slug, string mcVersion, CancellationToken ct)
    {
        if (string.IsNullOrWhiteSpace(slug)) return null;
        string url = $"{ModrinthApiBase}/project/{Uri.EscapeDataString(slug)}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{Uri.EscapeDataString(mcVersion)}%22%5D";
        using var resp = await _http.GetAsync(url, ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode) return null;

        using var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct).ConfigureAwait(false);
        if (doc.RootElement.ValueKind != JsonValueKind.Array || doc.RootElement.GetArrayLength() == 0)
            return null;

        var firstVer = doc.RootElement[0];
        if (!firstVer.TryGetProperty("files", out var filesArr) || filesArr.GetArrayLength() == 0)
            return null;

        JsonElement chosen = filesArr[0];
        foreach (var f in filesArr.EnumerateArray())
        {
            if (f.TryGetProperty("primary", out var prim) && prim.GetBoolean())
            {
                chosen = f;
                break;
            }
        }

        string? dlUrl = chosen.TryGetProperty("url", out var u) ? u.GetString() : null;
        string? fileName = chosen.TryGetProperty("filename", out var fn) ? fn.GetString() : null;
        if (string.IsNullOrWhiteSpace(dlUrl) || string.IsNullOrWhiteSpace(fileName))
            return null;

        string? sha512 = null;
        string? sha1 = null;
        if (chosen.TryGetProperty("hashes", out var hashes))
        {
            if (hashes.TryGetProperty("sha512", out var s512)) sha512 = s512.GetString();
            if (hashes.TryGetProperty("sha1", out var s1)) sha1 = s1.GetString();
        }

        return new ResolvedModDownload(fileName, dlUrl, sha512, sha1);
    }

    private async Task<ResolvedModDownload?> ResolveBaritoneGitHubReleaseAsync(string mcVersion, CancellationToken ct)
    {
        using var resp = await _http.GetAsync(BaritoneGitHubReleasesUrl, ct).ConfigureAwait(false);
        if (!resp.IsSuccessStatusCode) return null;

        using var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct).ConfigureAwait(false);
        if (doc.RootElement.ValueKind != JsonValueKind.Array) return null;

        string verPrefix = mcVersion.StartsWith("1.16") ? "1.16.5" : mcVersion;
        foreach (var rel in doc.RootElement.EnumerateArray())
        {
            if (!rel.TryGetProperty("assets", out var assets)) continue;
            foreach (var asset in assets.EnumerateArray())
            {
                string name = asset.TryGetProperty("name", out var n) ? (n.GetString() ?? "") : "";
                string dl = asset.TryGetProperty("browser_download_url", out var u) ? (u.GetString() ?? "") : "";
                if (name.Contains("fabric", StringComparison.OrdinalIgnoreCase) &&
                    name.Contains(verPrefix, StringComparison.OrdinalIgnoreCase) &&
                    name.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) &&
                    !name.Contains("-sources", StringComparison.OrdinalIgnoreCase) &&
                    !name.Contains("-dev", StringComparison.OrdinalIgnoreCase))
                {
                    return new ResolvedModDownload(name, dl, null, null);
                }
            }
        }
        return null;
    }

    public sealed record ResolvedModDownload(string FileName, string DownloadUrl, string? Sha512, string? Sha1);
}
