"""The dancing gypsy: a hand-drawn figure animated on the wallpaper.

Everything is ``pygame.draw`` - polygons, thick lines and circles.  The pose is
driven by the beat of the wallpaper song: four step poses cycling every bar
(kick left, kick right, squat, spin) plus continuous arm waving, so the figure
never looks like it is stuck in a loop.
"""

from __future__ import annotations

import math
from typing import Tuple

import pygame

Color = Tuple[int, int, int]

SKIN: Color = (222, 168, 120)
SKIN_DARK: Color = (182, 128, 88)
SHIRT: Color = (245, 235, 215)
SHIRT_SHADE: Color = (206, 192, 168)
VEST: Color = (36, 26, 34)
VEST_EDGE: Color = (24, 16, 22)
GOLD: Color = (232, 186, 84)
SASH: Color = (188, 34, 42)
SASH_DARK: Color = (140, 20, 28)
TROUSERS: Color = (52, 44, 74)
TROUSERS_DARK: Color = (38, 32, 56)
BOOT: Color = (52, 36, 28)
HAT: Color = (28, 22, 26)
HAIR: Color = (24, 16, 14)


class Dancer:
    """A stylised, fully procedural gypsy dancer."""

    def __init__(self, rng=None) -> None:
        self.rng = rng

    # ------------------------------------------------------------------ #
    # Pose
    # ------------------------------------------------------------------ #
    @staticmethod
    def _ease(p: float) -> float:
        """Smooth 0 -> 1 -> 0 over one beat."""
        p = max(0.0, min(1.0, p))
        return math.sin(math.pi * p)

    def draw(
        self,
        surface: pygame.Surface,
        x: float,
        y: float,
        height: float,
        beat_index: int,
        phase: float,
        *,
        shadow: bool = True,
        alpha: int = 255,
    ) -> None:
        """Draw the dancer standing with the feet at ``(x, y)``.

        ``height`` is the full figure height; ``beat_index`` and ``phase``
        (0..1 inside the current beat) drive the choreography.
        """
        h = float(height)
        if h < 20:
            return
        pose = int(beat_index) % 4
        swing = self._ease(phase)
        cx = float(x)
        feet_y = float(y)

        # Continuous groove: subtle vertical bounce and side sway.
        bounce = -h * 0.018 * abs(math.sin(math.pi * phase))
        sway = math.sin(math.pi * 2.0 * (phase + pose * 0.25)) * h * 0.012
        squat = 0.0
        squash = 1.0
        if pose == 2:  # присядка
            squat = h * 0.10 * swing
        if pose == 3:  # spin: flatten the figure horizontally
            squash = 0.30 + 0.70 * abs(math.cos(math.pi * phase))

        cx += sway
        feet_y += bounce + squat

        def px(value: float) -> int:
            """Apply the spin squash around the vertical axis."""
            return int(cx + (value - cx) * squash)

        def py(value: float) -> int:
            return int(value)

        leg = 0.45 * h
        hip_y = feet_y - leg
        shoulder_y = feet_y - 0.745 * h
        head_r = h * 0.070
        head_cy = feet_y - 0.835 * h

        if shadow:
            shadow_w = int(h * 0.42 * (0.85 + 0.15 * squash))
            pygame.draw.ellipse(
                surface, (24, 6, 8),
                (int(cx - shadow_w / 2), int(feet_y - h * 0.018),
                 shadow_w, max(3, int(h * 0.05))),
            )

        # --- legs ---------------------------------------------------- #
        kick = 0.0
        if pose == 0:
            kick = swing
        elif pose == 1:
            kick = -swing

        for side in (-1, 1):
            is_kicking = (side < 0 and kick > 0) or (side > 0 and kick < 0)
            amount = abs(kick) if is_kicking else 0.0
            foot_x = cx + side * (0.075 + 0.20 * amount) * h
            foot_y = feet_y - 0.16 * h * amount
            knee_x = (cx + side * 0.06 * h + foot_x) * 0.5 + side * 0.035 * h
            knee_y = (hip_y + foot_y) * 0.5

            pygame.draw.line(
                surface, TROUSERS,
                (px(cx + side * 0.05 * h), py(hip_y)),
                (px(knee_x), py(knee_y)),
                max(3, int(h * 0.098)),
            )
            pygame.draw.line(
                surface, TROUSERS_DARK,
                (px(knee_x), py(knee_y)),
                (px(foot_x), py(foot_y - h * 0.02)),
                max(3, int(h * 0.088)),
            )
            # Boot.
            boot_w = max(4, int(h * 0.085))
            boot_h = max(3, int(h * 0.045))
            pygame.draw.ellipse(
                surface, BOOT,
                (px(foot_x) - boot_w // 2, py(foot_y) - boot_h // 2, boot_w, boot_h),
            )

        # --- torso ---------------------------------------------------- #
        waist_y = hip_y - 0.02 * h
        torso = [
            (px(cx - 0.125 * h), py(waist_y)),
            (px(cx + 0.125 * h), py(waist_y)),
            (px(cx + 0.155 * h), py(shoulder_y)),
            (px(cx - 0.155 * h), py(shoulder_y)),
        ]
        pygame.draw.polygon(surface, SHIRT, torso)
        # Shirt shading on the left.
        pygame.draw.polygon(
            surface, SHIRT_SHADE,
            [torso[0], (px(cx - 0.02 * h), py(waist_y)),
             (px(cx - 0.02 * h), py(shoulder_y)), torso[3]],
        )
        # Open vest.
        pygame.draw.polygon(
            surface, VEST,
            [
                (px(cx - 0.155 * h), py(shoulder_y)),
                (px(cx - 0.045 * h), py(shoulder_y)),
                (px(cx - 0.03 * h), py(waist_y + 0.02 * h)),
                (px(cx - 0.14 * h), py(waist_y)),
            ],
        )
        pygame.draw.polygon(
            surface, VEST,
            [
                (px(cx + 0.155 * h), py(shoulder_y)),
                (px(cx + 0.045 * h), py(shoulder_y)),
                (px(cx + 0.03 * h), py(waist_y + 0.02 * h)),
                (px(cx + 0.14 * h), py(waist_y)),
            ],
        )
        pygame.draw.line(
            surface, VEST_EDGE,
            (px(cx - 0.045 * h), py(shoulder_y)),
            (px(cx - 0.03 * h), py(waist_y)), max(1, int(h * 0.006)),
        )
        pygame.draw.line(
            surface, VEST_EDGE,
            (px(cx + 0.045 * h), py(shoulder_y)),
            (px(cx + 0.03 * h), py(waist_y)), max(1, int(h * 0.006)),
        )
        # Gold trim + buttons.
        for side in (-1, 1):
            pygame.draw.line(
                surface, GOLD,
                (px(cx + side * 0.11 * h), py(shoulder_y - 0.005 * h)),
                (px(cx + side * 0.105 * h), py(waist_y)),
                max(1, int(h * 0.005)),
            )
        for step in range(3):
            button_y = shoulder_y + (waist_y - shoulder_y) * (0.22 + 0.26 * step)
            pygame.draw.circle(
                surface, GOLD, (px(cx), py(button_y)), max(1, int(h * 0.008))
            )

        # --- sash ----------------------------------------------------- #
        sash_h = max(3, int(h * 0.045))
        pygame.draw.rect(
            surface, SASH,
            (px(cx - 0.13 * h), py(waist_y) - sash_h // 2,
             int(0.26 * h * squash), sash_h),
        )
        pygame.draw.rect(
            surface, SASH_DARK,
            (px(cx - 0.13 * h), py(waist_y) + sash_h // 2 - max(1, sash_h // 4),
             int(0.26 * h * squash), max(1, sash_h // 4)),
        )

        # --- arms ------------------------------------------------------ #
        if pose % 2 == 1:
            # Clapping overhead: both hands meet above the hat.
            lift = 0.55 + 0.45 * math.cos(math.pi * phase)
            left_angle = right_angle = math.radians(78 + 14 * lift)
        else:
            # Alternating raises, like a real dancer using the arms for rhythm.
            left_angle = math.radians(18 + 62 * abs(math.sin(math.pi * phase)))
            right_angle = math.radians(18 + 62 * abs(math.sin(math.pi * phase + math.pi / 2)))

        arm_len = 0.30 * h
        hands = []
        for side, angle in ((-1, left_angle), (1, right_angle)):
            shoulder = (cx + side * 0.145 * h, shoulder_y + 0.02 * h)
            # Mirror: the angle is measured from +x, y grows downwards.
            hand_x = shoulder[0] + math.cos(angle) * arm_len * (1 if side > 0 else -1)
            hand_y = shoulder[1] - math.sin(angle) * arm_len
            elbow_x = (shoulder[0] + hand_x) * 0.5 + side * 0.02 * h
            elbow_y = (shoulder[1] + hand_y) * 0.5 + 0.02 * h
            pygame.draw.circle(
                surface, SHIRT, (px(shoulder[0]), py(shoulder[1])),
                max(3, int(h * 0.038)),
            )
            pygame.draw.line(
                surface, SHIRT,
                (px(shoulder[0]), py(shoulder[1])),
                (px(elbow_x), py(elbow_y)),
                max(3, int(h * 0.065)),
            )
            pygame.draw.line(
                surface, SKIN,
                (px(elbow_x), py(elbow_y)),
                (px(hand_x), py(hand_y)),
                max(2, int(h * 0.05)),
            )
            pygame.draw.circle(
                surface, SKIN, (px(hand_x), py(hand_y)), max(2, int(h * 0.026))
            )
            hands.append((px(hand_x), py(hand_y)))

        # Clap spark when the hands meet.
        if pose % 2 == 1 and phase > 0.42 and phase < 0.58:
            mid = ((hands[0][0] + hands[1][0]) // 2, (hands[0][1] + hands[1][1]) // 2)
            for index in range(4):
                angle = index * math.pi / 2.0 + phase * 3.0
                length = h * 0.05
                pygame.draw.line(
                    surface, GOLD,
                    mid,
                    (int(mid[0] + math.cos(angle) * length),
                     int(mid[1] + math.sin(angle) * length)),
                    max(1, int(h * 0.008)),
                )

        # --- neck & head ------------------------------------------------ #
        pygame.draw.line(
            surface, SKIN_DARK,
            (px(cx), py(shoulder_y)),
            (px(cx), py(head_cy + head_r * 0.7)),
            max(2, int(h * 0.035)),
        )
        pygame.draw.circle(surface, SKIN, (px(cx), py(head_cy)), int(head_r))
        # Hair at the sides.
        pygame.draw.circle(
            surface, HAIR, (px(cx - head_r * 0.7), py(head_cy + head_r * 0.25)),
            max(2, int(head_r * 0.45)),
        )
        pygame.draw.circle(
            surface, HAIR, (px(cx + head_r * 0.7), py(head_cy + head_r * 0.25)),
            max(2, int(head_r * 0.45)),
        )
        # Eyes (cheerful squints).
        eye_y = head_cy - head_r * 0.18
        for side in (-1, 1):
            pygame.draw.arc(
                surface, (40, 26, 20),
                pygame.Rect(
                    px(cx + side * head_r * 0.42) - int(head_r * 0.20),
                    py(eye_y) - int(head_r * 0.18),
                    int(head_r * 0.40), int(head_r * 0.36),
                ),
                3.5, 6.1, max(1, int(h * 0.008)),
            )
        # Moustache: two curled halves.
        for side in (-1, 1):
            moustache = pygame.Rect(
                0, 0, int(head_r * 0.62), max(2, int(head_r * 0.22))
            )
            moustache.center = (
                px(cx + side * head_r * 0.30),
                py(head_cy + head_r * 0.30),
            )
            pygame.draw.ellipse(surface, HAIR, moustache)
        # Smile.
        pygame.draw.arc(
            surface, (120, 40, 34),
            pygame.Rect(
                px(cx) - int(head_r * 0.42), py(head_cy + head_r * 0.30),
                int(head_r * 0.84), int(head_r * 0.55),
            ),
            3.4, 5.9, max(1, int(h * 0.008)),
        )

        # --- hat -------------------------------------------------------- #
        brim_w = int(head_r * 2.7 * squash)
        brim_h = max(3, int(head_r * 0.55))
        pygame.draw.ellipse(
            surface, HAT,
            (px(cx) - brim_w // 2, py(head_cy - head_r * 0.72), brim_w, brim_h),
        )
        crown_w = max(4, int(head_r * 1.5 * squash))
        crown_h = int(head_r * 1.35)
        pygame.draw.polygon(
            surface, HAT,
            [
                (px(cx) - crown_w // 2, py(head_cy - head_r * 0.7)),
                (px(cx) + crown_w // 2, py(head_cy - head_r * 0.7)),
                (px(cx) + crown_w // 3, py(head_cy - head_r * 0.7 - crown_h)),
                (px(cx) - crown_w // 3, py(head_cy - head_r * 0.7 - crown_h)),
            ],
        )
        pygame.draw.rect(
            surface, SASH,
            (px(cx) - crown_w // 2, py(head_cy - head_r * 0.98),
             crown_w, max(2, int(head_r * 0.18))),
        )
