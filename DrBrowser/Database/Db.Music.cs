using System;
using System.Collections.Generic;
using System.Linq;

namespace DrBrowser;

/// <summary>Read-only view of one row of the DrMusic favourites table.</summary>
public sealed record MusicFavoriteRow(string Path, string Title, string Artist, string Category, bool IsStream);

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

    /// <summary>True when the path is already in the favourites list.</summary>
    public bool IsMusicFavorite(string path) => QueryMusicFavorites().Any(f => f.Path == path);
}
