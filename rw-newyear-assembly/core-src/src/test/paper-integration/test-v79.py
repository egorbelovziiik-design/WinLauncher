#!/usr/bin/env python3
"""Two real players, nine pets, spawn protection, gifts, cancellation and SIGKILL."""
import argparse,os,queue,shutil,signal,subprocess,sys,tempfile,threading,time
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
import run as harness
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]

def phase(a,server,port,number):
 (server/'plugins/KgV79Probe/config.yml').write_text(f'phase: {number}\n',encoding='utf8')
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
  if not started:raise RuntimeError('Paper startup failed')
  env=os.environ.copy();env['NODE_PATH']=str(a.node_modules.resolve())
  client=subprocess.Popen([a.node,str(HERE/'v79-client.cjs'),str(port)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
  deadline=time.monotonic()+300
  while time.monotonic()<deadline:
   try:line=messages.get(timeout=1)
   except queue.Empty:
    if proc.poll() is not None or client.poll() is not None:break
    continue
   if 'KG-V79 ' in line:print(line.strip(),flush=True)
   if 'KG-V79 REQUEST ' in line:
    request=line.split('KG-V79 REQUEST ',1)[1].strip();client.stdin.write(request+'\n');client.stdin.flush()
   if 'KG-V79 RESULT PASS' in line:success=True;break
   if 'KG-V79 RESULT FAIL' in line:break
  if not success:raise RuntimeError('V79 assertions failed or timed out')
  if number==1:
   proc.kill();proc.wait(timeout=30);print('V79 PHASE 1 SIGKILL AFTER COMMITTED GIFTS AND PET',flush=True)
 finally:
  if client and client.poll() is None:
   try:client_text=client.communicate(input='done\n',timeout=10)[0];print(client_text,end='')
   except subprocess.TimeoutExpired:client.kill();client.wait()
  if proc.poll() is None:
   try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
   except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
  thread.join(timeout=10)
  if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-200:]),file=sys.stderr)
 expected=-signal.SIGKILL if number==1 else 0
 if proc.returncode!=expected or not client or client.returncode!=0 or 'V79_CLIENT PASS' not in client_text:
  raise RuntimeError(f'phase {number}: Paper={proc.returncode}, expected={expected}, client={client and client.returncode}')
 if any('The server has not responded for 10 seconds' in line for line in output):raise RuntimeError('Paper watchdog warning')

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('paper','vanilla','node-modules'):p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--api-classpath',required=True);p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
 p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node')
 p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 cache=harness.validate_paper_and_vanilla(a.paper,a.vanilla);port=harness.available_port()
 with tempfile.TemporaryDirectory(prefix='kinggrief-v79-test-') as tmp:
  root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
  (server/'plugins/KgV79Probe').mkdir();shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.core,server/'plugins/KingGriefCore.jar')
  (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
  (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
  (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=2\n',encoding='utf8')
  cp=str(a.core.resolve())+os.pathsep+a.api_classpath
  for source,yaml,name in [('V79Probe.java','plugin-v79.yml','KgV79Probe.jar'),('MockPlayerPoints.java','plugin-mock-points.yml','PlayerPoints.jar')]:
   classes=root/(name+'-classes');classes.mkdir()
   subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/source)],check=True)
   with ZipFile(server/'plugins'/name,'w',ZIP_DEFLATED) as z:
    for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
    z.write(HERE/yaml,'plugin.yml')
  phase(a,server,port,1);phase(a,server,port,2)
 print('V79 INTEGRATION: PASS (pets, spawn protection, navigation and gift crash restart)',flush=True)
if __name__=='__main__':main()
