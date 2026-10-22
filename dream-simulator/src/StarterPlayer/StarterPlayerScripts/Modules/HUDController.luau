--!nocheck
--[[
	HUDController.luau
	Игровой интерфейс сна: таймер, рассудок/выносливость/здоровье, задания,
	подсказки, уведомления, диалоги, события, компас и мобильные кнопки.
]]

local RunService = game:GetService("RunService")
local ContextActionService = game:GetService("ContextActionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local Config = require(ReplicatedStorage.SharedModules.Config)
local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ClientState = require(script.Parent.ClientState)

local HUDController = {}

local hud
local dialogGui
local resultGui
local toastQueue = {}
local toastActive = false
local questLogOpen = false
local dialogOpen = false
local eventHideAt = 0
local monsterAlertUntil = 0

local function getGui()
	hud = hud or ClientState.getGui("DreamHUD")
	return hud
end

local function setVisible(element, visible)
	if element then
		element.Visible = visible
	end
end

------------------------------------------------------------------
-- Уведомления
------------------------------------------------------------------
local function showNextToast()
	if toastActive or #toastQueue == 0 then
		return
	end
	local gui = getGui()
	local area = gui and gui:FindFirstChild("ToastArea", true)
	local template = area and area:FindFirstChild("ToastTemplate")
	if not area or not template then
		return
	end
	toastActive = true
	local payload = table.remove(toastQueue, 1)

	local frame = template:Clone()
	frame.Name = "Toast"
	frame.Visible = true
	frame.Parent = area
	if payload.color then
		local accent = frame:FindFirstChild("Accent") or frame:FindFirstChildWhichIsA("Frame")
		if accent and accent ~= frame then
			accent.BackgroundColor3 = payload.color
		end
		local stroke = frame:FindFirstChildWhichIsA("UIStroke")
		if stroke then
			stroke.Color = payload.color
		end
		local text = frame:FindFirstChild("Text")
		if text then
			text.TextColor3 = payload.color
		end
	end
	local icon = frame:FindFirstChild("Icon")
	if icon then
		icon.Text = payload.icon or "✨"
	end
	local text = frame:FindFirstChild("Text")
	if text then
		text.Text = payload.text
	end
	frame.BackgroundTransparency = 1
	frame.Position = UDim2.new(frame.Position.X.Scale + 0.05, 0, frame.Position.Y.Scale, 0)
	ClientState.tween(frame, 0.22, {
		BackgroundTransparency = 0.12,
		Position = UDim2.new(1 - frame.Size.X.Scale - 0.02, 0, frame.Position.Y.Scale, 0),
	})
	task.delay(3.4, function()
		ClientState.tween(frame, 0.35, { BackgroundTransparency = 1, Position = UDim2.new(frame.Position.X.Scale + 0.05, 0, frame.Position.Y.Scale, 0) })
		task.wait(0.4)
		if frame then
			frame:Destroy()
		end
		toastActive = false
		showNextToast()
	end)
end

function HUDController.toast(payload)
	table.insert(toastQueue, payload)
	showNextToast()
end

------------------------------------------------------------------
-- Задания
------------------------------------------------------------------

local function clearChildren(parent, keepNames)
	for _, child in ipairs(parent:GetChildren()) do
		if not keepNames or not keepNames[child.Name] then
			if child.Name ~= "UIListLayout" and child.Name ~= "UIPadding" and child.Name ~= "ObjectiveTemplate" and child.Name ~= "QuestEntryTemplate" then
				child:Destroy()
			end
		end
	end
end

local function buildObjectiveText(quest)
	local parts = {}
	for _, objective in ipairs(quest.objectives) do
		local prefix = objective.done and "✔" or "•"
		local progress = objective.goal > 1 and (" " .. objective.current .. "/" .. objective.goal) or ""
		table.insert(parts, prefix .. " " .. objective.text .. progress)
	end
	return table.concat(parts, "\n")
end

-- Плашка «ФОНАРИК: ВКЛ/ВЫКЛ» + цвет по заряду
function HUDController.renderFlashlight(data)
	local label = ClientState.el("DreamHUD", "FlashlightLabel")
	local chip = ClientState.el("DreamHUD", "FlashlightChip")
	local on = data and data.flashlight == true
	local battery = math.floor((data and data.battery) or 0)
	if label then
		if on then
			label.Text = "🔦 ФОНАРИК: ВКЛ · " .. tostring(battery) .. "%"
			label.TextColor3 = Color3.fromRGB(255, 226, 150)
		else
			label.Text = "🔦 ФОНАРИК: ВЫКЛ · " .. tostring(battery) .. "%"
			label.TextColor3 = Color3.fromRGB(158, 161, 198)
		end
	end
	if chip then
		local stroke = chip:FindFirstChildWhichIsA("UIStroke")
		if stroke then
			stroke.Transparency = on and 0.15 or 0.6
			stroke.Color = on and Color3.fromRGB(255, 209, 102) or Color3.fromRGB(120, 122, 160)
		end
		chip.BackgroundTransparency = on and 0.1 or 0.3
	end
	-- Процент заряда прямо в подписи шкалы батареи
	local batteryBar = ClientState.el("DreamHUD", "BatteryBar")
	if batteryBar then
		for _, child in ipairs(batteryBar:GetChildren()) do
			if child:IsA("TextLabel") then
				child.Text = "🔦 БАТАРЕЯ: " .. tostring(battery) .. "%"
				child.TextColor3 = battery < 20 and Color3.fromRGB(255, 150, 150) or Color3.fromRGB(158, 161, 198)
			end
		end
	end
	-- Мобильная кнопка фонарика
	local mobileButton = ClientState.el("DreamHUD", "FlashlightButton")
	if mobileButton then
		mobileButton.BackgroundTransparency = on and 0.05 or 0.25
	end
	-- Предупреждение о низком заряде (один раз за сон)
	if not on and battery > 0 and battery <= 18 and not HUDController.batteryWarned then
		HUDController.batteryWarned = true
		ClientState.toast("Батарея фонарика почти пуста", Color3.fromRGB(255, 190, 120), "🔋")
	end
end

function HUDController.renderQuests()
	local gui = getGui()
	if not gui then
		return
	end
	-- Трекер
	local tracker = gui:FindFirstChild("QuestTracker", true)
	if tracker then
		local list = tracker:FindFirstChild("ObjectiveList")
		local title = tracker:FindFirstChild("QuestTitle")
		local template = list and list:FindFirstChild("ObjectiveTemplate")
		if list and template then
			for _, child in ipairs(list:GetChildren()) do
				if child ~= template and child:IsA("TextLabel") then
					child:Destroy()
				end
			end
			local shown = 0
			local mainTitle = nil
			for _, quest in ipairs(ClientState.quests) do
				if quest.state == "active" and shown < 2 then
					shown += 1
					if not mainTitle then
						mainTitle = (quest.mandatory and "❗ " or "⭐ ") .. quest.title
					end
					for _, objective in ipairs(quest.objectives) do
						local label = template:Clone()
						label.Name = "Objective"
						label.Visible = true
						label.Text = (objective.done and "✔ " or "• ") .. objective.text ..
							(objective.goal > 1 and ("  " .. objective.current .. "/" .. objective.goal) or "")
						label.TextColor3 = objective.done and Color3.fromRGB(140, 240, 180) or Color3.fromRGB(220, 225, 255)
						label.Parent = list
					end
				end
			end
			if shown == 0 then
				local offerered = nil
				for _, quest in ipairs(ClientState.quests) do
					if quest.state == "offered" then
						offerered = quest
						break
					end
				end
				if offerered then
					mainTitle = "📜 Новое задание у " .. (offerered.giver or "NPC")
				else
					mainTitle = "Все задания выполнены"
				end
			end
			if title then
				title.Text = mainTitle or "ЗАДАНИЯ"
			end
			tracker.Visible = true
		end
	end

	-- Полный лог
	local logFrame = gui:FindFirstChild("QuestLog", true)
	if logFrame then
		local list = logFrame:FindFirstChild("QuestLogList")
		local template = list and list:FindFirstChild("QuestEntryTemplate")
		if list and template then
			for _, child in ipairs(list:GetChildren()) do
				if child ~= template and child:IsA("Frame") then
					child:Destroy()
				end
			end
			local count = 0
			for _, quest in ipairs(ClientState.quests) do
				count += 1
				local entry = template:Clone()
				entry.Name = "Quest_" .. quest.id
				entry.Visible = true
				entry.Parent = list
				local title = entry:FindFirstChild("Title")
				if title then
					local stateText = "В РАБОТЕ"
					if quest.state == "done" then
						stateText = "ВЫПОЛНЕНО"
					elseif quest.state == "offered" then
						stateText = "ПОГОВОРИТЕ С NPC"
					end
					title.Text = (quest.mandatory and "❗ " or "⭐ ") .. quest.title .. "  <font color='#9d92c9'>[" .. stateText .. "]</font>"
					title.TextColor3 = quest.state == "done" and Color3.fromRGB(140, 240, 180) or Color3.fromRGB(245, 240, 255)
				end
				local body = entry:FindFirstChild("Body")
				if body then
					if quest.state == "offered" then
						body.Text = "Выдаёт: " .. (quest.giver or "NPC") .. "\nНаграда: " .. quest.reward .. " 🪙\nПоговорите с ним, чтобы начать."
					else
						body.Text = buildObjectiveText(quest) .. "\n\nНаграда: " .. quest.reward .. " 🪙"
					end
				end
				local accent = entry:FindFirstChild("Accent")
				if accent then
					accent.BackgroundColor3 = quest.mandatory and Color3.fromRGB(255, 140, 160) or Color3.fromRGB(255, 214, 120)
				end
			end
			local empty = logFrame:FindFirstChild("EmptyLabel")
			if empty then
				empty.Visible = count == 0
			end
		end
	end
end

function HUDController.toggleQuestLog(force)
	local gui = getGui()
	if not gui then
		return
	end
	local logFrame = gui:FindFirstChild("QuestLog", true)
	if not logFrame then
		return
	end
	questLogOpen = force ~= nil and force or not questLogOpen
	HUDController.renderQuests()
	logFrame.Visible = questLogOpen
	if questLogOpen then
		logFrame.BackgroundTransparency = 1
		ClientState.tween(logFrame, 0.25, { BackgroundTransparency = 0.08 })
	end
	ClientState.setCursorFree(questLogOpen)
	ClientState.emit("QuestLogToggled", questLogOpen)
end

------------------------------------------------------------------
-- Диалоги
------------------------------------------------------------------

local function closeDialog()
	dialogOpen = false
	local gui = getGui()
	if gui then
		local dialog = gui:FindFirstChild("Dialog", true)
		setVisible(dialog, false)
	end
	ClientState.setCursorFree(false)
end

function HUDController.openDialog(payload)
	local gui = getGui()
	if not gui then
		return
	end
	-- В первом лице курсор заблокирован: освобождаем, иначе варианты не нажать
	ClientState.setCursorFree(true)
	local dialog = gui:FindFirstChild("Dialog", true)
	if not dialog then
		return
	end
	dialogOpen = true
	dialog.Visible = true
	local panel = dialog:FindFirstChild("Panel") or dialog
	local nameLabel = panel:FindFirstChild("NpcName", true)
	local textLabel = panel:FindFirstChild("DialogText", true)
	local portrait = panel:FindFirstChild("Portrait", true)
	local optionsFrame = panel:FindFirstChild("Options", true)
	if nameLabel then
		nameLabel.Text = payload.npcName or "??? "
	end
	if textLabel then
		textLabel.Text = payload.text or ""
		textLabel.TextTransparency = 1
		ClientState.tween(textLabel, 0.35, { TextTransparency = 0 })
	end
	if portrait then
		local icon = portrait:FindFirstChild("Icon")
		if icon then
			icon.Text = payload.kind == "code" and "🔐" or (payload.kind == "note" and "📜" or "👤")
		end
	end
	if optionsFrame then
		local template = optionsFrame:FindFirstChild("OptionTemplate")
		for _, child in ipairs(optionsFrame:GetChildren()) do
			if child ~= template and child:IsA("TextButton") then
				child:Destroy()
			end
		end
		if template then
			local options = payload.options or {}
			for index, option in ipairs(options) do
				local button = template:Clone()
				button.Name = "Option_" .. index
				button.Visible = true
				button.Parent = optionsFrame
				local label = button:FindFirstChild("Label") or button
				if label and label ~= button then
					label.Text = tostring(index) .. ". " .. option.text
				else
					button.Text = option.text
				end
				button.BackgroundTransparency = 1
				local stroke = button:FindFirstChildWhichIsA("UIStroke")
				button.MouseEnter:Connect(function()
					ClientState.tween(button, 0.15, { BackgroundTransparency = 0.55 })
					if stroke then
						ClientState.tween(stroke, 0.15, { Transparency = 0 })
					end
				end)
				button.MouseLeave:Connect(function()
					ClientState.tween(button, 0.2, { BackgroundTransparency = 0.85 })
					if stroke then
						ClientState.tween(stroke, 0.2, { Transparency = 0.5 })
					end
				end)
				button.MouseButton1Click:Connect(function()
					ClientState.playSound("Click")
					Remotes.get("DialogChoice"):FireServer(option.action)
					if option.action == "close" then
						closeDialog()
					end
				end)
			end
		end
	end
end

function HUDController.closeDialog()
	closeDialog()
end

------------------------------------------------------------------
-- Инициализация
------------------------------------------------------------------
HUDController.batteryWarned = false

function HUDController.start()
	local gui = getGui()
	if not gui then
		return
	end
	hud = gui
	gui.Enabled = false

	local function bind(name, handler)
		local element = ClientState.el("DreamHUD", name)
		if element then
			element.MouseButton1Click:Connect(function()
				ClientState.playSound("Click")
				handler()
			end)
		end
	end
	bind("SprintButton", function()
		ClientState.emit("MobileAction", "sprint")
	end)
	bind("InteractButton", function()
		ClientState.emit("MobileAction", "interact")
	end)
	bind("FlashlightButton", function()
		ClientState.emit("MobileAction", "flashlight")
	end)
	bind("UseItemButton", function()
		ClientState.emit("MobileAction", "item")
	end)
	bind("QuestLogClose", function()
		HUDController.toggleQuestLog(false)
	end)

	local mobile = ClientState.el("DreamHUD", "MobileControls")
	if mobile then
		mobile.Visible = ClientState.isMobile
	end
	local crosshair = ClientState.el("DreamHUD", "Crosshair")
	if crosshair then
		crosshair.Visible = false
	end

	ClientState.on("Toast", HUDController.toast)

	ClientState.on("Phase", function(phase)
		local visible = phase == "dream" or phase == "waking"
		gui.Enabled = visible
		if phase == "dream" then
			HUDController.batteryWarned = false
		elseif phase == "menu" then
			closeDialog()
			HUDController.toggleQuestLog(false)
		end
	end)

	-- Выбор варианта диалога цифрами 1..9 (удобно и без мыши)
	ContextActionService:BindAction("DreamDialogChoose", function(_, state, input)
		if state ~= Enum.UserInputState.Begin or not dialogOpen then
			return Enum.ContextActionResult.Pass
		end
		local number = tonumber(string.sub(input.KeyCode.Name, 2))
		if not number then
			return Enum.ContextActionResult.Pass
		end
		local gui = getGui()
		local dialog = gui and gui:FindFirstChild("Dialog", true)
		local options = dialog and dialog:FindFirstChild("Options", true)
		if not options then
			return Enum.ContextActionResult.Pass
		end
		local buttons = {}
		for _, child in ipairs(options:GetChildren()) do
			if child:IsA("TextButton") and child.Name:match("^Option_") then
				table.insert(buttons, child)
			end
		end
		table.sort(buttons, function(a, b)
			return (tonumber(a.Name:match("%d+")) or 0) < (tonumber(b.Name:match("%d+")) or 0)
		end)
		local target = buttons[number]
		if target then
			target:Activate()
			return Enum.ContextActionResult.Sink
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.One, Enum.KeyCode.Two, Enum.KeyCode.Three, Enum.KeyCode.Four,
		Enum.KeyCode.Five, Enum.KeyCode.Six, Enum.KeyCode.Seven, Enum.KeyCode.Eight, Enum.KeyCode.Nine)

	Remotes.get("TimerUpdate").OnClientEvent:Connect(function(data)
		ClientState.timer.remaining = data.remaining
		ClientState.timer.total = data.total or Config.DreamDuration
		local label = ClientState.el("DreamHUD", "TimerLabel")
		if label then
			label.Text = "🌙 ДО ПРОБУЖДЕНИЯ: " .. ClientState.fmtTime(data.remaining)
			label.TextColor3 = data.remaining <= 60 and Color3.fromRGB(255, 120, 130) or Color3.fromRGB(240, 238, 255)
		end
		local bar = ClientState.el("DreamHUD", "TimerBar")
		local fill = bar and bar:FindFirstChild("Fill")
		if fill then
			ClientState.tween(fill, 0.25, { Size = UDim2.new(math.clamp(data.remaining / (data.total or 600), 0, 1), 0, 1, 0) })
		end
	end)

	Remotes.get("VitalsUpdate").OnClientEvent:Connect(function(data)
		ClientState.vitals = data
		local function setBar(name, value, max)
			local bar = ClientState.el("DreamHUD", name)
			local fill = bar and bar:FindFirstChild("Fill")
			if fill then
				ClientState.tween(fill, 0.2, { Size = UDim2.new(math.clamp(value / math.max(1, max), 0, 1), 0, 1, 0) })
			end
		end
		setBar("SanityBar", data.sanity, data.maxSanity or 100)
		setBar("StaminaBar", data.stamina, data.maxStamina or 100)
		setBar("HealthBar", data.health, data.maxHealth or 100)
		setBar("BatteryBar", data.battery or 100, data.batteryMax or 100)
		HUDController.renderFlashlight(data)
		ClientState.emit("Vitals", data)
	end)

	Remotes.get("PromptUpdate").OnClientEvent:Connect(function(data)
		ClientState.prompt = data and data.id and data or nil
		local promptFrame = ClientState.el("DreamHUD", "InteractPrompt")
		if not promptFrame then
			return
		end
		if data and data.id and not dialogOpen then
			promptFrame.Visible = true
			local label = promptFrame:FindFirstChild("PromptText")
			if label then
				label.Text = data.text or "Взаимодействовать"
			end
			local key = promptFrame:FindFirstChild("KeyLabel")
			if key then
				key.Text = ClientState.isMobile and "👆" or "E"
			end
		else
			promptFrame.Visible = false
		end
	end)

	Remotes.get("QuestUpdate").OnClientEvent:Connect(function(data)
		ClientState.quests = data.quests or {}
		ClientState.mandatoryLeft = data.mandatoryLeft or 0
		HUDController.renderQuests()
		local hint = ClientState.el("DreamHUD", "HintLabel")
		if hint then
			if ClientState.quests and #ClientState.quests > 0 then
				local anyActive = false
				for _, quest in ipairs(ClientState.quests) do
					if quest.state == "active" then
						anyActive = true
					end
				end
				if not anyActive then
					hint.Text = "Найдите NPC с меткой ❗ и возьмите задание"
					hint.Visible = true
					task.delay(7, function()
						if hint then
							hint.Visible = false
						end
					end)
				end
			end
		end
	end)

	Remotes.get("QuestCompleted").OnClientEvent:Connect(function(data)
		ClientState.playSound("Purchase")
		HUDController.toast({
			text = (data.mandatory and "Обязательное задание выполнено: " or "Задание выполнено: ") .. (data.title or ""),
			color = data.mandatory and Color3.fromRGB(140, 240, 200) or Color3.fromRGB(255, 220, 140),
			icon = data.mandatory and "✅" or "⭐",
		})
		HUDController.toast({
			text = "+" .. tostring(data.reward or 0) .. " Dream Coins",
			color = Color3.fromRGB(255, 220, 140),
			icon = "🪙",
		})
	end)

	Remotes.get("DialogOpen").OnClientEvent:Connect(function(payload)
		if ClientState.phase ~= "dream" then
			return
		end
		HUDController.openDialog(payload)
	end)

	Remotes.get("DialogClosed").OnClientEvent:Connect(function()
		closeDialog()
	end)

	Remotes.get("EventStarted").OnClientEvent:Connect(function(data)
		local banner = ClientState.el("DreamHUD", "EventBanner")
		if not banner then
			return
		end
		banner.Visible = true
		local icon = banner:FindFirstChild("EventIcon")
		local title = banner:FindFirstChild("EventTitle")
		local desc = banner:FindFirstChild("EventDesc")
		if icon then
			icon.Text = data.icon or "⚡"
		end
		if title then
			title.Text = data.title or "СОБЫТИЕ"
		end
		if desc then
			desc.Text = data.description or ""
		end
		banner.BackgroundTransparency = 1
		ClientState.tween(banner, 0.4, { BackgroundTransparency = 0.15 })
		eventHideAt = os.clock() + math.min(6, data.duration or 4) + 1.5
		task.delay(1.2, function()
			ClientState.tween(banner, 0.3, { Size = UDim2.new(0, 560, 0, 110) })
		end)
		task.delay(math.min(7, (data.duration or 4) + 1.5), function()
			ClientState.tween(banner, 0.5, { BackgroundTransparency = 1 })
			task.wait(0.6)
			if banner then
				banner.Visible = false
			end
		end)
	end)

	Remotes.get("MonsterAlert").OnClientEvent:Connect(function(data)
		local alert = ClientState.el("DreamHUD", "MonsterAlert")
		if not alert then
			return
		end
		local nameLabel = alert:FindFirstChild("MonsterName")
		if nameLabel then
			nameLabel.Text = "ОНО РЯДОМ: " .. tostring(data.name or "МОНСТР")
		end
		alert.Visible = true
		alert.BackgroundTransparency = 0
		monsterAlertUntil = os.clock() + 4
		ClientState.tween(alert, 0.4, { BackgroundTransparency = 1 })
		task.delay(4, function()
			if alert and os.clock() >= monsterAlertUntil then
				alert.Visible = false
			end
		end)
	end)

	Remotes.get("PortalOpened").OnClientEvent:Connect(function(data)
		local hint = ClientState.el("DreamHUD", "PortalHint")
		if hint then
			hint.Visible = true
			local label = hint:FindFirstChild("Label") or hint
			if label and label ~= hint then
				label.Text = "🌀 ПОРТАЛ ПРОБУЖДЕНИЯ ОТКРЫТ — иди к фиолетовому свету"
			else
				hint.Text = "🌀 ПОРТАЛ ПРОБУЖДЕНИЯ ОТКРЫТ"
			end
		end
		ClientState.portalPosition = data and data.position or nil
	end)

	Remotes.get("Hint").OnClientEvent:Connect(function(text)
		local hint = ClientState.el("DreamHUD", "HintLabel")
		if hint then
			hint.Text = text
			hint.Visible = true
			task.delay(8, function()
				if hint then
					hint.Visible = false
				end
			end)
		end
	end)

	ClientState.on("ToggleQuestLog", function()
		HUDController.toggleQuestLog()
	end)

	-- Компас: стрелка к ближайшей цели
	RunService.RenderStepped:Connect(function()
		if ClientState.phase ~= "dream" then
			return
		end
		local compass = ClientState.el("DreamHUD", "Compass")
		local arrow = compass and compass:FindFirstChild("CompassArrow")
		local distanceLabel = compass and compass:FindFirstChild("DistanceLabel")
		if not compass or not arrow then
			return
		end
		local camera = workspace.CurrentCamera
		local target = nil
		if ClientState.portalPosition then
			local p = ClientState.portalPosition
			target = Vector3.new(p.x, p.y, p.z)
		else
			for _, quest in ipairs(ClientState.quests) do
				if quest.target and quest.state == "active" then
					target = Vector3.new(quest.target.x, quest.target.y, quest.target.z)
					break
				end
			end
		end
		if target and camera then
			local root = ClientState.player.Character and ClientState.player.Character:FindFirstChild("HumanoidRootPart")
			local origin = root and root.Position or camera.CFrame.Position
			local delta = target - origin
			local flat = Vector3.new(delta.X, 0, delta.Z)
			local look = camera.CFrame.LookVector
			local flatLook = Vector3.new(look.X, 0, look.Z)
			if flat.Magnitude > 1 and flatLook.Magnitude > 0.01 then
				local angle = math.deg(math.atan2(flat.X, flat.Z) - math.atan2(flatLook.X, flatLook.Z))
				arrow.Rotation = -angle
				if distanceLabel then
					distanceLabel.Text = math.floor(flat.Magnitude) .. " м"
				end
				compass.Visible = true
			end
		else
			compass.Visible = false
		end
		if compass.Visible and not ClientState.bonuses.compass and not ClientState.portalPosition then
			compass.Visible = false
		end
	end)

	-- Результаты
	resultGui = ClientState.getGui("ResultGUI")
	ClientState.on("ShowResult", function(result)
		HUDController.showResult(result)
	end)
end

function HUDController.showResult(result)
	local gui = ClientState.getGui("ResultGUI")
	if not gui then
		return
	end
	gui.Enabled = true
	local frame = gui
	local function setText(name, text, color)
		local element = frame:FindFirstChild(name, true)
		if element and element:IsA("TextLabel") then
			element.Text = text
			if color then
				element.TextColor3 = color
			end
		end
	end
	setText("OutcomeTitle", result.won and "🌟 ТЫ ПРОСНУЛСЯ!" or "💀 СОН ПРОВАЛЕН",
		result.won and Color3.fromRGB(160, 255, 200) or Color3.fromRGB(255, 110, 120))
	setText("OutcomeSubtitle", result.won and "Кошмар остался позади. Пока что." or (result.reasonText or "Кошмар оказался сильнее тебя..."))
	setText("DreamValue", result.dreamName or "—")
	setText("DifficultyValue", result.difficultyName or "—")
	setText("TimeValue", ClientState.fmtTime(result.survived or 0) .. " из " .. ClientState.fmtTime(Config.DreamDuration))
	setText("QuestsValue", tostring(result.questsDone or 0) .. " / " .. tostring(result.questsTotal or 0))
	setText("CoinsValue", "+" .. tostring(result.coins or 0) .. " 🪙")
	setText("ReasonValue", result.won and "Портал пробуждения" or (result.reasonText or "—"))

	local card = gui:FindFirstChild("Card", true)
	if card then
		card.Position = UDim2.new(0.5, 0, 0.62, 0)
		card.BackgroundTransparency = 1
		ClientState.tween(card, 0.6, { Position = UDim2.new(0.5, 0, 0.5, 0), BackgroundTransparency = 0.05 }, Enum.EasingStyle.Back)
	end

	local menuButton = gui:FindFirstChild("MenuButton", true)
	if menuButton then
		menuButton.MouseButton1Click:Connect(function()
			gui.Enabled = false
			ClientState.emit("ResultAction", "menu")
		end)
	end
	local retryButton = gui:FindFirstChild("RetryButton", true)
	if retryButton then
		retryButton.MouseButton1Click:Connect(function()
			gui.Enabled = false
			ClientState.emit("ResultAction", "retry")
		end)
	end
end

function HUDController.hideResult()
	local gui = ClientState.getGui("ResultGUI")
	if gui then
		gui.Enabled = false
	end
end

return HUDController
