"""Команда Проекта (КП): Набор в стафф, Баллы/Выговоры, Вызов на проверку читов."""
from __future__ import annotations

from typing import TYPE_CHECKING

import discord
from discord import app_commands
from discord.ext import commands

from bot.utils.permissions import (
    COLOR_ERROR,
    COLOR_GOLD,
    COLOR_INFO,
    COLOR_ORANGE,
    COLOR_SUCCESS,
    is_bot_admin,
    is_staff_member,
    rw_embed,
)

if TYPE_CHECKING:
    from main import ReallyWorldBot


class KPApplicationModal(discord.ui.Modal):
    def __init__(self, bot: "ReallyWorldBot", position: str) -> None:
        super().__init__(title=f"Заявка в КП — {position}")
        self.bot = bot
        self.position = position

        self.mc_nick = discord.ui.TextInput(
            label="Ваш никнейм в Minecraft",
            placeholder="Например: robik0277",
            min_length=3,
            max_length=20,
        )
        self.age = discord.ui.TextInput(
            label="Ваш реальный возраст",
            placeholder="Например: 16",
            min_length=1,
            max_length=3,
        )
        self.tz = discord.ui.TextInput(
            label="Часовой пояс и прайм-тайм (онлайн в день)",
            placeholder="МСК, 4-6 часов в день",
            max_length=60,
        )
        self.exp = discord.ui.TextInput(
            label="Опыт модерации / проверок читов и знание правил",
            style=discord.TextStyle.paragraph,
            placeholder="Расскажите о своём опыте на ReallyWorld и других проектах...",
            min_length=15,
            max_length=800,
        )
        self.add_item(self.mc_nick)
        self.add_item(self.age)
        self.add_item(self.tz)
        self.add_item(self.exp)

    async def on_submit(self, interaction: discord.Interaction) -> None:
        if not interaction.guild:
            return
        app_id = await self.bot.db.create_kp_application(
            interaction.guild.id,
            interaction.user.id,
            self.position,
            self.mc_nick.value.strip(),
            self.age.value.strip(),
            self.tz.value.strip(),
            self.exp.value.strip(),
        )
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        review_cid = settings.get("kp_review_channel_id") or settings.get("log_channel_id")
        review_chan = interaction.guild.get_channel(review_cid) if review_cid else None

        emb = rw_embed(f"📨 Анкета в КП #{app_id} — {self.position}", color=COLOR_ORANGE)
        emb.add_field(name="Кандидат", value=f"{interaction.user.mention} (`{interaction.user.id}`)", inline=True)
        emb.add_field(name="Ник в игре", value=f"`{self.mc_nick.value}`", inline=True)
        emb.add_field(name="Возраст", value=f"`{self.age.value}`", inline=True)
        emb.add_field(name="Часовой пояс и онлайн", value=self.tz.value, inline=False)
        emb.add_field(name="Опыт и знания правил", value=self.exp.value, inline=False)
        emb.add_field(name="Статус", value="⏳ Ожидает рассмотрения куратором КП", inline=False)

        if isinstance(review_chan, discord.TextChannel):
            await review_chan.send(embed=emb, view=KPReviewPersistentView(self.bot, app_id))

        await interaction.response.send_message(
            f"✅ Ваша заявка **#{app_id}** на должность **{self.position}** отправлена кураторам КП! Ожидайте ответа в ЛС.",
            ephemeral=True,
        )


class KPRejectModal(discord.ui.Modal, title="Причина отказа в КП"):
    reason = discord.ui.TextInput(
        label="Укажите причину отказа",
        placeholder="Недостаточный возраст / слабая анкета...",
        max_length=300,
    )

    def __init__(self, bot: "ReallyWorldBot", app_id: int) -> None:
        super().__init__()
        self.bot = bot
        self.app_id = app_id

    async def on_submit(self, interaction: discord.Interaction) -> None:
        app_row = await self.bot.db.update_kp_application(self.app_id, "REJECTED", interaction.user.id, self.reason.value)
        if not app_row:
            await interaction.response.send_message("❌ Заявка не найдена.", ephemeral=True)
            return
        if interaction.message and interaction.message.embeds:
            emb = interaction.message.embeds[0]
            emb.color = COLOR_ERROR
            emb.set_field_at(
                len(emb.fields) - 1,
                name="Статус",
                value=f"❌ Отклонено куратором {interaction.user.mention}\nПричина: `{self.reason.value}`",
                inline=False,
            )
            await interaction.message.edit(embed=emb, view=None)

        if interaction.guild:
            member = interaction.guild.get_member(app_row["user_id"])
            if member:
                try:
                    await member.send(
                        embed=rw_embed(
                            f"❌ Заявка в КП #{self.app_id} отклонена",
                            f"Должность: **{app_row['position']}**\nПричина: `{self.reason.value}`",
                            COLOR_ERROR,
                        )
                    )
                except discord.HTTPException:
                    pass
        await interaction.response.send_message(f"✅ Заявка #{self.app_id} отклонена.", ephemeral=True)


class KPReviewPersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot", app_id: int = 0) -> None:
        super().__init__(timeout=None)
        self.bot = bot
        self.app_id = app_id

    def _extract_app_id(self, interaction: discord.Interaction) -> int:
        if self.app_id:
            return self.app_id
        if interaction.message and interaction.message.embeds:
            title = interaction.message.embeds[0].title or ""
            if "#" in title:
                num = title.split("#", 1)[1].split(" ", 1)[0]
                if num.isdigit():
                    return int(num)
        return 0

    async def _check_curator(self, interaction: discord.Interaction) -> bool:
        if not isinstance(interaction.user, discord.Member):
            return False
        if await is_bot_admin(self.bot, interaction.user) or interaction.user.guild_permissions.administrator:
            return True
        await interaction.response.send_message("❌ Рассматривать анкеты могут только Кураторы КП и Владелец бота.", ephemeral=True)
        return False

    @discord.ui.button(label="📞 Вызвать на обзвон", style=discord.ButtonStyle.primary, custom_id="rw_kp_interview_btn")
    async def interview_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not await self._check_curator(interaction):
            return
        app_id = self._extract_app_id(interaction)
        app_row = await self.bot.db.update_kp_application(app_id, "INTERVIEW", interaction.user.id, "Назначен обзвон")
        if not app_row:
            await interaction.response.send_message("❌ Анкета не найдена в БД.", ephemeral=True)
            return
        if interaction.message and interaction.message.embeds:
            emb = interaction.message.embeds[0]
            emb.color = COLOR_INFO
            emb.set_field_at(
                len(emb.fields) - 1,
                name="Статус",
                value=f"📞 Приглашён на обзвон куратором {interaction.user.mention}",
                inline=False,
            )
            await interaction.message.edit(embed=emb)
        if interaction.guild:
            cand = interaction.guild.get_member(app_row["user_id"])
            if cand:
                try:
                    await cand.send(
                        embed=rw_embed(
                            "📞 Приглашение на обзвон в КП!",
                            f"Ваша заявка **#{app_id}** на должность **{app_row['position']}** прошла первый этап!\n"
                            f"Куратор: {interaction.user.mention}. Свяжитесь или зайдите в канал ожидания обзвона.",
                            COLOR_INFO,
                        )
                    )
                except discord.HTTPException:
                    pass
        await interaction.response.send_message(f"✅ Кандидат по заявке #{app_id} приглашён на обзвон!", ephemeral=True)

    @discord.ui.button(label="✅ Принять в КП", style=discord.ButtonStyle.success, custom_id="rw_kp_approve_btn")
    async def approve_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not await self._check_curator(interaction):
            return
        app_id = self._extract_app_id(interaction)
        app_row = await self.bot.db.update_kp_application(app_id, "ACCEPTED", interaction.user.id, "Принят в КП")
        if not app_row or not interaction.guild:
            await interaction.response.send_message("❌ Заявка не найдена.", ephemeral=True)
            return

        await self.bot.db.ensure_kp_member(interaction.guild.id, app_row["user_id"], app_row["position"])
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        cand = interaction.guild.get_member(app_row["user_id"])
        if cand and settings.get("staff_role_id"):
            role = interaction.guild.get_role(settings["staff_role_id"])
            if role:
                try:
                    await cand.add_roles(role, reason=f"Принят в КП ({app_row['position']})")
                except discord.HTTPException:
                    pass
        if cand:
            try:
                await cand.send(
                    embed=rw_embed(
                        "🎉 Добро пожаловать в Команду Проекта!",
                        f"Ваша анкета **#{app_id}** одобрена куратором {interaction.user.mention}!\nНазначена должность: **{app_row['position']}**.",
                        COLOR_SUCCESS,
                    )
                )
            except discord.HTTPException:
                pass

        if interaction.message and interaction.message.embeds:
            emb = interaction.message.embeds[0]
            emb.color = COLOR_SUCCESS
            emb.set_field_at(
                len(emb.fields) - 1,
                name="Статус",
                value=f"✅ Принят в КП куратором {interaction.user.mention}",
                inline=False,
            )
            await interaction.message.edit(embed=emb, view=None)
        await interaction.response.send_message(f"🎉 Кандидат принят в КП на должность **{app_row['position']}**!", ephemeral=True)

    @discord.ui.button(label="❌ Отказать", style=discord.ButtonStyle.danger, custom_id="rw_kp_reject_btn")
    async def reject_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        if not await self._check_curator(interaction):
            return
        app_id = self._extract_app_id(interaction)
        await interaction.response.send_modal(KPRejectModal(self.bot, app_id))


class KPApplySelect(discord.ui.Select):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot
        options = [
            discord.SelectOption(label="Хелпер", description="Помощь игрокам, муты в чате, ответы на тикеты", emoji="🛡️"),
            discord.SelectOption(label="Модератор", description="Проверки на читы, баны, слежка за сервером", emoji="⚔️"),
            discord.SelectOption(label="Тех. Поддержка", description="Разбор сложных тикетов и баг-репортов", emoji="🛠️"),
            discord.SelectOption(label="Билдер", description="Постройка карт, спавнов и арен ивентов", emoji="🏗️"),
        ]
        super().__init__(
            placeholder="Выберите должность в Команде Проекта...",
            min_values=1,
            max_values=1,
            options=options,
            custom_id="rw_kp_apply_select",
        )

    async def callback(self, interaction: discord.Interaction) -> None:
        await interaction.response.send_modal(KPApplicationModal(self.bot, self.values[0]))


class KPApplyPersistentView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.add_item(KPApplySelect(bot))


class CheatCheckControlView(discord.ui.View):
    def __init__(self, bot: "ReallyWorldBot") -> None:
        super().__init__(timeout=None)
        self.bot = bot

    async def _finish(self, interaction: discord.Interaction, verdict: str, color: int) -> None:
        if not isinstance(interaction.user, discord.Member) or not interaction.guild or not interaction.channel:
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Завершать проверку может только сотрудник КП!", ephemeral=True)
            return
        check = await self.bot.db.get_active_check_by_channel(interaction.channel.id)
        if not check:
            await interaction.response.send_message("❌ Активная проверка в этом канале не найдена.", ephemeral=True)
            return

        await self.bot.db.finish_cheat_check(check["id"], verdict)
        pts = int(self.bot.cfg.raw_json.get("kp_settings", {}).get("points_per_check", 5))
        await self.bot.db.modify_kp_stats(interaction.guild.id, interaction.user.id, points_delta=pts, checks_delta=1)

        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        suspect = interaction.guild.get_member(check["suspect_id"])
        if suspect and settings.get("check_role_id"):
            role = interaction.guild.get_role(settings["check_role_id"])
            if role:
                try:
                    await suspect.remove_roles(role, reason=f"Завершение проверки: {verdict}")
                except discord.HTTPException:
                    pass

        log_cid = settings.get("log_channel_id")
        log_chan = interaction.guild.get_channel(log_cid) if log_cid else None
        if isinstance(log_chan, discord.TextChannel):
            emb = rw_embed(f"🔍 Итог проверки #{check['id']}", color=color)
            emb.add_field(name="Игрок", value=f"<@{check['suspect_id']}> (`{check['mc_nick']}`)", inline=True)
            emb.add_field(name="Проверяющий КП", value=f"{interaction.user.mention} (+{pts} баллов)", inline=True)
            emb.add_field(name="Вердикт", value=f"**{verdict}**", inline=False)
            await log_chan.send(embed=emb)

        await interaction.response.send_message(f"✅ Проверка завершена с вердиктом: **{verdict}**. Канал удалится через 5 секунд.")
        await discord.utils.sleep_until(discord.utils.utcnow())
        try:
            await interaction.channel.delete(reason=f"Проверка #{check['id']}: {verdict}")
        except discord.HTTPException:
            pass

    @discord.ui.button(label="✅ Чист (Отпустить)", style=discord.ButtonStyle.success, custom_id="rw_check_clean_btn")
    async def clean_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await self._finish(interaction, "ЧИСТ — запрещённого ПО не обнаружено", COLOR_SUCCESS)

    @discord.ui.button(label="🔨 Обнаружены читы / Признание", style=discord.ButtonStyle.danger, custom_id="rw_check_ban_btn")
    async def cheat_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await self._finish(interaction, "БАН — Обнаружены читы / Признание", COLOR_ERROR)

    @discord.ui.button(label="🚫 Отказ / Выход с проверки", style=discord.ButtonStyle.secondary, custom_id="rw_check_refuse_btn")
    async def refuse_btn(self, interaction: discord.Interaction, _: discord.ui.Button) -> None:
        await self._finish(interaction, "БАН — Отказ от проверки / Выход с сервера", COLOR_ERROR)


class KPStaffCog(commands.Cog):
    kp_group = app_commands.Group(name="kp", description="Управление Командой Проекта (КП)")
    check_group = app_commands.Group(name="check", description="Вызов игроков на проверку читов (КП)")

    def __init__(self, bot: "ReallyWorldBot") -> None:
        self.bot = bot

    @kp_group.command(name="setup-apply", description="Опубликовать интерактивную панель набора в КП")
    async def setup_apply(self, interaction: discord.Interaction, channel: discord.TextChannel | None = None) -> None:
        if not isinstance(interaction.user, discord.Member) or not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Нет прав.", ephemeral=True)
            return
        target = channel or interaction.channel
        if not isinstance(target, discord.TextChannel):
            return
        emb = rw_embed(
            "👑 Набор в Команду Проекта ReallyWorld (КП)",
            "Хочешь следить за порядком на сервере, проводить проверки на читы и расти от **Хелпера** до **Куратора КП**?\n\n"
            "**Требования к кандидатам:**\n"
            "• Возраст от **14 лет** (возможны исключения при отличной анкете)\n"
            "• Знание правил сервера и механик ReallyWorld\n"
            "• Наличие рабочего микрофона и Discord для обзвона\n"
            "• Адекватность, грамотность и стрессоустойчивость\n\n"
            "👇 **Выберите желаемую должность в меню ниже, чтобы заполнить анкету!**",
            COLOR_GOLD,
        )
        await target.send(embed=emb, view=KPApplyPersistentView(self.bot))
        await interaction.response.send_message(f"✅ Панель набора в КП опубликована в {target.mention}!", ephemeral=True)

    @kp_group.command(name="profile", description="Профиль сотрудника Команды Проекта (баллы, тикеты, выговоры)")
    async def kp_profile(self, interaction: discord.Interaction, member: discord.Member | None = None) -> None:
        if not interaction.guild:
            return
        target = member or interaction.user
        p = await self.bot.db.ensure_kp_member(interaction.guild.id, target.id)
        max_strikes = self.bot.cfg.raw_json.get("kp_settings", {}).get("max_strikes", 3)
        emb = rw_embed(f"🛡️ Досье сотрудника КП — {target.display_name}", color=COLOR_GOLD)
        emb.set_thumbnail(url=target.display_avatar.url)
        emb.add_field(name="Должность", value=f"**{p['position']}**", inline=True)
        emb.add_field(name="Баллы КП", value=f"⭐ `{p['points']}`", inline=True)
        emb.add_field(name="Выговоры", value=f"⚠️ `{p['strikes']} / {max_strikes}`", inline=True)
        emb.add_field(name="Закрыто тикетов", value=f"🎫 `{p['closed_tickets']}`", inline=True)
        emb.add_field(name="Проведено проверок", value=f"🔍 `{p['completed_checks']}`", inline=True)
        await interaction.response.send_message(embed=emb)

    @kp_group.command(name="points", description="Начислить или снять баллы сотруднику КП (Куратор/Владелец)")
    async def kp_points(
        self, interaction: discord.Interaction, member: discord.Member, delta: int, position: str | None = None
    ) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not (await is_bot_admin(self.bot, interaction.user) or interaction.user.guild_permissions.administrator):
            await interaction.response.send_message("❌ Доступ только Кураторам КП и Владельцу бота.", ephemeral=True)
            return
        updated = await self.bot.db.modify_kp_stats(interaction.guild.id, member.id, points_delta=delta, position=position)
        sign = f"+{delta}" if delta >= 0 else str(delta)
        await interaction.response.send_message(
            embed=rw_embed(
                "⭐ Обновление баллов КП",
                f"Сотрудник: {member.mention} (**{updated['position']}**)\nИзменение: `{sign}`\nТекущий баланс: **`{updated['points']}` баллов**",
                COLOR_SUCCESS,
            )
        )

    @kp_group.command(name="strike", description="Выдать или снять строгий выговор сотруднику КП")
    async def kp_strike(self, interaction: discord.Interaction, member: discord.Member, delta: int, reason: str) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not (await is_bot_admin(self.bot, interaction.user) or interaction.user.guild_permissions.administrator):
            await interaction.response.send_message("❌ Доступ только Кураторам КП и Владельцу бота.", ephemeral=True)
            return
        updated = await self.bot.db.modify_kp_stats(interaction.guild.id, member.id, strikes_delta=delta)
        max_s = int(self.bot.cfg.raw_json.get("kp_settings", {}).get("max_strikes", 3))
        alert = f"\n🚨 **ВНИМАНИЕ (<@{self.bot.cfg.owner_id}>): Достигнут лимит выговоров ({updated['strikes']}/{max_s}) — требуется снятие!**" if updated["strikes"] >= max_s else ""
        await interaction.response.send_message(
            embed=rw_embed(
                "⚠️ Дисциплинарное взыскание КП",
                f"Сотрудник: {member.mention}\nПричина: `{reason}`\nВыговоры: **`{updated['strikes']} / {max_s}`**{alert}",
                COLOR_ERROR if delta > 0 else COLOR_SUCCESS,
            )
        )

    @kp_group.command(name="top", description="Рейтинг лучших сотрудников Команды Проекта")
    async def kp_top(self, interaction: discord.Interaction) -> None:
        if not interaction.guild:
            return
        rows = await self.bot.db.get_kp_top(interaction.guild.id, 10)
        if not rows:
            await interaction.response.send_message("Список сотрудников КП пока пуст.", ephemeral=True)
            return
        lines = []
        for idx, r in enumerate(rows, 1):
            lines.append(
                f"**{idx}.** <@{r['user_id']}> (`{r['position']}`) — ⭐ **{r['points']}** баллов | 🎫 `{r['closed_tickets']}` | 🔍 `{r['completed_checks']}`"
            )
        await interaction.response.send_message(embed=rw_embed("🏆 Топ сотрудников КП", "\n".join(lines), COLOR_GOLD))

    # ---------------- Cheat Checks (/check start) ----------------
    @check_group.command(name="start", description="Вызвать игрока на проверку читов в приватный канал")
    async def check_start(
        self, interaction: discord.Interaction, suspect: discord.Member, mc_nick: str, reason: str = "Подозрение в использовании читов"
    ) -> None:
        if not interaction.guild or not isinstance(interaction.user, discord.Member):
            return
        if not await is_staff_member(self.bot, interaction.user):
            await interaction.response.send_message("❌ Вызывать на проверку могут только сотрудники КП.", ephemeral=True)
            return

        await interaction.response.defer(ephemeral=True)
        settings = await self.bot.db.get_guild_settings(interaction.guild.id)
        category = interaction.guild.get_channel(settings["ticket_category_id"]) if settings.get("ticket_category_id") else None

        overwrites: dict[discord.Role | discord.Member, discord.PermissionOverwrite] = {
            interaction.guild.default_role: discord.PermissionOverwrite(view_channel=False),
            suspect: discord.PermissionOverwrite(view_channel=True, send_messages=True, read_message_history=True),
            interaction.user: discord.PermissionOverwrite(view_channel=True, send_messages=True, manage_channels=True),
        }
        if settings.get("staff_role_id"):
            srole = interaction.guild.get_role(settings["staff_role_id"])
            if srole:
                overwrites[srole] = discord.PermissionOverwrite(view_channel=True, send_messages=True)

        chan = await interaction.guild.create_text_channel(
            name=f"проверка-{mc_nick.lower()[:16]}",
            category=category if isinstance(category, discord.CategoryChannel) else None,
            overwrites=overwrites,
            reason=f"Проверка на читы от {interaction.user}",
        )
        if settings.get("check_role_id"):
            crole = interaction.guild.get_role(settings["check_role_id"])
            if crole:
                try:
                    await suspect.add_roles(crole, reason="Вызван на проверку читов")
                except discord.HTTPException:
                    pass

        check_id = await self.bot.db.create_cheat_check(
            interaction.guild.id, chan.id, suspect.id, mc_nick, interaction.user.id, reason
        )
        timeout_min = self.bot.cfg.raw_json.get("kp_settings", {}).get("check_timeout_minutes", 5)
        emb = rw_embed(
            f"🚨 ВЫ ВЫЗВАНЫ НА ПРОВЕРКУ ЧИТОВ (#{check_id})",
            f"Игрок: {suspect.mention} (Ник: **`{mc_nick}`**)\n"
            f"Проверяющий модератор КП: {interaction.user.mention}\n"
            f"Причина: `{reason}`\n\n"
            f"⚠️ **ПРАВИЛА ПРОВЕРКИ:**\n"
            f"1. У вас есть **{timeout_min} минут**, чтобы ответить в этом чате и зайти в голосовой канал проверки.\n"
            f"2. **Строго запрещено** выходить из Minecraft, очищать корзину, менять файлы или удалять процессы — это приравнивается к бану!\n"
            f"3. Подготовьте **AnyDesk** или демонстрацию экрана в Discord по указанию модератора.",
            COLOR_ERROR,
        )
        await chan.send(content=f"{suspect.mention} {interaction.user.mention}", embed=emb, view=CheatCheckControlView(self.bot))
        await interaction.followup.send(f"✅ Канал проверки создан: {chan.mention}", ephemeral=True)


async def setup(bot: "ReallyWorldBot") -> None:
    await bot.add_cog(KPStaffCog(bot))
