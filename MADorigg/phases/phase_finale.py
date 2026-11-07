"""Фаза 4: финал — мега-атака, гибель проигравшего, триумф победителя."""

from __future__ import annotations

import logging
import math
import random

import pygame

import config

logger = logging.getLogger(__name__)


class PhaseFinale:
    """≈5 сек: рывок армии победителя, мега-удар, взрыв, победный танец."""

    def __init__(self, manager) -> None:
        self.manager = manager
        self.t: float = 0.0
        self._rng = random.Random()
        self.winner: str = "red"
        self.loser: str = "blue"
        self._mega_fired: bool = False
        self._loser_exploded: bool = False
        self._celebrating: bool = False
        self._fanfare_done: bool = False
        self._confetti_timer: float = 0.0
        self._victory_text_ready: bool = False

    def enter(self) -> None:
        self.t = 0.0
        self._mega_fired = False
        self._loser_exploded = False
        self._celebrating = False
        self._fanfare_done = False
        self._confetti_timer = 0.0
        self._victory_text_ready = False
        m = self.manager

        # Победитель: случай 50/50 (если не определил фронт)
        if m.winner is None:
            m.winner = "red" if self._rng.random() < 0.5 else "blue"
        self.winner = m.winner
        self.loser = "blue" if self.winner == "red" else "red"

        logger.info("FINALE: %s side wins!", self.winner.upper())

        # Армия победителя ×3, армия проигравшего уменьшается
        m.army.boost(self.winner, multiplier=3.0)
        m.army.kill_random(self.loser, 0.4, self._rng)

        # Боссы: победитель растёт и светится, проигравший мигает
        if self.winner == "red":
            m.red_boss.grow_victory()
            m.red_boss.victory()
            m.blue_boss.shrink_defeat()
        else:
            m.blue_boss.grow_victory()
            m.blue_boss.victory()
            m.red_boss.shrink_defeat()

        # Напряжение на максимуме
        m.music.set_intensity(1.4)
        m.music.play("crowd", volume=0.8)

    # ------------------------------------------------------------------
    @property
    def winner_boss(self):
        return self.manager.red_boss if self.winner == "red" else self.manager.blue_boss

    @property
    def loser_boss(self):
        return self.manager.blue_boss if self.winner == "red" else self.manager.red_boss

    # ------------------------------------------------------------------
    def update(self, dt: float) -> str | None:
        self.t += dt
        m = self.manager
        t = self.t

        # Армии продолжают двигаться
        m.army.spawn_tick(dt * 0.3)  # почти без новых волн
        m.army.update(dt, m.particles)

        # Боссы обновляются
        m.red_boss.update(dt)
        m.blue_boss.update(dt)

        # Линия фронта стремительно сдвигается к проигравшему
        m.front.surge(self.winner, dt, speed=0.5)

        # Проигравший: продолжает уменьшаться
        if t >= config.dur(0.5):
            self.loser_boss.shrink_defeat()

        # --- МЕГА-АТАКА ---
        if t >= config.dur(1.2) and not self._mega_fired:
            self._mega_fired = True
            mega = self.winner_boss.mega_attack(
                self.loser_boss.x, self.loser_boss.y
            )
            m.spawn_projectile(mega)
            m.music.play("mega", volume=1.0)
            m.music.play("fire", volume=0.9)
            m.shake.trigger(15.0, 0.5)
            m.glitch.trigger(0.4)
            logger.info("Mega attack fired!")

        # Обновление снарядов (включая мега)
        for projectile in m.projectiles:
            projectile.update(dt)
            if projectile.alive and projectile.kind == "fireball":
                m.particles.fire_explosion(projectile.x, projectile.y)
        m.projectiles = [
            p for p in m.projectiles
            if p.alive and not p.offscreen(m.width)
        ]

        # --- ВЫБУХ ПРОИГРАВШЕГО ---
        mega_hit = any(
            p.kind == "mega"
            and math.hypot(
                p.x - self.loser_boss.x, p.y - self.loser_boss.y
            )
            < self.loser_boss.radius + p.size * 0.5
            for p in m.projectiles
        )
        if self._mega_fired and (mega_hit or t >= config.dur(2.0)):
            if not self._loser_exploded:
                self._explode_loser()

        # --- ПОБЕДИТЕЛЬ ПЕРЕСЕЛЯЕТСЯ В ЦЕНТР ---
        if self._loser_exploded and not self._celebrating:
            self._celebrating = True
            self.winner_boss.victory()
            m.army.kill_all(self.loser)
            m.music.play("fanfare", volume=1.0)
            m.music.play("crowd", volume=1.0)
            self._fanfare_done = True
            logger.info("Winner celebrates!")

        if self._celebrating:
            boss = self.winner_boss
            # Плавно в центр экрана
            target_x = m.width * 0.5
            target_y = m.height * 0.5
            ease = min(1.0, dt * 2.5)
            boss.set_home(
                boss.home_x + (target_x - boss.home_x) * ease,
                boss.home_y + (target_y - boss.home_y) * ease,
            )
            boss.x += (boss.home_x - boss.x) * ease
            boss.y += (boss.home_y - boss.y) * ease
            # Увеличивается до 300×300
            boss.target_size = float(config.BOSS_FINAL_SIZE)
            # Прыжки и вращение через позу победы
            boss.state = "victory"

            # Фейерверки/конфетти
            self._confetti_timer -= dt
            if self._confetti_timer <= 0.0:
                self._confetti_timer = 0.3
                fx = self._rng.uniform(m.width * 0.2, m.width * 0.8)
                fy = self._rng.uniform(m.height * 0.2, m.height * 0.6)
                m.particles.confetti(fx, fy)
                if self._rng.random() < 0.5:
                    m.particles.confetti(
                        self._rng.uniform(0, m.width),
                        self._rng.uniform(0, m.height * 0.5),
                    )
            if t >= config.dur(3.0):
                self._victory_text_ready = True

        # Случайный глитч для эпичности
        if int(t) != int(t - dt) and self._rng.random() < 0.4:
            m.glitch.trigger(0.2)

        # --- Конец фазы ---
        if t >= config.dur(config.PHASE_FINALE_DURATION):
            m.take_snapshot()
            return "cleanup"
        return None

    # ------------------------------------------------------------------
    def _explode_loser(self) -> None:
        """Проигравший взрывается: 50+ частиц, вся армия падает."""
        self._loser_exploded = True
        m = self.manager
        boss = self.loser_boss

        # 50+ частиц разлёта
        for _ in range(3):
            m.particles.fire_explosion(boss.x, boss.y)
            m.particles.spark(boss.x, boss.y)
            m.particles.smoke(boss.x, boss.y)
        m.particles.confetti(boss.x, boss.y)

        boss.die()
        m.army.kill_all(self.loser)
        m.shake.trigger(15.0, 0.5)
        m.flash.trigger((255, 255, 255), 0.5, peak_alpha=255)
        m.music.play("explosion", volume=1.0)
        m.music.play("crowd", volume=0.9)
        logger.info("Loser boss exploded!")

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface) -> None:
        m = self.manager
        t = self.t
        w, h = surface.get_size()

        m.background.draw(surface, t, reveal=1.0)
        m.front.draw(surface, t)
        m.army.draw(surface, t)

        # Боссы (проигравший мигает и уменьшается, победитель — в центре)
        if not m.red_boss.dead:
            m.red_boss.draw(surface, t)
        if not m.blue_boss.dead:
            m.blue_boss.draw(surface, t)

        for projectile in m.projectiles:
            projectile.draw(surface, t)

        m.particles.draw(surface)

        # Текст победителя: огромный, золотой, пульсирует
        if self._victory_text_ready or (
            self._celebrating and t >= config.dur(3.0)
        ):
            m.text.victory(surface, self.winner, t)
            subtitle = "MADorigg решает, кто король экрана"
            m.text.pulse(
                surface, subtitle,
                size=max(22, w // 48),
                color=(255, 255, 255),
                y=int(h * 0.26),
                t=t,
                speed=5.0,
            )
