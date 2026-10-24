"""Moderation commands (/ban, /kick, /mute, /warn, /clear, /lock) & Auto-Audit Logs."""
from __future__ import annotations

import re
from datetime import timedelta
from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands

from bot.utils.permissions import (
    COLOR_ERROR,
    COLOR_INFO,
    COLOR_ORANGE,
    COLOR_SUCCESS,
    is_staff_member,
    rw_embed,
)

if TYPE_CHECKING:
    from main import ReallyWorldBot

INVITE_RE = re.compile(r"(discord\.gg/|discord\.com/invite/|discordapp\.com/invite/)[A-Za-z0-9\-]+", re.IGNORECASE)


class ModerationCog(commands.Cog):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot

    async def _send_log(self, guild: discord.Guild, embed: discord.Embed) -> None:
        settings = await self.bot.db.get_guild_settings(guild.id)
        cid = settings.get("log_channel_id")
        if cid:
            chan = guild.get_channel(cid)
            if isinstance(chan, discord.TextChannel):
                try:
                    await chan.send(embed=embed)
                except discord.HTTPException:
                    pass

    @app_commands.command(name="ban", description="Заблокировать нарушителя на сервере")
    @app_commands.default_permissions(ban_members=True)
    async def ban_cmd(
        self, interaction: discord.Interaction, member: discord.Member, reason: str = "Нарушение правил сервера"
    ) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав модератора.", ephemeral=True)
            return
        if member.id == self.bot.cfg.owner_id or member.top_role >= interaction.user.top_role and interaction.user.id != self.bot.cfg.owner_id:
            await interaction.response.send_message("❌ Вы не можете заблокировать этого пользователя.", ephemeral=True)
            return
        try:
            await member.send(embed=rw_embed("🔨 Вы заблокированы", f"Сервер: **{interaction.guild.name}**\nПричина: `{reason}`", COLOR_ERROR))
        except discord.HTTPException:
            pass
        await member.ban(reason=f"{interaction.user}: {reason}")
        emb = rw_embed("🔨 Блокировка участника", f"**Нарушитель:** {member.mention} (`{member.id}`)\n**Модератор:** {interaction.user.mention}\n**Причина:** `{reason}`", COLOR_ERROR)
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="unban", description="Разблокировать пользователя по Discord ID")
    @app_commands.default_permissions(ban_members=True)
    async def unban_cmd(self, interaction: discord.Interaction, user_id: str, reason: str = "Амнистия / Разбан") -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        if not user_id.strip().isdigit():
            await interaction.response.send_message("❌ Укажите числовой ID пользователя.", ephemeral=True)
            return
        user_obj = discord.Object(id=int(user_id.strip()))
        try:
            await interaction.guild.unban(user_obj, reason=f"{interaction.user}: {reason}")
        except discord.HTTPException:
            await interaction.response.send_message("❌ Пользователь не находится в бан-листе.", ephemeral=True)
            return
        emb = rw_embed("✅ Разблокировка", f"Пользователь `<@{user_id}>` разблокирован модератором {interaction.user.mention}.", COLOR_SUCCESS)
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="kick", description="Исключить участника с сервера")
    @app_commands.default_permissions(kick_members=True)
    async def kick_cmd(self, interaction: discord.Interaction, member: discord.Member, reason: str = "Нарушение правил") -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        if member.id == self.bot.cfg.owner_id:
            await interaction.response.send_message("❌ Нельзя исключить владельца бота!", ephemeral=True)
            return
        await member.kick(reason=f"{interaction.user}: {reason}")
        emb = rw_embed("👢 Исключение (Kick)", f"**Участник:** {member.mention}\n**Модератор:** {interaction.user.mention}\n**Причина:** `{reason}`", COLOR_ORANGE)
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="mute", description="Выдать мут (тайм-аут) участнику в минутах")
    @app_commands.default_permissions(moderate_members=True)
    async def mute_cmd(
        self, interaction: discord.Interaction, member: discord.Member, minutes: app_commands.Range[int, 1, 40320], reason: str = "Нарушение правил чата"
    ) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        if member.id == self.bot.cfg.owner_id:
            await interaction.response.send_message("❌ Нельзя выдать мут владельцу бота!", ephemeral=True)
            return
        await member.timeout(timedelta(minutes=minutes), reason=f"{interaction.user}: {reason}")
        emb = rw_embed(
            "🔇 Мут выдан",
            f"**Участник:** {member.mention}\n**Срок:** `{minutes} мин.`\n**Модератор:** {interaction.user.mention}\n**Причина:** `{reason}`",
            COLOR_ORANGE,
        )
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="unmute", description="Снять мут (тайм-аут) с участника")
    @app_commands.default_permissions(moderate_members=True)
    async def unmute_cmd(self, interaction: discord.Interaction, member: discord.Member) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        await member.timeout(None, reason=f"Снятие мута: {interaction.user}")
        emb = rw_embed("🔊 Мут снят", f"С участника {member.mention} снят мут модератором {interaction.user.mention}.", COLOR_SUCCESS)
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="warn", description="Выдать предупреждение нарушителю")
    @app_commands.default_permissions(moderate_members=True)
    async def warn_cmd(self, interaction: discord.Interaction, member: discord.Member, reason: str) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        warn_id = await self.bot.db.add_warning(interaction.guild.id, member.id, interaction.user.id, reason)
        all_warns = await self.bot.db.get_warnings(interaction.guild.id, member.id)
        emb = rw_embed(
            f"⚠️ Предупреждение #{warn_id}",
            f"**Участник:** {member.mention} (всего варнов: **`{len(all_warns)}`**)\n"
            f"**Модератор:** {interaction.user.mention}\n**Причина:** `{reason}`",
            COLOR_ORANGE,
        )
        await interaction.response.send_message(embed=emb)
        await self._send_log(interaction.guild, emb)

    @app_commands.command(name="warns", description="Посмотреть список предупреждений участника")
    async def warns_cmd(self, interaction: discord.Interaction, member: discord.Member | None = None) -> None:
        if not interaction.guild:
            return
        target = member or interaction.user
        rows = await self.bot.db.get_warnings(interaction.guild.id, target.id)
        if not rows:
            await interaction.response.send_message(f"✅ У {target.mention} нет активных предупреждений!", ephemeral=True)
            return
        text = "\n".join(f"`#{r['id']}` — <@{r['moderator_id']}>: `{r['reason']}`" for r in rows[:15])
        await interaction.response.send_message(embed=rw_embed(f"⚠️ Предупреждения — {target.display_name}", text, COLOR_ORANGE))

    @app_commands.command(name="unwarn", description="Удалить предупреждение по его номеру")
    @app_commands.default_permissions(moderate_members=True)
    async def unwarn_cmd(self, interaction: discord.Interaction, warn_id: int) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        ok = await self.bot.db.delete_warning(interaction.guild.id, warn_id)
        msg = f"✅ Предупреждение `#{warn_id}` удалено." if ok else f"❌ Предупреждение `#{warn_id}` не найдено."
        await interaction.response.send_message(msg, ephemeral=True)

    @app_commands.command(name="clear", description="Очистить последние сообщения в чате (от 1 до 100)")
    @app_commands.default_permissions(manage_messages=True)
    async def clear_cmd(self, interaction: discord.Interaction, amount: app_commands.Range[int, 1, 100]) -> None:
        if not isinstance(interaction.channel, discord.TextChannel) or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        await interaction.response.defer(ephemeral=True)
        deleted = await interaction.channel.purge(limit=amount)
        await interaction.followup.send(f"🧹 Удалено сообщений: **`{len(deleted)}`**.", ephemeral=True)

    @app_commands.command(name="lock", description="Закрыть текущий чат для обычных участников")
    @app_commands.default_permissions(manage_channels=True)
    async def lock_cmd(self, interaction: discord.Interaction) -> None:
        if not interaction.guild or not isinstance(interaction.channel, discord.TextChannel) or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
        overwrite.send_messages = False
        await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite)
        await interaction.response.send_message(embed=rw_embed("🔒 Канал временно закрыт", f"Чат закрыт модератором {interaction.user.mention}.", COLOR_ERROR))

    @app_commands.command(name="unlock", description="Открыть текущий чат для всех участников")
    @app_commands.default_permissions(manage_channels=True)
    async def unlock_cmd(self, interaction: discord.Interaction) -> None:
        if not interaction.guild or not isinstance(interaction.channel, discord.TextChannel) or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        overwrite = interaction.channel.overwrites_for(interaction.guild.default_role)
        overwrite.send_messages = None
        await interaction.channel.set_permissions(interaction.guild.default_role, overwrite=overwrite)
        await interaction.response.send_message(embed=rw_embed("🔓 Канал открыт", f"Чат снова открыт модератором {interaction.user.mention}!", COLOR_SUCCESS))

    # ---------------- Listeners: Anti-Invite & Audit Logs ----------------
    @commands.Cog.listener()
    async def on_message(self, message: discord.Message) -> None:
        if message.author.bot or not message.guild or not isinstance(message.author, discord.Member):
            return
        settings = await self.bot.db.get_guild_settings(message.guild.id)
        if settings.get("automod_invites", 1) and INVITE_RE.search(message.content):
            if not await is_staff_member(self.bot, message.author):
                try:
                    await message.delete()
                    await message.channel.send(
                        f"🚫 {message.author.mention}, реклама сторонних Discord-серверов запрещена!",
                        delete_after=6,
                    )
                except discord.HTTPException:
                    pass
                await self._send_log(
                    message.guild,
                    rw_embed(
                        "🚫 Заблокирована инвайт-ссылка",
                        f"**Автор:** {message.author.mention}\n**Канал:** {message.channel.mention}\n**Текст:** `{message.content[:300]}`",
                        COLOR_ERROR,
                    ),
                )

    @commands.Cog.listener()
    async def on_message_delete(self, message: discord.Message) -> None:
        if message.author.bot or not message.guild or not message.content:
            return
        emb = rw_embed(
            "🗑️ Сообщение удалено",
            f"**Автор:** {message.author.mention} (`{message.author.id}`)\n"
            f"**Канал:** {message.channel.mention}\n"
            f"**Содержимое:**\n>>> {message.content[:900]}",
            COLOR_INFO,
        )
        await self._send_log(message.guild, emb)


async def setup(bot: "ReallyWorldBot") -> None:
    await bot.add_cog(ModerationCog(bot))
