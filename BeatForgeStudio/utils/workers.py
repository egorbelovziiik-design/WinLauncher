"""Фоновые задачи: универсальные QRunnable/Worker + QThreadPool.

Модуль даёт простой способ выполнить функцию в пуле потоков и вернуть результат
в GUI-поток через сигналы, не блокируя интерфейс.

Пример::

    from utils.workers import submit

    submit(
        requests.get, "https://example.com",
        on_done=lambda r: print(r.status_code),
        on_error=lambda e: print("boom", e),
        name="http-probe",
    )
"""

from __future__ import annotations

import logging
import traceback
from typing import Any, Callable, Optional

from PyQt6.QtCore import QObject, QRunnable, QThreadPool, pyqtSignal

LOG = logging.getLogger(__name__)


class WorkerSignals(QObject):
    """Сигналы задачи (живут в GUI-потоке)."""

    started = pyqtSignal()
    progress = pyqtSignal(int, str)
    result = pyqtSignal(object)
    error = pyqtSignal(str, str)  # сообщение, traceback
    finished = pyqtSignal()


class Worker(QRunnable):
    """Выполнить ``fn(*args, **kwargs)`` в пуле потоков.

    Args:
        fn: Функция.
        *args: Позиционные аргументы.
        **kwargs: Именованные аргументы.

    Атрибуты:
        signals: :class:`WorkerSignals`.
        name: Имя задачи (для логов).
    """

    def __init__(self, fn: Callable[..., Any], *args: Any, name: str = "",
                 **kwargs: Any) -> None:
        super().__init__()
        self._fn = fn
        self._args = args
        self._kwargs = kwargs
        self.signals = WorkerSignals()
        self.name = name or getattr(fn, "__name__", "task")
        self.setAutoDelete(True)

    def run(self) -> None:  # noqa: D401 - метод Qt
        """Тело задачи (выполняется в рабочем потоке)."""
        self.signals.started.emit()
        try:
            result = self._fn(*self._args, **self._kwargs)
            self.signals.result.emit(result)
        except Exception as exc:
            LOG.exception("Задача '%s' упала: %s", self.name, exc)
            self.signals.error.emit(str(exc), traceback.format_exc())
        finally:
            self.signals.finished.emit()


_pool: Optional[QThreadPool] = None


def pool() -> QThreadPool:
    """Вернуть общий пул потоков приложения."""
    global _pool
    if _pool is None:
        _pool = QThreadPool.globalInstance()
        _pool.setMaxThreadCount(max(4, _pool.maxThreadCount()))
    return _pool


def submit(fn: Callable[..., Any], *args: Any,
           on_done: Optional[Callable[[Any], None]] = None,
           on_error: Optional[Callable[[str, str], None]] = None,
           on_progress: Optional[Callable[[int, str], None]] = None,
           on_finished: Optional[Callable[[], None]] = None,
           name: str = "", **kwargs: Any) -> Worker:
    """Запустить ``fn`` в пуле потоков с колбэками.

    Args:
        fn: Функция для выполнения.
        *args: Аргументы функции.
        on_done: Колбэк с результатом (в GUI-потоке).
        on_error: Колбэк ``(сообщение, traceback)`` при ошибке.
        on_progress: Колбэк прогресса (если функция принимает ``progress_cb``).
        on_finished: Колбэк завершения (всегда).
        name: Имя задачи для логов.
        **kwargs: Именованные аргументы функции.

    Returns:
        :class:`Worker` (можно дождаться через ``worker.signals.finished``).
    """
    worker = Worker(fn, *args, name=name, **kwargs)
    if on_done:
        worker.signals.result.connect(on_done)
    if on_error:
        worker.signals.error.connect(on_error)
    else:
        worker.signals.error.connect(lambda msg, tb: LOG.error("Необработанная ошибка: %s\n%s", msg, tb))
    if on_progress:
        worker.signals.progress.connect(on_progress)
    if on_finished:
        worker.signals.finished.connect(on_finished)
    pool().start(worker)
    return worker


def wait_all(msecs: int = -1) -> bool:
    """Дождаться завершения всех задач пула (например, при закрытии приложения)."""
    return pool().waitForDone(msecs)
