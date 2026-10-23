--!nonstrict
local View = {}
function View.cosmetics(app, body)
    local UI = app.UI; local p = app.State.profile
    UI.heading(body, "ПОСТОЯННЫЙ ПРОФИЛЬ  /  " .. p.Coins .. " COINS")
    UI.paragraph(body, "Только внешний вид. Без Robux, без платного урона и PvP-преимуществ. Раскраски общие для solo и coop; покупки сохраняются вместе с профилем.", 55)
    for _, id in app:Sorted(app.Economy.Cosmetics) do
        local def = app.Economy.Cosmetics[id]; local card = UI.card(body, 154)
        local swatch = UI.frame(card, {Position = UDim2.fromOffset(16, 16), Size = UDim2.fromOffset(46, 46), BackgroundColor3 = Color3.fromRGB(table.unpack(def.color))}); UI.corner(swatch, 8)
        UI.label(card, def.name, 17, {Position = UDim2.fromOffset(78, 15), Size = UDim2.new(1, -94, 0, 27), Font = Enum.Font.GothamBold})
        UI.label(card, def.price .. " COINS", 12, {Position = UDim2.fromOffset(78, 43), Size = UDim2.new(1, -94, 0, 24), TextColor3 = UI.Theme.accent})
        UI.label(card, def.description, 12, {Position = UDim2.fromOffset(16, 77), Size = UDim2.new(1, -32, 0, 29), TextColor3 = UI.Theme.muted, TextWrapped = true})
        UI.button(card, p.EquippedSkin == id and "ВЫБРАНО" or p.Cosmetics[id] and "ПРИМЕНИТЬ" or "КУПИТЬ", function() app:Send("ShopRemote", {action = "cosmetic", id = id}) end,
            {Position = UDim2.fromOffset(16, 114), Size = UDim2.new(1, -32, 0, 29), BackgroundColor3 = p.EquippedSkin == id and UI.Theme.raised or UI.Theme.accent, TextSize = 11})
    end
end
function View.trade(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.paragraph(body, "Торговец Макс в Меридиане и квартирмейстер Феликс в НОВОМ РУБЕЖЕ проводят сделки только рядом со своим прилавком. Цены растут при дефиците; склад пополняется каждые 5 минут.", 62)
    UI.heading(body, c.Cash .. " SURVIVAL CASH  /  РЕПУТАЦИЯ " .. c.Reputation.TRADERS)
    for _, product in app.Economy.Shop do
        local count = app.State.shop[product.id] or 0
        local function total(n)
            local sum = 0
            for i = 0, n - 1 do
                local scarcity = 1 + 0.35 * (1 - math.clamp((count - i) / product.stock, 0, 1))
                sum += math.max(1, math.ceil(product.price * scarcity * (1 - math.min(0.15, c.Reputation.TRADERS * 0.0015))))
            end
            return sum
        end
        local card = UI.card(body, 101)
        UI.label(card, app.Items[product.id].name .. "   /   " .. count .. " в наличии", 14, {Position = UDim2.fromOffset(12, 9), Size = UDim2.new(1, -24, 0, 24), Font = Enum.Font.GothamBold})
        UI.label(card, "Продажа: " .. math.floor(product.price * 0.35) .. " Cash/шт.  ·  У вас: " .. app:Count(product.id), 11, {Position = UDim2.fromOffset(12, 35), Size = UDim2.new(1, -24, 0, 20), TextColor3 = UI.Theme.muted})
        local row = UI.row(card, 28); row.Position = UDim2.fromOffset(12, 64); row.Size = UDim2.new(1, -24, 0, 28)
        if count > 0 then UI.button(row, "1 за " .. total(1), function() app:Send("ShopRemote", {action = "buy", id = product.id, count = 1}) end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11, BackgroundColor3 = UI.Theme.accent}) end
        if count >= 5 then UI.button(row, "5 за " .. total(5), function() app:Send("ShopRemote", {action = "buy", id = product.id, count = 5}) end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11}) end
        local item = app.Items[product.id]
        local held = app:Count(product.id)
        if not item.weapon and not item.armor and held > 0 then
            UI.button(row, "Продать " .. math.min(5, held), function() app:Send("ShopRemote", {action = "sell", id = product.id, count = math.min(5, held)}) end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11})
        end
    end
end
function View.weapons(app, body)
    local UI = app.UI
    UI.paragraph(body, "Виктор ремонтирует оружие и всю броню. Экипированная броня физически отображается на персонаже. Улучшение оружия +1…+10 даёт +6% урона за уровень.", 55)
    for _, e in app.State.campaign.Inventory do
        local item = app.Items[e.id]
        if not item.weapon and not item.armor then continue end
        local card = UI.card(body, 140)
        if item.armor then
            local metal = math.max(1, math.ceil((100 - e.durability) / 20))
            UI.label(card, item.name .. "  /  " .. math.floor(item.armor * 100) .. "% защиты", 16, {Position = UDim2.fromOffset(14, 9), Size = UDim2.new(1, -28, 0, 24), Font = Enum.Font.GothamBold})
            UI.label(card, "Прочность " .. math.floor(e.durability) .. "%   /   Ремонт: 25 Cash + " .. metal .. " металла", 11, {Position = UDim2.fromOffset(14, 38), Size = UDim2.new(1, -28, 0, 24), TextColor3 = UI.Theme.muted, TextWrapped = true})
            UI.label(card, "Серверная защита • видимые нагрудник, спина и уникальные детали комплекта", 10, {Position = UDim2.fromOffset(14, 65), Size = UDim2.new(1, -28, 0, 24), TextWrapped = true, TextColor3 = UI.Theme.accent})
            if e.durability < 100 then UI.button(card, "Восстановить броню", function() app:Send("CombatRemote", {action = "repair", uid = e.uid}) end, {Position = UDim2.fromOffset(14, 100), Size = UDim2.new(1, -28, 0, 28), TextSize = 11}) end
        else
            local cost = {metal = 2 + e.upgrade, parts = 1 + math.floor(e.upgrade / 3)}
            if e.upgrade >= 3 then cost.electronics = math.floor(e.upgrade / 3) end
            local cash = 25 * (e.upgrade + 1)
            UI.label(card, item.name .. "  +" .. e.upgrade, 16, {Position = UDim2.fromOffset(14, 9), Size = UDim2.new(1, -28, 0, 24), Font = Enum.Font.GothamBold})
            UI.label(card, "Прочность " .. math.floor(e.durability) .. "%   /   Ремонт: 20 Cash + " .. math.max(1, math.ceil((100 - e.durability) / 25)) .. " металла", 11, {Position = UDim2.fromOffset(14, 38), Size = UDim2.new(1, -28, 0, 24), TextColor3 = UI.Theme.muted, TextWrapped = true})
            UI.label(card, e.upgrade < 10 and ("+1: " .. cash .. " Cash  ·  " .. UI.price(cost, app.Items)) or "МАКСИМАЛЬНОЕ УЛУЧШЕНИЕ", 10, {Position = UDim2.fromOffset(14, 65), Size = UDim2.new(1, -28, 0, 24), TextWrapped = true, TextColor3 = UI.Theme.accent})
            if e.durability < 100 then UI.button(card, "Ремонт", function() app:Send("CombatRemote", {action = "repair", uid = e.uid}) end, {Position = UDim2.fromOffset(14, 100), Size = UDim2.new(0.5, -20, 0, 28), TextSize = 11}) end
            if e.upgrade < 10 then UI.button(card, "Улучшить", function() app:Send("CombatRemote", {action = "upgrade", uid = e.uid}) end, {Position = UDim2.new(0.5, 0, 0, 100), Size = UDim2.new(0.5, -14, 0, 28), TextSize = 11}) end
        end
    end
end
return View
