"""Менеджер битвы: главный цикл, состояние мира, переходы между фазами."""

from __future__ import annotations

import logging
import random
from typing import Optional

import pygame

import config
from audio.music_player import MusicPlayer
from audio.volume_control import VolumeControl
from battle.collision import CollisionSystem
from battle.front_line import FrontLine
from entities.army import Army
from entities.boss import Boss
from entities.projectile import Projectile
from visuals.background import BattleBackground
from visuals.effects import Flash, Glitch, ScreenShake, vignette
from visuals.particles import ParticleSystem
from visuals.text_renderer import TextRenderer

logger = logging.getLogger(__name__)


class BattleManager:
    """Владеет всем мировым состоянием и запускает фазы сценария."""

    def __init__(
        self,
        screen: pygame.Surface,
        volume: VolumeControl,
        music: MusicPlayer,
    ) -> None:
        self.screen = screen
        self.width, self.height = screen.get_size()
        self.volume = volume
        self.music = music
        self.rng = random.Random()

        # Подсистемы
        self.particles = ParticleSystem()
        self.shake = ScreenShake()
        self.flash = Flash()
        self.glitch = Glitch()
        self.text = TextRenderer()
        self.collision = CollisionSystem(self.rng)
        self.front = FrontLine()
        self.background = BattleBackground(self.width, self.height)

        # Мир
        boss_y = self.height * config.BOSS_Y
        self.army = Army(self.width, self.height, self.rng)
        self.red_boss = Boss("red", -config.BOSS_SIZE, boss_y)
        self.blue_boss = Boss("blue", self.width + config.BOSS_SIZE, boss_y)
        self.projectiles: list[Projectile] = []
        self.winner: Optional[str] = None
        self.score: dict[str, int] = {"red": 0, "blue": 0}
        self.reveal: float = 0.0          # 0..1 — разделение экрана
        self.battle_elapsed: float = 0.0
        self.glitch_timer: float = 0.0
        self.intensity_timer: float = 0.0
        self.time: float = 0.0            # общее время, сек
        self.final_mode: bool = False
        self._snapshot: Optional[pygame.Surface] = None

        # Фазы импортируются лениво, чтобы не было циклических импортов
        from phases.phase_battle import PhaseBattle
        from phases.phase_bosses import PhaseBosses
        from phases.phase_cleanup import PhaseCleanup
        from phases.phase_finale import PhaseFinale
        from phases.phase_intro import PhaseIntro

        self.phases = {
            "intro": PhaseIntro(self),
            "bosses": PhaseBosses(self),
            "battle": PhaseBattle(self),
            "finale": PhaseFinale(self),
            "cleanup": PhaseCleanup(self),
        }
        self.current_phase_name: str = "intro"
        self.running: bool = True
        self.frame: pygame.Surface = pygame.Surface(
            (self.width, self.height)
        )

    # ------------------------------------------------------------------
    @property
    def phase(self):
        return self.phases[self.current_phase_name]

    def switch_phase(self, name: str) -> None:
        """Переключить фазу сценария."""
        logger.info("Phase → %s", name)
        self.current_phase_name = name
        self.phases[name].enter()

    def quit(self) -> None:
        """Аварийный выход (Alt+F4 / ESC) с корректным завершением."""
        logger.info("Quit requested.")
        self.running = False

    # ------------------------------------------------------------------
    def spawn_projectile(self, projectile: Projectile) -> None:
        self.projectiles.append(projectile)

    def count_red(self) -> int:
        return self.army.count("red")

    def count_blue(self) -> int:
        return self.army.count("blue")

    # ------------------------------------------------------------------
    def run(self) -> None:
        """Блокирующий главный цикл всего сценария (≈42 сек)."""
        clock = pygame.time.Clock()
        self.phases["intro"].enter()

        while self.running:
            dt = clock.tick(config.FPS) / 1000.0
            # Защита от «прыжков» после фризов
            dt = min(dt, 0.05)

            # --- События ---
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    self.quit()
                elif event.type == pygame.KEYDOWN:
                    if event.key in (pygame.K_ESCAPE, pygame.K_F4):
                        self.quit()
                    if event.key == pygame.K_LALT or event.key == pygame.K_RALT:
                        pass  # Alt+F4 приходит как QUIT
                elif event.type == pygame.WINDOWFOCUSLOST:
                    pass

            if not self.running:
                break

            # --- Обновление мира ---
            self.time += dt
            self._update_common(dt)

            try:
                next_phase = self.phase.update(dt)
            except Exception:
                logger.exception("Phase '%s' crashed", self.current_phase_name)
                raise

            if next_phase is not None:
                self.switch_phase(next_phase)

            # --- Отрисовка ---
            self.phase.draw(self.screen)
            self._post_effects(self.screen)
            pygame.display.flip()

        # Конец цикла
        logger.info("Battle loop finished.")

    # ------------------------------------------------------------------
    def _update_common(self, dt: float) -> None:
        """Общее обновление: частицы, эффекты, звук, таймеры."""
        self.particles.update(dt)
        self.shake.update(dt)
        self.flash.update(dt)
        self.glitch.update(dt)
        self.music.update(dt)
        self.collision.update_timers(dt)

    def _post_effects(self, surface: pygame.Surface) -> None:
        """Тряска → глитч → вспышка → виньетка (поверх всего)."""
        # Тряска экрана
        if self.shake.active:
            original = surface.copy()
            surface.fill(config.BLACK)
            surface.blit(original, (self.shake.offset_x, self.shake.offset_y))

        # Глитч каждые 5–8 сек (таймер крутит фаза битвы)
        self.glitch.apply(surface)

        # Вспышки (белая/цветная)
        self.flash.draw(surface)

        # Виньетка
        vignette(surface, strength=110)

    # ------------------------------------------------------------------
    def take_snapshot(self) -> None:
        """Сохранить кадр (для плавного fade-to-black в cleanup)."""
        self._snapshot = self.screen.copy()

    @property
    def snapshot(self) -> Optional[pygame.Surface]:
        return self._snapshot
