#!/usr/bin/env python3
"""Exercise non-blocking event placement and cleanup on a disposable Paper server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--paper", type=Path, required=True)
    p.add_argument("--vanilla", type=Path, required=True)
    p.add_argument("--api-classpath", required=True)
    p.add_argument("--core", type=Path, default=ROOT / "KingGriefCore.jar")
    p.add_argument("--template", type=Path, default=ROOT / "server/plugins/KingGriefCore/spawn-v66-template.zip")
    p.add_argument("--java", default="java")
    p.add_argument("--javac", default="javac")
    p.add_argument("--accept-eula", action="store_true")
    args = p.parse_args()
    if not args.accept_eula:
        p.error("review https://aka.ms/MinecraftEULA and explicitly pass --accept-eula")
    with zipfile.ZipFile(args.paper) as z:
        if json.loads(z.read("version.json"))["id"] != "1.21.4":
            raise ValueError("Paper 1.21.4 required")
        digest, _, cache_name = z.read("META-INF/download-context").decode().strip().split("\t")
    if Path(cache_name).name != cache_name or hashlib.sha256(args.vanilla.read_bytes()).hexdigest() != digest:
        raise ValueError("Mojang server jar does not match Paper")
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        port = s.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix="kinggrief-event-search-test-") as tmp:
        root = Path(tmp)
        server = root / "server"
        data = server / "plugins/KingGriefCore"
        data.mkdir(parents=True)
        (server / "cache").mkdir()
        shutil.copy2(args.vanilla, server / "cache" / cache_name)
        shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
        shutil.copy2(args.template, data / "spawn-v66-template.zip")
        (data / "config.yml").write_text("enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n", encoding="utf8")
        (server / "eula.txt").write_text("eula=true\n", encoding="utf8")
        (server / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n"
            "view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n"
            "level-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\n", encoding="utf8")
        classes = root / "classes"
        classes.mkdir()
        cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
        subprocess.run([args.javac, "-encoding", "UTF-8", "-classpath", cp, "-d", str(classes), str(HERE / "EventSearchProbe.java")], check=True)
        with zipfile.ZipFile(server / "plugins/KgEventSearchProbe.jar", "w", zipfile.ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob("*.class")):
                z.write(path, path.relative_to(classes).as_posix())
            z.write(HERE / "plugin-event-search.yml", "plugin.yml")
        try:
            result = subprocess.run([args.java, "-Xms512M", "-Xmx1536M", "-jar", str(args.paper.resolve()), "nogui"],
                                    cwd=server, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, timeout=360)
        except subprocess.TimeoutExpired as error:
            print((error.stdout or b"")[-30000:], file=sys.stderr)
            raise RuntimeError("Paper did not complete the event-search probe")
        log = result.stdout
        good = (result.returncode == 0 and "KG-EVENT-SEARCH PASS:" in log
                and "KG-EVENT-SEARCH FAIL:" not in log
                and "The server has not responded for 10 seconds" not in log)
        if not good:
            print("\n--- SERVER LOG TAIL ---\n" + "\n".join(log.splitlines()[-220:]), file=sys.stderr)
            raise RuntimeError(f"event-search probe failed (exit {result.returncode})")
        for line in log.splitlines():
            if "KG-EVENT-SEARCH PASS:" in line:
                print(line, flush=True)
    print("EVENT SEARCH INTEGRATION: PASS", flush=True)


if __name__ == "__main__":
    main()
