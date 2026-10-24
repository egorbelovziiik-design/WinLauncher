"""Owner Control Panel & Guild Setup commands (locked to robik0277 / 1381255368650526861)."""
from __future__ import annotations

import platform
import time
from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands

from bot.config import build_invite_url
from bot.utils.permissions import (
    COLOR_ERROR,
    COLOR_GOLD,
    COLOR_INFO,
    COLOR_SUCCESS,
    is_bot_admin,
    is_supreme_owner,
    rw_embed,
)

if TYPE_CHECKING:
    from main import ReallyWorldBot


class OwnerControlView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=300)
        self.bot = bot

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if not await is_bot_admin(self.bot, interaction.user):
            await interaction.response.send_message(
                f"❌ Доступ только для владельца бота (`{self.bot.cfg.owner_username}` / `{self.bot.cfg.owner_id}`).",
                ephemeral=True,
            )
            return False
        return True

    @discord.ui.button(label="🔄 Синхронизировать /команды", style=discord.ButtonStyle.primary)
    async def sync_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True)
        synced = await self.bot.tree.sync()
        if interaction.guild:
            self.bot.tree.copy_global_to(guild=interaction.guild)
            await self.bot.tree.sync(guild=interaction.guild)
        await interaction.followup.send(
            f"✅ Успешно синхронизировано **{len(synced)}** глобальных групп слэш-команд (и мгновенно обновлено для этого сервера)!",
            ephemeral=True,
        )

    @discord.ui.button(label="🖼 Установить аватарку КП", style=discord.ButtonStyle.secondary)
    async def avatar_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True)
        avatar_file = self.bot.cfg.avatar_path
        if not avatar_file.exists():
            await interaction.followup.send("❌ Файл `assets/avatar_kp_beach.png` не найден.", ephemeral=True)
            return
        try:
            await self.bot.user.edit(avatar=avatar_file.read_bytes())
            await interaction.followup.send("✅ Фирменная аватарка **КП** успешно установлена на бота!", ephemeral=True)
        except discord.HTTPException as exc:
            await interaction.followup.send(f"⚠️ Discord ограничил частоту смены аватара: `{exc}`", ephemeral=True)

    @discord.ui.button(label="🔗 Ссылка приглашения", style=discord.ButtonStyle.success)
    async def invite_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        cid = self.bot.user.id if self.bot.user else self.bot.cfg.client_id
        admin_url = build_invite_url(cid, permissions=8)
        mod_url = build_invite_url(cid, permissions=1099780066422)
        emb = rw_embed(
            "🔗 Пригласить бота в Discord",
            f"**1. Полные права Администратора (рекомендуется для КП/Тикетов):**\n👉 [Пригласить бота на сервер]({admin_url})\n\n"
            f"**2. Только права Модерации и Управления каналами:**\n👉 [Пригласить с базовыми правами]({mod_url})",
            COLOR_SUCCESS,
        )
        await interaction.response.send_message(embed=emb, ephemeral=True)

    @discord.ui.button(label="♻️ Перезагрузить модули", style=discord.ButtonStyle.secondary)
    async def reload_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True)
        reloaded = []
        for ext in list(self.bot.extensions.keys()):
            await self.bot.reload_extension(ext)
            reloaded.append(ext)
        await interaction.followup.send(
            f"✅ Без перезапуска перезагружено модулей (`{len(reloaded)}`): `{', '.join(reloaded)}`",
            ephemeral=True,
        )


class OwnerCog(commands.Cog):
    owner_group = app_commands.Group(name="owner", description="Панель главного владельца бота (robik0277)")
    setup_group = app_commands.Group(
        name="setup",
        description="Настройка каналов и ролей КП на сервере",
        default_permissions=discord.Permissions(administrator=True),
    )

    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot

    @owner_group.command(name="panel", description="Открыть интерактивную панель владельца бота")
    async def owner_panel(self, interaction: discord.Interaction) -> None:
        if not await is_bot_admin(self.bot, interaction.user):
            await interaction.response.send_message(
                f"❌ Эта команда доступна только владельцу бота (`{self.bot.cfg.owner_username}` / ID `{self.bot.cfg.owner_id}`).",
                ephemeral=True,
            )
            return

        uptime_sec = int(time.time() - self.bot.started_at)
        hours, rem = divmod(uptime_sec, 3600)
        mins, secs = divmod(rem, 60)
        co_owners = await self.bot.db.list_co_owners()

        emb = rw_embed("👑 Панель Владельца Бота ReallyWorld КП", color=COLOR_GOLD)
        emb.add_field(
            name="Владелец",
            value=f"`{self.bot.cfg.owner_username}` (<@{self.bot.cfg.owner_id}>\nID: `{self.bot.cfg.owner_id}`)",
            inline=True,
        )
        emb.add_field(
            name="Состояние",
            value=f"Пинг: `{round(self.bot.latency * 1000)} мс`\nАптайм: `{hours}ч {mins}м {secs}с`\nСерверов: `{len(self.bot.guilds)}`",
            inline=True,
        )
        emb.add_field(
            name="Среда",
            value=f"Python `{platform.python_version()}`\ndiscord.py `{discord.__version__}`\nСовладельцев: `{len(co_owners)}`",
            inline=True,
        )
        await interaction.response.send_message(embed=emb, view=OwnerControlView(self.bot), ephemeral=True)

    @owner_group.command(name="invite", description="Получить прямую ссылку для приглашения бота на сервер")
    async def owner_invite(self, interaction: discord.Interaction) -> None:
        cid = self.bot.user.id if self.bot.user else self.bot.cfg.client_id
        if not cid:
            await interaction.response.send_message("❌ CLIENT_ID ещё не определён.", ephemeral=True)
            return
        url = build_invite_url(cid, permissions=8)
        emb = rw_embed(
            "🔗 Прямая ссылка на приглашение бота",
            f"Нажмите ниже, чтобы добавить бота на свой Discord-сервер:\n\n👉 **[ПРИГЛАСИТЬ БОТА НА СЕРВЕР]({url})**\n\n`{url}`",
            COLOR_SUCCESS,
        )
        await interaction.response.send_message(embed=emb, ephemeral=True)

    @owner_group.command(name="coowner-add", description="Назначить доверенного совладельца бота (только robik0277)")
    @app_commands.describe(user="Пользователь, которому выдаётся доступ совладельца")
    async def coowner_add(self, interaction: discord.Interaction, user: discord.User) -> None:
        if not await is_supreme_owner(self.bot, interaction.user):
            await interaction.response.send_message(
                f"❌ Только главный владелец `{self.bot.cfg.owner_username}` (`{self.bot.cfg.owner_id}`) может управлять совладельцами!",
                ephemeral=True,
            )
            return
        await self.bot.db.add_co_owner(user.id, interaction.user.id)
        await interaction.response.send_message(
            embed=rw_embed("✅ Совладелец добавлен", f"Пользователь {user.mention} (`{user.id}`) добавлен в список совладельцев.", COLOR_SUCCESS),
            ephemeral=True,
        )

    @owner_group.command(name="coowner-remove", description="Снять права совладельца бота (только robik0277)")
    async def coowner_remove(self, interaction: discord.Interaction, user: discord.User) -> None:
        if not await is_supreme_owner(self.bot, interaction.user):
            await interaction.response.send_message("❌ Доступ только главному владельцу.", ephemeral=True)
            return
        removed = await self.bot.db.remove_co_owner(user.id)
        msg = f"Права совладельца у {user.mention} сняты." if removed else "Этот пользователь не был совладельцем."
        await interaction.response.send_message(embed=rw_embed("ℹ️ Управление совладельцами", msg, COLOR_INFO), ephemeral=True)

    @owner_group.command(name="status", description="Изменить статус активности бота в Discord")
    @app_commands.choices(
        activity_type=[
            app_commands.Choice(name="Играет в...", value="playing"),
            app_commands.Choice(name="Смотрит...", value="watching"),
            app_commands.Choice(name="Слушает...", value="listening"),
        ]
    )
    async def owner_status(self, interaction: discord.Interaction, activity_type: str, text: str) -> None:
        if not await is_bot_admin(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет доступа.", ephemeral=True)
            return
        if activity_type == "watching":
            act = discord.Activity(type=discord.ActivityType.watching, name=text)
        elif activity_type == "listening":
            act = discord.Activity(type=discord.ActivityType.listening, name=text)
        else:
            act = discord.Game(name=text)
        await self.bot.change_presence(status=discord.Status.online, activity=act)
        await interaction.response.send_message(f"✅ Статус бота обновлён на: **{text}**", ephemeral=True)

    @owner_group.command(name="broadcast", description="Отправить официальное объявление от лица КП")
    async def owner_broadcast(
        self, interaction: discord.Interaction, channel: discord.TextChannel, title: str, message: str, ping_everyone: bool = False
    ) -> None:
        if not await is_bot_admin(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет доступа.", ephemeral=True)
            return
        emb = rw_embed(f"📢 {title}", message, COLOR_GOLD)
        content = "@everyone" if ping_everyone else None
        await channel.send(content=content, embed=emb)
        await interaction.response.send_message(f"✅ Объявление опубликовано в {channel.mention}!", ephemeral=True)

    # ---------------- Guild Configuration (/setup) ----------------
    @setup_group.command(name="config", description="Привязать роли и каналы КП / Логирования / Тикетов")
    async def setup_config(
        self,
        interaction: discord.Interaction,
        staff_role: discord.Role | None = None,
        verify_role: discord.Role | None = None,
        check_role: discord.Role | None = None,
        log_channel: discord.TextChannel | None = None,
        ticket_category: discord.CategoryChannel | None = None,
        ticket_log_channel: discord.TextChannel | None = None,
        kp_review_channel: discord.TextChannel | None = None,
        automod_invites: bool | None = None,
    ) -> None:
        if not interaction.guild:
            return
        gid = interaction.guild.id
        updates = []
        if staff_role:
            await self.bot.db.update_guild_setting(gid, "staff_role_id", staff_role.id)
            updates.append(f"Роль КП/Стаффа: {staff_role.mention}")
        if verify_role:
            await self.bot.db.update_guild_setting(gid, "verify_role_id", verify_role.id)
            updates.append(f"Роль верификации: {verify_role.mention}")
        if check_role:
            await self.bot.db.update_guild_setting(gid, "check_role_id", check_role.id)
            updates.append(f"Роль «На проверке»: {check_role.mention}")
        if log_channel:
            await self.bot.db.update_guild_setting(gid, "log_channel_id", log_channel.id)
            updates.append(f"Канал логов модерации: {log_channel.mention}")
        if ticket_category:
            await self.bot.db.update_guild_setting(gid, "ticket_category_id", ticket_category.id)
            updates.append(f"Категория тикетов: **{ticket_category.name}**")
        if ticket_log_channel:
            await self.bot.db.update_guild_setting(gid, "ticket_log_channel_id", ticket_log_channel.id)
            updates.append(f"Логи тикетов: {ticket_log_channel.mention}")
        if kp_review_channel:
            await self.bot.db.update_guild_setting(gid, "kp_review_channel_id", kp_review_channel.id)
            updates.append(f"Канал анкет в КП: {kp_review_channel.mention}")
        if automod_invites is not None:
            await self.bot.db.update_guild_setting(gid, "automod_invites", 1 if automod_invites else 0)
            updates.append(f"Анти-реклама Discord-ссылок: `{'ВКЛ' if automod_invites else 'ВЫКЛ'}`")

        text = "\n".join(f"• {u}" for u in updates) if updates else "Параметры не переданы. Используйте `/setup view` для просмотра."
        await interaction.response.send_message(embed=rw_embed("⚙️ Настройки сервера обновлены", text, COLOR_SUCCESS), ephemeral=True)

    @setup_group.command(name="view", description="Показать текущие настройки каналов и ролей сервера")
    async def setup_view(self, interaction: discord.Interaction) -> None:
        if not interaction.guild:
            return
        s = await self.bot.db.get_guild_settings(interaction.guild.id)
        fmt_role = lambda rid: f"<@&{rid}>" if rid else "`Не задана`"
        fmt_chan = lambda cid: f"<#{cid}>" if cid else "`Не задан`"
        desc = (
            f"🛡 **Роль КП / Персонала:** {fmt_role(s.get('staff_role_id'))}\n"
            f"✅ **Роль Верификации:** {fmt_role(s.get('verify_role_id'))}\n"
            f"🔍 **Роль «На проверке»:** {fmt_role(s.get('check_role_id'))}\n"
            f"📋 **Канал логов:** {fmt_chan(s.get('log_channel_id'))}\n"
            f"📂 **Категория тикетов:** {fmt_chan(s.get('ticket_category_id'))}\n"
            f"🗂 **Канал транскриптов тикетов:** {fmt_chan(s.get('ticket_log_channel_id'))}\n"
            f"📝 **Канал заявок в КП:** {fmt_chan(s.get('kp_review_channel_id'))}\n"
            f"🚫 **Блокировка чужих инвайтов:** `{'ВКЛ' if s.get('automod_invites') else 'ВЫКЛ'}`"
        )
        await interaction.response.send_message(embed=rw_embed("⚙️ Конфигурация сервера", desc, COLOR_INFO), ephemeral=True)


async def setup(bot: "ReallyWorldBot") -> None:
    await bot.add_cog(OwnerCog(bot))
