"""Collision resolution between soldiers and projectiles.

Soldier-vs-soldier checks use a one-dimensional spatial hash on ``x`` so even
240 fighters cost only a few hundred distance checks per frame.
"""

from __future__ import annotations

import logging
import math
from typing import TYPE_CHECKING, Dict, List

from config import SOLDIER_ENGAGE_DISTANCE

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext
    from entities.projectile import Projectile
    from entities.soldier import Soldier

LOG = logging.getLogger(__name__)

BUCKET_SIZE = 48.0


def _spark_flash(ctx: "BattleContext", x: float, y: float) -> None:
    """Yellow impact burst used by every collision."""
    ctx.particles.emit(
        x, y, 4,
        ((255, 250, 170), (255, 225, 80), (255, 160, 40)),
        speed=240.0, lifetime=0.3, size=2.6, gravity=190.0, glow=True,
    )


def resolve_soldier_combat(ctx: "BattleContext") -> None:
    """Pair up nearby enemies and let one of each pair die.

    Pairs are collected first and shuffled, then resolved only if both fighters
    are still standing - this keeps the battle perfectly fair no matter which
    side is iterated first.
    """
    reds = ctx.armies["red"].targetable()
    blues = ctx.armies["blue"].targetable()
    if not reds or not blues:
        return

    buckets: Dict[int, List["Soldier"]] = {}
    for blue in blues:
        buckets.setdefault(int(blue.x // BUCKET_SIZE), []).append(blue)

    pairs: List[tuple] = []
    for red in reds:
        bucket_index = int(red.x // BUCKET_SIZE)
        for offset in (-1, 0, 1):
            match = None
            for blue in buckets.get(bucket_index + offset, ()):
                if red.distance_to(blue) <= SOLDIER_ENGAGE_DISTANCE * ctx.ui_scale:
                    match = blue
                    break
            if match is not None:
                pairs.append((red, match))
                break

    if not pairs:
        return
    ctx.rng.shuffle(pairs)
    for red, blue in pairs:
        if red.targetable and blue.targetable:
            _resolve_pair(ctx, red, blue)

    ctx.armies["red"].soldiers = [s for s in ctx.armies["red"].soldiers if s.state != "dead"]
    ctx.armies["blue"].soldiers = [s for s in ctx.armies["blue"].soldiers if s.state != "dead"]


def _resolve_pair(ctx: "BattleContext", red: "Soldier", blue: "Soldier") -> None:
    """Kill one of the two fighters (50/50) and animate the clash."""
    mid_x = (red.x + blue.x) * 0.5
    mid_y = (red.y + blue.y) * 0.5 - 10.0
    _spark_flash(ctx, mid_x, mid_y)
    if ctx.audio is not None:
        ctx.audio.play("hit", 0.55)
        if ctx.rng.random() < 0.25:
            ctx.audio.play("sword", 0.35, cooldown=0.12)

    if ctx.rng.random() < 0.5:
        red.die(ctx)
        blue.fight()
    else:
        blue.die(ctx)
        red.fight()


def resolve_projectiles(ctx: "BattleContext", dt: float) -> None:
    """Advance projectiles and apply their effect to enemy soldiers."""
    if not ctx.projectiles:
        return

    for projectile in ctx.projectiles:
        projectile.update(dt, ctx)
        if not projectile.alive:
            continue
        target_side = "blue" if projectile.side == "red" else "red"
        targets = ctx.armies[target_side].targetable()
        if not targets:
            continue
        for soldier in list(targets):
            if not soldier.targetable:
                continue
            if not projectile.hits(soldier):
                continue

            hits = getattr(projectile, "_hits", 0)
            if hits == 0:
                # First casualty of each projectile gets a tiny bit of impact.
                ctx.hitstop(0.05)
                ctx.shake.trigger(4.0, 0.16)
                if projectile.kind == "ice_beam":
                    ctx.particles.ice_shatter(projectile.x, projectile.y, 0.8)
                else:
                    ctx.particles.fire_explosion(projectile.x, projectile.y, 0.7)
            setattr(projectile, "_hits", hits + 1)

            if projectile.kind == "ice_beam":
                # A single beam can only lock up so many soldiers at once.
                frozen_total = getattr(projectile, "_freezes", 0)
                if frozen_total >= 7:
                    continue
                setattr(projectile, "_freezes", frozen_total + 1)
                soldier.freeze(ctx)
                if ctx.audio is not None:
                    ctx.audio.play("ice", 0.4, cooldown=0.15)
            else:
                # A fireball burns itself out after a handful of victims.
                burned = getattr(projectile, "_burns", 0)
                if burned >= 9:
                    projectile.alive = False
                    break
                setattr(projectile, "_burns", burned + 1)
                soldier.die(ctx, cause="burn")
                ctx.particles.fire_explosion(soldier.x, soldier.y - 8.0, 0.55)
                if ctx.audio is not None:
                    ctx.audio.play("explosion", 0.3, cooldown=0.1)

    ctx.projectiles = [p for p in ctx.projectiles if p.alive]


def wipe_projectiles(ctx: "BattleContext") -> None:
    """Remove every projectile (used at the finale)."""
    for projectile in ctx.projectiles:
        projectile.alive = False
    ctx.projectiles.clear()


def distance(a: "Soldier", b: "Soldier") -> float:
    """Helper kept public for tests and debugging."""
    return math.hypot(a.x - b.x, a.y - b.y)
