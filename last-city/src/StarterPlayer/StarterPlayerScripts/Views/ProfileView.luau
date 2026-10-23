--!nonstrict
local View = {}
function View.profile(app, body)
    local UI = app.UI; local p = app.State.profile; local c = app.State.campaign
    local card = UI.card(body, 145)
    UI.new("ImageLabel", {Position = UDim2.fromOffset(15, 15), Size = UDim2.fromOffset(90, 90), BackgroundTransparency = 1,
        Image = "rbxthumb://type=AvatarHeadShot&id=" .. app.Player.UserId .. "&w=150&h=150"}, card)
    UI.label(card, app.Player.DisplayName, 22, {Position = UDim2.fromOffset(120, 18), Size = UDim2.new(1, -135, 0, 30), Font = Enum.Font.GothamBold})
    UI.label(card, "@" .. app.Player.Name .. "\nУРОВЕНЬ " .. app:Level() .. "  /  " .. c.XP .. " XP\n" .. p.Coins .. " COINS", 13, {Position = UDim2.fromOffset(120, 55), Size = UDim2.new(1, -135, 0, 77), TextColor3 = UI.Theme.muted})
    UI.heading(body, "СТАТИСТИКА ПРОФИЛЯ")
    for _, entry in {{"Побеждено зомби", p.Stats.kills}, {"Выполнено заданий", p.Stats.quests}, {"Построено зданий", p.Stats.buildings}, {"Время в игре", math.floor(p.Stats.seconds / 60) .. " мин"}, {"Кампания", app.State.mode == "solo" and "Одиночная / отдельное сохранение" or "Общий PvE-сервер / личная база"}, {"Survival Cash", c.Cash}} do
        local row = UI.card(body, 42)
        UI.label(row, entry[1] .. "    " .. tostring(entry[2]), 13, {Position = UDim2.fromOffset(12, 0), Size = UDim2.new(1, -24, 1, 0)})
    end
    UI.paragraph(body, app:SaveText(), 40, app.State.save == "SessionOnly" and UI.Theme.accent or UI.Theme.muted)
    UI.button(body, "СОХРАНИТЬ СЕЙЧАС  /  не чаще раза в 20 секунд", function() app:Send("SessionRemote", {action = "save"}) end, {BackgroundColor3 = UI.Theme.accent, TextSize = 12})
end
function View.achievements(app, body)
    local UI = app.UI
    for _, id in app:Sorted(app.Economy.Achievements) do
        local def = app.Economy.Achievements[id]; local done = app.State.profile.Achievements[id]
        local card = UI.card(body, 80)
        UI.label(card, (done and "✓  " or "◇  ") .. def.name, 16, {Position = UDim2.fromOffset(14, 10), Size = UDim2.new(1, -28, 0, 26), Font = Enum.Font.GothamBold, TextColor3 = done and UI.Theme.success or UI.Theme.text})
        UI.label(card, math.min(def.count, app.State.profile.Stats[def.stat] or 0) .. "/" .. def.count .. "   ·   " .. def.coins .. " Coins" .. (done and "  /  получено" or "  /  автоматически"), 12, {Position = UDim2.fromOffset(14, 44), Size = UDim2.new(1, -28, 0, 25), TextColor3 = UI.Theme.muted})
    end
end
function View.settings(app, body)
    local UI = app.UI; local s = app.State.profile.Settings
    local function choices(title, key, values)
        UI.heading(body, title)
        local row = UI.row(body, 34)
        for _, value in values do
            UI.button(row, value, function() app:Send("SessionRemote", {action = "setting", id = key, value = value}) end,
                {Size = UDim2.new(1 / #values, -6, 1, 0), BackgroundColor3 = s[key] == value and UI.Theme.accent or UI.Theme.raised, TextSize = 11})
        end
    end
    choices("ГРАФИКА / ТЕНИ И ПОСТОБРАБОТКА", "graphics", {"Low", "Medium", "High", "Ultra"})
    choices("СЛОЖНОСТЬ ВЫЖИВАНИЯ / РАСХОД ЕДЫ И ВОДЫ", "difficulty", {"Relaxed", "Normal", "Hard"})
    for _, item in {{"Громкость UI", "uiSound", 0.1}, {"Музыка и атмосфера", "music", 0.1}, {"Звуки боя", "effects", 0.1}, {"Голоса NPC / English TTS", "voice", 0.1}, {"Чувствительность мыши", "sensitivity", 0.1}, {"Масштаб интерфейса", "scale", 0.05}} do
        local row = UI.card(body, 54)
        UI.label(row, item[1] .. "  " .. math.floor(s[item[2]] * 100) .. "%", 12, {Position = UDim2.fromOffset(12, 0), Size = UDim2.new(1, -115, 1, 0)})
        for index, delta in {-item[3], item[3]} do
            UI.button(row, index == 1 and "−" or "+", function() app:Send("SessionRemote", {action = "setting", id = item[2], value = s[item[2]] + delta}) end,
                {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, index == 1 and -57 or -12, 0, 10), Size = UDim2.fromOffset(37, 34), TextSize = 17})
        end
    end
    UI.button(body, s.firstPerson and "КАМЕРА: ОТ ПЕРВОГО ЛИЦА" or "КАМЕРА: ОТ ТРЕТЬЕГО ЛИЦА", function()
        app:Send("SessionRemote", {action = "setting", id = "firstPerson", value = not s.firstPerson})
    end, {BackgroundColor3 = s.firstPerson and UI.Theme.accent or UI.Theme.raised})
    UI.button(body, "Обучение / награда за первый день", function() app:OpenPanel("tutorial") end)
    UI.heading(body, "УПРАВЛЕНИЕ")
    UI.paragraph(body, "WASD — движение / Space — прыжок\nShift — бег / Ctrl — тихий шаг с приседанием камеры\nЛКМ — атака / R — перезарядка или поворот постройки\nE — NPC и предметы / F — фонарик / V — Commander / Z — вид от первого лица\nK — рюкзак / J — квесты / B — строительство / G — база / M — карта\n1–6 — сохранённые быстрые слоты (1 всегда переключает лом)\nНа телефоне: штатный стик Roblox и кнопки АТАКА, R, V, 1P, БЕГ.", 155)
    UI.paragraph(body, "Low отключает тени и эффекты постобработки; Medium оставляет цветокоррекцию. Музыка/атмосфера и эффекты управляются отдельно и используют только встроенные звуки Roblox. Переназначения клавиш пока нет.", 66)
end
function View.updates(app, body)
    local UI = app.UI
    UI.heading(body, "0.6.1  /  ECHOES OF STEEL")
    UI.paragraph(body, "Это развиваемый настоящий PvE-срез. Цены, задания, наём, добыча, бой, строительство и сохранение подтверждает сервер; интерфейс не выдаёт фиктивных наград.", 66)
    UI.heading(body, "НОВОЕ В ЭТОЙ ВЕРСИИ")
    UI.paragraph(body, "• ИГРАТЬ теперь сам восстанавливает отсутствующего, недогруженного или погибшего персонажа; повторные клики не создают второй переход.\n• Каждая завершённая грядка даёт ровно +10 Survival Cash в секунду и плавное зелёное «+10 CASH»; постоянные Coins не затрагиваются.\n• Две парящие доски заземлены как поддоны склада дерева. Терминал E переносит настоящее дерево из рюкзака, а сохранённая личная куча растёт.\n• Быстрая панель расширена до шести сохранённых слотов. «Экипировать» атомарно занимает первый свободный; в рюкзаке слот можно назначить или очистить. Клавиша 1 по-прежнему переключает лом.\n• Добавлены четыре меча, пять огнестрельных образцов и четыре раздельных типа боеприпасов. У каждого есть серверные параметры, модель, магазин, перезарядка, продажа и рецепт.\n• Осколочная, зажигательная и светошумовая гранаты реально расходуются сервером: действуют урон с падением, горение или ограниченное оглушение. Friendly fire не добавлен.\n• К четырём новым комплектам брони добавлены броня следопыта и сапёра; все видимы на персонаже, ремонтируются и создаются, а сапёрская броня реально снижает взрывной урон.\n• Сохранены НОВЫЙ РУБЕЖ, Prism Cave, Радужный Страж, специалисты, первое лицо, адаптивный звук и guarded engine TTS версии 0.6.0.", 440)
    UI.heading(body, "СОХРАНЁННЫЕ КОНТРАКТЫ")
    UI.paragraph(body, "Обычный заражённый наносит ровно 5 HP. Награда шахты остаётся атомарной: Glock 10/10 и ровно 48 запасных патронов. Клавиша 1 переключает лом, K открывает рюкзак, I свободна. Coins отделены от Survival Cash; friendly fire и чужое строительство блокируются сервером.", 92)
    UI.heading(body, "ПРОВЕРКА")
    UI.paragraph(body, "Автотесты компилируют исходники, исполняют доменную/adapter-логику и независимо разбирают бинарный place. Roblox Studio, визуальная физика, Pathfinding, живой DataStore, TTS и мультиплеер требуют отдельного ручного gate — docs/STUDIO_QA.md.", 88)
end
return View
