--!nonstrict
return function(ctx)
    local CollectionService = game:GetService("CollectionService")
    local Service = {Models = {}, BuilderTicks = {}, BuilderDeadlines = {}}
    function Service:Position(player, b)
        local s = ctx.Sessions[player]; local plot = ctx.Config.Plots[s.Plot]
        return Vector3.new(plot.x + b.x, 0, plot.z + b.z)
    end
    function Service:Render(player, b)
        local map = self.Models[player] or {}; self.Models[player] = map
        local model = map[b.uid]
        if not model then
            model = ctx.Factory.building(b.id, ctx.BuildingDefs[b.id], CFrame.new(self:Position(player, b)) * CFrame.Angles(0, b.r * math.pi / 2, 0))
            model.Name = "Building_" .. player.UserId .. "_" .. b.uid
            model:SetAttribute("Owner", player.UserId); model:SetAttribute("BuildingUid", b.uid)
            model:SetAttribute("BuildingId", b.id); model.Parent = ctx.World.Buildings
            CollectionService:AddTag(model, "PlayerBuilding"); map[b.uid] = model
        end
        model:SetAttribute("Health", b.hp); model:SetAttribute("MaxHealth", ctx.BuildingDefs[b.id].health); model:SetAttribute("ReadyAt", b.readyAt)
        local building = not ctx.Building.complete(ctx.Sessions[player].Campaign, b, os.time())
        local powered = ctx.Sessions[player].Power and ctx.Sessions[player].Power.powered[b.uid] == true
        model:SetAttribute("Powered", powered)
        for _, part in model:GetDescendants() do
            if part:IsA("BasePart") then part.Transparency = building and 0.5 or 0
            elseif part:IsA("Light") then part.Enabled = not building and powered end
        end
    end
    function Service:Restore(player)
        local s = ctx.Sessions[player]
        for _, b in s.Campaign.Buildings do self:Render(player, b) end
        local terminal = ctx.World.Plots:FindFirstChild("Plot" .. s.Plot):FindFirstChild("Terminal")
        terminal:SetAttribute("Owner", player.UserId)
        local sign = terminal:FindFirstChild("OwnerGui")
        if sign then sign.Label.Text = player.DisplayName .. "\nУЧАСТОК " .. s.Plot end
    end
    function Service:RemovePlayer(player)
        for _, m in self.Models[player] or {} do m:Destroy() end
        self.Models[player] = nil; self.BuilderTicks[player] = nil; self.BuilderDeadlines[player] = nil
        local s = ctx.Sessions[player]
        if s then
            local terminal = ctx.World.Plots:FindFirstChild("Plot" .. s.Plot):FindFirstChild("Terminal")
            terminal:SetAttribute("Owner", nil)
            terminal.OwnerGui.Label.Text = "СВОБОДНЫЙ УЧАСТОК\n" .. s.Plot
        end
    end
    function Service:Damage(player, uid, amount)
        local s = ctx.Sessions[player]; if not s or not s.Active or s.Closing or s.Frozen then return end
        local b = ctx.Building.find(s.Campaign, uid); if not b then return end
        b.hp = math.max(0, b.hp - amount)
        if b.hp == 0 then
            local name = ctx.BuildingDefs[b.id].name
            ctx.Building.remove(s.Campaign, uid, false)
            local model = self.Models[player] and self.Models[player][uid]
            if model then model:Destroy(); self.Models[player][uid] = nil end
            ctx:Notify(player, name .. " уничтожена. Не забывайте ремонтировать оборону.", "error")
        else self:Render(player, b) end
        ctx:Changed(player)
    end
    function Service:Nearest(player, position)
        local s = ctx.Sessions[player]; if not s or not s.Active or s.Closing then return nil end
        local best, point, distance
        for _, b in s.Campaign.Buildings do
            local center = self:Position(player, b); local hx, hz = ctx.Building.extents(b.id, b.r)
            local closest = Vector3.new(math.clamp(position.X, center.X - hx, center.X + hx), position.Y, math.clamp(position.Z, center.Z - hz, center.Z + hz))
            local d = (closest - position).Magnitude
            if not distance or d < distance then best = b; point = closest; distance = d end
        end
        return best, point, distance
    end
    function Service:Placed(player, s, building, byBuilder)
        if ctx.Services.NPCService and ctx.Services.NPCService.Evacuate then ctx.Services.NPCService:Evacuate(player, building) end
        local specialist = ctx.Services.RecruitService and ctx.Services.RecruitService:AdjustBuildTime(player, building)
        self:Render(player, building); s.Data.Stats.buildings += 1; s.Campaign.XP += 15
        ctx.Services.QuestService:Record(player, "build", building.id, 1)
        ctx.Progression.fact(s.Campaign, "build")
        if building.id == "generator" or building.id == "diesel" or building.id == "advanced_generator" then ctx.Progression.fact(s.Campaign, "generator") end
        ctx:Notify(player, ctx.BuildingDefs[building.id].name .. (byBuilder and ": Степан начал строительство" or ": строительство началось") .. (specialist and " • строитель Аркадий ускорил срок на 35%" or ""), "success")
    end
    function Service:BuilderAccess(c, id, x, z, r)
        local hx, hz = ctx.Building.extents(id, r); local radius = 1.75
        local function reserved(px, pz)
            for _, zone in ctx.Config.PlotReserved do
                if math.abs(px - zone.x) < zone.hx + radius and math.abs(pz - zone.z) < zone.hz + radius then return true end
            end
            return false
        end
        local function blocked(px, pz)
            if math.abs(px) > ctx.Config.PlotHalfSize - radius or math.abs(pz) > ctx.Config.PlotHalfSize - radius then return true end
            -- Include the proposed footprint so a route that cuts through the
            -- future model is never accepted.
            if math.abs(px - x) < hx + radius and math.abs(pz - z) < hz + radius then return true end
            for _, other in c.Buildings do
                local ox, oz = ctx.Building.extents(other.id, other.r)
                if math.abs(px - other.x) < ox + radius and math.abs(pz - other.z) < oz + radius then return true end
            end
            return false
        end
        local targets = {}
        for _, point in {{x, z + hz + 4}, {x + hx + 4, z}, {x, z - hz - 4}, {x - hx - 4, z}} do
            if not blocked(point[1], point[2]) and not reserved(point[1], point[2]) then table.insert(targets, point) end
        end
        if #targets == 0 then return false end
        -- A cheap deterministic flood-fill catches sealed construction sites
        -- before any resources are charged. The protected entrance/terminal
        -- zone supplies a stable route origin and cannot contain a building.
        local step = 2; local queue = {}; local first = 1; local seen = {}
        local function add(px, pz)
            px = math.floor(px / step + 0.5) * step; pz = math.floor(pz / step + 0.5) * step
            local key = px .. ":" .. pz
            if not seen[key] and not blocked(px, pz) then seen[key] = true; table.insert(queue, {px, pz}) end
        end
        add(0, 36); add(0, 28); add(10, 34); add(-10, 34)
        while first <= #queue do
            local point = queue[first]; first += 1
            for _, target in targets do
                if math.abs(point[1] - target[1]) <= step and math.abs(point[2] - target[2]) <= step then return true end
            end
            add(point[1] + step, point[2]); add(point[1] - step, point[2]); add(point[1], point[2] + step); add(point[1], point[2] - step)
        end
        return false
    end
    function Service:BuilderWork(player, position, canWork, elapsed)
        local s = ctx.Sessions[player]; local state = s and s.Campaign.Builder
        if not s or not s.Loaded or s.Closing or not state then return false, false end
        local building = ctx.Building.find(s.Campaign, state.order)
        if not building then
            local changed = state.order ~= "" or state.remaining ~= 0 or state.working == true
            state.order = ""; state.remaining = 0; state.working = false
            if changed then ctx:Changed(player) end
            return false, false
        end
        local now = os.time(); local remaining = tonumber(state.remaining)
        if not remaining then remaining = math.max(0, building.readyAt - now) end
        if remaining <= 0 then
            state.order = ""; state.remaining = 0; state.working = false; building.readyAt = now
            self:Render(player, building); ctx:Changed(player)
            return false, true
        end
        local center = self:Position(player, building); local hx, hz = ctx.Building.extents(building.id, building.r)
        local atSite = false
        if typeof(position) == "Vector3" then
            local dx = math.max(0, math.abs(position.X - center.X) - hx)
            local dz = math.max(0, math.abs(position.Z - center.Z) - hz)
            atSite = math.sqrt(dx * dx + dz * dz) <= 9
        end
        local wasWorking = state.working == true
        state.working = s.Active and not s.Frozen and not s.Traveling and canWork == true and atSite
        local oldWhole = math.ceil(remaining); local nowClock = os.clock()
        local step = tonumber(elapsed)
        if step then step = math.clamp(step, 0, 0.5)
        else step = math.clamp(nowClock - (self.BuilderTicks[player] or nowClock), 0, 0.5) end
        self.BuilderTicks[player] = nowClock
        if state.working then remaining = math.max(0, remaining - step) end
        state.remaining = remaining
        local newWhole = math.ceil(remaining)
        -- readyAt is only a projected deadline for shared readiness checks. It is
        -- continuously rebased while work is paused and never drives progress.
        building.readyAt = remaining > 0 and now + math.max(1, newWhole) or now
        if self.BuilderDeadlines[player] ~= newWhole or wasWorking ~= state.working then
            self.BuilderDeadlines[player] = newWhole
            local model = self.Models[player] and self.Models[player][building.uid]
            if model then
                model:SetAttribute("ReadyAt", building.readyAt)
                model:SetAttribute("BuildProgress", math.clamp(1 - remaining / ctx.BuildingDefs[building.id].seconds, 0, 0.99))
            end
            ctx:Changed(player)
        end
        if remaining > 0 then
            if state.working and newWhole < oldWhole and newWhole % 3 == 0 then ctx:FX("build_spark", {from = position + Vector3.new(0, 1.2, 0), user = player.UserId}) end
            return atSite, false
        end
        state.order = ""; state.working = false; building.readyAt = now
        self:Render(player, building)
        ctx:Notify(player, ctx.BuildingDefs[building.id].name .. ": Степан завершил строительство.", "success")
        ctx:Changed(player)
        return true, true
    end
    function Service:AutoPlace(player, id)
        local s = ctx.Sessions[player]
        if not s or not s.Active or s.Closing or s.Frozen or not ctx.BuildingDefs[id] then return false, "Проект не найден" end
        local builder = s.Campaign.Builder
        local current = builder and ctx.Building.find(s.Campaign, builder.order)
        if current and (builder.remaining or math.max(0, current.readyAt - os.time())) > 0 then return false, "Степан уже строит: " .. ctx.BuildingDefs[current.id].name end
        if builder then builder.order = ""; builder.remaining = 0; builder.working = false end
        local candidates = {}
        for z = -40, 40, ctx.Config.Grid do
            for x = -40, 40, ctx.Config.Grid do
                for r = 0, 1 do table.insert(candidates, {x = x, z = z, r = r, distance = x * x + z * z}) end
            end
        end
        table.sort(candidates, function(a, b)
            if a.distance == b.distance then
                if a.z == b.z then return a.x == b.x and a.r < b.r or a.x < b.x end
                return a.z < b.z
            end
            return a.distance < b.distance
        end)
        local found
        for _, point in candidates do
            if ctx.Building.valid(s.Campaign, id, point.x, point.z, point.r) and self:BuilderAccess(s.Campaign, id, point.x, point.z, point.r) then found = point; break end
        end
        if not found then return false, "На участке нет свободной площадки с проходом для Степана" end
        local now = os.time()
        local ok, building = ctx.Building.place(s.Campaign, id, found.x, found.z, found.r, now)
        if not ok then return false, building end
        builder.order = building.uid
        self:Placed(player, s, building, true)
        builder.remaining = math.max(1, building.readyAt - now); builder.working = false
        building.readyAt = now + builder.remaining
        self.BuilderTicks[player] = 0; self.BuilderDeadlines[player] = nil
        ctx:Changed(player)
        return true, building
    end
    function Service:Init()
        for index in ctx.Config.Plots do
            local terminal = ctx.World.Plots:FindFirstChild("Plot" .. index):FindFirstChild("Terminal")
            local prompt = Instance.new("ProximityPrompt"); prompt.ObjectText = "Участок " .. index; prompt.ActionText = "Склад и управление"
            prompt.MaxActivationDistance = 10; prompt.RequiresLineOfSight = true; prompt.Parent = terminal
            prompt.Triggered:Connect(function(player)
                local s = ctx.Services.NetworkService:Permit(player, "terminal", true)
                local root = ctx:Root(player)
                if s and root and (root.Position - terminal.Position).Magnitude <= ctx.Config.InteractRange then
                    if s.Plot == index then ctx.Remotes.StateRemote:FireClient(player, "panel", "base")
                    else ctx:Notify(player, "Это чужой участок. Ваш номер: " .. s.Plot) end
                end
            end)
        end
        ctx.Services.NetworkService:Register("BuildRemote", true, function(player, s, p)
            if not ctx:InPlot(player, 12) then ctx:Notify(player, "Стройте только на своём участке. Его номер: " .. s.Plot, "error"); return end
            local ok, result
            if p.action == "place" then
                ok, result = ctx.Building.place(s.Campaign, p.id, p.x, p.z, p.r, os.time())
                if ok then
                    -- Placement validity is data-only; residents never block it. Move any resident
                    -- out of the accepted footprint before the physical model is parented.
                    self:Placed(player, s, result, false)
                end
            elseif p.action == "repair" then
                ok, result = ctx.Building.repair(s.Campaign, p.uid)
                if ok then self:Render(player, ctx.Building.find(s.Campaign, p.uid)) end
            elseif p.action == "demolish" then
                ok, result = ctx.Building.remove(s.Campaign, p.uid, true)
                if ok and self.Models[player] and self.Models[player][p.uid] then self.Models[player][p.uid]:Destroy(); self.Models[player][p.uid] = nil end
            else return end
            if not ok then ctx:Notify(player, result or "Строительство отклонено", "error") end
            ctx:Changed(player)
        end)
    end
    return Service
end
