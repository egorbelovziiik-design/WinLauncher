from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import CallbackQuery, Message
from database import Database
from meme_library import MEMES

router = Router()


async def format_stats(database: Database, user_id: int) -> str:
    count, favorite = await database.stats(user_id)
    m = MEMES.get(favorite or '')
    label = f"{m['emoji']} {m['caller']} ({favorite})" if m else 'пока нет'
    return f'📊 Ваша статистика\n📞 Отправлено голосовых: {count}\n⭐ Любимый сценарий: {label}'


@router.message(Command('stats'))
async def stats(message: Message, database: Database) -> None:
    if message.from_user:
        await message.answer(await format_stats(database, message.from_user.id))


@router.callback_query(F.data == 'stats')
async def stats_button(callback: CallbackQuery, database: Database) -> None:
    await callback.answer()
    if isinstance(callback.message, Message):
        await callback.message.answer(await format_stats(database, callback.from_user.id))
