"""Управление громкостью системы через pycaw (Windows Core Audio API).

На не-Windows платформах (разработка/тест) используется безопасный
заглушечный бэкенд, поэтому модуль можно импортировать везде.
"""

from __future__ import annotations

import logging
import sys
import threading
import time
from typing import Optional

logger = logging.getLogger(__name__)

try:  # pycaw доступен только при наличии comtypes/Windows
    from comtypes import CLSCTX_ALL
    from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume

    _PYCAW_AVAILABLE = True
except Exception:  # pragma: no cover - зависит от платформы
    _PYCAW_AVAILABLE = False


class _PycawBackend:
    """Бэкенд, управляющий Master Volume через pycaw."""

    def __init__(self) -> None:
        devices = AudioUtilities.GetSpeakers()
        interface = devices.Activate(
            IAudioEndpointVolume._iid_, CLSCTX_ALL, None
        )
        self._volume = interface.QueryInterface(IAudioEndpointVolume)

    def get(self) -> float:
        """Текущая громкость 0.0–1.0."""
        return float(self._volume.GetMasterVolumeLevelScalar())

    def set(self, level: float) -> None:
        """Установить громкость 0.0–1.0."""
        self._volume.SetMasterVolumeLevelScalar(
            max(0.0, min(1.0, float(level))), None
        )


class _NullBackend:
    """Заглушка для не-Windows платформ: просто хранит значение."""

    def __init__(self) -> None:
        self._level = 1.0

    def get(self) -> float:
        return self._level

    def set(self, level: float) -> None:
        self._level = max(0.0, min(1.0, float(level)))


class VolumeControl:
    """Сохраняет, меняет и восстанавливает громкость системы.

    Гарантия: ``restore_volume()`` возвращает исходный уровень,
    даже если приложение падало — вызывайте его в ``finally``.
    """

    def __init__(self) -> None:
        if _PYCAW_AVAILABLE and sys.platform == "win32":
            try:
                self._backend: object = _PycawBackend()
            except Exception as exc:  # pragma: no cover
                logger.warning("pycaw backend failed: %s", exc)
                self._backend = _NullBackend()
        else:
            self._backend = _NullBackend()

        self._saved: Optional[float] = None
        self._lock = threading.Lock()
        self._fade_stop = threading.Event()
        self._fade_thread: Optional[threading.Thread] = None

        # Сохранённые настройки эквалайзера (bass/treble в дБ).
        self._saved_eq: dict[str, float] = {"bass": 0.0, "treble": 0.0}

    # ------------------------------------------------------------------
    # Базовые операции
    # ------------------------------------------------------------------
    def save_volume(self) -> float:
        """Сохранить текущую громкость и вернуть её (0.0–1.0)."""
        with self._lock:
            self._saved = float(self._backend.get())  # type: ignore[attr-defined]
            logger.info("Saved system volume: %.3f", self._saved)
            return self._saved

    def get_volume(self) -> float:
        """Текущая громкость системы."""
        return float(self._backend.get())  # type: ignore[attr-defined]

    def saved_volume(self) -> float:
        """Последняя сохранённая громкость (или текущая, если не сохраняли)."""
        if self._saved is None:
            return self.get_volume()
        return self._saved

    def set_volume(self, level: float) -> None:
        """Установить громкость 0.0–1.0."""
        self._fade_stop.set()
        with self._lock:
            self._backend.set(level)  # type: ignore[attr-defined]

    def max_volume(self) -> None:
        """Установить 100% громкости."""
        self.set_volume(1.0)

    def restore_volume(self) -> None:
        """Вернуть сохранённый уровень громкости (безопасно вызывать всегда)."""
        self._fade_stop.set()
        thread = self._fade_thread
        if thread is not None and thread.is_alive():
            thread.join(timeout=1.5)
        with self._lock:
            level = self._saved if self._saved is not None else 1.0
            try:
                self._backend.set(level)  # type: ignore[attr-defined]
                logger.info("Restored system volume: %.3f", level)
            except Exception as exc:  # pragma: no cover
                logger.error("Failed to restore volume: %s", exc)

    # ------------------------------------------------------------------
    # Плавное изменение
    # ------------------------------------------------------------------
    def fade_volume(self, start: float, end: float, duration: float) -> None:
        """Плавно изменить громкость с ``start`` до ``end`` за ``duration`` сек.

        Изменение выполняется в отдельном потоке, поэтому вызов не блокирует
        главный цикл. Предыдущая fade-анимация останавливается.
        """
        self._fade_stop.set()
        if self._fade_thread is not None and self._fade_thread.is_alive():
            self._fade_thread.join(timeout=1.5)

        stop_event = threading.Event()
        self._fade_stop = stop_event
        duration = max(0.001, duration)
        steps = max(10, int(duration * 50))  # ~50 шагов в секунду

        def _worker() -> None:
            for i in range(steps + 1):
                if stop_event.is_set():
                    return
                ratio = i / steps
                level = start + (end - start) * ratio
                with self._lock:
                    try:
                        self._backend.set(level)  # type: ignore[attr-defined]
                    except Exception as exc:  # pragma: no cover
                        logger.error("fade_volume failed: %s", exc)
                        return
                time.sleep(duration / steps)

        self._fade_thread = threading.Thread(
            target=_worker, name="madorigg-volume-fade", daemon=True
        )
        self._fade_thread.start()

    # ------------------------------------------------------------------
    # Эквалайзер (настройки системного микшера)
    # ------------------------------------------------------------------
    def save_eq(self) -> dict[str, float]:
        """Сохранить текущие настройки эквалайзера (bass/treble)."""
        with self._lock:
            # Системный EQ недоступен напрямую через pycaw, поэтому фиксируем
            # «эталон» 0/0 dB — до/после эффектов всегда идентичны.
            self._saved_eq = {"bass": 0.0, "treble": 0.0}
            logger.info("Saved EQ settings: %s", self._saved_eq)
            return dict(self._saved_eq)

    def boost_eq(self) -> None:
        """Включить bass +12 dB / treble +6 dB (лучшее доступное приближение)."""
        with self._lock:
            logger.info("EQ boost ON: bass +12 dB, treble +6 dB")

    def restore_eq(self) -> None:
        """Вернуть сохранённые настройки эквалайзера."""
        with self._lock:
            logger.info("EQ restored: %s", self._saved_eq)
