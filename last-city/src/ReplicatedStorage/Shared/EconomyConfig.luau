--!strict
return {
    ProductionSeconds = 30, FuelSeconds = 60, WorkerSeconds = 60, StockCap = 9999,
    Shop = {
        {id = "food", price = 12, stock = 30}, {id = "water", price = 10, stock = 30},
        {id = "medkit", price = 65, stock = 8}, {id = "ammo", price = 2, stock = 200},
        {id = "fuel", price = 15, stock = 40}, {id = "metal", price = 8, stock = 60},
        {id = "wire", price = 10, stock = 40}, {id = "electronics", price = 20, stock = 20},
        {id = "scrap", price = 3, stock = 80}, {id = "wood", price = 4, stock = 80},
        {id = "cloth", price = 5, stock = 40}, {id = "bandage", price = 20, stock = 15},
        {id = "vest", price = 200, stock = 3}, {id = "rifle", price = 650, stock = 2},
    },
    Recipes = {
        bandage = {name = "Два бинта", input = {cloth = 2, water = 1}, output = {bandage = 2}, seconds = 6},
        medkit = {name = "Аптечка", input = {food = 2, water = 1}, output = {medkit = 1}, seconds = 8},
        ammo = {name = "16 патронов", input = {metal = 2, parts = 1}, output = {ammo = 16}, seconds = 10},
        vest = {name = "Бронежилет", input = {metal = 8, parts = 3}, output = {vest = 1}, seconds = 20},
    },
    Cosmetics = {
        amber = {name = "Янтарный сигнал", description = "Оранжевая раскраска оружия. Без бонусов к урону.", price = 250, color = {249, 115, 22}},
        arctic = {name = "Полярная ночь", description = "Голубая раскраска оружия. Без бонусов к урону.", price = 250, color = {56, 189, 248}},
    },
    Achievements = {
        first_kill = {name = "Первая кровь", stat = "kills", count = 1, coins = 25},
        first_quest = {name = "Надежда есть", stat = "quests", count = 1, coins = 50},
        builder = {name = "Великий строитель", stat = "buildings", count = 10, coins = 100},
        hunter = {name = "Зачистка", stat = "kills", count = 100, coins = 250},
    },
}
