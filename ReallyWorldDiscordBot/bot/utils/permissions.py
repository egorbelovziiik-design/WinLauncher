"""Permission checks & ReallyWorld embed helpers."""
from __future__ import annotations

from datetime import datetime, timezone
from typing import TYPE_CHECKING

import discord

if TYPE_CHECKING:
    from main import ReallyWorldBot

COLOR_GOLD = 0xFFAA00
COLOR_ORANGE = 0xFF6B00
COLOR_SUCCESS = 0x2ECC71
COLOR_ERROR = 0xE74C3C
COLOR_INFO = 0x3498DB


def rw_embed(title: str, description: str = "", color: int = COLOR_GOLD) -> discord.Embed:
    emb = discord.Embed(
        title=title,
        description=description,
        color=color,
        timestamp=datetime.now(timezone.utc),
    )
    emb.set_footer(text="ReallyWorld | Команда Проекта (КП)")
    return emb


async def is_supreme_owner(bot: "ReallyWorldBot", user: discord.abc.User) -> bool:
    """True strictly for robik0277 (1381255368650526861) or Discord App Owner."""
    if user.id == bot.cfg.owner_id:
        return True
    return await bot.is_owner(user)


async def is_bot_admin(bot: "ReallyWorldBot", user: discord.abc.User) -> bool:
    """True for supreme owner (1381255368650526861) or trusted co-owner in DB."""
    if await is_supreme_owner(bot, user):
        return True
    return await bot.db.is_co_owner(user.id)


async def is_staff_member(bot: "ReallyWorldBot", member: discord.Member) -> bool:
    """True for bot owner, server Administrator, Manage Messages/Moderation, or configured КП role."""
    if await is_bot_admin(bot, member):
        return True
    if member.guild_permissions.administrator or member.guild_permissions.moderate_members:
        return True
    settings = await bot.db.get_guild_settings(member.guild.id)
    staff_role_id = settings.get("staff_role_id")
    if staff_role_id and any(r.id == staff_role_id for r in member.roles):
        return True
    return False
