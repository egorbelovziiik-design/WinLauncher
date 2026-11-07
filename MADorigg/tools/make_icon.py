"""Generate ``assets/icon.ico`` for the Windows build (no Pillow required).

Windows Vista+ accepts a PNG-compressed icon, so the script renders a 256x256
PNG with pygame and wraps it in a minimal ICO container by hand.
"""

from __future__ import annotations

import os
import struct
import tempfile
from pathlib import Path

os.environ.setdefault("PYGAME_HIDE_SUPPORT_PROMPT", "1")
os.environ.setdefault("SDL_VIDEODRIVER", "dummy")

import pygame  # noqa: E402  (after env setup)

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "assets"


def draw_icon(size: int = 256) -> pygame.Surface:
    """Render the split-screen smiley icon."""
    pygame.display.init()
    pygame.display.set_mode((1, 1))
    surface = pygame.Surface((size, size), pygame.SRCALPHA)
    surface.fill((0, 0, 0, 0))

    half = size // 2
    radius = int(size * 0.16)
    pygame.draw.rect(surface, (120, 0, 10), (0, 0, half, size), border_top_left_radius=radius)
    pygame.draw.rect(
        surface, (10, 30, 120), (half, 0, size - half, size),
        border_top_right_radius=radius,
    )
    pygame.draw.rect(surface, (255, 80, 60), (0, 0, half, size), 3)
    pygame.draw.rect(surface, (90, 160, 255), (half, 0, size - half, size), 3)

    # Lightning seam.
    mid = half
    pygame.draw.lines(
        surface,
        (255, 235, 150),
        False,
        [
            (mid - 6, 12), (mid + 6, size * 0.28), (mid - 4, size * 0.45),
            (mid + 7, size * 0.62), (mid - 5, size * 0.8), (mid + 3, size - 12),
        ],
        5,
    )

    face = int(size * 0.25)
    red_center = (int(size * 0.27), int(size * 0.55))
    blue_center = (int(size * 0.73), int(size * 0.55))

    pygame.draw.circle(surface, (255, 40, 30), red_center, face)
    pygame.draw.circle(surface, (120, 0, 0), red_center, face, 4)
    eye_r = max(3, face // 6)
    for sign in (-1, 1):
        pygame.draw.circle(
            surface, (255, 250, 240),
            (red_center[0] + sign * face // 3, red_center[1] - face // 4), eye_r,
        )
        pygame.draw.circle(
            surface, (20, 0, 0),
            (red_center[0] + sign * face // 3, red_center[1] - face // 4), eye_r // 2,
        )
        pygame.draw.line(
            surface, (70, 0, 0),
            (red_center[0] + sign * face // 6, red_center[1] - face // 2),
            (red_center[0] + sign * face * 0.55, red_center[1] - face * 0.68),
            max(3, face // 10),
        )
    mouth = pygame.Rect(0, 0, int(face * 0.9), int(face * 0.55))
    mouth.center = (red_center[0], red_center[1] + face // 3)
    pygame.draw.ellipse(surface, (60, 0, 0), mouth)
    pygame.draw.ellipse(surface, (255, 150, 30), mouth.inflate(-face // 3, -face // 3))

    pygame.draw.circle(surface, (20, 90, 230), blue_center, face)
    pygame.draw.circle(surface, (5, 30, 90), blue_center, face, 4)
    lens_w, lens_h = int(face * 0.52), int(face * 0.34)
    lens_y = blue_center[1] - face // 5
    for sign in (-1, 1):
        lens = pygame.Rect(0, 0, lens_w, lens_h)
        lens.center = (blue_center[0] + sign * face // 3, lens_y)
        pygame.draw.rect(surface, (10, 10, 25), lens, border_radius=6)
        pygame.draw.line(
            surface, (200, 235, 255),
            (lens.x + 6, lens.bottom - 6), (lens.right - 10, lens.y + 6), 3,
        )
    smirk = pygame.Rect(0, 0, int(face * 1.0), int(face * 0.5))
    smirk.center = (blue_center[0], blue_center[1] + face // 3)
    pygame.draw.arc(surface, (5, 20, 60), smirk, 3.3, 6.0, max(3, face // 10))

    pygame.draw.rect(surface, (0, 0, 0), (0, 0, size, size), 6, border_radius=radius)
    return surface


def write_ico(png_path: Path, ico_path: Path) -> None:
    """Wrap a PNG file into a single-image ICO container."""
    payload = png_path.read_bytes()
    header = struct.pack("<HHH", 0, 1, 1)  # reserved, type=icon, count=1
    entry = struct.pack(
        "<BBBBHHII",
        0, 0, 0, 0,  # width=256, height=256, palette, reserved
        1, 32,      # colour planes, bits per pixel
        len(payload),
        22,         # offset of the PNG data
    )
    ico_path.write_bytes(header + entry + payload)


def main() -> int:
    """Render the icon and save ``assets/icon.ico`` (+ a PNG preview)."""
    ASSETS.mkdir(parents=True, exist_ok=True)
    icon = draw_icon(256)
    png_path = ASSETS / "icon.png"
    ico_path = ASSETS / "icon.ico"
    pygame.image.save(icon, str(png_path))
    with tempfile.TemporaryDirectory() as tmp:
        tmp_png = Path(tmp) / "icon.png"
        tmp_png.write_bytes(png_path.read_bytes())
        write_ico(tmp_png, ico_path)
    print(f"icon written to {ico_path} ({ico_path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
