"""
Система предупреждений КП-KingGrief.

Логика варнов:
  1 варн  → 🟡 «Первое предупреждение»;
  2 варна → 🟠 «Последнее предупреждение»;
  3 варна → 🔴 «Снятие с должности» — после подтверждения уполномоченного
            администратора бот снимает КОНТРОЛИРУЕМУЮ роль.

Все действия пишутся в `warnings`, `punishments` и `audit_logs`.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

import discord

from database.models import Repository, repo as _global_repo
from services.role_manager import RoleManager, RoleRemovalResult, role_manager as _role_manager
from utils import embeds as E
from utils.logging import get_logger

log = get_logger("warning_manager")

ACTION_WARN = "warn"
ACTION_UNWARN = "unwarn"
ACTION_CLEAR = "clearwarns"
ACTION_DEMOTE = "role_removal"


@dataclass(slots=True)
class WarnOutcome:
    """Итог операции с предупреждениями."""

    kind: str  # warn | last_warn | demote_pending | demote_done | none | error
    before: int = 0
    after: int = 0
    max_warns: int = 3
    reason: str = ""
    message: str = ""
    pending_id: int | None = None
    removal: RoleRemovalResult | None = None
    roles: list[str] = field(default_factory=list)
    embed: discord.Embed | None = None

    @property
    def needs_confirmation(self) -> bool:
        return self.kind == "demote_pending"

    @property
    def ok(self) -> bool:
        return self.kind not in ("error", "none")


class WarningManager:
    """Выдача, снятие и учёт предупреждений."""

    def __init__(
        self,
        repository: Repository | None = None,
        roles: RoleManager | None = None,
    ) -> None:
        self.repo = repository or _global_repo
        self.roles = roles or _role_manager

    # ------------------------------------------------------------------ #
    #  Базовые операции
    # ------------------------------------------------------------------ #
    async def count(self, guild_id: int, user_id: int) -> int:
        return await self.repo.warnings.count(guild_id, user_id)

    async def max_warnings(self, guild_id: int) -> int:
        return max(1, int(await self.repo.settings.get(guild_id, "max_warnings", 3) or 3))

    async def list(self, guild_id: int, user_id: int) -> list[Any]:
        return await self.repo.warnings.list(guild_id, user_id)

    async def position_of(self, guild: discord.Guild, member: discord.Member) -> str:
        staff = await self.repo.staff.get(guild.id, member.id)
        if staff and staff.position:
            return staff.position
        position, _ = await self.roles.primary_position(guild, member)
        return position

    async def is_staff(self, guild: discord.Guild, member: discord.Member) -> bool:
        staff = await self.repo.staff.get(guild.id, member.id)
        if staff and staff.is_active:
            return True
        return bool(await self.roles.member_roles(guild, member))

    async def controlled_roles_of(self, guild: discord.Guild, member: discord.Member) -> list[dict[str, Any]]:
        return await self.roles.member_roles(guild, member)

    # ------------------------------------------------------------------ #
    #  Выдача
    # ------------------------------------------------------------------ #
    async def add_warning(
        self,
        guild: discord.Guild,
        member: discord.Member,
        issuer: discord.Member,
        reason: str = "",
        *,
        notify: bool = True,
    ) -> WarnOutcome:
        """Добавляет одно предупреждение (аналог /addwarn)."""
        before = await self.count(guild.id, member.id)
        limit = await self.max_warnings(guild.id)
        await self.repo.warnings.add(guild.id, member.id, issuer.id, reason)
        after = before + 1
        position = await self.position_of(guild, member)

        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action=ACTION_WARN,
            detail=f"Предупреждение {after}/{limit}. Причина: {reason or 'не указана'}",
            performed_by=issuer.id,
        )
        await self.repo.audit.add(
            guild.id,
            ACTION_WARN,
            actor_id=issuer.id,
            target_id=member.id,
            detail=f"Варн {after}/{limit}: {reason or 'не указана'}",
        )

        embed = E.warn_embed(
            member=member,
            position=position,
            warns=after,
            max_warns=limit,
            reason=reason,
            issuer=issuer,
        )
        if notify:
            await self.publish(guild, embed)
            await self.notify_staff(member, embed)

        kind = "last_warn" if after >= limit - 1 else "warn"
        return WarnOutcome(
            kind=kind,
            before=before,
            after=after,
            max_warns=limit,
            reason=reason,
            message="✅ Предупреждение успешно выдано.",
            embed=embed,
        )

    async def set_warnings(
        self,
        guild: discord.Guild,
        member: discord.Member,
        issuer: discord.Member,
        target: int,
        reason: str = "",
        *,
        notify: bool = True,
        auto_demote: bool | None = None,
        require_confirm: bool | None = None,
    ) -> WarnOutcome:
        """Устанавливает точное количество предупреждений (команда /warn).

        При достижении максимума создаёт запрос на снятие с должности.
        """
        limit = await self.max_warnings(guild.id)
        target = max(0, min(int(target), limit))
        position = await self.position_of(guild, member)

        if auto_demote is None:
            auto_demote = bool(
                await self.repo.settings.get(guild.id, "auto_demote_on_max_warnings", True)
            )
        if require_confirm is None:
            require_confirm = bool(
                await self.repo.settings.get(guild.id, "require_confirm_demote", True)
            )

        before_count, after_count, delta = await self.repo.warnings.set_count(
            guild.id, member.id, target, issuer.id, reason
        )

        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action=ACTION_WARN,
            detail=(
                f"Установлено предупреждений: {after_count}/{limit} "
                f"(было {before_count}). Причина: {reason or 'не указана'}"
            ),
            performed_by=issuer.id,
        )
        await self.repo.audit.add(
            guild.id,
            ACTION_WARN,
            actor_id=issuer.id,
            target_id=member.id,
            detail=f"/warn {after_count}/{limit} ({'+' if delta >= 0 else ''}{delta})",
        )

        # --- достижение максимума ------------------------------------- #
        if after_count >= limit and auto_demote:
            controlled = await self.controlled_roles_of(guild, member)
            if not controlled:
                return WarnOutcome(
                    kind="error",
                    before=before_count,
                    after=after_count,
                    max_warns=limit,
                    message="❌ У пользователя нет контролируемой роли.",
                )

            pending = await self.repo.punishments.add(
                guild_id=guild.id,
                user_id=member.id,
                action=ACTION_DEMOTE,
                detail=f"Достигнут максимум предупреждений ({after_count}/{limit})",
                performed_by=issuer.id,
                status="pending",
                metadata={
                    "reason": reason,
                    "roles": [
                        {"name": r["name"], "role_id": r["role_id"]} for r in controlled
                    ],
                },
            )
            embed = E.demote_pending_embed(
                member=member,
                position=position,
                warns=after_count,
                max_warns=limit,
                issuer=issuer,
                role_options=[str(r["name"]) for r in controlled],
            )
            if notify:
                await self.publish(guild, embed)

            if not require_confirm and len(controlled) == 1:
                # однозначный случай — снимаем сразу
                return await self.confirm_demotion(
                    guild,
                    member,
                    controlled[0]["role"],
                    issuer,
                    reason=reason or "Максимум предупреждений",
                    pending_id=pending,
                )

            return WarnOutcome(
                kind="demote_pending",
                before=before_count,
                after=after_count,
                max_warns=limit,
                reason=reason,
                pending_id=pending,
                roles=[str(r["name"]) for r in controlled],
                embed=embed,
                message="🛡️ Требуется подтверждение администратора для снятия с должности.",
            )

        # --- обычное предупреждение ----------------------------------- #
        embed = E.warn_embed(
            member=member,
            position=position,
            warns=after_count,
            max_warns=limit,
            reason=reason,
            issuer=issuer,
        )
        if notify:
            await self.publish(guild, embed)
            await self.notify_staff(member, embed)

        if after_count >= limit - 1 and limit > 1:
            kind = "last_warn"
        else:
            kind = "warn"
        return WarnOutcome(
            kind=kind,
            before=before_count,
            after=after_count,
            max_warns=limit,
            reason=reason,
            embed=embed,
            message="✅ Предупреждение успешно выдано.",
        )

    # ------------------------------------------------------------------ #
    #  Снятие
    # ------------------------------------------------------------------ #
    async def remove_warning(
        self,
        guild: discord.Guild,
        member: discord.Member,
        actor: discord.Member,
    ) -> WarnOutcome:
        """Снимает одно (последнее) предупреждение."""
        limit = await self.max_warnings(guild.id)
        before = await self.count(guild.id, member.id)
        record = await self.repo.warnings.remove_one(guild.id, member.id, actor.id)
        if record is None:
            return WarnOutcome(
                kind="none",
                before=before,
                after=before,
                max_warns=limit,
                message="❌ У сотрудника нет активных предупреждений.",
            )
        after = before - 1
        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action=ACTION_UNWARN,
            detail=f"Снято предупреждение ({before} → {after}). Было: {record.reason or 'без причины'}",
            performed_by=actor.id,
        )
        await self.repo.audit.add(
            guild.id,
            ACTION_UNWARN,
            actor_id=actor.id,
            target_id=member.id,
            detail=f"Варн снят: {before} → {after}",
        )
        embed = E.success(
            "ПРЕДУПРЕЖДЕНИЕ СНЯТО",
            f"👤 Сотрудник: {member.mention}\n"
            f"🎖️ Должность: {await self.position_of(guild, member)}\n"
            f"⚠️ Варны: **{after}/{limit}**\n"
            f"👮 Снял: {actor.mention}",
        )
        await self.publish(guild, embed)
        return WarnOutcome(
            kind="warn",
            before=before,
            after=after,
            max_warns=limit,
            embed=embed,
            message="✅ Предупреждение снято.",
        )

    async def clear_warnings(
        self,
        guild: discord.Guild,
        member: discord.Member,
        actor: discord.Member,
    ) -> WarnOutcome:
        """Обнуляет все предупреждения сотрудника."""
        limit = await self.max_warnings(guild.id)
        before = await self.count(guild.id, member.id)
        removed = await self.repo.warnings.clear(guild.id, member.id, actor.id)
        if not removed:
            return WarnOutcome(
                kind="none",
                before=before,
                after=before,
                max_warns=limit,
                message="❌ У сотрудника нет активных предупреждений.",
            )
        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action=ACTION_CLEAR,
            detail=f"Предупреждения сброшены ({removed} шт.)",
            performed_by=actor.id,
        )
        await self.repo.audit.add(
            guild.id,
            ACTION_CLEAR,
            actor_id=actor.id,
            target_id=member.id,
            detail=f"Сброшено варнов: {removed}",
        )
        embed = E.success(
            "ПРЕДУПРЕЖДЕНИЯ СБРОШЕНЫ",
            f"👤 Сотрудник: {member.mention}\n"
            f"⚠️ Снято предупреждений: **{removed}**\n"
            f"⚠️ Варны: **0/{limit}**\n"
            f"👮 Выполнил: {actor.mention}",
        )
        await self.publish(guild, embed)
        return WarnOutcome(
            kind="warn",
            before=before,
            after=0,
            max_warns=limit,
            embed=embed,
            message="✅ Предупреждения сброшены до нуля.",
        )

    # ------------------------------------------------------------------ #
    #  Снятие с должности
    # ------------------------------------------------------------------ #
    async def confirm_demotion(
        self,
        guild: discord.Guild,
        member: discord.Member,
        role: discord.Role,
        actor: discord.Member,
        *,
        reason: str = "",
        pending_id: int | None = None,
    ) -> WarnOutcome:
        """Подтверждённое снятие контролируемой роли."""
        limit = await self.max_warnings(guild.id)
        warns = await self.count(guild.id, member.id)
        position = await self.position_of(guild, member)
        allow_above = bool(
            await self.repo.settings.get(guild.id, "allow_demote_above_executor", False)
        )

        removal = await self.roles.remove_controlled_role(
            guild,
            member,
            role,
            actor=actor,
            reason=reason or f"Максимум предупреждений ({warns}/{limit})",
            allow_above_executor=allow_above,
        )
        if not removal.ok:
            await self.repo.audit.add(
                guild.id,
                "role_removal_denied",
                actor_id=actor.id,
                target_id=member.id,
                detail=f"Отказ: {removal.reason}",
            )
            return WarnOutcome(
                kind="error",
                after=warns,
                max_warns=limit,
                removal=removal,
                message=removal.message,
            )

        if pending_id:
            await self.repo.punishments.resolve(pending_id, "completed")
        else:
            existing = await self.repo.punishments.pending_for(guild.id, member.id)
            if existing:
                await self.repo.punishments.resolve(existing.id, "completed")

        embed = E.demote_embed(
            member=member,
            position=position,
            warns=warns,
            max_warns=limit,
            issuer=actor,
            role_name=removal.role_name,
        )
        await self.publish(guild, embed)
        await self.notify_staff(member, embed)

        return WarnOutcome(
            kind="demote_done",
            after=warns,
            max_warns=limit,
            removal=removal,
            embed=embed,
            message="✅ Роль успешно снята.",
            roles=[removal.role_name],
        )

    async def cancel_demotion(
        self, guild: discord.Guild, member: discord.Member, actor: discord.Member
    ) -> bool:
        """Отменяет ожидающее снятие с должности."""
        pending = await self.repo.punishments.pending_for(guild.id, member.id)
        if not pending:
            return False
        await self.repo.punishments.resolve(pending.id, "cancelled")
        await self.repo.audit.add(
            guild.id,
            "demotion_cancelled",
            actor_id=actor.id,
            target_id=member.id,
            detail="Снятие с должности отменено администратором",
        )
        return True

    # ------------------------------------------------------------------ #
    #  Уведомления
    # ------------------------------------------------------------------ #
    async def warns_channel(self, guild: discord.Guild):
        """Канал «варны-кп». Подходит любой текстовый канал или тред."""
        channel_id = int(await self.repo.settings.get(guild.id, "warns_channel_id", 0) or 0)
        if not channel_id:
            return None
        channel = guild.get_channel(channel_id)
        if channel is None or not hasattr(channel, "send"):
            return None
        return channel

    async def publish(self, guild: discord.Guild, embed: discord.Embed) -> bool:
        """Публикует уведомление в канал «варны-кп»."""
        channel = await self.warns_channel(guild)
        if channel is None:
            log.debug("Канал «варны-кп» не настроен — уведомление не отправлено.")
            return False
        try:
            await channel.send(embed=embed)
            return True
        except discord.Forbidden:
            log.warning("Нет прав на отправку в канал «варны-кп» (%s).", channel)
        except discord.HTTPException as exc:
            log.error("Не удалось отправить уведомление в «варны-кп»: %s", exc)
        return False

    async def notify_staff(self, member: discord.Member, embed: discord.Embed) -> bool:
        """Личное уведомление сотруднику (если разрешено настройками)."""
        guild = getattr(member, "guild", None)
        if guild is None:
            return False
        if not await self.repo.settings.get(guild.id, "notify_staff_on_warn", True):
            return False
        try:
            await member.send(embed=embed)
            return True
        except (discord.Forbidden, discord.HTTPException):
            return False

    # ------------------------------------------------------------------ #
    #  Отчёты по варнам
    # ------------------------------------------------------------------ #
    async def summary(self, guild_id: int) -> dict[str, Any]:
        active = await self.repo.warnings.total_active(guild_id)
        top = await self.repo.warnings.leaderboard(guild_id, 10)
        return {"active": active, "top": top}

    async def warn_card(
        self, guild: discord.Guild, member: discord.Member
    ) -> discord.Embed:
        """Embed со списком предупреждений сотрудника (/warns)."""
        limit = await self.max_warnings(guild.id)
        records = await self.list(guild.id, member.id)
        active = [w for w in records if w.is_active]
        inactive = [w for w in records if not w.is_active]

        embed = E.warning(
            f"{_TITLE} | ПРЕДУПРЕЖДЕНИЯ",
            f"👤 Сотрудник: {member.mention}\n"
            f"🎖️ Должность: {await self.position_of(guild, member)}\n"
            f"⚠️ Варны: **{len(active)}/{limit}**",
        )
        if active:
            lines = [
                f"**{i}.** `{w.created_at[:16]}` — {w.reason or 'причина не указана'} "
                f"(выдал <@{w.issued_by}>)"
                for i, w in enumerate(active, start=1)
            ]
            embed.add_field(name="⚠️ Активные", value="\n".join(lines)[:1024], inline=False)
        if inactive:
            lines = [
                f"~~`{w.created_at[:16]}` — {w.reason or 'без причины'}~~" for w in inactive[:10]
            ]
            embed.add_field(name="🟢 Снятые", value="\n".join(lines)[:1024], inline=False)
        if not active and not inactive:
            embed.add_field(
                name="ℹ️", value="У сотрудника нет предупреждений. Так держать!", inline=False
            )
        return embed


_TITLE = "КП-KingGrief"

warning_manager = WarningManager()
