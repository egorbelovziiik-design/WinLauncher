"""
КП-KingGrief — глобальная конфигурация.

Все значения можно переопределить через переменные окружения или файл `.env`.
Токен бота НИКОГДА не вшивается в код и не попадает в EXE — он читается
только из внешнего `.env` / окружения.
"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Any

try:  # python-dotenv — опциональная зависимость, но входит в requirements
    from dotenv import load_dotenv
except Exception:  # pragma: no cover - fallback, если dotenv не установлен
    load_dotenv = None  # type: ignore[assignment]


# --------------------------------------------------------------------------- #
#  Пути. Корректно работают и в обычном Python, и в PyInstaller (--onefile).
# --------------------------------------------------------------------------- #
def _app_dir() -> Path:
    """Каталог, в котором лежит исполняемый файл (или исходники)."""
    if getattr(sys, "frozen", False):  # запуск из PyInstaller
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


BASE_DIR: Path = _app_dir()

# Загрузка .env: сначала рядом с exe/скриптом, потом в текущей директории.
if load_dotenv is not None:
    load_dotenv(BASE_DIR / ".env", override=False)
    load_dotenv(Path.cwd() / ".env", override=False)


def _env_path(name: str, default: Path) -> Path:
    raw = os.getenv(name)
    return Path(raw).expanduser() if raw else default


DATA_DIR: Path = _env_path("KP_DATA_DIR", BASE_DIR / "data")
LOG_DIR: Path = _env_path("KP_LOG_DIR", BASE_DIR / "logs")
BACKUP_DIR: Path = _env_path("KP_BACKUP_DIR", DATA_DIR / "backups")
DB_PATH: Path = _env_path("KP_DB_PATH", DATA_DIR / "kinggrief.db")

for _d in (DATA_DIR, LOG_DIR, BACKUP_DIR):
    _d.mkdir(parents=True, exist_ok=True)


# --------------------------------------------------------------------------- #
#  Идентичность бота
# --------------------------------------------------------------------------- #
BOT_NAME: str = os.getenv("KP_BOT_NAME", "КП-KingGrief")
BOT_VERSION: str = "1.0.0"
BOT_DESCRIPTION: str = "Контроль персонала Discord-сервера KingGrief"
BOT_ACTIVITY: str = os.getenv("KP_BOT_ACTIVITY", "🛡️ Контролирует персонал KingGrief")
BOT_STATUS_TEXT: str = "🟢 Онлайн"

DISCORD_TOKEN: str | None = os.getenv("DISCORD_TOKEN") or None
OWNER_ID: int | None = (
    int(v) if (v := os.getenv("OWNER_ID", "")).strip().isdigit() else None
)
TEST_GUILD_ID: int | None = (
    int(v) if (v := os.getenv("TEST_GUILD_ID", "")).strip().isdigit() else None
)

DEFAULT_TIMEZONE: str = os.getenv("KP_TIMEZONE", "Europe/Moscow")


# --------------------------------------------------------------------------- #
#  Цвета Embed'ов
# --------------------------------------------------------------------------- #
class Colors:
    """Палитра бота (Discord принимает int)."""

    SUCCESS = 0x2ECC71  # 🟢 успешные действия
    ERROR = 0xE74C3C  # 🔴 наказания / ошибки
    WARNING = 0xF1C40F  # 🟡 предупреждения
    ORANGE = 0xE67E22  # 🟠 последнее предупреждение
    INFO = 0x3498DB  # 🔵 информация
    STATS = 0x9B59B6  # 🟣 статистика
    NEUTRAL = 0x2C2F33
    BLURPLE = 0x5865F2


# --------------------------------------------------------------------------- #
#  Каналы, которые бот ищет автоматически в категории «Кп Сервера»
# --------------------------------------------------------------------------- #
CATEGORY_ALIASES: tuple[str, ...] = (
    "кп сервера",
    "кп-сервера",
    "кп сервер",
    "кп сервера kinggrief",
)

# Ключ настройки -> (каноническое имя канала, человекочитаемое название, алиасы)
CHANNEL_SLOTS: dict[str, dict[str, Any]] = {
    "reports_channel_id": {
        "canonical": "отчеты-кп",
        "title": "Отчёты КП",
        "emoji": "📋",
        "aliases": ("отчеты-кп", "отчёты-кп", "отчеты кп", "отчёты кп", "report", "reports"),
        "required": True,
    },
    "info_channel_id": {
        "canonical": "информация-кп",
        "title": "Информация КП",
        "emoji": "ℹ️",
        "aliases": ("информация-кп", "информация кп", "info"),
        "required": False,
    },
    "announcements_channel_id": {
        "canonical": "объявления-администрации",
        "title": "Объявления администрации",
        "emoji": "📢",
        "aliases": ("объявления-администрации", "объявления администрации", "объявления"),
        "required": False,
    },
    "anticheat_channel_id": {
        "canonical": "анти-чит-сервера",
        "title": "Анти-чит сервера",
        "emoji": "🕵️",
        "aliases": ("анти-чит-сервера", "анти чит сервера", "анти-чит", "anticheat"),
        "required": False,
    },
    "staff_chat_channel_id": {
        "canonical": "стафф-чат",
        "title": "Стафф-чат",
        "emoji": "💬",
        "aliases": ("стафф-чат", "стафф чат", "staff-chat", "staff"),
        "required": False,
    },
    "warns_channel_id": {
        "canonical": "варны-кп",
        "title": "Варны КП",
        "emoji": "⚠️",
        "aliases": ("варны-кп", "варны кп", "warns", "warn"),
        "required": False,
    },
}


# --------------------------------------------------------------------------- #
#  Контролируемые роли.
#  priority: чем больше число — тем выше должность.
#  tracked:  отслеживается ли роль по умолчанию.
# --------------------------------------------------------------------------- #
CONTROLLED_ROLES: list[dict[str, Any]] = [
    {"name": "Админ", "priority": 100, "tracked": True},
    {"name": "Гл.Модер", "priority": 90, "tracked": True},
    {"name": "Ст.Модер", "priority": 80, "tracked": True},
    {"name": "Модер", "priority": 70, "tracked": True},
    {"name": "Мл.Модер", "priority": 60, "tracked": True},
    {"name": "Стажер", "priority": 50, "tracked": True},
    {"name": "Мл.Админ", "priority": 95, "tracked": True},
    {"name": "Д.Админ", "priority": 45, "tracked": False},
    {"name": "Д.Гл.Модер", "priority": 40, "tracked": False},
    {"name": "Д.Ст.Модер", "priority": 35, "tracked": False},
    {"name": "Д.Модер", "priority": 30, "tracked": False},
]

# Порядок должностей от старшей к младшей (для отображения и выбора «главной»).
POSITION_ORDER: list[str] = [
    r["name"]
    for r in sorted(CONTROLLED_ROLES, key=lambda r: -int(r["priority"]))
]

# Имена ролей, которые дают расширенные права в боте (настраиваются через /settings).
DEFAULT_ADMIN_ROLE_NAMES = ("Админ", "Гл.Модер")
DEFAULT_SENIOR_ROLE_NAMES = ("Гл.Модер", "Ст.Модер")
DEFAULT_MOD_ROLE_NAMES = ("Модер", "Мл.Модер")
DEFAULT_TRAINEE_ROLE_NAMES = ("Стажер",)


# --------------------------------------------------------------------------- #
#  Статусы отчётов
# --------------------------------------------------------------------------- #
class ReportStatus:
    PENDING = "pending"  # ⏳ На проверке
    ACCEPTED = "accepted"  # ✅ Принят
    REJECTED = "rejected"  # ❌ Отклонён
    DELETED = "deleted"  # 🗑️ Удалён

    LABELS = {
        PENDING: "⏳ На проверке",
        ACCEPTED: "✅ Принят",
        REJECTED: "❌ Отклонён",
        DELETED: "🗑️ Сообщение удалено",
    }

    @classmethod
    def emoji(cls, status: str) -> str:
        return cls.LABELS.get(status, status).split(" ", 1)[0]


# --------------------------------------------------------------------------- #
#  Значения настроек по умолчанию (переопределяются через /settings и БД)
# --------------------------------------------------------------------------- #
DEFAULT_SETTINGS: dict[str, Any] = {
    # Каналы (0 = не настроен)
    "reports_category_id": 0,
    "reports_channel_id": 0,
    "info_channel_id": 0,
    "announcements_channel_id": 0,
    "anticheat_channel_id": 0,
    "staff_chat_channel_id": 0,
    "warns_channel_id": 0,
    "weekly_report_channel_id": 0,  # 0 = использовать reports_channel_id
    "log_channel_id": 0,

    # Роли
    "owner_id": OWNER_ID or 0,
    "admin_role_ids": [],
    "senior_role_ids": [],
    "mod_role_ids": [],
    "trainee_role_ids": [],
    "control_roles": [],  # [{name, role_id, priority, tracked}]
    "auto_detect_channels": True,
    "auto_detect_roles": True,

    # Контроль ролей
    "max_warnings": 3,
    "auto_demote_on_max_warnings": True,
    "require_confirm_demote": True,
    "allow_demote_above_executor": False,
    "notify_staff_on_warn": True,

    # Определение отчётов
    "report_detection_mode": "smart",  # smart | prefix | keyword | length | everything
    "report_keywords": [
        "отчёт",
        "отчет",
        "отчётность",
        "отчетность",
        "отработан",
        "отработал",
        "выдал",
        "наказал",
    ],
    "report_prefixes": ["отчёт", "отчет", "📋", "**отчёт", "**отчет"],
    "report_min_length": 40,
    "report_min_words": 5,
    "report_min_attachments": 0,
    "report_require_attachment": False,
    "report_ignore_bots": True,
    "report_ignore_webhooks": True,
    "report_min_message_age_seconds": 0,

    # Еженедельная проверка
    "weekly_check_enabled": True,
    "weekly_check_day": "sun",  # mon..sun
    "weekly_check_hour": 20,
    "weekly_check_minute": 0,
    "timezone": DEFAULT_TIMEZONE,
    "week_start_day": "mon",

    # База / обслуживание
    "backup_enabled": True,
    "backup_interval_hours": 12,
    "backup_keep": 14,
    "watchdog_enabled": True,
    "watchdog_interval_minutes": 10,
}

# Описание настроек для команды /settings (тип + диапазон + подпись).
SETTING_SCHEMA: dict[str, dict[str, Any]] = {
    "reports_channel_id": {"type": "channel", "label": "Канал отчётов", "emoji": "📋"},
    "info_channel_id": {"type": "channel", "label": "Информация КП", "emoji": "ℹ️"},
    "announcements_channel_id": {"type": "channel", "label": "Объявления", "emoji": "📢"},
    "anticheat_channel_id": {"type": "channel", "label": "Анти-чит", "emoji": "🕵️"},
    "staff_chat_channel_id": {"type": "channel", "label": "Стафф-чат", "emoji": "💬"},
    "warns_channel_id": {"type": "channel", "label": "Варны КП", "emoji": "⚠️"},
    "weekly_report_channel_id": {"type": "channel", "label": "Канал еженедельного отчёта", "emoji": "📊"},
    "log_channel_id": {"type": "channel", "label": "Канал логов", "emoji": "📜"},
    "max_warnings": {"type": "int", "label": "Варнов до снятия", "min": 1, "max": 10, "emoji": "⚠️"},
    "auto_demote_on_max_warnings": {"type": "bool", "label": "Снимать роль после макс. варнов", "emoji": "🔻"},
    "require_confirm_demote": {"type": "bool", "label": "Требовать подтверждение снятия", "emoji": "🛡️"},
    "allow_demote_above_executor": {"type": "bool", "label": "Снимать роли выше инициатора", "emoji": "⚖️"},
    "notify_staff_on_warn": {"type": "bool", "label": "Уведомлять сотрудника о варне", "emoji": "🔔"},
    "report_detection_mode": {
        "type": "choice",
        "label": "Режим определения отчёта",
        "emoji": "🔎",
        "choices": ["smart", "prefix", "keyword", "length", "everything"],
    },
    "report_min_length": {"type": "int", "label": "Мин. длина отчёта (симв.)", "min": 0, "max": 4000, "emoji": "📏"},
    "report_min_words": {"type": "int", "label": "Мин. количество слов", "min": 0, "max": 500, "emoji": "🔤"},
    "report_min_attachments": {"type": "int", "label": "Мин. вложений", "min": 0, "max": 10, "emoji": "📎"},
    "report_require_attachment": {"type": "bool", "label": "Требовать вложение", "emoji": "🖼️"},
    "report_ignore_bots": {"type": "bool", "label": "Игнорировать ботов", "emoji": "🤖"},
    "report_ignore_webhooks": {"type": "bool", "label": "Игнорировать вебхуки", "emoji": "🔗"},
    "weekly_check_enabled": {"type": "bool", "label": "Еженедельная проверка", "emoji": "⏰"},
    "weekly_check_day": {
        "type": "choice",
        "label": "День проверки",
        "emoji": "📅",
        "choices": ["mon", "tue", "wed", "thu", "fri", "sat", "sun"],
    },
    "weekly_check_hour": {"type": "int", "label": "Час проверки", "min": 0, "max": 23, "emoji": "🕐"},
    "weekly_check_minute": {"type": "int", "label": "Минута проверки", "min": 0, "max": 59, "emoji": "🕑"},
    "auto_detect_channels": {"type": "bool", "label": "Авто-поиск каналов", "emoji": "🛰️"},
    "auto_detect_roles": {"type": "bool", "label": "Авто-поиск ролей", "emoji": "🎭"},
    "backup_enabled": {"type": "bool", "label": "Резервные копии БД", "emoji": "💾"},
    "backup_interval_hours": {"type": "int", "label": "Интервал бэкапов (ч)", "min": 1, "max": 168, "emoji": "⏳"},
    "backup_keep": {"type": "int", "label": "Хранить копий", "min": 1, "max": 200, "emoji": "🗄️"},
}

WEEKDAY_NAMES = {
    "mon": ("понедельник", 0),
    "tue": ("вторник", 1),
    "wed": ("среду", 2),
    "thu": ("четверг", 3),
    "fri": ("пятницу", 4),
    "sat": ("субботу", 5),
    "sun": ("воскресенье", 6),
}

PERIOD_CHOICES = {
    "сегодня": "today",
    "неделя": "week",
    "месяц": "month",
    "все": "all",
    "today": "today",
    "week": "week",
    "month": "month",
    "all": "all",
}

PERIOD_LABELS = {
    "today": "📅 Период: сегодня",
    "week": "📅 Период: текущая неделя",
    "month": "📅 Период: текущий месяц",
    "all": "📅 Период: всё время",
}


def dumps(value: Any) -> str:
    """Безопасная сериализация значений настроек."""
    return json.dumps(value, ensure_ascii=False)


def loads(raw: str | None, default: Any = None) -> Any:
    """Безопасная десериализация значений настроек."""
    if raw is None:
        return default
    if isinstance(raw, (list, dict, int, float, bool)):
        return raw
    try:
        return json.loads(raw)
    except Exception:
        return default


def get_timezone(name: str | None = None):
    """Возвращает zoneinfo.ZoneInfo с корректным откатом."""
    from datetime import timezone as _tz, timedelta

    candidates = [name or DEFAULT_TIMEZONE, DEFAULT_TIMEZONE, "Europe/Moscow", "UTC"]
    for cand in candidates:
        try:
            from zoneinfo import ZoneInfo

            return ZoneInfo(cand)
        except Exception:
            continue
    return _tz(timedelta(hours=3), name="MSK")
