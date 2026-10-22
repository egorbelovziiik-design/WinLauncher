--!nonstrict
return function(ctx)
    local TweenService = game:GetService("TweenService")
    local Players = game:GetService("Players")
    local Service = {Doors = {}}
    function Service:Attach(model)
        local hinge, leaf = model:FindFirstChild("Hinge"), model:FindFirstChild("Leaf")
        local width = model:FindFirstChild("DoorWidth")
        if not hinge or not leaf or not width then return end
        local prompt = Instance.new("ProximityPrompt"); prompt.ActionText = "Открыть дверь"
        prompt.ObjectText = model.DoorLabel.Value; prompt.MaxActivationDistance = 14; prompt.HoldDuration = 0.2
        prompt.RequiresLineOfSight = false; prompt.Parent = hinge
        local state = {open = false, busy = false}; self.Doors[model] = state
        local closed = leaf.CFrame
        prompt.Triggered:Connect(function(player)
            local s = ctx.Services.NetworkService:Permit(player, "door", true); local root = ctx:Root(player)
            if not s or not root or state.busy or (root.Position - hinge.Position).Magnitude > 16 then return end
            if not ctx:LineOfSight(root.Position, hinge.Position, {player.Character, model}) then return end
            if state.open then
                for _, other in Players:GetPlayers() do
                    local r = ctx:Root(other)
                    if r and (r.Position - hinge.Position).Magnitude < width.Value + 1 then ctx:Notify(player, "Отойдите от зоны движения двери, чтобы закрыть её."); return end
                end
            end
            state.busy = true; state.open = not state.open
            model:SetAttribute("Open", state.open)
            local goal = state.open and hinge.CFrame * CFrame.Angles(0, -math.pi / 2, 0) * CFrame.new(width.Value / 2, 0, 0) or closed
            TweenService:Create(leaf, TweenInfo.new(0.45, Enum.EasingStyle.Quad), {CFrame = goal}):Play()
            prompt.ActionText = state.open and "Закрыть дверь" or "Открыть дверь"
            task.delay(0.5, function() state.busy = false end)
        end)
        return prompt
    end
    function Service:Init()
        local folder = ctx.World:FindFirstChild("Interactables")
        if folder then for _, model in folder:GetChildren() do self:Attach(model) end end
    end
    return Service
end
