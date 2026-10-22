--!nonstrict
return function(app)
    local TweenService = game:GetService("TweenService")
    local Pool = {}; local nextIndex = 1
    local folder = Instance.new("Folder"); folder.Name = "ClientEffects"; folder.Parent = workspace
    for i = 1, 12 do
        local p = Instance.new("Part"); p.Name = "Tracer"; p.Anchored = true; p.CanCollide = false; p.CanQuery = false; p.CanTouch = false
        p.Material = Enum.Material.Neon; p.Color = Color3.fromRGB(255, 190, 100); p.Transparency = 1; p.CastShadow = false; p.Parent = folder
        Pool[i] = {part = p, serial = 0}
    end
    app.Remotes.EffectsRemote.OnClientEvent:Connect(function(kind, data)
        if kind ~= "shot" then return end
        if not data.melee then
            local e = Pool[nextIndex]; nextIndex = nextIndex % #Pool + 1; e.serial += 1
            local serial = e.serial; local length = (data.to - data.from).Magnitude
            if length > 0.01 then
                e.part.Size = Vector3.new(0.045, 0.045, length); e.part.CFrame = CFrame.lookAt((data.from + data.to) / 2, data.to); e.part.Transparency = 0.12
                task.delay(0.07, function() if e.serial == serial then e.part.Transparency = 1 end end)
            end
        end
        if data.user == app.Player.UserId then
            if data.hit then
                app.HitMarker.Visible = true
                task.delay(0.12, function() app.HitMarker.Visible = false end)
            end
            if data.melee then
                local char = app.Player.Character; local tool = char and char:FindFirstChildOfClass("Tool")
                if tool then
                    local original = tool.Grip
                    local tween = TweenService:Create(tool, TweenInfo.new(0.12, Enum.EasingStyle.Quad, Enum.EasingDirection.Out, 0, true), {Grip = original * CFrame.Angles(math.rad(-65), 0, math.rad(20))})
                    tween:Play()
                end
            end
        end
    end)
    -- Small pooled rain geometry, with no texture IDs or per-frame Instance allocation.
    local rain = {}
    for i = 1, 28 do
        local p = Instance.new("Part"); p.Name = "RainStreak"; p.Anchored = true
        p.Size = Vector3.new(0.035, 2, 0.035); p.Color = Color3.fromRGB(148, 180, 193)
        p.Material = Enum.Material.Glass; p.CanCollide = false; p.CanQuery = false; p.CanTouch = false
        p.CastShadow = false; p.Transparency = 1; p.Parent = folder
        rain[i] = {part = p, x = math.random(-24, 24), z = math.random(-24, 24), phase = i / 28}
    end
    local elapsed = 0
    game:GetService("RunService").Heartbeat:Connect(function(dt)
        elapsed += dt
        if elapsed < 0.05 then return end
        elapsed = 0
        local root = app.Player.Character and app.Player.Character:FindFirstChild("HumanoidRootPart")
        local enabled = root and app.State and app.State.active and app.State.weather == "Дождь" and app.State.profile.Settings.graphics ~= "Low"
        for _, drop in rain do
            drop.part.Transparency = enabled and 0.55 or 1
            if enabled then
                local y = 18 - ((os.clock() * 1.3 + drop.phase) % 1) * 22
                drop.part.CFrame = CFrame.new(root.Position + Vector3.new(drop.x, y, drop.z)) * CFrame.Angles(0, 0, 0.08)
            end
        end
    end)
    return {Folder = folder}
end
