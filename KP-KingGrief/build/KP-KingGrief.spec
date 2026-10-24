# -*- mode: python ; coding: utf-8 -*-
"""
Спецификация PyInstaller для сборки КП-KingGrief.

Сборка:  pyinstaller build/KP-KingGrief.spec --noconfirm
Или:     python build/build_exe.py

Токен бота в EXE НЕ встраивается — он читается из внешнего `.env`.
"""

from pathlib import Path

ROOT = Path(SPECPATH).parent  # каталог проекта
APP_NAME = "КП-KingGrief"

# Явные скрытые импорты: PyInstaller не всегда находит динамически
# используемые модули APScheduler и zoneinfo.
hiddenimports = [
    # Cogs бота
    "bot",
    "bot.client",
    "bot.events",
    "bot.permissions",
    "commands",
    "commands.reports",
    "commands.warnings",
    "commands.staff",
    "commands.statistics",
    "commands.settings",
    "database",
    "database.connection",
    "database.migrations",
    "database.models",
    "services",
    "services.report_tracker",
    "services.role_manager",
    "services.scheduler",
    "services.warning_manager",
    "utils",
    "utils.embeds",
    "utils.logging",
    "utils.pagination",
    "utils.periods",
    # зависимости
    "discord",
    "discord.ext.commands",
    "discord.app_commands",
    "dotenv",
    "apscheduler",
    "apscheduler.schedulers.asyncio",
    "apscheduler.schedulers.background",
    "apscheduler.triggers.cron",
    "apscheduler.triggers.interval",
    "apscheduler.triggers.date",
    "apscheduler.jobstores.memory",
    "apscheduler.executors.asyncio",
    "tzdata",
    "zoneinfo",
    "sqlite3",
    "email.mime.text",
    "email.mime.multipart",
]

# Подмодули APScheduler, которые грузятся по строковым именам
collect_apscheduler = []
try:
    from PyInstaller.utils.hooks import collect_submodules

    collect_apscheduler = collect_submodules("apscheduler")
    hiddenimports += collect_apscheduler
except Exception:
    pass

a = Analysis(
    [str(ROOT / "main.py")],
    pathex=[str(ROOT)],
    binaries=[],
    datas=[],
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[
        "tkinter",
        "matplotlib",
        "numpy",
        "pandas",
        "PyQt5",
        "PySide2",
        "IPython",
        "jupyter",
    ],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name=APP_NAME,
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,          # консольное окно нужно для логов и паузы при ошибке
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,             # укажите путь к .ico при наличии
)
