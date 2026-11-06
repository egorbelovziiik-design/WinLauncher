"""SQLite-база BeatForge Studio: кэш треков, история поиска, проекты, аккаунты.

Единая точка доступа — :class:`CacheDB` («тонкий» слой над ``sqlite3``).
Соединение создаётся на каждый поток (``check_same_thread`` у SQLite строгий),
поэтому класс хранит путь и открывает соединение через :class:`threading.local`.

Схема (v2):

* ``tracks`` — скачанные/найденные треки (кэш поиска и загрузок);
* ``searches`` — история запросов;
* ``projects`` — недавние проекты (.bfs);
* ``accounts`` — привязанные соцсети (токены в зашифрованном виде);
* ``downloads`` — журнал загрузок с прогрессом;
* ``snippets`` — сохранённые фрагменты/лупы (для будущего).
"""

from __future__ import annotations

import json
import logging
import sqlite3
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, Iterable, Iterator, List, Optional

from utils.config import get_config

LOG = logging.getLogger(__name__)

SCHEMA_VERSION = 2

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE IF NOT EXISTS tracks (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    source       TEXT NOT NULL,              -- youtube | soundcloud | local
    source_id    TEXT NOT NULL,              -- video id / soundcloud id / hash файла
    url          TEXT NOT NULL,
    title        TEXT NOT NULL,
    artist       TEXT DEFAULT '',
    duration     REAL DEFAULT 0,
    thumbnail    TEXT DEFAULT '',
    file_path    TEXT DEFAULT '',
    genre        TEXT DEFAULT '',
    bpm          REAL,
    musical_key  TEXT DEFAULT '',
    mood         TEXT DEFAULT '',
    is_beat      INTEGER DEFAULT 0,
    date_added   REAL NOT NULL,
    last_used    REAL,
    UNIQUE(source, source_id)
);
CREATE INDEX IF NOT EXISTS idx_tracks_title ON tracks(title);
CREATE INDEX IF NOT EXISTS idx_tracks_genre ON tracks(genre);
CREATE INDEX IF NOT EXISTS idx_tracks_added ON tracks(date_added DESC);

CREATE TABLE IF NOT EXISTS searches (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    query     TEXT NOT NULL,
    source    TEXT NOT NULL DEFAULT '',
    kind      TEXT NOT NULL DEFAULT 'music',   -- music | beat
    results   INTEGER DEFAULT 0,
    created   REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_searches_created ON searches(created DESC);

CREATE TABLE IF NOT EXISTS projects (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    path         TEXT NOT NULL UNIQUE,
    name         TEXT NOT NULL,
    kind         TEXT DEFAULT 'audio',        -- audio | video
    duration     REAL DEFAULT 0,
    thumbnail    TEXT DEFAULT '',
    opened_at    REAL NOT NULL,
    created_at   REAL NOT NULL
);

CREATE TABLE IF NOT EXISTS accounts (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    platform      TEXT NOT NULL,             -- youtube | tiktok
    account_id    TEXT NOT NULL,
    display_name  TEXT DEFAULT '',
    avatar_url    TEXT DEFAULT '',
    token_blob    BLOB,
    meta_json     TEXT DEFAULT '{}',
    created_at    REAL NOT NULL,
    updated_at    REAL NOT NULL,
    UNIQUE(platform, account_id)
);

CREATE TABLE IF NOT EXISTS downloads (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    url         TEXT NOT NULL,
    title       TEXT DEFAULT '',
    target_path TEXT DEFAULT '',
    status      TEXT DEFAULT 'pending',      -- pending | running | done | error | canceled
    progress    REAL DEFAULT 0,
    error       TEXT DEFAULT '',
    created_at  REAL NOT NULL,
    finished_at REAL
);

CREATE TABLE IF NOT EXISTS snippets (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    file_path  TEXT NOT NULL,
    start      REAL DEFAULT 0,
    duration   REAL DEFAULT 0,
    tags       TEXT DEFAULT '',
    created_at REAL NOT NULL
);
"""


@dataclass
class TrackRecord:
    """Запись трека в кэше.

    Attributes:
        source: ``youtube`` | ``soundcloud`` | ``local``.
        source_id: Идентификатор в источнике.
        url: Ссылка на страницу/файл.
        title: Название.
        artist: Исполнитель/канал.
        duration: Длительность, сек.
        thumbnail: URL обложки (или локальный путь).
        file_path: Путь к скачанному файлу.
        genre: Жанр (для битов).
        bpm: BPM (если определён).
        musical_key: Тональность.
        mood: Настроение.
        is_beat: Является ли битом.
        id: Первичный ключ (заполняется после вставки).
        date_added: Когда добавлен (unix time).
    """

    source: str
    source_id: str
    url: str
    title: str
    artist: str = ""
    duration: float = 0.0
    thumbnail: str = ""
    file_path: str = ""
    genre: str = ""
    bpm: Optional[float] = None
    musical_key: str = ""
    mood: str = ""
    is_beat: bool = False
    id: Optional[int] = None
    date_added: float = field(default_factory=time.time)

    @property
    def downloaded(self) -> bool:
        """Есть ли локальный файл."""
        return bool(self.file_path) and Path(self.file_path).exists()

    def to_dict(self) -> Dict[str, Any]:
        """Словарь для сериализации (в проекты/UI)."""
        return {
            "id": self.id, "source": self.source, "source_id": self.source_id,
            "url": self.url, "title": self.title, "artist": self.artist,
            "duration": self.duration, "thumbnail": self.thumbnail,
            "file_path": self.file_path, "genre": self.genre, "bpm": self.bpm,
            "musical_key": self.musical_key, "mood": self.mood,
            "is_beat": self.is_beat, "date_added": self.date_added,
        }


class CacheDB:
    """Потокобезопасная обёртка над SQLite-базой приложения."""

    def __init__(self, db_path: Optional[Path | str] = None) -> None:
        """Args:
            db_path: Путь к файлу БД (по умолчанию из конфига).
        """
        self._path = Path(db_path) if db_path else Path(get_config().paths.db_path)
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._local = threading.local()
        self._write_lock = threading.Lock()
        self._init_schema()

    # ------------------------------------------------------------ соединение
    @property
    def path(self) -> Path:
        """Путь к файлу базы."""
        return self._path

    def _conn(self) -> sqlite3.Connection:
        conn = getattr(self._local, "conn", None)
        if conn is None:
            conn = sqlite3.connect(str(self._path), timeout=30.0, check_same_thread=False)
            conn.row_factory = sqlite3.Row
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute("PRAGMA synchronous=NORMAL")
            conn.execute("PRAGMA foreign_keys=ON")
            self._local.conn = conn
        return conn

    @contextmanager
    def transaction(self) -> Iterator[sqlite3.Connection]:
        """Контекстный менеджер транзакции с автоматическим rollback."""
        conn = self._conn()
        with self._write_lock:
            try:
                yield conn
                conn.commit()
            except Exception:
                conn.rollback()
                raise

    def _init_schema(self) -> None:
        with self.transaction() as conn:
            conn.executescript(SCHEMA)
            conn.execute("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_version', ?)",
                         (str(SCHEMA_VERSION),))
        LOG.info("База кэша готова: %s", self._path)

    def close(self) -> None:
        """Закрыть соединение текущего потока."""
        conn = getattr(self._local, "conn", None)
        if conn is not None:
            conn.close()
            self._local.conn = None

    # ------------------------------------------------------------ треки
    def upsert_track(self, track: TrackRecord) -> int:
        """Вставить/обновить трек, вернуть его id.

        Args:
            track: Запись трека.

        Returns:
            Первичный ключ записи.
        """
        with self.transaction() as conn:
            conn.execute(
                """INSERT INTO tracks (source, source_id, url, title, artist, duration,
                       thumbnail, file_path, genre, bpm, musical_key, mood, is_beat, date_added)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                   ON CONFLICT(source, source_id) DO UPDATE SET
                       url=excluded.url, title=excluded.title, artist=excluded.artist,
                       duration=excluded.duration,
                       thumbnail=CASE WHEN excluded.thumbnail<>'' THEN excluded.thumbnail
                                      ELSE tracks.thumbnail END,
                       file_path=CASE WHEN excluded.file_path<>'' THEN excluded.file_path
                                      ELSE tracks.file_path END,
                       genre=CASE WHEN excluded.genre<>'' THEN excluded.genre ELSE tracks.genre END,
                       bpm=COALESCE(excluded.bpm, tracks.bpm),
                       musical_key=CASE WHEN excluded.musical_key<>'' THEN excluded.musical_key
                                        ELSE tracks.musical_key END,
                       mood=CASE WHEN excluded.mood<>'' THEN excluded.mood ELSE tracks.mood END,
                       is_beat=MAX(tracks.is_beat, excluded.is_beat)""",
                (track.source, track.source_id, track.url, track.title, track.artist,
                 track.duration, track.thumbnail, track.file_path, track.genre, track.bpm,
                 track.musical_key, track.mood, int(track.is_beat), track.date_added),
            )
            row = conn.execute("SELECT id FROM tracks WHERE source=? AND source_id=?",
                               (track.source, track.source_id)).fetchone()
        track.id = int(row["id"]) if row else None
        return int(track.id or 0)

    def set_track_file(self, source: str, source_id: str, file_path: str,
                       duration: Optional[float] = None) -> None:
        """Отметить, что трек скачан (обновить путь/длительность)."""
        with self.transaction() as conn:
            conn.execute(
                "UPDATE tracks SET file_path=?, duration=COALESCE(?, duration), last_used=? "
                "WHERE source=? AND source_id=?",
                (str(file_path), duration, time.time(), source, source_id),
            )

    def get_track(self, source: str, source_id: str) -> Optional[TrackRecord]:
        """Найти трек по источнику и id."""
        row = self._conn().execute(
            "SELECT * FROM tracks WHERE source=? AND source_id=?", (source, source_id)
        ).fetchone()
        return _row_to_track(row) if row else None

    def search_tracks(self, query: str, limit: int = 30, only_beats: bool = False,
                      genre: str = "", bpm_min: float = 0.0, bpm_max: float = 300.0
                      ) -> List[TrackRecord]:
        """Найти треки в локальном кэше по подстроке.

        Args:
            query: Текст запроса (ищется в title/artist/genre).
            limit: Максимум результатов.
            only_beats: Только биты.
            genre: Фильтр жанра.
            bpm_min, bpm_max: Диапазон BPM.

        Returns:
            Список записей, сначала недавно использованные.
        """
        sql = ["SELECT * FROM tracks WHERE 1=1"]
        params: List[Any] = []
        if query:
            sql.append("AND (title LIKE ? OR artist LIKE ? OR genre LIKE ?)")
            like = f"%{query}%"
            params += [like, like, like]
        if only_beats:
            sql.append("AND is_beat=1")
        if genre:
            sql.append("AND genre=?")
            params.append(genre)
        if bpm_min or bpm_max < 300:
            sql.append("AND bpm IS NOT NULL AND bpm BETWEEN ? AND ?")
            params += [bpm_min, bpm_max]
        sql.append("ORDER BY COALESCE(last_used, date_added) DESC LIMIT ?")
        params.append(int(limit))
        rows = self._conn().execute(" ".join(sql), params).fetchall()
        return [_row_to_track(r) for r in rows]

    def recent_tracks(self, limit: int = 20, downloaded_only: bool = False) -> List[TrackRecord]:
        """Недавние треки (для браузера медиа)."""
        sql = "SELECT * FROM tracks"
        if downloaded_only:
            sql += " WHERE file_path<>''"
        sql += " ORDER BY date_added DESC LIMIT ?"
        rows = self._conn().execute(sql, (int(limit),)).fetchall()
        return [_row_to_track(r) for r in rows]

    def delete_track(self, track_id: int) -> None:
        """Удалить запись трека (файл на диске не трогается)."""
        with self.transaction() as conn:
            conn.execute("DELETE FROM tracks WHERE id=?", (int(track_id),))

    def touch_track(self, track_id: int) -> None:
        """Обновить время последнего использования (влияет на сортировку)."""
        with self.transaction() as conn:
            conn.execute("UPDATE tracks SET last_used=? WHERE id=?", (time.time(), int(track_id)))

    # ------------------------------------------------------------ поиск: история
    def add_search(self, query: str, source: str = "", kind: str = "music",
                   results: int = 0) -> None:
        """Записать запрос в историю поиска."""
        with self.transaction() as conn:
            conn.execute(
                "INSERT INTO searches(query, source, kind, results, created) VALUES(?,?,?,?,?)",
                (query.strip(), source, kind, int(results), time.time()),
            )
            conn.execute(
                "DELETE FROM searches WHERE id NOT IN "
                "(SELECT id FROM searches ORDER BY created DESC LIMIT 300)"
            )

    def search_history(self, limit: int = 20, kind: Optional[str] = None) -> List[str]:
        """Уникальные недавние запросы (для QCompleter)."""
        sql = "SELECT query, MAX(created) AS c FROM searches"
        params: List[Any] = []
        if kind:
            sql += " WHERE kind=?"
            params.append(kind)
        sql += " GROUP BY query ORDER BY c DESC LIMIT ?"
        params.append(int(limit))
        return [r["query"] for r in self._conn().execute(sql, params).fetchall()]

    def clear_history(self) -> None:
        """Очистить историю поиска."""
        with self.transaction() as conn:
            conn.execute("DELETE FROM searches")

    # ------------------------------------------------------------ проекты
    def remember_project(self, path: str | Path, name: str, kind: str = "audio",
                         duration: float = 0.0, thumbnail: str = "") -> None:
        """Добавить/обновить проект в списке недавних."""
        now = time.time()
        with self.transaction() as conn:
            conn.execute(
                """INSERT INTO projects(path, name, kind, duration, thumbnail, opened_at, created_at)
                   VALUES(?,?,?,?,?,?,?)
                   ON CONFLICT(path) DO UPDATE SET
                       name=excluded.name, kind=excluded.kind, duration=excluded.duration,
                       thumbnail=CASE WHEN excluded.thumbnail<>'' THEN excluded.thumbnail
                                      ELSE projects.thumbnail END,
                       opened_at=excluded.opened_at""",
                (str(path), name, kind, float(duration), thumbnail, now, now),
            )

    def recent_projects(self, limit: int = 12) -> List[Dict[str, Any]]:
        """Недавние проекты (для стартового экрана)."""
        rows = self._conn().execute(
            "SELECT * FROM projects ORDER BY opened_at DESC LIMIT ?", (int(limit),)
        ).fetchall()
        return [dict(r) for r in rows]

    def forget_project(self, path: str | Path) -> None:
        """Убрать проект из недавних."""
        with self.transaction() as conn:
            conn.execute("DELETE FROM projects WHERE path=?", (str(path),))

    # ------------------------------------------------------------ аккаунты
    def upsert_account(self, platform: str, account_id: str, display_name: str = "",
                       avatar_url: str = "", token_blob: Optional[bytes] = None,
                       meta: Optional[Dict[str, Any]] = None) -> int:
        """Сохранить/обновить привязанный аккаунт.

        Args:
            platform: ``youtube`` | ``tiktok``.
            account_id: Стабильный id аккаунта в платформе.
            display_name: Отображаемое имя/канал.
            avatar_url: Ссылка на аватар.
            token_blob: Зашифрованные токены (bytes).
            meta: Доп. данные (scopes, expiry и т.п.).

        Returns:
            id записи.
        """
        now = time.time()
        meta_json = json.dumps(meta or {}, ensure_ascii=False)
        with self.transaction() as conn:
            conn.execute(
                """INSERT INTO accounts(platform, account_id, display_name, avatar_url,
                        token_blob, meta_json, created_at, updated_at)
                   VALUES(?,?,?,?,?,?,?,?)
                   ON CONFLICT(platform, account_id) DO UPDATE SET
                       display_name=excluded.display_name,
                       avatar_url=CASE WHEN excluded.avatar_url<>'' THEN excluded.avatar_url
                                       ELSE accounts.avatar_url END,
                       token_blob=COALESCE(excluded.token_blob, accounts.token_blob),
                       meta_json=excluded.meta_json, updated_at=excluded.updated_at""",
                (platform, account_id, display_name, avatar_url, token_blob, meta_json, now, now),
            )
            row = conn.execute(
                "SELECT id FROM accounts WHERE platform=? AND account_id=?",
                (platform, account_id)).fetchone()
        return int(row["id"]) if row else 0

    def accounts(self, platform: Optional[str] = None) -> List[Dict[str, Any]]:
        """Список привязанных аккаунтов."""
        sql = "SELECT * FROM accounts"
        params: List[Any] = []
        if platform:
            sql += " WHERE platform=?"
            params.append(platform)
        sql += " ORDER BY created_at DESC"
        out = []
        for row in self._conn().execute(sql, params).fetchall():
            item = dict(row)
            try:
                item["meta"] = json.loads(item.pop("meta_json") or "{}")
            except json.JSONDecodeError:
                item["meta"] = {}
            item["has_token"] = bool(item.get("token_blob"))
            item.pop("token_blob", None)
            out.append(item)
        return out

    def account_token(self, platform: str, account_id: str) -> Optional[bytes]:
        """Зашифрованные токены аккаунта."""
        row = self._conn().execute(
            "SELECT token_blob FROM accounts WHERE platform=? AND account_id=?",
            (platform, account_id)).fetchone()
        return bytes(row["token_blob"]) if row and row["token_blob"] else None

    def delete_account(self, account_id: int) -> None:
        """Отвязать аккаунт."""
        with self.transaction() as conn:
            conn.execute("DELETE FROM accounts WHERE id=?", (int(account_id),))

    # ------------------------------------------------------------ загрузки
    def start_download(self, url: str, title: str = "", target: str = "") -> int:
        """Создать запись о загрузке, вернуть её id."""
        with self.transaction() as conn:
            cur = conn.execute(
                "INSERT INTO downloads(url, title, target_path, status, created_at) "
                "VALUES(?,?,?,'pending',?)",
                (url, title, target, time.time()),
            )
            return int(cur.lastrowid)

    def update_download(self, download_id: int, *, status: Optional[str] = None,
                        progress: Optional[float] = None, error: Optional[str] = None,
                        target: Optional[str] = None) -> None:
        """Обновить статус/прогресс загрузки."""
        fields: List[str] = []
        params: List[Any] = []
        if status:
            fields.append("status=?")
            params.append(status)
            if status in ("done", "error", "canceled"):
                fields.append("finished_at=?")
                params.append(time.time())
        if progress is not None:
            fields.append("progress=?")
            params.append(float(progress))
        if error is not None:
            fields.append("error=?")
            params.append(error[:2000])
        if target:
            fields.append("target_path=?")
            params.append(target)
        if not fields:
            return
        params.append(int(download_id))
        with self.transaction() as conn:
            conn.execute(f"UPDATE downloads SET {', '.join(fields)} WHERE id=?", params)

    def downloads(self, limit: int = 50) -> List[Dict[str, Any]]:
        """Последние загрузки."""
        rows = self._conn().execute(
            "SELECT * FROM downloads ORDER BY created_at DESC LIMIT ?", (int(limit),)
        ).fetchall()
        return [dict(r) for r in rows]

    # ------------------------------------------------------------ сервис
    def stats(self) -> Dict[str, int]:
        """Количество записей по таблицам (для экрана настроек)."""
        conn = self._conn()
        out: Dict[str, int] = {}
        for table in ("tracks", "searches", "projects", "accounts", "downloads", "snippets"):
            row = conn.execute(f"SELECT COUNT(*) AS c FROM {table}").fetchone()
            out[table] = int(row["c"]) if row else 0
        try:
            out["file_size"] = int(self._path.stat().st_size)
        except OSError:
            out["file_size"] = 0
        return out

    def clear_cache(self, keep_accounts: bool = True) -> None:
        """Очистить кэш треков/поиска/загрузок (аккаунты можно сохранить)."""
        tables = ["tracks", "searches", "downloads", "snippets"]
        if not keep_accounts:
            tables.append("accounts")
        with self.transaction() as conn:
            for table in tables:
                conn.execute(f"DELETE FROM {table}")
        self.vacuum()  # VACUUM нельзя запускать внутри транзакции
        LOG.info("Кэш очищен: %s", ", ".join(tables))

    def vacuum(self) -> None:
        """Сжать файл базы (вне транзакции)."""
        conn = self._conn()
        conn.isolation_level = None
        try:
            conn.execute("VACUUM")
        finally:
            conn.isolation_level = ""


def _row_to_track(row: sqlite3.Row) -> TrackRecord:
    """Преобразовать строку SQLite в :class:`TrackRecord`."""
    return TrackRecord(
        id=row["id"], source=row["source"], source_id=row["source_id"], url=row["url"],
        title=row["title"], artist=row["artist"] or "", duration=row["duration"] or 0.0,
        thumbnail=row["thumbnail"] or "", file_path=row["file_path"] or "",
        genre=row["genre"] or "", bpm=row["bpm"], musical_key=row["musical_key"] or "",
        mood=row["mood"] or "", is_beat=bool(row["is_beat"]), date_added=row["date_added"],
    )


_db: Optional[CacheDB] = None


def get_db(reload: bool = False) -> CacheDB:
    """Вернуть глобальный экземпляр :class:`CacheDB`."""
    global _db
    if _db is None or reload:
        _db = CacheDB()
    return _db
