using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.Data.Sqlite;

namespace DrBrowser;

/// <summary>Read-only view of one row of the DrMusic favourites table.</summary>
public sealed record MusicFavoriteRow(string Path, string Title, string Artist, string Category, bool IsStream);

/// <summary>One playlist with its track count (DrMusic).</summary>
public sealed record MusicPlaylistRow(long Id, string Name, int TrackCount);

/// <summary>One track inside a playlist, in playlist order.</summary>
public sealed record MusicPlaylistTrackRow(long Id, long PlaylistId, int Position, string Path, string Title,
    string Artist, string Category, bool IsStream);

/// <summary>Read-only view of one row of the DrMusic recently-played table.</summary>
public sealed record MusicRecentRow(string Path, string Title, string Artist, bool IsStream);

public partial class Db
{
    // ---------------- DrMusic: favourites + recently played ----------------

    public void AddMusicFavorite(MusicFavoriteRow row)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            INSERT INTO music_favorites (path, title, artist, category, is_stream, added_at)
            VALUES ($path, $title, $artist, $category, $stream, $at)
            ON CONFLICT(path) DO UPDATE SET
                title = $title, artist = $artist, category = $category, is_stream = $stream";
        command.Parameters.AddWithValue("$path", row.Path);
        command.Parameters.AddWithValue("$title", row.Title);
        command.Parameters.AddWithValue("$artist", row.Artist);
        command.Parameters.AddWithValue("$category", row.Category);
        command.Parameters.AddWithValue("$stream", row.IsStream ? 1 : 0);
        command.Parameters.AddWithValue("$at", DateTime.UtcNow.ToString("O"));
        command.ExecuteNonQuery();
    }

    public void RemoveMusicFavorite(string path)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM music_favorites WHERE path = $path";
        command.Parameters.AddWithValue("$path", path);
        command.ExecuteNonQuery();
    }

    public List<MusicFavoriteRow> QueryMusicFavorites()
    {
        var result = new List<MusicFavoriteRow>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT path, title, artist, category, is_stream FROM music_favorites ORDER BY added_at DESC";
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new MusicFavoriteRow(reader.GetString(0), reader.GetString(1), reader.GetString(2),
                reader.GetString(3), reader.GetInt64(4) != 0));
        }

        return result;
    }

    /// <summary>Records a play; replaying a track moves it up instead of duplicating it.</summary>
    public void AddMusicRecent(MusicRecentRow row, int keep = 40)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            INSERT INTO music_recent (path, title, artist, is_stream, played_at)
            VALUES ($path, $title, $artist, $stream, $at)
            ON CONFLICT(path) DO UPDATE SET played_at = $at, title = $title, artist = $artist";
        command.Parameters.AddWithValue("$path", row.Path);
        command.Parameters.AddWithValue("$title", row.Title);
        command.Parameters.AddWithValue("$artist", row.Artist);
        command.Parameters.AddWithValue("$stream", row.IsStream ? 1 : 0);
        command.Parameters.AddWithValue("$at", DateTime.UtcNow.ToString("O"));
        command.ExecuteNonQuery();

        using var trim = connection.CreateCommand();
        trim.CommandText =
            "DELETE FROM music_recent WHERE path NOT IN (SELECT path FROM music_recent ORDER BY played_at DESC LIMIT $keep)";
        trim.Parameters.AddWithValue("$keep", Math.Max(1, keep));
        trim.ExecuteNonQuery();
    }

    public List<MusicRecentRow> QueryMusicRecent(int limit = 12)
    {
        var result = new List<MusicRecentRow>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "SELECT path, title, artist, is_stream FROM music_recent ORDER BY played_at DESC LIMIT $limit";
        command.Parameters.AddWithValue("$limit", Math.Max(1, limit));
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new MusicRecentRow(reader.GetString(0), reader.GetString(1), reader.GetString(2),
                reader.GetInt64(3) != 0));
        }

        return result;
    }

    public void ClearMusicRecent()
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM music_recent";
        command.ExecuteNonQuery();
    }

    // ---------------- playlists ----------------

    /// <summary>Creates a playlist, or returns the id of the existing one with that name (case-insensitive).</summary>
    public long CreatePlaylist(string name)
    {
        var clean = (name ?? string.Empty).Trim();
        if (clean.Length == 0) throw new ArgumentException("Пустое имя плейлиста", nameof(name));

        using var connection = Open();
        using var insert = connection.CreateCommand();
        insert.CommandText = "INSERT OR IGNORE INTO playlists (name, created_at) VALUES ($name, $at)";
        insert.Parameters.AddWithValue("$name", clean);
        insert.Parameters.AddWithValue("$at", DateTime.UtcNow.ToString("O"));
        insert.ExecuteNonQuery();

        using var select = connection.CreateCommand();
        select.CommandText = "SELECT id FROM playlists WHERE name = $name COLLATE NOCASE";
        select.Parameters.AddWithValue("$name", clean);
        return Convert.ToInt64(select.ExecuteScalar());
    }

    public List<MusicPlaylistRow> QueryPlaylists()
    {
        var result = new List<MusicPlaylistRow>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT p.id, p.name, COUNT(t.id)
            FROM playlists p LEFT JOIN playlist_tracks t ON t.playlist_id = p.id
            GROUP BY p.id, p.name
            ORDER BY p.created_at, p.id";
        using var reader = command.ExecuteReader();
        while (reader.Read()) result.Add(new MusicPlaylistRow(reader.GetInt64(0), reader.GetString(1), (int)reader.GetInt64(2)));
        return result;
    }

    public void RenamePlaylist(long id, string name)
    {
        var clean = (name ?? string.Empty).Trim();
        if (clean.Length == 0) throw new ArgumentException("Пустое имя плейлиста", nameof(name));

        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "UPDATE playlists SET name = $name WHERE id = $id";
        command.Parameters.AddWithValue("$name", clean);
        command.Parameters.AddWithValue("$id", id);
        command.ExecuteNonQuery();
    }

    public void DeletePlaylist(long id)
    {
        using var connection = Open();
        using var deleteTracks = connection.CreateCommand();
        deleteTracks.CommandText = "DELETE FROM playlist_tracks WHERE playlist_id = $id";
        deleteTracks.Parameters.AddWithValue("$id", id);
        deleteTracks.ExecuteNonQuery();

        using var deleteList = connection.CreateCommand();
        deleteList.CommandText = "DELETE FROM playlists WHERE id = $id";
        deleteList.Parameters.AddWithValue("$id", id);
        deleteList.ExecuteNonQuery();
    }

    /// <summary>Appends a track unless it is already in the playlist; returns false when it was a duplicate.</summary>
    public bool AddPlaylistTrack(long playlistId, MusicPlaylistTrackRow track)
    {
        using var connection = Open();
        using var check = connection.CreateCommand();
        check.CommandText = "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = $id AND path = $path";
        check.Parameters.AddWithValue("$id", playlistId);
        check.Parameters.AddWithValue("$path", track.Path);
        if (Convert.ToInt64(check.ExecuteScalar()) > 0) return false;

        using var next = connection.CreateCommand();
        next.CommandText = "SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlist_id = $id";
        next.Parameters.AddWithValue("$id", playlistId);
        var position = Convert.ToInt32(next.ExecuteScalar());

        using var insert = connection.CreateCommand();
        insert.CommandText = @"
            INSERT INTO playlist_tracks (playlist_id, position, path, title, artist, category, is_stream)
            VALUES ($id, $position, $path, $title, $artist, $category, $stream)";
        insert.Parameters.AddWithValue("$id", playlistId);
        insert.Parameters.AddWithValue("$position", position);
        insert.Parameters.AddWithValue("$path", track.Path);
        insert.Parameters.AddWithValue("$title", track.Title);
        insert.Parameters.AddWithValue("$artist", track.Artist);
        insert.Parameters.AddWithValue("$category", track.Category);
        insert.Parameters.AddWithValue("$stream", track.IsStream ? 1 : 0);
        insert.ExecuteNonQuery();
        return true;
    }

    public void RemovePlaylistTrack(long playlistId, string path)
    {
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = "DELETE FROM playlist_tracks WHERE playlist_id = $id AND path = $path";
        command.Parameters.AddWithValue("$id", playlistId);
        command.Parameters.AddWithValue("$path", path);
        command.ExecuteNonQuery();
        NormalizePlaylistPositions(connection, playlistId);
    }

    public List<MusicPlaylistTrackRow> QueryPlaylistTracks(long playlistId)
    {
        var result = new List<MusicPlaylistTrackRow>();
        using var connection = Open();
        using var command = connection.CreateCommand();
        command.CommandText = @"
            SELECT id, playlist_id, position, path, title, artist, category, is_stream
            FROM playlist_tracks WHERE playlist_id = $id ORDER BY position, id";
        command.Parameters.AddWithValue("$id", playlistId);
        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            result.Add(new MusicPlaylistTrackRow(reader.GetInt64(0), reader.GetInt64(1), reader.GetInt32(2),
                reader.GetString(3), reader.GetString(4), reader.GetString(5), reader.GetString(6),
                reader.GetInt64(7) != 0));
        }

        return result;
    }

    /// <summary>Moves a track one slot up (-1) or down (+1) inside its playlist.</summary>
    public bool MovePlaylistTrack(long playlistId, string path, int delta)
    {
        var tracks = QueryPlaylistTracks(playlistId);
        var index = tracks.FindIndex(t => string.Equals(t.Path, path, StringComparison.OrdinalIgnoreCase));
        if (index < 0) return false;

        var moved = MoveItem(tracks, index, delta);
        if (moved == index) return false;

        using var connection = Open();
        using var transaction = connection.BeginTransaction();
        for (var i = 0; i < tracks.Count; i++)
        {
            using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = "UPDATE playlist_tracks SET position = $position WHERE id = $id";
            command.Parameters.AddWithValue("$position", i);
            command.Parameters.AddWithValue("$id", tracks[i].Id);
            command.ExecuteNonQuery();
        }

        transaction.Commit();
        return true;
    }

    /// <summary>Pure list re-ordering used by the playlists and by the self-test.</summary>
    public static int MoveItem<T>(IList<T> list, int index, int delta)
    {
        if (list is null || list.Count == 0) return 0;
        var target = Math.Clamp(index + delta, 0, list.Count - 1);
        if (target == index || index < 0 || index >= list.Count) return index;
        var item = list[index];
        list.RemoveAt(index);
        list.Insert(target, item);
        return target;
    }

    private static void NormalizePlaylistPositions(SqliteConnection connection, long playlistId)
    {
        using var select = connection.CreateCommand();
        select.CommandText = "SELECT id FROM playlist_tracks WHERE playlist_id = $id ORDER BY position, id";
        select.Parameters.AddWithValue("$id", playlistId);

        var ids = new List<long>();
        using (var reader = select.ExecuteReader())
        {
            while (reader.Read()) ids.Add(reader.GetInt64(0));
        }

        for (var i = 0; i < ids.Count; i++)
        {
            using var command = connection.CreateCommand();
            command.CommandText = "UPDATE playlist_tracks SET position = $position WHERE id = $id";
            command.Parameters.AddWithValue("$position", i);
            command.Parameters.AddWithValue("$id", ids[i]);
            command.ExecuteNonQuery();
        }
    }

    /// <summary>True when the path is already in the favourites list.</summary>
    public bool IsMusicFavorite(string path) => QueryMusicFavorites().Any(f => f.Path == path);
}
