#!/usr/bin/env python3
"""Smoke-test the actual flat v7 assembly on disposable, loopback-only Paper."""
import argparse, os, queue, re, shutil, subprocess, sys, tempfile, threading, time
from pathlib import Path
from zipfile import ZipFile
import run as harness
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--paper',required=True,type=Path);p.add_argument('--vanilla',required=True,type=Path)
 p.add_argument('--assembly',type=Path,default=ROOT/'rw-newyear-full-assembly-v7.4.0.zip')
 p.add_argument('--node-modules',required=True,type=Path);p.add_argument('--java',default='java');p.add_argument('--node',default='node')
 p.add_argument('--api-classpath',required=True);p.add_argument('--javac',default='javac')
 p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 cache=harness.validate_paper_and_vanilla(a.paper,a.vanilla);port=harness.available_port()
 with tempfile.TemporaryDirectory(prefix='kinggrief-full-smoke-') as tmp:
  server=Path(tmp)/'server';server.mkdir()
  with ZipFile(a.assembly) as z:
   if z.testzip() is not None:raise RuntimeError('Corrupt assembly')
   names=z.namelist()
   if 'plugins/KingGriefCore/spawn-v74-template.zip' not in names or any(n in names for n in ('plugins/KingGriefCore/spawn-v73-template.zip','plugins/KingGriefCore/spawn-v71-template.zip','plugins/KingGriefCore/spawn-v70-template.zip','plugins/KingGriefCore/spawn-v66-template.zip')):
    raise RuntimeError('Incorrect spawn template in new assembly')
   import io
   with ZipFile(io.BytesIO(z.read('plugins/KingGriefCore/spawn-v74-template.zip'))) as template:
    if 'data/chunks.dat' in template.namelist():raise RuntimeError('Template contains forced chunk tickets')
    if '.kinggrief_prebuilt_v74' not in template.namelist():raise RuntimeError('Missing v74 marker')
   with ZipFile(io.BytesIO(z.read('plugins/KingGriefCore.jar'))) as core:
    if 'version: 7.4.0' not in core.read('plugin.yml').decode():raise RuntimeError('Incorrect Core version')
   for plugin, version in (('KingAC','8.3.1'),('KingClear','1.0.2')):
    with ZipFile(io.BytesIO(z.read('plugins/'+plugin+'.jar'))) as jar:
     if ('version: '+version) not in jar.read('plugin.yml').decode(): raise RuntimeError('Incorrect '+plugin+' version')
   for name in names:
    if name.startswith('/') or '..' in Path(name).parts:raise RuntimeError('Unsafe assembly path')
   z.extractall(server)
  # Include a probe which verifies final world, blocks, models, commands and bridges.
  classes=Path(tmp)/'classes';classes.mkdir()
  cp=str(server/'plugins/KingGriefCore.jar')+os.pathsep+a.api_classpath
  subprocess.run([a.javac,'-encoding','UTF-8','-classpath',cp,'-d',str(classes),str(HERE/'SpawnAvailabilityProbe.java')],check=True)
  with ZipFile(server/'plugins/KgSpawnAvailabilityProbe.jar','w') as probe:
   for path in classes.rglob('*.class'):probe.write(path,path.relative_to(classes).as_posix())
   probe.write(HERE/'plugin-spawn-availability.yml','plugin.yml')
  cfg=server/'plugins/KgSpawnAvailabilityProbe';cfg.mkdir()
  (cfg/'config.yml').write_text('expected-world: spawn_world\n',encoding='utf8')
  bridge=(server/'plugins/KingGriefBridge/config.yml').read_text()
  if not re.search(r'^secret:\s*["\']?CHANGE_ME["\']?\s*$',bridge,re.M):
   raise RuntimeError('Refusing to run smoke test with a non-placeholder bridge credential')
  # Never contact a real KingConnect cloud or real servers from a test instance.
  with ZipFile(server/'plugins/KingConnect.jar') as z: connect=z.read('config.yml').decode()
  connect,n=re.subn(r'(cloud-sync:\s*\n\s*enabled:)\s*true',r'\1 false',connect,count=1)
  if n!=1:raise RuntimeError('Cannot isolate cloud-sync in the test config')
  conffile=server/'plugins/KingConnect/config.yml';conffile.parent.mkdir(parents=True);conffile.write_text(connect)
  (server/'cache').mkdir();shutil.copy2(a.vanilla,server/'cache'/cache)
  (server/'eula.txt').write_text('eula=true\n')
  with (server/'server.properties').open('a') as f:
   f.write(f'\nonline-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=3\n')
  proc=subprocess.Popen([a.java,'-Xms512M','-Xmx1536M','-jar',str(a.paper.resolve()),'nogui'],cwd=server,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
  output=[];messages=queue.Queue()
  def read():
   for line in proc.stdout:output.append(line);messages.put(line)
  threading.Thread(target=read,daemon=True).start();success=False
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
   client=subprocess.run([a.node,str(HERE/'full-client.cjs'),str(port)],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,env=env,timeout=75)
   print(client.stdout.strip(),flush=True)
   if client.returncode or 'FULL_CLIENT_PREPARED_SPAWN_POSITION' not in client.stdout:raise RuntimeError('Protocol client could not enter the full assembly')
   text=''.join(output)
   for plugin in ('KingGriefCore','KingAC','KingConnect','KingClear','KingGriefBridge'):
    if not re.search(r'Enabling '+plugin+r' v',text):raise RuntimeError(plugin+' was not enabled')
   if 'KG-SPAWN-AVAILABLE RESULT PASS' not in text:raise RuntimeError('Prepared-world probe did not pass')
   if '[SpawnInstall] Prepared v74 spawn installed and initialized' not in text:raise RuntimeError('Prepared spawn was not installed')
   if 'Error occurred while enabling' in text or 'Exception in server tick loop' in text:raise RuntimeError('Plugin or server startup error')
   if not (server/'plugins/KingGriefCore/.spawn-v70-template-installed').is_file():raise RuntimeError('Missing v70 install marker')
   success=True;print('FULL ASSEMBLY SMOKE: PASS 5 plugins, real player in prepared world, tree, floor, NPC and 11 warps',flush=True)
  finally:
   if proc.poll() is None:
    try:proc.stdin.write('stop\n');proc.stdin.flush();proc.wait(timeout=60)
    except (BrokenPipeError,subprocess.TimeoutExpired):proc.kill();proc.wait()
   if not success:print('\n--- SERVER LOG TAIL ---\n'+''.join(output[-180:]),file=sys.stderr)
if __name__=='__main__':main()
