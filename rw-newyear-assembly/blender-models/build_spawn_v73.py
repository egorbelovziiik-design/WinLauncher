#!/usr/bin/env python3
"""Build the v7.3 winter-village spawn while preserving the 6.6 warp/NPC coordinates.

A redesigned tree with three stacked block-built people, pedestrian plaza, guarded
separate ice formations, refreshed palace facade, and reserved human-scale details.
The existing functional PvP, mine, cases and parkour layouts remain compatible.
All listed warp/NPC/palace positions are checked by 3-D walkability BFS.
"""

import gzip
import math
import os
import random
import struct
from pathlib import Path
from collections import deque

W, H, L = 251, 132, 251  # X: -125..+125, Y: 64..195, Z: -125..+125
OX, OY, OZ = 125, 36, 125
Y_OFFSET = 64

PALETTE_NAMES = [
    "AIR",
    # Snow, Glacial Ice & Marble
    "SNOW_BLOCK", "PACKED_ICE", "BLUE_ICE", "SMOOTH_QUARTZ", "QUARTZ_PILLAR",
    "CHISELED_QUARTZ_BLOCK", "POLISHED_DIORITE", "CALCITE",
    # Castle Stone & Roofs
    "STONE_BRICKS", "CHISELED_STONE_BRICKS", "DEEPSLATE_BRICKS", "DEEPSLATE_TILES",
    "POLISHED_BLACKSTONE_BRICKS", "DARK_PRISMARINE", "PRISMARINE_BRICKS",
    # Woods & REAL Evergreen Tree Leaves (persistent=true in SpawnEngine!)
    "SPRUCE_PLANKS", "DARK_OAK_PLANKS", "CRIMSON_PLANKS", "SPRUCE_LOG", "SPRUCE_WOOD",
    "DARK_OAK_WOOD", "STRIPPED_SPRUCE_LOG",
    "SPRUCE_LEAVES", "DARK_OAK_LEAVES", "AZALEA_LEAVES", "FLOWERING_AZALEA_LEAVES",
    # Festive Accents & Textiles
    "RED_CONCRETE", "WHITE_CONCRETE", "YELLOW_CONCRETE", "ORANGE_CONCRETE",
    "PURPLE_CONCRETE", "MAGENTA_CONCRETE", "LIGHT_BLUE_CONCRETE", "BLUE_CONCRETE",
    "CYAN_CONCRETE", "BLACK_CONCRETE",
    "RED_WOOL", "WHITE_WOOL",
    "RED_CARPET", "WHITE_CARPET", "YELLOW_CARPET", "CYAN_CARPET",
    # Stained Glass & Metals
    "RED_STAINED_GLASS", "WHITE_STAINED_GLASS", "YELLOW_STAINED_GLASS",
    "LIGHT_BLUE_STAINED_GLASS", "BLUE_STAINED_GLASS", "CYAN_STAINED_GLASS",
    "PURPLE_STAINED_GLASS", "MAGENTA_STAINED_GLASS", "LIME_STAINED_GLASS",
    "GOLD_BLOCK", "EMERALD_BLOCK", "DIAMOND_BLOCK", "AMETHYST_BLOCK",
    "NETHERITE_BLOCK", "LAPIS_BLOCK", "COPPER_BLOCK", "REDSTONE_BLOCK",
    # Lighting
    "SEA_LANTERN", "SHROOMLIGHT", "GLOWSTONE", "OCHRE_FROGLIGHT",
    "PEARLESCENT_FROGLIGHT", "VERDANT_FROGLIGHT", "END_ROD", "LANTERN", "CARVED_PUMPKIN",
    # Functional & Palace Interior Furniture
    "IRON_BARS", "SPRUCE_FENCE", "DARK_OAK_FENCE",
    "BOOKSHELF", "ENCHANTING_TABLE", "ANVIL", "ENDER_CHEST", "CRAFTING_TABLE",
    "BREWING_STAND", "CAULDRON", "SMITHING_TABLE", "CARTOGRAPHY_TABLE", "BARREL",
    "NOTE_BLOCK", "LEVER",
    "LIGHT_WEIGHTED_PRESSURE_PLATE", "HEAVY_WEIGHTED_PRESSURE_PLATE",
    # Mine & Special
    "COAL_ORE", "IRON_ORE", "GOLD_ORE", "DIAMOND_ORE", "EMERALD_ORE", "LAPIS_ORE",
    "OBSIDIAN", "CRYING_OBSIDIAN", "COBWEB", "MAGMA_BLOCK", "HAY_BLOCK",
]

P = {name: idx for idx, name in enumerate(PALETTE_NAMES)}
assert len(PALETTE_NAMES) < 250, "Palette fits in 1 byte!"

grid = bytearray(W * H * L)
rng = random.Random(20260508)


def s(x, y, z, mat_id):
    lx = x + OX
    ly = y - Y_OFFSET
    lz = z + OZ
    if 0 <= lx < W and 0 <= ly < H and 0 <= lz < L:
        grid[(lx * H + ly) * L + lz] = mat_id


def g(x, y, z):
    lx = x + OX
    ly = y - Y_OFFSET
    lz = z + OZ
    if 0 <= lx < W and 0 <= ly < H and 0 <= lz < L:
        return grid[(lx * H + ly) * L + lz]
    return 0


def fill_box(x1, y1, z1, x2, y2, z2, mat_id):
    lx1, lx2 = max(0, min(x1, x2) + OX), min(W - 1, max(x1, x2) + OX)
    ly1, ly2 = max(0, min(y1, y2) - Y_OFFSET), min(H - 1, max(y1, y2) - Y_OFFSET)
    lz1, lz2 = max(0, min(z1, z2) + OZ), min(L - 1, max(z1, z2) + OZ)
    if lx1 > lx2 or ly1 > ly2 or lz1 > lz2:
        return
    row_len = lz2 - lz1 + 1
    patch = bytes([mat_id]) * row_len
    for lx in range(lx1, lx2 + 1):
        base_x = lx * H
        for ly in range(ly1, ly2 + 1):
            start = (base_x + ly) * L + lz1
            grid[start:start + row_len] = patch


def fill_cyl(cx, cz, r, y1, y2, mat_id):
    r2 = r * r
    ir = int(math.ceil(r))
    for dx in range(-ir, ir + 1):
        for dz in range(-ir, ir + 1):
            if dx * dx + dz * dz <= r2:
                fill_box(cx + dx, y1, cz + dz, cx + dx, y2, cz + dz, mat_id)


# ==============================================================================
# 1. FLOATING GLACIAL ISLANDS WITH DEEP LAYERED BLUE-ICE STALACTITES
# ==============================================================================
print("[1/11] Sculpting 7 Floating Glacial Islands & Deep Ice Stalactites...")


def build_floating_island(cx, cz, rx, rz, top_y=99, depth=26):
    irx, irz = int(math.ceil(rx)), int(math.ceil(rz))
    for dx in range(-irx, irx + 1):
        for dz in range(-irz, irz + 1):
            nd = math.sqrt((dx / rx) ** 2 + (dz / rz) ** 2)
            ang = math.atan2(dz, dx)
            edge_wobble = 0.045 * math.sin(ang * 7.0 + cx * 0.2) + 0.03 * math.cos(ang * 11.0 - cz * 0.2)
            if nd > 1.0 + edge_wobble:
                continue
            x, z = cx + dx, cz + dz
            spike = 0.62 + 0.25 * math.sin(x * 0.33 + z * 0.21) + 0.20 * math.cos(x * 0.51 - z * 0.39)
            col_d = max(4, int(depth * ((1.03 - min(1.0, nd)) ** 0.72) * spike))
            bot_y = max(Y_OFFSET + 1, top_y - col_d)
            for y in range(bot_y, top_y - 2):
                rel = (y - bot_y) / max(1, (top_y - 2 - bot_y))
                if rel < 0.30:
                    s(x, y, z, P["BLUE_ICE"])
                elif rel < 0.62:
                    s(x, y, z, P["PACKED_ICE"])
                else:
                    s(x, y, z, P["DEEPSLATE_BRICKS"] if (x + y + z) % 5 == 0 else P["STONE_BRICKS"])
            s(x, top_y - 2, z, P["STONE_BRICKS"])
            s(x, top_y - 1, z, P["POLISHED_DIORITE"])
            # Subtle textured winter marble + snow surface
            if (x * 3 + z * 5) % 17 == 0:
                s(x, top_y, z, P["CALCITE"])
            elif (x + z) % 9 == 0:
                s(x, top_y, z, P["SMOOTH_QUARTZ"])
            else:
                s(x, top_y, z, P["SNOW_BLOCK"])


# Central Heart Island + South Palace Peninsula
build_floating_island(0, 4, 52, 52, top_y=99, depth=30)
build_floating_island(0, 62, 34, 34, top_y=99, depth=26)
# North Shulker Case Terrace
build_floating_island(0, -65, 42, 24, top_y=101, depth=24)
# East Gladiator Colosseum + Duel Kabinki Wing
build_floating_island(82, 16, 30, 32, top_y=99, depth=27)
build_floating_island(78, -24, 24, 29, top_y=99, depth=23)
# West Crystal Auto-Mine + NW Mob Farm Bastion
build_floating_island(-83, 4, 26, 25, top_y=99, depth=25)
build_floating_island(-80, -39, 25, 20, top_y=99, depth=22)
# SW Casino 777 Island & SE Bafovar Wizard Island
build_floating_island(-66, 68, 22, 20, top_y=99, depth=21)
build_floating_island(66, 68, 22, 20, top_y=99, depth=21)


# ==============================================================================
# 2. ROYAL MARBLE & STONEBRICK BOULEVARDS + ARCHED BRIDGES (NO YELLOW SPAM!)
# ==============================================================================
print("[2/11] Building Royal Marble Plazas, Arched Bridges & Streetlights...")

# Central Compass-Rose Marble Inlay Plaza (R=12..34 around the Tree)
for dx in range(-36, 37):
    for dz in range(-36, 56):
        d = math.hypot(dx, dz)
        if 11.5 <= d <= 35.5 or (abs(dx) <= 16 and 12 <= dz <= 56):
            x, z = dx, dz
            if abs(dx) <= 3 and 24 <= dz <= 55:
                # Grand Royal Carpet Boulevard from /spawn (0,52) straight toward the Tree!
                s(x, 99, z, P["DARK_PRISMARINE"] if abs(dx) <= 1 else P["POLISHED_DIORITE"])
                if abs(dx) == 2 and z % 3 == 0:
                    s(x, 99, z, P["CHISELED_QUARTZ_BLOCK"])
            elif int(round(d)) in (13, 23, 33):
                s(x, 99, z, P["DEEPSLATE_TILES"])
            elif abs(dx) == abs(dz) or dx == 0 or dz == 0:
                s(x, 99, z, P["SMOOTH_QUARTZ"])
            elif (dx + dz) % 2 == 0:
                s(x, 99, z, P["POLISHED_DIORITE"])
            else:
                s(x, 99, z, P["CALCITE"] if (dx * dz) % 5 == 0 else P["SNOW_BLOCK"])


def build_royal_bridge(x1, z1, x2, z2, half_w=4, y_surf=99):
    steps = int(max(abs(x2 - x1), abs(z2 - z1)) * 2) + 1
    visited = set()
    for i in range(steps + 1):
        t = i / max(1, steps)
        cx = int(round(x1 + (x2 - x1) * t))
        cz = int(round(z1 + (z2 - z1) * t))
        for dx in range(-half_w - 1, half_w + 2):
            for dz in range(-half_w - 1, half_w + 2):
                if dx * dx + dz * dz > (half_w + 1.2) ** 2:
                    continue
                bx, bz = cx + dx, cz + dz
                if (bx, bz) in visited:
                    continue
                visited.add((bx, bz))
                dist_edge = math.hypot(dx, dz)
                # Solid stone/ice bridge arch underside
                arch_drop = int(3 + 3 * math.sin(t * math.pi))
                for yy in range(y_surf - arch_drop, y_surf):
                    if g(bx, yy, bz) == P["AIR"]:
                        s(bx, yy, bz, P["PACKED_ICE"] if yy < y_surf - 2 else P["STONE_BRICKS"])
                if dist_edge >= half_w + 0.15:
                    s(bx, y_surf, bz, P["DEEPSLATE_TILES"])
                else:
                    s(bx, y_surf, bz, P["POLISHED_DIORITE"] if (bx + bz) % 2 == 0 else P["SMOOTH_QUARTZ"])


# Bridges connecting all 7 outer islands to Central Island
build_royal_bridge(28, 17, 65, 17, half_w=5, y_surf=99)     # -> East Colosseum (/warp pvp)
build_royal_bridge(30, -24, 66, -24, half_w=4, y_surf=99)   # -> East Kabinki & Parkour
build_royal_bridge(63, 12, 63, -24, half_w=4, y_surf=99)    # -> Colosseum <-> Train <-> Kabinki
build_royal_bridge(-28, 4, -69, 4, half_w=4, y_surf=99)     # -> West Auto-Mine (/warp mine)
build_royal_bridge(-34, -26, -80, -35, half_w=4, y_surf=99) # -> Top-5 -> NW Mob Farm
build_royal_bridge(-18, 46, -59, 68, half_w=4, y_surf=99)   # -> SW Casino 777
build_royal_bridge(18, 46, 59, 68, half_w=4, y_surf=99)     # -> SE Bafovar Cathedral

# Grand Stepped Marble Bridge North to Shulker Case Terrace (Z=-34 Y=99 -> Z=-50 Y=101)
for z in range(-52, -33):
    ty = 99
    if z <= -42:
        ty = 100
    if z <= -46:
        ty = 101
    for x in range(-8, 9):
        fill_box(x, ty - 5, z, x, ty - 1, z, P["STONE_BRICKS"])
        if abs(x) == 8:
            s(x, ty, z, P["DEEPSLATE_TILES"])
            s(x, ty + 1, z, P["QUARTZ_PILLAR"] if z % 4 == 0 else P["IRON_BARS"])
            if z % 4 == 0:
                s(x, ty + 2, z, P["SEA_LANTERN"])
        elif abs(x) <= 2:
            s(x, ty, z, P["DARK_PRISMARINE"])
        else:
            s(x, ty, z, P["SMOOTH_QUARTZ"] if (x + z) % 2 == 0 else P["POLISHED_DIORITE"])
        fill_box(x, ty + 1, z, x, ty + 7, z, P["AIR"])


# ==============================================================================
# 3. TALL GLACIAL WALLS AROUND THE PERIMETER — NEVER THROUGH BUILDINGS
# ==============================================================================
print("[3] Building four tall ice walls with open gateways and protected architecture...")
# The reserved zones include buildings, approaches and foundations, not just
# visible facades. Every wall footprint, including its safety margin, is
# checked against them before a block is placed.
WALL_RESERVED_ZONES = [
    (-27,27,58,100), (-45,45,-92,-39), (59,109,-3,43), (65,107,-52,2),
    (-102,-69,-13,21), (-101,-59,-54,-35), (-88,-49,49,88), (49,88,49,88),
    (23,72,9,25), (24,72,-33,-15), (-77,-22,-5,13), (-87,-27,-45,-19),
    (-66,-10,37,77), (10,66,37,77), (-11,11,-60,-30), (-12,12,32,100),
]
wall_skipped = 0
wall_built = 0
wall_footprints = []
wall_panels = []
def wall_site_safe(x, z, base_r, height, lean_x=0.0, lean_z=0.0):
    rx = math.ceil(base_r + abs(lean_x) * height + 3)
    rz = math.ceil(base_r + abs(lean_z) * height + 3)
    return (x-rx >= -124 and x+rx <= 124 and z-rz >= -124 and z+rz <= 124
            and not any(x+rx >= a and x-rx <= b and z+rz >= c and z-rz <= d
                        for a,b,c,d in WALL_RESERVED_ZONES))

def wall_panel(cx, cz, axis, top, panel_number):
    """Five-block-deep faceted ice wall with crystal highlights and snow caps."""
    global wall_built, wall_skipped
    if not wall_site_safe(cx, cz, 2.5, top-92):
        wall_skipped += 1
        return
    wall_built += 1
    wall_panels.append((cx, cz, axis, top))
    wall_footprints.append((cx, cz, 2.5, top-92, 0.0, 0.0))
    # Adjacent four-block panels overlap by one block; silhouettes are jagged
    # while the wall remains an actual continuous wall rather than sparse cones.
    for along in range(-2, 3):
        for across in range(-2, 3):
            x = cx + (across if axis == 'x' else along)
            z = cz + (along if axis == 'x' else across)
            local_top = top - (1 if abs(across) == 2 else 0) - (1 if abs(along) == 2 else 0)
            for y in range(92, local_top + 1):
                if y >= local_top - 1:
                    mat = P['SNOW_BLOCK']
                elif (panel_number % 5 == 0 and along == 0 and abs(across) == 2
                      and y in (103, 113, 123, 133)):
                    mat = P['SEA_LANTERN']
                elif (along + y // 4 + panel_number) % 5 == 0 or abs(across) == 2 and y % 9 <= 1:
                    mat = P['BLUE_ICE']
                else:
                    mat = P['PACKED_ICE']
                s(x, y, z, mat)
    # Thin crystal teeth break up the upper silhouette.
    if panel_number % 3 == 0:
        for y in range(top, top + 5):
            s(cx, y, cz, P['SEA_LANTERN'] if y == top + 4 else P['BLUE_ICE'])

def build_corner(cx, cz):
    global wall_built, wall_skipped
    if not wall_site_safe(cx, cz, 2.5, 53):
        wall_skipped += 1
        return
    wall_built += 1
    wall_footprints.append((cx, cz, 2.5, 53, 0.0, 0.0))
    for y in range(92, 146):
        radius = 2.5 if y < 133 else max(0.5, 2.5 - (y-132) * .18)
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                if dx*dx + dz*dz <= radius*radius:
                    mat = P['SNOW_BLOCK'] if y == 145 else (P['BLUE_ICE'] if (dx+dz+y)%4 == 0 else P['PACKED_ICE'])
                    s(cx+dx, y, cz+dz, mat)
    s(cx, 146, cz, P['SEA_LANTERN'])

# Four sides 236 blocks apart, with a generous open gate in the middle of each
# side. The gaps preserve the boulevards and views; all panels lie outside the
# reserved buildings. The uneven tops range from Y=128..145, well above the
# old 12–16-block ridges.
for side_index, (axis, fixed) in enumerate((('x',-118),('x',118),('z',-118),('z',118))):
    for t in range(-116, 117, 4):
        if abs(t) <= 8:  # four clear gateways
            continue
        top = 134 + round(4 * math.sin((t + side_index * 17) * .105))
        if (t // 4 + side_index) % 5 == 0: top += 4
        cx, cz = (fixed, t) if axis == 'x' else (t, fixed)
        wall_panel(cx, cz, axis, top, (t // 4 + side_index * 7))
for x in (-116, 116):
    for z in (-116, 116): build_corner(x, z)
assert all(wall_site_safe(*footprint) for footprint in wall_footprints)
assert len(wall_panels) >= 200 and min(top for _,_,_,top in wall_panels) >= 130
assert {axis for _,_,axis,_ in wall_panels} == {'x','z'}

# ==============================================================================
# 4. LARGER TREE AND THREE TALLER PEOPLE RAISING THE STAR
# ==============================================================================
print("[4] Growing a taller tree and three taller people setting its star...")
# The garden is intentionally low and bounded: NPC plazas and paths stay clear.
fill_cyl(0, 0, 10.5, 99, 99, P['DEEPSLATE_TILES'])
fill_cyl(0, 0, 9.5, 99, 99, P['SNOW_BLOCK'])
fill_box(-1, 100, -1, 1, 132, 1, P['SPRUCE_WOOD'])
# Six overlapping, wider real-leaf skirts: 35+ blocks tall, versus 23 before.
for tier, (bottom, top, radius) in enumerate(((102,112,9.6),(107,117,8.6),
        (112,122,7.4),(117,127,6.3),(122,132,5.2),(127,134,3.7))):
    for y in range(bottom, top + 1):
        t = (y-bottom) / float(top-bottom)
        r = radius * (1 - .66*t)
        for x in range(-math.ceil(r), math.ceil(r)+1):
            for z in range(-math.ceil(r), math.ceil(r)+1):
                dist = math.hypot(x, z)
                if 1.3 < dist <= r and g(x,y,z) != P['SPRUCE_WOOD']:
                    leaf = P['DARK_OAK_LEAVES'] if (x*7+z*3+y*5)%6 == 0 else P['SPRUCE_LEAVES']
                    s(x,y,z,leaf)
                    if dist >= r-.8 and (x*11+z*13+y)%15 == 0:
                        s(x,y,z, P['SNOW_BLOCK'] if (x+y+z)%2 else P['OCHRE_FROGLIGHT'])
# Ornaments and warm garlands follow the surface, without filling passages.
for y in range(104, 134, 3):
    r = max(2.2, 9.5-(y-102)*.22)
    for n in range(10):
        angle = n*math.pi/5 + y*.38
        x,z = round(math.cos(angle)*r), round(math.sin(angle)*r)
        if g(x,y,z) in (P['SPRUCE_LEAVES'], P['DARK_OAK_LEAVES']):
            s(x,y,z,(P['RED_CONCRETE'],P['GOLD_BLOCK'],P['LIGHT_BLUE_STAINED_GLASS'],P['SHROOMLIGHT'],P['MAGENTA_CONCRETE'])[n%5])
STAR_Y = 135
s(0, STAR_Y, 0, P['SEA_LANTERN'])
for k in range(-2, 3):
    s(k, STAR_Y, 0, P['GOLD_BLOCK'] if abs(k)<2 else P['OCHRE_FROGLIGHT'])
    s(0, STAR_Y+k, 0, P['GOLD_BLOCK'] if abs(k)<2 else P['OCHRE_FROGLIGHT'])
s(0,STAR_Y,1,P['GOLD_BLOCK']); s(0,STAR_Y,-1,P['GOLD_BLOCK'])
for gx,gz,col in [(-6,5,'RED_CONCRETE'),(-6,-5,'LIGHT_BLUE_CONCRETE'),(5,-6,'PURPLE_CONCRETE')]:
    fill_box(gx-1,100,gz-1,gx+1,101,gz+1,P[col])
    for d in (-1,0,1):
        s(gx+d,101,gz,P['GOLD_BLOCK']); s(gx,101,gz+d,P['GOLD_BLOCK'])

def sculpt_person(base_y, coat, trousers, skin, hair, top=False):
    """A ten-block-tall, block-built human, standing on the preceding figure."""
    for za,zb in ((-2,-1),(1,2)):
        fill_box(13,base_y,za,15,base_y+1,zb,P['DARK_OAK_PLANKS'])
        fill_box(13,base_y+2,za,15,base_y+3,zb,P[trousers])
    fill_box(12,base_y+4,-2,15,base_y+6,2,P[coat])
    for z in (-3,3):
        fill_box(13,base_y+4,z,14,base_y+6,z,P[coat])
        fill_box(13,base_y+3,z,14,base_y+3,z,P[skin])
    fill_box(13,base_y+7,-2,15,base_y+9,2,P[skin])
    fill_box(13,base_y+9,-2,15,base_y+9,2,P[hair])
    # The faces point toward the tree (-X); contrasting eyes remain visible.
    for z in (-2,2): s(12,base_y+8,z,P['WHITE_CONCRETE'])
    for z in (-1,1): s(12,base_y+8,z,P['BLACK_CONCRETE'])
    s(12,base_y+7,0,P[skin])
    if top:
        # A bent raised arm continues to a glove touching the star's x=2 tip.
        for x,y in ((12,base_y+6),(11,base_y+7),(10,base_y+8),
                    (9,base_y+9),(8,base_y+10),(7,base_y+11),
                    (6,base_y+12),(5,base_y+13),(4,base_y+14)):
            s(x,y,0,P[coat])
        s(3,base_y+14,0,P[skin])
fill_box(11,100,-4,16,100,4,P['CHISELED_STONE_BRICKS'])
sculpt_person(101,'BLUE_CONCRETE','BLACK_CONCRETE','ORANGE_CONCRETE','DARK_OAK_PLANKS')
sculpt_person(111,'RED_CONCRETE','BLUE_CONCRETE','ORANGE_CONCRETE','SPRUCE_PLANKS')
sculpt_person(121,'LIGHT_BLUE_CONCRETE','PURPLE_CONCRETE','WHITE_CONCRETE','DARK_OAK_PLANKS',True)
assert g(3,135,0)==P['WHITE_CONCRETE'] and g(2,135,0)==P['OCHRE_FROGLIGHT']
assert g(13,130,0)==P['DARK_OAK_PLANKS'] and g(13,120,0)==P['SPRUCE_PLANKS']


# 5. Open winter sky: no floating ribbons or oversized sleigh obstructing the view.

# ==============================================================================
# 6. SOUTHERN 3-FLOOR IMPERIAL WINTER PALACE WITH FULL INTERIOR & BALCONY!
#    ("и добавь в это помещение декор этажи и тд" — exact room from image-2.png!)
# ==============================================================================
print("[6/11] Building 3-Floor Imperial Winter Palace (Stairs, Library, Atrium, Chandelier & Balcony)...")

PX1, PX2 = -21, 21
PZ1, PZ2 = 65, 91
PY1, PY2 = 100, 122

# Solid Palace Foundation down to Y=96
fill_box(PX1 - 1, 96, PZ1 - 1, PX2 + 1, 99, PZ2 + 1, P["STONE_BRICKS"])

# Floor 1 Inlaid Royal Marble Floor (Y=99)
for x in range(PX1, PX2 + 1):
    for z in range(PZ1, PZ2 + 1):
        if abs(x) <= 2 and 65 <= z <= 84:
            s(x, 99, z, P["RED_CONCRETE"])
        elif abs(x) == 3 and 65 <= z <= 84:
            s(x, 99, z, P["GOLD_BLOCK"] if z % 2 == 0 else P["POLISHED_DIORITE"])
        elif (x + z) % 2 == 0:
            s(x, 99, z, P["POLISHED_DIORITE"])
        else:
            s(x, 99, z, P["SMOOTH_QUARTZ"])

# Clear Interior Volume first
fill_box(PX1, PY1, PZ1, PX2, PY2 + 6, PZ2, P["AIR"])

# Outer Palace Walls (Gothic Quartz Pillars, Deepslate Trim & Tall Stained-Glass Windows!)
for y in range(PY1, PY2 + 1):
    for x in range(PX1, PX2 + 1):
        for z in (PZ1, PZ2):
            if abs(x) % 6 == 0 or y in (100, 107, 114, PY2):
                mat = P["DARK_PRISMARINE"] if y in (107, 114, PY2) else P["QUARTZ_PILLAR"]
            elif 102 <= y <= 105 or 109 <= y <= 112 or 116 <= y <= 119:
                mat = P["CYAN_STAINED_GLASS"] if (x + y) % 2 == 0 else P["LIGHT_BLUE_STAINED_GLASS"]
            else:
                mat = P["SMOOTH_QUARTZ"]
            s(x, y, z, mat)
    for z in range(PZ1, PZ2 + 1):
        for x in (PX1, PX2):
            if (z - PZ1) % 6 == 0 or y in (100, 107, 114, PY2):
                mat = P["DARK_PRISMARINE"] if y in (107, 114, PY2) else P["QUARTZ_PILLAR"]
            elif 102 <= y <= 105 or 109 <= y <= 112 or 116 <= y <= 119:
                mat = P["CYAN_STAINED_GLASS"] if (z + y) % 2 == 0 else P["BLUE_STAINED_GLASS"]
            else:
                mat = P["SMOOTH_QUARTZ"]
            s(x, y, z, mat)

# 4 Corner Palace Bell-Towers with Conical Dark-Prismarine & Ice Spires!
for tx in (PX1, PX2):
    for tz in (PZ1, PZ2):
        fill_cyl(tx, tz, 3.2, 100, 125, P["STONE_BRICKS"])
        fill_cyl(tx, tz, 2.2, 101, 124, P["SMOOTH_QUARTZ"])
        for ry in range(126, 135):
            rr = max(0.6, (135 - ry) * 0.42)
            fill_cyl(tx, tz, rr, ry, ry, P["DARK_PRISMARINE"] if ry < 133 else P["BLUE_ICE"])
        s(tx, 135, tz, P["SEA_LANTERN"])

# Vaulted Cathedral Roof (NO flat yellow ceiling! Ribbed Dark-Prismarine + Quartz Vaults Y=122..127!)
for z in range(PZ1 - 1, PZ2 + 2):
    for x in range(PX1 - 1, PX2 + 2):
        roof_rise = max(0, 5 - abs(x) // 4)
        ry = PY2 + roof_rise
        s(x, ry, z, P["DEEPSLATE_TILES"] if z % 4 == 0 else P["DARK_PRISMARINE"])
        # Warm vaulted interior ribs underneath
        if PX1 < x < PX2 and PZ1 < z < PZ2:
            s(x, ry - 1, z, P["QUARTZ_PILLAR"] if z % 6 == 0 else P["SPRUCE_PLANKS"])
            if z % 6 == 0 and abs(x) in (0, 8, 15):
                s(x, ry - 2, z, P["SEA_LANTERN"])

# Grand Arched Main Entrance on North Wall (Z=65..66, X=-4..4, Y=100..105)
for x in range(-4, 5):
    arch_top = 105 - (abs(x) // 3)
    fill_box(x, 100, PZ1 - 1, x, arch_top, PZ1 + 1, P["AIR"])
    s(x, arch_top + 1, PZ1, P["GOLD_BLOCK"])
for sx in (-5, 5):
    fill_box(sx, 100, PZ1 - 1, sx, 106, PZ1, P["QUARTZ_PILLAR"])
    s(sx, 107, PZ1 - 1, P["SEA_LANTERN"])

# ---------------- FLOOR 1 INTERIOR (Y=100..106 — IMPERIAL THRONE HALL) ----------------
# 6 Fluted Interior Marble Columns with Lantern Sconces
for cx in (-10, 10):
    for cz in (70, 76, 82):
        fill_box(cx, 100, cz, cx, 106, cz, P["QUARTZ_PILLAR"])
        s(cx, 100, cz, P["CHISELED_QUARTZ_BLOCK"])
        s(cx, 106, cz, P["CHISELED_QUARTZ_BLOCK"])
        s(cx + (1 if cx < 0 else -1), 104, cz, P["SEA_LANTERN"])

# Gift #3 Star Pedestal at (0, 100, 74) — SpawnEngine places Gift #3 shulker at (0, 100, 74)!
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) + abs(dz) <= 3:
            s(dx, 99, 74 + dz, P["CHISELED_QUARTZ_BLOCK"])
s(0, 99, 74, P["GOLD_BLOCK"])

# Grand Imperial Ruby-Gold Throne Dais at South Wall (X=-5..5, Z=84..89)
fill_box(-5, 100, 85, 5, 100, 89, P["POLISHED_DIORITE"])
fill_box(-3, 100, 84, 3, 100, 89, P["RED_CONCRETE"])
fill_box(-3, 101, 86, 3, 101, 89, P["SMOOTH_QUARTZ"])
fill_box(-1, 101, 85, 1, 101, 89, P["RED_CONCRETE"])
# Sculpted King's Throne Chair
fill_box(-2, 102, 88, 2, 105, 89, P["GOLD_BLOCK"])
fill_box(-1, 102, 88, 1, 104, 88, P["RED_WOOL"])
s(-2, 102, 87, P["GOLD_BLOCK"])
s(2, 102, 87, P["GOLD_BLOCK"])
s(0, 106, 88, P["DIAMOND_BLOCK"])
s(-4, 102, 86, P["EMERALD_BLOCK"])
s(-4, 103, 86, P["SHROOMLIGHT"])
s(4, 102, 86, P["EMERALD_BLOCK"])
s(4, 103, 86, P["SHROOMLIGHT"])

# Floor 1 West Wing (X=-19..-12, Z=68..75): Royal Fireplace & Banquet Lounge
fill_box(-20, 100, 70, -19, 104, 74, P["DEEPSLATE_BRICKS"])
fill_box(-19, 100, 71, -19, 102, 73, P["MAGMA_BLOCK"])
fill_box(-19, 101, 71, -19, 102, 73, P["SHROOMLIGHT"])
fill_box(-18, 100, 71, -18, 102, 73, P["IRON_BARS"])
# Banquet Table & Velvet Chairs
fill_box(-15, 100, 69, -13, 100, 73, P["SPRUCE_PLANKS"])
s(-14, 101, 71, P["LANTERN"])
for cz in (69, 71, 73):
    s(-16, 100, cz, P["RED_WOOL"])
    s(-12, 100, cz, P["RED_WOOL"])
# Utility Chests & Crafting
s(-19, 100, 68, P["ENDER_CHEST"])
s(-18, 100, 68, P["CRAFTING_TABLE"])

# Floor 1 East Wing (X=12..19, Z=68..75): Royal Armory, Smithing & Mini Pine Decor
fill_box(18, 100, 68, 19, 103, 73, P["BOOKSHELF"])
s(17, 100, 69, P["ANVIL"])
s(17, 100, 71, P["SMITHING_TABLE"])
s(17, 100, 73, P["ENDER_CHEST"])
# Indoor Festive Mini-Spruce Trees (REAL SPRUCE_LEAVES!)
for mx, mz in [(-8, 68), (8, 68), (-8, 86), (8, 86)]:
    s(mx, 100, mz, P["BARREL"])
    fill_box(mx, 101, mz, mx, 103, mz, P["SPRUCE_LEAVES"])
    s(mx, 104, mz, P["OCHRE_FROGLIGHT"])

# ---------------- FLOOR 2 SLAB (Y=107) + CENTRAL ATRIUM + TWIN STAIRCASES 1->2 ----------------
for x in range(PX1 + 1, PX2):
    for z in range(PZ1 + 1, PZ2):
        # Central Open Atrium (X=-7..7, Z=71..82)
        if -7 <= x <= 7 and 71 <= z <= 82:
            continue
        # Stairwell 1->2 cutouts on West (X=-18..-16, Z=77..84) & East (X=16..18, Z=77..84)
        if (abs(x) in (16, 17, 18)) and 77 <= z <= 84:
            continue
        s(x, 107, z, P["SPRUCE_PLANKS"] if (x + z) % 2 == 0 else P["DARK_OAK_PLANKS"])

# Atrium Balustrade on Floor 2 (Y=108 around X=-8..8, Z=70..83)
for x in range(-8, 9):
    for z in (70, 83):
        s(x, 107, z, P["DEEPSLATE_TILES"])
        s(x, 108, z, P["QUARTZ_PILLAR"] if x % 4 == 0 else P["IRON_BARS"])
        if x % 4 == 0:
            s(x, 109, z, P["LANTERN"])
for z in range(70, 84):
    for x in (-8, 8):
        s(x, 107, z, P["DEEPSLATE_TILES"])
        s(x, 108, z, P["QUARTZ_PILLAR"] if (z - 70) % 4 == 0 else P["IRON_BARS"])

# Twin Grand Staircases Floor 1 (Y=100, Z=84) -> Floor 2 (Y=107, Z=77)
for sx_range in (range(-18, -15), range(16, 19)):
    for step_i in range(8):
        sz = 84 - step_i
        sy = 100 + step_i
        for sx in sx_range:
            fill_box(sx, 100, sz, sx, sy, sz, P["SMOOTH_QUARTZ"] if step_i % 2 == 0 else P["POLISHED_DIORITE"])
            fill_box(sx, sy + 1, sz, sx, sy + 5, sz, P["AIR"])
    # Safety railing along inner edge of stairwell
    rail_x = -15 if sx_range[0] < 0 else 15
    for sz in range(78, 85):
        if g(rail_x, 108, sz) == P["AIR"]:
            s(rail_x, 108, sz, P["IRON_BARS"])

# Floor 2 Furnishings (Y=108..113): Royal Library (West) & VIP Alchemy Lounge (East)!
# West Library Stacks & Enchanting Nook
fill_box(-20, 108, 67, -19, 112, 74, P["BOOKSHELF"])
fill_box(-14, 108, 72, -13, 110, 74, P["BOOKSHELF"])
s(-16, 108, 70, P["ENCHANTING_TABLE"])
s(-16, 107, 70, P["GOLD_BLOCK"])
s(-11, 108, 75, P["CARTOGRAPHY_TABLE"])
fill_box(-12, 108, 78, -10, 108, 80, P["RED_CARPET"])

# East VIP Alchemy Bar & Velvet Seating
fill_box(19, 108, 68, 20, 111, 74, P["BOOKSHELF"])
fill_box(16, 108, 69, 16, 108, 73, P["DARK_PRISMARINE"])
s(16, 109, 70, P["BREWING_STAND"])
s(16, 109, 72, P["BREWING_STAND"])
s(18, 108, 70, P["CAULDRON"])
s(18, 108, 72, P["ENDER_CHEST"])
for sz in (75, 77, 79):
    s(10, 108, sz, P["RED_WOOL"])
fill_box(0, 108, 68, 0, 108, 69, P["RED_CARPET"])

# ---------------- FLOOR 3 SLAB (Y=114) + TREASURY + WALK-OUT NORTH BALCONY ----------------
for x in range(PX1 + 1, PX2):
    for z in range(PZ1 + 1, PZ2):
        # Central Upper Atrium Opening (X=-6..6, Z=72..81)
        if -6 <= x <= 6 and 72 <= z <= 81:
            continue
        # Stairwell 2->3 cutouts at X=-13..-11 & X=11..13, Z=78..84
        if (11 <= abs(x) <= 13) and 78 <= z <= 84:
            continue
        s(x, 114, z, P["POLISHED_DIORITE"] if (x + z) % 2 == 0 else P["SMOOTH_QUARTZ"])

# Atrium Balustrade on Floor 3 (Y=115 around X=-7..7, Z=71..82)
for x in range(-7, 8):
    for z in (71, 82):
        s(x, 114, z, P["GOLD_BLOCK"] if x % 3 == 0 else P["DEEPSLATE_TILES"])
        s(x, 115, z, P["IRON_BARS"])
for z in range(71, 83):
    for x in (-7, 7):
        s(x, 114, z, P["DEEPSLATE_TILES"])
        s(x, 115, z, P["IRON_BARS"])

# Twin Upper Staircases Floor 2 (Y=107, Z=84) -> Floor 3 (Y=114, Z=77)
for sx_range in (range(-13, -10), range(11, 14)):
    for step_i in range(7):
        sz = 83 - step_i
        sy = 108 + step_i
        for sx in sx_range:
            fill_box(sx, 108, sz, sx, sy, sz, P["SMOOTH_QUARTZ"] if step_i % 2 == 0 else P["POLISHED_DIORITE"])
            fill_box(sx, sy + 1, sz, sx, sy + 5, sz, P["AIR"])

# Floor 3 King's Treasury Glass Vaults (West & East)
for side in (-1, 1):
    vx = side * 18
    fill_box(vx - 1, 115, 68, vx + 1, 118, 74, P["CYAN_STAINED_GLASS"])
    s(vx, 115, 69, P["DIAMOND_BLOCK"])
    s(vx, 115, 71, P["EMERALD_BLOCK"])
    s(vx, 116, 71, P["GOLD_BLOCK"])
    s(vx, 115, 73, P["AMETHYST_BLOCK"])
    s(vx, 118, 71, P["SEA_LANTERN"])

# Colossal Hanging Crystal Chandelier cascading down the 3-Floor Atrium (X=0, Z=76, Y=110..125)!
fill_box(0, 113, 76, 0, 125, 76, P["IRON_BARS"])
fill_box(-1, 114, 75, 1, 116, 77, P["LIGHT_BLUE_STAINED_GLASS"])
fill_box(0, 113, 76, 0, 117, 76, P["SEA_LANTERN"])
for arm in (-3, -2, 2, 3):
    s(arm, 115, 76, P["IRON_BARS"])
    s(0, 115, 76 + arm, P["IRON_BARS"])
    if abs(arm) == 3:
        s(arm, 116, 76, P["END_ROD"])
        s(0, 116, 76 + arm, P["END_ROD"])
        s(arm, 114, 76, P["OCHRE_FROGLIGHT"])
        s(0, 114, 76 + arm, P["OCHRE_FROGLIGHT"])
s(0, 111, 76, P["END_ROD"])
s(0, 112, 76, P["SEA_LANTERN"])

# Walk-Out North Panoramic Royal Balcony on Floor 3 (X=-8..8, Y=114, Z=60..65)!
fill_box(-3, 115, PZ1 - 1, 3, 119, PZ1 + 1, P["AIR"])  # Arched balcony doorway
fill_box(-8, 113, 60, 8, 114, 65, P["SMOOTH_QUARTZ"])
fill_box(-7, 114, 61, 7, 114, 65, P["RED_CARPET"] if False else P["POLISHED_DIORITE"])
for x in range(-2, 3):
    for z in range(61, 67):
        s(x, 114, z, P["RED_CONCRETE"])
for x in range(-8, 9):
    s(x, 115, 60, P["QUARTZ_PILLAR"] if x % 4 == 0 else P["IRON_BARS"])
    if x % 4 == 0:
        s(x, 116, 60, P["SEA_LANTERN"])
for z in range(60, 65):
    s(-8, 115, z, P["IRON_BARS"])
    s(8, 115, z, P["IRON_BARS"])
# Corbel arches supporting the balcony above the main entrance
for sx in (-7, 7):
    fill_box(sx, 108, 62, sx, 113, 64, P["QUARTZ_PILLAR"])


# ==============================================================================
# 7. CENTRAL MARKET STALLS, ALLEY OF FAME (/warp top) & SNOWMAN PLAZA
# ==============================================================================
print("[7/11] Furnishing Festive Market Stalls, Alley of Fame & Street Decor...")


def build_festive_stall(cx, cz, facing_x, stripe_mat):
    bx = cx - facing_x * 4
    fill_box(bx - 1, 100, cz - 2, bx + 1, 100, cz + 2, P["DARK_OAK_PLANKS"])
    for sz in (cz - 2, cz + 2):
        fill_box(bx - 1, 100, sz, bx - 1, 103, sz, P["SPRUCE_LOG"])
        fill_box(bx + 1, 100, sz, bx + 1, 103, sz, P["SPRUCE_LOG"])
    for dx in range(-2, 3):
        for dz in range(-3, 4):
            s(bx + dx, 104, cz + dz, stripe_mat if (dx + dz) % 2 == 0 else P["WHITE_WOOL"])
    s(bx, 103, cz, P["SHROOMLIGHT"])


build_festive_stall(25, 12, -1, P["RED_WOOL"])
build_festive_stall(25, 5, -1, P["LIGHT_BLUE_CONCRETE"])
build_festive_stall(-25, 9, 1, P["ORANGE_CONCRETE"])
build_festive_stall(-25, -1, 1, P["VERDANT_FROGLIGHT"])
build_festive_stall(-23, 28, 1, P["YELLOW_CONCRETE"])
build_festive_stall(23, 28, -1, P["PURPLE_CONCRETE"])

# Ornate Streetlights along Central Plazas (clear of NPC spots!)
LAMP_SPOTS = [(-11, 48), (11, 48), (-20, 38), (20, 38), (-21, 18), (21, 18), (-20, -10), (20, -10)]
for lx, lz in LAMP_SPOTS:
    s(lx, 100, lz, P["DEEPSLATE_BRICKS"])
    fill_box(lx, 101, lz, lx, 104, lz, P["SPRUCE_FENCE"])
    s(lx, 105, lz, P["SEA_LANTERN"])
    s(lx, 106, lz, P["DARK_PRISMARINE"])

# Alley of Fame Classical Pantheon Colonnade behind Top-5 Boards (-38 & -30, Z=-35..-38)
fill_box(-43, 100, -37, -25, 100, -35, P["POLISHED_DIORITE"])
for px in (-42, -38, -34, -30, -26):
    fill_box(px, 101, -36, px, 107, -36, P["QUARTZ_PILLAR"])
    s(px, 108, -36, P["GOLD_BLOCK"])
fill_box(-43, 108, -37, -25, 109, -35, P["SMOOTH_QUARTZ"])
fill_box(-41, 110, -36, -27, 110, -36, P["GOLD_BLOCK"])
s(-38, 107, -35, P["SEA_LANTERN"])
s(-30, 107, -35, P["SEA_LANTERN"])

# Sculpted Festive Snowman at (-34, 100, 23) near Gift #2 (-31, 100, 21)
fill_cyl(-35, 23, 3.0, 100, 104, P["SNOW_BLOCK"])
fill_cyl(-35, 23, 2.3, 105, 108, P["SNOW_BLOCK"])
fill_cyl(-35, 23, 1.7, 109, 111, P["SNOW_BLOCK"])
fill_cyl(-35, 23, 2.5, 112, 112, P["BLACK_CONCRETE"])
fill_cyl(-35, 23, 1.6, 113, 115, P["BLACK_CONCRETE"])
s(-33, 110, 23, P["ORANGE_CONCRETE"])


# ==============================================================================
# 8. NORTH SHULKER CASE TERRACE WITH TALL GLACIAL CROWN ARCHES (/warp cases)
# ==============================================================================
print("[8/11] Building North Shulker Case Amphitheater & Glacial Crown Backdrop...")

CASE_COORDS = [
    (-30, -58), (-24, -63), (-18, -67), (-12, -70), (-6, -72),
    (0, -73), (6, -72), (12, -70), (18, -67), (24, -63), (30, -58)
]
for idx, (cx, cz) in enumerate(CASE_COORDS):
    fill_box(cx - 1, 101, cz - 1, cx + 1, 101, cz + 1, P["DEEPSLATE_TILES"])
    s(cx, 102, cz, P["GOLD_BLOCK"] if idx % 2 == 0 else P["EMERALD_BLOCK"])
    # Tall Glacial & Marble Arch Column behind each Shulker Pedestal (4 blocks further North!)
    ang = math.atan2(cz + 48, cx)
    bx = int(round(cx + math.cos(ang) * 5.0))
    bz = int(round(cz + math.sin(ang) * 5.0))
    # Deliberate slender architectural columns behind each pedestal, not wall spires.
    fill_box(bx, 102, bz, bx, 107, bz, P["QUARTZ_PILLAR"])
    s(bx, 108, bz, P["SEA_LANTERN"])
    s(bx, 109, bz, P["DARK_PRISMARINE"])


# ==============================================================================
# 9. EAST GLADIATOR PVP COLOSSEUM, TRAINING ARMORY & 8 DUEL KABINKI
# ==============================================================================
print("[9/11] Constructing Roman-Winter PvP Colosseum, Training Hall & 8 Duel Kabinki...")

# Sunken PvP Arena Pit (X=67..101, Z=5..37, floor Y=91)
fill_box(66, 90, 4, 102, 99, 38, P["DEEPSLATE_BRICKS"])
fill_box(68, 92, 6, 100, 108, 36, P["AIR"])
for x in range(68, 101):
    for z in range(6, 37):
        d_c = math.hypot(x - 84, z - 21)
        if abs(d_c - 9.0) < 0.8 or x == 84 or z == 21:
            s(x, 91, z, P["RED_CONCRETE"])
        elif (x + z) % 2 == 0:
            s(x, 91, z, P["SMOOTH_QUARTZ"])
        else:
            s(x, 91, z, P["POLISHED_DIORITE"])

# Central Gladiator Pillar + Corner Cobwebs/Utilities
fill_box(83, 92, 20, 85, 95, 22, P["QUARTZ_PILLAR"])
s(84, 96, 21, P["SEA_LANTERN"])
for cx, cz in [(70, 8), (98, 8), (70, 34), (98, 34)]:
    s(cx, 92, cz, P["COBWEB"])
s(65, 100, 11, P["ENDER_CHEST"])
s(65, 100, 12, P["ANVIL"])
s(65, 100, 23, P["ENDER_CHEST"])
s(65, 100, 24, P["ANVIL"])

# Grand Tiered Colosseum Arches around North, East & South Rim (X=66..103, Z=3..39, Y=100..115)
for x in range(68, 103, 4):
    for z in (3, 39):
        fill_box(x - 1, 100, z, x + 1, 112, z, P["DEEPSLATE_BRICKS"])
        fill_box(x, 101, z, x, 111, z, P["QUARTZ_PILLAR"])
        s(x, 113, z, P["SEA_LANTERN"])
for z in range(5, 38, 4):
    fill_box(103, 100, z - 1, 103, 114, z + 1, P["DEEPSLATE_BRICKS"])
    fill_box(103, 101, z, 103, 113, z, P["QUARTZ_PILLAR"])
    s(103, 115, z, P["SHROOMLIGHT"])
fill_box(68, 111, 3, 103, 112, 3, P["RED_CONCRETE"])
fill_box(68, 111, 39, 103, 112, 39, P["RED_CONCRETE"])
fill_box(103, 112, 3, 103, 113, 39, P["RED_CONCRETE"])

# WIDE UNBLOCKED ENTRY + 3 GRAND STAIRCASES INTO PVP PIT (Y=99 -> Y=92)!
fill_box(58, 100, 13, 67, 107, 22, P["AIR"])
for step in range(8):
    sx = 67 + step
    sy = 99 - step
    for z in range(14, 22):
        fill_box(sx, 91, z, sx, sy, z, P["SMOOTH_QUARTZ"] if step % 2 == 0 else P["RED_CONCRETE"])
        fill_box(sx, sy + 1, z, sx, 106, z, P["AIR"])
    for x in range(79, 85):
        sz_n = 5 + step
        fill_box(x, 91, sz_n, x, sy, sz_n, P["SMOOTH_QUARTZ"])
        fill_box(x, sy + 1, sz_n, x, 106, sz_n, P["AIR"])
        sz_s = 37 - step
        fill_box(x, 91, sz_s, x, sy, sz_s, P["SMOOTH_QUARTZ"])
        fill_box(x, sy + 1, sz_s, x, 106, sz_s, P["AIR"])

# Training Armory Canopy at /warp train (66.5, 100, -5/-3/-1)
fill_box(65, 99, -7, 69, 99, 1, P["DEEPSLATE_TILES"])
for dz in (-5, -3, -1):
    s(66, 99, dz, P["GOLD_BLOCK"])
    s(68, 100, dz, P["HAY_BLOCK"])
    s(68, 101, dz, P["HAY_BLOCK"])
for cz in (-7, 1):
    fill_box(68, 100, cz, 68, 104, cz, P["SPRUCE_LOG"])
fill_box(65, 105, -7, 69, 105, 1, P["DARK_PRISMARINE"])
s(67, 104, -3, P["SEA_LANTERN"])

# 8 Glass Duel Kabinki (6x 1v1 + 2x 2v2) — exact door coords preserved!
for zc in [-42, -35, -28, -21, -14, -7]:
    fill_box(68, 99, zc - 2, 74, 99, zc + 2, P["POLISHED_DIORITE"])
    fill_box(68, 100, zc - 2, 74, 103, zc + 2, P["CYAN_STAINED_GLASS"])
    fill_box(68, 104, zc - 2, 74, 104, zc + 2, P["DARK_PRISMARINE"])
    s(71, 104, zc, P["SEA_LANTERN"])
    fill_box(69, 100, zc - 1, 73, 103, zc + 1, P["AIR"])
    s(68, 100, zc, P["AIR"])
    s(68, 101, zc, P["AIR"])
    s(68, 102, zc, P["GOLD_BLOCK"])

for zc in [-32, -16]:
    fill_box(82, 99, zc - 3, 90, 99, zc + 3, P["POLISHED_DIORITE"])
    fill_box(82, 100, zc - 3, 90, 103, zc + 3, P["MAGENTA_STAINED_GLASS"])
    fill_box(82, 104, zc - 3, 90, 104, zc + 3, P["DARK_PRISMARINE"])
    s(86, 104, zc, P["SEA_LANTERN"])
    fill_box(83, 100, zc - 2, 89, 103, zc + 2, P["AIR"])
    s(82, 100, zc, P["AIR"])
    s(82, 101, zc, P["AIR"])
    s(82, 102, zc, P["GOLD_BLOCK"])


# ==============================================================================
# 10. WEST CRYSTAL AUTO-MINE, NW MOB FARM, SW CASINO 777 & SE BAFOVAR CATHEDRAL
# ==============================================================================
print("[10/11] Detailing Crystal Auto-Mine Cavern, Mob Farm, Casino 777 & Bafovar...")

# West Crystal Auto-Mine (X=-96..-75, Y=91..98, Z=-6..14) + Giant Amethyst/Ice Cavern Arch!
fill_box(-98, 90, -8, -73, 99, 16, P["DEEPSLATE_BRICKS"])
ORES = [P["COAL_ORE"], P["IRON_ORE"], P["GOLD_ORE"], P["DIAMOND_ORE"], P["EMERALD_ORE"], P["LAPIS_ORE"]]
for x in range(-96, -74):
    for y in range(91, 99):
        for z in range(-6, 15):
            s(x, y, z, ORES[(x * 7 + y * 13 + z * 5) % len(ORES)])
# Sculpted Dwarven Crystal Portal Arch over the Auto-Mine!
for z in range(-8, 17):
    arch_h = 109 + int(5 * math.cos((z - 4) * 0.22))
    fill_box(-98, 100, z, -97, arch_h, z, P["DEEPSLATE_BRICKS"] if z % 3 else P["AMETHYST_BLOCK"])
    s(-97, arch_h + 1, z, P["BLUE_ICE"])
for x in range(-98, -73, 5):
    for z in (-8, 16):
        # Pillars form the mine's own boundary; they cannot grow into the pit.
        fill_box(x, 100, z, x, 105, z, P["DEEPSLATE_BRICKS"])
        s(x, 106, z, P["SEA_LANTERN"])
for y in range(91, 101):
    s(-74, y, 4, P["QUARTZ_PILLAR"])
s(-73, 100, 0, P["ENDER_CHEST"])
s(-73, 100, 9, P["ANVIL"])

# NW Safe Mob Farm Fortress (4 Chambers at X=-92, -84, -76, -68, Z=-42)
fill_box(-96, 100, -47, -64, 107, -38, P["DEEPSLATE_BRICKS"])
fill_box(-97, 108, -48, -63, 108, -37, P["DARK_PRISMARINE"])
for cx in [-92, -84, -76, -68]:
    fill_box(cx - 2, 100, -45, cx + 2, 105, -40, P["AIR"])
    s(cx, 106, -42, P["SEA_LANTERN"])
    fill_box(cx - 1, 100, -39, cx + 1, 100, -38, P["AIR"])
    fill_box(cx - 1, 101, -39, cx + 1, 101, -38, P["DEEPSLATE_TILES"])
    fill_box(cx - 1, 102, -39, cx + 1, 104, -38, P["CYAN_STAINED_GLASS"])

# SW Grand Casino 777 Dome (-66, 100, 68) — Rich Interior + Roulette Inlay + Slot Machines!
fill_cyl(-68, 68, 13, 100, 111, P["DEEPSLATE_BRICKS"])
fill_cyl(-68, 68, 11, 100, 110, P["AIR"])
for ry in range(111, 119):
    rr = max(1.5, 13.0 - (ry - 110) * 1.45)
    fill_cyl(-68, 68, rr, ry, ry, P["GOLD_BLOCK"] if ry % 2 == 0 else P["RED_CONCRETE"])
s(-68, 119, 68, P["SEA_LANTERN"])
fill_box(-60, 100, 64, -54, 106, 72, P["AIR"])  # Wide East entrance
# Roulette Carpet Floor & Chandelier inside Casino
for dx in range(-8, 9):
    for dz in range(-8, 9):
        if dx * dx + dz * dz <= 49:
            s(-68 + dx, 99, 68 + dz, P["RED_CONCRETE"] if (dx + dz) % 2 == 0 else P["BLACK_CONCRETE"])
fill_box(-68, 107, 68, -68, 110, 68, P["IRON_BARS"])
s(-68, 106, 68, P["SEA_LANTERN"])
for sz in [63, 66, 70, 73]:
    s(-76, 100, sz, P["GOLD_BLOCK"])
    s(-76, 101, sz, P["NOTE_BLOCK"])
    s(-76, 102, sz, P["SEA_LANTERN"])
    s(-75, 101, sz, P["LEVER"])

# SE Bafovar & Wizard Cathedral (66, 100, 68) — Amethyst Spire, Bookshelves & Enchanting!
fill_cyl(68, 68, 13, 100, 111, P["DEEPSLATE_BRICKS"])
fill_cyl(68, 68, 11, 100, 110, P["AIR"])
for ry in range(111, 122):
    rr = max(1.0, 13.0 - (ry - 110) * 1.15)
    fill_cyl(68, 68, rr, ry, ry, P["PURPLE_CONCRETE"] if ry % 2 == 0 else P["AMETHYST_BLOCK"])
s(68, 122, 68, P["SEA_LANTERN"])
fill_box(54, 100, 64, 60, 106, 72, P["AIR"])  # Wide West entrance
for dx in range(-2, 3):
    for dz in range(-2, 3):
        if abs(dx) == 2 or abs(dz) == 2:
            if not (dx == -2 and abs(dz) <= 1):
                fill_box(70 + dx, 100, 68 + dz, 70 + dx, 102, 68 + dz, P["BOOKSHELF"])
s(70, 100, 68, P["ENCHANTING_TABLE"])
s(73, 100, 63, P["BREWING_STAND"])
s(73, 100, 73, P["ANVIL"])
s(71, 100, 73, P["ENDER_CHEST"])


# ==============================================================================
# 11. COMFORTABLE, SCENIC 32-ISLAND SKY PARKOUR (/warp parkour -> Y=129)
#     ("сделай паркур нормальным")
# ==============================================================================
print("[11/11] Building Tested 32-Island Scenic Sky Parkour (2x2 & 3x3 Pads, Zero Collisions)...")

PARKOUR_PAD_MATS = [
    P["PACKED_ICE"], P["LIGHT_BLUE_CONCRETE"], P["CYAN_CONCRETE"],
    P["PRISMARINE_BRICKS"], P["AMETHYST_BLOCK"], P["PURPLE_CONCRETE"],
    P["MAGENTA_CONCRETE"], P["SMOOTH_QUARTZ"],
]

# Carefully parametric sky-arc starting right at /warp parkour (38.5, 100, -24.5)!
# Step #0 is at (39, 101, -29) — just 4 blocks North of /warp parkour!
PARKOUR_STEPS = []
px_f, pz_f = 39.0, -29.0
py_i = 101
ang_cur = math.atan2(pz_f, px_f)  # ~ -0.64 rad
rad_cur = math.hypot(px_f, pz_f)  # ~ 48.6

for i in range(32):
    ix, iz = int(round(px_f)), int(round(pz_f))
    PARKOUR_STEPS.append((ix, py_i, iz))
    # Advance ~3.85 blocks along a smooth counter-clockwise ascending sky helix (R=45..41)
    target_r = 45.5 - i * 0.14
    d_theta = -3.85 / max(36.0, rad_cur)
    ang_cur += d_theta
    rad_cur = rad_cur * 0.7 + target_r * 0.3
    px_f = math.cos(ang_cur) * rad_cur
    pz_f = math.sin(ang_cur) * rad_cur
    if i % 9 != 4:  # +1 Y on most steps, flat breather every 9th step -> Y=101 -> Y=129!
        py_i += 1

# Verify every single parkour jump distance BEFORE placing blocks!
for i in range(len(PARKOUR_STEPS) - 1):
    x1, y1, z1 = PARKOUR_STEPS[i]
    x2, y2, z2 = PARKOUR_STEPS[i + 1]
    horiz = math.hypot(x2 - x1, z2 - z1)
    dy = y2 - y1
    assert 2.8 <= horiz <= 4.5, f"Bad parkour horiz {horiz:.2f} at step {i}->{i+1}"
    assert 0 <= dy <= 1, f"Bad parkour dy {dy} at step {i}->{i+1}"

# Clear generous air envelope around EVERY parkour step first, then sculpt lush sky pads!
for idx, (px, py, pz) in enumerate(PARKOUR_STEPS):
    fill_box(px - 2, py + 1, pz - 2, px + 2, py + 6, pz + 2, P["AIR"])

for idx, (px, py, pz) in enumerate(PARKOUR_STEPS):
    is_start = (idx == 0)
    is_cp = idx in (8, 16, 24)
    is_finish = (idx == 31)

    if is_finish:
        # Grand 5x5 Trophy Summit Island!
        fill_box(px - 2, py, pz - 2, px + 2, py, pz + 2, P["SMOOTH_QUARTZ"])
        fill_box(px - 1, py, pz - 1, px + 1, py, pz + 1, P["DIAMOND_BLOCK"])
        fill_box(px - 1, py - 2, pz - 1, px + 1, py - 1, pz + 1, P["PACKED_ICE"])
        s(px, py, pz, P["EMERALD_BLOCK"])
        s(px, py + 1, pz, P["HEAVY_WEIGHTED_PRESSURE_PLATE"])
        for dx in (-2, 2):
            for dz in (-2, 2):
                s(px + dx, py + 1, pz + dz, P["QUARTZ_PILLAR"])
                s(px + dx, py + 2, pz + dz, P["SEA_LANTERN"])
    elif is_start or is_cp:
        # Comfortable 3x3 Golden Checkpoint Cloud Pad!
        fill_box(px - 1, py, pz - 1, px + 1, py, pz + 1, P["SMOOTH_QUARTZ"])
        fill_box(px - 1, py - 1, pz - 1, px + 1, py - 1, pz + 1, P["PACKED_ICE"])
        s(px, py - 2, pz, P["BLUE_ICE"])
        s(px, py, pz, P["GOLD_BLOCK"])
        s(px, py + 1, pz, P["LIGHT_WEIGHTED_PRESSURE_PLATE"])
        s(px - 1, py, pz - 1, P["SEA_LANTERN"])
        s(px + 1, py, pz + 1, P["SEA_LANTERN"])
    else:
        # Comfortable 2x2 Cushioned Sky Pad with Ice Icicle Underside!
        pad_mat = PARKOUR_PAD_MATS[idx % len(PARKOUR_PAD_MATS)]
        fill_box(px, py, pz, px + 1, py, pz + 1, pad_mat)
        s(px, py, pz, P["SNOW_BLOCK"] if idx % 3 == 0 else pad_mat)
        s(px, py - 1, pz, P["PACKED_ICE"])
        s(px + 1, py - 1, pz + 1, P["BLUE_ICE"])
        if idx % 4 == 0:
            s(px + 1, py - 1, pz, P["SEA_LANTERN"])

# Stepping stone from plaza Y=99 up to Start Pad #0 (39, 101, -29)
fill_box(38, 100, -27, 40, 100, -27, P["SMOOTH_QUARTZ"])
s(39, 100, -27, P["GOLD_BLOCK"])


# 12. The old floating aurora and repetitive arch spam are intentionally omitted.
# Human-scale lanterns, seating and planting are added after the reservations below.

# ==============================================================================
# MANDATORY WALKABILITY GUARANTEES & 3D BFS VERIFICATION (INCL. ALL 3 PALACE FLOORS!)
# ==============================================================================
WARP_SPOTS = [
    ("spawn", 0, 100, 52),
    ("wheel", 0, 100, 40),
    ("pvp", 61, 100, 17),
    ("train", 61, 100, -3),
    ("kabinki", 63, 100, -24),
    ("parkour", 38, 100, -24),
    ("mine", -68, 100, 4),
    ("farm", -80, 100, -35),
    ("casino", -59, 100, 68),
    ("bafs", 59, 100, 68),
    ("cases", 0, 102, -55),
    ("top", -34, 100, -28),
    ("palace_f1", 0, 100, 78),
    ("palace_f2", 0, 108, 69),
    ("palace_f3_balcony", 0, 115, 62),
]

NPC_SPOTS = [
    (-8, 100, 42), (8, 100, 42), (0, 100, 36), (-13, 100, 34), (13, 100, 34),
    (-16, 100, 25), (16, 100, 25), (-16, 100, 14), (16, 100, 14),
    (-14, 100, -15), (14, 100, -15), (-8, 100, 19), (8, 100, 19),
    (25, 100, 12), (25, 100, 5), (-25, 100, 9), (-25, 100, -1),
    (-23, 100, 28), (23, 100, 28), (-34, 100, -26), (62, 100, -3),
    (0, 102, -53), (-66, 100, 4), (-78, 100, -34), (-66, 100, 68),
    (66, 100, 68), (35, 100, -24),
]

GIFT_SPOTS = [
    (0, 100, 24), (-31, 100, 21), (0, 100, 74), (-26, 102, -54), (41, 100, -21),
    (63, 100, 34), (80, 100, -24), (-70, 100, 13), (-74, 100, 68), (75, 100, 68),
]

# New street furniture and winter planting: placement requires solid floor,
# completely clear volume and a 3-block exclusion around every NPC/warp/gift.
# The test below also means decorations never replace any building or wall.
protected_positions = [(x,z) for _,x,_,z in WARP_SPOTS] + [(x,z) for x,_,z in NPC_SPOTS] + [(x,z) for x,_,z in GIFT_SPOTS]
def safe_decor(cx,cz,rx,rz,height):
    if any(abs(cx-px)<=rx+3 and abs(cz-pz)<=rz+3 for px,pz in protected_positions): return False
    for x in range(cx-rx,cx+rx+1):
        for z in range(cz-rz,cz+rz+1):
            if g(x,99,z)==P["AIR"]: return False
            if any(g(x,y,z)!=P["AIR"] for y in range(100,100+height)): return False
    return True

decor_count=0
for x,z in [(-10,45),(10,45),(-19,31),(19,31),(-29,17),(29,17),(-20,-21),(20,-21),(-10,-30),(10,-30),(-37,7),(37,7)]:
    if not safe_decor(x,z,1,1,6): continue
    s(x,100,z,P["CHISELED_STONE_BRICKS"])
    fill_box(x,101,z,x,103,z,P["SPRUCE_FENCE"])
    s(x,104,z,P["LANTERN"]); s(x,105,z,P["DARK_PRISMARINE"])
    decor_count+=1
for x,z in [(-11,29),(11,29),(-30,-11),(30,-11),(-17,-29),(17,-29),(-35,29),(35,29)]:
    if not safe_decor(x,z,1,0,2): continue
    fill_box(x-1,100,z,x+1,100,z,P["SPRUCE_PLANKS"])
    s(x-1,101,z,P["SPRUCE_FENCE"]);s(x+1,101,z,P["SPRUCE_FENCE"])
    decor_count+=1
for x,z in [(-11,10),(11,10),(-18,-5),(18,-5),(-30,35),(30,35),(-34,-18),(34,-18)]:
    if not safe_decor(x,z,2,2,7): continue
    s(x,100,z,P["BARREL"])
    fill_box(x,101,z,x,104,z,P["SPRUCE_WOOD"])
    fill_cyl(x,z,2.0,102,103,P["SPRUCE_LEAVES"])
    fill_cyl(x,z,1.5,104,105,P["SPRUCE_LEAVES"])
    s(x,106,z,P["SNOW_BLOCK"]);decor_count+=1
print(f"    Carefully reserved furniture/plantings: {decor_count}")
assert decor_count >= 8

# Three new open-sided winter pavilions.  They are selected by a strict empty-volume
# check so no existing wall, building, NPC, gift or warp is overwritten.
def build_pavilion(cx, cz, variant):
    floor = (P["POLISHED_DIORITE"], P["DARK_PRISMARINE"], P["STONE_BRICKS"])[variant]
    cap = (P["LIGHT_BLUE_STAINED_GLASS"], P["PURPLE_STAINED_GLASS"], P["RED_STAINED_GLASS"])[variant]
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            s(cx + dx, 99, cz + dz, floor if (dx + dz) % 2 else P["SMOOTH_QUARTZ"])
            # Roof is above player head-height; all four sides remain open.
            s(cx + dx, 106, cz + dz, cap if abs(dx) < 3 and abs(dz) < 3 else P["QUARTZ_PILLAR"])
    for dx in (-3, 3):
        for dz in (-3, 3):
            for y in range(100, 106): s(cx + dx, y, cz + dz, P["QUARTZ_PILLAR"])
            s(cx + dx, 106, cz + dz, P["SEA_LANTERN"])
    s(cx, 105, cz, P["SEA_LANTERN"])
    s(cx, 107, cz, P["GOLD_BLOCK"])

reserved_points = [(x, z) for _, x, _, z in WARP_SPOTS] + [(x, z) for x, _, z in NPC_SPOTS] + [(x, z) for x, _, z in GIFT_SPOTS]
pavilions = []
for tx, tz in [(-34, 24), (34, 24), (35, -30)]:
    candidates = sorted(((x, z) for x in range(-42, 43, 2) for z in range(-38, 44, 2)),
                        key=lambda pos: (pos[0]-tx)**2 + (pos[1]-tz)**2)
    for x, z in candidates:
        if abs(x) < 10 or abs(z) < 6: continue  # keep the principal boulevards open
        if any((x-px)**2 + (z-pz)**2 < 12**2 for px, pz in reserved_points + pavilions): continue
        if any(g(x+dx, 99, z+dz) == P["AIR"] or
               any(g(x+dx, y, z+dz) != P["AIR"] for y in range(100, 108))
               for dx in range(-4, 5) for dz in range(-4, 5)):
            continue
        build_pavilion(x, z, len(pavilions))
        pavilions.append((x, z))
        break
assert len(pavilions) == 3, f"Could not reserve three clear pavilions: {pavilions}"
assert wall_built > 0, "At least some safe glacier wall segments must remain"
print(f"    New open pavilions: {pavilions}; protected wall segments skipped={wall_skipped}, kept={wall_built}")

for _, wx, wy, wz in WARP_SPOTS:
    if g(wx, wy - 1, wz) == P["AIR"]:
        s(wx, wy - 1, wz, P["SMOOTH_QUARTZ"])
    fill_box(wx - 1, wy, wz - 1, wx + 1, wy + 3, wz + 1, P["AIR"])

for nx, ny, nz in NPC_SPOTS:
    fill_box(nx - 1, ny - 1, nz - 1, nx + 1, ny - 1, nz + 1, P["POLISHED_DIORITE"])
    fill_box(nx - 1, ny, nz - 1, nx + 1, ny + 3, nz + 1, P["AIR"])

for gx, gy, gz in GIFT_SPOTS:
    s(gx, gy - 1, gz, P["GOLD_BLOCK"])
    fill_box(gx, gy, gz, gx, gy + 2, gz, P["AIR"])

NON_SOLID = {
    P["AIR"], P["LIGHT_WEIGHTED_PRESSURE_PLATE"], P["HEAVY_WEIGHTED_PRESSURE_PLATE"],
    P["RED_CARPET"], P["WHITE_CARPET"], P["YELLOW_CARPET"], P["CYAN_CARPET"],
    P["LEVER"], P["END_ROD"], P["COBWEB"],
}


def can_stand(x, y, z):
    below = g(x, y - 1, z)
    return (below not in NON_SOLID) and (g(x, y, z) in NON_SOLID) and (g(x, y + 1, z) in NON_SOLID)


visited = set()
q = deque([(0, 100, 52)])
visited.add((0, 100, 52))
while q:
    cx, cy, cz = q.popleft()
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = cx + dx, cz + dz
        if not (-120 <= nx <= 120 and -120 <= nz <= 120):
            continue
        for dy in (0, 1, -1):
            ny = cy + dy
            if (nx, ny, nz) not in visited and can_stand(nx, ny, nz):
                # If stepping up +1, ensure head clearance at (cx, cy+2, cz) too!
                if dy == 1 and g(cx, cy + 2, cz) not in NON_SOLID:
                    continue
                if dy == -1 and g(nx, cy + 1, nz) not in NON_SOLID:
                    continue
                visited.add((nx, ny, nz))
                q.append((nx, ny, nz))

print(f"    Total 3D-walkable tiles reachable on foot from /spawn: {len(visited):,}")
for wname, wx, wy, wz in WARP_SPOTS:
    ok = any((wx + dx, wy + dy, wz + dz) in visited
             for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1))
    print(f"    Warp '{wname:18s}' ({wx:4d}, {wy:3d}, {wz:4d}) reachable on foot: {ok}")
    assert ok, f"CRITICAL: Warp {wname} is blocked!"

for idx, (nx, ny, nz) in enumerate(NPC_SPOTS):
    ok = any((nx + dx, ny + dy, nz + dz) in visited
             for dx in (-1, 0, 1) for dy in (-1, 0, 1) for dz in (-1, 0, 1))
    assert ok, f"CRITICAL: NPC #{idx} at ({nx},{ny},{nz}) is unreachable!"
print(f"    All {len(NPC_SPOTS)} NPC plazas 100% reachable on foot!")
for x, z in pavilions:
    assert (x, 100, z) in visited, f"Pavilion {(x, z)} is unreachable"
print("    All three new pavilions reachable on foot!")
print(f"    Parkour steps: {len(PARKOUR_STEPS)} | Start={PARKOUR_STEPS[0]} | Finish={PARKOUR_STEPS[-1]}")

# Confirm ZERO GREEN_CONCRETE / LIME_CONCRETE anywhere in the map!
assert "GREEN_CONCRETE" not in P and "LIME_CONCRETE" not in P, "Concrete leaves forbidden!"
leaf_count = sum(1 for b in grid if b in (
    P["SPRUCE_LEAVES"], P["DARK_OAK_LEAVES"], P["AZALEA_LEAVES"], P["FLOWERING_AZALEA_LEAVES"]))
ice_count = sum(1 for b in grid if b in (P["PACKED_ICE"], P["BLUE_ICE"]))
print(f"    Real Leaf Blocks: {leaf_count:,} | Glacial Ice Blocks: {ice_count:,}")

# ==============================================================================
# EXPORT COMPACT GZIPPED RLE BINARY RESOURCE (spawn_v5.dat)
# ==============================================================================
out_dat = os.environ.get("KINGGRIEF_SPAWN_OUTPUT", str(Path(__file__).resolve().parent.parent / "core-src/src/main/resources/spawn_v73.dat"))
os.makedirs(os.path.dirname(out_dat), exist_ok=True)

runs = bytearray()
cur_val = grid[0]
cur_len = 0
non_air = 0

for b in grid:
    if b != 0:
        non_air += 1
    if b == cur_val and cur_len < 65535:
        cur_len += 1
    else:
        runs.extend(struct.pack(">BH", cur_val, cur_len))
        cur_val = b
        cur_len = 1
if cur_len > 0:
    runs.extend(struct.pack(">BH", cur_val, cur_len))
runs.extend(struct.pack(">BH", 0, 0))

with open(out_dat, "wb") as raw_out, gzip.GzipFile(filename="", mode="wb", fileobj=raw_out, compresslevel=9, mtime=0) as f:
    f.write(struct.pack(">hhhhhh", W, H, L, OX, OY, OZ))
    f.write(struct.pack(">H", len(PALETTE_NAMES)))
    for name in PALETTE_NAMES:
        nb = name.encode("utf-8")
        f.write(struct.pack(">H", len(nb)) + nb)
    f.write(runs)

sz_kb = os.path.getsize(out_dat) / 1024.0
print(f"SUCCESS! Wrote v7.3 Winter Village Spawn RLE ({non_air:,} solid blocks -> {sz_kb:.1f} KB) to {out_dat}")
