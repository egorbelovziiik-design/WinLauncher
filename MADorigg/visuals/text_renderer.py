"""Cached font rendering with glitch, pulse and outline effects.

Emoji are stripped automatically when the active font has no glyph for them
(Windows fonts usually lack colour emoji in pygame), so the show never renders
"tofu" boxes.
"""

from __future__ import annotations

import logging
import math
import random
from typing import Dict, Optional, Sequence, Tuple

import pygame

from config import BLACK, FONT_CANDIDATES, GOLD, WHITE

LOG = logging.getLogger(__name__)

Color = Tuple[int, int, int]
Point = Tuple[int, int]


class TextRenderer:
    """Font factory + text surface cache."""

    def __init__(self, rng: Optional[random.Random] = None) -> None:
        self.rng = rng or random.Random()
        self._fonts: Dict[int, pygame.font.Font] = {}
        self._cache: Dict[Tuple[str, int, Color], pygame.Surface] = {}
        self._glyph_cache: Dict[Tuple[str, int], bool] = {}
        self._font_path = self._pick_font_path()

    # ------------------------------------------------------------------ #
    # Fonts
    # ------------------------------------------------------------------ #
    @staticmethod
    def _pick_font_path() -> Optional[str]:
        """Find a bold system font that can render Cyrillic."""
        for name in FONT_CANDIDATES:
            try:
                path = pygame.font.match_font(name, bold=True)
            except Exception:  # pragma: no cover - defensive
                path = None
            if path:
                return path
        return None

    def font(self, size: int) -> pygame.font.Font:
        """Return a cached font for the requested pixel size."""
        size = max(8, int(size))
        cached = self._fonts.get(size)
        if cached is not None:
            return cached
        try:
            font = pygame.font.Font(self._font_path, size)
        except Exception:  # pragma: no cover - font loading edge cases
            font = pygame.font.Font(None, size)
        if self._font_path is None:
            font.set_bold(True)
        self._fonts[size] = font
        return font

    def _has_glyph(self, char: str, font: pygame.font.Font) -> bool:
        """Check glyph coverage using ``Font.metrics`` (None == missing)."""
        key = (char, font.get_height())
        if key in self._glyph_cache:
            return self._glyph_cache[key]
        try:
            metrics = font.metrics(char)
            supported = bool(metrics) and metrics[0] is not None
        except Exception:  # pragma: no cover - defensive
            supported = False
        self._glyph_cache[key] = supported
        return supported

    def sanitize(self, text: str, font: pygame.font.Font) -> str:
        """Drop characters the current font cannot draw (emoji, symbols)."""
        chars = []
        for char in text:
            if char == "\n":
                chars.append(char)
                continue
            if char == " " or self._has_glyph(char, font):
                chars.append(char)
            else:
                chars.append(" ")
        return "".join(chars)

    # ------------------------------------------------------------------ #
    # Rendering
    # ------------------------------------------------------------------ #
    def render(self, text: str, size: int, color: Color = WHITE) -> pygame.Surface:
        """Render (and cache) a single line of text."""
        font = self.font(size)
        label = self.sanitize(text, font)
        key = (label, size, color)
        cached = self._cache.get(key)
        if cached is not None:
            return cached
        surface = font.render(label, True, color)
        if len(self._cache) > 512:
            self._cache.clear()
        self._cache[key] = surface
        return surface

    def draw(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        pos: Point,
        color: Color = WHITE,
        anchor: str = "topleft",
        shadow: bool = True,
        shadow_offset: int = 3,
    ) -> pygame.Rect:
        """Draw text at ``pos`` using the given ``anchor``."""
        image = self.render(text, size, color)
        rect = image.get_rect(**{anchor: pos})
        if shadow:
            shadow_img = self.render(text, size, BLACK)
            surface.blit(shadow_img, (rect.x + shadow_offset, rect.y + shadow_offset))
        surface.blit(image, rect)
        return rect

    def draw_centered(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        center: Point,
        color: Color = WHITE,
        shadow: bool = True,
    ) -> pygame.Rect:
        """Convenience wrapper around :meth:`draw` with ``center`` anchor."""
        return self.draw(surface, text, size, center, color, anchor="center", shadow=shadow)

    # ------------------------------------------------------------------ #
    # Effects
    # ------------------------------------------------------------------ #
    def draw_glitch(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        center: Point,
        t: float,
        color: Color = WHITE,
        amplitude: int = 7,
        jitter: float = 1.0,
    ) -> pygame.Rect:
        """RGB-split, horizontally sliced glitch text."""
        font = self.font(size)
        label = self.sanitize(text, font)
        base = self.render(label, size, color)
        rect = base.get_rect(center=center)

        red = self.render(label, size, (255, 40, 60)).copy()
        blue = self.render(label, size, (60, 120, 255)).copy()
        red.set_alpha(110)
        blue.set_alpha(110)

        wobble = int(math.sin(t * 27.0) * amplitude * 0.25)
        dx = int(self.rng.uniform(-amplitude, amplitude) * jitter * 0.4) + wobble
        dy = int(self.rng.uniform(-2, 2) * jitter)
        surface.blit(red, (rect.x - abs(dx), rect.y + dy))
        surface.blit(blue, (rect.x + abs(dx), rect.y - dy))

        # Slice the main text horizontally for the classic datamosh look.
        if self.rng.random() < 0.7:
            height = base.get_height()
            for _ in range(2):
                slice_y = self.rng.randint(0, max(0, height - 6))
                slice_h = self.rng.randint(3, max(4, height // 5))
                try:
                    strip = base.subsurface(pygame.Rect(0, slice_y, base.get_width(), slice_h)).copy()
                except ValueError:  # pragma: no cover - degenerate slice
                    continue
                surface.blit(
                    strip,
                    (rect.x + self.rng.randint(-6, 6), rect.y + slice_y),
                )

        surface.blit(base, rect)
        return rect

    def draw_pulse(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        center: Point,
        t: float,
        color: Color = GOLD,
        pulse: float = 0.07,
        speed: float = 3.2,
        glitch: bool = False,
        amplitude: int = 5,
    ) -> pygame.Rect:
        """Pulsing headline, optionally with glitch ghosts."""
        scale = 1.0 + pulse * math.sin(t * speed)
        font = self.font(size)
        label = self.sanitize(text, font)
        base = self.render(label, size, color)
        scaled = pygame.transform.rotozoom(base, 0.0, max(0.2, scale))
        rect = scaled.get_rect(center=center)

        if glitch:
            # Chromatic ghosts scaled to match the pulsing base text.
            offset = max(2, int(amplitude * 0.7))
            for ghost_color, dx in (
                ((255, 45, 70), -offset),
                ((60, 120, 255), offset),
            ):
                ghost = pygame.transform.rotozoom(
                    self.render(label, size, ghost_color), 0.0, max(0.2, scale)
                )
                ghost.set_alpha(120)
                surface.blit(ghost, (rect.x + dx, rect.y))

        shadow = pygame.transform.rotozoom(self.render(label, size, BLACK), 0.0, max(0.2, scale))
        shadow.set_alpha(200)
        surface.blit(shadow, (rect.x + 4, rect.y + 4))
        surface.blit(scaled, rect)
        return rect

    def draw_outlined(
        self,
        surface: pygame.Surface,
        text: str,
        size: int,
        center: Point,
        color: Color = WHITE,
        outline: Color = BLACK,
        thickness: int = 3,
    ) -> pygame.Rect:
        """Text with a hard outline (readable on any background)."""
        image = self.render(text, size, color)
        outline_image = self.render(text, size, outline)
        rect = image.get_rect(center=center)
        for dx in range(-thickness, thickness + 1, max(1, thickness)):
            for dy in range(-thickness, thickness + 1, max(1, thickness)):
                if dx or dy:
                    surface.blit(outline_image, (rect.x + dx, rect.y + dy))
        surface.blit(image, rect)
        return rect

    def draw_multiline(
        self,
        surface: pygame.Surface,
        lines: Sequence[str],
        size: int,
        center: Point,
        color: Color = WHITE,
        line_gap: float = 1.2,
    ) -> None:
        """Draw several centred lines stacked vertically."""
        line_height = int(size * line_gap)
        total = line_height * len(lines)
        start_y = center[1] - total // 2 + line_height // 2
        for index, line in enumerate(lines):
            self.draw_centered(
                surface, line, size, (center[0], start_y + index * line_height), color
            )
