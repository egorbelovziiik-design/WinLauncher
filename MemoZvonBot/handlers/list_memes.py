from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message
from keyboards.inline import list_keyboard
from meme_library import MEMES

router = Router()


@router.message(Command('list'))
async def list_memes(message: Message) -> None:
    await message.answer(f'📚 Каталог: {len(MEMES)} вымышленных сценариев. Нажмите на любой:', reply_markup=list_keyboard(0))
