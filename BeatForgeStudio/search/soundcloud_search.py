"""Поиск на SoundCloud через yt-dlp (``scsearch``).

SoundCloud не выдаёт публичный поисковый API без client_id, поэтому поиск идёт
через yt-dlp: ``extract_flat`` даёт быстрый список, полные метаданные
подтягиваются по требованию.

Пример::

    from search.soundcloud_search import SoundCloudSearch

    sc = SoundCloudSearch()
    for item in sc.search("phonk", limit=10):
        print(item.title, item.duration_text)
"""

from __future__ import annotations

import logging
from typing import Any, Dict, List, Optional

from search.models import (SearchResult, guess_genre, guess_mood, parse_bpm,
                           parse_key)
from search.youtube_search import SearchError
from utils.cache_db import get_db
from utils.config import get_config

LOG = logging.getLogger(__name__)


def _opts(limit: int) -> Dict[str, Any]:
    """Опции yt-dlp для SoundCloud-поиска."""
    cfg = get_config().search
    opts: Dict[str, Any] = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": "in_playlist",
        "ignoreerrors": True,
        "default_search": f"scsearch{max(1, min(50, limit))}",
        "socket_timeout": 20,
    }
    if cfg.proxy:
        opts["proxy"] = cfg.proxy
    return opts


def parse_entry(entry: Dict[str, Any], is_beat: bool = False) -> Optional[SearchResult]:
    """Преобразовать запись SoundCloud (yt-dlp) в :class:`SearchResult`."""
    url = entry.get("url") or entry.get("webpage_url")
    if not url:
        return None
    title = entry.get("title") or "Без названия"
    artist = entry.get("uploader") or entry.get("channel") or entry.get("uploader_id") or ""
    source_id = entry.get("id") or url.rstrip("/").rsplit("/", 1)[-1]
    thumb = entry.get("thumbnail") or ""
    if not thumb:
        thumbs = entry.get("thumbnails") or []
        if thumbs:
            thumb = thumbs[-1].get("url", "")
    return SearchResult(
        source="soundcloud",
        source_id=str(source_id),
        url=str(url),
        title=str(title),
        artist=str(artist),
        duration=float(entry.get("duration") or 0.0),
        thumbnail=str(thumb),
        genre=guess_genre(title),
        bpm=parse_bpm(title) or (float(entry["bpm"]) if entry.get("bpm") else None),
        musical_key=parse_key(title),
        mood=guess_mood(title),
        is_beat=is_beat or "type beat" in title.lower() or "instrumental" in title.lower(),
        extra={"play_count": entry.get("view_count"), "cached": False},
    )


class SoundCloudSearch:
    """Поиск треков и битов на SoundCloud."""

    def __init__(self, db: Optional[Any] = None) -> None:
        """Args:
            db: Кэш (по умолчанию глобальный).
        """
        self._db = db or get_db()

    def search(self, query: str, limit: Optional[int] = None, *,
               is_beat: bool = False, use_cache: bool = True) -> List[SearchResult]:
        """Найти треки на SoundCloud.

        Args:
            query: Поисковая строка.
            limit: Число результатов.
            is_beat: Пометить результаты как биты.
            use_cache: Подмешивать результаты из локального кэша.

        Returns:
            Список результатов.

        Raises:
            SearchError: Если поиск не удался и кэш пуст.
        """
        query = (query or "").strip()
        if not query:
            return []
        limit = int(limit or get_config().search.results_limit)

        cached: List[SearchResult] = []
        if use_cache and get_config().search.cache_results:
            cached = self.from_cache(query, limit, is_beat)

        try:
            import yt_dlp
        except ImportError as exc:  # pragma: no cover
            raise SearchError("yt-dlp не установлен: pip install yt-dlp") from exc

        try:
            with yt_dlp.YoutubeDL(_opts(limit)) as ydl:
                info = ydl.extract_info(query, download=False)
        except Exception as exc:
            LOG.exception("SoundCloud-поиск не удался")
            if cached:
                return cached[:limit]
            raise SearchError(f"SoundCloud недоступен: {exc}") from exc

        seen = {r.source_id for r in cached}
        results: List[SearchResult] = []
        for entry in (info or {}).get("entries") or []:
            if not entry:
                continue
            parsed = parse_entry(entry, is_beat)
            if parsed is None or parsed.source_id in seen:
                continue
            seen.add(parsed.source_id)
            self._store(parsed)
            results.append(parsed)

        self._db.add_search(query, source="soundcloud",
                            kind="beat" if is_beat else "music", results=len(results))
        return (cached + results)[:limit]

    def resolve_url(self, url: str) -> Optional[SearchResult]:
        """Получить метаданные по ссылке SoundCloud."""
        try:
            import yt_dlp

            opts = _opts(1)
            opts["extract_flat"] = False
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
            return parse_entry(info or {}) if info else None
        except Exception as exc:
            LOG.warning("Не удалось получить метаданные SoundCloud %s: %s", url, exc)
            return None

    def from_cache(self, query: str, limit: int = 30, is_beat: bool = False
                   ) -> List[SearchResult]:
        """Результаты из локального кэша (source=soundcloud)."""
        rows = [r for r in self._db.search_tracks(query, limit=limit * 2, only_beats=False)
                if r.source == "soundcloud" or not query]
        out: List[SearchResult] = []
        for row in rows:
            if is_beat and not row.is_beat:
                continue
            out.append(SearchResult(
                source=row.source, source_id=row.source_id, url=row.url, title=row.title,
                artist=row.artist, duration=row.duration, thumbnail=row.thumbnail,
                genre=row.genre, bpm=row.bpm, musical_key=row.musical_key,
                mood=row.mood, is_beat=row.is_beat, file_path=row.file_path,
                extra={"cached": True},
            ))
        return out[:limit]

    def _store(self, result: SearchResult) -> None:
        """Сохранить результат в кэш."""
        try:
            from utils.cache_db import TrackRecord

            self._db.upsert_track(TrackRecord(
                source=result.source, source_id=result.source_id, url=result.url,
                title=result.title, artist=result.artist, duration=result.duration,
                thumbnail=result.thumbnail, genre=result.genre, bpm=result.bpm,
                musical_key=result.musical_key, mood=result.mood, is_beat=result.is_beat,
            ))
        except Exception:
            LOG.debug("Не удалось сохранить результат SoundCloud", exc_info=True)
