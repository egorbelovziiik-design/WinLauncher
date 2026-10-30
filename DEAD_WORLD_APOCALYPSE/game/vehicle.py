"""Arcade drivable vehicles: enter/exit, acceleration/braking/steering, fuel,
damage, wheel spin & steer visuals, zombie run-over. Static wrecks reuse the
same models without physics."""
import json, math, os
from ursina import Entity

VEHICLE_STATS = {
    "sedan":    dict(name="Седан", accel=9.5, max_speed=27, fuel_use=0.55, hp=180),
    "suv":      dict(name="Внедорожник", accel=9.0, max_speed=26, fuel_use=0.7, hp=240),
    "pickup":   dict(name="Пикап", accel=8.2, max_speed=24, fuel_use=0.65, hp=260),
    "military": dict(name="Военный джип", accel=8.8, max_speed=25, fuel_use=0.85, hp=380),
}


class Vehicle:
    def __init__(self, model_name, pos, yaw_deg, game, fuel=25.0, drivable=True):
        self.model_name = model_name
        self.game = game
        self.stats = VEHICLE_STATS.get(model_name, VEHICLE_STATS["sedan"])
        self.drivable = drivable
        self.fuel = fuel
        self.hp = float(self.stats["hp"])
        self.speed = 0.0
        self.steer = 0.0
        self.occupied = False
        self.yaw_deg = float(yaw_deg)

        meta = game.world.manifest["vehicles"][model_name]["meta"]
        self.meta = meta
        self.body = Entity(model=f"assets/models/vehicles/{model_name}/body.obj",
                           position=pos, rotation_y=yaw_deg)
        self.wheels = []
        for off in meta["wheel_offsets"]:
            w = Entity(parent=self.body, model=f"assets/models/vehicles/{model_name}/wheel.obj",
                       position=(off[0], off[1], off[2]))
            self.wheels.append(w)
        self.blocker = None
        if not drivable:
            w = meta["width"]
            l = meta["length"]
            bw, bd = (l, w) if int(yaw_deg) % 180 == 90 else (w, l)
            self.blocker = game.world.add_blocker(pos[0], pos[2], bw, bd, 1.6)
            game.world.obstacles.append((pos[0], pos[2], max(bw, bd) / 2))
        self._last_hit_cooldown = 0.0

    @property
    def pos_xz(self):
        return (self.body.x, self.body.z)

    @property
    def alive(self):
        return self.hp > 0

    def exit_position(self):
        yaw = math.radians(self.yaw_deg)
        # step out to the left of the car
        lx = self.body.x - math.cos(yaw) * -1.6
        lz = self.body.z - math.sin(yaw) * -1.6
        # left vector in engine space (facing +Z rotated by yaw): (-cos, ... ) use side offset:
        side_x = math.cos(yaw)
        side_z = -math.sin(yaw)
        return (self.body.x - side_x * 1.7, 0, self.body.z - side_z * 1.7)

    def update(self, dt, throttle=0.0, steer_input=0.0, brake=False):
        g = self.game
        self._last_hit_cooldown = max(0.0, self._last_hit_cooldown - dt)
        if not self.occupied or not self.alive:
            # rolling to a stop
            self.speed *= max(0.0, 1.0 - dt * 1.8)
            throttle = 0.0
        if self.occupied and self.fuel > 0 and self.alive:
            self.speed += throttle * self.stats["accel"] * dt
            if throttle != 0:
                self.fuel = max(0.0, self.fuel - self.stats["fuel_use"] * dt * (0.4 + abs(self.speed) / 30))
        if brake:
            self.speed -= math.copysign(min(abs(self.speed), 16 * dt), self.speed)
        # drag + clamp
        self.speed *= max(0.0, 1.0 - dt * 0.35)
        max_rev = 8.0
        self.speed = max(-max_rev, min(self.stats["max_speed"], self.speed))
        if self.fuel <= 0:
            self.speed *= max(0.0, 1.0 - dt * 0.9)

        # steering effectiveness fades at high speed
        self.steer += (steer_input - self.steer) * min(1.0, dt * 7.0)
        if abs(self.speed) > 0.2:
            turn = self.steer * 34.0 / (1.0 + abs(self.speed) * 0.14)
            self.yaw_deg += turn * (self.speed / max(4.0, abs(self.speed))) * dt * (1 if self.speed >= 0 else -1) * min(1.0, abs(self.speed) / 4.0) * 4.0

        yaw = math.radians(self.yaw_deg)
        nx = self.body.x + math.sin(yaw) * self.speed * dt
        nz = self.body.z + math.cos(yaw) * self.speed * dt
        # collision: sample the four corners; if any resolves far, count as crash
        half_w = self.meta["width"] / 2
        half_l = self.meta["length"] / 2
        crashed = False
        for cx, cz in ((half_w, half_l), (-half_w, half_l), (half_w, -half_l), (-half_w, -half_l)):
            wx = nx + math.cos(yaw) * cx + math.sin(yaw) * cz
            wz = nz - math.sin(yaw) * cx + math.cos(yaw) * cz
            rx, rz = g.world.resolve_collision((wx, wz), 0.05)
            if abs(rx - wx) > 0.02 or abs(rz - wz) > 0.02:
                crashed = True
        if crashed:
            if abs(self.speed) > 5 and self._last_hit_cooldown <= 0:
                self._last_hit_cooldown = 0.4
                dmg = abs(self.speed) * 1.6
                self.hp -= dmg
                g.audio.play("car_hit", 0.8)
                if self.occupied:
                    g.on_vehicle_crash(dmg * 0.35)
            self.speed *= 0.25
        else:
            self.body.x, self.body.z = nx, nz
        self.body.rotation_y = self.yaw_deg

        # wheels: spin + front steer (offsets order: FL, FR, RL, RR with z forward +)
        spin = -self.speed * dt / max(0.2, self.meta["wheel_radius"]) * 57.3
        for i, w in enumerate(self.wheels):
            w.rotation_x += spin
            if i < 2:
                w.rotation_y = self.steer * 26

        # run-over zombies
        if abs(self.speed) > 2.5:
            for zmb in list(g.zombies):
                if zmb.dead:
                    continue
                if math.dist(self.pos_xz, zmb.pos_xz) < max(half_l, half_w) + 0.4:
                    # transform into car space
                    dx = zmb.pos_xz[0] - self.body.x
                    dz = zmb.pos_xz[1] - self.body.z
                    lx = math.cos(yaw) * dx - math.sin(yaw) * dz
                    lz = math.sin(yaw) * dx + math.cos(yaw) * dz
                    if abs(lx) < half_w + 0.35 and abs(lz) < half_l + 0.35:
                        dmg = 55 + abs(self.speed) * 7
                        dirx = math.sin(yaw) * (1 if self.speed >= 0 else -1)
                        dirz = math.cos(yaw) * (1 if self.speed >= 0 else -1)
                        zmb.damage(dmg, "body", self.pos_xz, (dirx * 2.2, dirz * 2.2))
                        self.hp -= 3
                        g.audio.play_at("car_hit", (self.body.x, 1, self.body.z), g.player_pos(), 0.7)

    def to_dict(self):
        return {"model": self.model_name, "pos": [self.body.x, 0, self.body.z],
                "yaw": self.yaw_deg, "fuel": self.fuel, "hp": self.hp, "drivable": self.drivable}

    def apply_dict(self, d):
        if not d:
            return
        self.body.position = tuple(d.get("pos", self.body.position))
        self.yaw_deg = d.get("yaw", self.yaw_deg)
        self.body.rotation_y = self.yaw_deg
        self.fuel = d.get("fuel", self.fuel)
        self.hp = d.get("hp", self.hp)


def spawn_wrecks(game):
    wrecks = []
    for (x, z, ry) in game.world.wreck_positions:
        v = Vehicle("wrecked_car", (x, 0, z), ry, game, fuel=0, drivable=False)
        # some wrecks missing a wheel / tilted
        if int(x) % 3 == 0:
            v.body.rotation_z = 2.5
        wrecks.append(v)
    return wrecks
