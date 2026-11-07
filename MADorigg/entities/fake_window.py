"""Fake application windows that fly around the wallpaper and refuse to close.

Each window is a little media player: a miniature of the dancing man on the
left, the track name and a live equaliser on the right, plus a progress bar
tied to the wallpaper song.  Pressing the close button only makes the window
bounce away with a cheeky note - nothing ever closes.
"""

from __future__ import annotations

import math
import random
from typing import TYPE_CHECKING, Optional, Tuple

import pygame

from .dancer import Dancer

if TYPE_CHECKING:  # pragma: no cover - typing only
    from battle.battle_manager import BattleContext

Color = Tuple[int, int, int]

CHROME: Color = (238, 238, 242)
CHROME_SHADE: Color = (206, 206, 214)
CHROME_LINE: Color = (176, 176, 186)
BODY: Color = (26, 26, 32)
BODY_LIGHT: Color = (44, 44, 52)
TITLE_TEXT: Color = (34, 34, 40)


class FakeWindow:
    """A draggable-looking app window with its own velocity."""

    TITLES: Tuple[str, ...] = (
        "Эк дрын тын опа.mp4",
        "Пляски цыган HD",
        "winamp 2.95",
        "YouTube — Эк дрын тын опа",
    )

    def __init__(
        self,
        title: str,
        rect: Tuple[int, int, int, int],
        velocity: Tuple[float, float],
        accent: Color,
        rng: Optional[random.Random] = None,
    ) -> None:
        self.title = title
        self.x, self.y, self.w, self.h = map(float, rect)
        self.vx, self.vy = velocity
        self.accent = accent
        self.rng = rng or random.Random()
        self.dancer = Dancer(rng)
        self.shake = 0.0
        self.note_timer = 0.0
        self.bob_phase = self.rng.uniform(0.0, math.tau)
        self.spawn_flash = 0.0

    # ------------------------------------------------------------------ #
    # Geometry
    # ------------------------------------------------------------------ #
    @property
    def rect(self) -> pygame.Rect:
        """Current window rectangle in screen coordinates."""
        return pygame.Rect(int(self.x), int(self.y), int(self.w), int(self.h))

    def _close_rect(self) -> pygame.Rect:
        size = max(10, int(self.h * 0.055))
        return pygame.Rect(
            int(self.x + self.w - size - size // 2),
            int(self.y + size // 2),
            size,
            size,
        )

    def close_hit(self, pos: Tuple[int, int]) -> bool:
        """True if the point lands on the close button."""
        return self._close_rect().collidepoint(pos)

    # ------------------------------------------------------------------ #
    # Simulation
    # ------------------------------------------------------------------ #
    def update(
        self,
        dt: float,
        bounds: Tuple[int, int],
        t: float,
        avoid: Optional[pygame.Rect] = None,
    ) -> None:
        """Move, bounce off the screen edges and age the effects.

        ``avoid`` is the dancer's stage: windows treat it as a wall so the
        centrepiece of the wallpaper is never hidden for long.
        """
        width, height = bounds
        self.x += self.vx * dt
        self.y += self.vy * dt

        margin_x = self.w * 0.08
        margin_top = height * 0.10
        margin_bottom = height * 0.04
        if self.x < margin_x:
            self.x = margin_x
            self.vx = abs(self.vx)
        elif self.x + self.w > width - margin_x:
            self.x = width - margin_x - self.w
            self.vx = -abs(self.vx)
        if self.y < margin_top:
            self.y = margin_top
            self.vy = abs(self.vy)
        elif self.y + self.h > height - margin_bottom:
            self.y = height - margin_bottom - self.h
            self.vy = -abs(self.vy)

        if avoid is not None:
            box = self.rect
            if box.colliderect(avoid):
                left = box.right - avoid.left
                right = avoid.right - box.left
                top = box.bottom - avoid.top
                bottom = avoid.bottom - box.top
                smallest = min(left, right, top, bottom)
                if smallest == left:
                    self.x = avoid.left - self.w - 2
                    self.vx = -abs(self.vx)
                elif smallest == right:
                    self.x = avoid.right + 2
                    self.vx = abs(self.vx)
                elif smallest == top:
                    self.y = avoid.top - self.h - 2
                    self.vy = -abs(self.vy)
                else:
                    self.y = avoid.bottom + 2
                    self.vy = abs(self.vy)
                self.x = max(self.w * 0.05, min(width - self.w * 1.05, self.x))
                self.y = max(height * 0.09, min(height - self.h * 1.05, self.y))

        self.shake = max(0.0, self.shake - dt * 3.0)
        self.note_timer = max(0.0, self.note_timer - dt)
        self.spawn_flash = max(0.0, self.spawn_flash - dt * 2.5)
        self.bob_phase += dt * 1.6

    def refuse(self) -> None:
        """React to a click on the close button: bounce away and gloat."""
        self.shake = 1.0
        self.note_timer = 1.4
        self.vx *= -1.25
        self.vy *= -1.0
        if abs(self.vx) < 120:
            self.vx = 120 if self.vx >= 0 else -120
        if abs(self.vy) < 60:
            self.vy = 60 if self.vy >= 0 else -60

    # ------------------------------------------------------------------ #
    # Drawing
    # ------------------------------------------------------------------ #
    def draw(
        self,
        surface: pygame.Surface,
        ctx: "BattleContext",
        t: float,
        beat_index: int,
        beat_phase: float,
        progress: float,
    ) -> None:
        """Render the whole window (chrome, mini dancer, player UI)."""
        shake_x = int(math.sin(t * 60.0) * self.shake * 6)
        rect = self.rect.move(shake_x, int(math.sin(self.bob_phase) * 2))

        # Drop shadow.
        shadow = pygame.Surface((rect.w + 24, rect.h + 24), pygame.SRCALPHA)
        pygame.draw.rect(
            shadow, (0, 0, 0, 90), shadow.get_rect(), border_radius=ctx.fs(10)
        )
        surface.blit(shadow, (rect.x - 6, rect.y - 2))

        # Window body.
        pygame.draw.rect(surface, CHROME, rect, border_radius=ctx.fs(9))
        bar_h = max(18, int(rect.h * 0.11))
        bar = pygame.Rect(rect.x, rect.y, rect.w, bar_h)
        pygame.draw.rect(
            surface, CHROME_SHADE, bar,
            border_top_left_radius=ctx.fs(9), border_top_right_radius=ctx.fs(9),
        )
        pygame.draw.line(
            surface, CHROME_LINE, (rect.x, rect.y + bar_h), (rect.right, rect.y + bar_h)
        )

        # Title bar icon + text.
        icon_size = max(8, bar_h - ctx.fs(8))
        icon = pygame.Rect(rect.x + ctx.fs(7), bar.y + (bar_h - icon_size) // 2,
                           icon_size, icon_size)
        pygame.draw.rect(surface, self.accent, icon, border_radius=ctx.fs(3))
        pygame.draw.polygon(
            surface, (255, 255, 255),
            [
                (icon.x + icon_size * 0.38, icon.y + icon_size * 0.28),
                (icon.x + icon_size * 0.38, icon.y + icon_size * 0.72),
                (icon.x + icon_size * 0.72, icon.y + icon_size * 0.5),
            ],
        )
        title_size = max(9, int(bar_h * 0.52))
        available = rect.right - ctx.fs(84) - icon.right
        title = self.title
        while title and ctx.text.render(title, title_size, TITLE_TEXT).get_width() > available:
            title = title[:-2]
            if title and not title.endswith("…"):
                title += "…"
        ctx.text.draw(
            surface, title, title_size,
            (icon.right + ctx.fs(6), bar.centery),
            TITLE_TEXT, anchor="midleft", shadow=False,
        )

        # Window buttons.
        btn_size = max(9, int(bar_h * 0.5))
        for index, kind in enumerate(("min", "max", "close")):
            bx = rect.right - ctx.fs(8) - (3 - index) * (btn_size + ctx.fs(6))
            by = bar.centery - btn_size // 2
            box = pygame.Rect(bx, by, btn_size, btn_size)
            if kind == "close":
                pygame.draw.rect(surface, (214, 60, 60), box, border_radius=ctx.fs(3))
                pad = max(2, btn_size // 4)
                pygame.draw.line(surface, (255, 255, 255),
                                 (box.x + pad, box.y + pad),
                                 (box.right - pad, box.bottom - pad), 2)
                pygame.draw.line(surface, (255, 255, 255),
                                 (box.right - pad, box.y + pad),
                                 (box.x + pad, box.bottom - pad), 2)
            else:
                pygame.draw.rect(surface, (150, 150, 160), box, 1, border_radius=ctx.fs(3))
                if kind == "min":
                    pygame.draw.line(surface, (90, 90, 100),
                                     (box.x + 3, box.centery),
                                     (box.right - 3, box.centery), 2)
                else:
                    pygame.draw.rect(surface, (90, 90, 100),
                                     box.inflate(-6, -6), 1)

        # Video area.
        pad = max(4, ctx.fs(6))
        video = pygame.Rect(
            rect.x + pad, rect.y + bar_h + pad, rect.w - pad * 2, rect.h - bar_h - pad * 2
        )
        pygame.draw.rect(surface, BODY, video, border_radius=ctx.fs(4))

        # Left half: the dancing man.
        dancer_w = int(video.w * 0.42)
        stage = pygame.Rect(video.x, video.y, dancer_w, video.h)
        pygame.draw.rect(surface, (40, 14, 16), stage,
                         border_top_left_radius=ctx.fs(4),
                         border_bottom_left_radius=ctx.fs(4))
        glow = pygame.Surface((stage.w, stage.h), pygame.SRCALPHA)
        for step in range(8, 0, -1):
            pygame.draw.circle(
                glow, (255, 170, 90, 10 + step * 2),
                (stage.centerx, int(stage.y + stage.h * 0.62)),
                int(stage.w * 0.1 + step * stage.w * 0.045),
            )
        surface.blit(glow, stage.topleft)
        pygame.draw.ellipse(
            surface, (74, 26, 26),
            (stage.centerx - stage.w * 0.22, stage.bottom - stage.h * 0.10,
             stage.w * 0.44, stage.h * 0.05),
        )
        self.dancer.draw(
            surface, stage.centerx, stage.bottom - stage.h * 0.08,
            stage.h * 0.86, beat_index, beat_phase,
        )

        # Right half: track info + equaliser + progress.
        info_x = stage.right + ctx.fs(8)
        info_w = video.right - info_x - ctx.fs(6)
        text_size = max(9, int(video.h * 0.09))
        ctx.text.draw(
            surface, "Эк дрын тын опа", text_size,
            (info_x, video.y + ctx.fs(6)), (245, 245, 250), anchor="topleft", shadow=False,
        )
        ctx.text.draw(
            surface, "ай дигидиги ай", max(8, int(text_size * 0.75)),
            (info_x, video.y + ctx.fs(6) + text_size + ctx.fs(2)),
            (170, 170, 180), anchor="topleft", shadow=False,
        )

        # Equaliser.
        track_y = video.bottom - ctx.fs(14)
        bars = max(6, min(16, int(info_w / ctx.fs(6))))
        bar_w = max(2, int(info_w / (bars * 1.55)))
        base_y = track_y - ctx.fs(8)
        max_len = int((base_y - video.y - ctx.fs(30)) * 0.72)
        for index in range(bars):
            level = 0.22 + 0.78 * abs(
                math.sin(t * (5.0 + index * 0.6) + index * 0.9)
            ) * (0.55 + 0.45 * (1.0 - beat_phase))
            bar_h = max(2, int(max_len * level))
            x = info_x + index * (bar_w + max(1, bar_w // 2))
            if x + bar_w > video.right - ctx.fs(4):
                break
            color = (
                int(self.accent[0] * (0.6 + 0.4 * level)),
                int(self.accent[1] * (0.6 + 0.4 * level)),
                int(self.accent[2] * (0.6 + 0.4 * level)),
            )
            pygame.draw.rect(surface, color, (x, base_y - bar_h, bar_w, bar_h))

        # Progress bar.
        track = pygame.Rect(info_x, track_y, info_w, max(3, ctx.fs(4)))
        pygame.draw.rect(surface, BODY_LIGHT, track, border_radius=track.h)
        fill = track.copy()
        fill.width = int(track.w * max(0.0, min(1.0, progress)))
        if fill.width > 1:
            pygame.draw.rect(surface, self.accent, fill, border_radius=track.h)
        seconds = int(progress * 20.0)
        ctx.text.draw(
            surface, f"0:{seconds:02d} / 0:20", max(7, int(text_size * 0.7)),
            (info_x, track.y - ctx.fs(14)), (150, 150, 160),
            anchor="topleft", shadow=False,
        )

        # Gloating bubble after a close click.
        if self.note_timer > 0.0:
            label = ctx.text.render("не закроешь :)", max(9, int(bar_h * 0.5)), (255, 240, 210))
            bubble = pygame.Surface(
                (label.get_width() + ctx.fs(14), label.get_height() + ctx.fs(8)),
                pygame.SRCALPHA,
            )
            pygame.draw.rect(
                bubble, (28, 12, 14, 225), bubble.get_rect(), border_radius=ctx.fs(6)
            )
            pygame.draw.rect(
                bubble, (232, 186, 84, 230), bubble.get_rect(), width=1,
                border_radius=ctx.fs(6),
            )
            bubble.blit(label, (ctx.fs(7), ctx.fs(4)))
            bubble.set_alpha(int(255 * min(1.0, self.note_timer / 0.4)))
            surface.blit(bubble, (rect.x + rect.w // 2 - bubble.get_width() // 2,
                                  rect.y - bubble.get_height() - ctx.fs(6)))
