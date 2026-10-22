--!nonstrict
-- NPCManager: NPC во сне, диалоги и выдача заданий.

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Remotes = require(SharedModules:WaitForChild("Remotes"))
local QuestDefinitions = require(SharedModules:WaitForChild("QuestDefinitions"))

local Config = require(SharedModules:WaitForChild("Config"))
local MapService = require(script.Parent:WaitForChild("MapService"))
local QuestManager = require(script.Parent:WaitForChild("QuestManager"))
local DataManager = require(script.Parent:WaitForChild("DataManager"))

local NPCManager = {}

local NpcToast

local function labelPart(part, text)
	local gui = Instance.new("BillboardGui")
	gui.Name = "NameTag"
	gui.Size = UDim2.fromOffset(230, 50)
	gui.StudsOffset = Vector3.new(0, 3.4, 0)
	gui.AlwaysOnTop = true
	gui.MaxDistance = 80
	gui.Adornee = part
	gui.Parent = part

	local label = Instance.new("TextLabel")
	label.Size = UDim2.fromScale(1, 1)
	label.BackgroundTransparency = 1
	label.Text = text
	label.TextColor3 = Color3.fromRGB(235, 230, 255)
	label.TextStrokeTransparency = 0.35
	label.TextScaled = true
	label.Font = Enum.Font.GothamBold
	label.Parent = gui
end

function NPCManager.spawnNPCs(session)
	session.npcs = {}
	local templates = ServerStorage:FindFirstChild("NPCTemplates")
	local template = templates and templates:FindFirstChild("NPC")
	if not template then
		warn("[NPCManager] нет шаблона NPC")
		return
	end
	local markers = MapService.collectMarkers(session.mapModel, "MK_NPC")
	local definitions = session.dream.npcs or {}
	for index, marker in ipairs(markers) do
		local definition = definitions[((index - 1) % math.max(1, #definitions)) + 1]
		if not definition then
			break
		end
		local model = template:Clone()
		model.Name = "NPC_" .. index
		for _, part in ipairs(model:GetDescendants()) do
			if part:IsA("BasePart") and string.sub(part.Name, 1, 3) ~= "Eye" and part.Name ~= "Root" and part.Name ~= "Head" then
				part.Color = definition.color or Color3.fromRGB(180, 180, 200)
			end
		end
		local root = model.PrimaryPart or model:FindFirstChild("Root")
		if not root then
			model:Destroy()
			continue
		end
		model:PivotTo(CFrame.new(marker.Position + Vector3.new(0, 3.2, 0)))
		model.Parent = session.container
		labelPart(root, "❗ " .. definition.name)

		local talkPart = model:FindFirstChild("TalkPoint", true)
		if not talkPart then
			talkPart = Instance.new("Part")
			talkPart.Name = "TalkPoint"
			talkPart.Size = Vector3.new(4.5, 6, 4.5)
			talkPart.Transparency = 1
			talkPart.Anchored = true
			talkPart.CanCollide = false
			talkPart.CanQuery = false
			talkPart.CanTouch = false
			talkPart.CFrame = root.CFrame
			talkPart.Parent = model
		end
		talkPart:SetAttribute("InteractKind", "npc")
		talkPart:SetAttribute("NPCIndex", index)
		talkPart:SetAttribute("Prompt", "Поговорить: " .. definition.name)
		CollectionService:AddTag(talkPart, "Interactable")

		session.npcs[index] = { model = model, definition = definition, talkPart = talkPart }
	end
end

local function questForNPC(session, npcName)
	local fallback = nil
	for _, questState in ipairs(session.quests or {}) do
		if questState.state == "offered" and (questState.giver == npcName or not questState.giver) then
			return questState
		end
		if questState.state == "active" and not fallback then
			fallback = questState
		end
	end
	return fallback
end

local function vitals(session)
	local character = session.player and session.player.Character
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

local HEAL_PRICE = 120
local CALM_PRICE = 0
local ITEM_PRICE = 80

function NPCManager.openDialog(session, part)
	if session.ended then
		return
	end
	local index = part:GetAttribute("NPCIndex")
	local npc = session.npcs and session.npcs[index]
	local definition = npc and npc.definition
	if not definition then
		return
	end
	if npc then
		npc.talks = (npc.talks or 0) + 1
		npc.lastTalk = os.clock()
	end

	local questState = questForNPC(session, definition.name)
	local options = {}
	local lines = { definition.line or "..." }

	-- Приветствие зависит от числа разговоров
	if npc and npc.talks and npc.talks > 1 then
		table.insert(lines, "«Ты снова здесь? Сон не любит, когда его замечают.»")
	end

	if questState and questState.state == "offered" then
		table.insert(lines, "«Мне нужна помощь: " .. questState.def.title .. ". " .. questState.def.desc .. "»")
		table.insert(options, { text = "📜 Взять задание", action = "accept_quest:" .. questState.def.id })
	elseif questState and questState.state == "active" then
		table.insert(lines, "«" .. questState.def.desc .. "»")
		table.insert(options, { text = "💡 Подсказка", action = "hint:" .. questState.def.id })
	elseif questState and questState.state == "done" then
		table.insert(lines, "«Ты справился. Сон запомнит это.»")
		if not questState.thanked then
			table.insert(options, { text = "🎁 Поблагодарить", action = "thank:" .. questState.def.id })
		end
	end

	-- Услуги: лечение и успокоение
	local coins = DataManager.get(session.player).coins
	local character = session.player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	local hurt = humanoid and humanoid.Health < humanoid.MaxHealth - 1
	if hurt or (session.sanity or 100) < Config.MaxSanity - 1 then
		table.insert(options, {
			text = ("🩹 Перевязать раны (%d 🪙)"):format(HEAL_PRICE),
			action = "heal",
		})
		table.insert(options, { text = "🧘 Успокоиться (бесплатно)", action = "calm" })
	end

	-- Обмен: NPC отдаёт зелье рассудка
	local stock = npc and npc.stock
	if stock == nil then
		stock = 1
		if npc then
			npc.stock = stock
		end
	end
	if stock > 0 then
		table.insert(options, { text = ("🧪 Купить зелье рассудка (%d 🪙)"):format(ITEM_PRICE), action = "trade" })
	end

	table.insert(options, { text = "🕯 Что здесь случилось?", action = "lore" })
	table.insert(options, { text = "🚪 Уйти", action = "close" })

	session.dialog = { npcIndex = index, questId = questState and questState.def.id, coins = coins }

	local remote = Remotes.get("DialogOpen")
	if remote then
		remote:FireClient(session.player, {
			npcName = definition.name,
			text = table.concat(lines, "\n\n"),
			kind = "npc",
			options = options,
		})
	end

	-- Разговор засчитывается заданию «поговорить с NPC»
	QuestManager.progress(session, "talk", definition.name, 1)
end

function NPCManager.handleChoice(session, action)
	if not session then
		return
	end
	local close = Remotes.get("DialogClosed")
	if close then
		close:FireClient(session.player)
	end
	if type(action) ~= "string" then
		return
	end

	if string.sub(action, 1, 12) == "accept_quest" then
		local questId = string.match(action, "accept_quest:(.+)")
		if questId and QuestManager.acceptQuest(session, questId) then
			NpcToast(session, "Задание взято: " .. questId)
		end
	elseif string.sub(action, 1, 4) == "hint" then
		local questId = string.match(action, "hint:(.+)")
		local quest = questId and QuestDefinitions.get(questId)
		local hint = Remotes.get("Hint")
		if hint then
			hint:FireClient(session.player, quest and ("«" .. quest.title .. "»: " .. quest.desc) or (session.dream.hint or "Слушай тишину — она подскажет."))
		end
	elseif action == "lore" then
		local messages = session.dream.messages or {}
		local hint = Remotes.get("Hint")
		if hint then
			hint:FireClient(session.player, #messages > 0 and messages[math.random(1, #messages)] or (session.dream.hint or "Слушай тишину — она подскажет."))
		end
	elseif action == "heal" then
		local character = session.player.Character
		local humanoid = character and character:FindFirstChildOfClass("Humanoid")
		if not humanoid then
			return
		end
		if session.sanity >= Config.MaxSanity - 1 and humanoid.Health >= humanoid.MaxHealth - 1 then
			NpcToast(session, "Тебе не нужна помощь")
			return
		end
		if not DataManager.spendCoins(session.player, HEAL_PRICE) then
			NpcToast(session, "Не хватает монет: нужно " .. HEAL_PRICE)
			return
		end
		humanoid.Health = math.min(humanoid.MaxHealth, humanoid.Health + 45)
		session.sanity = math.min(Config.MaxSanity, (session.sanity or 100) + 35)
		vitals(session)
		NpcToast(session, "NPC перевязал раны: +45 ❤ и +35 🧠")
	elseif action == "calm" then
		session.sanity = math.min(Config.MaxSanity, (session.sanity or 100) + 18)
		session.stamina = math.min(Config.MaxStamina, (session.stamina or 100) + 20)
		vitals(session)
		NpcToast(session, "Ты дышишь ровнее: +18 🧠 и +20 ⚡")
	elseif action == "trade" then
		local index = session.dialog and session.dialog.npcIndex
		local npc = index and session.npcs and session.npcs[index]
		if npc and (npc.stock or 0) <= 0 then
			NpcToast(session, "У NPC больше нет зелий")
			return
		end
		if not DataManager.spendCoins(session.player, ITEM_PRICE) then
			NpcToast(session, "Не хватает монет: нужно " .. ITEM_PRICE)
			return
		end
		if npc then
			npc.stock = math.max(0, (npc.stock or 1) - 1)
		end
		local profile = DataManager.get(session.player)
		profile.consumables.sanity_potion = (profile.consumables.sanity_potion or 0) + 1
		DataManager.pushProfile(session.player)
		NpcToast(session, "Получено зелье рассудка (используй в инвентаре или кнопкой)")
	elseif string.sub(action, 1, 5) == "thank" then
		local questId = string.match(action, "thank:(.+)")
		local questState = questId and session.questStates and session.questStates[questId]
		if questState and not questState.thanked then
			questState.thanked = true
			DataManager.addCoins(session.player, 25)
			session.coinsEarned = (session.coinsEarned or 0) + 25
			NpcToast(session, "NPC отблагодарил тебя: +25 🪙")
		end
	end
end

NpcToast = function(session, text)
	local remote = Remotes.get("Toast")
	if remote and session.player then
		remote:FireClient(session.player, { text = text, color = Color3.fromRGB(160, 200, 255), icon = "📜" })
	end
end

function NPCManager.cleanup(session)
	for _, npc in pairs(session.npcs or {}) do
		if npc.model and npc.model.Parent then
			npc.model:Destroy()
		end
	end
	session.npcs = {}
end

return NPCManager
