using System.Collections.ObjectModel;
using System.IO;
using System.Net.Http;
using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>
/// Ad / tracker blocker. Filters are parsed from AdBlock-Plus-compatible lists
/// (<c>||domain^</c>, <c>@@</c> exceptions, <c>*</c> wildcards, plain substrings) and
/// applied to every WebView2 request through WebResourceRequested. Cosmetic-only rules
/// (<c>##</c>) are ignored because they do not map to network requests.
/// </summary>
public sealed class AdBlockService
{
    private readonly Logger _log;
    private readonly HashSet<string> _blockedDomains = new(StringComparer.OrdinalIgnoreCase);
    private readonly List<string> _blockedFragments = new();
    private readonly List<Regex> _blockedRegexes = new();
    private readonly HashSet<string> _exceptionDomains = new(StringComparer.OrdinalIgnoreCase);
    private readonly List<string> _exceptionFragments = new();
    private readonly object _sync = new();

    private static readonly string[] BaseBlocklist =
    {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "googletagservices.com", "googletagmanager.com", "adnxs.com",
        "adsrvr.org", "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "scorecardresearch.com", "quantserve.com", "zedo.com", "pubmatic.com", "rubiconproject.com",
        "openx.net", "casalemedia.com", "smartadserver.com", "adform.net", "advertising.com",
        "adroll.com", "bidswitch.net", "bluekai.com", "demdex.net", "exelator.com", "krxd.net",
        "mathtag.com", "ml314.com", "moatads.com", "mookie1.com", "netmng.com", "nexac.com",
        "simpli.fi", "sitescout.com", "spotxchange.com", "teads.tv", "tremorhub.com", "tribalfusion.com",
        "turn.com", "yieldmo.com", "yieldlab.net", "adcolony.com", "applovin.com", "unityads.unity3d.com",
        "mopub.com", "inmobi.com", "chartboost.com", "vungle.com", "ironsrc.com", "supersonicads.com",
        "ad-maven.com", "popads.net", "propellerads.com", "pushground.com", "revcontent.com",
        "mgid.com", "adskeeper.com", "adsterra.com", "hilltopads.net", "clickadu.com",
        "onclickads.net", "adcash.com", "media.net", "an.yandex.ru",
        "adfox.ru", "banners.adfox.ru", "rtb.com.ru", "adriver.ru", "ad.adriver.ru",
        "casalemedia.com", "33across.com", "1rx.io", "3lift.com", "4info.com", "adentifi.com",
        "adhigh.net", "adnami.io", "adsafeprotected.com", "adtelligent.com", "adyoulike.com",
        "agkn.com", "bidr.io", "bttrack.com", "contextweb.com", "conversantmedia.com"
    };

    public AdBlockService(Logger log)
    {
        _log = log;
        foreach (var entry in BaseBlocklist)
        {
            // Path-scoped entries must never become a whole-domain block (that would
            // break e.g. all of yandex.ru), so they are matched as URL fragments.
            if (entry.Contains('/')) _blockedFragments.Add(entry);
            else _blockedDomains.Add(entry);
        }

        _blockedFragments.AddRange(new[]
        {
            "/pagead/", "/adserver/", "/ads/", "/advert/", "/banner_ad", "/popunder", "/prebid",
            "/yandex.ru/ads/", "ad-click", "ad_frame", "adframe", "adsbygoogle", "/analytics.js", "/gtag/js",
            "banner-ad", "sponsored-content", "/ad_banner", "/advertise", "advertisement."
        });
    }

    public ObservableCollection<string> ActiveLists { get; } = new();
    public ObservableCollection<string> Whitelist { get; } = new();

    public long BlockedCount { get; private set; }
    public long EvaluatedCount { get; private set; }
    public bool Enabled { get; private set; } = true;
    public string LastStatus { get; private set; } = "Фильтры не загружены (используется встроенный список).";
    public int RuleCount => _blockedDomains.Count + _blockedFragments.Count + _blockedRegexes.Count;

    public event EventHandler? CountersChanged;

    public void Configure(bool enabled, IEnumerable<string>? whitelist)
    {
        Enabled = enabled;
        Whitelist.Clear();
        if (whitelist is not null)
        {
            foreach (var host in whitelist.Where(h => !string.IsNullOrWhiteSpace(h)))
                Whitelist.Add(host.Trim().ToLowerInvariant());
        }
    }

    public bool IsWhitelisted(string host)
        => !string.IsNullOrWhiteSpace(host) &&
           Whitelist.Any(w => host.Equals(w, StringComparison.OrdinalIgnoreCase)
                              || host.EndsWith("." + w, StringComparison.OrdinalIgnoreCase));

    public async Task LoadAsync(string customFilters, CancellationToken cancellationToken = default)
    {
        try
        {
            var rules = new List<string>();
            if (!string.IsNullOrWhiteSpace(customFilters))
                rules.AddRange(customFilters.Split('\n'));

            if (Directory.Exists(AppPaths.FiltersDir))
            {
                foreach (var file in Directory.GetFiles(AppPaths.FiltersDir, "*.txt"))
                {
                    rules.AddRange(await File.ReadAllLinesAsync(file, cancellationToken).ConfigureAwait(false));
                    if (!ActiveLists.Contains(Path.GetFileName(file))) ActiveLists.Add(Path.GetFileName(file));
                }
            }

            Parse(rules);
            LastStatus = $"Правил: {RuleCount} · доменов: {_blockedDomains.Count} · исключений: {_exceptionDomains.Count}";
            _log.Info(LastStatus);
        }
        catch (Exception ex)
        {
            _log.Error("adblock filter load failed", ex);
            LastStatus = "Не удалось загрузить часть фильтров: " + ex.Message;
        }
    }

    /// <summary>Parses AdBlock-Plus style rules into the three match buckets.</summary>
    public void Parse(IEnumerable<string> rules)
    {
        lock (_sync)
        {
            foreach (var raw in rules)
            {
                var line = raw.Trim();
                if (line.Length == 0 || line.StartsWith('!') || line.StartsWith('[')) continue;
                if (line.Contains("##") || line.Contains("#@#") || line.Contains("#?#")) continue;

                var isException = line.StartsWith("@@");
                if (isException) line = line[2..];

                // Drop $options (third-party, script, domain=...) but keep the pattern.
                var optionsAt = line.LastIndexOf('$');
                if (optionsAt > 0) line = line[..optionsAt];
                if (line.Length == 0) continue;

                if (line.Contains("domain=", StringComparison.OrdinalIgnoreCase)) continue;

                if (isException)
                {
                    AddException(line);
                    continue;
                }

                AddRule(line);
            }
        }
    }

    private void AddRule(string line)
    {
        if (line.StartsWith("||") && line.EndsWith("^") && line.Length > 3)
        {
            _blockedDomains.Add(NormalizeDomain(line[2..^1]));
            return;
        }

        if (line.StartsWith("|") || line.Contains('*'))
        {
            var pattern = line.TrimStart('|').TrimEnd('|');
            var regex = "^" + Regex.Escape(pattern).Replace("\\*", ".*") + (line.EndsWith('|') ? "$" : string.Empty);
            try
            {
                _blockedRegexes.Add(new Regex(regex, RegexOptions.IgnoreCase | RegexOptions.Compiled));
            }
            catch
            {
                // invalid pattern -> ignore rather than break blocking
            }

            return;
        }

        if (line.Contains('/'))
        {
            _blockedFragments.Add(line.TrimStart('|'));
            return;
        }

        // plain host or host/path fragment
        _blockedDomains.Add(NormalizeDomain(line));
    }

    private void AddException(string line)
    {
        if (line.StartsWith("||") && line.EndsWith("^") && line.Length > 3)
        {
            _exceptionDomains.Add(NormalizeDomain(line[2..^1]));
            return;
        }

        if (line.Contains('*'))
        {
            var pattern = line.TrimStart('|').TrimEnd('|');
            try
            {
                _blockedRegexes.Add(new Regex("^" + Regex.Escape(pattern).Replace("\\*", ".*") + "$",
                    RegexOptions.IgnoreCase | RegexOptions.Compiled));
            }
            catch
            {
                // ignore
            }

            return;
        }

        _exceptionFragments.Add(line.TrimStart('|'));
    }

    private static string NormalizeDomain(string value)
    {
        var domain = value.Trim().TrimStart('|').TrimEnd('^', '|');
        var slash = domain.IndexOf('/');
        if (slash > 0) domain = domain[..slash];
        return domain.ToLowerInvariant();
    }

    /// <summary>
    /// Decides whether a request should be blocked. Exceptions win, then the page's own
    /// host is never blocked to avoid breaking first-party resources.
    /// </summary>
    public bool ShouldBlock(string requestUrl, string pageHost, out string reason)
    {
        reason = string.Empty;
        if (!Enabled || string.IsNullOrWhiteSpace(requestUrl)) return false;

        EvaluatedCount++;

        if (!Uri.TryCreate(requestUrl, UriKind.Absolute, out var uri)) return false;
        if (uri.Scheme is not ("http" or "https")) return false;

        var host = uri.Host;
        if (IsWhitelisted(pageHost) || IsWhitelisted(host)) return false;
        if (!string.IsNullOrWhiteSpace(pageHost) && host.Equals(pageHost, StringComparison.OrdinalIgnoreCase)) return false;

        foreach (var exception in _exceptionDomains)
        {
            if (MatchesDomain(host, exception)) return false;
        }

        foreach (var fragment in _exceptionFragments)
        {
            if (requestUrl.Contains(fragment, StringComparison.OrdinalIgnoreCase)) return false;
        }

        foreach (var domain in _blockedDomains)
        {
            if (MatchesDomain(host, domain) || requestUrl.Contains(domain, StringComparison.OrdinalIgnoreCase) && domain.Contains('/'))
            {
                reason = domain;
                return true;
            }
        }

        foreach (var fragment in _blockedFragments)
        {
            if (requestUrl.Contains(fragment, StringComparison.OrdinalIgnoreCase))
            {
                reason = fragment;
                return true;
            }
        }

        foreach (var regex in _blockedRegexes)
        {
            try
            {
                if (regex.IsMatch(requestUrl))
                {
                    reason = regex.ToString();
                    return true;
                }
            }
            catch
            {
                // ignore
            }
        }

        return false;
    }

    private static bool MatchesDomain(string host, string domain)
        => host.Equals(domain, StringComparison.OrdinalIgnoreCase)
           || host.EndsWith("." + domain, StringComparison.OrdinalIgnoreCase);

    public void RegisterBlocked(string reason)
    {
        BlockedCount++;
        if (BlockedCount % 10 == 0) CountersChanged?.Invoke(this, EventArgs.Empty);
        _ = reason;
    }

    public void ResetCounters()
    {
        BlockedCount = 0;
        EvaluatedCount = 0;
        CountersChanged?.Invoke(this, EventArgs.Empty);
    }

    /// <summary>Downloads a compatible filter list (EasyList by default) into the filters folder.</summary>
    public async Task<string> UpdateFromUrlAsync(string url, CancellationToken cancellationToken = default)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme is not ("http" or "https"))
            return "Некорректный адрес списка фильтров.";

        try
        {
            using var client = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
            client.DefaultRequestHeaders.UserAgent.ParseAdd("DrBrowser/1.0");
            var text = await client.GetStringAsync(uri, cancellationToken).ConfigureAwait(false);
            if (text.Length < 500) return "Список подозрительно короткий — обновление отменено.";

            AppPaths.EnsureCreated();
            var target = Path.Combine(AppPaths.FiltersDir, "easylist.txt");
            await File.WriteAllTextAsync(target, text, cancellationToken).ConfigureAwait(false);

            await LoadAsync(string.Empty, cancellationToken).ConfigureAwait(false);
            return $"Список обновлён: {RuleCount} правил.";
        }
        catch (Exception ex)
        {
            _log.Error("filter list download failed", ex);
            return "Не удалось скачать список: " + ex.Message;
        }
    }
}
