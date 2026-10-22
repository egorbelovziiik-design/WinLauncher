--!nonstrict
return function(app)
    local RunService = game:GetService("RunService")
    local Input = game:GetService("UserInputService")
    local Camera = {Commander = false, Focus = nil, Height = 96, Pan = Vector3.zero, Dialogue = nil}
    local self = Camera
    local controls, disabled
    task.spawn(function()
        local module = app.Player.PlayerScripts:WaitForChild("PlayerModule", 15)
        if module then local ok, playerModule = pcall(require, module); if ok then controls = playerModule:GetControls() end end
    end)
    app.Player.CameraMinZoomDistance = 7; app.Player.CameraMaxZoomDistance = 22
    function Camera:SetCommander(value)
        if self.Commander and not value and workspace.CurrentCamera then
            self.ExitFrom = workspace.CurrentCamera.CFrame; self.ExitAt = os.clock()
        end
        self.Commander = value; self.Pan = Vector3.zero
        if value then app:Toast("COMMANDER • WASD — обзор, колесо — высота, V — вернуться. Персонаж остаётся уязвимым.") end
    end
    function Camera:SetDialogue(model) self.Dialogue = model end
    Input.InputChanged:Connect(function(input, processed)
        if not processed and self.Commander and input.UserInputType == Enum.UserInputType.MouseWheel then self.Height = math.clamp(self.Height - input.Position.Z * 9, 55, 155) end
    end)
    RunService:BindToRenderStep("LastCityCamera", Enum.RenderPriority.Camera.Value + 1, function(dt)
        local camera = workspace.CurrentCamera; if not camera then return end
        local char = app.Player.Character; local root = char and char:FindFirstChild("HumanoidRootPart"); local h = char and char:FindFirstChildOfClass("Humanoid")
        local active = app.State and app.State.active
        local blocked = not active or self.Commander or self.ExitFrom ~= nil or app.PanelName ~= nil
        if controls and disabled ~= blocked then disabled = blocked; if blocked then controls:Disable() else controls:Enable() end end
        Input.MouseIconEnabled = not active or app.PanelName ~= nil or self.Commander or app.Build and app.Build.Active or Input.TouchEnabled
        if not active then
            camera.CameraType = Enum.CameraType.Scriptable
            local t = os.clock() * 0.055
            camera.CFrame = CFrame.lookAt(Vector3.new(55 + math.sin(t) * 18, 32 + math.sin(t * 0.7) * 4, -5 + math.cos(t) * 12), Vector3.new(-30, 17, 120))
            camera.FieldOfView = 65
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
