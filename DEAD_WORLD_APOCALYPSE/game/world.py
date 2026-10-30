"""Open-world district: city blocks, hospital, police, shops, village, forest,
industrial zone with the lab, military checkpoint, roads, props.
Custom collision registry (AABBs) shared by player, zombies and vehicles.
Enterable houses are built from wall boxes with interactive breakable doors."""
import json, math, os, random
from ursina import Entity, color, destroy

from game.config import WORLD_SIZE
from game.combat import ray_aabb

HALF = WORLD_SIZE / 2.0


class Door:
    def __init__(self, world, pivot, panel, aabb, hp=120.0):
        self.world = world
        self.pivot = pivot
        self.panel = panel
        self.aabb = aabb          # [mn, mx] mutable entry inside world.blockers
        self.hp = hp
        self.open = False
        self.broken = False
        self.angle = 0.0
        self.target_angle = 0.0

    @property
    def pos_xz(self):
        return (self.pivot.x, self.pivot.z)

    def toggle(self):
        if self.broken:
            return
        self.open = not self.open
        self.target_angle = 100.0 if self.open else 0.0
        self.world.game.audio.play_at("door", (self.pivot.x, 1, self.pivot.z),
                                      self.world.game.player_pos(), 0.7)
        self._sync_blocker()

    def smash(self):
        self.broken = True
        self.open = True
        self.target_angle = 115.0
        self.panel.rotation_x = 18
        self._sync_blocker()

    def _sync_blocker(self):
        if self.open or self.broken:
            self.aabb[0] = (0, -10, 0)     # park the AABB underground = no blocking
            self.aabb[1] = (0, -9, 0)
        else:
            mn, mx = self._closed_aabb
            self.aabb[0], self.aabb[1] = mn, mx

    def update(self, dt):
        if abs(self.angle - self.target_angle) > 0.5:
            step = 160 * dt
            self.angle += step if self.target_angle > self.angle else -step
            self.pivot.rotation_y = self.base_yaw + self.angle


class Pickup:
    def __init__(self, item_id, pos, game, model_name=None):
        from game.data import ITEMS
        self.item_id = item_id
        self.game = game
        mdl = model_name or ITEMS[item_id].get("model", "crate")
        if ITEMS[item_id].get("kind") == "weapon":
            path = f"assets/models/weapons/{mdl}.obj"
            scale = 1.6
            y = 0.45
        else:
            path = f"assets/models/environment/{mdl}.obj"
            scale = 1.9
            y = 0.35
        self.entity = Entity(model=path, position=(pos[0], y, pos[2] if len(pos) > 2 else pos[1]),
                             scale=scale)
        self.base_y = self.entity.y
        self.pos_xz = (self.entity.x, self.entity.z)
        self.t = random.uniform(0, 6.28)
        self.taken = False
        # soft glow marker so loot is readable at night
        self.glow = Entity(parent=self.entity, model="circle", scale=0.5,
                           color=color.rgba(255, 220, 120, 60), billboard=True, y=0.1)

    def update(self, dt):
        if self.taken:
            return
        self.t += dt
        self.entity.rotation_y += dt * 55
        self.entity.y = self.base_y + math.sin(self.t * 2.0) * 0.05

    def take(self):
        self.taken = True
        destroy(self.entity)


class World:
    def __init__(self, game):
        self.game = game
        self.rng = random.Random(20240)
        manifest_path = os.path.join(game.assets_path, "manifest.json")
        self.manifest = json.load(open(manifest_path, encoding="utf-8"))
        self.blockers = []       # list of [mn, mx] (x,y,z) tuples-as-lists
        self.obstacles = []      # (x, z, radius) steering circles
        self.doors = []
        self.lamps = []          # glow entities toggled at night
        self.loot_points = []    # (x, z, loot_table)
        self.zones = {
            "hospital": (62, -78), "police": (-64, -72), "gas": (58, 56),
            "lab": (98, 28), "military": (-96, -96), "village": (2, 96),
            "spawn": (0, 20), "shop": (18, -34),
        }
        self.zone_rects = [  # (name, cx, cz, radius) for spawn-pool selection
            ("hospital", 62, -78, 34), ("police", -64, -72, 30),
            ("military", -96, -96, 36), ("industrial", 96, 60, 48),
            ("forest", -110, 40, 55), ("forest", 40, -120, 45),
        ]
        self.signs = []

    # -------------------------------------------------------------- registry
    def add_blocker(self, cx, cz, w, d, height=6.0):
        entry = [(cx - w / 2, 0, cz - d / 2), (cx + w / 2, height, cz + d / 2)]
        self.blockers.append(entry)
        return entry

    def resolve_collision(self, pos_xz, radius, ignore_doors=False):
        x, z = pos_xz
        x = max(-HALF + 1.5, min(HALF - 1.5, x))
        z = max(-HALF + 1.5, min(HALF - 1.5, z))
        for entry in self.blockers:
            mn, mx = entry
            if mx[1] < 0.5:      # parked open-door AABB
                continue
            if mn[0] - radius < x < mx[0] + radius and mn[2] - radius < z < mx[2] + radius:
                dxl = x - (mn[0] - radius)
                dxr = (mx[0] + radius) - x
                dzl = z - (mn[2] - radius)
                dzr = (mx[2] + radius) - z
                m = min(dxl, dxr, dzl, dzr)
                if m == dxl:
                    x = mn[0] - radius
                elif m == dxr:
                    x = mx[0] + radius
                elif m == dzl:
                    z = mn[2] - radius
                else:
                    z = mx[2] + radius
        return (x, z)

    def line_blocked(self, p1, p2):
        dx, dy, dz = p2[0] - p1[0], p2[1] - p1[1], p2[2] - p1[2]
        dist = math.sqrt(dx * dx + dy * dy + dz * dz)
        if dist < 1e-6:
            return False
        direction = (dx / dist, dy / dist, dz / dist)
        for mn, mx in self.blockers:
            if mx[1] < 0.5:
                continue
            t = ray_aabb(p1, direction, mn, mx)
            if t is not None and 0.05 < t < dist - 0.05:
                return True
        return False

    def nearby_obstacles(self, pos_xz, radius):
        return [o for o in self.obstacles
                if abs(o[0] - pos_xz[0]) < radius + o[2] and abs(o[1] - pos_xz[1]) < radius + o[2]]

    def zone_at(self, pos_xz):
        for name, cx, cz, r in self.zone_rects:
            if math.dist(pos_xz, (cx, cz)) < r:
                return name
        return "city"

    # -------------------------------------------------------------- doors
    def nearest_door(self, pos_xz, max_dist=2.6):
        best, bd = None, max_dist
        for d in self.doors:
            if d.open or d.broken:
                continue
            dist = math.dist(pos_xz, d.pos_xz)
            if dist < bd:
                best, bd = d, dist
        return best

    def door_between(self, a_xz, b_xz):
        for d in self.doors:
            if d.open or d.broken:
                continue
            if math.dist(a_xz, d.pos_xz) < 3.2 and math.dist(b_xz, d.pos_xz) < 26:
                return True
        return False

    def damage_door(self, door, dmg):
        door.hp -= dmg
        door.panel.shake(duration=0.18, magnitude=0.05, speed=30)
        if door.hp <= 0 and not door.broken:
            door.smash()
            self.game.notify("Дверь выбита!", bad=True)

    # -------------------------------------------------------------- build
    def build(self, progress=None):
        g = self.game
        T = g.tex
        prog = progress or (lambda *a: None)
        # ground
        ground = Entity(model="plane", scale=(WORLD_SIZE + 60, 1, WORLD_SIZE + 60),
                        texture=T("grass.png"), texture_scale=(110, 110), color=color.rgb(150, 160, 140))
        self.ground = ground
        # roads
        road_specs = []
        for z in (-90, -30, 30, 90):
            road_specs.append((0, z, WORLD_SIZE, 10))
        for x in (-90, -30, 30, 90):
            road_specs.append((x, 0, 10, WORLD_SIZE))
        road_specs.append((0, 0, WORLD_SIZE, 11))
        road_specs.append((0, 0, 11, WORLD_SIZE))
        for (x, z, w, d) in road_specs:
            Entity(model="plane", position=(x, 0.02, z), scale=(w, 1, d),
                   texture=T("asphalt.png"), texture_scale=(max(2, w // 9), max(2, d // 9)),
                   color=color.rgb(120, 120, 125))
        # plaza at spawn
        Entity(model="plane", position=(0, 0.03, 14), scale=(26, 1, 26),
               texture=T("concrete.png"), texture_scale=(8, 8))
        # dirt patches in forest / industrial
        for (x, z, s) in [(-112, 42, 90), (100, 62, 80), (40, -118, 70)]:
            Entity(model="plane", position=(x, 0.015, z), scale=(s, 1, s),
                   texture=T("dirt.png"), texture_scale=(24, 24), color=color.rgb(140, 128, 108))

        steps = [
            ("Строим город...", self._place_buildings),
            ("Строим дома...", lambda: [self._place_enterable((2, 66), "Дом у дороги"),
                                        self._place_enterable((-52, 60), "Дом лесника"),
                                        self._place_enterable((84, -44), "Медпункт"),
                                        self._place_enterable((-88, 16), "Склад-магазин")]),
            ("Заправка и блокпост...", lambda: (self._place_gas_station(), self._place_military())),
            ("Расставляем объекты...", self._place_props),
            ("Сажаем лес...", self._place_forest),
            ("Границы района...", lambda: (self._border(), self._loot_points_init())),
        ]
        for i, (label, fn) in enumerate(steps):
            prog(label, 0.42 + 0.30 * i / len(steps))
            fn()

    def _building(self, name, x, z, yaw_deg, loot=None, sign=None):
        info = self.manifest["buildings"].get(name) or self.manifest["environment"].get(name)
        e = Entity(model=f"assets/models/buildings/{name}.obj", position=(x, 0, z), rotation_y=yaw_deg)
        w, h, d = info["size_xyz_engine"]
        if yaw_deg % 180 == 90:
            w, d = d, w
        self.add_blocker(x, z, w * 0.94, d * 0.94, height=h)
        if sign:
            s = Entity(model="quad", position=(x, h * 0.82, z - (d / 2 + 0.25)),
                       scale=(min(11, w * 0.62), 1.5), texture=self.game.tex(sign), double_sided=True)
            if yaw_deg == 90:
                s.position = (x - (w / 2 + 0.25), h * 0.82, z)
                s.rotation_y = -90
            self.signs.append(s)
        if loot:
            for dx, dz in loot:
                self.loot_points.append((x + dx, z + dz, loot[dx] if False else None))
        return e

    def _loot(self, x, z, table, n=1, spread=1.6):
        for _ in range(n):
            self.loot_points.append((x + self.rng.uniform(-spread, spread),
                                     z + self.rng.uniform(-spread, spread), table))

    def _place_buildings(self):
        B = self._building
        B("hospital", 62, -78, 0, sign="sign_hospital.png")
        self._loot(52, -68, "medical", 3, 4); self._loot(72, -68, "medical", 3, 4)
        self._loot(62, -88, "medical", 2, 5)
        B("police", -64, -72, 0, sign="sign_police.png")
        self._loot(-64, -63, "police", 3, 4); self._loot(-56, -72, "police", 2, 3)
        B("shop", 18, -34, 0, sign="sign_shop.png")
        self._loot(18, -27, "food", 3, 4)
        B("shop", -24, 30, 180, sign="sign_shop.png")
        self._loot(-24, 23, "food", 3, 4)
        B("apartment", 40, -16, 0); self._loot(40, -8, "generic", 2, 5)
        B("apartment", -42, -14, 0); self._loot(-42, -6, "generic", 2, 5)
        B("apartment", 46, 28, 90); self._loot(38, 28, "generic", 2, 4)
        B("apartment2", -50, 40, 90); self._loot(-42, 40, "generic", 2, 4)
        B("apartment2", 14, -60, 0); B("apartment2", -16, -54, 0)
        B("ruined", 72, 6, 0); self._loot(72, 13, "generic", 2, 5)
        B("ruined", -78, -4, 90)
        B("ruined2", 28, 64, 0)
        B("house", -32, 86, 0); B("house2", -10, 94, 0)
        B("village_house", 18, 88, 0); B("village_house", 40, 96, 180)
        B("house", 62, -30, 90)
        B("factory", 98, 92, 0); self._loot(88, 82, "garage", 3, 5)
        B("warehouse", 98, 28, 0, sign="sign_military.png")   # the LAB
        self._loot(90, 40, "weapon_rare", 2, 3); self._loot(106, 18, "military", 2, 3)
        B("warehouse", -98, 86, 0); self._loot(-98, 78, "garage", 3, 5)
        B("apartment", -108, -58, 0); B("apartment2", 108, -84, 90)
        B("house2", -70, 108, 0); B("house", 76, 112, 0)
        self._loot(0, 20, "generic", 1, 2)

    def _place_enterable(self, pos, label):
        """A small house with real interior: walls, roof, breakable door, loot."""
        x, z = pos
        Wd, D, H, t = 8.0, 7.0, 3.1, 0.25
        wall_c = color.rgb(150, 132, 110)
        roof_c = color.rgb(96, 58, 44)

        def wall(w, h, d, px, py, pz, tex="brick.png"):
            e = Entity(model="cube", scale=(w, h, d), position=(px, py, pz),
                       texture=self.game.tex(tex), color=wall_c)
            return e
        # north & south walls (south has door gap at center)
        wall(Wd, H, t, x, H / 2, z - D / 2)
        seg = (Wd - 1.2) / 2
        # south wall: two boxes beside the 1.2m door gap
        wall(seg, H, t, x - 0.6 - seg / 2, H / 2, z + D / 2)
        wall(seg, H, t, x + 0.6 + seg / 2, H / 2, z + D / 2)
        wall(1.2, 0.7, t, x, H - 0.35, z + D / 2)  # lintel
        wall(t, H, D, x - Wd / 2, H / 2, z)
        wall(t, H, D, x + Wd / 2, H / 2, z)
        roof = Entity(model="cube", scale=(Wd + 0.7, 0.18, D + 0.7), position=(x, H + 0.09, z),
                      color=roof_c)
        floor = Entity(model="cube", scale=(Wd, 0.06, D), position=(x, 0.03, z),
                       texture=self.game.tex("wood.png"), color=color.rgb(130, 110, 88))
        # window holes faked with dark quads
        for dx in (-2.4, 2.4):
            Entity(model="quad", scale=(1.1, 1.0), position=(x + dx, 1.7, z - D / 2 - 0.14),
                   color=color.rgb(24, 30, 38), double_sided=True)
        # colliders
        self.add_blocker(x, z - D / 2, Wd, t + 0.15, H)
        self.add_blocker(x - 0.6 - seg / 2, z + D / 2, seg, t + 0.15, H)
        self.add_blocker(x + 0.6 + seg / 2, z + D / 2, seg, t + 0.15, H)
        self.add_blocker(x - Wd / 2, z, t + 0.15, D, H)
        self.add_blocker(x + Wd / 2, z, t + 0.15, D, H)
        # door on hinge pivot
        pivot = Entity(position=(x - 0.6, 0, z + D / 2))
        panel = Entity(parent=pivot, model="cube", scale=(1.15, 2.25, 0.08),
                       position=(0.58, 1.13, 0), texture=self.game.tex("wood.png"),
                       color=color.rgb(110, 78, 50))
        aabb = self.add_blocker(x, z + D / 2, 1.25, 0.35, 2.4)
        door = Door(self, pivot, panel, aabb)
        door.base_yaw = 0.0
        door._closed_aabb = (aabb[0], aabb[1])
        # pivot yaw rotates around hinge; base yaw 0 because panel spans +X
        self.doors.append(door)
        # interior: table + loot
        Entity(model="assets/models/environment/crate.obj", position=(x - 2.6, 0, z - 2.2), scale=1.2)
        Entity(model="assets/models/environment/crate.obj", position=(x + 2.7, 0, z - 2.0), scale=1.0, rotation_y=30)
        Entity(model="assets/models/environment/barrel.obj", position=(x + 3.1, 0, z + 1.8))
        table = "medical" if label == "Медпункт" else ("police" if "Склад" in label else "generic")
        self._loot(x - 1.5, z - 1.5, table, 2, 1.2)
        self._loot(x + 1.8, z + 0.5, "food" if label != "Медпункт" else "medical", 1, 1.0)
        return door

    def _place_gas_station(self):
        x, z = self.zones["gas"]
        Entity(model="assets/models/environment/gas_station.obj", position=(x, 0, z), rotation_y=180)
        self.add_blocker(x, z - 3.2, 6.2, 4.7, 3.1)     # kiosk (rotated 180: now on -Z side)
        for dx, dz in [(-4.4, 4.6), (4.4, 4.6), (-4.4, -0.2), (4.4, -0.2)]:
            self.obstacles.append((x + dx, z + dz, 0.25))
            self.add_blocker(x + dx, z + dz, 0.45, 0.45, 4.3)
        for dx in (-2.2, 2.2):  # pumps
            self.add_blocker(x + dx, z + 2.2, 0.7, 0.55, 1.4)
        Entity(model="quad", position=(x - 5.6, 6.4, z + 4.8), scale=(2.6, 0.9),
               texture=self.game.tex("sign_gas.png"), double_sided=True)
        self._loot(x + 1, z - 1, "garage", 3, 2)
        self._loot(x - 3, z + 5.6, "garage", 2, 1.5)

    def _place_military(self):
        x, z = self.zones["military"]
        for dx, dz, ry in [(-6, -4, 20), (4, -6, -15), (-2, 6, 80)]:
            Entity(model="assets/models/environment/military_tent.obj",
                   position=(x + dx, 0, z + dz), rotation_y=ry)
            self.add_blocker(x + dx, z + dz, 3.0, 2.6, 1.8)
        for dx, dz, ry in [(-9, 1, 90), (9, 2, 90), (0, 10, 0), (-5, 10, 0), (5, 10, 0)]:
            Entity(model="assets/models/environment/sandbag_wall.obj",
                   position=(x + dx, 0, z + dz), rotation_y=ry)
            w, d = (0.9, 2.6) if ry == 90 else (2.6, 0.9)
            self.add_blocker(x + dx, z + dz, w, d, 1.2)
        Entity(model="quad", position=(x, 3.4, z + 11), scale=(6, 1.2),
               texture=self.game.tex("sign_military.png"), double_sided=True)
        self._loot(x, z, "military", 4, 4)
        self._loot(x - 5, z + 4, "military", 2, 2)

    def _place_props(self):
        rng = self.rng
        # street lamps along avenues
        lamp_pos = []
        for k in range(-160, 170, 36):
            lamp_pos += [(k, -6.5), (k + 18, 6.5), (-6.5, k + 9), (6.5, k - 9)]
        for (x, z) in lamp_pos:
            ry = 0 if abs(z) == 6.5 else 90
            Entity(model="assets/models/environment/street_lamp.obj", position=(x, 0, z), rotation_y=ry)
            self.obstacles.append((x, z, 0.18))
            glow = Entity(model="sphere", scale=0.55, position=(x + (0 if ry == 0 else 1.05), 4.72,
                          z + (1.05 if ry == 0 else 0)), color=color.rgba(255, 214, 140, 120), enabled=False)
            self.lamps.append(glow)
        # containers / crates / barrels clusters
        clusters = [(90, 60), (104, 44), (-90, 74), (30, -12), (-30, -70), (76, -58),
                    (-70, 96), (12, 44), (-58, -94), (86, 104)]
        for (cx, cz) in clusters:
            kind = rng.choice(["container", "crates", "barrels"])
            if kind == "container":
                ry = rng.choice([0, 90])
                Entity(model="assets/models/environment/container.obj",
                       position=(cx, 0, cz), rotation_y=ry)
                w, d = (2.5, 6.1) if ry == 90 else (6.1, 2.5)
                self.add_blocker(cx, cz, w, d, 2.6)
            elif kind == "crates":
                for i in range(rng.randint(2, 4)):
                    px, pz = cx + rng.uniform(-1.2, 1.2), cz + rng.uniform(-1.2, 1.2)
                    Entity(model="assets/models/environment/crate.obj",
                           position=(px, 0, pz), rotation_y=rng.uniform(0, 90), scale=rng.uniform(0.9, 1.4))
                    self.obstacles.append((px, pz, 0.5))
            else:
                for i in range(rng.randint(2, 5)):
                    px, pz = cx + rng.uniform(-1.4, 1.4), cz + rng.uniform(-1.4, 1.4)
                    Entity(model="assets/models/environment/barrel.obj", position=(px, 0, pz))
                    self.obstacles.append((px, pz, 0.35))
        # barricades & fences
        for (x, z, ry) in [(20, -2, 90), (-36, 4, 90), (4, -62, 0), (-6, 70, 0), (52, -92, 0)]:
            Entity(model="assets/models/environment/barricade.obj", position=(x, 0, z), rotation_y=ry)
            w, d = (0.8, 3.4) if ry == 90 else (3.4, 0.8)
            self.add_blocker(x, z, w, d, 1.4)
        for (x, z, ry) in [(-20, 78, 0), (-14, 78, 0), (24, 102, 0), (30, 102, 0), (-96, -84, 0), (-90, -84, 0)]:
            Entity(model="assets/models/environment/fence.obj", position=(x, 0, z), rotation_y=ry)
            self.add_blocker(x, z, 3.0, 0.25, 1.25)
        # wrecked cars on roads
        self.wreck_positions = []
        for i in range(13):
            if i % 2:
                x, z = rng.uniform(-160, 160), rng.choice([-2.5, 2.5]) + rng.choice([0, -30, 30, -90, 90]) * 0
                z = rng.choice([0, -30, 30]) + rng.uniform(-3, 3)
            else:
                x = rng.choice([0, -30, 30]) + rng.uniform(-3, 3)
                z = rng.uniform(-160, 160)
            if math.dist((x, z), (0, 20)) < 14:
                continue
            self.wreck_positions.append((x, z, rng.choice([0, 0, 90, 180])))

    def _place_forest(self):
        rng = self.rng
        placed = 0
        tree_models = ["tree", "tree2", "pine", "pine2", "dead_tree"]
        attempts = 0
        while placed < 130 and attempts < 900:
            attempts += 1
            # bias towards forest zones but scatter everywhere green
            if rng.random() < 0.62:
                cx, cz = rng.choice([(-112, 42), (40, -118), (-60, 130), (120, 130), (-130, -60)])
                x = cx + rng.gauss(0, 26)
                z = cz + rng.gauss(0, 26)
            else:
                x = rng.uniform(-HALF + 6, HALF - 6)
                z = rng.uniform(-HALF + 6, HALF - 6)
            if abs(x) > HALF - 4 or abs(z) > HALF - 4:
                continue
            if self._on_road(x, z) or self._inside_blocker(x, z, pad=3.2):
                continue
            name = rng.choice(tree_models)
            s = rng.uniform(0.8, 1.5)
            Entity(model=f"assets/models/environment/{name}.obj", position=(x, 0, z),
                   rotation_y=rng.uniform(0, 360), scale=s)
            self.obstacles.append((x, z, 0.45 * s))
            self.add_blocker(x, z, 0.55 * s, 0.55 * s, 3)
            placed += 1
        rocks = 0
        attempts = 0
        while rocks < 36 and attempts < 400:
            attempts += 1
            x = rng.uniform(-HALF + 6, HALF - 6)
            z = rng.uniform(-HALF + 6, HALF - 6)
            if self._on_road(x, z) or self._inside_blocker(x, z, pad=2.0):
                continue
            s = rng.uniform(0.5, 2.0)
            Entity(model=f"assets/models/environment/{rng.choice(['rock', 'rock2'])}.obj",
                   position=(x, 0, z), rotation_y=rng.uniform(0, 360), scale=s)
            self.obstacles.append((x, z, 0.7 * s))
            rocks += 1

    def _on_road(self, x, z):
        for c in (-90, -30, 0, 30, 90):
            if abs(z - c) < 7.5 or abs(x - c) < 7.5:
                return True
        return False

    def _inside_blocker(self, x, z, pad=0.0):
        for mn, mx in self.blockers:
            if mn[0] - pad < x < mx[0] + pad and mn[2] - pad < z < mx[2] + pad:
                return True
        return False

    def _border(self):
        # broken perimeter fence visual (collision handled by resolve clamp)
        for k in range(-175, 180, 15):
            if self.rng.random() < 0.4:
                continue
            Entity(model="assets/models/environment/fence.obj",
                   position=(k, 0, -HALF + 1), scale=(5, 1.6, 1))
            Entity(model="assets/models/environment/fence.obj",
                   position=(k, 0, HALF - 1), scale=(5, 1.6, 1))
            Entity(model="assets/models/environment/fence.obj",
                   position=(-HALF + 1, 0, k), rotation_y=90, scale=(5, 1.6, 1))
            Entity(model="assets/models/environment/fence.obj",
                   position=(HALF - 1, 0, k), rotation_y=90, scale=(5, 1.6, 1))

    def _loot_points_init(self):
        rng = self.rng
        # scattered street loot
        for _ in range(46):
            x = rng.uniform(-HALF + 10, HALF - 10)
            z = rng.uniform(-HALF + 10, HALF - 10)
            if self._inside_blocker(x, z, pad=1.2):
                continue
            self.loot_points.append((x, z, rng.choice(
                ["generic", "generic", "food", "garage"])))

    # -------------------------------------------------------------- per-frame
    def update(self, dt):
        for d in self.doors:
            d.update(dt)

    def set_lamps(self, on):
        for gl in self.lamps:
            gl.enabled = on
