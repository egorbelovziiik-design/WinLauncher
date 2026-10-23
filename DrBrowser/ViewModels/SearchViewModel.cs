using System.Collections.ObjectModel;
using System.Windows.Input;

namespace DrBrowser;

/// <summary>One row of DrSearch results.</summary>
public sealed class SearchResult
{
    public string Title { get; init; } = string.Empty;
    public string Url { get; init; } = string.Empty;
    public string Subtitle { get; init; } = string.Empty;
    public string Glyph { get; init; } = "🌐";
    public string Badge { get; init; } = string.Empty;
    public bool IsLocal { get; init; }
}

/// <summary>
/// DrSearch — the browser's own search page. It ranks the user's own data (history,
/// bookmarks, quick links) locally and offers one-click hand-off to a web engine.
/// Nothing leaves the machine unless the user clicks a web engine chip.
/// </summary>
public sealed class SearchViewModel : ObservableObject
{
    private readonly Logger _log;
    private readonly HistoryService _history;
    private readonly BookmarkService _bookmarks;
    private readonly AppSettings _settings;
    private readonly Action<string> _navigate;

    private string _query = string.Empty;
    private string _lastQuery = string.Empty;
    private bool _localOnly;
    private bool _isSearching;

    public SearchViewModel(
        Logger log,
        HistoryService history,
        BookmarkService bookmarks,
        AppSettings settings,
        Action<string> navigate)
    {
        _log = log;
        _history = history;
        _bookmarks = bookmarks;
        _settings = settings;
        _navigate = navigate;
        _localOnly = settings.DrSearchLocalOnly;
    }

    public ObservableCollection<SearchResult> Results { get; } = new();

    public static IReadOnlyList<string> WebEngines { get; } = new[] { "Google", "Яндекс", "Bing", "DuckDuckGo" };

    public string Query
    {
        get => _query;
        set => Set(ref _query, value ?? string.Empty);
    }

    public string LastQuery
    {
        get => _lastQuery;
        private set => Set(ref _lastQuery, value);
    }

    public bool LocalOnly
    {
        get => _localOnly;
        set
        {
            if (!Set(ref _localOnly, value)) return;
            _settings.DrSearchLocalOnly = value;
            Raise(nameof(PrivacyText));
        }
    }

    public string PrivacyText => LocalOnly
        ? "Приватный режим: запросы не отправляются в сеть, ищем только по вашим данным"
        : "DrSearch ищет по истории, закладкам и быстрым ссылкам — сеть используется только по кнопке движка";

    public bool IsSearching
    {
        get => _isSearching;
        private set => Set(ref _isSearching, value);
    }

    public string Summary
    {
        get
        {
            if (string.IsNullOrWhiteSpace(LastQuery)) return "Введите запрос — DrSearch ищет локально, без трекеров";
            return Results.Count == 0
                ? $"По запросу «{LastQuery}» ничего не найдено в ваших данных"
                : $"{Results.Count} результатов по запросу «{LastQuery}» · {Results.Count(r => r.IsLocal)} из ваших данных";
        }
    }

    public string EngineHint => $"Текущий движок: {_settings.SearchEngine}";

    public ICommand SearchCommand => new RelayCommand(() => Run(Query));
    public ICommand ClearCommand => new RelayCommand(() =>
    {
        Results.Clear();
        LastQuery = string.Empty;
        Query = string.Empty;
        Raise(nameof(Summary));
    });

    public ICommand OpenResultCommand => new RelayCommand(p =>
    {
        if (p is SearchResult result && !string.IsNullOrWhiteSpace(result.Url)) _navigate(result.Url);
    });

    /// <summary>Hands the current query to a real web engine (explicit user action).</summary>
    public ICommand SearchWebCommand => new RelayCommand(async p =>
    {
        var query = string.IsNullOrWhiteSpace(Query) ? LastQuery : Query;
        if (string.IsNullOrWhiteSpace(query)) return;

        var engine = p as string ?? "Google";
        var template = AppSettings.SearchEngines.FirstOrDefault(e => e.Name == engine).Template
                       ?? AppSettings.SearchEngines[0].Template;

        if (template.StartsWith(UrlParser.InternalScheme, StringComparison.OrdinalIgnoreCase)) return;

        _navigate(string.Format(template, Uri.EscapeDataString(query)));
        await Task.CompletedTask;
    });

    public Task RunAsync(string query)
    {
        Run(query);
        return Task.CompletedTask;
    }

    public void Run(string query)
    {
        LastQuery = query?.Trim() ?? string.Empty;
        Results.Clear();
        Raise(nameof(Summary));

        if (LastQuery.Length == 0) return;

        IsSearching = true;
        try
        {
            var needle = LastQuery;

            // 1. Bookmarks (highest value: the user saved them on purpose).
            foreach (var bookmark in _bookmarks.Items
                         .Where(b => Matches(b.Title, needle) || Matches(b.Url, needle))
                         .Take(8))
            {
                Results.Add(new SearchResult
                {
                    Title = bookmark.Title,
                    Url = bookmark.Url,
                    Subtitle = bookmark.Url,
                    Glyph = "★",
                    Badge = "ЗАКЛАДКА",
                    IsLocal = true
                });
            }

            // 2. History rows.
            foreach (var line in _history.Suggest(needle, 10))
            {
                var split = line.Split(" — ", 2);
                var title = split.Length == 2 ? split[0] : line;
                var url = split.Length == 2 ? split[1] : line;

                if (Results.Any(r => r.Url.Equals(url, StringComparison.OrdinalIgnoreCase))) continue;

                Results.Add(new SearchResult
                {
                    Title = title,
                    Url = url,
                    Subtitle = url,
                    Glyph = "🕘",
                    Badge = "ИСТОРИЯ",
                    IsLocal = true
                });
            }

            // 3. Direct navigation / known hosts.
            if (UrlParser.LooksLikeUrl(needle))
            {
                var url = needle.Contains("://") ? needle : "https://" + needle;
                Results.Insert(0, new SearchResult
                {
                    Title = "Открыть " + url,
                    Url = url,
                    Subtitle = "Прямой переход по адресу",
                    Glyph = "↗",
                    Badge = "АДРЕС",
                    IsLocal = true
                });
            }
        }
        catch (Exception ex)
        {
            _log.Error("DrSearch query failed", ex);
            Results.Add(new SearchResult
            {
                Title = "Ошибка поиска",
                Url = string.Empty,
                Subtitle = ex.Message,
                Glyph = "⚠"
            });
        }
        finally
        {
            IsSearching = false;
            Raise(nameof(Summary));
            _log.Info($"DrSearch: \"{LastQuery}\" → {Results.Count} local results");
        }
    }

    private static bool Matches(string? value, string needle)
        => !string.IsNullOrEmpty(value) && value.Contains(needle, StringComparison.CurrentCultureIgnoreCase);
}
