#!/usr/bin/env python3
"""Run a disposable Paper 1.21.4 integration test using local, supplied jars."""
import argparse
import hashlib
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
from zipfile import ZipFile, ZIP_DEFLATED

HERE = Path(__file__).resolve().parent
DEFAULT_CORE = HERE.parents[3] / "KingGriefCore.jar"
EXPECTED_VERSION = "1.21.4"


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper", type=Path, required=True, help="Paper 1.21.4 paperclip jar")
    parser.add_argument("--vanilla", type=Path, required=True, help="matching Mojang server jar for the paperclip cache")
    parser.add_argument("--api-classpath", required=True, help="Spigot 1.21 API jar and compile dependencies, separated by the platform classpath separator")
    parser.add_argument("--node-modules", type=Path, required=True, help="node_modules containing minecraft-protocol 1.62")
    parser.add_argument("--core", type=Path, default=DEFAULT_CORE, help="KingGriefCore jar under test")
    parser.add_argument("--java", default="java", help="Java 21 executable for Paper")
    parser.add_argument("--javac", default="javac", help="Java compiler executable")
    parser.add_argument("--node", default="node", help="Node.js executable")
    parser.add_argument("--accept-eula", action="store_true", help="accept the Minecraft EULA for the temporary test server")
    args = parser.parse_args()
    if not args.accept_eula:
        parser.error("the disposable server requires --accept-eula; review https://aka.ms/MinecraftEULA first")
    return args


def validate_paper_and_vanilla(paper, vanilla):
    import json
    with ZipFile(paper) as archive:
        version = json.loads(archive.read("version.json"))["id"]
        if version != EXPECTED_VERSION:
            raise ValueError(f"Expected Paper {EXPECTED_VERSION}, found {version}")
        sha256, _url, filename = archive.read("META-INF/download-context").decode().strip().split("\t")
    if hashlib.sha256(vanilla.read_bytes()).hexdigest() != sha256:
        raise ValueError("The supplied vanilla jar does not match this Paper build's SHA-256")
    if Path(filename).name != filename:
        raise ValueError("Invalid Paper cache filename")
    return filename


def available_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def build_probe(args, root):
    classes = root / "probe-classes"
    classes.mkdir()
    classpath = str(args.core.resolve()) + os.pathsep + args.api_classpath
    subprocess.run([args.javac, "-encoding", "UTF-8", "-classpath", classpath,
                    "-d", str(classes), str(HERE / "ProbePlugin.java")], check=True)
    plugin = root / "server/plugins/KgProbe.jar"
    with ZipFile(plugin, "w", ZIP_DEFLATED) as archive:
        for path in sorted(classes.rglob("*.class")):
            archive.write(path, path.relative_to(classes).as_posix())
        archive.write(HERE / "plugin.yml", "plugin.yml")


def launch_and_check(args, server, port):
    process = subprocess.Popen([args.java, "-Xms512M", "-Xmx1536M", "-jar", str(args.paper.resolve()), "nogui"],
                               cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, bufsize=1)
    output, messages = [], queue.Queue()

    def read_server():
        for line in process.stdout:
            output.append(line)
            messages.put(line)

    threading.Thread(target=read_server, daemon=True).start()
    client = None
    success = False
    try:
        started = False
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            try:
                line = messages.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None:
                    break
                continue
            if "Done (" in line and "For help" in line:
                print("SERVER STARTED:", line.strip())
                started = True
                break
            if "Error occurred while enabling KingGriefCore" in line:
                break
        if not started:
            return False
        env = os.environ.copy()
        env["NODE_PATH"] = str(args.node_modules.resolve())
        client = subprocess.Popen([args.node, str(HERE / "client.cjs"), str(port)],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
        deadline = time.monotonic() + 70
        while time.monotonic() < deadline:
            try:
                line = messages.get(timeout=1)
            except queue.Empty:
                if process.poll() is not None or client.poll() is not None:
                    break
                continue
            if "KG-PROBE PASS" in line:
                print("SERVER ASSERTIONS:", line.strip())
                success = True
                break
            if "KG-PROBE FAIL" in line:
                print("SERVER ASSERTIONS:", line.strip())
                break
        try:
            client_output = client.communicate(timeout=30)[0]
        except subprocess.TimeoutExpired:
            client.kill()
            client_output = client.communicate()[0]
        print(client_output, end="")
        success = success and client.returncode == 0
        return success
    finally:
        if client and client.poll() is None:
            client.kill()
            client.wait()
        if process.poll() is None:
            try:
                process.stdin.write("stop\n")
                process.stdin.flush()
                process.wait(timeout=40)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                process.kill()
                process.wait()
        if not success:
            print("\n--- SERVER LOG TAIL ---", file=sys.stderr)
            print("".join(output[-150:]), file=sys.stderr)


def main():
    args = arguments()
    cache_name = validate_paper_and_vanilla(args.paper, args.vanilla)
    with tempfile.TemporaryDirectory(prefix="kinggrief-paper-test-") as directory:
        root = Path(directory)
        server = root / "server"
        (server / "plugins/KingGriefCore").mkdir(parents=True)
        (server / "cache").mkdir()
        shutil.copy2(args.vanilla, server / "cache" / cache_name)
        shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
        (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (server / "plugins/KingGriefCore/config.yml").write_text(
            'enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\n'
            'push-resourcepack-on-join: true\n'
            'resourcepack-url: "https://kinggrief.vercel.app/kinggrief-mega-rp.zip?v=63"\n'
            'resourcepack-sha1: "3637015af079e6fea621c10fe6dfa51e35cc0113"\n', encoding="utf-8")
        port = available_port()
        (server / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n"
            "view-distance=2\nsimulation-distance=3\nallow-nether=false\n"
            "spawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
            "max-players=5\nresource-pack=\nresource-pack-sha1=\n", encoding="utf-8")
        build_probe(args, root)
        success = launch_and_check(args, server, port)
    print("INTEGRATION RESULT:", "PASS" if success else "FAIL")
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
