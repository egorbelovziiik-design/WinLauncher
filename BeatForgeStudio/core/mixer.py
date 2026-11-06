"""Мультитрек-микшер: дорожки с клипами, громкостью, mute/solo и эффектами.

``MixerProject`` — модель проекта аудиоредактора (до 8 дорожек), умеет:

* добавлять клипы (:class:`MixerClip`) с офсетом на таймлайне;
* миксовать всё в один буфер (:meth:`MixerProject.render`) с учётом громкости,
  mute/solo, панорамы, fade и цепочек эффектов;
* экспортировать дорожки отдельно (stems);
* отдавать транспортный источник для real-time воспроизведения
  (:meth:`MixerProject.build_source`).

Модель полностью сериализуема в JSON — используется проектами (.bfs).
"""

from __future__ import annotations

import logging
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

from core.audio_engine import AudioBuffer, BufferSource, load_audio, normalize_buffer
from core.effects_audio import AudioEffect, EffectChain
from utils.config import get_config

LOG = logging.getLogger(__name__)

#: Палитра дорожек (по кругу).
TRACK_COLORS = ("#FF6600", "#00BFFF", "#8A7CFF", "#3FBF6F",
                "#E8A33D", "#E85A5A", "#00C2A8", "#B07CFF")


@dataclass
class FadeSpec:
    """Fade in/out для клипа.

    Attributes:
        fade_in: Длительность нарастания, сек.
        fade_out: Длительность затухания, сек.
        curve: ``linear`` | ``exp`` | ``log``.
    """

    fade_in: float = 0.0
    fade_out: float = 0.0
    curve: str = "linear"


@dataclass
class MixerClip:
    """Клип на аудиодорожке.

    Attributes:
        id: Уникальный id.
        path: Файл-источник.
        start: Начало на таймлайне, сек.
        duration: Длительность, сек.
        source_start: Смещение внутри файла, сек.
        gain: Громкость клипа 0..2.
        fade: Fade-параметры.
        effects: Цепочка эффектов именно этого клипа.
        name: Подпись.
        reversed: Проигрывать задом наперёд.
    """

    id: str
    path: str
    start: float = 0.0
    duration: float = 0.0
    source_start: float = 0.0
    gain: float = 1.0
    fade: FadeSpec = field(default_factory=FadeSpec)
    effects: List[AudioEffect] = field(default_factory=list)
    name: str = ""
    reversed: bool = False
    _buffer: Optional[AudioBuffer] = field(default=None, repr=False, compare=False)

    @property
    def end(self) -> float:
        """Конец клипа, сек."""
        return self.start + self.duration

    def load(self, sample_rate: int, channels: int) -> AudioBuffer:
        """Ленивая загрузка и подготовка буфера клипа (с эффектами).

        Args:
            sample_rate: Частота проекта.
            channels: Каналы проекта.

        Returns:
            Буфер нужной длительности и с применёнными эффектами.

        Raises:
            RuntimeError: если файл не читается.
        """
        if self._buffer is None:
            buffer = load_audio(self.path, sample_rate, channels)
            if self.reversed:
                buffer = AudioBuffer(np.ascontiguousarray(buffer.samples[::-1]), sample_rate,
                                     buffer.path)
            self._buffer = buffer
        buffer = self._buffer
        if self.effects:
            chain = EffectChain(self.effects)
            buffer = chain.process(buffer)
        start = int(max(0.0, self.source_start) * sample_rate)
        end = start + int(max(0.01, self.duration) * sample_rate)
        if end > buffer.frames:
            end = buffer.frames
            self.duration = max(0.01, (end - start) / sample_rate)
        sliced = buffer.samples[start:end].copy()
        return AudioBuffer(sliced, sample_rate, buffer.path)

    def invalidate(self) -> None:
        """Сбросить кэш буфера (после смены эффектов/скорости)."""
        self._buffer = None

    def to_dict(self) -> Dict[str, object]:
        """Сериализация клипа."""
        return {
            "id": self.id, "path": self.path, "start": self.start, "duration": self.duration,
            "source_start": self.source_start, "gain": self.gain, "name": self.name,
            "reversed": self.reversed,
            "fade": {"fade_in": self.fade.fade_in, "fade_out": self.fade.fade_out,
                     "curve": self.fade.curve},
            "effects": [e.to_dict() for e in self.effects],
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "MixerClip":
        """Восстановить клип из проекта."""
        fade_data = data.get("fade") or {}
        return cls(
            id=str(data.get("id", "")),
            path=str(data.get("path", "")),
            start=float(data.get("start", 0.0)),
            duration=float(data.get("duration", 0.0)),
            source_start=float(data.get("source_start", 0.0)),
            gain=float(data.get("gain", 1.0)),
            name=str(data.get("name", "")),
            reversed=bool(data.get("reversed", False)),
            fade=FadeSpec(float(fade_data.get("fade_in", 0.0)),
                          float(fade_data.get("fade_out", 0.0)),
                          str(fade_data.get("curve", "linear"))),
            effects=[AudioEffect.from_dict(e) for e in (data.get("effects") or [])],
        )


@dataclass
class MixerTrack:
    """Аудиодорожка микшера.

    Attributes:
        index: Индекс дорожки (0-based).
        name: Название.
        volume: Громкость 0..1.
        pan: Панорама -1 (лево) … 1 (право).
        mute: Выключена.
        solo: Соло.
        color: Цвет.
        clips: Клипы.
        effects: Цепочка эффектов дорожки.
        armed: Готова к записи (для будущей записи).
    """

    index: int
    name: str = ""
    volume: float = 0.8
    pan: float = 0.0
    mute: bool = False
    solo: bool = False
    color: str = "#FF6600"
    clips: List[MixerClip] = field(default_factory=list)
    effects: List[AudioEffect] = field(default_factory=list)
    armed: bool = False

    @property
    def end(self) -> float:
        """Конец последнего клипа, сек."""
        return max((c.end for c in self.clips), default=0.0)

    def to_dict(self) -> Dict[str, object]:
        """Сериализация дорожки."""
        return {
            "index": self.index, "name": self.name, "volume": self.volume, "pan": self.pan,
            "mute": self.mute, "solo": self.solo, "color": self.color, "armed": self.armed,
            "effects": [e.to_dict() for e in self.effects],
            "clips": [c.to_dict() for c in self.clips],
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "MixerTrack":
        """Восстановить дорожку из проекта."""
        return cls(
            index=int(data.get("index", 0)),
            name=str(data.get("name", "")),
            volume=float(data.get("volume", 0.8)),
            pan=float(data.get("pan", 0.0)),
            mute=bool(data.get("mute", False)),
            solo=bool(data.get("solo", False)),
            color=str(data.get("color", "#FF6600")),
            armed=bool(data.get("armed", False)),
            effects=[AudioEffect.from_dict(e) for e in (data.get("effects") or [])],
            clips=[MixerClip.from_dict(c) for c in (data.get("clips") or [])],
        )


class MixerProject:
    """Модель мультитрек-проекта (аудио)."""

    def __init__(self, name: str = "Без названия", max_tracks: Optional[int] = None) -> None:
        """Args:
            name: Название проекта.
            max_tracks: Максимум дорожек (по умолчанию из конфига).
        """
        cfg = get_config().audio
        self.name = name
        self.sample_rate = cfg.sample_rate
        self.channels = cfg.channels
        self.master_volume = cfg.master_volume
        self.max_tracks = int(max_tracks if max_tracks is not None else cfg.max_audio_tracks)
        self.tracks: List[MixerTrack] = []
        self.master_effects: List[AudioEffect] = []
        self._id_counter = 0
        self._lock = threading.RLock()
        self.bpm: Optional[float] = None
        self.ensure_tracks(2)

    # ================================================================ дорожки
    def ensure_tracks(self, count: int) -> None:
        """Гарантировать наличие ``count`` дорожек."""
        while len(self.tracks) < min(count, self.max_tracks):
            self.add_track()

    def add_track(self, name: str = "") -> Optional[MixerTrack]:
        """Добавить дорожку.

        Returns:
            Созданная дорожка или None, если лимит достигнут.
        """
        with self._lock:
            if len(self.tracks) >= self.max_tracks:
                LOG.info("Достигнут лимит дорожек: %s", self.max_tracks)
                return None
            index = len(self.tracks)
            track = MixerTrack(index=index, name=name or f"Дорожка {index + 1}",
                               color=TRACK_COLORS[index % len(TRACK_COLORS)])
            self.tracks.append(track)
            return track

    def remove_track(self, index: int) -> None:
        """Удалить дорожку и переиндексировать остальные."""
        with self._lock:
            if not (0 <= index < len(self.tracks)):
                return
            self.tracks.pop(index)
            for i, track in enumerate(self.tracks):
                track.index = i

    def track(self, index: int) -> Optional[MixerTrack]:
        """Дорожка по индексу."""
        return self.tracks[index] if 0 <= index < len(self.tracks) else None

    # ================================================================ клипы
    def _next_id(self) -> str:
        self._id_counter += 1
        return f"clip{self._id_counter}"

    def add_clip(self, track_index: int, path: str, start: float = 0.0,
                 duration: Optional[float] = None, name: str = "") -> Optional[MixerClip]:
        """Добавить клип на дорожку.

        Args:
            track_index: Индекс дорожки.
            path: Путь к аудиофайлу.
            start: Начало на таймлайне, сек.
            duration: Длительность (по умолчанию — вся длина файла).
            name: Подпись.

        Returns:
            Клип или None при ошибке.
        """
        track = self.track(track_index)
        if track is None:
            return None
        if not Path(path).exists():
            LOG.warning("Файл не найден: %s", path)
            return None
        try:
            buffer = load_audio(path, self.sample_rate, self.channels)
        except Exception as exc:
            LOG.error("Не удалось прочитать %s: %s", path, exc)
            return None
        clip = MixerClip(
            id=self._next_id(), path=str(path), start=max(0.0, start),
            duration=float(duration if duration is not None else buffer.duration),
            name=name or Path(path).stem, _buffer=buffer,
        )
        track.clips.append(clip)
        return clip

    def remove_clip(self, clip_id: str) -> bool:
        """Удалить клип по id."""
        for track in self.tracks:
            for clip in list(track.clips):
                if clip.id == clip_id:
                    track.clips.remove(clip)
                    return True
        return False

    def find_clip(self, clip_id: str) -> Optional[MixerClip]:
        """Найти клип по id."""
        for track in self.tracks:
            for clip in track.clips:
                if clip.id == clip_id:
                    return clip
        return None

    def split_clip(self, clip_id: str, at: float) -> Optional[MixerClip]:
        """Разрезать клип в момент времени таймлайна ``at``.

        Args:
            clip_id: Клип.
            at: Время разреза на таймлайне, сек.

        Returns:
            Новая правая часть клипа (или None, если разрез невозможен).
        """
        clip = self.find_clip(clip_id)
        if clip is None or not (clip.start < at < clip.end):
            return None
        track = next((t for t in self.tracks if clip in t.clips), None)
        if track is None:
            return None
        offset = at - clip.start
        right = MixerClip(
            id=self._next_id(), path=clip.path, start=at,
            duration=max(0.01, clip.duration - offset),
            source_start=clip.source_start + offset, gain=clip.gain,
            name=clip.name, reversed=clip.reversed,
            effects=[AudioEffect(e.kind, dict(e.params), e.bypass, e.name) for e in clip.effects],
        )
        clip.duration = max(0.01, offset)
        clip.invalidate()
        right.invalidate()
        track.clips.append(right)
        track.clips.sort(key=lambda c: c.start)
        return right

    def duplicate_clip(self, clip_id: str) -> Optional[MixerClip]:
        """Дублировать клип сразу после оригинала."""
        clip = self.find_clip(clip_id)
        if clip is None:
            return None
        track = next((t for t in self.tracks if clip in t.clips), None)
        if track is None:
            return None
        copy = MixerClip.from_dict(clip.to_dict())
        copy.id = self._next_id()
        copy.start = clip.end
        track.clips.append(copy)
        track.clips.sort(key=lambda c: c.start)
        return copy

    # ================================================================ свойства
    def _any_solo(self) -> bool:
        return any(t.solo for t in self.tracks)

    def set_volume(self, track_index: int, volume: float) -> None:
        """Установить громкость дорожки 0..1."""
        track = self.track(track_index)
        if track:
            track.volume = float(max(0.0, min(1.0, volume)))

    def set_mute(self, track_index: int, muted: bool) -> None:
        """Включить/выключить mute дорожки."""
        track = self.track(track_index)
        if track:
            track.mute = bool(muted)

    def set_solo(self, track_index: int, solo: bool) -> None:
        """Включить/выключить solo дорожки."""
        track = self.track(track_index)
        if track:
            track.solo = bool(solo)

    def set_pan(self, track_index: int, pan: float) -> None:
        """Панорама дорожки -1..1."""
        track = self.track(track_index)
        if track:
            track.pan = float(max(-1.0, min(1.0, pan)))

    @property
    def duration(self) -> float:
        """Длительность проекта, сек."""
        return max((t.end for t in self.tracks), default=0.0)

    # ================================================================ рендер
    def render(self, start: float = 0.0, end: Optional[float] = None,
               normalize_result: Optional[float] = None,
               progress_cb=None) -> AudioBuffer:
        """Смикшировать проект в один буфер.

        Args:
            start: Начало рендера, сек.
            end: Конец рендера (по умолчанию — конец проекта).
            normalize_result: Целевой пик в dBFS (None — без нормализации).
            progress_cb: Колбэк ``(процент, статус)``.

        Returns:
            Готовый стерео-буфер.
        """
        sr, ch = self.sample_rate, self.channels
        end = float(end if end is not None else self.duration)
        total_frames = max(1, int((end - start) * sr))
        mix = np.zeros((total_frames, ch), dtype=np.float32)
        solo_active = self._any_solo()
        tracks = [t for t in self.tracks if t.clips]
        total = max(1, len(tracks))

        for i, track in enumerate(tracks):
            if track.mute or (solo_active and not track.solo):
                if progress_cb:
                    progress_cb(int((i + 1) / total * 100), f"пропуск {track.name}")
                continue
            track_buffer = np.zeros((total_frames, ch), dtype=np.float32)
            for clip in track.clips:
                try:
                    clip_buffer = clip.load(sr, ch)
                except Exception as exc:
                    LOG.error("Клип %s не загрузился: %s", clip.path, exc)
                    continue
                data = self._apply_clip_shape(clip_buffer.samples, clip, sr)
                offset = int((clip.start - start) * sr)
                src_begin = max(0, -offset)
                dst_begin = max(0, offset)
                length = min(data.shape[0] - src_begin, total_frames - dst_begin)
                if length <= 0:
                    continue
                track_buffer[dst_begin:dst_begin + length] += data[src_begin:src_begin + length]

            if track.effects:
                chain = EffectChain(track.effects)
                track_buffer = chain.process(
                    AudioBuffer(track_buffer, sr)).samples

            track_buffer = self._apply_pan(track_buffer, track.pan)
            mix += track_buffer * float(max(0.0, min(2.0, track.volume)))
            if progress_cb:
                progress_cb(int((i + 1) / total * 100), f"сведено: {track.name}")

        if self.master_effects:
            mix = EffectChain(self.master_effects).process(AudioBuffer(mix, sr)).samples

        mix *= float(self.master_volume)
        # мягкое ограничение, чтобы не клиппило
        peak = float(np.max(np.abs(mix))) if mix.size else 0.0
        if peak > 1.0:
            mix /= peak
        result = AudioBuffer(mix.astype(np.float32), sr)
        if normalize_result is not None:
            result = normalize_buffer(result, normalize_result)
        if progress_cb:
            progress_cb(100, "готово")
        return result

    def _apply_clip_shape(self, data: np.ndarray, clip: MixerClip,
                          sample_rate: int) -> np.ndarray:
        """Применить gain и fade к данным клипа."""
        out = data.copy()
        if clip.gain != 1.0:
            out *= float(clip.gain)
        fade = clip.fade
        if fade.fade_in > 0 or fade.fade_out > 0:
            frames = out.shape[0]
            if fade.fade_in > 0:
                n = min(frames, int(fade.fade_in * sample_rate))
                if n > 0:
                    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
                    if fade.curve == "exp":
                        ramp = ramp ** 2
                    elif fade.curve == "log":
                        ramp = np.sqrt(ramp)
                    out[:n] *= ramp[:, None]
            if fade.fade_out > 0:
                n = min(frames, int(fade.fade_out * sample_rate))
                if n > 0:
                    ramp = np.linspace(1.0, 0.0, n, dtype=np.float32)
                    if fade.curve == "exp":
                        ramp = ramp ** 2
                    elif fade.curve == "log":
                        ramp = np.sqrt(ramp)
                    out[-n:] *= ramp[:, None]
        return out

    @staticmethod
    def _apply_pan(data: np.ndarray, pan: float) -> np.ndarray:
        """Панорамирование стерео-буфера (constant-power)."""
        if abs(pan) < 1e-3 or data.shape[1] < 2:
            return data
        angle = (pan + 1.0) * np.pi / 4.0
        left, right = np.cos(angle), np.sin(angle)
        out = data.copy()
        out[:, 0] *= left * 1.4142
        out[:, 1] *= right * 1.4142
        return out

    def render_stems(self, out_dir: str | Path, fmt: str = "wav") -> List[Path]:
        """Отрендерить каждую дорожку отдельным файлом.

        Args:
            out_dir: Каталог для stems.
            fmt: Формат (``wav``/``flac``).

        Returns:
            Список путей.
        """
        import soundfile as sf

        target = Path(out_dir)
        target.mkdir(parents=True, exist_ok=True)
        paths: List[Path] = []
        solo_active = self._any_solo()
        for track in self.tracks:
            if not track.clips or track.mute or (solo_active and not track.solo):
                continue
            single = MixerProject(name=track.name, max_tracks=1)
            single.tracks = [MixerTrack(index=0, name=track.name, volume=track.volume,
                                        color=track.color, clips=track.clips,
                                        effects=track.effects)]
            buffer = single.render()
            out = target / f"{track.index + 1:02d}_{track.name or 'track'}.{fmt}".replace(" ", "_")
            sf.write(str(out), buffer.samples, buffer.sample_rate)
            paths.append(out)
        return paths

    # ================================================================ транспорт
    def build_source(self) -> BufferSource:
        """Собрать источник для real-time воспроизведения через :class:`AudioEngine`."""
        buffer = self.render()
        return BufferSource(buffer)

    # ================================================================ сериализация
    def to_dict(self) -> Dict[str, object]:
        """Сериализация проекта в словарь."""
        return {
            "kind": "audio",
            "name": self.name,
            "sample_rate": self.sample_rate,
            "channels": self.channels,
            "master_volume": self.master_volume,
            "bpm": self.bpm,
            "tracks": [t.to_dict() for t in self.tracks],
            "master_effects": [e.to_dict() for e in self.master_effects],
        }

    @classmethod
    def from_dict(cls, data: Dict[str, object]) -> "MixerProject":
        """Восстановить проект из словаря."""
        project = cls(name=str(data.get("name", "Проект")))
        project.sample_rate = int(data.get("sample_rate", project.sample_rate))
        project.channels = int(data.get("channels", project.channels))
        project.master_volume = float(data.get("master_volume", project.master_volume))
        bpm = data.get("bpm")
        project.bpm = float(bpm) if bpm else None
        project.tracks = [MixerTrack.from_dict(t) for t in (data.get("tracks") or [])]
        project.master_effects = [AudioEffect.from_dict(e)
                                  for e in (data.get("master_effects") or [])]
        return project
