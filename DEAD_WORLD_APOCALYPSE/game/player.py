"""First-person player: movement (walk/sprint/crouch/jump), survival coupling,
weapon handling (hitscan guns + melee), viewmodel animation, interaction."""
import math, random
from ursina import Entity, camera, color, held_keys, mouse, destroy, SpotLight

from game.combat import hitscan, HitZone, spread_direction, apply_damage, melee_targets
from game.data import WEAPONS
from game.config import GRAVITY, PLAYER_EYE

WALK, SPRINT, CROUCH = 4.4, 7.4, 2.1


class Player:
    def __init__(self, game):
        self.game = game
        self.rng = random.Random(5)
        sx, sz = game.world.zones["spawn"]
        self.root = Entity(position=(sx, 0, sz))
        self.head = Entity(parent=self.root, position=(0, PLAYER_EYE, 0))
        self.yaw_deg = 180.0     # face the plaza / city center (-Z is north, spawn looks north)
        self.pitch_deg = 0.0
        self.vel_y = 0.0
        self.grounded = True
        self.crouched = False
        self.sprinting = False
        self.moving = False
        self.bob_phase = 0.0
        self.step_phase = 0.0
        self.vehicle = None
        self.cam_shake = 0.0

        camera.parent = self.head
        camera.position = (0, 0, 0)
        camera.rotation = (0, 0, 0)
        camera.fov = 76

        # flashlight
        self.flashlight = SpotLight(parent=self.head, position=(0.15, -0.1, 0.2))
        self.flashlight.color = color.rgb32(255, 240, 205)
        try:
            self.flashlight._light.setAttenuation((0.4, 0.08, 0.02))
            lens = self.flashlight._light.get_lens()
            lens.setFov(38)
            lens.setNearFar(0.5, 45)
        except Exception:
            pass
        self.flashlight.enabled = False
        self.flashlight_on = False

        # viewmodel
        self.vm_holder = Entity(parent=camera, position=(0.26, -0.24, 0.55))
        self.viewmodel = None
        self.current_weapon = None
        self.fire_cooldown = 0.0
        self.reload_left = 0.0
        self.reload_weapon = None
        self.swing_t = 0.0
        self.swing_hit_done = True
        self.recoil = 0.0
        self.aiming = False
        self.muzzle = Entity(parent=camera, model="quad", scale=0.22,
                             color=color.rgba32(255, 255, 255, 0), billboard=True,
                             double_sided=True, enabled=False)
        self.muzzle.texture = game.tex("muzzle_flash.png")
        self.muzzle_t = 0.0
        # tracers pool
        self.tracers = []
        for _ in range(10):
            t = Entity(model="cube", color=color.rgba32(255, 230, 160, 160), enabled=False, unlit=True)
            self.tracers.append({"e": t, "ttl": 0.0})
        # blood decals pool
        self.blood = []
        for _ in range(36):
            b = Entity(model="quad", rotation_x=90, y=0.045, color=color.rgba32(255, 255, 255, 235),
                       enabled=False)
            b.texture = game.tex("blood_splat.png")
            self.blood.append(b)
        self.blood_i = 0

    # ------------------------------------------------------------------ basics
    @property
    def pos(self):
        return (self.root.x, self.root.y, self.root.z)

    @property
    def pos_xz(self):
        return (self.root.x, self.root.z)

    def eye(self):
        p = self.head.world_position
        return (p.x, p.y, p.z)

    def forward(self):
        yaw = math.radians(self.yaw_deg)
        pitch = math.radians(self.pitch_deg)   # pitch_deg stores rotation_x (down = +)
        return (math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch))

    def set_position(self, pos, yaw=None):
        self.root.position = (pos[0], pos[1] if len(pos) > 2 else 0, pos[2] if len(pos) > 2 else pos[1])
        if yaw is not None:
            self.yaw_deg = yaw

    # ------------------------------------------------------------------ weapons
    def equip(self, weapon_id):
        if weapon_id == self.current_weapon:
            return
        self.current_weapon = weapon_id
        self.reload_left = 0.0
        self.swing_t = 0.0
        if self.viewmodel:
            destroy(self.viewmodel)
            self.viewmodel = None
        if weapon_id:
            meta = self.game.weapon_view_meta.get(weapon_id, {})
            self.viewmodel = Entity(parent=self.vm_holder,
                                    model=f"assets/models/weapons/{weapon_id}.obj",
                                    scale=meta.get("scale", 1.0),
                                    position=meta.get("pos", (0, 0, 0)),
                                    rotation=meta.get("rot", (0, 0, 0)))
            self._vm_base_pos = self.vm_holder.position
        self.game.refresh_hotbar_ui()

    def select_slot(self, idx):
        inv = self.game.inventory
        if 0 <= idx < len(inv.hotbar):
            item = inv.hotbar[idx]
            if item and item in WEAPONS:
                self.equip(item)
            elif item:
                self.game.use_item_from_hotbar(item)
            self.game.selected_slot = idx
            self.game.refresh_hotbar_ui()

    def cycle_weapon(self, direction):
        owned = [i for i, h in enumerate(self.game.inventory.hotbar) if h in WEAPONS]
        if not owned:
            return
        cur = self.game.selected_slot if self.game.selected_slot in owned else owned[0]
        k = owned.index(cur)
        self.select_slot(owned[(k + direction) % len(owned)])

    def start_reload(self):
        wid = self.current_weapon
        if not wid:
            return
        w = WEAPONS[wid]
        if w["kind"] != "gun" or self.reload_left > 0:
            return
        inv = self.game.inventory
        if inv.mags.get(wid, 0) >= w["mag"] or inv.ammo.get(w["ammo"], 0) <= 0:
            self.game.audio.play("dry_fire", 0.5)
            return
        self.reload_left = w["reload_time"]
        self.reload_weapon = wid
        self.game.audio.play("reload", 0.75)

    def try_fire(self):
        g = self.game
        wid = self.current_weapon
        if not wid or self.fire_cooldown > 0 or self.reload_left > 0:
            return
        w = WEAPONS[wid]
        inv = g.inventory
        if w["kind"] == "melee":
            if g.stats.stamina < w.get("stamina", 6) * 0.4:
                return
            self.fire_cooldown = 1.0 / w["rate"]
            self.swing_t = 0.32
            self.swing_hit_done = False
            g.stats.stamina = max(0.0, g.stats.stamina - w.get("stamina", 6) * 0.55)
            g.audio.play(w["sound_swing"], 0.6)
            return
        # gun
        if inv.mags.get(wid, 0) <= 0:
            self.fire_cooldown = 0.28
            g.audio.play("dry_fire", 0.6)
            self.start_reload()
            return
        inv.fire_round(wid)
        self.fire_cooldown = 1.0 / w["rate"]
        self.recoil = min(1.6, self.recoil + w["recoil"] * 0.5)
        self.pitch_deg -= w["recoil"] * 0.55      # kick up
        self.cam_shake = min(0.5, self.cam_shake + w["recoil"] * 0.06)
        g.audio.play(w["sound"], 0.95)
        g.director.emit_noise(self.pos_xz, w["noise"], "gunshot")
        self._show_muzzle()
        pellets = w.get("pellets", 1)
        spread = w["spread"] * (0.45 if self.aiming else 1.0) * (1.0 if not self.sprinting else 1.7)
        eye = self.eye()
        base_dir = self.forward()
        zones = []
        for z in g.zombies:
            zones.extend(z.hit_zones())
        blockers = [(mn, mx) for mn, mx in g.world.blockers if mx[1] >= 0.4]
        for _ in range(pellets):
            direction = spread_direction(base_dir, spread, self.rng)
            zone, point, dist = hitscan(eye, direction, w["range"], zones, blockers)
            self._spawn_tracer(eye, point)
            if zone is not None:
                zmb = zone.zombie
                dmg = apply_damage(w["damage"], zone.zone, zmb.cfg, distance=dist)
                killed = zmb.damage(dmg, zone.zone, self.pos_xz,
                                     (direction[0], direction[2]))
                g.hud.show_hitmarker(kill=killed)
                g.audio.play("hit_flesh", 0.35)
                self._blood_splat((zmb.root.x, zmb.root.z))
                if zone.zone == "head" and killed:
                    g.headshots += 1

    def _melee_strike(self):
        g = self.game
        wid = self.current_weapon
        w = WEAPONS[wid]
        zpos = [z.pos_xz for z in g.zombies if not z.dead]
        idxs = melee_targets(self.pos_xz, math.radians(self.yaw_deg), zpos, w["range"])
        alive = [z for z in g.zombies if not z.dead]
        hit_any = False
        for i in idxs:
            zmb = alive[i]
            # height check: aiming up helps head hits with edged weapons
            zone = "head" if self.pitch_deg < -6 and wid in ("axe", "machete") else "body"
            dmg = apply_damage(w["damage"], zone, zmb.cfg)
            fwd = self.forward()
            kb = w.get("knockback", 1.4)
            killed = zmb.damage(dmg, zone, self.pos_xz, (fwd[0] * kb, fwd[2] * kb))
            g.hud.show_hitmarker(kill=killed)
            if zone == "head" and killed:
                g.headshots += 1
            self._blood_splat(zmb.pos_xz)
            hit_any = True
        if hit_any:
            g.audio.play(w["sound_hit"], 0.8)
            g.director.emit_noise(self.pos_xz, 7, "melee")
        # doors can be chopped too
        door = g.world.nearest_door(self.pos_xz, 2.4)
        if door and not hit_any:
            g.world.damage_door(door, w["damage"] * 0.8)
            g.audio.play("door", 0.5)

    def _show_muzzle(self):
        meta = self.game.weapon_view_meta.get(self.current_weapon, {})
        mz = meta.get("muzzle", (0.26, -0.19, 1.1))
        self.muzzle.position = mz
        self.muzzle.rotation_z = self.rng.uniform(0, 360)
        s = self.rng.uniform(0.16, 0.3)
        self.muzzle.scale = (s, s)
        self.muzzle.enabled = True
        self.muzzle_t = 0.045

    def _spawn_tracer(self, origin, point):
        dx = point[0] - origin[0]
        dy = point[1] - origin[1]
        dz = point[2] - origin[2]
        dist = math.sqrt(dx * dx + dy * dy + dz * dz)
        if dist < 1.0:
            return
        slot = min(self.tracers, key=lambda t: t["ttl"])
        e = slot["e"]
        slot["ttl"] = 0.055
        e.enabled = True
        e.position = ((origin[0] + point[0]) / 2, (origin[1] + point[1]) / 2, (origin[2] + point[2]) / 2)
        e.scale = (0.012, 0.012, dist)
        yaw = math.degrees(math.atan2(dx, dz))
        pitch = math.degrees(math.atan2(-dy, math.hypot(dx, dz)))
        e.rotation = (pitch, yaw, 0)

    def _blood_splat(self, xz):
        b = self.blood[self.blood_i]
        self.blood_i = (self.blood_i + 1) % len(self.blood)
        b.enabled = True
        s = self.rng.uniform(0.7, 1.7)
        b.scale = (s, s)
        b.position = (xz[0] + self.rng.uniform(-0.2, 0.2), 0.045 + self.blood_i * 0.0004,
                      xz[1] + self.rng.uniform(-0.2, 0.2))
        b.rotation_z = self.rng.uniform(0, 360)

    # ------------------------------------------------------------------ vehicle mode
    def enter_vehicle(self, vehicle):
        self.vehicle = vehicle
        vehicle.occupied = True
        self.root.enabled = True
        camera.parent = self.game.cam_rig
        self.game.audio.set_loop("engine", 0.5)
        self.game.notify(f"{vehicle.stats['name']}. E — выйти, F — заправить снаружи")

    def exit_vehicle(self):
        v = self.vehicle
        if not v:
            return
        v.occupied = False
        self.vehicle = None
        ex = v.exit_position()
        rx, rz = self.game.world.resolve_collision((ex[0], ex[2]), 0.4)
        self.root.position = (rx, 0, rz)
        camera.parent = self.head
        camera.position = (0, 0, 0)
        camera.rotation = (0, 0, 0)
        camera.fov = 76
        self.game.audio.set_loop("engine", 0.0)

    # ------------------------------------------------------------------ update
    def update(self, dt):
        g = self.game
        st = g.stats
        self.fire_cooldown = max(0.0, self.fire_cooldown - dt)
        if self.reload_left > 0:
            self.reload_left -= dt
            if self.reload_left <= 0 and self.reload_weapon:
                g.inventory.reload(self.reload_weapon)
                self.reload_weapon = None
        if self.muzzle_t > 0:
            self.muzzle_t -= dt
            if self.muzzle_t <= 0:
                self.muzzle.enabled = False
        for t in self.tracers:
            if t["ttl"] > 0:
                t["ttl"] -= dt
                if t["ttl"] <= 0:
                    t["e"].enabled = False
        self.recoil = max(0.0, self.recoil - dt * 6.0)
        self.cam_shake = max(0.0, self.cam_shake - dt * 2.4)

        # ---------- driving
        if self.vehicle:
            v = self.vehicle
            throttle = (1 if held_keys["w"] else 0) - (1 if held_keys["s"] else 0)
            steer = (1 if held_keys["a"] else 0) - (1 if held_keys["d"] else 0)
            v.update(dt, throttle=throttle, steer_input=steer, brake=bool(held_keys["space"]))
            self.root.position = (v.body.x, 0, v.body.z)
            self.yaw_deg = v.yaw_deg
            # chase camera via rig
            yaw = math.radians(self.yaw_deg)
            cx = v.body.x - math.sin(yaw) * 8.5
            cz = v.body.z - math.cos(yaw) * 8.5
            rig = g.cam_rig
            rig.position = (cx, 3.4, cz)
            rig.look_at((v.body.x, 1.4, v.body.z))
            camera.position = (0, 0, 0)
            camera.rotation = (0, 0, 0)
            engine_vol = 0.32 + abs(v.speed) / 30 * 0.4 if v.fuel > 0 else 0.0
            g.audio.set_loop("engine", engine_vol, pitch=0.85 + abs(v.speed) / 26 * 0.7)
            g.director.emit_noise(self.pos_xz, 16 + abs(v.speed), "car") if abs(v.speed) > 1 else None
            st.update(dt, ambient_temp_c=g.director.ambient_temp + 4, wet=False)
            return

        # ---------- look
        if mouse.locked:
            sens = g.settings.mouse_sensitivity
            self.yaw_deg += mouse.velocity[0] * sens * 2.2
            inv = -1 if g.settings.invert_y else 1
            self.pitch_deg -= mouse.velocity[1] * sens * 2.2 * inv
            self.pitch_deg = max(-84, min(84, self.pitch_deg))
        self.crouched = bool(held_keys["control"]) or bool(held_keys["c"])
        want_sprint = bool(held_keys["shift"]) and not self.crouched
        self.sprinting = want_sprint and st.can_sprint and self.moving

        # ---------- move
        f = (1 if held_keys["w"] else 0) - (1 if held_keys["s"] else 0)
        r = (1 if held_keys["d"] else 0) - (1 if held_keys["a"] else 0)
        self.moving = bool(f or r)
        speed = CROUCH if self.crouched else (SPRINT if self.sprinting else WALK)
        speed *= st.speed_multiplier()
        if g.inventory.total_weight() > 26 * 0.92:
            speed *= 0.9
        if self.aiming:
            speed *= 0.55
        yaw = math.radians(self.yaw_deg)
        vx = (math.sin(yaw) * f + math.cos(yaw) * r)
        vz = (math.cos(yaw) * f - math.sin(yaw) * r)
        l = math.hypot(vx, vz) or 1.0
        nx = self.root.x + vx / l * speed * dt
        nz = self.root.z + vz / l * speed * dt
        rx, rz = g.world.resolve_collision((nx, nz), 0.42)
        self.root.x, self.root.z = rx, rz
        # gravity / jump (flat world)
        if held_keys["space"] and self.grounded and st.stamina > 6:
            self.vel_y = 7.6
            self.grounded = False
            st.stamina -= 5
        self.vel_y -= GRAVITY * dt
        self.root.y += self.vel_y * dt
        if self.root.y <= 0:
            self.root.y = 0
            self.vel_y = 0
            self.grounded = True
        # head bob + footsteps
        eye_h = 1.18 if self.crouched else PLAYER_EYE
        if self.moving and self.grounded:
            rate = 10.5 if self.sprinting else 7.5
            self.bob_phase += dt * rate
            bob = math.sin(self.bob_phase) * (0.035 if not self.sprinting else 0.055)
            prev = self.step_phase
            self.step_phase = math.sin(self.bob_phase)
            if prev > 0 and self.step_phase <= 0:
                self._footstep()
        else:
            bob = math.sin(g.time_played * 1.7) * 0.008
        target_head_y = eye_h + bob
        self.head.y += (target_head_y - self.head.y) * min(1.0, dt * 10)
        shake_x = self.rng.uniform(-1, 1) * self.cam_shake if self.cam_shake > 0 else 0
        shake_y = self.rng.uniform(-1, 1) * self.cam_shake if self.cam_shake > 0 else 0
        self.root.rotation_y = self.yaw_deg
        self.head.rotation_x = self.pitch_deg + shake_x * 3
        self.head.rotation_z = shake_y * 2 + (-r * 1.6 if self.moving else 0)  # strafe lean

        # ---------- survival tick
        wet = g.director.weather in ("rain", "storm")
        st.update(dt, sprinting=self.sprinting, moving=self.moving,
                  ambient_temp_c=g.director.ambient_temp, wet=wet)

        # ---------- weapon timers / hold to fire
        if self.swing_t > 0:
            self.swing_t -= dt
            if not self.swing_hit_done and self.swing_t < 0.16:
                self.swing_hit_done = True
                self._melee_strike()
        wid = self.current_weapon
        if wid and mouse.left and WEAPONS[wid]["kind"] == "gun" and WEAPONS[wid].get("auto"):
            self.try_fire()
        self.aiming = bool(mouse.right) and bool(wid)
        zoom = WEAPONS[wid].get("zoom", 1.15) if wid else 1.0
        target_fov = 76 / zoom if self.aiming else 76
        camera.fov += (target_fov - camera.fov) * min(1.0, dt * 9)

        # viewmodel animation: bob, sway, recoil, melee swing, reload dip
        if self.viewmodel:
            t = g.time_played
            sw_x = math.sin(self.bob_phase * 0.5) * 0.012 if self.moving else math.sin(t * 1.6) * 0.004
            sw_y = abs(math.cos(self.bob_phase * 0.5)) * 0.014 if self.moving else math.sin(t * 2.1) * 0.003
            base = (0.12, -0.13, 0.55) if self.aiming else (0.26, -0.24, 0.55)
            dip = -0.16 if self.reload_left > 0 else 0.0
            swing_rot = 0.0
            if self.swing_t > 0:
                u = 1.0 - self.swing_t / 0.32
                swing_rot = -math.sin(u * math.pi) * 55
            self.vm_holder.position = (base[0] + sw_x, base[1] + sw_y + dip,
                                       base[2] + self.recoil * 0.06)
            self.vm_holder.rotation = (self.recoil * 6 + swing_rot + (18 if self.reload_left > 0 else 0),
                                       swing_rot * 0.4, swing_rot * 0.3)

    def _footstep(self):
        g = self.game
        on_road = g.world._on_road(self.root.x, self.root.z)
        clip = "step_hard" if on_road else "step_grass"
        vol = 0.32 if not self.sprinting else 0.5
        if self.crouched:
            vol = 0.12
        g.audio.play(clip, vol)
        radius = 1.5 if self.crouched else (13.0 if self.sprinting else 5.5)
        g.director.emit_noise(self.pos_xz, radius, "steps")
