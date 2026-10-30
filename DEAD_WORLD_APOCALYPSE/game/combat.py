"""Hitscan & melee mathematics: ray vs spheres (zombie hit zones) and ray vs
AABB (buildings). Pure functions -> deterministic, unit-testable shooting."""
import math


def v_sub(a, b): return (a[0] - b[0], a[1] - b[1], a[2] - b[2])
def v_dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
def v_len(a): return math.sqrt(v_dot(a, a))


def v_norm(a):
    l = v_len(a) or 1.0
    return (a[0] / l, a[1] / l, a[2] / l)


def ray_sphere(origin, direction, center, radius):
    """Return distance t of hit or None. direction must be normalized."""
    oc = v_sub(origin, center)
    b = v_dot(oc, direction)
    c = v_dot(oc, oc) - radius * radius
    disc = b * b - c
    if disc < 0:
        return None
    t = -b - math.sqrt(disc)
    if t < 0:
        t = -b + math.sqrt(disc)
    return t if t >= 0 else None


def ray_aabb(origin, direction, mn, mx):
    """Slab method. Returns t or None."""
    tmin, tmax = 0.0, float("inf")
    for i in range(3):
        o, d = origin[i], direction[i]
        if abs(d) < 1e-9:
            if o < mn[i] or o > mx[i]:
                return None
        else:
            t1 = (mn[i] - o) / d
            t2 = (mx[i] - o) / d
            if t1 > t2:
                t1, t2 = t2, t1
            tmin = max(tmin, t1)
            tmax = min(tmax, t2)
            if tmin > tmax:
                return None
    return tmin


class HitZone:
    __slots__ = ("zombie", "zone", "center", "radius", "multiplier")

    def __init__(self, zombie, zone, center, radius, multiplier):
        self.zombie = zombie
        self.zone = zone            # "head" | "body" | "limb"
        self.center = center
        self.radius = radius
        self.multiplier = multiplier


def hitscan(origin, direction, max_dist, zones, blockers):
    """zones: [HitZone], blockers: [(mn, mx), ...] AABBs.
    Returns (HitZone|None, hit_point, distance)."""
    direction = v_norm(direction)
    best_t = max_dist
    best_zone = None
    for (mn, mx) in blockers:
        t = ray_aabb(origin, direction, mn, mx)
        if t is not None and t < best_t:
            best_t = t
            best_zone = None
    wall_t = best_t
    for z in zones:
        t = ray_sphere(origin, direction, z.center, z.radius)
        if t is not None and t < best_t:
            best_t = t
            best_zone = z
    point = (origin[0] + direction[0] * best_t,
             origin[1] + direction[1] * best_t,
             origin[2] + direction[2] * best_t)
    return best_zone, point, best_t


def apply_damage(base_damage, zone, zombie_cfg, distance=0.0, pellets_hit=1):
    """Damage model: headshot x3 (helmet reduces), armored bodies absorb,
    shotgun-style falloff with distance."""
    dmg = float(base_damage) * pellets_hit
    if zone == "head":
        mult = 3.0
        if zombie_cfg.get("helmet"):
            mult = 1.6     # helmet eats part of the headshot
        dmg *= mult
    elif zone == "body":
        dmg *= (1.0 - zombie_cfg.get("armor_body", 0.0))
    elif zone == "limb":
        dmg *= 0.65
    if distance > 25:      # gentle falloff for everything except point blank
        dmg *= max(0.55, 1.0 - (distance - 25) / 220.0)
    return dmg


def melee_targets(origin, facing_yaw, zombies_pos, reach, arc_cos=0.35):
    """Return indices of zombies within reach and swing arc.
    zombies_pos: [(x,z),...], origin: (x,z)."""
    out = []
    fx, fz = math.sin(facing_yaw), math.cos(facing_yaw)
    for i, (x, z) in enumerate(zombies_pos):
        dx, dz = x - origin[0], z - origin[1]
        d = math.hypot(dx, dz)
        if d <= reach and d > 1e-6:
            if (dx / d) * fx + (dz / d) * fz >= arc_cos:
                out.append(i)
        elif d <= 0.7:
            out.append(i)
    return out


def spread_direction(direction, spread_deg, rng):
    """Perturb a unit direction within a cone (degrees)."""
    if spread_deg <= 0:
        return direction
    # build an orthonormal basis around direction
    d = v_norm(direction)
    up = (0.0, 1.0, 0.0) if abs(d[1]) < 0.95 else (1.0, 0.0, 0.0)
    right = v_norm((d[1] * up[2] - d[2] * up[1],
                    d[2] * up[0] - d[0] * up[2],
                    d[0] * up[1] - d[1] * up[0]))
    up2 = (right[1] * d[2] - right[2] * d[1],
           right[2] * d[0] - right[0] * d[2],
           right[0] * d[1] - right[1] * d[0])
    a = math.radians(spread_deg)
    rx = rng.uniform(-a, a)
    ry = rng.uniform(-a, a)
    out = (d[0] + right[0] * rx + up2[0] * ry,
           d[1] + right[1] * rx + up2[1] * ry,
           d[2] + right[2] * rx + up2[2] * ry)
    return v_norm(out)
