"""Build ``MADorigg.exe`` with PyInstaller.

PyInstaller cannot cross-compile: this script must run **on Windows** (or in a
``windows-latest`` GitHub Actions runner, see ``.github/workflows/madorigg-exe.yml``).

Usage
-----
    python tools/make_icon.py      # once, creates assets/icon.ico
    python tools/build_exe.py      # -> dist/MADorigg.exe
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
BUILD = ROOT / "build"
ICON = ROOT / "assets" / "icon.ico"


def main() -> int:
    """Run PyInstaller with the flags the show needs."""
    if sys.platform != "win32":
        print("This builds a Windows executable and must run on Windows.")
        print("Use the GitHub Actions workflow or a Windows VM instead.")
        return 2

    if not ICON.exists():
        print("Icon missing - generating it first...")
        subprocess.call([sys.executable, str(ROOT / "tools" / "make_icon.py")], cwd=ROOT)

    if DIST.exists():
        shutil.rmtree(DIST, ignore_errors=True)
    BUILD.mkdir(parents=True, exist_ok=True)

    command = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--noconfirm",
        "--clean",
        "--onefile",
        "--windowed",
        "--name",
        "MADorigg",
        "--distpath",
        str(DIST),
        "--workpath",
        str(BUILD / "work"),
        "--specpath",
        str(BUILD),
        "--hidden-import",
        "comtypes",
        "--hidden-import",
        "pycaw",
        "--hidden-import",
        "pycaw.pycaw",
        "--collect-submodules",
        "comtypes",
        "--collect-submodules",
        "pycaw",
        "--collect-submodules",
        "pygame",
    ]
    if ICON.exists():
        command += ["--icon", str(ICON)]
    command.append(str(ROOT / "main.py"))

    print(" ".join(command))
    code = subprocess.call(command, cwd=ROOT)
    if code == 0:
        exe = DIST / "MADorigg.exe"
        size_mb = exe.stat().st_size / (1024 * 1024) if exe.exists() else 0.0
        print(f"\nDONE -> {exe} ({size_mb:.1f} MB)")
    else:
        print("\nBuild failed. Check the PyInstaller log above.")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
