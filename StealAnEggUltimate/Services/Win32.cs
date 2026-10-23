using System.Runtime.InteropServices;
using System.Text;

namespace StealAnEggUltimate;

[StructLayout(LayoutKind.Sequential)]
public struct RECT
{
    public int Left;
    public int Top;
    public int Right;
    public int Bottom;

    public readonly int Width => Math.Max(0, Right - Left);
    public readonly int Height => Math.Max(0, Bottom - Top);
}

[StructLayout(LayoutKind.Sequential)]
public struct POINT
{
    public int X;
    public int Y;
}

[StructLayout(LayoutKind.Sequential)]
public struct MONITORINFO
{
    public int cbSize;
    public RECT rcMonitor;
    public RECT rcWork;
    public uint dwFlags;
}

/// <summary>
/// Thin Win32 interop layer. Only window enumeration / geometry APIs are used:
/// the hub never opens a handle with memory rights, never injects and never
/// modifies the Roblox client.
/// </summary>
public static class Win32
{
    public const int GWL_EXSTYLE = -20;
    public const int WS_EX_TRANSPARENT = 0x00000020;
    public const int WS_EX_TOOLWINDOW = 0x00000080;
    public const int WS_EX_LAYERED = 0x00080000;
    public const int WS_EX_NOACTIVATE = 0x08000000;
    public const uint SWP_NOSIZE = 0x0001;
    public const uint SWP_NOMOVE = 0x0002;
    public const uint SWP_NOZORDER = 0x0004;
    public const uint SWP_NOACTIVATE = 0x0010;
    public const uint SWP_SHOWWINDOW = 0x0040;
    public static readonly IntPtr HWND_TOPMOST = new(-1);
    public static readonly IntPtr HWND_NOTOPMOST = new(-2);

    public const uint MONITOR_DEFAULTTONEAREST = 2;

    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool IsWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool IsIconic(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);

    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    public static extern bool GetClientRect(IntPtr hWnd, out RECT lpRect);

    [DllImport("user32.dll")]
    public static extern bool ClientToScreen(IntPtr hWnd, ref POINT lpPoint);

    [DllImport("user32.dll")]
    public static extern bool SetWindowPos(IntPtr hWnd, IntPtr hWndInsertAfter, int X, int Y, int cx, int cy, uint uFlags);

    [DllImport("user32.dll")]
    public static extern IntPtr GetWindowLongPtr(IntPtr hWnd, int nIndex);

    [DllImport("user32.dll")]
    public static extern IntPtr SetWindowLongPtr(IntPtr hWnd, int nIndex, IntPtr dwNewLong);

    [DllImport("user32.dll")]
    public static extern uint GetDpiForWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern IntPtr MonitorFromWindow(IntPtr hWnd, uint dwFlags);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    [DllImport("dwmapi.dll")]
    public static extern int DwmGetWindowAttribute(IntPtr hwnd, int dwAttribute, out RECT pvAttribute, int cbAttribute);

    /// <summary>DWMWA_EXTENDED_FRAME_BOUNDS — the visible window rect without the invisible resize border.</summary>
    public const int DWMWA_EXTENDED_FRAME_BOUNDS = 9;

    public static string GetTitle(IntPtr hWnd)
    {
        var buffer = new StringBuilder(512);
        GetWindowText(hWnd, buffer, buffer.Capacity);
        return buffer.ToString();
    }

    public static string GetClass(IntPtr hWnd)
    {
        var buffer = new StringBuilder(256);
        GetClassName(hWnd, buffer, buffer.Capacity);
        return buffer.ToString();
    }

    /// <summary>Real visible bounds (falls back to GetWindowRect when DWM is unavailable).</summary>
    public static RECT GetVisibleBounds(IntPtr hWnd)
    {
        try
        {
            if (DwmGetWindowAttribute(hWnd, DWMWA_EXTENDED_FRAME_BOUNDS, out var bounds, Marshal.SizeOf<RECT>()) == 0
                && bounds.Width > 0 && bounds.Height > 0)
            {
                return bounds;
            }
        }
        catch
        {
            // dwmapi missing (very old Windows) -> fall through
        }

        return GetWindowRect(hWnd, out var rect) ? rect : default;
    }

    /// <summary>Screen-space client area (the drawable game viewport).</summary>
    public static RECT GetClientAreaOnScreen(IntPtr hWnd)
    {
        if (!GetClientRect(hWnd, out var client)) return default;

        var topLeft = new POINT { X = client.Left, Y = client.Top };
        if (!ClientToScreen(hWnd, ref topLeft)) return default;

        return new RECT
        {
            Left = topLeft.X,
            Top = topLeft.Y,
            Right = topLeft.X + client.Width,
            Bottom = topLeft.Y + client.Height
        };
    }

    public static double GetScaleForWindow(IntPtr hWnd)
    {
        try
        {
            var dpi = GetDpiForWindow(hWnd);
            if (dpi >= 48) return dpi / 96.0;
        }
        catch
        {
            // older OS
        }

        return 1.0;
    }

    public static RECT GetMonitorWorkArea(IntPtr hWnd)
    {
        try
        {
            var monitor = MonitorFromWindow(hWnd, MONITOR_DEFAULTTONEAREST);
            if (monitor != IntPtr.Zero)
            {
                var info = new MONITORINFO { cbSize = Marshal.SizeOf<MONITORINFO>() };
                if (GetMonitorInfo(monitor, ref info)) return info.rcWork;
            }
        }
        catch
        {
            // ignore
        }

        return new RECT { Left = 0, Top = 0, Right = 1920, Bottom = 1080 };
    }

    public static void MakeClickThrough(IntPtr hWnd, bool enabled)
    {
        try
        {
            var style = (long)GetWindowLongPtr(hWnd, GWL_EXSTYLE);
            var updated = enabled
                ? style | WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW
                : style & ~(WS_EX_TRANSPARENT | WS_EX_NOACTIVATE);
            if (updated != style) SetWindowLongPtr(hWnd, GWL_EXSTYLE, new IntPtr(updated));
        }
        catch
        {
            // style changes are best effort
        }
    }
}
