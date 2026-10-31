#!/usr/bin/env python3
"""Build deterministic KingAC / KingClear JARs and matching assembly copies.

Requires an externally supplied Spigot/Paper API classpath. No dependency
classes are packaged. Paper integration tests are required separately.
"""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
PLUGINS = {'KingAC': 'kingac-src', 'KingClear': 'clear-src'}


def add(jar, name, data):
    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.external_attr = 0o100644 << 16
    jar.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def build(name, directory, args):
    source = ROOT/directory
    resources = source/'src/main/resources'
    metadata = (resources/'plugin.yml').read_text(encoding='utf-8')
    match = re.search(r'(?m)^version:\s*([0-9]+(?:\.[0-9]+)+)\s*$', metadata)
    if not match: raise ValueError(f'{name} plugin version is missing')
    version = match.group(1)
    with tempfile.TemporaryDirectory(prefix='kinggrief-standalone-build-') as temp:
        classes = Path(temp)/'classes'; classes.mkdir()
        sources = sorted((source/'src/main/java').rglob('*.java'))
        subprocess.run([args.javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                        '-classpath', args.api_classpath, '-d', str(classes)] + list(map(str, sources)), check=True)
        staged = Path(temp)/f'{name}.jar'
        with zipfile.ZipFile(staged, 'w') as jar:
            add(jar, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\nCreated-By: KingGrief build\r\n\r\n')
            entries = [(p.relative_to(classes).as_posix(), p.read_bytes()) for p in classes.rglob('*.class')]
            entries += [(p.relative_to(resources).as_posix(), p.read_bytes()) for p in resources.rglob('*') if p.is_file()]
            for entry, contents in sorted(entries):
                if entry.startswith(('org/bukkit/', 'net/md_5/', 'java/')): raise ValueError(f'Dependency leaked: {entry}')
                add(jar, entry, contents)
        with zipfile.ZipFile(staged) as jar:
            if jar.testzip() is not None or jar.read('plugin.yml').decode('utf-8') != metadata:
                raise ValueError(f'Invalid staged {name} JAR')
        public = ROOT.parent/'kinggrief/public'
        for target in (ROOT/f'{name}.jar', ROOT/'server/plugins'/f'{name}.jar', public/f'{name}.jar',
                       public/f'{name}-v{version}.jar'):
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(staged, target)
        print(f'{name} {version}: {staged.stat().st_size} bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api-classpath', required=True)
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--plugin', choices=('all', *PLUGINS), default='all')
    args = parser.parse_args()
    for name, directory in PLUGINS.items():
        if args.plugin in ('all', name): build(name, directory, args)


if __name__ == '__main__': main()
