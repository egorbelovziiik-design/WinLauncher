--!nonstrict
-- AchievementManager: проверка условий достижений.

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local DreamDefinitions = require(SharedModules:WaitForChild("DreamDefinitions"))

local DataManager = require(script.Parent:WaitForChild("DataManager"))

local AchievementManager = {}

local function unlock(player, id)
	DataManager.unlockAchievement(player, id)
end

-- cause: "coins" | "shop" | "dream" | "quest" | "end"
function AchievementManager.check(player, cause, context)
	local profile = DataManager.get(player)
	if not profile then
		return
	end
	local stats = profile.stats
	context = context or {}

	if stats.dreams >= 1 then
		unlock(player, "first_dream")
	end
	if stats.dreams >= 10 then
		unlock(player, "dreams_10")
	end
	if stats.dreams >= 25 then
		unlock(player, "dreams_25")
	end
	if stats.wins >= 5 then
		unlock(player, "wins_5")
	end
	if stats.wins >= 20 then
		unlock(player, "wins_20")
	end
	if stats.quests >= 10 then
		unlock(player, "quests_10")
	end
	if stats.quests >= 30 then
		unlock(player, "quests_30")
	end
	if stats.coinsEarned >= 1000 then
		unlock(player, "coins_1000")
	end
	if stats.coinsEarned >= 5000 then
		unlock(player, "coins_5000")
	end

	local ownedCount = 0
	for _ in pairs(profile.owned) do
		ownedCount += 1
	end
	if ownedCount >= 5 then
		unlock(player, "shop_5")
	end

	local allVisited = true
	for _, dream in ipairs(DreamDefinitions.List) do
		if not profile.stats.dreamsVisited[dream.id] then
			allVisited = false
			break
		end
	end
	if allVisited then
		unlock(player, "all_dreams")
	end

	if cause == "end" then
		if context.win then
			unlock(player, "first_win")
			if context.difficulty == "Extreme" then
				unlock(player, "extreme_win")
			end
			if context.survived and context.survived <= 300 then
				unlock(player, "speedrun")
			end
			if context.health and context.health <= 1 then
				unlock(player, "survivor")
			end
		else
			unlock(player, "death")
		end
	end
end

return AchievementManager
