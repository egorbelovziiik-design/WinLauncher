"""Фаза 0–1: подготовка (чёрный экран) и взрыв звука с титром MADorigg."""

from __future__ import annotations

import logging

import pygame

import config
from visuals.text_renderer import TextRenderer

logger = logging.getLogger(__name__)


class PhaseIntro:
    """0–5 сек: тишина → громкость на максимум → вспышка → глитч-титр."""

    def __init__(self, manager) -> None:
        self.manager = manager
        self.t: float = 0.0
        self._sound_ramped: bool = False
        self._flashed: bool = False
        self._music_started: bool = False

    def enter(self) -> None:
        self.t = 0.0
        self._sound_ramped = False
        self._flashed = False
        self._music_started = False
        # Фаза 0: экран чёрный, курсор скрыт (в main), громкость сохранена.
        logger.info("Intro: black screen for 1 second...")

    # ------------------------------------------------------------------
    def update(self, dt: float) -> str | None:
        self.t += dt
        m = self.manager

        # --- Фаза 1 (t=2): взрыв звука ---
        if self.t >= config.dur(2.0) and not self._sound_ramped:
            self._sound_ramped = True
            current = m.volume.get_volume()
            # Громкость плавно (за 1 сек) выкручивается на 100%
            m.volume.fade_volume(current, config.MASTER_VOLUME, 1.0)
            m.volume.boost_eq()
            # Эпичная музыка 140 BPM начинает орать
            m.music.prepare_music(config.INTRO_BPM, 12.0)
            m.music.play_music(config.INTRO_BPM, 12.0, volume=0.85)
            m.music.play("explosion", volume=1.0)
            m.music.play("siren", volume=0.7)
            self._music_started = True
            logger.info("Intro: SOUND EXPLOSION!")

        # Белая вспышка 0.3 сек
        if self.t >= config.dur(2.0) and not self._flashed:
            self._flashed = True
            m.flash.trigger((255, 255, 255), 0.3, peak_alpha=255)
            m.shake.trigger(8.0, 0.35)

        # Глитч каждые 0.8 сек на титре
        if self.t >= config.dur(2.3) and int(self.t / max(0.001, config.dur(0.8))) != int((self.t - dt) / max(0.001, config.dur(0.8))):
            m.glitch.trigger(0.25)

        # --- Конец фазы ---
        if self.t >= config.dur(config.PHASE_INTRO_DURATION):
            return "bosses"
        return None

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface) -> None:
        surface.fill(config.BLACK)
        t = self.t
        w, h = surface.get_size()

        # Титр «MADorigg» с глитч-эффектом (буквы дрожат, красный↔синий)
        if t >= config.dur(2.3):
            self.manager.text.glitch(
                surface,
                "MADorigg",
                size=max(60, w // 8),
                y=int(h * 0.45),
                t=t,
                amplitude=7.0,
            )
            if t >= config.dur(3.2):
                self.manager.text.draw(
                    surface,
                    "ЭПИЧНАЯ БИТВА СКОРО НАЧНЁТСЯ...",
                    size=max(24, w // 42),
                    color=(255, 120, 120),
                    y=int(h * 0.58),
                )

        # Нагнетание: тонкая белая линия-таймер в первые 2 секунды
        if t < config.dur(2.0):
            ratio = t / max(0.001, config.dur(2.0))
            bar_w = int(w * ratio)
            if bar_w > 0:
                pygame.draw.rect(
                    surface, (40, 0, 0),
                    (0, h // 2 - 2, bar_w, 4),
                )
