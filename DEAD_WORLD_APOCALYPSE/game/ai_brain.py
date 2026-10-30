"""Zombie AI brain: perception (sight/hearing), behavior state machine and
steering. Pure logic operating on (x,z) tuples - engine feeds positions in,
gets desired velocity + state out. Unit-testable without graphics."""
import math, random

# states
IDLE, WANDER, SEARCH, CHASE, ATTACK, STAGGER, DEAD = (
    "idle", "wander", "search", "chase", "attack", "stagger", "dead")

MEMORY_TIME = 7.0      # seconds a zombie remembers last seen player position
FOV_COS = math.cos(math.radians(65))   # half-angle of vision cone


def dist2(a, b):
    return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2


def dist(a, b):
    return math.sqrt(dist2(a, b))


def norm_angle(a):
    while a > math.pi:
        a -= 2 * math.pi
    while a < -math.pi:
        a += 2 * math.pi
    return a


def angle_lerp(a, b, t):
    return a + norm_angle(b - a) * max(0.0, min(1.0, t))


class NoiseEvent:
    """A sound zombies can hear (gunshot, sprint, car, scream...)."""

    def __init__(self, pos, radius, kind="generic"):
        self.pos = pos
        self.radius = radius
        self.kind = kind


class ZombieBrain:
    def __init__(self, cfg, rng=None):
        self.cfg = cfg
        self.rng = rng or random.Random()
        self.state = IDLE
        self.state_time = 0.0
        self.target_pos = None        # last known player position
        self.wander_target = None
        self.memory = 0.0
        self.alert_level = 0.0        # 0 calm .. 1 fully alerted
        self.attack_cooldown = 0.0
        self.screamed = False
        self.home = (0.0, 0.0)

    # ---------------------------------------------------------------- perception
    def can_see(self, my_pos, my_yaw, player_pos, player_crouched=False,
                darkness=0.0, line_blocked=False):
        """Vision: distance (reduced by darkness/crouch) + FOV cone + LOS."""
        if line_blocked:
            return False
        vision = self.cfg["vision"]
        vision *= (1.0 - 0.45 * darkness)
        if player_crouched:
            vision *= 0.62
        d = dist(my_pos, player_pos)
        if d > vision:
            return False
        if d < 2.2:      # point-blank: always noticed
            return True
        dx = player_pos[0] - my_pos[0]
        dz = player_pos[1] - my_pos[1]
        to_player = math.atan2(dx, dz)
        return math.cos(norm_angle(to_player - my_yaw)) > FOV_COS

    def hear(self, my_pos, noise):
        return dist(my_pos, noise.pos) <= max(noise.radius, self.cfg["hearing"] * (noise.radius / 14.0))

    # ---------------------------------------------------------------- events
    def on_noise(self, my_pos, noise):
        if self.state in (DEAD, CHASE, ATTACK):
            return False
        if self.hear(my_pos, noise):
            self.target_pos = noise.pos
            self.memory = MEMORY_TIME * 0.7
            self.alert_level = max(self.alert_level, 0.6)
            self._set_state(SEARCH)
            return True
        return False

    def on_damaged(self, attacker_pos, heavy=False):
        if self.state == DEAD:
            return
        self.target_pos = attacker_pos
        self.memory = MEMORY_TIME
        self.alert_level = 1.0
        if heavy:
            self._set_state(STAGGER)
        elif self.state not in (ATTACK,):
            self._set_state(CHASE)

    def alert_to(self, pos):
        """Screamer / horde propagation."""
        if self.state in (DEAD, ATTACK):
            return
        self.target_pos = pos
        self.memory = MEMORY_TIME
        self.alert_level = 1.0
        self._set_state(CHASE)

    def _set_state(self, s):
        if s != self.state:
            self.state = s
            self.state_time = 0.0

    # ---------------------------------------------------------------- update
    def update(self, dt, my_pos, my_yaw, player_pos, player_alive=True,
               player_crouched=False, darkness=0.0, line_blocked=False,
               door_in_way=False):
        """Returns (desired_state, move_target_pos or None, speed_multiplier_zones).
        The engine reads `state` and `move_target` after each call."""
        if self.state == DEAD:
            return self.state
        self.state_time += dt
        self.attack_cooldown = max(0.0, self.attack_cooldown - dt)
        if self.memory > 0:
            self.memory -= dt

        sees = False
        if player_alive:
            sees = self.can_see(my_pos, my_yaw, player_pos, player_crouched, darkness, line_blocked)
        if sees:
            if self.state == STAGGER and self.state_time < 0.45:
                pass
            else:
                self.target_pos = player_pos
                self.memory = MEMORY_TIME
                self.alert_level = 1.0
                d = dist(my_pos, player_pos)
                if d <= self.cfg["attack_range"] and not door_in_way:
                    self._set_state(ATTACK)
                else:
                    self._set_state(CHASE)
        else:
            self.alert_level = max(0.0, self.alert_level - dt * 0.05)

        if self.state == STAGGER and self.state_time > 0.55:
            self._set_state(CHASE if self.memory > 0 else SEARCH)
        elif self.state == ATTACK:
            d = dist(my_pos, player_pos) if player_alive else 999
            if d > self.cfg["attack_range"] * 1.35:
                self._set_state(CHASE)
        elif self.state == CHASE:
            if self.memory <= 0:
                self._set_state(SEARCH)
        elif self.state == SEARCH:
            if self.target_pos and dist(my_pos, self.target_pos) < 1.4:
                if self.state_time > 3.2:
                    self._set_state(WANDER)
            elif self.state_time > 9.0:
                self._set_state(WANDER)
        elif self.state == IDLE:
            if self.state_time > self.rng.uniform(2.0, 5.0) and self.state_time > 4.0:
                self._set_state(WANDER)
                self.wander_target = self._pick_wander(my_pos)
        elif self.state == WANDER:
            if self.wander_target is None or dist(my_pos, self.wander_target) < 1.2 \
                    or self.state_time > 14.0:
                if self.rng.random() < 0.45:
                    self._set_state(IDLE)
                else:
                    self.wander_target = self._pick_wander(my_pos)
                    self.state_time = 0.0
        return self.state

    def _pick_wander(self, my_pos, radius=16.0):
        a = self.rng.uniform(0, 2 * math.pi)
        r = self.rng.uniform(4.0, radius)
        return (self.home[0] + math.sin(a) * r * 0.5 + (my_pos[0] - self.home[0]) * 0.3,
                self.home[1] + math.cos(a) * r * 0.5 + (my_pos[1] - self.home[1]) * 0.3)

    @property
    def move_target(self):
        if self.state in (CHASE,):
            return self.target_pos
        if self.state == SEARCH:
            return self.target_pos
        if self.state == WANDER:
            return self.wander_target
        return None

    def speed(self):
        if self.state == CHASE:
            return self.cfg["speed_chase"]
        if self.state == SEARCH:
            return self.cfg["speed_walk"] * 1.35
        if self.state == WANDER:
            return self.cfg["speed_walk"]
        return 0.0


def steer(my_pos, my_yaw, target, speed, dt, obstacles=(), separation=(), turn_rate=3.2):
    """Simple steering: seek + obstacle avoidance (circles) + horde separation.
    Returns (new_pos, new_yaw). obstacles: (x, z, radius)."""
    if target is None or speed <= 0:
        return my_pos, my_yaw
    dx = target[0] - my_pos[0]
    dz = target[1] - my_pos[1]
    d = math.hypot(dx, dz)
    if d < 0.05:
        return my_pos, my_yaw
    vx, vz = dx / d, dz / d
    heading = (vx, vz)
    # obstacle avoidance: radial push-away + tangential slide so a dead-ahead
    # obstacle deflects the zombie sideways instead of just slowing it.
    for (ox, oz, orad) in obstacles:
        odx = my_pos[0] - ox
        odz = my_pos[1] - oz
        od = math.hypot(odx, odz)
        if 0.01 < od < orad + 1.4:
            w = (orad + 1.4 - od) / (orad + 1.4)
            ax, az = odx / od, odz / od
            # perpendicular of away-vector; pick the side closer to our heading
            px, pz = -az, ax
            if px * heading[0] + pz * heading[1] < 0:
                px, pz = -px, -pz
            vx += ax * w * 1.5 + px * w * 1.3
            vz += az * w * 1.5 + pz * w * 1.3
    # separation from other zombies
    for (sx, sz) in separation:
        sdx = my_pos[0] - sx
        sdz = my_pos[1] - sz
        sd = math.hypot(sdx, sdz)
        if 0.01 < sd < 0.95:
            vx += (sdx / sd) * 0.7
            vz += (sdz / sd) * 0.7
    vd = math.hypot(vx, vz) or 1.0
    vx, vz = vx / vd, vz / vd
    desired_yaw = math.atan2(vx, vz)
    new_yaw = angle_lerp(my_yaw, desired_yaw, turn_rate * dt)
    step = min(speed * dt, d)
    return (my_pos[0] + math.sin(new_yaw) * step,
            my_pos[1] + math.cos(new_yaw) * step), new_yaw
