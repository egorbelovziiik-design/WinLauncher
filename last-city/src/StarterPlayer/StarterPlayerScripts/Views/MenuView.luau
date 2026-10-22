--!nonstrict
return function(app)
    local UI = app.UI; local T = UI.Theme
    local root = UI.new("CanvasGroup", {Name = "MainMenu", Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1}, app.Root)
    local wash = UI.frame(root, {Size = UDim2.fromScale(1, 1), BackgroundColor3 = T.background, BackgroundTransparency = 0.04})
    UI.new("UIGradient", {Color = ColorSequence.new(Color3.new(1, 1, 1)), Transparency = NumberSequence.new({NumberSequenceKeypoint.new(0, 0), NumberSequenceKeypoint.new(0.45, 0.22), NumberSequenceKeypoint.new(1, 0.94)})}, wash)
    local content = UI.frame(root, {Name = "MenuContent", Position = UDim2.fromOffset(52, 28), Size = UDim2.new(0, 350, 1, -55), BackgroundTransparency = 1})
    UI.label(content, "M / 07     EMERGENCY NETWORK", 11, {TextColor3 = T.muted, Font = Enum.Font.GothamMedium})
    UI.frame(content, {Position = UDim2.fromOffset(0, 51), Size = UDim2.fromOffset(36, 3), BackgroundColor3 = T.accent})
    UI.label(content, "LAST\nCITY 3D", 78, {Position = UDim2.fromOffset(-4, 70), Size = UDim2.fromOffset(355, 162), Font = Enum.Font.GothamBlack, TextYAlignment = Enum.TextYAlignment.Top, LineHeight = 1})
    UI.label(content, "D E A D   W O R L D", 16, {Position = UDim2.fromOffset(1, 239), Size = UDim2.fromOffset(350, 28), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    UI.label(content, "Мир закончился. Ваша история — нет.", 13, {Position = UDim2.fromOffset(0, 276), Size = UDim2.fromOffset(350, 25), TextColor3 = T.muted})
    local buttons = UI.frame(content, {Position = UDim2.fromOffset(0, 322), Size = UDim2.fromOffset(338, 300), BackgroundTransparency = 1}); UI.list(buttons, 8)
    app.PlayButton = UI.button(buttons, "ИГРАТЬ                                      →", function() app:Send("SessionRemote", {action = "play"}) end,
        {Size = UDim2.new(1, 0, 0, 51), BackgroundColor3 = T.accent, Font = Enum.Font.GothamBold, TextSize = 16})
    local modes = UI.row(buttons, 34)
    app.SoloNote = UI.label(modes, "SOLO: ПОСЛЕ ПУБЛИКАЦИИ", 10, {Size = UDim2.new(0.5, -4, 1, 0), TextColor3 = T.muted, TextWrapped = true, Visible = false})
    app.SoloButton = UI.button(modes, "ОДИНОЧНАЯ", function() app:Send("SessionRemote", {action = "solo"}) end, {Size = UDim2.new(0.5, -4, 1, 0), TextSize = 11})
    UI.button(modes, "КООПЕРАТИВ", function() app:Send("SessionRemote", {action = "play"}) end, {Size = UDim2.new(0.5, -4, 1, 0), TextSize = 11})
    for _, entries in {{{"Обучение", "tutorial"}, {"Магазин", "cosmetics"}}, {{"Профиль", "profile"}, {"Достижения", "achievements"}}, {{"Настройки", "settings"}, {"Обновления", "updates"}}} do
        local row = UI.row(buttons, 33)
        for _, entry in entries do
            UI.button(row, entry[1], function() app:OpenPanel(entry[2]) end, {Size = UDim2.new(0.5, -4, 1, 0), TextSize = 12, BackgroundColor3 = Color3.fromRGB(26, 31, 36)})
        end
    end
    local footer = UI.label(root, "BLACK VEIN  /  0.4.0     •     ВЕРТИКАЛЬНЫЙ СРЕЗ", 10, {Position = UDim2.new(0, 52, 1, -28), Size = UDim2.new(1, -70, 0, 20), TextColor3 = T.muted})
    local dispatch = UI.frame(root, {AnchorPoint = Vector2.new(1, 1), Position = UDim2.new(1, -42, 1, -65), Size = UDim2.fromOffset(288, 148), BackgroundTransparency = 0.18})
    UI.corner(dispatch, 8); UI.stroke(dispatch, T.line, 0.25)
    UI.label(dispatch, "●  СИГНАЛ ОБНАРУЖЕН", 10, {Position = UDim2.fromOffset(20, 17), Size = UDim2.new(1, -40, 0, 20), TextColor3 = T.accent, Font = Enum.Font.GothamBold})
    UI.label(dispatch, "Убежище «Меридиан»", 18, {Position = UDim2.fromOffset(20, 46), Size = UDim2.new(1, -40, 0, 26), Font = Enum.Font.GothamBold})
    UI.label(dispatch, "Найдите Алекса у северных ворот.\nВерните городу первый свет.", 13, {Position = UDim2.fromOffset(20, 83), Size = UDim2.new(1, -40, 0, 44), TextColor3 = T.muted, TextWrapped = true})
    function app:LayoutMenu()
        content.Position = UDim2.fromOffset(self.Width < 750 and 25 or 52, 28)
        dispatch.Visible = self.Width > 850; footer.Position = UDim2.new(0, self.Width < 750 and 25 or 52, 1, -28)
        -- Landscape phones use a compact menu scale; portrait keeps readable controls.
        local scale = content:FindFirstChildOfClass("UIScale") or UI.new("UIScale", {}, content)
        scale.Scale = math.min(1, (self.Height - 65) / 620)
    end
    return root
end
