"""Фоны: красный градиент слева, синий справа, вертикальная молния-разделитель."""

from __future__ import annotations

import math
import random

import pygame

import config


class BattleBackground:
    """Полноэкранный фон битвы с двумя градиентами и молнией по центру."""

    def __init__(self, width: int, height: int) -> None:
        self.width = width
        self.height = height
        self.left_surface = self._make_gradient(
            config.RED_DARK, (255, 40, 40), left_side=True
        )
        self.right_surface = self._make_gradient(
            config.BLUE_DARK, (30, 90, 255), left_side=False
        )
        self._bolt: list[tuple[int, int]] = []
        self._bolt_timer: float = 0.0

    # ------------------------------------------------------------------
    def _make_gradient(
        self,
        start: tuple[int, int, int],
        end: tuple[int, int, int],
        left_side: bool,
    ) -> pygame.Surface:
        """Вертикальный градиент для своей половины экрана."""
        half_w = max(1, self.width // 2)
        surface = pygame.Surface((half_w, self.height))
        for x in range(half_w):
            ratio = x / max(1, half_w - 1)
            if not left_side:
                ratio = 1.0 - ratio
            # Ярче к центру, темнее к краям + верх/низ затемнены
            r = int(start[0] + (end[0] - start[0]) * ratio)
            g = int(start[1] + (end[1] - start[1]) * ratio)
            b = int(start[2] + (end[2] - start[2]) * ratio)
            for y in range(self.height):
                v_ratio = abs(y / self.height - 0.5) * 1.2
                dim = max(0.35, 1.0 - v_ratio)
                surface.set_at(
                    (x, y),
                    (int(r * dim), int(g * dim), int(b * dim)),
                )
        return surface

    # ------------------------------------------------------------------
    def _update_bolt(self, dt: float) -> None:
        """Периодически перестраивать зигзаг молнии."""
        self._bolt_timer -= dt
        if self._bolt_timer > 0.0 and self._bolt:
            return
        self._bolt_timer = random.uniform(0.05, 0.12)
        cx = self.width // 2
        points: list[tuple[int, int]] = []
        step = max(24, self.height // 24)
        y = -step
        while y < self.height + step:
            jitter = random.randint(-14, 14)
            points.append((cx + jitter, y))
            y += step
        self._bolt = points

    # ------------------------------------------------------------------
    def draw(
        self,
        surface: pygame.Surface,
        t: float,
        reveal: float = 1.0,
    ) -> None:
        """Нарисовать фон.

        ``reveal`` 0..1 — анимация разделения экрана (0 = чёрный).
        """
        self._update_bolt(1.0 / config.FPS)
        surface.fill(config.BLACK)

        if reveal <= 0.0:
            return

        half_w = self.width // 2
        reveal = max(0.0, min(1.0, reveal))

        # Градиенты с прозрачностью (проявление из центра)
        if reveal < 1.0:
            left = self.left_surface.copy()
            right = self.right_surface.copy()
            left.set_alpha(int(255 * reveal))
            right.set_alpha(int(255 * reveal))
        else:
            left = self.left_surface
            right = self.right_surface

        # Левая половина выезжает из-под молнии влево
        left_offset = int((1.0 - reveal) * half_w)
        surface.blit(left, (left_offset - 0, 0))
        surface.blit(right, (half_w, 0))

        # Центральная молния (мерцает)
        if len(self._bolt) >= 2:
            flicker = 0.7 + 0.3 * math.sin(t * 40.0)
            glow_color = (
                int(120 * flicker),
                int(160 * flicker),
                int(255 * flicker),
            )
            core_color = (
                int(255 * flicker),
                int(255 * flicker),
                min(255, int(180 * flicker + 60)),
            )
            pygame.draw.lines(surface, glow_color, False, self._bolt, 6)
            pygame.draw.lines(surface, core_color, False, self._bolt, 2)

        # Тонкая световая граница между половинками
        if reveal >= 1.0:
            divider = pygame.Surface((4, self.height), pygame.SRCALPHA)
            divider.fill((255, 255, 255, 60))
            surface.blit(divider, (half_w - 2, 0))

    def center_x(self) -> float:
        """X-координата линии раздела."""
        return float(self.width // 2)
