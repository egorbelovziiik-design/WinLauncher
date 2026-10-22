--!nonstrict
return function(ctx)
    local Pathfinding = game:GetService("PathfindingService")
    local CollectionService = game:GetService("CollectionService")
    local Debris = game:GetService("Debris")
    local Service = {Zombies = {}, NextId = 0, PendingPaths = 0, BossRespawnAt = 0, BossAlive = false}
    function Service:Count()
        local count = 0; for _ in self.Zombies do count += 1 end; return count
    end
    function Service:Nearest(position, range)
        local closest, distance = nil, range
        for _, z in self.Zombies do
            if not z.dead and z.model.Parent then
                local d = (z.model.PrimaryPart.Position - position).Magnitude
                if d < distance then closest = z; distance = d end
            end
        end
        return closest, distance
    end
    function Service:FromPart(part)
        local model = part:FindFirstAncestorOfClass("Model")
        return model and self.Zombies[model:GetAttribute("ZombieId")]
    end
    function Service:Damage(z, amount, player)
        if not z or z.dead or z.humanoid.Health <= 0 then return end
        if player then z.credits[player] = os.clock() end
        local def = ctx.ZombieDefs[z.kind]
        local applied = math.max(1, amount * (1 - (def.armor or 0)))
        z.humanoid:TakeDamage(applied); z.model:SetAttribute("Health", z.humanoid.Health)
        if z.bossFill then z.bossFill.Size = UDim2.fromScale(math.clamp(z.humanoid.Health / z.humanoid.MaxHealth, 0, 1), 1) end
    end
    function Service:Die(z)
        if z.dead then return end
        z.dead = true; self.Zombies[z.id] = nil
        if z.kind == "foreman" then self.BossAlive = false; self.BossRespawnAt = os.clock() + 120 end
        for player, at in z.credits do
            local s = ctx.Sessions[player]; local root = ctx:Root(player)
            if s and s.Active and not s.Closing and not s.Frozen and root and os.clock() - at < 25 and (root.Position - z.model.PrimaryPart.Position).Magnitude < 240 then
                local def = ctx.ZombieDefs[z.kind]
                s.Campaign.Cash += def.cash; s.Campaign.XP += def.xp
                s.Campaign.Stats.kills += 1; s.Data.Stats.kills += 1
                ctx.Progression.fact(s.Campaign, "kill")
                ctx.Services.QuestService:Record(player, "kill", z.kind, 1)
                ctx:Notify(player, "+" .. def.cash .. " Cash  /  +" .. def.xp .. " XP", "success")
                ctx:Changed(player)
            end
        end
        z.model:SetAttribute("Health", 0); z.model:SetAttribute("State", "Dead")
        for _, p in z.model:GetDescendants() do if p:IsA("BasePart") then p.CanCollide = false; p.Anchored = true; p.Transparency = math.max(p.Transparency, 0.6) end end
        Debris:AddItem(z.model, 2)
    end
    local function addArmor(model, def, kind)
        if not (kind == "armored" or kind == "tank" or kind == "foreman") then return end
        local torso = model:FindFirstChild("UpperTorso"); local head = model:FindFirstChild("Head")
        local function plate(name, body, size, offset, color)
            if not body then return end
            local p = Instance.new("Part"); p.Name = name; p.Size = size * def.size
            p.CFrame = body.CFrame * CFrame.new(offset * def.size); p.Color = color; p.Material = Enum.Material.Metal
            p.CanCollide = false; p.CanTouch = false; p.CanQuery = false; p.Massless = true; p.Parent = model
            local weld = Instance.new("WeldConstraint"); weld.Part0 = body; weld.Part1 = p; weld.Parent = p
        end
        plate("ChestArmor", torso, Vector3.new(2.35, 1.55, 0.28), Vector3.new(0, 0.1, -0.58), Color3.fromRGB(65, 72, 76))
        plate("ShoulderArmor", torso, Vector3.new(3.15, 0.36, 0.9), Vector3.new(0, 0.7, 0), Color3.fromRGB(78, 84, 85))
        plate("Helmet", head, Vector3.new(1.72, 0.42, 1.55), Vector3.new(0, 0.66, 0), kind == "foreman" and Color3.fromRGB(235, 155, 42) or Color3.fromRGB(72, 79, 82))
    end
    local function bossLabel(model)
        local gui = Instance.new("BillboardGui"); gui.Name = "BossHealth"; gui.Size = UDim2.fromOffset(270, 54)
        gui.StudsOffset = Vector3.new(0, 3.2, 0); gui.AlwaysOnTop = true; gui.MaxDistance = 140
        gui.Adornee = model:FindFirstChild("Head"); gui.Parent = model
        local title = Instance.new("TextLabel"); title.Size = UDim2.new(1, 0, 0, 30); title.BackgroundTransparency = 1
        title.Text = "ШАХТЁР-БРИГАДИР"; title.Font = Enum.Font.GothamBold; title.TextSize = 16
        title.TextColor3 = Color3.fromRGB(249, 184, 76); title.TextStrokeTransparency = 0.2; title.Parent = gui
        local back = Instance.new("Frame"); back.Position = UDim2.fromOffset(10, 34); back.Size = UDim2.new(1, -20, 0, 10)
        back.BackgroundColor3 = Color3.fromRGB(35, 39, 39); back.BorderSizePixel = 0; back.Parent = gui
        local fill = Instance.new("Frame"); fill.Name = "Fill"; fill.Size = UDim2.fromScale(1, 1)
        fill.BackgroundColor3 = Color3.fromRGB(196, 65, 45); fill.BorderSizePixel = 0; fill.Parent = back
        return fill
    end
    function Service:Spawn(kind, position, raidOwner)
        if self:Count() >= ctx.Config.MaxZombies then return end
        local def = ctx.ZombieDefs[kind]; if not def then return end
        if def.boss and self.BossAlive then return end
        self.NextId += 1
        local m, h = ctx.Factory.rig(def.name, CFrame.new(position + Vector3.new(0, 3 * def.size, 0)), Color3.fromRGB(59, 69, 61), Color3.fromRGB(table.unpack(def.color)), def.size, "zombie_" .. kind)
        addArmor(m, def, kind)
        m.Name = "Zombie_" .. self.NextId; m:SetAttribute("ZombieId", self.NextId); m:SetAttribute("Kind", kind)
        m:SetAttribute("Health", def.health); m:SetAttribute("MaxHealth", def.health); m:SetAttribute("State", "ZombieIdle"); m:SetAttribute("AnimationSeed", self.NextId % 97)
        m.Parent = ctx.World.Zombies; m.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(m, "Zombie"); CollectionService:AddTag(m, "LastCityActor")
        h.MaxHealth = def.health; h.Health = def.health; h.WalkSpeed = def.speed
        local fill = def.boss and bossLabel(m) or nil
        local z = {id = self.NextId, model = m, humanoid = h, kind = kind, home = position, raidOwner = raidOwner, credits = {}, nextAttack = 0, attackUntil = 0, nextPath = 0, pathIndex = 1, born = os.clock(), bossFill = fill}
        self.Zombies[z.id] = z; if def.boss then self.BossAlive = true end
        h.Died:Connect(function() self:Die(z) end)
        return z
    end
    function Service:Move(z, destination)
        if not z.model.Parent then return end
        local root = z.model.PrimaryPart
        if ctx:LineOfSight(root.Position, destination + Vector3.new(0, 2, 0), {z.model}) then
            z.path = nil; z.humanoid:MoveTo(destination); return
        end
        if z.path then
            local waypoint = z.path[z.pathIndex]
            if waypoint and (waypoint.Position - root.Position).Magnitude < 4 then z.pathIndex += 1; waypoint = z.path[z.pathIndex] end
            if waypoint then
                if waypoint.Action == Enum.PathWaypointAction.Jump then z.humanoid.Jump = true end
                z.humanoid:MoveTo(waypoint.Position)
            else z.path = nil end
        end
        if z.pending or os.clock() < z.nextPath or self.PendingPaths >= 2 then return end
        z.nextPath = os.clock() + ctx.Config.PathInterval + math.random() * 0.8; z.pending = true; self.PendingPaths += 1
        task.spawn(function()
            local path = Pathfinding:CreatePath({AgentRadius = 2, AgentHeight = 6, AgentCanJump = true, WaypointSpacing = 5})
            local ok = pcall(function() path:ComputeAsync(root.Position, destination) end)
            self.PendingPaths -= 1; z.pending = false
            if z.dead or not z.model.Parent then return end
            if ok and path.Status == Enum.PathStatus.Success then z.path = path:GetWaypoints(); z.pathIndex = 2
            else z.humanoid:MoveTo(destination) end
        end)
    end
    function Service:Step(z)
        if z.dead or not z.model.Parent then return end
        local root = z.model.PrimaryPart; local def = ctx.ZombieDefs[z.kind]
        local nearest, distance = nil, def.aggro or 110
        for player, s in ctx.Sessions do
            local pr = ctx:Root(player)
            if s.Active and not s.Closing and not s.Frozen and pr and ctx:Alive(player) and not ctx:InShelter(player)
                and (not def.leash or (pr.Position - z.home).Magnitude <= def.leash) then
                local d = (pr.Position - root.Position).Magnitude
                if d < distance and (not s.Crouching or d < 65) then nearest = player; distance = d end
            end
        end
        local motionState = "ZombieIdle"
        if nearest then
            local pr = ctx:Root(nearest); motionState = "ZombieChase"
            self:Move(z, pr.Position)
            if distance < 5.2 and os.clock() >= z.nextAttack and ctx:LineOfSight(root.Position, pr.Position, {z.model, nearest.Character}) then
                z.nextAttack = os.clock() + def.interval; z.attackUntil = os.clock() + 0.55
                ctx.Services.CombatService:Hurt(nearest, def.damage)
            end
        elseif z.raidOwner and ctx.Sessions[z.raidOwner] and ctx.Sessions[z.raidOwner].Active then
            local b, point, d = ctx.Services.BuildingService:Nearest(z.raidOwner, root.Position)
            if b then
                motionState = "ZombieChase"; self:Move(z, point)
                if d < 5 and os.clock() >= z.nextAttack then
                    z.nextAttack = os.clock() + def.interval; z.attackUntil = os.clock() + 0.55
                    ctx.Services.BuildingService:Damage(z.raidOwner, b.uid, def.structureDamage or def.damage)
                end
            end
            if os.clock() - z.born > 180 then z.model:Destroy(); self.Zombies[z.id] = nil; return end
        else
            -- Far simulation: no pathfinding for unobserved idle zombies.
            local observed = false
            for player, s in ctx.Sessions do
                local pr = ctx:Root(player)
                if s.Active and pr and (pr.Position - root.Position).Magnitude < 200 then observed = true; break end
            end
            if observed then
                motionState = "ZombieWalk"
                self:Move(z, z.home + Vector3.new(math.sin(os.clock() / 10 + z.id) * 12, 0, math.cos(os.clock() / 10 + z.id) * 12))
            end
            if not observed and not def.boss and os.clock() - z.born > 240 then z.model:Destroy(); self.Zombies[z.id] = nil; return end
        end
        if root.Position.Y < -60 then
            if def.boss then self.BossAlive = false; self.BossRespawnAt = os.clock() + 20 end
            z.model:Destroy(); self.Zombies[z.id] = nil; return
        end
        if os.clock() < z.attackUntil then z.model:SetAttribute("State", "ZombieAttack")
        elseif root.AssemblyLinearVelocity.Magnitude > 1 then z.model:SetAttribute("State", motionState)
        else z.model:SetAttribute("State", "ZombieIdle") end
    end
    function Service:NightRaid()
        local candidates = {}
        for player, s in ctx.Sessions do
            if s.Active and not s.Closing and not s.Frozen and #s.Campaign.Buildings >= 2 then
                table.insert(candidates, {player = player, session = s, count = 0, goal = math.min(6, 2 + ctx.Util.level(s.Campaign.XP))})
            end
        end
        -- Round-robin the global budget: the first plot must not consume every slot.
        for wave = 1, 6 do
            for _, entry in candidates do
                if wave > entry.goal or self:Count() >= ctx.Config.MaxZombies then continue end
                local plot = ctx.Config.Plots[entry.session.Plot]
                local z = self:Spawn(wave == entry.goal and "brute" or (wave % 3 == 0 and "lurker" or "walker"), Vector3.new(plot.x + (wave % 2 == 0 and 66 or -66), 0, plot.z - 35 + wave * 8), entry.player)
                if z then entry.count += 1 end
            end
        end
        for _, entry in candidates do
            if entry.count == 0 then continue end
            local player, s = entry.player, entry.session
            s.Raid = "НОЧНАЯ ОРДА"
            ctx:Notify(player, "Орда у вашего участка! Ремонтируйте стены и защищайте производство.", "error")
            task.delay(185, function() if ctx.Sessions[player] == s then s.Raid = "Спокойно" end end)
        end
    end
    function Service:Init()
        task.spawn(function()
            while task.wait(ctx.Config.AIInterval) do
                for _, z in self.Zombies do self:Step(z) end
            end
        end)
        task.spawn(function()
            local points, bossPoint = {}, nil
            for _, point in ctx.World.ZombieSpawns:GetChildren() do
                if point.Name == "MineBossSpawn" then bossPoint = point else table.insert(points, point) end
            end
            local function pick(zone)
                local roll = math.random(1, 100)
                if zone == "mine" then
                    return roll <= 34 and "lurker" or roll <= 57 and "ravager" or roll <= 76 and "runner"
                        or roll <= 91 and "brute" or roll <= 98 and "armored" or "tank"
                end
                return roll <= 45 and "walker" or roll <= 65 and "lurker" or roll <= 80 and "runner"
                    or roll <= 90 and "ravager" or roll <= 96 and "brute" or roll <= 99 and "armored" or "tank"
            end
            while task.wait(5) do
                local active = 0; for _, s in ctx.Sessions do if s.Active then active += 1 end end
                if active > 0 and bossPoint and not self.BossAlive and os.clock() >= self.BossRespawnAt
                    and self:Count() < ctx.Config.MaxZombies then
                    local clear = true
                    for player in ctx.Sessions do
                        local root = ctx:Root(player)
                        if root and (root.Position - bossPoint.Position).Magnitude < 16 then clear = false end
                    end
                    if clear then self:Spawn("foreman", bossPoint.Position) end
                end
                local desired = math.min(20, 6 + active * 4)
                if #points == 0 or self:Count() >= desired then continue end
                local point = points[math.random(1, #points)]
                local clear = true
                for player in ctx.Sessions do
                    local root = ctx:Root(player)
                    if root and (root.Position - point.Position).Magnitude < 30 then clear = false end
                end
                if clear then self:Spawn(pick(point.Name:match("^Mine") and "mine" or point:GetAttribute("Zone")), point.Position) end
            end
        end)
    end
    return Service
end
