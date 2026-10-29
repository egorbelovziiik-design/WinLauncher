#!/usr/bin/env python3
"""Generate a clean, pre-built spawn-world template on a disposable Paper server.

The template contains world files at the ZIP root, not a server root. It never
contains players, the Paper/Mojang JARs, a world UUID, or runtime NPC entities.
The real server's Core recreates its NPCs/holograms after loading the template.
"""
import argparse
import hashlib
import json
import queue
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import zipfile

ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT = ROOT / "server/plugins/KingGriefCore/spawn-v66-template.zip"


def arguments():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--paper", type=Path, required=True, help="Paper 1.21.4 paperclip JAR")
    p.add_argument("--vanilla", type=Path, required=True, help="matching Mojang server JAR")
    p.add_argument("--core", type=Path, default=ROOT / "KingGriefCore.jar")
    p.add_argument("--java", default="java", help="Java 21 executable")
    p.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    p.add_argument("--update-checksum", action="store_true", help="write the template SHA-256 into Core's resources before rebuilding the JAR")
    p.add_argument("--accept-eula", action="store_true", help="accept the Minecraft EULA for this disposable build server")
    args = p.parse_args()
    if not args.accept_eula:
        p.error("review https://aka.ms/MinecraftEULA and pass --accept-eula for the disposable build server")
    return args


def paper_cache_filename(paper, vanilla):
    with zipfile.ZipFile(paper) as jar:
        if json.loads(jar.read("version.json"))["id"] != "1.21.4":
            raise ValueError("The spawn template must be generated with Paper 1.21.4")
        sha256, _url, filename = jar.read("META-INF/download-context").decode().strip().split("\t")
    if Path(filename).name != filename or hashlib.sha256(vanilla.read_bytes()).hexdigest() != sha256:
        raise ValueError("Mojang JAR does not match the Paper download context")
    return filename


def available_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def generate_world(args, server):
    cache_name = paper_cache_filename(args.paper, args.vanilla)
    (server / "plugins/KingGriefCore").mkdir(parents=True)
    (server / "cache").mkdir()
    shutil.copy2(args.vanilla, server / "cache" / cache_name)
    shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (server / "plugins/KingGriefCore/config.yml").write_text(
        "enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\n"
        "push-resourcepack-on-join: false\n", encoding="utf-8")
    (server / "server.properties").write_text(
        f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={available_port()}\n"
        "view-distance=2\nsimulation-distance=3\nallow-nether=false\n"
        "spawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
        "max-players=1\nresource-pack=\nresource-pack-sha1=\n", encoding="utf-8")
    process = subprocess.Popen([args.java, "-Xms512M", "-Xmx1536M", "-jar", str(args.paper.resolve()), "nogui"],
                               cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    messages, output = queue.Queue(), []

    def collect():
        for line in process.stdout:
            output.append(line)
            messages.put(line)
    threading.Thread(target=collect, daemon=True).start()
    built = started = False
    try:
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            try:
                line = messages.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if "Spawn built (" in line:
                print(line.strip(), flush=True)
                built = True
            if "Done (" in line and "For help" in line:
                print(line.strip(), flush=True)
                started = True
                break
            if "Error occurred while enabling KingGriefCore" in line:
                break
        if not built or not started:
            raise RuntimeError("Paper did not successfully build the spawn")
        process.stdin.write("stop\n")
        process.stdin.flush()
        process.wait(timeout=120)
        if process.returncode != 0:
            raise RuntimeError(f"Paper failed to save the spawn (exit {process.returncode})")
    except BaseException:
        if process.poll() is None:
            process.kill()
            process.wait()
        print("\n".join(output[-150:]), file=sys.stderr)
        raise
    world = server / "spawn_world"
    if not (world / ".spawn_v66_royal_ok").is_file() or not (world / "level.dat").is_file():
        raise RuntimeError("Generated world lacks its v66 marker or level.dat")
    if not list((world / "region").glob("r.*.mca")):
        raise RuntimeError("Generated world has no saved region chunks")
    return world


def archive_world(world, output):
    # Runtime entities are regenerated by Core. Reusing uid.dat would duplicate
    # Bukkit world UUIDs across servers; session.lock must never be distributed.
    excluded_roots = {"entities", "playerdata", "stats", "advancements"}
    excluded_files = {"uid.dat", "session.lock", "level.dat_old"}
    paths = []
    for path in sorted(world.rglob("*")):
        if path.is_symlink():
            raise RuntimeError(f"Refusing symbolic link in generated world: {path}")
        if not path.is_file():
            continue
        rel = path.relative_to(world)
        if rel.parts[0] in excluded_roots or rel.as_posix() in excluded_files:
            continue
        paths.append((rel.as_posix(), path.read_bytes()))
    paths.append((".kinggrief_prebuilt_v66", b"KINGGRIEF Royal Winter spawn v66; Paper 1.21.4\n"))
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="kinggrief-spawn-archive-", dir=output.parent) as temp:
        staged = Path(temp) / output.name
        with zipfile.ZipFile(staged, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, data in sorted(paths):
                entry = zipfile.ZipInfo(name, (2020, 1, 1, 0, 0, 0))
                entry.external_attr = 0o100644 << 16
                z.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        with zipfile.ZipFile(staged) as z:
            if z.testzip() is not None:
                raise RuntimeError("Generated spawn ZIP failed its CRC check")
        staged.replace(output)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    print(f"SPAWN TEMPLATE: {output} ({output.stat().st_size} bytes; SHA-256 {digest})", flush=True)
    return digest


def main():
    args = arguments()
    with tempfile.TemporaryDirectory(prefix="kinggrief-spawn-build-") as temp:
        world = generate_world(args, Path(temp) / "server")
        digest = archive_world(world, args.output)
    if args.update_checksum:
        resource = ROOT / "core-src/src/main/resources/spawn_v66_template.sha256"
        resource.write_text(digest + "\n", encoding="ascii")
        print("Updated", resource, "— rebuild Core before distributing this template.", flush=True)


if __name__ == "__main__":
    main()
