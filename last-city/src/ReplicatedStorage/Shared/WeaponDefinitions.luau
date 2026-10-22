--!strict
-- Every combat value is authoritative on the server.  Accuracy is a unitless cone
-- factor; hitRadius only controls the conservative near-ray fallback used when a
-- moving infected slips between client presentation and the server raycast.
return {
    crowbar = {damage = 28, interval = 0.65, magazine = 0, reload = 0, range = 8, accuracy = 1, durability = 100, rarity = "COMMON", level = 1, melee = true, recoil = 0.3},
    bat = {damage = 32, interval = 0.58, magazine = 0, reload = 0, range = 8.2, accuracy = 1, durability = 135, rarity = "COMMON", level = 1, melee = true, recoil = 0.35},
    hatchet = {damage = 38, interval = 0.68, magazine = 0, reload = 0, range = 7.4, accuracy = 1, durability = 130, rarity = "UNCOMMON", level = 1, melee = true, recoil = 0.45},
    machete = {damage = 42, interval = 0.72, magazine = 0, reload = 0, range = 7.5, accuracy = 1, durability = 120, rarity = "UNCOMMON", level = 1, melee = true, recoil = 0.45},
    pickaxe = {damage = 50, interval = 0.92, magazine = 0, reload = 0, range = 7, accuracy = 1, durability = 140, rarity = "UNCOMMON", level = 1, melee = true, recoil = 0.6},
    prism_blade = {damage = 62, interval = 0.6, magazine = 0, reload = 0, range = 8.4, accuracy = 1, durability = 180, rarity = "MYTHIC", level = 1, melee = true, recoil = 0.55},

    pistol = {damage = 32, interval = 0.32, magazine = 8, reload = 1.7, range = 160, accuracy = 0.99, hitRadius = 1.55, durability = 100, rarity = "UNCOMMON", level = 1, ammo = "ammo", recoil = 0.85},
    glock = {damage = 30, interval = 0.22, magazine = 10, reload = 1.45, range = 175, accuracy = 0.992, hitRadius = 1.5, durability = 110, rarity = "RARE", level = 1, ammo = "ammo", recoil = 0.72},
    revolver = {damage = 54, interval = 0.48, magazine = 6, reload = 2.15, range = 190, accuracy = 0.994, hitRadius = 1.4, durability = 125, rarity = "RARE", level = 2, ammo = "ammo", recoil = 1.45},
    smg = {damage = 18, interval = 0.09, magazine = 30, reload = 2.15, range = 145, accuracy = 0.94, hitRadius = 1.75, durability = 100, rarity = "RARE", level = 2, ammo = "ammo", auto = true, recoil = 0.42},
    double_barrel = {damage = 19, pellets = 9, interval = 1.05, magazine = 2, reload = 2.3, range = 82, accuracy = 0.86, hitRadius = 1.9, durability = 135, rarity = "RARE", level = 2, ammo = "shells", recoil = 2.15},
    shotgun = {damage = 16, pellets = 7, interval = 0.88, magazine = 5, reload = 2.65, range = 95, accuracy = 0.88, hitRadius = 1.85, durability = 125, rarity = "RARE", level = 2, ammo = "shells", recoil = 1.8},
    hunting_rifle = {damage = 72, interval = 0.82, magazine = 5, reload = 2.55, range = 320, accuracy = 0.998, hitRadius = 1.2, durability = 130, rarity = "EPIC", level = 2, ammo = "rifle_ammo", recoil = 1.65},
    rifle = {damage = 26, interval = 0.15, magazine = 24, reload = 2.4, range = 240, accuracy = 0.98, hitRadius = 1.5, durability = 110, rarity = "EPIC", level = 3, ammo = "rifle_ammo", auto = true, recoil = 0.58},
    assault_rifle = {damage = 31, interval = 0.12, magazine = 30, reload = 2.55, range = 270, accuracy = 0.984, hitRadius = 1.5, durability = 125, rarity = "EPIC", level = 3, ammo = "rifle_ammo", auto = true, recoil = 0.68},
}
