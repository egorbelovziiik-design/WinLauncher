--!nocheck
--[[
	ClientMain.client.luau
	Запуск клиентских контроллеров DREAM SIMULATOR: NIGHTMARE WORLDS.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local StarterGui = game:GetService("StarterGui")
local Workspace = game:GetService("Workspace")

local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ClientState = require(script.Parent.Modules.ClientState)
local SoundController = require(script.Parent.Modules.SoundController)
local CameraController = require(script.Parent.Modules.CameraController)
local MenuController = require(script.Parent.Modules.MenuController)
local HUDController = require(script.Parent.Modules.HUDController)
local EffectsController = require(script.Parent.Modules.EffectsController)
local CutsceneController = require(script.Parent.Modules.CutsceneController)
local InputController = require(script.Parent.Modules.InputController)
local DiagnosticsController = require(script.Parent.Modules.DiagnosticsController)

local player = Players.LocalPlayer

-- Прячем стандартные элементы интерфейса, которые не нужны в хорроре.
-- CoreGui грузится не сразу, поэтому повторяем несколько секунд.
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
	for _ = 1, 12 do
		task.wait(0.5)
		hideCoreGui()
	end
end)

-- Ждём, пока сервер создаст Remotes
Remotes.getFolder()
task.wait(0.3)

local function safeStart(name, startFn)
	local ok, err = pcall(startFn)
	if not ok then
		warn("[DreamSimulator] " .. name .. " не запустился: " .. tostring(err))
	end
	return ok
end

safeStart("SoundController", SoundController.start)
safeStart("CameraController", CameraController.start)
safeStart("HUDController", HUDController.start)
safeStart("MenuController", MenuController.start)
safeStart("EffectsController", EffectsController.start)
safeStart("CutsceneController", CutsceneController.start)
safeStart("InputController", InputController.start)
safeStart("DiagnosticsController", DiagnosticsController.start)

ClientState.setPhase("loading")

------------------------------------------------------------------
-- Профиль
------------------------------------------------------------------
local profileFunction = Remotes.get("RequestProfile")
task.spawn(function()
	local ok, profile = pcall(function()
		return profileFunction:InvokeServer()
	end)
	if ok and profile then
		ClientState.setProfile(profile)
	end
end)

Remotes.get("ProfileUpdated").OnClientEvent:Connect(function(profile)
	if profile then
		ClientState.setProfile(profile)
	end
end)

------------------------------------------------------------------
-- Переходы между фазами
------------------------------------------------------------------
Remotes.get("DreamStateChanged").OnClientEvent:Connect(function(data)
	if data.state == "menu" then
		-- CutsceneController уже переключил фазу, но подстрахуемся
		if ClientState.phase ~= "menu" then
			ClientState.setPhase("menu")
		end
	end
end)

ClientState.on("Phase", function(phase)
	if phase == "menu" then
		CameraController.setMode("menu")
		MenuController.show()
	elseif phase == "dream" then
		MenuController.hide()
	end
end)

-- Стартуем в меню: показываем спальню и кнопки
task.delay(0.6, function()
	ClientState.setPhase("menu")
	CameraController.setMode("menu")
	MenuController.show()
	local ready = Remotes.get("PlayerReady")
	if ready then
		ready:FireServer()
	end
end)

-- Кнопка МЕНЮ всегда доступна, даже если что-то выше упало
task.delay(1.6, function()
	local topBar = player:WaitForChild("PlayerGui"):FindFirstChild("TopBar")
	if topBar then
		topBar.Enabled = true
	end
	if ClientState.phase ~= "dream" then
		MenuController.show()
	end
end)

------------------------------------------------------------------
-- Персонаж: первое лицо включаем только во сне
------------------------------------------------------------------
player.CameraMode = Enum.CameraMode.Classic
-- Не переводим камеру в Scriptable на старте: до первой фазы она должна
-- показывать лобби, иначе игрок видит чёрный экран до срабатывания меню.
if Workspace.CurrentCamera and Workspace.CurrentCamera.CameraType ~= Enum.CameraType.Custom then
	CameraController.setMode("menu")
end

player.CharacterAdded:Connect(function()
	if ClientState.phase == "menu" then
		CameraController.setMode("menu")
	end
end)

------------------------------------------------------------------
-- Резервная кнопка МЕНЮ живёт в отдельном скрипте BootMenu.client.luau
------------------------------------------------------------------

print("[DreamSimulator] Клиент готов")
