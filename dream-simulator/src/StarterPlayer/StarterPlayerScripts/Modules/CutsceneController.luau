--!nocheck
--[[
	CutsceneController.luau
	Анимация засыпания (кровать → полёт через пространство снов → белая вспышка)
	и анимация пробуждения (потолок → персонаж садится → кадр становится чётким).
]]

local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")
local Workspace = game:GetService("Workspace")
local Lighting = game:GetService("Lighting")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Config = require(ReplicatedStorage.SharedModules.Config)
local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local DreamDefinitions = require(ReplicatedStorage.SharedModules.DreamDefinitions)

local ClientState = require(script.Parent.ClientState)
local CameraController = require(script.Parent.CameraController)
local SoundController = require(script.Parent.SoundController)
local HUDController = require(script.Parent.HUDController)
local EffectsController = require(script.Parent.EffectsController)

local CutsceneController = {}

local flightFolder = nil

------------------------------------------------------------------
-- Вспомогательные элементы
------------------------------------------------------------------
local function gui()
	return ClientState.getGui("CutsceneGUI")
end

local function cutsceneEl(name)
	local g = gui()
	if not g then
		return nil
	end
	return g:FindFirstChild(name, true)
end

local function setText(name, text)
	local label = cutsceneEl(name)
	if label and label:IsA("TextLabel") then
		label.Text = text
	end
end

local function showFade(alpha, duration)
	local fade = cutsceneEl("Fade")
	if not fade then
		return
	end
	fade.Visible = true
	fade.BackgroundTransparency = fade.BackgroundTransparency
	ClientState.tween(fade, duration or 0.6, { BackgroundTransparency = alpha })
end

local function showFlash(duration)
	local flash = cutsceneEl("WhiteFlash")
	if not flash then
		return
	end
	flash.Visible = true
	flash.BackgroundTransparency = 0
	ClientState.tween(flash, duration or 2, { BackgroundTransparency = 1 })
	task.delay((duration or 2) + 0.2, function()
		if flash then
			flash.Visible = false
		end
	end)
end

local function letterbox(enabled)
	for _, name in ipairs({ "LetterboxTop", "LetterboxBottom" }) do
		local frame = cutsceneEl(name)
		if frame then
			frame.Visible = true
			local closed = name == "LetterboxTop" and UDim2.new(1, 0, 0.12, 0) or UDim2.new(1, 0, 0.12, 0)
			if enabled then
				frame.Size = UDim2.new(1, 0, 0, 0)
				ClientState.tween(frame, 0.8, { Size = closed }, Enum.EasingStyle.Quint)
			else
				ClientState.tween(frame, 0.6, { Size = UDim2.new(1, 0, 0, 0) })
			end
		end
	end
end

local function bedroomPart(name)
	local bedroom = Workspace:FindFirstChild("MenuBedroom")
	if not bedroom then
		return nil
	end
	local found = bedroom:FindFirstChild(name, true)
	if found and found:IsA("BasePart") then
		return found
	end
	return nil
end

local function playerCharacter()
	local character = ClientState.player.Character
	if not character or not character:FindFirstChild("HumanoidRootPart") then
		return nil
	end
	return character
end

local function lockCharacter(locked)
	local character = playerCharacter()
	if not character then
		return
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid then
		humanoid.WalkSpeed = locked and 0 or Config.BaseWalkSpeed
		humanoid.JumpPower = locked and 0 or Config.JumpPower
		humanoid.UseJumpPower = true
	end
end

------------------------------------------------------------------
-- Полёт через пространство снов
------------------------------------------------------------------
local function buildFlightSpace()
	if flightFolder then
		flightFolder:Destroy()
	end
	flightFolder = Instance.new("Folder")
	flightFolder.Name = "DreamFlight"
	flightFolder.Parent = Workspace

	local baseY = 4000
	local function part(size, cframe, color, material, transparency)
		local p = Instance.new("Part")
		p.Anchored = true
		p.CanCollide = false
		p.CanQuery = false
		p.CanTouch = false
		p.Size = size
		p.CFrame = cframe
		p.Color = color
		p.Material = material or Enum.Material.Neon
		p.Transparency = transparency or 0
		p.Parent = flightFolder
		return p
	end

	-- Звёзды
	for i = 1, 160 do
		local x = math.random(-260, 260)
		local y = baseY + math.random(-160, 160)
		local z = -math.random(0, 900)
		local size = math.random(3, 9) / 10
		local star = part(Vector3.new(size, size, size), CFrame.new(x, y, z), Color3.fromRGB(255, 250, 230), Enum.Material.Neon)
		star.Shape = Enum.PartType.Ball
	end

	-- Облака
	for i = 1, 42 do
		local x = math.random(-140, 140)
		local y = baseY + math.random(-50, 50)
		local z = -math.random(0, 800)
		local scale = math.random(20, 55)
		local cloud = part(Vector3.new(scale * 1.8, scale * 0.7, scale), CFrame.new(x, y, z), Color3.fromRGB(190, 180, 255), Enum.Material.SmoothPlastic, 0.72)
		cloud.Shape = Enum.PartType.Ball
	end

	-- Портал
	for i = 1, 8 do
		local x = math.random(-90, 90)
		local y = baseY + math.random(-40, 40)
		local z = -math.random(60, 760)
		local size = math.random(14, 26)
		local ring = part(Vector3.new(1.2, size, size), CFrame.new(x, y, z) * CFrame.Angles(0, math.rad(math.random(0, 180)), math.rad(90)), Color3.fromRGB(190, 150, 255), Enum.Material.Neon, 0.1)
		ring.Shape = Enum.PartType.Cylinder
		local core = part(Vector3.new(size * 0.72, size * 0.72, 1), CFrame.new(x, y, z) * CFrame.Angles(0, 0, 0), Color3.fromRGB(120, 90, 220), Enum.Material.ForceField, 0.45)
		core.Shape = Enum.PartType.Ball
		local light = Instance.new("PointLight")
		light.Color = Color3.fromRGB(170, 140, 255)
		light.Brightness = 3
		light.Range = 60
		light.Parent = ring
	end

	-- Летающие часы
	for i = 1, 10 do
		local x = math.random(-70, 70)
		local y = baseY + math.random(-45, 45)
		local z = -math.random(40, 820)
		local size = math.random(10, 18)
		local face = part(Vector3.new(0.8, size, size), CFrame.new(x, y, z) * CFrame.Angles(0, 0, math.rad(90)), Color3.fromRGB(255, 240, 200), Enum.Material.Neon, 0.2)
		face.Shape = Enum.PartType.Cylinder
		local hand = part(Vector3.new(size * 0.7, 0.6, 0.6), CFrame.new(x, y, z + 1), Color3.fromRGB(90, 70, 40), Enum.Material.Neon)
		hand.CFrame = CFrame.new(face.Position + face.CFrame.LookVector * 0.6) * CFrame.Angles(0, 0, math.rad(math.random(0, 360))) * CFrame.new(size * 0.35, 0, 0)
	end

	-- Острова
	for i = 1, 12 do
		local x = math.random(-120, 120)
		local y = baseY + math.random(-70, 20)
		local z = -math.random(80, 860)
		local island = part(Vector3.new(math.random(16, 40), 4, math.random(16, 40)), CFrame.new(x, y, z), Color3.fromRGB(90, 80, 140), Enum.Material.Slate, 0)
		island.CFrame = island.CFrame * CFrame.Angles(math.rad(math.random(-20, 20)), math.rad(math.random(0, 360)), math.rad(math.random(-20, 20)))
		local glow = part(Vector3.new(2, 8, 2), CFrame.new(x, y + 6, z), Color3.fromRGB(120, 255, 220), Enum.Material.Neon, 0.2)
	end

	-- Странные предметы
	for i = 1, 24 do
		local x = math.random(-80, 80)
		local y = baseY + math.random(-40, 40)
		local z = -math.random(20, 840)
		local item = part(Vector3.new(2, 2, 2), CFrame.new(x, y, z) * CFrame.Angles(math.random(), math.random(), math.random()),
			Color3.fromRGB(math.random(120, 255), math.random(100, 255), math.random(150, 255)), Enum.Material.Neon, 0.1)
		item.Shape = (i % 3 == 0) and Enum.PartType.Ball or Enum.PartType.Block
	end

	-- Частицы вокруг камеры
	local holder = part(Vector3.new(0.2, 0.2, 0.2), CFrame.new(0, baseY, 10), Color3.new(1, 1, 1), Enum.Material.SmoothPlastic, 1)
	holder.CanCollide = false
	local emitter = Instance.new("ParticleEmitter")
	emitter.Texture = "rbxassetid://241876428"
	emitter.Rate = 160
	emitter.Lifetime = NumberRange.new(1.5, 3)
	emitter.Speed = NumberRange.new(1, 6)
	emitter.Size = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0.9),
		NumberSequenceKeypoint.new(1, 0),
	})
	emitter.Transparency = NumberSequence.new({
		NumberSequenceKeypoint.new(0, 0.1),
		NumberSequenceKeypoint.new(1, 1),
	})
	emitter.Color = ColorSequence.new(Color3.fromRGB(220, 200, 255), Color3.fromRGB(120, 220, 255))
	emitter.LightEmission = 1
	emitter.LightInfluence = 0
	emitter.SpreadAngle = Vector2.new(40, 40)
	emitter.Parent = holder

	return baseY
end

local runToken = 0

-- Отложенные шаги катсцены не должны «срабатывать» в следующем сне:
-- каждая катсцена увеличивает токен, устаревшие колбэки молча выходят.
local function later(delay, fn)
	local token = runToken
	task.delay(delay, function()
		if token == runToken then
			fn()
		end
	end)
end

local function playFlight(duration)
	local baseY = buildFlightSpace()
	local camera = Workspace.CurrentCamera
	local startAt = os.clock()
	local start = CFrame.new(0, baseY, 40)
	local finish = CFrame.new(0, baseY + 10, -620)

	CameraController.setMode("script")
	local done = false
	local connection
	connection = RunService.RenderStepped:Connect(function()
		if done then
			return
		end
		local alpha = math.clamp((os.clock() - startAt) / duration, 0, 1)
		local eased = 1 - (1 - alpha) ^ 2
		local position = start.Position:Lerp(finish.Position, eased) + Vector3.new(math.sin(alpha * 9) * 6, math.cos(alpha * 7) * 4, 0)
		local look = position + Vector3.new(math.sin(alpha * 4) * 12, math.cos(alpha * 3) * 8, -60)
		camera.CFrame = CFrame.lookAt(position, look)
		camera.FieldOfView = 70 + math.sin(alpha * 8) * 10
	end)

	task.delay(duration, function()
		done = true
		if connection then
			connection:Disconnect()
		end
		camera.FieldOfView = 78
		if flightFolder then
			flightFolder:Destroy()
			flightFolder = nil
		end
	end)
end

------------------------------------------------------------------
-- Засыпание
------------------------------------------------------------------
function CutsceneController.playSleep(payload)
	runToken += 1
	ClientState.setPhase("sleeping")
	ClientState.dream = payload
	local guiRef = gui()
	if guiRef then
		guiRef.Enabled = true
	end
	for _, name in ipairs({ "DreamText", "Subtitle", "Heartbeat" }) do
		local element = cutsceneEl(name)
		if element then
			element.Visible = false
		end
	end
	local fade = cutsceneEl("Fade")
	if fade then
		fade.Visible = true
		fade.BackgroundTransparency = 1
	end

	setText("Subtitle", payload.dreamName .. " — " .. payload.difficultyName)
	letterbox(true)
	lockCharacter(true)
	SoundController.setMenu()
	SoundController.playMusic("DreamMusic")

	-- Камера у кровати
	local bedPoint = bedroomPart("BedPoint")
	local cameraPoint = bedroomPart("SleepCameraPoint")
	local camera = Workspace.CurrentCamera
	CameraController.setMode("script")
	if cameraPoint then
		camera.CFrame = cameraPoint.CFrame
	else
		camera.CFrame = CFrame.new(6, 6, 6)
	end
	local lookTarget = bedPoint and bedPoint.Position or Vector3.new(0, 3, 0)

	-- Персонаж идёт к кровати, садится, ложится
	task.spawn(function()
		local character = playerCharacter()
		if character and bedPoint then
			local root = character:FindFirstChild("HumanoidRootPart")
			local humanoid = character:FindFirstChildOfClass("Humanoid")
			local target = CFrame.new(bedPoint.Position + Vector3.new(0, 2.2, 0), bedPoint.Position + Vector3.new(0, 2.2, 4))
			local start = root.CFrame
			local time = 2.2
			local startAt = os.clock()
			while os.clock() - startAt < time do
				local alpha = math.clamp((os.clock() - startAt) / time, 0, 1)
				local eased = alpha * alpha * (3 - 2 * alpha)
				pcall(function()
					character:PivotTo(start:Lerp(target, eased))
				end)
				task.wait()
			end
			-- Ложится
			local lying = CFrame.new(bedPoint.Position + Vector3.new(0, 1.4, 0)) * CFrame.Angles(math.rad(-90), 0, 0)
			pcall(function()
				character:PivotTo(lying)
			end)
			if humanoid then
				humanoid.WalkSpeed = 0
			end
		end
	end)

	-- Камера медленно приближается и темнеет
	task.spawn(function()
		local startAt = os.clock()
		local duration = 3.2
		while os.clock() - startAt < duration do
			local alpha = math.clamp((os.clock() - startAt) / duration, 0, 1)
			local position = (cameraPoint and cameraPoint.Position or Vector3.new(7, 6, 7)):Lerp(lookTarget + Vector3.new(2.5, 2.5, 3.5), alpha * 0.75)
			camera.CFrame = CFrame.lookAt(position, lookTarget)
			task.wait()
		end
		CameraController.setMode("script")
	end)

	later(1.2, function()
		setText("DreamText", "ЗАСЫПАНИЕ...")
		local text = cutsceneEl("DreamText")
		if text then
			text.Visible = true
			text.TextTransparency = 1
			ClientState.tween(text, 1.2, { TextTransparency = 0 })
		end
		SoundController.heartbeat(true)
	end)

	later(3.4, function()
		showFade(0, 1.4)
		local text = cutsceneEl("DreamText")
		if text then
			ClientState.tween(text, 0.8, { TextTransparency = 1 })
			later(0.9, function()
				text.Visible = false
			end)
		end
	end)

	-- Полёт
	later(4.6, function()
		playFlight(4.2)
		SoundController.heartbeat(false)
		setText("DreamText", "ТЫ ПОГРУЖАЕШЬСЯ В СОН...")
		local text = cutsceneEl("DreamText")
		if text then
			text.Visible = true
			text.TextTransparency = 1
			ClientState.tween(text, 0.8, { TextTransparency = 0 })
		end
	end)

	later(8.4, function()
		showFlash(1.6)
		local text = cutsceneEl("DreamText")
		if text then
			ClientState.tween(text, 0.4, { TextTransparency = 1 })
		end
	end)
end

------------------------------------------------------------------
-- Пробуждение
------------------------------------------------------------------
function CutsceneController.playWake(won)
	runToken += 1
	ClientState.setPhase("waking")
	local guiRef = gui()
	if guiRef then
		guiRef.Enabled = true
	end
	SoundController.setWaking(won)
	letterbox(true)

	local fade = cutsceneEl("Fade")
	if fade then
		fade.Visible = true
		fade.BackgroundTransparency = 0
	end

	local camera = Workspace.CurrentCamera
	local bedPoint = bedroomPart("BedPoint")
	CameraController.setMode("script")
	local eye = bedPoint and (bedPoint.Position + Vector3.new(0, 2.6, 0)) or Vector3.new(0, 3, 0)
	camera.CFrame = CFrame.lookAt(eye, eye + Vector3.new(0, 12, 2))

	-- Размытый потолок
	showFade(0.82, 2.2)
	later(0.8, function()
		local dof = Lighting:FindFirstChild("DreamDOF")
		if dof then
			dof.FarIntensity = 1
			dof.InFocusRadius = 8
		end
		SoundController.heartbeat(true)
	end)

	-- Садится и картинка становится чёткой
	later(2.6, function()
		local character = playerCharacter()
		if character and bedPoint then
			local sit = CFrame.new(bedPoint.Position + Vector3.new(0, 2.4, 0), bedPoint.Position + Vector3.new(0, 2.4, 4))
			pcall(function()
				character:PivotTo(sit)
			end)
		end
		local dof = Lighting:FindFirstChild("DreamDOF")
		if dof then
			dof.FarIntensity = 0.25
			dof.InFocusRadius = 40
		end
	end)

	later(4.2, function()
		lockCharacter(false)
		SoundController.heartbeat(false)
		letterbox(false)
		showFade(0, 1.6)
		later(1.8, function()
			if guiRef then
				guiRef.Enabled = false
			end
			local fadeElement = cutsceneEl("Fade")
			if fadeElement then
				fadeElement.Visible = false
			end
			ClientState.setCursorFree(true)
			ClientState.setPhase("result")
			if ClientState.result then
				HUDController.showResult(ClientState.result)
			end
		end)
	end)
end

------------------------------------------------------------------
-- События сервера
------------------------------------------------------------------
function CutsceneController.start()
	Remotes.get("SleepSequence").OnClientEvent:Connect(function(payload)
		CutsceneController.playSleep(payload)
	end)

	Remotes.get("DreamStarted").OnClientEvent:Connect(function(payload)
		ClientState.dream = payload
		ClientState.bonuses = payload.bonuses or {}
		ClientState.timer.remaining = payload.duration or Config.DreamDuration
		ClientState.timer.total = payload.duration or Config.DreamDuration
		if payload.settings then
			for key, value in pairs(payload.settings) do
				ClientState.settings[key] = value
			end
		end
		local guiRef = gui()
		if guiRef then
			guiRef.Enabled = false
		end
		showFlash(0.9)
		ClientState.setPhase("dream")
		CameraController.setMode("dream")
		CameraController.setFieldOfView(80, 0.1)
		SoundController.setDream()
		local dream = DreamDefinitions.get(payload.dreamId)
		if dream then
			if dream.weather == "Rain" then
				EffectsController.setWeather("rain")
			elseif dream.weather == "Snow" then
				EffectsController.setWeather("snow")
			elseif dream.weather == "Storm" then
				EffectsController.setWeather("rain")
			end
		end
	end)

	Remotes.get("DreamEnded").OnClientEvent:Connect(function(result)
		ClientState.result = result
		CutsceneController.playWake(result.won == true)
	end)

	Remotes.get("DreamStateChanged").OnClientEvent:Connect(function(data)
		if data.state == "menu" then
			ClientState.result = nil
			ClientState.portalPosition = nil
			ClientState.quests = {}
			HUDController.hideResult()
			ClientState.setPhase("menu")
			ClientState.emit("EnteredMenu")
		elseif data.state == "waking" then
			local guiRef = gui()
			if guiRef then
				guiRef.Enabled = true
			end
		end
	end)

	ClientState.on("ResultAction", function(action)
		if action == "retry" then
			local difficulty = ClientState.result and ClientState.result.difficulty or "Normal"
			ClientState.setPhase("menu")
			ClientState.result = nil
			ClientState.emit("RequestDream", difficulty)
			Remotes.get("RequestDream"):FireServer(difficulty)
		else
			ClientState.setPhase("menu")
			ClientState.result = nil
			Remotes.get("ReturnToMenu"):FireServer()
		end
	end)
end

return CutsceneController
