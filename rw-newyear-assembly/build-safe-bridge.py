#!/usr/bin/env python3
"""Replace the public example secret in the unsigned Bridge JAR, preserving its classes.

This is a resources-only 1.4.1 patch of the shipped 1.4.0. Existing external
server configurations are not touched. Rotate any secret used with old archives.
"""
from pathlib import Path
import hashlib
import re
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parent
SOURCE = ROOT / 'server/plugins/KingGriefBridge.jar'
COPIES = [SOURCE, ROOT / 'plugins/KingGriefBridge.jar',
          REPO / 'kinggrief/minecraft-plugin/KingGriefBridge.jar',
          REPO / 'kinggrief/public/KingGriefBridge.jar',
          REPO / 'kinggrief/public/KingGriefBridge-v1.4.1.jar']
EXPECTED_OLD_SHA256 = '6e08c254c6dc71bdf7da6471e0f0842a7934e79f77b3476204e91e2e509b38a8'


def main():
    original = SOURCE.read_bytes()
    with zipfile.ZipFile(SOURCE) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)) or jar.testzip():
            raise ValueError('Bridge JAR has duplicate entries or is corrupt')
        if any(x.upper().startswith('META-INF/') and x.upper().endswith(('.SF', '.RSA', '.DSA', '.EC')) for x in names):
            raise ValueError('Refusing to alter a signed JAR')
        plugin = jar.read('plugin.yml').decode('utf8')
        old_version = bool(re.search(r'(?m)^version:\s*1\.4\.0\s*$', plugin))
        new_version = bool(re.search(r'(?m)^version:\s*1\.4\.1\s*$', plugin))
        if old_version and hashlib.sha256(original).hexdigest() != EXPECTED_OLD_SHA256:
            raise ValueError('Unexpected old Bridge JAR; refusing to patch')
        if not (old_version or new_version):
            raise ValueError('Unexpected Bridge version')
        entries = [(entry, jar.read(entry)) for entry in jar.infolist()]
    import io
    out = io.BytesIO()
    with zipfile.ZipFile(out, 'w') as jar:
        for entry, data in entries:
            if entry.filename == 'config.yml':
                text, count = re.subn(r'(?m)^secret:\s*.*$', 'secret: "CHANGE_ME"', data.decode('utf8'))
                if count != 1: raise ValueError('Cannot identify the Bridge example secret')
                data = text.encode('utf8')
            elif entry.filename == 'plugin.yml':
                text = data.decode('utf8')
                if old_version: text = re.sub(r'(?m)^version:\s*1\.4\.0\s*$', 'version: 1.4.1', text)
                data = text.encode('utf8')
            jar.writestr(entry, data)
    data = out.getvalue()
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        if jar.testzip() or not re.search(r'(?m)^secret:\s*["\']?CHANGE_ME["\']?\s*$', jar.read('config.yml').decode('utf8')):
            raise ValueError('Safe Bridge validation failed')
        if not re.search(r'(?m)^version:\s*1\.4\.1\s*$', jar.read('plugin.yml').decode('utf8')):
            raise ValueError('Safe Bridge version mismatch')
    for path in COPIES:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    print('KingGriefBridge 1.4.1: example secret removed; SHA-256', hashlib.sha256(data).hexdigest())

if __name__ == '__main__':
    main()
