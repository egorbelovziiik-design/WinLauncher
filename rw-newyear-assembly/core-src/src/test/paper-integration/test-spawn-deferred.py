#!/usr/bin/env python3
"""Verify embedded spawn, live queue/cancel, backups and interrupted swap recovery on Paper."""
import importlib.util
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("prebuilt_helper", HERE / "test-prebuilt-install.py")
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def run(args, server, mode, recovery=False):
    print("Starting Paper:", mode, flush=True)
    config = server / "plugins/KgSpawnRebuildProbe/config.yml"
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(f"mode: {mode}\n", encoding="utf-8")
    try:
        proc = subprocess.run([args.java, "-Xms512M", "-Xmx1536M", "-jar", str(args.paper.resolve()), "nogui"],
                              cwd=server, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, text=True, timeout=360)
    except subprocess.TimeoutExpired as error:
        print((error.stdout or b"")[-30000:], file=sys.stderr)
        raise RuntimeError(f"Paper timeout during {mode}")
    log = proc.stdout
    expected = "Interrupted swap recovered" if recovery else f"KG-REBUILD RESULT PASS {mode}"
    if proc.returncode != 0 or expected not in log or "KG-REBUILD FAIL" in log or "The server has stopped responding!" in log:
        print("\n--- SERVER LOG TAIL ---\n" + "\n".join(log.splitlines()[-180:]), file=sys.stderr)
        raise RuntimeError(f"Paper rebuild test failed: {mode}")
    if mode == "queue" and ("Prepared v73 spawn installed and initialized" not in log or "Building the v7.0 Winter Village Spawn" in log):
        raise AssertionError("fresh standalone Core did not use embedded prepared world")
    for line in log.splitlines():
        if "KG-REBUILD " in line or "Interrupted swap recovered" in line:
            print(line, flush=True)
    return log


def server_setup(args, root, cache, main_spawn=False):
    server = root / "server"
    data = server / "plugins/KingGriefCore"
    data.mkdir(parents=True)
    (server / "cache").mkdir()
    shutil.copy2(args.vanilla, server / "cache" / cache)
    shutil.copy2(args.core, server / "plugins/KingGriefCore.jar")
    (data / "config.yml").write_text("enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n", encoding="utf-8")
    (server / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    level = "level-name=spawn_world\n" if main_spawn else ""
    (server / "server.properties").write_text(
        f"{level}online-mode=false\nserver-ip=127.0.0.1\nserver-port={h.free_port()}\n"
        "view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\n", encoding="utf-8")
    h.build_test_plugin(args, root, HERE / "SpawnRebuildProbe.java", HERE / "plugin-spawn-rebuild.yml", server / "plugins/KgSpawnRebuildProbe.jar")
    return server, data


def journal_fixture(server, data, template, after_swap, fallback=False):
    stage_name = ".kinggrief-spawn-staged-7200001"
    backup_name = "spawn_world_backup_1234567890123_deadbeef"
    target, stage, backup = (server / ("kinggrief_spawn" if fallback else "spawn_world"), server / stage_name, server / backup_name)
    if stage.exists() or backup.exists():
        raise AssertionError("fixture collision")
    stage.mkdir()
    with ZipFile(template) as z:
        z.extractall(stage)
    shutil.copy2(target / "uid.dat", stage / "uid.dat")
    (data / ".spawn-v72-rebuild-pending").write_text("v71\n", encoding="ascii")
    if fallback:
        selection=(data / ".spawn-v722-active-world").read_text(encoding="ascii").splitlines()
        if selection[1] != "kinggrief_spawn": raise AssertionError("unexpected fallback selection")
        shutil.copy2(target / ".kinggrief_spawn_owner", stage / ".kinggrief_spawn_owner")
        journal=f"v2\n{stage_name}\n{backup_name}\n1\n1\n{selection[1]}\n{selection[2]}\n"
    else:
        journal=f"v1\n{stage_name}\n{backup_name}\n1\n1\n"
    (data / ".spawn-v72-swap-journal").write_text(journal, encoding="ascii")
    if after_swap:
        target.rename(backup)
        stage.rename(target)
    return stage, backup


def main():
    args = h.args_parser()
    cache = h.cache_name(args.paper, args.vanilla)
    with tempfile.TemporaryDirectory(prefix="kinggrief-deferred-spawn-") as tmp:
        root = Path(tmp)
        server, data = server_setup(args, root, cache)
        run(args, server, "queue")
        run(args, server, "installed")
        run(args, server, "repeat")
        probe = server / "plugins/KgSpawnRebuildProbe.jar"
        disabled = root / "probe-disabled.jar"
        probe.rename(disabled)
        journal_fixture(server, data, args.template, after_swap=True)
        run(args, server, "interrupted-after-swap", recovery=True)
        if (data / ".spawn-v72-rebuild-pending").exists() or (data / ".spawn-v72-swap-journal").exists():
            raise AssertionError("recovery left request or journal behind")
        if len(list(server.glob("spawn_world_failed_*"))) != 1:
            raise AssertionError("interrupted replacement not quarantined")
        disabled.rename(probe)
        run(args, server, "recovered")
        probe.rename(disabled)
        stage, backup = journal_fixture(server, data, args.template, after_swap=False)
        run(args, server, "interrupted-before-swap", recovery=True)
        if stage.exists() or backup.exists():
            raise AssertionError("pre-rename recovery left staging/backup paths")
        disabled.rename(probe)
        run(args, server, "recovered")
    with tempfile.TemporaryDirectory(prefix="kinggrief-custom-spawn-") as tmp:
        root = Path(tmp)
        server, data = server_setup(args, root, cache)
        target = server / "spawn_world"; target.mkdir()
        with ZipFile(args.template) as archive:
            archive.extractall(target)
        (target / ".spawn_v70_village_ok").unlink()
        (target / ".kinggrief_prebuilt_v70").unlink()
        (target / "custom-data.txt").write_text("custom user file\n", encoding="utf-8")
        original_uid = bytes.fromhex("00112233445566778899aabbccddeeff")
        (target / "uid.dat").write_bytes(original_uid)
        shutil.copy2(args.template, data / "spawn-v73-template.zip")
        run(args, server, "custom-preserve")
        if (target / "uid.dat").read_bytes() != original_uid or (target / ".spawn_v70_village_ok").exists():
            raise AssertionError("custom world changed during ordinary startup or request")
        run(args, server, "custom-installed")
        if (target / "uid.dat").read_bytes() != original_uid:
            raise AssertionError("the original custom world was altered")
        probe=server / "plugins/KgSpawnRebuildProbe.jar"; disabled=root / "probe-disabled.jar"
        probe.rename(disabled)
        journal_fixture(server, data, args.template, after_swap=True, fallback=True)
        run(args, server, "fallback-interrupted-after-swap", recovery=True)
        disabled.rename(probe); run(args, server, "custom-recovered")
        probe.rename(disabled)
        stage, backup = journal_fixture(server, data, args.template, after_swap=False, fallback=True)
        # A changed selection must never redirect a rollback onto a different world.
        selection=data / ".spawn-v722-active-world"; original_selection=selection.read_text(encoding="ascii")
        parts=original_selection.splitlines(); parts[2]="11111111-2222-3333-4444-555555555555"
        selection.write_text("\n".join(parts)+"\n",encoding="ascii")
        proc=subprocess.run([args.java,"-Xms512M","-Xmx1536M","-jar",str(args.paper.resolve()),"nogui"],cwd=server,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=360)
        if "Swap journal and active world selection differ" not in proc.stdout or not stage.is_dir() or backup.exists():
            raise AssertionError("mismatched selection was not refused safely")
        selection.write_text(original_selection,encoding="ascii")
        run(args, server, "fallback-interrupted-before-swap", recovery=True)
        if stage.exists() or backup.exists(): raise AssertionError("fallback recovery left stage/backup")
        disabled.rename(probe); run(args, server, "custom-recovered")
    with tempfile.TemporaryDirectory(prefix="kinggrief-main-spawn-") as tmp:
        root = Path(tmp)
        server, data = server_setup(args, root, cache, main_spawn=True)
        run(args, server, "main-world")
        if (data / ".spawn-v72-rebuild-pending").exists():
            raise AssertionError("main-world rebuild request was accepted")
    print("DEFERRED SPAWN INTEGRATION: PASS", flush=True)


if __name__ == "__main__":
    main()
