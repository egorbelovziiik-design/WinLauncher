using System.Diagnostics;
using System.IO.Compression;
using System.Net;
using System.Runtime.InteropServices;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace AxsesClient.Launcher.Services;

public sealed record DetectedJavaRuntime(string ExecutablePath, int MajorVersion, string RawVersion);

public sealed partial class JavaRuntimeLocator
{
    private readonly string _runtimesRoot;
    private readonly HttpClient _http;
    private readonly SemaphoreSlim _installLock = new(1, 1);

    public JavaRuntimeLocator(string rootDataDir, HttpClient? httpClient = null)
    {
        _runtimesRoot = Path.Combine(rootDataDir, "runtimes");
        if (httpClient is not null)
        {
            _http = httpClient;
        }
        else
        {
            var handler = new HttpClientHandler
            {
                AllowAutoRedirect = true,
                AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate
            };
            _http = new HttpClient(handler) { Timeout = TimeSpan.FromMinutes(12) };
            _http.DefaultRequestHeaders.UserAgent.ParseAdd("AXSESCLIENT-Launcher/2.0 (Windows NT 10.0; Win64; x64)");
        }
    }

    public List<DetectedJavaRuntime> ScanSystemRuntimes(string? preferredJava8 = null, string? preferredJava21 = null)
    {
        var candidates = new HashSet<string>(
            RuntimeInformation.IsOSPlatform(OSPlatform.Windows) ? StringComparer.OrdinalIgnoreCase : StringComparer.Ordinal);
        string exeName = RuntimeInformation.IsOSPlatform(OSPlatform.Windows) ? "java.exe" : "java";

        void AddIfFile(string? p)
        {
            if (!string.IsNullOrWhiteSpace(p) && File.Exists(p))
                candidates.Add(Path.GetFullPath(p));
        }

        AddIfFile(preferredJava8);
        AddIfFile(preferredJava21);

        // Managed AXSESCLIENT runtimes (highest priority)
        ScanDirectoryForJava(_runtimesRoot, exeName, candidates, maxDepth: 5);

        // JAVA_HOME & Environment Overrides
        foreach (string envVar in new[] { "JAVA_HOME", "JAVA_HOME_8_X64", "JAVA_HOME_21_X64" })
        {
            string? jh = Environment.GetEnvironmentVariable(envVar);
            if (!string.IsNullOrWhiteSpace(jh))
                AddIfFile(Path.Combine(jh, "bin", exeName));
        }

        // PATH
        string? pathEnv = Environment.GetEnvironmentVariable("PATH");
        if (!string.IsNullOrWhiteSpace(pathEnv))
        {
            foreach (string dir in pathEnv.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
            {
                try { AddIfFile(Path.Combine(dir.Trim(), exeName)); } catch { }
            }
        }

        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            string pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            string pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            string localApp = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);

            string[] winRoots =
            [
                Path.Combine(pf, "Eclipse Adoptium"),
                Path.Combine(pf, "Java"),
                Path.Combine(pf, "Microsoft"),
                Path.Combine(pf, "BellSoft"),
                Path.Combine(pf, "Zulu"),
                Path.Combine(pf86, "Minecraft Launcher", "runtime"),
                Path.Combine(appData, ".minecraft", "runtime"),
                Path.Combine(localApp, "Programs", "Eclipse Adoptium"),
                Path.Combine(localApp, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local", "runtime")
            ];
            foreach (string root in winRoots)
                ScanDirectoryForJava(root, exeName, candidates, maxDepth: 6);
        }
        else
        {
            ScanDirectoryForJava("/usr/lib/jvm", exeName, candidates, maxDepth: 4);
            ScanDirectoryForJava("/Library/Java/JavaVirtualMachines", exeName, candidates, maxDepth: 5);
        }

        var results = new List<DetectedJavaRuntime>();
        foreach (string javaExe in candidates)
        {
            var probed = ProbeJavaExecutable(javaExe);
            if (probed is not null)
                results.Add(probed);
        }

        return results
            .OrderByDescending(r => r.ExecutablePath.StartsWith(_runtimesRoot, StringComparison.OrdinalIgnoreCase))
            .ThenByDescending(r => r.MajorVersion)
            .ToList();
    }

    public DetectedJavaRuntime? FindMatchingRuntime(int requiredMajor, string? explicitOverride = null)
    {
        if (!string.IsNullOrWhiteSpace(explicitOverride) && File.Exists(explicitOverride))
        {
            var probed = ProbeJavaExecutable(explicitOverride);
            if (probed is not null && IsCompatibleMajor(probed.MajorVersion, requiredMajor))
                return probed;
        }

        var all = ScanSystemRuntimes();
        return all.FirstOrDefault(r => r.MajorVersion == requiredMajor)
               ?? all.FirstOrDefault(r => IsCompatibleMajor(r.MajorVersion, requiredMajor));
    }

    public async Task<DetectedJavaRuntime> EnsureJavaRuntimeAsync(
        int requiredMajor,
        string? explicitOverride = null,
        Action<string, double>? progressCallback = null,
        CancellationToken ct = default)
    {
        var existing = FindMatchingRuntime(requiredMajor, explicitOverride);
        if (existing is not null)
            return existing;

        await _installLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            // Re-check inside lock in case another task just finished installing it
            existing = FindMatchingRuntime(requiredMajor, explicitOverride);
            if (existing is not null)
                return existing;

            if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                throw new InvalidOperationException(
                    $"Java {requiredMajor} runtime not found on host. Install OpenJDK/Temurin {requiredMajor} or specify path in Settings.");
            }

            string targetDir = Path.Combine(_runtimesRoot, $"java-{requiredMajor}");
            Directory.CreateDirectory(_runtimesRoot);
            if (Directory.Exists(targetDir))
            {
                try { Directory.Delete(targetDir, recursive: true); } catch { }
            }
            Directory.CreateDirectory(targetDir);

            string zipPath = Path.Combine(_runtimesRoot, $"axses-jre-{requiredMajor}-win-x64.zip");
            var downloadUrls = await ResolveWindowsJreMirrorUrlsAsync(requiredMajor, ct).ConfigureAwait(false);

            Exception? lastErr = null;
            bool downloaded = false;

            foreach (var (providerName, url) in downloadUrls)
            {
                try
                {
                    progressCallback?.Invoke($"Скачивание Java {requiredMajor} ({providerName})...", 0.05);
                    using var resp = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
                    resp.EnsureSuccessStatusCode();
                    long total = resp.Content.Headers.ContentLength ?? 52_000_000L;

                    await using (var net = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false))
                    await using (var fs = new FileStream(zipPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
                    {
                        byte[] buf = new byte[81920];
                        long readSum = 0;
                        int n;
                        while ((n = await net.ReadAsync(buf, ct).ConfigureAwait(false)) > 0)
                        {
                            await fs.WriteAsync(buf.AsMemory(0, n), ct).ConfigureAwait(false);
                            readSum += n;
                            double frac = Math.Clamp((double)readSum / Math.Max(1L, total), 0.02, 0.90);
                            progressCallback?.Invoke(
                                $"Авто-установка Java {requiredMajor}: {readSum / (1024 * 1024)} / {Math.Max(1L, total / (1024 * 1024))} МБ ({(int)(frac * 100)}%)",
                                frac);
                        }
                    }

                    if (new FileInfo(zipPath).Length > 5_000_000L)
                    {
                        downloaded = true;
                        break;
                    }
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    lastErr = ex;
                }
            }

            if (!downloaded)
            {
                throw new InvalidOperationException(
                    $"Не удалось автоматически скачать Java {requiredMajor}: {lastErr?.Message ?? "все зеркала недоступны"}");
            }

            progressCallback?.Invoke($"Распаковка Java {requiredMajor}...", 0.94);
            ZipFile.ExtractToDirectory(zipPath, targetDir, overwriteFiles: true);
            try { File.Delete(zipPath); } catch { }

            var installed = FindMatchingRuntime(requiredMajor);
            if (installed is null)
                throw new InvalidOperationException($"Архив Java {requiredMajor} распакован, но java.exe не найден.");

            progressCallback?.Invoke($"Java {requiredMajor} успешно установлена ({installed.RawVersion})!", 1.0);
            return installed;
        }
        finally
        {
            _installLock.Release();
        }
    }

    private async Task<List<(string Provider, string Url)>> ResolveWindowsJreMirrorUrlsAsync(int requiredMajor, CancellationToken ct)
    {
        var mirrors = new List<(string Provider, string Url)>
        {
            ("Eclipse Temurin", $"https://api.adoptium.net/v3/binary/latest/{requiredMajor}/ga/windows/x64/jre/hotspot/normal/eclipse")
        };

        try
        {
            string zuluMetaUrl = $"https://api.azul.com/metadata/v1/zulu/packages/?java_version={requiredMajor}&os=windows&arch=x64&archive_type=zip&java_package_type=jre&latest=true&release_status=ga";
            using var resp = await _http.GetAsync(zuluMetaUrl, ct).ConfigureAwait(false);
            if (resp.IsSuccessStatusCode)
            {
                using var doc = JsonDocument.Parse(await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false));
                if (doc.RootElement.ValueKind == JsonValueKind.Array && doc.RootElement.GetArrayLength() > 0)
                {
                    var first = doc.RootElement[0];
                    if (first.TryGetProperty("download_url", out var dlEl) && dlEl.GetString() is { Length: > 10 } dlUrl)
                    {
                        mirrors.Add(("Azul Zulu OpenJDK", dlUrl));
                    }
                }
            }
        }
        catch
        {
            // Keep primary mirror if metadata probe fails
        }

        return mirrors;
    }

    public static DetectedJavaRuntime? ProbeJavaExecutable(string javaExePath)
    {
        if (!File.Exists(javaExePath)) return null;
        try
        {
            using var proc = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = javaExePath,
                    Arguments = "-version",
                    RedirectStandardError = true,
                    RedirectStandardOutput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }
            };
            proc.Start();
            string stderr = proc.StandardError.ReadToEnd();
            string stdout = proc.StandardOutput.ReadToEnd();
            proc.WaitForExit(4500);

            string combined = string.IsNullOrWhiteSpace(stderr) ? stdout : stderr;
            int major = ParseJavaMajorVersion(combined, out string rawVer);
            return major > 0 ? new DetectedJavaRuntime(javaExePath, major, rawVer) : null;
        }
        catch
        {
            return null;
        }
    }

    public static int ParseJavaMajorVersion(string versionOutput, out string rawVersion)
    {
        rawVersion = "unknown";
        if (string.IsNullOrWhiteSpace(versionOutput)) return 0;

        var m = VersionQuoteRegex().Match(versionOutput);
        if (!m.Success) return 0;

        rawVersion = m.Groups[1].Value;
        string[] parts = rawVersion.Split('.', '_', '-', '+');
        if (parts.Length == 0) return 0;

        if (parts[0] == "1" && parts.Length >= 2 && int.TryParse(parts[1], out int legacyMajor))
            return legacyMajor;

        return int.TryParse(parts[0], out int modernMajor) ? modernMajor : 0;
    }

    private static bool IsCompatibleMajor(int detected, int required) =>
        required == 8 ? (detected == 8) : (detected >= required);

    private static void ScanDirectoryForJava(string root, string exeName, HashSet<string> outPaths, int maxDepth, int depth = 0)
    {
        if (depth > maxDepth || string.IsNullOrWhiteSpace(root) || !Directory.Exists(root))
            return;

        try
        {
            string direct = Path.Combine(root, "bin", exeName);
            if (File.Exists(direct))
                outPaths.Add(Path.GetFullPath(direct));

            foreach (string sub in Directory.EnumerateDirectories(root))
                ScanDirectoryForJava(sub, exeName, outPaths, maxDepth, depth + 1);
        }
        catch
        {
        }
    }

    [GeneratedRegex(@"version\s+""([^""]+)""", RegexOptions.IgnoreCase)]
    private static partial Regex VersionQuoteRegex();
}
