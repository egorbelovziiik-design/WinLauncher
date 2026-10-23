namespace DrBrowser;

public sealed record MusicPlaylistInfo(long Id, string Name, int TrackCount)
{
    public string Label => $"{Name}  ·  {TrackCount}";
}

/// <summary>SQLite persistence for named DrMusic playlists and favorites.</summary>
public sealed class MusicPlaylistService
{
    private readonly Db _db;

    public MusicPlaylistService(Db db) => _db = db;

    public Task<IReadOnlyList<MusicPlaylistInfo>> LoadAsync() => Task.Run<IReadOnlyList<MusicPlaylistInfo>>(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT p.id, p.name, COUNT(pt.track_id)
            FROM music_playlists p
            LEFT JOIN music_playlist_tracks pt ON pt.playlist_id=p.id
            GROUP BY p.id, p.name
            ORDER BY p.name COLLATE NOCASE;
            """;
        using var reader = command.ExecuteReader();
        var result = new List<MusicPlaylistInfo>();
        while (reader.Read()) result.Add(new MusicPlaylistInfo(reader.GetInt64(0), reader.GetString(1), reader.GetInt32(2)));
        return result;
    });

    public Task<long> CreateAsync(string name) => Task.Run(() =>
    {
        name = (name ?? string.Empty).Trim();
        if (name.Length is < 1 or > 80) throw new ArgumentException("Название плейлиста: от 1 до 80 символов.");
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "INSERT INTO music_playlists(name, created_at, updated_at) VALUES($name,$now,$now); SELECT last_insert_rowid();";
        command.Parameters.AddWithValue("$name", name);
        command.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        try { return (long)(command.ExecuteScalar() ?? 0L); }
        catch (Exception ex) when (ex.Message.Contains("UNIQUE", StringComparison.OrdinalIgnoreCase))
        { throw new ArgumentException("Плейлист с таким названием уже существует."); }
    });

    public Task DeleteAsync(long playlistId) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var transaction = connection.BeginTransaction();
        using var items = connection.CreateCommand();
        items.Transaction = transaction;
        items.CommandText = "DELETE FROM music_playlist_tracks WHERE playlist_id=$id;";
        items.Parameters.AddWithValue("$id", playlistId);
        items.ExecuteNonQuery();
        using var playlist = connection.CreateCommand();
        playlist.Transaction = transaction;
        playlist.CommandText = "DELETE FROM music_playlists WHERE id=$id;";
        playlist.Parameters.AddWithValue("$id", playlistId);
        playlist.ExecuteNonQuery();
        transaction.Commit();
    });

    public Task AddTrackAsync(long playlistId, MusicTrack track) => Task.Run(() =>
    {
        if (playlistId <= 0) throw new ArgumentException("Сначала выберите обычный плейлист.");
        using var connection = _db.Connect();
        using var transaction = connection.BeginTransaction();
        var trackId = UpsertTrack(connection, transaction, track);
        using var add = connection.CreateCommand();
        add.Transaction = transaction;
        add.CommandText = """
            INSERT INTO music_playlist_tracks(playlist_id,track_id,sort_order)
            VALUES($playlist,$track,COALESCE((SELECT MAX(sort_order)+1 FROM music_playlist_tracks WHERE playlist_id=$playlist),0))
            ON CONFLICT(playlist_id,track_id) DO NOTHING;
            UPDATE music_playlists SET updated_at=$now WHERE id=$playlist;
            """;
        add.Parameters.AddWithValue("$playlist", playlistId);
        add.Parameters.AddWithValue("$track", trackId);
        add.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        add.ExecuteNonQuery();
        transaction.Commit();
    });

    public Task RemoveTrackAsync(long playlistId, MusicTrack track) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM music_playlist_tracks WHERE playlist_id=$playlist AND track_id=(SELECT id FROM music_tracks WHERE path=$path);";
        command.Parameters.AddWithValue("$playlist", playlistId);
        command.Parameters.AddWithValue("$path", track.FilePath);
        command.ExecuteNonQuery();
    });

    public Task<HashSet<string>> LoadPathsAsync(long playlistId) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = """
            SELECT t.path FROM music_playlist_tracks pt
            JOIN music_tracks t ON t.id=pt.track_id
            WHERE pt.playlist_id=$id ORDER BY pt.sort_order;
            """;
        command.Parameters.AddWithValue("$id", playlistId);
        using var reader = command.ExecuteReader();
        var result = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        while (reader.Read()) result.Add(reader.GetString(0));
        return result;
    });

    public Task<HashSet<string>> LoadFavoritePathsAsync() => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT t.path FROM music_favorites f JOIN music_tracks t ON t.id=f.track_id ORDER BY f.added_at DESC;";
        using var reader = command.ExecuteReader();
        var result = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        while (reader.Read()) result.Add(reader.GetString(0));
        return result;
    });

    public Task<bool> ToggleFavoriteAsync(MusicTrack track) => Task.Run(() =>
    {
        using var connection = _db.Connect();
        using var transaction = connection.BeginTransaction();
        var trackId = UpsertTrack(connection, transaction, track);
        using var exists = connection.CreateCommand();
        exists.Transaction = transaction;
        exists.CommandText = "SELECT COUNT(*) FROM music_favorites WHERE track_id=$id;";
        exists.Parameters.AddWithValue("$id", trackId);
        var isFavorite = Convert.ToInt64(exists.ExecuteScalar()) > 0;
        using var update = connection.CreateCommand();
        update.Transaction = transaction;
        update.CommandText = isFavorite
            ? "DELETE FROM music_favorites WHERE track_id=$id;"
            : "INSERT INTO music_favorites(track_id,added_at) VALUES($id,$now);";
        update.Parameters.AddWithValue("$id", trackId);
        update.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        update.ExecuteNonQuery();
        transaction.Commit();
        return !isFavorite;
    });

    private static long UpsertTrack(Microsoft.Data.Sqlite.SqliteConnection connection,
        Microsoft.Data.Sqlite.SqliteTransaction transaction, MusicTrack track)
    {
        using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            INSERT INTO music_tracks(path,title,artist,album,duration_ms,added_at)
            VALUES($path,$title,$artist,$album,0,$now)
            ON CONFLICT(path) DO UPDATE SET title=excluded.title,artist=excluded.artist,album=excluded.album;
            """;
        command.Parameters.AddWithValue("$path", track.FilePath);
        command.Parameters.AddWithValue("$title", track.Title);
        command.Parameters.AddWithValue("$artist", track.Artist);
        command.Parameters.AddWithValue("$album", track.Category);
        command.Parameters.AddWithValue("$now", DateTime.UtcNow.ToString("O"));
        command.ExecuteNonQuery();
        using var select = connection.CreateCommand();
        select.Transaction = transaction;
        select.CommandText = "SELECT id FROM music_tracks WHERE path=$path;";
        select.Parameters.AddWithValue("$path", track.FilePath);
        return (long)(select.ExecuteScalar() ?? 0L);
    }
}
