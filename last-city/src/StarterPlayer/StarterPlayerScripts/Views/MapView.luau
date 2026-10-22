--!nonstrict
local Map = {}
local MIN_X, MAX_X, MIN_Z, MAX_Z = -560, 1220, -730, 450
local WIDTH, DEPTH = MAX_X - MIN_X, MAX_Z - MIN_Z
local function mapX(x) return (x - MIN_X) / WIDTH end
local function mapZ(z) return (MAX_Z - z) / DEPTH end
function Map.project(x, z) return Vector2.new(math.clamp(mapX(x), 0.02, 0.98), math.clamp(mapZ(z), 0.02, 0.98)) end
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
    rect(-300, 150, 18, 240, Color3.fromRGB(60, 70, 70)); rect(-261.5, 38, 59, 18, Color3.fromRGB(60, 70, 70))
    for _, p in {{-62, 221}, {62, 99}, {62, 218}, {-64, 348}, {63, 348}, {-185, 92}, {-185, 220}, {185, 220}, {-180, 351}, {185, 351}} do rect(p[1], p[2], 54, 60, Color3.fromRGB(73, 80, 76)) end
    for _, p in {{-338,80},{-262,80},{-338,150},{-262,150},{-338,220},{-262,220}} do rect(p[1],p[2],30,25,Color3.fromRGB(81,88,77)) end
    for _, p in {{-455,40},{-455,325},{470,340}} do rect(p[1],p[2],34,34,Color3.fromRGB(91,55,48),0.2) end
    rect(-62, 100, 65, 65, Color3.fromRGB(126, 102, 65)); rect(187, 98, 72, 70, Color3.fromRGB(116, 86, 62))
    rect(-62, 5, 83, 39, Color3.fromRGB(88, 104, 79)); rect(62, 5, 45, 28, Color3.fromRGB(81, 91, 79))
    rect(0, -100, 158, 150, Color3.fromRGB(55, 94, 79))
    rect(330, 160, 70, 80, Color3.fromRGB(91, 82, 62)); rect(382, 160, 104, 25, Color3.fromRGB(55, 56, 52)); rect(432, 160, 52, 52, Color3.fromRGB(65, 66, 61))
    -- Streamed second city; there is no walkable road across the quarantine divide.
    rect(950, 80, 490, 700, Color3.fromRGB(48, 59, 53), 0.15)
    rect(950, 220, 430, 28, Color3.fromRGB(60, 70, 70)); rect(920, 80, 28, 570, Color3.fromRGB(60, 70, 70)); rect(950,-215,430,22,Color3.fromRGB(60,70,70))
    rect(920, 95, 286, 160, Color3.fromRGB(69, 88, 70)); rect(1085, 105, 54, 56, Color3.fromRGB(78, 86, 80)); rect(1085, 315, 58, 62, Color3.fromRGB(78, 86, 80)); rect(805, 315, 56, 60, Color3.fromRGB(78, 86, 80))
    for _,x in {760,825,875,970,1035,1110} do rect(x,-180,30,25,Color3.fromRGB(81,88,77)) end
    rect(1140,-76,70,86,Color3.fromRGB(74,58,82),0.1)
    for _,p in {{750,395},{1150,395},{1150,-245}} do rect(p[1],p[2],34,34,Color3.fromRGB(91,55,48),0.2) end
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
                at = ({alex = {-18, -55}, max = {34, -103}, victor = {-48, -121}, ira = {304, 154}, boris = {18, -34}, makar = {888, 72}, nora = {952, 82}, lada = {1018, 132}, quartermaster = {850, 115}})[def.npc]
            elseif q.status == "Active" then
                at = ({first_blood = {-12, 23}, lost_supplies = {-62, 100}, daily_hunt = {0, 160}, medical_run = {-62, 220}, radio_recovery = {62, 220}, relief_recon = {-62, 5}, ash_reaver = {-455, 40}, mine_foreman = {434, 160}, second_city = {0, 160}, rescue_fireteam = {1080, 240}, harbor_aid = {1085, 250}, frontier_patrol = {1120, 245}, frontier_homes = {970, -180}, frontier_rebuild = {1010, 255}, prism_cave = {1140, -76}})[tracked]
                local bossKind = tracked == "ash_reaver" and "reaver" or tracked == "rescue_fireteam" and "warden" or tracked == "prism_cave" and "rainbow"
                local bossSites = bossKind == "reaver" and {{-455, 40}, {-455, 325}, {470, 340}}
                    or bossKind == "warden" and {{750, 395}, {1150, 395}, {1150, -245}} or bossKind == "rainbow" and {{1140, -76}}
                local bossState = bossKind and app.State.bosses and app.State.bosses[bossKind]
                if bossSites and bossState and bossState.lastPoint > 0 then at = bossSites[bossState.lastPoint] or at end
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
        for _, pin in {{-25, -100, "УБЕЖИЩЕ", T.success}, {118, -120, "ВЕРТОЛЁТ", T.accent}, {-62, 18, "ЛАГЕРЬ", T.accent}, {-62, 125, "MERIDIAN", T.accent}, {187, 130, "ПРОМЗОНА", T.accent}, {-62, 245, "АПТЕКА", T.success}, {62, 245, "ПОЛИЦИЯ", T.blue}, {268, 137, "АЗС", T.accent}, {368, 125, "ШАХТА", T.danger}, {0, 405, "КАРАНТИН", T.danger}} do
            UI.label(map, pin[3], 10, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.fromScale(mapX(pin[1]), mapZ(pin[2])), Size = UDim2.fromOffset(110, 20), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = pin[4], Font = Enum.Font.GothamBold})
        end
        local unlock = app.State.campaign.Quests.second_city
        local open = unlock and unlock.status == "RewardClaimed"
        UI.label(map, open and "НОВЫЙ РУБЕЖ  /  ОТКРЫТ" or "НОВЫЙ РУБЕЖ  /  ЗАКРЫТ", 11, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.fromScale(mapX(920), mapZ(185)), Size = UDim2.fromOffset(190, 22), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = open and T.success or T.danger, Font = Enum.Font.GothamBold})
        UI.label(map, "PRISM CAVE", 9, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.fromScale(mapX(1140), mapZ(-76)), Size = UDim2.fromOffset(100, 18), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = T.danger, Font = Enum.Font.GothamBold})
    end
    UI.label(map, "N", 11, {Position = UDim2.fromOffset(9, 5), Size = UDim2.fromOffset(20, 18), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    return player
end
function Map.page(app, body)
    local UI = app.UI
    UI.paragraph(body, "Меридиан, шахта и НОВЫЙ РУБЕЖ • белая стрелка — вы. Второй город открывает квест Алекса на 25 убийств; перемещение возможно только настоящим вертолётным рейсом.", 52)
    local holder = UI.frame(body, {Size = UDim2.new(1, -8, 0, math.min(470, app.Height - 230)), BackgroundTransparency = 1})
    app.MapDot = Map.draw(app, holder, false)
    UI.paragraph(body, "Первый район: убежище, девять участков, жилой западный сектор, карьер и шахта. Второй район: гарнизон, шесть домов, богатые тайники и пещера Радужного Стража.", 48)
end
return Map
