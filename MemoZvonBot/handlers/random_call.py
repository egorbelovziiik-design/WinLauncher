"""Случайный звонок использует тот же конвейер и лимиты, что и /call."""
import random
from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import CallbackQuery, Message
from database import Database
from handlers.call import perform_call
from meme_library import MEMES
from rate_limiter import RateLimiter
from tts_engine import TTSEngine

router = Router()


def random_meme() -> dict:
    return random.choice(list(MEMES.values()))


@router.message(Command('random'))
async def random_command(message: Message, limiter: RateLimiter, tts: TTSEngine, database: Database) -> None:
    if message.from_user:
        await perform_call(message, message.from_user.id, random_meme(), limiter, tts, database)


@router.callback_query(F.data == 'random')
async def random_button(callback: CallbackQuery, limiter: RateLimiter, tts: TTSEngine, database: Database) -> None:
    await callback.answer('Готовим сюрприз…')
    if isinstance(callback.message, Message):
        await perform_call(callback.message, callback.from_user.id, random_meme(), limiter, tts, database)
