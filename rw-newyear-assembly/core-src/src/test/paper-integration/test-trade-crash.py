#!/usr/bin/env python3
"""SIGKILL a disposable Paper server between trade saves, restart, verify recovery."""
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


def wait_for(process, messages, text, timeout=240):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            line = messages.get(timeout=1)
        except queue.Empty:
            if process.poll() is not None:
                break
            continue
        if 'KG-TRADE-CRASH ' in line:
            print(line.strip(), flush=True)
        if 'KG-TRADE-CRASH FAIL' in line or 'Error occurred while enabling KingGriefCore' in line:
            raise RuntimeError(line.strip())
        if text in line:
            return
    raise RuntimeError('timed out waiting for ' + text)


def boot(args, server, port):
    proc = subprocess.Popen([args.java, '-Xms512M', '-Xmx1536M', '-jar', str(args.paper.resolve()), 'nogui'],
                            cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, bufsize=1)
    messages, output = queue.Queue(), []
    def read():
        for line in proc.stdout:
            output.append(line)
            messages.put(line)
    threading.Thread(target=read, daemon=True).start()
    try:
        wait_for(proc, messages, 'Done (', 360)
    except Exception:
        print(''.join(output[-180:]), file=sys.stderr)
        proc.kill(); proc.wait()
        raise
    env = os.environ.copy()
    env['NODE_PATH'] = str(args.node_modules.resolve())
    client = subprocess.Popen([args.node, str(HERE / 'orb-client.cjs'), str(port)],
                              stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, env=env)
    return proc, client, messages, output


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--vanilla', type=Path, required=True)
    p.add_argument('--api-classpath', required=True)
    p.add_argument('--node-modules', type=Path, required=True)
    p.add_argument('--core', type=Path, default=ROOT / 'KingGriefCore.jar')
    p.add_argument('--template', type=Path, default=ROOT / 'server/plugins/KingGriefCore/spawn-v71-template.zip')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    p.add_argument('--node', default='node')
    p.add_argument('--accept-eula', action='store_true')
    args = p.parse_args()
    if not args.accept_eula:
        p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache_name = harness.validate_paper_and_vanilla(args.paper, args.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-trade-crash-test-') as tmp:
        root = Path(tmp); server = root / 'server'; data = server / 'plugins/KingGriefCore'
        data.mkdir(parents=True); (server/'cache').mkdir()
        shutil.copy2(args.vanilla, server/'cache'/cache_name)
        shutil.copy2(args.core, server/'plugins/KingGriefCore.jar')
        shutil.copy2(args.template, data/'spawn-v71-template.zip')
        (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\nevents:\n  auto-enabled: false\ncrown-event:\n  auto-enabled: false\n', encoding='utf8')
        (server/'eula.txt').write_text('eula=true\n', encoding='utf8')
        (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n',encoding='utf8')
        classes=root/'classes';classes.mkdir()
        subprocess.run([args.javac,'-encoding','UTF-8','-classpath',str(args.core.resolve())+os.pathsep+args.api_classpath,'-d',str(classes),str(HERE/'TradeCrashProbe.java')],check=True)
        with ZipFile(server/'plugins/KgTradeCrashProbe.jar','w',ZIP_DEFLATED) as z:
            for path in classes.rglob('*.class'):z.write(path,path.relative_to(classes).as_posix())
            z.write(HERE/'plugin-trade-crash.yml','plugin.yml')
        proc=client=None; output=[]; success=False
        try:
            proc,client,messages,output=boot(args,server,port)
            wait_for(proc,messages,'KG-TRADE-CRASH READY',240)
            # No plugin shutdown, player-quit or clean world save: this is a real process kill.
            proc.kill();proc.wait(timeout=30)
            client.kill();client.wait(timeout=15)
            restart=server/'plugins/KgTradeCrashProbe/restart';restart.parent.mkdir(parents=True,exist_ok=True);restart.write_text('restart',encoding='utf8')
            proc,client,messages,output=boot(args,server,port)
            wait_for(proc,messages,'KG-TRADE-CRASH PASS:',240)
            success=True
        finally:
            if client is not None and client.poll() is None:
                try: client.communicate(input='done\n',timeout=10)
                except subprocess.TimeoutExpired: client.kill();client.wait()
            if proc is not None and proc.poll() is None:
                try: proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=50)
                except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
            if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]),file=sys.stderr)
    print('TRADE CRASH RECOVERY INTEGRATION: PASS',flush=True)


if __name__ == '__main__':main()
