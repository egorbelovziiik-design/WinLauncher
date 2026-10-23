using System.IO;
using Microsoft.Data.Sqlite;

namespace DrBrowser;

/// <summary>SQLite storage for history, bookmarks, passwords, downloads and quick links.</summary>
public sealed partial class Db
{
    private readonly string _connectionString;

    public Db(string? path = null)
    {
        var file = path ?? AppPaths.DatabaseFile;
        var dir = Path.GetDirectoryName(file);
        if (!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
        _connectionString = new SqliteConnectionStringBuilder { DataSource = file }.ToString();
        Initialize();
    }

    public string FilePath => new SqliteConnectionStringBuilder(_connectionString).DataSource;

    private SqliteConnection Open()
    {
        var connection = new SqliteConnection(_connectionString);
        connection.Open();
        using var pragma = connection.CreateCommand();
        pragma.CommandText = "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;";
        pragma.ExecuteNonQuery();
        return connection;
    }

    public void Initialize()
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            CREATE TABLE IF NOT EXISTS history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL,
                title TEXT,
                host TEXT,
                visited_at TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS ix_history_visited ON history(visited_at DESC);
            CREATE INDEX IF NOT EXISTS ix_history_host ON history(host);

            CREATE TABLE IF NOT EXISTS bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                folder TEXT NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS passwords (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                site TEXT NOT NULL,
                username TEXT NOT NULL,
                secret BLOB NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS downloads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_name TEXT NOT NULL,
                path TEXT NOT NULL,
                url TEXT,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                state TEXT NOT NULL,
                started_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS quick_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                url TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS site_permissions (
                host TEXT NOT NULL,
                kind TEXT NOT NULL,
                allowed INTEGER NOT NULL,
                PRIMARY KEY (host, kind)
            );

            CREATE TABLE IF NOT EXISTS site_exceptions (
                host TEXT PRIMARY KEY
            );

            CREATE TABLE IF NOT EXISTS music_favorites (
                path TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                category TEXT NOT NULL,
                is_stream INTEGER NOT NULL DEFAULT 0,
                added_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS music_recent (
                path TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                is_stream INTEGER NOT NULL DEFAULT 0,
                played_at TEXT NOT NULL
            );
            """;
        command.ExecuteNonQuery();
    }

    public SqliteConnection Connect() => Open();

    // ---------------- history ----------------

    public long AddHistory(string url, string title, string host, DateTime at)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO history(url, title, host, visited_at) VALUES ($url, $title, $host, $at); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$url", url);
        command.Parameters.AddWithValue("$title", title ?? string.Empty);
        command.Parameters.AddWithValue("$host", host ?? string.Empty);
        command.Parameters.AddWithValue("$at", at.ToString("O"));
        return (long)(command.ExecuteScalar() ?? 0L);
    }

    public List<HistoryEntry> QueryHistory(string? search, DateTime? from, DateTime? to, int limit = 500)
    {
        var result = new List<HistoryEntry>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        var sql = "SELECT id, url, title, host, visited_at FROM history WHERE 1=1";
        if (!string.IsNullOrWhiteSpace(search))
        {
            sql += " AND (url LIKE $q OR title LIKE $q)";
            command.Parameters.AddWithValue("$q", $"%{search.Trim()}%");
        }
        if (from is not null)
        {
            sql += " AND visited_at >= $from";
            command.Parameters.AddWithValue("$from", from.Value.ToString("O"));
        }
        if (to is not null)
        {
            sql += " AND visited_at <= $to";
            command.Parameters.AddWithValue("$to", to.Value.ToString("O"));
        }
        sql += " ORDER BY visited_at DESC LIMIT $limit;";
        command.Parameters.AddWithValue("$limit", limit);
        command.CommandText = sql;

        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new HistoryEntry
            {
                Id = reader.GetInt64(0),
                Url = reader.GetString(1),
                Title = reader.IsDBNull(2) ? string.Empty : reader.GetString(2),
                Host = reader.IsDBNull(3) ? string.Empty : reader.GetString(3),
                VisitedAt = DateTime.TryParse(reader.GetString(4), out var when) ? when : DateTime.Now
            });
        }

        return result;
    }

    public void DeleteHistory(long id)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM history WHERE id = $id;";
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    public void ClearHistory()
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM history;";
        command.ExecuteNonQuery();
    }

    public List<VisitedSite> QueryMostVisited(int limit = 12)
    {
        var result = new List<VisitedSite>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT host, MAX(url) AS last_url, MAX(title) AS last_title, COUNT(*) AS visits, MAX(visited_at) AS last_at
            FROM history
            WHERE host <> ''
            GROUP BY host
            ORDER BY visits DESC, last_at DESC
            LIMIT $limit;
            """;
        command.Parameters.AddWithValue("$limit", limit);

        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new VisitedSite
            {
                Host = reader.GetString(0),
                Url = reader.IsDBNull(1) ? string.Empty : reader.GetString(1),
                Title = reader.IsDBNull(2) ? string.Empty : reader.GetString(2),
                Visits = reader.GetInt32(3),
                LastVisit = DateTime.TryParse(reader.IsDBNull(4) ? string.Empty : reader.GetString(4), out var when) ? when : DateTime.Now
            });
        }

        return result;
    }

    // ---------------- bookmarks ----------------

    public List<Bookmark> QueryBookmarks()
    {
        var result = new List<Bookmark>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, title, url, folder, created_at FROM bookmarks ORDER BY folder, title COLLATE NOCASE;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new Bookmark
            {
                Id = reader.GetInt64(0),
                Title = reader.GetString(1),
                Url = reader.GetString(2),
                Folder = reader.GetString(3),
                CreatedAt = DateTime.TryParse(reader.GetString(4), out var when) ? when : DateTime.Now
            });
        }

        return result;
    }

    public long AddBookmark(Bookmark bookmark)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO bookmarks(title, url, folder, created_at) VALUES ($t, $u, $f, $c); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$t", bookmark.Title);
        command.Parameters.AddWithValue("$u", bookmark.Url);
        command.Parameters.AddWithValue("$f", bookmark.Folder);
        command.Parameters.AddWithValue("$c", bookmark.CreatedAt.ToString("O"));
        return (long)(command.ExecuteScalar() ?? 0L);
    }

    public void UpdateBookmark(Bookmark bookmark)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "UPDATE bookmarks SET title = $t, url = $u, folder = $f WHERE id = $id;";
        command.Parameters.AddWithValue("$t", bookmark.Title);
        command.Parameters.AddWithValue("$u", bookmark.Url);
        command.Parameters.AddWithValue("$f", bookmark.Folder);
        command.Parameters.AddWithValue("$id", bookmark.Id);
        command.ExecuteNonQuery();
    }

    public void DeleteBookmark(long id)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM bookmarks WHERE id = $id;";
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    // ---------------- passwords ----------------

    public List<(long Id, string Site, string Username, byte[] Secret, DateTime Created)> QueryPasswords()
    {
        var result = new List<(long, string, string, byte[], DateTime)>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, site, username, secret, created_at FROM passwords ORDER BY site COLLATE NOCASE;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            var blob = (byte[])reader[3];
            result.Add((reader.GetInt64(0), reader.GetString(1), reader.GetString(2), blob,
                DateTime.TryParse(reader.GetString(4), out var when) ? when : DateTime.Now));
        }

        return result;
    }

    public long AddPassword(string site, string username, byte[] secret, DateTime created)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO passwords(site, username, secret, created_at) VALUES ($s, $u, $p, $c); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$s", site);
        command.Parameters.AddWithValue("$u", username);
        command.Parameters.Add("$p", SqliteType.Blob).Value = secret;
        command.Parameters.AddWithValue("$c", created.ToString("O"));
        return (long)(command.ExecuteScalar() ?? 0L);
    }

    public void DeletePassword(long id)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM passwords WHERE id = $id;";
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    // ---------------- downloads ----------------

    public long AddDownload(DownloadItem item)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO downloads(file_name, path, url, total_bytes, state, started_at)
            VALUES ($n, $p, $u, $t, $s, $d); SELECT last_insert_rowid();
            """;
        command.Parameters.AddWithValue("$n", item.FileName);
        command.Parameters.AddWithValue("$p", item.Path);
        command.Parameters.AddWithValue("$u", item.Url);
        command.Parameters.AddWithValue("$t", item.TotalBytes);
        command.Parameters.AddWithValue("$s", item.State.ToString());
        command.Parameters.AddWithValue("$d", item.StartedAt.ToString("O"));
        return (long)(command.ExecuteScalar() ?? 0L);
    }

    public void UpdateDownloadState(long id, DownloadState state, long totalBytes)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "UPDATE downloads SET state = $s, total_bytes = $t WHERE id = $id;";
        command.Parameters.AddWithValue("$s", state.ToString());
        command.Parameters.AddWithValue("$t", totalBytes);
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    public List<(string FileName, string Path, long Size, string State, DateTime Started)> QueryDownloads(int limit = 100)
    {
        var result = new List<(string, string, long, string, DateTime)>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT file_name, path, total_bytes, state, started_at FROM downloads ORDER BY started_at DESC LIMIT $l;";
        command.Parameters.AddWithValue("$l", limit);
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add((reader.GetString(0), reader.GetString(1), reader.IsDBNull(2) ? 0 : reader.GetInt64(2),
                reader.GetString(3), DateTime.TryParse(reader.GetString(4), out var when) ? when : DateTime.Now));
        }

        return result;
    }

    // ---------------- quick links ----------------

    public List<QuickLink> QueryQuickLinks()
    {
        var result = new List<QuickLink>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT id, title, url, sort_order FROM quick_links ORDER BY sort_order, id;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new QuickLink
            {
                Id = reader.GetInt64(0),
                Title = reader.GetString(1),
                Url = reader.GetString(2),
                SortOrder = reader.GetInt32(3)
            });
        }

        return result;
    }

    public long AddQuickLink(string title, string url, int sortOrder)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO quick_links(title, url, sort_order) VALUES ($t, $u, $o); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$t", title);
        command.Parameters.AddWithValue("$u", url);
        command.Parameters.AddWithValue("$o", sortOrder);
        return (long)(command.ExecuteScalar() ?? 0L);
    }

    public void DeleteQuickLink(long id)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM quick_links WHERE id = $id;";
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    // ---------------- site permissions / adblock exceptions ----------------

    public bool? GetPermission(string host, string kind)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT allowed FROM site_permissions WHERE host = $h AND kind = $k;";
        command.Parameters.AddWithValue("$h", host);
        command.Parameters.AddWithValue("$k", kind);
        var value = command.ExecuteScalar();
        return value is null ? null : Convert.ToInt64(value) != 0;
    }

    public void SetPermission(string host, string kind, bool allowed)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = """
            INSERT INTO site_permissions(host, kind, allowed) VALUES ($h, $k, $a)
            ON CONFLICT(host, kind) DO UPDATE SET allowed = excluded.allowed;
            """;
        command.Parameters.AddWithValue("$h", host);
        command.Parameters.AddWithValue("$k", kind);
        command.Parameters.AddWithValue("$a", allowed ? 1 : 0);
        command.ExecuteNonQuery();
    }

    public List<(string Host, string Kind, bool Allowed)> QueryPermissions()
    {
        var result = new List<(string, string, bool)>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT host, kind, allowed FROM site_permissions ORDER BY host;";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add((reader.GetString(0), reader.GetString(1), reader.GetInt64(2) != 0));
        }

        return result;
    }

    public void ClearPermissions()
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM site_permissions;";
        command.ExecuteNonQuery();
    }
}
