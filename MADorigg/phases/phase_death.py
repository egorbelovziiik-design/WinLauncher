"""Phase 5: a ten-second fake Windows crash screen (a prank, not a real BSOD).

The whole thing is drawn procedurally with ``pygame.draw`` and ``pygame.font``:
a convincing blue screen with a sad face, wrapped paragraph, an erratic
"NN% complete" counter, a stop code, a fake QR block, scanlines, film grain,
occasional tearing and finally a CRT power-off collapse.
"""

from __future__ import annotations

import logging
import math
import random
from typing import List, Tuple

import pygame

import config
from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)

BSOD_BLUE: Tuple[int, int, int] = (0, 103, 206)
BSOD_TEXT: Tuple[int, int, int] = (255, 255, 255)

HEADLINE = (
    "Your device ran into a problem and needs to restart. "
    "We're just collecting some error info, and then we'll restart for you."
)
STOP_CODE = "Stop code: CRITICAL_PROCESS_DIED"
FAILED_MODULE = "What failed: MADorigg.sys"
FOOTER = "For more information about this issue and possible fixes, visit https://www.windows.com/stopcode"

# (time in seconds, progress percent) - irregular, like a real machine.
PROGRESS_STEPS: Tuple[Tuple[float, int], ...] = (
    (0.7, 0),
    (1.2, 3),
    (1.7, 9),
    (2.2, 12),
    (2.9, 12),
    (3.4, 18),
    (3.9, 22),
    (4.5, 22),
    (5.0, 31),
    (5.5, 38),
    (6.0, 45),
    (6.5, 45),
    (6.9, 57),
    (7.3, 64),
    (7.7, 73),
    (8.0, 81),
    (8.25, 88),
    (8.45, 94),
    (8.6, 100),
)


def _progress_at(t: float) -> int:
    """Interpolate the fake completion percentage for the given time."""
    if t <= PROGRESS_STEPS[0][0]:
        return 0
    for index in range(1, len(PROGRESS_STEPS)):
        prev_t, prev_v = PROGRESS_STEPS[index - 1]
        cur_t, cur_v = PROGRESS_STEPS[index]
        if t <= cur_t:
            span = max(1e-3, cur_t - prev_t)
            k = (t - prev_t) / span
            return int(round(prev_v + (cur_v - prev_v) * k))
    return 100


def _wrap(ctx: BattleContext, text: str, size: int, max_width: int) -> List[str]:
    """Greedy word wrap using the real rendered width of the font."""
    words = text.split()
    lines: List[str] = []
    current = ""
    for word in words:
        trial = f"{current} {word}".strip()
        if ctx.text.render(trial, size, BSOD_TEXT).get_width() <= max_width or not current:
            current = trial
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def _draw_qr(ctx: BattleContext, surface: pygame.Surface, x: int, y: int, side: int) -> None:
    """Deterministic fake QR block (three finder patterns + noise modules)."""
    modules = 21
    cell = max(2, side // modules)
    side = cell * modules
    rng = random.Random(20260214)
    pygame.draw.rect(surface, BSOD_TEXT, (x - cell, y - cell, side + 2 * cell, side + 2 * cell))

    def finder(cx: int, cy: int) -> None:
        pygame.draw.rect(
            surface, (0, 0, 0), (x + cx * cell, y + cy * cell, 7 * cell, 7 * cell)
        )
        pygame.draw.rect(
            surface, BSOD_TEXT,
            (x + (cx + 1) * cell, y + (cy + 1) * cell, 5 * cell, 5 * cell),
        )
        pygame.draw.rect(
            surface, (0, 0, 0),
            (x + (cx + 2) * cell, y + (cy + 2) * cell, 3 * cell, 3 * cell),
        )

    for row in range(modules):
        for col in range(modules):
            in_finder = (
                (row < 7 and col < 7)
                or (row < 7 and col >= modules - 7)
                or (row >= modules - 7 and col < 7)
            )
            if in_finder:
                continue
            if rng.random() < 0.46:
                pygame.draw.rect(
                    surface, (0, 0, 0), (x + col * cell, y + row * cell, cell, cell)
                )

    finder(0, 0)
    finder(modules - 7, 0)
    finder(0, modules - 7)


def run(ctx: BattleContext) -> None:
    """Show the fake crash screen for ~10 seconds, then collapse like a CRT."""
    duration = config.PHASE_DEATH_DURATION
    started = ctx.elapsed
    width, height = ctx.width, ctx.height

    ctx.particles.clear()
    ctx.glitch.duration = 0.0
    ctx.shake.reset()

    # --- precomputed overlays ------------------------------------------- #
    scanlines = pygame.Surface((width, height), pygame.SRCALPHA)
    for y in range(0, height, 3):
        pygame.draw.line(scanlines, (0, 0, 0, 30), (0, y), (width, y))

    headline_size = ctx.fs(30)
    small_size = ctx.fs(23)
    big_size = ctx.fs(96)
    margin = int(width * 0.085)
    text_width = int(width * 0.56)
    headline_lines = _wrap(ctx, HEADLINE, headline_size, text_width)

    qr_side = ctx.fs(132)
    qr_x = margin
    qr_y = int(height * 0.70)
    qr_side = min(qr_side, height - qr_y - ctx.fs(24))

    ctx.emit("powerdown", 0.9, cooldown=0.0)
    ctx.flash.trigger((255, 255, 255), 0.18)

    glitch_timer = 1.4
    tear_timer = 2.1
    tick_timer = 0.0
    screen_on = False
    power_off_at = duration - 1.0

    while True:
        t = ctx.elapsed - started
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - started

        if not screen_on and t >= 0.32:
            screen_on = True
            ctx.emit("beep", 0.35, cooldown=0.0)
            ctx.emit("explosion", 0.35, cooldown=0.0)

        # --- draw -------------------------------------------------------- #
        if t < 0.32:
            # Broken-signal flicker between the previous frame and black.
            ctx.canvas.fill(config.BLACK)
            if int(t * 60) % 2 == 0:
                flash = pygame.Surface((width, height)).convert()
                flash.fill((235, 240, 255))
                flash.set_alpha(70)
                ctx.canvas.blit(flash, (0, 0))
        else:
            ctx.canvas.fill(BSOD_BLUE)

            # Headline paragraph.
            ctx.text.draw(
                ctx.canvas, ":(", big_size, (margin, int(height * 0.20)),
                BSOD_TEXT, anchor="topleft", shadow=False,
            )

            line_y = int(height * 0.345)
            line_height = ctx.fs(40)
            for line in headline_lines:
                ctx.text.draw(
                    ctx.canvas, line, headline_size, (margin, line_y),
                    BSOD_TEXT, anchor="topleft", shadow=False,
                )
                line_y += line_height

            # Erratic percentage counter.
            percent = _progress_at(t)
            ctx.text.draw(
                ctx.canvas, f"{percent}% complete", headline_size,
                (margin, line_y + ctx.fs(26)), BSOD_TEXT, anchor="topleft", shadow=False,
            )

            # Stop code block.
            ctx.text.draw(
                ctx.canvas, STOP_CODE, small_size,
                (margin, int(height * 0.60)), BSOD_TEXT, anchor="topleft", shadow=False,
            )
            ctx.text.draw(
                ctx.canvas, FAILED_MODULE, small_size,
                (margin, int(height * 0.60) + ctx.fs(32)), (200, 225, 255),
                anchor="topleft", shadow=False,
            )

            # Fake support QR + footer text.
            _draw_qr(ctx, ctx.canvas, qr_x, qr_y, qr_side)
            footer_x = qr_x + qr_side + ctx.fs(26)
            footer_lines = _wrap(
                ctx, FOOTER, small_size, width - footer_x - ctx.fs(30)
            )
            footer_y = qr_y + ctx.fs(6)
            for line in footer_lines:
                ctx.text.draw(
                    ctx.canvas, line, small_size, (footer_x, footer_y),
                    (225, 235, 250), anchor="topleft", shadow=False,
                )
                footer_y += ctx.fs(30)

            # Subtle "video" artefacts: grain + scanlines + brightness flicker.
            grain = pygame.Surface((width, height), pygame.SRCALPHA)
            for _ in range(240):
                gx = ctx.rng.randrange(width)
                gy = ctx.rng.randrange(height)
                value = ctx.rng.randint(150, 255)
                grain.set_at((gx, gy), (value, value, value, ctx.rng.randint(12, 34)))
            ctx.canvas.blit(grain, (0, 0))
            ctx.canvas.blit(scanlines, (0, 0))

            flicker = 0.5 + 0.5 * math.sin(t * 21.0) + ctx.rng.uniform(-0.15, 0.15)
            if flicker > 0.9:
                shade = pygame.Surface((width, height)).convert()
                shade.fill((0, 0, 0))
                shade.set_alpha(int(12 * min(1.0, flicker - 0.9) * 10))
                ctx.canvas.blit(shade, (0, 0))

            # Occasional horizontal tearing, like a bad capture.
            tear_timer -= dt
            if tear_timer <= 0.0:
                tear_timer = ctx.rng.uniform(1.4, 2.8)
                band_h = ctx.rng.randint(ctx.fs(6), ctx.fs(26))
                band_y = ctx.rng.randint(0, max(0, height - band_h))
                try:
                    strip = ctx.canvas.subsurface(
                        pygame.Rect(0, band_y, width, band_h)
                    ).copy()
                    strip.fill((40, 60, 90), special_flags=pygame.BLEND_ADD)
                    ctx.canvas.blit(strip, (ctx.rng.randint(-ctx.fs(24), ctx.fs(24)), band_y))
                except ValueError:  # pragma: no cover - degenerate rect
                    pass

            # Rare chromatic jolt on the whole panel.
            glitch_timer -= dt
            if glitch_timer <= 0.0:
                glitch_timer = ctx.rng.uniform(2.2, 3.6)
                ctx.glitch.trigger(0.09, 0.5)

        # --- CRT power-off collapse -------------------------------------- #
        if t >= power_off_at:
            k = min(1.0, (t - power_off_at) / 0.55)
            try:
                snapshot = ctx.canvas.copy()
                scale_y = max(0.006, 1.0 - k)
                scale_x = max(0.04, 1.0 - k * 0.8)
                squeezed = pygame.transform.smoothscale(
                    snapshot,
                    (max(2, int(width * scale_x)), max(2, int(height * scale_y))),
                )
                ctx.canvas.fill(config.BLACK)
                ctx.canvas.blit(
                    squeezed,
                    squeezed.get_rect(center=(width // 2, height // 2)),
                )
                if k > 0.55:
                    line_alpha = int(255 * (1.0 - (k - 0.55) / 0.45))
                    glow = pygame.Surface((width, ctx.fs(10)), pygame.SRCALPHA)
                    pygame.draw.rect(
                        glow, (235, 245, 255, max(0, line_alpha)),
                        glow.get_rect(), border_radius=ctx.fs(5),
                    )
                    ctx.canvas.blit(
                        glow, glow.get_rect(center=(width // 2, height // 2))
                    )
            except Exception:  # pragma: no cover - scaling guard
                ctx.canvas.fill(config.BLACK)

        if t >= duration - 0.45:
            ctx.canvas.fill(config.BLACK)
            ctx.emit("beep", 0.15, cooldown=0.6)

        ctx.present()

    # Hand a clean black frame to the cleanup phase.
    ctx.canvas.fill(config.BLACK)
    ctx.glitch.duration = 0.0
    ctx.flash.duration = 0.0
    LOG.info("Fake crash screen finished after %.1f s", duration)
