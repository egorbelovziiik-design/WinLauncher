"""Фаза 2: экран делится молнией, появляются два босса-смайлика."""

from __future__ import annotations

import logging
import math
import random

import pygame

import config

logger = logging.getLogger(__name__)


class PhaseBosses:
    """5–8 сек: разделение экрана, красный 😡 слева, синий 😎 справа."""

    def __init__(self, manager) -> None:
        self.manager = manager
        self.t: float = 0.0
        self._gong_played: bool = False
        self._rng = random.Random()
        self._spark_timer: float = 0.0
        self._split_sound_done: bool = False

    def enter(self) -> None:
        self.t = 0.0
        self._gong_played = False
        self._split_sound_done = False
        self._spark_timer = 0.0
        m = self.manager
        m.reveal = 0.0
        # Боссы начинают за краями экрана
        m.red_boss.set_home(
            m.width * config.BOSS_X_RED, m.height * config.BOSS_Y
        )
        m.blue_boss.set_home(
            m.width * config.BOSS_X_BLUE, m.height * config.BOSS_Y
        )
        logger.info("Bosses: screen splits with lightning!")

    # ------------------------------------------------------------------
    def update(self, dt: float) -> str | None:
        self.t += dt
        m = self.manager

        # Проявление разделения экрана за первые 0.8 сек
        m.reveal = min(1.0, m.reveal + dt / max(0.001, config.dur(0.8)))

        # Появление боссов (выезжают к домашним позициям)
        ease = min(1.0, dt * 6.0)
        m.red_boss.x += (m.red_boss.home_x - m.red_boss.x) * ease
        m.red_boss.y += (m.red_boss.home_y - m.red_boss.y) * ease
        m.blue_boss.x += (m.blue_boss.home_x - m.blue_boss.x) * ease
        m.blue_boss.y += (m.blue_boss.home_y - m.blue_boss.y) * ease
        m.red_boss.update(dt)
        m.blue_boss.update(dt)

        # Эпичный гонг + рёв толпы
        if not self._gong_played and self.t >= config.dur(0.4):
            self._gong_played = True
            m.music.play("gong", volume=1.0)
            m.music.play("crowd", volume=0.9)
        if not self._split_sound_done and self.t >= config.dur(0.05):
            self._split_sound_done = True
            m.music.play("explosion", volume=0.5)
            m.shake.trigger(5.0, 0.3)

        # Молнии и искры между боссами
        self._spark_timer -= dt
        if self._spark_timer <= 0.0 and m.reveal > 0.5:
            self._spark_timer = self._rng.uniform(0.05, 0.15)
            cx = m.width * 0.5
            y = m.height * self._rng.uniform(0.2, 0.8)
            m.particles.lightning_spark(cx, y)

        # Глитч на появлении
        if config.dur(0.7) < self.t < config.dur(0.75):
            m.glitch.trigger(0.3)

        if self.t >= config.dur(config.PHASE_BOSSES_DURATION):
            return "battle"
        return None

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface) -> None:
        m = self.manager
        t = self.t

        # Фон: красный градиент | молния | синий градиент
        m.background.draw(surface, t, reveal=m.reveal)

        # Боссы
        m.red_boss.draw(surface, t)
        m.blue_boss.draw(surface, t)

        # Частицы (искры молний)
        m.particles.draw(surface)

        # Текст сверху: пульсирует + глитч
        title = "БИТВА ЗА ЭКРАН НАЧИНАЕТСЯ!"
        w, h = surface.get_size()
        if t >= config.dur(0.6):
            if int(t * 6) % 4 == 0:
                m.text.glitch(
                    surface, title,
                    size=max(36, w // 24),
                    y=int(h * 0.10),
                    t=t,
                    amplitude=5.0,
                )
            else:
                m.text.pulse(
                    surface, title,
                    size=max(36, w // 24),
                    color=config.GOLD,
                    y=int(h * 0.10),
                    t=t,
                    speed=8.0,
                )

        # Декоративная молния по центру при малом reveal
        if m.reveal < 1.0:
            cx = w // 2
            pts = [
                (cx + int(math.sin(t * 50 + i) * 18), i * 40 - 20)
                for i in range(h // 40 + 2)
            ]
            if len(pts) >= 2:
                pygame.draw.lines(surface, (255, 255, 220), False, pts, 4)
