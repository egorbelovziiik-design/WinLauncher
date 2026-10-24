"""
Основной клиент бота КП-KingGrief.

Здесь происходит:
  * инициализация базы данных и миграций;
  * регистрация всех Cogs (статические импорты — важно для PyInstaller);
  * автоматический поиск каналов/ролей и синхронизация персонала;
  * запуск планировщика;
  * защита от запуска второй копии процесса.
"""

from __future__ import annotations

import asyncio
import os
import signal
import time
from pathlib import Path
from typing import Any

import discord
from discord import app_commands
from discord.ext import commands

from config import (
    BASE_DIR,
    BOT_ACTIVITY,
    BOT_NAME,
    BOT_VERSION,
    DATA_DIR,
    DISCORD_TOKEN,
    TEST_GUILD_ID,
)
from database.connection import db
from database.migrations import ensure_ready
from database.models import Repository
from services.report_tracker import ReportTracker, report_tracker
from services.role_manager import RoleManager, role_manager
from services.scheduler import SchedulerService
from services.warning_manager import WarningManager, warning_manager
from utils.logging import banner, get_logger

log = get_logger("client")

LOCK_FILE = Path(os.getenv("KP_LOCK_FILE", DATA_DIR / "bot.lock"))


# --------------------------------------------------------------------------- #
#  Защита от одновременного запуска нескольких копий
# --------------------------------------------------------------------------- #
class SingleInstanceLock:
    """Файловая блокировка процесса (работает на Windows и Linux)."""

    def __init__(self, path: Path = LOCK_FILE) -> None:
        self.path = Path(path)
        self._handle = None

    def _stale(self) -> bool:
        try:
            pid = int(self.path.read_text(encoding="utf-8").strip() or "0")
        except (OSError, ValueError):
            return True
        if pid <= 0:
            return True
        if os.name == "nt":  # pragma: no cover - Windows
            try:
                import ctypes

                kernel32 = ctypes.windll.kernel32  # type: ignore[attr-defined]
                handle = kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED
                if not handle:
                    return True
                kernel32.CloseHandle(handle)
                return False
            except Exception:
                return True
        try:
            os.kill(pid, 0)
            return False
        except ProcessLookupError:
            return True
        except PermissionError:
            return False

    def acquire(self) -> bool:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if self.path.exists() and not self._stale():
            log.error(
                "Обнаружен запущенный экземпляр бота (PID %s). Второй запуск отменён.",
                self.path.read_text(encoding="utf-8").strip(),
            )
            return False
        try:
            self._handle = open(self.path, "w", encoding="utf-8")  # noqa: SIM115
            self._handle.write(str(os.getpid()))
            self._handle.flush()
            return True
        except OSError as exc:
            log.error("Не удалось создать файл блокировки %s: %s", self.path, exc)
            return False

    def release(self) -> None:
        try:
            if self._handle is not None:
                self._handle.close()
                self._handle = None
            if self.path.exists():
                self.path.unlink()
        except OSError as exc:  # pragma: no cover
            log.debug("Не удалось удалить файл блокировки: %s", exc)


# --------------------------------------------------------------------------- #
class KingGriefBot(commands.Bot):
    """Discord-бот контроля персонала KingGrief."""

    def __init__(
        self,
        *,
        database: Any = None,
        repository: Repository | None = None,
        **kwargs: Any,
    ) -> None:
        intents = kwargs.pop("intents", None) or discord.Intents.default()
        intents.message_content = True
        intents.members = True
        intents.guilds = True
        intents.messages = True
        intents.reactions = True

        super().__init__(
            command_prefix=commands.when_mentioned,
            intents=intents,
            help_command=None,
            **kwargs,
        )

        self.db = database or db
        self.repo = repository or Repository(self.db)
        self.report_tracker: ReportTracker = report_tracker
        self.report_tracker.repo = self.repo
        self.warning_manager: WarningManager = warning_manager
        self.warning_manager.repo = self.repo
        self.role_manager: RoleManager = role_manager
        self.role_manager.repo = self.repo

        # все глобальные сервисы должны работать с одной и той же БД
        from bot.permissions import permissions as _permissions

        _permissions.repo = self.repo
        self.scheduler = SchedulerService(bot=self, repository=self.repo)
        self.ready_guilds: set[int] = set()
        self.started_at: float = time.monotonic()
        self.version = BOT_VERSION

    # ------------------------------------------------------------------ #
    #  Инициализация
    # ------------------------------------------------------------------ #
    async def setup_hook(self) -> None:  # noqa: D102
        log.info("Инициализация %s v%s…", BOT_NAME, BOT_VERSION)
        await ensure_ready(self.db)
        await self._register_cogs()

        guild = discord.Object(id=TEST_GUILD_ID) if TEST_GUILD_ID else None
        if guild:
            self.tree.copy_global_to(guild=guild)
            log.info("Slash-команды будут синхронизированы в тестовую гильдию %s.", TEST_GUILD_ID)

    async def _register_cogs(self) -> None:
        """Статическая регистрация всех Cogs (важно для PyInstaller)."""
        from commands.reports import ReportsCog
        from commands.settings import SettingsCog
        from commands.staff import StaffCog
        from commands.statistics import StatisticsCog
        from commands.warnings import WarningsCog
        from bot.events import EventsCog

        for cog in (
            EventsCog(self),
            ReportsCog(self),
            WarningsCog(self),
            StaffCog(self),
            StatisticsCog(self),
            SettingsCog(self),
        ):
            await self.add_cog(cog)
            log.info("Cog загружен: %s", cog.qualified_name)

    # ------------------------------------------------------------------ #
    #  События готовности
    # ------------------------------------------------------------------ #
    async def on_ready(self) -> None:  # noqa: D102
        self.started_at = time.monotonic()
        try:
            await self.change_presence(
                activity=discord.Activity(type=discord.ActivityType.watching, name=BOT_ACTIVITY),
                status=discord.Status.online,
            )
        except discord.HTTPException as exc:  # pragma: no cover
            log.warning("Не удалось установить статус: %s", exc)

        await self.sync_commands()
        for guild in self.guilds:
            await self.bootstrap_guild(guild)

        print(
            banner(
                f"  🛡️  {BOT_NAME} v{BOT_VERSION} запущен\n"
                f"  👤 {self.user}\n"
                f"  🌐 Серверов: {len(self.guilds)}\n"
                f"  🟢 {BOT_ACTIVITY}"
            )
        )

    async def on_guild_join(self, guild: discord.Guild) -> None:
        log.info("Бот добавлен на сервер «%s».", guild.name)
        await self.bootstrap_guild(guild)

    async def bootstrap_guild(self, guild: discord.Guild) -> dict[str, Any]:
        """Первичная настройка сервера: каналы, роли, персонал, планировщик."""
        if guild.id in self.ready_guilds:
            return {"already": True}
        try:
            channels: dict[str, int] = {}
            if await self.repo.settings.get(guild.id, "auto_detect_channels", True):
                channels = await self.role_manager.auto_discover_channels(guild)
            roles_found = 0
            if await self.repo.settings.get(guild.id, "auto_detect_roles", True):
                roles_found, _ = await self.role_manager.sync_role_config(guild)
                await self.role_manager.sync_permission_roles(guild)
            summary = await self.role_manager.sync_guild(guild)
            await self.scheduler.setup(guild.id)
            self.ready_guilds.add(guild.id)
            log.info(
                "Сервер «%s» готов: каналов %s, ролей %s, сотрудников %s.",
                guild.name,
                len(channels),
                roles_found,
                summary["total"],
            )
            return {"channels": channels, "roles": roles_found, "staff": summary}
        except Exception as exc:  # noqa: BLE001
            log.exception("Не удалось подготовить сервер «%s»: %s", guild.name, exc)
            return {"error": str(exc)}

    async def sync_commands(self) -> list[app_commands.AppCommand]:
        """Синхронизация Slash-команд с Discord."""
        try:
            if TEST_GUILD_ID:
                guild = discord.Object(id=TEST_GUILD_ID)
                self.tree.copy_global_to(guild=guild)
                synced = await self.tree.sync(guild=guild)
            else:
                synced = await self.tree.sync()
            log.info("Синхронизировано slash-команд: %s", len(synced))
            return synced
        except discord.HTTPException as exc:
            log.error("Не удалось синхронизировать команды: %s", exc)
            return []
        except Exception as exc:  # noqa: BLE001 - например, нет application_id до on_ready
            log.warning("Синхронизация команд отложена: %s", exc)
            return []

    # ------------------------------------------------------------------ #
    #  Обработка ошибок команд
    # ------------------------------------------------------------------ #
    async def on_app_command_error(
        self, interaction: discord.Interaction, error: app_commands.AppCommandError
    ) -> None:
        from bot.permissions import PermissionDenied
        from utils import embeds as E

        if isinstance(error, PermissionDenied):
            message = str(error)
        elif isinstance(error, app_commands.MissingPermissions):
            message = "❌ Недостаточно прав."
        elif isinstance(error, app_commands.CommandNotFound):
            message = "❌ Команда не найдена."
        elif isinstance(error, app_commands.CommandOnCooldown):
            message = f"⏳ Команда на перезарядке: {error.retry_after:.1f} сек."
        else:
            message = "❌ Произошла внутренняя ошибка. Информация записана в лог."
            log.exception("Ошибка команды: %s", error)

        embed = E.error("Ошибка", message)
        try:
            if interaction.response.is_done():
                await interaction.followup.send(embed=embed, ephemeral=True)
            else:
                await interaction.response.send_message(embed=embed, ephemeral=True)
        except discord.HTTPException:  # pragma: no cover
            pass

    # ------------------------------------------------------------------ #
    async def close(self) -> None:  # noqa: D102
        try:
            await self.scheduler.stop()
        except Exception as exc:  # pragma: no cover
            log.debug("Ошибка при остановке планировщика: %s", exc)
        try:
            await self.db.close()
        except Exception as exc:  # pragma: no cover
            log.debug("Ошибка при закрытии БД: %s", exc)
        await super().close()


# --------------------------------------------------------------------------- #
#  Запуск с автоматическим переподключением
# --------------------------------------------------------------------------- #
async def run_forever(
    token: str,
    *,
    restarts: int = 0,
    max_restarts: int = 25,
    delay: float = 5.0,
) -> int:
    """Запускает бота и перезапускает его при сбоях.

    Возвращает код завершения процесса.
    """
    attempt = restarts
    while True:
        bot = KingGriefBot()
        try:
            await bot.start(token)
            return 0
        except discord.LoginFailure:
            log.critical(
                "Неверный токен бота. Проверьте DISCORD_TOKEN в файле .env."
            )
            return 2
        except discord.PrivilegedIntentsRequired:
            log.critical(
                "Не включены привилегированные Intents. Включите в Discord Developer "
                "Portal: SERVER MEMBERS INTENT и MESSAGE CONTENT INTENT."
            )
            return 3
        except (discord.GatewayNotFound, discord.HTTPException, OSError) as exc:
            attempt += 1
            if attempt > max_restarts:
                log.critical("Превышено число перезапусков (%s). Выход.", max_restarts)
                return 4
            log.error("Сбой соединения (%s). Перезапуск через %.0f сек… (%s/%s)",
                      exc, delay, attempt, max_restarts)
            await asyncio.sleep(delay)
            delay = min(delay * 1.5, 60.0)
        except asyncio.CancelledError:  # pragma: no cover
            log.info("Получен сигнал остановки.")
            raise
        except Exception:  # noqa: BLE001
            log.exception("Непредвиденная ошибка — перезапуск.")
            attempt += 1
            if attempt > max_restarts:
                return 4
            await asyncio.sleep(delay)
        finally:
            if not bot.is_closed():
                try:
                    await bot.close()
                except Exception:  # pragma: no cover
                    pass


def install_signal_handlers(loop: asyncio.AbstractEventLoop, stop: asyncio.Event) -> None:
    """Корректное завершение по Ctrl+C / SIGTERM."""

    def handler() -> None:
        log.info("Получен сигнал завершения — останавливаю бота…")
        stop.set()

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, handler)
        except (NotImplementedError, RuntimeError):  # pragma: no cover - Windows
            signal.signal(sig, lambda *_: stop.set())


def missing_token_message() -> str:
    """Понятная ошибка, если токен не задан."""
    return (
        "❌ Не найден токен бота (DISCORD_TOKEN).\n\n"
        f"1. Создайте файл .env рядом с программой ({BASE_DIR}).\n"
        "2. Добавьте строку:  DISCORD_TOKEN=ваш_токен\n"
        "3. Токен берётся на https://discord.com/developers/applications → Bot → Reset Token.\n\n"
        "Токен не встраивается в EXE намеренно — он всегда хранится во внешнем .env."
    )


def check_token() -> str | None:
    """Возвращает токен или печатает понятную ошибку."""
    token = (DISCORD_TOKEN or "").strip()
    if not token:
        print(missing_token_message())
        return None
    return token
