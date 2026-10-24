"""Persistent Multi-Category Ticket System with TXT Transcripts."""
from __future__ import annotations

import io
from datetime import datetime, timezone
from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands

from bot.utils.permissions import (
    COLOR_ERROR,
    COLOR_GOLD,
    COLOR_INFO,
    COLOR_SUCCESS,
    is_staff_member,
    rw_embed,
)

if TYPE_CHECKING:
    from main import ReallyWorldBot


class TicketTopicModal(discord.ui.Modal):
    topic = discord.ui.TextInput(
        label="Ваш ник в Minecraft и суть обращения",
        style=discord.TextStyle.paragraph,
        placeholder="Ник: ...\nОпишите проблему максимально подробно...",
        min_length=10,
        max_length=800,
    )

    def __init__(self, bot: "ReallyWorldBot", category_label: str) -> None:
        super().__init__(title=f"Тикет — {category_label}")
        self.bot = bot
        self.category_label = category_label

    async def on_submit(self, interaction: discord.Interaction) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return

        existing = await self.bot.db.get_open_ticket(interaction.guild.id, interaction.user.id, self.category_label)
        if existing:
            await interaction.response.send_message(
                f"⚠️ У вас уже открыт тикет в этой категории: <#{existing['channel_id']}>!",
                ephemeral=True,
            )
            return

        await interaction.response.defer(ephemeral=True)
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        cat_obj = interaction.guild.get_channel(settings["ticket_category_id"]) if settings.get("ticket_category_id") else None

        overwrites: dict[discord.Role | discord.Member, discord.PermissionOverwrite] = {
            interaction.guild.default_role: discord.PermissionOverwrite(view_channel=False),
            interaction.user: discord.PermissionOverwrite(
                view_channel=True, send_messages=True, attach_files=True, embed_links=True, read_message_history=True
            ),
        }
        staff_ping = ""
        if settings.get("staff_role_id"):
            srole = interaction.guild.get_role(settings["staff_role_id"])
            if srole:
                overwrites[srole] = discord.PermissionOverwrite(
                    view_channel=True, send_messages=True, manage_messages=True, read_message_history=True
                )
                staff_ping = srole.mention

        safe_name = "".join(c for c in interaction.user.name.lower() if c.isalnum())[:14] or "user"
        channel = await interaction.guild.create_text_channel(
            name=f"тикет-{safe_name}",
            category=cat_obj if isinstance(cat_obj, discord.CategoryChannel) else None,
            overwrites=overwrites,
            reason=f"Тикет ({self.category_label}) от {interaction.user}",
        )

        ticket_id = await self.bot.db.create_ticket(
            interaction.guild.id, channel.id, interaction.user.id, self.category_label, self.topic.value.strip()
        )
        emb = rw_embed(
            f"🎫 Обращение #{ticket_id} — {self.category_label}",
            f"**Автор:** {interaction.user.mention} (`{interaction.user.id}`)\n"
            f"**Описание обращения:**\n>>> {self.topic.value.strip()}\n\n"
            "Сотрудник **Команды Проекта** ответит вам в ближайшее время. Пожалуйста, не тегайте персонал без необходимости.",
            COLOR_GOLD,
        )
        await channel.send(
            content=f"{interaction.user.mention} {staff_ping}".strip(),
            embed=emb,
            view=TicketControlPersistentView(self.bot),
        )
        await interaction.followup.send(f"✅ Ваш тикет создан: {channel.mention}", ephemeral=True)


class TicketControlPersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.bot = bot

    @discord.ui.button(label="🙋 Взять тикет (КП)", style=discord.ButtonStyle.primary, custom_id="rw_ticket_claim_btn")
    async def claim_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not isinstance(interaction.user, discord.Member) or not interaction.channel:
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Взять тикет может только сотрудник КП.", ephemeral=True)
            return
        claimed = await self.bot.db.claim_ticket(interaction.channel.id, interaction.user.id)
        if not claimed:
            await interaction.response.send_message("⚠️ Этот тикет уже взят другим сотрудником КП!", ephemeral=True)
            return
        await interaction.response.send_message(
            embed=rw_embed("🛡️ Тикет принят в работу", f"Вашим обращением занимается сотрудник КП {interaction.user.mention}!", COLOR_INFO)
        )

    @discord.ui.button(label="🔒 Закрыть с отчётом", style=discord.ButtonStyle.danger, custom_id="rw_ticket_close_btn")
    async def close_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not isinstance(interaction.user, discord.Member) or not interaction.guild or not isinstance(interaction.channel, discord.TextChannel):
            return
        ticket = await self.bot.db.get_ticket_by_channel(interaction.channel.id)
        if not ticket:
            await interaction.response.send_message("❌ Этот канал не является активным тикетом.", ephemeral=True)
            return
        is_staff = await is_staff_member(self.bot, interaction.user)
        if not is_staff and interaction.user.id != ticket["opener_id"]:
            await interaction.response.send_message("❌ Нет прав для закрытия чужого тикета.", ephemeral=True)
            return

        await interaction.response.send_message("🔒 Сохраняю лог переписки и закрываю тикет...")
        await self.bot.db.close_ticket(interaction.channel.id)

        if is_staff:
            pts = int(self.bot.cfg.raw_json.get("kp_settings", {}).get("points_per_ticket", 2))
            await self.bot.db.modify_kp_stats(interaction.guild.id, interaction.user.id, points_delta=pts, tickets_delta=1)

        # Build clean text transcript
        lines = [
            f"=== ТРАНСКРИПТ ТИКЕТА #{ticket['id']} ({ticket['category']}) ===",
            f"Сервер: {interaction.guild.name}",
            f"Автор ID: {ticket['opener_id']} | Закрыл: {interaction.user} ({interaction.user.id})",
            f"Дата закрытия (UTC): {datetime.now(timezone.utc).isoformat()}",
            "=" * 64,
        ]
        async for msg in interaction.channel.history(limit=300, oldest_first=True):
            ts = msg.created_at.strftime("%Y-%m-%d %H:%M:%S")
            body = msg.clean_content or "[Вложение / Embed]"
            lines.append(f"[{ts}] {msg.author}: {body}")

        transcript_bytes = "\n".join(lines).encode("utf-8")
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        log_cid = settings.get("ticket_log_channel_id") or settings.get("log_channel_id")
        log_chan = interaction.guild.get_channel(log_cid) if log_cid else None
        if isinstance(log_chan, discord.TextChannel):
            emb = rw_embed(f"🗂 Тикет #{ticket['id']} закрыт", color=COLOR_SUCCESS)
            emb.add_field(name="Категория", value=ticket["category"], inline=True)
            emb.add_field(name="Автор", value=f"<@{ticket['opener_id']}>", inline=True)
            emb.add_field(name="Закрыл", value=interaction.user.mention, inline=True)
            await log_chan.send(
                embed=emb,
                file=discord.File(io.BytesIO(transcript_bytes), filename=f"ticket-{ticket['id']}.txt"),
            )

        try:
            await interaction.channel.delete(reason=f"Тикет #{ticket['id']} закрыт {interaction.user}")
        except discord.HTTPException:
            pass


class TicketCategorySelect(discord.ui.Select):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot
        options = [
            discord.SelectOption(label="Техническая поддержка", description="Баги, проблемы со входом, лаунчер, ресурспак", emoji="🛠️"),
            discord.SelectOption(label="Вопросы по донату", description="Риллики, привилегии, кейсы, перенос доната", emoji="💎"),
            discord.SelectOption(label="Жалоба / Апелляция", description="Жалоба на читера или разбан аккаунта", emoji="⚖️"),
            discord.SelectOption(label="Связь с КП / Куратором", description="Вопросы к руководству и жалобы на стафф", emoji="👑"),
        ]
        super().__init__(
            placeholder="Выберите категорию обращения...",
            min_values=1,
            max_values=1,
            options=options,
            custom_id="rw_ticket_category_select",
        )

    async def callback(self, interaction: discord.Interaction) -> None:
        await interaction.response.send_modal(TicketTopicModal(self.bot, self.values[0]))


class TicketCreatePersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.add_item(TicketCategorySelect(bot))


class TicketsCog(commands.Cog):
    tickets_group = app_commands.Group(name="tickets", description="Система тикетов поддержки ReallyWorld")

    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot

    @tickets_group.command(name="setup", description="Опубликовать панель создания тикетов с кнопками/меню")
    async def tickets_setup(self, interaction: discord.Interaction, channel: discord.TextChannel | None = None) -> None:
        if not isinstance(interaction.user, discord.Member) or not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        target = channel or interaction.channel
        if not isinstance(target, discord.TextChannel):
            return
        emb = rw_embed(
            "🎫 Служба Поддержки и Связь с КП",
            "Нужна помощь по игре, возник вопрос по донату, нашли нарушителя или хотите подать апелляцию?\n\n"
            "• Выберите нужный раздел в выпадающем списке ниже.\n"
            "• Бот мгновенно создаст приватный канал только для вас и сотрудников **Команды Проекта**.\n"
            "• За ложные вызовы и спам тикетами выдаётся предупреждение.",
            COLOR_GOLD,
        )
        await target.send(embed=emb, view=TicketCreatePersistentView(self.bot))
        await interaction.response.send_message(f"✅ Панель тикетов опубликована в {target.mention}!", ephemeral=True)


async def setup(bot: "ReallyWorldBot") -> None:
    await bot.add_cog(TicketsCog(bot))
