"""Unit tests for pure game logic (no graphics required)."""
import math, random

from game.survival import SurvivalStats
from game.inventory import Inventory, MAX_WEIGHT
from game.ai_brain import ZombieBrain, steer, NoiseEvent, CHASE, ATTACK, SEARCH
from game.combat import ray_sphere, ray_aabb, hitscan, HitZone, apply_damage, melee_targets, spread_direction
from game.data import ZOMBIE_TYPES, WEAPONS, ITEMS
from game.anim import AnimationLibrary, Animator, AnimClip
from game import save_system


# ---------------- survival ----------------

def test_survival_needs_decay():
    s = SurvivalStats()
    s.update(60)
    assert s.hunger < 82 and s.thirst < 78 and not s.dead


def test_survival_bleeding_and_bandage():
    s = SurvivalStats()
    s.damage(10, cause_bleeding_chance=1.0)
    assert s.bleeding
    h = s.health
    s.update(10)
    assert s.health < h - 3
    s.use_item(ITEMS["bandage"])
    assert not s.bleeding


def test_survival_infection_kills():
    s = SurvivalStats()
    s.infection = 99.0
    s.update(400)
    assert s.dead


def test_survival_food_and_water():
    s = SurvivalStats(hunger=10, thirst=10)
    s.use_item(ITEMS["food_can"])
    s.use_item(ITEMS["water_bottle"])
    assert s.hunger > 40 and s.thirst > 50


def test_survival_sprint_stamina():
    s = SurvivalStats()
    s.update(3, sprinting=True, moving=True)
    assert s.stamina < 75
    s.update(3, sprinting=False, moving=False)
    assert s.stamina > 75


# ---------------- inventory ----------------

def test_inventory_stack_and_weight():
    inv = Inventory()
    assert inv.add("food_can", 5) == 5
    assert inv.count("food_can") == 5
    assert inv.total_weight() > 0
    assert inv.remove("food_can", 2) == 2
    assert inv.count("food_can") == 3


def test_inventory_weight_limit():
    inv = Inventory()
    added = inv.add("fuel_canister", 20)   # 4kg each -> hits limit
    assert added < 20
    assert inv.total_weight() <= MAX_WEIGHT + inv.max_weight_bonus + 0.01


def test_inventory_ammo_and_reload():
    inv = Inventory()
    inv.add("rifle")
    inv.add("ammo_rifle", 3)   # 3 boxes * 20 = 60 rounds
    assert inv.ammo["ammo_rifle"] == 60
    assert inv.reload("rifle") == 30
    assert inv.mags["rifle"] == 30
    assert inv.fire_round("rifle") and inv.mags["rifle"] == 29
    assert inv.ammo["ammo_rifle"] == 30


def test_inventory_hotbar_weapon():
    inv = Inventory()
    inv.add("pistol")
    assert "pistol" in inv.hotbar
    inv.remove("pistol")
    assert "pistol" not in inv.hotbar


def test_inventory_save_roundtrip():
    inv = Inventory()
    inv.add("axe"); inv.add("medkit", 2); inv.add("ammo_9mm", 2)
    inv2 = Inventory.from_dict(inv.to_dict())
    assert inv2.count("medkit") == 2
    assert inv2.ammo["ammo_9mm"] == 24
    assert inv2.has("axe")


# ---------------- AI brain ----------------

def _brain(tid="zombie_walker"):
    return ZombieBrain(ZOMBIE_TYPES[tid], rng=random.Random(7))


def test_brain_vision_cone():
    b = _brain()
    assert b.can_see((0, 0), 0.0, (0, 12)) is True       # ahead (+Z facing yaw=0)
    assert b.can_see((0, 0), 0.0, (0, -12)) is False     # behind
    assert b.can_see((0, 0), 0.0, (0, 90)) is False      # too far
    assert b.can_see((0, 0), 0.0, (0, 12), line_blocked=True) is False


def test_brain_chase_and_attack():
    b = _brain()
    b.update(0.1, (0, 0), 0.0, (0, 10))
    assert b.state == CHASE
    b.update(0.1, (0, 0), 0.0, (0, 1.2))
    assert b.state == ATTACK


def test_brain_hearing_noise():
    b = _brain()
    assert b.on_noise((0, 0), NoiseEvent((8, 0), 30, "gunshot")) is True
    assert b.state == SEARCH


def test_brain_loses_player():
    b = _brain()
    b.update(0.1, (0, 0), 0.0, (0, 10))
    assert b.state == CHASE
    for _ in range(80):  # 8 seconds without sight
        b.update(0.1, (0, 0), 0.0, (0, 300))
    assert b.state in (SEARCH, "wander", "idle")


def test_steer_moves_toward_target():
    pos, yaw = steer((0, 0), 0.0, (0, 20), speed=3.0, dt=0.5)
    assert pos[1] > 1.0
    # obstacle in front pushes sideways
    pos2, _ = steer((0, 0), 0.0, (0, 20), speed=3.0, dt=0.5, obstacles=[(0, 2.0, 1.2)])
    assert abs(pos2[0]) > 0.05 or pos2[1] < pos[1]


# ---------------- combat ----------------

def test_ray_sphere_and_aabb():
    assert ray_sphere((0, 0, 0), (0, 0, 1), (0, 0, 10), 1.0) == 9.0
    assert ray_sphere((0, 0, 0), (0, 0, 1), (5, 0, 10), 1.0) is None
    assert ray_aabb((0, 1, 0), (0, 0, 1), (-1, 0, 8), (1, 3, 10)) == 8.0
    assert ray_aabb((0, 5, 0), (0, 0, 1), (-1, 0, 8), (1, 3, 10)) is None


def test_hitscan_wall_blocks():
    zone = HitZone("z", "head", (0, 1.6, 20), 0.2, 3.0)
    hit, pt, t = hitscan((0, 1.6, 0), (0, 0, 1), 100, [zone], [])
    assert hit is zone
    hit2, pt2, t2 = hitscan((0, 1.6, 0), (0, 0, 1), 100, [zone],
                            [((-2, 0, 9), (2, 3, 11))])
    assert hit2 is None and t2 == 9.0


def test_damage_zones():
    cfg = ZOMBIE_TYPES["zombie_soldier"]   # armor_body .45, helmet
    assert apply_damage(30, "body", cfg) < 30
    head = apply_damage(30, "head", cfg)
    plain = apply_damage(30, "head", ZOMBIE_TYPES["zombie_walker"])
    assert plain == 90 and head < plain   # helmet helps


def test_melee_arc():
    idx = melee_targets((0, 0), 0.0, [(0, 1.5), (0, -1.5), (4, 0)], reach=2.2)
    assert idx == [0]


def test_spread_keeps_unit_length():
    rng = random.Random(3)
    d = spread_direction((0, 0, 1), 3.0, rng)
    assert abs(math.sqrt(sum(c * c for c in d)) - 1) < 1e-6


# ---------------- animation ----------------

def test_anim_clip_sampling_loop(tmp_path):
    clip = AnimClip("t", {"fps": 10, "loop": True,
                          "keys": [[0, {"thigh.L": [0, 0, 0]}],
                                   [10, {"thigh.L": [40, 0, 0]}],
                                   [20, {"thigh.L": [0, 0, 0]}]]})
    mid = clip.sample(0.5)
    assert 5 < mid["thigh.L"][0] < 35
    wrapped = clip.sample(2.5)   # 2s duration -> same as 0.5
    assert abs(wrapped["thigh.L"][0] - mid["thigh.L"][0]) < 1e-6


def test_animator_overlay_and_crossfade():
    import os
    lib = AnimationLibrary(os.path.join("assets", "animations", "zombie_animations.json"))
    a = Animator(lib, base="idle")
    pose = a.update(0.1)
    assert "head" in pose
    a.set_base("walk")
    a.update(0.1)
    a.play_overlay("attack")
    pose = a.update(0.2)
    assert a.overlay_active
    for _ in range(40):
        a.update(0.1)
    assert not a.overlay_active   # one-shot finishes and releases


# ---------------- save system ----------------

def test_save_roundtrip_and_checksum(tmp_path, monkeypatch):
    monkeypatch.setattr(save_system, "user_data_dir", lambda: str(tmp_path))
    state = {"day": 3, "kills": 17, "stats": {"health": 55.0}}
    save_system.save_game(state, "slot1")
    loaded = save_system.load_game("slot1")
    assert loaded == state
    # corrupt the file -> checksum must catch it
    import json
    p = save_system.save_path("slot1")
    env = json.load(open(p, encoding="utf-8"))
    env["state"]["kills"] = 99999
    json.dump(env, open(p, "w", encoding="utf-8"))
    import pytest
    with pytest.raises(ValueError):
        save_system.load_game("slot1")
