using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.Sqlite;

namespace DrBrowser;

public sealed record SearchDocumentInput(
    string Url,
    string Title,
    string Body,
    string Keywords = "",
    string? CanonicalUrl = null,
    string? Language = null,
    DateTimeOffset? FetchedAt = null);

public sealed record IndexedSearchHit(
    long Id,
    string Url,
    string Title,
    string Snippet,
    string Language,
    DateTimeOffset IndexedAt,
    double Score);

public sealed record SearchIndexStats(long DocumentCount, DateTimeOffset? LastUpdated, long PendingUrls);

/// <summary>
/// DrSearch's own local index. Documents are stored in SQLite and mirrored into an
/// FTS5 table. SQLite's native bm25() function performs the first ranking pass; title,
/// URL and freshness signals are then exposed as a stable score to the UI/DrMind.
/// </summary>
public sealed partial class SearchIndexService
{
    private readonly Db _db;
    private readonly Logger _log;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public SearchIndexService(Db db, Logger log)
    {
        _db = db;
        _log = log;
        Initialize();
    }

    public bool IsAvailable { get; private set; }
    public string InitializationError { get; private set; } = string.Empty;

    private void Initialize()
    {
        try
        {
            using var connection = _db.Connect();
            using var command = connection.CreateCommand();
            command.CommandText = """
                CREATE TABLE IF NOT EXISTS search_documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    url TEXT NOT NULL,
                    canonical_url TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    keywords TEXT NOT NULL DEFAULT '',
                    language TEXT NOT NULL DEFAULT 'und',
                    content_hash TEXT NOT NULL,
                    fetched_at TEXT NOT NULL,
                    indexed_at TEXT NOT NULL,
                    quality REAL NOT NULL DEFAULT 0
                );
                CREATE INDEX IF NOT EXISTS ix_search_documents_hash ON search_documents(content_hash);
                CREATE INDEX IF NOT EXISTS ix_search_documents_updated ON search_documents(indexed_at DESC);
                CREATE INDEX IF NOT EXISTS ix_search_documents_url ON search_documents(url);

                CREATE VIRTUAL TABLE IF NOT EXISTS search_documents_fts USING fts5(
                    url,
                    title,
                    body,
                    keywords,
                    language UNINDEXED,
                    tokenize='unicode61 remove_diacritics 2'
                );

                CREATE TABLE IF NOT EXISTS search_crawl_queue (
                    url TEXT PRIMARY KEY,
                    depth INTEGER NOT NULL,
                    state TEXT NOT NULL DEFAULT 'pending',
                    discovered_at TEXT NOT NULL,
                    attempted_at TEXT,
                    error TEXT
                );
                CREATE INDEX IF NOT EXISTS ix_search_crawl_queue_state ON search_crawl_queue(state, depth);

                CREATE TABLE IF NOT EXISTS search_index_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
                INSERT INTO search_index_metadata(key, value) VALUES ('schema_version', '1')
                    ON CONFLICT(key) DO NOTHING;
                """;
            command.ExecuteNonQuery();
            IsAvailable = true;
        }
        catch (Exception ex)
        {
            IsAvailable = false;
            InitializationError = ex.Message;
            _log.Error("DrSearch FTS5 initialization failed", ex);
        }
    }

    public async Task<long> UpsertAsync(SearchDocumentInput document, CancellationToken token = default)
    {
        if (!IsAvailable) throw new InvalidOperationException("FTS5 index is unavailable: " + InitializationError);
        token.ThrowIfCancellationRequested();

        var url = NormalizeUrl(document.Url);
        var canonical = NormalizeUrl(string.IsNullOrWhiteSpace(document.CanonicalUrl) ? url : document.CanonicalUrl!);
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme is not ("http" or "https"))
            throw new ArgumentException("Only public HTTP/HTTPS documents can be indexed.", nameof(document));
        if (!Uri.TryCreate(canonical, UriKind.Absolute, out var canonicalUri)
            || canonicalUri.Scheme is not ("http" or "https"))
            canonical = url;

        var title = CleanText(document.Title, 500);
        if (title.Length == 0) title = uri.Host;
        var body = CleanText(document.Body, 1_000_000);
        if (body.Length < 20) throw new ArgumentException("The extracted document text is too short to index.", nameof(document));
        var keywords = CleanText(document.Keywords, 2000);
        var language = NormalizeLanguage(document.Language, body);
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(body)));
        var now = DateTimeOffset.UtcNow;
        var quality = CalculateQuality(title, body);

        await _gate.WaitAsync(token).ConfigureAwait(false);
        try
        {
            using var connection = _db.Connect();
            using var transaction = connection.BeginTransaction();

            // Exact-content duplicates do not take another place in the index. A later
            // crawl of the canonical URL still refreshes that document below.
            using (var duplicate = connection.CreateCommand())
            {
                duplicate.Transaction = transaction;
                duplicate.CommandText = "SELECT id, canonical_url FROM search_documents WHERE content_hash=$hash LIMIT 1;";
                duplicate.Parameters.AddWithValue("$hash", hash);
                using var reader = duplicate.ExecuteReader();
                if (reader.Read() && !reader.GetString(1).Equals(canonical, StringComparison.OrdinalIgnoreCase))
                {
                    var existing = reader.GetInt64(0);
                    transaction.Commit();
                    return existing;
                }
            }

            long id;
            using (var upsert = connection.CreateCommand())
            {
                upsert.Transaction = transaction;
                upsert.CommandText = """
                    INSERT INTO search_documents(url, canonical_url, title, body, keywords, language, content_hash, fetched_at, indexed_at, quality)
                    VALUES($url,$canonical,$title,$body,$keywords,$language,$hash,$fetched,$indexed,$quality)
                    ON CONFLICT(canonical_url) DO UPDATE SET
                        url=excluded.url,
                        title=excluded.title,
                        body=excluded.body,
                        keywords=excluded.keywords,
                        language=excluded.language,
                        content_hash=excluded.content_hash,
                        fetched_at=excluded.fetched_at,
                        indexed_at=excluded.indexed_at,
                        quality=excluded.quality
                    RETURNING id;
                    """;
                upsert.Parameters.AddWithValue("$url", url);
                upsert.Parameters.AddWithValue("$canonical", canonical);
                upsert.Parameters.AddWithValue("$title", title);
                upsert.Parameters.AddWithValue("$body", body);
                upsert.Parameters.AddWithValue("$keywords", keywords);
                upsert.Parameters.AddWithValue("$language", language);
                upsert.Parameters.AddWithValue("$hash", hash);
                upsert.Parameters.AddWithValue("$fetched", (document.FetchedAt ?? now).ToString("O"));
                upsert.Parameters.AddWithValue("$indexed", now.ToString("O"));
                upsert.Parameters.AddWithValue("$quality", quality);
                id = Convert.ToInt64(upsert.ExecuteScalar(), CultureInfo.InvariantCulture);
            }

            using (var removeFts = connection.CreateCommand())
            {
                removeFts.Transaction = transaction;
                removeFts.CommandText = "DELETE FROM search_documents_fts WHERE rowid=$id;";
                removeFts.Parameters.AddWithValue("$id", id);
                removeFts.ExecuteNonQuery();
            }

            using (var addFts = connection.CreateCommand())
            {
                addFts.Transaction = transaction;
                addFts.CommandText = """
                    INSERT INTO search_documents_fts(rowid,url,title,body,keywords,language)
                    VALUES($id,$url,$title,$body,$keywords,$language);
                    """;
                addFts.Parameters.AddWithValue("$id", id);
                addFts.Parameters.AddWithValue("$url", url);
                addFts.Parameters.AddWithValue("$title", title);
                addFts.Parameters.AddWithValue("$body", body);
                addFts.Parameters.AddWithValue("$keywords", keywords);
                addFts.Parameters.AddWithValue("$language", language);
                addFts.ExecuteNonQuery();
            }

            using (var metadata = connection.CreateCommand())
            {
                metadata.Transaction = transaction;
                metadata.CommandText = """
                    INSERT INTO search_index_metadata(key,value) VALUES('last_updated',$at)
                    ON CONFLICT(key) DO UPDATE SET value=excluded.value;
                    """;
                metadata.Parameters.AddWithValue("$at", now.ToString("O"));
                metadata.ExecuteNonQuery();
            }

            transaction.Commit();
            return id;
        }
        finally
        {
            _gate.Release();
        }
    }

    public IReadOnlyList<IndexedSearchHit> Search(string query, int limit = 30, string? domain = null, string? language = null)
    {
        if (!IsAvailable || string.IsNullOrWhiteSpace(query)) return Array.Empty<IndexedSearchHit>();
        var match = BuildMatchQuery(query);
        if (match.Length == 0) return Array.Empty<IndexedSearchHit>();

        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        var where = new StringBuilder("search_documents_fts MATCH $query");
        command.Parameters.AddWithValue("$query", match);

        if (!string.IsNullOrWhiteSpace(domain))
        {
            where.Append(" AND d.url LIKE $domain");
            command.Parameters.AddWithValue("$domain", "%://" + domain.Trim().TrimStart('.').ToLowerInvariant() + "/%");
        }
        if (!string.IsNullOrWhiteSpace(language) && language is not "all")
        {
            where.Append(" AND d.language=$language");
            command.Parameters.AddWithValue("$language", language.ToLowerInvariant());
        }

        command.CommandText = $"""
            SELECT d.id,
                   d.url,
                   d.title,
                   snippet(search_documents_fts, 2, '⟦', '⟧', ' … ', 28),
                   d.language,
                   d.indexed_at,
                   bm25(search_documents_fts, 2.5, 9.0, 1.0, 4.0, 0.0) AS rank,
                   d.quality
            FROM search_documents_fts
            JOIN search_documents d ON d.id=search_documents_fts.rowid
            WHERE {where}
            ORDER BY rank ASC, d.quality DESC, d.indexed_at DESC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$limit", Math.Clamp(limit, 1, 100));

        var results = new List<IndexedSearchHit>();
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            var indexed = DateTimeOffset.TryParse(reader.GetString(5), CultureInfo.InvariantCulture,
                DateTimeStyles.RoundtripKind, out var parsed) ? parsed : DateTimeOffset.MinValue;
            var rank = reader.IsDBNull(6) ? 0 : reader.GetDouble(6);
            var quality = reader.IsDBNull(7) ? 0 : reader.GetDouble(7);
            var freshness = indexed == DateTimeOffset.MinValue ? 0 : Math.Max(0, 1 - (DateTimeOffset.UtcNow - indexed).TotalDays / 365.0);
            results.Add(new IndexedSearchHit(
                reader.GetInt64(0),
                reader.GetString(1),
                reader.GetString(2),
                reader.IsDBNull(3) ? string.Empty : reader.GetString(3),
                reader.IsDBNull(4) ? "und" : reader.GetString(4),
                indexed,
                Math.Round(-rank + quality + freshness * 0.15, 4)));
        }

        return results;
    }

    public SearchIndexStats GetStats()
    {
        if (!IsAvailable) return new SearchIndexStats(0, null, 0);
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT
              (SELECT COUNT(*) FROM search_documents),
              (SELECT value FROM search_index_metadata WHERE key='last_updated'),
              (SELECT COUNT(*) FROM search_crawl_queue WHERE state='pending');
            """;
        using var reader = command.ExecuteReader();
        if (!reader.Read()) return new SearchIndexStats(0, null, 0);
        DateTimeOffset? updated = null;
        if (!reader.IsDBNull(1) && DateTimeOffset.TryParse(reader.GetString(1), out var parsed)) updated = parsed;
        return new SearchIndexStats(reader.GetInt64(0), updated, reader.GetInt64(2));
    }

    public async Task ClearAsync(CancellationToken token = default)
    {
        if (!IsAvailable) return;
        await _gate.WaitAsync(token).ConfigureAwait(false);
        try
        {
            using var connection = _db.Connect();
            using var transaction = connection.BeginTransaction();
            foreach (var sql in new[]
                     {
                         "DELETE FROM search_documents_fts;",
                         "DELETE FROM search_documents;",
                         "DELETE FROM search_crawl_queue;",
                         "DELETE FROM search_index_metadata WHERE key='last_updated';"
                     })
            {
                using var command = connection.CreateCommand();
                command.Transaction = transaction;
                command.CommandText = sql;
                command.ExecuteNonQuery();
            }
            transaction.Commit();
        }
        finally
        {
            _gate.Release();
        }
    }

    public static string NormalizeUrl(string raw)
    {
        if (!Uri.TryCreate(raw?.Trim(), UriKind.Absolute, out var uri)) return raw?.Trim() ?? string.Empty;
        if (uri.Scheme is not ("http" or "https")) return uri.ToString();

        var query = uri.Query.TrimStart('?')
            .Split('&', StringSplitOptions.RemoveEmptyEntries)
            .Select(part => part.Split('=', 2))
            .Where(pair => pair.Length > 0 && !IsTrackingParameter(Uri.UnescapeDataString(pair[0])))
            .OrderBy(pair => pair[0], StringComparer.OrdinalIgnoreCase)
            .Select(pair => string.Join("=", pair));

        var builder = new UriBuilder(uri)
        {
            Scheme = uri.Scheme.ToLowerInvariant(),
            Host = uri.IdnHost.ToLowerInvariant(),
            Fragment = string.Empty,
            Query = string.Join("&", query)
        };
        if ((builder.Scheme == "https" && builder.Port == 443) || (builder.Scheme == "http" && builder.Port == 80))
            builder.Port = -1;
        if (builder.Path.Length > 1) builder.Path = builder.Path.TrimEnd('/');
        return builder.Uri.AbsoluteUri;
    }

    public static string DetectLanguage(string text)
    {
        var cyrillic = text.Count(c => c is >= '\u0400' and <= '\u04FF');
        var latin = text.Count(c => c is >= 'A' and <= 'Z' or >= 'a' and <= 'z');
        if (cyrillic > latin * 0.25 && cyrillic > 8) return "ru";
        if (latin > 8) return "en";
        return "und";
    }

    private static string NormalizeLanguage(string? language, string body)
    {
        var value = language?.Trim().ToLowerInvariant() ?? string.Empty;
        if (value.StartsWith("ru")) return "ru";
        if (value.StartsWith("en")) return "en";
        return DetectLanguage(body);
    }

    private static double CalculateQuality(string title, string body)
    {
        var lengthScore = Math.Min(1, body.Length / 3000d);
        var titleScore = title.Length is >= 4 and <= 160 ? 0.35 : 0.05;
        var words = WordRegex().Matches(body).Count;
        var lexical = body.Length == 0 ? 0 : Math.Min(0.4, words / Math.Max(1d, body.Length / 5d) * 0.4);
        return Math.Round(lengthScore * 0.5 + titleScore + lexical, 4);
    }

    private static string BuildMatchQuery(string input)
    {
        var terms = QueryTokenRegex().Matches(input.Normalize(NormalizationForm.FormKC))
            .Select(match => match.Groups[1].Success ? match.Groups[1].Value : match.Value)
            .Select(value => value.Trim().Replace("\"", "\"\""))
            .Where(value => value.Length > 0)
            .Take(16)
            .Select(value => value.Contains(' ') ? $"\"{value}\"" : $"\"{value}\"*")
            .ToList();
        return string.Join(" AND ", terms);
    }

    private static string CleanText(string? value, int max)
    {
        var text = WhitespaceRegex().Replace(value ?? string.Empty, " ").Trim();
        return text.Length <= max ? text : text[..max];
    }

    private static bool IsTrackingParameter(string key)
        => key.StartsWith("utm_", StringComparison.OrdinalIgnoreCase)
           || key.Equals("fbclid", StringComparison.OrdinalIgnoreCase)
           || key.Equals("gclid", StringComparison.OrdinalIgnoreCase)
           || key.Equals("yclid", StringComparison.OrdinalIgnoreCase)
           || key.Equals("mc_cid", StringComparison.OrdinalIgnoreCase)
           || key.Equals("mc_eid", StringComparison.OrdinalIgnoreCase);

    [GeneratedRegex(@"[\p{L}\p{N}]{2,}")]
    private static partial Regex WordRegex();

    [GeneratedRegex("\"([^\"]+)\"|[\\p{L}\\p{N}_-]+")]
    private static partial Regex QueryTokenRegex();

    [GeneratedRegex(@"\s+")]
    private static partial Regex WhitespaceRegex();
}
