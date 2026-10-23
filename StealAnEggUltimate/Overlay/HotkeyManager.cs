using System.Runtime.InteropServices;
using System.Windows.Input;

namespace StealAnEggUltimate;

/// <summary>
/// Global low-level keyboard observer (WH_KEYBOARD_LL).
///
/// The hook only *observes* keys and always passes them on with CallNextHookEx,
/// so Right Shift still reaches Roblox and the game keeps its normal controls.
/// No key is swallowed, remapped or blocked.
/// </summary>
public sealed class HotkeyManager : IDisposable
{
    private const int WH_KEYBOARD_LL = 13;
    private const int WM_KEYDOWN = 0x0100;
    private const int WM_KEYUP = 0x0101;
    private const int WM_SYSKEYDOWN = 0x0104;
    private const int WM_SYSKEYUP = 0x0105;

    private static readonly HashSet<int> PressedKeys = new();

    private readonly Logger _log;
    private readonly LowLevelKeyboardProc _proc;
    private IntPtr _hook = IntPtr.Zero;
    private bool _running;
    private bool _disposed;

    public HotkeyManager(Logger log)
    {
        _log = log;
        _proc = HookCallback;
    }

    /// <summary>Keys that are allowed in the keybind manager (avoid keys games need for movement).</summary>
    public static readonly IReadOnlyList<string> AllowedKeys = new List<string>
    {
        "RightShift", "LeftShift", "RightCtrl", "LeftCtrl", "RightAlt", "LeftAlt",
        "Insert", "Delete", "Home", "End", "PageUp", "PageDown", "CapsLock", "Scroll",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "OemTilde", "OemOpenBrackets", "OemQuotes"
    };

    public bool IsRunning => _running;

    /// <summary>Raised once per physical key press (auto-repeat filtered).</summary>
    public event EventHandler<Key>? KeyPressed;

    public bool Start()
    {
        if (_disposed) return false;

        if (!OperatingSystem.IsWindows())
        {
            _log.Warn("Global hotkeys need Windows; hotkeys are disabled.");
            return false;
        }

        if (_running) return true;

        try
        {
            _hook = SetWindowsHookEx(WH_KEYBOARD_LL, _proc, GetModuleHandle(null), 0);
            _running = _hook != IntPtr.Zero;
            _log.Info(_running ? "Global keyboard hook installed (observer only)." : "Global keyboard hook could not be installed.");
        }
        catch (Exception ex)
        {
            _running = false;
            _log.Error("Keyboard hook installation failed.", ex);
        }

        return _running;
    }

    public void Stop()
    {
        if (_hook != IntPtr.Zero)
        {
            try
            {
                UnhookWindowsHookEx(_hook);
            }
            catch
            {
                // ignore
            }

            _hook = IntPtr.Zero;
            _log.Info("Global keyboard hook removed.");
        }

        _running = false;
        PressedKeys.Clear();
    }

    private IntPtr HookCallback(int nCode, IntPtr wParam, IntPtr lParam)
    {
        try
        {
            if (nCode >= 0)
            {
                var data = Marshal.PtrToStructure<KbdLlHookStruct>(lParam);
                var vk = (int)data.vkCode;
                var message = wParam.ToInt32();

                if (message is WM_KEYDOWN or WM_SYSKEYDOWN)
                {
                    if (PressedKeys.Add(vk))
                    {
                        var key = KeyInterop.KeyFromVirtualKey(vk);
                        var dispatcher = System.Windows.Application.Current?.Dispatcher;
                        if (dispatcher is not null && !dispatcher.CheckAccess())
                            dispatcher.BeginInvoke(new Action(() => RaiseKey(key)), System.Windows.Threading.DispatcherPriority.Input);
                        else
                            RaiseKey(key);
                    }
                }
                else if (message is WM_KEYUP or WM_SYSKEYUP)
                {
                    PressedKeys.Remove(vk);
                }
            }
        }
        catch
        {
            // never break the input chain
        }

        return CallNextHookEx(_hook, nCode, wParam, lParam);
    }

    private void RaiseKey(Key key)
    {
        try
        {
            KeyPressed?.Invoke(this, key);
        }
        catch (Exception ex)
        {
            _log.Error("Hotkey handler failed.", ex);
        }
    }

    private delegate IntPtr LowLevelKeyboardProc(int nCode, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct KbdLlHookStruct
    {
        public uint vkCode;
        public uint scanCode;
        public uint flags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr SetWindowsHookEx(int idHook, LowLevelKeyboardProc lpfn, IntPtr hMod, uint dwThreadId);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnhookWindowsHookEx(IntPtr hhk);

    [DllImport("user32.dll")]
    private static extern IntPtr CallNextHookEx(IntPtr hhk, int nCode, IntPtr wParam, IntPtr lParam);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string? lpModuleName);

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
    }
}
