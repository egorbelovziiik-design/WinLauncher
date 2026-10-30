using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using AxsesClient.Launcher.Models;

namespace AxsesClient.Launcher.Services;

public sealed record DiagnosticCheckItem(string Category, string Name, bool Passed, string Detail);

public sealed class DiagnosticsService
{
    private readonly JavaRuntimeLocator _javaLocator;
    private readonly MinecraftLaunchEngine _launchEngine;
    private readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(8) };

    public DiagnosticsService(JavaRuntimeLocator javaLocator, MinecraftLaunchEngine launchEngine)
    {
        _javaLocator = javaLocator;
        _launchEngine = launchEngine;
    }

    public async Task<List<DiagnosticCheckItem>> RunFullDiagnosticsAsync(
        LauncherSettings settings,
        IReadOnlyList<SupportedVersionSpec> versions,
        CancellationToken ct = default)
    {
        var items = new List<DiagnosticCheckItem>();

        // 1. OS & Hardware
        long totalRamMb = GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / (1024 * 1024);
        items.Add(new DiagnosticCheckItem(
            "System",
            "Operating System & Architecture",
            Environment.Is64BitOperatingSystem,
            $"{RuntimeInformation.OSDescription} ({RuntimeInformation.OSArchitecture}, {Environment.ProcessorCount} threads)"));

        items.Add(new DiagnosticCheckItem(
            "System",
            "Available System Memory",
            totalRamMb >= 4096,
            $"{totalRamMb:N0} MB detected (.NET {Environment.Version})"));

        // 2. Java Runtimes
        var j8 = _javaLocator.FindMatchingRuntime(8, settings.Java8Path);
        items.Add(new DiagnosticCheckItem(
            "Java",
            "Java 8 Runtime (for Minecraft 1.16.5)",
            j8 is not null,
            j8 is not null ? $"Java {j8.RawVersion} ({j8.ExecutablePath})" : "Not yet installed (Auto-provisioned on 1.16.5 install)"));

        var j21 = _javaLocator.FindMatchingRuntime(21, settings.Java21Path);
        items.Add(new DiagnosticCheckItem(
            "Java",
            "Java 21 Runtime (for Minecraft 1.21.4 & 1.21.11)",
            j21 is not null,
            j21 is not null ? $"Java {j21.RawVersion} ({j21.ExecutablePath})" : "Not yet installed (Auto-provisioned on 1.21.x install)"));

        // 3. Network Endpoints
        items.Add(await ProbeEndpointAsync("Mojang Piston Meta", MinecraftLaunchEngine.MojangVersionManifestUrl, ct).ConfigureAwait(false));
        items.Add(await ProbeEndpointAsync("Fabric Meta API", "https://meta.fabricmc.net/v2/versions/loader", ct).ConfigureAwait(false));

        // 4. Version & Mod JAR Integrity
        foreach (var spec in versions)
        {
            _launchEngine.RefreshVersionState(spec);
            string? modJar = _launchEngine.LocateBundledModJar(spec.ModJarFileName);
            bool hasBundledMod = modJar is not null && File.Exists(modJar);
            string detail = hasBundledMod
                ? $"State: {spec.State.ToDisplayBadge()} • Bundled {spec.ModJarFileName} verified ({new FileInfo(modJar!).Length / 1024} KB)"
                : $"State: {spec.State.ToDisplayBadge()} • Bundled {spec.ModJarFileName} not found next to launcher";
            items.Add(new DiagnosticCheckItem("Client JARs", spec.DisplayTitle, hasBundledMod, detail));
        }

        return items;
    }

    public static string FormatReport(IEnumerable<DiagnosticCheckItem> checks, IEnumerable<string> recentLogs)
    {
        var sb = new StringBuilder();
        sb.AppendLine("=== AXSESCLIENT (GENESIS X) v1.0.0 DIAGNOSTIC REPORT ===");
        sb.AppendLine($"Generated UTC: {DateTime.UtcNow:O}");
        sb.AppendLine();
        foreach (var c in checks)
        {
            sb.AppendLine($"[{(c.Passed ? "PASS" : "WARN")}] [{c.Category}] {c.Name}: {c.Detail}");
        }
        sb.AppendLine();
        sb.AppendLine("=== RECENT SCRUBBED LOGS ===");
        foreach (string line in recentLogs.TakeLast(40))
        {
            sb.AppendLine(SecureTokenVault.ScrubSecrets(line));
        }
        return sb.ToString();
    }

    private async Task<DiagnosticCheckItem> ProbeEndpointAsync(string label, string url, CancellationToken ct)
    {
        try
        {
            var sw = Stopwatch.StartNew();
            using var req = new HttpRequestMessage(HttpMethod.Head, url);
            using var resp = await _http.SendAsync(req, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            sw.Stop();
            bool ok = (int)resp.StatusCode < 500;
            return new DiagnosticCheckItem("Network", label, ok, $"HTTP {(int)resp.StatusCode} ({sw.ElapsedMilliseconds} ms)");
        }
        catch (Exception ex)
        {
            return new DiagnosticCheckItem("Network", label, false, $"Unreachable ({ex.Message})");
        }
    }
}
