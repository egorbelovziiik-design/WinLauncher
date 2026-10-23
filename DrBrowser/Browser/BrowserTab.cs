using System.Windows.Controls;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace DrBrowser;

public enum TabKind
{
    Web,
    NewTab,
    Settings,
    History,
    Downloads,
    Bookmarks,
    Passwords
}

/// <summary>One browser tab: model state plus (lazily) its own WebView2 instance.</summary>
public sealed class BrowserTab : ObservableObject, IDisposable
{
    private string _title = "Новая вкладка";
    private string _url = "drbrowser://newtab";
    private bool _isPinned;
    private bool _isMuted;
    private bool _isLoading;
    private bool _isSuspended;
    private bool _canGoBack;
    private bool _canGoForward;
    private string _lastError = string.Empty;
    private TabKind _kind = TabKind.NewTab;
    private bool _webViewReady;
    private bool _isActive;

    public string Id { get; } = Guid.NewGuid().ToString("N");
    public DateTime CreatedAt { get; } = DateTime.Now;
    public DateTime LastActivatedAt { get; set; } = DateTime.Now;

    /// <summary>WebView2 control for this tab (created on first activation).</summary>
    public WebView2? View { get; set; }
    public CoreWebView2? Core => View?.CoreWebView2;
    public bool HasWebView => View is not null;
    public bool IsWebViewReady => _webViewReady;

    /// <summary>URL that was requested before the WebView existed.</summary>
    public string? PendingUrl { get; set; }

    public string Title { get => _title; set { if (Set(ref _title, value)) Raise(nameof(TitleLetter), nameof(DisplayTitle)); } }
    public string Url { get => _url; set { if (Set(ref _url, value)) Raise(nameof(DisplayUrl), nameof(HostText), nameof(TitleLetter)); } }
    public bool IsPinned { get => _isPinned; set => Set(ref _isPinned, value); }
    public bool IsMuted { get => _isMuted; set { if (Set(ref _isMuted, value)) OnPropertyChanged(nameof(MuteGlyph)); } }
    public bool IsLoading { get => _isLoading; set => Set(ref _isLoading, value); }
    public bool IsSuspended { get => _isSuspended; set => Set(ref _isSuspended, value); }
    public bool CanGoBack { get => _canGoBack; set => Set(ref _canGoBack, value); }
    public bool CanGoForward { get => _canGoForward; set => Set(ref _canGoForward, value); }
    public string LastError { get => _lastError; set { if (Set(ref _lastError, value)) OnPropertyChanged(nameof(HasError)); } }
    public bool HasError => !string.IsNullOrWhiteSpace(LastError);

    public TabKind Kind
    {
        get => _kind;
        set { if (Set(ref _kind, value)) Raise(nameof(IsInternal), nameof(IsWeb)); }
    }

    public bool IsInternal => Kind != TabKind.Web;

    /// <summary>True for the tab currently shown in the window (drives the tab styling).</summary>
    public bool IsActive { get => _isActive; set => Set(ref _isActive, value); }
    public bool IsWeb => Kind == TabKind.Web;

    public string HostText => UrlParser.DisplayHost(Url);
    public string DisplayUrl => UrlParser.IsInternal(Url) ? Url : Url;
    public string TitleLetter
    {
        get
        {
            var source = string.IsNullOrWhiteSpace(Title) ? HostText : Title;
            return string.IsNullOrWhiteSpace(source) ? "•" : source.Trim()[..1].ToUpperInvariant();
        }
    }

    public string DisplayTitle => string.IsNullOrWhiteSpace(Title) ? UrlParser.FallbackTitle(Url) : Title;
    public string MuteGlyph => IsMuted ? "🔇" : "🔊";

    public void MarkReady()
    {
        _webViewReady = true;
        OnPropertyChanged(nameof(IsWebViewReady));
    }

    /// <summary>Suspends the renderer of a background tab (WebView2 TrySuspend).</summary>
    public async Task<bool> TrySuspendAsync()
    {
        if (Core is null || IsSuspended) return false;
        try
        {
            var ok = await Core.TrySuspendAsync();
            IsSuspended = ok;
            return ok;
        }
        catch
        {
            return false;
        }
    }

    public async Task ResumeAsync()
    {
        if (Core is null || !IsSuspended) return;
        try
        {
            Core.Resume();
        }
        catch
        {
            // ignored
        }

        IsSuspended = false;
        await Task.CompletedTask;
    }

    public void Dispose()
    {
        try
        {
            View?.Dispose();
        }
        catch
        {
            // ignored
        }

        View = null;
        _webViewReady = false;
    }
}
