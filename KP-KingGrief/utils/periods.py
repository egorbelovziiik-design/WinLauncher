"""
Работа с периодами (сегодня / неделя / месяц / всё время).

Все границы считаются в тайм-зоне сервера (по умолчанию Europe/Moscow),
чтобы «неделя» совпадала с тем, что видит администрация.
"""

from __future__ import annotations

from datetime import datetime, time, timedelta
from typing import Any

from config import WEEKDAY_NAMES, get_timezone

DB_FORMAT = "%Y-%m-%d %H:%M:%S"


def now(tz_name: str | None = None) -> datetime:
    """Текущее время в нужной тайм-зоне (naive, но локальное для сервера)."""
    return datetime.now(get_timezone(tz_name)).replace(tzinfo=None)


def to_db(dt: datetime | None) -> str:
    return (dt or now()).strftime(DB_FORMAT)


def from_db(raw: str | None) -> datetime | None:
    if not raw:
        return None
    for fmt in (DB_FORMAT, "%Y-%m-%dT%H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return datetime.strptime(raw[:19], fmt)
        except ValueError:
            continue
    try:  # запасной вариант — ISO с микросекундами
        return datetime.fromisoformat(raw[:19])
    except ValueError:
        return None


def fmt_date(dt: datetime | str | None, pattern: str = "%d.%m.%Y") -> str:
    parsed = from_db(dt) if isinstance(dt, str) else dt
    return parsed.strftime(pattern) if parsed else "—"


# --------------------------------------------------------------------------- #
#  Ключи периодов
# --------------------------------------------------------------------------- #
def week_start(dt: datetime, week_start_day: str = "mon") -> datetime:
    """Начало недели (по умолчанию с понедельника)."""
    offset = WEEKDAY_NAMES.get(week_start_day.lower(), ("понедельник", 0))[1]
    days_since = (dt.weekday() - offset) % 7
    start = (dt - timedelta(days=days_since)).date()
    return datetime.combine(start, time.min)


def week_end(dt: datetime, week_start_day: str = "mon") -> datetime:
    return week_start(dt, week_start_day) + timedelta(days=7)


def month_start(dt: datetime) -> datetime:
    return datetime(dt.year, dt.month, 1)


def month_end(dt: datetime) -> datetime:
    nxt = datetime(dt.year + 1, 1, 1) if dt.month == 12 else datetime(dt.year, dt.month + 1, 1)
    return nxt


def day_start(dt: datetime) -> datetime:
    return datetime.combine(dt.date(), time.min)


def period_key(dt: datetime, week_start_day: str = "mon") -> dict[str, str]:
    """Возвращает ключи дня / недели / месяца для момента времени."""
    start = week_start(dt, week_start_day)
    iso_year, iso_week, _ = start.isocalendar()
    return {
        "day": dt.strftime("%Y-%m-%d"),
        "week": f"{iso_year}-W{iso_week:02d}",
        "month": dt.strftime("%Y-%m"),
    }


def period_range(period: str, tz_name: str | None = None, week_start_day: str = "mon") -> tuple[str, str]:
    """Диапазон [start, end) в формате БД для фильтрации отчётов."""
    reference = now(tz_name)
    if period == "today":
        return to_db(day_start(reference)), to_db(day_start(reference) + timedelta(days=1))
    if period == "week":
        return to_db(week_start(reference, week_start_day)), to_db(
            week_end(reference, week_start_day)
        )
    if period == "month":
        return to_db(month_start(reference)), to_db(month_end(reference))
    return "0000-00-00 00:00:00", "9999-12-31 23:59:59"


def period_label(period: str, tz_name: str | None = None, week_start_day: str = "mon") -> str:
    reference = now(tz_name)
    if period == "today":
        return f"📅 Период: сегодня ({fmt_date(reference)})"
    if period == "week":
        start = week_start(reference, week_start_day)
        end = week_end(reference, week_start_day) - timedelta(days=1)
        return f"📅 Период: неделя {fmt_date(start)} — {fmt_date(end)}"
    if period == "month":
        return f"📅 Период: {reference.strftime('%m.%Y')}"
    return "📅 Период: всё время"


def next_weekly_check(
    day: str, hour: int, minute: int, tz_name: str | None = None
) -> datetime | None:
    """Ближайший момент еженедельной проверки."""
    target_weekday = WEEKDAY_NAMES.get(str(day).lower(), ("воскресенье", 6))[1]
    reference = now(tz_name)
    candidate = datetime.combine(reference.date(), time(hour, minute))
    delta = (target_weekday - reference.weekday()) % 7
    candidate += timedelta(days=delta)
    if candidate <= reference:
        candidate += timedelta(days=7)
    return candidate


def describe_schedule(day: str, hour: int, minute: int, tz_name: str | None = None) -> str:
    name = WEEKDAY_NAMES.get(str(day).lower(), ("воскресенье", 0))[0]
    zone = tz_name or "Europe/Moscow"
    when = next_weekly_check(day, hour, minute, tz_name)
    suffix = f"\nСледующая проверка: {fmt_date(when, '%d.%m.%Y %H:%M')}" if when else ""
    return f"Каждую {name} в {hour:02d}:{minute:02d} ({zone}){suffix}"


def seconds_until(when: datetime | None) -> int | None:
    if when is None:
        return None
    return max(0, int((when - now()).total_seconds()))


def normalize_period(value: str | None) -> str:
    """Приводит пользовательский ввод к today/week/month/all."""
    if not value:
        return "week"
    value = value.strip().lower()
    table = {
        "сегодня": "today",
        "today": "today",
        "день": "today",
        "неделя": "week",
        "week": "week",
        "неделю": "week",
        "месяц": "month",
        "month": "month",
        "все": "all",
        "всё": "all",
        "all": "all",
    }
    return table.get(value, "week")


def activity_percent(weeks_covered: int, weeks_with_reports: int) -> int:
    if weeks_covered <= 0:
        return 0
    return max(0, min(100, round(weeks_with_reports / weeks_covered * 100)))


def safe_div(numerator: float, denominator: float, default: float = 0.0) -> float:
    return numerator / denominator if denominator else default


def compact_number(value: Any) -> str:
    try:
        return f"{int(value):,}".replace(",", " ")
    except (TypeError, ValueError):
        return str(value)
