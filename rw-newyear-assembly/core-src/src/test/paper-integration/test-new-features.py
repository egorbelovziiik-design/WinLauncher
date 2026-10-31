#!/usr/bin/env python3
"""Test airship, crown and safe trade with two clients on disposable Paper 1.21.4."""
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


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--vanilla', type=Path, required=True)
    p.add_argument('--api-classpath', required=True)
    p.add_argument('--node-modules', type=Path, required=True)
    p.add_argument('--core', type=Path, default=ROOT / 'KingGriefCore.jar')
    p.add_argument('--template', type=Path, default=ROOT / 'server/plugins/KingGriefCore/spawn-v73-template.zip')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    p.add_argument('--node', default='node')
    p.add_argument('--arrows-only', action='store_true', help='run only real-client bow/arrow regressions')
    p.add_argument('--boss-only', action='store_true', help='run only the boss-projectile regression')
    p.add_argument('--crossbows-only', action='store_true', help='run only crossbow regressions')
    p.add_argument('--throws-only', action='store_true', help='run only thrown-weapon regressions')
    p.add_argument('--protect-only', action='store_true', help='run only projectile cancellation regression')
    p.add_argument('--abilities-only', action='store_true', help='run other real custom arrow ability regressions')
    p.add_argument('--accept-eula', action='store_true')
    args = p.parse_args()
    if not args.accept_eula:
        p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache_name = harness.validate_paper_and_vanilla(args.paper, args.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-new-features-test-') as tmp:
        root = Path(tmp)
        server = root / 'server'
        data = server / 'plugins/KingGriefCore'
        data.mkdir(parents=True)
        (server / 'cache').mkdir()
        shutil.copy2(args.vanilla, server / 'cache' / cache_name)
        shutil.copy2(args.core, server / 'plugins/KingGriefCore.jar')
        shutil.copy2(args.template, data / 'spawn-v73-template.zip')
        (data / 'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n', encoding='utf8')
        probe_config = server / 'plugins/KgNewFeaturesProbe/config.yml'
        probe_config.parent.mkdir(parents=True, exist_ok=True)
        probe_config.write_text('arrows-only: ' + ('true' if args.arrows_only else 'false') + '\n'
                                + 'boss-only: ' + ('true' if args.boss_only else 'false') + '\n'
                                + 'crossbows-only: ' + ('true' if args.crossbows_only else 'false') + '\n'
                                + 'throws-only: ' + ('true' if args.throws_only else 'false') + '\n'
                                + 'protect-only: ' + ('true' if args.protect_only else 'false') + '\n'
                                + 'abilities-only: ' + ('true' if args.abilities_only else 'false') + '\n', encoding='utf8')
        (server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
        (server / 'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n', encoding='utf8')
        classes = root / 'classes'
        classes.mkdir()
        cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-classpath', cp, '-d', str(classes), str(HERE / 'NewFeaturesProbe.java')], check=True)
        with ZipFile(server / 'plugins/KgNewFeaturesProbe.jar', 'w', ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')):
                z.write(path, path.relative_to(classes).as_posix())
            z.write(HERE / 'plugin-new-features.yml', 'plugin.yml')
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
            started = False
            deadline = time.monotonic() + 360
            while time.monotonic() < deadline:
                try: line = messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None: break
                    continue
                if 'Done (' in line and 'For help' in line:
                    print('SERVER STARTED:', line.strip(), flush=True)
                    started = True
                    break
                if 'Error occurred while enabling' in line: break
            if not started: raise RuntimeError('Paper failed to start')
            env = os.environ.copy()
            env['NODE_PATH'] = str(args.node_modules.resolve())
            client = subprocess.Popen([args.node, str(HERE / 'orb-client.cjs'), str(port)],
                                      stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
            deadline = time.monotonic() + 360
            while time.monotonic() < deadline:
                try: line = messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None or client.poll() is not None: break
                    continue
                if 'KG-NEW ' in line: print(line.strip(), flush=True)
                for request, command in [('REQUEST_SHOT_OFF', 'shoot-off'), ('REQUEST_SHOT_DOWN', 'shoot-down'), ('REQUEST_THROW_OFF', 'throw-off'),
                                         ('REQUEST_CROSSBOW', 'crossbow'), ('REQUEST_SHOT ', 'shoot'),
                                         ('REQUEST_THROW ', 'throw')]:
                    if 'KG-NEW ' + request in line:
                        client.stdin.write(command + '\n'); client.stdin.flush()
                        break
                if 'KG-NEW RESULT PASS' in line:
                    success = True
                    break
                if 'KG-NEW RESULT FAIL' in line: break
            if not success: raise RuntimeError('new feature assertions failed or timed out')
        finally:
            if client and client.poll() is None:
                try: print(client.communicate(input='done\n', timeout=10)[0], end='')
                except subprocess.TimeoutExpired:
                    client.kill(); client.wait()
            if proc.poll() is None:
                try:
                    proc.stdin.write('stop\n'); proc.stdin.flush(); proc.wait(timeout=40)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    proc.kill(); proc.wait()
            reader.join(timeout=10)
            if not success:
                print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-180:]), file=sys.stderr)
        watchdog = next((i for i, line in enumerate(output) if 'The server has not responded for 10 seconds' in line), None)
        if proc.returncode != 0 or client.returncode != 0 or watchdog is not None:
            if watchdog is not None: print('\n--- WATCHDOG ---\n' + ''.join(output[watchdog:watchdog+100]), file=sys.stderr)
            print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-180:]), file=sys.stderr)
            raise RuntimeError(f'Paper exit={proc.returncode}, client exit={client.returncode}, watchdog={watchdog is not None}')
    print('NEW FEATURES INTEGRATION: PASS', flush=True)


if __name__ == '__main__':
    main()
