# PyInstaller spec: DEAD WORLD: APOCALYPSE (onedir, windowed)
# Run from the DEAD_WORLD_APOCALYPSE folder:  pyinstaller build/DeadWorldApocalypse.spec
import os
from PyInstaller.utils.hooks import collect_all

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(SPEC)))

datas, binaries, hiddenimports = [], [], []
for pkg in ("ursina", "panda3d", "direct"):
    d, b, h = collect_all(pkg)
    datas += d
    binaries += b
    hiddenimports += h

# Game assets: runtime needs .obj/.json/.png/.wav/.ttf only.
# .blend/.glb DCC sources stay in the repo but are excluded from the build to keep it lean.
def add_assets():
    out = []
    assets_root = os.path.join(ROOT, "assets")
    keep_ext = {".obj", ".json", ".png", ".wav", ".ttf"}
    for dirpath, _dirs, files in os.walk(assets_root):
        for f in files:
            if os.path.splitext(f)[1].lower() in keep_ext:
                src = os.path.join(dirpath, f)
                dst = os.path.relpath(dirpath, ROOT)
                out.append((src, dst))
    return out

datas += add_assets()

icon_path = os.path.join(ROOT, "build", "icon.ico")

a = Analysis(
    [os.path.join(ROOT, "main.py")],
    pathex=[ROOT],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports + ["ursina", "direct.showbase.ShowBase"],
    hookspath=[],
    runtime_hooks=[],
    excludes=["bpy", "pytest", "numpy.tests"],
    noarchive=False,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name="DeadWorldApocalypse",
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,          # windowed game; --selftest writes selftest_result.txt
    icon=icon_path if os.path.exists(icon_path) else None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    name="DeadWorldApocalypse",
)
