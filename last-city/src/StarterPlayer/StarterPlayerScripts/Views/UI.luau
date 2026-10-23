--!nonstrict
local TweenService = game:GetService("TweenService")
local UI = {}
UI.Theme = {
    background = Color3.fromRGB(16, 19, 23), panel = Color3.fromRGB(27, 34, 43), raised = Color3.fromRGB(37, 46, 56),
    accent = Color3.fromRGB(249, 115, 22), danger = Color3.fromRGB(239, 68, 68), success = Color3.fromRGB(34, 197, 94),
    text = Color3.fromRGB(245, 246, 247), muted = Color3.fromRGB(148, 163, 174), line = Color3.fromRGB(62, 73, 83), blue = Color3.fromRGB(56, 189, 248),
}
function UI.new(class, props, parent)
    local instance = Instance.new(class)
    for key, value in props or {} do instance[key] = value end
    instance.Parent = parent; return instance
end
function UI.corner(parent, radius) return UI.new("UICorner", {CornerRadius = UDim.new(0, radius or 7)}, parent) end
function UI.stroke(parent, color, transparency)
    return UI.new("UIStroke", {Color = color or UI.Theme.line, Transparency = transparency or 0.3, Thickness = 1}, parent)
end
function UI.pad(parent, value)
    UI.new("UIPadding", {PaddingLeft = UDim.new(0, value), PaddingRight = UDim.new(0, value), PaddingTop = UDim.new(0, value), PaddingBottom = UDim.new(0, value)}, parent)
end
function UI.label(parent, text, size, props)
    local values = {BackgroundTransparency = 1, Text = text, TextColor3 = UI.Theme.text, TextSize = size or 15,
        Font = Enum.Font.Gotham, TextXAlignment = Enum.TextXAlignment.Left, TextYAlignment = Enum.TextYAlignment.Center,
        Size = UDim2.new(1, 0, 0, 24), BorderSizePixel = 0}
    for key, value in props or {} do values[key] = value end
    return UI.new("TextLabel", values, parent)
end
function UI.frame(parent, props)
    local values = {BackgroundColor3 = UI.Theme.panel, BorderSizePixel = 0, Size = UDim2.new(1, 0, 0, 80)}
    for key, value in props or {} do values[key] = value end
    return UI.new("Frame", values, parent)
end
function UI.button(parent, text, callback, props)
    local values = {BackgroundColor3 = UI.Theme.raised, TextColor3 = UI.Theme.text, Font = Enum.Font.GothamMedium,
        TextSize = 13, Text = text, Size = UDim2.new(1, 0, 0, 38), BorderSizePixel = 0, AutoButtonColor = false}
    for key, value in props or {} do values[key] = value end
    local button = UI.new("TextButton", values, parent); UI.corner(button, 5)
    local base = values.BackgroundColor3
    button.MouseEnter:Connect(function()
        TweenService:Create(button, TweenInfo.new(0.15), {BackgroundColor3 = base:Lerp(Color3.new(1, 1, 1), 0.12)}):Play()
        if UI.Sound then UI.Sound(0.35) end
    end)
    button.MouseLeave:Connect(function() TweenService:Create(button, TweenInfo.new(0.15), {BackgroundColor3 = base}):Play() end)
    button.Activated:Connect(function() if UI.Sound then UI.Sound(1) end; callback() end)
    return button
end
function UI.list(parent, gap, horizontal)
    return UI.new("UIListLayout", {Padding = UDim.new(0, gap or 8), FillDirection = horizontal and Enum.FillDirection.Horizontal or Enum.FillDirection.Vertical,
        SortOrder = Enum.SortOrder.LayoutOrder, HorizontalAlignment = Enum.HorizontalAlignment.Left}, parent)
end
function UI.card(parent, height)
    local card = UI.frame(parent, {Size = UDim2.new(1, -6, 0, height or 88), BackgroundColor3 = UI.Theme.panel})
    UI.corner(card); UI.stroke(card, nil, 0.65); return card
end
function UI.paragraph(parent, text, height, color)
    return UI.label(parent, text, 14, {Size = UDim2.new(1, -8, 0, height or 54), TextWrapped = true, TextColor3 = color or UI.Theme.muted, TextYAlignment = Enum.TextYAlignment.Top})
end
function UI.heading(parent, text)
    return UI.label(parent, text, 12, {Font = Enum.Font.GothamBold, TextColor3 = UI.Theme.accent, Size = UDim2.new(1, 0, 0, 28)})
end
function UI.row(parent, height)
    local row = UI.frame(parent, {Size = UDim2.new(1, -6, 0, height or 40), BackgroundTransparency = 1}); UI.list(row, 8, true); return row
end
function UI.bar(parent, title, color, y, width)
    local frame = UI.frame(parent, {Position = UDim2.fromOffset(0, y), Size = UDim2.fromOffset(width, 29), BackgroundTransparency = 1})
    local label = UI.label(frame, title, 11, {TextColor3 = UI.Theme.muted, Size = UDim2.new(1, 0, 0, 15)})
    local track = UI.frame(frame, {Position = UDim2.fromOffset(0, 21), Size = UDim2.new(1, 0, 0, 4), BackgroundColor3 = UI.Theme.raised})
    UI.corner(track, 3)
    local fill = UI.frame(track, {Size = UDim2.fromScale(1, 1), BackgroundColor3 = color}); UI.corner(fill, 3)
    return {label = label, fill = fill, set = function(self, value, max)
        self.fill.Size = UDim2.fromScale(math.clamp(value / max, 0, 1), 1)
        self.label.Text = title .. "  " .. math.ceil(value) .. " / " .. max
    end}
end
function UI.clear(parent)
    for _, child in parent:GetChildren() do if not child:IsA("UIListLayout") and not child:IsA("UIPadding") then child:Destroy() end end
end
function UI.price(cost, items)
    local result = {}; for id, count in cost do table.insert(result, (items[id] and items[id].name or id) .. " ×" .. count) end
    table.sort(result); return table.concat(result, "  ·  ")
end
return UI
