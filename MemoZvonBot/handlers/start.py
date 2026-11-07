"""Приветствие и безопасные deep links: получатель сам запускает сценарий."""
from aiogram import Router
from aiogram.filters import Command, CommandObject
from aiogram.types import Message
from keyboards.inline import meme_keyboard, menu_keyboard
from meme_library import MEMES

router = Router()


@router.message(Command('start'))
async def start(message: Message, command: CommandObject) -> None:
    arg = command.args or ''
    name = arg[5:] if arg.startswith('meme_') else ''
    if name in MEMES:
        meme = MEMES[name]
        await message.answer(f"🎭 Друг поделился вымышленным звонком: {meme['emoji']} {meme['caller']}.\nНажмите кнопку, чтобы прослушать. Реальных звонков и ответов нет.", reply_markup=meme_keyboard(name))
    else:
        await message.answer('📞 Привет! Я МемоЗвон — генератор смешных вымышленных звонков.\n\n🎭 Все голоса и «ответы жертвы» — постановка. Я не звоню реальным людям.\n\nВыберите сценарий в /list, попробуйте /call pizza или /random.\nПоделиться: /prank pizza. Помощь: /help', reply_markup=menu_keyboard())


@router.message(Command('help'))
async def help_command(message: Message) -> None:
    await message.answer('/menu — главное меню\n/list — 50 сценариев\n/call pizza — создать звонок по ID\n/random — случайный звонок\n/prank pizza — ссылка для друга\n/stats — ваша статистика\n\nОтчёт и диалог вымышлены. Бот не совершает телефонных звонков.')
