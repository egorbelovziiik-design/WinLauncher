#!/usr/bin/env python3
"""Real Core 7.6.0 -> 7.7 migration, with actual edited chest and forced chunk."""
import argparse, hashlib, importlib.util, shutil, tempfile
from pathlib import Path
from zipfile import ZipFile
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[3]
spec=importlib.util.spec_from_file_location('prebuilt',HERE/'test-prebuilt-install.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--paper',required=True,type=Path);p.add_argument('--vanilla',required=True,type=Path)
 p.add_argument('--api-classpath',required=True);p.add_argument('--java',default='java');p.add_argument('--javac',default='javac')
 p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
 p.add_argument('--old-core',type=Path,default=ROOT.parent/'kinggrief/public/KingGriefCore-v7.6.0.jar')
 p.add_argument('--old-template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v76-template.zip')
 p.add_argument('--node-modules',type=Path)
 p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 if hashlib.sha256(a.old_core.read_bytes()).hexdigest()!='05b01850069eeca0b53f98ee0092c9d1d4b41dae4623b18f272256d6662eb007':raise AssertionError('Historical 7.6.0 Core JAR differs from the published release')
 if hashlib.sha256(a.old_template.read_bytes()).hexdigest()!='aac91f34f2663626b90ea88cb47bf0c33355b0baad43a76f26f76e0250bec2d2':raise AssertionError('Historical v76 template differs from the published release')
 cache=h.cache_name(a.paper,a.vanilla)
 for fallback in (False,True):
  with tempfile.TemporaryDirectory(prefix='kg-v77-real-upgrade-') as tmp:
   root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
   shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.old_core,server/'plugins/KingGriefCore.jar');shutil.copy2(a.old_template,data/'spawn-v76-template.zip')
   (server/'eula.txt').write_text('eula=true\n',encoding='utf8');(data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
   (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={h.free_port()}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n',encoding='utf8')
   if fallback:
    unknown=server/'spawn_world';unknown.mkdir()
    with ZipFile(a.old_template) as z:z.extractall(unknown)
    (unknown/'.kinggrief_prebuilt_v70').unlink();(unknown/'.spawn_v70_village_ok').unlink()
    (unknown/'foreign-data.txt').write_text('do not modify\n',encoding='utf8')
    foreign=h.fingerprint(unknown)
   h.build_test_plugin(a,root,HERE/'SpawnV77UpgradeProbe.java',HERE/'plugin-spawn-v77-upgrade.yml',server/'plugins/KgSpawnInstallProbe.jar')
   h.run_server(a,server,'v760seed')
   probe=server/'plugins/KgSpawnInstallProbe'
   old_name=(probe/'old-name.txt').read_text()
   if old_name != ('kinggrief_spawn' if fallback else 'spawn_world'):raise AssertionError('old Core did not select expected path')
   old=server/old_name;before=h.fingerprint(old)
   shutil.copy2(a.core,server/'plugins/KingGriefCore.jar')
   h.run_server(a,server,'upgrade')
   if h.fingerprint(old)!=before:raise AssertionError('Original old world files changed during migration')
   if fallback and h.fingerprint(unknown)!=foreign:raise AssertionError('Unknown world changed during migration')
   expected='kinggrief_spawn_2' if fallback else 'kinggrief_spawn'
   if not (server/expected/'.kinggrief_prebuilt_v77').is_file():raise AssertionError('Expected new world missing')
   print('REAL 7.6.0 UPGRADE PASS:',old_name,'->',expected,'edited chest, file and force-load preserved',flush=True)
 print('REAL V77 UPGRADE INTEGRATION: PASS',flush=True)
if __name__=='__main__':main()
