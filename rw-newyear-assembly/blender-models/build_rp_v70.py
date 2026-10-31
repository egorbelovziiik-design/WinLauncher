import atexit
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import tempfile
import zipfile

import numpy as np
from PIL import Image, ImageDraw

# The old /kinggrief-mega-rp.zip URL and its SHA-1 are still used by older
# server installations. Build to a new versioned filename instead of changing
# those bytes. The build directory is temporary and never deletes user files.
SCRIPT_DIR = Path(__file__).resolve().parent
ASSEMBLY_DIR = SCRIPT_DIR.parent
OUT_RP_DIR = tempfile.mkdtemp(prefix="kinggrief-rp-")
atexit.register(shutil.rmtree, OUT_RP_DIR, ignore_errors=True)
OUT_ZIP = os.environ.get("KINGGRIEF_RP_OUTPUT", str(ASSEMBLY_DIR / "kinggrief-mega-rp-v70.zip"))
# Rebuilding the separate ranks-only pack is opt-in, not a side effect.
OUT_RANKS_ONLY_ZIP = os.environ.get("KINGGRIEF_RP_RANKS_ZIP")

MODEL_DATA = json.loads((SCRIPT_DIR / "item-model-map-v70.json").read_text(encoding="utf-8"))
VANILLA_FALLBACKS = json.loads((SCRIPT_DIR / "vanilla-item-fallbacks.json").read_text(encoding="utf-8"))

# ==============================================================================
# 1. ALL 40 RANKS IN EXACT 16PX-TALL LUXURY REALLYWORLD "OWNER" SPLIT-PLAQUE STYLE!
#    Matches owner-badge/owner_preview_10x.png:
#    - 16px tall rounded dark bezel
#    - Left square medallion with 4 gold corner rivets + rich multi-tone 10x9 icon
#    - Golden hinge clasps between icon medallion and nameplate
#    - Right beveled plaque with 2x2 gold L-brackets, jewel checkerboard,
#      bold 8px-tall 2px-stroke outlined cream-gold letters & smooth 8-frame shimmer!
# ==============================================================================
RANKS_40 = [
    (0,  "owner",     "OWNER",     (68, 8, 22),   (232, 30, 68),   (255, 195, 38),  "crown"),
    (1,  "techadmin", "TECH.ADM",  (62, 10, 28),  (244, 63, 94),   (56, 210, 255),  "gear"),
    (2,  "curator",   "CURATOR",   (8, 56, 24),   (34, 205, 94),   (190, 242, 100), "clover"),
    (3,  "kp",        "KP",        (8, 54, 42),   (16, 195, 138),  (255, 215, 64),  "shield"),
    (4,  "glmoder",   "GL.MODER",  (42, 14, 76),  (155, 92, 252),  (255, 210, 75),  "sword"),
    (5,  "stmoder",   "ST.MODER",  (24, 20, 78),  (99, 112, 250),  (199, 210, 254), "sword"),
    (6,  "moder",     "MODER",     (12, 34, 82),  (59, 138, 252),  (147, 215, 255), "shield"),
    (7,  "sthelper",  "ST.HELPER", (10, 44, 72),  (14, 175, 240),  (186, 235, 255), "star"),
    (8,  "helper",    "HELPER",    (10, 50, 68),  (56, 198, 252),  (224, 245, 255), "star"),
    (9,  "downer",    "D.OWNER",   (68, 42, 6),   (250, 198, 22),  (255, 75, 85),   "crown"),
    (10, "opka",      "OPKA",      (58, 10, 68),  (217, 70, 245),  (255, 215, 50),  "bolt"),
    (11, "dkp",       "D.KP",      (68, 24, 6),   (255, 115, 16),  (255, 220, 68),  "shield"),
    (12, "dadmin",    "D.ADMIN",   (66, 10, 14),  (239, 68, 68),   (255, 205, 85),  "crown"),
    (13, "dmladmin",  "D.ML.ADM",  (64, 22, 8),   (249, 118, 26),  (254, 230, 85),  "sword"),
    (14, "dglmoder",  "D.GL.MOD",  (44, 16, 72),  (172, 88, 250),  (245, 190, 255), "sword"),
    (15, "dstmoder",  "D.ST.MOD",  (16, 36, 78),  (64, 140, 250),  (165, 225, 255), "shield"),
    (16, "dmoder",    "D.MODER",   (8, 54, 50),   (20, 194, 174),  (167, 248, 232), "shield"),
    (17, "dhelper",   "D.HELPER",  (10, 56, 52),  (45, 216, 196),  (215, 252, 245), "star"),
    (18, "mediaplus", "MEDIA+",    (68, 10, 32),  (248, 68, 104),  (255, 225, 72),  "play"),
    (19, "media",     "MEDIA",     (64, 12, 42),  (238, 76, 158),  (252, 195, 228), "play"),
    (20, "yt",        "YOUTUBE",   (72, 8, 10),   (255, 32, 64),   (255, 245, 245), "play"),
    (21, "twitch",    "TWITCH",    (42, 14, 74),  (152, 78, 255),  (238, 220, 255), "play"),
    (22, "tiktok",    "TIKTOK",    (18, 18, 30),  (255, 20, 92),   (0, 242, 234),   "play"),
    (23, "sponsor",   "SPONSOR",   (66, 46, 8),   (252, 196, 36),  (255, 245, 150), "gem"),
    (24, "bull",      "BULL",      (66, 26, 8),   (238, 96, 18),   (255, 220, 150), "horns"),
    (25, "king",      "KING",      (50, 14, 72),  (176, 92, 252),  (255, 212, 42),  "crown"),
    (26, "dragon",    "DRAGON",    (60, 10, 62),  (222, 74, 242),  (255, 150, 195), "fang"),
    (27, "imperator", "IMPERATOR", (66, 10, 26),  (246, 68, 98),   (255, 224, 72),  "crown"),
    (28, "overlord",  "OVERLORD",  (8, 54, 54),   (45, 216, 196),  (185, 248, 220), "bolt"),
    (29, "god",       "GOD",       (66, 50, 8),   (252, 208, 26),  (255, 252, 210), "star"),
    (30, "dracula",   "DRACULA",   (62, 6, 12),   (228, 38, 44),   (255, 155, 145), "fang"),
    (31, "tiger",     "TIGER",     (66, 32, 8),   (252, 150, 56),  (255, 240, 140), "fang"),
    (32, "vampire",   "VAMPIRE",   (64, 8, 18),   (242, 68, 74),   (252, 145, 145), "fang"),
    (33, "rabbit",    "RABBIT",    (12, 42, 68),  (56, 194, 250),  (228, 244, 255), "ears"),
    (34, "titan",     "TITAN",     (18, 34, 72),  (96, 168, 252),  (205, 228, 255), "shield"),
    (35, "bunny",     "BUNNY",     (62, 14, 42),  (238, 76, 158),  (252, 215, 236), "ears"),
    (36, "hero",      "HERO",      (12, 44, 68),  (56, 194, 250),  (255, 226, 76),  "sword"),
    (37, "cobra",     "COBRA",     (26, 56, 10),  (136, 212, 26),  (222, 252, 162), "fang"),
    (38, "pegas",     "PEGAS",     (10, 56, 26),  (36, 204, 98),   (195, 250, 215), "wing"),
    (39, "default",   "PLAYER",    (28, 36, 48),  (148, 166, 190), (228, 236, 246), "helm"),
    (40, "mlmoder",   "ML.MODER",  (10, 42, 86),  (38, 162, 252),  (168, 228, 255), "shield"),
    (41, "mladmin",   "ML.ADMIN",  (66, 22, 8),   (249, 115, 22),  (255, 224, 71),  "sword"),
    (42, "admin",     "ADMIN",     (72, 8, 18),   (239, 44, 68),   (255, 208, 52),  "crown"),
    (43, "curator_discord", "CUR.DS",  (27, 36, 90), (112, 135, 255), (209, 218, 255), "star"),
    (44, "curator_kp",      "CUR.KP",  (8, 56, 32),  (30, 205, 115),  (202, 255, 215), "shield"),
    (45, "curator_media",   "CUR.MED", (70, 18, 55), (239, 82, 166),  (255, 213, 238), "play"),
    (46, "curator_dkp",     "CUR.DKP", (72, 38, 10), (245, 154, 54),  (255, 229, 174), "shield"),
    (47, "gladmin",         "GL.ADMIN",(80, 10, 25), (250, 48, 75),   (255, 215, 122), "crown"),
    (48, "stadmin",         "ST.ADMIN",(76, 28, 12), (252, 115, 68),  (255, 222, 172), "crown"),
]

# Bold 2px-stroke 8px-tall Arcade/ReallyWorld Font (matches OWNER in owner_preview_10x.png!)
FONT_BOLD_8 = {
    'A': ["011110","110011","110011","111111","111111","110011","110011","110011"],
    'B': ["111110","110011","110011","111110","111110","110011","110011","111110"],
    'C': ["011111","110000","110000","110000","110000","110000","110000","011111"],
    'D': ["111110","110011","110011","110011","110011","110011","110011","111110"],
    'E': ["111111","110000","110000","111110","111110","110000","110000","111111"],
    'F': ["111111","110000","110000","111110","111110","110000","110000","110000"],
    'G': ["011111","110000","110000","110111","110011","110011","110011","011111"],
    'H': ["110011","110011","110011","111111","111111","110011","110011","110011"],
    'I': ["111111","001100","001100","001100","001100","001100","001100","111111"],
    'J': ["001111","000011","000011","000011","110011","110011","110011","011110"],
    'K': ["110011","110110","111100","111000","111100","110110","110011","110011"],
    'L': ["110000","110000","110000","110000","110000","110000","110000","111111"],
    'M': ["1100011","1110111","1111111","1101011","1100011","1100011","1100011","1100011"],
    'N': ["110011","111011","111111","110111","110011","110011","110011","110011"],
    'O': ["011110","110011","110011","110011","110011","110011","110011","011110"],
    'P': ["111110","110011","110011","111110","110000","110000","110000","110000"],
    'Q': ["011110","110011","110011","110011","110111","110011","011110","000011"],
    'R': ["111110","110011","110011","111110","111100","110110","110011","110011"],
    'S': ["011111","110000","110000","011110","000011","000011","110011","011110"],
    'T': ["111111","111111","001100","001100","001100","001100","001100","001100"],
    'U': ["110011","110011","110011","110011","110011","110011","110011","011110"],
    'V': ["110011","110011","110011","110011","110011","011110","011110","001100"],
    'W': ["1100011","1100011","1100011","1101011","1111111","1110111","1100011","0100010"],
    'X': ["110011","110011","011110","001100","011110","110011","110011","110011"],
    'Y': ["110011","110011","110011","011110","001100","001100","001100","001100"],
    'Z': ["111111","000011","000110","001100","011000","110000","110000","111111"],
    '+': ["00000","00100","00100","11111","11111","00100","00100","00000"],
    '.': ["00","00","00","00","00","00","11","11"],
    ' ': ["000","000","000","000","000","000","000","000"],
}

# Rich multi-shaded 10x8 icons ('.' empty, '1' dark shade, '2' main gold/accent, '3' bright highlight, '4' ruby/gem)
ICONS_10X8 = {
    "crown": [
        "....33....",
        "3..3223..3",
        "32.2222.23",
        "2222442222",
        "2322442212",
        "2221111212",
        "2222222212",
        ".11111111.",
    ],
    "clover": [
        "..33..33..",
        ".32233223.",
        ".22322322.",
        "..224422..",
        ".32222221.",
        "..122221..",
        "....21....",
        "...221....",
    ],
    "gear": [
        "...3223...",
        ".32222221.",
        "3221441221",
        "2214334121",
        "2214334121",
        "1221441221",
        ".12222221.",
        "...1221...",
    ],
    "shield": [
        ".33222211.",
        "3223333211",
        "3234444211",
        "2234334211",
        ".22444421.",
        ".12222211.",
        "..122211..",
        "...1211...",
    ],
    "sword": [
        ".......332",
        "......3321",
        ".....3321.",
        "..3.3321..",
        "..23321...",
        ".42221....",
        "24212.....",
        "21........",
    ],
    "star": [
        "....33....",
        "...3221...",
        "3332332111",
        ".32344221.",
        "..224421..",
        ".32211221.",
        "321....121",
        "..........",
    ],
    "bolt": [
        "....33322.",
        "...33221..",
        "..33221...",
        ".33322221.",
        "....3221..",
        "...3221...",
        "..321.....",
        ".31.......",
    ],
    "play": [
        "..33......",
        "..3322....",
        "..332221..",
        "..33442211",
        "..2244221.",
        "..22221...",
        "..221.....",
        "..........",
    ],
    "gem": [
        "..333322..",
        ".33444421.",
        "3343334221",
        "2244444211",
        ".22244211.",
        "..122211..",
        "...1211...",
        "....11....",
    ],
    "horns": [
        "33......22",
        "321....121",
        ".32233221.",
        "..234421..",
        "..224421..",
        "..122221..",
        "...1221...",
        "..........",
    ],
    "fang": [
        ".33....22.",
        "3223333221",
        "2243224311",
        ".22222221.",
        ".23.11.21.",
        ".21....11.",
        "..1....1..",
        "..........",
    ],
    "ears": [
        ".33....32.",
        ".321..321.",
        ".321..321.",
        "..332221..",
        ".32422421.",
        ".22233221.",
        "..122221..",
        "..........",
    ],
    "wing": [
        "......3332",
        "....333221",
        "..3332221.",
        ".3322221..",
        "3322211...",
        "22211.....",
        ".11.......",
        "..........",
    ],
    "helm": [
        "..333221..",
        ".33222221.",
        "3221111221",
        "2214334121",
        "2211111121",
        "221....121",
        ".11....11.",
        "..........",
    ],
}

def clamp_rgb(r, g, b):
    return (max(0, min(255, int(r))), max(0, min(255, int(g))), max(0, min(255, int(b))))

def render_luxury_owner_style_badge(label, bg, border, accent, icon_type, frame_idx):
    # Exact 16px-tall structure matching owner_preview_10x.png!
    h = 16
    char_widths = [len(FONT_BOLD_8.get(ch, FONT_BOLD_8[' '])[0]) for ch in label]
    text_w = sum(char_widths) + max(0, len(label) - 1)
    left_x0, left_x1 = 2, 15     # 14px wide left medallion
    clasp_x0, clasp_x1 = 16, 17  # 2px hinge groove + golden clasps
    right_x0 = 18
    pad_side = 5
    right_inner_w = text_w + pad_side * 2
    right_x1 = right_x0 + right_inner_w + 3
    w = right_x1 + 3

    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    dark_bezel = (*clamp_rgb(bg[0] * 0.28 + 8, bg[1] * 0.22 + 3, bg[2] * 0.28 + 8), 255)
    phase = (frame_idx / 8.0) * 2.0 * math.pi
    pulse = 0.90 + 0.12 * math.sin(phase)
    sweep_x = int(((frame_idx / 8.0) * (w + 14)) - 7)

    # 1. Rounded 2px Outer Dark Bezel (exact silhouette of owner.png!)
    for y in range(h):
        x_start = 2 if y in (0, 15) else (1 if y in (1, 14) else 0)
        x_end = (w - 3) if y in (0, 15) else ((w - 2) if y in (1, 14) else (w - 1))
        for x in range(x_start, x_end + 1):
            px[x, y] = dark_bezel

    # Helper for shimmer intensity
    def shim(x, y):
        d = abs((x + (y // 2)) - sweep_x)
        return max(0.0, 1.0 - d / 5.5)

    # 2. Left Square Icon Medallion (x=2..15, y=2..13)
    for y in range(2, 14):
        for x in range(left_x0, left_x1 + 1):
            s = shim(x, y)
            if x in (left_x0, left_x1) or y in (2, 13):
                lit = 1.12 if y == 2 else (0.78 if y == 13 else 0.95)
                c = clamp_rgb(border[0]*lit*pulse + accent[0]*0.38*s,
                              border[1]*lit*pulse + accent[1]*0.38*s,
                              border[2]*lit*pulse + accent[2]*0.38*s)
                px[x, y] = (*c, 255)
            else:
                checker = 0.85 if ((x + y) % 2 == 0) else 1.05
                c = clamp_rgb(bg[0]*checker + border[0]*0.18*s,
                              bg[1]*checker + border[1]*0.18*s,
                              bg[2]*checker + border[2]*0.18*s)
                px[x, y] = (*c, 255)

    # 4 Golden Corner Rivets on Left Medallion
    gold_hi = clamp_rgb(accent[0]*1.1 + 25, accent[1]*1.05 + 20, accent[2]*0.9 + 10)
    gold_mid = clamp_rgb(accent[0]*0.88, accent[1]*0.78, accent[2]*0.55)
    for rx, ry in [(left_x0, 2), (left_x1, 2), (left_x0, 13), (left_x1, 13)]:
        px[rx, ry] = (*(gold_hi if ry == 2 else gold_mid), 255)

    # Golden Hinge Clasps at x=16 (y=5 and y=10)
    for hy in range(3, 13):
        px[16, hy] = (*clamp_rgb(border[0]*0.45, border[1]*0.45, border[2]*0.45), 255)
    px[16, 5] = (*gold_hi, 255)
    px[16, 10] = (*gold_mid, 255)

    # Draw Shaded 10x8 Icon inside Left Medallion (x=4..13, y=4..11) with 1px dark outline
    icon_grid = ICONS_10X8.get(icon_type, ICONS_10X8["star"])
    bob = -1 if frame_idx in (2, 3) else 0
    icon_pts = {}
    for iy, row in enumerate(icon_grid):
        for ix, ch in enumerate(row):
            if ch != '.':
                icon_pts[(4 + ix, 4 + iy + bob)] = ch
    for (ix, iy) in list(icon_pts.keys()):
        for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            nx, ny = ix + dx, iy + dy
            if (nx, ny) not in icon_pts and 3 <= nx <= 14 and 3 <= ny <= 12:
                px[nx, ny] = dark_bezel
    for (ix, iy), ch in icon_pts.items():
        if not (3 <= ix <= 14 and 3 <= iy <= 12):
            continue
        s = shim(ix, iy)
        if ch == '3':
            col = clamp_rgb(255, 252, 225)
        elif ch == '2':
            col = clamp_rgb(accent[0] + 35*s, accent[1] + 30*s, accent[2] + 25*s)
        elif ch == '4':
            col = clamp_rgb(border[0]*1.2 + 40, border[1]*0.9 + 25, border[2]*1.2 + 40)
        else:
            col = clamp_rgb(accent[0]*0.68, accent[1]*0.58, accent[2]*0.42)
        px[ix, iy] = (*col, 255)

    # 3. Right Luxury Beveled Nameplate (x=right_x0..right_x1, y=2..13)
    for y in range(2, 14):
        for x in range(right_x0, right_x1 + 1):
            s = shim(x, y)
            if x in (right_x0, right_x1) or y in (2, 13):
                lit = 1.15 if y == 2 else (0.76 if y == 13 else 0.96)
                c = clamp_rgb(border[0]*lit*pulse + accent[0]*0.42*s,
                              border[1]*lit*pulse + accent[1]*0.42*s,
                              border[2]*lit*pulse + accent[2]*0.42*s)
                px[x, y] = (*c, 255)
            elif x in (right_x0 + 1, right_x1 - 1) or y in (3, 12):
                c = clamp_rgb(border[0]*0.58 + bg[0]*0.45 + accent[0]*0.25*s,
                              border[1]*0.58 + bg[1]*0.45 + accent[1]*0.25*s,
                              border[2]*0.58 + bg[2]*0.45 + accent[2]*0.25*s)
                px[x, y] = (*c, 255)
            else:
                # Subtle luxury jewel checkerboard interior
                chk = 1.14 if ((x + y) % 2 == 0) else 0.92
                grad = 1.15 - 0.25 * ((y - 4) / 7.0)
                c = clamp_rgb(bg[0]*chk*grad + border[0]*0.28*s,
                              bg[1]*chk*grad + border[1]*0.28*s,
                              bg[2]*chk*grad + border[2]*0.28*s)
                px[x, y] = (*c, 255)

    # 4 L-Shaped 2x2 Golden Corner Brackets on Right Nameplate (exact match to OWNER!)
    for bx, by, dx, dy in [
        (right_x0, 2, 1, 1),
        (right_x1, 2, -1, 1),
        (right_x0, 13, 1, -1),
        (right_x1, 13, -1, -1),
    ]:
        px[bx, by] = (*gold_hi, 255)
        px[bx + dx, by] = (*gold_mid, 255)
        px[bx, by + dy] = (*gold_mid, 255)

    # Animated Twinkling Sparkle Dots inside Left & Right Margins of Plaque
    sp1_y = 5 + (frame_idx % 5)
    sp2_y = 10 - (frame_idx % 5)
    if right_x0 + 3 < right_x1 - 3:
        px[right_x0 + 3, sp1_y] = (*(gold_hi if frame_idx % 2 == 0 else border), 255)
        px[right_x1 - 3, sp2_y] = (*(border if frame_idx % 2 == 0 else gold_hi), 255)

    # 4. Bold 8px-Tall Outlined Cream-Gold Block Letters (y=4..11)
    text_x0 = right_x0 + 2 + pad_side
    letter_pts = set()
    cx = text_x0
    for ch in label:
        glyph = FONT_BOLD_8.get(ch, FONT_BOLD_8[' '])
        gw = len(glyph[0])
        for gy, grow in enumerate(glyph):
            for gx, bit in enumerate(grow):
                if bit == '1':
                    letter_pts.add((cx + gx, 4 + gy))
        cx += gw + 1

    # Full 1px dark outline around all letters so text is 1000% crisp & readable!
    for (lx, ly) in letter_pts:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                nx, ny = lx + dx, ly + dy
                if (nx, ny) not in letter_pts and right_x0 + 1 < nx < right_x1 - 1 and 3 <= ny <= 12:
                    px[nx, ny] = dark_bezel

    # Cream-white -> warm ivory-gold vertical letter fill + diagonal shimmer!
    for (lx, ly) in letter_pts:
        s = shim(lx, ly)
        t = (ly - 4) / 7.0
        base_r = 255
        base_g = int(255 - 16 * t)
        base_b = int(252 - 58 * t)
        if s > 0.45:
            px[lx, ly] = (255, 255, 245, 255)
        else:
            px[lx, ly] = (base_r, base_g, base_b, 255)

    return img

font_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/textures/font")
os.makedirs(os.path.join(font_dir, "ranks"), exist_ok=True)
providers = []

# Generate preview sheet of all 41 luxury badges to verify visually!
badge_preview = Image.new("RGBA", (680, 25 * 38 + 24), (18, 22, 32, 255))

for idx, rid, label, bg, border, accent, icon_type in RANKS_40:
    for f in range(8):
        im = render_luxury_owner_style_badge(label, bg, border, accent, icon_type, f)
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
        if f == 0:
            col = idx % 2
            row = idx // 2
            scaled = im.resize((im.width * 2, im.height * 2), Image.NEAREST)
            badge_preview.paste(scaled, (16 + col * 335, 12 + row * 38), scaled)

badge_preview.save("/tmp/preview_all_40_badges.png")

# Legacy compatibility aliases so NO \uE900..\uE96F character EVER renders as a missing box '▯'!
spacer_path = os.path.join(font_dir, "legacy_spacer.png")
sp_im = Image.new("RGBA", (1, 8), (0, 0, 0, 0))
sp_im.putpixel((0, 0), (255, 255, 255, 1))
sp_im.save(spacer_path)

for f in range(13):
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/ranks/curator_{f % 8}.png",
        "ascent": 8, "height": 9, "chars": [chr(0xE940 + f)]
    })
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/ranks/owner_{f % 8}.png",
        "ascent": 8, "height": 9, "chars": [chr(0xE950 + f)]
    })

used_e9 = set(range(0xE940, 0xE94D)) | set(range(0xE950, 0xE95D)) | set(range(0xE970, 0xE980))
legacy_chars = "".join(chr(cp) for cp in range(0xE900, 0xE9FF) if cp not in used_e9)
providers.append({
    "type": "bitmap",
    "file": "minecraft:font/legacy_spacer.png",
    "ascent": 7, "height": 8, "chars": [legacy_chars]
})

# 16 Crisp HUD Icons (\uE970..\uE97F)
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
    him = Image.new("RGBA", (12, 10), (0, 0, 0, 0))
    hpx = him.load()
    grid = ICONS_10X8[ic]
    for y, r in enumerate(grid):
        for x, b in enumerate(r):
            if b != '.':
                mult = 1.2 if b == '3' else (0.75 if b == '1' else 1.0)
                c = clamp_rgb(col[0]*mult, col[1]*mult, col[2]*mult)
                hpx[x + 1, y + 1] = (*c, 255)
    him.save(os.path.join(font_dir, f"{hname}.png"))
    providers.append({
        "type": "bitmap",
        "file": f"minecraft:font/{hname}.png",
        "ascent": 7, "height": 8, "chars": [chr(cp)]
    })

os.makedirs(os.path.join(OUT_RP_DIR, "assets/minecraft/font"), exist_ok=True)
with open(os.path.join(OUT_RP_DIR, "assets/minecraft/font/default.json"), "w", encoding="utf-8") as fp:
    json.dump({"providers": providers}, fp, ensure_ascii=False, indent=2)

# Also rebuild standalone kinggrief-ranks-pack.zip so if user has both packs loaded, ALL 40 work!
if OUT_RANKS_ONLY_ZIP:
    ranks_meta = json.dumps({"pack": {"pack_format": 6, "supported_formats": [6, 75],
        "min_format": 6, "max_format": 75,
        "description": "§b§lKINGGRIEF v6.6 §8· §fАнимированные ранги и кураторы"}},
        ensure_ascii=False, indent=2).encode("utf-8")
    ranks_path = Path(OUT_RANKS_ONLY_ZIP)
    ranks_path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ranks_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as rzf:
        entries = [("pack.mcmeta", ranks_meta)]
        font_json = Path(OUT_RP_DIR) / "assets/minecraft/font/default.json"
        entries.append(("assets/minecraft/font/default.json", font_json.read_bytes()))
        for full in sorted(Path(font_dir).rglob("*")):
            if full.is_file(): entries.append((full.relative_to(OUT_RP_DIR).as_posix(), full.read_bytes()))
        for rel, data in sorted(entries):
            info = zipfile.ZipInfo(rel, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            rzf.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

print("1. Built ALL 40 Luxury OWNER-style Animated Rank Badges!")

# ==============================================================================
# 2. CRYSTAL-CLEAR, HIGH-CONTRAST 32x32 12-FRAME 3D ANIMATED ITEMS!
#    - Covers EVERY SINGLE exact modelId in ItemFactory.java + aliases!
#    - Binary crisp alpha (0 or 255) + 1px dark outline + bright specular rim light
#    - Iconic front-readable silhouette at all times (ZERO blurry 360 ghosting!)
#    - Uses native 3D voxel extrusion (item/generated & item/handheld) with
#      firstperson rotated toward the player's eyes so every detail is clear!
# ==============================================================================
ITEMS_SPECS = [
    # (primary_id, aliases, category, subtype, main_rgb, glow_rgb, trim_rgb)
    ("shar_titana",       ["titan_sphere"],     "sphere",   "titan",     (35, 125, 255),  (125, 225, 255), (255, 200, 45)),
    ("shar_andromedy",    ["andromeda_sphere"], "sphere",   "andromeda", (155, 55, 255),  (245, 130, 255), (255, 215, 70)),
    ("shar_chimery",      ["chimera_sphere"],   "sphere",   "chimera",   (25, 205, 95),   (145, 255, 175), (255, 195, 45)),
    ("shar_apollona",     ["apollo_sphere"],    "sphere",   "apollo",    (255, 175, 25),  (255, 245, 140), (255, 95, 35)),
    ("shar_aida",         [],                   "sphere",   "aida",      (220, 25, 45),   (255, 120, 50),  (65, 45, 75)),
    ("shar_osirisa",      [],                   "sphere",   "osiris",    (15, 195, 215),  (160, 255, 235), (255, 205, 50)),
    ("shar_aresa",        ["ares_sphere"],      "sphere",   "ares",      (195, 30, 30),   (255, 155, 55),  (255, 210, 65)),
    ("shar_santy",        [],                   "sphere",   "santa",     (235, 35, 65),   (135, 235, 255), (255, 220, 65)),

    ("tal_krushitelya",   ["krush_talisman"],   "talisman", "krush",     (235, 55, 30),   (255, 185, 50),  (255, 215, 65)),
    ("tal_grani",         [],                   "talisman", "grani",     (250, 195, 35),  (140, 240, 255), (255, 240, 150)),
    ("tal_dedala",        [],                   "talisman", "dedal",     (56, 189, 248),  (225, 248, 255), (255, 215, 65)),
    ("tal_tritona",       [],                   "talisman", "triton",    (15, 155, 235),  (110, 255, 235), (255, 215, 75)),
    ("tal_feniksa",       ["phoenix_talisman"], "talisman", "phoenix",   (255, 105, 20),  (255, 235, 85),  (235, 40, 50)),
    ("tal_echidny",       [],                   "talisman", "echidna",   (34, 197, 94),   (185, 255, 110), (155, 65, 235)),
    ("tal_stuzhi",        ["frost_talisman"],   "talisman", "frost",     (95, 210, 255),  (235, 252, 255), (255, 220, 80)),
    ("immortal_totem",    [],                   "talisman", "immortal",  (250, 204, 21),  (80, 255, 155),  (255, 245, 175)),
    # 9 NEW v5.7 CUSTOM 3D TALISMANS (INCLUDING DIVINE OWNER TALISMAN!)
    ("tal_owner",         ["owner_talisman"],   "talisman", "owner",     (245, 22, 62),   (255, 235, 95),  (255, 205, 35)),
    ("tal_karatelya",     [],                   "talisman", "karatel",   (195, 20, 35),   (255, 125, 35),  (95, 75, 105)),
    ("tal_zeusa",         [],                   "talisman", "zeus",      (250, 204, 21),  (125, 235, 255), (255, 248, 210)),
    ("tal_draculy",       [],                   "talisman", "dracula",   (175, 15, 55),   (255, 75, 125),  (58, 38, 72)),
    ("tal_kolossa",       [],                   "talisman", "koloss",    (115, 130, 155), (95, 245, 185),  (255, 195, 55)),
    ("tal_zhneca",        [],                   "talisman", "reaper",    (115, 45, 195),  (225, 125, 255), (215, 225, 240)),
    ("tal_severa",        [],                   "talisman", "sever",     (56, 189, 248),  (240, 252, 255), (145, 215, 255)),
    ("tal_bezdn",         [],                   "talisman", "void",      (78, 35, 155),   (56, 235, 255),  (215, 155, 255)),
    ("tal_imperatora",    [],                   "talisman", "imperator", (255, 155, 20),  (255, 245, 135), (235, 40, 65)),

    ("elytra_wind",       [],                   "wings",    "wind",      (125, 218, 255), (240, 252, 255), (255, 220, 85)),
    ("elytra_phoenix",    [],                   "wings",    "phoenix",   (255, 95, 20),   (255, 225, 70),  (195, 25, 35)),
    ("elytra_vampire",    [],                   "wings",    "vampire",   (205, 20, 50),   (255, 95, 125),  (55, 35, 68)),
    ("elytra_thunder",    [],                   "wings",    "thunder",   (250, 205, 35),  (115, 225, 255), (65, 95, 195)),
    ("elytra_titan",      [],                   "wings",    "titan",     (95, 115, 145),  (95, 215, 255),  (255, 195, 45)),
    ("elytra_santa",      [],                   "wings",    "santa",     (235, 30, 55),   (145, 242, 255), (255, 248, 245)),

    ("plast",             ["plast_item"],       "plast",    "plast",     (75, 55, 125),   (195, 115, 255), (65, 225, 255)),
    ("trapka",            ["trapka_item"],      "cage",     "trapka",    (72, 68, 86),    (255, 55, 95),   (255, 195, 55)),
    ("dezorient",         ["dezorient_item"],   "eye",      "dezorient", (175, 45, 235),  (255, 135, 245), (255, 215, 65)),
    ("yavnaya_pyl",       [],                   "flask",    "dust",      (56, 215, 255),  (255, 245, 135), (255, 205, 55)),
    ("bozhya_aura",       [],                   "halo",     "aura",      (255, 205, 45),  (255, 252, 210), (245, 248, 255)),
    ("ognenny_smerch",    [],                   "tornado",  "fire",      (255, 95, 20),   (255, 230, 75),  (215, 30, 35)),
    ("snezok_zamorozki",  ["snow_freeze"],      "snowflake","icebomb",   (95, 210, 255),  (240, 253, 255), (55, 135, 235)),
    ("pautinnaya_granata",["spider_net"],       "web",      "web",       (225, 242, 248), (105, 245, 175), (65, 175, 125)),
    # 3 NEW FTxRW PVP CONSUMABLES
    ("bozhya_kara",       [],                   "bolt",     "smite",     (255, 215, 35),  (135, 238, 255), (255, 252, 220)),
    ("grav_sfera",        [],                   "vortex",   "gravity",   (115, 35, 215),  (235, 95, 255),  (56, 215, 255)),
    ("sneg_zavesa",       [],                   "snowflake","veil",      (155, 230, 255), (250, 254, 255), (95, 175, 255)),

    ("pick_djamshut",     ["drill_pickaxe"],    "pickaxe",  "djamshut",  (255, 130, 20),  (255, 235, 85),  (82, 72, 92)),
    ("pick_spawner",      [],                   "pickaxe",  "spawner",   (250, 204, 21),  (95, 245, 255),  (165, 75, 245)),
    # 10 NEW CUSTOM 3D PICKAXES & HAMMERS -> 12 LEGENDARY PICKAXES TOTAL (incl. Кирка Крушителя 3x3 & Молот OWNER 10x10)!
    ("pick_krush",        [],                   "pickaxe",  "krush",     (255, 78, 20),   (255, 225, 65),  (58, 44, 68)),
    ("hammer_owner",      [],                   "hammer",   "owner",     (238, 22, 58),   (255, 215, 45),  (65, 18, 36)),
    ("pick_vein",         [],                   "pickaxe",  "vein",      (18, 205, 95),   (155, 255, 195), (38, 72, 58)),
    ("pick_frost",        [],                   "pickaxe",  "frost",     (48, 192, 255),  (235, 252, 255), (36, 68, 112)),
    ("pick_molten",       [],                   "pickaxe",  "molten",    (235, 58, 18),   (255, 215, 55),  (68, 36, 32)),
    ("pick_magnet",       [],                   "pickaxe",  "magnet",    (38, 168, 255),  (255, 55, 85),   (54, 62, 82)),
    ("pick_obsidian",     [],                   "pickaxe",  "obsidian",  (145, 48, 245),  (235, 145, 255), (36, 26, 54)),
    ("pick_midas",        [],                   "pickaxe",  "midas",     (252, 198, 24),  (255, 250, 185), (145, 92, 18)),
    ("pick_tunnel",       [],                   "hammer",   "tunnel",    (56, 138, 245),  (255, 208, 52),  (52, 58, 74)),
    ("pick_meteor",       [],                   "pickaxe",  "meteor",    (175, 52, 248),  (255, 225, 75),  (52, 38, 78)),
    # 4 NEW 3D WEAPONS & ARTIFACTS (Коса Жнеца, Секира Тора, Сфера Возврата, Магнит Лута)!
    ("scythe_reaper",     [],                   "scythe",   "reaper",    (215, 20, 62),   (215, 95, 255),  (44, 28, 52)),
    ("axe_thor",          [],                   "axe",      "thor",      (42, 175, 255),  (255, 232, 95),  (58, 68, 88)),
    ("orb_recall",        [],                   "sphere",   "recall",    (38, 212, 245),  (225, 255, 255), (255, 215, 65)),
    ("loot_magnet",       [],                   "talisman", "magnet",    (238, 38, 62),   (52, 188, 255),  (245, 248, 252)),
    ("sword_dracula",     [],                   "sword",    "dracula",   (215, 22, 48),   (255, 110, 135), (58, 42, 68)),
    ("sword_krush",       ["krush_sword"],      "sword",    "krush",     (255, 105, 20),  (255, 235, 85),  (75, 62, 82)),
    ("sword_owner",       [],                   "sword",    "owner",     (245, 20, 60),   (255, 235, 95),  (255, 200, 35)),
    # 9 BRAND-NEW 3D SWORDS FOR FULL 12-SWORD ARSENAL (/swords)!
    ("sword_frost",       [],                   "sword",    "frost",     (68, 205, 255),  (235, 253, 255), (38, 95, 185)),
    ("sword_zeus",        [],                   "sword",    "zeus",      (252, 208, 32),  (115, 242, 255), (242, 248, 255)),
    ("sword_katana",      [],                   "sword",    "katana",    (235, 240, 248), (255, 92, 168),  (48, 28, 42)),
    ("sword_wither",      [],                   "sword",    "wither",    (54, 42, 68),    (198, 52, 255),  (235, 38, 72)),
    ("sword_venom",       [],                   "sword",    "venom",     (28, 208, 88),   (192, 255, 85),  (38, 68, 52)),
    ("sword_phantom",     [],                   "sword",    "phantom",   (128, 68, 238),  (68, 238, 255),  (42, 38, 64)),
    ("sword_phoenix",     [],                   "sword",    "phoenix",   (252, 82, 18),   (255, 232, 72),  (145, 34, 28)),
    ("sword_void",        [],                   "sword",    "void",      (38, 32, 64),    (88, 225, 255),  (185, 95, 255)),
    ("sword_midas",       [],                   "sword",    "midas",     (252, 198, 22),  (255, 252, 195), (168, 98, 18)),
    ("mace_krush",        [],                   "mace",     "krush",     (245, 95, 25),   (255, 230, 75),  (85, 72, 95)),
    ("trident_poseidon",  [],                   "trident",  "poseidon",  (25, 195, 235),  (165, 252, 255), (255, 215, 65)),

    # 6 CUSTOM 3D TNT VARIETIES!
    ("tnt_tier1",         [],                   "tnt",      "tier2",     (225, 38, 38),   (255, 215, 75),  (245, 245, 248)),
    ("tnt_black",         [],                   "tnt",      "black",     (42, 36, 54),    (195, 75, 255),  (255, 195, 45)),
    ("tnt_nuke",          [],                   "tnt",      "nuke",      (45, 195, 65),   (245, 255, 65),  (38, 42, 48)),
    ("tnt_flash",         [],                   "tnt",      "flash",     (255, 175, 20),  (125, 238, 255), (255, 250, 225)),
    ("tnt_cryo",          [],                   "tnt",      "cryo",      (65, 195, 255),  (240, 253, 255), (45, 105, 195)),
    ("tnt_miner",         [],                   "tnt",      "miner",     (235, 125, 25),  (65, 235, 255),  (95, 85, 98)),
    ("tnt_storm",         [],                   "tnt",      "storm",     (235, 200, 45),  (235, 250, 255), (55, 105, 220)),
    ("tnt_gravity",       [],                   "tnt",      "gravity",   (130, 85, 200),  (210, 175, 255), (35, 35, 80)),
    ("tnt_ender",         [],                   "tnt",      "ender",     (65, 45, 120),   (195, 95, 255),  (85, 235, 215)),

    # 4 CUSTOM 3D BOWS WITH FULL 4-STAGE PULLING ANIMATION!
    ("bow_ice_rw",        [],                   "bow",      "icebow",    (85, 205, 255),  (235, 252, 255), (255, 215, 70)),
    ("bow_inferno",       [],                   "bow",      "inferno",   (245, 75, 20),   (255, 225, 65),  (95, 45, 55)),
    ("bow_zeus",          [],                   "bow",      "zeus",      (250, 205, 35),  (125, 238, 255), (245, 248, 255)),
    ("bow_vampire",       [],                   "bow",      "vampire",   (205, 18, 52),   (255, 95, 135),  (58, 38, 68)),

    # 6 CUSTOM 3D ARROWS!
    ("arrow_ice",         [],                   "arrow",    "ice",       (85, 210, 255),  (240, 253, 255), (205, 235, 255)),
    ("arrow_explosive",   [],                   "arrow",    "explosive", (245, 65, 25),   (255, 220, 65),  (85, 75, 85)),
    ("arrow_lightning",   [],                   "arrow",    "lightning", (250, 210, 35),  (135, 240, 255), (255, 250, 220)),
    ("arrow_vampire",     [],                   "arrow",    "vampire",   (210, 18, 55),   (255, 105, 140), (68, 45, 78)),
    ("arrow_poison",      [],                   "arrow",    "poison",    (34, 205, 85),   (185, 255, 105), (135, 65, 195)),
    ("arrow_anti_fly",    [],                   "arrow",    "antifly",   (155, 65, 245),  (95, 235, 255),  (235, 215, 75)),

    # 6 CUSTOM ARMOR SETS x 4 PIECES = 24 ARMOR MODELS!
    ("armor_krush_helmet",      [], "helmet",     "krush",   (225, 65, 25),   (255, 210, 65),  (75, 58, 72)),
    ("armor_krush_chestplate",  [], "chestplate", "krush",   (225, 65, 25),   (255, 210, 65),  (75, 58, 72)),
    ("armor_krush_leggings",    [], "leggings",   "krush",   (225, 65, 25),   (255, 210, 65),  (75, 58, 72)),
    ("armor_krush_boots",       [], "boots",      "krush",   (225, 65, 25),   (255, 210, 65),  (75, 58, 72)),

    ("armor_dracula_helmet",     [], "helmet",     "dracula", (195, 18, 52),  (255, 95, 135),  (52, 36, 65)),
    ("armor_dracula_chestplate", [], "chestplate", "dracula", (195, 18, 52),  (255, 95, 135),  (52, 36, 65)),
    ("armor_dracula_leggings",   [], "leggings",   "dracula", (195, 18, 52),  (255, 95, 135),  (52, 36, 65)),
    ("armor_dracula_boots",      [], "boots",      "dracula", (195, 18, 52),  (255, 95, 135),  (52, 36, 65)),

    ("armor_frost_helmet",      [], "helmet",     "frost",   (75, 195, 255),  (240, 253, 255), (255, 215, 75)),
    ("armor_frost_chestplate",  [], "chestplate", "frost",   (75, 195, 255),  (240, 253, 255), (255, 215, 75)),
    ("armor_frost_leggings",    [], "leggings",   "frost",   (75, 195, 255),  (240, 253, 255), (255, 215, 75)),
    ("armor_frost_boots",       [], "boots",      "frost",   (75, 195, 255),  (240, 253, 255), (255, 215, 75)),

    ("armor_zeus_helmet",       [], "helmet",     "zeus",    (250, 200, 30),  (120, 238, 255), (248, 250, 255)),
    ("armor_zeus_chestplate",   [], "chestplate", "zeus",    (250, 200, 30),  (120, 238, 255), (248, 250, 255)),
    ("armor_zeus_leggings",     [], "leggings",   "zeus",    (250, 200, 30),  (120, 238, 255), (248, 250, 255)),
    ("armor_zeus_boots",        [], "boots",      "zeus",    (250, 200, 30),  (120, 238, 255), (248, 250, 255)),

    ("armor_phantom_helmet",     [], "helmet",     "phantom", (95, 55, 185),  (56, 235, 255),  (45, 42, 62)),
    ("armor_phantom_chestplate", [], "chestplate", "phantom", (95, 55, 185),  (56, 235, 255),  (45, 42, 62)),
    ("armor_phantom_leggings",   [], "leggings",   "phantom", (95, 55, 185),  (56, 235, 255),  (45, 42, 62)),
    ("armor_phantom_boots",      [], "boots",      "phantom", (95, 55, 185),  (56, 235, 255),  (45, 42, 62)),

    ("armor_owner_helmet",      [], "helmet",     "owner",   (245, 18, 58),   (255, 238, 95),  (255, 198, 28)),
    ("armor_owner_chestplate",  [], "chestplate", "owner",   (245, 18, 58),   (255, 238, 95),  (255, 198, 28)),
    ("armor_owner_leggings",    [], "leggings",   "owner",   (245, 18, 58),   (255, 238, 95),  (255, 198, 28)),
    ("armor_owner_boots",       [], "boots",      "owner",   (245, 18, 58),   (255, 238, 95),  (255, 198, 28)),

    ("ny_mandarin",       [],                   "mandarin", "mandarin",  (255, 132, 18),  (255, 215, 95),  (42, 195, 75)),
    ("snowflake_coin",    [],                   "snowflake","coin",      (80, 205, 255),  (240, 252, 255), (255, 215, 65)),
    ("bounty_scroll",     [],                   "scroll",   "bounty",    (235, 215, 165), (235, 45, 55),   (255, 195, 45)),

    # Items introduced after the original v5.9 pack. The IDs must match the
    # item_model / CustomModelData assigned by KingGriefCore's ItemFactory.
    ("shar_kronosa",      [], "sphere", "kronos",    (136, 83, 233), (232, 205, 255), (255, 211, 75)),
    ("shar_geliosa",      [], "sphere", "gelios",    (255, 150, 24), (255, 243, 158), (255, 192, 42)),
    ("shar_morozko",      [], "sphere", "morozko",   (45, 181, 245), (228, 253, 255), (120, 220, 255)),
    ("shar_kvadrat",      [], "sphere", "kvadrat",   (208, 37, 221), (143, 247, 255), (255, 220, 80)),
    ("totem_rw",          [], "talisman", "rw",      (235, 115, 34), (255, 240, 130), (255, 204, 66)),
    ("totem_ledyanoy",    [], "talisman", "ice",     (53, 170, 245), (224, 252, 255), (116, 222, 255)),
    ("totem_voskresheniya", [], "talisman", "life",  (245, 183, 33), (238, 255, 172), (255, 226, 93)),
    ("totem_santy",       [], "talisman", "santa",   (226, 37, 67), (240, 252, 255), (255, 207, 70)),
    ("mega_heal",         [], "heal", "heal",        (232, 49, 105), (255, 225, 240), (255, 198, 73)),
    ("mega_buff",         [], "flask", "buff",       (236, 167, 32), (255, 244, 109), (255, 207, 73)),
    ("mega_shield",       [], "shield", "shield",    (35, 165, 224), (218, 250, 255), (255, 210, 83)),
    ("scroll_ice",        [], "scroll", "ice",       (235, 224, 191), (66, 191, 255), (204, 157, 85)),
    ("scroll_vampire",    [], "scroll", "vampire",   (235, 224, 191), (221, 37, 80), (204, 157, 85)),
    ("scroll_blast",      [], "scroll", "blast",     (235, 224, 191), (255, 132, 32), (204, 157, 85)),
    ("scroll_smelt",      [], "scroll", "smelt",     (235, 224, 191), (248, 186, 29), (204, 157, 85)),
    ("sword_sever",       [], "sword", "sever",      (72, 190, 247), (235, 253, 255), (92, 149, 190)),
    ("sword_zima",        [], "hammer", "zima",      (90, 180, 226), (233, 252, 255), (145, 210, 240)),
    ("tal_zvezdy",        [], "talisman", "star",    (238, 186, 25), (255, 248, 150), (255, 214, 66)),
    ("tal_moroza",        [], "talisman", "frost",   (60, 174, 235), (233, 252, 255), (144, 221, 255)),
    ("arrow_thor",        [], "arrow", "thor",       (39, 183, 240), (255, 236, 111), (123, 217, 255)),
    ("arrow_frostnova",   [], "arrow", "frostnova",  (83, 201, 248), (235, 252, 255), (189, 232, 255)),
    ("arrow_gold",        [], "arrow", "gold",       (241, 180, 24), (255, 245, 137), (188, 128, 34)),
]

# One distinct animated glyph/rank texture per rune. Model IDs are stable and
# deterministic: family-major order, the same numbering as WinterItemsManager.
RUNE_COLORS = [
    (87,183,236),(183,191,197),(86,193,145),(213,81,78),(236,112,53),
    (45,174,203),(197,217,238),(174,115,225),(78,163,199),(211,71,91),
    (152,139,236),(236,156,55),(187,107,170),(150,173,78),(84,205,187),
    (220,183,89),(56,150,211),(218,182,86),(164,98,209),(132,213,226),
]
for family, col in enumerate(RUNE_COLORS):
    for tier in range(1,6):
        number=family*5+tier
        iid=f"winter_rune_{number:03d}"
        ITEMS_SPECS.append((iid, [], "rune", f"{family:02d}_{tier}", col, (245,230,178), (34,45,64)))
ITEMS_SPECS.append(("mega_gapple", [], "mega_apple", "mega", (230,153,28), (255,225,100), (180,59,42)))

def render_item_frame_32(cat, sub, mcol, gcol, tcol, frame_idx, n_frames=12, pull_stage=-1):
    if cat == "rune":
        # Clear 32px crystal silhouette, unique family rune and 1–5 tier pips.
        family, tier = map(int, sub.split("_"))
        im = Image.new("RGBA", (32,32), (0,0,0,0)); d = ImageDraw.Draw(im)
        dark = tuple(int(v*0.33) for v in mcol)+(255,)
        light = tuple(min(255,int(v*0.55+100)) for v in mcol)+(255,)
        d.polygon([(16,1),(27,8),(29,19),(16,29),(3,19),(5,8)], fill=dark)
        d.polygon([(16,3),(25,9),(27,18),(16,26),(5,18),(7,9)], fill=(*mcol,255))
        d.polygon([(16,4),(16,25),(6,18),(8,9)], fill=light)
        d.line([(16,4),(16,25),(26,18)], fill=dark, width=1)
        # Family index is represented by a readable capital; colour and glyph
        # together distinguish the 20 effects, bottom pips distinguish tiers.
        glyph=FONT_BOLD_8["ABCDEFGHIJKLMNOPQRST"[family]]
        for gy,row in enumerate(glyph):
            for gx,pixel in enumerate(row):
                if pixel == "1": d.point((13+gx,8+gy),fill=(35,45,62,255))
        for pip in range(tier): d.rectangle((8+pip*4,21,10+pip*4,23),fill=(255,230,143,255))
        sparkle=6+(frame_idx*2)%18
        d.point((sparkle,9),fill=(255,255,245,255))
        return im
    if cat == "mega_apple":
        im=Image.new("RGBA",(32,32),(0,0,0,0)); d=ImageDraw.Draw(im)
        d.polygon([(7,7),(12,5),(16,7),(20,5),(25,7),(28,13),(27,21),(23,27),(18,29),(16,27),(14,29),(9,27),(5,21),(4,13)],fill=(90,44,35,255))
        d.polygon([(8,8),(12,7),(16,9),(20,7),(24,8),(26,13),(25,21),(22,25),(18,27),(16,25),(14,27),(10,25),(7,21),(6,13)],fill=(*mcol,255))
        d.polygon([(8,10),(12,8),(15,10),(15,23),(11,24),(8,20)],fill=(*gcol,255))
        d.rectangle((16,2,18,7), fill=(84,56,31,255)); d.polygon([(18,3),(23,1),(25,3),(20,6)],fill=(82,164,88,255))
        d.line([(12,16),(14,12),(16,16),(18,12),(20,16)],fill=(151,58,44,255),width=2)
        d.rectangle((8+(frame_idx%8),10,9+(frame_idx%8),11),fill=(255,255,231,255))
        return im
    if cat == "sphere":
        # This entire square is used as each face of the real 3-D cube model.
        im = Image.new("RGBA", (32, 32), (*mcol, 255))
        d = ImageDraw.Draw(im)
        dark = tuple(max(0, int(v * 0.38)) for v in mcol) + (255,)
        bright = tuple(min(255, int(v * 0.55 + 90)) for v in gcol) + (255,)
        d.rectangle((0, 0, 31, 31), fill=dark)
        d.rectangle((2, 2, 29, 29), fill=(*mcol, 255))
        d.rectangle((4, 4, 27, 27), outline=bright, width=2)
        for px,py in ((3,3),(26,3),(3,26),(26,26)):
            d.rectangle((px,py,px+2,py+2),fill=(*tcol,255))
        d.rectangle((7, 7, 24, 24), fill=dark)
        emblems = {"titan": "T", "andromeda": "A", "chimera": "C", "apollo": "A",
                   "aida": "D", "osiris": "O", "ares": "R", "santa": "S",
                   "kronos": "K", "gelios": "G", "morozko": "M", "kvadrat": "Q", "recall": "R"}
        glyph = FONT_BOLD_8.get(emblems.get(sub, "S"), FONT_BOLD_8["S"])
        for gy, row in enumerate(glyph):
            for gx, pixel in enumerate(row):
                if pixel == "1": d.rectangle((10 + gx * 2, 8 + gy * 2, 11 + gx * 2, 9 + gy * 2), fill=(*tcol, 255))
        # Animate an inner edge highlight without changing the square silhouette.
        marker = 4 + frame_idx * 2 % 22
        d.rectangle((marker, 3, min(marker + 2, 28), 4), fill=(255, 255, 255, 255))
        return im

    # Render on 64x64 supersampled canvas -> crisp 32x32 pixel-art with binary alpha & 1px dark outline!
    S = 32
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    # Normalized [-1, 1], y_up positive upward!
    nx = (xx - 15.5) / 14.0
    ny = (15.5 - yy) / 14.0
    r = np.sqrt(nx * nx + ny * ny)
    ang = np.arctan2(ny, nx)

    ph = (frame_idx / float(n_frames)) * 2.0 * math.pi
    bob = 0.045 * math.sin(ph)
    ny_b = ny - bob
    r_b = np.sqrt(nx * nx + ny_b * ny_b)

    rgb = np.zeros((S, S, 3), dtype=np.float32)
    mask = np.zeros((S, S), dtype=bool)

    m_v = np.array(mcol, dtype=np.float32)
    g_v = np.array(gcol, dtype=np.float32)
    t_v = np.array(tcol, dtype=np.float32)
    white = np.array([255.0, 255.0, 255.0], dtype=np.float32)

    # Moving specular light vector for rich 3D surface perception
    lx = -0.42 + 0.24 * math.cos(ph)
    ly = 0.52 + 0.16 * math.sin(ph)
    lz = 0.72
    ln = math.sqrt(lx*lx + ly*ly + lz*lz)
    lx, ly, lz = lx/ln, ly/ln, lz/ln

    if cat == "sphere":
        rad = 0.68
        in_orb = r_b <= rad
        z = np.sqrt(np.clip(rad*rad - nx*nx - ny_b*ny_b, 0.0, 1.0)) / rad
        snx, sny, snz = nx / rad, ny_b / rad, z
        diff = np.clip(snx * lx + sny * ly + snz * lz, 0.0, 1.0)
        spec = np.power(np.clip(diff, 0.0, 1.0), 9.0)
        fresnel = np.power(1.0 - z, 2.2)

        col = m_v * (0.35 + 0.78 * diff)[..., None] + g_v * (0.32 * fresnel + 0.25 * (1.0 - r_b / rad))[..., None] + white * (0.75 * spec)[..., None]
        rgb[in_orb] = col[in_orb]
        mask |= in_orb

        # Subtype unmistakable front 3D relief on the sphere!
        if sub == "santa":
            # Festive white fur brim + red Santa cap on top + golden 4-point star emblem in center!
            fur = (np.abs(ny_b - 0.48) <= 0.11) & (np.abs(nx) <= 0.56)
            hat = (ny_b > 0.54) & (ny_b <= 0.90) & (np.abs(nx - 0.12*(ny_b-0.54)*2) <= 0.48 * (1.0 - (ny_b-0.54)/0.44))
            pom = (nx - 0.34)**2 + (ny_b - 0.80)**2 <= 0.025
            star = ((np.abs(nx) <= 0.08) & (np.abs(ny_b + 0.05) <= 0.30)) | ((np.abs(ny_b + 0.05) <= 0.08) & (np.abs(nx) <= 0.30))
            rgb[hat] = [235, 30, 50]
            rgb[fur | pom] = [248, 250, 255]
            rgb[star & in_orb] = t_v * 1.08
            mask |= fur | hat | pom
        elif sub == "titan":
            band = in_orb & ((np.abs(nx) <= 0.10) | (np.abs(ny_b) <= 0.10) | ((r_b >= 0.56) & (r_b <= 0.68)))
            rgb[band] = t_v * (0.65 + 0.45 * diff)[band, None]
        elif sub in ("apollo", "gelios"):
            for k in range(8):
                a = k * (math.pi / 4.0) + 0.18 * math.sin(ph)
                rx_d = nx * math.cos(a) + ny_b * math.sin(a)
                ry_d = -nx * math.sin(a) + ny_b * math.cos(a)
                ray = (rx_d >= 0.62) & (rx_d <= 0.92) & (np.abs(ry_d) <= 0.11 * (1.0 - (rx_d - 0.62)/0.34))
                rgb[ray] = t_v if k % 2 == 0 else g_v
                mask |= ray
        elif sub == "kronos":
            clock_rim = in_orb & (np.abs(r_b - 0.40) <= 0.055)
            hands = in_orb & (((np.abs(nx) <= 0.055) & (ny_b >= 0) & (ny_b <= 0.28)) |
                              ((np.abs(ny_b) <= 0.055) & (nx >= 0) & (nx <= 0.25)))
            rgb[clock_rim | hands] = t_v
        elif sub == "morozko":
            snow = in_orb & (r_b <= 0.48) & ((np.abs(nx) <= 0.055) |
                    (np.abs(ny_b - nx * 0.58) <= 0.055) | (np.abs(ny_b + nx * 0.58) <= 0.055))
            rgb[snow] = white * 0.80 + g_v * 0.20
        elif sub == "kvadrat":
            ax, ay = np.abs(nx), np.abs(ny_b)
            square = in_orb & (ax <= 0.40) & (ay <= 0.40) & ((ax >= 0.29) | (ay >= 0.29))
            rgb[square] = t_v
        elif sub == "aida":
            for sx in (-1.0, 1.0):
                horn = ((nx - sx*0.48)**2 + (ny_b - 0.52)**2 <= 0.06) & ((nx - sx*0.36)**2 + (ny_b - 0.42)**2 >= 0.015)
                rgb[horn] = [75, 55, 88]
                mask |= horn
        elif sub == "ares":
            blades = in_orb & ((np.abs(nx - ny_b) <= 0.09) | (np.abs(nx + ny_b) <= 0.09)) & (r_b <= 0.52)
            rgb[blades] = t_v
        elif sub == "osiris":
            ankh = in_orb & (((np.abs(nx) <= 0.08) & (ny_b >= -0.38) & (ny_b <= 0.12)) | ((np.abs(ny_b + 0.04) <= 0.07) & (np.abs(nx) <= 0.28)) | (np.abs(np.sqrt(nx*nx + (ny_b-0.22)**2) - 0.15) <= 0.06))
            rgb[ankh] = t_v

        # Animated tilted 3D energy orbit ring (front arc overlaps sphere, back arc behind sphere!)
        tilt = 0.36
        rx_r = nx * math.cos(0.38) - ny_b * math.sin(0.38)
        ry_r = (nx * math.sin(0.38) + ny_b * math.cos(0.38)) / tilt
        rr = np.sqrt(rx_r * rx_r + ry_r * ry_r)
        on_ring = (rr >= 0.80) & (rr <= 0.96)
        front_half = ry_r < 0.0
        orb_ang = np.arctan2(ry_r, rx_r)
        pulse_ring = 0.65 + 0.35 * np.cos(orb_ang * 2.0 - ph * 2.0)
        ring_col = g_v * pulse_ring[..., None] + t_v * (1.0 - pulse_ring)[..., None]
        back_ring = on_ring & (~front_half) & (~mask)
        front_ring = on_ring & front_half
        rgb[back_ring] = ring_col[back_ring] * 0.78
        rgb[front_ring] = np.clip(ring_col[front_ring] * 1.12, 0, 255)
        mask |= on_ring

        # 2 crisp orbiting star gems!
        for k in range(2):
            oa = ph + k * math.pi
            ox = 0.88 * math.cos(oa) * math.cos(0.38) + 0.88 * tilt * math.sin(oa) * math.sin(0.38)
            oy = -0.88 * math.cos(oa) * math.sin(0.38) + 0.88 * tilt * math.sin(oa) * math.cos(0.38)
            sat = (np.abs(nx - ox) + np.abs(ny_b - oy)) <= 0.15
            if math.sin(oa) < 0 or True:
                rgb[sat] = white * 0.6 + g_v * 0.4
                mask |= sat

    elif cat == "talisman":
        flap = 0.08 * math.sin(ph)
        # Sculpted Winged Totem Idol!
        head = (np.abs(nx) <= 0.28) & (ny_b >= 0.24) & (ny_b <= 0.68)
        crown = (np.abs(nx) <= 0.34) & (ny_b >= 0.64) & (ny_b <= 0.82) & ((np.abs(nx) <= 0.09) | (np.abs(nx) >= 0.20) | (ny_b <= 0.72))
        torso = (np.abs(nx) <= 0.32 * (1.0 - 0.32 * np.clip(-ny_b, 0, 1))) & (ny_b >= -0.68) & (ny_b <= 0.28)
        wings = (np.abs(nx) >= 0.26) & (np.abs(nx) <= 0.92) & (
            np.abs(ny_b - (0.10 + (np.abs(nx) - 0.26) * (0.45 + flap))) <= (0.28 - 0.14 * (np.abs(nx) - 0.26))
        )
        body = head | torso
        shade = np.clip(0.75 + 0.35 * (-nx * 0.6 + ny_b * 0.5), 0.45, 1.25)
        rgb[wings] = (m_v * shade[..., None])[wings]
        # Feather ridges on wings
        ridge = wings & (((((np.abs(nx) * 14).astype(int)) % 2) == 0))
        rgb[ridge] = np.clip(rgb[ridge] * 1.22 + g_v * 0.18, 0, 255)
        rgb[body] = (t_v * shade[..., None])[body]
        rgb[crown] = g_v
        # Glowing eyes & pulsing chest gem!
        eyes = (np.abs(np.abs(nx) - 0.13) <= 0.06) & (np.abs(ny_b - 0.44) <= 0.06)
        gem = (np.abs(nx) + np.abs(ny_b + 0.06)) <= (0.23 + 0.03 * math.sin(ph * 2))
        rgb[eyes] = white * 0.7 + g_v * 0.3
        rgb[gem] = g_v * (0.85 + 0.15 * math.sin(ph * 2)) + white * 0.25
        mask |= wings | body | crown | eyes | gem
        if sub == "owner":
            # Divine 5-spiked Royal Crown + Ruby-Gold Seraphim Halo & Upper Wings!
            big_crown = (np.abs(nx) <= 0.46) & (ny_b >= 0.62) & (ny_b <= 0.92) & (
                (ny_b <= 0.74) | (np.abs(nx) <= 0.09) | (np.abs(np.abs(nx) - 0.22) <= 0.07) | (np.abs(np.abs(nx) - 0.39) <= 0.07)
            )
            halo_ring = (r_b >= 0.78) & (r_b <= 0.92) & (np.cos(ang * 6.0 - ph * 2.0) > -0.35)
            rgb[big_crown] = [255, 215, 45]
            rgb[big_crown & (ny_b >= 0.84)] = [255, 250, 190]
            rgb[halo_ring & (~mask)] = g_v * 0.9
            mask |= big_crown | halo_ring

    elif cat == "wings":
        # Wide, majestic, unmistakable Pair of Sculpted Multi-Feather Elytra Wings!
        flap = 0.10 * math.sin(ph)
        ax = np.abs(nx)
        top_curve = 0.66 * np.sin(np.clip((ax - 0.06) / 0.88, 0.0, 1.0) * math.pi * 0.82) + 0.12 + flap * ax
        bot_curve = -0.62 * np.power(np.clip(1.0 - np.abs(ax - 0.46) / 0.52, 0.0, 1.0), 0.75) + 0.10 * np.sin(ax * 18.0) + flap * ax * 0.6
        wing = (ax >= 0.08) & (ax <= 0.94) & (ny_b <= top_curve) & (ny_b >= bot_curve)
        shade = np.clip(0.65 + 0.48 * ((ny_b - bot_curve) / np.maximum(0.15, top_curve - bot_curve)), 0.4, 1.25)
        rgb[wing] = (m_v * shade[..., None])[wing]
        # Crisp white/gold top wing arch + glowing feather tips!
        top_trim = wing & ((top_curve - ny_b) <= 0.14)
        tips = wing & ((ny_b - bot_curve) <= 0.15)
        rgb[top_trim] = t_v
        rgb[tips] = g_v * (0.78 + 0.22 * math.sin(ph * 2))
        # Center armored clasp gem
        core = (ax + np.abs(ny_b - 0.08)) <= 0.22
        rgb[core] = g_v * 0.7 + white * 0.3
        mask |= wing | core

    elif cat == "plast":
        # 3D Isometric Heavy Armored Barrier Slab (5x5x2 Plast) with pulsing neon grid!
        u = (nx + ny_b * 1.45)
        v = (ny_b * 1.45 - nx)
        top_face = (np.abs(u) <= 0.88) & (np.abs(v) <= 0.88)
        left_side = (nx >= -0.88) & (nx <= 0.0) & (ny_b <= -0.60 - nx * 0.68) & (ny_b >= -0.92 - nx * 0.68)
        # Simpler rock-solid isometric diamond slab + extruded 3D side bevels:
        dx_iso = np.abs(nx) / 0.88 + np.abs(ny_b - 0.10) / 0.50
        top_d = dx_iso <= 1.0
        side_d = (np.abs(nx) <= 0.88) & (ny_b < 0.10 - (1.0 - np.abs(nx)/0.88)*0.50 + 0.02) & (ny_b >= -0.26 - (1.0 - np.abs(nx)/0.88)*0.50)
        rgb[side_d] = np.where((nx < 0)[..., None], m_v * 0.72, m_v * 0.50)[side_d]
        side_glow = side_d & (np.abs(ny_b - (-0.08 - (1.0 - np.abs(nx)/0.88)*0.50)) <= 0.06)
        rgb[side_glow] = t_v
        rgb[top_d] = m_v * 1.05
        # Glowing neon forcefield grid + expanding pulse on top slab!
        grid_lines = top_d & ((np.abs((nx + ny_b*1.7) * 5.0 - np.round((nx + ny_b*1.7) * 5.0)) <= 0.14) |
                              (np.abs((nx - ny_b*1.7) * 5.0 - np.round((nx - ny_b*1.7) * 5.0)) <= 0.14) |
                              (dx_iso >= 0.82))
        pulse_r = (frame_idx / float(n_frames))
        wave = top_d & (np.abs(dx_iso - pulse_r) <= 0.13)
        rgb[grid_lines] = g_v
        rgb[wave] = white * 0.55 + t_v * 0.45
        mask |= top_d | side_d

    elif cat == "cage":
        # 3D Isometric Iron-Obsidian Trapka 5x5x5 Prison Cage with glowing red-gold core!
        box = (np.abs(nx) <= 0.72) & (np.abs(ny_b) <= 0.72)
        bars_x = (np.abs(nx) >= 0.58) | (np.abs(nx) <= 0.08) | (np.abs(np.abs(nx) - 0.34) <= 0.07)
        bars_y = (np.abs(ny_b) >= 0.58) | (np.abs(ny_b) <= 0.08) | (np.abs(np.abs(ny_b) - 0.34) <= 0.07)
        frame = box & (bars_x | bars_y)
        inner_core = r_b <= (0.34 + 0.05 * math.sin(ph * 2))
        spikes = (np.abs(np.abs(nx) - 0.65) <= 0.08) & (ny_b >= 0.72) & (ny_b <= 0.88)
        rgb[inner_core] = g_v * (0.8 + 0.2 * math.sin(ph * 2)) + white * 0.22
        rgb[frame] = (m_v * np.clip(0.85 + 0.35 * (-nx + ny_b), 0.55, 1.35)[..., None])[frame]
        corners = box & (np.abs(nx) >= 0.56) & (np.abs(ny_b) >= 0.56)
        rgb[corners] = t_v
        rgb[spikes] = t_v
        mask |= frame | inner_core | spikes

    elif cat in ("sword", "pickaxe"):
        # Standard Minecraft 45° handheld diagonal: bottom-left (-,-) hilt -> top-right (+,+) tip!
        # u along diagonal [-1.2 .. +1.2], v perpendicular!
        u = (nx + ny) * 0.7071
        v = (ny - nx) * 0.7071
        wave_pos = -0.3 + 1.45 * (frame_idx / float(n_frames))

        if cat == "sword":
            # Distinct custom silhouettes across all 12 swords!
            if sub == "katana":
                # Sleek curved Samurai Katana with round tsuba guard
                vc = v - 0.14 * np.clip(u + 0.38, 0.0, 1.5) ** 2
                hilt = (u >= -1.06) & (u <= -0.44) & (np.abs(v + 0.02) <= 0.085)
                pommel = ((u + 1.05)**2 + (v + 0.02)**2) <= 0.022
                guard = ((u + 0.42)**2 + v*v) <= 0.075
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.68) / 0.44, 0.0, 1.0)
                blade = (u >= -0.38) & (u <= 1.10) & (np.abs(vc) <= 0.13 * taper + 0.025)
                fuller = blade & (vc >= 0.02) & (u <= 0.92)
            elif sub == "venom":
                # Hooked Persian Cobra Scimitar
                vc = v + 0.16 * np.sin(np.clip(u + 0.38, 0.0, 1.45) * 2.15)
                hilt = (u >= -1.02) & (u <= -0.45) & (np.abs(v) <= 0.095)
                pommel = ((u + 1.02)**2 + (v - 0.06)**2) <= 0.032
                guard = (np.abs(u + 0.42) <= 0.10) & (np.abs(v) <= 0.42)
                widen = 1.0 + 0.35 * np.clip(u - 0.15, 0.0, 0.65)
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.65) / 0.44, 0.0, 1.0)
                blade = (u >= -0.38) & (u <= 1.06) & (np.abs(vc) <= 0.19 * widen * taper + 0.025)
                fuller = blade & (np.abs(vc) <= 0.055) & (u <= 0.80)
            elif sub == "phantom":
                # Slender Assassin Rapier with swept basket guard
                hilt = (u >= -1.04) & (u <= -0.44) & (np.abs(v) <= 0.08)
                pommel = ((u + 1.04)**2 + v*v) <= 0.028
                ring = (np.abs(np.hypot(u + 0.56, v * 0.85) - 0.24) <= 0.055) & (u <= -0.34)
                guard = ((np.abs(u + 0.40) <= 0.08) & (np.abs(v) <= 0.52)) | ring
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.45) / 0.68, 0.0, 1.0)
                blade = (u >= -0.38) & (u <= 1.12) & (np.abs(v) <= 0.125 * taper + 0.022)
                fuller = blade & (np.abs(v) <= 0.04) & (u <= 0.86)
            elif sub == "phoenix":
                # Flamberge Wave-Edge Solar Greatsword
                wave_edge = 0.045 * np.sin(u * 15.0)
                hilt = (u >= -1.04) & (u <= -0.44) & (np.abs(v) <= 0.105)
                pommel = ((u + 1.03)**2 + v*v) <= 0.036
                guard = (np.abs(u + 0.40 - 0.16 * np.abs(v)) <= 0.11) & (np.abs(v) <= 0.56)
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.55) / 0.54, 0.0, 1.0)
                blade = (u >= -0.38) & (u <= 1.08) & (np.abs(v - wave_edge * 0.5) <= (0.24 + np.abs(wave_edge)) * taper + 0.03)
                fuller = blade & (np.abs(v) <= 0.07) & (u <= 0.80)
            elif sub == "void":
                # Split-Core Astral Greatsword (twin dark blades around glowing energy rift!)
                hilt = (u >= -1.04) & (u <= -0.44) & (np.abs(v) <= 0.11)
                pommel = ((u + 1.03)**2 + v*v) <= 0.038
                guard = (np.abs(u + 0.42) <= (0.12 + 0.10 * np.abs(v))) & (np.abs(v) <= 0.54)
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.56) / 0.54, 0.0, 1.0)
                outer = (u >= -0.38) & (u <= 1.08) & (np.abs(v) <= 0.27 * taper + 0.035)
                rift = (u >= -0.22) & (u <= 0.68) & (np.abs(v) <= 0.055)
                blade = outer & (~rift)
                fuller = (u >= -0.26) & (u <= 0.82) & (np.abs(np.abs(v) - 0.085) <= 0.035) & outer
            elif sub in ("wither", "frost", "zeus"):
                # Jagged Sawtooth / Crystal Spike / Forked Thunder Greatsword!
                teeth = 0.055 * ((np.floor((u + 0.3) * 8.0) % 2 == 0).astype(np.float32)) * ((u >= -0.15) & (u <= 0.72))
                hilt = (u >= -1.04) & (u <= -0.44) & (np.abs(v) <= 0.105)
                pommel = ((u + 1.02)**2 + v*v) <= 0.035
                guard = (np.abs(u + 0.42 + (0.14 * np.abs(v) if sub == "wither" else -0.12 * np.abs(v))) <= 0.115) & (np.abs(v) <= 0.54)
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.54) / 0.56, 0.0, 1.0)
                fork_gap = (sub == "zeus") & (u >= 0.78) & (np.abs(v) <= 0.045)
                blade = (u >= -0.38) & (u <= 1.08) & (np.abs(v) <= (0.23 + teeth) * taper + 0.03) & (~fork_gap)
                fuller = blade & (np.abs(v) <= 0.07) & (u <= 0.80)
            else:
                hilt = (u >= -1.02) & (u <= -0.46) & (np.abs(v) <= 0.10)
                pommel = ((u + 1.02)**2 + v*v) <= 0.032
                guard = (np.abs(u + 0.42) <= (0.11 + 0.08 * np.abs(v))) & (np.abs(v) <= 0.48)
                taper = np.clip(1.0 - np.maximum(0.0, u - 0.52) / 0.58, 0.0, 1.0)
                blade = (u >= -0.38) & (u <= 1.06) & (np.abs(v) <= 0.22 * taper + 0.03)
                fuller = blade & (np.abs(v) <= 0.065) & (u <= 0.78)

            rgb[hilt] = t_v * 0.85
            rgb[pommel] = g_v
            rgb[guard] = t_v if sub in ("dracula", "wither", "katana", "venom") else np.array([255, 204, 48], dtype=np.float32)
            rgb[blade] = np.where((v >= 0)[..., None], m_v * 1.18 + 30, m_v * 0.78)[blade]
            rgb[fuller] = g_v
            ewave = blade & (np.abs(u - wave_pos) <= 0.14)
            rgb[ewave] = white * 0.65 + g_v * 0.35
            mask |= hilt | pommel | guard | blade
        else:
            # 45° Pickaxe: sturdy shaft + curved heavy twin pickaxe head at u=+0.52!
            shaft = (u >= -1.04) & (u <= 0.66) & (np.abs(v) <= 0.095)
            pommel = ((u + 1.02)**2 + v*v) <= 0.026
            curve_u = 0.52 - 0.42 * (v * v)
            head = (np.abs(v) <= 0.86) & (np.abs(u - curve_u) <= (0.20 * (1.0 - 0.55 * np.abs(v)) + 0.04))
            tips = head & (np.abs(v) >= 0.52)
            ewave = head & (np.abs(np.abs(v) - (frame_idx / float(n_frames)) * 0.86) <= 0.14)

            rgb[shaft] = [135, 92, 48] if sub == "djamshut" else t_v * 0.75
            rgb[pommel] = g_v
            rgb[head] = (m_v * np.clip(0.85 + 0.35 * (u - curve_u) * 4.0, 0.6, 1.3)[..., None])[head]
            rgb[tips] = g_v
            rgb[ewave] = white * 0.6 + g_v * 0.4
            mask |= shaft | pommel | head

    elif cat == "bow":
        # Full 4-Stage Bow Animation! pull_stage: -1=idle, 0=pulling_0, 1=pulling_1, 2=pulling_2
        u = (nx + ny) * 0.7071
        v = (ny - nx) * 0.7071
        flex = 0.0 if pull_stage < 0 else (0.12 + 0.11 * pull_stage)
        arc_u = (0.38 - 0.10 * flex) - (0.72 + 0.35 * flex) * (v * v)
        limb = (np.abs(v) <= (0.88 - 0.04 * flex)) & (np.abs(u - arc_u) <= 0.115)
        tips_u = (0.38 - 0.10 * flex) - (0.72 + 0.35 * flex) * (0.82 * 0.82)
        if pull_stage < 0:
            # Taut straight bowstring when idle!
            string = (np.abs(v) <= 0.82) & (np.abs(u - tips_u) <= 0.045)
            rgb[string] = [225, 240, 255]
            rgb[limb] = (m_v * np.clip(0.85 + 0.35 * (u - arc_u) * 4.0, 0.65, 1.3)[..., None])[limb]
            rgb[limb & (np.abs(v) <= 0.22)] = t_v
            rgb[limb & (np.abs(v) >= 0.72)] = g_v
            mask |= limb | string
        else:
            # V-shaped pulled bowstring + nocked elemental arrow!
            pull_back = tips_u - (0.18 + 0.20 * pull_stage)
            str_target_u = pull_back + (tips_u - pull_back) * (np.abs(v) / 0.82)
            string = (np.abs(v) <= 0.82) & (np.abs(u - str_target_u) <= 0.05)
            arr_tail = pull_back
            arr_head = arr_tail + 1.12
            shaft = (u >= arr_tail) & (u <= arr_head - 0.16) & (np.abs(v) <= 0.055)
            fletch = (u >= arr_tail) & (u <= arr_tail + 0.22) & (np.abs(np.abs(v) - 0.11) <= 0.055)
            tip = (u >= arr_head - 0.24) & (u <= arr_head + 0.06) & (np.abs(v) <= 0.20 * np.clip(1.0 - (u - (arr_head - 0.24)) / 0.30, 0.0, 1.0))
            rgb[string] = [235, 246, 255]
            rgb[limb] = (m_v * np.clip(0.85 + 0.35 * (u - arc_u) * 4.0, 0.65, 1.3)[..., None])[limb]
            rgb[limb & (np.abs(v) <= 0.22)] = t_v
            rgb[limb & (np.abs(v) >= 0.70)] = g_v
            rgb[shaft] = [185, 145, 95]
            rgb[fletch] = t_v
            rgb[tip] = g_v * (0.85 + 0.15 * math.sin(ph * 2)) + white * (0.25 if pull_stage == 2 else 0.10)
            if pull_stage == 2:
                spark = ((u - (arr_head - 0.06))**2 + v*v) <= 0.055
                rgb[spark] = white * 0.7 + g_v * 0.3
                mask |= spark
            mask |= limb | string | shaft | fletch | tip

    elif cat == "arrow":
        # Crisp 45° Elemental Arrow: feathered tail (-,-) -> razor broadhead (+,+)!
        u = (nx + ny) * 0.7071
        v = (ny - nx) * 0.7071
        shaft = (u >= -0.82) & (u <= 0.62) & (np.abs(v) <= 0.065)
        fletch = (u >= -0.96) & (u <= -0.54) & (np.abs(np.abs(v) - (0.08 + 0.32 * (-0.54 - u))) <= 0.075)
        head = (u >= 0.44) & (u <= 0.98) & (np.abs(v) <= 0.28 * np.clip(1.0 - (u - 0.44) / 0.54, 0.0, 1.0) + 0.02)
        barb = (u >= 0.36) & (u <= 0.52) & (np.abs(np.abs(v) - 0.20) <= 0.07)
        rgb[shaft] = t_v * 0.78
        rgb[fletch] = g_v
        rgb[head | barb] = m_v * 1.12
        rgb[head & (np.abs(v) <= 0.08)] = g_v * 0.75 + white * 0.25
        mask |= shaft | fletch | head | barb

    elif cat == "helmet":
        dome = (np.abs(nx) <= 0.64) & (ny_b >= -0.46) & (ny_b <= 0.58) & ((nx*nx / 0.46 + (ny_b - 0.05)**2 / 0.38) <= 1.18)
        visor = dome & (np.abs(nx) <= 0.42) & (ny_b >= -0.26) & (ny_b <= 0.04)
        nose = (np.abs(nx) <= 0.09) & (ny_b >= -0.34) & (ny_b <= 0.22)
        horns = (np.abs(nx) >= 0.48) & (np.abs(nx) <= 0.86) & (ny_b >= 0.24) & (ny_b <= 0.78) & (np.abs(ny_b - (0.20 + (np.abs(nx) - 0.44) * 1.25)) <= 0.14)
        crest = (np.abs(nx) <= 0.34) & (ny_b >= 0.50) & (ny_b <= 0.84) & ((sub == "owner") | (np.abs(nx) <= 0.14))
        shade = np.clip(0.75 + 0.38 * (-nx * 0.6 + ny_b * 0.5), 0.48, 1.28)
        rgb[dome] = (m_v * shade[..., None])[dome]
        rgb[dome & (np.abs(ny_b - 0.22) <= 0.08)] = t_v
        rgb[visor] = g_v * (0.75 + 0.25 * math.sin(ph * 2))
        rgb[nose] = t_v
        rgb[horns | crest] = g_v if sub == "owner" else t_v
        mask |= dome | horns | crest

    elif cat == "chestplate":
        shoulders = (np.abs(nx) <= 0.86) & (np.abs(nx) >= 0.26) & (ny_b >= 0.18) & (ny_b <= 0.68)
        torso = (np.abs(nx) <= 0.58 * (1.0 - 0.22 * np.clip(-ny_b, 0, 1))) & (ny_b >= -0.72) & (ny_b <= 0.52)
        neck_cut = (np.abs(nx) <= 0.24) & (ny_b >= 0.36)
        plate = (shoulders | torso) & (~neck_cut)
        shade = np.clip(0.74 + 0.38 * (-nx * 0.55 + ny_b * 0.45), 0.48, 1.28)
        rgb[plate] = (m_v * shade[..., None])[plate]
        trim = plate & ((ny_b >= 0.52) | (ny_b <= -0.58) | (np.abs(np.abs(nx) - 0.52) <= 0.07))
        core = plate & ((np.abs(nx) + np.abs(ny_b - 0.04)) <= 0.26)
        rgb[trim] = t_v
        rgb[core] = g_v * (0.82 + 0.18 * math.sin(ph * 2)) + white * 0.2
        mask |= plate

    elif cat == "leggings":
        belt = (np.abs(nx) <= 0.58) & (ny_b >= 0.42) & (ny_b <= 0.68)
        legs = (np.abs(np.abs(nx) - 0.32) <= 0.22) & (ny_b >= -0.74) & (ny_b <= 0.46)
        knees = legs & (np.abs(ny_b + 0.08) <= 0.14)
        cuffs = legs & (ny_b <= -0.56)
        all_leg = belt | legs
        shade = np.clip(0.76 + 0.34 * (-nx * 0.5 + ny_b * 0.4), 0.5, 1.25)
        rgb[all_leg] = (m_v * shade[..., None])[all_leg]
        rgb[belt & (np.abs(nx) <= 0.16)] = g_v
        rgb[knees] = g_v
        rgb[cuffs] = t_v
        mask |= all_leg

    elif cat == "boots":
        shins = (np.abs(np.abs(nx) - 0.36) <= 0.20) & (ny_b >= -0.48) & (ny_b <= 0.36)
        soles = (np.abs(np.abs(nx) - 0.42) <= 0.28) & (ny_b >= -0.68) & (ny_b <= -0.34)
        wings_b = (np.abs(nx) >= 0.54) & (np.abs(nx) <= 0.80) & (ny_b >= -0.10) & (ny_b <= 0.34)
        boot = shins | soles
        shade = np.clip(0.76 + 0.34 * (-nx * 0.5 + ny_b * 0.4), 0.5, 1.25)
        rgb[boot] = (m_v * shade[..., None])[boot]
        rgb[soles & (ny_b <= -0.54)] = t_v
        rgb[shins & (ny_b >= 0.22)] = g_v
        rgb[wings_b] = g_v
        mask |= boot | wings_b

    elif cat == "bolt":
        # Divine Lightning Bolt (Bozhya Kara)!
        zig_x = np.where(ny_b >= 0.0, -0.22 + 0.55 * ny_b, 0.22 + 0.55 * ny_b)
        bolt = (np.abs(ny_b) <= 0.86) & (np.abs(nx - zig_x) <= (0.22 - 0.10 * np.abs(ny_b)))
        aura = (r_b >= 0.58) & (r_b <= 0.78) & (np.cos(ang * 6.0 + ph * 2.0) > 0.25)
        rgb[aura] = g_v
        rgb[bolt] = m_v
        rgb[bolt & (np.abs(nx - zig_x) <= 0.08)] = white
        mask |= bolt | aura

    elif cat == "vortex":
        # Gravity Black Hole Vortex (Grav Sfera)!
        arms = np.zeros((S, S), dtype=bool)
        spiral = np.sin(ang * 3.0 + r_b * 8.0 - ph * 2.0)
        swirl = (r_b >= 0.24) & (r_b <= 0.88) & (spiral > 0.15)
        core = r_b <= 0.34
        rim = (r_b >= 0.30) & (r_b <= 0.40)
        rgb[swirl] = g_v
        rgb[core] = m_v * 0.45
        rgb[rim] = t_v
        mask |= swirl | core

    elif cat == "tnt":
        # Crisp 3D Isometric Dynamite Block + Label Band + Sparkling Fuse on Top!
        top_rhomb = (np.abs(nx) / 0.78 + np.abs(ny_b - 0.34) / 0.36) <= 1.0
        left_face = (nx >= -0.78) & (nx <= 0.0) & (ny_b <= 0.34 - (1.0 + nx / 0.78) * 0.36 + 0.03) & (ny_b >= -0.52 - (1.0 + nx / 0.78) * 0.32)
        right_face = (nx > 0.0) & (nx <= 0.78) & (ny_b <= 0.34 - (1.0 - nx / 0.78) * 0.36 + 0.03) & (ny_b >= -0.52 - (1.0 - nx / 0.78) * 0.32)
        sides = left_face | right_face
        band_y = -0.10 - (1.0 - np.abs(nx) / 0.78) * 0.34
        band = sides & (np.abs(ny_b - band_y) <= 0.16)
        fuse = (np.abs(nx - 0.08 * np.sin(ny_b * 8.0)) <= 0.07) & (ny_b >= 0.34) & (ny_b <= 0.76)
        spark = ((nx - 0.05)**2 + (ny_b - 0.80)**2) <= (0.038 + 0.018 * math.sin(ph * 3.0))
        rgb[left_face] = m_v * 0.92
        rgb[right_face] = m_v * 0.68
        # Vertical dynamite stick seams
        seams = sides & ((np.abs(np.abs(nx) - 0.28) <= 0.04) | (np.abs(np.abs(nx) - 0.56) <= 0.04))
        rgb[seams] = m_v * 0.48
        rgb[band] = t_v
        rgb[band & (np.abs(nx) <= 0.34) & (np.abs(ny_b - band_y) <= 0.08)] = g_v
        rgb[top_rhomb] = m_v * 1.15
        rgb[top_rhomb & ((np.abs(nx) / 0.45 + np.abs(ny_b - 0.34) / 0.22) <= 1.0)] = g_v * 0.65 + t_v * 0.35
        rgb[fuse] = [215, 205, 185]
        rgb[spark] = white * 0.6 + g_v * 0.4
        mask |= top_rhomb | sides | fuse | spark

    elif cat in ("hammer", "scythe", "axe"):
        u = (nx + ny) * 0.7071
        v = (ny - nx) * 0.7071
        shaft = (u >= -1.04) & (u <= 0.56) & (np.abs(v) <= 0.085)
        pommel = ((u + 1.02)**2 + v*v) <= 0.032
        rgb[shaft] = t_v * 0.85
        rgb[pommel] = g_v
        if cat == "hammer":
            # Colossal Twin-Headed Royal Warhammer (Молот OWNER'а 10x10 / Бур Титана 5x5)!
            block_h = (u >= 0.24) & (u <= 0.82) & (np.abs(v) <= 0.54)
            bevel = (u >= 0.18) & (u <= 0.88) & (np.abs(np.abs(v) - 0.50) <= 0.11)
            crown_tip = (u >= 0.80) & (u <= 1.04) & (np.abs(v) <= 0.18 * (1.05 - u) * 3.2)
            gem = ((u - 0.53)**2 + v*v) <= 0.055
            shade = np.clip(0.84 + 0.35 * v * 1.8, 0.55, 1.32)
            rgb[block_h] = (m_v * shade[..., None])[block_h]
            rgb[bevel | crown_tip] = g_v
            rgb[gem] = white * 0.45 + g_v * 0.55
            mask |= shaft | pommel | block_h | bevel | crown_tip
        elif cat == "scythe":
            # Wicked Curved Soul Reaper Scythe!
            collar = (np.abs(u - 0.52) <= 0.10) & (np.abs(v) <= 0.16)
            vp = np.clip(v, 0.0, 1.0)
            curve = (v >= -0.04) & (v <= 0.78) & (np.abs(u - (0.64 - 0.52 * (vp ** 1.35))) <= (0.17 - 0.11 * vp))
            edge = curve & (u >= (0.68 - 0.52 * (vp ** 1.35)))
            rgb[collar] = g_v
            rgb[curve] = m_v * 1.08
            rgb[edge] = g_v * 0.7 + white * 0.3
            mask |= shaft | pommel | collar | curve
        else:
            # Double-Bladed Storm Battleaxe of Thor!
            wings_a = (u >= 0.20) & (u <= 0.84) & (np.abs(v) >= 0.06) & (np.abs(v) <= (0.24 + 0.46 * np.sin(np.clip((u - 0.18) / 0.68, 0, 1) * math.pi)))
            rim_a = wings_a & (np.abs(v) >= 0.46)
            core_a = ((u - 0.52)**2 + v*v) <= 0.04
            rgb[wings_a] = m_v * 1.05
            rgb[rim_a] = g_v * 0.75 + white * 0.25
            rgb[core_a] = g_v
            mask |= shaft | pommel | wings_a

    elif cat in ("mace", "trident"):
        u = (nx + ny) * 0.7071
        v = (ny - nx) * 0.7071
        shaft = (u >= -1.02) & (u <= 0.48) & (np.abs(v) <= 0.085)
        pommel = ((u + 1.00)**2 + v*v) <= 0.03
        rgb[shaft] = t_v * 0.8
        rgb[pommel] = g_v
        if cat == "mace":
            head = (u >= 0.28) & (u <= 0.88) & (np.abs(v) <= 0.34)
            spikes = ((np.abs(u - 0.58) <= 0.09) & (np.abs(v) <= 0.50)) | ((u >= 0.84) & (u <= 1.02) & (np.abs(v) <= 0.10))
            core = ((u - 0.58)**2 + v*v) <= 0.045
            rgb[head] = (m_v * np.clip(0.85 + 0.35 * v * 2.5, 0.6, 1.3)[..., None])[head]
            rgb[spikes] = t_v
            rgb[core] = g_v * (0.8 + 0.2 * math.sin(ph * 2)) + white * 0.2
            mask |= shaft | pommel | head | spikes
        else:
            crossbar = (np.abs(u - 0.36) <= 0.08) & (np.abs(v) <= 0.36)
            prongs = (u >= 0.36) & (u <= 0.98) & ((np.abs(v) <= 0.075) | (np.abs(np.abs(v) - 0.28) <= 0.07))
            tips = prongs & (u >= 0.78)
            rgb[crossbar] = t_v
            rgb[prongs] = m_v * 1.1
            rgb[tips] = g_v * 0.75 + white * 0.25
            mask |= shaft | pommel | crossbar | prongs

    elif cat == "eye":
        # Hypnotic Arcane Eye (Dezorient)
        eye_shape = (np.abs(ny_b) <= 0.56 * (1.0 - (nx / 0.88)**2)) & (np.abs(nx) <= 0.88)
        iris = r_b <= 0.40
        pupil = (np.abs(nx) <= 0.10) & (np.abs(ny_b) <= 0.30)
        ring = (np.abs(r_b - (0.76 + 0.05 * math.sin(ph))) <= 0.08) & (np.cos(ang * 3 - ph * 2) > -0.2)
        rgb[eye_shape] = g_v
        rgb[iris] = m_v
        rgb[pupil] = t_v
        rgb[ring] = t_v
        mask |= eye_shape | ring

    elif cat == "halo":
        # Radiant Bozhya Aura Crown-Halo + Seraph Wings!
        ring = (r_b >= 0.44) & (r_b <= 0.72)
        cross = ((np.abs(nx) <= 0.09) & (r_b <= 0.88)) | ((np.abs(ny_b) <= 0.09) & (r_b <= 0.88))
        rays = (r_b >= 0.68) & (r_b <= 0.94) & (np.cos(ang * 8.0 - ph) > 0.45)
        rgb[ring] = m_v
        rgb[cross | rays] = g_v
        mask |= ring | cross | rays

    elif cat == "tornado":
        # Swirling 4-tier Fire Tornado!
        swirl_x = 0.14 * np.sin(ny_b * 5.5 - ph * 2.0)
        width = 0.18 + 0.46 * ((ny_b + 0.82) / 1.64)
        body = (ny_b >= -0.82) & (ny_b <= 0.82) & (np.abs(nx - swirl_x) <= width)
        bands = body & (np.sin(ny_b * 9.0 - ph * 2.0 + nx * 3.0) > 0.15)
        rgb[body] = m_v
        rgb[bands] = g_v
        mask |= body

    elif cat == "flask":
        # Sparkling Starlight Dust Pouch/Flask (Yavnaya Pyl)
        bulb = (nx*nx + (ny_b + 0.15)**2) <= 0.34
        neck = (np.abs(nx) <= 0.18) & (ny_b >= 0.25) & (ny_b <= 0.62)
        rim = (np.abs(nx) <= 0.26) & (ny_b >= 0.56) & (ny_b <= 0.70)
        rgb[bulb] = m_v
        rgb[bulb & ((nx*nx + (ny_b + 0.18)**2) <= 0.16)] = g_v
        rgb[neck] = [215, 240, 255]
        rgb[rim] = t_v
        mask |= bulb | neck | rim
        for k in range(4):
            sa = ph + k * (math.pi / 2.0)
            sx, sy = 0.76 * math.cos(sa), 0.76 * math.sin(sa)
            sp = (np.abs(nx - sx) + np.abs(ny_b - sy)) <= 0.14
            rgb[sp] = g_v
            mask |= sp

    elif cat == "snowflake":
        # Crisp 6-pointed Crystalline Snowflake / Ice-Bomb!
        rot_a = ang + 0.08 * math.sin(ph)
        arms = np.zeros((S, S), dtype=bool)
        for k in range(3):
            a = k * (math.pi / 3.0)
            rx_s = nx * math.cos(a) + ny_b * math.sin(a)
            ry_s = -nx * math.sin(a) + ny_b * math.cos(a)
            arm = (np.abs(ry_s) <= 0.10) & (np.abs(rx_s) <= 0.88)
            barb = (np.abs(rx_s) >= 0.36) & (np.abs(rx_s) <= 0.68) & (np.abs(np.abs(ry_s) - (np.abs(rx_s) - 0.34)*0.85) <= 0.08)
            arms |= arm | barb
        core = r_b <= (0.44 if sub == "icebomb" else 0.32)
        rgb[arms] = g_v
        rgb[core] = m_v
        rgb[r_b <= 0.18] = t_v if sub == "coin" else white
        mask |= arms | core

    elif cat == "web":
        # Crisp Octagonal Spiderweb Grenade!
        spokes = np.zeros((S, S), dtype=bool)
        for k in range(4):
            a = k * (math.pi / 4.0)
            ry_s = -nx * math.sin(a) + ny_b * math.cos(a)
            spokes |= (np.abs(ry_s) <= 0.075) & (r_b <= 0.88)
        rings = ((np.abs(r_b - 0.38) <= 0.075) | (np.abs(r_b - 0.72) <= 0.075)) & (r_b <= 0.88)
        core = r_b <= (0.22 + 0.03 * math.sin(ph * 2))
        rgb[spokes | rings] = m_v
        rgb[core] = g_v
        mask |= spokes | rings | core

    elif cat == "heal":
        # A bright heart and a contrasting medical cross read clearly in a GUI slot.
        lobes = (((nx - 0.29)**2 + (ny_b - 0.22)**2) <= 0.12) | (((nx + 0.29)**2 + (ny_b - 0.22)**2) <= 0.12)
        lower = (ny_b >= -0.74) & (ny_b <= 0.25) & (np.abs(nx) <= 0.64 * np.clip((ny_b + 0.74) / 0.99, 0.0, 1.0))
        heart = lobes | lower
        shine = heart & (nx < -0.20) & (ny_b > 0.18)
        cross = heart & (((np.abs(nx) <= 0.085) & (np.abs(ny_b + 0.02) <= 0.26)) |
                         ((np.abs(ny_b + 0.02) <= 0.085) & (np.abs(nx) <= 0.26)))
        rgb[heart] = m_v
        rgb[shine] = np.clip(m_v * 0.65 + white * 0.35, 0, 255)
        rgb[cross] = g_v
        mask |= heart

    elif cat == "shield":
        # Gold-edged blue shield with a high-contrast central emblem.
        width = 0.67 * np.clip((ny_b + 0.78) / 0.78, 0.0, 1.0)
        outer = (ny_b >= -0.78) & (ny_b <= 0.68) & (np.abs(nx) <= width)
        inner_width = 0.49 * np.clip((ny_b + 0.60) / 0.60, 0.0, 1.0)
        inner = outer & (ny_b <= 0.51) & (ny_b >= -0.59) & (np.abs(nx) <= inner_width)
        emblem = inner & (((np.abs(nx) <= 0.07) & (np.abs(ny_b) <= 0.31)) |
                           ((np.abs(ny_b) <= 0.07) & (np.abs(nx) <= 0.27)))
        rgb[outer] = t_v
        rgb[inner] = m_v
        rgb[emblem] = g_v
        mask |= outer

    elif cat == "mandarin":
        orb = (nx*nx / 0.56 + (ny_b + 0.06)**2 / 0.44) <= 1.0
        leaf = ((nx - 0.22)**2 / 0.10 + (ny_b - 0.62)**2 / 0.03) <= 1.0
        stem = (np.abs(nx) <= 0.08) & (ny_b >= 0.52) & (ny_b <= 0.72)
        diff = np.clip(-nx * 0.45 + ny_b * 0.55 + 0.65, 0.4, 1.25)
        rgb[orb] = m_v * diff[orb, None]
        rgb[orb & (((nx + 0.24)**2 + (ny_b - 0.18)**2) <= 0.05)] = g_v
        rgb[leaf | stem] = t_v
        mask |= orb | leaf | stem

    else:  # scroll
        page = (np.abs(nx) <= 0.58) & (np.abs(ny_b) <= 0.68)
        rolls = (np.abs(nx) <= 0.70) & ((np.abs(ny_b - 0.68) <= 0.12) | (np.abs(ny_b + 0.68) <= 0.12))
        seal = (nx*nx + (ny_b + 0.05)**2) <= 0.09
        rgb[page] = m_v
        rgb[rolls] = t_v
        rgb[seal] = g_v
        mask |= page | rolls | seal

    # Diagonal traveling metallic sheen across ALL items so animation is smooth & crisp!
    sheen_pos = -1.6 + 3.2 * (frame_idx / float(n_frames))
    sheen = mask & (np.abs((nx * 0.75 + ny_b * 0.65) - sheen_pos) <= 0.14)
    rgb[sheen] = np.clip(rgb[sheen] * 1.22 + 42.0, 0, 255)

    # Crisp 1px dark pixel-art outline around solid silhouette (100% binary alpha -> ZERO flat quad artifacts!)
    dil = np.zeros_like(mask)
    for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
        shifted = np.zeros_like(mask)
        x_src = slice(max(0, -dx), S - max(0, dx))
        x_dst = slice(max(0, dx), S - max(0, -dx))
        y_src = slice(max(0, -dy), S - max(0, dy))
        y_dst = slice(max(0, dy), S - max(0, -dy))
        shifted[y_dst, x_dst] = mask[y_src, x_src]
        dil |= shifted
    outline = dil & (~mask)
    dark_out = np.clip(m_v * 0.52 + g_v * 0.16 + 20.0, 28, 135)
    rgb[outline] = dark_out

    final_mask = mask | outline
    rgba = np.zeros((S, S, 4), dtype=np.uint8)
    rgba[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    rgba[..., 3] = np.where(final_mask, 255, 0).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")

def make_clean_item_model_json(item_id, cat):
    tex_ref = f"kinggrief:item/{item_id}"
    if cat == "bow":
        # Drawing states are selected by the base bow's legacy overrides or by
        # the modern item definition. Keeping the custom parent free of
        # overrides also prevents inherited pull models selecting themselves.
        return {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": tex_ref},
            "display": copy.deepcopy(VANILLA_FALLBACKS["legacy"]["bow"]["display"]),
        }
    if cat == "sphere":
        # 6x6x6 voxel cube; full opaque square texture on each face. GUI faces
        # the viewer directly, while the hands show the item's actual depth.
        faces = {name: {"uv": [0, 0, 16, 16], "texture": "#layer0"}
                 for name in ("down", "up", "north", "south", "west", "east")}
        return {
            "parent": "minecraft:block/block", "ambientocclusion": False,
            "textures": {"layer0": tex_ref, "particle": tex_ref},
            "elements": [{"from": [5, 5, 5], "to": [11, 11, 11], "faces": faces}],
            "display": {
                "firstperson_righthand": {"rotation": [12, -28, 0], "translation": [1.25, 2.8, 1.15], "scale": [0.60, 0.60, 0.60]},
                "firstperson_lefthand": {"rotation": [12, 28, 0], "translation": [1.25, 2.6, 1.15], "scale": [0.58, 0.58, 0.58]},
                "thirdperson_righthand": {"rotation": [15, -25, 0], "translation": [0, 3.0, 1.0], "scale": [0.75, 0.75, 0.75]},
                "thirdperson_lefthand": {"rotation": [15, 25, 0], "translation": [0, 3.0, 1.0], "scale": [0.75, 0.75, 0.75]},
                "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.2, 1.2, 1.2]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.75, 0.75, 0.75]},
                "fixed": {"rotation": [0, 180, 0], "scale": [1.0, 1.0, 1.0]},
            },
        }
    if cat in ("sword", "pickaxe", "mace", "trident", "hammer", "scythe", "axe"):
        return {
            "parent": "minecraft:item/handheld",
            "textures": {"layer0": tex_ref},
            "display": {
                "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                "firstperson_lefthand":  {"rotation": [0,  90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.62, 0.62, 0.62]},
                "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
                "thirdperson_lefthand":  {"rotation": [0,  90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
            }
        }
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": tex_ref},
        "display": {
            "firstperson_righthand": {"rotation": [0, -24, 8], "translation": [1.25, 2.8, 1.15], "scale": [0.50, 0.50, 0.50]},
            "firstperson_lefthand":  {"rotation": [0, -24, 8], "translation": [1.25, 2.6, 1.15], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3.0, 1.0], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand":  {"rotation": [0, 0, 0], "translation": [0, 3.0, 1.0], "scale": [0.55, 0.55, 0.55]},
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2.5, 0], "scale": [0.55, 0.55, 0.55]},
            "fixed": {"rotation": [0, 180, 0], "scale": [1.0, 1.0, 1.0]}
        }
    }

tex_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/textures/item")
mod_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/models/item")
def_item_dir = os.path.join(OUT_RP_DIR, "assets/kinggrief/items")
mc_mod_item_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/models/item")
os.makedirs(tex_item_dir, exist_ok=True)
os.makedirs(mod_item_dir, exist_ok=True)
os.makedirs(def_item_dir, exist_ok=True)
os.makedirs(mc_mod_item_dir, exist_ok=True)

N_FRAMES = 12
n_rows = (len(ITEMS_SPECS) + 7) // 8
items_preview = Image.new("RGBA", (8 * 76 + 16, n_rows * 76 + 16), (20, 24, 36, 255))

for idx, (primary_id, aliases, cat, sub, mcol, gcol, tcol) in enumerate(ITEMS_SPECS):
    strip = Image.new("RGBA", (32, 32 * N_FRAMES), (0, 0, 0, 0))
    for f in range(N_FRAMES):
        fim = render_item_frame_32(cat, sub, mcol, gcol, tcol, f, N_FRAMES, -1)
        strip.paste(fim, (0, f * 32))
        if f == 0:
            col = idx % 8
            row = idx // 8
            up = fim.resize((64, 64), Image.NEAREST)
            items_preview.paste(up, (14 + col * 76, 14 + row * 76), up)

    pull_strips = {}
    if cat == "bow":
        for ps in (0, 1, 2):
            pstrip = Image.new("RGBA", (32, 32 * N_FRAMES), (0, 0, 0, 0))
            for f in range(N_FRAMES):
                pfim = render_item_frame_32(cat, sub, mcol, gcol, tcol, f, N_FRAMES, ps)
                pstrip.paste(pfim, (0, f * 32))
            pull_strips[ps] = pstrip

    all_ids = [primary_id] + aliases
    for iid in all_ids:
        png_p = os.path.join(tex_item_dir, f"{iid}.png")
        strip.save(png_p)
        with open(png_p + ".mcmeta", "w", encoding="utf-8") as fp:
            json.dump({"animation": {"frametime": 3, "interpolate": False}}, fp, indent=2)
        with open(os.path.join(mod_item_dir, f"{iid}.json"), "w", encoding="utf-8") as fp:
            json.dump(make_clean_item_model_json(iid, cat), fp, indent=2)

        if cat == "bow":
            for ps in (0, 1, 2):
                ppng = os.path.join(tex_item_dir, f"{iid}_pulling_{ps}.png")
                pull_strips[ps].save(ppng)
                with open(ppng + ".mcmeta", "w", encoding="utf-8") as fp:
                    json.dump({"animation": {"frametime": 3, "interpolate": False}}, fp, indent=2)
                with open(os.path.join(mod_item_dir, f"{iid}_pulling_{ps}.json"), "w", encoding="utf-8") as fp:
                    json.dump({
                        "parent": f"kinggrief:item/{iid}",
                        "textures": {"layer0": f"kinggrief:item/{iid}_pulling_{ps}"}
                    }, fp, indent=2)
            # 1.21.4 - 1.21.11 Official Bow Pulling Item Definition!
            with open(os.path.join(def_item_dir, f"{iid}.json"), "w", encoding="utf-8") as fp:
                json.dump({
                    "model": {
                        "type": "minecraft:condition",
                        "property": "minecraft:using_item",
                        "on_false": {
                            "type": "minecraft:model",
                            "model": f"kinggrief:item/{iid}"
                        },
                        "on_true": {
                            "type": "minecraft:range_dispatch",
                            "property": "minecraft:use_duration",
                            "scale": 0.05,
                            "entries": [
                                {"threshold": 0.65, "model": {"type": "minecraft:model", "model": f"kinggrief:item/{iid}_pulling_1"}},
                                {"threshold": 0.9,  "model": {"type": "minecraft:model", "model": f"kinggrief:item/{iid}_pulling_2"}}
                            ],
                            "fallback": {
                                "type": "minecraft:model",
                                "model": f"kinggrief:item/{iid}_pulling_0"
                            }
                        }
                    }
                }, fp, indent=2)
        else:
            with open(os.path.join(def_item_dir, f"{iid}.json"), "w", encoding="utf-8") as fp:
                json.dump({"model": {"type": "minecraft:model", "model": f"kinggrief:item/{iid}"}}, fp, indent=2)

# Models in the old pack had no dispatch files for most base materials. As a
# result, a perfectly valid item on 1.16–1.21.3 appeared as a vanilla totem,
# pickaxe, etc. 1.21.4+ needs *item definitions*, not just those legacy models.
def json_write(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fp:
        json.dump(value, fp, ensure_ascii=False, indent=2)
        fp.write("\n")


def legacy_base_model(material):
    if material in ("bow", "elytra"):
        return copy.deepcopy(VANILLA_FALLBACKS["legacy"][material])
    if material == "tnt":
        return {"parent": "minecraft:block/tnt"}
    if material == "totem_of_undying":
        # Keep the smaller PvP totem position from the previous pack.
        return {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "minecraft:item/totem_of_undying"},
            "display": {
                "firstperson_righthand": {"rotation": [0, -24, 8], "translation": [1.25, 2.6, 1.15], "scale": [0.46, 0.46, 0.46]},
                "firstperson_lefthand": {"rotation": [0, -24, 8], "translation": [1.25, 2.4, 1.15], "scale": [0.44, 0.44, 0.44]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3.0, 1.0], "scale": [0.52, 0.52, 0.52]},
                "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3.0, 1.0], "scale": [0.52, 0.52, 0.52]},
            },
        }
    parent = "minecraft:item/generated"
    if material.endswith(("_sword", "_pickaxe", "_axe")):
        parent = "minecraft:item/handheld"
    elif material == "mace":
        parent = "minecraft:item/handheld_mace"
    return {"parent": parent, "textures": {"layer0": f"minecraft:item/{material}"}}


def legacy_overrides(material, entries):
    result = []
    for cmd, item_id in entries:
        result.append({"predicate": {"custom_model_data": cmd}, "model": f"kinggrief:item/{item_id}"})
        if material == "bow":
            for stage, pull in ((0, None), (1, 0.65), (2, 0.9)):
                predicate = {"custom_model_data": cmd, "pulling": 1}
                if pull is not None:
                    predicate["pull"] = pull
                result.append({"predicate": predicate, "model": f"kinggrief:item/{item_id}_pulling_{stage}"})
    return result


def modern_vanilla_fallback(material):
    special = VANILLA_FALLBACKS["modern"].get(material)
    return copy.deepcopy(special) if special else {"type": "minecraft:model", "model": f"minecraft:item/{material}"}


mc_item_def_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/items")
for material_name, mapped_items in sorted(MODEL_DATA.items()):
    material = material_name.lower()
    entries = sorted((int(cmd), item_id) for cmd, item_id in mapped_items)
    if len({cmd for cmd, _ in entries}) != len(entries):
        raise ValueError(f"Duplicate CustomModelData for {material_name}")
    for _, item_id in entries:
        if not os.path.isfile(os.path.join(def_item_dir, f"{item_id}.json")):
            raise ValueError(f"{material_name}: missing item definition for {item_id}")

    legacy = legacy_base_model(material)
    legacy.setdefault("overrides", []).extend(legacy_overrides(material, entries))
    json_write(os.path.join(mc_mod_item_dir, f"{material}.json"), legacy)

    # Armor trim item models were added in 1.20. The trim-aware override is
    # applied only to 1.20.2–1.21.3 (which support overlays); 1.16 clients do
    # not have to resolve nonexistent trim models. 1.21.4+ uses the definition
    # below and retains its native trim selector in the fallback.
    if material in ("netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots"):
        trimmed = copy.deepcopy(VANILLA_FALLBACKS["legacy"][material])
        trimmed.setdefault("overrides", []).extend(legacy_overrides(material, entries))
        json_write(os.path.join(OUT_RP_DIR, "legacy_trim", "assets/minecraft/models/item", f"{material}.json"), trimmed)

    fallback = modern_vanilla_fallback(material)
    occupied = {cmd for cmd, _ in entries}
    modern_entries = []
    for cmd, item_id in entries:
        with open(os.path.join(def_item_dir, f"{item_id}.json"), encoding="utf-8") as fp:
            custom_model = json.load(fp)["model"]
        modern_entries.append({"threshold": cmd, "model": custom_model})
        # range_dispatch selects the highest lower bound. Reset after each
        # group of consecutive integer IDs so unrelated IDs don't inherit the
        # last KINGGRIEF item model.
        if cmd + 1 not in occupied:
            modern_entries.append({"threshold": cmd + 1, "model": copy.deepcopy(fallback)})
    json_write(os.path.join(mc_item_def_dir, f"{material}.json"), {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "fallback": fallback,
            "entries": modern_entries,
        },
    })

print(f"3. Mapped {sum(map(len, MODEL_DATA.values()))} material/CMD pairs for old and modern clients.")

items_preview.save("/tmp/preview_all_items_v57.png")
print("2. Rendered ALL", len(ITEMS_SPECS), "crisp 3D items + 4-stage Bow Pulling Animations + Custom Arrows + 6 Armor Sets!")

# ==============================================================================
# 3. SUBTLE CLEAN PVP ENCHANTMENT GLINT (NO MORE PURPLE FOG HIDING ITEM DETAILS!)
#    + HIGHLIGHTED ORES + LOW PVP FIRE + PACK.MCMETA
# ==============================================================================
misc_dir = os.path.join(OUT_RP_DIR, "assets/minecraft/textures/misc")
os.makedirs(misc_dir, exist_ok=True)
glint = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
gpx = glint.load()
for y in range(64):
    for x in range(64):
        d1 = (x + y) % 32
        d2 = (x * 2 - y) % 48
        v = 0
        if d1 in (0, 1):
            v = 42 if d1 == 0 else 24
        elif d2 == 0:
            v = 28
        gpx[x, y] = (min(255, int(v * 0.85)), min(255, int(v * 0.90)), min(255, int(v * 1.15)), 255)
glint.save(os.path.join(misc_dir, "enchanted_glint_item.png"))
glint.save(os.path.join(misc_dir, "enchanted_glint_entity.png"))

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
            # 1.16.5 reads pack_format; 1.20.2–1.21.8 read
            # supported_formats; 1.21.9+ read min_format/max_format.
            "pack_format": 6,
            "supported_formats": [6, 75],
            "min_format": 6,
            "max_format": 75,
            "description": "§b§l❄ §c§lKINGGRIEF v7.0 §8· §fЗимние руны, мега-гепль и шары",
        },
        "overlays": {
            "entries": [{"directory": "legacy_trim", "formats": [18, 42], "min_format": 18, "max_format": 42}]
        },
    }, fp, ensure_ascii=False, indent=2)

pack_icon = Image.open(os.path.join(tex_item_dir, "shar_santy.png")).crop((0, 0, 32, 32)).resize((64, 64), Image.NEAREST)
pack_icon.save(os.path.join(OUT_RP_DIR, "pack.png"))

# Fixed timestamps and sorted paths make the ZIP (and therefore the server's
# SHA-1) reproducible when built with the same Pillow and NumPy versions.
output_path = Path(OUT_ZIP)
output_path.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
    files = sorted(p for p in Path(OUT_RP_DIR).rglob("*") if p.is_file())
    for full in files:
        rel = full.relative_to(OUT_RP_DIR).as_posix()
        info = zipfile.ZipInfo(rel, date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        zf.writestr(info, full.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)

sha1 = hashlib.sha1(output_path.read_bytes()).hexdigest()
print(f"PACKAGED {output_path.name} v7.0: {output_path.stat().st_size:,} bytes | SHA1={sha1}")
