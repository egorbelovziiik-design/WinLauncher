using System.Diagnostics;
using System.Linq;

namespace StealAnEggUltimate;

public sealed record RobloxWindowInfo(
    IntPtr Handle,
    int ProcessId,
    string Title,
    RECT ClientRect,
    RECT VisibleRect,
    double Scale,
    bool IsMinimized,
    bool IsForeground)
{
    public string HandleText => $"0x{Handle.ToInt64():X}";
    public string ResolutionText => $"{ClientRect.Width} × {ClientRect.Height}";
}

/// <summary>
/// Finds a running Roblox player client by enumerating its top-level windows and
/// reading the window geometry. This is passive observation only: no process
/// handle with read/write rights is opened and the client is never modified.
/// </summary>
public sealed class RobloxDetector : IDisposable
{
    private const string RobloxProcessName = "RobloxPlayerBeta";
    private const string StudioProcessName = "RobloxStudioBeta";

    private readonly Logger _log;
    private readonly object _sync = new();
    private Timer? _timer;
    private RobloxWindowInfo? _window;
    private bool _detected;
    private bool _studioDetected;
    private bool _disposed;
    private bool _loggedWindowsNotice;

    public RobloxDetector(Logger log)
    {
        _log = log;
    }

    public bool IsSupported => OperatingSystem.IsWindows();

    public bool IsDetected
    {
        get
        {
            lock (_sync) return _detected;
        }
    }

    public bool IsStudioDetected
    {
        get
        {
            lock (_sync) return _studioDetected;
        }
    }

    public RobloxWindowInfo? CurrentWindow
    {
        get
        {
            lock (_sync) return _window;
        }
    }

    public string StatusText
    {
        get
        {
            if (!IsSupported) return "Roblox Not Found  ·  Windows required";
            lock (_sync)
            {
                if (_detected) return "Roblox Detected";
                return _studioDetected ? "Roblox Not Found  ·  Studio is running" : "Roblox Not Found";
            }
        }
    }

    public event EventHandler? StateChanged;
    public event EventHandler<RobloxWindowInfo>? WindowUpdated;

    public void Start(TimeSpan? interval = null)
    {
        if (_disposed) return;

        if (!IsSupported)
        {
            if (!_loggedWindowsNotice)
            {
                _loggedWindowsNotice = true;
                _log.Warn("Roblox detection needs Windows; running in UI-only mode.");
            }

            return;
        }

        var period = interval ?? TimeSpan.FromMilliseconds(1200);
        _timer ??= new Timer(_ => SafeScan(), null, TimeSpan.Zero, period);
        _log.Info($"Roblox detector started (interval {period.TotalMilliseconds:0} ms).");
    }

    public void Stop()
    {
        _timer?.Dispose();
        _timer = null;
    }

    /// <summary>Forces an immediate synchronous scan (used by the CONNECT button).</summary>
    public RobloxWindowInfo? ScanNow()
    {
        if (!IsSupported) return null;
        SafeScan();
        return CurrentWindow;
    }

    private void SafeScan()
    {
        try
        {
            Scan();
        }
        catch (Exception ex)
        {
            _log.Error("Roblox detection failed.", ex);
        }
    }

    private void Scan()
    {
        var detectedChanged = false;
        var studioChanged = false;

        var playerPids = SafeProcessIds(RobloxProcessName);
        var studioPids = SafeProcessIds(StudioProcessName);
        RobloxWindowInfo? best = null;

        if (playerPids.Count > 0)
        {
            var foreground = Win32.GetForegroundWindow();
            var found = new List<RobloxWindowInfo>();

            Win32.EnumWindows((hWnd, _) =>
            {
                try
                {
                    if (!Win32.IsWindowVisible(hWnd)) return true;

                    Win32.GetWindowThreadProcessId(hWnd, out var pid);
                    if (!playerPids.Contains((int)pid)) return true;

                    var title = Win32.GetTitle(hWnd);
                    var cls = Win32.GetClass(hWnd);
                    if (cls is "ConsoleWindowClass") return true;
                    if (string.IsNullOrWhiteSpace(title)) return true;

                    var client = Win32.GetClientAreaOnScreen(hWnd);
                    if (client.Width < 200 || client.Height < 150) return true;

                    found.Add(new RobloxWindowInfo(
                        hWnd,
                        (int)pid,
                        title,
                        client,
                        Win32.GetVisibleBounds(hWnd),
                        Win32.GetScaleForWindow(hWnd),
                        Win32.IsIconic(hWnd),
                        hWnd == foreground));
                }
                catch
                {
                    // skip malformed windows
                }

                return true;
            }, IntPtr.Zero);

            best = found
                .OrderByDescending(w => w.IsForeground)
                .ThenByDescending(w => w.ClientRect.Width * (long)w.ClientRect.Height)
                .FirstOrDefault();
        }

        lock (_sync)
        {
            if ((_detected) != (best is not null)) detectedChanged = true;
            if (_studioDetected != studioPids.Count > 0) studioChanged = true;

            _detected = best is not null;
            _studioDetected = studioPids.Count > 0;

            var previous = _window;
            _window = best;

            if (best is not null && (previous is null
                || previous.Handle != best.Handle
                || previous.ClientRect.Left != best.ClientRect.Left
                || previous.ClientRect.Top != best.ClientRect.Top
                || previous.ClientRect.Width != best.ClientRect.Width
                || previous.ClientRect.Height != best.ClientRect.Height
                || previous.IsForeground != best.IsForeground))
            {
                WindowUpdated?.Invoke(this, best);
            }
        }

        if (detectedChanged)
        {
            _log.Info(best is null
                ? "Roblox client lost."
                : $"Roblox client found: pid {best.ProcessId}, {best.ResolutionText}, hwnd {best.HandleText}.");
            StateChanged?.Invoke(this, EventArgs.Empty);
        }
        else if (studioChanged)
        {
            StateChanged?.Invoke(this, EventArgs.Empty);
        }
    }

    private static HashSet<int> SafeProcessIds(string name)
    {
        var result = new HashSet<int>();
        try
        {
            foreach (var process in Process.GetProcessesByName(name))
            {
                try
                {
                    if (!process.HasExited) result.Add(process.Id);
                }
                finally
                {
                    process.Dispose();
                }
            }
        }
        catch
        {
            // access denied / process vanished
        }

        return result;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
    }
}
