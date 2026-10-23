using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>What a link found in the chat turned out to be.</summary>
public enum MediaKind
{
    Unknown,
    Video,
    Audio,
    Page
}

/// <summary>
/// Everything DrAI can honestly know about a link: title, author, description and a
/// short human-readable summary. No frame analysis — that needs a vision model.
/// </summary>
public sealed record MediaContext(
    string SourceUrl,
    MediaKind Kind,
    string Title,
    string Author,
    string Description,
    string Provider,
    bool Ok,
    string Message)
{
    public string OneLine => Ok
        ? $"{Provider} · {Kind} · {Title}"
        : Message;

    public string AsPromptBlock()
    {
        if (!Ok) return $"Ссылка: {SourceUrl}\nНе удалось получить метаданные: {Message}";

        var text = $"Ссылка: {SourceUrl}\nПровайдер: {Provider}\nТип: {Kind}\nЗаголовок: {Title}";
        if (!string.IsNullOrWhiteSpace(Author)) text += $"\nАвтор/канал: {Author}";
        if (!string.IsNullOrWhiteSpace(Description)) text += $"\nОписание: {Description}";
        return text;
    }
}

/// <summary>
/// Resolves concrete questions about links, videos and audio.
///
/// For known media hosts it asks the provider's public oEmbed endpoint (title, author,
/// thumbnail). For everything else it reads the page's &lt;title&gt;, meta description and
/// OpenGraph tags, and detects whether the page embeds a video/audio player.
///
/// It deliberately does not try to "watch" a video: without a vision model that would
/// be guessing, and DrBrowser does not guess.
/// </summary>
public static class MediaContextService
{
    private static readonly HttpClient Client = CreateClient();

    private static HttpClient CreateClient()
    {
        var handler = new HttpClientHandler
        {
            AutomaticDecompression = DecompressionMethods.GZip | DecompressionMethods.Deflate,
            UseCookies = false,
            AllowAutoRedirect = true
        };

        var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(12) };
        client.DefaultRequestHeaders.TryAddWithoutValidation("User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36 DrBrowser/2.1");
        client.DefaultRequestHeaders.TryAddWithoutValidation("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8");
        return client;
    }

    private static readonly Regex UrlRegex = new(
        @"https?://[^\s<>""']+", RegexOptions.Compiled | RegexOptions.IgnoreCase);

    /// <summary>First http(s) link mentioned in the text, if any.</summary>
    public static bool TryExtractUrl(string? text, out Uri uri)
    {
        uri = null!;
        if (string.IsNullOrWhiteSpace(text)) return false;

        var match = UrlRegex.Match(text);
        if (!match.Success) return false;

        var raw = match.Value.TrimEnd('.', ',', ')', ']', ';', ':', '!', '?', '»', '"', '\'');
        if (!Uri.TryCreate(raw, UriKind.Absolute, out var parsed)) return false;
        if (parsed.Scheme != Uri.UriSchemeHttp && parsed.Scheme != Uri.UriSchemeHttps) return false;

        uri = parsed;
        return true;
    }

    /// <summary>oEmbed endpoint for the hosts that publish one, else null.</summary>
    public static string? OEmbedEndpoint(Uri url)
    {
        var host = url.Host.ToLowerInvariant();
        var encoded = Uri.EscapeDataString(url.ToString());

        if (host is "youtu.be" or "www.youtube.com" or "youtube.com" or "m.youtube.com")
            return $"https://www.youtube.com/oembed?url={encoded}&format=json";

        if (host.EndsWith("vimeo.com", StringComparison.Ordinal))
            return $"https://vimeo.com/api/oembed.json?url={encoded}";

        if (host.EndsWith("rutube.ru", StringComparison.Ordinal))
            return $"https://rutube.ru/api/oembed/?url={encoded}&format=json";

        if (host.EndsWith("dailymotion.com", StringComparison.Ordinal) || host is "dai.ly")
            return $"https://www.dailymotion.com/services/oembed?url={encoded}&format=json";

        if (host.EndsWith("soundcloud.com", StringComparison.Ordinal))
            return $"https://soundcloud.com/oembed?url={encoded}&format=json";

        if (host.EndsWith("vimeo.com", StringComparison.Ordinal) || host.EndsWith("twitter.com", StringComparison.Ordinal))
            return null;

        return null;
    }

    public static MediaKind KindFor(Uri url)
    {
        var host = url.Host.ToLowerInvariant();
        if (host is "youtu.be" or "www.youtube.com" or "youtube.com" or "m.youtube.com"
            || host.EndsWith("vimeo.com", StringComparison.Ordinal)
            || host.EndsWith("rutube.ru", StringComparison.Ordinal)
            || host.EndsWith("dailymotion.com", StringComparison.Ordinal) || host is "dai.ly"
            || host.EndsWith("twitch.tv", StringComparison.Ordinal))
            return MediaKind.Video;

        if (host.EndsWith("soundcloud.com", StringComparison.Ordinal)
            || host.EndsWith("music.yandex.ru", StringComparison.Ordinal)
            || host.EndsWith("open.spotify.com", StringComparison.Ordinal))
            return MediaKind.Audio;

        return MediaKind.Page;
    }

    public static string ProviderFor(Uri url)
    {
        var host = url.Host.ToLowerInvariant();
        return host switch
        {
            "youtu.be" or "www.youtube.com" or "youtube.com" or "m.youtube.com" => "YouTube",
            _ when host.EndsWith("vimeo.com", StringComparison.Ordinal) => "Vimeo",
            _ when host.EndsWith("rutube.ru", StringComparison.Ordinal) => "RUTUBE",
            _ when host.EndsWith("dailymotion.com", StringComparison.Ordinal) || host is "dai.ly" => "Dailymotion",
            _ when host.EndsWith("soundcloud.com", StringComparison.Ordinal) => "SoundCloud",
            _ when host.EndsWith("music.yandex.ru", StringComparison.Ordinal) => "Яндекс Музыка",
            _ when host.EndsWith("open.spotify.com", StringComparison.Ordinal) => "Spotify",
            _ when host.EndsWith("twitch.tv", StringComparison.Ordinal) => "Twitch",
            _ => host
        };
    }

    /// <summary>Parses an oEmbed JSON document. Pure function — covered by the self-test.</summary>
    public static MediaContext ParseOEmbed(string json, Uri source)
    {
        var provider = ProviderFor(source);
        var kind = KindFor(source);

        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            string Get(params string[] names)
            {
                foreach (var name in names)
                {
                    if (root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String)
                    {
                        var text = value.GetString();
                        if (!string.IsNullOrWhiteSpace(text)) return text!.Trim();
                    }
                }

                return string.Empty;
            }

            var title = Get("title");
            var author = Get("author_name", "author");
            var description = Get("description", "summary");
            var type = Get("type");

            if (string.IsNullOrWhiteSpace(title) && string.IsNullOrWhiteSpace(description))
                return new MediaContext(source.ToString(), kind, string.Empty, string.Empty, string.Empty,
                    provider, false, "oEmbed не вернул ни заголовка, ни описания.");

            if (type.Equals("video", StringComparison.OrdinalIgnoreCase)) kind = MediaKind.Video;
            else if (type.Equals("audio", StringComparison.OrdinalIgnoreCase)
                     || type.Equals("rich", StringComparison.OrdinalIgnoreCase) && kind == MediaKind.Unknown)
                kind = kind == MediaKind.Unknown ? MediaKind.Audio : kind;

            return new MediaContext(source.ToString(), kind, title, author, description, provider, true,
                "метаданные получены через oEmbed");
        }
        catch (Exception ex)
        {
            return new MediaContext(source.ToString(), kind, string.Empty, string.Empty, string.Empty,
                provider, false, "oEmbed вернул не JSON: " + ex.Message);
        }
    }

    /// <summary>Reads title/description/OpenGraph tags out of generic HTML. Pure function.</summary>
    public static MediaContext ParseHtml(string html, Uri source)
    {
        var title = FirstMatch(html, @"<title[^>]*>(?<v>.*?)</title>");
        var ogTitle = MetaContent(html, "og:title");
        var description = MetaContent(html, "og:description");
        if (string.IsNullOrWhiteSpace(description)) description = MetaContent(html, "description");
        var ogSite = MetaContent(html, "og:site_name");
        var ogVideo = MetaContent(html, "og:video:type") + MetaContent(html, "og:video");

        var kind = KindFor(source);
        if (kind == MediaKind.Page && (html.Contains("<video", StringComparison.OrdinalIgnoreCase)
                                       || ogVideo.Length > 0))
            kind = MediaKind.Video;

        var provider = string.IsNullOrWhiteSpace(ogSite) ? ProviderFor(source) : ogSite;

        ParseHtmlTruncate(description, out var shortDescription);

        var finalTitle = string.IsNullOrWhiteSpace(ogTitle) ? title : ogTitle;
        var ok = !string.IsNullOrWhiteSpace(finalTitle) || !string.IsNullOrWhiteSpace(shortDescription);

        return new MediaContext(source.ToString(), kind, WebUtility.HtmlDecode(finalTitle ?? string.Empty),
            string.Empty, shortDescription, provider, ok,
            ok ? "метаданные прочитаны со страницы" : "страница не отдала заголовок и описание");
    }

    private static void ParseHtmlTruncate(string? description, out string shortDescription)
    {
        var text = WebUtility.HtmlDecode(description ?? string.Empty).Trim();
        shortDescription = text.Length <= 600 ? text : text[..600] + "…";
    }

    private static string? FirstMatch(string html, string pattern)
    {
        var match = Regex.Match(html, pattern, RegexOptions.IgnoreCase | RegexOptions.Singleline);
        return match.Success ? Regex.Replace(match.Groups["v"].Value, @"\s+", " ").Trim() : null;
    }

    private static string MetaContent(string html, string name)
    {
        var pattern = $@"<meta[^>]+(?:property|name)\s*=\s*[""']{Regex.Escape(name)}[""'][^>]*content\s*=\s*[""'](?<v>[^""']*)[""']";
        var match = Regex.Match(html, pattern, RegexOptions.IgnoreCase);
        return match.Success ? match.Groups["v"].Value.Trim() : string.Empty;
    }

    /// <summary>Full lookup: oEmbed first, generic page metadata second. Never throws.</summary>
    public static async Task<MediaContext> DescribeAsync(Uri url, CancellationToken token = default)
    {
        var kind = KindFor(url);
        var provider = ProviderFor(url);

        var endpoint = OEmbedEndpoint(url);
        if (endpoint is not null)
        {
            try
            {
                using var response = await Client.GetAsync(endpoint, token).ConfigureAwait(false);
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync(token).ConfigureAwait(false);
                    var parsed = ParseOEmbed(json, url);
                    if (parsed.Ok) return parsed;
                }
            }
            catch
            {
                // fall through to the generic page reader
            }
        }

        try
        {
            using var response = await Client.GetAsync(url, token).ConfigureAwait(false);
            if (!response.IsSuccessStatusCode)
                return new MediaContext(url.ToString(), kind, string.Empty, string.Empty, string.Empty, provider,
                    false, $"сайт ответил HTTP {(int)response.StatusCode}");

            var html = await response.Content.ReadAsStringAsync(token).ConfigureAwait(false);
            if (html.Length > 400_000) html = html[..400_000];
            return ParseHtml(html, url);
        }
        catch (OperationCanceledException)
        {
            return new MediaContext(url.ToString(), kind, string.Empty, string.Empty, string.Empty, provider,
                false, "запрос отменён");
        }
        catch (Exception ex)
        {
            return new MediaContext(url.ToString(), kind, string.Empty, string.Empty, string.Empty, provider,
                false, ex.Message);
        }
    }
}
