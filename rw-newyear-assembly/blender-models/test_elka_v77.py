#!/usr/bin/env python3
"""Conservative 3-D scene tests: every scale and the full animated intervals.

The scene uses axis-aligned display cuboids, not solid voxels. These checks are
not a graphical Minecraft-client test; they verify that model pieces are aligned,
connected where intentional, and clear of scenery, gifts, NPCs and parkour.
"""
import csv
import math
from itertools import combinations
from pathlib import Path
from test_spawn_v77 import block, layout

ROOT=Path(__file__).resolve().parent.parent
rows=[]
with (ROOT/'core-src/src/main/resources/elka_v77.csv').open(encoding='ascii') as f:
    for row in csv.reader(line for line in f if not line.startswith('#')):
        group,material,*nums=row
        rows.append((group,material,*map(float,nums)))
expected={'tree','gift_west','gift_north','gift_south','figure_base','person_1','person_2','person_3','hand_star'}
assert 0<len(rows)<=256 and {p[0] for p in rows}==expected
people={'person_1','person_2','person_3'}
gifts={'gift_west','gift_north','gift_south'}

def bounds(p,scale):
    _,_,x,y,z,dx,dy,dz=p
    return (x*scale,100+y*scale,z*scale,(x+dx)*scale,100+(y+dy)*scale,(z+dz)*scale)
def gap(a,b):
    return max(b[0]-a[3],a[0]-b[3],b[1]-a[4],a[1]-b[4],b[2]-a[5],a[2]-b[5])
def joined(a,b):
    return all(a[i]<=b[i+3]+1e-6 and b[i]<=a[i+3]+1e-6 for i in range(3))

# Every face, leg, glove and arm is actually joined to its own person's body.
for group in people:
    parts=[p for p in rows if p[0]==group]
    if group=='person_3':parts += [p for p in rows if p[0]=='hand_star']
    seen={0};todo=[0]
    while todo:
        a=bounds(parts[todo.pop()],1)
        for j,other in enumerate(parts):
            if j not in seen and joined(a,bounds(other,1)):seen.add(j);todo.append(j)
    assert len(seen)==len(parts), ('disconnected body/arm/face',group,[parts[i] for i in range(len(parts)) if i not in seen])
    assert sum(p[1]=='BLACK_CONCRETE' and p[6] < 1 for p in parts)>=2, ('missing front-facing pupils',group)

# Stacked bodies and pedestal are connected; this is deliberate, unlike gifts.
def groups_touch(a,b):
    return any(joined(bounds(p,1),bounds(q,1)) for p in rows if p[0]==a for q in rows if q[0]==b)
assert groups_touch('figure_base','person_1')
assert groups_touch('person_1','person_2') and groups_touch('person_2','person_3')
star=next(p for p in rows if p[0]=='tree' and p[1]=='GOLD_BLOCK' and abs(p[2]+1.35)<1e-5 and abs(p[3]-44.65)<1e-5)
hand=next(p for p in rows if p[0]=='hand_star')

# NPC and warp locations are live entities, not voxels in the blueprint.
points=[(-8,42),(8,42),(0,36),(-13,34),(13,34),(-16,25),(16,25),(-16,14),(16,14),
        (-14,-15),(14,-15),(-8,19),(8,19),(25,12),(25,5),(-25,9),(-25,-1),
        (-23,28),(23,28),(-34,-26),(62,-3),(0,-53),(-66,4),(-78,-34),(-66,68),
        (66,68),(35,-24),(0,24)]
for n in range(70,131):
    scale=n/100
    starbox,handbox=bounds(star,scale),bounds(hand,scale)
    assert abs(starbox[3]-handbox[0])<1e-5 and joined(starbox,handbox), ('hand is detached',n,starbox,handbox)
    scaled=[(p,bounds(p,scale)) for p in rows]
    for (p,a),(q,b) in combinations(scaled,2):
        if p[0]==q[0]:continue
        g=gap(a,b)
        # Every gift remains separate from every other gift, the tree, people
        # and the foundation throughout the full animated resize.
        if p[0] in gifts or q[0] in gifts:
            assert g>=.5-1e-6, ('gift touches scene',n,p,q,g)
        elif 'tree' in (p[0],q[0]):
            if {p[0],q[0]}=={'tree','hand_star'} and (p==star or q==star):
                assert abs(g)<1e-5, ('star/glove must meet, not overlap',n,p,q,g)
            else:
                assert g>=.15-1e-6, ('figure intersects the tree',n,p,q,g)
        elif {p[0],q[0]}!={'person_3','hand_star'}:
            assert g>=-1e-6, ('separate bodies intersect',n,p,q,g)
    for p,b in scaled:
        x1,y1,z1,x2,y2,z2=b
        for x,z in points:
            assert gap(b,(x-.75,100,z-.75,x+.75,104,z+.75))>=.5, ('NPC/warp/gift obstruction',n,p,x,z)
        for x in range(math.floor(x1+1e-6),math.ceil(x2-1e-6)):
            for y in range(math.floor(y1+1e-6),math.ceil(y2-1e-6)):
                for z in range(math.floor(z1+1e-6),math.ceil(z2-1e-6)):
                    mat=block(x,y,z)
                    if mat=='AIR' or (p[0]=='tree' and x==z==0 and 100<=y<=102 and mat=='SPRUCE_WOOD'):continue
                    raise AssertionError(('model overlaps world',n,p,(x,y,z),mat))
        for i,x,y,z,half,kind in layout:
            assert x2<x-half-1 or x1>x+half+2 or z2<z-half-1 or z1>z+half+2 or y2<y-1 or y1>y+4, (n,p,i)

# Swept AABB for each interval encloses all intermediate interpolation frames.
for n in range(70,130):
    for p in rows:
        a,b=bounds(p,n/100),bounds(p,(n+1)/100)
        x1,y1,z1=(min(a[i],b[i]) for i in range(3))
        x2,y2,z2=(max(a[i],b[i]) for i in range(3,6))
        for x in range(math.floor(x1+1e-6),math.ceil(x2-1e-6)):
            for y in range(math.floor(y1+1e-6),math.ceil(y2-1e-6)):
                for z in range(math.floor(z1+1e-6),math.ceil(z2-1e-6)):
                    mat=block(x,y,z)
                    if mat=='AIR' or (p[0]=='tree' and x==z==0 and 100<=y<=102 and mat=='SPRUCE_WOOD'):continue
                    raise AssertionError(('swept model hits world',n,p,(x,y,z),mat))
print(f'ELKA V77: PASS ({len(rows)} parts, 3 connected people/arms, glove touches star, 61 scales, 60 swept intervals; gifts, NPCs, parkour and world clear)')
