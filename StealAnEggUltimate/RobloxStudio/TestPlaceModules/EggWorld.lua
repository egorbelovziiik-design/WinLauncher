--!strict
--[[
	EggWorld — server ModuleScript for the official STEAL AN EGG test place.

	Creates a small egg island, tracks egg values and handles collection,
	respawn and the coin economy. Only this module mutates game state; the
	bridge script is a thin transport on top of it.

	Place this ModuleScript in ServerScriptService as "EggWorld".
]]

local EggWorld = {}

local Workspace = game:GetService("Workspace")
local Players = game:GetService("Players")

local RARITIES = {
	{ Name = "Common", Weight = 42, Min = 5, Max = 28, Color = Color3.fromRGB(156, 163, 175) },
	{ Name = "Uncommon", Weight = 25, Min = 40, Max = 95, Color = Color3.fromRGB(52, 211, 153) },
	{ Name = "Rare", Weight = 16, Min = 120, Max = 280, Color = Color3.fromRGB(56, 189, 248) },
	{ Name = "Epic", Weight = 10, Min = 400, Max = 950, Color = Color3.fromRGB(168, 85, 247) },
	{ Name = "Legendary", Weight = 5, Min = 1500, Max = 3600, Color = Color3.fromRGB(251, 191, 36) },
	{ Name = "Mythic", Weight = 2, Min = 6000, Max = 12000, Color = Color3.fromRGB(244, 114, 182) },
}

local NAMES = {
	Common = { "Speckled Egg", "Moss Egg", "Sandstone Egg", "Clover Egg" },
	Uncommon = { "Azure Egg", "Copper Egg", "Fern Egg", "Tide Egg" },
	Rare = { "Golden Egg", "Frost Egg", "Coral Egg", "Sunburst Egg" },
	Epic = { "Amethyst Egg", "Storm Egg", "Magma Egg", "Moonlit Egg" },
	Legendary = { "Celestial Egg", "Obsidian Egg", "Prism Egg" },
	Mythic = { "Eclipse Egg", "Nebula Egg" },
}

local EGG_COUNT = 24
local ISLAND_RADIUS = 100
local RESPAWN_SECONDS = 12
local COLLECT_RANGE = 12

local random = Random.new(20261023)
local eggs: { [Model]: any } = {}
local eggFolder: Folder
local coinsByPlayer: { [Player]: number } = {}
local collectedByPlayer: { [Player]: number } = {}
local headingByPlayer: { [Player]: number } = {}

local function pickRarity()
	local total = 0
	for _, entry in RARITIES do
		total += entry.Weight
	end

	local roll = random:NextNumber() * total
	for _, entry in RARITIES do
		roll -= entry.Weight
		if roll <= 0 then
			return entry
		end
	end

	return RARITIES[1]
end

local function getOrCreateIsland(): BasePart
	local existing = Workspace:FindFirstChild("EggIsland")
	if existing and existing:IsA("BasePart") then
		return existing
	end

	local island = Instance.new("Part")
	island.Name = "EggIsland"
	island.Size = Vector3.new(ISLAND_RADIUS * 2 + 40, 2, ISLAND_RADIUS * 2 + 40)
	island.Position = Vector3.new(0, 0, 0)
	island.Anchored = true
	island.Material = Enum.Material.Slate
	island.Color = Color3.fromRGB(24, 18, 38)
	island.TopSurface = Enum.SurfaceType.Smooth
	island.Parent = Workspace

	local light = Instance.new("PointLight")
	light.Brightness = 1.4
	light.Range = 60
	light.Color = Color3.fromRGB(168, 85, 247)
	light.Parent = island

	return island
end

local function getOrCreateSpawn(island: BasePart): SpawnLocation
	local existing = Workspace:FindFirstChild("EggSpawn")
	if existing and existing:IsA("SpawnLocation") then
		return existing
	end

	local spawnPad = Instance.new("SpawnLocation")
	spawnPad.Name = "EggSpawn"
	spawnPad.Size = Vector3.new(16, 1, 16)
	spawnPad.Position = Vector3.new(0, 3, 0)
	spawnPad.Anchored = true
	spawnPad.Neutral = true
	spawnPad.Duration = 0
	spawnPad.Material = Enum.Material.Neon
	spawnPad.Color = Color3.fromRGB(124, 58, 237)
	spawnPad.Parent = Workspace

	return spawnPad
end

local function makeEggModel(rarityName: string, name: string, value: number, position: Vector3)
	local rarity = RARITIES[1]
	for _, entry in RARITIES do
		if entry.Name == rarityName then
			rarity = entry
		end
	end

	local model = Instance.new("Model")
	model.Name = name

	local part = Instance.new("Part")
	part.Name = "EggPart"
	part.Shape = Enum.PartType.Ball
	part.Size = Vector3.new(3, 4, 3)
	part.Position = position
	part.Anchored = true
	part.CanCollide = false
	part.Material = if rarity.Name == "Common" or rarity.Name == "Uncommon" then Enum.Material.SmoothPlastic else Enum.Material.Neon
	part.Color = rarity.Color
	part.Parent = model

	local glow = Instance.new("PointLight")
	glow.Brightness = if rarity.Name == "Mythic" or rarity.Name == "Legendary" then 2.2 else 0.7
	glow.Range = 18
	glow.Color = rarity.Color
	glow.Parent = part

	local billboard = Instance.new("BillboardGui")
	billboard.Size = UDim2.fromOffset(180, 54)
	billboard.StudsOffset = Vector3.new(0, 3.4, 0)
	billboard.AlwaysOnTop = true
	billboard.MaxDistance = 220
	billboard.Parent = part

	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = UDim2.fromScale(1, 1)
	label.Font = Enum.Font.GothamBold
	label.TextScaled = true
	label.TextColor3 = rarity.Color
	label.Text = string.format("%s\n%s · %d", name, rarity.Name, value)
	label.Parent = billboard

	local prompt = Instance.new("ProximityPrompt")
	prompt.ActionText = "Steal Egg"
	prompt.ObjectText = name
	prompt.HoldDuration = 0
	prompt.MaxActivationDistance = COLLECT_RANGE
	prompt.RequiresLineOfSight = false
	prompt.Parent = part

	model:SetAttribute("Rarity", rarity.Name)
	model:SetAttribute("Value", value)
	part:SetAttribute("EggId", name)

	model.PrimaryPart = part
	model.Parent = eggFolder

	local record = {
		Model = model,
		Part = part,
		Name = name,
		Rarity = rarity.Name,
		Value = value,
		SpawnPosition = position,
		Taken = false,
		RespawnAt = 0,
	}

	eggs[model] = record

	prompt.Triggered:Connect(function(player)
		EggWorld.Collect(record, player)
	end)

	return record
end

local function randomSpawnPosition(): Vector3
	local angle = random:NextNumber() * math.pi * 2
	local radius = 20 + random:NextNumber() * (ISLAND_RADIUS - 24)
	return Vector3.new(math.cos(angle) * radius, 4.2, math.sin(angle) * radius)
end

function EggWorld.AwardCoins(player: Player, amount: number)
	coinsByPlayer[player] = (coinsByPlayer[player] or 0) + amount
	local stats = player:FindFirstChild("leaderstats")
	if stats then
		local coins = stats:FindFirstChild("Coins")
		if coins and coins:IsA("IntValue") then
			coins.Value = coinsByPlayer[player]
		end
	end
end

function EggWorld.GetCoins(player: Player): number
	return coinsByPlayer[player] or 0
end

function EggWorld.SpendCoins(player: Player, amount: number): boolean
	local current = coinsByPlayer[player] or 0
	if current < amount then
		return false
	end

	EggWorld.AwardCoins(player, -amount)
	return true
end

function EggWorld.SetHeading(player: Player, degrees: number)
	headingByPlayer[player] = degrees
end

function EggWorld.Collect(record, player: Player?): (boolean, number)
	if not record or record.Taken then
		return false, 0
	end

	record.Taken = true
	record.RespawnAt = os.clock() + RESPAWN_SECONDS
	record.Part.Transparency = 1
	record.Part.CanTouch = false

	if player then
		EggWorld.AwardCoins(player, record.Value)
		collectedByPlayer[player] = (collectedByPlayer[player] or 0) + 1
	end

	return true, record.Value
end

function EggWorld.CollectNearest(position: Vector3, player: Player?, rareOnly: boolean?): (boolean, any)
	local best = nil
	local bestDistance = math.huge

	for _, record in eggs do
		if not record.Taken then
			local isRare = record.Rarity == "Rare" or record.Rarity == "Epic" or record.Rarity == "Legendary" or record.Rarity == "Mythic"
			if not rareOnly or isRare then
				local distance = (record.Part.Position - position).Magnitude
				if distance < bestDistance then
					best = record
					bestDistance = distance
				end
			end
		end
	end

	if best and bestDistance <= COLLECT_RANGE then
		local ok, value = EggWorld.Collect(best, player)
		return ok, { Record = best, Distance = bestDistance, Value = value }
	end

	return false, best
end

function EggWorld.Nearest(position: Vector3, rareOnly: boolean?): any
	local best = nil
	local bestDistance = math.huge

	for _, record in eggs do
		if not record.Taken then
			local isRare = record.Rarity == "Rare" or record.Rarity == "Epic" or record.Rarity == "Legendary" or record.Rarity == "Mythic"
			if not rareOnly or isRare then
				local distance = (record.Part.Position - position).Magnitude
				if distance < bestDistance then
					best = record
					bestDistance = distance
				end
			end
		end
	end

	return best, bestDistance
end

function EggWorld.SpawnBonus(rarityName: string): any
	local rarity = RARITIES[1]
	for _, entry in RARITIES do
		if entry.Name == rarityName then
			rarity = entry
		end
	end

	local names = NAMES[rarity.Name] or NAMES.Common
	local name = names[random:NextInteger(1, #names)]
	local value = random:NextInteger(rarity.Min, rarity.Max)
	return makeEggModel(rarity.Name, name, value, randomSpawnPosition())
end

function EggWorld.Init()
	local island = getOrCreateIsland()
	getOrCreateSpawn(island)

	local existing = Workspace:FindFirstChild("Eggs")
	if existing then
		existing:Destroy()
	end

	eggFolder = Instance.new("Folder")
	eggFolder.Name = "Eggs"
	eggFolder.Parent = Workspace

	eggs = {}

	for index = 1, EGG_COUNT do
		local rarity = pickRarity()
		local names = NAMES[rarity.Name] or NAMES.Common
		local name = names[random:NextInteger(1, #names)]
		local value = random:NextInteger(rarity.Min, rarity.Max)
		local record = makeEggModel(rarity.Name, name, value, randomSpawnPosition())
		record.Part:SetAttribute("EggIndex", index)
	end

	print(string.format("[EggWorld] spawned %d eggs", EGG_COUNT))
end

function EggWorld.Update()
	local now = os.clock()

	for _, record in eggs do
		if record.Taken and now >= record.RespawnAt then
			record.Taken = false
			record.Part.Transparency = 0
			record.Part.CanTouch = true
		end
	end
end

function EggWorld.GetEggRecords(): any
	return eggs
end

function EggWorld.Snapshot(player: Player?): any
	local entries = {}

	for _, record in eggs do
		if not record.Taken then
			local position = record.Part.Position
			table.insert(entries, {
				name = record.Name,
				rarity = record.Rarity,
				value = record.Value,
				x = position.X,
				y = position.Y,
				z = position.Z,
				taken = false,
			})
		end
	end

	local playerState = nil
	if player and player.Character then
		local humanoid = player.Character:FindFirstChildOfClass("Humanoid")
		local root = player.Character:FindFirstChild("HumanoidRootPart")
		if humanoid and root then
			playerState = {
				name = player.Name,
				x = root.Position.X,
				y = root.Position.Y,
				z = root.Position.Z,
				health = humanoid.Health,
				maxHealth = humanoid.MaxHealth,
				walkSpeed = humanoid.WalkSpeed,
				coins = coinsByPlayer[player] or 0,
				place = game.Name,
				heading = headingByPlayer[player] or 0,
				cameraHeight = 12,
			}
		end
	end

	return {
		player = player and player.Name or "",
		place = game.Name,
		time = os.time() * 1000,
		source = "official-test-place",
		eggs = entries,
		playerState = playerState,
	}
end

function EggWorld.GetCollectedCount(player: Player): number
	return collectedByPlayer[player] or 0
end

Players.PlayerRemoving:Connect(function(player)
	coinsByPlayer[player] = nil
	collectedByPlayer[player] = nil
	headingByPlayer[player] = nil
end)

return EggWorld
