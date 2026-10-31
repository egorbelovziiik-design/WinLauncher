#!/usr/bin/env python3
"""Extend the immutable v70 pack with 50 original animated artifact textures.

Requires Pillow. Builds a new versioned ZIP; never overwrites the old pack.
"""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import shutil
import zipfile
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
OLD = ROOT / 'kinggrief-mega-rp-v70.zip'
OUTPUT = ROOT / 'kinggrief-mega-rp-v72.zip'
MANIFEST = HERE / 'item-model-map-v72.json'
EXPECTED_OLD_SHA1 = '8286228d4a1e0a95f56ad5fd265f12d1a57827be'

# Ten distinct utility families. Five ranks have different numbers of pips,
# colours and sparkles, so all 50 textures are independently identifiable.
PALETTES = [
    ('#42142b','#d84072','#ffd1e2'), ('#543314','#cf9143','#fff0b4'),
    ('#173d2a','#46bc6a','#dbffd4'), ('#30234e','#9177d4','#eee0ff'),
    ('#531c14','#eb6d32','#ffe0a3'), ('#103f54','#27aeca','#d6f9ff'),
    ('#133b4c','#51c7df','#e1fbff'), ('#4c2020','#d25050','#ffe0b5'),
    ('#353020','#c6a652','#fff3c4'), ('#343056','#9d9df5','#f3efff'),
]


def jbytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def texture(group, tier):
    dark, base, highlight = PALETTES[group]
    strip = Image.new('RGBA', (32, 32 * 12), (0, 0, 0, 0))
    for frame in range(12):
        im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        # Frosted bevelled medallion with a dark, opaque outline.
        d.polygon([(16,1),(25,4),(30,12),(28,23),(21,29),(11,29),(4,23),(2,12),(7,4)],fill='#11131c')
        d.polygon([(16,3),(24,6),(28,12),(26,22),(20,27),(12,27),(6,22),(4,12),(8,6)],fill=dark)
        d.polygon([(16,5),(23,8),(26,13),(24,21),(19,25),(13,25),(8,21),(6,13),(9,8)],fill=base)
        d.line([(9,8),(16,5),(23,8)],fill=highlight,width=1)
        ink = '#fff7e5'; shadow = '#20202d'
        # Purpose-specific symbols: heart, food, leaf/antidote, ward,
        # flame, droplet, wing, sword, pickaxe, and eye.
        if group == 0:
            d.polygon([(16,22),(9,16),(9,12),(11,10),(14,10),(16,12),(18,10),(21,10),(23,12),(23,16)],fill=ink,outline=shadow)
            d.rectangle((15,12,17,19),fill=base);d.rectangle((13,14,19,16),fill=base)
        elif group == 1:
            d.rounded_rectangle((9,11,23,21),radius=4,fill=ink,outline=shadow,width=1)
            for x in (12,16,20): d.line((x,13,x+1,15),fill=base,width=1)
        elif group == 2:
            d.polygon([(10,21),(10,15),(13,10),(22,9),(22,16),(18,21)],fill=ink,outline=shadow)
            d.line((11,20,20,11),fill=base,width=2);d.line((14,16,18,17),fill=base,width=1)
        elif group == 3:
            d.polygon([(9,10),(16,8),(23,10),(22,18),(16,23),(10,18)],fill=ink,outline=shadow)
            d.line((12,15,15,18,20,12),fill=base,width=2)
        elif group == 4:
            d.polygon([(16,23),(11,20),(10,16),(13,12),(14,15),(17,9),(21,13),(22,18),(20,22)],fill=ink,outline=shadow)
            d.polygon([(16,21),(14,18),(16,14),(19,18),(19,20)],fill=base)
        elif group == 5:
            d.polygon([(16,8),(10,16),(10,19),(12,22),(20,22),(22,19),(22,16)],fill=ink,outline=shadow)
            d.line((12,18,13,20,16,21),fill=base,width=1)
        elif group == 6:
            d.polygon([(9,12),(22,9),(20,14),(23,14),(19,18),(21,18),(16,22),(11,20)],fill=ink,outline=shadow)
            d.line((11,19,19,13),fill=base,width=1)
        elif group == 7:
            d.polygon([(20,8),(23,10),(17,17),(18,19),(16,21),(14,19),(11,22),(10,21),(13,18),(11,16),(13,14),(15,15)],fill=ink,outline=shadow)
        elif group == 8:
            d.polygon([(9,12),(13,9),(18,9),(23,12),(22,14),(18,12),(16,13),(20,20),(18,22),(13,14),(10,15)],fill=ink,outline=shadow)
        else:
            d.polygon([(8,16),(12,12),(16,11),(20,12),(24,16),(20,20),(16,21),(12,20)],fill=ink,outline=shadow)
            d.ellipse((13,13,19,19),fill=base,outline=shadow);d.point((16,15),fill=ink)
        # Rank pips and travelling highlight.
        for i in range(tier + 1): d.rectangle((8 + i*3,25,9 + i*3,26),fill=highlight)
        pos = frame % 8
        sx, sy = [(8,6),(12,5),(17,5),(22,7),(26,12),(25,19),(21,24),(7,19)][pos]
        d.point((sx,sy),fill='#ffffff')
        strip.alpha_composite(im,(0,frame*32))
    out=io.BytesIO();strip.save(out,format='PNG',optimize=True);return out.getvalue()


def main():
    if hashlib.sha1(OLD.read_bytes()).hexdigest() != EXPECTED_OLD_SHA1:
        raise ValueError('v70 source pack has changed; refusing to build')
    with zipfile.ZipFile(OLD) as z:
        if z.testzip() or len(z.namelist()) != len(set(z.namelist())): raise ValueError('invalid source ZIP')
        entries = {name:z.read(name) for name in z.namelist() if not name.endswith('/')}
    mapping=json.loads((HERE/'item-model-map-v70.json').read_text(encoding='utf-8'))
    extras=[[3000+n,f'winter_rune_{n:03d}'] for n in range(101,151)]
    mapping['AMETHYST_SHARD'].extend(extras)
    MANIFEST.write_bytes(jbytes(mapping))

    legacy_path='assets/minecraft/models/item/amethyst_shard.json'
    modern_path='assets/minecraft/items/amethyst_shard.json'
    legacy=json.loads(entries[legacy_path]);modern=json.loads(entries[modern_path])
    modern_entries=modern['model']['entries']
    # v70's 3101 fallback is now occupied by the first new artifact.
    modern_entries[:]=[entry for entry in modern_entries if entry['threshold'] != 3101]
    template=json.loads(entries['assets/kinggrief/models/item/winter_rune_001.json'])
    seen=set()
    for n in range(101,151):
        iid=f'winter_rune_{n:03d}';cmd=3000+n
        raw=texture((n-101)//5,(n-101)%5);digest=hashlib.sha256(raw).digest()
        if digest in seen: raise AssertionError('duplicate artifact texture')
        seen.add(digest)
        entries[f'assets/kinggrief/textures/item/{iid}.png']=raw
        entries[f'assets/kinggrief/textures/item/{iid}.png.mcmeta']=jbytes({'animation':{'frametime':2}})
        model=dict(template);model['textures']={'layer0':f'kinggrief:item/{iid}'}
        entries[f'assets/kinggrief/models/item/{iid}.json']=jbytes(model)
        direct={'type':'minecraft:model','model':f'kinggrief:item/{iid}'}
        entries[f'assets/kinggrief/items/{iid}.json']=jbytes({'model':direct})
        legacy['overrides'].append({'predicate':{'custom_model_data':cmd},'model':f'kinggrief:item/{iid}'})
        modern_entries.append({'threshold':cmd,'model':direct})
    # Stop legacy >= comparisons from turning unrelated larger CMD values
    # into item 150. A separate vanilla-textured model avoids self-reference.
    entries['assets/kinggrief/models/item/vanilla_amethyst_shard.json'] = jbytes({
        'parent': 'minecraft:item/generated', 'textures': {'layer0': 'minecraft:item/amethyst_shard'}
    })
    legacy['overrides'].append({'predicate': {'custom_model_data': 3151}, 'model': 'kinggrief:item/vanilla_amethyst_shard'})
    modern_entries.append({'threshold':3151,'model':modern['model']['fallback']})
    modern_entries.sort(key=lambda e:e['threshold'])
    if len({entry['threshold'] for entry in modern_entries}) != len(modern_entries): raise AssertionError('duplicate thresholds')
    entries[legacy_path]=jbytes(legacy);entries[modern_path]=jbytes(modern)
    meta=json.loads(entries['pack.mcmeta']);meta['pack']['description']='§b§l❄ §c§lKINGGRIEF v7.2 §8· §f150 артефактов, мега-гепль и шары'
    entries['pack.mcmeta']=jbytes(meta)
    with zipfile.ZipFile(OUTPUT,'w') as z:
        for name,data in sorted(entries.items()):
            zi=zipfile.ZipInfo(name,(1980,1,1,0,0,0));zi.external_attr=0o100644<<16;zi.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(zi,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(OUTPUT) as z:
        if z.testzip():raise ValueError('corrupt output ZIP')
    for dest in (ROOT/'server/resourcepack'/OUTPUT.name,ROOT.parent/'kinggrief/public'/OUTPUT.name):
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(OUTPUT,dest)
    print(f'{OUTPUT.name}: {OUTPUT.stat().st_size} bytes, SHA-1 {hashlib.sha1(OUTPUT.read_bytes()).hexdigest()}, SHA-256 {hashlib.sha256(OUTPUT.read_bytes()).hexdigest()}')

if __name__=='__main__':main()
