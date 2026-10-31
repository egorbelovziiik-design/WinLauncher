#!/usr/bin/env python3
"""50 new artifacts via real use-item packets, immediate utilities and protected purchases on Paper."""
import argparse, os, queue, shutil, subprocess, sys, tempfile, threading, time
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import run as harness
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--paper',type=Path,required=True);p.add_argument('--vanilla',type=Path,required=True)
 p.add_argument('--api-classpath',required=True);p.add_argument('--node-modules',type=Path,required=True)
 p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar');p.add_argument('--template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v74-template.zip')
 p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--node',default='node');p.add_argument('--accept-eula',action='store_true')
 p.add_argument('--mock-player-points',action='store_true',help='Also test shop failure handling with a mock PlayerPoints plugin')
 a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 cache=harness.validate_paper_and_vanilla(a.paper,a.vanilla);port=harness.available_port()
 with tempfile.TemporaryDirectory(prefix='kinggrief-artifacts-test-') as tmp:
  root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
  shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.core,server/'plugins/KingGriefCore.jar');shutil.copy2(a.template,data/'spawn-v74-template.zip')
  (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
  (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
  (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=2\n',encoding='utf8')
  classes=root/'classes';classes.mkdir();cp=str(a.core.resolve())+os.pathsep+a.api_classpath
  subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'NewArtifactsProbe.java')],check=True)
  with ZipFile(server/'plugins/KgNewArtifactsProbe.jar','w',ZIP_DEFLATED) as z:
   for f in classes.rglob('*.class'):z.write(f,f.relative_to(classes).as_posix())
   z.write(HERE/'plugin-new-artifacts.yml','plugin.yml')
  if a.mock_player_points:
   mockclasses=root/'mock-classes';mockclasses.mkdir()
   subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(mockclasses),str(HERE/'MockPlayerPoints.java')],check=True)
   with ZipFile(server/'plugins/MockPlayerPoints.jar','w',ZIP_DEFLATED) as z:
    for f in mockclasses.rglob('*.class'):z.write(f,f.relative_to(mockclasses).as_posix())
    z.write(HERE/'plugin-mock-points.yml','plugin.yml')
  proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
  output=[];messages=queue.Queue()
  def read():
   for line in proc.stdout:output.append(line);messages.put(line)
  threading.Thread(target=read,daemon=True).start();client=None;success=False
  try:
   deadline=time.monotonic()+360;started=False
   while time.monotonic()<deadline:
    try:line=messages.get(timeout=1)
    except queue.Empty:
     if proc.poll() is not None:break
     continue
    if 'Done (' in line and 'For help' in line:print('SERVER STARTED:',line.strip(),flush=True);started=True;break
    if 'Error occurred while enabling' in line:break
   if not started:raise RuntimeError('Paper did not start')
   env=os.environ.copy();env['NODE_PATH']=str(a.node_modules.resolve())
   client=subprocess.Popen([a.node,str(HERE/'winter-client.cjs'),str(port)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env)
   deadline=time.monotonic()+240
   while time.monotonic()<deadline:
    try:line=messages.get(timeout=1)
    except queue.Empty:
     if proc.poll() is not None or client.poll() is not None:break
     continue
    if 'KG-ART REQUEST_USE' in line:client.stdin.write('use\n');client.stdin.flush()
    elif 'KG-ART REQUEST_OFF' in line:client.stdin.write('off\n');client.stdin.flush()
    elif 'KG-ART ' in line:print(line.strip(),flush=True)
    if 'KG-ART RESULT PASS' in line:success=True;break
    if 'KG-ART RESULT FAIL' in line:break
   if not success:raise RuntimeError('New artifacts test failed/timed out')
  finally:
   if client and client.poll() is None:
    try:print(client.communicate(input='done\n',timeout=10)[0],end='')
    except subprocess.TimeoutExpired:client.kill();client.wait()
   if proc.poll() is None:
    try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
    except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
   if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-180:]),file=sys.stderr)
 print('NEW ARTIFACTS INTEGRATION: PASS',flush=True)
if __name__=='__main__':main()
