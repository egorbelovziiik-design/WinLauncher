# -*- mode: python ; coding: utf-8 -*-
"""Спецификация PyInstaller для BeatForge Studio (onefile).

Сборка::

    pyinstaller build.spec

Результат — один исполняемый файл в ``dist/``:

* Windows: ``dist/BeatForgeStudio.exe``;
* Linux/macOS: ``dist/BeatForgeStudio``.

Что важно:

* ``assets/`` (QSS, шрифты, пресеты переходов) упаковываются как данные;
* скрытые импорты нужны, т.к. часть модулей подгружается динамически
  (плагины yt-dlp, бэкенды Qt, опциональные кодеки);
* FFmpeg не встраивается: используется системный или бинарник
  ``imageio-ffmpeg`` (он попадает в сборку как зависимость);
* ``--onefile`` распаковывает всё во временную папку — настройки и кэш
  хранятся в ``~/.beatforge_studio`` и не теряются.
"""

import sys
from pathlib import Path

from PyInstaller.utils.hooks import collect_data_files, collect_submodules

ROOT = Path(SPECPATH).resolve()  # noqa: F821 - SPECPATH задаёт PyInstaller

# --- данные приложения
datas = [
    (str(ROOT / "assets"), "assets"),
    (str(ROOT / "assets" / "styles.qss"), "assets"),
]

# --- данные сторонних библиотек
for package in ("imageio_ffmpeg", "yt_dlp", "certifi", "librosa", "soundfile"):
    try:
        datas += collect_data_files(package)
    except Exception:
        pass

hiddenimports = [
    # Qt-плагины
    "PyQt6.QtCore", "PyQt6.QtGui", "PyQt6.QtWidgets", "PyQt6.QtNetwork",
    # поиск/загрузка
    "yt_dlp.extractor", "yt_dlp.extractor.youtube", "yt_dlp.extractor.soundcloud",
    "yt_dlp.downloader", "yt_dlp.postprocessor",
    # видео/аудио
    "imageio_ffmpeg", "soundfile", "sounddevice", "pydub", "librosa",
    "scipy.signal", "scipy.io.wavfile", "numpy",
    "PIL.Image", "PIL.ImageDraw", "PIL.ImageFont", "cv2",
    "moviepy", "moviepy.editor",
    # публикация
    "googleapiclient.discovery", "googleapiclient.http", "googleapiclient.errors",
    "google_auth_oauthlib.flow", "google.oauth2.credentials",
    "google.auth.transport.requests", "cryptography.hazmat.primitives.ciphers.aead",
    "playwright", "playwright.sync_api",
    # прочее
    "sqlite3", "dataclasses", "logging.handlers", "json", "base64",
    "utils", "core", "ui", "search", "social",
]

# Модули проекта — собираем все, чтобы не пропустить динамические импорты
for package in ("core", "utils", "search", "social", "ui"):
    try:
        hiddenimports += [name for name in collect_submodules(package)
                          if not name.endswith("__main__")]
    except Exception:
        pass

# Опциональные модули: подключаем мягко (если установлены)
OPTIONAL = ["whisper", "tiktok_uploader", "playwright"]
for name in OPTIONAL:
    try:
        __import__(name)
        hiddenimports.append(name)
    except Exception:
        pass

excludes = [
    # экономия размера: не тащим лишние GUI/научные пакеты
    "tkinter", "matplotlib", "PyQt5", "PySide2", "PySide6",
    "IPython", "jupyter", "pytest", "sphinx", "tests",
    "numba", "llvmlite", "torch", "tensorflow",
]

block_cipher = None

a = Analysis(  # noqa: F821
    [str(ROOT / "main.py")],
    pathex=[str(ROOT)],
    binaries=[],
    datas=datas,
    hiddenimports=sorted(set(hiddenimports)),
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=excludes,
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)  # noqa: F821

icon_path = ROOT / "assets" / "icon.ico"
icon_arg = str(icon_path) if icon_path.exists() else None

exe = EXE(  # noqa: F821
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name="BeatForgeStudio",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=["vcruntime140.dll", "python311.dll", "Qt6Core.dll"],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=icon_arg,
)

if sys.platform == "darwin":
    app = BUNDLE(  # noqa: F821
        exe,
        name="BeatForge Studio.app",
        icon=icon_arg,
        bundle_identifier="studio.beatforge.app",
        info_plist={
            "CFBundleShortVersionString": "1.0.0",
            "NSHighResolutionCapable": True,
            "NSRequiresAquaSystemAppearance": False,
        },
    )
