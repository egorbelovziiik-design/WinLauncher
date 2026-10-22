--!nonstrict
return function(ctx)
    local Service = {}
    function Service:Init()
        ctx.Services.NetworkService:Register("MovementRemote", true, function(player, s, p)
            if p.action == "sprint" and type(p.value) == "boolean" then s.Sprinting = p.value
            elseif p.action == "crouch" and type(p.value) == "boolean" then s.Crouching = p.value
            elseif p.action == "commander" then
                if s.Commander then s.Commander = false
                elseif ctx:InPlot(player, 0) then s.Commander = true; s.Sprinting = false
                else ctx:Notify(player, "Режим Commander работает только на вашем участке.", "error") end
                ctx.Remotes.StateRemote:FireClient(player, "commander", s.Commander == true)
            elseif p.action == "flashlight" then
                s.Flashlight = not s.Flashlight
                local head = player.Character and player.Character:FindFirstChild("Head")
                if head then
                    local light = head:FindFirstChild("SurvivorLight") or Instance.new("SpotLight")
                    light.Name = "SurvivorLight"; light.Face = Enum.NormalId.Front; light.Angle = 85
                    light.Range = 55; light.Brightness = 2; light.Color = Color3.fromRGB(255, 237, 198)
                    light.Enabled = s.Flashlight; light.Parent = head
                end
            end
        end)
        task.spawn(function()
            while task.wait(0.25) do
                for player, s in ctx.Sessions do
                    if not s.Loaded or not s.Active or s.Closing or s.Frozen then continue end
                    local h = ctx:Alive(player); local root = ctx:Root(player)
                    if not h or not root then continue end
                    if s.Commander and not ctx:InPlot(player, 4) then s.Commander = false; ctx.Remotes.StateRemote:FireClient(player, "commander", false) end
                    local moving = h.MoveDirection.Magnitude > 0.05 or root.AssemblyLinearVelocity.Magnitude > 2
                    local sprint = s.Sprinting and not s.Crouching and not s.Commander and s.Stamina > 4 and moving
                    h.WalkSpeed = s.Commander and 0 or s.Crouching and 8 or sprint and 24 or 16
                    s.Stamina = math.clamp(s.Stamina + (sprint and -3 or 3.5), 0, 100)
                    -- Correct large character warps. NetworkService independently validates every sensitive request.
                    local now = os.clock()
                    if s.SafePosition and now - (s.PositionAt or now) < 1 and now > (s.SpawnGrace or 0) then
                        local delta = root.Position - s.SafePosition
                        if Vector3.new(delta.X, 0, delta.Z).Magnitude > 22 then
                            root.CFrame = CFrame.new(s.SafePosition); root.AssemblyLinearVelocity = Vector3.zero
                        end
                    end
                    s.SafePosition = root.Position; s.PositionAt = now
                end
            end
        end)
        task.spawn(function()
            while task.wait(5) do
                for player, s in ctx.Sessions do
                    if not s.Active or s.Closing or s.Frozen or not ctx:Alive(player) then continue end
                    local difficulty = s.Data.Settings.difficulty
                    local rate = difficulty == "Relaxed" and 0.25 or difficulty == "Hard" and 1.3 or 0.65
                    s.Campaign.Hunger = math.max(0, s.Campaign.Hunger - rate)
                    s.Campaign.Thirst = math.max(0, s.Campaign.Thirst - rate * 1.35)
                    if s.Campaign.Hunger <= 0 or s.Campaign.Thirst <= 0 then ctx.Services.CombatService:Hurt(player, 2)
                    elseif ctx:InShelter(player) and s.Campaign.Hunger > 50 and s.Campaign.Thirst > 50 then
                        local h = ctx:Alive(player); h.Health = math.min(h.MaxHealth, h.Health + 2)
                    end
                end
            end
        end)
    end
    return Service
end
