using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace AxsesClient.Updater;

public sealed record UpdateArtifact(
    [property: JsonPropertyName("name")] string Name,
    [property: JsonPropertyName("targetRelativePath")] string TargetRelativePath,
    [property: JsonPropertyName("url")] string Url,
    [property: JsonPropertyName("sha256")] string Sha256,
    [property: JsonPropertyName("size")] long Size
);

public sealed record UpdateManifest(
    [property: JsonPropertyName("latestVersion")] string LatestVersion,
    [property: JsonPropertyName("codename")] string Codename,
    [property: JsonPropertyName("publishedAt")] string PublishedAt,
    [property: JsonPropertyName("changelog")] List<string> Changelog,
    [property: JsonPropertyName("artifacts")] List<UpdateArtifact> Artifacts
);

public sealed record UpdateCheckResult(
    bool UpdateAvailable,
    string CurrentVersion,
    string LatestVersion,
    IReadOnlyList<string> Changelog,
    UpdateManifest? Manifest,
    string StatusMessage
);

public sealed class AutoUpdaterService
{
    private readonly HttpClient _http;
    private readonly string _installRoot;
    private readonly string _stagingDir;
    private readonly string _rollbackDir;

    public AutoUpdaterService(string installRoot, HttpClient? httpClient = null)
    {
        _installRoot = Path.GetFullPath(installRoot);
        _stagingDir = Path.Combine(_installRoot, "updates", "staging");
        _rollbackDir = Path.Combine(_installRoot, "updates", "rollback");
        _http = httpClient ?? CreateDefaultHttpClient();
    }

    private static HttpClient CreateDefaultHttpClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd("AXSESCLIENT-Updater/1.0.0");
        return client;
    }

    public async Task<UpdateCheckResult> CheckForUpdatesAsync(
        string currentVersion,
        string manifestUrlOrPath,
        CancellationToken ct = default)
    {
        try
        {
            string rawJson;
            if (Uri.TryCreate(manifestUrlOrPath, UriKind.Absolute, out var uri) &&
                (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
            {
                using var resp = await _http.GetAsync(uri, ct).ConfigureAwait(false);
                resp.EnsureSuccessStatusCode();
                rawJson = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            }
            else if (File.Exists(manifestUrlOrPath))
            {
                rawJson = await File.ReadAllTextAsync(manifestUrlOrPath, ct).ConfigureAwait(false);
            }
            else
            {
                return new UpdateCheckResult(false, currentVersion, currentVersion, Array.Empty<string>(), null,
                    "Update manifest endpoint unreachable or not configured; running local release v" + currentVersion);
            }

            var manifest = JsonSerializer.Deserialize<UpdateManifest>(rawJson);
            if (manifest is null || string.IsNullOrWhiteSpace(manifest.LatestVersion))
            {
                return new UpdateCheckResult(false, currentVersion, currentVersion, Array.Empty<string>(), null,
                    "Invalid update manifest format.");
            }

            bool newer = CompareSemVer(manifest.LatestVersion, currentVersion) > 0;
            return new UpdateCheckResult(
                newer,
                currentVersion,
                manifest.LatestVersion,
                manifest.Changelog ?? new List<string>(),
                manifest,
                newer ? $"Update v{manifest.LatestVersion} ({manifest.Codename}) available." : $"Up to date (v{currentVersion})."
            );
        }
        catch (Exception ex)
        {
            return new UpdateCheckResult(false, currentVersion, currentVersion, Array.Empty<string>(), null,
                $"Update check failed safely: {ex.Message}");
        }
    }

    public async Task<string> DownloadAndVerifyArtifactAsync(
        UpdateArtifact artifact,
        IProgress<double>? progress = null,
        CancellationToken ct = default)
    {
        Directory.CreateDirectory(_stagingDir);
        string safeName = Path.GetFileName(artifact.Name);
        if (string.IsNullOrWhiteSpace(safeName))
            throw new InvalidOperationException("Artifact name cannot be empty.");

        string partPath = Path.Combine(_stagingDir, safeName + ".part");
        string finalStagedPath = Path.Combine(_stagingDir, safeName);

        if (Uri.TryCreate(artifact.Url, UriKind.Absolute, out var uri) &&
            (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
        {
            using var resp = await _http.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            resp.EnsureSuccessStatusCode();
            long total = resp.Content.Headers.ContentLength ?? artifact.Size;
            await using var netStream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            await using (var fs = new FileStream(partPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true))
            {
                byte[] buffer = new byte[81920];
                long readTotal = 0;
                int read;
                while ((read = await netStream.ReadAsync(buffer, ct).ConfigureAwait(false)) > 0)
                {
                    await fs.WriteAsync(buffer.AsMemory(0, read), ct).ConfigureAwait(false);
                    readTotal += read;
                    if (total > 0)
                        progress?.Report(Math.Clamp((double)readTotal / total, 0.0, 1.0));
                }
            }
        }
        else if (File.Exists(artifact.Url))
        {
            File.Copy(artifact.Url, partPath, overwrite: true);
            progress?.Report(1.0);
        }
        else
        {
            throw new FileNotFoundException($"Update source not found: {artifact.Url}");
        }

        string actualHash = await ComputeSha256Async(partPath, ct).ConfigureAwait(false);
        if (!string.Equals(actualHash, artifact.Sha256, StringComparison.OrdinalIgnoreCase))
        {
            File.Delete(partPath);
            throw new CryptographicException(
                $"SHA-256 verification failed for {artifact.Name}. Expected {artifact.Sha256}, got {actualHash}.");
        }

        File.Move(partPath, finalStagedPath, overwrite: true);
        return finalStagedPath;
    }

    public async Task ApplyStagedUpdateAsync(
        UpdateManifest manifest,
        string previousVersion,
        CancellationToken ct = default)
    {
        string backupRoot = Path.Combine(_rollbackDir, SanitizeSegment(previousVersion));
        Directory.CreateDirectory(backupRoot);

        var appliedTargets = new List<(string targetPath, string? backupPath)>();
        try
        {
            foreach (var artifact in manifest.Artifacts)
            {
                ct.ThrowIfCancellationRequested();
                string relative = NormalizeSafeRelativePath(artifact.TargetRelativePath);
                string targetPath = Path.GetFullPath(Path.Combine(_installRoot, relative));
                if (!targetPath.StartsWith(_installRoot, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidOperationException("Path traversal blocked in update manifest.");

                string stagedPath = Path.Combine(_stagingDir, Path.GetFileName(artifact.Name));
                if (!File.Exists(stagedPath))
                    throw new FileNotFoundException($"Staged update file missing: {stagedPath}");

                string actualHash = await ComputeSha256Async(stagedPath, ct).ConfigureAwait(false);
                if (!string.Equals(actualHash, artifact.Sha256, StringComparison.OrdinalIgnoreCase))
                    throw new CryptographicException($"Staged SHA-256 mismatch for {artifact.Name}.");

                string? backupPath = null;
                if (File.Exists(targetPath))
                {
                    backupPath = Path.Combine(backupRoot, relative);
                    Directory.CreateDirectory(Path.GetDirectoryName(backupPath)!);
                    File.Copy(targetPath, backupPath, overwrite: true);
                }

                Directory.CreateDirectory(Path.GetDirectoryName(targetPath)!);
                File.Copy(stagedPath, targetPath, overwrite: true);
                appliedTargets.Add((targetPath, backupPath));
            }
        }
        catch
        {
            foreach (var (targetPath, backupPath) in appliedTargets)
            {
                try
                {
                    if (backupPath is not null && File.Exists(backupPath))
                        File.Copy(backupPath, targetPath, overwrite: true);
                }
                catch
                {
                    // Best-effort transactional rollback
                }
            }
            throw;
        }
    }

    public bool Rollback(string versionToRestore)
    {
        string backupRoot = Path.Combine(_rollbackDir, SanitizeSegment(versionToRestore));
        if (!Directory.Exists(backupRoot))
            return false;

        foreach (string backupFile in Directory.EnumerateFiles(backupRoot, "*", SearchOption.AllDirectories))
        {
            string rel = Path.GetRelativePath(backupRoot, backupFile);
            string target = Path.GetFullPath(Path.Combine(_installRoot, rel));
            if (!target.StartsWith(_installRoot, StringComparison.OrdinalIgnoreCase))
                continue;
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            File.Copy(backupFile, target, overwrite: true);
        }
        return true;
    }

    public static async Task<string> ComputeSha256Async(string filePath, CancellationToken ct = default)
    {
        await using var stream = new FileStream(filePath, FileMode.Open, FileAccess.Read, FileShare.Read, 81920, true);
        byte[] hash = await SHA256.HashDataAsync(stream, ct).ConfigureAwait(false);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    public static int CompareSemVer(string a, string b)
    {
        int[] pa = ParseVersionParts(a);
        int[] pb = ParseVersionParts(b);
        for (int i = 0; i < 3; i++)
        {
            int cmp = pa[i].CompareTo(pb[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int[] ParseVersionParts(string v)
    {
        int[] res = new int[3];
        if (string.IsNullOrWhiteSpace(v)) return res;
        string clean = v.Trim().TrimStart('v', 'V');
        int dash = clean.IndexOf('-');
        if (dash >= 0) clean = clean[..dash];
        string[] parts = clean.Split('.');
        for (int i = 0; i < Math.Min(3, parts.Length); i++)
        {
            if (int.TryParse(parts[i], out int n)) res[i] = n;
        }
        return res;
    }

    private static string NormalizeSafeRelativePath(string rel)
    {
        string normalized = rel.Replace('/', Path.DirectorySeparatorChar).Replace('\\', Path.DirectorySeparatorChar).TrimStart(Path.DirectorySeparatorChar);
        if (normalized.Contains("..", StringComparison.Ordinal))
            throw new InvalidOperationException("Relative path cannot contain '..'");
        return normalized;
    }

    private static string SanitizeSegment(string s)
    {
        var invalid = Path.GetInvalidFileNameChars();
        return new string(s.Select(c => invalid.Contains(c) ? '_' : c).ToArray());
    }
}
