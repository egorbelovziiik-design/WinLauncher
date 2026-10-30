"""Static game data: weapons, items, zombie types, loot tables. Pure data."""

WEAPONS = {
    # id: name_ru, kind, model, stats...
    "knife":    dict(name="Нож", kind="melee", damage=34, rate=2.2, range=2.1, stamina=6,
                     sound_swing="melee_swing", sound_hit="melee_hit", weight=0.3),
    "bat":      dict(name="Бита с гвоздями", kind="melee", damage=46, rate=1.3, range=2.4, stamina=10,
                     knockback=3.2, sound_swing="melee_swing", sound_hit="melee_hit", weight=1.1),
    "machete":  dict(name="Мачете", kind="melee", damage=58, rate=1.6, range=2.3, stamina=8,
                     sound_swing="melee_swing", sound_hit="melee_hit", weight=0.7),
    "axe":      dict(name="Топор", kind="melee", damage=78, rate=1.0, range=2.5, stamina=14,
                     knockback=2.0, sound_swing="melee_swing", sound_hit="melee_hit", weight=1.6),
    "pistol":   dict(name="Пистолет", kind="gun", damage=26, rate=3.5, range=120, mag=12,
                     ammo="ammo_9mm", spread=0.9, recoil=0.55, reload_time=1.1, auto=False,
                     sound="shoot_pistol", noise=46, weight=0.9, zoom=1.15),
    "revolver": dict(name="Револьвер", kind="gun", damage=52, rate=1.8, range=130, mag=6,
                     ammo="ammo_9mm", spread=0.6, recoil=1.0, reload_time=1.8, auto=False,
                     sound="shoot_pistol", noise=55, weight=1.2, zoom=1.15),
    "rifle":    dict(name="Штурмовая винтовка", kind="gun", damage=30, rate=8.0, range=180, mag=30,
                     ammo="ammo_rifle", spread=1.5, recoil=0.8, reload_time=1.6, auto=True,
                     sound="shoot_rifle", noise=70, weight=3.4, zoom=1.3),
    "shotgun":  dict(name="Дробовик", kind="gun", damage=13, pellets=8, rate=1.1, range=45, mag=6,
                     ammo="ammo_shells", spread=5.5, recoil=2.2, reload_time=2.2, auto=False,
                     sound="shoot_shotgun", noise=80, knockback=4.0, weight=3.8, zoom=1.1),
    "sniper":   dict(name="Снайперская винтовка", kind="gun", damage=160, rate=0.7, range=400, mag=5,
                     ammo="ammo_rifle", spread=0.05, recoil=2.6, reload_time=2.4, auto=False,
                     sound="shoot_sniper", noise=95, weight=5.2, zoom=3.2),
    "crossbow": dict(name="Арбалет", kind="gun", damage=85, rate=0.9, range=90, mag=1,
                     ammo="ammo_bolts", spread=0.25, recoil=0.3, reload_time=1.9, auto=False,
                     sound="melee_swing", noise=6, weight=2.6, zoom=1.4),
}

ITEMS = {
    "food_can":    dict(name="Консервы", kind="food", food=38, water=4, weight=0.35, model="food_can", stack=10),
    "water_bottle": dict(name="Вода", kind="drink", water=45, weight=0.5, model="water_bottle", stack=8),
    "bandage":     dict(name="Бинт", kind="med", heal=12, stop_bleeding=True, weight=0.08, model="bandage", stack=10),
    "medkit":      dict(name="Аптечка", kind="med", heal=55, stop_bleeding=True, cure_infection=8, weight=0.6, model="medkit", stack=5),
    "antibiotics": dict(name="Антибиотики", kind="med", cure_infection=45, weight=0.1, model="medkit", stack=6),
    "painkillers": dict(name="Обезболивающее", kind="med", heal=8, stamina_boost=25, weight=0.05, model="bandage", stack=10),
    "ammo_9mm":    dict(name="Патроны 9мм", kind="ammo", amount=12, weight=0.15, model="ammo_box", stack=20),
    "ammo_rifle":  dict(name="Патроны 5.56", kind="ammo", amount=20, weight=0.3, model="ammo_box", stack=20),
    "ammo_shells": dict(name="Картечь 12к", kind="ammo", amount=6, weight=0.25, model="ammo_box", stack=20),
    "ammo_bolts":  dict(name="Болты", kind="ammo", amount=5, weight=0.12, model="ammo_box", stack=20),
    "fuel_canister": dict(name="Канистра топлива", kind="fuel", fuel=35, weight=4.0, model="fuel_canister", stack=2),
    "backpack":    dict(name="Рюкзак", kind="gear", weight=1.2, model="backpack", stack=1),
}
# weapons are also carryable items
for _wid, _w in WEAPONS.items():
    ITEMS[_wid] = dict(name=_w["name"], kind="weapon", weight=_w.get("weight", 1.0),
                       model=_wid, stack=1, weapon_id=_wid)

ZOMBIE_TYPES = {
    # id -> behavior + balance. model dir = id
    "zombie_walker":      dict(name="Обычный зомби", hp=100, speed_walk=1.5, speed_chase=2.6,
                               damage=9, attack_range=1.7, vision=26, hearing=14, anim_walk="walk",
                               anim_speed=1.0, xp=10),
    "zombie_slow":        dict(name="Толстяк", hp=190, speed_walk=0.9, speed_chase=1.7,
                               damage=14, attack_range=1.8, vision=22, hearing=12, anim_walk="walk",
                               anim_speed=0.7, knock_resist=0.4, xp=16),
    "zombie_runner":      dict(name="Бегун", hp=75, speed_walk=2.2, speed_chase=5.4,
                               damage=8, attack_range=1.6, vision=34, hearing=18, anim_walk="run",
                               anim_speed=1.0, xp=18),
    "zombie_crawler":     dict(name="Ползун", hp=55, speed_walk=0.8, speed_chase=1.6,
                               damage=7, attack_range=1.3, vision=18, hearing=12, anim_walk="crawl",
                               anim_speed=1.0, crawler=True, xp=12),
    "zombie_soldier":     dict(name="Зомби-солдат", hp=150, speed_walk=1.5, speed_chase=2.9,
                               damage=11, attack_range=1.7, vision=28, hearing=15, anim_walk="walk",
                               anim_speed=1.0, armor_body=0.45, helmet=True, xp=24),
    "zombie_police":      dict(name="Зомби-полицейский", hp=120, speed_walk=1.5, speed_chase=2.8,
                               damage=10, attack_range=1.7, vision=27, hearing=15, anim_walk="walk",
                               anim_speed=1.0, armor_body=0.25, xp=20),
    "zombie_doctor":      dict(name="Зомби-врач", hp=95, speed_walk=1.4, speed_chase=2.7,
                               damage=9, attack_range=1.7, vision=25, hearing=14, anim_walk="walk",
                               anim_speed=1.0, infection_bonus=6, xp=20),
    "zombie_firefighter": dict(name="Зомби-пожарный", hp=165, speed_walk=1.3, speed_chase=2.4,
                                damage=12, attack_range=1.8, vision=24, hearing=13, anim_walk="walk",
                                anim_speed=0.9, armor_body=0.3, helmet=True, xp=24),
    "zombie_screamer":    dict(name="Крикун", hp=70, speed_walk=1.7, speed_chase=3.1,
                                damage=6, attack_range=1.6, vision=36, hearing=22, anim_walk="walk",
                                anim_speed=1.15, screamer=True, xp=30),
    "zombie_mutant":      dict(name="Мутант", hp=320, speed_walk=1.9, speed_chase=4.4,
                                damage=19, attack_range=2.1, vision=34, hearing=20, anim_walk="run",
                                anim_speed=0.85, knock_resist=0.6, leap=True, xp=60),
    "zombie_boss":        dict(name="ГИГАНТ", hp=950, speed_walk=1.4, speed_chase=3.4,
                                damage=30, attack_range=2.8, vision=40, hearing=26, anim_walk="walk",
                                anim_speed=0.75, knock_resist=0.85, armor_body=0.2, boss=True, xp=200),
}

# Weighted spawn pools per zone
SPAWN_POOLS = {
    "city":      [("zombie_walker", 40), ("zombie_slow", 12), ("zombie_runner", 12),
                  ("zombie_crawler", 8), ("zombie_police", 8), ("zombie_screamer", 4),
                  ("zombie_doctor", 5), ("zombie_firefighter", 5), ("zombie_mutant", 2)],
    "hospital":  [("zombie_doctor", 40), ("zombie_walker", 30), ("zombie_crawler", 15),
                  ("zombie_screamer", 8), ("zombie_runner", 7)],
    "police":    [("zombie_police", 45), ("zombie_walker", 30), ("zombie_soldier", 15),
                  ("zombie_runner", 10)],
    "military":  [("zombie_soldier", 50), ("zombie_walker", 25), ("zombie_runner", 15),
                  ("zombie_mutant", 10)],
    "industrial": [("zombie_walker", 35), ("zombie_firefighter", 20), ("zombie_slow", 15),
                   ("zombie_mutant", 12), ("zombie_crawler", 10), ("zombie_runner", 8)],
    "forest":    [("zombie_walker", 45), ("zombie_runner", 20), ("zombie_crawler", 15),
                  ("zombie_screamer", 10), ("zombie_slow", 10)],
}

LOOT_TABLES = {
    "generic":  [("food_can", 22), ("water_bottle", 20), ("bandage", 14), ("ammo_9mm", 12),
                 ("painkillers", 8), ("fuel_canister", 4)],
    "medical":  [("medkit", 22), ("bandage", 26), ("antibiotics", 18), ("painkillers", 20),
                 ("water_bottle", 14)],
    "police":   [("ammo_9mm", 26), ("ammo_shells", 16), ("pistol", 7), ("shotgun", 4),
                 ("bat", 10), ("bandage", 12), ("food_can", 10)],
    "military": [("ammo_rifle", 28), ("rifle", 6), ("sniper", 3), ("medkit", 12),
                 ("fuel_canister", 10), ("ammo_shells", 12), ("crossbow", 3), ("ammo_bolts", 8)],
    "food":     [("food_can", 42), ("water_bottle", 34), ("knife", 6), ("bandage", 8)],
    "garage":   [("fuel_canister", 30), ("axe", 8), ("ammo_shells", 10), ("food_can", 12),
                 ("bandage", 10)],
    "weapon_rare": [("revolver", 16), ("machete", 20), ("axe", 16), ("crossbow", 10),
                    ("ammo_rifle", 20), ("sniper", 6)],
}

ZOMBIE_DROPS = {
    "zombie_doctor": [("bandage", 25), ("medkit", 10), ("antibiotics", 12)],
    "zombie_police": [("ammo_9mm", 22), ("ammo_shells", 8)],
    "zombie_soldier": [("ammo_rifle", 22), ("bandage", 10)],
    "zombie_firefighter": [("axe", 4), ("bandage", 10)],
    "_default": [("food_can", 4), ("ammo_9mm", 4), ("bandage", 4)],
    "zombie_boss": [("sniper", 60), ("medkit", 80), ("ammo_rifle", 90)],
    "zombie_mutant": [("medkit", 20), ("ammo_rifle", 20)],
}

OBJECTIVES = [
    dict(id="find_weapon", text="Найдите оружие (ищите в домах и у полицейского участка)", check="has_gun"),
    dict(id="hospital", text="Доберитесь до больницы и найдите антибиотики", check="has_antibiotics"),
    dict(id="fuel", text="Найдите топливо и заправьте пикап у заправки", check="car_fueled"),
    dict(id="lab", text="Зачистите лабораторию в промзоне: убейте ГИГАНТА", check="boss_dead"),
    dict(id="survive", text="Выживайте. Дни сочтены не у вас — у них.", check="never"),
]
