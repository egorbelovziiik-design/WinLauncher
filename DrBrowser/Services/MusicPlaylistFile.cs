using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;

namespace DrBrowser;

/// <summary>One entry of an M3U/M3U8 playlist file.</summary>
public sealed record MusicPlaylistEntry(string Location, string Title, string Artist)
{
    public bool IsStream => MusicPlaylistFile.IsStreamLocation(Location);
    public string DisplayTitle => string.IsNullOrWhiteSpace(Title) ? MusicPlaylistFile.FileNameOf(Location) : Title;
}

/// <summary>
/// Reading and writing of M3U/M3U8 playlist files (the format VLC, foobar2000 and AIMP understand).
/// Pure text logic — no file dialogs — so the self-test can exercise it directly.
/// </summary>
public static class MusicPlaylistFile
{
    public const string Filter = "M3U-плейлист|*.m3u;*.m3u8|Все файлы|*.*";

    /// <summary>Builds an UTF-8 M3U file body: header, one `#EXTINF` line and one path per track.</summary>
    public static string Build(IEnumerable<MusicTrack> tracks)
    {
        var builder = new StringBuilder("#EXTM3U");
        builder.Append('\n');

        foreach (var track in tracks ?? Enumerable.Empty<MusicTrack>())
        {
            if (string.IsNullOrWhiteSpace(track.FilePath)) continue;

            // Duration is unknown for streams and for files we did not decode, hence -1.
            var artist = string.IsNullOrWhiteSpace(track.Artist) ? "DrMusic" : track.Artist;
            var title = string.IsNullOrWhiteSpace(track.Title) ? FileNameOf(track.FilePath) : track.Title;
            builder.Append("#EXTINF:-1,").Append(artist).Append(" - ").Append(title).Append('\n');
            builder.Append(track.FilePath).Append('\n');
        }

        return builder.ToString();
    }

    /// <summary>
    /// Parses M3U/M3U8 text. Comments, blank lines and unknown tags are skipped; relative paths
    /// are resolved against <paramref name="baseDirectory"/> so an imported file keeps working.
    /// </summary>
    public static List<MusicPlaylistEntry> Parse(string? content, string? baseDirectory = null)
    {
        var result = new List<MusicPlaylistEntry>();
        if (string.IsNullOrWhiteSpace(content)) return result;

        string? pendingTitle = null;
        string? pendingArtist = null;

        foreach (var rawLine in content.Replace("\r\n", "\n").Replace('\r', '\n').Split('\n'))
        {
            var line = rawLine.Trim().TrimStart('\uFEFF');
            if (line.Length == 0) continue;

            if (line.StartsWith("#EXTINF:", StringComparison.OrdinalIgnoreCase))
            {
                (pendingArtist, pendingTitle) = ParseExtendedInfo(line);
                continue;
            }

            if (line.StartsWith("#", StringComparison.Ordinal)) continue; // #EXTM3U, #PLAYLIST, comments

            var location = ResolveLocation(line, baseDirectory);
            if (location.Length == 0) continue;

            result.Add(new MusicPlaylistEntry(location, pendingTitle ?? string.Empty, pendingArtist ?? string.Empty));
            pendingTitle = null;
            pendingArtist = null;
        }

        return result;
    }

    /// <summary>Reads a playlist file from disk (UTF-8, BOM or not — older files are usually ANSI).</summary>
    public static List<MusicPlaylistEntry> ReadFile(string path)
    {
        if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return new List<MusicPlaylistEntry>();
        var directory = Path.GetDirectoryName(path);
        var content = File.ReadAllText(path, Encoding.UTF8);
        return Parse(content, directory);
    }

    /// <summary>Turns a playlist entry into a track the DrMusic library can play.</summary>
    public static MusicTrack ToTrack(MusicPlaylistEntry entry, string category = "Импорт M3U") => new()
    {
        FilePath = entry.Location,
        Title = entry.DisplayTitle,
        Artist = entry.Artist,
        Category = category,
        IsStream = entry.IsStream
    };

    /// <summary>Filename without extension, used when `#EXTINF` carries no title.</summary>
    public static string FileNameOf(string location)
    {
        if (string.IsNullOrWhiteSpace(location)) return "Без названия";
        if (IsStreamLocation(location))
        {
            var withoutScheme = location[(location.IndexOf("://", StringComparison.Ordinal) + 3)..];
            return withoutScheme.Length == 0 ? location : withoutScheme;
        }

        var name = Path.GetFileName(location);
        return string.IsNullOrWhiteSpace(name) ? location : name;
    }

    public static bool IsStreamLocation(string location)
        => location.StartsWith("http://", StringComparison.OrdinalIgnoreCase)
           || location.StartsWith("https://", StringComparison.OrdinalIgnoreCase);

    /// <summary>
    /// Resolves a location from an M3U line: absolute paths and URLs stay as-is,
    /// relative paths are combined with the folder of the playlist file.
    /// </summary>
    public static string ResolveLocation(string raw, string? baseDirectory)
    {
        var location = (raw ?? string.Empty).Trim();
        if (location.Length == 0) return string.Empty;
        if (IsStreamLocation(location)) return location;

        // Some players write file:// URLs.
        if (location.StartsWith("file://", StringComparison.OrdinalIgnoreCase))
        {
            var pathPart = Uri.UnescapeDataString(location["file:///".Length..]);
            return pathPart.Length > 0 ? pathPart.Replace('/', '\\') : string.Empty;
        }

        if (string.IsNullOrWhiteSpace(baseDirectory)) return location;
        if (Path.IsPathRooted(location)) return location;

        try
        {
            return Path.GetFullPath(Path.Combine(baseDirectory, location));
        }
        catch (Exception)
        {
            return location;
        }
    }

    /// <summary>Parses `#EXTINF:123,Artist - Title` into (artist, title).</summary>
    public static (string Artist, string Title) ParseExtendedInfo(string line)
    {
        var comma = line.IndexOf(',');
        if (comma < 0 || comma == line.Length - 1) return (string.Empty, string.Empty);

        var text = line[(comma + 1)..].Trim();
        if (text.Length == 0) return (string.Empty, string.Empty);

        var dash = text.IndexOf(" - ", StringComparison.Ordinal);
        if (dash <= 0) return (string.Empty, text);

        var artist = text[..dash].Trim();
        var title = text[(dash + 3)..].Trim();
        return (artist, title.Length == 0 ? text : title);
    }

    /// <summary>Default file name for "export playlist" (illegal characters stripped).</summary>
    public static string SuggestFileName(string playlistName)
    {
        var invalid = Path.GetInvalidFileNameChars();
        var clean = new string((playlistName ?? string.Empty)
            .Select(ch => char.IsControl(ch) || Array.IndexOf(invalid, ch) >= 0 ? '_' : ch)
            .ToArray()).Trim();
        if (clean.Length == 0) clean = "playlist";
        return clean + ".m3u8";
    }
}
