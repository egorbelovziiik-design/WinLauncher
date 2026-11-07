"""Borderless fullscreen window with topmost behaviour and cursor hiding."""

from __future__ import annotations

import ctypes
import logging
import sys
from typing import Optional, Tuple

import pygame

from config import WINDOWED_SIZE, WINDOW_TITLE

LOG = logging.getLogger(__name__)

# SetWindowPos flags
_HWND_TOPMOST = -1
_SWP_NOSIZE = 0x0001
_SWP_NOMOVE = 0x0002
_SWP_SHOWWINDOW = 0x0040


class Screen:
    """Owns the pygame display surface and the OS window.

    The renderer draws into an internal canvas; :meth:`present` blits that
    canvas onto the real window, optionally offset to create screen shake.
    """

    def __init__(
        self,
        windowed: bool = False,
        caption: str = WINDOW_TITLE,
        topmost: bool = True,
        size: Optional[Tuple[int, int]] = None,
    ) -> None:
        self.windowed = bool(windowed)
        self.caption = caption
        self.topmost = topmost and sys.platform == "win32"
        self._requested_size = size
        self.surface: Optional[pygame.Surface] = None
        self._cursor_hidden = False
        self._hwnd: Optional[int] = None

    # ------------------------------------------------------------------ #
    # Window lifecycle
    # ------------------------------------------------------------------ #
    def open(self) -> pygame.Surface:
        """Create the window.  Falls back gracefully if fullscreen is denied."""
        if pygame.display.get_init():
            pass
        else:
            pygame.display.init()
        if not pygame.font.get_init():
            pygame.font.init()

        pygame.display.set_caption(self.caption)
        self._enable_dpi_awareness()

        if self.windowed:
            width, height = self._requested_size or WINDOWED_SIZE
            self.surface = pygame.display.set_mode((width, height))
        else:
            self.surface = self._open_fullscreen()

        self._hwnd = self._read_hwnd()
        if self.topmost:
            self.set_topmost(True)
        LOG.info("Display opened at %s", self.surface.get_size())
        return self.surface

    def _open_fullscreen(self) -> pygame.Surface:
        """Try a real borderless fullscreen, degrade to a big window."""
        try:
            info = pygame.display.Info()
            width, height = int(info.current_w), int(info.current_h)
            if width <= 0 or height <= 0:
                width, height = 1920, 1080
            return pygame.display.set_mode((width, height), pygame.FULLSCREEN | pygame.NOFRAME)
        except pygame.error as exc:  # pragma: no cover - depends on the host
            LOG.warning("Fullscreen unavailable (%s) - using a window", exc)
            width, height = self._requested_size or WINDOWED_SIZE
            return pygame.display.set_mode((width, height))

    @staticmethod
    def _enable_dpi_awareness() -> None:
        """Ask Windows for real pixels instead of a scaled viewport."""
        if sys.platform != "win32":
            return
        try:  # pragma: no cover - Windows only
            ctypes.windll.shcore.SetProcessDpiAwareness(2)
        except BaseException:
            try:  # pragma: no cover - older Windows
                ctypes.windll.user32.SetProcessDPIAware()
            except BaseException:
                LOG.debug("Could not enable DPI awareness")

    def _read_hwnd(self) -> Optional[int]:
        """Return the native window handle (Windows only)."""
        if sys.platform != "win32":
            return None
        try:  # pragma: no cover - Windows only
            return int(pygame.display.get_wm_info().get("window", 0)) or None
        except BaseException:
            return None

    def set_topmost(self, enabled: bool = True) -> None:
        """Keep the window above everything else (Windows only)."""
        if not self.topmost or self._hwnd is None:
            return
        try:  # pragma: no cover - Windows only
            ctypes.windll.user32.SetWindowPos(
                self._hwnd,
                _HWND_TOPMOST if enabled else -2,
                0,
                0,
                0,
                0,
                _SWP_NOSIZE | _SWP_NOMOVE | _SWP_SHOWWINDOW,
            )
        except BaseException as exc:
            LOG.debug("SetWindowPos failed: %s", exc)

    def set_cursor_visible(self, visible: bool) -> None:
        """Show/hide the mouse cursor."""
        try:
            pygame.mouse.set_visible(visible)
            self._cursor_hidden = not visible
        except Exception as exc:  # pragma: no cover - defensive
            LOG.debug("Cursor toggle failed: %s", exc)

    # ------------------------------------------------------------------ #
    # Presentation
    # ------------------------------------------------------------------ #
    @property
    def hwnd(self) -> Optional[int]:
        """Native window handle (``None`` outside Windows)."""
        return self._hwnd

    @property
    def size(self) -> Tuple[int, int]:
        """Current window size in pixels."""
        if self.surface is None:
            return self._requested_size or WINDOWED_SIZE
        return self.surface.get_size()

    @property
    def width(self) -> int:
        """Window width in pixels."""
        return self.size[0]

    @property
    def height(self) -> int:
        """Window height in pixels."""
        return self.size[1]

    def present(self, canvas: pygame.Surface, offset: Tuple[int, int] = (0, 0)) -> None:
        """Blit the canvas to the window (with shake offset) and flip."""
        if self.surface is None:
            return
        dx, dy = int(offset[0]), int(offset[1])
        if dx or dy:
            self.surface.fill((0, 0, 0))
            self.surface.blit(canvas, (dx, dy))
        else:
            self.surface.blit(canvas, (0, 0))

    def close(self) -> None:
        """Make sure the cursor is back and the display is released."""
        self.set_cursor_visible(True)
        try:
            pygame.display.quit()
        except Exception:  # pragma: no cover - defensive
            pass
