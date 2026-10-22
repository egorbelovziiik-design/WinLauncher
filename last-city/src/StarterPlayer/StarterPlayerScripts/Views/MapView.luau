--!nonstrict
local Map = {}
local MIN_X, MAX_X, MIN_Z, MAX_Z = -300, 480, -730, 450
local WIDTH, DEPTH = MAX_X - MIN_X, MAX_Z - MIN_Z
local function mapX(x) return (x - MIN_X) / WIDTH end
local function mapZ(z) return (MAX_Z - z) / DEPTH end
function Map.draw(app, parent, compact)
    local UI = app.UI; local T = UI.Theme
    local map = UI.frame(parent, {Name = "DistrictMap", Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.fromRGB(22, 30, 31), ClipsDescendants = true})
    UI.corner(map, 6)
    local function rect(x, z, w, d, tint, transparency)
        return UI.frame(map, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale(mapX(x), mapZ(z)), Size = UDim2.fromScale(w / WIDTH, d / DEPTH), BackgroundColor3 = tint, BackgroundTransparency = transparency or 0})
    end
    for x = MIN_X, MAX_X, 60 do rect(x, (MIN_Z + MAX_Z) / 2, 1, DEPTH, Color3.fromRGB(46, 58, 59), 0.65) end
    for z = MIN_Z, MAX_Z, 100 do rect((MIN_X + MAX_X) / 2, z, WIDTH, 1, Color3.fromRGB(46, 58, 59), 0.65) end
    for _, x in {-122, 0, 122} do rect(x, 200, 22, 430, Color3.fromRGB(60, 70, 70)) end
    for _, z in {38, 160, 282, 404} do rect(0, z, 464, 26, Color3.fromRGB(60, 70, 70)) end
    rect(282.5, 160, 101, 26, Color3.fromRGB(60, 70, 70))
    for _, p in {{-62, 221}, {62, 99}, {62, 218}, {-64, 348}, {63, 348}, {-185, 92}, {-185, 220}, {185, 220}, {-180, 351}, {185, 351}} do rect(p[1], p[2], 54, 60, Color3.fromRGB(73, 80, 76)) end
    rect(-62, 100, 65, 65, Color3.fromRGB(126, 102, 65)); rect(187, 98, 72, 70, Color3.fromRGB(116, 86, 62))
    rect(-62, 5, 83, 39, Color3.fromRGB(88, 104, 79)); rect(62, 5, 45, 28, Color3.fromRGB(81, 91, 79))
    rect(0, -100, 158, 150, Color3.fromRGB(55, 94, 79))
    rect(330, 160, 70, 80, Color3.fromRGB(91, 82, 62)); rect(382, 160, 104, 25, Color3.fromRGB(55, 56, 52)); rect(432, 160, 52, 52, Color3.fromRGB(65, 66, 61))
    for _, x in {-72, 72} do rect(x, -410, 16, 450, Color3.fromRGB(60, 70, 70)) end
    for _, z in {-185, -324, -466, -608} do rect(0, z, 464, 16, Color3.fromRGB(60, 70, 70)) end
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
                at = ({alex = {-18, -55}, max = {34, -103}, victor = {-48, -121}, ira = {304, 154}})[def.npc]
            elseif q.status == "Active" then
                at = ({first_blood = {-12, 23}, lost_supplies = {-62, 100}, daily_hunt = {0, 160}, medical_run = {-62, 220}, radio_recovery = {62, 220}, relief_recon = {-62, 5}, mine_foreman = {434, 160}})[tracked]
                if tracked == "radio_recovery" and q.seen["intel:PoliceDispatch"] then at = {274, 121} end
                if tracked == "city_power" then
                    if q.progress[1] < 20 or q.progress[2] < 10 or q.progress[3] < 5 then at = {187, 98}
                    else local plot = app.Config.Plots[app.State.plot]; at = {plot.x, plot.z} end
                end
            end
            if at then UI.label(map, "◆", compact and 12 or 21, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale(mapX(at[1]), mapZ(at[2])), Size = UDim2.fromOffset(24, 24), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = T.accent, ZIndex = 3}) end
        end
    end
    local player = UI.label(map, "▲", compact and 13 or 19, {Name = "PlayerDot", AnchorPoint = Vector2.new(0.5, 0.5), Size = UDim2.fromOffset(24, 24), TextColor3 = T.text, TextXAlignment = Enum.TextXAlignment.Center, ZIndex = 4})
    if not compact then
        for _, pin in {{-25, -100, "УБЕЖИЩЕ", T.success}, {-62, 18, "ЛАГЕРЬ ЭВАКУАЦИИ", T.amber or T.accent}, {-62, 125, "MERIDIAN", T.accent}, {187, 130, "ПРОМЗОНА", T.accent}, {-62, 245, "АПТЕКА", T.success}, {62, 245, "ПОЛИЦИЯ", T.blue}, {268, 137, "АЗС", T.accent}, {368, 125, "ШАХТА", T.danger}, {0, 405, "КАРАНТИН", T.danger}} do
            UI.label(map, pin[3], 10, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.fromScale(mapX(pin[1]), mapZ(pin[2])), Size = UDim2.fromOffset(130, 20), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = pin[4], Font = Enum.Font.GothamBold})
        end
    end
    UI.label(map, "N", 11, {Position = UDim2.fromOffset(9, 5), Size = UDim2.fromOffset(20, 18), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    return player
end
function Map.page(app, body)
    local UI = app.UI
    UI.paragraph(body, "Меридиан и восточный карьер • белая стрелка — вы. Оранжевый участок — ваша база. Телепортации по карте нет.", 44)
    local holder = UI.frame(body, {Size = UDim2.new(1, -8, 0, math.min(470, app.Height - 230)), BackgroundTransparency = 1})
    app.MapDot = Map.draw(app, holder, false)
    UI.paragraph(body, "Север: заражённый город. Восток: АЗС, карьер и проходимая шахта с боссом. Юг: убежище и девять жилых участков.", 42)
end
return Map
