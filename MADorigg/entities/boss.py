"""The two boss smileys - the whole show is their fight for the screen."""

from __future__ import annotations

import logging
import math
import random
from typing import TYPE_CHECKING, Optional, Tuple

import pygame

from config import (
    BLUE_BRIGHT,
    BLUE_PRIMARY,
    BOSS_ATTACK_COOLDOWN,
    BOSS_SIZE,
    FIREBALL_SPEED,
    ICE_BEAM_SPEED,
    RED_BRIGHT,
    RED_PRIMARY,
    WHITE,
)
from .projectile import Projectile

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)

Color = Tuple[int, int, int]


class Boss:
    """A giant smiley that shoots fire or ice at the enemy army."""

    def __init__(
        self,
        side: str,
        x: float,
        y: float,
        size: int = BOSS_SIZE,
        rng: Optional[random.Random] = None,
    ) -> None:
        self.side = side
        self.rng = rng or random.Random()
        self.x = float(x)
        self.y = float(y)
        self.home_x = float(x)
        self.home_y = float(y)
        self.size = float(size)
        self.target_size = float(size)
        self.health = 100.0
        self.state = "idle"  # idle | attacking | hurt | victory | dead
        self.facing = 1 if side == "red" else -1
        self.attack_timer = 0.0
        self.attack_cooldown = BOSS_ATTACK_COOLDOWN
        self.auto_attack = True
        self.attack_anim = 0.0
        self.recoil = 0.0
        self.hurt_timer = 0.0
        self.alive = True
        self.t = 0.0
        self.rotation = 0.0
        self.offset_x = 0.0
        self.offset_y = 0.0
        self.glow = 0.0
        self._tmp: Optional[pygame.Surface] = None
        self._tmp_size = 0

    # ------------------------------------------------------------------ #
    # Queries
    # ------------------------------------------------------------------ #
    @property
    def radius(self) -> float:
        """Current radius in pixels."""
        return self.size * 0.5

    @property
    def center(self) -> Tuple[float, float]:
        """Current centre including animation offsets."""
        return self.x + self.offset_x, self.y + self.offset_y

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(self, dt: float, ctx: "BattleContext") -> Optional[Projectile]:
        """Animate the boss and return a projectile when it attacks."""
        self.t += dt
        self.size += (self.target_size - self.size) * min(1.0, dt * 3.0)

        if self.attack_anim > 0.0:
            self.attack_anim = max(0.0, self.attack_anim - dt)
        if self.recoil > 0.0:
            self.recoil = max(0.0, self.recoil - dt * 3.5)
        if self.hurt_timer > 0.0:
            self.hurt_timer = max(0.0, self.hurt_timer - dt)

        self._update_motion()

        projectile: Optional[Projectile] = None
        if self.alive and self.auto_attack and self.state not in ("dead", "victory"):
            self.attack_timer += dt
            if self.attack_timer >= self.attack_cooldown:
                self.attack_timer = 0.0
                projectile = self.attack(ctx)
        return projectile

    def _update_motion(self) -> None:
        """Idle shake / sway, recoil, victory bounce and rotation."""
        r = self.size * 0.5
        if self.state == "victory":
            self.rotation = math.sin(self.t * 2.2) * 12.0
            self.offset_y = -abs(math.sin(self.t * 7.0)) * self.size * 0.12
            self.offset_x = math.sin(self.t * 1.4) * self.size * 0.04
            self.glow = 0.5 + 0.5 * math.sin(self.t * 6.0)
        elif self.state == "dead":
            self.offset_x = self.offset_y = 0.0
            self.rotation = 0.0
        elif self.side == "red":
            self.rotation = math.sin(self.t * 26.0) * 1.2
            self.offset_x = math.sin(self.t * 42.0) * 2.6 - self.facing * self.recoil * r * 0.16
            self.offset_y = math.sin(self.t * 2.7) * 2.5
            self.glow = 0.5 + 0.5 * math.sin(self.t * 5.0)
        else:
            self.rotation = math.sin(self.t * 1.7) * 3.0
            self.offset_x = math.sin(self.t * 1.7) * 7.0 + self.facing * self.recoil * r * 0.16
            self.offset_y = math.sin(self.t * 2.2) * 3.5
            self.glow = 0.5 + 0.5 * math.sin(self.t * 3.0 + 1.0)
        if self.hurt_timer > 0.0:
            self.offset_x += self.rng.uniform(-7.0, 7.0)

    def attack(self, ctx: "BattleContext") -> Optional[Projectile]:
        """Fire a projectile toward the opposite side."""
        if not self.alive:
            return None
        self.attack_anim = 0.35
        self.recoil = 1.0
        self.attack_timer = 0.0
        target_y = ctx.ground_y - 12.0

        if ctx.audio is not None:
            ctx.audio.play("fire" if self.side == "red" else "ice", 0.85)

        if self.side == "red":
            origin_x = self.x + self.size * 0.42
            ctx.particles.fire_explosion(origin_x, self.y + self.size * 0.12, 0.5)
            return Projectile(
                "fireball", "red", origin_x, target_y, speed=FIREBALL_SPEED
            )

        origin_x = self.x - self.size * 0.42
        ctx.particles.ice_shatter(origin_x, self.y, 0.6)
        return Projectile("ice_beam", "blue", origin_x, target_y, speed=ICE_BEAM_SPEED)

    def take_damage(self, amount: float, ctx: "BattleContext") -> None:
        """Apply damage with a hit flash (kept for future game modes)."""
        if not self.alive:
            return
        self.health = max(0.0, self.health - abs(amount))
        self.hurt_timer = 0.25
        ctx.particles.spark(self.x, self.y, 12)
        if self.health <= 0.0:
            self.die(ctx)

    def grow_to(self, size: float) -> None:
        """Animate the boss to a new size."""
        self.target_size = float(size)

    def start_victory(self) -> None:
        """Switch to the celebration animation."""
        self.state = "victory"
        self.auto_attack = False

    def die(self, ctx: "BattleContext") -> None:
        """Explode into a cloud of particles."""
        if not self.alive:
            return
        self.alive = False
        self.state = "dead"
        colors = (
            ((255, 200, 60), (255, 90, 30), (200, 30, 20), (255, 255, 220))
            if self.side == "red"
            else ((200, 245, 255), (110, 200, 255), (60, 120, 240), (255, 255, 255))
        )
        ctx.particles.shatter(self.x, self.y, 70, colors, power=1.2)
        ctx.particles.smoke(self.x, self.y, 18)
        if ctx.audio is not None:
            ctx.audio.play("explosion", 1.0)
            ctx.audio.play("mega_boom", 0.7, cooldown=0.2)

    # ------------------------------------------------------------------ #
    # Rendering
    # ------------------------------------------------------------------ #
    def draw(self, surface: pygame.Surface, ctx: "BattleContext") -> None:
        """Render the smiley (aura, face, state overlays) at its current pose."""
        if not self.alive or self.state == "dead":
            return
        size = int(max(8.0, self.size))
        canvas_size = int(size * 1.5)
        if self._tmp is None or self._tmp_size != canvas_size:
            self._tmp = pygame.Surface((canvas_size, canvas_size), pygame.SRCALPHA)
            self._tmp_size = canvas_size
        else:
            self._tmp.fill((0, 0, 0, 0))

        centre = canvas_size // 2
        self._draw_body(self._tmp, centre, centre, size, self.t)

        if abs(self.rotation) > 0.2:
            image = pygame.transform.rotozoom(self._tmp, self.rotation, 1.0)
        else:
            image = self._tmp

        cx, cy = self.center
        surface.blit(image, image.get_rect(center=(int(cx), int(cy))))

    def _draw_body(
        self, surface: pygame.Surface, cx: float, cy: float, size: float, t: float
    ) -> None:
        """Dispatch to the red or blue face renderer."""
        if self.side == "red":
            self._draw_red(surface, cx, cy, size, t)
        else:
            self._draw_blue(surface, cx, cy, size, t)

    # ------------------------------------------------------------------ #
    # Red boss
    # ------------------------------------------------------------------ #
    def _draw_red(self, surface: pygame.Surface, cx: float, cy: float, size: float, t: float) -> None:
        """Angry red smiley with burning eyes and a flaming mouth."""
        r = size * 0.5
        attacking = self.attack_anim > 0.0
        pulse = 0.5 + 0.5 * math.sin(t * 7.0)
        cx, cy = float(cx), float(cy)

        # Fiery aura.
        pygame.draw.circle(surface, (60, 6, 4), (int(cx), int(cy)), int(r * 1.18))
        pygame.draw.circle(
            surface, (110 + int(30 * pulse), 12, 8), (int(cx), int(cy)), int(r * 1.09)
        )
        if self.state == "victory":
            pygame.draw.circle(
                surface, (255, 205, 70), (int(cx), int(cy)), int(r * 1.16), max(3, int(r * 0.06))
            )

        # Body.
        pygame.draw.circle(surface, (150, 8, 4), (int(cx), int(cy)), int(r))
        pygame.draw.circle(surface, RED_PRIMARY, (int(cx - r * 0.02), int(cy - r * 0.04)), int(r * 0.95))
        pygame.draw.circle(surface, RED_BRIGHT, (int(cx - r * 0.18), int(cy - r * 0.22)), int(r * 0.62))
        pygame.draw.circle(surface, (110, 0, 0), (int(cx), int(cy)), int(r), max(2, int(r * 0.055)))

        # Eyes.
        eye_w = r * 0.40
        eye_h = r * 0.30 * (0.45 if attacking else 1.0)
        for sign in (-1, 1):
            rect = pygame.Rect(0, 0, int(eye_w), max(4, int(eye_h)))
            rect.center = (int(cx + sign * r * 0.33), int(cy - r * 0.15))
            pygame.draw.ellipse(surface, (255, 248, 235), rect)
            pupil = max(2, int(r * 0.085))
            pupil_x = rect.centerx + int(r * 0.05 * self.facing)
            pygame.draw.circle(surface, (25, 0, 0), (pupil_x, rect.centery), pupil)
            pygame.draw.circle(surface, (255, 255, 255), (pupil_x - 1, rect.centery - 2), max(1, pupil // 3))

            # Angry brows.
            inner = (cx + sign * r * 0.10, cy - r * 0.34)
            outer = (cx + sign * r * 0.60, cy - r * 0.50)
            pygame.draw.line(
                surface, (75, 0, 0), (int(inner[0]), int(inner[1])),
                (int(outer[0]), int(outer[1])), max(3, int(r * 0.10)),
            )

        # Mouth with fire.
        mouth_w = r * (1.05 if attacking else 0.82)
        mouth_h = r * (0.80 if attacking else 0.58)
        mouth = pygame.Rect(0, 0, int(mouth_w), int(mouth_h))
        mouth.center = (int(cx), int(cy + r * 0.44))
        pygame.draw.ellipse(surface, (50, 0, 0), mouth)
        flame_h = mouth_h * (0.55 + 0.2 * math.sin(t * 18.0))
        flame = pygame.Rect(0, 0, int(mouth_w * 0.7), max(3, int(flame_h)))
        flame.center = (int(cx), int(cy + r * 0.46))
        pygame.draw.ellipse(surface, (255, 110, 20), flame)
        flame_core = flame.inflate(-max(2, flame.width * 0.45), -max(2, flame.height * 0.45))
        pygame.draw.ellipse(surface, (255, 230, 120), flame_core)

        # Teeth.
        tooth_w = max(3, int(mouth_w / 6))
        for index in range(3):
            tx = mouth.x + int(mouth.width * (0.2 + index * 0.3))
            pygame.draw.polygon(
                surface,
                (255, 250, 240),
                [(tx, mouth.y + 1), (tx + tooth_w, mouth.y + 1), (tx + tooth_w // 2, mouth.y + max(4, mouth_h // 4))],
            )
            pygame.draw.polygon(
                surface,
                (255, 250, 240),
                [
                    (tx, mouth.bottom - 1),
                    (tx + tooth_w, mouth.bottom - 1),
                    (tx + tooth_w // 2, mouth.bottom - max(4, mouth_h // 4)),
                ],
            )

        # Attack flames spilling out of the mouth.
        if attacking:
            for index in range(3):
                offset = (index - 1) * mouth_w * 0.28
                height = mouth_h * (0.9 + 0.3 * math.sin(t * 24.0 + index))
                pygame.draw.polygon(
                    surface,
                    (255, 140, 30),
                    [
                        (int(cx + offset - mouth_w * 0.12), int(mouth.bottom - 2)),
                        (int(cx + offset + mouth_w * 0.12), int(mouth.bottom - 2)),
                        (int(cx + offset + self.facing * mouth_w * 0.1), int(mouth.bottom + height)),
                    ],
                )

        if self.hurt_timer > 0.0:
            overlay = pygame.Surface(surface.get_size(), pygame.SRCALPHA)
            pygame.draw.circle(
                overlay, (255, 255, 255, 130), (int(cx), int(cy)), int(r)
            )
            surface.blit(overlay, (0, 0))

    # ------------------------------------------------------------------ #
    # Blue boss
    # ------------------------------------------------------------------ #
    def _draw_blue(self, surface: pygame.Surface, cx: float, cy: float, size: float, t: float) -> None:
        """Cool blue smiley with sunglasses, a smirk and a freezing aura."""
        r = size * 0.5
        cx, cy = float(cx), float(cy)

        pygame.draw.circle(surface, (5, 22, 55), (int(cx), int(cy)), int(r * 1.18))
        pygame.draw.circle(surface, (10, 50, 120), (int(cx), int(cy)), int(r * 1.09))
        if self.state == "victory":
            pygame.draw.circle(
                surface, (255, 205, 70), (int(cx), int(cy)), int(r * 1.16), max(3, int(r * 0.06))
            )

        # Floating snowflakes.
        for index in range(5):
            angle = t * 0.9 + index * (math.tau / 5.0)
            sx = cx + math.cos(angle) * r * 1.24
            sy = cy + math.sin(angle) * r * 1.24
            arm = max(2.0, r * 0.07)
            for pair in range(3):
                theta = pair * math.pi / 3.0
                dx, dy = math.cos(theta) * arm, math.sin(theta) * arm
                pygame.draw.line(
                    surface, (180, 235, 255), (int(sx - dx), int(sy - dy)),
                    (int(sx + dx), int(sy + dy)), max(1, int(r * 0.015)),
                )

        # Body.
        pygame.draw.circle(surface, (8, 30, 95), (int(cx), int(cy)), int(r))
        pygame.draw.circle(surface, BLUE_PRIMARY, (int(cx - r * 0.02), int(cy - r * 0.04)), int(r * 0.95))
        pygame.draw.circle(surface, BLUE_BRIGHT, (int(cx - r * 0.20), int(cy - r * 0.24)), int(r * 0.58))
        pygame.draw.circle(surface, (10, 30, 90), (int(cx), int(cy)), int(r), max(2, int(r * 0.055)))

        # Sunglasses.
        lens_w = r * 0.54
        lens_h = r * 0.34
        lens_y = cy - r * 0.12
        for sign in (-1, 1):
            lens = pygame.Rect(0, 0, int(lens_w), max(6, int(lens_h)))
            lens.center = (int(cx + sign * r * 0.31), int(lens_y))
            pygame.draw.rect(surface, (8, 10, 20), lens, border_radius=max(2, int(r * 0.08)))
            pygame.draw.rect(
                surface, (90, 140, 210), lens, width=max(1, int(r * 0.02)),
                border_radius=max(2, int(r * 0.08)),
            )
            pygame.draw.line(
                surface, (210, 240, 255),
                (lens.x + max(2, int(lens_w * 0.15)), lens.bottom - max(2, int(lens_h * 0.2))),
                (lens.x + max(3, int(lens_w * 0.55)), lens.y + max(2, int(lens_h * 0.2))),
                max(2, int(r * 0.025)),
            )
            # Temple arm.
            pygame.draw.line(
                surface, (8, 10, 20),
                (lens.centerx + sign * lens.width // 2, lens.centery),
                (int(cx + sign * r * 0.66), int(lens_y - r * 0.02)),
                max(2, int(r * 0.04)),
            )
        pygame.draw.line(
            surface, (8, 10, 20),
            (int(cx - r * 0.08), int(lens_y)), (int(cx + r * 0.08), int(lens_y)),
            max(3, int(r * 0.06)),
        )

        # Confident smirk.
        smirk = pygame.Rect(0, 0, int(r * 0.72), int(r * 0.42))
        smirk.center = (int(cx + r * 0.04), int(cy + r * 0.42))
        pygame.draw.arc(
            surface, (10, 20, 55), smirk, math.pi * 1.06, math.pi * 1.94,
            max(3, int(r * 0.075)),
        )
        pygame.draw.circle(
            surface, (10, 20, 55),
            (int(smirk.right - r * 0.02), int(smirk.centery + r * 0.03)),
            max(2, int(r * 0.035)),
        )

        # Firing gesture: a glowing icy hand thrust toward the enemy.
        if self.attack_anim > 0.0:
            hand_x = cx - r * 0.95
            hand_y = cy + r * 0.18
            for factor, color in ((1.6, (40, 90, 200)), (1.1, (110, 200, 255)), (0.6, (235, 252, 255))):
                pygame.draw.circle(
                    surface, color, (int(hand_x), int(hand_y)), max(3, int(r * 0.14 * factor))
                )
            for index in range(3):
                angle = -0.5 + index * 0.5
                pygame.draw.line(
                    surface, (200, 245, 255),
                    (int(hand_x), int(hand_y)),
                    (int(hand_x - r * 0.35 * math.cos(angle)), int(hand_y + r * 0.35 * math.sin(angle))),
                    max(2, int(r * 0.02)),
                )

        if self.hurt_timer > 0.0:
            overlay = pygame.Surface(surface.get_size(), pygame.SRCALPHA)
            pygame.draw.circle(
                overlay, (255, 255, 255, 130), (int(cx), int(cy)), int(r)
            )
            surface.blit(overlay, (0, 0))

        if self.state == "victory":
            ring = max(2, int(r * 0.03))
            pygame.draw.circle(
                surface, WHITE, (int(cx), int(cy)),
                int(r * (1.05 + 0.03 * math.sin(t * 5.0))), ring,
            )
