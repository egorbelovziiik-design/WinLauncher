--!nonstrict
-- EconomyManager: магазин, инвентарь, настройки. Все проверки — на сервере.

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local ItemDefinitions = require(SharedModules:WaitForChild("ItemDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))

local DataManager = require(script.Parent:WaitForChild("DataManager"))

local EconomyManager = {}

local function sendResult(player, ok, message, itemId)
	local remote = Remotes.get("PurchaseResult")
	if remote then
		remote:FireClient(player, { ok = ok, message = message, itemId = itemId })
	end
end

local function sendEquipResult(player, ok, message, itemId)
	local remote = Remotes.get("EquipResult")
	if remote then
		remote:FireClient(player, { ok = ok, message = message, itemId = itemId })
	end
end

function EconomyManager.buyItem(player, itemId)
	if type(itemId) ~= "string" then
		return false
	end
	local item = ItemDefinitions.get(itemId)
	if not item then
		sendResult(player, false, "Такого предмета нет", itemId)
		return false
	end
	local profile = DataManager.get(player)

	if item.stackable then
		if (profile.consumables[itemId] or 0) >= 99 then
			sendResult(player, false, "Больше 99 штук не поместится", itemId)
			return false
		end
	elseif profile.owned[itemId] then
		sendResult(player, false, "Этот предмет уже куплен", itemId)
		return false
	end

	if profile.coins < item.price then
		sendResult(player, false, ("Не хватает монет: нужно %d"):format(item.price), itemId)
		return false
	end
	if not DataManager.spendCoins(player, item.price) then
		sendResult(player, false, "Покупка отклонена сервером", itemId)
		return false
	end

	if item.stackable then
		profile.consumables[itemId] = (profile.consumables[itemId] or 0) + 1
	else
		profile.owned[itemId] = true
		if item.slot == "flashlight" and not profile.owned.flashlight then
			profile.owned.flashlight = true
		end
	end

	DataManager.pushProfile(player)
	DataManager.save(player)
	sendResult(player, true, ("Куплено: %s"):format(item.name), itemId)

	local AchievementManager = script.Parent:FindFirstChild("AchievementManager")
	if AchievementManager then
		require(AchievementManager).check(player, "shop")
	end
	return true
end

function EconomyManager.equipItem(player, itemId)
	local item = ItemDefinitions.get(itemId)
	local profile = DataManager.get(player)
	if not item or item.stackable then
		sendEquipResult(player, false, "Этот предмет нельзя надеть", itemId)
		return false
	end
	if not (profile.owned[itemId] or (itemId == "flashlight" and profile.owned.flashlight)) then
		sendEquipResult(player, false, "Предмет не куплен", itemId)
		return false
	end
	if profile.equipped[itemId] then
		profile.equipped[itemId] = nil
		sendEquipResult(player, true, ("Снято: %s"):format(item.name), itemId)
	else
		-- один предмет на слот
		if item.slot then
			for otherId in pairs(profile.equipped) do
				local other = ItemDefinitions.get(otherId)
				if other and other.slot == item.slot then
					profile.equipped[otherId] = nil
				end
			end
		end
		profile.equipped[itemId] = true
		sendEquipResult(player, true, ("Надето: %s"):format(item.name), itemId)
	end
	DataManager.pushProfile(player)
	DataManager.save(player)
	EconomyManager.applyCosmetics(player)
	return true
end

function EconomyManager.unequipItem(player, slot)
	local profile = DataManager.get(player)
	for itemId in pairs(profile.equipped) do
		local item = ItemDefinitions.get(itemId)
		if item and item.slot == slot then
			profile.equipped[itemId] = nil
		end
	end
	DataManager.pushProfile(player)
	DataManager.save(player)
	EconomyManager.applyCosmetics(player)
	sendEquipResult(player, true, "Снято", nil)
	return true
end

-- Суммарные бонусы надетых предметов.
function EconomyManager.getBonuses(player)
	local profile = DataManager.get(player)
	local bonuses = {
		sanityMul = 1,
		timeBonus = 0,
		lightRange = 0,
		lightColor = nil,
		speedBonus = 0,
		scareMul = 1,
	}
	for itemId in pairs(profile.equipped) do
		local item = ItemDefinitions.get(itemId)
		if item then
			if item.lightRange then
				bonuses.lightRange = math.max(bonuses.lightRange, item.lightRange)
			end
			if item.lightColor then
				bonuses.lightColor = item.lightColor
			end
			local bonus = item.bonus or {}
			if bonus.sanityMul then
				bonuses.sanityMul = bonuses.sanityMul * bonus.sanityMul
			end
			if bonus.timeBonus then
				bonuses.timeBonus = bonuses.timeBonus + bonus.timeBonus
			end
			if bonus.speedBonus then
				bonuses.speedBonus = bonuses.speedBonus + bonus.speedBonus
			end
			if bonus.scareMul then
				bonuses.scareMul = math.min(bonuses.scareMul, bonus.scareMul)
			end
		end
	end
	if profile.owned.flashlight and bonuses.lightRange <= 0 then
		bonuses.lightRange = 30
	end
	return bonuses
end

function EconomyManager.applyCosmetics(player)
	local profile = DataManager.get(player)
	local character = player.Character
	if not character then
		return
	end
	local root = character:FindFirstChild("HumanoidRootPart")
	if not root then
		return
	end

	local oldTrail = root:FindFirstChild("DreamTrail")
	if oldTrail then
		oldTrail:Destroy()
	end
	if profile.equipped.trail then
		local attachment0 = Instance.new("Attachment")
		attachment0.Name = "TrailA0"
		attachment0.Position = Vector3.new(0, 1.4, 0)
		attachment0.Parent = root
		local attachment1 = Instance.new("Attachment")
		attachment1.Name = "TrailA1"
		attachment1.Position = Vector3.new(0, -1.4, 0)
		attachment1.Parent = root
		local trail = Instance.new("Trail")
		trail.Name = "DreamTrail"
		trail.Attachment0 = attachment0
		trail.Attachment1 = attachment1
		trail.Lifetime = 0.6
		trail.Color = ColorSequence.new(Color3.fromRGB(170, 180, 255))
		trail.Transparency = NumberSequence.new(0.2)
		trail.Parent = root
	end

	local oldWings = character:FindFirstChild("DreamWings")
	if oldWings then
		oldWings:Destroy()
	end
	if profile.equipped.wings then
		local wings = Instance.new("Model")
		wings.Name = "DreamWings"
		for side = -1, 1, 2 do
			for index = 1, 3 do
				local part = Instance.new("Part")
				part.Name = "Wing"
				part.Size = Vector3.new(0.2, 3 + index, 2.4)
				part.Color = Color3.fromRGB(70, 50, 110)
				part.Material = Enum.Material.Neon
				part.Transparency = 0.35
				part.CanCollide = false
				part.Massless = true
				part.Anchored = false
				part.CFrame = root.CFrame * CFrame.new(side * (1.2 + index * 0.5), 1.4, 0.4 - index * 0.3)
				part.Parent = wings
				local weld = Instance.new("WeldConstraint")
				weld.Part0 = root
				weld.Part1 = part
				weld.Parent = part
			end
		end
		wings.Parent = character
	end
end

function EconomyManager.useConsumable(player, itemId)
	local item = ItemDefinitions.get(itemId)
	local profile = DataManager.get(player)
	if not item or not item.stackable then
		sendEquipResult(player, false, "Это не расходник", itemId)
		return false
	end
	local count = profile.consumables[itemId] or 0
	if count <= 0 then
		sendEquipResult(player, false, "Расходник закончился", itemId)
		return false
	end
	local DreamManager = script.Parent:FindFirstChild("DreamManager")
	local applied = false
	if DreamManager then
		applied = require(DreamManager).applyConsumable(player, item)
	end
	if not applied then
		sendEquipResult(player, false, "Сейчас это нельзя использовать", itemId)
		return false
	end
	profile.consumables[itemId] = count - 1
	DataManager.pushProfile(player)
	DataManager.save(player)
	sendEquipResult(player, true, ("Использовано: %s"):format(item.name), itemId)
	return true
end

function EconomyManager.updateSettings(player, key, value)
	local profile = DataManager.get(player)
	if type(key) == "table" then
		for innerKey, innerValue in pairs(key) do
			EconomyManager.updateSettings(player, innerKey, innerValue)
		end
		return true
	end
	if type(key) ~= "string" then
		return false
	end
	local settings = profile.settings
	if key == "musicVolume" or key == "sfxVolume" or key == "sensitivity" or key == "scareIntensity" then
		value = tonumber(value)
		if value ~= value or value == math.huge then
			return false
		end
		local maxValue = (key == "scareIntensity") and 2 or 1
		settings[key] = math.clamp(value, 0, maxValue)
	elseif key == "hints" or key == "cameraShake" then
		settings[key] = value == true
	elseif key == "quality" then
		if value == "Низкое" or value == "Средний" or value == "Высокое" then
			settings.quality = value
		end
	else
		return false
	end
	DataManager.pushProfile(player)
	DataManager.save(player)
	return true
end

function EconomyManager.resetSettings(player)
	local profile = DataManager.get(player)
	profile.settings = Config.defaultSettings()
	DataManager.pushProfile(player)
	DataManager.save(player)
	return true
end

return EconomyManager
