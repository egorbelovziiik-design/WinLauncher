"""
Команды работы с персоналом:
/staff · /profile · /check · /history
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

from bot.permissions import permissions, require
from utils import embeds as E
from utils.logging import get_logger
from utils.pagination import chunk, send_paginated

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("cmd.staff")

STAFF_PAGE = 12
HISTORY_PAGE = 8


class ProfileView(discord.ui.View):
    """Кнопки профиля сотрудника."""

    def __init__(self, bot: "KingGriefBot", user_id: int) -> None:
        super().__init__(timeout=300)
        self.bot = bot
        self.user_id = user_id

    async def _member(self, interaction: discord.Interaction) -> discord.Member | None:
        member = interaction.guild.get_member(self.user_id)
        if member is None:
            try:
                member = await interaction.guild.fetch_member(self.user_id)
            except discord.HTTPException:
                member = None
        return member

    async def _refresh(self, interaction: discord.Interaction) -> None:
        data = await self.bot.report_tracker.profile_data(interaction.guild.id, self.user_id)
        if data is None:
            await interaction.response.edit_message(
                embed=E.error(
                    "Сотрудник не найден",
                    "❌ Пользователь не является сотрудником КП.",
                ),
                view=None,
            )
            return
        member = await self._member(interaction)
        if member is not None:
            data["member"] = member
        else:
            data["member"] = _UserStub(self.user_id, data["staff"].username)
        await interaction.response.edit_message(embed=E.profile_embed(data), view=self)

    @discord.ui.button(emoji="📋", label="Отчёты", style=discord.ButtonStyle.primary, custom_id="prof_reports")
    async def reports(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        rows = await self.bot.report_tracker.recent_reports(
            interaction.guild.id, self.user_id, period="all", limit=500
        )
        pages = chunk(rows, 5)
        member = await self._member(interaction)
        if not interaction.response.is_done():
            await interaction.response.defer()

        def render(page: int) -> discord.Embed:
            return E.reports_list_embed(
                member=member,
                reports=pages[page - 1],
                page=page,
                pages=len(pages),
                total=len(rows),
            )

        if pages and pages[0]:
            await send_paginated(interaction, total_pages=len(pages), render=render)
            return
        await interaction.followup.send(
            embed=E.info("Отчётов нет", "У сотрудника пока нет сохранённых отчётов."),
            ephemeral=True,
        )

    @discord.ui.button(emoji="⚠️", label="Варны", style=discord.ButtonStyle.secondary, custom_id="prof_warns")
    async def warns(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        member = await self._member(interaction)
        if member is None:
            await interaction.response.send_message(
                embed=E.error("Сотрудник не найден", "Пользователь покинул сервер."),
                ephemeral=True,
            )
            return
        embed = await self.bot.warning_manager.warn_card(interaction.guild, member)
        await interaction.response.send_message(embed=embed, ephemeral=True)

    @discord.ui.button(emoji="📜", label="История", style=discord.ButtonStyle.secondary, custom_id="prof_history")
    async def history(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        entries, total = await self.bot.repo.punishments.list(
            interaction.guild.id, user_id=self.user_id, limit=HISTORY_PAGE
        )
        await interaction.response.send_message(
            embed=E.history_embed(
                [
                    {
                        "user_id": e.user_id,
                        "action": e.action,
                        "detail": e.detail,
                        "created_at": e.created_at,
                    }
                    for e in entries
                ],
                1,
                1,
                total,
            ),
            ephemeral=True,
        )

    @discord.ui.button(emoji="🔄", label="Обновить", style=discord.ButtonStyle.success, custom_id="prof_refresh")
    async def refresh(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._refresh(interaction)


class _UserStub:
    """Заглушка пользователя, если он покинул сервер (для Embed'ов)."""

    def __init__(self, user_id: int, name: str) -> None:
        self.id = user_id
        self.name = name
        self.mention = f"<@{user_id}>"
        self.display_avatar = None

    def __str__(self) -> str:
        return self.name


class StaffCog(commands.Cog, name="Персонал"):
    """Список персонала, профили, проверки и история."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo

    # ------------------------------------------------------------------ #
    @app_commands.command(name="staff", description="👥 Список персонала сервера")
    @app_commands.describe(должность="Фильтр по должности", страница="Номер страницы")
    @require("staff")
    async def staff(
        self,
        interaction: discord.Interaction,
        должность: str | None = None,
        страница: int | None = None,
    ) -> None:
        await interaction.response.defer(thinking=True)
        roster = await self.bot.role_manager.staff_roster(interaction.guild)
        if должность:
            wanted = должность.strip().casefold()
            roster = [r for r in roster if r["position"].casefold() == wanted]
        pages = chunk(roster, STAFF_PAGE)

        def render(page: int) -> discord.Embed:
            return E.staff_list_embed(pages[page - 1], page, len(pages), len(roster))

        await send_paginated(
            interaction,
            total_pages=len(pages),
            render=render,
            page=min(max(1, страница or 1), len(pages)),
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="profile", description="🛡️ Профиль сотрудника")
    @app_commands.describe(сотрудник="Сотрудник (по умолчанию — вы)")
    @require("profile")
    async def profile(
        self, interaction: discord.Interaction, сотрудник: discord.Member | None = None
    ) -> None:
        target = сотрудник or interaction.user
        level = await permissions.level(interaction.guild, interaction.user)
        if not permissions.can_view_target(level, interaction.user.id, target.id):
            await interaction.response.send_message(
                embed=E.error(
                    "Недостаточно прав", "Вы можете смотреть только свой профиль."
                ),
                ephemeral=True,
            )
            return

        data = await self.bot.report_tracker.profile_data(interaction.guild.id, target.id)
        if data is None:
            await interaction.response.send_message(
                embed=E.error(
                    "Сотрудник не найден", "❌ Пользователь не является сотрудником КП."
                ),
                ephemeral=True,
            )
            return
        data["member"] = target
        await interaction.response.send_message(
            embed=E.profile_embed(data), view=ProfileView(self.bot, target.id)
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="check", description="🔎 Быстрая проверка сотрудника")
    @app_commands.describe(сотрудник="Сотрудник для проверки")
    @require("check")
    async def check(
        self, interaction: discord.Interaction, сотрудник: discord.Member
    ) -> None:
        guild = interaction.guild
        options = await self.repo.settings.get_many(guild.id)
        staff = await self.repo.staff.get(guild.id, сотрудник.id)
        roles = await self.bot.role_manager.member_roles(guild, сотрудник)
        counts = await self.repo.reports.count_by_status(
            guild.id, user_id=сотрудник.id, period="week"
        )
        week_reports = sum(counts.values())
        total_reports = await self.repo.reports.count(
            guild.id, user_id=сотрудник.id, period="all"
        )
        warns = await self.repo.warnings.count(guild.id, сотрудник.id)
        limit = int(options.get("max_warnings", 3))

        issues: list[str] = []
        if staff is None and not roles:
            issues.append("Пользователь не состоит в базе персонала и не имеет контролируемой роли.")
        if roles and staff is None:
            issues.append("Есть контролируемая роль, но нет записи в базе — выполните /sync.")
        if roles and week_reports == 0:
            issues.append("Нет отчётов за текущую неделю.")
        if warns >= limit - 1 and warns < limit:
            issues.append("Осталось одно предупреждение до снятия с должности.")
        if warns >= limit:
            issues.append("Достигнут максимум предупреждений — требуется снятие с должности.")
        missing = await self.bot.role_manager.missing_roles(guild)
        if missing:
            issues.append(f"Не найдены роли на сервере: {', '.join(missing[:5])}")

        if not issues:
            verdict = "✅ Сотрудник в порядке: отчёты сдаются, предупреждений нет."
        elif week_reports > 0 and warns == 0:
            verdict = "🟢 Сотрудник активен, есть замечания."
        else:
            verdict = "🟡 Требуется внимание администрации."

        data = {
            "member": сотрудник,
            "position": (staff.position if staff else "") or (roles[0]["name"] if roles else ""),
            "roles": [str(r["name"]) for r in roles],
            "week_reports": week_reports,
            "total_reports": total_reports,
            "warns": warns,
            "max_warns": limit,
            "verdict": verdict,
            "issues": issues,
        }
        await interaction.response.send_message(embed=E.check_embed(data))

    # ------------------------------------------------------------------ #
    @app_commands.command(name="history", description="📜 История наказаний и событий")
    @app_commands.describe(
        сотрудник="Фильтр по сотруднику", страница="Номер страницы", дней="За сколько дней"
    )
    @require("history")
    async def history(
        self,
        interaction: discord.Interaction,
        сотрудник: discord.Member | None = None,
        страница: int | None = None,
        дней: int | None = None,
    ) -> None:
        await interaction.response.defer(thinking=True)
        rows: list[dict[str, Any]] = []
        punishments, total = await self.repo.punishments.list(
            interaction.guild.id,
            user_id=сотрудник.id if сотрудник else None,
            limit=500,
            since_days=дней,
        )
        for item in punishments:
            rows.append(
                {
                    "id": item.id,
                    "user_id": item.user_id,
                    "action": item.action,
                    "detail": item.detail,
                    "created_at": item.created_at,
                }
            )
        audit_rows, audit_total = await self.repo.audit.list(
            interaction.guild.id, target_id=сотрудник.id if сотрудник else None, limit=500
        )
        for item in audit_rows:
            rows.append(
                {
                    "id": item["id"],
                    "user_id": item["target_id"],
                    "action": item["action"],
                    "detail": item["detail"],
                    "created_at": item["created_at"],
                }
            )
        rows.sort(key=lambda r: r["created_at"], reverse=True)
        total = len(rows) or (total + audit_total)
        pages = chunk(rows, HISTORY_PAGE)

        def render(page: int) -> discord.Embed:
            return E.history_embed(pages[page - 1], page, len(pages), total)

        await send_paginated(
            interaction,
            total_pages=len(pages),
            render=render,
            page=min(max(1, страница or 1), len(pages)),
        )


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover
    await bot.add_cog(StaffCog(bot))
