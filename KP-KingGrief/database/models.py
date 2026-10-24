"""
Модели и репозитории КП-KingGrief.

Каждая таблица из ТЗ закрыта своим репозиторием:
staff · staff_roles · reports · warnings · punishments · settings ·
audit_logs · report_periods.
"""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass, field
from datetime import datetime, timedelta
from typing import Any, Iterable, Sequence

from config import (
    CONTROLLED_ROLES,
    DEFAULT_SETTINGS,
    ReportStatus,
    dumps,
    loads,
)
from database.connection import Database, db as _global_db
from utils.periods import from_db, now, period_key, period_range, to_db

# --------------------------------------------------------------------------- #
#  Data-классы
# --------------------------------------------------------------------------- #


def as_row(obj: Any) -> dict[str, Any]:
    """Преобразует dataclass (в т.ч. со `slots=True`) в обычный словарь."""
    return asdict(obj)


@dataclass(slots=True)
class Staff:
    guild_id: int
    user_id: int
    username: str = ""
    display_name: str = ""
    position: str = ""
    role_id: int = 0
    assigned_at: str = ""
    left_at: str | None = None
    is_active: bool = True
    total_reports: int = 0
    id: int = 0

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "Staff":
        return cls(
            id=int(row["id"]),
            guild_id=int(row["guild_id"]),
            user_id=int(row["user_id"]),
            username=row.get("username") or "",
            display_name=row.get("display_name") or "",
            position=row.get("position") or "",
            role_id=int(row.get("role_id") or 0),
            assigned_at=row.get("assigned_at") or "",
            left_at=row.get("left_at"),
            is_active=bool(row.get("is_active", 1)),
            total_reports=int(row.get("total_reports") or 0),
        )


@dataclass(slots=True)
class StaffRole:
    guild_id: int
    user_id: int
    role_id: int
    role_name: str = ""
    priority: int = 0
    granted_at: str = ""
    revoked_at: str | None = None
    is_active: bool = True

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "StaffRole":
        return cls(
            guild_id=int(row["guild_id"]),
            user_id=int(row["user_id"]),
            role_id=int(row["role_id"]),
            role_name=row.get("role_name") or "",
            priority=int(row.get("priority") or 0),
            granted_at=row.get("granted_at") or "",
            revoked_at=row.get("revoked_at"),
            is_active=bool(row.get("is_active", 1)),
        )


@dataclass(slots=True)
class Report:
    guild_id: int
    user_id: int
    message_id: int
    channel_id: int = 0
    username: str = ""
    position: str = ""
    content: str = ""
    attachment_count: int = 0
    message_url: str = ""
    status: str = ReportStatus.PENDING
    created_at: str = ""
    period_day: str = ""
    period_week: str = ""
    period_month: str = ""
    decided_by: int | None = None
    decided_at: str | None = None
    id: int = 0

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "Report":
        return cls(
            id=int(row["id"]),
            guild_id=int(row["guild_id"]),
            user_id=int(row["user_id"]),
            message_id=int(row["message_id"]),
            channel_id=int(row.get("channel_id") or 0),
            username=row.get("username") or "",
            position=row.get("position") or "",
            content=row.get("content") or "",
            attachment_count=int(row.get("attachment_count") or 0),
            message_url=row.get("message_url") or "",
            status=row.get("status") or ReportStatus.PENDING,
            created_at=row.get("created_at") or "",
            period_day=row.get("period_day") or "",
            period_week=row.get("period_week") or "",
            period_month=row.get("period_month") or "",
            decided_by=row.get("decided_by"),
            decided_at=row.get("decided_at"),
        )


@dataclass(slots=True)
class Warning:
    guild_id: int
    user_id: int
    issued_by: int = 0
    reason: str = ""
    created_at: str = ""
    is_active: bool = True
    id: int = 0

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "Warning":
        return cls(
            id=int(row["id"]),
            guild_id=int(row["guild_id"]),
            user_id=int(row["user_id"]),
            issued_by=int(row.get("issued_by") or 0),
            reason=row.get("reason") or "",
            created_at=row.get("created_at") or "",
            is_active=bool(row.get("is_active", 1)),
        )


@dataclass(slots=True)
class Punishment:
    guild_id: int
    user_id: int
    action: str
    detail: str = ""
    performed_by: int = 0
    role_id: int | None = None
    role_name: str | None = None
    status: str = "completed"
    metadata: dict[str, Any] = field(default_factory=dict)
    created_at: str = ""
    id: int = 0

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "Punishment":
        return cls(
            id=int(row["id"]),
            guild_id=int(row["guild_id"]),
            user_id=int(row["user_id"]),
            action=row.get("action") or "",
            detail=row.get("detail") or "",
            performed_by=int(row.get("performed_by") or 0),
            role_id=row.get("role_id"),
            role_name=row.get("role_name"),
            status=row.get("status") or "completed",
            metadata=loads(row.get("metadata"), {}) or {},
            created_at=row.get("created_at") or "",
        )


# --------------------------------------------------------------------------- #
#  Настройки
# --------------------------------------------------------------------------- #
class SettingsRepo:
    """Хранилище настроек сервера (таблица `settings`)."""

    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def get_raw(self, guild_id: int, key: str) -> str | None:
        return await self.db.fetch_val(
            "SELECT value FROM settings WHERE guild_id = ? AND key = ?;",
            (guild_id, key),
        )

    async def get(self, guild_id: int, key: str, default: Any = None) -> Any:
        if default is None:
            default = DEFAULT_SETTINGS.get(key)
        raw = await self.get_raw(guild_id, key)
        if raw is None:
            return default
        if isinstance(default, bool):
            return raw.lower() in ("1", "true", "yes", "on")
        if isinstance(default, int) and not isinstance(default, bool):
            try:
                return int(raw)
            except (TypeError, ValueError):
                return default
        if isinstance(default, (list, dict)):
            return loads(raw, default)
        return raw

    async def set(self, guild_id: int, key: str, value: Any) -> None:
        encoded = value if isinstance(value, str) else dumps(value)
        await self.db.execute(
            """
            INSERT INTO settings (guild_id, key, value, updated_at)
            VALUES (?, ?, ?, datetime('now'))
            ON CONFLICT(guild_id, key)
            DO UPDATE SET value = excluded.value, updated_at = datetime('now');
            """,
            (guild_id, key, encoded),
        )

    async def get_many(self, guild_id: int, keys: Iterable[str] | None = None) -> dict[str, Any]:
        keys = list(keys) if keys else list(DEFAULT_SETTINGS.keys())
        rows = await self.db.fetch_all(
            "SELECT key, value FROM settings WHERE guild_id = ?;", (guild_id,)
        )
        stored = {row["key"]: row["value"] for row in rows}
        result: dict[str, Any] = {}
        for key in keys:
            default = DEFAULT_SETTINGS.get(key)
            raw = stored.get(key)
            if raw is None:
                result[key] = default
            elif isinstance(default, bool):
                result[key] = raw.lower() in ("1", "true", "yes", "on")
            elif isinstance(default, int) and not isinstance(default, bool):
                try:
                    result[key] = int(raw)
                except (TypeError, ValueError):
                    result[key] = default
            elif isinstance(default, (list, dict)):
                result[key] = loads(raw, default)
            else:
                result[key] = raw
        return result

    async def all_keys(self, guild_id: int) -> dict[str, str]:
        rows = await self.db.fetch_all(
            "SELECT key, value FROM settings WHERE guild_id = ?;", (guild_id,)
        )
        return {row["key"]: row["value"] for row in rows}

    async def control_roles(self, guild_id: int) -> list[dict[str, Any]]:
        """Список контролируемых ролей с привязкой к Discord ID."""
        stored = await self.get(guild_id, "control_roles", [])
        if stored:
            return [dict(item) for item in stored]
        return [dict(item) for item in CONTROLLED_ROLES]

    async def tracked_role_names(self, guild_id: int) -> list[str]:
        return [r["name"] for r in await self.control_roles(guild_id) if r.get("tracked", True)]

    async def all_controlled_names(self, guild_id: int) -> list[str]:
        return [r["name"] for r in await self.control_roles(guild_id)]

    async def role_id_for(self, guild_id: int, role_name: str) -> int:
        """Discord ID роли по её названию (0, если роль не найдена)."""
        wanted = role_name.strip().casefold()
        for role in await self.control_roles(guild_id):
            if str(role.get("name", "")).strip().casefold() == wanted:
                return int(role.get("role_id") or 0)
        return 0

    async def priority_for(self, guild_id: int, role_name: str) -> int:
        wanted = role_name.strip().casefold()
        for role in await self.control_roles(guild_id):
            if str(role.get("name", "")).strip().casefold() == wanted:
                return int(role.get("priority") or 0)
        return 0


# --------------------------------------------------------------------------- #
#  Персонал
# --------------------------------------------------------------------------- #
class StaffRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def upsert(
        self,
        *,
        guild_id: int,
        user_id: int,
        username: str,
        display_name: str = "",
        position: str = "",
        role_id: int = 0,
        assigned_at: str | None = None,
        activate: bool = True,
    ) -> Staff:
        """Добавляет сотрудника или обновляет его данные (без дублей)."""
        existing = await self.get(guild_id, user_id)
        timestamp = assigned_at or to_db(now())
        if existing is None:
            row_id = await self.db.insert(
                """
                INSERT INTO staff
                    (guild_id, user_id, username, display_name, position, role_id,
                     assigned_at, is_active, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, datetime('now'));
                """,
                (
                    guild_id,
                    user_id,
                    username,
                    display_name or username,
                    position,
                    role_id,
                    timestamp,
                    int(activate),
                ),
            )
            return Staff(
                id=row_id,
                guild_id=guild_id,
                user_id=user_id,
                username=username,
                display_name=display_name or username,
                position=position,
                role_id=role_id,
                assigned_at=timestamp,
                is_active=activate,
            )

        # должность/ник обновляются; дата назначения сохраняется, если должность та же
        new_assigned = existing.assigned_at
        if position and position != existing.position:
            new_assigned = timestamp
        await self.db.execute(
            """
            UPDATE staff
               SET username = ?, display_name = ?, position = ?, role_id = ?,
                   assigned_at = ?, is_active = ?, left_at = NULL, updated_at = datetime('now')
             WHERE id = ?;
            """,
            (
                username,
                display_name or username,
                position,
                role_id,
                new_assigned,
                int(activate),
                existing.id,
            ),
        )
        existing.username = username
        existing.display_name = display_name or username
        existing.position = position
        existing.role_id = role_id
        existing.assigned_at = new_assigned
        existing.is_active = activate
        existing.left_at = None
        return existing

    async def get(self, guild_id: int, user_id: int) -> Staff | None:
        row = await self.db.fetch_one(
            "SELECT * FROM staff WHERE guild_id = ? AND user_id = ?;", (guild_id, user_id)
        )
        return Staff.from_row(row) if row else None

    async def list(
        self,
        guild_id: int,
        *,
        active_only: bool = True,
        position: str | None = None,
        limit: int | None = None,
        offset: int = 0,
    ) -> list[Staff]:
        sql = "SELECT * FROM staff WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if active_only:
            sql += " AND is_active = 1"
        if position:
            sql += " AND position = ?"
            params.append(position)
        sql += " ORDER BY position DESC, username ASC"
        if limit:
            sql += " LIMIT ? OFFSET ?"
            params += [limit, offset]
        rows = await self.db.fetch_all(sql, params)
        return [Staff.from_row(row) for row in rows]

    async def count(self, guild_id: int, *, active_only: bool = True) -> int:
        sql = "SELECT COUNT(*) FROM staff WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if active_only:
            sql += " AND is_active = 1"
        return int(await self.db.fetch_val(sql, params, 0) or 0)

    async def mark_left(self, guild_id: int, user_id: int) -> bool:
        changed = await self.db.execute(
            """
            UPDATE staff
               SET is_active = 0, left_at = datetime('now'), updated_at = datetime('now')
             WHERE guild_id = ? AND user_id = ?;
            """,
            (guild_id, user_id),
        )
        return bool(changed)

    async def bump_reports(self, guild_id: int, user_id: int, delta: int = 1) -> None:
        await self.db.execute(
            """
            UPDATE staff
               SET total_reports = MAX(0, total_reports + ?), updated_at = datetime('now')
             WHERE guild_id = ? AND user_id = ?;
            """,
            (delta, guild_id, user_id),
        )

    async def positions_breakdown(self, guild_id: int) -> dict[str, int]:
        rows = await self.db.fetch_all(
            """
            SELECT position, COUNT(*) AS total FROM staff
             WHERE guild_id = ? AND is_active = 1
             GROUP BY position ORDER BY total DESC;
            """,
            (guild_id,),
        )
        return {row["position"] or "—": int(row["total"]) for row in rows}


# --------------------------------------------------------------------------- #
#  Роли сотрудников
# --------------------------------------------------------------------------- #
class StaffRoleRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def set_roles(
        self,
        guild_id: int,
        user_id: int,
        roles: Sequence[dict[str, Any]],
    ) -> tuple[list[str], list[str]]:
        """Синхронизирует набор контролируемых ролей.

        Возвращает (добавленные, снятые) — по названиям.
        """
        current = {r.role_id: r for r in await self.active(guild_id, user_id)}
        wanted = {int(r["role_id"]): r for r in roles if r.get("role_id")}
        added: list[str] = []
        removed: list[str] = []

        for role_id, info in wanted.items():
            if role_id in current:
                await self.db.execute(
                    """
                    UPDATE staff_roles
                       SET role_name = ?, priority = ?, is_active = 1, revoked_at = NULL
                     WHERE guild_id = ? AND user_id = ? AND role_id = ?;
                    """,
                    (info.get("name", ""), int(info.get("priority", 0)), guild_id, user_id, role_id),
                )
                continue
            await self.db.execute(
                """
                INSERT INTO staff_roles
                    (guild_id, user_id, role_id, role_name, priority, granted_at, is_active)
                VALUES (?, ?, ?, ?, ?, datetime('now'), 1)
                ON CONFLICT(guild_id, user_id, role_id)
                DO UPDATE SET role_name = excluded.role_name,
                              priority  = excluded.priority,
                              is_active = 1,
                              revoked_at = NULL;
                """,
                (
                    guild_id,
                    user_id,
                    role_id,
                    info.get("name", ""),
                    int(info.get("priority", 0)),
                ),
            )
            added.append(info.get("name", str(role_id)))

        for role_id, record in current.items():
            if role_id in wanted:
                continue
            await self.db.execute(
                """
                UPDATE staff_roles
                   SET is_active = 0, revoked_at = datetime('now')
                 WHERE guild_id = ? AND user_id = ? AND role_id = ?;
                """,
                (guild_id, user_id, role_id),
            )
            removed.append(record.role_name or str(role_id))

        return added, removed

    async def active(self, guild_id: int, user_id: int) -> list[StaffRole]:
        rows = await self.db.fetch_all(
            """
            SELECT * FROM staff_roles
             WHERE guild_id = ? AND user_id = ? AND is_active = 1
             ORDER BY priority DESC;
            """,
            (guild_id, user_id),
        )
        return [StaffRole.from_row(row) for row in rows]

    async def revoke(self, guild_id: int, user_id: int, role_id: int) -> bool:
        changed = await self.db.execute(
            """
            UPDATE staff_roles
               SET is_active = 0, revoked_at = datetime('now')
             WHERE guild_id = ? AND user_id = ? AND role_id = ?;
            """,
            (guild_id, user_id, role_id),
        )
        return bool(changed)

    async def history(self, guild_id: int, user_id: int) -> list[StaffRole]:
        rows = await self.db.fetch_all(
            "SELECT * FROM staff_roles WHERE guild_id = ? AND user_id = ? ORDER BY granted_at DESC;",
            (guild_id, user_id),
        )
        return [StaffRole.from_row(row) for row in rows]


# --------------------------------------------------------------------------- #
#  Отчёты
# --------------------------------------------------------------------------- #
class ReportRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    @staticmethod
    def content_hash(text: str, attachments: int) -> str:
        payload = f"{text.strip().lower()}|{attachments}"
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:32]

    async def upsert(
        self,
        *,
        guild_id: int,
        message_id: int,
        channel_id: int,
        user_id: int,
        username: str,
        position: str,
        content: str,
        attachment_count: int,
        message_url: str,
        created_at: datetime | str,
        match_score: int = 0,
        is_valid: bool = True,
        invalid_reason: str | None = None,
        week_start_day: str = "mon",
    ) -> tuple[Report, bool]:
        """Сохраняет отчёт. Возвращает (отчёт, создан_ли_новый).

        Защита от дублей — уникальный индекс по `message_id` + hash контента.
        """
        created = created_at if isinstance(created_at, datetime) else from_db(created_at) or now()
        keys = period_key(created, week_start_day)
        payload_hash = self.content_hash(content, attachment_count)

        existing = await self.db.fetch_one(
            "SELECT * FROM reports WHERE message_id = ?;", (message_id,)
        )
        if existing:
            await self.db.execute(
                """
                UPDATE reports
                   SET content = ?, attachment_count = ?, username = ?, position = ?,
                       message_url = ?, content_hash = ?, match_score = ?, is_valid = ?,
                       invalid_reason = ?, period_day = ?, period_week = ?, period_month = ?,
                       updated_at = datetime('now'),
                       status = CASE WHEN status = ? THEN ? ELSE status END,
                       deleted_at = NULL
                 WHERE message_id = ?;
                """,
                (
                    content,
                    attachment_count,
                    username,
                    position,
                    message_url,
                    payload_hash,
                    match_score,
                    int(is_valid),
                    invalid_reason,
                    keys["day"],
                    keys["week"],
                    keys["month"],
                    ReportStatus.DELETED,
                    ReportStatus.PENDING,
                    message_id,
                ),
            )
            row = await self.db.fetch_one(
                "SELECT * FROM reports WHERE message_id = ?;", (message_id,)
            )
            return Report.from_row(row), False  # type: ignore[arg-type]

        row_id = await self.db.insert(
            """
            INSERT INTO reports
                (guild_id, channel_id, message_id, user_id, username, position, content,
                 attachment_count, message_url, content_hash, match_score, is_valid,
                 invalid_reason, status, period_day, period_week, period_month, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
            """,
            (
                guild_id,
                channel_id,
                message_id,
                user_id,
                username,
                position,
                content,
                attachment_count,
                message_url,
                payload_hash,
                match_score,
                int(is_valid),
                invalid_reason,
                ReportStatus.PENDING if is_valid else ReportStatus.REJECTED,
                keys["day"],
                keys["week"],
                keys["month"],
                to_db(created),
            ),
        )
        return (
            Report(
                id=row_id,
                guild_id=guild_id,
                user_id=user_id,
                message_id=message_id,
                channel_id=channel_id,
                username=username,
                position=position,
                content=content,
                attachment_count=attachment_count,
                message_url=message_url,
                status=ReportStatus.PENDING if is_valid else ReportStatus.REJECTED,
                created_at=to_db(created),
            ),
            True,
        )

    async def get(self, report_id: int) -> Report | None:
        row = await self.db.fetch_one("SELECT * FROM reports WHERE id = ?;", (report_id,))
        return Report.from_row(row) if row else None

    async def get_by_message(self, message_id: int) -> Report | None:
        row = await self.db.fetch_one(
            "SELECT * FROM reports WHERE message_id = ?;", (message_id,)
        )
        return Report.from_row(row) if row else None

    async def set_status(
        self,
        message_id: int,
        status: str,
        *,
        decided_by: int = 0,
        reason: str | None = None,
    ) -> bool:
        changed = await self.db.execute(
            """
            UPDATE reports
               SET status = ?, decided_by = ?, decided_at = datetime('now'),
                   decision_reason = ?, updated_at = datetime('now')
             WHERE message_id = ?;
            """,
            (status, decided_by, reason, message_id),
        )
        return bool(changed)

    async def mark_deleted(self, message_id: int) -> bool:
        changed = await self.db.execute(
            """
            UPDATE reports
               SET status = ?, deleted_at = datetime('now'), updated_at = datetime('now')
             WHERE message_id = ?;
            """,
            (ReportStatus.DELETED, message_id),
        )
        return bool(changed)

    async def list(
        self,
        guild_id: int,
        *,
        user_id: int | None = None,
        period: str | None = None,
        status: str | None = None,
        limit: int = 100,
        offset: int = 0,
        week_start_day: str = "mon",
        tz: str | None = None,
    ) -> list[Report]:
        sql = "SELECT * FROM reports WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if user_id:
            sql += " AND user_id = ?"
            params.append(user_id)
        if period and period != "all":
            start, end = period_range(period, tz, week_start_day)
            sql += " AND created_at >= ? AND created_at < ?"
            params += [start, end]
        if status:
            sql += " AND status = ?"
            params.append(status)
        sql += " ORDER BY created_at DESC LIMIT ? OFFSET ?"
        params += [limit, offset]
        rows = await self.db.fetch_all(sql, params)
        return [Report.from_row(row) for row in rows]

    async def count(self, guild_id: int, **filters: Any) -> int:
        sql = "SELECT COUNT(*) FROM reports WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if filters.get("user_id"):
            sql += " AND user_id = ?"
            params.append(filters["user_id"])
        if filters.get("status"):
            sql += " AND status = ?"
            params.append(filters["status"])
        if filters.get("period") and filters["period"] != "all":
            start, end = period_range(
                filters["period"], filters.get("tz"), filters.get("week_start_day", "mon")
            )
            sql += " AND created_at >= ? AND created_at < ?"
            params += [start, end]
        return int(await self.db.fetch_val(sql, params, 0) or 0)

    async def count_by_status(self, guild_id: int, **filters: Any) -> dict[str, int]:
        rows = await self.list(guild_id, limit=100000, **filters)
        out = {
            ReportStatus.ACCEPTED: 0,
            ReportStatus.PENDING: 0,
            ReportStatus.REJECTED: 0,
            ReportStatus.DELETED: 0,
        }
        for report in rows:
            out[report.status] = out.get(report.status, 0) + 1
        return out

    async def per_staff_counts(
        self,
        guild_id: int,
        *,
        period: str = "week",
        week_start_day: str = "mon",
        tz: str | None = None,
        count_deleted: bool = False,
    ) -> dict[int, dict[str, int]]:
        """Количество отчётов по каждому сотруднику за период."""
        start, end = period_range(period, tz, week_start_day)
        rows = await self.db.fetch_all(
            """
            SELECT user_id, COUNT(*) AS total,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS accepted,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS rejected,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS pending
              FROM reports
             WHERE guild_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY user_id;
            """,
            (
                ReportStatus.ACCEPTED,
                ReportStatus.REJECTED,
                ReportStatus.PENDING,
                guild_id,
                start,
                end,
            ),
        )
        result: dict[int, dict[str, int]] = {}
        for row in rows:
            total = int(row["total"] or 0)
            if not count_deleted:
                deleted = int(row.get("deleted", 0) or 0)
                total -= deleted
            result[int(row["user_id"])] = {
                "count": max(0, total),
                "accepted": int(row["accepted"] or 0),
                "rejected": int(row["rejected"] or 0),
                "pending": int(row["pending"] or 0),
            }
        return result

    async def totals(self, guild_id: int) -> dict[str, int]:
        row = await self.db.fetch_one(
            """
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS accepted,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS pending,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS rejected,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS deleted
              FROM reports WHERE guild_id = ?;
            """,
            (
                ReportStatus.ACCEPTED,
                ReportStatus.PENDING,
                ReportStatus.REJECTED,
                ReportStatus.DELETED,
                guild_id,
            ),
        ) or {}
        return {
            "total": int(row.get("total") or 0),
            "accepted": int(row.get("accepted") or 0),
            "pending": int(row.get("pending") or 0),
            "rejected": int(row.get("rejected") or 0),
            "deleted": int(row.get("deleted") or 0),
        }

    async def weeks_with_reports(self, guild_id: int, user_id: int) -> tuple[int, int]:
        """(сколько недель сотрудник на должности, сколько из них были отчёты)."""
        staff = await StaffRepo(self.db).get(guild_id, user_id)
        if staff is None or not staff.assigned_at:
            return 0, 0
        assigned = from_db(staff.assigned_at) or now()
        weeks_total = max(1, int((now() - assigned).days // 7) + 1)
        weeks = await self.db.fetch_val(
            "SELECT COUNT(DISTINCT period_week) FROM reports WHERE guild_id = ? AND user_id = ?;",
            (guild_id, user_id),
            0,
        )
        return weeks_total, int(weeks or 0)

    async def top(self, guild_id: int, *, period: str = "month", limit: int = 10, **kw: Any) -> list[dict[str, Any]]:
        start, end = period_range(period, kw.get("tz"), kw.get("week_start_day", "mon"))
        rows = await self.db.fetch_all(
            """
            SELECT user_id, username, position, COUNT(*) AS total,
                   SUM(CASE WHEN status = ? THEN 1 ELSE 0 END) AS accepted
              FROM reports
             WHERE guild_id = ? AND created_at >= ? AND created_at < ?
             GROUP BY user_id
             ORDER BY total DESC, accepted DESC
             LIMIT ?;
            """,
            (ReportStatus.ACCEPTED, guild_id, start, end, limit),
        )
        return [
            {
                "user_id": int(row["user_id"]),
                "username": row["username"],
                "position": row["position"],
                "count": int(row["total"] or 0),
                "accepted": int(row["accepted"] or 0),
            }
            for row in rows
        ]

    async def recent_range_days(self, guild_id: int, days: int = 7) -> list[dict[str, Any]]:
        since = to_db(now() - timedelta(days=days))
        return await self.db.fetch_all(
            """
            SELECT period_day, COUNT(*) AS total FROM reports
             WHERE guild_id = ? AND created_at >= ?
             GROUP BY period_day ORDER BY period_day;
            """,
            (guild_id, since),
        )


# --------------------------------------------------------------------------- #
#  Предупреждения
# --------------------------------------------------------------------------- #
class WarningRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def add(
        self, guild_id: int, user_id: int, issued_by: int, reason: str = ""
    ) -> Warning:
        row_id = await self.db.insert(
            """
            INSERT INTO warnings (guild_id, user_id, issued_by, reason, created_at, is_active)
            VALUES (?, ?, ?, ?, datetime('now'), 1);
            """,
            (guild_id, user_id, issued_by, reason),
        )
        return Warning(
            id=row_id,
            guild_id=guild_id,
            user_id=user_id,
            issued_by=issued_by,
            reason=reason,
            created_at=to_db(now()),
        )

    async def count(self, guild_id: int, user_id: int) -> int:
        return int(
            await self.db.fetch_val(
                "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND user_id = ? AND is_active = 1;",
                (guild_id, user_id),
                0,
            )
            or 0
        )

    async def list(self, guild_id: int, user_id: int, *, active_only: bool = True) -> list[Warning]:
        sql = "SELECT * FROM warnings WHERE guild_id = ? AND user_id = ?"
        if active_only:
            sql += " AND is_active = 1"
        sql += " ORDER BY created_at DESC;"
        rows = await self.db.fetch_all(sql, (guild_id, user_id))
        return [Warning.from_row(row) for row in rows]

    async def remove_one(self, guild_id: int, user_id: int, removed_by: int) -> Warning | None:
        """Снимает самое свежее активное предупреждение."""
        target = await self.db.fetch_one(
            """
            SELECT * FROM warnings
             WHERE guild_id = ? AND user_id = ? AND is_active = 1
             ORDER BY created_at DESC, id DESC LIMIT 1;
            """,
            (guild_id, user_id),
        )
        if not target:
            return None
        await self.db.execute(
            """
            UPDATE warnings
               SET is_active = 0, removed_by = ?, removed_at = datetime('now')
             WHERE id = ?;
            """,
            (removed_by, target["id"]),
        )
        record = Warning.from_row(target)
        record.is_active = False
        return record

    async def clear(self, guild_id: int, user_id: int, removed_by: int) -> int:
        return int(
            await self.db.execute(
                """
                UPDATE warnings
                   SET is_active = 0, removed_by = ?, removed_at = datetime('now')
                 WHERE guild_id = ? AND user_id = ? AND is_active = 1;
                """,
                (removed_by, guild_id, user_id),
            )
        )

    async def set_count(
        self, guild_id: int, user_id: int, target: int, issuer: int, reason: str = ""
    ) -> tuple[int, int, int]:
        """Устанавливает точное число варнов. Возвращает (было, стало, добавлено)."""
        before = await self.count(guild_id, user_id)
        target = max(0, int(target))
        if target > before:
            for _ in range(target - before):
                await self.add(guild_id, user_id, issuer, reason)
        elif target < before:
            for _ in range(before - target):
                await self.remove_one(guild_id, user_id, issuer)
        return before, target, target - before

    async def total_active(self, guild_id: int) -> int:
        return int(
            await self.db.fetch_val(
                "SELECT COUNT(*) FROM warnings WHERE guild_id = ? AND is_active = 1;",
                (guild_id,),
                0,
            )
            or 0
        )

    async def leaderboard(self, guild_id: int, limit: int = 10) -> list[dict[str, Any]]:
        return await self.db.fetch_all(
            """
            SELECT user_id, COUNT(*) AS total FROM warnings
             WHERE guild_id = ? AND is_active = 1
             GROUP BY user_id ORDER BY total DESC LIMIT ?;
            """,
            (guild_id, limit),
        )


# --------------------------------------------------------------------------- #
#  Наказания
# --------------------------------------------------------------------------- #
class PunishmentRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def add(
        self,
        *,
        guild_id: int,
        user_id: int,
        action: str,
        detail: str = "",
        performed_by: int = 0,
        role_id: int | None = None,
        role_name: str | None = None,
        status: str = "completed",
        metadata: dict[str, Any] | None = None,
    ) -> int:
        return await self.db.insert(
            """
            INSERT INTO punishments
                (guild_id, user_id, action, detail, role_id, role_name,
                 performed_by, status, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'));
            """,
            (
                guild_id,
                user_id,
                action,
                detail,
                role_id,
                role_name,
                performed_by,
                status,
                dumps(metadata or {}),
            ),
        )

    async def get(self, punishment_id: int) -> Punishment | None:
        row = await self.db.fetch_one(
            "SELECT * FROM punishments WHERE id = ?;", (punishment_id,)
        )
        return Punishment.from_row(row) if row else None

    async def pending_for(self, guild_id: int, user_id: int) -> Punishment | None:
        row = await self.db.fetch_one(
            """
            SELECT * FROM punishments
             WHERE guild_id = ? AND user_id = ? AND status = 'pending'
             ORDER BY created_at DESC, id DESC LIMIT 1;
            """,
            (guild_id, user_id),
        )
        return Punishment.from_row(row) if row else None

    async def resolve(self, punishment_id: int, status: str = "completed") -> bool:
        changed = await self.db.execute(
            """
            UPDATE punishments
               SET status = ?, resolved_at = datetime('now')
             WHERE id = ?;
            """,
            (status, punishment_id),
        )
        return bool(changed)

    async def list(
        self,
        guild_id: int,
        *,
        user_id: int | None = None,
        action: str | None = None,
        limit: int = 25,
        offset: int = 0,
        since_days: int | None = None,
    ) -> tuple[list[Punishment], int]:
        sql = "SELECT * FROM punishments WHERE guild_id = ?"
        count_sql = "SELECT COUNT(*) FROM punishments WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if user_id:
            sql += " AND user_id = ?"
            count_sql += " AND user_id = ?"
            params.append(user_id)
        if action:
            sql += " AND action = ?"
            count_sql += " AND action = ?"
            params.append(action)
        if since_days:
            boundary = to_db(now() - timedelta(days=since_days))
            sql += " AND created_at >= ?"
            count_sql += " AND created_at >= ?"
            params.append(boundary)
        total = int(await self.db.fetch_val(count_sql, params, 0) or 0)
        rows = await self.db.fetch_all(
            sql + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?;",
            [*params, limit, offset],
        )
        return [Punishment.from_row(row) for row in rows], total

    async def count_recent(self, guild_id: int, days: int = 30) -> int:
        boundary = to_db(now() - timedelta(days=days))
        return int(
            await self.db.fetch_val(
                "SELECT COUNT(*) FROM punishments WHERE guild_id = ? AND created_at >= ?;",
                (guild_id, boundary),
                0,
            )
            or 0
        )


# --------------------------------------------------------------------------- #
#  Аудит
# --------------------------------------------------------------------------- #
class AuditRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def add(
        self,
        guild_id: int,
        action: str,
        *,
        actor_id: int = 0,
        target_id: int = 0,
        detail: str = "",
    ) -> None:
        await self.db.execute(
            """
            INSERT INTO audit_logs (guild_id, actor_id, action, target_id, detail, created_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'));
            """,
            (guild_id, actor_id, action, target_id, detail[:1000]),
        )

    async def list(
        self,
        guild_id: int,
        *,
        target_id: int | None = None,
        limit: int = 25,
        offset: int = 0,
    ) -> tuple[list[dict[str, Any]], int]:
        sql = "SELECT * FROM audit_logs WHERE guild_id = ?"
        count_sql = "SELECT COUNT(*) FROM audit_logs WHERE guild_id = ?"
        params: list[Any] = [guild_id]
        if target_id:
            sql += " AND target_id = ?"
            count_sql += " AND target_id = ?"
            params.append(target_id)
        total = int(await self.db.fetch_val(count_sql, params, 0) or 0)
        rows = await self.db.fetch_all(
            sql + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?;",
            [*params, limit, offset],
        )
        return rows, total


# --------------------------------------------------------------------------- #
#  Периоды отчётности
# --------------------------------------------------------------------------- #
class PeriodRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def record(
        self,
        *,
        guild_id: int,
        user_id: int,
        period_key_value: str,
        period_type: str,
        report_count: int,
        accepted: int,
        submitted: bool,
    ) -> None:
        await self.db.execute(
            """
            INSERT INTO report_periods
                (guild_id, user_id, period_key, period_type, report_count,
                 accepted, submitted, checked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(guild_id, user_id, period_key)
            DO UPDATE SET report_count = excluded.report_count,
                          accepted     = excluded.accepted,
                          submitted    = excluded.submitted,
                          checked_at   = datetime('now');
            """,
            (
                guild_id,
                user_id,
                period_key_value,
                period_type,
                report_count,
                accepted,
                int(submitted),
            ),
        )

    async def history(self, guild_id: int, user_id: int, limit: int = 12) -> list[dict[str, Any]]:
        return await self.db.fetch_all(
            """
            SELECT * FROM report_periods
             WHERE guild_id = ? AND user_id = ?
             ORDER BY period_key DESC LIMIT ?;
            """,
            (guild_id, user_id, limit),
        )

    async def last_check(self, guild_id: int, period_key_value: str) -> dict[str, Any] | None:
        return await self.db.fetch_one(
            """
            SELECT COUNT(*) AS total, SUM(submitted) AS done
              FROM report_periods WHERE guild_id = ? AND period_key = ?;
            """,
            (guild_id, period_key_value),
        )


# --------------------------------------------------------------------------- #
#  Служебное состояние
# --------------------------------------------------------------------------- #
class StateRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def set(self, key: str, value: Any, guild_id: int = 0) -> None:
        await self.db.execute(
            """
            INSERT INTO bot_state (key, guild_id, value, updated_at)
            VALUES (?, ?, ?, datetime('now'))
            ON CONFLICT(key, guild_id)
            DO UPDATE SET value = excluded.value, updated_at = datetime('now');
            """,
            (key, guild_id, value if isinstance(value, str) else dumps(value)),
        )

    async def get(self, key: str, guild_id: int = 0, default: Any = None) -> Any:
        raw = await self.db.fetch_val(
            "SELECT value FROM bot_state WHERE key = ? AND guild_id = ?;",
            (key, guild_id),
        )
        return raw if raw is not None else default


# --------------------------------------------------------------------------- #
#  Кэш каналов и ролей
# --------------------------------------------------------------------------- #
class CacheRepo:
    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db

    async def put_channel(
        self, guild_id: int, channel_id: int, name: str, category_id: int, slot: str | None
    ) -> None:
        await self.db.execute(
            """
            INSERT INTO channel_cache (guild_id, channel_id, name, category_id, slot, discovered_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(guild_id, channel_id)
            DO UPDATE SET name = excluded.name, category_id = excluded.category_id,
                          slot = excluded.slot, discovered_at = datetime('now');
            """,
            (guild_id, channel_id, name, category_id, slot),
        )

    async def put_role(
        self, guild_id: int, role_id: int, name: str, is_controlled: bool, position: int
    ) -> None:
        await self.db.execute(
            """
            INSERT INTO role_cache (guild_id, role_id, name, is_controlled, position, discovered_at)
            VALUES (?, ?, ?, ?, ?, datetime('now'))
            ON CONFLICT(guild_id, role_id)
            DO UPDATE SET name = excluded.name, is_controlled = excluded.is_controlled,
                          position = excluded.position, discovered_at = datetime('now');
            """,
            (guild_id, role_id, name, int(is_controlled), position),
        )

    async def channels(self, guild_id: int) -> list[dict[str, Any]]:
        return await self.db.fetch_all(
            "SELECT * FROM channel_cache WHERE guild_id = ? ORDER BY name;", (guild_id,)
        )

    async def roles(self, guild_id: int) -> list[dict[str, Any]]:
        return await self.db.fetch_all(
            "SELECT * FROM role_cache WHERE guild_id = ? ORDER BY position DESC;", (guild_id,)
        )


class Repository:
    """Единая точка доступа ко всем репозиториям."""

    def __init__(self, database: Database | None = None) -> None:
        self.db = database or _global_db
        self.settings = SettingsRepo(self.db)
        self.staff = StaffRepo(self.db)
        self.staff_roles = StaffRoleRepo(self.db)
        self.reports = ReportRepo(self.db)
        self.warnings = WarningRepo(self.db)
        self.punishments = PunishmentRepo(self.db)
        self.audit = AuditRepo(self.db)
        self.periods = PeriodRepo(self.db)
        self.state = StateRepo(self.db)
        self.cache = CacheRepo(self.db)


repo = Repository()
