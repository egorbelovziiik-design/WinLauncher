"""Full-screen effects: flashes, screen shake, RGB glitch and vignette."""

from __future__ import annotations

import logging
import math
import random
from typing import Optional, Tuple

import numpy as np
import pygame

from config import VIGNETTE_STRENGTH

LOG = logging.getLogger(__name__)

Color = Tuple[int, int, int]


class Flash:
    """Colour overlay that fades out over a fixed duration."""

    def __init__(self) -> None:
        self.color: Color = (255, 255, 255)
        self.duration: float = 0.0
        self.age: float = 0.0
        self._overlay: Optional[pygame.Surface] = None
        self._overlay_size: Tuple[int, int] = (0, 0)

    @property
    def active(self) -> bool:
        """True while the flash is still visible."""
        return self.age < self.duration

    @property
    def alpha(self) -> int:
        """Current overlay opacity (0-255)."""
        if not self.active or self.duration <= 0:
            return 0
        return int(255 * (1.0 - self.age / self.duration))

    def trigger(self, color: Color = (255, 255, 255), duration: float = 0.3) -> None:
        """Start (or restart) a flash."""
        self.color = color
        self.duration = max(0.01, float(duration))
        self.age = 0.0

    def update(self, dt: float) -> None:
        """Advance the fade."""
        if self.active:
            self.age += dt

    def draw(self, surface: pygame.Surface) -> None:
        """Blit the overlay onto ``surface``."""
        if not self.active:
            return
        size = surface.get_size()
        if self._overlay is None or self._overlay_size != size:
            self._overlay = pygame.Surface(size).convert()
            self._overlay_size = size
        self._overlay.fill(self.color)
        self._overlay.set_alpha(self.alpha)
        surface.blit(self._overlay, (0, 0))


class ScreenShake:
    """Random camera offset that decays to zero."""

    def __init__(self, rng: Optional[random.Random] = None) -> None:
        self.rng = rng or random.Random()
        self.amplitude = 0.0
        self.duration = 0.0
        self.age = 0.0
        self._offset: Tuple[int, int] = (0, 0)

    @property
    def active(self) -> bool:
        """True while the camera still shakes."""
        return self.age < self.duration

    def trigger(self, amplitude: float = 6.0, duration: float = 0.25) -> None:
        """Start a shake, keeping the strongest one if already running."""
        remaining = self.duration - self.age
        if self.active and amplitude * remaining < self.amplitude * (self.duration - self.age):
            return
        self.amplitude = max(0.0, amplitude)
        self.duration = max(0.01, duration)
        self.age = 0.0

    def update(self, dt: float) -> None:
        """Advance the shake and recompute the current offset."""
        if not self.active:
            self._offset = (0, 0)
            return
        self.age += dt
        k = max(0.0, 1.0 - self.age / self.duration)
        magnitude = self.amplitude * k
        self._offset = (
            int(self.rng.uniform(-magnitude, magnitude)),
            int(self.rng.uniform(-magnitude, magnitude)),
        )

    def offset(self) -> Tuple[int, int]:
        """Current camera offset in pixels."""
        return self._offset

    def reset(self) -> None:
        """Stop shaking immediately."""
        self.duration = 0.0
        self.age = 0.0
        self._offset = (0, 0)


class Glitch:
    """Horizontal band displacement + RGB channel split."""

    def __init__(self, rng: Optional[random.Random] = None) -> None:
        self.rng = rng or random.Random()
        self.duration = 0.0
        self.age = 0.0
        self.intensity = 1.0

    @property
    def active(self) -> bool:
        """True while the glitch is running."""
        return self.age < self.duration

    def trigger(self, duration: float = 0.2, intensity: float = 1.0) -> None:
        """Start a glitch burst."""
        self.duration = max(0.01, float(duration))
        self.age = 0.0
        self.intensity = max(0.2, float(intensity))

    def update(self, dt: float) -> None:
        """Advance the burst timer."""
        if self.active:
            self.age += dt

    def apply(self, surface: pygame.Surface) -> None:
        """Distort ``surface`` in place."""
        if not self.active or self.duration <= 0:
            return
        strength = 1.0 - self.age / self.duration
        width, height = surface.get_size()
        rng = self.rng

        try:
            source = surface.copy()
        except Exception:  # pragma: no cover - defensive
            return

        # --- RGB channel split ------------------------------------------ #
        split = int(max(2, 12 * self.intensity * strength))
        red = source.copy()
        red.fill((255, 40, 40), special_flags=pygame.BLEND_MULT)
        blue = source.copy()
        blue.fill((40, 90, 255), special_flags=pygame.BLEND_MULT)
        red.set_alpha(120)
        blue.set_alpha(120)
        surface.blit(red, (split, 0), special_flags=pygame.BLEND_RGBA_ADD)
        surface.blit(blue, (-split, 0), special_flags=pygame.BLEND_RGBA_ADD)

        # --- torn horizontal bands -------------------------------------- #
        # Strips are drawn *over* the untouched frame, so the screen never
        # gets black holes - it just looks like the picture slipped.
        bands = int(4 + 6 * self.intensity)
        max_shift = int(26 * self.intensity)
        for _ in range(bands):
            band_height = rng.randint(3, max(4, int(height * 0.05)))
            y = rng.randint(0, max(0, height - band_height))
            shift = rng.randint(-max_shift, max_shift)
            rect = pygame.Rect(0, y, width, band_height)
            try:
                strip = source.subsurface(rect).copy()
            except ValueError:  # pragma: no cover - degenerate rect
                continue
            surface.blit(strip, (shift, y))

        # --- occasional bright scanline --------------------------------- #
        if rng.random() < 0.4:
            y = rng.randint(0, max(0, height - 3))
            pygame.draw.line(surface, (200, 255, 255), (0, y), (width, y), 2)


class Vignette:
    """Static radial darkening mask, precomputed once per resolution."""

    def __init__(self, strength: float = VIGNETTE_STRENGTH) -> None:
        self.strength = max(0.0, min(0.95, strength))
        self._mask: Optional[pygame.Surface] = None
        self._size: Tuple[int, int] = (0, 0)

    def _build(self, size: Tuple[int, int]) -> pygame.Surface:
        width, height = size
        ys, xs = np.mgrid[0:height, 0:width].astype(np.float32)
        nx = (xs - width * 0.5) / max(1.0, width * 0.5)
        ny = (ys - height * 0.5) / max(1.0, height * 0.5)
        radius = np.sqrt(nx * nx + ny * ny) / math.sqrt(2.0)
        factor = 1.0 - self.strength * np.clip(radius * 1.12, 0.0, 1.0) ** 2.1
        factor = np.clip(factor, 0.05, 1.0)
        # pygame.surfarray expects [x][y][rgb] -> transpose the (h, w) mask.
        rgb = np.repeat((factor.T * 255.0).astype(np.uint8)[:, :, None], 3, axis=2)
        return pygame.surfarray.make_surface(np.ascontiguousarray(rgb))

    def draw(self, surface: pygame.Surface) -> None:
        """Multiply the canvas with the cached vignette mask."""
        size = surface.get_size()
        if self._mask is None or self._size != size:
            self._mask = self._build(size)
            self._size = size
        surface.blit(self._mask, (0, 0), special_flags=pygame.BLEND_MULT)
