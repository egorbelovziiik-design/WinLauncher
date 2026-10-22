--!nocheck
--[[
	DiagnosticsController.luau
	Диагностика и защита от «тихих» ошибок:
	  • F8 — панель состояния (сборка, режим данных, фаза, сон, FPS, пинг);
	  • ошибки клиентских скриптов превращаются в понятные тосты,
		а не молча ломают игру.

	Панель создаётся кодом, поэтому не зависит от верстки ScreenGui.
]]

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")
local Stats = game:GetService("Stats")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local DreamDefinitions = require(SharedModules:WaitForChild("DreamDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))
local ClientState = require(script.Parent.ClientState)

local DiagnosticsController = {}
local panel
local lines = {}
local visible = false
local errorCount = 0
local lastError = ""

local COLORS = {
	bg = Color3.fromRGB(11, 12, 24),
	stroke = Color3.fromRGB(140, 130, 255),
	text = Color3.fromRGB(226, 228, 246),
	dim = Color3.fromRGB(150, 154, 190),
	good = Color3.fromRGB(130, 235, 170),
	warn = Color3.fromRGB(255, 200, 120),
	bad = Color3.fromRGB(255, 130, 140),
}

local function newLine(parentFrame, index, text, color)
	local label = Instance.new("TextLabel")
	label.Name = "Line" .. index
	label.BackgroundTransparency = 1
	label.Position = UDim2.new(0, 18, 0, 46 + (index - 1) * 22)
	label.Size = UDim2.new(1, -36, 0, 20)
	label.Font = Enum.Font.Gotham
	label.TextSize = 15
	label.TextXAlignment = Enum.TextXAlignment.Left
	label.TextColor3 = color or COLORS.text
	label.Text = text
	label.Parent = parentFrame
	lines[index] = label
	return label
end

local function buildPanel()
	local playerGui = Players.LocalPlayer:WaitForChild("PlayerGui")
	local gui = Instance.new("ScreenGui")
	gui.Name = "DiagnosticsGUI"
	gui.ResetOnSpawn = false
	gui.IgnoreGuiInset = true
	gui.DisplayOrder = 60
	gui.Enabled = false
	gui.Parent = playerGui

	local card = Instance.new("Frame")
	card.Name = "Card"
	card.AnchorPoint = Vector2.new(0, 0)
	card.Position = UDim2.new(0, 24, 0, 96)
	card.Size = UDim2.new(0, 400, 0, 300)
	card.BackgroundColor3 = COLORS.bg
	card.BackgroundTransparency = 0.08
	card.BorderSizePixel = 0
	card.Parent = gui

	local corner = Instance.new("UICorner")
	corner.CornerRadius = UDim.new(0, 14)
	corner.Parent = card

	local stroke = Instance.new("UIStroke")
	stroke.Color = COLORS.stroke
	stroke.Thickness = 1.4
	stroke.Transparency = 0.35
	stroke.Parent = card

	local title = Instance.new("TextLabel")
	title.Name = "Title"
	title.BackgroundTransparency = 1
	title.Position = UDim2.new(0, 18, 0, 12)
	title.Size = UDim2.new(1, -36, 0, 24)
	title.Font = Enum.Font.GothamBold
	title.TextSize = 17
	title.TextXAlignment = Enum.TextXAlignment.Left
	title.TextColor3 = COLORS.stroke
	title.Text = "🛠 ДИАГНОСТИКА  ·  F8 — закрыть"
	title.Parent = card

	for index = 1, 10 do
		newLine(card, index, "", COLORS.dim)
	end

	panel = gui
	return gui
end

local currentFps = 0
local fpsAccum, fpsFrames = 0, 0
RunService.RenderStepped:Connect(function(dt)
	fpsAccum += dt
	fpsFrames += 1
	if fpsAccum >= 0.5 then
		currentFps = math.floor(fpsFrames / fpsAccum)
		fpsAccum, fpsFrames = 0, 0
	end
end)

local function describeSettings()
	local settings = ClientState.settings or {}
	local quality = settings.quality or "Средний"
	return ("качество %s · скрымеры %.1f · тряска %s"):format(
		quality,
		settings.scareIntensity or 1,
		settings.cameraShake and "вкл" or "выкл"
	)
end

function DiagnosticsController.refresh()
	if not panel then
		return
	end
	local dataMode = (workspace:GetAttribute("DataStoreMode")) or "неизвестно"
	local version = (workspace:GetAttribute("BuildVersion")) or "dev"
	local ready = workspace:GetAttribute("GameReady") == true
	local dream = ClientState.dream
	local quests = 0
	for _, quest in ipairs(ClientState.quests or {}) do
		if quest.state == "done" then
			quests += 1
		end
	end
	local ping = 0
	pcall(function()
		ping = math.floor(Stats.Network.ServerStatsItem["Data Ping"]:GetValue())
	end)

	local clockText = os.date("%H:%M:%S")
	local rows = {
		{ "Сборка: " .. tostring(version) .. (ready and "  ·  мир готов" or "  ·  мир грузится"), ready and COLORS.good or COLORS.warn },
		{ "Данные: " .. tostring(dataMode), dataMode == "cloud" and COLORS.good or COLORS.warn },
		{ "Фаза: " .. tostring(ClientState.phase) .. "  ·  FPS " .. tostring(currentFps) .. tostring(ping > 0 and ("  ·  " .. ping .. " мс") or ""), COLORS.text },
		{ dream and ("Сон: " .. tostring(dream.dreamName) .. " (" .. tostring(dream.dreamId) .. ")") or "Сон: нет", COLORS.dim },
		{ ("Заданий выполнено: %d  ·  батарея %d%%"):format(quests, math.floor((ClientState.vitals and ClientState.vitals.battery) or 0)), COLORS.text },
		{ "Снов в базе: " .. tostring(#DreamDefinitions.List) .. "  ·  каналов: " .. tostring(#Remotes.EVENTS + #Remotes.FUNCTIONS), COLORS.dim },
		{ describeSettings(), COLORS.dim },
		{ ("Ошибок клиента: %d"):format(errorCount), errorCount > 0 and COLORS.bad or COLORS.good },
		{ errorCount > 0 and ("Последняя: " .. string.sub(lastError, 1, 54)) or "Ошибок нет", errorCount > 0 and COLORS.bad or COLORS.good },
		{ "Время: " .. clockText, COLORS.dim },
	}

	for index, row in ipairs(rows) do
		local label = lines[index]
		if label then
			label.Text = row[1]
			label.TextColor3 = row[2]
		end
	end
end

function DiagnosticsController.toggle(force)
	if not panel then
		buildPanel()
	end
	visible = force ~= nil and force or not visible
	panel.Enabled = visible
	if visible then
		DiagnosticsController.refresh()
	end
end

function DiagnosticsController.report(message)
	errorCount += 1
	lastError = tostring(message or "неизвестная ошибка"):gsub("\n.*", "")
	ClientState.toast("Ошибка: " .. string.sub(lastError, 1, 90), COLORS.bad, "⚠️")
	if visible then
		DiagnosticsController.refresh()
	end
end

function DiagnosticsController.start()
	buildPanel()

	-- Чтобы F8 не занимала игра, биндим напрямую
	UserInputService.InputBegan:Connect(function(input, processed)
		if processed then
			return
		end
		if input.KeyCode == Enum.KeyCode.F8 then
			DiagnosticsController.toggle()
		end
	end)

	-- Ошибки клиента: показываем тост и считаем
	local okContext, scriptContext = pcall(function()
		return game:GetService("ScriptContext")
	end)
	if okContext and scriptContext then
		scriptContext.Error:Connect(function(message, stack, _script)
			local first = tostring(message)
			errorCount += 1
			lastError = first
			warn("[DreamSimulator][client] " .. first .. "\n" .. tostring(stack))
			ClientState.toast("Ошибка: " .. string.sub(first, 1, 90), COLORS.bad, "⚠️")
			if visible then
				DiagnosticsController.refresh()
			end
		end)
	end

	-- Раз в секунду обновляем панель, если она открыта
	task.spawn(function()
		while true do
			task.wait(1)
			if visible then
				DiagnosticsController.refresh()
			end
		end
	end)
end

return DiagnosticsController
