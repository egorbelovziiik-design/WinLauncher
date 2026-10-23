--!strict
-- Common infected always hits for exactly 5 HP. Rare variants rise in readable,
-- survivable steps; player armor is applied by CombatService before health loss.
return {
    walker = {name = "Заражённый", health = 72, speed = 9, damage = 5, interval = 1.55, xp = 10, cash = 7, color = {102, 119, 84}, size = 1, voice = 0.88},
    lurker = {name = "Шатун", health = 82, speed = 11, damage = 6, interval = 1.45, xp = 14, cash = 9, color = {91, 116, 104}, size = 0.96, voice = 1.02},
    runner = {name = "Бегун", health = 58, speed = 16, damage = 7, interval = 1.2, xp = 18, cash = 12, color = {142, 118, 77}, size = 0.92, voice = 1.2},
    ravager = {name = "Дикарь", health = 120, speed = 10, damage = 8, interval = 1.35, xp = 24, cash = 16, color = {122, 82, 76}, size = 1.08, voice = 0.82},
    corrosive = {name = "Коррозийный", health = 105, speed = 9, damage = 8, interval = 2.25, xp = 28, cash = 19, color = {112, 151, 72}, size = 1.02, ranged = 24, city2Only = true, voice = 1.12},
    stalker = {name = "Сталкер", health = 98, speed = 14, damage = 9, interval = 1.3, xp = 30, cash = 21, color = {78, 91, 94}, size = 0.98, city2Only = true, voice = 1.25},
    howler = {name = "Воющий", health = 92, speed = 12, damage = 7, interval = 1.45, xp = 36, cash = 25, color = {128, 88, 76}, size = 1.02, city2Only = true, howlRadius = 34, howlCooldown = 11, enrageDuration = 7, voice = 1.34},
    shocker = {name = "Разрядник", health = 118, speed = 8.5, damage = 9, interval = 2.4, xp = 42, cash = 30, color = {69, 129, 143}, size = 1.04, city2Only = true, ranged = 34, damageType = "electric", staminaDrain = 18, rangedEffect = "electric", voice = 1.08},
    crusher = {name = "Дробитель", health = 310, speed = 6.4, damage = 14, interval = 1.9, xp = 66, cash = 47, color = {92, 102, 96}, size = 1.28, armor = 0.2, city2Only = true, regenPerSecond = 11, regenDelay = 4.5, structureDamage = 24, voice = 0.56},
    brute = {name = "Громила", health = 190, speed = 8, damage = 10, interval = 1.7, xp = 34, cash = 23, color = {113, 104, 80}, size = 1.2, structureDamage = 15, voice = 0.7},
    mine_guard = {name = "Шахтный громила", health = 225, speed = 8.5, damage = 12, interval = 1.65, xp = 44, cash = 30, color = {112, 90, 67}, size = 1.18, armor = 0.12, structureDamage = 18, aggro = 78, leash = 82, mineOnly = true, minion = true, voice = 0.66},
    armored = {name = "Бронированный", health = 250, speed = 7, damage = 12, interval = 1.85, xp = 48, cash = 34, color = {83, 101, 102}, size = 1.16, armor = 0.28, structureDamage = 20, voice = 0.74},
    tank = {name = "Тяжёлый", health = 340, speed = 6, damage = 15, interval = 2.05, xp = 62, cash = 44, color = {93, 100, 106}, size = 1.35, armor = 0.12, structureDamage = 26, voice = 0.58},

    -- Each boss owns an independent lifecycle and one randomly selected authored
    -- encounter point. Mine guards and the Foreman remain mine-exclusive.
    reaver = {name = "ПЕПЕЛЬНЫЙ ЖНЕЦ", health = 620, speed = 8, damage = 14, interval = 1.65, xp = 145, cash = 110, color = {132, 72, 57}, size = 1.34, armor = 0.14, structureDamage = 25, aggro = 105, leash = 120, boss = true, city1Only = true, voice = 0.57},
    warden = {name = "ТЮРЕМЩИК РУБЕЖА", health = 760, speed = 6.8, damage = 16, interval = 2, xp = 185, cash = 145, color = {72, 86, 83}, size = 1.4, armor = 0.22, structureDamage = 29, aggro = 110, leash = 125, boss = true, city2Only = true, voice = 0.5},
    rainbow = {name = "РАДУЖНЫЙ СТРАЖ", health = 1050, speed = 7.2, damage = 17, interval = 1.85, xp = 260, cash = 210, profileCoins = 35, color = {176, 82, 201}, size = 1.46, armor = 0.24, structureDamage = 31, aggro = 95, leash = 100, boss = true, city2Only = true, voice = 0.54},
    ironclad = {name = "ЖЕЛЕЗНЫЙ БАСТИОН", health = 1380, speed = 6.2, damage = 17, interval = 2.25, xp = 310, cash = 245, color = {71, 91, 94}, size = 1.52, armor = 0.3, structureDamage = 34, aggro = 112, leash = 118, boss = true, city2Only = true, ranged = 30, damageType = "electric", staminaDrain = 24, rangedEffect = "electric", regenPerSecond = 15, regenDelay = 6, howlRadius = 40, howlCooldown = 14, enrageDuration = 6, voice = 0.43},
    foreman = {name = "ШАХТЁР-БРИГАДИР", health = 900, speed = 6.5, damage = 18, interval = 1.8, xp = 220, cash = 180, color = {105, 84, 66}, size = 1.45, armor = 0.2, structureDamage = 30, aggro = 90, leash = 105, boss = true, mineOnly = true, voice = 0.46},
}
