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
function Factory.rig(name, cf, shirt, skin, scale)
    scale = scale or 1
    local m = Instance.new("Model"); m.Name = name; m.ModelStreamingMode = Enum.ModelStreamingMode.Atomic
    local function limb(n, size, offset, color)
        local p = part(m, n, size * scale, cf * CFrame.new(offset * scale), color, Enum.Material.SmoothPlastic)
        p.Anchored = false; p.CanCollide = n == "Torso"; p.Massless = n ~= "HumanoidRootPart"
        return p
    end
    local root = limb("HumanoidRootPart", Vector3.new(2, 2, 1), Vector3.zero, shirt); root.Transparency = 1
    local torso = limb("Torso", Vector3.new(2, 2, 1), Vector3.zero, shirt)
    local head = limb("Head", Vector3.new(1.6, 1, 1), Vector3.new(0, 1.5, 0), skin)
    local leftArm = limb("Left Arm", Vector3.new(1, 2, 1), Vector3.new(-1.5, 0, 0), shirt)
    local rightArm = limb("Right Arm", Vector3.new(1, 2, 1), Vector3.new(1.5, 0, 0), shirt)
    local leftLeg = limb("Left Leg", Vector3.new(1, 2, 1), Vector3.new(-0.5, -2, 0), dark)
    local rightLeg = limb("Right Leg", Vector3.new(1, 2, 1), Vector3.new(0.5, -2, 0), dark)
    local function motor(n, a, b, offset0, offset1)
        local j = Instance.new("Motor6D"); j.Name = n; j.Part0 = a; j.Part1 = b
        j.C0 = CFrame.new(offset0 * scale); j.C1 = CFrame.new(offset1 * scale); j.Parent = a
        return j
    end
    motor("RootJoint", root, torso, Vector3.zero, Vector3.zero)
    motor("Neck", torso, head, Vector3.new(0, 1, 0), Vector3.new(0, -0.5, 0))
    motor("Left Shoulder", torso, leftArm, Vector3.new(-1, 0.5, 0), Vector3.new(0.5, 0.5, 0))
    motor("Right Shoulder", torso, rightArm, Vector3.new(1, 0.5, 0), Vector3.new(-0.5, 0.5, 0))
    motor("Left Hip", torso, leftLeg, Vector3.new(-0.5, -1, 0), Vector3.new(0, 1, 0))
    motor("Right Hip", torso, rightLeg, Vector3.new(0.5, -1, 0), Vector3.new(0, 1, 0))
    -- Eyes use geometry, not restricted third-party decals.
    for _, x in {-0.35, 0.35} do
        local eye = part(m, "Eye", Vector3.new(0.16, 0.16, 0.05) * scale, head.CFrame * CFrame.new(x * scale, 0.12 * scale, -0.52 * scale), Color3.fromRGB(21, 26, 24))
        eye.Anchored = false; eye.CanCollide = false; eye.Massless = true
        local weld = Instance.new("WeldConstraint"); weld.Part0 = head; weld.Part1 = eye; weld.Parent = eye
    end
    local h = Instance.new("Humanoid"); h.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
    h.BreakJointsOnDeath = false; h.WalkSpeed = 9; h.Parent = m
    m.PrimaryPart = root
    return m, h
end
function Factory.animate(model, walking, phase, zombie)
    local torso = model:FindFirstChild("Torso"); if not torso then return end
    local amplitude = walking and math.sin(phase) * 0.55 or 0
    local scale = torso.Size.Y / 2
    for _, data in {{"Left Hip", -0.5, -1, amplitude}, {"Right Hip", 0.5, -1, -amplitude}, {"Left Shoulder", -1, 0.5, zombie and -0.9 or -amplitude}, {"Right Shoulder", 1, 0.5, zombie and -0.9 or amplitude}} do
        local joint = torso:FindFirstChild(data[1])
        if joint then joint.C0 = CFrame.new(data[2] * scale, data[3] * scale, 0) * CFrame.Angles(data[4], 0, 0) end
    end
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
