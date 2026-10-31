#!/usr/bin/env python3
"""Structural regression on the v73 RLE blueprint, not a visual client test."""
import gzip
import struct
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def load(version):
    data = gzip.decompress((ROOT / f'core-src/src/main/resources/spawn_{version}.dat').read_bytes())
    w, h, length, ox, oy, oz = struct.unpack_from('>hhhhhh', data, 0)
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
    assert len(grid) == w*h*length
    def block(x, y, z):
        if not (0 <= x+ox < w and 0 <= y-64 < h and 0 <= z+oz < length): return 'AIR'
        return names[grid[((x+ox)*h + y-64)*length + z+oz]]
    return block, grid, names


old, _, _ = load('v70')
block, grid, names = load('v73')
# The exact star/hand contact, taller stacked figures, and increased trunk height.
assert old(0, 130, 0) == 'AIR' and block(0, 130, 0) == 'SPRUCE_WOOD'
assert old(0, 123, 0) != 'AIR' and block(0, 137, 0) == 'OCHRE_FROGLIGHT'
assert block(2,135,0) == 'OCHRE_FROGLIGHT' and block(3,135,0) == 'WHITE_CONCRETE'
for y in (101,111,121): assert block(13,y,1) == 'DARK_OAK_PLANKS'
for y, hair in ((110,'DARK_OAK_PLANKS'),(120,'SPRUCE_PLANKS'),(130,'DARK_OAK_PLANKS')):
    assert block(13,y,0) == hair
ice = {'PACKED_ICE','BLUE_ICE'}
assert sum(grid.count(names.index(m)) for m in ice) > 250_000
# At player height and well above the old ridges, four sides have connected
# samples between the deliberately open gates. Four cardinal gates stay open.
for t in range(-112, 113, 4):
    if abs(t) <= 8: continue
    for x,z in ((118,t),(-118,t),(t,118),(t,-118)):
        for y in (100,120): assert block(x,y,z) in ice, (x,y,z,block(x,y,z))
        assert block(x,129,z) in ice | {'SNOW_BLOCK'}, (x,129,z,block(x,129,z))
for y in (100,110,120,130):
    for x,z in ((118,0),(-118,0),(0,118),(0,-118)):
        assert block(x,y,z) == 'AIR', ('blocked gate', x,y,z,block(x,y,z))
# Sampling representative palace/functional architecture guards against ice
# walls overwriting important existing interior volumes.
for x1,x2,z1,z2,y1,y2 in [(-21,21,65,91,96,128),(-102,-69,-13,21,95,105),
                            (59,109,-3,43,95,105),(-45,45,-92,-45,95,106)]:
    for x in range(x1,x2+1,3):
        for z in range(z1,z2+1,3):
            for y in range(y1,y2+1,3):
                assert block(x,y,z) == old(x,y,z), ('existing structure changed', x,y,z, old(x,y,z), block(x,y,z))
# Footpath to guide remains clear and walkable in the new blueprint.
non_solid = {'AIR','LIGHT_WEIGHTED_PRESSURE_PLATE','HEAVY_WEIGHTED_PRESSURE_PLATE','RED_CARPET',
             'WHITE_CARPET','YELLOW_CARPET','CYAN_CARPET','LEVER','END_ROD','COBWEB'}
def stand(x,y,z): return block(x,y-1,z) not in non_solid and block(x,y,z) in non_solid and block(x,y+1,z) in non_solid
for x in range(-14,-11):
    for z in range(51,54):
        assert block(x,99,z) not in non_solid
        assert all(block(x,y,z) == 'AIR' for y in range(100,104))
visited={(0,100,52)}; q=deque(visited); goal=(-13,100,52)
while q and goal not in visited:
    x,y,z=q.popleft()
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
        nx,nz=x+dx,z+dz
        if not (-120<=nx<=120 and -120<=nz<=120): continue
        for dy in (0,1,-1):
            ny=y+dy; p=(nx,ny,nz)
            if p in visited or not stand(nx,ny,nz): continue
            if dy==1 and block(x,y+2,z) not in non_solid: continue
            if dy==-1 and block(nx,y+1,nz) not in non_solid: continue
            visited.add(p); q.append(p)
assert goal in visited
print('SPAWN V73 BLUEPRINT: PASS larger tree/figures, four tall ice walls & gates, protected structures, route to guide')
