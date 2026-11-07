"""Windows kiosk lock for the duration of the show.

While the prank runs, the window must stay in front no matter what the victim
presses.  The lock combines three mechanisms:

* a **low-level keyboard hook** that swallows ``Alt+Tab``, ``Alt+Esc``,
  ``Ctrl+Esc``, ``Win`` and ``Tab`` (``Alt+F4`` stays available on purpose, so
  the show can always be closed cleanly);
* a **focus watchdog** that re-asserts ``TOPMOST`` + foreground every 250 ms,
  so even if a switch slips through the window comes right back;
* optional **cursor clipping** so the mouse cannot wander to another monitor.

It is deliberately *not* absolute: holding ``Esc`` for two seconds always
exits and restores the system, and ``Ctrl+Alt+Del`` is reserved by Windows and
is never intercepted.  Everything is undone in :meth:`stop`.
"""

from __future__ import annotations

import ctypes
import logging
import sys
import threading
from ctypes import wintypes
from typing import Optional

LOG = logging.getLogger(__name__)

IS_WINDOWS = sys.platform == "win32"

# --- Win32 constants ------------------------------------------------------ #
WH_KEYBOARD_LL = 13
HC_ACTION = 0
WM_QUIT = 0x0012
SW_MAXIMIZE = 3
HWND_TOPMOST = -1
SWP_NOSIZE = 0x0001
SWP_NOMOVE = 0x0002
SWP_SHOWWINDOW = 0x0040

VK_TAB = 0x09
VK_ESCAPE = 0x1B
VK_CONTROL = 0x11
VK_MENU = 0x12
VK_LMENU = 0xA4
VK_RMENU = 0xA5
VK_LWIN = 0x5B
VK_RWIN = 0x5C
VK_F4 = 0x73

BLOCKED_ALWAYS = (VK_TAB, VK_LWIN, VK_RWIN)


class KBDLLHOOKSTRUCT(ctypes.Structure):
    """Low-level keyboard event structure."""

    _fields_ = [
        ("vkCode", wintypes.DWORD),
        ("scanCode", wintypes.DWORD),
        ("flags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.c_size_t),
    ]


class KioskLock:
    """Keep MADorigg in front and block window switching (Windows only)."""

    def __init__(
        self,
        hwnd: Optional[int] = None,
        enabled: bool = True,
        block_keys: bool = True,
        clip_cursor: bool = True,
    ) -> None:
        self.hwnd = int(hwnd) if hwnd else None
        self.enabled = bool(enabled) and IS_WINDOWS
        self.block_keys = block_keys
        self.clip_cursor = clip_cursor
        self.active = False

        self._user32 = ctypes.WinDLL("user32", use_last_error=True) if IS_WINDOWS else None
        self._kernel32 = ctypes.WinDLL("kernel32", use_last_error=True) if IS_WINDOWS else None
        self._hook = None
        self._hook_proc = None
        self._hook_thread: Optional[threading.Thread] = None
        self._hook_thread_id = 0
        self._focus_thread: Optional[threading.Thread] = None
        self._stop = threading.Event()
        self._cursor_clipped = False

        if not self.enabled:
            LOG.info("Kiosk lock disabled (platform or configuration)")

    # ------------------------------------------------------------------ #
    # Public API
    # ------------------------------------------------------------------ #
    def start(self) -> bool:
        """Engage the lock.  Returns ``True`` if anything is actually locked."""
        if not self.enabled:
            return False
        try:
            if self.block_keys:
                self._start_hook()
            self._focus_thread = threading.Thread(
                target=self._focus_loop, name="madorigg-focus", daemon=True
            )
            self._focus_thread.start()
            self._clip_cursor()
            self.active = True
            LOG.info(
                "Kiosk lock engaged (hwnd=%s, hook=%s)", self.hwnd, bool(self._hook)
            )
        except BaseException as exc:  # pragma: no cover - Windows only
            LOG.warning("Kiosk lock failed to start: %s", exc)
            self.active = False
        return self.active

    def stop(self) -> None:
        """Release every lock and restore normal window behaviour."""
        if not self.enabled:
            return
        self._stop.set()
        try:
            if self._hook_thread_id and self._user32 is not None:
                self._user32.PostThreadMessageW(self._hook_thread_id, WM_QUIT, 0, 0)
        except BaseException:
            pass
        thread = self._hook_thread
        if thread is not None and thread.is_alive():
            thread.join(timeout=1.0)
        try:
            if self._hook and self._user32 is not None:  # pragma: no cover
                self._user32.UnhookWindowsHookEx(self._hook)
                self._hook = None
        except BaseException:
            pass
        try:
            if self._user32 is not None:  # pragma: no cover
                self._user32.ClipCursor(None)
        except BaseException:
            pass
        self._cursor_clipped = False
        focus = self._focus_thread
        if focus is not None and focus.is_alive():
            focus.join(timeout=1.0)
        try:
            if self.hwnd and self._user32 is not None:  # pragma: no cover
                self._user32.SetWindowPos(
                    self.hwnd, -2, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE
                )
        except BaseException:
            pass
        self.active = False
        LOG.info("Kiosk lock released")

    def __enter__(self) -> "KioskLock":
        self.start()
        return self

    def __exit__(self, *exc_info) -> None:
        self.stop()

    # ------------------------------------------------------------------ #
    # Keyboard hook
    # ------------------------------------------------------------------ #
    def _start_hook(self) -> None:
        """Install the low-level hook on its own message-pumping thread."""
        if not self.enabled:
            return
        self._hook_thread = threading.Thread(
            target=self._hook_loop, name="madorigg-kbd-hook", daemon=True
        )
        self._hook_thread.start()

    def _hook_loop(self) -> None:  # pragma: no cover - Windows only
        user32 = self._user32
        kernel32 = self._kernel32
        proc_type = ctypes.WINFUNCTYPE(
            ctypes.c_ssize_t, ctypes.c_int, ctypes.c_size_t, ctypes.c_void_p
        )
        self._hook_proc = proc_type(self._callback)
        user32.SetWindowsHookExW.restype = ctypes.c_void_p
        user32.CallNextHookEx.restype = ctypes.c_ssize_t
        user32.CallNextHookEx.argtypes = [
            ctypes.c_void_p, ctypes.c_int, ctypes.c_size_t, ctypes.c_void_p
        ]
        self._hook = user32.SetWindowsHookExW(
            WH_KEYBOARD_LL, self._hook_proc, kernel32.GetModuleHandleW(None), 0
        )
        if not self._hook:
            LOG.warning("SetWindowsHookEx failed: %s", ctypes.get_last_error())
            return
        self._hook_thread_id = kernel32.GetCurrentThreadId()
        LOG.info("Keyboard hook installed (thread %s)", self._hook_thread_id)

        message = wintypes.MSG()
        while user32.GetMessageW(ctypes.byref(message), None, 0, 0) > 0:
            user32.TranslateMessage(ctypes.byref(message))
            user32.DispatchMessageW(ctypes.byref(message))

        try:
            user32.UnhookWindowsHookEx(self._hook)
        finally:
            self._hook = None
            LOG.info("Keyboard hook removed")

    def _callback(self, code: int, w_param: int, l_param: int) -> int:  # pragma: no cover
        user32 = self._user32
        if code == HC_ACTION and self._should_block(int(w_param), l_param):
            return 1
        return user32.CallNextHookEx(None, code, w_param, l_param)

    def _should_block(self, w_param: int, l_param: int) -> bool:  # pragma: no cover
        # Only swallow key-down events, never key-up: that way stuck-modifier
        # states cannot happen if the lock is stopped mid-press.
        if w_param not in (0x0100, 0x0104):  # WM_KEYDOWN, WM_SYSKEYDOWN
            return False
        try:
            info = ctypes.cast(l_param, ctypes.POINTER(KBDLLHOOKSTRUCT)).contents
            vk = int(info.vkCode)
        except BaseException:
            return False

        if vk in BLOCKED_ALWAYS:
            return True
        if vk == VK_ESCAPE:
            alt = bool(self._user32.GetAsyncKeyState(VK_MENU) & 0x8000)
            ctrl = bool(self._user32.GetAsyncKeyState(VK_CONTROL) & 0x8000)
            if alt or ctrl:
                return True
        return False

    # ------------------------------------------------------------------ #
    # Focus watchdog
    # ------------------------------------------------------------------ #
    def _focus_loop(self) -> None:  # pragma: no cover - Windows only
        user32 = self._user32
        kernel32 = self._kernel32
        while not self._stop.wait(0.25):
            if not self.hwnd:
                continue
            try:
                foreground = user32.GetForegroundWindow()
                if foreground != self.hwnd:
                    user32.ShowWindow(self.hwnd, SW_MAXIMIZE)
                    user32.SetWindowPos(
                        self.hwnd, HWND_TOPMOST, 0, 0, 0, 0,
                        SWP_NOMOVE | SWP_NOSIZE | SWP_SHOWWINDOW,
                    )
                    user32.BringWindowToTop(self.hwnd)
                    foreground_thread = user32.GetWindowThreadProcessId(foreground, None)
                    my_thread = kernel32.GetCurrentThreadId()
                    if foreground_thread and foreground_thread != my_thread:
                        user32.AttachThreadInput(foreground_thread, my_thread, True)
                        user32.SetForegroundWindow(self.hwnd)
                        user32.AttachThreadInput(foreground_thread, my_thread, False)
                    else:
                        user32.SetForegroundWindow(self.hwnd)
                    try:
                        user32.SwitchToThisWindow(self.hwnd, True)
                    except BaseException:
                        pass
                if self.clip_cursor and not self._cursor_clipped:
                    self._clip_cursor()
            except BaseException as exc:
                LOG.debug("Focus watchdog step failed: %s", exc)

    def _clip_cursor(self) -> None:  # pragma: no cover - Windows only
        """Keep the mouse inside the show window."""
        if not self.clip_cursor or not self.hwnd or self._user32 is None:
            return
        try:
            rect = wintypes.RECT()
            if self._user32.GetWindowRect(self.hwnd, ctypes.byref(rect)):
                self._user32.ClipCursor(ctypes.byref(rect))
                self._cursor_clipped = True
        except BaseException:
            pass

    def release_cursor(self) -> None:  # pragma: no cover - Windows only
        """Free the mouse (used before the final fade)."""
        if self._user32 is None:
            return
        try:
            self._user32.ClipCursor(None)
        finally:
            self._cursor_clipped = False
