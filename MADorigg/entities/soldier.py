"""Stick-figure soldiers, drawn entirely with ``pygame.draw``.

Three unit archetypes spawn per side (scout / regular / brute) with different
sizes, speeds and line weights, so the armies do not look like copy-paste.
"""

from __future__ import annotations

import logging
import math
import random
from typing import TYPE_CHECKING, Optional, Tuple

import pygame

from config import SOLDIER_HEIGHT, SOLDIER_SPEED

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
        base_height: Optional[float] = None,
    ) -> None:
        self.side = side
        self.rng = rng or random.Random()
        self.x = float(x)
        self.y = float(y)

        # Scout / regular / brute.
        roll = self.rng.random()
        if roll < 0.18:
            self.scale = 0.8
            self.archetype = "scout"
            speed_bonus = 1.25
        elif roll > 0.85:
            self.scale = 1.3
            self.archetype = "brute"
            speed_bonus = 0.72
        else:
            self.scale = 1.0
            self.archetype = "regular"
            speed_bonus = 1.0

        base = float(base_height if base_height is not None else self.HEIGHT)
        self.height = max(12, int(round(base * self.scale)))
        base_speed = speed if speed is not None else self.rng.uniform(*SOLDIER_SPEED)
        self.speed = float(base_speed * speed_bonus)
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

    @property
    def line_width(self) -> int:
        """Limb thickness for the current archetype."""
        return 3 if self.archetype == "brute" else 2

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
        ctx.particles.ice_shatter(self.x, self.y - self.height * 0.5, 0.5)

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
            ctx.particles.ice_shatter(self.x, self.y - self.height * 0.5)
        else:
            ctx.particles.spark(self.x, self.y - self.height * 0.6, 5)

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
        if self.anim_timer >= (0.09 if self.archetype == "scout" else 0.11):
            self.anim_timer = 0.0
            self.anim_frame ^= 1
        if self.flash > 0.0:
            self.flash = max(0.0, self.flash - dt * 4.0)

        if self.state == "running":
            self.x += self.direction * self.speed * self.speed_boost * dt
            if self.x < -80 or self.x > ctx.width + 80:
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

        # Ground shadow (cheap, no alpha surface).
        shadow_w = int(self.height * 0.55)
        shadow_h = max(2, int(self.height * 0.16))
        shadow_color = (30, 12, 12) if self.side == "red" else (10, 16, 34)
        pygame.draw.ellipse(
            surface, shadow_color,
            (x - shadow_w // 2, y - shadow_h // 2, shadow_w, shadow_h),
        )

        if self.state == "frozen":
            block_w = int(self.height * 0.8)
            block_h = int(self.height * 1.3)
            ice = pygame.Surface((block_w, block_h), pygame.SRCALPHA)
            pygame.draw.rect(ice, (170, 235, 255, 90), ice.get_rect(), border_radius=4)
            pygame.draw.rect(
                ice, (235, 250, 255, 230), ice.get_rect(), width=1, border_radius=4
            )
            pygame.draw.line(
                ice, (255, 255, 255, 160), (4, block_h - 6), (block_w - 6, 5), 1
            )
            surface.blit(
                ice, (x - block_w // 2 + sway, y - block_h + block_h // 6 + bob)
            )

        self._draw_figure(surface, x + sway, y - bob, sway)

    def _draw_figure(self, surface: pygame.Surface, x: int, y: int, sway: int) -> None:
        """Draw head, torso, arms and legs of the stick figure."""
        body = self.color
        limb = self.limb_color
        outline = (35, 0, 0) if self.side == "red" else (0, 10, 45)
        h = self.height
        lw = self.line_width

        head_radius = max(3, int(round(h * 0.2)))
        head_center = (x, y - h + head_radius)
        shoulder_y = y - int(h * 0.62)
        hip_y = y - int(h * 0.3)

        # Torso.
        pygame.draw.line(surface, body, (x, shoulder_y), (x, hip_y), lw)

        if self.state == "fighting":
            # Boxing pose: fists up, bouncing.
            pygame.draw.line(surface, body, (x, shoulder_y), (x - int(h * 0.25), shoulder_y - int(h * 0.15)), lw)
            pygame.draw.line(surface, body, (x, shoulder_y), (x + int(h * 0.25), shoulder_y - int(h * 0.15)), lw)
            pygame.draw.circle(
                surface, limb, (x - int(h * 0.28), shoulder_y - int(h * 0.18)), max(2, h // 10)
            )
            pygame.draw.circle(
                surface, limb, (x + int(h * 0.28), shoulder_y - int(h * 0.18)), max(2, h // 10)
            )
            pygame.draw.line(surface, body, (x, hip_y), (x - int(h * 0.2), y), lw)
            pygame.draw.line(surface, body, (x, hip_y), (x + int(h * 0.2), y), lw)
        else:
            # Running: arms and legs alternate with the animation frame.
            arm_y = shoulder_y + int(h * 0.1)
            pygame.draw.line(surface, body, (x, shoulder_y), (x - int(h * 0.18) * sway, arm_y), lw)
            pygame.draw.line(surface, body, (x, shoulder_y), (x + int(h * 0.18) * sway, arm_y), lw)
            pygame.draw.circle(
                surface, limb, (x - int(h * 0.22) * sway, arm_y + int(h * 0.06)), max(2, h // 11)
            )
            pygame.draw.circle(
                surface, limb, (x + int(h * 0.22) * sway, arm_y + int(h * 0.06)), max(2, h // 11)
            )
            pygame.draw.line(surface, body, (x, hip_y), (x - int(h * 0.16) * sway, y), lw)
            pygame.draw.line(surface, body, (x, hip_y), (x + int(h * 0.16) * sway, y), lw)

        # Head.
        pygame.draw.circle(surface, body, head_center, head_radius)
        pygame.draw.circle(surface, outline, head_center, head_radius, 1)

        # Face.
        eye_y = head_center[1] - max(1, head_radius // 3)
        eye_dx = max(1, int(head_radius * 0.45))
        if self.side == "blue":
            pygame.draw.line(
                surface, (10, 15, 30),
                (x - eye_dx - 1, eye_y), (x + eye_dx + 1, eye_y), 1,
            )
        else:
            pygame.draw.circle(surface, (20, 0, 0), (x - eye_dx, eye_y), max(1, head_radius // 4))
            pygame.draw.circle(surface, (20, 0, 0), (x + eye_dx, eye_y), max(1, head_radius // 4))
            brow_y = eye_y - max(2, head_radius // 2)
            pygame.draw.line(
                surface, (20, 0, 0),
                (x - eye_dx - 1, brow_y), (x - 1, brow_y + 1), 1,
            )
            pygame.draw.line(
                surface, (20, 0, 0),
                (x + eye_dx + 1, brow_y), (x + 1, brow_y + 1), 1,
            )

        # Brute shield / scout bandana accents.
        if self.archetype == "brute":
            pygame.draw.circle(surface, limb, head_center, head_radius, max(2, head_radius // 3))
        elif self.archetype == "scout":
            pygame.draw.line(
                surface, limb,
                (x - head_radius, head_center[1] - head_radius // 2),
                (x + head_radius, head_center[1] - head_radius // 2),
                2,
            )

    def _draw_dying(self, surface: pygame.Surface) -> None:
        """Falling body followed by a fading cross marker."""
        x = int(self.x)
        y = int(self.y)
        progress = min(1.0, self.death_timer / 1.0)
        fall = self.fall_offset
        h = self.height

        if self.death_timer < 0.35:
            # Body tips over to the side.
            pygame.draw.circle(
                surface, self.color,
                (x + int(h * 0.3) * self.direction, y - int(h * 0.2) + int(fall)),
                max(3, int(h * 0.2)),
            )
            pygame.draw.line(
                surface, self.limb_color,
                (x + int(h * 0.15) * self.direction, y - int(h * 0.15) + int(fall)),
                (x - int(h * 0.3) * self.direction, y - int(h * 0.1) + int(fall)),
                self.line_width,
            )
            return

        alpha = int(255 * max(0.0, 1.0 - (progress - 0.35) / 0.65))
        if alpha <= 0:
            return
        size = max(10, int(h * 0.7))
        marker = pygame.Surface((size, size), pygame.SRCALPHA)
        color = (*self.limb_color, alpha)
        pygame.draw.line(marker, color, (2, 2), (size - 2, size - 2), 2)
        pygame.draw.line(marker, color, (size - 2, 2), (2, size - 2), 2)
        surface.blit(marker, (x - size // 2, y - size + 2))
