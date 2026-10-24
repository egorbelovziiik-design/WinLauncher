"""
Конструкторы Embed'ов КП-KingGrief.

Всё оформление на русском языке, с цветовой индикацией:
🟢 успех · 🔴 наказание/ошибка · 🟡 предупреждение · 🔵 информация · 🟣 статистика.
"""

from __future__ import annotations

from datetime import datetime
from typing import Any, Iterable

import discord

from config import BOT_NAME, BOT_VERSION, Colors, PERIOD_LABELS, ReportStatus


def _now() -> datetime:
    return datetime.now()


def _stamp() -> str:
    return _now().strftime("%d.%m.%Y %H:%M")


# --------------------------------------------------------------------------- #
#  Базовые
# --------------------------------------------------------------------------- #
def base(
    title: str | None = None,
    description: str | None = None,
    color: int = Colors.NEUTRAL,
    *,
    footer: str | None = None,
    thumbnail: str | None = None,
) -> discord.Embed:
    """Базовый Embed с фирменным футером."""
    embed = discord.Embed(
        title=title,
        description=description,
        color=color,
        timestamp=_now(),
    )
    embed.set_footer(
        text=footer or f"{BOT_NAME} • v{BOT_VERSION}",
    )
    if thumbnail:
        embed.set_thumbnail(url=thumbnail)
    return embed


def success(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"✅ {title}", description or None, Colors.SUCCESS, **kwargs)


def error(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"❌ {title}", description or None, Colors.ERROR, **kwargs)


def warning(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"🟡 {title}", description or None, Colors.WARNING, **kwargs)


def info(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"🔵 {title}", description or None, Colors.INFO, **kwargs)


def stats(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"📊 {title}", description or None, Colors.STATS, **kwargs)


def punishment(title: str, description: str = "", **kwargs: Any) -> discord.Embed:
    return base(f"🔴 {title}", description or None, Colors.ERROR, **kwargs)


# --------------------------------------------------------------------------- #
#  Специализированные
# --------------------------------------------------------------------------- #
def warn_embed(
    *,
    member: discord.Member | discord.User,
    position: str,
    warns: int,
    max_warns: int,
    reason: str,
    issuer: discord.Member | discord.User,
) -> discord.Embed:
    """Embed предупреждения (1 и 2 варн)."""
    last = warns >= max_warns - 1
    color = Colors.ORANGE if last else Colors.WARNING
    status = "🟠 Последнее предупреждение" if last else "🟡 Первое предупреждение"
    if warns > 1 and not last:
        status = f"🟡 Предупреждение №{warns}"

    embed = base(
        f"⚠️ {BOT_NAME.upper()} | ПРЕДУПРЕЖДЕНИЕ",
        None,
        color,
    )
    embed.add_field(name="👤 Сотрудник", value=f"{member.mention}\n`{member}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=position or "—", inline=True)
    embed.add_field(name="⚠️ Варны", value=f"**{warns}/{max_warns}**", inline=True)
    embed.add_field(name="📝 Причина", value=reason or "Не указана", inline=False)
    embed.add_field(name="👮 Выдал", value=f"{issuer.mention}", inline=True)
    embed.add_field(name="📅 Дата", value=_stamp(), inline=True)
    embed.add_field(name="Статус", value=status, inline=False)
    return embed


def demote_embed(
    *,
    member: discord.Member | discord.User,
    position: str,
    warns: int,
    max_warns: int,
    issuer: discord.Member | discord.User,
    role_name: str | None = None,
) -> discord.Embed:
    """Embed снятия с должности (3 варн)."""
    embed = base(
        f"🔴 {BOT_NAME.upper()} | СНЯТИЕ С ДОЛЖНОСТИ",
        None,
        Colors.ERROR,
    )
    embed.add_field(name="👤 Сотрудник", value=f"{member.mention}\n`{member}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=position or role_name or "—", inline=True)
    embed.add_field(name="⚠️ Варны", value=f"**{warns}/{max_warns}**", inline=True)
    embed.add_field(name="👮 Инициатор", value=issuer.mention, inline=True)
    embed.add_field(name="📅 Дата", value=_stamp(), inline=True)
    embed.add_field(name="Статус", value="❌ Снят с должности", inline=False)
    if role_name:
        embed.add_field(
            name="🎭 Снята роль", value=f"`{role_name}`", inline=True
        )
    return embed


def demote_pending_embed(
    *,
    member: discord.Member | discord.User,
    position: str,
    warns: int,
    max_warns: int,
    issuer: discord.Member | discord.User,
    role_options: list[str],
) -> discord.Embed:
    """Запрос подтверждения снятия роли администратором."""
    embed = base(
        f"🛡️ {BOT_NAME.upper()} | ТРЕБУЕТСЯ ПОДТВЕРЖДЕНИЕ",
        "Сотрудник набрал максимальное количество предупреждений.\n"
        "Уполномоченный администратор должен подтвердить снятие с должности.",
        Colors.ORANGE,
    )
    embed.add_field(name="👤 Сотрудник", value=f"{member.mention}\n`{member}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=position or "—", inline=True)
    embed.add_field(name="⚠️ Варны", value=f"**{warns}/{max_warns}**", inline=True)
    embed.add_field(name="👮 Инициатор", value=issuer.mention, inline=True)
    embed.add_field(
        name="🎭 Контролируемые роли",
        value="\n".join(f"• `{n}`" for n in role_options) or "—",
        inline=False,
    )
    embed.add_field(name="📅 Дата", value=_stamp(), inline=True)
    return embed


def weekly_report_embed(
    *,
    total: int,
    submitted: list[tuple[str, str, int]],
    missed: list[tuple[str, str, int]],
    period_label: str,
    channel_hint: str = "",
) -> discord.Embed:
    """Итоги еженедельной проверки."""
    done = len(submitted)
    pct = round(done / total * 100) if total else 0
    embed = base(
        "📊 ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ КП",
        f"{period_label}" + (f"\n{channel_hint}" if channel_hint else ""),
        Colors.STATS,
    )
    embed.add_field(name="👥 Всего сотрудников", value=str(total), inline=True)
    embed.add_field(name="✅ Сдали", value=str(done), inline=True)
    embed.add_field(name="❌ Не сдали", value=str(len(missed)), inline=True)
    embed.add_field(name="📈 Выполнение", value=f"**{pct}%**", inline=False)

    if missed:
        embed.add_field(
            name="❌ НЕ СДАЛИ",
            value=_join_list([f"{n} — {p}" for n, p, _ in missed]),
            inline=False,
        )
    if submitted:
        embed.add_field(
            name="✅ СДАЛИ",
            value=_join_list([f"{n} — {p} ({c})" for n, p, c in submitted]),
            inline=False,
        )
    return embed


def profile_embed(data: dict[str, Any]) -> discord.Embed:
    """Профиль сотрудника."""
    member = data["member"]
    avatar = getattr(member, "display_avatar", None)
    embed = base(
        f"🛡️ {BOT_NAME.upper()} | ПРОФИЛЬ",
        None,
        Colors.STATS,
        thumbnail=str(avatar.url) if avatar else None,
    )
    embed.add_field(name="👤 Пользователь", value=f"{member.mention}", inline=True)
    embed.add_field(name="🆔 Discord ID", value=f"`{member.id}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=data.get("position") or "—", inline=True)
    embed.add_field(
        name="📅 Назначен", value=data.get("assigned_at") or "—", inline=True
    )
    embed.add_field(
        name="📋 Отчётов за неделю", value=str(data.get("week_reports", 0)), inline=True
    )
    embed.add_field(
        name="📊 Всего отчётов", value=str(data.get("total_reports", 0)), inline=True
    )
    embed.add_field(name="✅ Принято", value=str(data.get("accepted", 0)), inline=True)
    embed.add_field(name="❌ Отклонено", value=str(data.get("rejected", 0)), inline=True)
    embed.add_field(
        name="⚠️ Варны",
        value=f"**{data.get('warns', 0)}/{data.get('max_warns', 3)}**",
        inline=True,
    )
    embed.add_field(
        name="📈 Активность", value=f"**{data.get('activity', 0)}%**", inline=False
    )
    if data.get("all_roles"):
        embed.add_field(
            name="🎭 Контролируемые роли",
            value=", ".join(f"`{r}`" for r in data["all_roles"]),
            inline=False,
        )
    if data.get("left_at"):
        embed.add_field(name="🚪 Покинул сервер", value=str(data["left_at"]), inline=True)
    return embed


def staff_block(name: str, position: str, count: int, submitted: bool) -> str:
    """Один блок сотрудника для списка /ot."""
    mark = "🟢" if submitted else "🔴"
    status = "✅ Сдал" if submitted else "❌ Не сдал"
    return (
        f"{mark} {name}\n"
        f"🎖️ Должность: {position}\n"
        f"📋 Отчётов: {count}\n"
        f"{status}"
    )


def _join_list(items: Iterable[str], limit: int = 1000, per: int = 20) -> str:
    items = list(items)
    out: list[str] = []
    for item in items[:per]:
        chunk = f"{item}\n"
        if len(out) + len(chunk) > limit:
            break
        out.append(chunk)
    text = "".join(out).strip()
    if len(items) > len(out):
        text += f"\n… и ещё {len(items) - len(out)}"
    return text or "—"


def ot_embed(
    *,
    period: str,
    submitted: list[dict[str, Any]],
    missed: list[dict[str, Any]],
    total: int,
    page: int = 1,
    pages: int = 1,
    view: str = "all",
    role_filter: str | None = None,
    user_filter: str | None = None,
) -> discord.Embed:
    """Главный Embed команды /ot."""
    done = len(submitted)
    pct = round(done / total * 100) if total else 0

    title_map = {
        "all": "📊 ОТЧЁТЫ ПЕРСОНАЛА",
        "submitted": "✅ СДАЛИ ОТЧЁТЫ",
        "missed": "❌ НЕ СДАЛИ ОТЧЁТЫ",
        "stats": "📈 СТАТИСТИКА ОТЧЁТОВ",
    }
    embed = base(
        f"{BOT_NAME} | {title_map.get(view, '📊 ОТЧЁТЫ ПЕРСОНАЛА')}",
        PERIOD_LABELS.get(period, period),
        Colors.STATS,
    )
    filters: list[str] = []
    if role_filter:
        filters.append(f"🎭 Роль: `{role_filter}`")
    if user_filter:
        filters.append(f"👤 Пользователь: {user_filter}")
    if filters:
        embed.description = (embed.description or "") + "\n🔎 Фильтры: " + " · ".join(filters)
    if pages > 1:
        embed.description = (embed.description or "") + f"\n📄 Страница {page}/{pages}"

    if view in ("all", "submitted"):
        blocks = [
            staff_block(s["name"], s["position"], s["count"], True) for s in submitted
        ]
        if blocks:
            embed.add_field(
                name=f"✅ СДАЛИ ОТЧЁТЫ ({len(submitted)})",
                value="\n\n".join(blocks)[:1024],
                inline=False,
            )
    if view in ("all", "missed"):
        blocks = [
            staff_block(s["name"], s["position"], s["count"], False) for s in missed
        ]
        if blocks:
            embed.add_field(
                name=f"❌ НЕ СДАЛИ ОТЧЁТЫ ({len(missed)})",
                value="\n\n".join(blocks)[:1024],
                inline=False,
            )
    if not submitted and not missed:
        embed.add_field(name="ℹ️", value="Сотрудники не найдены.", inline=False)

    if view in ("all", "stats"):
        embed.add_field(
            name="📈 СТАТИСТИКА",
            value=(
                f"👥 Всего сотрудников: **{total}**\n"
                f"✅ Сдали: **{done}**\n"
                f"❌ Не сдали: **{len(missed)}**\n"
                f"📊 Выполнение: **{pct}%**"
            ),
            inline=False,
        )
    return embed


def reports_list_embed(
    *,
    member: discord.Member | discord.User | None,
    reports: list[dict[str, Any]],
    page: int = 1,
    pages: int = 1,
    total: int = 0,
) -> discord.Embed:
    """Список отчётов сотрудника."""
    who = member.mention if member else "Все сотрудники"
    embed = base(
        f"📋 {BOT_NAME} | ОТЧЁТЫ",
        f"{who}\nВсего: **{total}**" + (f"\n📄 Страница {page}/{pages}" if pages > 1 else ""),
        Colors.INFO,
    )
    if not reports:
        embed.add_field(name="ℹ️", value="Отчёты не найдены.", inline=False)
        return embed

    lines: list[str] = []
    for r in reports:
        status = ReportStatus.emoji(r["status"])
        created = r["created_at"][:16].replace("T", " ")
        content = (r["content"] or "").replace("\n", " ")
        if len(content) > 90:
            content = content[:90] + "…"
        lines.append(
            f"{status} `{created}` — {r.get('username', '?')}\n"
            f"🔗 [открыть]({r['message_url']})\n{content}\n"
        )
    embed.add_field(name="📋 Последние отчёты", value="".join(lines)[:1024], inline=False)
    return embed


def report_detail_embed(report: dict[str, Any]) -> discord.Embed:
    """Карточка одного отчёта."""
    color = {
        ReportStatus.ACCEPTED: Colors.SUCCESS,
        ReportStatus.REJECTED: Colors.ERROR,
        ReportStatus.DELETED: Colors.NEUTRAL,
    }.get(report["status"], Colors.WARNING)

    embed = base(f"📋 ОТЧЁТ #{report['id']}", None, color)
    embed.add_field(name="👤 Сотрудник", value=f"`{report.get('username')}`", inline=True)
    embed.add_field(name="🆔 Discord ID", value=f"`{report.get('user_id')}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=report.get("position") or "—", inline=True)
    embed.add_field(name="📅 Дата", value=report.get("created_at", "")[:19], inline=True)
    embed.add_field(name="📎 Вложений", value=str(report.get("attachment_count", 0)), inline=True)
    embed.add_field(
        name="🏷️ Статус", value=ReportStatus.LABELS.get(report["status"], report["status"]), inline=True
    )
    embed.add_field(
        name="📝 Текст", value=(report.get("content") or "—")[:1024], inline=False
    )
    embed.add_field(
        name="🔗 Ссылка", value=report.get("message_url") or "—", inline=False
    )
    if report.get("decided_by"):
        embed.add_field(
            name="⚖️ Решение принял", value=f"<@{report['decided_by']}>", inline=True
        )
        embed.add_field(name="📅 Решение", value=str(report.get("decided_at", ""))[:19], inline=True)
    return embed


def panel_embed(data: dict[str, Any]) -> discord.Embed:
    """Главная панель управления."""
    embed = base(
        f"🛡️ {BOT_NAME}\nПАНЕЛЬ УПРАВЛЕНИЯ ПЕРСОНАЛОМ",
        "Выберите раздел кнопками ниже.",
        Colors.BLURPLE,
    )
    embed.add_field(
        name="📈 Сводка",
        value=(
            f"👥 Сотрудников: **{data.get('staff_total', 0)}**\n"
            f"📋 Отчётов за неделю: **{data.get('week_reports', 0)}**\n"
            f"✅ Сдали отчёты: **{data.get('submitted', 0)}**\n"
            f"❌ Не сдали: **{data.get('missed', 0)}**\n"
            f"⚠️ Активных предупреждений: **{data.get('active_warns', 0)}**\n"
            f"🔴 Наказаний за 30 дней: **{data.get('punishments', 0)}**"
        ),
        inline=False,
    )
    embed.add_field(
        name="⏰ Еженедельная проверка",
        value=data.get("weekly_next", "—"),
        inline=True,
    )
    embed.add_field(
        name="🆔 Сервер",
        value=str(data.get("guild", "—")),
        inline=True,
    )
    return embed


def staff_list_embed(entries: list[dict[str, Any]], page: int, pages: int, total: int) -> discord.Embed:
    """Список персонала."""
    embed = base(
        f"👥 {BOT_NAME} | ПЕРСОНАЛ",
        f"Всего: **{total}**" + (f"\n📄 Страница {page}/{pages}" if pages > 1 else ""),
        Colors.INFO,
    )
    if not entries:
        embed.add_field(name="ℹ️", value="Сотрудники не найдены.", inline=False)
        return embed
    lines = [
        f"🟢 **{e['username']}** — {e['position']} · 📋 {e.get('week_reports', 0)} · "
        f"⚠️ {e.get('warns', 0)}"
        for e in entries
    ]
    embed.add_field(name="👥 Список", value="\n".join(lines)[:1024], inline=False)
    return embed


def top_embed(entries: list[dict[str, Any]], period_label: str) -> discord.Embed:
    """Рейтинг сотрудников."""
    embed = base(f"🏆 {BOT_NAME} | РЕЙТИНГ СОТРУДНИКОВ", period_label, Colors.STATS)
    if not entries:
        embed.add_field(name="ℹ️", value="Нет данных для рейтинга.", inline=False)
        return embed
    medals = ["🥇", "🥈", "🥉"]
    lines = [
        f"{medals[i] if i < 3 else f'{i + 1}.'} **{e['username']}** — {e['position']}\n"
        f"     📋 Отчётов: {e['count']} · ✅ Принято: {e.get('accepted', 0)} · ⚠️ Варны: {e.get('warns', 0)}"
        for i, e in enumerate(entries)
    ]
    embed.add_field(name="🏆 Топ", value="\n".join(lines)[:1024], inline=False)
    return embed


def history_embed(entries: list[dict[str, Any]], page: int, pages: int, total: int) -> discord.Embed:
    """История наказаний."""
    embed = base(
        f"📜 {BOT_NAME} | ИСТОРИЯ НАКАЗАНИЙ",
        f"Всего записей: **{total}**" + (f"\n📄 Страница {page}/{pages}" if pages > 1 else ""),
        Colors.ERROR,
    )
    if not entries:
        embed.add_field(name="ℹ️", value="Записей нет.", inline=False)
        return embed
    icon = {
        "warn": "⚠️",
        "unwarn": "🟢",
        "clearwarns": "🧹",
        "role_removal": "🔻",
        "demotion": "🔴",
        "staff_join": "👋",
        "staff_leave": "🚪",
        "role_change": "🎭",
        "settings": "⚙️",
        "report": "📋",
    }
    lines = [
        f"{icon.get(e['action'], '•')} `{e['created_at'][:16]}` — "
        f"<@{e['user_id']}> · {e.get('detail', '')[:120]}"
        for e in entries
    ]
    embed.add_field(name="📜 Записи", value="\n".join(lines)[:1024], inline=False)
    return embed


def check_embed(data: dict[str, Any]) -> discord.Embed:
    """Результат /check."""
    embed = base(f"🔎 {BOT_NAME} | ПРОВЕРКА СОТРУДНИКА", None, Colors.INFO)
    embed.add_field(name="👤 Пользователь", value=data["member"].mention, inline=True)
    embed.add_field(name="🆔 ID", value=f"`{data['member'].id}`", inline=True)
    embed.add_field(name="🎖️ Должность", value=data.get("position") or "—", inline=True)
    embed.add_field(
        name="🎭 Контролируемые роли",
        value=", ".join(f"`{r}`" for r in data.get("roles", [])) or "—",
        inline=False,
    )
    embed.add_field(name="📋 Отчётов (неделя)", value=str(data.get("week_reports", 0)), inline=True)
    embed.add_field(name="📋 Отчётов (всего)", value=str(data.get("total_reports", 0)), inline=True)
    embed.add_field(name="⚠️ Варны", value=f"{data.get('warns', 0)}/{data.get('max_warns', 3)}", inline=True)
    verdict = data.get("verdict") or "—"
    embed.add_field(name="🧾 Вердикт", value=verdict, inline=False)
    if data.get("issues"):
        embed.add_field(
            name="⚠️ Замечания",
            value="\n".join(f"• {i}" for i in data["issues"]),
            inline=False,
        )
    return embed


def general_stats_embed(data: dict[str, Any]) -> discord.Embed:
    """Общая статистика сервера (/stats)."""
    embed = base(f"📊 {BOT_NAME} | СТАТИСТИКА", None, Colors.STATS)
    embed.add_field(name="👥 Сотрудников всего", value=str(data.get("staff_total", 0)), inline=True)
    embed.add_field(name="🟢 Активных", value=str(data.get("staff_active", 0)), inline=True)
    embed.add_field(name="🚪 Уволилось", value=str(data.get("staff_left", 0)), inline=True)
    embed.add_field(name="📋 Отчётов всего", value=str(data.get("reports_total", 0)), inline=True)
    embed.add_field(name="📋 За неделю", value=str(data.get("reports_week", 0)), inline=True)
    embed.add_field(name="📋 За сегодня", value=str(data.get("reports_today", 0)), inline=True)
    embed.add_field(name="✅ Принято", value=str(data.get("accepted", 0)), inline=True)
    embed.add_field(name="⏳ На проверке", value=str(data.get("pending", 0)), inline=True)
    embed.add_field(name="❌ Отклонено", value=str(data.get("rejected", 0)), inline=True)
    embed.add_field(name="⚠️ Активных варнов", value=str(data.get("active_warns", 0)), inline=True)
    embed.add_field(name="🔴 Наказаний", value=str(data.get("punishments", 0)), inline=True)
    embed.add_field(name="📈 Выполнение (нед.)", value=f"**{data.get('completion', 0)}%**", inline=True)
    if data.get("by_position"):
        embed.add_field(
            name="🎖️ По должностям",
            value="\n".join(f"• {k}: **{v}**" for k, v in data["by_position"].items()),
            inline=False,
        )
    if data.get("top"):
        embed.add_field(
            name="🏆 Топ-3",
            value="\n".join(f"{i + 1}. {e['username']} — {e['count']} отч." for i, e in enumerate(data["top"])),
            inline=False,
        )
    return embed


def help_embed(groups: dict[str, list[tuple[str, str]]]) -> discord.Embed:
    """Справка."""
    embed = base(
        f"🛡️ {BOT_NAME} | ПОМОЩЬ",
        "Все команды работают через Slash Commands. Полный список разделов ниже.",
        Colors.BLURPLE,
    )
    for group, items in groups.items():
        embed.add_field(
            name=group,
            value="\n".join(f"`/{name}` — {desc}" for name, desc in items)[:1024],
            inline=False,
        )
    return embed
