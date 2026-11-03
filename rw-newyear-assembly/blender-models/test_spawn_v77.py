#!/usr/bin/env python3
"""Structural regression for the v7.7 blueprint (not a rendered client test)."""
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


block, grid, names = load(os.environ.get('KINGGRIEF_V77_TEST_FILE', ROOT / 'core-src/src/main/resources/spawn_v77.dat'))
old, _, _ = load(ROOT / 'core-src/src/main/resources/spawn_v73.dat')
layout = []
for line in (ROOT / 'core-src/src/main/resources/parkour_v74.csv').read_text(encoding='ascii').splitlines():
    if line and not line.startswith('#'):
        i, x, y, z, half, kind = line.split(',')
        layout.append((int(i), int(x), int(y), int(z), int(half), kind))
assert len(layout) == 32 and [p[0] for p in layout] == list(range(32))
assert [(p[0],p[5]) for p in layout if p[5]!='step'] == [(0,'start'),(8,'checkpoint'),(16,'checkpoint'),(24,'checkpoint'),(31,'finish')]

# Only a low collision stub is fixed. The tree and the people are all
# scale-coupled display parts, so there can be no duplicate rigid sculptures.
assert block(0,100,0)=='SPRUCE_WOOD' and block(0,102,0)=='SPRUCE_WOOD'
assert block(0,103,0)=='AIR' and block(0,130,0)=='AIR' and block(0,140,0)=='AIR'
for x,y,z in ((16,101,1),(14,115,1),(12,129,1),(12,142,0),(3,146,0)):
    assert block(x,y,z)=='AIR', ('old rigid figure remains',x,y,z,block(x,y,z))
assert sum(grid.count(names.index(m)) for m in ('SPRUCE_LEAVES','DARK_OAK_LEAVES')) > 100

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
print('SPAWN V77 BLUEPRINT: PASS (32 landings, five plate areas, dynamic scene reservation, protected architecture and walkable start/gates)')
