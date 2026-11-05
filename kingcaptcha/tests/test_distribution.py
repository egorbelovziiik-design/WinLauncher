#!/usr/bin/env python3
"""Check the exact flat update ZIP and Java 21 plugins, not just source files."""
import argparse, hashlib
from pathlib import Path
from zipfile import ZipFile

p=argparse.ArgumentParser();p.add_argument('directory',type=Path);a=p.parse_args()
d=a.directory
with ZipFile(d/'KingHub-Captcha-1.0.0-update.zip') as archive:
    assert set(archive.namelist())=={'KingHub.jar','KingCaptcha.jar','ПРОЧИТАЙ_СНАЧАЛА.md'}
    assert archive.testzip() is None
    for name in ('KingHub.jar','KingCaptcha.jar'):assert archive.read(name)==(d/name).read_bytes()
    text=archive.read('ПРОЧИТАЙ_СНАЧАЛА.md').decode('utf8')
    assert 'отдельного хаба' in text and 'не для сервера грифа' in text and 'auth.yml' in text
for name,version,main in [('KingHub','1.2.4','net/kinggrief/hub/KingHubPlugin.class'),('KingCaptcha','1.0.0','net/kinggrief/captcha/KingCaptchaPlugin.class')]:
    with ZipFile(d/(name+'.jar')) as jar:
        assert jar.testzip() is None
        assert 'version: '+version in jar.read('plugin.yml').decode()
        classfile=jar.read(main)
        assert classfile[:4]==b'\xca\xfe\xba\xbe' and int.from_bytes(classfile[6:8],'big')==65
        assert not any(x.startswith(('org/bukkit/','io/papermc/','java/')) for x in jar.namelist())
    print(name,version,'SHA256',hashlib.sha256((d/(name+'.jar')).read_bytes()).hexdigest())
print('UPDATE ZIP SHA256',hashlib.sha256((d/'KingHub-Captcha-1.0.0-update.zip').read_bytes()).hexdigest())
print('DISTRIBUTION PASS')
