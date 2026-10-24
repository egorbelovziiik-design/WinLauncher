"""
Проверка slash-команд: /ot, /warn, /addwarn, /unwarn, /warns, /clearwarns,
/staff, /profile, /stats, /top, /history, /check, /panel, /setup, /settings,
/sync, /help, /report, /reports.

Команды вызываются напрямую через их настоящие callback'и.
"""

from __future__ import annotations

import discord
import pytest

from tests.conftest import invoke
from tests.fakes import FakeInteraction, FakeMessage
from utils.periods import now

REPORT_TEXT = (
    "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы игроков, "
    "проверил анти-чит, нарушителей отправил в бан."
)


async def submit_report(bot, guild, member, message_id: int, text: str = REPORT_TEXT):
    """Отправляет «настоящее» сообщение в канал отчётов."""
    channel = guild.get_channel(int(await bot.repo.settings.get(guild.id, "reports_channel_id")))
    message = FakeMessage(channel, member, text, message_id=message_id)
    return await bot.report_tracker.handle_message(message)


# --------------------------------------------------------------------------- #
#  /ot
# --------------------------------------------------------------------------- #
async def test_ot_lists_submitted_and_missed(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5001)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "ot", interaction)

    embed = interaction.last_embed()
    assert embed is not None
    assert "ОТЧЁТЫ ПЕРСОНАЛА" in embed.title
    fields = {f.name: f.value for f in embed.fields}

    assert any("СДАЛИ ОТЧЁТЫ" in name for name in fields)
    assert any("НЕ СДАЛИ ОТЧЁТЫ" in name for name in fields)
    assert any("СТАТИСТИКА" in name for name in fields)
    submitted = next(v for k, v in fields.items() if "СДАЛИ ОТЧЁТЫ" in k and "НЕ" not in k)
    assert staff_member.mention in submitted
    stats = next(v for k, v in fields.items() if "СТАТИСТИКА" in k)
    assert "Выполнение" in stats


async def test_ot_period_filters(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5002)
    for value in ("today", "week", "month", "all"):
        interaction = FakeInteraction(guild, admin, bot)
        await invoke(
            bot,
            "ot",
            interaction,
            период=discord.app_commands.Choice(name=value, value=value),
        )
        embed = interaction.last_embed()
        assert embed is not None
        assert "Период" in (embed.description or "")


async def test_ot_user_filter(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5003)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "ot", interaction, пользователь=staff_member)
    embed = interaction.last_embed()
    text = "\n".join(f.value for f in embed.fields) + (embed.description or "")
    assert staff_member.mention in text
    assert "Пользователь" in (embed.description or "")


async def test_ot_role_filter(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5004)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "ot", interaction, роль="Мл.Модер")
    embed = interaction.last_embed()
    assert "Мл.Модер" in (embed.description or "")
    stats = next(f.value for f in embed.fields if "СТАТИСТИКА" in f.name)
    assert "Всего сотрудников: **1**" in stats


async def test_ot_combined_filters(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5005)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "ot",
        interaction,
        период=discord.app_commands.Choice(name="неделя", value="week"),
        пользователь=staff_member,
        роль="Мл.Модер",
    )
    embed = interaction.last_embed()
    assert "Роль" in (embed.description or "")
    assert "Пользователь" in (embed.description or "")


async def test_ot_view_buttons_switch_views(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5006)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "ot", interaction)
    view = interaction.last_view()
    assert view is not None

    labels = {child.label for child in view.children if getattr(child, "label", None)}
    assert {"Обновить", "Сдали", "Не сдали", "Статистика", "Скачать TXT"} <= labels

    await view.btn_missed.callback(FakeInteraction(guild, admin, bot))
    assert view.controller.view_mode == "missed"
    await view.btn_submitted.callback(FakeInteraction(guild, admin, bot))
    assert view.controller.view_mode == "submitted"
    await view.btn_stats.callback(FakeInteraction(guild, admin, bot))
    assert view.controller.view_mode == "stats"
    await view.btn_refresh.callback(FakeInteraction(guild, admin, bot))
    assert view.controller.loaded is True


async def test_ot_txt_export(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 5007)
    data = await bot.report_tracker.build_ot_data(guild.id, period="week")
    text, filename = await bot.report_tracker.txt_export(guild.id, data, guild.name)
    assert filename.endswith(".txt")
    assert "СДАЛИ ОТЧЁТЫ" in text
    assert "НЕ СДАЛИ ОТЧЁТЫ" in text
    assert "Выполнение" in text
    assert staff_member.name in text


async def test_ot_pagination(ready, guild, admin, roles, bot):
    trainee = guild.role("Стажер")
    for index in range(10):
        guild.member(200 + index, f"Сотрудник{index}", roles=[trainee])
    await bot.role_manager.sync_guild(guild)

    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "ot", interaction)
    view = interaction.last_view()
    assert view.total_pages > 1
    assert "Страница" in (interaction.last_embed().description or "")

    page2 = FakeInteraction(guild, admin, bot)
    await view.next.callback(page2)
    assert view.page == 2


async def test_ot_denied_for_regular_member(ready, guild, roles, bot, bot_member):
    outsider = guild.member(300, "ОбычныйЮзер", roles=[roles["Обычная"]])
    interaction = FakeInteraction(guild, outsider, bot)
    await invoke(bot, "ot", interaction)
    embed = interaction.last_embed()
    assert "Недостаточно прав" in embed.title


# --------------------------------------------------------------------------- #
#  /warn 1 · 2 · 3
# --------------------------------------------------------------------------- #
async def test_warn_one(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="1", value=1),
        причина="Опоздание",
    )
    embed = interaction.last_embed()
    assert "ПРЕДУПРЕЖДЕНИЕ" in embed.title
    fields = {f.name: f.value for f in embed.fields}
    assert fields["⚠️ Варны"] == "**1/3**"
    assert "Первое предупреждение" in fields["Статус"]
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 1

    warns_channel = guild.get_channel(int(await bot.repo.settings.get(guild.id, "warns_channel_id")))
    assert len(warns_channel.sent) == 1


async def test_warn_two_is_last_warning(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="2", value=2),
    )
    embed = interaction.last_embed()
    fields = {f.name: f.value for f in embed.fields}
    assert fields["⚠️ Варны"] == "**2/3**"
    assert "Последнее предупреждение" in fields["Статус"]
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 2


async def test_warn_three_requires_confirmation(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="3", value=3),
        причина="Систематические нарушения",
    )
    embed = interaction.last_embed()
    assert "ТРЕБУЕТСЯ ПОДТВЕРЖДЕНИЕ" in embed.title
    view = interaction.last_view()
    assert view is not None

    pending = await bot.repo.punishments.pending_for(guild.id, staff_member.id)
    assert pending is not None
    # роль ещё НЕ снята
    assert any(r.name == "Мл.Модер" for r in staff_member.roles)


async def test_warn_three_confirm_removes_only_controlled_role(
    ready, guild, admin, staff_member, roles, bot
):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="3", value=3),
    )
    view = interaction.last_view()

    confirm = FakeInteraction(guild, admin, bot)
    await view.confirm.callback(confirm)

    names = {role.name for role in staff_member.roles}
    assert "Мл.Модер" not in names, "контролируемая роль должна быть снята"
    assert "Обычная роль" in names, "посторонняя роль не должна быть затронута"
    assert "@everyone" in names

    outcome_embed = confirm.last_embed()
    assert "СНЯТИЕ С ДОЛЖНОСТИ" in outcome_embed.title

    warns_channel = guild.get_channel(int(await bot.repo.settings.get(guild.id, "warns_channel_id")))
    assert any("СНЯТИЕ С ДОЛЖНОСТИ" in (e.title or "") for e in warns_channel.sent)


async def test_warn_three_cancel_keeps_role(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="3", value=3),
    )
    view = interaction.last_view()
    cancel = FakeInteraction(guild, admin, bot)
    await view.cancel.callback(cancel)

    assert any(r.name == "Мл.Модер" for r in staff_member.roles)
    assert await bot.repo.punishments.pending_for(guild.id, staff_member.id) is None
    assert "отменено" in (cancel.last_embed().title or "").lower()


async def test_confirm_requires_admin(ready, guild, admin, staff_member, roles, bot, bot_member):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="3", value=3),
    )
    view = interaction.last_view()

    trainee = guild.member(301, "СтажерПетя", roles=[roles["Стажер"]])
    outsider = FakeInteraction(guild, trainee, bot)
    await view.confirm.callback(outsider)
    assert "Недостаточно прав" in (outsider.last_embed().title or "")
    assert any(r.name == "Мл.Модер" for r in staff_member.roles)


async def test_multiple_controlled_roles_requires_choice(
    ready, guild, admin, roles, bot, bot_member
):
    multi = guild.member(302, "ДвеДолжности", roles=[roles["Мл.Модер"], roles["Модер"]])
    await bot.role_manager.sync_member(guild, multi)

    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=multi,
        количество=discord.app_commands.Choice(name="3", value=3),
    )
    view = interaction.last_view()
    assert view.picker is not None, "при нескольких ролях должен появиться выбор"

    # без явного выбора снимаем «Модер» — старшую из двух
    picker_interaction = FakeInteraction(guild, admin, bot)
    view.picker._values = [str(roles["Модер"].id)]
    await view.picker.callback(picker_interaction)
    assert view.selected_role_id == roles["Модер"].id

    confirm = FakeInteraction(guild, admin, bot)
    await view.confirm.callback(confirm)
    names = {r.name for r in multi.roles}
    assert "Модер" not in names
    assert "Мл.Модер" in names, "вторая контролируемая роль должна остаться"


async def test_warn_denied_for_moderator(ready, guild, staff_member, roles, bot, bot_member):
    moderator = guild.member(303, "МодераторБезПрав", roles=[roles["Модер"]])
    interaction = FakeInteraction(guild, moderator, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=staff_member,
        количество=discord.app_commands.Choice(name="1", value=1),
    )
    assert "Недостаточно прав" in (interaction.last_embed().title or "")
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 0


async def test_warn_on_non_staff_rejected(ready, guild, admin, roles, bot, bot_member):
    outsider = guild.member(304, "НеСотрудник", roles=[roles["Обычная"]])
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "warn",
        interaction,
        сотрудник=outsider,
        количество=discord.app_commands.Choice(name="1", value=1),
    )
    assert "не является сотрудником" in (interaction.last_embed().description or "").lower()


# --------------------------------------------------------------------------- #
#  Защита от снятия посторонних ролей
# --------------------------------------------------------------------------- #
async def test_bot_never_removes_uncontrolled_role(ready, guild, admin, roles, bot, bot_member):
    victim = guild.member(305, "Жертва", roles=[roles["Обычная"], roles["Мл.Модер"]])
    result = await bot.role_manager.remove_controlled_role(
        guild, victim, roles["Обычная"], actor=admin, reason="проверка"
    )
    assert result.ok is False
    assert "посторонн" in result.reason.lower()
    assert any(r.name == "Обычная роль" for r in victim.roles)


async def test_bot_cannot_remove_role_above_itself(ready, guild, admin, roles, bot, bot_member):
    # поднимаем роль Админ выше роли бота — Discord не позволит её снять
    roles["Админ"].position = 99
    victim = guild.member(306, "Высокий", roles=[roles["Админ"]])
    result = await bot.role_manager.remove_controlled_role(
        guild, victim, roles["Админ"], actor=admin, reason="проверка"
    )
    assert result.ok is False
    assert "иерарх" in result.reason.lower()
    assert any(r.name == "Админ" for r in victim.roles)


async def test_managed_role_not_removed(ready, guild, admin, roles, bot, bot_member):
    victim = guild.member(307, "Интеграция", roles=[roles["Интеграция"]])
    result = await bot.role_manager.remove_controlled_role(
        guild, victim, roles["Интеграция"], actor=admin, reason="проверка"
    )
    assert result.ok is False


async def test_bot_without_manage_roles_refuses(ready, guild, admin, roles, bot, bot_member):
    from tests.fakes import FakePermissions

    bot_member.guild_permissions = FakePermissions(manage_roles=False)
    victim = guild.member(308, "Проверяемый", roles=[roles["Мл.Модер"]])
    result = await bot.role_manager.remove_controlled_role(
        guild, victim, roles["Мл.Модер"], actor=admin, reason="проверка"
    )
    assert result.ok is False
    assert "Управлять ролями" in result.reason


# --------------------------------------------------------------------------- #
#  /addwarn · /unwarn · /warns · /clearwarns
# --------------------------------------------------------------------------- #
async def test_addwarn(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "addwarn", interaction, сотрудник=staff_member, причина="Тест")
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 1
    assert "ПРЕДУПРЕЖДЕНИЕ" in (interaction.last_embed().title or "")

    second = FakeInteraction(guild, admin, bot)
    await invoke(bot, "addwarn", second, сотрудник=staff_member, причина="Тест 2")
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 2


async def test_unwarn(ready, guild, admin, staff_member, bot):
    await bot.repo.warnings.set_count(guild.id, staff_member.id, 2, admin.id, "тест")
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "unwarn", interaction, сотрудник=staff_member)
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 1
    assert "СНЯТО" in (interaction.last_embed().title or "")


async def test_unwarn_when_none(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "unwarn", interaction, сотрудник=staff_member)
    assert "нет активных предупреждений" in (interaction.last_embed().description or "")


async def test_warns_shows_all(ready, guild, admin, staff_member, bot):
    await bot.repo.warnings.set_count(guild.id, staff_member.id, 2, admin.id, "Причина А")
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "warns", interaction, сотрудник=staff_member)
    embed = interaction.last_embed()
    assert "ПРЕДУПРЕЖДЕНИЯ" in embed.title
    assert "**2/3**" in embed.description


async def test_clearwarns(ready, guild, admin, staff_member, bot):
    await bot.repo.warnings.set_count(guild.id, staff_member.id, 3, admin.id, "тест")
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "clearwarns", interaction, сотрудник=staff_member)
    assert await bot.repo.warnings.count(guild.id, staff_member.id) == 0
    assert "СБРОШЕНЫ" in (interaction.last_embed().title or "")


async def test_warnings_persist_after_restart(ready, guild, admin, staff_member, bot, tmp_path):
    await bot.repo.warnings.set_count(guild.id, staff_member.id, 2, admin.id, "persist")
    path = bot.db.path
    await bot.db.close()

    from database.connection import Database
    from database.models import Repository

    reopened = Database(path)
    await reopened.connect()
    try:
        count = await Repository(reopened).warnings.count(guild.id, staff_member.id)
    finally:
        await reopened.close()
    assert count == 2


# --------------------------------------------------------------------------- #
#  Остальные команды
# --------------------------------------------------------------------------- #
async def test_staff_command(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "staff", interaction)
    embed = interaction.last_embed()
    assert "ПЕРСОНАЛ" in embed.title
    assert staff_member.name in embed.fields[0].value


async def test_profile_command(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6001)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "profile", interaction, сотрудник=staff_member)
    embed = interaction.last_embed()
    assert "ПРОФИЛЬ" in embed.title
    names = [f.name for f in embed.fields]
    assert "📋 Отчётов за неделю" in names
    assert "⚠️ Варны" in names
    assert "📈 Активность" in names
    assert interaction.last_view() is not None


async def test_profile_self_only_for_staff(ready, guild, staff_member, bot):
    interaction = FakeInteraction(guild, staff_member, bot)
    await invoke(bot, "profile", interaction)
    assert "ПРОФИЛЬ" in (interaction.last_embed().title or "")

    other = FakeInteraction(guild, staff_member, bot)
    another = guild.member(400, "Другой", roles=[])
    await invoke(bot, "profile", other, сотрудник=another)
    assert "Недостаточно прав" in (other.last_embed().title or "")


async def test_stats_command(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6002)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "stats", interaction)
    embed = interaction.last_embed()
    assert "СТАТИСТИКА" in embed.title
    names = [f.name for f in embed.fields]
    assert "👥 Сотрудников всего" in names
    assert "📈 Выполнение (нед.)" in names


async def test_top_command(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6003)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "top", interaction)
    embed = interaction.last_embed()
    assert "РЕЙТИНГ" in embed.title


async def test_history_command(ready, guild, admin, staff_member, bot):
    await bot.repo.warnings.set_count(guild.id, staff_member.id, 1, admin.id, "для истории")
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "history", interaction)
    embed = interaction.last_embed()
    assert "ИСТОРИЯ" in embed.title


async def test_check_command(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "check", interaction, сотрудник=staff_member)
    embed = interaction.last_embed()
    assert "ПРОВЕРКА" in embed.title
    assert any("Вердикт" in f.name for f in embed.fields)


async def test_panel_command(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6004)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "panel", interaction)
    embed = interaction.last_embed()
    assert "ПАНЕЛЬ УПРАВЛЕНИЯ" in embed.title
    view = interaction.last_view()
    labels = {c.label for c in view.children if getattr(c, "label", None)}
    assert {"Отчёты", "Персонал", "Предупреждения", "Рейтинг", "Статистика", "Настройки"} <= labels


async def test_panel_buttons_open_sections(ready, guild, admin, staff_member, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "panel", interaction)
    view = interaction.last_view()

    for button, expected in (
        (view.reports, "ОТЧЁТЫ ПЕРСОНАЛА"),
        (view.staff, "ПЕРСОНАЛ"),
        (view.warns, "ПРЕДУПРЕЖДЕНИЯ"),
        (view.top, "РЕЙТИНГ"),
        (view.stats, "СТАТИСТИКА"),
    ):
        sub = FakeInteraction(guild, admin, bot)
        await button.callback(sub)
        embed = sub.last_embed()
        assert embed is not None, f"кнопка {button.label} ничего не вернула"
        assert expected in (embed.title or "")


async def test_panel_denied_for_trainee(ready, guild, roles, bot, bot_member):
    """Стажер (уровень STAFF) не имеет доступа к панели управления."""
    trainee = guild.member(410, "СтажерПанель", roles=[roles["Стажер"]])
    interaction = FakeInteraction(guild, trainee, bot)
    await invoke(bot, "panel", interaction)
    assert "Недостаточно прав" in (interaction.last_embed().title or "")


async def test_panel_settings_button_requires_senior(ready, guild, staff_member, bot):
    """Модератор видит панель, но не раздел настроек."""
    interaction = FakeInteraction(guild, staff_member, bot)
    await invoke(bot, "panel", interaction)
    assert "ПАНЕЛЬ УПРАВЛЕНИЯ" in (interaction.last_embed().title or "")
    view = interaction.last_view()
    click = FakeInteraction(guild, staff_member, bot)
    await view.settings.callback(click)
    assert "Недостаточно прав" in (click.last_embed().title or "")


async def test_moderator_sees_only_own_profile(ready, guild, staff_member, bot):
    """Модер/Мл.Модер не видит чужие профили (по ТЗ)."""
    other = guild.member(411, "ДругойСотрудник", roles=[])
    interaction = FakeInteraction(guild, staff_member, bot)
    await invoke(bot, "profile", interaction, сотрудник=other)
    assert "Недостаточно прав" in (interaction.last_embed().title or "")


async def test_help_command(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "help", interaction)
    embed = interaction.last_embed()
    assert "ПОМОЩЬ" in embed.title
    joined = " ".join(f.name for f in embed.fields)
    assert "Отчёты" in joined and "Предупреждения" in joined


async def test_help_available_to_everyone(ready, guild, roles, bot, bot_member):
    outsider = guild.member(401, "ПростоЮзер", roles=[roles["Обычная"]])
    interaction = FakeInteraction(guild, outsider, bot)
    await invoke(bot, "help", interaction)
    assert "ПОМОЩЬ" in (interaction.last_embed().title or "")


async def test_setup_command(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "setup_cmd", interaction)
    embed = interaction.last_embed()
    assert "НАСТРОЙКА" in embed.title
    assert interaction.last_view() is not None


async def test_settings_command_shows_state(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "settings", interaction)
    embed = interaction.last_embed()
    assert "НАСТРОЙКИ" in embed.title


async def test_settings_change_int_value(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "settings",
        interaction,
        параметр=discord.app_commands.Choice(name="max", value="max_warnings"),
        значение="5",
    )
    assert await bot.repo.settings.get(guild.id, "max_warnings") == 5


async def test_settings_rejects_out_of_range(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "settings",
        interaction,
        параметр=discord.app_commands.Choice(name="max", value="max_warnings"),
        значение="999",
    )
    assert "вне диапазона" in (interaction.last_embed().title or "")


async def test_settings_change_weekly_time(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(
        bot,
        "settings",
        interaction,
        параметр=discord.app_commands.Choice(name="hour", value="weekly_check_hour"),
        значение="18",
    )
    assert await bot.repo.settings.get(guild.id, "weekly_check_hour") == 18
    info = await bot.scheduler.weekly_info(guild.id)
    assert "18:00" in info["description"]


async def test_sync_command(ready, guild, admin, bot):
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "sync", interaction)
    embed = interaction.last_embed()
    assert "Синхронизация завершена" in embed.title


async def test_report_command_by_id(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6005)
    report = await bot.repo.reports.get_by_message(6005)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "report", interaction, идентификатор=report.id)
    embed = interaction.last_embed()
    assert f"ОТЧЁТ #{report.id}" in embed.title
    assert interaction.last_view() is not None


async def test_report_accept_button(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6006)
    report = await bot.repo.reports.get_by_message(6006)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "report", interaction, идентификатор=report.id)
    view = interaction.last_view()

    click = FakeInteraction(guild, admin, bot)
    await view.accept.callback(click)
    assert (await bot.repo.reports.get_by_message(6006)).status == "accepted"


async def test_reports_command(ready, guild, admin, staff_member, bot):
    await submit_report(bot, guild, staff_member, 6007)
    interaction = FakeInteraction(guild, admin, bot)
    await invoke(bot, "reports", interaction, сотрудник=staff_member)
    embed = interaction.last_embed()
    assert "ОТЧЁТЫ" in embed.title
