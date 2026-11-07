from aiogram import F, Router
from aiogram.types import CallbackQuery, Message
from keyboards.inline import PAGE_SIZE, list_keyboard
from meme_library import MEMES

router = Router()


@router.callback_query(F.data.startswith('page:'))
async def paginate(callback: CallbackQuery) -> None:
    try:
        page = int((callback.data or '').split(':', 1)[1])
        if not 0 <= page <= (len(MEMES) - 1) // PAGE_SIZE:
            raise ValueError
    except ValueError:
        await callback.answer('Страница не найдена', show_alert=True)
        return
    await callback.answer()
    if isinstance(callback.message, Message):
        await callback.message.edit_text(f'📚 Каталог: страница {page + 1}. Выберите сценарий:', reply_markup=list_keyboard(page))
