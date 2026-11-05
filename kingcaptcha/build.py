#!/usr/bin/env python3
"""Build exactly the Hub and Captcha release JARs, without changing server data.

Supply a Paper 1.21.4 API/dependency classpath and Java 21 javac. Outputs are
isolated; nothing is copied into the grief server, a world, or plugins/update/.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent


def entry(archive, name, data):
    info = ZipInfo(name, (1980, 1, 1, 0, 0, 0))
    info.external_attr = 0o100644 << 16
    archive.writestr(info, data, compress_type=ZIP_DEFLATED, compresslevel=9)


def build_plugin(name, roots, resource_dir, output_dir, args):
    with tempfile.TemporaryDirectory(prefix='kingcaptcha-build-') as temp:
        classes = Path(temp) / 'classes'
        classes.mkdir()
        sources = sorted(path for root in roots for path in root.rglob('*.java'))
        if not sources: raise RuntimeError('No Java sources for ' + name)
        subprocess.run([args.javac, '-encoding', 'UTF-8', '--release', '21', '-classpath', args.api_classpath,
                        '-d', str(classes), *map(str, sources)], check=True)
        output = output_dir / (name + '.jar')
        entries = [(p.relative_to(classes).as_posix(), p.read_bytes()) for p in classes.rglob('*.class')]
        entries += [(p.relative_to(resource_dir).as_posix(), p.read_bytes()) for p in resource_dir.rglob('*') if p.is_file()]
        with ZipFile(output, 'w') as archive:
            entry(archive, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\nCreated-By: KingGrief\r\n\r\n')
            for path, data in sorted(entries):
                if path.startswith(('org/bukkit/', 'io/papermc/', 'java/')):
                    raise ValueError('Dependency leaked into plugin: ' + path)
                entry(archive, path, data)
        print('Built', output, output.stat().st_size, 'bytes', flush=True)
        return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api-classpath', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    captcha = build_plugin('KingCaptcha', [HERE/'src/main/java'], HERE/'src/main/resources', args.output_dir, args)
    hub = build_plugin('KingHub', [ROOT/'rw-newyear-assembly/kinghub-src/src/main/java',
                                   ROOT/'rw-newyear-assembly/common-src/src/main/java'],
                       ROOT/'rw-newyear-assembly/kinghub-src/src/main/resources', args.output_dir, args)
    # Flat-root update archive for an EXISTING hub; no worlds or configs included.
    with ZipFile(args.output_dir/'KingHub-Captcha-1.0.0-update.zip', 'w') as archive:
        entry(archive, 'KingCaptcha.jar', captcha.read_bytes())
        entry(archive, 'KingHub.jar', hub.read_bytes())
        entry(archive, 'ПРОЧИТАЙ_СНАЧАЛА.md', (HERE/'ИНСТРУКЦИЯ.md').read_bytes())


if __name__ == '__main__': main()
