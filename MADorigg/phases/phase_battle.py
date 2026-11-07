"""Phase 3: the actual battle - armies, boss attacks, particles, glitches."""

from __future__ import annotations

import logging
import math

import pygame

import config
from battle.battle_manager import BattleContext, draw_hud
from battle.collision import resolve_projectiles, resolve_soldier_combat

LOG = logging.getLogger(__name__)


def run(ctx: BattleContext) -> None:
    """Fight until the timer runs out or the front line collapses."""
    duration = ctx.rng.uniform(*config.PHASE_BATTLE_DURATION)
    start = ctx.elapsed
    LOG.info("Battle duration: %.1f s", duration)

    for boss in ctx.bosses.values():
        boss.auto_attack = True
        boss.attack_timer = 0.0

    next_spawn = 0.0
    next_glitch = ctx.rng.uniform(*config.GLITCH_INTERVAL)
    next_volume_bump = 5.0
    music_gain = 0.85
    next_smoke = 0.0

    while True:
        t = ctx.elapsed - start
        if t >= duration:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - start
        ctx.battle_elapsed = t
        ctx.wave = 1 + int(t // 4.0)

        # --- reinforcements ------------------------------------------- #
        if t >= next_spawn:
            next_spawn = t + config.SOLDIER_SPAWN_INTERVAL
            red_spawned = ctx.armies["red"].spawn_wave(ctx)
            blue_spawned = ctx.armies["blue"].spawn_wave(ctx)
            ctx.stats.red_spawned += red_spawned
            ctx.stats.blue_spawned += blue_spawned
            if ctx.rng.random() < 0.3:
                ctx.emit("scream", 0.28, cooldown=0.5)

        # --- bosses ---------------------------------------------------- #
        for boss in list(ctx.bosses.values()):
            projectile = boss.update(dt, ctx)
            if projectile is not None:
                ctx.projectiles.append(projectile)
                ctx.shake.trigger(2.5, 0.12)
                ctx.emit("whoosh", 0.5, cooldown=0.08)

        # --- soldiers & fighting --------------------------------------- #
        ctx.armies["red"].update(dt, ctx)
        ctx.armies["blue"].update(dt, ctx)
        resolve_soldier_combat(ctx)
        resolve_projectiles(ctx, dt)
        ctx.front.update(dt, ctx.armies["red"].count(), ctx.armies["blue"].count())

        # --- particles / ambience -------------------------------------- #
        ctx.particles.update(dt)
        for _ in range(2):
            ctx.particles.embers(ctx.rng.uniform(0, ctx.width * 0.5), ctx.height * 0.92, 1)
            ctx.particles.snow(
                ctx.rng.uniform(ctx.width * 0.5, ctx.width), ctx.height * 0.1, 1
            )
        if t >= next_smoke:
            next_smoke = t + 0.35
            if ctx.rng.random() < 0.6:
                ctx.particles.smoke(
                    ctx.rng.uniform(ctx.width * 0.25, ctx.width * 0.75),
                    ctx.ground_y - ctx.rng.uniform(0, 40),
                    ctx.rng.randint(1, 3),
                )

        # --- random glitch + shake ------------------------------------- #
        if t >= next_glitch:
            next_glitch = t + ctx.rng.uniform(*config.GLITCH_INTERVAL)
            ctx.glitch.trigger(ctx.rng.uniform(0.12, 0.22), ctx.rng.uniform(0.7, 1.25))
            ctx.shake.trigger(ctx.rng.uniform(2.5, 5.0), 0.18)
            ctx.emit("thunder", 0.35, cooldown=0.3)

        # --- rising tension: +5 % every 5 s ---------------------------- #
        if t >= next_volume_bump:
            next_volume_bump += 5.0
            music_gain = min(1.0, music_gain + 0.05)
            if ctx.audio is not None:
                ctx.audio.fade_master(music_gain, 1.0)

        # --- draw ------------------------------------------------------- #
        ctx.bg.draw_arena(
            ctx.canvas,
            ctx.elapsed,
            pulse=0.5 + 0.5 * math.sin(ctx.elapsed * 5.0),
        )
        ctx.bg.draw_floor(ctx.canvas, ctx.elapsed)

        ctx.armies["red"].draw(ctx.canvas)
        ctx.armies["blue"].draw(ctx.canvas)
        for projectile in ctx.projectiles:
            projectile.draw(ctx.canvas, ctx)
        for boss in ctx.bosses.values():
            boss.draw(ctx.canvas, ctx)

        ctx.front.draw(ctx.canvas, ctx)
        draw_hud(ctx)

        ctx.text.draw(
            ctx.canvas,
            "MADorigg",
            ctx.fs(22),
            (ctx.width - ctx.fs(18), ctx.height - ctx.fs(12)),
            (120, 120, 135),
            anchor="bottomright",
        )

        ctx.particles.draw(ctx.canvas)
        ctx.present()

        if ctx.front.position <= 0.2 or ctx.front.position >= config.FRONT_LINE_LIMIT:
            LOG.info("Front line collapsed at %.2f - early finale", ctx.front.position)
            break

    LOG.info(
        "Battle stats: red %d/%d kills %d | blue %d/%d kills %d",
        ctx.stats.red_spawned,
        ctx.stats.red_dead,
        ctx.stats.red_kills,
        ctx.stats.blue_spawned,
        ctx.stats.blue_dead,
        ctx.stats.blue_kills,
    )
