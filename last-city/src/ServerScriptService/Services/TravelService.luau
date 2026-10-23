--!nonstrict
-- Quest-gated, server-authoritative helicopter transfer. The client only renders the flight;
-- the server owns the lock, timing and final character position.
return function(ctx)
    local Service = {}
    local function unlocked(c)
        local q = c.Quests[ctx.Config.SecondCity.unlockQuest]
        return q and q.status == "RewardClaimed"
    end
    function Service:Travel(player, fromId)
        local s = ctx.Sessions[player]; local root = ctx:Root(player); local humanoid = ctx:Alive(player)
        local transports = ctx.World:FindFirstChild("Transports")
        local source = transports and transports:FindFirstChild(fromId .. "Pad")
        if not s or not s.Active or s.Closing or s.Frozen or s.Traveling or not root or not humanoid or not source then return end
        if (root.Position - source.Position).Magnitude > ctx.Config.InteractRange then return end
        local destinationId = fromId == "City1" and "City2" or "City1"
        if not unlocked(s.Campaign) then
            ctx:Notify(player, "Маршрут закрыт. Возьмите у Алекса задание «ВТОРОЙ РУБЕЖ» и уничтожьте 25 заражённых.", "error"); return
        end
        local destination = transports:FindFirstChild(destinationId .. "Arrival") or transports:FindFirstChild(destinationId .. "Pad")
        if not destination then return end
        local token = {}; s.Traveling = token; s.Commander = false; s.Sprinting = false; s.Crouching = false
        s.SpawnGrace = os.clock() + 9; s.SafePosition = nil; humanoid.WalkSpeed = 0; root.Anchored = true
        ctx.Remotes.StateRemote:FireClient(player, "travel", {
            phase = "start", from = source.Position + Vector3.new(0, 7, 0), to = destination.Position + Vector3.new(0, 7, 0),
            duration = 6, destination = destinationId == "City2" and "НОВЫЙ РУБЕЖ" or "МЕРИДИАН",
            helicopter = fromId .. "Helicopter",
        })
        task.delay(6, function()
            if ctx.Sessions[player] ~= s or s.Traveling ~= token or s.Closing then return end
            local currentRoot = ctx:Root(player); local currentHumanoid = ctx:Alive(player)
            if not currentRoot or not currentHumanoid then
                s.Traveling = nil
                ctx.Remotes.StateRemote:FireClient(player, "travel", {phase = "arrive", canceled = true})
                return
            end
            currentRoot.CFrame = CFrame.new(destination.Position + Vector3.new(0, 3.2, 0))
            currentRoot.AssemblyLinearVelocity = Vector3.zero; currentRoot.Anchored = false; currentHumanoid.WalkSpeed = 16
            s.SafePosition = currentRoot.Position; s.PositionAt = os.clock(); s.SpawnGrace = os.clock() + 3; s.Traveling = nil
            s.Region = destinationId
            ctx.Services.SquadService:TravelWith(player, currentRoot.Position)
            if ctx.Services.RecruitService then ctx.Services.RecruitService:TravelWith(player, currentRoot.Position) end
            ctx.Remotes.StateRemote:FireClient(player, "travel", {phase = "arrive", destination = destinationId == "City2" and "НОВЫЙ РУБЕЖ" or "МЕРИДИАН"})
            ctx:Notify(player, "Посадка: " .. (destinationId == "City2" and "НОВЫЙ РУБЕЖ" or "МЕРИДИАН") .. ".", "success")
        end)
    end
    function Service:Init()
        local transports = ctx.World:FindFirstChild("Transports")
        if not transports then warn("[LastCity] Transports folder missing"); return end
        for _, id in {"City1", "City2"} do
            local pad = transports:FindFirstChild(id .. "Pad")
            if not pad or not pad:IsA("BasePart") then continue end
            local prompt = Instance.new("ProximityPrompt"); prompt.Name = "HelicopterPrompt"
            prompt.ActionText = id == "City1" and "Лететь во второй город" or "Вернуться в Меридиан"
            prompt.ObjectText = "Вертолётный маршрут"; prompt.HoldDuration = 0.8; prompt.MaxActivationDistance = 11
            prompt.RequiresLineOfSight = false; prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.Parent = pad
            prompt.Triggered:Connect(function(player)
                if ctx.Services.NetworkService:Permit(player, "travel", true) then self:Travel(player, id) end
            end)
        end
    end
    return Service
end
