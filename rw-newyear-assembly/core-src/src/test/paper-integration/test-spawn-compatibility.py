#!/usr/bin/env python3
"""Exercise standalone and failing-Core spawn bridges on disposable Paper servers."""
import argparse, importlib.util, os, queue, shutil, subprocess, sys, tempfile, threading, time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
spec = importlib.util.spec_from_file_location('prebuilt_helper', HERE/'test-prebuilt-install.py')
helper = importlib.util.module_from_spec(spec); spec.loader.exec_module(helper)


def scenario(args, cache, mode):
    broken = mode in ('broken-core', 'disabled-core')
    protected = mode != 'standalone-survival'
    xray = mode != 'standalone-spawn'
    world = 'world' if mode == 'standalone-survival' else 'spawn_world'
    print('STARTING COMPATIBILITY SCENARIO:', mode, flush=True)
    with tempfile.TemporaryDirectory(prefix='kg-spawn-compat-') as tmp:
        root = Path(tmp); server = root/'server'; plugins = server/'plugins'
        (plugins/'KgClearCompatibilityProbe').mkdir(parents=True); (server/'cache').mkdir()
        shutil.copy2(args.vanilla, server/'cache'/cache)
        shutil.copy2(args.kingclear, plugins/'KingClear.jar'); shutil.copy2(args.kingac, plugins/'KingAC.jar')
        if broken: helper.build_test_plugin(args, root, HERE/'BrokenCoreProbe.java', HERE/'plugin-broken-core.yml', plugins/'KingGriefCore.jar')
        helper.build_test_plugin(args, root, HERE/'ClearCompatibilityProbe.java', HERE/'plugin-clear-compat.yml', plugins/'KgClearCompatibilityProbe.jar')
        (plugins/'KgClearCompatibilityProbe/config.yml').write_text(
            f'mode: {mode}\nexpect-protection: {str(protected).lower()}\nexpect-xray: {str(xray).lower()}\n'
            f'broken-core: {str(broken).lower()}\ndisabled-core: {str(mode == "disabled-core").lower()}\n',encoding='utf8')
        (server/'eula.txt').write_text('eula=true\n',encoding='utf8'); port=helper.free_port()
        (server/'server.properties').write_text(f'level-name={world}\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\n'
            'simulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=2\n',encoding='utf8')
        proc=subprocess.Popen([args.java,'-Xms512M','-Xmx1536M','-jar',str(args.paper.resolve()),'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        output,messages=[],queue.Queue()
        def collect():
            for line in proc.stdout:output.append(line);messages.put(line)
        threading.Thread(target=collect,daemon=True).start();client=None;success=False
        try:
            started=False;deadline=time.monotonic()+360
            while time.monotonic()<deadline:
                try:line=messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None:break
                    continue
                if 'Done (' in line and 'For help' in line:print('SERVER STARTED:',line.strip(),flush=True);started=True;break
                if 'Error occurred while enabling' in line:break
            if not started:raise RuntimeError('Paper did not start')
            env=os.environ.copy();env['NODE_PATH']=str(args.node_modules.resolve())
            client=subprocess.Popen([args.node,str(HERE/'spawn-availability-client.cjs'),str(port),'CompatVisitor'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
            deadline=time.monotonic()+120
            while time.monotonic()<deadline:
                try:line=messages.get(timeout=1)
                except queue.Empty:
                    if proc.poll() is not None or client.poll() is not None:break
                    continue
                if 'KG-CLEAR-COMPAT ' in line:print(line.strip(),flush=True)
                if 'KG-CLEAR-COMPAT RESULT PASS' in line:success=True;break
                if 'KG-CLEAR-COMPAT RESULT FAIL' in line:break
            if success and broken:
                log=''.join(output);success="Cannot resolve Core's active spawn" in log and "Cannot check Core's active spawn" in log
            if not success:raise RuntimeError('Standalone/fail-safe verification failed: '+mode)
        finally:
            if proc.poll() is None:
                try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
                except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
            if client:
                try:print(client.communicate(timeout=10)[0],end='')
                except subprocess.TimeoutExpired:client.kill();client.wait()
            if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-180:]),file=sys.stderr)
    print('COMPATIBILITY SCENARIO PASS:',mode,flush=True)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper',type=Path,required=True);p.add_argument('--vanilla',type=Path,required=True)
    p.add_argument('--api-classpath',required=True);p.add_argument('--node-modules',type=Path,required=True)
    p.add_argument('--kingac',type=Path,default=ROOT/'KingAC.jar');p.add_argument('--kingclear',type=Path,default=ROOT/'KingClear.jar')
    p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node')
    modes=('standalone-spawn','standalone-survival','broken-core','disabled-core')
    p.add_argument('--scenario',choices=('all',)+modes,default='all');p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
    if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
    cache=helper.cache_name(a.paper,a.vanilla)
    for mode in modes if a.scenario=='all' else (a.scenario,):scenario(a,cache,mode)
    print('SPAWN COMPATIBILITY INTEGRATION: PASS',flush=True)
if __name__=='__main__':main()
