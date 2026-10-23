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
    private readonly WebSearchService _web;
    private readonly SearchIndexService? _index;
    private readonly SearchCrawlerService? _crawler;

    private string _query = string.Empty;
    private string _lastQuery = string.Empty;
    private bool _localOnly;
    private bool _isSearching;
    private bool _webBusy;
    private string _webNote = string.Empty;
    private string _crawlUrl = "https://example.com/";
    private string _crawlStatus = "Краулер готов. Он работает только по явному запуску и соблюдает robots.txt.";
    private bool _crawlBusy;
    private long _indexDocumentCount;
    private DateTimeOffset? _indexUpdated;
    private CancellationTokenSource? _crawlCancellation;

    public SearchViewModel(
        Logger log,
        HistoryService history,
        BookmarkService bookmarks,
        AppSettings settings,
        Action<string> navigate,
        WebSearchService web,
        SearchIndexService? index = null,
        SearchCrawlerService? crawler = null)
    {
        _web = web;
        _index = index;
        _crawler = crawler;
        _log = log;
        _history = history;
        _bookmarks = bookmarks;
        _settings = settings;
        _navigate = navigate;
        _localOnly = settings.DrSearchLocalOnly;

        foreach (var recent in settings.DrSearchRecent) Recents.Add(recent);
        RefreshIndexStats();
    }

    public ObservableCollection<SearchResult> Results { get; } = new();

    /// <summary>Real web results fetched by DrSearch (rendered inside our own page).</summary>
    public ObservableCollection<WebSearchHit> WebResults { get; } = new();

    public string CrawlUrl { get => _crawlUrl; set => Set(ref _crawlUrl, value ?? string.Empty); }
    public string CrawlStatus { get => _crawlStatus; private set => Set(ref _crawlStatus, value); }
    public bool CrawlBusy
    {
        get => _crawlBusy;
        private set
        {
            if (!Set(ref _crawlBusy, value)) return;
            Raise(nameof(CrawlButtonText), nameof(CanStartCrawl), nameof(CanCancelCrawl));
        }
    }
    public string CrawlButtonText => CrawlBusy ? "Индексирование…" : "Добавить сайт в индекс";
    public bool CanStartCrawl => !CrawlBusy && _crawler is not null && _index?.IsAvailable == true;
    public bool CanCancelCrawl => CrawlBusy;
    public long IndexDocumentCount => _indexDocumentCount;
    public string IndexStatsText => _index?.IsAvailable != true
        ? "FTS5 недоступен" + (string.IsNullOrWhiteSpace(_index?.InitializationError) ? string.Empty : ": " + _index.InitializationError)
        : $"{IndexDocumentCount:N0} документов в собственном индексе";
    public string IndexUpdatedText => _indexUpdated is null
        ? "Индекс ещё не обновлялся"
        : "Обновлён " + _indexUpdated.Value.ToLocalTime().ToString("dd.MM.yyyy HH:mm");
    public string CrawlerLimitsText => $"Лимит: {_settings.SearchCrawlerMaxPages} стр. · глубина {_settings.SearchCrawlerMaxDepth} · задержка {_settings.SearchCrawlerDelayMs} мс";

    public ICommand StartCrawlCommand => new RelayCommand(async () => await StartCrawlAsync());
    public ICommand CancelCrawlCommand => new RelayCommand(() => _crawlCancellation?.Cancel());

    public bool WebBusy
    {
        get => _webBusy;
        private set
        {
            if (!Set(ref _webBusy, value)) return;
            Raise(nameof(WebButtonText));
        }
    }

    public string WebButtonText => WebBusy ? "Ищу в интернете…" : "Искать в интернете";

    public string WebNote
    {
        get => _webNote;
        private set => Set(ref _webNote, value);
    }

    public bool HasWebResults => WebResults.Count > 0;
    public bool WebSearchEnabled
    {
        get => _settings.DrSearchWebEnabled;
        set
        {
            if (_settings.DrSearchWebEnabled == value) return;
            _settings.DrSearchWebEnabled = value;
            Raise(nameof(WebSearchEnabled), nameof(PrivacyText));
        }
    }

    public ICommand SearchWebNowCommand => new RelayCommand(async () =>
    {
        var query = string.IsNullOrWhiteSpace(Query) ? LastQuery : Query;
        if (string.IsNullOrWhiteSpace(query)) return;
        await RunWebAsync(query);
    });

    public ICommand OpenWebResultCommand => new RelayCommand(p =>
    {
        if (p is WebSearchHit hit && !string.IsNullOrWhiteSpace(hit.Url)) _navigate(hit.Url);
    });

    /// <summary>Fetches web results for the current query (no-op in local-only mode).</summary>
    public async Task RunWebAsync(string query)
    {
        if (LocalOnly || !_settings.DrSearchWebEnabled) return;
        if (string.IsNullOrWhiteSpace(query)) return;

        WebBusy = true;
        WebNote = "DrSearch запрашивает открытый веб-индекс…";
        try
        {
            var outcome = await _web.SearchAsync(query).ConfigureAwait(true);
            WebResults.Clear();
            foreach (var hit in outcome.Hits) WebResults.Add(hit);
            WebNote = outcome.Note;

            // A "!g query" bang is handed to the chosen engine instead of showing results.
            if (!outcome.FromWeb && outcome.UsedQuery.Length > 0 && outcome.Note.Contains("оператором", StringComparison.Ordinal))
            {
                var engine = outcome.Note.Replace("Запрос с оператором ", string.Empty).Split(':')[0].Trim();
                var template = AppSettings.SearchEngines.FirstOrDefault(e => e.Name == engine).Template;
                if (!string.IsNullOrWhiteSpace(template) && !template.StartsWith(UrlParser.InternalScheme, StringComparison.OrdinalIgnoreCase))
                    _navigate(string.Format(template, Uri.EscapeDataString(outcome.UsedQuery)));
            }
        }
        catch (Exception ex)
        {
            WebNote = "Ошибка веб-поиска: " + ex.Message;
        }
        finally
        {
            WebBusy = false;
            Raise(nameof(HasWebResults), nameof(Summary));
        }
    }

    /// <summary>Last queries kept locally (max 8) — shown as chips on the DrSearch page.</summary>
    public ObservableCollection<string> Recents { get; } = new();

    public bool HasRecents => Recents.Count > 0;

    public ICommand UseRecentCommand => new RelayCommand(p =>
    {
        if (p is not string query) return;
        Query = query;
        Run(query);
    });

    public ICommand ClearRecentsCommand => new RelayCommand(() =>
    {
        Recents.Clear();
        _settings.DrSearchRecent = new List<string>();
        Raise(nameof(HasRecents));
    });

    public static IReadOnlyList<string> WebEngines { get; } = new[] { "Google", "Яндекс", "Bing", "DuckDuckGo" };

    public string Query
    {
        get => _query;
        set
        {
            if (!Set(ref _query, value ?? string.Empty)) return;
            Raise(nameof(HasQuery));
        }
    }

    public bool HasQuery => !string.IsNullOrEmpty(_query);

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
        : _settings.DrSearchWebEnabled
            ? "DrSearch ищет локально и в открытом веб-индексе; история запросов остаётся на этом компьютере"
            : "DrSearch ищет только по вашим данным — веб-поиск выключен";

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
        WebResults.Clear();
        WebNote = string.Empty;
        Raise(nameof(Summary), nameof(HasWebResults));

        if (LastQuery.Length == 0) return;

        _ = RunWebAsync(LastQuery);

        IsSearching = true;
        try
        {
            var needle = LastQuery;

            // 1. DrSearch's own SQLite FTS5 corpus. bm25() ranking happens inside
            // SQLite; the snippet contains ⟦highlighted⟧ terms from the indexed page.
            if (_index?.IsAvailable == true)
            {
                foreach (var hit in _index.Search(needle, 20))
                {
                    Results.Add(new SearchResult
                    {
                        Title = hit.Title,
                        Url = hit.Url,
                        Subtitle = string.IsNullOrWhiteSpace(hit.Snippet) ? hit.Url : hit.Snippet,
                        Glyph = "◎",
                        Badge = $"DRSEARCH · {hit.Language.ToUpperInvariant()}",
                        IsLocal = true
                    });
                }
            }

            // 2. Bookmarks (high value: the user saved them on purpose).
            foreach (var bookmark in _bookmarks.Items
                         .Where(b => Matches(b.Title, needle) || Matches(b.Url, needle))
                         .Take(8))
            {
                if (Results.Any(r => r.Url.Equals(bookmark.Url, StringComparison.OrdinalIgnoreCase))) continue;
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
            AddRecent(LastQuery);
            Raise(nameof(Summary));
            _log.Info($"DrSearch: \"{LastQuery}\" → {Results.Count} local results");
        }
    }

    private async Task StartCrawlAsync()
    {
        if (!CanStartCrawl || _crawler is null) return;
        if (!Uri.TryCreate(CrawlUrl.Trim(), UriKind.Absolute, out var uri) || uri.Scheme is not ("http" or "https"))
        {
            CrawlStatus = "Нужен полный адрес вида https://example.com/.";
            return;
        }

        _crawlCancellation?.Dispose();
        _crawlCancellation = new CancellationTokenSource();
        CrawlBusy = true;
        CrawlStatus = "Проверяю robots.txt…";
        try
        {
            var options = new CrawlOptions(
                _settings.SearchCrawlerMaxPages,
                _settings.SearchCrawlerMaxDepth,
                _settings.SearchCrawlerDelayMs,
                _settings.SearchAllowedDomains,
                _settings.SearchBlockedDomains);
            var progress = new Progress<CrawlProgress>(value =>
            {
                CrawlStatus = $"{value.Status} · просмотрено {value.Visited}, в индексе {value.Indexed}, очередь {value.Queued}\n{value.Url}";
            });
            var report = await _crawler.CrawlAsync(uri.ToString(), options, progress, _crawlCancellation.Token).ConfigureAwait(true);
            CrawlStatus = $"Готово: {report.Indexed} добавлено, {report.Skipped} пропущено, {report.Failed} ошибок · {report.Elapsed.TotalSeconds:0.0} с";
            RefreshIndexStats();
        }
        catch (OperationCanceledException)
        {
            CrawlStatus = "Индексирование остановлено пользователем.";
        }
        catch (Exception ex)
        {
            _log.Error("DrSearch crawl failed", ex);
            CrawlStatus = "Краулер: " + ex.Message;
        }
        finally
        {
            CrawlBusy = false;
        }
    }

    public void RefreshIndexStats()
    {
        try
        {
            var stats = _index?.GetStats() ?? new SearchIndexStats(0, null, 0);
            _indexDocumentCount = stats.DocumentCount;
            _indexUpdated = stats.LastUpdated;
        }
        catch (Exception ex)
        {
            _log.Warn("DrSearch stats failed: " + ex.Message);
            _indexDocumentCount = 0;
            _indexUpdated = null;
        }
        Raise(nameof(IndexDocumentCount), nameof(IndexStatsText), nameof(IndexUpdatedText), nameof(CanStartCrawl), nameof(CrawlerLimitsText));
    }

    private void AddRecent(string query)
    {
        if (string.IsNullOrWhiteSpace(query)) return;

        var existing = Recents.FirstOrDefault(r => string.Equals(r, query, StringComparison.CurrentCultureIgnoreCase));
        if (existing is not null) Recents.Remove(existing);

        Recents.Insert(0, query);
        while (Recents.Count > 8) Recents.RemoveAt(Recents.Count - 1);

        _settings.DrSearchRecent = Recents.ToList();
        Raise(nameof(HasRecents));
    }

    private static bool Matches(string? value, string needle)
        => !string.IsNullOrEmpty(value) && value.Contains(needle, StringComparison.CurrentCultureIgnoreCase);
}
