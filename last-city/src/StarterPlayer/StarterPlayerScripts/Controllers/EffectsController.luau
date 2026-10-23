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
    local impactPool = {}; local impactIndex = 1
    for i = 1, 8 do
        local spark = Instance.new("Part"); spark.Name = "ImpactSpark"; spark.Shape = Enum.PartType.Ball; spark.Size = Vector3.new(0.3, 0.3, 0.3)
        spark.Anchored = true; spark.CanCollide = false; spark.CanQuery = false; spark.CanTouch = false; spark.Material = Enum.Material.Neon
        spark.Transparency = 1; spark.CastShadow = false; spark.Parent = folder; impactPool[i] = {part = spark, serial = 0}
    end
    local function impact(position, tint, size)
        if typeof(position) ~= "Vector3" then return end
        local item = impactPool[impactIndex]; impactIndex = impactIndex % #impactPool + 1; item.serial += 1
        local serial = item.serial; item.part.CFrame = CFrame.new(position); item.part.Color = tint
        item.part.Size = Vector3.new(size or 0.32, size or 0.32, size or 0.32); item.part.Transparency = 0.08
        TweenService:Create(item.part, TweenInfo.new(0.16, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {Size = Vector3.new(0.04, 0.04, 0.04), Transparency = 1}):Play()
        task.delay(0.18, function() if item.serial == serial then item.part.Transparency = 1 end end)
    end
    local blastPool = {}; local blastIndex = 1
    for i = 1, 5 do
        local sphere = Instance.new("Part"); sphere.Name = "GrenadeBlast"; sphere.Shape = Enum.PartType.Ball; sphere.Size = Vector3.new(1, 1, 1)
        sphere.Anchored = true; sphere.CanCollide = false; sphere.CanQuery = false; sphere.CanTouch = false; sphere.Material = Enum.Material.Neon
        sphere.Transparency = 1; sphere.CastShadow = false; sphere.Parent = folder; blastPool[i] = {part = sphere, serial = 0}
    end
    local function blast(data, burning)
        if typeof(data.from) ~= "Vector3" then return end
        local item = blastPool[blastIndex]; blastIndex = blastIndex % #blastPool + 1; item.serial += 1
        local serial = item.serial; local radius = math.clamp(data.radius or 8, 2, 24)
        item.part.CFrame = CFrame.new(data.from); item.part.Size = Vector3.new(1, 1, 1)
        item.part.Color = burning and Color3.fromRGB(255, 101, 32) or data.electric and Color3.fromRGB(76, 218, 245) or data.concussion and Color3.fromRGB(224, 235, 255) or Color3.fromRGB(255, 190, 78)
        item.part.Transparency = burning and 0.55 or 0.18
        TweenService:Create(item.part, TweenInfo.new(burning and 0.4 or 0.24, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {
            Size = Vector3.new(radius * 2, radius * 0.65, radius * 2), Transparency = 1,
        }):Play()
        task.delay(0.45, function() if item.serial == serial then item.part.Transparency = 1 end end)
    end
    local damagePool = {}; local damageIndex = 1
    -- Enough pooled world labels for every allowed farm plus simultaneous combat hits.
    -- They are allocated once on the client; the one-second income loop creates no Instances.
    for i = 1, (app.Config.MaxBuildings or 36) + 8 do
        local anchor = Instance.new("Part"); anchor.Name = "FloatingPopup"; anchor.Anchored = true; anchor.Transparency = 1
        anchor.CanCollide = false; anchor.CanQuery = false; anchor.CanTouch = false; anchor.Parent = folder
        local gui = Instance.new("BillboardGui"); gui.Size = UDim2.fromOffset(120, 52); gui.StudsOffset = Vector3.new(0, 2.5, 0)
        gui.AlwaysOnTop = true; gui.Enabled = false; gui.Parent = anchor
        local label = Instance.new("TextLabel"); label.Size = UDim2.fromScale(1, 1); label.BackgroundTransparency = 1
        label.Font = Enum.Font.GothamBold; label.TextSize = 18; label.TextStrokeTransparency = 0.25; label.Parent = gui
        damagePool[i] = {anchor = anchor, gui = gui, label = label, serial = 0}
    end
    local hurtFlash = Instance.new("Frame"); hurtFlash.Name = "DamageVignette"; hurtFlash.Size = UDim2.fromScale(1, 1)
    hurtFlash.BackgroundColor3 = Color3.fromRGB(142, 31, 31); hurtFlash.BackgroundTransparency = 1; hurtFlash.BorderSizePixel = 0
    hurtFlash.ZIndex = 50; hurtFlash.Parent = app.Root
    local markerSerial = 0
    local function popup(data)
        if typeof(data.position) ~= "Vector3" then return end
        local item = damagePool[damageIndex]; damageIndex = damageIndex % #damagePool + 1; item.serial += 1
        local serial = item.serial; local cash = data.cash == true; local duration = cash and 0.82 or 0.55
        item.anchor.CFrame = CFrame.new(data.position); item.gui.Enabled = true
        item.gui.StudsOffset = Vector3.new(0, cash and 1.8 or 2.5, 0); item.label.TextTransparency = 0; item.label.TextStrokeTransparency = 0.25
        item.label.Text = cash and ("+" .. tostring(math.max(0, math.floor(data.amount or 0))) .. " CASH")
            or ((data.killed and "☠  " or data.critical and "CRIT  " or "") .. tostring(math.max(1, math.floor((data.damage or 0) + 0.5))))
        item.label.TextColor3 = cash and Color3.fromRGB(112, 238, 145) or data.killed and Color3.fromRGB(255, 211, 92)
            or data.critical and Color3.fromRGB(255, 132, 76) or data.armored and Color3.fromRGB(100, 194, 255) or Color3.fromRGB(238, 238, 220)
        TweenService:Create(item.gui, TweenInfo.new(duration, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {StudsOffset = Vector3.new(0, cash and 4.8 or 4.2, 0)}):Play()
        TweenService:Create(item.label, TweenInfo.new(duration), {TextTransparency = 1, TextStrokeTransparency = 1}):Play()
        task.delay(duration + 0.03, function() if item.serial == serial then item.gui.Enabled = false end end)
    end
    app.Remotes.EffectsRemote.OnClientEvent:Connect(function(kind, data)
        data = data or {}
        if kind == "shot" or kind == "acid" or kind == "electric" then
            if not data.melee then
                local e = Pool[nextIndex]; nextIndex = nextIndex % #Pool + 1; e.serial += 1
                local serial = e.serial; local length = (data.to - data.from).Magnitude
                if length > 0.01 then
                    e.part.Color = kind == "acid" and Color3.fromRGB(151, 212, 70) or kind == "electric" and Color3.fromRGB(76, 218, 245) or Color3.fromRGB(255, 190, 100)
                    local thickness = kind == "acid" and 0.14 or kind == "electric" and 0.1 or 0.045
                    e.part.Size = Vector3.new(thickness, thickness, length)
                    e.part.CFrame = CFrame.lookAt((data.from + data.to) / 2, data.to); e.part.Transparency = kind == "acid" and 0.25 or kind == "electric" and 0.08 or 0.12
                    task.delay(kind == "acid" and 0.16 or kind == "electric" and 0.12 or 0.07, function() if e.serial == serial then e.part.Transparency = 1 end end)
                end
            end
            if kind == "shot" and data.user == app.Player.UserId and data.melee then
                local char = app.Player.Character; local tool = char and char:FindFirstChildOfClass("Tool")
                if tool then
                    local original = tool.Grip; local itemId = tool:GetAttribute("ItemId")
                    if itemId == "crowbar" then
                        local serial = (tool:GetAttribute("SwingSerial") or 0) + 1; tool:SetAttribute("SwingSerial", serial)
                        TweenService:Create(tool, TweenInfo.new(0.07, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {Grip = original * CFrame.Angles(math.rad(24), math.rad(-10), math.rad(-22))}):Play()
                        task.delay(0.065, function() if tool.Parent and tool:GetAttribute("SwingSerial") == serial then TweenService:Create(tool, TweenInfo.new(0.11, Enum.EasingStyle.Quad, Enum.EasingDirection.In), {Grip = original * CFrame.Angles(math.rad(-88), math.rad(8), math.rad(30))}):Play() end end)
                        task.delay(0.18, function() if tool.Parent and tool:GetAttribute("SwingSerial") == serial then TweenService:Create(tool, TweenInfo.new(0.16, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {Grip = original}):Play() end end)
                    else
                        TweenService:Create(tool, TweenInfo.new(0.12, Enum.EasingStyle.Quad, Enum.EasingDirection.Out, 0, true), {Grip = original * CFrame.Angles(math.rad(-65), 0, math.rad(20))}):Play()
                    end
                end
            end
        elseif kind == "grenade_throw" then
            impact(data.from, data.grenade == "incendiary" and Color3.fromRGB(255, 111, 35) or Color3.fromRGB(180, 188, 154), 0.38)
        elseif kind == "grenade_explosion" then
            blast(data, data.incendiary == true); impact(data.from, data.concussion and Color3.fromRGB(225, 238, 255) or Color3.fromRGB(255, 165, 62), 1.1)
        elseif kind == "grenade_burn" then
            blast(data, true)
        elseif kind == "impact" then
            impact(data.from, Color3.fromRGB(231, 200, 145), 0.28)
        elseif kind == "build_spark" then
            impact(data.from, Color3.fromRGB(255, 187, 72), 0.48)
        elseif kind == "zombie_howl" then
            data.electric = true; data.radius = math.min(data.radius or 12, 24); blast(data, false)
            impact(data.from, Color3.fromRGB(234, 101, 73), 0.72)
        elseif kind == "zombie_hurt" then
            impact(data.from, Color3.fromRGB(164, 204, 104), data.boss and 0.5 or 0.34)
        elseif kind == "zombie_death" then
            impact(data.from, data.boss and Color3.fromRGB(231, 96, 235) or Color3.fromRGB(105, 137, 91), data.boss and 0.9 or 0.55)
        elseif kind == "farm_cash" and type(data.amount) == "number" then
            popup({position = data.position, amount = math.clamp(data.amount, 0, 1000), cash = true})
        elseif kind == "hit_confirm" then
            impact(data.position, data.armored and Color3.fromRGB(91, 180, 232) or data.killed and Color3.fromRGB(255, 211, 92) or Color3.fromRGB(225, 232, 205), data.killed and 0.5 or 0.32)
            markerSerial += 1; local serial = markerSerial
            app.HitMarker.Text = data.killed and "✦" or data.critical and "×" or "+"
            app.HitMarker.TextColor3 = data.killed and Color3.fromRGB(255, 211, 92) or data.critical and Color3.fromRGB(255, 128, 72)
                or data.armored and Color3.fromRGB(94, 184, 255) or app.UI.Theme.accent
            app.HitMarker.Visible = true; popup(data)
            task.delay(data.killed and 0.22 or 0.12, function() if markerSerial == serial then app.HitMarker.Visible = false end end)
        elseif kind == "player_hurt" then
            hurtFlash.BackgroundColor3 = data.damageType == "electric" and Color3.fromRGB(38, 146, 184) or data.damageType == "acid" and Color3.fromRGB(91, 139, 46) or Color3.fromRGB(142, 31, 31)
            hurtFlash.BackgroundTransparency = math.clamp(0.78 + math.min(0.14, 0.14 - (data.damage or 0) * 0.004), 0.72, 0.9)
            TweenService:Create(hurtFlash, TweenInfo.new(0.3), {BackgroundTransparency = 1}):Play()
            task.delay(0.34, function() if hurtFlash.BackgroundTransparency >= 0.99 then hurtFlash.BackgroundColor3 = Color3.fromRGB(142, 31, 31) end end)
        elseif kind == "medic_heal" then
            hurtFlash.BackgroundColor3 = Color3.fromRGB(53, 145, 91); hurtFlash.BackgroundTransparency = 0.9
            TweenService:Create(hurtFlash, TweenInfo.new(0.35), {BackgroundTransparency = 1}):Play()
            task.delay(0.4, function() hurtFlash.BackgroundColor3 = Color3.fromRGB(142, 31, 31) end)
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
