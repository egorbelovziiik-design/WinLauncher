"""Phase 1: the prank itself - the wallpaper starts singing and dancing.

Twenty seconds of a gypsy dance tune coming *out of the wallpaper*: a drawn
dancer stomps on the boards, sound rings radiate from the wall, and four fake
player windows fly around the screen.  The close buttons refuse to work.

The cursor is visible here on purpose: this is supposed to look like the
victim's own desktop, so they can try to click the X (and fail).
"""

from __future__ import annotations

import datetime
import logging
import math
from typing import List, Tuple

import pygame

import config
from battle.battle_manager import BattleContext
from entities.dancer import Dancer
from entities.fake_window import FakeWindow
from visuals.wallpaper import WallpaperRenderer

LOG = logging.getLogger(__name__)

GOLD_SPARK: Tuple[Tuple[int, int, int], ...] = (
    (255, 236, 170),
    (255, 200, 80),
    (240, 150, 50),
)

WINDOW_SPECS: Tuple[Tuple[str, Tuple[float, float], Tuple[float, float], Tuple[int, int, int]], ...] = (
    ("Эк дрын тын опа.mp4", (0.05, 0.14), (150.0, 95.0), (232, 186, 84)),
    ("Пляски цыган HD", (0.60, 0.10), (-135.0, 110.0), (110, 190, 255)),
    ("winamp 2.95", (0.10, 0.50), (170.0, -85.0), (255, 120, 90)),
    ("YouTube — Эк дрын тын опа", (0.55, 0.50), (-150.0, 100.0), (200, 140, 255)),
)


def _draw_speaker(surface: pygame.Surface, x: int, y: int, size: int, color) -> None:
    """Tiny speaker glyph (no emoji fonts on Windows)."""
    body = [
        (x, y + size * 0.32),
        (x + size * 0.28, y + size * 0.32),
        (x + size * 0.58, y),
        (x + size * 0.58, y + size),
        (x + size * 0.28, y + size * 0.68),
        (x, y + size * 0.68),
    ]
    pygame.draw.polygon(surface, color, [(int(px), int(py)) for px, py in body])
    for index, radius in enumerate((0.24, 0.42)):
        pygame.draw.arc(
            surface, color,
            pygame.Rect(x + int(size * 0.5), y + int(size * (0.18 - radius * 0.18)),
                        int(size * radius * 2.0), int(size * (0.6 + radius * 0.4))),
            -0.9, 0.9, max(1, int(size * 0.08)),
        )


def _draw_osd(
    ctx: BattleContext,
    t: float,
    progress: float,
    beat_pulse: float,
) -> None:
    """Media-style overlay: 'sound is coming from the wallpaper'."""
    width = int(ctx.width * 0.36)
    height = int(ctx.fs(64))
    x = ctx.width - width - int(ctx.width * 0.03)
    y = int(ctx.height * 0.035)
    panel = pygame.Surface((width, height), pygame.SRCALPHA)
    pygame.draw.rect(panel, (12, 8, 14, 190), panel.get_rect(), border_radius=ctx.fs(10))
    pygame.draw.rect(
        panel, (232, 186, 84, 170), panel.get_rect(), width=1, border_radius=ctx.fs(10)
    )
    _draw_speaker(panel, ctx.fs(13), ctx.fs(16), ctx.fs(26), (255, 228, 170))
    ctx.text.draw(
        panel, "Эк дрын тын опа  ·  звук с обоев", ctx.fs(17),
        (ctx.fs(48), ctx.fs(11)), (245, 236, 220), anchor="topleft", shadow=False,
    )
    ctx.text.draw(
        panel, "wallpaper.mp4", ctx.fs(13),
        (ctx.fs(48), ctx.fs(33)), (175, 168, 160), anchor="topleft", shadow=False,
    )
    bar = pygame.Rect(ctx.fs(48), height - ctx.fs(14), width - ctx.fs(76), ctx.fs(5))
    pygame.draw.rect(panel, (54, 44, 46), bar, border_radius=bar.h)
    filled = bar.copy()
    filled.width = int(bar.width * max(0.0, min(1.0, progress)))
    if filled.width > 1:
        pygame.draw.rect(panel, (255, 214, 120), filled, border_radius=bar.h)
    # Little level meter pulsing with the beat.
    for index in range(4):
        level = 0.3 + 0.7 * abs(math.sin(t * 6.0 + index * 0.7)) * (0.6 + 0.4 * beat_pulse)
        h = max(2, int(ctx.fs(16) * level))
        pygame.draw.rect(
            panel, (232, 186, 84),
            (width - ctx.fs(20) + index * ctx.fs(4), height - ctx.fs(8) - h,
             ctx.fs(2), h),
        )
    ctx.canvas.blit(panel, (x, y))


def _draw_taskbar(ctx: BattleContext, t: float) -> None:
    """A fake Windows taskbar so the wallpaper reads as a real desktop."""
    height = max(28, ctx.fs(44))
    y = ctx.height - height
    bar = pygame.Surface((ctx.width, height), pygame.SRCALPHA)
    pygame.draw.rect(bar, (32, 32, 38, 238), bar.get_rect())
    pygame.draw.line(bar, (58, 58, 66, 255), (0, 0), (ctx.width, 0), 1)

    # Start button (four-pane logo).
    size = int(height * 0.44)
    sx = ctx.fs(14)
    sy = (height - size) // 2
    panes = (
        (0, 0), (size * 0.54, 0), (0, size * 0.54), (size * 0.54, size * 0.54),
    )
    for index, (dx, dy) in enumerate(panes):
        color = (86, 156, 214)
        if index == 2:
            color = (90, 200, 160)
        elif index == 3:
            color = (220, 180, 80)
        pygame.draw.rect(
            bar, color,
            (sx + int(dx), sy + int(dy), max(2, int(size * 0.42)), max(2, int(size * 0.42))),
        )

    # Search pill.
    pill = pygame.Rect(sx + size + ctx.fs(14), height // 2 - int(height * 0.28),
                       int(ctx.width * 0.13), int(height * 0.56))
    pygame.draw.rect(bar, (44, 44, 52), pill, border_radius=pill.h // 2)
    pygame.draw.circle(bar, (150, 150, 160),
                       (pill.x + pill.h // 2, pill.centery), max(2, pill.h // 6), 2)
    pygame.draw.line(bar, (150, 150, 160),
                     (pill.x + pill.h // 2 + 3, pill.centery + 3),
                     (pill.x + pill.h // 2 + 7, pill.centery + 7), 2)

    # Pinned app tiles.
    tile = int(height * 0.56)
    x = pill.right + ctx.fs(12)
    colors = ((236, 190, 90), (120, 180, 240), (240, 120, 110), (170, 140, 235))
    for index, color in enumerate(colors):
        if x + tile > ctx.width * 0.72:
            break
        box = pygame.Rect(x, (height - tile) // 2, tile, tile)
        bright = 1.0 + 0.15 * math.sin(t * 2.0 + index)
        tint = tuple(min(255, int(c * bright)) for c in color)
        pygame.draw.rect(bar, tint, box, border_radius=ctx.fs(5))
        pygame.draw.rect(bar, (60, 60, 70), box, 1, border_radius=ctx.fs(5))
        x += tile + ctx.fs(10)

    # Tray + clock.
    now = datetime.datetime.now()
    clock_text = now.strftime("%H:%M")
    date_text = now.strftime("%d.%m.%Y")
    ctx.text.draw(
        bar, clock_text, ctx.fs(14), (ctx.width - ctx.fs(16), height // 2 - ctx.fs(2)),
        (232, 232, 238), anchor="midright", shadow=False,
    )
    ctx.text.draw(
        bar, date_text, ctx.fs(11), (ctx.width - ctx.fs(16), height // 2 + ctx.fs(14)),
        (190, 190, 198), anchor="midright", shadow=False,
    )
    tray_x = ctx.width - ctx.fs(82)
    pygame.draw.circle(bar, (200, 200, 210), (tray_x, height // 2), max(2, ctx.fs(4)), 1)
    pygame.draw.arc(
        bar, (200, 200, 210),
        pygame.Rect(tray_x - ctx.fs(7), height // 2 - ctx.fs(7), ctx.fs(14), ctx.fs(14)),
        3.6, 5.9, 1,
    )
    ctx.canvas.blit(bar, (0, y))


def run(ctx: BattleContext) -> None:
    """Play the wallpaper song, the dancing man and the flying windows."""
    duration = config.PHASE_GYPSY_DURATION
    beat = 60.0 / config.GYPSY_BPM
    start = ctx.elapsed

    wallpaper = WallpaperRenderer((ctx.width, ctx.height), ctx.rng)
    dancer = Dancer(ctx.rng)
    windows: List[FakeWindow] = []
    spawned = 0

    win_w = ctx.width * 0.235
    win_h = win_w * 0.62
    for index, (title, position, velocity, accent) in enumerate(WINDOW_SPECS):
        spec_w = win_w
        spec_h = win_h
        window = FakeWindow(
            title,
            (ctx.width * position[0], ctx.height * position[1], spec_w, spec_h),
            (velocity[0] * ctx.ui_scale, velocity[1] * ctx.ui_scale),
            accent,
            ctx.rng,
        )
        window.spawn_flash = 1.0
        windows.append(window)
    windows[0].spawn_flash = 0.0
    spawned = 1

    # This phase pretends to be the desktop: the cursor must be visible.
    ctx.screen.set_cursor_visible(True)

    if ctx.audio is not None:
        ctx.audio.play_music("gypsy_theme", loops=0, volume=0.95)

    ctx.emit("crowd", 0.55, cooldown=0.0)
    was_down = False
    next_beat = 0
    focus = (ctx.width * 0.5, ctx.height * 0.62)
    dancer_height = min(ctx.height * 0.54, ctx.width * 0.40)

    while True:
        t = ctx.elapsed - start
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - start

        beat_index = int(t / beat)
        beat_phase = (t / beat) % 1.0
        pulse = (1.0 - beat_phase) ** 2.2
        progress = max(0.0, min(1.0, t / max(0.01, duration)))

        # --- window spawns -------------------------------------------- #
        if spawned < len(windows) and t >= 0.55 + spawned * 0.85:
            windows[spawned].spawn_flash = 1.0
            ctx.emit("whoosh", 0.55, cooldown=0.05)
            spawned += 1

        # --- clicks: the X does nothing ------------------------------- #
        pressed = pygame.mouse.get_pressed()[0]
        if pressed and not was_down:
            pos = pygame.mouse.get_pos()
            for window in reversed(windows):
                if window.close_hit(pos):
                    window.refuse()
                    ctx.emit("hit", 0.6, cooldown=0.05)
                    ctx.particles.emit(
                        pos[0], pos[1], 8, GOLD_SPARK, speed=220.0, lifetime=0.5,
                        size=3.0, gravity=120.0, glow=True,
                    )
                    break
        was_down = pressed

        # --- simulation ------------------------------------------------ #
        stage = pygame.Rect(
            int(focus[0] - dancer_height * 0.55),
            int(focus[1] + dancer_height * 0.5 - dancer_height),
            int(dancer_height * 1.1),
            int(dancer_height * 1.06),
        )
        for window in windows:
            window.update(dt, (ctx.width, ctx.height), t, avoid=stage)

        # Stomp on every beat: dust and sparks under the boots.
        if beat_index > next_beat:
            next_beat = beat_index
            for _ in range(6):
                angle = ctx.rng.uniform(0.15, math.pi - 0.15)
                ctx.particles.emit(
                    focus[0] + ctx.rng.uniform(-ctx.width * 0.06, ctx.width * 0.06),
                    focus[1] + dancer_height * 0.45,
                    1, GOLD_SPARK, speed=ctx.rng.uniform(80.0, 190.0),
                    lifetime=ctx.rng.uniform(0.4, 0.9), size=ctx.rng.uniform(2.0, 4.0),
                    angle=angle, spread=0.5, gravity=-90.0, glow=True, jitter=4.0,
                )
            if beat_index % 4 == 0:
                ctx.emit("clap", 0.35, cooldown=0.05)

        ctx.particles.update(dt)

        # --- draw ------------------------------------------------------ #
        wallpaper.draw(
            ctx.canvas,
            t,
            pulse=pulse,
            brightness=min(1.0, 0.35 + t / 0.9),
            focus=(focus[0], focus[1] + dancer_height * 0.1),
            rings=1.0 - beat_phase,
        )

        # The dancer himself.
        dancer.draw(
            ctx.canvas,
            focus[0],
            focus[1] + dancer_height * 0.5,
            dancer_height,
            beat_index,
            beat_phase,
        )

        for window in windows:
            window.draw(ctx.canvas, ctx, t, beat_index, beat_phase, progress)

        _draw_taskbar(ctx, t)
        _draw_osd(ctx, t, progress, pulse)

        ctx.particles.draw(ctx.canvas)

        # Fade in from black.
        if t < 0.9:
            shade = pygame.Surface((ctx.width, ctx.height)).convert()
            shade.fill((0, 0, 0))
            shade.set_alpha(int(255 * (1.0 - t / 0.9)))
            ctx.canvas.blit(shade, (0, 0))

        ctx.present()

    # Song over: the machine "dies" on the last beat.
    if ctx.audio is not None:
        try:
            ctx.audio.stop_music(fade_ms=0)
        except Exception:  # pragma: no cover - defensive
            pass
    ctx.screen.set_cursor_visible(False)
    LOG.info("Wallpaper dance finished after %.1f s", duration)
