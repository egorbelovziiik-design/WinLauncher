#!/usr/bin/env python3
"""Structural regression for the v7.4 blueprint (not a rendered client test)."""
import gzip
import math
import os
import struct
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load(path):
    data = gzip.decompress(Path(path).read_bytes())
    width, height, length, ox, _, oz = struct.unpack_from('>hhhhhh', data, 0)
    pos = 12
    n, = struct.unpack_from('>H', data, pos); pos += 2
    names = []
    for _ in range(n):
        size, = struct.unpack_from('>H', data, pos); pos += 2
        names.append(data[pos:pos + size].decode('utf8')); pos += size
    grid = bytearray()
    while True:
        value, count = struct.unpack_from('>BH', data, pos); pos += 3
        if not count: break
        grid.extend(bytes([value]) * count)
    assert len(grid) == width * height * length
    def block(x, y, z):
        if not (0 <= x+ox < width and 0 <= y-64 < height and 0 <= z+oz < length): return 'AIR'
        return names[grid[((x+ox)*height + y-64)*length + z+oz]]
    return block, grid, names


block, grid, names = load(os.environ.get('KINGGRIEF_V74_TEST_FILE', ROOT / 'core-src/src/main/resources/spawn_v74.dat'))
old, _, _ = load(ROOT / 'core-src/src/main/resources/spawn_v73.dat')
layout = []
for line in (ROOT / 'core-src/src/main/resources/parkour_v74.csv').read_text(encoding='ascii').splitlines():
    if line and not line.startswith('#'):
        i, x, y, z, half, kind = line.split(',')
        layout.append((int(i), int(x), int(y), int(z), int(half), kind))
assert len(layout) == 32 and [p[0] for p in layout] == list(range(32))
assert [(p[0],p[5]) for p in layout if p[5]!='step'] == [(0,'start'),(8,'checkpoint'),(16,'checkpoint'),(24,'checkpoint'),(31,'finish')]

# Distinct legs, 5-high torsos, 4-high heads, eyes and hands that actually
# support the next figure. The top glove touches (rather than replaces) the star.
assert old(0, 140, 0) == 'AIR' and block(0, 140, 0) == 'SPRUCE_WOOD'
assert block(2,146,0)=='OCHRE_FROGLIGHT' and block(3,146,0)=='WHITE_CONCRETE'
for x, y, coat, hair in ((16,101,'BLUE_CONCRETE','DARK_OAK_PLANKS'),
                         (14,115,'RED_CONCRETE','SPRUCE_PLANKS'),
                         (12,129,'LIGHT_BLUE_CONCRETE','DARK_OAK_PLANKS')):
    assert block(x,y,-2)=='DARK_OAK_PLANKS' and block(x,y,2)=='DARK_OAK_PLANKS'
    assert block(x,y+7,2)==coat
    assert block(x,y+13,0)==hair
    assert block(x+3,y+12,-1)==block(x+3,y+12,1)=='BLACK_CONCRETE'
assert block(16,114,-3)=='ORANGE_CONCRETE' and block(16,115,-3)=='DARK_OAK_PLANKS'
assert block(14,128,-3)=='ORANGE_CONCRETE' and block(14,129,-3)=='DARK_OAK_PLANKS'
# Flood fill checks six-face connected arm/hand, rather than a diagonal chain.
start=(12,138,-4); goal=(3,146,0); seen={start}; queue=deque(seen)
while queue and goal not in seen:
    x,y,z=queue.popleft()
    for dx,dy,dz in ((1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)):
        v=(x+dx,y+dy,z+dz)
        if not (3<=v[0]<=14 and 136<=v[1]<=146 and -5<=v[2]<=1) or v in seen: continue
        if block(*v) in {'LIGHT_BLUE_CONCRETE','WHITE_CONCRETE'}:
            seen.add(v); queue.append(v)
assert goal in seen, 'reaching arm is not connected to its glove'
assert sum(grid.count(names.index(m)) for m in ('SPRUCE_LEAVES','DARK_OAK_LEAVES')) > 3000

# All 32 landing centres have a 3x3 solid, head-clear area. All three CPs,
# start and finish have 3x3 real weighted plates and a full 5x5 floor. The
# nearest edges of consecutive islands are within a gentle jump/step distance.
non_solid = {'AIR','LIGHT_WEIGHTED_PRESSURE_PLATE','HEAVY_WEIGHTED_PRESSURE_PLATE','RED_CARPET',
             'WHITE_CARPET','YELLOW_CARPET','CYAN_CARPET','LEVER','END_ROD','COBWEB'}
for i,x,y,z,half,kind in layout:
    assert half == (1 if kind=='step' else 2)
    for dx in range(-1,2):
        for dz in range(-1,2):
            assert block(x+dx,y,z+dz) not in non_solid, ('floor',i,dx,dz)
            expected = ('HEAVY_WEIGHTED_PRESSURE_PLATE' if kind=='finish' else 'LIGHT_WEIGHTED_PRESSURE_PLATE') if kind!='step' else 'AIR'
            assert block(x+dx,y+1,z+dz)==expected, ('plate/headroom',i,dx,dz,block(x+dx,y+1,z+dz))
            assert block(x+dx,y+2,z+dz)=='AIR', ('headroom',i,dx,dz)
    if kind!='step':
        for dx in range(-2,3):
            for dz in range(-2,3): assert block(x+dx,y,z+dz) not in non_solid, ('broad island',i,dx,dz)
for a,b in zip(layout,layout[1:]):
    _,x,y,z,half,_=a; _,nx,ny,nz,n_half,_=b
    assert ny-y in (0,1)
    gap_x=max(0,abs(nx-x)-half-n_half-1);gap_z=max(0,abs(nz-z)-half-n_half-1)
    assert math.hypot(gap_x,gap_z)<=1.5, ('jump gap',a[0],b[0],gap_x,gap_z)

# Protect the functional palace, mine, PvP buildings and case terrace; these
# regions do not include the tree or the new parkour footprint.
for x1,x2,z1,z2,y1,y2 in [(-21,21,65,91,96,128),(-102,-69,-13,21,95,105),
                            (59,109,-3,43,95,105),(-45,45,-92,-48,95,106)]:
    for x in range(x1,x2+1,3):
        for z in range(z1,z2+1,3):
            for y in range(y1,y2+1,3):
                assert block(x,y,z)==old(x,y,z), ('architecture changed',x,y,z,old(x,y,z),block(x,y,z))
ice={'PACKED_ICE','BLUE_ICE'}
for t in range(-112,113,4):
    if abs(t)<=8: continue
    for x,z in ((118,t),(-118,t),(t,118),(t,-118)):
        for y in (100,120): assert block(x,y,z) in ice
for x,z in ((118,0),(-118,0),(0,118),(0,-118)):
    assert block(x,99,z)=='SNOW_BLOCK' and block(x,100,z)==block(x,101,z)=='AIR'
for x,z in ((123,0),(-123,0),(0,123),(0,-123)):
    assert block(x,100,z)==block(x,101,z)=='LIGHT_BLUE_STAINED_GLASS'

def stand(x,y,z):
    return block(x,y-1,z) not in non_solid and block(x,y,z) in non_solid and block(x,y+1,z) in non_solid
visited={(0,100,52)}; queue=deque(visited)
goals={(39,102,-28),(118,100,0),(-118,100,0),(0,100,118),(0,100,-118)}
while queue and not goals.issubset(visited):
    x,y,z=queue.popleft()
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
        nx,nz=x+dx,z+dz
        if not (-120<=nx<=120 and -120<=nz<=120): continue
        for dy in (0,1,-1):
            ny=y+dy; p=(nx,ny,nz)
            if p in visited or not stand(nx,ny,nz): continue
            if dy==1 and block(x,y+2,z) not in non_solid: continue
            if dy==-1 and block(nx,y+1,nz) not in non_solid: continue
            visited.add(p);queue.append(p)
assert goals.issubset(visited), ('unreachable start or gate',goals-visited)
print('SPAWN V74 BLUEPRINT: PASS (32 landings, five plate areas, connected arms/star, protected architecture and walkable start/gates)')
