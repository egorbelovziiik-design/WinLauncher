"""Настройки и ранняя проверка конфигурации."""
from dataclasses import dataclass
import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent


@dataclass(frozen=True)
class Config:
    token: str
    database_path: Path
    rate_limit_seconds: int
    tts_concurrency: int


def load_config() -> Config:
    load_dotenv(BASE_DIR / '.env')
    token = os.getenv('BOT_TOKEN', '').strip()
    if not token or 'replace_with' in token:
        raise ValueError('Укажите BOT_TOKEN в файле .env')
    try:
        cooldown = int(os.getenv('RATE_LIMIT_SECONDS', '15'))
        concurrency = int(os.getenv('TTS_CONCURRENCY', '3'))
    except ValueError as exc:
        raise ValueError('RATE_LIMIT_SECONDS и TTS_CONCURRENCY должны быть целыми числами') from exc
    if not 1 <= cooldown <= 86400 or not 1 <= concurrency <= 20:
        raise ValueError('RATE_LIMIT_SECONDS должен быть от 1 до 86400, TTS_CONCURRENCY — от 1 до 20')
    path = Path(os.getenv('DATABASE_PATH', 'data/memozvon.sqlite3')).expanduser()
    if not path.is_absolute():
        path = BASE_DIR / path
    return Config(token, path, cooldown, concurrency)
