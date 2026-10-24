#!/usr/bin/env python3
"""
ReallyWorld Ultimate & КП (Команда Проекта) — Готовый Discord-бот.
Главный владелец по умолчанию: robik0277 (ID: 1381255368650526861)
"""
from __future__ import annotations

import asyncio
import logging
import sys
import time

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
    def __init__(self, cfg: BotConfig) -> None:
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
        self.started_at = time.time()
        self._avatar_checked = False

    async def setup_hook(self) -> None:
        await self.db.connect()
        for ext in COGS:
            await self.load_extension(ext)
            log.info("Загружен модуль: %s", ext)

        # Регистрируем вечные кнопки и выпадающие меню (работают даже после перезагрузки бота!)
        self.add_view(KPApplyPersistentView(self))
        self.add_view(KPReviewPersistentView(self))
        self.add_view(CheatCheckControlView(self))
        self.add_view(TicketCreatePersistentView(self))
        self.add_view(TicketControlPersistentView(self))
        self.add_view(VerifyPersistentView(self))
        self.add_view(GiveawayPersistentView(self))

        if self.cfg.dev_guild_id:
            dev_guild = discord.Object(id=self.cfg.dev_guild_id)
            self.tree.copy_global_to(guild=dev_guild)
            await self.tree.sync(guild=dev_guild)
            log.info("Слэш-команды мгновенно синхронизированы для сервера ID %s!", self.cfg.dev_guild_id)
        await self.tree.sync()
        log.info("Глобальное дерево слэш-команд успешно синхронизировано.")

    async def on_ready(self) -> None:
        assert self.user is not None
        invite_url = build_invite_url(self.user.id, permissions=8)
        banner = (
            "\n" + "=" * 76 + "\n"
            f"  БОТ ЗАПУЩЕН: {self.user} (ID: {self.user.id})\n"
            f"  ГЛАВНЫЙ ВЛАДЕЛЕЦ: {self.cfg.owner_username} (Discord ID: {self.cfg.owner_id})\n"
            f"  ПРЯМАЯ ССЫЛКА ПРИГЛАШЕНИЯ В ВАШ DISCORD:\n"
            f"  >>> {invite_url} <<<\n" + "=" * 76
        )
        print(banner, flush=True)

        await self.change_presence(
            status=discord.Status.online,
            activity=discord.Game(name=f"{self.cfg.minecraft_ip} | /help"),
        )

        # Автоматическая установка аватарки КП при первом запуске (если у бота ещё стандартная аватарка)
        if not self._avatar_checked and self.cfg.auto_set_kp_avatar:
            self._avatar_checked = True
            if self.user.avatar is None and self.cfg.avatar_path.exists():
                try:
                    await self.user.edit(avatar=self.cfg.avatar_path.read_bytes())
                    log.info("Фирменная аватарка КП (avatar_kp_beach.png) автоматически установлена на бота!")
                except discord.HTTPException as exc:
                    log.warning("Не удалось автоматически установить аватарку при старте: %s", exc)

    async def close(self) -> None:
        await self.db.close()
        await super().close()


async def main() -> None:
    cfg = BotConfig.load()
    if cfg.client_id.isdigit():
        print(
            f"\n[ССЫЛКА ПРИГЛАШЕНИЯ БОТА]:\n{build_invite_url(cfg.client_id, permissions=8)}\n",
            flush=True,
        )

    if not cfg.token or cfg.token == "PASTE_YOUR_BOT_TOKEN_HERE":
        print(
            "\n[!] ВНИМАНИЕ: Токен бота ещё не указан!\n"
            "1. Откройте файл .env (скопируйте из .env.example или скачайте через invite-generator.html)\n"
            "2. Вставьте ваш токен с https://discord.com/developers/applications в строку DISCORD_TOKEN=...\n"
            f"3. Владелец бота уже закреплён за аккаунтом: {cfg.owner_username} (ID: {cfg.owner_id})\n",
            file=sys.stderr,
        )
        sys.exit(1)

    bot = ReallyWorldBot(cfg)
    async with bot:
        await bot.start(cfg.token)


if __name__ == "__main__":
    asyncio.run(main())
