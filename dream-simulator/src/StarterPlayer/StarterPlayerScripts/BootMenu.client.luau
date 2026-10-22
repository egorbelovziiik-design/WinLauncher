--!nocheck
--[[
	BootMenu.client.luau
	Страховочный интерфейс лобби: кнопка «МЕНЮ» сверху экрана.
	Не зависит ни от одного модуля игры — работает, даже если
	основной клиентский интерфейс по какой-то причине не загрузился.
	Когда полноценное меню готово (TopBar.MenuReady == true), эта панель
	сама прячется, чтобы не дублировать кнопки.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")

local player = Players.LocalPlayer
local playerGui = player:WaitForChild("PlayerGui")

------------------------------------------------------------------
-- Скрываем стандартный интерфейс Roblox (с повторами: CoreGui грузится не сразу)
------------------------------------------------------------------
local function hideCoreGui()
	for _, guiType in ipairs({
		Enum.CoreGuiType.Backpack,
		Enum.CoreGuiType.PlayerList,
		Enum.CoreGuiType.EmotesMenu,
		Enum.CoreGuiType.Health,
		Enum.CoreGuiType.Chat,
	}) do
		pcall(function()
			StarterGui:SetCoreGuiEnabled(guiType, false)
		end)
	end
	pcall(function()
		StarterGui:SetCore("TopbarEnabled", false)
	end)
end
hideCoreGui()
task.spawn(function()
	for _ = 1, 20 do
		task.wait(0.5)
		hideCoreGui()
	end
end)

------------------------------------------------------------------
-- Резервная панель
------------------------------------------------------------------
local DARK = Color3.fromRGB(10, 11, 22)
local PANEL = Color3.fromRGB(22, 24, 44)
local ACCENT = Color3.fromRGB(166, 140, 255)
local TEXT = Color3.fromRGB(238, 238, 255)
local DIM = Color3.fromRGB(190, 190, 220)

local function corner(parent, radius)
	local c = Instance.new("UICorner")
	c.CornerRadius = UDim.new(0, radius)
	c.Parent = parent
	return c
end

local function stroke(parent, color, transparency)
	local s = Instance.new("UIStroke")
	s.Color = color or ACCENT
	s.Transparency = transparency or 0.4
	s.Thickness = 1.5
	s.Parent = parent
	return s
end

local function makeButton(parent, name, text, position, size, callback)
	local button = Instance.new("TextButton")
	button.Name = name
	button.Text = text
	button.Position = position
	button.Size = size
	button.BackgroundColor3 = Color3.fromRGB(52, 40, 118)
	button.TextColor3 = TEXT
	button.Font = Enum.Font.GothamBold
	button.TextScaled = true
	button.AutoButtonColor = true
	button.ZIndex = 62
	button.Parent = parent
	corner(button, 12)
	stroke(button, ACCENT, 0.35)
	local constraint = Instance.new("UITextSizeConstraint")
	constraint.MaxTextSize = 20
	constraint.Parent = button
	if callback then
		button.MouseButton1Click:Connect(callback)
	end
	return button
end

local boot = playerGui:FindFirstChild("BootUI")
if not boot then
	boot = Instance.new("ScreenGui")
	boot.Name = "BootUI"
	boot.ResetOnSpawn = false
	boot.IgnoreGuiInset = false
	boot.DisplayOrder = 95
	boot.ZIndexBehavior = Enum.ZIndexBehavior.Sibling
	boot.Parent = playerGui
end

local menuButton = boot:FindFirstChild("MenuButton")
if not menuButton then
	menuButton = makeButton(boot, "MenuButton", "☰  МЕНЮ", UDim2.fromOffset(12, 48), UDim2.fromOffset(150, 40), nil)
	menuButton.BackgroundColor3 = Color3.fromRGB(44, 34, 104)
end

local back = boot:FindFirstChild("BootBackdrop")
if not back then
	back = Instance.new("Frame")
	back.Name = "BootBackdrop"
	back.Size = UDim2.fromScale(1, 1)
	back.BackgroundColor3 = DARK
	back.BackgroundTransparency = 0.45
	back.Visible = false
	back.ZIndex = 60
	back.Parent = boot

	local card = Instance.new("Frame")
	card.Name = "Card"
	card.AnchorPoint = Vector2.new(0.5, 0.5)
	card.Position = UDim2.fromScale(0.5, 0.5)
	card.Size = UDim2.fromOffset(520, 560)
	card.BackgroundColor3 = PANEL
	card.BackgroundTransparency = 0.02
	card.ZIndex = 61
	card.Parent = back
	corner(card, 22)
	stroke(card, ACCENT, 0.3)

	local title = Instance.new("TextLabel")
	title.Size = UDim2.new(1, 0, 0, 56)
	title.Position = UDim2.fromOffset(0, 22)
	title.BackgroundTransparency = 1
	title.Text = "🌙 DREAM SIMULATOR"
	title.TextColor3 = TEXT
	title.Font = Enum.Font.GothamBold
	title.TextScaled = true
	title.ZIndex = 62
	title.Parent = card
	local c1 = Instance.new("UITextSizeConstraint")
	c1.MaxTextSize = 30
	c1.Parent = title

	local subtitle = Instance.new("TextLabel")
	subtitle.Size = UDim2.new(1, -40, 0, 26)
	subtitle.Position = UDim2.fromOffset(20, 78)
	subtitle.BackgroundTransparency = 1
	subtitle.Text = "Каждый сон — новая история..."
	subtitle.TextColor3 = DIM
	subtitle.Font = Enum.Font.Gotham
	subtitle.TextScaled = true
	subtitle.ZIndex = 62
	subtitle.Parent = card
	local c2 = Instance.new("UITextSizeConstraint")
	c2.MaxTextSize = 17
	c2.Parent = subtitle

	local difficultyTitle = Instance.new("TextLabel")
	difficultyTitle.Size = UDim2.new(1, -40, 0, 24)
	difficultyTitle.Position = UDim2.fromOffset(20, 118)
	difficultyTitle.BackgroundTransparency = 1
	difficultyTitle.TextXAlignment = Enum.TextXAlignment.Left
	difficultyTitle.Text = "ВЫБЕРИ СЛОЖНОСТЬ И НАЧНИ СОН"
	difficultyTitle.TextColor3 = ACCENT
	difficultyTitle.Font = Enum.Font.GothamBold
	difficultyTitle.TextScaled = true
	difficultyTitle.ZIndex = 62
	difficultyTitle.Parent = card
	local c3 = Instance.new("UITextSizeConstraint")
	c3.MaxTextSize = 15
	c3.Parent = difficultyTitle

	local entries = {
		{ text = "🟢  ЛЁГКИЙ  ·  100 монет", key = "Easy" },
		{ text = "🟡  ОБЫЧНЫЙ  ·  250 монет", key = "Normal" },
		{ text = "🔴  СЛОЖНЫЙ  ·  500 монет", key = "Hard" },
		{ text = "💀  ЭКСТРИМ  ·  1000 монет", key = "Extreme" },
	}

	local remotesFolder = ReplicatedStorage:WaitForChild("Remotes", 20)

	local function startDream(key)
		back.Visible = false
		if remotesFolder then
			local remote = remotesFolder:FindFirstChild("RequestDream")
			if remote then
				remote:FireServer(key)
			end
		end
	end

	for index, entry in ipairs(entries) do
		makeButton(card, "Difficulty_" .. entry.key, entry.text,
			UDim2.fromOffset(30, 150 + (index - 1) * 64), UDim2.new(1, -60, 0, 54),
			function()
				startDream(entry.key)
			end)
	end

	makeButton(card, "CloseBoot", "✕  ЗАКРЫТЬ", UDim2.fromOffset(30, 420), UDim2.new(1, -60, 0, 50), function()
		back.Visible = false
	end)

	local hint = Instance.new("TextLabel")
	hint.Size = UDim2.new(1, -60, 0, 50)
	hint.Position = UDim2.fromOffset(30, 480)
	hint.BackgroundTransparency = 1
	hint.TextWrapped = true
	hint.Text = "Можно ходить по спальне: WASD — движение, SHIFT — бег. Меню открывается кнопкой сверху или клавишей M."
	hint.TextColor3 = DIM
	hint.Font = Enum.Font.Gotham
	hint.TextScaled = true
	hint.ZIndex = 62
	hint.Parent = card
	local c4 = Instance.new("UITextSizeConstraint")
	c4.MaxTextSize = 14
	c4.Parent = hint
end

menuButton.MouseButton1Click:Connect(function()
	-- Если полноценное меню работает, кнопку не перехватываем
	local topBar = playerGui:FindFirstChild("TopBar")
	if topBar and topBar:GetAttribute("MenuReady") == true then
		return
	end
	back.Visible = not back.Visible
end)

------------------------------------------------------------------
-- Прячемся, когда основное меню готово
------------------------------------------------------------------
task.spawn(function()
	while true do
		task.wait(1)
		local topBar = playerGui:FindFirstChild("TopBar")
		local ready = topBar and topBar:GetAttribute("MenuReady") == true
		if ready then
			boot.Enabled = false
			break
		end
	end
end)

print("[DreamSimulator] BootMenu готов")
