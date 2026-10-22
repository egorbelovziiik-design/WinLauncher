--!nonstrict
local Factory = {}
local function part(parent, name, size, cf, color, material)
    local p = Instance.new("Part")
    p.Name = name; p.Size = size; p.CFrame = cf; p.Color = color
    p.Material = material or Enum.Material.Metal; p.Anchored = true
    p.TopSurface = Enum.SurfaceType.Smooth; p.BottomSurface = Enum.SurfaceType.Smooth
    p.Parent = parent; return p
end
Factory.part = part
local dark = Color3.fromRGB(43, 52, 57)
local orange = Color3.fromRGB(249, 115, 22)
function Factory.building(id, def, cf)
    local m = Instance.new("Model"); m.Name = def.name
    m.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
    local function p(name, size, x, y, z, color, material)
        return part(m, name, size, cf * CFrame.new(x, y, z), color, material)
    end
    local s = def.size
    local base = p("Foundation", Vector3.new(s[1], 0.6, s[3]), 0, 0.3, 0, dark, Enum.Material.Concrete)
    m.PrimaryPart = base
    if id == "wall" then
        for x = -5, 5, 2 do p("Timber", Vector3.new(1.8, 6, 1.6), x, 3.5, 0, Color3.fromRGB(104, 82, 58), Enum.Material.Wood) end
        for y = 2, 5, 3 do p("Brace", Vector3.new(12, 0.6, 0.4), 0, y, 1, dark) end
        p("Warning", Vector3.new(4, 0.5, 0.2), 0, 5.5, 1.3, orange)
    elseif id == "generator" or id == "diesel" then
        p("Engine", Vector3.new(s[1] - 2, 3.2, 5), 0, 2.2, 0, Color3.fromRGB(80, 101, 91))
        p("Tank", Vector3.new(2, 2.6, 6), s[1] / 2 - 1.5, 2, 0, orange)
        p("Exhaust", Vector3.new(0.5, 4, 0.5), -2, 3, 2, dark)
        for x = -2, 1, 0.7 do p("Vent", Vector3.new(0.2, 1.8, 0.2), x, 2.6, -2.6, dark) end
        local status = p("Status", Vector3.new(0.6, 0.6, 0.2), 0, 3.7, -2.6, orange, Enum.Material.Neon)
        local light = Instance.new("PointLight"); light.Color = orange; light.Range = 9; light.Brightness = 0.6; light.Parent = status
    elseif id == "farm" then
        p("Soil", Vector3.new(11, 0.4, 11), 0, 0.8, 0, Color3.fromRGB(62, 46, 36), Enum.Material.Ground)
        for z = -4, 4, 4 do
            p("Row", Vector3.new(11, 0.2, 0.3), 0, 1, z, dark, Enum.Material.Wood)
            for x = -4, 4, 2 do p("Crop", Vector3.new(0.8, 1, 0.8), x, 1.5, z, Color3.fromRGB(91, 126, 57), Enum.Material.Grass) end
        end
    elseif id == "well" then
        p("Well", Vector3.new(6, 3, 6), 0, 2, 0, Color3.fromRGB(100, 108, 108), Enum.Material.Cobblestone)
        p("Water", Vector3.new(4.8, 0.2, 4.8), 0, 3.55, 0, Color3.fromRGB(45, 116, 134), Enum.Material.Glass)
        p("Pump", Vector3.new(1.5, 5.5, 1.5), 2, 3.3, 2, orange)
        p("Spout", Vector3.new(3, 0.6, 0.6), 0.8, 5.8, 2, dark)
    elseif id == "workshop" then
        for _, x in {-7, 7} do for _, z in {-5, 5} do p("Post", Vector3.new(0.6, 9, 0.6), x, 4.8, z, dark) end end
        p("Roof", Vector3.new(16, 0.6, 12), 0, 9.7, 0, Color3.fromRGB(87, 99, 96))
        p("Workbench", Vector3.new(10, 0.6, 3), 0, 3, -3, Color3.fromRGB(142, 109, 69), Enum.Material.Wood)
        p("Cabinet", Vector3.new(4, 3, 3), -4, 1.8, -3, dark)
        p("Toolbox", Vector3.new(2, 1, 1.6), 2, 3.8, -3, orange)
    elseif id == "lamp" then
        p("Pole", Vector3.new(0.6, 11, 0.6), 0, 6, 0, dark)
        local head = p("Lens", Vector3.new(3, 0.8, 2), 0, 12, 0, Color3.fromRGB(253, 222, 160), Enum.Material.Neon)
        local light = Instance.new("PointLight"); light.Color = head.Color; light.Range = 48; light.Brightness = 2; light.Enabled = false; light.Parent = head
    elseif id == "turret" then
        p("Mount", Vector3.new(3, 5, 3), 0, 3, 0, dark)
        p("TurretHead", Vector3.new(4, 2, 3), 0, 6.5, 0, Color3.fromRGB(74, 90, 80))
        p("Barrel", Vector3.new(0.7, 0.7, 5), 0, 6.5, -3, dark)
        p("Sensor", Vector3.new(0.7, 0.4, 0.2), 0, 7.3, -1.6, orange, Enum.Material.Neon)
    end
    return m
end
function Factory.rig(name, cf, shirt, skin, scale, style)
    scale = scale or 1
    style = style or "survivor"
    local m = Instance.new("Model"); m.Name = name; m.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
    m:SetAttribute("BodyRig", "LC-R15")
    m:SetAttribute("AnimationStyle", style)
    m:SetAttribute("State", style:match("^zombie") and "ZombieIdle" or "Idle")
    local pants = style:match("^zombie") and Color3.fromRGB(49, 53, 49) or dark
    local function body(n, size, offset, color, rounded)
        local p = part(m, n, size * scale, cf * CFrame.new(offset * scale), color, Enum.Material.SmoothPlastic)
        p.Anchored = false; p.CanCollide = n == "LowerTorso"; p.Massless = n ~= "HumanoidRootPart"
        if rounded then
            local mesh = Instance.new("SpecialMesh"); mesh.MeshType = Enum.MeshType.Sphere; mesh.Scale = Vector3.new(1, 1, 1); mesh.Parent = p
        end
        return p
    end
    -- Fifteen articulated body segments use Roblox R15 names and player-like proportions.
    local root = body("HumanoidRootPart", Vector3.new(2, 2, 1), Vector3.zero, shirt, false); root.Transparency = 1; root.CanCollide = false; root.Massless = false
    local lower = body("LowerTorso", Vector3.new(1.62, 1.18, 0.9), Vector3.new(0, 0.15, 0), shirt, true)
    local upper = body("UpperTorso", Vector3.new(2.15, 1.62, 1), Vector3.new(0, 1.35, 0), shirt, true)
    local head = body("Head", Vector3.new(1.55, 1.55, 1.48), Vector3.new(0, 2.95, 0), skin, true)
    local leftUpperArm = body("LeftUpperArm", Vector3.new(0.75, 1.4, 0.75), Vector3.new(-1.4, 1.35, 0), shirt, true)
    local rightUpperArm = body("RightUpperArm", Vector3.new(0.75, 1.4, 0.75), Vector3.new(1.4, 1.35, 0), shirt, true)
    local leftLowerArm = body("LeftLowerArm", Vector3.new(0.65, 1.1, 0.65), Vector3.new(-1.4, 0.17, 0), skin, true)
    local rightLowerArm = body("RightLowerArm", Vector3.new(0.65, 1.1, 0.65), Vector3.new(1.4, 0.17, 0), skin, true)
    local leftHand = body("LeftHand", Vector3.new(0.67, 0.57, 0.67), Vector3.new(-1.4, -0.62, 0), skin, true)
    local rightHand = body("RightHand", Vector3.new(0.67, 0.57, 0.67), Vector3.new(1.4, -0.62, 0), skin, true)
    local leftUpperLeg = body("LeftUpperLeg", Vector3.new(0.84, 1.6, 0.86), Vector3.new(-0.45, -0.9, 0), pants, true)
    local rightUpperLeg = body("RightUpperLeg", Vector3.new(0.84, 1.6, 0.86), Vector3.new(0.45, -0.9, 0), pants, true)
    local leftLowerLeg = body("LeftLowerLeg", Vector3.new(0.72, 1.3, 0.75), Vector3.new(-0.45, -2.05, 0), pants, true)
    local rightLowerLeg = body("RightLowerLeg", Vector3.new(0.72, 1.3, 0.75), Vector3.new(0.45, -2.05, 0), pants, true)
    local leftFoot = body("LeftFoot", Vector3.new(0.78, 0.46, 1.25), Vector3.new(-0.45, -2.78, -0.18), pants, true)
    local rightFoot = body("RightFoot", Vector3.new(0.78, 0.46, 1.25), Vector3.new(0.45, -2.78, -0.18), pants, true)
    local function motor(n, a, b, offset0, offset1)
        local c0, c1 = CFrame.new(offset0 * scale), CFrame.new(offset1 * scale)
        local attachmentName = n .. "RigAttachment"
        local a0 = Instance.new("Attachment"); a0.Name = attachmentName; a0.CFrame = c0; a0.Parent = a
        local a1 = Instance.new("Attachment"); a1.Name = attachmentName; a1.CFrame = c1; a1.Parent = b
        local j = Instance.new("Motor6D"); j.Name = n; j.Part0 = a; j.Part1 = b
        j.C0 = c0; j.C1 = c1; j.Parent = a
        return j
    end
    motor("Root", root, lower, Vector3.zero, Vector3.new(0, -0.15, 0))
    motor("Waist", lower, upper, Vector3.new(0, 0.5, 0), Vector3.new(0, -0.7, 0))
    motor("Neck", upper, head, Vector3.new(0, 0.81, 0), Vector3.new(0, -0.79, 0))
    motor("LeftShoulder", upper, leftUpperArm, Vector3.new(-1.08, 0.4, 0), Vector3.new(0.32, 0.4, 0))
    motor("RightShoulder", upper, rightUpperArm, Vector3.new(1.08, 0.4, 0), Vector3.new(-0.32, 0.4, 0))
    motor("LeftElbow", leftUpperArm, leftLowerArm, Vector3.new(0, -0.67, 0), Vector3.new(0, 0.51, 0))
    motor("RightElbow", rightUpperArm, rightLowerArm, Vector3.new(0, -0.67, 0), Vector3.new(0, 0.51, 0))
    motor("LeftWrist", leftLowerArm, leftHand, Vector3.new(0, -0.53, 0), Vector3.new(0, 0.26, 0))
    motor("RightWrist", rightLowerArm, rightHand, Vector3.new(0, -0.53, 0), Vector3.new(0, 0.26, 0))
    motor("LeftHip", lower, leftUpperLeg, Vector3.new(-0.45, -0.45, 0), Vector3.new(0, 0.6, 0))
    motor("RightHip", lower, rightUpperLeg, Vector3.new(0.45, -0.45, 0), Vector3.new(0, 0.6, 0))
    motor("LeftKnee", leftUpperLeg, leftLowerLeg, Vector3.new(0, -0.65, 0), Vector3.new(0, 0.5, 0))
    motor("RightKnee", rightUpperLeg, rightLowerLeg, Vector3.new(0, -0.65, 0), Vector3.new(0, 0.5, 0))
    motor("LeftAnkle", leftLowerLeg, leftFoot, Vector3.new(0, -0.57, -0.03), Vector3.new(0, 0.16, 0.15))
    motor("RightAnkle", rightLowerLeg, rightFoot, Vector3.new(0, -0.57, -0.03), Vector3.new(0, 0.16, 0.15))
    local function detail(n, size, offset, color, attach, material, rounded)
        local p = part(m, n, size * scale, cf * CFrame.new(offset * scale), color, material or Enum.Material.SmoothPlastic)
        p.Anchored = false; p.CanCollide = false; p.CanQuery = false; p.CanTouch = false; p.Massless = true
        if rounded then local mesh = Instance.new("SpecialMesh"); mesh.MeshType = Enum.MeshType.Sphere; mesh.Parent = p end
        local weld = Instance.new("WeldConstraint"); weld.Part0 = attach; weld.Part1 = p; weld.Parent = p
        return p
    end
    -- Geometry-only face and role details: no external decals, packages or fake mesh IDs.
    for _, x in {-0.32, 0.32} do detail("Eye", Vector3.new(0.14, 0.18, 0.06), Vector3.new(x, 3.08, -0.73), Color3.fromRGB(20, 26, 25), head) end
    detail("Mouth", Vector3.new(0.42, 0.07, 0.05), Vector3.new(0, 2.67, -0.74), style:match("^zombie") and Color3.fromRGB(74, 36, 31) or Color3.fromRGB(92, 54, 44), head)
    if style == "alex" then
        detail("Beret", Vector3.new(1.65, 0.28, 1.45), Vector3.new(0, 3.72, 0), Color3.fromRGB(45, 66, 55), head, nil, true)
        detail("CommanderVest", Vector3.new(1.9, 1.35, 0.28), Vector3.new(0, 1.35, -0.58), Color3.fromRGB(43, 57, 49), upper, Enum.Material.Fabric)
        detail("RankPatch", Vector3.new(0.42, 0.25, 0.08), Vector3.new(-0.55, 1.52, -0.75), orange, upper, Enum.Material.Neon)
    elseif style == "max" then
        detail("Hair", Vector3.new(1.62, 0.55, 1.45), Vector3.new(0, 3.57, 0.08), Color3.fromRGB(64, 44, 31), head, nil, true)
        detail("Scarf", Vector3.new(1.35, 0.3, 1.02), Vector3.new(0, 2.2, 0), Color3.fromRGB(160, 106, 57), upper, Enum.Material.Fabric, true)
        detail("Satchel", Vector3.new(0.65, 1.25, 0.38), Vector3.new(1.03, 0.72, 0.28), Color3.fromRGB(104, 72, 44), lower, Enum.Material.Leather)
    elseif style == "victor" then
        detail("SafetyHelmet", Vector3.new(1.7, 0.52, 1.52), Vector3.new(0, 3.62, 0), Color3.fromRGB(221, 156, 45), head, Enum.Material.Plastic, true)
        detail("Goggles", Vector3.new(1.15, 0.26, 0.08), Vector3.new(0, 3.08, -0.76), Color3.fromRGB(45, 91, 105), head, Enum.Material.Glass)
        detail("ToolBelt", Vector3.new(1.76, 0.28, 1.02), Vector3.new(0, 0.45, 0), Color3.fromRGB(83, 61, 42), lower, Enum.Material.Leather)
    elseif style == "worker_mira" then
        detail("Hair", Vector3.new(1.62, 0.58, 1.45), Vector3.new(0, 3.58, 0.08), Color3.fromRGB(54, 40, 30), head, nil, true)
        detail("Bandanna", Vector3.new(1.62, 0.2, 1.5), Vector3.new(0, 3.45, -0.02), Color3.fromRGB(139, 69, 55), head, Enum.Material.Fabric)
    elseif style == "worker_lev" then
        detail("Cap", Vector3.new(1.65, 0.38, 1.52), Vector3.new(0, 3.65, 0), Color3.fromRGB(68, 85, 72), head, Enum.Material.Fabric, true)
        detail("CapBill", Vector3.new(1.05, 0.12, 0.65), Vector3.new(0, 3.5, -0.72), Color3.fromRGB(68, 85, 72), head, Enum.Material.Fabric)
    elseif style:match("^zombie") then
        detail("TornShirt", Vector3.new(1.85, 0.38, 1.08), Vector3.new(0, 0.78, 0), Color3.fromRGB(47, 57, 49), lower, Enum.Material.Fabric)
        if style == "zombie_tank" then
            detail("ShoulderMass", Vector3.new(3.05, 0.48, 1.25), Vector3.new(0, 1.78, 0), Color3.fromRGB(77, 83, 79), upper, nil, true)
        end
    else
        detail("Hair", Vector3.new(1.62, 0.48, 1.45), Vector3.new(0, 3.62, 0.06), Color3.fromRGB(62, 49, 39), head, nil, true)
    end
    local h = Instance.new("Humanoid"); h.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
    h.BreakJointsOnDeath = false; h.RequiresNeck = true; h.RigType = Enum.HumanoidRigType.R15
    h.AutomaticScalingEnabled = false; h.WalkSpeed = 9; h.HipHeight = 2 * scale; h.Parent = m
    local animator = Instance.new("Animator"); animator.Parent = h
    m.PrimaryPart = root
    return m, h
end
function Factory.animate(model, walking, _phase, zombie)
    -- Compatibility shim. Smooth joint poses are evaluated locally by ActorAnimationController.
    model:SetAttribute("State", walking and (zombie and "ZombieWalk" or "Walking") or (zombie and "ZombieIdle" or "Idle"))
end
function Factory.weapon(id, color)
    local tool = Instance.new("Tool"); tool.Name = id; tool.CanBeDropped = false
    tool.RequiresHandle = true; tool.ManualActivationOnly = false
    local h = part(tool, "Handle", id == "crowbar" and Vector3.new(0.25, 3.5, 0.25) or Vector3.new(0.4, 0.9, 0.6), CFrame.new(), color or dark)
    h.Anchored = false; h.CanCollide = false; h.Massless = true
    local function add(name, size, offset, tint)
        local p = part(tool, name, size, h.CFrame * offset, tint); p.Anchored = false; p.CanCollide = false; p.Massless = true
        local w = Instance.new("WeldConstraint"); w.Part0 = h; w.Part1 = p; w.Parent = p
    end
    if id == "crowbar" then
        add("Hook", Vector3.new(0.65, 0.25, 0.25), CFrame.new(0.2, 1.7, 0), color or dark)
        tool.Grip = CFrame.new(0, -0.7, 0) * CFrame.Angles(math.rad(15), 0, 0)
    else
        add("Barrel", Vector3.new(0.45, 0.45, id == "rifle" and 3.4 or 1.5), CFrame.new(0, 0.5, -0.6), color or dark)
        add("Sight", Vector3.new(0.2, 0.15, 0.2), CFrame.new(0, 0.8, -1), orange)
        tool.Grip = CFrame.new(0, -0.1, 0)
    end
    return tool
end
return Factory
