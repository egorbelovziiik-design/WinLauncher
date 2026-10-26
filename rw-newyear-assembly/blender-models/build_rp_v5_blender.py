import json
import math
import os
import hashlib
import shutil
import zipfile
import bpy
import numpy as np
from PIL import Image, ImageDraw

OUT_RP_DIR = "/home/user/WinLauncher/rw-newyear-assembly/mega-rp-build"
OUT_ZIP = "/home/user/WinLauncher/rw-newyear-assembly/kinggrief-mega-rp.zip"
OUT_BLEND_DIR = "/home/user/WinLauncher/rw-newyear-assembly/blender-models"

if os.path.exists(OUT_RP_DIR):
    shutil.rmtree(OUT_RP_DIR)
os.makedirs(OUT_RP_DIR, exist_ok=True)
os.makedirs(OUT_BLEND_DIR, exist_ok=True)

# ==============================================================================
# 1. ALL 40 RANKS — 8-FRAME ANIMATED GRAPHICAL BADGES (\uEA00 .. \uEB3F)
#    Exact order must match RankManager.ORDERED_RANK_IDS (0..39)
# ==============================================================================
RANKS_40 = [
    # idx, id, badge_text, bg_rgb, border_rgb, accent_rgb, icon_type
    (0,  "owner",     "OWNER",     (42, 6, 14),   (255, 23, 68),   (255, 215, 64),  "crown"),
    (1,  "techadmin", "TECH.ADM",  (38, 8, 20),   (244, 63, 94),   (56, 189, 248),  "gear"),
    (2,  "curator",   "CURATOR",   (6, 38, 18),   (34, 197, 94),   (163, 230, 53),  "clover"),
    (3,  "kp",        "KP STAFF",  (6, 36, 28),   (16, 185, 129),  (110, 231, 183), "shield"),
    (4,  "glmoder",   "GL.MODER",  (26, 10, 48),  (139, 92, 246),  (216, 180, 254), "sword"),
    (5,  "stmoder",   "ST.MODER",  (16, 14, 52),  (99, 102, 241),  (165, 180, 252), "sword"),
    (6,  "moder",     "MODER",     (10, 22, 54),  (59, 130, 246),  (147, 197, 253), "shield"),
    (7,  "sthelper",  "ST.HELPR",  (8, 30, 48),   (14, 165, 233),  (125, 211, 252), "star"),
    (8,  "helper",    "HELPER",    (8, 34, 46),   (56, 189, 248),  (186, 230, 253), "star"),
    (9,  "downer",    "D.OWNER",   (44, 30, 4),   (250, 204, 21),  (255, 82, 82),   "crown"),
    (10, "opka",      "OPKA",      (40, 8, 46),   (217, 70, 239),  (250, 204, 21),  "bolt"),
    (11, "dkp",       "D.KP",      (46, 18, 4),   (255, 109, 0),   (255, 215, 64),  "shield"),
    (12, "dadmin",    "D.ADMIN",   (44, 8, 8),    (239, 68, 68),   (253, 186, 116), "crown"),
    (13, "dmladmin",  "D.ML.ADM",  (44, 16, 6),   (249, 115, 22),  (253, 224, 71),  "sword"),
    (14, "dglmoder",  "D.GLMOD",   (28, 12, 48),  (168, 85, 247),  (240, 171, 252), "sword"),
    (15, "dstmoder",  "D.STMOD",   (12, 24, 52),  (59, 130, 246),  (125, 211, 252), "shield"),
    (16, "dmoder",    "D.MODER",   (6, 36, 34),   (20, 184, 166),  (153, 246, 228), "shield"),
    (17, "dhelper",   "D.HELPER",  (8, 38, 36),   (45, 212, 191),  (204, 251, 241), "star"),
    (18, "mediaplus", "MEDIA+",    (46, 8, 22),   (244, 63, 94),   (253, 224, 71),  "play"),
    (19, "media",     "MEDIA",     (44, 10, 28),  (236, 72, 153),  (249, 168, 212), "play"),
    (20, "yt",        "YOUTUBE",   (48, 6, 6),    (255, 23, 68),   (255, 255, 255), "play"),
    (21, "twitch",    "TWITCH",    (28, 10, 50),  (145, 70, 255),  (233, 213, 255), "play"),
    (22, "tiktok",    "TIKTOK",    (14, 14, 22),  (255, 0, 80),    (0, 242, 234),   "play"),
    (23, "sponsor",   "SPONSOR",   (44, 32, 6),   (251, 191, 36),  (254, 240, 138), "gem"),
    (24, "bull",      "BULL",      (44, 18, 6),   (234, 88, 12),   (254, 215, 170), "horns"),
    (25, "king",      "KING",      (34, 10, 48),  (168, 85, 247),  (250, 204, 21),  "crown"),
    (26, "dragon",    "DRAGON",    (40, 8, 42),   (217, 70, 239),  (255, 128, 171), "fang"),
    (27, "imperator", "IMPERAT",   (44, 8, 18),   (244, 63, 94),   (253, 224, 71),  "crown"),
    (28, "overlord",  "OVERLRD",   (6, 36, 36),   (45, 212, 191),  (167, 243, 208), "bolt"),
    (29, "god",       "GOD",       (44, 34, 6),   (250, 204, 21),  (255, 255, 255), "star"),
    (30, "dracula",   "DRACULA",   (40, 4, 8),    (220, 38, 38),   (255, 138, 128), "fang"),
    (31, "tiger",     "TIGER",     (44, 22, 6),   (251, 146, 60),  (254, 240, 138), "fang"),
    (32, "vampire",   "VAMPIRE",   (42, 6, 12),   (239, 68, 68),   (248, 113, 113), "fang"),
    (33, "rabbit",    "RABBIT",    (8, 28, 46),   (56, 189, 248),  (224, 242, 254), "ears"),
    (34, "titan",     "TITAN",     (12, 24, 48),  (96, 165, 250),  (191, 219, 254), "shield"),
    (35, "bunny",     "BUNNY",     (42, 10, 28),  (236, 72, 153),  (251, 207, 232), "ears"),
    (36, "hero",      "HERO",      (8, 30, 46),   (56, 189, 248),  (253, 224, 71),  "sword"),
    (37, "cobra",     "COBRA",     (18, 38, 8),   (132, 204, 22),  (217, 249, 157), "fang"),
    (38, "pegas",     "PEGAS",     (8, 38, 18),   (34, 197, 94),   (187, 247, 208), "wing"),
    (39, "default",   "PLAYER",    (22, 28, 36),  (148, 163, 184), (226, 232, 240), "helm"),
]

# Crisp 3x5 bitmap font so EVERY letter of EVERY rank is razor-sharp at 9px chat height
FONT_3X5 = {
    'A': ["010","101","111","101","101"],
    'B': ["110","101","110","101","110"],
    'C': ["011","100","100","100","011"],
    'D': ["110","101","101","101","110"],
    'E': ["111","100","110","100","111"],
    'F': ["111","100","110","100","100"],
    'G': ["011","100","101","101","011"],
    'H': ["101","101","111","101","101"],
    'I': ["111","010","010","010","111"],
    'J': ["001","001","001","101","010"],
    'K': ["1001","1010","1100","1010","1001"],
    'L': ["100","100","100","100","111"],
    'M': ["10001","11011","10101","10001","10001"],
    'N': ["1001","1101","1011","1001","1001"],
    'O': ["0110","1001","1001","1001","0110"],
    'P': ["110","101","110","100","100"],
    'Q': ["0110","1001","1001","0111","0001"],
    'R': ["1110","1001","1110","1010","1001"],
    'S': ["011","100","010","001","110"],
    'T': ["111","010","010","010","010"],
    'U': ["1001","1001","1001","1001","0110"],
    'V': ["1001","1001","1001","0110","0110"],
    'W': ["10001","10001","10101","11011","10001"],
    'X': ["101","101","010","101","101"],
    'Y': ["101","101","010","010","010"],
    'Z': ["111","001","010","100","111"],
    '+': ["000","010","111","010","000"],
    '.': ["0","0","0","0","1"],
    ' ': ["00","00","00","00","00"],
}

ICONS_7X7 = {
    "crown":  ["0001000","1011101","1111111","1101011","1111111","0111110","0000000"],
    "clover": ["0110110","1111111","0111110","1110111","0111110","0001000","0011000"],
    "gear":   ["0011100","0110110","1100011","1001001","1100011","0110110","0011100"],
    "shield": ["1111111","1101011","1111111","0111110","0111110","0011100","0001000"],
    "sword":  ["0000011","0000111","0001110","0101100","0111000","1110000","1101000"],
    "star":   ["0001000","0011100","1111111","0111110","0110110","1100011","0000000"],
    "bolt":   ["0001110","0011100","0111000","1111110","0001100","0011000","0110000"],
    "play":   ["0110000","0111100","0111110","0111111","0111110","0111100","0110000"],
    "gem":    ["0111110","1101011","1111111","0111110","0011100","0001000","0000000"],
    "horns":  ["1100011","1010101","0111110","0101010","0111110","0011100","0000000"],
    "fang":   ["1100011","1110111","0111110","0101010","0100010","0010100","0000000"],
    "ears":   ["0100010","1100011","1100011","0111110","1101011","0111110","0000000"],
    "wing":   ["0000111","0011110","0111100","1111110","1111000","0110000","0000000"],
    "helm":   ["0111110","1111111","1100011","1101011","1100011","0100010","0000000"],
}

def render_badge_frame(label, bg, border, accent, icon_type, frame_idx):
    # Compute pixel width cleanly so badge never clips and fits chat/TAB/Scoreboard
    char_widths = [len(FONT_3X5.get(ch, FONT_3X5[' '])[0]) for ch in label]
    text_w = sum(char_widths) + max(0, len(label) - 1)
    h = 13
    left_box_w = 13
    w = left_box_w + text_w + 8
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    phase = (frame_idx / 8.0) * 2.0 * math.pi
    pulse = 0.82 + 0.18 * math.sin(phase)
    sweep_x = int(((frame_idx / 8.0) * (w + 10)) - 5)

    # Outer dark shadow + bevel plaque
    for y in range(1, h - 1):
        for x in range(1, w - 1):
            is_edge = (x == 1 or x == w - 2 or y == 1 or y == h - 2 or x == left_box_w)
            dist_sweep = abs((x + y // 2) - sweep_x)
            shimmer = max(0.0, 1.0 - dist_sweep / 4.0)
            if is_edge:
                r = min(255, int(border[0] * pulse + accent[0] * 0.45 * shimmer))
                g = min(255, int(border[1] * pulse + accent[1] * 0.45 * shimmer))
                b = min(255, int(border[2] * pulse + accent[2] * 0.45 * shimmer))
                px[x, y] = (r, g, b, 255)
            else:
                grad = 1.15 - 0.35 * (y / float(h))
                r = min(255, int(bg[0] * grad + border[0] * 0.22 * shimmer))
                g = min(255, int(bg[1] * grad + border[1] * 0.22 * shimmer))
                b = min(255, int(bg[2] * grad + border[2] * 0.22 * shimmer))
                px[x, y] = (r, g, b, 245)

    # Outer black 1px crisp outline
    for x in range(1, w - 1):
        px[x, 0] = (10, 12, 18, 235)
        px[x, h - 1] = (10, 12, 18, 235)
    for y in range(1, h - 1):
        px[0, y] = (10, 12, 18, 235)
        px[w - 1, y] = (10, 12, 18, 235)

    # Corner gold/accent rivets
    for cx, cy in [(2, 2), (left_box_w - 1, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)]:
        px[cx, cy] = (accent[0], accent[1], accent[2], 255)

    # Draw 7x7 animated icon in left box
    icon = ICONS_7X7.get(icon_type, ICONS_7X7["star"])
    bob = 0 if frame_idx in (0, 1, 4, 5) else (-1 if frame_idx in (2, 3) else 0)
    for iy, row in enumerate(icon):
        for ix, bit in enumerate(row):
            if bit == '1':
                tx, ty = 3 + ix, 3 + iy + bob
                if 2 <= tx < left_box_w and 2 <= ty < h - 2:
                    bright = 1.0 if iy < 3 else 0.88
                    px[tx, ty] = (
                        min(255, int(accent[0] * bright)),
                        min(255, int(accent[1] * bright)),
                        min(255, int(accent[2] * bright)),
                        255,
                    )

    # Draw crisp white/shimmering text on right plaque with 1px drop shadow
    cx = left_box_w + 4
    cy = 4
    for ch in label:
        glyph = FONT_3X5.get(ch, FONT_3X5[' '])
        gw = len(glyph[0])
        for gy, grow in enumerate(glyph):
            for gx, bit in enumerate(grow):
                if bit == '1':
                    tx, ty = cx + gx, cy + gy
                    if ty + 1 < h - 1 and tx + 1 < w - 1:
                        px[tx + 1, ty + 1] = (12, 10, 18, 230)
                    dist_s = abs(tx - sweep_x)
                    if dist_s <= 2:
                        px[tx, ty] = (255, 255, 220, 255)
                    elif gy == 0:
                        px[tx, ty] = (255, 255, 255, 255)
                    else:
                        px[tx, ty] = (242, 246, 255, 255)
        cx += gw + 1

    # Animated sparkle star inside plaque
    sp_x = left_box_w + 2 + ((frame_idx * 5) % max(4, (w - left_box_w - 5)))
    sp_y = 2 if frame_idx % 2 == 0 else h - 3
    if left_box_w + 1 < sp_x < w - 2:
        px[sp_x, sp_y] = (255, 255, 255, 255)

    return img

font_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/textures/font")
os.makedirs(os.path.join(font_dir, "ranks"), exist_ok=True)

providers = []

# Generate 40 * 8 = 320 animated rank badges (\uEA00 + idx*8 + frame)
for idx, rid, label, bg, border, accent, icon_type in RANKS_40:
    for f in range(8):
        im = render_badge_frame(label, bg, border, accent, icon_type, f)
        fn = f"ranks/{rid}_{f}.png"
        im.save(os.path.join(font_dir, fn))
        cp = 0xEA00 + idx * 8 + f
        providers.append({
            "type": "bitmap",
            "file": f"minecraft:font/{fn}",
            "ascent": 8,
            "height": 9,
            "chars": [chr(cp)]
        })

# Also keep \uE940..\uE947 (curator) and \uE950..\uE957 (owner) compatibility aliases
for f in range(8):
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/ranks/curator_{f}.png",
        "ascent": 8, "height": 9, "chars": [chr(0xE940 + f)]
    })
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/ranks/owner_{f}.png",
        "ascent": 8, "height": 9, "chars": [chr(0xE950 + f)]
    })

# Generate 16 crisp HUD icons (\uE970..\uE97F)
HUD_SPECS = [
    (0xE970, "hud_crown",  (255, 215, 64), "crown"),
    (0xE971, "hud_clan",   (168, 85, 247), "shield"),
    (0xE972, "hud_coin",   (250, 204, 21), "gem"),
    (0xE973, "hud_relic",  (56, 189, 248), "star"),
    (0xE974, "hud_swords", (244, 63, 94),  "sword"),
    (0xE975, "hud_ping",   (34, 197, 94),  "bolt"),
    (0xE976, "hud_snow",   (125, 211, 252),"star"),
    (0xE977, "hud_gift",   (251, 113, 133),"gem"),
    (0xE978, "hud_casino", (250, 204, 21), "star"),
    (0xE979, "hud_pick",   (56, 189, 248), "sword"),
    (0xE97A, "hud_potion", (217, 70, 239), "clover"),
    (0xE97B, "hud_star",   (253, 224, 71), "star"),
    (0xE97C, "hud_fire",   (249, 115, 22), "bolt"),
    (0xE97D, "hud_heart",  (239, 68, 68),  "clover"),
    (0xE97E, "hud_bolt",   (250, 204, 21), "bolt"),
    (0xE97F, "hud_lp",     (16, 185, 129), "gear"),
]
for cp, hname, col, ic in HUD_SPECS:
    him = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    hpx = him.load()
    grid = ICONS_7X7[ic]
    for y, r in enumerate(grid):
        for x, b in enumerate(r):
            if b == '1':
                hpx[x + 1, y + 1] = (col[0], col[1], col[2], 255)
                if y + 2 < 9 and hpx[x + 1, y + 2][3] == 0:
                    hpx[x + 1, y + 2] = (15, 18, 26, 220)
    him.save(os.path.join(font_dir, f"{hname}.png"))
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/{hname}.png",
        "ascent": 7, "height": 8, "chars": [chr(cp)]
    })

os.makedirs(os.path.join(OUT_RP_DIR, "assets/minecraft/font"), exist_ok=True)
with open(os.path.join(OUT_RP_DIR, "assets/minecraft/font/default.json"), "w", encoding="utf-8") as fp:
    json.dump({"providers": providers}, fp, ensure_ascii=False, indent=2)

print("Generated 320 animated rank badge frames + 16 HUD glyphs!")

# ==============================================================================
# 2. REAL BLENDER 4.2 (bpy + CYCLES) 3D ANIMATED ITEM RENDERER + 3D JSON MODELS!
# ==============================================================================
ITEMS_3D = [
    # id, kind, primary_rgb, emission_rgb
    ("titan_sphere",     "sphere",   (0.20, 0.55, 1.00), (0.10, 0.70, 1.00)),
    ("andromeda_sphere", "sphere",   (0.72, 0.22, 1.00), (0.90, 0.35, 1.00)),
    ("chimera_sphere",   "sphere",   (0.15, 0.92, 0.42), (0.20, 1.00, 0.55)),
    ("apollo_sphere",    "sphere",   (1.00, 0.78, 0.15), (1.00, 0.85, 0.25)),
    ("shar_aida",        "sphere",   (0.95, 0.12, 0.18), (1.00, 0.25, 0.10)),
    ("shar_osirisa",     "sphere",   (0.95, 0.65, 0.12), (0.30, 1.00, 0.75)),
    ("ares_sphere",      "sphere",   (0.88, 0.18, 0.12), (1.00, 0.45, 0.10)),
    ("shar_santy",       "sphere",   (0.25, 0.88, 1.00), (1.00, 0.20, 0.35)),

    ("krush_talisman",   "talisman", (0.95, 0.15, 0.20), (1.00, 0.50, 0.10)),
    ("tal_grani",        "talisman", (0.20, 0.85, 0.95), (0.40, 0.95, 1.00)),
    ("tal_dedala",       "talisman", (1.00, 0.80, 0.20), (1.00, 0.92, 0.50)),
    ("tal_tritona",      "talisman", (0.12, 0.55, 1.00), (0.20, 0.95, 0.85)),
    ("phoenix_talisman", "talisman", (1.00, 0.42, 0.08), (1.00, 0.85, 0.15)),
    ("tal_echidny",      "talisman", (0.55, 0.12, 0.85), (0.25, 1.00, 0.45)),
    ("frost_talisman",   "talisman", (0.45, 0.90, 1.00), (0.80, 0.97, 1.00)),
    ("immortal_totem",   "talisman", (1.00, 0.85, 0.20), (0.30, 1.00, 0.55)),

    ("trapka_item",      "cage",     (0.22, 0.16, 0.32), (0.85, 0.20, 1.00)),
    ("plast_item",       "plast",    (0.16, 0.20, 0.30), (0.20, 0.85, 1.00)),
    ("dezorient_item",   "vortex",   (0.75, 0.15, 0.95), (1.00, 0.30, 0.85)),
    ("yavnaya_pyl",      "star",     (1.00, 0.90, 0.25), (1.00, 0.98, 0.65)),
    ("bozhya_aura",      "halo",     (1.00, 0.84, 0.22), (1.00, 0.95, 0.55)),
    ("ognenny_smerch",   "vortex",   (1.00, 0.32, 0.06), (1.00, 0.78, 0.15)),
    ("snow_freeze",      "star",     (0.35, 0.88, 1.00), (0.80, 0.98, 1.00)),
    ("spider_net",       "cage",     (0.88, 0.92, 0.96), (0.45, 0.85, 1.00)),

    ("elytra_wind",      "wings",    (0.45, 0.90, 1.00), (0.80, 0.98, 1.00)),
    ("elytra_phoenix",   "wings",    (1.00, 0.36, 0.08), (1.00, 0.82, 0.18)),
    ("elytra_vampire",   "wings",    (0.85, 0.08, 0.18), (1.00, 0.20, 0.35)),
    ("elytra_thunder",   "wings",    (0.95, 0.82, 0.15), (0.35, 0.80, 1.00)),
    ("elytra_titan",     "wings",    (0.28, 0.32, 0.42), (0.25, 0.75, 1.00)),
    ("elytra_santa",     "wings",    (0.95, 0.14, 0.22), (0.40, 0.95, 1.00)),

    ("drill_pickaxe",    "pickaxe",  (1.00, 0.55, 0.12), (1.00, 0.88, 0.25)),
    ("pick_spawner",     "pickaxe",  (0.65, 0.25, 1.00), (0.30, 0.95, 1.00)),
    ("sword_dracula",    "sword",    (0.90, 0.08, 0.16), (1.00, 0.25, 0.35)),
    ("krush_sword",      "sword",    (1.00, 0.38, 0.10), (1.00, 0.85, 0.20)),
    ("snowflake_coin",   "star",     (0.35, 0.88, 1.00), (0.85, 0.98, 1.00)),
    ("bounty_scroll",    "halo",     (0.95, 0.25, 0.20), (1.00, 0.80, 0.25)),
]

tex_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/textures/item")
mod_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/models/item")
def_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/items")
os.makedirs(tex_item_dir, exist_ok=True)
os.makedirs(mod_item_dir, exist_ok=True)
os.makedirs(def_item_dir, exist_ok=True)

def make_mat(name, base_col, emit_col, emit_strength=2.2, metallic=0.75, rough=0.22):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes["Principled BSDF"]
    bsdf.inputs["Base Color"].default_value = (*base_col, 1.0)
    bsdf.inputs["Metallic"].default_value = metallic
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Emission Color"].default_value = (*emit_col, 1.0)
    bsdf.inputs["Emission Strength"].default_value = emit_strength
    return mat

def build_blender_rig(kind, pcol, ecol):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    root = bpy.data.objects.new("RigRoot", None)
    bpy.context.collection.objects.link(root)

    mat_main = make_mat("Main", pcol, pcol, 0.55, 0.85, 0.28)
    mat_glow = make_mat("Glow", ecol, ecol, 1.35, 0.35, 0.18)
    mat_gold = make_mat("Gold", (0.95, 0.68, 0.08), (0.85, 0.50, 0.05), 0.35, 0.92, 0.22)

    ring1 = None
    ring2 = None

    if kind == "sphere":
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=3, radius=0.72)
        core = bpy.context.active_object
        core.data.materials.append(mat_glow)
        core.parent = root

        bpy.ops.mesh.primitive_torus_add(major_radius=1.02, minor_radius=0.09, major_segments=32, minor_segments=12)
        ring1 = bpy.context.active_object
        ring1.rotation_euler = (0.65, 0.35, 0.0)
        ring1.data.materials.append(mat_main)
        ring1.parent = root

        bpy.ops.mesh.primitive_torus_add(major_radius=1.18, minor_radius=0.06, major_segments=32, minor_segments=12)
        ring2 = bpy.context.active_object
        ring2.rotation_euler = (-0.55, 0.75, 0.2)
        ring2.data.materials.append(mat_gold)
        ring2.parent = root

    elif kind == "talisman":
        bpy.ops.mesh.primitive_uv_sphere_add(segments=18, ring_count=12, radius=0.55)
        body = bpy.context.active_object
        body.scale = (0.85, 0.55, 1.15)
        body.data.materials.append(mat_gold)
        body.parent = root

        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.34, location=(0, -0.35, 0.1))
        gem = bpy.context.active_object
        gem.data.materials.append(mat_glow)
        gem.parent = root

        for sx in (-1, 1):
            bpy.ops.mesh.primitive_cube_add(size=0.55, location=(sx * 0.85, 0, 0.25))
            wing = bpy.context.active_object
            wing.scale = (1.15, 0.18, 0.65)
            wing.rotation_euler = (0, sx * -0.42, 0)
            wing.data.materials.append(mat_main)
            wing.parent = root

    elif kind == "cage":
        bpy.ops.mesh.primitive_cube_add(size=1.35)
        cube = bpy.context.active_object
        bpy.ops.object.modifier_add(type='WIREFRAME')
        cube.modifiers["Wireframe"].thickness = 0.16
        cube.data.materials.append(mat_main)
        cube.parent = root

        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.42)
        inner = bpy.context.active_object
        inner.data.materials.append(mat_glow)
        inner.parent = root

    elif kind == "plast":
        bpy.ops.mesh.primitive_cube_add(size=1.45)
        plate = bpy.context.active_object
        plate.scale = (1.05, 0.28, 1.05)
        plate.rotation_euler = (0.35, 0.0, 0.45)
        plate.data.materials.append(mat_main)
        plate.parent = root

        bpy.ops.mesh.primitive_torus_add(major_radius=0.68, minor_radius=0.09)
        ring1 = bpy.context.active_object
        ring1.rotation_euler = (1.3, 0.0, 0.45)
        ring1.data.materials.append(mat_glow)
        ring1.parent = root

    elif kind == "wings":
        for sx in (-1, 1):
            bpy.ops.mesh.primitive_cone_add(vertices=6, radius1=0.55, depth=1.55, location=(sx * 0.62, 0, 0.05))
            w = bpy.context.active_object
            w.scale = (0.85, 0.22, 1.05)
            w.rotation_euler = (0.0, sx * 0.48, 0.0)
            w.data.materials.append(mat_main)
            w.parent = root
        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=2, radius=0.35, location=(0, -0.2, 0.2))
        core = bpy.context.active_object
        core.data.materials.append(mat_glow)
        core.parent = root

    elif kind == "sword":
        bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=0.24, radius2=0.03, depth=1.75, location=(0, 0, 0.35))
        blade = bpy.context.active_object
        blade.scale = (1.0, 0.32, 1.0)
        blade.data.materials.append(mat_glow)
        blade.parent = root

        bpy.ops.mesh.primitive_cube_add(size=0.5, location=(0, 0, -0.42))
        guard = bpy.context.active_object
        guard.scale = (1.6, 0.35, 0.28)
        guard.data.materials.append(mat_gold)
        guard.parent = root

        bpy.ops.mesh.primitive_cylinder_add(radius=0.11, depth=0.65, location=(0, 0, -0.82))
        hilt = bpy.context.active_object
        hilt.data.materials.append(mat_main)
        hilt.parent = root
        root.rotation_euler = (0, 0.65, 0)

    elif kind == "pickaxe":
        bpy.ops.mesh.primitive_cylinder_add(radius=0.11, depth=1.85, location=(0, 0, -0.15))
        shaft = bpy.context.active_object
        shaft.data.materials.append(mat_gold)
        shaft.parent = root

        bpy.ops.mesh.primitive_torus_add(major_radius=0.78, minor_radius=0.16, location=(0, 0, 0.35))
        head = bpy.context.active_object
        head.scale = (1.15, 0.35, 0.55)
        head.data.materials.append(mat_glow)
        head.parent = root
        root.rotation_euler = (0, 0.62, 0)

    else:  # vortex / star / halo
        bpy.ops.mesh.primitive_torus_add(major_radius=0.88, minor_radius=0.16)
        ring1 = bpy.context.active_object
        ring1.rotation_euler = (1.1, 0.25, 0)
        ring1.data.materials.append(mat_main)
        ring1.parent = root

        bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.52)
        star = bpy.context.active_object
        star.data.materials.append(mat_glow)
        star.parent = root

    # Studio 3-point lighting + orthographic camera
    cam_data = bpy.data.cameras.new("Cam")
    cam_data.type = 'ORTHO'
    cam_data.ortho_scale = 2.75
    cam = bpy.data.objects.new("Cam", cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = (0, -4.0, 0.55)
    cam.rotation_euler = (1.43, 0, 0)
    bpy.context.scene.camera = cam

    for lx, ly, lz, eng, lcol in [(2.5, -2.5, 3.2, 65, (1.0, 0.98, 0.95)), (-2.5, -2.0, 1.5, 45, pcol), (0, 2.5, 2.5, 55, ecol)]:
        ld = bpy.data.lights.new("L", type='POINT')
        ld.energy = eng
        ld.color = lcol
        lo = bpy.data.objects.new("L", ld)
        bpy.context.collection.objects.link(lo)
        lo.location = (lx, ly, lz)

    scene = bpy.context.scene
    scene.render.engine = 'CYCLES'
    scene.cycles.samples = 8
    scene.cycles.use_denoising = False
    scene.render.resolution_x = 48
    scene.render.resolution_y = 48
    scene.render.film_transparent = True
    return root, ring1, ring2

# Build 3D multi-element JSON model per kind so in-hand/ground/GUI is TRUE 3D!
def make_3d_json_model(item_id, kind):
    tex_ref = f"kinggrief:item/{item_id}"
    all_faces = {
        "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "east":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "west":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "up":    {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "down":  {"uv": [0, 0, 16, 16], "texture": "#layer0"},
    }
    card_faces = {
        "north": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"},
        "east":  {"uv": [7, 0, 9, 16],  "texture": "#layer0"},
        "west":  {"uv": [7, 0, 9, 16],  "texture": "#layer0"},
        "up":    {"uv": [0, 7, 16, 9],  "texture": "#layer0"},
        "down":  {"uv": [0, 7, 16, 9],  "texture": "#layer0"},
    }

    if kind == "sphere":
        elements = [
            # 3D Glowing Core Cube
            {"from": [4, 4, 4], "to": [12, 12, 12], "faces": all_faces},
            # Tilted X-Gyro Ring
            {"from": [1, 7, 1], "to": [15, 9, 15], "rotation": {"angle": 22.5, "axis": "x", "origin": [8, 8, 8]}, "faces": all_faces},
            # Tilted Z-Gyro Ring
            {"from": [1, 7, 1], "to": [15, 9, 15], "rotation": {"angle": -22.5, "axis": "z", "origin": [8, 8, 8]}, "faces": all_faces},
            # Front/Back HD Animated Billboard Slice
            {"from": [0, 0, 7.4], "to": [16, 16, 8.6], "faces": card_faces},
        ]
    elif kind in ("cage", "plast"):
        thick_z = [5, 11] if kind == "plast" else [2, 14]
        elements = [
            {"from": [2, 2, thick_z[0]], "to": [14, 14, thick_z[1]], "faces": all_faces},
            {"from": [1, 1, 7.2], "to": [15, 15, 8.8], "rotation": {"angle": 22.5, "axis": "y", "origin": [8, 8, 8]}, "faces": card_faces},
        ]
    else:
        # 3D extruded emblem + angled 3D cross-depth element (looks rich 3D in hand & GUI!)
        elements = [
            {"from": [0, 0, 7.0], "to": [16, 16, 9.0], "faces": card_faces},
            {"from": [3, 3, 5.5], "to": [13, 13, 10.5], "rotation": {"angle": 22.5, "axis": "y", "origin": [8, 8, 8]}, "faces": all_faces},
        ]

    return {
        "gui_light": "front",
        "textures": {
            "layer0": tex_ref,
            "particle": tex_ref
        },
        "elements": elements,
        "display": {
            "gui": {"rotation": [18, 25, 0], "translation": [0, 0, 0], "scale": [0.92, 0.92, 0.92]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.55, 0.55, 0.55]},
            "fixed": {"rotation": [0, 180, 0], "scale": [0.95, 0.95, 0.95]},
            "thirdperson_righthand": {"rotation": [65, 35, 0], "translation": [0, 2.5, 1.5], "scale": [0.62, 0.62, 0.62]},
            "thirdperson_lefthand":  {"rotation": [65, 35, 0], "translation": [0, 2.5, 1.5], "scale": [0.62, 0.62, 0.62]},
            "firstperson_righthand": {"rotation": [0, -45, 15], "translation": [1.1, 3.2, 1.1], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand":  {"rotation": [0, -45, 15], "translation": [1.1, 3.2, 1.1], "scale": [0.68, 0.68, 0.68]}
        }
    }

FRAMES = 8
for idx, (item_id, kind, pcol, ecol) in enumerate(ITEMS_3D):
    root, ring1, ring2 = build_blender_rig(kind, pcol, ecol)
    strip = Image.new("RGBA", (48, 48 * FRAMES), (0, 0, 0, 0))

    for f in range(FRAMES):
        angle = (f / float(FRAMES)) * 2.0 * math.pi
        if kind in ("sword", "pickaxe", "wings", "talisman"):
            # Smooth 3D sway + bobbing animation so weapon/wings stay iconic from front & 3D
            root.rotation_euler[2] = 0.32 * math.sin(angle)
            root.rotation_euler[0] = 0.18 * math.cos(angle)
            root.location[2] = 0.08 * math.sin(angle)
        else:
            # Full 360° 3D rotation for Spheres, Trapka, Plast, Vortex, Halo!
            root.rotation_euler[2] = angle
            if ring1 is not None:
                ring1.rotation_euler[0] = 0.65 + angle
            if ring2 is not None:
                ring2.rotation_euler[1] = 0.75 - angle

        tmp_frame = f"/tmp/bl_frame_{f}.png"
        bpy.context.scene.render.filepath = tmp_frame
        bpy.ops.render.render(write_still=True)
        fim = Image.open(tmp_frame).convert("RGBA")
        # Add crisp dark outline + vivid color boost around the Cycles 3D render!
        arr = np.array(fim, dtype=np.float32)
        alpha = arr[:, :, 3]
        solid = alpha > 28
        # Dilate solid mask by 1px for crisp pixel-art outline
        dil = np.zeros_like(solid)
        for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            dil |= np.roll(np.roll(solid, dx, axis=1), dy, axis=0)
        outline = dil & (~solid)
        arr[solid, 0] = np.clip(arr[solid, 0] * 1.18, 0, 255)
        arr[solid, 1] = np.clip(arr[solid, 1] * 1.18, 0, 255)
        arr[solid, 2] = np.clip(arr[solid, 2] * 1.18, 0, 255)
        arr[outline] = [int(pcol[0]*70), int(pcol[1]*70), int(pcol[2]*70), 245]
        fim = Image.fromarray(arr.astype(np.uint8), "RGBA")
        strip.paste(fim, (0, f * 48))

    # Save master .blend file on the flagship item so the repo contains real Blender source!
    if idx == 0:
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT_BLEND_DIR, "kinggrief_3d_items_v5.blend"))

    png_path = os.path.join(tex_item_dir, f"{item_id}.png")
    strip.save(png_path)
    with open(png_path + ".mcmeta", "w", encoding="utf-8") as fp:
        json.dump({
            "animation": {
                "frametime": 2,
                "interpolate": True
            }
        }, fp, indent=2)

    with open(os.path.join(mod_item_dir, f"{item_id}.json"), "w", encoding="utf-8") as fp:
        json.dump(make_3d_json_model(item_id, kind), fp, indent=2)

    with open(os.path.join(def_item_dir, f"{item_id}.json"), "w", encoding="utf-8") as fp:
        json.dump({
            "model": {
                "type": "minecraft:model",
                "model": f"kinggrief:item/{item_id}"
            }
        }, fp, indent=2)

print("BLENDER 4.2 CYCLES RENDERED ALL", len(ITEMS_3D), "3D ANIMATED ITEMS!")

# ==============================================================================
# 3. PVP HIGHLIGHTED ORES + LOW FIRE + PACK.MCMETA (1.21 - 1.21.11 / 26.2)
# ==============================================================================
blk_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/textures/block")
os.makedirs(blk_dir, exist_ok=True)

ORE_SPECS = [
    ("diamond_ore",           (115, 115, 122), (34, 211, 238)),
    ("deepslate_diamond_ore", (62, 62, 70),    (34, 211, 238)),
    ("emerald_ore",           (115, 115, 122), (34, 197, 94)),
    ("deepslate_emerald_ore", (62, 62, 70),    (34, 197, 94)),
    ("gold_ore",              (115, 115, 122), (250, 204, 21)),
    ("deepslate_gold_ore",    (62, 62, 70),    (250, 204, 21)),
    ("iron_ore",              (115, 115, 122), (251, 146, 60)),
]
for oname, stone, border_c in ORE_SPECS:
    oim = Image.new("RGBA", (16, 16), (*stone, 255))
    opx = oim.load()
    for i in range(16):
        opx[i, 0] = (*border_c, 255)
        opx[i, 15] = (*border_c, 255)
        opx[0, i] = (*border_c, 255)
        opx[15, i] = (*border_c, 255)
    for gx, gy in [(4,4),(5,4),(4,5),(10,6),(11,6),(10,7),(6,11),(7,11),(7,12),(11,11)]:
        opx[gx, gy] = (*border_c, 255)
    oim.save(os.path.join(blk_dir, f"{oname}.png"))

# PvP Low Fire (transparent top 65% so fire never blinds screen in FT/RW PvP!)
for fn in ("fire_0", "fire_1"):
    fstrip = Image.new("RGBA", (16, 16 * 8), (0, 0, 0, 0))
    fpx = fstrip.load()
    for fr in range(8):
        for y in range(11, 16):
            for x in range(16):
                wave = int(2 * math.sin((x + fr * 2) * 0.7))
                if y + wave >= 11:
                    fpx[x, fr * 16 + y] = (255, 140 + (15 - y) * 20, 20, 215)
    fstrip.save(os.path.join(blk_dir, f"{fn}.png"))
    with open(os.path.join(blk_dir, f"{fn}.png.mcmeta"), "w") as fp:
        json.dump({"animation": {"frametime": 2}}, fp)

with open(os.path.join(OUT_RP_DIR, "pack.mcmeta"), "w", encoding="utf-8") as fp:
    json.dump({
        "pack": {
            "pack_format": 75,
            "supported_formats": [7, 99],
            "min_format": 7,
            "max_format": 99,
            "description": "§b§l❄ §c§lKINGGRIEF v5.0 §8· §f3D Blender Items, 40 Anim Ranks & FT/RW PvP"
        }
    }, fp, ensure_ascii=False, indent=2)

# Copy first frame of titan_sphere scaled to 64x64 as pack.png
pack_icon = Image.open(os.path.join(tex_item_dir, "titan_sphere.png")).crop((0, 0, 48, 48)).resize((64, 64))
pack_icon.save(os.path.join(OUT_RP_DIR, "pack.png"))

if os.path.exists(OUT_ZIP):
    os.remove(OUT_ZIP)
with zipfile.ZipFile(OUT_ZIP, "w", compression=zipfile.ZIP_DEFLATED) as zf:
    for root_d, _, files in os.walk(OUT_RP_DIR):
        for f in sorted(files):
            full = os.path.join(root_d, f)
            rel = os.path.relpath(full, OUT_RP_DIR)
            zf.write(full, rel)

sha1 = hashlib.sha1(open(OUT_ZIP, "rb").read()).hexdigest()
print(f"PACKAGED kinggrief-mega-rp.zip v5.0: {os.path.getsize(OUT_ZIP)} bytes | SHA1={sha1}")
