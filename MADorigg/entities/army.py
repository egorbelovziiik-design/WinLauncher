"""Управление армией: волны, обновление, отрисовка, подсчёт."""

from __future__ import annotations

import random
from typing import Literal

import pygame

import config
from entities.soldier import Soldier

Side = Literal["red", "blue"]


class Army:
    """Две армии бойцов: красная (слева) и синяя (справа)."""

    def __init__(self, width: int, height: int, rng: random.Random | None = None) -> None:
        self.width = width
        self.height = height
        self.rng = rng or random.Random()
        self.red: list[Soldier] = []
        self.blue: list[Soldier] = []
        self.soldier_cap: int = config.MAX_SOLDIERS
        self._spawn_accum_red: float = 0.0
        self._spawn_accum_blue: float = 0.0

    # ------------------------------------------------------------------
    def spawn_wave(self, side: Side, count: int) -> int:
        """Выбросить волну бойцов с края экрана. Возвращает число созданных."""
        lst = self.red if side == "red" else self.blue
        created = 0
        cap = self.soldier_cap

        for _ in range(max(0, count)):
            if len(lst) >= cap:
                break
            if side == "red":
                x = self.rng.uniform(-30.0, -5.0)
                direction = 1
            else:
                x = self.rng.uniform(self.width + 5.0, self.width + 30.0)
                direction = -1
            y = self.height * self.rng.uniform(
                config.ARMY_Y_TOP, config.ARMY_Y_BOTTOM
            )
            lst.append(Soldier(side, x, y, direction, self.rng))
            created += 1
        return created

    def spawn_tick(self, dt: float) -> None:
        """Набор 5–10 новых бойцов каждую секунду на каждую сторону."""
        self._spawn_accum_red += dt
        self._spawn_accum_blue += dt

        if self._spawn_accum_red >= 1.0:
            self._spawn_accum_red -= 1.0
            self.spawn_wave("red", self.rng.randint(5, 10))
        if self._spawn_accum_blue >= 1.0:
            self._spawn_accum_blue -= 1.0
            self.spawn_wave("blue", self.rng.randint(5, 10))

    # ------------------------------------------------------------------
    def count(self, side: Side) -> int:
        """Живых бойцов на стороне."""
        lst = self.red if side == "red" else self.blue
        return sum(1 for s in lst if s.alive)

    def soldiers(self, side: Side) -> list[Soldier]:
        return self.red if side == "red" else self.blue

    def kill_random(self, side: Side, fraction: float, rng: random.Random | None = None) -> int:
        """Убить часть армии (финальное уменьшение). Возвращает число жертв."""
        rng = rng or self.rng
        lst = self.soldiers(side)
        victims = [s for s in lst if s.alive]
        n = int(len(victims) * fraction)
        chosen = rng.sample(victims, min(n, len(victims)))
        for soldier in chosen:
            soldier.die()
        return len(chosen)

    def kill_all(self, side: Side) -> None:
        """Убить всю сторону (одновременный финальный ва-банк)."""
        for soldier in self.soldiers(side):
            soldier.die()

    def boost(self, side: Side, multiplier: float = 3.0) -> None:
        """Увеличить армию победителя (в финале) — с защитой от зависания."""
        old_cap = self.soldier_cap
        self.soldier_cap = config.MAX_SOLDIERS * 3
        try:
            alive = [s for s in self.soldiers(side) if s.alive]
            target = int(len(alive) * multiplier)
            stall = 0
            while stall < 5:
                current = sum(1 for s in self.soldiers(side) if s.alive)
                if current >= target:
                    break
                created = self.spawn_wave(side, min(10, target - current))
                stall = stall + 1 if created == 0 else 0
        finally:
            # Возврат обычного лимита необязателен в финале, но оставим
            # повышенным: фаза cleanup всё равно завершает приложение.
            if self.soldier_cap < old_cap:
                self.soldier_cap = old_cap

    # ------------------------------------------------------------------
    def update(self, dt: float, particles: object | None = None) -> None:
        """Обновить всех бойцов; при смерти от заморозки — осколки льда."""
        from visuals.particles import ParticleSystem  # локальный импорт

        for lst in (self.red, self.blue):
            for soldier in lst:
                was_frozen = soldier.frozen
                freeze_expired = (
                    was_frozen and soldier.freeze_timer <= 0.0
                )
                soldier.update(dt)
                # Разбитый лёд: искры осколков
                if freeze_expired and particles is not None:
                    if isinstance(particles, ParticleSystem):
                        particles.ice_shatter(soldier.x, soldier.y)
            # Чистка мёртвых
            alive = [s for s in lst if s.state != "dead"]
            removed = len(lst) - len(alive)
            if removed:
                lst[:] = alive
            _ = removed

    def draw(self, surface: pygame.Surface, t: float) -> None:
        """Нарисовать всех живых бойцов."""
        for soldier in self.red:
            soldier.draw(surface, t)
        for soldier in self.blue:
            soldier.draw(surface, t)

    def clear(self) -> None:
        """Полная очистка армий."""
        self.red.clear()
        self.blue.clear()
