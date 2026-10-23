using System.Net.Http;
using System.Net;
using System.Text.Json;

namespace DrBrowser;

/// <summary>A track found in one of the public music catalogs.</summary>
public sealed class CatalogTrack
{
    public string Source { get; init; } = "iTunes";
    public string Title { get; init; } = string.Empty;
    public string Artist { get; init; } = string.Empty;
    public string Album { get; init; } = string.Empty;
    public string PreviewUrl { get; init; } = string.Empty;
    public string PageUrl { get; init; } = string.Empty;
    public string ArtworkUrl { get; init; } = string.Empty;
    public int DurationSeconds { get; init; }

    public bool HasPreview => !string.IsNullOrWhiteSpace(PreviewUrl);
    public string Badge => HasPreview ? Source.ToUpperInvariant() : Source.ToUpperInvariant() + " · LINK";
    public string Subtitle => string.IsNullOrWhiteSpace(Album) ? Artist : $"{Artist} · {Album}";
    public string DurationText => DurationSeconds <= 0
        ? (HasPreview ? "0:30" : "—")
        : $"{DurationSeconds / 60}:{DurationSeconds % 60:00}";
    public string Hint => HasPreview ? "Слушать 30-секундный превью-фрагмент" : "Открыть трек на сайте каталога";

    public MusicTrack ToMusicTrack() => new()
    {
        FilePath = HasPreview ? PreviewUrl : PageUrl,
        Title = string.IsNullOrWhiteSpace(Artist) ? Title : $"{Title} — {Artist}",
        Artist = Artist,
        Category = "Онлайн · " + Source,
        IsStream = true
    };
}

public sealed record CatalogOutcome(IReadOnlyList<CatalogTrack> Tracks, string Note);

/// <summary>
/// Online music search for DrMusic.
///
/// Sources: the public iTunes Search API and the public Deezer API. Both return a
/// 30-second preview URL that the browser can play directly, plus a link to the full
/// track. Nothing is downloaded, ripped or re-hosted.
///
/// About «взять источники из Яндекс Музыки»: Yandex Music has no public search or
/// streaming API, and its terms forbid third-party playback of its catalog. DrMusic
/// therefore offers a one-click hand-off to Yandex Music
/// (music.yandex.ru/search?text=…) instead of scraping its private endpoints — a
/// button that always works beats a parser that breaks every release.
/// </summary>
public sealed class MusicCatalogService
{
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromSeconds(15) };

    private readonly Logger _log;

    public MusicCatalogService(Logger log) => _log = log;

    public static IReadOnlyList<string> Sources { get; } = new[] { "iTunes", "Deezer" };

    public static string YandexMusicUrl(string query)
        => "https://music.yandex.ru/search?text=" + Uri.EscapeDataString(query ?? string.Empty);

    public async Task<CatalogOutcome> SearchAsync(string query, string source, CancellationToken token = default)
    {
        var term = (query ?? string.Empty).Trim();
        if (term.Length == 0) return new CatalogOutcome(Array.Empty<CatalogTrack>(), "Введите название трека или исполнителя.");

        try
        {
            if (string.Equals(source, "Deezer", StringComparison.OrdinalIgnoreCase))
            {
                var url = "https://api.deezer.com/search?limit=25&q=" + Uri.EscapeDataString(term);
                var json = await Client.GetStringAsync(url, token).ConfigureAwait(false);
                var tracks = ParseDeezer(json);
                _log.Info($"DrMusic catalog: Deezer \"{term}\" → {tracks.Count}");
                return new CatalogOutcome(tracks, tracks.Count == 0
                    ? "Deezer не нашёл совпадений."
                    : $"Deezer · {tracks.Count} треков с 30-секундным превью");
            }

            var itunes = "https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + Uri.EscapeDataString(term);
            var payload = await Client.GetStringAsync(itunes, token).ConfigureAwait(false);
            var found = ParseItunes(payload);
            _log.Info($"DrMusic catalog: iTunes \"{term}\" → {found.Count}");
            return new CatalogOutcome(found, found.Count == 0
                ? "iTunes не нашёл совпадений."
                : $"iTunes · {found.Count} треков с 30-секундным превью");
        }
        catch (OperationCanceledException)
        {
            return new CatalogOutcome(Array.Empty<CatalogTrack>(), "Поиск отменён.");
        }
        catch (Exception ex)
        {
            _log.Warn("music catalog search failed: " + ex.Message);
            return new CatalogOutcome(Array.Empty<CatalogTrack>(),
                "Каталог недоступен (нет сети или API ограничил запросы). Можно открыть поиск в Яндекс Музыке.");
        }
    }

    public static List<CatalogTrack> ParseItunes(string json)
    {
        var tracks = new List<CatalogTrack>();
        using var doc = JsonDocument.Parse(json);
        if (!doc.RootElement.TryGetProperty("results", out var results) || results.ValueKind != JsonValueKind.Array)
            return tracks;

        foreach (var item in results.EnumerateArray())
        {
            var title = Text(item, "trackName");
            var preview = Text(item, "previewUrl");
            var page = Text(item, "trackViewUrl");
            if (title.Length == 0) continue;
            if (preview.Length == 0 && page.Length == 0) continue;

            tracks.Add(new CatalogTrack
            {
                Source = "iTunes",
                Title = title,
                Artist = Text(item, "artistName"),
                Album = Text(item, "collectionName"),
                PreviewUrl = preview,
                PageUrl = page,
                ArtworkUrl = Text(item, "artworkUrl100"),
                DurationSeconds = Number(item, "trackTimeMillis") / 1000
            });
        }

        return tracks;
    }

    public static List<CatalogTrack> ParseDeezer(string json)
    {
        var tracks = new List<CatalogTrack>();
        using var doc = JsonDocument.Parse(json);
        if (!doc.RootElement.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Array)
            return tracks;

        foreach (var item in data.EnumerateArray())
        {
            var title = Text(item, "title");
            if (title.Length == 0) continue;

            tracks.Add(new CatalogTrack
            {
                Source = "Deezer",
                Title = title,
                Artist = item.TryGetProperty("artist", out var artist) ? Text(artist, "name") : string.Empty,
                Album = item.TryGetProperty("album", out var album) ? Text(album, "title") : string.Empty,
                PreviewUrl = Text(item, "preview"),
                PageUrl = Text(item, "link"),
                DurationSeconds = Number(item, "duration")
            });
        }

        return tracks;
    }

    private static string Text(JsonElement element, string name)
        => element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String
            ? WebUtility.HtmlDecode(value.GetString() ?? string.Empty)
            : string.Empty;

    private static int Number(JsonElement element, string name)
        => element.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number
            ? (int)value.GetDouble()
            : 0;
}
