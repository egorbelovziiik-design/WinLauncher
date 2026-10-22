--!nocheck
--[[
	CameraController.luau
	Кинематографичная камера меню, строгое первое лицо во сне,
	камера для катсцен и тряска (её можно отключить в настройках).
]]

local Workspace = game:GetService("Workspace")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")

local ClientState = require(script.Parent.ClientState)

local CameraController = {}

local camera = Workspace.CurrentCamera
local mode = "menu"
local menuTime = 0
local shakeAmount = 0
local shakeDecay = 0
local connection

local function getBedroom()
	return Workspace:FindFirstChild("MenuBedroom")
end

local function getCameraNodes()
	local bedroom = getBedroom()
	local result = {}
	if not bedroom then
		return result
	end
	local folder = bedroom:FindFirstChild("CameraPath")
	if not folder then
		return result
	end
	local nodes = {}
	for _, child in ipairs(folder:GetChildren()) do
		if child:IsA("BasePart") then
			local index = tonumber(child.Name:match("%d+")) or 1
			nodes[index] = child
		end
	end
	for index = 1, #nodes do
		if nodes[index] then
			table.insert(result, nodes[index])
		end
	end
	return result
end

local function getLookTarget()
	local bedroom = getBedroom()
	local target = bedroom and bedroom:FindFirstChild("CameraTarget", true)
	if target and target:IsA("BasePart") then
		return target.Position
	end
	return Vector3.new(0, 6, -2)
end

function CameraController.setMode(newMode)
	mode = newMode
	camera = Workspace.CurrentCamera
	if mode == "menu" then
		camera.CameraType = Enum.CameraType.Custom
		CameraController.releaseToPlayer()
		ClientState.player.CameraMode = Enum.CameraMode.Classic
	elseif mode == "dream" then
		camera.CameraType = Enum.CameraType.Custom
		ClientState.player.CameraMode = Enum.CameraMode.LockFirstPerson
		camera.FieldOfView = 78
	elseif mode == "script" then
		camera.CameraType = Enum.CameraType.Scriptable
		ClientState.player.CameraMode = Enum.CameraMode.Classic
	end
end

-- Возвращает управление камерой персонажу (лобби).
function CameraController.releaseToPlayer()
	local character = ClientState.player.Character
	if not character then
		return
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	camera = Workspace.CurrentCamera
	if not camera then
		return
	end
	camera.CameraType = Enum.CameraType.Custom
	if humanoid then
		camera.CameraSubject = humanoid
	end
	ClientState.player.CameraMode = Enum.CameraMode.Classic
end

function CameraController.getMode()
	return mode
end

function CameraController.setFieldOfView(fov, duration)
	ClientState.tween(camera, duration or 1, { FieldOfView = fov })
end

function CameraController.shake(intensity, duration)
	if not ClientState.settings.cameraShake then
		return
	end
	local scare = math.clamp(ClientState.settings.scareIntensity or 1, 0.35, 1.6)
	shakeAmount = math.max(shakeAmount, (intensity or 0.5) * scare)
	shakeDecay = math.max(shakeDecay, (intensity or 0.5) * scare / math.max(0.2, duration or 1))
end

function CameraController.setScriptableCFrame(cframe)
	mode = "script"
	camera = Workspace.CurrentCamera
	camera.CameraType = Enum.CameraType.Scriptable
	camera.CFrame = cframe
end

function CameraController.applySensitivity()
	local sensitivity = ClientState.settings.sensitivity or 0.5
	UserInputService.MouseDeltaSensitivity = 0.4 + sensitivity * 1.6
	if sensitivity < 0.2 then
		ClientState.tween(camera, 1, { FieldOfView = mode == "dream" and 60 or 70 })
	end
end

function CameraController.lookAt(target, cframePosition)
	if not cframePosition then
		return
	end
	local direction = (target - cframePosition).Unit
	CameraController.setScriptableCFrame(CFrame.lookAt(cframePosition, cframePosition + direction))
end

function CameraController.start()
	applySensitivity()
	local nodes = getCameraNodes()
	if #nodes == 0 then
		-- Запасная траектория, если карта меню не загрузилась
		nodes = {
			{ CFrame = CFrame.new(6, 7, 10) },
			{ CFrame = CFrame.new(-6, 6, 8) },
			{ CFrame = CFrame.new(-2, 8, -4) },
		}
	end
	local target = getLookTarget()
	local segmentTime = 9

	connection = RunService.RenderStepped:Connect(function(dt)
		camera = Workspace.CurrentCamera
		if not camera then
			return
		end
		menuTime += dt

		if mode == "menu" then
			-- В лобби камера обычная (от третьего лица), игрок свободно ходит.
			if camera.CameraType ~= Enum.CameraType.Custom then
				camera.CameraType = Enum.CameraType.Custom
				camera.CameraSubject = nil
			end
		end

		if shakeAmount > 0.001 then
			shakeAmount = math.max(0, shakeAmount - shakeDecay * dt)
			local offset = Vector3.new(
				(math.random() - 0.5) * shakeAmount,
				(math.random() - 0.5) * shakeAmount,
				0
			) * 0.35
			if camera.CameraType == Enum.CameraType.Scriptable then
				camera.CFrame = camera.CFrame * CFrame.new(offset) * CFrame.Angles(0, 0, math.rad((math.random() - 0.5) * shakeAmount))
			else
				local character = ClientState.player.Character
				local humanoid = character and character:FindFirstChildOfClass("Humanoid")
				if humanoid then
					humanoid.CameraOffset = Vector3.new(offset.X, offset.Y, 0)
				end
			end
		else
			local character = ClientState.player.Character
			local humanoid = character and character:FindFirstChildOfClass("Humanoid")
			if humanoid and humanoid.CameraOffset.Magnitude > 0.001 then
				humanoid.CameraOffset = Vector3.zero
			end
		end
	end)
end

function CameraController.stop()
	if connection then
		connection:Disconnect()
		connection = nil
	end
end

return CameraController
