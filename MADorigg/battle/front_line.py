"""Линия фронта: зигзагообразная вертикальная линия по центру экрана."""

from __future__ import annotations

import math
import random

import pygame

import config


class FrontLine:
    """Позиция фронта 0.0 (левый край) .. 1.0 (правый край)."""

    def __init__(self) -> None:
        self.position: float = 0.5
        self._jitter: dict[int, float] = {}
        self._rng = random.Random()

    # ------------------------------------------------------------------
    @property
    def x(self) -> float:
        """Экранная X-координата линии."""
        return self.position

    def triggered(self) -> bool:
        """Достигнута ли граница 20%/80% → пора начинать финал."""
        low, high = config.FRONT_LINE_TRIGGER
        return self.position <= low or self.position >= high

    def winning_side(self) -> str | None:
        """Чья армия давит: 'red' при position > 0.5, иначе 'blue'."""
        if self.position >= config.FRONT_LINE_TRIGGER[1]:
            return "red"
        if self.position <= config.FRONT_LINE_TRIGGER[0]:
            return "blue"
        return None

    # ------------------------------------------------------------------
    def update(self, red_count: int, blue_count: int, dt: float) -> None:
        """Сдвиг в сторону более сильной армии."""
        diff = red_count - blue_count
        if diff == 0:
            return
        # Скорость растёт с перевесом, но ограничена
        push = config.FRONT_LINE_SPEED * dt * (1.0 + min(abs(diff), 40) * 0.12)
        if diff > 0:
            self.position += push
        else:
            self.position -= push
        self.position = max(0.0, min(1.0, self.position))

    def surge(self, side: str, dt: float, speed: float = 0.45) -> None:
        """Финальный рывок линии в сторону победителя."""
        if side == "red":
            self.position += speed * dt
        else:
            self.position -= speed * dt
        self.position = max(0.0, min(1.0, self.position))

    def snap(self, position: float) -> None:
        self.position = max(0.0, min(1.0, position))

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface, t: float) -> None:
        """Мерцающая зигзагообразная линия (жёлтая/белая)."""
        width, height = surface.get_size()
        x0 = self.position * width
        if x0 < 2 or x0 > width - 2:
            return

        step = 46
        points: list[tuple[int, int]] = []
        idx = 0
        y = -step // 2
        while y < height + step:
            key = idx
            # Стабильный, но живой джиттер на каждый узел
            if key not in self._jitter:
                self._jitter[key] = self._rng.uniform(-10.0, 10.0)
            wobble = math.sin(t * 6.0 + idx * 0.9) * 4.0
            px = x0 + self._jitter[key] + wobble
            points.append((int(px), y))
            y += step
            idx += 1

        # Мерцание: жёлтый ↔ белый
        flicker = int(t * 12) % 2
        main_color = config.GOLD if flicker == 0 else config.WHITE
        glow_color = (120, 100, 0) if flicker == 0 else (140, 140, 140)

        if len(points) >= 2:
            pygame.draw.lines(surface, glow_color, False, points, 7)
            pygame.draw.lines(surface, main_color, False, points, 3)
            # Точки-искры на узлах
            if int(t * 20) % 3 == 0 and points:
                i = int(t * 10) % len(points)
                px, py = points[i]
                pygame.draw.circle(surface, (255, 255, 220), (px, py), 5)
