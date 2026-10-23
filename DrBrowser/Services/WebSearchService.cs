using System.Net;
using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>One web result returned by DrSearch.</summary>
public sealed class WebSearchHit
{
    public string Title { get; init; } = string.Empty;
    public string Url { get; init; } = string.Empty;
    public string Snippet { get; init; } = string.Empty;
    public string Source { get; init; } = "DrSearch";
    public string Host => UrlParser.DisplayHost(Url);
}

public sealed record WebSearchOutcome(
    IReadOnlyList<WebSearchHit> Hits,
    string UsedQuery,
    string Note,
    bool FromWeb);

/// <summary>
/// DrSearch web layer. It performs the search itself (no search page is embedded in an
/// iframe), parses the returned HTML and hands the ranked links back to the browser.
///
/// Honest scope: DrBrowser has no own crawler and no own index — that infrastructure
/// costs millions of pages of storage. DrSearch therefore queries the public
/// DuckDuckGo HTML endpoint, strips its ads/tracking wrappers and renders the results
/// inside DrBrowser's own search page. If the endpoint is unreachable the caller falls
/// back to local results plus one-click engine chips.
/// </summary>
public sealed partial class WebSearchService
{
    private const string Endpoint = "https://html.duckduckgo.com/html/";

    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        var handler = new HttpClientHandler
        {
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate,
            UseCookies = false
        };

        var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(15) };
        client.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36 DrBrowser/2.1");
        client.DefaultRequestHeaders.TryAddWithoutValidation("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
        return client;
    }

    private readonly Logger _log;

    public WebSearchService(Logger log) => _log = log;

    /// <summary>Search engine bangs: "!g query" forces Google, "!y" Yandex, "!b" Bing, "!d" DuckDuckGo.</summary>
    public static (string Query, string? Engine) ParseBang(string raw)
    {
        var text = (raw ?? string.Empty).Trim();
        if (!text.StartsWith('!')) return (text, null);

        var space = text.IndexOf(' ');
        if (space < 0) return (text, null);

        var bang = text[..space].ToLowerInvariant();
        var rest = text[(space + 1)..].Trim();
        if (rest.Length == 0) return (text, null);

        return bang switch
        {
            "!g" => (rest, "Google"),
            "!y" or "!ya" => (rest, "Яндекс"),
            "!b" => (rest, "Bing"),
            "!d" or "!ddg" => (rest, "DuckDuckGo"),
            _ => (text, null)
        };
    }

    /// <summary>Runs a search. Never throws: network problems come back as a note.</summary>
    public async Task<WebSearchOutcome> SearchAsync(string query, CancellationToken token = default)
    {
        var (clean, engine) = ParseBang(query);
        if (engine is not null)
            return new WebSearchOutcome(Array.Empty<WebSearchHit>(), clean,
                $"Запрос с оператором {engine}: открываю движок напрямую.", false);

        if (string.IsNullOrWhiteSpace(clean))
            return new WebSearchOutcome(Array.Empty<WebSearchHit>(), string.Empty, "Пустой запрос.", false);

        try
        {
            using var content = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["q"] = clean,
                ["kl"] = "ru-ru"
            });

            using var response = await Client.PostAsync(Endpoint, content, token).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
            {
                _log.Warn($"DrSearch: HTTP {(int)response.StatusCode} from the web endpoint");
                return new WebSearchOutcome(Array.Empty<WebSearchHit>(), clean,
                    $"Веб-поиск вернул HTTP {(int)response.StatusCode}. Показаны локальные результаты.", false);
            }

            var html = await response.Content.ReadAsStringAsync(token).ConfigureAwait(false);
            var hits = Parse(html, 20);

            _log.Info($"DrSearch: \"{clean}\" → {hits.Count} web results");
            return hits.Count == 0
                ? new WebSearchOutcome(hits, clean, "Веб-поиск не вернул результатов — попробуйте другой движок ниже.", false)
                : new WebSearchOutcome(hits, clean, $"DrSearch · {hits.Count} результатов из открытого веб-индекса", true);
        }
        catch (OperationCanceledException)
        {
            return new WebSearchOutcome(Array.Empty<WebSearchHit>(), clean, "Веб-поиск отменён.", false);
        }
        catch (Exception ex)
        {
            _log.Warn("DrSearch web request failed: " + ex.Message);
            return new WebSearchOutcome(Array.Empty<WebSearchHit>(), clean,
                "Сеть недоступна — показаны локальные результаты и кнопки движков.", false);
        }
    }

    /// <summary>Parses the result page. Public so the self-test can pin it against a fixture.</summary>
    public static List<WebSearchHit> Parse(string html, int max)
    {
        var hits = new List<WebSearchHit>();
        if (string.IsNullOrWhiteSpace(html)) return hits;

        foreach (Match match in ResultRegex().Matches(html))
        {
            var url = DecodeHref(match.Groups["url"].Value);
            var title = Clean(match.Groups["title"].Value);
            if (string.IsNullOrWhiteSpace(url) || string.IsNullOrWhiteSpace(title)) continue;
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) continue;
            if (uri.Scheme is not ("http" or "https")) continue;
            if (hits.Any(h => h.Url.Equals(url, StringComparison.OrdinalIgnoreCase))) continue;

            hits.Add(new WebSearchHit
            {
                Title = WebUtility.HtmlDecode(title),
                Url = uri.ToString(),
                Snippet = string.Empty,
                Source = "DrSearch web"
            });

            if (hits.Count >= max) break;
        }

        // Snippets come in a second pass so a missing snippet never drops a result.
        var snippets = SnippetRegex().Matches(html);
        for (var i = 0; i < hits.Count && i < snippets.Count; i++)
        {
            var text = Clean(snippets[i].Groups["text"].Value);
            if (text.Length == 0) continue;
            var current = hits[i];
            hits[i] = new WebSearchHit
            {
                Title = current.Title,
                Url = current.Url,
                Snippet = WebUtility.HtmlDecode(text),
                Source = current.Source
            };
        }

        return hits;
    }

    private static string DecodeHref(string raw)
    {
        var href = WebUtility.HtmlDecode(raw ?? string.Empty).Trim();
        if (href.StartsWith("//", StringComparison.Ordinal)) href = "https:" + href;

        if (href.Contains("uddg=", StringComparison.Ordinal))
        {
            var index = href.IndexOf("uddg=", StringComparison.Ordinal) + 5;
            var end = href.IndexOf('&', index);
            var encoded = end < 0 ? href[index..] : href[index..end];
            return Uri.UnescapeDataString(encoded);
        }

        return href;
    }

    private static string Clean(string html)
    {
        var text = TagRegex().Replace(html ?? string.Empty, " ");
        text = WebUtility.HtmlDecode(text);
        return WhitespaceRegex().Replace(text, " ").Trim();
    }

    [GeneratedRegex("""<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="(?<url>[^"]+)"[^>]*>(?<title>.*?)</a>""",
        RegexOptions.Singleline | RegexOptions.IgnoreCase)]
    private static partial Regex ResultRegex();

    [GeneratedRegex("""<(?:a|div)[^>]+class="[^"]*result__snippet[^"]*"[^>]*>(?<text>.*?)</(?:a|div)>""",
        RegexOptions.Singleline | RegexOptions.IgnoreCase)]
    private static partial Regex SnippetRegex();

    [GeneratedRegex("<[^>]+>")]
    private static partial Regex TagRegex();

    [GeneratedRegex(@"\s{2,}")]
    private static partial Regex WhitespaceRegex();
}
