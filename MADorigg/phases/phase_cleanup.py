"""Фаза 5: очистка — затухание экрана и звука, восстановление, выход."""

from __future__ import annotations

import logging

import pygame

import config

logger = logging.getLogger(__name__)


class PhaseCleanup:
    """≈4 сек: fade-to-black, fade-out звука, restore громкости/EQ/курсора."""

    def __init__(self, manager) -> None:
        self.manager = manager
        self.t: float = 0.0
        self._audio_restored: bool = False
        self._cursor_shown: bool = False
        self._message_shown: bool = False

    def enter(self) -> None:
        self.t = 0.0
        self._audio_restored = False
        self._cursor_shown = False
        self._message_shown = False
        m = self.manager

        # Звук плавно затихает (fade-out 1.5 сек)
        m.music.fade_out_music(1.5)

        # Громкость системы плавно возвращается (за 1 сек)
        saved = m.volume.saved_volume()
        current = m.volume.get_volume()
        m.volume.fade_volume(current, saved, 1.0)

        # Эквалайзер возвращается к исходным настройкам
        m.volume.restore_eq()
        logger.info("Cleanup: fading out and restoring everything...")

    # ------------------------------------------------------------------
    def update(self, dt: float) -> str | None:
        self.t += dt
        m = self.manager

        # Курсор возвращается
        if self.t >= config.dur(1.0) and not self._cursor_shown:
            self._cursor_shown = True
            from visuals.screen import show_cursor

            show_cursor()
            logger.info("Cursor restored.")

        # Страховка: точное восстановление громкости к концу фазы
        if self.t >= config.dur(1.0) and not self._audio_restored:
            self._audio_restored = True
            m.volume.restore_volume()
            m.volume.restore_eq()
            logger.info("Volume + EQ fully restored.")

        # Текст прощания (~2 сек)
        if self.t >= config.dur(1.5) and not self._message_shown:
            self._message_shown = True
            logger.info("Bye!")

        # Полное завершение приложения
        if self.t >= config.dur(config.PHASE_CLEANUP_DURATION):
            m.running = False
            return None
        return None

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface) -> None:
        m = self.manager
        t = self.t
        w, h = surface.get_size()

        # Последний кадр (снимок финала) под затемнением
        snapshot = m.snapshot
        if snapshot is not None:
            surface.blit(snapshot, (0, 0))
        else:
            surface.fill(config.BLACK)

        # Fade to black за 1 секунду
        black = pygame.Surface((w, h), pygame.SRCALPHA)
        fade_ratio = min(1.0, t / max(0.001, config.dur(1.0)))
        black.fill((0, 0, 0, int(255 * fade_ratio)))
        surface.blit(black, (0, 0))

        # «MADorigg was here 😈» по центру (эмодзи нарисован вручную)
        if t >= config.dur(1.5):
            msg = "MADorigg was here"
            size = max(26, w // 44)
            rect = m.text.draw(
                surface, msg, size, (230, 230, 230), h // 2,
                center_x=w // 2 - size // 2,
            )
            # Дьявол 😈 рядом с текстом (вместо emoji-глифа)
            devil_r = max(10, int(size * 0.55))
            m.text.draw_devil(
                surface,
                rect.right + devil_r + 8,
                h // 2,
                devil_r,
                t=t,
            )
