--!nocheck
--[[
	EffectsController.luau
	Атмосфера и страх: искажение картинки от низкого рассудка, скримеры,
	фонарик, погодные эффекты, галлюцинации, тряска камеры, качество графики.
]]

local RunService = game:GetService("RunService")
local Lighting = game:GetService("Lighting")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Workspace = game:GetService("Workspace")
local Debris = game:GetService("Debris")

local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ClientState = require(script.Parent.ClientState)
local SoundController = require(script.Parent.SoundController)
local CameraController = require(script.Parent.CameraController)

local EffectsController = {}

local flashlightPart
local flashlightLight
local flashlightGlow
local flashlightBody
local flashlightLens
local weatherEmitter
local weatherAttachment
local baseFogEnd = Lighting.FogEnd
local baseColor = nil
local activeTimers = {}
local scareCooldown = 0

local function getEffect(name)
	return Lighting:FindFirstChild(name)
end

------------------------------------------------------------------
-- Фонарик
------------------------------------------------------------------
local flashlightOn = false
local flashlightTarget = false
local flashlightFlicker = 0
local flashlightSway = 0

-- Параметры луча зависят от экипированного предмета.
local BEAMS = {
	basic = { range = 44, angle = 52, brightness = 1.9, color = Color3.fromRGB(255, 244, 214) },
	flashlight = { range = 62, angle = 58, brightness = 2.6, color = Color3.fromRGB(255, 248, 224) },
	lantern = { range = 76, angle = 84, brightness = 3.0, color = Color3.fromRGB(255, 214, 156) },
}

local function flashlightSettings()
	local tier = (ClientState.dream and ClientState.dream.flashlightTier)
		or (ClientState.vitals and ClientState.vitals.flashlightTier)
		or "basic"
	return BEAMS[tier] or BEAMS.basic
end

local function ensureFlashlight()
	if flashlightPart and flashlightPart.Parent then
		return
	end
	flashlightPart = Instance.new("Part")
	flashlightPart.Name = "ClientFlashlight"
	flashlightPart.Size = Vector3.new(0.2, 0.2, 0.2)
	flashlightPart.Transparency = 1
	flashlightPart.Anchored = true
	flashlightPart.CanCollide = false
	flashlightPart.CanQuery = false
	flashlightPart.CanTouch = false
	flashlightPart.CastShadow = false
	flashlightPart.Parent = Workspace

	flashlightLight = Instance.new("SpotLight")
	flashlightLight.Name = "Beam"
	flashlightLight.Face = Enum.NormalId.Front
	flashlightLight.Shadows = true
	flashlightLight.Enabled = false
	flashlightLight.Parent = flashlightPart

	-- Мягкая подсветка вокруг игрока, чтобы фонарик не выглядел «плоским».
	flashlightGlow = Instance.new("PointLight")
	flashlightGlow.Name = "Glow"
	flashlightGlow.Range = 16
	flashlightGlow.Brightness = 0.55
	flashlightGlow.Shadows = false
	flashlightGlow.Enabled = false
	flashlightGlow.Parent = flashlightPart

	-- Видимая «тушка» фонарика в руке: без неё свет выглядит беспричинным.
	flashlightBody = Instance.new("Part")
	flashlightBody.Name = "ClientFlashlightBody"
	flashlightBody.Size = Vector3.new(0.26, 0.26, 1.5)
	flashlightBody.Color = Color3.fromRGB(46, 48, 56)
	flashlightBody.Material = Enum.Material.Metal
	flashlightBody.Anchored = true
	flashlightBody.CanCollide = false
	flashlightBody.CanQuery = false
	flashlightBody.CanTouch = false
	flashlightBody.CastShadow = false
	flashlightBody.Transparency = 1
	flashlightBody.Parent = Workspace

	flashlightLens = Instance.new("Part")
	flashlightLens.Name = "ClientFlashlightLens"
	flashlightLens.Size = Vector3.new(0.3, 0.3, 0.12)
	flashlightLens.Color = Color3.fromRGB(255, 246, 214)
	flashlightLens.Material = Enum.Material.Neon
	flashlightLens.Anchored = true
	flashlightLens.CanCollide = false
	flashlightLens.CanQuery = false
	flashlightLens.CanTouch = false
	flashlightLens.CastShadow = false
	flashlightLens.Transparency = 1
	flashlightLens.Parent = Workspace
end

local function applyBeam()
	local beam = flashlightSettings()
	if flashlightLight then
		flashlightLight.Range = beam.range
		flashlightLight.Angle = beam.angle
		flashlightLight.Brightness = beam.brightness
		flashlightLight.Color = beam.color
		flashlightLight.Shadows = ClientState.quality() ~= "Низкое"
	end
	if flashlightGlow then
		flashlightGlow.Color = beam.color
		flashlightGlow.Range = beam.range * 0.3
		flashlightGlow.Brightness = beam.brightness * 0.22
	end
end

local function setFlashlight(enabled)
	ensureFlashlight()
	applyBeam()
	flashlightTarget = enabled == true
	flashlightOn = enabled == true
	-- Ставим источник света сразу в камеру, иначе первый кадр луч летит из нуля.
	local camera = Workspace.CurrentCamera
	if flashlightOn and camera then
		flashlightPart.CFrame = camera.CFrame * CFrame.new(0.35, -0.25, -0.4)
	end
	if flashlightLight then
		flashlightLight.Enabled = flashlightOn
	end
	if flashlightGlow then
		flashlightGlow.Enabled = flashlightOn
	end
	if flashlightBody then
		flashlightBody.Transparency = flashlightOn and 0 or 1
	end
	if flashlightLens then
		flashlightLens.Transparency = flashlightOn and 0 or 1
	end
	if flashlightOn then
		SoundController.play("Click", 0.5)
	end
end

-- Плавное мерцание при низкой батарее/рассудке + лёгкое покачивание луча.
local function updateFlashlight(dt)
	if not flashlightPart or not flashlightPart.Parent then
		return
	end
	if not ClientState.isDream() then
		if flashlightLight and flashlightLight.Enabled then
			flashlightLight.Enabled = false
			if flashlightGlow then
				flashlightGlow.Enabled = false
			end
			if flashlightBody then
				flashlightBody.Transparency = 1
			end
			if flashlightLens then
				flashlightLens.Transparency = 1
			end
		end
		return
	end
	local battery = (ClientState.vitals and ClientState.vitals.battery) or 100
	local sanity = (ClientState.vitals and ClientState.vitals.sanity) or 100
	flashlightSway += dt

	local camera = Workspace.CurrentCamera
	if camera and flashlightOn then
		-- Небольшое отставание луча от резких поворотов — «живой» фонарик.
		local target = camera.CFrame * CFrame.new(0.35, -0.25, -0.4)
		flashlightPart.CFrame = flashlightPart.CFrame:Lerp(target, math.min(1, dt * 12))
		-- Тушка и линза идут за камерой чуть иначе — с покачиванием при ходьбе.
		local bob = math.sin(flashlightSway * 6) * 0.02
		local handCframe = camera.CFrame * CFrame.new(0.62 + bob, -0.62 + bob * 0.6, -0.95)
			* CFrame.Angles(math.rad(-2), math.rad(-4), 0)
		if flashlightBody then
			flashlightBody.CFrame = handCframe
		end
		if flashlightLens then
			flashlightLens.CFrame = handCframe * CFrame.new(0, 0, -0.78)
		end
	end

	if not flashlightOn then
		return
	end

	-- Мерцание: слабая батарея или низкий рассудок
	local flickerChance = 0
	if battery < 20 then
		flickerChance += (20 - battery) / 20 * 0.5
	end
	if sanity < 40 then
		flickerChance += (40 - sanity) / 40 * 0.45
	end
	flashlightFlicker = math.max(0, flashlightFlicker - dt)
	if flickerChance > 0 and math.random() < flickerChance * dt * 3 then
		flashlightFlicker = 0.06 + math.random() * 0.16
	end
	local lit = flashlightFlicker <= 0
	if flashlightLight then
		flashlightLight.Enabled = lit
		local beam = flashlightSettings()
		local fade = 0.55 + 0.45 * math.clamp(battery / 100, 0, 1)
		flashlightLight.Brightness = beam.brightness * fade
		flashlightLight.Range = beam.range * (0.75 + 0.25 * math.clamp(battery / 100, 0, 1))
	end
	if flashlightGlow then
		flashlightGlow.Enabled = lit
	end
end

------------------------------------------------------------------
-- Рассудок
------------------------------------------------------------------
function EffectsController.applySanity(sanity)
	local color = getEffect("DreamColor")
	local bloom = getEffect("DreamBloom")
	local dof = getEffect("DreamDOF")
	local hud = ClientState.getGui("DreamHUD")
	local vignette = hud and hud:FindFirstChild("Vignette", true)
	local whisper = hud and hud:FindFirstChild("WhisperLabel", true)

	local factor = math.clamp(1 - sanity / 100, 0, 1) -- 0 = ясен, 1 = безумие
	if color then
		color.Contrast = 0.1 + factor * 0.6
		color.Saturation = -0.1 - factor * 0.55
		color.Brightness = factor * -0.06
	end
	if dof then
		local quality = ClientState.quality()
		local base = quality == "Низкое" and 0 or 0.45
		dof.FarIntensity = base + factor * 0.5
		dof.NearIntensity = quality == "Низкое" and 0 or factor * 0.35
		dof.InFocusRadius = 45 - factor * 25
	end
	if bloom then
		bloom.Intensity = 0.6 + factor * 0.6
	end
	if vignette then
		vignette.BackgroundTransparency = 1 - factor * 0.62
		vignette.Visible = factor > 0.05
	end
	if whisper then
		if factor > 0.55 then
			whisper.Visible = true
			whisper.TextTransparency = math.clamp(1 - (factor - 0.55) * 2.2, 0, 1)
			local texts = { "не оборачивайся", "ты уже здесь был", "мы ждём тебя", "проснись", "это не твой сон" }
			whisper.Text = texts[math.random(1, #texts)]
		else
			whisper.Visible = false
		end
	end
	if factor > 0.4 then
		CameraController.shake(0.25 * factor, 0.4)
	end
	-- Шёпот в наушниках тем громче, чем ниже рассудок.
	SoundController.whispers(factor > 0.5, factor)
	if factor > 0.86 and not EffectsController.lowSanityWarned then
		EffectsController.lowSanityWarned = true
		ClientState.toast("Рассудок на нуле — монстры совсем рядом", Color3.fromRGB(255, 140, 160), "🌀")
	elseif factor < 0.6 then
		EffectsController.lowSanityWarned = false
	end
end

------------------------------------------------------------------
-- Скримеры
------------------------------------------------------------------
function EffectsController.jumpscare(name, intensity)
	local level = math.clamp(ClientState.settings.scareIntensity or 1, 0, 1.6)
	if level < 0.15 then
		return
	end
	local hud = ClientState.getGui("DreamHUD")
	local frame = hud and hud:FindFirstChild("Jumpscare", true)
	local icon = frame and frame:FindFirstChild("JumpscareIcon")
	local label = frame and frame:FindFirstChild("JumpscareName")
	if frame then
		frame.Visible = true
		frame.BackgroundTransparency = 0.15
		if icon then
			icon.Text = "👁️"
		end
		if label then
			label.Text = name or "БЕГИ"
		end
		ClientState.tween(frame, 0.5, { BackgroundTransparency = 1 })
		task.delay(0.6, function()
			if frame then
				frame.Visible = false
			end
		end)
	end
	CameraController.shake(2.4 * (intensity or 1) * level, 0.9)
	SoundController.play("Scare")
	-- Красная вспышка
	local color = getEffect("DreamColor")
	if color then
		local tint = color.TintColor
		color.TintColor = Color3.fromRGB(255, 120, 120)
		task.delay(0.35, function()
			if color then
				color.TintColor = tint
			end
		end)
	end
end

function EffectsController.hallucination()
	if math.clamp(ClientState.settings.scareIntensity or 1, 0, 2) < 0.3 then
		return
	end
	local character = ClientState.player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not root then
		return
	end
	local camera = Workspace.CurrentCamera
	local look = camera.CFrame.LookVector
	local offset = Vector3.new(look.X, 0, look.Z).Unit * 18 + Vector3.new(math.random(-6, 6), 0, math.random(-6, 6))
	local position = root.Position + offset
	local part = Instance.new("Part")
	part.Name = "Hallucination"
	part.Anchored = true
	part.CanCollide = false
	part.CanQuery = false
	part.Size = Vector3.new(2, 6, 2)
	part.CFrame = CFrame.new(position + Vector3.new(0, 3, 0))
	part.Color = Color3.fromRGB(18, 12, 26)
	part.Material = Enum.Material.Neon
	part.Transparency = 0.35
	part.Parent = Workspace
	local eyes = Instance.new("PointLight")
	eyes.Color = Color3.fromRGB(255, 60, 80)
	eyes.Brightness = 2
	eyes.Range = 12
	eyes.Parent = part
	Debris:AddItem(part, 1.8)
	SoundController.play("Whisper", 0.8)
end

------------------------------------------------------------------
-- Погода
------------------------------------------------------------------
local function clearWeather()
	if weatherEmitter then
		weatherEmitter:Destroy()
		weatherEmitter = nil
	end
	if weatherAttachment then
		weatherAttachment:Destroy()
		weatherAttachment = nil
	end
end

local function setWeather(kind, duration)
	clearWeather()
	local camera = Workspace.CurrentCamera
	weatherAttachment = Instance.new("Attachment")
	weatherAttachment.Parent = camera

	weatherEmitter = Instance.new("ParticleEmitter")
	weatherEmitter.Parent = weatherAttachment
	weatherEmitter.LightInfluence = 0.2
	weatherEmitter.Rate = kind == "snow" and 90 or 160
	weatherEmitter.Speed = NumberRange.new(kind == "snow" and 6 or 40)
	weatherEmitter.Lifetime = NumberRange.new(1.2, 2)
	weatherEmitter.SpreadAngle = Vector2.new(20, 20)
	weatherEmitter.Acceleration = Vector3.new(0, kind == "snow" and -8 or -60, 0)
	if kind == "snow" then
		weatherEmitter.Size = NumberSequence.new({
			NumberSequenceKeypoint.new(0, 0.45),
			NumberSequenceKeypoint.new(1, 0.2),
		})
		weatherEmitter.Color = ColorSequence.new(Color3.fromRGB(240, 250, 255))
		weatherEmitter.Transparency = NumberSequence.new(0.2)
	else
		weatherEmitter.Size = NumberSequence.new({
			NumberSequenceKeypoint.new(0, 0.08),
			NumberSequenceKeypoint.new(1, 0.05),
		})
		weatherEmitter.Color = ColorSequence.new(Color3.fromRGB(170, 200, 230))
		weatherEmitter.Transparency = NumberSequence.new(0.35)
	end
	weatherEmitter.Texture = "rbxasset://textures/particles/sparkles_main.dds"
	if kind == "snow" then
		weatherEmitter.Texture = "rbxasset://textures/particles/smoke_main.dds"
	end
	if duration then
		task.delay(duration, clearWeather)
	end
end

------------------------------------------------------------------
-- События сна
------------------------------------------------------------------
function EffectsController.handleEvent(data)
	local id = data.id
	local duration = data.duration or 10
	if id == "rain" then
		setWeather("rain", duration)
	elseif id == "fog_boost" then
		Lighting.FogEnd = math.max(45, baseFogEnd * 0.45)
		task.delay(duration, function()
			Lighting.FogEnd = baseFogEnd
		end)
	elseif id == "red_moon" then
		local color = getEffect("DreamColor")
		if color then
			local tint = color.TintColor
			color.TintColor = Color3.fromRGB(255, 160, 150)
			task.delay(duration, function()
				if color then
					color.TintColor = tint
				end
			end)
		end
	elseif id == "whispers" then
		SoundController.play("Whisper")
		local hud = ClientState.getGui("DreamHUD")
		local whisper = hud and hud:FindFirstChild("WhisperLabel", true)
		if whisper then
			whisper.Visible = true
			whisper.Text = "ты не должен быть здесь"
			whisper.TextTransparency = 0
			task.delay(duration, function()
				if whisper then
					whisper.Visible = false
				end
			end)
		end
	elseif id == "scary_sound" then
		SoundController.play("Scare", 0.6)
		CameraController.shake(1.2, 0.8)
	elseif id == "footsteps" then
		SoundController.play("MonsterGrowl", 0.5)
		CameraController.shake(0.35, 1.2)
	elseif id == "flicker" then
		task.spawn(function()
			local finish = os.clock() + duration
			while os.clock() < finish do
				Lighting.Brightness = math.random() * 0.2
				task.wait(math.random(3, 12) / 100)
				Lighting.Brightness = 1
				task.wait(math.random(20, 80) / 100)
			end
			Lighting.Brightness = 1
		end)
	elseif id == "lights_out" then
		local color = getEffect("DreamColor")
		if color then
			baseColor = color.Brightness
			color.Brightness = -0.35
			task.delay(duration, function()
				if color then
					color.Brightness = baseColor or 0
				end
			end)
		end
	elseif id == "sanity_aura" then
		CameraController.shake(0.5, duration)
	end
end

------------------------------------------------------------------
-- Качество графики
------------------------------------------------------------------
function EffectsController.applyQuality()
	local quality = ClientState.quality()
	local bloom = getEffect("DreamBloom")
	local dof = getEffect("DreamDOF")
	local atmosphere = getEffect("DreamAtmosphere")
	if quality == "Низкое" then
		if bloom then bloom.Enabled = false end
		if dof then dof.Enabled = false end
		if atmosphere then atmosphere.Enabled = false end
		Lighting.GlobalShadows = false
	elseif quality == "Средний" then
		if bloom then bloom.Enabled = true end
		if dof then dof.Enabled = true end
		if atmosphere then atmosphere.Enabled = true end
		Lighting.GlobalShadows = true
	else
		if bloom then bloom.Enabled = true end
		if dof then dof.Enabled = true end
		if atmosphere then atmosphere.Enabled = true end
		Lighting.GlobalShadows = true
	end
end

------------------------------------------------------------------
-- Инициализация
------------------------------------------------------------------
function EffectsController.start()
	ensureFlashlight()
	baseFogEnd = Lighting.FogEnd

	ClientState.on("Flashlight", function(enabled)
		setFlashlight(enabled == true)
	end)

	-- Сервер — источник истины: если батарея села, свет гаснет у всех.
	-- Слушаем канал напрямую, чтобы фонарик работал даже если HUD не загрузился.
	local lastServerState = nil
	local vitalsRemote = Remotes.get("VitalsUpdate")
	if vitalsRemote then
		vitalsRemote.OnClientEvent:Connect(function(data)
			if not data then
				return
			end
			ClientState.vitals = data
			local serverOn = data.flashlight == true
			if lastServerState ~= serverOn then
				lastServerState = serverOn
				if serverOn ~= flashlightOn then
					setFlashlight(serverOn)
				end
			end
		end)
	end

	RunService.RenderStepped:Connect(updateFlashlight)

	ClientState.on("Phase", function(phase)
		if phase == "dream" then
			baseFogEnd = Lighting.FogEnd
			EffectsController.applyQuality()
		elseif phase == "menu" then
			setFlashlight(false)
			clearWeather()
			local hud = ClientState.getGui("DreamHUD")
			local vignette = hud and hud:FindFirstChild("Vignette", true)
			if vignette then
				vignette.Visible = false
			end
			EffectsController.applyQuality()
		end
	end)

	ClientState.on("QualityChanged", function()
		EffectsController.applyQuality()
	end)

	Remotes.get("Scare").OnClientEvent:Connect(function(data)
		local kind = data.kind
		local now = os.clock()
		if kind == "jumpscare" then
			if now - scareCooldown < 1.5 then
				return
			end
			scareCooldown = now
			EffectsController.jumpscare(data.monsterName, data.intensity or 1)
		elseif kind == "whisper" then
			SoundController.play("Whisper", 0.7)
			local hud = ClientState.getGui("DreamHUD")
			local whisper = hud and hud:FindFirstChild("WhisperLabel", true)
			if whisper then
				whisper.Visible = true
				whisper.Text = "проснись... проснись... проснись..."
				whisper.TextTransparency = 0.1
				task.delay(2.5, function()
					if whisper then
						whisper.Visible = false
					end
				end)
			end
			CameraController.shake(0.5, 1)
		elseif kind == "hallucination" then
			EffectsController.hallucination()
		elseif kind == "growl" then
			SoundController.play("MonsterGrowl", 0.7)
			CameraController.shake(0.45, 0.6)
		elseif kind == "reveal" then
			EffectsController.jumpscare(data.monsterName or "ОБМАНЩИК", 1)
		elseif kind == "boss" then
			EffectsController.jumpscare(data.monsterName or "КОШМАР", 1.2)
		end
	end)

	Remotes.get("EventStarted").OnClientEvent:Connect(function(data)
		EffectsController.handleEvent(data)
	end)

	Remotes.get("VitalsUpdate").OnClientEvent:Connect(function(data)
		EffectsController.applySanity(data.sanity or 100)
		if (data.sanity or 100) < 35 then
			SoundController.heartbeat(true)
		else
			SoundController.heartbeat(false)
		end
	end)

	-- Фонарик следует за камерой
	-- Поворот и мерцание фонарика выполняет updateFlashlight (см. выше)

	EffectsController.applyQuality()
end

EffectsController.setWeather = setWeather

return EffectsController
