"""
Миграции и схема базы данных КП-KingGrief.

Версии схемы хранятся в таблице `schema_migrations` и применяются строго
последовательно. Перед первым применением миграций создаётся резервная копия
(если файл БД уже существовал).
"""

from __future__ import annotations

from typing import Awaitable, Callable

from database.connection import Database, db
from utils.logging import get_logger

log = get_logger("migrations")

Migration = Callable[[Database], Awaitable[None]]


async def _v1_initial(database: Database) -> None:
    """Базовая схема: 8 таблиц из технического задания."""
    await database.script(
        """
        CREATE TABLE IF NOT EXISTS staff (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id      INTEGER NOT NULL,
            user_id       INTEGER NOT NULL,
            username      TEXT    NOT NULL DEFAULT '',
            display_name  TEXT    NOT NULL DEFAULT '',
            position      TEXT    NOT NULL DEFAULT '',
            role_id       INTEGER NOT NULL DEFAULT 0,
            assigned_at   TEXT    NOT NULL DEFAULT '',
            left_at       TEXT,
            is_active     INTEGER NOT NULL DEFAULT 1,
            report_streak INTEGER NOT NULL DEFAULT 0,
            total_reports INTEGER NOT NULL DEFAULT 0,
            created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
            updated_at    TEXT    NOT NULL DEFAULT (datetime('now')),
            UNIQUE (guild_id, user_id)
        );
        CREATE INDEX IF NOT EXISTS idx_staff_guild_active ON staff (guild_id, is_active);
        CREATE INDEX IF NOT EXISTS idx_staff_position     ON staff (guild_id, position);

        CREATE TABLE IF NOT EXISTS staff_roles (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id   INTEGER NOT NULL,
            user_id    INTEGER NOT NULL,
            role_id    INTEGER NOT NULL,
            role_name  TEXT    NOT NULL DEFAULT '',
            priority   INTEGER NOT NULL DEFAULT 0,
            granted_at TEXT    NOT NULL DEFAULT (datetime('now')),
            revoked_at TEXT,
            is_active  INTEGER NOT NULL DEFAULT 1,
            UNIQUE (guild_id, user_id, role_id)
        );
        CREATE INDEX IF NOT EXISTS idx_staff_roles_user ON staff_roles (guild_id, user_id, is_active);

        CREATE TABLE IF NOT EXISTS reports (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id         INTEGER NOT NULL,
            channel_id       INTEGER NOT NULL DEFAULT 0,
            message_id       INTEGER NOT NULL,
            user_id          INTEGER NOT NULL,
            username         TEXT    NOT NULL DEFAULT '',
            position         TEXT    NOT NULL DEFAULT '',
            content          TEXT    NOT NULL DEFAULT '',
            attachment_count INTEGER NOT NULL DEFAULT 0,
            message_url      TEXT    NOT NULL DEFAULT '',
            content_hash     TEXT    NOT NULL DEFAULT '',
            match_score      INTEGER NOT NULL DEFAULT 0,
            is_valid         INTEGER NOT NULL DEFAULT 1,
            invalid_reason   TEXT,
            status           TEXT    NOT NULL DEFAULT 'pending',
            decided_by       INTEGER,
            decided_at       TEXT,
            decision_reason  TEXT,
            period_day       TEXT    NOT NULL DEFAULT '',
            period_week      TEXT    NOT NULL DEFAULT '',
            period_month     TEXT    NOT NULL DEFAULT '',
            created_at       TEXT    NOT NULL,
            updated_at       TEXT    NOT NULL DEFAULT (datetime('now')),
            deleted_at       TEXT,
            UNIQUE (message_id)
        );
        CREATE INDEX IF NOT EXISTS idx_reports_user   ON reports (guild_id, user_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_reports_week   ON reports (guild_id, period_week);
        CREATE INDEX IF NOT EXISTS idx_reports_status ON reports (guild_id, status);

        CREATE TABLE IF NOT EXISTS warnings (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id     INTEGER NOT NULL,
            user_id      INTEGER NOT NULL,
            issued_by    INTEGER NOT NULL DEFAULT 0,
            reason       TEXT    NOT NULL DEFAULT '',
            created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
            is_active    INTEGER NOT NULL DEFAULT 1,
            removed_by   INTEGER,
            removed_at   TEXT,
            expires_at   TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_warnings_user ON warnings (guild_id, user_id, is_active);

        CREATE TABLE IF NOT EXISTS punishments (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id     INTEGER NOT NULL,
            user_id      INTEGER NOT NULL,
            action       TEXT    NOT NULL,
            detail       TEXT    NOT NULL DEFAULT '',
            role_id      INTEGER,
            role_name    TEXT,
            performed_by INTEGER NOT NULL DEFAULT 0,
            status       TEXT    NOT NULL DEFAULT 'completed',
            metadata     TEXT,
            created_at   TEXT    NOT NULL DEFAULT (datetime('now')),
            resolved_at  TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_punish_user   ON punishments (guild_id, user_id);
        CREATE INDEX IF NOT EXISTS idx_punish_status ON punishments (guild_id, status);

        CREATE TABLE IF NOT EXISTS settings (
            guild_id   INTEGER NOT NULL,
            key        TEXT    NOT NULL,
            value      TEXT,
            updated_at TEXT    NOT NULL DEFAULT (datetime('now')),
            PRIMARY KEY (guild_id, key)
        );

        CREATE TABLE IF NOT EXISTS audit_logs (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id   INTEGER NOT NULL,
            actor_id   INTEGER NOT NULL DEFAULT 0,
            action     TEXT    NOT NULL,
            target_id  INTEGER NOT NULL DEFAULT 0,
            detail     TEXT    NOT NULL DEFAULT '',
            created_at TEXT    NOT NULL DEFAULT (datetime('now'))
        );
        CREATE INDEX IF NOT EXISTS idx_audit_guild ON audit_logs (guild_id, created_at);
        CREATE INDEX IF NOT EXISTS idx_audit_user  ON audit_logs (target_id);

        CREATE TABLE IF NOT EXISTS report_periods (
            id           INTEGER PRIMARY KEY AUTOINCREMENT,
            guild_id     INTEGER NOT NULL,
            user_id      INTEGER NOT NULL,
            period_key   TEXT    NOT NULL,
            period_type  TEXT    NOT NULL DEFAULT 'week',
            report_count INTEGER NOT NULL DEFAULT 0,
            accepted     INTEGER NOT NULL DEFAULT 0,
            submitted    INTEGER NOT NULL DEFAULT 0,
            checked_at   TEXT    NOT NULL DEFAULT (datetime('now')),
            UNIQUE (guild_id, user_id, period_key)
        );
        CREATE INDEX IF NOT EXISTS idx_periods_key ON report_periods (guild_id, period_key);
        """
    )


async def _v2_channel_cache(database: Database) -> None:
    """Кэш автоматически найденных каналов и ролей сервера."""
    await database.script(
        """
        CREATE TABLE IF NOT EXISTS channel_cache (
            guild_id     INTEGER NOT NULL,
            channel_id   INTEGER NOT NULL,
            name         TEXT    NOT NULL DEFAULT '',
            category_id  INTEGER NOT NULL DEFAULT 0,
            slot         TEXT,
            discovered_at TEXT   NOT NULL DEFAULT (datetime('now')),
            PRIMARY KEY (guild_id, channel_id)
        );
        CREATE TABLE IF NOT EXISTS role_cache (
            guild_id    INTEGER NOT NULL,
            role_id     INTEGER NOT NULL,
            name        TEXT    NOT NULL DEFAULT '',
            is_controlled INTEGER NOT NULL DEFAULT 0,
            position    INTEGER NOT NULL DEFAULT 0,
            discovered_at TEXT  NOT NULL DEFAULT (datetime('now')),
            PRIMARY KEY (guild_id, role_id)
        );
        """
    )


async def _v3_bot_state(database: Database) -> None:
    """Служебная таблица состояния процесса (watchdog, last_seen)."""
    await database.script(
        """
        CREATE TABLE IF NOT EXISTS bot_state (
            key        TEXT    NOT NULL,
            guild_id   INTEGER NOT NULL DEFAULT 0,
            value      TEXT,
            updated_at TEXT    NOT NULL DEFAULT (datetime('now')),
            PRIMARY KEY (key, guild_id)
        );
        """
    )


MIGRATIONS: list[tuple[int, str, Migration]] = [
    (1, "initial_schema", _v1_initial),
    (2, "channel_and_role_cache", _v2_channel_cache),
    (3, "bot_state", _v3_bot_state),
]


async def current_version(database: Database) -> int:
    await database.script(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version    INTEGER PRIMARY KEY,
            name       TEXT NOT NULL DEFAULT '',
            applied_at TEXT NOT NULL DEFAULT (datetime('now'))
        );
        """
    )
    value = await database.fetch_val(
        "SELECT MAX(version) FROM schema_migrations;", default=0
    )
    return int(value or 0)


async def migrate(database: Database | None = None) -> list[str]:
    """Применяет все неприменённые миграции. Возвращает список применённых."""
    database = database or db
    await database.connect()
    version = await current_version(database)
    applied: list[str] = []

    if database.path.exists() and database.path.stat().st_size > 0 and version == 0:
        await database.backup(tag="pre-migration")

    for number, name, func in MIGRATIONS:
        if number <= version:
            continue
        log.info("Применяю миграцию v%s (%s)…", number, name)
        async with database.transaction():
            await func(database)
            await database.execute(
                "INSERT OR REPLACE INTO schema_migrations (version, name) VALUES (?, ?);",
                (number, name),
            )
        applied.append(f"v{number}:{name}")
        log.info("Миграция v%s применена.", number)

    if not applied:
        log.info("Схема базы данных актуальна (v%s).", version)
    return applied


async def ensure_ready(database: Database | None = None) -> Database:
    """Подключается к БД и доводит схему до актуального состояния."""
    database = database or db
    await database.connect()
    await migrate(database)
    return database
