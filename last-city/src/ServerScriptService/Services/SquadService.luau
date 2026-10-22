--!nonstrict
-- Server-authoritative persistent fireteam. Members never accept client damage/targets;
-- commands are validated here and all ammunition/resource outputs come from campaign state.
return function(ctx)
    local CollectionService = game:GetService("CollectionService")
    local Service = {Members = {}}
    local roster = {
        {name = "Сержант Рада", style = "soldier_rada", skin = Color3.fromRGB(187, 143, 109)},
        {name = "Стрелок Глеб", style = "soldier_gleb", skin = Color3.fromRGB(153, 113, 86)},
    }
    local expeditionLoot = {
        metal = {metal = 8, scrap = 10, steel_plate = 2},
        mine = {ore = 8, coal = 6, metal = 3},
        wood = {wood = 14, herbs = 5, cloth = 3},
    }
    local function unlocked(c)
        local q = c.Quests.rescue_fireteam
        return q and q.status == "RewardClaimed"
    end
    local function setHidden(actor, hidden)
        actor.hidden = hidden
        for _, node in actor.model:GetDescendants() do
            if node:IsA("BasePart") then
                node.Transparency = hidden and 1 or (node.Name == "HumanoidRootPart" and 1 or 0)
                node.CanCollide = not hidden and node.Name == "LowerTorso"
            elseif node:IsA("BillboardGui") or node:IsA("ProximityPrompt") then node.Enabled = not hidden end
        end
    end
    local function label(model, title)
        local gui = Instance.new("BillboardGui"); gui.Name = "SquadLabel"; gui.Size = UDim2.fromOffset(230, 55)
        gui.StudsOffset = Vector3.new(0, 2.5, 0); gui.MaxDistance = 70; gui.Adornee = model:FindFirstChild("Head"); gui.Parent = model
        local text = Instance.new("TextLabel"); text.Size = UDim2.fromScale(1, 1); text.BackgroundTransparency = 1
        text.Text = title .. "\nВАШ ОТРЯД • E"; text.Font = Enum.Font.GothamBold; text.TextSize = 13
        text.TextColor3 = Color3.fromRGB(188, 220, 170); text.TextStrokeTransparency = 0.25; text.Parent = gui
    end
    function Service:IsUnlocked(player)
        local s = ctx.Sessions[player]
        return s and unlocked(s.Campaign) == true
    end
    function Service:NearOwn(player)
        local root = ctx:Root(player); if not root then return false end
        for _, actor in self.Members[player] or {} do
            if actor.model.Parent and not actor.hidden and (actor.model.PrimaryPart.Position - root.Position).Magnitude <= ctx.Config.InteractRange then return true end
        end
        return false
    end
    function Service:Talk(player, actor)
        if not self:NearOwn(player) then return end
        local s = ctx.Sessions[player]; if not s then return end
        actor.talkUntil = os.clock() + 12; actor.model:SetAttribute("State", "Salute"); actor.humanoid:MoveTo(actor.model.PrimaryPart.Position)
        local squad = s.Campaign.Squad
        local names = {base = "охраняет базу", follow = "следует за вами", gather = "на экспедиции"}
        local text = "Приказ: отряд " .. (names[squad.command] or "ожидает") .. ". Автоматический огонь расходует карабинные патроны со склада."
        if squad.command == "gather" then text ..= " Возвращение через " .. math.max(0, squad.returnAt - os.time()) .. " секунд." end
        local spoken = "Current order: " .. (squad.command == "base" and "guard the base" or squad.command == "follow" and "follow the commander" or "resource expedition") .. ". Rifle fire consumes ammunition from base storage."
        if squad.command == "gather" then spoken ..= " We return in " .. math.max(0, squad.returnAt - os.time()) .. " seconds." end
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {
            id = "squad", name = actor.name, role = "Военная броня • карабин R4", text = text,
            model = actor.model, squad = true, voiceText = spoken, voiceId = actor.index == 1 and "4" or "3",
        })
    end
    function Service:Spawn(player)
        if self.Members[player] or not self:IsUnlocked(player) then return end
        local s = ctx.Sessions[player]
        local list = {}; self.Members[player] = list
        for index, def in roster do
            local at = self:GuardPoint(player, index)
            local model, h = ctx.Factory.rig(def.name, CFrame.new(at), Color3.fromRGB(58, 78, 60), def.skin, 1, def.style)
            model.Name = "Squad_" .. player.UserId .. "_" .. index; model:SetAttribute("Owner", player.UserId)
            model:SetAttribute("AnimationSeed", (player.UserId + index * 29) % 97); model:SetAttribute("State", "Idle")
            model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(model, "LastCityActor")
            h.MaxHealth = 220; h.Health = 220; h.WalkSpeed = 11; label(model, def.name)
            local actor = {name = def.name, model = model, humanoid = h, owner = player, index = index, health = 220,
                nextShot = 0, nextMove = 0, pathIndex = 1, forcePath = true, talkUntil = 0, downUntil = 0, hidden = false}
            local prompt = Instance.new("ProximityPrompt"); prompt.Name = "TalkPrompt"; prompt.ActionText = "Команды отряду"
            prompt.ObjectText = def.name; prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.GamepadKeyCode = Enum.KeyCode.ButtonX
            prompt.MaxActivationDistance = 11; prompt.HoldDuration = 0; prompt.RequiresLineOfSight = false; prompt.Parent = model:FindFirstChild("Head")
            prompt.Triggered:Connect(function(visitor)
                if visitor ~= player then ctx:Notify(visitor, "Этот боец подчиняется другому командиру."); return end
                if ctx.Services.NetworkService:Permit(visitor, "dialogue", true) then self:Talk(visitor, actor) end
            end)
            table.insert(list, actor)
        end
        if s.Campaign.Squad.command == "gather" then for _, actor in list do setHidden(actor, true) end end
    end
    function Service:SyncPlayer(player)
        if self:IsUnlocked(player) then self:Spawn(player)
        elseif self.Members[player] then self:RemovePlayer(player) end
    end
    function Service:RemovePlayer(player)
        for _, actor in self.Members[player] or {} do if actor.model.Parent then actor.model:Destroy() end end
        self.Members[player] = nil
    end
    function Service:Command(player, command, resource)
        local s = ctx.Sessions[player]
        if not s or not s.Active or not unlocked(s.Campaign) then ctx:Notify(player, "Сначала выполните задание «Бойцы Бориса» у старшины Бориса.", "error"); return false end
        if not ctx:InPlot(player, 12) and not self:NearOwn(player) then ctx:Notify(player, "Отдавайте приказ у терминала базы или рядом с бойцом.", "error"); return false end
        if not table.find({"base", "follow", "gather"}, command) then return false end
        local squad = s.Campaign.Squad
        if command == "gather" then
            if not table.find({"metal", "mine", "wood"}, resource) then return false end
            if squad.command == "gather" and squad.returnAt > os.time() then ctx:Notify(player, "Экспедиция уже выполняется."); return false end
            squad.command = "gather"; squad.resource = resource; squad.returnAt = os.time() + 90; squad.expedition += 1
            for _, actor in self.Members[player] or {} do setHidden(actor, true) end
            ctx:Notify(player, "Отряд отправлен на 90-секундную экспедицию. Результат будет сохранён на складе.", "success")
        else
            if squad.command == "gather" and squad.returnAt > os.time() then ctx:Notify(player, "Экспедиция отозвана без добычи.", "info") end
            squad.command = command; squad.returnAt = 0
            ctx:Notify(player, command == "follow" and "Отряд следует за вами." or "Отряд возвращается охранять базу.", "success")
            for _, actor in self.Members[player] or {} do
                if command == "base" then
                    local point = self:GuardPoint(player, actor.index)
                    if (actor.model.PrimaryPart.Position - point).Magnitude > 300 then actor.model:PivotTo(CFrame.new(point)); actor.path = nil; actor.pathTarget = nil end
                end
                setHidden(actor, false)
            end
        end
        ctx:Changed(player); return true
    end
    function Service:GuardPoint(player, index)
        local s = ctx.Sessions[player]; local plot = ctx.Config.Plots[s.Plot]
        for _, b in s.Campaign.Buildings do
            if ctx.BuildingDefs[b.id].guardPost and b.readyAt <= os.time() and b.hp > 0 then
                local at = ctx.Services.BuildingService:Position(player, b)
                return at + Vector3.new(index == 1 and -3 or 3, 3, 0)
            end
        end
        local candidates = index == 1
            and {{26, 18}, {36, 8}, {28, -28}, {-36, 5}, {-28, -30}}
            or {{32, 18}, {-36, 8}, {-28, -28}, {36, 5}, {28, -34}}
        for _, point in candidates do
            if not ctx.Services.NPCService.PointClear or ctx.Services.NPCService:PointClear(s, point[1], point[2], 3) then
                return Vector3.new(plot.x + point[1], 3, plot.z + point[2])
            end
        end
        return ctx.Services.NPCService:WanderTarget(s, {})
    end
    function Service:CompleteExpedition(player, s)
        local squad = s.Campaign.Squad
        if squad.command ~= "gather" or squad.returnAt <= 0 or squad.returnAt > os.time() or squad.completed >= squad.expedition then return end
        local multiplier = 1
        for _, b in s.Campaign.Buildings do
            if ctx.BuildingDefs[b.id].commandPost and b.readyAt <= os.time() and s.Power and s.Power.powered[b.uid] then multiplier = 1.5; break end
        end
        local summary = {}
        for id, count in expeditionLoot[squad.resource] do
            local amount = math.floor(count * multiplier)
            s.Campaign.Stockpile[id] = math.min(ctx.EconomyDefs.StockCap, (s.Campaign.Stockpile[id] or 0) + amount)
            table.insert(summary, ctx.Items[id].name .. " ×" .. amount)
        end
        squad.completed = squad.expedition; squad.command = "base"; squad.returnAt = 0
        for _, actor in self.Members[player] or {} do
            actor.model:PivotTo(CFrame.new(self:GuardPoint(player, actor.index))); actor.path = nil; actor.pathTarget = nil
            setHidden(actor, false)
        end
        ctx:Notify(player, "Экспедиция вернулась: " .. table.concat(summary, ", "), "success"); ctx:Changed(player)
    end
    function Service:NearestTarget(position, range)
        local best, distance
        for _, list in self.Members do
            for _, actor in list do
                if not actor.hidden and actor.downUntil <= os.clock() and actor.model.Parent then
                    local d = (actor.model.PrimaryPart.Position - position).Magnitude
                    if d < range and (not distance or d < distance) then best = actor; distance = d end
                end
            end
        end
        return best, distance
    end
    function Service:Hurt(actor, damage)
        if not actor or actor.hidden or actor.downUntil > os.clock() then return end
        actor.health = math.max(0, actor.health - damage * 0.58) -- military armor reduces incoming damage by 42%
        actor.model:SetAttribute("Health", actor.health)
        if actor.health <= 0 then
            actor.downUntil = os.clock() + 30; actor.model:SetAttribute("State", "Downed"); actor.humanoid:MoveTo(actor.model.PrimaryPart.Position)
        end
    end
    function Service:TravelWith(player, destination)
        local s = ctx.Sessions[player]; if not s or s.Campaign.Squad.command ~= "follow" then return end
        for index, actor in self.Members[player] or {} do
            setHidden(actor, false); actor.path = nil; actor.pathTarget = nil
            actor.model:PivotTo(CFrame.new(destination + Vector3.new((index == 1 and -4 or 4), 0, 5)))
        end
    end
    function Service:Evacuate(player, building)
        local s = ctx.Sessions[player]; if not s then return end
        local plot = ctx.Config.Plots[s.Plot]; local hx, hz = ctx.Building.extents(building.id, building.r)
        for _, actor in self.Members[player] or {} do
            local p = actor.model.PrimaryPart.Position
            if math.abs(p.X - plot.x - building.x) < hx + 2.5 and math.abs(p.Z - plot.z - building.z) < hz + 2.5 then
                local safe = self:GuardPoint(player, actor.index)
                actor.model:PivotTo(CFrame.new(safe)); actor.path = nil; actor.pathTarget = nil
                actor.pathToken = (actor.pathToken or 0) + 1
            end
        end
    end
    function Service:Step(player, s, actor)
        if not actor.model.Parent or actor.hidden then return end
        local now = os.clock()
        if actor.downUntil > now then actor.model:SetAttribute("State", "Downed"); return end
        if actor.health <= 0 then actor.health = 220; actor.model:SetAttribute("Health", 220) end
        if now <= actor.talkUntil then actor.model:SetAttribute("State", "Salute"); return end
        local squad = s.Campaign.Squad
        local enemy, distance = ctx.Services.ZombieService:Nearest(actor.model.PrimaryPart.Position, 72)
        if enemy and not enemy.dead and distance and distance <= 72 and squad.command ~= "gather" then
            local origin = actor.model:FindFirstChild("Head").Position
            local target = enemy.model.PrimaryPart.Position
            if now >= actor.nextShot and (s.Campaign.Stockpile.rifle_ammo or 0) > 0
                and ctx:LineOfSight(origin, target, {actor.model, enemy.model}) then
                actor.nextShot = now + 0.48; s.Campaign.Stockpile.rifle_ammo -= 1
                ctx.Services.ZombieService:Damage(enemy, 20, player)
                ctx:FX("shot", {from = origin, to = target, melee = false, hit = true, user = 0})
                actor.model:SetAttribute("State", "Aiming"); return
            end
        end
        local target
        if squad.command == "follow" then
            local root = ctx:Root(player)
            if root then target = root.Position + root.CFrame.RightVector * (actor.index == 1 and -5 or 5) + root.CFrame.LookVector * -5 end
        else target = self:GuardPoint(player, actor.index) end
        if target then
            ctx.Services.NPCService:Walk(actor, target)
            actor.model:SetAttribute("State", actor.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1 and "Walking" or "ArmedIdle")
        end
    end
    function Service:Init()
        task.spawn(function()
            while task.wait(0.25) do
                for player, list in self.Members do
                    local s = ctx.Sessions[player]
                    if not s or not s.Active or s.Closing or s.Frozen then continue end
                    for _, actor in list do self:Step(player, s, actor) end
                end
            end
        end)
        task.spawn(function()
            while task.wait(1) do
                for player, s in ctx.Sessions do
                    if s.Loaded and not s.Closing then
                        self:SyncPlayer(player); self:CompleteExpedition(player, s)
                        if s.Campaign.Squad.command == "gather" and s.Campaign.Squad.returnAt > os.time() then
                            for _, actor in self.Members[player] or {} do setHidden(actor, true) end
                        end
                    end
                end
            end
        end)
    end
    return Service
end
