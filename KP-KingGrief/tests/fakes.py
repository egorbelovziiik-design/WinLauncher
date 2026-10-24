"""
Лёгкие подставки Discord-объектов для тестов.

Они реализуют ровно тот интерфейс, который использует бот, поэтому
тесты проходят по НАСТОЯЩЕМУ коду (классификация отчётов, варны,
снятие ролей, права, /ot), а не по его копии.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any, Iterable

import discord


class FakePermissions:
    def __init__(self, *, manage_roles: bool = True, administrator: bool = False) -> None:
        self.manage_roles = manage_roles
        self.administrator = administrator


class FakeRole:
    """Роль с реальной семантикой сравнения по иерархии."""

    def __init__(self, guild: "FakeGuild", role_id: int, name: str, position: int = 1, managed: bool = False) -> None:
        self.guild = guild
        self.id = role_id
        self.name = name
        self.position = position
        self.managed = managed

    @property
    def mention(self) -> str:
        return f"<@&{self.id}>"

    def _key(self) -> tuple[int, int]:
        return (self.position, self.id)

    def __ge__(self, other: Any) -> bool:
        return self._key() >= other._key()

    def __gt__(self, other: Any) -> bool:
        return self._key() > other._key()

    def __le__(self, other: Any) -> bool:
        return self._key() <= other._key()

    def __lt__(self, other: Any) -> bool:
        return self._key() < other._key()

    def __eq__(self, other: Any) -> bool:
        return isinstance(other, FakeRole) and self.id == other.id

    def __hash__(self) -> int:
        return hash(self.id)

    def __repr__(self) -> str:  # pragma: no cover - отладка
        return f"<FakeRole {self.name}#{self.id} pos={self.position}>"


class FakeMember:
    def __init__(
        self,
        guild: "FakeGuild",
        user_id: int,
        name: str,
        *,
        bot: bool = False,
        roles: Iterable[FakeRole] = (),
        permissions: FakePermissions | None = None,
    ) -> None:
        self.guild = guild
        self.id = user_id
        self.name = name
        self.bot = bot
        self.roles: list[FakeRole] = [guild.default_role, *roles]
        self.guild_permissions = permissions or FakePermissions()
        self.sent_dms: list[Any] = []
        self.display_avatar = None

    @property
    def display_name(self) -> str:
        return self.name

    @property
    def mention(self) -> str:
        return f"<@{self.id}>"

    @property
    def top_role(self) -> FakeRole:
        return max(self.roles, key=lambda r: r._key())

    def __str__(self) -> str:
        return self.name

    async def add_roles(self, *roles: FakeRole, reason: str | None = None) -> None:
        for role in roles:
            if role not in self.roles:
                self.roles.append(role)

    async def remove_roles(self, *roles: FakeRole, reason: str | None = None) -> None:
        for role in roles:
            self.roles = [r for r in self.roles if r.id != role.id]

    async def send(self, *args: Any, **kwargs: Any) -> Any:
        self.sent_dms.append(kwargs.get("embed") or (args[0] if args else None))
        return True


class FakeCategory:
    def __init__(self, guild: "FakeGuild", category_id: int, name: str) -> None:
        self.guild = guild
        self.id = category_id
        self.name = name


class FakeTextChannel:
    def __init__(
        self,
        guild: "FakeGuild",
        channel_id: int,
        name: str,
        category: FakeCategory | None = None,
        *,
        can_send: bool = True,
    ) -> None:
        self.guild = guild
        self.id = channel_id
        self.name = name
        self.category = category
        self.category_id = category.id if category else None
        self.sent: list[discord.Embed] = []
        self.can_send = can_send
        self.messages: list["FakeMessage"] = []

    @property
    def mention(self) -> str:
        return f"#{self.name}"

    def __str__(self) -> str:
        return self.name

    async def send(self, content: str | None = None, **kwargs: Any) -> Any:
        if not self.can_send:
            raise discord.Forbidden(
                type("R", (), {"status": 403, "code": 50001})(), "Missing Permissions"
            )
        embed = kwargs.get("embed")
        self.sent.append(embed)
        return FakeMessage(
            self, self.guild.get_member(0) or FakeMember(self.guild, 0, "bot"), content or ""
        )

    def history(self, limit: int = 100, oldest_first: bool = False):
        items = list(self.messages)[:limit]
        if not oldest_first:
            items.reverse()

        async def iterator():
            for item in items:
                yield item

        return _AsyncIter(iterator())


class _AsyncIter:
    def __init__(self, iterator) -> None:
        self._iterator = iterator

    def __aiter__(self):
        return self

    async def __anext__(self):
        return await self._iterator.__anext__()


class FakeMessage:
    def __init__(
        self,
        channel: FakeTextChannel,
        author: FakeMember,
        content: str,
        *,
        message_id: int | None = None,
        attachments: list[Any] | None = None,
        created_at: datetime | None = None,
        webhook_id: int | None = None,
    ) -> None:
        self.channel = channel
        self.guild = channel.guild
        self.author = author
        self.content = content
        self.id = message_id or (id(self) % 10**9)
        self.attachments = attachments if attachments is not None else []
        self.created_at = created_at or datetime.now()
        self.jump_url = f"https://discord.com/channels/{self.guild.id}/{channel.id}/{self.id}"
        self.webhook_id = webhook_id


class FakeGuild:
    def __init__(self, guild_id: int = 1, name: str = "KingGrief") -> None:
        self.id = guild_id
        self.name = name
        self.owner_id = 1
        self.roles: list[FakeRole] = []
        self.members: list[FakeMember] = []
        self.channels: list[FakeTextChannel] = []
        self.categories: list[FakeCategory] = []
        self.default_role = FakeRole(self, guild_id, "@everyone", position=0)
        self.roles.append(self.default_role)
        self.me: FakeMember | None = None

    # -- фабрики ------------------------------------------------------- #
    def role(self, name: str, position: int = 1, managed: bool = False) -> FakeRole:
        role = FakeRole(self, 1000 + len(self.roles), name, position, managed)
        self.roles.append(role)
        return role

    def category(self, name: str) -> FakeCategory:
        category = FakeCategory(self, 2000 + len(self.categories), name)
        self.categories.append(category)
        return category

    def channel(
        self, name: str, category: FakeCategory | None = None, *, can_send: bool = True
    ) -> FakeTextChannel:
        channel = FakeTextChannel(
            self, 3000 + len(self.channels), name, category, can_send=can_send
        )
        self.channels.append(channel)
        return channel

    def member(
        self,
        user_id: int,
        name: str,
        *,
        roles: Iterable[FakeRole] = (),
        bot: bool = False,
        permissions: FakePermissions | None = None,
    ) -> FakeMember:
        member = FakeMember(
            self, user_id, name, bot=bot, roles=roles, permissions=permissions
        )
        self.members.append(member)
        return member

    # -- интерфейс discord.Guild --------------------------------------- #
    @property
    def text_channels(self) -> list[FakeTextChannel]:
        return list(self.channels)

    def get_channel(self, channel_id: int) -> FakeTextChannel | None:
        for channel in self.channels:
            if channel.id == channel_id:
                return channel
        return None

    def get_member(self, user_id: int) -> FakeMember | None:
        for member in self.members:
            if member.id == user_id:
                return member
        return None

    async def fetch_member(self, user_id: int) -> FakeMember:
        member = self.get_member(user_id)
        if member is None:
            raise discord.NotFound(type("R", (), {"status": 404, "code": 10007})(), "Unknown Member")
        return member


class FakeResponse:
    def __init__(self) -> None:
        self.messages: list[dict[str, Any]] = []
        self.done = False

    def is_done(self) -> bool:
        return self.done

    async def send_message(self, *args: Any, **kwargs: Any) -> None:
        self.done = True
        self.messages.append(kwargs)

    async def edit_message(self, *args: Any, **kwargs: Any) -> None:
        self.messages.append(kwargs)

    async def defer(self, *args: Any, **kwargs: Any) -> None:
        self.done = True


class FakeFollowup:
    def __init__(self) -> None:
        self.messages: list[dict[str, Any]] = []

    async def send(self, *args: Any, **kwargs: Any) -> Any:
        self.messages.append(kwargs)
        return True


class FakeInteraction:
    """Минимальный discord.Interaction для вызова callback'ов команд."""

    def __init__(self, guild: FakeGuild, user: FakeMember, client: Any = None) -> None:
        self.guild = guild
        self.user = user
        self.client = client
        self.response = FakeResponse()
        self.followup = FakeFollowup()
        self.id = 1

    # -- вспомогательное для тестов ------------------------------------ #
    def last_embed(self) -> discord.Embed | None:
        for source in (self.response.messages, self.followup.messages):
            for message in reversed(source):
                if message.get("embed") is not None:
                    return message["embed"]
        return None

    def last_view(self) -> Any:
        for source in (self.response.messages, self.followup.messages):
            for message in reversed(source):
                if message.get("view") is not None:
                    return message["view"]
        return None

    def all_embeds(self) -> list[discord.Embed]:
        embeds = []
        for source in (self.response.messages, self.followup.messages):
            for message in source:
                if message.get("embed") is not None:
                    embeds.append(message["embed"])
        return embeds
