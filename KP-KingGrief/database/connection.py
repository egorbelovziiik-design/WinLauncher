"""
Асинхронная обёртка над SQLite для КП-KingGrief.

Особенности:
  * стандартный модуль `sqlite3` + `asyncio.to_thread` — без лишних зависимостей
    и без проблем при сборке PyInstaller;
  * один поток записи через `asyncio.Lock`, включён WAL;
  * автоматические повторные попытки при `database is locked`;
  * горячие резервные копии через `sqlite3.Connection.backup`;
  * `integrity_check` + автоматическое восстановление из бэкапа при повреждении.
"""

from __future__ import annotations

import asyncio
import shutil
import sqlite3
import time
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from typing import Any, AsyncIterator, Iterable, Sequence

from config import BACKUP_DIR, DB_PATH
from utils.logging import get_logger

log = get_logger("database")

_SQLITE_BUSY = "database is locked"


def _row_to_dict(cursor: sqlite3.Cursor, row: sqlite3.Row) -> dict[str, Any]:
    return {key: row[key] for key in row.keys()}


def split_sql(script: str) -> list[str]:
    """Разбирает SQL-скрипт на отдельные выражения.

    Точка с запятой внутри строковых литералов, кавычек-идентификаторов
    и однострочных комментариев игнорируется.
    """
    statements: list[str] = []
    buffer: list[str] = []
    quote: str | None = None
    index = 0
    length = len(script)

    while index < length:
        char = script[index]

        if quote:
            buffer.append(char)
            if char == quote:
                # удвоенная кавычка внутри литерала — не конец строки
                if index + 1 < length and script[index + 1] == quote:
                    buffer.append(script[index + 1])
                    index += 2
                    continue
                quote = None
            index += 1
            continue

        if char in ("'", '"', "`"):
            quote = char
            buffer.append(char)
            index += 1
            continue

        if char == "-" and script.startswith("--", index):
            while index < length and script[index] != "\n":
                index += 1
            continue

        if char == "/" and script.startswith("/*", index):
            end = script.find("*/", index + 2)
            index = length if end == -1 else end + 2
            continue

        if char == ";":
            statement = "".join(buffer).strip()
            if statement:
                statements.append(statement)
            buffer = []
            index += 1
            continue

        buffer.append(char)
        index += 1

    tail = "".join(buffer).strip()
    if tail:
        statements.append(tail)
    return statements


class AsyncRLock:
    """Реентерабельный asyncio-лок.

    Нужен, чтобы транзакция (`transaction()`) могла вызывать обычные методы
    (`execute`, `script`) без взаимной блокировки: `asyncio.Lock` не реентерабелен.
    """

    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self._owner: asyncio.Task[Any] | None = None
        self._depth = 0

    async def acquire(self) -> bool:
        current = asyncio.current_task()
        if self._owner is current:
            self._depth += 1
            return True
        await self._lock.acquire()
        self._owner = current
        self._depth = 1
        return True

    def release(self) -> None:
        if self._owner is not asyncio.current_task():
            raise RuntimeError("AsyncRLock: разблокировка чужой блокировки")
        self._depth -= 1
        if self._depth <= 0:
            self._owner = None
            self._depth = 0
            self._lock.release()

    async def __aenter__(self) -> "AsyncRLock":
        await self.acquire()
        return self

    async def __aexit__(self, exc_type, exc, tb) -> bool:
        self.release()
        return False



def _sorted_backups(directory: Path) -> list[Path]:
    """Копии от новых к старым (имя содержит timestamp, поэтому сортировка стабильна)."""
    return sorted(
        directory.glob("*.db"),
        key=lambda p: (p.stat().st_mtime, p.name),
        reverse=True,
    )


class Database:
    """Асинхронный доступ к файлу SQLite."""

    def __init__(
        self,
        path: str | Path = DB_PATH,
        *,
        timeout: float = 30.0,
        retries: int = 5,
        retry_delay: float = 0.08,
    ) -> None:
        self.path = Path(path)
        self.timeout = timeout
        self.retries = max(1, retries)
        self.retry_delay = retry_delay
        self._conn: sqlite3.Connection | None = None
        self._lock = AsyncRLock()
        self._closed = False

    # ------------------------------------------------------------------ #
    #  Жизненный цикл
    # ------------------------------------------------------------------ #
    def _connect(self) -> sqlite3.Connection:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        conn = sqlite3.connect(
            str(self.path),
            timeout=self.timeout,
            check_same_thread=False,
            isolation_level=None,  # управляем транзакциями вручную
        )
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA synchronous=NORMAL;")
        conn.execute("PRAGMA foreign_keys=ON;")
        conn.execute("PRAGMA busy_timeout=15000;")
        conn.execute("PRAGMA temp_store=MEMORY;")
        return conn

    async def connect(self) -> "Database":
        """Создаёт подключение и проверяет целостность файла."""
        if self._conn is not None:
            return self
        try:
            self._conn = await asyncio.to_thread(self._connect)
        except sqlite3.DatabaseError as exc:
            log.error("Не удалось открыть БД %s: %s", self.path, exc)
            restored = await self.restore_from_backup()
            if restored:
                self._conn = await asyncio.to_thread(self._connect)
            else:
                raise
        healthy = await self.integrity_check()
        if not healthy:
            log.warning("Файл БД повреждён — пробую восстановить из резервной копии.")
            if await self.restore_from_backup():
                self._conn = await asyncio.to_thread(self._connect)
        log.info("База данных подключена: %s", self.path)
        return self

    async def close(self) -> None:
        if self._conn is None:
            return
        self._closed = True
        conn, self._conn = self._conn, None
        await asyncio.to_thread(conn.close)
        log.info("Соединение с базой данных закрыто.")

    @property
    def connection(self) -> sqlite3.Connection:
        if self._conn is None:
            raise RuntimeError("База данных не подключена. Вызовите await db.connect().")
        return self._conn

    async def ping(self) -> bool:
        """Проверка живости соединения."""
        try:
            await self.fetch_val("SELECT 1")
            return True
        except Exception as exc:  # pragma: no cover - зависит от состояния диска
            log.error("База данных недоступна: %s", exc)
            return False

    # ------------------------------------------------------------------ #
    #  Низкоуровневое выполнение
    # ------------------------------------------------------------------ #
    async def _run(self, func, *args, **kwargs):
        """Выполняет синхронную операцию с повторами при блокировке БД."""
        last: Exception | None = None
        for attempt in range(self.retries):
            try:
                return await asyncio.to_thread(func, *args, **kwargs)
            except sqlite3.OperationalError as exc:
                last = exc
                if _SQLITE_BUSY not in str(exc).lower():
                    raise
                delay = self.retry_delay * (2**attempt)
                log.debug("БД заблокирована (попытка %s), ждём %.2fs", attempt + 1, delay)
                await asyncio.sleep(delay)
        raise last  # type: ignore[misc]

    def _exec(self, sql: str, params: Sequence[Any]) -> sqlite3.Cursor:
        return self.connection.execute(sql, tuple(params))

    def _execmany(self, sql: str, seq: Iterable[Sequence[Any]]) -> sqlite3.Cursor:
        return self.connection.executemany(sql, [tuple(p) for p in seq])

    async def execute(self, sql: str, params: Sequence[Any] = ()) -> int:
        """INSERT/UPDATE/DELETE. Возвращает `rowcount`."""
        async with self._lock:
            def work() -> int:
                cur = self._exec(sql, params)
                return cur.rowcount

            return int(await self._run(work))

    async def insert(self, sql: str, params: Sequence[Any] = ()) -> int:
        """INSERT. Возвращает `lastrowid`."""
        async with self._lock:
            def work() -> int:
                cur = self._exec(sql, params)
                return int(cur.lastrowid or 0)

            return await self._run(work)

    async def execute_many(self, sql: str, seq: Iterable[Sequence[Any]]) -> int:
        async with self._lock:
            def work() -> int:
                cur = self._execmany(sql, seq)
                return cur.rowcount

            return await self._run(work)

    async def fetch_all(self, sql: str, params: Sequence[Any] = ()) -> list[dict[str, Any]]:
        async with self._lock:
            def work() -> list[dict[str, Any]]:
                cur = self._exec(sql, params)
                return [_row_to_dict(cur, row) for row in cur.fetchall()]

            return await self._run(work)

    async def fetch_one(self, sql: str, params: Sequence[Any] = ()) -> dict[str, Any] | None:
        rows = await self.fetch_all(sql, params)
        return rows[0] if rows else None

    async def fetch_val(self, sql: str, params: Sequence[Any] = (), default: Any = None) -> Any:
        row = await self.fetch_one(sql, params)
        if not row:
            return default
        first = next(iter(row.values()))
        return default if first is None else first

    async def script(self, sql_script: str) -> None:
        """Выполняет многострочный SQL-скрипт (миграции, схема).

        Намеренно НЕ использует `executescript`: он неявно делает COMMIT и
        ломает внешнюю транзакцию. Вместо этого скрипт разбирается на
        отдельные выражения и выполняется по одному.
        """
        statements = split_sql(sql_script)
        async with self._lock:
            for sql in statements:
                def work(statement: str = sql) -> None:
                    self.connection.execute(statement)

                await self._run(work)

    @asynccontextmanager
    async def transaction(self) -> AsyncIterator["Database"]:
        """Явная транзакция с откатом при ошибке."""
        await self._lock.acquire()
        began = False
        try:
            if not self.connection.in_transaction:
                self.connection.execute("BEGIN IMMEDIATE;")
                began = True
            yield self
            if began and self.connection.in_transaction:
                self.connection.execute("COMMIT;")
        except Exception:
            if self.connection.in_transaction:
                try:
                    self.connection.execute("ROLLBACK;")
                except sqlite3.Error as exc:  # pragma: no cover
                    log.warning("Не удалось откатить транзакцию: %s", exc)
            raise
        finally:
            self._lock.release()

    # ------------------------------------------------------------------ #
    #  Обслуживание
    # ------------------------------------------------------------------ #
    async def integrity_check(self) -> bool:
        try:
            result = await self.fetch_val("PRAGMA integrity_check;", default=None)
            return str(result).lower() == "ok"
        except Exception as exc:
            log.error("integrity_check завершился ошибкой: %s", exc)
            return False

    async def backup(self, directory: str | Path | None = None, tag: str = "") -> Path | None:
        """Горячая резервная копия БД. Возвращает путь к копии."""
        if self._conn is None:
            return None
        target_dir = Path(directory or BACKUP_DIR)
        target_dir.mkdir(parents=True, exist_ok=True)
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S-%f")
        suffix = f"-{tag}" if tag else ""
        target = target_dir / f"kinggrief-{stamp}{suffix}.db"
        # защита от совпадения имён при нескольких копиях за одну микросекунду
        counter = 1
        while target.exists():
            target = target_dir / f"kinggrief-{stamp}{suffix}-{counter}.db"
            counter += 1

        def work() -> Path:
            source = sqlite3.connect(str(self.path), timeout=self.timeout)
            try:
                dest = sqlite3.connect(str(target))
                try:
                    source.backup(dest)
                finally:
                    dest.close()
            finally:
                source.close()
            return target

        try:
            path = await asyncio.to_thread(work)
            log.info("Резервная копия БД создана: %s", path.name)
            return path
        except Exception as exc:
            log.error("Не удалось создать резервную копию: %s", exc)
            return None

    async def restore_from_backup(self, directory: str | Path | None = None) -> bool:
        """Восстанавливает БД из самой свежей резервной копии."""
        target_dir = Path(directory or BACKUP_DIR)
        if not target_dir.exists():
            return False
        backups = _sorted_backups(target_dir)
        if not backups:
            return False
        newest = backups[0]
        try:
            if self._conn is not None:
                conn, self._conn = self._conn, None
                await asyncio.to_thread(conn.close)
            for suffix in ("", "-wal", "-shm"):
                broken = Path(f"{self.path}{suffix}")
                if broken.exists():
                    broken.rename(broken.with_suffix(broken.suffix + f".corrupt-{int(time.time())}"))
            await asyncio.to_thread(shutil.copy2, str(newest), str(self.path))
            log.warning("База данных восстановлена из копии %s", newest.name)
            return True
        except Exception as exc:
            log.error("Восстановление из резервной копии не удалось: %s", exc)
            return False

    async def prune_backups(self, keep: int = 14, directory: str | Path | None = None) -> int:
        """Удаляет старые копии, оставляя `keep` последних."""
        target_dir = Path(directory or BACKUP_DIR)
        if not target_dir.exists():
            return 0
        backups = _sorted_backups(target_dir)
        removed = 0
        for old in backups[max(1, keep) :]:
            try:
                old.unlink()
                removed += 1
            except OSError as exc:
                log.warning("Не удалось удалить копию %s: %s", old.name, exc)
        return removed

    async def vacuum(self) -> None:
        await self.script("VACUUM;")

    async def size_bytes(self) -> int:
        def work() -> int:
            total = 0
            for suffix in ("", "-wal", "-shm"):
                f = Path(f"{self.path}{suffix}")
                if f.exists():
                    total += f.stat().st_size
            return total

        return await asyncio.to_thread(work)


# Глобальный экземпляр — импортируется по всему проекту.
db = Database()
