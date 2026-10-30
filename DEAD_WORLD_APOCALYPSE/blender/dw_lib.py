"""
DEAD WORLD: APOCALYPSE - Blender procedural modeling library.
Run inside Blender (bpy). Builds shaped low-poly models (no plain-cube placeholders):
humanoids with armatures + authored animation actions, weapons, vehicles,
buildings and environment props. Exports:
  * part-kit .obj files (origin at joint pivot) for the runtime hierarchy animator
  * fully rigged .glb (armature + all actions) for DCC / other engines
  * skeleton.json + animations.json (semantic keyframe tables, engine space)
"""
import bpy, math, os, json, random
from mathutils import Vector

# ---------------------------------------------------------------- scene utils

def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.unit_settings.system = 'METRIC'
    scene.render.fps = 24
    return scene


def _srgb_to_linear(c):
    return tuple((v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4) for v in c)


def mat(name, color, roughness=0.85, metallic=0.0):
    m = bpy.data.materials.get(name)
    if m is None:
        m = bpy.data.materials.new(name)
        m.use_nodes = True
        bsdf = m.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = (*_srgb_to_linear(tuple(color)), 1.0)
        bsdf.inputs["Roughness"].default_value = roughness
        bsdf.inputs["Metallic"].default_value = metallic
    return m


def paint(obj, rgb):
    """Fill a vertex-color layer (OBJ export, runtime vertex-color shading)."""
    mesh = obj.data
    if not mesh.color_attributes:
        mesh.color_attributes.new(name="Color", type='FLOAT_COLOR', domain='POINT')
    layer = mesh.color_attributes.active_color
    col = (*rgb, 1.0)
    for i in range(len(layer.data)):
        layer.data[i].color = col
    return obj


def _finish(obj, material=None, smooth=False):
    if material:
        obj.data.materials.append(material)
        paint(obj, material.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value[:3])
    if smooth:
        for p in obj.data.polygons:
            p.use_smooth = True
    return obj


def box(name, size, loc=(0, 0, 0), material=None, bevel=0.0, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.dimensions = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel > 0:
        mod = o.modifiers.new("bev", 'BEVEL')
        mod.width = bevel
        mod.segments = 2
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return _finish(o, material)


def shaped_box(name, size, loc, material, profile, z_levels=5, bevel=0.01):
    """Box whose XY cross-section scales along Z by profile(z_norm)->(sx, sy, xoff, yoff).
    Gives organic tapers (torsos, blades, stocks) instead of raw cubes."""
    bpy.ops.mesh.primitive_cube_add(size=1, location=(0, 0, 0))
    o = bpy.context.active_object
    o.name = name
    o.dimensions = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    # add loop cuts along Z via subdivision on Z only: use simple subdiv then keep levels
    mod = o.modifiers.new("sub", 'SUBSURF')
    mod.subdivision_type = 'SIMPLE'
    mod.levels = 2
    bpy.ops.object.modifier_apply(modifier=mod.name)
    zz = [v.co.z for v in o.data.vertices]
    zmin, zmax = min(zz), max(zz)
    for v in o.data.vertices:
        zn = (v.co.z - zmin) / max(1e-6, (zmax - zmin))
        sx, sy, xo, yo = profile(zn)
        v.co.x = v.co.x * sx + xo
        v.co.y = v.co.y * sy + yo
    o.location = loc
    if bevel > 0:
        mod = o.modifiers.new("bev", 'BEVEL')
        mod.width = bevel
        mod.segments = 1
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return _finish(o, material)


def cylinder(name, radius, depth, loc=(0, 0, 0), material=None, vertices=12,
             rot=(0, 0, 0), taper_top=1.0, smooth=True):
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=depth, vertices=vertices,
                                        location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    if taper_top != 1.0:
        zz = [v.co.z for v in o.data.vertices]
        zmax = max(zz)
        for v in o.data.vertices:
            if v.co.z > zmax - 1e-4:
                v.co.x *= taper_top
                v.co.y *= taper_top
    return _finish(o, material, smooth=smooth)


def sphere(name, radius, loc=(0, 0, 0), scale=(1, 1, 1), material=None, smooth=True, segments=16):
    bpy.ops.mesh.primitive_uv_sphere_add(radius=radius, segments=segments, ring_count=max(8, segments // 2),
                                         location=loc)
    o = bpy.context.active_object
    o.name = name
    o.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return _finish(o, material, smooth=smooth)


def cone(name, r1, r2, depth, loc=(0, 0, 0), material=None, rot=(0, 0, 0), vertices=10):
    bpy.ops.mesh.primitive_cone_add(radius1=r1, radius2=r2, depth=depth, vertices=vertices,
                                    location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return _finish(o, material, smooth=True)


def ico(name, radius, loc, material, noise=0.25, seed=1, scale=(1, 1, 1)):
    rnd = random.Random(seed)
    bpy.ops.mesh.primitive_ico_sphere_add(radius=radius, subdivisions=2, location=loc)
    o = bpy.context.active_object
    o.name = name
    for v in o.data.vertices:
        d = 1.0 + rnd.uniform(-noise, noise)
        v.co = Vector((v.co.x * d, v.co.y * d, v.co.z * d))
    o.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return _finish(o, material, smooth=True)


def join(objs, name):
    objs = [o for o in objs if o is not None]
    if len(objs) == 1:
        objs[0].name = name
        return objs[0]
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    o = bpy.context.active_object
    o.name = name
    return o


def move_geom(obj, delta):
    """Translate geometry inside the mesh (origin stays at pivot)."""
    d = Vector(delta)
    for v in obj.data.vertices:
        v.co += d
    return obj


def stats(obj):
    me = obj.data
    return {"verts": len(me.vertices), "polys": len(me.polygons)}


# ---------------------------------------------------------------- exporters

_OBJ_KW = None


def bake_transform(obj):
    """Bake location/rotation/scale into the mesh: afterwards the geometry is in
    true pivot-local coordinates and the object transform is identity."""
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    obj.select_set(False)
    return obj


def export_obj(obj, path):
    """Export a single object. The full object transform is baked into the
    geometry first, so the file is always pivot-local (buildings sit on the
    ground plane, limbs hang from their joint, weapon grips are at origin)."""
    global _OBJ_KW
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bake_transform(obj)
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    if _OBJ_KW is None:
        props = {p.identifier for p in bpy.ops.wm.obj_export.get_rna_type().properties}
        want = {"export_selected_objects": True, "axis_forward": '-Z', "axis_up": 'Y',
                "export_materials": False, "export_normals": True, "export_uv": True,
                "export_colors": True, "apply_modifiers": True, "path_mode": 'COPY'}
        _OBJ_KW = {k: v for k, v in want.items() if k in props}
    bpy.ops.wm.obj_export(filepath=path, **_OBJ_KW)


def export_glb(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    props = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    kw = {}
    want = {"export_format": 'GLB', "export_animations": True, "export_nla_strips": True,
            "export_yup": True, "export_apply": True, "export_skins": True,
            "export_all_influences": False, "export_morph": False,
            "export_animation_mode": 'NLA_TRACKS'}
    for k, v in want.items():
        if k in props:
            kw[k] = v
    bpy.ops.export_scene.gltf(filepath=path, **kw)


def save_blend(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=path, check_existing=False)


# ---------------------------------------------------------------- humanoid skeleton
# Blender space: Z up, character faces -Y. Joint positions (meters), A-pose.

BASE_JOINTS = {
    "hips":      (0.0, 0.0, 0.99),
    "spine":     (0.0, 0.0, 1.13),
    "chest":     (0.0, 0.0, 1.34),
    "neck":      (0.0, 0.0, 1.51),
    "head":      (0.0, 0.0, 1.58),
    "head_top":  (0.0, 0.0, 1.83),
    "shoulder.L": (0.20, 0.0, 1.47),
    "elbow.L":    (0.235, 0.0, 1.21),
    "wrist.L":    (0.25, 0.0, 0.97),
    "hand_tip.L": (0.255, 0.0, 0.80),
    "shoulder.R": (-0.20, 0.0, 1.47),
    "elbow.R":    (-0.235, 0.0, 1.21),
    "wrist.R":    (-0.25, 0.0, 0.97),
    "hand_tip.R": (-0.255, 0.0, 0.80),
    "hip.L":      (0.10, 0.0, 0.96),
    "knee.L":     (0.105, -0.01, 0.56),
    "ankle.L":    (0.11, 0.0, 0.12),
    "toe.L":      (0.11, -0.16, 0.035),
    "hip.R":      (-0.10, 0.0, 0.96),
    "knee.R":     (-0.105, -0.01, 0.56),
    "ankle.R":    (-0.11, 0.0, 0.12),
    "toe.R":      (-0.11, -0.16, 0.035),
}

BONES = [  # (name, head_joint, tail_joint, parent)
    ("hips", "hips", "spine", None),
    ("spine", "spine", "chest", "hips"),
    ("chest", "chest", "neck", "spine"),
    ("neck", "neck", "head", "chest"),
    ("head", "head", "head_top", "neck"),
    ("upperarm.L", "shoulder.L", "elbow.L", "chest"),
    ("forearm.L", "elbow.L", "wrist.L", "upperarm.L"),
    ("hand.L", "wrist.L", "hand_tip.L", "forearm.L"),
    ("upperarm.R", "shoulder.R", "elbow.R", "chest"),
    ("forearm.R", "elbow.R", "wrist.R", "upperarm.R"),
    ("hand.R", "wrist.R", "hand_tip.R", "forearm.R"),
    ("thigh.L", "hip.L", "knee.L", "hips"),
    ("shin.L", "knee.L", "ankle.L", "thigh.L"),
    ("foot.L", "ankle.L", "toe.L", "shin.L"),
    ("thigh.R", "hip.R", "knee.R", "hips"),
    ("shin.R", "knee.R", "ankle.R", "thigh.R"),
    ("foot.R", "ankle.R", "toe.R", "shin.R"),
]

# Runtime part-kit: part name -> (bone, parent part). Geometry hangs below the joint.
PARTS = [
    ("pelvis", "hips", None),
    ("torso", "spine", "pelvis"),
    ("head", "head", "torso"),
    ("upper_arm.L", "upperarm.L", "torso"),
    ("forearm.L", "forearm.L", "upper_arm.L"),
    ("hand.L", "hand.L", "forearm.L"),
    ("upper_arm.R", "upperarm.R", "torso"),
    ("forearm.R", "forearm.R", "upper_arm.R"),
    ("hand.R", "hand.R", "forearm.R"),
    ("thigh.L", "thigh.L", "pelvis"),
    ("shin.L", "shin.L", "thigh.L"),
    ("foot.L", "foot.L", "shin.L"),
    ("thigh.R", "thigh.R", "pelvis"),
    ("shin.R", "shin.R", "thigh.R"),
    ("foot.R", "foot.R", "shin.R"),
]


def to_engine_coords(p):
    """Blender (x, y, z-up) -> engine Y-up coords matching OBJ export (-Z forward, Y up)."""
    return [round(p[0], 4), round(p[2], 4), round(-p[1], 4)]


def skeleton_json(joints, scale, part_colors):
    """Engine-space hierarchy: each part's rest offset relative to its parent part pivot."""
    bone_head = {b[0]: joints[b[1]] for b in BONES}
    bone_parent = {b[0]: b[3] for b in BONES}
    part_bone = {p[0]: p[1] for p in PARTS}
    out = {"scale": scale, "parts": {}}
    # torso pivot = spine joint but torso geometry starts slightly below (see builder)
    for part, bone, parent_part in PARTS:
        head = Vector(bone_head[bone])
        if parent_part is None:
            local = head
            parent_name = "ROOT"
        else:
            parent_bone = part_bone[parent_part]
            # walk up bone chain to parent_bone accumulating rest offsets
            local = head - Vector(bone_head[parent_bone])
            parent_name = parent_part
        out["parts"][part] = {
            "parent": parent_name,
            "bone": bone,
            "rest_offset": to_engine_coords(tuple(local)),
            "color": part_colors.get(part, [0.5, 0.5, 0.5]),
        }
    out["height"] = round(joints["head_top"][2] * 1.0, 3)
    return out


# ---------------------------------------------------------------- animation tables
# Semantic joint angles in DEGREES: (pitch, yaw, roll).
# pitch>0 = swing/lean forward, yaw>0 = turn left, roll>0 = tilt/splay left side down.
# Joints: hips, spine, head, upper_arm.L/R, forearm.L/R, thigh.L/R, shin.L/R, foot.L/R
# Each animation: {fps, loop, keys: [(frame, {joint: (p,y,r)})]}  (missing joint = inherit 0 / prev)

def K(frame, **j):
    return (frame, j)

ANIMATIONS = {
    "idle": {"fps": 24, "loop": True, "keys": [
        K(0,  spine=(4, 0, 1), head=(6, -6, 3), **{"upper_arm.L": (10, 0, 6), "upper_arm.R": (12, 0, -7),
          "forearm.L": (14, 0, 0), "forearm.R": (18, 0, 0), "hips": (0, 0, 0)}),
        K(24, spine=(6, 2, -1), head=(9, 7, -3), **{"upper_arm.L": (13, 0, 8), "upper_arm.R": (9, 0, -5),
          "forearm.L": (18, 0, 0), "forearm.R": (13, 0, 0)}),
        K(48, spine=(4, 0, 1), head=(6, -6, 3), **{"upper_arm.L": (10, 0, 6), "upper_arm.R": (12, 0, -7),
          "forearm.L": (14, 0, 0), "forearm.R": (18, 0, 0)}),
    ]},
    "walk": {"fps": 24, "loop": True, "keys": [  # classic zombie: arms reach forward
        K(0,  spine=(10, -4, 2), head=(6, 3, 4), hips=(0, -5, 3),
          **{"thigh.L": (34, 0, 0), "shin.L": (8, 0, 0), "foot.L": (-12, 0, 0),
             "thigh.R": (-26, 0, 0), "shin.R": (22, 0, 0), "foot.R": (18, 0, 0),
             "upper_arm.L": (72, 0, 8), "forearm.L": (22, 0, 0),
             "upper_arm.R": (66, 0, -8), "forearm.R": (30, 0, 0)}),
        K(8,  spine=(12, 0, -2), head=(8, -2, -3), hips=(0, 0, -3),
          **{"thigh.L": (2, 0, 0), "shin.L": (14, 0, 0), "foot.L": (4, 0, 0),
             "thigh.R": (-4, 0, 0), "shin.R": (34, 0, 0), "foot.R": (-6, 0, 0),
             "upper_arm.L": (66, 0, 7), "forearm.L": (30, 0, 0),
             "upper_arm.R": (74, 0, -7), "forearm.R": (20, 0, 0)}),
        K(16, spine=(10, 4, 2), head=(6, -3, 4), hips=(0, 5, 3),
          **{"thigh.L": (-26, 0, 0), "shin.L": (22, 0, 0), "foot.L": (18, 0, 0),
             "thigh.R": (34, 0, 0), "shin.R": (8, 0, 0), "foot.R": (-12, 0, 0),
             "upper_arm.L": (74, 0, 9), "forearm.L": (20, 0, 0),
             "upper_arm.R": (68, 0, -9), "forearm.R": (26, 0, 0)}),
        K(24, spine=(12, 0, -2), head=(8, 2, -3), hips=(0, 0, -3),
          **{"thigh.L": (-4, 0, 0), "shin.L": (34, 0, 0), "foot.L": (-6, 0, 0),
             "thigh.R": (2, 0, 0), "shin.R": (14, 0, 0), "foot.R": (4, 0, 0),
             "upper_arm.L": (68, 0, 6), "forearm.L": (26, 0, 0),
             "upper_arm.R": (70, 0, -6), "forearm.R": (24, 0, 0)}),
        K(32, spine=(10, -4, 2), head=(6, 3, 4), hips=(0, -5, 3),
          **{"thigh.L": (34, 0, 0), "shin.L": (8, 0, 0), "foot.L": (-12, 0, 0),
             "thigh.R": (-26, 0, 0), "shin.R": (22, 0, 0), "foot.R": (18, 0, 0),
             "upper_arm.L": (72, 0, 8), "forearm.L": (22, 0, 0),
             "upper_arm.R": (66, 0, -8), "forearm.R": (30, 0, 0)}),
    ]},
    "run": {"fps": 24, "loop": True, "keys": [
        K(0,  spine=(22, -8, 3), head=(2, 4, 0), hips=(4, -10, 4),
          **{"thigh.L": (58, 0, 0), "shin.L": (26, 0, 0), "foot.L": (-18, 0, 0),
             "thigh.R": (-44, 0, 0), "shin.R": (52, 0, 0), "foot.R": (30, 0, 0),
             "upper_arm.L": (50, 0, 10), "forearm.L": (58, 0, 0),
             "upper_arm.R": (96, 0, -10), "forearm.R": (34, 0, 0)}),
        K(6,  spine=(24, 0, -3), head=(4, 0, -2), hips=(4, 0, -4),
          **{"thigh.L": (0, 0, 0), "shin.L": (38, 0, 0), "foot.L": (8, 0, 0),
             "thigh.R": (-6, 0, 0), "shin.R": (66, 0, 0), "foot.R": (-8, 0, 0),
             "upper_arm.L": (74, 0, 8), "forearm.L": (44, 0, 0),
             "upper_arm.R": (72, 0, -8), "forearm.R": (48, 0, 0)}),
        K(12, spine=(22, 8, 3), head=(2, -4, 0), hips=(4, 10, 4),
          **{"thigh.L": (-44, 0, 0), "shin.L": (52, 0, 0), "foot.L": (30, 0, 0),
             "thigh.R": (58, 0, 0), "shin.R": (26, 0, 0), "foot.R": (-18, 0, 0),
             "upper_arm.L": (96, 0, 10), "forearm.L": (34, 0, 0),
             "upper_arm.R": (50, 0, -10), "forearm.R": (58, 0, 0)}),
        K(18, spine=(24, 0, -3), head=(4, 0, -2), hips=(4, 0, -4),
          **{"thigh.L": (-6, 0, 0), "shin.L": (66, 0, 0), "foot.L": (-8, 0, 0),
             "thigh.R": (0, 0, 0), "shin.R": (38, 0, 0), "foot.R": (8, 0, 0),
             "upper_arm.L": (72, 0, 8), "forearm.L": (48, 0, 0),
             "upper_arm.R": (74, 0, -8), "forearm.R": (44, 0, 0)}),
        K(24, spine=(22, -8, 3), head=(2, 4, 0), hips=(4, -10, 4),
          **{"thigh.L": (58, 0, 0), "shin.L": (26, 0, 0), "foot.L": (-18, 0, 0),
             "thigh.R": (-44, 0, 0), "shin.R": (52, 0, 0), "foot.R": (30, 0, 0),
             "upper_arm.L": (50, 0, 10), "forearm.L": (58, 0, 0),
             "upper_arm.R": (96, 0, -10), "forearm.R": (34, 0, 0)}),
    ]},
    "crawl": {"fps": 24, "loop": True, "keys": [  # crawler drags itself with arms (root lowered by engine)
        K(0,  spine=(18, -10, 4), head=(-14, 6, 0),
          **{"upper_arm.L": (118, 0, 6), "forearm.L": (20, 0, 0),
             "upper_arm.R": (30, 0, -6), "forearm.R": (70, 0, 0),
             "thigh.L": (16, 0, 4), "thigh.R": (-8, 0, -4)}),
        K(12, spine=(22, 8, -4), head=(-12, -6, 0),
          **{"upper_arm.L": (34, 0, 8), "forearm.L": (66, 0, 0),
             "upper_arm.R": (114, 0, -8), "forearm.R": (24, 0, 0),
             "thigh.L": (-8, 0, 4), "thigh.R": (14, 0, -4)}),
        K(24, spine=(18, -10, 4), head=(-14, 6, 0),
          **{"upper_arm.L": (118, 0, 6), "forearm.L": (20, 0, 0),
             "upper_arm.R": (30, 0, -6), "forearm.R": (70, 0, 0),
             "thigh.L": (16, 0, 4), "thigh.R": (-8, 0, -4)}),
    ]},
    "attack": {"fps": 24, "loop": False, "keys": [
        K(0,  spine=(10, 0, 0), head=(6, 0, 0),
          **{"upper_arm.L": (70, 0, 6), "upper_arm.R": (70, 0, -6), "forearm.L": (24, 0, 0), "forearm.R": (24, 0, 0)}),
        K(5,  spine=(2, 0, 0), head=(-8, 0, 0),
          **{"upper_arm.L": (102, 0, 10), "upper_arm.R": (98, 0, -10), "forearm.L": (40, 0, 0), "forearm.R": (36, 0, 0)}),
        K(9,  spine=(26, 0, 0), head=(22, 0, 0),
          **{"upper_arm.L": (88, 0, -4), "upper_arm.R": (88, 0, 4), "forearm.L": (6, 0, 0), "forearm.R": (6, 0, 0),
             "thigh.L": (12, 0, 0), "thigh.R": (-8, 0, 0)}),
        K(16, spine=(12, 0, 0), head=(8, 0, 0),
          **{"upper_arm.L": (72, 0, 6), "upper_arm.R": (72, 0, -6), "forearm.L": (22, 0, 0), "forearm.R": (22, 0, 0)}),
    ]},
    "hit": {"fps": 24, "loop": False, "keys": [
        K(0, spine=(8, 0, 0), head=(6, 0, 0), **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6)}),
        K(3, spine=(-10, 6, -6), head=(-16, -10, -8),
          **{"upper_arm.L": (44, 0, 20), "upper_arm.R": (30, 0, -16), "forearm.L": (34, 0, 0), "forearm.R": (40, 0, 0)}),
        K(10, spine=(6, 0, 0), head=(4, 0, 0), **{"upper_arm.L": (64, 0, 6), "upper_arm.R": (64, 0, -6)}),
    ]},
    "stagger": {"fps": 24, "loop": False, "keys": [
        K(0, spine=(8, 0, 0), head=(6, 0, 0)),
        K(4, spine=(-14, -8, 8), head=(-20, 8, 10), hips=(0, 0, 6),
          **{"upper_arm.L": (30, 0, 34), "upper_arm.R": (24, 0, -30), "thigh.L": (-14, 0, 0), "thigh.R": (18, 0, 0),
             "shin.L": (10, 0, 0), "shin.R": (16, 0, 0)}),
        K(10, spine=(-6, 6, -6), head=(-8, -6, -8), hips=(0, 0, -5),
          **{"upper_arm.L": (48, 0, 22), "upper_arm.R": (40, 0, -24), "thigh.L": (10, 0, 0), "thigh.R": (-8, 0, 0)}),
        K(16, spine=(8, 0, 0), head=(6, 0, 0), hips=(0, 0, 0),
          **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6), "thigh.L": (0, 0, 0), "thigh.R": (0, 0, 0)}),
    ]},
    "death": {"fps": 24, "loop": False, "keys": [  # joints go limp; engine topples the root
        K(0, spine=(8, 0, 0), head=(6, 0, 0), **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6)}),
        K(6, spine=(16, 4, 4), head=(18, 6, 6),
          **{"upper_arm.L": (40, 0, 14), "upper_arm.R": (34, 0, -18), "forearm.L": (30, 0, 0), "forearm.R": (22, 0, 0),
             "thigh.L": (10, 0, 0), "thigh.R": (-6, 0, 0), "shin.L": (18, 0, 0), "shin.R": (8, 0, 0)}),
        K(14, spine=(24, -6, -8), head=(10, 14, 18),
          **{"upper_arm.L": (16, 0, 26), "upper_arm.R": (22, 0, -30), "forearm.L": (42, 0, 0), "forearm.R": (12, 0, 0),
             "thigh.L": (22, 0, 6), "thigh.R": (6, 0, -6), "shin.L": (30, 0, 0), "shin.R": (20, 0, 0)}),
        K(20, spine=(24, -6, -8), head=(10, 14, 18),
          **{"upper_arm.L": (16, 0, 26), "upper_arm.R": (22, 0, -30), "forearm.L": (42, 0, 0), "forearm.R": (12, 0, 0),
             "thigh.L": (22, 0, 6), "thigh.R": (6, 0, -6), "shin.L": (30, 0, 0), "shin.R": (20, 0, 0)}),
    ]},
    "headshot_death": {"fps": 24, "loop": False, "keys": [
        K(0, spine=(8, 0, 0), head=(6, 0, 0), **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6)}),
        K(3, spine=(-6, 0, 0), head=(-38, 0, 0),
          **{"upper_arm.L": (50, 0, 18), "upper_arm.R": (50, 0, -18)}),
        K(8, spine=(-2, 2, 2), head=(-20, 8, 12),
          **{"upper_arm.L": (20, 0, 24), "upper_arm.R": (16, 0, -22), "forearm.L": (18, 0, 0), "forearm.R": (26, 0, 0),
             "thigh.L": (6, 0, 0), "thigh.R": (12, 0, 0), "shin.L": (14, 0, 0), "shin.R": (24, 0, 0)}),
        K(14, spine=(-2, 2, 2), head=(-20, 8, 12),
          **{"upper_arm.L": (20, 0, 24), "upper_arm.R": (16, 0, -22), "forearm.L": (18, 0, 0), "forearm.R": (26, 0, 0),
             "thigh.L": (6, 0, 0), "thigh.R": (12, 0, 0), "shin.L": (14, 0, 0), "shin.R": (24, 0, 0)}),
    ]},
    "scream": {"fps": 24, "loop": False, "keys": [
        K(0, spine=(8, 0, 0), head=(6, 0, 0), **{"upper_arm.L": (60, 0, 6), "upper_arm.R": (60, 0, -6)}),
        K(8, spine=(-8, 0, 0), head=(-30, 0, 0),
          **{"upper_arm.L": (84, 0, 40), "upper_arm.R": (84, 0, -40), "forearm.L": (16, 0, 0), "forearm.R": (16, 0, 0)}),
        K(20, spine=(-6, 0, 0), head=(-26, 4, 0),
          **{"upper_arm.L": (80, 0, 36), "upper_arm.R": (86, 0, -38)}),
        K(30, spine=(8, 0, 0), head=(6, 0, 0), **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6)}),
    ]},
    "search": {"fps": 24, "loop": True, "keys": [
        K(0, spine=(8, -8, 0), head=(4, -34, 2), **{"upper_arm.L": (40, 0, 8), "upper_arm.R": (36, 0, -8)}),
        K(20, spine=(10, 6, 0), head=(2, 30, -2), **{"upper_arm.L": (36, 0, 8), "upper_arm.R": (42, 0, -8)}),
        K(40, spine=(8, -8, 0), head=(4, -34, 2), **{"upper_arm.L": (40, 0, 8), "upper_arm.R": (36, 0, -8)}),
    ]},
    "getup": {"fps": 24, "loop": False, "keys": [
        K(0, spine=(38, 0, 0), head=(20, 0, 0),
          **{"upper_arm.L": (88, 0, 10), "upper_arm.R": (84, 0, -10), "thigh.L": (40, 0, 0), "thigh.R": (24, 0, 0),
             "shin.L": (52, 0, 0), "shin.R": (60, 0, 0)}),
        K(16, spine=(22, 0, 0), head=(10, 0, 0),
          **{"upper_arm.L": (72, 0, 8), "upper_arm.R": (70, 0, -8), "thigh.L": (20, 0, 0), "thigh.R": (10, 0, 0),
             "shin.L": (26, 0, 0), "shin.R": (30, 0, 0)}),
        K(30, spine=(8, 0, 0), head=(6, 0, 0),
          **{"upper_arm.L": (66, 0, 6), "upper_arm.R": (66, 0, -6), "thigh.L": (0, 0, 0), "thigh.R": (0, 0, 0),
             "shin.L": (4, 0, 0), "shin.R": (4, 0, 0)}),
    ]},
    # human (hero/survivor) idles reuse names with calmer arms
    "human_idle": {"fps": 24, "loop": True, "keys": [
        K(0, spine=(2, 0, 0), head=(2, -8, 0), **{"upper_arm.L": (4, 0, 5), "upper_arm.R": (4, 0, -5),
          "forearm.L": (8, 0, 0), "forearm.R": (8, 0, 0)}),
        K(30, spine=(3, 0, 0), head=(3, 9, 0), **{"upper_arm.L": (6, 0, 6), "upper_arm.R": (3, 0, -4)}),
        K(60, spine=(2, 0, 0), head=(2, -8, 0), **{"upper_arm.L": (4, 0, 5), "upper_arm.R": (4, 0, -5)}),
    ]},
    "human_walk": {"fps": 24, "loop": True, "keys": [
        K(0, spine=(4, -4, 1), hips=(0, -5, 2),
          **{"thigh.L": (28, 0, 0), "shin.L": (10, 0, 0), "foot.L": (-8, 0, 0),
             "thigh.R": (-22, 0, 0), "shin.R": (18, 0, 0), "foot.R": (14, 0, 0),
             "upper_arm.L": (-18, 0, 5), "forearm.L": (12, 0, 0),
             "upper_arm.R": (18, 0, -5), "forearm.R": (16, 0, 0)}),
        K(8, spine=(5, 0, -1), hips=(0, 0, -2),
          **{"thigh.L": (0, 0, 0), "shin.L": (16, 0, 0), "thigh.R": (-2, 0, 0), "shin.R": (30, 0, 0),
             "upper_arm.L": (0, 0, 5), "upper_arm.R": (0, 0, -5)}),
        K(16, spine=(4, 4, 1), hips=(0, 5, 2),
          **{"thigh.L": (-22, 0, 0), "shin.L": (18, 0, 0), "foot.L": (14, 0, 0),
             "thigh.R": (28, 0, 0), "shin.R": (10, 0, 0), "foot.R": (-8, 0, 0),
             "upper_arm.L": (18, 0, 5), "forearm.L": (16, 0, 0),
             "upper_arm.R": (-18, 0, -5), "forearm.R": (12, 0, 0)}),
        K(24, spine=(5, 0, -1), hips=(0, 0, -2),
          **{"thigh.L": (-2, 0, 0), "shin.L": (30, 0, 0), "thigh.R": (0, 0, 0), "shin.R": (16, 0, 0),
             "upper_arm.L": (0, 0, 5), "upper_arm.R": (0, 0, -5)}),
        K(32, spine=(4, -4, 1), hips=(0, -5, 2),
          **{"thigh.L": (28, 0, 0), "shin.L": (10, 0, 0), "foot.L": (-8, 0, 0),
             "thigh.R": (-22, 0, 0), "shin.R": (18, 0, 0), "foot.R": (14, 0, 0),
             "upper_arm.L": (-18, 0, 5), "forearm.L": (12, 0, 0),
             "upper_arm.R": (18, 0, -5), "forearm.R": (16, 0, 0)}),
    ]},
}

JOINT_TO_BONE = {
    "hips": "hips", "spine": "spine", "head": "head",
    "upper_arm.L": "upperarm.L", "upper_arm.R": "upperarm.R",
    "forearm.L": "forearm.L", "forearm.R": "forearm.R",
    "thigh.L": "thigh.L", "thigh.R": "thigh.R",
    "shin.L": "shin.L", "shin.R": "shin.R",
    "foot.L": "foot.L", "foot.R": "foot.R",
}
# Sign mapping semantic(pitch,yaw,roll) -> Blender bone euler (x,y,z), see header notes.
# Downward bones (limbs): forward pitch = -X. Upward bones (spine/head): forward pitch = +X.
BONE_SIGN = {}
for _b in ("hips", "spine", "chest", "neck", "head"):
    BONE_SIGN[_b] = (1, 0, 2, 1)   # euler.x <- pitch*(+1); stored as (axis_for_pitch...)
DOWN_BONES = {"upperarm.L", "upperarm.R", "forearm.L", "forearm.R", "hand.L", "hand.R",
              "thigh.L", "thigh.R", "shin.L", "shin.R", "foot.L", "foot.R"}


def semantic_to_bone_euler(bone, pyr):
    pitch, yaw, roll = [math.radians(a) for a in pyr]
    if bone in DOWN_BONES:
        # bone Y points down limb; local X ~ world X; local Z ~ -facing(back)
        return (-pitch, roll, yaw)
    else:
        # bone Y points up; local Z ~ facing
        return (pitch, -roll, -yaw)


def write_animations_json(path):
    data = {}
    for name, a in ANIMATIONS.items():
        data[name] = {"fps": a["fps"], "loop": a["loop"],
                      "keys": [[f, {j: list(v) for j, v in jd.items()}] for f, jd in a["keys"]]}
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    return data


# ---------------------------------------------------------------- humanoid builder

def _limb(name, length, r0, r1, material, sleeve_mat=None, sleeve_frac=0.45, seed=0):
    """Tapered limb segment, origin at proximal joint, extends down -Z."""
    parts = []
    main = cylinder(name + "_m", r0, length, (0, 0, -length / 2), material, vertices=10, taper_top=r1 / r0)
    # cylinders taper at +Z end in helper; our distal end is -Z => rebuild taper manually:
    parts.append(main)
    o = join(parts, name)
    return o


def _tapered_segment(name, length, r_prox, r_dist, material, bulge=1.12):
    """Limb segment with a muscle bulge, origin top, extends -Z."""
    bpy.ops.mesh.primitive_cylinder_add(radius=1.0, depth=length, vertices=10,
                                        location=(0, 0, 0))
    o = bpy.context.active_object
    o.name = name
    for v in o.data.vertices:
        zn = v.co.z / length + 0.5   # 0 at bottom .. 1 at top (before shift)
        zn = max(0.0, min(1.0, zn))
        r = r_dist + (r_prox - r_dist) * zn
        r *= 1.0 + (bulge - 1.0) * math.sin(math.pi * zn)
        v.co.x *= r
        v.co.y *= r * 0.92
        v.co.z -= length / 2.0       # now local z in [-length, 0]: hangs below pivot
    for p in o.data.polygons:
        p.use_smooth = True
    return _finish(o, material)


def _head(name, skin, hair_mat, cfg):
    hs = cfg.get("head_scale", 1.0)
    parts = []
    cranium = sphere("cranium", 0.115 * hs, (0, 0.012, 0.115), (0.88, 0.98, 1.02), skin, segments=18)
    parts.append(cranium)
    jaw = box("jaw", (0.105 * hs, 0.11, 0.085), (0, -0.035, 0.035), skin, bevel=0.02)
    parts.append(jaw)
    nose = box("nose", (0.028, 0.03, 0.045), (0, -0.115 * hs, 0.085), skin, bevel=0.008)
    parts.append(nose)
    dark = mat("eye_dark", (0.05, 0.04, 0.05), 0.4)
    for s in (-1, 1):
        eye = sphere("eye", 0.0135, (0.042 * s * hs, -0.098 * hs, 0.115), (1, 0.5, 1), dark, segments=8)
        parts.append(eye)
        brow = box("brow", (0.045, 0.012, 0.012), (0.042 * s * hs, -0.1 * hs, 0.148), hair_mat, bevel=0.003)
        parts.append(brow)
    mouth = box("mouth", (0.055 * hs, 0.008, 0.014), (0, -0.098 * hs, 0.028),
                mat("mouth_dark", (0.16, 0.03, 0.03), 0.6), bevel=0.002)
    parts.append(mouth)
    if cfg.get("hair", True):
        hair = sphere("hair", 0.12 * hs, (0, 0.028, 0.132), (0.92, 1.0, 0.95), hair_mat, segments=14)
        # cut lower/front of hair blob by moving front-bottom verts in
        for v in hair.data.vertices:
            if v.co.y < -0.02 and v.co.z < 0.13:
                v.co.y = -0.02
            if v.co.z < 0.045:
                v.co.z = 0.045
        parts.append(hair)
    if cfg.get("zombie"):
        blood = mat("blood_dry", (0.32, 0.03, 0.03), 0.55)
        wound = sphere("wound", 0.032, (0.062 * hs, -0.07, 0.13), (0.7, 0.5, 0.9), blood, segments=8)
        parts.append(wound)
        drip = box("blood_drip", (0.015, 0.005, 0.055), (0.018, -0.1 * hs, -0.005), blood, bevel=0.001)
        parts.append(drip)
    return join(parts, name)


def _hand(name, skin, cfg):
    parts = [box("palm", (0.075, 0.028, 0.085), (0, 0, -0.045), skin, bevel=0.008)]
    claw = cfg.get("claws", False)
    for i, fx in enumerate((-0.027, -0.009, 0.009, 0.027)):
        ln = 0.055 - abs(i - 1.5) * 0.006
        f = box("finger", (0.014, 0.014, ln), (fx, 0, -0.085 - ln / 2), skin, bevel=0.004)
        parts.append(f)
        if claw:
            c = cone("claw", 0.006, 0.0008, 0.045, (fx, 0, -0.085 - ln - 0.02),
                     mat("claw_bone", (0.75, 0.7, 0.58), 0.5), rot=(math.pi, 0, 0), vertices=6)
            parts.append(c)
    thumb = box("thumb", (0.015, 0.015, 0.045), (0.04 if "L" in name else -0.04, -0.005, -0.05), skin, bevel=0.004,
                rot=(0, 0.5 if "L" in name else -0.5, 0))
    parts.append(thumb)
    return join(parts, name)


def _foot(name, shoe_mat, cfg):
    parts = [box("shoe", (0.085, 0.23, 0.055), (0, -0.055, -0.085), shoe_mat, bevel=0.012),
             box("toe", (0.088, 0.07, 0.04), (0, -0.15, -0.095), shoe_mat, bevel=0.014),
             box("ankle", (0.07, 0.08, 0.06), (0, 0.01, -0.03), shoe_mat, bevel=0.01)]
    return join(parts, name)


def _torso(name, shirt, cfg):
    belly = cfg.get("belly", 0.0)

    def profile(z):  # z: 0 bottom (waist) -> 1 top (neck)
        sx = 0.80 + 0.26 * math.sin(math.pi * min(1.0, z * 1.15)) ** 0.7 - (0.16 if z > 0.9 else 0)
        sy = 0.78 + 0.22 * math.sin(math.pi * z) + belly * math.exp(-((z - 0.35) ** 2) / 0.02)
        return (sx, sy, 0.0, -belly * 0.35 * math.exp(-((z - 0.35) ** 2) / 0.02))

    t = shaped_box("torso_m", (0.38, 0.21, 0.52), (0, 0, 0.26), shirt, profile, bevel=0.015)
    objs = [t]
    neck = cylinder("neck_stump", 0.045, 0.07, (0, 0, 0.545), cfg["_skin"], vertices=10)
    objs.append(neck)
    if cfg.get("zombie"):
        blood = mat("blood_dry", (0.32, 0.03, 0.03), 0.55)
        stain = box("blood_chest", (0.15, 0.012, 0.2), (0.05, -0.098, 0.3), blood, bevel=0.004)
        objs.append(stain)
        stain2 = box("blood_belly", (0.1, 0.012, 0.12), (-0.07, -0.104, 0.14), blood, bevel=0.004)
        objs.append(stain2)
    acc = cfg.get("accessories", [])
    if "vest" in acc:
        vest = shaped_box("vest", (0.4, 0.24, 0.34), (0, 0, 0.3), mat("vest_olive", (0.25, 0.28, 0.16), 0.95),
                          lambda z: (0.95, 0.95, 0, 0), bevel=0.01)
        objs.append(vest)
        for i, px in enumerate((-0.09, 0.0, 0.09)):
            pouch = box("pouch", (0.07, 0.04, 0.1), (px, -0.13, 0.22), mat("vest_olive", (0.25, 0.28, 0.16)), bevel=0.008)
            objs.append(pouch)
    if "backpack" in acc:
        bp = box("backpack", (0.27, 0.13, 0.38), (0, 0.15, 0.3), mat("backpack", (0.3, 0.24, 0.14)), bevel=0.025)
        objs.append(bp)
        roll = cylinder("bedroll", 0.05, 0.26, (0, 0.16, 0.53), mat("bedroll", (0.35, 0.38, 0.3)), rot=(0, math.pi / 2, 0))
        objs.append(roll)
    if "doctor_cross" in acc:
        red = mat("med_red", (0.75, 0.08, 0.08), 0.6)
        objs.append(box("cross_v", (0.035, 0.008, 0.11), (-0.1, -0.108, 0.36), red, bevel=0.001))
        objs.append(box("cross_h", (0.11, 0.008, 0.035), (-0.1, -0.108, 0.36), red, bevel=0.001))
    if "spikes" in acc:
        bone_m = mat("spike_bone", (0.72, 0.66, 0.52), 0.5)
        for i, zz in enumerate((0.14, 0.26, 0.38, 0.48)):
            sp = cone("spine_spike", 0.016, 0.001, 0.09 + 0.02 * math.sin(i), (0, 0.115, zz),
                      bone_m, rot=(math.pi / 2.4, 0, 0), vertices=6)
            objs.append(sp)
    return join(objs, name)


def _pelvis(name, pants, cfg):
    p = shaped_box("pelvis_m", (0.315, 0.2, 0.19), (0, 0, -0.055), pants,
                   lambda z: (1.0 - 0.12 * z, 0.95, 0, 0), bevel=0.018)
    objs = [p]
    belt = box("belt", (0.325, 0.21, 0.045), (0, 0, 0.022), mat("belt", (0.16, 0.12, 0.09)), bevel=0.006)
    objs.append(belt)
    return join(objs, name)


def _accessory_head(cfg, skin):
    acc = cfg.get("accessories", [])
    objs = []
    if "police_cap" in acc:
        cap_m = mat("police_blue", (0.08, 0.11, 0.3), 0.8)
        objs.append(cylinder("cap_top", 0.105, 0.045, (0, 0.005, 0.215), cap_m, vertices=14))
        objs.append(box("cap_brim", (0.15, 0.09, 0.012), (0, -0.1, 0.19), mat("cap_black", (0.05, 0.05, 0.06)), bevel=0.004))
        objs.append(box("badge", (0.025, 0.006, 0.03), (0, -0.105, 0.215), mat("badge_gold", (0.8, 0.65, 0.2), 0.35, 0.8), bevel=0.001))
    if "helmet" in acc:
        h = sphere("helmet", 0.128, (0, 0.01, 0.125), (0.95, 1.02, 0.92), mat("helmet_olive", (0.26, 0.3, 0.18), 0.7), segments=14)
        for v in h.data.vertices:
            if v.co.z < 0.105:
                v.co.z = 0.105
        objs.append(h)
        objs.append(cylinder("helmet_rim", 0.132, 0.018, (0, 0.01, 0.108), mat("helmet_olive", (0.26, 0.3, 0.18)), vertices=14))
    if "fire_helmet" in acc:
        fm = mat("fire_yellow", (0.8, 0.62, 0.1), 0.5)
        h = sphere("fire_helmet", 0.126, (0, 0.01, 0.13), (0.95, 1.05, 0.9), fm, segments=14)
        for v in h.data.vertices:
            if v.co.z < 0.12:
                v.co.z = 0.12
        objs.append(h)
        objs.append(cylinder("fire_brim", 0.165, 0.012, (0, 0.03, 0.115), fm, vertices=16))
    if "mask" in acc:
        objs.append(box("mask", (0.17, 0.03, 0.1), (0, -0.085, 0.045), mat("mask_black", (0.06, 0.06, 0.07)), bevel=0.01))
    if "hat" in acc:
        hm = mat("hat_brown", (0.34, 0.24, 0.13), 0.9)
        objs.append(cylinder("hat_brim", 0.17, 0.012, (0, 0, 0.185), hm, vertices=16))
        objs.append(cylinder("hat_top", 0.1, 0.09, (0, 0, 0.225), hm, vertices=14, taper_top=0.85))
    if "horns" in acc:
        bm = mat("spike_bone", (0.72, 0.66, 0.52), 0.5)
        for s in (-1, 1):
            objs.append(cone("horn", 0.02, 0.001, 0.14, (0.08 * s, 0.02, 0.24), bm,
                             rot=(0, s * -0.45, 0), vertices=6))
    return objs


def build_humanoid(cfg):
    """Build part-kit objects (origin = joint) + full rigged copy. Returns dict of info.
    cfg keys: name, scale, head_scale, skin, hair, shirt, pants, shoe (RGB), zombie,
              accessories[], claws, belly, crawler, missing_arm ('R'/'L'/None), arm_scale
    """
    reset_scene()
    rnd = random.Random(cfg["name"])
    scale = cfg.get("scale", 1.0)
    joints = {k: tuple(c * scale for c in v) for k, v in BASE_JOINTS.items()}

    skin = mat("skin_" + cfg["name"], cfg["skin"], 0.75)
    cfg["_skin"] = skin
    hair_m = mat("hair_" + cfg["name"], cfg.get("hair", (0.12, 0.09, 0.06)), 0.95)
    shirt = mat("shirt_" + cfg["name"], cfg["shirt"], 0.92)
    pants = mat("pants_" + cfg["name"], cfg["pants"], 0.92)
    shoe = mat("shoe_" + cfg["name"], cfg.get("shoe", (0.1, 0.09, 0.09)), 0.8)

    arm_s = cfg.get("arm_scale", 1.0)
    fat = cfg.get("fat", 1.0)

    def L(a, b):  # distance between joints
        return (Vector(joints[a]) - Vector(joints[b])).length

    parts = {}
    part_colors = {}

    parts["pelvis"] = _pelvis("pelvis", pants, cfg)
    part_colors["pelvis"] = list(cfg["pants"])
    p = parts["pelvis"]
    p.scale = (fat, fat, 1.0)

    parts["torso"] = _torso("torso", shirt, cfg)
    part_colors["torso"] = list(cfg["shirt"])
    parts["torso"].scale = (fat, fat, 1.0)
    # torso geometry built 0..0.52 up from its pivot; pivot sits at 'spine' joint.
    # shift geometry down a bit so it overlaps pelvis:
    move_geom(parts["torso"], (0, 0, -0.06))

    head_objs = [_head("head_m", skin, hair_m, cfg)] + _accessory_head(cfg, skin)
    parts["head"] = join(head_objs, "head")
    part_colors["head"] = list(cfg["skin"])
    if cfg.get("accessories") and ("helmet" in cfg["accessories"] or "fire_helmet" in cfg["accessories"]):
        pass  # face still dominant near hitbox; fine

    for side in ("L", "R"):
        missing = cfg.get("missing_arm") == side
        ua_len = L(f"shoulder.{side}", f"elbow.{side}") * arm_s / max(scale, 1e-6) * 1.0
        ua_len = 0.27 * arm_s
        fa_len = 0.25 * arm_s
        if missing:
            stump = _tapered_segment(f"upper_arm.{side}", 0.12, 0.052, 0.03, skin)
            blood = sphere("stump_blood", 0.032, (0, 0, -0.12), (1, 1, 0.6),
                           mat("blood_dry", (0.32, 0.03, 0.03), 0.55), segments=8)
            blood.parent = None
            parts[f"upper_arm.{side}"] = join([stump, blood], f"upper_arm.{side}")
            parts[f"forearm.{side}"] = box(f"forearm.{side}", (0.001, 0.001, 0.001), (0, 0, 0), skin)
            parts[f"hand.{side}"] = box(f"hand.{side}", (0.001, 0.001, 0.001), (0, 0, 0), skin)
        else:
            ua = _tapered_segment(f"upper_arm.{side}_m", ua_len, 0.055 * fat, 0.040, skin)
            sleeve = _tapered_segment("sleeve", ua_len * 0.5, 0.062 * fat, 0.056, shirt, bulge=1.02)
            parts[f"upper_arm.{side}"] = join([ua, sleeve], f"upper_arm.{side}")
            parts[f"forearm.{side}"] = _tapered_segment(f"forearm.{side}", fa_len, 0.041, 0.030, skin)
            parts[f"hand.{side}"] = _hand(f"hand.{side}", skin, cfg)
        part_colors[f"upper_arm.{side}"] = list(cfg["shirt"])
        part_colors[f"forearm.{side}"] = list(cfg["skin"])
        part_colors[f"hand.{side}"] = list(cfg["skin"])

    for side in ("L", "R"):
        thigh_len = 0.40
        shin_len = 0.44
        th = _tapered_segment(f"thigh.{side}_m", thigh_len, 0.072 * fat, 0.048, pants)
        if cfg.get("crawler") and side == "R":
            th = join([th], f"thigh.{side}")
        parts[f"thigh.{side}"] = th if isinstance(th, bpy.types.Object) else th
        parts[f"thigh.{side}"].name = f"thigh.{side}"
        part_colors[f"thigh.{side}"] = list(cfg["pants"])
        if cfg.get("crawler"):
            # legs are mangled stumps below the knee
            stump = _tapered_segment(f"shin.{side}", 0.10, 0.046, 0.02,
                                     mat("blood_dry", (0.32, 0.03, 0.03), 0.55))
            parts[f"shin.{side}"] = stump
            parts[f"foot.{side}"] = box(f"foot.{side}", (0.001, 0.001, 0.001), (0, 0, 0), skin)
            part_colors[f"shin.{side}"] = [0.32, 0.03, 0.03]
            part_colors[f"foot.{side}"] = list(cfg["skin"])
        else:
            sh = _tapered_segment(f"shin.{side}_m", shin_len, 0.048, 0.032, pants if not cfg.get("torn_pants") else skin)
            parts[f"shin.{side}"] = join([sh], f"shin.{side}")
            parts[f"foot.{side}"] = _foot(f"foot.{side}", shoe, cfg)
            part_colors[f"shin.{side}"] = list(cfg["pants"] if not cfg.get("torn_pants") else cfg["skin"])
            part_colors[f"foot.{side}"] = list(cfg.get("shoe", (0.1, 0.09, 0.09)))

    # Bake every part into true pivot-local geometry (identity transform),
    # then apply the character's uniform scale about that pivot so the
    # exported OBJs are world-size correct.
    for part in parts.values():
        bake_transform(part)
        if scale != 1.0:
            part.scale = (scale, scale, scale)
            bpy.context.view_layer.objects.active = part
            part.select_set(True)
            bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
            part.select_set(False)

    skel = skeleton_json(joints, scale, part_colors)
    return {"parts": parts, "joints": joints, "skeleton": skel, "cfg": cfg}


def build_armature_and_rig(info):
    """Create armature from joints, duplicate parts into rest positions,
    weight each part 100% to its bone, author all actions as NLA strips. Returns armature obj."""
    joints = info["joints"]
    cfg = info["cfg"]
    arm_data = bpy.data.armatures.new(cfg["name"] + "_rig")
    arm = bpy.data.objects.new(cfg["name"] + "_armature", arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode='EDIT')
    for bname, hj, tj, parent in BONES:
        eb = arm_data.edit_bones.new(bname)
        eb.head = joints[hj]
        eb.tail = joints[tj]
        if parent:
            eb.parent = arm_data.edit_bones[parent]
            eb.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')

    # position kit parts at rest joint locations and bind
    bone_head = {b[0]: joints[b[1]] for b in BONES}
    part_bone = {p[0]: p[1] for p in PARTS}
    for part_name, obj in info["parts"].items():
        bone = part_bone[part_name]
        obj.location = bone_head[bone]
        vg = obj.vertex_groups.new(name=bone)
        vg.add(list(range(len(obj.data.vertices))), 1.0, 'REPLACE')
        mod = obj.modifiers.new("arm", 'ARMATURE')
        mod.object = arm
        obj.parent = arm

    # author actions
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode='POSE')
    if not arm.animation_data:
        arm.animation_data_create()
    pose_bones = arm.pose.bones
    for aname, anim in ANIMATIONS.items():
        action = bpy.data.actions.new(f"{cfg['name']}_{aname}")
        arm.animation_data.action = action
        for frame, jd in anim["keys"]:
            bpy.context.scene.frame_set(frame)
            for pb in pose_bones:
                pb.rotation_mode = 'XYZ'
                pb.rotation_euler = (0, 0, 0)
                pb.location = (0, 0, 0)
            for joint, pyr in jd.items():
                bone = JOINT_TO_BONE.get(joint)
                if bone is None:
                    continue
                if joint == "spine":
                    # split spine bend across spine+chest for a smoother curve
                    for bn, f in (("spine", 0.6), ("chest", 0.4)):
                        e = semantic_to_bone_euler(bn, pyr)
                        pose_bones[bn].rotation_euler = tuple(v * f for v in e)
                else:
                    pose_bones[bone].rotation_euler = semantic_to_bone_euler(bone, pyr)
            for pb in pose_bones:
                pb.keyframe_insert(data_path="rotation_euler", frame=frame)
        # push into NLA so glTF exports every action
        track = arm.animation_data.nla_tracks.new()
        track.name = aname
        strip = track.strips.new(aname, int(action.frame_range[0]), action)
        strip.name = aname
    arm.animation_data.action = None
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.context.scene.frame_set(0)
    return arm


def export_humanoid(info, out_dir, glb=True, blend=True):
    """Export kit OBJs (identity transform) then rigged GLB + blend. Order matters:
    OBJs first (kit local), then rig positions parts in world."""
    cfg = info["cfg"]
    os.makedirs(out_dir, exist_ok=True)
    manifest_parts = {}
    for part_name, obj in info["parts"].items():
        path = os.path.join(out_dir, part_name.replace(".", "_") + ".obj")
        export_obj(obj, path)
        manifest_parts[part_name] = {**stats(obj), "file": os.path.basename(path)}
    with open(os.path.join(out_dir, "skeleton.json"), "w", encoding="utf-8") as fh:
        json.dump(info["skeleton"], fh, ensure_ascii=False, indent=1)
    arm = build_armature_and_rig(info)
    glb_path = None
    if glb:
        glb_path = os.path.join(out_dir, cfg["name"] + ".glb")
        export_glb(glb_path)
    if blend:
        save_blend(os.path.join(out_dir, cfg["name"] + ".blend"))
    return {"parts": manifest_parts, "glb": glb_path, "skeleton": "skeleton.json",
            "height": info["skeleton"]["height"]}
