using System.Collections.ObjectModel;
using System.IO;
using System.Text;
using System.Text.RegularExpressions;

namespace DrBrowser;

/// <summary>Bookmarks in SQLite with folders, search, import and export.</summary>
public sealed partial class BookmarkService
{
    private readonly Db _db;
    private readonly Logger _log;

    public BookmarkService(Db db, Logger log)
    {
        _db = db;
        _log = log;
    }

    public ObservableCollection<Bookmark> Items { get; } = new();
    public ObservableCollection<string> Folders { get; } = new();
    public ObservableCollection<Bookmark> BarItems { get; } = new();

    public async Task LoadAsync()
    {
        List<Bookmark> rows;
        try
        {
            rows = await Task.Run(_db.QueryBookmarks).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _log.Error("bookmark read failed", ex);
            rows = new List<Bookmark>();
        }

        Items.Clear();
        foreach (var row in rows) Items.Add(row);
        RefreshDerived();
    }

    private void RefreshDerived()
    {
        var folders = Items.Select(b => b.Folder).Where(f => !string.IsNullOrWhiteSpace(f)).Distinct().OrderBy(f => f).ToList();
        if (!folders.Contains("Закладки")) folders.Insert(0, "Закладки");

        Folders.Clear();
        foreach (var folder in folders) Folders.Add(folder);

        BarItems.Clear();
        foreach (var item in Items.Take(14)) BarItems.Add(item);
    }

    public async Task<Bookmark> AddAsync(string title, string url, string folder = "Закладки")
    {
        var bookmark = new Bookmark
        {
            Title = string.IsNullOrWhiteSpace(title) ? UrlParser.FallbackTitle(url) : title.Trim(),
            Url = url,
            Folder = string.IsNullOrWhiteSpace(folder) ? "Закладки" : folder.Trim(),
            CreatedAt = DateTime.Now
        };

        bookmark.Id = await Task.Run(() => _db.AddBookmark(bookmark)).ConfigureAwait(false);
        Items.Add(bookmark);
        RefreshDerived();
        return bookmark;
    }

    public async Task UpdateAsync(Bookmark bookmark)
    {
        await Task.Run(() => _db.UpdateBookmark(bookmark)).ConfigureAwait(false);
        RefreshDerived();
    }

    public async Task DeleteAsync(Bookmark bookmark)
    {
        await Task.Run(() => _db.DeleteBookmark(bookmark.Id)).ConfigureAwait(false);
        Items.Remove(bookmark);
        RefreshDerived();
    }

    public IEnumerable<Bookmark> Search(string? query)
    {
        if (string.IsNullOrWhiteSpace(query)) return Items;
        var text = query.Trim();
        return Items.Where(b =>
            b.Title.Contains(text, StringComparison.OrdinalIgnoreCase) ||
            b.Url.Contains(text, StringComparison.OrdinalIgnoreCase) ||
            b.Folder.Contains(text, StringComparison.OrdinalIgnoreCase));
    }

    public List<string> Suggest(string prefix, int limit = 5)
    {
        var text = prefix?.Trim() ?? string.Empty;
        if (text.Length < 2) return new List<string>();
        return Items
            .Where(b => b.Title.Contains(text, StringComparison.OrdinalIgnoreCase) || b.Url.Contains(text, StringComparison.OrdinalIgnoreCase))
            .Select(b => $"{b.Title} — {b.Url}")
            .Take(limit)
            .ToList();
    }

    /// <summary>Exports Netscape bookmark HTML (importable by Chrome, Edge, Firefox).</summary>
    public async Task ExportHtmlAsync(string path)
    {
        var sb = new StringBuilder();
        sb.AppendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>");
        sb.AppendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">");
        sb.AppendLine("<TITLE>Bookmarks</TITLE><H1>Bookmarks</H1><DL><p>");

        foreach (var group in Items.GroupBy(b => b.Folder))
        {
            sb.AppendLine($"    <DT><H3>{System.Net.WebUtility.HtmlEncode(group.Key)}</H3>");
            sb.AppendLine("    <DL><p>");
            foreach (var bookmark in group)
            {
                var add = new DateTimeOffset(bookmark.CreatedAt).ToUnixTimeSeconds();
                sb.AppendLine($"        <DT><A HREF=\"{System.Net.WebUtility.HtmlEncode(bookmark.Url)}\" ADD_DATE=\"{add}\">{System.Net.WebUtility.HtmlEncode(bookmark.Title)}</A>");
            }
            sb.AppendLine("    </DL><p>");
        }

        sb.AppendLine("</DL><p>");
        await File.WriteAllTextAsync(path, sb.ToString(), Encoding.UTF8).ConfigureAwait(false);
    }

    [GeneratedRegex("<A\\s+[^>]*HREF=\"(?<url>[^\"]+)\"[^>]*>(?<title>.*?)</A>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex AnchorRegex();

    [GeneratedRegex("<H3[^>]*>(?<folder>.*?)</H3>", RegexOptions.IgnoreCase | RegexOptions.Singleline)]
    private static partial Regex FolderRegex();

    /// <summary>Imports an HTML bookmarks file; folders are preserved.</summary>
    public async Task<int> ImportHtmlAsync(string path)
    {
        var html = await File.ReadAllTextAsync(path).ConfigureAwait(false);
        var folder = Path.GetFileNameWithoutExtension(path);
        var imported = 0;

        foreach (var folderMatch in FolderRegex().Matches(html).Cast<Match>())
        {
            var name = System.Net.WebUtility.HtmlDecode(folderMatch.Groups["folder"].Value).Trim();
            if (!string.IsNullOrWhiteSpace(name)) folder = name;
        }

        foreach (var match in AnchorRegex().Matches(html).Cast<Match>())
        {
            var url = System.Net.WebUtility.HtmlDecode(match.Groups["url"].Value).Trim();
            if (!Uri.TryCreate(url, UriKind.Absolute, out var uri)) continue;
            if (uri.Scheme is not ("http" or "https")) continue;

            var title = System.Net.WebUtility.HtmlDecode(StripTags(match.Groups["title"].Value)).Trim();
            if (Items.Any(b => b.Url == url)) continue;

            await AddAsync(string.IsNullOrWhiteSpace(title) ? uri.Host : title, url, folder).ConfigureAwait(false);
            imported++;
        }

        return imported;
    }

    private static string StripTags(string value) => Regex.Replace(value, "<.*?>", string.Empty).Trim();
}
