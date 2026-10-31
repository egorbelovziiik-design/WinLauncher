#!/usr/bin/env python3
"""Historical regression: install a real v70 spawn, then upgrade with the original v71 Core while preserving edits and force-loads."""
import argparse, importlib.util, shutil, tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
spec=importlib.util.spec_from_file_location('prebuilt',HERE/'test-prebuilt-install.py')
h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--paper',type=Path,required=True);p.add_argument('--vanilla',type=Path,required=True);p.add_argument('--api-classpath',required=True)
 p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--accept-eula',action='store_true')
 p.add_argument('--core',type=Path,default=ROOT.parent/'kinggrief/public/KingGriefCore-v7.1.0.jar');p.add_argument('--old-core',type=Path,default=ROOT.parent/'kinggrief/public/KingGriefCore-v7.0.0.jar')
 p.add_argument('--template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v71-template.zip')
 p.add_argument('--old-template',type=Path,default=ROOT/'server/plugins/KingGriefCore/spawn-v70-template.zip')
 a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 cache=h.cache_name(a.paper,a.vanilla)
 with tempfile.TemporaryDirectory(prefix='kg-v70-upgrade-') as tmp:
  root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
  shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.old_core,server/'plugins/KingGriefCore.jar');shutil.copy2(a.old_template,data/'spawn-v70-template.zip')
  (server/'eula.txt').write_text('eula=true\n',encoding='utf8');(data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n',encoding='utf8')
  (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={h.free_port()}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n',encoding='utf8')
  h.build_test_plugin(a,root,HERE/'SpawnV70UpgradeProbe.java',HERE/'plugin-spawn-v70-upgrade.yml',server/'plugins/KgSpawnInstallProbe.jar')
  h.run_server(a,server,'v70seed')
  shutil.copy2(a.core,server/'plugins/KingGriefCore.jar');shutil.copy2(a.template,data/'spawn-v71-template.zip')
  h.run_server(a,server,'v71upgrade')
 print('V70 TO V71 WORLD UPGRADE: PASS',flush=True)
if __name__=='__main__':main()
