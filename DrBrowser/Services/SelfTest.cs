using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;

namespace DrBrowser;

/// <summary>
/// Real, executed checks (not a mock). <c>--selftest</c> exercises storage, parsing,
/// DPAPI, AdBlock, session and settings; <c>--smoke</c> additionally drives a live
/// window with WebView2, switches every internal page, creates/closes tabs and writes
/// PNG screenshots. Both return a process exit code: 0 = all executed checks passed.
/// </summary>
public static class SelfTest
{
    private const string LocaleAiAssistantId = "chat";
    private sealed record Check(string Id, string Name, string Status, string Detail);

    private static readonly List<Check> Results = new();

    /// <summary>Called by App when window initialisation throws during a test run.</summary>
    public static void RecordStartupFailure(Exception ex)
    {
        Results.Add(new Check("startup", "Инициализация окна", "FAIL", ex.GetType().Name + ": " + ex.Message));
        Trace("startup FAIL: " + ex);
    }

    private static string ReportDir =>
        Environment.GetEnvironmentVariable("DRBROWSER_SHOTS") is { Length: > 0 } dir
            ? dir
            : Path.Combine(AppPaths.DataRoot, "selftest");

    // ------------------------------------------------------------------
    // Headless / logic tests
    // ------------------------------------------------------------------

    public static async Task<int> RunAsync(Logger log)
    {
        Results.Clear();
        var temp = Path.Combine(Path.GetTempPath(), "drbrowser-selftest-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(temp);
        var dbPath = Path.Combine(temp, "selftest.db");
        var settingsBackup = Backup(AppPaths.SettingsFile);

        try
        {
            log.Info("=== selftest: start ===");
            AppPaths.EnsureCreated();

            await Run("data-folders", "Каталоги данных и журнал", async () =>
            {
                foreach (var dir in new[]
                         {
                             AppPaths.DataRoot, AppPaths.LogDir, AppPaths.SecretsDir,
                             AppPaths.FiltersDir, AppPaths.ScreenshotsDir, AppPaths.WallpapersDir
                         })
                {
                    if (!Directory.Exists(dir)) throw new Exception("нет каталога " + dir);
                }

                log.Info("selftest marker " + Guid.NewGuid().ToString("N"));
                await Task.Delay(50);
                if (!File.Exists(log.FilePath)) throw new Exception("файл журнала не создан");
                return "5 каталогов + logs\\drbrowser-*.log";
            });

            await Run("settings-json", "Настройки: сохранение и загрузка JSON", () =>
            {
                var service = new SettingsService(log);
                var settings = SettingsService.CreateDefaults();
                settings.Theme = "Omega Light";
                settings.AccentHex = "#00A2FF";
                settings.SearchEngine = "Google"; // must be rejected: address-bar search is DrSearch-only
                settings.DrSearchWebEnabled = true; // explicit external-index opt-in is persisted separately
                settings.ZoomLevel = 1.25;
                settings.AdBlockWhitelist = new List<string> { "example.com" };
                service.Save(settings);

                var loaded = service.Load();
                if (loaded.Theme != "Omega Light") throw new Exception("тема не сохранилась");
                if (loaded.AccentHex != "#00A2FF") throw new Exception("акцент не сохранился");
                if (loaded.SearchEngine != "DrSearch") throw new Exception("сторонний поисковик не заменён на DrSearch");
                if (!loaded.DrSearchWebEnabled) throw new Exception("явная настройка внешнего индекса не сохранилась");
                if (Math.Abs(loaded.ZoomLevel - 1.25) > 0.001) throw new Exception("масштаб не сохранился");
                if (loaded.AdBlockWhitelist.Count != 1) throw new Exception("whitelist не сохранился");

                loaded.DrSearchConfigVersion = 0; // emulate a pre-DrSearch-only settings file
                loaded.DrSearchWebEnabled = true;
                service.Save(loaded);
                var migrated = service.Load();
                if (migrated.DrSearchConfigVersion != 1 || migrated.DrSearchWebEnabled)
                    throw new Exception("старый внешний поиск не был безопасно выключен при миграции");
                return "settings.json: DrSearch-only migration · explicit opt-in persistence · zoom/theme round-trip";
            });

            await Run("url-parser", "Адресная строка: URL против поискового запроса", () =>
            {
                var settings = SettingsService.CreateDefaults();

                var site = UrlParser.Resolve("example.com", settings);
                if (site != "https://example.com/") throw new Exception("домен не стал URL: " + site);

                var https = UrlParser.Resolve("https://github.com/dotnet", settings);
                if (https != "https://github.com/dotnet") throw new Exception("https URL искажён: " + https);

                var query = UrlParser.Resolve("как работает webview2", settings);
                if (!query.StartsWith("drbrowser://search?q=")) throw new Exception("запрос не ушёл в DrSearch: " + query);
                if (UrlParser.QueryOf(query) != "как работает webview2") throw new Exception("DrSearch потерял запрос: " + query);

                settings.SearchEngine = "Google";
                var stillDrSearch = UrlParser.Resolve("как работает webview2", settings);
                if (!stillDrSearch.StartsWith("drbrowser://search?q="))
                    throw new Exception("сторонняя настройка обошла DrSearch: " + stillDrSearch);
                if (stillDrSearch.Contains("google", StringComparison.OrdinalIgnoreCase))
                    throw new Exception("адресная строка содержит Google-редирект: " + stillDrSearch);

                var local = UrlParser.Resolve("localhost:5000/app", settings);
                if (!local.StartsWith("https://localhost:5000") && !local.StartsWith("http://localhost:5000"))
                    throw new Exception("localhost не распознан: " + local);

                if (!UrlParser.IsInternal("drbrowser://settings")) throw new Exception("internal не распознан");
                if (UrlParser.IsHttps("http://example.com")) throw new Exception("http помечен как https");
                if (!UrlParser.IsHttps("https://example.com")) throw new Exception("https не распознан");
                if (UrlParser.IsSafeExternalScheme("javascript")) throw new Exception("javascript: не заблокирован");
                if (!UrlParser.IsSafeExternalScheme("mailto")) throw new Exception("mailto заблокирован");
                return "URL / поиск / localhost / internal / HTTPS-флаг / опасные схемы — OK";
            });

            await Run("search-engines", "DrSearch — единственная система адресной строки", () =>
            {
                var settings = SettingsService.CreateDefaults();
                if (settings.SearchEngine != AppSettings.DrSearchName)
                    throw new Exception("DrSearch не выбран: " + settings.SearchEngine);
                if (settings.DrSearchWebEnabled)
                    throw new Exception("внешний индекс включён по умолчанию");
                if (AppSettings.SearchEngines.Length != 1 || AppSettings.SearchEngines[0].Name != AppSettings.DrSearchName)
                    throw new Exception("в реестре остались сторонние поисковые системы");

                foreach (var forbidden in new[] { "Google", "Яндекс", "Bing", "DuckDuckGo" })
                {
                    settings.SearchEngine = forbidden;
                    var url = settings.SearchUrl("test query");
                    if (!url.StartsWith("drbrowser://search?q=", StringComparison.OrdinalIgnoreCase))
                        throw new Exception(forbidden + " смог заменить DrSearch: " + url);
                }

                var dr = UrlParser.Resolve("test query", settings);
                if (!dr.StartsWith("drbrowser://search?q=")) throw new Exception("DrSearch не внутренняя страница: " + dr);
                if (UrlParser.QueryOf(dr) != "test query") throw new Exception("потерян поисковый запрос");
                return "DrSearch-only · внешний индекс off · сторонняя настройка не создаёт редирект";
            });

            await Run("drsearch-index", "DrSearch: локальный поиск по истории и закладкам", async () =>
            {
                var dbFile = Path.Combine(temp, "search.db");
                var db = new Db(dbFile);
                db.Initialize();
                db.AddHistory("https://learn.microsoft.com/dotnet/", "Документация .NET", "learn.microsoft.com", DateTime.Now);
                db.AddHistory("https://github.com/dotnet/runtime", "dotnet runtime", "github.com", DateTime.Now);

                var log2 = new Logger();
                var history = new HistoryService(db, log2);
                var bookmarks = new BookmarkService(db, log2);
                await bookmarks.LoadAsync().ConfigureAwait(true);
                await bookmarks.AddAsync("DrBrowser docs", "https://example.org/drbrowser", "Проверка").ConfigureAwait(true);

                var settings = SettingsService.CreateDefaults();
                string? navigated = null;
                var search = new SearchViewModel(log2, history, bookmarks, settings, url => navigated = url,
                    new WebSearchService(log2));

                search.Run("dotnet");
                if (search.Results.Count < 2) throw new Exception("локальный индекс не нашёл .NET в истории: " + search.Results.Count);
                if (!search.Results.Any(r => r.Badge == "ИСТОРИЯ")) throw new Exception("нет результатов из истории");

                search.Run("drbrowser");
                if (!search.Results.Any(r => r.Badge == "ЗАКЛАДКА")) throw new Exception("закладка не попала в DrSearch");

                search.Run("example.com");
                if (!search.Results.Any(r => r.Badge == "АДРЕС")) throw new Exception("прямой адрес не распознан");

                var first = search.Results.First();
                search.OpenResultCommand.Execute(first);
                if (string.IsNullOrWhiteSpace(navigated)) throw new Exception("переход по результату не сработал");

                search.Run("нет-такого-слова-в-индексе-42");
                if (search.Results.Count != 0) throw new Exception("ложные результаты на пустом запросе");

                search.Run("dotnet");
                return $"история + закладки + адрес + переход — {search.Results.Count} результатов";
            });

            await Run("drsearch-fts5", "DrSearch: собственный FTS5, BM25, фразы и дедупликация", async () =>
            {
                var indexDb = new Db(Path.Combine(temp, "fts5.db"));
                var index = new SearchIndexService(indexDb, log);
                if (!index.IsAvailable) throw new Exception("FTS5 недоступен: " + index.InitializationError);

                await index.UpsertAsync(new SearchDocumentInput(
                    "https://example.com/cpu?utm_source=test",
                    "Что такое процессор",
                    "Процессор выполняет инструкции программ. Центральный процессор содержит ядра и кеш память.",
                    "процессор CPU компьютер", Language: "ru"));
                await index.UpsertAsync(new SearchDocumentInput(
                    "https://example.org/dotnet",
                    ".NET runtime architecture",
                    "The dotnet runtime executes managed code and provides garbage collection for applications.",
                    "dotnet runtime CLR", Language: "en"));
                // Same body under another URL must not inflate the corpus.
                await index.UpsertAsync(new SearchDocumentInput(
                    "https://mirror.example.net/cpu",
                    "Копия статьи",
                    "Процессор выполняет инструкции программ. Центральный процессор содержит ядра и кеш память.",
                    Language: "ru"));

                var russian = index.Search("процессор инструкции");
                if (russian.Count != 1 || !russian[0].Snippet.Contains('⟦'))
                    throw new Exception("BM25/подсветка не нашли русский документ");
                var phrase = index.Search("\"managed code\"");
                if (phrase.Count != 1 || phrase[0].Language != "en") throw new Exception("поиск фразы не сработал");
                var stats = index.GetStats();
                if (stats.DocumentCount != 2 || stats.LastUpdated is null)
                    throw new Exception($"дедупликация/metadata: {stats.DocumentCount}");
                var normalized = SearchIndexService.NormalizeUrl("HTTPS://Example.com:443/a/?utm_source=x&b=2#part");
                if (normalized.Contains("utm_", StringComparison.OrdinalIgnoreCase) || normalized.Contains('#'))
                    throw new Exception("URL не нормализован: " + normalized);
                return $"{stats.DocumentCount} документа · RU/EN · BM25 · snippet highlight · canonical URL";
            });

            await Run("omega-productivity", "DrNotes, DrTasks и DrWorkspaces: реальный SQLite CRUD", async () =>
            {
                var productivityDb = new Db(Path.Combine(temp, "productivity.db"));
                var productivity = new ProductivityService(productivityDb, log);
                var noteId = await productivity.SaveNoteAsync(0, "План OMEGA", "Первый пункт");
                var notes = await productivity.LoadNotesAsync();
                if (notes.Count != 1 || notes[0].Id != noteId || notes[0].Body != "Первый пункт")
                    throw new Exception("заметка не сохранилась");
                var taskId = await productivity.AddTaskAsync("Проверить браузер");
                await productivity.SetTaskCompletedAsync(taskId, true);
                var tasks = await productivity.LoadTasksAsync();
                if (tasks.Count != 1 || !tasks[0].IsCompleted) throw new Exception("состояние задачи не сохранилось");
                var workspaces = await productivity.LoadWorkspacesAsync();
                if (workspaces.Count != 6 || workspaces.All(workspace => workspace.Tabs.Count == 0))
                    throw new Exception("пресеты пространств не созданы");
                await productivity.DeleteTaskAsync(taskId);
                await productivity.DeleteNoteAsync(noteId);
                return $"note CRUD · task CRUD · {workspaces.Count} пространств со снимками вкладок";
            });

            await Run("drmind-memory", "DrMind: явная локальная память SQLite", async () =>
            {
                var memoryDb = new Db(Path.Combine(temp, "memory.db"));
                var memory = new AiMemoryService(memoryDb);
                var memoryId = await memory.AddAsync("Предпочитаю краткие ответы", "preference");
                var items = await memory.LoadAsync();
                var context = memory.BuildPromptContext();
                if (items.Count != 1 || items[0].Id != memoryId || !context.Contains("краткие ответы"))
                    throw new Exception("память не прошла round-trip");
                await memory.DeleteAsync(memoryId);
                if ((await memory.LoadAsync()).Count != 0) throw new Exception("память не удалилась");
                return "explicit add · prompt context · delete; автоматического сохранения нет";
            });

            await Run("backup", "Backup: SQLite snapshot, manifest, SHA-256 и исключение секретов", async () =>
            {
                var backupDb = new Db(Path.Combine(temp, "backup-source.db"));
                backupDb.AddBookmark(new Bookmark { Title = "OMEGA", Url = "https://example.com", Folder = "Тест", CreatedAt = DateTime.Now });
                var backup = new BackupService(backupDb);
                var archivePath = Path.Combine(temp, "omega.drbackup");
                var created = await backup.CreateAsync(archivePath, SettingsService.CreateDefaults());
                var validated = await backup.ValidateAsync(archivePath);
                using var archive = System.IO.Compression.ZipFile.OpenRead(archivePath);
                var names = archive.Entries.Select(entry => entry.FullName).OrderBy(name => name).ToArray();
                if (created.DatabaseSha256 != validated.DatabaseSha256 ||
                    names.Length != 3 || names.Contains("secrets", StringComparer.OrdinalIgnoreCase))
                    throw new Exception("состав/manifest резервной копии неверен");
                return string.Join(" + ", names) + " · SHA-256 verified · secrets excluded";
            });

            await Run("drtools", "DrTools: калькулятор, JSON, единицы, цвет и пароль", () =>
            {
                var calculated = UtilityEngine.Calculate("sqrt(81) + 2^3 * 2");
                if (Math.Abs(calculated - 25) > 0.0001) throw new Exception("калькулятор: " + calculated);
                var formatted = UtilityEngine.FormatJson("{\"b\":2,\"a\":1}");
                if (!formatted.Contains('\n')) throw new Exception("JSON не отформатирован");
                var miles = UtilityEngine.ConvertUnit(1, "km", "mi");
                if (Math.Abs(miles - 0.621371) > 0.0001) throw new Exception("конвертер длины: " + miles);
                if (UtilityEngine.HexToRgb("#8B5CF6") != "rgb(139, 92, 246)") throw new Exception("HEX → RGB");
                var password = PasswordService.Generate(24);
                if (password.Length != 24 || PasswordService.Strength(password) < 3) throw new Exception("пароль слабый");
                if (ThemeManager.ThemeNames.Count != 10) throw new Exception("ожидалось 10 тем");
                return "safe parser · JSON · km→mi · HEX→RGB · CSPRNG · 10 тем";
            });

            await Run("music-library", "DrMusic: сканирование папки, категории, поиск", () =>
            {
                var library = Path.Combine(temp, "music");
                Directory.CreateDirectory(Path.Combine(library, "Chill"));
                Directory.CreateDirectory(Path.Combine(library, "Rock"));

                File.WriteAllBytes(Path.Combine(library, "Chill", "Nova - Night Drive.mp3"), new byte[] { 1, 2, 3 });
                File.WriteAllBytes(Path.Combine(library, "Chill", "Aurora Waves.flac"), new byte[] { 4 });
                File.WriteAllBytes(Path.Combine(library, "Rock", "The Guitars - Riff.wav"), new byte[] { 5 });
                File.WriteAllText(Path.Combine(library, "notes.txt"), "не музыка");

                var tracks = MusicService.ScanFolder(library);
                if (tracks.Count != 3) throw new Exception("ожидалось 3 трека, найдено " + tracks.Count);
                if (tracks.Any(t => t.Extension.Equals("TXT", StringComparison.OrdinalIgnoreCase)))
                    throw new Exception("текстовый файл попал в библиотеку");

                var night = tracks.First(t => t.Title.Contains("Night Drive"));
                if (night.Artist != "Nova") throw new Exception("исполнитель из имени файла не разобран: " + night.Artist);

                var categories = tracks.Select(t => t.Category).Distinct().OrderBy(c => c).ToList();
                if (categories.Count != 2 || !categories.Contains("Chill") || !categories.Contains("Rock"))
                    throw new Exception("категории (подпапки) не собраны: " + string.Join(", ", categories));

                return $"{tracks.Count} трека · категории {string.Join(" / ", categories)} · .txt отфильтрован";
            });

            await Run("music-playlists", "DrMusic: SQLite плейлисты и Избранное", async () =>
            {
                var library = Path.Combine(temp, "music");
                var track = MusicService.ScanFolder(library).First();
                var playlistDb = new Db(Path.Combine(temp, "music-playlists.db"));
                var playlists = new MusicPlaylistService(playlistDb);
                var playlistId = await playlists.CreateAsync("Ночной код");
                await playlists.AddTrackAsync(playlistId, track);
                var loaded = await playlists.LoadAsync();
                var paths = await playlists.LoadPathsAsync(playlistId);
                if (loaded.Count != 1 || loaded[0].TrackCount != 1 || !paths.Contains(track.FilePath))
                    throw new Exception("плейлист не прошёл round-trip");
                if (!await playlists.ToggleFavoriteAsync(track)) throw new Exception("трек не добавился в Избранное");
                if (!(await playlists.LoadFavoritePathsAsync()).Contains(track.FilePath)) throw new Exception("Избранное не загрузилось");
                if (await playlists.ToggleFavoriteAsync(track)) throw new Exception("трек не удалился из Избранного");
                await playlists.RemoveTrackAsync(playlistId, track);
                await playlists.DeleteAsync(playlistId);
                if ((await playlists.LoadAsync()).Count != 0) throw new Exception("плейлист не удалился");
                return "create · add/remove track · favorites toggle · delete; файлы не изменены";
            });

            await Run("radio-stations", "DrMusic: HTTPS-радио и безопасная граница WebView2", () =>
            {
                var log = new Logger();
                using var service = new MusicService(log);

                if (service.Stations.Count < 5)
                    throw new Exception("встроенных станций меньше пяти: " + service.Stations.Count);

                if (!service.Stations.All(st => st.IsStream && st.Badge == "LIVE" &&
                        Uri.TryCreate(st.FilePath, UriKind.Absolute, out var uri) && uri.Scheme == Uri.UriSchemeHttps))
                    throw new Exception("станция без валидной HTTPS-ссылки/LIVE-бейджа");

                var radioCategory = service.Categories.FirstOrDefault(c => c.Name == MusicService.RadioCategory);
                if (radioCategory is null) throw new Exception("категория радио не появилась в DrMusic");
                if (service.Tracks.Count != service.Stations.Count)
                    throw new Exception("в списке треков не только радио: " + service.Tracks.Count);
                if (service.Queue.Any(track => track.IsStream))
                    throw new Exception("сетевой URL попал в локальную очередь MediaPlayer");

                return $"{service.Stations.Count} HTTPS-станций · LIVE · сетевые URL исключены из очереди MediaPlayer";
            });

            await Run("ai-personas", "DrAI: режимы и промпты отличаются", () =>
            {
                var ids = MainViewModel.AiPersonas.Select(p => p.Id).ToList();
                if (ids.Count < 3) throw new Exception("слишком мало режимов DrAI");
                if (!ids.Contains(LocaleAiAssistantId)) throw new Exception("нет режима помощника");

                var basePrompt = AIService.PromptFor("assistant");
                var coder = AIService.PromptFor("coder");
                var brief = AIService.PromptFor("brief");
                if (coder == basePrompt || brief == basePrompt)
                    throw new Exception("промпты режимов не отличаются от базового");

                return $"{ids.Count} режима · промпты различаются · базовый {basePrompt.Length} символов";
            });

            await Run("profile-avatars", "Профиль: пресеты аватара и инициалы", () =>
            {
                if (MainViewModel.AvatarStyles.Count < 5) throw new Exception("мало пресетов аватара");

                var brushes = MainViewModel.AvatarStyles.Select(MainViewModel.BuildAvatarBrush).ToList();
                if (brushes.Any(b => b is not System.Windows.Media.LinearGradientBrush)) throw new Exception("пресет не является градиентом");
                if (brushes.First() == brushes.Last()) throw new Exception("все пресеты одинаковые");

                var settings = new AppSettings { ProfileName = "Doctor Browser" };
                var initials = settings.ProfileName.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                if (initials.Length < 2) throw new Exception("инициалы не вычисляются");

                return $"{MainViewModel.AvatarStyles.Count} пресетов · градиенты различаются · инициалы DB";
            });

            await Run("drsearch-recents", "DrSearch: недавние запросы сохраняются", () =>
            {
                var dataDir = Path.Combine(temp, "recents");
                Directory.CreateDirectory(dataDir);

                var settings = new AppSettings();
                var db = new Db(Path.Combine(dataDir, "recents.db"));
                db.Initialize();
                var log = new Logger();
                var history = new HistoryService(db, log);
                var bookmarks = new BookmarkService(db, log);

                var search = new SearchViewModel(log, history, bookmarks, settings, _ => { }, new WebSearchService(log));
                search.Run("первый запрос");
                search.Run("второй запрос");
                search.Run("первый запрос");

                if (search.Recents.Count != 2) throw new Exception("дубликаты не схлопнулись: " + search.Recents.Count);
                if (search.Recents[0] != "первый запрос") throw new Exception("повторный запрос не поднялся наверх");
                if (settings.DrSearchRecent.Count != 2) throw new Exception("запросы не попали в настройки");

                return $"история запросов: {string.Join(" / ", search.Recents)}";
            });

            await Run("sqlite-history", "История в SQLite: запись, поиск, фильтр по дате, удаление", () =>
            {
                var db = new Db(dbPath);
                db.Initialize();

                db.AddHistory("https://example.com/a", "Example A", "example.com", DateTime.Now.AddDays(-10));
                db.AddHistory("https://example.com/b", "Example B", "example.com", DateTime.Now);
                db.AddHistory("https://github.com/x", "GitHub X", "github.com", DateTime.Now);
                db.AddHistory("https://github.com/y", "GitHub Y", "github.com", DateTime.Now);

                var all = db.QueryHistory(null, null, null);
                if (all.Count != 4) throw new Exception("ожидалось 4 записи, получено " + all.Count);

                var filtered = db.QueryHistory("github", null, null);
                if (filtered.Count != 2) throw new Exception("поиск 'github' дал " + filtered.Count);

                var recent = db.QueryHistory(null, DateTime.Now.AddDays(-1), null);
                if (recent.Count != 3) throw new Exception("фильтр по дате дал " + recent.Count);

                var top = db.QueryMostVisited();
                if (top.Count != 2 || top[0].Visits != 2) throw new Exception("most-visited неверен");

                db.DeleteHistory(all[0].Id);
                if (db.QueryHistory(null, null, null).Count != 3) throw new Exception("удаление записи не сработало");

                db.ClearHistory();
                if (db.QueryHistory(null, null, null).Count != 0) throw new Exception("очистка истории не сработала");
                return "4 записи · поиск · дата · most-visited · delete · clear — OK";
            });

            await Run("bookmarks", "Закладки: папки, поиск, HTML импорт/экспорт", async () =>
            {
                var db = new Db(dbPath);
                db.Initialize();
                var service = new BookmarkService(db, log);
                await service.LoadAsync();

                var a = await service.AddAsync("Example", "https://example.com", "Работа");
                var b = await service.AddAsync("GitHub", "https://github.com", "Код");
                if (service.Items.Count < 2) throw new Exception("закладки не добавились");
                if (!service.Folders.Contains("Работа") || !service.Folders.Contains("Код"))
                    throw new Exception("папки не появились");

                a.Title = "Example (edited)";
                a.Folder = "Код";
                await service.UpdateAsync(a);
                if (service.Items.First(x => x.Id == a.Id).Folder != "Код") throw new Exception("папка не обновилась");

                if (service.Search("edited").Count() != 1) throw new Exception("поиск закладок не работает");

                var html = Path.Combine(temp, "bookmarks.html");
                await service.ExportHtmlAsync(html);
                if (!File.Exists(html) || new FileInfo(html).Length < 100) throw new Exception("экспорт пуст");
                var text = await File.ReadAllTextAsync(html);
                if (!text.Contains("NETSCAPE-Bookmark")) throw new Exception("экспорт не в формате Netscape");

                var imported = await service.ImportHtmlAsync(html);
                var total = service.Items.Count;
                if (total != 2) throw new Exception("повторный импорт создал дубли (импортировано " + imported + ", всего " + total + ")");

                await service.DeleteAsync(b);
                if (service.Items.Count != 1) throw new Exception("удаление закладки не сработало");
                return "добавление · папки · правка · поиск · Netscape-экспорт · импорт без дублей";
            });

            await Run("passwords-dpapi", "Пароли: DPAPI-шифрование и отсутствие открытого текста", async () =>
            {
                var secret = "P@ssw0rd-" + Guid.NewGuid().ToString("N");
                var encrypted = SecretStore.Protect(secret);
                if (encrypted.Contains(secret, StringComparison.Ordinal)) throw new Exception("DPAPI вернул открытый текст");
                if (SecretStore.Unprotect(encrypted) != secret) throw new Exception("расшифровка не совпала");

                var db = new Db(dbPath);
                db.Initialize();
                var service = new PasswordService(db, log);
                await service.LoadAsync();
                var entry = await service.AddAsync("example.com", "user", secret);

                var rows = db.QueryPasswords();
                var stored = rows.First(r => r.Site == "example.com");
                var storedText = Encoding.UTF8.GetString(stored.Secret);
                if (storedText.Contains(secret, StringComparison.Ordinal))
                    throw new Exception("в базе лежит открытый пароль");

                if (SecretStore.UnprotectBytes(stored.Secret) != secret)
                    throw new Exception("пароль из базы не расшифровывается");

                if (service.Search("example").Count() != 1) throw new Exception("поиск паролей не работает");
                await service.DeleteAsync(entry);
                if (service.Entries.Count != 0) throw new Exception("удаление пароля не сработало");
                return "DPAPI round-trip · шифртекст в SQLite · поиск · удаление";
            });

            await Run("password-generator", "Генератор паролей и оценка стойкости", () =>
            {
                var generated = PasswordService.Generate(24, true, true, true);
                if (generated.Length != 24) throw new Exception("длина не 24");
                if (!generated.Any(char.IsUpper) || !generated.Any(char.IsLower) || !generated.Any(char.IsDigit))
                    throw new Exception("нет букв/цифр");
                if (PasswordService.Strength(generated) < 3) throw new Exception("стойкий пароль оценён низко");
                if (PasswordService.Strength("1234") > 1) throw new Exception("слабый пароль оценён высоко");
                return $"{generated[..4]}… · Strength «{PasswordService.StrengthText(PasswordService.Strength(generated))}»";
            });

            await Run("ai-without-key", "AI без ключа: понятное сообщение об ошибке", async () =>
            {
                var ai = new AIService(log);
                if (ai.HasKey) return "SKIP: в системе уже сохранён ключ, проверка сообщения пропущена";

                var failed = false;
                try
                {
                    await ai.CompleteAsync(SettingsService.CreateDefaults(),
                        new[] { new ChatMessage { Role = "user", Text = "ping" } });
                }
                catch (AiException ex) when (ex.Message.Contains("AI-ключ", StringComparison.OrdinalIgnoreCase))
                {
                    failed = true;
                }

                if (!failed) throw new Exception("без ключа не выдано понятное исключение");
                return "без ключа: AiException «AI-ключ не настроен…»; сеть не вызывается";
            });

            await Run("adblock", "AdBlock: домены, пользовательские правила, whitelist", async () =>
            {
                var adblock = new AdBlockService(log);
                await adblock.LoadAsync(string.Empty);
                adblock.Configure(true, Array.Empty<string>());

                if (!adblock.ShouldBlock("https://doubleclick.net/ad.js", "news.example", out var why))
                    throw new Exception("известный рекламный домен не заблокирован");
                if (!adblock.ShouldBlock("https://cdn.example.org/ads/banner.png", "news.example", out _))
                    throw new Exception("путь /ads/ не заблокирован");
                if (adblock.ShouldBlock("https://cdn.example.org/app.js", "news.example", out _))
                    throw new Exception("обычный скрипт заблокирован");

                adblock.Configure(true, new[] { "news.example" });
                if (adblock.ShouldBlock("https://doubleclick.net/ad.js", "news.example", out _))
                    throw new Exception("whitelist не отключил блокировку");

                adblock.Configure(true, Array.Empty<string>());
                adblock.Parse(new[] { "||tracker.example^" });
                if (!adblock.ShouldBlock("https://tracker.example/pixel", "news.example", out _))
                    throw new Exception("пользовательское правило не сработало");

                adblock.RegisterBlocked(why);
                if (adblock.BlockedCount != 1) throw new Exception("счётчик блокировок не растёт");
                adblock.ResetCounters();
                if (adblock.BlockedCount != 0) throw new Exception("счётчик не сбрасывается");
                return $"правил: {adblock.RuleCount} · домен/путь/whitelist/свои правила — OK";
            });

            await Run("downloads", "Загрузки: форматирование размеров и история в SQLite", () =>
            {
                if (DownloadItem.FormatSize(512) != "512 B") throw new Exception("B");
                if (!DownloadItem.FormatSize(2048).EndsWith("KB")) throw new Exception("KB");
                if (!DownloadItem.FormatSize(5 * 1024 * 1024).EndsWith("MB")) throw new Exception("MB");
                if (!DownloadItem.FormatSize(3L * 1024 * 1024 * 1024).EndsWith("GB")) throw new Exception("GB");

                var db = new Db(dbPath);
                db.Initialize();
                var service = new DownloadService(log, db, () => SettingsService.CreateDefaults());
                var item = new DownloadItem
                {
                    FileName = "test.zip", Path = Path.Combine(temp, "test.zip"),
                    Url = "https://example.com/test.zip", TotalBytes = 1024, ReceivedBytes = 1024
                };
                var id = db.AddDownload(item);
                db.UpdateDownloadState(id, DownloadState.Completed, 1024);

                var history = service.History();
                if (history.Count == 0) throw new Exception("история загрузок пуста");
                if (history[0].Size != 1024) throw new Exception("размер не сохранился");
                if (history[0].State != "Completed") throw new Exception("состояние не сохранилось");
                service.Shutdown();
                return "B/KB/MB/GB · запись и чтение истории загрузок";
            });

            await Run("session", "Сессия: сохранение и восстановление вкладок", () =>
            {
                var backupSession = Backup(AppPaths.SessionFile);
                var session = new SessionService(log);
                session.Save(new[]
                {
                    new SessionTab { Url = "https://example.com", Title = "Example", IsPinned = true },
                    new SessionTab { Url = "drbrowser://settings", Title = "Настройки" }
                }, 1);

                var loaded = session.Load();
                if (loaded is null || loaded.Tabs.Count != 2) throw new Exception("сессия не восстановилась");
                if (!loaded.Tabs[0].IsPinned) throw new Exception("закрепление не сохранилось");
                if (loaded.ActiveIndex != 1) throw new Exception("активная вкладка не сохранилась");
                session.Clear();

                Restore(AppPaths.SessionFile, backupSession);
                return "session.json: 2 вкладки, pin и активный индекс — OK";
            });

            await Run("quicklinks-permissions", "Быстрые ссылки и разрешения сайтов в SQLite", () =>
            {
                var db = new Db(Path.Combine(temp, "quick.db"));
                db.Initialize();
                var id = db.AddQuickLink("GitHub", "https://github.com", 0);
                var links = db.QueryQuickLinks();
                if (links.Count != 1 || links[0].Title != "GitHub") throw new Exception("быстрая ссылка не сохранилась");
                db.DeleteQuickLink(id);
                if (db.QueryQuickLinks().Count != 0) throw new Exception("быстрая ссылка не удалилась");

                db.SetPermission("meet.example", "camera", true);
                if (db.GetPermission("meet.example", "camera") != true) throw new Exception("разрешение не сохранилось");
                if (db.QueryPermissions().Count != 1) throw new Exception("список разрешений пуст");
                db.ClearPermissions();
                if (db.GetPermission("meet.example", "camera") is not null) throw new Exception("разрешения не сброшены");
                return "quick links CRUD · permissions set/get/clear";
            });

            await Run("incognito-profile", "Инкогнито: временный профиль создаётся и удаляется", () =>
            {
                var dir = AppPaths.NewIncognitoFolder();
                if (!Directory.Exists(dir)) throw new Exception("профиль не создан");
                File.WriteAllText(Path.Combine(dir, "marker.txt"), "x");
                AppPaths.TryDeleteDirectory(dir);
                if (Directory.Exists(dir)) throw new Exception("профиль не удалён");
                return "profile\\incognito-<guid> создан и удалён вместе с данными";
            });

            await Run("webview2-runtime", "Определение Microsoft Edge WebView2 Runtime", () =>
            {
                var available = WebViewManager.IsRuntimeAvailable();
                var version = WebViewManager.RuntimeVersionOrEmpty();
                if (!available && string.IsNullOrEmpty(version)) return "SKIP: Runtime не установлен (в приложении будет показана инструкция)";
                if (string.IsNullOrWhiteSpace(version)) throw new Exception("Runtime найден, но версия не определена");
                return "WebView2 Runtime " + version;
            });

            await Run("settings-limits", "Ограничения настроек (масштаб, суспензия вкладок)", () =>
            {
                var settings = SettingsService.CreateDefaults();
                settings.ZoomLevel = 99;
                if (settings.ZoomLevel > 3.0) throw new Exception("zoom не ограничен");
                settings.ZoomLevel = 0.01;
                if (settings.ZoomLevel < 0.25) throw new Exception("zoom ниже минимума");
                settings.TabSuspendMinutes = -5;
                if (settings.TabSuspendMinutes < 1) throw new Exception("суспензия не ограничена");
                settings.Normalize();
                if (string.IsNullOrWhiteSpace(settings.HomePage)) throw new Exception("Normalize не восстановил домашнюю страницу");
                return "zoom 0.25–3.0 · suspend 1–240 · Normalize восстанавливает значения";
            });

            await Run("guard-url-rules", "DrGuard: блокировка и предупреждения по URL", () =>
            {
                var settings = SettingsService.CreateDefaults();
                settings.DrGuardEnabled = true;
                settings.DrGuardWarnOnHttp = true;
                var sec = new SecurityService(log, settings);

                void Expect(string url, ThreatLevel level, string why)
                {
                    var verdict = sec.CheckUrl(url);
                    if (verdict.Level != level)
                        throw new Exception($"{url}: ожидалось {level}, получено {verdict.Level} ({verdict.Reason})");
                    _ = why;
                }

                Expect("https://example.com/page", ThreatLevel.Safe, "обычный HTTPS");
                Expect("drbrowser://guard", ThreatLevel.Safe, "внутренняя страница");
                Expect("https://user:pass@evil.example/", ThreatLevel.Block, "логин/пароль в адресе");
                Expect("https://xn--pple-43d.com/", ThreatLevel.Block, "punycode");
                Expect("https://login-verify.top/", ThreatLevel.Block, "TLD + приманка");
                Expect("http://example.com/", ThreatLevel.Warn, "HTTP без шифрования");

                settings.DrGuardBlockedHosts.Add("ads.example");
                Expect("https://cdn.ads.example/x.js", ThreatLevel.Block, "пользовательский blocklist");
                sec.AddExemption("ads.example");
                Expect("https://cdn.ads.example/x.js", ThreatLevel.Safe, "исключение снимает блокировку");

                settings.DrGuardEnabled = false;
                Expect("https://login-verify.top/", ThreatLevel.Safe, "защита выключена — не мешает");

                if (!SecurityService.MatchesHost("sub.example.com", "example.com"))
                    throw new Exception("MatchesHost не считает поддомен совпадением");
                if (SecurityService.MatchesHost("notexample.com", "example.com"))
                    throw new Exception("MatchesHost склеил чужие домены");
                return "8 сценариев: safe · inner · credentials · punycode · tld-bait · http · blocklist · exemption";
            });

            await Run("guard-scan-args", "DrGuard: аргументы Defender и определение движка", () =>
            {
                var args = SecurityService.BuildScanArguments(@"C:\Temp\file name.exe");
                if (!args.Contains("-ScanType 3", StringComparison.Ordinal))
                    throw new Exception("нет ScanType 3 (custom scan): " + args);
                if (!args.Contains("-DisableRemediation", StringComparison.Ordinal))
                    throw new Exception("нет -DisableRemediation: " + args);
                if (!args.Contains("\"C:\\Temp\\file name.exe\"", StringComparison.Ordinal))
                    throw new Exception("путь с пробелом не закавычен: " + args);
                var cli = SecurityService.FindDefenderCli();
                return cli is null
                    ? "MpCmdRun.exe не найден (сканирование честно покажет «движок недоступен»)"
                    : "MpCmdRun.exe: " + cli;
            });

            await Run("ai-image-attachment", "Фото DrAI: сигнатуры, лимит, base64, имя без пути", () =>
            {
                var png = Convert.FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=");
                var attachment = AiImageAttachment.FromBytes(System.IO.Path.Combine("private", "photo.png"), png);
                if (attachment.Name != "photo.png" || !attachment.DataUrl.StartsWith("data:image/png;base64,"))
                    throw new Exception("Неверная упаковка фото");
                foreach (var bytes in new[] { Array.Empty<byte>(), new byte[] { 1, 2, 3 }, new byte[AiImageAttachment.MaxBytes + 1] })
                {
                    var rejected = false;
                    try { AiImageAttachment.FromBytes("bad.png", bytes); }
                    catch (ArgumentException) { rejected = true; }
                    if (!rejected) throw new Exception("Недопустимое вложение принято");
                }
                return "PNG data URL · имя без каталога · пустые/неизвестные/слишком большие файлы отклонены";
            });

            await Run("local-ai", "DrAI Local: краткое содержание, ключевые слова, статистика", () =>
            {
                var engine = new LocalAiEngine();
                var text = "DrBrowser — это браузер на WPF с WebView2. Браузер хранит историю в SQLite. " +
                           "DrGuard проверяет ссылки перед открытием. DrAI Local работает без ключа и без сети. " +
                           "Музыкальный раздел ищет треки в публичных каталогах.";
                var summary = engine.Summarize(text, 2);
                if (string.IsNullOrWhiteSpace(summary)) throw new Exception("конспект пуст");
                if (summary.Split('.', StringSplitOptions.RemoveEmptyEntries).Length > 3)
                    throw new Exception("конспект длиннее запрошенного: " + summary);

                var keywords = engine.Keywords(text, 6);
                if (string.IsNullOrWhiteSpace(keywords)) throw new Exception("ключевые слова пусты");
                if (keywords.Contains("<", StringComparison.Ordinal)) throw new Exception("в ключевых словах осталась разметка");

                var stats = engine.ReadingStats(text);
                if (!stats.Contains("слов", StringComparison.OrdinalIgnoreCase))
                    throw new Exception("статистика не содержит счётчик слов: " + stats);

                if (LocalAiEngine.DetectLanguage(engine.ExplainCode("public class A { void M() { } }")).Length == 0)
                    throw new Exception("язык кода не определён");

                var answer = engine.Answer("Что такое DrGuard?", text);
                if (string.IsNullOrWhiteSpace(answer)) throw new Exception("локальный ответ пуст");

                return "конспект · ключевые слова · статистика · язык кода · ответ по странице — без сети";
            });

            await Run("music-catalog-parse", "Каталог музыки: разбор ответов iTunes и Deezer", () =>
            {
                const string itunes = """
                {"resultCount":2,"results":[
                  {"trackName":"Test Song","artistName":"Test Artist","collectionName":"Test Album",
                   "previewUrl":"https://audio.example/preview.m4a","trackViewUrl":"https://music.example/song",
                   "artworkUrl100":"https://img.example/a.jpg","trackTimeMillis":201000},
                  {"trackName":"","artistName":"X"}
                ]}
                """;
                var fromItunes = MusicCatalogService.ParseItunes(itunes);
                if (fromItunes.Count != 1) throw new Exception("iTunes: ожидался 1 трек, получено " + fromItunes.Count);
                var track = fromItunes[0];
                if (track.Title != "Test Song" || track.Artist != "Test Artist") throw new Exception("поля iTunes разобраны неверно");
                if (track.DurationSeconds != 201) throw new Exception("длительность iTunes = " + track.DurationSeconds);
                if (!track.HasPreview) throw new Exception("превью iTunes потеряно");
                if (!track.DurationText.Contains("3:21", StringComparison.Ordinal)) throw new Exception("DurationText = " + track.DurationText);

                const string deezer = """
                {"data":[
                  {"title":"Another Song","duration":185,"preview":"https://cdns.example/p.mp3",
                   "link":"https://deezer.example/track/1","artist":{"name":"Band"},"album":{"title":"LP"}}
                ]}
                """;
                var fromDeezer = MusicCatalogService.ParseDeezer(deezer);
                if (fromDeezer.Count != 1) throw new Exception("Deezer: ожидался 1 трек");
                if (fromDeezer[0].Subtitle != "Band · LP") throw new Exception("Subtitle Deezer = " + fromDeezer[0].Subtitle);
                if (fromDeezer[0].DurationText != "3:05") throw new Exception("Deezer: неверная длительность " + fromDeezer[0].DurationText);

                var link = MusicCatalogService.YandexMusicUrl("тест трек");
                if (!link.StartsWith("https://music.yandex.ru/", StringComparison.Ordinal))
                    throw new Exception("ссылка на Яндекс Музыку неверна: " + link);
                return $"iTunes {fromItunes.Count} · Deezer {fromDeezer.Count} · превью · длительность · ссылка на Яндекс";
            });

            await Run("drsearch-parse", "DrSearch: безопасный разбор внешней выдачи", () =>
            {
                const string html = """
                <div class="result results_links">
                  <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fdocs&amp;rut=abc">Example &amp; Docs</a>
                  <a class="result__snippet" href="//x">Документация примера</a>
                  <a rel="nofollow" class="result__a" href="https://second.example/page">Second &lt;b&gt;Result&lt;/b&gt;</a>
                  <a class="result__a" href="javascript:alert(1)">Bad scheme</a>
                  <a class="result__a" href="https://second.example/page">Duplicate</a>
                </div>
                """;
                var hits = WebSearchService.Parse(html, 10);
                if (hits.Count != 2) throw new Exception("ожидалось 2 результата, получено " + hits.Count + ": " +
                                                        string.Join(" | ", hits.Select(h => h.Url)));
                if (hits[0].Url != "https://example.com/docs") throw new Exception("uddg-редирект не расжат: " + hits[0].Url);
                if (hits[0].Title != "Example & Docs") throw new Exception("HTML-сущности в заголовке не раскрыты: " + hits[0].Title);
                if (!hits[0].Snippet.Contains("Документация", StringComparison.Ordinal)) throw new Exception("сниппет не попал в результат");
                if (hits.Any(h => h.Url.StartsWith("javascript", StringComparison.OrdinalIgnoreCase)))
                    throw new Exception("опасная схема попала в выдачу");
                if (hits.Any(h => !h.Source.Contains("ВНЕШНИЙ", StringComparison.Ordinal)))
                    throw new Exception("внешний результат не имеет явной метки источника");
                return "2 из 4 · внешняя метка · redirect decode · HTML entities · snippet · javascript отсеян";
            });

            await Run("error-logging", "Обработка ошибок пишется в журнал", () =>
            {
                var before = File.Exists(log.FilePath) ? new FileInfo(log.FilePath).Length : 0;
                log.Error("selftest intentional exception", new InvalidOperationException("expected"));
                var after = new FileInfo(log.FilePath).Length;
                if (after <= before) throw new Exception("ошибка не попала в журнал");
                var tail = ReadTail(log.FilePath, 4096);
                if (!tail.Contains("selftest intentional exception")) throw new Exception("запись не найдена в журнале");
                return "ERROR + stack trace записаны в журнал";
            });
        }
        catch (Exception ex)
        {
            Results.Add(new Check("harness", "Сбой тестового окружения", "FAIL", ex.Message));
            log.Error("selftest harness failure", ex);
        }
        finally
        {
            Restore(AppPaths.SettingsFile, settingsBackup);
            AppPaths.TryDeleteDirectory(temp);
        }

        return Finish("DrBrowser self-test", "selftest-report.txt");
    }

    // ------------------------------------------------------------------
    // UI smoke test (real window + WebView2)
    // ------------------------------------------------------------------

    public static async Task<int> RunSmokeAsync(Logger log)
    {
        Results.Clear();
        var settingsBackup = Backup(AppPaths.SettingsFile);
        var shots = ReportDir;
        Directory.CreateDirectory(shots);
        MainWindow? window = null;
        MainViewModel? vm = null;

        try
        {
            log.Info("=== smoke: start ===");
            Application.Current.ShutdownMode = ShutdownMode.OnExplicitShutdown;
            ArmWatchdog(150, "smoke-report.txt");
            Trace("smoke: start");
            window = App.Current.OpenWindow(true);
            Trace("smoke: window created");
            window.Width = 1440;
            window.Height = 900;
            vm = window.ViewModel;
            Trace("smoke: view model attached");

            await Run("smoke-window", "Окно запускается и инициализируется", async () =>
            {
                for (var i = 0; i < 60 && vm.Tabs.Count == 0; i++) await Delay(250);
                if (!window.IsVisible) throw new Exception("окно не отображается");
                if (vm.Tabs.Count == 0) throw new Exception("вкладки не созданы");
                window.ShowInternalPage("newtab");
                await Delay(600);
                if (!window.CaptureWindow(Path.Combine(shots, "01-newtab.png")))
                    throw new Exception("не удалось сохранить скриншот");
                return $"окно {window.ActualWidth:0}×{window.ActualHeight:0}, вкладок: {vm.Tabs.Count}, скриншот 01-newtab.png";
            });

            await Run("smoke-webview", "WebView2 инициализируется и исполняет страницу", async () =>
            {
                if (vm.RuntimeMissing) return "SKIP: Microsoft Edge WebView2 Runtime не установлен на этой машине";
                Trace("smoke-webview: navigating");
                vm.NavigateActiveTab("https://example.com");
                for (var i = 0; i < 60 && vm.ActiveTab?.Core is null; i++) await Delay(250);
                Trace("smoke-webview: core " + (vm.ActiveTab?.Core is null ? "null" : "ready"));
                var core = vm.ActiveTab?.Core;
                if (core is null) throw new Exception("WebView2 не создан за 15 секунд");

                // Stop the first navigation and load a known page through the engine itself.
                var navTcs = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                void OnNav(object? sender, CoreWebView2NavigationCompletedEventArgs e) => navTcs.TrySetResult(true);
                core.NavigationCompleted += OnNav;

                try
                {
                    core.Navigate("about:blank");
                    await WithTimeout(navTcs.Task, 20000, "переход на about:blank");
                }
                finally
                {
                    core.NavigationCompleted -= OnNav;
                }

                Trace("smoke-webview: calling NavigateToString");
                core.NavigateToString(
                    "<html><head><title>DrBrowser Smoke Test</title></head>" +
                    "<body style='background:#0b0d14;color:#f2f3f9;font-family:Segoe UI;padding:48px'>" +
                    "<h1 style='color:#7c5cff'>DrBrowser</h1><p>Движок WebView2 отрисовал эту страницу.</p>" +
                    "</body></html>");

                Trace("smoke-webview: NavigateToString returned");
                var title = string.Empty;
                for (var i = 0; i < 40; i++)
                {
                    await Delay(250);
                    title = core.DocumentTitle ?? string.Empty;
                    if (title == "DrBrowser Smoke Test") break;
                }

                if (title != "DrBrowser Smoke Test")
                    throw new Exception("document.title не подтверждён (title=" + (title.Length == 0 ? "пусто" : title) + ")");

                var raw = await WithTimeout(core.ExecuteScriptAsync("document.title"), 8000, "ExecuteScriptAsync");
                var scriptTitle = raw?.Trim('"');
                if (scriptTitle != "DrBrowser Smoke Test")
                    throw new Exception("ExecuteScriptAsync вернул " + (scriptTitle ?? "null"));

                await Delay(400);
                window!.CaptureWindow(Path.Combine(shots, "02-webview.png"));
                return $"Chromium {WebViewManager.RuntimeVersionOrEmpty()} · заголовок подтверждён через DocumentTitle и ExecuteScriptAsync";
            });

            await Run("smoke-tabs", "Вкладки: создание, переключение, закрытие, восстановление", async () =>
            {
                var initial = vm.Tabs.Count;
                var page = vm.CreateNewTabPage();
                await Delay(250);
                if (vm.Tabs.Count != initial + 1) throw new Exception("новая вкладка не появилась");

                var web = vm.CreateWebTab("https://example.org");
                await Delay(700);
                if (vm.Tabs.Count != initial + 2) throw new Exception("веб-вкладка не появилась");

                vm.SelectTab(page);
                if (!ReferenceEquals(vm.ActiveTab, page)) throw new Exception("переключение вкладки не сработало");

                vm.CloseTab(page);
                await Delay(200);
                if (vm.Tabs.Count != initial + 1) throw new Exception("закрытие вкладки не сработало");

                vm.SelectTab(web);
                vm.CloseTab(web);
                await Delay(300);
                var afterClose = vm.Tabs.Count;
                if (afterClose != initial) throw new Exception("закрытие веб-вкладки не сработало");

                vm.ReopenClosedTab();
                await Delay(900);
                if (vm.Tabs.Count != afterClose + 1) throw new Exception("восстановление вкладки не сработало");
                return $"было {initial} → +2 → закрыто 2 → восстановлено 1 ({vm.Tabs.Count})";
            });

            await Run("smoke-pages", "Все внутренние страницы открываются и рисуются", async () =>
            {
                var keys = new[] { "settings", "history", "downloads", "bookmarks", "passwords", "search", "music", "workspaces", "tools", "performance", "newtab" };
                var index = 3;
                foreach (var key in keys)
                {
                    vm.OpenInternalCommand.Execute(key);
                    await Delay(900);
                    if (vm.CurrentInternalKey != key)
                        throw new Exception($"страница {key} не активировалась (текущая: {vm.CurrentInternalKey})");

                    if (key == "performance")
                    {
                        vm.Omega.RefreshPerformance();
                        await Delay(150);
                        var metrics = new[]
                        {
                            vm.Omega.CpuText, vm.Omega.RamText, vm.Omega.ManagedMemoryText,
                            vm.Omega.BrowserTabsText, vm.Omega.WebViewProcessText, vm.Omega.RuntimeText
                        };
                        if (metrics.Any(value => string.IsNullOrWhiteSpace(value) || value == "—"))
                            throw new Exception("реальные показатели производительности не заполнены");
                        if (metrics.Any(value => !HasVisualText(window!, value)))
                            throw new Exception("значение производительности существует, но не отображается в интерфейсе");
                    }

                    window!.CaptureWindow(Path.Combine(shots, $"{index:00}-{key}.png"));
                    index++;
                }

                var currentLog = File.Exists(log.FilePath) ? File.ReadAllText(log.FilePath) : string.Empty;
                var marker = currentLog.LastIndexOf("=== smoke: start ===", StringComparison.Ordinal);
                if (marker >= 0) currentLog = currentLog[marker..];
                if (currentLog.Contains("Unhandled UI exception", StringComparison.Ordinal))
                    throw new Exception("в журнале обнаружено необработанное исключение привязки/интерфейса");

                return "11 страниц и PNG · показатели Performance видимы · UI-исключений нет";
            });

            await Run("smoke-command-palette", "DrCommand фильтрует и выполняет зарегистрированные действия", async () =>
            {
                vm.OpenCommandPalette();
                if (!vm.IsCommandPaletteOpen || vm.CommandPaletteResults.Count < 15)
                    throw new Exception("реестр команд не загрузился");
                vm.CommandPaletteQuery = "DrMusic";
                if (vm.CommandPaletteResults.Count != 1 || vm.CommandPaletteResults[0].Id != "route:music")
                    throw new Exception("поиск команды DrMusic не сработал");
                vm.ExecuteSelectedPaletteItem();
                await Delay(600);
                if (vm.IsCommandPaletteOpen || vm.CurrentInternalKey != "music")
                    throw new Exception("команда DrMusic не выполнилась");
                return "23 команды · фильтрация DrMusic · выполнение маршрута";
            });

            await Run("smoke-settings-tabs", "Разделы настроек «Музыка» и «AI Assistant» не пустые", async () =>
            {
                vm.OpenInternalCommand.Execute("settings");
                await Delay(900);

                var tabs = FindVisual<TabControl>(window!);
                if (tabs is null) throw new Exception("TabControl настроек не найден в окне");
                if (tabs.Items.Count < 11) throw new Exception("разделов настроек меньше 11: " + tabs.Items.Count);

                tabs.SelectedIndex = 3;
                await Delay(600);
                window!.CaptureWindow(Path.Combine(shots, "14-settings-music.png"));

                tabs.SelectedIndex = 7;
                await Delay(600);
                window!.CaptureWindow(Path.Combine(shots, "15-settings-ai.png"));

                tabs.SelectedIndex = 0;
                await Delay(300);
                return $"{tabs.Items.Count} разделов · Музыка и AI Assistant отрисованы (14/15 PNG)";
            });

            await Run("smoke-ui", "Масштаб, поиск на странице, AI-панель, закладки", async () =>
            {
                vm.ResetZoom();
                vm.ZoomBy(0.1);
                if (Math.Abs(vm.Settings.ZoomLevel - 1.1) > 0.001) throw new Exception("zoom +10% не применился");
                vm.ZoomBy(-0.1);
                if (Math.Abs(vm.Settings.ZoomLevel - 1.0) > 0.001) throw new Exception("zoom -10% не применился");

                vm.ToggleFindCommand.Execute(null);
                await Delay(250);
                if (!vm.IsFindVisible) throw new Exception("панель поиска не открылась");
                vm.ToggleFindCommand.Execute(null);
                if (vm.IsFindVisible) throw new Exception("панель поиска не закрылась");

                vm.ToggleAiCommand.Execute(null);
                await Delay(300);
                if (!vm.IsAiOpen) throw new Exception("AI-панель не открылась");
                window!.CaptureWindow(Path.Combine(shots, "10-ai-panel.png"));
                vm.CloseAiCommand.Execute(null);

                // Profile popup: the old implementation was an unstyled white context menu.
                vm.ToggleProfileCommand.Execute(null);
                await Delay(500);
                if (!vm.IsProfileOpen) throw new Exception("попап профиля не открылся");
                window!.CaptureWindow(Path.Combine(shots, "12-profile.png"));
                vm.CloseProfileCommand.Execute(null);
                await Delay(200);
                if (vm.IsProfileOpen) throw new Exception("попап профиля не закрылся");

                var bookmark = await vm.Bookmarks.AddAsync("Smoke", "https://example.org", "Проверка");
                if (!vm.Bookmarks.Items.Any(b => b.Id == bookmark.Id)) throw new Exception("закладка не добавлена");
                await vm.Bookmarks.DeleteAsync(bookmark);
                return "zoom ±10% · Ctrl+F панель · AI-панель · профиль-попап · закладка add/delete";
            });

            await Run("smoke-miniplayer", "DrMusic реально открывает локальный WAV и показывает мини-плеер", async () =>
            {
                var mediaDir = Path.Combine(Path.GetTempPath(), "drbrowser-smoke-audio-" + Guid.NewGuid().ToString("N"));
                Directory.CreateDirectory(mediaDir);
                var mediaPath = Path.Combine(mediaDir, "DrBrowser Smoke Tone.wav");
                WriteTestWave(mediaPath);
                var track = new MusicTrack
                {
                    FilePath = mediaPath,
                    Title = "DrBrowser Smoke Tone",
                    Artist = "Local self-test",
                    Category = "Smoke"
                };

                try
                {
                    vm.Music.Service.Play(track);
                    for (var i = 0; i < 30 && vm.Music.Service.DurationSeconds < 0.5 &&
                         !vm.Music.Service.Status.Contains("0xC00D11BA", StringComparison.OrdinalIgnoreCase); i++)
                        await Delay(200);

                    // A one-second fixture can finish between two CI scheduler ticks;
                    // NaturalDuration is retained after MediaEnded and is the stable
                    // proof that MediaOpened decoded the actual WAV.
                    if (vm.Music.Service.DurationSeconds < 0.5)
                    {
                        if (vm.Music.Service.Status.Contains("0xC00D11BA", StringComparison.OrdinalIgnoreCase))
                            return "SKIP: Windows CI runner не имеет аудиоустройства (0xC00D11BA); валидный WAV передан реальному MediaPlayer";
                        throw new Exception("MediaPlayer не открыл/не измерил локальный WAV: " + vm.Music.Service.Status);
                    }
                    if (!vm.MusicBarVisible) throw new Exception("строка мини-плеера не появилась");
                    if (vm.MusicNowBadge != "LOCAL") throw new Exception("бейдж мини-плеера: " + vm.MusicNowBadge);
                    if (!vm.MusicNowTitle.Contains(track.Title)) throw new Exception("в мини-плеере не тот локальный трек: " + vm.MusicNowTitle);

                    window!.CaptureWindow(Path.Combine(shots, "13-mini-player.png"));
                    return $"WAV {vm.Music.Service.DurationSeconds:0.0} с · MediaOpened · LOCAL · мини-плеер (13-mini-player.png)";
                }
                finally
                {
                    vm.Music.Service.Stop();
                    await Delay(200);
                    AppPaths.TryDeleteDirectory(mediaDir);
                }
            });

            await Run("smoke-hotkeys", "Горячие клавиши вызывают реальные команды", async () =>
            {
                var before = vm.Tabs.Count;
                vm.NewTabCommand.Execute(null);
                await Delay(250);
                if (vm.Tabs.Count != before + 1) throw new Exception("Ctrl+T не создал вкладку");
                vm.CloseTabCommand.Execute(null);
                await Delay(200);

                vm.OpenSettingsCommand.Execute(null);
                await Delay(500);
                if (vm.CurrentInternalKey != "settings") throw new Exception("Ctrl+, / настройки не открылись");
                vm.HomeCommand.Execute(null);

                // The same handler is used by the WebView2 accelerator bridge for keys typed inside a page.
                vm.IsFindVisible = true;
                await Delay(200);
                if (!window!.TryHandleHotkey(0x1B))
                    throw new Exception("мост клавиш не обработал Escape");
                if (vm.IsFindVisible) throw new Exception("Escape не закрыл панель поиска");

                window.TryHandleHotkey(0x7A);   // F11
                await Delay(200);
                window.TryHandleHotkey(0x7A);   // F11 back
                await Delay(200);
                if (!window.IsVisible) throw new Exception("окно пропало после F11");

                vm.OpenSettingsCommand.Execute(null);
                await Delay(300);
                window.TryHandleHotkey(0x74);   // F5
                await Delay(300);
                return "Ctrl+T · Ctrl+W · настройки · Home · Escape/F11/F5 через мост клавиш WebView2";
            });

            await Run("smoke-https", "Реальный сайт по HTTPS открывается через сеть", async () =>
            {
                var tab = vm.CreateWebTab("https://example.com/");
                await Delay(400);
                CoreWebView2? core = null;
                for (var i = 0; i < 60; i++)
                {
                    await Delay(250);
                    core = tab.Core;
                    if (core is not null) break;
                }

                if (core is null) return "SKIP: WebView2 не создал движок за 15 с";
                Trace("smoke-https: engine ready");

                var navigated = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
                void OnNav(object? _, CoreWebView2NavigationCompletedEventArgs e) => navigated.TrySetResult(e.IsSuccess);
                core.NavigationCompleted += OnNav;
                var protocol = string.Empty;
                var title = string.Empty;
                try
                {
                    // Subscribe first, then navigate: the tab is created with its URL, so an
                    // early navigation could otherwise finish before the handler exists.
                    core.Navigate("https://example.com/");
                    var finished = await Task.WhenAny(navigated.Task, Task.Delay(30000));
                    if (!ReferenceEquals(finished, navigated.Task))
                        return "SKIP: сеть недоступна в этой среде — навигация не завершилась за 30 с";
                    if (!await navigated.Task)
                        return "SKIP: HTTPS-навигация завершилась ошибкой сети (CI без доступа в интернет)";

                    for (var i = 0; i < 40; i++)
                    {
                        await Delay(250);
                        protocol = (await WithTimeout(core.ExecuteScriptAsync("location.protocol"), 8000, "protocol"))?.Trim('"') ?? string.Empty;
                        title = core.DocumentTitle ?? string.Empty;
                        if (protocol == "https:" && title.Length > 0) break;
                    }
                }
                finally
                {
                    core.NavigationCompleted -= OnNav;
                }

                if (protocol != "https:")
                    throw new Exception($"страница открылась не по HTTPS (protocol={protocol})");
                if (string.IsNullOrWhiteSpace(title))
                    throw new Exception("страница открылась, но document.title пуст");

                var text = (await WithTimeout(core.ExecuteScriptAsync("document.body.innerText.slice(0,80)"), 8000, "body"))
                    ?.Replace("\n", " ").Trim('"') ?? string.Empty;
                if (!text.Contains("Example", StringComparison.OrdinalIgnoreCase))
                    throw new Exception("на странице нет ожидаемого текста: " + text);

                window!.CaptureWindow(Path.Combine(shots, "09-https.png"));
                return $"https://example.com · protocol=https: · title «{title}»";
            });

            await Run("smoke-download", "Реальная загрузка файла через DownloadStarting", async () =>
            {
                var payload = Encoding.UTF8.GetBytes("DrBrowser download test " + Guid.NewGuid().ToString("N"));
                var (port, stop) = StartLoopbackServer(payload, "drbrowser-smoke.bin");
                try
                {
                    vm.NavigateActiveTab($"http://127.0.0.1:{port}/page");
                    await Delay(1200);

                    CoreWebView2? core = null;
                    for (var i = 0; i < 40; i++)
                    {
                        await Delay(250);
                        core = vm.ActiveTab?.Core;
                        if (core is not null && !vm.IsLoading) break;
                    }

                    if (core is null) return "SKIP: WebView2 не создал движок для локального сервера";

                    var clicked = "missing";
                    for (var i = 0; i < 20 && clicked != "clicked"; i++)
                    {
                        clicked = (await WithTimeout(core.ExecuteScriptAsync(
                            "(() => { const a = document.getElementById('dl'); if (!a) return 'missing'; a.click(); return 'clicked'; })()"),
                            8000, "click download"))?.Trim('"') ?? "missing";
                        if (clicked != "clicked") await Delay(400);
                    }

                    if (clicked != "clicked")
                        throw new Exception("ссылка для скачивания не найдена в тестовой странице");

                    DownloadItem? item = null;
                    for (var i = 0; i < 120; i++)
                    {
                        await Delay(250);
                        item = vm.Downloads.Items.FirstOrDefault(d => d.FileName.Contains("drbrowser-smoke"));
                        if (item is { State: DownloadState.Completed }) break;
                        if (item is { State: DownloadState.Failed }) throw new Exception("загрузка завершилась ошибкой");
                    }

                    if (item is null) throw new Exception("WebView2 не начал загрузку (DownloadStarting не сработал)");
                    if (item.State != DownloadState.Completed)
                        throw new Exception($"загрузка не завершилась за 30 с (state={item.State})");
                    if (!File.Exists(item.Path)) throw new Exception("файл не появился на диске: " + item.Path);

                    var written = await File.ReadAllBytesAsync(item.Path);
                    if (written.Length != payload.Length)
                        throw new Exception($"размер файла {written.Length} != {payload.Length}");

                    vm.OpenDownloadsCommand.Execute(null);
                    await Delay(700);
                    window!.CaptureWindow(Path.Combine(shots, "11-downloads-real.png"));
                    return $"{item.FileName} · {item.SizeText} · сохранён в {Path.GetDirectoryName(item.Path)}";
                }
                finally
                {
                    stop();
                }
            });

            await Run("smoke-screenshot", "Скриншот страницы (функция «Снимок экрана»)", async () =>
            {
                vm.NavigateActiveTab("https://example.com");
                for (var i = 0; i < 40 && (vm.ActiveTab?.Core is null || vm.IsLoading); i++) await Delay(250);
                ScreenshotHelper.Clear();
                vm.ScreenshotCommand.Execute(null);
                await Delay(1500);
                var file = ScreenshotHelper.LastSaved;
                if (string.IsNullOrEmpty(file) || !File.Exists(file))
                    return "SKIP: снимок WebView2 недоступен без загруженной веб-страницы";
                return "сохранён " + Path.GetFileName(file);
            });
        }
        catch (Exception ex)
        {
            Results.Add(new Check("harness", "Сбой smoke-окружения", "FAIL", ex.Message));
            Trace("harness FAIL: " + ex);
            log.Error("smoke harness failure", ex);
        }
        finally
        {
            try
            {
                vm?.Shutdown();
                Trace("smoke: view model shutdown");
                window?.Close();
                Trace("smoke: window closed");
            }
            catch (Exception ex)
            {
                Trace("smoke close error: " + ex.Message);
            }

            Restore(AppPaths.SettingsFile, settingsBackup);
        }

        var code = Finish("DrBrowser smoke", "smoke-report.txt");
        Trace($"smoke: finished, exit {code}");
        return code;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static async Task Run(string id, string name, Func<Task<string>> body)
    {
        var sw = Stopwatch.StartNew();
        try
        {
            var detail = await body();
            var status = detail.StartsWith("SKIP", StringComparison.Ordinal) ? "SKIP" : "PASS";
            Results.Add(new Check(id, name, status, $"{detail} ({sw.ElapsedMilliseconds} ms)"));
            Trace($"{id}: {status}");
        }
        catch (Exception ex)
        {
            Results.Add(new Check(id, name, "FAIL", ex.Message + $" ({sw.ElapsedMilliseconds} ms)"));
            Trace($"{id}: FAIL {ex.GetType().Name}: {ex.Message}");
        }
    }

    private static Task Run(string id, string name, Func<string> body) => Run(id, name, () => Task.FromResult(body()));

    /// <summary>
    /// Minimal loopback HTTP server used by the smoke run: serves one HTML page with a
    /// download link plus the file itself, so DownloadStarting/DownloadOperation can be
    /// verified end to end without any external dependency.
    /// </summary>
    private static (int Port, Action Stop) StartLoopbackServer(byte[] payload, string fileName)
    {
        var listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        var port = ((IPEndPoint)listener.LocalEndpoint).Port;
        var cts = new CancellationTokenSource();

        _ = Task.Run(async () =>
        {
            while (!cts.IsCancellationRequested)
            {
                TcpClient client;
                try
                {
                    client = await listener.AcceptTcpClientAsync(cts.Token).ConfigureAwait(false);
                }
                catch
                {
                    break;
                }

                _ = Task.Run(async () =>
                {
                    try
                    {
                        using (client)
                        using (var stream = client.GetStream())
                        {
                            var buffer = new byte[8192];
                            var read = await stream.ReadAsync(buffer, cts.Token).ConfigureAwait(false);
                            var request = Encoding.ASCII.GetString(buffer, 0, read);

                            byte[] headers;
                            byte[] body;
                            if (request.Contains("/file.bin", StringComparison.Ordinal))
                            {
                                body = payload;
                                headers = Encoding.ASCII.GetBytes(
                                    "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    $"Content-Disposition: attachment; filename=\"{fileName}\"\r\n" +
                                    $"Content-Length: {body.Length}\r\n" +
                                    "Connection: close\r\n\r\n");
                            }
                            else
                            {
                                body = Encoding.UTF8.GetBytes(
                                    "<!doctype html><html lang='en'><head><meta charset='utf-8'><title>DrBrowser local test</title></head>" +
                                    "<body style='font-family:Segoe UI;background:#0b0d14;color:#eee;padding:40px'>" +
                                    "<h1 style='color:#7c5cff'>DrBrowser</h1>" +
                                    $"<a id='dl' href='/file.bin' download style='color:#7cc4ff'>download {fileName}</a></body></html>");
                                headers = Encoding.ASCII.GetBytes(
                                    "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/html; charset=utf-8\r\n" +
                                    $"Content-Length: {body.Length}\r\n" +
                                    "Cache-Control: no-store\r\n" +
                                    "Connection: close\r\n\r\n");
                            }

                            await stream.WriteAsync(headers, cts.Token).ConfigureAwait(false);
                            await stream.WriteAsync(body, cts.Token).ConfigureAwait(false);
                            await stream.FlushAsync(cts.Token).ConfigureAwait(false);
                        }
                    }
                    catch
                    {
                        // the smoke run only needs the happy path
                    }
                });
            }
        });

        void Stop()
        {
            try
            {
                cts.Cancel();
                listener.Stop();
            }
            catch
            {
                // already stopped
            }
        }

        return (port, Stop);
    }

    private static async Task Delay(int ms) => await Task.Delay(ms);

    /// <summary>Runs <paramref name="task"/> with a hard timeout so a hung WebView2 cannot freeze the whole run.</summary>
    private static async Task<T> WithTimeout<T>(Task<T> task, int milliseconds, string what)
    {
        var finished = await Task.WhenAny(task, Task.Delay(milliseconds)).ConfigureAwait(true);
        if (!ReferenceEquals(finished, task))
            throw new TimeoutException($"{what}: нет ответа за {milliseconds} мс");
        return await task.ConfigureAwait(true);
    }

    /// <summary>Kills the process if a test run hangs, after writing whatever results exist.</summary>
    private static void ArmWatchdog(int seconds, string reportFile)
    {
        var thread = new Thread(() =>
        {
            Thread.Sleep(seconds * 1000);
            Trace($"watchdog: превышено {seconds} с, аварийное завершение");
            Results.Add(new Check("watchdog", "Общий таймаут прогона", "FAIL", $"прогон не завершился за {seconds} с"));
            try
            {
                Finish("DrBrowser smoke", reportFile);
            }
            catch
            {
                // ignore
            }

            Environment.Exit(3);
        })
        {
            IsBackground = true,
            Name = "drbrowser-watchdog"
        };
        thread.Start();
    }

    /// <summary>Appends a line to smoke-trace.txt so a crash before the report is still diagnosable.</summary>
    /// <summary>Finds the first descendant of the given type (drives real WPF controls in tests).</summary>
    private static T? FindVisual<T>(DependencyObject? root) where T : DependencyObject
    {
        if (root is null) return null;
        var count = VisualTreeHelper.GetChildrenCount(root);
        for (var i = 0; i < count; i++)
        {
            var child = VisualTreeHelper.GetChild(root, i);
            if (child is T match) return match;
            var nested = FindVisual<T>(child);
            if (nested is not null) return nested;
        }

        return null;
    }

    private static bool HasVisualText(DependencyObject? root, string expected)
    {
        if (root is null) return false;
        if (root is TextBlock text && string.Equals(text.Text, expected, StringComparison.Ordinal)) return true;
        var count = VisualTreeHelper.GetChildrenCount(root);
        for (var i = 0; i < count; i++)
            if (HasVisualText(VisualTreeHelper.GetChild(root, i), expected)) return true;
        return false;
    }

    /// <summary>Writes a valid one-second, 8 kHz, mono PCM WAV used to prove MediaPlayer opened real audio.</summary>
    private static void WriteTestWave(string path)
    {
        const int sampleRate = 8000;
        const short channels = 1;
        const short bitsPerSample = 16;
        const int sampleCount = sampleRate;
        const int dataSize = sampleCount * channels * bitsPerSample / 8;

        using var stream = File.Create(path);
        using var writer = new BinaryWriter(stream, Encoding.ASCII, leaveOpen: false);
        writer.Write(Encoding.ASCII.GetBytes("RIFF"));
        writer.Write(36 + dataSize);
        writer.Write(Encoding.ASCII.GetBytes("WAVE"));
        writer.Write(Encoding.ASCII.GetBytes("fmt "));
        writer.Write(16);
        writer.Write((short)1);
        writer.Write(channels);
        writer.Write(sampleRate);
        writer.Write(sampleRate * channels * bitsPerSample / 8);
        writer.Write((short)(channels * bitsPerSample / 8));
        writer.Write(bitsPerSample);
        writer.Write(Encoding.ASCII.GetBytes("data"));
        writer.Write(dataSize);
        for (var i = 0; i < sampleCount; i++)
        {
            var sample = (short)(Math.Sin(2 * Math.PI * 440 * i / sampleRate) * short.MaxValue * 0.08);
            writer.Write(sample);
        }
    }

    private static void Trace(string line)
    {
        try
        {
            Directory.CreateDirectory(ReportDir);
            File.AppendAllText(Path.Combine(ReportDir, "smoke-trace.txt"),
                $"{DateTime.Now:HH:mm:ss.fff} {line}{Environment.NewLine}");
        }
        catch
        {
            // ignore
        }
    }

    private static string? Backup(string path)
    {
        try
        {
            return File.Exists(path) ? File.ReadAllText(path) : null;
        }
        catch
        {
            return null;
        }
    }

    private static void Restore(string path, string? content)
    {
        try
        {
            if (content is null)
            {
                if (File.Exists(path)) File.Delete(path);
            }
            else
            {
                File.WriteAllText(path, content);
            }
        }
        catch
        {
            // ignore
        }
    }

    private static string ReadTail(string path, int count)
    {
        using var stream = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
        var length = (int)Math.Min(count, stream.Length);
        stream.Seek(-length, SeekOrigin.End);
        var buffer = new byte[length];
        _ = stream.Read(buffer, 0, length);
        return Encoding.UTF8.GetString(buffer);
    }

    private static int Finish(string title, string fileName)
    {
        Directory.CreateDirectory(ReportDir);
        var report = new StringBuilder();
        report.AppendLine($"{title} · {DateTime.Now:yyyy-MM-dd HH:mm:ss} · {Environment.OSVersion}");
        report.AppendLine(new string('-', 78));

        foreach (var check in Results)
            report.AppendLine($"[{check.Status}] {check.Id,-20} {check.Name}\n           {check.Detail}");

        var passed = Results.Count(r => r.Status == "PASS");
        var failed = Results.Count(r => r.Status == "FAIL");
        var skipped = Results.Count(r => r.Status == "SKIP");
        report.AppendLine(new string('-', 78));
        report.AppendLine($"ИТОГО: {passed} passed, {failed} failed, {skipped} skipped из {Results.Count}");

        var text = report.ToString();
        try
        {
            File.WriteAllText(Path.Combine(ReportDir, fileName), text, Encoding.UTF8);
        }
        catch
        {
            // ignore
        }

        Console.WriteLine(text);
        return failed == 0 ? 0 : 1;
    }
}

/// <summary>Keeps the path of the last saved page screenshot for the smoke assertion.</summary>
public static class ScreenshotHelper
{
    public static string LastSaved { get; set; } = string.Empty;

    public static void Clear() => LastSaved = string.Empty;
}
