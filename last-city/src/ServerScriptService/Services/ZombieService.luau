--!nonstrict
return function(ctx)
    local Pathfinding = game:GetService("PathfindingService")
    local CollectionService = game:GetService("CollectionService")
    local Debris = game:GetService("Debris")
    local Service = {Zombies = {}, NextId = 0, PendingPaths = 0, Bosses = {}}
    for kind, def in ctx.ZombieDefs do if def.boss then Service.Bosses[kind] = {alive = false, respawnAt = 0, lastPoint = 0} end end
    function Service:IsMine(position)
        return position.X >= 326 and position.X <= 462 and position.Z >= 132 and position.Z <= 188
    end
    function Service:IsCity2(position)
        return position.X >= 720 and position.X <= 1180 and position.Z >= -265 and position.Z <= 420
    end
    function Service:IsCity1(position)
        return position.X >= -540 and position.X <= 540 and position.Z >= -625 and position.Z <= 490 and not self:IsMine(position)
    end
    function Service:BossState(kind)
        local state = self.Bosses[kind]
        return state and {alive = state.alive, respawnAt = state.respawnAt, lastPoint = state.lastPoint} or nil
    end
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
        if not z or z.dead or z.humanoid.Health <= 0 then return 0, false end
        if player then z.credits[player] = os.clock() end
        local def = ctx.ZombieDefs[z.kind]
        local applied = math.max(1, amount * (1 - (def.armor or 0)))
        local lethal = applied >= z.humanoid.Health
        z.humanoid:TakeDamage(applied); z.model:SetAttribute("Health", z.humanoid.Health)
        if z.bossFill then z.bossFill.Size = UDim2.fromScale(math.clamp(z.humanoid.Health / z.humanoid.MaxHealth, 0, 1), 1) end
        if not lethal and os.clock() >= (z.nextPainVoice or 0) then
            z.nextPainVoice = os.clock() + 1.4
            ctx:FX("zombie_hurt", {from = z.model.PrimaryPart.Position, tone = def.voice or 1})
        end
        return applied, lethal
    end
    function Service:AwardRainbowLoot(player, s, position)
        local c = s.Campaign
        c.BossPity = c.BossPity or {rainbowArmor = 0, rainbowWeapon = 0}
        local armorDrop = math.random(1, 100) <= 22 or c.BossPity.rainbowArmor >= 3
        local weaponDrop = math.random(1, 100) <= 30 or c.BossPity.rainbowWeapon >= 2
        c.BossPity.rainbowArmor = armorDrop and 0 or math.min(3, c.BossPity.rainbowArmor + 1)
        c.BossPity.rainbowWeapon = weaponDrop and 0 or math.min(2, c.BossPity.rainbowWeapon + 1)
        local bundles = {
            {metal = 14, electronics = 5, prism_shard = 5, ammo = 30},
            {steel_plate = 7, chemicals = 8, prism_shard = 6, rifle_ammo = 24},
            {concrete = 12, glass = 9, prism_shard = 7, shells = 18},
        }
        local resources = bundles[math.random(1, #bundles)]
        local ok = ctx.Inventory.grant(c, resources)
        if not ok then
            for id, count in resources do c.Stockpile[id] = math.min(9999, (c.Stockpile[id] or 0) + count) end
            ctx:Notify(player, "Рюкзак заполнен: обычная добыча Радужного Стража отправлена на склад базы.", "info")
        end
        local drops = {}
        local function rare(id, label, offset)
            local granted = ctx.Inventory.grant(c, {[id] = 1})
            if not granted then ctx.Services.ResourceService:Drop(player, id, 1, position + offset) end
            table.insert(drops, label)
        end
        if armorDrop then rare("prism_armor", "Радужная броня", Vector3.new(-2, 0, 0)) end
        if weaponDrop then rare("prism_blade", "Призматический клинок", Vector3.new(2, 0, 0)) end
        s.Data.Coins += 35
        local rareText = #drops > 0 and table.concat(drops, " + ") or "редкий предмет не выпал"
        ctx:Notify(player, "РАДУЖНАЯ ДОБЫЧА: +35 Coins, ресурсы и " .. rareText .. ". Шансы: броня 22%, клинок 30%.", #drops > 0 and "success" or "info")
    end
    function Service:Die(z)
        if z.dead then return end
        z.dead = true; self.Zombies[z.id] = nil
        local def = ctx.ZombieDefs[z.kind]
        if def.boss then
            local state = self.Bosses[z.kind]
            if state then state.alive = false; state.respawnAt = os.clock() + 180 end
        end
        ctx:FX("zombie_death", {from = z.model.PrimaryPart.Position, tone = def.voice or 1, boss = def.boss == true})
        for player, at in z.credits do
            local s = ctx.Sessions[player]; local root = ctx:Root(player)
            if s and s.Active and not s.Closing and not s.Frozen and root and os.clock() - at < 25 and (root.Position - z.model.PrimaryPart.Position).Magnitude < 240 then
                s.Campaign.Cash += def.cash; s.Campaign.XP += def.xp
                s.Campaign.Stats.kills += 1; s.Data.Stats.kills += 1
                ctx.Progression.fact(s.Campaign, "kill")
                ctx.Services.QuestService:Record(player, "kill", z.kind, 1)
                if z.kind == "rainbow" then self:AwardRainbowLoot(player, s, z.model.PrimaryPart.Position)
                elseif def.profileCoins then s.Data.Coins += def.profileCoins end
                ctx:Notify(player, "+" .. def.cash .. " Cash  /  +" .. def.xp .. " XP", "success")
                ctx:Changed(player)
            end
        end
        z.model:SetAttribute("Health", 0); z.model:SetAttribute("State", "Dead")
        for _, p in z.model:GetDescendants() do if p:IsA("BasePart") then p.CanCollide = false; p.Anchored = true; p.Transparency = math.max(p.Transparency, 0.6) end end
        Debris:AddItem(z.model, 2)
    end
    local function addArmor(model, def, kind)
        if not (kind == "armored" or kind == "tank" or kind == "foreman" or kind == "mine_guard" or kind == "warden" or kind == "reaver") then return end
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
    local function bossLabel(model, def)
        local gui = Instance.new("BillboardGui"); gui.Name = "BossHealth"; gui.Size = UDim2.fromOffset(300, 54)
        gui.StudsOffset = Vector3.new(0, 3.2, 0); gui.AlwaysOnTop = true; gui.MaxDistance = 140
        gui.Adornee = model:FindFirstChild("Head"); gui.Parent = model
        local title = Instance.new("TextLabel"); title.Size = UDim2.new(1, 0, 0, 30); title.BackgroundTransparency = 1
        title.Text = def.name; title.Font = Enum.Font.GothamBold; title.TextSize = 16
        title.TextColor3 = def.name:find("РАДУЖНЫЙ") and Color3.fromRGB(232, 121, 249) or Color3.fromRGB(249, 184, 76); title.TextStrokeTransparency = 0.2; title.Parent = gui
        local back = Instance.new("Frame"); back.Position = UDim2.fromOffset(10, 34); back.Size = UDim2.new(1, -20, 0, 10)
        back.BackgroundColor3 = Color3.fromRGB(35, 39, 39); back.BorderSizePixel = 0; back.Parent = gui
        local fill = Instance.new("Frame"); fill.Name = "Fill"; fill.Size = UDim2.fromScale(1, 1)
        fill.BackgroundColor3 = Color3.fromRGB(196, 65, 45); fill.BorderSizePixel = 0; fill.Parent = back
        return fill
    end
    function Service:Spawn(kind, position, raidOwner)
        if self:Count() >= ctx.Config.MaxZombies then return end
        local def = ctx.ZombieDefs[kind]; if not def then return end
        -- Zone-specific enemies cannot be injected into another district. Every boss
        -- has its own lifecycle, so a city encounter never suppresses the Foreman.
        if def.mineOnly and not self:IsMine(position) then return end
        if def.city2Only and not self:IsCity2(position) then return end
        if def.city1Only and not self:IsCity1(position) then return end
        local bossState = def.boss and self.Bosses[kind]
        if bossState and bossState.alive then return end
        self.NextId += 1
        local m, h = ctx.Factory.rig(def.name, CFrame.new(position + Vector3.new(0, 3 * def.size, 0)), Color3.fromRGB(59, 69, 61), Color3.fromRGB(table.unpack(def.color)), def.size, "zombie_" .. kind)
        addArmor(m, def, kind)
        m.Name = "Zombie_" .. self.NextId; m:SetAttribute("ZombieId", self.NextId); m:SetAttribute("Kind", kind)
        m:SetAttribute("Health", def.health); m:SetAttribute("MaxHealth", def.health); m:SetAttribute("State", "ZombieIdle"); m:SetAttribute("AnimationSeed", self.NextId % 97)
        m.Parent = ctx.World.Zombies; m.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(m, "Zombie"); CollectionService:AddTag(m, "LastCityActor")
        h.MaxHealth = def.health; h.Health = def.health; h.WalkSpeed = def.speed
        local fill = def.boss and bossLabel(m, def) or nil
        local z = {id = self.NextId, model = m, humanoid = h, kind = kind, home = position, raidOwner = raidOwner, credits = {}, nextAttack = 0, attackUntil = 0, nextPath = 0, pathIndex = 1, born = os.clock(), bossFill = fill, nextVoice = os.clock() + 2 + math.random() * 4}
        self.Zombies[z.id] = z; if bossState then bossState.alive = true end
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
                z.humanoid:MoveTo(waypoint.Position)
            else z.path = nil end
        end
        if z.pending or os.clock() < z.nextPath or self.PendingPaths >= 2 then return end
        z.nextPath = os.clock() + ctx.Config.PathInterval + math.random() * 0.8; z.pending = true; self.PendingPaths += 1
        task.spawn(function()
            -- Barricades are real defenses; infected route to/attack them instead of vaulting over walls.
            local path = Pathfinding:CreatePath({AgentRadius = 2, AgentHeight = 6, AgentCanJump = false, WaypointSpacing = 5})
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
        if os.clock() < (z.stunnedUntil or 0) then
            z.humanoid:MoveTo(root.Position); z.model:SetAttribute("State", "ZombieStunned"); return
        end
        local targetPlayer, targetSquad, targetRecruit, distance = nil, nil, nil, def.aggro or 110
        for player, s in ctx.Sessions do
            local pr = ctx:Root(player)
            if s.Active and not s.Closing and not s.Frozen and not s.Traveling and pr and ctx:Alive(player) and not ctx:InShelter(player)
                and (not def.leash or (pr.Position - z.home).Magnitude <= def.leash) then
                local d = (pr.Position - root.Position).Magnitude
                if d < distance and (not s.Crouching or d < 65) then targetPlayer = player; targetSquad = nil; targetRecruit = nil; distance = d end
            end
        end
        if ctx.Services.SquadService then
            local member, memberDistance = ctx.Services.SquadService:NearestTarget(root.Position, distance)
            if member and memberDistance and (not def.leash or (member.model.PrimaryPart.Position - z.home).Magnitude <= def.leash) then
                targetPlayer = nil; targetSquad = member; targetRecruit = nil; distance = memberDistance
            end
        end
        if ctx.Services.RecruitService then
            local recruit, recruitDistance = ctx.Services.RecruitService:NearestTarget(root.Position, distance)
            if recruit and recruitDistance and (not def.leash or (recruit.model.PrimaryPart.Position - z.home).Magnitude <= def.leash) then
                targetPlayer = nil; targetSquad = nil; targetRecruit = recruit; distance = recruitDistance
            end
        end
        local motionState = "ZombieIdle"
        if targetPlayer or targetSquad or targetRecruit then
            if os.clock() >= (z.nextVoice or 0) then
                z.nextVoice = os.clock() + 7 + math.random() * 8
                ctx:FX("zombie_voice", {from = root.Position, tone = def.voice or 1, boss = def.boss == true})
            end
            local targetRoot = targetPlayer and ctx:Root(targetPlayer) or targetSquad and targetSquad.model.PrimaryPart or targetRecruit.model.PrimaryPart
            if not targetRoot then return end
            local targetPosition = targetRoot.Position; motionState = "ZombieChase"
            local attackRange = def.ranged or 5.2
            local allyModel = targetSquad and targetSquad.model or targetRecruit and targetRecruit.model
            local ignore = {z.model, targetPlayer and targetPlayer.Character or allyModel}
            local visible = ctx:LineOfSight(root.Position + Vector3.new(0, 1.5, 0), targetPosition + Vector3.new(0, 1.5, 0), ignore)
            local attackedBarrier = false
            -- If a player's defense makes the route impossible, infected damage that real
            -- defense instead of jumping it or endlessly pushing through its collision.
            if not visible then
                local owner = targetPlayer or targetSquad and targetSquad.owner or targetRecruit and targetRecruit.owner
                local barrier, barrierDistance
                if owner then local _point; barrier, _point, barrierDistance = ctx.Services.BuildingService:Nearest(owner, root.Position) end
                if barrier and barrierDistance and barrierDistance < 5.2 and os.clock() >= z.nextAttack then
                    z.nextAttack = os.clock() + def.interval; z.attackUntil = os.clock() + 0.55; attackedBarrier = true
                    ctx.Services.BuildingService:Damage(owner, barrier.uid, def.structureDamage or def.damage)
                end
            end
            if not attackedBarrier then
                if def.ranged and distance < attackRange * 0.72 then z.humanoid:MoveTo(root.Position)
                else self:Move(z, targetPosition) end
                if distance < attackRange and visible and os.clock() >= z.nextAttack then
                    z.nextAttack = os.clock() + def.interval; z.attackUntil = os.clock() + 0.55
                    if targetPlayer then ctx.Services.CombatService:Hurt(targetPlayer, def.damage, def.ranged and "acid" or "melee")
                    elseif targetSquad then ctx.Services.SquadService:Hurt(targetSquad, def.damage)
                    else ctx.Services.RecruitService:Hurt(targetRecruit, def.damage) end
                    ctx:FX("zombie_attack", {from = root.Position, tone = def.voice or 1, ranged = def.ranged ~= nil})
                    if def.ranged then ctx:FX("acid", {from = root.Position + Vector3.new(0, 2, 0), to = targetPosition + Vector3.new(0, 1.5, 0), user = 0}) end
                end
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
            if not observed and not def.boss and not def.minion and os.clock() - z.born > 240 then z.model:Destroy(); self.Zombies[z.id] = nil; return end
        end
        if root.Position.Y < -60 then
            if def.boss and self.Bosses[z.kind] then self.Bosses[z.kind].alive = false; self.Bosses[z.kind].respawnAt = os.clock() + 20 end
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
            local points, minionPoints = {}, {}
            local bossPoints = {foreman = {}, reaver = {}, warden = {}, rainbow = {}}
            for _, point in ctx.World.ZombieSpawns:GetChildren() do
                if point.Name == "MineBossSpawn" then table.insert(bossPoints.foreman, point)
                elseif point.Name:match("^City1BossSpawn") then table.insert(bossPoints.reaver, point)
                elseif point.Name:match("^City2BossSpawn") then table.insert(bossPoints.warden, point)
                elseif point.Name == "RainbowBossSpawn" then table.insert(bossPoints.rainbow, point)
                elseif point.Name:match("^MineMinionSpawn") then table.insert(minionPoints, point)
                else table.insert(points, point) end
            end
            local function zone(point)
                if point.Name:match("^Mine") then return "mine" end
                if point.Name:match("^Harbor") then return "city2" end
                return "city"
            end
            local function pick(area)
                local roll = math.random(1, 100)
                if area == "mine" then
                    return roll <= 30 and "lurker" or roll <= 52 and "ravager" or roll <= 70 and "runner"
                        or roll <= 88 and "brute" or roll <= 97 and "armored" or "tank"
                elseif area == "city2" then
                    return roll <= 27 and "corrosive" or roll <= 48 and "stalker" or roll <= 62 and "walker"
                        or roll <= 72 and "runner" or roll <= 86 and "brute" or roll <= 95 and "armored" or "tank"
                end
                return roll <= 45 and "walker" or roll <= 65 and "lurker" or roll <= 80 and "runner"
                    or roll <= 90 and "ravager" or roll <= 96 and "brute" or roll <= 99 and "armored" or "tank"
            end
            local function playersNear(position, range)
                for player, session in ctx.Sessions do
                    local root = ctx:Root(player)
                    if session.Active and not session.Closing and root and (root.Position - position).Magnitude <= range then return true end
                end
                return false
            end
            local function tryBoss(kind)
                local state, candidates = self.Bosses[kind], bossPoints[kind]
                if not state or state.alive or os.clock() < state.respawnAt or #candidates == 0 then return end
                local eligible = {}
                for index, point in candidates do
                    if playersNear(point.Position, 190) and not playersNear(point.Position, 20) then table.insert(eligible, {index = index, point = point}) end
                end
                if #eligible == 0 then return end
                local choices = {}
                for _, entry in eligible do if #eligible == 1 or entry.index ~= state.lastPoint then table.insert(choices, entry) end end
                local selected = choices[math.random(1, #choices)]
                local reserve = kind == "foreman" and 4 or 1
                if self:Count() > ctx.Config.MaxZombies - reserve then return end
                local boss = self:Spawn(kind, selected.point.Position)
                if not boss then return end
                state.lastPoint = selected.index
                if kind == "foreman" then
                    local existing = 0; for _, z in self.Zombies do if z.kind == "mine_guard" and not z.dead then existing += 1 end end
                    for index = 1, math.min(#minionPoints, math.max(0, 3 - existing)) do self:Spawn("mine_guard", minionPoints[index].Position) end
                end
            end
            while task.wait(5) do
                local active = 0; for _, session in ctx.Sessions do if session.Active and not session.Closing then active += 1 end end
                if active > 0 then
                    tryBoss("foreman"); tryBoss("reaver"); tryBoss("warden"); tryBoss("rainbow")
                end
                local desired = math.min(26, 7 + active * 4)
                if #points == 0 or self:Count() >= desired then continue end
                local eligible = {}
                for _, point in points do if playersNear(point.Position, 270) and not playersNear(point.Position, 30) then table.insert(eligible, point) end end
                if #eligible == 0 then continue end
                local point = eligible[math.random(1, #eligible)]
                self:Spawn(pick(zone(point)), point.Position)
            end
        end)
    end
    return Service
end
