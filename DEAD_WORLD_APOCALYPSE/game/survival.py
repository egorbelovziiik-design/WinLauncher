"""Survival simulation: health, hunger, thirst, stamina, bleeding, infection,
temperature. Pure logic - fully unit-testable."""
from dataclasses import dataclass, asdict


def clamp(v, lo=0.0, hi=100.0):
    return lo if v < lo else hi if v > hi else v


@dataclass
class SurvivalStats:
    health: float = 100.0
    hunger: float = 82.0        # 100 = fed
    thirst: float = 78.0        # 100 = hydrated
    stamina: float = 100.0
    bleeding: bool = False
    infection: float = 0.0      # 0..100, 100 = turned
    temperature: float = 36.6   # body temp C
    stamina_boost_time: float = 0.0

    # ---- events -------------------------------------------------------------
    def damage(self, amount, cause_bleeding_chance=0.0, infection_gain=0.0, rng=None):
        self.health = clamp(self.health - amount)
        if infection_gain:
            self.infection = clamp(self.infection + infection_gain)
        if cause_bleeding_chance > 0 and rng is not None and rng.random() < cause_bleeding_chance:
            self.bleeding = True
        elif cause_bleeding_chance >= 1.0:
            self.bleeding = True
        return self.health <= 0

    def heal(self, amount):
        self.health = clamp(self.health + amount)

    def eat(self, food=0.0, water=0.0):
        self.hunger = clamp(self.hunger + food)
        self.thirst = clamp(self.thirst + water)

    def use_item(self, item):
        """Apply an ITEMS entry. Returns human-readable result str or None if not usable."""
        kind = item.get("kind")
        if kind in ("food", "drink"):
            self.eat(item.get("food", 0), item.get("water", 0))
            return "eaten"
        if kind == "med":
            if item.get("heal"):
                self.heal(item["heal"])
            if item.get("stop_bleeding"):
                self.bleeding = False
            if item.get("cure_infection"):
                self.infection = clamp(self.infection - item["cure_infection"])
            if item.get("stamina_boost"):
                self.stamina = clamp(self.stamina + item["stamina_boost"])
                self.stamina_boost_time = 30.0
            return "treated"
        return None

    # ---- simulation -----------------------------------------------------------
    def update(self, dt, sprinting=False, moving=False, ambient_temp_c=12.0, wet=False):
        """Advance needs. Rates are per real second; a game day (720s) should
        require roughly 3 meals and 3 drinks."""
        if dt <= 0:
            return
        activity = 1.6 if sprinting else (1.15 if moving else 0.8)
        self.hunger = clamp(self.hunger - dt * 0.028 * activity)
        self.thirst = clamp(self.thirst - dt * 0.042 * activity)

        if sprinting and moving:
            self.stamina = clamp(self.stamina - dt * 11.0)
        else:
            regen = 9.0 if not moving else 5.0
            if self.stamina_boost_time > 0:
                regen += 6.0
            self.stamina = clamp(self.stamina + dt * regen)
        if self.stamina_boost_time > 0:
            self.stamina_boost_time -= dt

        if self.bleeding:
            self.health = clamp(self.health - dt * 0.55)

        # starvation / dehydration damage, well-fed slow regen
        if self.hunger <= 0:
            self.health = clamp(self.health - dt * 0.25)
        if self.thirst <= 0:
            self.health = clamp(self.health - dt * 0.4)
        if self.hunger > 55 and self.thirst > 55 and not self.bleeding:
            self.health = clamp(self.health + dt * 0.10)

        # infection creeps once seeded
        if 0 < self.infection < 100:
            self.infection = clamp(self.infection + dt * 0.006 * (1 + self.infection / 50.0))
        if self.infection > 60:  # fever
            self.temperature = min(40.2, self.temperature + dt * 0.002)

        # body temperature drifts toward comfort unless cold/wet night air
        target = 36.6
        if ambient_temp_c < 2.0:
            target = 35.4
        if wet and ambient_temp_c < 8.0:
            target = min(target, 35.6)
        speed = 0.01 if target < self.temperature else 0.02
        self.temperature += (target - self.temperature) * min(1.0, dt * speed * 6)
        if self.temperature < 35.0:  # hypothermia drains stamina and health
            self.stamina = clamp(self.stamina - dt * 2.0)
            self.health = clamp(self.health - dt * 0.08)

    # ---- derived ---------------------------------------------------------------
    @property
    def dead(self):
        return self.health <= 0 or self.infection >= 100

    @property
    def can_sprint(self):
        return self.stamina > 4 and self.health > 5

    def speed_multiplier(self):
        m = 1.0
        if self.hunger < 20 or self.thirst < 20:
            m *= 0.88
        if self.health < 30:
            m *= 0.85
        if self.temperature < 35.2:
            m *= 0.85
        if self.infection > 70:
            m *= 0.9
        return m

    def warnings(self):
        out = []
        if self.health < 30:
            out.append("health")
        if self.hunger < 25:
            out.append("hunger")
        if self.thirst < 25:
            out.append("thirst")
        if self.bleeding:
            out.append("bleeding")
        if self.infection > 20:
            out.append("infection")
        if self.temperature < 35.4:
            out.append("cold")
        elif self.temperature > 38.5:
            out.append("fever")
        return out

    def to_dict(self):
        return asdict(self)

    @classmethod
    def from_dict(cls, d):
        s = cls()
        for k, v in (d or {}).items():
            if hasattr(s, k):
                setattr(s, k, v)
        return s
