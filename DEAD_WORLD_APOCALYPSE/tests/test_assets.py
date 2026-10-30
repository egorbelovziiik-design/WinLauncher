"""Asset validation: Blender outputs exist, are well-formed and consistent
with the game's data tables (this is the automated geometry/animation QA gate)."""
import json, os, struct

from game.data import ZOMBIE_TYPES, WEAPONS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")


def load_manifest():
    return json.load(open(os.path.join(ASSETS, "manifest.json"), encoding="utf-8"))


def parse_obj(path):
    verts = 0
    miny = 1e9
    colors = 0
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                parts = line.split()
                verts += 1
                miny = min(miny, float(parts[2]))   # OBJ here is Y-up: y is parts[2] (v x y z)
                if len(parts) >= 7:
                    colors += 1
    return verts, colors, miny


def glb_animations(path):
    data = open(path, "rb").read()
    assert data[:4] == b"glTF"
    ln = struct.unpack("<I", data[12:16])[0]
    g = json.loads(data[20:20 + ln])
    return [a.get("name") for a in g.get("animations", [])], len(g.get("skins", []))


def test_manifest_categories():
    m = load_manifest()
    assert len(m["zombies"]) >= 10
    assert len(m["characters"]) >= 5
    assert len(m["vehicles"]) == 5
    assert len(m["buildings"]) >= 10
    assert len(m["weapons"]) == len(WEAPONS)


def test_every_zombie_type_has_kit():
    m = load_manifest()
    for tid in ZOMBIE_TYPES:
        assert tid in m["zombies"], f"model missing for zombie type {tid}"
        d = os.path.join(ASSETS, "models", "zombies", tid)
        skel = json.load(open(os.path.join(d, "skeleton.json"), encoding="utf-8"))
        assert len(skel["parts"]) == 15
        assert 1.2 < skel["height"] < 3.2, f"implausible height {skel['height']} for {tid}"
        for part in skel["parts"]:
            f = os.path.join(d, part.replace(".", "_") + ".obj")
            assert os.path.exists(f), f
        assert m["zombies"][tid]["total_polys"] > 900, "model too primitive"


def test_zombie_glb_has_rig_and_animations():
    for tid in ("zombie_walker", "zombie_boss", "zombie_crawler"):
        anims, skins = glb_animations(os.path.join(ASSETS, "models", "zombies", tid, tid + ".glb"))
        assert skins == 1
        for needed in ("idle", "walk", "run", "attack", "death", "headshot_death"):
            assert needed in anims, f"{tid} missing anim {needed}"


def test_obj_vertex_colors_present():
    path = os.path.join(ASSETS, "models", "zombies", "zombie_walker", "torso.obj")
    verts, colors, _ = parse_obj(path)
    assert verts > 100 and colors > verts * 0.9


def test_weapons_files_and_balance():
    for wid, w in WEAPONS.items():
        p = os.path.join(ASSETS, "models", "weapons", wid + ".obj")
        assert os.path.exists(p), p
        verts, _, _ = parse_obj(p)
        assert verts >= 25, f"weapon {wid} suspiciously empty"
        assert w["damage"] > 0 and w["rate"] > 0
        if w["kind"] == "gun":
            assert w["mag"] >= 1 and w["ammo"] in ("ammo_9mm", "ammo_rifle", "ammo_shells", "ammo_bolts")


def test_vehicle_metadata_sane():
    m = load_manifest()
    for name, info in m["vehicles"].items():
        meta = info["meta"]
        assert 3.5 < meta["length"] < 6.5
        assert len(meta["wheel_offsets"]) == 4
        assert 0.25 < meta["wheel_radius"] < 0.45
        for f in ("body.obj", "wheel.obj"):
            assert os.path.exists(os.path.join(ASSETS, "models", "vehicles", name, f))


def test_animation_json_continuity():
    anims = json.load(open(os.path.join(ASSETS, "animations", "zombie_animations.json"), encoding="utf-8"))
    assert len(anims) >= 13
    for name, a in anims.items():
        frames = [k[0] for k in a["keys"]]
        assert frames == sorted(frames), f"{name}: keys out of order"
        if a["loop"]:
            first, last = a["keys"][0][1], a["keys"][-1][1]
            for joint, v in first.items():
                if joint in last:
                    for i in range(3):
                        assert abs(v[i] - last[joint][i]) < 25, \
                            f"{name}: loop pops at {joint} ({v} vs {last[joint]})"
        for _, jd in a["keys"]:
            for joint, pyr in jd.items():
                assert all(-180 <= x <= 180 for x in pyr), f"{name}/{joint} angle out of range"


def test_audio_and_textures_exist():
    wavs = [f for f in os.listdir(os.path.join(ASSETS, "audio")) if f.endswith(".wav")]
    assert len(wavs) >= 28
    for needed in ("shoot_rifle.wav", "zombie_scream.wav", "wind.wav", "music_ambient.wav", "engine.wav"):
        assert needed in wavs
    pngs = [f for f in os.listdir(os.path.join(ASSETS, "textures")) if f.endswith(".png")]
    assert len(pngs) >= 18


def test_buildings_have_plausible_sizes():
    m = load_manifest()
    for name, info in m["buildings"].items():
        w, h, d = info["size_xyz_engine"]
        assert 3 < w < 30 and 2.5 < h < 20 and 3 < d < 30, f"{name}: {w}x{h}x{d}"
        assert info["polys"] > 150


def _obj_bounds(path):
    xs, ys, zs = [], [], []
    with open(path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                p = line.split()
                xs.append(float(p[1])); ys.append(float(p[2])); zs.append(float(p[3]))
    return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))


def test_kit_assembles_standing_on_ground():
    """Rest-pose assembly via skeleton offsets must put feet on y=0 and the
    head at the declared height - catches pivot/origin pipeline bugs."""
    for kind, name in [("zombies", "zombie_walker"), ("zombies", "zombie_boss"),
                       ("zombies", "zombie_runner"), ("characters", "hero")]:
        d = os.path.join(ASSETS, "models", kind, name)
        skel = json.load(open(os.path.join(d, "skeleton.json"), encoding="utf-8"))
        world_off = {}

        def off(part):
            if part not in world_off:
                info = skel["parts"][part]
                o = info["rest_offset"]
                if info["parent"] in skel["parts"]:
                    po = off(info["parent"])
                    o = [o[i] + po[i] for i in range(3)]
                world_off[part] = o
            return world_off[part]

        def wb(part):
            mn, mx = _obj_bounds(os.path.join(d, part.replace(".", "_") + ".obj"))
            o = off(part)
            return [mn[i] + o[i] for i in range(3)], [mx[i] + o[i] for i in range(3)]

        foot_mn, _ = wb("foot.L")
        assert -0.04 <= foot_mn[1] <= 0.08, f"{name}: foot bottom at y={foot_mn[1]:.3f} (should touch ground)"
        _, head_mx = wb("head")
        assert abs(head_mx[1] - skel["height"]) < 0.14, f"{name}: head top {head_mx[1]:.2f} vs height {skel['height']}"
        thigh_mn, _ = wb("thigh.L")
        _, shin_mx = wb("shin.L")
        assert abs(shin_mx[1] - thigh_mn[1]) < 0.12, f"{name}: knee gap thigh_bottom={thigh_mn[1]:.3f} shin_top={shin_mx[1]:.3f}"
        shin_mn, _ = wb("shin.L")
        _, foot_mx = wb("foot.L")
        assert abs(foot_mx[1] - shin_mn[1]) < 0.12, f"{name}: ankle gap shin_bottom={shin_mn[1]:.3f} foot_top={foot_mx[1]:.3f}"


def test_buildings_sit_on_ground():
    for name in ("house", "apartment", "hospital"):
        mn, mx = _obj_bounds(os.path.join(ASSETS, "models", "buildings", name + ".obj"))
        assert -0.35 <= mn[1] <= 0.35, f"{name}: base at y={mn[1]:.2f} (should be ~0)"
        assert mx[1] > 2.5


def test_vehicle_wheels_touch_ground():
    m = load_manifest()
    meta = m["vehicles"]["sedan"]["meta"]
    mn, mx = _obj_bounds(os.path.join(ASSETS, "models", "vehicles", "sedan", "wheel.obj"))
    bottom = meta["wheel_offsets"][0][1] + mn[1]
    assert abs(bottom) < 0.03, f"wheel bottom at {bottom:.3f}"
    bmn, _ = _obj_bounds(os.path.join(ASSETS, "models", "vehicles", "sedan", "body.obj"))
    assert -0.05 <= bmn[1] <= 0.35, f"car body base y={bmn[1]:.2f}"
