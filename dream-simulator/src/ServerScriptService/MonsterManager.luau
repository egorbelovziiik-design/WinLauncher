--!nonstrict
-- MonsterManager: спавн и ИИ монстров на PathfindingService.

local PathfindingService = game:GetService("PathfindingService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")
local CollectionService = game:GetService("CollectionService")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local MonsterDefinitions = require(SharedModules:WaitForChild("MonsterDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))

local MapService = require(script.Parent:WaitForChild("MapService"))

local MonsterManager = {}

local TEMPLATE_BY_BEHAVIOR = {
	stalker = "Stalker",
	chaser = "Chaser",
	watcher = "Watcher",
	mimic = "Mimic",
	hunter = "Hunter",
}

local function getTemplate(behavior)
	local templates = ServerStorage:FindFirstChild("MonsterTemplates")
	local name = TEMPLATE_BY_BEHAVIOR[behavior] or "Stalker"
	return templates and templates:FindFirstChild(name)
end

local function tintModel(model, color, eyeColor, transparency)
	for _, part in ipairs(model:GetDescendants()) do
		if part:IsA("BasePart") then
			if part:GetAttribute("Eye") or string.sub(part.Name, 1, 3) == "Eye" then
				part.Color = eyeColor
				part.Material = Enum.Material.Neon
				part.Transparency = 0
			else
				part.Color = color
				if transparency and transparency > 0 then
					part.Transparency = transparency
				end
			end
		elseif part:IsA("PointLight") then
			part.Color = eyeColor
		elseif part:IsA("ParticleEmitter") then
			part.Color = ColorSequence.new(eyeColor)
		end
	end
end

function MonsterManager.spawnMonster(session, monsterId, position)
	local def = MonsterDefinitions.get(monsterId)
	local template = getTemplate(def.behavior)
	if not template then
		warn("[MonsterManager] нет шаблона: " .. tostring(def.behavior))
		return nil
	end
	local model = template:Clone()
	model.Name = "Monster_" .. def.id
	local scale = def.scale or 1
	if scale ~= 1 then
		pcall(function()
			model:ScaleTo(scale)
		end)
	end
	tintModel(model, def.color, def.eyeColor, def.transparency)

	local root = model.PrimaryPart or model:FindFirstChild("Root")
	if not root then
		model:Destroy()
		return nil
	end
	model:PivotTo(CFrame.new(position))
	model.Parent = session.container

	local monster = {
		id = def.id,
		model = model,
		root = root,
		def = def,
		state = "patrol",
		waypoints = {},
		waypointIndex = 1,
		lastRepath = 0,
		lastSeen = 0,
		attackCooldown = 1.5,
		announced = false,
		home = position,
		visible = false,
		patrolIndex = 1,
	}

	table.insert(session.monsters, monster)
	CollectionService:AddTag(model, "Monster")
	return monster
end

function MonsterManager.start(session)
	session.monsters = session.monsters or {}
	local markers = MapService.collectMarkers(session.mapModel, "MK_Monster")
	local spawnPoints = {}
	for _, marker in ipairs(markers) do
		table.insert(spawnPoints, marker.Position + Vector3.new(0, 4, 0))
	end
	if #spawnPoints == 0 then
		local spawnPos = session.mapModel:GetAttribute("SpawnPosition") or Vector3.new(0, 6, 0)
		table.insert(spawnPoints, spawnPos + Vector3.new(30, 0, 30))
	end
	local types = session.dream.monsters or { "shadow" }
	local rng = session.rng or Random.new()
	local count = session.difficulty.monsterCount or 3
	for index = 1, count do
		local monsterId = types[rng:NextInteger(1, #types)]
		local point = spawnPoints[((index - 1) % #spawnPoints) + 1]
		MonsterManager.spawnMonster(session, monsterId, point + Vector3.new(rng:NextNumber(-8, 8), 0, rng:NextNumber(-8, 8)))
	end
end

function MonsterManager.spawnBoss(session)
	if session.bossSpawned or session.ended then
		return
	end
	session.bossSpawned = true
	local bossId = session.dream.boss or "nightmare"
	local spawnPos = session.mapModel:GetAttribute("SpawnPosition") or Vector3.new(0, 6, 0)
	local monster = MonsterManager.spawnMonster(session, bossId, spawnPos + Vector3.new(0, 0, -34))
	if monster then
		monster.state = "hunt"
		local alert = Remotes.get("MonsterAlert")
		if alert then
			alert:FireClient(session.player, { name = monster.def.name })
		end
		local scare = Remotes.get("Scare")
		if scare then
			scare:FireClient(session.player, { kind = "boss", monsterName = monster.def.name, intensity = 1.3 })
		end
		local hint = Remotes.get("Hint")
		if hint then
			hint:FireClient(session.player, "🩸 Кошмар проснулся. Не смотри ему в глаза — беги.")
		end
	end
end

local function getTargetRoot(session)
	local character = session.player and session.player.Character
	if not character then
		return nil
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	local root = character:FindFirstChild("HumanoidRootPart")
	if not humanoid or not root or humanoid.Health <= 0 then
		return nil
	end
	return root
end

local function isVisible(fromPosition, targetPosition, ignoreModel)
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = { ignoreModel }
	params.IgnoreWater = true
	local result = workspace:Raycast(fromPosition, targetPosition - fromPosition, params)
	return result == nil
end

local function moveMonster(monster, dt, targetPosition, speed)
	local position = monster.root.Position
	local direction = targetPosition - position
	local distance = direction.Magnitude
	if distance < 0.5 then
		return true
	end
	local step = math.min(speed * dt, distance)
	local newPosition = position + Vector3.new(direction.X, 0, direction.Z).Unit * step
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = { monster.model }
	local hit = workspace:Raycast(newPosition + Vector3.new(0, 14, 0), Vector3.new(0, -34, 0), params)
	if hit then
		newPosition = Vector3.new(newPosition.X, hit.Position.Y + 3.2, newPosition.Z)
	end
	local lookAt = Vector3.new(newPosition.X + direction.X, newPosition.Y, newPosition.Z + direction.Z)
	if (lookAt - newPosition).Magnitude > 0.1 then
		monster.model:PivotTo(CFrame.lookAt(newPosition, lookAt))
	else
		monster.model:PivotTo(CFrame.new(newPosition))
	end
	return distance <= step + 0.5
end

function MonsterManager.damagePlayer(session, monster)
	local player = session.player
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	if not humanoid or humanoid.Health <= 0 then
		return
	end
	local difficulty = session.difficulty
	local bonuses = session.bonuses or {}
	local damage = monster.def.damage * (difficulty.monsterDamage or 1) * (bonuses.scareMul or 1)
	humanoid:TakeDamage(damage)
	session.sanity = math.max(0, (session.sanity or 100) - monster.def.sanityDrain * 0.8)
	session.lastAttacker = monster.def.name

	local scare = Remotes.get("Scare")
	if scare and math.random() < 0.65 then
		scare:FireClient(player, {
			kind = monster.def.boss and "boss" or "jumpscare",
			monsterName = monster.def.name,
			intensity = (monster.def.boss and 1.4 or 1) * (difficulty.monsterDamage or 1),
		})
	end
	local growl = Remotes.get("Scare")
	if growl and math.random() < 0.3 then
		growl:FireClient(player, { kind = "growl", monsterName = monster.def.name, intensity = 0.7 })
	end
end

local function repath(session, monster, goal)
	local path = PathfindingService:CreatePath({
		AgentRadius = 2.5,
		AgentHeight = 6,
		AgentCanJump = true,
		AgentJumpHeight = 8,
		AgentMaxSlope = 45,
		WaypointSpacing = 6,
	})
	local ok = pcall(function()
		path:ComputeAsync(monster.root.Position, goal)
	end)
	if ok and path.Status == Enum.PathStatus.Success then
		monster.waypoints = path:GetWaypoints()
	else
		monster.waypoints = { { Position = goal } }
	end
	monster.waypointIndex = 2
	monster.lastRepath = os.clock()
end

function MonsterManager.update(session, monster, dt)
	if session.ended or not monster.model.Parent then
		return
	end
	local now = os.clock()
	monster.attackCooldown = math.max(0, monster.attackCooldown - dt)

	local targetRoot = getTargetRoot(session)
	if not targetRoot then
		return
	end
	local def = monster.def
	local monsterPosition = monster.root.Position
	local targetPosition = targetRoot.Position
	local distance = (targetPosition - monsterPosition).Magnitude
	local noise = session.sprinting and 1.35 or 1
	local detectRange = def.detectRange * noise
	local visible = false
	if distance <= detectRange then
		visible = isVisible(monsterPosition + Vector3.new(0, 2.5, 0), targetPosition, monster.model)
	end
	monster.visible = visible
	if visible then
		monster.lastSeen = now
	end

	local behavior = def.behavior
	if behavior == "watcher" then
		-- Наблюдатель замирает, когда на него смотрят, и атакует разум на расстоянии.
		local lookVector = session.lookVector
		local watched = false
		if lookVector then
			local toMonster = (monsterPosition - targetPosition)
			if toMonster.Magnitude > 0.1 then
				local dot = Vector3.new(lookVector.X, 0, lookVector.Z).Unit:Dot(Vector3.new(toMonster.X, 0, toMonster.Z).Unit)
				watched = dot > 0.55
			end
		end
		if visible and watched and distance < 60 then
			session.sanity = math.max(0, session.sanity - 1.2 * dt)
		end
		if visible and not watched and distance > 8 then
			if now - monster.lastRepath > 2 then
				repath(session, monster, targetPosition)
			end
			local waypoint = monster.waypoints[monster.waypointIndex]
			if waypoint then
				if moveMonster(monster, dt, waypoint.Position, def.speed * 0.55) then
					monster.waypointIndex = monster.waypointIndex + 1
				end
			end
		end
		if distance <= def.attackRange and monster.attackCooldown <= 0 and not watched then
			monster.attackCooldown = 3.2
			MonsterManager.damagePlayer(session, monster)
		end
		return
	end

	local chasing = false
	if behavior == "mimic" then
		if distance < 14 and visible then
			chasing = true
			if not monster.revealed then
				monster.revealed = true
				local scare = Remotes.get("Scare")
				if scare then
					scare:FireClient(session.player, { kind = "reveal", monsterName = def.name, intensity = 1 })
				end
				local alert = Remotes.get("MonsterAlert")
				if alert then
					alert:FireClient(session.player, { name = def.name })
				end
			end
		elseif now - monster.lastSeen < 5 then
			chasing = true
		end
	else
		chasing = (visible and distance <= detectRange) or (now - monster.lastSeen < 5)
	end

	local goal = targetPosition
	if not chasing then
		local patrols = session.patrolPoints
		if patrols and #patrols > 0 then
			local point = patrols[((monster.patrolIndex - 1) % #patrols) + 1].Position
			if (monsterPosition - point).Magnitude < 8 then
				monster.patrolIndex = monster.patrolIndex + 1
			end
			goal = point + Vector3.new(0, 3, 0)
		else
			goal = monster.home
		end
	end

	local speed = def.speed * (session.difficulty.monsterSpeed or 1)
	if chasing then
		speed *= 1.1
	end

	if now - monster.lastRepath > (chasing and 1.4 or 3.5) then
		repath(session, monster, goal)
	end
	local waypoint = monster.waypoints[monster.waypointIndex]
	if waypoint then
		if moveMonster(monster, dt, waypoint.Position, speed) then
			monster.waypointIndex = monster.waypointIndex + 1
		end
	else
		moveMonster(monster, dt, goal, speed)
	end

	if distance <= def.attackRange and monster.attackCooldown <= 0 then
		monster.attackCooldown = 2.6
		MonsterManager.damagePlayer(session, monster)
	end

	if not monster.announced and distance < 42 and visible then
		monster.announced = true
		local alert = Remotes.get("MonsterAlert")
		if alert then
			alert:FireClient(session.player, { name = def.name })
		end
		local growl = Remotes.get("Scare")
		if growl then
			growl:FireClient(session.player, { kind = "growl", monsterName = def.name, intensity = 0.8 })
		end
	end
end

function MonsterManager.stop(session)
	if not session.monsters then
		return
	end
	for _, monster in ipairs(session.monsters) do
		if monster.model and monster.model.Parent then
			monster.model:Destroy()
		end
	end
	session.monsters = {}
end

function MonsterManager.getNearest(session, position)
	local nearest = nil
	local best = math.huge
	for _, monster in ipairs(session.monsters or {}) do
		if monster.model and monster.model.Parent then
			local distance = (monster.root.Position - position).Magnitude
			if distance < best then
				best = distance
				nearest = monster
			end
		end
	end
	return nearest, best
end

return MonsterManager
