using System.Diagnostics;
using System.IO;
using System.Windows.Input;

namespace DrBrowser;

/// <summary>
/// DrGuard wiring: URL reputation on every navigation, automatic scan of finished
/// downloads and the state behind drbrowser://guard.
/// </summary>
public sealed partial class MainViewModel
{
    private readonly HashSet<string> _scannedFiles = new(StringComparer.OrdinalIgnoreCase);

    private string _guardCheckInput = string.Empty;
    private string _guardVerdict = "Вставьте ссылку — DrGuard проверит её по встроенным правилам и вашему списку.";
    private string _guardLastBlocked = string.Empty;
    private string _guardScanSummary = string.Empty;
    private bool _guardBusy;

    public SecurityService Security { get; private set; } = null!;
    public LocalAiEngine LocalAi { get; private set; } = null!;

    public string GuardCheckInput
    {
        get => _guardCheckInput;
        set => Set(ref _guardCheckInput, value ?? string.Empty);
    }

    public string GuardVerdict
    {
        get => _guardVerdict;
        private set => Set(ref _guardVerdict, value);
    }

    public string GuardLastBlocked
    {
        get => _guardLastBlocked;
        private set
        {
            if (!Set(ref _guardLastBlocked, value)) return;
            Raise(nameof(HasBlockedLink));
        }
    }

    public bool HasBlockedLink => !string.IsNullOrWhiteSpace(GuardLastBlocked);

    public string GuardScanSummary
    {
        get => _guardScanSummary;
        private set => Set(ref _guardScanSummary, value);
    }

    public bool GuardBusy
    {
        get => _guardBusy;
        private set => Set(ref _guardBusy, value);
    }

    public string GuardBlockedHosts => Settings.DrGuardBlockedHosts.Count == 0
        ? "Свой список пуст"
        : string.Join(", ", Settings.DrGuardBlockedHosts);

    public string GuardExemptions => Settings.DrGuardExemptions.Count == 0
        ? "Исключений нет"
        : string.Join(", ", Settings.DrGuardExemptions);

    public string GuardCounters =>
        $"{Security?.BlockedText ?? "0 заблокировано"} · {Security?.ScanText ?? "0 файлов проверено"} · {Security?.ThreatText ?? "угроз не найдено"}";

    private void InitializeGuard()
    {
        Security = new SecurityService(_log, Settings);
        LocalAi = new LocalAiEngine();

        WebViews.UrlGuard = url => Security.CheckUrl(url);
        WebViews.NavigationBlocked += OnNavigationBlocked;
        WebViews.NavigationWarning += OnNavigationWarning;

        Security.PropertyChanged += (_, _) => Raise(nameof(GuardCounters), nameof(GuardDefenderStatus),
            nameof(GuardSignatureText), nameof(GuardScanStatus), nameof(GuardThreats));

        Downloads.Changed += (_, _) => ScanFinishedDownloads();

        _ = Security.RefreshDefenderStatusAsync();
    }

    public string GuardDefenderStatus => Security?.DefenderStatus ?? "Проверка…";
    public string GuardSignatureText => Security?.SignatureText ?? string.Empty;
    public string GuardScanStatus => Security?.ScanStatus ?? string.Empty;
    public object? GuardThreats => Security?.Threats;

    private void OnNavigationBlocked(string url, string reason)
    {
        Security.RegisterBlocked(url, reason);
        GuardLastBlocked = url;
        StatusMessage = $"DrGuard заблокировал переход: {reason}";
        OpenInternal("guard");
        Raise(nameof(GuardCounters), nameof(GuardLastBlocked), nameof(HasBlockedLink));
    }

    private void OnNavigationWarning(string url, string reason)
    {
        StatusMessage = $"DrGuard: {reason}";
        _log.Info($"DrGuard warning for {url}: {reason}");
    }

    /// <summary>Scans downloads as soon as a WebView2 download finishes.</summary>
    private void ScanFinishedDownloads()
    {
        if (!Settings.DrGuardScanDownloads) return;

        foreach (var item in Downloads.Items.ToList())
        {
            if (item.State != DownloadState.Completed) continue;
            if (string.IsNullOrWhiteSpace(item.Path) || _scannedFiles.Contains(item.Path)) continue;

            _scannedFiles.Add(item.Path);
            _ = ScanDownloadAsync(item.Path);
        }
    }

    private async Task ScanDownloadAsync(string path)
    {
        try
        {
            var result = await Security.ScanFileAsync(path).ConfigureAwait(true);
            GuardScanSummary = $"{result.FileName}: {result.Status} — {result.Detail}";
            StatusMessage = result.IsThreat
                ? $"DrGuard: угроза в файле {result.FileName} — {result.Detail}"
                : $"DrGuard проверил {result.FileName}: {result.Detail}";

            if (result.IsThreat)
                _shell?.ShowMessage("DrGuard: обнаружена угроза",
                    $"Файл: {path}\n\n{result.Detail}\n\nРекомендуется удалить его и не открывать.");

            Raise(nameof(GuardCounters), nameof(GuardScanStatus));
        }
        catch (Exception ex)
        {
            _log.Error("download scan failed", ex);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public ICommand OpenGuardCommand => Cmd("guard.open", () => OpenInternal("guard"));

    public ICommand RefreshGuardCommand => Cmd("guard.refresh", async () =>
    {
        GuardBusy = true;
        try
        {
            await Security.RefreshDefenderStatusAsync(force: true).ConfigureAwait(true);
        }
        finally
        {
            GuardBusy = false;
            Raise(nameof(GuardDefenderStatus), nameof(GuardSignatureText), nameof(GuardCounters));
        }
    });

    public ICommand CheckLinkCommand => Cmd("guard.check", () =>
    {
        var input = GuardCheckInput.Trim();
        if (input.Length == 0)
        {
            GuardVerdict = "Введите ссылку для проверки.";
            return;
        }

        if (!input.Contains("://", StringComparison.Ordinal)) input = "https://" + input;

        var verdict = Security.CheckUrl(input);
        GuardVerdict = verdict.Level switch
        {
            ThreatLevel.Block => $"⛔ Блокировка: {verdict.Reason} (правило {verdict.Rule})",
            ThreatLevel.Warn => $"⚠ Предупреждение: {verdict.Reason}",
            _ => "✅ По правилам DrGuard ссылка выглядит безопасной. Это не гарантия: проверка эвристическая."
        };

        if (verdict.IsBlocked) Security.RegisterBlocked(input, verdict.Reason);
        Raise(nameof(GuardCounters));
    });

    public ICommand ScanDownloadsCommand => Cmd("guard.scanfolder", async () =>
    {
        if (GuardBusy) return;
        GuardBusy = true;
        try
        {
            var folder = Downloads.ResolveFolder();
            var results = await Security.ScanFolderAsync(folder).ConfigureAwait(true);
            var threats = results.Count(r => r.IsThreat);
            GuardScanSummary = results.Count == 0
                ? $"В папке загрузок нет файлов: {folder}"
                : $"Проверено {results.Count} файлов в {folder}: угроз {threats}.";
            Raise(nameof(GuardCounters));
        }
        finally
        {
            GuardBusy = false;
        }
    });

    public ICommand ScanFileCommand => Cmd("guard.scanfile", async () =>
    {
        var file = _shell?.PickOpenFile("Все файлы|*.*");
        if (string.IsNullOrWhiteSpace(file)) return;

        GuardBusy = true;
        try
        {
            var result = await Security.ScanFileAsync(file).ConfigureAwait(true);
            GuardScanSummary = $"{result.FileName}: {result.Status} — {result.Detail}";
            if (result.IsThreat)
                _shell?.ShowMessage("DrGuard: обнаружена угроза", $"{file}\n\n{result.Detail}");
            Raise(nameof(GuardCounters));
        }
        finally
        {
            GuardBusy = false;
        }
    });

    public ICommand BlockHostCommand => Cmd("guard.blockhost", () =>
    {
        var host = GuardCheckInput.Trim();
        if (host.Length == 0) return;
        Security.AddBlockedHost(host);
        _settingsService.Save(Settings);
        GuardVerdict = $"Домен добавлен в блок-лист: {host}";
        Raise(nameof(GuardBlockedHosts));
    });

    public ICommand AllowHostCommand => Cmd("guard.allowhost", () =>
    {
        if (!HasBlockedLink) return;
        var host = UrlParser.HostOf(GuardLastBlocked);
        if (string.IsNullOrWhiteSpace(host)) return;

        Security.AddExemption(host);
        _settingsService.Save(Settings);
        GuardVerdict = $"Домен {host} добавлен в исключения DrGuard.";
        Raise(nameof(GuardExemptions));
    });

    public ICommand ClearBlockedHostsCommand => Cmd("guard.clearhosts", () =>
    {
        Settings.DrGuardBlockedHosts.Clear();
        _settingsService.Save(Settings);
        Raise(nameof(GuardBlockedHosts));
        GuardVerdict = "Свой блок-лист очищен.";
    });

    public ICommand ClearGuardLogCommand => Cmd("guard.clearlog", () =>
    {
        Security.Threats.Clear();
        Raise(nameof(GuardThreats));
    });

    public ICommand OpenDownloadFolderCommand => Cmd("guard.openfolder", () =>
    {
        var folder = Downloads.ResolveFolder();
        if (Directory.Exists(folder)) _shell?.OpenExternal(folder);
        else StatusMessage = "Папка загрузок не найдена: " + folder;
    });

    /// <summary>Used by the smoke test: performs a real Defender scan of a temp file.</summary>
    public async Task<string> RunSecurityProbeAsync()
    {
        var path = Path.Combine(Path.GetTempPath(), "drbrowser-guard-probe.txt");
        await File.WriteAllTextAsync(path, "DrBrowser self-check file. Not malicious.").ConfigureAwait(false);

        var cli = SecurityService.FindDefenderCli();
        var result = await Security.ScanFileAsync(path).ConfigureAwait(false);
        var status = $"engine={result.Engine} · status={result.Status} · defenderCli={(cli is null ? "missing" : "present")}";

        try
        {
            File.Delete(path);
        }
        catch
        {
            // temp file only
        }

        return status;
    }

    /// <summary>Smoke helper: exercises the heuristic verdicts without navigation.</summary>
    public string RunGuardUrlProbe()
    {
        var cases = new[]
        {
            ("https://example.com", ThreatLevel.Safe),
            ("http://example.com", ThreatLevel.Warn),
            ("https://xn--80ak6aa92e.com/login", ThreatLevel.Block),
            ("https://free-robux-top.tk/verify", ThreatLevel.Block),
            ("https://user:pass@example.com/", ThreatLevel.Block),
            ("http://localhost:5173/", ThreatLevel.Safe)
        };

        var failures = new List<string>();
        foreach (var (url, expected) in cases)
        {
            var verdict = Security.CheckUrl(url);
            if (verdict.Level != expected)
                failures.Add($"{url} → {verdict.Level} (ожидалось {expected})");
        }

        return failures.Count == 0
            ? $"DrGuard URL rules: {cases.Length}/{cases.Length} верно"
            : "DrGuard URL rules: ошибки — " + string.Join("; ", failures);
    }
}
