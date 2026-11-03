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

# A flat star facing the entrance. The top glove meets its right tip exactly,
# and every part shares the same transform at every size.
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

# Keep the visible trunk short: the old 44-block brown column stuck out
# through the canopy and looked like a pole in the player's screenshot. This
# model still encloses the small fixed 3-block collision stub at every scale.
part('tree','SPRUCE_WOOD',0,0,0,1.5,4.5,1.5)

# Three block-built human figures and their foundation. They face the public
# plaza (+Z), with visible eyes and mouths. Their offsets and sizes use exactly
# the same origin/factor as the tree and all gifts. Feet meet the pedestal and
# the two lower people support the feet of the person above with raised hands.
part('figure_base','CHISELED_STONE_BRICKS',13.8,0,-2.1,4.4,1,4.2)

def person(group,x,y,coat,trousers,skin,hair,upper=False):
    # Separate legs, shoes, torso, belt and contrasting buttons.
    for dx in (0,2.15):
        part(group,'DARK_OAK_PLANKS',x+dx,y,-1.5,1.85,1.2,3)
        part(group,trousers,x+dx,y+1.2,-1.5,1.85,3.8,3)
    part(group,coat,x,y+5,-1.8,4,5,3.6)
    part(group,'DARK_OAK_PLANKS',x,y+5.1,1.8,4,.45,.12)
    for h in (6.5,8.1): part(group,'GOLD_BLOCK',x+1.84,y+h,1.8,.32,.32,.14)
    # Head with hair and an actual face on the side visible from /spawn.
    part(group,skin,x-.2,y+10,-2,4.4,3,4)
    part(group,hair,x-.2,y+12.65,-2,4.4,.35,4)
    part(group,hair,x-.2,y+10,-2.06,4.4,2.65,.06)
    for ex in (.6,2.65):
        part(group,'WHITE_CONCRETE',x+ex,y+11.25,2,.75,.65,.10)
        part(group,'BLACK_CONCRETE',x+ex+.25,y+11.35,2.10,.3,.40,.10)
    part(group,'RED_CONCRETE',x+1.45,y+10.35,2,1.1,.25,.12)
    if not upper:
        # Shoulders and raised forearms. Gloves sit ABOVE the head and exactly
        # touch the next figure's two shoes without piercing a face or head.
        part(group,coat,x-1,y+6.3,-1,1.2,6.7,2)
        part(group,coat,x-1.65,y+10.5,-.9,1.2,2.5,1.8)
        part(group,skin,x-1.9,y+13,-1,1.8,1,2)
        part(group,coat,x+3.8,y+6.3,-1,1.2,6.7,2)
        part(group,coat,x+1.8,y+13,-1,3.2,.4,2)
        part(group,skin,x+.15,y+13,-1,1.85,1,2)
    else:
        # A connected bent arm climbs toward the star. The very last glove has
        # its own group, so tests can prove contact with the star, not overlap.
        part(group,coat,x-1,y+6,-1,1.2,4.2,2)
        part(group,coat,x-3,y+9,-1,2.3,2,2)
        part(group,coat,x-4.9,y+10.5,-1,2.4,2,2)
        part(group,coat,x-6.7,y+12,-.85,2.1,2.5,1.7)
        part(group,coat,x-8,y+14,-.65,1.7,2,1.3)
        # The star cross ends at x=1.35, y=44.65..45.40, z=±.38.
        part('hand_star',skin,1.35,44.65,-.38,1.1,.75,.76)
        part(group,coat,x+3.8,y+6,-1,1.2,4,2)
        part(group,skin,x+4.3,y+5,-1,1.4,1.3,2)

person('person_1',14,1,'BLUE_CONCRETE','BLACK_CONCRETE','ORANGE_TERRACOTTA','DARK_OAK_PLANKS')
person('person_2',12,15,'RED_CONCRETE','BLUE_CONCRETE','ORANGE_TERRACOTTA','SPRUCE_PLANKS')
person('person_3',10,29,'LIGHT_BLUE_CONCRETE','PURPLE_CONCRETE','PINK_TERRACOTTA','DARK_OAK_PLANKS',True)

out=ROOT/'core-src/src/main/resources/elka_v77.csv'
with out.open('w',newline='',encoding='ascii') as f:
    f.write('# group,material,x,y,z,width,height,depth; dimensions are at level 0\n')
    writer=csv.writer(f,lineterminator='\n');writer.writerows(parts)
print(f'ELKA MODEL: wrote {len(parts)} block-display parts to {out}')
