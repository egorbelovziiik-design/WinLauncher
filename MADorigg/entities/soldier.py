"""Солдаты-человечки: бег, бой, смерть. Рисуются через pygame.draw."""

from __future__ import annotations

import math
import random
from typing import Literal

import pygame

import config

Side = Literal["red", "blue"]


class Soldier:
    """Маленький боец (≈20×20 px): кружок-голова + палочки-конечности."""

    def __init__(
        self,
        side: Side,
        x: float,
        y: float,
        direction: int,
        rng: random.Random | None = None,
    ) -> None:
        rng = rng or random
        self.side = side
        self.x = x
        self.y = y
        self.direction = 1 if direction >= 0 else -1   # 1 = вправо, -1 = влево
        self.speed: float = rng.uniform(*config.SOLDIER_SPEED)
        self.state: Literal["running", "fighting", "dying", "dead"] = "running"
        self.frozen: bool = False
        self.freeze_timer: float = 0.0
        self.anim_frame: int = 0
        self.anim_timer: float = 0.0
        self.fight_timer: float = 0.0
        self.die_timer: float = 0.0
        self.vy: float = 0.0
        self.rotation: float = 0.0
        self.color = (
            config.RED_SOLDIER if side == "red" else config.BLUE_SOLDIER
        )
        self.color_dark = (
            config.RED_SOLDIER_DARK if side == "red" else config.BLUE_SOLDIER_DARK
        )

    # ------------------------------------------------------------------
    @property
    def alive(self) -> bool:
        """Ещё участвует в бою (не мёртв)."""
        return self.state != "dead"

    @property
    def combatant(self) -> bool:
        """Может драться (бегущий, не замороженный)."""
        return self.state == "running" and not self.frozen

    def pos(self) -> tuple[float, float]:
        return (self.x, self.y)

    # ------------------------------------------------------------------
    def freeze(self) -> None:
        """Заморозить ледяным лучом (разбивается через 1 сек)."""
        if self.state in ("dying", "dead") or self.frozen:
            return
        self.frozen = True
        self.freeze_timer = 1.0
        self.speed = 0.0

    def start_fighting(self) -> None:
        """Вступить в рукопашную (короткая пауза с ударом рукой)."""
        if self.state in ("dying", "dead"):
            return
        self.state = "fighting"
        self.fight_timer = random.uniform(0.25, 0.45)

    def die(self) -> None:
        """Умереть: падение + крестик + исчезновение через 1 сек."""
        if self.state in ("dying", "dead"):
            return
        self.state = "dying"
        self.die_timer = 1.0
        self.vy = -random.uniform(40.0, 90.0)
        self.frozen = False

    # ------------------------------------------------------------------
    def update(self, dt: float) -> None:
        """Движение, анимация, таймеры."""
        if self.state == "dead":
            return

        # Заморозка: отсчёт до разрушения
        if self.frozen:
            self.freeze_timer -= dt
            if self.freeze_timer <= 0.0:
                self.frozen = False
                self.die()
            return

        # Смерть: гравитация + вращение + fade
        if self.state == "dying":
            self.die_timer -= dt
            self.vy += 600.0 * dt
            self.y += self.vy * dt
            self.x += self.direction * 30.0 * dt
            self.rotation += self.direction * 360.0 * dt
            if self.die_timer <= 0.0:
                self.state = "dead"
            return

        # Рукопашная пауза
        if self.state == "fighting":
            self.fight_timer -= dt
            self.anim_timer += dt
            if self.anim_timer >= 0.12:
                self.anim_timer = 0.0
                self.anim_frame = 1 - self.anim_frame
            if self.fight_timer <= 0.0:
                self.state = "running"
            return

        # Бег + анимация (2 кадра: ноги врозь / вместе)
        self.x += self.direction * self.speed * dt
        self.anim_timer += dt
        if self.anim_timer >= 0.12:
            self.anim_timer = 0.0
            self.anim_frame = 1 - self.anim_frame

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface, t: float) -> None:
        """Нарисовать человечка."""
        if self.state == "dead":
            return
        x, y = int(self.x), int(self.y)

        if self.state == "dying":
            self._draw_dead(surface, x, y)
            return

        color = self.color
        dark = self.color_dark
        if self.frozen:
            color = (170, 235, 255)
            dark = (90, 180, 230)

        # Ноги: 2 кадра анимации бега
        if self.anim_frame == 0:
            leg_l = (x - 5, y + 9)
            leg_r = (x + 5, y + 9)
        else:
            leg_l = (x - 2, y + 9)
            leg_r = (x + 2, y + 9)

        # Тело
        pygame.draw.line(surface, dark, (x, y - 2), (x, y + 3), 3)

        # Руки: бег — вверх-вниз, бой — кулаки вперёд
        if self.state == "fighting":
            punch = 7 if self.anim_frame == 0 else 4
            arm_l = (x - 3, y - 4)
            arm_r = (x + self.direction * punch, y - 3)
            pygame.draw.line(surface, dark, (x, y - 1), arm_l, 2)
            pygame.draw.line(surface, dark, (x, y - 1), arm_r, 2)
            # Кулак
            pygame.draw.circle(surface, color, arm_r, 3)
        else:
            swing = 4 if self.anim_frame == 0 else -3
            arm_l = (x - 5, y - 1 + swing)
            arm_r = (x + 5, y + 1 - swing)
            pygame.draw.line(surface, dark, (x, y - 1), arm_l, 2)
            pygame.draw.line(surface, dark, (x, y - 1), arm_r, 2)

        pygame.draw.line(surface, dark, (x, y + 3), leg_l, 2)
        pygame.draw.line(surface, dark, (x, y + 3), leg_r, 2)

        # Голова-кружок
        pygame.draw.circle(surface, color, (x, y - 8), 6)
        pygame.draw.circle(surface, dark, (x, y - 8), 6, 1)

        # Лицо
        if self.side == "red":
            # Злые глаза
            pygame.draw.line(surface, (40, 0, 0), (x - 4, y - 11), (x - 1, y - 9), 2)
            pygame.draw.line(surface, (40, 0, 0), (x + 4, y - 11), (x + 1, y - 9), 2)
            pygame.draw.circle(surface, (40, 0, 0), (x - 2, y - 8), 1)
            pygame.draw.circle(surface, (40, 0, 0), (x + 2, y - 8), 1)
        else:
            # Солнечные очки
            pygame.draw.rect(surface, (15, 15, 25), (x - 5, y - 10, 10, 4))
            pygame.draw.rect(surface, (240, 240, 255), (x - 4, y - 10, 3, 1))

        # Ледяной блеск при заморозке
        if self.frozen:
            pygame.draw.line(
                surface,
                (255, 255, 255),
                (x - 4, y - 12),
                (x + 4, y - 14),
                1,
            )

        _ = t  # время зарезервировано для будущих анимаций

    def _draw_dead(self, surface: pygame.Surface, x: int, y: int) -> None:
        """Падение → крестик ✖ → исчезновение (fade за 1 сек)."""
        ratio = max(0.0, min(1.0, self.die_timer))
        alpha_col = tuple(int(c * (0.3 + 0.7 * ratio)) for c in self.color)

        if ratio > 0.55:
            # Ещё падает: наклонный человечек
            angle = math.radians(self.rotation)
            dx = int(math.cos(angle) * 6)
            dy = int(math.sin(angle) * 6)
            pygame.draw.circle(surface, alpha_col, (x, y - 8), 6)
            pygame.draw.line(surface, alpha_col, (x - dx, y - dy), (x + dx, y + dy), 3)
        else:
            # Превратился в крестик ✖
            size = max(3, int(8 * (ratio / 0.55)))
            color = (
                int(self.color[0] * ratio + 60 * (1 - ratio)),
                int(self.color[1] * ratio + 60 * (1 - ratio)),
                int(self.color[2] * ratio + 60 * (1 - ratio)),
            )
            pygame.draw.line(surface, color, (x - size, y - size), (x + size, y + size), 3)
            pygame.draw.line(surface, color, (x - size, y + size), (x + size, y - size), 3)
