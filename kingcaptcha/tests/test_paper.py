#!/usr/bin/env python3
"""Run KingCaptcha + exact Hub JAR on disposable Paper and real protocol clients."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
from urllib.parse import urlparse
from zipfile import ZipFile, ZIP_DEFLATED

HERE = Path(__file__).resolve().parent


def cache_name(paper, vanilla):
    with ZipFile(paper) as archive:
        if json.loads(archive.read('version.json'))['id'] != '1.21.4': raise ValueError('Expected Paper 1.21.4')
        sha, url, name = archive.read('META-INF/download-context').decode().strip().split('\t')
    if urlparse(url).scheme != 'https' or Path(name).name != name: raise ValueError('Invalid Paper download context')
    if hashlib.sha256(vanilla.read_bytes()).hexdigest() != sha: raise ValueError('Vanilla server checksum mismatch')
    return name


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0))
        return sock.getsockname()[1]


def build_probe(args, root, plugins):
    classes=root/'probe-classes';classes.mkdir()
    subprocess.run([args.javac,'-encoding','UTF-8','--release','21','-classpath',args.api_classpath,
                    '-d',str(classes),str(HERE/'CaptchaProbe.java')],check=True)
    with ZipFile(plugins/'KgCaptchaProbe.jar','w',ZIP_DEFLATED) as jar:
        for file in sorted(classes.rglob('*.class')):jar.write(file,file.relative_to(classes).as_posix())
        jar.write(HERE/'probe.yml','plugin.yml')


def phase(args, root, mode):
    server=root/mode;plugins=server/'plugins';plugins.mkdir(parents=True);(server/'cache').mkdir()
    shutil.copy2(args.vanilla,server/'cache'/cache_name(args.paper,args.vanilla))
    shutil.copy2(args.hub,plugins/'KingHub.jar')
    if mode=='combined':shutil.copy2(args.captcha,plugins/'KingCaptcha.jar')
    (plugins/'KingHub').mkdir(); (plugins/'KingCaptcha').mkdir()
    (plugins/'KingHub/config.yml').write_text('''mode: proxy
secret-key: kingcaptcha-test-secret-2026
grief1:
  host: 127.0.0.1
  port: 25566
  proxy-name: grief1
push-resourcepack-on-join: true
resourcepack-url: https://example.org/test-pack.zip
resourcepack-sha1: 0000000000000000000000000000000000000000
hub:
  world-name: hub_world
  auto-build-newyear-map: false
  compass-slot: 4
  double-jump: true
  auth-enabled: true
  spawn: {x: 0.5, y: 100.0, z: 22.5, yaw: 180.0, pitch: 0.0}
  npc: {x: 0.5, y: 100.0, z: 21.5, yaw: 0.0}
cloud-sync: {enabled: false}
ping-interval-seconds: 10
''',encoding='utf8')
    (plugins/'KingCaptcha/config.yml').write_text('rounds: 3\nmax-mistakes: 3\ntimeout-seconds: 90\nretry-cooldown-seconds: 3\nbossbar: true\n',encoding='utf8')
    (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
    listen=port()
    (server/'server.properties').write_text(f'''online-mode=false
server-ip=127.0.0.1
server-port={listen}
accepts-transfers=true
view-distance=2
simulation-distance=3
allow-nether=false
spawn-protection=0
level-type=minecraft:flat
generate-structures=false
max-players=12
enforce-secure-profile=false
''',encoding='utf8')
    build_probe(args,root,plugins)
    proc=subprocess.Popen([args.java,'-Xms512M','-Xmx1536M','-jar',str(args.paper.resolve()),'nogui'],cwd=server,
                          stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    output=[];messages=queue.Queue();client=None;client_text='';success=False
    def reader():
        for line in proc.stdout:output.append(line);messages.put(line)
    thread=threading.Thread(target=reader,daemon=True);thread.start()
    try:
        started=False;deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            try:line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None:break
                continue
            if 'Done (' in line and 'For help' in line: started=True;break
            if 'Error occurred while enabling' in line or 'KG-CAPTCHA RESULT FAIL' in line:break
        if not started:raise RuntimeError(mode+': Paper failed to start')
        env=os.environ.copy();env['NODE_PATH']=str(args.node_modules.resolve());env['KG_CAPTCHA_MODE']=mode
        client=subprocess.Popen([args.node,str(HERE/'client.cjs'),str(listen)],stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
        deadline=time.monotonic()+200
        while time.monotonic()<deadline:
            try:line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None:break
                continue
            if 'KG-CAPTCHA ' in line:print(line.strip(),flush=True)
            if 'KG-CAPTCHA REQUEST ' in line:
                client.stdin.write(line.split('KG-CAPTCHA REQUEST ',1)[1].strip()+'\n');client.stdin.flush()
            if 'KG-CAPTCHA RESULT PASS' in line:success=True;break
            if 'KG-CAPTCHA RESULT FAIL' in line:break
        if not success:raise RuntimeError(mode+': probe failed or timed out')
    finally:
        if client and client.poll() is None:
            try:client_text=client.communicate(input='done\n',timeout=15)[0]
            except subprocess.TimeoutExpired:client.kill();client_text=client.communicate()[0]
            print(client_text,end='',flush=True)
        elif client:client_text=client.communicate()[0];print(client_text,end='',flush=True)
        if proc.poll() is None:
            try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
            except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
        thread.join(timeout=10)
        if not success:print('\n--- '+mode+' SERVER LOG TAIL ---\n'+''.join(output[-260:]),file=sys.stderr,flush=True)
    match=re.search(r'CAPTCHA_CLIENT PASS (\{[^\n]+\})',client_text)
    if proc.returncode!=0 or not client or client.returncode!=0 or not match:
        raise RuntimeError(f'{mode}: Paper exit={proc.returncode}, client={client and client.returncode}, telemetry={bool(match)}')
    if any('The server has not responded for 10 seconds' in line for line in output):raise RuntimeError(mode+': Paper watchdog warning')
    if any('Error occurred while enabling' in line for line in output):raise RuntimeError(mode+': plugin enable error')
    print('PAPER PHASE',mode,'PASS',json.loads(match.group(1)),flush=True)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ('paper','vanilla','node-modules','captcha','hub'):parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--api-classpath',required=True)
    parser.add_argument('--java',default='java');parser.add_argument('--javac',default='javac');parser.add_argument('--node',default='node')
    parser.add_argument('--accept-eula',action='store_true');args=parser.parse_args()
    if not args.accept_eula:parser.error('Review https://aka.ms/MinecraftEULA before passing --accept-eula')
    args.paper=args.paper.resolve();args.vanilla=args.vanilla.resolve();args.hub=args.hub.resolve();args.captcha=args.captcha.resolve()
    cache_name(args.paper,args.vanilla)
    with tempfile.TemporaryDirectory(prefix='kingcaptcha-paper-') as tmp:
        for mode in ('hub-only','combined'):
            root=Path(tmp)/('run-'+mode);root.mkdir();phase(args,root,mode)
    print('KINGCAPTCHA PAPER INTEGRATION: PASS',flush=True)


if __name__=='__main__':main()
