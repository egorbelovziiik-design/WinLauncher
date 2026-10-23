namespace StealAnEggUltimate;

/// <summary>
/// Owns the lifetime and geometry of the overlay window: attaches it to the Roblox
/// client area, keeps it on top, hides it while the client is minimised and closes
/// it cleanly on disconnect / game exit.
/// </summary>
public sealed class OverlayManager : IDisposable
{
    private readonly MainViewModel _hub;
    private readonly Logger _log;
    private readonly RobloxDetector _detector;

    private OverlayWindow? _window;
    private RobloxWindowInfo? _target;
    private DateTime _lastTopmost = DateTime.MinValue;
    private bool _disposed;

    public OverlayManager(MainViewModel hub, Logger log, RobloxDetector detector)
    {
        _hub = hub;
        _log = log;
        _detector = detector;
    }

    public bool IsMenuOpen => _window?.IsMenuOpen ?? false;

    /// <summary>The live overlay window (diagnostics / smoke captures only).</summary>
    public OverlayWindow? Window => _window;

    /// <summary>
    /// True when the overlay is attached to the desktop instead of a Roblox client
    /// (no client running). Everything is usable, but it is explicitly a preview.
    /// </summary>
    public bool IsPreview { get; private set; }

    public (double Width, double Height) ViewSize => _window?.ViewSize ?? (1280.0, 720.0);

    public string Summary => _window is null
        ? "Overlay is not attached."
        : IsPreview
            ? $"Attached to the desktop · PREVIEW · {(_window.IsMenuOpen ? "menu open" : "menu hidden")}"
            : _target is null
                ? "Overlay is not attached."
                : $"Attached · {_target.ResolutionText} · pid {_target.ProcessId} · {(_window.IsMenuOpen ? "menu open" : "menu hidden")}";

    public event EventHandler? MenuStateChanged;

    public void Attach(RobloxWindowInfo window)
    {
        _target = window;
        IsPreview = false;

        var created = EnsureWindow();
        _window!.Width = Math.Max(320, window.ClientRect.Width / Math.Max(1, window.Scale));
        _window.Height = Math.Max(240, window.ClientRect.Height / Math.Max(1, window.Scale));

        if (created) _window.Show();
        else if (!_window.IsVisible) _window.Show();

        _window.SetOverlayVisible(true);
        ApplySettings();
        PositionWindow(window, force: true);
        _log.Info($"Overlay attached to hwnd {window.HandleText} ({window.ResolutionText}).");
    }

    /// <summary>
    /// Attaches the very same overlay to the primary monitor when no Roblox client is
    /// running, so the menu, the demo ESP, dragging and every hotkey can be used and
    /// verified without the game. All data is demo/snapshot data — nothing is sent to
    /// any game process.
    /// </summary>
    public void AttachPreview()
    {
        IsPreview = true;
        _target = null;

        var area = Win32.GetMonitorWorkArea(IntPtr.Zero);
        if (area.Width < 320 || area.Height < 240)
            area = new RECT { Left = 0, Top = 0, Right = 1280, Bottom = 720 };

        var created = EnsureWindow();
        _window!.Width = area.Width;
        _window.Height = area.Height;

        if (created || !_window.IsVisible) _window.Show();
        _window.SetOverlayVisible(true);
        ApplySettings();
        PositionRect(area, force: true);
        _log.Info($"Overlay attached to the desktop in PREVIEW mode ({area.Width}x{area.Height}).");
    }

    /// <summary>Creates the overlay window on first use; returns true when it was created now.</summary>
    private bool EnsureWindow()
    {
        if (_window is not null) return false;

        var vm = new OverlayViewModel(_hub);
        _window = new OverlayWindow(vm, _log) { ShowActivated = false };
        _window.MenuStateChanged += (_, _) =>
        {
            if (!IsMenuOpen) FocusGame();
            MenuStateChanged?.Invoke(this, EventArgs.Empty);
        };

        return true;
    }

    public void UpdateTargetWindow(RobloxWindowInfo window)
    {
        if (_window is null) return;
        _target = window;
        PositionWindow(window, force: true);
    }

    public void ApplySettings()
    {
        if (_window is null) return;

        var settings = _hub.Settings;
        _window.Opacity = Math.Clamp(settings.Overlay.Opacity, 0.25, 1.0);
    }

    public void Tick(TimeSpan delta)
    {
        if (_window is null || _disposed) return;

        if (IsPreview)
        {
            // No client to follow: keep the desktop overlay visible and on top.
            if ((DateTime.UtcNow - _lastTopmost).TotalSeconds > 1.5)
            {
                _lastTopmost = DateTime.UtcNow;
                EnsureTopmost();
            }

            _ = delta;
            return;
        }

        var target = _detector.CurrentWindow;
        if (target is null) return;

        if (_target is null || target.Handle != _target.Handle)
        {
            _target = target;
            PositionWindow(target, force: true);
        }
        else if (target.ClientRect.Left != _target.ClientRect.Left
                 || target.ClientRect.Top != _target.ClientRect.Top
                 || target.ClientRect.Width != _target.ClientRect.Width
                 || target.ClientRect.Height != _target.ClientRect.Height
                 || target.IsMinimized != _target.IsMinimized)
        {
            _target = target;
            PositionWindow(target, force: false);
        }

        _window.SetOverlayVisible(!target.IsMinimized);

        if ((DateTime.UtcNow - _lastTopmost).TotalSeconds > 1.5)
        {
            _lastTopmost = DateTime.UtcNow;
            EnsureTopmost();
        }

        _ = delta;
    }

    public void ShowMenu() => _window?.OpenMenu();

    public void HideMenu() => _window?.CloseMenu();

    public void ToggleMenu()
    {
        if (_window is null) return;
        if (_window.IsMenuOpen) _window.CloseMenu();
        else _window.OpenMenu();
    }

    private void PositionWindow(RobloxWindowInfo window, bool force)
    {
        if (_window is null) return;

        var rect = window.ClientRect;
        if (rect.Width < 200 || rect.Height < 150)
        {
            var work = Win32.GetMonitorWorkArea(_window.Handle);
            rect = new RECT
            {
                Left = work.Left + 40,
                Top = work.Top + 40,
                Right = work.Right - 40,
                Bottom = work.Bottom - 40
            };
        }

        PositionRect(rect, force);
    }

    private void PositionRect(RECT rect, bool force)
    {
        if (_window is null) return;

        var handle = _window.Handle;
        if (handle == IntPtr.Zero) return;

        try
        {
            Win32.SetWindowPos(handle, Win32.HWND_TOPMOST, rect.Left, rect.Top, rect.Width, rect.Height,
                Win32.SWP_NOACTIVATE | Win32.SWP_SHOWWINDOW);

            if (force)
                _log.Debug($"Overlay positioned at {rect.Left},{rect.Top} size {rect.Width}x{rect.Height}.");
        }
        catch (Exception ex)
        {
            _log.Error("Could not position the overlay window.", ex);
        }
    }

    private void EnsureTopmost()
    {
        if (_window is null || _window.Handle == IntPtr.Zero) return;
        try
        {
            Win32.SetWindowPos(_window.Handle, Win32.HWND_TOPMOST, 0, 0, 0, 0,
                Win32.SWP_NOMOVE | Win32.SWP_NOSIZE | Win32.SWP_NOACTIVATE);
        }
        catch
        {
            // best effort
        }
    }

    private void FocusGame()
    {
        if (_target is null) return;
        try
        {
            Win32.SetForegroundWindow(_target.Handle);
        }
        catch
        {
            // ignore
        }
    }

    public void Detach()
    {
        if (_window is null) return;

        var window = _window;
        _window = null;
        IsPreview = false;
        _target = null;
        try
        {
            window.CloseFromManager();
        }
        catch (Exception ex)
        {
            _log.Error("Overlay window close failed.", ex);
        }

        _log.Info("Overlay detached.");
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Detach();
    }
}
