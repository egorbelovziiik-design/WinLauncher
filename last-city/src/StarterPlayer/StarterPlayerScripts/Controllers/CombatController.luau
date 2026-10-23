--!nonstrict
return function(app)
    local CAS = game:GetService("ContextActionService")
    local Input = game:GetService("UserInputService")
    local RunService = game:GetService("RunService")
    local Controller = {Held = false, NextShot = 0}
    local self = Controller
    local function playing() return app.State and app.State.active and not app.PanelName end
    function Controller:Fire()
        if not playing() or app.Camera.Commander or app.Build.Active then return end
        local entry = app:Equipped(); if not entry then return end
        local def = app.Weapons[entry.id]; if not def or self.NextShot > os.clock() then return end
        if not def.melee and (entry.ammo or 0) <= 0 then return end
        if def.melee and (app.State.stamina or 0) < 8 then return end
        self.NextShot = os.clock() + def.interval
        local camera = workspace.CurrentCamera
        local ray = camera:ViewportPointToRay(camera.ViewportSize.X / 2, camera.ViewportSize.Y / 2)
        local params = RaycastParams.new(); params.FilterType = Enum.RaycastFilterType.Exclude; params.FilterDescendantsInstances = app.Player.Character and {app.Player.Character} or {}
        local hit = workspace:Raycast(ray.Origin, ray.Direction * def.range, params)
        local target = hit and hit.Position or ray.Origin + ray.Direction * def.range
        local head = app.Player.Character and app.Player.Character:FindFirstChild("Head")
        if head then
            local delta = target - head.Position
            if delta.Magnitude > 0.01 then
                app:Send("CombatRemote", {action = "attack", aim = delta.Unit})
                if app.Audio then app.Audio:Weapon(entry.id) end
                if not def.melee and camera then
                    local recoil = math.rad(def.recoil or 0.6)
                    camera.CFrame = camera.CFrame * CFrame.Angles(-recoil, (math.random() - 0.5) * recoil * 0.45, 0)
                end
                if app.Crosshair then
                    local old = app.Crosshair.TextSize; app.Crosshair.TextSize = old + (def.melee and 2 or 6)
                    task.delay(0.08, function() if app.Crosshair then app.Crosshair.TextSize = old end end)
                end
            end
        end
    end
    CAS:BindAction("LC_Attack", function(_, state)
        if state == Enum.UserInputState.Begin then
            if app.Build.Active then app.Build:Place()
            else self.Held = true; self:Fire() end
        elseif state == Enum.UserInputState.End or state == Enum.UserInputState.Cancel then self.Held = false end
        return playing() and Enum.ContextActionResult.Sink or Enum.ContextActionResult.Pass
    end, true, Enum.KeyCode.ButtonR2)
    CAS:SetTitle("LC_Attack", "АТАКА"); CAS:SetPosition("LC_Attack", UDim2.fromScale(0.83, 0.58))
    CAS:BindAction("LC_Reload", function(_, state)
        if state == Enum.UserInputState.Begin and playing() then
            if app.Build.Active then app.Build:Rotate() else app:Send("CombatRemote", {action = "reload"}) end
        end
        return Enum.ContextActionResult.Pass
    end, true, Enum.KeyCode.R, Enum.KeyCode.ButtonX)
    CAS:SetTitle("LC_Reload", "R"); CAS:SetPosition("LC_Reload", UDim2.fromScale(0.91, 0.42))
    CAS:BindAction("LC_Commander", function(_, state)
        if state == Enum.UserInputState.Begin and playing() then app:Send("MovementRemote", {action = "commander"}) end
        return Enum.ContextActionResult.Pass
    end, true, Enum.KeyCode.V, Enum.KeyCode.DPadUp)
    CAS:SetTitle("LC_Commander", "V"); CAS:SetPosition("LC_Commander", UDim2.fromScale(0.82, 0.42))
    CAS:BindAction("LC_FirstPerson", function(_, state)
        if state == Enum.UserInputState.Begin and playing() and not app.Camera.Commander then
            local enabled = not app.Camera.FirstPerson
            app.Camera:SetFirstPerson(enabled)
            app:Send("SessionRemote", {action = "setting", id = "firstPerson", value = enabled})
            app:Toast(enabled and "КАМЕРА: ПЕРВОЕ ЛИЦО" or "КАМЕРА: ТРЕТЬЕ ЛИЦО")
        end
        return Enum.ContextActionResult.Pass
    end, true, Enum.KeyCode.Z, Enum.KeyCode.DPadRight)
    CAS:SetTitle("LC_FirstPerson", "1P"); CAS:SetPosition("LC_FirstPerson", UDim2.fromScale(0.73, 0.42))
    CAS:BindAction("LC_Sprint", function(_, state)
        if app.State and app.State.active then
            app:Send("MovementRemote", {action = "sprint", value = state == Enum.UserInputState.Begin and playing()})
        end
        return Enum.ContextActionResult.Pass
    end, true, Enum.KeyCode.LeftShift, Enum.KeyCode.ButtonL3)
    CAS:SetTitle("LC_Sprint", "БЕГ"); CAS:SetPosition("LC_Sprint", UDim2.fromScale(0.08, 0.48))
    Input.InputBegan:Connect(function(input, processed)
        if processed or Input:GetFocusedTextBox() then return end
        if input.UserInputType == Enum.UserInputType.MouseButton1 and playing() then
            if app.Build.Active then app.Build:Place() else self.Held = true; self:Fire() end
            return
        end
        local key = input.KeyCode
        if key == Enum.KeyCode.Escape and app.Build.Active then app.Build:Cancel(); return end
        if key == Enum.KeyCode.Escape and app.PanelName then app:ClosePanel(); return end
        if not app.State or not app.State.active or app.Camera and app.Camera.Travel then return end
        local panels = {[Enum.KeyCode.K] = "inventory", [Enum.KeyCode.J] = "quests", [Enum.KeyCode.B] = "build", [Enum.KeyCode.M] = "map", [Enum.KeyCode.G] = "base", [Enum.KeyCode.P] = "profile", [Enum.KeyCode.T] = "tutorial", [Enum.KeyCode.O] = "settings"}
        if panels[key] then
            if app.PanelName == panels[key] then app:ClosePanel() else app:OpenPanel(panels[key]) end
            if key == Enum.KeyCode.K then app:Send("SessionRemote", {action = "inventoryOpened"}) end
        elseif key == Enum.KeyCode.F then app:Send("MovementRemote", {action = "flashlight"})
        elseif key == Enum.KeyCode.LeftControl then
            app.Crouching = not app.Crouching; app:Send("MovementRemote", {action = "crouch", value = app.Crouching})
        else
            local quick = {[Enum.KeyCode.One] = 1, [Enum.KeyCode.Two] = 2, [Enum.KeyCode.Three] = 3,
                [Enum.KeyCode.Four] = 4, [Enum.KeyCode.Five] = 5, [Enum.KeyCode.Six] = 6}
            if quick[key] then app:QuickSlot(quick[key]) end
        end
    end)
    Input.InputEnded:Connect(function(input)
        if input.UserInputType == Enum.UserInputType.MouseButton1 then self.Held = false end
    end)
    Input.WindowFocusReleased:Connect(function()
        self.Held = false
        if app.State and app.State.active then app:Send("MovementRemote", {action = "sprint", value = false}) end
    end)
    RunService.Heartbeat:Connect(function()
        if not playing() then self.Held = false end
        local e = app:Equipped(); local def = e and app.Weapons[e.id]
        if self.Held and def and def.auto then self:Fire() end
        local show = playing() and Input.TouchEnabled
        for _, name in {"LC_Attack", "LC_Reload", "LC_Commander", "LC_FirstPerson", "LC_Sprint"} do
            local button = CAS:GetButton(name); if button then button.Visible = show end
        end
    end)
    return Controller
end
