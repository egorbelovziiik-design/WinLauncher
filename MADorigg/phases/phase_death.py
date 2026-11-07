"""Phase 5: a ten-second fake Windows crash screen (a prank, not a real BSOD).

Everything here is aimed at one thing: looking *indistinguishable* from the
real thing for ten seconds.

* exact Windows blue ``#0078D7`` and a perfectly flat, full-screen fill -
  no vignette, no grain, no scan lines, no tearing;
* the real Segoe UI Light / Segoe UI faces straight from ``C:\\Windows\\Fonts``
  (with a graceful fallback for other systems);
* the authentic Windows 11 layout: ``:(`` , the paragraph, ``NN% complete``,
  the white QR block with the support URL, then the stop-code block;
* percentages that only ever appear in **steps of five**, exactly like the
  Windows bug-check counter;
* absolute silence: the music is killed on the crash frame and nothing is
  played again until the show resumes.

The only liberty taken is the module name in ``What failed`` - the rest is
drawn to match a real screen shot pixel for pixel.
"""

from __future__ import annotations

import logging
import os
import random
from typing import Dict, List, Tuple

import pygame

import config
from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)

# Real Windows bug-check colours.
BSOD_BLUE: Tuple[int, int, int] = (0, 120, 215)
BSOD_TEXT: Tuple[int, int, int] = (255, 255, 255)

HEADLINE = (
    "Your device ran into a problem and needs to restart. "
    "We're just collecting some error info, and then we'll restart for you."
)
SUPPORT_URL = "For more information about this issue and possible fixes, visit https://www.windows.com/stopcode"
SUPPORT_LINE = "If you call a support person, give them this info:"
STOP_CODE = "Stop code: CRITICAL_PROCESS_DIED"
FAILED_MODULE = "What failed: MADorigg.sys"

# Windows counts in 5 % steps; the timing is deliberately irregular.
PROGRESS_STEPS: Tuple[Tuple[float, int], ...] = (
    (0.0, 0),
    (1.1, 0),
    (1.35, 5),
    (1.85, 10),
    (2.35, 15),
    (3.05, 20),
    (3.35, 20),
    (3.75, 25),
    (4.20, 30),
    (4.70, 35),
    (5.05, 40),
    (5.60, 45),
    (5.95, 50),
    (6.35, 50),
    (6.70, 55),
    (7.05, 60),
    (7.45, 65),
    (7.80, 70),
    (8.05, 75),
    (8.30, 80),
    (8.50, 85),
    (8.70, 90),
    (8.90, 95),
    (9.10, 100),
)

# (path, cache key) pairs, best match first.
_FONT_CANDIDATES: Tuple[str, ...] = (
    r"C:\Windows\Fonts\segoeuil.ttf",  # Segoe UI Light
    r"C:\Windows\Fonts\segoeuisl.ttf",  # Segoe UI Semilight
    r"C:\Windows\Fonts\segoeui.ttf",  # Segoe UI
    "/System/Library/Fonts/SFNSDisplay.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
)

_FONT_CACHE: Dict[Tuple[str, int], pygame.font.Font] = {}


def _font(size: int, *, regular: bool = False) -> pygame.font.Font:
    """Load Segoe UI (or the closest available face) at ``size``."""
    size = max(8, int(size))
    candidates = _FONT_CANDIDATES[2:] if regular else _FONT_CANDIDATES
    for path in candidates:
        key = (path, size)
        cached = _FONT_CACHE.get(key)
        if cached is not None:
            return cached
        if not os.path.exists(path):
            continue
        try:
            font = pygame.font.Font(path, size)
        except Exception:  # pragma: no cover - broken font file
            continue
        _FONT_CACHE[key] = font
        return font
    key = ("<default>", size)
    cached = _FONT_CACHE.get(key)
    if cached is None:
        cached = pygame.font.Font(None, size)
        _FONT_CACHE[key] = cached
    return cached


def _progress_at(t: float) -> int:
    """Windows-style percentage: jumps in fives, never goes backwards."""
    value = 0
    for when, percent in PROGRESS_STEPS:
        if t >= when:
            value = percent
        else:
            break
    return value


def _wrap(font: pygame.font.Font, text: str, max_width: int) -> List[str]:
    """Greedy word wrap using the real rendered width of the font."""
    words = text.split()
    lines: List[str] = []
    current = ""
    for word in words:
        trial = f"{current} {word}".strip()
        if font.size(trial)[0] <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def _draw_qr(surface: pygame.Surface, x: int, y: int, side: int) -> None:
    """Inverted (white-on-blue) QR block, like Windows draws it.

    Not a scannable code, but structurally correct: three finder patterns,
    an alignment pattern, timing rows and pseudo-random data modules.
    """
    modules = 29
    cell = max(2, side // modules)
    size = cell * modules
    rng = random.Random(0xB50D)

    def finder(col: int, row: int) -> None:
        pygame.draw.rect(
            surface, BSOD_TEXT, (x + col * cell, y + row * cell, 7 * cell, 7 * cell)
        )
        pygame.draw.rect(
            surface, BSOD_BLUE,
            (x + (col + 1) * cell, y + (row + 1) * cell, 5 * cell, 5 * cell),
        )
        pygame.draw.rect(
            surface, BSOD_TEXT,
            (x + (col + 2) * cell, y + (row + 2) * cell, 3 * cell, 3 * cell),
        )

    for row in range(modules):
        for col in range(modules):
            in_finder = (
                (row < 8 and col < 8)
                or (row < 8 and col >= modules - 8)
                or (row >= modules - 8 and col < 8)
            )
            if in_finder:
                continue
            if row == 6 or col == 6:
                if (row + col) % 2 == 0:
                    pygame.draw.rect(
                        surface, BSOD_TEXT, (x + col * cell, y + row * cell, cell, cell)
                    )
                continue
            if rng.random() < 0.47:
                pygame.draw.rect(
                    surface, BSOD_TEXT, (x + col * cell, y + row * cell, cell, cell)
                )

    finder(0, 0)
    finder(modules - 7, 0)
    finder(0, modules - 7)

    # Alignment pattern near the bottom-right corner.
    align_col, align_row = modules - 9, modules - 9
    pygame.draw.rect(
        surface, BSOD_TEXT,
        (x + align_col * cell, y + align_row * cell, 5 * cell, 5 * cell),
    )
    pygame.draw.rect(
        surface, BSOD_BLUE,
        (x + (align_col + 1) * cell, y + (align_row + 1) * cell, 3 * cell, 3 * cell),
    )
    pygame.draw.rect(
        surface, BSOD_TEXT,
        (x + (align_col + 2) * cell, y + (align_row + 2) * cell, cell, cell),
    )


def run(ctx: BattleContext) -> None:
    """Show the fake crash screen for ~10 seconds, then go dark like a reboot."""
    duration = config.PHASE_DEATH_DURATION
    started = ctx.elapsed
    width, height = ctx.width, ctx.height

    frozen = ctx.canvas.copy()
    ctx.particles.clear()
    ctx.glitch.duration = 0.0
    ctx.shake.reset()
    ctx.flash.duration = 0.0

    # The machine just died: cut the sound dead, like a real bug check.
    if ctx.audio is not None:
        try:
            ctx.audio.fade_master(0.0, 0.05)
            ctx.audio.stop_music(fade_ms=60)
        except Exception:  # pragma: no cover - defensive
            pass

    # --- typography (all sizes relative to 1080p, like the real screen) --- #
    headline_size = int(height * 0.0385)
    body_font = _font(headline_size)
    small_size = int(height * 0.0175)
    small_font = _font(small_size, regular=True)
    tiny_size = int(height * 0.0148)
    tiny_font = _font(tiny_size, regular=True)
    face_size = int(height * 0.178)
    face_font = _font(face_size)

    margin = int(width * 0.104)
    wrap_width = int(width * 0.52)
    paragraph = _wrap(body_font, HEADLINE, wrap_width)
    line_height = int(headline_size * 1.28)

    paragraph_top = int(height * 0.38)
    percent_top = int(height * 0.565)
    qr_side = int(width * 0.062)
    qr_x = margin
    qr_top = int(height * 0.655)
    qr_side = min(qr_side, height - qr_top - int(height * 0.09))
    column_x = qr_x + qr_side + int(width * 0.0065)
    url_top = int(height * 0.645)
    support_top = int(height * 0.705)
    stop_top = int(height * 0.742)
    failed_top = int(height * 0.775)

    def text(
        surface: pygame.Surface,
        font: pygame.font.Font,
        value: str,
        pos: Tuple[int, int],
        color: Tuple[int, int, int] = BSOD_TEXT,
    ) -> None:
        """Blit one antialiased line, no shadow, exactly like Windows."""
        image = font.render(value, True, color)
        surface.blit(image, image.get_rect(topleft=pos))

    crash_cut = 0.14  # a stalled frame, then the bug check
    black_at = duration - 0.75
    last_percent = -1

    while True:
        t = ctx.elapsed - started
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - started

        if t < crash_cut:
            # The last rendered frame freezes for a few dozen milliseconds -
            # exactly what a display does when the GPU stops presenting.
            ctx.canvas.blit(frozen, (0, 0))
            if int(t * 1000) % 4 < 2:
                band_y = int(height * 0.5)
                try:
                    strip = ctx.canvas.subsurface(
                        pygame.Rect(0, band_y, width, max(2, int(height * 0.03)))
                    ).copy()
                    ctx.canvas.blit(strip, (int(width * 0.004), band_y))
                except ValueError:  # pragma: no cover - degenerate rect
                    pass
        elif t >= black_at:
            ctx.canvas.fill((0, 0, 0))
        else:
            ctx.canvas.fill(BSOD_BLUE)

            text(ctx.canvas, face_font, ":(", (margin, int(height * 0.148)))

            line_y = paragraph_top
            for line in paragraph:
                text(ctx.canvas, body_font, line, (margin, line_y))
                line_y += line_height

            percent = _progress_at(t)
            if percent != last_percent:
                last_percent = percent
            text(
                ctx.canvas,
                body_font,
                f"{percent}% complete",
                (margin, percent_top),
            )

            _draw_qr(ctx.canvas, qr_x, qr_top, qr_side)
            text(ctx.canvas, small_font, SUPPORT_URL, (column_x, url_top))
            text(ctx.canvas, tiny_font, SUPPORT_LINE, (column_x, support_top))
            text(ctx.canvas, small_font, STOP_CODE, (column_x, stop_top))
            text(ctx.canvas, tiny_font, FAILED_MODULE, (column_x, failed_top))

        # Present without any of the show's post-processing (no vignette,
        # no flash, no glitch): a real BSOD is a flat, untouched picture.
        ctx.screen.present(ctx.canvas, (0, 0))
        pygame.display.flip()
        ctx._capture_tick()

    # Hand a clean black frame to the cleanup phase.
    ctx.canvas.fill((0, 0, 0))
    ctx.screen.present(ctx.canvas, (0, 0))
    pygame.display.flip()
    LOG.info("Fake crash screen finished after %.1f s", duration)


__all__ = ["run", "PROGRESS_STEPS", "BSOD_BLUE"]
