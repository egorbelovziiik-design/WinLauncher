"""Inventory: stacks, weight limit, hotbar. Pure logic, unit-testable."""
from game.data import ITEMS, WEAPONS

MAX_WEIGHT = 26.0
GRID_SIZE = 24          # inventory grid slots
HOTBAR_SIZE = 6


class Inventory:
    def __init__(self):
        self.slots = []          # list of {"id":..., "count":...}
        self.hotbar = [None] * HOTBAR_SIZE   # item ids (weapons / usable)
        self.max_weight_bonus = 0.0          # backpack etc.
        self.ammo = {"ammo_9mm": 0, "ammo_rifle": 0, "ammo_shells": 0, "ammo_bolts": 0}
        # loaded magazines per weapon id
        self.mags = {}
        # fuel carried is represented by fuel_canister items

    # ------------------------------------------------------------------ weight
    def item_weight(self, item_id):
        return ITEMS.get(item_id, {}).get("weight", 0.2)

    def total_weight(self):
        w = sum(self.item_weight(s["id"]) * s["count"] for s in self.slots)
        w += sum(self.ammo.values()) * 0.012
        return round(w, 2)

    @property
    def max_weight(self):
        return MAX_WEIGHT + self.max_weight_bonus

    def can_carry(self, item_id, count=1):
        return self.total_weight() + self.item_weight(item_id) * count <= self.max_weight

    # ------------------------------------------------------------------ stacks
    def count(self, item_id):
        if item_id in self.ammo:
            return self.ammo[item_id]
        return sum(s["count"] for s in self.slots if s["id"] == item_id)

    def has(self, item_id, count=1):
        return self.count(item_id) >= count

    def add(self, item_id, count=1):
        """Add items or ammo. Returns number actually added."""
        if item_id not in ITEMS:
            return 0
        if ITEMS[item_id].get("kind") == "ammo":
            amount = ITEMS[item_id].get("amount", 1) * count
            self.ammo[item_id] = self.ammo.get(item_id, 0) + amount
            return count
        added = 0
        stack_max = ITEMS[item_id].get("stack", 1)
        for _ in range(count):
            if not self.can_carry(item_id, 1):
                break
            placed = False
            for s in self.slots:
                if s["id"] == item_id and s["count"] < stack_max:
                    s["count"] += 1
                    placed = True
                    break
            if not placed:
                if len(self.slots) >= GRID_SIZE:
                    break
                self.slots.append({"id": item_id, "count": 1})
            added += 1
            if ITEMS[item_id].get("kind") == "weapon":
                wid = ITEMS[item_id]["weapon_id"]
                if wid not in self.mags:
                    self.mags[wid] = 0
                self.assign_hotbar(item_id)
        return added

    def remove(self, item_id, count=1):
        if item_id in self.ammo and ITEMS.get(item_id, {}).get("kind") == "ammo":
            take = min(count, self.ammo[item_id])
            self.ammo[item_id] -= take
            return take
        removed = 0
        for s in list(self.slots):
            if removed >= count:
                break
            if s["id"] == item_id:
                take = min(s["count"], count - removed)
                s["count"] -= take
                removed += take
                if s["count"] <= 0:
                    self.slots.remove(s)
        if removed and not self.has(item_id):
            self.hotbar = [None if h == item_id else h for h in self.hotbar]
        return removed

    def use_consumable(self, item_id, stats):
        """Use food/drink/med via SurvivalStats. Returns True if consumed."""
        item = ITEMS.get(item_id)
        if not item or not self.has(item_id):
            return False
        result = stats.use_item(item)
        if result:
            self.remove(item_id, 1)
            return True
        return False

    # ------------------------------------------------------------------ hotbar / weapons
    def assign_hotbar(self, item_id):
        if item_id in self.hotbar:
            return self.hotbar.index(item_id)
        for i, h in enumerate(self.hotbar):
            if h is None:
                self.hotbar[i] = item_id
                return i
        return -1

    def weapons_owned(self):
        return [s["id"] for s in self.slots if ITEMS[s["id"]].get("kind") == "weapon"]

    def reload(self, weapon_id):
        """Move ammo from reserves into the magazine. Returns rounds loaded."""
        w = WEAPONS.get(weapon_id)
        if not w or w.get("kind") != "gun":
            return 0
        mag_size = w["mag"]
        current = self.mags.get(weapon_id, 0)
        need = mag_size - current
        if need <= 0:
            return 0
        avail = self.ammo.get(w["ammo"], 0)
        load = min(need, avail)
        self.ammo[w["ammo"]] -= load
        self.mags[weapon_id] = current + load
        return load

    def fire_round(self, weapon_id):
        if self.mags.get(weapon_id, 0) <= 0:
            return False
        self.mags[weapon_id] -= 1
        return True

    # ------------------------------------------------------------------ misc
    def sort(self):
        order = {"weapon": 0, "med": 1, "food": 2, "drink": 3, "fuel": 4, "ammo": 5, "gear": 6}
        self.slots.sort(key=lambda s: (order.get(ITEMS[s["id"]].get("kind"), 9), s["id"]))

    def summary(self):
        return {"slots": [dict(s) for s in self.slots], "weight": self.total_weight(),
                "ammo": dict(self.ammo), "mags": dict(self.mags), "hotbar": list(self.hotbar),
                "max_weight_bonus": self.max_weight_bonus}

    def to_dict(self):
        return self.summary()

    @classmethod
    def from_dict(cls, d):
        inv = cls()
        if not d:
            return inv
        inv.slots = [dict(s) for s in d.get("slots", []) if s.get("id") in ITEMS]
        inv.ammo.update(d.get("ammo", {}))
        inv.mags = dict(d.get("mags", {}))
        inv.max_weight_bonus = float(d.get("max_weight_bonus", 0.0))
        hb = list(d.get("hotbar", []))[:HOTBAR_SIZE]
        inv.hotbar = hb + [None] * (HOTBAR_SIZE - len(hb))
        return inv
