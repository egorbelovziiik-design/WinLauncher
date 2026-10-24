#!/usr/bin/env python3
"""
КП-KingGrief — точка входа.

Запуск:
    python main.py            (из исходников)
    КП-KingGrief.exe          (сборка PyInstaller)

Токен бота читается ТОЛЬКО из внешнего файла `.env` или переменной
окружения `DISCORD_TOKEN`. В исполняемый файл он не встраивается.
"""

from __future__ import annotations

import asyncio
import os
import sys

from config import BASE_DIR, BOT_DESCRIPTION, BOT_NAME, BOT_VERSION, DB_PATH, LOG_DIR
from utils.logging import banner, get_logger, setup_logging

log = get_logger("main")


def print_header() -> None:
    print(
        banner(
            f"  🛡️  {BOT_NAME} v{BOT_VERSION}\n"
            f"  {BOT_DESCRIPTION}\n"
            f"  📁 Каталог: {BASE_DIR}\n"
            f"  💾 База:    {DB_PATH}\n"
            f"  📜 Логи:    {LOG_DIR}"
        )
    )


async def amain() -> int:
    """Асинхронная точка входа. Возвращает код завершения."""
    from bot.client import SingleInstanceLock, check_token, run_forever

    print_header()

    token = check_token()
    if token is None:
        input("\nНажмите Enter для выхода…") if sys.stdin.isatty() else None
        return 1

    lock = SingleInstanceLock()
    if not lock.acquire():
        print(
            "\n❌ Бот уже запущен. Второй экземпляр может повредить базу данных.\n"
            "   Если вы уверены, что процесс не работает — удалите файл "
            f"{lock.path}\n"
        )
        input("Нажмите Enter для выхода…") if sys.stdin.isatty() else None
        return 5

    stop = asyncio.Event()
    try:
        install_handlers(stop)
        return await run_forever(token)
    except asyncio.CancelledError:  # pragma: no cover
        return 0
    finally:
        lock.release()


def install_handlers(stop: asyncio.Event) -> None:
    """Корректное завершение по Ctrl+C."""
    from bot.client import install_signal_handlers

    try:
        install_signal_handlers(asyncio.get_running_loop(), stop)
    except RuntimeError:  # pragma: no cover
        pass


def main() -> int:
    setup_logging(os.getenv("KP_LOG_LEVEL", "INFO"))
    try:
        return asyncio.run(amain())
    except KeyboardInterrupt:
        print("\n🛑 Остановка бота по команде пользователя.")
        return 0
    except Exception as exc:  # noqa: BLE001
        log.critical("Критическая ошибка запуска: %s", exc, exc_info=True)
        print(f"\n❌ Критическая ошибка запуска: {exc}")
        print(f"   Подробности: {LOG_DIR / 'error.log'}")
        if sys.stdin.isatty():
            input("Нажмите Enter для выхода…")
        return 1


if __name__ == "__main__":
    sys.exit(main())
