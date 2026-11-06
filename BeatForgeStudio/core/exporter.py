"""Экспорт: аудио (MP3/WAV/FLAC/OGG), видео (MP4/WebM/MOV/GIF), загрузка ссылкой.

Ключевые элементы:

* :func:`export_audio` — из :class:`AudioBuffer` в файл с нормализацией,
  дитерингом и корректным битрейтом;
* :func:`export_video` — рендер :class:`core.video_engine.Scene`;
* :func:`upload_to_fileio`, :func:`upload_to_transfer` — экспорт ссылкой;
* :class:`ExportWorker` / :class:`UploadWorker` — Qt-потоки с прогрессом;
* :func:`unique_path`, :func:`build_filename` — безопасные имена файлов.

Все функции синхронные: их следует вызывать из фоновых потоков.
"""

from __future__ import annotations

import logging
import math
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Sequence, Tuple

import numpy as np

from core.audio_engine import AudioBuffer, normalize_buffer
from utils.config import get_config
from utils.ffmpeg_helper import ffmpeg_path, run

LOG = logging.getLogger(__name__)

#: Поддерживаемые форматы аудио: расширение → параметры.
AUDIO_FORMATS: Dict[str, Dict[str, Any]] = {
    "mp3": {"codec": "libmp3lame", "subtypes": {"bitrate": ("128k", "192k", "256k", "320k")}},
    "wav": {"codec": "pcm_s16le", "subtypes": {"bit_depth": (16, 24, 32)}},
    "flac": {"codec": "flac", "subtypes": {}},
    "ogg": {"codec": "libvorbis", "subtypes": {"quality": (4, 6, 8, 10)}},
    "m4a": {"codec": "aac", "subtypes": {"bitrate": ("128k", "192k", "256k")}},
}

#: Пресеты видео для экспорта.
VIDEO_PRESETS: Dict[str, Dict[str, Any]] = {
    "YouTube 1080p": {"width": 1920, "height": 1080, "fps": 30, "crf": 20, "codec": "libx264"},
    "YouTube 4K": {"width": 3840, "height": 2160, "fps": 30, "crf": 22, "codec": "libx264"},
    "TikTok HD": {"width": 1080, "height": 1920, "fps": 30, "crf": 20, "codec": "libx264"},
    "Instagram Reels": {"width": 1080, "height": 1920, "fps": 30, "crf": 22, "codec": "libx264"},
    "Максимальное качество": {"width": 1920, "height": 1080, "fps": 60, "crf": 16,
                              "codec": "libx265"},
    "Минимальный размер": {"width": 1280, "height": 720, "fps": 30, "crf": 30,
                           "codec": "libx264"},
}

#: Сервисы для «экспорта ссылкой».
UPLOAD_SERVICES = {
    "fileio": "file.io (1 скачивание, 24 ч)",
    "transfer": "transfer.sh (7 дней)",
}


# ============================================================
#  Имена файлов
# ============================================================
def sanitize_filename(name: str, max_len: int = 120) -> str:
    """Убрать недопустимые символы из имени файла."""
    cleaned = re.sub(r'[\\/:*?"<>|\x00-\x1f]+', "_", str(name)).strip(" .")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return (cleaned[:max_len] or "export")


def build_filename(kind: str = "audio", stem: str = "", ext: str = "mp3",
                   template: Optional[str] = None) -> str:
    """Собрать имя файла по шаблону из настроек.

    Args:
        kind: ``audio`` | ``video`` | ``stem``.
        stem: Пользовательское имя (если задано — используется оно).
        ext: Расширение без точки.
        template: Шаблон с плейсхолдерами ``{kind}``, ``{date}``, ``{time}``, ``{stem}``.

    Returns:
        Имя файла с расширением.
    """
    now = datetime.now()
    tpl = template or get_config().export.filename_template
    name = tpl.format(kind=kind, date=now.strftime("%Y-%m-%d"),
                      time=now.strftime("%H-%M"), stem=stem or kind)
    if stem:
        name = stem
    if "{kind}" in name or name == "":
        name = f"BeatForge_{kind}_{now:%Y-%m-%d_%H-%M}"
    return f"{sanitize_filename(name)}.{ext.lstrip('.')}"


def unique_path(path: str | Path) -> Path:
    """Вернуть свободный путь, добавив ``(1)``, ``(2)``… при совпадении."""
    candidate = Path(path)
    if not candidate.exists():
        return candidate
    stem, suffix, parent = candidate.stem, candidate.suffix, candidate.parent
    for i in range(1, 1000):
        candidate = parent / f"{stem} ({i}){suffix}"
        if not candidate.exists():
            return candidate
    return parent / f"{stem}_{uuid.uuid4().hex[:6]}{suffix}"


# ============================================================
#  Аудио
# ============================================================
def _write_with_soundfile(buffer: AudioBuffer, out: Path, fmt: str,
                          bit_depth: int = 16, dither: bool = True) -> None:
    """Записать буфер через soundfile (WAV/FLAC/OGG)."""
    import soundfile as sf

    data = buffer.samples
    if dither and bit_depth == 16:
        # TPDF-дизер: ±1 LSB
        rng = np.random.default_rng(int(time.time()) % 100000)
        lsb = 1.0 / 32768.0
        data = data + (rng.random(data.shape, dtype=np.float32) -
                       rng.random(data.shape, dtype=np.float32)) * lsb * 0.5
    subtype = {16: "PCM_16", 24: "PCM_24", 32: "FLOAT"}.get(bit_depth, "PCM_16")
    if fmt == "flac":
        subtype = {16: "PCM_16", 24: "PCM_24"}.get(bit_depth, "PCM_16")
    elif fmt == "ogg":
        subtype = "VORBIS"
    sf.write(str(out), np.clip(data, -1.0, 1.0), buffer.sample_rate, subtype=subtype)


def export_audio(buffer: AudioBuffer, out_path: str | Path, fmt: Optional[str] = None,
                 bitrate: Optional[int] = None, bit_depth: int = 16,
                 normalize: bool = False, target_db: float = -1.0,
                 progress_cb: Optional[Callable[[int, str], None]] = None,
                 metadata: Optional[Dict[str, str]] = None) -> Path:
    """Экспортировать аудио-буфер в файл.

    Args:
        buffer: Аудиоданные.
        out_path: Путь назначения.
        fmt: Формат (``mp3``/``wav``/``flac``/``ogg``/``m4a``); по расширению.
        bitrate: Битрейт MP3, кбит/с (128/192/320).
        bit_depth: Разрядность WAV (16/24).
        normalize: Нормализовать перед записью.
        target_db: Целевой пик при нормализации.
        progress_cb: Колбэк прогресса.
        metadata: Теги (title/artist/comment) — только для MP3/M4A через ffmpeg.

    Returns:
        Путь к готовому файлу.

    Raises:
        RuntimeError: если формат не поддержан или запись не удалась.
    """
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    kind = (fmt or out.suffix.lstrip(".") or "wav").lower()
    if kind not in AUDIO_FORMATS:
        raise RuntimeError(f"Неподдерживаемый формат аудио: {kind}")

    if normalize:
        buffer = normalize_buffer(buffer, target_db)
    if progress_cb:
        progress_cb(10, "подготовка…")

    if kind in ("wav", "flac", "ogg"):
        _write_with_soundfile(buffer, out, kind, bit_depth)
        if progress_cb:
            progress_cb(100, "готово")
        return out

    # MP3/M4A — через ffmpeg (качественнее и с тегами)
    ff = ffmpeg_path()
    if not ff:
        raise RuntimeError("Для экспорта в MP3/M4A нужен FFmpeg")
    rate = bitrate or get_config().export.audio_bitrate
    tmp_wav = Path(out.parent) / f".bfs_tmp_{uuid.uuid4().hex[:8]}.wav"
    try:
        _write_with_soundfile(buffer, tmp_wav, "wav", 24, dither=False)
        if progress_cb:
            progress_cb(45, "кодирование…")
        cmd = [ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(tmp_wav),
               "-c:a", AUDIO_FORMATS[kind]["codec"], "-b:a", f"{int(rate)}k",
               "-ar", str(buffer.sample_rate)]
        for key, value in (metadata or {}).items():
            if key in ("title", "artist", "album", "comment", "genre", "date"):
                cmd += ["-metadata", f"{key}={value}"]
        cmd.append(str(out))
        run(cmd, timeout=3600)
    finally:
        tmp_wav.unlink(missing_ok=True)
    if progress_cb:
        progress_cb(100, "готово")
    return out


# ============================================================
#  Видео
# ============================================================
def export_video(scene, out_path: str | Path, preset: str = "YouTube 1080p",
                 fmt: Optional[str] = None, bitrate_mbps: Optional[float] = None,
                 progress_cb: Optional[Callable[[int, str], None]] = None,
                 cancel_event: Optional[threading.Event] = None,
                 extra_args: Optional[List[str]] = None) -> Path:
    """Отрендерить сцену в видеофайл.

    Args:
        scene: :class:`core.video_engine.Scene`.
        out_path: Путь результата.
        preset: Имя пресета из :data:`VIDEO_PRESETS`.
        fmt: ``mp4``/``webm``/``mov``/``gif`` (по расширению).
        bitrate_mbps: Ручной битрейт (перебивает CRF).
        progress_cb: Прогресс.
        cancel_event: Отмена.
        extra_args: Доп. аргументы FFmpeg.

    Returns:
        Путь к файлу.
    """
    from core.video_engine import VideoEngine

    out = Path(out_path)
    kind = (fmt or out.suffix.lstrip(".") or "mp4").lower()
    params = dict(VIDEO_PRESETS.get(preset, VIDEO_PRESETS["YouTube 1080p"]))
    scene.width = int(params["width"])
    scene.height = int(params["height"])
    scene.fps = int(params["fps"])

    codec = str(params["codec"])
    preset_args = ["-preset", "medium"]
    if kind == "webm":
        codec = "libvpx-vp9"
        preset_args = ["-b:v", f"{int((bitrate_mbps or 8) * 1000)}k", "-row-mt", "1"]
    elif kind == "gif":
        return export_gif(out, scene, progress_cb=progress_cb)

    engine = VideoEngine()
    bitrate = f"{bitrate_mbps:.1f}M" if bitrate_mbps else None
    return engine.render(scene, out, on_progress=progress_cb, cancel_event=cancel_event,
                         preset_data={"codec": codec, "crf": int(params["crf"]),
                                      "bitrate": bitrate, "preset": "medium"},
                         extra_args=(preset_args + (extra_args or [])))


def export_gif(out_path: str | Path, scene, fps: int = 15, width: int = 640,
               progress_cb: Optional[Callable[[int, str], None]] = None) -> Path:
    """Экспорт GIF: сначала рендер во временный MP4, затем палитра + GIF."""
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp_mp4 = out.with_name(f".bfs_tmp_{uuid.uuid4().hex[:8]}.mp4")
    try:
        from core.video_engine import VideoEngine

        engine = VideoEngine()
        engine.render(scene, tmp_mp4, on_progress=progress_cb)
        ff = ffmpeg_path()
        if not ff:
            raise RuntimeError("FFmpeg не найден")
        palette = out.with_name(f".bfs_pal_{uuid.uuid4().hex[:8]}.png")
        try:
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(tmp_mp4),
                 "-vf", f"fps={fps},scale={width}:-1:flags=lanczos,palettegen", str(palette)],
                timeout=1800)
            run([ff, "-hide_banner", "-loglevel", "error", "-y", "-i", str(tmp_mp4),
                 "-i", str(palette), "-lavfi",
                 f"fps={fps},scale={width}:-1:flags=lanczos[x];[x][1:v]paletteuse",
                 str(out)], timeout=1800)
        finally:
            palette.unlink(missing_ok=True)
        if progress_cb:
            progress_cb(100, "готово")
        return out
    finally:
        tmp_mp4.unlink(missing_ok=True)


# ============================================================
#  Загрузка ссылкой
# ============================================================
@dataclass
class UploadResult:
    """Результат загрузки файла на файлообменник.

    Attributes:
        url: Прямая ссылка.
        expires: Человекочитаемый срок жизни.
        service: Имя сервиса.
        max_downloads: Ограничение числа скачиваний (если известно).
    """

    url: str
    expires: str = ""
    service: str = ""
    max_downloads: Optional[int] = None


def _file_size_mb(path: Path) -> float:
    """Размер файла в МБ."""
    return path.stat().st_size / 1048576.0


def upload_to_fileio(path: str | Path, expires: str = "1d",
                     progress_cb: Optional[Callable[[int, str], None]] = None,
                     timeout: int = 600) -> UploadResult:
    """Загрузить файл на file.io (одноразовая ссылка).

    Args:
        path: Файл.
        expires: Срок жизни (``1d``, ``1w``, ``1h``).
        progress_cb: Прогресс (0..100).
        timeout: Таймаут, сек.

    Returns:
        :class:`UploadResult` с ссылкой.

    Raises:
        RuntimeError: при ошибке сети/лимитов.
    """
    import requests

    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")
    size = _file_size_mb(src)
    if size > 200:
        raise RuntimeError("file.io не принимает файлы больше 200 МБ")

    if progress_cb:
        progress_cb(10, f"отправка {size:.1f} МБ…")
    mime = mimetypes.guess_type(str(src))[0] or "application/octet-stream"
    try:
        with src.open("rb") as handle:
            response = requests.post(
                "https://file.io",
                files={"file": (src.name, handle, mime)},
                data={"expires": expires, "maxDownloads": "1"},
                timeout=timeout,
            )
    except Exception as exc:
        raise RuntimeError(f"file.io недоступен: {exc}") from exc

    if response.status_code >= 400:
        raise RuntimeError(f"file.io вернул {response.status_code}: {response.text[:200]}")
    try:
        payload = response.json()
    except ValueError as exc:
        raise RuntimeError("file.io вернул не-JSON ответ") from exc
    if not payload.get("success", True):
        raise RuntimeError(f"file.io отклонил загрузку: {payload}")
    link = payload.get("link") or payload.get("key")
    if not link:
        raise RuntimeError(f"file.io не вернул ссылку: {payload}")
    if progress_cb:
        progress_cb(100, "готово")
    LOG.info("file.io: %s (%s МБ)", link, f"{size:.1f}")
    return UploadResult(url=str(link), expires="24 часа, 1 скачивание", service="file.io",
                        max_downloads=1)


def upload_to_transfer(path: str | Path, progress_cb: Optional[Callable[[int, str], None]] = None,
                       timeout: int = 900) -> UploadResult:
    """Загрузить файл на transfer.sh (до 10 ГБ, 7 дней).

    Args:
        path: Файл.
        progress_cb: Прогресс.
        timeout: Таймаут, сек.

    Returns:
        :class:`UploadResult`.

    Raises:
        RuntimeError: при ошибке.
    """
    import requests

    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")
    size = _file_size_mb(src)
    if progress_cb:
        progress_cb(10, f"отправка {size:.1f} МБ…")
    url = f"https://transfer.sh/{src.name}"
    headers = {"Max-Days": "7", "Max-Downloads": "50"}
    try:
        with src.open("rb") as handle:
            response = requests.put(url, data=handle, headers=headers, timeout=timeout)
    except Exception as exc:
        raise RuntimeError(f"transfer.sh недоступен: {exc}") from exc
    if response.status_code >= 400:
        raise RuntimeError(f"transfer.sh вернул {response.status_code}: {response.text[:200]}")
    link = response.text.strip()
    if not link.startswith("http"):
        raise RuntimeError(f"transfer.sh вернул неожиданный ответ: {link[:200]}")
    if progress_cb:
        progress_cb(100, "готово")
    return UploadResult(url=link, expires="7 дней, до 50 скачиваний", service="transfer.sh",
                        max_downloads=50)


def upload_file(path: str | Path, service: Optional[str] = None,
                progress_cb: Optional[Callable[[int, str], None]] = None,
                fallback: bool = True) -> UploadResult:
    """Загрузить файл на выбранный сервис с fallback.

    Args:
        path: Файл.
        service: ``fileio`` | ``transfer`` (по умолчанию из настроек).
        progress_cb: Прогресс.
        fallback: Попробовать второй сервис при ошибке.

    Returns:
        :class:`UploadResult`.
    """
    chosen = service or get_config().export.upload_service
    try:
        if chosen == "transfer":
            return upload_to_transfer(path, progress_cb)
        return upload_to_fileio(path, get_config().export.upload_ttl, progress_cb)
    except Exception as exc:
        if not fallback:
            raise
        LOG.warning("Загрузка через %s не удалась (%s), пробую второй сервис", chosen, exc)
        if chosen == "transfer":
            return upload_to_fileio(path, progress_cb=progress_cb)
        return upload_to_transfer(path, progress_cb=progress_cb)


# ============================================================
#  Qt-обёртки
# ============================================================
class ExportWorker(threading.Thread):
    """Потоковой исполнитель экспорта с Qt-совместимыми колбэками.

    Используйте через :class:`ExportTask` (QThread) или напрямую, передав
    ``signal_emit`` — функцию, которая безопасно вызывает сигналы.
    """

    def __init__(self, fn: Callable[[], Path], progress_cb=None, done_cb=None,
                 error_cb=None, name: str = "export") -> None:
        super().__init__(name=name, daemon=True)
        self._fn = fn
        self._progress = progress_cb
        self._done = done_cb
        self._error = error_cb
        self.cancel_event = threading.Event()

    def run(self) -> None:  # noqa: D401
        """Выполнить экспорт и вызвать колбэки."""
        try:
            path = self._fn()
            if self._done:
                self._done(path)
        except Exception as exc:
            LOG.exception("Экспорт не удался")
            if self._error:
                self._error(str(exc))


try:  # Qt зависит от версии, поэтому импорт мягкий
    from PyQt6.QtCore import QObject, QThread, pyqtSignal

    class ExportTask(QThread):
        """QThread-обёртка экспорта (аудио или видео) с прогрессом и отменой.

        Сигналы:
            progress(int, str): Прогресс.
            finished_ok(str): Путь результата.
            failed(str): Ошибка.
            uploaded(object): :class:`UploadResult` после загрузки ссылкой.
        """

        progress = pyqtSignal(int, str)
        finished_ok = pyqtSignal(str)
        failed = pyqtSignal(str)
        uploaded = pyqtSignal(object)

        def __init__(self, buffer_or_scene, out_path: str | Path, *,
                     kind: str = "audio", fmt: Optional[str] = None,
                     preset: str = "YouTube 1080p", bitrate: Optional[int] = None,
                     normalize: Optional[bool] = None, upload: bool = False,
                     upload_service: Optional[str] = None,
                     metadata: Optional[Dict[str, str]] = None,
                     parent: Optional[QObject] = None) -> None:
            """Args:
                buffer_or_scene: :class:`AudioBuffer` или :class:`Scene`.
                out_path: Путь результата.
                kind: ``audio`` | ``video``.
                fmt: Формат (необязательно).
                preset: Пресет видео.
                bitrate: Битрейт MP3.
                normalize: Нормализация (по умолчанию из настроек).
                upload: Загрузить ссылкой после экспорта.
                upload_service: Сервис загрузки.
                metadata: Теги.
                parent: Родитель.
            """
            super().__init__(parent)
            self._payload = buffer_or_scene
            self._out = Path(out_path)
            self._kind = kind
            self._fmt = fmt
            self._preset = preset
            self._bitrate = bitrate
            self._normalize = normalize
            self._upload = upload
            self._service = upload_service
            self._metadata = metadata
            self._cancel = threading.Event()

        def cancel(self) -> None:
            """Запросить отмену."""
            self._cancel.set()

        def run(self) -> None:  # noqa: D401
            """Выполнить экспорт (и, при необходимости, загрузку)."""
            try:
                if self._kind == "audio":
                    cfg = get_config().export
                    path = export_audio(
                        self._payload, self._out, self._fmt, self._bitrate,
                        cfg.wav_bit_depth,
                        normalize=(cfg.normalize_audio if self._normalize is None
                                   else self._normalize),
                        target_db=cfg.normalize_target_db,
                        progress_cb=lambda p, s: self.progress.emit(p, s),
                        metadata=self._metadata,
                    )
                else:
                    path = export_video(
                        self._payload, self._out, self._preset, self._fmt,
                        progress_cb=lambda p, s: self.progress.emit(p, s),
                        cancel_event=self._cancel,
                    )
                self.finished_ok.emit(str(path))
                if self._upload:
                    self.progress.emit(100, "загрузка ссылкой…")
                    result = upload_file(path, self._service,
                                         lambda p, s: self.progress.emit(p, s))
                    self.uploaded.emit(result)
            except Exception as exc:
                LOG.exception("Экспорт не удался")
                self.failed.emit(str(exc))

    class UploadTask(QThread):
        """QThread только для загрузки готового файла ссылкой.

        Сигналы:
            progress(int, str): Прогресс.
            finished_ok(object): :class:`UploadResult`.
            failed(str): Ошибка.
        """

        progress = pyqtSignal(int, str)
        finished_ok = pyqtSignal(object)
        failed = pyqtSignal(str)

        def __init__(self, path: str | Path, service: Optional[str] = None,
                     parent: Optional[QObject] = None) -> None:
            """Args:
                path: Файл для загрузки.
                service: Сервис.
                parent: Родитель.
            """
            super().__init__(parent)
            self._path = Path(path)
            self._service = service

        def run(self) -> None:  # noqa: D401
            """Загрузить файл и вернуть ссылку."""
            try:
                result = upload_file(self._path, self._service,
                                     lambda p, s: self.progress.emit(p, s))
                self.finished_ok.emit(result)
            except Exception as exc:
                LOG.exception("Загрузка ссылкой не удалась")
                self.failed.emit(str(exc))

except Exception as _exc:  # pragma: no cover - Qt должен быть установлен
    LOG.debug("Qt-обёртки экспорта недоступны: %s", _exc)
    ExportTask = None  # type: ignore[assignment]
    UploadTask = None  # type: ignore[assignment]


def open_in_explorer(path: str | Path) -> None:
    """Открыть папку с файлом в системном проводнике."""
    target = Path(path)
    folder = target if target.is_dir() else target.parent
    try:
        if os.name == "nt":
            os.startfile(str(folder))  # type: ignore[attr-defined]
        elif sys.platform == "darwin":
            subprocess.Popen(["open", str(folder)])
        else:
            subprocess.Popen(["xdg-open", str(folder)])
    except Exception as exc:
        LOG.warning("Не удалось открыть папку %s: %s", folder, exc)
