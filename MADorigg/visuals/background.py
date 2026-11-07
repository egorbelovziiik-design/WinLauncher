"""Arena backgrounds: red and blue gradients, lightning, glowing floor."""

from __future__ import annotations

import math
import random
from typing import List, Optional, Tuple

import numpy as np
import pygame

from config import (
    BLACK,
    BLUE_BRIGHT,
    BLUE_DEEP,
    BLUE_PRIMARY,
    RED_BRIGHT,
    RED_DEEP,
    RED_PRIMARY,
    WHITE,
)

Color = Tuple[int, int, int]


def _gradient_half(
    width: int,
    height: int,
    outer: Color,
    inner: Color,
    accent: Color,
    flip: bool = False,
) -> pygame.Surface:
    """Build a static gradient that is bright at the arena seam.

    The aura hotspot sits at 34 % of the half width, exactly where the boss
    stands; ``flip`` mirrors the image for the blue side so both halves are
    perfectly symmetric.
    """
    xs = np.linspace(0.0, 1.0, width, dtype=np.float32)[:, None]
    ys = np.linspace(0.0, 1.0, height, dtype=np.float32)[None, :]
    horizontal = xs ** 1.8
    vertical = 0.45 + 0.55 * np.clip(
        np.sin(np.pi * np.clip(ys, 0.0, 1.0)), 0.0, 1.0
    ) ** 0.6
    field = np.clip((0.16 + 0.84 * horizontal) * vertical, 0.0, 1.0)

    array = np.zeros((width, height, 3), dtype=np.float32)
    for channel in range(3):
        array[:, :, channel] = outer[channel] + (inner[channel] - outer[channel]) * field

    # Localised aura around the boss.
    distance = ((xs - 0.34) ** 2 * 5.0) + ((ys - 0.42) ** 2 * 11.0)
    glow = np.exp(-distance * 2.2) * 0.65
    for channel in range(3):
        array[:, :, channel] += accent[channel] * glow

    surface = pygame.surfarray.make_surface(np.clip(array, 0, 255).astype(np.uint8))
    if flip:
        surface = pygame.transform.flip(surface, True, False)
    return surface


class BackgroundRenderer:
    """Draws the two warring halves of the screen and the battle floor."""

    def __init__(self, size: Tuple[int, int], rng: Optional[random.Random] = None) -> None:
        self.width, self.height = size
        self.rng = rng or random.Random()
        self.half_width = max(2, self.width // 2)

        self._red_half = _gradient_half(
            self.half_width, self.height, RED_DEEP, (150, 12, 8), RED_BRIGHT
        )
        self._blue_half = _gradient_half(
            self.half_width, self.height, BLUE_DEEP, (8, 30, 110), BLUE_BRIGHT,
            flip=True,
        )
        self._floor = self._build_floor()

    # ------------------------------------------------------------------ #
    # Static caches
    # ------------------------------------------------------------------ #
    def _build_floor(self) -> pygame.Surface:
        """Dark floor band with a subtle horizon glow."""
        surface = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        horizon = int(self.height * 0.78)
        band_height = max(1, self.height - horizon)
        for index in range(band_height):
            k = index / max(1, band_height - 1)
            alpha = int(200 * k ** 0.7)
            pygame.draw.line(
                surface,
                (5, 5, 12, alpha),
                (0, horizon + index),
                (self.width, horizon + index),
            )
        return surface

    # ------------------------------------------------------------------ #
    # Intro
    # ------------------------------------------------------------------ #
    def draw_intro(self, surface: pygame.Surface, t: float) -> None:
        """Near-black background with a slow heartbeat glow."""
        surface.fill((3, 3, 6))
        pulse = 0.5 + 0.5 * math.sin(t * 2.2)
        radius = int(min(self.width, self.height) * (0.16 + 0.03 * pulse))
        glow = pygame.Surface((radius * 2, radius * 2), pygame.SRCALPHA)
        for step in range(6, 0, -1):
            alpha = int(10 * pulse * step / 6.0)
            pygame.draw.circle(
                glow, (120, 0, 0, alpha), (radius, radius), int(radius * step / 6.0)
            )
        surface.blit(glow, (self.width // 2 - radius, self.height // 2 - radius))

    # ------------------------------------------------------------------ #
    # Arena
    # ------------------------------------------------------------------ #
    def draw_arena(
        self,
        surface: pygame.Surface,
        t: float,
        red_alpha: float = 1.0,
        blue_alpha: float = 1.0,
        pulse: float = 0.0,
    ) -> None:
        """Draw both gradients (with fade-in), the horizon and scanlines."""
        surface.fill((4, 4, 8))
        red_alpha = max(0.0, min(1.0, red_alpha))
        blue_alpha = max(0.0, min(1.0, blue_alpha))

        if red_alpha > 0.0:
            self._red_half.set_alpha(int(255 * red_alpha))
            surface.blit(self._red_half, (0, 0))
        if blue_alpha > 0.0:
            self._blue_half.set_alpha(int(255 * blue_alpha))
            surface.blit(self._blue_half, (self.half_width, 0))

        # Pulsing energy near the centre seam.
        if pulse > 0.0:
            seam = self.half_width
            aura = pygame.Surface((240, self.height), pygame.SRCALPHA)
            aura.fill((0, 0, 0, 0))
            for radius in range(120, 0, -12):
                alpha = int(10 * pulse * (120 - radius) / 120.0)
                pygame.draw.rect(
                    aura, (200, 200, 255, alpha), (120 - radius, 0, radius * 2, self.height)
                )
            surface.blit(aura, (seam - 120, 0))

        # Horizon line.
        horizon = int(self.height * 0.78)
        pygame.draw.line(surface, (255, 90, 60), (0, horizon), (self.half_width, horizon), 2)
        pygame.draw.line(
            surface, (90, 150, 255), (self.half_width, horizon), (self.width, horizon), 2
        )

        # Slow sweeping highlight along the horizon for a bit of motion.
        sweep = int((t * 90.0) % (self.width * 1.4)) - self.width * 0.2
        for index in range(2):
            x = sweep + index * self.width * 0.7
            pygame.draw.line(
                surface, (80, 24, 20), (int(x), horizon - 2), (int(x + 180), horizon - 2), 2
            )
            pygame.draw.line(
                surface, (20, 40, 90), (int(x + 200), horizon + 2), (int(x + 380), horizon + 2), 2
            )

    def draw_floor(self, surface: pygame.Surface, t: float) -> None:
        """Draw the darkened ground band with a moving highlight."""
        surface.blit(self._floor, (0, 0))
        horizon = int(self.height * 0.78)
        y = int(self.height * 0.94)
        pygame.draw.line(surface, (30, 30, 42), (0, y), (self.width, y), 2)
        shimmer = int(120 + 60 * math.sin(t * 4.0))
        pygame.draw.line(
            surface,
            (shimmer, shimmer // 3, shimmer // 4),
            (0, horizon),
            (self.half_width, horizon),
            1,
        )

    # ------------------------------------------------------------------ #
    # Lightning
    # ------------------------------------------------------------------ #
    def bolt_points(
        self,
        x: float,
        y0: float = 0.0,
        y1: Optional[float] = None,
        segments: int = 14,
        jitter: float = 26.0,
    ) -> List[Tuple[int, int]]:
        """Generate a jagged vertical polyline for a lightning bolt."""
        y1 = self.height if y1 is None else y1
        points: List[Tuple[int, int]] = []
        for index in range(segments + 1):
            k = index / segments
            px = x + self.rng.uniform(-jitter, jitter) * (0.35 + 0.65 * math.sin(math.pi * k))
            points.append((int(px), int(y0 + (y1 - y0) * k)))
        return points

    def draw_lightning(
        self,
        surface: pygame.Surface,
        x: float,
        intensity: float = 1.0,
        color: Color = (220, 235, 255),
        branches: int = 2,
        jitter: float = 26.0,
    ) -> None:
        """Draw a layered lightning bolt with optional branches."""
        if intensity <= 0.0:
            return
        points = self.bolt_points(x, jitter=jitter)
        pygame.draw.lines(surface, (60, 90, 200), False, points, 9)
        pygame.draw.lines(surface, (160, 190, 255), False, points, 4)
        pygame.draw.lines(surface, color, False, points, 2)

        for _ in range(branches):
            start_index = self.rng.randint(2, max(2, len(points) - 3))
            sx, sy = points[start_index]
            end_x = sx + self.rng.uniform(-90, 90)
            end_y = sy + self.rng.uniform(40, 160)
            branch = [(sx, sy), (int((sx + end_x) / 2 + self.rng.uniform(-18, 18)),
                                 int((sy + end_y) / 2)), (int(end_x), int(end_y))]
            pygame.draw.lines(surface, (170, 200, 255), False, branch, 2)

        # Impact glow on the floor.
        glow_radius = int(40 * intensity)
        if glow_radius > 2:
            glow = pygame.Surface((glow_radius * 2, glow_radius * 2), pygame.SRCALPHA)
            pygame.draw.circle(
                glow, (*color, 70), (glow_radius, glow_radius), glow_radius
            )
            surface.blit(glow, (points[-1][0] - glow_radius, points[-1][1] - glow_radius))

    def draw_blast_ring(
        self, surface: pygame.Surface, x: float, y: float, radius: float, color: Color
    ) -> None:
        """Expanding shockwave ring."""
        if radius < 2:
            return
        width = max(2, int(radius * 0.05))
        pygame.draw.circle(surface, color, (int(x), int(y)), int(radius), width)
        pygame.draw.circle(
            surface,
            (255, 255, 255),
            (int(x), int(y)),
            max(1, int(radius * 0.85)),
            max(1, width // 2),
        )
