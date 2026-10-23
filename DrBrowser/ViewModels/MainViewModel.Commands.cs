using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Input;
using Microsoft.Web.WebView2.Core;

namespace DrBrowser;

public sealed partial class MainViewModel
{
    private readonly Dictionary<string, RelayCommand> _commands = new();

    private ICommand Cmd(string name, Action action)
    {
        if (!_commands.TryGetValue(name, out var command))
        {
            command = new RelayCommand(action);
            _commands[name] = command;
        }

        return command;
    }

    private ICommand Cmd(string name, Action<object?> action)
    {
        var key = name + "#param";
        if (!_commands.TryGetValue(key, out var command))
        {
            command = new RelayCommand(action);
            _commands[key] = command;
        }

        return command;
    }

    // ------------------------------------------------------------------
    // Toolbar / navigation
    // ------------------------------------------------------------------

    public ICommand NewTabCommand => Cmd("newtab", () => CreateNewTabPage());
    public ICommand CloseTabCommand => Cmd("closetab", p => CloseTab(p as BrowserTab));
    public ICommand ReopenTabCommand => Cmd("reopen", ReopenClosedTab);
    public ICommand DuplicateTabCommand => Cmd("duplicate", p => DuplicateTab(p as BrowserTab));
    public ICommand TogglePinCommand => Cmd("pin", p => TogglePin(p as BrowserTab));
    public ICommand ToggleMuteCommand => Cmd("mute", p => ToggleMute(p as BrowserTab));
    public ICommand SelectTabCommand => Cmd("selecttab", p => { if (p is BrowserTab tab) SelectTab(tab); });
    public ICommand CloseOtherTabsCommand => Cmd("closeothers", p =>
    {
        if (p is not BrowserTab keep) return;
        foreach (var tab in Tabs.Where(t => !ReferenceEquals(t, keep) && !t.IsPinned).ToList()) TabManager.Close(tab);
        TabManager.ActiveTab = keep;
        Raise(nameof(TabsSummary));
    });

    public ICommand BackCommand => Cmd("back", GoBack);
    public ICommand ForwardCommand => Cmd("forward", GoForward);
    public ICommand ReloadCommand => Cmd("reload", IsLoading ? StopLoading : Reload);
    public ICommand HomeCommand => Cmd("home", GoHome);
    public ICommand GoCommand => Cmd("go", p => NavigateActiveTab(p as string ?? AddressText));
    public ICommand FocusAddressCommand => Cmd("focusaddress", FocusAddressBar);
    public ICommand CopyUrlCommand => Cmd("copyurl", () =>
    {
        try
        {
            Clipboard.SetText(CurrentUrl);
            StatusMessage = "Адрес скопирован.";
        }
        catch (Exception ex)
        {
            StatusMessage = "Буфер обмена недоступен: " + ex.Message;
        }
    });

    public ICommand UseSuggestionCommand => Cmd("usesuggestion", p => { if (p is SuggestionItem item) UseSuggestion(item); });
    public ICommand HideSuggestionsCommand => Cmd("hidesuggestions", () => { IsAddressEditing = false; });

    public ICommand OpenSettingsCommand => Cmd("settings", () => OpenInternal("settings"));
    public ICommand OpenHistoryCommand => Cmd("history", () => OpenInternal("history"));
    public ICommand OpenDownloadsCommand => Cmd("downloads", () => OpenInternal("downloads"));
    public ICommand OpenBookmarksCommand => Cmd("bookmarks", () => OpenInternal("bookmarks"));
    public ICommand OpenPasswordsCommand => Cmd("passwords", () => OpenInternal("passwords"));
    public ICommand OpenSearchCommand => Cmd("drsearch", () => OpenInternal("search"));
    public ICommand OpenMusicCommand => Cmd("music", () => OpenInternal("music"));

    public ICommand ToggleProfileCommand => Cmd("profile", () => ToggleProfile());
    public ICommand CloseProfileCommand => Cmd("closeprofile", () => CloseProfile());

    public ICommand PlayPauseMusicCommand => Cmd("playpause", () => Music.Service.TogglePlay());

    public ICommand ChooseAvatarCommand => Cmd("chooseavatar", () =>
    {
        var path = _shell?.PickOpenFile("Изображение|*.png;*.jpg;*.jpeg;*.bmp;*.webp");
        if (string.IsNullOrWhiteSpace(path)) return;
        Settings.AvatarPath = path;
        _settingsService.Save(Settings);
        Raise(nameof(AvatarImagePath), nameof(HasCustomAvatar));
        StatusMessage = "Аватар профиля обновлён.";
    });

    public ICommand OpenAiCommand => Cmd("openai", () => IsAiOpen = true);

    public ICommand OpenUrlCommand => Cmd("openurl", p =>
    {
        if (p is string url) NavigateActiveTab(url);
    });

    public ICommand OpenInternalCommand => Cmd("openinternal", p =>
    {
        if (p is string key) OpenInternal(key);
    });

    public void OpenInternal(string key)
    {
        if (string.IsNullOrWhiteSpace(key)) return;
        var tab = ActiveTab ?? CreateNewTabPage();
        OpenInternalInTab(tab, "drbrowser://" + key);
        _ = LoadPageDataAsync(key);
    }

    // ------------------------------------------------------------------
    // Zoom / view
    // ------------------------------------------------------------------

    public ICommand ZoomInCommand => Cmd("zoomin", () => ZoomBy(0.1));
    public ICommand ZoomOutCommand => Cmd("zoomout", () => ZoomBy(-0.1));
    public ICommand ZoomResetCommand => Cmd("zoomreset", ResetZoom);

    public ICommand ToggleFindCommand => Cmd("togglefind", () =>
    {
        IsFindVisible = !IsFindVisible;
        if (!IsFindVisible)
        {
            FindStatus = string.Empty;
            ClearFindHighlight();
        }
    });

    public ICommand FindNextCommand => Cmd("findnext", () => _ = FindInPageAsync(FindText, false));
    public ICommand FindPreviousCommand => Cmd("findprevious", () => _ = FindInPageAsync(FindText, true));

    private string _findStatus = string.Empty;
    public string FindStatus
    {
        get => _findStatus;
        private set => Set(ref _findStatus, value);
    }

    public void FindInPage(string text) => _ = FindInPageAsync(text, false);

    /// <summary>Runs the browser's built-in page search and reports the real result.</summary>
    public async Task<bool> FindInPageAsync(string text, bool backwards)
    {
        if (string.IsNullOrWhiteSpace(text))
        {
            FindStatus = string.Empty;
            return false;
        }

        var tab = ActiveTab;
        if (tab?.Core is null)
        {
            StatusMessage = "Поиск на странице доступен только для веб-страниц.";
            FindStatus = "только для веб-страниц";
            return false;
        }

        try
        {
            var script = $"(function(){{ const ok = window.find({JsonSerializer.Serialize(text)}, false, " +
                         $"{(backwards ? "true" : "false")}, true); return ok ? '1' : '0'; }})()";
            var raw = await tab.Core.ExecuteScriptAsync(script);
            var found = raw.Trim('"') == "1";
            FindStatus = found ? "найдено" : "не найдено";
            StatusMessage = found
                ? $"Поиск: «{text}» — найдено"
                : $"Поиск: «{text}» — совпадений нет";
            if (!found) FindStatus = "не найдено";
            return found;
        }
        catch (Exception ex)
        {
            _log.Error("find in page failed", ex);
            FindStatus = "ошибка поиска";
            StatusMessage = "Поиск на странице не сработал: " + ex.Message;
            return false;
        }
    }

    private void ClearFindHighlight()
    {
        if (ActiveTab?.Core is null) return;
        _ = ActiveTab.Core.ExecuteScriptAsync("window.getSelection().removeAllRanges();");
    }

    public ICommand ToggleReaderCommand => Cmd("reader", ToggleReaderMode);

    public async void ToggleReaderMode()
    {
        var tab = ActiveTab;
        if (tab?.Core is null)
        {
            StatusMessage = "Режим чтения доступен только для веб-страниц.";
            return;
        }

        try
        {
            var dark = Settings.ReaderModeDark ? "1" : "0";
            var script = $"(function(){{ if (window.__drReader) {{ document.documentElement.innerHTML = window.__drReaderOriginal; window.__drReader = false; return 'off'; }}" +
                         $" window.__drReaderOriginal = document.documentElement.innerHTML; window.__drReader = true;" +
                         $" const dark = {dark};" +
                         $" const article = document.querySelector('article, main, [role=main], .article, .post, #content') || document.body;" +
                         $" const text = article.innerText; document.documentElement.innerHTML = '<html><head><meta charset=\"utf-8\"><style>body{{margin:0;background:'+(dark?'#12141f':'#fafafa')+';color:'+(dark?'#e8e9f2':'#1b1e2b')+';font:19px/1.7 Georgia,serif}}#dr{{max-width:760px;margin:48px auto;padding:0 24px;white-space:pre-wrap}}</style></head><body><div id=\"dr\"></div></body></html>';" +
                         $" document.getElementById('dr').textContent = text; return 'on'; }})()";

            var result = await tab.Core.ExecuteScriptAsync(script);
            StatusMessage = result.Contains("off") ? "Режим чтения выключен." : "Режим чтения включён.";
        }
        catch (Exception ex)
        {
            _log.Error("reader mode failed", ex);
            StatusMessage = "Режим чтения не сработал: " + ex.Message;
        }
    }

    public ICommand PictureInPictureCommand => Cmd("pip", async () =>
    {
        if (ActiveTab?.Core is null) return;
        var script = "(function(){ const v=document.querySelector('video'); if(!v) return 'novideo';" +
                     " if (document.pictureInPictureElement) { document.exitPictureInPicture(); return 'closed'; }" +
                     " if (!v.requestPictureInPicture) return 'unsupported';" +
                     " v.requestPictureInPicture().catch(e => console.warn(e)); return 'opened'; })()";
        var result = await ActiveTab.Core.ExecuteScriptAsync(script);
        StatusMessage = result.Contains("opened") ? "Picture-in-Picture включён."
            : result.Contains("closed") ? "Picture-in-Picture выключен."
            : result.Contains("novideo") ? "На странице нет видео."
            : "Движок не поддерживает Picture-in-Picture для этого видео.";
    });

    public ICommand ScreenshotCommand => Cmd("screenshot", TakeScreenshot);

    public async void TakeScreenshot()
    {
        var tab = ActiveTab;
        if (tab?.Core is null)
        {
            StatusMessage = "Скриншот доступен только для веб-страниц.";
            return;
        }

        try
        {
            Directory.CreateDirectory(AppPaths.ScreenshotsDir);
            var name = $"screenshot-{DateTime.Now:yyyyMMdd-HHmmss}.png";
            var path = Path.Combine(AppPaths.ScreenshotsDir, name);

            await using var stream = File.Create(path);
            await tab.Core.CapturePreviewAsync(CoreWebView2CapturePreviewImageFormat.Png, stream);
            ScreenshotHelper.LastSaved = path;
            StatusMessage = $"Скриншот сохранён: {path}";
            _log.Info("screenshot saved: " + path);
        }
        catch (Exception ex)
        {
            _log.Error("screenshot failed", ex);
            StatusMessage = "Скриншот не удался: " + ex.Message;
        }
    }

    public ICommand SavePdfCommand => Cmd("pdf", SavePagePdf);

    public async void SavePagePdf()
    {
        var tab = ActiveTab;
        if (tab?.Core is null || _shell is null)
        {
            StatusMessage = "PDF доступен только для веб-страниц.";
            return;
        }

        var target = _shell.PickSaveFile("PDF|*.pdf", $"{SanitizeName(tab.DisplayTitle)}.pdf", AppPaths.DefaultDownloadsFolder);
        if (string.IsNullOrWhiteSpace(target)) return;

        try
        {
            var ok = await tab.Core.PrintToPdfAsync(target, null);
            StatusMessage = ok ? $"PDF сохранён: {target}" : "Движок не смог напечатать страницу в PDF.";
        }
        catch (Exception ex)
        {
            _log.Error("print to pdf failed", ex);
            StatusMessage = "PDF не удался: " + ex.Message;
        }
    }

    private static string SanitizeName(string name)
    {
        foreach (var invalid in Path.GetInvalidFileNameChars()) name = name.Replace(invalid, '_');
        return string.IsNullOrWhiteSpace(name) ? "page" : name.Length > 80 ? name[..80] : name;
    }

    // ------------------------------------------------------------------
    // Bookmarks
    // ------------------------------------------------------------------

    public ICommand ToggleBookmarkCommand => Cmd("togglebookmark", async () =>
    {
        var tab = ActiveTab;
        if (tab is null || tab.IsInternal)
        {
            StatusMessage = "Внутренние страницы нельзя добавить в закладки.";
            return;
        }

        var existing = Bookmarks.Items.FirstOrDefault(b => b.Url == tab.Url);
        if (existing is not null)
        {
            await Bookmarks.DeleteAsync(existing);
            StatusMessage = "Закладка удалена.";
        }
        else
        {
            await Bookmarks.AddAsync(tab.DisplayTitle, tab.Url);
            StatusMessage = "Добавлено в закладки.";
        }

        Raise(nameof(IsBookmarked));
    });

    public bool IsBookmarked => Bookmarks.Items.Any(b => b.Url == CurrentUrl);

    public ICommand DeleteBookmarkCommand => Cmd("delbookmark", async p =>
    {
        if (p is Bookmark bookmark)
        {
            await Bookmarks.DeleteAsync(bookmark);
            StatusMessage = "Закладка удалена.";
        }
    });

    public ICommand AddBookmarkCommand => Cmd("addbookmark", async () =>
    {
        if (ActiveTab is null || ActiveTab.IsInternal) return;
        await Bookmarks.AddAsync(ActiveTab.DisplayTitle, ActiveTab.Url);
        StatusMessage = "Добавлено в закладки.";
    });

    public ICommand OpenBookmarkCommand => Cmd("openbookmark", p =>
    {
        if (p is Bookmark bookmark) NavigateActiveTab(bookmark.Url);
        else if (p is string url) NavigateActiveTab(url);
    });

    public ICommand ExportBookmarksCommand => Cmd("exportbookmarks", async () =>
    {
        if (_shell is null) return;
        var path = _shell.PickSaveFile("HTML|*.html", "drbrowser-bookmarks.html", AppPaths.DefaultDownloadsFolder);
        if (string.IsNullOrWhiteSpace(path)) return;

        await Bookmarks.ExportHtmlAsync(path);
        StatusMessage = $"Закладки экспортированы: {path}";
    });

    public ICommand ImportBookmarksCommand => Cmd("importbookmarks", async () =>
    {
        if (_shell is null) return;
        var path = _shell.PickOpenFile("HTML|*.html;*.htm");
        if (string.IsNullOrWhiteSpace(path)) return;

        var count = await Bookmarks.ImportHtmlAsync(path);
        StatusMessage = $"Импортировано закладок: {count}.";
    });

    public ICommand ToggleBookmarksBarCommand => Cmd("togglebar", () =>
    {
        Settings.ShowBookmarksBar = !Settings.ShowBookmarksBar;
        Raise(nameof(ShowBookmarksBar));
    });

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    public ICommand DeleteHistoryCommand => Cmd("delhistory", async p =>
    {
        if (p is HistoryEntry entry)
        {
            await History.DeleteAsync(entry.Id);
        }
    });

    public ICommand ClearHistoryCommand => Cmd("clearhistory", async () =>
    {
        if (_shell is null) return;
        if (!await _shell.ConfirmAsync("Очистить историю", "Удалить всю историю посещений? Это действие необратимо.")) return;

        await History.ClearAsync();
        await NewTab.RefreshRecentAsync();
        StatusMessage = "История очищена.";
    });

    public ICommand RefreshHistoryCommand => Cmd("refreshhistory", async () =>
    {
        await History.LoadAsync();
        StatusMessage = "История обновлена.";
    });

    public ICommand OpenHistoryEntryCommand => Cmd("openhistoryentry", p =>
    {
        if (p is HistoryEntry entry) NavigateActiveTab(entry.Url);
        else if (p is VisitedSite site) NavigateActiveTab(site.Url);
    });

    // ------------------------------------------------------------------
    // Downloads
    // ------------------------------------------------------------------

    public ICommand CancelDownloadCommand => Cmd("canceldownload", p => { if (p is DownloadItem item) Downloads.Cancel(item); });
    public ICommand OpenDownloadCommand => Cmd("opendownload", p => { if (p is DownloadItem item) Downloads.OpenFile(item); });
    public ICommand ShowDownloadFolderCommand => Cmd("showdownload", p => { if (p is DownloadItem item) Downloads.ShowInFolder(item); });
    public ICommand ClearDownloadsCommand => Cmd("cleardownloads", () => Downloads.ClearFinished());
    public ICommand OpenDownloadsFolderCommand => Cmd("opendownloadsfolder", () => _shell?.OpenExternal(Downloads.ResolveFolder()));

    // ------------------------------------------------------------------
    // Passwords
    // ------------------------------------------------------------------

    public string NewPasswordSite { get; set; } = string.Empty;
    public string NewPasswordLogin { get; set; } = string.Empty;
    public string NewPasswordValue { get; set; } = string.Empty;

    public ICommand AddPasswordCommand => Cmd("addpassword", async () =>
    {
        if (string.IsNullOrWhiteSpace(NewPasswordSite) || string.IsNullOrWhiteSpace(NewPasswordLogin)
            || string.IsNullOrWhiteSpace(NewPasswordValue))
        {
            StatusMessage = "Заполните сайт, логин и пароль.";
            return;
        }

        await Passwords.AddAsync(NewPasswordSite, NewPasswordLogin, NewPasswordValue);
        StatusMessage = "Запись добавлена (пароль зашифрован DPAPI).";
        NewPasswordSite = NewPasswordLogin = NewPasswordValue = string.Empty;
        Raise(nameof(NewPasswordSite), nameof(NewPasswordLogin), nameof(NewPasswordValue));
    });

    public ICommand DeletePasswordCommand => Cmd("delpassword", async p =>
    {
        if (p is PasswordEntry entry)
        {
            await Passwords.DeleteAsync(entry);
            StatusMessage = "Запись удалена.";
        }
    });

    public ICommand CopyLoginCommand => Cmd("copylogin", p =>
    {
        if (p is PasswordEntry entry)
        {
            try
            {
                Clipboard.SetText(entry.Username);
                StatusMessage = "Логин скопирован.";
            }
            catch (Exception ex)
            {
                StatusMessage = ex.Message;
            }
        }
    });

    public ICommand CopyPasswordCommand => Cmd("copypassword", p =>
    {
        if (p is PasswordEntry entry)
        {
            try
            {
                Clipboard.SetText(entry.Password);
                StatusMessage = "Пароль скопирован в буфер обмена.";
            }
            catch (Exception ex)
            {
                StatusMessage = ex.Message;
            }
        }
    });

    public ICommand GeneratePasswordCommand => Cmd("generatepassword", () =>
    {
        NewPasswordValue = PasswordService.Generate(20);
        Raise(nameof(NewPasswordValue), nameof(GeneratedPasswordStrength));
        StatusMessage = "Сгенерирован пароль на 20 символов.";
    });

    public string GeneratedPasswordStrength => string.IsNullOrEmpty(NewPasswordValue)
        ? string.Empty
        : $"{PasswordService.StrengthText(PasswordService.Strength(NewPasswordValue))} ({NewPasswordValue.Length} симв.)";

    // ------------------------------------------------------------------
    // AdBlock / privacy
    // ------------------------------------------------------------------

    public ICommand ToggleAdBlockCommand => Cmd("toggleadblock", () =>
    {
        Settings.AdBlockEnabled = !Settings.AdBlockEnabled;
        Raise(nameof(AdBlockText));
        StatusMessage = Settings.AdBlockEnabled ? "AdBlock включён." : "AdBlock выключен.";
    });

    public ICommand UpdateFiltersCommand => Cmd("updatefilters", async () =>
    {
        StatusMessage = "Загрузка списка фильтров…";
        var result = await AdBlock.UpdateFromUrlAsync(Settings.AdBlockUpdateUrl);
        await AdBlock.LoadAsync(Settings.AdBlockCustomFilters);
        Raise(nameof(AdBlockText), nameof(AdBlockRuleText));
        StatusMessage = result;
    });

    public string AdBlockRuleText => AdBlock.LastStatus;

    public ICommand ResetAdBlockCounterCommand => Cmd("resetadblock", () =>
    {
        AdBlock.ResetCounters();
        Raise(nameof(AdBlockText));
    });

    public string WhitelistInput { get; set; } = string.Empty;

    public ICommand AddWhitelistCommand => Cmd("addwhitelist", () =>
    {
        var host = (WhitelistInput ?? string.Empty).Trim().ToLowerInvariant();
        if (host.Length == 0) return;

        if (Uri.TryCreate(host, UriKind.Absolute, out var uri)) host = uri.Host;
        if (string.IsNullOrWhiteSpace(host) || Settings.AdBlockWhitelist.Contains(host, StringComparer.OrdinalIgnoreCase)) return;

        Settings.AdBlockWhitelist = new List<string>(Settings.AdBlockWhitelist) { host };
        AdBlock.Configure(Settings.AdBlockEnabled, Settings.AdBlockWhitelist);
        WhitelistInput = string.Empty;
        Raise(nameof(WhitelistInput), nameof(WhitelistText));
        StatusMessage = $"Исключение добавлено: {host}";
    });

    public ICommand RemoveWhitelistCommand => Cmd("removewhitelist", p =>
    {
        if (p is not string host) return;
        var list = Settings.AdBlockWhitelist.Where(h => !h.Equals(host, StringComparison.OrdinalIgnoreCase)).ToList();
        Settings.AdBlockWhitelist = list;
        AdBlock.Configure(Settings.AdBlockEnabled, list);
        Raise(nameof(WhitelistText));
        StatusMessage = $"Исключение удалено: {host}";
    });

    public string WhitelistText => Settings.AdBlockWhitelist.Count == 0
        ? "Исключений нет — AdBlock работает на всех сайтах."
        : $"Исключения: {string.Join(", ", Settings.AdBlockWhitelist)}";

    public ICommand ClearCookiesCommand => Cmd("clearcookies", async () =>
    {
        await ClearBrowsingDataAsync(CoreWebView2BrowsingDataKinds.Cookies);
        StatusMessage = "Cookies очищены.";
    });

    public ICommand ClearCacheCommand => Cmd("clearcache", async () =>
    {
        await ClearBrowsingDataAsync(CoreWebView2BrowsingDataKinds.DiskCache);
        StatusMessage = "Кэш очищен.";
    });

    private async Task ClearBrowsingDataAsync(CoreWebView2BrowsingDataKinds kinds)
    {
        var cores = Tabs.Where(t => t.Core is not null).Select(t => t.Core!).ToList();
        if (cores.Count == 0)
        {
            StatusMessage = "Нет активного WebView2 — очистка невозможна.";
            return;
        }

        foreach (var core in cores)
        {
            try
            {
                await core.Profile.ClearBrowsingDataAsync(kinds);
            }
            catch (Exception ex)
            {
                _log.Error("clear browsing data failed", ex);
                StatusMessage = "Очистка не удалась: " + ex.Message;
            }
        }
    }

    public ICommand ClearPermissionsCommand => Cmd("clearpermissions", () =>
    {
        _db.ClearPermissions();
        StatusMessage = "Разрешения сайтов сброшены.";
    });

    // ------------------------------------------------------------------
    // Window / profile / AI
    // ------------------------------------------------------------------

    public ICommand OpenPrivateWindowCommand => Cmd("privatewindow", () => _shell?.OpenPrivateWindow());
    public ICommand ExitCommand => Cmd("exit", () => _shell?.CloseWindow());
    public ICommand ToggleAiCommand => Cmd("toggleai", () => IsAiOpen = !IsAiOpen);
    public ICommand CloseAiCommand => Cmd("closeai", () => IsAiOpen = false);

    public ICommand ApplyThemeCommand => Cmd("theme", p =>
    {
        if (p is string theme)
        {
            Settings.Theme = theme;
            ThemeManager.Apply(Settings);
            FlushSettings();
        }
    });

    public ICommand SetAccentCommand => Cmd("accent", p =>
    {
        if (p is string hex)
        {
            Settings.AccentHex = hex;
            ThemeManager.Apply(Settings);
            FlushSettings();
        }
    });

    public ICommand SaveAiKeyCommand => Cmd("saveaikey", () =>
    {
        if (string.IsNullOrWhiteSpace(AiKeyInput))
        {
            AiStatus = "Введите API-ключ.";
            return;
        }

        SecretStore.SaveAiKey(AiKeyInput);
        AiKeyInput = string.Empty;
        Raise(nameof(AiKeyInput), nameof(AiKeyStatus));
        AiStatus = "Ключ сохранён в защищённом хранилище Windows (DPAPI).";
        StatusMessage = AiStatus;
    });

    public ICommand ClearAiKeyCommand => Cmd("clearaikey", () =>
    {
        SecretStore.ClearAiKey();
        Raise(nameof(AiKeyStatus));
        AiStatus = "Ключ удалён.";
    });

    public string AiKeyStatus => AI.HasKey
        ? "Ключ настроен и хранится в DPAPI-хранилище."
        : AIService.IsLocalEndpoint(AIService.BaseUrlOf(Settings))
            ? "Ключ не нужен: выбрана локальная модель на этом компьютере."
            : "Ключ не задан — подключите Ollama / LM Studio или вставьте ключ облачного API.";
}
