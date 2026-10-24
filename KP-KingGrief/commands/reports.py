"""
Команды работы с отчётами: /ot, /report, /reports.

/ot — главный инструмент контроля: кто сдал отчёты, а кто нет.
Поддерживает фильтры (период, пользователь, роль), пагинацию,
кнопки-переключатели и выгрузку в TXT.
"""

from __future__ import annotations

import io
from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

from bot.permissions import Level, require
from config import ReportStatus
from database.models import as_row
from utils import embeds as E
from utils.logging import get_logger
from utils.pagination import PaginatorView, chunk, page_count, send_paginated
from utils.periods import normalize_period

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("cmd.reports")

PAGE_SIZE = 4  # сотрудников на один Embed


class OtController:
    """Хранит состояние списка /ot и рендерит страницы."""

    def __init__(
        self,
        bot: "KingGriefBot",
        guild: discord.Guild,
        *,
        period: str = "week",
        user_id: int | None = None,
        role_filter: str | None = None,
    ) -> None:
        self.bot = bot
        self.guild = guild
        self.period = normalize_period(period)
        self.user_id = user_id
        self.role_filter = role_filter
        self.view_mode = "all"  # all | submitted | missed | stats
        self.data: dict[str, Any] = {}
        self.loaded = False

    # ------------------------------------------------------------------ #
    async def load(self) -> dict[str, Any]:
        self.data = await self.bot.report_tracker.build_ot_data(
            self.guild.id,
            period=self.period,
            role_filter=self.role_filter,
            user_id=self.user_id,
        )
        self.loaded = True
        return self.data

    def entries(self) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        submitted = self.data.get("submitted", [])
        missed = self.data.get("missed", [])
        if self.view_mode == "submitted":
            return submitted, []
        if self.view_mode == "missed":
            return [], missed
        if self.view_mode == "stats":
            return [], []
        return submitted, missed

    def total_pages(self) -> int:
        submitted, missed = self.entries()
        if self.view_mode == "stats":
            return 1
        blocks = len(submitted) + len(missed)
        return page_count(blocks, PAGE_SIZE)

    def render(self, page: int) -> discord.Embed:
        submitted, missed = self.entries()
        pages = self.total_pages()
        page = min(max(1, page), pages)

        if self.view_mode == "stats":
            embed = E.ot_embed(
                period=self.period,
                submitted=submitted,
                missed=missed,
                total=self.data.get("total", 0),
                page=1,
                pages=1,
                view="stats",
                role_filter=self.role_filter,
                user_filter=f"<@{self.user_id}>" if self.user_id else None,
            )
            return embed

        offset = (page - 1) * PAGE_SIZE
        page_submitted: list[dict[str, Any]] = []
        page_missed: list[dict[str, Any]] = []
        remaining = PAGE_SIZE

        for record in submitted[offset:]:
            if remaining <= 0:
                break
            page_submitted.append(record)
            remaining -= 1
        if remaining > 0:
            start = max(0, offset - len(submitted))
            for record in missed[start : start + remaining]:
                page_missed.append(record)

        return E.ot_embed(
            period=self.period,
            submitted=page_submitted,
            missed=page_missed,
            total=self.data.get("total", 0),
            page=page,
            pages=pages,
            view=self.view_mode,
            role_filter=self.role_filter,
            user_filter=f"<@{self.user_id}>" if self.user_id else None,
        )


class OtView(PaginatorView):
    """Панель управления списком отчётов."""

    def __init__(self, controller: OtController, **kwargs: Any) -> None:
        super().__init__(**kwargs)
        self.controller = controller
        self.render = controller.render
        self._refresh_states()

    def _refresh_states(self) -> None:
        mode = self.controller.view_mode
        self.btn_submitted.style = (
            discord.ButtonStyle.success if mode == "submitted" else discord.ButtonStyle.secondary
        )
        self.btn_missed.style = (
            discord.ButtonStyle.danger if mode == "missed" else discord.ButtonStyle.secondary
        )
        self.btn_stats.style = (
            discord.ButtonStyle.primary if mode == "stats" else discord.ButtonStyle.secondary
        )

    async def _switch(self, interaction: discord.Interaction, mode: str) -> None:
        if not await self._owner_only(interaction):
            return
        self.controller.view_mode = mode
        self.controller.total_pages()
        self.page = 1
        self.total_pages = self.controller.total_pages()
        self._refresh_states()
        self._sync()
        await interaction.response.edit_message(
            embed=self.controller.render(self.page), view=self
        )

    # ------------------------------------------------------------------ #
    @discord.ui.button(emoji="🔄", label="Обновить", style=discord.ButtonStyle.secondary, custom_id="ot_refresh")
    async def btn_refresh(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.controller.load()
        self.total_pages = self.controller.total_pages()
        self.page = min(self.page, self.total_pages)
        self._sync()
        await interaction.response.edit_message(
            embed=self.controller.render(self.page), view=self
        )

    @discord.ui.button(emoji="✅", label="Сдали", style=discord.ButtonStyle.secondary, custom_id="ot_submitted")
    async def btn_submitted(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._switch(interaction, "submitted")

    @discord.ui.button(emoji="❌", label="Не сдали", style=discord.ButtonStyle.secondary, custom_id="ot_missed")
    async def btn_missed(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._switch(interaction, "missed")

    @discord.ui.button(emoji="📊", label="Статистика", style=discord.ButtonStyle.secondary, custom_id="ot_stats")
    async def btn_stats(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._switch(interaction, "stats")

    @discord.ui.button(emoji="📥", label="Скачать TXT", style=discord.ButtonStyle.success, custom_id="ot_txt")
    async def btn_txt(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        text, filename = await self.bot.report_tracker.txt_export(
            self.guild_id, self.controller.data, self.controller.guild.name
        )
        file = discord.File(io.BytesIO(text.encode("utf-8")), filename=filename)
        await interaction.followup.send(
            content="📥 Файл с отчётами персонала:", file=file, ephemeral=True
        )

    @property
    def guild_id(self) -> int:
        return self.controller.guild.id


# --------------------------------------------------------------------------- #
class ReportDecisionView(discord.ui.View):
    """Кнопки принятия/отклонения конкретного отчёта."""

    def __init__(self, bot: "KingGriefBot", message_id: int, report_id: int) -> None:
        super().__init__(timeout=600)
        self.bot = bot
        self.message_id = message_id
        self.report_id = report_id

    async def _decide(self, interaction: discord.Interaction, status: str) -> None:
        from bot.permissions import permissions

        level = await permissions.level(interaction.guild, interaction.user)
        if level < Level.ADMIN:
            await interaction.response.send_message(
                embed=E.error("Недостаточно прав", "Принимать отчёты может только администрация."),
                ephemeral=True,
            )
            return
        await self.bot.report_tracker.decide(
            self.message_id, status, decided_by=interaction.user.id
        )
        report = await self.bot.repo.reports.get(self.report_id)
        label = "✅ Отчёт принят" if status == ReportStatus.ACCEPTED else "❌ Отчёт отклонён"
        await interaction.response.edit_message(
            embed=E.success(
                label,
                f"👤 Сотрудник: <@{report.user_id}>\n"
                f"📋 Отчёт: #{report.id}\n"
                f"⚖️ Решение принял: {interaction.user.mention}",
            ),
            view=None,
        )

    @discord.ui.button(emoji="✅", label="Принять", style=discord.ButtonStyle.success, custom_id="rep_accept")
    async def accept(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._decide(interaction, ReportStatus.ACCEPTED)

    @discord.ui.button(emoji="❌", label="Отклонить", style=discord.ButtonStyle.danger, custom_id="rep_reject")
    async def reject(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._decide(interaction, ReportStatus.REJECTED)


# --------------------------------------------------------------------------- #
PERIOD_OPTIONS = [
    app_commands.Choice(name="📅 Сегодня", value="today"),
    app_commands.Choice(name="📆 Текущая неделя", value="week"),
    app_commands.Choice(name="🗓️ Текущий месяц", value="month"),
    app_commands.Choice(name="♾️ Всё время", value="all"),
]


class ReportsCog(commands.Cog, name="Отчёты"):
    """Команды контроля отчётности персонала."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo

    @property
    def cog_permissions(self):
        from bot.permissions import permissions

        return permissions

    # ------------------------------------------------------------------ #
    @app_commands.command(name="ot", description="📊 Кто сдал отчёты, а кто нет")
    @app_commands.describe(
        период="Отчётный период",
        пользователь="Показать только конкретного сотрудника",
        роль="Фильтр по должности (например: Мл.Модер)",
    )
    @app_commands.choices(период=PERIOD_OPTIONS)
    @require("ot")
    async def ot(
        self,
        interaction: discord.Interaction,
        период: app_commands.Choice[str] | None = None,
        пользователь: discord.Member | None = None,
        роль: str | None = None,
    ) -> None:
        """Показывает сдавших и не сдавших отчёты сотрудников."""
        await interaction.response.defer(ephemeral=False, thinking=True)

        controller = OtController(
            self.bot,
            interaction.guild,
            period=(период.value if период else "week"),
            user_id=пользователь.id if пользователь else None,
            role_filter=(роль.strip() if роль else None),
        )
        await controller.load()

        if controller.data["total"] == 0:
            await interaction.followup.send(
                embed=E.error(
                    "Сотрудники не найдены",
                    "На сервере ещё нет сотрудников с контролируемыми ролями.\n"
                    "Используйте `/setup` или `/sync`, чтобы найти их автоматически.",
                ),
                ephemeral=True,
            )
            return

        view = OtView(
            controller,
            total_pages=controller.total_pages(),
            owner_id=None,
            timeout=600,
        )
        await interaction.followup.send(embed=controller.render(1), view=view)
        await self.repo.audit.add(
            interaction.guild.id,
            "report",
            actor_id=interaction.user.id,
            detail=f"/ot период={controller.period} роль={роль or '—'}",
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="report", description="📋 Посмотреть конкретный отчёт")
    @app_commands.describe(идентификатор="Номер отчёта (ID)", сотрудник="Отчёты сотрудника")
    @require("report")
    async def report(
        self,
        interaction: discord.Interaction,
        идентификатор: int | None = None,
        сотрудник: discord.Member | None = None,
    ) -> None:
        """Карточка одного отчёта с кнопками принятия/отклонения."""
        if идентификатор is None and сотрудник is None:
            await interaction.response.send_message(
                embed=E.error(
                    "Уточните запрос",
                    "Передайте номер отчёта (`идентификатор`) или сотрудника.",
                ),
                ephemeral=True,
            )
            return

        if идентификатор is not None:
            report = await self.repo.reports.get(идентификатор)
            if report is None or report.guild_id != interaction.guild.id:
                await interaction.response.send_message(
                    embed=E.error("Отчёт не найден", f"Отчёт с номером `{идентификатор}` не найден."),
                    ephemeral=True,
                )
                return
            view = ReportDecisionView(self.bot, report.message_id, report.id)
            await interaction.response.send_message(
                embed=E.report_detail_embed(as_row(report)), view=view
            )
            return

        rows = await self.bot.report_tracker.recent_reports(
            interaction.guild.id, сотрудник.id, period="all", limit=5
        )
        if not rows:
            await interaction.response.send_message(
                embed=E.info("Отчётов нет", f"У {сотрудник.mention} нет сохранённых отчётов."),
                ephemeral=True,
            )
            return
        await interaction.response.send_message(
            embed=E.reports_list_embed(
                member=сотрудник, reports=rows, page=1, pages=1, total=len(rows)
            )
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="reports", description="📜 История отчётов сотрудника")
    @app_commands.describe(
        сотрудник="Сотрудник (по умолчанию — вы)", период="Период", страница="Номер страницы"
    )
    @app_commands.choices(период=PERIOD_OPTIONS)
    @require("reports")
    async def reports(
        self,
        interaction: discord.Interaction,
        сотрудник: discord.Member | None = None,
        период: app_commands.Choice[str] | None = None,
        страница: int | None = None,
    ) -> None:
        """История отчётов с пагинацией."""
        target = сотрудник or interaction.user
        level = await self.cog_permissions.level(interaction.guild, interaction.user)
        if target.id != interaction.user.id and not self.cog_permissions.can_view_target(
            level, interaction.user.id, target.id
        ):
            await interaction.response.send_message(
                embed=E.error(
                    "Недостаточно прав",
                    "Вы можете смотреть только свои отчёты.",
                ),
                ephemeral=True,
            )
            return

        period = normalize_period(период.value if период else "all")
        all_rows = await self.bot.report_tracker.recent_reports(
            interaction.guild.id, target.id, period=period, limit=500
        )
        pages = chunk(all_rows, 5)

        def render(page: int) -> discord.Embed:
            return E.reports_list_embed(
                member=target,
                reports=pages[page - 1],
                page=page,
                pages=len(pages),
                total=len(all_rows),
            )

        await send_paginated(
            interaction,
            total_pages=len(pages),
            render=render,
            page=min(max(1, страница or 1), len(pages)),
        )


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover
    await bot.add_cog(ReportsCog(bot))
