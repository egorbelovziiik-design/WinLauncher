"""Точка входа MemoZvon: long polling, SQLite, планировщик."""
import asyncio
import logging
import shutil

from aiogram import Bot, Dispatcher
from aiogram.types import BotCommand
from apscheduler.schedulers.asyncio import AsyncIOScheduler

from config import load_config
from database import Database
from handlers import call, callbacks, list_memes, menu, prank, random_call, start, stats
from middleware.logging_mw import LoggingMiddleware
from rate_limiter import RateLimiter
from tts_engine import TTSEngine

logger = logging.getLogger(__name__)


async def main() -> None:
    config = load_config()
    if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
        raise RuntimeError('Установите FFmpeg и ffprobe и добавьте их в PATH')
    database = Database(config.database_path)
    await database.init()
    bot = Bot(token=config.token)
    dispatcher = Dispatcher(database=database, limiter=RateLimiter(database, config.rate_limit_seconds), tts=TTSEngine(config.tts_concurrency))
    dispatcher.update.outer_middleware(LoggingMiddleware())
    for router in (start.router, menu.router, call.router, list_memes.router, random_call.router, prank.router, stats.router, callbacks.router):
        dispatcher.include_router(router)
    scheduler = AsyncIOScheduler(timezone='UTC')
    scheduler.add_job(database.prune_limits, 'interval', hours=24, max_instances=1, coalesce=True)
    scheduler.start()
    try:
        await bot.set_my_commands([BotCommand(command='start', description='Начать'), BotCommand(command='menu', description='Меню'),
                                   BotCommand(command='list', description='Каталог'), BotCommand(command='call', description='Звонок по ID'),
                                   BotCommand(command='random', description='Случайный звонок'), BotCommand(command='prank', description='Поделиться'),
                                   BotCommand(command='stats', description='Статистика'), BotCommand(command='help', description='Помощь')])
        logger.info('МемоЗвон запущен')
        await dispatcher.start_polling(bot, handle_signals=True)
    finally:
        scheduler.shutdown(wait=False)
        await bot.session.close()


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(name)s: %(message)s')
    try:
        asyncio.run(main())
    except (ValueError, RuntimeError) as exc:
        logger.error('%s', exc)
        raise SystemExit(1) from exc
