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
    ash_reaver = {
        order = 8, name = "ПЕПЕЛЬ НА ОКРАИНЕ", type = "Босс", npc = "alex", location = "Три заражённых очага вокруг Меридиана",
        requires = "first_blood", description = "Пепельный Жнец меняет логово после каждой победы. Проверь отмеченные на карте зоны босса, уничтожь его и вернись к Алексу. Попадания в голову наносят критический урон.",
        objectives = {{event = "kill", target = "reaver", count = 1, label = "Пепельный Жнец"}},
        reward = {coins = 500, xp = 475, items = {scout_vest = 1, ammo = 24}, loadedWeapons = {{id = "revolver", count = 1, ammo = 6}}}, reputation = "SURVIVORS",
    },
    mine_foreman = {
        order = 9, name = "СЕРДЦЕ ШАХТЫ", type = "Босс", npc = "ira", location = "Шахта «Чёрный пласт» — восточный карьер",
        description = "Ира слышала удары из закрытой выработки. Прими задание, спустись в шахту и уничтожь Бригадира. Его усиленная шахтная охрана не покидает выработку. Награда выдаётся сразу после победы.",
        objectives = {{event = "kill", target = "foreman", count = 1, label = "Шахтёр-бригадир"}},
        reward = {coins = 600, xp = 650, items = {ammo = 48}, loadedWeapons = {{id = "glock", count = 1, ammo = 10}}},
        reputation = "SURVIVORS", autoClaim = true,
    },
    second_city = {
        order = 10, name = "ВТОРОЙ РУБЕЖ", type = "Сюжетное", npc = "alex", location = "Меридиан / вертолётная площадка",
        requires = "first_blood",
        description = "Алекс откроет воздушный маршрут к укреплённому району НОВЫЙ РУБЕЖ, если ты докажешь, что способен пережить дорогу. Уничтожь 25 заражённых любого типа. После выполнения вертолётный маршрут разблокируется автоматически.",
        objectives = {{event = "kill", target = "*", count = 25, label = "Любые заражённые"}},
        reward = {coins = 700, xp = 550, items = {}}, reputation = "SURVIVORS", autoClaim = true, unlock = "second_city",
    },
    rescue_fireteam = {
        order = 11, name = "БОЙЦЫ БОРИСА", type = "Отряд", npc = "boris", location = "Старшина Борис / НОВЫЙ РУБЕЖ",
        requires = "second_city",
        description = "Старшина Борис просит вытащить двух военных из отрезанного патруля. Уничтожь шесть коррозийных заражённых и Тюремщика во втором городе. После награды Рада и Глеб поселятся на твоей базе: им можно приказать следовать за тобой, охранять базу или добывать ресурсы.",
        objectives = {
            {event = "kill", target = "corrosive", count = 6, label = "Коррозийные заражённые"},
            {event = "kill", target = "warden", count = 1, label = "Заражённый Тюремщик"},
        },
        reward = {coins = 850, xp = 750, items = {rifle_ammo = 60, military_generator_plan = 1}}, reputation = "SURVIVORS", unlock = "fireteam",
    },
    harbor_aid = {
        order = 12, name = "ПОЛЕВОЙ ГОСПИТАЛЬ", type = "Исследование", npc = "nora", location = "НОВЫЙ РУБЕЖ / гражданский квартал",
        requires = "second_city",
        description = "Доктор Нора оставила три герметичных медицинских контейнера в гражданском квартале. Найди каждый контейнер и возвращайся в госпиталь.",
        objectives = {{event = "harbor_medical", target = "harbor", count = 3, label = "Медицинские контейнеры"}},
        reward = {coins = 420, xp = 360, items = {medkit = 2, bandage = 6, chemicals = 3}}, reputation = "SURVIVORS",
    },
    frontier_patrol = {
        order = 13, name = "ТЁМНЫЙ ПАТРУЛЬ", type = "Зачистка", npc = "makar", location = "НОВЫЙ РУБЕЖ / внешний периметр",
        requires = "second_city", description = "Гарнизону нужен безопасный коридор к жилым домам. Уничтожь пять сталкеров и трёх коррозийных заражённых во втором городе.",
        objectives = {
            {event = "kill", target = "stalker", count = 5, label = "Сталкеры"},
            {event = "kill", target = "corrosive", count = 3, label = "Коррозийные"},
        },
        reward = {coins = 520, xp = 480, items = {rifle_ammo = 45, medkit = 2}}, reputation = "SURVIVORS",
    },
    frontier_homes = {
        order = 14, name = "СВЕТ В ОКНАХ", type = "Исследование", npc = "lada", location = "НОВЫЙ РУБЕЖ / новая жилая улица",
        requires = "second_city", description = "Лада ищет пригодные помещения для будущих специалистов. Осмотри четыре отмеченных ящика учёта внутри новых домов.",
        objectives = {{event = "frontier_home", target = "residential", count = 4, label = "Осмотренные дома"}},
        reward = {coins = 460, xp = 390, items = {food = 5, water = 5, cloth = 8}}, reputation = "SURVIVORS",
    },
    frontier_rebuild = {
        order = 15, name = "КРЕПЧЕ БЕТОНА", type = "Снабжение", npc = "quartermaster", location = "НОВЫЙ РУБЕЖ / элитные ресурсные ящики",
        requires = "second_city", description = "Собери материалы из усиленных ящиков второго города: стальные пластины, бетон и закалённое стекло. Покупка не засчитывается — нужны найденные ресурсы.",
        objectives = {
            {event = "collect", target = "steel_plate", count = 8, label = "Найти стальные пластины"},
            {event = "collect", target = "concrete", count = 16, label = "Найти бетон"},
            {event = "collect", target = "glass", count = 10, label = "Найти стекло"},
        },
        reward = {coins = 650, xp = 520, items = {electronics = 5, battery = 2}}, reputation = "TRADERS",
    },
    prism_cave = {
        order = 16, name = "СПЕКТР ПОД ЗЕМЛЁЙ", type = "Босс", npc = "nora", location = "НОВЫЙ РУБЕЖ / PRISM CAVE",
        requires = "frontier_homes", description = "В пещере у южной жилой улицы проснулся Радужный Страж. Победи его. Каждый участник получает обычные ресурсы и 35 Coins; шанс Радужной брони — 22% (гарантия на 4-й бой), Призматического клинка — 30% (гарантия на 3-й).",
        objectives = {{event = "kill", target = "rainbow", count = 1, label = "Радужный Страж"}},
        reward = {coins = 900, xp = 800, items = {prism_shard = 8, medkit = 2}}, reputation = "SURVIVORS",
    },

}
