"""Боссы: красный 😡 и синий 😎 смайлики. Рисуются через pygame.draw."""

from __future__ import annotations

import math
import random
from typing import Literal

import pygame

import config
from entities.projectile import Projectile

Side = Literal["red", "blue"]


class Boss:
    """Крупный смайлик-босс со своими атаками и анимациями."""

    def __init__(self, side: Side, x: float, y: float) -> None:
        self.side = side
        self.x = x
        self.y = y
        self.home_x = x
        self.home_y = y
        self.size: float = float(config.BOSS_SIZE)
        self.target_size: float = float(config.BOSS_SIZE)
        self.health: float = 100.0
        self.attack_timer: float = 0.0
        self.attack_cooldown: float = config.BOSS_ATTACK_COOLDOWN
        self.state: Literal["idle", "attacking", "hurt", "victory", "dead"] = "idle"
        self.animation_frame: int = 0
        self.anim_timer: float = 0.0
        self.recoil: float = 0.0          # отдача после выстрела
        self.gesture: float = 0.0         # жест руки (синий)
        self.hurt_timer: float = 0.0
        self.glowing: bool = False        # свечение победителя
        self.blinking: bool = False       # мигание проигравшего
        self.dead: bool = False
        self.scale_goal: float = 1.0      # итоговый масштаб (финал)

    # ------------------------------------------------------------------
    @property
    def radius(self) -> float:
        return self.size / 2.0

    @property
    def center(self) -> tuple[float, float]:
        return (self.x, self.y)

    def mouth_pos(self) -> tuple[float, float]:
        """Точка выстрела — «рот» смайлика."""
        if self.side == "red":
            return (self.x + self.radius * 0.55, self.y + self.radius * 0.35)
        return (self.x - self.radius * 0.55, self.y + self.radius * 0.35)

    # ------------------------------------------------------------------
    def attack_ready(self) -> bool:
        """Готов ли босс к новой атаке."""
        return (
            not self.dead
            and self.state != "victory"
            and self.attack_timer >= self.attack_cooldown
        )

    def attack(self, screen_w: int, screen_h: int) -> Projectile | None:
        """Выстрелить снарядом своей стороны."""
        if not self.attack_ready():
            return None
        self.attack_timer = 0.0
        self.state = "attacking"
        self.recoil = 1.0
        if self.side == "red":
            self.gesture = 1.0
        mx, my = self.mouth_pos()
        army_y = screen_h * random.uniform(
            config.ARMY_Y_TOP, config.ARMY_Y_BOTTOM
        )
        if self.side == "red":
            # Огненный шар — вправо, слегка вниз к армии синих
            vx = 620.0
            flight = max(0.4, (screen_w - mx) / vx)
            vy = (army_y - my) / flight
            return Projectile(
                "fireball", mx, my, vx, vy, owner="red",
                size=random.uniform(20.0, 40.0),
                damage=random.uniform(10.0, 30.0),
            )
        # Ледяной луч — влево к армии красных
        vx = -700.0
        flight = max(0.4, mx / -vx)
        vy = (army_y - my) / flight
        return Projectile(
            "ice_beam", mx, my, vx, vy, owner="blue",
            size=random.uniform(14.0, 22.0),
            damage=random.uniform(10.0, 30.0),
        )

    def mega_attack(self, target_x: float, target_y: float) -> Projectile:
        """Финальная мега-атака по экрану."""
        self.state = "attacking"
        self.recoil = 1.0
        mx, my = self.mouth_pos()
        dx, dy = target_x - mx, target_y - my
        dist = math.hypot(dx, dy) or 1.0
        speed = 1100.0
        return Projectile(
            "mega", mx, my,
            dx / dist * speed, dy / dist * speed,
            owner=self.side,
            size=110.0,
            damage=100.0,
        )

    # ------------------------------------------------------------------
    def take_damage(self, amount: float) -> None:
        """Получить урон (анимация hurt)."""
        if self.dead:
            return
        self.health = max(0.0, self.health - amount)
        self.hurt_timer = 0.25
        self.state = "hurt"
        if self.health <= 0.0:
            self.die()

    def victory(self) -> None:
        """Перейти в режим победы."""
        if not self.dead:
            self.state = "victory"
            self.glowing = True

    def die(self) -> None:
        """Умереть (взрыв — часть финала)."""
        self.state = "dead"
        self.dead = True

    def shrink_defeat(self) -> None:
        """Проигравший: уменьшается и мигает."""
        self.target_size = config.BOSS_SIZE * 0.45
        self.blinking = True

    def grow_victory(self) -> None:
        """Победитель: увеличивается в 2 раза и светится."""
        self.target_size = config.BOSS_SIZE * 2.0
        self.glowing = True

    # ------------------------------------------------------------------
    def update(self, dt: float) -> None:
        """Таймеры атак, плавный рост/уменьшение, анимации."""
        if self.dead:
            return
        self.attack_timer += dt
        self.anim_timer += dt
        if self.anim_timer >= 0.25:
            self.anim_timer = 0.0
            self.animation_frame = 1 - self.animation_frame

        if self.recoil > 0.0:
            self.recoil = max(0.0, self.recoil - dt * 5.0)
        if self.gesture > 0.0:
            self.gesture = max(0.0, self.gesture - dt * 3.0)
        if self.hurt_timer > 0.0:
            self.hurt_timer = max(0.0, self.hurt_timer - dt)
            if self.hurt_timer == 0.0 and self.state == "hurt":
                self.state = "idle"

        # Плавный масштаб
        self.size += (self.target_size - self.size) * min(1.0, dt * 4.0)

        # Покачивание / тряска
        if self.state == "idle":
            if self.side == "red":
                # Красный: злость — дрожит
                self.x = self.home_x + math.sin(self.anim_timer * 40.0) * 3.0
                self.y = self.home_y + math.cos(self.anim_timer * 33.0) * 2.0
            else:
                # Синий: уверенность — покачивается
                self.x = self.home_x + math.sin(self.anim_timer * 2.0) * 8.0
                self.y = self.home_y + math.sin(self.anim_timer * 3.0) * 5.0

        if self.state == "attacking" and self.attack_timer > 0.4:
            self.state = "idle"

    def set_home(self, x: float, y: float) -> None:
        """Задать позицию покоя (для выезда на экран)."""
        self.home_x = x
        self.home_y = y

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface, t: float) -> None:
        """Нарисовать смайлик."""
        if self.dead:
            return
        # Мигание проигравшего
        if self.blinking and int(t * 10) % 2 == 0:
            return

        x, y = int(self.x), int(self.y)
        r = int(self.radius)

        # Отдача
        recoil_dx = 0.0
        if self.recoil > 0.0:
            kick = int(self.recoil * 14.0)
            recoil_dx = -kick if self.side == "red" else kick
        x += int(recoil_dx)

        # Победитель увеличен и светится
        if self.glowing:
            glow_r = r + int(16 + 8 * math.sin(t * 8.0))
            glow = pygame.Surface((glow_r * 2 + 4, glow_r * 2 + 4), pygame.SRCALPHA)
            gc = (255, 220, 80) if self.side == "red" else (140, 220, 255)
            pygame.draw.circle(glow, (*gc, 70), (glow_r + 2, glow_r + 2), glow_r)
            pygame.draw.circle(glow, (255, 255, 255, 120), (glow_r + 2, glow_r + 2), glow_r, 3)
            surface.blit(glow, (x - glow_r - 2, y - glow_r - 2))

        if self.side == "red":
            self._draw_red(surface, x, y, r, t)
        else:
            self._draw_blue(surface, x, y, r, t)

        # Флеш урона
        if self.hurt_timer > 0.0:
            flash = pygame.Surface((r * 2, r * 2), pygame.SRCALPHA)
            pygame.draw.circle(flash, (255, 255, 255, 120), (r, r), r)
            surface.blit(flash, (x - r, y - r))

    # ------------------------------------------------------------------
    def _draw_red(
        self, surface: pygame.Surface, x: int, y: int, r: int, t: float
    ) -> None:
        """Красный злюка: V-брови, горящие глаза, огонь изо рта."""
        # Круг
        pygame.draw.circle(surface, (255, 0, 0), (x, y), r)
        pygame.draw.circle(surface, (160, 0, 0), (x, y), r, max(3, r // 18))

        # Глаза (горят)
        eye_dx = r // 3
        eye_dy = r // 5
        eye_r = max(5, r // 6)
        for sx in (-1, 1):
            ex = x + sx * eye_dx
            ey = y - eye_dy
            # Жёлтое свечение
            pygame.draw.circle(surface, (255, 230, 60), (ex, ey), eye_r)
            pygame.draw.circle(surface, (255, 90, 0), (ex, ey), max(2, eye_r // 2))
            # Зрачок
            pygame.draw.circle(surface, (30, 0, 0), (ex + sx * 2, ey), max(1, eye_r // 3))

        # Злые V-брови
        brow_len = r // 2
        brow_w = max(4, r // 10)
        pygame.draw.line(
            surface, (90, 0, 0),
            (x - eye_dx - eye_r, y - eye_dy - eye_r - 2),
            (x - eye_dx + eye_r + 4, y - eye_dy - eye_r + brow_len // 2),
            brow_w,
        )
        pygame.draw.line(
            surface, (90, 0, 0),
            (x + eye_dx + eye_r, y - eye_dy - eye_r - 2),
            (x + eye_dx - eye_r - 4, y - eye_dy - eye_r + brow_len // 2),
            brow_w,
        )

        # Открытый рот с огнём
        mouth_w = r // 2
        mouth_h = r // 3
        mouth_y = y + r // 3
        pygame.draw.ellipse(
            surface, (40, 0, 0),
            (x - mouth_w // 2, mouth_y, mouth_w, mouth_h),
        )
        pygame.draw.ellipse(
            surface, (140, 0, 0),
            (x - mouth_w // 2, mouth_y, mouth_w, mouth_h),
            max(2, r // 25),
        )
        # Пламя изо рта
        flick = int(mouth_h * (0.5 + 0.5 * math.sin(t * 22.0)))
        flame_pts = [
            (x - mouth_w // 3, mouth_y + mouth_h - 2),
            (x + mouth_w // 3, mouth_y + mouth_h - 2),
            (x, mouth_y + mouth_h + flick + r // 6),
        ]
        pygame.draw.polygon(surface, (255, 140, 0), flame_pts)
        inner = [
            (x - mouth_w // 6, mouth_y + mouth_h - 4),
            (x + mouth_w // 6, mouth_y + mouth_h - 4),
            (x, mouth_y + mouth_h + flick // 2),
        ]
        pygame.draw.polygon(surface, (255, 240, 90), inner)

    # ------------------------------------------------------------------
    def _draw_blue(
        self, surface: pygame.Surface, x: int, y: int, r: int, t: float
    ) -> None:
        """Синий крутарь: солнечные очки, ухмылка, аура холода."""
        # Аура холода (полупрозрачные круги)
        aura = pygame.Surface((r * 2 + 60, r * 2 + 60), pygame.SRCALPHA)
        for i, (ar, alpha) in enumerate(
            ((r + 26, 30), (r + 14, 45), (r + 6, 60))
        ):
            pulse = int(4 * math.sin(t * 4.0 + i))
            pygame.draw.circle(
                aura, (160, 230, 255, alpha),
                (r + 30, r + 30), ar + pulse, 3,
            )
        # Ледяные кристаллы вокруг
        for i in range(6):
            ang = t * 1.5 + i * math.tau / 6
            cx = r + 30 + int(math.cos(ang) * (r + 24))
            cy = r + 30 + int(math.sin(ang) * (r + 24))
            pygame.draw.polygon(
                aura, (220, 245, 255, 160),
                [(cx, cy - 7), (cx + 4, cy), (cx, cy + 7), (cx - 4, cy)],
            )
        surface.blit(aura, (x - r - 30, y - r - 30))

        # Круг
        pygame.draw.circle(surface, (0, 102, 255), (x, y), r)
        pygame.draw.circle(surface, (0, 40, 140), (x, y), r, max(3, r // 18))

        # Солнечные очки
        glass_h = max(8, r // 3)
        glass_w = max(14, r // 2)
        glass_y = y - r // 5
        # Линза слева
        pygame.draw.rect(
            surface, (10, 10, 20),
            (x - r // 2, glass_y, glass_w, glass_h),
            border_radius=4,
        )
        # Линза справа
        pygame.draw.rect(
            surface, (10, 10, 20),
            (x + r // 2 - glass_w, glass_y, glass_w, glass_h),
            border_radius=4,
        )
        # Перемычка
        pygame.draw.line(
            surface, (10, 10, 20),
            (x - r // 2 + glass_w, glass_y + glass_h // 3),
            (x + r // 2 - glass_w, glass_y + glass_h // 3),
            max(3, r // 20),
        )
        # Блик на очках
        pygame.draw.line(
            surface, (230, 245, 255),
            (x - r // 2 + 3, glass_y + 3),
            (x - r // 2 + glass_w // 2, glass_y + 3),
            max(2, r // 30),
        )

        # Ухмылка
        smile_rect = pygame.Rect(x - r // 2, y + r // 8, r, r // 2)
        pygame.draw.arc(
            surface, (0, 30, 90), smile_rect,
            math.pi * 0.15, math.pi * 0.85,
            max(4, r // 12),
        )

        # Жест рукой после атаки (синий делает «черпак»)
        if self.gesture > 0.0:
            g = self.gesture  # 1 → 0
            arm_len = r + int(30 * g)
            ang = math.pi * (0.8 + 0.5 * (1.0 - g))
            ax = x + int(math.cos(ang) * arm_len)
            ay = y + int(math.sin(ang) * arm_len)
            pygame.draw.line(surface, (0, 60, 180), (x, y + r // 2), (ax, ay), max(5, r // 14))
            pygame.draw.circle(surface, (0, 102, 255), (ax, ay), max(6, r // 12))
            pygame.draw.circle(surface, (255, 255, 255), (ax, ay), max(6, r // 12), 2)
