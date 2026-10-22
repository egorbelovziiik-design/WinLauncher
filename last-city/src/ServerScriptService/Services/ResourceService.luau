--!nonstrict
return function(ctx)
    local Debris = game:GetService("Debris")
    local Service = {Drops = {}, Nodes = {}}
    function Service:CanDrop(player)
        local list = self.Drops[player.UserId] or {}
        local count = 0; for _, part in list do if part.Parent then count += 1 end end
        return count < 12
    end
    function Service:Attach(part, item, amount, cooldown, drop, ownerId, ownerToken)
        local prompt = Instance.new("ProximityPrompt"); prompt.ActionText = drop and "Подобрать" or "Собрать"
        prompt.ObjectText = ctx.Items[item].name .. " ×" .. amount; prompt.HoldDuration = 0.45
        prompt.MaxActivationDistance = 10; prompt.RequiresLineOfSight = true; prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.Parent = part
        local state = {part = part, readyAt = 0, busy = false}; table.insert(self.Nodes, state)
        prompt.Triggered:Connect(function(player)
            local s = ctx.Services.NetworkService:Permit(player, "loot", true)
            local root = ctx:Root(player)
            if not s or not root or not part.Parent or (root.Position - part.Position).Magnitude > ctx.Config.InteractRange or state.busy or state.readyAt > os.time() then return end
            if ownerId and (player.UserId ~= ownerId or s.Token ~= ownerToken) then
                ctx:Notify(player, "Личный предмет: его может вернуть только владелец в этой сессии."); return
            end
            if not ctx:LineOfSight(root.Position, part.Position, {player.Character, part}) then return end
            state.busy = true
            local ok, err = ctx.Inventory.grant(s.Campaign, {[item] = amount})
            if ok then
                ctx.Progression.fact(s.Campaign, "pickup")
                if item == "metal" or item == "wood" then ctx.Progression.fact(s.Campaign, "resource") end
                ctx.Services.QuestService:Record(player, "collect", item, amount)
                ctx:Notify(player, "+" .. amount .. " " .. ctx.Items[item].name, "success")
                if drop then part:Destroy() else
                    state.readyAt = os.time() + cooldown; prompt.Enabled = false; part.Transparency = 0.75
                end
                ctx:Changed(player)
            else ctx:Notify(player, err, "error") end
            state.busy = false
        end)
        return prompt
    end
    function Service:Drop(player, id, count, position)
        local p = ctx.Factory.part(ctx.World.LootSpawns, "Dropped_" .. id, Vector3.new(1.8, 1.8, 1.8), CFrame.new(position.X, 1, position.Z), Color3.fromRGB(170, 130, 76), Enum.Material.Wood)
        local list = self.Drops[player.UserId] or {}; self.Drops[player.UserId] = list
        for index = #list, 1, -1 do if not list[index].Parent then table.remove(list, index) end end
        table.insert(list, p); self:Attach(p, id, count, 0, true, player.UserId, ctx.Sessions[player].Token); Debris:AddItem(p, 90)
    end
    function Service:RemovePlayer(player)
        for _, part in self.Drops[player.UserId] or {} do if part.Parent then part:Destroy() end end
        self.Drops[player.UserId] = nil
    end
    function Service:Supply(part)
        local prompt = Instance.new("ProximityPrompt"); prompt.ActionText = "Найти припасы"; prompt.ObjectText = "MERIDIAN • " .. part.Name
        prompt.HoldDuration = 0.6; prompt.MaxActivationDistance = 10; prompt.RequiresLineOfSight = true; prompt.Parent = part
        prompt.Triggered:Connect(function(player)
            local s = ctx.Services.NetworkService:Permit(player, "loot", true)
            local root = ctx:Root(player)
            if not s or not root or (root.Position - part.Position).Magnitude > ctx.Config.InteractRange then return end
            if not ctx:LineOfSight(root.Position, part.Position, {player.Character, part}) then return end
            local q = s.Campaign.Quests.lost_supplies
            if not q or q.status ~= "Active" then ctx:Notify(player, "Примите задание «Потерянные припасы» у Макса."); return end
            if q.seen["supply:" .. part.Name] then ctx:Notify(player, "Этот ящик уже найден."); return end
            ctx.Services.QuestService:Record(player, "supply", "market", 1, part.Name)
            ctx.Progression.fact(s.Campaign, "pickup"); ctx:Notify(player, "Ящик отмечен. Макс будет рад.", "success")
        end)
    end
    function Service:QuestCache(part)
        local id = part.QuestId.Value
        local def = ctx.QuestDefs[id]
        if not def then return end
        local event = part.QuestEvent.Value; local target = part.QuestTarget.Value
        local prompt = Instance.new("ProximityPrompt"); prompt.ActionText = "Осмотреть"
        prompt.ObjectText = part.QuestLabel.Value; prompt.HoldDuration = 0.6; prompt.MaxActivationDistance = 10
        prompt.RequiresLineOfSight = true; prompt.Parent = part
        prompt.Triggered:Connect(function(player)
            local s = ctx.Services.NetworkService:Permit(player, "loot", true); local root = ctx:Root(player)
            if not s or not root or not part.Parent or (root.Position - part.Position).Magnitude > ctx.Config.InteractRange then return end
            if not ctx:LineOfSight(root.Position, part.Position, {player.Character, part}) then return end
            local q = s.Campaign.Quests[id]
            if not q or q.status ~= "Active" then ctx:Notify(player, "Нужно активное задание «" .. def.name .. "»."); return end
            if q.seen[event .. ":" .. part.Name] then ctx:Notify(player, "Вы уже осмотрели этот предмет."); return end
            ctx.Services.QuestService:Record(player, event, target, 1, part.Name)
            ctx.Progression.fact(s.Campaign, "pickup"); ctx:Notify(player, "Найдено: " .. part.QuestLabel.Value, "success")
        end)
        return prompt
    end
    function Service:Init()
        for _, node in ctx.World.LootSpawns:GetChildren() do
            if not node:IsA("BasePart") then continue end
            local value = node:FindFirstChild("ItemId")
            if node:FindFirstChild("QuestId") then self:QuestCache(node)
            elseif value then self:Attach(node, value.Value, node.Amount.Value, node.Cooldown.Value, false)
            elseif node.Name:match("^Supply") then self:Supply(node) end
        end
        task.spawn(function()
            while task.wait(2) do
                for index = #self.Nodes, 1, -1 do
                    local node = self.Nodes[index]
                    if not node.part.Parent then table.remove(self.Nodes, index)
                    elseif node.readyAt > 0 and node.readyAt <= os.time() then
                        node.readyAt = 0; node.part.Transparency = 0
                        local prompt = node.part:FindFirstChildOfClass("ProximityPrompt"); if prompt then prompt.Enabled = true end
                    end
                end
                for userId, list in self.Drops do
                    for i = #list, 1, -1 do if not list[i].Parent then table.remove(list, i) end end
                    if #list == 0 then self.Drops[userId] = nil end
                end
            end
        end)
    end
    return Service
end
