using System.Net;
using System.Net.Http.Headers;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;

namespace DrBrowser;

public sealed record CrawlOptions(
    int MaxPages = 25,
    int MaxDepth = 2,
    int RequestDelayMs = 1200,
    IReadOnlyCollection<string>? AllowedDomains = null,
    IReadOnlyCollection<string>? BlockedDomains = null)
{
    public CrawlOptions Normalize() => this with
    {
        MaxPages = Math.Clamp(MaxPages, 1, 500),
        MaxDepth = Math.Clamp(MaxDepth, 0, 5),
        RequestDelayMs = Math.Clamp(RequestDelayMs, 500, 60000),
        AllowedDomains = AllowedDomains ?? Array.Empty<string>(),
        BlockedDomains = BlockedDomains ?? Array.Empty<string>()
    };
}

public sealed record CrawlProgress(int Visited, int Indexed, int Queued, string Url, string Status);
public sealed record CrawlReport(int Visited, int Indexed, int Skipped, int Failed, TimeSpan Elapsed, string SeedUrl);

/// <summary>
/// Bounded, polite crawler for DrSearch. It has no cookies or credentials, obeys
/// robots.txt/noindex, stays on explicitly allowed public domains and rate-limits each
/// host. CAPTCHA/authentication are never bypassed.
/// </summary>
public sealed partial class SearchCrawlerService : IDisposable
{
    public const string UserAgent = "DrBrowserOmegaBot/3.0 (+local user-directed indexer; respects robots.txt)";
    private const int MaxHtmlBytes = 2 * 1024 * 1024;

    private readonly SearchIndexService _index;
    private readonly Logger _log;
    private readonly HttpClient _client;
    private readonly Dictionary<string, RobotsPolicy> _robots = new(StringComparer.OrdinalIgnoreCase);
    private readonly Dictionary<string, DateTimeOffset> _lastRequest = new(StringComparer.OrdinalIgnoreCase);

    public SearchCrawlerService(SearchIndexService index, Logger log, HttpMessageHandler? handler = null)
    {
        _index = index;
        _log = log;
        handler ??= new HttpClientHandler
        {
            AllowAutoRedirect = false,
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate | DecompressionMethods.Brotli,
            UseCookies = false,
            UseDefaultCredentials = false
        };
        _client = new HttpClient(handler, disposeHandler: true) { Timeout = TimeSpan.FromSeconds(20) };
        _client.DefaultRequestHeaders.UserAgent.ParseAdd(UserAgent);
        _client.DefaultRequestHeaders.Accept.ParseAdd("text/html,application/xhtml+xml;q=0.9,text/plain;q=0.6");
        _client.DefaultRequestHeaders.AcceptLanguage.ParseAdd("ru-RU,ru;q=0.9,en;q=0.8");
    }

    public async Task<CrawlReport> CrawlAsync(
        string seedUrl,
        CrawlOptions options,
        IProgress<CrawlProgress>? progress = null,
        CancellationToken token = default)
    {
        if (!_index.IsAvailable) throw new InvalidOperationException("Локальный FTS5-индекс недоступен.");
        options = options.Normalize();
        var normalizedSeed = SearchIndexService.NormalizeUrl(seedUrl);
        if (!Uri.TryCreate(normalizedSeed, UriKind.Absolute, out var seed) || seed.Scheme is not ("http" or "https"))
            throw new ArgumentException("Укажите полный публичный URL с http:// или https://.", nameof(seedUrl));
        await EnsurePublicAddressAsync(seed, token).ConfigureAwait(false);

        var allowed = new HashSet<string>(options.AllowedDomains!
            .Select(NormalizeHost).Where(host => host.Length > 0), StringComparer.OrdinalIgnoreCase);
        // An explicit seed is consent to crawl that host. Links still cannot leave it unless
        // the user has also put another host in AllowedDomains.
        allowed.Add(NormalizeHost(seed.IdnHost));
        var blocked = new HashSet<string>(options.BlockedDomains!
            .Select(NormalizeHost).Where(host => host.Length > 0), StringComparer.OrdinalIgnoreCase);

        var queue = new Queue<(Uri Uri, int Depth)>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        queue.Enqueue((seed, 0));
        seen.Add(normalizedSeed);

        var visited = 0;
        var indexed = 0;
        var skipped = 0;
        var failed = 0;
        var started = DateTimeOffset.UtcNow;

        while (queue.Count > 0 && visited < options.MaxPages)
        {
            token.ThrowIfCancellationRequested();
            var (uri, depth) = queue.Dequeue();
            var displayUrl = SearchIndexService.NormalizeUrl(uri.ToString());

            if (!IsAllowedHost(uri.IdnHost, allowed, blocked) || IsUnsupportedPath(uri.AbsolutePath))
            {
                skipped++;
                progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "пропущено правилом домена/типа"));
                continue;
            }

            try
            {
                await EnsurePublicAddressAsync(uri, token).ConfigureAwait(false);
                var robots = await GetRobotsAsync(uri, options.RequestDelayMs, token).ConfigureAwait(false);
                if (!robots.IsAllowed(uri.PathAndQuery))
                {
                    skipped++;
                    progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "запрещено robots.txt"));
                    continue;
                }

                await RespectDelayAsync(uri.IdnHost, Math.Max(options.RequestDelayMs, robots.CrawlDelayMs), token).ConfigureAwait(false);
                progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "загрузка"));
                var fetched = await FetchHtmlAsync(uri, allowed, blocked, token).ConfigureAwait(false);
                visited++;

                if (fetched is null)
                {
                    skipped++;
                    progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "не HTML или ответ пуст"));
                    continue;
                }

                var page = ParseHtml(fetched.Value.FinalUri, fetched.Value.Html);
                if (page.NoIndex)
                {
                    skipped++;
                    progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "noindex — не добавлено"));
                }
                else if (page.Body.Length >= 80)
                {
                    await _index.UpsertAsync(new SearchDocumentInput(
                        fetched.Value.FinalUri.ToString(),
                        page.Title,
                        page.Body,
                        page.Keywords,
                        page.CanonicalUrl,
                        page.Language,
                        DateTimeOffset.UtcNow), token).ConfigureAwait(false);
                    indexed++;
                    progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "добавлено в FTS5"));
                }
                else
                {
                    skipped++;
                    progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "слишком мало основного текста"));
                }

                if (depth >= options.MaxDepth || page.NoFollow) continue;
                foreach (var link in page.Links)
                {
                    if (seen.Count >= options.MaxPages * 30) break;
                    if (!Uri.TryCreate(fetched.Value.FinalUri, link, out var target)) continue;
                    if (target.Scheme is not ("http" or "https")) continue;
                    if (!IsAllowedHost(target.IdnHost, allowed, blocked) || IsUnsupportedPath(target.AbsolutePath)) continue;
                    var normalized = SearchIndexService.NormalizeUrl(target.ToString());
                    if (!seen.Add(normalized)) continue;
                    if (Uri.TryCreate(normalized, UriKind.Absolute, out var queued)) queue.Enqueue((queued, depth + 1));
                }
            }
            catch (OperationCanceledException) when (token.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception ex)
            {
                failed++;
                _log.Warn($"DrSearch crawler: {displayUrl}: {ex.Message}");
                progress?.Report(new CrawlProgress(visited, indexed, queue.Count, displayUrl, "ошибка: " + ex.Message));
            }
        }

        var elapsed = DateTimeOffset.UtcNow - started;
        _log.Info($"DrSearch crawl: {normalizedSeed} · visited={visited}, indexed={indexed}, skipped={skipped}, failed={failed}");
        return new CrawlReport(visited, indexed, skipped, failed, elapsed, normalizedSeed);
    }

    private async Task<RobotsPolicy> GetRobotsAsync(Uri page, int defaultDelay, CancellationToken token)
    {
        var hostKey = page.Scheme + "://" + page.Authority;
        if (_robots.TryGetValue(hostKey, out var cached)) return cached;

        var robotsUri = new Uri(hostKey + "/robots.txt");
        try
        {
            await RespectDelayAsync(page.IdnHost, defaultDelay, token).ConfigureAwait(false);
            using var request = new HttpRequestMessage(HttpMethod.Get, robotsUri);
            using var response = await _client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);
            RobotsPolicy policy;
            if (response.StatusCode == HttpStatusCode.NotFound)
                policy = RobotsPolicy.AllowAll;
            else if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
                policy = RobotsPolicy.DenyAll;
            else if (!response.IsSuccessStatusCode)
                policy = RobotsPolicy.DenyAll;
            else
                policy = RobotsPolicy.Parse(await ReadLimitedStringAsync(response.Content, 512 * 1024, token).ConfigureAwait(false));
            _robots[hostKey] = policy;
            return policy;
        }
        catch (OperationCanceledException) when (token.IsCancellationRequested)
        {
            throw;
        }
        catch (Exception ex)
        {
            // Conservative failure: no crawling when a site's policy could not be checked.
            _log.Warn($"robots.txt unavailable for {page.IdnHost}: {ex.Message}");
            _robots[hostKey] = RobotsPolicy.DenyAll;
            return RobotsPolicy.DenyAll;
        }
    }

    private async Task<(Uri FinalUri, string Html)?> FetchHtmlAsync(
        Uri initial,
        HashSet<string> allowed,
        HashSet<string> blocked,
        CancellationToken token)
    {
        var current = initial;
        for (var redirect = 0; redirect <= 5; redirect++)
        {
            token.ThrowIfCancellationRequested();
            await EnsurePublicAddressAsync(current, token).ConfigureAwait(false);
            using var request = new HttpRequestMessage(HttpMethod.Get, current);
            request.Headers.Referrer = null;
            using var response = await _client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token).ConfigureAwait(false);

            var statusCode = (int)response.StatusCode;
            if (statusCode is >= 300 and < 400 && response.Headers.Location is not null)
            {
                var next = response.Headers.Location.IsAbsoluteUri
                    ? response.Headers.Location
                    : new Uri(current, response.Headers.Location);
                if (next.Scheme is not ("http" or "https") || !IsAllowedHost(next.IdnHost, allowed, blocked))
                    throw new InvalidOperationException("Редирект за пределы разрешённых доменов заблокирован.");
                current = next;
                continue;
            }

            if (!response.IsSuccessStatusCode) return null;
            var mediaType = response.Content.Headers.ContentType?.MediaType?.ToLowerInvariant() ?? string.Empty;
            if (mediaType.Length > 0 && mediaType is not ("text/html" or "application/xhtml+xml" or "text/plain")) return null;
            if (response.Content.Headers.ContentLength is > MaxHtmlBytes) return null;
            var html = await ReadLimitedStringAsync(response.Content, MaxHtmlBytes, token).ConfigureAwait(false);
            return html.Length == 0 ? null : (current, html);
        }

        throw new InvalidOperationException("Слишком много HTTP-редиректов.");
    }

    private async Task RespectDelayAsync(string host, int delayMs, CancellationToken token)
    {
        if (_lastRequest.TryGetValue(host, out var previous))
        {
            var remaining = TimeSpan.FromMilliseconds(delayMs) - (DateTimeOffset.UtcNow - previous);
            if (remaining > TimeSpan.Zero) await Task.Delay(remaining, token).ConfigureAwait(false);
        }
        _lastRequest[host] = DateTimeOffset.UtcNow;
    }

    private static async Task<string> ReadLimitedStringAsync(HttpContent content, int maxBytes, CancellationToken token)
    {
        await using var stream = await content.ReadAsStreamAsync(token).ConfigureAwait(false);
        using var memory = new MemoryStream();
        var buffer = new byte[16 * 1024];
        var total = 0;
        while (true)
        {
            var count = await stream.ReadAsync(buffer.AsMemory(0, buffer.Length), token).ConfigureAwait(false);
            if (count == 0) break;
            total += count;
            if (total > maxBytes) throw new InvalidOperationException("Страница превышает безопасный лимит размера.");
            await memory.WriteAsync(buffer.AsMemory(0, count), token).ConfigureAwait(false);
        }
        return Encoding.UTF8.GetString(memory.ToArray());
    }

    private static ParsedPage ParseHtml(Uri pageUri, string html)
    {
        var robots = MetaRobotsRegex().Matches(html)
            .Select(match => WebUtility.HtmlDecode(match.Groups[1].Value).ToLowerInvariant())
            .ToArray();
        var noIndex = robots.Any(value => value.Contains("noindex", StringComparison.Ordinal));
        var noFollow = robots.Any(value => value.Contains("nofollow", StringComparison.Ordinal));

        var title = Clean(TitleRegex().Match(html).Groups[1].Value);
        var language = HtmlLanguageRegex().Match(html).Groups[1].Value;
        var keywords = Clean(MetaKeywordsRegex().Match(html).Groups[1].Value);
        var canonicalRaw = CanonicalRegex().Match(html).Groups[1].Value;
        var canonical = Uri.TryCreate(pageUri, WebUtility.HtmlDecode(canonicalRaw), out var canonicalUri)
            && canonicalUri.Scheme is "http" or "https"
            ? SearchIndexService.NormalizeUrl(canonicalUri.ToString())
            : SearchIndexService.NormalizeUrl(pageUri.ToString());

        var content = ScriptStyleRegex().Replace(html, " ");
        content = NoiseBlockRegex().Replace(content, " ");
        content = TagRegex().Replace(content, " ");
        var body = Clean(content);

        var links = LinkRegex().Matches(html)
            .Select(match => WebUtility.HtmlDecode(match.Groups[1].Value).Trim())
            .Where(link => link.Length > 0 && !link.StartsWith('#')
                           && !link.StartsWith("javascript:", StringComparison.OrdinalIgnoreCase)
                           && !link.StartsWith("mailto:", StringComparison.OrdinalIgnoreCase)
                           && !link.StartsWith("tel:", StringComparison.OrdinalIgnoreCase))
            .Take(1000)
            .ToArray();

        return new ParsedPage(title, body, keywords, language, canonical, noIndex, noFollow, links);
    }

    private static string Clean(string value)
        => WhitespaceRegex().Replace(WebUtility.HtmlDecode(value ?? string.Empty), " ").Trim();

    private static bool IsAllowedHost(string host, HashSet<string> allowed, HashSet<string> blocked)
    {
        var normalized = NormalizeHost(host);
        if (blocked.Any(item => normalized.Equals(item, StringComparison.OrdinalIgnoreCase)
                                || normalized.EndsWith('.' + item, StringComparison.OrdinalIgnoreCase))) return false;
        return allowed.Any(item => normalized.Equals(item, StringComparison.OrdinalIgnoreCase)
                                   || normalized.EndsWith('.' + item, StringComparison.OrdinalIgnoreCase));
    }

    private static string NormalizeHost(string host)
        => (host ?? string.Empty).Trim().Trim('.').ToLowerInvariant();

    private static bool IsUnsupportedPath(string path)
        => Regex.IsMatch(path, @"\.(?:zip|7z|rar|exe|msi|dmg|iso|pdf|docx?|xlsx?|pptx?|mp[34]|m4a|wav|flac|ogg|avi|mkv|webm|jpe?g|png|gif|svg|webp|css|js|xml)(?:$|/)", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    private static async Task EnsurePublicAddressAsync(Uri uri, CancellationToken token)
    {
        if (uri.IsLoopback || string.IsNullOrWhiteSpace(uri.Host))
            throw new InvalidOperationException("Локальные адреса не индексируются краулером.");
        var addresses = await Dns.GetHostAddressesAsync(uri.DnsSafeHost, token).ConfigureAwait(false);
        if (addresses.Length == 0 || addresses.Any(IsPrivateAddress))
            throw new InvalidOperationException("Приватный или служебный сетевой адрес заблокирован.");
    }

    private static bool IsPrivateAddress(IPAddress address)
    {
        if (IPAddress.IsLoopback(address)) return true;
        if (address.AddressFamily == AddressFamily.InterNetworkV6)
        {
            if (address.IsIPv6LinkLocal || address.IsIPv6SiteLocal || address.IsIPv6Multicast) return true;
            var bytes = address.GetAddressBytes();
            return (bytes[0] & 0xFE) == 0xFC; // fc00::/7
        }

        var octets = address.GetAddressBytes();
        return octets[0] == 10
               || octets[0] == 127
               || (octets[0] == 169 && octets[1] == 254)
               || (octets[0] == 172 && octets[1] is >= 16 and <= 31)
               || (octets[0] == 192 && octets[1] == 168)
               || (octets[0] == 100 && octets[1] is >= 64 and <= 127)
               || octets[0] >= 224;
    }

    public void Dispose() => _client.Dispose();

    private sealed record ParsedPage(string Title, string Body, string Keywords, string Language,
        string CanonicalUrl, bool NoIndex, bool NoFollow, IReadOnlyList<string> Links);

    private sealed record RobotsRule(bool Allow, string Pattern)
    {
        public int Specificity => Pattern.Replace("*", string.Empty, StringComparison.Ordinal).TrimEnd('$').Length;
        public bool Matches(string path)
        {
            if (Pattern.Length == 0) return false;
            var expression = "^" + Regex.Escape(Pattern).Replace("\\*", ".*", StringComparison.Ordinal);
            if (expression.EndsWith("\\$", StringComparison.Ordinal)) expression = expression[..^2] + "$";
            return Regex.IsMatch(path, expression, RegexOptions.CultureInvariant);
        }
    }

    private sealed record RobotsPolicy(IReadOnlyList<RobotsRule> Rules, int CrawlDelayMs)
    {
        public static RobotsPolicy AllowAll { get; } = new(Array.Empty<RobotsRule>(), 0);
        public static RobotsPolicy DenyAll { get; } = new(new[] { new RobotsRule(false, "/") }, 0);

        public bool IsAllowed(string path)
        {
            var match = Rules.Where(rule => rule.Matches(path)).OrderByDescending(rule => rule.Specificity).FirstOrDefault();
            return match?.Allow ?? true;
        }

        public static RobotsPolicy Parse(string text)
        {
            var groups = new List<(List<string> Agents, List<RobotsRule> Rules, int Delay)>();
            List<string>? agents = null;
            List<RobotsRule>? rules = null;
            var delay = 0;
            var hadDirective = false;

            void Finish()
            {
                if (agents is { Count: > 0 } && rules is not null) groups.Add((agents, rules, delay));
                agents = null; rules = null; delay = 0; hadDirective = false;
            }

            foreach (var raw in (text ?? string.Empty).Split('\n'))
            {
                var line = raw.Split('#', 2)[0].Trim();
                if (line.Length == 0) continue;
                var colon = line.IndexOf(':');
                if (colon < 0) continue;
                var key = line[..colon].Trim().ToLowerInvariant();
                var value = line[(colon + 1)..].Trim();

                if (key == "user-agent")
                {
                    if (hadDirective) Finish();
                    agents ??= new List<string>();
                    rules ??= new List<RobotsRule>();
                    agents.Add(value.ToLowerInvariant());
                    continue;
                }
                if (agents is null || rules is null) continue;
                hadDirective = true;
                if (key == "allow") rules.Add(new RobotsRule(true, value));
                else if (key == "disallow" && value.Length > 0) rules.Add(new RobotsRule(false, value));
                else if (key == "crawl-delay" && double.TryParse(value, System.Globalization.NumberStyles.Float,
                             System.Globalization.CultureInfo.InvariantCulture, out var seconds))
                    delay = Math.Clamp((int)(seconds * 1000), 0, 60000);
            }
            Finish();

            var specific = groups.Where(group => group.Agents.Any(agent => UserAgent.StartsWith(agent, StringComparison.OrdinalIgnoreCase)
                                                                            || "drbrowseromegabot".StartsWith(agent, StringComparison.OrdinalIgnoreCase))).ToList();
            var selected = specific.Count > 0 ? specific : groups.Where(group => group.Agents.Contains("*")).ToList();
            return selected.Count == 0
                ? AllowAll
                : new RobotsPolicy(selected.SelectMany(group => group.Rules).ToArray(), selected.Max(group => group.Delay));
        }
    }

    [GeneratedRegex(@"<title[^>]*>(.*?)</title>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex TitleRegex();
    [GeneratedRegex("""<html[^>]+lang\s*=\s*["']([^"']+)["']""", RegexOptions.IgnoreCase)]
    private static partial Regex HtmlLanguageRegex();
    [GeneratedRegex("""<meta[^>]+(?:name|property)\s*=\s*["'](?:robots|googlebot)["'][^>]+content\s*=\s*["']([^"']*)["'][^>]*>""", RegexOptions.IgnoreCase)]
    private static partial Regex MetaRobotsRegex();
    [GeneratedRegex("""<meta[^>]+name\s*=\s*["']keywords["'][^>]+content\s*=\s*["']([^"']*)["'][^>]*>""", RegexOptions.IgnoreCase)]
    private static partial Regex MetaKeywordsRegex();
    [GeneratedRegex("""<link[^>]+rel\s*=\s*["'][^"']*canonical[^"']*["'][^>]+href\s*=\s*["']([^"']+)["'][^>]*>""", RegexOptions.IgnoreCase)]
    private static partial Regex CanonicalRegex();
    [GeneratedRegex(@"<(script|style|noscript|template|svg)[^>]*>.*?</\1>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex ScriptStyleRegex();
    [GeneratedRegex(@"<(nav|footer|form)[^>]*>.*?</\1>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex NoiseBlockRegex();
    [GeneratedRegex(@"<[^>]+>", RegexOptions.Singleline)]
    private static partial Regex TagRegex();
    [GeneratedRegex("""<a[^>]+href\s*=\s*["']([^"']+)["']""", RegexOptions.IgnoreCase)]
    private static partial Regex LinkRegex();
    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();
}
