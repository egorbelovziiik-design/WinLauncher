--!strict
return {
    wall = {order = 1, name = "Баррикада", category = "Оборона", size = {12, 7, 4}, cost = {wood = 10}, cash = 10, seconds = 3, health = 400, description = "Блокирует путь и принимает удары орды."},
    generator = {order = 2, name = "Генератор", category = "Энергия", size = {8, 5, 8}, cost = {metal = 20, wire = 10, electronics = 5}, cash = 50, seconds = 8, health = 240, power = 12, fuel = 1, description = "12 кВт. 1 топлива / минуту со склада."},
    farm = {order = 3, name = "Грядки", category = "Производство", size = {12, 2, 12}, cost = {wood = 15, metal = 4}, cash = 20, seconds = 5, health = 140, output = {food = 2}, worker = true, description = "2 еды / 30 с. Назначьте работника."},
    well = {order = 4, name = "Водокачка", category = "Производство", size = {8, 7, 8}, cost = {metal = 10, wire = 2}, cash = 30, seconds = 5, health = 220, demand = 2, output = {water = 3}, description = "3 воды / 30 с. Требует 2 кВт."},
    workshop = {order = 5, name = "Мастерская", category = "Производство", size = {16, 10, 12}, cost = {wood = 20, metal = 15, electronics = 2}, cash = 60, seconds = 10, health = 360, demand = 4, input = {scrap = 2}, output = {metal = 1, parts = 1}, worker = true, description = "2 лома → 1 металл + 1 деталь / 30 с. 4 кВт и работник. Открывает крафт."},
    lamp = {order = 6, name = "Прожектор", category = "Оборона", size = {4, 13, 4}, cost = {metal = 5, wire = 3}, cash = 15, seconds = 3, health = 100, demand = 1, description = "Освещает участок при наличии 1 кВт."},
    turret = {order = 7, name = "Автотурель", category = "Оборона", size = {8, 9, 8}, cost = {metal = 25, parts = 8, electronics = 5}, cash = 150, seconds = 12, health = 320, demand = 5, level = 2, description = "Защищает участок. 5 кВт, патроны со склада."},
    diesel = {order = 8, name = "Дизельная станция", category = "Энергия", size = {12, 6, 8}, cost = {metal = 35, wire = 12, parts = 6}, cash = 180, seconds = 12, health = 400, power = 30, fuel = 2, blueprint = "generator_plan", description = "30 кВт. 2 топлива / минуту. Нужен чертёж Виктора."},
}
