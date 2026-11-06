"""Фоновая загрузка аудио/видео через yt-dlp с прогрессом и отменой.

Модуль содержит:

* :func:`download_media` — синхронная загрузка (для вызова из потока);
* :class:`DownloadTask` — QRunnable-обёртка с Qt-сигналами прогресса;
* :class:`DownloadManager` — очередь задач, история в SQLite, отмена.

Прогресс отдаётся в процентах и в мегабайтах; по завершении файл
регистрируется в кэше (:class:`utils.cache_db.CacheDB`).
"""

from __future__ import annotations

import logging
import re
import threading
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Dict, Optional

from PyQt6.QtCore import QObject, QRunnable, QThreadPool, pyqtSignal

from utils.cache_db import CacheDB, TrackRecord, get_db
from utils.config import get_config
from utils.ffmpeg_helper import ffmpeg_path

LOG = logging.getLogger(__name__)

#: Расширения аудиофайлов, которые считаем «скачанными».
AUDIO_FMT = ("wav", "mp3", "flac", "m4a", "ogg", "opus")


@dataclass
class DownloadResult:
    """Результат загрузки.

    Attributes:
        path: Путь к готовому файлу.
        title: Название трека.
        duration: Длительность, сек.
        thumbnail: Путь к обложке (если скачана).
        url: Исходная ссылка.
        source: ``youtube`` | ``soundcloud`` | ``other``.
        meta: Доп. метаданные (жанр, BPM, настроение, исполнитель).
    """

    path: Path
    title: str = ""
    duration: float = 0.0
    thumbnail: str = ""
    url: str = ""
    source: str = "other"
    meta: Dict[str, Any] = field(default_factory=dict)


def detect_source(url: str) -> str:
    """Определить источник по ссылке."""
    low = url.lower()
    if "youtube.com" in low or "youtu.be" in low:
        return "youtube"
    if "soundcloud.com" in low:
        return "soundcloud"
    if "beatstars.com" in low:
        return "beatstars"
    return "other"


def source_id_from_url(url: str) -> str:
    """Извлечь стабильный id из ссылки (YouTube id / последний сегмент пути)."""
    yt = re.search(r"(?:v=|youtu\.be/|shorts/|embed/)([\w-]{6,})", url)
    if yt:
        return yt.group(1)
    cleaned = url.split("?")[0].rstrip("/")
    return cleaned.rsplit("/", 1)[-1] or cleaned


def download_media(url: str, out_dir: Optional[Path] = None, *, audio_only: bool = True,
                   quality: str = "best", progress_cb: Optional[Callable[[float, str], None]] = None,
                   cancel_event: Optional[threading.Event] = None,
                   filename: Optional[str] = None,
                   meta: Optional[Dict[str, Any]] = None) -> DownloadResult:
    """Скачать трек/видео через yt-dlp.

    Args:
        url: Ссылка на страницу.
        out_dir: Каталог сохранения (по умолчанию ``~/BeatForgeStudio/downloads``).
        audio_only: Скачивать только звук (MP3) или видео (MP4).
        quality: ``best`` | ``1080`` | ``720`` и т.п. (для видео).
        progress_cb: Колбэк ``(процент, статус)``.
        cancel_event: Флаг отмены.
        filename: Желаемое имя файла без расширения.
        meta: Доп. метаданные (``genre``, ``bpm``, ``mood``, ``artist``, ``is_beat``)
            — сохраняются в результат для записи в кэш.

    Returns:
        :class:`DownloadResult`.

    Raises:
        RuntimeError: при ошибке/отмене.
    """
    try:
        import yt_dlp
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("yt-dlp не установлен: pip install yt-dlp") from exc

    cfg = get_config()
    target_dir = Path(out_dir) if out_dir else Path(cfg.paths.downloads_dir)
    target_dir.mkdir(parents=True, exist_ok=True)
    safe_name = re.sub(r"[^\w\-. ()]+", "_", filename).strip() if filename else "%(title)s"

    opts: Dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "nocheckcertificate": False,
        "outtmpl": str(target_dir / f"{safe_name}.%(ext)s"),
        "restrictfilenames": False,
        "retries": 3,
        "fragment_retries": 5,
        "concurrent_fragment_downloads": 4,
        "ffmpeg_location": ffmpeg_path(),
        "progress_hooks": [],
        "postprocessor_hooks": [],
        "overwrites": False,
    }
    if cfg.search.proxy:
        opts["proxy"] = cfg.search.proxy
    if cfg.search.cookies_browser:
        opts["cookiesfrombrowser"] = (cfg.search.cookies_browser,)

    if audio_only:
        opts["format"] = "bestaudio/best"
        opts["postprocessors"] = [
            {"key": "FFmpegExtractAudio", "preferredcodec": "mp3",
             "preferredquality": str(cfg.export.audio_bitrate)},
            {"key": "FFmpegMetadata"},
        ]
    else:
        height = re.sub(r"\D", "", quality) or "1080"
        opts["format"] = f"bestvideo[height<={height}]+bestaudio/best[height<={height}]/best"
        opts["merge_output_format"] = "mp4"

    if cfg.search.max_duration_sec:
        opts["match_filter"] = yt_dlp.utils.match_filter_func(
            f"duration < {int(cfg.search.max_duration_sec)}"
        )

    def hook(data: Dict[str, Any]) -> None:
        if cancel_event is not None and cancel_event.is_set():
            raise RuntimeError("Загрузка отменена пользователем")
        status = data.get("status")
        if status == "downloading" and progress_cb:
            total = data.get("total_bytes") or data.get("total_bytes_estimate") or 0
            done = data.get("downloaded_bytes") or 0
            pct = (done / total * 100.0) if total else 0.0
            speed = data.get("speed") or 0
            speed_mb = f"{speed / 1048576:.1f} МБ/с" if speed else ""
            progress_cb(pct, f"{done / 1048576:.1f} МБ {speed_mb}".strip())
        elif status == "finished" and progress_cb:
            progress_cb(100.0, "обработка…")

    opts["progress_hooks"].append(hook)

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            if info is None:
                raise RuntimeError("yt-dlp не вернул информацию о треке")
            prepared = ydl.prepare_filename(info)
            if audio_only:
                path = Path(prepared).with_suffix(".mp3")
                if not path.exists():
                    candidates = list(target_dir.glob(Path(prepared).stem + ".*"))
                    audio = [c for c in candidates if c.suffix.lstrip(".") in AUDIO_FMT]
                    if not audio:
                        raise RuntimeError("Файл не найден после загрузки")
                    path = audio[0]
            else:
                path = Path(prepared)
                if not path.exists():
                    candidates = list(target_dir.glob(Path(prepared).stem + ".*"))
                    if not candidates:
                        raise RuntimeError("Файл не найден после загрузки")
                    path = candidates[0]
    except Exception as exc:
        if cancel_event is not None and cancel_event.is_set():
            raise RuntimeError("Загрузка отменена пользователем") from exc
        raise RuntimeError(f"Ошибка загрузки: {exc}") from exc

    if progress_cb:
        progress_cb(100.0, "готово")
    extras = dict(meta or {})
    return DownloadResult(
        path=path,
        title=info.get("title", path.stem),
        duration=float(info.get("duration") or 0.0),
        thumbnail=str(info.get("thumbnail") or ""),
        url=url,
        source=detect_source(url),
        meta=extras,
    )


class DownloadTaskSignals(QObject):
    """Сигналы задачи загрузки."""

    progress = pyqtSignal(int, str)
    done = pyqtSignal(object)  # DownloadResult
    failed = pyqtSignal(str)
    canceled = pyqtSignal()


class DownloadTask(QRunnable):
    """QRunnable-обёртка загрузки с Qt-сигналами и отменой."""

    def __init__(self, url: str, *, audio_only: bool = True, out_dir: Optional[Path] = None,
                 filename: Optional[str] = None, quality: str = "best",
                 db: Optional[CacheDB] = None, meta: Optional[Dict[str, str]] = None) -> None:
        """Args:
            url: Ссылка.
            audio_only: Только аудио.
            out_dir: Каталог сохранения.
            filename: Желаемое имя файла.
            quality: Качество видео.
            db: База кэша (по умолчанию глобальная).
            meta: Доп. поля для записи в кэш (``genre``, ``mood`` и т.п.).
        """
        super().__init__()
        self.url = url
        self.signals = DownloadTaskSignals()
        self._audio_only = audio_only
        self._out_dir = out_dir
        self._filename = filename
        self._quality = quality
        self._db = db
        self._meta = meta or {}
        self._cancel = threading.Event()
        self._last_emit = 0.0
        self.setAutoDelete(True)

    def cancel(self) -> None:
        """Отменить загрузку."""
        self._cancel.set()

    @property
    def canceled(self) -> bool:
        """Был ли запрос отмены."""
        return self._cancel.is_set()

    def _on_progress(self, pct: float, status: str) -> None:
        now = time.monotonic()
        if now - self._last_emit < 0.15 and pct < 100:
            return
        self._last_emit = now
        self.signals.progress.emit(int(max(0, min(100, pct))), status)

    def run(self) -> None:  # noqa: D401
        """Выполнить загрузку в рабочем потоке."""
        db = self._db or get_db()
        dl_id = db.start_download(self.url)
        try:
            result = download_media(
                self.url, out_dir=self._out_dir, audio_only=self._audio_only,
                quality=self._quality, progress_cb=self._on_progress,
                cancel_event=self._cancel, filename=self._filename,
                meta=self._meta,
            )
            db.update_download(dl_id, status="done", progress=100, target=str(result.path))
            record = TrackRecord(
                source=result.source,
                source_id=source_id_from_url(self.url),
                url=self.url,
                title=result.title,
                artist=str(self._meta.get("artist", "")),
                duration=result.duration,
                thumbnail=result.thumbnail,
                file_path=str(result.path),
                genre=str(self._meta.get("genre", "")),
                bpm=float(self._meta["bpm"]) if self._meta.get("bpm") else None,
                mood=str(self._meta.get("mood", "")),
                is_beat=bool(self._meta.get("is_beat", False)),
            )
            db.upsert_track(record)
            self.signals.done.emit(result)
        except Exception as exc:
            if self._cancel.is_set():
                db.update_download(dl_id, status="canceled", error="отменено")
                self.signals.canceled.emit()
            else:
                LOG.exception("Загрузка не удалась: %s", exc)
                db.update_download(dl_id, status="error", error=str(exc))
                self.signals.failed.emit(str(exc))


class DownloadManager(QObject):
    """Очередь загрузок (максимум N параллельно) с прогрессом для UI.

    Сигналы:
        taskStarted(str, str): url и заголовок (если известен).
        taskProgress(str, int, str): url, процент, статус.
        taskFinished(str, object): url и :class:`DownloadResult`.
        taskFailed(str, str): url и ошибка.
        queueChanged(int): сколько задач в очереди/работает.
    """

    taskStarted = pyqtSignal(str, str)
    taskProgress = pyqtSignal(str, int, str)
    taskFinished = pyqtSignal(str, object)
    taskFailed = pyqtSignal(str, str)
    queueChanged = pyqtSignal(int)

    def __init__(self, parent: Optional[QObject] = None, max_parallel: int = 3) -> None:
        """Args:
            parent: Родитель.
            max_parallel: Сколько загрузок выполнять одновременно.
        """
        super().__init__(parent)
        self._pool = QThreadPool(self)
        self._pool.setMaxThreadCount(max(1, max_parallel))
        self._tasks: Dict[str, DownloadTask] = {}

    def enqueue(self, url: str, *, audio_only: bool = True, filename: Optional[str] = None,
                out_dir: Optional[Path] = None, quality: str = "best",
                meta: Optional[Dict[str, str]] = None) -> DownloadTask:
        """Добавить загрузку в очередь.

        Args:
            url: Ссылка на трек.
            audio_only: Только аудио.
            filename: Имя файла без расширения.
            out_dir: Каталог сохранения.
            quality: Качество видео.
            meta: Доп. метаданные (жанр/BPM/настроение).

        Returns:
            Созданная задача (можно отменить через ``task.cancel()``).
        """
        task = DownloadTask(url, audio_only=audio_only, out_dir=out_dir, filename=filename,
                            quality=quality, meta=meta)
        self._tasks[url] = task
        task.signals.progress.connect(lambda p, s, u=url: self.taskProgress.emit(u, p, s))
        task.signals.done.connect(lambda res, u=url: self._on_done(u, res))
        task.signals.failed.connect(lambda err, u=url: self._on_failed(u, err))
        task.signals.canceled.connect(lambda u=url: self._on_failed(u, "Отменено"))
        self.taskStarted.emit(url, (meta or {}).get("title", ""))
        self._pool.start(task)
        self.queueChanged.emit(self._pool.activeThreadCount())
        return task

    def cancel(self, url: str) -> None:
        """Отменить загрузку по url."""
        task = self._tasks.get(url)
        if task:
            task.cancel()

    def cancel_all(self) -> None:
        """Отменить все загрузки."""
        for task in list(self._tasks.values()):
            task.cancel()

    def _on_done(self, url: str, result: DownloadResult) -> None:
        self._tasks.pop(url, None)
        self.taskFinished.emit(url, result)
        self.queueChanged.emit(self._pool.activeThreadCount())

    def _on_failed(self, url: str, error: str) -> None:
        self._tasks.pop(url, None)
        self.taskFailed.emit(url, error)
        self.queueChanged.emit(self._pool.activeThreadCount())

    @property
    def active(self) -> int:
        """Число активных загрузок."""
        return self._pool.activeThreadCount()
