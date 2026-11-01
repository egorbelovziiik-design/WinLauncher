#!/usr/bin/env python3
"""Upgrade real old forge parts, craft variants, and fight with real WG on Paper.

Uses an isolated offline-mode server, two protocol clients and real WorldGuard /
WorldEdit JARs. Review the Minecraft EULA before passing --accept-eula.
"""
import argparse
import json
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

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]


def check_pack(pack):
    # Static linkage only; it does not replace a rendered-client test.
    with ZipFile(pack) as archive:
        item=json.loads(archive.read('assets/minecraft/items/netherite_sword.json'))['model']
        old=json.loads(archive.read('assets/minecraft/models/item/netherite_sword.json'))['overrides']
        for model_id,identifier in ((1831,'sword_frost'),(1832,'sword_zeus'),(1836,'sword_phantom')):
            model='kinggrief:item/'+identifier
            assert any(e.get('threshold')==model_id and e.get('model',{}).get('model')==model for e in item['entries'])
            assert any(e.get('predicate',{}).get('custom_model_data')==model_id and e.get('model')==model for e in old)
            definition=json.loads(archive.read('assets/kinggrief/models/item/'+identifier+'.json'))
            assert definition['textures']['layer0']==model
            assert archive.read('assets/kinggrief/textures/item/'+identifier+'.png').startswith(b'\x89PNG\r\n\x1a\n')
        assert item['fallback']['model']=='minecraft:item/netherite_sword'
    print('EXISTING PACK MODEL LINKS: PASS',flush=True)


def run_phase(args,server,port,phase):
    (server/'plugins/KgModularForgeProbe/config.yml').write_text(f'phase: {phase}\n',encoding='utf8')
    proc=subprocess.Popen([args.java,'-Xms512M','-Xmx1536M','-jar',str(args.paper.resolve()),'nogui'],
                          cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    output,messages=[],queue.Queue()
    def read():
        for line in proc.stdout: output.append(line);messages.put(line)
    reader=threading.Thread(target=read,daemon=True);reader.start();client=None;success=False
    try:
        deadline=time.monotonic()+360
        while time.monotonic()<deadline:
            try: line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None: break
                continue
            if 'Done (' in line and 'For help' in line: print(f'PHASE {phase} SERVER STARTED',flush=True);break
            if 'Error occurred while enabling' in line: raise RuntimeError('plugin enable failed')
        else: raise RuntimeError('Paper startup timed out')
        if proc.poll() is not None: raise RuntimeError('Paper exited during startup')
        env=os.environ.copy();env['NODE_PATH']=str(args.node_modules.resolve())
        client=subprocess.Popen([args.node,str(HERE/'frost-forge-client.cjs'),str(port)],stdin=subprocess.PIPE,
                                stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
        deadline=time.monotonic()+300
        while time.monotonic()<deadline:
            try: line=messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None: break
                continue
            if 'KG-MODFORGE ' in line: print(line.strip(),flush=True)
            if 'KG-MODFORGE REQUEST ' in line:
                command=line.split('KG-MODFORGE REQUEST ',1)[1].strip();client.stdin.write(command+'\n');client.stdin.flush()
            if 'KG-MODFORGE RESULT PASS' in line: success=True;break
            if 'KG-MODFORGE RESULT FAIL' in line: break
        if not success: raise RuntimeError(f'phase {phase} failed or timed out')
    finally:
        if client and client.poll() is None:
            try: print(client.communicate(input='done\n',timeout=10)[0],end='')
            except subprocess.TimeoutExpired: client.kill();client.wait()
        if proc.poll() is None:
            try: proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=50)
            except (BrokenPipeError,subprocess.TimeoutExpired): proc.kill();proc.wait()
        reader.join(timeout=10)
        if not success: print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-220:]),file=sys.stderr)
    watchdog=any('The server has not responded for 10 seconds' in line for line in output)
    if proc.returncode!=0 or not client or client.returncode!=0 or watchdog:
        print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-220:]),file=sys.stderr)
        raise RuntimeError(f'Paper exit={proc.returncode}, client exit={client and client.returncode}, watchdog={watchdog}')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper',type=Path,required=True);p.add_argument('--vanilla',type=Path,required=True)
    p.add_argument('--api-classpath',required=True);p.add_argument('--node-modules',type=Path,required=True)
    p.add_argument('--worldguard',type=Path,required=True);p.add_argument('--worldedit',type=Path,required=True)
    p.add_argument('--core-old',type=Path,required=True);p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
    p.add_argument('--template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v74-template.zip')
    p.add_argument('--pack',type=Path,default=ROOT/'server/resourcepack/kinggrief-mega-rp-v72.zip')
    p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node')
    p.add_argument('--accept-eula',action='store_true');args=p.parse_args()
    if not args.accept_eula: p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    check_pack(args.pack)
    cache=harness.validate_paper_and_vanilla(args.paper,args.vanilla);port=harness.available_port()
    with tempfile.TemporaryDirectory(prefix='kinggrief-modular-forge-test-') as tmp:
        root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True)
        (server/'plugins/KgModularForgeProbe').mkdir();(server/'cache').mkdir()
        shutil.copy2(args.vanilla,server/'cache'/cache);shutil.copy2(args.core_old,server/'plugins/KingGriefCore.jar')
        shutil.copy2(args.worldguard,server/'plugins/WorldGuard.jar');shutil.copy2(args.worldedit,server/'plugins/WorldEdit.jar')
        shutil.copy2(args.template,data/'spawn-v74-template.zip')
        (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
        (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
        (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            'level-type=minecraft:flat\ngenerate-structures=false\nmax-players=2\n',encoding='utf8')
        classes=root/'classes';classes.mkdir()
        cp=os.pathsep.join((str(args.core_old.resolve()),str(args.worldguard.resolve()),str(args.worldedit.resolve()),args.api_classpath))
        subprocess.run([args.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'ModularForgeProbe.java')],check=True)
        with ZipFile(server/'plugins/KgModularForgeProbe.jar','w',ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')): z.write(path,path.relative_to(classes).as_posix())
            z.write(HERE/'plugin-modular-forge.yml','plugin.yml')
        run_phase(args,server,port,1)
        shutil.copy2(args.core,server/'plugins/KingGriefCore.jar')
        run_phase(args,server,port,2)
        run_phase(args,server,port,3)
    print('MODULAR FORGE UPGRADE, RESTART AND WORLDGUARD INTEGRATION: PASS',flush=True)


if __name__=='__main__': main()
