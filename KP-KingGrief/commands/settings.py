"""
Настройка бота без правки кода:
/setup · /settings · /sync

Все каналы и роли выбираются вручную через нативные селекторы Discord
или находятся автоматически по названиям в категории «Кп Сервера».
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

from bot.permissions import permissions, require
from commands.statistics import _settings_embed
from config import CHANNEL_SLOTS, SETTING_SCHEMA
from utils import embeds as E
from utils.logging import get_logger

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("cmd.settings")


# --------------------------------------------------------------------------- #
#  Выбор канала для конкретного слота
# --------------------------------------------------------------------------- #
class ChannelPickView(discord.ui.View):
    def __init__(self, bot: "KingGriefBot", slot: str) -> None:
        super().__init__(timeout=300)
        self.bot = bot
        self.slot = slot
        self.meta = CHANNEL_SLOTS[slot]

    @discord.ui.select(
        cls=discord.ui.ChannelSelect,
        placeholder="Выберите канал…",
        channel_types=[discord.ChannelType.text],
        min_values=1,
        max_values=1,
        custom_id="setup_channel_select",
    )
    async def pick(
        self, interaction: discord.Interaction, select: discord.ui.ChannelSelect
    ) -> None:
        channel = select.values[0]
        await self.bot.role_manager.assign_channel(interaction.guild, self.slot, channel)
        await self.repo_audit(interaction, channel)
        await interaction.response.edit_message(
            embed=E.success(
                "Канал сохранён",
                f"{self.meta['emoji']} {self.meta['title']}: {channel.mention}",
            ),
            view=None,
        )

    async def repo_audit(self, interaction: discord.Interaction, channel: discord.abc.GuildChannel) -> None:
        await self.bot.repo.audit.add(
            interaction.guild.id,
            "settings",
            actor_id=interaction.user.id,
            detail=f"{self.meta['title']} → #{channel.name} ({channel.id})",
        )

    @discord.ui.button(emoji="🚫", label="Сбросить", style=discord.ButtonStyle.secondary, custom_id="setup_channel_reset")
    async def reset(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.bot.role_manager.assign_channel(interaction.guild, self.slot, None)
        await interaction.response.edit_message(
            embed=E.info("Сброшено", f"{self.meta['emoji']} {self.meta['title']}: канал не задан."),
            view=None,
        )


class RolePickView(discord.ui.View):
    """Настройка ролей прав доступа."""

    def __init__(self, bot: "KingGriefBot") -> None:
        super().__init__(timeout=300)
        self.bot = bot

    @discord.ui.select(
        cls=discord.ui.RoleSelect,
        placeholder="👑 Роли главных администраторов",
        min_values=0,
        max_values=10,
        custom_id="setup_admin_roles",
    )
    async def admin(self, interaction: discord.Interaction, select: discord.ui.RoleSelect) -> None:
        await self._save(interaction, "admin_role_ids", select.values, "Главные администраторы")

    @discord.ui.select(
        cls=discord.ui.RoleSelect,
        placeholder="🛡️ Роли старшего состава (Гл.Модер, Ст.Модер)",
        min_values=0,
        max_values=10,
        custom_id="setup_senior_roles",
    )
    async def senior(self, interaction: discord.Interaction, select: discord.ui.RoleSelect) -> None:
        await self._save(interaction, "senior_role_ids", select.values, "Старший состав")

    @discord.ui.select(
        cls=discord.ui.RoleSelect,
        placeholder="👮 Роли модераторов (Модер, Мл.Модер)",
        min_values=0,
        max_values=10,
        custom_id="setup_mod_roles",
    )
    async def mod(self, interaction: discord.Interaction, select: discord.ui.RoleSelect) -> None:
        await self._save(interaction, "mod_role_ids", select.values, "Модераторы")

    @discord.ui.select(
        cls=discord.ui.RoleSelect,
        placeholder="🎓 Роли стажеров",
        min_values=0,
        max_values=10,
        custom_id="setup_trainee_roles",
    )
    async def trainee(self, interaction: discord.Interaction, select: discord.ui.RoleSelect) -> None:
        await self._save(interaction, "trainee_role_ids", select.values, "Стажеры")

    async def _save(
        self,
        interaction: discord.Interaction,
        key: str,
        roles: list[discord.Role],
        label: str,
    ) -> None:
        ids = [role.id for role in roles]
        await self.bot.repo.settings.set(interaction.guild.id, key, ids)
        await self.bot.repo.audit.add(
            interaction.guild.id,
            "settings",
            actor_id=interaction.user.id,
            detail=f"{label}: {', '.join(r.name for r in roles) or 'очищено'}",
        )
        text = "\n".join(f"• {role.mention}" for role in roles) or "— роль не выбрана —"
        await interaction.response.send_message(
            embed=E.success(f"Сохранено: {label}", text), ephemeral=True
        )


# --------------------------------------------------------------------------- #
class SetupView(discord.ui.View):
    """Главное меню первоначальной настройки."""

    def __init__(self, bot: "KingGriefBot") -> None:
        super().__init__(timeout=900)
        self.bot = bot

    async def _status(self, guild: discord.Guild) -> discord.Embed:
        options = await self.bot.repo.settings.get_many(guild.id)
        embed = E.info(f"{bot_name()} | ПЕРВОНАЧАЛЬНАЯ НАСТРОЙКА", f"Сервер: **{guild.name}**")
        lines = []
        for slot, meta in CHANNEL_SLOTS.items():
            value = int(options.get(slot) or 0)
            channel = guild.get_channel(value) if value else None
            lines.append(
                f"{'✅' if channel else '⚠️'} {meta['emoji']} {meta['title']}: "
                + (channel.mention if channel else "`не настроен`")
            )
        embed.add_field(name="📺 Каналы", value="\n".join(lines), inline=False)

        roles = await self.bot.repo.settings.control_roles(guild.id)
        found = [r for r in roles if r.get("role_id")]
        tracked = [r for r in roles if r.get("tracked", True)]
        embed.add_field(
            name="🎭 Роли",
            value=(
                f"Найдено на сервере: **{len(found)}/{len(roles)}**\n"
                f"Отслеживается: **{len([r for r in tracked if r.get('role_id')])}**\n"
                f"Сотрудников в базе: **{await self.bot.repo.staff.count(guild.id)}**"
            ),
            inline=False,
        )
        missing = await self.bot.role_manager.missing_roles(guild)
        if missing:
            embed.add_field(
                name="🔴 Не найдены роли",
                value=", ".join(f"`{m}`" for m in missing),
                inline=False,
            )
        return embed

    # ------------------------------------------------------------------ #
    @discord.ui.button(emoji="🛰️", label="Найти автоматически", style=discord.ButtonStyle.success, custom_id="setup_auto")
    async def auto(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        channels = await self.bot.role_manager.auto_discover_channels(interaction.guild)
        found_roles, total = await self.bot.role_manager.sync_role_config(interaction.guild)
        summary = await self.bot.role_manager.sync_guild(interaction.guild)
        embed = E.success(
            "Автоматическая настройка выполнена",
            f"📺 Каналов найдено: **{len(channels)}**\n"
            f"🎭 Ролей найдено: **{found_roles}/{total}**\n"
            f"👥 Сотрудников в базе: **{summary['total']}**\n\n"
            + self.bot.role_manager.describe_assigned(channels),
        )
        await interaction.followup.send(embed=embed, ephemeral=True)

    @discord.ui.button(emoji="📋", label="Канал отчётов", style=discord.ButtonStyle.primary, custom_id="setup_reports")
    async def reports(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "reports_channel_id")

    @discord.ui.button(emoji="⚠️", label="Канал варнов", style=discord.ButtonStyle.primary, custom_id="setup_warns")
    async def warns(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "warns_channel_id")

    @discord.ui.button(emoji="💬", label="Стафф-чат", style=discord.ButtonStyle.primary, custom_id="setup_staffchat")
    async def staff_chat(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "staff_chat_channel_id")

    @discord.ui.button(emoji="📊", label="Канал еженед. отчёта", style=discord.ButtonStyle.primary, custom_id="setup_weekly")
    async def weekly(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "weekly_report_channel_id")

    @discord.ui.button(emoji="📢", label="Объявления", style=discord.ButtonStyle.primary, custom_id="setup_announce")
    async def announce(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "announcements_channel_id")

    @discord.ui.button(emoji="ℹ️", label="Информация", style=discord.ButtonStyle.primary, custom_id="setup_info")
    async def info_channel(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "info_channel_id")

    @discord.ui.button(emoji="🕵️", label="Анти-чит", style=discord.ButtonStyle.primary, custom_id="setup_anticheat")
    async def anticheat(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._pick_channel(interaction, "anticheat_channel_id")

    @discord.ui.button(emoji="🎭", label="Роли прав", style=discord.ButtonStyle.secondary, custom_id="setup_roles")
    async def roles(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.send_message(
            embed=E.info(
                "Настройка ролей прав доступа",
                "Выберите роли в селекторах ниже.\n"
                "👑 Главные администраторы — полный доступ.\n"
                "🛡️ Старший состав — просмотр отчётов и персонала.\n"
                "👮 Модераторы — свой профиль и отчёты.\n"
                "🎓 Стажеры — свой профиль.",
            ),
            view=RolePickView(self.bot),
            ephemeral=True,
        )

    @discord.ui.button(emoji="➕", label="Должности Д.*", style=discord.ButtonStyle.secondary, custom_id="setup_droles")
    async def d_roles(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        names = ["Д.Модер", "Д.Ст.Модер", "Д.Гл.Модер", "Д.Админ"]
        config = await self.bot.repo.settings.control_roles(interaction.guild.id)
        state = {str(r["name"]): bool(r.get("tracked", True)) for r in config}
        enabled = all(state.get(n, False) for n in names)
        changed = await self.bot.role_manager.enable_tracking(
            interaction.guild.id, names, not enabled
        )
        await self.bot.role_manager.sync_guild(interaction.guild)
        action = "выключено" if enabled else "включено"
        await interaction.response.send_message(
            embed=E.success(
                f"Отслеживание Д.* ролей {action}",
                f"Затронуты роли: {', '.join(f'`{c}`' for c in changed) or '—'}",
            ),
            ephemeral=True,
        )

    @discord.ui.button(emoji="🔄", label="Обновить меню", style=discord.ButtonStyle.blurple, custom_id="setup_refresh")
    async def refresh(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.edit_message(embed=await self._status(interaction.guild))

    # ------------------------------------------------------------------ #
    async def _pick_channel(self, interaction: discord.Interaction, slot: str) -> None:
        meta = CHANNEL_SLOTS[slot]
        await interaction.response.send_message(
            embed=E.info(
                f"Выбор канала: {meta['title']}",
                f"Каноническое имя на сервере: `{meta['canonical']}`\n"
                "Выберите канал в селекторе ниже.",
            ),
            view=ChannelPickView(self.bot, slot),
            ephemeral=True,
        )


def bot_name() -> str:
    from config import BOT_NAME

    return BOT_NAME


# --------------------------------------------------------------------------- #
class SettingToggleView(discord.ui.View):
    """Переключение булевых настроек."""

    def __init__(self, bot: "KingGriefBot", key: str) -> None:
        super().__init__(timeout=300)
        self.bot = bot
        self.key = key
        self.schema = SETTING_SCHEMA[key]

    @discord.ui.button(emoji="✅", label="Включить", style=discord.ButtonStyle.success, custom_id="set_on")
    async def on(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._set(interaction, True)

    @discord.ui.button(emoji="❌", label="Выключить", style=discord.ButtonStyle.danger, custom_id="set_off")
    async def off(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._set(interaction, False)

    async def _set(self, interaction: discord.Interaction, value: bool) -> None:
        await self.bot.repo.settings.set(interaction.guild.id, self.key, value)
        await self.bot.repo.audit.add(
            interaction.guild.id,
            "settings",
            actor_id=interaction.user.id,
            detail=f"{self.schema['label']}: {'включено' if value else 'выключено'}",
        )
        if self.key.startswith("weekly_"):
            await self.bot.scheduler.schedule_weekly(interaction.guild.id)
        await interaction.response.edit_message(
            embed=E.success(
                "Настройка обновлена",
                f"{self.schema['emoji']} {self.schema['label']}: "
                f"**{'включено' if value else 'выключено'}**",
            ),
            view=None,
        )


class SettingsCog(commands.Cog, name="Настройки"):
    """Первоначальная настройка, параметры бота и синхронизация."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo

    # ------------------------------------------------------------------ #
    @app_commands.command(name="setup", description="⚙️ Первоначальная настройка бота")
    @require("setup")
    async def setup_cmd(self, interaction: discord.Interaction) -> None:
        view = SetupView(self.bot)
        await interaction.response.send_message(
            embed=await view._status(interaction.guild), view=view
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="settings", description="⚙️ Настройки бота")
    @app_commands.describe(
        параметр="Какой параметр изменить",
        значение="Новое значение (число, текст, true/false)",
    )
    @app_commands.choices(
        параметр=[
            app_commands.Choice(name=f"{meta['emoji']} {meta['label']}", value=key)
            for key, meta in SETTING_SCHEMA.items()
            if meta["type"] != "channel"
        ][:25]
    )
    @require("settings")
    async def settings(
        self,
        interaction: discord.Interaction,
        параметр: app_commands.Choice[str] | None = None,
        значение: str | None = None,
    ) -> None:
        if параметр is None:
            await interaction.response.send_message(
                embed=await _settings_embed(self.bot, interaction.guild), ephemeral=True
            )
            return

        key = параметр.value
        schema = SETTING_SCHEMA[key]

        if значение is None:
            current = await self.repo.settings.get(interaction.guild.id, key)
            await interaction.response.send_message(
                embed=E.info(
                    f"{schema['emoji']} {schema['label']}",
                    f"Текущее значение: `{current}`\n"
                    f"Тип: **{schema['type']}**\n"
                    + (
                        f"Допустимые значения: {', '.join(f'`{c}`' for c in schema['choices'])}"
                        if schema["type"] == "choice"
                        else (
                            f"Диапазон: {schema.get('min')}…{schema.get('max')}"
                            if schema["type"] == "int"
                            else "true / false"
                        )
                    ),
                ),
                ephemeral=True,
            )
            return

        value: Any
        if schema["type"] == "bool":
            value = str(значение).strip().lower() in ("1", "true", "да", "on", "вкл", "yes")
        elif schema["type"] == "int":
            try:
                value = int(str(значение).strip())
            except ValueError:
                await interaction.response.send_message(
                    embed=E.error("Неверное значение", "Ожидалось целое число."),
                    ephemeral=True,
                )
                return
            if value < schema.get("min", -(10**9)) or value > schema.get("max", 10**9):
                await interaction.response.send_message(
                    embed=E.error(
                        "Значение вне диапазона",
                        f"Допустимо: {schema.get('min')}…{schema.get('max')}.",
                    ),
                    ephemeral=True,
                )
                return
        elif schema["type"] == "choice":
            value = str(значение).strip().lower()
            if value not in schema["choices"]:
                await interaction.response.send_message(
                    embed=E.error(
                        "Недопустимое значение",
                        "Доступно: " + ", ".join(f"`{c}`" for c in schema["choices"]),
                    ),
                    ephemeral=True,
                )
                return
        else:
            value = str(значение)

        await self.repo.settings.set(interaction.guild.id, key, value)
        await self.repo.audit.add(
            interaction.guild.id,
            "settings",
            actor_id=interaction.user.id,
            detail=f"{schema['label']} = {value}",
        )

        if key.startswith("weekly_") or key == "timezone":
            await self.bot.scheduler.schedule_weekly(interaction.guild.id)

        embed = E.success(
            "Настройка обновлена",
            f"{schema['emoji']} {schema['label']}: **{value}**",
        )
        if key.startswith("weekly_"):
            info = await self.bot.scheduler.weekly_info(interaction.guild.id)
            embed.add_field(name="⏰ Расписание", value=info["description"], inline=False)
        await interaction.response.send_message(embed=embed, ephemeral=True)

    # ------------------------------------------------------------------ #
    @app_commands.command(name="sync", description="🔄 Обновить роли, каналы и персонал")
    @app_commands.describe(
        история="Просканировать историю канала отчётов", сообщений="Сколько сообщений истории читать"
    )
    @require("sync")
    async def sync(
        self,
        interaction: discord.Interaction,
        история: bool | None = None,
        сообщений: int | None = None,
    ) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        guild = interaction.guild

        channels = await self.bot.role_manager.auto_discover_channels(guild)
        found_roles, total_roles = await self.bot.role_manager.sync_role_config(guild)
        await self.bot.role_manager.sync_permission_roles(guild)
        summary = await self.bot.role_manager.sync_guild(guild)

        rescanned = 0
        if история:
            channel_id = int(await self.repo.settings.get(guild.id, "reports_channel_id", 0) or 0)
            channel = guild.get_channel(channel_id) if channel_id else None
            if isinstance(channel, discord.TextChannel):
                try:
                    rescanned = await self.bot.report_tracker.rescan_channel(
                        channel, guild_id=guild.id, limit=max(1, min(1000, сообщений or 200))
                    )
                except discord.Forbidden:
                    log.warning("Нет прав на чтение истории канала отчётов.")

        await self.bot.scheduler.setup(guild.id)
        await self.bot.sync_commands()

        embed = E.success(
            "Синхронизация завершена",
            f"📺 Каналов найдено: **{len(channels)}**\n"
            f"🎭 Ролей найдено: **{found_roles}/{total_roles}**\n"
            f"👥 Сотрудников: **{summary['total']}** "
            f"(новых {summary['added']}, обновлено {summary['updated']}, снято {summary['removed']})\n"
            f"📋 Отчётов добавлено из истории: **{rescanned}**\n"
            f"🧩 Slash-команд обновлено",
        )
        missing = await self.bot.role_manager.missing_roles(guild)
        if missing:
            embed.add_field(
                name="🔴 Не найдены роли",
                value=", ".join(f"`{m}`" for m in missing),
                inline=False,
            )
        await interaction.followup.send(embed=embed, ephemeral=True)

    # ------------------------------------------------------------------ #
    @app_commands.command(name="permissions", description="🔐 Ваши права и уровни доступа")
    @require("help")
    async def perms(self, interaction: discord.Interaction) -> None:
        text = await permissions.describe(interaction.guild, interaction.user)
        await interaction.response.send_message(
            embed=E.info("🔐 СИСТЕМА ПРАВ", text), ephemeral=True
        )


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover
    await bot.add_cog(SettingsCog(bot))
