--!nonstrict
local Map = {}
function Map.draw(app, parent, compact)
    local UI = app.UI; local T = UI.Theme
    local map = UI.frame(parent, {Name = "DistrictMap", Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.fromRGB(22, 30, 31), ClipsDescendants = true})
    UI.corner(map, 6)
    local function rect(x, z, w, d, color, transparency)
        return UI.frame(map, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale((x + 300) / 600, (450 - z) / 1000), Size = UDim2.fromScale(w / 600, d / 1000), BackgroundColor3 = color, BackgroundTransparency = transparency or 0})
    end
    for x = -300, 300, 60 do rect(x, -50, 1, 1000, Color3.fromRGB(46, 58, 59), 0.65) end
    for z = -550, 450, 100 do rect(0, z, 600, 1, Color3.fromRGB(46, 58, 59), 0.65) end
    for _, x in {-122, 0, 122} do rect(x, 200, 22, 430, Color3.fromRGB(60, 70, 70)) end
    for _, z in {38, 160, 282, 404} do rect(0, z, 464, 26, Color3.fromRGB(60, 70, 70)) end
    for _, p in {{-62, 221}, {62, 99}, {62, 218}, {-64, 348}, {63, 348}, {-185, 92}, {-185, 220}, {185, 220}, {-180, 351}, {185, 351}} do rect(p[1], p[2], 54, 60, Color3.fromRGB(73, 80, 76)) end
    rect(-62, 100, 65, 65, Color3.fromRGB(126, 102, 65)); rect(187, 98, 72, 70, Color3.fromRGB(116, 86, 62))
    rect(0, -100, 158, 150, Color3.fromRGB(55, 94, 79))
    for _, x in {-72, 72} do rect(x, -329, 16, 290, Color3.fromRGB(60, 70, 70)) end
    for _, z in {-185, -324, -466} do rect(0, z, 464, 16, Color3.fromRGB(60, 70, 70)) end
    for index, p in app.Config.Plots do
        local own = app.State and app.State.plot == index
        local plot = rect(p.x, p.z, 96, 96, own and T.accent or Color3.fromRGB(70, 78, 66), own and 0.25 or 0.1)
        if not compact then UI.label(plot, tostring(index), 14, {Size = UDim2.fromScale(1, 1), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = own and T.text or T.muted}) end
    end
    if app.State then
        local tracked = app.TrackedQuest
        if not tracked then
            for _, id in app:Sorted(app.Quests) do
                local q = app.State.campaign.Quests[id]
                if q and (q.status == "Active" or q.status == "Completed") then tracked = id; break end
            end
        end
        if tracked and app.Quests[tracked] then
            local def = app.Quests[tracked]; local q = app.State.campaign.Quests[tracked]
            local at
            if not q or q.status == "Completed" then
                at = ({alex = {-18, -55}, max = {34, -103}, victor = {-45, -128}})[def.npc]
            elseif q.status == "Active" then
                at = ({first_blood = {-12, 23}, lost_supplies = {-62, 100}, daily_hunt = {0, 160}, medical_run = {-62, 220}, radio_recovery = {62, 220}})[tracked]
                if tracked == "radio_recovery" and q.seen["intel:PoliceDispatch"] then at = {274, 121} end
                if tracked == "city_power" then
                    if q.progress[1] < 20 or q.progress[2] < 10 or q.progress[3] < 5 then at = {187, 98}
                    else local plot = app.Config.Plots[app.State.plot]; at = {plot.x, plot.z} end
                end
            end
            if at then UI.label(map, "◆", compact and 12 or 21, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale((at[1] + 300) / 600, (450 - at[2]) / 1000), Size = UDim2.fromOffset(24, 24), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = T.accent, ZIndex = 3}) end
        end
    end
    local player = UI.label(map, "▲", compact and 13 or 19, {Name = "PlayerDot", AnchorPoint = Vector2.new(0.5, 0.5), Size = UDim2.fromOffset(24, 24), TextColor3 = T.text, TextXAlignment = Enum.TextXAlignment.Center, ZIndex = 4})
    if not compact then
        for _, pin in {{-25, -100, "УБЕЖИЩЕ", T.success}, {-62, 125, "MERIDIAN", T.accent}, {187, 130, "ПРОМЗОНА", T.accent}, {-62, 245, "АПТЕКА", T.success}, {62, 245, "ПОЛИЦИЯ", T.blue}, {268, 137, "АЗС", T.accent}, {0, 405, "КАРАНТИН", T.danger}} do
            UI.label(map, pin[3], 10, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.fromScale((pin[1] + 300) / 600, (450 - pin[2]) / 1000), Size = UDim2.fromOffset(130, 20), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = pin[4], Font = Enum.Font.GothamBold})
        end
    end
    UI.label(map, "N", 11, {Position = UDim2.fromOffset(9, 5), Size = UDim2.fromOffset(20, 18), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    return player
end
function Map.page(app, body)
    local UI = app.UI
    UI.paragraph(body, "Меридиан • один исследуемый район. Белая стрелка — вы. Оранжевый участок — ваша база. Телепортации по карте нет.", 44)
    local holder = UI.frame(body, {Size = UDim2.new(1, -8, 0, math.min(470, app.Height - 230)), BackgroundTransparency = 1})
    app.MapDot = Map.draw(app, holder, false)
    UI.paragraph(body, "Север: заражённый город, магазин, аптека, полиция и АЗС. Юг: убежище и жилые участки. Дороги соединяют все шесть баз.", 42)
end
return Map
