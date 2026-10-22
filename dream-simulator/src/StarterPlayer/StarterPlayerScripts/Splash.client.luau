--!nocheck
--[[
	Splash.client.luau
	Заставка запуска: закрывает «сырые» первые секунды (загрузка карты,
	клонирование интерфейса) и красиво уводит в лобби.

	Скрипт полностью автономный — не требует ни одного модуля игры,
	поэтому работает даже если остальной клиент упал.
]]

local Players = game:GetService("Players")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local player = Players.LocalPlayer
local playerGui = player:WaitForChild("PlayerGui")

local gui = Instance.new("ScreenGui")
gui.Name = "SplashGUI"
gui.ResetOnSpawn = false
gui.IgnoreGuiInset = true
gui.DisplayOrder = 200
gui.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
gui.Parent = playerGui

local backdrop = Instance.new("Frame")
backdrop.Name = "Backdrop"
backdrop.Size = UDim2.fromScale(1, 1)
backdrop.BackgroundColor3 = Color3.fromRGB(5, 6, 14)
backdrop.BorderSizePixel = 0
backdrop.Parent = gui

local glow = Instance.new("Frame")
glow.Name = "Glow"
glow.AnchorPoint = Vector2.new(0.5, 0.5)
glow.Position = UDim2.fromScale(0.5, 0.45)
glow.Size = UDim2.fromOffset(900, 900)
glow.BackgroundColor3 = Color3.fromRGB(54, 38, 120)
glow.BackgroundTransparency = 0.72
glow.BorderSizePixel = 0
glow.Parent = backdrop

local glowCorner = Instance.new("UICorner")
glowCorner.CornerRadius = UDim.new(1, 0)
glowCorner.Parent = glow

local glowGradient = Instance.new("UIGradient")
glowGradient.Transparency = NumberSequence.new({
	NumberSequenceKeypoint.new(0, 0.15),
	NumberSequenceKeypoint.new(1, 1),
})
glowGradient.Parent = glow

local moon = Instance.new("TextLabel")
moon.Name = "Moon"
moon.AnchorPoint = Vector2.new(0.5, 0.5)
moon.Position = UDim2.fromScale(0.5, 0.32)
moon.Size = UDim2.fromOffset(160, 160)
moon.BackgroundTransparency = 1
moon.Font = Enum.Font.GothamBold
moon.Text = "🌙"
moon.TextSize = 120
moon.TextTransparency = 1
moon.Parent = backdrop

local title = Instance.new("TextLabel")
title.Name = "Title"
title.AnchorPoint = Vector2.new(0.5, 0.5)
title.Position = UDim2.fromScale(0.5, 0.5)
title.Size = UDim2.new(0.9, 0, 0, 64)
title.BackgroundTransparency = 1
title.Font = Enum.Font.GothamBold
title.Text = "DREAM SIMULATOR"
title.TextColor3 = Color3.fromRGB(240, 238, 255)
title.TextScaled = true
title.TextTransparency = 1
title.Parent = backdrop

local titleConstraint = Instance.new("UITextSizeConstraint")
titleConstraint.MaxTextSize = 46
titleConstraint.Parent = title

local subtitle = Instance.new("TextLabel")
subtitle.Name = "Subtitle"
subtitle.AnchorPoint = Vector2.new(0.5, 0.5)
subtitle.Position = UDim2.fromScale(0.5, 0.58)
subtitle.Size = UDim2.new(0.9, 0, 0, 26)
subtitle.BackgroundTransparency = 1
subtitle.Font = Enum.Font.Gotham
subtitle.Text = "Каждый сон — новая история..."
subtitle.TextColor3 = Color3.fromRGB(170, 168, 210)
subtitle.TextScaled = true
subtitle.TextTransparency = 1
subtitle.Parent = backdrop

local subtitleConstraint = Instance.new("UITextSizeConstraint")
subtitleConstraint.MaxTextSize = 18
subtitleConstraint.Parent = subtitle

local track = Instance.new("Frame")
track.Name = "Track"
track.AnchorPoint = Vector2.new(0.5, 0.5)
track.Position = UDim2.fromScale(0.5, 0.68)
track.Size = UDim2.fromOffset(320, 6)
track.BackgroundColor3 = Color3.fromRGB(40, 40, 70)
track.BorderSizePixel = 0
track.BackgroundTransparency = 0.3
track.Parent = backdrop

local trackCorner = Instance.new("UICorner")
trackCorner.CornerRadius = UDim.new(1, 0)
trackCorner.Parent = track

local fill = Instance.new("Frame")
fill.Name = "Fill"
fill.Size = UDim2.fromScale(0, 1)
fill.BackgroundColor3 = Color3.fromRGB(166, 140, 255)
fill.BorderSizePixel = 0
fill.Parent = track

local fillCorner = Instance.new("UICorner")
fillCorner.CornerRadius = UDim.new(1, 0)
fillCorner.Parent = fill

local status = Instance.new("TextLabel")
status.Name = "Status"
status.AnchorPoint = Vector2.new(0.5, 0.5)
status.Position = UDim2.fromScale(0.5, 0.74)
status.Size = UDim2.new(0.9, 0, 0, 20)
status.BackgroundTransparency = 1
status.Font = Enum.Font.Gotham
status.Text = "собираем мир снов..."
status.TextColor3 = Color3.fromRGB(130, 128, 170)
status.TextSize = 14
status.TextTransparency = 1
status.Parent = backdrop

-- Появление
TweenService:Create(moon, TweenInfo.new(0.9), { TextTransparency = 0 }):Play()
TweenService:Create(title, TweenInfo.new(1.0, Enum.EasingStyle.Quint), { TextTransparency = 0 }):Play()
TweenService:Create(subtitle, TweenInfo.new(1.2), { TextTransparency = 0.15 }):Play()
TweenService:Create(status, TweenInfo.new(1.4), { TextTransparency = 0.2 }):Play()
TweenService:Create(fill, TweenInfo.new(2.0, Enum.EasingStyle.Quad), { Size = UDim2.fromScale(1, 1) }):Play()

local phrases = {
	"собираем мир снов...",
	"зажигаем фонарик...",
	"будим монстров...",
	"готово",
}
task.spawn(function()
	for index, text in ipairs(phrases) do
		task.wait(0.55)
		status.Text = text
	end
end)

-- Уход в лобби (ровно один раз)
local faded = false
local function fadeOut()
	if faded or not gui.Parent then
		return
	end
	faded = true
	local info = TweenInfo.new(0.75, Enum.EasingStyle.Quad)
	TweenService:Create(backdrop, info, { BackgroundTransparency = 1 }):Play()
	TweenService:Create(title, info, { TextTransparency = 1 }):Play()
	TweenService:Create(subtitle, info, { TextTransparency = 1 }):Play()
	TweenService:Create(moon, info, { TextTransparency = 1 }):Play()
	TweenService:Create(status, info, { TextTransparency = 1 }):Play()
	TweenService:Create(track, info, { BackgroundTransparency = 1 }):Play()
	TweenService:Create(fill, info, { BackgroundTransparency = 1 }):Play()
	TweenService:Create(glow, info, { BackgroundTransparency = 1 }):Play()
	task.wait(0.8)
	gui:Destroy()
end

-- Не держим игрока дольше 2.6 секунды, даже если сервер тормозит.
task.delay(2.2, fadeOut)

-- Если сервер сообщил, что мир готов раньше — уходим сразу после минимума.
task.spawn(function()
	local startedAt = os.clock()
	while os.clock() - startedAt < 1.4 do
		task.wait(0.15)
	end
	while os.clock() - startedAt < 4 do
		if workspace:GetAttribute("GameReady") == true then
			fadeOut()
			return
		end
		task.wait(0.2)
	end
end)

-- Страховка от «вечной заставки»
task.delay(6, function()
	if gui.Parent then
		gui:Destroy()
	end
end)
