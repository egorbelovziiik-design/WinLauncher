"""SQLite: атомарный лимит и статистика отправленных голосовых."""
import asyncio
from contextlib import contextmanager
from collections.abc import Iterator
import sqlite3
import time
from pathlib import Path


class Database:
    def __init__(self, path: Path):
        self.path = path

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self.path, timeout=10)
        try:
            connection.execute('PRAGMA busy_timeout=10000')
            with connection:
                yield connection
        finally:
            connection.close()

    async def init(self) -> None:
        def run() -> None:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            with self._connect() as db:
                db.execute('PRAGMA journal_mode=WAL')
                db.executescript('''
                    CREATE TABLE IF NOT EXISTS limits (user_id INTEGER PRIMARY KEY, last_call REAL NOT NULL);
                    CREATE TABLE IF NOT EXISTS calls (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL,
                        meme TEXT NOT NULL, created_at REAL NOT NULL);
                    CREATE INDEX IF NOT EXISTS calls_user ON calls(user_id);
                ''')
        await asyncio.to_thread(run)

    async def reserve(self, user_id: int, cooldown: int) -> int:
        """Зарезервировать слот; вернуть 0 или число секунд ожидания."""
        def run() -> int:
            import math
            with self._connect() as db:
                db.execute('BEGIN IMMEDIATE')
                now = time.time()
                row = db.execute('SELECT last_call FROM limits WHERE user_id=?', (user_id,)).fetchone()
                if row and now - row[0] < cooldown:
                    return max(1, math.ceil(cooldown - (now - row[0])))
                db.execute('INSERT INTO limits VALUES (?, ?) ON CONFLICT(user_id) DO UPDATE SET last_call=excluded.last_call', (user_id, now))
                return 0
        return await asyncio.to_thread(run)

    async def record_call(self, user_id: int, meme: str) -> None:
        def run() -> None:
            with self._connect() as db:
                db.execute('INSERT INTO calls(user_id, meme, created_at) VALUES (?, ?, ?)', (user_id, meme, time.time()))
        await asyncio.to_thread(run)

    async def stats(self, user_id: int) -> tuple[int, str | None]:
        def run() -> tuple[int, str | None]:
            with self._connect() as db:
                total = db.execute('SELECT count(*) FROM calls WHERE user_id=?', (user_id,)).fetchone()[0]
                favorite = db.execute('SELECT meme FROM calls WHERE user_id=? GROUP BY meme ORDER BY count(*) DESC, meme LIMIT 1', (user_id,)).fetchone()
                return total, favorite[0] if favorite else None
        return await asyncio.to_thread(run)

    async def prune_limits(self) -> None:
        """Удалить давно не используемые ограничения; статистика остаётся."""
        def run() -> None:
            with self._connect() as db:
                db.execute('DELETE FROM limits WHERE last_call < ?', (time.time() - 86400 * 30,))
        await asyncio.to_thread(run)
