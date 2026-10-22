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
	local battery = (ClientState.vitals and ClientState.vitals.battery) or 100
	if force == nil and not flashlightOn and battery <= 1 then
		ClientState.toast("Батарея пуста — используй батарейку (Q или магазин)", Color3.fromRGB(255, 176, 120), "🔋")
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
	-- Включаем локальный луч сразу, сервер подтвердит или выключит при пустой батарее.
	ClientState.emit("Flashlight", flashlightOn)
	if flashlightOn and ClientState.ownsItem("lantern") then
		ClientState.toast("Лампа сновидца: широкий тёплый свет", Color3.fromRGB(255, 214, 156), "🏮")
	end
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
	-- Держим локальное состояние в согласии с сервером (он гасит свет при пустой батарее).
	ClientState.on("Vitals", function(data)
		if data and data.flashlight ~= nil then
			local serverOn = data.flashlight == true
			if serverOn ~= flashlightOn then
				flashlightOn = serverOn
				ClientState.emit("Flashlight", serverOn)
			end
		end
	end)

	ClientState.on("MobileAction", function(action)
		if action == "sprint" then
			if sprinting then
				InputController.setSprintEnabled(false)
				ClientState.toast("Бег выключен", Color3.fromRGB(160, 200, 255), "🏃")
			elseif (ClientState.vitals and ClientState.vitals.stamina or 0) > 8 then
				InputController.setSprintEnabled(true)
				ClientState.toast("Бег включён — монстры слышат тебя", Color3.fromRGB(255, 200, 150), "🏃")
			else
				ClientState.toast("Не хватает выносливости", Color3.fromRGB(255, 150, 150), "⚡")
			end
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
		if phase == "menu" or phase == "result" then
			InputController.resetSprint()
			flashlightOn = false
			ClientState.emit("Flashlight", false)
		elseif phase == "dream" then
			flashlightOn = false
			ClientState.emit("Flashlight", false)
		end
	end)
end

function InputController.isFlashlightOn()
	return flashlightOn
end

return InputController
