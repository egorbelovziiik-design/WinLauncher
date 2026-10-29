#!/usr/bin/env python3
"""Verify corrupt and dangling-linked data.yml never becomes an empty economy."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import run as harness

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--paper',type=Path,required=True);p.add_argument('--vanilla',type=Path,required=True)
    p.add_argument('--core',type=Path,default=ROOT/'KingGriefCore.jar');p.add_argument('--java',default='java')
    p.add_argument('--accept-eula',action='store_true');args=p.parse_args()
    if not args.accept_eula:p.error('review https://aka.ms/MinecraftEULA and pass --accept-eula')
    cache_name=harness.validate_paper_and_vanilla(args.paper,args.vanilla)
    for scenario in ('dangling-symlink','malformed-yaml','unknown-trade-journal','unknown-order-journal'):
        with tempfile.TemporaryDirectory(prefix='kinggrief-data-failclosed-') as tmp:
            server=Path(tmp)/'server';data=server/'plugins/KingGriefCore';data.mkdir(parents=True);(server/'cache').mkdir()
            shutil.copy2(args.vanilla,server/'cache'/cache_name);shutil.copy2(args.core,server/'plugins/KingGriefCore.jar')
            (server/'eula.txt').write_text('eula=true\n',encoding='utf8')
            port=harness.available_port()
            (server/'server.properties').write_text(f'online-mode=false\nserver-ip=127.0.0.1\nserver-port={port}\nview-distance=2\nsimulation-distance=3\nallow-nether=false\nlevel-type=minecraft:flat\ngenerate-structures=false\n',encoding='utf8')
            file=data/'data.yml';target=data/'important-original.yml';bad=b'players:\n  foo: [unterminated\n'
            if scenario=='dangling-symlink':
                file.symlink_to(target.name);expected='[DataStore] Cannot read data.yml; stopping without replacing player data'
            elif scenario=='malformed-yaml':
                file.write_bytes(bad);expected='[DataStore] Cannot read data.yml; stopping without replacing player data'
            else:
                trade=scenario=='unknown-trade-journal'
                pending=data/('safe-trades/pending' if trade else 'buy-orders/pending');pending.mkdir(parents=True)
                file=pending/('unidentifiable.trade' if trade else 'unidentifiable.order');bad=b'broken journal, preserve for investigation'
                file.write_bytes(bad)
                expected=('[Trade] Journal participants cannot be identified; stopping to protect inventories.' if trade
                          else '[BuyOrders] Cannot identify affected account; stopping to protect inventories.')
            result=subprocess.run([args.java,'-Xms512M','-Xmx1536M','-jar',str(args.paper.resolve()),'nogui'],cwd=server,
                                  stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=240)
            if expected not in result.stdout:
                print('\n'.join(result.stdout.splitlines()[-150:]));raise RuntimeError(scenario+' did not fail closed')
            if scenario=='dangling-symlink':
                if not file.is_symlink() or target.exists():raise RuntimeError('dangling link was replaced or target created')
            elif file.read_bytes()!=bad:raise RuntimeError(scenario+' was overwritten')
            print('DATA FAIL-CLOSED PASS:',scenario,flush=True)
    print('DATA FAIL-CLOSED INTEGRATION: PASS',flush=True)

if __name__=='__main__':main()
