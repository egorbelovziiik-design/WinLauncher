"""Единая модель результатов поиска для YouTube / SoundCloud / BeatStars."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any, Dict, Optional


@dataclass
class SearchResult:
    """Один результат поиска (трек или бит).

    Attributes:
        source: ``youtube`` | ``soundcloud`` | ``beatstars`` | ``local``.
        source_id: Идентификатор в источнике.
        url: Ссылка на страницу.
        title: Название.
        artist: Канал/исполнитель.
        duration: Длительность, сек.
        thumbnail: URL обложки.
        genre: Жанр (для битов).
        bpm: BPM (если известен/распознан).
        musical_key: Тональность.
        mood: Настроение.
        is_beat: Это бит.
        file_path: Локальный файл (если уже скачан).
        extra: Прочие данные (просмотры, дата загрузки и т.п.).
    """

    source: str
    source_id: str
    url: str
    title: str
    artist: str = ""
    duration: float = 0.0
    thumbnail: str = ""
    genre: str = ""
    bpm: Optional[float] = None
    musical_key: str = ""
    mood: str = ""
    is_beat: bool = False
    file_path: str = ""
    extra: Dict[str, Any] = field(default_factory=dict)

    @property
    def duration_text(self) -> str:
        """Длительность в виде ``M:SS``."""
        seconds = int(max(0, self.duration))
        minutes, sec = divmod(seconds, 60)
        hours, minutes = divmod(minutes, 60)
        return f"{hours}:{minutes:02d}:{sec:02d}" if hours else f"{minutes}:{sec:02d}"

    @property
    def bpm_text(self) -> str:
        """BPM как строка (``—`` если неизвестен)."""
        return f"{int(round(self.bpm))} BPM" if self.bpm else "—"

    @property
    def subtitle(self) -> str:
        """Строка для второй линии карточки."""
        parts = [self.artist or "неизвестный автор"]
        if self.bpm:
            parts.append(f"{int(round(self.bpm))} BPM")
        if self.musical_key:
            parts.append(self.musical_key)
        return " • ".join(parts)

    def to_dict(self) -> Dict[str, Any]:
        """Сериализация в словарь (для кэша/проекта)."""
        return {
            "source": self.source, "source_id": self.source_id, "url": self.url,
            "title": self.title, "artist": self.artist, "duration": self.duration,
            "thumbnail": self.thumbnail, "genre": self.genre, "bpm": self.bpm,
            "musical_key": self.musical_key, "mood": self.mood, "is_beat": self.is_beat,
            "file_path": self.file_path, "extra": self.extra,
        }

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "SearchResult":
        """Десериализация с игнорированием лишних ключей."""
        known = {f for f in cls.__dataclass_fields__}  # type: ignore[attr-defined]
        return cls(**{k: v for k, v in data.items() if k in known})


#: Регулярка для BPM в названии: «140bpm», «BPM 140», «140 BPM».
_BPM_RE = re.compile(r"\b(\d{2,3})\s*bpm\b|\bbpm\s*(\d{2,3})\b", re.IGNORECASE)
#: Регулярка для тональности: «C min», «F#maj», «Abm», «Fm».
_KEY_RE = re.compile(r"\b([A-G][#b]?)\s?(min|maj|m|minor|major)\b", re.IGNORECASE)
#: Настроения для эвристики по названию.
MOOD_WORDS = {
    "sad": ("sad", "emotional", "melancholy", "cry", "грустн", "печаль"),
    "hype": ("hype", "hard", "aggressive", "banger", "энерг", "жестк"),
    "chill": ("chill", "relax", "lofi", "lo-fi", "calm", "спокой"),
    "dark": ("dark", "evil", "horror", "тьм", "мрач"),
    "aggressive": ("aggressive", "rage", "distort", "зл"),
    "happy": ("happy", "bright", "summer", "sunny", "весел"),
}
#: Жанры для распознавания в названии.
GENRE_WORDS = {
    "Trap": ("trap", "трэп"),
    "Drill": ("drill", "дрилл"),
    "Lo-Fi": ("lofi", "lo-fi", "лофай"),
    "Boom Bap": ("boom bap", "boombap", "бумбап"),
    "R&B": ("r&b", "rnb", "рнб"),
    "Pop": ("pop", "поп"),
    "Phonk": ("phonk", "фонк"),
    "Hyperpop": ("hyperpop",),
    "Jersey Club": ("jersey", "джерси"),
    "Rage": ("rage", "рэйдж"),
    "Pluggnb": ("pluggnb", "plug"),
}


def parse_bpm(text: str) -> Optional[float]:
    """Достать BPM из названия трека.

    Args:
        text: Название/описание.

    Returns:
        BPM или None.
    """
    match = _BPM_RE.search(text or "")
    if not match:
        return None
    value = match.group(1) or match.group(2)
    try:
        bpm = float(value)
    except (TypeError, ValueError):
        return None
    return bpm if 40 <= bpm <= 260 else None


def parse_key(text: str) -> str:
    """Достать тональность из названия (``C min``, ``F#maj``)."""
    match = _KEY_RE.search(text or "")
    if not match:
        return ""
    root, quality = match.group(1), match.group(2).lower()
    short = "m" if quality.startswith("m") and not quality.startswith("maj") else "maj"
    return f"{root}{short}"


def guess_mood(text: str) -> str:
    """Определить настроение по тексту названия."""
    low = (text or "").lower()
    for mood, words in MOOD_WORDS.items():
        if any(word in low for word in words):
            return mood
    return ""


def guess_genre(text: str) -> str:
    """Определить жанр по тексту названия."""
    low = (text or "").lower()
    for genre, words in GENRE_WORDS.items():
        if any(word in low for word in words):
            return genre
    return ""


def make_beats_query(genre: str, mood: str = "", bpm: Optional[int] = None,
                     key: str = "", year: Optional[int] = None, free: bool = False) -> str:
    """Сформировать поисковый запрос для битов.

    Args:
        genre: Жанр (Trap, Drill, …).
        mood: Настроение (sad/hype/chill/dark).
        bpm: BPM (добавляется в запрос, если задан).
        key: Тональность.
        year: Год.
        free: Искать бесплатные биты.

    Returns:
        Строка запроса, например ``"Trap type beat sad 140 bpm instrumental 2026"``.
    """
    parts = []
    if free:
        parts.append("free")
    parts.append(f"{genre} type beat" if genre else "type beat")
    if mood:
        parts.append(mood)
    if bpm:
        parts.append(f"{int(bpm)} bpm")
    if key:
        parts.append(key)
    parts.append("instrumental")
    if year:
        parts.append(str(year))
    return " ".join(parts)
