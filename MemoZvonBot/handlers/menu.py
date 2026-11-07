from aiogram import Router
from aiogram.filters import Command
from aiogram.types import Message
from keyboards.inline import menu_keyboard

router = Router()


@router.message(Command('menu'))
async def menu(message: Message) -> None:
    await message.answer('📞 Главное меню', reply_markup=menu_keyboard())
