"""Phase 2: the screen splits and both bosses make their entrance."""

from __future__ import annotations

import logging
import math

import pygame

import config
from battle.battle_manager import BattleContext
from entities.boss import Boss

LOG = logging.getLogger(__name__)


def _ease_out_cubic(p: float) -> float:
    """Fast start, soft landing."""
    p = max(0.0, min(1.0, p))
    return 1.0 - (1.0 - p) ** 3


def run(ctx: BattleContext) -> None:
    """Create the bosses, animate them in and play the gong."""
    duration = config.PHASE_BOSSES_DURATION
    start = ctx.elapsed

    boss_size = config.BOSS_SIZE
    boss_y = ctx.height * config.BOSS_Y_RATIO
    red_home = ctx.width * config.BOSS_X_RATIO
    blue_home = ctx.width * (1.0 - config.BOSS_X_RATIO)

    red = Boss("red", -boss_size * 1.4, boss_y, boss_size, ctx.rng)
    red.home_x = red_home
    blue = Boss("blue", ctx.width + boss_size * 1.4, boss_y, boss_size, ctx.rng)
    blue.home_x = blue_home
    for boss in (red, blue):
        boss.auto_attack = False
    ctx.bosses = {"red": red, "blue": blue}

    ctx.emit("gong", 1.0, cooldown=0.0)
    ctx.emit("thunder", 0.8, cooldown=0.0)
    crowd_played = False
    banner_played = False
    split_progress = 0.0
    entry_start = 0.35

    while True:
        t = ctx.elapsed - start
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - start

        split_progress = min(1.0, t / 0.55)

        # Slide the bosses in from the outer edges.
        entry = _ease_out_cubic((t - entry_start) / 0.9) if t > entry_start else 0.0
        red.x = -boss_size * 1.4 + (red_home + boss_size * 1.4) * entry
        blue.x = ctx.width + boss_size * 1.4 - (ctx.width + boss_size * 1.4 - blue_home) * entry

        red.update(dt, ctx)
        blue.update(dt, ctx)

        if not crowd_played and t > 1.1:
            crowd_played = True
            ctx.emit("crowd", 0.85, cooldown=0.0)
            ctx.emit("explosion", 0.6, cooldown=0.0)
            ctx.flash.trigger((180, 190, 255), 0.25)
            ctx.shake.trigger(5.0, 0.25)
        if not banner_played and t > 1.9:
            banner_played = True
            ctx.emit("thunder", 0.9, cooldown=0.0)
            ctx.glitch.trigger(0.2, 0.9)

        # Ambient element particles.
        for _ in range(2):
            ctx.particles.embers(
                ctx.rng.uniform(0.0, ctx.width * 0.5), ctx.height * 0.9, 1
            )
            ctx.particles.snow(
                ctx.rng.uniform(ctx.width * 0.5, ctx.width), ctx.height * 0.15, 1
            )

        # --- draw -------------------------------------------------------- #
        ctx.bg.draw_arena(
            ctx.canvas, ctx.elapsed, split_progress, split_progress,
            pulse=0.6 + 0.4 * math.sin(ctx.elapsed * 6.0),
        )
        ctx.bg.draw_floor(ctx.canvas, ctx.elapsed)

        # Lightning seam between the halves.
        seam = ctx.width * 0.5
        bolt_offset = math.sin(ctx.elapsed * 9.0) * 26.0
        ctx.bg.draw_lightning(
            ctx.canvas, seam + bolt_offset,
            intensity=1.0 + 0.4 * math.sin(ctx.elapsed * 12.0),
            color=(235, 240, 255),
            branches=3,
            jitter=18.0,
        )

        red.draw(ctx.canvas, ctx)
        blue.draw(ctx.canvas, ctx)

        # Boss name plates.
        ctx.text.draw(
            ctx.canvas, "RED", ctx.fs(38), (int(red_home), int(boss_y + boss_size * 0.95)),
            config.RED_BRIGHT, anchor="center",
        )
        ctx.text.draw(
            ctx.canvas, "BLUE", ctx.fs(38), (int(blue_home), int(boss_y + boss_size * 0.95)),
            config.BLUE_BRIGHT, anchor="center",
        )

        # Pulsing banner.
        if t > 1.9:
            local = min(1.0, (t - 1.9) / 0.3)
            size = ctx.fs(int(58 * local + 16))
            ctx.text.draw_pulse(
                ctx.canvas,
                "БИТВА ЗА ЭКРАН НАЧИНАЕТСЯ!",
                size,
                (ctx.width // 2, int(ctx.height * 0.13)),
                ctx.elapsed,
                color=config.GOLD,
                glitch=True,
                amplitude=4,
            )

        ctx.particles.update(dt)
        ctx.particles.draw(ctx.canvas)
        ctx.present()

    for boss in ctx.bosses.values():
        boss.x = boss.home_x
