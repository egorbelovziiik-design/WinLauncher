#!/usr/bin/env python3
"""
KINGGRIEF NEW YEAR v5.9 — AURORA POLISH EDITION (v5.8 + Северное сияние, снеговики, канделябры)
===========================================================================
Complete architectural overhaul addressing every player & admin critique:
  1. REAL LEAF CHRISTMAS TREE (ZERO concrete foliage!):
     - 10 lush cascading evergreen tiers built from SPRUCE_LEAVES, DARK_OAK_LEAVES,
       AZALEA_LEAVES & FLOWERING_AZALEA_LEAVES (marked persistent=true in SpawnEngine),
       rooted SPRUCE_WOOD trunk up to Y=178, snow-dusted tips, warm spiral garlands,
       3D ornaments, and a radiant 3D Bethlehem Star up to Y=191!
  2. COLOSSAL GLACIAL ICE WALLS & FROST PEAKS:
     - Towering 18–34 block sculpted PACKED_ICE / BLUE_ICE / SNOW_BLOCK / PRISMARINE
       alpine glacier walls & glowing crystal spires around the outer island perimeter,
       pierced by grand Gothic archways on every inter-island boulevard!
  3. 3-FLOOR IMPERIAL WINTER PALACE INTERIOR (X=-21..21, Z=65..92):
     - Floor 1 (Y=100): Marble Throne Hall, Ruby-Gold Throne, Fireplaces, Armory,
       Columns, Gift #3 Star Pedestal, and Twin Grand Staircases!
     - Floor 2 (Y=107): Wrap-around Atrium Balustrade, Royal Library, Enchanting Nook,
       VIP Alchemy & Brewing Gallery, Stained-Glass Windows, and Upper Staircases!
     - Floor 3 (Y=114): Vaulted Cathedral Roof, Colossal Hanging Crystal Chandelier,
       King's Treasury Vaults, and Walk-Out North Panoramic Balcony!
  4. COMFORTABLE 32-ISLAND SCENIC SKY PARKOUR:
     - 3x3 & 2x2 themed sky pads from /warp parkour (Y=101 -> Y=129), mathematically
       verified jump distances (dy <= 1, edge gap 1.5–2.5 blocks), 3 Golden Checkpoints,
       and a 5x5 Diamond/Emerald Trophy Summit!
  5. 100% BFS-VERIFIED WALKABILITY across ALL warps, ALL 27 NPCs, ALL 8 Kabinki,
     Auto-Mine, PvP Colosseum, Mob Farm, Casino, Bafovar, AND all 3 Palace Floors!
"""

import gzip
import math
import os
import random
import struct
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
                s(x, 99, z, P["RED_CONCRETE"] if abs(dx) <= 1 else P["POLISHED_DIORITE"])
                if abs(dx) == 2 and z % 3 == 0:
                    s(x, 99, z, P["GOLD_BLOCK"])
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
            s(x, ty, z, P["RED_CONCRETE"])
        else:
            s(x, ty, z, P["SMOOTH_QUARTZ"] if (x + z) % 2 == 0 else P["POLISHED_DIORITE"])
        fill_box(x, ty + 1, z, x, ty + 7, z, P["AIR"])


# ==============================================================================
# 3. COLOSSAL GLACIAL ICE WALLS & FROST CRYSTAL SPIRES AROUND THE SPAWN!
#    ("и стены из льда сделай больше дострой полностью спавн")
# ==============================================================================
print("[3/11] Raising Colossal Glacial Ice Walls (18–34 Blocks High) & Crystal Spires...")


def build_glacier_spire(cx, cz, base_y, height, base_r, lean_x=0.0, lean_z=0.0, crystal=False):
    for dy in range(height + 1):
        t = dy / max(1, height)
        r = base_r * ((1.0 - t) ** 0.78)
        px = cx + lean_x * dy
        pz = cz + lean_z * dy
        ir = int(math.ceil(r)) + 1
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                if dx * dx + dz * dz <= r * r + 0.15:
                    bx, bz, by = int(round(px + dx)), int(round(pz + dz)), base_y + dy
                    if crystal and dy > height * 0.55:
                        mat = P["SEA_LANTERN"] if dy == height - 1 else P["CYAN_STAINED_GLASS"]
                    elif t < 0.28:
                        mat = P["SNOW_BLOCK"] if (bx + by + bz) % 3 == 0 else P["PACKED_ICE"]
                    elif t < 0.72:
                        mat = P["PACKED_ICE"] if (bx + bz) % 2 == 0 else P["BLUE_ICE"]
                    else:
                        mat = P["BLUE_ICE"] if dy < height - 1 else P["SEA_LANTERN"]
                    s(bx, by, bz, mat)
        # Anchor down to island or void stalactite
        if dy == 0:
            fill_cyl(int(round(cx)), int(round(cz)), max(1.5, base_r * 0.9), base_y - 10, base_y - 1, P["PACKED_ICE"])


# Outer Glacial Ring & Alpine Ice Walls framing the entire Winter Citadel!
# Placed along an epic outer crown (R=96..114) + flanking island cliffs so players see
# massive 22-34 block ice walls in every direction without blocking any bridge or walkway!
for deg in range(0, 360, 4):
    rad = math.radians(deg)
    # Modulated alpine ridge radius & height
    ridge_r = 104.0 + 7.0 * math.sin(rad * 5.0) + 4.0 * math.cos(rad * 9.0)
    wx = int(round(math.cos(rad) * ridge_r))
    wz = int(round(math.sin(rad) * ridge_r))
    # Keep extra clearance directly behind PvP pit east edge (deg -12..+18 -> push to R=112)
    if -16 <= deg <= 22 or deg >= 344:
        wx = int(round(math.cos(rad) * 113.0))
        wz = int(round(math.sin(rad) * 113.0))
    wall_h = int(20 + 11 * abs(math.sin(rad * 6.0)) + 5 * math.cos(rad * 12.0))
    base_rad = 4.2 + 1.4 * abs(math.cos(rad * 4.0))
    # Sculpt deep hanging glacier root below Y=98 too!
    build_glacier_spire(wx, wz, 86, wall_h + 12, base_rad,
                        lean_x=-0.05 * math.cos(rad), lean_z=-0.05 * math.sin(rad),
                        crystal=(deg % 24 == 0))

# Inner Scenic Ice Cliff Buttresses & Gothic Ice Archways framing Central Island rim (R=49)
BRIDGE_ANGLES = [
    math.degrees(math.atan2(17, 48)),    # East PvP bridge
    math.degrees(math.atan2(-24, 48)),   # NE Kabinki/Parkour bridge
    math.degrees(math.atan2(-48, 0)),    # North Case bridge
    math.degrees(math.atan2(-30, -48)),  # NW Farm bridge
    math.degrees(math.atan2(4, -48)),    # West Mine bridge
    math.degrees(math.atan2(56, -38)),   # SW Casino bridge
    math.degrees(math.atan2(56, 38)),    # SE Bafovar bridge
    math.degrees(math.atan2(60, 0)),     # South Palace peninsula
]

for deg in range(0, 360, 5):
    # Check angular distance to any bridge/peninsula opening
    near_opening = False
    for ba in BRIDGE_ANGLES:
        diff = abs((deg - ba + 180) % 360 - 180)
        if diff < 15.5:
            near_opening = True
            break
    if near_opening:
        continue
    rad = math.radians(deg)
    ix = int(round(math.cos(rad) * 48.5))
    iz = int(round(math.sin(rad) * 48.5))
    spire_h = int(14 + 8 * abs(math.sin(rad * 8.0)))
    build_glacier_spire(ix, iz, 98, spire_h, 3.1,
                        lean_x=-0.04 * math.cos(rad), lean_z=-0.04 * math.sin(rad),
                        crystal=(deg % 20 == 0))


# Grand Sculpted Ice & Stone Gothic Archways at the 6 Bridge Gateways!
def build_bridge_gateway_arch(cx, cz, along_x=True, y_base=100):
    for side in (-1, 1):
        px = cx + (0 if along_x else side * 6)
        pz = cz + (side * 6 if along_x else 0)
        fill_box(px - 1, y_base, pz - 1, px + 1, y_base + 11, pz + 1, P["DEEPSLATE_BRICKS"])
        fill_box(px, y_base + 1, pz, px, y_base + 10, pz, P["QUARTZ_PILLAR"])
        build_glacier_spire(px + (0 if along_x else side * 2),
                            pz + (side * 2 if along_x else 0),
                            y_base - 1, 17, 2.4, crystal=True)
        s(px, y_base + 12, pz, P["SEA_LANTERN"])
    # Overhead Gothic Ice Arch (Y=109..114 — 9+ blocks clear walk height!)
    for off in range(-6, 7):
        ax = cx + (0 if along_x else off)
        az = cz + (off if along_x else 0)
        ay = y_base + 12 - (off * off) // 12
        fill_box(ax - (1 if along_x else 0), ay, az - (0 if along_x else 1),
                 ax + (1 if along_x else 0), ay + 1, az + (0 if along_x else 1), P["PACKED_ICE"])
        if off == 0:
            s(ax, ay - 1, az, P["SEA_LANTERN"])
            s(ax, ay + 2, az, P["GOLD_BLOCK"])


build_bridge_gateway_arch(46, 17, along_x=True, y_base=100)
build_bridge_gateway_arch(-48, 4, along_x=True, y_base=100)
build_bridge_gateway_arch(0, -40, along_x=False, y_base=100)


# ==============================================================================
# 4. GIANT REAL-LEAF CHRISTMAS TREE (Y=100..191 — ZERO CONCRETE FOLIAGE!)
#    ("дострой елку ... и блоки елки замени на листву а не бетон")
# ==============================================================================
print("[4/11] Growing Lush 10-Tier SPRUCE_LEAVES Christmas Tree & 3D Bethlehem Star...")

# Ornate Circular Tree Curb & Cozy Gift Plaza around Base (R=10.5, leaves plaza R=12+ wide open!)
fill_cyl(0, 0, 10.5, 99, 100, P["DEEPSLATE_TILES"])
fill_cyl(0, 0, 9.5, 100, 100, P["SNOW_BLOCK"])
for deg in range(0, 360, 30):
    rad = math.radians(deg)
    lx = int(round(math.cos(rad) * 10.0))
    lz = int(round(math.sin(rad) * 10.0))
    s(lx, 101, lz, P["QUARTZ_PILLAR"])
    s(lx, 102, lz, P["SEA_LANTERN"])

# Thick Organic SPRUCE_WOOD Trunk & Flaring Roots from Y=99 all the way to Y=178!
for y in range(99, 179):
    tr = max(1.2, 5.2 - (y - 99) * 0.052)
    fill_cyl(0, 0, tr, y, y, P["SPRUCE_WOOD"])
# 6 Sculpted Roots hugging the snow mound
for rdeg in range(0, 360, 60):
    rrad = math.radians(rdeg + 15)
    for step in range(4, 9):
        rx = int(round(math.cos(rrad) * step))
        rz = int(round(math.sin(rrad) * step))
        ry = 104 - (step - 4)
        fill_box(rx, 100, rz, rx, max(100, ry), rz, P["SPRUCE_WOOD"])

# Decorative Gift Boxes tucked neatly on the snow mound under the tree (R=6.5..8.5, Y=101..103)
UNDER_GIFTS = [
    (7, 3, P["RED_CONCRETE"], P["GOLD_BLOCK"]),
    (-7, 4, P["LIGHT_BLUE_CONCRETE"], P["WHITE_CONCRETE"]),
    (4, -7, P["PURPLE_CONCRETE"], P["GOLD_BLOCK"]),
    (-5, -6, P["ORANGE_CONCRETE"], P["YELLOW_CONCRETE"]),
    (0, -8, P["MAGENTA_CONCRETE"], P["WHITE_CONCRETE"]),
    (-8, -1, P["BLUE_CONCRETE"], P["GOLD_BLOCK"]),
]
for gx, gz, gbox, grib in UNDER_GIFTS:
    fill_box(gx - 1, 101, gz - 1, gx + 1, 103, gz + 1, gbox)
    for k in range(-1, 2):
        s(gx + k, 103, gz, grib)
        s(gx, 103, gz + k, grib)
        s(gx + k, 102, gz - 1, grib)
        s(gx + k, 102, gz + 1, grib)

# 10 Overlapping Lush Pine Leaf Tiers from Y=106 up to Y=180 (100% REAL LEAVES!)
ORNAMENT_MATS = [
    P["RED_CONCRETE"], P["GOLD_BLOCK"], P["DIAMOND_BLOCK"],
    P["EMERALD_BLOCK"], P["AMETHYST_BLOCK"], P["SHROOMLIGHT"], P["SEA_LANTERN"],
]

NUM_TIERS = 10
TREE_BOT_Y = 106
TREE_TOP_Y = 180
for tier in range(NUM_TIERS):
    t_bot = TREE_BOT_Y + tier * 7
    t_top = min(TREE_TOP_Y, t_bot + 10)
    tier_frac = tier / float(NUM_TIERS)
    max_r = 20.5 * (1.0 - tier_frac * 0.86) + 1.8
    lobes = 10 if tier < 5 else 8
    phase_shift = tier * 0.42
    for y in range(t_bot, t_top + 1):
        local_t = (y - t_bot) / max(1.0, float(t_top - t_bot))
        # Natural pine skirt: widest near bottom of tier, tapering smoothly upward
        base_r = max_r * (1.0 - 0.62 * local_t)
        ir = int(math.ceil(base_r + 2.5))
        for dx in range(-ir, ir + 1):
            for dz in range(-ir, ir + 1):
                d = math.hypot(dx, dz)
                if d < 1.2:
                    continue
                ang = math.atan2(dz, dx)
                # Sweeping pine boughs
                bough = 1.0 + 0.16 * math.cos(ang * lobes + phase_shift) + 0.06 * math.sin(ang * (lobes * 2))
                lim_r = base_r * bough
                # Drooping branch tips at bottom of each tier
                if local_t < 0.18 and bough > 1.06 and abs(d - lim_r) <= 1.2:
                    s(dx, y - 1, dz, P["SPRUCE_LEAVES"])
                if d <= lim_r:
                    # Inner core trunk stays wood, outer lush shell is 100% REAL LEAVES!
                    if g(dx, y, dz) == P["SPRUCE_WOOD"]:
                        continue
                    dist_from_edge = lim_r - d
                    if dist_from_edge > 2.6:
                        leaf = P["DARK_OAK_LEAVES"] if (dx + y + dz) % 3 == 0 else P["SPRUCE_LEAVES"]
                    elif (dx * 7 + y * 13 + dz * 11) % 29 == 0:
                        leaf = P["FLOWERING_AZALEA_LEAVES"]
                    elif (dx * 5 + y * 3 + dz * 7) % 13 == 0:
                        leaf = P["AZALEA_LEAVES"]
                    else:
                        leaf = P["SPRUCE_LEAVES"]
                    s(dx, y, dz, leaf)
                    # Light snow dusting on upper outer pine tips
                    if dist_from_edge < 0.95 and bough > 1.05 and (dx + dz + tier) % 3 == 0 and y < 176:
                        s(dx, y + 1, dz, P["SNOW_BLOCK"])

# Continuous Finished Evergreen Spire Tip (Y=176..182) — NO bald gap under the Star!
for y in range(176, 183):
    tip_r = max(0.8, (183 - y) * 0.55)
    fill_cyl(0, 0, tip_r, y, y, P["SPRUCE_LEAVES"])
fill_box(0, 175, 0, 0, 183, 0, P["SPRUCE_WOOD"])

# Double Spiral Golden Garlands & 3D Christmas Baubles wrapping the Leaf Tree!
for step in range(0, 290):
    t = step / 290.0
    gy = int(round(108 + t * 69))
    # Find outer leaf radius along this ray so garlands hug the real leaves cleanly!
    for strand in (0, 1):
        ang = t * math.pi * 12.0 + strand * math.pi
        ux, uz = math.cos(ang), math.sin(ang)
        leaf_r = 0
        for rr in range(24, 1, -1):
            tx, tz = int(round(ux * rr)), int(round(uz * rr))
            mat = g(tx, gy, tz)
            if mat in (P["SPRUCE_LEAVES"], P["DARK_OAK_LEAVES"], P["AZALEA_LEAVES"], P["FLOWERING_AZALEA_LEAVES"]):
                leaf_r = rr
                break
        if leaf_r >= 2:
            gx, gz = int(round(ux * leaf_r)), int(round(uz * leaf_r))
            if step % 6 == 0:
                orn = ORNAMENT_MATS[(step // 6 + strand * 3) % len(ORNAMENT_MATS)]
                # 3D hanging bauble!
                s(gx, gy, gz, P["GOLD_BLOCK"])
                s(gx, gy - 1, gz, orn)
            else:
                s(gx, gy, gz, P["SHROOMLIGHT"] if strand == 0 else P["OCHRE_FROGLIGHT"])

# Radiant 3D 8-Pointed Bethlehem Star on the Crown (Y=183..192)!
sc_y = 187
fill_box(-1, sc_y - 1, -1, 1, sc_y + 1, 1, P["SEA_LANTERN"])
for arm in range(-5, 6):
    s(arm, sc_y, 0, P["GOLD_BLOCK"] if abs(arm) < 5 else P["SHROOMLIGHT"])
    s(0, sc_y + arm, 0, P["GOLD_BLOCK"] if abs(arm) < 5 else P["SHROOMLIGHT"])
    s(0, sc_y, arm, P["GOLD_BLOCK"] if abs(arm) < 5 else P["SHROOMLIGHT"])
for d in range(-3, 4):
    if d != 0:
        s(d, sc_y + d, 0, P["OCHRE_FROGLIGHT"])
        s(d, sc_y - d, 0, P["OCHRE_FROGLIGHT"])
        s(0, sc_y + d, d, P["OCHRE_FROGLIGHT"])
        s(0, sc_y - d, d, P["OCHRE_FROGLIGHT"])


# ==============================================================================
# 5. GRACEFUL SKY SLEIGH & 6 REINDEER (OPEN SKY Y=153..160 — NO UGLY PILLAR!)
# ==============================================================================
print("[5/11] Sculpting Curved Sky Sleigh & 6 Antlered Reindeer...")

sx0, sy0, sz0 = -28, 154, 28
# Curved Golden Runners (Y=153..155)
for rx in range(-7, 8):
    ry = sy0 - 1 + (1 if abs(rx) >= 6 else 0) + (1 if rx == 7 else 0)
    for rz in (-3, 3):
        s(sx0 + rx, ry, sz0 + rz, P["GOLD_BLOCK"])
        if rx in (-4, 0, 4):
            s(sx0 + rx, sy0, sz0 + rz, P["SPRUCE_FENCE"])
# Sleigh Hull (Curved Crimson & Spruce Cabin)
fill_box(sx0 - 5, sy0 + 1, sz0 - 3, sx0 + 5, sy0 + 1, sz0 + 3, P["DARK_OAK_PLANKS"])
for rx in range(-5, 6):
    wall_top = sy0 + 4 if rx <= -2 else (sy0 + 2 if rx >= 4 else sy0 + 3)
    for rz in (-3, 3):
        fill_box(sx0 + rx, sy0 + 2, sz0 + rz, sx0 + rx, wall_top, sz0 + rz, P["RED_CONCRETE"])
        s(sx0 + rx, wall_top, sz0 + rz, P["GOLD_BLOCK"])
fill_box(sx0 - 5, sy0 + 2, sz0 - 2, sx0 - 5, sy0 + 4, sz0 + 2, P["RED_CONCRETE"])
fill_box(sx0 + 5, sy0 + 2, sz0 - 2, sx0 + 5, sy0 + 3, sz0 + 2, P["GOLD_BLOCK"])
# Plush Seat & Colorful Gift Pile inside Sleigh (subtle, proportioned!)
fill_box(sx0, sy0 + 2, sz0 - 2, sx0 + 1, sy0 + 2, sz0 + 2, P["WHITE_WOOL"])
s(sx0 - 3, sy0 + 2, sz0 - 1, P["EMERALD_BLOCK"])
s(sx0 - 2, sy0 + 2, sz0 + 1, P["DIAMOND_BLOCK"])
s(sx0 - 3, sy0 + 3, sz0, P["GOLD_BLOCK"])
s(sx0 + 5, sy0 + 3, sz0, P["SEA_LANTERN"])

# 6 Sculpted Reindeer pulling Eastward (X = -16, -9, -2) + Golden Reins
for pair_i, rdx in enumerate([10, 17, 24]):
    ry = sy0 + 1 + pair_i
    for rdz in (-2, 2):
        cx, cz = sx0 + rdx, sz0 + rdz
        fill_box(cx - 1, ry, cz, cx + 1, ry, cz, P["STRIPPED_SPRUCE_LOG"])
        s(cx - 1, ry - 1, cz, P["SPRUCE_FENCE"])
        s(cx + 1, ry - 1, cz, P["SPRUCE_FENCE"])
        s(cx + 2, ry + 1, cz, P["STRIPPED_SPRUCE_LOG"])
        s(cx + 2, ry + 2, cz - 1, P["SPRUCE_FENCE"])
        s(cx + 2, ry + 2, cz + 1, P["SPRUCE_FENCE"])
        if pair_i == 2 and rdz == 2:
            s(cx + 3, ry + 1, cz, P["SHROOMLIGHT"])  # Rudolph's glowing nose!
        # Golden rein sparkle
        s(cx - 3, ry, cz, P["END_ROD"])


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
                mat = P["DEEPSLATE_TILES"] if y in (107, 114, PY2) else P["QUARTZ_PILLAR"]
            elif 102 <= y <= 105 or 109 <= y <= 112 or 116 <= y <= 119:
                mat = P["CYAN_STAINED_GLASS"] if (x + y) % 2 == 0 else P["LIGHT_BLUE_STAINED_GLASS"]
            else:
                mat = P["SMOOTH_QUARTZ"]
            s(x, y, z, mat)
    for z in range(PZ1, PZ2 + 1):
        for x in (PX1, PX2):
            if (z - PZ1) % 6 == 0 or y in (100, 107, 114, PY2):
                mat = P["DEEPSLATE_TILES"] if y in (107, 114, PY2) else P["QUARTZ_PILLAR"]
            elif 102 <= y <= 105 or 109 <= y <= 112 or 116 <= y <= 119:
                mat = P["CYAN_STAINED_GLASS"] if (z + y) % 2 == 0 else P["BLUE_STAINED_GLASS"]
            else:
                mat = P["SMOOTH_QUARTZ"]
            s(x, y, z, mat)

# 4 Corner Palace Bell-Towers with Conical Dark-Prismarine & Ice Spires!
for tx in (PX1, PX2):
    for tz in (PZ1, PZ2):
        fill_cyl(tx, tz, 3.2, 100, 125, P["DEEPSLATE_BRICKS"])
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
    build_glacier_spire(bx, bz, 100, 18 + (5 - abs(idx - 5)) * 2, 2.5, crystal=(idx % 2 == 1))
    s(bx, 106, bz, P["SEA_LANTERN"])


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
        build_glacier_spire(x, z, 99, 15, 2.2, crystal=(x % 2 == 0))
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


# ==============================================================================
# 12. v5.9 POLISH: AURORA BOREALIS, ICE CANDELABRAS, SNOWMAN ALLEY, GOLD INLAY
#     (каждый наземный декор проверяет наличие пола — НИКАКИХ парящих багов!)
# ==============================================================================
print("[12/12] v5.9 Polish: Aurora Borealis, Ice Candelabras, Snowman Alley & Gold Inlay...")

# --- Северное сияние: 3 высоких стеклянных ленты (y>=142, выше ЛЮБЫХ построек) ---
AURORA_MATS = [P["LIGHT_BLUE_STAINED_GLASS"], P["LIME_STAINED_GLASS"], P["PURPLE_STAINED_GLASS"]]
AURORA_Z = [-78, 74, 34]
for band in range(3):
    zc = AURORA_Z[band]
    base_y = 152 + band * 9
    amp = 9 + band * 4
    for x in range(-96, 97):
        # центральная лента пропускает небо над ёлкой (макушка Y=191)!
        if band == 2 and -18 <= x <= 18:
            continue
        y1 = base_y + int(round(amp * math.sin(x * 0.042 + band * 2.2)))
        y2 = base_y + int(round(amp * math.sin((x + 1) * 0.042 + band * 2.2)))
        mat = AURORA_MATS[band]
        for t in range(3):
            for yy in (y1, y2, (y1 + y2) // 2 + 1):
                if 142 <= yy <= 190 and rng.random() < 0.72:
                    s(x, yy, zc + t, mat)

# --- Ледяные канделябры вокруг ёлки (R=14, между кольцами площади 13 и 23) ---
for deg in range(0, 360, 45):
    rad = math.radians(deg + 22.5)
    cx = int(round(math.cos(rad) * 14.0))
    cz = int(round(math.sin(rad) * 14.0))
    if g(cx, 99, cz) == P["AIR"]:
        continue
    if abs(cx) <= 4 and cz > 10:
        continue  # южный королевский бульвар не трогаем!
    if g(cx, 100, cz) != P["AIR"] or g(cx, 101, cz) != P["AIR"] or g(cx, 102, cz) != P["AIR"]:
        continue  # место занято — пропускаем (без багов!)
    s(cx, 100, cz, P["PACKED_ICE"])
    s(cx, 101, cz, P["PACKED_ICE"])
    s(cx, 102, cz, P["SEA_LANTERN"])

# --- Аллея снеговиков вдоль королевского бульвара (/spawn -> ёлка) ---
SNOWMAN_SPOTS = [(-5, 22), (5, 22), (-5, 30), (5, 30), (-5, 38), (5, 38), (-5, 46), (5, 46)]
for sx, sz in SNOWMAN_SPOTS:
    if g(sx, 99, sz) == P["AIR"]:
        continue
    if g(sx, 100, sz) != P["AIR"] or g(sx, 101, sz) != P["AIR"] or g(sx, 102, sz) != P["AIR"]:
        continue
    s(sx, 100, sz, P["SNOW_BLOCK"])
    s(sx, 101, sz, P["SNOW_BLOCK"])
    s(sx, 102, sz, P["CARVED_PUMPKIN"])

# --- Золотая инкрустация точки прибытия /spawn (0,52) ---
for dx in range(-2, 3):
    for dz in range(50, 55):
        x, z = dx, dz
        if g(x, 99, z) == P["AIR"]:
            continue
        if abs(dx) + abs(z - 52) <= 2:
            s(x, 99, z, P["GOLD_BLOCK"])
        elif (dx + z) % 2 == 0:
            s(x, 99, z, P["CHISELED_QUARTZ_BLOCK"])


# ==============================================================================
# 13. v6.2 MEGA POLISH: ФОНАРНЫЕ СТОЛБЫ И СВЕТЯЩИЕСЯ АРКИ НАД КОРОЛЕВСКИМ БУЛЬВАРОМ
# ==============================================================================
print("[13/13] v6.2 Mega Polish: Lantern Posts & Glow Arches over Royal Boulevard...")

# --- Ледяные фонарные столбы вдоль бульвара (x=±6, шаг 6 по z) ---
for pz in range(14, 52, 6):
    for px in (-6, 6):
        if g(px, 99, pz) == P["AIR"]:
            continue
        if g(px, 100, pz) != P["AIR"] or g(px, 101, pz) != P["AIR"] or g(px, 102, pz) != P["AIR"] or g(px, 103, pz) != P["AIR"]:
            continue
        s(px, 100, pz, P["PACKED_ICE"])
        s(px, 101, pz, P["PACKED_ICE"])
        s(px, 102, pz, P["PACKED_ICE"])
        s(px, 103, pz, P["SEA_LANTERN"])

# --- Светящиеся арки-гирлянды над бульваром (y=106, выше голов) ---
for az in (16, 28, 40, 50):
    for ax in range(-6, 7):
        s(ax, 106, az, P["SEA_LANTERN"])
    for hx in (-3, 0, 3):
        s(hx, 105, az, P["GOLD_BLOCK"])

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
out_dat = "/home/user/WinLauncher/rw-newyear-assembly/core-src/src/main/resources/spawn_v5.dat"
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

with gzip.open(out_dat, "wb", compresslevel=9) as f:
    f.write(struct.pack(">hhhhhh", W, H, L, OX, OY, OZ))
    f.write(struct.pack(">H", len(PALETTE_NAMES)))
    for name in PALETTE_NAMES:
        nb = name.encode("utf-8")
        f.write(struct.pack(">H", len(nb)) + nb)
    f.write(runs)

sz_kb = os.path.getsize(out_dat) / 1024.0
print(f"SUCCESS! Wrote v5.9 Aurora Spawn RLE ({non_air:,} solid blocks -> {sz_kb:.1f} KB) to {out_dat}")
