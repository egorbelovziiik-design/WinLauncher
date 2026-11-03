#!/usr/bin/env python3
"""Paper regression: sender boundaries, old spawn and actual hopper transfers."""
import argparse,os,queue,shutil,subprocess,sys,tempfile,threading,time
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import run as harness
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('paper','vanilla','node-modules'):p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--api-classpath',required=True);p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
 p.add_argument('--old-template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v76-template.zip')
 p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node');p.add_argument('--accept-eula',action='store_true')
 a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 cache=harness.validate_paper_and_vanilla(a.paper,a.vanilla);port=harness.available_port()
 with tempfile.TemporaryDirectory(prefix='kinggrief-global-fix-') as tmp:
  root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
  shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.core,server/'plugins/KingGriefCore.jar')
  (server/'legacy_spawn').mkdir()
  with ZipFile(a.old_template) as z:z.extractall(server/'legacy_spawn')
  (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
  (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
  (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\nenable-command-block=true\n',encoding='utf8')
  classes=root/'classes';classes.mkdir();cp=str(a.core.resolve())+os.pathsep+a.api_classpath
  subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'GlobalFixProbe.java')],check=True)
  with ZipFile(server/'plugins/KgGlobalFixProbe.jar','w',ZIP_DEFLATED) as z:
   for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
   z.write(HERE/'plugin-global-fix.yml','plugin.yml')
  proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
  output=[];messages=queue.Queue()
  def read():
   for line in proc.stdout:output.append(line);messages.put(line)
  thread=threading.Thread(target=read,daemon=True);thread.start();client=None;success=False;client_text=''
  try:
   deadline=time.monotonic()+360;started=False
   while time.monotonic()<deadline:
    try:line=messages.get(timeout=1)
    except queue.Empty:
     if proc.poll() is not None:break
     continue
    if 'Done (' in line and 'For help' in line:started=True;break
    if 'Error occurred while enabling' in line:break
   if not started:raise RuntimeError('Paper did not start')
   env=os.environ.copy();env['NODE_PATH']=str(a.node_modules.resolve())
   client=subprocess.Popen([a.node,str(HERE/'wheel-client.cjs'),str(port)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
   deadline=time.monotonic()+300
   while time.monotonic()<deadline:
    try:line=messages.get(timeout=1)
    except queue.Empty:
     if proc.poll() is not None or client.poll() is not None:break
     continue
    if 'KG-GLOBAL ' in line:print(line.strip(),flush=True)
    if 'KG-GLOBAL RESULT PASS' in line:success=True;break
    if 'KG-GLOBAL RESULT FAIL' in line:break
   if not success:raise RuntimeError('Global fix assertions failed or timed out')
  finally:
   if client and client.poll() is None:
    try:client_text=client.communicate(input='done\n',timeout=10)[0];print(client_text,end='')
    except subprocess.TimeoutExpired:client.kill();client.wait()
   if proc.poll() is None:
    try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
    except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
   thread.join(timeout=10)
   if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]),file=sys.stderr)
  if proc.returncode!=0 or not client or client.returncode!=0 or 'WHEEL_CLIENT PASS' not in client_text:raise RuntimeError('Paper or clients failed')
 print('GLOBAL FIX INTEGRATION: PASS',flush=True)
if __name__=='__main__':main()
