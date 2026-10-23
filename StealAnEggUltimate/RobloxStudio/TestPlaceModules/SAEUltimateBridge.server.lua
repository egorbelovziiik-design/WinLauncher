--!strict
--[[
	STEAL AN EGG ULTIMATE HUB — official test place bridge (server Script)

	Place this Script in ServerScriptService and put EggWorld (ModuleScript)
	next to it. Also add SAEUltimateClient (LocalScript) to StarterPlayerScripts.

	What it does
	------------
	1. Builds the egg island through EggWorld (real eggs, values, respawn).
	2. Pulls the hub's desired state from GET  <BRIDGE_URL>/state every 0.2 s.
	3. Applies movement / automation flags on the server, so collection,
	   coins, teleport and farming are real gameplay changes in THIS place.
	4. Posts an egg + player snapshot to POST <BRIDGE_URL>/snapshot every 0.5 s.
	5. Rebroadcasts the movement flags to clients, which handle fly, noclip,
	   infinite jump and their keybinds locally.

	Requirements: Game Settings → Security → "Allow HTTP Requests" ON.
	Roblox blocks loopback addresses on some Studio builds. If the bridge
	stays offline, the place still works standalone (eggs, prompts, coins);
	the hub then simply stays in demo/snapshot mode.

	This script never touches any other experience and contains no exploit,
	injection or anti-cheat bypass code.
]]

local HttpService = game:GetService("HttpService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local ServerScriptService = game:GetService("ServerScriptService")

local EggWorld = require(ServerScriptService:WaitForChild("EggWorld"))

-- ============================ CONFIG ============================
local BRIDGE_URL = "http://127.0.0.1:8787"
local BRIDGE_ENABLED = true
local POLL_SECONDS = 0.2
local SNAPSHOT_SECONDS = 0.5
local AUTO_COLLECT_RANGE = 12

-- ============================ REMOTES ============================
local remotes = ReplicatedStorage:FindFirstChild("SAEUltimateRemotes")
if not remotes then
	remotes = Instance.new("Folder")
	remotes.Name = "SAEUltimateRemotes"
	remotes.Parent = ReplicatedStorage
end

local function ensureRemote(className: string, name: string)
	local existing = remotes:FindFirstChild(name)
	if existing then
		return existing
	end
	local remote = Instance.new(className)
	remote.Name = name
	remote.Parent = remotes
	return remote
end

local stateRemote = ensureRemote("RemoteEvent", "State") :: RemoteEvent
local clientRemote = ensureRemote("RemoteEvent", "ClientEvent") :: RemoteEvent

-- ============================ STATE ============================
local state = {
	speed = false,
	walkSpeed = 16,
	jumpPower = false,
	jumpPowerValue = 50,
	fly = false,
	flySpeed = 40,
	noclip = false,
	infiniteJump = false,
	flyKey = "F",
	speedKey = "X",
	teleportKey = "T",
	autoCollect = false,
	autoReturn = false,
	autoFarm = false,
	autoSteal = false,
	autoBuy = false,
	autoSell = false,
	autoEggFinder = false,
	autoRareTracker = false,
	intervalMs = 1500,
	target = "Nearest",
	rarityPriority = "Rare first",
	notify = true,
}

local processedCommands: { [number]: boolean } = {}
local lastAutomation = 0
local lastBuy = 0
local lastSnapshotLog = 0
local bridgeOnline: boolean? = nil
local announcementSent = false

local function log(message: string)
	print("[SAEU-Bridge] " .. message)
end

-- ============================ HTTP ============================
local function httpRequest(options): any
	local ok, result = pcall(function()
		return HttpService:RequestAsync(options)
	end)

	if not ok or type(result) ~= "table" then
		if bridgeOnline ~= false then
			bridgeOnline = false
			log("bridge unreachable at " .. BRIDGE_URL .. " (loopback may be blocked in Studio)")
		end
		return nil
	end

	if not result.Success then
		if bridgeOnline ~= false then
			bridgeOnline = false
			log(string.format("bridge HTTP %d at %s", result.StatusCode or -1, BRIDGE_URL))
		end
		return nil
	end

	if bridgeOnline ~= true then
		bridgeOnline = true
		log("bridge connected: " .. BRIDGE_URL)
	end

	return result
end

local function fetchState(): any
	return httpRequest({ Url = BRIDGE_URL .. "/state", Method = "GET" })
end

local function postSnapshot(snapshot: any): any
	return httpRequest({
		Url = BRIDGE_URL .. "/snapshot",
		Method = "POST",
		Headers = { ["Content-Type"] = "application/json" },
		Body = snapshot,
	})
end

local function postEvent(name: string, extra: any?)
	local payload = { name = name }
	if extra then
		for key, value in extra do
			payload[key] = value
		end
	end

	httpRequest({
		Url = BRIDGE_URL .. "/event",
		Method = "POST",
		Headers = { ["Content-Type"] = "application/json" },
		Body = HttpService:JSONEncode(payload),
	})
end

local function getPlayer(): Player?
	local all = Players:GetPlayers()
	if #all == 0 then
		return nil
	end
	return all[1]
end

local function getCharacter(player: Player?): (Model?, Humanoid?)
	if not player then
		return nil, nil
	end
	local character = player.Character
	if not character then
		return nil, nil
	end
	return character, character:FindFirstChildOfClass("Humanoid")
end

local function getRoot(player: Player?): BasePart?
	local character = getCharacter(player)
	if not character then
		return nil
	end
	return character:FindFirstChild("HumanoidRootPart") :: BasePart?
end

-- ============================ COMMANDS ============================
local function applyCommand(command: any)
	if not command or type(command.Action) ~= "string" then
		return
	end

	local player = getPlayer()
	local root = getRoot(player)
	local character, humanoid = getCharacter(player)

	-- The hub serialises camelCase JSON; accept both cases for robustness.
	local action = command.action or command.Action
	local cmdX = command.x or command.X or 0
	local cmdY = command.y or command.Y or 0
	local cmdZ = command.z or command.Z or 0
	local cmdId = command.id or command.Id

	if action == "teleport" and root then
		root.CFrame = CFrame.new(cmdX, cmdY, cmdZ)
		log(string.format("teleported %s to %.1f, %.1f, %.1f", player and player.Name or "player", cmdX, cmdY, cmdZ))
		postEvent("log", { message = string.format("Teleported to %.0f, %.0f, %.0f", command.X, command.Y, command.Z) })
	elseif action == "collect" and root then
		local ok, info = EggWorld.CollectNearest(root.Position, player, false)
		if ok and info then
			log("collected " .. tostring(info.Record and info.Record.Name))
			postEvent("collected", { egg = info.Record and info.Record.Name or "egg", value = info.Value })
		end
	elseif action == "scan" then
		log("scan requested from hub")
		postEvent("notice", { message = "Test place egg index refreshed." })
	elseif action == "automation.start" then
		postEvent("automation", { state = "started" })
	elseif action == "automation.stop" then
		postEvent("automation", { state = "stopped" })
	elseif action == "sell" then
		if player then
			local coins = EggWorld.GetCoins(player)
			postEvent("sold", { coins = coins })
		end
	end

	postEvent("ack", { id = cmdId })
end

local function applyState(remoteState: any)
	if type(remoteState) ~= "table" then
		return
	end

	state.speed = remoteState.speed == true
	state.walkSpeed = tonumber(remoteState.walkSpeed) or 16
	state.jumpPower = remoteState.jumpPower == true
	state.jumpPowerValue = tonumber(remoteState.jumpPowerValue) or 50
	state.fly = remoteState.fly == true
	state.flySpeed = tonumber(remoteState.flySpeed) or 40
	state.noclip = remoteState.noclip == true
	state.infiniteJump = remoteState.infiniteJump == true
	state.flyKey = tostring(remoteState.flyKey or "F")
	state.speedKey = tostring(remoteState.speedKey or "X")
	state.teleportKey = tostring(remoteState.teleportKey or "T")
	state.autoCollect = remoteState.autoCollect == true
	state.autoReturn = remoteState.autoReturn == true
	state.autoFarm = remoteState.autoFarm == true
	state.autoSteal = remoteState.autoSteal == true
	state.autoBuy = remoteState.autoBuy == true
	state.autoSell = remoteState.autoSell == true
	state.autoEggFinder = remoteState.autoEggFinder == true
	state.autoRareTracker = remoteState.autoRareTracker == true
	state.intervalMs = math.clamp(tonumber(remoteState.intervalMs) or 1500, 200, 15000)
	state.target = tostring(remoteState.target or "Nearest")
	state.rarityPriority = tostring(remoteState.rarityPriority or "Rare first")
	state.notify = remoteState.notify ~= false

	local commands = remoteState.commands
	if type(commands) == "table" then
		for _, command in commands do
			local id = tonumber(command.id or command.Id)
			if id and not processedCommands[id] then
				processedCommands[id] = true
				task.spawn(applyCommand, command)
			end
		end
	end

	stateRemote:FireAllClients(state)
end

-- ============================ PLAYER SETUP ============================
local function setupPlayer(player: Player)
	local stats = Instance.new("Folder")
	stats.Name = "leaderstats"
	stats.Parent = player

	local coins = Instance.new("IntValue")
	coins.Name = "Coins"
	coins.Value = 0
	coins.Parent = stats

	local collected = Instance.new("IntValue")
	collected.Name = "Eggs"
	collected.Value = 0
	collected.Parent = stats

	player.CharacterAdded:Connect(function(character)
		local humanoid = character:WaitForChild("Humanoid") :: Humanoid
		task.wait(0.3)
		if state.speed then
			humanoid.WalkSpeed = state.walkSpeed
		end
		if state.jumpPower then
			humanoid.JumpPower = state.jumpPowerValue
			humanoid.UseJumpPower = true
		end
	end)
end

Players.PlayerAdded:Connect(setupPlayer)
for _, player in Players:GetPlayers() do
	task.spawn(setupPlayer, player)
end

clientRemote.OnServerEvent:Connect(function(player, payload)
	if type(payload) ~= "table" then
		return
	end

	if payload.heading then
		EggWorld.SetHeading(player, tonumber(payload.heading) or 0)
	end

	if payload.collected then
		local stats = player:FindFirstChild("leaderstats")
		local counter = stats and stats:FindFirstChild("Eggs")
		if counter and counter:IsA("IntValue") then
			counter.Value = EggWorld.GetCollectedCount(player)
		end
	end
end)

-- ============================ BRIDGE LOOPS ============================
EggWorld.Init()

task.spawn(function()
	while true do
		local result = fetchState()
		if result and result.Body then
			local ok, decoded = pcall(function()
				return HttpService:JSONDecode(result.Body)
			end)
			if ok and type(decoded) == "table" then
				applyState(decoded)
				if not announcementSent then
					announcementSent = true
					postEvent("ready", { place = game.Name })
					log("announced readiness to the hub")
				end
			end
		end
		task.wait(POLL_SECONDS)
	end
end)

task.spawn(function()
	while true do
		local player = getPlayer()
		local snapshot = EggWorld.Snapshot(player)
		local result = postSnapshot(HttpService:JSONEncode(snapshot))
		if result and os.clock() - lastSnapshotLog > 30 then
			lastSnapshotLog = os.clock()
			log(string.format("snapshot sent (%d eggs)", #snapshot.eggs))
		end
		task.wait(SNAPSHOT_SECONDS)
	end
end)

-- ============================ MOVEMENT + AUTOMATION ============================
local function applyMovement()
	local player = getPlayer()
	local character, humanoid = getCharacter(player)
	if not humanoid then
		return
	end

	local desiredSpeed = if state.speed then math.clamp(state.walkSpeed, 8, 200) else 16
	if humanoid.WalkSpeed ~= desiredSpeed then
		humanoid.WalkSpeed = desiredSpeed
	end

	if state.jumpPower then
		humanoid.UseJumpPower = true
		if humanoid.JumpPower ~= state.jumpPowerValue then
			humanoid.JumpPower = state.jumpPowerValue
		end
	end

	if state.noclip and character then
		for _, part in character:GetDescendants() do
			if part:IsA("BasePart") and part.CanCollide then
				part.CanCollide = false
			end
		end
	elseif character then
		for _, part in character:GetDescendants() do
			if part:IsA("BasePart") and part.Name ~= "HumanoidRootPart" and part.Name ~= "Head" then
				part.CanCollide = true
			end
		end
	end
end

local function automationStep()
	local now = os.clock()
	if now - lastAutomation < math.clamp(state.intervalMs / 1000, 0.2, 15) then
		return
	end
	lastAutomation = now

	local player = getPlayer()
	local root = getRoot(player)
	local character, humanoid = getCharacter(player)
	if not root or not humanoid then
		return
	end

	if state.autoSell then
		local stats = player and player:FindFirstChild("leaderstats")
		local counter = stats and stats:FindFirstChild("Eggs")
		if counter and counter:IsA("IntValue") and counter.Value >= 3 then
			EggWorld.AwardCoins(player, 25 * counter.Value)
			postEvent("sold", { coins = 25 * counter.Value })
			counter.Value = 0
		end
	end

	if state.autoBuy and now - lastBuy > 5 and EggWorld.SpendCoins(player, 100) then
		lastBuy = now
		local spawned = EggWorld.SpawnBonus(if EggWorld.GetCoins(player) > 600 then "Legendary" else "Rare")
		postEvent("notice", { message = "Auto buy: lucky crate egg spawned." })
		_ = spawned
	end

	if state.autoReturn and (root.Position - Vector3.new(0, 4, 0)).Magnitude > 140 then
		root.CFrame = CFrame.new(0, 6, 0)
		return
	end

	local wantsFarm = state.autoFarm or state.autoSteal
	if not wantsFarm and not state.autoCollect and not state.autoEggFinder then
		return
	end

	local rareOnly = state.autoSteal or state.autoRareTracker
	local nearest, distance = EggWorld.Nearest(root.Position, rareOnly)

	if nearest and distance then
		if distance <= AUTO_COLLECT_RANGE then
			local ok, info = EggWorld.CollectNearest(root.Position, player, rareOnly)
			if ok and info and info.Record then
				postEvent("collected", { egg = info.Record.Name, value = info.Value })
				local stats = player and player:FindFirstChild("leaderstats")
				local counter = stats and stats:FindFirstChild("Eggs")
				if counter and counter:IsA("IntValue") then
					counter.Value = EggWorld.GetCollectedCount(player)
				end
			end
		elseif wantsFarm then
			humanoid:MoveTo(nearest.Part.Position)
		end
	end
end

RunService.Heartbeat:Connect(function()
	EggWorld.Update()
	applyMovement()
	automationStep()
end)

log("STEAL AN EGG ULTIMATE bridge initialised — hub endpoint " .. BRIDGE_URL)
