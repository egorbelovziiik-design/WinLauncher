"""
Команды системы предупреждений:
/warn · /addwarn · /unwarn · /warns · /clearwarns

После третьего варна создаётся запрос на снятие с должности. Бот снимает
ТОЛЬКО контролируемую роль, и только после подтверждения администратора.
"""

from __future__ import annotations

from typing import TYPE_CHECKING, Any

import discord
from discord import app_commands
from discord.ext import commands

from bot.permissions import Level, require
from utils import embeds as E
from utils.logging import get_logger

if TYPE_CHECKING:  # pragma: no cover
    from bot.client import KingGriefBot

log = get_logger("cmd.warnings")


class RolePicker(discord.ui.Select):
    """Выбор конкретной контролируемой роли для снятия."""

    def __init__(self, roles: list[dict[str, Any]]) -> None:
        options = [
            discord.SelectOption(
                label=str(role["name"])[:100],
                value=str(role["role_id"]),
                description="Контролируемая роль сотрудника",
                emoji="🎭",
            )
            for role in roles[:25]
        ]
        super().__init__(
            placeholder="Выберите роль для снятия…",
            options=options,
            min_values=1,
            max_values=1,
            custom_id="demote_role_select",
        )
        self.selected_role_id: int | None = int(options[0].value) if options else None

    async def callback(self, interaction: discord.Interaction) -> None:
        self.selected_role_id = int(self.values[0])
        view: DemoteConfirmView | None = self.view  # type: ignore[assignment]
        if view is not None:
            view.selected_role_id = self.selected_role_id
        await interaction.response.edit_message(
            content=f"🎭 Выбрана роль: <@&{self.selected_role_id}>",
        )


class DemoteConfirmView(discord.ui.View):
    """Подтверждение снятия сотрудника с должности."""

    def __init__(
        self,
        bot: "KingGriefBot",
        *,
        member_id: int,
        roles: list[dict[str, Any]],
        pending_id: int | None,
        reason: str,
        actor_id: int,
    ) -> None:
        super().__init__(timeout=900)
        self.bot = bot
        self.member_id = member_id
        self.roles = roles
        self.pending_id = pending_id
        self.reason = reason
        self.actor_id = actor_id
        self.selected_role_id: int | None = (
            int(roles[0]["role_id"]) if len(roles) == 1 else None
        )
        self.picker: RolePicker | None = None
        if len(roles) > 1:
            self.picker = RolePicker(roles)
            self.selected_role_id = self.picker.selected_role_id
            self.add_item(self.picker)
        self.confirm.disabled = len(roles) > 1 and self.selected_role_id is None

    # ------------------------------------------------------------------ #
    async def _authorized(self, interaction: discord.Interaction) -> bool:
        from bot.permissions import permissions

        level = await permissions.level(interaction.guild, interaction.user)
        if level < Level.ADMIN:
            await interaction.response.send_message(
                embed=E.error(
                    "Недостаточно прав",
                    "Подтверждать снятие с должности может только главный администратор.",
                ),
                ephemeral=True,
            )
            return False
        return True

    async def _resolve_member(self, interaction: discord.Interaction) -> discord.Member | None:
        member = interaction.guild.get_member(self.member_id)
        if member is None:
            try:
                member = await interaction.guild.fetch_member(self.member_id)
            except discord.HTTPException:
                member = None
        return member

    # ------------------------------------------------------------------ #
    @discord.ui.button(
        emoji="✅", label="Подтвердить снятие", style=discord.ButtonStyle.danger, custom_id="demote_yes"
    )
    async def confirm(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        if not await self._authorized(interaction):
            return
        member = await self._resolve_member(interaction)
        if member is None:
            await interaction.response.edit_message(
                embed=E.error("Сотрудник не найден", "Пользователь покинул сервер."), view=None
            )
            return

        role_id = self.selected_role_id
        role = discord.utils.get(member.roles, id=role_id) if role_id else None
        if role is None:
            await interaction.response.edit_message(
                embed=E.error(
                    "Роль не найдена",
                    "Выбранная роль уже снята или не существует на сервере.",
                ),
                view=None,
            )
            return

        outcome = await self.bot.warning_manager.confirm_demotion(
            interaction.guild,
            member,
            role,
            interaction.user,
            reason=self.reason,
            pending_id=self.pending_id,
        )
        if outcome.kind == "error":
            await interaction.response.edit_message(
                embed=E.error("Бот не может снять роль", outcome.message), view=None
            )
            return

        await interaction.response.edit_message(embed=outcome.embed, view=None)
        self.stop()

    @discord.ui.button(
        emoji="❌", label="Отменить", style=discord.ButtonStyle.secondary, custom_id="demote_no"
    )
    async def cancel(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        if not await self._authorized(interaction):
            return
        member = await self._resolve_member(interaction)
        if member is not None:
            await self.bot.warning_manager.cancel_demotion(
                interaction.guild, member, interaction.user
            )
        await interaction.response.edit_message(
            embed=E.info(
                "Снятие с должности отменено",
                f"👤 Сотрудник: <@{self.member_id}>\n"
                f"👮 Отменил: {interaction.user.mention}\n"
                "ℹ️ Предупреждения остались без изменений.",
            ),
            view=None,
        )
        self.stop()


# --------------------------------------------------------------------------- #
WARN_CHOICES = [
    app_commands.Choice(name="⚠️ 1 — первое предупреждение", value=1),
    app_commands.Choice(name="🟠 2 — последнее предупреждение", value=2),
    app_commands.Choice(name="🔴 3 — снятие с должности", value=3),
]


class WarningsCog(commands.Cog, name="Предупреждения"):
    """Выдача и снятие предупреждений персонала."""

    def __init__(self, bot: "KingGriefBot") -> None:
        self.bot = bot
        self.repo = bot.repo
        self.manager = bot.warning_manager

    # ------------------------------------------------------------------ #
    async def _guard_staff(
        self, interaction: discord.Interaction, member: discord.Member
    ) -> str | None:
        """Проверки перед выдачей варна. Возвращает текст ошибки или None."""
        if member.bot:
            return "❌ Боты не являются сотрудниками КП."
        if not await self.manager.is_staff(interaction.guild, member):
            return (
                "❌ Пользователь не является сотрудником КП.\n"
                "У него нет контролируемой роли (Стажер, Мл.Модер, Модер и т. д.)."
            )
        return None

    # ------------------------------------------------------------------ #
    @app_commands.command(name="warn", description="⚠️ Установить количество предупреждений (1-3)")
    @app_commands.describe(
        сотрудник="Сотрудник, которому выдаётся предупреждение",
        количество="Сколько предупреждений установить",
        причина="Причина предупреждения",
    )
    @app_commands.choices(количество=WARN_CHOICES)
    @require("warn")
    async def warn(
        self,
        interaction: discord.Interaction,
        сотрудник: discord.Member,
        количество: app_commands.Choice[int] | None = None,
        причина: str | None = None,
    ) -> None:
        """Устанавливает точное число варнов; при 3/3 — запрос на снятие роли."""
        error = await self._guard_staff(interaction, сотрудник)
        if error:
            await interaction.response.send_message(
                embed=E.error("Операция невозможна", error), ephemeral=True
            )
            return

        target = количество.value if количество else 1
        outcome = await self.manager.set_warnings(
            interaction.guild,
            сотрудник,
            interaction.user,
            target,
            причина or "",
        )

        if outcome.kind == "error":
            await interaction.response.send_message(
                embed=E.error("Операция невозможна", outcome.message), ephemeral=True
            )
            return

        if outcome.needs_confirmation:
            view = DemoteConfirmView(
                self.bot,
                member_id=сотрудник.id,
                roles=[
                    {
                        "name": role["name"],
                        "role_id": role["role_id"],
                    }
                    for role in await self.manager.controlled_roles_of(
                        interaction.guild, сотрудник
                    )
                ],
                pending_id=outcome.pending_id,
                reason=причина or "Максимум предупреждений",
                actor_id=interaction.user.id,
            )
            await interaction.response.send_message(embed=outcome.embed, view=view)
            return

        await interaction.response.send_message(embed=outcome.embed)

    # ------------------------------------------------------------------ #
    @app_commands.command(name="addwarn", description="⚠️ Добавить одно предупреждение")
    @app_commands.describe(сотрудник="Сотрудник", причина="Причина предупреждения")
    @require("addwarn")
    async def addwarn(
        self,
        interaction: discord.Interaction,
        сотрудник: discord.Member,
        причина: str | None = None,
    ) -> None:
        error = await self._guard_staff(interaction, сотрудник)
        if error:
            await interaction.response.send_message(
                embed=E.error("Операция невозможна", error), ephemeral=True
            )
            return
        outcome = await self.manager.add_warning(
            interaction.guild, сотрудник, interaction.user, причина or ""
        )
        await interaction.response.send_message(
            embed=outcome.embed or E.success("Готово", outcome.message)
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="unwarn", description="🟢 Снять одно предупреждение")
    @app_commands.describe(сотрудник="Сотрудник")
    @require("unwarn")
    async def unwarn(self, interaction: discord.Interaction, сотрудник: discord.Member) -> None:
        outcome = await self.manager.remove_warning(
            interaction.guild, сотрудник, interaction.user
        )
        embed = outcome.embed or (
            E.error("Ошибка", outcome.message)
            if outcome.kind == "none"
            else E.success("Готово", outcome.message)
        )
        await interaction.response.send_message(embed=embed)

    # ------------------------------------------------------------------ #
    @app_commands.command(name="warns", description="⚠️ Все предупреждения сотрудника")
    @app_commands.describe(сотрудник="Сотрудник (по умолчанию — вы)")
    @require("warns")
    async def warns(
        self, interaction: discord.Interaction, сотрудник: discord.Member | None = None
    ) -> None:
        from bot.permissions import permissions

        target = сотрудник or interaction.user
        level = await permissions.level(interaction.guild, interaction.user)
        if not permissions.can_view_target(level, interaction.user.id, target.id):
            await interaction.response.send_message(
                embed=E.error(
                    "Недостаточно прав", "Вы можете смотреть только свои предупреждения."
                ),
                ephemeral=True,
            )
            return
        await interaction.response.send_message(
            embed=await self.manager.warn_card(interaction.guild, target)
        )

    # ------------------------------------------------------------------ #
    @app_commands.command(name="clearwarns", description="🧹 Сбросить предупреждения до нуля")
    @app_commands.describe(сотрудник="Сотрудник")
    @require("clearwarns")
    async def clearwarns(
        self, interaction: discord.Interaction, сотрудник: discord.Member
    ) -> None:
        outcome = await self.manager.clear_warnings(
            interaction.guild, сотрудник, interaction.user
        )
        embed = outcome.embed or (
            E.error("Ошибка", outcome.message)
            if outcome.kind == "none"
            else E.success("Готово", outcome.message)
        )
        await interaction.response.send_message(embed=embed)


async def setup(bot: "KingGriefBot") -> None:  # pragma: no cover
    await bot.add_cog(WarningsCog(bot))
