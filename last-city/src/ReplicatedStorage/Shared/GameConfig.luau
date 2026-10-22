--!strict
return table.freeze({
    Version = "0.2.0 — MERIDIAN REBUILT", SchemaVersion = 1,
    Title = "LAST CITY 3D", Subtitle = "DEAD WORLD: ULTIMATE SURVIVAL",
    MaxPlayers = 6, MaxZombies = 24, AIInterval = 0.25, PathInterval = 2.5,
    StudioUseDataStore = false, -- Explicit opt-in for cloud-save testing; downloaded Studio files use SessionOnly.
    AutosaveSeconds = 55, LeaseSeconds = 180, DataStoreName = "LastCity_Profile_v1",
    DayLength = 900, InventorySlots = 24, CarryWeight = 180,
    Grid = 4, PlotHalfSize = 48, MaxBuildings = 24, InteractRange = 13,
    PlotReserved = {
        {x = 0, z = 36, hx = 8, hz = 10, reason = "Оставьте свободным вход и терминал"},
        {x = -22, z = 35, hx = 7, hz = 6, reason = "Здесь расположена палатка жителей"},
    },
    Plots = {
        {x = -144, z = -252}, {x = 0, z = -252}, {x = 144, z = -252},
        {x = -144, z = -396}, {x = 0, z = -396}, {x = 144, z = -396},
    },
    TutorialSteps = {
        {key = "move", text = "Пройдите 12 метров: WASD / левый стик"},
        {key = "pickup", text = "Подберите припасы: подойдите к ящику и удерживайте E"},
        {key = "inventory", text = "Откройте рюкзак: I / кнопка «Рюкзак»"},
        {key = "attack", text = "Используйте лом: ЛКМ / кнопка атаки"},
        {key = "kill", text = "Победите зомби за северными воротами"},
        {key = "talk", text = "Поговорите с капитаном Алексом: E"},
        {key = "quest", text = "Примите задание «Первая кровь»"},
        {key = "resource", text = "Соберите металл на улице за воротами"},
        {key = "build", text = "На своём участке откройте B и постройте стену"},
        {key = "generator", text = "Постройте генератор: B. Ресурсы есть на складе"},
        {key = "reward", text = "Завершите задание и получите награду у NPC"},
        {key = "home", text = "Вернитесь в убежище и заберите награду обучения"},
    },
    Audio = { -- Built-in Roblox sound. No third-party music or unlicensed IDs.
        UI = "rbxasset://sounds/electronicpingshort.wav",
        Menu = "", Explore = "", Combat = "", -- Supply your OWN permitted asset IDs.
    },
})
