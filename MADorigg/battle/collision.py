"""Столкновения: рукопашная между армиями, действие снарядов."""

from __future__ import annotations

import math
import random
from typing import TYPE_CHECKING

from entities.soldier import Soldier

if TYPE_CHECKING:  # pragma: no cover
    from battle.battle_manager import BattleManager


class CollisionSystem:
    """Резолвит боевые столкновения между бойцами и снарядами."""

    def __init__(self, rng: random.Random | None = None) -> None:
        self.rng = rng or random.Random()
        self._hit_sound_cooldown: float = 0.0

    # ------------------------------------------------------------------
    def update_timers(self, dt: float) -> None:
        if self._hit_sound_cooldown > 0.0:
            self._hit_sound_cooldown = max(0.0, self._hit_sound_cooldown - dt)

    # ------------------------------------------------------------------
    def resolve_melee(self, manager: "BattleManager") -> int:
        """Бой красных и синих: дистанция < 15 px → вспышка, один погибает.

        Возвращает число состоявшихся столкновений.
        """
        collisions = 0
        blue_alive = [s for s in manager.army.blue if s.combatant]
        if not blue_alive:
            return 0

        # Пространственная сетка по X для скорости
        cell = 40
        grid: dict[int, list[Soldier]] = {}
        for b in blue_alive:
            key = int(b.x // cell)
            grid.setdefault(key, []).append(b)

        for red in manager.army.red:
            if not red.combatant:
                continue
            key = int(red.x // cell)
            candidates: list[Soldier] = []
            for dk in (key - 1, key, key + 1):
                candidates.extend(grid.get(dk, ()))
            for blue in candidates:
                if not blue.combatant:
                    continue
                dx = red.x - blue.x
                dy = red.y - blue.y
                if dx * dx + dy * dy >= 15 * 15:
                    continue
                # Столкновение!
                collisions += 1
                red.start_fighting()
                blue.start_fighting()
                mx = (red.x + blue.x) / 2.0
                my = (red.y + blue.y) / 2.0
                manager.particles.spark(mx, my)
                manager.shake.trigger(
                    self.rng.uniform(3.0, 8.0), 0.2
                )
                if self._hit_sound_cooldown <= 0.0:
                    manager.music.play("hit", volume=0.7)
                    if self.rng.random() < 0.35:
                        manager.music.play("sword", volume=0.4)
                    self._hit_sound_cooldown = 0.12

                # Один из них погибает (50/50)
                if self.rng.random() < 0.5:
                    blue.die()
                    if self.rng.random() < 0.3:
                        manager.music.play("death", volume=0.35)
                else:
                    red.die()
                    if self.rng.random() < 0.3:
                        manager.music.play("death", volume=0.35)
                break

        return collisions

    # ------------------------------------------------------------------
    def resolve_projectiles(self, manager: "BattleManager") -> None:
        """Действие снарядов по армиям противника."""
        for projectile in list(manager.projectiles):
            if not projectile.alive:
                continue

            if projectile.kind == "fireball":
                self._fireball_hits(manager, projectile)
            elif projectile.kind == "ice_beam":
                self._ice_beam_hits(manager, projectile)

    # ------------------------------------------------------------------
    def _fireball_hits(self, manager: "BattleManager", projectile) -> None:
        """Огненный шар уничтожает всех синих на своём пути."""
        rect = projectile.beam_rect()
        victims = [
            s for s in manager.army.blue
            if s.alive and rect.collidepoint(int(s.x), int(s.y))
        ]
        if not victims:
            return
        for soldier in victims:
            soldier.die()
        # Взрыв в точке попадания
        manager.particles.fire_explosion(projectile.x, projectile.y)
        manager.particles.smoke(projectile.x, projectile.y)
        manager.shake.trigger(self.rng.uniform(4.0, 7.0), 0.25)
        manager.music.play("explosion", volume=0.65)
        manager.music.play("fire", volume=0.5)
        # Шар уничтожен первым касанием
        projectile.lifetime = 0.0
        projectile.alive = False

    def _ice_beam_hits(self, manager: "BattleManager", projectile) -> None:
        """Ледяной луч замораживает красных на пути."""
        rect = projectile.beam_rect()
        frozen_any = False
        for soldier in manager.army.red:
            if not soldier.alive or soldier.frozen:
                continue
            if rect.collidepoint(int(soldier.x), int(soldier.y)):
                soldier.freeze()
                frozen_any = True
                manager.particles.ice_beam_spark(soldier.x, soldier.y)
        if frozen_any and self._hit_sound_cooldown <= 0.0:
            manager.music.play("ice", volume=0.6)
            self._hit_sound_cooldown = 0.3

    # ------------------------------------------------------------------
    @staticmethod
    def distance(a: tuple[float, float], b: tuple[float, float]) -> float:
        return math.hypot(a[0] - b[0], a[1] - b[1])
