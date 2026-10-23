--!nonstrict
local View = {}
function View.build(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.paragraph(body, "Участок №" .. app.State.plot .. " • B — чертежи, V — Commander, R — поворот. Зелёный контур разрешает размещение. Сервер повторно проверяет цену, границы и пересечения.", 57)
    for _, id in app:Sorted(app.Buildings) do
        local def = app.Buildings[id]; local card = UI.card(body, 143)
        UI.label(card, def.name:upper(), 16, {Position = UDim2.fromOffset(14, 9), Size = UDim2.new(1, -28, 0, 25), Font = Enum.Font.GothamBold})
        UI.label(card, def.description, 12, {Position = UDim2.fromOffset(14, 39), Size = UDim2.new(1, -28, 0, 36), TextWrapped = true, TextColor3 = UI.Theme.muted})
        UI.label(card, UI.price(def.cost, app.Items) .. "  ·  " .. def.cash .. " Cash", 10, {Position = UDim2.fromOffset(14, 80), Size = UDim2.new(1, -28, 0, 20), TextWrapped = true, TextColor3 = UI.Theme.accent})
        UI.button(card, "РАЗМЕСТИТЬ  /  " .. def.seconds .. " c", function() app.Build:Start(id) end,
            {Position = UDim2.fromOffset(14, 106), Size = UDim2.new(1, -28, 0, 28), BackgroundColor3 = UI.Theme.raised, TextSize = 11})
    end
    UI.paragraph(body, "Построено: " .. #c.Buildings .. "/" .. app.Config.MaxBuildings .. ". Снос возвращает 25% материалов. Чужие участки защищены от действий игроков.", 42)
end
function View.base(app, body)
    local UI = app.UI; local T = UI.Theme; local s = app.State; local c = s.campaign; local p = s.power
    UI.heading(body, "УБЕЖИЩЕ / УЧАСТОК " .. s.plot)
    UI.paragraph(body, string.format("Энергия: %d кВт произведено / %d требуется / дефицит %d\nРаботники: %d  ·  Мораль: %d%%  ·  %s", p.supply, p.demand, p.deficit, #c.Workers, c.Morale, s.raid), 50)
    UI.paragraph(body, "Генераторы автоматически берут топливо со склада. Работники едят по 1 еде и воде в минуту. Производство каждые 30 с; ночью работники отдыхают. Офлайн-производства нет.", 57)
    local row = UI.row(body, 36)
    UI.button(row, "Постройки", function() app:OpenPanel("build") end, {Size = UDim2.new(1 / 3, -5, 1, 0), BackgroundColor3 = T.accent})
    UI.button(row, "Крафт", function() app:OpenPanel("craft") end, {Size = UDim2.new(1 / 3, -5, 1, 0)})
    UI.button(row, "Военный отряд", function() app:OpenPanel("squad") end, {Size = UDim2.new(1 / 3, -5, 1, 0)})
    UI.button(body, "КОНТРАКТНЫЕ СПЕЦИАЛИСТЫ  /  ЛАДА", function() app:OpenPanel("recruits") end, {Size = UDim2.new(1, -6, 0, 31), TextSize = 11})
    UI.paragraph(body, "Строитель Степан живёт на участке. Поговорите с ним по E: он возьмёт ресурсы, найдёт площадку с проходом и построит объект сам. Работа идёт только пока Степан находится у стройки.", 56)
    UI.heading(body, "СКЛАД  /  ВЗЯТЬ ИЛИ ПОЛОЖИТЬ 5")
    local ids = {}; for id, def in app.Items do if not def.weapon and not def.armor and not def.blueprint then table.insert(ids, id) end end; table.sort(ids)
    for _, id in ids do
        local amount = c.Stockpile[id] or 0; local held = app:Count(id)
        local card = UI.card(body, 67)
        UI.label(card, app.Items[id].name .. "   " .. amount .. "  /  рюкзак " .. held, 12, {Position = UDim2.fromOffset(12, 4), Size = UDim2.new(1, -24, 0, 23)})
        if amount > 0 then UI.button(card, "Взять " .. math.min(5, amount), function() app:Send("InventoryRemote", {action = "withdraw", id = id, count = math.min(5, amount)}) end, {Position = UDim2.fromOffset(12, 32), Size = UDim2.new(0.5, -18, 0, 26), TextSize = 11}) end
        if held > 0 then UI.button(card, "Положить " .. math.min(5, held), function() app:Send("InventoryRemote", {action = "deposit", id = id, count = math.min(5, held)}) end, {Position = UDim2.new(0.5, 0, 0, 32), Size = UDim2.new(0.5, -12, 0, 26), TextSize = 11}) end
    end
    UI.heading(body, "РАБОТНИКИ И ПОСТРОЙКИ")
    for index, w in c.Workers do
        local b = app:FindBuilding(w.job)
        UI.paragraph(body, w.name .. " — " .. (b and app.Buildings[b.id].name or "не назначен") .. ". Назначьте у нужной постройки ниже.", 32)
        if b then UI.button(body, "Освободить: " .. w.name, function() app:Send("NPCRemote", {action = "assign", worker = index, uid = ""}) end, {Size = UDim2.new(1, -6, 0, 28), TextSize = 11}) end
    end
    for _, b in c.Buildings do
        local def = app.Buildings[b.id]
        local builderProject = c.Builder and c.Builder.order == b.uid and (c.Builder.remaining or 0) > 0
        local ready = not builderProject and b.readyAt <= s.serverTime
        local construction = builderProject and ("Степан: " .. math.ceil(c.Builder.remaining or 0) .. " с работы · " .. (c.Builder.working and "СТРОИТ" or "ПАУЗА / В ПУТИ"))
            or (not ready and "Строительство: " .. math.max(0, b.readyAt - s.serverTime) .. " c" or nil)
        local card = UI.card(body, def.worker and 140 or 100)
        UI.label(card, def.name .. "  /  " .. math.ceil(b.hp) .. " HP", 14, {Position = UDim2.fromOffset(12, 7), Size = UDim2.new(1, -24, 0, 23), Font = Enum.Font.GothamBold})
        UI.label(card, construction or p.powered[b.uid] and "ГОТОВО / ЭНЕРГИЯ ЕСТЬ" or "ОЖИДАЕТ ТОПЛИВО / ЭНЕРГИЮ", 10, {Position = UDim2.fromOffset(12, 34), Size = UDim2.new(1, -24, 0, 20), TextColor3 = p.powered[b.uid] and T.success or T.accent})
        UI.button(card, "Ремонт", function() app:Send("BuildRemote", {action = "repair", uid = b.uid}) end, {Position = UDim2.fromOffset(12, 62), Size = UDim2.new(0.5, -18, 0, 27), TextSize = 11})
        UI.button(card, app.ConfirmDemolish == b.uid and "ТОЧНО СНЕСТИ?" or "Снести · 25%", function()
            if app.ConfirmDemolish == b.uid then app:Send("BuildRemote", {action = "demolish", uid = b.uid}); app.ConfirmDemolish = nil
            else app.ConfirmDemolish = b.uid; app:RefreshPanel() end
        end, {Position = UDim2.new(0.5, 0, 0, 62), Size = UDim2.new(0.5, -12, 0, 27), TextSize = 11, TextColor3 = T.danger})
        if def.worker then
            for i, w in c.Workers do
                UI.button(card, w.name .. (w.job == b.uid and " ✓" or " → сюда"), function() app:Send("NPCRemote", {action = "assign", worker = i, uid = b.uid}) end, {Position = UDim2.new((i - 1) / 2, 12, 0, 102), Size = UDim2.new(0.5, -20, 0, 27), TextSize = 11})
            end
        end
    end
    if #c.Buildings == 0 then UI.paragraph(body, "Здесь появятся ваши здания. Начните с баррикады и генератора.", 45) end
end
function View.craft(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.paragraph(body, "Нужна готовая мастерская на своём участке. Материалы берутся со склада и из рюкзака. Очередь — один заказ; сохраняется при выходе.", 55)
    if c.Craft then
        local remaining = math.max(0, c.Craft.readyAt - app.State.serverTime)
        UI.heading(body, app.Economy.Recipes[c.Craft.id].name .. (remaining > 0 and " • " .. remaining .. " с" or " • ГОТОВО"))
        if remaining == 0 then UI.button(body, "ЗАБРАТЬ ЗАКАЗ", function() app:Send("CraftingRemote", {action = "collect"}) end, {BackgroundColor3 = UI.Theme.accent}) end
    end
    for _, id in app:Sorted(app.Economy.Recipes) do
        local recipe = app.Economy.Recipes[id]; local card = UI.card(body, 113)
        local required = recipe.requiresQuest and c.Quests[recipe.requiresQuest]
        local locked = recipe.requiresQuest and (not required or required.status ~= "RewardClaimed")
        UI.label(card, recipe.name .. (locked and "  /  ЗАКРЫТО" or ""), 16, {Position = UDim2.fromOffset(14, 10), Size = UDim2.new(1, -28, 0, 25), Font = Enum.Font.GothamBold, TextColor3 = locked and UI.Theme.muted or UI.Theme.text})
        UI.label(card, locked and "Требуется доступ в НОВЫЙ РУБЕЖ" or UI.price(recipe.input, app.Items) .. " / " .. recipe.seconds .. " с", 11, {Position = UDim2.fromOffset(14, 43), Size = UDim2.new(1, -28, 0, 22), TextColor3 = locked and UI.Theme.danger or UI.Theme.muted, TextWrapped = true})
        if not c.Craft and not locked then UI.button(card, "СОЗДАТЬ", function() app:Send("CraftingRemote", {action = "start", id = id}) end, {Position = UDim2.fromOffset(14, 75), Size = UDim2.new(1, -28, 0, 28), TextSize = 11}) end
    end
end
function View.builder(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.paragraph(body, "Степан списывает точную стоимость из склада и рюкзака. Сервер выбирает ближайшую точку сетки с доступной стороной, после чего Степан действительно идёт туда и работает. Путь, разговор или заражённые ставят прогресс на паузу; офлайн-стройки нет.", 86)
    local active = c.Builder and app:FindBuilding(c.Builder.order)
    if active and (c.Builder.remaining or 0) > 0 then
        UI.heading(body, "ПРОЕКТ: " .. app.Buildings[active.id].name:upper() .. " / " .. math.ceil(c.Builder.remaining or 0) .. " с РАБОТЫ")
        UI.paragraph(body, c.Builder.working and "Степан на площадке — строительство идёт." or "Прогресс остановлен: Степан в пути, разговаривает, обходит преграду или спасается от заражённых.", 42)
    end
    for _, id in app:Sorted(app.Buildings) do
        local def = app.Buildings[id]; local card = UI.card(body, 126)
        UI.label(card, def.name .. "  /  " .. def.category, 15, {Position = UDim2.fromOffset(13, 8), Size = UDim2.new(1, -26, 0, 24), Font = Enum.Font.GothamBold})
        UI.label(card, def.description, 11, {Position = UDim2.fromOffset(13, 35), Size = UDim2.new(1, -26, 0, 34), TextWrapped = true, TextColor3 = UI.Theme.muted})
        UI.label(card, UI.price(def.cost, app.Items) .. "  ·  " .. def.cash .. " Cash", 10, {Position = UDim2.fromOffset(13, 72), Size = UDim2.new(1, -26, 0, 18), TextColor3 = UI.Theme.accent, TextWrapped = true})
        UI.button(card, "ПЕРЕДАТЬ РЕСУРСЫ И СТРОИТЬ", function() app:Send("NPCRemote", {action = "builder", id = id}) end,
            {Position = UDim2.fromOffset(13, 96), Size = UDim2.new(1, -26, 0, 24), TextSize = 10})
    end
end
function View.squad(app, body)
    local UI = app.UI; local c = app.State.campaign; local q = c.Quests.rescue_fireteam
    UI.paragraph(body, "Рада и Глеб появляются только после задания старшины Бориса. Их военная броня снижает входящий урон на 42%; карабины расходуют реальные винтовочные патроны со склада. Бойцы не прыгают через стены и обходят постройки.", 72)
    if not q or q.status ~= "RewardClaimed" then
        UI.heading(body, "ОТРЯД ЕЩЁ НЕ РАЗБЛОКИРОВАН")
        UI.paragraph(body, "Сначала выполните у Алекса «ВТОРОЙ РУБЕЖ» (25 зомби), затем возьмите у военного Бориса задание «БОЙЦЫ БОРИСА» и спасите патруль в НОВОМ РУБЕЖЕ.", 62)
        return
    end
    local squad = c.Squad
    local names = {base = "ОХРАНА БАЗЫ", follow = "СОПРОВОЖДЕНИЕ", gather = "ЭКСПЕДИЦИЯ"}
    UI.heading(body, "ТЕКУЩИЙ ПРИКАЗ: " .. (names[squad.command] or "ОЖИДАНИЕ"))
    if squad.command == "gather" then UI.paragraph(body, "Возвращение примерно через " .. math.max(0, squad.returnAt - app.State.serverTime) .. " с. Отзыв отменит добычу.", 36) end
    UI.paragraph(body, "Боеприпасы на складе: " .. (c.Stockpile.rifle_ammo or 0) .. ". Командная радиомачта с энергией увеличивает результат экспедиции на 50%.", 46)
    local row = UI.row(body, 38)
    UI.button(row, "Охранять базу", function() app:Send("NPCRemote", {action = "squad", command = "base"}) end, {Size = UDim2.new(0.5, -5, 1, 0), BackgroundColor3 = squad.command == "base" and UI.Theme.accent or UI.Theme.raised})
    UI.button(row, "Следовать за мной", function() app:Send("NPCRemote", {action = "squad", command = "follow"}) end, {Size = UDim2.new(0.5, -5, 1, 0), BackgroundColor3 = squad.command == "follow" and UI.Theme.accent or UI.Theme.raised})
    UI.heading(body, "90-СЕКУНДНАЯ ДОБЫВАЮЩАЯ ЭКСПЕДИЦИЯ")
    for _, option in {{"metal", "Металл / лом / пластины"}, {"mine", "Руда / уголь / металл"}, {"wood", "Дерево / травы / ткань"}} do
        UI.button(body, option[2], function() app:Send("NPCRemote", {action = "squad", command = "gather", resource = option[1]}) end,
            {BackgroundColor3 = squad.command == "gather" and squad.resource == option[1] and UI.Theme.accent or UI.Theme.raised})
    end
end
function View.recruits(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.paragraph(body, "Вербовщик Лада находится во втором городе. Найм оплачивается только Survival Cash и сохраняется в кампании. Купленные специалисты — реальные NPC на вашей базе с рабочими серверными способностями.", 67)
    for _, id in app:Sorted(app.Recruits) do
        local def = app.Recruits[id]; local record = c.Recruits[id]
        local card = UI.card(body, record and (id == "gatherer" and 205 or 164) or 145)
        UI.label(card, def.name .. "  /  " .. def.role:upper(), 15, {Position = UDim2.fromOffset(13, 9), Size = UDim2.new(1, -26, 0, 24), Font = Enum.Font.GothamBold})
        UI.label(card, def.description, 11, {Position = UDim2.fromOffset(13, 38), Size = UDim2.new(1, -26, 0, 40), TextWrapped = true, TextColor3 = UI.Theme.muted})
        if not record then
            UI.label(card, def.price .. " SURVIVAL CASH  /  один раз", 11, {Position = UDim2.fromOffset(13, 83), Size = UDim2.new(1, -26, 0, 20), TextColor3 = UI.Theme.accent})
            UI.button(card, "НАНЯТЬ У ЛАДЫ", function() app:Send("NPCRemote", {action = "recruit", id = id}) end,
                {Position = UDim2.fromOffset(13, 109), Size = UDim2.new(1, -26, 0, 27), BackgroundColor3 = UI.Theme.accent, TextSize = 11})
        else
            local title = record.command == "follow" and "СЛЕДУЕТ" or record.command == "gather" and "ЭКСПЕДИЦИЯ" or "НА БАЗЕ"
            UI.label(card, "НАНЯТ  •  " .. title, 10, {Position = UDim2.fromOffset(13, 82), Size = UDim2.new(1, -26, 0, 18), TextColor3 = UI.Theme.success})
            local row = UI.row(card, 30); row.Position = UDim2.fromOffset(13, 107); row.Size = UDim2.new(1, -26, 0, 30)
            UI.button(row, "На базу", function() app:Send("NPCRemote", {action = "recruitCommand", id = id, command = "base"}) end,
                {Size = UDim2.new(0.5, -5, 1, 0), BackgroundColor3 = record.command == "base" and UI.Theme.accent or UI.Theme.raised, TextSize = 11})
            UI.button(row, "За мной", function() app:Send("NPCRemote", {action = "recruitCommand", id = id, command = "follow"}) end,
                {Size = UDim2.new(0.5, -5, 1, 0), BackgroundColor3 = record.command == "follow" and UI.Theme.accent or UI.Theme.raised, TextSize = 11})
            if id == "gatherer" then
                UI.label(card, record.command == "gather" and ("Возвращение через " .. math.max(0, record.returnAt - app.State.serverTime) .. " с") or "Отправить на 120 секунд:", 10,
                    {Position = UDim2.fromOffset(13, 143), Size = UDim2.new(1, -26, 0, 18), TextColor3 = UI.Theme.muted})
                local gather = UI.row(card, 28); gather.Position = UDim2.fromOffset(13, 169); gather.Size = UDim2.new(1, -26, 0, 28)
                for index, option in {{"metal", "Металл"}, {"mine", "Шахта"}, {"wood", "Дерево"}} do
                    UI.button(gather, option[2], function() app:Send("NPCRemote", {action = "recruitCommand", id = id, command = "gather", resource = option[1]}) end,
                        {Size = UDim2.new(1 / 3, -5, 1, 0), TextSize = 10})
                end
            end
        end
    end
end
return View
