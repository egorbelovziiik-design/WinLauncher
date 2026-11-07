"""Scenario orchestration: shared context, stats and the phase runner."""

from __future__ import annotations

import logging
import math
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
        require_escape_hold: bool = False,
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
        self.battle_elapsed = 0.0
        self.wave = 0
        self.last_dt = 1.0 / config.FPS
        self.last_raw_dt = 1.0 / config.FPS
        self.frame = 0
        self.phase = "boot"
        self.winner: Optional[str] = None
        self.skip_death = False

        # Escape hatch + time effects.
        self.require_escape_hold = bool(require_escape_hold)
        self.escape_hold = 0.0
        self.hitstop_time = 0.0
        self.slowmo_time = 0.0
        self.slowmo_factor = config.SLOWMO_FACTOR

        # HUD juice.
        self.hud_flash_red = 0.0
        self.hud_flash_blue = 0.0
        self._last_kills: Tuple[int, int] = (0, 0)

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
    # Rhythm + cinematic time
    # ------------------------------------------------------------------ #
    @property
    def beat_period(self) -> float:
        """Seconds per beat of the battle theme."""
        return 60.0 / max(1, config.MUSIC_BPM)

    def beat_pulse(self, sharpness: float = 3.0) -> float:
        """1.0 right on the beat, decaying to 0.0 before the next one."""
        phase = (self.elapsed % self.beat_period) / self.beat_period
        return (1.0 - phase) ** sharpness

    def hitstop(self, seconds: float = config.HITSTOP_SECONDS) -> None:
        """Freeze time for a split second to sell an impact."""
        self.hitstop_time = max(self.hitstop_time, seconds)

    def slowmo(
        self,
        factor: float = config.SLOWMO_FACTOR,
        seconds: float = config.SLOWMO_SECONDS,
    ) -> None:
        """Briefly slow the simulation down (used for the final blow)."""
        self.slowmo_factor = max(0.05, min(1.0, factor))
        self.slowmo_time = max(self.slowmo_time, seconds)

    @property
    def time_factor(self) -> float:
        """1.0 normally, less during hitstop / slow motion."""
        factor = 1.0
        if self.hitstop_time > 0.0:
            factor *= 0.06
        elif self.slowmo_time > 0.0:
            factor *= self.slowmo_factor
        return factor

    # ------------------------------------------------------------------ #
    # Frame lifecycle
    # ------------------------------------------------------------------ #
    def begin_frame(self) -> float:
        """Pump events, compute a clamped delta and advance all timers."""
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                raise ScenarioAbort("window closed")

        raw = self.clock.tick(config.FPS) / 1000.0
        raw = min(raw, config.MAX_DT)
        self.last_raw_dt = raw

        # Emergency exit: hold Escape (the kiosk lock makes a single tap too
        # easy to trigger by accident).
        if pygame.key.get_pressed()[pygame.K_ESCAPE]:
            self.escape_hold += raw
            if not self.require_escape_hold or self.escape_hold >= config.ESCAPE_HOLD_SECONDS:
                raise ScenarioAbort("escape")
        else:
            self.escape_hold = 0.0

        if self.hitstop_time > 0.0:
            self.hitstop_time = max(0.0, self.hitstop_time - raw)
        if self.slowmo_time > 0.0:
            self.slowmo_time = max(0.0, self.slowmo_time - raw)

        dt = raw * self.time_scale * self.time_factor
        self.last_dt = dt
        self.elapsed += dt
        self.frame += 1

        self.flash.update(dt)
        self.shake.update(dt)
        self.glitch.update(dt)
        self.hud_flash_red = max(0.0, self.hud_flash_red - raw * 2.5)
        self.hud_flash_blue = max(0.0, self.hud_flash_blue - raw * 2.5)
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
        if self.require_escape_hold and self.escape_hold > 0.12:
            self._draw_escape_hint()
        pygame.display.flip()
        self._capture_tick()

    def _draw_escape_hint(self) -> None:
        """Progress bar for the 'hold Escape' emergency exit."""
        progress = min(1.0, self.escape_hold / max(0.01, config.ESCAPE_HOLD_SECONDS))
        bar_w = int(self.width * 0.3)
        bar_h = max(6, self.fs(9))
        x = (self.width - bar_w) // 2
        y = self.height - self.fs(64)
        panel = pygame.Surface((bar_w, bar_h + self.fs(34)), pygame.SRCALPHA)
        pygame.draw.rect(panel, (0, 0, 0, 170), panel.get_rect(), border_radius=self.fs(8))
        pygame.draw.rect(
            panel, (255, 255, 255, 60), panel.get_rect(), width=2,
            border_radius=self.fs(8),
        )
        inner = pygame.Rect(self.fs(8), bar_h + self.fs(8), bar_w - self.fs(16), bar_h)
        pygame.draw.rect(panel, (40, 40, 50, 220), inner, border_radius=bar_h // 2)
        filled = inner.copy()
        filled.width = int(inner.width * progress)
        if filled.width > 2:
            pygame.draw.rect(panel, (255, 80, 70, 240), filled, border_radius=bar_h // 2)
        label = self.text.render("УДЕРЖИВАЙ ESC — ВЫХОД", self.fs(17), (215, 215, 225))
        panel.blit(label, label.get_rect(center=(bar_w // 2, self.fs(14))))
        self.screen.surface.blit(panel, panel.get_rect(center=(self.width // 2, y)))

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


# --------------------------------------------------------------------------- #
# HUD
# --------------------------------------------------------------------------- #
def draw_hud(ctx: BattleContext) -> None:
    """Broadcast-style scoreboard: army counts, kills, push bar and wave."""
    red = ctx.armies["red"].count()
    blue = ctx.armies["blue"].count()
    kills_red = ctx.stats.red_kills
    kills_blue = ctx.stats.blue_kills

    if (kills_red, kills_blue) != ctx._last_kills:
        if kills_red > ctx._last_kills[0]:
            ctx.hud_flash_red = 1.0
        if kills_blue > ctx._last_kills[1]:
            ctx.hud_flash_blue = 1.0
        ctx._last_kills = (kills_red, kills_blue)

    pulse = ctx.beat_pulse(2.4)
    panel_w = int(ctx.width * 0.56)
    panel_h = ctx.fs(96)
    x0 = (ctx.width - panel_w) // 2
    y0 = ctx.fs(10)
    radius = ctx.fs(16)
    half = panel_w // 2

    panel = pygame.Surface((panel_w, panel_h), pygame.SRCALPHA)
    for index, color in enumerate(((255, 62, 48), (70, 150, 255))):
        part = pygame.Surface((half, panel_h), pygame.SRCALPHA)
        pygame.draw.rect(
            part, (7, 7, 13, 215), part.get_rect(), border_radius=radius
        )
        corners = (
            dict(
                border_top_left_radius=radius,
                border_bottom_left_radius=radius,
                border_top_right_radius=0,
                border_bottom_right_radius=0,
            )
            if index == 0
            else dict(
                border_top_right_radius=radius,
                border_bottom_right_radius=radius,
                border_top_left_radius=0,
                border_bottom_left_radius=0,
            )
        )
        pygame.draw.rect(
            part, (*color, 230), part.get_rect(), width=2, border_radius=radius,
            **corners,
        )
        panel.blit(part, (index * half, 0))
    pygame.draw.line(
        panel, (255, 215, 0, 190), (half, ctx.fs(10)), (half, panel_h - ctx.fs(10)), 2
    )
    ctx.canvas.blit(panel, (x0, y0))

    top = y0 + ctx.fs(8)
    for side, cx, count, kills, color, flash in (
        ("red", x0 + panel_w * 0.21, red, kills_red, config.RED_BRIGHT, ctx.hud_flash_red),
        ("blue", x0 + panel_w * 0.79, blue, kills_blue, config.BLUE_BRIGHT, ctx.hud_flash_blue),
    ):
        crest = pygame.Surface((ctx.fs(44), ctx.fs(28)), pygame.SRCALPHA)
        glow = int(180 + 75 * max(flash, pulse * 0.6))
        pygame.draw.rect(crest, (*color, glow), crest.get_rect(), border_radius=ctx.fs(5))
        pygame.draw.rect(
            crest, (255, 255, 255, 90), crest.get_rect(), width=1,
            border_radius=ctx.fs(5),
        )
        ctx.canvas.blit(crest, crest.get_rect(center=(int(cx), int(top + ctx.fs(10)))))
        label = "RED" if side == "red" else "BLUE"
        ctx.text.draw(
            ctx.canvas, label, ctx.fs(15), (int(cx), int(top + ctx.fs(31))),
            (235, 235, 242), anchor="center", shadow=False,
        )
        count_size = int(ctx.fs(38) * (1.0 + 0.07 * pulse))
        ctx.text.draw(
            ctx.canvas, str(count), count_size, (int(cx), int(top + ctx.fs(55))),
            color, anchor="center", shadow=True,
        )
        ctx.text.draw(
            ctx.canvas, f"KILLS {kills}", ctx.fs(14),
            (int(cx), int(top + ctx.fs(78))),
            (190, 190, 205), anchor="center", shadow=False,
        )

    badge = ctx.text.render("VS", ctx.fs(22), config.GOLD)
    ctx.canvas.blit(badge, badge.get_rect(center=(int(ctx.width / 2), int(top + ctx.fs(22)))))
    ctx.text.draw(
        ctx.canvas, f"WAVE {max(1, ctx.wave)}", ctx.fs(15),
        (int(ctx.width / 2), int(top + ctx.fs(46))),
        (230, 230, 238), anchor="center", shadow=False,
    )
    ctx.text.draw(
        ctx.canvas, f"{int(ctx.battle_elapsed)}s", ctx.fs(14),
        (int(ctx.width / 2), int(top + ctx.fs(66))),
        (170, 170, 185), anchor="center", shadow=False,
    )

    # Push bar.
    bar_w = int(panel_w * 0.9)
    bar_h = max(6, ctx.fs(9))
    bar_x = (ctx.width - bar_w) // 2
    bar_y = y0 + panel_h + ctx.fs(10)
    total = max(1, red + blue)
    red_w = int(bar_w * red / total)
    pygame.draw.rect(
        ctx.canvas, (10, 10, 16), (bar_x - 2, bar_y - 2, bar_w + 4, bar_h + 4),
        border_radius=bar_h,
    )
    pygame.draw.rect(ctx.canvas, config.RED_PRIMARY, (bar_x, bar_y, red_w, bar_h))
    pygame.draw.rect(
        ctx.canvas, config.BLUE_PRIMARY,
        (bar_x + red_w, bar_y, bar_w - red_w, bar_h),
    )
    for tick in range(1, 4):
        tx = bar_x + bar_w * tick // 4
        pygame.draw.line(ctx.canvas, (0, 0, 0), (tx, bar_y), (tx, bar_y + bar_h), 1)

    marker_x = bar_x + int(bar_w * ctx.front.position)
    pygame.draw.polygon(
        ctx.canvas, config.GOLD,
        [
            (marker_x, bar_y - ctx.fs(7)),
            (marker_x - ctx.fs(6), bar_y - ctx.fs(15)),
            (marker_x + ctx.fs(6), bar_y - ctx.fs(15)),
        ],
    )
    pygame.draw.line(
        ctx.canvas, config.GOLD,
        (marker_x, bar_y - 2), (marker_x, bar_y + bar_h + 2), 2,
    )


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
            phase_death,
            phase_finale,
            phase_intro,
        )

        acts = [phase_intro, phase_bosses, phase_battle, phase_finale]
        if not self.ctx.skip_death:
            acts.append(phase_death)
        acts.append(phase_cleanup)

        started = time.perf_counter()
        for phase in acts:
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
