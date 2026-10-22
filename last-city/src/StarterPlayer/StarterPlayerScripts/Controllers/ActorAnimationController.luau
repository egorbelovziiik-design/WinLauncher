--!nonstrict
-- Asset-free, client-side R15 pose evaluation for server-owned NPCs and zombies.
-- The server owns movement/state; clients only smooth Motor6D.Transform visuals.
return function(_app)
    local CollectionService = game:GetService("CollectionService")
    local RunService = game:GetService("RunService")
    local actors = {}
    local motorNames = {
        "Root", "Waist", "Neck", "LeftShoulder", "RightShoulder", "LeftElbow", "RightElbow",
        "LeftWrist", "RightWrist", "LeftHip", "RightHip", "LeftKnee", "RightKnee", "LeftAnkle", "RightAnkle",
    }
    local function track(model)
        if actors[model] or not model:IsA("Model") or model:GetAttribute("BodyRig") ~= "LC-R15" then return end
        local motors = {}
        for _, name in motorNames do
            local joint = model:FindFirstChild(name, true)
            if joint and joint:IsA("Motor6D") then motors[name] = joint end
        end
        actors[model] = {motors = motors, seed = model:GetAttribute("AnimationSeed") or 0}
    end
    local function untrack(model) actors[model] = nil end
    for _, model in CollectionService:GetTagged("LastCityActor") do track(model) end
    local added = CollectionService:GetInstanceAddedSignal("LastCityActor"):Connect(track)
    local removed = CollectionService:GetInstanceRemovedSignal("LastCityActor"):Connect(untrack)
    local function pose(actor, state, style, now)
        local p = {}
        local speed = style == "zombie_runner" and 7.5 or style == "zombie_tank" and 2.7 or 4.7
        local t = now * speed + actor.seed * 0.31
        local stride = math.sin(t)
        local opposite = math.sin(t + math.pi)
        local bob = math.abs(math.sin(t * 2))
        if state == "Walking" then
            p.LeftHip = CFrame.Angles(stride * 0.62, 0, 0); p.RightHip = CFrame.Angles(opposite * 0.62, 0, 0)
            p.LeftKnee = CFrame.Angles(math.max(0, -stride) * 0.62, 0, 0); p.RightKnee = CFrame.Angles(math.max(0, -opposite) * 0.62, 0, 0)
            p.LeftShoulder = CFrame.Angles(opposite * 0.48, 0, -0.04); p.RightShoulder = CFrame.Angles(stride * 0.48, 0, 0.04)
            p.LeftElbow = CFrame.Angles(-0.12 - math.max(0, stride) * 0.25, 0, 0); p.RightElbow = CFrame.Angles(-0.12 - math.max(0, opposite) * 0.25, 0, 0)
            p.Waist = CFrame.new(0, bob * 0.045, 0) * CFrame.Angles(0.025, stride * 0.035, 0)
        elseif state == "Fleeing" then
            p.LeftHip = CFrame.Angles(stride * 0.95, 0, 0); p.RightHip = CFrame.Angles(opposite * 0.95, 0, 0)
            p.LeftKnee = CFrame.Angles(math.max(0, -stride) * 0.9, 0, 0); p.RightKnee = CFrame.Angles(math.max(0, -opposite) * 0.9, 0, 0)
            p.LeftShoulder = CFrame.Angles(-0.48 + opposite * 0.65, 0, -0.12); p.RightShoulder = CFrame.Angles(-0.48 + stride * 0.65, 0, 0.12)
            p.LeftElbow = CFrame.Angles(-0.72, 0, 0); p.RightElbow = CFrame.Angles(-0.72, 0, 0)
            p.Waist = CFrame.new(0, bob * 0.07, -0.03) * CFrame.Angles(-0.26, stride * 0.05, 0)
            p.Neck = CFrame.Angles(0.18, 0, 0)
        elseif state == "Talking" then
            local gesture = math.sin(t * 0.55)
            p.Neck = CFrame.Angles(0.03 + math.sin(t * 0.28) * 0.04, gesture * 0.09, 0)
            if style == "alex" then
                p.RightShoulder = CFrame.Angles(-0.72 + gesture * 0.16, -0.18, 0.12); p.RightElbow = CFrame.Angles(-0.72, 0, 0)
                p.LeftShoulder = CFrame.Angles(-0.08, 0, -0.08); p.Waist = CFrame.Angles(0, -gesture * 0.04, 0)
            elseif style == "max" then
                p.LeftShoulder = CFrame.Angles(-0.45 - gesture * 0.16, 0, -0.42); p.RightShoulder = CFrame.Angles(-0.45 + gesture * 0.16, 0, 0.42)
                p.LeftElbow = CFrame.Angles(-0.45, 0, 0); p.RightElbow = CFrame.Angles(-0.45, 0, 0)
            else
                p.LeftShoulder = CFrame.Angles(-0.95, 0.1, -0.08); p.LeftElbow = CFrame.Angles(-1.05, 0, 0)
                p.RightShoulder = CFrame.Angles(-0.38 + gesture * 0.25, 0, 0.15); p.RightElbow = CFrame.Angles(-0.72, 0, 0)
            end
        elseif state == "Working" then
            local strike = (math.sin(t * 1.35) + 1) * 0.5
            p.Waist = CFrame.Angles(-0.22, 0.12, 0); p.Neck = CFrame.Angles(0.2, -0.12, 0)
            p.RightShoulder = CFrame.Angles(-0.62 - strike * 1.05, 0.05, 0.16); p.RightElbow = CFrame.Angles(-0.42 - strike * 0.65, 0, 0)
            p.LeftShoulder = CFrame.Angles(-0.72, 0, -0.16); p.LeftElbow = CFrame.Angles(-0.82, 0, 0)
            p.LeftHip = CFrame.Angles(0.16, 0, 0); p.RightKnee = CFrame.Angles(0.2, 0, 0)
        elseif state == "Eating" then
            local bite = math.sin(t * 0.65) * 0.08
            p.LeftShoulder = CFrame.Angles(-1.28 + bite, 0.12, -0.18); p.RightShoulder = CFrame.Angles(-1.28 - bite, -0.12, 0.18)
            p.LeftElbow = CFrame.Angles(-1.18, 0, 0); p.RightElbow = CFrame.Angles(-1.18, 0, 0)
            p.Neck = CFrame.Angles(0.13 + bite, 0, 0)
        elseif state == "Sleeping" then
            local breath = math.sin(t * 0.25) * 0.025
            p.Waist = CFrame.new(0, breath, 0) * CFrame.Angles(-0.12, 0, 0.18)
            p.Neck = CFrame.Angles(0.2, 0, -0.22); p.LeftShoulder = CFrame.Angles(-0.22, 0, -0.12); p.RightShoulder = CFrame.Angles(-0.2, 0, 0.16)
            p.LeftHip = CFrame.Angles(0.18, 0, 0); p.RightHip = CFrame.Angles(-0.1, 0, 0); p.LeftKnee = CFrame.Angles(0.38, 0, 0)
        elseif state == "ZombieWalk" or state == "ZombieChase" then
            local run = state == "ZombieChase" and 1.15 or 0.76
            p.LeftHip = CFrame.Angles(stride * run, 0, 0.05); p.RightHip = CFrame.Angles(opposite * run, 0, -0.05)
            p.LeftKnee = CFrame.Angles(math.max(0, -stride) * run * 0.8, 0, 0); p.RightKnee = CFrame.Angles(math.max(0, -opposite) * run * 0.8, 0, 0)
            local stagger = style == "zombie_runner" and -0.82 or -1.02
            p.LeftShoulder = CFrame.Angles(stagger + stride * 0.18, 0.14, -0.12); p.RightShoulder = CFrame.Angles(stagger + opposite * 0.18, -0.14, 0.12)
            p.LeftElbow = CFrame.Angles(-0.48, 0, 0); p.RightElbow = CFrame.Angles(-0.68, 0, 0)
            p.Waist = CFrame.new(0, bob * 0.05, 0) * CFrame.Angles(-0.22, stride * 0.11, math.sin(t * 0.5) * 0.05)
            p.Neck = CFrame.Angles(0.12, math.sin(t * 0.38) * 0.12, 0.08)
        elseif state == "ZombieAttack" then
            local hit = math.max(0, math.sin(t * 1.45))
            p.Waist = CFrame.Angles(-0.35 - hit * 0.18, 0.22, 0)
            p.LeftShoulder = CFrame.Angles(-1.15, 0.08, -0.12); p.RightShoulder = CFrame.Angles(-0.55 - hit * 1.45, -0.2, 0.2)
            p.LeftElbow = CFrame.Angles(-0.55, 0, 0); p.RightElbow = CFrame.Angles(-0.28, 0, 0)
            p.Neck = CFrame.Angles(0.18, -0.18, 0)
        elseif state == "Dead" then
            p.Waist = CFrame.Angles(0, 0, 1.1); p.Neck = CFrame.Angles(0.35, 0, 0.35)
        elseif style:match("^zombie") then
            local sway = math.sin(t * 0.35)
            p.Waist = CFrame.Angles(-0.14, sway * 0.08, sway * 0.04); p.Neck = CFrame.Angles(0.12, -sway * 0.12, 0.08)
            p.LeftShoulder = CFrame.Angles(-0.65, 0, -0.1); p.RightShoulder = CFrame.Angles(-0.82, 0, 0.12)
            p.LeftElbow = CFrame.Angles(-0.38, 0, 0); p.RightElbow = CFrame.Angles(-0.52, 0, 0)
        else
            local breath = math.sin(t * 0.32)
            p.Waist = CFrame.new(0, breath * 0.018, 0) * CFrame.Angles(0.012 * breath, 0, 0)
            p.Neck = CFrame.Angles(-0.01 * breath, math.sin(t * 0.13) * 0.035, 0)
            p.LeftShoulder = CFrame.Angles(0.03 * breath, 0, -0.045); p.RightShoulder = CFrame.Angles(-0.03 * breath, 0, 0.045)
        end
        return p
    end
    local elapsed = 0
    local render = RunService.RenderStepped:Connect(function(dt)
        elapsed += dt
        if elapsed < 1 / 30 then return end
        dt = elapsed; elapsed = 0
        local now = os.clock(); local alpha = 1 - math.exp(-math.min(dt, 0.1) * 14)
        for model, actor in actors do
            if not model.Parent then actors[model] = nil; continue end
            local state = model:GetAttribute("State") or "Idle"
            local style = model:GetAttribute("AnimationStyle") or "survivor"
            local targets = pose(actor, state, style, now)
            for name, joint in actor.motors do
                local target = targets[name] or CFrame.new()
                joint.Transform = (joint.Transform or CFrame.new()):Lerp(target, alpha)
            end
        end
    end)
    return {
        Destroy = function()
            render:Disconnect(); added:Disconnect(); removed:Disconnect()
            for model in actors do actors[model] = nil end
        end,
    }
end
