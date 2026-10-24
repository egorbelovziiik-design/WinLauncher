#!/usr/bin/env python3
"""
ReallyWorld Multi-Bot Engine & CLI Entry Point.
Supports running one or many independent Discord bots concurrently with per-bot modules, status, and SQLite DB.
"""
from __future__ import annotations

import asyncio
import logging
import sys
import time
from collections.abc import Sequence

import discord
from discord.ext import commands

from bot.cogs.community import GiveawayPersistentView, VerifyPersistentView
from bot.cogs.kp_staff import CheatCheckControlView, KPApplyPersistentView, KPReviewPersistentView
from bot.cogs.tickets import TicketControlPersistentView, TicketCreatePersistentView
from bot.config import BotConfig, build_invite_url
from bot.database import BotDatabase

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
log = logging.getLogger("ReallyWorldBot")

COGS = (
    "bot.cogs.owner",
    "bot.cogs.kp_staff",
    "bot.cogs.tickets",
    "bot.cogs.moderation",
    "bot.cogs.community",
)


class ReallyWorldBot(commands.Bot):
    def __init__(
        self,
        cfg: BotConfig,
        *,
        enabled_cogs: Sequence[str] | None = None,
        status_type: str = "playing",
        status_text: str | None = None,
    ) -> None:
        intents = discord.Intents.default()
        intents.members = True
        intents.message_content = True
        intents.guilds = True

        super().__init__(
            command_prefix="!",
            intents=intents,
            owner_id=cfg.owner_id,
            help_command=None,
        )
        self.cfg = cfg
        self.db = BotDatabase(cfg.db_path)
        self.enabled_cogs = tuple(enabled_cogs) if enabled_cogs is not None else COGS
        self.status_type = status_type
        self.status_text = status_text or f"{cfg.minecraft_ip} | /help"
        self.started_at = time.time()
        self._avatar_checked = False

    async def setup_hook(self) -> None:
        await self.db.connect()
        for ext in self.enabled_cogs:
            if ext in COGS:
                await self.load_extension(ext)
                log.info("Загружен модуль [%s]: %s", self.cfg.project_name, ext)

        if "bot.cogs.kp_staff" in self.enabled_cogs:
            self.add_view(KPApplyPersistentView(self))
            self.add_view(KPReviewPersistentView(self))
            self.add_view(CheatCheckControlView(self))
        if "bot.cogs.tickets" in self.enabled_cogs:
            self.add_view(TicketCreatePersistentView(self))
            self.add_view(TicketControlPersistentView(self))
        if "bot.cogs.community" in self.enabled_cogs:
            self.add_view(VerifyPersistentView(self))
            self.add_view(GiveawayPersistentView(self))

        if self.cfg.dev_guild_id:
            dev_guild = discord.Object(id=self.cfg.dev_guild_id)
            self.tree.copy_global_to(guild=dev_guild)
            await self.tree.sync(guild=dev_guild)
        await self.tree.sync()

    def _build_activity(self) -> discord.BaseActivity:
        text = self.status_text.strip() or "/help"
        if self.status_type == "watching":
            return discord.Activity(type=discord.ActivityType.watching, name=text)
        if self.status_type == "listening":
            return discord.Activity(type=discord.ActivityType.listening, name=text)
        return discord.Game(name=text)

    async def on_ready(self) -> None:
        assert self.user is not None
        invite_url = build_invite_url(self.user.id, permissions=8)
        banner = (
            "\n" + "=" * 76 + "\n"
            f"  БОТ ЗАПУЩЕН: {self.user} (ID: {self.user.id})\n"
            f"  ВЛАДЕЛЕЦ: {self.cfg.owner_username} (Discord ID: {self.cfg.owner_id})\n"
            f"  ПРЯМАЯ ССЫЛКА ПРИГЛАШЕНИЯ:\n"
            f"  >>> {invite_url} <<<\n" + "=" * 76
        )
        print(banner, flush=True)

        await self.change_presence(status=discord.Status.online, activity=self._build_activity())

        if not self._avatar_checked and self.cfg.auto_set_kp_avatar:
            self._avatar_checked = True
            if self.user.avatar is None and self.cfg.avatar_path.exists():
                try:
                    await self.user.edit(avatar=self.cfg.avatar_path.read_bytes())
                    log.info("Аватарка (%s) автоматически установлена на бота %s!", self.cfg.avatar_path.name, self.user)
                except discord.HTTPException as exc:
                    log.warning("Не удалось автоматически установить аватарку при старте: %s", exc)

    async def close(self) -> None:
        comm = self.get_cog("CommunityCog")
        if comm is not None:
            comm.cog_unload()
        await self.db.close()
        await super().close()


async def main() -> None:
    cfg = BotConfig.load()
    if cfg.client_id.isdigit():
        print(f"\n[ССЫЛКА ПРИГЛАШЕНИЯ БОТА]:\n{build_invite_url(cfg.client_id, permissions=8)}\n", flush=True)

    if not cfg.token or cfg.token == "PASTE_YOUR_BOT_TOKEN_HERE":
        print(
            "\n[!] ВНИМАНИЕ: Токен бота не указан в .env!\n"
            "Для запуска НЕСКОЛЬКИХ ботов через графическое окно используйте gui_app.py или DiscordBotHub.exe.\n",
            file=sys.stderr,
        )
        sys.exit(1)

    bot = ReallyWorldBot(cfg)
    async with bot:
        await bot.start(cfg.token)


if __name__ == "__main__":
    asyncio.run(main())
