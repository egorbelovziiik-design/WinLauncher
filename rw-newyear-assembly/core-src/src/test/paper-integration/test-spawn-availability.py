#!/usr/bin/env python3
"""Verify the prepared world, commands and preservation on disposable Paper servers."""
import argparse
import importlib.util
import os
from pathlib import Path
import queue
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from zipfile import ZIP_DEFLATED, ZipFile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
spec = importlib.util.spec_from_file_location('prebuilt_helper', HERE / 'test-prebuilt-install.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def run_server(args, server, expected, mode, username='SpawnVisitor'):
    print('STARTING SPAWN SCENARIO:', mode, flush=True)
    config = server / 'plugins/KgSpawnAvailabilityProbe/config.yml'
    config.parent.mkdir(parents=True, exist_ok=True)
    config.write_text(f'expected-world: {expected}\n', encoding='utf-8')
    proc = subprocess.Popen([args.java, '-Xms512M', '-Xmx1536M', '-jar', str(args.paper.resolve()), 'nogui'],
                            cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)
    messages, output = queue.Queue(), []
    def collect():
        for line in proc.stdout: output.append(line); messages.put(line)
    threading.Thread(target=collect, daemon=True).start()
    success, client = False, None
    try:
        deadline, started = time.monotonic() + 360, False
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None: break
                continue
            if 'Done (' in line and 'For help' in line:
                print('SERVER STARTED:', line.strip(), flush=True); started = True; break
            if 'Error occurred while enabling KingGriefCore' in line: break
        if not started: raise RuntimeError('Paper did not start')
        env = os.environ.copy(); env['NODE_PATH'] = str(args.node_modules.resolve())
        client = subprocess.Popen([args.node, str(HERE/'spawn-availability-client.cjs'), str(args.port), username],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try: line = messages.get(timeout=1)
            except queue.Empty:
                if proc.poll() is not None or client.poll() is not None: break
                continue
            if 'KG-SPAWN-AVAILABLE ' in line: print(line.strip(), flush=True)
            if 'KG-SPAWN-AVAILABLE RESULT PASS' in line: success = True; break
            if 'KG-SPAWN-AVAILABLE RESULT FAIL' in line: break
        if not success: raise RuntimeError(f'Prepared spawn is unavailable: {mode}')
    finally:
        if proc.poll() is None:
            try: proc.stdin.write('stop\n'); proc.stdin.flush(); proc.wait(timeout=60)
            except (BrokenPipeError, subprocess.TimeoutExpired): proc.kill(); proc.wait()
        if client:
            try: print(client.communicate(timeout=10)[0], end='')
            except subprocess.TimeoutExpired: client.kill(); client.wait()
        if not success: print('\n--- SERVER LOG TAIL ---\n' + ''.join(output[-180:]), file=sys.stderr)


def build_probe(args, root, server):
    classes = root/'classes'; classes.mkdir()
    cp = str(args.core.resolve()) + os.pathsep + args.api_classpath
    subprocess.run([args.javac, '-encoding', 'UTF-8', '-classpath', cp, '-d', str(classes), str(HERE/'SpawnAvailabilityProbe.java')], check=True)
    with ZipFile(server/'plugins/KgSpawnAvailabilityProbe.jar', 'w', ZIP_DEFLATED) as z:
        for path in classes.rglob('*.class'): z.write(path, path.relative_to(classes).as_posix())
        z.write(HERE/'plugin-spawn-availability.yml', 'plugin.yml')


def scenario(args, cache, name):
    with tempfile.TemporaryDirectory(prefix=f'kg-spawn-{name}-') as tmp:
        root = Path(tmp); server = root/'server'; data = server/'plugins/KingGriefCore'
        data.mkdir(parents=True); (server/'cache').mkdir(); shutil.copy2(args.vanilla, server/'cache'/cache)
        shutil.copy2(args.core, server/'plugins/KingGriefCore.jar')
        if args.kingac: shutil.copy2(args.kingac, server/'plugins/KingAC.jar')
        if args.kingclear: shutil.copy2(args.kingclear, server/'plugins/KingClear.jar')
        (server/'eula.txt').write_text('eula=true\n', encoding='utf-8')
        (data/'config.yml').write_text('enable-builtin-auth: false\nenable-builtin-tab-and-scoreboard: false\npush-resourcepack-on-join: false\n', encoding='utf-8')
        args.port = helper.free_port()
        main_world = 'level-name=spawn_world\n' if name == 'main' else ''
        (server/'server.properties').write_text(
            f'{main_world}online-mode=false\nserver-ip=127.0.0.1\nserver-port={args.port}\nview-distance=2\nsimulation-distance=3\n'
            'allow-nether=false\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-players=2\n', encoding='utf-8')
        old, before, collision = server/'spawn_world', None, None
        if name not in ('fresh', 'selected-conflict'):
            real = root/'linked-user-world' if name == 'symlink' else old
            real.mkdir()
            if name != 'incomplete':
                with ZipFile(args.template) as z: z.extractall(real)
            if name not in ('damaged', 'preexisting-good', 'incomplete'):
                (real/'.spawn_v70_village_ok').unlink(); (real/'.kinggrief_prebuilt_v70').unlink()
            if name == 'damaged': (real/'region/r.0.0.mca').unlink()
            (real/'kg-user-data.txt').write_text('existing spawn is not disposable\n', encoding='utf-8')
            if name == 'symlink': old.symlink_to(real, target_is_directory=True)
            if name not in ('main', 'preexisting-good'): before = helper.fingerprint(real)
        if name == 'stale-done': (data/'.spawn-v70-template-installed').write_text('v70\n', encoding='ascii')
        if name == 'collision':
            collision = server/'kinggrief_spawn'; collision.mkdir()
            (collision/'user-data.txt').write_text('another world, do not touch\n', encoding='utf-8')
            collision_before = helper.fingerprint(collision)
        if name == 'selected-conflict':
            world = server/'kinggrief_spawn'; world.mkdir()
            (world/'user-data.txt').write_text('not the previously installed world\n', encoding='utf-8')
            before = helper.fingerprint(world)
            (data/'.spawn-v722-active-world').write_text('v1\nkinggrief_spawn\n01234567-0123-0123-0123-0123456789ab\n', encoding='ascii')
            proc = subprocess.run([args.java, '-Xms512M', '-Xmx1536M', '-jar', str(args.paper.resolve()), 'nogui'],
                                  cwd=server, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, timeout=360)
            if ('Selected spawn path is not the previously installed world' not in proc.stdout
                    or 'Server is stopping after a failed spawn install' not in proc.stdout
                    or helper.fingerprint(world) != before):
                print('\n'.join(proc.stdout.splitlines()[-120:]), file=sys.stderr)
                raise AssertionError('A conflicting selected-world path was not preserved safely')
            print('SPAWN OWNER CONFLICT: PASS (server stopped without changing the other world)', flush=True)
            return
        build_probe(args, root, server)
        if name == 'command-conflict':
            helper.build_test_plugin(args, root, HERE/'SpawnConflictProbe.java', HERE/'plugin-spawn-conflict.yml',
                                     server/'plugins/KgSpawnConflict.jar')
        expected = 'spawn_world' if name in ('fresh', 'preexisting-good') else ('kinggrief_spawn_2' if name == 'collision' else 'kinggrief_spawn')
        run_server(args, server, expected, name)
        if name == 'legacy-repeat':
            selected = server/'kinggrief_spawn'; uid = (selected/'uid.dat').read_bytes()
            (selected/'kg-custom-file.txt').write_text('retain on ordinary restart\n')
            run_server(args, server, expected, name + '-restart')
            if (selected/'uid.dat').read_bytes() != uid or (selected/'kg-custom-file.txt').read_text() != 'retain on ordinary restart\n':
                raise AssertionError('Ordinary restart changed prepared world identity or edits')
        if name == 'damaged-selected':
            previous = server/'kinggrief_spawn'
            (previous/'region/r.0.0.mca').unlink()
            previous_before = helper.fingerprint(previous)
            run_server(args, server, 'kinggrief_spawn_2', name + '-restart', username='NewVisitor')
            if helper.fingerprint(previous) != previous_before: raise AssertionError('Damaged world was altered')
        if name == 'stale-done' and (data/'.spawn-v70-template-installed').read_text(encoding='ascii') != 'v70\n':
            raise AssertionError('Old install marker changed')
        if before is not None:
            real = root/'linked-user-world' if name == 'symlink' else old
            if helper.fingerprint(real) != before: raise AssertionError('Original world changed during automatic installation')
        if name == 'symlink' and not old.is_symlink(): raise AssertionError('Original symbolic link changed')
        if name == 'main' and (old/'kg-user-data.txt').read_text(encoding='utf-8') != 'existing spawn is not disposable\n':
            raise AssertionError('Main-world user file changed')
        if collision is not None and helper.fingerprint(collision) != collision_before:
            raise AssertionError('An unrelated world with the first fallback name was modified')
    print('SPAWN SCENARIO PASS:', name, flush=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper', required=True, type=Path); p.add_argument('--vanilla', required=True, type=Path)
    p.add_argument('--api-classpath', required=True); p.add_argument('--node-modules', required=True, type=Path)
    p.add_argument('--core', type=Path, default=ROOT/'KingGriefCore.jar')
    p.add_argument('--kingac', type=Path); p.add_argument('--kingclear', type=Path)
    p.add_argument('--template', type=Path, default=ROOT/'server/plugins/KingGriefCore/spawn-v71-template.zip')
    p.add_argument('--java', default='java'); p.add_argument('--javac', default='javac'); p.add_argument('--node', default='node')
    scenarios = ('fresh', 'legacy', 'legacy-repeat', 'stale-done', 'collision', 'command-conflict', 'main', 'damaged', 'damaged-selected', 'incomplete', 'symlink', 'preexisting-good', 'selected-conflict')
    p.add_argument('--scenario', choices=('all',) + scenarios, default='all')
    p.add_argument('--accept-eula', action='store_true'); args = p.parse_args()
    if not args.accept_eula: p.error('Review https://aka.ms/MinecraftEULA; pass --accept-eula')
    cache = helper.cache_name(args.paper, args.vanilla)
    for name in scenarios if args.scenario == 'all' else (args.scenario,): scenario(args, cache, name)
    print('SPAWN AVAILABILITY INTEGRATION: PASS', flush=True)


if __name__ == '__main__': main()
