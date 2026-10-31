#!/usr/bin/env python3
"""Build the flat-root grief-server ZIP and publish an identical local web copy.

Only files already present under server/ are included. Paper/Java and the separate
Hub plugin are intentionally not bundled. Symbolic links are rejected.
"""
from pathlib import Path
import hashlib
import re
import shutil
import zipfile
import tempfile

ROOT = Path(__file__).resolve().parent
SERVER = ROOT / "server"
ASSEMBLY = ROOT / "rw-newyear-full-assembly-v7.3.0.zip"
PUBLIC = ROOT.parent / "kinggrief/public" / ASSEMBLY.name
TEMPLATE = SERVER / "plugins/KingGriefCore/spawn-v73-template.zip"
PUBLIC_TEMPLATE = ROOT.parent / "kinggrief/public/kinggrief-spawn-v73.zip"
CHECKSUM = ROOT / "core-src/src/main/resources/spawn_v73_template.sha256"
CORE = SERVER / "plugins/KingGriefCore.jar"


def verify_spawn_template():
    # Never put world files at server root: extracting over an existing server
    # must not replace its world before Core can back it up safely.
    for name in ("spawn_world", "kinggrief_spawn", "kinggrief_spawn_2"):
        if (SERVER / name).exists() or (SERVER / name).is_symlink():
            raise RuntimeError("Do not package a runtime world; use the checked template")
    if TEMPLATE.is_symlink() or not TEMPLATE.is_file():
        raise RuntimeError("Missing regular prebuilt spawn template")
    template = TEMPLATE.read_bytes()
    digest = hashlib.sha256(template).hexdigest()
    expected = CHECKSUM.read_text(encoding="ascii").strip()
    if len(expected) != 64 or digest != expected:
        raise RuntimeError("Spawn template does not match Core's recorded SHA-256")
    with zipfile.ZipFile(CORE) as jar:
        if not re.search(r"(?m)^version:\s*7\.3\.0\s*$", jar.read("plugin.yml").decode("utf-8")):
            raise RuntimeError("Assembly needs KingGriefCore 7.3.0")
        if jar.read("spawn_v73_template.sha256").decode("ascii").strip() != digest:
            raise RuntimeError("Built Core JAR does not match the spawn template; rebuild Core")
        if hashlib.sha256(jar.read("spawn_v73_template.zip")).hexdigest() != digest:
            raise RuntimeError("Embedded Core template does not match the distributed template")
        if "net/kinggrief/core/PreparedSpawnInstaller.class" not in jar.namelist():
            raise RuntimeError("Built Core cannot install the prepared spawn")
    with zipfile.ZipFile(TEMPLATE) as world:
        if world.testzip() is not None:
            raise RuntimeError("Corrupt prebuilt spawn template")
        names = world.namelist()
        if len(names) != len(set(names)):
            raise RuntimeError("Duplicate prebuilt spawn paths")
        required = {"level.dat", ".spawn_v70_village_ok", ".kinggrief_prebuilt_v70", ".kinggrief_prebuilt_v73"}
        if not required.issubset(names) or not any(n.startswith("region/") and n.endswith(".mca") for n in names):
            raise RuntimeError("Prebuilt spawn is missing required world files")
        if "data/chunks.dat" in names:
            raise RuntimeError("Spawn template must not contain persistent forced-chunk tickets")
        if any(n.startswith(("entities/", "playerdata/", "stats/", "advancements/"))
               or n in {"uid.dat", "session.lock", "level.dat_old"} for n in names):
            raise RuntimeError("Prebuilt spawn contains runtime/player-specific data")
    return template


def verify_distribution_hygiene():
    for name, version in (("KingAC", "8.3.1"), ("KingClear", "1.0.2")):
        with zipfile.ZipFile(SERVER / "plugins" / f"{name}.jar") as jar:
            if jar.testzip() is not None or not re.search(r"(?m)^version:\s*" + re.escape(version) + r"\s*$", jar.read("plugin.yml").decode("utf-8")):
                raise RuntimeError(f"Bundled {name} must be version {version}")
    # The public download must never package the operator's credentials or runtime data.
    bridge = SERVER / "plugins/KingGriefBridge/config.yml"
    if bridge.is_symlink() or not bridge.is_file():
        raise RuntimeError("Missing regular KingGriefBridge example config")
    config = bridge.read_text(encoding="utf-8")
    if not re.search(r"(?m)^secret:\s*[\"']?CHANGE_ME[\"']?\s*$", config):
        raise RuntimeError("Bridge credential must be CHANGE_ME in the public assembly")
    runtime = ("world", "world_nether", "world_the_end", "logs", "cache", "libraries", "versions",
               "plugins/.paper-remapped", "plugins/KingGriefCore/data.yml", "plugins/KingGriefCore/safe-trades",
               "plugins/KingGriefCore/buy-orders", "ops.json", "whitelist.json", "usercache.json",
               "banned-players.json", "banned-ips.json", "eula.txt", "server.jar", "paper.jar")
    for name in runtime:
        path = SERVER / name
        if path.exists() or path.is_symlink():
            raise RuntimeError(f"Refusing to package runtime or player data: {name}")


def main():
    template = verify_spawn_template()
    verify_distribution_hygiene()
    paths = sorted(SERVER.rglob("*"), key=lambda p: p.relative_to(SERVER).as_posix())
    files = []
    for path in paths:
        if path.is_symlink():
            raise RuntimeError(f"Refusing to archive symlink: {path}")
        if path.is_dir():
            continue
        if not path.is_file():
            raise RuntimeError(f"Not a regular file: {path}")
        # Historical templates stay in the repository/download history, but
        # new assemblies ship only the clean non-pinned v73 template.
        if path.relative_to(SERVER).as_posix() in {"plugins/KingGriefCore/spawn-v66-template.zip", "plugins/KingGriefCore/spawn-v70-template.zip", "plugins/KingGriefCore/spawn-v71-template.zip"}:
            continue
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
    shutil.copy2(SERVER / "АРТЕФАКТЫ_7.2.0.md", ROOT.parent / "kinggrief/public/KingGrief-50-artifacts-7.2.0.md")
    with tempfile.NamedTemporaryFile(prefix="kinggrief-spawn-publish-", dir=PUBLIC_TEMPLATE.parent, delete=False) as staged:
        staged_path = Path(staged.name)
        try:
            staged.write(template)
            staged.close()
            staged_path.chmod(0o644)
            staged_path.replace(PUBLIC_TEMPLATE)
        finally:
            staged_path.unlink(missing_ok=True)
    print(f"{ASSEMBLY.name}: {ASSEMBLY.stat().st_size} bytes, SHA-256 {hashlib.sha256(ASSEMBLY.read_bytes()).hexdigest()}")
    print(f"{PUBLIC_TEMPLATE.name}: {len(template)} bytes, SHA-256 {hashlib.sha256(template).hexdigest()}")


if __name__ == "__main__":
    main()
