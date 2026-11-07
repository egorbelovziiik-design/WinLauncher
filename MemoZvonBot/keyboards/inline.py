"""Кнопки меню, каталога и добровольной отправки мемов."""
from urllib.parse import quote
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup
from meme_library import MEMES

PAGE_SIZE = 8


def menu_keyboard() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text='🎲 Случайный звонок', callback_data='random')],
        [InlineKeyboardButton(text='📚 Каталог мемов', callback_data='page:0')],
        [InlineKeyboardButton(text='📊 Моя статистика', callback_data='stats')],
    ])


def meme_keyboard(name: str) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text='📞 Прослушать', callback_data=f'call:{name}')],
        [InlineKeyboardButton(text='📚 Каталог', callback_data='page:0')],
    ])


def list_keyboard(page: int) -> InlineKeyboardMarkup:
    items = list(MEMES.values())
    max_page = (len(items) - 1) // PAGE_SIZE
    page = max(0, min(page, max_page))
    rows = [[InlineKeyboardButton(text=f"{m['emoji']} {m['caller']} · {m['name']}", callback_data=f"call:{m['name']}")] for m in items[page * PAGE_SIZE:(page + 1) * PAGE_SIZE]]
    navigation = []
    if page > 0:
        navigation.append(InlineKeyboardButton(text='⬅️', callback_data=f'page:{page - 1}'))
    if page < max_page:
        navigation.append(InlineKeyboardButton(text='➡️', callback_data=f'page:{page + 1}'))
    if navigation:
        rows.append(navigation)
    return InlineKeyboardMarkup(inline_keyboard=rows)


def share_keyboard(bot_username: str, meme_id: str) -> InlineKeyboardMarkup:
    link = f'https://t.me/{bot_username}?start=meme_{meme_id}'
    share_url = 'https://t.me/share/url?url=' + quote(link, safe='') + '&text=' + quote('🎭 Послушай вымышленный мем-звонок!')
    return InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text='📤 Поделиться с другом', url=share_url)]])
