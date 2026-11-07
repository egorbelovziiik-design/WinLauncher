"""Lightweight particle system with element presets.

A single :class:`ParticleSystem` is shared by the whole show; it hard-caps the
number of live particles and drops the oldest ones so the frame time stays
predictable even during the finale.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from typing import Optional, Sequence, Tuple, Union

import pygame

from config import (
    CONFETTI_COLORS,
    FIRE_COLORS,
    ICE_COLORS,
    MAX_PARTICLES,
    SMOKE_COLORS,
)

Color = Tuple[int, int, int]
ColorArg = Union[Color, Sequence[Color]]


def _mix(a: Color, b: Color, t: float) -> Color:
    """Linear interpolation between two RGB colours."""
    t = max(0.0, min(1.0, t))
    return (
        int(a[0] + (b[0] - a[0]) * t),
        int(a[1] + (b[1] - a[1]) * t),
        int(a[2] + (b[2] - a[2]) * t),
    )


def _scale(color: Color, factor: float) -> Color:
    """Darken a colour by ``factor`` (clamped to 0..255)."""
    factor = max(0.0, min(1.0, factor))
    return (
        max(0, min(255, int(color[0] * factor))),
        max(0, min(255, int(color[1] * factor))),
        max(0, min(255, int(color[2] * factor))),
    )


@dataclass
class Particle:
    """A single animated dot."""

    x: float
    y: float
    vx: float
    vy: float
    color: Color
    size: float
    lifetime: float
    age: float = 0.0
    gravity: float = 0.0
    drag: float = 0.0
    shrink: bool = True
    glow: bool = False
    fade_to: Optional[Color] = None

    @property
    def alive(self) -> bool:
        """True while the particle still has life left."""
        return self.age < self.lifetime

    @property
    def progress(self) -> float:
        """Normalised age in ``0..1``."""
        return 0.0 if self.lifetime <= 0 else min(1.0, self.age / self.lifetime)

    def update(self, dt: float) -> bool:
        """Integrate one step.  Returns ``False`` when the particle died."""
        self.age += dt
        if self.age >= self.lifetime:
            return False
        if self.drag:
            factor = max(0.0, 1.0 - self.drag * dt)
            self.vx *= factor
            self.vy *= factor
        self.vy += self.gravity * dt
        self.x += self.vx * dt
        self.y += self.vy * dt
        return True

    def draw(self, surface: pygame.Surface) -> None:
        """Render the particle with size and colour fading."""
        k = 1.0 - self.progress
        color = self.color
        if self.fade_to is not None:
            color = _mix(self.fade_to, self.color, k)
        color = _scale(color, 0.35 + 0.65 * k)
        radius = self.size * (0.25 + 0.75 * k) if self.shrink else self.size
        px, py = int(self.x), int(self.y)
        if self.glow:
            halo = _scale(color, 0.4)
            pygame.draw.circle(surface, halo, (px, py), max(1, int(radius * 2.1)))
        pygame.draw.circle(surface, color, (px, py), max(1, int(radius)))


class ParticleSystem:
    """Fixed-capacity particle pool with element-specific presets."""

    def __init__(self, capacity: int = MAX_PARTICLES, rng: Optional[random.Random] = None) -> None:
        self.capacity = max(32, int(capacity))
        self.rng = rng or random.Random()
        self.particles: list[Particle] = []

    # ------------------------------------------------------------------ #
    # Core API
    # ------------------------------------------------------------------ #
    def __len__(self) -> int:
        return len(self.particles)

    def clear(self) -> None:
        """Remove every particle."""
        self.particles.clear()

    def emit(
        self,
        x: float,
        y: float,
        count: int,
        color: ColorArg,
        speed: float = 240.0,
        lifetime: float = 0.7,
        size: float = 4.0,
        *,
        size_range: float = 0.6,
        angle: float = 0.0,
        spread: float = math.tau,
        gravity: float = 0.0,
        drag: float = 0.0,
        glow: bool = False,
        shrink: bool = True,
        fade_to: Optional[Color] = None,
        jitter: float = 2.0,
        life_range: float = 0.5,
    ) -> None:
        """Spawn ``count`` particles at ``(x, y)``.

        ``color`` accepts a single RGB tuple or a palette (sequence of tuples).
        """
        if count <= 0:
            return
        palette: Sequence[Color]
        if color and isinstance(color[0], (tuple, list)):  # type: ignore[index]
            palette = color  # type: ignore[assignment]
        else:
            palette = (color,)  # type: ignore[assignment]

        rng = self.rng
        for _ in range(count):
            theta = angle + rng.uniform(-spread * 0.5, spread * 0.5)
            velocity = speed * rng.uniform(0.35, 1.15)
            life = max(0.05, lifetime * rng.uniform(1.0 - life_range * 0.5, 1.0 + life_range * 0.5))
            self.particles.append(
                Particle(
                    x=x + rng.uniform(-jitter, jitter),
                    y=y + rng.uniform(-jitter, jitter),
                    vx=math.cos(theta) * velocity,
                    vy=math.sin(theta) * velocity,
                    color=palette[rng.randrange(len(palette))],
                    size=max(1.0, size * rng.uniform(1.0 - size_range * 0.5, 1.0 + size_range * 0.5)),
                    lifetime=life,
                    gravity=gravity,
                    drag=drag,
                    shrink=shrink,
                    glow=glow,
                    fade_to=fade_to,
                )
            )
        overflow = len(self.particles) - self.capacity
        if overflow > 0:
            del self.particles[:overflow]

    def update(self, dt: float) -> None:
        """Integrate all particles and drop the dead ones."""
        if not self.particles:
            return
        survivors = [p for p in self.particles if p.update(dt)]
        self.particles = survivors

    def draw(self, surface: pygame.Surface) -> None:
        """Render every live particle."""
        for particle in self.particles:
            particle.draw(surface)

    # ------------------------------------------------------------------ #
    # Presets
    # ------------------------------------------------------------------ #
    def fire_explosion(self, x: float, y: float, power: float = 1.0) -> None:
        """30-ish fire particles + a little smoke."""
        count = int(30 * power)
        self.emit(
            x, y, count, FIRE_COLORS, speed=340 * power, lifetime=0.75,
            size=6.0, gravity=-140.0, drag=1.2, glow=True, jitter=6.0,
        )
        self.smoke(x, y, count=max(3, count // 4))

    def ice_shatter(self, x: float, y: float, power: float = 1.0) -> None:
        """20-ish frozen shards flying outwards."""
        self.emit(
            x, y, int(20 * power), ICE_COLORS, speed=260 * power, lifetime=0.7,
            size=4.0, gravity=420.0, drag=0.4, glow=True, jitter=4.0,
        )

    def spark(self, x: float, y: float, count: int = 10) -> None:
        """Tiny white-hot sparks for hits and lightning."""
        self.emit(
            x, y, count, ((255, 255, 255), (255, 236, 80), (255, 180, 40)),
            speed=320.0, lifetime=0.35, size=2.4, gravity=260.0,
            glow=True, jitter=3.0,
        )

    def confetti(self, x: float, y: float, count: int = 50) -> None:
        """Rainbow confetti with strong gravity."""
        self.emit(
            x, y, count, CONFETTI_COLORS, speed=520.0, lifetime=2.4,
            size=4.0, gravity=560.0, drag=0.6, shrink=False, jitter=8.0,
            angle=-math.pi / 2.0, spread=math.pi * 1.1,
        )

    def smoke(self, x: float, y: float, count: int = 15) -> None:
        """Slow grey smoke drifting upwards."""
        self.emit(
            x, y, count, SMOKE_COLORS, speed=70.0, lifetime=1.8,
            size=10.0, gravity=-40.0, drag=0.8, jitter=10.0,
        )

    def embers(self, x: float, y: float, count: int = 2) -> None:
        """Ambient fire embers for the red half."""
        self.emit(
            x, y, count, FIRE_COLORS[1:4], speed=70.0, lifetime=1.4,
            size=2.6, gravity=-110.0, angle=-math.pi / 2.0, spread=0.9,
            glow=True, jitter=6.0,
        )

    def snow(self, x: float, y: float, count: int = 2) -> None:
        """Ambient snowflakes for the blue half."""
        self.emit(
            x, y, count, ICE_COLORS[:3], speed=45.0, lifetime=2.0,
            size=2.2, gravity=55.0, angle=math.pi / 2.0, spread=0.7,
            jitter=6.0,
        )

    def shatter(self, x: float, y: float, count: int, color: ColorArg, power: float = 1.0) -> None:
        """Generic explosion used for bosses and the final blow."""
        self.emit(
            x, y, count, color, speed=520.0 * power, lifetime=1.2,
            size=7.0, gravity=320.0, drag=0.5, glow=True, jitter=12.0,
        )

    def slash(self, x: float, y: float, color: ColorArg) -> None:
        """Horizontal streak for beams and fast projectiles."""
        self.emit(
            x, y, 4, color, speed=180.0, lifetime=0.3, size=3.0,
            angle=0.0, spread=0.5, drag=2.0, glow=True, jitter=3.0,
        )
