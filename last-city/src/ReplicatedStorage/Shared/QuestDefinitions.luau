--!strict
return {
    first_blood = {
        order = 1, name = "ПЕРВАЯ КРОВЬ", type = "Сюжетное", npc = "alex", location = "Северные ворота",
        description = "Заражённые подходят к воротам. Уничтожь пять обычных зомби и возвращайся. Я выдам тебе пистолет и патроны.",
        objectives = {{event = "kill", target = "walker", count = 5, label = "Обычные зомби"}},
        reward = {coins = 200, xp = 150, items = {pistol = 1, ammo = 24}}, reputation = "SURVIVORS",
    },
    lost_supplies = {
        order = 2, name = "ПОТЕРЯННЫЕ ПРИПАСЫ", type = "Побочное", npc = "max", location = "Магазин MERIDIAN",
        description = "В MERIDIAN, за воротами слева, остались три оранжевых ящика. Найди каждый и возвращайся за наградой.",
        objectives = {{event = "supply", target = "market", count = 3, label = "Разные ящики MERIDIAN"}},
        reward = {coins = 350, xp = 250, items = {medkit = 2}}, reputation = "TRADERS",
    },
    city_power = {
        order = 3, name = "ЭЛЕКТРИЧЕСТВО ДЛЯ ГОРОДА", type = "Строительство", npc = "victor", location = "Промзона / ваш участок",
        description = "После принятия задания собери 20 металла, 10 проводов и 5 электронных компонентов. Затем построй генератор на своём участке. Топливо он берёт со склада.",
        objectives = {
            {event = "collect", target = "metal", count = 20, label = "Найти металл"},
            {event = "collect", target = "wire", count = 10, label = "Найти провода"},
            {event = "collect", target = "electronics", count = 5, label = "Найти электронику"},
            {event = "build", target = "generator", count = 1, label = "Построить генератор"},
        },
        reward = {coins = 500, xp = 400, items = {generator_plan = 1}}, reputation = "SURVIVORS",
    },
    daily_hunt = {
        order = 4, name = "ДЕЖУРСТВО У ВОРОТ", type = "Ежедневное", npc = "alex", location = "Город", period = 86400,
        requires = "first_blood", description = "Победи 20 заражённых любого типа. Задание обновляется в полночь UTC.",
        objectives = {{event = "kill", target = "*", count = 20, label = "Заражённые"}},
        reward = {coins = 100, xp = 200, items = {ammo = 16}}, reputation = "SURVIVORS",
    },
}
