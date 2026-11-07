"""The flickering front line that decides who is winning."""

from __future__ import annotations

import logging
import random
from typing import List, Optional, Tuple

import pygame

from config import GOLD, WHITE

LOG = logging.getLogger(__name__)


class FrontLine:
    """Vertical zig-zag boundary that drifts toward the stronger army."""

    def __init__(self, width: int, height: int, rng: Optional[random.Random] = None) -> None:
        self.width = width
        self.height = height
        self.rng = rng or random.Random()
        self.position = 0.5  # 0.0 = far left, 1.0 = far right
        self.margin = width * 0.06
        self._spark_timer = 0.0

    # ------------------------------------------------------------------ #
    # Geometry
    # ------------------------------------------------------------------ #
    @property
    def x(self) -> float:
        """Screen x-coordinate of the line."""
        usable = max(1.0, self.width - 2.0 * self.margin)
        return self.margin + self.position * usable

    def reset(self) -> None:
        """Put the line back in the middle."""
        self.position = 0.5

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(self, dt: float, red_count: int, blue_count: int) -> None:
        """Drift toward the side with more soldiers, with a pull to the centre."""
        difference = red_count - blue_count
        normalised = max(-1.0, min(1.0, difference / 25.0))
        drift = 0.035 * normalised
        drift += (0.5 - self.position) * 0.12  # gentle balance spring
        drift += self.rng.uniform(-0.012, 0.012)
        self.position += drift * dt
        self.position = max(0.06, min(0.94, self.position))

    # ------------------------------------------------------------------ #
    # Rendering
    # ------------------------------------------------------------------ #
    def draw(self, surface: pygame.Surface, ctx) -> None:
        """Draw a shimmering lightning-like boundary."""
        x = self.x
        jitter = 9.0
        points: List[Tuple[int, int]] = []
        step = max(24, self.height // 26)
        for y in range(-10, self.height + step, step):
            offset = self.rng.uniform(-jitter, jitter)
            points.append((int(x + offset), min(self.height, y)))

        # Soft dark glow, gold core, white hot centre.
        pygame.draw.lines(surface, (90, 60, 0), False, points, 9)
        pygame.draw.lines(surface, GOLD, False, points, 4)
        pygame.draw.lines(surface, WHITE, False, points, 1)

        # Nodes.
        for index, (px, py) in enumerate(points):
            if index % 4 == 0:
                pygame.draw.circle(surface, (255, 240, 180), (px, py), 3)
                pygame.draw.circle(surface, GOLD, (px, py), 6, 1)

        # Sparks along the line.
        self._spark_timer += ctx.last_dt if hasattr(ctx, "last_dt") else 0.016
        if self._spark_timer > 0.05:
            self._spark_timer = 0.0
            ctx.particles.spark(x + self.rng.uniform(-6, 6), self.rng.uniform(0, self.height), 2)
