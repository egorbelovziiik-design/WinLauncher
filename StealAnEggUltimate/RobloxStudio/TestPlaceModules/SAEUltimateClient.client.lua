--!strict
--[[
	STEAL AN EGG ULTIMATE HUB — client module (LocalScript)

	Put this LocalScript in StarterPlayer → StarterPlayerScripts.

	Handles the parts of the movement feature set that must run on the client:
	  * Fly (camera-relative BodyVelocity flight, key from the hub settings)
	  * Noclip (local collision toggle)
	  * Infinite Jump
	  * Teleport-to-cursor key
	  * Camera heading reporting so the hub ESP projection matches your view

	Everything is gated by the hub state received from the server bridge.
	Without a hub connection every flag stays false and the place plays
	normally. No external code is loaded or executed here.
]]

local Players = game:GetService("Players")
local UserInputService = game:GetService("UserInputService")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local Workspace = game:GetService("Workspace")

local player = Players.LocalPlayer
local remotes = ReplicatedStorage:WaitForChild("SAEUltimateRemotes", 30)
if not remotes then
	warn("[SAEU-Client] remotes not found — is the server bridge installed?")
	return
end

local stateRemote = remotes:WaitForChild("State") :: RemoteEvent
local clientRemote = remotes:WaitForChild("ClientEvent") :: RemoteEvent

local state = {
	fly = false,
	flySpeed = 40,
	noclip = false,
	infiniteJump = false,
	flyKey = "F",
	speedKey = "X",
	teleportKey = "T",
}

local flying = false
local flyVelocity: BodyVelocity? = nil
local flyGyro: BodyGyro? = nil
local lastHeading = 0
local lastHeadingSend = 0

local function getCharacter(): (Model?, Humanoid?, BasePart?)
	local character = player.Character
	if not character then
		return nil, nil, nil
	end
	return character, character:FindFirstChildOfClass("Humanoid"), character:FindFirstChild("HumanoidRootPart") :: BasePart?
end

local function stopFlying()
	flying = false
	if flyVelocity then
		flyVelocity:Destroy()
		flyVelocity = nil
	end
	if flyGyro then
		flyGyro:Destroy()
		flyGyro = nil
	end

	local character, humanoid = getCharacter()
	if character and humanoid then
		humanoid.PlatformStand = false
	end
end

local function startFlying()
	local character, humanoid, root = getCharacter()
	if not character or not humanoid or not root then
		return
	end

	if flying then
		stopFlying()
		return
	end

	flying = true
	humanoid.PlatformStand = true

	local velocity = Instance.new("BodyVelocity")
	velocity.Name = "SAEUFlyVelocity"
	velocity.MaxForce = Vector3.new(1, 1, 1) * 1e5
	velocity.Velocity = Vector3.zero
	velocity.Parent = root
	flyVelocity = velocity

	local gyro = Instance.new("BodyGyro")
	gyro.Name = "SAEUFlyGyro"
	gyro.MaxTorque = Vector3.new(1, 1, 1) * 1e5
	gyro.P = 1500
	gyro.D = 60
	gyro.CFrame = CFrame.new(root.Position, root.Position + Workspace.CurrentCamera.CFrame.LookVector)
	gyro.Parent = root
	flyGyro = gyro
end

local function applyNoclip()
	local character = getCharacter()
	if not character then
		return
	end

	for _, part in character:GetDescendants() do
		if part:IsA("BasePart") then
			if state.noclip then
				part.CanCollide = false
			elseif part.Name ~= "HumanoidRootPart" then
				part.CanCollide = true
			end
		end
	end
end

local function teleportToCursor()
	local character, humanoid, root = getCharacter()
	local camera = Workspace.CurrentCamera
	if not character or not root or not camera then
		return
	end

	local mouse = player:GetMouse()
	local parameters = RaycastParams.new()
	parameters.FilterType = Enum.RaycastFilterType.Exclude
	parameters.FilterDescendantsInstances = { character }

	local origin = camera.CFrame.Position
	local target = mouse.Hit and mouse.Hit.Position or camera.CFrame.Position + camera.CFrame.LookVector * 30
	local ray = Workspace:Raycast(origin, target - origin, parameters)
	local destination = if ray then ray.Position + Vector3.new(0, 4, 0) else target + Vector3.new(0, 4, 0)

	root.CFrame = CFrame.new(destination)
	_ = humanoid
end

-- ============================ INPUT ============================
local function keyMatches(input: InputObject, name: string): boolean
	local keyName = name:gsub("%s", ""):upper()
	local userKey = input.KeyCode.Name
	if userKey == keyName then
		return true
	end

	-- Support single letters and common aliases.
	if #keyName == 1 then
		return userKey == "Key" .. keyName
	end

	return false
end

UserInputService.InputBegan:Connect(function(input, processed)
	if processed then
		return
	end
	if input.UserInputType ~= Enum.UserInputType.Keyboard then
		return
	end

	if keyMatches(input, state.flyKey) and (state.fly or flying) then
		startFlying()
	elseif keyMatches(input, state.teleportKey) and state.teleportKey ~= "" then
		teleportToCursor()
	end
end)

UserInputService.JumpRequest:Connect(function()
	if not state.infiniteJump then
		return
	end

	local character, humanoid = getCharacter()
	if humanoid and character and humanoid:GetState() ~= Enum.HumanoidStateType.Dead then
		humanoid:ChangeState(Enum.HumanoidStateType.Jumping)
	end
end)

-- ============================ LOOPS ============================
stateRemote.OnClientEvent:Connect(function(payload)
	if type(payload) ~= "table" then
		return
	end

	local wasFlying = flying
	state.fly = payload.fly == true
	state.flySpeed = tonumber(payload.flySpeed) or 40
	state.noclip = payload.noclip == true
	state.infiniteJump = payload.infiniteJump == true
	state.flyKey = tostring(payload.flyKey or "F")
	state.speedKey = tostring(payload.speedKey or "X")
	state.teleportKey = tostring(payload.teleportKey or "T")

	if state.fly and not wasFlying then
		startFlying()
	elseif wasFlying and not state.fly then
		stopFlying()
	end
end)

RunService.RenderStepped:Connect(function(dt)
	local character, humanoid, root = getCharacter()
	if not character or not root then
		if flying then
			stopFlying()
		end
		return
	end

	if flying then
		if not state.fly or not flyVelocity then
			stopFlying()
		else
			local camera = Workspace.CurrentCamera
			local move = Vector3.zero
			if UserInputService:IsKeyDown(Enum.KeyCode.W) then
				move += camera.CFrame.LookVector
			end
			if UserInputService:IsKeyDown(Enum.KeyCode.S) then
				move -= camera.CFrame.LookVector
			end
			if UserInputService:IsKeyDown(Enum.KeyCode.D) then
				move += camera.CFrame.RightVector
			end
			if UserInputService:IsKeyDown(Enum.KeyCode.A) then
				move -= camera.CFrame.RightVector
			end
			if UserInputService:IsKeyDown(Enum.KeyCode.Space) then
				move += Vector3.new(0, 1, 0)
			end
			if UserInputService:IsKeyDown(Enum.KeyCode.LeftControl) then
				move -= Vector3.new(0, 1, 0)
			end

			flyVelocity.Velocity = move * state.flySpeed
			flyGyro.CFrame = CFrame.new(root.Position, root.Position + camera.CFrame.LookVector)
		end
		_ = dt
	end

	if state.noclip and math.floor(os.clock() * 5) ~= math.floor((os.clock() - dt) * 5) then
		applyNoclip()
	end
end)

task.spawn(function()
	while true do
		local camera = Workspace.CurrentCamera
		if camera then
			local look = camera.CFrame.LookVector
			local heading = math.deg(math.atan2(-look.X, -look.Z))
			lastHeading = heading

			local now = os.clock()
			if now - lastHeadingSend > 0.2 then
				lastHeadingSend = now
				clientRemote:FireServer({ heading = math.floor(heading * 10) / 10 })
			end
		end
		task.wait(0.1)
	end
end)

player.CharacterAdded:Connect(function()
	flying = false
	flyVelocity = nil
	flyGyro = nil
end)

print(string.format("[SAEU-Client] ready · last heading %.1f", lastHeading))
