"""Единый конвейер команд и кнопок: голосовое, затем вымышленный отчёт."""
import asyncio
import logging
from aiogram import F, Router
from aiogram.enums import ChatAction
from aiogram.filters import Command, CommandObject
from aiogram.types import BufferedInputFile, CallbackQuery, Message
from aiogram.exceptions import TelegramAPIError
from call_result import send_call_result
from database import Database
from meme_library import MEMES
from rate_limiter import RateLimiter
from tts_engine import TTSEngine

logger = logging.getLogger(__name__)
router = Router()


async def perform_call(message: Message, user_id: int, meme: dict, limiter: RateLimiter, tts: TTSEngine, database: Database) -> None:
    """Отправить результат только после успешной отправки голосового."""
    try:
        remaining = await limiter.check(user_id)
    except Exception:
        logger.exception('Ошибка проверки лимита')
        await message.answer('⚠️ Не удалось проверить лимит. Попробуйте позже.')
        return
    if remaining:
        await message.answer(f'⏳ Подождите ещё {remaining} сек. перед следующим звонком.')
        return
    try:
        await message.bot.send_chat_action(chat_id=message.chat.id, action=ChatAction.RECORD_VOICE)
        audio = await tts.render(meme['text'])
        await message.answer_voice(BufferedInputFile(audio, filename='memozvon.ogg'),
                                   caption=f"🎭 Вымышленный звонок: {meme['emoji']} {meme['caller']}")
    except Exception:
        logger.exception('Не удалось создать или отправить голосовое для %s', meme['name'])
        try:
            await message.answer('⚠️ Не удалось создать или отправить голосовое. Проверьте доступ к gTTS и FFmpeg и попробуйте позже.')
        except TelegramAPIError:
            logger.exception('Не удалось сообщить об ошибке')
        return
    async def record() -> None:
        try:
            await database.record_call(user_id, meme['name'])
        except Exception:
            logger.exception('Не удалось сохранить статистику')

    # Запись статистики не должна увеличивать задержку перед результатом.
    record_task = asyncio.create_task(record())
    try:
        await send_call_result(message, meme)
    except TelegramAPIError:
        logger.exception('Голосовое отправлено, но отчёт доставить не удалось')
    finally:
        await record_task


@router.message(Command('call'))
async def call_command(message: Message, command: CommandObject, limiter: RateLimiter, tts: TTSEngine, database: Database) -> None:
    name = (command.args or '').strip().lower()
    if name not in MEMES:
        await message.answer('Укажите ID мема: /call pizza\nВсе сценарии: /list')
        return
    if message.from_user:
        await perform_call(message, message.from_user.id, MEMES[name], limiter, tts, database)


@router.callback_query(F.data.startswith('call:'))
async def call_button(callback: CallbackQuery, limiter: RateLimiter, tts: TTSEngine, database: Database) -> None:
    name = (callback.data or '').partition(':')[2]
    if name not in MEMES:
        await callback.answer('Сценарий не найден', show_alert=True)
        return
    await callback.answer('Готовим звонок…')
    if isinstance(callback.message, Message):
        await perform_call(callback.message, callback.from_user.id, MEMES[name], limiter, tts, database)
