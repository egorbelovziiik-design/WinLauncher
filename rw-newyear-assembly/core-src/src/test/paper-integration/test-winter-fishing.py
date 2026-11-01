#!/usr/bin/env python3
"""Exercise real and synthetic winter fishing on disposable Paper.

Requires Java 21, a matching Paper/Mojang pair and minecraft-protocol@1.62.0.
Pass --accept-eula only after reviewing the Minecraft EULA. No production data
is read or modified. Synthetic fixtures exercise failures and limits separately.
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
    (server / 'plugins/KgWinterFishingProbe/config.yml').write_text(f'phase: {phase}\n', encoding='utf8')
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
    client, success = None, False
    try:
        deadline = time.monotonic() + 360
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None: break
                continue
            if 'Done (' in line and 'For help' in line:
                print(f'PHASE {phase} SERVER STARTED', flush=True)
                break
            if 'Error occurred while enabling' in line: raise RuntimeError('plugin enable failed')
        else: raise RuntimeError('Paper startup timed out')
        if proc.poll() is not None: raise RuntimeError('Paper exited during startup')
        env = os.environ.copy()
        env['NODE_PATH'] = str(args.node_modules.resolve())
        env['KG_FISH_PHASE'] = str(phase)
        client = subprocess.Popen([args.node, str(HERE / 'winter-fishing-client.cjs'), str(port)],
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, env=env)
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None: break
                continue
            if 'KG-FISH ' in line: print(line.strip(), flush=True)
            if 'KG-FISH REQUEST ' in line:
                request=line.split('KG-FISH REQUEST ',1)[1].strip()
                client.stdin.write(request+'\n');client.stdin.flush()
            if 'KG-FISH RESULT PASS' in line:
                success = True
                break
            if 'KG-FISH RESULT FAIL' in line: break
        if not success: raise RuntimeError(f'phase {phase} assertions failed or timed out')
    finally:
        if client and client.poll() is None:
            try: print(client.communicate(input='done\n', timeout=10)[0], end='')
            except subprocess.TimeoutExpired:
                client.kill(); client.wait()
        if proc.poll() is None:
            try:
                proc.stdin.write('stop\n'); proc.stdin.flush(); proc.wait(timeout=50)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                proc.kill(); proc.wait()
        reader.join(timeout=10)
        if not success:
            print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-200:]), file=sys.stderr)
    watchdog = any('The server has not responded for 10 seconds' in line for line in output)
    if proc.returncode != 0 or client.returncode != 0 or watchdog:
        print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-200:]), file=sys.stderr)
        raise RuntimeError(f'Paper exit={proc.returncode}, client exit={client.returncode}, watchdog={watchdog}')


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
    if not args.accept_eula:
        p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache_name = harness.validate_paper_and_vanilla(args.paper, args.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-fishing-test-') as tmp:
        root = Path(tmp)
        server = root / 'server'
        data = server / 'plugins/KingGriefCore'
        data.mkdir(parents=True)
        (server / 'plugins/KgWinterFishingProbe').mkdir()
        (server / 'cache').mkdir()
        shutil.copy2(args.vanilla, server / 'cache' / cache_name)
        shutil.copy2(args.core, server / 'plugins/KingGriefCore.jar')
        shutil.copy2(args.template, data / 'spawn-v74-template.zip')
        (data / 'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n', encoding='utf8')
        (server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
        (server / 'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n', encoding='utf8')
        classes = root / 'classes'
        classes.mkdir()
        cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-classpath', cp,
                        '-d', str(classes), str(HERE / 'WinterFishingProbe.java')], check=True)
        with ZipFile(server / 'plugins/KgWinterFishingProbe.jar', 'w', ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')):
                z.write(path, path.relative_to(classes).as_posix())
            z.write(HERE / 'plugin-winter-fishing.yml', 'plugin.yml')
        run_phase(args, server, port, 1)
        run_phase(args, server, port, 2)
    print('WINTER FISHING AND RESTART INTEGRATION: PASS', flush=True)


if __name__ == '__main__':
    main()
