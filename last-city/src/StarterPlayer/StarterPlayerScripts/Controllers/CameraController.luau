--!nonstrict
return function(app)
    local RunService = game:GetService("RunService")
    local Input = game:GetService("UserInputService")
    local Camera = {Commander = false, Focus = nil, Height = 96, Pan = Vector3.zero, Dialogue = nil, Travel = nil, FirstPerson = false}
    local self = Camera
    local controls, disabled
    task.spawn(function()
        local module = app.Player.PlayerScripts:WaitForChild("PlayerModule", 15)
        if module then local ok, playerModule = pcall(require, module); if ok then controls = playerModule:GetControls() end end
    end)
    app.Player.CameraMinZoomDistance = 0.5; app.Player.CameraMaxZoomDistance = 22
    function Camera:SetFirstPerson(value)
        self.FirstPerson = value == true
        app.Player.CameraMode = self.FirstPerson and Enum.CameraMode.LockFirstPerson or Enum.CameraMode.Classic
        app.Player.CameraMinZoomDistance = self.FirstPerson and 0.5 or 7
        app.Player.CameraMaxZoomDistance = self.FirstPerson and 0.5 or 22
    end
    function Camera:SetCommander(value)
        if self.Commander and not value and workspace.CurrentCamera then
            self.ExitFrom = workspace.CurrentCamera.CFrame; self.ExitAt = os.clock()
        end
        self.Commander = value; self.Pan = Vector3.zero
        if value then app:Toast("COMMANDER • WASD — обзор, колесо — высота, V — вернуться. Персонаж остаётся уязвимым.") end
    end
    function Camera:SetDialogue(model) self.Dialogue = model end
    function Camera:SetTravel(data)
        local old = self.Travel
        if old then
            if old.model then old.model:Destroy() end
            for _, saved in old.hidden or {} do if saved.part.Parent then saved.part.LocalTransparencyModifier = saved.value end end
        end
        self.Travel = nil
        if not data or data.phase ~= "start" then if app.Audio then app.Audio:SetTravel(false) end; return end
        local transports = workspace:FindFirstChild("World") and workspace.World:FindFirstChild("Transports")
        local template = transports and transports:FindFirstChild(data.helicopter)
        local hidden = {}
        -- The authored parked aircraft stay server-owned; hide both only for this client
        -- during the cinematic so the moving clone never overlaps a static helicopter.
        if transports then
            for _, helicopterName in {"City1Helicopter", "City2Helicopter"} do
                local parked = transports:FindFirstChild(helicopterName)
                for _, node in parked and parked:GetDescendants() or {} do
                    if node:IsA("BasePart") then
                        table.insert(hidden, {part = node, value = node.LocalTransparencyModifier})
                        node.LocalTransparencyModifier = 1
                    end
                end
            end
        end
        local model = template and template:Clone() or nil
        local rotors = {}
        if model then
            model.Name = "ClientFlightHelicopter"
            for _, node in model:GetDescendants() do
                if node:IsA("BasePart") then
                    node.CanCollide = false; node.CanQuery = false; node.CanTouch = false; node.LocalTransparencyModifier = 0
                    if node.Name == "MainRotor" and model.PrimaryPart then table.insert(rotors, {part = node, relative = model.PrimaryPart.CFrame:ToObjectSpace(node.CFrame)}) end
                end
                if node:IsA("ProximityPrompt") then node:Destroy() end
            end
            model.Parent = workspace; model:PivotTo(CFrame.new(data.from))
        end
        self.Travel = {from = data.from, to = data.to, duration = data.duration, started = os.clock(), model = model, rotors = rotors, hidden = hidden}
        self.Dialogue = nil; self.Commander = false
        if app.Audio then app.Audio:SetTravel(true) end
    end
    app.Player.CharacterAdded:Connect(function()
        -- A reset is a hard visual cancellation even if the server arrival packet races
        -- character replacement. It also restores parked aircraft and rotor audio.
        if self.Travel then self:SetTravel(nil) end
    end)
    Input.InputChanged:Connect(function(input, processed)
        if not processed and self.Commander and input.UserInputType == Enum.UserInputType.MouseWheel then self.Height = math.clamp(self.Height - input.Position.Z * 9, 55, 155) end
    end)
    RunService:BindToRenderStep("LastCityCamera", Enum.RenderPriority.Camera.Value + 1, function(dt)
        local camera = workspace.CurrentCamera; if not camera then return end
        local char = app.Player.Character; local root = char and char:FindFirstChild("HumanoidRootPart"); local h = char and char:FindFirstChildOfClass("Humanoid")
        local active = app.State and app.State.active
        local blocked = not active or self.Commander or self.Travel ~= nil or self.ExitFrom ~= nil or app.PanelName ~= nil
        if controls and disabled ~= blocked then disabled = blocked; if blocked then controls:Disable() else controls:Enable() end end
        Input.MouseIconEnabled = not active or app.PanelName ~= nil or self.Commander or app.Build and app.Build.Active or Input.TouchEnabled
        if not active then
            camera.CameraType = Enum.CameraType.Scriptable
            local t = os.clock() * 0.055
            camera.CFrame = CFrame.lookAt(Vector3.new(55 + math.sin(t) * 18, 32 + math.sin(t * 0.7) * 4, -5 + math.cos(t) * 12), Vector3.new(-30, 17, 120))
            camera.FieldOfView = 65
        elseif self.Travel then
            local flight = self.Travel
            local t = math.clamp((os.clock() - flight.started) / flight.duration, 0, 1)
            local smooth = t * t * (3 - 2 * t)
            local base = flight.from:Lerp(flight.to, smooth)
            local position = base + Vector3.new(0, math.sin(t * math.pi) * 82, 0)
            local direction = flight.to - flight.from
            local flat = Vector3.new(direction.X, 0, direction.Z)
            if flat.Magnitude < 0.1 then flat = Vector3.new(0, 0, -1) else flat = flat.Unit end
            local helicopterFrame = CFrame.lookAt(position, position + flat)
            if flight.model and flight.model.Parent then
                flight.model:PivotTo(helicopterFrame)
                local spin = (os.clock() - flight.started) * 17
                for _, rotor in flight.rotors do if rotor.part.Parent then rotor.part.CFrame = helicopterFrame * rotor.relative * CFrame.Angles(0, spin, 0) end end
            end
            camera.CameraType = Enum.CameraType.Scriptable; camera.FieldOfView = 72
            camera.CFrame = CFrame.lookAt(position - flat * 24 + Vector3.new(14, 10, 0), position + flat * 8)
        elseif self.Dialogue and self.Dialogue.Parent and self.Dialogue.PrimaryPart then
            local target = self.Dialogue.PrimaryPart.Position + Vector3.new(0, 1.4, 0)
            local pos = self.Dialogue.PrimaryPart.CFrame:PointToWorldSpace(Vector3.new(3, 2, -6))
            camera.CameraType = Enum.CameraType.Scriptable
            camera.CFrame = camera.CFrame:Lerp(CFrame.lookAt(pos, target), 1 - math.exp(-dt * 5))
            if root and (root.Position - target).Magnitude > 17 then app:ClosePanel() end
        elseif self.Commander and root then
            camera.CameraType = Enum.CameraType.Scriptable
            local plot = app.Config.Plots[app.State.plot]
            local movement = Vector3.zero
            if not app.PanelName and not Input:GetFocusedTextBox() then
                if Input:IsKeyDown(Enum.KeyCode.W) then movement += Vector3.new(0, 0, -1) end
                if Input:IsKeyDown(Enum.KeyCode.S) then movement += Vector3.new(0, 0, 1) end
                if Input:IsKeyDown(Enum.KeyCode.A) then movement += Vector3.new(-1, 0, 0) end
                if Input:IsKeyDown(Enum.KeyCode.D) then movement += Vector3.new(1, 0, 0) end
            end
            self.Pan += movement * dt * 38
            self.Pan = Vector3.new(math.clamp(self.Pan.X, -40, 40), 0, math.clamp(self.Pan.Z, -40, 40))
            local focus = Vector3.new(plot.x, 0, plot.z) + self.Pan
            local goal = CFrame.lookAt(focus + Vector3.new(0, self.Height, self.Height * 0.48), focus)
            camera.CFrame = camera.CFrame:Lerp(goal, 1 - math.exp(-dt * 6))
        elseif self.ExitFrom and root then
            local t = math.clamp((os.clock() - self.ExitAt) / 0.4, 0, 1)
            local goal = CFrame.lookAt(root.Position - root.CFrame.LookVector * 12 + Vector3.new(0, 6, 0), root.Position + Vector3.new(0, 2, 0))
            camera.CameraType = Enum.CameraType.Scriptable
            camera.CFrame = self.ExitFrom:Lerp(goal, t * t * (3 - 2 * t))
            if t >= 1 then self.ExitFrom = nil end
        elseif h then
            camera.CameraType = Enum.CameraType.Custom; camera.CameraSubject = h; camera.FieldOfView = 70
            h.CameraOffset = app.Crouching and Vector3.new(0, -0.8, 0) or Vector3.zero
        end
    end)
    return Camera
end
