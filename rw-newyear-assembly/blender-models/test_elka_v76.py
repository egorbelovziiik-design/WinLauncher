#!/usr/bin/env python3
"""Test real model bounds at all intermediate scales against the frozen world.

This is a conservative cuboid/voxel check, not a rendered Minecraft test.
"""
import csv
import math
from itertools import combinations
from pathlib import Path
from test_spawn_v76 import block, layout

ROOT=Path(__file__).resolve().parent.parent
rows=[]
with (ROOT/'core-src/src/main/resources/elka_v76.csv').open(encoding='ascii') as f:
    for row in csv.reader(line for line in f if not line.startswith('#')):
        group,material,*nums=row
        rows.append((group,material,*map(float,nums)))
assert 0 < len(rows) <= 200
assert {p[0] for p in rows}=={'tree','gift_west','gift_north','gift_south'}

def bounds(p,scale):
    _,_,x,y,z,dx,dy,dz=p
    return (x*scale,100+y*scale,z*scale,(x+dx)*scale,100+(y+dy)*scale,(z+dz)*scale)
def gap(a,b):
    # A gap in any dimension separates the boxes. Negative means overlap.
    return max(b[0]-a[3],a[0]-b[3],b[1]-a[4],a[1]-b[4],b[2]-a[5],a[2]-b[5])

# Include all 0.01 intermediate factors: resize animations are a continuous
# homothety about the same point, so the gap constraints hold between levels.
for n in range(70,131):
    scale=n/100
    scaled=[(p,bounds(p,scale)) for p in rows]
    for (p,a),(q,b) in combinations(scaled,2):
        if p[0]!=q[0]:
            assert gap(a,b)>=.5, ('models touch',n,p,q,gap(a,b))
    for p,b in scaled:
        x1,y1,z1,x2,y2,z2=b
        for x in range(math.floor(x1+1e-6),math.ceil(x2-1e-6)):
            for y in range(math.floor(y1+1e-6),math.ceil(y2-1e-6)):
                for z in range(math.floor(z1+1e-6),math.ceil(z2-1e-6)):
                    mat=block(x,y,z)
                    if mat=='AIR':continue
                    # One-block trunk is deliberately inside the tree at every
                    # scale, and is the only collidable part of the model.
                    if p[0]=='tree' and (x,z)==(0,0) and 100<=y<=130 and mat=='SPRUCE_WOOD':continue
                    raise AssertionError(('model overlaps physical world',n,p,(x,y,z),mat))
        # Course and model remain in disjoint zones (with empty breathing room).
        for i,x,y,z,half,kind in layout:
            assert x2 < x-half-1 or x1 > x+half+2 or z2 < z-half-1 or z1 > z+half+2 or y2 < y-1 or y1 > y+4, (n,p,i)
print(f'ELKA V76: PASS ({len(rows)} parts, 61 factors; tree/presents separated, no world or parkour collisions)')
