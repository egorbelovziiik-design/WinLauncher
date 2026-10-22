--!nonstrict
-- MapService: клонирование карт снов, освещение, поиск маркеров.

local Lighting = game:GetService("Lighting")
local ServerStorage = game:GetService("ServerStorage")
local Workspace = game:GetService("Workspace")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local TweenService = game:GetService("TweenService")

local SharedModules = ReplicatedStorage:WaitForChild("SharedModules")
local Config = require(SharedModules:WaitForChild("Config"))

local MapService = {}

-- Смещение мира снов относительно спальни меню.
MapService.DREAM_OFFSET = Vector3.new(0, 0, 4000)

local savedLighting = nil

local function ensure(className, name, parent)
	local existing = parent:FindFirstChild(name)
	if existing and existing:IsA(className) then
		return existing
	end
	if existing then
		existing:Destroy()
	end
	local instance = Instance.new(className)
	instance.Name = name
	instance.Parent = parent
	return instance
end

function MapService.captureLighting()
	if savedLighting then
		return
	end
	savedLighting = {
		Ambient = Lighting.Ambient,
		OutdoorAmbient = Lighting.OutdoorAmbient,
		Brightness = Lighting.Brightness,
		ClockTime = Lighting.ClockTime,
		FogColor = Lighting.FogColor,
		FogStart = Lighting.FogStart,
		FogEnd = Lighting.FogEnd,
		GlobalShadows = Lighting.GlobalShadows,
		ExposureCompensation = Lighting.ExposureCompensation,
		EnvironmentDiffuseScale = Lighting.EnvironmentDiffuseScale,
		EnvironmentSpecularScale = Lighting.EnvironmentSpecularScale,
	}
end

function MapService.applyLighting(dreamDef)
	MapService.captureLighting()
	Lighting.Ambient = dreamDef.ambient or Color3.fromRGB(40, 40, 55)
	Lighting.OutdoorAmbient = dreamDef.outdoorAmbient or Color3.fromRGB(20, 20, 34)
	Lighting.Brightness = dreamDef.brightness or 1
	Lighting.ClockTime = dreamDef.clockTime or 0.5
	Lighting.FogColor = dreamDef.fogColor or Color3.fromRGB(30, 34, 50)
	Lighting.FogStart = dreamDef.fogStart or 20
	Lighting.FogEnd = dreamDef.fogEnd or 300
	Lighting.GlobalShadows = true
	Lighting.EnvironmentDiffuseScale = 0.6
	Lighting.EnvironmentSpecularScale = 0.4

	local atmosphere = ensure("Atmosphere", "DreamAtmosphere", Lighting)
	local atmo = dreamDef.atmosphere or {}
	atmosphere.Density = atmo.density or 0.35
	atmosphere.Offset = atmo.offset or 0.1
	atmosphere.Color = atmo.color or Color3.fromRGB(120, 130, 160)
	atmosphere.Decay = atmo.decay or Color3.fromRGB(60, 70, 90)
	atmosphere.Glare = atmo.glare or 0.05
	atmosphere.Haze = atmo.haze or 1.5

	local bloom = ensure("BloomEffect", "DreamBloom", Lighting)
	local bloomDef = dreamDef.bloom or {}
	bloom.Enabled = true
	bloom.Intensity = bloomDef.intensity or 0.5
	bloom.Size = bloomDef.size or 24
	bloom.Threshold = bloomDef.threshold or 1.1

	local color = ensure("ColorCorrectionEffect", "DreamColor", Lighting)
	local cc = dreamDef.colorCorrection or {}
	color.Enabled = true
	color.TintColor = cc.tint or Color3.fromRGB(210, 215, 240)
	color.Saturation = cc.saturation or -0.1
	color.Contrast = cc.contrast or 0.15
	color.Brightness = cc.brightness or -0.03

	local dof = ensure("DepthOfFieldEffect", "DreamDOF", Lighting)
	local dofDef = dreamDef.depthOfField or {}
	dof.Enabled = true
	dof.FocusDistance = dofDef.focus or 40
	dof.NearIntensity = dofDef.near or 0.2
	dof.FarIntensity = dofDef.far or 0.4
	dof.InFocusRadius = 60
end

function MapService.resetLighting()
	if not savedLighting then
		return
	end
	for key, value in pairs(savedLighting) do
		pcall(function()
			Lighting[key] = value
		end)
	end
	for _, name in ipairs({ "DreamAtmosphere", "DreamBloom", "DreamColor", "DreamDOF" }) do
		local instance = Lighting:FindFirstChild(name)
		if instance then
			instance:Destroy()
		end
	end
end

-- Возвращает папку сессии с клоном карты.
function MapService.buildDream(dreamDef, parentFolder)
	local template = ServerStorage:FindFirstChild("DreamMaps")
	template = template and template:FindFirstChild(dreamDef.map)
	if not template then
		warn("[MapService] нет карты для сна: " .. tostring(dreamDef.map))
		return nil
	end
	local model = template:Clone()
	model.Name = "Dream_" .. dreamDef.map
	-- Сны строятся далеко от спальни меню, чтобы миры не пересекались.
	model:PivotTo(model:GetPivot() + MapService.DREAM_OFFSET)
	model.Parent = parentFolder

	-- Точка спавна
	local spawnPart = MapService.findMarker(model, "MK_Spawn")
	if spawnPart then
		model:SetAttribute("SpawnPosition", spawnPart.Position)
	end
	model:SetAttribute("DreamId", dreamDef.id)
	MapService.applyLighting(dreamDef)
	return model
end

function MapService.findMarker(model, name)
	for _, descendant in ipairs(model:GetDescendants()) do
		if descendant.Name == name and descendant:IsA("BasePart") then
			return descendant
		end
	end
	return nil
end

function MapService.collectMarkers(model, prefix)
	local out = {}
	for _, descendant in ipairs(model:GetDescendants()) do
		if descendant:IsA("BasePart") and string.sub(descendant.Name, 1, #prefix) == prefix then
			table.insert(out, descendant)
		end
	end
	table.sort(out, function(a, b)
		return a.Name < b.Name
	end)
	return out
end

function MapService.spawnPosition(model, fallback)
	local marker = MapService.findMarker(model, "MK_Spawn")
	if marker then
		return marker.Position + Vector3.new(0, 4, 0)
	end
	return fallback or Vector3.new(0, 8, 0)
end

function MapService.cleanup(folder)
	if folder and folder.Parent then
		folder:Destroy()
	end
	MapService.resetLighting()
end

function MapService.setFogIntensity(multiplier)
	Lighting.FogEnd = math.max(20, Lighting.FogEnd * multiplier)
end

return MapService
