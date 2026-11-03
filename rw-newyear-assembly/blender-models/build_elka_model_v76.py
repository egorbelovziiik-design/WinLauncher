#!/usr/bin/env python3
"""One small, vanilla block-display model, shared by the server and geometry tests.

All parts use a common local origin (0,100,0). Every dimension/offset scales
uniformly. Present groups are visual decorations, not the /gifts hunt chests.
"""
import csv
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
parts=[]
def part(group,material,x,y,z,dx,dy,dz):
    assert min(dx,dy,dz)>0
    parts.append((group,material,*[round(n,4) for n in (x,y,z,dx,dy,dz)]))

# A rich 46-block tall silhouette: twenty-two layered octagonal boughs. The
# base is narrow enough to leave NPCs and the three standing figures clear.
# The fixed wood trunk in the template is hidden inside the smallest version.
for layer in range(21):
    y=2+layer*2
    radius=max(.65, 8.3*(1-y/47) + (0.55 if layer%3==0 and y<30 else 0))
    a=radius*.72
    mat='DARK_OAK_LEAVES' if layer%4==0 else 'SPRUCE_LEAVES'
    part('tree',mat,-radius,y,-a,2*radius,2,2*a)
    part('tree',mat,-a,y,-radius,2*a,2,radius-a)
    part('tree',mat,-a,y,a,2*a,2,radius-a)
    # Four bright baubles attached outside the foliage, rotating around tiers.
    if layer%2==0 and layer<19:
        for i in range(4):
            if y >= 32 and i == 0: continue  # the figure reaches in from +X
            ang=math.pi*.5*i + (0.16 if layer%4==0 else .32)
            r=radius*.90
            x,z=math.cos(ang)*r,math.sin(ang)*r
            part('tree',('RED_CONCRETE','GOLD_BLOCK','LIGHT_BLUE_STAINED_GLASS','MAGENTA_CONCRETE')[(i+layer//2)%4],
                 x-.30,y+.65,z-.30,.60,.60,.60)
    # Snow dustings along a subset of branch edges.
    if layer%3==0:
        for i in range(4):
            ang=math.pi*.5*i+math.pi/4
            r=radius*.8;x,z=math.cos(ang)*r,math.sin(ang)*r
            part('tree','SNOW_BLOCK',x-.5,y+1.88,z-.5,1,.14,1)

# Treetop star, just clear of the nearby statue's reaching hand even at +3.
part('tree','OCHRE_FROGLIGHT',-.38,43.8,-.38,.76,2.5,.76)
part('tree','GOLD_BLOCK',-1.35,44.65,-.38,2.7,.75,.76)
part('tree','SEA_LANTERN',-.45,44.55,-.45,.9,.9,.9)

# Three presents, radially outside the tree on three clear sides. The entire
# group, including ribbon and bow, moves and scales with exactly the same factor.
for name,cx,cz,color,ribbon in [
    ('gift_west',-12.6,0,'RED_CONCRETE','GOLD_BLOCK'),
    ('gift_north',0,-12.6,'LIGHT_BLUE_CONCRETE','WHITE_CONCRETE'),
    ('gift_south',0,12.6,'PURPLE_CONCRETE','GOLD_BLOCK')]:
    size=2.1; h=2.05
    part(name,color,cx-size/2,0,cz-size/2,size,h,size)
    # Ribbons sit on the faces, not through the coloured wrapping.
    t=.10; w=.30
    part(name,ribbon,cx-w/2,0,cz-size/2-t,w,h+t,t)
    part(name,ribbon,cx-w/2,0,cz+size/2,w,h+t,t)
    part(name,ribbon,cx-size/2-t,0,cz-w/2,t,h+t,w)
    part(name,ribbon,cx+size/2,0,cz-w/2,t,h+t,w)
    part(name,ribbon,cx-w/2,h,cz-size/2,w,t,size)
    part(name,ribbon,cx-size/2,h,cz-w/2,size,t,w)
    part(name,ribbon,cx-.75,h+t,cz-.18,.60,.32,.36)
    part(name,ribbon,cx+.15,h+t,cz-.18,.60,.32,.36)
    part(name,ribbon,cx-.15,h+t,cz-.18,.30,.26,.36)

out=ROOT/'core-src/src/main/resources/elka_v76.csv'
with out.open('w',newline='',encoding='ascii') as f:
    f.write('# group,material,x,y,z,width,height,depth; dimensions are at level 0\n')
    writer=csv.writer(f,lineterminator='\n');writer.writerows(parts)
print(f'ELKA MODEL: wrote {len(parts)} block-display parts to {out}')
