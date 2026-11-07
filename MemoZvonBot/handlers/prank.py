"""Делиться можно только ссылкой; бот не пишет третьим лицам без их участия."""
from aiogram import Router
from aiogram.filters import Command, CommandObject
from aiogram.types import Message
from keyboards.inline import share_keyboard
from meme_library import MEMES

router = Router()


@router.message(Command('prank'))
async def prank(message: Message, command: CommandObject) -> None:
    name = (command.args or '').strip().lower()
    if name not in MEMES:
        await message.answer('Укажите ID сценария: /prank pizza. Все ID: /list')
        return
    bot = await message.bot.get_me()
    if not bot.username:
        await message.answer('У бота не задан username, ссылку создать не удалось.')
        return
    await message.answer('🎭 Поделитесь ссылкой: друг сам решит, прослушивать ли вымышленный звонок.', reply_markup=share_keyboard(bot.username, name))
