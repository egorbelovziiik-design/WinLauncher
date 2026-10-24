#!/usr/bin/env python3
"""
Сборка КП-KingGrief в один исполняемый файл (PyInstaller).

Запуск:
    python build/build_exe.py            # сборка для текущей ОС
    python build/build_exe.py --clean    # с очисткой кэша

ВАЖНО: PyInstaller не умеет кросс-компиляцию. EXE для Windows можно
собрать только на Windows (или в CI-задаче `build-windows.yml`).
"""

from __future__ import annotations

import argparse
import platform
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "build" / "KP-KingGrief.spec"
DIST = ROOT / "dist"
APP_NAME = "КП-KingGrief"


def exe_name() -> str:
    return f"{APP_NAME}.exe" if platform.system() == "Windows" else APP_NAME


def ensure_pyinstaller() -> None:
    try:
        import PyInstaller  # noqa: F401
    except ImportError:
        print("→ PyInstaller не найден, устанавливаю…")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])


def build(clean: bool = False) -> int:
    ensure_pyinstaller()

    if clean:
        for folder in (ROOT / "build_temp", DIST):
            if folder.exists():
                shutil.rmtree(folder)

    command = [
        sys.executable,
        "-m",
        "PyInstaller",
        str(SPEC),
        "--noconfirm",
        "--clean",
        "--distpath",
        str(DIST),
        "--workpath",
        str(ROOT / "build_temp"),
    ]
    print("→", " ".join(command))
    result = subprocess.run(command, cwd=str(ROOT))
    if result.returncode != 0:
        print(f"\n❌ Сборка завершилась ошибкой (код {result.returncode}).")
        return result.returncode

    artifact = DIST / exe_name()
    if not artifact.exists():
        print(f"\n❌ Ожидаемый файл не найден: {artifact}")
        return 1

    size_mb = artifact.stat().st_size / (1024 * 1024)
    print(
        "\n" + "━" * 58 + "\n"
        f"✅ Сборка завершена\n"
        f"   📦 {artifact}\n"
        f"   📏 {size_mb:.1f} МБ\n"
        f"   💻 {platform.system()} {platform.release()}\n"
        + "━" * 58
    )
    print(
        "\n⚠️  Не забудьте положить рядом с программой файл `.env` с DISCORD_TOKEN.\n"
        "    Токен намеренно не встраивается в исполняемый файл."
    )
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Сборка КП-KingGrief")
    parser.add_argument("--clean", action="store_true", help="очистить кэш перед сборкой")
    args = parser.parse_args()

    if platform.system() != "Windows":
        print(
            f"⚠️  Текущая ОС: {platform.system()}. PyInstaller не поддерживает "
            "кросс-компиляцию — будет собран исполняемый файл для этой ОС.\n"
            "    Для Windows EXE используйте build/build.bat на Windows "
            "или workflow .github/workflows/build-windows.yml.\n"
        )
    return build(clean=args.clean)


if __name__ == "__main__":
    sys.exit(main())
