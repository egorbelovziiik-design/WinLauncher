"""Phase 0-1: blackout, volume blast, white flash and the glitching title."""

from __future__ import annotations

import logging
import math

import pygame

import config
from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)


def _draw_scanlines(surface: pygame.Surface, alpha: int = 26) -> None:
    """Cheap CRT scanline overlay (one thin line every six pixels)."""
    width, height = surface.get_size()
    line = pygame.Surface((width, 1), pygame.SRCALPHA)
    line.fill((0, 0, 0, alpha))
    for y in range(0, height, 6):
        surface.blit(line, (0, y))


def run(ctx: BattleContext) -> None:
    """Run the intro.  Leaves the screen black for the boss reveal."""
    duration = config.PHASE_INTRO_DURATION
    start = ctx.elapsed

    ctx.screen.set_cursor_visible(False)
    ctx.screen.set_topmost(True)
    ctx.canvas.fill(config.BLACK)

    # Rendering the sound bank takes a moment - do it behind the black screen.
    if ctx.audio is not None:
        ctx.audio.preload()

    volume_started = False
    music_started = False
    last_beat = -1.0

    while True:
        t = ctx.elapsed - start
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - start

        # --- volume slammed to 100 % ----------------------------------- #
        if not volume_started and t >= 0.45:
            volume_started = True
            if ctx.volume is not None:
                ctx.volume.fade_to(config.SYSTEM_VOLUME_TARGET, config.VOLUME_FADE_SECONDS)
                LOG.info("System volume ramping to %.0f%%", config.SYSTEM_VOLUME_TARGET * 100)

        # --- heartbeat during the blackout ----------------------------- #
        if t < 1.3 and t - last_beat >= 0.42:
            last_beat = t
            ctx.emit("kick", 0.75, cooldown=0.0)
        if 0.25 <= t < 1.3 and not getattr(ctx, "_riser_played", False):
            ctx._riser_played = True
            ctx.emit("riser", 0.9, cooldown=0.0)

        # --- the drop --------------------------------------------------- #
        if not music_started and t >= 1.3:
            music_started = True
            if ctx.audio is not None:
                ctx.audio.play_music("battle_theme", loops=-1, volume=0.85)
            ctx.emit("explosion", 1.0, cooldown=0.0)
            ctx.emit("siren", 0.75, cooldown=0.0)
            ctx.emit("thunder", 0.7, cooldown=0.0)
            ctx.flash.trigger(config.WHITE, 0.35)
            ctx.shake.trigger(7.0, 0.3)
            ctx.glitch.trigger(0.25, 1.0)
            for _ in range(4):
                ctx.particles.spark(
                    ctx.rng.uniform(ctx.width * 0.15, ctx.width * 0.85),
                    ctx.rng.uniform(ctx.height * 0.15, ctx.height * 0.85),
                    14,
                )
            ctx.particles.fire_explosion(ctx.width * 0.5, ctx.height * 0.55, 2.0)

        # --- draw -------------------------------------------------------- #
        ctx.bg.draw_intro(ctx.canvas, ctx.elapsed)

        if 1.3 <= t <= 2.0:
            k = (t - 1.3) / 0.7
            radius = int(min(ctx.width, ctx.height) * (0.05 + 0.62 * k))
            ring_width = max(1, int(ctx.fs(18) * (1.0 - k)))
            pygame.draw.circle(
                ctx.canvas, (255, 205, 170),
                (ctx.width // 2, int(ctx.height * 0.44)), radius, ring_width,
            )
            pygame.draw.circle(
                ctx.canvas, (120, 190, 255),
                (ctx.width // 2, int(ctx.height * 0.44)),
                max(1, int(radius * 0.72)), max(1, ring_width // 2),
            )

        if t >= 1.3:
            local = t - 1.3
            title_size = ctx.fs(int(150 + 70 * min(1.0, local / 1.2)))
            title = ctx.text.draw_glitch(
                ctx.canvas,
                config.APP_NAME,
                title_size,
                (ctx.width // 2, int(ctx.height * 0.44)),
                ctx.elapsed,
                config.WHITE,
                amplitude=12,
                jitter=1.0,
            )
            subtitle_size = ctx.fs(38)
            pulse = 0.5 + 0.5 * math.sin(ctx.elapsed * 4.0)
            ctx.text.draw_glitch(
                ctx.canvas,
                "БИТВА ЗА ЭКРАН",
                subtitle_size,
                (ctx.width // 2, title.bottom + ctx.fs(34)),
                ctx.elapsed * 1.4,
                (int(160 + 80 * pulse), 40, 40),
                amplitude=4,
            )
            if 2.6 <= local <= 4.0:
                hint = "не переключайся..."
                alpha = int(180 * (1.0 - abs(local - 3.3) / 0.7))
                if alpha > 0:
                    image = ctx.text.render(hint, ctx.fs(26), (170, 170, 180)).copy()
                    image.set_alpha(max(0, alpha))
                    rect = image.get_rect(center=(ctx.width // 2, int(ctx.height * 0.78)))
                    ctx.canvas.blit(image, rect)

        _draw_scanlines(ctx.canvas, 22)

        ctx.particles.update(dt)
        ctx.particles.draw(ctx.canvas)
        ctx.present()

    ctx.canvas.fill(config.BLACK)
