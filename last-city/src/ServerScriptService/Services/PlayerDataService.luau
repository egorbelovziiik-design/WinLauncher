--!nonstrict
return function(ctx)
    local Players = game:GetService("Players")
    local TeleportService = game:GetService("TeleportService")
    local RunService = game:GetService("RunService")
    local Service = {}
    function Service:Snapshot(player)
        local s = ctx.Sessions[player]
        if not s or not s.Loaded or not player.Parent then return end
        local bosses = {}
        if ctx.Services.ZombieService then
            for kind in ctx.Services.ZombieService.Bosses do bosses[kind] = ctx.Services.ZombieService:BossState(kind) end
        end
        ctx.Remotes.StateRemote:FireClient(player, "state", {
            profile = {Coins = s.Data.Coins, Stats = s.Data.Stats, Cosmetics = s.Data.Cosmetics, EquippedSkin = s.Data.EquippedSkin,
                Achievements = s.Data.Achievements, Settings = s.Data.Settings, TutorialRewarded = s.Data.TutorialRewarded},
            campaign = s.Campaign, mode = s.Mode, active = s.Active, plot = s.Plot, revision = s.Data.Revision,
            save = s.SaveState, lastSave = s.LastSave, stamina = s.Stamina, staminaMax = s.StaminaMax or 100, bosses = bosses,
            power = s.Power or {supply = 0, demand = 0, deficit = 0, spare = 0, powered = {}},
            stage = ctx.Progression.stage(s.Campaign), serverTime = os.time(), studio = RunService:IsStudio(),
            weather = ctx.Services.WorldService.Weather, raid = s.Raid or "Спокойно", shop = ctx.Services.ShopService:Stock(), scout = s.ScoutPing,
        })
    end
    function Service:Character(player, character)
        local h = character:WaitForChild("Humanoid", 10)
        local root = character:WaitForChild("HumanoidRootPart", 10)
        if not h or not root then return end
        local s = ctx.Sessions[player]
        if not s then return end
        if s.Traveling then
            -- Reset/respawn cancels the old server token. Without this, the replacement
            -- character could be teleported by a stale flight and active remotes stay locked.
            s.Traveling = nil
            ctx.Remotes.StateRemote:FireClient(player, "travel", {phase = "arrive", canceled = true})
        end
        h.WalkSpeed = 16; h.MaxHealth = 100
        s.SpawnGrace = os.clock() + 4; s.SafePosition = nil
        character:PivotTo(CFrame.new(0, 5, -106))
        root.Anchored = not s.Active
        local shield = character:FindFirstChild("MenuShield")
        if not s.Active and not shield then shield = Instance.new("ForceField"); shield.Name = "MenuShield"; shield.Visible = false; shield.Parent = character end
        h.Died:Connect(function()
            s.Commander = false; s.Reloading = nil; s.Stamina = 100
            if s.Traveling then
                s.Traveling = nil
                if root.Parent then root.Anchored = false end
                ctx.Remotes.StateRemote:FireClient(player, "travel", {phase = "arrive", canceled = true})
            end
            s.Campaign.Hunger = math.max(s.Campaign.Hunger, 35); s.Campaign.Thirst = math.max(s.Campaign.Thirst, 35)
            ctx:Notify(player, "Вас доставили в убежище. Инвентарь сохранён.", "info")
        end)
        if s.Active then ctx.Services.CombatService:SyncTool(player) end
    end
    function Service:JoinInternal(player)
        if ctx.Closing then player:Kick("Сервер закрывается"); return end
        local occupied = {}
        for _, other in ctx.Sessions do occupied[other.Plot] = true end
        local plot
        for index in ctx.Config.Plots do if not occupied[index] then plot = index; break end end
        if not plot then player:Kick("В этом убежище заняты все " .. #ctx.Config.Plots .. " участков. Выберите другой сервер."); return end
        ctx.Sessions[player] = {Loaded = false, Plot = plot, Active = false}
        player.CharacterAdded:Connect(function(character) self:Character(player, character) end)
        if player.Character then task.spawn(function() self:Character(player, player.Character) end) end
        player:SetAttribute("LastCityLoadPhase", "Profile")
        local loaded, err = ctx.Services.SaveService:Load(player)
        if not loaded then
            ctx.Sessions[player] = nil
            player:SetAttribute("LastCityLoadError", err)
            if player.Parent then player:Kick(err) end
            return
        end
        local s = ctx.Sessions[player]
        if not s then s = {Plot = plot}; ctx.Sessions[player] = s end
        for key, value in loaded do s[key] = value end
        s.Mode = game.PrivateServerId ~= "" and game.PrivateServerOwnerId == 0 and "solo" or "coop"
        s.Campaign = s.Data.Campaigns[s.Mode]; s.Loaded = true; s.Active = false; s.Stamina = 100
        s.SessionStart = os.clock(); s.LastPosition = nil
        if not player.Parent then s.Closing = true; ctx.Services.SaveService:Save(player, true); ctx.Sessions[player] = nil; return end
        player:SetAttribute("LastCityLoadPhase", "Base")
        ctx.Services.BuildingService:Restore(player)
        ctx.Services.NPCService:AddWorkers(player)
        ctx.Services.SquadService:SyncPlayer(player)
        ctx.Services.RecruitService:SyncPlayer(player)
        player:SetAttribute("LastCityLoadPhase", "Ready")
        self:Snapshot(player)
    end
    function Service:Join(player)
        if ctx.Sessions[player] then return end
        player:SetAttribute("LastCityLoadError", nil)
        local ok, err = xpcall(function() self:JoinInternal(player) end, debug.traceback)
        if not ok then
            player:SetAttribute("LastCityLoadError", "Ошибка подготовки игрока. См. Output: LC_JOIN")
            local s = ctx.Sessions[player]
            if s then s.Frozen = true; s.Active = false; s.Closing = true end
            if s and s.Loaded then task.spawn(function() ctx.Services.SaveService:Save(player, true) end) end
            warn("[LastCity LC_JOIN] " .. tostring(err))
        end
    end
    function Service:Start(player, s)
        if s.Active then return end
        local root = ctx:Root(player); local h = player.Character and player.Character:FindFirstChildOfClass("Humanoid")
        if not root or not h or h.Health <= 0 then ctx:Notify(player, "Персонаж загружается. Попробуйте ещё раз."); return end
        s.Active = true; s.Campaign.Started = true
        root.Anchored = false; h.Health = h.MaxHealth
        local shield = player.Character:FindFirstChild("MenuShield"); if shield then shield:Destroy() end
        ctx.Services.CombatService:SyncTool(player)
        ctx:Changed(player)
    end
    function Service:Init()
        Players.PlayerAdded:Connect(function(player) task.spawn(function() self:Join(player) end) end)
        Players.PlayerRemoving:Connect(function(player)
            local s = ctx.Sessions[player]
            if not s then return end
            if not s.Loaded then return end -- Load task releases its lease when it sees player.Parent == nil.
            s.Closing = true; s.Active = false
            ctx.Services.ResourceService:RemovePlayer(player)
            ctx.Services.SaveService:Save(player, true)
            ctx.Services.BuildingService:RemovePlayer(player)
            ctx.Services.NPCService:RemovePlayer(player)
            ctx.Services.SquadService:RemovePlayer(player)
            ctx.Services.RecruitService:RemovePlayer(player)
            ctx.Services.CombatService:Remove(player)
            ctx.Services.NetworkService:Remove(player)
            ctx.Sessions[player] = nil
        end)
        for _, player in Players:GetPlayers() do task.spawn(function() self:Join(player) end) end
        ctx.Services.NetworkService:Register("SessionRemote", false, function(player, s, p)
            -- SessionRemote also serves the pre-game menu, so its channel cannot require
            -- Active globally. During a flight only read-only sync/manual save remain valid.
            if s.Traveling and p.action ~= "sync" and p.action ~= "save" then return end
            if p.action == "sync" then self:Snapshot(player)
            elseif p.action == "play" then self:Start(player, s)
            elseif p.action == "save" then
                if (s.ManualSave or 0) + 20 > os.clock() then return end
                s.ManualSave = os.clock(); task.spawn(function() ctx.Services.SaveService:Save(player, false); self:Snapshot(player) end)
            elseif p.action == "solo" then
                if s.Mode == "solo" then self:Start(player, s); return end
                if RunService:IsStudio() then ctx:Notify(player, "В Studio используйте Play для одного игрока. Отдельная solo-кампания доступна после публикации."); return end
                if s.Teleporting then return end
                s.Teleporting = true
                task.spawn(function()
                    local options = Instance.new("TeleportOptions"); options.ShouldReserveServer = true
                    local ok, err = pcall(function() TeleportService:TeleportAsync(game.PlaceId, {player}, options) end)
                    if not ok then s.Teleporting = false; warn(err); ctx:Notify(player, "Не удалось открыть одиночный сервер. Попробуйте позже.", "error") end
                end)
            elseif p.action == "tutorial" and s.Active then
                if p.choice == "skip" then s.Campaign.Tutorial.status = "Skipped"
                elseif p.choice == "start" then
                    local facts = {}
                    -- Replays reuse already-proven one-time milestones rather than soft-locking
                    -- behind a storyline reward which correctly cannot be claimed twice.
                    for _, q in s.Campaign.Quests do
                        facts.quest = true
                        if q.status == "RewardClaimed" then facts.reward = true end
                    end
                    for _, b in s.Campaign.Buildings do
                        facts.build = true
                        if b.id == "generator" or b.id == "diesel" then facts.generator = true end
                    end
                    s.Campaign.Tutorial = {status = "Active", facts = facts}
                    s.TutorialOrigin = ctx:Root(player).Position
                end
                ctx:Changed(player)
            elseif p.action == "tutorialReward" and s.Active and ctx:InShelter(player) then
                ctx.Progression.fact(s.Campaign, "home")
                local wasRewarded = s.Data.TutorialRewarded
                local ok, err = ctx.Progression.tutorial(s.Data, s.Campaign)
                ctx:Notify(player, ok and (wasRewarded and "Повтор обучения завершён. Первая награда уже была получена." or "Обучение завершено! 500 Coins, 100 XP и набор получены.") or err, ok and "success" or "error")
                ctx:Changed(player)
            elseif p.action == "inventoryOpened" and s.Active then ctx.Progression.fact(s.Campaign, "inventory"); ctx:Changed(player)
            elseif p.action == "setting" then
                local allowed = {graphics = {"Low", "Medium", "High", "Ultra"}, difficulty = {"Relaxed", "Normal", "Hard"}}
                local bounds = {uiSound = {0, 1}, music = {0, 1}, effects = {0, 1}, voice = {0, 1}, sensitivity = {0.3, 2}, scale = {0.75, 1.15}}
                if allowed[p.id] and table.find(allowed[p.id], p.value) then s.Data.Settings[p.id] = p.value
                elseif p.id == "firstPerson" and type(p.value) == "boolean" then s.Data.Settings.firstPerson = p.value
                elseif bounds[p.id] and ctx.Util.finite(p.value) then s.Data.Settings[p.id] = math.clamp(p.value, bounds[p.id][1], bounds[p.id][2]) end
                ctx:Changed(player)
            end
        end)
        TeleportService.TeleportInitFailed:Connect(function(player)
            local s = ctx.Sessions[player]; if s then s.Teleporting = false; ctx:Notify(player, "Телепортация отменена. Вы остались на этом сервере.", "error") end
        end)
        task.spawn(function()
            while task.wait(1) do
                for player, s in ctx.Sessions do
                    if s.Loaded and not s.Closing then
                        if s.Active and not s.Frozen then
                            s.Campaign.Stats.seconds += 1; s.Data.Stats.seconds += 1
                            local root = ctx:Root(player)
                            if root and s.TutorialOrigin and (root.Position - s.TutorialOrigin).Magnitude >= 12 then ctx.Progression.fact(s.Campaign, "move") end
                            if ctx:InShelter(player) and ctx.Progression.stage(s.Campaign) == 12 then ctx.Progression.fact(s.Campaign, "home") end
                        end
                        self:Snapshot(player)
                    end
                end
            end
        end)
    end
    return Service
end
