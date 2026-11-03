#!/usr/bin/env python3
"""Verify the actual 7.9.0 server, update and standalone artifacts agree."""
import hashlib
import io
from pathlib import Path
import re
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
PUBLIC = ROOT.parent / 'kinggrief/public'


def sha(data): return hashlib.sha256(data).hexdigest()


class Distribution(unittest.TestCase):
    def test_jar_and_full_server(self):
        core = (ROOT / 'KingGriefCore.jar').read_bytes()
        for p in (ROOT/'plugins/KingGriefCore.jar', ROOT/'server/plugins/KingGriefCore.jar',
                  PUBLIC/'KingGriefCore.jar', PUBLIC/'KingGriefCore-v7.9.0.jar'):
            self.assertEqual(p.read_bytes(), core, str(p))
        with ZipFile(io.BytesIO(core)) as jar:
            self.assertIsNone(jar.testzip())
            metadata = jar.read('plugin.yml').decode('utf8')
            self.assertRegex(metadata, r'(?m)^version:\s*7\.9\.0\s*$')
            for name in ('elka','wheel','kgtree','parkour','pet','cosmetic','wintershow','winterfish',
                         'snowmemory','frostforge','achievements','gamevote','buildcontest',
                         'calendar','trainstats','autosell','kgbackup','minestats','deathpoint',
                         'sortinv','compare','way','kgstatus','rg','ah','clan'):
                self.assertRegex(metadata, rf'(?m)^  {name}:\s*$', name)
            self.assertRegex(metadata, r'(?m)^  pet:\s*\n\s+aliases:\s*\[pets\]')
            self.assertRegex(metadata, r'(?m)^  kgtree:\s*\n\s+aliases:\s*\[tree\]')
            for manager in ('SpawnTreeModel','FortuneWheelManager','WinterTreeManager','TreeSeasonStore','FrostForgeManager',
                            'MemoryGameManager','AutoBuyerManager','MiniGameVoteManager','PendingBanJournal','LegacySpawnGuard','CommandSenderPolicy','SpawnWorldGuard','DisplayPetManager'):
                self.assertIn('net/kinggrief/core/'+manager+'.class', jar.namelist())
            self.assertIn('elka_v77.csv', jar.namelist())
            expected=(ROOT/'core-src/src/main/resources/spawn_v77_template.sha256').read_text().strip()
            self.assertEqual(sha(jar.read('spawn_v77_template.zip')), expected)
            self.assertEqual(expected, 'dacec177a987bfd74b80e63faa2e18f446e4577f9d6a071badd382061b705118')
        full = ROOT/'rw-newyear-full-assembly-v7.9.0.zip'
        self.assertEqual(full.read_bytes(), (PUBLIC/full.name).read_bytes())
        with ZipFile(full) as z:
            self.assertIsNone(z.testzip())
            names=z.namelist()
            self.assertEqual(len(names),len(set(names)))
            self.assertEqual(z.read('plugins/KingGriefCore.jar'), core)
            self.assertEqual(sha(z.read('plugins/KingGriefCore/spawn-v77-template.zip')), expected)
            self.assertEqual(z.read('resourcepack/kinggrief-mega-rp-v72.zip'), (PUBLIC/'kinggrief-mega-rp-v72.zip').read_bytes())
            self.assertIn('НОВЫЕ_ФУНКЦИИ_7.5.0.md', names)
            self.assertIn('ОБНОВЛЕНИЕ_7.9.0.md', names)
            self.assertNotIn('ОБНОВЛЕНИЕ_7.5.0.md', names)
            self.assertNotIn('ОБНОВЛЕНИЕ_7.6.0.md', names)
            self.assertNotIn('ОБНОВЛЕНИЕ_7.7.0.md', names)
            self.assertNotIn('ОБНОВЛЕНИЕ_7.8.0.md', names)
            self.assertNotIn('plugins/KingGriefCore/spawn-v76-template.zip', names)
            self.assertIn('ТЕХ.АДМИН', z.read('НОВЫЕ_ФУНКЦИИ_7.5.0.md').decode('utf8'))
            self.assertNotIn('plugins/KingHub.jar', names)
            for name in names:
                self.assertFalse(name.startswith(('/', 'world/', 'world_nether/', 'world_the_end/', 'spawn_world/')), name)
                self.assertNotIn('..', Path(name).parts, name)
                self.assertNotEqual(name, 'plugins/KingGriefCore/data.yml')
            bridge = z.read('plugins/KingGriefBridge.jar')
            with ZipFile(io.BytesIO(bridge)) as jar:
                self.assertIsNone(jar.testzip())
                self.assertRegex(jar.read('plugin.yml').decode('utf8'), r'(?m)^version:\s*1\.4\.1\s*$')
                self.assertRegex(jar.read('config.yml').decode('utf8'), r'(?m)^secret:\s*["\']?CHANGE_ME["\']?\s*$')
            self.assertEqual(bridge, (PUBLIC/'KingGriefBridge-v1.4.1.jar').read_bytes())
            self.assertRegex(z.read('plugins/KingGriefBridge/config.yml').decode('utf8'), r'(?m)^secret:\s*["\']?CHANGE_ME["\']?\s*$')
            connect=z.read('plugins/KingConnect/config.yml').decode('utf8')
            self.assertRegex(connect, r'(?m)^secret-key:\s*["\']?CHANGE_ME["\']?\s*$')
            self.assertRegex(connect, r'(?m)^cloud-sync:\s*\n\s*enabled:\s*false\s*$')
            connect_jar=z.read('plugins/KingConnect.jar')
            for path in (ROOT/'KingConnect.jar', ROOT/'plugins/KingConnect.jar', PUBLIC/'KingConnect.jar', PUBLIC/'KingConnect-v2.3.1.jar'):
                self.assertEqual(path.read_bytes(), connect_jar, str(path))
            with ZipFile(io.BytesIO(connect_jar)) as jar:
                self.assertIsNone(jar.testzip())
                self.assertRegex(jar.read('plugin.yml').decode('utf8'), r'(?m)^version:\s*2\.3\.1\s*$')
                embedded=jar.read('config.yml').decode('utf8')
                self.assertRegex(embedded, r'(?m)^secret-key:\s*["\']?CHANGE_ME["\']?\s*$')
                self.assertRegex(embedded, r'(?m)^cloud-sync:\s*\n\s*enabled:\s*false\s*$')

    def test_update_is_not_a_server_overlay(self):
        core = (PUBLIC/'KingGriefCore-v7.9.0.jar').read_bytes()
        patch = ROOT/'KingGriefCore-7.9.0-update.zip'
        self.assertEqual(patch.read_bytes(),(PUBLIC/patch.name).read_bytes())
        with ZipFile(patch) as z:
            self.assertIsNone(z.testzip())
            self.assertEqual(set(z.namelist()),{'ПРОЧИТАЙ_СНАЧАЛА.md','KingGriefCore.jar','KingAC.jar','KingClear.jar','KingGriefBridge.jar','KingConnect.jar'})
            self.assertEqual(z.read('KingGriefCore.jar'),core)
            instructions=z.read('ПРОЧИТАЙ_СНАЧАЛА.md').decode('utf8')
            self.assertIn(sha(core),instructions)
            self.assertNotIn('{{',instructions)
            self.assertIn('plugins/update/',instructions)
            for name in ('KingAC','KingClear','KingGriefBridge','KingConnect'):
                self.assertIn(sha(z.read(name+'.jar')), instructions)
            self.assertEqual(z.read('KingConnect.jar'),(PUBLIC/'KingConnect-v2.3.1.jar').read_bytes())
        self.assertEqual(sha((PUBLIC/'KingGriefCore-v7.8.0.jar').read_bytes()),
                         'f1b9e9b96932ca50abc573d3924f5a86aaffa06c20119fc60c2169bb0e9ee4a2')
        self.assertEqual(sha((PUBLIC/'KingGriefCore-v7.7.0.jar').read_bytes()),
                         '3bb83146b1a2a46f78123f6bad140723220b572deda510c6341683c7dd7812e9')
        self.assertEqual(sha((PUBLIC/'KingGriefCore-v7.5.0.jar').read_bytes()),
                         'a9bf93949df8a915a1d4d54697be3df6bf6ac820830425d6335ce98cab65196f')
        self.assertEqual(sha((PUBLIC/'KingGriefCore-v7.4.0.jar').read_bytes()),
                         '373e615f1c406b9f508e543eb7cc3203599320682d655667765abfda825f488d')

if __name__ == '__main__': unittest.main(verbosity=2)
