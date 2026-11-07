"""Procedural "live wallpaper" that replaces the victim's desktop.

The wallpaper is what makes the prank work: a warm gypsy-camp backdrop with a
gold ornament lattice, a wooden floor, a spotlight and beat-driven sound rings
that visually come *out of the wallpaper itself*.
"""

from __future__ import annotations

import math
import random
from typing import Optional, Tuple

import pygame

Color = Tuple[int, int, int]

WALL_TOP: Color = (26, 8, 12)
WALL_MID: Color = (58, 14, 18)
WALL_BOTTOM: Color = (104, 26, 20)
GOLD: Color = (214, 170, 74)
GOLD_DIM: Color = (128, 96, 40)


class WallpaperRenderer:
    """Pre-renders the static layers once and animates the light show per frame."""

    def __init__(self, size: Tuple[int, int], rng: Optional[random.Random] = None) -> None:
        self.width, self.height = size
        self.rng = rng or random.Random()
        self._base = self._build_base()
        self._ornament = self._build_ornament()
        self._floor = self._build_floor()
        self._curtains = self._build_curtains()
        self._spot = self._build_spot()
        self._ring = self._build_ring()

    # ------------------------------------------------------------------ #
    # Static layers
    # ------------------------------------------------------------------ #
    def _build_base(self) -> pygame.Surface:
        """Vertical burgundy gradient with a warm pool of light in the middle."""
        surface = pygame.Surface((self.width, self.height)).convert()
        for y in range(self.height):
            k = y / max(1, self.height - 1)
            if k < 0.6:
                mix = k / 0.6
                color = tuple(
                    int(WALL_TOP[c] + (WALL_MID[c] - WALL_TOP[c]) * mix) for c in range(3)
                )
            else:
                mix = (k - 0.6) / 0.4
                color = tuple(
                    int(WALL_MID[c] + (WALL_BOTTOM[c] - WALL_MID[c]) * mix) for c in range(3)
                )
            pygame.draw.line(surface, color, (0, y), (self.width, y))

        # Warm central glow.
        glow = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        cx, cy = self.width // 2, int(self.height * 0.52)
        radius = int(min(self.width, self.height) * 0.55)
        for step in range(18, 0, -1):
            r = int(radius * step / 18.0)
            alpha = int(3 + 10 * (1.0 - step / 18.0))
            pygame.draw.circle(glow, (255, 170, 90, alpha), (cx, cy), r)
        surface.blit(glow, (0, 0))
        return surface

    def _build_ornament(self) -> pygame.Surface:
        """Seamless gold diamond-lattice tile."""
        tile = pygame.Surface((160, 160), pygame.SRCALPHA)
        for cx, cy in ((0, 0), (160, 0), (0, 160), (160, 160), (80, 80)):
            points = [(cx, cy - 46), (cx + 46, cy), (cx, cy + 46), (cx - 46, cy)]
            pygame.draw.polygon(tile, (*GOLD_DIM, 26), points, 2)
            pygame.draw.polygon(
                tile, (*GOLD_DIM, 14),
                [(cx, cy - 24), (cx + 24, cy), (cx, cy + 24), (cx - 24, cy)], 1,
            )
            pygame.draw.circle(tile, (*GOLD, 30), (cx, cy), 3)
        return tile

    def _build_floor(self) -> pygame.Surface:
        """Wooden planks with a soft reflection band."""
        floor = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        top = int(self.height * 0.74)
        plank_h = max(6, int(self.height * 0.028))
        y = top
        index = 0
        while y < self.height:
            shade = 58 + (index % 3) * 12
            color = (shade, int(shade * 0.42), int(shade * 0.26), 235)
            pygame.draw.rect(floor, color, (0, y, self.width, plank_h))
            pygame.draw.line(
                floor, (18, 8, 6, 220), (0, y), (self.width, y), 2
            )
            # Plank seams, offset per row for a hand-laid look.
            step = max(80, int(self.width * 0.12))
            offset = (index % 2) * step // 2
            for x in range(-step, self.width + step, step):
                pygame.draw.line(
                    floor, (20, 9, 6, 160),
                    (x + offset, y), (x + offset, y + plank_h), 2,
                )
            y += plank_h
            index += 1

        # Warm sheen on the boards.
        sheen = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        for step in range(6):
            alpha = 16 - step * 2
            pygame.draw.ellipse(
                sheen, (255, 190, 120, max(0, alpha)),
                (int(self.width * 0.18) - step * 30,
                 top + int(self.height * 0.02),
                 int(self.width * 0.64) + step * 60,
                 int(self.height * 0.12) + step * 10),
            )
        floor.blit(sheen, (0, 0))
        return floor

    def _build_curtains(self) -> pygame.Surface:
        """Two heavy drapes framing the stage."""
        layer = pygame.Surface((self.width, self.height), pygame.SRCALPHA)
        drape_w = int(self.width * 0.11)
        for side in (0, 1):
            x0 = 0 if side == 0 else self.width - drape_w
            for column in range(drape_w):
                k = column / max(1, drape_w - 1)
                fold = 0.55 + 0.45 * abs(math.sin(k * math.pi * 3.2))
                alpha = int(215 * (1.0 - 0.45 * k if side == 0 else 0.55 + 0.45 * k))
                shade = int(96 * fold)
                pygame.draw.line(
                    layer, (max(0, shade), int(shade * 0.28), int(shade * 0.3), max(0, alpha)),
                    (x0 + column, 0), (x0 + column, self.height),
                )
        # Gold valance across the top of each drape.
        for side in (0, 1):
            x0 = 0 if side == 0 else self.width - drape_w
            pygame.draw.rect(
                layer, (150, 116, 48, 120), (x0, 0, drape_w, int(self.height * 0.035))
            )
        return layer

    def _build_spot(self) -> pygame.Surface:
        """Radial spotlight used behind the dancer."""
        size = int(min(self.width, self.height) * 0.95)
        spot = pygame.Surface((size, size), pygame.SRCALPHA)
        for step in range(20, 0, -1):
            r = max(2, int(size / 2 * step / 20.0))
            alpha = int(4 + 9 * (1.0 - step / 20.0))
            pygame.draw.circle(spot, (255, 210, 150, alpha), (size // 2, size // 2), r)
        return spot

    def _build_ring(self) -> pygame.Surface:
        """Sound-wave ring that pulses out of the wallpaper."""
        size = 256
        ring = pygame.Surface((size, size), pygame.SRCALPHA)
        pygame.draw.circle(ring, (255, 200, 120, 90), (size // 2, size // 2), size // 2 - 3, 3)
        pygame.draw.circle(ring, (255, 230, 180, 45), (size // 2, size // 2), size // 2 - 12, 2)
        return ring

    # ------------------------------------------------------------------ #
    # Per-frame drawing
    # ------------------------------------------------------------------ #
    def draw(
        self,
        surface: pygame.Surface,
        t: float,
        *,
        pulse: float = 0.0,
        brightness: float = 1.0,
        focus: Optional[Tuple[float, float]] = None,
        rings: float = 0.0,
    ) -> None:
        """Draw the wallpaper with beat pulsing and sound rings."""
        surface.blit(self._base, (0, 0))

        # Ornament lattice, very slowly breathing with the beat.
        tile = self._ornament
        alpha = int(150 + 70 * pulse)
        tile.set_alpha(alpha)
        tw, th = tile.get_size()
        for y in range(0, self.height, th):
            offset = (tw // 2) if (y // th) % 2 else 0
            for x in range(-tw, self.width + tw, tw):
                surface.blit(tile, (x + offset, y))

        # Spotlight behind the dancer, growing with the beat.
        if focus is not None:
            scale = 1.0 + 0.06 * pulse
            spot = self._spot
            sw = int(spot.get_width() * scale)
            sh = int(spot.get_height() * scale)
            scaled = pygame.transform.smoothscale(spot, (sw, sh))
            surface.blit(scaled, scaled.get_rect(center=focus))

            # Sound rings radiating from the wallpaper.
            if rings > 0.01:
                diameter = int(min(self.width, self.height) * (0.35 + 0.9 * (1.0 - rings)))
                ring = pygame.transform.smoothscale(self._ring, (diameter, diameter))
                ring.set_alpha(int(200 * rings))
                surface.blit(ring, ring.get_rect(center=focus))

        surface.blit(self._floor, (0, 0))
        surface.blit(self._curtains, (0, 0))

        if brightness < 0.999:
            shade = pygame.Surface((self.width, self.height)).convert()
            shade.fill((0, 0, 0))
            shade.set_alpha(int(255 * (1.0 - brightness)))
            surface.blit(shade, (0, 0))
