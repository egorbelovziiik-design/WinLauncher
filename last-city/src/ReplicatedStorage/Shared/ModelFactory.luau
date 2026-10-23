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
    elseif id == "metal_wall" then
        for x = -5, 5, 2 do p("SteelPost", Vector3.new(0.55, 7.2, 1), x, 4, 0, Color3.fromRGB(68, 81, 84), Enum.Material.Metal) end
        for y = 2, 6, 2 do p("SteelPlate", Vector3.new(11.7, 1.55, 0.42), 0, y, 0, Color3.fromRGB(94, 108, 108), Enum.Material.DiamondPlate) end
        p("ConcreteFoot", Vector3.new(12, 1.1, 3.2), 0, 0.85, 0, Color3.fromRGB(99, 102, 98), Enum.Material.Concrete)
        p("Reflector", Vector3.new(4, 0.35, 0.18), 0, 6.8, -0.35, orange, Enum.Material.Neon)
    elseif id == "spikes" then
        for x = -3, 3, 2 do for z = -3, 3, 2 do
            local spike = p("Spike", Vector3.new(0.45, 2.2, 0.45), x, 1.4, z, Color3.fromRGB(112, 119, 116), Enum.Material.Metal)
            spike.CFrame = spike.CFrame * CFrame.Angles(0, 0, math.rad((x + z) % 2 == 0 and 16 or -16))
        end end
        p("TrapFrame", Vector3.new(7.4, 0.5, 7.4), 0, 0.75, 0, dark, Enum.Material.DiamondPlate)
    elseif id == "generator" or id == "diesel" or id == "advanced_generator" then
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
    elseif id == "greenhouse" then
        for _, x in {-7, 7} do for _, z in {-5, 5} do p("Frame", Vector3.new(0.45, 7, 0.45), x, 4, z, dark) end end
        for _, z in {-5.5, 5.5} do p("GlassWall", Vector3.new(14.5, 6.5, 0.25), 0, 4, z, Color3.fromRGB(125, 179, 184), Enum.Material.Glass) end
        for _, x in {-7.5, 7.5} do p("GlassWall", Vector3.new(0.25, 6.5, 11), x, 4, 0, Color3.fromRGB(125, 179, 184), Enum.Material.Glass) end
        p("GlassRoof", Vector3.new(15, 0.35, 11), 0, 7.6, 0, Color3.fromRGB(145, 197, 195), Enum.Material.Glass)
        for z = -3, 3, 3 do p("GrowBed", Vector3.new(12, 0.7, 1.6), 0, 1, z, Color3.fromRGB(65, 78, 48), Enum.Material.Ground) end
    elseif id == "well" then
        p("Well", Vector3.new(6, 3, 6), 0, 2, 0, Color3.fromRGB(100, 108, 108), Enum.Material.Cobblestone)
        p("Water", Vector3.new(4.8, 0.2, 4.8), 0, 3.55, 0, Color3.fromRGB(45, 116, 134), Enum.Material.Glass)
        p("Pump", Vector3.new(1.5, 5.5, 1.5), 2, 3.3, 2, orange)
        p("Spout", Vector3.new(3, 0.6, 0.6), 0.8, 5.8, 2, dark)
    elseif id == "purifier" then
        for x = -3, 3, 3 do
            local tank = p("FilterTank", Vector3.new(2.7, 6, 2.7), x, 3.6, 0, Color3.fromRGB(82, 105, 108), Enum.Material.Metal)
            tank.Shape = Enum.PartType.Cylinder
            p("Pipe", Vector3.new(0.55, 2.2, 0.55), x, 6.5, 2.2, orange, Enum.Material.Metal)
        end
        p("PipeRack", Vector3.new(9, 0.55, 0.55), 0, 7.2, 2.2, orange, Enum.Material.Metal)
        p("ControlBox", Vector3.new(2.4, 3, 1.4), 4.5, 2.1, -2.8, dark, Enum.Material.Metal)
    elseif id == "workshop" then
        for _, x in {-7, 7} do for _, z in {-5, 5} do p("Post", Vector3.new(0.6, 9, 0.6), x, 4.8, z, dark) end end
        p("Roof", Vector3.new(16, 0.6, 12), 0, 9.7, 0, Color3.fromRGB(87, 99, 96))
        p("Workbench", Vector3.new(10, 0.6, 3), 0, 3, -3, Color3.fromRGB(142, 109, 69), Enum.Material.Wood)
        p("Cabinet", Vector3.new(4, 3, 3), -4, 1.8, -3, dark)
        p("Toolbox", Vector3.new(2, 1, 1.6), 2, 3.8, -3, orange)
    elseif id == "ammo_press" then
        p("PressBody", Vector3.new(4, 6.5, 4), -2.5, 3.7, 0, Color3.fromRGB(72, 89, 83), Enum.Material.Metal)
        p("PressRam", Vector3.new(1.2, 5, 1.2), -2.5, 4.2, -2.2, orange, Enum.Material.Metal)
        p("FeedHopper", Vector3.new(3.5, 2.2, 3.5), 2.5, 5.5, 0, Color3.fromRGB(103, 111, 106), Enum.Material.DiamondPlate)
        p("AmmoTray", Vector3.new(5, 0.5, 3), 1.5, 1.4, -3.1, dark, Enum.Material.Metal)
        p("Guard", Vector3.new(11, 0.4, 0.4), 0, 7.4, 4, orange, Enum.Material.Neon)
    elseif id == "field_hospital" then
        for _, x in {-6, 6} do for _, z in {-5, 5} do p("TentPost", Vector3.new(0.4, 8, 0.4), x, 4.3, z, dark) end end
        p("Canopy", Vector3.new(13.5, 0.45, 11.5), 0, 8.4, 0, Color3.fromRGB(188, 194, 176), Enum.Material.Fabric)
        for x = -3.5, 3.5, 7 do p("MedicalCot", Vector3.new(5.5, 0.65, 2.4), x, 1.7, 1, Color3.fromRGB(175, 185, 170), Enum.Material.Fabric) end
        p("MedicalCabinet", Vector3.new(3, 5, 2), 4.8, 3, -3.8, Color3.fromRGB(211, 218, 204), Enum.Material.Metal)
        p("MedicalMark", Vector3.new(1.2, 1.2, 0.15), 4.8, 4, -4.85, Color3.fromRGB(184, 61, 55), Enum.Material.Neon)
    elseif id == "lamp" then
        p("Pole", Vector3.new(0.6, 11, 0.6), 0, 6, 0, dark)
        local head = p("Lens", Vector3.new(3, 0.8, 2), 0, 12, 0, Color3.fromRGB(253, 222, 160), Enum.Material.Neon)
        local light = Instance.new("PointLight"); light.Color = head.Color; light.Range = 48; light.Brightness = 2; light.Enabled = false; light.Parent = head
    elseif id == "watchtower" then
        for _, x in {-4, 4} do for _, z in {-4, 4} do p("TowerLeg", Vector3.new(0.7, 15, 0.7), x, 7.8, z, Color3.fromRGB(77, 67, 52), Enum.Material.Wood) end end
        p("Platform", Vector3.new(10, 0.8, 10), 0, 14, 0, Color3.fromRGB(92, 78, 58), Enum.Material.WoodPlanks)
        p("Roof", Vector3.new(11, 0.6, 11), 0, 17.5, 0, Color3.fromRGB(72, 84, 78), Enum.Material.Metal)
        for _, x in {-4.5, 4.5} do p("Rail", Vector3.new(0.25, 2.5, 9), x, 15.3, 0, dark) end
        for _, z in {-4.5, 4.5} do p("Rail", Vector3.new(9, 2.5, 0.25), 0, 15.3, z, dark) end
    elseif id == "radio_tower" then
        for _, x in {-2.5, 2.5} do for _, z in {-2.5, 2.5} do p("MastLeg", Vector3.new(0.35, 20, 0.35), x, 10.3, z, Color3.fromRGB(76, 88, 89), Enum.Material.Metal) end end
        for y = 3, 18, 3 do
            p("CrossBrace", Vector3.new(6, 0.25, 0.25), 0, y, -2.5, orange, Enum.Material.Metal)
            p("CrossBrace", Vector3.new(0.25, 0.25, 6), 2.5, y, 0, orange, Enum.Material.Metal)
        end
        p("Antenna", Vector3.new(0.25, 5, 0.25), 0, 22, 0, orange, Enum.Material.Neon)
        p("RadioCabinet", Vector3.new(3.5, 3, 3), 0, 1.8, 0, dark, Enum.Material.Metal)
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
    elseif style == "ira" then
        detail("Hair", Vector3.new(1.66, 0.55, 1.48), Vector3.new(0, 3.58, 0.08), Color3.fromRGB(73, 44, 31), head, nil, true)
        detail("MinerHelmet", Vector3.new(1.76, 0.36, 1.58), Vector3.new(0, 3.78, 0), Color3.fromRGB(196, 126, 37), head, Enum.Material.Plastic, true)
        detail("HeadLamp", Vector3.new(0.42, 0.42, 0.18), Vector3.new(0, 3.7, -0.82), orange, head, Enum.Material.Neon)
    elseif style == "resident_medic" then
        detail("MedicalCap", Vector3.new(1.67, 0.3, 1.48), Vector3.new(0, 3.68, 0), Color3.fromRGB(220, 225, 214), head, Enum.Material.Fabric, true)
        detail("MedicalPanel", Vector3.new(1.8, 1.28, 0.22), Vector3.new(0, 1.34, -0.6), Color3.fromRGB(218, 224, 211), upper, Enum.Material.Fabric)
        detail("Cross", Vector3.new(0.35, 0.35, 0.08), Vector3.new(-0.5, 1.45, -0.74), Color3.fromRGB(178, 63, 57), upper)
    elseif style == "resident_guard" then
        detail("GuardCap", Vector3.new(1.7, 0.38, 1.52), Vector3.new(0, 3.65, 0), Color3.fromRGB(43, 61, 67), head, Enum.Material.Fabric, true)
        detail("GuardVest", Vector3.new(1.92, 1.4, 0.3), Vector3.new(0, 1.3, -0.58), Color3.fromRGB(50, 61, 59), upper, Enum.Material.Fabric)
    elseif style == "resident_mechanic" then
        detail("MechanicCap", Vector3.new(1.68, 0.32, 1.5), Vector3.new(0, 3.67, 0), Color3.fromRGB(79, 91, 79), head, Enum.Material.Fabric, true)
        detail("ToolBelt", Vector3.new(1.76, 0.28, 1.02), Vector3.new(0, 0.45, 0), Color3.fromRGB(83, 61, 42), lower, Enum.Material.Leather)
    elseif style == "resident_volunteer" then
        detail("Hair", Vector3.new(1.63, 0.52, 1.46), Vector3.new(0, 3.6, 0.06), Color3.fromRGB(61, 42, 32), head, nil, true)
        detail("ReliefArmband", Vector3.new(0.82, 0.34, 0.82), Vector3.new(-1.4, 1.38, 0), Color3.fromRGB(193, 92, 58), leftUpperArm, Enum.Material.Fabric, true)
    elseif style == "resident_farmer" then
        detail("FieldHat", Vector3.new(2.1, 0.28, 1.9), Vector3.new(0, 3.7, 0), Color3.fromRGB(155, 125, 72), head, Enum.Material.Fabric, true)
        detail("Apron", Vector3.new(1.62, 1.45, 0.2), Vector3.new(0, 1.05, -0.58), Color3.fromRGB(76, 94, 66), upper, Enum.Material.Fabric)
    elseif style == "resident_scout" then
        detail("Hood", Vector3.new(1.72, 0.52, 1.55), Vector3.new(0, 3.58, 0.1), Color3.fromRGB(56, 69, 64), head, Enum.Material.Fabric, true)
        detail("Backpack", Vector3.new(1.45, 1.65, 0.55), Vector3.new(0, 1.2, 0.72), Color3.fromRGB(65, 72, 58), upper, Enum.Material.Fabric)
    elseif style == "resident_cook" then
        detail("CookCap", Vector3.new(1.72, 0.62, 1.5), Vector3.new(0, 3.76, 0), Color3.fromRGB(221, 218, 200), head, Enum.Material.Fabric, true)
        detail("Apron", Vector3.new(1.65, 1.5, 0.2), Vector3.new(0, 1.05, -0.58), Color3.fromRGB(211, 201, 179), upper, Enum.Material.Fabric)
    elseif style == "worker_mira" then
        detail("Hair", Vector3.new(1.62, 0.58, 1.45), Vector3.new(0, 3.58, 0.08), Color3.fromRGB(54, 40, 30), head, nil, true)
        detail("Bandanna", Vector3.new(1.62, 0.2, 1.5), Vector3.new(0, 3.45, -0.02), Color3.fromRGB(139, 69, 55), head, Enum.Material.Fabric)
    elseif style == "worker_lev" then
        detail("Cap", Vector3.new(1.65, 0.38, 1.52), Vector3.new(0, 3.65, 0), Color3.fromRGB(68, 85, 72), head, Enum.Material.Fabric, true)
        detail("CapBill", Vector3.new(1.05, 0.12, 0.65), Vector3.new(0, 3.5, -0.72), Color3.fromRGB(68, 85, 72), head, Enum.Material.Fabric)
    elseif style == "builder" then
        detail("BuilderHelmet", Vector3.new(1.72, 0.48, 1.55), Vector3.new(0, 3.68, 0), Color3.fromRGB(229, 158, 48), head, Enum.Material.Plastic, true)
        detail("BuilderVest", Vector3.new(1.92, 1.38, 0.28), Vector3.new(0, 1.32, -0.59), Color3.fromRGB(198, 104, 39), upper, Enum.Material.Fabric)
        detail("ToolBelt", Vector3.new(1.8, 0.3, 1.02), Vector3.new(0, 0.43, 0), Color3.fromRGB(75, 55, 39), lower, Enum.Material.Leather)
        detail("Hammer", Vector3.new(0.32, 1.7, 0.32), Vector3.new(1.05, 0.2, 0.15), Color3.fromRGB(108, 79, 48), lower, Enum.Material.Wood)
    elseif style == "major" then
        detail("MajorBeret", Vector3.new(1.68, 0.3, 1.48), Vector3.new(0, 3.72, 0), Color3.fromRGB(48, 71, 51), head, Enum.Material.Fabric, true)
        detail("MilitaryVest", Vector3.new(2, 1.5, 0.36), Vector3.new(0, 1.3, -0.59), Color3.fromRGB(55, 75, 56), upper, Enum.Material.Fabric)
        detail("RankBars", Vector3.new(0.5, 0.22, 0.08), Vector3.new(-0.55, 1.55, -0.8), orange, upper, Enum.Material.Neon)
    elseif style == "quartermaster" then
        detail("GarrisonCap", Vector3.new(1.68, 0.36, 1.5), Vector3.new(0, 3.68, 0), Color3.fromRGB(56, 74, 57), head, Enum.Material.Fabric, true)
        detail("SupplyVest", Vector3.new(1.94, 1.42, 0.32), Vector3.new(0, 1.3, -0.58), Color3.fromRGB(67, 78, 56), upper, Enum.Material.Fabric)
        detail("LedgerBag", Vector3.new(0.72, 1.3, 0.4), Vector3.new(-1.05, 0.8, 0.25), Color3.fromRGB(90, 68, 45), lower, Enum.Material.Leather)
    elseif style == "pilot" then
        detail("PilotHelmet", Vector3.new(1.72, 0.58, 1.57), Vector3.new(0, 3.61, 0), Color3.fromRGB(63, 75, 70), head, Enum.Material.Plastic, true)
        detail("Visor", Vector3.new(1.28, 0.4, 0.12), Vector3.new(0, 3.08, -0.78), Color3.fromRGB(50, 83, 91), head, Enum.Material.Glass)
        detail("FlightHarness", Vector3.new(1.92, 1.4, 0.3), Vector3.new(0, 1.31, -0.58), Color3.fromRGB(82, 91, 73), upper, Enum.Material.Fabric)
    elseif style:match("^soldier_") then
        detail("CombatHelmet", Vector3.new(1.78, 0.56, 1.62), Vector3.new(0, 3.64, 0), Color3.fromRGB(54, 72, 55), head, Enum.Material.Plastic, true)
        detail("MilitaryArmor", Vector3.new(2.03, 1.55, 0.4), Vector3.new(0, 1.3, -0.6), Color3.fromRGB(48, 68, 51), upper, Enum.Material.Fabric)
        detail("ShoulderGuard", Vector3.new(3.02, 0.3, 0.84), Vector3.new(0, 1.82, 0), Color3.fromRGB(61, 78, 61), upper, Enum.Material.Metal)
        detail("RifleBody", Vector3.new(0.5, 0.65, 2.7), Vector3.new(1.4, -0.42, -1.1), Color3.fromRGB(48, 56, 54), rightHand, Enum.Material.Metal)
        detail("RifleBarrel", Vector3.new(0.25, 0.25, 2.4), Vector3.new(1.4, -0.38, -3.25), Color3.fromRGB(35, 42, 42), rightHand, Enum.Material.Metal)
        detail("RifleMagazine", Vector3.new(0.48, 0.9, 0.55), Vector3.new(1.4, -0.96, -1.25), Color3.fromRGB(57, 65, 61), rightHand, Enum.Material.Metal)
    elseif style:match("^zombie") then
        detail("TornShirt", Vector3.new(1.85, 0.38, 1.08), Vector3.new(0, 0.78, 0), Color3.fromRGB(47, 57, 49), lower, Enum.Material.Fabric)
        if style == "zombie_tank" or style == "zombie_brute" then
            detail("ShoulderMass", Vector3.new(3.05, 0.48, 1.25), Vector3.new(0, 1.78, 0), Color3.fromRGB(77, 83, 79), upper, nil, true)
        elseif style == "zombie_runner" then
            detail("RunnerWrap", Vector3.new(1.62, 0.28, 1.5), Vector3.new(0, 3.34, 0), Color3.fromRGB(112, 79, 55), head, Enum.Material.Fabric, true)
        elseif style == "zombie_lurker" then
            detail("LurkerHood", Vector3.new(1.72, 0.58, 1.58), Vector3.new(0, 3.55, 0.08), Color3.fromRGB(42, 57, 53), head, Enum.Material.Fabric, true)
        elseif style == "zombie_ravager" then
            detail("RavagerPlate", Vector3.new(1.9, 1.1, 0.3), Vector3.new(0, 1.3, -0.58), Color3.fromRGB(97, 64, 56), upper, Enum.Material.Slate)
        elseif style == "zombie_armored" then
            detail("BrokenVisor", Vector3.new(1.25, 0.3, 0.1), Vector3.new(0, 3.05, -0.77), Color3.fromRGB(50, 75, 79), head, Enum.Material.Glass)
        elseif style == "zombie_corrosive" then
            detail("CorrosiveSac", Vector3.new(1.35, 1.5, 0.75), Vector3.new(0, 1.2, 0.78), Color3.fromRGB(123, 163, 64), upper, Enum.Material.Neon, true)
            detail("Respirator", Vector3.new(0.75, 0.55, 0.25), Vector3.new(0, 2.75, -0.8), Color3.fromRGB(73, 91, 62), head, Enum.Material.Metal)
        elseif style == "zombie_stalker" then
            detail("StalkerHood", Vector3.new(1.75, 0.62, 1.6), Vector3.new(0, 3.56, 0.08), Color3.fromRGB(37, 48, 49), head, Enum.Material.Fabric, true)
            detail("StalkerWrap", Vector3.new(1.9, 1.25, 0.25), Vector3.new(0, 1.3, -0.58), Color3.fromRGB(45, 54, 55), upper, Enum.Material.Fabric)
        elseif style == "zombie_mine_guard" then
            detail("CrackedMinerHelmet", Vector3.new(1.78, 0.45, 1.6), Vector3.new(0, 3.7, 0), Color3.fromRGB(190, 119, 36), head, Enum.Material.Plastic, true)
            detail("MineLamp", Vector3.new(0.38, 0.38, 0.18), Vector3.new(0, 3.66, -0.84), orange, head, Enum.Material.Neon)
        elseif style == "zombie_warden" then
            detail("WardenMask", Vector3.new(1.38, 0.72, 0.18), Vector3.new(0, 3.05, -0.79), Color3.fromRGB(54, 66, 66), head, Enum.Material.Metal)
            detail("WardenArmor", Vector3.new(2.1, 1.55, 0.4), Vector3.new(0, 1.3, -0.6), Color3.fromRGB(58, 69, 67), upper, Enum.Material.Metal)
        elseif style == "zombie_rainbow" then
            detail("PrismCrown", Vector3.new(1.72, 0.48, 1.58), Vector3.new(0, 3.75, 0), Color3.fromRGB(232, 89, 255), head, Enum.Material.Neon, true)
            detail("PrismArmor", Vector3.new(2.12, 1.6, 0.42), Vector3.new(0, 1.3, -0.61), Color3.fromRGB(88, 220, 255), upper, Enum.Material.Neon)
            detail("PrismBand", Vector3.new(2.25, 0.22, 0.48), Vector3.new(0, 1.48, -0.83), Color3.fromRGB(255, 220, 75), upper, Enum.Material.Neon)
            detail("PrismShoulders", Vector3.new(3.15, 0.32, 0.95), Vector3.new(0, 1.82, 0), Color3.fromRGB(105, 255, 164), upper, Enum.Material.Neon)
        elseif style == "zombie_foreman" then
            detail("ForemanLamp", Vector3.new(0.5, 0.5, 0.2), Vector3.new(0, 3.72, -0.86), orange, head, Enum.Material.Neon)
        end
    else
        detail("Hair", Vector3.new(1.62, 0.48, 1.45), Vector3.new(0, 3.62, 0.06), Color3.fromRGB(62, 49, 39), head, nil, true)
    end
    local h = Instance.new("Humanoid"); h.DisplayDistanceType = Enum.HumanoidDisplayDistanceType.None
    h.BreakJointsOnDeath = false; h.RequiresNeck = true; h.RigType = Enum.HumanoidRigType.R15
    h.AutomaticScalingEnabled = false; h.WalkSpeed = 9; h.HipHeight = 2 * scale
    h.UseJumpPower = true; h.JumpPower = 0; h.AutoJumpEnabled = false; h.Parent = m
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
    local sword = table.find({"short_sword", "saber", "longsword", "katana"}, id) ~= nil
    local melee = table.find({"crowbar", "bat", "hatchet", "machete", "pickaxe", "prism_blade", "short_sword", "saber", "longsword", "katana"}, id) ~= nil
    local handleSize = id == "crowbar" and Vector3.new(0.25, 3.5, 0.25)
        or id == "bat" and Vector3.new(0.52, 3.4, 0.52)
        or id == "hatchet" and Vector3.new(0.32, 2.55, 0.32)
        or id == "machete" and Vector3.new(0.34, 1.35, 0.34)
        or id == "pickaxe" and Vector3.new(0.32, 3.4, 0.32)
        or id == "prism_blade" and Vector3.new(0.34, 1.45, 0.34)
        or sword and Vector3.new(0.34, 1.35, 0.34)
        or Vector3.new(0.42, 0.9, 0.62)
    local weaponColor = id == "crowbar" and Color3.fromRGB(132, 48, 42) or (color or dark)
    local h = part(tool, "Handle", handleSize, CFrame.new(), weaponColor)
    h.Anchored = false; h.CanCollide = false; h.Massless = true
    local function add(name, size, offset, tint, material)
        local p = part(tool, name, size, h.CFrame * offset, tint, material); p.Anchored = false; p.CanCollide = false; p.Massless = true
        local w = Instance.new("WeldConstraint"); w.Part0 = h; w.Part1 = p; w.Parent = p
        return p
    end
    if id == "crowbar" then
        add("HookNeck", Vector3.new(0.9, 0.28, 0.28), CFrame.new(0.36, 1.62, 0) * CFrame.Angles(0, 0, math.rad(-18)), weaponColor)
        add("HookTip", Vector3.new(0.28, 0.85, 0.28), CFrame.new(0.73, 1.38, 0) * CFrame.Angles(0, 0, math.rad(-48)), Color3.fromRGB(161, 64, 51))
        add("PryClaw", Vector3.new(0.72, 0.18, 0.5), CFrame.new(0.22, -1.78, 0) * CFrame.Angles(0, 0, math.rad(13)), Color3.fromRGB(181, 187, 183), Enum.Material.Metal)
        for y = -1.05, -0.35, 0.23 do add("GripWrap", Vector3.new(0.34, 0.09, 0.34), CFrame.new(0, y, 0), Color3.fromRGB(41, 45, 44), Enum.Material.Rubber) end
        tool.Grip = CFrame.new(0, -0.72, 0) * CFrame.Angles(math.rad(17), 0, math.rad(-6))
    elseif id == "bat" then
        h.Color = Color3.fromRGB(129, 91, 55); h.Material = Enum.Material.Wood
        add("BatTop", Vector3.new(0.78, 1.9, 0.78), CFrame.new(0, 1.55, 0), Color3.fromRGB(151, 108, 66), Enum.Material.Wood)
        for y = -1.2, -0.45, 0.24 do add("BatGrip", Vector3.new(0.61, 0.08, 0.61), CFrame.new(0, y, 0), Color3.fromRGB(35, 41, 41), Enum.Material.Rubber) end
        tool.Grip = CFrame.new(0, -0.7, 0) * CFrame.Angles(math.rad(8), 0, math.rad(-4))
    elseif id == "hatchet" then
        h.Color = Color3.fromRGB(111, 76, 48); h.Material = Enum.Material.Wood
        add("AxeHead", Vector3.new(1.65, 0.72, 0.3), CFrame.new(0.46, 1.15, 0), Color3.fromRGB(166, 174, 173), Enum.Material.Metal)
        add("AxeEdge", Vector3.new(0.18, 1.05, 0.5), CFrame.new(1.25, 1.15, 0), Color3.fromRGB(211, 217, 213), Enum.Material.Metal)
        add("HatchetGrip", Vector3.new(0.4, 0.75, 0.4), CFrame.new(0, -0.9, 0), Color3.fromRGB(43, 48, 46), Enum.Material.Rubber)
        tool.Grip = CFrame.new(0, -0.52, 0) * CFrame.Angles(math.rad(12), 0, math.rad(-5))
    elseif id == "machete" then
        add("Blade", Vector3.new(0.16, 2.8, 0.72), CFrame.new(0, 1.95, 0), Color3.fromRGB(177, 184, 181))
        add("Guard", Vector3.new(0.9, 0.16, 0.3), CFrame.new(0, 0.72, 0), color or dark)
        tool.Grip = CFrame.new(0, -0.35, 0) * CFrame.Angles(math.rad(8), 0, 0)
    elseif id == "pickaxe" then
        add("PickHead", Vector3.new(3.2, 0.34, 0.38), CFrame.new(0, 1.65, 0), Color3.fromRGB(132, 139, 140))
        add("PickTip", Vector3.new(0.42, 0.42, 0.42), CFrame.new(1.75, 1.65, 0) * CFrame.Angles(0, 0, math.rad(45)), Color3.fromRGB(177, 184, 181))
        tool.Grip = CFrame.new(0, -0.72, 0) * CFrame.Angles(math.rad(12), 0, 0)
    elseif id == "prism_blade" then
        h.Color = Color3.fromRGB(48, 45, 61); h.Material = Enum.Material.Metal
        add("PrismCore", Vector3.new(0.22, 3.15, 0.58), CFrame.new(0, 2.15, 0), Color3.fromRGB(226, 91, 255), Enum.Material.Neon)
        add("PrismEdge", Vector3.new(0.1, 2.8, 0.78), CFrame.new(0.16, 2.22, 0), Color3.fromRGB(83, 224, 255), Enum.Material.Neon)
        add("PrismTip", Vector3.new(0.5, 0.85, 0.5), CFrame.new(0, 3.78, 0) * CFrame.Angles(0, 0, math.rad(45)), Color3.fromRGB(255, 220, 89), Enum.Material.Neon)
        add("PrismGuard", Vector3.new(1.25, 0.18, 0.42), CFrame.new(0, 0.82, 0), Color3.fromRGB(104, 255, 158), Enum.Material.Neon)
        tool.Grip = CFrame.new(0, -0.35, 0) * CFrame.Angles(math.rad(8), 0, 0)
    elseif sword then
        h.Color = id == "katana" and Color3.fromRGB(35, 45, 51) or Color3.fromRGB(82, 58, 43); h.Material = Enum.Material.Leather
        local bladeLength = id == "short_sword" and 2.55 or id == "saber" and 3.1 or id == "longsword" and 3.75 or 3.45
        local bladeTint = id == "katana" and Color3.fromRGB(117, 222, 235) or id == "saber" and Color3.fromRGB(222, 228, 226) or Color3.fromRGB(187, 196, 197)
        add("SwordBlade", Vector3.new(id == "longsword" and 0.18 or 0.13, bladeLength, id == "saber" and 0.5 or 0.68), CFrame.new(0, 0.68 + bladeLength / 2, 0), bladeTint, Enum.Material.Metal)
        add("SwordTip", Vector3.new(0.48, 0.7, id == "saber" and 0.42 or 0.58), CFrame.new(0, 1.02 + bladeLength, 0) * CFrame.Angles(0, 0, math.rad(45)), bladeTint, Enum.Material.Metal)
        add("SwordGuard", Vector3.new(id == "saber" and 1.3 or id == "longsword" and 1.65 or 1.15, 0.18, 0.42), CFrame.new(0, 0.72, 0), id == "katana" and Color3.fromRGB(49, 62, 68) or Color3.fromRGB(172, 127, 52), Enum.Material.Metal)
        if id == "katana" then for y = -0.42, 0.28, 0.23 do add("KatanaWrap", Vector3.new(0.42, 0.08, 0.42), CFrame.new(0, y, 0), Color3.fromRGB(72, 156, 163), Enum.Material.Fabric) end end
        tool.Grip = CFrame.new(0, -0.38, 0) * CFrame.Angles(math.rad(8), 0, id == "saber" and math.rad(-4) or 0)
    else
        local long = table.find({"rifle", "assault_rifle", "hunting_rifle", "shotgun", "double_barrel", "smg", "carbine", "sniper_rifle", "lmg"}, id) ~= nil
        local length = (id == "rifle" or id == "assault_rifle" or id == "carbine") and 3.5 or (id == "hunting_rifle" or id == "sniper_rifle") and 4.25
            or id == "shotgun" and 3.9 or id == "double_barrel" and 4.1 or id == "lmg" and 3.85 or id == "smg" and 2.35
            or id == "machine_pistol" and 1.85 or id == "magnum" and 1.9 or 1.5
        local wide = id == "shotgun" or id == "double_barrel" or id == "lmg"
        if id == "double_barrel" then
            add("LeftBarrel", Vector3.new(0.32, 0.42, length), CFrame.new(-0.2, 0.52, -0.55 - length * 0.18), Color3.fromRGB(76, 82, 81))
            add("RightBarrel", Vector3.new(0.32, 0.42, length), CFrame.new(0.2, 0.52, -0.55 - length * 0.18), Color3.fromRGB(76, 82, 81))
        else
            add("Barrel", Vector3.new(wide and 0.58 or 0.45, wide and 0.58 or 0.45, length), CFrame.new(0, 0.5, -0.55 - length * 0.18), color or dark)
        end
        add("Sight", Vector3.new(0.2, 0.15, 0.2), CFrame.new(0, 0.82, -math.min(1.4, length * 0.42)), orange, Enum.Material.Neon)
        if long then
            local wooden = id == "shotgun" or id == "double_barrel" or id == "hunting_rifle"
            add("Stock", Vector3.new(0.55, 0.76, 1.55), CFrame.new(0, 0.24, 0.95), wooden and Color3.fromRGB(112, 78, 48) or (color or dark), wooden and Enum.Material.Wood or Enum.Material.Metal)
            if id ~= "double_barrel" and id ~= "hunting_rifle" and id ~= "sniper_rifle" then
                local magazineHeight = id == "lmg" and 1.45 or (id == "smg" or id == "assault_rifle") and 1.2 or 0.92
                add("Magazine", Vector3.new(id == "lmg" and 0.9 or 0.5, magazineHeight, id == "lmg" and 1.05 or 0.65), CFrame.new(0, -0.55, -0.4), color or dark)
            end
        end
        if id == "shotgun" then add("Pump", Vector3.new(0.72, 0.72, 1.15), CFrame.new(0, 0.47, -1.3), Color3.fromRGB(112, 78, 48), Enum.Material.Wood) end
        if id == "hunting_rifle" or id == "sniper_rifle" then
            add("Bolt", Vector3.new(0.75, 0.14, 0.14), CFrame.new(0.47, 0.54, -0.38), Color3.fromRGB(175, 182, 180), Enum.Material.Metal)
            add("Scope", Vector3.new(id == "sniper_rifle" and 0.5 or 0.42, id == "sniper_rifle" and 0.5 or 0.42, id == "sniper_rifle" and 1.7 or 1.35), CFrame.new(0, 1.02, -0.52), Color3.fromRGB(41, 49, 51), Enum.Material.Metal)
        end
        if id == "revolver" or id == "magnum" then
            local cylinder = add("Cylinder", Vector3.new(0.72, 0.72, 0.72), CFrame.new(0, 0.46, -0.38), Color3.fromRGB(100, 107, 106), Enum.Material.Metal)
            cylinder.Shape = Enum.PartType.Cylinder
        elseif id == "glock" or id == "machine_pistol" then
            add("Slide", Vector3.new(0.5, 0.36, id == "machine_pistol" and 1.72 or 1.48), CFrame.new(0, 0.68, -0.52), Color3.fromRGB(67, 72, 71))
            if id == "machine_pistol" then add("ExtendedMagazine", Vector3.new(0.48, 1.35, 0.55), CFrame.new(0, -0.72, 0.02), color or dark, Enum.Material.Metal) end
        end
        if id == "assault_rifle" or id == "carbine" or id == "lmg" then
            add("Handguard", Vector3.new(id == "lmg" and 0.82 or 0.72, 0.72, 1.3), CFrame.new(0, 0.49, -1.28), Color3.fromRGB(62, 72, 72), Enum.Material.DiamondPlate)
        end
        if id == "lmg" then
            add("LeftBipod", Vector3.new(0.12, 1.25, 0.12), CFrame.new(-0.45, -0.28, -1.55) * CFrame.Angles(0, 0, math.rad(-18)), Color3.fromRGB(66, 72, 71), Enum.Material.Metal)
            add("RightBipod", Vector3.new(0.12, 1.25, 0.12), CFrame.new(0.45, -0.28, -1.55) * CFrame.Angles(0, 0, math.rad(18)), Color3.fromRGB(66, 72, 71), Enum.Material.Metal)
        end
        tool.Grip = CFrame.new(0, -0.1, 0)
    end
    tool:SetAttribute("Melee", melee)
    return tool
end
return Factory
