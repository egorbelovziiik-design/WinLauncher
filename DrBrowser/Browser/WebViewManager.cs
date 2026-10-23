using System.IO;
using System.Windows.Controls;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace DrBrowser;

/// <summary>
/// Creates and configures one WebView2 per tab and wires every browser-level event:
/// navigation, ad blocking, downloads, new-window requests, site permissions, crash
/// recovery and fullscreen changes. All calls happen on the UI thread.
/// </summary>
public sealed class WebViewManager : IDisposable
{
    private readonly Logger _log;
    private readonly Func<AppSettings> _settings;
    private readonly AdBlockService _adBlock;
    private readonly DownloadService _downloads;
    private readonly Db _db;
    private readonly Func<string, string, Task<bool>> _askPermission;
    private readonly Func<string, string, string, string?> _pickSaveFile;
    private readonly string _userDataFolder;

    private CoreWebView2Environment? _environment;

    public WebViewManager(
        Logger log,
        Func<AppSettings> settings,
        AdBlockService adBlock,
        DownloadService downloads,
        Db db,
        Func<string, string, Task<bool>> askPermission,
        Func<string, string, string, string?> pickSaveFile,
        string userDataFolder)
    {
        _log = log;
        _settings = settings;
        _adBlock = adBlock;
        _downloads = downloads;
        _db = db;
        _askPermission = askPermission;
        _pickSaveFile = pickSaveFile;
        _userDataFolder = userDataFolder;
    }

    public event Action<BrowserTab>? TabStateChanged;
    public event Action<BrowserTab>? NavigationFinished;
    public event Action<BrowserTab, string, bool>? NewWindowRequested;
    public event Action<BrowserTab, string>? TabCrashed;
    public event Action<bool>? FullScreenChanged;
    public event Action<string>? StatusTextChanged;
    public event Action? BlockedRequest;

    public bool IsReady => _environment is not null;

    public string RuntimeVersion { get; private set; } = "не определена";

    /// <summary>Initialises the shared WebView2 environment (browser process not started yet).</summary>
    public async Task<bool> InitializeAsync()
    {
        try
        {
            RuntimeVersion = CoreWebView2Environment.GetAvailableBrowserVersionString() ?? "unknown";
        }
        catch (Exception ex)
        {
            _log.Error("WebView2 Runtime not found", ex);
            return false;
        }

        if (_environment is not null) return true;

        try
        {
            Directory.CreateDirectory(_userDataFolder);
            var extraArgs = Environment.GetEnvironmentVariable("DRBROWSER_BROWSER_ARGS");
            var options = new CoreWebView2EnvironmentOptions(
                string.IsNullOrWhiteSpace(extraArgs)
                    ? "--disable-features=msSmartScreenProtection"
                    : "--disable-features=msSmartScreenProtection " + extraArgs);
            _environment = await CoreWebView2Environment.CreateAsync(null, _userDataFolder, options);
            _log.Info($"WebView2 runtime {RuntimeVersion}, profile {_userDataFolder}");
            return true;
        }
        catch (Exception ex)
        {
            _log.Error("WebView2 environment creation failed", ex);
            return false;
        }
    }

    public static bool IsRuntimeAvailable()
    {
        try
        {
            var version = CoreWebView2Environment.GetAvailableBrowserVersionString();
            return !string.IsNullOrWhiteSpace(version);
        }
        catch
        {
            return false;
        }
    }

    public static string RuntimeVersionOrEmpty()
    {
        try
        {
            return CoreWebView2Environment.GetAvailableBrowserVersionString() ?? string.Empty;
        }
        catch
        {
            return string.Empty;
        }
    }

    public async Task<WebView2?> AttachAsync(BrowserTab tab, Panel host)
    {
        if (tab.View is not null) return tab.View;

        if (_environment is null && !await InitializeAsync()) return null;

        var view = new WebView2
        {
            DefaultBackgroundColor = System.Drawing.Color.FromArgb(255, 13, 14, 22),
            ZoomFactor = Math.Clamp(_settings().ZoomLevel, 0.25, 3.0)
        };

        host.Children.Add(view);
        tab.View = view;

        try
        {
            await view.EnsureCoreWebView2Async(_environment);
        }
        catch (Exception ex)
        {
            _log.Error("EnsureCoreWebView2Async failed", ex);
            tab.LastError = "WebView2 не инициализировался: " + ex.Message;
            try
            {
                host.Children.Remove(view);
            }
            catch
            {
                // ignore
            }

            view.Dispose();
            tab.View = null;
            return null;
        }

        Configure(tab);
        tab.MarkReady();
        TabStateChanged?.Invoke(tab);

        if (!string.IsNullOrWhiteSpace(tab.PendingUrl))
        {
            var pending = tab.PendingUrl!;
            tab.PendingUrl = null;
            try
            {
                tab.Core!.Navigate(pending);
            }
            catch (Exception ex)
            {
                _log.Error("initial navigation failed: " + pending, ex);
            }
        }

        return view;
    }

    private void Configure(BrowserTab tab)
    {
        var core = tab.Core!;
        var settings = core.Settings;

        settings.AreDefaultScriptDialogsEnabled = true;
        settings.AreDefaultContextMenusEnabled = true;
        settings.IsStatusBarEnabled = false;
        settings.IsZoomControlEnabled = true;
        settings.AreDevToolsEnabled = true;
        settings.IsBuiltInErrorPageEnabled = true;
        settings.IsPasswordAutosaveEnabled = false;   // no silent credential capture
        settings.IsGeneralAutofillEnabled = false;
        settings.IsReputationCheckingRequired = false;

        core.NavigationStarting += (_, e) =>
        {
            tab.IsLoading = true;
            tab.LastError = string.Empty;
            StatusTextChanged?.Invoke("Загрузка " + SafeHost(e.Uri) + "…");
            TabStateChanged?.Invoke(tab);
        };

        core.NavigationCompleted += (_, e) =>
        {
            tab.IsLoading = false;
            if (!e.IsSuccess)
            {
                tab.LastError = $"Не удалось загрузить страницу ({e.WebErrorStatus}).";
                _log.Warn($"navigation failed: {tab.Url} :: {e.WebErrorStatus}");
            }
            else
            {
                tab.LastError = string.Empty;
                tab.Url = core.Source ?? tab.Url;
                if (!string.IsNullOrWhiteSpace(core.DocumentTitle)) tab.Title = core.DocumentTitle;
            }

            UpdateHistoryState(tab);
            StatusTextChanged?.Invoke(string.Empty);
            TabStateChanged?.Invoke(tab);
            if (e.IsSuccess) NavigationFinished?.Invoke(tab);
        };

        core.SourceChanged += (_, _) =>
        {
            tab.Url = core.Source ?? tab.Url;
            UpdateHistoryState(tab);
            TabStateChanged?.Invoke(tab);
        };

        core.DocumentTitleChanged += (_, _) =>
        {
            if (!string.IsNullOrWhiteSpace(core.DocumentTitle)) tab.Title = core.DocumentTitle;
            TabStateChanged?.Invoke(tab);
        };

        core.HistoryChanged += (_, _) => UpdateHistoryState(tab);

        core.StatusBarTextChanged += (_, _) => StatusTextChanged?.Invoke(core.StatusBarText ?? string.Empty);

        core.NewWindowRequested += (_, e) =>
        {
            e.Handled = true;
            if (!Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri)) return;
            if (uri.Scheme is not ("http" or "https")) return;
            NewWindowRequested?.Invoke(tab, e.Uri, e.IsUserInitiated);
        };

        core.DownloadStarting += (_, e) =>
        {
            try
            {
                var folder = _downloads.ResolveFolder();
                var suggested = e.ResultFilePath;
                if (!string.IsNullOrWhiteSpace(suggested)) suggested = Path.GetFileName(suggested);

                string? exactPath = null;
                if (_settings().AskForDownloadLocation)
                {
                    exactPath = _pickSaveFile("Все файлы|*.*", suggested ?? "download.bin", folder);
                    if (!string.IsNullOrWhiteSpace(exactPath))
                        folder = Path.GetDirectoryName(exactPath) ?? folder;
                }

                e.Handled = true;
                e.Cancel = false;
                var item = _downloads.Start(e, suggested ?? "download.bin", tab.Url, folder, exactPath);
                StatusTextChanged?.Invoke($"Загрузка: {item.FileName}");
            }
            catch (Exception ex)
            {
                _log.Error("download starting failed", ex);
            }
        };

        core.PermissionRequested += async (_, e) =>
        {
            var deferral = e.GetDeferral();
            try
            {
                var host = SafeHost(e.Uri);
                var kind = e.PermissionKind.ToString();
                var stored = _db.GetPermission(host, kind);

                if (stored is null)
                    stored = await _askPermission(host, DescribePermission(e.PermissionKind));

                if (stored is not null)
                {
                    _db.SetPermission(host, kind, stored.Value);
                    e.State = stored.Value ? CoreWebView2PermissionState.Allow : CoreWebView2PermissionState.Deny;
                }
                else
                {
                    e.State = CoreWebView2PermissionState.Deny;
                }

                e.SavesInProfile = false;
            }
            catch (Exception ex)
            {
                _log.Error("permission handling failed", ex);
                e.State = CoreWebView2PermissionState.Deny;
            }
            finally
            {
                deferral.Complete();
            }
        };

        core.ProcessFailed += (_, e) =>
        {
            tab.IsLoading = false;
            tab.LastError = $"Вкладка остановлена ({e.ProcessFailedKind}). Нажмите «Обновить», чтобы восстановить.";
            _log.Warn($"tab process failed: {e.ProcessFailedKind}");
            TabCrashed?.Invoke(tab, e.ProcessFailedKind.ToString());
            TabStateChanged?.Invoke(tab);
        };

        core.ContainsFullScreenElementChanged += (_, _) =>
            FullScreenChanged?.Invoke(core.ContainsFullScreenElement);

        core.AddWebResourceRequestedFilter("*", CoreWebView2WebResourceContext.All);
        core.WebResourceRequested += (_, e) =>
        {
            try
            {
                if (!_settings().AdBlockEnabled) return;

                var requestUri = e.Request.Uri;
                var pageHost = UrlParser.HostOf(tab.Url);

                if (!_adBlock.ShouldBlock(requestUri, pageHost, out var reason)) return;

                e.Response = _environment!.CreateWebResourceResponse(
                    new MemoryStream(Array.Empty<byte>()), 403, "Blocked by DrBrowser", "Content-Type: text/plain");
                _adBlock.RegisterBlocked(reason);
                BlockedRequest?.Invoke();
            }
            catch (Exception ex)
            {
                _log.Error("adblock request handling failed", ex);
            }
        };

        _log.Info($"WebView2 configured for tab {tab.Id[..6]}");
    }

    private void UpdateHistoryState(BrowserTab tab)
    {
        try
        {
            tab.CanGoBack = tab.Core?.CanGoBack ?? false;
            tab.CanGoForward = tab.Core?.CanGoForward ?? false;
        }
        catch
        {
            // ignore
        }
    }

    private static string SafeHost(string? uri)
        => Uri.TryCreate(uri, UriKind.Absolute, out var parsed) ? parsed.Host : uri ?? string.Empty;

    public static string DescribePermission(CoreWebView2PermissionKind kind) => kind switch
    {
        CoreWebView2PermissionKind.Camera => "камера",
        CoreWebView2PermissionKind.Microphone => "микрофон",
        CoreWebView2PermissionKind.Geolocation => "геолокация",
        CoreWebView2PermissionKind.Notifications => "уведомления",
        CoreWebView2PermissionKind.ClipboardRead => "чтение буфера обмена",
        CoreWebView2PermissionKind.OtherSensors => "датчики устройства",
        CoreWebView2PermissionKind.MultipleAutomaticDownloads => "несколько загрузок подряд",
        CoreWebView2PermissionKind.WindowManagement => "управление окнами",
        _ => kind.ToString()
    };

    public void Dispose()
    {
        _environment = null;
    }
}
