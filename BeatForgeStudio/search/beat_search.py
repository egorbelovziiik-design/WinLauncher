"""Поиск битов: жанровые пресеты, BPM-фильтр, настроение, BeatStars-превью.

Модуль собирает запросы вида ``"{жанр} type beat {настроение} {bpm} bpm instrumental"``
и умеет искать в трёх местах:

* YouTube — основной источник (биты выкладывают почти все продюсеры);
* SoundCloud — альтернатива;
* BeatStars — публичные превью популярных битов (HTML-страница поиска,
  из неё вытаскиваются заголовки, ссылки и обложки; полные треки не качаются).

Результаты фильтруются по BPM/жанру/настроению и кэшируются в SQLite.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional, Sequence
from urllib.parse import quote_plus

from search.models import (SearchResult, guess_mood, make_beats_query, parse_bpm,
                           parse_key)
from search.soundcloud_search import SoundCloudSearch
from search.youtube_search import SearchError, YouTubeSearch
from utils.cache_db import get_db

LOG = logging.getLogger(__name__)

#: Жанровые пресеты для UI (чипы).
GENRE_PRESETS: Sequence[str] = (
    "Trap", "Drill", "Lo-Fi", "Boom Bap", "R&B", "Pop",
    "Phonk", "Hyperpop", "Jersey Club", "Rage", "Pluggnb",
)

#: Настроения для UI.
MOOD_PRESETS: Sequence[str] = ("sad", "hype", "chill", "dark", "aggressive", "happy")

#: Тональности (для выпадающего списка).
KEY_PRESETS: Sequence[str] = (
    "", "C min", "C# min", "D min", "D# min", "E min", "F min", "F# min",
    "G min", "G# min", "A min", "A# min", "B min",
    "C maj", "D maj", "E maj", "F maj", "G maj", "A maj", "B maj",
)


@dataclass
class BeatFilter:
    """Фильтр для поиска битов.

    Attributes:
        genre: Жанр (из :data:`GENRE_PRESETS`).
        mood: Настроение (``sad``/``hype``/…).
        bpm: Желаемый BPM (или None).
        bpm_tolerance: Допуск по BPM при фильтрации результатов.
        key: Тональность (``"C min"``).
        year: Год в запросе.
        free: Искать бесплатные (``free … type beat``).
        source: ``youtube`` | ``soundcloud`` | ``beatstars`` | ``all``.
        limit: Сколько результатов вернуть.
    """

    genre: str = "Trap"
    mood: str = ""
    bpm: Optional[int] = None
    bpm_tolerance: int = 10
    key: str = ""
    year: Optional[int] = None
    free: bool = False
    source: str = "youtube"
    limit: int = 15

    def query(self) -> str:
        """Сформировать строку запроса."""
        return make_beats_query(self.genre, self.mood, self.bpm, self.key,
                                self.year, self.free)

    def matches(self, result: SearchResult) -> bool:
        """Проверить результат на соответствие BPM/жанру/настроению.

        Args:
            result: Результат поиска.

        Returns:
            True, если результат подходит (мягкая фильтрация: неизвестный BPM
            не отбрасывается, если явно не требуется).
        """
        if self.bpm and result.bpm:
            if abs(result.bpm - self.bpm) > self.bpm_tolerance:
                # BPM может быть вдвое больше/меньше (140 ↔ 70) — это норма для битов
                if not (abs(result.bpm / 2 - self.bpm) <= self.bpm_tolerance or
                        abs(result.bpm * 2 - self.bpm) <= self.bpm_tolerance):
                    return False
        if self.genre and result.genre and result.genre.lower() != self.genre.lower():
            title_low = result.title.lower()
            if self.genre.lower() not in title_low:
                return False
        if self.mood and result.mood and result.mood.lower() != self.mood.lower():
            return False
        return True

    def describe(self) -> str:
        """Человекочитаемое описание фильтра (для заголовка результатов)."""
        bits = [self.genre]
        if self.mood:
            bits.append(self.mood)
        if self.bpm:
            bits.append(f"{self.bpm} BPM ±{self.bpm_tolerance}")
        if self.key:
            bits.append(self.key)
        if self.free:
            bits.append("free")
        return " • ".join(bits)


class BeatSearch:
    """Поиск битов по жанрам и фильтрам."""

    def __init__(self, db: Optional[Any] = None) -> None:
        """Args:
            db: Кэш (по умолчанию глобальный).
        """
        self._db = db or get_db()
        self._yt = YouTubeSearch(self._db)
        self._sc = SoundCloudSearch(self._db)

    # ------------------------------------------------------------------ поиск
    def search(self, flt: BeatFilter) -> List[SearchResult]:
        """Выполнить поиск битов по фильтру.

        Args:
            flt: :class:`BeatFilter`.

        Returns:
            Отфильтрованный список результатов (отсортированный по релевантности).

        Raises:
            SearchError: если все источники недоступны и кэш пуст.
        """
        query = flt.query()
        LOG.info("Поиск битов: %s (%s)", query, flt.source)
        results: List[SearchResult] = []
        errors: List[str] = []

        if flt.source in ("youtube", "all"):
            try:
                results += self._yt.search(query, limit=flt.limit, use_cache=True)
            except SearchError as exc:
                errors.append(f"YouTube: {exc}")
        if flt.source in ("soundcloud", "all"):
            try:
                results += self._sc.search(query, limit=flt.limit, is_beat=True)
            except SearchError as exc:
                errors.append(f"SoundCloud: {exc}")
        if flt.source in ("beatstars", "all"):
            try:
                results += self.search_beatstars(query, limit=flt.limit)
            except SearchError as exc:
                errors.append(f"BeatStars: {exc}")

        for item in results:
            item.is_beat = True
            if not item.genre:
                item.genre = flt.genre
            if not item.mood:
                item.mood = flt.mood or guess_mood(item.title)
            if item.bpm is None:
                item.bpm = parse_bpm(item.title)
            if not item.musical_key:
                item.musical_key = parse_key(item.title)

        filtered = [r for r in results if flt.matches(r)]
        if not filtered and results:
            LOG.info("Фильтр был слишком строгим — отдаю все %s результатов", len(results))
            filtered = results
        if not filtered and errors:
            raise SearchError("; ".join(errors))

        seen: set = set()
        unique: List[SearchResult] = []
        for item in filtered:
            key = (item.source, item.source_id)
            if key in seen:
                continue
            seen.add(key)
            unique.append(item)

        unique.sort(key=lambda r: (r.file_path != "", r.bpm is not None), reverse=True)
        return unique[: flt.limit]

    def quick(self, genre: str, limit: int = 15) -> List[SearchResult]:
        """Быстрый поиск по жанру без фильтров (для чипов)."""
        return self.search(BeatFilter(genre=genre, limit=limit))

    # ------------------------------------------------------------------ BeatStars
    def search_beatstars(self, query: str, limit: int = 15) -> List[SearchResult]:
        """Поиск публичных превью на BeatStars (HTML, без API-ключа).

        Args:
            query: Поисковый запрос (``"trap type beat"``).
            limit: Максимум результатов.

        Returns:
            Список результатов с ссылками на превью.

        Raises:
            SearchError: если страница недоступна или структура изменилась.
        """
        import requests

        url = f"https://www.beatstars.com/search?q={quote_plus(query)}"
        headers = {
            "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                           "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"),
            "Accept-Language": "en-US,en;q=0.9",
        }
        try:
            resp = requests.get(url, headers=headers, timeout=20)
            resp.raise_for_status()
        except Exception as exc:
            raise SearchError(f"BeatStars недоступен: {exc}") from exc

        html = resp.text
        results: List[SearchResult] = []
        # превью-ссылки вида /track/название-id и обложки из CDN
        for match in re.finditer(r'href="(/track/[^"#?]+)"[^>]*>(.*?)</a>', html, re.S):
            path, inner = match.group(1), match.group(2)
            title = re.sub(r"<[^>]+>", " ", inner)
            title = re.sub(r"\s+", " ", title).strip()
            if not title or len(title) < 3:
                continue
            track_id = path.rstrip("/").rsplit("-", 1)[-1]
            results.append(SearchResult(
                source="beatstars",
                source_id=track_id,
                url=f"https://www.beatstars.com{path}",
                title=title,
                artist="",
                duration=0.0,
                thumbnail="",
                genre="",
                bpm=parse_bpm(title),
                musical_key=parse_key(title),
                mood=guess_mood(title),
                is_beat=True,
            ))
            if len(results) >= limit:
                break

        if not results:
            raise SearchError("BeatStars не вернул результатов (возможно, изменилась вёрстка)")
        LOG.info("BeatStars: найдено %s превью", len(results))
        return results

    # ------------------------------------------------------------------ кэш
    def cached_beats(self, genre: str = "", limit: int = 40) -> List[SearchResult]:
        """Биты из локального кэша (мгновенная выдача)."""
        rows = self._db.search_tracks("", limit=limit, only_beats=True, genre=genre)
        return [SearchResult(
            source=r.source, source_id=r.source_id, url=r.url, title=r.title,
            artist=r.artist, duration=r.duration, thumbnail=r.thumbnail, genre=r.genre,
            bpm=r.bpm, musical_key=r.musical_key, mood=r.mood, is_beat=True,
            file_path=r.file_path, extra={"cached": True},
        ) for r in rows]

    def detect_bpm(self, file_path: str) -> Optional[float]:
        """Определить BPM файла через librosa (офлайн, для загруженных битов).

        Args:
            file_path: Путь к аудио.

        Returns:
            BPM (округлённый) или None.
        """
        try:
            import librosa

            y, sr = librosa.load(file_path, sr=22050, mono=True, duration=60)
            tempo, _ = librosa.beat.beat_track(y=y, sr=sr)
            value = float(tempo if not hasattr(tempo, "__len__") else tempo[0])
            return round(value, 1) if value > 0 else None
        except Exception as exc:
            LOG.warning("Не удалось определить BPM %s: %s", file_path, exc)
            return None

    @staticmethod
    def moods() -> Iterable[str]:
        """Доступные настроения (для UI)."""
        return MOOD_PRESETS

    @staticmethod
    def genres() -> Iterable[str]:
        """Доступные жанры (для UI)."""
        return GENRE_PRESETS
