#!/usr/bin/env python3
"""Build deterministic Core/Hub plugin JARs from sources and distribute copies.

Requires a JDK and an externally supplied Spigot/Paper API compile classpath.
No API or test-fixture classes are packaged. Paper 1.21.4 + Java 21 is the
supported runtime; compilation alone does not replace an integration test.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def jar_write(jar, name, data):
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.external_attr = 0o100644 << 16
    jar.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build(name, source_dir, args):
    with tempfile.TemporaryDirectory(prefix="kinggrief-build-") as tmp:
        classes = Path(tmp) / "classes"
        classes.mkdir()
        sources = sorted((source_dir / "src/main/java").rglob("*.java"))
        sources += sorted((ROOT / "common-src/src/main/java").rglob("*.java"))
        command = [args.javac, "-encoding", "UTF-8", "-source", "8", "-target", "8",
                   "-classpath", args.api_classpath, "-d", str(classes)] + list(map(str, sources))
        subprocess.run(command, check=True)
        output = ROOT / f"{name}.jar"
        with zipfile.ZipFile(output, "w") as jar:
            jar_write(jar, "META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\r\nCreated-By: KingGrief build\r\n\r\n")
            resources = source_dir / "src/main/resources"
            entries = [(p.relative_to(classes).as_posix(), p.read_bytes()) for p in classes.rglob("*.class")]
            entries += [(p.relative_to(resources).as_posix(), p.read_bytes()) for p in resources.rglob("*") if p.is_file()]
            for path, data in sorted(entries):
                if path.startswith(("org/bukkit/", "java/")):
                    raise ValueError(f"Compile dependency leaked into output: {path}")
                jar_write(jar, path, data)
        return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api-classpath", required=True, help="Spigot API and compile dependencies, separated by the platform classpath separator")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    core = build("KingGriefCore", ROOT / "core-src", args)
    hub = build("KingHub", ROOT / "kinghub-src", args)
    copies = {core: [ROOT / "plugins/KingGriefCore.jar", ROOT / "server/plugins/KingGriefCore.jar", ROOT.parent / "kinggrief/public/KingGriefCore.jar"],
              hub: [ROOT.parent / "kinggrief/public/KingHub.jar"]}
    for source, dests in copies.items():
        for destination in dests:
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        print(f"Built {source.name}: {source.stat().st_size} bytes")


if __name__ == "__main__":
    main()
