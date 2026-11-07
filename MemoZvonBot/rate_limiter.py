"""Защита от частых запросов (общая для всех обработчиков)."""
from database import Database


class RateLimiter:
    def __init__(self, database: Database, cooldown: int):
        self.database = database
        self.cooldown = cooldown

    async def check(self, user_id: int) -> int:
        return await self.database.reserve(user_id, self.cooldown)
