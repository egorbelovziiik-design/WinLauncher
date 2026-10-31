#!/usr/bin/env python3
"""Exercise prebuilt spawn upgrade, flushed backup, repeat restart and bad ZIP on Paper."""
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


def args_parser():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--paper", type=Path, required=True)
    p.add_argument("--vanilla", type=Path, required=True)
    p.add_argument("--api-classpath", required=True)
    p.add_argument("--java", default="java")
    p.add_argument("--javac", default="javac")
    p.add_argument("--core", type=Path, default=ROOT / "KingGriefCore.jar")
    p.add_argument("--template", type=Path, default=ROOT / "server/plugins/KingGriefCore/spawn-v74-template.zip")
    p.add_argument("--old-core", type=Path, default=ROOT.parent / "kinggrief/public/KingGriefCore-v6.7.1.jar")
    p.add_argument("--old-template", type=Path, default=ROOT / "server/plugins/KingGriefCore/spawn-v66-template.zip")
    p.add_argument("--only-symlink", action="store_true", help="run only the linked-world refusal test")
    p.add_argument("--accept-eula", action="store_true")
    args = p.parse_args()
    if not args.accept_eula:
        p.error("review https://aka.ms/MinecraftEULA and explicitly pass --accept-eula")
    return args


def cache_name(paper, vanilla):
    with zipfile.ZipFile(paper) as z:
        if json.loads(z.read("version.json"))["id"] != "1.21.4":
            raise ValueError("Paper 1.21.4 required")
        expected, _url, name = z.read("META-INF/download-context").decode().strip().split("\t")
    if Path(name).name != name or hashlib.sha256(vanilla.read_bytes()).hexdigest() != expected:
        raise ValueError("Mojang server jar does not match Paper")
    return name


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def build_test_plugin(args, root, source, yaml, jar):
    classes = root / (source.stem + "-classes")
    classes.mkdir()
    subprocess.run([args.javac, "-encoding", "UTF-8", "-classpath", args.api_classpath,
                    "-d", str(classes), str(source)], check=True)
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(classes.rglob("*.class")):
            z.write(path, path.relative_to(classes).as_posix())
        z.write(yaml, "plugin.yml")


def run_server(args, server, mode):
    print("Starting Paper:", mode, flush=True)
    config = server / "plugins/KgSpawnInstallProbe/config.yml"
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(f"mode: {mode}\n", encoding="utf-8")
    try:
        result = subprocess.run([args.java, "-Xms512M", "-Xmx1536M", "-jar", str(args.paper.resolve()), "nogui"],
                                cwd=server, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, timeout=360)
    except subprocess.TimeoutExpired as e:
        print((e.stdout or b"")[-30000:], file=sys.stderr)
        raise RuntimeError(f"Paper timed out during {mode}")
    log = result.stdout
    if mode in ("corrupt", "corrupt-fresh", "symlink"):
        expected = "Selected spawn is a symbolic link" if mode == "symlink" else "Template checksum mismatch"
        if (result.returncode != 0 or expected not in log
                or "Server is stopping after a failed spawn install" not in log):
            print("\n--- SERVER LOG TAIL ---\n" + "\n".join(log.splitlines()[-150:]), file=sys.stderr)
            raise RuntimeError(f"Paper did not stop safely after {mode}")
        print(f"KG-SPAWN-INSTALL PASS: {mode} rejected; server stopped safely", flush=True)
        return
    success_line = f"KG-SPAWN-INSTALL PASS: {mode}"
    if result.returncode != 0 or success_line not in log or "KG-SPAWN-INSTALL FAIL" in log:
        print("\n--- SERVER LOG TAIL ---\n" + "\n".join(log.splitlines()[-150:]), file=sys.stderr)
        raise RuntimeError(f"Paper spawn install test failed: {mode} (exit {result.returncode})")
    if mode == "seed" and "Prepared v66 spawn installed and initialized" not in log:
        raise AssertionError("v6.7.1 seed world was not installed")
    if mode == "installed":
        if "KG-PRELOAD: old spawn loaded and changed in memory" not in log:
            raise AssertionError("existing world was not preloaded by the test plugin")
        if "Prepared v74 spawn installed and initialized" not in log or "Previous spawn saved intact" not in log:
            raise AssertionError("prepared spawn was not installed over the existing world")
        if "Building the v7.0 Winter Village Spawn" in log:
            raise AssertionError("prepared world was unexpectedly regenerated")
    if mode == "repeat" and ("Prepared v74 spawn installed and initialized" in log or "Building the v7.0 Winter Village Spawn" in log):
        raise AssertionError(f"{mode} startup unexpectedly replaced or rebuilt the world")
    print(success_line, flush=True)


def fingerprint(directory):
    # These are isolated test-world files, not user data. Any mutation during a
    # rejected install (including newly created files) is a failure.
    return {p.relative_to(directory).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in directory.rglob("*") if p.is_file()}


def main():
    args = args_parser()
    cache = cache_name(args.paper, args.vanilla)
    with tempfile.TemporaryDirectory(prefix="kinggrief-prebuilt-test-") as tmp:
        root = Path(tmp)
        server = root / "server"
        data = server / "plugins/KingGriefCore"
        data.mkdir(parents=True)
        (server / "cache").mkdir()
        shutil.copy2(args.vanilla, server / "cache" / cache)
        shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
        (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
        (data / "config.yml").write_text("enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n", encoding="utf-8")
        (server / "server.properties").write_text(
            f"online-mode=false\nserver-ip=127.0.0.1\nserver-port={free_port()}\n"
            "view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n"
            "level-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\n", encoding="utf-8")
        build_test_plugin(args, root, HERE / "SpawnInstallProbe.java", HERE / "plugin-spawn-install.yml", server / "plugins/KgSpawnInstallProbe.jar")
        preload_jar = root / "KgSpawnPreloadProbe.jar"
        build_test_plugin(args, root, HERE / "SpawnPreloadProbe.java", HERE / "plugin-spawn-preload.yml", preload_jar)
        if args.only_symlink:
            linked_target = root / "linked-user-world"
            linked_target.mkdir()
            (linked_target / "user-data.txt").write_text("keep linked data\n", encoding="utf-8")
            (server / "spawn_world").symlink_to(linked_target, target_is_directory=True)
            (data / ".spawn-v72-rebuild-pending").write_text("v71\n", encoding="ascii")
            shutil.copy2(args.template, data / "spawn-v74-template.zip")
            before = fingerprint(linked_target)
            run_server(args, server, "symlink")
            if not (server / "spawn_world").is_symlink() or fingerprint(linked_target) != before:
                raise AssertionError("linked world changed during a refused install")
            print("PREBUILT SYMLINK RESULT: PASS", flush=True)
            return
        (data / "spawn-v74-template.zip").write_bytes(b"intentionally corrupted template")
        run_server(args, server, "corrupt-fresh")
        if (server / "spawn_world").exists():
            raise AssertionError("corrupted template created a world on a fresh server")
        (data / "spawn-v74-template.zip").unlink()
        # An actual older Core and v66 template exercise migration, not only a
        # synthetic old marker. The new version must honour its separate marker.
        shutil.copy2(args.old_core, server / "plugins/KingGriefCore.jar")
        shutil.copy2(args.old_template, data / "spawn-v66-template.zip")
        run_server(args, server, "seed")
        if not (data / ".spawn-v66-template-installed").is_file():
            raise AssertionError("old v66 install marker missing")
        shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
        shutil.copy2(args.template, data / "spawn-v74-template.zip")
        shutil.copy2(preload_jar, server / "plugins/KgSpawnPreloadProbe.jar")
        # 7.2 never replaces an existing world merely because a template exists.
        (data / ".spawn-v72-rebuild-pending").write_text("v71\n", encoding="ascii")
        run_server(args, server, "installed")
        (server / "plugins/KgSpawnPreloadProbe.jar").unlink()
        run_server(args, server, "repeat")
        # Restore the old backup while retaining the one-time marker. It must
        # stay restored even though the template is still present.
        backup = next(server.glob("spawn_world_backup_*"))
        (server / "spawn_world").rename(server / "spawn_world_new_after_install")
        shutil.copytree(backup, server / "spawn_world")
        # Deliberately load the restored legacy world; 7.2 leaves it to the operator.
        shutil.copy2(preload_jar, server / "plugins/KgSpawnPreloadProbe.jar")
        run_server(args, server, "restored")
        (server / "plugins/KgSpawnPreloadProbe.jar").unlink()
        (data / ".spawn-v70-template-installed").unlink()
        (data / ".spawn-v72-rebuild-pending").write_text("v71\n", encoding="ascii")
        (data / "spawn-v74-template.zip").write_bytes(b"intentionally corrupted template")
        old_files = fingerprint(server / "spawn_world")
        run_server(args, server, "corrupt")
        if fingerprint(server / "spawn_world") != old_files or len(list(server.glob("spawn_world_backup_*"))) != 1:
            raise AssertionError("rejected template changed or moved the existing world")
        # Linked worlds can point outside the server; never follow or replace them.
        shutil.copy2(args.template, data / "spawn-v74-template.zip")
        selected = server / "kinggrief_spawn"
        linked_target = server / "kinggrief_spawn_linked_test_target"
        selected_before = fingerprint(selected)
        selected.rename(linked_target)
        selected.symlink_to(linked_target.name, target_is_directory=True)
        run_server(args, server, "symlink")
        if not selected.is_symlink() or fingerprint(linked_target) != selected_before or fingerprint(server / "spawn_world") != old_files:
            raise AssertionError("linked or original world changed during a refused install")
    print("PREBUILT INSTALLATION RESULT: PASS", flush=True)


if __name__ == "__main__":
    main()
