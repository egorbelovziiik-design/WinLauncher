--!nocheck
--[[
	MenuController.luau
	Главное меню, выбор сложности, магазин, инвентарь, достижения,
	статистика и настройки. Все кнопки действительно работают.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local UserInputService = game:GetService("UserInputService")
local TweenService = game:GetService("TweenService")

local Config = require(ReplicatedStorage.SharedModules.Config)
local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ItemDefinitions = require(ReplicatedStorage.SharedModules.ItemDefinitions)
local AchievementDefinitions = require(ReplicatedStorage.SharedModules.AchievementDefinitions)
local DreamDefinitions = require(ReplicatedStorage.SharedModules.DreamDefinitions)
local ClientState = require(script.Parent.ClientState)
local SoundController = require(script.Parent.SoundController)
local CameraController = require(script.Parent.CameraController)

local MenuController = {}

local PANELS = { "ShopGUI", "InventoryGUI", "AchievementsGUI", "StatsGUI", "SettingsGUI", "DifficultyGUI" }
local openPanel = nil
local buttonsReady = false

------------------------------------------------------------------
-- Утилиты
------------------------------------------------------------------
local function setVisible(guiName, visible)
	local gui = ClientState.getGui(guiName)
	if gui then
		gui.Enabled = visible
	end
end

local function hover(button, hoverTransparency)
	if not button or not button:IsA("GuiButton") then
		return
	end
	local base = button.BackgroundTransparency
	button.MouseEnter:Connect(function()
		ClientState.tween(button, 0.15, { BackgroundTransparency = hoverTransparency or math.max(0, base - 0.15) })
		ClientState.tween(button, 0.15, { Size = UDim2.new(button.Size.X.Scale * 1.02, button.Size.X.Offset, button.Size.Y.Scale, button.Size.Y.Offset) })
	end)
	button.MouseLeave:Connect(function()
		ClientState.tween(button, 0.2, { BackgroundTransparency = base })
	end)
end

local function clickSound()
	SoundController.play("Click", 0.6)
end

local function makeButton(name)
	local button = ClientState.el("MainMenu", name)
	if not button then
		return nil
	end
	hover(button)
	return button
end

------------------------------------------------------------------
-- Главное меню
------------------------------------------------------------------
function MenuController.show()
	setVisible("MainMenu", true)
	CameraController.setMode("menu")
	local menu = ClientState.getGui("MainMenu")
	if menu then
		local content = menu:FindFirstChild("Content", true)
		if content then
			content.Position = UDim2.new(0, -420, 0, content.Position.Y.Offset)
			ClientState.tween(content, 0.9, { Position = UDim2.new(0, 64, 0, content.Position.Y.Offset) }, Enum.EasingStyle.Quint)
		end
		local fade = menu:FindFirstChild("Fade", true)
		if fade then
			fade.Visible = true
			fade.BackgroundTransparency = 0
			ClientState.tween(fade, 1.2, { BackgroundTransparency = 1 })
		end
	end
	MenuController.refreshProfile()
	SoundController.setMenu()
end

function MenuController.hide()
	setVisible("MainMenu", false)
	for _, panel in ipairs(PANELS) do
		setVisible(panel, false)
	end
	openPanel = nil
end

function MenuController.closePanels()
	for _, panel in ipairs(PANELS) do
		setVisible(panel, false)
	end
	openPanel = nil
	MenuController.refreshProfile()
end

function MenuController.openPanelByName(name)
	clickSound()
	for _, panel in ipairs(PANELS) do
		setVisible(panel, panel == name)
	end
	openPanel = name
	if name == "ShopGUI" then
		MenuController.renderShop()
	elseif name == "InventoryGUI" then
		MenuController.renderInventory()
	elseif name == "AchievementsGUI" then
		MenuController.renderAchievements()
	elseif name == "StatsGUI" then
		MenuController.renderStats()
	elseif name == "SettingsGUI" then
		MenuController.renderSettings()
	elseif name == "DifficultyGUI" then
		MenuController.renderDifficulty()
	end
end

function MenuController.refreshProfile()
	local profile = ClientState.profile
	local coins = profile and profile.coins or 0
	for _, name in ipairs({ "CoinsLabel", "CoinValue", "CoinsLabelShop" }) do
		for _, guiName in ipairs({ "MainMenu", "ShopGUI", "InventoryGUI", "AchievementsGUI", "StatsGUI" }) do
			local label = ClientState.el(guiName, name)
			if label then
				if name == "CoinValue" then
					label.Text = tostring(coins)
				else
					label.Text = "🪙 " .. tostring(coins) .. " DREAM COINS"
				end
			end
		end
	end
	if openPanel == "ShopGUI" then
		MenuController.renderShop()
	elseif openPanel == "InventoryGUI" then
		MenuController.renderInventory()
	end
end

------------------------------------------------------------------
-- Выбор сложности
------------------------------------------------------------------
function MenuController.renderDifficulty()
	local gui = ClientState.getGui("DifficultyGUI")
	if not gui then
		return
	end
	local dreamInfo = gui:FindFirstChild("DreamPreview", true)
	if dreamInfo then
		local dream = DreamDefinitions.get(DreamDefinitions.Order[math.random(1, #DreamDefinitions.Order)])
		dreamInfo.Text = "Следующий сон: " .. dream.name .. "\n" .. dream.subtitle
	end
	for _, key in ipairs(Config.DifficultyOrder) do
		local difficulty = Config.Difficulties[key]
		local cardName = key .. "Card"
		local card = gui:FindFirstChild(cardName, true)
		if card then
			card.BackgroundColor3 = Color3.fromRGB(14, 16, 30)
			local stroke = card:FindFirstChildWhichIsA("UIStroke")
			if stroke then
				stroke.Color = difficulty.color
			end
			local nameLabel = card:FindFirstChild("Name") or card:FindFirstChild("CardName")
			if nameLabel then
				nameLabel.Text = difficulty.icon .. " " .. difficulty.name
				nameLabel.TextColor3 = difficulty.color
			end
			local desc = card:FindFirstChild("Desc") or card:FindFirstChild("CardDesc")
			if desc then
				desc.Text = difficulty.description
			end
			local reward = card:FindFirstChild("Reward") or card:FindFirstChild("CardReward")
			if reward then
				reward.Text = "🏆 " .. difficulty.reward .. " монет"
			end
			local time = card:FindFirstChild("Time") or card:FindFirstChild("CardTime")
			if time then
				time.Text = "⏱ 10 минут"
			end
			card.MouseButton1Click:Connect(function()
				MenuController.startDream(key)
			end)
			hover(card, 0.35)
		end
	end
end

function MenuController.startDream(difficultyKey)
	clickSound()
	MenuController.hide()
	ClientState.emit("RequestDream", difficultyKey)
	local remote = Remotes.get("RequestDream")
	if remote then
		remote:FireServer(difficultyKey)
	end
end

------------------------------------------------------------------
-- Магазин
------------------------------------------------------------------
local function clearList(list, templateName)
	for _, child in ipairs(list:GetChildren()) do
		if child.Name ~= templateName and not child:IsA("UIGridLayout") and not child:IsA("UIListLayout") and not child:IsA("UIPadding") then
			child:Destroy()
		end
	end
end

function MenuController.renderShop()
	local gui = ClientState.getGui("ShopGUI")
	local list = gui and gui:FindFirstChild("ItemList", true)
	local template = list and list:FindFirstChild("CardTemplate")
	if not list or not template then
		return
	end
	clearList(list, "CardTemplate")
	for index, itemId in ipairs(ItemDefinitions.Order) do
		local item = ItemDefinitions.get(itemId)
		if item then
			local card = template:Clone()
			card.Name = "Card_" .. itemId
			card.Visible = true
			card.LayoutOrder = index
			card.Parent = list
			local icon = card:FindFirstChild("Icon")
			if icon then
				icon.Text = item.icon
				icon.TextColor3 = item.color
			end
			local name = card:FindFirstChild("Name")
			if name then
				name.Text = item.name
				name.TextColor3 = item.color
			end
			local desc = card:FindFirstChild("Desc")
			if desc then
				desc.Text = item.description
			end
			local price = card:FindFirstChild("Price")
			if price then
				price.Text = "🪙 " .. item.price
			end
			local rarity = card:FindFirstChild("Rarity")
			if rarity then
				rarity.Text = item.rarity
				rarity.TextColor3 = item.color
			end
			local buy = card:FindFirstChild("BuyButton")
			local ownedBadge = card:FindFirstChild("OwnedBadge")
			local owned = ClientState.ownsItem(itemId)
			local consumableCount = ClientState.consumableCount(itemId)
			if ownedBadge then
				ownedBadge.Visible = owned and not item.stackable
				if item.stackable and consumableCount > 0 then
					ownedBadge.Text = "У тебя: " .. consumableCount
					ownedBadge.Visible = true
				end
			end
			if buy then
				buy.Text = (owned and not item.stackable) and "КУПЛЕНО" or (item.stackable and "КУПИТЬ ЕЩЁ" or "КУПИТЬ")
				buy.BackgroundColor3 = owned and not item.stackable and Color3.fromRGB(40, 60, 50) or Color3.fromRGB(60, 46, 120)
				buy.AutoButtonColor = false
				hover(buy, 0.3)
				buy.MouseButton1Click:Connect(function()
					if owned and not item.stackable then
						return
					end
					clickSound()
					Remotes.get("BuyItem"):FireServer(itemId)
				end)
			end
			card.MouseEnter:Connect(function()
				local stroke = card:FindFirstChildWhichIsA("UIStroke")
				if stroke then
					ClientState.tween(stroke, 0.2, { Transparency = 0 })
				end
			end)
			card.MouseLeave:Connect(function()
				local stroke = card:FindFirstChildWhichIsA("UIStroke")
				if stroke then
					ClientState.tween(stroke, 0.2, { Transparency = 0.45 })
				end
			end)
		end
	end
end

------------------------------------------------------------------
-- Инвентарь
------------------------------------------------------------------
function MenuController.renderInventory()
	local gui = ClientState.getGui("InventoryGUI")
	local list = gui and gui:FindFirstChild("ItemList", true)
	local template = list and list:FindFirstChild("CardTemplate")
	if not list or not template then
		return
	end
	clearList(list, "CardTemplate")
	local profile = ClientState.profile
	if not profile then
		return
	end
	local index = 0
	for _, itemId in ipairs(ItemDefinitions.Order) do
		local item = ItemDefinitions.get(itemId)
		local owned = profile.owned[itemId]
		local count = profile.consumables[itemId]
		if item and (owned or (count and count > 0)) then
			index += 1
			local equipped = profile.equipped[itemId] == true
			local card = template:Clone()
			card.Name = "Card_" .. itemId
			card.Visible = true
			card.LayoutOrder = index
			card.Parent = list
			local icon = card:FindFirstChild("Icon")
			if icon then
				icon.Text = item.icon
				icon.TextColor3 = item.color
			end
			local name = card:FindFirstChild("Name")
			if name then
				name.Text = item.name .. (count and (" ×" .. count) or "")
				name.TextColor3 = item.color
			end
			local desc = card:FindFirstChild("Desc")
			if desc then
				desc.Text = item.description
			end
			local countLabel = card:FindFirstChild("Count")
			if countLabel then
				countLabel.Text = count and ("Количество: " .. count) or item.rarity
			end
			local toggle = card:FindFirstChild("ToggleButton")
			if toggle then
				if item.stackable then
					toggle.Text = "ИСПОЛЬЗОВАТЬ"
					toggle.BackgroundColor3 = Color3.fromRGB(40, 70, 90)
					toggle.MouseButton1Click:Connect(function()
						clickSound()
						Remotes.get("UseItem"):FireServer(itemId)
					end)
				else
					toggle.Text = equipped and "СНЯТЬ" or "НАДЕТЬ"
					toggle.BackgroundColor3 = equipped and Color3.fromRGB(60, 90, 70) or Color3.fromRGB(60, 46, 120)
					toggle.MouseButton1Click:Connect(function()
						clickSound()
						Remotes.get("EquipItem"):FireServer(itemId)
					end)
				end
				hover(toggle, 0.3)
			end
			local state = card:FindFirstChild("StateLabel")
			if state then
				state.Text = equipped and "✔ НАДЕТО" or (item.stackable and "" or "НЕ НАДЕТО")
				state.TextColor3 = equipped and Color3.fromRGB(140, 240, 180) or Color3.fromRGB(150, 150, 180)
			end
		end
	end
	local equippedLabel = gui and gui:FindFirstChild("EquippedLabel", true)
	if equippedLabel then
		local names = {}
		for itemId in pairs(profile.equipped) do
			local item = ItemDefinitions.get(itemId)
			if item then
				table.insert(names, item.name)
			end
		end
		equippedLabel.Text = #names > 0 and ("Экипировано: " .. table.concat(names, ", ")) or "Экипировано: ничего"
	end
	if index == 0 then
		local empty = gui and gui:FindFirstChild("EmptyLabel", true)
		if empty then
			empty.Visible = true
		end
	end
end

------------------------------------------------------------------
-- Достижения
------------------------------------------------------------------
function MenuController.renderAchievements()
	local gui = ClientState.getGui("AchievementsGUI")
	local list = gui and gui:FindFirstChild("List", true)
	local template = list and list:FindFirstChild("RowTemplate")
	if not list or not template then
		return
	end
	clearList(list, "RowTemplate")
	local profile = ClientState.profile
	local unlockedCount = 0
	for index, achievement in ipairs(AchievementDefinitions.List) do
		local unlocked = profile and profile.achievements[achievement.id] == true
		if unlocked then
			unlockedCount += 1
		end
		local row = template:Clone()
		row.Name = "Row_" .. achievement.id
		row.Visible = true
		row.LayoutOrder = index
		row.Parent = list
		local icon = row:FindFirstChild("Icon")
		if icon then
			icon.Text = achievement.icon
			icon.TextTransparency = unlocked and 0 or 0.6
		end
		local name = row:FindFirstChild("Name")
		if name then
			name.Text = achievement.name
			name.TextColor3 = unlocked and Color3.fromRGB(200, 255, 220) or Color3.fromRGB(170, 170, 200)
		end
		local desc = row:FindFirstChild("Desc")
		if desc then
			desc.Text = achievement.description
			desc.TextTransparency = unlocked and 0.15 or 0.55
		end
		local state = row:FindFirstChild("State")
		if state then
			state.Text = unlocked and "✔ ПОЛУЧЕНО" or "🔒 ЗАКРЫТО"
			state.TextColor3 = unlocked and Color3.fromRGB(140, 240, 180) or Color3.fromRGB(140, 140, 170)
		end
		row.BackgroundTransparency = unlocked and 0.15 or 0.55
	end
	local progress = gui and gui:FindFirstChild("ProgressLabel", true)
	if progress then
		progress.Text = "Открыто: " .. unlockedCount .. " / " .. #AchievementDefinitions.List
	end
end

------------------------------------------------------------------
-- Статистика
------------------------------------------------------------------
function MenuController.renderStats()
	local profile = ClientState.profile
	if not profile then
		return
	end
	local stats = profile.stats
	local function setText(name, value)
		local label = ClientState.el("StatsGUI", name)
		if label then
			label.Text = tostring(value)
		end
	end
	local timeMinutes = math.floor((stats.playtime or 0) / 60)
	setText("DreamsValue", stats.dreams or 0)
	setText("WinsValue", stats.wins or 0)
	setText("LossesValue", stats.losses or 0)
	setText("QuestsValue", stats.quests or 0)
	setText("CoinsValue", "🪙 " .. (stats.coinsEarned or 0))
	setText("TimeValue", timeMinutes .. " мин")
	setText("AchievementsValue", (function()
		local n = 0
		for _ in pairs(profile.achievements) do
			n += 1
		end
		return n
	end)())
	setText("BestTimeValue", ClientState.fmtTime(stats.bestTime or 0))
	local visited = 0
	for _ in pairs(stats.dreamsVisited or {}) do
		visited += 1
	end
	setText("DreamsVisitedValue", visited .. " / 10")
	local winrate = 0
	if (stats.dreams or 0) > 0 then
		winrate = math.floor((stats.wins or 0) / stats.dreams * 100)
	end
	setText("WinrateValue", winrate .. "%")
end

------------------------------------------------------------------
-- Настройки
------------------------------------------------------------------
local function bindSlider(frame, value, onChange)
	if not frame then
		return
	end
	local fill = frame:FindFirstChild("Fill")
	local knob = frame:FindFirstChild("Knob", true) or fill
	local valueLabel = frame:FindFirstChild("Value")
	local current = math.clamp(value or 0.5, 0, 1)
	local dragging = false

	local function apply(alpha, final)
		current = math.clamp(alpha, 0, 1)
		if fill then
			fill.Size = UDim2.new(current, 0, 1, 0)
		end
		if knob and knob ~= fill then
			knob.Position = UDim2.new(current, 0, 0.5, 0)
		end
		if valueLabel then
			valueLabel.Text = tostring(math.floor(current * 100)) .. "%"
		end
		if final then
			onChange(current)
		end
	end

	apply(current, false)

	frame.InputBegan:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch then
			dragging = true
			local alpha = (input.Position.X - frame.AbsolutePosition.X) / math.max(1, frame.AbsoluteSize.X)
			apply(alpha, true)
		end
	end)
	UserInputService.InputChanged:Connect(function(input)
		if dragging and (input.UserInputType == Enum.UserInputType.MouseMovement or input.UserInputType == Enum.UserInputType.Touch) then
			local alpha = (input.Position.X - frame.AbsolutePosition.X) / math.max(1, frame.AbsoluteSize.X)
			apply(alpha, false)
		end
	end)
	UserInputService.InputEnded:Connect(function(input)
		if dragging and (input.UserInputType == Enum.UserInputType.MouseButton1 or input.UserInputType == Enum.UserInputType.Touch) then
			dragging = false
			onChange(current)
		end
	end)
end

local function bindToggle(button, initial, key)
	if not button then
		return
	end
	local state = initial == true
	local function render()
		button.Text = (button:GetAttribute("BaseText") or button.Text:gsub(":.*", "")) .. ": " .. (state and "ВКЛ" or "ВЫКЛ")
		button.TextColor3 = state and Color3.fromRGB(150, 250, 190) or Color3.fromRGB(200, 160, 170)
	end
	button:SetAttribute("BaseText", button.Text)
	render()
	button.MouseButton1Click:Connect(function()
		state = not state
		render()
		clickSound()
		Remotes.get("UpdateSettings"):FireServer(key, state)
	end)
end

function MenuController.renderSettings()
	local settings = ClientState.settings
	local gui = ClientState.getGui("SettingsGUI")
	if not gui then
		return
	end
	bindSlider(gui:FindFirstChild("MusicSlider", true), settings.musicVolume, function(value)
		Remotes.get("UpdateSettings"):FireServer("musicVolume", value)
		ClientState.settings.musicVolume = value
		SoundController.applyVolumes()
	end)
	bindSlider(gui:FindFirstChild("SfxSlider", true), settings.sfxVolume, function(value)
		Remotes.get("UpdateSettings"):FireServer("sfxVolume", value)
		ClientState.settings.sfxVolume = value
		SoundController.applyVolumes()
	end)
	bindSlider(gui:FindFirstChild("SensitivitySlider", true), settings.sensitivity, function(value)
		Remotes.get("UpdateSettings"):FireServer("sensitivity", value)
		ClientState.settings.sensitivity = value
		CameraController.applySensitivity()
	end)
	bindSlider(gui:FindFirstChild("ScareSlider", true), settings.scareIntensity, function(value)
		Remotes.get("UpdateSettings"):FireServer("scareIntensity", value)
		ClientState.settings.scareIntensity = value
	end)
	bindToggle(gui:FindFirstChild("HintsToggle", true), settings.hints, "hints")
	bindToggle(gui:FindFirstChild("CameraShakeToggle", true), settings.cameraShake, "cameraShake")
	local scareToggle = gui:FindFirstChild("ScareToggle", true)
	if scareToggle then
		local enabled = (settings.scareIntensity or 1) > 0.25
		local baseText = scareToggle.Text
		local function renderScare()
			scareToggle.Text = baseText .. ": " .. (enabled and "ВКЛ" or "ВЫКЛ")
			scareToggle.TextColor3 = enabled and Color3.fromRGB(255, 170, 190) or Color3.fromRGB(150, 150, 180)
		end
		renderScare()
		scareToggle.MouseButton1Click:Connect(function()
			enabled = not enabled
			renderScare()
			clickSound()
			ClientState.settings.scareIntensity = enabled and 1 or 0.15
			Remotes.get("UpdateSettings"):FireServer("scareIntensity", ClientState.settings.scareIntensity)
		end)
	end
	for _, pair in ipairs({ { "QualityLow", "Низкое" }, { "QualityMedium", "Средний" }, { "QualityHigh", "Высокое" } }) do
		local button = gui:FindFirstChild(pair[1], true)
		if button then
			button.TextColor3 = settings.quality == pair[2] and Color3.fromRGB(180, 255, 210) or Color3.fromRGB(190, 190, 220)
			button.MouseButton1Click:Connect(function()
				clickSound()
				Remotes.get("UpdateSettings"):FireServer("quality", pair[2])
				ClientState.settings.quality = pair[2]
				MenuController.renderSettings()
				ClientState.emit("QualityChanged", pair[2])
			end)
		end
	end
	local reset = gui:FindFirstChild("ResetButton", true)
	if reset then
		reset.MouseButton1Click:Connect(function()
			clickSound()
			Remotes.get("ResetSettings"):FireServer()
		end)
	end
end

------------------------------------------------------------------
-- Инициализация
------------------------------------------------------------------
function MenuController.start()
	if buttonsReady then
		return
	end
	buttonsReady = true

	local menu = ClientState.getGui("MainMenu")
	if menu then
		menu.Enabled = true
	end

	local playButton = makeButton("PlayButton")
	if playButton then
		playButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("DifficultyGUI")
		end)
	end
	local shopButton = makeButton("ShopButton")
	if shopButton then
		shopButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("ShopGUI")
		end)
	end
	local inventoryButton = makeButton("InventoryButton")
	if inventoryButton then
		inventoryButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("InventoryGUI")
		end)
	end
	local achievementsButton = makeButton("AchievementsButton")
	if achievementsButton then
		achievementsButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("AchievementsGUI")
		end)
	end
	local statsButton = makeButton("StatsButton")
	if statsButton then
		statsButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("StatsGUI")
		end)
	end
	local settingsButton = makeButton("SettingsButton")
	if settingsButton then
		settingsButton.MouseButton1Click:Connect(function()
			MenuController.openPanelByName("SettingsGUI")
		end)
	end

	for _, guiName in ipairs(PANELS) do
		local gui = ClientState.getGui(guiName)
		if gui then
			gui.Enabled = false
			local close = gui:FindFirstChild("CloseButton", true) or gui:FindFirstChild("BackButton", true)
			if close then
				hover(close)
				close.MouseButton1Click:Connect(function()
					clickSound()
					MenuController.closePanels()
				end)
			end
		end
	end

	ClientState.on("ClosePanel", function()
		if openPanel then
			MenuController.closePanels()
		end
	end)

	ClientState.on("Profile", function()
		MenuController.refreshProfile()
	end)

	ClientState.on("Phase", function(phase)
		if phase == "menu" then
			MenuController.show()
		elseif phase == "sleeping" or phase == "dream" or phase == "waking" then
			MenuController.hide()
		end
	end)

	ClientState.on("Toast", function(payload)
		-- HUD уже показывает уведомления; в меню тоже полезно видеть покупки
		if ClientState.phase == "menu" then
			local label = ClientState.el("MainMenu", "MenuToast")
			if label then
				label.Text = payload.text
				label.TextTransparency = 0
				label.Visible = true
				ClientState.tween(label, 0.3, { TextTransparency = 0 })
				task.delay(3, function()
					ClientState.tween(label, 0.6, { TextTransparency = 1 })
				end)
			end
		end
	end)

	Remotes.get("PurchaseResult").OnClientEvent:Connect(function(result)
		if result.ok then
			SoundController.play("Purchase")
			ClientState.toast(result.message or "Куплено", Color3.fromRGB(150, 255, 190), "🛍️")
		else
			ClientState.toast(result.message or "Покупка не удалась", Color3.fromRGB(255, 140, 140), "❌")
		end
		MenuController.refreshProfile()
	end)

	Remotes.get("EquipResult").OnClientEvent:Connect(function(result)
		ClientState.toast(result.message or "", result.ok and Color3.fromRGB(150, 255, 190) or Color3.fromRGB(255, 140, 140), "🎒")
		MenuController.refreshProfile()
	end)

	Remotes.get("AchievementUnlocked").OnClientEvent:Connect(function(data)
		SoundController.play("Win", 0.6)
		ClientState.toast("Достижение: " .. data.name .. " (+150 🪙)", Color3.fromRGB(255, 230, 160), data.icon or "🏆")
	end)

	-- Анимация заголовка
	task.spawn(function()
		local title = ClientState.el("MainMenu", "Title")
		while true do
			task.wait(3)
			if title and title.Parent then
				title.Rotation = 1
				ClientState.tween(title, 0.4, { Rotation = 0 })
			end
		end
	end)
end

return MenuController
