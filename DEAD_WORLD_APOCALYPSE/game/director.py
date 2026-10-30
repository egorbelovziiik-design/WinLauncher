"""Game director: day/night cycle, dynamic weather, spawn director,
horde events, noise propagation, boss trigger at the lab."""
import math, random
from ursina import Entity, color, scene

from game.ai_brain import NoiseEvent
from game.data import ZOMBIE_TYPES, SPAWN_POOLS
from game.config import DAY_LENGTH_SEC

# (hour, sky_rgb, fog_rgb, sun_rgb, sun_intensity, ambient_intensity, fog_density)
SKY_KEYS = [
    (0.0,  (8, 11, 24),   (10, 12, 22),  (40, 55, 90),   0.25, 0.22, 0.013),
    (5.0,  (12, 16, 34),  (16, 18, 32),  (50, 60, 95),   0.3, 0.24, 0.013),
    (6.8,  (96, 92, 110), (110, 96, 92), (255, 160, 90), 0.9, 0.4, 0.010),
    (9.0,  (104, 148, 196), (170, 188, 205), (255, 240, 210), 2.4, 0.62, 0.0055),
    (16.5, (96, 140, 188), (165, 182, 198), (255, 232, 195), 2.2, 0.6, 0.0055),
    (19.3, (120, 86, 96), (128, 96, 90), (255, 140, 80), 0.9, 0.38, 0.010),
    (20.8, (16, 20, 40),  (18, 20, 36),  (60, 70, 105),  0.35, 0.26, 0.013),
    (24.0, (8, 11, 24),   (10, 12, 22),  (40, 55, 90),   0.25, 0.22, 0.013),
]

WEATHERS = ["clear", "clear", "cloudy", "rain", "fog", "storm"]


def _lerp(a, b, t):
    return a + (b - a) * t


def _lerp3(a, b, t):
    return tuple(_lerp(a[i], b[i], t) for i in range(3))


class Director:
    def __init__(self, game):
        self.game = game
        self.rng = random.Random(777)
        self.hour = 8.0
        self.day = 1
        self.darkness = 0.0
        self.weather = "clear"
        self.weather_time = self.rng.uniform(60, 140)
        self.ambient_temp = 12.0
        self.rain_drops = []
        self.thunder_timer = self.rng.uniform(6, 18)
        self.flash = 0.0
        self.spawn_timer = 2.0
        self.horde_timer = self.rng.uniform(170, 260)
        self.boss_spawned = False
        self.night_warned = False
        self._build_rain_pool()

    # ------------------------------------------------------------------ sky
    def sky_at(self, hour):
        keys = SKY_KEYS
        for i in range(len(keys) - 1):
            h0, h1 = keys[i][0], keys[i + 1][0]
            if h0 <= hour <= h1:
                t = 0 if h1 == h0 else (hour - h0) / (h1 - h0)
                a, b = keys[i], keys[i + 1]
                return (_lerp3(a[1], b[1], t), _lerp3(a[2], b[2], t),
                        _lerp3(a[3], b[3], t), _lerp(a[4], b[4], t),
                        _lerp(a[5], b[5], t), _lerp(a[6], b[6], t))
        k = keys[-1]
        return (k[1], k[2], k[3], k[4], k[5], k[6])

    def _build_rain_pool(self):
        n = self.game.settings.particle_count()
        for _ in range(n):
            e = Entity(model="cube", scale=(0.014, 0.55, 0.014),
                       color=color.rgba32(170, 195, 225, 150), enabled=False, unlit=True)
            self.rain_drops.append(e)

    # ------------------------------------------------------------------ noise
    def emit_noise(self, pos_xz, radius, kind="generic"):
        ev = NoiseEvent(pos_xz, radius, kind)
        for z in self.game.zombies:
            if not z.dead:
                z.brain.on_noise(z.pos_xz, ev)

    def screamer_alert(self, pos_xz):
        player = self.game.player_pos()
        target = (player[0], player[2])
        count = 0
        for z in self.game.zombies:
            if not z.dead and math.dist(z.pos_xz, pos_xz) < 75:
                z.brain.alert_to(target)
                count += 1
        self.game.notify("Крикун призвал орду!", bad=True)
        for _ in range(3):
            self.spawn_zombie_near(pos_xz, rush=True)

    # ------------------------------------------------------------------ spawning
    def pick_type(self, zone):
        pool = SPAWN_POOLS.get(zone, SPAWN_POOLS["city"])
        # later days allow nastier mixes
        total = sum(w for _, w in pool)
        r = self.rng.uniform(0, total)
        for tid, w in pool:
            r -= w
            if r <= 0:
                return tid
        return pool[0][0]

    def spawn_zombie(self, type_id, pos_xz):
        from game.character import Zombie
        cfg = dict(ZOMBIE_TYPES[type_id])
        # gentle scaling by day count
        cfg["hp"] = cfg["hp"] * (1.0 + 0.07 * (self.day - 1))
        cfg["speed_chase"] = cfg["speed_chase"] * (1.0 + 0.02 * (self.day - 1))
        z = Zombie(type_id, cfg, (pos_xz[0], 0, pos_xz[1]), self.game)
        self.game.zombies.append(z)
        return z

    def spawn_zombie_near(self, center_xz, rush=False, zone=None):
        a = self.rng.uniform(0, math.tau)
        r = self.rng.uniform(48, 78)
        x = center_xz[0] + math.sin(a) * r
        z = center_xz[1] + math.cos(a) * r
        x, z = self.game.world.resolve_collision((x, z), 0.45)
        tid = self.pick_type(zone or self.game.world.zone_at((x, z)))
        zb = self.spawn_zombie(tid, (x, z))
        if rush:
            p = self.game.player_pos()
            zb.brain.alert_to((p[0], p[2]))
        return zb

    def initial_population(self):
        # themed garrisons around POIs
        w = self.game.world
        garrisons = [("hospital", 7), ("police", 6), ("military", 6),
                     ("lab", 5), ("gas", 3), ("village", 2), ("shop", 3)]
        for zone, n in garrisons:
            cx, cz = w.zones[zone]
            for _ in range(n):
                a = self.rng.uniform(0, math.tau)
                r = self.rng.uniform(6, 20)
                x, z = w.resolve_collision((cx + math.sin(a) * r, cz + math.cos(a) * r), 0.45)
                self.spawn_zombie(self.pick_type(zone if zone in SPAWN_POOLS else "city"), (x, z))
        # a few street walkers far from spawn
        for _ in range(8):
            a = self.rng.uniform(0, math.tau)
            r = self.rng.uniform(45, 110)
            self.spawn_zombie_near((0, 20)) if False else None
            x, z = w.resolve_collision((math.sin(a) * r, 20 + math.cos(a) * r), 0.45)
            self.spawn_zombie(self.pick_type(w.zone_at((x, z))), (x, z))

    def maybe_spawn_boss(self):
        if self.boss_spawned:
            return
        p = self.game.player_pos()
        lab = self.game.world.zones["lab"]
        if math.dist((p[0], p[2]), lab) < 42:
            self.boss_spawned = True
            boss = self.spawn_zombie("zombie_boss", (lab[0] - 8, lab[1] + 14))
            boss.brain.alert_to((p[0], p[2]))
            self.game.audio.play("zombie_scream", 0.9)
            self.game.notify("ГИГАНТ пробудился в лаборатории!", bad=True, big=True)
            self.game.boss_ref = boss

    # ------------------------------------------------------------------ update
    def update(self, dt):
        g = self.game
        # time
        prev_hour = self.hour
        self.hour += dt * 24.0 / DAY_LENGTH_SEC
        if self.hour >= 24.0:
            self.hour -= 24.0
            self.day += 1
            self.night_warned = False
            g.notify(f"День {self.day}. Вы всё ещё живы.", big=True)
            g.audio.play("quest_done", 0.6)
        if prev_hour < 19.0 <= self.hour and not self.night_warned:
            self.night_warned = True
            g.notify("Наступает ночь. Они голоднее ночью...", bad=True)

        sky, fog_c, sun_c, sun_i, amb_i, fog_d = self.sky_at(self.hour)
        self.darkness = max(0.0, min(1.0, 1.0 - (sun_i - 0.25) / 1.4))
        # weather
        self.weather_time -= dt
        if self.weather_time <= 0:
            self.weather = self.rng.choice(WEATHERS)
            self.weather_time = self.rng.uniform(50, 130)
        fog_mult = {"clear": 1.0, "cloudy": 1.7, "rain": 2.1, "fog": 4.2, "storm": 2.6}[self.weather]
        grey = {"clear": 0.0, "cloudy": 0.35, "rain": 0.5, "fog": 0.55, "storm": 0.6}[self.weather]
        sky = _lerp3(sky, (105, 110, 118), grey * (1 - self.darkness * 0.7))
        fog_c = _lerp3(fog_c, (112, 116, 122), grey * (1 - self.darkness * 0.6))

        if self.flash > 0:
            self.flash = max(0.0, self.flash - dt * 6)
            boost = self.flash * 120
            sky = tuple(min(255, c + boost) for c in sky)

        g.apply_environment(sky, fog_c, sun_c, sun_i, amb_i,
                            fog_d * fog_mult if g.settings.fog else fog_d * 0.5,
                            self.hour)
        self.ambient_temp = 15 - 11 * self.darkness - (4 if self.weather in ("rain", "storm") else 0)

        # thunder
        if self.weather == "storm":
            self.thunder_timer -= dt
            if self.thunder_timer <= 0:
                self.thunder_timer = self.rng.uniform(5, 20)
                self.flash = 1.0
                g.audio.play("thunder", self.rng.uniform(0.4, 0.9))

        # rain particles
        raining = self.weather in ("rain", "storm")
        p = g.player_pos()
        for e in self.rain_drops:
            e.enabled = raining
            if raining:
                e.y -= dt * 16.0
                e.x += dt * 2.0
                if e.y < 0 or e.y > 30:
                    e.position = (p[0] + self.rng.uniform(-16, 16),
                                  p[1] + self.rng.uniform(9, 15),
                                  p[2] + self.rng.uniform(-16, 16))
        g.audio.set_loop("rain", 0.55 if raining else 0.0)
        g.audio.set_loop("wind", 0.28 if self.weather in ("storm",) else 0.13)
        g.audio.set_loop("music_ambient", 0.5)

        # lamps
        g.world.set_lamps(self.darkness > 0.55)

        # ---- population control
        self.spawn_timer -= dt
        alive = [z for z in g.zombies if not z.dead]
        target_pop = g.settings.max_zombies()
        if self.darkness > 0.6:
            target_pop = int(target_pop * 1.25)
        # despawn the far-away undead to keep pressure local & perf sane
        pxz = (p[0], p[2])
        for z in list(alive):
            if math.dist(z.pos_xz, pxz) > 165 and not z.cfg.get("boss"):
                z.despawn()
        if self.spawn_timer <= 0:
            self.spawn_timer = 1.6
            alive_count = sum(1 for z in g.zombies if not z.dead)
            if alive_count < target_pop:
                self.spawn_zombie_near(pxz)

        # horde waves
        self.horde_timer -= dt * (1.6 if self.darkness > 0.6 else 1.0)
        if self.horde_timer <= 0:
            self.horde_timer = self.rng.uniform(180, 300)
            n = self.rng.randint(4, 7) + min(4, self.day)
            g.notify("Орда приближается!", bad=True, big=True)
            g.audio.play("zombie_scream", 0.5)
            for _ in range(n):
                if sum(1 for z in g.zombies if not z.dead) < g.settings.max_zombies() + 8:
                    self.spawn_zombie_near(pxz, rush=True)

        self.maybe_spawn_boss()
