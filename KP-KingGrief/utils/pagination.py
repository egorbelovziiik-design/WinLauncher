"""
Пагинация для КП-KingGrief.

`chunk()` разбивает данные на страницы, `PaginatorView` — универсальный
набор кнопок (⏮ ◀ 🗑 ▶ ⏭) с авто-таймаутом и привязкой к вызывающему.
"""

from __future__ import annotations

import math
from typing import Any, Callable, Sequence

import discord

EMBED_LIMIT = 6000
FIELD_LIMIT = 1024


def chunk(items: Sequence[Any], size: int) -> list[list[Any]]:
    """Режет последовательность на страницы заданного размера."""
    size = max(1, int(size))
    items = list(items)
    return [items[i : i + size] for i in range(0, len(items), size)] or [[]]


def page_count(total: int, size: int) -> int:
    return max(1, math.ceil(max(0, total) / max(1, size)))


class PaginatorView(discord.ui.View):
    """Универсальная пагинация по кнопкам.

    `render(page)` должна возвращать `(embed, view)` или только `embed`.
    """

    def __init__(
        self,
        *,
        total_pages: int,
        page: int = 1,
        owner_id: int | None = None,
        timeout: float = 300.0,
        extra_buttons: list[discord.ui.Button] | None = None,
    ) -> None:
        super().__init__(timeout=timeout)
        self.total_pages = max(1, total_pages)
        self.page = min(max(1, page), self.total_pages)
        self.owner_id = owner_id
        self.render: Callable[[int], Any] | None = None
        for button in extra_buttons or []:
            self.add_item(button)
        self._sync()

    # ------------------------------------------------------------------ #
    def _sync(self) -> None:
        single = self.total_pages <= 1
        for item in self.children:
            if getattr(item, "custom_id", "").startswith("pg_"):
                item.disabled = single  # type: ignore[attr-defined]
        self.first.disabled = single or self.page <= 1
        self.prev.disabled = single or self.page <= 1
        self.next.disabled = single or self.page >= self.total_pages
        self.last.disabled = single or self.page >= self.total_pages

    async def _owner_only(self, interaction: discord.Interaction) -> bool:
        if self.owner_id is None or interaction.user.id == self.owner_id:
            return True
        await interaction.response.send_message(
            "❌ Эта панель доступна только тому, кто её вызвал.",
            ephemeral=True,
        )
        return False

    async def _go(self, interaction: discord.Interaction, page: int) -> None:
        if not await self._owner_only(interaction):
            return
        self.page = min(max(1, page), self.total_pages)
        self._sync()
        if self.render is None:  # pragma: no cover - защита от неверной настройки
            await interaction.response.edit_message(view=self)
            return
        result = self.render(self.page)
        if isinstance(result, tuple):
            embed, _view = result
            await interaction.response.edit_message(embed=embed, view=self)
        else:
            await interaction.response.edit_message(embed=result, view=self)

    # ------------------------------------------------------------------ #
    @discord.ui.button(emoji="⏮️", style=discord.ButtonStyle.blurple, custom_id="pg_first")
    async def first(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._go(interaction, 1)

    @discord.ui.button(emoji="◀️", style=discord.ButtonStyle.primary, custom_id="pg_prev")
    async def prev(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._go(interaction, self.page - 1)

    @discord.ui.button(emoji="🗑️", style=discord.ButtonStyle.danger, custom_id="pg_close")
    async def close(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        if not await self._owner_only(interaction):
            return
        await interaction.response.edit_message(view=None)
        self.stop()

    @discord.ui.button(emoji="▶️", style=discord.ButtonStyle.primary, custom_id="pg_next")
    async def next(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._go(interaction, self.page + 1)

    @discord.ui.button(emoji="⏭️", style=discord.ButtonStyle.blurple, custom_id="pg_last")
    async def last(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self._go(interaction, self.total_pages)


class PageSelect(discord.ui.Select):
    """Выпадающее меню быстрого перехода по страницам (до 25)."""

    def __init__(self, total_pages: int, current: int = 1) -> None:
        options = [
            discord.SelectOption(label=f"Страница {i}", value=str(i), emoji="📄")
            for i in range(1, min(total_pages, 25) + 1)
        ]
        super().__init__(
            placeholder=f"Перейти к странице ({total_pages} всего)",
            options=options,
            min_values=1,
            max_values=1,
        )

    async def callback(self, interaction: discord.Interaction) -> None:
        view: PaginatorView | None = self.view  # type: ignore[assignment]
        if view is None:  # pragma: no cover
            return
        await view._go(interaction, int(self.values[0]))


async def send_paginated(
    ctx: discord.Interaction,
    *,
    total_pages: int,
    render: Callable[[int], discord.Embed],
    page: int = 1,
    ephemeral: bool = False,
    extra_buttons: list[discord.ui.Button] | None = None,
) -> PaginatorView:
    """Отправляет первый кадр пагинатора и привязывает к нему рендер."""
    view = PaginatorView(
        total_pages=total_pages,
        page=page,
        owner_id=ctx.user.id,
        extra_buttons=extra_buttons,
    )
    view.render = render
    embed = render(view.page)
    if ctx.response.is_done():
        await ctx.followup.send(embed=embed, view=view, ephemeral=ephemeral)
    else:
        await ctx.response.send_message(embed=embed, view=view, ephemeral=ephemeral)
    return view
