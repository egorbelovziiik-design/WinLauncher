"""Аудиодвижок BeatForge Studio.

Модуль отвечает за:

* декодирование файлов в float32-буферы (:func:`load_audio`, :class:`AudioBuffer`);
* воспроизведение через ``sounddevice.OutputStream`` с callback-буфером
  (:class:`AudioEngine`);
* перемотку с кроссфейдом 10 мс, громкость с плавным рампом, изменение
  скорости (tape-style и с сохранением тональности через librosa);
* стриминговое превью первых N секунд трека из сети (:class:`PreviewManager`).

Движок не знает про Qt-виджеты: он лишь эмитит сигналы, которые подключает UI.

Если PortAudio/sounddevice недоступны (CI, headless-сервер, битая установка),
движок автоматически переходит в «немой» режим: позиция и состояние работают,
звук не выводится — это позволяет тестировать логику без звуковой карты.
"""

from __future__ import annotations

import logging
import math
import os
import shutil
import subprocess
import tempfile
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, List, Optional, Protocol, Tuple

import numpy as np

from PyQt6.QtCore import QObject, QTimer, pyqtSignal

from utils.config import get_config

LOG = logging.getLogger(__name__)

#: Поддерживаемые расширения аудиофайлов.
AUDIO_EXTENSIONS: Tuple[str, ...] = (
    ".wav", ".mp3", ".flac", ".ogg", ".opus", ".m4a", ".aac", ".aiff",
    ".aif", ".wma", ".webm", ".mp4", ".mkv",
)


# ============================================================
#  Загрузка аудио
# ============================================================
@dataclass
class AudioBuffer:
    """Многоканальный аудиобуфер float32.

    Attributes:
        samples: Массив формы ``(frames, channels)`` со значениями в [-1, 1].
        sample_rate: Частота дискретизации буфера, Гц.
        path: Исходный файл (если буфер получен из файла).
    """

    samples: np.ndarray
    sample_rate: int
    path: Optional[Path] = None

    def __post_init__(self) -> None:
        if self.samples.ndim == 1:
            self.samples = self.samples[:, None]
        if self.samples.dtype != np.float32:
            self.samples = self.samples.astype(np.float32)
        self.samples = np.ascontiguousarray(self.samples)

    # ---------------------------------------------------------------- свойства
    @property
    def frames(self) -> int:
        """Число кадров (сэмплов на канал)."""
        return int(self.samples.shape[0])

    @property
    def channels(self) -> int:
        """Число каналов."""
        return int(self.samples.shape[1])

    @property
    def duration(self) -> float:
        """Длительность, сек."""
        return self.frames / float(self.sample_rate) if self.sample_rate else 0.0

    # ---------------------------------------------------------------- операции
    def to_channels(self, channels: int) -> "AudioBuffer":
        """Привести число каналов к ``channels`` (моно ↔ стерео)."""
        if self.channels == channels:
            return self
        if channels == 1:
            return AudioBuffer(self.samples.mean(axis=1, keepdims=True), self.sample_rate, self.path)
        if self.channels == 1:
            return AudioBuffer(np.repeat(self.samples, channels, axis=1), self.sample_rate, self.path)
        if channels < self.channels:
            return AudioBuffer(self.samples[:, :channels], self.sample_rate, self.path)
        pad = np.repeat(self.samples[:, -1:], channels - self.channels, axis=1)
        return AudioBuffer(np.hstack([self.samples, pad]), self.sample_rate, self.path)

    def resample(self, sample_rate: int) -> "AudioBuffer":
        """Передискретизировать буфер (линейная интерполяция, быстро)."""
        if sample_rate == self.sample_rate or self.frames == 0:
            return AudioBuffer(self.samples, sample_rate, self.path)
        ratio = sample_rate / float(self.sample_rate)
        new_frames = max(1, int(round(self.frames * ratio)))
        x_old = np.arange(self.frames, dtype=np.float64)
        x_new = np.linspace(0.0, self.frames - 1, new_frames, dtype=np.float64)
        out = np.empty((new_frames, self.channels), dtype=np.float32)
        for ch in range(self.channels):
            out[:, ch] = np.interp(x_new, x_old, self.samples[:, ch]).astype(np.float32)
        return AudioBuffer(out, sample_rate, self.path)

    def slice(self, start: float, end: float) -> "AudioBuffer":
        """Вырезать фрагмент по времени (сек)."""
        a = max(0, int(start * self.sample_rate))
        b = min(self.frames, int(end * self.sample_rate))
        return AudioBuffer(self.samples[a:b].copy(), self.sample_rate, self.path)

    def peak(self) -> float:
        """Максимальная абсолютная амплитуда."""
        return float(np.max(np.abs(self.samples))) if self.frames else 0.0

    def to_mono_peaks(self, buckets: int) -> np.ndarray:
        """Усреднённые пики для отрисовки волны.

        Args:
            buckets: Сколько точек нужно.

        Returns:
            Массив из ``buckets`` значений 0..1.
        """
        if self.frames == 0 or buckets <= 0:
            return np.zeros(max(1, buckets), dtype=np.float32)
        mono = np.abs(self.samples).mean(axis=1)
        edges = np.linspace(0, mono.size, buckets + 1, dtype=np.int64)
        out = np.empty(buckets, dtype=np.float32)
        for i in range(buckets):
            a, b = edges[i], max(edges[i] + 1, edges[i + 1])
            out[i] = float(mono[a:b].max()) if b > a else 0.0
        peak = float(out.max()) or 1.0
        return out / peak


def _ffmpeg_exe() -> Optional[str]:
    """Найти ffmpeg: конфиг → PATH → пакет imageio-ffmpeg."""
    cfg = get_config()
    if cfg.video.ffmpeg_path and Path(cfg.video.ffmpeg_path).exists():
        return cfg.video.ffmpeg_path
    found = shutil.which("ffmpeg")
    if found:
        return found
    try:
        import imageio_ffmpeg

        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:  # pragma: no cover - зависит от окружения
        return None


def load_audio(path: str | Path, sample_rate: int = 44100,
               channels: int = 2, max_seconds: Optional[float] = None) -> AudioBuffer:
    """Декодировать аудиофайл в :class:`AudioBuffer`.

    Порядок попыток: ``soundfile`` → ``ffmpeg`` (через временный WAV) → ``pydub``.

    Args:
        path: Путь к файлу.
        sample_rate: Целевая частота дискретизации.
        channels: Целевое число каналов.
        max_seconds: Обрезать результат (для быстрых превью).

    Returns:
        Готовый буфер нужной частоты и канальности.

    Raises:
        RuntimeError: Если файл не удалось декодировать ни одним способом.
    """
    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")

    duration_limit = max_seconds
    data: Optional[np.ndarray] = None
    sr = sample_rate

    try:
        import soundfile as sf

        info = sf.info(str(src))
        frames = -1 if duration_limit is None else int(duration_limit * info.samplerate)
        data, sr = sf.read(str(src), frames=frames, always_2d=True, dtype="float32")
    except Exception as exc:  # soundfile не читает mp3 без libsndfile >= 1.1 и т.п.
        LOG.debug("soundfile не смог прочитать %s: %s", src, exc)

    if data is None:
        ffmpeg = _ffmpeg_exe()
        if not ffmpeg:
            raise RuntimeError("Не найден ffmpeg — без него нельзя прочитать этот формат")
        with tempfile.TemporaryDirectory(prefix="bfs_dec_") as tmp:
            out_path = Path(tmp) / "decoded.wav"
            cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", str(src)]
            if duration_limit:
                cmd += ["-t", f"{duration_limit:.3f}"]
            cmd += ["-vn", "-ac", str(channels), "-ar", str(sample_rate), "-f", "wav", str(out_path)]
            proc = subprocess.run(cmd, capture_output=True, text=True)
            if proc.returncode != 0 or not out_path.exists():
                raise RuntimeError(f"ffmpeg не смог декодировать {src.name}: {proc.stderr.strip()[:300]}")
            import soundfile as sf

            data, sr = sf.read(str(out_path), always_2d=True, dtype="float32")

    buffer = AudioBuffer(np.asarray(data, dtype=np.float32), int(sr), src)
    if buffer.sample_rate != sample_rate:
        buffer = buffer.resample(sample_rate)
    if buffer.channels != channels:
        buffer = buffer.to_channels(channels)
    if duration_limit is not None and buffer.duration > duration_limit:
        buffer = buffer.slice(0.0, duration_limit)
    return AudioBuffer(buffer.samples, buffer.sample_rate, src)


# ============================================================
#  Источники звука
# ============================================================
class AudioSource(Protocol):
    """Интерфейс источника, который умеет отдавать кадры по запросу."""

    def read(self, start_frame: int, count: int) -> np.ndarray:
        """Вернуть ``count`` кадров начиная с ``start_frame`` (тишина за концом)."""\

    @property
    def length_frames(self) -> int:
        """Полная длина источника в кадрах."""\


class BufferSource:
    """Источник на основе готового :class:`AudioBuffer`."""

    def __init__(self, buffer: AudioBuffer, gain: float = 1.0) -> None:
        """Args:
            buffer: Аудиоданные.
            gain: Линейный коэффициент усиления.
        """
        self._buffer = buffer
        self.gain = float(gain)

    @property
    def length_frames(self) -> int:
        """Длина буфера в кадрах."""
        return self._buffer.frames

    @property
    def channels(self) -> int:
        """Число каналов буфера."""
        return self._buffer.channels

    def read(self, start_frame: int, count: int) -> np.ndarray:
        """Прочитать кадры с нулями за пределами буфера."""
        out = np.zeros((count, self.channels), dtype=np.float32)
        data = self._buffer.samples
        if start_frame >= self._buffer.frames:
            return out
        end = min(self._buffer.frames, start_frame + count)
        chunk = data[max(0, start_frame):end]
        out[: chunk.shape[0]] = chunk * self.gain
        return out


class SilentSource:
    """Пустой источник (позволяет «играть» без загруженного файла)."""

    def __init__(self, channels: int = 2, length_frames: int = 0) -> None:
        self._channels = channels
        self._length = int(length_frames)

    @property
    def length_frames(self) -> int:
        """Длина источника (может быть 0)."""
        return self._length

    def read(self, start_frame: int, count: int) -> np.ndarray:
        """Всегда тишина."""
        return np.zeros((count, self._channels), dtype=np.float32)


# ============================================================
#  Движок воспроизведения
# ============================================================
class AudioEngine(QObject):
    """Транспорт воспроизведения с realtime-микшированием.

    Сигналы:
        positionChanged(float): Позиция в секундах (≈30 Гц, из GUI-таймера).
        stateChanged(str): ``"playing"`` | ``"paused"`` | ``"stopped"``.
        finished(): Дошли до конца источника.
        errorOccurred(str): Текст ошибки для показа пользователю.
        levelChanged(float): Пиковый уровень мастера 0..1 (для индикатора).
    """

    positionChanged = pyqtSignal(float)
    stateChanged = pyqtSignal(str)
    finished = pyqtSignal()
    errorOccurred = pyqtSignal(str)
    levelChanged = pyqtSignal(float)

    def __init__(self, parent: Optional[QObject] = None) -> None:
        """Создать движок и попытаться открыть аудиоустройство.

        Args:
            parent: Родительский QObject.
        """
        super().__init__(parent)
        cfg = get_config().audio
        self._sr: int = int(cfg.sample_rate)
        self._block: int = int(cfg.block_size)
        self._channels: int = int(cfg.channels)
        self._volume: float = float(cfg.master_volume)
        self._speed: float = float(cfg.speed)
        self._source: AudioSource = SilentSource(self._channels)
        self._source_frames: int = 0
        self._pos: float = 0.0  # позиция в кадрах источника (float!)
        self._state: str = "stopped"
        self._lock = threading.RLock()
        self._current_gain: float = 1.0
        self._fade_in: int = 0  # кадров осталось для кроссфейда
        self._last_block: Optional[np.ndarray] = None
        self._stream = None
        self._device_ok = False
        self._sounddevice = None
        self._load_sounddevice()

        self._timer = QTimer(self)
        self._timer.setInterval(33)
        self._timer.timeout.connect(self._emit_position)

        self._set_state("stopped")

    # ------------------------------------------------------------ устройство
    def _load_sounddevice(self) -> None:
        """Импортировать sounddevice и открыть OutputStream (если возможно)."""
        try:
            import sounddevice as sd  # noqa: WPS433 (ленивый импорт)

            self._sounddevice = sd
        except Exception as exc:  # PortAudio отсутствует и т.п.
            LOG.warning("sounddevice недоступен, работаю в немом режиме: %s", exc)
            self._device_ok = False
            return

        try:
            device = get_config().audio.device or None
            self._stream = sd.OutputStream(
                samplerate=self._sr,
                blocksize=self._block,
                channels=self._channels,
                dtype="float32",
                device=device,
                latency=get_config().audio.latency,
                callback=self._callback,
            )
            self._stream.start()
            self._device_ok = True
            LOG.info("Аудиовыход открыт: %s Гц, %s каналов, блок %s",
                     self._sr, self._channels, self._block)
        except Exception as exc:
            LOG.warning("Не удалось открыть аудиовыход: %s", exc)
            self._device_ok = False

    @property
    def device_available(self) -> bool:
        """Есть ли реальный аудиовыход (False — немой режим)."""
        return self._device_ok

    # ------------------------------------------------------------ callback
    def _callback(self, outdata: np.ndarray, frames: int, time_info, status) -> None:  # noqa: ANN001
        """Callback PortAudio: заполняет буфер миксами источника."""
        if status:
            LOG.debug("audio status: %s", status)
        try:
            with self._lock:
                if self._state != "playing" or self._source is None:
                    outdata.fill(0.0)
                    return
                speed = self._speed
                need = int(math.ceil(frames * speed)) + 2
                start = int(self._pos)
                chunk = self._source.read(start, need)

                if speed != 1.0 and chunk.shape[0] > 1:
                    idx = np.arange(frames, dtype=np.float64) * speed
                    src = chunk[:, 0]
                    out = np.empty((frames, chunk.shape[1]), dtype=np.float32)
                    xs = np.arange(chunk.shape[0], dtype=np.float64)
                    for ch in range(chunk.shape[1]):
                        out[:, ch] = np.interp(idx, xs, chunk[:, ch]).astype(np.float32)
                    del src
                    chunk = out
                else:
                    chunk = chunk[:frames]
                    if chunk.shape[0] < frames:
                        pad = np.zeros((frames - chunk.shape[0], chunk.shape[1]), dtype=np.float32)
                        chunk = np.vstack([chunk, pad])

                # плавная громкость (без щелчков)
                target = self._volume
                if self._fade_in > 0:
                    ramp = np.linspace(0.0, 1.0, min(frames, self._fade_in), dtype=np.float32)
                    gain_arr = np.ones((frames, 1), dtype=np.float32)
                    gain_arr[: ramp.size, 0] = ramp
                    self._fade_in -= frames
                    chunk = chunk * gain_arr * target
                else:
                    chunk = chunk * target

                if chunk.shape[1] != self._channels:
                    chunk = self._fit_channels(chunk, frames)

                outdata[:] = np.clip(chunk, -1.0, 1.0)
                peak = float(np.max(np.abs(outdata))) if frames else 0.0
                self._last_level = peak
                self._pos += frames * speed
                if self._source_frames and self._pos >= self._source_frames:
                    self._pos = float(self._source_frames)
                    self._state = "paused"
                    self._pending_finish = True
        except Exception:  # никогда не выбрасываем исключение из callback
            LOG.exception("Ошибка в аудио-callback")
            outdata.fill(0.0)

    def _fit_channels(self, chunk: np.ndarray, frames: int) -> np.ndarray:
        """Подогнать число каналов чанка к выходному."""
        ch = chunk.shape[1]
        if ch == self._channels:
            return chunk
        if ch == 1:
            return np.repeat(chunk, self._channels, axis=1)
        if ch > self._channels:
            return chunk[:, : self._channels]
        pad = np.repeat(chunk[:, -1:], self._channels - ch, axis=1)
        return np.hstack([chunk, pad])

    # ------------------------------------------------------------ источник
    def set_source(self, source: AudioSource, keep_position: bool = False) -> None:
        """Назначить источник воспроизведения.

        Args:
            source: Объект с методом ``read``.
            keep_position: Сохранить текущую позицию (если возможно).
        """
        with self._lock:
            old_len = self._source_frames
            self._source = source
            self._source_frames = int(getattr(source, "length_frames", 0))
            if not keep_position or self._pos > self._source_frames:
                self._pos = 0.0
            LOG.debug("Источник заменён: %s кадров (было %s)", self._source_frames, old_len)

    def load_file(self, path: str | Path) -> AudioBuffer:
        """Загрузить файл и начать использовать его как источник.

        Args:
            path: Путь к аудиофайлу.

        Returns:
            Декодированный буфер (для waveform и метаданных).
        """
        buffer = load_audio(path, self._sr, self._channels)
        self.set_source(BufferSource(buffer))
        return buffer

    def set_buffer(self, buffer: AudioBuffer) -> None:
        """Использовать готовый буфер как источник."""
        buf = buffer
        if buf.sample_rate != self._sr:
            buf = buf.resample(self._sr)
        if buf.channels != self._channels:
            buf = buf.to_channels(self._channels)
        self.set_source(BufferSource(buf))

    # ------------------------------------------------------------ транспорт
    @property
    def duration(self) -> float:
        """Длительность текущего источника, сек."""
        return self._source_frames / float(self._sr)

    @property
    def position(self) -> float:
        """Текущая позиция, сек."""
        return self._pos / float(self._sr)

    @property
    def state(self) -> str:
        """``playing`` | ``paused`` | ``stopped``."""
        return self._state

    @property
    def is_playing(self) -> bool:
        """Идёт ли воспроизведение."""
        return self._state == "playing"

    def play(self) -> None:
        """Начать/продолжить воспроизведение."""
        if self._source_frames == 0:
            self.errorOccurred.emit("Нет загруженного аудио")
            return
        with self._lock:
            if self._pos >= self._source_frames:
                self._pos = 0.0
            self._fade_in = int(self._sr * get_config().audio.seek_crossfade_ms / 1000)
            self._set_state("playing")
        self._timer.start()
        self._ensure_stream()

    def pause(self) -> None:
        """Поставить на паузу."""
        with self._lock:
            if self._state == "playing":
                self._set_state("paused")
        self._timer.stop()

    def toggle(self) -> None:
        """Переключить play/pause."""
        if self.is_playing:
            self.pause()
        else:
            self.play()

    def stop(self) -> None:
        """Остановить и вернуть позицию в начало."""
        with self._lock:
            self._set_state("stopped")
            self._pos = 0.0
        self._timer.stop()
        self.positionChanged.emit(0.0)

    def seek(self, seconds: float) -> None:
        """Перемотать на позицию с кроссфейдом (без щелчков).

        Args:
            seconds: Позиция в секундах; обрезается по границам источника.
        """
        target = max(0.0, min(float(seconds), self.duration))
        with self._lock:
            self._pos = target * self._sr
            self._fade_in = int(self._sr * get_config().audio.seek_crossfade_ms / 1000)
        self.positionChanged.emit(target)

    def seek_relative(self, delta: float) -> None:
        """Перемотать на ``delta`` секунд относительно текущей позиции."""
        self.seek(self.position + delta)

    def skip(self, direction: int = 1, step: float = 5.0) -> None:
        """Перейти на ``step`` секунд вперёд/назад."""
        self.seek(self.position + direction * step)

    # ------------------------------------------------------------ параметры
    @property
    def volume(self) -> float:
        """Громкость мастера 0..1."""
        return self._volume

    def set_volume(self, value: float) -> None:
        """Установить громкость мастера (0..1, плавно в callback)."""
        self._volume = float(max(0.0, min(1.0, value)))

    @property
    def speed(self) -> float:
        """Скорость воспроизведения."""
        return self._speed

    def set_speed(self, value: float) -> None:
        """Установить скорость 0.25..2.0 (изменяет высоту тона, «ленточный» эффект)."""
        self._speed = float(max(0.25, min(2.0, value)))

    def set_speed_preserve_pitch(self, value: float) -> None:
        """Установить скорость с сохранением тональности (librosa, офлайн).

        Буфер предварительно растягивается в фоновом потоке — на время обработки
        воспроизведение ставится на паузу.
        """
        value = float(max(0.25, min(2.0, value)))
        source = self._source
        if not isinstance(source, BufferSource):
            LOG.info("Источник не буфер — меняю скорость без сохранения тона")
            self.set_speed(value)
            return

        def work() -> None:
            try:
                import librosa

                data = source._buffer.samples  # noqa: SLF001 - внутренний доступ по делу
                stretched = np.stack(
                    [
                        librosa.effects.time_stretch(data[:, ch], rate=value)
                        for ch in range(data.shape[1])
                    ],
                    axis=1,
                )
                buf = AudioBuffer(stretched.astype(np.float32), self._sr)
                self.set_source(BufferSource(buf))
                self.set_speed(1.0)  # новая скорость уже «вшита» в буфер
                LOG.info("Тональность сохранена при скорости x%.2f", value)
            except Exception as exc:
                LOG.exception("Не удалось растянуть без изменения тона")
                self.errorOccurred.emit(f"Time-stretch недоступен: {exc}")

        threading.Thread(target=work, name="bfs-timestretch", daemon=True).start()

    def set_sample_rate(self, sample_rate: int) -> None:
        """Сменить частоту дискретизации выхода (переоткрывает устройство)."""
        if sample_rate == self._sr:
            return
        self._close_stream()
        self._sr = int(sample_rate)
        self._load_sounddevice()

    # ------------------------------------------------------------ внутреннее
    def _set_state(self, state: str) -> None:
        if state != self._state:
            self._state = state
            self.stateChanged.emit(state)

    def _ensure_stream(self) -> None:
        """Переоткрыть поток, если устройство было потеряно."""
        if self._device_ok or self._sounddevice is None:
            return
        self._load_sounddevice()

    def _emit_position(self) -> None:
        """Таймер GUI: отправить позицию/уровень, обработать конец трека."""
        self.positionChanged.emit(self.position)
        level = getattr(self, "_last_level", 0.0)
        self.levelChanged.emit(min(1.0, max(0.0, level)))
        if getattr(self, "_pending_finish", False):
            self._pending_finish = False
            self._timer.stop()
            self._set_state("stopped")
            self.finished.emit()

    def _close_stream(self) -> None:
        if self._stream is not None:
            try:
                self._stream.stop()
                self._stream.close()
            except Exception:
                LOG.debug("Ошибка закрытия потока", exc_info=True)
            self._stream = None
            self._device_ok = False

    def shutdown(self) -> None:
        """Остановить воспроизведение и закрыть аудиоустройство."""
        self._timer.stop()
        with self._lock:
            self._state = "stopped"
            self._source = SilentSource(self._channels)
            self._source_frames = 0
        self._close_stream()
        LOG.info("Аудиодвижок остановлен")


# ============================================================
#  Превью из сети
# ============================================================
class PreviewManager(QObject):
    """Скачивает первые секунды трека и проигрывает их через :class:`AudioEngine`.

    Скачивание выполняется в отдельном потоке (``threading``), сигналы Qt
    доставляются в главный поток автоматически (queued connection).

    Сигналы:
        previewStarted(str): URL, который начали грузить.
        previewReady(str): Путь к временному файлу превью.
        previewFailed(str, str): URL и текст ошибки.
        previewFinished(): Превью доиграло/остановлено.
    """

    previewStarted = pyqtSignal(str)
    previewReady = pyqtSignal(str)
    previewFailed = pyqtSignal(str, str)
    previewFinished = pyqtSignal()

    def __init__(self, engine: AudioEngine, parent: Optional[QObject] = None) -> None:
        """Args:
            engine: Движок, через который играется превью.
            parent: Родительский QObject.
        """
        super().__init__(parent)
        self._engine = engine
        self._url: Optional[str] = None
        self._buffer: Optional[AudioBuffer] = None
        self._tempdir: Optional[tempfile.TemporaryDirectory] = None
        self._seconds = int(get_config().search.preview_seconds)
        self._engine.finished.connect(self.previewFinished)

    @property
    def seconds(self) -> int:
        """Длительность превью, сек."""
        return self._seconds

    def set_seconds(self, seconds: int) -> None:
        """Изменить длительность превью."""
        self._seconds = max(5, min(120, int(seconds)))

    def play_url(self, url: str) -> None:
        """Скачать первые секунды трека и запустить воспроизведение.

        Args:
            url: Страница трека на YouTube/SoundCloud.
        """
        if not url:
            return
        if self._url == url and self._buffer is not None:
            self._engine.set_buffer(self._buffer)
            self._engine.play()
            return
        self._url = url
        self.previewStarted.emit(url)
        threading.Thread(target=self._download_and_play, args=(url,),
                         name="bfs-preview", daemon=True).start()

    def _download_and_play(self, url: str) -> None:
        """Фоновый поток: yt-dlp качает только начало трека во временный файл."""
        try:
            import yt_dlp

            self._cleanup()
            self._tempdir = tempfile.TemporaryDirectory(prefix="bfs_prev_")
            cfg = get_config().search
            opts = {
                "quiet": True,
                "no_warnings": True,
                "format": "bestaudio/best",
                "outtmpl": str(Path(self._tempdir.name) / "preview.%(ext)s"),
                "noplaylist": True,
                "download_ranges": None,
                "postprocessors": [
                    {"key": "FFmpegExtractAudio", "preferredcodec": "wav"},
                ],
                "external_downloader": None,
                "ffmpeg_location": _ffmpeg_exe(),
            }
            # скачиваем только первые секунды (yt-dlp >= 2023.10 поддерживает секции)
            opts["download_ranges"] = lambda info, ydl: [{"start_time": 0.0,
                                                          "end_time": float(self._seconds)}]
            if cfg.proxy:
                opts["proxy"] = cfg.proxy
            if cfg.cookies_browser:
                opts["cookiesfrombrowser"] = (cfg.cookies_browser,)

            with yt_dlp.YoutubeDL(opts) as ydl:
                ydl.download([url])

            files = sorted(Path(self._tempdir.name).glob("preview*"))
            if not files:
                raise RuntimeError("Не удалось получить аудиопоток")
            path = files[0]
            buffer = load_audio(path, sample_rate=self._engine._sr, channels=self._engine._channels)  # noqa: SLF001
            self._buffer = buffer
            self._engine.set_buffer(buffer)
            self._engine.play()
            self.previewReady.emit(str(path))
        except Exception as exc:
            LOG.exception("Ошибка превью: %s", exc)
            self.previewFailed.emit(url, str(exc))

    def stop(self) -> None:
        """Остановить превью."""
        self._engine.stop()
        self.previewFinished.emit()

    def _cleanup(self) -> None:
        if self._tempdir is not None:
            try:
                self._tempdir.cleanup()
            except Exception:
                LOG.debug("Не удалось удалить временные файлы превью", exc_info=True)
            self._tempdir = None
        self._buffer = None


# ============================================================
#  Утилиты смешивания
# ============================================================
def mix_buffers(buffers: List[AudioBuffer], sample_rate: int = 44100,
                channels: int = 2) -> AudioBuffer:
    """Смешать несколько буферов в один (без нормализации).

    Args:
        buffers: Список буферов (разной длины/частоты — допускается).
        sample_rate: Частота результата.
        channels: Каналы результата.

    Returns:
        Суммарный буфер.
    """
    if not buffers:
        return AudioBuffer(np.zeros((0, channels), dtype=np.float32), sample_rate)
    prepared = [b.resample(sample_rate).to_channels(channels) for b in buffers]
    total = max(b.frames for b in prepared)
    out = np.zeros((total, channels), dtype=np.float32)
    for buf in prepared:
        out[: buf.frames] += buf.samples
    return AudioBuffer(out, sample_rate)


def normalize_buffer(buffer: AudioBuffer, target_db: float = -1.0) -> AudioBuffer:
    """Нормализовать буфер по пику.

    Args:
        buffer: Исходный буфер.
        target_db: Целевой пик в dBFS (например, -1.0).

    Returns:
        Новый буфер с пиком ``target_db`` (или исходный, если он тише).
    """
    peak = buffer.peak()
    if peak <= 1e-6:
        return buffer
    target = 10 ** (target_db / 20.0)
    factor = target / peak
    if factor <= 1.0:
        return AudioBuffer(buffer.samples.copy(), buffer.sample_rate, buffer.path)
    return AudioBuffer((buffer.samples * factor).astype(np.float32), buffer.sample_rate, buffer.path)


def format_time(seconds: float, with_ms: bool = False) -> str:
    """Отформатировать секунды как ``MM:SS`` или ``MM:SS.mmm``.

    Args:
        seconds: Время в секундах.
        with_ms: Показывать миллисекунды.

    Returns:
        Строка времени.
    """
    seconds = max(0.0, float(seconds))
    minutes, sec = divmod(int(seconds), 60)
    hours, minutes = divmod(minutes, 60)
    prefix = f"{hours:d}:" if hours else ""
    if with_ms:
        ms = int(round((seconds - int(seconds)) * 1000))
        return f"{prefix}{minutes:02d}:{sec:02d}.{ms:03d}"
    return f"{prefix}{minutes:02d}:{sec:02d}"
