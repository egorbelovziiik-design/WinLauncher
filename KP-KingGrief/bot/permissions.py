"""
Гибкая система прав КП-KingGrief.

Уровни (по убыванию):
  OWNER  — владелец бота/сервера и роли из `admin_role_ids`;
  ADMIN  — Админ, Гл.Модер;
  SENIOR — Ст.Модер (просмотр отчётов/персонала/варнов);
  MOD    — Модер, Мл.Модер (свой профиль, свои отчёты и варны);
  STAFF  — Стажер (свой профиль и свои отчёты);
  NONE   — обычный участник.

Все уровни определяются по Discord ID ролей из настроек сервера, поэтому
права настраиваются командой /settings без правки кода.
"""

from __future__ import annotations

import functools
from enum import IntEnum
from typing import Any, Callable

import discord
from discord import app_commands

from config import Colors
from database.models import Repository, repo as _global_repo
from utils import embeds as E
from utils.logging import get_logger

log = get_logger("permissions")


class Level(IntEnum):
    NONE = 0
    STAFF = 10
    MOD = 30
    SENIOR = 50
    ADMIN = 70
    OWNER = 100

    @property
    def label(self) -> str:
        return {
            Level.NONE: "Участник",
            Level.STAFF: "Стажер",
            Level.MOD: "Модератор",
            Level.SENIOR: "Старший модератор",
            Level.ADMIN: "Администратор",
            Level.OWNER: "Главный администратор",
        }[Level(self)]


# Минимальный уровень для каждой команды.
COMMAND_LEVELS: dict[str, Level] = {
    "ot": Level.MOD,
    "warn": Level.ADMIN,
    "addwarn": Level.ADMIN,
    "unwarn": Level.ADMIN,
    "warns": Level.MOD,
    "clearwarns": Level.ADMIN,
    "staff": Level.MOD,
    "profile": Level.STAFF,
    "stats": Level.MOD,
    "history": Level.SENIOR,
    "check": Level.MOD,
    "top": Level.MOD,
    "setup": Level.ADMIN,
    "settings": Level.ADMIN,
    "sync": Level.SENIOR,
    "help": Level.NONE,
    "panel": Level.MOD,
    "report": Level.MOD,
    "reports": Level.STAFF,
}


class PermissionDenied(app_commands.AppCommandError):
    """Недостаточно прав для выполнения команды."""

    def __init__(self, message: str = "Недостаточно прав.", *, command: str = "") -> None:
        super().__init__(message)
        self.command = command


class PermissionManager:
    """Определяет уровень доступа участника и проверяет права команд."""

    def __init__(self, repository: Repository | None = None) -> None:
        self.repo = repository or _global_repo
        self._cache: dict[int, tuple[float, Level]] = {}

    # ------------------------------------------------------------------ #
    async def role_buckets(self, guild_id: int) -> dict[str, set[int]]:
        options = await self.repo.settings.get_many(guild_id)

        def ids(key: str) -> set[int]:
            return {int(x) for x in (options.get(key) or []) if int(x or 0)}

        return {
            "admin": ids("admin_role_ids"),
            "senior": ids("senior_role_ids"),
            "mod": ids("mod_role_ids"),
            "trainee": ids("trainee_role_ids"),
        }

    async def level(self, guild: discord.Guild | None, member: discord.Member | discord.User) -> Level:
        """Уровень доступа участника."""
        if guild is None:
            return Level.NONE

        owner_id = int(await self.repo.settings.get(guild.id, "owner_id", 0) or 0)
        if owner_id and member.id == owner_id:
            return Level.OWNER
        if getattr(guild, "owner_id", None) == member.id:
            return Level.OWNER

        buckets = await self.role_buckets(guild.id)
        role_ids = {role.id for role in getattr(member, "roles", []) or []}

        if role_ids & buckets["admin"]:
            return Level.ADMIN
        perms = getattr(member, "guild_permissions", None)
        if perms is not None and bool(getattr(perms, "administrator", False)):
            return Level.ADMIN
        if role_ids & buckets["senior"]:
            return Level.SENIOR
        if role_ids & buckets["mod"]:
            return Level.MOD
        if role_ids & buckets["trainee"]:
            return Level.STAFF

        staff = await self.repo.staff.get(guild.id, member.id)
        if staff and staff.is_active:
            return Level.STAFF
        return Level.NONE

    async def required(self, command: str) -> Level:
        return COMMAND_LEVELS.get(command, Level.MOD)

    async def can(
        self,
        guild: discord.Guild | None,
        member: discord.Member | discord.User,
        command: str,
    ) -> bool:
        need = await self.required(command)
        if need is Level.NONE:
            return True
        return await self.level(guild, member) >= need

    # ------------------------------------------------------------------ #
    def can_view_target(
        self, level: Level, actor_id: int, target_id: int | None
    ) -> bool:
        """Может ли участник смотреть данные другого пользователя.

        По ТЗ Модер и Мл.Модер видят только СВОИ данные; чужие профили,
        отчёты и варны доступны начиная со старшего состава.
        """
        if level >= Level.SENIOR:
            return True
        return target_id is None or target_id == actor_id

    async def describe(self, guild: discord.Guild, member: discord.Member) -> str:
        level = await self.level(guild, member)
        buckets = await self.role_buckets(guild.id)
        lines = [
            f"👤 Пользователь: {member.mention}",
            f"🛡️ Уровень: **{level.label}** ({int(level)})",
            "",
            "🔐 Разрешения:",
        ]
        grants = {
            Level.OWNER: [
                "Управление ботом",
                "Выдача и снятие предупреждений",
                "Изменение настроек",
                "Просмотр всех отчётов",
                "Подтверждение снятия ролей",
            ],
            Level.ADMIN: [
                "Выдача и снятие предупреждений",
                "Просмотр всех отчётов",
                "Подтверждение снятия ролей",
                "Настройка каналов и ролей",
            ],
            Level.SENIOR: [
                "Просмотр отчётов",
                "Просмотр персонала",
                "Просмотр предупреждений",
                "Синхронизация ролей и каналов",
            ],
            Level.MOD: [
                "Просмотр своего профиля",
                "Просмотр своих отчётов",
                "Просмотр своих предупреждений",
            ],
            Level.STAFF: ["Просмотр своего профиля", "Просмотр своих отчётов"],
            Level.NONE: ["Доступ к публичной справке /help"],
        }
        lines += [f"• {item}" for item in grants[level]]
        lines += [
            "",
            f"🎭 Ролей администратора настроено: {len(buckets['admin'])}",
            f"🎭 Ролей старшего состава: {len(buckets['senior'])}",
            f"🎭 Ролей модераторов: {len(buckets['mod'])}",
            f"🎭 Ролей стажеров: {len(buckets['trainee'])}",
        ]
        return "\n".join(lines)


permissions = PermissionManager()


# --------------------------------------------------------------------------- #
#  Декоратор проверки прав
# --------------------------------------------------------------------------- #
def require(command: str | None = None, level: Level | None = None) -> Callable:
    """Декоратор slash-команды: проверяет права до выполнения.

    При отказе отправляет русский Embed с ошибкой и прерывает выполнение.
    """

    def decorator(func: Callable) -> Callable:
        name = command or getattr(func, "__name__", "")
        needed = level or COMMAND_LEVELS.get(name, Level.MOD)

        @functools.wraps(func)
        async def wrapper(self, interaction: discord.Interaction, *args: Any, **kwargs: Any):
            need = needed
            guild = interaction.guild
            member = interaction.user

            if guild is None:
                embed = E.error("Команда недоступна", "Эта команда работает только на сервере.")
                return await _respond(interaction, embed)

            current = await permissions.level(guild, member)
            if need is not Level.NONE and current < need:
                log.warning(
                    "Отказ в доступе: %s (%s) → /%s (нужен %s)",
                    member,
                    current.label,
                    name,
                    need.label,
                )
                await permissions.repo.audit.add(
                    guild.id,
                    "permission_denied",
                    actor_id=member.id,
                    detail=f"Попытка использовать /{name} (уровень: {current.label})",
                )
                embed = E.error(
                    "Недостаточно прав",
                    f"Для команды `/{name}` нужен уровень **{need.label}**.\n"
                    f"Ваш уровень: **{current.label}**.",
                )
                return await _respond(interaction, embed)

            try:
                return await func(self, interaction, *args, **kwargs)
            except discord.Forbidden:
                embed = E.error(
                    "Недостаточно прав у бота",
                    "Боту не хватает прав Discord для этого действия.",
                )
                return await _respond(interaction, embed)
            except app_commands.CommandError as exc:
                embed = E.error("Ошибка команды", str(exc) or "Неизвестная ошибка.")
                return await _respond(interaction, embed)

        wrapper.__kp_requires__ = needed  # type: ignore[attr-defined]
        return wrapper

    return decorator


async def _respond(interaction: discord.Interaction, embed: discord.Embed) -> None:
    """Отвечает на взаимодействие с учётом уже отправленного ответа."""
    try:
        if interaction.response.is_done():
            await interaction.followup.send(embed=embed, ephemeral=True)
        else:
            await interaction.response.send_message(embed=embed, ephemeral=True)
    except discord.HTTPException as exc:  # pragma: no cover
        log.error("Не удалось отправить сообщение об ошибке: %s", exc)


def level_color(level: Level) -> int:
    """Цвет индикатора уровня прав."""
    return {
        Level.OWNER: Colors.SUCCESS,
        Level.ADMIN: Colors.SUCCESS,
        Level.SENIOR: Colors.INFO,
        Level.MOD: Colors.WARNING,
        Level.STAFF: Colors.NEUTRAL,
        Level.NONE: Colors.ERROR,
    }.get(level, Colors.NEUTRAL)
