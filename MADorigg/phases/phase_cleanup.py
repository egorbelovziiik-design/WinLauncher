"""Phase 5: fade out, restore the system and leave a calling card."""

from __future__ import annotations

import logging

import pygame

import config
from battle.battle_manager import BattleContext

LOG = logging.getLogger(__name__)


def _draw_devil(surface: pygame.Surface, center: tuple[int, int], size: int, alpha: int) -> None:
    """Draw a tiny devil face (pygame has no colour emoji fonts)."""
    if alpha <= 0 or size < 4:
        return
    canvas = pygame.Surface((size * 3, size * 3), pygame.SRCALPHA)
    cx, cy = canvas.get_width() // 2, canvas.get_height() // 2
    r = size

    # Horns.
    for sign in (-1, 1):
        pygame.draw.polygon(
            canvas,
            (190, 30, 30),
            [
                (cx + sign * int(r * 0.35), cy - int(r * 0.55)),
                (cx + sign * int(r * 0.85), cy - int(r * 1.35)),
                (cx + sign * int(r * 0.95), cy - int(r * 0.35)),
            ],
        )
    # Face.
    pygame.draw.circle(canvas, (225, 45, 40), (cx, cy), r)
    pygame.draw.circle(canvas, (120, 10, 10), (cx, cy), r, max(1, size // 12))
    # Eyes.
    for sign in (-1, 1):
        pygame.draw.circle(
            canvas, (255, 240, 140), (cx + sign * r // 3, cy - r // 4), max(2, r // 4)
        )
        pygame.draw.circle(
            canvas, (30, 0, 0), (cx + sign * r // 3, cy - r // 4), max(1, r // 9)
        )
    # Grin.
    pygame.draw.arc(
        canvas,
        (60, 0, 0),
        pygame.Rect(cx - r // 2, cy, r, max(4, r // 2)),
        3.4,
        6.0,
        max(1, size // 10),
    )
    canvas.set_alpha(alpha)
    surface.blit(canvas, canvas.get_rect(center=center))


def run(ctx: BattleContext) -> None:
    """Fade to black, restore audio/volume/cursor and show the watermark."""
    duration = config.PHASE_CLEANUP_DURATION
    start = ctx.elapsed

    snapshot = ctx.canvas.copy()
    ctx.particles.clear()
    ctx.glitch.duration = 0.0

    audio_fading = False
    volume_restored = False
    cursor_restored = False
    watermark_size = ctx.fs(54)

    try:
        while True:
            t = ctx.elapsed - start
            if t >= duration:
                break
            dt = ctx.begin_frame()
            t = ctx.elapsed - start

            # --- fade the music and restore the OS volume ---------------- #
            if not audio_fading and t >= 0.2:
                audio_fading = True
                if ctx.audio is not None:
                    ctx.audio.fade_master(0.0, 1.5)
                    ctx.audio.stop_music(fade_ms=1500)

            if not volume_restored and t >= 0.3 and ctx.volume is not None:
                volume_restored = True
                saved = ctx.volume.saved_volume
                if saved is not None:
                    LOG.info("Restoring system volume to %.2f", saved)
                    ctx.volume.fade_to(saved, config.VOLUME_FADE_SECONDS)
                else:
                    ctx.volume.restore_volume()

            if not cursor_restored and t >= 0.7:
                cursor_restored = True
                ctx.screen.set_cursor_visible(True)

            # --- visual fade to black ------------------------------------ #
            ctx.canvas.blit(snapshot, (0, 0))
            fade = min(1.0, t / 1.0)
            if fade > 0.0:
                shade = pygame.Surface((ctx.width, ctx.height)).convert()
                shade.fill(config.BLACK)
                shade.set_alpha(int(255 * fade))
                ctx.canvas.blit(shade, (0, 0))

            # --- calling card -------------------------------------------- #
            if t >= 1.0:
                ctx.canvas.fill(config.BLACK)
                local = t - 1.0
                visible_for = duration - 1.0
                alpha_in = min(1.0, local / 0.45)
                alpha_out = min(1.0, max(0.0, (visible_for - local) / 0.7))
                alpha = int(255 * min(alpha_in, alpha_out))
                if alpha > 0:
                    image = ctx.text.render(
                        config.WATERMARK_TEXT, watermark_size, (225, 225, 230)
                    ).copy()
                    image.set_alpha(alpha)
                    rect = image.get_rect(center=(ctx.width // 2, ctx.height // 2))
                    ctx.canvas.blit(image, rect)
                    _draw_devil(
                        ctx.canvas,
                        (rect.right + ctx.fs(30), rect.centery - ctx.fs(4)),
                        ctx.fs(20),
                        alpha,
                    )

                    hint = ctx.text.render(
                        "until next time...", ctx.fs(22), (110, 110, 120)
                    ).copy()
                    hint.set_alpha(alpha)
                    hint_rect = hint.get_rect(
                        center=(ctx.width // 2, ctx.height // 2 + ctx.fs(56))
                    )
                    ctx.canvas.blit(hint, hint_rect)

            ctx.present()

    except Exception as exc:  # pragma: no cover - cleanup must never explode
        LOG.warning("Cleanup interrupted: %s", exc)

    finally:
        # Belt and braces: the caller (main.py) repeats this in its finally block.
        try:
            ctx.screen.set_cursor_visible(True)
        except Exception:
            pass
        if ctx.volume is not None:
            try:
                ctx.volume.restore_volume()
            except Exception as exc:  # pragma: no cover - defensive
                LOG.warning("Volume restore failed: %s", exc)
        if ctx.audio is not None:
            try:
                ctx.audio.stop_music(fade_ms=0)
            except Exception:
                pass
        ctx.canvas.fill(config.BLACK)
        ctx.present()
