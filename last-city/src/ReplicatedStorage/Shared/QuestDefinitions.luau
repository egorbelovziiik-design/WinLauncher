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
    medical_run = {
        order = 5, name = "ПОСЛЕДНИЕ ЛЕКАРСТВА", type = "Побочное", npc = "max", location = "Аптека MERIDIAN MEDICAL",
        requires = "first_blood", description = "В аптеке на западной стороне города остались три медицинских контейнера. Открой входную дверь, осмотри стеллажи и принеси отметки Максу.",
        objectives = {{event = "medical", target = "pharmacy", count = 3, label = "Медицинские контейнеры"}},
        reward = {coins = 300, xp = 250, items = {medkit = 3, bandage = 4}}, reputation = "TRADERS",
    },
    radio_recovery = {
        order = 6, name = "ПОТЕРЯННАЯ ЧАСТОТА", type = "Исследование", npc = "victor", location = "Полиция / офис АЗС",
        requires = "first_blood", description = "В полицейском участке найдёшь журнал диспетчера. Вторую схему ищи в офисе АЗС к востоку от промзоны. Нужны оба документа — принеси их мне.",
        objectives = {{event = "intel", target = "radio", count = 2, label = "Документы ретранслятора"}},
        reward = {coins = 450, xp = 300, items = {vest = 1, ammo = 32}}, reputation = "SURVIVORS",
    },
    relief_recon = {
        order = 7, name = "ПОСЛЕДНИЙ СПИСОК", type = "Исследование", npc = "alex", location = "Эвакуационный лагерь у северных ворот",
        requires = "first_blood", description = "Сразу за воротами остался лагерь эвакуации. Найди список граждан, аварийный передатчик и жетоны спасателей. Каждый предмет засчитывается сервером только один раз.",
        objectives = {{event = "relief", target = "camp", count = 3, label = "Свидетельства эвакуации"}},
        reward = {coins = 350, xp = 300, items = {cloth = 6, water = 3, bandage = 2}}, reputation = "SURVIVORS",
    },

}
