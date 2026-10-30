using System.Diagnostics;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;

namespace AxsesClient.Launcher.Services;

public sealed record DetectedJavaRuntime(string ExecutablePath, int MajorVersion, string RawVersion);

public sealed partial class JavaRuntimeLocator
{
    private readonly string _runtimesRoot;
    private readonly HttpClient _http;

    public JavaRuntimeLocator(string rootDataDir, HttpClient? httpClient = null)
    {
        _runtimesRoot = Path.Combine(rootDataDir, "runtimes");
        _http = httpClient ?? new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
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

        // Managed AXSESCLIENT runtimes
        ScanDirectoryForJava(_runtimesRoot, exeName, candidates, maxDepth: 4);

        // JAVA_HOME
        string? javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrWhiteSpace(javaHome))
            AddIfFile(Path.Combine(javaHome, "bin", exeName));

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

            string[] winRoots =
            [
                Path.Combine(pf, "Eclipse Adoptium"),
                Path.Combine(pf, "Java"),
                Path.Combine(pf, "Microsoft"),
                Path.Combine(pf, "BellSoft"),
                Path.Combine(pf, "Zulu"),
                Path.Combine(pf86, "Java"),
                Path.Combine(localApp, "Programs", "Eclipse Adoptium"),
                Path.Combine(localApp, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local", "runtime")
            ];
            foreach (string root in winRoots)
                ScanDirectoryForJava(root, exeName, candidates, maxDepth: 5);
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

        if (!RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            throw new InvalidOperationException(
                $"Java {requiredMajor} runtime not found on system. Install Temurin/OpenJDK {requiredMajor} or specify path in Settings.");
        }

        // Auto-provision isolated Eclipse Temurin Windows x64 JRE inside %APPDATA%\AXSESCLIENT\runtimes\java-{major}
        string targetDir = Path.Combine(_runtimesRoot, $"java-{requiredMajor}");
        Directory.CreateDirectory(targetDir);
        string zipPath = Path.Combine(_runtimesRoot, $"temurin-jre-{requiredMajor}-win-x64.zip");
        string adoptiumUrl = $"https://api.adoptium.net/v3/binary/latest/{requiredMajor}/ga/windows/x64/jre/hotspot/normal/eclipse";

        progressCallback?.Invoke($"Downloading Eclipse Temurin Java {requiredMajor} Runtime...", 0.05);
        using (var resp = await _http.GetAsync(adoptiumUrl, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false))
        {
            resp.EnsureSuccessStatusCode();
            long total = resp.Content.Headers.ContentLength ?? 50_000_000L;
            await using var net = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            await using var fs = new FileStream(zipPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);
            byte[] buf = new byte[81920];
            long readSum = 0;
            int n;
            while ((n = await net.ReadAsync(buf, ct).ConfigureAwait(false)) > 0)
            {
                await fs.WriteAsync(buf.AsMemory(0, n), ct).ConfigureAwait(false);
                readSum += n;
                progressCallback?.Invoke(
                    $"Downloading Java {requiredMajor} Runtime ({readSum / (1024 * 1024)} MB)...",
                    Math.Clamp((double)readSum / total, 0.05, 0.90));
            }
        }

        progressCallback?.Invoke($"Extracting Java {requiredMajor} Runtime...", 0.94);
        ZipFile.ExtractToDirectory(zipPath, targetDir, overwriteFiles: true);
        File.Delete(zipPath);

        var installed = FindMatchingRuntime(requiredMajor);
        if (installed is null)
            throw new InvalidOperationException($"Extracted Java {requiredMajor} archive did not contain a valid java.exe.");

        progressCallback?.Invoke($"Java {requiredMajor} ready ({installed.RawVersion}).", 1.0);
        return installed;
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
            proc.WaitForExit(4000);

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
            return legacyMajor; // "1.8.0_422" -> 8

        return int.TryParse(parts[0], out int modernMajor) ? modernMajor : 0; // "21.0.5" -> 21
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
            // Ignore permission-restricted directories
        }
    }

    [GeneratedRegex(@"version\s+""([^""]+)""", RegexOptions.IgnoreCase)]
    private static partial Regex VersionQuoteRegex();
}
