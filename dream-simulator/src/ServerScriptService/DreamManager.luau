--!nonstrict
-- DreamManager: полный цикл сна (меню → сон → кошмар → пробуждение).

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local CollectionService = game:GetService("CollectionService")
local Workspace = game:GetService("Workspace")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local DreamDefinitions = require(SharedModules:WaitForChild("DreamDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))
local Util = require(SharedModules:WaitForChild("Util"))

local DataManager = require(script.Parent:WaitForChild("DataManager"))
local MapService = require(script.Parent:WaitForChild("MapService"))
local QuestManager = require(script.Parent:WaitForChild("QuestManager"))
local MonsterManager = require(script.Parent:WaitForChild("MonsterManager"))
local NPCManager = require(script.Parent:WaitForChild("NPCManager"))

local DreamManager = {}

local sessions = {}
local states = {}

local EVENTS = {
	{ id = "rain", title = "ДОЖДЬ", description = "Вода стекает по стенам сна.", icon = "🌧", duration = 12 },
	{ id = "fog_boost", title = "ТУМАН", description = "Мир теряет очертания.", icon = "🌫", duration = 14, sanity = 2 },
	{ id = "red_moon", title = "КРОВАВАЯ ЛУНА", description = "Свет стал красным.", icon = "🌕", duration = 12 },
	{ id = "whispers", title = "ШЁПОТ", description = "Голоса повторяют твоё имя.", icon = "🗣", duration = 8, sanity = 6 },
	{ id = "scary_sound", title = "КРИК", description = "Крик раздался внутри головы.", icon = "📢", duration = 5, sanity = 5 },
	{ id = "footsteps", title = "ШАГИ", description = "Кто-то идёт за тобой.", icon = "👣", duration = 8, sanity = 3 },
	{ id = "flicker", title = "МЕРЦАНИЕ", description = "Свет боится тебя.", icon = "💡", duration = 10, sanity = 2 },
	{ id = "lights_out", title = "ТЕМНОТА", description = "Свет погас.", icon = "🕶", duration = 12, sanity = 4 },
	{ id = "sanity_aura", title = "ДАВЛЕНИЕ", description = "Сон сжимает виски.", icon = "🌀", duration = 10, sanity = 5 },
	{ id = "monster_rush", title = "РЫВОК", description = "Что-то пронеслось рядом.", icon = "💨", duration = 6, spawn = true },
	{ id = "monster_wave", title = "ВОЛНА", description = "Их стало больше.", icon = "🌊", duration = 6, spawn = true },
	{ id = "cold", title = "ХОЛОД", description = "Пальцы не слушаются.", icon = "❄️", duration = 8, stamina = 30 },
	{ id = "clock", title = "УКРАДЕННОЕ ВРЕМЯ", description = "Часы сделали шаг назад.", icon = "⏰", duration = 6, time = 12 },
	{ id = "darkness_pulse", title = "ПУЛЬС", description = "Тьма дышит в такт с тобой.", icon = "🖤", duration = 10, sanity = 4 },
	{ id = "scream_wave", title = "ХОР", description = "Сотни голосов смеются.", icon = "😱", duration = 7, sanity = 7 },
	{ id = "calm", title = "ЗАТИШЬЕ", description = "На миг стало спокойно.", icon = "🕯", duration = 12, sanity = -6 },
}

local function setState(player, state)
	states[player] = state
	local remote = Remotes.get("DreamStateChanged")
	if remote then
		remote:FireClient(player, { state = state })
	end
end

function DreamManager.getState(player)
	return states[player] or "menu"
end

function DreamManager.getSession(player)
	return sessions[player]
end

function DreamManager.isBusy(player)
	local state = states[player]
	return state == "sleeping" or state == "dream"
end

local function getHumanoid(player)
	local character = player.Character
	return character and character:FindFirstChildOfClass("Humanoid")
end

function DreamManager.menuSpawn()
	local bedroom = Workspace:FindFirstChild("MenuBedroom")
	if bedroom then
		local marker = MapService.findMarker(bedroom, "MenuSpawn")
		if marker then
			return marker.Position + Vector3.new(0, 4, 0)
		end
	end
	return Vector3.new(0, 6, 0)
end

function DreamManager.teleportToMenu(player)
	local character = player.Character
	if not character then
		return
	end
	character:PivotTo(CFrame.new(DreamManager.menuSpawn()))
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		humanoid.WalkSpeed = Config.BaseWalkSpeed
		humanoid.JumpPower = Config.JumpPower
		humanoid.UseJumpPower = true
		humanoid.Health = humanoid.MaxHealth
	end
	character:SetAttribute("InDream", false)
	local backpack = player:FindFirstChildOfClass("Backpack")
	if backpack then
		backpack:ClearAllChildren()
	end
end

function DreamManager.preparePlayer(player)
	local character = player.Character or player.CharacterAdded:Wait()
	local humanoid = character:WaitForChild("Humanoid", 10)
	if humanoid then
		humanoid.MaxHealth = Config.MaxHealth
		humanoid.Health = Config.MaxHealth
		humanoid.WalkSpeed = Config.BaseWalkSpeed
		humanoid.JumpPower = Config.JumpPower
		humanoid.UseJumpPower = true
	end
	DreamManager.teleportToMenu(player)

	local EconomyManager = script.Parent:FindFirstChild("EconomyManager")
	if EconomyManager then
		require(EconomyManager).applyCosmetics(player)
	end
end

local function newSession(player, dreamDef, difficulty, bonuses)
	local container = Instance.new("Folder")
	container.Name = ("Dream_%s_%d"):format(player.Name, os.time())
	container.Parent = Workspace:FindFirstChild("ActiveDreams") or Workspace

	local session = {
		player = player,
		dream = dreamDef,
		difficulty = difficulty,
		bonuses = bonuses or {},
		container = container,
		rng = Random.new(),
		monsters = {},
		npcs = {},
		quests = {},
		questStates = {},
		sanity = Config.MaxSanity,
		stamina = Config.MaxStamina,
		timeLeft = difficulty.time + (bonuses and bonuses.timeBonus or 0),
		totalTime = difficulty.time + (bonuses and bonuses.timeBonus or 0),
		elapsed = 0,
		ended = false,
		sprinting = false,
		flashlight = false,
		promptIds = {},
		promptParts = {},
		promptSerial = 0,
		lookVector = nil,
		coinsEarned = 0,
		questsDone = 0,
		bonusXp = 0,
	}

	session.mapModel = MapService.buildDream(dreamDef, container)
	if not session.mapModel then
		container:Destroy()
		return nil
	end
	session.patrolPoints = MapService.collectMarkers(session.mapModel, "MK_Patrol")
	return session
end

function DreamManager.startDream(player, difficultyKey, dreamId)
	if DreamManager.isBusy(player) then
		return false, "Сон уже идёт"
	end
	-- Повторный сон с экрана результата: убираем прошлую сессию.
	local previous = sessions[player]
	if previous then
		sessions[player] = nil
		MonsterManager.stop(previous)
		NPCManager.cleanup(previous)
		QuestManager.cleanup(previous)
		if previous.container and previous.container.Parent then
			previous.container:Destroy()
		end
	end
	if type(difficultyKey) ~= "string" or not Config.Difficulties[difficultyKey] then
		difficultyKey = "Normal"
	end
	local difficulty = Config.getDifficulty(difficultyKey)
	local profile = DataManager.get(player)
	profile.difficulty = difficultyKey

	local chosenId = dreamId
	if type(chosenId) ~= "string" or not DreamDefinitions.ById[chosenId] then
		chosenId = DreamDefinitions.random(profile.lastDream)
	end
	local dreamDef = DreamDefinitions.get(chosenId)

	local EconomyManager = script.Parent:FindFirstChild("EconomyManager")
	local bonuses = EconomyManager and require(EconomyManager).getBonuses(player) or {}

	setState(player, "sleeping")
	local humanoid = getHumanoid(player)
	if humanoid then
		humanoid.WalkSpeed = 0
		humanoid.JumpPower = 0
		humanoid.Health = humanoid.MaxHealth
	end

	local sleepRemote = Remotes.get("SleepSequence")
	if sleepRemote then
		sleepRemote:FireClient(player, {
			dreamName = dreamDef.name or dreamDef.title,
			dreamId = dreamDef.id,
			subtitle = dreamDef.subtitle,
			icon = dreamDef.icon,
			desc = dreamDef.desc,
			difficultyName = difficulty.name,
			difficulty = difficultyKey,
			duration = Config.SleepDuration,
		})
	end

	task.wait(Config.SleepDuration)

	if not player.Parent then
		return false, "Игрок вышел"
	end

	local session = newSession(player, dreamDef, difficulty, bonuses)
	if not session then
		setState(player, "menu")
		return false, "Не удалось построить сон"
	end
	sessions[player] = session

	profile.lastDream = dreamDef.id
	DataManager.markDreamVisited(player, dreamDef.id)
	DataManager.updateStat(player, "dreams", 1)

	local spawnPosition = MapService.spawnPosition(session.mapModel, Vector3.new(0, 8, 0))
	local character = player.Character
	if character then
		character:PivotTo(CFrame.new(spawnPosition))
		local hum = character:FindFirstChildOfClass("Humanoid")
		if hum then
			hum.Health = hum.MaxHealth
			hum.WalkSpeed = Config.BaseWalkSpeed + (bonuses.speedBonus or 0)
			hum.JumpPower = Config.JumpPower
		end
		character:SetAttribute("InDream", true)
	end

	QuestManager.startSession(session)
	NPCManager.spawnNPCs(session)
	MonsterManager.start(session)
	setState(player, "dream")

	local AchievementManager = script.Parent:FindFirstChild("AchievementManager")
	if AchievementManager then
		require(AchievementManager).check(player, "dream")
	end

	local startedRemote = Remotes.get("DreamStarted")
	if startedRemote then
		startedRemote:FireClient(player, {
			dreamId = dreamDef.id,
			dreamName = dreamDef.name or dreamDef.title,
			subtitle = dreamDef.subtitle,
			icon = dreamDef.icon,
			difficulty = difficultyKey,
			difficultyName = difficulty.name,
			duration = session.totalTime,
			bonuses = bonuses,
			settings = profile.settings,
		})
	end

	local timerRemote = Remotes.get("TimerUpdate")
	if timerRemote then
		timerRemote:FireClient(player, { remaining = session.timeLeft, total = session.totalTime })
	end
	DreamManager.sendVitals(session)
	DreamManager.startLoop(session)
	return true
end

function DreamManager.sendVitals(session)
	local character = session.player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	local remote = Remotes.get("VitalsUpdate")
	if remote then
		remote:FireClient(session.player, {
			health = humanoid and humanoid.Health or 0,
			maxHealth = humanoid and humanoid.MaxHealth or Config.MaxHealth,
			sanity = session.sanity,
			maxSanity = Config.MaxSanity,
			stamina = session.stamina,
			maxStamina = Config.MaxStamina,
			sprinting = session.sprinting,
			flashlight = session.flashlight,
		})
	end
end

function DreamManager.startLoop(session)
	local player = session.player
	local difficulty = session.difficulty
	local lastSecond = math.floor(session.timeLeft)
	local lastEvent = 0
	local eventInterval = difficulty.eventInterval or 30
	local vitalsTimer = 0
	local promptTimer = 0
	local lastPromptPart = nil

	task.spawn(function()
		while not session.ended and player.Parent do
			local dt = task.wait(0.1) or 0.1
			if session.ended then
				break
			end
			session.elapsed += dt

			local character = player.Character
			local humanoid = character and character:FindFirstChildOfClass("Humanoid")
			local root = character and character:FindFirstChild("HumanoidRootPart")

			-- Таймер
			session.timeLeft = math.max(0, session.timeLeft - dt)
			local currentSecond = math.floor(session.timeLeft)
			if currentSecond ~= lastSecond then
				lastSecond = currentSecond
				local timerRemote = Remotes.get("TimerUpdate")
				if timerRemote then
					timerRemote:FireClient(player, { remaining = session.timeLeft, total = session.totalTime })
				end
			end

			-- Выносливость и скорость
			if session.sprinting and session.stamina > 0 then
				session.stamina = math.max(0, session.stamina - Config.SprintDrain * dt)
				if humanoid then
					humanoid.WalkSpeed = Config.SprintSpeed + (session.bonuses.speedBonus or 0)
				end
				if session.stamina <= 0 then
					session.sprinting = false
				end
			else
				session.stamina = math.min(Config.MaxStamina, session.stamina + Config.StaminaRegen * dt)
				if humanoid then
					humanoid.WalkSpeed = Config.BaseWalkSpeed + (session.bonuses.speedBonus or 0)
				end
			end

			-- Рассудок
			local drain = 0
			if root then
				local nearest, distance = MonsterManager.getNearest(session, root.Position)
				if nearest and distance < 45 then
					drain += Config.Sanity.DrainMonster * (1 - distance / 45)
				end
				if session.flashlight then
					session.sanity = math.min(Config.MaxSanity, session.sanity + Config.Sanity.RegenLight * dt)
				else
					drain += Config.Sanity.DrainDark
				end
				if session.sprinting then
					drain += 0.3
				end
			end
			local sanityMul = (session.bonuses.sanityMul or 1)
			session.sanity = math.max(0, session.sanity - drain * difficulty.sanityDrain * sanityMul * dt)

			if session.sanity <= 0 and humanoid and humanoid.Health > 0 then
				humanoid:TakeDamage(4.5 * dt)
			elseif session.sanity < Config.Sanity.Critical and session.elapsed > 45 and not session.bossSpawned then
				MonsterManager.spawnBoss(session)
			end

			-- Монстры
			for _, monster in ipairs(session.monsters) do
				MonsterManager.update(session, monster, dt)
			end

			-- Задания и цель компаса
			QuestManager.tick(session, dt)

			-- События
			if session.elapsed - lastEvent > eventInterval then
				lastEvent = session.elapsed
				DreamManager.triggerEvent(session)
			end

			-- Подсказки взаимодействия и подбор предметов
			promptTimer += dt
			if promptTimer >= 0.25 and root then
				promptTimer = 0
				local part = DreamManager.findPromptPart(session, root.Position)
				if part ~= lastPromptPart then
					lastPromptPart = part
					local promptRemote = Remotes.get("PromptUpdate")
					if promptRemote then
						if part then
							promptRemote:FireClient(player, { id = DreamManager.promptId(session, part), text = part:GetAttribute("Prompt") or "Взаимодействовать" })
						else
							promptRemote:FireClient(player, nil)
						end
					end
				end
			end

			-- Синхронизация витальных показателей
			vitalsTimer += dt
			if vitalsTimer >= 0.5 then
				vitalsTimer = 0
				DreamManager.sendVitals(session)
			end

			-- Проверка завершения
			if session.timeLeft <= 0 then
				local done = session.mandatoryQuest and session.mandatoryQuest.state == "done"
				if done then
					DreamManager.endDream(player, true, "Ты успел выбраться до пробуждения")
				else
					DreamManager.endDream(player, false, "Время вышло — сон поглотил тебя")
				end
				break
			end
			if humanoid and humanoid.Health <= 0 then
				DreamManager.endDream(player, false, "Кошмар настиг тебя")
				break
			end
		end
	end)
end

function DreamManager.promptId(session, part)
	local existing = session.promptIds[part]
	if existing then
		return existing
	end
	session.promptSerial += 1
	local id = "obj_" .. session.promptSerial
	session.promptIds[part] = id
	session.promptParts[id] = part
	return id
end

function DreamManager.findPromptPart(session, position)
	local best = nil
	local bestDistance = Config.InteractRange
	for _, part in ipairs(CollectionService:GetTagged("Interactable")) do
		if part:IsA("BasePart") and part:IsDescendantOf(session.container) and part.Parent then
			local distance = (part.Position - position).Magnitude
			if distance < bestDistance then
				bestDistance = distance
				best = part
			end
		end
	end
	return best
end

function DreamManager.triggerEvent(session)
	if session.ended then
		return
	end
	local event = EVENTS[math.random(1, #EVENTS)]
	local remote = Remotes.get("EventStarted")
	if remote then
		remote:FireClient(session.player, {
			id = event.id,
			title = event.title,
			description = event.description,
			icon = event.icon,
			duration = event.duration or 8,
		})
	end
	if event.sanity then
		if event.sanity > 0 then
			session.sanity = math.max(0, session.sanity - event.sanity)
		else
			session.sanity = math.min(Config.MaxSanity, session.sanity - event.sanity)
		end
	end
	if event.stamina then
		session.stamina = math.max(0, session.stamina - event.stamina)
	end
	if event.time then
		session.timeLeft = math.max(1, session.timeLeft - event.time)
	end
	if event.spawn then
		local root = session.player.Character and session.player.Character:FindFirstChild("HumanoidRootPart")
		if root then
			local types = session.dream.monsters or { "shadow" }
			MonsterManager.spawnMonster(
				session,
				types[math.random(1, #types)],
				root.Position + Vector3.new(math.random(-32, 32), 3, math.random(-32, 32))
			)
		end
	end
end

function DreamManager.handleInteract(player, promptId)
	local session = sessions[player]
	if not session or session.ended then
		return
	end
	local part = session.promptParts[promptId]
	if not part or not part.Parent then
		return
	end
	local character = player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not root then
		return
	end
	if (root.Position - part.Position).Magnitude > Config.InteractRange * 1.8 then
		return
	end

	local kind = part:GetAttribute("InteractKind")
	local label = part:GetAttribute("QuestLabel")

	if kind == "collect" then
		CollectionService:RemoveTag(part, "Interactable")
		session.promptIds[part] = nil
		session.promptParts[promptId] = nil
		part:Destroy()
		QuestManager.progress(session, "collect", label, 1)
	elseif kind == "object" or kind == "lamp" then
		if part:GetAttribute("Used") then
			return
		end
		part:SetAttribute("Used", true)
		if kind == "lamp" then
			part.Material = Enum.Material.Neon
			part.Color = Color3.fromRGB(255, 240, 190)
			local bulb = part:FindFirstChild("Bulb")
			if bulb then
				bulb.Enabled = true
				bulb.Brightness = 3
			end
			QuestManager.progress(session, "lights", label, 1)
		else
			part.Color = Color3.fromRGB(120, 240, 160)
			local light = part:FindFirstChildOfClass("PointLight")
			if not light then
				light = Instance.new("PointLight")
				light.Color = Color3.fromRGB(140, 255, 180)
				light.Brightness = 2
				light.Range = 18
				light.Parent = part
			end
			QuestManager.progress(session, "interact", label, 1)
		end
	elseif kind == "npc" then
		NPCManager.openDialog(session, part)
	elseif kind == "portal" then
		if not session.portalOpen then
			local hint = Remotes.get("Hint")
			if hint then
				hint:FireClient(player, "Портал ещё не открыт. Выполни основное задание.")
			end
			return
		end
		DreamManager.endDream(player, true, "Ты вошёл в портал пробуждения")
	end
end

function DreamManager.handleDialogChoice(player, action)
	local session = sessions[player]
	if not session then
		return
	end
	NPCManager.handleChoice(session, action)
end

function DreamManager.setSprinting(player, value)
	local session = sessions[player]
	if not session or session.ended then
		return
	end
	session.sprinting = value == true and session.stamina > 1
end

function DreamManager.setFlashlight(player, value)
	local session = sessions[player]
	if not session or session.ended then
		return
	end
	session.flashlight = value == true
end

function DreamManager.setLook(player, lookVector)
	local session = sessions[player]
	if not session or session.ended then
		return
	end
	if typeof(lookVector) == "Vector3" then
		session.lookVector = lookVector
	end
end

function DreamManager.applyConsumable(player, item)
	local session = sessions[player]
	if not session or session.ended then
		return false
	end
	local effect = item.consumable or {}
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	if effect.sanity then
		session.sanity = math.min(Config.MaxSanity, session.sanity + effect.sanity)
	end
	if effect.health and humanoid then
		humanoid.Health = math.min(humanoid.MaxHealth, humanoid.Health + effect.health)
	end
	if effect.stamina then
		session.stamina = math.min(Config.MaxStamina, session.stamina + effect.stamina)
	end
	if effect.flashlight then
		session.flashlight = true
	end
	if effect.speed and humanoid then
		local base = Config.BaseWalkSpeed + (session.bonuses.speedBonus or 0)
		humanoid.WalkSpeed = base * 1.4
		task.delay(effect.speed, function()
			if humanoid and humanoid.Parent and not session.ended then
				humanoid.WalkSpeed = base
			end
		end)
	end
	DreamManager.sendVitals(session)
	return true
end

function DreamManager.endDream(player, won, reasonText)
	local session = sessions[player]
	if not session or session.ended then
		return
	end
	session.ended = true
	local difficultyKey = session.difficulty.key
	local survived = math.floor(session.elapsed)

	setState(player, "waking")
	MonsterManager.stop(session)
	NPCManager.cleanup(session)

	local questCoins = session.coinsEarned or 0
	local base = session.difficulty.reward or 100
	local coins = math.floor(questCoins + (won and base or base * 0.25))

	DataManager.addCoins(player, coins)
	DataManager.updateStat(player, "wins", won and 1 or 0)
	DataManager.updateStat(player, "losses", won and 0 or 1)
	DataManager.updateStat(player, "playtime", survived)
	local profile = DataManager.get(player)
	if won and (profile.stats.bestTime <= 0 or survived < profile.stats.bestTime) then
		profile.stats.bestTime = survived
	end

	local humanoid = getHumanoid(player)
	local AchievementManager = script.Parent:FindFirstChild("AchievementManager")
	if AchievementManager then
		require(AchievementManager).check(player, "end", {
			win = won,
			difficulty = difficultyKey,
			survived = survived,
			health = humanoid and humanoid.Health or 0,
		})
	end
	DataManager.save(player)

	local payload = {
		won = won,
		reasonText = reasonText or "",
		dreamName = session.dream.name or session.dream.title,
		dreamId = session.dream.id,
		difficulty = difficultyKey,
		difficultyName = session.difficulty.name,
		survived = survived,
		questsDone = session.questsDone or 0,
		questsTotal = #session.quests,
		coins = coins,
	}
	local endRemote = Remotes.get("DreamEnded")
	if endRemote then
		endRemote:FireClient(player, payload)
	end

	-- Экран результата остаётся у игрока; мир сна убираем, персонажа возвращаем в спальню.
	task.delay(Config.WakeDuration, function()
		if sessions[player] == session then
			sessions[player] = nil
		end
		MonsterManager.stop(session)
		NPCManager.cleanup(session)
		QuestManager.cleanup(session)
		if session.container and session.container.Parent then
			session.container:Destroy()
		end
		MapService.resetLighting()
		if player.Parent and states[player] ~= "menu" then
			DreamManager.teleportToMenu(player)
		end
	end)
end

function DreamManager.returnToMenu(player)
	local session = sessions[player]
	if session and not session.ended then
		DreamManager.endDream(player, false, "Ты сдался и проснулся")
		return
	end
	if states[player] == "waking" then
		return
	end
	sessions[player] = nil
	setState(player, "menu")
	DreamManager.teleportToMenu(player)
	DataManager.pushProfile(player)
end

function DreamManager.cleanupPlayer(player)
	local session = sessions[player]
	if session then
		session.ended = true
		MonsterManager.stop(session)
		NPCManager.cleanup(session)
		QuestManager.cleanup(session)
		if session.container and session.container.Parent then
			session.container:Destroy()
		end
	end
	sessions[player] = nil
	states[player] = nil
	MapService.resetLighting()
end

function DreamManager.init()
	MapService.captureLighting()
	Players.PlayerRemoving:Connect(function(player)
		DreamManager.cleanupPlayer(player)
	end)
end

return DreamManager
