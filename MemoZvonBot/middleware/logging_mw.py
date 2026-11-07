"""Логируем только тип события и ошибки, без текста и пользовательских данных."""
import logging
from typing import Any, Awaitable, Callable
from aiogram import BaseMiddleware
from aiogram.types import TelegramObject

logger = logging.getLogger(__name__)


class LoggingMiddleware(BaseMiddleware):
    async def __call__(self, handler: Callable[[TelegramObject, dict[str, Any]], Awaitable[Any]], event: TelegramObject, data: dict[str, Any]) -> Any:
        logger.info('Получено событие %s', type(event).__name__)
        try:
            return await handler(event, data)
        except Exception:
            logger.exception('Ошибка обработки события %s', type(event).__name__)
            raise
