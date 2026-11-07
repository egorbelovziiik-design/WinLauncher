"""Scenario orchestration: shared context, stats and the phase runner."""

from __future__ import annotations

import logging
import random
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import pygame

import config
from audio.music_player import MusicPlayer
from audio.volume_control import VolumeControl
from entities.army import Army
from entities.boss import Boss
from entities.projectile import Projectile
from visuals.background import BackgroundRenderer
from visuals.effects import Flash, Glitch, ScreenShake, Vignette
from visuals.particles import ParticleSystem
from visuals.screen import Screen
from visuals.text_renderer import TextRenderer
from .front_line import FrontLine

LOG = logging.getLogger(__name__)


class ScenarioAbort(Exception):
    """Raised when the user asks to stop the show (window closed / Escape)."""


@dataclass
class Stats:
    """Live battle statistics shown in the HUD."""

    red_spawned: int = 0
    blue_spawned: int = 0
    red_dead: int = 0
    blue_dead: int = 0
    red_kills: int = 0
    blue_kills: int = 0

    def reset(self) -> None:
        """Zero every counter."""
        self.red_spawned = self.blue_spawned = 0
        self.red_dead = self.blue_dead = 0
        self.red_kills = self.blue_kills = 0

    def record_death(self, side: str) -> None:
        """Register one death of ``side`` (and a kill for the opponent)."""
        if side == "red":
            self.red_dead += 1
            self.blue_kills += 1
        else:
            self.blue_dead += 1
            self.red_kills += 1


class BattleContext:
    """Everything a phase needs: canvas, actors, effects, audio and timing."""

    def __init__(
        self,
        screen: Screen,
        audio: Optional[MusicPlayer],
        volume: Optional[VolumeControl],
        *,
        time_scale: float = 1.0,
        seed: Optional[int] = None,
        capture_dir: Optional[Path] = None,
        capture_interval: float = 0.4,
    ) -> None:
        self.screen = screen
        self.audio = audio
        self.volume = volume
        self.width, self.height = screen.size
        self.canvas = pygame.Surface((self.width, self.height)).convert()
        self.canvas.fill(config.BLACK)
        self.clock = pygame.time.Clock()

        self.rng = random.Random(seed)
        self.time_scale = max(0.01, float(time_scale))
        self.ui_scale = self.height / 1080.0

        self.particles = ParticleSystem(config.MAX_PARTICLES, self.rng)
        self.flash = Flash()
        self.shake = ScreenShake(self.rng)
        self.glitch = Glitch(self.rng)
        self.vignette = Vignette()
        self.bg = BackgroundRenderer((self.width, self.height), self.rng)
        self.text = TextRenderer(self.rng)
        self.front = FrontLine(self.width, self.height, self.rng)

        self.ground_y = self.height * config.GROUND_RATIO
        self.armies: Dict[str, Army] = {
            "red": Army("red", self),
            "blue": Army("blue", self),
        }
        self.bosses: Dict[str, Boss] = {}
        self.projectiles: List[Projectile] = []
        self.stats = Stats()

        self.elapsed = 0.0
        self.last_dt = 1.0 / config.FPS
        self.frame = 0
        self.phase = "boot"
        self.winner: Optional[str] = None

        self.capture_dir = Path(capture_dir) if capture_dir else None
        self.capture_interval = max(0.05, float(capture_interval))
        self._capture_timer = 0.0
        self._capture_index = 0
        if self.capture_dir is not None:
            self.capture_dir.mkdir(parents=True, exist_ok=True)

        # Warm up the effect caches so the first battle frame is not slower.
        self.vignette.draw(self.canvas)
        self.canvas.fill(config.BLACK)

    # ------------------------------------------------------------------ #
    # Helpers
    # ------------------------------------------------------------------ #
    def fs(self, size: float) -> int:
        """Scale a 1080p font size to the current resolution."""
        return max(10, int(size * self.ui_scale))

    def emit(self, name: str, volume: float = 1.0, cooldown: float = 0.035) -> None:
        """Fire-and-forget sound effect (audio may be disabled)."""
        if self.audio is not None:
            self.audio.play(name, volume, cooldown)

    # ------------------------------------------------------------------ #
    # Frame lifecycle
    # ------------------------------------------------------------------ #
    def begin_frame(self) -> float:
        """Pump events, compute a clamped delta and advance all timers."""
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                raise ScenarioAbort("window closed")
            if event.type == pygame.KEYDOWN and event.key == pygame.K_ESCAPE:
                raise ScenarioAbort("escape pressed")
            if event.type == pygame.KEYDOWN and event.key == pygame.K_F4:
                if pygame.key.get_mods() & pygame.KMOD_ALT:
                    raise ScenarioAbort("alt+f4")

        raw = self.clock.tick(config.FPS) / 1000.0
        raw = min(raw, config.MAX_DT)
        dt = raw * self.time_scale
        self.last_dt = dt
        self.elapsed += dt
        self.frame += 1

        self.flash.update(dt)
        self.shake.update(dt)
        self.glitch.update(dt)
        if self.audio is not None:
            self.audio.update(dt)
        return dt

    def present(self) -> None:
        """Apply screen-space effects, blit and optionally capture a frame."""
        if self.glitch.active:
            self.glitch.apply(self.canvas)
        self.vignette.draw(self.canvas)
        self.screen.present(self.canvas, self.shake.offset())
        self.flash.draw(self.screen.surface)
        pygame.display.flip()
        self._capture_tick()

    def _capture_tick(self) -> None:
        """Save periodic PNG frames when ``--capture`` is enabled (docs/tests)."""
        if self.capture_dir is None:
            return
        self._capture_timer += self.last_dt
        if self._capture_timer < self.capture_interval:
            return
        self._capture_timer = 0.0
        path = self.capture_dir / (
            f"frame_{self._capture_index:04d}_{self.phase}.png"
        )
        self._capture_index += 1
        try:
            pygame.image.save(self.canvas, str(path))
        except Exception as exc:  # pragma: no cover - disk issues
            LOG.debug("Frame capture failed: %s", exc)


def draw_hud(ctx: BattleContext) -> None:
    """Draw the live ``RED: n | VS | BLUE: m`` counter at the top."""
    red_count = ctx.armies["red"].count()
    blue_count = ctx.armies["blue"].count()
    size = ctx.fs(34)
    red_img = ctx.text.render(f"RED: {red_count}", size, (255, 115, 105))
    blue_img = ctx.text.render(f"BLUE: {blue_count}", size, (125, 190, 255))
    vs_img = ctx.text.render("VS", size, config.GOLD)
    gap = ctx.fs(38)
    dot = ctx.fs(9)

    total = (
        red_img.get_width()
        + blue_img.get_width()
        + vs_img.get_width()
        + gap * 2
        + dot * 4
        + ctx.fs(30)
    )
    x = (ctx.width - total) // 2
    y = ctx.fs(16)
    row_center = y + red_img.get_height() // 2

    pygame.draw.circle(ctx.canvas, config.RED_PRIMARY, (x + dot, row_center), dot)
    x += dot * 2 + ctx.fs(10)
    ctx.canvas.blit(red_img, (x, y))
    x += red_img.get_width() + gap
    ctx.canvas.blit(vs_img, (x, y))
    x += vs_img.get_width() + gap
    ctx.canvas.blit(blue_img, (x, y))
    x += blue_img.get_width() + ctx.fs(10)
    pygame.draw.circle(ctx.canvas, config.BLUE_PRIMARY, (x + dot, row_center), dot)


class BattleManager:
    """Runs the five phases in order."""

    def __init__(self, ctx: BattleContext) -> None:
        self.ctx = ctx

    def run(self) -> str:
        """Execute the whole scenario and return the winner side."""
        from phases import (
            phase_battle,
            phase_bosses,
            phase_cleanup,
            phase_finale,
            phase_intro,
        )

        started = time.perf_counter()
        for phase in (phase_intro, phase_bosses, phase_battle, phase_finale, phase_cleanup):
            name = phase.__name__.split(".")[-1]
            self.ctx.phase = name
            LOG.info("=== %s ===", name)
            phase.run(self.ctx)
        LOG.info(
            "Scenario finished in %.1f s (%.1f s of show time)",
            time.perf_counter() - started,
            self.ctx.elapsed,
        )
        return self.ctx.winner or "red"
