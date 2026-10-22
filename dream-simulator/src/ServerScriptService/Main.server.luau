--!nonstrict
-- Main: запуск сервера, подключение сетевых событий, жизненный цикл игрока.

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))

local DataManager = require(script.Parent:WaitForChild("DataManager"))
local EconomyManager = require(script.Parent:WaitForChild("EconomyManager"))
local AchievementManager = require(script.Parent:WaitForChild("AchievementManager"))
local DreamManager = require(script.Parent:WaitForChild("DreamManager"))

print(("[DREAM SIMULATOR] сервер запущен, версия %s"):format(Config.VERSION))

DataManager.init()
DreamManager.init()

local function setupLeaderstats(player)
	local existing = player:FindFirstChild("leaderstats")
	if existing then
		existing:Destroy()
	end
	local folder = Instance.new("Folder")
	folder.Name = "leaderstats"
	folder.Parent = player

	local coins = Instance.new("IntValue")
	coins.Name = "Монеты"
	coins.Value = DataManager.get(player).coins
	coins.Parent = folder

	local dreams = Instance.new("IntValue")
	dreams.Name = "Сны"
	dreams.Value = DataManager.get(player).stats.dreams
	dreams.Parent = folder

	task.spawn(function()
		while player.Parent do
			local profile = DataManager.get(player)
			coins.Value = profile.coins
			dreams.Value = profile.stats.dreams
			task.wait(2)
		end
	end)
end

local function onCharacterAdded(player, character)
	task.wait(0.3)
	if not player.Parent then
		return
	end
	DreamManager.preparePlayer(player)
	local state = DreamManager.getState(player)
	if state == "dream" then
		local session = DreamManager.getSession(player)
		if session and session.mapModel then
			local spawnPos = session.mapModel:GetAttribute("SpawnPosition") or Vector3.new(0, 8, 0)
			character:PivotTo(CFrame.new(spawnPos))
		end
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		humanoid.Died:Connect(function()
			local session = DreamManager.getSession(player)
			if session and not session.ended then
				DreamManager.endDream(player, false, "Кошмар настиг тебя")
			end
		end)
	end
end

local function onPlayerAdded(player)
	DataManager.load(player)
	setupLeaderstats(player)

	if player.Character then
		task.spawn(function()
			DreamManager.preparePlayer(player)
			DreamManager.returnToMenu(player)
			DataManager.pushProfile(player)
		end)
	end

	player.CharacterAdded:Connect(function(character)
		onCharacterAdded(player, character)
	end)

	task.delay(2.5, function()
		if player.Parent then
			DataManager.pushProfile(player)
			local state = Remotes.get("DreamStateChanged")
			if state then
				state:FireClient(player, { state = DreamManager.getState(player) })
			end
		end
	end)
end

Players.PlayerAdded:Connect(onPlayerAdded)
for _, player in ipairs(Players:GetPlayers()) do
	task.spawn(onPlayerAdded, player)
end

Players.PlayerRemoving:Connect(function(player)
	DreamManager.cleanupPlayer(player)
	DataManager.remove(player)
end)

-- ------------------------------------------------------------------
-- Сетевые события
-- ------------------------------------------------------------------

Remotes.get("PlayerReady").OnServerEvent:Connect(function(player)
	DataManager.pushProfile(player)
	local state = Remotes.get("DreamStateChanged")
	if state then
		state:FireClient(player, { state = DreamManager.getState(player) })
	end
	local toast = Remotes.get("Toast")
	if toast then
		toast:FireClient(player, { text = "Добро пожаловать в мир снов", color = Config.Colors.accent, icon = "🌙" })
	end
end)

Remotes.get("RequestDream").OnServerEvent:Connect(function(player, difficultyKey, dreamId)
	if type(difficultyKey) ~= "string" or not Config.validDifficulty(difficultyKey) then
		difficultyKey = "Normal"
	end
	if type(dreamId) ~= "string" then
		dreamId = nil
	end
	local ok, err = DreamManager.startDream(player, difficultyKey, dreamId)
	if not ok and err then
		local toast = Remotes.get("Toast")
		if toast then
			toast:FireClient(player, { text = err, color = Config.Colors.bad, icon = "⚠️" })
		end
	end
end)

Remotes.get("ReturnToMenu").OnServerEvent:Connect(function(player)
	DreamManager.returnToMenu(player)
end)

Remotes.get("BuyItem").OnServerEvent:Connect(function(player, itemId)
	if type(itemId) ~= "string" then
		return
	end
	EconomyManager.buyItem(player, itemId)
end)

Remotes.get("EquipItem").OnServerEvent:Connect(function(player, itemId)
	if type(itemId) ~= "string" then
		return
	end
	EconomyManager.equipItem(player, itemId)
end)

Remotes.get("UnequipItem").OnServerEvent:Connect(function(player, slot)
	if type(slot) ~= "string" then
		return
	end
	EconomyManager.unequipItem(player, slot)
end)

Remotes.get("UseItem").OnServerEvent:Connect(function(player, itemId)
	if type(itemId) ~= "string" then
		return
	end
	EconomyManager.useConsumable(player, itemId)
end)

Remotes.get("UpdateSettings").OnServerEvent:Connect(function(player, key, value)
	EconomyManager.updateSettings(player, key, value)
end)

Remotes.get("ResetSettings").OnServerEvent:Connect(function(player)
	EconomyManager.resetSettings(player)
end)

Remotes.get("Interact").OnServerEvent:Connect(function(player, promptId)
	if type(promptId) ~= "string" then
		return
	end
	DreamManager.handleInteract(player, promptId)
end)

Remotes.get("DialogChoice").OnServerEvent:Connect(function(player, action)
	if type(action) ~= "string" then
		return
	end
	DreamManager.handleDialogChoice(player, action)
end)

Remotes.get("CloseDialog").OnServerEvent:Connect(function(player)
	local closed = Remotes.get("DialogClosed")
	if closed then
		closed:FireClient(player)
	end
end)

Remotes.get("SetSprinting").OnServerEvent:Connect(function(player, value)
	DreamManager.setSprinting(player, value == true)
end)

Remotes.get("SetFlashlight").OnServerEvent:Connect(function(player, value)
	DreamManager.setFlashlight(player, value == true)
end)

Remotes.get("LookUpdate").OnServerEvent:Connect(function(player, lookVector)
	DreamManager.setLook(player, lookVector)
end)

Remotes.get("ReportDeath").OnServerEvent:Connect(function(player)
	local session = DreamManager.getSession(player)
	if session and not session.ended then
		DreamManager.endDream(player, false, "Кошмар настиг тебя")
	end
end)

Remotes.get("RequestProfile").OnServerInvoke = function(player)
	return DataManager.get(player), DreamManager.getState(player)
end

-- Страховка: раз в минуту сохраняем прогресс игроков в меню.
task.spawn(function()
	while true do
		task.wait(60)
		for _, player in ipairs(Players:GetPlayers()) do
			local state = DreamManager.getState(player)
			if state ~= "dream" and state ~= "sleeping" then
				DataManager.save(player)
			end
		end
	end
end)

print("[DREAM SIMULATOR] все системы готовы")
