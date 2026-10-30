#!/usr/bin/env python3
"""
Deterministic generator for AXSESCLIENT GENESIS X original NEO-DARK branding assets,
8 built-in theme presets, and JSON schemas. Uses only Python 3 standard library.
"""
import json
import math
import os
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "assets"
BRANDING_DIR = ASSETS_DIR / "branding"
ICONS_DIR = ASSETS_DIR / "icons"
THEMES_DIR = ROOT / "shared" / "themes"
SCHEMAS_DIR = ROOT / "shared" / "schemas"

for d in (BRANDING_DIR, ICONS_DIR, THEMES_DIR, SCHEMAS_DIR):
    d.mkdir(parents=True, exist_ok=True)


def write_png(path: Path, width: int, height: int, rgba_bytes: bytes) -> bytes:
    """Encode raw RGBA bytes into a valid PNG image."""
    raw_rows = bytearray()
    stride = width * 4
    for y in range(height):
        raw_rows.append(0)  # filter type 0 (None)
        raw_rows.extend(rgba_bytes[y * stride : (y + 1) * stride])

    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    png = bytearray(b"\x89PNG\r\n\x1a\n")
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png.extend(chunk(b"IHDR", ihdr))
    png.extend(chunk(b"IDAT", zlib.compress(bytes(raw_rows), 9)))
    png.extend(chunk(b"IEND", b""))
    out = bytes(png)
    path.write_bytes(out)
    return out


def write_ico(path: Path, png_entries: list[tuple[int, int, bytes]]) -> None:
    """Create a multi-resolution Windows ICO container from PNG buffers."""
    count = len(png_entries)
    header = struct.pack("<HHH", 0, 1, count)
    offset = 6 + 16 * count
    directory = bytearray()
    payload = bytearray()
    for w, h, data in png_entries:
        bw = 0 if w >= 256 else w
        bh = 0 if h >= 256 else h
        directory.extend(struct.pack("<BBBBHHII", bw, bh, 0, 0, 1, 32, len(data), offset))
        payload.extend(data)
        offset += len(data)
    path.write_bytes(header + bytes(directory) + bytes(payload))


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * max(0.0, min(1.0, t))


def dist_to_segment(px: float, py: float, x1: float, y1: float, x2: float, y2: float) -> float:
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 <= 1e-9:
        return math.hypot(px - x1, py - y1)
    t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / l2))
    proj_x = x1 + t * dx
    proj_y = y1 + t * dy
    return math.hypot(px - proj_x, py - proj_y)


def render_axses_emblem(size: int) -> bytes:
    """Render crisp anti-aliased AXSES NEO-DARK hexagonal A+X prism icon."""
    buf = bytearray(size * size * 4)
    half = size * 0.5
    rad = size * 0.44
    corner = size * 0.22

    # Normalized coordinates [-1, 1]
    for y in range(size):
        ny = (y + 0.5 - half) / half
        for x in range(size):
            nx = (x + 0.5 - half) / half

            # Rounded squircle SDF
            qx = abs(nx) - (0.90 - 0.24)
            qy = abs(ny) - (0.90 - 0.24)
            sdf_bg = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - 0.24
            aa_px = 2.0 / size
            bg_alpha = max(0.0, min(1.0, (0.0 - sdf_bg) / aa_px + 0.5))
            if bg_alpha <= 0.0:
                continue

            # Deep Neo-Dark base gradient (#070911 -> #10131D -> #191D2B)
            grad_t = (nx * 0.35 + ny * 0.65 + 1.0) * 0.5
            r = lerp(7, 22, grad_t)
            g = lerp(9, 26, grad_t)
            b = lerp(17, 42, grad_t)

            # Ambient radial glow (#9060FF + #00D9FF)
            d_center = math.hypot(nx, ny + 0.05)
            glow = max(0.0, 1.0 - d_center * 1.15) ** 2.2
            r += glow * 52
            g += glow * 34
            b += glow * 95

            # Outer border highlight
            border_band = max(0.0, 1.0 - abs(sdf_bg + 0.028) / (max(aa_px * 1.4, 0.022)))
            r = lerp(r, 144, border_band * 0.65)
            g = lerp(g, 96, border_band * 0.65)
            b = lerp(b, 255, border_band * 0.65)

            # Geometric "AX" Genesis X Prism Strokes:
            # Left & right ascending A-chevron + intersecting X-cyan beam
            d_a1 = dist_to_segment(nx, ny, -0.44, 0.46, 0.0, -0.50)
            d_a2 = dist_to_segment(nx, ny, 0.44, 0.46, 0.0, -0.50)
            d_x1 = dist_to_segment(nx, ny, -0.46, -0.22, 0.46, 0.36)
            d_x2 = dist_to_segment(nx, ny, -0.34, 0.14, 0.38, -0.30)

            stroke_a = 0.088
            stroke_x = 0.062

            a_cov = max(
                max(0.0, min(1.0, (stroke_a - d_a1) / aa_px + 0.5)),
                max(0.0, min(1.0, (stroke_a - d_a2) / aa_px + 0.5)),
            )
            x_cov = max(
                max(0.0, min(1.0, (stroke_x - d_x1) / aa_px + 0.5)),
                max(0.0, min(1.0, (stroke_x - d_x2) / aa_px + 0.5)),
            )

            if a_cov > 0.0:
                # Primary (#9060FF) -> Secondary (#536FFF) -> Text highlight
                at = max(0.0, min(1.0, (ny + 0.5) / 1.0))
                ar = lerp(175, 83, at)
                ag = lerp(120, 111, at)
                ab = 255.0
                r = lerp(r, ar, a_cov)
                g = lerp(g, ag, a_cov)
                b = lerp(b, ab, a_cov)

            if x_cov > 0.0:
                # Accent (#00D9FF) -> Crisp White core
                xt = max(0.0, min(1.0, (nx + 0.5) / 1.0))
                xr = lerp(144, 0, xt)
                xg = lerp(96, 217, xt)
                xb = 255.0
                r = lerp(r, xr, x_cov * 0.95)
                g = lerp(g, xg, x_cov * 0.95)
                b = lerp(b, xb, x_cov * 0.95)

            # Apex diamond star
            d_apex = abs(nx) + abs(ny + 0.50)
            apex_cov = max(0.0, min(1.0, (0.085 - d_apex) / aa_px + 0.5))
            if apex_cov > 0.0:
                r = lerp(r, 247, apex_cov)
                g = lerp(g, 248, apex_cov)
                b = lerp(b, 255, apex_cov)

            idx = (y * size + x) * 4
            buf[idx] = max(0, min(255, int(round(r))))
            buf[idx + 1] = max(0, min(255, int(round(g))))
            buf[idx + 2] = max(0, min(255, int(round(b))))
            buf[idx + 3] = max(0, min(255, int(round(bg_alpha * 255))))
    return bytes(buf)


def render_splash(width: int = 960, height: int = 540) -> bytes:
    """Render AXSESCLIENT GENESIS X Neo-Dark splash backdrop with subtle grid & prism aura."""
    buf = bytearray(width * height * 4)
    emblem_size = 160
    emblem = render_axses_emblem(emblem_size)
    ex0 = (width - emblem_size) // 2
    ey0 = (height - emblem_size) // 2 - 24

    for y in range(height):
        ny = y / height
        for x in range(width):
            nx = x / width
            # Base #070911 -> #10131D
            r = lerp(7, 16, ny * 0.85)
            g = lerp(9, 19, ny * 0.85)
            b = lerp(17, 29, ny * 0.85)

            # Dual radial aurora (#9060FF top-left-center, #00D9FF bottom-right-center)
            d1 = math.hypot((nx - 0.42) * 1.6, ny - 0.42)
            d2 = math.hypot((nx - 0.60) * 1.6, ny - 0.56)
            a1 = max(0.0, 1.0 - d1 * 1.6) ** 2.4
            a2 = max(0.0, 1.0 - d2 * 1.8) ** 2.4
            r += a1 * 48 + a2 * 4
            g += a1 * 26 + a2 * 56
            b += a1 * 92 + a2 * 76

            # Subtle engineering 32px grid
            if (x % 32 == 0 or y % 32 == 0):
                r += 5
                g += 6
                b += 11

            # Bottom accent progress track bar preview
            if height - 6 <= y < height:
                t_bar = nx
                r = lerp(144, 0, t_bar)
                g = lerp(96, 217, t_bar)
                b = 255

            idx = (y * width + x) * 4
            buf[idx] = max(0, min(255, int(r)))
            buf[idx + 1] = max(0, min(255, int(g)))
            buf[idx + 2] = max(0, min(255, int(b)))
            buf[idx + 3] = 255

    # Composite emblem centered
    for ey in range(emblem_size):
        ty = ey0 + ey
        if not (0 <= ty < height):
            continue
        for ex in range(emblem_size):
            tx = ex0 + ex
            if not (0 <= tx < width):
                continue
            si = (ey * emblem_size + ex) * 4
            sa = emblem[si + 3] / 255.0
            if sa <= 0.0:
                continue
            di = (ty * width + tx) * 4
            buf[di] = int(buf[di] * (1.0 - sa) + emblem[si] * sa)
            buf[di + 1] = int(buf[di + 1] * (1.0 - sa) + emblem[si + 1] * sa)
            buf[di + 2] = int(buf[di + 2] * (1.0 - sa) + emblem[si + 2] * sa)
            buf[di + 3] = 255
    return bytes(buf)


# 1. Generate Multi-Resolution Icon PNGs + Windows .ico + Splash
ico_entries = []
for sz in (16, 32, 48, 64, 128, 256):
    rgba = render_axses_emblem(sz)
    png_data = write_png(BRANDING_DIR / f"icon-{sz}.png", sz, sz, rgba)
    ico_entries.append((sz, sz, png_data))

write_ico(BRANDING_DIR / "axsesclient.ico", ico_entries)
write_png(BRANDING_DIR / "splash.png", 960, 540, render_splash(960, 540))

# 2. Vector SVG Logo & App Icon
logo_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 160" width="640" height="160">
  <defs>
    <linearGradient id="axPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#9060FF"/>
      <stop offset="55%" stop-color="#536FFF"/>
      <stop offset="100%" stop-color="#00D9FF"/>
    </linearGradient>
    <linearGradient id="axCyan" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#9060FF"/>
      <stop offset="100%" stop-color="#00D9FF"/>
    </linearGradient>
  </defs>
  <rect x="8" y="16" width="128" height="128" rx="28" fill="#10131D" stroke="url(#axPrimary)" stroke-width="3"/>
  <path d="M40 118 L72 38 L104 118" fill="none" stroke="url(#axPrimary)" stroke-width="11" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M38 58 L106 106 M46 98 L102 52" fill="none" stroke="url(#axCyan)" stroke-width="8" stroke-linecap="round"/>
  <polygon points="72,28 79,36 72,44 65,36" fill="#F7F8FF"/>
  <text x="160" y="88" fill="#F7F8FF" font-family="Segoe UI, Inter, sans-serif" font-size="44" font-weight="800" letter-spacing="3">AXSES<tspan fill="#9060FF">CLIENT</tspan></text>
  <text x="163" y="118" fill="#00D9FF" font-family="Segoe UI, Inter, sans-serif" font-size="15" font-weight="600" letter-spacing="6">GENESIS X · NEO-DARK PLATFORM</text>
</svg>
"""
(BRANDING_DIR / "logo.svg").write_text(logo_svg, encoding="utf-8")

icon_svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" width="256" height="256">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#070911"/>
      <stop offset="100%" stop-color="#191D2B"/>
    </linearGradient>
    <linearGradient id="pri" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#9060FF"/>
      <stop offset="100%" stop-color="#536FFF"/>
    </linearGradient>
    <linearGradient id="acc" x1="0%" y1="100%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#9060FF"/>
      <stop offset="100%" stop-color="#00D9FF"/>
    </linearGradient>
  </defs>
  <rect x="12" y="12" width="232" height="232" rx="52" fill="url(#bg)" stroke="#9060FF" stroke-width="6"/>
  <path d="M72 192 L128 60 L184 192" fill="none" stroke="url(#pri)" stroke-width="22" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M68 96 L188 176 M84 156 L180 86" fill="none" stroke="url(#acc)" stroke-width="15" stroke-linecap="round"/>
  <polygon points="128,44 140,58 128,72 116,58" fill="#F7F8FF"/>
</svg>
"""
(BRANDING_DIR / "icon.svg").write_text(icon_svg, encoding="utf-8")

# 3. Category SVG Icons (8 ClickGUI & Launcher categories)
category_svgs = {
    "combat_training": '<path d="M12 3a9 9 0 1 0 9 9 9 9 0 0 0-9-9zm0 4a5 5 0 1 1-5 5 5 5 0 0 1 5-5zm0 3a2 2 0 1 0 2 2 2 2 0 0 0-2-2z" fill="none" stroke="#9060FF" stroke-width="2"/><path d="M12 1v3M12 20v3M1 12h3M20 12h3" stroke="#00D9FF" stroke-width="2" stroke-linecap="round"/>',
    "movement": '<path d="M13 3L4 14h7l-1 7 9-11h-7l1-7z" fill="none" stroke="#00D9FF" stroke-width="2" stroke-linejoin="round"/>',
    "render": '<path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7-10-7-10-7z" fill="none" stroke="#9060FF" stroke-width="2"/><circle cx="12" cy="12" r="3" fill="none" stroke="#00D9FF" stroke-width="2"/>',
    "player": '<circle cx="12" cy="8" r="4" fill="none" stroke="#9060FF" stroke-width="2"/><path d="M5 20c0-3.87 3.13-7 7-7s7 3.13 7 7" fill="none" stroke="#00D9FF" stroke-width="2" stroke-linecap="round"/>',
    "world": '<circle cx="12" cy="12" r="9" fill="none" stroke="#9060FF" stroke-width="2"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18" fill="none" stroke="#00D9FF" stroke-width="1.8"/>',
    "utility": '<rect x="3" y="4" width="18" height="16" rx="3" fill="none" stroke="#9060FF" stroke-width="2"/><path d="M8 10l3 3-3 3M13 16h4" fill="none" stroke="#00D9FF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>',
    "hud": '<rect x="3" y="3" width="8" height="8" rx="2" fill="none" stroke="#9060FF" stroke-width="2"/><rect x="13" y="3" width="8" height="5" rx="1.5" fill="none" stroke="#00D9FF" stroke-width="2"/><rect x="13" y="10" width="8" height="11" rx="2" fill="none" stroke="#9060FF" stroke-width="2"/><rect x="3" y="13" width="8" height="8" rx="2" fill="none" stroke="#00D9FF" stroke-width="2"/>',
    "settings": '<circle cx="12" cy="12" r="3" fill="none" stroke="#00D9FF" stroke-width="2"/><path d="M12 2v3M12 19v3M4.93 4.93l2.12 2.12M16.95 16.95l2.12 2.12M2 12h3M19 12h3M4.93 19.07l2.12-2.12M16.95 7.05l2.12-2.12" stroke="#9060FF" stroke-width="2" stroke-linecap="round"/>',
}
for name, body in category_svgs.items():
    svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24">{body}</svg>\n'
    (ICONS_DIR / f"{name}.svg").write_text(svg, encoding="utf-8")

# 4. Eight Built-in Theme Presets (Section 020)
themes = [
    {
        "schemaVersion": 1,
        "id": "axses-dark",
        "name": "AXSES DARK",
        "builtIn": True,
        "backgroundColor": "#070911",
        "surfaceColor": "#10131D",
        "elevatedColor": "#191D2B",
        "primaryColor": "#9060FF",
        "secondaryColor": "#536FFF",
        "accentColor": "#00D9FF",
        "textColor": "#F7F8FF",
        "mutedColor": "#929BB1",
        "successColor": "#39DFA2",
        "warningColor": "#FFC16A",
        "errorColor": "#FF5B79",
        "opacity": 0.94,
        "animationSpeed": 1.0,
        "cornerRadius": 10,
        "shadowIntensity": 0.75,
    },
    {
        "schemaVersion": 1,
        "id": "axses-purple",
        "name": "AXSES PURPLE",
        "builtIn": True,
        "backgroundColor": "#0B0716",
        "surfaceColor": "#150E28",
        "elevatedColor": "#21163D",
        "primaryColor": "#B36BFF",
        "secondaryColor": "#7E42FF",
        "accentColor": "#E580FF",
        "textColor": "#FAF7FF",
        "mutedColor": "#A596C7",
        "successColor": "#39DFA2",
        "warningColor": "#FFC16A",
        "errorColor": "#FF5B79",
        "opacity": 0.94,
        "animationSpeed": 1.0,
        "cornerRadius": 12,
        "shadowIntensity": 0.80,
    },
    {
        "schemaVersion": 1,
        "id": "axses-blue",
        "name": "AXSES BLUE",
        "builtIn": True,
        "backgroundColor": "#060B16",
        "surfaceColor": "#0D1629",
        "elevatedColor": "#16233E",
        "primaryColor": "#3B82F6",
        "secondaryColor": "#2563EB",
        "accentColor": "#38BDF8",
        "textColor": "#F4F8FF",
        "mutedColor": "#8EA4C8",
        "successColor": "#34D399",
        "warningColor": "#FBBF24",
        "errorColor": "#FB7185",
        "opacity": 0.94,
        "animationSpeed": 1.0,
        "cornerRadius": 10,
        "shadowIntensity": 0.70,
    },
    {
        "schemaVersion": 1,
        "id": "axses-cyber",
        "name": "AXSES CYBER",
        "builtIn": True,
        "backgroundColor": "#050A0E",
        "surfaceColor": "#0B141C",
        "elevatedColor": "#12212E",
        "primaryColor": "#00E5FF",
        "secondaryColor": "#00FFA3",
        "accentColor": "#FFE600",
        "textColor": "#F0FCFF",
        "mutedColor": "#7FA6B8",
        "successColor": "#00FFA3",
        "warningColor": "#FFE600",
        "errorColor": "#FF3366",
        "opacity": 0.92,
        "animationSpeed": 1.15,
        "cornerRadius": 6,
        "shadowIntensity": 0.85,
    },
    {
        "schemaVersion": 1,
        "id": "axses-midnight",
        "name": "AXSES MIDNIGHT",
        "builtIn": True,
        "backgroundColor": "#04050A",
        "surfaceColor": "#0A0D18",
        "elevatedColor": "#12172A",
        "primaryColor": "#6366F1",
        "secondaryColor": "#4338CA",
        "accentColor": "#818CF8",
        "textColor": "#EEF2FF",
        "mutedColor": "#7E88AC",
        "successColor": "#39DFA2",
        "warningColor": "#FFC16A",
        "errorColor": "#FF5B79",
        "opacity": 0.96,
        "animationSpeed": 0.95,
        "cornerRadius": 10,
        "shadowIntensity": 0.85,
    },
    {
        "schemaVersion": 1,
        "id": "axses-minimal",
        "name": "AXSES MINIMAL",
        "builtIn": True,
        "backgroundColor": "#0C0D10",
        "surfaceColor": "#14161B",
        "elevatedColor": "#1E2028",
        "primaryColor": "#E2E8F0",
        "secondaryColor": "#94A3B8",
        "accentColor": "#38BDF8",
        "textColor": "#F8FAFC",
        "mutedColor": "#8B95A5",
        "successColor": "#4ADE80",
        "warningColor": "#FACC15",
        "errorColor": "#F87171",
        "opacity": 0.95,
        "animationSpeed": 1.0,
        "cornerRadius": 8,
        "shadowIntensity": 0.45,
    },
    {
        "schemaVersion": 1,
        "id": "axses-graphite",
        "name": "AXSES GRAPHITE",
        "builtIn": True,
        "backgroundColor": "#101216",
        "surfaceColor": "#181B21",
        "elevatedColor": "#232831",
        "primaryColor": "#7C93FF",
        "secondaryColor": "#5A6FD6",
        "accentColor": "#5EEAD4",
        "textColor": "#F3F4F6",
        "mutedColor": "#9CA3AF",
        "successColor": "#34D399",
        "warningColor": "#FBBF24",
        "errorColor": "#F87171",
        "opacity": 0.96,
        "animationSpeed": 1.0,
        "cornerRadius": 8,
        "shadowIntensity": 0.60,
    },
    {
        "schemaVersion": 1,
        "id": "axses-neon",
        "name": "AXSES NEON",
        "builtIn": True,
        "backgroundColor": "#080612",
        "surfaceColor": "#120D24",
        "elevatedColor": "#1C1436",
        "primaryColor": "#FF3CAC",
        "secondaryColor": "#784BA0",
        "accentColor": "#00F5D4",
        "textColor": "#FFF7FD",
        "mutedColor": "#B39CC8",
        "successColor": "#00F5D4",
        "warningColor": "#FEE440",
        "errorColor": "#FF4D6D",
        "opacity": 0.92,
        "animationSpeed": 1.2,
        "cornerRadius": 12,
        "shadowIntensity": 0.90,
    },
]

for t in themes:
    (THEMES_DIR / f"{t['id']}.json").write_text(json.dumps(t, indent=2) + "\n", encoding="utf-8")

# 5. JSON Schemas for Config, Theme, and Update Manifest
config_schema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "AXSESCLIENT Configuration Profile Schema v1",
    "type": "object",
    "required": ["schemaVersion", "profileName", "activeThemeId", "guiScale", "modules", "hudElements"],
    "properties": {
        "schemaVersion": {"type": "integer", "const": 1},
        "profileName": {"type": "string", "minLength": 1, "maxLength": 64},
        "activeThemeId": {"type": "string"},
        "guiScale": {"type": "number", "minimum": 0.5, "maximum": 2.0},
        "guiX": {"type": "number"},
        "guiY": {"type": "number"},
        "openGuiKey": {"type": "integer"},
        "reducedMotion": {"type": "boolean"},
        "animationSpeed": {"type": "number", "minimum": 0.25, "maximum": 3.0},
        "animationIntensity": {"type": "number", "minimum": 0.0, "maximum": 1.0},
        "colorBlindMode": {"type": "string", "enum": ["NONE", "PROTANOPIA", "DEUTERANOPIA", "TRITANOPIA"]},
        "fontScale": {"type": "string", "enum": ["SMALL", "NORMAL", "LARGE"]},
        "language": {"type": "string", "enum": ["en_US", "ru_RU"]},
        "modules": {"type": "object"},
        "hudElements": {"type": "object"},
    },
}
(SCHEMAS_DIR / "config.schema.json").write_text(json.dumps(config_schema, indent=2) + "\n", encoding="utf-8")

theme_schema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "AXSESCLIENT Theme Schema v1",
    "type": "object",
    "required": [
        "schemaVersion",
        "id",
        "name",
        "backgroundColor",
        "surfaceColor",
        "primaryColor",
        "secondaryColor",
        "accentColor",
        "textColor",
        "opacity",
        "animationSpeed",
        "cornerRadius",
        "shadowIntensity",
    ],
    "properties": {
        "schemaVersion": {"type": "integer", "const": 1},
        "id": {"type": "string", "pattern": "^[a-z0-9_-]{2,48}$"},
        "name": {"type": "string", "minLength": 1, "maxLength": 48},
        "opacity": {"type": "number", "minimum": 0.35, "maximum": 1.0},
        "animationSpeed": {"type": "number", "minimum": 0.25, "maximum": 3.0},
        "cornerRadius": {"type": "integer", "minimum": 0, "maximum": 28},
        "shadowIntensity": {"type": "number", "minimum": 0.0, "maximum": 1.0},
    },
}
(SCHEMAS_DIR / "theme.schema.json").write_text(json.dumps(theme_schema, indent=2) + "\n", encoding="utf-8")

update_schema = {
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "AXSESCLIENT Signed Update Manifest Schema v1",
    "type": "object",
    "required": ["schemaVersion", "productVersion", "publishedAt", "artifacts", "signatureAlgorithm", "signatureBase64"],
    "properties": {
        "schemaVersion": {"type": "integer", "const": 1},
        "productVersion": {"type": "string"},
        "publishedAt": {"type": "string"},
        "releaseNotes": {"type": "string"},
        "signatureAlgorithm": {"type": "string", "enum": ["RSA-SHA256"]},
        "signatureBase64": {"type": "string"},
        "artifacts": {
            "type": "array",
            "items": {
                "type": "object",
                "required": ["name", "relativeTarget", "url", "sha256", "sizeBytes"],
                "properties": {
                    "name": {"type": "string"},
                    "relativeTarget": {"type": "string"},
                    "url": {"type": "string"},
                    "sha256": {"type": "string", "pattern": "^[a-fA-F0-9]{64}$"},
                    "sizeBytes": {"type": "integer", "minimum": 1},
                },
            },
        },
    },
}
(SCHEMAS_DIR / "update-manifest.schema.json").write_text(json.dumps(update_schema, indent=2) + "\n", encoding="utf-8")

print("Generated AXSES NEO-DARK branding assets, 8 themes, and 3 schemas successfully.")
