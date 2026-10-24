"""
Система прав, еженедельная проверка, события сервера и smoke-тест команд.
"""

from __future__ import annotations

import discord
import pytest

from bot.permissions import COMMAND_LEVELS, Level, permissions
from config import WEEKDAY_NAMES
from tests.conftest import invoke
from tests.fakes import FakeInteraction, FakeMessage

REPORT_TEXT = (
    "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы игроков, "
    "проверил анти-чит, нарушителей отправил в бан."
)


async def submit(bot, guild, member, message_id: int, text: str = REPORT_TEXT):
    channel = guild.get_channel(int(await bot.repo.settings.get(guild.id, "reports_channel_id")))
    return await bot.report_tracker.handle_message(
        FakeMessage(channel, member, text, message_id=message_id)
    )


# --------------------------------------------------------------------------- #
#  Система прав
# --------------------------------------------------------------------------- #
async def test_owner_level(ready, guild, admin, bot):
    await bot.repo.settings.set(guild.id, "owner_id", admin.id)
    assert await permissions.level(guild, admin) is Level.OWNER


async def test_admin_level_from_role(ready, guild, admin, bot):
    await bot.repo.settings.set(guild.id, "owner_id", 0)
    await bot.repo.settings.set(guild.id, "admin_role_ids", [admin.roles[1].id])
    assert await permissions.level(guild, admin) is Level.ADMIN


async def test_level_hierarchy(ready, guild, roles, bot, bot_member):
    cases = {
        "Админ": Level.ADMIN,
        "Гл.Модер": Level.ADMIN,
        "Ст.Модер": Level.SENIOR,
        "Модер": Level.MOD,
        "Мл.Модер": Level.MOD,
        "Стажер": Level.STAFF,
    }
    for index, (role_name, expected) in enumerate(cases.items()):
        member = guild.member(700 + index, f"Тест{index}", roles=[roles[role_name]])
        assert await permissions.level(guild, member) is expected, role_name


async def test_outsider_level_none(ready, guild, roles, bot, bot_member):
    outsider = guild.member(799, "Чужой", roles=[roles["Обычная"]])
    assert await permissions.level(guild, outsider) is Level.NONE


async def test_rights_matrix_matches_spec(ready, guild, roles, bot, bot_member):
    """Проверка матрицы прав из ТЗ."""
    trainee = guild.member(801, "Стаж", roles=[roles["Стажер"]])
    moderator = guild.member(802, "Мод", roles=[roles["Модер"]])
    senior = guild.member(803, "СтМод", roles=[roles["Ст.Модер"]])
    admin = guild.member(804, "Адм", roles=[roles["Админ"]])

    # Стажер: свой профиль и свои отчёты
    assert await permissions.can(guild, trainee, "profile") is True
    assert await permissions.can(guild, trainee, "reports") is True
    assert await permissions.can(guild, trainee, "ot") is False
    assert await permissions.can(guild, trainee, "warn") is False
    assert await permissions.can(guild, trainee, "settings") is False

    # Модер / Мл.Модер: свои данные
    assert await permissions.can(guild, moderator, "profile") is True
    assert await permissions.can(guild, moderator, "warns") is True
    assert await permissions.can(guild, moderator, "warn") is False
    assert await permissions.can(guild, moderator, "settings") is False

    # Гл.Модер / Ст.Модер: просмотр отчётов, персонала, варнов
    assert await permissions.can(guild, senior, "ot") is True
    assert await permissions.can(guild, senior, "staff") is True
    assert await permissions.can(guild, senior, "history") is True
    assert await permissions.can(guild, senior, "warn") is False

    # Главные администраторы: всё
    for command in COMMAND_LEVELS:
        assert await permissions.can(guild, admin, command) is True, command


async def test_outsider_cannot_use_admin_commands(ready, guild, roles, bot, bot_member):
    outsider = guild.member(805, "Мимо", roles=[roles["Обычная"]])
    for command in ("warn", "addwarn", "unwarn", "clearwarns", "setup", "settings", "sync", "stats"):
        assert await permissions.can(guild, outsider, command) is False
    assert await permissions.can(guild, outsider, "help") is True


async def test_permission_denied_is_logged(ready, guild, roles, bot, bot_member):
    outsider = guild.member(806, "Мимо2", roles=[roles["Обычная"]])
    interaction = FakeInteraction(guild, outsider, bot)
    await invoke(bot, "warn", interaction, сотрудник=outsider)
    rows, _ = await bot.repo.audit.list(guild.id, limit=50)
    assert any(r["action"] == "permission_denied" for r in rows)


async def test_rights_reconfigurable_by_role_ids(ready, guild, roles, bot, bot_member):
    """Права настраиваются через Discord ID ролей без правки кода."""
    member = guild.member(807, "Назначенный", roles=[roles["Обычная"]])
    assert await permissions.level(guild, member) is Level.NONE
    await bot.repo.settings.set(guild.id, "senior_role_ids", [roles["Обычная"].id])
    assert await permissions.level(guild, member) is Level.SENIOR


# --------------------------------------------------------------------------- #
#  Еженедельная проверка
# --------------------------------------------------------------------------- #
async def test_weekly_check_counts_and_publishes(ready, guild, admin, staff_member, bot):
    await submit(bot, guild, staff_member, 9001)
    trainee_role = next(
        r for r in guild.roles if r.name == "Стажер"
    )
    lazy = guild.member(900, "Лентяй", roles=[trainee_role])
    await bot.role_manager.sync_member(guild, lazy)

    result = await bot.scheduler.weekly_report_check(guild.id, guild)
    assert result["ok"] is True
    assert result["sent"] is True

    channel = guild.get_channel(
        int(await bot.repo.settings.get(guild.id, "reports_channel_id"))
    )
    embed = channel.sent[-1]
    assert "ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ" in embed.title
    text = "\n".join(f.value for f in embed.fields)
    assert lazy.mention in text  # попал в список не сдавших
    assert staff_member.mention in text  # попал в список сдавших


async def test_weekly_check_does_not_issue_warnings(ready, guild, admin, staff_member, bot):
    trainee_role = next(r for r in guild.roles if r.name == "Стажер")
    lazy = guild.member(901, "Лентяй2", roles=[trainee_role])
    await bot.role_manager.sync_member(guild, lazy)

    await bot.scheduler.weekly_report_check(guild.id, guild)
    assert await bot.repo.warnings.count(guild.id, lazy.id) == 0


async def test_weekly_check_respects_custom_channel(ready, guild, admin, staff_member, bot):
    staff_chat = next(c for c in guild.channels if c.name == "стафф-чат")
    await bot.repo.settings.set(guild.id, "weekly_report_channel_id", staff_chat.id)
    await submit(bot, guild, staff_member, 9002)
    result = await bot.scheduler.weekly_report_check(guild.id, guild)
    assert result["sent"] is True
    assert any("ЕЖЕНЕДЕЛЬНЫЙ ОТЧЁТ" in (e.title or "") for e in staff_chat.sent)


async def test_weekly_check_can_be_disabled(ready, guild, bot):
    await bot.repo.settings.set(guild.id, "weekly_check_enabled", False)
    info = await bot.scheduler.schedule_weekly(guild.id)
    assert info["enabled"] is False
    assert bot.scheduler.next_run(f"weekly_report_check:{guild.id}") is None


async def test_weekly_time_is_configurable(ready, guild, bot):
    await bot.repo.settings.set(guild.id, "weekly_check_day", "fri")
    await bot.repo.settings.set(guild.id, "weekly_check_hour", 15)
    await bot.repo.settings.set(guild.id, "weekly_check_minute", 30)
    info = await bot.scheduler.weekly_info(guild.id)
    assert "пятницу" in info["description"]
    assert "15:30" in info["description"]
    assert info["next"].weekday() == WEEKDAY_NAMES["fri"][1]
    assert (info["next"].hour, info["next"].minute) == (15, 30)


async def test_default_schedule_is_sunday_20_msk(ready, guild, bot):
    info = await bot.scheduler.weekly_info(guild.id)
    assert info["day"] == "sun"
    assert info["hour"] == 20 and info["minute"] == 0
    assert info["timezone"] == "Europe/Moscow"
    assert "воскресенье" in info["description"]


async def test_period_results_are_persisted(ready, guild, admin, staff_member, bot):
    await submit(bot, guild, staff_member, 9003)
    result = await bot.scheduler.weekly_report_check(guild.id, guild)
    rows = await bot.repo.periods.history(guild.id, staff_member.id)
    assert rows
    assert rows[0]["period_key"] == result["period_key"]


# --------------------------------------------------------------------------- #
#  События сервера
# --------------------------------------------------------------------------- #
async def test_new_controlled_role_registers_staff(ready, guild, roles, bot, bot_member):
    newbie = guild.member(1001, "Новенький", roles=[roles["Обычная"]])
    assert await bot.repo.staff.get(guild.id, newbie.id) is None

    await newbie.add_roles(roles["Стажер"])
    events = bot.get_cog("События")
    before = type("M", (), {"roles": [roles["Обычная"], guild.default_role], "guild": guild})()
    await events.on_member_update(before, newbie)

    staff = await bot.repo.staff.get(guild.id, newbie.id)
    assert staff is not None
    assert staff.position == "Стажер"


async def test_position_change_updates_staff(ready, guild, staff_member, roles, bot):
    events = bot.get_cog("События")
    before_roles = list(staff_member.roles)
    await staff_member.add_roles(roles["Модер"])
    before = type("M", (), {"roles": before_roles, "guild": guild})()
    await events.on_member_update(before, staff_member)

    staff = await bot.repo.staff.get(guild.id, staff_member.id)
    assert staff.position == "Модер"


async def test_member_leave_keeps_history(ready, guild, staff_member, bot):
    await submit(bot, guild, staff_member, 9100)
    events = bot.get_cog("События")
    await events.on_member_remove(staff_member)

    staff = await bot.repo.staff.get(guild.id, staff_member.id)
    assert staff is not None
    assert staff.is_active is False
    assert staff.left_at
    assert await bot.repo.reports.count(guild.id, user_id=staff_member.id, period="all") == 1


async def test_raw_delete_marks_report(ready, guild, staff_member, bot):
    await submit(bot, guild, staff_member, 9101)
    events = bot.get_cog("События")
    payload = discord.RawMessageDeleteEvent(
        {"id": 9101, "channel_id": 1, "guild_id": guild.id}
    )
    await events.on_raw_message_delete(payload)
    assert (await bot.repo.reports.get_by_message(9101)).status == "deleted"


async def test_bot_messages_not_counted(ready, guild, bot):
    channel = guild.get_channel(int(await bot.repo.settings.get(guild.id, "reports_channel_id")))
    events = bot.get_cog("События")
    await events.on_message(FakeMessage(channel, guild.me, REPORT_TEXT, message_id=9102))
    assert await bot.repo.reports.count(guild.id, period="all") == 0


# --------------------------------------------------------------------------- #
#  Smoke: регистрация всех команд
# --------------------------------------------------------------------------- #
REQUIRED_COMMANDS = {
    "ot", "warn", "addwarn", "unwarn", "warns", "clearwarns", "staff",
    "profile", "stats", "history", "check", "top", "setup", "settings",
    "sync", "help", "panel", "report", "reports",
}


async def test_all_required_commands_registered(bot):
    registered = {command.name for command in bot.tree.get_commands()}
    missing = REQUIRED_COMMANDS - registered
    assert not missing, f"Не зарегистрированы команды: {missing}"


async def test_every_command_has_russian_description(bot):
    for command in bot.tree.get_commands():
        assert command.description, f"/{command.name} без описания"


async def test_permissions_command_works(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "perms", interaction)
    embed = interaction.last_embed()
    assert "СИСТЕМА ПРАВ" in embed.title


async def test_watchdog_job(ready, guild, bot):
    result = await bot.scheduler.watchdog(guild.id)
    assert result["db"] is True
    assert await bot.repo.state.get("watchdog_db_ok", guild.id) == "True"


async def test_backup_job(ready, guild, bot, tmp_path):
    result = await bot.scheduler.backup_job(guild.id)
    assert result["ok"] is True
    assert result["path"]
