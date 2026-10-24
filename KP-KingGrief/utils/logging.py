"""
Логирование КП-KingGrief.

Пишет и в консоль, и в ротатируемый файл `logs/bot.log` (+ `logs/error.log`
только для ошибок). Работает и в PyInstaller-сборке.
"""

from __future__ import annotations

import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path

from config import LOG_DIR

_CONFIGURED = False
_LOGGER_NAME = "kp"

CONSOLE_FORMAT = "%(asctime)s │ %(levelname)-8s │ %(name)-22s │ %(message)s"
FILE_FORMAT = "%(asctime)s │ %(levelname)-8s │ %(name)-28s │ %(funcName)s:%(lineno)d │ %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"


class _ColorFormatter(logging.Formatter):
    """Цветной вывод в консоль (без ANSI на Windows без поддержки)."""

    COLORS = {
        "DEBUG": "\033[90m",
        "INFO": "\033[94m",
        "WARNING": "\033[93m",
        "ERROR": "\033[91m",
        "CRITICAL": "\033[101m",
    }
    RESET = "\033[0m"

    def __init__(self, *args, use_color: bool = True, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self.use_color = use_color

    def format(self, record: logging.LogRecord) -> str:  # noqa: D102
        text = super().format(record)
        if not self.use_color:
            return text
        color = self.COLORS.get(record.levelname)
        return f"{color}{text}{self.RESET}" if color else text


def _supports_color() -> bool:
    if not hasattr(sys.stdout, "isatty") or not sys.stdout.isatty():
        return False
    if sys.platform == "win32":
        try:
            import ctypes

            kernel32 = ctypes.windll.kernel32  # type: ignore[attr-defined]
            handle = kernel32.GetStdHandle(-11)
            mode = ctypes.c_uint32()
            if kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
                kernel32.SetConsoleMode(handle, mode.value | 0x0004)
                return True
            return False
        except Exception:
            return False
    return True


def setup_logging(level: str = "INFO", log_dir: Path | None = None) -> logging.Logger:
    """Инициализирует логирование (идемпотентно)."""
    global _CONFIGURED
    root = logging.getLogger(_LOGGER_NAME)
    if _CONFIGURED:
        return root

    directory = Path(log_dir or LOG_DIR)
    directory.mkdir(parents=True, exist_ok=True)

    root.setLevel(getattr(logging, str(level).upper(), logging.INFO))
    root.propagate = False
    root.handlers.clear()

    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(
        _ColorFormatter(CONSOLE_FORMAT, DATE_FORMAT, use_color=_supports_color())
    )
    root.addHandler(console)

    try:
        file_handler = RotatingFileHandler(
            directory / "bot.log",
            maxBytes=5 * 1024 * 1024,
            backupCount=5,
            encoding="utf-8",
        )
        file_handler.setFormatter(logging.Formatter(FILE_FORMAT, DATE_FORMAT))
        root.addHandler(file_handler)

        err_handler = RotatingFileHandler(
            directory / "error.log",
            maxBytes=2 * 1024 * 1024,
            backupCount=3,
            encoding="utf-8",
        )
        err_handler.setLevel(logging.ERROR)
        err_handler.setFormatter(logging.Formatter(FILE_FORMAT, DATE_FORMAT))
        root.addHandler(err_handler)
    except Exception as exc:  # диск недоступен — не роняем бота
        root.warning("Не удалось создать файловые логи: %s", exc)

    # discord.py шумит на DEBUG, оставляем WARNING
    logging.getLogger("discord").setLevel(logging.WARNING)
    logging.getLogger("discord.gateway").setLevel(logging.WARNING)
    logging.getLogger("discord.http").setLevel(logging.WARNING)
    logging.getLogger("apscheduler").setLevel(logging.WARNING)
    logging.getLogger("aiosqlite").setLevel(logging.WARNING)

    _CONFIGURED = True
    root.info("Логирование инициализировано (%s)", directory)
    return root


def get_logger(name: str) -> logging.Logger:
    """Возвращает дочерний логгер вида `kp.<name>`."""
    if not _CONFIGURED:
        setup_logging()
    return logging.getLogger(f"{_LOGGER_NAME}.{name}")


def banner(text: str, char: str = "━", width: int = 58) -> str:
    """Красивая рамка для консольного вывода."""
    line = char * width
    return f"{line}\n{text}\n{line}"
