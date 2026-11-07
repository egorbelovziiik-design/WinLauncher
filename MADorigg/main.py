"""MADorigg - entry point.

The show is a 35-40 second prank: fullscreen, topmost, system volume to 100 %,
procedurally generated epic music and two smileys fighting for the screen.
Everything is restored in the ``finally`` block, even if the show crashes.

Usage
-----
    python main.py                  # the real deal
    python main.py --windowed       # windowed (handy while developing)
    python main.py --no-audio --no-volume --time-scale 20    # fast dry run
"""

from __future__ import annotations

import os

os.environ.setdefault("PYGAME_HIDE_SUPPORT_PROMPT", "1")

import argparse
import logging
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Optional, Sequence

import pygame

import config
from audio.music_player import MusicPlayer
from audio.volume_control import VolumeControl
from battle.battle_manager import BattleContext, BattleManager, ScenarioAbort
from visuals.screen import Screen

LOG = logging.getLogger("madorigg")


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    """Parse command line switches."""
    parser = argparse.ArgumentParser(
        prog="MADorigg",
        description="Fullscreen prank show: two smileys fight for your screen.",
    )
    parser.add_argument("--windowed", action="store_true", help="run in a window instead of fullscreen")
    parser.add_argument("--no-audio", action="store_true", help="disable all sound (mixer is not opened)")
    parser.add_argument("--no-volume", action="store_true", help="never touch the system volume")
    parser.add_argument(
        "--time-scale", type=float, default=1.0,
        help="speed multiplier for the whole show (20 = fast dry run)",
    )
    parser.add_argument("--seed", type=int, default=None, help="deterministic random seed")
    parser.add_argument("--self-delete", action="store_true", help="delete the .exe after the show")
    parser.add_argument("--capture", metavar="DIR", default=None, help="save PNG frames to DIR")
    parser.add_argument(
        "--capture-interval", type=float, default=0.4,
        help="seconds of show time between captured frames",
    )
    parser.add_argument("--log-level", default="INFO", help="DEBUG / INFO / WARNING / ERROR")
    parser.add_argument("--version", action="version", version=f"{config.APP_NAME} {config.APP_VERSION}")
    return parser.parse_args(argv)


def schedule_self_delete() -> None:
    """Remove the frozen executable two seconds after exit (Windows only).

    Uses a detached ``cmd`` script so the file is unlocked before deletion.
    Disabled for source runs - we never delete ``main.py``.
    """
    if not getattr(sys, "frozen", False):
        LOG.info("--self-delete ignored: not running as a frozen executable")
        return
    if sys.platform != "win32":
        LOG.info("--self-delete is Windows-only")
        return
    target = Path(sys.executable).resolve()
    script = Path(tempfile.gettempdir()) / "madorigg_cleanup.bat"
    try:
        script.write_text(
            "@echo off\r\n"
            "timeout /t 2 /nobreak >nul\r\n"
            f'del /f /q "{target}"\r\n'
            '(goto) 2>nul & del "%~f0"\r\n',
            encoding="utf-8",
        )
        subprocess.Popen(
            ["cmd", "/c", str(script)],
            creationflags=subprocess.DETACHED_PROCESS | subprocess.CREATE_NO_WINDOW,
            close_fds=True,
        )
        LOG.info("Self-delete scheduled for %s", target)
    except Exception as exc:  # pragma: no cover - Windows specific
        LOG.warning("Could not schedule self-delete: %s", exc)


def main(argv: Optional[Sequence[str]] = None) -> int:
    """Run the show.  Returns a process exit code."""
    args = parse_args(argv)
    logging.basicConfig(
        level=getattr(logging, str(args.log_level).upper(), logging.INFO),
        format="%(asctime)s %(levelname)-7s %(name)s: %(message)s",
    )

    LOG.info("%s %s - starting", config.APP_NAME, config.APP_VERSION)
    if not args.no_volume:
        LOG.warning("System volume will be set to 100%% and restored afterwards")

    screen: Optional[Screen] = None
    volume = VolumeControl(enabled=not args.no_volume)
    audio = MusicPlayer(enabled=not args.no_audio)
    exit_code = 0

    try:
        screen = Screen(windowed=args.windowed)
        screen.open()
        screen.set_cursor_visible(False)

        # Snapshot the system state *before* any audio is played.
        volume.save_volume()
        LOG.info("Equalizer baseline captured (DSP bus only)")

        ctx = BattleContext(
            screen,
            audio,
            volume,
            time_scale=args.time_scale,
            seed=args.seed,
            capture_dir=args.capture,
            capture_interval=args.capture_interval,
        )
        BattleManager(ctx).run()

    except ScenarioAbort as exc:
        LOG.info("Show stopped early: %s", exc)
    except KeyboardInterrupt:
        LOG.info("Interrupted by user")
    except Exception as exc:  # pragma: no cover - last line of defence
        LOG.exception("Unexpected error: %s", exc)
        exit_code = 1
    finally:
        # ------------------------------------------------------------------ #
        # CRITICAL: leave the machine exactly as we found it.
        # ------------------------------------------------------------------ #
        try:
            volume.restore_volume()
        except Exception as exc:  # pragma: no cover - defensive
            LOG.error("Volume restore failed: %s", exc)
        try:
            volume.close()
        except Exception:
            pass
        try:
            if screen is not None:
                screen.set_cursor_visible(True)
                screen.close()
        except Exception:
            pass
        try:
            audio.shutdown()
        except Exception:
            pass
        try:
            pygame.quit()
        except Exception:
            pass
        if args.self_delete:
            schedule_self_delete()
        LOG.info("Done - screen, cursor, volume and equalizer restored")

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
