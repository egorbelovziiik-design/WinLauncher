"""MADorigg — точка входа. Эпичная битва двух смайликов за экран.

Запуск:  pip install -r requirements.txt && python main.py
Выход:   Alt+F4 / ESC / крестик окна (если в окне) — громкость
         восстанавливается ВСЕГДА, даже при падении (try/finally).
"""

from __future__ import annotations

import logging
import sys
import traceback

import pygame

import config
from audio.music_player import MusicPlayer
from audio.volume_control import VolumeControl
from battle.battle_manager import BattleManager
from visuals.screen import create_screen, hide_cursor, show_cursor

logger = logging.getLogger("madorigg")


def _setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] %(levelname)s %(name)s: %(message)s",
        datefmt="%H:%M:%S",
    )


def main() -> None:
    """Главная функция: инициализация, сценарий, гарантированная очистка."""
    _setup_logging()
    logger.info("MADorigg starting...")

    # 1. Инициализация pygame
    pygame.init()
    try:
        pygame.mixer.init(
            frequency=config.SAMPLE_RATE,
            size=-16,
            channels=2,
            buffer=1024,
        )
    except pygame.error as exc:
        logger.warning("Audio init failed: %s (running muted)", exc)

    # 2. Полноэкранный режим + topmost
    screen = create_screen()
    hide_cursor()

    # 3. ТИХО сохраняем громкость и настройки эквалайзера
    vol_ctrl = VolumeControl()
    vol_ctrl.save_volume()
    vol_ctrl.save_eq()

    music = MusicPlayer()

    try:
        # 4. Весь сценарий (фазы 0–5, ≈42 сек)
        battle = BattleManager(screen, vol_ctrl, music)
        battle.run()
    except (KeyboardInterrupt, SystemExit):
        logger.info("Interrupted by user.")
    except Exception:
        logger.error("Fatal error:\n%s", traceback.format_exc())
    finally:
        # 5. ОБЯЗАТЕЛЬНОЕ восстановление — в любом случае!
        logger.info("Restoring system state...")
        try:
            vol_ctrl.restore_volume()
        except Exception:
            logger.exception("Volume restore failed")
        try:
            vol_ctrl.restore_eq()
        except Exception:
            logger.exception("EQ restore failed")
        try:
            music.stop_all()
        except Exception:
            pass
        try:
            show_cursor()
        except Exception:
            pass
        pygame.quit()

        # 6. Опциональное самоудаление .exe (по умолчанию выключено)
        if config.SELF_DELETE:
            _schedule_self_delete()

        logger.info("MADorigg closed. System restored.")


def _schedule_self_delete() -> None:
    """Запланировать удаление собственного .exe через bat-скрипт.

    bat: `ping -n 3 127.0.0.1 >nul & del "%~f0" & del "<exe>"` —
    сама себя и исполняемый файл удаляет через 2 секунды после выхода.
    """
    import os
    import subprocess
    import tempfile

    if not getattr(sys, "frozen", False):
        return  # самоудаление имеет смысл только для собранного .exe
    try:
        exe_path = os.path.abspath(sys.executable)
        bat_path = os.path.join(tempfile.gettempdir(), "madorigg_cleanup.bat")
        with open(bat_path, "w", encoding="utf-8") as fh:
            fh.write("@echo off\r\n")
            fh.write("ping -n 3 127.0.0.1 >nul\r\n")
            fh.write(f'del "{exe_path}"\r\n')
            fh.write('del "%~f0"\r\n')
        subprocess.Popen(
            ["cmd", "/c", bat_path],
            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
            | getattr(subprocess, "DETACHED_PROCESS", 0x00000008),
        )
        logger.info("Self-delete scheduled.")
    except Exception:
        logger.exception("Self-delete scheduling failed")


if __name__ == "__main__":
    try:
        main()
    finally:
        # Последний рубеж: pygame гарантированно закрыт
        if pygame.get_init():
            pygame.quit()
    sys.exit(0)
