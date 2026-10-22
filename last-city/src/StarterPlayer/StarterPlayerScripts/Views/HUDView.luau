--!nonstrict
return function(app)
    local UI = app.UI; local T = UI.Theme
    local hud = UI.frame(app.Root, {Name = "HUD", Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1, Visible = false})
    local top = UI.frame(hud, {Position = UDim2.fromOffset(18, 16), Size = UDim2.new(1, -36, 0, 50), BackgroundTransparency = 0.12}); UI.corner(top)
    UI.label(top, "LC / 07", 17, {Position = UDim2.fromOffset(15, 0), Size = UDim2.fromOffset(95, 50), Font = Enum.Font.GothamBlack, TextColor3 = T.accent})
    app.TopResources = UI.label(top, "", 13, {Position = UDim2.fromOffset(116, 0), Size = UDim2.new(1, -240, 1, 0), Font = Enum.Font.GothamMedium})
    app.Clock = UI.label(top, "", 12, {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -12, 0, 0), Size = UDim2.fromOffset(130, 50), TextXAlignment = Enum.TextXAlignment.Right, TextColor3 = T.muted})
    local tracker = UI.frame(hud, {Position = UDim2.fromOffset(18, 84), Size = UDim2.fromOffset(266, 158), BackgroundTransparency = 0.12}); UI.corner(tracker)
    UI.frame(tracker, {Size = UDim2.fromOffset(3, 158), BackgroundColor3 = T.accent})
    UI.label(tracker, "ЗАДАНИЕ  /  J", 10, {Position = UDim2.fromOffset(16, 12), Size = UDim2.fromOffset(230, 18), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    app.TrackerTitle = UI.label(tracker, "ПЕРВЫЙ ДЕНЬ", 14, {Position = UDim2.fromOffset(16, 38), Size = UDim2.fromOffset(230, 22), Font = Enum.Font.GothamBold})
    app.TrackerText = UI.label(tracker, "Найдите капитана Алекса у северных ворот.", 12, {Position = UDim2.fromOffset(16, 68), Size = UDim2.fromOffset(234, 78), TextWrapped = true, TextYAlignment = Enum.TextYAlignment.Top, TextColor3 = T.muted})
    local tutorial = UI.frame(hud, {Position = UDim2.fromOffset(18, 252), Size = UDim2.fromOffset(266, 90), BackgroundTransparency = 0.1, Visible = false}); UI.corner(tutorial)
    app.TutorialCard = tutorial
    UI.button(tutorial, "↗", function() app:OpenPanel("tutorial") end, {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -5, 0, 5), Size = UDim2.fromOffset(24, 22), TextSize = 12})
    app.TutorialLabel = UI.label(tutorial, "", 12, {Position = UDim2.fromOffset(14, 10), Size = UDim2.new(1, -48, 1, -20), TextColor3 = T.text, TextWrapped = true, TextYAlignment = Enum.TextYAlignment.Top})
    local map = UI.frame(hud, {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -18, 0, 84), Size = UDim2.fromOffset(178, 210), BackgroundTransparency = 0.1}); UI.corner(map)
    UI.label(map, "MERIDIAN  /  M", 10, {Position = UDim2.fromOffset(12, 8), Size = UDim2.new(1, -24, 0, 20), TextColor3 = T.muted})
    local mapHolder = UI.frame(map, {Position = UDim2.fromOffset(10, 33), Size = UDim2.new(1, -20, 1, -43), BackgroundTransparency = 1})
    app.MiniMapHolder = mapHolder
    app.MiniDot = app.Map.draw(app, mapHolder, true)
    local bars = UI.frame(hud, {Position = UDim2.new(0, 20, 1, -151), Size = UDim2.fromOffset(240, 108), BackgroundTransparency = 1})
    app.HealthBar = UI.bar(bars, "ЗДОРОВЬЕ", T.success, 0, 235)
    app.StaminaBar = UI.bar(bars, "ВЫНОСЛИВОСТЬ", T.accent, 34, 235)
    app.SurvivalLabel = UI.label(bars, "", 11, {Position = UDim2.fromOffset(0, 76), Size = UDim2.fromOffset(260, 24), TextColor3 = T.muted})
    local equipment = UI.frame(hud, {AnchorPoint = Vector2.new(1, 1), Position = UDim2.new(1, -18, 1, -51), Size = UDim2.fromOffset(250, 94), BackgroundTransparency = 0.1}); UI.corner(equipment)
    app.EquipmentTitle = UI.label(equipment, "ЛОМ", 16, {Position = UDim2.fromOffset(14, 10), Size = UDim2.fromOffset(224, 23), Font = Enum.Font.GothamBold})
    app.AmmoLabel = UI.label(equipment, "", 12, {Position = UDim2.fromOffset(14, 37), Size = UDim2.fromOffset(224, 20), TextColor3 = T.muted})
    for index, name in {"1  Лом", "2  Пист.", "3  Лечить"} do
        UI.button(equipment, name, function() app:QuickSlot(index) end, {Position = UDim2.new((index - 1) / 3, 8, 0, 63), Size = UDim2.new(1 / 3, -12, 0, 24), TextSize = 10})
    end
    local hotbar = UI.frame(hud, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.new(0.5, 0, 1, -15), Size = UDim2.fromOffset(526, 29), BackgroundTransparency = 1}); UI.list(hotbar, 6, true)
    for _, entry in {{"K  Рюкзак", "inventory"}, {"J  Квесты", "quests"}, {"B  Стройка", "build"}, {"G  База", "base"}, {"M  Карта", "map"}, {"⚙", "settings"}} do
        UI.button(hotbar, entry[1], function()
            app:OpenPanel(entry[2]); if entry[2] == "inventory" then app:Send("SessionRemote", {action = "inventoryOpened"}) end
        end, {Size = UDim2.fromOffset(entry[2] == "settings" and 40 or 90, 29), TextSize = 10})
    end
    app.SaveLabel = UI.label(hud, "", 10, {Position = UDim2.new(0, 20, 1, -28), Size = UDim2.fromOffset(245, 20), TextColor3 = T.muted})
    app.ScoutLabel = UI.label(hud, "", 10, {AnchorPoint = Vector2.new(0.5, 0), Position = UDim2.new(0.5, 0, 0, 71), Size = UDim2.fromOffset(310, 22), TextXAlignment = Enum.TextXAlignment.Center, TextColor3 = T.danger, Font = Enum.Font.GothamBold, Visible = false})
    app.Crosshair = UI.label(hud, "+", 22, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale(0.5, 0.5), Size = UDim2.fromOffset(28, 28), TextXAlignment = Enum.TextXAlignment.Center, TextTransparency = 0.25})
    app.HitMarker = UI.label(hud, "×", 34, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale(0.5, 0.5), Size = UDim2.fromOffset(40, 40), TextColor3 = T.accent, TextXAlignment = Enum.TextXAlignment.Center, Visible = false})
    app.BuildBar = UI.frame(hud, {AnchorPoint = Vector2.new(0.5, 1), Position = UDim2.new(0.5, 0, 1, -172), Size = UDim2.fromOffset(550, 80), BackgroundTransparency = 0.07, Visible = false}); UI.corner(app.BuildBar)
    app.BuildHint = UI.label(app.BuildBar, "", 11, {Position = UDim2.fromOffset(12, 7), Size = UDim2.new(1, -24, 0, 32), TextWrapped = true})
    for i, pair in {{"Разместить", function() app.Build:Place() end}, {"Повернуть", function() app.Build:Rotate() end}, {"Отмена", function() app.Build:Cancel() end}} do
        UI.button(app.BuildBar, pair[1], pair[2], {Position = UDim2.new((i - 1) / 3, 8, 0, 43), Size = UDim2.new(1 / 3, -16, 0, 28), TextSize = 11})
    end
    function app:LayoutHUD()
        local compact = self.Width < 900
        tracker.Size = UDim2.fromOffset(compact and 220 or 266, 158)
        self.TrackerTitle.Size = UDim2.fromOffset(compact and 190 or 230, 22)
        self.TrackerText.Size = UDim2.fromOffset(compact and 190 or 234, 78)
        map.Visible = not compact
        top.Position = UDim2.fromOffset(12, 12); top.Size = UDim2.new(1, -24, 0, 50)
        self.TopResources.TextSize = compact and 11 or 13
        hotbar.Size = UDim2.fromOffset(compact and 452 or 526, 29)
        local scale = hotbar:FindFirstChildOfClass("UIScale") or UI.new("UIScale", {}, hotbar)
        scale.Scale = compact and 0.83 or 1
        if game:GetService("UserInputService").TouchEnabled then
            hotbar.AnchorPoint = Vector2.new(0.5, 0); hotbar.Position = UDim2.new(0.5, 0, 0, 69)
            tracker.Position = UDim2.fromOffset(18, 111); tutorial.Position = UDim2.fromOffset(18, 278)
            bars.Position = UDim2.new(0, 20, 1, -190)
            equipment.Position = UDim2.new(1, -18, 1, -190)
        end
        equipment.Size = UDim2.fromOffset(compact and 210 or 250, 94)
        if compact then
            bars.Size = UDim2.fromOffset(190, 108)
            self.HealthBar.fill.Parent.Size = UDim2.fromOffset(175, 4); self.StaminaBar.fill.Parent.Size = UDim2.fromOffset(175, 4)
            self.SurvivalLabel.TextSize = 10
        end
        self.BuildBar.Size = UDim2.fromOffset(math.min(550, self.Width - 30), 80)
    end
    return hud
end
