"""Headless smoke test + preview montage builder.

Runs the complete show with the dummy SDL driver (no window, no sound, no
system volume changes), verifies that every phase produced frames, and stitches
a preview image for the docs.

Usage
-----
    python tools/smoke_test.py                 # run + docs/preview.png
    python tools/smoke_test.py --keep-frames   # keep the captured PNGs
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import tempfile
from pathlib import Path

os.environ.setdefault("PYGAME_HIDE_SUPPORT_PROMPT", "1")
os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
os.environ.setdefault("SDL_AUDIODRIVER", "dummy")

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import pygame  # noqa: E402  (after env setup)

import main as app  # noqa: E402

PICKS = (
    ("gypsy", 0.15, "WALLPAPER DANCE"),
    ("gypsy", 0.45, "FLYING WINDOWS"),
    ("gypsy", 0.80, "DANCE / OSD"),
    ("death", 0.25, "FAKE BSOD"),
    ("death", 0.85, "FAKE BSOD / 100%"),
    ("cleanup", 0.55, "CLEANUP"),
)


def _group_by_phase(frames: list[Path]) -> dict[str, list[Path]]:
    """Group captured frames by the phase encoded in their file name."""
    groups: dict[str, list[Path]] = {}
    for frame in frames:
        phase = frame.stem.rsplit("_", 1)[-1]
        groups.setdefault(phase, []).append(frame)
    return groups


def build_montage(frames: list[Path], output: Path, columns: int = 3) -> None:
    """Stitch one representative frame per phase into a preview image."""
    if not frames:
        raise SystemExit("No frames were captured - the show did not render.")

    groups = _group_by_phase(frames)
    pygame.font.init()
    tile_w, tile_h = 480, 270
    caption_h = 34
    rows = (len(PICKS) + columns - 1) // columns
    montage = pygame.Surface((tile_w * columns, (tile_h + caption_h) * rows))
    montage.fill((10, 10, 14))
    font = pygame.font.Font(None, 26)

    for index, (phase, fraction, label) in enumerate(PICKS):
        pool = groups.get(phase) or frames
        frame = pool[min(len(pool) - 1, max(0, int(len(pool) * fraction)))]
        image = pygame.transform.smoothscale(
            pygame.image.load(str(frame)), (tile_w, tile_h)
        )
        col = index % columns
        row = index // columns
        x = col * tile_w
        y = row * (tile_h + caption_h)
        montage.blit(image, (x, y))
        pygame.draw.rect(montage, (60, 60, 70), (x, y, tile_w, tile_h), 1)
        text = font.render(f"{label}", True, (225, 225, 235))
        montage.blit(text, (x + 8, y + tile_h + 8))
        small = font.render(frame.name, True, (120, 120, 135))
        montage.blit(small, (x + 8 + text.get_width() + 10, y + tile_h + 8))

    output.parent.mkdir(parents=True, exist_ok=True)
    pygame.image.save(montage, str(output))
    print(f"preview written to {output}")


def main(argv: list[str] | None = None) -> int:
    """Run the show headless and build the preview montage."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--time-scale", type=float, default=20.0)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--keep-frames", action="store_true")
    parser.add_argument("--frames-dir", default=None)
    parser.add_argument("--output", default=str(ROOT / "docs" / "preview.png"))
    args = parser.parse_args(argv)

    frames_dir = Path(args.frames_dir) if args.frames_dir else Path(tempfile.mkdtemp(prefix="madorigg_"))
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    code = app.main(
        [
            "--windowed",
            "--no-audio",
            "--no-volume",
            "--time-scale",
            str(args.time_scale),
            "--seed",
            str(args.seed),
            "--capture",
            str(frames_dir),
            "--capture-interval",
            "0.35",
            "--log-level",
            "WARNING",
        ]
    )
    frames = sorted(frames_dir.glob("frame_*.png"))
    print(f"captured {len(frames)} frames, exit code {code}")
    if len(frames) < 10:
        raise SystemExit("Too few frames captured - rendering probably failed.")

    build_montage(frames, Path(args.output))
    if not args.keep_frames:
        shutil.rmtree(frames_dir, ignore_errors=True)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
