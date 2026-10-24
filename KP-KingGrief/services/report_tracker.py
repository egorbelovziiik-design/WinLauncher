"""
Отслеживание и учёт отчётов персонала.

Задачи модуля:
  1. Отличить настоящий отчёт от обычного сообщения по настраиваемым критериям.
  2. Сохранить отчёт в SQLite (без дублей).
  3. Отреагировать на редактирование и удаление сообщения.
  4. Подготовить данные для /ot, /top, /profile и еженедельной проверки.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any

import discord

from config import ReportStatus
from database.models import Repository, as_row, repo as _global_repo
from utils.logging import get_logger
from utils.periods import fmt_date, from_db, normalize_period, now, period_key, period_range

log = get_logger("report_tracker")


# --------------------------------------------------------------------------- #
#  Классификатор сообщений
# --------------------------------------------------------------------------- #
@dataclass(slots=True)
class MatchResult:
    """Результат проверки сообщения на «отчётность»."""

    valid: bool
    score: int = 0
    reasons: list[str] = field(default_factory=list)

    @property
    def reason_text(self) -> str:
        return "; ".join(self.reasons) if self.reasons else ""


def _normalize(text: str) -> str:
    return " ".join((text or "").lower().replace("ё", "е").split())


class ReportMatcher:
    """Определяет, является ли сообщение отчётом сотрудника.

    Режимы:
      * `smart`      — префикс ИЛИ ключевое слово, плюс проверка длины/слов;
      * `prefix`     — сообщение начинается с одного из префиксов;
      * `keyword`    — в тексте есть одно из ключевых слов;
      * `length`     — только длина/количество слов (без ключевых слов);
      * `everything` — любое сообщение сотрудника в канале отчётов.
    """

    def __init__(self, options: dict[str, Any]) -> None:
        self.mode = str(options.get("report_detection_mode", "smart")).lower()
        self.keywords = [
            _normalize(str(k))
            for k in (options.get("report_keywords") or [])
            if str(k).strip()
        ]
        self.prefixes = [
            _normalize(str(p))
            for p in (options.get("report_prefixes") or [])
            if str(p).strip()
        ]
        self.min_length = int(options.get("report_min_length", 40) or 0)
        self.min_words = int(options.get("report_min_words", 5) or 0)
        self.min_attachments = int(options.get("report_min_attachments", 0) or 0)
        self.require_attachment = bool(options.get("report_require_attachment", False))
        self.ignore_bots = bool(options.get("report_ignore_bots", True))
        self.ignore_webhooks = bool(options.get("report_ignore_webhooks", True))

    # ------------------------------------------------------------------ #
    def has_keyword(self, text: str) -> bool:
        normalized = _normalize(text)
        return any(keyword in normalized for keyword in self.keywords)

    def has_prefix(self, text: str) -> bool:
        normalized = _normalize(text).lstrip("*#> ")
        return any(normalized.startswith(prefix) for prefix in self.prefixes)

    def word_count(self, text: str) -> int:
        return len([w for w in (text or "").split() if any(c.isalnum() for c in w)])

    def classify(
        self,
        *,
        content: str,
        attachment_count: int,
        author_is_bot: bool = False,
        is_webhook: bool = False,
        is_staff: bool = True,
    ) -> MatchResult:
        """Проверяет сообщение по всем настроенным критериям."""
        reasons: list[str] = []
        score = 0

        if self.ignore_bots and author_is_bot:
            return MatchResult(False, 0, ["автор — бот"])
        if self.ignore_webhooks and is_webhook:
            return MatchResult(False, 0, ["сообщение от вебхука"])
        if not is_staff:
            return MatchResult(False, 0, ["автор не является сотрудником КП"])

        content = content or ""
        words = self.word_count(content)
        stripped = content.strip()

        if self.mode == "everything":
            return MatchResult(True, 10, ["режим: учитывать все сообщения"])

        keyword_hit = self.has_keyword(content)
        prefix_hit = self.has_prefix(content)

        if self.mode == "prefix" and not prefix_hit:
            reasons.append("нет префикса отчёта")
        elif self.mode == "keyword" and not keyword_hit:
            reasons.append("нет ключевого слова отчёта")
        elif self.mode == "smart" and not (keyword_hit or prefix_hit):
            reasons.append("не похоже на отчёт (нет префикса/ключевого слова)")

        if keyword_hit:
            score += 5
        if prefix_hit:
            score += 5

        if len(stripped) < self.min_length:
            reasons.append(f"короткое сообщение ({len(stripped)} < {self.min_length} симв.)")
        else:
            score += 2

        if words < self.min_words:
            reasons.append(f"мало слов ({words} < {self.min_words})")
        else:
            score += 2

        if attachment_count < self.min_attachments:
            reasons.append(
                f"мало вложений ({attachment_count} < {self.min_attachments})"
            )
        elif attachment_count:
            score += 1

        if self.require_attachment and attachment_count == 0:
            reasons.append("требуется вложение")

        if reasons:
            return MatchResult(False, score, reasons)
        return MatchResult(True, max(1, score), ["критерии отчёта выполнены"])


# --------------------------------------------------------------------------- #
#  Трекер
# --------------------------------------------------------------------------- #
class ReportTracker:
    """Основной сервис учёта отчётов."""

    def __init__(self, repository: Repository | None = None) -> None:
        self.repo = repository or _global_repo

    # ------------------------------------------------------------------ #
    #  Настройки
    # ------------------------------------------------------------------ #
    async def options(self, guild_id: int) -> dict[str, Any]:
        return await self.repo.settings.get_many(guild_id)

    async def matcher(self, guild_id: int) -> ReportMatcher:
        return ReportMatcher(await self.options(guild_id))

    async def reports_channel_id(self, guild_id: int) -> int:
        return int(await self.repo.settings.get(guild_id, "reports_channel_id", 0) or 0)

    # ------------------------------------------------------------------ #
    #  Обработка сообщений
    # ------------------------------------------------------------------ #
    async def is_report_channel(self, message: discord.Message) -> bool:
        guild = getattr(message, "guild", None)
        if guild is None:
            return False
        return message.channel.id == await self.reports_channel_id(guild.id)

    async def process(
        self,
        message: discord.Message,
        *,
        guild_id: int,
        options: dict[str, Any] | None = None,
    ) -> tuple[Any, bool, MatchResult]:
        """Сохраняет сообщение как отчёт (или фиксирует, почему это не отчёт).

        Возвращает `(report, is_new, match)`. Для «не отчётов» report is None.
        """
        options = options or await self.options(guild_id)
        matcher = ReportMatcher(options)
        author = message.author
        attachments = len(getattr(message, "attachments", []) or [])

        staff = await self.repo.staff.get(guild_id, author.id)
        is_staff = staff is not None and staff.is_active

        match = matcher.classify(
            content=message.content,
            attachment_count=attachments,
            author_is_bot=bool(getattr(author, "bot", False)),
            is_webhook=getattr(message, "webhook_id", None) is not None,
            is_staff=is_staff,
        )
        if not match.valid:
            return None, False, match

        created_at = getattr(message, "created_at", None) or now()
        if created_at.tzinfo is not None:
            created_at = created_at.replace(tzinfo=None)

        report, is_new = await self.repo.reports.upsert(
            guild_id=guild_id,
            message_id=message.id,
            channel_id=message.channel.id,
            user_id=author.id,
            username=str(author),
            position=(staff.position if staff else "") or "",
            content=message.content or "",
            attachment_count=attachments,
            message_url=getattr(message, "jump_url", "") or "",
            created_at=created_at,
            match_score=match.score,
            is_valid=True,
            week_start_day=str(options.get("week_start_day", "mon")),
        )

        if is_new:
            await self.repo.staff.bump_reports(guild_id, author.id, +1)
            await self.repo.audit.add(
                guild_id,
                "report_registered",
                actor_id=author.id,
                target_id=message.id,
                detail=f"Зарегистрирован отчёт #{report.id} от {author}",
            )
            log.info(
                "Отчёт #%s зарегистрирован: %s (%s)",
                report.id,
                author,
                report.position,
            )
        else:
            log.info("Отчёт #%s обновлён (сообщение отредактировано).", report.id)
        return report, is_new, match

    async def handle_message(self, message: discord.Message) -> tuple[Any, bool, MatchResult] | None:
        """Вызывается из on_message."""
        if not await self.is_report_channel(message):
            return None
        guild = message.guild
        return await self.process(message, guild_id=guild.id)

    async def handle_edit(
        self, before: discord.Message, after: discord.Message
    ) -> tuple[Any, bool, MatchResult] | None:
        """Повторная проверка изменённого сообщения."""
        if not await self.is_report_channel(after):
            return None
        existing = await self.repo.reports.get_by_message(after.id)
        result = await self.process(after, guild_id=after.guild.id)
        if result and existing and result[0] and result[0].content != existing.content:
            await self.repo.audit.add(
                after.guild.id,
                "report_edited",
                actor_id=after.author.id,
                target_id=after.id,
                detail=f"Отчёт #{result[0].id} отредактирован и перепроверен",
            )
        return result

    async def handle_delete(self, message: discord.Message) -> bool:
        """Помечает удалённый отчёт."""
        existing = await self.repo.reports.get_by_message(message.id)
        if existing is None:
            return False
        await self.repo.reports.mark_deleted(message.id)
        await self.repo.staff.bump_reports(message.guild.id, message.author.id, -1)
        await self.repo.audit.add(
            message.guild.id,
            "report_deleted",
            actor_id=message.author.id,
            target_id=message.id,
            detail=f"Сообщение отчёта #{existing.id} удалено",
        )
        log.info("Отчёт #%s помечен как удалённый.", existing.id)
        return True

    async def decide(
        self,
        message_id: int,
        status: str,
        *,
        decided_by: int,
        reason: str | None = None,
    ) -> bool:
        """Принять/отклонить отчёт."""
        ok = await self.repo.reports.set_status(
            message_id, status, decided_by=decided_by, reason=reason
        )
        if ok:
            report = await self.repo.reports.get_by_message(message_id)
            await self.repo.audit.add(
                report.guild_id if report else 0,
                "report_decision",
                actor_id=decided_by,
                target_id=message_id,
                detail=f"Отчёт #{report.id if report else '?'}: {ReportStatus.LABELS.get(status, status)}",
            )
        return ok

    # ------------------------------------------------------------------ #
    #  Аналитика
    # ------------------------------------------------------------------ #
    async def build_ot_data(
        self,
        guild_id: int,
        *,
        period: str = "week",
        role_filter: str | None = None,
        user_id: int | None = None,
        options: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Собирает данные для /ot: сдавшие, не сдавшие и статистику."""
        options = options or await self.options(guild_id)
        period = normalize_period(period)
        week_start_day = str(options.get("week_start_day", "mon"))
        tz = str(options.get("timezone") or "Europe/Moscow")

        staff_list = await self.repo.staff.list(guild_id, active_only=True)
        counts = await self.repo.reports.per_staff_counts(
            guild_id, period=period, week_start_day=week_start_day, tz=tz
        )

        if role_filter:
            wanted = role_filter.strip().casefold()
            staff_list = [
                s
                for s in staff_list
                if s.position.casefold() == wanted
                or any(
                    (r.role_name or "").casefold() == wanted
                    for r in await self.repo.staff_roles.active(guild_id, s.user_id)
                )
            ]
        if user_id:
            staff_list = [s for s in staff_list if s.user_id == user_id]

        submitted: list[dict[str, Any]] = []
        missed: list[dict[str, Any]] = []
        for staff in staff_list:
            entry = counts.get(staff.user_id, {"count": 0})
            record = {
                "user_id": staff.user_id,
                "name": f"<@{staff.user_id}>",
                "username": staff.username,
                "position": staff.position or "—",
                "count": int(entry.get("count", 0)),
                "accepted": int(entry.get("accepted", 0)),
                "rejected": int(entry.get("rejected", 0)),
                "pending": int(entry.get("pending", 0)),
            }
            if record["count"] > 0:
                submitted.append(record)
            else:
                missed.append(record)

        submitted.sort(key=lambda r: (-r["count"], r["username"]))
        missed.sort(key=lambda r: r["username"])

        total = len(staff_list)
        return {
            "period": period,
            "submitted": submitted,
            "missed": missed,
            "total": total,
            "completion": round(len(submitted) / total * 100) if total else 0,
            "counts": counts,
        }

    async def persist_period(
        self, guild_id: int, data: dict[str, Any], reference: datetime | None = None
    ) -> str:
        """Сохраняет срез периода в `report_periods` (для истории)."""
        reference = reference or now()
        options = await self.options(guild_id)
        key = period_key(reference, str(options.get("week_start_day", "mon")))["week"]
        for record in [*data["submitted"], *data["missed"]]:
            await self.repo.periods.record(
                guild_id=guild_id,
                user_id=record["user_id"],
                period_key_value=key,
                period_type="week",
                report_count=record["count"],
                accepted=record.get("accepted", 0),
                submitted=record["count"] > 0,
            )
        return key

    async def txt_export(
        self, guild_id: int, data: dict[str, Any], guild_name: str = "KingGrief"
    ) -> tuple[str, str]:
        """Формирует текст и имя файла для «📥 Скачать TXT»."""
        lines: list[str] = [
            "━" * 56,
            "КП-KingGrief | ОТЧЁТЫ ПЕРСОНАЛА",
            f"Сервер: {guild_name}",
            f"Сформировано: {now().strftime('%d.%m.%Y %H:%M:%S')}",
            f"Период: {data['period']}",
            "━" * 56,
            "",
            f"✅ СДАЛИ ОТЧЁТЫ ({len(data['submitted'])}):",
            "",
        ]
        for record in data["submitted"]:
            lines.append(
                f"  🟢 {record['username']:<24} | {record['position']:<12} | "
                f"отчётов: {record['count']} | принято: {record['accepted']}"
            )
        lines += ["", f"❌ НЕ СДАЛИ ОТЧЁТЫ ({len(data['missed'])}):", ""]
        for record in data["missed"]:
            lines.append(
                f"  🔴 {record['username']:<24} | {record['position']:<12} | отчётов: 0"
            )
        lines += [
            "",
            "📈 СТАТИСТИКА:",
            f"  👥 Всего сотрудников: {data['total']}",
            f"  ✅ Сдали: {len(data['submitted'])}",
            f"  ❌ Не сдали: {len(data['missed'])}",
            f"  📊 Выполнение: {data['completion']}%",
            "",
            "━" * 56,
            "Сформировано ботом КП-KingGrief",
        ]
        filename = f"otchet-kp-{now().strftime('%Y-%m-%d_%H-%M')}.txt"
        return "\n".join(lines), filename

    # ------------------------------------------------------------------ #
    #  Профиль
    # ------------------------------------------------------------------ #
    async def profile_data(self, guild_id: int, user_id: int) -> dict[str, Any] | None:
        staff = await self.repo.staff.get(guild_id, user_id)
        if staff is None:
            return None
        options = await self.options(guild_id)
        tz = str(options.get("timezone") or "Europe/Moscow")
        week_start = str(options.get("week_start_day", "mon"))

        week = await self.repo.reports.count(
            guild_id, user_id=user_id, period="week", week_start_day=week_start, tz=tz
        )
        total = await self.repo.reports.count(guild_id, user_id=user_id, period="all")
        by_status = await self.repo.reports.count_by_status(guild_id, user_id=user_id, period="all")
        warns = await self.repo.warnings.count(guild_id, user_id)
        weeks_total, weeks_done = await self.repo.reports.weeks_with_reports(guild_id, user_id)
        roles = await self.repo.staff_roles.active(guild_id, user_id)

        activity = round(weeks_done / weeks_total * 100) if weeks_total else 0
        return {
            "staff": staff,
            "position": staff.position,
            "assigned_at": fmt_date(staff.assigned_at),
            "week_reports": week,
            "total_reports": total,
            "accepted": by_status.get(ReportStatus.ACCEPTED, 0),
            "rejected": by_status.get(ReportStatus.REJECTED, 0),
            "pending": by_status.get(ReportStatus.PENDING, 0),
            "warns": warns,
            "max_warns": int(options.get("max_warnings", 3)),
            "activity": max(0, min(100, activity)),
            "all_roles": [r.role_name for r in roles],
            "left_at": fmt_date(staff.left_at) if staff.left_at else None,
        }

    async def recent_reports(
        self, guild_id: int, user_id: int | None, period: str = "all", limit: int = 25
    ) -> list[dict[str, Any]]:
        options = await self.options(guild_id)
        reports = await self.repo.reports.list(
            guild_id,
            user_id=user_id,
            period=normalize_period(period),
            limit=limit,
            week_start_day=str(options.get("week_start_day", "mon")),
            tz=str(options.get("timezone") or "Europe/Moscow"),
        )
        return [as_row(report) for report in reports]

    async def range_summary(self, guild_id: int, days: int = 7) -> dict[str, int]:
        rows = await self.repo.reports.recent_range_days(guild_id, days)
        return {row["period_day"]: int(row["total"]) for row in rows}

    @staticmethod
    def period_bounds(period: str, tz: str = "Europe/Moscow", week_start_day: str = "mon"):
        return period_range(normalize_period(period), tz, week_start_day)

    @staticmethod
    def message_time(message: discord.Message) -> datetime:
        created = getattr(message, "created_at", None) or now()
        return created.replace(tzinfo=None) if created.tzinfo else created

    @staticmethod
    def parse_created(raw: str) -> datetime | None:
        return from_db(raw)

    def describe_options(self, options: dict[str, Any]) -> str:
        """Человекочитаемое описание текущих критериев отчёта."""
        mode_names = {
            "smart": "умный (префикс/ключевое слово + объём)",
            "prefix": "по префиксу",
            "keyword": "по ключевому слову",
            "length": "по объёму текста",
            "everything": "все сообщения в канале",
        }
        return (
            f"🔎 Режим: {mode_names.get(str(options.get('report_detection_mode')), 'умный')}\n"
            f"📏 Мин. длина: {options.get('report_min_length')} симв.\n"
            f"🔤 Мин. слов: {options.get('report_min_words')}\n"
            f"📎 Мин. вложений: {options.get('report_min_attachments')}\n"
            f"🖼️ Требовать вложение: {'да' if options.get('report_require_attachment') else 'нет'}\n"
            f"🤖 Игнорировать ботов: {'да' if options.get('report_ignore_bots', True) else 'нет'}"
        )

    async def rescan_channel(
        self,
        channel: discord.TextChannel,
        *,
        guild_id: int,
        limit: int = 200,
        since: datetime | None = None,
    ) -> int:
        """Историческое сканирование канала отчётов (используется в /sync)."""
        options = await self.options(guild_id)
        processed = 0
        async for message in channel.history(limit=limit, oldest_first=False):
            created = self.message_time(message)
            if since and created < since:
                break
            report, is_new, _match = await self.process(
                message, guild_id=guild_id, options=options
            )
            if report and is_new:
                processed += 1
        log.info("Историческое сканирование канала: добавлено отчётов — %s", processed)
        return processed


report_tracker = ReportTracker()
