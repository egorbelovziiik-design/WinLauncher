"""Army container: spawning waves, updating and drawing a side's soldiers."""

from __future__ import annotations

import logging
import random
from typing import TYPE_CHECKING, List, Optional

import pygame

from config import MAX_SOLDIERS, SOLDIER_SPAWN_BATCH
from .soldier import Soldier

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)


class Army:
    """All soldiers of one colour plus their spawn logic."""

    def __init__(self, side: str, ctx: "BattleContext") -> None:
        self.side = side
        self.rng: random.Random = ctx.rng
        self.soldiers: List[Soldier] = []
        self.spawn_count = 0

    # ------------------------------------------------------------------ #
    # Queries
    # ------------------------------------------------------------------ #
    def __len__(self) -> int:
        return len(self.soldiers)

    @property
    def active(self) -> List[Soldier]:
        """Soldiers that are still fighting (running, boxing or frozen)."""
        return [s for s in self.soldiers if s.state in ("running", "fighting", "frozen")]

    def count(self) -> int:
        """Number of combat-ready soldiers."""
        return len(self.active)

    def targetable(self) -> List[Soldier]:
        """Soldiers enemies may pair up with."""
        return [s for s in self.soldiers if s.targetable]

    # ------------------------------------------------------------------ #
    # Spawning
    # ------------------------------------------------------------------ #
    def spawn_wave(self, ctx: "BattleContext", count: Optional[int] = None) -> int:
        """Spawn a wave of soldiers just outside the screen edge.

        Returns the number of soldiers actually created (respects the cap).
        """
        if count is None:
            count = self.rng.randint(*SOLDIER_SPAWN_BATCH)
        room = MAX_SOLDIERS - self.count()
        if room <= 0:
            return 0
        count = max(0, min(count, room))
        if count == 0:
            return 0

        ground_y = ctx.ground_y
        for index in range(count):
            if self.side == "red":
                x = -self.rng.uniform(10.0, 170.0)
            else:
                x = ctx.width + self.rng.uniform(10.0, 170.0)
            y = ground_y + self.rng.uniform(-26.0, 26.0)
            y = max(ctx.ground_y - 40.0, min(ctx.height - 6.0, y))
            self.soldiers.append(Soldier(self.side, x, y, self.rng))
            self.spawn_count += 1
        return count

    def spawn_at(self, x: float, y: float) -> Soldier:
        """Spawn a single soldier at an explicit position (used by the finale)."""
        soldier = Soldier(self.side, x, y, self.rng)
        self.soldiers.append(soldier)
        self.spawn_count += 1
        return soldier

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(self, dt: float, ctx: "BattleContext") -> None:
        """Update every soldier and drop the fully removed ones."""
        for soldier in self.soldiers:
            soldier.update(dt, ctx)
        if len(self.soldiers) > MAX_SOLDIERS + 40:
            self.soldiers = self.soldiers[-MAX_SOLDIERS:]
        dead = [s for s in self.soldiers if s.state == "dead"]
        if dead:
            self.soldiers = [s for s in self.soldiers if s.state != "dead"]

    def draw(self, surface: pygame.Surface) -> None:
        """Draw the whole army (back rows first for a little depth)."""
        for soldier in sorted(self.soldiers, key=lambda s: s.y):
            soldier.draw(surface)

    # ------------------------------------------------------------------ #
    # Bulk actions
    # ------------------------------------------------------------------ #
    def kill_all(self, ctx: "BattleContext", cause: str = "combat") -> None:
        """Kill every standing soldier (loser wipe in the finale)."""
        for soldier in self.soldiers:
            if soldier.state in ("running", "fighting", "frozen"):
                soldier.die(ctx, cause=cause)

    def clear(self) -> None:
        """Remove every soldier instantly."""
        self.soldiers.clear()

    def boost(self, factor: float) -> None:
        """Speed up (or slow down) all soldiers' run animation."""
        for soldier in self.soldiers:
            soldier.speed_boost = max(0.1, factor)
