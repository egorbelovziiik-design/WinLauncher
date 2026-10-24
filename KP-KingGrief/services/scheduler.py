"""
Планировщик КП-KingGrief (APScheduler).

Задачи:
  * еженедельная проверка отчётов (по умолчанию воскресенье 20:00 Europe/Moscow);
  * автоматические резервные копии базы;
  * watchdog — контроль «зависаний» и переподключений.

Время проверки настраивается через /settings без перезапуска бота.
"""

from __future__ import annotations

import asyncio
from datetime import datetime
from typing import TYPE_CHECKING, Any, Callable

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger

from config import WEEKDAY_NAMES, get_timezone
from database.models import Repository, repo as _global_repo
from utils import embeds as E
from utils.logging import get_logger
from utils.periods import describe_schedule, next_weekly_check, now

if TYPE_CHECKING:  # pragma: no cover
    import discord

    from bot.client import KingGriefBot

log = get_logger("scheduler")

JOB_WEEKLY = "weekly_report_check"
JOB_BACKUP = "database_backup"
JOB_WATCHDOG = "watchdog"


class SchedulerService:
    """Обёртка над AsyncIOScheduler с динамической перенастройкой."""

    def __init__(
        self,
        bot: "KingGriefBot | None" = None,
        repository: Repository | None = None,
        timezone: str | None = None,
    ) -> None:
        self.bot = bot
        self.repo = repository or _global_repo
        self.timezone = timezone or "Europe/Moscow"
        self._scheduler: AsyncIOScheduler | None = None
        self.last_run: dict[str, datetime] = {}
        self.run_errors: dict[str, str] = {}
        self._started = False

    # ------------------------------------------------------------------ #
    #  Жизненный цикл
    # ------------------------------------------------------------------ #
    @property
    def running(self) -> bool:
        return bool(self._scheduler and self._scheduler.running)

    def start(self) -> None:
        if self.running:
            return
        self._scheduler = AsyncIOScheduler(timezone=get_timezone(self.timezone))
        self._scheduler.start()
        self._started = True
        log.info("Планировщик запущен (тайм-зона: %s).", self.timezone)

    async def stop(self) -> None:
        if self._scheduler is not None and self._scheduler.running:
            self._scheduler.shutdown(wait=False)
            log.info("Планировщик остановлен.")
        self._scheduler = None
        self._started = False

    def jobs(self) -> list[dict[str, Any]]:
        if not self._scheduler:
            return []
        return [
            {
                "id": job.id,
                "name": job.name,
                "next_run": getattr(job, "next_run_time", None),
            }
            for job in self._scheduler.get_jobs()
        ]

    def next_run(self, job_id: str) -> datetime | None:
        if not self._scheduler:
            return None
        job = self._scheduler.get_job(job_id)
        return getattr(job, "next_run_time", None) if job else None

    # ------------------------------------------------------------------ #
    #  Регистрация задач
    # ------------------------------------------------------------------ #
    async def setup(self, guild_id: int) -> None:
        """Регистрирует/перерегистрирует задачи для сервера."""
        self.start()
        await self.schedule_weekly(guild_id)
        await self.schedule_backup(guild_id)
        await self.schedule_watchdog(guild_id)

    async def weekly_info(self, guild_id: int) -> dict[str, Any]:
        """Описание текущих настроек еженедельной проверки (ничего не меняет)."""
        options = await self.repo.settings.get_many(guild_id)
        day = str(options.get("weekly_check_day", "sun")).lower()
        hour = int(options.get("weekly_check_hour", 20))
        minute = int(options.get("weekly_check_minute", 0))
        tz_name = str(options.get("timezone") or self.timezone)
        enabled = bool(options.get("weekly_check_enabled", True))
        description = describe_schedule(day, hour, minute, tz_name)
        if not enabled:
            description = "⛔ Еженедельная проверка отключена"
        return {
            "enabled": enabled,
            "day": day,
            "hour": hour,
            "minute": minute,
            "timezone": tz_name,
            "description": description,
            "next": next_weekly_check(day, hour, minute, tz_name) if enabled else None,
        }

    # псевдоним для совместимости с кодом панели
    schedule_weekly_info = weekly_info

    async def schedule_weekly(self, guild_id: int) -> dict[str, Any]:
        """Еженедельная проверка отчётов."""
        options = await self.repo.settings.get_many(guild_id)
        enabled = bool(options.get("weekly_check_enabled", True))
        day = str(options.get("weekly_check_day", "sun")).lower()
        hour = int(options.get("weekly_check_hour", 20))
        minute = int(options.get("weekly_check_minute", 0))
        tz_name = str(options.get("timezone") or self.timezone)
        self.timezone = tz_name

        job_id = f"{JOB_WEEKLY}:{guild_id}"
        if self._scheduler is None:
            self.start()
        existing = self._scheduler.get_job(job_id) if self._scheduler else None
        if existing:
            existing.remove()

        info = await self.weekly_info(guild_id)
        if not enabled:
            log.info("Еженедельная проверка отключена для гильдии %s.", guild_id)
            return info

        trigger = CronTrigger(
            day_of_week=WEEKDAY_NAMES[day][1],
            hour=hour,
            minute=minute,
            timezone=get_timezone(tz_name),
        )
        self._scheduler.add_job(
            self._safe_run,
            trigger=trigger,
            id=job_id,
            name=f"Еженедельная проверка отчётов ({guild_id})",
            kwargs={"job_id": job_id, "callback": self.weekly_report_check, "guild_id": guild_id},
            replace_existing=True,
            misfire_grace_time=3600,
            coalesce=True,
        )
        log.info("Еженедельная проверка: %s", info["description"])
        return info

    async def schedule_backup(self, guild_id: int) -> None:
        options = await self.repo.settings.get_many(guild_id)
        if not bool(options.get("backup_enabled", True)):
            return
        hours = max(1, int(options.get("backup_interval_hours", 12)))
        job_id = f"{JOB_BACKUP}:{guild_id}"
        self.start()
        self._scheduler.add_job(
            self._safe_run,
            trigger=IntervalTrigger(hours=hours),
            id=job_id,
            name=f"Резервная копия БД ({guild_id})",
            kwargs={"job_id": job_id, "callback": self.backup_job, "guild_id": guild_id},
            replace_existing=True,
            coalesce=True,
        )

    async def schedule_watchdog(self, guild_id: int) -> None:
        options = await self.repo.settings.get_many(guild_id)
        if not bool(options.get("watchdog_enabled", True)):
            return
        minutes = max(1, int(options.get("watchdog_interval_minutes", 10)))
        job_id = f"{JOB_WATCHDOG}:{guild_id}"
        self.start()
        self._scheduler.add_job(
            self._safe_run,
            trigger=IntervalTrigger(minutes=minutes),
            id=job_id,
            name=f"Watchdog ({guild_id})",
            kwargs={"job_id": job_id, "callback": self.watchdog, "guild_id": guild_id},
            replace_existing=True,
            coalesce=True,
        )

    # ------------------------------------------------------------------ #
    #  Обёртка выполнения
    # ------------------------------------------------------------------ #
    async def _safe_run(self, job_id: str, callback: Callable[..., Any], **kwargs: Any) -> Any:
        """Выполняет задачу, не давая исключению уронить планировщик."""
        try:
            result = await callback(**kwargs)
            self.last_run[job_id] = now()
            self.run_errors.pop(job_id, None)
            return result
        except Exception as exc:  # noqa: BLE001 - задача не должна ронять бота
            self.run_errors[job_id] = str(exc)
            log.exception("Задача «%s» завершилась ошибкой: %s", job_id, exc)
            return None

    # ------------------------------------------------------------------ #
    #  Задачи
    # ------------------------------------------------------------------ #
    async def weekly_report_check(
        self, guild_id: int, guild: "discord.Guild | None" = None
    ) -> dict[str, Any]:
        """Проверка отчётов за неделю + публикация итогов."""
        if guild is None:
            if self.bot is None:
                raise RuntimeError("Планировщик не привязан к боту.")
            guild = self.bot.get_guild(guild_id)
        if guild is None:
            log.warning("Еженедельная проверка: гильдия %s недоступна.", guild_id)
            return {"ok": False, "reason": "guild_unavailable"}

        tracker = self.bot.report_tracker
        options = await self.repo.settings.get_many(guild_id)
        data = await tracker.build_ot_data(guild_id, period="week", options=options)
        period_key = await tracker.persist_period(guild_id, data)

        embed = E.weekly_report_embed(
            total=data["total"],
            submitted=[(r["name"], r["position"], r["count"]) for r in data["submitted"]],
            missed=[(r["name"], r["position"], r["count"]) for r in data["missed"]],
            period_label=f"Неделя {period_key}",
        )
        embed.add_field(
            name="ℹ️",
            value="Предупреждения за отсутствие отчёта не выдаются автоматически.",
            inline=False,
        )

        channel_id = int(options.get("weekly_report_channel_id") or 0) or int(
            options.get("reports_channel_id") or 0
        )
        channel = guild.get_channel(channel_id) if channel_id else None
        sent = False
        if channel is not None:
            try:
                await channel.send(embed=embed)
                sent = True
            except Exception as exc:  # noqa: BLE001
                log.error("Не удалось опубликовать еженедельный отчёт: %s", exc)

        await self.repo.audit.add(
            guild_id,
            "weekly_check",
            detail=(
                f"Еженедельная проверка: всего {data['total']}, сдали "
                f"{len(data['submitted'])}, не сдали {len(data['missed'])}"
            ),
        )
        log.info(
            "Еженедельная проверка (%s): %s/%s сдали отчёты.",
            guild.name,
            len(data["submitted"]),
            data["total"],
        )
        return {"ok": True, "sent": sent, "data": data, "period_key": period_key}

    async def backup_job(self, guild_id: int) -> dict[str, Any]:
        """Создаёт резервную копию БД и чистит старые."""
        keep = int(await self.repo.settings.get(guild_id, "backup_keep", 14))
        path = await self.repo.db.backup(tag="auto")
        removed = await self.repo.db.prune_backups(keep)
        return {"ok": path is not None, "path": str(path) if path else None, "pruned": removed}

    async def watchdog(self, guild_id: int) -> dict[str, Any]:
        """Контроль живости: БД, шлюз, лаги событийного цикла."""
        healthy_db = await self.repo.db.ping()
        latency = getattr(self.bot, "latency", float("nan")) if self.bot else float("nan")
        closed = bool(self.bot and self.bot.is_closed()) if self.bot else True
        await self.repo.state.set("watchdog_at", now().strftime("%Y-%m-%d %H:%M:%S"), guild_id)
        await self.repo.state.set("watchdog_db_ok", str(healthy_db), guild_id)
        await self.repo.state.set("watchdog_latency", f"{latency:.3f}", guild_id)

        if not healthy_db:
            log.error("Watchdog: база данных не отвечает — пробую переподключиться.")
            try:
                await self.repo.db.connect()
            except Exception as exc:  # noqa: BLE001
                log.critical("Watchdog: не удалось восстановить соединение с БД: %s", exc)
        if latency == latency and latency > 5.0:  # NaN-safe сравнение
            log.warning("Watchdog: высокая задержка шлюза — %.2fs", latency)
        return {"db": healthy_db, "latency": latency, "gateway_closed": closed}

    # ------------------------------------------------------------------ #
    async def run_weekly_now(
        self, guild_id: int, guild: "discord.Guild | None" = None
    ) -> dict[str, Any]:
        """Ручной запуск проверки (кнопка в панели)."""
        return await self._safe_run(
            job_id=f"{JOB_WEEKLY}:manual",
            callback=self.weekly_report_check,
            guild_id=guild_id,
            guild=guild,
        )

    async def wait_next(self, guild_id: int, timeout: float = 5.0) -> None:
        """Небольшая пауза для отладки (не используется в проде)."""
        await asyncio.sleep(min(timeout, 0.01))


scheduler_service = SchedulerService()
