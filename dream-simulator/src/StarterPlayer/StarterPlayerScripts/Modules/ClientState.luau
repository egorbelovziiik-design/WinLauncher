--!nocheck
--[[
	ClientState.luau
	Общее состояние клиента: профиль, настройки, фаза игры, кэш элементов интерфейса.
]]

local Players = game:GetService("Players")
local TweenService = game:GetService("TweenService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local UserInputService = game:GetService("UserInputService")
local RunService = game:GetService("RunService")

local Config = require(ReplicatedStorage.SharedModules.Config)
local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ItemDefinitions = require(ReplicatedStorage.SharedModules.ItemDefinitions)

local ClientState = {}

ClientState.player = Players.LocalPlayer
ClientState.playerGui = ClientState.player:WaitForChild("PlayerGui")
ClientState.phase = "loading" -- loading | menu | sleeping | dream | waking | result
ClientState.profile = nil
ClientState.settings = {
	musicVolume = 0.5,
	sfxVolume = 0.75,
	quality = "Средний",
	sensitivity = 0.5,
	scareIntensity = 1,
	hints = true,
	cameraShake = true,
}
ClientState.dream = nil
ClientState.quests = {}
ClientState.mandatoryLeft = 0
ClientState.timer = { remaining = Config.DreamDuration, total = Config.DreamDuration }
ClientState.vitals = { sanity = 100, stamina = 100, health = 100, maxHealth = 100, maxSanity = 100, maxStamina = 100, sprinting = false, flashlight = false, battery = 100, batteryMax = 100 }
ClientState.prompt = nil
ClientState.result = nil
ClientState.bonuses = {}
ClientState.isMobile = UserInputService.TouchEnabled and not UserInputService.KeyboardEnabled
ClientState.toastHandlers = {}
ClientState.stateHandlers = {}
ClientState.elementCache = {}

function ClientState.on(event, handler)
	ClientState[event .. "Handlers"] = ClientState[event .. "Handlers"] or {}
	table.insert(ClientState[event .. "Handlers"], handler)
	return function()
		local list = ClientState[event .. "Handlers"]
		for index, item in ipairs(list) do
			if item == handler then
				table.remove(list, index)
				break
			end
		end
	end
end

function ClientState.emit(event, ...)
	local list = ClientState[event .. "Handlers"]
	if not list then
		return
	end
	for _, handler in ipairs(list) do
		task.spawn(handler, ...)
	end
end

function ClientState.setPhase(phase)
	if ClientState.phase == phase then
		return
	end
	ClientState.phase = phase
	ClientState.emit("Phase", phase)
end

------------------------------------------------------------------
-- Профиль
------------------------------------------------------------------
function ClientState.setProfile(profile)
	if not profile then
		return
	end
	ClientState.profile = profile
	if profile.settings then
		for key, value in pairs(profile.settings) do
			ClientState.settings[key] = value
		end
	end
	ClientState.emit("Profile", profile)
end

function ClientState.getCoins()
	return ClientState.profile and ClientState.profile.coins or 0
end

function ClientState.ownsItem(itemId)
	return ClientState.profile ~= nil and ClientState.profile.owned[itemId] == true
end

function ClientState.isEquipped(itemId)
	return ClientState.profile ~= nil and ClientState.profile.equipped[itemId] == true
end

function ClientState.consumableCount(itemId)
	return ClientState.profile and ClientState.profile.consumables[itemId] or 0
end

------------------------------------------------------------------
-- Интерфейс
------------------------------------------------------------------
function ClientState.getGui(name)
	local gui = ClientState.playerGui:FindFirstChild(name)
	if not gui then
		gui = ClientState.playerGui:WaitForChild(name, 12)
	end
	return gui
end

function ClientState.el(guiName, elementName)
	local key = guiName .. "::" .. elementName
	local cached = ClientState.elementCache[key]
	if cached and cached.Parent then
		return cached
	end
	local gui = ClientState.getGui(guiName)
	local found = gui and gui:FindFirstChild(elementName, true)
	if found then
		ClientState.elementCache[key] = found
	end
	return found
end

function ClientState.setGuiEnabled(guiName, enabled)
	local gui = ClientState.getGui(guiName)
	if gui then
		gui.Enabled = enabled
	end
end

function ClientState.tween(instance, duration, goal, style, direction)
	if not instance then
		return nil
	end
	local info = TweenInfo.new(
		duration,
		style or Enum.EasingStyle.Quad,
		direction or Enum.EasingDirection.Out
	)
	local tween = TweenService:Create(instance, info, goal)
	tween:Play()
	return tween
end

function ClientState.toast(text, color, icon)
	if not text then
		return
	end
	ClientState.emit("Toast", { text = text, color = color, icon = icon })
end

function ClientState.playSound(name)
	ClientState.emit("Sound", name)
end

function ClientState.isDream()
	return ClientState.phase == "dream"
end

-- Курсор: во сне он заблокирован по центру (первое лицо),
-- но для диалогов, инвентаря и меню его нужно освобождать.
function ClientState.setCursorFree(free)
	local ok = pcall(function()
		if free then
			UserInputService.MouseBehavior = Enum.MouseBehavior.Default
			UserInputService.MouseIconEnabled = true
		elseif ClientState.phase == "dream" then
			UserInputService.MouseBehavior = Enum.MouseBehavior.LockCenter
			UserInputService.MouseIconEnabled = false
		else
			UserInputService.MouseBehavior = Enum.MouseBehavior.Default
			UserInputService.MouseIconEnabled = true
		end
	end)
	return ok
end

function ClientState.fmtTime(seconds)
	local s = math.max(0, math.floor(seconds))
	return string.format("%02d:%02d", math.floor(s / 60), s % 60)
end

function ClientState.quality()
	return ClientState.settings.quality or "Средний"
end

function ClientState.scareLevel()
	return math.clamp(ClientState.settings.scareIntensity or 1, 0, 1)
end

return ClientState
