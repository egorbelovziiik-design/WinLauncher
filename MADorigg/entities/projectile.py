"""Снаряды: огненный шар, ледяной луч, мега-атака."""

from __future__ import annotations

import math
from typing import Literal

import pygame

import config

ProjectileType = Literal["fireball", "ice_beam", "mega"]


class Projectile:
    """Снаряд босса, летящий через экран."""

    def __init__(
        self,
        kind: ProjectileType,
        x: float,
        y: float,
        vx: float,
        vy: float = 0.0,
        owner: Literal["red", "blue"] = "red",
        size: float | None = None,
        damage: float | None = None,
    ) -> None:
        self.kind = kind
        self.x = x
        self.y = y
        self.vx = vx
        self.vy = vy
        self.owner = owner
        if kind == "fireball":
            self.size = size if size is not None else 24.0
            self.damage = damage if damage is not None else 25.0
        elif kind == "ice_beam":
            self.size = size if size is not None else 40.0
            self.damage = damage if damage is not None else 15.0
        else:  # mega
            self.size = size if size is not None else 120.0
            self.damage = damage if damage is not None else 100.0
        self.lifetime: float = 3.0
        self.alive: bool = True
        self.age: float = 0.0
        # Для ледяного луча — хвост за головой
        self.tail_length: float = 260.0 if kind == "ice_beam" else 0.0

    # ------------------------------------------------------------------
    def update(self, dt: float) -> None:
        """Движение и life-time."""
        if not self.alive:
            return
        self.age += dt
        self.x += self.vx * dt
        self.y += self.vy * dt
        self.lifetime -= dt
        if self.lifetime <= 0.0:
            self.alive = False

    # ------------------------------------------------------------------
    def head_pos(self) -> tuple[float, float]:
        return (self.x, self.y)

    def tail_pos(self) -> tuple[float, float]:
        """Хвост (для луча — позади головы по направлению движения)."""
        if self.tail_length <= 0.0:
            return (self.x, self.y)
        speed = math.hypot(self.vx, self.vy) or 1.0
        ux, uy = self.vx / speed, self.vy / speed
        return (self.x - ux * self.tail_length, self.y - uy * self.tail_length)

    def beam_rect(self) -> pygame.Rect:
        """Охватывающий прямоугольник (для столкновений)."""
        if self.kind == "ice_beam":
            tx, ty = self.tail_pos()
            min_x = int(min(self.x, tx) - self.size)
            max_x = int(max(self.x, tx) + self.size)
            min_y = int(min(self.y, ty) - self.size)
            max_y = int(max(self.y, ty) + self.size)
            return pygame.Rect(min_x, min_y, max_x - min_x, max_y - min_y)
        r = int(self.size)
        return pygame.Rect(int(self.x - r), int(self.y - r), r * 2, r * 2)

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface, t: float) -> None:
        """Нарисовать снаряд."""
        if not self.alive:
            return
        x, y = int(self.x), int(self.y)

        if self.kind == "fireball":
            self._draw_fireball(surface, x, y, t)
        elif self.kind == "ice_beam":
            self._draw_ice_beam(surface, x, y, t)
        else:
            self._draw_mega(surface, x, y, t)

    # ------------------------------------------------------------------
    def _draw_fireball(
        self, surface: pygame.Surface, x: int, y: int, t: float
    ) -> None:
        """Красный круг с оранжевым хвостом и жёлтым ядром."""
        # Хвост (треугольник позади по направлению движения)
        speed = math.hypot(self.vx, self.vy) or 1.0
        ux, uy = self.vx / speed, self.vy / speed
        px, py = -uy, ux
        r = int(self.size)
        tail_len = r * 2.4
        flicker = 0.85 + 0.15 * math.sin(t * 30.0)
        back = (x - int(ux * tail_len * flicker), y - int(uy * tail_len * flicker))
        left = (x + int(px * r * 0.8), y + int(py * r * 0.8))
        right = (x - int(px * r * 0.8), y - int(py * r * 0.8))
        pygame.draw.polygon(surface, (255, 120, 0), [ (x, y), left, back ])
        pygame.draw.polygon(surface, (255, 120, 0), [ (x, y), right, back ])
        # Тело
        pygame.draw.circle(surface, (255, 60, 0), (x, y), r)
        pygame.draw.circle(surface, (255, 160, 20), (x, y), max(3, int(r * 0.7)))
        pygame.draw.circle(surface, (255, 240, 90), (x - r // 4, y - r // 4), max(2, int(r * 0.35)))

    def _draw_ice_beam(
        self, surface: pygame.Surface, x: int, y: int, t: float
    ) -> None:
        """Синий луч (толстая линия) с белым ядром и снежными искрами."""
        tx, ty = self.tail_pos()
        tail = (int(tx), int(ty))
        glow = 0.7 + 0.3 * math.sin(t * 25.0)
        width = max(4, int(self.size * 0.4 * glow))
        # Голова луча
        pygame.draw.circle(surface, (140, 210, 255), (x, y), int(self.size * 0.55))
        pygame.draw.circle(surface, (255, 255, 255), (x, y), max(3, int(self.size * 0.3)))
        # Луч
        pygame.draw.line(surface, (60, 150, 255), (x, y), tail, width)
        pygame.draw.line(surface, (200, 240, 255), (x, y), tail, max(2, width // 3))
        pygame.draw.circle(surface, (255, 255, 255), tail, max(2, int(self.size * 0.2)))

    def _draw_mega(
        self, surface: pygame.Surface, x: int, y: int, t: float
    ) -> None:
        """Огромная мега-атака: многослойный шар с пульсацией."""
        pulse = 1.0 + 0.12 * math.sin(t * 18.0)
        r = int(self.size * pulse)
        pygame.draw.circle(surface, (120, 0, 0), (x, y), r)
        pygame.draw.circle(surface, (255, 60, 0), (x, y), int(r * 0.85))
        pygame.draw.circle(surface, (255, 170, 30), (x, y), int(r * 0.6))
        pygame.draw.circle(surface, (255, 255, 200), (x, y), int(r * 0.35))
        pygame.draw.circle(surface, (255, 255, 255), (x, y), int(r * 0.15))

    # ------------------------------------------------------------------
    def offscreen(self, width: int, margin: int = 200) -> bool:
        """Вылетел ли снаряд за пределы экрана."""
        return (
            self.x < -margin
            or self.x > width + margin
            or self.y < -margin
            or self.y > 4000
        )
