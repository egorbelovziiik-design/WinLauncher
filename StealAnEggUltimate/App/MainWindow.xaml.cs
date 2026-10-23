using System.Collections.Specialized;
using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Threading;

namespace StealAnEggUltimate;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm;
    private bool _allowClose;
    private bool _exitRequested;

    public MainWindow(MainViewModel viewModel)
    {
        _vm = viewModel;
        InitializeComponent();

        DataContext = viewModel;
        SourceInitialized += OnSourceInitialized;
        Loaded += OnLoaded;

        _vm.ConsoleLines.CollectionChanged += OnConsoleLinesChanged;
        _vm.ActivateHubRequested += (_, _) => RestoreWindow();
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        // Start the ambient background animation.
        var glowA = (System.Windows.Media.TranslateTransform)((System.Windows.Shapes.Ellipse)FindName("GlowA")!).RenderTransform;
        var glowB = (System.Windows.Media.TranslateTransform)((System.Windows.Shapes.Ellipse)FindName("GlowB")!).RenderTransform;

        AnimateTransform(glowA, -30, 40, 60, 14);
        AnimateTransform(glowB, 30, -50, -40, 18);
    }

    private static void AnimateTransform(System.Windows.Media.TranslateTransform transform, double fromX, double toX, double fromY, double seconds)
    {
        var storyboard = new System.Windows.Media.Animation.Storyboard { RepeatBehavior = System.Windows.Media.Animation.RepeatBehavior.Forever, AutoReverse = true };
        var duration = TimeSpan.FromSeconds(Math.Max(4, seconds));

        var x = new System.Windows.Media.Animation.DoubleAnimation(fromX, toX, duration)
        {
            EasingFunction = new System.Windows.Media.Animation.SineEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseInOut }
        };
        var y = new System.Windows.Media.Animation.DoubleAnimation(fromY, fromY + 70, duration)
        {
            EasingFunction = new System.Windows.Media.Animation.SineEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseInOut }
        };

        System.Windows.Media.Animation.Storyboard.SetTarget(x, transform);
        System.Windows.Media.Animation.Storyboard.SetTargetProperty(x, new PropertyPath(System.Windows.Media.TranslateTransform.XProperty));
        System.Windows.Media.Animation.Storyboard.SetTarget(y, transform);
        System.Windows.Media.Animation.Storyboard.SetTargetProperty(y, new PropertyPath(System.Windows.Media.TranslateTransform.YProperty));

        storyboard.Children.Add(x);
        storyboard.Children.Add(y);
        storyboard.Begin();
    }

    private void OnConsoleLinesChanged(object? sender, NotifyCollectionChangedEventArgs e)
    {
        if (e.Action != NotifyCollectionChangedAction.Add || ConsoleList.Items.Count == 0) return;

        Dispatcher.BeginInvoke(new Action(() =>
        {
            try
            {
                ConsoleList.ScrollIntoView(ConsoleList.Items[ConsoleList.Items.Count - 1]);
            }
            catch
            {
                // ignore scroll races
            }
        }), DispatcherPriority.Background);
    }

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        var handle = new WindowInteropHelper(this).Handle;
        HwndSource.FromHwnd(handle)?.AddHook(WndProc);
    }

    /// <summary>Keeps a borderless window inside the monitor work area when maximized.</summary>
    private IntPtr WndProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        const int WM_GETMINMAXINFO = 0x0024;
        if (msg != WM_GETMINMAXINFO) return IntPtr.Zero;

        try
        {
            var monitor = Win32.MonitorFromWindow(hwnd, Win32.MONITOR_DEFAULTTONEAREST);
            var info = new MONITORINFO { cbSize = System.Runtime.InteropServices.Marshal.SizeOf<MONITORINFO>() };
            if (!Win32.GetMonitorInfo(monitor, ref info)) return IntPtr.Zero;

            var mmi = System.Runtime.InteropServices.Marshal.PtrToStructure<MINMAXINFO>(lParam);
            mmi.ptMaxPosition.X = info.rcWork.Left - info.rcMonitor.Left;
            mmi.ptMaxPosition.Y = info.rcWork.Top - info.rcMonitor.Top;
            mmi.ptMaxSize.X = info.rcWork.Right - info.rcWork.Left;
            mmi.ptMaxSize.Y = info.rcWork.Bottom - info.rcWork.Top;
            System.Runtime.InteropServices.Marshal.StructureToPtr(mmi, lParam, true);
            handled = true;
        }
        catch
        {
            // fall back to WPF behaviour
        }

        return IntPtr.Zero;
    }

    [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)]
    private struct POINT
    {
        public int X;
        public int Y;
    }

    [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)]
    private struct MINMAXINFO
    {
        public POINT ptReserved;
        public POINT ptMaxSize;
        public POINT ptMaxPosition;
        public POINT ptMinTrackSize;
        public POINT ptMaxTrackSize;
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
            return;
        }

        try
        {
            DragMove();
        }
        catch
        {
            // called when the mouse was released too early
        }
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Close_Click(object sender, RoutedEventArgs e) => _vm.Shutdown();

    private void OpenSnapshotFolder_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var folder = AppPaths.SnapshotsDir;
            System.IO.Directory.CreateDirectory(folder);
            Process.Start(new ProcessStartInfo { FileName = "explorer.exe", Arguments = $"\"{folder}\"", UseShellExecute = true });
        }
        catch (Exception ex)
        {
            _vm.AddNotice($"Could not open the snapshots folder: {ex.Message}", NoticeKind.Error);
        }
    }

    private void RestoreWindow()
    {
        if (!IsVisible) Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();
        Topmost = true;
        Topmost = false;
    }

    protected override void OnClosing(CancelEventArgs e)
    {
        if (_allowClose)
        {
            base.OnClosing(e);
            return;
        }

        e.Cancel = true;
        if (_exitRequested) return;

        _exitRequested = true;

        // Defer so the first close attempt finishes before the hub tears services down.
        Dispatcher.BeginInvoke(new Action(() => _vm.Shutdown()), DispatcherPriority.Background);
    }

    public void ForceClose()
    {
        _allowClose = true;
        try
        {
            Close();
        }
        catch
        {
            Application.Current?.Shutdown(0);
        }
    }

    protected override void OnClosed(EventArgs e)
    {
        _vm.ConsoleLines.CollectionChanged -= OnConsoleLinesChanged;
        base.OnClosed(e);
        Application.Current?.Shutdown(0);
    }
}
