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
    for _, item in {{"Громкость UI", "uiSound", 0.1}, {"Чувствительность мыши", "sensitivity", 0.1}, {"Масштаб интерфейса", "scale", 0.05}} do
        local row = UI.card(body, 54)
        UI.label(row, item[1] .. "  " .. math.floor(s[item[2]] * 100) .. "%", 12, {Position = UDim2.fromOffset(12, 0), Size = UDim2.new(1, -115, 1, 0)})
        for index, delta in {-item[3], item[3]} do
            UI.button(row, index == 1 and "−" or "+", function() app:Send("SessionRemote", {action = "setting", id = item[2], value = s[item[2]] + delta}) end,
                {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, index == 1 and -57 or -12, 0, 10), Size = UDim2.fromOffset(37, 34), TextSize = 17})
        end
    end
    UI.button(body, "Обучение / награда за первый день", function() app:OpenPanel("tutorial") end)
    UI.heading(body, "УПРАВЛЕНИЕ")
    UI.paragraph(body, "WASD — движение / Space — прыжок\nShift — бег / Ctrl — тихий шаг с приседанием камеры\nЛКМ — атака / R — перезарядка или поворот постройки\nE — NPC и предметы / F — фонарик / V — Commander\nI — рюкзак / J — квесты / B — строительство / G — база / M — карта\n1, 2, 3 — быстрые слоты\nНа телефоне: штатный стик Roblox и кнопки АТАКА, R, V, БЕГ.", 155)
    UI.paragraph(body, "Low отключает тени и эффекты постобработки; Medium оставляет цветокоррекцию. В этом срезе нет саундтрека и переназначения клавиш — не показываем неработающие настройки.", 60)
end
function View.updates(app, body)
    local UI = app.UI
    UI.heading(body, "0.2.0  /  MERIDIAN REBUILT")
    UI.paragraph(body, "Это первый вертикальный срез, не финальная игра из полного технического задания. Исходники и бинарный файл места собираются из одного проекта.", 58)
    UI.heading(body, "РЕАЛИЗОВАНО В КОДЕ")
    UI.paragraph(body, "• Перестроенные дороги, аптека, полиция, АЗС и промзона.\n• NPC Алекс, Макс, Виктор, диалоги и 6 заданий.\n• Лом, пистолет, карабин; 3 типа зомби.\n• Инвентарь, серверный бой, улучшения, броня.\n• 8 построек, энергия, производство, работники и крафт.\n• Открываемые двери, крыши, бинты и новый крафт.\n• Профиль, косметика, достижения, DataStore и ограничения запросов.", 165)
    UI.heading(body, "ЕЩЁ НЕ РЕАЛИЗОВАНО")
    UI.paragraph(body, "Транспорт, 20 локаций, боссы, PvP, полная сюжетная кампания, команды с совместным строительством, экспедиции, караваны, полноценная погодная симуляция и саундтрек. Кнопок-заглушек для них нет.", 84)
    UI.heading(body, "ПРОВЕРКА")
    UI.paragraph(body, "Автотесты проверяют доменную логику и структуру файла. Roblox Studio, визуальные эффекты, физика, TeleportService и живой DataStore требуют отдельных тестов. Смотрите docs/TEST_REPORT.md и STUDIO_QA.md в исходниках.", 78)
end
return View
