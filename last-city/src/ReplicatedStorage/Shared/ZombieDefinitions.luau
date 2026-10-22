--!strict
-- Damage deliberately rises in readable steps. The common infected hits for exactly 5;
-- armor is applied by CombatService before health is reduced.
return {
    walker = {name = "Заражённый", health = 72, speed = 9, damage = 5, interval = 1.55, xp = 10, cash = 7, color = {102, 119, 84}, size = 1},
    lurker = {name = "Шатун", health = 82, speed = 11, damage = 6, interval = 1.45, xp = 14, cash = 9, color = {91, 116, 104}, size = 0.96},
    runner = {name = "Бегун", health = 58, speed = 16, damage = 7, interval = 1.2, xp = 18, cash = 12, color = {142, 118, 77}, size = 0.92},
    ravager = {name = "Дикарь", health = 120, speed = 10, damage = 8, interval = 1.35, xp = 24, cash = 16, color = {122, 82, 76}, size = 1.08},
    brute = {name = "Громила", health = 190, speed = 8, damage = 10, interval = 1.7, xp = 34, cash = 23, color = {113, 104, 80}, size = 1.2, structureDamage = 15},
    armored = {name = "Бронированный", health = 250, speed = 7, damage = 12, interval = 1.85, xp = 48, cash = 34, color = {83, 101, 102}, size = 1.16, armor = 0.28, structureDamage = 20},
    tank = {name = "Тяжёлый", health = 340, speed = 6, damage = 15, interval = 2.05, xp = 62, cash = 44, color = {93, 100, 106}, size = 1.35, armor = 0.12, structureDamage = 26},
    foreman = {name = "ШАХТЁР-БРИГАДИР", health = 900, speed = 6.5, damage = 18, interval = 1.8, xp = 220, cash = 180, color = {105, 84, 66}, size = 1.45, armor = 0.2, structureDamage = 30, aggro = 90, leash = 105, boss = true},
}
