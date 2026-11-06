"""Логирование BeatForge Studio: консоль + ротируемый файл.

Использование::

    from utils.logger import init_logging, get_logger

    init_logging()
    log = get_logger(__name__)
    log.info("старт")
"""

from __future__ import annotations

import logging
import logging.handlers
import sys
from pathlib import Path
from typing import Optional

APP_DIR = Path.home() / ".beatforge_studio"
LOG_FILE = APP_DIR / "beatforge_studio.log"

_FORMAT = "%(asctime)s | %(levelname)-8s | %(name)-28s | %(message)s"
_DATEFMT = "%H:%M:%S"

_configured = False


class _ColorFormatter(logging.Formatter):
    """Форматтер с ANSI-цветами для консоли (Windows 10+ поддерживает)."""

    COLORS = {
        logging.DEBUG: "\033[38;5;245m",
        logging.INFO: "\033[38;5;39m",
        logging.WARNING: "\033[38;5;214m",
        logging.ERROR: "\033[38;5;203m",
        logging.CRITICAL: "\033[1;38;5;196m",
    }
    RESET = "\033[0m"

    def __init__(self, use_color: bool = True) -> None:
        super().__init__(_FORMAT, _DATEFMT)
        self.use_color = use_color

    def format(self, record: logging.LogRecord) -> str:
        text = super().format(record)
        if not self.use_color:
            return text
        color = self.COLORS.get(record.levelno, "")
        return f"{color}{text}{self.RESET}"


def init_logging(level: int = logging.INFO,
                 log_file: Optional[Path] = None,
                 to_file: bool = True) -> None:
    """Инициализировать корневой логгер (идемпотентно).

    Args:
        level: Минимальный уровень логирования.
        log_file: Путь к файлу лога; по умолчанию ``~/.beatforge_studio/beatforge_studio.log``.
        to_file: Писать ли лог в файл (5 МБ × 3 бэкапа).
    """
    global _configured
    root = logging.getLogger()
    if _configured:
        root.setLevel(level)
        return

    root.setLevel(level)
    # В оконной сборке (PyInstaller console=False) sys.stdout == None,
    # поэтому консольный обработчик добавляем только если поток есть.
    stream = sys.stdout or sys.stderr
    if stream is not None:
        use_color = getattr(stream, "isatty", lambda: False)() and sys.platform != "win32"
        console = logging.StreamHandler(stream)
        console.setFormatter(_ColorFormatter(use_color))
        root.addHandler(console)

    if to_file:
        target = Path(log_file) if log_file else LOG_FILE
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            handler = logging.handlers.RotatingFileHandler(
                target, maxBytes=5 * 1024 * 1024, backupCount=3, encoding="utf-8"
            )
            handler.setFormatter(logging.Formatter(_FORMAT, "%Y-%m-%d %H:%M:%S"))
            root.addHandler(handler)
        except OSError as exc:
            root.warning("Лог-файл недоступен (%s): %s", target, exc)

    for noisy in ("urllib3", "yt_dlp", "matplotlib", "PIL", "googleapiclient"):
        logging.getLogger(noisy).setLevel(logging.WARNING)

    _configured = True
    get_logger(__name__).info("Логирование инициализировано")


def get_logger(name: str) -> logging.Logger:
    """Вернуть логгер с указанным именем (обёртка над :func:`logging.getLogger`)."""
    return logging.getLogger(name)


def log_file_path() -> Path:
    """Путь к текущему лог-файлу."""
    return LOG_FILE


def setup_logging(level: int = logging.INFO,
                  log_file: Optional[Path] = None) -> None:
    """Совместимый псевдоним :func:`init_logging` (используется в ``main.py``).

    Args:
        level: Минимальный уровень.
        log_file: Путь к файлу лога (по умолчанию — стандартный).
    """
    init_logging(level=level, log_file=log_file)


def log_exception(message: str = "Исключение",
                  details: Optional[str] = None) -> None:
    """Записать исключение в лог (с текущим traceback, если он есть).

    Args:
        message: Текст сообщения.
        details: Готовый traceback (если передан — используется он).
    """
    logger = logging.getLogger("beatforge")
    if details:
        logger.error("%s\n%s", message, details)
    else:
        logger.exception(message)
