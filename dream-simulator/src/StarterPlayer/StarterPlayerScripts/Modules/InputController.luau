--!nocheck
--[[
	InputController.luau
	Управление:
		WASD — движение, SHIFT — бег, E — взаимодействие, F — фонарик,
		TAB — задания, Q — эликсир рассудка, ESC — закрыть окно.
	Мобильные кнопки приходят из DreamHUD (SprintButton / InteractButton / FlashlightButton / UseItemButton).
]]

local ContextActionService = game:GetService("ContextActionService")
local UserInputService = game:GetService("UserInputService")
local RunService = game:GetService("RunService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Remotes = require(ReplicatedStorage.SharedModules.Remotes)
local ClientState = require(script.Parent.ClientState)

local InputController = {}

local sprinting = false
local flashlightOn = false
local lookAccumulator = 0
local actionsBound = false

local function interactRemote()
	return Remotes.get("Interact")
end

local function sendSprint(want)
	if sprinting == want then
		return
	end
	sprinting = want
	local remote = Remotes.get("SetSprinting")
	if remote then
		remote:FireServer(want)
	end
end

function InputController.toggleFlashlight(force)
	if not ClientState.isDream() then
		return
	end
	if not ClientState.ownsItem("flashlight") then
		ClientState.toast("Фонарик можно купить в магазине", Color3.fromRGB(255, 190, 120), "🔦")
		return
	end
	if force ~= nil then
		flashlightOn = force == true
	else
		flashlightOn = not flashlightOn
	end
	local remote = Remotes.get("SetFlashlight")
	if remote then
		remote:FireServer(flashlightOn)
	end
	ClientState.emit("Flashlight", flashlightOn)
end

function InputController.setSprintEnabled(enabled)
	if not ClientState.isDream() then
		sendSprint(false)
		return
	end
	sendSprint(enabled == true)
end

function InputController.interact()
	if not ClientState.prompt or not ClientState.prompt.id then
		return
	end
	local remote = interactRemote()
	if remote then
		remote:FireServer(ClientState.prompt.id)
	end
	ClientState.emit("Interacted")
end

function InputController.useSanityPotion()
	if not ClientState.isDream() then
		return
	end
	local count = ClientState.consumableCount("sanity_potion")
	if count <= 0 then
		ClientState.toast("Нет эликсира рассудка", Color3.fromRGB(255, 150, 150), "🧪")
		return
	end
	Remotes.get("UseItem"):FireServer("sanity_potion")
end

function InputController.resetSprint()
	sprinting = false
end

function InputController.start()
	if actionsBound then
		return
	end
	actionsBound = true

	ContextActionService:BindAction("DreamSprint", function(_, state)
		if state == Enum.UserInputState.Begin then
			InputController.setSprintEnabled(true)
		elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then
			InputController.setSprintEnabled(false)
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.LeftShift, Enum.KeyCode.RightShift)

	ContextActionService:BindAction("DreamInteract", function(_, state)
		if state == Enum.UserInputState.Begin then
			InputController.interact()
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.E)

	ContextActionService:BindAction("DreamFlashlight", function(_, state)
		if state == Enum.UserInputState.Begin then
			InputController.toggleFlashlight()
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.F)

	ContextActionService:BindAction("DreamQuestLog", function(_, state)
		if state == Enum.UserInputState.Begin then
			ClientState.emit("ToggleQuestLog")
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.Tab)

	ContextActionService:BindAction("DreamUseItem", function(_, state)
		if state == Enum.UserInputState.Begin then
			InputController.useSanityPotion()
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.Q)

	ContextActionService:BindAction("DreamClosePanel", function(_, state)
		if state == Enum.UserInputState.Begin then
			ClientState.emit("ClosePanel")
		end
		return Enum.ContextActionResult.Pass
	end, false, Enum.KeyCode.Escape)

	-- Мобильные действия из HUD
	ClientState.on("MobileAction", function(action)
		if action == "sprint" then
			InputController.setSprintEnabled(true)
			task.delay(1.2, function()
				if not UserInputService.TouchEnabled then
					return
				end
			end)
		elseif action == "interact" then
			InputController.interact()
		elseif action == "flashlight" then
			InputController.toggleFlashlight()
		elseif action == "item" then
			InputController.useSanityPotion()
		end
	end)

	-- Отправка направления взгляда (нужно НАБЛЮДАТЕЛЮ)
	RunService.RenderStepped:Connect(function(dt)
		if not ClientState.isDream() then
			return
		end
		lookAccumulator += dt
		if lookAccumulator > 0.25 then
			lookAccumulator = 0
			local camera = workspace.CurrentCamera
			local remote = Remotes.get("LookUpdate")
			if camera and remote then
				remote:FireServer(camera.CFrame.LookVector)
			end
		end
		if sprinting then
			local vitals = ClientState.vitals
			if vitals and vitals.stamina and vitals.stamina <= 1 then
				sendSprint(false)
			end
		end
	end)

	ClientState.on("Phase", function(phase)
		if phase == "menu" then
			InputController.resetSprint()
			flashlightOn = false
			ClientState.emit("Flashlight", false)
		end
	end)
end

function InputController.isFlashlightOn()
	return flashlightOn
end

return InputController
