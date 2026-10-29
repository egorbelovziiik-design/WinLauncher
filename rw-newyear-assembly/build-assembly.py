#!/usr/bin/env python3
"""Build the flat-root grief-server ZIP and publish an identical local web copy.

Only files already present under server/ are included. Paper/Java and the separate
Hub plugin are intentionally not bundled. Symbolic links are rejected.
"""
from pathlib import Path
import hashlib
import shutil
import zipfile
import tempfile

ROOT = Path(__file__).resolve().parent
SERVER = ROOT / "server"
ASSEMBLY = ROOT / "rw-newyear-full-assembly.zip"
PUBLIC = ROOT.parent / "kinggrief/public" / ASSEMBLY.name


def main():
    paths = sorted(SERVER.rglob("*"), key=lambda p: p.relative_to(SERVER).as_posix())
    files = []
    for path in paths:
        if path.is_symlink():
            raise RuntimeError(f"Refusing to archive symlink: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise RuntimeError(f"Not a regular file: {path}")
        files.append(path)
    # Do not truncate the existing downloadable ZIP if preflight/archiving fails.
    with tempfile.TemporaryDirectory(prefix="kinggrief-assembly-", dir=ROOT) as tmp:
        staged = Path(tmp) / ASSEMBLY.name
        with zipfile.ZipFile(staged, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for path in files:
                info = zipfile.ZipInfo(path.relative_to(SERVER).as_posix(), date_time=(2020, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = ((path.stat().st_mode & 0o777) | 0o100000) << 16
                archive.writestr(info, path.read_bytes(), compresslevel=9)
        staged.replace(ASSEMBLY)
    shutil.copy2(ASSEMBLY, PUBLIC)
    print(f"{ASSEMBLY.name}: {ASSEMBLY.stat().st_size} bytes, SHA-256 {hashlib.sha256(ASSEMBLY.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
