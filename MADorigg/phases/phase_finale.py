"""Phase 4: one side wins, the loser is erased, the winner celebrates."""

from __future__ import annotations

import logging
import math

import pygame

import config
from battle.battle_manager import BattleContext
from battle.collision import resolve_projectiles, wipe_projectiles

LOG = logging.getLogger(__name__)

PRESSURE_TIME = 1.3
MEGA_TIME = 1.6
VICTORY_TIME = 2.4


def _draw_crowd(ctx: BattleContext, t: float) -> None:
    """Dark silhouettes of the audience cheering along the bottom edge."""
    count = 44
    base = ctx.height + ctx.fs(6)
    step = ctx.width / count
    for index in range(count):
        height = ctx.fs(24) + (index % 5) * ctx.fs(4)
        bob = abs(math.sin(t * 3.1 + index * 0.7)) * ctx.fs(5)
        x = (index + 0.5) * step + math.sin(t * 1.6 + index) * ctx.fs(3)
        color = (26, 7, 9) if x < ctx.width * 0.5 else (7, 12, 28)
        pygame.draw.ellipse(
            ctx.canvas, color,
            (int(x - height * 0.35), int(base - height - bob), int(height * 0.7), height),
        )
        # Raised hands.
        pygame.draw.line(
            ctx.canvas, color,
            (int(x), int(base - height * 0.55 - bob)),
            (int(x - height * 0.22), int(base - height * 1.05 - bob)), 2,
        )


def run(ctx: BattleContext) -> None:
    """Play the finale and set ``ctx.winner``."""
    winner = ctx.rng.choice(("red", "blue"))
    loser = "blue" if winner == "red" else "red"
    ctx.winner = winner
    LOG.info("Winner: %s", winner)

    winner_boss = ctx.bosses.get(winner)
    loser_boss = ctx.bosses.get(loser)
    for boss in ctx.bosses.values():
        boss.auto_attack = False
    wipe_projectiles(ctx)

    start = ctx.elapsed
    total = config.PHASE_FINALE_DURATION + 0.3

    from entities.projectile import MegaAttack

    mega = None
    spawn_timer = 0.0
    firework_timer = 0.0
    victory_started = False
    loser_destroyed = False
    crowd_played = False

    winner_color = config.RED_BRIGHT if winner == "red" else config.BLUE_BRIGHT
    winner_title = "КРАСНЫЕ ПОБЕДИЛИ!" if winner == "red" else "СИНИЕ ПОБЕДИЛИ!"

    while True:
        t = ctx.elapsed - start
        if t >= total:
            break
        dt = ctx.begin_frame()
        t = ctx.elapsed - start

        # ---------------------------------------------------------------- #
        # 1. Pressure: the winner snowballs, the loser panics
        # ---------------------------------------------------------------- #
        if t < PRESSURE_TIME:
            spawn_timer -= dt
            if spawn_timer <= 0.0:
                spawn_timer = 0.12
                ctx.armies[winner].spawn_wave(ctx, count=4)
                if ctx.armies[loser].count() > 6:
                    victims = ctx.armies[loser].targetable()
                    if victims:
                        ctx.rng.choice(victims).die(ctx)
            if winner_boss is not None:
                winner_boss.grow_to(config.BOSS_SIZE * 1.3)
            if loser_boss is not None:
                loser_boss.grow_to(config.BOSS_SIZE * 0.55)
                loser_boss.hurt_timer = 0.12
            ctx.front.position += (0.55 if winner == "red" else -0.55) * dt
            ctx.front.position = max(0.05, min(0.95, ctx.front.position))
            if not crowd_played and t > 0.2:
                crowd_played = True
                ctx.emit("crowd", 0.9, cooldown=0.0)
                ctx.emit("siren", 0.5, cooldown=0.0)
            if ctx.rng.random() < 0.4:
                ctx.shake.trigger(3.0, 0.15)

        # ---------------------------------------------------------------- #
        # 2. Mega attack
        # ---------------------------------------------------------------- #
        if mega is None and t >= PRESSURE_TIME and winner_boss is not None:
            origin_x = winner_boss.x + winner_boss.facing * winner_boss.size * 0.45
            origin_y = winner_boss.y
            mega = MegaAttack(winner, origin_x, origin_y, ctx.width, ctx.height)
            ctx.emit("whoosh", 0.9, cooldown=0.0)
            ctx.emit("siren", 0.7, cooldown=0.0)

        if mega is not None:
            mega.update(dt, ctx)
            if mega.hit_applied and not loser_destroyed:
                loser_destroyed = True
                ctx.slowmo(0.4, 0.7)
                ctx.hitstop(0.08)
                if loser_boss is not None:
                    loser_boss.die(ctx)
                ctx.armies[loser].kill_all(ctx, cause="burn")
                ctx.particles.shatter(
                    ctx.width * (0.2 if loser == "red" else 0.8),
                    ctx.ground_y,
                    60,
                    config.ICE_COLORS if loser == "blue" else config.FIRE_COLORS,
                    power=1.4,
                )

        # ---------------------------------------------------------------- #
        # 3. Victory
        # ---------------------------------------------------------------- #
        victory_time = PRESSURE_TIME + MEGA_TIME
        if t >= victory_time and not victory_started:
            victory_started = True
            if winner_boss is not None:
                winner_boss.start_victory()
                winner_boss.grow_to(config.BOSS_FINAL_SIZE)
            if ctx.audio is not None:
                ctx.audio.play_music("victory_theme", loops=-1, volume=0.95)
            ctx.emit("fanfare", 1.0, cooldown=0.0)
            ctx.emit("crowd", 1.0, cooldown=0.0)
            ctx.flash.trigger((255, 245, 210), 0.35)
            ctx.particles.confetti(ctx.width * 0.5, ctx.height * 0.2, 120)
            for _ in range(3):
                ctx.shake.trigger(6.0, 0.3)

        if victory_started:
            if winner_boss is not None:
                # Fly to the centre of the screen.
                target_x = ctx.width * 0.5
                target_y = ctx.height * 0.42
                winner_boss.x += (target_x - winner_boss.x) * min(1.0, dt * 3.0)
                winner_boss.y += (target_y - winner_boss.y) * min(1.0, dt * 3.0)
            firework_timer -= dt
            if firework_timer <= 0.0:
                firework_timer = 0.18
                fx = ctx.rng.uniform(ctx.width * 0.1, ctx.width * 0.9)
                fy = ctx.rng.uniform(ctx.height * 0.12, ctx.height * 0.58)
                ctx.particles.confetti(fx, fy, 26)
                ctx.particles.emit(
                    fx, fy, 26, config.CONFETTI_COLORS,
                    speed=430.0, lifetime=1.1, size=3.0,
                    gravity=150.0, drag=0.9, glow=True,
                )
                ctx.particles.spark(fx, fy, 10)
                if ctx.rng.random() < 0.6:
                    ctx.emit("kick", 0.4, cooldown=0.05)

        # ---------------------------------------------------------------- #
        # 4. Simulation of the leftovers
        # ---------------------------------------------------------------- #
        ctx.armies[winner].update(dt, ctx)
        ctx.armies[loser].update(dt, ctx)
        if not victory_started:
            resolve_projectiles(ctx, dt)
        ctx.particles.update(dt)
        if winner_boss is not None:
            winner_boss.update(dt, ctx)
        if loser_boss is not None and not loser_destroyed:
            loser_boss.update(dt, ctx)

        # ---------------------------------------------------------------- #
        # 5. Draw
        # ---------------------------------------------------------------- #
        ctx.bg.draw_arena(
            ctx.canvas,
            ctx.elapsed,
            red_alpha=1.0,
            blue_alpha=1.0,
            pulse=0.5 + 0.5 * math.sin(ctx.elapsed * 6.0),
        )
        ctx.bg.draw_floor(ctx.canvas, ctx.elapsed)
        ctx.armies["red"].draw(ctx.canvas)
        ctx.armies["blue"].draw(ctx.canvas)
        if loser_boss is not None:
            loser_boss.draw(ctx.canvas, ctx)
        if winner_boss is not None:
            winner_boss.draw(ctx.canvas, ctx)
        if mega is not None:
            mega.draw(ctx.canvas, ctx)
        for projectile in ctx.projectiles:
            projectile.draw(ctx.canvas, ctx)
        ctx.front.draw(ctx.canvas, ctx)

        if victory_started:
            _draw_crowd(ctx, ctx.elapsed)

        if victory_started:
            size = ctx.fs(96)
            ctx.text.draw_pulse(
                ctx.canvas,
                winner_title,
                size,
                (ctx.width // 2, int(ctx.height * 0.77)),
                ctx.elapsed,
                color=config.GOLD,
                pulse=0.06,
                speed=4.0,
                glitch=True,
                amplitude=6,
            )
            ctx.text.draw(
                ctx.canvas,
                config.APP_NAME,
                ctx.fs(30),
                (ctx.width // 2, int(ctx.height * 0.87)),
                winner_color,
                anchor="center",
            )
        else:
            ctx.text.draw_pulse(
                ctx.canvas,
                "ФИНАЛЬНЫЙ УДАР!",
                ctx.fs(64),
                (ctx.width // 2, int(ctx.height * 0.12)),
                ctx.elapsed,
                color=(255, 240, 220),
                glitch=True,
                amplitude=3,
            )

        ctx.particles.draw(ctx.canvas)
        ctx.present()

    if winner_boss is not None:
        winner_boss.start_victory()
