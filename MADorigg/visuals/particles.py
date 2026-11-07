"""Система частиц: искры, огонь, лёд, дым, конфетти.

Всё рисуется через ``pygame.draw`` — внешних изображений нет.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass

import pygame

import config


@dataclass
class Particle:
    """Одна частица."""

    x: float
    y: float
    vx: float
    vy: float
    color: tuple[int, int, int]
    size: float
    lifetime: float            # остаток жизни, сек
    max_lifetime: float
    gravity: float = 0.0
    drag: float = 0.0
    shrink: bool = True

    @property
    def alive(self) -> bool:
        """Жива ли частица."""
        return self.lifetime > 0.0

    @property
    def alpha_ratio(self) -> float:
        """0..1 — доля оставшейся жизни."""
        if self.max_lifetime <= 0.0:
            return 0.0
        return max(0.0, min(1.0, self.lifetime / self.max_lifetime))

    def update(self, dt: float) -> None:
        """Обновить позицию и время жизни."""
        self.lifetime -= dt
        self.vy += self.gravity * dt
        if self.drag:
            damping = math.exp(-self.drag * dt)
            self.vx *= damping
            self.vy *= damping
        self.x += self.vx * dt
        self.y += self.vy * dt


class ParticleSystem:
    """Пул частиц с ограничением ``config.MAX_PARTICLES``."""

    def __init__(self, max_particles: int | None = None) -> None:
        self.max_particles = max_particles or config.MAX_PARTICLES
        self.particles: list[Particle] = []

    # ------------------------------------------------------------------
    # Эмиссия
    # ------------------------------------------------------------------
    def emit(
        self,
        x: float,
        y: float,
        count: int,
        color: tuple[int, int, int] | None = None,
        colors: list[tuple[int, int, int]] | None = None,
        speed: tuple[float, float] = (40.0, 160.0),
        lifetime: tuple[float, float] = (0.3, 0.9),
        size: tuple[float, float] = (2.0, 5.0),
        gravity: float = 0.0,
        drag: float = 0.0,
        direction: float | None = None,
        spread: float = math.pi * 2.0,
    ) -> None:
        """Создать ``count`` частиц в точке (x, y).

        ``direction`` — угол эмиссии (рад), ``spread`` — разброс вокруг него.
        """
        for _ in range(max(0, count)):
            if len(self.particles) >= self.max_particles:
                # Удаляем самые старые
                self.particles.pop(0)
            angle = (
                random.uniform(direction - spread / 2, direction + spread / 2)
                if direction is not None
                else random.uniform(0.0, math.tau)
            )
            vel = random.uniform(*speed)
            life = random.uniform(*lifetime)
            if colors:
                col = random.choice(colors)
            elif color is not None:
                col = color
            else:
                col = (255, 255, 255)
            self.particles.append(
                Particle(
                    x=x,
                    y=y,
                    vx=math.cos(angle) * vel,
                    vy=math.sin(angle) * vel,
                    color=col,
                    size=random.uniform(*size),
                    lifetime=life,
                    max_lifetime=life,
                    gravity=gravity,
                    drag=drag,
                )
            )

    # ------------------------------------------------------------------
    # Пресеты
    # ------------------------------------------------------------------
    def fire_explosion(self, x: float, y: float) -> None:
        """30 частиц красного/оранжевого/жёлтого огня."""
        self.emit(
            x, y, 30,
            colors=[(255, 60, 0), (255, 140, 0), (255, 220, 40)],
            speed=(80.0, 320.0),
            lifetime=(0.3, 0.8),
            size=(3.0, 7.0),
            gravity=220.0,
            drag=1.5,
        )

    def ice_shatter(self, x: float, y: float) -> None:
        """20 частиц голубого/белого/синего льда."""
        self.emit(
            x, y, 20,
            colors=[(170, 235, 255), (255, 255, 255), (80, 160, 255)],
            speed=(60.0, 260.0),
            lifetime=(0.3, 0.7),
            size=(2.0, 5.0),
            gravity=300.0,
        )

    def spark(self, x: float, y: float) -> None:
        """10 жёлто-белых искр."""
        self.emit(
            x, y, 10,
            colors=[(255, 240, 80), (255, 255, 255)],
            speed=(100.0, 380.0),
            lifetime=(0.1, 0.4),
            size=(1.0, 3.0),
            gravity=400.0,
        )

    def confetti(self, x: float, y: float) -> None:
        """50 разноцветных частиц конфетти/фейерверка."""
        rainbow = [
            (255, 0, 0), (255, 127, 0), (255, 255, 0),
            (0, 200, 0), (0, 128, 255), (128, 0, 255),
            (255, 0, 255), (255, 255, 255),
        ]
        self.emit(
            x, y, 50,
            colors=rainbow,
            speed=(80.0, 360.0),
            lifetime=(0.8, 1.8),
            size=(3.0, 6.0),
            gravity=260.0,
            drag=0.8,
        )

    def smoke(self, x: float, y: float) -> None:
        """15 серых медленных частиц дыма."""
        self.emit(
            x, y, 15,
            colors=[(90, 90, 90), (130, 130, 130), (60, 60, 60)],
            speed=(10.0, 50.0),
            lifetime=(0.6, 1.4),
            size=(5.0, 12.0),
            gravity=-40.0,
            drag=1.0,
        )

    def lightning_spark(self, x: float, y: float) -> None:
        """Искры молнии между боссами."""
        self.emit(
            x, y, 6,
            colors=[(255, 255, 200), (180, 220, 255), (255, 255, 255)],
            speed=(60.0, 300.0),
            lifetime=(0.08, 0.3),
            size=(1.0, 3.0),
            gravity=120.0,
        )

    def ice_beam_spark(self, x: float, y: float) -> None:
        """Снежные частицы вокруг ледяного луча."""
        self.emit(
            x, y, 3,
            colors=[(220, 245, 255), (255, 255, 255), (150, 210, 255)],
            speed=(20.0, 120.0),
            lifetime=(0.2, 0.6),
            size=(1.0, 3.0),
            gravity=60.0,
        )

    # ------------------------------------------------------------------
    # Обновление и отрисовка
    # ------------------------------------------------------------------
    def update(self, dt: float) -> None:
        """Обновить все частицы, удалить мёртвые."""
        for particle in self.particles:
            particle.update(dt)
        self.particles = [p for p in self.particles if p.alive]

    def draw(self, surface: pygame.Surface) -> None:
        """Нарисовать частицы с затуханием альфы."""
        overlay = pygame.Surface(surface.get_size(), pygame.SRCALPHA)
        for p in self.particles:
            ratio = p.alpha_ratio
            alpha = int(255 * ratio)
            size = p.size * (0.4 + 0.6 * ratio) if p.shrink else p.size
            radius = max(1, int(size))
            pygame.draw.circle(
                overlay,
                (*p.color, alpha),
                (int(p.x), int(p.y)),
                radius,
            )
        surface.blit(overlay, (0, 0))

    def clear(self) -> None:
        """Удалить все частицы."""
        self.particles.clear()

    def __len__(self) -> int:
        return len(self.particles)
