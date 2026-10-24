"""
Статистика, рейтинг, панель управления и справка:
/stats · /top · /panel · /help
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

from bot.permissions import COMMAND_LEVELS, Level, permissions, require
from config import BOT_DESCRIPTION, BOT_NAME, BOT_VERSION
from utils import embeds as E
from utils.logging import get_logger
from utils.periods import describe_schedule, now

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("cmd.statistics")

HELP_GROUPS: dict[str, list[tuple[str, str]]] = {
    "📋 Отчёты": [
        ("ot", "кто сдал отчёты, а кто нет (фильтры: период, пользователь, роль)"),
        ("report", "карточка конкретного отчёта с кнопками принятия"),
        ("reports", "история отчётов сотрудника"),
    ],
    "⚠️ Предупреждения": [
        ("warn", "установить количество предупреждений (1-3)"),
        ("addwarn", "добавить одно предупреждение"),
        ("unwarn", "снять одно предупреждение"),
        ("warns", "все предупреждения сотрудника"),
        ("clearwarns", "сбросить предупреждения до нуля"),
    ],
    "👥 Персонал": [
        ("staff", "список персонала сервера"),
        ("profile", "профиль сотрудника"),
        ("check", "быстрая проверка сотрудника"),
        ("history", "история наказаний и событий"),
    ],
    "📊 Статистика": [
        ("stats", "общая статистика по серверу"),
        ("top", "рейтинг сотрудников по отчётам"),
        ("panel", "интерактивная панель управления"),
    ],
    "⚙️ Настройка": [
        ("setup", "первоначальная настройка каналов и ролей"),
        ("settings", "изменение настроек бота"),
        ("sync", "обновить роли, каналы и состав персонала"),
        ("help", "эта справка"),
    ],
}


# --------------------------------------------------------------------------- #
class PanelView(discord.ui.View):
    """Главная интерактивная панель управления."""

    def __init__(self, bot: "KingGriefBot", guild: discord.Guild) -> None:
        super().__init__(timeout=900)
        self.bot = bot
        self.guild = guild

    # ------------------------------------------------------------------ #
    async def _panel_embed(self) -> discord.Embed:
        repo = self.bot.repo
        options = await repo.settings.get_many(self.guild.id)
        ot = await self.bot.report_tracker.build_ot_data(
            self.guild.id, period="week", options=options
        )
        warns = await repo.warnings.total_active(self.guild.id)
        punishments = await repo.punishments.count_recent(self.guild.id, 30)
        info = await self.bot.scheduler.schedule_weekly_info(self.guild.id)
        return E.panel_embed(
            {
                "staff_total": ot["total"],
                "week_reports": sum(r["count"] for r in ot["submitted"]),
                "submitted": len(ot["submitted"]),
                "missed": len(ot["missed"]),
                "active_warns": warns,
                "punishments": punishments,
                "weekly_next": info["description"],
                "guild": self.guild.name,
            }
        )

    async def _open(
        self, interaction: discord.Interaction, embed: discord.Embed, view: discord.ui.View | None
    ) -> None:
        if interaction.response.is_done():
            await interaction.followup.send(embed=embed, view=view, ephemeral=True)
        else:
            await interaction.response.edit_message(embed=embed, view=view)

    # ------------------------------------------------------------------ #
    @discord.ui.button(emoji="📊", label="Отчёты", style=discord.ButtonStyle.primary, custom_id="panel_reports")
    async def reports(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        options = await self.bot.repo.settings.get_many(self.guild.id)
        data = await self.bot.report_tracker.build_ot_data(
            self.guild.id, period="week", options=options
        )
        embed = E.ot_embed(
            period="week",
            submitted=data["submitted"][:4],
            missed=data["missed"][:4],
            total=data["total"],
            page=1,
            pages=1,
        )
        await self._open(interaction, embed, SubPanelView(self.bot, self.guild, "reports"))

    @discord.ui.button(emoji="👥", label="Персонал", style=discord.ButtonStyle.secondary, custom_id="panel_staff")
    async def staff(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        roster = await self.bot.role_manager.staff_roster(self.guild)
        breakdown = await self.bot.repo.staff.positions_breakdown(self.guild.id)
        embed = E.info(
            f"{BOT_NAME} | ПЕРСОНАЛ",
            f"👥 Всего сотрудников: **{len(roster)}**",
        )
        embed.add_field(
            name="🎖️ По должностям",
            value="\n".join(f"• {k}: **{v}**" for k, v in breakdown.items()) or "—",
            inline=False,
        )
        embed.add_field(
            name="👥 Список",
            value="\n".join(
                f"🟢 **{r['username']}** — {r['position']} · 📋 {r['week_reports']} · ⚠️ {r['warns']}"
                for r in roster[:15]
            )[:1024]
            or "—",
            inline=False,
        )
        await self._open(interaction, embed, SubPanelView(self.bot, self.guild, "staff"))

    @discord.ui.button(emoji="⚠️", label="Предупреждения", style=discord.ButtonStyle.danger, custom_id="panel_warns")
    async def warns(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        summary = await self.bot.warning_manager.summary(self.guild.id)
        limit = int(await self.bot.repo.settings.get(self.guild.id, "max_warnings", 3))
        embed = E.warning(
            f"{BOT_NAME} | ПРЕДУПРЕЖДЕНИЯ",
            f"⚠️ Активных предупреждений: **{summary['active']}**\n"
            f"🔺 Максимум до снятия: **{limit}**",
        )
        if summary["top"]:
            lines = [
                f"• <@{row['user_id']}> — **{row['total']}** варн(а)" for row in summary["top"]
            ]
            embed.add_field(name="🔻 Чаще всего наказываются", value="\n".join(lines), inline=False)
        else:
            embed.add_field(name="✅", value="Активных предупреждений нет.", inline=False)
        await self._open(interaction, embed, SubPanelView(self.bot, self.guild, "warns"))

    @discord.ui.button(emoji="🏆", label="Рейтинг", style=discord.ButtonStyle.success, custom_id="panel_top")
    async def top(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._top(interaction, "month")

    @discord.ui.button(emoji="📈", label="Статистика", style=discord.ButtonStyle.blurple, custom_id="panel_stats")
    async def stats(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        data = await _collect_stats(self.bot, self.guild)
        await self._open(interaction, E.general_stats_embed(data), SubPanelView(self.bot, self.guild, "stats"))

    @discord.ui.button(emoji="⚙️", label="Настройки", style=discord.ButtonStyle.secondary, custom_id="panel_settings")
    async def settings(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        level = await permissions.level(self.guild, interaction.user)
        if level < Level.SENIOR:
            await interaction.response.send_message(
                embed=E.error("Недостаточно прав", "Настройки доступны администрации."),
                ephemeral=True,
            )
            return
        embed = await _settings_embed(self.bot, self.guild)
        await self._open(interaction, embed, SubPanelView(self.bot, self.guild, "settings"))

    # ------------------------------------------------------------------ #
    async def _top(self, interaction: discord.Interaction, period: str) -> None:
        options = await self.bot.repo.settings.get_many(self.guild.id)
        top = await self.bot.repo.reports.top(
            self.guild.id,
            period=period,
            limit=10,
            tz=str(options.get("timezone")),
            week_start_day=str(options.get("week_start_day", "mon")),
        )
        for row in top:
            row["warns"] = await self.bot.repo.warnings.count(self.guild.id, row["user_id"])
        labels = {"week": "📅 Период: текущая неделя", "month": "📅 Период: текущий месяц", "all": "📅 Всё время"}
        embed = E.top_embed(top, labels.get(period, period))
        await self._open(interaction, embed, SubPanelView(self.bot, self.guild, "top"))


class SubPanelView(discord.ui.View):
    """Возврат из подраздела панели."""

    def __init__(self, bot: "KingGriefBot", guild: discord.Guild, section: str) -> None:
        super().__init__(timeout=900)
        self.bot = bot
        self.guild = guild
        self.section = section

    @discord.ui.button(emoji="◀️", label="Назад в панель", style=discord.ButtonStyle.blurple, custom_id="panel_back")
    async def back(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        panel = PanelView(self.bot, self.guild)
        await interaction.response.edit_message(embed=await panel._panel_embed(), view=panel)


async def _collect_stats(bot: "KingGriefBot", guild: discord.Guild) -> dict[str, Any]:
    """Собирает данные для /stats и панели."""
    repo = bot.repo
    options = await repo.settings.get_many(guild.id)
    totals = await repo.reports.totals(guild.id)
    week_counts = await repo.reports.count(
        guild.id,
        period="week",
        week_start_day=str(options.get("week_start_day", "mon")),
        tz=str(options.get("timezone")),
    )
    today_counts = await repo.reports.count(
        guild.id,
        period="today",
        week_start_day=str(options.get("week_start_day", "mon")),
        tz=str(options.get("timezone")),
    )
    ot = await bot.report_tracker.build_ot_data(guild.id, period="week", options=options)
    top = await repo.reports.top(guild.id, period="month", limit=3, tz=str(options.get("timezone")))
    return {
        "staff_total": await repo.staff.count(guild.id, active_only=False),
        "staff_active": await repo.staff.count(guild.id, active_only=True),
        "staff_left": await repo.staff.count(guild.id, active_only=False)
        - await repo.staff.count(guild.id, active_only=True),
        "reports_total": totals["total"],
        "reports_week": week_counts,
        "reports_today": today_counts,
        "accepted": totals["accepted"],
        "pending": totals["pending"],
        "rejected": totals["rejected"],
        "active_warns": await repo.warnings.total_active(guild.id),
        "punishments": await repo.punishments.count_recent(guild.id, 30),
        "completion": ot["completion"],
        "by_position": await repo.staff.positions_breakdown(guild.id),
        "top": top,
    }


async def _settings_embed(bot: "KingGriefBot", guild: discord.Guild) -> discord.Embed:
    """Embed с текущими настройками (используется панелью и /settings)."""
    from config import CHANNEL_SLOTS

    options = await bot.repo.settings.get_many(guild.id)
    embed = E.info(f"{BOT_NAME} | НАСТРОЙКИ", f"Сервер: **{guild.name}**")

    lines = []
    for slot, meta in CHANNEL_SLOTS.items():
        value = int(options.get(slot) or 0)
        channel = guild.get_channel(value) if value else None
        lines.append(
            f"{meta['emoji']} {meta['title']}: "
            + (channel.mention if channel else "`не настроен`")
        )
    embed.add_field(name="📺 Каналы", value="\n".join(lines)[:1024], inline=False)

    role_lines = []
    for role in await bot.repo.settings.control_roles(guild.id):
        mark = "✅" if role.get("tracked", True) else "⛔"
        found = "🟢" if role.get("role_id") else "🔴"
        role_lines.append(f"{mark}{found} `{role['name']}`")
    embed.add_field(
        name="🎭 Контролируемые роли",
        value="\n".join(role_lines)[:1024] or "—",
        inline=False,
    )

    embed.add_field(
        name="⚠️ Наказания",
        value=(
            f"Варнов до снятия: **{options.get('max_warnings')}**\n"
            f"Снимать роль автоматически: **{'да' if options.get('auto_demote_on_max_warnings') else 'нет'}**\n"
            f"Требовать подтверждение: **{'да' if options.get('require_confirm_demote') else 'нет'}**"
        ),
        inline=False,
    )
    embed.add_field(
        name="🔎 Определение отчёта",
        value=bot.report_tracker.describe_options(options),
        inline=False,
    )
    embed.add_field(
        name="⏰ Еженедельная проверка",
        value=describe_schedule(
            str(options.get("weekly_check_day")),
            int(options.get("weekly_check_hour", 20)),
            int(options.get("weekly_check_minute", 0)),
            str(options.get("timezone")),
        ),
        inline=False,
    )
    return embed


# --------------------------------------------------------------------------- #
class StatisticsCog(commands.Cog, name="Статистика"):
    """Общая статистика, рейтинг, панель и справка."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo

    # ------------------------------------------------------------------ #
    @app_commands.command(name="stats", description="📊 Общая статистика КП")
    @require("stats")
    async def stats(self, interaction: discord.Interaction) -> None:
        await interaction.response.defer(thinking=True)
        data = await _collect_stats(self.bot, interaction.guild)
        await interaction.followup.send(embed=E.general_stats_embed(data))

    # ------------------------------------------------------------------ #
    @app_commands.command(name="top", description="🏆 Рейтинг сотрудников по отчётам")
    @app_commands.describe(период="Период рейтинга", количество="Сколько мест показать")
    @app_commands.choices(
        период=[
            app_commands.Choice(name="📅 Неделя", value="week"),
            app_commands.Choice(name="🗓️ Месяц", value="month"),
            app_commands.Choice(name="♾️ Всё время", value="all"),
        ]
    )
    @require("top")
    async def top(
        self,
        interaction: discord.Interaction,
        период: app_commands.Choice[str] | None = None,
        количество: int | None = None,
    ) -> None:
        await interaction.response.defer(thinking=True)
        period = период.value if период else "month"
        limit = max(1, min(25, количество or 10))
        options = await self.repo.settings.get_many(interaction.guild.id)
        rows = await self.repo.reports.top(
            interaction.guild.id,
            period=period,
            limit=limit,
            tz=str(options.get("timezone")),
            week_start_day=str(options.get("week_start_day", "mon")),
        )
        for row in rows:
            row["warns"] = await self.repo.warnings.count(interaction.guild.id, row["user_id"])
        labels = {
            "week": "📅 Период: текущая неделя",
            "month": "📅 Период: текущий месяц",
            "all": "📅 Период: всё время",
        }
        await interaction.followup.send(embed=E.top_embed(rows, labels.get(period, period)))

    # ------------------------------------------------------------------ #
    @app_commands.command(name="panel", description="🎛️ Панель управления персоналом")
    @require("panel")
    async def panel(self, interaction: discord.Interaction) -> None:
        view = PanelView(self.bot, interaction.guild)
        await interaction.response.send_message(embed=await view._panel_embed(), view=view)
        await self.repo.audit.add(
            interaction.guild.id, "settings", actor_id=interaction.user.id, detail="/panel открыт"
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="help", description="🛡️ Помощь по командам бота")
    @app_commands.describe(команда="Показать доступный уровень конкретной команды")
    @require("help")
    async def help(self, interaction: discord.Interaction, команда: str | None = None) -> None:
        if команда:
            name = команда.strip().lstrip("/").lower()
            if name not in COMMAND_LEVELS:
                await interaction.response.send_message(
                    embed=E.error("Команда не найдена", f"Команды `/{name}` не существует."),
                    ephemeral=True,
                )
                return
            need = COMMAND_LEVELS[name]
            level = await permissions.level(interaction.guild, interaction.user)
            embed = E.info(
                f"Команда /{name}",
                f"🔐 Требуемый уровень: **{need.label}**\n"
                f"👤 Ваш уровень: **{level.label}**\n"
                f"{'✅ Доступно' if level >= need else '❌ Недостаточно прав'}",
            )
            await interaction.response.send_message(embed=embed, ephemeral=True)
            return

        embed = E.help_embed(HELP_GROUPS)
        embed.add_field(
            name="ℹ️ О боте",
            value=f"{BOT_DESCRIPTION}\nВерсия: **{BOT_VERSION}**\n`{now().strftime('%d.%m.%Y %H:%M')}`",
            inline=False,
        )
        await interaction.response.send_message(embed=embed, ephemeral=True)


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover
    await bot.add_cog(StatisticsCog(bot))
