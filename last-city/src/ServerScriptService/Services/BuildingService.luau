--!nonstrict
return function(ctx)
    local CollectionService = game:GetService("CollectionService")
    local Service = {Models = {}}
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
        local building = b.readyAt > os.time()
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
        self.Models[player] = nil
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
                    if ctx.Services.NPCService and ctx.Services.NPCService.Evacuate then ctx.Services.NPCService:Evacuate(player, result) end
                    self:Render(player, result); s.Data.Stats.buildings += 1; s.Campaign.XP += 15
                    ctx.Services.QuestService:Record(player, "build", p.id, 1)
                    ctx.Progression.fact(s.Campaign, "build")
                    if p.id == "generator" then ctx.Progression.fact(s.Campaign, "generator") end
                    ctx:Notify(player, ctx.BuildingDefs[p.id].name .. ": строительство началось", "success")
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
