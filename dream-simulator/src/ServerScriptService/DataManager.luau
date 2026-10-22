--!nonstrict
-- DataManager: профиль игрока, DataStore, автосохранение, валидация.

local DataStoreService = game:GetService("DataStoreService")
local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local ItemDefinitions = require(SharedModules:WaitForChild("ItemDefinitions"))
local AchievementDefinitions = require(SharedModules:WaitForChild("AchievementDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))

local DataManager = {}

local store = nil
local profiles = {}
local saving = {}

local function defaultProfile()
	return {
		version = Config.ProfileVersion,
		coins = 0,
		owned = {},
		consumables = {},
		equipped = {},
		achievements = {},
		settings = Config.defaultSettings(),
		difficulty = "Normal",
		lastDream = nil,
		stats = {
			dreams = 0,
			wins = 0,
			losses = 0,
			quests = 0,
			coinsEarned = 0,
			playtime = 0,
			bestTime = 0,
			dreamsVisited = {},
		},
	}
end

local function clampNumber(value, fallback, minValue, maxValue)
	if type(value) ~= "number" or value ~= value or value == math.huge or value == -math.huge then
		return fallback
	end
	if value < minValue then
		return minValue
	end
	if value > maxValue then
		return maxValue
	end
	return value
end

local function validateSettings(raw)
	local settings = Config.defaultSettings()
	if type(raw) ~= "table" then
		return settings
	end
	settings.musicVolume = clampNumber(raw.musicVolume, settings.musicVolume, 0, 1)
	settings.sfxVolume = clampNumber(raw.sfxVolume, settings.sfxVolume, 0, 1)
	settings.sensitivity = clampNumber(raw.sensitivity, settings.sensitivity, 0, 1)
	settings.scareIntensity = clampNumber(raw.scareIntensity, settings.scareIntensity, 0, 2)
	if raw.hints ~= nil then
		settings.hints = raw.hints == true
	end
	if raw.cameraShake ~= nil then
		settings.cameraShake = raw.cameraShake == true
	end
	if raw.quality == "Низкое" or raw.quality == "Средний" or raw.quality == "Высокое" then
		settings.quality = raw.quality
	end
	return settings
end

-- Приводит любые данные (в том числе испорченные) к корректному профилю.
function DataManager.validate(raw)
	local profile = defaultProfile()
	if type(raw) ~= "table" then
		return profile
	end
	profile.coins = math.floor(clampNumber(raw.coins, 0, 0, 99999999))
	profile.difficulty = Config.Difficulties[raw.difficulty] and raw.difficulty or "Normal"
	profile.lastDream = type(raw.lastDream) == "string" and raw.lastDream or nil
	profile.settings = validateSettings(raw.settings)

	if type(raw.owned) == "table" then
		for itemId, value in pairs(raw.owned) do
			if value == true and ItemDefinitions.get(itemId) then
				profile.owned[itemId] = true
			end
		end
	end
	if type(raw.consumables) == "table" then
		for itemId, count in pairs(raw.consumables) do
			local item = ItemDefinitions.get(itemId)
			if item and item.stackable then
				profile.consumables[itemId] = math.floor(clampNumber(count, 0, 0, 999))
			end
		end
	end
	if type(raw.equipped) == "table" then
		for itemId, value in pairs(raw.equipped) do
			if value == true and profile.owned[itemId] then
				profile.equipped[itemId] = true
			end
		end
	end
	if type(raw.achievements) == "table" then
		for id, value in pairs(raw.achievements) do
			if value == true and AchievementDefinitions.get(id) then
				profile.achievements[id] = true
			end
		end
	end
	local stats = raw.stats
	if type(stats) == "table" then
		for _, key in ipairs({ "dreams", "wins", "losses", "quests", "coinsEarned" }) do
			profile.stats[key] = math.floor(clampNumber(stats[key], 0, 0, 99999999))
		end
		profile.stats.playtime = clampNumber(stats.playtime, 0, 0, 99999999)
		profile.stats.bestTime = clampNumber(stats.bestTime, 0, 0, 99999)
		if type(stats.dreamsVisited) == "table" then
			for dreamId, value in pairs(stats.dreamsVisited) do
				if value == true then
					profile.stats.dreamsVisited[dreamId] = true
				end
			end
		end
	end
	return profile
end

function DataManager.init()
	local ok, result = pcall(function()
		return DataStoreService:GetDataStore(Config.DataStoreName)
	end)
	if ok then
		store = result
		workspace:SetAttribute("DataStoreMode", "cloud")
	else
		workspace:SetAttribute("DataStoreMode", "memory")
		warn("[DataManager] DataStore недоступен, прогресс только в памяти: " .. tostring(result))
	end

	task.spawn(function()
		while true do
			task.wait(Config.SaveInterval)
			for _, player in ipairs(Players:GetPlayers()) do
				DataManager.save(player)
			end
		end
	end)

	game:BindToClose(function()
		for _, player in ipairs(Players:GetPlayers()) do
			DataManager.save(player)
		end
		if not RunService:IsStudio() then
			task.wait(2)
		end
	end)
end

function DataManager.load(player)
	if profiles[player] then
		return profiles[player]
	end
	local raw = nil
	if store then
		local key = Config.DataStoreKey .. tostring(player.UserId)
		for attempt = 1, 4 do
			local ok, result = pcall(function()
				return store:GetAsync(key)
			end)
			if ok then
				raw = result
				break
			end
			warn(("[DataManager] GetAsync fail (%d): %s"):format(attempt, tostring(result)))
			task.wait(attempt * 0.6)
		end
	end
	local profile = DataManager.validate(raw)
	profiles[player] = profile
	return profile
end

function DataManager.get(player)
	return profiles[player] or DataManager.load(player)
end

function DataManager.save(player)
	local profile = profiles[player]
	if not profile or saving[player] then
		return
	end
	saving[player] = true
	if store then
		local key = Config.DataStoreKey .. tostring(player.UserId)
		local ok, err = pcall(function()
			store:SetAsync(key, profile)
		end)
		if not ok then
			warn("[DataManager] сохранение не удалось: " .. tostring(err))
		end
	end
	saving[player] = false
end

function DataManager.remove(player)
	DataManager.save(player)
	profiles[player] = nil
	saving[player] = nil
end

function DataManager.pushProfile(player)
	local remote = Remotes.get("ProfileUpdated")
	if remote then
		remote:FireClient(player, DataManager.get(player))
	end
end

function DataManager.addCoins(player, amount)
	local profile = DataManager.get(player)
	amount = math.floor(tonumber(amount) or 0)
	if amount <= 0 then
		return 0
	end
	profile.coins = math.min(profile.coins + amount, 99999999)
	profile.stats.coinsEarned = profile.stats.coinsEarned + amount
	DataManager.pushProfile(player)
	local AchievementManager = script.Parent:FindFirstChild("AchievementManager")
	if AchievementManager then
		local manager = require(AchievementManager)
		manager.check(player, "coins")
	end
	return amount
end

function DataManager.spendCoins(player, amount)
	local profile = DataManager.get(player)
	amount = math.floor(tonumber(amount) or 0)
	if amount <= 0 or profile.coins < amount then
		return false
	end
	profile.coins = profile.coins - amount
	DataManager.pushProfile(player)
	return true
end

function DataManager.updateStat(player, key, delta)
	local profile = DataManager.get(player)
	if type(profile.stats[key]) == "number" then
		profile.stats[key] = profile.stats[key] + (tonumber(delta) or 0)
	else
		profile.stats[key] = delta
	end
end

function DataManager.markDreamVisited(player, dreamId)
	local profile = DataManager.get(player)
	profile.stats.dreamsVisited[dreamId] = true
end

function DataManager.unlockAchievement(player, id)
	local profile = DataManager.get(player)
	local def = AchievementDefinitions.get(id)
	if not def or profile.achievements[id] then
		return false
	end
	profile.achievements[id] = true
	local remote = Remotes.get("AchievementUnlocked")
	if remote then
		remote:FireClient(player, {
			id = id,
			name = def.name,
			description = def.description,
			icon = def.icon,
			reward = def.reward,
		})
	end
	profile.coins = math.min(profile.coins + (def.reward or 0), 99999999)
	profile.stats.coinsEarned = profile.stats.coinsEarned + (def.reward or 0)
	DataManager.pushProfile(player)
	return true
end

return DataManager
