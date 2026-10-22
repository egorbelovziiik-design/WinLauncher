--!nonstrict
return function(app, body)
    local UI = app.UI; local T = UI.Theme; local c = app.State.campaign
    local weight = 0; for _, e in c.Inventory do weight += app.Items[e.id].weight * e.count end
    UI.paragraph(body, string.format("ПРОСТОЙ РЮКЗАК   /   %d из %d ячеек   /   %.1f из %d кг\nКлик — действие. Стрелки меняют порядок ячеек. Склад доступен только на вашем участке.", #c.Inventory, app.Config.InventorySlots, weight, app.Config.CarryWeight), 46)
    local categories = {"Все", "Оружие", "Боеприпасы", "Ресурсы", "Расходники", "Броня", "Чертежи"}
    for row = 0, 1 do
        local line = UI.row(body, 30)
        for i = 1, 4 do
            local filter = categories[row * 4 + i]
            if filter then UI.button(line, filter, function() app.InventoryFilter = filter; app:RefreshPanel(true) end, {Size = UDim2.new(0.25, -6, 1, 0), TextSize = 10, BackgroundColor3 = (app.InventoryFilter or "Все") == filter and T.accent or T.raised}) end
        end
    end
    local tools = UI.row(body, 30)
    UI.button(tools, "Сортировать", function() app:Send("InventoryRemote", {action = "sort"}) end, {Size = UDim2.new(0.5, -4, 1, 0), TextSize = 11})
    UI.button(tools, "Склад базы", function() app:OpenPanel("base") end, {Size = UDim2.new(0.5, -4, 1, 0), TextSize = 11})
    local shown = 0
    for index, e in c.Inventory do
        local def = app.Items[e.id]
        if (app.InventoryFilter or "Все") ~= "Все" and app.InventoryFilter ~= def.category then continue end
        shown += 1
        local card = UI.card(body, 121)
        UI.frame(card, {Position = UDim2.fromOffset(0, 10), Size = UDim2.fromOffset(3, 92), BackgroundColor3 = Color3.fromHex(def.color)})
        UI.label(card, string.format("%02d   %s   ×%d", index, def.name, e.count), 15, {Position = UDim2.fromOffset(13, 10), Size = UDim2.new(1, -30, 0, 23), Font = Enum.Font.GothamBold})
        local detail = def.category .. "   ·   " .. string.format("%.1f кг", def.weight * e.count)
        if def.weapon then
            local w = app.Weapons[e.id]
            detail = w.rarity .. "  ·  урон " .. math.floor(w.damage * (1 + e.upgrade * 0.06)) .. (w.pellets and "×" .. w.pellets or "") .. "  ·  +" .. e.upgrade .. "  ·  прочность " .. math.floor(e.durability) .. "%"
            if not w.melee then detail ..= "  ·  магазин " .. e.ammo .. "/" .. w.magazine end
        elseif def.armor then detail = "Защита " .. math.floor(def.armor * 100) .. "%  ·  Прочность " .. math.floor(e.durability) .. "%"
        elseif def.blueprint then detail = "Открывает дизельную станцию в меню строительства" end
        UI.label(card, detail, 11, {Position = UDim2.fromOffset(13, 38), Size = UDim2.new(1, -26, 0, 22), TextColor3 = T.muted, TextWrapped = true})
        local row = UI.row(card, 32); row.Position = UDim2.fromOffset(12, 75); row.Size = UDim2.new(1, -24, 0, 32)
        local function button(text, callback, width, accent)
            UI.button(row, text, callback, {Size = UDim2.fromOffset(width or 90, 32), TextSize = 11, BackgroundColor3 = accent and T.accent or T.raised})
        end
        if def.weapon or def.armor then
            if c.Equipped == e.uid or c.Armor == e.uid then UI.label(row, "ЭКИПИРОВАНО", 10, {Size = UDim2.fromOffset(110, 32), TextColor3 = T.success})
            else button("Экипировать", function() app:Send("InventoryRemote", {action = "equip", uid = e.uid}) end, 110, true) end
        elseif def.use then button("Использовать", function() app:Send("InventoryRemote", {action = "use", uid = e.uid}) end, 104, true)
        elseif not def.blueprint then button("На склад", function() app:Send("InventoryRemote", {action = "deposit", id = e.id, count = e.count}) end, 90) end
        if e.count > 1 then button("½", function() app:Send("InventoryRemote", {action = "split", uid = e.uid, count = math.floor(e.count / 2)}) end, 32) end
        if not def.weapon and not def.armor and not def.blueprint then button("Бросить 1", function() app:Send("InventoryRemote", {action = "drop", uid = e.uid, count = 1}) end, 75) end
        if index > 1 then button("↑", function() app:Send("InventoryRemote", {action = "move", uid = e.uid, index = index - 1}) end, 28) end
        if index < #c.Inventory then button("↓", function() app:Send("InventoryRemote", {action = "move", uid = e.uid, index = index + 1}) end, 28) end
    end
    if shown == 0 then UI.paragraph(body, "В этой категории пока нет предметов.", 35) end
end
