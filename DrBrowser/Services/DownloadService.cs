using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Windows.Threading;
using Microsoft.Web.WebView2.Core;

namespace DrBrowser;

/// <summary>
/// Owns the download list. Progress comes from WebView2's DownloadOperation and is
/// polled on the UI thread; every finished download is persisted to SQLite.
/// </summary>
public sealed class DownloadService
{
    private readonly Logger _log;
    private readonly Db _db;
    private readonly Func<AppSettings> _settings;
    private readonly DispatcherTimer _timer;
    private readonly Dictionary<DownloadItem, DateTime> _lastSample = new();
    private readonly Dictionary<DownloadItem, long> _lastBytes = new();

    public DownloadService(Logger log, Db db, Func<AppSettings> settings)
    {
        _log = log;
        _db = db;
        _settings = settings;

        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(400) };
        _timer.Tick += (_, _) => Poll();
        _timer.Start();
    }

    public ObservableCollection<DownloadItem> Items { get; } = new();

    public event EventHandler? Changed;

    public string ResolveFolder()
    {
        var folder = _settings().DownloadsFolder;
        if (string.IsNullOrWhiteSpace(folder)) folder = AppPaths.DefaultDownloadsFolder;
        Directory.CreateDirectory(folder);
        return folder;
    }

    /// <summary>
    /// Called from CoreWebView2.DownloadStarting. The default WebView2 download shelf is
    /// suppressed (Handled = true) because the browser shows its own manager; the target
    /// path is chosen here (settings folder or a Save dialog).
    /// </summary>
    public DownloadItem Start(CoreWebView2DownloadStartingEventArgs args, string suggestedName, string url, string folder, string? exactPath = null)
    {
        var safeName = SanitizeFileName(string.IsNullOrWhiteSpace(suggestedName) ? "download.bin" : suggestedName);
        var path = string.IsNullOrWhiteSpace(exactPath)
            ? UniquePath(System.IO.Path.Combine(folder, safeName))
            : exactPath!;
        safeName = System.IO.Path.GetFileName(path);

        try
        {
            args.ResultFilePath = path;
            args.Handled = true;
        }
        catch (Exception ex)
        {
            _log.Warn("could not override download path: " + ex.Message);
        }

        var operation = args.DownloadOperation;
        var item = new DownloadItem
        {
            FileName = safeName,
            Path = path,
            Url = url,
            StartedAt = DateTime.Now,
            Operation = operation,
            TotalBytes = operation.TotalBytesToReceive < 0 ? 0 : operation.TotalBytesToReceive
        };

        operation.StateChanged += (_, _) => OnStateChanged(item, operation);
        operation.BytesReceivedChanged += (_, _) =>
        {
            item.ReceivedBytes = operation.BytesReceived;
            if (operation.TotalBytesToReceive > 0) item.TotalBytes = operation.TotalBytesToReceive;
        };

        Items.Insert(0, item);
        _lastSample[item] = DateTime.UtcNow;
        _lastBytes[item] = 0;

        try
        {
            item.Id = _db.AddDownload(item);
        }
        catch (Exception ex)
        {
            _log.Error("download record failed", ex);
        }

        _log.Info($"download started: {safeName} -> {path}");
        Changed?.Invoke(this, EventArgs.Empty);
        return item;
    }

    private void OnStateChanged(DownloadItem item, CoreWebView2DownloadOperation operation)
    {
        switch (operation.State)
        {
            case CoreWebView2DownloadState.Completed:
                item.State = DownloadState.Completed;
                item.ReceivedBytes = operation.BytesReceived;
                if (operation.TotalBytesToReceive > 0) item.TotalBytes = operation.TotalBytesToReceive;
                break;

            case CoreWebView2DownloadState.Interrupted:
                item.State = DownloadState.Failed;
                break;

            default:
                return;
        }

        Persist(item);
        Changed?.Invoke(this, EventArgs.Empty);
    }

    private void Persist(DownloadItem item)
    {
        try
        {
            _db.UpdateDownloadState(item.Id, item.State, item.TotalBytes);
        }
        catch (Exception ex)
        {
            _log.Error("download state persist failed", ex);
        }
    }

    private void Poll()
    {
        foreach (var item in Items.Where(i => i.IsActive))
        {
            if (item.Operation is not CoreWebView2DownloadOperation operation) continue;

            item.ReceivedBytes = operation.BytesReceived;
            if (operation.TotalBytesToReceive > 0) item.TotalBytes = operation.TotalBytesToReceive;

            var now = DateTime.UtcNow;
            if (_lastSample.TryGetValue(item, out var last) && (now - last).TotalMilliseconds > 300)
            {
                var delta = item.ReceivedBytes - _lastBytes[item];
                var seconds = (now - last).TotalSeconds;
                item.SpeedBytesPerSecond = seconds > 0 ? delta / seconds : 0;
                _lastSample[item] = now;
                _lastBytes[item] = item.ReceivedBytes;
            }
        }
    }

    public void Cancel(DownloadItem item)
    {
        try
        {
            if (item.Operation is CoreWebView2DownloadOperation operation && item.IsActive)
                operation.Cancel();

            item.State = DownloadState.Cancelled;
            Persist(item);
            Changed?.Invoke(this, EventArgs.Empty);
        }
        catch (Exception ex)
        {
            _log.Error("download cancel failed", ex);
        }
    }

    public void OpenFile(DownloadItem item)
    {
        try
        {
            if (File.Exists(item.Path)) Process.Start(new ProcessStartInfo(item.Path) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            _log.Error("open download failed", ex);
        }
    }

    public void ShowInFolder(DownloadItem item)
    {
        try
        {
            var folder = Path.GetDirectoryName(item.Path);
            if (!string.IsNullOrEmpty(folder) && Directory.Exists(folder))
                Process.Start("explorer.exe", File.Exists(item.Path) ? $"/select,\"{item.Path}\"" : $"\"{folder}\"");
        }
        catch (Exception ex)
        {
            _log.Error("show download folder failed", ex);
        }
    }

    public void ClearFinished()
    {
        foreach (var item in Items.Where(i => !i.IsActive).ToList()) Items.Remove(item);
        Changed?.Invoke(this, EventArgs.Empty);
    }

    public List<(string FileName, string Path, long Size, string State, DateTime Started)> History()
    {
        try
        {
            return _db.QueryDownloads();
        }
        catch (Exception ex)
        {
            _log.Error("download history failed", ex);
            return new List<(string, string, long, string, DateTime)>();
        }
    }

    private static string SanitizeFileName(string name)
    {
        foreach (var invalid in Path.GetInvalidFileNameChars()) name = name.Replace(invalid, '_');
        return name.Length > 120 ? name[..120] : name;
    }

    private static string UniquePath(string path)
    {
        if (!File.Exists(path)) return path;

        var dir = Path.GetDirectoryName(path) ?? string.Empty;
        var name = Path.GetFileNameWithoutExtension(path);
        var ext = Path.GetExtension(path);

        for (var i = 1; i < 1000; i++)
        {
            var candidate = Path.Combine(dir, $"{name} ({i}){ext}");
            if (!File.Exists(candidate)) return candidate;
        }

        return path;
    }

    public void Shutdown() => _timer.Stop();
}
