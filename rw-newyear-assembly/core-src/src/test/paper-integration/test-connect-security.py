#!/usr/bin/env python3
"""Test KingConnect's public-key rejection on a disposable Paper server.

No real cloud or server is contacted. Pass --accept-eula only after reviewing
Minecraft's EULA. The test signs synthetic tokens with disposable keys.
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


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--vanilla', type=Path, required=True)
    p.add_argument('--api-classpath', required=True)
    p.add_argument('--node-modules', type=Path, required=True)  # shared runner interface
    p.add_argument('--connect', type=Path, default=ROOT / 'KingConnect.jar')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    p.add_argument('--accept-eula', action='store_true')
    a = p.parse_args()
    if not a.accept_eula: p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache = harness.validate_paper_and_vanilla(a.paper, a.vanilla)
    port = harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kingconnect-security-test-') as tmp:
        root=Path(tmp); server=root/'server'; plugins=server/'plugins'; plugins.mkdir(parents=True)
        (server/'cache').mkdir()
        shutil.copy2(a.vanilla, server/'cache'/cache)
        shutil.copy2(a.connect, plugins/'KingConnect.jar')
        (server/'eula.txt').write_text('eula=true\n')
        (server/'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\n'
            'allow-nether=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=1\n')
        classes=root/'classes'; classes.mkdir()
        subprocess.run([a.javac,'-encoding','UTF-8','-classpath',a.api_classpath,'-d',str(classes),
                        str(HERE/'ConnectSecurityProbe.java')],check=True)
        with ZipFile(plugins/'KgConnectSecurityProbe.jar','w',ZIP_DEFLATED) as z:
            for file in sorted(classes.rglob('*.class')): z.write(file,file.relative_to(classes).as_posix())
            z.write(HERE/'plugin-connect-security.yml','plugin.yml')
        proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,
                              stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        output=[]; messages=queue.Queue()
        def read():
            for line in proc.stdout: output.append(line); messages.put(line)
        reader=threading.Thread(target=read,daemon=True); reader.start(); success=False
        try:
            deadline=time.monotonic()+360
            while time.monotonic()<deadline:
                try: line=messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None: break
                    continue
                if 'KG-CONNECT-SECURITY ' in line: print(line.strip(),flush=True)
                if 'KG-CONNECT-SECURITY RESULT PASS' in line: success=True; break
                if 'KG-CONNECT-SECURITY RESULT FAIL' in line or 'Error occurred while enabling' in line: break
            if not success: raise RuntimeError('KingConnect security assertions failed or timed out')
        finally:
            if proc.poll() is None:
                try: proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=50)
                except (BrokenPipeError,subprocess.TimeoutExpired): proc.kill();proc.wait()
            reader.join(timeout=10)
            if not success: print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]),file=sys.stderr)
        watchdog=any('The server has not responded for 10 seconds' in line for line in output)
        if proc.returncode != 0 or watchdog: raise RuntimeError(f'Paper exit={proc.returncode}, watchdog={watchdog}')
    print('KINGCONNECT SECURITY INTEGRATION: PASS',flush=True)

if __name__ == '__main__': main()
