"""Общие фикстуры тестов КП-KingGrief."""

from __future__ import annotations

import os
import sys
import tempfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

# Изолированная БД и каталоги — до импорта config
_TMP = Path(tempfile.mkdtemp(prefix="kp-tests-"))
os.environ.setdefault("KP_DB_PATH", str(_TMP / "test.db"))
os.environ.setdefault("KP_DATA_DIR", str(_TMP))
os.environ.setdefault("KP_BACKUP_DIR", str(_TMP / "backups"))
os.environ.setdefault("KP_LOG_DIR", str(_TMP / "logs"))
os.environ.setdefault("KP_LOCK_FILE", str(_TMP / "bot.lock"))

from config import CONTROLLED_ROLES  # noqa: E402
from database.connection import Database  # noqa: E402
from database.migrations import migrate  # noqa: E402
from tests.fakes import (  # noqa: E402
    FakeCategory,
    FakeGuild,
    FakeInteraction,
    FakeMember,
    FakePermissions,
    FakeRole,
)


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture
async def database(tmp_path):
    db = Database(tmp_path / "kinggrief.db")
    await db.connect()
    await migrate(db)
    yield db
    await db.close()


@pytest.fixture
async def bot(database):
    """Настоящий KingGriefBot (без подключения к Discord)."""
    from bot.client import KingGriefBot

    instance = KingGriefBot(database=database)
    await instance._register_cogs()
    yield instance
    try:
        await instance.scheduler.stop()
    except Exception:
        pass


@pytest.fixture
def guild() -> FakeGuild:
    """Сервер KingGrief с категорией «Кп Сервера» и всеми каналами из ТЗ."""
    fake = FakeGuild(1, "KingGrief")
    category = fake.category("Кп Сервера")
    fake.channel("отчеты-кп", category)
    fake.channel("информация-кп", category)
    fake.channel("объявления-администрации", category)
    fake.channel("анти-чит-сервера", category)
    fake.channel("стафф-чат", category)
    fake.channel("варны-кп", category)
    fake.channel("флуд", None)
    return fake


@pytest.fixture
def roles(guild: FakeGuild) -> dict[str, FakeRole]:
    """Все контролируемые роли с корректной иерархией."""
    # позиция растёт вместе с приоритетом: Стажер ниже Модера, Админ выше всех
    order = [r["name"] for r in sorted(CONTROLLED_ROLES, key=lambda r: r["priority"])]
    result: dict[str, FakeRole] = {}
    for index, name in enumerate(order, start=1):
        result[name] = guild.role(name, position=index)
    result["Интеграция"] = guild.role("Интеграция", position=50, managed=True)
    result["Обычная"] = guild.role("Обычная роль", position=2)
    return result


@pytest.fixture
def bot_member(guild: FakeGuild, roles: dict[str, FakeRole]) -> FakeMember:
    """Сам бот — роль выше всех контролируемых, есть manage_roles."""
    me = FakeMember(
        guild,
        999,
        "КП-KingGrief",
        bot=True,
        roles=[guild.role("КП-Бот", position=50)],
        permissions=FakePermissions(manage_roles=True),
    )
    guild.me = me
    return me


@pytest.fixture
def admin(guild: FakeGuild, roles: dict[str, FakeRole], bot_member: FakeMember) -> FakeMember:
    return guild.member(
        10,
        "ГлавныйАдмин",
        roles=[roles["Админ"]],
        permissions=FakePermissions(manage_roles=True, administrator=True),
    )


@pytest.fixture
def staff_member(
    guild: FakeGuild, roles: dict[str, FakeRole], bot_member: FakeMember
) -> FakeMember:
    return guild.member(
        20, "МодераторИван", roles=[roles["Мл.Модер"], roles["Обычная"]]
    )


@pytest.fixture
async def ready(bot, guild, roles, admin, staff_member, bot_member):
    """Полностью настроенный сервер: каналы, роли, персонал в базе."""
    await bot.bootstrap_guild(guild)
    return bot


@pytest.fixture
def interaction(guild, admin, bot):
    return FakeInteraction(guild, admin, bot)


def make_interaction(guild: FakeGuild, user: FakeMember, bot) -> FakeInteraction:
    return FakeInteraction(guild, user, bot)


async def invoke(bot, command_name: str, interaction, **kwargs):
    """Вызывает НАСТОЯЩИЙ callback slash-команды бота."""
    for cog in bot.cogs.values():
        command = getattr(cog, command_name, None)
        if command is not None and hasattr(command, "callback"):
            return await command.callback(cog, interaction, **kwargs)
    raise LookupError(f"Команда /{command_name} не найдена ни в одном Cog")
