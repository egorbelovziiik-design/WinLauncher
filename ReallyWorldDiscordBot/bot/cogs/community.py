"""Verification, Giveaways, Server Info (/server, /rules) & Chat Activity Economy."""
from __future__ import annotations

import random
import time
from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands, tasks

from bot.utils.permissions import (
    COLOR_GOLD,
    COLOR_INFO,
    COLOR_ORANGE,
    COLOR_SUCCESS,
    is_staff_member,
    rw_embed,
)

if TYPE_CHECKING:
    from main import ReallyWorldBot


class VerifyPersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.bot = bot

    @discord.ui.button(label="✅ Пройти верификацию", style=discord.ButtonStyle.success, custom_id="rw_verify_btn")
    async def verify_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        role_id = settings.get("verify_role_id")
        if not role_id:
            await interaction.response.send_message(
                "⚠️ Роль верификации ещё не настроена администратором (`/setup config verify_role:...`).",
                ephemeral=True,
            )
            return
        role = interaction.guild.get_role(role_id)
        if not role:
            await interaction.response.send_message("❌ Указанная роль не найдена на сервере.", ephemeral=True)
            return
        if role in interaction.user.roles:
            await interaction.response.send_message("✅ Вы уже верифицированы на сервере!", ephemeral=True)
            return
        try:
            await interaction.user.add_roles(role, reason="Прохождение верификации")
            await interaction.response.send_message(
                f"🎉 Добро пожаловать на **{self.bot.cfg.project_name}**! Вам выдана роль {role.mention}.",
                ephemeral=True,
            )
        except discord.HTTPException as exc:
            await interaction.response.send_message(f"❌ Не удалось выдать роль (проверьте иерархию роли бота): `{exc}`", ephemeral=True)


class GiveawayPersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.bot = bot

    @discord.ui.button(label="🎉 Участвовать", style=discord.ButtonStyle.primary, custom_id="rw_giveaway_join_btn")
    async def join_btn(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        if not interaction.message:
            return
        gw = await self.bot.db.get_giveaway_by_message(interaction.message.id)
        if not gw or gw["ended"]:
            await interaction.response.send_message("⚠️ Этот розыгрыш уже завершён!", ephemeral=True)
            return
        joined, count = await self.bot.db.toggle_giveaway_entry(gw["id"], interaction.user.id)
        button.label = f"🎉 Участвовать ({count})"
        try:
            await interaction.message.edit(view=self)
        except discord.HTTPException:
            pass
        msg = "✅ Вы зарегистрированы в розыгрыше! Удачи!" if joined else "ℹ️ Вы вышли из списка участников розыгрыша."
        await interaction.response.send_message(msg, ephemeral=True)


class CommunityCog(commands.Cog):
    verify_group = app_commands.Group(name="verify", description="Система верификации участников")
    giveaway_group = app_commands.Group(name="giveaway", description="Розыгрыши Рилликов и привилегий")

    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot
        self._chat_cooldowns: dict[tuple[int, int], float] = {}
        self.giveaway_watcher.start()

    def cog_unload(self) -> None:
        self.giveaway_watcher.cancel()

    @tasks.loop(seconds=15)
    async def giveaway_watcher(self) -> None:
        due = await self.bot.db.get_due_giveaways()
        for gw in due:
            participants = await self.bot.db.finish_giveaway(gw["id"])
            guild = self.bot.get_guild(gw["guild_id"])
            if not guild:
                continue
            chan = guild.get_channel(gw["channel_id"])
            if not isinstance(chan, discord.TextChannel):
                continue

            if participants:
                winners = random.sample(participants, k=min(gw["winners_count"], len(participants)))
                mentions = ", ".join(f"<@{uid}>" for uid in winners)
                result_text = f"🏆 **Победители:** {mentions}\n🎁 **Приз:** **{gw['prize']}**"
            else:
                mentions = ""
                result_text = "В розыгрыше никто не принял участия."

            if gw.get("message_id"):
                try:
                    msg = await chan.fetch_message(gw["message_id"])
                    emb = rw_embed(f"🎉 Розыгрыш завершён — {gw['prize']}", result_text, COLOR_SUCCESS)
                    await msg.edit(embed=emb, view=None)
                except discord.HTTPException:
                    pass
            await chan.send(content=mentions or None, embed=rw_embed("🎊 Итоги розыгрыша!", result_text, COLOR_GOLD))

    @giveaway_watcher.before_loop
    async def _before_watcher(self) -> None:
        await self.bot.wait_until_ready()

    @verify_group.command(name="setup", description="Опубликовать плашку верификации с кнопкой")
    async def verify_setup(self, interaction: discord.Interaction, channel: discord.TextChannel | None = None) -> None:
        if not isinstance(interaction.user, discord.Member) or not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        target = channel or interaction.channel
        if not isinstance(target, discord.TextChannel):
            return
        emb = rw_embed(
            "✅ Верификация на сервере ReallyWorld",
            "Добро пожаловать в официальный Discord-сервер!\n\n"
            "Нажмите зелёную кнопку **«✅ Пройти верификацию»** ниже, чтобы подтвердить ознакомление с правилами и открыть доступ ко всем чатам и каналам сервера.",
            COLOR_SUCCESS,
        )
        await target.send(embed=emb, view=VerifyPersistentView(self.bot))
        await interaction.response.send_message(f"✅ Панель верификации отправлена в {target.mention}!", ephemeral=True)

    @giveaway_group.command(name="start", description="Запустить честный розыгрыш привилегий / Рилликов")
    async def giveaway_start(
        self,
        interaction: discord.Interaction,
        prize: str,
        minutes: app_commands.Range[int, 1, 10080],
        winners: app_commands.Range[int, 1, 20] = 1,
        channel: discord.TextChannel | None = None,
    ) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        target = channel or interaction.channel
        if not isinstance(target, discord.TextChannel):
            return

        ends_at = int(time.time()) + minutes * 60
        gw_id = await self.bot.db.create_giveaway(
            interaction.guild.id, target.id, prize, winners, interaction.user.id, ends_at
        )
        emb = rw_embed(
            f"🎁 РОЗЫГРЫШ: {prize}",
            f"**Организатор:** {interaction.user.mention}\n"
            f"**Количество победителей:** `{winners}`\n"
            f"**Завершение:** <t:{ends_at}:R> (<t:{ends_at}:f>)\n\n"
            "👇 Нажмите **«🎉 Участвовать»**, чтобы испытать удачу!",
            COLOR_ORANGE,
        )
        msg = await target.send(embed=emb, view=GiveawayPersistentView(self.bot))
        await self.bot.db.set_giveaway_message(gw_id, msg.id)
        await interaction.response.send_message(f"✅ Розыгрыш **#{gw_id}** запущен в {target.mention}!", ephemeral=True)

    @app_commands.command(name="server", description="Информация об IP и версиях сервера ReallyWorld")
    async def server_cmd(self, interaction: discord.Interaction) -> None:
        emb = rw_embed(
            f"🔥 {self.bot.cfg.project_name}",
            f"🎮 **IP для входа:** `{self.bot.cfg.minecraft_ip}`\n"
            f"📦 **Поддерживаемые версии:** `{self.bot.cfg.minecraft_versions}` (Java Edition)\n"
            f"👑 **Главный владелец бота:** `{self.bot.cfg.owner_username}` (<@{self.bot.cfg.owner_id}>)",
            COLOR_GOLD,
        )
        await interaction.response.send_message(embed=emb)

    @app_commands.command(name="rules", description="Основные правила Discord-сервера и проекта")
    async def rules_cmd(self, interaction: discord.Interaction) -> None:
        emb = rw_embed(
            "📜 Основные Правила Проекта",
            "**1.1.** Запрещены оскорбления участников, провокации, токсичное поведение и розжиг.\n"
            "**1.2.** Строго запрещена реклама сторонних проектов и рассылка ссылок.\n"
            "**1.3.** Запрещено использование читов, макросов, дюпов и обход проверок КП.\n"
            "**1.4.** Запрещён обман администрации и ложные вызовы сотрудников Команды Проекта.\n"
            "**1.5.** Решения Куратора КП и Владельца проекта являются окончательными.",
            COLOR_GOLD,
        )
        await interaction.response.send_message(embed=emb)

    @app_commands.command(name="help", description="Полный справочник команд бота ReallyWorld КП")
    async def help_cmd(self, interaction: discord.Interaction) -> None:
        emb = rw_embed("📖 Команды бота ReallyWorld КП", color=COLOR_GOLD)
        emb.add_field(
            name="👑 Владелец и Настройка",
            value="`/owner panel`, `/owner invite`, `/owner status`, `/owner broadcast`, `/owner coowner-add`, `/setup config`, `/setup view`",
            inline=False,
        )
        emb.add_field(
            name="🛡️ Команда Проекта (КП)",
            value="`/kp setup-apply`, `/kp profile`, `/kp points`, `/kp strike`, `/kp top`, `/check start`",
            inline=False,
        )
        emb.add_field(
            name="🎫 Тикеты и Модерация",
            value="`/tickets setup`, `/ban`, `/unban`, `/kick`, `/mute`, `/unmute`, `/warn`, `/warns`, `/unwarn`, `/clear`, `/lock`, `/unlock`",
            inline=False,
        )
        emb.add_field(
            name="🎉 Сообщество и Активность",
            value="`/verify setup`, `/giveaway start`, `/server`, `/rules`, `/bal`, `/daily`, `/top`",
            inline=False,
        )
        await interaction.response.send_message(embed=emb, ephemeral=True)

    @app_commands.command(name="bal", description="Проверить свой баланс монет активности в Discord")
    async def bal_cmd(self, interaction: discord.Interaction, member: discord.Member | None = None) -> None:
        if not interaction.guild:
            return
        target = member or interaction.user
        p = await self.bot.db.get_economy_profile(interaction.guild.id, target.id)
        emb = rw_embed(
            f"💰 Профиль активности — {target.display_name}",
            f"Монеты чата: **`{p['coins']} ⛃`**\nСообщений написано: **`{p['messages_count']}`**",
            COLOR_GOLD,
        )
        await interaction.response.send_message(embed=emb)

    @app_commands.command(name="daily", description="Получить ежедневный бонус монет активности")
    async def daily_cmd(self, interaction: discord.Interaction) -> None:
        if not interaction.guild:
            return
        reward = int(self.bot.cfg.raw_json.get("economy", {}).get("daily_reward", 250))
        ok, wait_sec = await self.bot.db.claim_daily(interaction.guild.id, interaction.user.id, reward)
        if not ok:
            hours, rem = divmod(wait_sec, 3600)
            mins = rem // 60
            await interaction.response.send_message(f"⏳ Вы уже забирали бонус! Подождите ещё `{hours}ч {mins}м`.", ephemeral=True)
            return
        await interaction.response.send_message(
            embed=rw_embed("🎁 Ежедневная награда!", f"Вам начислено **`+{reward} ⛃`** за ежедневную активность!", COLOR_SUCCESS)
        )

    @app_commands.command(name="top", description="Топ-10 самых активных участников Discord-сервера")
    async def top_cmd(self, interaction: discord.Interaction) -> None:
        if not interaction.guild:
            return
        rows = await self.bot.db.get_economy_top(interaction.guild.id, 10)
        if not rows:
            await interaction.response.send_message("Рейтинг активности пока пуст.", ephemeral=True)
            return
        lines = [f"**{i}.** <@{r['user_id']}> — **`{r['coins']} ⛃`** (`{r['messages_count']}` сообщ.)" for i, r in enumerate(rows, 1)]
        await interaction.response.send_message(embed=rw_embed("🏆 Топ активности чата", "\n".join(lines), COLOR_INFO))

    @commands.Cog.listener()
    async def on_message(self, message: discord.Message) -> None:
        if message.author.bot or not message.guild or len(message.content.strip()) < 4:
            return
        eco_cfg = self.bot.cfg.raw_json.get("economy", {})
        cd = int(eco_cfg.get("chat_cooldown_seconds", 45))
        key = (message.guild.id, message.author.id)
        now = time.time()
        if now - self._chat_cooldowns.get(key, 0.0) < cd:
            return
        self._chat_cooldowns[key] = now
        gain = random.randint(int(eco_cfg.get("chat_reward_min", 5)), int(eco_cfg.get("chat_reward_max", 15)))
        await self.bot.db.add_chat_reward(message.guild.id, message.author.id, gain, is_message=True)


async def setup(bot: "ReallyWorldBot") -> None:
    await bot.add_cog(CommunityCog(bot))
