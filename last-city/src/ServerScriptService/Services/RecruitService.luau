--!nonstrict
-- Persistent, server-authoritative specialists hired from New Frontier. Their price,
-- ammunition, expedition timers, outputs, healing and construction bonus are all
-- validated here; clients only send an intent.
return function(ctx)
    local CollectionService = game:GetService("CollectionService")
    local Service = {Actors = {}}
    local offsets = {
        builder = Vector3.new(-30, 3, 26), medic = Vector3.new(-20, 3, 27), guard = Vector3.new(20, 3, 27),
        scout = Vector3.new(29, 3, 25), gatherer = Vector3.new(0, 3, 29),
    }
    local expeditionLoot = {
        metal = {metal = 12, scrap = 14, steel_plate = 3},
        mine = {ore = 12, coal = 9, metal = 5, prism_shard = 1},
        wood = {wood = 20, herbs = 7, cloth = 5},
    }
    local function home(s, id)
        local plot = ctx.Config.Plots[s.Plot]; local offset = offsets[id]
        if ctx.Services.NPCService and ctx.Services.NPCService.PointClear
            and not ctx.Services.NPCService:PointClear(s, offset.X, offset.Z, 3) then
            return ctx.Services.NPCService:WanderTarget(s, {})
        end
        return Vector3.new(plot.x, 0, plot.z) + offset
    end
    local function unlocked(c)
        local q = c.Quests.second_city
        return q and q.status == "RewardClaimed"
    end
    local function setHidden(actor, hidden)
        actor.hidden = hidden
        for _, node in actor.model:GetDescendants() do
            if node:IsA("BasePart") then
                node.Transparency = hidden and 1 or (node.Name == "HumanoidRootPart" and 1 or 0)
                node.CanCollide = not hidden and node.Name == "LowerTorso"
            elseif node:IsA("BillboardGui") or node:IsA("ProximityPrompt") then
                node.Enabled = not hidden
            end
        end
    end
    local function label(model, def)
        local gui = Instance.new("BillboardGui"); gui.Name = "RecruitLabel"; gui.Size = UDim2.fromOffset(240, 54)
        gui.StudsOffset = Vector3.new(0, 2.5, 0); gui.MaxDistance = 70; gui.Adornee = model:FindFirstChild("Head"); gui.Parent = model
        local text = Instance.new("TextLabel"); text.Size = UDim2.fromScale(1, 1); text.BackgroundTransparency = 1
        text.Text = def.name .. "\n" .. def.role:upper() .. " • E"; text.Font = Enum.Font.GothamBold; text.TextSize = 13
        text.TextColor3 = Color3.fromRGB(167, 224, 205); text.TextStrokeTransparency = 0.25; text.Parent = gui
    end
    function Service:SpawnOne(player, id)
        local s = ctx.Sessions[player]; local def = ctx.RecruitDefs[id]
        if not s or not def or not s.Campaign.Recruits[id] then return end
        local map = self.Actors[player] or {}; self.Actors[player] = map
        if map[id] and map[id].model.Parent then return map[id] end
        local at = home(s, id)
        local model, humanoid = ctx.Factory.rig(def.name, CFrame.new(at), Color3.fromRGB(table.unpack(def.color)), Color3.fromRGB(table.unpack(def.skin)), 1, def.style)
        model.Name = "Recruit_" .. player.UserId .. "_" .. id; model:SetAttribute("Owner", player.UserId)
        model:SetAttribute("RecruitId", id); model:SetAttribute("Health", def.health); model:SetAttribute("MaxHealth", def.health)
        model:SetAttribute("State", "Idle"); model:SetAttribute("AnimationSeed", (player.UserId + def.order * 37) % 97)
        model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(model, "LastCityActor")
        humanoid.MaxHealth = def.health; humanoid.Health = def.health; humanoid.WalkSpeed = def.speed
        local actor = {id = id, model = model, humanoid = humanoid, owner = player, home = at, nextMove = 0, pathIndex = 1,
            forcePath = true, hidden = false, nextShot = 0, nextHeal = 0, health = def.health, downUntil = 0}
        map[id] = actor; label(model, def)
        local prompt = Instance.new("ProximityPrompt"); prompt.Name = "RecruitPrompt"; prompt.ActionText = "Управление специалистами"
        prompt.ObjectText = def.name; prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.GamepadKeyCode = Enum.KeyCode.ButtonX
        prompt.MaxActivationDistance = 11; prompt.HoldDuration = 0; prompt.RequiresLineOfSight = false; prompt.Parent = model:FindFirstChild("Head")
        prompt.Triggered:Connect(function(visitor)
            if visitor ~= player then ctx:Notify(visitor, "Этот специалист нанят другим выжившим."); return end
            if ctx.Services.NetworkService:Permit(visitor, "recruitPanel", true) then ctx.Remotes.StateRemote:FireClient(visitor, "panel", "recruits") end
        end)
        local record = s.Campaign.Recruits[id]
        if record.command == "gather" and record.returnAt > os.time() then setHidden(actor, true) end
        return actor
    end
    function Service:SyncPlayer(player)
        local s = ctx.Sessions[player]; if not s then return end
        for id, record in s.Campaign.Recruits do if record.owned then self:SpawnOne(player, id) end end
    end
    function Service:RemovePlayer(player)
        for _, actor in self.Actors[player] or {} do if actor.model.Parent then actor.model:Destroy() end end
        self.Actors[player] = nil
    end
    function Service:NearOwn(player)
        local root = ctx:Root(player); if not root then return false end
        for _, actor in self.Actors[player] or {} do
            if actor.model.Parent and not actor.hidden and (actor.model.PrimaryPart.Position - root.Position).Magnitude <= ctx.Config.InteractRange then return true end
        end
        return false
    end
    function Service:Purchase(player, id)
        local s = ctx.Sessions[player]; local def = ctx.RecruitDefs[id]
        if not s or not def or not unlocked(s.Campaign) then ctx:Notify(player, "Сначала откройте НОВЫЙ РУБЕЖ.", "error"); return false end
        if not ctx.Services.NPCService:Near(player, "lada") then ctx:Notify(player, "Найм доступен только рядом с вербовщиком Ладой.", "error"); return false end
        if s.Campaign.Recruits[id] then ctx:Notify(player, "Этот специалист уже нанят."); return false end
        if s.Campaign.Cash < def.price then ctx:Notify(player, "Не хватает Survival Cash для найма.", "error"); return false end
        s.Campaign.Cash -= def.price
        s.Campaign.Recruits[id] = {owned = true, command = "base", resource = "metal", returnAt = 0, expedition = 0, completed = 0}
        self:SpawnOne(player, id)
        ctx:Notify(player, def.name .. " нанят навсегда для этой кампании.", "success"); ctx:Changed(player)
        return true
    end
    function Service:Command(player, id, command, resource)
        local s = ctx.Sessions[player]; local record = s and s.Campaign.Recruits[id]
        if not s or not record or not ctx.RecruitDefs[id] then return false end
        if not ctx:InPlot(player, 12) and not self:NearOwn(player) and not ctx.Services.NPCService:Near(player, "lada") then
            ctx:Notify(player, "Командуйте на базе, рядом со специалистом или у Лады.", "error"); return false
        end
        if command == "gather" then
            if id ~= "gatherer" or not table.find({"metal", "mine", "wood"}, resource) then return false end
            if record.command == "gather" and record.returnAt > os.time() then ctx:Notify(player, "Добытчик уже в экспедиции."); return false end
            record.command = "gather"; record.resource = resource; record.returnAt = os.time() + 120; record.expedition += 1
            local actor = self.Actors[player] and self.Actors[player][id]; if actor then setHidden(actor, true) end
            ctx:Notify(player, "Егор отправлен за ресурсами на 120 секунд. Экспедиция сохранена.", "success")
        elseif command == "base" or command == "follow" then
            record.command = command; record.returnAt = 0
            local actor = self.Actors[player] and self.Actors[player][id]
            if actor then
                if command == "base" and (actor.model.PrimaryPart.Position - actor.home).Magnitude > 300 then
                    actor.model:PivotTo(CFrame.new(actor.home)); actor.path = nil; actor.pathTarget = nil; actor.pathToken = (actor.pathToken or 0) + 1
                end
                setHidden(actor, false)
            end
            ctx:Notify(player, command == "follow" and ctx.RecruitDefs[id].name .. " следует за вами." or ctx.RecruitDefs[id].name .. " возвращается на базу.", "success")
        else return false end
        ctx:Changed(player); return true
    end
    function Service:CompleteExpedition(player, s, id, record)
        if record.command ~= "gather" or record.returnAt > os.time() or record.expedition <= record.completed then return end
        local bonus = s.Campaign.Recruits.scout and 1.25 or 1
        for item, amount in expeditionLoot[record.resource] do
            s.Campaign.Stockpile[item] = math.min(ctx.EconomyDefs.StockCap, (s.Campaign.Stockpile[item] or 0) + math.floor(amount * bonus))
        end
        record.completed = record.expedition; record.command = "base"; record.returnAt = 0
        local actor = self.Actors[player] and self.Actors[player][id]
        if actor then
            actor.model:PivotTo(CFrame.new(actor.home)); actor.path = nil; actor.pathTarget = nil; actor.pathToken = (actor.pathToken or 0) + 1
            setHidden(actor, false)
        end
        ctx:Notify(player, "Добытчик вернулся. Ресурсы доставлены на склад" .. (bonus > 1 and " с бонусом разведчика +25%." or "."), "success")
        ctx:Changed(player)
    end
    function Service:AdjustBuildTime(player, building)
        local s = ctx.Sessions[player]
        if not s or not s.Campaign.Recruits.builder or building.readyAt <= os.time() then return false end
        building.readyAt = os.time() + math.max(1, math.ceil((building.readyAt - os.time()) * 0.65))
        return true
    end
    function Service:TravelWith(player, position)
        local s = ctx.Sessions[player]
        for index, actor in self.Actors[player] or {} do
            local record = s and s.Campaign.Recruits[index]
            -- Base guards remain at the base; only an explicit follow order grants
            -- cross-city travel, preventing actors from walking across the map void.
            if record and record.command == "follow" and not actor.hidden and actor.model.Parent then
                actor.model:PivotTo(CFrame.new(position + Vector3.new((ctx.RecruitDefs[index].order - 3) * 3, 0, 7)))
                actor.path = nil; actor.pathTarget = nil; actor.pathToken = (actor.pathToken or 0) + 1
            end
        end
    end
    function Service:Evacuate(player, building)
        local s = ctx.Sessions[player]; if not s then return end
        local plot = ctx.Config.Plots[s.Plot]; local hx, hz = ctx.Building.extents(building.id, building.r)
        for _, actor in self.Actors[player] or {} do
            if not actor.model.Parent or not actor.model.PrimaryPart then continue end
            local position = actor.model.PrimaryPart.Position
            if math.abs(position.X - plot.x - building.x) < hx + 2.5 and math.abs(position.Z - plot.z - building.z) < hz + 2.5 then
                actor.home = ctx.Services.NPCService:WanderTarget(s, actor)
                actor.model:PivotTo(CFrame.new(actor.home)); actor.path = nil; actor.pathTarget = nil; actor.pathToken = (actor.pathToken or 0) + 1
            end
        end
    end
    function Service:NearestTarget(position, range)
        local best, distance
        for _, actors in self.Actors do
            for _, actor in actors do
                if not actor.hidden and actor.model.Parent and actor.downUntil <= os.clock() then
                    local d = (actor.model.PrimaryPart.Position - position).Magnitude
                    if d < range and (not distance or d < distance) then best, distance = actor, d end
                end
            end
        end
        return best, distance
    end
    function Service:Hurt(actor, damage)
        if actor.hidden or actor.downUntil > os.clock() then return end
        actor.health = math.max(0, actor.health - damage * (actor.id == "guard" and 0.7 or 1))
        actor.model:SetAttribute("Health", actor.health)
        if actor.health <= 0 then
            actor.downUntil = os.clock() + 30; actor.model:SetAttribute("State", "Downed"); actor.humanoid:MoveTo(actor.model.PrimaryPart.Position)
        end
    end
    function Service:Step(player, s)
        if not s.Loaded or s.Closing then return end
        for id, record in s.Campaign.Recruits do
            if record.command == "gather" then self:CompleteExpedition(player, s, id, record) end
        end
        if not s.Active or s.Frozen or s.Traveling then s.ScoutPing = nil; return end
        local playerRoot = ctx:Root(player)
        s.ScoutPing = nil
        for id, actor in self.Actors[player] or {} do
            local record = s.Campaign.Recruits[id]
            if not record or actor.hidden or not actor.model.Parent then continue end
            if actor.downUntil > os.clock() then continue end
            if actor.downUntil > 0 then
                actor.downUntil = 0; actor.health = ctx.RecruitDefs[id].health; actor.model:SetAttribute("Health", actor.health)
            end
            local base = actor.home
            local target = record.command == "follow" and playerRoot and playerRoot.Position + Vector3.new((ctx.RecruitDefs[id].order - 3) * 2.2, 0, 6) or base
            local state = record.command == "follow" and "Walking" or "Idle"
            if id == "builder" and s.Campaign.Builder then
                local building = ctx.Building.find(s.Campaign, s.Campaign.Builder.order)
                local site = building and ctx.Services.BuildingService:Position(player, building)
                if building and building.readyAt > os.time() and site and (site - actor.model.PrimaryPart.Position).Magnitude < 300 then
                    target = site + Vector3.new(0, 3, 5); state = "Working"
                end
            elseif id == "medic" and playerRoot and (playerRoot.Position - actor.model.PrimaryPart.Position).Magnitude <= 22 and os.clock() >= actor.nextHeal then
                local humanoid = ctx:Alive(player)
                if humanoid and humanoid.Health < humanoid.MaxHealth then
                    actor.nextHeal = os.clock() + 6; humanoid.Health = math.min(humanoid.MaxHealth, humanoid.Health + 4); state = "Working"
                    ctx.Remotes.EffectsRemote:FireClient(player, "medic_heal", {from = actor.model.PrimaryPart.Position})
                end
            elseif id == "guard" then
                local zombie, distance = ctx.Services.ZombieService:Nearest(actor.model.PrimaryPart.Position, 95)
                if zombie then
                    state = "Aiming"
                    if distance > 24 then target = zombie.model.PrimaryPart.Position end
                    if distance <= 90 and os.clock() >= actor.nextShot and (s.Campaign.Stockpile.rifle_ammo or 0) > 0
                        and ctx:LineOfSight(actor.model.PrimaryPart.Position + Vector3.new(0, 2, 0), zombie.model.PrimaryPart.Position, {actor.model, zombie.model}) then
                        actor.nextShot = os.clock() + 0.72; s.Campaign.Stockpile.rifle_ammo -= 1
                        ctx.Services.ZombieService:Damage(zombie, 22, player)
                        ctx:FX("shot", {from = actor.model.PrimaryPart.Position + Vector3.new(0, 2, 0), to = zombie.model.PrimaryPart.Position, melee = false, hit = true, user = 0, weapon = "rifle"})
                    end
                end
            elseif id == "scout" then
                local zombie, distance = ctx.Services.ZombieService:Nearest(actor.model.PrimaryPart.Position, 150)
                s.ScoutPing = zombie and {position = zombie.model.PrimaryPart.Position, kind = zombie.kind, distance = math.floor(distance)} or nil
            end
            if target then ctx.Services.NPCService:Walk(actor, target) end
            if actor.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1 and state ~= "Aiming" and state ~= "Working" then state = "Walking" end
            actor.model:SetAttribute("State", state)
        end
    end
    function Service:Init()
        task.spawn(function()
            while task.wait(0.5) do for player, s in ctx.Sessions do self:Step(player, s) end end
        end)
    end
    return Service
end
