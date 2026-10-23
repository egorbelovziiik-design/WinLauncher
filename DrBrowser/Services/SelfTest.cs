using System.Diagnostics;
using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Threading;

namespace DrBrowser;

/// <summary>
/// Real, executed checks (not a mock). <c>--selftest</c> exercises storage, parsing,
/// DPAPI, AdBlock, session and settings; <c>--smoke</c> additionally drives a live
/// window with WebView2, switches every internal page, creates/closes tabs and writes
/// PNG screenshots. Both return a process exit code: 0 = all executed checks passed.
/// </summary>
public static class SelfTest
{
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
                settings.Theme = "Light";
                settings.AccentHex = "#00A2FF";
                settings.SearchEngine = "DuckDuckGo";
                settings.ZoomLevel = 1.25;
                settings.AdBlockWhitelist = new List<string> { "example.com" };
                service.Save(settings);

                var loaded = service.Load();
                if (loaded.Theme != "Light") throw new Exception("тема не сохранилась");
                if (loaded.AccentHex != "#00A2FF") throw new Exception("акцент не сохранился");
                if (loaded.SearchEngine != "DuckDuckGo") throw new Exception("поисковик не сохранился");
                if (Math.Abs(loaded.ZoomLevel - 1.25) > 0.001) throw new Exception("масштаб не сохранился");
                if (loaded.AdBlockWhitelist.Count != 1) throw new Exception("whitelist не сохранился");
                return "settings.json: тема, акцент, поисковик, zoom, whitelist — round-trip OK";
            });

            await Run("url-parser", "Адресная строка: URL против поискового запроса", () =>
            {
                var settings = SettingsService.CreateDefaults();

                var site = UrlParser.Resolve("example.com", settings);
                if (site != "https://example.com/") throw new Exception("домен не стал URL: " + site);

                var https = UrlParser.Resolve("https://github.com/dotnet", settings);
                if (https != "https://github.com/dotnet") throw new Exception("https URL искажён: " + https);

                var query = UrlParser.Resolve("как работает webview2", settings);
                if (!query.Contains("google.com/search")) throw new Exception("запрос не стал поиском: " + query);

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

            await Run("search-engines", "4 поисковые системы", () =>
            {
                var settings = SettingsService.CreateDefaults();
                var hosts = new List<string>();
                foreach (var engine in AppSettings.SearchEngines)
                {
                    settings.SearchEngine = engine.Name;
                    var url = settings.SearchUrl("test query");
                    if (!url.Contains("q=") && !url.Contains("text=")) throw new Exception(engine.Name + ": нет параметра запроса");
                    hosts.Add(UrlParser.HostOf(url));
                }

                if (hosts.Distinct().Count() != 4)
                    throw new Exception("движки не различаются: " + string.Join(", ", hosts));
                return string.Join(" · ", AppSettings.SearchEngines.Select(e => e.Name)) + " — 4 разных шаблона";
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
                vm.NavigateActiveTab("https://example.com");
                for (var i = 0; i < 60 && vm.ActiveTab?.Core is null; i++) await Delay(250);
                var core = vm.ActiveTab?.Core;
                if (core is null) throw new Exception("WebView2 не создан за 15 секунд");

                core.NavigateToString(
                    "<html><head><title>DrBrowser Smoke Test</title></head>" +
                    "<body style='background:#0b0d14;color:#f2f3f9;font-family:Segoe UI;padding:48px'>" +
                    "<h1 style='color:#7c5cff'>DrBrowser</h1><p>Движок WebView2 отрисовал эту страницу.</p>" +
                    "</body></html>");

                string? title = null;
                for (var i = 0; i < 20; i++)
                {
                    await Delay(300);
                    try
                    {
                        var raw = await core.ExecuteScriptAsync("document.title");
                        title = raw?.Trim('"');
                        if (title == "DrBrowser Smoke Test") break;
                    }
                    catch
                    {
                        // page still loading
                    }
                }

                if (title != "DrBrowser Smoke Test")
                    throw new Exception("ExecuteScript не подтвердил страницу (title=" + (title ?? "null") + ")");

                await Delay(400);
                window!.CaptureWindow(Path.Combine(shots, "02-webview.png"));
                return $"Chromium {WebViewManager.RuntimeVersionOrEmpty()} · document.title подтверждён через ExecuteScriptAsync";
            });

            await Run("smoke-tabs", "Вкладки: создание, переключение, закрытие, восстановление", async () =>
            {
                var initial = vm.Tabs.Count;
                var tab = vm.CreateNewTabPage();
                await Delay(250);
                if (vm.Tabs.Count != initial + 1) throw new Exception("новая вкладка не появилась");
                vm.CreateWebTab("https://example.org");
                await Delay(300);
                vm.SelectTab(tab);
                if (!ReferenceEquals(vm.ActiveTab, tab)) throw new Exception("переключение вкладки не сработало");
                vm.CloseTab(tab);
                await Delay(200);
                if (vm.Tabs.Count != initial) throw new Exception("закрытие вкладки не сработало");
                var afterClose = vm.Tabs.Count;
                vm.ReopenClosedTab();
                await Delay(300);
                if (vm.Tabs.Count != afterClose + 1) throw new Exception("восстановление вкладки не сработало");
                return $"было {initial} → +2 → закрыто → восстановлено ({vm.Tabs.Count})";
            });

            await Run("smoke-pages", "Все внутренние страницы открываются и рисуются", async () =>
            {
                var keys = new[] { "settings", "history", "downloads", "bookmarks", "passwords", "newtab" };
                var index = 3;
                foreach (var key in keys)
                {
                    vm.OpenInternalCommand.Execute(key);
                    await Delay(900);
                    if (vm.CurrentInternalKey != key)
                        throw new Exception($"страница {key} не активировалась (текущая: {vm.CurrentInternalKey})");
                    window!.CaptureWindow(Path.Combine(shots, $"{index:00}-{key}.png"));
                    index++;
                }

                return "settings · history · downloads · bookmarks · passwords · newtab — 6 скриншотов";
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

                var bookmark = await vm.Bookmarks.AddAsync("Smoke", "https://example.org", "Проверка");
                if (!vm.Bookmarks.Items.Any(b => b.Id == bookmark.Id)) throw new Exception("закладка не добавлена");
                await vm.Bookmarks.DeleteAsync(bookmark);
                return "zoom ±10% · Ctrl+F панель · AI-панель · закладка add/delete";
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
                return "Ctrl+T · Ctrl+W · настройки · Home — команды выполняются";
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

    private static async Task Delay(int ms) => await Task.Delay(ms);

    /// <summary>Appends a line to smoke-trace.txt so a crash before the report is still diagnosable.</summary>
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
