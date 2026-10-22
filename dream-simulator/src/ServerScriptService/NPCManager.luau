--!nonstrict
-- NPCManager: NPC во сне, диалоги и выдача заданий.

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local ServerStorage = game:GetService("ServerStorage")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Remotes = require(SharedModules:WaitForChild("Remotes"))
local QuestDefinitions = require(SharedModules:WaitForChild("QuestDefinitions"))

local MapService = require(script.Parent:WaitForChild("MapService"))
local QuestManager = require(script.Parent:WaitForChild("QuestManager"))

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

	local questState = questForNPC(session, definition.name)
	local options = {}
	local text = definition.line or "..."

	if questState and questState.state == "offered" then
		text = text .. "\n\n«Мне нужна помощь: " .. questState.def.title .. ". " .. questState.def.desc .. "»"
		table.insert(options, { text = "📜 Взять задание", action = "accept_quest:" .. questState.def.id })
	elseif questState and questState.state == "active" then
		text = text .. "\n\n«" .. questState.def.desc .. "»"
		table.insert(options, { text = "💡 Подсказка", action = "hint:" .. questState.def.id })
	end

	table.insert(options, { text = "🕯 Что здесь случилось?", action = "lore" })
	table.insert(options, { text = "🚪 Уйти", action = "close" })

	session.dialog = { npcIndex = index, questId = questState and questState.def.id }

	local remote = Remotes.get("DialogOpen")
	if remote then
		remote:FireClient(session.player, {
			npcName = definition.name,
			text = text,
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
		if hint and #messages > 0 then
			hint:FireClient(session.player, messages[math.random(1, #messages)])
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
