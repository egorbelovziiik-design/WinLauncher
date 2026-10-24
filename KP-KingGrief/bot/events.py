"""
Обработчики событий Discord для КП-KingGrief.

Отвечает за:
  * регистрацию и перепроверку отчётов (on_message / on_message_edit);
  * реакцию на удаление отчёта;
  * отслеживание выдачи/снятия контролируемых ролей;
  * сохранение истории при уходе сотрудника.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord.ext import commands

from utils.logging import get_logger

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("events")


class EventsCog(commands.Cog, name="События"):
    """Слушатели событий сервера."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo
        self.tracker = bot.report_tracker
        self.roles = bot.role_manager

    # ------------------------------------------------------------------ #
    #  Сообщения
    # ------------------------------------------------------------------ #
    @commands.Cog.listener()
    async def on_message(self, message: discord.Message) -> None:
        """Регистрация отчёта. Сообщения ботов игнорируются."""
        if message.guild is None:
            return
        if getattr(message.author, "bot", False):
            return
        try:
            result = await self.tracker.handle_message(message)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при обработке сообщения %s", message.id)
            return
        if result and result[0] and result[1]:
            await self._announce_registration(message, result[0])

    @commands.Cog.listener()
    async def on_message_edit(self, before: discord.Message, after: discord.Message) -> None:
        if after.guild is None or before.content == after.content:
            return
        try:
            await self.tracker.handle_edit(before, after)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при перепроверке сообщения %s", after.id)

    @commands.Cog.listener()
    async def on_message_delete(self, message: discord.Message) -> None:
        if message.guild is None:
            return
        try:
            await self.tracker.handle_delete(message)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при обработке удаления сообщения %s", message.id)

    @commands.Cog.listener()
    async def on_raw_message_delete(self, payload: discord.RawMessageDeleteEvent) -> None:
        """Отчёт могли удалить, когда его уже нет в кэше."""
        if payload.guild_id is None:
            return
        existing = await self.repo.reports.get_by_message(payload.message_id)
        if existing is None or existing.status == "deleted":
            return
        await self.repo.reports.mark_deleted(payload.message_id)
        await self.repo.staff.bump_reports(payload.guild_id, existing.user_id, -1)
        await self.repo.audit.add(
            payload.guild_id,
            "report_deleted",
            target_id=payload.message_id,
            detail=f"Отчёт #{existing.id} удалён (вне кэша)",
        )
        log.info("Отчёт #%s помечен удалённым (raw event).", existing.id)

    # ------------------------------------------------------------------ #
    #  Участники
    # ------------------------------------------------------------------ #
    @commands.Cog.listener()
    async def on_member_update(self, before: discord.Member, after: discord.Member) -> None:
        if getattr(after, "bot", False):
            return
        try:
            result = await self.roles.handle_member_update(before, after)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при обновлении участника %s", after.id)
            return
        if result and result["action"] == "added":
            await self._announce_new_staff(after, result.get("position", ""))

    @commands.Cog.listener()
    async def on_member_remove(self, member: discord.Member) -> None:
        try:
            await self.roles.handle_member_remove(member)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при уходе участника %s", member.id)

    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member) -> None:
        if getattr(member, "bot", False):
            return
        try:
            await self.roles.sync_member(member.guild, member)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка при входе участника %s", member.id)

    @commands.Cog.listener()
    async def on_guild_available(self, guild: discord.Guild) -> None:
        """Сервер снова доступен после сбоя — синхронизируем персонал."""
        try:
            await self.roles.sync_guild(guild)
        except Exception:  # noqa: BLE001
            log.exception("Ошибка синхронизации после восстановления «%s»", guild.name)

    # ------------------------------------------------------------------ #
    #  Уведомления
    # ------------------------------------------------------------------ #
    async def _announce_registration(self, message: discord.Message, report) -> None:
        channel_id = int(
            await self.repo.settings.get(message.guild.id, "staff_chat_channel_id", 0) or 0
        )
        channel = message.guild.get_channel(channel_id) if channel_id else None
        if channel is None:
            return
        embed = discord.Embed(
            title="✅ Отчёт зарегистрирован",
            description=(
                f"👤 Сотрудник: {message.author.mention}\n"
                f"🎖️ Должность: {report.position or '—'}\n"
                f"📎 Вложений: {report.attachment_count}\n"
                f"🔗 [Открыть отчёт]({report.message_url})"
            ),
            color=0x2ECC71,
        )
        try:
            await channel.send(embed=embed)
        except discord.HTTPException as exc:
            log.debug("Не удалось уведомить стафф-чат: %s", exc)

    async def _announce_new_staff(self, member: discord.Member, position: str) -> None:
        channel_id = int(
            await self.repo.settings.get(member.guild.id, "staff_chat_channel_id", 0) or 0
        )
        channel = member.guild.get_channel(channel_id) if channel_id else None
        if channel is None:
            return
        embed = discord.Embed(
            title="👋 Новый сотрудник под контролем",
            description=f"{member.mention} получил должность **{position}**.",
            color=0x3498DB,
        )
        try:
            await channel.send(embed=embed)
        except discord.HTTPException as exc:
            log.debug("Не удалось уведомить стафф-чат: %s", exc)


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover - для load_extension
    await bot.add_cog(EventsCog(bot))
