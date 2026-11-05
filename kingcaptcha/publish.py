#!/usr/bin/env python3
"""Copy only the already-verified hub release; never touch grief plugins/worlds."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parent.parent
HERE=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('release',type=Path);args=p.parse_args();release=args.release.resolve()
subprocess.run(['python3',str(HERE/'tests/test_distribution.py'),str(release)],check=True)
report=(release/'paper-report.txt').read_text(encoding='utf8')
dist=(release/'distribution-report.txt').read_text(encoding='utf8')
for marker in ('PAPER PHASE hub-only PASS','PAPER PHASE combined PASS','KINGCAPTCHA PAPER INTEGRATION: PASS',
               'CAPTCHA_CLIENT PASS','signed SSO authenticates','real NPC click opens selector',
               'installed but disabled captcha fails closed'):
    if marker not in report: raise RuntimeError('Missing verification: '+marker)
if 'RESULT FAIL' in report or 'CAPTCHA_CLIENT FAIL' in report or 'DISTRIBUTION PASS' not in dist:
    raise RuntimeError('A test report contains failures')

copies={
    'KingCaptcha.jar':['rw-newyear-assembly/KingCaptcha.jar','kinggrief/public/KingCaptcha.jar','kinggrief/public/KingCaptcha-v1.0.0.jar'],
    'KingHub.jar':['rw-newyear-assembly/KingHub.jar','rw-newyear-assembly/KingHub-v1.2.4.jar','kinggrief/public/KingHub.jar','kinggrief/public/KingHub-v1.2.4.jar'],
    'KingHub-Captcha-1.0.0-update.zip':['rw-newyear-assembly/KingHub-Captcha-1.0.0-update.zip','kinggrief/public/KingHub-Captcha-1.0.0-update.zip'],
}
for source,destinations in copies.items():
    for dest in destinations:
        destination=ROOT/dest; destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(release/source,destination)
        if destination.read_bytes()!=(release/source).read_bytes():raise RuntimeError('Copy changed: '+dest)
shutil.copyfile(HERE/'ИНСТРУКЦИЯ.md',ROOT/'rw-newyear-assembly/ОБНОВЛЕНИЕ_ХАБА_КАПЧА_1.0.0.md')
shutil.copyfile(HERE/'ИНСТРУКЦИЯ.md',ROOT/'kinggrief/public/KingHub-Captcha-1.0.0-README.md')
sha=lambda name:hashlib.sha256((release/name).read_bytes()).hexdigest()
run='https://github.com/'+os.environ['GITHUB_REPOSITORY']+'/actions/runs/'+os.environ['GITHUB_RUN_ID']
text=('# Проверки KingCaptcha 1.0.0 / KingHub 1.2.4\n\n'
      'Изолированные серверы Paper 1.21.4 build 232 и Java 21. Проверены Hub отдельно и вместе с капчей; '
      'использованы протокольные клиенты minecraft-protocol и синтетический подписанный SSO-cookie. '
      'Реальные миры и данные не использовались. Графический клиент, настоящий Velocity и второй сервер грифа не тестировались. '
      'Сетевой пакет движения не создал событие на тестовом клиенте; ограничение движения проверено отдельно вызванным Paper-событием.\n\n'
      '- Проверенный KingCaptcha.jar SHA-256: `'+sha('KingCaptcha.jar')+'`\n'
      '- Проверенный KingHub.jar SHA-256: `'+sha('KingHub.jar')+'`\n'
      '- Архив обновления SHA-256: `'+sha('KingHub-Captcha-1.0.0-update.zip')+'`\n'
      '- GitHub Actions: '+run+'\n\n'
      '```\n'+dist+'\n'+report+'\n```\n')
(ROOT/'rw-newyear-assembly/ПРОВЕРКИ_ХАБА_КАПЧА_1.0.0.md').write_text(text,encoding='utf8')
print('Verified hub release copied, CI:',run,flush=True)
