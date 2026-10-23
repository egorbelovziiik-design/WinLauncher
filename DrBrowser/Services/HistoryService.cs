using System.Collections.ObjectModel;

namespace DrBrowser;

/// <summary>History facade on top of SQLite, safe to call from the UI thread.</summary>
public sealed class HistoryService
{
    private readonly Db _db;
    private readonly Logger _log;

    public HistoryService(Db db, Logger log)
    {
        _db = db;
        _log = log;
    }

    public ObservableCollection<HistoryEntry> Entries { get; } = new();
    public ObservableCollection<VisitedSite> MostVisited { get; } = new();

    public async Task RecordAsync(string url, string title, bool enabled)
    {
        if (!enabled) return;
        if (string.IsNullOrWhiteSpace(url) || UrlParser.IsInternal(url)) return;

        var host = UrlParser.HostOf(url);
        try
        {
            await Task.Run(() => _db.AddHistory(url, title, host, DateTime.Now)).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _log.Error("history write failed", ex);
        }
    }

    public async Task LoadAsync(string? search = null, DateTime? from = null, DateTime? to = null)
    {
        List<HistoryEntry> rows;
        List<VisitedSite> top;
        try
        {
            (rows, top) = await Task.Run(() =>
            {
                var history = _db.QueryHistory(search, from, to);
                var visited = _db.QueryMostVisited();
                return (history, visited);
            }).ConfigureAwait(false);
        }
        catch (Exception ex)
        {
            _log.Error("history read failed", ex);
            rows = new List<HistoryEntry>();
            top = new List<VisitedSite>();
        }

        Entries.Clear();
        foreach (var row in rows) Entries.Add(row);

        MostVisited.Clear();
        foreach (var site in top) MostVisited.Add(site);
    }

    public async Task DeleteAsync(long id)
    {
        await Task.Run(() => _db.DeleteHistory(id)).ConfigureAwait(false);
        var entry = Entries.FirstOrDefault(e => e.Id == id);
        if (entry is not null) Entries.Remove(entry);
    }

    public async Task ClearAsync()
    {
        await Task.Run(_db.ClearHistory).ConfigureAwait(false);
        Entries.Clear();
        MostVisited.Clear();
    }

    /// <summary>Suggestions for the address bar (history titles + urls).</summary>
    public List<string> Suggest(string prefix, int limit = 6)
    {
        var text = prefix?.Trim() ?? string.Empty;
        if (text.Length < 2) return new List<string>();

        try
        {
            return _db.QueryHistory(text, null, null, limit)
                .Select(e => string.IsNullOrWhiteSpace(e.Title) ? e.Url : $"{e.Title} — {e.Url}")
                .Distinct()
                .Take(limit)
                .ToList();
        }
        catch
        {
            return new List<string>();
        }
    }
}
