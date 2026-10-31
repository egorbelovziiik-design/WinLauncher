#!/usr/bin/env python3
"""Paper event placement regression: deterministic safety or a natural Overworld."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import zipfile
from run import build_mock_plugin

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', type=Path, required=True)
    p.add_argument('--vanilla', type=Path, required=True)
    p.add_argument('--api-classpath', required=True)
    p.add_argument('--core', type=Path, default=ROOT / 'KingGriefCore.jar')
    p.add_argument('--template', type=Path, default=ROOT / 'server/plugins/KingGriefCore/spawn-v73-template.zip')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    p.add_argument('--natural', action='store_true', help='run unforced manual and automatic searches in a generated normal world')
    p.add_argument('--world-seed', type=int, default=8150324189123)
    p.add_argument('--search-seed', type=int, default=46819235)
    p.add_argument('--worldguard', type=Path, help='real WorldGuard JAR; also provide --worldedit')
    p.add_argument('--worldedit', type=Path, help='real WorldEdit JAR; also provide --worldguard')
    p.add_argument('--accept-eula', action='store_true')
    args = p.parse_args()
    if not args.accept_eula:
        p.error('review https://aka.ms/MinecraftEULA and explicitly pass --accept-eula')
    real_wg = bool(args.worldguard and args.worldedit)
    if bool(args.worldguard) != bool(args.worldedit) or (real_wg and args.natural):
        p.error('provide both --worldguard and --worldedit; do not combine with --natural')
    with zipfile.ZipFile(args.paper) as z:
        if json.loads(z.read('version.json'))['id'] != '1.21.4':
            raise ValueError('Paper 1.21.4 required')
        digest, _, cache_name = z.read('META-INF/download-context').decode().strip().split('\t')
    if Path(cache_name).name != cache_name or hashlib.sha256(args.vanilla.read_bytes()).hexdigest() != digest:
        raise ValueError('Mojang server jar does not match Paper')
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        port = s.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix='kinggrief-event-placement-test-') as tmp:
        root = Path(tmp)
        server = root / 'server'
        data = server / 'plugins/KingGriefCore'
        data.mkdir(parents=True)
        (server / 'cache').mkdir()
        shutil.copy2(args.vanilla, server / 'cache' / cache_name)
        shutil.copy2(args.core, server / 'plugins/KingGriefCore.jar')
        shutil.copy2(args.template, data / 'spawn-v73-template.zip')
        (data / 'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n', encoding='utf8')
        (server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
        world_type = 'normal' if args.natural else 'flat'
        (server / 'server.properties').write_text(
            f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\n'
            'view-distance=2\nsimulation-distance=3\nallow-nether=false\nspawn-protection=0\n'
            f'level-type=minecraft:{world_type}\nlevel-seed={args.world_seed}\n'
            'generate-structures=false\nmax-players=1\n', encoding='utf8')
        if real_wg:
            shutil.copy2(args.worldedit, server / 'plugins/WorldEdit.jar')
            shutil.copy2(args.worldguard, server / 'plugins/WorldGuard.jar')
        elif not args.natural:
            build_mock_plugin(args, root, 'wg', 'WorldGuard')
        classes = root / 'classes'
        classes.mkdir()
        cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
        if not args.natural:
            cp += os.pathsep + str(server / 'plugins/WorldGuard.jar')
        if real_wg:
            cp += os.pathsep + str(server / 'plugins/WorldEdit.jar')
        source = 'NaturalEventsProbe.java' if args.natural else ('RealWorldGuardProbe.java' if real_wg else 'EventPlacementProbe.java')
        plugin_yml = 'plugin-natural-events.yml' if args.natural else ('plugin-real-worldguard.yml' if real_wg else 'plugin-event-placement.yml')
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-classpath', cp, '-d', str(classes), str(HERE / source)], check=True)
        with zipfile.ZipFile(server / 'plugins/KgEventPlacementProbe.jar', 'w', zipfile.ZIP_DEFLATED) as z:
            for path in sorted(classes.rglob('*.class')):
                z.write(path, path.relative_to(classes).as_posix())
            z.write(HERE / plugin_yml, 'plugin.yml')
        try:
            result = subprocess.run([args.java, '-Xms512M', '-Xmx1536M', f'-Dkg.search.seed={args.search_seed}', '-jar', str(args.paper.resolve()), 'nogui'],
                                    cwd=server, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                    stderr=subprocess.STDOUT, text=True, timeout=360)
        except subprocess.TimeoutExpired as error:
            print((error.stdout or b'')[-30000:], file=sys.stderr)
            raise RuntimeError('Paper did not complete event placement regression')
        log = result.stdout
        marker = 'KG-NATURAL-EVENTS' if args.natural else ('KG-REAL-WG' if real_wg else 'KG-EVENT-PLACEMENT')
        good = (result.returncode == 0 and f'{marker} PASS:' in log
                and f'{marker} FAIL:' not in log
                and 'The server has not responded for 10 seconds' not in log)
        if not good:
            print('\n--- SERVER LOG TAIL ---\n' + '\n'.join(log.splitlines()[-220:]), file=sys.stderr)
            raise RuntimeError(f'event placement regression failed (exit {result.returncode})')
        for line in log.splitlines():
            if f'{marker} PASS:' in line or 'KG-NATURAL MANUAL FOUND:' in line or 'KG-NATURAL AUTO FOUND:' in line:
                print(line, flush=True)
    mode = '(NATURAL WORLD)' if args.natural else ('(REAL WG)' if real_wg else '(MOCK WG)')
    print('EVENT PLACEMENT INTEGRATION ' + mode + ': PASS', flush=True)


if __name__ == '__main__':
    main()
