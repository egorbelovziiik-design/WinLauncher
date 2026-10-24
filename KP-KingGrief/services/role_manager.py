"""
Контроль ролей персонала и безопасное снятие должностей.

Главный принцип безопасности: бот снимает ТОЛЬКО те роли, которые явно
перечислены в списке контролируемых. Любая посторонняя роль не трогается
ни при каких обстоятельствах. Перед снятием проверяются:
  * наличие у бота права `manage_roles`;
  * иерархия ролей (роль бота должна быть выше снимаемой);
  * иерархия инициатора (если это не главный администратор);
  * принадлежность роли интеграции/боту (такие роли Discord не даст снять).
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Iterable, Sequence

import discord

from config import CONTROLLED_ROLES
from database.models import Repository, repo as _global_repo
from utils.logging import get_logger
from utils.periods import fmt_date, now, to_db

log = get_logger("role_manager")


def norm(name: str | None) -> str:
    """Нормализация названия роли/канала для сравнения."""
    return " ".join(str(name or "").lower().replace("ё", "е").strip().split())


@dataclass(slots=True)
class RoleRemovalResult:
    """Результат попытки снять контролируемую роль."""

    ok: bool
    role_name: str = ""
    role_id: int = 0
    reason: str = ""
    removed_roles: list[str] = field(default_factory=list)

    @property
    def message(self) -> str:
        if self.ok:
            return f"✅ Роль `{self.role_name}` успешно снята."
        return f"❌ {self.reason}"


# --------------------------------------------------------------------------- #
class RoleManager:
    """Поиск сотрудников по ролям, синхронизация и снятие должностей."""

    def __init__(self, repository: Repository | None = None) -> None:
        self.repo = repository or _global_repo

    # ------------------------------------------------------------------ #
    #  Определение контролируемых ролей
    # ------------------------------------------------------------------ #
    async def control_config(self, guild_id: int) -> list[dict[str, Any]]:
        """Конфигурация контролируемых ролей из настроек сервера."""
        return await self.repo.settings.control_roles(guild_id)

    async def controlled_names(self, guild_id: int, *, tracked_only: bool = False) -> set[str]:
        config = await self.control_config(guild_id)
        return {
            norm(r["name"])
            for r in config
            if not tracked_only or bool(r.get("tracked", True))
        }

    def resolve_roles(
        self, guild: discord.Guild, config: Sequence[dict[str, Any]], *, tracked_only: bool = False
    ) -> list[dict[str, Any]]:
        """Сопоставляет конфигурацию с реальными ролями сервера по названию."""
        by_name = {norm(role.name): role for role in guild.roles}
        resolved: list[dict[str, Any]] = []
        for entry in config:
            if tracked_only and not entry.get("tracked", True):
                continue
            role = by_name.get(norm(entry.get("name")))
            resolved.append(
                {
                    "name": entry.get("name"),
                    "role_id": role.id if role else int(entry.get("role_id") or 0),
                    "priority": int(entry.get("priority") or 0),
                    "tracked": bool(entry.get("tracked", True)),
                    "found": role is not None,
                }
            )
        return resolved

    async def sync_role_config(self, guild: discord.Guild) -> tuple[int, int]:
        """Обновляет Discord ID контролируемых ролей в настройках.

        Возвращает (найдено ролей, всего в конфигурации).
        """
        config = await self.control_config(guild.id)
        resolved = self.resolve_roles(guild, config)
        await self.repo.settings.set(guild.id, "control_roles", resolved)
        for role in guild.roles:
            await self.repo.cache.put_role(
                guild.id,
                role.id,
                role.name,
                norm(role.name) in {norm(r["name"]) for r in config},
                role.position,
            )
        found = sum(1 for item in resolved if item["found"])
        log.info(
            "Роли сервера «%s»: найдено %s из %s контролируемых.",
            guild.name,
            found,
            len(resolved),
        )
        return found, len(resolved)

    async def missing_roles(self, guild: discord.Guild) -> list[str]:
        """Контролируемые роли, которых нет на сервере."""
        resolved = self.resolve_roles(guild, await self.control_config(guild.id))
        return [r["name"] for r in resolved if not r["found"]]

    async def sync_permission_roles(self, guild: discord.Guild) -> dict[str, int]:
        """Заполняет «корзины» ролей прав доступа по стандартным названиям.

        Заполняются только пустые корзины — то, что администратор настроил
        вручную через /setup, никогда не перезаписывается.
        """
        from config import (
            DEFAULT_ADMIN_ROLE_NAMES,
            DEFAULT_MOD_ROLE_NAMES,
            DEFAULT_SENIOR_ROLE_NAMES,
            DEFAULT_TRAINEE_ROLE_NAMES,
        )

        buckets = {
            "admin_role_ids": DEFAULT_ADMIN_ROLE_NAMES,
            "senior_role_ids": DEFAULT_SENIOR_ROLE_NAMES,
            "mod_role_ids": DEFAULT_MOD_ROLE_NAMES,
            "trainee_role_ids": DEFAULT_TRAINEE_ROLE_NAMES,
        }
        counts: dict[str, int] = {}
        for key, names in buckets.items():
            current = [int(x) for x in await self.repo.settings.get(guild.id, key, []) or []]
            if current:
                counts[key] = len(current)
                continue
            ids = [
                role.id
                for name in names
                if (role := self.find_role_by_name(guild, name)) is not None
            ]
            await self.repo.settings.set(guild.id, key, ids)
            counts[key] = len(ids)
        log.info("Роли прав доступа (%s): %s", guild.name, counts)
        return counts

    # ------------------------------------------------------------------ #
    #  Роли конкретного участника
    # ------------------------------------------------------------------ #
    def member_controlled_roles(
        self, member: discord.Member, config: Sequence[dict[str, Any]]
    ) -> list[dict[str, Any]]:
        """Контролируемые роли участника, от старшей к младшей."""
        wanted = {norm(r["name"]): r for r in config}
        result: list[dict[str, Any]] = []
        for role in getattr(member, "roles", []) or []:
            entry = wanted.get(norm(role.name))
            if entry is None:
                continue
            result.append(
                {
                    "name": entry.get("name") or role.name,
                    "role_id": role.id,
                    "priority": int(entry.get("priority") or 0),
                    "tracked": bool(entry.get("tracked", True)),
                    "role": role,
                }
            )
        result.sort(key=lambda r: (-int(r["priority"]), r["name"]))
        return result

    async def member_roles(self, guild: discord.Guild, member: discord.Member) -> list[dict[str, Any]]:
        return self.member_controlled_roles(member, await self.control_config(guild.id))

    async def primary_position(self, guild: discord.Guild, member: discord.Member) -> tuple[str, int]:
        """Главная должность участника (самая старшая из контролируемых)."""
        roles = await self.member_roles(guild, member)
        if not roles:
            return "", 0
        top = roles[0]
        return str(top["name"]), int(top["role_id"])

    # ------------------------------------------------------------------ #
    #  Синхронизация сотрудников
    # ------------------------------------------------------------------ #
    async def sync_member(self, guild: discord.Guild, member: discord.Member) -> dict[str, Any]:
        """Добавляет/обновляет сотрудника в базе по его текущим ролям."""
        config = await self.control_config(guild.id)
        roles = self.member_controlled_roles(member, config)
        tracked = [r for r in roles if r["tracked"]]

        if not tracked:
            existing = await self.repo.staff.get(guild.id, member.id)
            if existing and existing.is_active:
                await self.repo.staff.mark_left(guild.id, member.id)
                await self.repo.staff_roles.set_roles(
                    guild.id,
                    member.id,
                    [{"role_id": r["role_id"], "name": r["name"], "priority": r["priority"]} for r in roles],
                )
                await self.repo.audit.add(
                    guild.id,
                    "staff_demoted",
                    actor_id=member.id,
                    target_id=member.id,
                    detail=f"{member} больше не имеет контролируемой роли",
                )
                return {"action": "removed", "member": member}
            return {"action": "skipped", "member": member}

        position, role_id = tracked[0]["name"], int(tracked[0]["role_id"])
        before = await self.repo.staff.get(guild.id, member.id)
        staff = await self.repo.staff.upsert(
            guild_id=guild.id,
            user_id=member.id,
            username=str(member),
            display_name=getattr(member, "display_name", "") or str(member),
            position=position,
            role_id=role_id,
        )
        added, removed = await self.repo.staff_roles.set_roles(
            guild.id,
            member.id,
            [{"role_id": r["role_id"], "name": r["name"], "priority": r["priority"]} for r in tracked],
        )

        action = "added" if before is None else "updated"
        if before is not None and before.position != position:
            action = "position_changed"
            await self.repo.punishments.add(
                guild_id=guild.id,
                user_id=member.id,
                action="role_change",
                detail=f"Должность изменена: {before.position} → {position}",
                role_id=role_id,
                role_name=position,
            )
        if added or removed:
            await self.repo.audit.add(
                guild.id,
                "role_change",
                actor_id=member.id,
                target_id=member.id,
                detail=(
                    f"{member}: +" + ", ".join(added) if added else f"{member}: "
                )
                + (f" / -{', '.join(removed)}" if removed else ""),
            )
        log.info(
            "Сотрудник %s синхронизирован: %s (роли +%s / -%s)",
            member,
            position,
            added or "—",
            removed or "—",
        )
        return {"action": action, "member": member, "staff": staff, "position": position}

    async def sync_guild(self, guild: discord.Guild) -> dict[str, int]:
        """Полная синхронизация состава персонала сервера."""
        await self.sync_role_config(guild)
        summary = {"added": 0, "updated": 0, "removed": 0, "total": 0, "skipped": 0}
        seen: set[int] = set()

        for member in guild.members:
            if getattr(member, "bot", False):
                continue
            result = await self.sync_member(guild, member)
            action = result["action"]
            if action == "skipped":
                summary["skipped"] += 1
                continue
            seen.add(member.id)
            if action == "added":
                summary["added"] += 1
            elif action == "removed":
                summary["removed"] += 1
            else:
                summary["updated"] += 1

        # те, кто в базе активен, но уже без роли на сервере
        for staff in await self.repo.staff.list(guild.id, active_only=True):
            if staff.user_id in seen:
                continue
            await self.repo.staff.mark_left(guild.id, staff.user_id)
            summary["removed"] += 1

        summary["total"] = await self.repo.staff.count(guild.id)
        await self.repo.audit.add(
            guild.id,
            "settings",
            detail=(
                f"Синхронизация персонала: всего {summary['total']}, "
                f"добавлено {summary['added']}, обновлено {summary['updated']}, "
                f"снято {summary['removed']}"
            ),
        )
        log.info("Синхронизация персонала «%s»: %s", guild.name, summary)
        return summary

    async def handle_member_update(
        self, before: discord.Member, after: discord.Member
    ) -> dict[str, Any] | None:
        """Реакция на изменение ролей/ника участника."""
        guild = getattr(after, "guild", None) or getattr(before, "guild", None)
        if guild is None:
            return None
        same_roles = {r.id for r in before.roles} == {r.id for r in after.roles}
        same_name = str(before) == str(after)
        if same_roles and same_name:
            return None
        return await self.sync_member(guild, after)

    async def handle_member_remove(self, member: discord.Member) -> bool:
        """Участник покинул сервер — история сохраняется, сотрудник деактивируется."""
        guild = getattr(member, "guild", None)
        if guild is None:
            return False
        existing = await self.repo.staff.get(guild.id, member.id)
        if existing is None:
            return False
        await self.repo.staff.mark_left(guild.id, member.id)
        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action="staff_leave",
            detail=f"{member} ({existing.position}) покинул сервер",
        )
        log.info("Сотрудник %s покинул сервер — история сохранена.", member)
        return True

    # ------------------------------------------------------------------ #
    #  Безопасное снятие роли
    # ------------------------------------------------------------------ #
    async def can_remove_role(
        self,
        guild: discord.Guild,
        role: discord.Role,
        *,
        actor: discord.Member | None = None,
        allow_above_executor: bool = False,
        guild_id: int | None = None,
    ) -> tuple[bool, str]:
        """Проверяет, может ли бот снять конкретную роль."""
        guild_id = guild_id or guild.id
        controlled = await self.controlled_names(guild_id)
        if norm(role.name) not in controlled:
            return False, "Роль не является контролируемой — бот не снимает посторонние роли."

        me = getattr(guild, "me", None)
        if me is None:  # pragma: no cover - всегда есть в реальном гильде
            return False, "Не удалось определить права бота на сервере."

        permissions = getattr(me, "guild_permissions", None)
        if permissions is not None and not bool(getattr(permissions, "manage_roles", False)):
            return False, "У бота нет права «Управлять ролями»."

        top_role = getattr(me, "top_role", None)
        if top_role is not None and role >= top_role:
            return False, "Роль находится выше или на уровне роли бота (иерархия Discord)."

        if role.managed:
            return False, "Роль управляется интеграцией или ботом — Discord не позволяет её снять."

        if actor is not None and not allow_above_executor:
            actor_top = getattr(actor, "top_role", None)
            if actor_top is not None and role >= actor_top:
                return False, "Роль выше роли инициатора — требуется главный администратор."
            actor_perms = getattr(actor, "guild_permissions", None)
            if actor_perms is not None and not (
                bool(getattr(actor_perms, "manage_roles", False))
                or bool(getattr(actor_perms, "administrator", False))
            ):
                return False, "У инициатора нет права «Управлять ролями»."

        return True, ""

    async def remove_controlled_role(
        self,
        guild: discord.Guild,
        member: discord.Member,
        role: discord.Role,
        *,
        actor: discord.Member | None = None,
        reason: str = "",
        allow_above_executor: bool = False,
    ) -> RoleRemovalResult:
        """Снимает одну контролируемую роль. Ничего лишнего не трогает."""
        allowed, why = await self.can_remove_role(
            guild,
            role,
            actor=actor,
            allow_above_executor=allow_above_executor,
            guild_id=guild.id,
        )
        if not allowed:
            log.warning(
                "Отказ в снятии роли «%s» у %s: %s", role.name, member, why
            )
            return RoleRemovalResult(False, role.name, role.id, why)

        snapshot = {r.id for r in member.roles}
        try:
            await member.remove_roles(role, reason=reason or "КП-KingGrief: снятие с должности")
        except discord.Forbidden:
            return RoleRemovalResult(
                False, role.name, role.id, "Бот не может снять роль (недостаточно прав)."
            )
        except discord.HTTPException as exc:
            return RoleRemovalResult(False, role.name, role.id, f"Ошибка Discord API: {exc}")

        after = {r.id for r in getattr(member, "roles", [])}
        removed = snapshot - after
        if role.id not in snapshot:
            return RoleRemovalResult(False, role.name, role.id, "У сотрудника уже нет этой роли.")

        # обновляем базу
        await self.repo.staff_roles.revoke(guild.id, member.id, role.id)
        remaining = await self.repo.staff_roles.active(guild.id, member.id)
        if remaining:
            top = remaining[0]
            await self.repo.staff.upsert(
                guild_id=guild.id,
                user_id=member.id,
                username=str(member),
                display_name=getattr(member, "display_name", "") or str(member),
                position=top.role_name,
                role_id=top.role_id,
            )
        else:
            await self.repo.staff.mark_left(guild.id, member.id)

        await self.repo.punishments.add(
            guild_id=guild.id,
            user_id=member.id,
            action="role_removal",
            detail=reason or f"Снята должность «{role.name}»",
            role_id=role.id,
            role_name=role.name,
            performed_by=getattr(actor, "id", 0) or 0,
            status="completed",
            metadata={"removed_ids": sorted(removed)},
        )
        await self.repo.audit.add(
            guild.id,
            "role_removal",
            actor_id=getattr(actor, "id", 0) or 0,
            target_id=member.id,
            detail=f"Снята роль «{role.name}» у {member}. Причина: {reason or 'не указана'}",
        )
        log.info("Роль «%s» снята у %s (инициатор: %s).", role.name, member, actor)
        return RoleRemovalResult(
            True, role.name, role.id, "", [role.name]
        )

    # ------------------------------------------------------------------ #
    #  Вспомогательное
    # ------------------------------------------------------------------ #
    def find_role(self, guild: discord.Guild, role_id: int) -> discord.Role | None:
        return discord.utils.get(guild.roles, id=role_id)

    def find_role_by_name(self, guild: discord.Guild, name: str) -> discord.Role | None:
        wanted = norm(name)
        return discord.utils.find(lambda r: norm(r.name) == wanted, guild.roles)

    async def default_config(self) -> list[dict[str, Any]]:
        return [dict(entry) for entry in CONTROLLED_ROLES]

    async def enable_tracking(self, guild_id: int, names: Iterable[str], enabled: bool) -> list[str]:
        """Включает/выключает отслеживание дополнительных ролей (Д.*)."""
        config = await self.control_config(guild_id)
        wanted = {norm(n) for n in names}
        changed: list[str] = []
        for entry in config:
            if norm(entry.get("name")) in wanted:
                entry["tracked"] = enabled
                changed.append(str(entry.get("name")))
        await self.repo.settings.set(guild_id, "control_roles", config)
        return changed

    async def assign_channel(
        self, guild: discord.Guild, slot: str, channel: discord.abc.GuildChannel | None
    ) -> None:
        """Сохраняет ID канала в настройках."""
        await self.repo.settings.set(guild.id, slot, channel.id if channel else 0)
        if channel is not None:
            await self.repo.cache.put_channel(
                guild.id,
                channel.id,
                channel.name,
                getattr(getattr(channel, "category", None), "id", 0) or 0,
                slot,
            )

    async def assign_role(self, guild: discord.Guild, slot: str, role: discord.Role | None) -> None:
        key = {
            "admin": "admin_role_ids",
            "senior": "senior_role_ids",
            "mod": "mod_role_ids",
            "trainee": "trainee_role_ids",
        }.get(slot)
        if key is None:
            return
        current = [int(r) for r in await self.repo.settings.get(guild.id, key, []) or []]
        if role is not None and role.id not in current:
            current.append(role.id)
        elif role is None:
            current = []
        await self.repo.settings.set(guild.id, key, current)

    async def auto_discover_channels(self, guild: discord.Guild) -> dict[str, int]:
        """Ищет каналы категории «Кп Сервера» по названиям.

        Никаких «выдуманных» ID: канал берётся только если он реально
        существует на сервере. Возвращает {slot: channel_id}.
        """
        from config import CATEGORY_ALIASES, CHANNEL_SLOTS

        found: dict[str, int] = {}
        category_ids = {
            cat.id
            for cat in guild.categories
            if norm(cat.name) in {norm(a) for a in CATEGORY_ALIASES}
        }
        preferred = [
            ch
            for ch in guild.text_channels
            if getattr(ch, "category_id", None) in category_ids
        ] or list(guild.text_channels)

        for slot, meta in CHANNEL_SLOTS.items():
            aliases = {norm(a) for a in meta["aliases"]} | {norm(meta["canonical"])}
            channel = discord.utils.find(lambda c: norm(c.name) in aliases, preferred)
            if channel is None:
                # частичное совпадение: «отчеты-кп» ↔ «отчеты кп | kinggrief»
                canonical = norm(meta["canonical"])
                channel = discord.utils.find(
                    lambda c: canonical in norm(c.name) or norm(c.name) in canonical,
                    preferred,
                )
            if channel is None:
                continue
            found[slot] = channel.id
            await self.repo.settings.set(guild.id, slot, channel.id)
            await self.repo.cache.put_channel(
                guild.id,
                channel.id,
                channel.name,
                getattr(getattr(channel, "category", None), "id", 0) or 0,
                slot,
            )
            log.info("Канал «%s» → %s (id=%s)", meta["title"], channel.name, channel.id)

        for category in guild.categories:
            if norm(category.name) in {norm(a) for a in CATEGORY_ALIASES}:
                await self.repo.settings.set(guild.id, "reports_category_id", category.id)
                break
        return found

    @staticmethod
    def describe_assigned(channels: dict[str, int]) -> str:
        from config import CHANNEL_SLOTS

        lines = []
        for slot, meta in CHANNEL_SLOTS.items():
            mark = "✅" if channels.get(slot) else "⚠️"
            lines.append(f"{mark} {meta['emoji']} {meta['title']}: `{channels.get(slot) or 'не найден'}`")
        return "\n".join(lines)

    async def staff_roster(self, guild: discord.Guild) -> list[dict[str, Any]]:
        """Актуальный список персонала с количеством отчётов и варнов."""
        options = await self.repo.settings.get_many(guild.id)
        counts = await self.repo.reports.per_staff_counts(
            guild.id,
            period="week",
            week_start_day=str(options.get("week_start_day", "mon")),
            tz=str(options.get("timezone")),
        )
        roster = []
        for staff in await self.repo.staff.list(guild.id, active_only=True):
            roster.append(
                {
                    "user_id": staff.user_id,
                    "username": staff.username,
                    "position": staff.position,
                    "assigned_at": fmt_date(staff.assigned_at),
                    "week_reports": int(counts.get(staff.user_id, {}).get("count", 0)),
                    "warns": await self.repo.warnings.count(guild.id, staff.user_id),
                }
            )
        return roster

    async def next_assignment_label(self, guild: discord.Guild) -> str:
        """Краткая сводка для панели: сколько сотрудников и должностей."""
        breakdown = await self.repo.staff.positions_breakdown(guild.id)
        return "\n".join(f"• {name}: **{count}**" for name, count in breakdown.items()) or "—"


role_manager = RoleManager()


def controlled_role_names() -> list[str]:
    """Список контролируемых ролей по умолчанию (для справки/тестов)."""
    return [entry["name"] for entry in CONTROLLED_ROLES]


__all__ = [
    "RoleManager",
    "RoleRemovalResult",
    "norm",
    "role_manager",
    "controlled_role_names",
    "to_db",
    "now",
]
