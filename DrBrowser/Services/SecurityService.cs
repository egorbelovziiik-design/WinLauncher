using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Text.Json;

namespace DrBrowser;

public enum ThreatLevel
{
    Safe,
    Warn,
    Block
}

public sealed record SecurityVerdict(ThreatLevel Level, string Reason, string Rule = "")
{
    public bool IsBlocked => Level == ThreatLevel.Block;
    public bool IsWarning => Level == ThreatLevel.Warn;
    public static readonly SecurityVerdict Safe = new(ThreatLevel.Safe, string.Empty);
}

public sealed record ThreatEntry(DateTime Time, string Target, string Reason, string Engine, string Action)
{
    public string TimeText => Time.ToString("dd.MM HH:mm:ss");
    public string Level => Action.Contains("заблок", StringComparison.OrdinalIgnoreCase) ? "BLOCK" : "SCAN";
}

public sealed record ScanResult(string Path, string Status, string Detail, string Engine)
{
    public bool IsThreat => Status.Equals("threat", StringComparison.OrdinalIgnoreCase);
    public bool IsClean => Status.Equals("clean", StringComparison.OrdinalIgnoreCase);
    public string FileName => System.IO.Path.GetFileName(Path);
}

/// <summary>
/// DrGuard — the browser security layer.
///
/// What it really does:
///  * blocks navigations to URLs that hit the user's blocklist or the built-in
///    phishing/scam heuristics (before the page is loaded);
///  * warns about plain-HTTP, punycode, IP-literal and credential-in-URL links;
///  * scans every downloaded file with Microsoft Defender (MpCmdRun.exe) and shows
///    the real verdict, not a fake "clean" badge;
///  * reports the actual Defender status of the machine and the age of its signatures.
///
/// What it does not do (and never claims): it is not a replacement for an antivirus
/// engine, it has no cloud sandbox and it does not inspect process memory.
/// </summary>
public sealed class SecurityService : ObservableObject
{
    private static readonly string[] SuspiciousTlds =
    {
        ".tk", ".ml", ".ga", ".cf", ".gq", ".top", ".click", ".zip", ".mov", ".work", ".loan", ".review"
    };

    private static readonly string[] BaitWords =
    {
        "login", "signin", "verify", "secure", "account", "update", "wallet", "seed", "bonus",
        "prize", "gift", "free-", "crypto", "airdrop", "support", "unlock", "recover"
    };

    private static readonly string[] DownloadExtensions =
    {
        ".exe", ".msi", ".bat", ".cmd", ".ps1", ".vbs", ".js", ".jse", ".scr", ".com", ".hta", ".jar"
    };

    private static readonly string[] DoubleExtensionTargets =
    {
        ".pdf", ".doc", ".docx", ".jpg", ".jpeg", ".png", ".txt", ".mp4", ".xls", ".zip"
    };

    private readonly Logger _log;
    private readonly AppSettings _settings;
    private readonly object _sync = new();

    private string _defenderStatus = "Проверка…";
    private string _signatureText = string.Empty;
    private DateTime _lastStatusCheck = DateTime.MinValue;
    private bool _statusBusy;
    private string _scanStatus = string.Empty;
    private bool _scanning;

    public SecurityService(Logger log, AppSettings settings)
    {
        _log = log;
        _settings = settings;
        foreach (var entry in LoadLog().TakeLast(80)) Threats.Insert(0, entry);
    }

    public ObservableCollection<ThreatEntry> Threats { get; } = new();

    public bool Enabled => _settings.DrGuardEnabled;
    public bool ScanDownloads => _settings.DrGuardScanDownloads;
    public bool WarnOnHttp => _settings.DrGuardWarnOnHttp;

    public int BlockedCount { get; private set; }
    public int ScannedCount { get; private set; }
    public int ThreatCount { get; private set; }

    public string BlockedText => $"{BlockedCount} заблокировано";
    public string ScanText => $"{ScannedCount} файлов проверено";
    public string ThreatText => ThreatCount == 0 ? "угроз не найдено" : $"{ThreatCount} угроз";

    public string DefenderStatus
    {
        get => _defenderStatus;
        private set => Set(ref _defenderStatus, value);
    }

    public string SignatureText
    {
        get => _signatureText;
        private set => Set(ref _signatureText, value);
    }

    public string ScanStatus
    {
        get => _scanStatus;
        private set => Set(ref _scanStatus, value);
    }

    public bool IsScanning
    {
        get => _scanning;
        private set
        {
            if (Set(ref _scanning, value)) Raise(nameof(ScanButtonText));
        }
    }

    public string ScanButtonText => IsScanning ? "Проверяю…" : "Проверить папку загрузок";

    public int CustomHostCount => _settings.DrGuardBlockedHosts.Count;

    public string CustomHostsText => CustomHostCount == 0
        ? "Свой список пуст — используются встроенные правила"
        : $"Свой список: {CustomHostCount} домен(ов)";

    // ------------------------------------------------------------------
    // URL reputation
    // ------------------------------------------------------------------

    /// <summary>
    /// Evaluates a navigation target. Runs before the request is made, so a blocked URL
    /// never reaches the network.
    /// </summary>
    public SecurityVerdict CheckUrl(string url)
    {
        if (!Enabled) return SecurityVerdict.Safe;
        if (string.IsNullOrWhiteSpace(url)) return SecurityVerdict.Safe;
        if (UrlParser.IsInternal(url)) return SecurityVerdict.Safe;
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) return SecurityVerdict.Safe;
        if (uri.Scheme is not ("http" or "https")) return SecurityVerdict.Safe;

        var host = uri.Host.ToLowerInvariant();

        if (IsExempt(host)) return SecurityVerdict.Safe;

        foreach (var blocked in _settings.DrGuardBlockedHosts)
        {
            if (MatchesHost(host, blocked))
                return new SecurityVerdict(ThreatLevel.Block, $"Домен «{blocked}» в вашем списке блокировки", "blocklist");
        }

        if (!string.IsNullOrEmpty(uri.UserInfo))
            return new SecurityVerdict(ThreatLevel.Block, "Ссылка содержит логин и пароль внутри адреса — типичная фишинговая схема", "credentials");

        if (host.StartsWith("xn--", StringComparison.Ordinal) || host.Contains(".xn--", StringComparison.Ordinal))
            return new SecurityVerdict(ThreatLevel.Block, "Домен записан в punycode — так маскируют поддельные адреса (например «аpple.com»)", "punycode");

        if (SuspiciousTlds.Any(tld => host.EndsWith(tld, StringComparison.Ordinal))
            && BaitWords.Any(word => host.Contains(word, StringComparison.Ordinal)))
            return new SecurityVerdict(ThreatLevel.Block,
                $"Домен на «{SuspiciousTlds.First(tld => host.EndsWith(tld, StringComparison.Ordinal))}» с приманкой в имени — распространённая схема мошенников",
                "tld-bait");

        if (uri.Scheme == "http" && WarnOnHttp && !IsPrivateHost(host))
            return new SecurityVerdict(ThreatLevel.Warn, "Соединение без шифрования (HTTP): трафик может быть прочитан в сети", "http");

        if (IPAddressLike(host) && !IsPrivateHost(host))
            return new SecurityVerdict(ThreatLevel.Warn, "Сайт открывается по IP-адресу, а не по домену — проверьте адрес", "ip-literal");

        return SecurityVerdict.Safe;
    }

    public bool IsExempt(string host)
    {
        foreach (var allowed in _settings.DrGuardExemptions)
        {
            if (MatchesHost(host.ToLowerInvariant(), allowed)) return true;
        }

        return false;
    }

    public void RegisterBlocked(string url, string reason)
    {
        lock (_sync)
        {
            BlockedCount++;
            AddThreat(new ThreatEntry(DateTime.Now, url, reason, "DrGuard", "заблокировано"));
        }

        Raise(nameof(BlockedCount), nameof(BlockedText));
        _log.Warn($"DrGuard blocked {url} — {reason}");
    }

    public void AddBlockedHost(string host)
    {
        var value = CleanHost(host);
        if (value.Length == 0) return;
        if (_settings.DrGuardBlockedHosts.Any(h => h.Equals(value, StringComparison.OrdinalIgnoreCase))) return;

        _settings.DrGuardBlockedHosts.Add(value);
        Raise(nameof(CustomHostCount), nameof(CustomHostsText));
    }

    public bool RemoveBlockedHost(string host)
    {
        var removed = _settings.DrGuardBlockedHosts.RemoveAll(h => h.Equals(host, StringComparison.OrdinalIgnoreCase)) > 0;
        Raise(nameof(CustomHostCount), nameof(CustomHostsText));
        return removed;
    }

    public bool AddExemption(string host)
    {
        var value = CleanHost(host);
        if (value.Length == 0) return false;
        if (_settings.DrGuardExemptions.Any(h => h.Equals(value, StringComparison.OrdinalIgnoreCase))) return true;
        _settings.DrGuardExemptions.Add(value);
        return true;
    }

    public static bool MatchesHost(string host, string rule)
    {
        var (h, r) = (host.ToLowerInvariant().Trim('.'), CleanHost(rule));
        if (r.Length == 0) return false;
        return h == r || h.EndsWith("." + r, StringComparison.Ordinal);
    }

    private static string CleanHost(string raw)
    {
        var text = (raw ?? string.Empty).Trim().ToLowerInvariant();
        if (text.Contains("://", StringComparison.Ordinal) && Uri.TryCreate(text, UriKind.Absolute, out var uri))
            text = uri.Host.ToLowerInvariant();
        text = text.Trim('/');
        if (text.StartsWith("www.", StringComparison.Ordinal)) text = text[4..];
        return text.Length > 200 ? string.Empty : text;
    }

    // ------------------------------------------------------------------
    // Download scanning (Microsoft Defender)
    // ------------------------------------------------------------------

    /// <summary>Finds MpCmdRun.exe of the installed Defender platform.</summary>
    public static string? FindDefenderCli()
    {
        var candidates = new List<string>
        {
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Windows Defender", "MpCmdRun.exe"),
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Windows Defender", "MpCmdRun.exe")
        };

        var platformRoot = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData),
            "Microsoft", "Windows Defender", "Platform");

        if (Directory.Exists(platformRoot))
        {
            candidates.AddRange(Directory.GetDirectories(platformRoot)
                .OrderByDescending(d => d, StringComparer.OrdinalIgnoreCase)
                .Select(dir => Path.Combine(dir, "MpCmdRun.exe")));
        }

        return candidates.FirstOrDefault(File.Exists);
    }

    public static string BuildScanArguments(string path)
        => $"-Scan -ScanType 3 -File \"{path}\" -DisableRemediation -Timeout 120";

    /// <summary>
    /// Scans one file. Uses Microsoft Defender when available, otherwise a clearly
    /// labelled local heuristic; it never reports "clean" without saying who decided.
    /// </summary>
    public async Task<ScanResult> ScanFileAsync(string path, CancellationToken token = default)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
            return new ScanResult(path ?? string.Empty, "error", "Файл не найден", "DrGuard");

        ScannedCount++;
        Raise(nameof(ScannedCount), nameof(ScanText));

        var cli = FindDefenderCli();
        if (cli is null)
        {
            return await Task.FromResult(HeuristicScan(path)).ConfigureAwait(false);
        }

        try
        {
            var psi = new ProcessStartInfo(cli, BuildScanArguments(path))
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };

            using var process = Process.Start(psi);
            if (process is null) return HeuristicScan(path);

            var output = new StringBuilder();
            output.Append(await process.StandardOutput.ReadToEndAsync(token).ConfigureAwait(false));
            output.Append(await process.StandardError.ReadToEndAsync(token).ConfigureAwait(false));
            await process.WaitForExitAsync(token).ConfigureAwait(false);

            // MpCmdRun: 0 = no threat found, 2 = threat found / action taken, other = error.
            var verdict = process.ExitCode switch
            {
                0 => new ScanResult(path, "clean", "Microsoft Defender: угроз не найдено", "Microsoft Defender"),
                2 => new ScanResult(path, "threat", "Microsoft Defender обнаружил угрозу", "Microsoft Defender"),
                _ => new ScanResult(path, "error", $"Microsoft Defender вернул код {process.ExitCode}", "Microsoft Defender")
            };

            _log.Info($"DrGuard scan {Path.GetFileName(path)} → {verdict.Status} (exit {process.ExitCode})");

            if (verdict.IsThreat)
            {
                ThreatCount++;
                AddThreat(new ThreatEntry(DateTime.Now, path, "Обнаружено Microsoft Defender", "Microsoft Defender", "найдена угроза"));
                Raise(nameof(ThreatCount), nameof(ThreatText));
            }

            return verdict;
        }
        catch (Exception ex)
        {
            _log.Warn("Defender scan failed: " + ex.Message);
            return HeuristicScan(path);
        }
    }

    /// <summary>Local fallback: EICAR signature + dangerous double extensions. Labelled as heuristic.</summary>
    public static ScanResult HeuristicScan(string path)
    {
        try
        {
            const string eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

            var info = new FileInfo(path);
            if (info.Length <= 4096)
            {
                var text = File.ReadAllText(path);
                if (text.Contains(eicar, StringComparison.Ordinal))
                    return new ScanResult(path, "threat", "Тестовая сигнатура EICAR — файл распознан эвристикой DrGuard", "DrGuard (эвристика)");
            }

            var name = info.Name.ToLowerInvariant();
            var secondary = Path.GetExtension(Path.GetFileNameWithoutExtension(name));
            var isDangerous = DownloadExtensions.Contains(Path.GetExtension(name));
            if (isDangerous && DoubleExtensionTargets.Contains(secondary))
                return new ScanResult(path, "threat", "Двойное расширение (например .pdf.exe) — признак маскировки", "DrGuard (эвристика)");

            return new ScanResult(path, "clean",
                "Эвристика DrGuard: сигнатур не найдено. Полноценную проверку выполняет только антивирус Windows.",
                "DrGuard (эвристика)");
        }
        catch (Exception ex)
        {
            return new ScanResult(path, "error", ex.Message, "DrGuard (эвристика)");
        }
    }

    /// <summary>Scans the newest files of a folder (default: the download folder).</summary>
    public async Task<IReadOnlyList<ScanResult>> ScanFolderAsync(string folder, int maxFiles = 25, CancellationToken token = default)
    {
        var results = new List<ScanResult>();
        if (!Directory.Exists(folder))
        {
            ScanStatus = "Папка не найдена: " + folder;
            return results;
        }

        IsScanning = true;
        ScanStatus = "Проверяю файлы…";

        try
        {
            var files = Directory.GetFiles(folder)
                .Select(f => new FileInfo(f))
                .Where(f => f.Exists)
                .OrderByDescending(f => f.LastWriteTimeUtc)
                .Take(maxFiles)
                .ToList();

            foreach (var file in files)
            {
                if (token.IsCancellationRequested) break;
                ScanStatus = $"Проверяю {file.Name}…";
                results.Add(await ScanFileAsync(file.FullName, token).ConfigureAwait(true));
            }

            var threats = results.Count(r => r.IsThreat);
            var errors = results.Count(r => !r.IsThreat && !r.IsClean);
            ScanStatus = results.Count == 0
                ? "В папке нет файлов для проверки."
                : $"Проверено {results.Count}: {results.Count - threats - errors} чистых, {threats} угроз, {errors} ошибок.";
        }
        catch (Exception ex)
        {
            _log.Error("folder scan failed", ex);
            ScanStatus = "Ошибка проверки: " + ex.Message;
        }
        finally
        {
            IsScanning = false;
        }

        return results;
    }

    // ------------------------------------------------------------------
    // Windows antivirus status
    // ------------------------------------------------------------------

    /// <summary>Reads the real state of Microsoft Defender (cached for 10 minutes).</summary>
    public async Task RefreshDefenderStatusAsync(bool force = false)
    {
        if (_statusBusy) return;
        if (!force && DateTime.UtcNow - _lastStatusCheck < TimeSpan.FromMinutes(10)) return;

        _statusBusy = true;
        try
        {
            var cli = FindDefenderCli();
            if (cli is null)
            {
                DefenderStatus = "Microsoft Defender не найден — используется эвристика DrGuard";
                SignatureText = "Сторонний антивирус не определяется этим способом.";
                _lastStatusCheck = DateTime.UtcNow;
                return;
            }

            var script = "try { $s = Get-MpComputerStatus; " +
                         "[pscustomobject]@{ av=[bool]$s.AntivirusEnabled; rt=[bool]$s.RealTimeProtectionEnabled; " +
                         "sig=[string]$s.AntivirusSignatureVersion; ver=[string]$s.AMEngineVersion } | ConvertTo-Json -Compress " +
                         "} catch { '{}' }";

            var psi = new ProcessStartInfo("powershell.exe",
                "-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"" + script.Replace("\"", "\\\"") + "\"")
            {
                UseShellExecute = false,
                CreateNoWindow = true,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };

            using var process = Process.Start(psi);
            if (process is null)
            {
                DefenderStatus = "Статус антивируса недоступен";
                return;
            }

            var json = await process.StandardOutput.ReadToEndAsync().ConfigureAwait(false);
            await process.WaitForExitAsync().ConfigureAwait(false);

            using var doc = JsonDocument.Parse(string.IsNullOrWhiteSpace(json) ? "{}" : json);
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object || !root.TryGetProperty("av", out var av))
            {
                DefenderStatus = "Статус антивируса недоступен (нужны права на чтение Defender)";
                _lastStatusCheck = DateTime.UtcNow;
                return;
            }

            var antivirus = av.GetBoolean();
            var realtime = root.TryGetProperty("rt", out var rt) && rt.GetBoolean();
            var signature = root.TryGetProperty("sig", out var sig) ? sig.GetString() : null;
            var engine = root.TryGetProperty("ver", out var ver) ? ver.GetString() : null;

            DefenderStatus = antivirus
                ? $"Microsoft Defender включён{(realtime ? ", защита в реальном времени" : ", защита в реальном времени выключена")}"
                : "Microsoft Defender выключен — DrGuard работает только в режиме эвристик";

            SignatureText = string.IsNullOrWhiteSpace(signature)
                ? string.Empty
                : $"Базы: {signature}{(string.IsNullOrWhiteSpace(engine) ? string.Empty : " · движок " + engine)}";

            _lastStatusCheck = DateTime.UtcNow;
        }
        catch (Exception ex)
        {
            _log.Warn("Defender status query failed: " + ex.Message);
            DefenderStatus = "Статус антивируса недоступен";
        }
        finally
        {
            _statusBusy = false;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public void AddThreat(ThreatEntry entry)
    {
        Threats.Insert(0, entry);
        while (Threats.Count > 80) Threats.RemoveAt(Threats.Count - 1);
        SaveLog();
    }

    private void SaveLog()
    {
        try
        {
            var path = Path.Combine(AppPaths.DataFolder, "guard-log.json");
            var payload = Threats.Select(t => new
            {
                t.Time, t.Target, t.Reason, t.Engine, t.Action
            });
            File.WriteAllText(path, JsonSerializer.Serialize(payload, new JsonSerializerOptions { WriteIndented = false }));
        }
        catch
        {
            // diagnostics only
        }
    }

    private List<ThreatEntry> LoadLog()
    {
        try
        {
            var path = Path.Combine(AppPaths.DataFolder, "guard-log.json");
            if (!File.Exists(path)) return new List<ThreatEntry>();

            var raw = JsonSerializer.Deserialize<List<ThreatEntry>>(File.ReadAllText(path));
            return raw ?? new List<ThreatEntry>();
        }
        catch
        {
            return new List<ThreatEntry>();
        }
    }

    private static bool IPAddressLike(string host)
        => host.Count(c => c == '.') == 3
           && host.Split('.').Length == 4
           && host.Split('.').All(p => int.TryParse(p, out var n) && n is >= 0 and <= 255);

    public static bool IsPrivateHost(string host)
    {
        if (host is "localhost" or "127.0.0.1" or "::1" or "0.0.0.0") return true;
        if (!IPAddressLike(host)) return false;

        var parts = host.Split('.').Select(int.Parse).ToArray();
        return parts[0] == 10
               || (parts[0] == 192 && parts[1] == 168)
               || (parts[0] == 172 && parts[1] is >= 16 and <= 31)
               || parts[0] == 127
               || (parts[0] == 169 && parts[1] == 254);
    }
}
