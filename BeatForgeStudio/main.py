"""Точка входа BeatForge Studio.

Запуск::

    pip install -r requirements.txt
    python main.py

Порядок инициализации:

1. логгер (файл + консоль);
2. конфиг ``~/.beatforge_studio/config.json``;
3. QApplication + тема;
4. глобальный обработчик исключений (ошибки — в QMessageBox и в лог, без падения);
5. главное окно.

Дополнительные ключи командной строки::

    python main.py --safe             # без анимаций и без открытия аудиоустройства
    python main.py --project file.bfs
    python main.py --export-audio out.mp3
    python main.py --reset-config     # сбросить настройки к значениям по умолчанию
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
import traceback
from types import TracebackType
from typing import Optional

LOG = logging.getLogger("beatforge")

#: Минимальная версия Python.
MIN_PYTHON = (3, 10)


def _parse_args(argv: list[str]) -> argparse.Namespace:
    """Разобрать аргументы командной строки.

    Args:
        argv: Список аргументов (без имени программы).

    Returns:
        Разобранные аргументы.
    """
    parser = argparse.ArgumentParser(
        prog="BeatForge Studio",
        description="Мультимедийная студия: поиск, аудио, видео, экспорт и публикация.",
    )
    parser.add_argument("--project", help="Открыть проект .bfs при запуске")
    parser.add_argument("--export-audio", help="Экспортировать аудио в файл и выйти")
    parser.add_argument("--safe", action="store_true",
                        help="Безопасный режим: без анимаций и звукового устройства")
    parser.add_argument("--reset-config", action="store_true",
                        help="Сбросить настройки к значениям по умолчанию")
    parser.add_argument("--debug", action="store_true", help="Подробный лог (DEBUG)")
    parser.add_argument("--version", action="store_true", help="Показать версию")
    return parser.parse_args(argv)


def _install_excepthook(app) -> None:
    """Поставить глобальный обработчик исключений.

    Неперехваченные исключения попадают в лог и в QMessageBox, но приложение
    продолжает работу — это требование по устойчивости.
    """
    from PyQt6.QtWidgets import QMessageBox

    from utils.logger import log_exception

    def hook(exc_type: type[BaseException], exc_value: BaseException,
             exc_tb: Optional[TracebackType]) -> None:
        if issubclass(exc_type, KeyboardInterrupt):
            sys.__excepthook__(exc_type, exc_value, exc_tb)
            return
        text = "".join(traceback.format_exception(exc_type, exc_value, exc_tb))
        log_exception("Неперехваченное исключение", text)
        try:
            box = QMessageBox()
            box.setWindowTitle("Непредвиденная ошибка")
            box.setIcon(QMessageBox.Icon.Warning)
            box.setText("Что-то пошло не так, но приложение продолжает работу.")
            box.setInformativeText(f"{exc_type.__name__}: {exc_value}")
            box.setDetailedText(text)
            box.exec()
        except Exception:
            print(text, file=sys.stderr)

    sys.excepthook = hook


def _headless_export_audio(window, out_path: str) -> int:
    """Отрендерить микс текущего проекта в файл без интерактива.

    Args:
        window: Главное окно с загруженным проектом.
        out_path: Путь назначения (расширение задаёт формат).

    Returns:
        Код возврата процесса: 0 — успех, 1 — нечего экспортировать, 2 — ошибка.
    """
    from pathlib import Path

    from core.exporter import export_audio

    project = window.audio_tab.project
    if project.duration <= 0:
        LOG.error("Автоэкспорт: в проекте нет аудио на таймлайне")
        return 1
    out = Path(out_path).expanduser()
    fmt = (out.suffix.lstrip(".") or "wav").lower()
    try:
        buffer = project.render()
        if buffer.frames <= 0:
            LOG.error("Автоэкспорт: пустой микс")
            return 1
        export_audio(buffer, out, fmt=fmt)
    except Exception:  # noqa: BLE001 - код возврата важнее трассировки
        LOG.exception("Автоэкспорт не удался")
        return 2
    LOG.info("Автоэкспорт готов: %s (%.1f КБ)", out, out.stat().st_size / 1024)
    return 0


def main(argv: Optional[list[str]] = None) -> int:
    """Запустить приложение.

    Args:
        argv: Аргументы командной строки (по умолчанию ``sys.argv[1:]``).

    Returns:
        Код возврата (0 — успех).
    """
    args = _parse_args(list(sys.argv[1:] if argv is None else argv))

    if args.version:
        # В оконной сборке stdout может отсутствовать
        message = "BeatForge Studio 1.0.0"
        for stream in (sys.stdout, sys.stderr):
            if stream is not None:
                stream.write(message + "\n")
                break
        return 0

    if sys.version_info < MIN_PYTHON:
        sys.stderr.write(
            f"Требуется Python {MIN_PYTHON[0]}.{MIN_PYTHON[1]} или новее, "
            f"сейчас {sys.version.split()[0]}\n")
        return 2

    # --- окружение до импорта Qt
    os.environ.setdefault("QT_ENABLE_HIGHDPI_SCALING", "1")
    if sys.platform.startswith("linux"):
        os.environ.setdefault("QT_QPA_PLATFORM", os.environ.get("QT_QPA_PLATFORM", ""))

    from utils.logger import setup_logging

    setup_logging(level=logging.DEBUG if args.debug else logging.INFO)
    LOG.info("Запуск BeatForge Studio %s", "1.0.0")

    from utils.config import get_config

    if args.reset_config:
        try:
            from utils.config import CONFIG_FILE

            CONFIG_FILE.unlink(missing_ok=True)
            LOG.info("Конфигурация сброшена")
        except OSError as exc:
            LOG.warning("Не удалось сбросить конфиг: %s", exc)
    cfg = get_config(reload=True)
    if args.safe:
        cfg.ui.animations = False
        cfg.ui.restore_session = False

    from PyQt6.QtCore import Qt
    from PyQt6.QtWidgets import QApplication

    # В Qt6 масштабирование HiDPI включено всегда; атрибут оставлен
    # для совместимости с Qt5-сборками (поэтому проверяется через hasattr).
    high_dpi = getattr(Qt.ApplicationAttribute, "AA_EnableHighDpiScaling", None)
    if high_dpi is not None:
        QApplication.setAttribute(high_dpi, True)
    app = QApplication(sys.argv[:1])
    app.setApplicationName("BeatForge Studio")
    app.setApplicationDisplayName("BeatForge Studio")
    app.setOrganizationName("BeatForge")
    app.setApplicationVersion("1.0.0")

    from ui.theme import apply_theme
    from utils.fonts import load_fonts

    load_fonts()
    apply_theme(app, cfg.ui.theme, cfg.ui.font_family, cfg.ui.font_size)
    _install_excepthook(app)

    from ui.main_window import MainWindow

    window = MainWindow()
    if args.export_audio:
        window.set_silent_errors(True)
    if args.safe:
        window.audio_engine._device_available = False  # noqa: SLF001 - безопасный режим
    if not args.export_audio:
        window.show()

    project_error: Optional[str] = None
    if args.project:
        errors: list[str] = []
        window.projects.errorOccurred.connect(errors.append)
        if window.projects.load(args.project) is None:
            project_error = errors[0] if errors else "не удалось открыть проект"

    if args.export_audio:
        LOG.info("Автоэкспорт аудио: %s", args.export_audio)
        if project_error:
            LOG.error("Автоэкспорт отменён: %s", project_error)
            return 1
        return _headless_export_audio(window, args.export_audio)

    LOG.info("Окно открыто (%.0f×%.0f)", window.width(), window.height())
    try:
        return app.exec()
    finally:
        LOG.info("Приложение завершено")


if __name__ == "__main__":
    sys.exit(main())
