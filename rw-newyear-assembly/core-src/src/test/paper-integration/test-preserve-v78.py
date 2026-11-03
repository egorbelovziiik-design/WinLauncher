#!/usr/bin/env python3
"""Upgrade actual 7.8.0 to 7.10.0 without reinstalling the active v77 world."""
import argparse,hashlib,importlib.util,shutil,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[3]
spec=importlib.util.spec_from_file_location('prebuilt',HERE/'test-prebuilt-install.py');h=importlib.util.module_from_spec(spec);spec.loader.exec_module(h)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--paper',required=True,type=Path);p.add_argument('--vanilla',required=True,type=Path);p.add_argument('--api-classpath',required=True)
 p.add_argument('--java',default='java');p.add_argument('--javac',default='javac');p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar')
 p.add_argument('--old-core',type=Path,default=ROOT.parent/'kinggrief/public/KingGriefCore-v7.8.0.jar');p.add_argument('--node-modules',type=Path);p.add_argument('--accept-eula',action='store_true');a=p.parse_args()
 if not a.accept_eula:p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
 if hashlib.sha256(a.old_core.read_bytes()).hexdigest()!='f1b9e9b96932ca50abc573d3924f5a86aaffa06c20119fc60c2169bb0e9ee4a2':raise AssertionError('Historical 7.8.0 JAR differs from published release')
 cache=h.cache_name(a.paper,a.vanilla)
 with tempfile.TemporaryDirectory(prefix='kg-preserve-v78-') as tmp:
  root=Path(tmp);server=root/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
  shutil.copy2(a.vanilla,server/'cache'/cache);shutil.copy2(a.old_core,server/'plugins/KingGriefCore.jar')
  (server/'eula.txt').write_text('eula=true\n');(data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n')
  (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={h.free_port()}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
  h.build_test_plugin(a,root,HERE/'PreserveV77Probe.java',HERE/'plugin-preserve-v77.yml',server/'plugins/KgSpawnInstallProbe.jar')
  h.run_server(a,server,'seed78');shutil.copy2(a.core,server/'plugins/KingGriefCore.jar');h.run_server(a,server,'verify710')
 print('V78 PRESERVATION INTEGRATION: PASS (world, chest, force ticket, warp, coins, wheel, tree, pet, gift and playtime)',flush=True)
if __name__=='__main__':main()
