--!nonstrict
-- MonsterManager: спавн и ИИ монстров. Полностью переписан:
-- монстры всегда ставятся на пол, не спавнятся рядом с игроком,
-- не застревают в геометрии и не «залипают» друг в друге.

local CollectionService = game:GetService("CollectionService")
local PathfindingService = game:GetService("PathfindingService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")
local Debris = game:GetService("Debris")

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

local ATTACK_COOLDOWN = 2.4

local function getTemplate(behavior)
	local templates = ServerStorage:FindFirstChild("MonsterTemplates")
	local name = TEMPLATE_BY_BEHAVIOR[behavior] or "Stalker"
	return templates and templates:FindFirstChild(name)
end

local function playerRoot(session)
	local character = session.player and session.player.Character
	if not character then
		return nil
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	local root = character:FindFirstChild("HumanoidRootPart")
	if not humanoid or not root or humanoid.Health <= 0 then
		return nil
	end
	return root, humanoid
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

-- Ставит модель на землю: нижняя грань ровно на поверхности.
local function snapModelToGround(model, groundPosition)
	local extents = model:GetExtentsSize()
	local pivot = model:GetPivot()
	model:PivotTo(CFrame.new(Vector3.new(groundPosition.X, groundPosition.Y + extents.Y * 0.5 + 0.1, groundPosition.Z)))
	local box = model:GetBoundingBox()
	local bottom = box.Position.Y - box.Size.Y * 0.5
	local root = model.PrimaryPart or model:FindFirstChild("Root", true)
	local rootOffset = root and (root.Position.Y - bottom) or extents.Y * 0.5
	return rootOffset
end

-- Монстр ставится на реальный пол карты.
local function placeMonster(model, position, ignoreList)
	local spot = MapService.findFreeSpot(position, {
		radius = 2.6,
		height = 7,
		reach = 30,
		ignore = ignoreList,
	})
	if not spot then
		return nil
	end
	return snapModelToGround(model, spot)
end

function MonsterManager.spawnMonster(session, monsterId, position)
	if session.ended then
		return nil
	end
	local def = MonsterDefinitions.get(monsterId)
	if not def then
		return nil
	end
	local template = getTemplate(def.behavior)
	if not template then
		warn("[MonsterManager] нет шаблона для поведения " .. tostring(def.behavior))
		return nil
	end

	local model = template:Clone()
	model.Name = "Monster_" .. tostring(def.id) .. "_" .. tostring(#session.monsters + 1)
	tintModel(model, def.color or Color3.fromRGB(20, 20, 30), def.eyeColor or Color3.fromRGB(255, 50, 60), def.transparency)
	if (def.scale or 1) ~= 1 then
		pcall(function()
			model:ScaleTo(def.scale)
		end)
	end

	local root = model.PrimaryPart or model:FindFirstChild("Root", true)
	if not root then
		model:Destroy()
		return nil
	end

	-- Модель ещё не в Workspace: проверяем настоящую геометрию карты.
	local rootOffset = placeMonster(model, position, nil)
	if not rootOffset then
		model:Destroy()
		return nil
	end
	model.Parent = session.container

	local monster = {
		id = def.id,
		def = def,
		model = model,
		root = root,
		home = root.Position,
		state = "patrol",
		waypoints = {},
		waypointIndex = 1,
		lastRepath = 0,
		lastSeen = 0,
		attackCooldown = 1 + math.random(),
		announced = false,
		patrolIndex = math.random(1, 4),
		lastPosition = root.Position,
		rootOffset = rootOffset,
		stuckTime = 0,
		teleports = 0,
		chasing = false,
		visible = false,
	}

	table.insert(session.monsters, monster)
	CollectionService:AddTag(model, "Monster")
	return monster
end

local function patrolPoints(session)
	if session.patrolGround and #session.patrolGround > 0 then
		return session.patrolGround
	end
	local out = {}
	for _, marker in ipairs(MapService.collectMarkers(session.mapModel, "MK_Patrol")) do
		local spot = MapService.findFreeSpot(marker.Position, { radius = 2.5, height = 6, reach = 14 })
		if spot then
			table.insert(out, spot)
		end
	end
	session.patrolGround = out
	return out
end

local function spawnPoints(session)
	local out = {}
	for _, marker in ipairs(MapService.collectMarkers(session.mapModel, "MK_Monster")) do
		local spot = MapService.findFreeSpot(marker.Position, { radius = 3, height = 7, reach = 20 })
		if spot then
			table.insert(out, spot)
		end
	end
	for _, point in ipairs(patrolPoints(session)) do
		table.insert(out, point)
	end
	if #out == 0 then
		local origin = session.mapModel:GetAttribute("SpawnPosition") or Vector3.new(0, 6, 0)
		for _, offset in ipairs({ Vector3.new(40, 0, 40), Vector3.new(-40, 0, 40), Vector3.new(0, 0, 55) }) do
			local spot = MapService.findFreeSpot(origin + offset, { radius = 3, height = 7, reach = 30 })
			if spot then
				table.insert(out, spot)
			end
		end
	end
	return out
end

-- Точка спавна не ближе minDistance и не дальше maxDistance от игрока.
local function pickSpawnPoint(session, points, minDistance, maxDistance)
	local root = playerRoot(session)
	local playerPosition = root and root.Position or nil
	local candidates = {}
	for _, point in ipairs(points) do
		local distance = playerPosition and (point - playerPosition).Magnitude or math.huge
		if distance >= minDistance and distance <= maxDistance then
			table.insert(candidates, point)
		end
	end
	if #candidates == 0 then
		for _, point in ipairs(points) do
			local distance = playerPosition and (point - playerPosition).Magnitude or math.huge
			if distance >= minDistance then
				table.insert(candidates, point)
			end
		end
	end
	if #candidates == 0 then
		return nil
	end
	return candidates[math.random(1, #candidates)]
end

function MonsterManager.start(session)
	session.monsters = {}
	session.started = true
	local types = session.dream.monsters or { "shadow" }
	local count = session.difficulty.monsterCount or 3
	local points = spawnPoints(session)
	if #points == 0 then
		warn("[MonsterManager] на карте нет точек спавна монстров: " .. tostring(session.dream.id))
	end

	-- Монстры выходят постепенно: первый — через 12 секунд сна.
	for index = 1, count do
		task.delay(12 + (index - 1) * 6, function()
			if session.ended or not session.monsters then
				return
			end
			if #session.monsters >= count then
				return
			end
			local point = pickSpawnPoint(session, points, 55, 160) or pickSpawnPoint(session, points, 35, 400)
			if not point then
				return
			end
			local monsterId = types[math.random(1, #types)]
			local monster = MonsterManager.spawnMonster(session, monsterId, point)
			if monster then
				local alert = Remotes.get("MonsterAlert")
				if alert then
					alert:FireClient(session.player, { name = monster.def.name })
				end
				local scare = Remotes.get("Scare")
				if scare then
					scare:FireClient(session.player, { kind = "growl", monsterName = monster.def.name, intensity = 0.7 })
				end
			end
		end)
	end

	MonsterManager.runLoop(session)
end

-- ---------------------------------------------------------------
-- Движение
-- ---------------------------------------------------------------

local function raycastDown(position, ignoreModel)
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = { ignoreModel }
	params.IgnoreWater = true
	local hit = workspace:Raycast(position + Vector3.new(0, 16, 0), Vector3.new(0, -60, 0), params)
	if hit then
		return hit.Position
	end
	return nil
end

local function repath(session, monster, goal)
	local root = monster.root
	if not root or not root.Parent then
		return
	end
	local path = PathfindingService:CreatePath({
		AgentRadius = 2.4,
		AgentHeight = 6.5,
		AgentCanJump = true,
		AgentJumpHeight = 8,
		AgentMaxSlope = 45,
		WaypointSpacing = 7,
	})
	local ok = pcall(function()
		path:ComputeAsync(root.Position, goal)
	end)
	if ok and path.Status == Enum.PathStatus.Success then
		monster.waypoints = path:GetWaypoints()
		monster.waypointIndex = 2
	else
		monster.waypoints = { { Position = goal, Action = Enum.PathWaypointAction.Walk } }
		monster.waypointIndex = 1
	end
	monster.lastRepath = os.clock()
end

local function moveToward(monster, dt, targetPosition, speed)
	local root = monster.root
	local position = root.Position
	local delta = targetPosition - position
	local flat = Vector3.new(delta.X, 0, delta.Z)
	local distance = flat.Magnitude
	if distance < 0.4 then
		return true
	end
	local step = math.min(speed * dt, distance)
	local nextPosition = position + flat.Unit * step

	local ground = raycastDown(nextPosition, monster.model)
	if ground then
		nextPosition = Vector3.new(nextPosition.X, ground.Y + (monster.rootOffset or 3.2), nextPosition.Z)
	elseif delta.Y > 2 then
		nextPosition = Vector3.new(nextPosition.X, position.Y + math.min(step, delta.Y), nextPosition.Z)
	end

	local lookTarget = nextPosition + flat.Unit * 4
	pcall(function()
		monster.model:PivotTo(CFrame.lookAt(nextPosition, lookTarget))
	end)
	return distance <= step + 0.5
end

-- Аварийное спасение застрявшего монстра: ставим рядом с игроком, но вне поля зрения.
local function rescue(session, monster, root)
	if monster.teleports >= 4 then
		return
	end
	local target = root and root.Position or monster.home
	local angle = math.random() * math.pi * 2
	local distance = 45 + math.random() * 25
	local candidate = target + Vector3.new(math.cos(angle) * distance, 0, math.sin(angle) * distance)
	local spot = MapService.findFreeSpot(candidate, {
		radius = 2.6,
		height = 7,
		reach = 40,
		ignore = { monster.model },
	})
	if spot then
		monster.teleports += 1
		monster.rootOffset = snapModelToGround(monster.model, spot)
		monster.waypoints = {}
		monster.lastRepath = 0
		monster.stuckTime = 0
		monster.lastPosition = spot
	end
end

-- ---------------------------------------------------------------
-- Атака
-- ---------------------------------------------------------------

function MonsterManager.damagePlayer(session, monster)
	local root, humanoid = playerRoot(session)
	if not humanoid or humanoid.Health <= 0 then
		return
	end
	local difficulty = session.difficulty
	local bonuses = session.bonuses or {}
	local damage = (monster.def.damage or 15) * (difficulty.monsterDamage or 1)
	humanoid:TakeDamage(damage)
	session.sanity = math.max(0, (session.sanity or 100) - (monster.def.sanityDrain or 5) * 0.8)
	session.lastAttacker = monster.def.name

	local scare = Remotes.get("Scare")
	if scare then
		scare:FireClient(session.player, {
			kind = monster.def.boss and "boss" or "jumpscare",
			monsterName = monster.def.name,
			intensity = (monster.def.boss and 1.4 or 1) * math.clamp(difficulty.monsterDamage or 1, 0.5, 1.6) * (bonuses.scareMul or 1),
		})
	end
end

-- ---------------------------------------------------------------
-- Основной цикл
-- ---------------------------------------------------------------

local function updateMonster(session, monster, dt)
	local root = monster.root
	if not monster.model.Parent or not root then
		return
	end
	local now = os.clock()
	monster.attackCooldown = math.max(0, monster.attackCooldown - dt)
	local def = monster.def

	local targetRoot = playerRoot(session)
	if not targetRoot then
		return
	end
	local targetPosition = targetRoot.Position
	local monsterPosition = root.Position

	-- Упал за пределы карты / слишком далеко — возвращаем
	if monsterPosition.Y < -60 or (monsterPosition - session.mapModel:GetPivot().Position).Magnitude > 1200 then
		rescue(session, monster, targetRoot)
		return
	end

	local flat = Vector3.new(targetPosition.X - monsterPosition.X, 0, targetPosition.Z - monsterPosition.Z)
	local distance = flat.Magnitude

	-- Свет фонарика выдаёт игрока: монстры замечают дальше
	local lightBonus = session.flashlight and 1.45 or 1
	local detectRange = (def.detectRange or 70) * lightBonus

	-- Линия видимости
	local visible = false
	if distance <= detectRange then
		local params = RaycastParams.new()
		params.FilterType = Enum.RaycastFilterType.Exclude
		params.FilterDescendantsInstances = { monster.model, session.container:FindFirstChild("QuestObjects") }
		params.IgnoreWater = true
		local eye = monsterPosition + Vector3.new(0, 4.5, 0)
		local target = targetPosition + Vector3.new(0, 1.5, 0)
		local hit = workspace:Raycast(eye, target - eye, params)
		visible = hit == nil
	end
	monster.visible = visible
	if visible then
		monster.lastSeen = now
		if session.flashlight and distance < detectRange * 0.75 then
			monster.lastSeen = now
		end
	end

	local noise = (session.sprinting and 1.4 or 1) * (session.flashlight and 1.2 or 1)
	local heard = distance <= 26 * noise
	local chasing = visible or (now - monster.lastSeen < 4) or (heard and distance < 34)

	-- Наблюдатель: замирает, пока на него смотрят
	if def.behavior == "watcher" then
		local look = session.lookVector
		local watched = false
		if look and distance > 0.5 then
			local direction = Vector3.new(flat.X, 0, flat.Z).Unit
			local lookFlat = Vector3.new(look.X, 0, look.Z)
			if lookFlat.Magnitude > 0.1 then
				watched = lookFlat.Unit:Dot(direction) > 0.6
			end
		end
		if visible and watched then
			chasing = false
			session.sanity = math.max(0, session.sanity - 1.4 * dt)
		end
	end

	monster.chasing = chasing

	if chasing then
		if now - monster.lastRepath > 1.6 then
			repath(session, monster, targetPosition)
		end
		local waypoint = monster.waypoints[monster.waypointIndex]
		local goal = waypoint and waypoint.Position or targetPosition
		if moveToward(monster, dt, goal, (def.speed or 14) * (session.difficulty.monsterSpeed or 1) * 1.08) then
			monster.waypointIndex += 1
			if monster.waypointIndex > #monster.waypoints then
				monster.waypoints = {}
			end
		end
	else
		local points = patrolPoints(session)
		local goal = monster.home
		if #points > 0 then
			local point = points[((monster.patrolIndex - 1) % #points) + 1]
			if (monsterPosition - point).Magnitude < 10 then
				monster.patrolIndex += 1
				point = points[((monster.patrolIndex - 1) % #points) + 1]
			end
			goal = point
		end
		if now - monster.lastRepath > 4 then
			repath(session, monster, goal)
		end
		local waypoint = monster.waypoints[monster.waypointIndex]
		if waypoint then
			if moveToward(monster, dt, waypoint.Position, (def.speed or 12) * 0.45) then
				monster.waypointIndex += 1
			end
		else
			moveToward(monster, dt, goal, (def.speed or 12) * 0.45)
		end
	end

	-- Атака: только если монстр видит игрока (или вплотную) и прошёл общий кулдаун.
	local nowClock = os.clock()
	local canAttack = distance <= (def.attackRange or 6) + 1
		and monster.attackCooldown <= 0
		and (visible or distance < 4)
		and (session.lastHitAt == nil or nowClock - session.lastHitAt > 1.1)
	if canAttack then
		monster.attackCooldown = ATTACK_COOLDOWN
		session.lastHitAt = nowClock
		MonsterManager.damagePlayer(session, monster)
	end

	-- Первая встреча: предупреждение
	if not monster.announced and visible and distance < 45 then
		monster.announced = true
		local alert = Remotes.get("MonsterAlert")
		if alert then
			alert:FireClient(session.player, { name = def.name })
		end
	end

	-- Обнаружение застревания
	if chasing then
		local moved = (root.Position - monster.lastPosition).Magnitude
		if moved < 0.35 then
			monster.stuckTime += dt
		else
			monster.stuckTime = math.max(0, monster.stuckTime - dt)
			monster.lastPosition = root.Position
		end
		if monster.stuckTime > 1.6 then
			monster.stuckTime = 0
			monster.waypoints = {}
			monster.lastRepath = 0
			if math.random() < 0.5 then
				local waypoint = monster.waypoints[monster.waypointIndex]
				if waypoint then
					local ground = raycastDown(waypoint.Position, monster.model) or waypoint.Position
					monster.rootOffset = snapModelToGround(monster.model, ground)
				end
			else
				rescue(session, monster, targetRoot)
			end
		end
	end
end

function MonsterManager.runLoop(session)
	task.spawn(function()
		local heartbeat = 0
		while session.started and not session.ended and session.player.Parent do
			local dt = task.wait(0.1) or 0.1
			local monsters = session.monsters or {}
			for index = #monsters, 1, -1 do
				local monster = monsters[index]
				if not monster or not monster.model or not monster.model.Parent then
					table.remove(monsters, index)
				else
					local ok, err = pcall(updateMonster, session, monster, dt)
					if not ok then
						warn("[MonsterManager] ошибка ИИ: " .. tostring(err))
					end
				end
			end
			heartbeat += dt
			if heartbeat > 8 then
				heartbeat = 0
				local root = playerRoot(session)
				if root and #monsters > 0 then
					local nearest = math.huge
					for _, monster in ipairs(monsters) do
						nearest = math.min(nearest, (monster.root.Position - root.Position).Magnitude)
					end
					if nearest > 260 then
						-- Игрок ушёл далеко: подтягиваем одного монстра поближе.
						local monster = monsters[math.random(1, #monsters)]
						rescue(session, monster, root)
					end
				end
			end
		end
	end)
end

function MonsterManager.spawnBoss(session)
	if session.bossSpawned or session.ended then
		return
	end
	session.bossSpawned = true
	local bossId = session.dream.boss or "nightmare"
	local root = playerRoot(session)
	local basePosition = root and root.Position or (session.mapModel:GetAttribute("SpawnPosition") or Vector3.new(0, 6, 0))
	local angle = math.random() * math.pi * 2
	local monster = MonsterManager.spawnMonster(session, bossId, basePosition + Vector3.new(math.cos(angle) * 70, 0, math.sin(angle) * 70))
	if monster then
		monster.home = monster.root.Position
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

function MonsterManager.stop(session)
	session.started = false
	for _, monster in ipairs(session.monsters or {}) do
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
		if monster.model and monster.model.Parent and monster.root then
			local distance = (monster.root.Position - position).Magnitude
			if distance < best then
				best = distance
				nearest = monster
			end
		end
	end
	return nearest, best
end

function MonsterManager.update() end

return MonsterManager
