#!/usr/bin/env python3
"""Read the immutable v70 RLE and verify the new guide's clear, reachable plaza."""
import gzip
import struct
from collections import deque
from pathlib import Path

path = Path(__file__).resolve().parent.parent / 'core-src/src/main/resources/spawn_v70.dat'
data = gzip.decompress(path.read_bytes())
w,h,l,ox,oy,oz=struct.unpack_from('>hhhhhh',data,0); offset=12
n,=struct.unpack_from('>H',data,offset); offset+=2
names=[]
for _ in range(n):
    size,=struct.unpack_from('>H',data,offset);offset+=2
    names.append(data[offset:offset+size].decode());offset+=size
grid=bytearray()
while True:
    value,count=struct.unpack_from('>BH',data,offset);offset+=3
    if not count:break
    grid.extend(bytes([value])*count)
assert len(grid)==w*h*l

def block(x,y,z):
    if not (0<=x+ox<w and 0<=y-64<h and 0<=z+oz<l):return 'AIR'
    return names[grid[((x+ox)*h+y-64)*l+z+oz]]
non_solid={'AIR','LIGHT_WEIGHTED_PRESSURE_PLATE','HEAVY_WEIGHTED_PRESSURE_PLATE','RED_CARPET',
           'WHITE_CARPET','YELLOW_CARPET','CYAN_CARPET','LEVER','END_ROD','COBWEB'}
def stand(x,y,z):return block(x,y-1,z) not in non_solid and block(x,y,z) in non_solid and block(x,y+1,z) in non_solid
# NPC at (-12.5,100,52.5) occupies x=-13, z=52, with a 3x3 clear footprint.
for x in range(-14,-11):
    for z in range(51,54):
        assert block(x,99,z) not in non_solid, (x,z,'floor')
        for y in range(100,104):assert block(x,y,z)=='AIR',(x,y,z,block(x,y,z))
visited={(0,100,52)};queue=deque(visited);goal=(-13,100,52)
while queue and goal not in visited:
    x,y,z=queue.popleft()
    for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
        nx,nz=x+dx,z+dz
        if not (-120<=nx<=120 and -120<=nz<=120):continue
        for dy in (0,1,-1):
            ny=y+dy;point=(nx,ny,nz)
            if point in visited or not stand(nx,ny,nz):continue
            if dy==1 and block(x,y+2,z) not in non_solid:continue
            if dy==-1 and block(nx,y+1,nz) not in non_solid:continue
            visited.add(point);queue.append(point)
assert goal in visited,'expedition guide cannot be reached from spawn'
print(f'GUIDE SPAWN: PASS clear 3×3 footprint and walkable route ({len(visited)} visited cells)')
