"""20x20 stick-figure soldiers, drawn entirely with ``pygame.draw``."""

from __future__ import annotations

import logging
import math
import random
from typing import TYPE_CHECKING, Optional, Tuple

import pygame

from config import BLUE_BRIGHT, RED_BRIGHT, SOLDIER_HEIGHT, SOLDIER_SPEED

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)

Color = Tuple[int, int, int]

RED_BODY: Color = (255, 70, 60)
RED_LIMB: Color = (200, 30, 25)
BLUE_BODY: Color = (95, 155, 255)
BLUE_LIMB: Color = (40, 85, 200)
FROZEN_BODY: Color = (170, 235, 255)
FROZEN_LIMB: Color = (120, 200, 240)


class Soldier:
    """A single tiny fighter.

    Lives on a 2.5D plane: ``x`` moves horizontally, ``y`` is the position of
    the feet on the ground band.
    """

    HEIGHT = SOLDIER_HEIGHT

    def __init__(
        self,
        side: str,
        x: float,
        y: float,
        rng: Optional[random.Random] = None,
        speed: Optional[float] = None,
    ) -> None:
        self.side = side
        self.rng = rng or random.Random()
        self.x = float(x)
        self.y = float(y)
        self.speed = float(speed if speed is not None else self.rng.uniform(*SOLDIER_SPEED))
        self.direction = 1 if side == "red" else -1
        self.state = "running"
        self.anim_frame = 0
        self.anim_timer = 0.0
        self.age = 0.0
        self.death_timer = 0.0
        self.frozen_timer = 0.0
        self.fall_speed = 0.0
        self.fall_offset = 0.0
        self.flash = 0.0
        self.speed_boost = 1.0
        self.death_counted = False

    # ------------------------------------------------------------------ #
    # Queries
    # ------------------------------------------------------------------ #
    @property
    def alive(self) -> bool:
        """True while the soldier is still on the field."""
        return self.state in ("running", "fighting", "frozen", "dying")

    @property
    def targetable(self) -> bool:
        """True if enemies may engage this soldier."""
        return self.state in ("running", "fighting")

    @property
    def color(self) -> Color:
        """Main body colour for the current state."""
        if self.state == "frozen":
            return FROZEN_BODY
        return RED_BODY if self.side == "red" else BLUE_BODY

    @property
    def limb_color(self) -> Color:
        """Secondary colour used for arms and legs."""
        if self.state == "frozen":
            return FROZEN_LIMB
        return RED_LIMB if self.side == "red" else BLUE_LIMB

    def distance_to(self, other: "Soldier") -> float:
        """Euclidean distance to another soldier."""
        return math.hypot(self.x - other.x, self.y - other.y)

    # ------------------------------------------------------------------ #
    # State changes
    # ------------------------------------------------------------------ #
    def fight(self) -> None:
        """Switch to the boxing-in-place animation."""
        if self.state == "running":
            self.state = "fighting"

    def freeze(self, ctx: "BattleContext") -> None:
        """Freeze solid (ice beam); the soldier shatters one second later."""
        if not self.targetable:
            return
        self.state = "frozen"
        self.frozen_timer = 1.0
        ctx.particles.ice_shatter(self.x, self.y - self.HEIGHT * 0.5, 0.5)

    def die(self, ctx: "BattleContext", cause: str = "combat") -> None:
        """Start the death animation, emit particles and count the casualty."""
        if self.state in ("dying", "dead"):
            return
        self.state = "dying"
        self.death_timer = 0.0
        self.fall_speed = -140.0
        if not self.death_counted:
            self.death_counted = True
            ctx.stats.record_death(self.side)
        if cause == "shatter":
            ctx.particles.ice_shatter(self.x, self.y - self.HEIGHT * 0.5)
        else:
            ctx.particles.spark(self.x, self.y - self.HEIGHT * 0.6, 5)

    def kill(self) -> None:
        """Instantly remove the soldier without effects."""
        self.state = "dead"

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(self, dt: float, ctx: "BattleContext") -> None:
        """Advance animation, movement and the death timer."""
        self.age += dt
        self.anim_timer += dt
        if self.anim_timer >= 0.11:
            self.anim_timer = 0.0
            self.anim_frame ^= 1
        if self.flash > 0.0:
            self.flash = max(0.0, self.flash - dt * 4.0)

        if self.state == "running":
            self.x += self.direction * self.speed * self.speed_boost * dt
            if self.x < -80 or self.x > ctx.width + 80:
                # Soldiers never retreat: recycle them to the front.
                self.x = max(-40.0, min(ctx.width + 40.0, self.x))

        elif self.state == "frozen":
            self.frozen_timer -= dt
            if self.frozen_timer <= 0.0:
                self.die(ctx, cause="shatter")

        elif self.state == "dying":
            self.death_timer += dt
            self.fall_speed += 900.0 * dt
            self.fall_offset = min(8.0, self.fall_offset + self.fall_speed * dt * 0.05)
            if self.death_timer > 1.0:
                self.state = "dead"

    # ------------------------------------------------------------------ #
    # Rendering
    # ------------------------------------------------------------------ #
    def draw(self, surface: pygame.Surface) -> None:
        """Draw the soldier in its current pose."""
        if self.state == "dead":
            return
        if self.state == "dying":
            self._draw_dying(surface)
            return

        x = int(self.x)
        y = int(self.y)
        sway = 1 if self.anim_frame == 0 else -1
        bob = 1 if self.anim_frame == 0 else 0

        if self.state == "frozen":
            ice = pygame.Surface((18, 26), pygame.SRCALPHA)
            pygame.draw.rect(ice, (170, 235, 255, 90), ice.get_rect(), border_radius=4)
            pygame.draw.rect(
                ice, (235, 250, 255, 230), ice.get_rect(), width=1, border_radius=4
            )
            surface.blit(ice, (x - 9 + sway, y - 24 + bob))

        self._draw_figure(surface, x + sway, y - bob, sway)

    def _draw_figure(self, surface: pygame.Surface, x: int, y: int, sway: int) -> None:
        """Draw head, torso, arms and legs of the stick figure."""
        body = self.color
        limb = self.limb_color
        outline = (30, 0, 0) if self.side == "red" else (0, 10, 45)

        head_center = (x, y - self.HEIGHT + 4)
        head_radius = 4

        # Torso.
        pygame.draw.line(surface, body, (x, y - 12), (x, y - 5), 3)

        if self.state == "fighting":
            # Boxing pose: fists up, bouncing.
            pygame.draw.line(surface, body, (x, y - 11), (x - 5, y - 14), 2)
            pygame.draw.line(surface, body, (x, y - 11), (x + 5, y - 14), 2)
            pygame.draw.circle(surface, limb, (x - 6, y - 15), 2)
            pygame.draw.circle(surface, limb, (x + 6, y - 15), 2)
            pygame.draw.line(surface, body, (x, y - 5), (x - 4, y), 2)
            pygame.draw.line(surface, body, (x, y - 5), (x + 4, y), 2)
        else:
            # Running: arms and legs alternate with the animation frame.
            pygame.draw.line(surface, body, (x, y - 10), (x - 4 * sway, y - 7), 2)
            pygame.draw.line(surface, body, (x, y - 10), (x + 4 * sway, y - 7), 2)
            pygame.draw.circle(surface, limb, (x - 5 * sway, y - 6), 2)
            pygame.draw.circle(surface, limb, (x + 5 * sway, y - 6), 2)
            pygame.draw.line(surface, body, (x, y - 5), (x - 3 * sway, y), 2)
            pygame.draw.line(surface, body, (x, y - 5), (x + 3 * sway, y), 2)

        # Head.
        pygame.draw.circle(surface, body, head_center, head_radius)
        pygame.draw.circle(surface, outline, head_center, head_radius, 1)

        # Face.
        if self.side == "blue":
            pygame.draw.line(
                surface, (10, 15, 30), (x - 3, y - 17), (x + 3, y - 17), 1
            )
        else:
            pygame.draw.circle(surface, (20, 0, 0), (x - 2, y - 17), 1)
            pygame.draw.circle(surface, (20, 0, 0), (x + 2, y - 17), 1)
            pygame.draw.line(surface, (20, 0, 0), (x - 3, y - 19), (x - 1, y - 18), 1)
            pygame.draw.line(surface, (20, 0, 0), (x + 3, y - 19), (x + 1, y - 18), 1)

    def _draw_dying(self, surface: pygame.Surface) -> None:
        """Falling body followed by a fading cross marker."""
        x = int(self.x)
        y = int(self.y)
        progress = min(1.0, self.death_timer / 1.0)
        fall = self.fall_offset

        if self.death_timer < 0.35:
            # Body tips over to the side.
            pygame.draw.circle(
                surface, self.color, (x + int(6 * self.direction), y - 4 + int(fall)), 4
            )
            pygame.draw.line(
                surface,
                self.limb_color,
                (x + int(3 * self.direction), y - 3 + int(fall)),
                (x - int(6 * self.direction), y - 2 + int(fall)),
                2,
            )
            return

        alpha = int(255 * max(0.0, 1.0 - (progress - 0.35) / 0.65))
        if alpha <= 0:
            return
        marker = pygame.Surface((14, 14), pygame.SRCALPHA)
        color = (*self.limb_color, alpha)
        pygame.draw.line(marker, color, (2, 2), (12, 12), 2)
        pygame.draw.line(marker, color, (12, 2), (2, 12), 2)
        surface.blit(marker, (x - 7, y - 10))
