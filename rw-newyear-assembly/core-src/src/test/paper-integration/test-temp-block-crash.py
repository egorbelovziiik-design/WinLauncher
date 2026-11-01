#!/usr/bin/env python3
"""Exercise temporary-block chunk recovery across two actual Paper SIGKILLs.

Runs ONLY in a disposable server directory. It explicitly saves chunks before
both kills: it does not simulate loss during a chunk write or a power failure.
Requires Java 21, matching Paper/Mojang jars and minecraft-protocol@1.62.0.
Review the Minecraft EULA before passing --accept-eula.
"""
import argparse
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from zipfile import ZipFile, ZIP_DEFLATED
import run as harness

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def run_phase(args, server, port, phase):
    (server / 'plugins/KgTempBlockCrashProbe/config.yml').write_text(f'phase: {phase}\n', encoding='utf8')
    proc = subprocess.Popen([args.java, '-Xms512M', '-Xmx1536M', '-jar', str(args.paper.resolve()), 'nogui'],
                            cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)
    output, messages = [], queue.Queue()
    def read():
        for line in proc.stdout:
            output.append(line)
            messages.put(line)
    reader = threading.Thread(target=read, daemon=True)
    reader.start()
    client, success, killed = None, False, False
    try:
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None: break
                continue
            if 'KG-TEMP ' in line: print(line.strip(), flush=True)
            if 'KG-TEMP RESULT FAIL' in line or 'Error occurred while enabling' in line:
                raise RuntimeError(f'phase {phase}: plugin/startup failed')
            if 'Done (' in line and 'For help' in line:
                print(f'PHASE {phase} SERVER STARTED', flush=True)
                break
        else: raise RuntimeError(f'phase {phase}: startup timed out')
        if proc.poll() is not None: raise RuntimeError(f'phase {phase}: Paper exited during startup')
        env = os.environ.copy(); env['NODE_PATH'] = str(args.node_modules.resolve())
        client = subprocess.Popen([args.node, str(HERE / 'claim-break-client.cjs'), str(port)],
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, env=env)
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None: break
                continue
            if 'KG-TEMP ' in line: print(line.strip(), flush=True)
            if 'KG-TEMP RESULT FAIL' in line: raise RuntimeError(f'phase {phase}: assertions failed')
            if 'KG-TEMP READY' in line: break
        else: raise RuntimeError(f'phase {phase}: assertion timeout')
        if proc.poll() is not None or client.poll() is not None:
            raise RuntimeError(f'phase {phase}: server or client exited before readiness')
        if phase < 3:
            proc.stdin.write('save-all flush\n'); proc.stdin.flush()
            deadline = time.monotonic() + 90
            saved = False
            while time.monotonic() < deadline:
                try: line = messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None: break
                    continue
                if 'Saved the game' in line:
                    saved = True; break
            if not saved: raise RuntimeError(f'phase {phase}: save-all flush did not complete')
            # A real abrupt process kill: no onDisable, quit or final world save.
            proc.kill(); proc.wait(timeout=30); killed = True
            client.kill(); client.wait(timeout=15)
            print(f'PHASE {phase} SAVED AND SIGKILLED', flush=True)
        success = True
    finally:
        if client is not None and client.poll() is None:
            try:
                client_output = client.communicate(input='done\n', timeout=10)[0]
                print(client_output, end='')
            except subprocess.TimeoutExpired:
                client.kill(); client.wait()
        if proc.poll() is None:
            try: proc.stdin.write('stop\n'); proc.stdin.flush(); proc.wait(timeout=50)
            except (BrokenPipeError, subprocess.TimeoutExpired): proc.kill(); proc.wait()
        reader.join(timeout=10)
        watchdog = any('The server has not responded for 10 seconds' in line for line in output)
        if not success or watchdog or (not killed and proc.returncode != 0):
            print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-220:]), file=sys.stderr)
        if watchdog: raise RuntimeError(f'phase {phase}: Paper watchdog detected')
        if success and not killed and proc.returncode != 0:
            raise RuntimeError(f'phase {phase}: Paper exited {proc.returncode}')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--vanilla', type=Path, required=True)
    p.add_argument('--api-classpath', required=True)
    p.add_argument('--node-modules', type=Path, required=True)
    p.add_argument('--core', type=Path, default=ROOT / 'KingGriefCore.jar')
    p.add_argument('--template', type=Path, default=ROOT / 'server/plugins/KingGriefCore/spawn-v74-template.zip')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    p.add_argument('--node', default='node')
    p.add_argument('--accept-eula', action='store_true')
    args = p.parse_args()
    if not args.accept_eula: p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache_name = harness.validate_paper_and_vanilla(args.paper, args.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-temp-block-crash-') as tmp:
        root = Path(tmp); server = root / 'server'; data = server / 'plugins/KingGriefCore'
        data.mkdir(parents=True); (server / 'plugins/KgTempBlockCrashProbe').mkdir(); (server / 'cache').mkdir()
        shutil.copy2(args.vanilla, server / 'cache' / cache_name)
        shutil.copy2(args.core, server / 'plugins/KingGriefCore.jar')
        shutil.copy2(args.template, data / 'spawn-v74-template.zip')
        (data / 'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\nevents:\n  auto-enabled: false\ncrown-event:\n  auto-enabled: false\n', encoding='utf8')
        (server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
        (server / 'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n', encoding='utf8')
        classes = root / 'classes'; classes.mkdir()
        cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-classpath', cp, '-d', str(classes),
                        str(HERE / 'TemporaryBlockCrashProbe.java')], check=True)
        with ZipFile(server / 'plugins/KgTempBlockCrashProbe.jar', 'w', ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')): z.write(path, path.relative_to(classes).as_posix())
            z.write(HERE / 'plugin-temp-block-crash.yml', 'plugin.yml')
        for phase in (1, 2, 3): run_phase(args, server, port, phase)
    print('TEMPORARY BLOCK SAVED-CHUNK CRASH RECOVERY: PASS', flush=True)


if __name__ == '__main__': main()
