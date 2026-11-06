"""Загрузка видео на YouTube через YouTube Data API v3.

Реализовано через resumable upload (``MediaFileUpload`` с чанками) — это даёт:

* реальный прогресс загрузки (байты/проценты);
* возможность докачки при обрыве связи;
* понятные ошибки квоты (403 quotaExceeded).

Модуль не показывает UI: прогресс отдаётся колбэком, ошибки — исключениями.
Qt-обёртка — :class:`YouTubeUploadTask`.
"""

from __future__ import annotations

import logging
import random
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional

from PyQt6.QtCore import QObject, QThread, pyqtSignal

from social.auth_manager import Account, AuthError, AuthManager
from utils.config import get_config

LOG = logging.getLogger(__name__)

#: Категории YouTube (id → название) — самые нужные.
YOUTUBE_CATEGORIES: Dict[str, str] = {
    "10": "Музыка", "24": "Развлечения", "22": "Люди и блоги", "20": "Игры",
    "27": "Образование", "28": "Наука и техника", "1": "Кино и анимация",
    "23": "Юмор", "26": "Хобби и стиль",
}

#: Приватность.
PRIVACY_VALUES = {"public": "Публичное", "unlisted": "По ссылке", "private": "Приватное"}


@dataclass
class VideoMetadata:
    """Метаданные публикуемого видео.

    Attributes:
        title: Название (до 100 символов).
        description: Описание (до 5000 символов).
        tags: Теги (суммарно до 500 символов).
        category_id: Категория YouTube.
        privacy: ``public`` | ``unlisted`` | ``private``.
        made_for_kids: Видео для детей (обязательный выбор).
        publish_at: Время отложенной публикации (ISO 8601 UTC) или None.
        thumbnail: Путь к изображению-превью (1280×720, <2 МБ).
        playlist_id: Плейлист для добавления.
        language: Язык видео (``ru``/``en``).
    """

    title: str = "BeatForge Studio"
    description: str = "Сделано в BeatForge Studio"
    tags: List[str] = field(default_factory=list)
    category_id: str = "10"
    privacy: str = "public"
    made_for_kids: bool = False
    publish_at: Optional[str] = None
    thumbnail: str = ""
    playlist_id: str = ""
    language: str = "ru"

    def validate(self) -> "VideoMetadata":
        """Привести значения к требованиям API.

        Returns:
            self с подрезанными полями.
        """
        self.title = (self.title or "Без названия").strip()[:100]
        self.description = (self.description or "")[:5000]
        self.tags = [t.strip() for t in self.tags if t.strip()][:30]
        if sum(len(t) for t in self.tags) > 500:
            trimmed: List[str] = []
            total = 0
            for tag in self.tags:
                if total + len(tag) + 1 > 500:
                    break
                trimmed.append(tag)
                total += len(tag) + 1
            self.tags = trimmed
        if self.privacy not in PRIVACY_VALUES:
            self.privacy = "public"
        if self.publish_at and self.privacy != "private":
            # отложенная публикация требует private
            self.privacy = "private"
        return self

    def to_api_body(self) -> Dict[str, Any]:
        """Собрать тело запроса ``videos.insert``."""
        self.validate()
        status: Dict[str, Any] = {
            "privacyStatus": self.privacy,
            "selfDeclaredMadeForKids": bool(self.made_for_kids),
        }
        if self.publish_at:
            status["publishAt"] = self.publish_at
        snippet: Dict[str, Any] = {
            "title": self.title,
            "description": self.description,
            "tags": self.tags,
            "categoryId": self.category_id,
            "defaultLanguage": self.language,
            "defaultAudioLanguage": self.language,
        }
        return {"snippet": snippet, "status": status}


@dataclass
class UploadProgress:
    """Состояние загрузки.

    Attributes:
        percent: Процент (0..100).
        uploaded: Сколько байт отправлено.
        total: Всего байт.
        speed: Скорость, байт/с.
        status: Текстовый статус.
    """

    percent: float = 0.0
    uploaded: int = 0
    total: int = 0
    speed: float = 0.0
    status: str = ""

    @property
    def speed_text(self) -> str:
        """Скорость в МБ/с."""
        return f"{self.speed / 1048576:.1f} МБ/с" if self.speed else ""


def _build_service(account: Account, auth: AuthManager):
    """Собрать authenticated-сервис YouTube Data API.

    Args:
        account: Аккаунт YouTube.
        auth: Менеджер авторизации.

    Raises:
        AuthError: если нет доступа/библиотек.
    """
    try:
        from googleapiclient.discovery import build
    except Exception as exc:  # pragma: no cover
        raise AuthError("Установите google-api-python-client") from exc
    creds = auth.youtube_credentials(account)
    return build("youtube", "v3", credentials=creds, cache_discovery=False)


def upload_video(path: str | Path, metadata: VideoMetadata, *,
                 account: Optional[Account] = None,
                 auth: Optional[AuthManager] = None,
                 progress_cb: Optional[Callable[[UploadProgress], None]] = None,
                 cancel_event: Optional[threading.Event] = None,
                 chunk_size: Optional[int] = None,
                 retries: int = 5) -> Dict[str, Any]:
    """Загрузить видео на YouTube (resumable upload).

    Args:
        path: Файл видео (MP4, H.264 + AAC).
        metadata: Название/описание/приватность.
        account: Аккаунт (по умолчанию первый привязанный).
        auth: Менеджер авторизации.
        progress_cb: Колбэк прогресса.
        cancel_event: Флаг отмены.
        chunk_size: Размер чанка (кратно 256 КБ), по умолчанию из настроек.
        retries: Сколько раз повторять чанк при сетевой ошибке.

    Returns:
        Ответ API (``{"id": ..., "snippet": {...}}``).

    Raises:
        AuthError: нет привязки/доступа.
        RuntimeError: ошибка загрузки (квота, файл, отмена).
    """
    src = Path(path)
    if not src.exists():
        raise RuntimeError(f"Файл не найден: {src}")
    if src.stat().st_size > 256 * 1024 * 1024 * 1024:
        raise RuntimeError("YouTube не принимает файлы больше 256 ГБ")

    manager = auth or AuthManager()
    acc = account or manager.default_account("youtube")
    if acc is None:
        raise AuthError("YouTube не привязан — добавьте аккаунт во вкладке «Публикация»")

    try:
        from googleapiclient.errors import HttpError
        from googleapiclient.http import MediaFileUpload
    except Exception as exc:  # pragma: no cover
        raise AuthError("Установите google-api-python-client") from exc

    service = _build_service(acc, manager)
    body = metadata.to_api_body()
    cfg = get_config().publish
    media = MediaFileUpload(str(src), chunksize=int(chunk_size or cfg.upload_chunk_size),
                            resumable=True, mimetype="video/*")
    request = service.videos().insert(part="snippet,status", body=body, media_body=media)

    total = src.stat().st_size
    started = time.time()
    response: Optional[Dict[str, Any]] = None
    attempt = 0
    while response is None:
        if cancel_event is not None and cancel_event.is_set():
            raise RuntimeError("Загрузка отменена пользователем")
        try:
            status, response = request.next_chunk()
            attempt = 0
        except HttpError as exc:
            code = getattr(exc.resp, "status", 0)
            text = str(exc)
            if code in (500, 502, 503, 504) and attempt < retries:
                attempt += 1
                wait = min(30, 2 ** attempt + random.random())
                LOG.warning("Временная ошибка YouTube (%s), ждём %.1f с", code, wait)
                time.sleep(wait)
                continue
            if "quotaExceeded" in text:
                raise RuntimeError("Закончилась квота YouTube API на сегодня") from exc
            if code == 401:
                raise AuthError("Доступ к YouTube истёк — привяжите аккаунт заново") from exc
            raise RuntimeError(f"YouTube API: {text[:300]}") from exc
        except Exception as exc:
            if attempt < retries:
                attempt += 1
                wait = min(20, 2 ** attempt)
                LOG.warning("Сетевая ошибка загрузки (%s), повтор через %.1f с", exc, wait)
                time.sleep(wait)
                continue
            raise RuntimeError(f"Загрузка прервана: {exc}") from exc

        if status is not None and progress_cb:
            uploaded = int(status.resumable_progress or 0)
            elapsed = max(0.01, time.time() - started)
            progress_cb(UploadProgress(
                percent=float(status.progress() * 100),
                uploaded=uploaded, total=total,
                speed=uploaded / elapsed,
                status=f"загрузка {status.progress() * 100:.0f}%",
            ))

    video_id = (response or {}).get("id", "")
    if not video_id:
        raise RuntimeError("YouTube не вернул id видео")

    # превью (опционально)
    if metadata.thumbnail and Path(metadata.thumbnail).exists():
        try:
            from googleapiclient.http import MediaFileUpload as ThumbUpload

            service.thumbnails().set(
                videoId=video_id,
                media_body=ThumbUpload(str(metadata.thumbnail), mimetype="image/jpeg"),
            ).execute()
            LOG.info("Превью установлено для %s", video_id)
        except Exception as exc:
            LOG.warning("Не удалось установить превью: %s", exc)

    # плейлист (опционально)
    if metadata.playlist_id:
        try:
            service.playlistItems().insert(
                part="snippet",
                body={"snippet": {"playlistId": metadata.playlist_id,
                                  "resourceId": {"kind": "youtube#video", "videoId": video_id}}},
            ).execute()
        except Exception as exc:
            LOG.warning("Не удалось добавить в плейлист: %s", exc)

    if progress_cb:
        progress_cb(UploadProgress(100, total, total, 0.0, "готово"))
    LOG.info("Видео загружено: https://youtu.be/%s", video_id)
    result = dict(response or {})
    result["url"] = f"https://youtu.be/{video_id}"
    result["watch_url"] = f"https://www.youtube.com/watch?v={video_id}"
    return result


def fetch_channel_info(account: Optional[Account] = None,
                       auth: Optional[AuthManager] = None) -> Dict[str, Any]:
    """Получить информацию о канале (имя, аватар, подписчики).

    Args:
        account: Аккаунт (по умолчанию первый).
        auth: Менеджер авторизации.

    Returns:
        Словарь с ``title``, ``thumbnail``, ``id``, ``subscribers``.
    """
    manager = auth or AuthManager()
    acc = account or manager.default_account("youtube")
    if acc is None:
        raise AuthError("YouTube не привязан")
    service = _build_service(acc, manager)
    response = service.channels().list(part="snippet,statistics", mine=True).execute()
    items = response.get("items") or []
    if not items:
        return {}
    item = items[0]
    snippet = item.get("snippet", {})
    stats = item.get("statistics", {})
    return {
        "id": item.get("id", ""),
        "title": snippet.get("title", ""),
        "thumbnail": (snippet.get("thumbnails", {}).get("default", {}) or {}).get("url", ""),
        "subscribers": stats.get("subscriberCount", "0"),
        "videos": stats.get("videoCount", "0"),
    }


def list_playlists(account: Optional[Account] = None,
                   auth: Optional[AuthManager] = None) -> List[Dict[str, str]]:
    """Список плейлистов канала для выпадающего списка."""
    manager = auth or AuthManager()
    acc = account or manager.default_account("youtube")
    if acc is None:
        return []
    try:
        service = _build_service(acc, manager)
        response = service.playlists().list(part="snippet", mine=True,
                                            maxResults=50).execute()
        return [{"id": item.get("id", ""), "title": item.get("snippet", {}).get("title", "")}
                for item in response.get("items", [])]
    except Exception as exc:
        LOG.warning("Не удалось получить плейлисты: %s", exc)
        return []


def to_iso8601(value: datetime) -> str:
    """Преобразовать datetime в ISO 8601 UTC (формат для ``publishAt``)."""
    if value.tzinfo is None:
        value = value.astimezone()
    return value.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


class YouTubeUploadTask(QThread):
    """QThread-обёртка загрузки на YouTube.

    Сигналы:
        progress(int, str): Процент и текст (``"45% · 3.2 МБ/с"``).
        finished_ok(object): Ответ API (содержит ``url``).
        failed(str): Ошибка.
        canceled(): Загрузка отменена.
    """

    progress = pyqtSignal(int, str)
    finished_ok = pyqtSignal(object)
    failed = pyqtSignal(str)
    canceled = pyqtSignal()

    def __init__(self, path: str | Path, metadata: VideoMetadata,
                 account: Optional[Account] = None,
                 parent: Optional[QObject] = None) -> None:
        """Args:
            path: Видеофайл.
            metadata: Метаданные.
            account: Аккаунт.
            parent: Родитель.
        """
        super().__init__(parent)
        self._path = Path(path)
        self._metadata = metadata
        self._account = account
        self._cancel = threading.Event()

    def cancel(self) -> None:
        """Отменить загрузку."""
        self._cancel.set()

    def run(self) -> None:  # noqa: D401
        """Выполнить загрузку в фоне."""
        def on_progress(state: UploadProgress) -> None:
            text = f"{state.percent:.0f}%"
            if state.speed_text:
                text += f" · {state.speed_text}"
            self.progress.emit(int(state.percent), text)

        try:
            result = upload_video(self._path, self._metadata, account=self._account,
                                 progress_cb=on_progress, cancel_event=self._cancel)
            self.finished_ok.emit(result)
        except Exception as exc:
            if self._cancel.is_set():
                self.canceled.emit()
            else:
                LOG.exception("Загрузка на YouTube не удалась")
                self.failed.emit(str(exc))
