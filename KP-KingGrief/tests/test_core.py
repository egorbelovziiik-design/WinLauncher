"""
Ядро: база данных, миграции, поиск каналов/ролей, классификация отчётов.
"""

from __future__ import annotations

import pytest

from config import CHANNEL_SLOTS, CONTROLLED_ROLES
from database.connection import split_sql
from services.report_tracker import ReportMatcher
from tests.fakes import FakeMessage


# --------------------------------------------------------------------------- #
#  База данных
# --------------------------------------------------------------------------- #
async def test_migrations_apply_and_are_idempotent(database):
    from database.migrations import current_version, migrate

    first = await migrate(database)
    second = await migrate(database)
    assert first == []  # уже применены в фикстуре
    assert second == []
    assert await current_version(database) == 3
    assert await database.integrity_check() is True


async def test_all_required_tables_exist(database):
    rows = await database.fetch_all(
        "SELECT name FROM sqlite_master WHERE type = 'table';"
    )
    tables = {row["name"] for row in rows}
    required = {
        "staff",
        "reports",
        "warnings",
        "punishments",
        "settings",
        "audit_logs",
        "staff_roles",
        "report_periods",
    }
    missing = required - tables
    assert not missing, f"Отсутствуют таблицы: {missing}"


async def test_data_survives_reconnect(database, tmp_path):
    from database.connection import Database
    from database.models import Repository

    repo = Repository(database)
    await repo.staff.upsert(guild_id=1, user_id=7, username="persist", position="Модер")
    await database.close()

    reopened = Database(tmp_path / "kinggrief.db")
    await reopened.connect()
    try:
        staff = await Repository(reopened).staff.get(1, 7)
    finally:
        await reopened.close()
    assert staff is not None
    assert staff.username == "persist"
    assert staff.position == "Модер"


async def test_backup_and_prune(database, tmp_path):
    path = await database.backup(tmp_path, "unit")
    assert path is not None and path.exists()
    for _ in range(5):
        await database.backup(tmp_path, "unit")
    removed = await database.prune_backups(keep=2, directory=tmp_path)
    remaining = list(tmp_path.glob("*.db"))
    assert removed >= 3
    assert len(remaining) == 2


def test_split_sql_ignores_semicolons_in_strings():
    statements = split_sql(
        "CREATE TABLE t (a TEXT DEFAULT 'x;y'); CREATE INDEX i ON t (a); -- коммент;\n"
    )
    assert len(statements) == 2
    assert statements[0].startswith("CREATE TABLE t")
    assert "'x;y'" in statements[0]


# --------------------------------------------------------------------------- #
#  Автопоиск каналов и ролей
# --------------------------------------------------------------------------- #
async def test_auto_discover_channels_finds_all_slots(ready, guild):
    options = await ready.repo.settings.get_many(guild.id)
    for slot in CHANNEL_SLOTS:
        assert int(options[slot] or 0) > 0, f"Канал не найден: {slot}"
        assert guild.get_channel(int(options[slot])) is not None


async def test_auto_discover_does_not_invent_ids(ready, guild):
    """ID берутся только из реально существующих каналов сервера."""
    options = await ready.repo.settings.get_many(guild.id)
    valid = {channel.id for channel in guild.channels}
    for slot in CHANNEL_SLOTS:
        assert int(options[slot] or 0) in valid


async def test_all_controlled_roles_resolved(ready, guild):
    config = await ready.repo.settings.control_roles(guild.id)
    by_name = {entry["name"]: entry for entry in config}
    for expected in CONTROLLED_ROLES:
        assert expected["name"] in by_name
        assert by_name[expected["name"]]["role_id"] > 0


async def test_staff_detected_by_role(ready, guild, staff_member):
    staff = await ready.repo.staff.get(guild.id, staff_member.id)
    assert staff is not None
    assert staff.position == "Мл.Модер"
    assert staff.is_active is True


async def test_missing_roles_reported(ready, guild, roles):
    guild.roles = [r for r in guild.roles if r.name != "Стажер"]
    missing = await ready.role_manager.missing_roles(guild)
    assert "Стажер" in missing


async def test_demote_roles_disabled_by_default(ready, guild):
    config = await ready.repo.settings.control_roles(guild.id)
    tracked = {c["name"] for c in config if c.get("tracked", True)}
    for name in ("Д.Модер", "Д.Ст.Модер", "Д.Гл.Модер", "Д.Админ"):
        assert name not in tracked


async def test_demote_roles_can_be_enabled(ready, guild):
    changed = await ready.role_manager.enable_tracking(
        guild.id, ["Д.Модер", "Д.Админ"], True
    )
    assert set(changed) == {"Д.Модер", "Д.Админ"}
    tracked = await ready.repo.settings.tracked_role_names(guild.id)
    assert "Д.Модер" in tracked and "Д.Админ" in tracked


# --------------------------------------------------------------------------- #
#  Классификация отчётов
# --------------------------------------------------------------------------- #
def base_options(**overrides):
    from config import DEFAULT_SETTINGS

    return {**DEFAULT_SETTINGS, **overrides}


def test_short_message_is_not_report():
    matcher = ReportMatcher(base_options())
    result = matcher.classify(content="ок", attachment_count=0, is_staff=True)
    assert result.valid is False


def test_normal_chat_message_is_not_report():
    matcher = ReportMatcher(base_options())
    result = matcher.classify(
        content="Кто хочет пойти поиграть сегодня вечером после работы?",
        attachment_count=0,
        is_staff=True,
    )
    assert result.valid is False


def test_valid_report_detected():
    matcher = ReportMatcher(base_options())
    result = matcher.classify(
        content="Отчёт за смену: выдал 3 предупреждения, обработал 5 жалоб, "
        "проверил анти-чит, нарушителей забанил.",
        attachment_count=2,
        is_staff=True,
    )
    assert result.valid is True
    assert result.score > 0


def test_non_staff_never_counted():
    matcher = ReportMatcher(base_options())
    result = matcher.classify(
        content="Отчёт за смену: выдал 3 предупреждения и обработал все жалобы игроков.",
        attachment_count=1,
        is_staff=False,
    )
    assert result.valid is False


def test_bots_ignored():
    matcher = ReportMatcher(base_options())
    result = matcher.classify(
        content="Отчёт за смену: выдал 3 предупреждения и обработал все жалобы игроков.",
        attachment_count=0,
        author_is_bot=True,
        is_staff=True,
    )
    assert result.valid is False


def test_require_attachment_enforced():
    matcher = ReportMatcher(base_options(report_require_attachment=True))
    text = "Отчёт за смену: выдал 3 предупреждения и обработал все жалобы игроков."
    assert matcher.classify(content=text, attachment_count=0, is_staff=True).valid is False
    assert matcher.classify(content=text, attachment_count=1, is_staff=True).valid is True


def test_everything_mode_counts_all():
    matcher = ReportMatcher(base_options(report_detection_mode="everything"))
    assert matcher.classify(content="привет", attachment_count=0, is_staff=True).valid is True


async def test_report_saved_and_not_duplicated(ready, guild, staff_member):
    channel = guild.get_channel(
        int(await ready.repo.settings.get(guild.id, "reports_channel_id"))
    )
    message = FakeMessage(
        channel,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4242,
        attachments=[object()],
    )
    first = await ready.report_tracker.handle_message(message)
    second = await ready.report_tracker.handle_message(message)

    assert first is not None and first[0] is not None and first[1] is True
    assert second is not None and second[1] is False
    assert await ready.repo.reports.count(guild.id, user_id=staff_member.id, period="all") == 1


async def test_messages_in_other_channels_ignored(ready, guild, staff_member):
    flood = next(c for c in guild.channels if c.name == "флуд")
    message = FakeMessage(
        flood,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4243,
    )
    assert await ready.report_tracker.handle_message(message) is None
    assert await ready.repo.reports.count(guild.id, period="all") == 0


async def test_edited_report_rechecked(ready, guild, staff_member):
    channel = guild.get_channel(
        int(await ready.repo.settings.get(guild.id, "reports_channel_id"))
    )
    before = FakeMessage(channel, staff_member, "ок", message_id=4244)
    after = FakeMessage(
        channel,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4244,
    )
    assert await ready.report_tracker.handle_message(before) is not None
    result = await ready.report_tracker.handle_edit(before, after)
    assert result is not None
    report = await ready.repo.reports.get_by_message(4244)
    assert "выдал 2 предупреждения" in report.content


async def test_deleted_report_marked(ready, guild, staff_member):
    channel = guild.get_channel(
        int(await ready.repo.settings.get(guild.id, "reports_channel_id"))
    )
    message = FakeMessage(
        channel,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4245,
    )
    await ready.report_tracker.handle_message(message)
    assert await ready.report_tracker.handle_delete(message) is True
    report = await ready.repo.reports.get_by_message(4245)
    assert report.status == "deleted"


async def test_report_stores_all_required_fields(ready, guild, staff_member):
    channel = guild.get_channel(
        int(await ready.repo.settings.get(guild.id, "reports_channel_id"))
    )
    message = FakeMessage(
        channel,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4246,
        attachments=[object(), object()],
    )
    result = await ready.report_tracker.handle_message(message)
    report = await ready.repo.reports.get_by_message(4246)
    assert result is not None
    assert report.username == str(staff_member)
    assert report.user_id == staff_member.id
    assert report.position == "Мл.Модер"
    assert report.attachment_count == 2
    assert report.message_url.startswith("https://discord.com/channels/")
    assert report.status == "pending"
    assert report.period_week and report.period_day and report.period_month


async def test_accept_and_reject_report(ready, guild, staff_member):
    channel = guild.get_channel(
        int(await ready.repo.settings.get(guild.id, "reports_channel_id"))
    )
    message = FakeMessage(
        channel,
        staff_member,
        "Отчёт за смену: выдал 2 предупреждения, обработал 4 жалобы, проверил анти-чит.",
        message_id=4247,
    )
    await ready.report_tracker.handle_message(message)
    await ready.report_tracker.decide(4247, "accepted", decided_by=10)
    assert (await ready.repo.reports.get_by_message(4247)).status == "accepted"
    await ready.report_tracker.decide(4247, "rejected", decided_by=10)
    assert (await ready.repo.reports.get_by_message(4247)).status == "rejected"
