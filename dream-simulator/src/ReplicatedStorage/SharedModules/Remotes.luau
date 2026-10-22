--!nonstrict
-- Remotes: доступ к каналам связи. Инстансы лежат в ReplicatedStorage.Remotes.

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Remotes = {}

local folder = ReplicatedStorage:WaitForChild("Remotes", 30)
if not folder then
	error("[Remotes] папка ReplicatedStorage.Remotes не найдена")
end

Remotes.folder = folder

-- Полный список каналов (используется сборкой места).
Remotes.EVENTS = {
	-- клиент -> сервер
	"RequestDream",
	"ReturnToMenu",
	"BuyItem",
	"EquipItem",
	"UnequipItem",
	"UseItem",
	"UpdateSettings",
	"ResetSettings",
	"Interact",
	"DialogChoice",
	"CloseDialog",
	"PlayerReady",
	"SetSprinting",
	"SetFlashlight",
	"LookUpdate",
	"ReportDeath",
	-- сервер -> клиент
	"ProfileUpdated",
	"TimerUpdate",
	"VitalsUpdate",
	"QuestUpdate",
	"QuestCompleted",
	"DialogOpen",
	"DialogClosed",
	"Toast",
	"Scare",
	"DreamStarted",
	"DreamEnded",
	"SleepSequence",
	"DreamStateChanged",
	"PortalOpened",
	"MonsterAlert",
	"Hint",
	"AchievementUnlocked",
	"EventStarted",
	"PurchaseResult",
	"EquipResult",
	"PromptUpdate",
	"InteractionResult",
}

Remotes.FUNCTIONS = { "RequestProfile" }

local cache = {}

function Remotes.get(name)
	if cache[name] then
		return cache[name]
	end
	local instance = folder:WaitForChild(name, 20)
	if not instance then
		warn("[Remotes] канал не найден: " .. tostring(name))
		return nil
	end
	cache[name] = instance
	return instance
end

function Remotes.getFolder()
	return folder
end

-- Удобные обёртки.
function Remotes.fire(name, ...)
	local remote = Remotes.get(name)
	if remote and remote:IsA("RemoteEvent") then
		remote:FireServer(...)
	end
end

function Remotes.toClient(player, name, ...)
	local remote = Remotes.get(name)
	if remote and remote:IsA("RemoteEvent") then
		remote:FireClient(player, ...)
	end
end

function Remotes.toAll(name, ...)
	local remote = Remotes.get(name)
	if remote and remote:IsA("RemoteEvent") then
		remote:FireAllClients(...)
	end
end

return Remotes
