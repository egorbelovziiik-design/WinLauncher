#!/usr/bin/env python3
"""Real Paper parkour: event edge cases and a 32-pad prismarine-physics player.

Requires mineflayer in --node-modules. The server is disposable and loopback-only;
this does not render a Minecraft client or claim compatibility with all plugins.
"""
import argparse
import importlib.util
import os
import queue
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
spec=importlib.util.spec_from_file_location('prebuilt_helper',HERE/'test-prebuilt-install.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)


def replay_restart(a, root, server, port, classpath):
    """Boot the same fixture again and check the saved replay with two clients."""
    classes=root/'restart-classes';classes.mkdir()
    subprocess.run([a.javac,'-encoding','UTF-8','-classpath',classpath,'-d',str(classes),
                    str(HERE/'ParkourRestartProbe.java')],check=True)
    with ZipFile(server/'plugins/KgParkourProbe.jar','w',ZIP_DEFLATED) as z:
        for cls in classes.rglob('*.class'):z.write(cls,cls.relative_to(classes).as_posix())
        z.write(HERE/'plugin-parkour-restart.yml','plugin.yml')
    proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,
                          stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=[];messages=queue.Queue()
    def capture():
        for line in proc.stdout:lines.append(line);messages.put(line)
    thread=threading.Thread(target=capture,daemon=True);thread.start()
    client=None;success=False
    try:
        started=False;deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            try:line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None:break
                continue
            if 'Done (' in line and 'For help' in line:started=True;break
            if 'Error occurred while enabling' in line:break
        if not started:raise RuntimeError('Replay restart did not start')
        env=os.environ.copy();env['NODE_PATH']=str(a.node_modules.resolve())
        client=subprocess.Popen([a.node,str(HERE/'ghost-observer-client.cjs'),str(port)],env=env,
                                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        deadline=time.monotonic()+180
        while time.monotonic()<deadline:
            try:line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None:break
                continue
            if 'KG-GHOST ' in line:print(line.strip(),flush=True)
            if 'KG-GHOST RESULT PASS' in line:success=True;break
            if 'KG-GHOST RESULT FAIL' in line:break
        if not success:raise RuntimeError('Replay restart/privacy checks failed')
    finally:
        if client and client.poll() is None:
            try:print(client.communicate(input='done\n',timeout=10)[0],end='')
            except subprocess.TimeoutExpired:client.kill();client.wait()
        if proc.poll() is None:
            try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
            except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
        thread.join(timeout=10)
        if not success:print('\n--- RESTART SERVER LOG TAIL ---\n'+''.join(lines[-200:]),file=sys.stderr)
    if proc.returncode!=0 or client.returncode!=0:
        raise RuntimeError(f'Replay restart exit: server={proc.returncode}, client={client.returncode}')
    print('PARKOUR REPLAY RESTART/PRIVACY: PASS',flush=True)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper',required=True,type=Path);p.add_argument('--vanilla',required=True,type=Path)
    p.add_argument('--api-classpath',required=True);p.add_argument('--node-modules',required=True,type=Path)
    p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
    p.add_argument('--kingac',type=Path);p.add_argument('--kingclear',type=Path)
    p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node')
    p.add_argument('--clean',action='store_true',help='run a clean ranked route and test the private ghost')
    p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
    if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
    cache=h.cache_name(a.paper,a.vanilla);port=h.free_port()
    with tempfile.TemporaryDirectory(prefix='kg-parkour-paper-') as tmp:
        root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore'
        data.mkdir(parents=True);(server/'cache').mkdir()
        shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.core,server/'plugins/KingGriefCore.jar')
        if a.kingac:shutil.copy2(a.kingac,server/'plugins/KingAC.jar')
        if a.kingclear:shutil.copy2(a.kingclear,server/'plugins/KingClear.jar')
        (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
        (server/'plugins/KgParkourProbe').mkdir()
        (server/'plugins/KgParkourProbe/config.yml').write_text('clean: '+('true' if a.clean else 'false')+'\n',encoding='utf8')
        (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
        (server/'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=6\nsimulation-distance=5\n'
            'allow-nether=false\nallow-flight=true\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\nmax-tick-time=-1\n',encoding='utf8')
        classes=root/'classes';classes.mkdir()
        cp=str(a.core.resolve())+os.pathsep+a.api_classpath
        subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'ParkourProbe.java')],check=True)
        with ZipFile(server/'plugins/KgParkourProbe.jar','w',ZIP_DEFLATED) as z:
            for cls in classes.rglob('*.class'):z.write(cls,cls.relative_to(classes).as_posix())
            z.write(HERE/'plugin-parkour.yml','plugin.yml')
        proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,
                              stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        lines=[];messages=queue.Queue()
        def capture():
            for line in proc.stdout:lines.append(line);messages.put(line)
        threading.Thread(target=capture,daemon=True).start(); success=False
        try:
            started=False;deadline=time.monotonic()+360
            while time.monotonic()<deadline:
                try:line=messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None:break
                    continue
                if 'Done (' in line and 'For help' in line:started=True;break
                if 'Error occurred while enabling KingGriefCore' in line:break
            if not started:raise RuntimeError('Paper did not start')
            env=os.environ.copy();env['NODE_PATH']=str(a.node_modules.resolve());env['KG_PARKOUR_CLEAN']='1' if a.clean else '0'
            client=subprocess.run([a.node,str(HERE/'parkour-client.cjs'),str(port)],env=env,
                                  stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=245)
            print(client.stdout,flush=True)
            if client.returncode or 'PARKOUR_CLIENT_RESULT PASS' not in client.stdout:
                raise RuntimeError('Physics client did not complete parkour')
            log=''.join(lines)
            if 'KG-PARKOUR RESULT PASS' not in log or 'KG-PARKOUR RESULT FAIL' in log:
                raise RuntimeError('Paper parkour assertions failed')
            for line in log.splitlines():
                if 'KG-PARKOUR ' in line:print(line,flush=True)
            success=True
            print('PARKOUR PAPER INTEGRATION: PASS event edge cases, 31 physical jumps, rewards and '+('clean ranked run/ghost' if a.clean else 'fall rescue'),flush=True)
        finally:
            if proc.poll() is None:
                try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
                except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
            if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(lines[-200:]),file=sys.stderr)
        if a.clean and success:replay_restart(a,root,server,port,cp)
if __name__=='__main__':main()
