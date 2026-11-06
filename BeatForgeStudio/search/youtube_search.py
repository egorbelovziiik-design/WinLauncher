"""Поиск музыки на YouTube через yt-dlp (без API-ключа).

Используется «плоский» режим ``extract_flat`` — это быстро и даёт список
результатов без загрузки страниц: id, заголовок, канал, длительность.

Пример::

    from search.youtube_search import YouTubeSearch

    searcher = YouTubeSearch()
    results = searcher.search("Drake type beat", limit=10)
"""

from __future__ import annotations

import logging
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from search.models import (SearchResult, guess_genre, guess_mood, parse_bpm,
                           parse_key)
from utils.cache_db import get_db
from utils.config import get_config

LOG = logging.getLogger(__name__)


class SearchError(RuntimeError):
    """Ошибка поиска (сеть, блокировка, пустой ответ)."""


def _base_opts(limit: int) -> Dict[str, Any]:
    """Собрать опции yt-dlp для быстрого поиска."""
    cfg = get_config().search
    opts: Dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "noplaylist": True,
        "ignoreerrors": True,
        "default_search": f"ytsearch{max(1, min(50, limit))}",
        "socket_timeout": 20,
    }
    if cfg.proxy:
        opts["proxy"] = cfg.proxy
    if cfg.cookies_browser:
        opts["cookiesfrombrowser"] = (cfg.cookies_browser,)
    return opts


def parse_entry(entry: Dict[str, Any], is_beat: bool = False) -> Optional[SearchResult]:
    """Преобразовать запись yt-dlp в :class:`SearchResult`.

    Args:
        entry: Словарь из yt-dlp.
        is_beat: Пометить результат как бит.

    Returns:
        Результат или None, если запись непригодна.
    """
    video_id = entry.get("id")
    if not video_id:
        return None
    title = entry.get("title") or "Без названия"
    url = entry.get("url") or entry.get("webpage_url") or f"https://www.youtube.com/watch?v={video_id}"
    if not str(url).startswith("http"):
        url = f"https://www.youtube.com/watch?v={video_id}"
    duration = entry.get("duration") or 0
    thumb = ""
    thumbs = entry.get("thumbnails") or []
    if thumbs:
        thumb = thumbs[-1].get("url", "")
    if not thumb:
        thumb = f"https://i.ytimg.com/vi/{video_id}/mqdefault.jpg"
    artist = entry.get("uploader") or entry.get("channel") or entry.get("uploader_id") or ""
    return SearchResult(
        source="youtube",
        source_id=str(video_id),
        url=str(url),
        title=str(title),
        artist=str(artist),
        duration=float(duration or 0),
        thumbnail=str(thumb),
        genre=guess_genre(title),
        bpm=parse_bpm(title),
        musical_key=parse_key(title),
        mood=guess_mood(title),
        is_beat=is_beat or "type beat" in title.lower() or "instrumental" in title.lower(),
        extra={"views": entry.get("view_count"), "channel_id": entry.get("channel_id")},
    )


class YouTubeSearch:
    """Поиск на YouTube (и SoundCloud — см. soundcloud_search) через yt-dlp."""

    def __init__(self, db: Optional[Any] = None) -> None:
        """Args:
            db: Кэш (по умолчанию глобальный).
        """
        self._db = db or get_db()

    # ------------------------------------------------------------------ поиск
    def search(self, query: str, limit: Optional[int] = None, *, use_cache: bool = True
               ) -> List[SearchResult]:
        """Найти треки по запросу.

        Args:
            query: Поисковая строка.
            limit: Сколько результатов вернуть (по умолчанию из конфига).
            use_cache: Отдавать сначала результаты из локального кэша.

        Returns:
            Список результатов (кэш + сеть, без дублей).

        Raises:
            SearchError: Если yt-dlp недоступен или поиск не удался.
        """
        query = (query or "").strip()
        if not query:
            return []
        cfg = get_config().search
        limit = int(limit or cfg.results_limit)

        cached: List[SearchResult] = []
        if use_cache and cfg.cache_results:
            cached = self.from_cache(query, limit, is_beat=False)

        try:
            import yt_dlp
        except ImportError as exc:  # pragma: no cover
            raise SearchError("yt-dlp не установлен: pip install yt-dlp") from exc

        try:
            with yt_dlp.YoutubeDL(_base_opts(limit)) as ydl:
                info = ydl.extract_info(query, download=False)
        except Exception as exc:
            LOG.exception("YouTube-поиск не удался")
            if cached:
                LOG.warning("Отдаю результаты из кэша из-за ошибки сети")
                return cached[:limit]
            raise SearchError(f"Не удалось выполнить поиск: {exc}") from exc

        entries = (info or {}).get("entries") or []
        results: List[SearchResult] = []
        seen = {r.source_id for r in cached}
        for entry in entries:
            if not entry:
                continue
            parsed = parse_entry(entry)
            if parsed is None or parsed.source_id in seen:
                continue
            seen.add(parsed.source_id)
            self._store(parsed)
            results.append(parsed)

        self._db.add_search(query, source="youtube", kind="music", results=len(results))
        combined = cached + results
        return combined[:limit]

    def search_raw(self, query: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Вернуть «сырые» записи yt-dlp (для отладки/расширений)."""
        import yt_dlp

        with yt_dlp.YoutubeDL(_base_opts(limit)) as ydl:
            info = ydl.extract_info(query, download=False)
        return [e for e in (info or {}).get("entries", []) if e]

    # ------------------------------------------------------------------ метаданные
    def resolve_url(self, url: str) -> Optional[SearchResult]:
        """Получить полные метаданные по ссылке (без загрузки)."""
        try:
            import yt_dlp

            opts = _base_opts(1)
            opts["extract_flat"] = False
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            return parse_entry(info or {}) if info else None
        except Exception as exc:
            LOG.warning("Не удалось получить метаданные %s: %s", url, exc)
            return None

    def stream_url(self, url: str, max_seconds: int = 30) -> Optional[str]:
        """Прямая ссылка на аудиопоток (для стримингового превью)."""
        try:
            import yt_dlp

            opts = {"quiet": True, "no_warnings": True,
                    "format": "bestaudio/best", "noplaylist": True}
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            if not info:
                return None
            for fmt in info.get("formats", []):
                if fmt.get("acodec") not in (None, "none") and fmt.get("url"):
                    return fmt["url"]
            return info.get("url")
        except Exception as exc:
            LOG.warning("Не удалось получить поток %s: %s", url, exc)
            return None

    # ------------------------------------------------------------------ кэш
    def from_cache(self, query: str, limit: int = 30, is_beat: bool = False) -> List[SearchResult]:
        """Выбрать подходящие записи из локального кэша."""
        rows = self._db.search_tracks(query, limit=limit, only_beats=False)
        results = []
        for row in rows:
            if is_beat and not row.is_beat:
                continue
            results.append(SearchResult(
                source=row.source, source_id=row.source_id, url=row.url, title=row.title,
                artist=row.artist, duration=row.duration, thumbnail=row.thumbnail,
                genre=row.genre, bpm=row.bpm, musical_key=row.musical_key, mood=row.mood,
                is_beat=row.is_beat, file_path=row.file_path, extra={"cached": True},
            ))
        return results

    def _store(self, result: SearchResult) -> None:
        """Сохранить результат в кэш (для мгновенной выдачи в следующий раз)."""
        try:
            from utils.cache_db import TrackRecord

            self._db.upsert_track(TrackRecord(
                source=result.source, source_id=result.source_id, url=result.url,
                title=result.title, artist=result.artist, duration=result.duration,
                thumbnail=result.thumbnail, genre=result.genre, bpm=result.bpm,
                musical_key=result.musical_key, mood=result.mood, is_beat=result.is_beat,
                file_path=result.file_path,
            ))
        except Exception:
            LOG.debug("Не удалось сохранить результат в кэш", exc_info=True)

    # ------------------------------------------------------------------ тренды
    def suggestions(self, prefix: str, limit: int = 8) -> List[str]:
        """Подсказки для строки поиска: история + YouTube-саджесты.

        Args:
            prefix: Введённый текст.
            limit: Сколько подсказок вернуть.

        Returns:
            Список строк.
        """
        items = [q for q in self._db.search_history(20) if q.lower().startswith(prefix.lower())]
        try:
            import requests

            resp = requests.get(
                "https://suggestqueries.google.com/complete/search",
                params={"client": "firefox", "ds": "yt", "q": prefix},
                timeout=4,
            )
            if resp.ok:
                data = resp.json()
                if isinstance(data, list) and len(data) > 1:
                    items += [str(x) for x in data[1]]
        except Exception:
            LOG.debug("Саджесты YouTube недоступны", exc_info=True)
        unique: List[str] = []
        for item in items:
            if item and item not in unique:
                unique.append(item)
        return unique[:limit]


def sanitize_filename(text: str, max_len: int = 80) -> str:
    """Сделать безопасное имя файла из названия трека."""
    cleaned = re.sub(r"[\\/:*?\"<>|]+", "_", text or "track").strip(" .")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned[:max_len] or "track"
