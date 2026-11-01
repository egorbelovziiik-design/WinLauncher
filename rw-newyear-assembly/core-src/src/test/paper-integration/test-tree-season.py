#!/usr/bin/env python3
"""Real Paper tree seasons: legacy upgrade, offline rewards, restarts and SIGKILL.

Uses three offline-mode protocol clients and only a temporary disposable server.
Requires Java 21, matching Paper/Mojang binaries and minecraft-protocol@1.62.0.
Review Minecraft's EULA before passing --accept-eula.
"""
import argparse
import json
import os
from pathlib import Path
import queue
import shutil
import signal
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
    (server / 'plugins/KgTreeSeasonProbe/config.yml').write_text(f'phase: {phase}\n', encoding='utf8')
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
    client, success, client_output = None, False, ''
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
        env['KG_TREE_PHASE'] = str(phase)
        client = subprocess.Popen([args.node, str(HERE / 'tree-season-client.cjs'), str(port)],
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, env=env)
        deadline = time.monotonic() + 300
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None: break
                continue
            if 'KG-TREE ' in line: print(line.strip(), flush=True)
            if 'KG-TREE REQUEST ' in line:
                request = line.split('KG-TREE REQUEST ',1)[1].strip()
                client.stdin.write(request+'\n');client.stdin.flush()
            if 'KG-TREE RESULT PASS' in line:
                success = True
                break
            if 'KG-TREE RESULT FAIL' in line: break
        if not success: raise RuntimeError(f'phase {phase} assertions failed or timed out')
        if phase == 2:
            proc.kill()  # SIGKILL immediately after checked durable tree transactions.
            proc.wait(timeout=30)
            print('PHASE 2 SERVER SIGKILL', flush=True)
    finally:
        if client and client.poll() is None:
            try:
                client_output = client.communicate(input='done\n', timeout=10)[0]
                print(client_output, end='')
            except subprocess.TimeoutExpired:
                client.kill(); client.wait()
        if proc.poll() is None:
            try:
                proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=50)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                proc.kill();proc.wait()
        reader.join(timeout=10)
        if not success:
            print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]), file=sys.stderr)
    if phase == 1:
        line = next((line for line in client_output.splitlines() if line.startswith('TREE_NETWORK ')), None)
        if not line: raise RuntimeError('network client did not report')
        report = json.loads(line.split(' ',1)[1])
        if report != {'clicked':1,'observerCopies':0,'disconnected':True}:
            raise RuntimeError(f'history component delivery/click mismatch: {report}')
    watchdog = any('The server has not responded for 10 seconds' in line for line in output)
    expected = -signal.SIGKILL if phase == 2 else 0
    if proc.returncode != expected or not client or client.returncode != 0 or watchdog:
        print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]), file=sys.stderr)
        raise RuntimeError(f'Paper exit={proc.returncode}, expected={expected}, client exit={client and client.returncode}, watchdog={watchdog}')


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
    cache_name = harness.validate_paper_and_vanilla(args.paper,args.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-tree-season-test-') as tmp:
        root=Path(tmp); server=root/'server'; data=server/'plugins/KingGriefCore'
        data.mkdir(parents=True)
        (server/'plugins/KgTreeSeasonProbe').mkdir()
        (server/'cache').mkdir()
        shutil.copy2(args.vanilla,server/'cache'/cache_name)
        shutil.copy2(args.core,server/'plugins/KingGriefCore.jar')
        shutil.copy2(args.template,data/'spawn-v74-template.zip')
        (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
        (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
        (server/'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n',encoding='utf8')
        classes=root/'classes';classes.mkdir()
        cp=str(args.core.resolve())+os.pathsep+args.api_classpath
        subprocess.run([args.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'TreeSeasonProbe.java')],check=True)
        with ZipFile(server/'plugins/KgTreeSeasonProbe.jar','w',ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')): z.write(path,path.relative_to(classes).as_posix())
            z.write(HERE/'plugin-tree-season.yml','plugin.yml')
        for phase in (1,2,3): run_phase(args,server,port,phase)
    print('TREE SEASONS, RESTART AND SIGKILL INTEGRATION: PASS',flush=True)


if __name__=='__main__': main()
