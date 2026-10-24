"""Async SQLite (WAL) storage layer for ReallyWorld КП Discord Bot."""
from __future__ import annotations

import time
from pathlib import Path
from typing import Any

import aiosqlite


SCHEMA_SQL = """
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS guild_settings (
    guild_id INTEGER PRIMARY KEY,
    log_channel_id INTEGER,
    ticket_category_id INTEGER,
    ticket_log_channel_id INTEGER,
    staff_role_id INTEGER,
    verify_role_id INTEGER,
    check_role_id INTEGER,
    kp_review_channel_id INTEGER,
    automod_invites INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS co_owners (
    user_id INTEGER PRIMARY KEY,
    added_by INTEGER NOT NULL,
    added_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS warnings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    moderator_id INTEGER NOT NULL,
    reason TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_warnings_guild_user ON warnings(guild_id, user_id);

CREATE TABLE IF NOT EXISTS tickets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id INTEGER NOT NULL,
    channel_id INTEGER UNIQUE NOT NULL,
    opener_id INTEGER NOT NULL,
    category TEXT NOT NULL,
    topic TEXT NOT NULL,
    claimed_by INTEGER,
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at INTEGER NOT NULL,
    closed_at INTEGER
);
CREATE INDEX IF NOT EXISTS idx_tickets_opener ON tickets(guild_id, opener_id, status);

CREATE TABLE IF NOT EXISTS kp_staff (
    guild_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    position TEXT NOT NULL DEFAULT 'Хелпер',
    points INTEGER NOT NULL DEFAULT 0,
    strikes INTEGER NOT NULL DEFAULT 0,
    closed_tickets INTEGER NOT NULL DEFAULT 0,
    completed_checks INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (guild_id, user_id)
);

CREATE TABLE IF NOT EXISTS kp_applications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    position TEXT NOT NULL,
    mc_nick TEXT NOT NULL,
    age TEXT NOT NULL,
    timezone_str TEXT NOT NULL,
    experience TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reviewer_id INTEGER,
    review_note TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS cheat_checks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id INTEGER NOT NULL,
    channel_id INTEGER NOT NULL,
    suspect_id INTEGER NOT NULL,
    mc_nick TEXT NOT NULL,
    moderator_id INTEGER NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    verdict TEXT,
    created_at INTEGER NOT NULL,
    finished_at INTEGER
);

CREATE TABLE IF NOT EXISTS giveaways (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    guild_id INTEGER NOT NULL,
    channel_id INTEGER NOT NULL,
    message_id INTEGER,
    prize TEXT NOT NULL,
    winners_count INTEGER NOT NULL DEFAULT 1,
    host_id INTEGER NOT NULL,
    ends_at INTEGER NOT NULL,
    ended INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS giveaway_entries (
    giveaway_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    joined_at INTEGER NOT NULL,
    PRIMARY KEY (giveaway_id, user_id),
    FOREIGN KEY (giveaway_id) REFERENCES giveaways(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_economy (
    guild_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    coins INTEGER NOT NULL DEFAULT 0,
    messages_count INTEGER NOT NULL DEFAULT 0,
    last_daily_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (guild_id, user_id)
);
"""


class BotDatabase:
    def __init__(self, db_path: Path) -> None:
        self.db_path = db_path
        self._conn: aiosqlite.Connection | None = None

    async def connect(self) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._conn = await aiosqlite.connect(self.db_path)
        self._conn.row_factory = aiosqlite.Row
        await self._conn.executescript(SCHEMA_SQL)
        await self._conn.commit()

    async def close(self) -> None:
        if self._conn is not None:
            await self._conn.close()
            self._conn = None

    @property
    def conn(self) -> aiosqlite.Connection:
        if self._conn is None:
            raise RuntimeError("Database connection is not initialized.")
        return self._conn

    # ---------------- Guild Settings ----------------
    async def get_guild_settings(self, guild_id: int) -> dict[str, Any]:
        async with self.conn.execute("SELECT * FROM guild_settings WHERE guild_id = ?", (guild_id,)) as cur:
            row = await cur.fetchone()
        if row:
            return dict(row)
        await self.conn.execute("INSERT OR IGNORE INTO guild_settings (guild_id) VALUES (?)", (guild_id,))
        await self.conn.commit()
        async with self.conn.execute("SELECT * FROM guild_settings WHERE guild_id = ?", (guild_id,)) as cur:
            row2 = await cur.fetchone()
        return dict(row2) if row2 else {"guild_id": guild_id, "automod_invites": 1}

    async def update_guild_setting(self, guild_id: int, column: str, value: Any) -> None:
        allowed = {
            "log_channel_id",
            "ticket_category_id",
            "ticket_log_channel_id",
            "staff_role_id",
            "verify_role_id",
            "check_role_id",
            "kp_review_channel_id",
            "automod_invites",
        }
        if column not in allowed:
            raise ValueError(f"Disallowed setting column: {column}")
        await self.get_guild_settings(guild_id)
        await self.conn.execute(f"UPDATE guild_settings SET {column} = ? WHERE guild_id = ?", (value, guild_id))
        await self.conn.commit()

    # ---------------- Co-Owners ----------------
    async def is_co_owner(self, user_id: int) -> bool:
        async with self.conn.execute("SELECT 1 FROM co_owners WHERE user_id = ?", (user_id,)) as cur:
            return (await cur.fetchone()) is not None

    async def add_co_owner(self, user_id: int, added_by: int) -> None:
        await self.conn.execute(
            "INSERT OR REPLACE INTO co_owners (user_id, added_by, added_at) VALUES (?, ?, ?)",
            (user_id, added_by, int(time.time())),
        )
        await self.conn.commit()

    async def remove_co_owner(self, user_id: int) -> bool:
        cur = await self.conn.execute("DELETE FROM co_owners WHERE user_id = ?", (user_id,))
        await self.conn.commit()
        return cur.rowcount > 0

    async def list_co_owners(self) -> list[dict[str, Any]]:
        async with self.conn.execute("SELECT * FROM co_owners ORDER BY added_at ASC") as cur:
            return [dict(r) for r in await cur.fetchall()]

    # ---------------- Moderation Warnings ----------------
    async def add_warning(self, guild_id: int, user_id: int, moderator_id: int, reason: str) -> int:
        cur = await self.conn.execute(
            "INSERT INTO warnings (guild_id, user_id, moderator_id, reason, created_at) VALUES (?, ?, ?, ?, ?)",
            (guild_id, user_id, moderator_id, reason, int(time.time())),
        )
        await self.conn.commit()
        return int(cur.lastrowid)

    async def get_warnings(self, guild_id: int, user_id: int) -> list[dict[str, Any]]:
        async with self.conn.execute(
            "SELECT * FROM warnings WHERE guild_id = ? AND user_id = ? ORDER BY id DESC",
            (guild_id, user_id),
        ) as cur:
            return [dict(r) for r in await cur.fetchall()]

    async def delete_warning(self, guild_id: int, warn_id: int) -> bool:
        cur = await self.conn.execute("DELETE FROM warnings WHERE guild_id = ? AND id = ?", (guild_id, warn_id))
        await self.conn.commit()
        return cur.rowcount > 0

    # ---------------- Tickets ----------------
    async def get_open_ticket(self, guild_id: int, opener_id: int, category: str) -> dict[str, Any] | None:
        async with self.conn.execute(
            "SELECT * FROM tickets WHERE guild_id = ? AND opener_id = ? AND category = ? AND status = 'OPEN'",
            (guild_id, opener_id, category),
        ) as cur:
            row = await cur.fetchone()
            return dict(row) if row else None

    async def get_ticket_by_channel(self, channel_id: int) -> dict[str, Any] | None:
        async with self.conn.execute("SELECT * FROM tickets WHERE channel_id = ?", (channel_id,)) as cur:
            row = await cur.fetchone()
            return dict(row) if row else None

    async def create_ticket(self, guild_id: int, channel_id: int, opener_id: int, category: str, topic: str) -> int:
        cur = await self.conn.execute(
            "INSERT INTO tickets (guild_id, channel_id, opener_id, category, topic, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            (guild_id, channel_id, opener_id, category, topic, int(time.time())),
        )
        await self.conn.commit()
        return int(cur.lastrowid)

    async def claim_ticket(self, channel_id: int, staff_id: int) -> bool:
        cur = await self.conn.execute(
            "UPDATE tickets SET claimed_by = ? WHERE channel_id = ? AND status = 'OPEN' AND claimed_by IS NULL",
            (staff_id, channel_id),
        )
        await self.conn.commit()
        return cur.rowcount > 0

    async def close_ticket(self, channel_id: int) -> None:
        await self.conn.execute(
            "UPDATE tickets SET status = 'CLOSED', closed_at = ? WHERE channel_id = ?",
            (int(time.time()), channel_id),
        )
        await self.conn.commit()

    # ---------------- КП (Команда Проекта) ----------------
    async def ensure_kp_member(self, guild_id: int, user_id: int, position: str = "Хелпер") -> dict[str, Any]:
        now = int(time.time())
        await self.conn.execute(
            "INSERT OR IGNORE INTO kp_staff (guild_id, user_id, position, updated_at) VALUES (?, ?, ?, ?)",
            (guild_id, user_id, position, now),
        )
        await self.conn.commit()
        async with self.conn.execute(
            "SELECT * FROM kp_staff WHERE guild_id = ? AND user_id = ?", (guild_id, user_id)
        ) as cur:
            row = await cur.fetchone()
            return dict(row)

    async def modify_kp_stats(
        self,
        guild_id: int,
        user_id: int,
        *,
        points_delta: int = 0,
        strikes_delta: int = 0,
        tickets_delta: int = 0,
        checks_delta: int = 0,
        position: str | None = None,
    ) -> dict[str, Any]:
        profile = await self.ensure_kp_member(guild_id, user_id)
        new_points = max(0, profile["points"] + points_delta)
        new_strikes = max(0, profile["strikes"] + strikes_delta)
        new_tickets = max(0, profile["closed_tickets"] + tickets_delta)
        new_checks = max(0, profile["completed_checks"] + checks_delta)
        new_pos = position if position else profile["position"]
        await self.conn.execute(
            """
            UPDATE kp_staff
               SET position = ?, points = ?, strikes = ?, closed_tickets = ?, completed_checks = ?, updated_at = ?
             WHERE guild_id = ? AND user_id = ?
            """,
            (new_pos, new_points, new_strikes, new_tickets, new_checks, int(time.time()), guild_id, user_id),
        )
        await self.conn.commit()
        return await self.ensure_kp_member(guild_id, user_id)

    async def get_kp_top(self, guild_id: int, limit: int = 10) -> list[dict[str, Any]]:
        async with self.conn.execute(
            "SELECT * FROM kp_staff WHERE guild_id = ? ORDER BY points DESC, closed_tickets DESC LIMIT ?",
            (guild_id, limit),
        ) as cur:
            return [dict(r) for r in await cur.fetchall()]

    async def create_kp_application(
        self, guild_id: int, user_id: int, position: str, mc_nick: str, age: str, timezone_str: str, experience: str
    ) -> int:
        cur = await self.conn.execute(
            """
            INSERT INTO kp_applications (guild_id, user_id, position, mc_nick, age, timezone_str, experience, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (guild_id, user_id, position, mc_nick, age, timezone_str, experience, int(time.time())),
        )
        await self.conn.commit()
        return int(cur.lastrowid)

    async def update_kp_application(self, app_id: int, status: str, reviewer_id: int, note: str = "") -> dict[str, Any] | None:
        await self.conn.execute(
            "UPDATE kp_applications SET status = ?, reviewer_id = ?, review_note = ? WHERE id = ?",
            (status, reviewer_id, note, app_id),
        )
        await self.conn.commit()
        async with self.conn.execute("SELECT * FROM kp_applications WHERE id = ?", (app_id,)) as cur:
            row = await cur.fetchone()
            return dict(row) if row else None

    # ---------------- Cheat Checks ----------------
    async def create_cheat_check(
        self, guild_id: int, channel_id: int, suspect_id: int, mc_nick: str, moderator_id: int, reason: str
    ) -> int:
        cur = await self.conn.execute(
            """
            INSERT INTO cheat_checks (guild_id, channel_id, suspect_id, mc_nick, moderator_id, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (guild_id, channel_id, suspect_id, mc_nick, moderator_id, reason, int(time.time())),
        )
        await self.conn.commit()
        return int(cur.lastrowid)

    async def get_active_check_by_channel(self, channel_id: int) -> dict[str, Any] | None:
        async with self.conn.execute(
            "SELECT * FROM cheat_checks WHERE channel_id = ? AND status = 'ACTIVE'", (channel_id,)
        ) as cur:
            row = await cur.fetchone()
            return dict(row) if row else None

    async def finish_cheat_check(self, check_id: int, verdict: str) -> None:
        await self.conn.execute(
            "UPDATE cheat_checks SET status = 'FINISHED', verdict = ?, finished_at = ? WHERE id = ?",
            (verdict, int(time.time()), check_id),
        )
        await self.conn.commit()

    # ---------------- Giveaways & Chat Economy ----------------
    async def create_giveaway(
        self, guild_id: int, channel_id: int, prize: str, winners_count: int, host_id: int, ends_at: int
    ) -> int:
        cur = await self.conn.execute(
            """
            INSERT INTO giveaways (guild_id, channel_id, prize, winners_count, host_id, ends_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (guild_id, channel_id, prize, winners_count, host_id, ends_at),
        )
        await self.conn.commit()
        return int(cur.lastrowid)

    async def set_giveaway_message(self, giveaway_id: int, message_id: int) -> None:
        await self.conn.execute("UPDATE giveaways SET message_id = ? WHERE id = ?", (message_id, giveaway_id))
        await self.conn.commit()

    async def get_giveaway_by_message(self, message_id: int) -> dict[str, Any] | None:
        async with self.conn.execute("SELECT * FROM giveaways WHERE message_id = ?", (message_id,)) as cur:
            row = await cur.fetchone()
            return dict(row) if row else None

    async def toggle_giveaway_entry(self, giveaway_id: int, user_id: int) -> tuple[bool, int]:
        async with self.conn.execute(
            "SELECT 1 FROM giveaway_entries WHERE giveaway_id = ? AND user_id = ?", (giveaway_id, user_id)
        ) as cur:
            exists = (await cur.fetchone()) is not None
        if exists:
            await self.conn.execute(
                "DELETE FROM giveaway_entries WHERE giveaway_id = ? AND user_id = ?", (giveaway_id, user_id)
            )
            joined = False
        else:
            await self.conn.execute(
                "INSERT INTO giveaway_entries (giveaway_id, user_id, joined_at) VALUES (?, ?, ?)",
                (giveaway_id, user_id, int(time.time())),
            )
            joined = True
        await self.conn.commit()
        async with self.conn.execute(
            "SELECT COUNT(*) AS cnt FROM giveaway_entries WHERE giveaway_id = ?", (giveaway_id,)
        ) as cur:
            cnt_row = await cur.fetchone()
        return joined, int(cnt_row["cnt"])

    async def get_due_giveaways(self) -> list[dict[str, Any]]:
        async with self.conn.execute(
            "SELECT * FROM giveaways WHERE ended = 0 AND ends_at <= ?", (int(time.time()),)
        ) as cur:
            return [dict(r) for r in await cur.fetchall()]

    async def finish_giveaway(self, giveaway_id: int) -> list[int]:
        await self.conn.execute("UPDATE giveaways SET ended = 1 WHERE id = ?", (giveaway_id,))
        await self.conn.commit()
        async with self.conn.execute(
            "SELECT user_id FROM giveaway_entries WHERE giveaway_id = ?", (giveaway_id,)
        ) as cur:
            return [int(r["user_id"]) for r in await cur.fetchall()]

    async def add_chat_reward(self, guild_id: int, user_id: int, coins_delta: int, is_message: bool = True) -> dict[str, Any]:
        await self.conn.execute(
            "INSERT OR IGNORE INTO chat_economy (guild_id, user_id) VALUES (?, ?)", (guild_id, user_id)
        )
        msg_inc = 1 if is_message else 0
        await self.conn.execute(
            """
            UPDATE chat_economy
               SET coins = MAX(0, coins + ?), messages_count = messages_count + ?
             WHERE guild_id = ? AND user_id = ?
            """,
            (coins_delta, msg_inc, guild_id, user_id),
        )
        await self.conn.commit()
        return await self.get_economy_profile(guild_id, user_id)

    async def claim_daily(self, guild_id: int, user_id: int, amount: int) -> tuple[bool, int]:
        profile = await self.get_economy_profile(guild_id, user_id)
        now = int(time.time())
        cooldown = 86400
        if now - profile["last_daily_at"] < cooldown:
            return False, cooldown - (now - profile["last_daily_at"])
        await self.conn.execute(
            "UPDATE chat_economy SET coins = coins + ?, last_daily_at = ? WHERE guild_id = ? AND user_id = ?",
            (amount, now, guild_id, user_id),
        )
        await self.conn.commit()
        return True, 0

    async def get_economy_profile(self, guild_id: int, user_id: int) -> dict[str, Any]:
        await self.conn.execute(
            "INSERT OR IGNORE INTO chat_economy (guild_id, user_id) VALUES (?, ?)", (guild_id, user_id)
        )
        await self.conn.commit()
        async with self.conn.execute(
            "SELECT * FROM chat_economy WHERE guild_id = ? AND user_id = ?", (guild_id, user_id)
        ) as cur:
            row = await cur.fetchone()
            return dict(row)

    async def get_economy_top(self, guild_id: int, limit: int = 10) -> list[dict[str, Any]]:
        async with self.conn.execute(
            "SELECT * FROM chat_economy WHERE guild_id = ? ORDER BY coins DESC LIMIT ?", (guild_id, limit)
        ) as cur:
            return [dict(r) for r in await cur.fetchall()]
