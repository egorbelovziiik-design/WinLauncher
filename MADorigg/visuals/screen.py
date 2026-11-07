"""Полноэкранный режим, topmost-окно и скрытие курсора."""

from __future__ import annotations

import logging
import sys

import pygame

import config

logger = logging.getLogger(__name__)


def create_screen() -> pygame.Surface:
    """Создать borderless-полноэкранное окно (поверх всех окон — topmost).

    На Windows включается FULLSCREEN | NOFRAME и HWND_TOPMOST через ctypes.
    """
    try:
        screen = pygame.display.set_mode(
            (0, 0), pygame.FULLSCREEN | pygame.NOFRAME
        )
    except pygame.error:
        logger.warning("Fullscreen failed, falling back to windowed mode.")
        screen = pygame.display.set_mode(
            (config.SCREEN_WIDTH, config.SCREEN_HEIGHT)
        )

    pygame.display.set_caption("MADorigg")
    set_topmost(screen)
    return screen


def set_topmost(screen: pygame.Surface) -> bool:
    """Сделать окно поверх всех окон (только Windows)."""
    if sys.platform != "win32":
        return False
    try:
        import ctypes

        hwnd = pygame.display.get_wm_info()["window"]
        # HWND_TOPMOST = -1, SWP_NOMOVE | SWP_NOSIZE
        ctypes.windll.user32.SetWindowPos(
            hwnd, -1, 0, 0, 0, 0, 0x0001 | 0x0002
        )
        logger.info("Window set topmost (hwnd=%s)", hwnd)
        return True
    except Exception as exc:
        logger.warning("SetWindowPos failed: %s", exc)
        return False


def hide_cursor() -> None:
    """Скрыть курсор мыши."""
    pygame.mouse.set_visible(False)


def show_cursor() -> None:
    """Показать курсор мыши."""
    pygame.mouse.set_visible(True)


def get_screen_size() -> tuple[int, int]:
    """Текущее разрешение экрана."""
    info = pygame.display.Info()
    return info.current_w, info.current_h
