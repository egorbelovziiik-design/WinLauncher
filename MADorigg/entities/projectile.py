"""Boss attacks: fireballs, ice beams and the screen-sized mega attack."""

from __future__ import annotations

import logging
import math
import random
from typing import TYPE_CHECKING, Optional, Tuple

import pygame

from config import (
    FIREBALL_SIZE,
    FIREBALL_SPEED,
    ICE_BEAM_LENGTH,
    ICE_BEAM_SPEED,
    PROJECTILE_LIFETIME,
)

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext
    from .soldier import Soldier

LOG = logging.getLogger(__name__)

Color = Tuple[int, int, int]


class Projectile:
    """A single travelling attack.

    ``fireball`` is a round flaming orb, ``ice_beam`` a horizontal freezing ray
    that travels leftwards and leaves snow in its wake.
    """

    def __init__(
        self,
        kind: str,
        side: str,
        x: float,
        y: float,
        speed: float = FIREBALL_SPEED,
        size: int = FIREBALL_SIZE,
        damage: int = 25,
    ) -> None:
        self.kind = kind
        self.side = side
        self.x = float(x)
        self.y = float(y)
        self.speed = abs(speed)
        self.vx = self.speed if side == "red" else -self.speed
        self.size = float(size)
        self.damage = damage
        self.lifetime = PROJECTILE_LIFETIME
        self.age = 0.0
        self.alive = True
        self.length = float(ICE_BEAM_LENGTH)
        self._trail_timer = 0.0
        self._hit_soldiers: set = set()

    # ------------------------------------------------------------------ #
    # Queries
    # ------------------------------------------------------------------ #
    @property
    def box(self) -> pygame.Rect:
        """Approximate bounding box (used for debug and culling)."""
        if self.kind == "ice_beam":
            return pygame.Rect(int(self.x), int(self.y - 18), int(self.length), 36)
        radius = int(self.size * 0.6)
        return pygame.Rect(int(self.x - radius), int(self.y - radius), radius * 2, radius * 2)

    def hits(self, soldier: "Soldier") -> bool:
        """True if this projectile affects ``soldier``."""
        if not soldier.targetable:
            return False
        if self.kind == "ice_beam":
            within_x = self.x <= soldier.x <= self.x + self.length
            within_y = abs(soldier.y - self.y) < 60.0
            return within_x and within_y
        distance = math.hypot(soldier.x - self.x, soldier.y - self.y)
        return distance < self.size * 0.7 + 10.0

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(self, dt: float, ctx: "BattleContext") -> None:
        """Move the projectile, emit particles and cull when off screen."""
        self.age += dt
        self.x += self.vx * dt
        self._trail_timer += dt

        if self.kind == "fireball":
            if self._trail_timer >= 0.02:
                self._trail_timer = 0.0
                ctx.particles.emit(
                    self.x - math.copysign(self.size * 0.4, self.vx),
                    self.y + ctx.rng.uniform(-4.0, 4.0),
                    2,
                    ((255, 190, 60), (255, 110, 30), (200, 40, 10)),
                    speed=90.0,
                    lifetime=0.45,
                    size=5.0,
                    gravity=-160.0,
                    glow=True,
                )
        else:
            if self._trail_timer >= 0.03:
                self._trail_timer = 0.0
                ctx.particles.snow(
                    self.x + ctx.rng.uniform(0.0, self.length),
                    self.y + ctx.rng.uniform(-24.0, 24.0),
                    2,
                )
                ctx.particles.spark(
                    self.x + ctx.rng.uniform(0.0, self.length),
                    self.y + ctx.rng.uniform(-16.0, 16.0),
                    1,
                )

        if self.age > self.lifetime:
            self.alive = False
        if self.x < -self.length - 120 or self.x > ctx.width + 200:
            self.alive = False

    # ------------------------------------------------------------------ #
    # Rendering
    # ------------------------------------------------------------------ #
    def draw(self, surface: pygame.Surface, ctx: "BattleContext") -> None:
        """Render the fireball or ice beam."""
        if not self.alive:
            return
        if self.kind == "ice_beam":
            self._draw_ice_beam(surface, ctx)
        else:
            self._draw_fireball(surface, ctx)

    def _draw_fireball(self, surface: pygame.Surface, ctx: "BattleContext") -> None:
        """Flaming orb with a tapered tail and a white-hot core."""
        t = self.age
        direction = -1 if self.vx > 0 else 1
        x = int(self.x)
        y = int(self.y)

        for index in range(6):
            k = index / 6.0
            radius = max(2, int(self.size * 0.55 * (1.0 - k * 0.75)))
            tail_x = x + int(direction * self.size * (0.35 + k * 1.1))
            tail_y = y + int(math.sin(t * 22.0 + index) * 3 * (1.0 - k))
            color = (
                (255, 240, 190),
                (255, 190, 60),
                (255, 120, 30),
                (230, 60, 20),
                (180, 30, 10),
                (120, 20, 10),
            )[min(index, 5)]
            pygame.draw.circle(surface, color, (tail_x, tail_y), radius)

        pygame.draw.circle(surface, (255, 120, 30), (x, y), int(self.size * 0.75))
        pygame.draw.circle(surface, (255, 200, 70), (x, y), int(self.size * 0.52))
        pygame.draw.circle(surface, (255, 255, 235), (x, y), int(self.size * 0.28))
        ctx.particles.embers(x, y, 1)

    def _draw_ice_beam(self, surface: pygame.Surface, ctx: "BattleContext") -> None:
        """Horizontal freezing ray: soft gradient body, white core, crystals."""
        t = self.age
        x = int(self.x)
        y = int(self.y)
        length = int(self.length)
        flicker = 1.0 + 0.12 * math.sin(t * 40.0)

        beam = pygame.Surface((max(4, length), 30), pygame.SRCALPHA)
        for half in range(11, 0, -1):
            ratio = (11 - half) / 11.0
            alpha = int(12 + 80 * ratio ** 1.3)
            pygame.draw.rect(
                beam, (90, 195, 255, alpha), (0, 15 - half, max(4, length), half * 2)
            )
        pygame.draw.rect(
            beam, (170, 235, 255, 210), (0, 13, max(4, length), max(2, int(4 * flicker)))
        )
        pygame.draw.rect(
            beam, (240, 252, 255, 235), (0, 14, max(4, length), 1)
        )
        surface.blit(beam, (x, y - 15))

        # Crystals drifting along the ray.
        for index in range(5):
            offset = int((t * 300.0 + index * length / 5.0) % max(1, length))
            size = max(2, int(4 + 2 * math.sin(t * 8.0 + index)))
            cx = x + offset
            pygame.draw.polygon(
                surface,
                (245, 255, 255),
                [(cx, y - size * 2), (cx + size, y), (cx, y + size * 2), (cx - size, y)],
            )

        # Glowing wavefront.
        for factor, color in ((2.2, (60, 140, 220)), (1.4, (150, 220, 255)), (0.7, (255, 255, 255))):
            pygame.draw.circle(
                surface, color, (x, y), max(2, int(13 * flicker * factor))
            )

        ctx.particles.ice_shatter(x, y, 0.12)

    def cover(self, soldier: "Soldier") -> bool:
        """Compatibility alias for :meth:`hits`."""
        return self.hits(soldier)


class MegaAttack:
    """The finale: a charging orb followed by a screen-wide blast."""

    CHARGE_TIME = 0.9
    BLAST_TIME = 0.7

    def __init__(self, side: str, x: float, y: float, width: int, height: int) -> None:
        self.side = side
        self.x = float(x)
        self.y = float(y)
        self.width = width
        self.height = height
        self.age = 0.0
        self.fired = False
        self.hit_applied = False

    @property
    def charging(self) -> bool:
        """True during the wind-up."""
        return self.age < self.CHARGE_TIME

    @property
    def charge_progress(self) -> float:
        """0..1 progress of the wind-up."""
        return min(1.0, self.age / self.CHARGE_TIME)

    @property
    def blast_progress(self) -> float:
        """0..1 progress of the blast."""
        return max(0.0, min(1.0, (self.age - self.CHARGE_TIME) / self.BLAST_TIME))

    @property
    def finished(self) -> bool:
        """True when the attack has fully played out."""
        return self.age > self.CHARGE_TIME + self.BLAST_TIME

    @property
    def colors(self) -> Tuple[Color, Color, Color]:
        """Inner, mid and outer colours for the current side."""
        if self.side == "red":
            return (255, 250, 220), (255, 150, 40), (220, 30, 20)
        return (255, 255, 255), (140, 220, 255), (40, 110, 230)

    def update(self, dt: float, ctx: "BattleContext") -> None:
        """Advance the animation and emit particles."""
        self.age += dt
        if self.charging:
            radius = 20.0 + 120.0 * self.charge_progress
            count = 3 if ctx.rng.random() < 0.7 else 6
            if self.side == "red":
                ctx.particles.emit(
                    self.x, self.y, count,
                    ((255, 230, 120), (255, 120, 30), (220, 40, 20)),
                    speed=260.0, lifetime=0.5, size=5.0, gravity=-80.0, glow=True,
                    jitter=radius * 0.4,
                )
            else:
                ctx.particles.emit(
                    self.x, self.y, count,
                    ((220, 250, 255), (120, 210, 255), (40, 110, 230)),
                    speed=260.0, lifetime=0.5, size=5.0, gravity=120.0, glow=True,
                    jitter=radius * 0.4,
                )
        elif not self.hit_applied:
            self.hit_applied = True
            self.fired = True
            if ctx.audio is not None:
                ctx.audio.play("mega_boom", 1.0, cooldown=0.0)
                ctx.audio.play("explosion", 1.0, cooldown=0.0)
            ctx.shake.trigger(15.0, 0.5)
            ctx.flash.trigger((255, 255, 255), 0.5)
            ctx.glitch.trigger(0.25, 1.4)
            ctx.particles.shatter(
                self.x, self.y, 90, self.colors, power=1.6
            )

    def draw(self, surface: pygame.Surface, ctx: "BattleContext") -> None:
        """Draw the charging orb or the expanding blast."""
        inner, mid, outer = self.colors
        cx, cy = int(self.x), int(self.y)

        if self.charging:
            progress = self.charge_progress
            radius = int(20 + 120 * progress)
            pulse = 1.0 + 0.1 * math.sin(self.age * 30.0)
            for step, color in ((1.6, outer), (1.2, mid), (0.75, inner)):
                pygame.draw.circle(
                    surface, color, (cx, cy), max(2, int(radius * step * pulse))
                )
            # Concentric rings sucking inward.
            for index in range(4):
                ring = int((radius * (2.2 - index * 0.35)) * (1.0 - progress * 0.4))
                if ring > 3:
                    pygame.draw.circle(
                        surface, mid, (cx, cy), ring, max(1, int(3 * (1.0 - progress)))
                    )
            return

        progress = self.blast_progress
        max_radius = int(math.hypot(self.width, self.height) * (0.25 + 1.25 * progress))
        alpha = int(255 * (1.0 - progress) ** 0.7)
        if alpha <= 0:
            return

        layer = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        for factor, color in ((1.0, outer), (0.72, mid), (0.42, inner)):
            radius = int(max_radius * factor)
            if radius <= 0:
                continue
            pygame.draw.circle(
                layer, (*color, max(0, min(255, alpha))), (cx, cy), radius
            )
        surface.blit(layer, (0, 0))

        # Shockwave rings.
        for index in range(3):
            ring_progress = (progress + index * 0.12) % 1.0
            radius = int(max_radius * (0.5 + ring_progress * 0.9))
            pygame.draw.circle(
                surface, (255, 255, 255), (cx, cy), radius,
                max(1, int(6 * (1.0 - ring_progress))),
            )
        del ctx
