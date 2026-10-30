"""
DEAD WORLD: APOCALYPSE - master asset generator (run with Blender's Python / bpy).
Usage:  python blender/generate_all.py [--only zombie_walker|weapons|vehicles|buildings|env|props|humans|zombies]
Outputs into ../assets/ + manifest.json + animations/zombie_animations.json
"""
import os, sys, json, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import bpy
from dw_lib import (build_humanoid, export_humanoid, write_animations_json,
                    export_obj, export_glb, save_blend, stats, reset_scene)
import dw_props

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "assets")

ZSKIN = {
    "pale": (0.62, 0.6, 0.5), "green": (0.45, 0.55, 0.36), "grey": (0.52, 0.52, 0.5),
    "dark": (0.4, 0.36, 0.3), "sick": (0.58, 0.58, 0.42),
}

ZOMBIES = {
    "zombie_walker": dict(skin=ZSKIN["pale"], hair=(0.15, 0.12, 0.1), shirt=(0.3, 0.32, 0.38),
                          pants=(0.2, 0.2, 0.24), zombie=True),
    "zombie_slow": dict(skin=ZSKIN["sick"], hair=(0.2, 0.16, 0.12), shirt=(0.38, 0.3, 0.2),
                        pants=(0.26, 0.24, 0.2), zombie=True, fat=1.18, belly=0.55),
    "zombie_runner": dict(skin=ZSKIN["grey"], hair=(0.08, 0.07, 0.07), shirt=(0.5, 0.16, 0.14),
                          pants=(0.14, 0.14, 0.16), zombie=True, fat=0.88, arm_scale=1.06, torn_pants=True),
    "zombie_crawler": dict(skin=ZSKIN["dark"], hair=(0.1, 0.08, 0.06), shirt=(0.28, 0.26, 0.22),
                           pants=(0.22, 0.2, 0.18), zombie=True, crawler=True, arm_scale=1.1),
    "zombie_soldier": dict(skin=ZSKIN["green"], hair=(0.1, 0.1, 0.08), shirt=(0.26, 0.3, 0.18),
                           pants=(0.24, 0.28, 0.17), zombie=True, accessories=["helmet", "vest"], fat=1.05),
    "zombie_police": dict(skin=ZSKIN["pale"], hair=(0.12, 0.1, 0.08), shirt=(0.1, 0.14, 0.32),
                          pants=(0.08, 0.1, 0.22), zombie=True, accessories=["police_cap"]),
    "zombie_doctor": dict(skin=ZSKIN["sick"], hair=(0.3, 0.3, 0.3), shirt=(0.82, 0.82, 0.8),
                          pants=(0.35, 0.38, 0.4), zombie=True, accessories=["doctor_cross"]),
    "zombie_firefighter": dict(skin=ZSKIN["dark"], hair=(0.12, 0.09, 0.07), shirt=(0.16, 0.15, 0.14),
                               pants=(0.15, 0.14, 0.13), zombie=True, accessories=["fire_helmet"], fat=1.08),
    "zombie_screamer": dict(skin=(0.72, 0.68, 0.6), hair=(0.05, 0.05, 0.05), shirt=(0.45, 0.4, 0.36),
                            pants=(0.3, 0.26, 0.24), zombie=True, fat=0.82, arm_scale=1.18, head_scale=1.06),
    "zombie_mutant": dict(skin=(0.36, 0.5, 0.28), hair=(0.05, 0.08, 0.04), shirt=(0.2, 0.22, 0.16),
                          pants=(0.18, 0.2, 0.15), zombie=True, scale=1.09, head_scale=1.14,
                          arm_scale=1.28, claws=True, fat=1.12, accessories=["spikes", "horns"], hair_on=False),
    "zombie_boss": dict(skin=(0.42, 0.38, 0.32), hair=(0.07, 0.06, 0.05), shirt=(0.22, 0.18, 0.16),
                        pants=(0.16, 0.15, 0.14), zombie=True, scale=1.52, head_scale=1.1,
                        arm_scale=1.15, claws=True, fat=1.3, belly=0.5,
                        accessories=["spikes", "horns"], hair_on=False),
}

HUMANS = {
    "hero": dict(skin=(0.78, 0.6, 0.46), hair=(0.2, 0.13, 0.08), shirt=(0.2, 0.24, 0.2),
                 pants=(0.16, 0.17, 0.2), accessories=["backpack"]),
    "survivor_m": dict(skin=(0.7, 0.54, 0.4), hair=(0.1, 0.08, 0.06), shirt=(0.42, 0.3, 0.18),
                       pants=(0.24, 0.22, 0.2)),
    "survivor_f": dict(skin=(0.82, 0.64, 0.5), hair=(0.35, 0.2, 0.1), shirt=(0.46, 0.2, 0.24),
                       pants=(0.18, 0.2, 0.28), fat=0.88, scale=0.96),
    "bandit": dict(skin=(0.66, 0.5, 0.38), hair=(0.06, 0.06, 0.06), shirt=(0.12, 0.12, 0.13),
                   pants=(0.14, 0.14, 0.15), accessories=["mask"], fat=1.04),
    "soldier_npc": dict(skin=(0.72, 0.56, 0.42), hair=(0.14, 0.1, 0.07), shirt=(0.28, 0.32, 0.2),
                        pants=(0.26, 0.3, 0.19), accessories=["helmet", "vest"]),
    "trader": dict(skin=(0.76, 0.6, 0.45), hair=(0.4, 0.36, 0.3), shirt=(0.5, 0.42, 0.28),
                   pants=(0.3, 0.26, 0.2), accessories=["hat", "backpack"], fat=1.1, belly=0.3),
}


def bake_transforms(obj):
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)


def gen_humanoid(kind_dir, name, cfg, manifest):
    t0 = time.time()
    cfg = dict(cfg)
    cfg["name"] = name
    if cfg.pop("hair_on", True) is False:
        cfg["hair"] = cfg.get("hair", (0.1, 0.1, 0.1))
        cfg["_no_hair"] = True
    info = build_humanoid(cfg)
    out = os.path.join(ASSETS, "models", kind_dir, name)
    res = export_humanoid(info, out)
    total = sum(p["polys"] for p in res["parts"].values())
    manifest[name] = {**res, "total_polys": total}
    print(f"[humanoid] {name}: {total} polys, {len(res['parts'])} parts, {time.time()-t0:.1f}s", flush=True)


def gen_weapons(manifest):
    out_dir = os.path.join(ASSETS, "models", "weapons")
    for name, fn in dw_props.WEAPON_BUILDERS.items():
        obj = fn()
        bake_transforms(obj)
        path = os.path.join(out_dir, name + ".obj")
        export_obj(obj, path)
        export_glb(os.path.join(out_dir, name + ".glb"))
        manifest[name] = {"file": name + ".obj", **stats(obj)}
        print(f"[weapon] {name} {stats(obj)}", flush=True)


def gen_vehicles(manifest):
    for name in dw_props.VEHICLE_CFGS:
        body, wheel, meta = dw_props.build_vehicle(name)
        out_dir = os.path.join(ASSETS, "models", "vehicles", name)
        os.makedirs(out_dir, exist_ok=True)
        export_obj(body, os.path.join(out_dir, "body.obj"))
        export_obj(wheel, os.path.join(out_dir, "wheel.obj"))
        # assemble a GLB: place wheel copies
        meta_out = dict(meta)
        manifest[name] = {"body": "body.obj", "wheel": "wheel.obj", "meta": meta_out,
                          "body_stats": stats(body), "wheel_stats": stats(wheel)}
        print(f"[vehicle] {name} body={stats(body)}", flush=True)


def gen_buildings(manifest):
    out_dir = os.path.join(ASSETS, "models", "buildings")
    for name, cfg in dw_props.BUILDINGS.items():
        obj = dw_props.build_building(name, **cfg)
        if name == "factory":
            obj = dw_props.build_factory_extra()
        bake_transforms(obj)
        export_obj(obj, os.path.join(out_dir, name + ".obj"))
        dims = [round(float(d), 2) for d in obj.dimensions]  # Blender x,y,z
        manifest[name] = {"file": name + ".obj", **stats(obj),
                          "size_xyz_engine": [dims[0], dims[2], dims[1]]}
        print(f"[building] {name} {stats(obj)} size={dims}", flush=True)


def gen_env(manifest):
    out_dir = os.path.join(ASSETS, "models", "environment")
    for name, fn in dw_props.ENV_BUILDERS.items():
        obj = fn()
        bake_transforms(obj)
        export_obj(obj, os.path.join(out_dir, name + ".obj"))
        dims = [round(float(d), 2) for d in obj.dimensions]
        manifest[name] = {"file": name + ".obj", **stats(obj),
                          "size_xyz_engine": [dims[0], dims[2], dims[1]]}
        print(f"[env] {name} {stats(obj)}", flush=True)


def gen_props(manifest):
    out_dir = os.path.join(ASSETS, "models", "environment")
    for name, fn in dw_props.PROP_BUILDERS.items():
        obj = fn()
        bake_transforms(obj)
        export_obj(obj, os.path.join(out_dir, name + ".obj"))
        manifest[name] = {"file": name + ".obj", **stats(obj)}
        print(f"[prop] {name} {stats(obj)}", flush=True)


def main():
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1]
    manifest = {"generator": f"Blender {bpy.app.version_string} bpy, dw_lib/dw_props",
                "zombies": {}, "characters": {}, "weapons": {}, "vehicles": {},
                "buildings": {}, "environment": {}, "props": {}}
    mpath = os.path.join(ASSETS, "manifest.json")
    if os.path.exists(mpath):
        try:
            manifest.update(json.load(open(mpath, encoding="utf-8")))
        except Exception:
            pass

    write_animations_json(os.path.join(ASSETS, "animations", "zombie_animations.json"))
    print("[anims] zombie_animations.json written", flush=True)

    if only in (None, "zombies") or only in ZOMBIES:
        for name, cfg in ZOMBIES.items():
            if only not in (None, "zombies") and only != name:
                continue
            gen_humanoid("zombies", name, cfg, manifest["zombies"])
    if only in (None, "humans") or only in HUMANS:
        for name, cfg in HUMANS.items():
            if only not in (None, "humans") and only != name:
                continue
            gen_humanoid("characters", name, cfg, manifest["characters"])
    if only in (None, "weapons"):
        gen_weapons(manifest["weapons"])
    if only in (None, "vehicles"):
        gen_vehicles(manifest["vehicles"])
    if only in (None, "buildings"):
        gen_buildings(manifest["buildings"])
    if only in (None, "env"):
        gen_env(manifest["environment"])
    if only in (None, "props"):
        gen_props(manifest["props"])

    with open(mpath, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=1)
    print("GENERATE_ALL_DONE", flush=True)


if __name__ == "__main__":
    main()
