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

local player = Players.LocalPlayer

-- Прячем стандартные элементы интерфейса, которые не нужны в хорроре
pcall(function()
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, false)
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.PlayerList, false)
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.EmotesMenu, false)
	StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Health, false)
end)

-- Ждём, пока сервер создаст Remotes
Remotes.getFolder()
task.wait(0.3)

SoundController.start()
CameraController.start()
HUDController.start()
MenuController.start()
EffectsController.start()
CutsceneController.start()
InputController.start()

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
	Remotes.get("PlayerReady"):FireServer()
end)

------------------------------------------------------------------
-- Персонаж: первое лицо включаем только во сне
------------------------------------------------------------------
player.CameraMode = Enum.CameraMode.Classic
Workspace.CurrentCamera.CameraType = Enum.CameraType.Scriptable

player.CharacterAdded:Connect(function()
	if ClientState.phase == "menu" then
		CameraController.setMode("menu")
	end
end)

print("[DreamSimulator] Клиент готов")
