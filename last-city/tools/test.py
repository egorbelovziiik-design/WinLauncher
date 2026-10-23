#!/usr/bin/env python3
"""Compile, analyze, execute genuine domain + mock adapter tests, build and inspect."""
from pathlib import Path
import hashlib, json, os, re, subprocess, sys
from mock_tests import build_harness
ROOT=Path(__file__).resolve().parents[2]
GAME=ROOT/'last-city'
OUT=ROOT/'release-artifacts/last-city'
OUT.mkdir(parents=True,exist_ok=True)
LUAU=Path(os.environ.get('LUAU',ROOT/'.local/bin/luau'))
COMPILER=Path(os.environ.get('LUAU_COMPILE',ROOT/'.local/bin/luau-compile'))
ANALYZER=Path(os.environ.get('LUAU_ANALYZE',ROOT/'.local/bin/luau-analyze'))
logs=[]
def run(args):
    result=subprocess.run([str(v) for v in args],cwd=ROOT,capture_output=True,text=True)
    output=result.stdout+result.stderr
    logs.append('$ '+' '.join(map(str,args))+'\n'+output)
    print(output,end='')
    if result.returncode:
        (OUT/'automated-tests.txt').write_text('\n'.join(logs))
        raise SystemExit(result.returncode)
    return output
for tool in [LUAU,COMPILER,ANALYZER]:
    if not tool.is_file(): sys.exit('Install pinned Luau first: bash last-city/tools/install-toolchain.sh')
sources=sorted((GAME/'src').rglob('*.luau'))
for source in sources:
    run([COMPILER,'--null',source])
print(f'PASSED | COMPILE {len(sources)} Luau source files')
analysis=run([ANALYZER,*sources])
# Luau CLI has no Roblox userdata type registry; Vector3 typeof is the sole known lint.
known="UnknownType: Unknown type 'Vector3'"
for line in analysis.splitlines():
    if line and known not in line: sys.exit('Unexpected analyzer diagnostic: '+line)
roads=run(['node','--test',GAME/'tests/roads.spec.mjs'])
serialization=run(['node','--test',GAME/'tests/serialization.spec.mjs'])
domain=run([LUAU,GAME/'tests/domain.spec.luau'])
adapters=run([LUAU,build_harness()])
run(['node',GAME/'tools/build.mjs'])
run(['node',GAME/'tools/verify.mjs'])
artifact=OUT/'LAST_CITY_3D.rbxl'
first=artifact.read_bytes()
run(['node',GAME/'tools/build.mjs'])
assert artifact.read_bytes()==first,'Non-deterministic place build'
print('PASSED | BUILD deterministic identical binary across two builds')
summary={
    'version':'0.7.0','serialization':{'status':'PASSED','count':int(re.search(r'# pass (\d+)',serialization)[1])},
    'previous_studio_import':{'version':'0.1.0','status':'FAILED','evidence':'User screenshot: Error while decoding data: unexpected end at offset 2'},
    'studio_import':{'version':'0.7.0','status':'NOT TESTED'},
    'studio_import_0_6_0':{'status':'FAILED','evidence':'User screenshot: menu loaded, but Play repeatedly returned the server character-loading notice'},
    'studio_import_0_5_0':{'status':'NOT TESTED'},
    'studio_import_0_4_0':{'status':'NOT TESTED'},
    'studio_import_0_3_0':{'status':'PASSED','evidence':'User screenshots show the released scene running in Studio with an E dialogue prompt; the user also reported visual/runtime defects'},
    'studio_import_0_2_0':{'status':'PASSED','evidence':'User screenshots show the released scene open and rendered in Studio'},
    'studio_import_0_1_1':{'status':'PASSED','evidence':'User screenshots show loading GUI and editable 3D scene; gameplay startup was not confirmed'},
    'roads':{'status':'PASSED','count':int(re.search(r'# pass (\d+)',roads)[1])},
    'luau_version':'0.743','source_compilation':{'status':'PASSED','count':len(sources)},
    'domain':{'status':'PASSED','count':int(re.search(r'DOMAIN RESULTS: (\d+) PASSED',domain)[1])},
    'mock_adapters':{'status':'PASSED','count':int(re.search(r'ADAPTER MOCK RESULTS: (\d+) PASSED',adapters)[1]),'scope':'API doubles; NOT Roblox Studio'},
    'binary':json.loads((OUT/'verification.json').read_text()),
    'deterministic_build':'PASSED','roblox_studio':'NOT TESTED','live_datastore':'NOT TESTED','live_multiplayer':'NOT TESTED',
    'known_cli_diagnostic':"Vector3 userdata type unavailable to standalone Luau analyzer; Roblox adapter syntax compiles",
}
(OUT/'test-results.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
(OUT/'automated-tests.txt').write_text('\n'.join(logs))
(OUT/'SHA256SUMS').write_text(hashlib.sha256(first).hexdigest()+'  LAST_CITY_3D.rbxl\n')
print('\nALL AUTOMATED CHECKS PASSED. Roblox Studio remains NOT TESTED.')
