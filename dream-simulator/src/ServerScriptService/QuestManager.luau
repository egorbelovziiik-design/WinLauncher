--!nonstrict
-- QuestManager: задания, объекты, прогресс, портал пробуждения.

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))
local QuestDefinitions = require(SharedModules:WaitForChild("QuestDefinitions"))
local Remotes = require(SharedModules:WaitForChild("Remotes"))
local Util = require(SharedModules:WaitForChild("Util"))

local MapService = require(script.Parent:WaitForChild("MapService"))
local DataManager = require(script.Parent:WaitForChild("DataManager"))

local QuestManager = {}

local function newPart(props)
	local part = Instance.new("Part")
	part.Anchored = true
	part.CanCollide = false
	part.CanQuery = false
	part.CanTouch = false
	part.TopSurface = Enum.SurfaceType.Smooth
	part.BottomSurface = Enum.SurfaceType.Smooth
	part.Material = Enum.Material.Neon
	for key, value in pairs(props) do
		part[key] = value
	end
	return part
end

local function makeBillboard(part, text, color)
	local gui = Instance.new("BillboardGui")
	gui.Name = "Label"
	gui.Size = UDim2.fromOffset(210, 44)
	gui.StudsOffset = Vector3.new(0, 2.4, 0)
	gui.AlwaysOnTop = true
	gui.MaxDistance = 70
	gui.Adornee = part
	gui.Parent = part

	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.Text = text
	label.TextColor3 = color or Color3.fromRGB(235, 235, 255)
	label.TextStrokeTransparency = 0.4
	label.TextScaled = true
	label.Font = Enum.Font.GothamBold
	label.Parent = gui
	return gui
end

local function objectiveText(quest)
	if quest.kind == "collect" then
		return "Найти: " .. (quest.label or quest.title)
	elseif quest.kind == "interact" then
		return "Активировать: " .. (quest.label or quest.title)
	elseif quest.kind == "lights" then
		return "Включить: " .. (quest.label or "свет")
	elseif quest.kind == "talk" then
		return "Поговорить с NPC"
	elseif quest.kind == "reach" then
		return "Добраться до цели"
	elseif quest.kind == "survive" then
		return "Продержаться"
	end
	return quest.title
end

function QuestManager.startSession(session)
	session.quests = {}
	session.questStates = {}
	local dreamDef = session.dream
	local mandatoryDef, optionalDefs = QuestDefinitions.forDream(dreamDef.id, dreamDef)

	local function makeState(def, optional, state, giver)
		local questState = {
			def = def,
			optional = optional,
			state = state or "active",
			progress = {},
			done = false,
			spawned = false,
			target = nil,
			giver = giver,
		}
		table.insert(session.quests, questState)
		session.questStates[def.id] = questState
		return questState
	end

	if mandatoryDef then
		session.mandatoryQuest = makeState(mandatoryDef, false, "active", nil)
	else
		session.mandatoryQuest = nil
	end

	local rng = session.rng or Random.new()
	local pool = {}
	for _, def in ipairs(optionalDefs) do
		table.insert(pool, def)
	end
	Util.shuffle(pool, rng)
	local take = math.min(3, #pool)
	local npcNames = {}
	for _, npc in ipairs(dreamDef.npcs or {}) do
		table.insert(npcNames, npc.name)
	end
	for index = 1, take do
		local giver = npcNames[((index - 1) % math.max(1, #npcNames)) + 1] or "Странник"
		makeState(pool[index], true, "offered", giver)
	end

	session.portalOpen = false
	session.questsDone = 0
	session.coinsEarned = 0

	QuestManager.spawnPortal(session)
	QuestManager.spawnObjectsForActive(session)
	QuestManager.sendUpdate(session)
end

function QuestManager.ensureObjectsFolder(session)
	if session.objects and session.objects.Parent then
		return session.objects
	end
	local objects = Instance.new("Folder")
	objects.Name = "QuestObjects"
	objects.Parent = session.container
	session.objects = objects
	return objects
end

function QuestManager.spawnObjectsForActive(session)
	local objects = QuestManager.ensureObjectsFolder(session)
	local model = session.mapModel
	local rng = session.rng or Random.new()
	local itemMarkers = MapService.collectMarkers(model, "MK_Item")
	local interactMarkers = MapService.collectMarkers(model, "MK_Interact")
	local lampMarkers = MapService.collectMarkers(model, "MK_Lamp")
	local reachMarkers = MapService.collectMarkers(model, "MK_Reach")

	session.markerCursor = session.markerCursor or { item = 1, interact = 1, lamp = 1, reach = 1 }

	local function nextMarker(markers, key)
		if #markers == 0 then
			return nil
		end
		local cursor = session.markerCursor[key]
		local marker = markers[((cursor - 1) % #markers) + 1]
		session.markerCursor[key] = cursor + 1
		return marker
	end

	for _, questState in ipairs(session.quests) do
		if questState.state == "active" and not questState.spawned then
			questState.spawned = true
			local def = questState.def

			if def.kind == "collect" then
				for i = 1, def.need do
					local marker = nextMarker(itemMarkers, "item")
					local basePosition = (marker and marker.Position or Vector3.new(i * 8, 3, 0))
						+ Vector3.new(rng:NextNumber(-4, 4), 0, rng:NextNumber(-4, 4))
					local position = MapService.findFreeSpot(basePosition, { radius = 2, height = 4, reach = 16 })
						or (basePosition + Vector3.new(0, 3, 0))
					local part = newPart({
						Name = "Collectible",
						Size = Vector3.new(1.7, 1.7, 1.7),
						Shape = Enum.PartType.Ball,
						Position = position,
						Color = Color3.fromRGB(150, 210, 255),
						Transparency = 0.1,
					})
					part:SetAttribute("InteractKind", "collect")
					part:SetAttribute("QuestId", def.id)
					part:SetAttribute("QuestLabel", def.label or def.title)
					part:SetAttribute("Prompt", "Подобрать: " .. (def.label or def.title))
					part.Parent = objects
					CollectionService:AddTag(part, "Interactable")
					makeBillboard(part, def.label or def.title, Color3.fromRGB(180, 230, 255))
					local light = Instance.new("PointLight")
					light.Color = Color3.fromRGB(150, 210, 255)
					light.Brightness = 1.6
					light.Range = 14
					light.Parent = part
					if not questState.target then
						questState.target = position
					end
				end
			elseif def.kind == "interact" or def.kind == "lights" then
				local markers = def.kind == "lights" and lampMarkers or interactMarkers
				local key = def.kind == "lights" and "lamp" or "interact"
				for i = 1, def.need do
					local marker = nextMarker(markers, key)
					local rawPosition = marker and marker.Position or Vector3.new(i * 12, 3, 0)
					local isLamp = def.kind == "lights"
					local position = MapService.findFreeSpot(rawPosition, { radius = 2.4, height = isLamp and 6 or 4, reach = 16 })
						or rawPosition
					local part = newPart({
						Name = isLamp and "Lamp" or "QuestObject",
						Size = isLamp and Vector3.new(1.6, 4.2, 1.6) or Vector3.new(3.2, 3.2, 3.2),
						Position = position + Vector3.new(0, isLamp and 3 or 2.2, 0),
						Color = isLamp and Color3.fromRGB(70, 72, 84) or Color3.fromRGB(120, 100, 200),
						Material = Enum.Material.Metal,
						Transparency = 0,
					})
					part:SetAttribute("InteractKind", isLamp and "lamp" or "object")
					part:SetAttribute("QuestId", def.id)
					part:SetAttribute("QuestLabel", def.label or def.title)
					part:SetAttribute("Prompt", (isLamp and "Включить: " or "Активировать: ") .. (def.label or def.title))
					part.Parent = objects
					CollectionService:AddTag(part, "Interactable")
					makeBillboard(part, def.label or def.title, isLamp and Color3.fromRGB(255, 240, 170) or Color3.fromRGB(200, 190, 255))

					if isLamp then
						local bulb = Instance.new("PointLight")
						bulb.Name = "Bulb"
						bulb.Color = Color3.fromRGB(255, 235, 180)
						bulb.Brightness = 0
						bulb.Range = 46
						bulb.Shadows = true
						bulb.Enabled = false
						bulb.Parent = part
					else
						local strip = newPart({
							Name = "Strip",
							Size = Vector3.new(3.4, 0.3, 3.4),
							Position = part.Position + Vector3.new(0, 1.7, 0),
							Color = Color3.fromRGB(200, 120, 255),
							Transparency = 0.1,
						})
						strip.Parent = part
					end
					if not questState.target then
						questState.target = part.Position
					end
				end
			elseif def.kind == "reach" then
				local marker = nextMarker(reachMarkers, "reach")
				local rawPosition = marker and marker.Position or Vector3.new(60, 3, 60)
				local position = MapService.findFreeSpot(rawPosition, { radius = 4, height = 6, reach = 22 }) or rawPosition
				local zone = newPart({
					Name = "ReachZone",
					Size = Vector3.new(16, 12, 16),
					Position = position + Vector3.new(0, 4, 0),
					Color = Color3.fromRGB(120, 220, 255),
					Transparency = 0.82,
					Material = Enum.Material.ForceField,
				})
				zone:SetAttribute("InteractKind", "zone")
				zone:SetAttribute("QuestId", def.id)
				zone:SetAttribute("QuestLabel", def.title)
				zone:SetAttribute("Prompt", "Зона: " .. def.title)
				zone.Parent = objects
				CollectionService:AddTag(zone, "Interactable")
				makeBillboard(zone, def.title, Color3.fromRGB(160, 225, 255))
				questState.zonePart = zone
				questState.target = position
			elseif def.kind == "talk" then
				-- цель — ближайший NPC, прогресс идёт через диалог
				local npcs = MapService.collectMarkers(model, "MK_NPC")
				if #npcs > 0 then
					questState.target = npcs[1].Position
				end
			end
		end
	end
end

function QuestManager.spawnPortal(session)
	if session.portal and session.portal.Parent then
		return
	end
	local model = session.mapModel
	local marker = MapService.findMarker(model, "MK_Portal")
	local rawPosition = marker and marker.Position or Vector3.new(0, 6, 0)
	local position = MapService.findFreeSpot(rawPosition, { radius = 5, height = 6, reach = 26 }) or rawPosition

	local portal = Instance.new("Model")
	portal.Name = "ExitPortal"
	portal.Parent = session.container

	local ring = newPart({
		Name = "PortalRing",
		Size = Vector3.new(14, 14, 1),
		Position = position + Vector3.new(0, 7, 0),
		Color = Color3.fromRGB(150, 110, 255),
		Material = Enum.Material.Neon,
		Transparency = 1,
	})
	ring.Parent = portal

	local center = newPart({
		Name = "PortalCore",
		Size = Vector3.new(12, 12, 0.4),
		Position = ring.Position,
		Color = Color3.fromRGB(60, 40, 120),
		Transparency = 1,
	})
	center.Parent = portal

	local trigger = newPart({
		Name = "PortalTrigger",
		Size = Vector3.new(10, 12, 4),
		Position = ring.Position,
		Transparency = 1,
	})
	trigger:SetAttribute("InteractKind", "portal")
	trigger:SetAttribute("Prompt", "Войти в портал пробуждения")
	trigger.Parent = portal
	CollectionService:AddTag(trigger, "Interactable")

	local light = Instance.new("PointLight")
	light.Color = Color3.fromRGB(150, 110, 255)
	light.Brightness = 0
	light.Range = 40
	light.Parent = ring

	local emitter = Instance.new("ParticleEmitter")
	emitter.Texture = "rbxasset://textures/particles/sparkles_main.dds"
	emitter.Rate = 0
	emitter.Lifetime = NumberRange.new(1, 2)
	emitter.Speed = NumberRange.new(1, 3)
	emitter.Size = NumberSequence.new(0.45)
	emitter.Color = ColorSequence.new(Color3.fromRGB(170, 140, 255))
	emitter.Parent = ring

	portal.PrimaryPart = ring
	session.portal = portal
	session.portalPosition = ring.Position
	session.portalRing = ring
	session.portalLight = light
	session.portalEmitter = emitter
	session.portalTrigger = trigger
end

function QuestManager.sendUpdate(session)
	local payload = { quests = {}, mandatoryLeft = 0 }
	for _, questState in ipairs(session.quests) do
		local def = questState.def
		local current = 0
		if def.kind == "talk" then
			current = questState.progress.talk or 0
		elseif def.kind == "reach" then
			current = questState.reached and 1 or 0
		else
			current = math.floor(questState.progress.total or 0)
		end
		if questState.state == "active" and current > def.need then
			current = def.need
		end
		local objectives = {
			{
				text = objectiveText(def),
				current = current,
				goal = def.need,
				done = questState.state == "done" or current >= def.need,
			},
		}
		table.insert(payload.quests, {
			id = def.id,
			title = def.title,
			desc = def.desc,
			state = questState.state == "done" and "done" or questState.state,
			mandatory = not questState.optional,
			optional = questState.optional,
			giver = questState.giver,
			reward = def.reward,
			target = questState.target,
			objectives = objectives,
		})
		if not questState.optional and questState.state ~= "done" then
			payload.mandatoryLeft += 1
		end
	end
	local remote = Remotes.get("QuestUpdate")
	if remote then
		remote:FireClient(session.player, payload)
	end
end

function QuestManager.acceptQuest(session, questId)
	local questState = session.questStates and session.questStates[questId]
	if not questState or questState.state ~= "offered" then
		return false
	end
	questState.state = "active"
	QuestManager.spawnObjectsForActive(session)
	QuestManager.sendUpdate(session)
	local remote = Remotes.get("Toast")
	if remote then
		remote:FireClient(session.player, {
			text = ("Взято задание: %s"):format(questState.def.title),
			color = Config.Colors.accent,
			icon = "📜",
		})
	end
	return true
end

local function completeQuest(session, questState)
	questState.state = "done"
	questState.done = true
	session.coinsEarned = (session.coinsEarned or 0) + questState.def.reward
	session.questsDone = (session.questsDone or 0) + 1
	local player = session.player
	DataManager.updateStat(player, "quests", 1)
	local remote = Remotes.get("QuestCompleted")
	if remote then
		remote:FireClient(player, {
			title = questState.def.title,
			reward = questState.def.reward,
			mandatory = not questState.optional,
		})
	end
	local AchievementManager = script.Parent:FindFirstChild("AchievementManager")
	if AchievementManager then
		require(AchievementManager).check(player, "quest")
	end
	DataManager.save(player)
	if not questState.optional then
		QuestManager.openPortal(session)
	end
end

function QuestManager.addProgress(session, questState, amount)
	local def = questState.def
	if def.kind == "survive" then
		questState.progress.total = (questState.progress.total or 0) + amount
	else
		questState.progress.total = (questState.progress.total or 0) + amount
	end
	if (questState.progress.total or 0) >= def.need and questState.state == "active" then
		completeQuest(session, questState)
	end
	QuestManager.sendUpdate(session)
end

-- label: имя NPC / метка объекта (nil = любой)
function QuestManager.progress(session, kind, label, amount)
	if not session or session.ended then
		return
	end
	for _, questState in ipairs(session.quests) do
		local def = questState.def
		if questState.state == "active" and def.kind == kind then
			local matches = true
			if label and def.label then
				matches = (def.label == label)
			end
			if matches then
				if kind == "talk" then
					if label and questState.progress[label] then
						-- уже поговорили
					else
						if label then
							questState.progress[label] = true
						end
						QuestManager.addProgress(session, questState, amount or 1)
					end
				else
					QuestManager.addProgress(session, questState, amount or 1)
				end
			end
		end
	end
end

function QuestManager.tick(session, dt)
	if not session or session.ended then
		return
	end
	local character = session.player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	local changed = false
	for _, questState in ipairs(session.quests) do
		local def = questState.def
		if questState.state == "active" then
			if def.kind == "survive" then
				questState.progress.total = (questState.progress.total or 0) + dt
				if (questState.progress.total or 0) >= def.need then
					completeQuest(session, questState)
					changed = true
				end
			elseif def.kind == "reach" and questState.zonePart and root and not questState.reached then
				local distance = (root.Position - questState.zonePart.Position).Magnitude
				if distance <= 11 then
					questState.reached = true
					completeQuest(session, questState)
					changed = true
				end
			end
		end
	end
	if changed then
		QuestManager.sendUpdate(session)
	end
end

function QuestManager.openPortal(session)
	if session.portalOpen or not session.portal then
		return
	end
	session.portalOpen = true
	local ring = session.portalRing
	local center = session.portalRing and session.portalRing.Parent:FindFirstChild("PortalCore")
	if ring then
		TweenService:Create(ring, TweenInfo.new(1.2), { Transparency = 0.1 }):Play()
	end
	if center then
		TweenService:Create(center, TweenInfo.new(1.2), { Transparency = 0.3 }):Play()
	end
	if session.portalLight then
		session.portalLight.Brightness = 4
	end
	if session.portalEmitter then
		session.portalEmitter.Rate = 30
	end
	local remote = Remotes.get("PortalOpened")
	if remote then
		remote:FireClient(session.player, { position = session.portalPosition })
	end
	local hint = Remotes.get("Hint")
	if hint then
		hint:FireClient(session.player, "🌀 Портал пробуждения открыт — иди на фиолетовый свет!")
	end
end

function QuestManager.cleanup(session)
	if session.objects then
		for _, child in ipairs(session.objects:GetDescendants()) do
			if child:IsA("BasePart") then
				CollectionService:RemoveTag(child, "Interactable")
			end
		end
	end
	if session.portal then
		for _, child in ipairs(session.portal:GetDescendants()) do
			if child:IsA("BasePart") then
				CollectionService:RemoveTag(child, "Interactable")
			end
		end
	end
	session.quests = {}
	session.questStates = {}
end

return QuestManager
