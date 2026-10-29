#!/usr/bin/env python3
"""Package only the versioned Core JAR and safe update instructions.

The archive is deliberately not a server-root overlay: it cannot overwrite
worlds, data folders or configs. Build the Core JAR first.
"""
from pathlib import Path
import hashlib
import re
import shutil
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
PUBLIC = ROOT.parent / "kinggrief" / "public"
CORE = ROOT / "KingGriefCore.jar"
INSTRUCTIONS = ROOT / "ИСПРАВЛЕНИЕ_WATCHDOG.md"


def add_file(archive, name, data):
    info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
    info.external_attr = 0o100644 << 16
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, data, compresslevel=9)


def main():
    core = CORE.read_bytes()
    with zipfile.ZipFile(CORE) as jar:
        plugin_yml = jar.read("plugin.yml").decode("utf8")
        version_match = re.search(r"(?m)^version:\s*['\"]?([0-9]+(?:\.[0-9]+)+)", plugin_yml)
        if not version_match or jar.testzip() is not None:
            raise ValueError("Core JAR has no valid version or is corrupt")
        version = version_match.group(1)
    if (ROOT / "server/plugins/KingGriefCore.jar").read_bytes() != core:
        raise ValueError("Server and patch Core JAR differ")
    if (PUBLIC / f"KingGriefCore-v{version}.jar").read_bytes() != core:
        raise ValueError("Public versioned Core JAR differs")
    instructions = INSTRUCTIONS.read_text(encoding="utf8")
    if f"KingGriefCore {version}" not in instructions:
        raise ValueError("Patch instructions do not match the built version")
    digest = hashlib.sha256(core).hexdigest()
    instructions, count = re.subn(r"(?m)^   `(?:\{\{CORE_SHA256\}\}|[0-9a-f]{64})`$",
                                   f"   `{digest}`", instructions)
    if count != 1:
        raise ValueError("Patch instructions need exactly one Core SHA-256 line")
    INSTRUCTIONS.write_text(instructions, encoding="utf8")
    instructions = instructions.encode("utf8")
    destination = ROOT / f"KingGriefCore-{version}-hotfix.zip"
    with tempfile.TemporaryDirectory(prefix="kinggrief-core-hotfix-", dir=ROOT) as tmp:
        staged = Path(tmp) / destination.name
        with zipfile.ZipFile(staged, "w") as archive:
            add_file(archive, "ПРОЧИТАЙ_СНАЧАЛА.md", instructions)
            add_file(archive, "KingGriefCore.jar", core)
        with zipfile.ZipFile(staged) as archive:
            if archive.testzip() is not None or archive.read("KingGriefCore.jar") != core:
                raise ValueError("Failed to validate the staged hotfix")
        staged.replace(destination)
    PUBLIC.mkdir(parents=True, exist_ok=True)
    shutil.copy2(destination, PUBLIC / destination.name)
    print(f"{destination.name}: {destination.stat().st_size} bytes, SHA-256 {hashlib.sha256(destination.read_bytes()).hexdigest()}")
    print(f"KingGriefCore.jar: SHA-256 {digest}")


if __name__ == "__main__":
    main()
