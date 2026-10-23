using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media.Animation;

namespace StealAnEggUltimate;

/// <summary>
/// Transparent, click-through overlay that sits on top of the Roblox client.
/// The menu is toggled with a global hotkey (Right Shift by default), animated,
/// draggable and remembers its position. When closed the window is click-through
/// so the game keeps every mouse and keyboard input.
/// </summary>
public partial class OverlayWindow : Window
{
    private readonly OverlayViewModel _vm;
    private readonly Logger _log;

    private bool _allowClose;
    private bool _menuOpen;
    private bool _dragging;
    private Point _grabOffset;
    private IntPtr _handle = IntPtr.Zero;

    public OverlayWindow(OverlayViewModel vm, Logger log)
    {
        _vm = vm;
        _log = log;

        InitializeComponent();
        DataContext = vm;

        SourceInitialized += OnSourceInitialized;
        Closing += OnClosing;
        SizeChanged += (_, _) => { };
    }

    public bool IsMenuOpen => _menuOpen;

    public (double Width, double Height) ViewSize => (
        ActualWidth > 40 ? ActualWidth : Width,
        ActualHeight > 40 ? ActualHeight : Height);

    public IntPtr Handle => _handle;

    public event EventHandler? MenuStateChanged;

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        try
        {
            _handle = new System.Windows.Interop.WindowInteropHelper(this).Handle;

            // Keep the overlay out of Alt+Tab and never activate it while the menu is closed.
            var style = (long)Win32.GetWindowLongPtr(_handle, Win32.GWL_EXSTYLE);
            var updated = style | Win32.WS_EX_TOOLWINDOW | Win32.WS_EX_NOACTIVATE;
            Win32.SetWindowLongPtr(_handle, Win32.GWL_EXSTYLE, new IntPtr(updated));
            SetClickThrough(true);
        }
        catch (Exception ex)
        {
            _log.Error("Overlay window init failed.", ex);
        }
    }

    private void OnClosing(object? sender, System.ComponentModel.CancelEventArgs e)
    {
        if (_allowClose) return;
        e.Cancel = true;
        CloseMenu();
    }

    public void CloseFromManager()
    {
        _allowClose = true;
        try
        {
            Close();
        }
        catch
        {
            // ignore
        }
    }

    public void SetClickThrough(bool enabled)
    {
        if (_handle == IntPtr.Zero) return;
        Win32.MakeClickThrough(_handle, enabled);
    }

    public void SetOverlayVisible(bool visible)
        => Visibility = visible ? Visibility.Visible : Visibility.Hidden;

    public void OpenMenu()
    {
        if (_menuOpen || _handle == IntPtr.Zero) return;

        _menuOpen = true;
        MenuRoot.Visibility = Visibility.Visible;
        MenuRoot.IsHitTestVisible = true;

        // Interactive while the menu is open: allow mouse activation so clicking
        // inside the menu focuses it (needed for the command box and mouse wheel).
        var style = (long)Win32.GetWindowLongPtr(_handle, Win32.GWL_EXSTYLE);
        Win32.SetWindowLongPtr(_handle, Win32.GWL_EXSTYLE,
            new IntPtr(style & ~(Win32.WS_EX_TRANSPARENT | Win32.WS_EX_NOACTIVATE)));

        var storyboard = (Storyboard)Resources["MenuOpenStory"];
        storyboard.Begin(this);
        TryActivate();

        MenuStateChanged?.Invoke(this, EventArgs.Empty);
        _log.Debug("Overlay menu opened.");
    }

    public void CloseMenu()
    {
        if (!_menuOpen)
        {
            SetClickThrough(true);
            return;
        }

        _menuOpen = false;
        MenuRoot.IsHitTestVisible = false;

        var storyboard = (Storyboard)Resources["MenuCloseStory"];
        storyboard.Begin(this);

        MenuStateChanged?.Invoke(this, EventArgs.Empty);
        _log.Debug("Overlay menu closed.");
    }

    private void MenuCloseStory_Completed(object? sender, EventArgs e)
    {
        if (_menuOpen) return;
        MenuRoot.Visibility = Visibility.Collapsed;
        SetClickThrough(true);

        // Restore the non-activating, click-through overlay state.
        var style = (long)Win32.GetWindowLongPtr(_handle, Win32.GWL_EXSTYLE);
        Win32.SetWindowLongPtr(_handle, Win32.GWL_EXSTYLE, new IntPtr(style | Win32.WS_EX_NOACTIVATE));
    }

    private void TryActivate()
    {
        try
        {
            Activate();
        }
        catch
        {
            // activation is best effort
        }
    }

    // ---------------- dragging ----------------

    private void Header_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton != MouseButton.Left) return;

        try
        {
            var position = e.GetPosition(MenuCanvas);
            _grabOffset = new Point(position.X - Canvas.GetLeft(MenuRoot), position.Y - Canvas.GetTop(MenuRoot));
            _dragging = true;
            DragHeader.CaptureMouse();
            e.Handled = true;
        }
        catch (Exception ex)
        {
            _log.Error("Overlay drag start failed.", ex);
        }
    }

    private void Header_MouseMove(object sender, MouseEventArgs e)
    {
        if (!_dragging) return;

        var position = e.GetPosition(MenuCanvas);
        _vm.Hub.SetMenuOffset(position.X - _grabOffset.X, position.Y - _grabOffset.Y);
    }

    private void Header_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (!_dragging) return;
        _dragging = false;
        DragHeader.ReleaseMouseCapture();
        _log.Debug($"Menu position saved: {_vm.Hub.MenuOffsetX:0}, {_vm.Hub.MenuOffsetY:0}");
    }

    // ---------------- menu buttons ----------------

    private void CloseMenu_Click(object sender, RoutedEventArgs e) => CloseMenu();

    private void MinimizeMenu_Click(object sender, RoutedEventArgs e) => CloseMenu();

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key is Key.Escape)
        {
            CloseMenu();
            e.Handled = true;
        }
    }
}
