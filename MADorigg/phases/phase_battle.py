"""Фаза 3: основная битва армий, атаки боссов, линия фронта."""

from __future__ import annotations

import logging
import random

import pygame

import config

logger = logging.getLogger(__name__)


class PhaseBattle:
    """15–22 сек (рандом): волны солдат, бои, снаряды, счёт, глитчи."""

    def __init__(self, manager) -> None:
        self.manager = manager
        self.t: float = 0.0
        self.duration: float = 18.0
        self._rng = random.Random()
        self._next_glitch: float = 6.0
        self._intensity_timer: float = 0.0
        self._ambient_timer: float = 2.5
        self._music_started: bool = False

    def enter(self) -> None:
        self.t = 0.0
        self.duration = config.dur(
            self._rng.uniform(*config.PHASE_BATTLE_DURATION)
        )
        self._next_glitch = self._rng.uniform(config.dur(5.0), config.dur(8.0))
        self._intensity_timer = 0.0
        self._ambient_timer = 2.0
        m = self.manager
        m.reveal = 1.0
        m.battle_elapsed = 0.0
        m.front.snap(0.5)
        m.final_mode = False

        # Первые волны сразу
        m.army.spawn_wave("red", self._rng.randint(5, 10))
        m.army.spawn_wave("blue", self._rng.randint(5, 10))

        # Фоновая эпичная музыка 160 BPM
        if not self._music_started:
            self._music_started = True
            m.music.prepare_music(config.MUSIC_BPM, 16.0)
            m.music.play_music(config.MUSIC_BPM, 16.0, volume=0.7)

        logger.info("Battle starts! duration=%.1fs", self.duration)

    # ------------------------------------------------------------------
    def update(self, dt: float) -> str | None:
        self.t += dt
        m = self.manager
        m.battle_elapsed = self.t

        # --- Армии: спавн волн и обновление ---
        m.army.spawn_tick(dt)
        m.army.update(dt, m.particles)

        # --- Боссы атакуют каждые 2 сек ---
        self._boss_attacks(dt)

        # --- Снаряды ---
        for projectile in m.projectiles:
            projectile.update(dt)
            if projectile.alive:
                self._projectile_trail(projectile)
        m.projectiles = [
            p for p in m.projectiles
            if p.alive and not p.offscreen(m.width)
        ]

        # --- Столкновения ---
        m.collision.resolve_melee(m)
        m.collision.resolve_projectiles(m)

        # --- Линия фронта ---
        m.front.update(m.count_red(), m.count_blue(), dt)

        # --- Счёт ---
        m.score["red"] = m.count_red()
        m.score["blue"] = m.count_blue()

        # --- Глитч каждые 5–8 сек ---
        self._next_glitch -= dt
        if self._next_glitch <= 0.0:
            m.glitch.trigger(self._rng.uniform(0.25, 0.45))
            self._next_glitch = self._rng.uniform(
                config.dur(5.0), config.dur(8.0)
            )

        # --- Нарастающее напряжение: +5% каждые 5 секунд ---
        self._intensity_timer += dt
        if self._intensity_timer >= config.dur(5.0):
            self._intensity_timer -= config.dur(5.0)
            m.music.raise_intensity(0.05)

        # --- Случайные фоновые звуки боя ---
        self._ambient_timer -= dt
        if self._ambient_timer <= 0.0:
            self._ambient_timer = self._rng.uniform(1.5, 3.5)
            self._ambient_sfx()

        # --- Условие завершения 1: линия на 20/80% ---
        if m.front.triggered():
            winner = m.front.winning_side()
            m.winner = winner or (
                "red" if m.front.position > 0.5 else "blue"
            )
            logger.info("Front line triggered → winner=%s", m.winner)
            return "finale"

        # --- Условие завершения 2: время вышло ---
        if self.t >= self.duration:
            # Сторона побеждает случайно (50/50), если фронт не решили
            m.winner = "red" if self._rng.random() < 0.5 else "blue"
            logger.info("Battle time is up → winner=%s (50/50)", m.winner)
            return "finale"

        return None

    # ------------------------------------------------------------------
    def _boss_attacks(self, dt: float) -> None:
        m = self.manager
        for boss in (m.red_boss, m.blue_boss):
            boss.update(dt)
            if boss.attack_ready():
                projectile = boss.attack(m.width, m.height)
                if projectile is not None:
                    m.spawn_projectile(projectile)
                    if boss.side == "red":
                        m.music.play("fire", volume=0.8)
                    else:
                        m.music.play("ice", volume=0.8)
                    m.shake.trigger(3.0, 0.15)

    def _projectile_trail(self, projectile) -> None:
        m = self.manager
        if projectile.kind == "fireball":
            m.particles.emit(
                projectile.x, projectile.y, 2,
                colors=[(255, 140, 0), (255, 220, 60)],
                speed=(20.0, 90.0),
                lifetime=(0.15, 0.4),
                size=(2.0, 5.0),
            )
        elif projectile.kind == "ice_beam":
            m.particles.ice_beam_spark(projectile.x, projectile.y)
            tx, ty = projectile.tail_pos()
            if self._rng.random() < 0.6:
                m.particles.ice_beam_spark(
                    projectile.x + (tx - projectile.x) * self._rng.random(),
                    projectile.y + (ty - projectile.y) * self._rng.random(),
                )

    def _ambient_sfx(self) -> None:
        m = self.manager
        choice = self._rng.random()
        if choice < 0.3:
            m.music.play("crowd", volume=0.3)
        elif choice < 0.55:
            m.music.play("sword", volume=0.35)
        elif choice < 0.7:
            m.music.play("death", volume=0.3)

    # ------------------------------------------------------------------
    def draw(self, surface: pygame.Surface) -> None:
        m = self.manager
        t = self.t
        w, h = surface.get_size()

        # Фон (красный | молния | синий)
        m.background.draw(surface, t, reveal=1.0)

        # Линия фронта (мерцающая, зигзагообразная)
        m.front.draw(surface, t)

        # Армии
        m.army.draw(surface, t)

        # Боссы
        m.red_boss.draw(surface, t)
        m.blue_boss.draw(surface, t)

        # Снаряды
        for projectile in m.projectiles:
            projectile.draw(surface, t)

        # Частицы
        m.particles.draw(surface)

        # Счётчик сверху
        m.text.score_hud(surface, m.score["red"], m.score["blue"])

        # Название с глитчем каждые пару секунд
        if int(t * 1.5) % 7 == 0 and (t % 1.0) < 0.4:
            m.text.glitch(
                surface, "MADorigg",
                size=max(30, w // 34),
                y=int(h * 0.045),
                t=t,
                amplitude=3.0,
                center_x=int(w * 0.5),
            )
