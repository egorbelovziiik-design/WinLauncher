"""Humanoid entities built from the Blender part-kits: joint-pivot hierarchy
driven by the animation state machine. Zombies (brain + combat lifecycle)
and friendly survivor NPCs."""
import json, math, os, random
from ursina import Entity, color, destroy

from game.ai_brain import ZombieBrain, steer, DEAD as BRAIN_DEAD, CHASE, ATTACK
from game.anim import Animator, JOINT_TO_PART, joint_to_engine_euler
from game.combat import HitZone

_skeleton_cache = {}


def load_skeleton(kind, name, assets_path):
    key = (kind, name)
    if key not in _skeleton_cache:
        path = os.path.join(assets_path, "models", kind, name, "skeleton.json")
        with open(path, encoding="utf-8") as f:
            _skeleton_cache[key] = json.load(f)
    return _skeleton_cache[key]


def build_humanoid_entities(kind, name, assets_path, parent_pos=(0, 0, 0), yaw_deg=0):
    """Create the part hierarchy. Part geometry hangs below each joint pivot;
    rotations from the animator produce the skeletal motion."""
    skel = load_skeleton(kind, name, assets_path)
    root = Entity(position=parent_pos, rotation_y=yaw_deg)
    parts = {}
    for part_name, info in skel["parts"].items():
        file = part_name.replace(".", "_") + ".obj"
        parent = parts[info["parent"]] if info["parent"] in parts else root
        off = info["rest_offset"]
        e = Entity(parent=parent,
                   model=f"assets/models/{kind}/{name}/{file}",
                   position=(off[0], off[1], off[2]))
        # vertex colors carry the materials; keep entity color white.
        parts[part_name] = e
    return root, parts, skel


class HumanoidAnimator:
    """Applies Animator poses to the part hierarchy incl. locomotion bob."""

    def __init__(self, parts, library, base="idle"):
        self.parts = parts
        self.animator = Animator(library, base=base)
        self.bob_phase = 0.0
        self.pelvis_rest_y = parts["pelvis"].y

    def apply(self, dt, speed_ratio=0.0):
        pose = self.animator.update(dt)
        for joint, part_name in JOINT_TO_PART.items():
            e = self.parts.get(part_name)
            if e is None:
                continue
            ex, ey, ez = joint_to_engine_euler(joint, pose[joint])
            e.rotation = (ex, ey, ez)
        # subtle vertical bob while moving (root motion feel)
        if speed_ratio > 0.05:
            self.bob_phase += dt * (6.0 + 5.0 * speed_ratio)
            self.parts["pelvis"].y = self.pelvis_rest_y + abs(math.sin(self.bob_phase)) * 0.045
        else:
            self.parts["pelvis"].y = self.pelvis_rest_y


class Zombie:
    """Full zombie: model kit + brain + health/damage lifecycle."""

    _uid = 0

    def __init__(self, type_id, cfg, pos, game):
        Zombie._uid += 1
        self.uid = Zombie._uid
        self.type_id = type_id
        self.cfg = cfg
        self.game = game
        self.rng = random.Random(self.uid * 7919)
        self.hp = float(cfg["hp"])
        self.max_hp = self.hp
        self.dead = False
        self.death_time = 0.0
        self.despawned = False
        self.yaw_rad = self.rng.uniform(0, math.tau)
        self.knockback = [0.0, 0.0]
        self.groan_timer = self.rng.uniform(2, 9)
        self.lunge_timer = 0.0
        self.leap_vel = 0.0
        self.height_off = 0.0
        self.attack_anim_cooldown = 0.0
        self.fell = False

        self.root, self.parts, self.skel = build_humanoid_entities(
            "zombies", type_id, game.assets_path, parent_pos=pos)
        self.scale = self.skel.get("scale", 1.0)
        if cfg.get("crawler"):
            # crawler drags along the ground: lower the pelvis visually by
            # pitching the torso assembly (handled per-frame via pose) and
            # dropping the root hit profile.
            self.root.y = 0.0
        base_anim = "walk" if not cfg.get("crawler") else "crawl"
        self.ha = HumanoidAnimator(self.parts, game.anim_library, base="idle")
        self.ha.animator.speed = cfg.get("anim_speed", 1.0)
        self.walk_anim = cfg.get("anim_walk", "walk")

        self.brain = ZombieBrain(cfg, rng=self.rng)
        self.brain.home = (pos[0], pos[2])
        self.pos_xz = (float(pos[0]), float(pos[2]))
        self.radius = 0.42 * self.scale

    # ------------------------------------------------------------------ helpers
    @property
    def pos(self):
        return (self.root.x, self.root.y, self.root.z)

    @property
    def eye_pos(self):
        return (self.root.x, self.root.y + 1.5 * self.scale, self.root.z)

    def hit_zones(self):
        if self.dead:
            return []
        head_e = self.parts.get("head")
        hp = head_e.world_position if head_e else None
        zones = []
        if self.cfg.get("crawler"):
            zones.append(HitZone(self, "body", (self.root.x, 0.55, self.root.z), 0.5 * self.scale, 1.0))
            if hp is not None:
                zones.append(HitZone(self, "head", (hp.x, max(0.35, hp.y), hp.z), 0.17, 3.0))
            return zones
        if hp is not None:
            zones.append(HitZone(self, "head", (hp.x, hp.y + 0.08, hp.z), 0.175 * self.scale, 3.0))
        zones.append(HitZone(self, "body", (self.root.x, 1.15 * self.scale, self.root.z), 0.36 * self.scale, 1.0))
        zones.append(HitZone(self, "limb", (self.root.x, 0.55 * self.scale, self.root.z), 0.34 * self.scale, 0.65))
        return zones

    # ------------------------------------------------------------------ damage
    def damage(self, amount, zone, attacker_pos, direction_xz=(0, 0)):
        if self.dead:
            return False
        self.hp -= amount
        resist = self.cfg.get("knock_resist", 0.0)
        kb = min(2.6, amount / 28.0) * (1.0 - resist)
        self.knockback[0] += direction_xz[0] * kb
        self.knockback[1] += direction_xz[1] * kb
        heavy = amount >= 45 or zone == "head"
        self.brain.on_damaged(attacker_pos, heavy=heavy)
        if self.hp <= 0:
            self.die(headshot=(zone == "head"))
            return True
        self.ha.animator.play_overlay("stagger" if heavy else "hit")
        self.game.audio.play_at("hit_flesh", self.pos, self.game.player_pos(), 0.7)
        return False

    def die(self, headshot=False):
        self.dead = True
        self.brain.state = BRAIN_DEAD
        self.death_time = 0.0
        self.ha.animator.play_overlay("headshot_death" if headshot else "death")
        self.game.audio.play_at("zombie_death", self.pos, self.game.player_pos(), 0.8)
        self.game.on_zombie_killed(self, headshot)

    # ------------------------------------------------------------------ update
    def update(self, dt):
        g = self.game
        if self.despawned:
            return
        if self.dead:
            self.death_time += dt
            self.ha.apply(dt, 0.0)
            # topple the whole body, then sink and despawn
            if self.death_time > 0.25 and not self.fell:
                self.fell = True
            if self.fell:
                target_pitch = -88 if self.uid % 2 else 88
                cur = self.root.rotation_x
                self.root.rotation_x = cur + (target_pitch - cur) * min(1.0, dt * 3.2)
                self.root.y = max(0.15, self.root.y - dt * 0.9)
            if self.death_time > 18.0:
                self.root.y -= dt * 0.25
            if self.death_time > 22.0:
                self.despawn()
            return

        player_pos = g.player_pos()
        pxz = (player_pos[0], player_pos[2])
        darkness = g.director.darkness if g.director else 0.0
        blocked = g.world.line_blocked(self.eye_pos, (player_pos[0], player_pos[1] + 1.2, player_pos[2]))
        door_way = g.world.door_between(self.pos_xz, pxz)
        prev_state = self.brain.state
        self.brain.update(dt, self.pos_xz, self.yaw_rad, pxz,
                          player_alive=g.player_alive(),
                          player_crouched=g.player_crouched(),
                          darkness=darkness, line_blocked=blocked,
                          door_in_way=door_way)
        state = self.brain.state

        # screamer: first time it spots the player, it screams and calls the horde
        if self.cfg.get("screamer") and state == CHASE and prev_state != CHASE and not self.brain.screamed:
            self.brain.screamed = True
            self.ha.animator.play_overlay("scream")
            g.audio.play_at("zombie_scream", self.pos, player_pos, 1.0, max_dist=90)
            g.director.screamer_alert(self.pos_xz)

        # mutant leap attack
        d_player = math.dist(self.pos_xz, pxz)
        speed = self.brain.speed()
        target = self.brain.move_target
        if self.cfg.get("leap") and state == CHASE and 3.0 < d_player < 6.5 and self.lunge_timer <= 0:
            self.lunge_timer = 3.5
            self.leap_vel = 3.4
            speed *= 2.1
        self.lunge_timer = max(0.0, self.lunge_timer - dt)

        # movement + collisions
        obstacles = g.world.nearby_obstacles(self.pos_xz, 4.0)
        separation = [z.pos_xz for z in g.zombies if z is not self and not z.dead
                      and abs(z.pos_xz[0] - self.pos_xz[0]) < 2 and abs(z.pos_xz[1] - self.pos_xz[1]) < 2]
        new_pos, new_yaw = steer(self.pos_xz, self.yaw_rad, target, speed, dt,
                                 obstacles=obstacles, separation=separation)
        # knockback decay
        new_pos = (new_pos[0] + self.knockback[0] * dt * 3.0,
                   new_pos[1] + self.knockback[1] * dt * 3.0)
        self.knockback[0] *= max(0.0, 1.0 - dt * 6.0)
        self.knockback[1] *= max(0.0, 1.0 - dt * 6.0)
        resolved = g.world.resolve_collision(new_pos, self.radius, ignore_doors=False)
        self.pos_xz = resolved
        # face movement / player
        if state == ATTACK:
            desired = math.atan2(pxz[0] - self.pos_xz[0], pxz[1] - self.pos_xz[1])
            from game.ai_brain import angle_lerp
            self.yaw_rad = angle_lerp(self.yaw_rad, desired, dt * 6.0)
        else:
            self.yaw_rad = new_yaw

        # leap vertical
        if self.leap_vel > 0 or self.height_off > 0:
            self.height_off += self.leap_vel * dt
            self.leap_vel -= 11.0 * dt
            if self.height_off <= 0:
                self.height_off = 0.0
                self.leap_vel = 0.0

        self.root.position = (self.pos_xz[0], self.height_off, self.pos_xz[1])
        self.root.rotation_y = math.degrees(self.yaw_rad)

        # door bashing
        if door_way and state in (CHASE, ATTACK):
            door = g.world.nearest_door(self.pos_xz, 2.0)
            if door is not None and self.attack_anim_cooldown <= 0:
                self.attack_anim_cooldown = 1.1
                self.ha.animator.play_overlay("attack")
                g.world.damage_door(door, self.cfg["damage"] * 1.4)
                g.audio.play_at("door", self.pos, player_pos, 0.6)

        # attacking the player / vehicle
        self.attack_anim_cooldown = max(0.0, self.attack_anim_cooldown - dt)
        if state == ATTACK and self.brain.attack_cooldown <= 0:
            self.brain.attack_cooldown = 1.15
            self.ha.animator.play_overlay("attack")
            g.audio.play_at("zombie_attack", self.pos, player_pos, 0.85)
            g.on_zombie_attack(self)

        # animation state
        if speed > 0.1:
            anim = self.walk_anim if state == CHASE else ("crawl" if self.cfg.get("crawler") else "walk")
            self.ha.animator.set_base(anim)
            ratio = min(1.2, speed / max(1.0, self.cfg["speed_chase"]))
        elif state in ("search",):
            self.ha.animator.set_base("search")
            ratio = 0.0
        else:
            self.ha.animator.set_base("idle")
            ratio = 0.0
        # LOD: far zombies animate at reduced rate (handled by director cadence too)
        self.ha.apply(dt, ratio)

        # crawler body pitch: drag torso forward-down
        if self.cfg.get("crawler"):
            self.parts["torso"].rotation_x += 52
            self.parts["pelvis"].y = self.ha.pelvis_rest_y - 0.52 * self.scale

        # ambience groans
        self.groan_timer -= dt
        if self.groan_timer <= 0:
            self.groan_timer = self.rng.uniform(4, 14)
            if d_player < 40:
                g.audio.play_at(self.rng.choice(["zombie_groan1", "zombie_groan2"]),
                                self.pos, player_pos, 0.55)

    def despawn(self):
        if self.despawned:
            return
        self.despawned = True
        try:
            destroy(self.root)
        except Exception:
            pass
        if self in self.game.zombies:
            self.game.zombies.remove(self)


class SurvivorNPC:
    """Friendly NPC: idle/wander around a safe zone, trader gives a hint line."""

    LINES = {
        "trader": ["Торговец: «В больнице ещё остались антибиотики. Наверное.»",
                   "Торговец: «Ночью они злее. Держись света, друг.»",
                   "Торговец: «У заправки стоит пикап. Нужен только бензин.»"],
        "soldier_npc": ["Военный: «Промзона — мёртвая зона. Там завелось нечто огромное.»",
                        "Военный: «Целься в голову. В тело — только патроны переводить.»"],
        "_default": ["Выживший: «Мы держимся здесь, пока можем.»",
                     "Выживший: «Слышал крикуна? Значит, скоро здесь будет вся округа.»"],
    }

    def __init__(self, model_name, pos, game):
        self.model_name = model_name
        self.game = game
        self.rng = random.Random(hash(model_name) & 0xFFFF)
        self.home = (pos[0], pos[2])
        self.pos_xz = (float(pos[0]), float(pos[2]))
        self.yaw_rad = self.rng.uniform(0, math.tau)
        self.target = None
        self.wait = self.rng.uniform(2, 6)
        self.root, self.parts, self.skel = build_humanoid_entities(
            "characters", model_name, game.assets_path, parent_pos=pos)
        self.ha = HumanoidAnimator(self.parts, game.anim_library, base="human_idle")

    def line(self):
        lines = self.LINES.get(self.model_name, self.LINES["_default"])
        return self.rng.choice(lines)

    def update(self, dt):
        self.wait -= dt
        speed_ratio = 0.0
        if self.target is not None:
            d = math.dist(self.pos_xz, self.target)
            if d < 0.4:
                self.target = None
                self.wait = self.rng.uniform(3, 8)
                self.ha.animator.set_base("human_idle")
            else:
                from game.ai_brain import angle_lerp
                desired = math.atan2(self.target[0] - self.pos_xz[0], self.target[1] - self.pos_xz[1])
                self.yaw_rad = angle_lerp(self.yaw_rad, desired, dt * 4.0)
                step = min(d, 1.35 * dt)
                np = (self.pos_xz[0] + math.sin(self.yaw_rad) * step,
                      self.pos_xz[1] + math.cos(self.yaw_rad) * step)
                self.pos_xz = self.game.world.resolve_collision(np, 0.4)
                speed_ratio = 0.5
                self.ha.animator.set_base("human_walk")
        elif self.wait <= 0:
            a = self.rng.uniform(0, math.tau)
            r = self.rng.uniform(2, 7)
            self.target = (self.home[0] + math.sin(a) * r, self.home[1] + math.cos(a) * r)
        self.root.position = (self.pos_xz[0], 0, self.pos_xz[1])
        self.root.rotation_y = math.degrees(self.yaw_rad)
        self.ha.apply(dt, speed_ratio)
