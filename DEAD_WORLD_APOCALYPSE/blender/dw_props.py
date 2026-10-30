"""DEAD WORLD: weapons, vehicles, buildings, environment & pickups (Blender/bpy).
All models: origin conventions documented per builder; forward = -Y (Blender),
which exports to +Z forward in the engine."""
import math, random
from dw_lib import (reset_scene, mat, box, shaped_box, cylinder, sphere, cone, ico,
                    join, move_geom, export_obj, export_glb, save_blend, stats)

GUNMETAL = (0.09, 0.09, 0.1)
GUNMETAL2 = (0.16, 0.16, 0.18)
WOOD = (0.36, 0.22, 0.11)
STEEL = (0.45, 0.47, 0.5)


def _m(name, c, r=0.6, mtl=0.55):
    return mat(name, c, r, mtl)


# ---------------------------------------------------------------- weapons
# Origin at grip, barrel along -Y, up +Z. Lengths in meters.

def build_pistol():
    reset_scene()
    gm, gm2 = _m("gm", GUNMETAL), _m("gm2", GUNMETAL2)
    o = []
    o.append(box("slide", (0.026, 0.19, 0.028), (0, -0.045, 0.028), gm, bevel=0.004))
    o.append(box("frame", (0.024, 0.16, 0.024), (0, -0.03, 0.002), gm2, bevel=0.003))
    o.append(cylinder("barrel_tip", 0.008, 0.02, (0, -0.145, 0.028), _m("steel", STEEL), rot=(math.pi / 2, 0, 0)))
    grip = box("grip", (0.03, 0.055, 0.115), (0, 0.028, -0.062), _m("grip_polym", (0.12, 0.12, 0.12), 0.9, 0.0),
               bevel=0.006, rot=(0.28, 0, 0))
    o.append(grip)
    o.append(box("mag_base", (0.032, 0.05, 0.014), (0, 0.045, -0.118), gm2, bevel=0.002, rot=(0.28, 0, 0)))
    o.append(box("trigger_guard", (0.012, 0.06, 0.008), (0, -0.035, -0.028), gm2, bevel=0.002))
    o.append(box("trigger", (0.006, 0.012, 0.03), (0, -0.02, -0.02), gm, bevel=0.001, rot=(-0.3, 0, 0)))
    o.append(box("sight_front", (0.004, 0.008, 0.014), (0, -0.125, 0.05), gm))
    o.append(box("sight_rear", (0.02, 0.01, 0.012), (0, 0.038, 0.048), gm))
    o.append(box("hammer", (0.008, 0.014, 0.02), (0, 0.052, 0.032), gm2, rot=(0.5, 0, 0)))
    for i in range(4):  # slide serrations
        o.append(box("serr", (0.028, 0.004, 0.02), (0, 0.028 - i * 0.009, 0.028), gm2))
    return join(o, "pistol")


def build_revolver():
    reset_scene()
    gm, wood = _m("gm", (0.2, 0.2, 0.22)), _m("wood", WOOD, 0.8, 0.0)
    o = []
    o.append(cylinder("barrel", 0.011, 0.17, (0, -0.115, 0.02), gm, rot=(math.pi / 2, 0, 0), vertices=10))
    o.append(box("barrel_rib", (0.012, 0.17, 0.008), (0, -0.115, 0.033), gm))
    o.append(cylinder("cylinder", 0.021, 0.045, (0, -0.012, 0.012), _m("steel", STEEL), rot=(math.pi / 2, 0, 0), vertices=12))
    o.append(box("frame", (0.022, 0.09, 0.035), (0, 0.01, 0.008), gm, bevel=0.003))
    o.append(box("grip", (0.028, 0.05, 0.11), (0, 0.035, -0.055), wood, bevel=0.007, rot=(0.38, 0, 0)))
    o.append(box("trigger_guard", (0.01, 0.05, 0.007), (0, -0.015, -0.028), gm))
    o.append(box("hammer", (0.008, 0.016, 0.022), (0, 0.052, 0.028), gm, rot=(0.6, 0, 0)))
    o.append(box("sight", (0.004, 0.008, 0.012), (0, -0.19, 0.042), gm))
    return join(o, "revolver")


def build_rifle():
    reset_scene()
    gm, gm2, wood = _m("gm", GUNMETAL), _m("gm2", GUNMETAL2), _m("wood", WOOD, 0.85, 0.0)
    o = []
    o.append(box("receiver", (0.045, 0.34, 0.06), (0, 0.02, 0.01), gm, bevel=0.005))
    stock = shaped_box("stock", (0.042, 0.26, 0.09), (0, 0, 0), wood,
                       lambda z: (1.0, 1.0, 0, 0), bevel=0.006)
    stock.rotation_euler = (0, math.radians(90), 0)  # not used; replaced below
    # build stock manually oriented along Y:
    import bpy as _bpy
    _bpy.data.objects.remove(stock, do_unlink=True)
    o.append(box("stock_main", (0.04, 0.24, 0.055), (0, 0.29, -0.005), wood, bevel=0.006, rot=(0.06, 0, 0)))
    o.append(box("butt", (0.042, 0.05, 0.115), (0, 0.41, -0.028), wood, bevel=0.006))
    o.append(box("handguard", (0.048, 0.22, 0.055), (0, -0.25, 0.008), wood, bevel=0.008))
    o.append(cylinder("barrel", 0.009, 0.3, (0, -0.45, 0.025), gm2, rot=(math.pi / 2, 0, 0)))
    o.append(cylinder("gas_tube", 0.007, 0.2, (0, -0.28, 0.045), gm2, rot=(math.pi / 2, 0, 0)))
    o.append(box("front_sight", (0.006, 0.012, 0.045), (0, -0.52, 0.045), gm))
    o.append(cylinder("muzzle", 0.012, 0.045, (0, -0.605, 0.025), gm, rot=(math.pi / 2, 0, 0)))
    # curved magazine from rotated segments
    for i in range(4):
        ang = 0.25 + i * 0.16
        y = -0.02 - math.sin(ang) * 0.05 * i - i * 0.028
        z = -0.05 - i * 0.045
        o.append(box("mag_seg", (0.028, 0.055, 0.05), (0, y, z), gm2, bevel=0.003, rot=(ang, 0, 0)))
    o.append(box("pistol_grip", (0.03, 0.05, 0.1), (0, 0.12, -0.065), mat("bakelite", (0.3, 0.16, 0.08), 0.7), bevel=0.005, rot=(0.35, 0, 0)))
    o.append(box("rear_sight", (0.03, 0.015, 0.02), (0, -0.08, 0.05), gm))
    o.append(box("charging", (0.01, 0.03, 0.012), (0.028, 0.06, 0.02), gm2))
    o.append(box("trigger_guard", (0.012, 0.07, 0.008), (0, 0.05, -0.035), gm2))
    o.append(box("trigger", (0.006, 0.012, 0.03), (0, 0.06, -0.03), gm, rot=(-0.3, 0, 0)))
    return join(o, "rifle")


def build_shotgun():
    reset_scene()
    gm, wood = _m("gm", GUNMETAL), _m("wood", (0.4, 0.26, 0.14), 0.85, 0.0)
    o = []
    o.append(box("receiver", (0.05, 0.22, 0.055), (0, 0.05, 0.005), gm, bevel=0.006))
    o.append(cylinder("barrel", 0.013, 0.5, (0, -0.3, 0.028), gm, rot=(math.pi / 2, 0, 0)))
    o.append(cylinder("mag_tube", 0.011, 0.4, (0, -0.22, -0.002), _m("gm2", GUNMETAL2), rot=(math.pi / 2, 0, 0)))
    pump = cylinder("pump", 0.023, 0.13, (0, -0.3, -0.002), wood, rot=(math.pi / 2, 0, 0), vertices=12)
    o.append(pump)
    for i in range(5):
        o.append(cylinder("pump_rib", 0.025, 0.008, (0, -0.35 + i * 0.025, -0.002), wood, rot=(math.pi / 2, 0, 0), vertices=12))
    o.append(box("stock", (0.042, 0.22, 0.06), (0, 0.26, -0.01), wood, bevel=0.007, rot=(0.08, 0, 0)))
    o.append(box("butt", (0.044, 0.05, 0.12), (0, 0.38, -0.03), wood, bevel=0.006))
    o.append(sphere("bead", 0.005, (0, -0.545, 0.045), (1, 1, 1), _m("steel", STEEL), segments=6))
    o.append(box("trigger_guard", (0.012, 0.06, 0.008), (0, 0.08, -0.035), gm))
    return join(o, "shotgun")


def build_sniper():
    reset_scene()
    gm, gm2 = _m("gm", (0.08, 0.09, 0.08)), _m("gm2", GUNMETAL2)
    body = _m("synthetic", (0.13, 0.14, 0.12), 0.9, 0.0)
    o = []
    o.append(box("chassis", (0.045, 0.5, 0.06), (0, -0.1, 0.0), body, bevel=0.006))
    o.append(box("stock", (0.042, 0.26, 0.075), (0, 0.28, -0.008), body, bevel=0.006))
    o.append(box("cheek", (0.044, 0.12, 0.03), (0, 0.22, 0.045), body, bevel=0.005))
    o.append(cylinder("barrel", 0.010, 0.55, (0, -0.55, 0.02), gm2, rot=(math.pi / 2, 0, 0)))
    o.append(cylinder("brake", 0.016, 0.07, (0, -0.85, 0.02), gm, rot=(math.pi / 2, 0, 0)))
    o.append(cylinder("scope", 0.021, 0.24, (0, -0.05, 0.085), gm, rot=(math.pi / 2, 0, 0), vertices=12))
    o.append(cylinder("scope_bell", 0.027, 0.05, (0, -0.16, 0.085), gm, rot=(math.pi / 2, 0, 0), vertices=12))
    o.append(cylinder("scope_lens", 0.024, 0.004, (0, -0.187, 0.085), _m("lens", (0.15, 0.3, 0.45), 0.15, 0.2), rot=(math.pi / 2, 0, 0), vertices=12))
    for y in (-0.12, 0.02):
        o.append(box("mount", (0.014, 0.02, 0.035), (0, y, 0.05), gm2))
    o.append(box("mag", (0.032, 0.07, 0.09), (0, 0.02, -0.07), gm2, bevel=0.004))
    o.append(box("grip", (0.03, 0.05, 0.1), (0, 0.14, -0.06), body, bevel=0.005, rot=(0.32, 0, 0)))
    for s in (-1, 1):  # bipod legs
        leg = cylinder("bipod", 0.005, 0.2, (0.05 * s, -0.32, -0.1), gm2, rot=(0.5, 0, s * 0.42))
        o.append(leg)
    o.append(box("bolt", (0.012, 0.05, 0.014), (0.03, 0.06, 0.03), _m("steel", STEEL), rot=(0, 0, -0.5)))
    return join(o, "sniper")


def _blade(name, length, width, thick, material):
    def prof(z):  # taper to point near top (z=1)
        sx = 1.0 if z < 0.75 else max(0.04, 1.0 - (z - 0.75) * 3.8)
        return (sx, 1.0, 0, 0)
    b = shaped_box(name, (width, thick, length), (0, 0, length / 2), material, prof, bevel=0.001)
    return b


def build_knife():
    reset_scene()
    o = [_blade("blade", 0.17, 0.032, 0.006, _m("steel", (0.62, 0.64, 0.66), 0.3, 0.9)),
         box("guard", (0.05, 0.012, 0.012), (0, 0, 0.0), _m("brass", (0.55, 0.42, 0.18), 0.4, 0.8)),
         cylinder("handle", 0.014, 0.11, (0, 0, -0.062), mat("knife_grip", (0.09, 0.08, 0.08), 0.9), vertices=10)]
    # blade points +Z (up from grip); rotate whole knife so blade points -Y forward
    k = join(o, "knife")
    k.rotation_euler = (-math.pi / 2, 0, 0)
    return k


def build_machete():
    reset_scene()
    blade = _blade("blade", 0.42, 0.055, 0.005, _m("steel_dark", (0.4, 0.42, 0.45), 0.4, 0.8))
    handle = box("handle", (0.028, 0.02, 0.12), (0, 0, -0.06), mat("machete_grip", (0.14, 0.1, 0.07), 0.9), bevel=0.004)
    k = join([blade, handle], "machete")
    k.rotation_euler = (-math.pi / 2, 0, 0)
    return k


def build_axe():
    reset_scene()
    handle = cylinder("haft", 0.016, 0.72, (0, 0, 0.1), _m("haft_wood", (0.42, 0.3, 0.16), 0.85, 0.0), taper_top=0.85)
    head = box("poll", (0.035, 0.06, 0.07), (0, 0, 0.42), _m("steel", (0.35, 0.36, 0.38), 0.45, 0.8), bevel=0.004)
    blade = shaped_box("axe_blade", (0.16, 0.012, 0.13), (0.085, 0, 0.42),
                       _m("steel", (0.5, 0.52, 0.55), 0.35, 0.9),
                       lambda z: (1.0, 1.0, 0, 0), bevel=0.002)
    a = join([handle, head, blade], "axe")
    a.rotation_euler = (-math.pi / 2, 0, 0)
    return a


def build_bat():
    reset_scene()
    import bpy
    bpy.ops.mesh.primitive_cylinder_add(radius=1.0, depth=0.82, vertices=14, location=(0, 0, 0.31))
    b = bpy.context.active_object
    b.name = "bat_m"
    for v in b.data.vertices:
        zn = (v.co.z + 0.1) / 0.82
        r = 0.016 + 0.02 * max(0.0, zn) ** 1.6
        v.co.x *= r; v.co.y *= r
    for p in b.data.polygons:
        p.use_smooth = True
    b.data.materials.append(mat("bat_wood", (0.55, 0.4, 0.22), 0.8))
    knob = cylinder("knob", 0.024, 0.02, (0, 0, -0.105), mat("bat_wood", (0.55, 0.4, 0.22), 0.8))
    nails = []
    for i in range(7):  # improvised nails near the striking end
        ang = i * 2.4
        nails.append(cone("nail", 0.0028, 0.0005, 0.035,
                          (math.cos(ang) * 0.035, math.sin(ang) * 0.035, 0.6 + (i % 3) * 0.05),
                          _m("nail_steel", (0.55, 0.55, 0.58), 0.35, 0.9),
                          rot=(math.sin(ang) * 1.2, -math.cos(ang) * 1.2, 0), vertices=5))
    b2 = join([b, knob] + nails, "bat")
    b2.rotation_euler = (-math.pi / 2, 0, 0)
    return b2


def build_crossbow():
    reset_scene()
    wood, gm = _m("cross_wood", (0.32, 0.2, 0.1), 0.85, 0.0), _m("gm", GUNMETAL)
    o = []
    o.append(box("stock", (0.04, 0.55, 0.055), (0, 0.02, 0.0), wood, bevel=0.006))
    o.append(box("butt", (0.042, 0.07, 0.11), (0, 0.31, -0.02), wood, bevel=0.005))
    o.append(box("rail", (0.02, 0.5, 0.012), (0, -0.02, 0.035), gm))
    for s in (-1, 1):  # limbs sweep back
        for i in range(3):
            x = s * (0.09 + i * 0.115)
            y = -0.24 + i * 0.075
            o.append(box("limb", (0.13, 0.028, 0.01), (x, y, 0.015), _m("limb", (0.15, 0.13, 0.11), 0.6, 0.3),
                         bevel=0.002, rot=(0, 0, s * (0.55 + i * 0.22))))
    o.append(cylinder("string_l", 0.0016, 0.42, (-0.165, -0.06, 0.03), gm, rot=(0.0, 0, 1.12)))
    o.append(cylinder("string_r", 0.0016, 0.42, (0.165, -0.06, 0.03), gm, rot=(0.0, 0, -1.12)))
    o.append(cylinder("bolt", 0.0035, 0.34, (0, -0.12, 0.045), _m("bolt_wood", (0.5, 0.38, 0.2), 0.8, 0.0), rot=(math.pi / 2, 0, 0)))
    o.append(cone("bolt_tip", 0.006, 0.0008, 0.03, (0, -0.3, 0.045), _m("steel", STEEL), rot=(-math.pi / 2, 0, 0), vertices=6))
    o.append(box("grip", (0.03, 0.05, 0.09), (0, 0.16, -0.055), wood, bevel=0.005, rot=(0.3, 0, 0)))
    o.append(box("sight", (0.006, 0.01, 0.025), (0, -0.2, 0.055), gm))
    return join(o, "crossbow")


WEAPON_BUILDERS = {"pistol": build_pistol, "revolver": build_revolver, "rifle": build_rifle,
                   "shotgun": build_shotgun, "sniper": build_sniper, "knife": build_knife,
                   "machete": build_machete, "axe": build_axe, "bat": build_bat,
                   "crossbow": build_crossbow}


# ---------------------------------------------------------------- vehicles
# Body origin at ground center; length along Y (front -Y). Wheel origin at axle center.

def _car_body(name, cfg):
    paint_m = mat(name + "_paint", cfg["color"], 0.35, 0.65)
    glass = mat("glass_dark", (0.08, 0.12, 0.16), 0.12, 0.4)
    trim = mat("trim_black", (0.07, 0.07, 0.08), 0.7)
    L, W, H = cfg["length"], cfg["width"], cfg["body_h"]
    cab_h, cab_len, cab_y = cfg["cab_h"], cfg["cab_len"], cfg["cab_y"]
    ground = 0.32
    o = []
    # lower body with sloped hood (front -Y) via profile on rotated shaped box:
    body = box("body", (W, L, H), (0, 0, ground + H / 2), paint_m, bevel=0.05)
    # slope the hood: move front-top verts down/forward
    for v in body.data.vertices:
        if v.co.y < -L * 0.28 and v.co.z > ground + H * 0.55:
            t = (-v.co.y - L * 0.28) / (L * 0.22)
            v.co.z -= cfg.get("hood_drop", 0.16) * max(0.0, min(1.0, t))
    o.append(body)
    cab = box("cabin", (W * 0.86, cab_len, cab_h), (0, cab_y, ground + H + cab_h / 2 - 0.04), paint_m, bevel=0.05)
    for v in cab.data.vertices:  # rake windshield & rear window
        if v.co.z > ground + H + cab_h * 0.3:
            if v.co.y < cab_y - cab_len * 0.3:
                v.co.y += cfg.get("rake", 0.22)
            if v.co.y > cab_y + cab_len * 0.3:
                v.co.y -= cfg.get("rake", 0.22) * 0.7
    o.append(cab)
    win = box("windows", (W * 0.88, cab_len * 0.94, cab_h * 0.52),
              (0, cab_y + 0.02, ground + H + cab_h * 0.42), glass, bevel=0.04)
    for v in win.data.vertices:
        if v.co.y < cab_y - cab_len * 0.3:
            v.co.y += cfg.get("rake", 0.22) * 0.92
        if v.co.y > cab_y + cab_len * 0.3:
            v.co.y -= cfg.get("rake", 0.22) * 0.62
    o.append(win)
    o.append(box("bumper_f", (W * 0.96, 0.18, 0.16), (0, -L / 2 - 0.02, ground + 0.1), trim, bevel=0.04))
    o.append(box("bumper_r", (W * 0.96, 0.18, 0.16), (0, L / 2 + 0.02, ground + 0.1), trim, bevel=0.04))
    o.append(box("grille", (W * 0.5, 0.03, 0.12), (0, -L / 2 - 0.06, ground + H * 0.42), trim, bevel=0.01))
    light_m = mat("headlight", (0.95, 0.9, 0.7), 0.25, 0.2)
    tail_m = mat("taillight", (0.6, 0.06, 0.06), 0.3, 0.2)
    for s in (-1, 1):
        o.append(box("headlight", (0.3, 0.03, 0.09), (s * W * 0.32, -L / 2 - 0.05, ground + H * 0.5), light_m, bevel=0.01))
        o.append(box("taillight", (0.28, 0.03, 0.08), (s * W * 0.32, L / 2 + 0.05, ground + H * 0.55), tail_m, bevel=0.01))
        o.append(box("mirror", (0.05, 0.09, 0.06), (s * (W * 0.48 + 0.03), cab_y - cab_len * 0.42, ground + H + 0.12), trim, bevel=0.01))
    if cfg.get("bed"):  # pickup bed walls
        for s in (-1, 1):
            o.append(box("bed_wall", (0.06, cfg["bed"], 0.3), (s * (W / 2 - 0.05), L / 2 - cfg["bed"] / 2, ground + H + 0.1), paint_m, bevel=0.02))
        o.append(box("bed_gate", (W * 0.94, 0.05, 0.3), (0, L / 2 - 0.04, ground + H + 0.1), paint_m, bevel=0.02))
        o.append(box("bed_floor", (W * 0.88, cfg["bed"], 0.04), (0, L / 2 - cfg["bed"] / 2, ground + H - 0.02), trim, bevel=0.01))
    if cfg.get("bullbar"):
        bm = mat("bullbar", (0.2, 0.2, 0.2), 0.4, 0.8)
        o.append(cylinder("bull_h", 0.03, W * 0.8, (0, -L / 2 - 0.18, ground + 0.35), bm, rot=(0, math.pi / 2, 0)))
        o.append(cylinder("bull_h2", 0.03, W * 0.8, (0, -L / 2 - 0.18, ground + 0.62), bm, rot=(0, math.pi / 2, 0)))
        for s in (-1, 1):
            o.append(cylinder("bull_v", 0.025, 0.45, (s * W * 0.35, -L / 2 - 0.18, ground + 0.48), bm))
    if cfg.get("roof_rack"):
        rm = mat("rack", (0.15, 0.15, 0.15), 0.6, 0.5)
        o.append(box("rack", (W * 0.7, cab_len * 0.7, 0.05), (0, cab_y, ground + H + cab_h + 0.05), rm, bevel=0.01))
    if cfg.get("wrecked"):
        rnd = random.Random(name)
        rust = mat("rust", (0.42, 0.24, 0.13), 0.95)
        for i in range(6):
            o.append(box("rust_patch", (rnd.uniform(0.2, 0.5), rnd.uniform(0.2, 0.6), 0.015),
                         (rnd.uniform(-W / 2, W / 2) * 0.9, rnd.uniform(-L / 2, L / 2),
                          ground + H + 0.01 + (0.0 if i % 2 else cab_h)), rust, bevel=0.005))
        for v in o[0].data.vertices:  # dent the body
            if v.co.y < -L * 0.35 and v.co.x > W * 0.2:
                v.co.z -= 0.12
                v.co.x -= 0.06
    return join(o, name)


def _wheel(name, radius, width):
    tire = cylinder("tire", radius, width, (0, 0, 0), mat("tire", (0.05, 0.05, 0.055), 0.95),
                    rot=(0, math.pi / 2, 0), vertices=18)
    hub = cylinder("hub", radius * 0.52, width + 0.012, (0, 0, 0), mat("hub", (0.4, 0.41, 0.43), 0.35, 0.8),
                   rot=(0, math.pi / 2, 0), vertices=10)
    spokes = []
    for i in range(5):
        a = i * 2 * math.pi / 5
        spokes.append(box("spoke", (width + 0.02, radius * 0.13, radius * 0.78),
                          (0, math.cos(a) * radius * 0.42, math.sin(a) * radius * 0.42),
                          mat("hub", (0.4, 0.41, 0.43), 0.35, 0.8), rot=(a, 0, 0)))
    return join([tire, hub] + spokes, name)


VEHICLE_CFGS = {
    "sedan":    dict(length=4.5, width=1.8, body_h=0.55, cab_h=0.5, cab_len=2.1, cab_y=0.15, color=(0.35, 0.13, 0.11), hood_drop=0.18),
    "suv":      dict(length=4.6, width=1.9, body_h=0.72, cab_h=0.62, cab_len=2.9, cab_y=0.3, color=(0.13, 0.2, 0.28), hood_drop=0.05, roof_rack=True, bullbar=False),
    "pickup":   dict(length=5.0, width=1.95, body_h=0.68, cab_h=0.6, cab_len=1.7, cab_y=-0.65, color=(0.4, 0.4, 0.42), hood_drop=0.08, bed=1.8),
    "military": dict(length=4.9, width=2.05, body_h=0.75, cab_h=0.6, cab_len=2.6, cab_y=0.1, color=(0.23, 0.27, 0.15), hood_drop=0.03, rake=0.05, bullbar=True, roof_rack=True),
    "wrecked_car": dict(length=4.4, width=1.78, body_h=0.55, cab_h=0.5, cab_len=2.0, cab_y=0.1, color=(0.3, 0.28, 0.26), hood_drop=0.2, wrecked=True),
}


def build_vehicle(name):
    reset_scene()
    cfg = VEHICLE_CFGS[name]
    body = _car_body(name + "_body", cfg)
    wheel_r = 0.34 if name in ("suv", "pickup", "military") else 0.31
    wheel = _wheel(name + "_wheel", wheel_r, 0.22)
    wheelbase = cfg["length"] * 0.62
    meta = {"wheel_radius": wheel_r, "wheel_offsets": [
        [-(cfg["width"] / 2 - 0.12), wheel_r, wheelbase / 2],
        [cfg["width"] / 2 - 0.12, wheel_r, wheelbase / 2],
        [-(cfg["width"] / 2 - 0.12), wheel_r, -wheelbase / 2],
        [cfg["width"] / 2 - 0.12, wheel_r, -wheelbase / 2]],
        "length": cfg["length"], "width": cfg["width"],
        "height": 0.32 + cfg["body_h"] + cfg["cab_h"],
        "seat_offset": [-0.42, 0.95, -cfg["cab_y"]],
    }
    return body, wheel, meta


# ---------------------------------------------------------------- buildings & env

def _windows_grid(objs, W, H, D, floors, cols, wall_z0, glass, seed=3, lit_ratio=0.0):
    rnd = random.Random(seed)
    lit = mat("window_lit", (0.9, 0.75, 0.4), 0.4)
    broken = mat("window_broken", (0.04, 0.04, 0.05), 0.3)
    for f in range(floors):
        for c in range(cols):
            x = -W / 2 + (c + 0.5) * (W / cols)
            z = wall_z0 + 1.1 + f * ((H - 1.6) / max(floors, 1))
            r = rnd.random()
            m = lit if r < lit_ratio else (broken if r < lit_ratio + 0.3 else glass)
            objs.append(box("win_f", (0.9, 0.05, 1.1), (x, -D / 2 - 0.01, z), m))
            objs.append(box("win_b", (0.9, 0.05, 1.1), (x, D / 2 + 0.01, z), m))
    return objs


def build_building(name, W=10, D=9, H=7, wall=(0.55, 0.5, 0.45), roof="flat",
                   floors=2, cols=4, ruined=False, sign_color=None, seed=5):
    reset_scene()
    rnd = random.Random(seed + len(name))
    wall_m = mat(name + "_wall", wall, 0.95)
    dark = mat("interior_dark", (0.05, 0.05, 0.05), 1.0)
    glass = mat("glass", (0.12, 0.18, 0.24), 0.2, 0.3)
    concrete = mat("concrete_dark", (0.32, 0.31, 0.3), 0.95)
    o = []
    body = box("body", (W, D, H), (0, 0, H / 2), wall_m, bevel=0.03)
    if ruined:  # collapse one top corner, scorch
        for v in body.data.vertices:
            d = (v.co.x - W / 2) + (v.co.z - H) + (v.co.y + D / 2)
            if v.co.x > W * 0.18 and v.co.z > H * 0.55 and v.co.y < -D * 0.1:
                v.co.z -= rnd.uniform(0.6, 2.2)
                v.co.x -= rnd.uniform(0, 0.4)
    o.append(body)
    if roof == "flat":
        o.append(box("roof", (W + 0.3, D + 0.3, 0.25), (0, 0, H + 0.1), concrete, bevel=0.02))
        o.append(box("roof_unit", (1.6, 1.2, 0.7), (W * 0.22, D * 0.15, H + 0.55), mat("hvac", (0.45, 0.46, 0.47), 0.7, 0.4), bevel=0.03))
    else:  # gable roof: two sloped slabs + gable triangles approximated
        rm = mat(name + "_roof", (0.3, 0.16, 0.12), 0.9)
        for s in (-1, 1):
            slab = box("roof_slab", (W + 0.5, D * 0.62, 0.1), (0, s * D * 0.24, H + 0.62), rm, bevel=0.02,
                       rot=(s * -0.72, 0, 0))
            o.append(slab)
        for s in (-1, 1):
            g = box("gable", (0.15, D * 0.7, 1.1), (s * (W / 2 - 0.08), 0, H + 0.4), wall_m, bevel=0.02)
            for v in g.data.vertices:
                if v.co.z > H + 0.85:
                    v.co.y *= 0.08
            o.append(g)
    # storefront / entrance on -Y face
    o.append(box("door_recess", (1.5, 0.1, 2.3), (0, -D / 2 - 0.02, 1.15), dark))
    o.append(box("door", (1.1, 0.06, 2.1), (0, -D / 2 - 0.05, 1.05), mat("door_wood", (0.25, 0.16, 0.1), 0.85), bevel=0.01))
    o.append(box("step", (2.0, 0.5, 0.14), (0, -D / 2 - 0.3, 0.07), concrete, bevel=0.01))
    _windows_grid(o, W, H, D, floors, cols, 0.0, glass, seed=seed, lit_ratio=0.06)
    # side windows
    for f in range(floors):
        for c in range(2):
            y = -D / 4 + c * (D / 2)
            z = 1.1 + f * ((H - 1.6) / max(floors, 1))
            o.append(box("win_s", (0.05, 0.9, 1.1), (-W / 2 - 0.01, y, z), glass))
            o.append(box("win_s2", (0.05, 0.9, 1.1), (W / 2 + 0.01, y, z), glass))
    if sign_color:
        o.append(box("sign_board", (W * 0.62, 0.12, 0.85), (0, -D / 2 - 0.12, H - 0.65),
                     mat(name + "_sign", sign_color, 0.6), bevel=0.02))
    if ruined:
        rub = mat("rubble", (0.36, 0.34, 0.31), 1.0)
        for i in range(7):
            o.append(ico("rubble", rnd.uniform(0.25, 0.7),
                         (rnd.uniform(-W / 2, W / 2), -D / 2 - rnd.uniform(0.4, 1.6), 0.2),
                         rub, noise=0.4, seed=seed + i))
        # exposed rebar
        for i in range(4):
            o.append(cylinder("rebar", 0.02, rnd.uniform(0.7, 1.4),
                              (W * rnd.uniform(0.1, 0.45), -D * 0.3, H - rnd.uniform(0.5, 1.6)),
                              mat("rebar", (0.3, 0.2, 0.15), 0.6, 0.7), rot=(rnd.uniform(-0.6, 0.6), rnd.uniform(-0.4, 0.4), 0), vertices=6))
        scorch = mat("scorch", (0.06, 0.05, 0.05), 1.0)
        o.append(box("scorch", (W * 0.4, 0.04, H * 0.5), (W * 0.2, -D / 2 - 0.015, H * 0.62), scorch))
    b = join(o, name)
    return b


BUILDINGS = {
    "house":      dict(W=8, D=7, H=3.4, wall=(0.6, 0.55, 0.45), roof="gable", floors=1, cols=3),
    "house2":     dict(W=9, D=8, H=6.2, wall=(0.5, 0.42, 0.36), roof="gable", floors=2, cols=3),
    "apartment":  dict(W=16, D=11, H=15, wall=(0.52, 0.48, 0.43), roof="flat", floors=5, cols=6),
    "apartment2": dict(W=13, D=10, H=11.5, wall=(0.45, 0.4, 0.38), roof="flat", floors=4, cols=5),
    "shop":       dict(W=12, D=9, H=4.6, wall=(0.58, 0.5, 0.4), roof="flat", floors=1, cols=4, sign_color=(0.8, 0.6, 0.15)),
    "hospital":   dict(W=20, D=13, H=12, wall=(0.78, 0.78, 0.75), roof="flat", floors=4, cols=7, sign_color=(0.8, 0.1, 0.1)),
    "police":     dict(W=14, D=10, H=7.5, wall=(0.42, 0.45, 0.52), roof="flat", floors=2, cols=5, sign_color=(0.1, 0.15, 0.45)),
    "warehouse":  dict(W=18, D=12, H=6, wall=(0.4, 0.42, 0.44), roof="flat", floors=1, cols=4),
    "factory":    dict(W=22, D=14, H=8, wall=(0.5, 0.38, 0.3), roof="flat", floors=2, cols=6),
    "ruined":     dict(W=13, D=10, H=11, wall=(0.4, 0.37, 0.34), roof="flat", floors=3, cols=5, ruined=True),
    "ruined2":    dict(W=9, D=8, H=6.5, wall=(0.44, 0.4, 0.36), roof="gable", floors=2, cols=3, ruined=True),
    "village_house": dict(W=7, D=6, H=3.1, wall=(0.55, 0.42, 0.3), roof="gable", floors=1, cols=2),
}


def build_factory_extra():
    """Chimney added to factory after base build (called by driver into same scene)."""
    parts = list(__import__("bpy").data.objects)
    ch = cylinder("chimney", 0.9, 7.0, (7.5, 3.5, 11.0), mat("chimney", (0.55, 0.35, 0.28), 0.9), vertices=14, taper_top=0.75)
    band = cylinder("chimney_band", 0.78, 0.8, (7.5, 3.5, 14.2), mat("chimney_top", (0.25, 0.22, 0.2), 0.9), vertices=14)
    return join(parts + [ch, band], "factory")


def build_gas_station():
    reset_scene()
    o = []
    o.append(box("kiosk", (6, 4.5, 3), (0, 3.2, 1.5), mat("gas_wall", (0.65, 0.62, 0.58), 0.9), bevel=0.03))
    o.append(box("kiosk_glass", (5.4, 0.06, 1.6), (0, 0.93, 1.4), mat("glass", (0.12, 0.18, 0.24), 0.2, 0.3)))
    canopy = mat("gas_canopy", (0.7, 0.16, 0.12), 0.6)
    o.append(box("canopy", (10, 6.5, 0.28), (0, -2.2, 4.4), canopy, bevel=0.04))
    o.append(box("canopy_trim", (10.15, 6.65, 0.1), (0, -2.2, 4.22), mat("canopy_line", (0.9, 0.88, 0.85), 0.5)))
    for x in (-4.4, 4.4):
        for y in (-4.6, 0.2):
            o.append(cylinder("pillar", 0.14, 4.3, (x, y, 2.15), mat("pillar", (0.6, 0.6, 0.62), 0.5, 0.6), vertices=10))
    for x in (-2.2, 2.2):  # pumps
        o.append(box("pump", (0.55, 0.4, 1.35), (x, -2.2, 0.68), mat("pump_body", (0.75, 0.72, 0.7), 0.5, 0.3), bevel=0.03))
        o.append(box("pump_screen", (0.36, 0.03, 0.3), (x, -2.42, 1.05), mat("pump_screen", (0.1, 0.16, 0.2), 0.2)))
        o.append(box("pump_red", (0.57, 0.42, 0.18), (x, -2.2, 1.3), canopy, bevel=0.02))
    o.append(box("sign_pole", (0.18, 0.18, 6.2), (-5.6, -4.8, 3.1), mat("pole", (0.5, 0.5, 0.52), 0.5, 0.6)))
    o.append(box("sign_board", (2.6, 0.15, 1.1), (-5.6, -4.8, 6.4), canopy, bevel=0.03))
    return join(o, "gas_station")


def build_tree(dead=False, seed=2):
    reset_scene()
    rnd = random.Random(seed)
    bark = mat("bark", (0.25, 0.18, 0.12), 1.0)
    o = [cylinder("trunk", 0.22, 3.2, (0, 0, 1.6), bark, vertices=8, taper_top=0.55)]
    for i in range(3):
        a = i * 2.1 + rnd.uniform(-0.4, 0.4)
        br = cylinder("branch", 0.07, 1.8, (math.cos(a) * 0.7, math.sin(a) * 0.7, 3.4 + i * 0.3),
                      bark, rot=(math.sin(a) * 0.8, -math.cos(a) * 0.8, 0), vertices=6, taper_top=0.3)
        o.append(br)
    if not dead:
        leaf = mat("foliage", (0.16, 0.3, 0.1), 1.0)
        leaf2 = mat("foliage2", (0.2, 0.36, 0.12), 1.0)
        for i, (x, y, z, r) in enumerate([(0, 0, 4.6, 1.7), (1.1, 0.5, 4.0, 1.15), (-1.1, -0.4, 4.1, 1.2),
                                          (0.3, -1.0, 4.3, 1.0), (-0.4, 1.0, 4.2, 1.05), (0, 0, 5.6, 1.2)]):
            o.append(ico("canopy", r, (x, y, z), leaf if i % 2 else leaf2, noise=0.3, seed=seed + i))
    return join(o, "dead_tree" if dead else "tree")


def build_pine(seed=4):
    reset_scene()
    bark = mat("bark", (0.24, 0.17, 0.11), 1.0)
    leaf = mat("pine_green", (0.1, 0.22, 0.1), 1.0)
    o = [cylinder("trunk", 0.16, 2.4, (0, 0, 1.2), bark, vertices=8, taper_top=0.6)]
    for i, (z, r) in enumerate([(2.2, 1.5), (3.4, 1.15), (4.6, 0.8), (5.7, 0.45)]):
        o.append(cone("pine_tier", r, 0.05, 1.7, (0, 0, z), leaf, vertices=10))
    return join(o, "pine")


def build_rock(seed=6):
    reset_scene()
    r = ico("rock", 0.8, (0, 0, 0.35), mat("rock", (0.36, 0.35, 0.33), 1.0), noise=0.42, seed=seed, scale=(1.2, 0.9, 0.75))
    return r


def build_fence():
    reset_scene()
    wood = mat("fence_wood", (0.4, 0.3, 0.18), 0.95)
    o = []
    for i, x in enumerate((-1.4, 0.0, 1.4)):
        o.append(box("post", (0.09, 0.09, 1.25), (x, 0, 0.62), wood, bevel=0.01))
    for z, tilt in ((0.95, 0.0), (0.55, 0.02), (0.18, -0.015)):
        o.append(box("rail", (3.0, 0.04, 0.16), (0, 0.02, z), wood, bevel=0.008, rot=(0, tilt, 0)))
    return join(o, "fence")


def build_barricade():
    reset_scene()
    wood = mat("plank", (0.42, 0.31, 0.17), 0.95)
    metal = mat("scrap", (0.35, 0.34, 0.35), 0.7, 0.5)
    o = []
    rnd = random.Random(9)
    for i in range(5):
        o.append(box("plank", (2.6, 0.05, 0.22), (rnd.uniform(-0.1, 0.1), rnd.uniform(-0.08, 0.08), 0.25 + i * 0.24),
                     wood, bevel=0.01, rot=(0, rnd.uniform(-0.04, 0.04), rnd.uniform(-0.03, 0.03))))
    for s in (-1, 1):
        o.append(box("leg", (0.08, 0.5, 0.08), (s * 1.15, 0.25, 0.35), metal, rot=(0.5, 0, 0)))
    o.append(box("tire_stack", (0.001, 0.001, 0.001), (99, 99, 99), metal))  # placeholder removed below
    import bpy
    bpy.data.objects.remove(o[-1], do_unlink=True)
    o.pop()
    for i in range(3):
        t = cylinder("tire", 0.3, 0.16, (-1.55, 0, 0.1 + i * 0.17), mat("tire_old", (0.06, 0.06, 0.06), 0.95),
                     vertices=14)
        o.append(t)
    return join(o, "barricade")


def build_lamp():
    reset_scene()
    pole_m = mat("lamp_pole", (0.18, 0.19, 0.2), 0.5, 0.7)
    o = [cylinder("pole", 0.06, 4.6, (0, 0, 2.3), pole_m, vertices=10),
         cylinder("arm", 0.04, 1.3, (0, -0.55, 4.72), pole_m, rot=(math.pi / 2.25, 0, 0), vertices=8),
         box("head", (0.16, 0.5, 0.09), (0, -1.05, 4.85), pole_m, bevel=0.02),
         box("bulb", (0.11, 0.38, 0.03), (0, -1.05, 4.8), mat("lamp_glass", (0.95, 0.88, 0.65), 0.3), bevel=0.01),
         cylinder("base", 0.14, 0.5, (0, 0, 0.25), pole_m, vertices=10)]
    return join(o, "street_lamp")


def build_container():
    reset_scene()
    cm = mat("container", (0.55, 0.28, 0.14), 0.75, 0.35)
    o = [box("body", (6.0, 2.4, 2.6), (0, 0, 1.3), cm, bevel=0.02)]
    rib = mat("container_rib", (0.48, 0.24, 0.12), 0.8, 0.3)
    for i in range(11):
        x = -2.75 + i * 0.55
        o.append(box("rib", (0.12, 0.04, 2.5), (x, -1.21, 1.3), rib))
        o.append(box("rib2", (0.12, 0.04, 2.5), (x, 1.21, 1.3), rib))
    o.append(box("door_line", (0.03, 2.3, 2.4), (3.01, 0, 1.3), rib))
    for i in range(4):
        o.append(box("lock_rod", (0.04, 0.05, 2.3), (3.03, -0.85 + i * 0.57, 1.3), mat("lock", (0.3, 0.3, 0.32), 0.4, 0.8)))
    return join(o, "container")


def build_crate():
    reset_scene()
    wood = mat("crate_wood", (0.48, 0.36, 0.2), 0.9)
    o = [box("crate", (0.62, 0.62, 0.62), (0, 0, 0.31), wood, bevel=0.015)]
    frame = mat("crate_frame", (0.38, 0.28, 0.15), 0.9)
    for sx in (-1, 1):
        for sy in (-1, 1):
            o.append(box("edge", (0.07, 0.07, 0.64), (sx * 0.285, sy * 0.285, 0.31), frame))
    for z in (0.04, 0.58):
        o.append(box("band", (0.65, 0.65, 0.07), (0, 0, z), frame))
    return join(o, "crate")


def build_barrel():
    reset_scene()
    bm = mat("barrel_rust", (0.5, 0.3, 0.14), 0.8, 0.3)
    o = [cylinder("barrel", 0.29, 0.88, (0, 0, 0.44), bm, vertices=16)]
    for z in (0.18, 0.44, 0.7):
        o.append(cylinder("ring", 0.3, 0.035, (0, 0, z), mat("barrel_ring", (0.4, 0.24, 0.11), 0.8, 0.3), vertices=16))
    return join(o, "barrel")


def build_sandbags():
    reset_scene()
    sm = mat("sandbag", (0.42, 0.4, 0.3), 1.0)
    o = []
    rnd = random.Random(3)
    for row in range(3):
        for i in range(5 - (row == 2)):
            x = -1.0 + i * 0.52 + (0.26 if row % 2 else 0) + rnd.uniform(-0.03, 0.03)
            bag = sphere("bag", 0.26, (x, rnd.uniform(-0.03, 0.03), 0.15 + row * 0.24), (1.15, 0.62, 0.55), sm, segments=10)
            o.append(bag)
    return join(o, "sandbag_wall")


def build_tent():
    reset_scene()
    tm = mat("tent_camo", (0.28, 0.33, 0.2), 0.95)
    o = []
    for s in (-1, 1):
        o.append(box("tent_side", (3.2, 1.7, 0.05), (0, s * 0.62, 0.85), tm, bevel=0.01, rot=(s * -0.9, 0, 0)))
    back = box("tent_back", (0.06, 1.6, 1.5), (1.58, 0, 0.7), tm, bevel=0.01)
    for v in back.data.vertices:
        if v.co.z > 1.4:
            v.co.y *= 0.05
    o.append(back)
    o.append(box("tent_door", (0.04, 0.7, 1.1), (-1.58, 0, 0.55), mat("tent_dark", (0.1, 0.12, 0.07), 1.0)))
    o.append(cylinder("ridge", 0.03, 3.3, (0, 0, 1.62), tm, rot=(0, math.pi / 2, 0), vertices=6))
    return join(o, "military_tent")


# ---------------------------------------------------------------- pickups / small props

def build_medkit():
    reset_scene()
    o = [box("case", (0.34, 0.24, 0.12), (0, 0, 0.06), mat("medkit_white", (0.88, 0.88, 0.86), 0.6), bevel=0.015),
         box("lid", (0.35, 0.25, 0.03), (0, 0, 0.13), mat("medkit_white", (0.88, 0.88, 0.86), 0.6), bevel=0.01)]
    red = mat("med_red", (0.78, 0.08, 0.08), 0.55)
    o.append(box("cross_v", (0.045, 0.14, 0.012), (0, 0, 0.148), red))
    o.append(box("cross_h", (0.14, 0.045, 0.012), (0, 0, 0.148), red))
    o.append(box("handle", (0.14, 0.03, 0.03), (0, 0, 0.165), mat("medkit_handle", (0.3, 0.3, 0.3), 0.6), bevel=0.008))
    return join(o, "medkit")


def build_ammo_box():
    reset_scene()
    gm = mat("ammo_green", (0.24, 0.3, 0.16), 0.6, 0.4)
    o = [box("box", (0.3, 0.17, 0.16), (0, 0, 0.08), gm, bevel=0.01),
         box("lid", (0.31, 0.18, 0.035), (0, 0, 0.17), gm, bevel=0.008),
         box("latch", (0.06, 0.02, 0.05), (0, -0.095, 0.13), mat("latch", (0.4, 0.4, 0.42), 0.4, 0.8))]
    brass = mat("brass", (0.72, 0.55, 0.2), 0.3, 0.9)
    for i in range(3):
        o.append(cylinder("round", 0.008, 0.05, (-0.05 + i * 0.05, -0.14, 0.008), brass, rot=(math.pi / 2, 0, 0.3), vertices=8))
    return join(o, "ammo_box")


def build_food_can():
    reset_scene()
    body = cylinder("can", 0.04, 0.11, (0, 0, 0.055), mat("can_label", (0.55, 0.4, 0.2), 0.5, 0.55), vertices=14)
    top = cylinder("can_top", 0.041, 0.008, (0, 0, 0.112), mat("can_metal", (0.65, 0.66, 0.68), 0.3, 0.9), vertices=14)
    band = cylinder("can_band", 0.041, 0.03, (0, 0, 0.06), mat("can_stripe", (0.62, 0.18, 0.12), 0.5, 0.4), vertices=14)
    return join([body, top, band], "food_can")


def build_water_bottle():
    reset_scene()
    pm = mat("bottle_plastic", (0.45, 0.62, 0.75), 0.25, 0.0)
    o = [cylinder("bottle", 0.035, 0.16, (0, 0, 0.08), pm, vertices=12),
         cone("bottle_neck", 0.034, 0.011, 0.05, (0, 0, 0.185), pm, vertices=12),
         cylinder("cap", 0.012, 0.022, (0, 0, 0.22), mat("bottle_cap", (0.75, 0.2, 0.15), 0.5), vertices=10)]
    return join(o, "water_bottle")


def build_bandage():
    reset_scene()
    o = [cylinder("roll", 0.04, 0.055, (0, 0, 0.028), mat("bandage_white", (0.85, 0.84, 0.8), 0.9), rot=(math.pi / 2, 0, 0), vertices=12),
         box("tail", (0.05, 0.14, 0.004), (0, 0.09, 0.012), mat("bandage_white", (0.85, 0.84, 0.8), 0.9), rot=(0.25, 0, 0))]
    return join(o, "bandage")


def build_fuel_can():
    reset_scene()
    rm = mat("fuel_red", (0.6, 0.16, 0.1), 0.65)
    o = [box("body", (0.32, 0.16, 0.42), (0, 0, 0.21), rm, bevel=0.02),
         box("handle", (0.2, 0.05, 0.05), (-0.02, 0, 0.45), rm, bevel=0.012),
         cylinder("neck", 0.035, 0.09, (0.12, 0, 0.46), rm, rot=(0, 0.5, 0), vertices=10),
         cylinder("cap", 0.028, 0.03, (0.145, 0, 0.5), mat("fuel_cap", (0.85, 0.7, 0.2), 0.5), vertices=10),
         box("cross1", (0.2, 0.012, 0.05), (0, -0.083, 0.22), mat("fuel_dark", (0.45, 0.12, 0.08), 0.7), rot=(0, 0, 0.7)),
         box("cross2", (0.2, 0.012, 0.05), (0, -0.083, 0.22), mat("fuel_dark", (0.45, 0.12, 0.08), 0.7), rot=(0, 0, -0.7))]
    return join(o, "fuel_canister")


def build_backpack_prop():
    reset_scene()
    bm = mat("bp_fabric", (0.3, 0.26, 0.16), 0.95)
    o = [box("pack", (0.34, 0.2, 0.48), (0, 0, 0.24), bm, bevel=0.05),
         box("pocket", (0.24, 0.08, 0.22), (0, -0.13, 0.18), bm, bevel=0.03),
         cylinder("roll", 0.06, 0.32, (0, 0, 0.52), mat("bp_roll", (0.4, 0.42, 0.34), 0.95), rot=(0, math.pi / 2, 0), vertices=10)]
    for s in (-1, 1):
        o.append(box("strap", (0.05, 0.02, 0.4), (s * 0.1, 0.11, 0.24), mat("bp_strap", (0.2, 0.17, 0.11), 0.95)))
    return join(o, "backpack")


PROP_BUILDERS = {
    "medkit": build_medkit, "ammo_box": build_ammo_box, "food_can": build_food_can,
    "water_bottle": build_water_bottle, "bandage": build_bandage,
    "fuel_canister": build_fuel_can, "backpack": build_backpack_prop,
}

ENV_BUILDERS = {
    "tree": lambda: build_tree(False, 2), "tree2": lambda: build_tree(False, 11),
    "dead_tree": lambda: build_tree(True, 5), "pine": lambda: build_pine(4),
    "pine2": lambda: build_pine(8), "rock": lambda: build_rock(6), "rock2": lambda: build_rock(17),
    "fence": build_fence, "barricade": build_barricade, "street_lamp": build_lamp,
    "container": build_container, "crate": build_crate, "barrel": build_barrel,
    "sandbag_wall": build_sandbags, "military_tent": build_tent,
    "gas_station": build_gas_station,
}
