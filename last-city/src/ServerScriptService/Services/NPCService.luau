--!nonstrict
return function(ctx)
    local Pathfinding = game:GetService("PathfindingService")
    local Service = {NPCs = {}, Workers = {}, PathBusy = false}
    local definitions = {
        alex = {name = "Капитан Алекс", role = "Лидер убежища", at = Vector3.new(-18, 3, -55), color = Color3.fromRGB(69, 91, 77), text = "Мы потеряли город, но не друг друга. За воротами — заражённые. Помоги расчистить подход, и я выдам тебе оружие."},
        max = {name = "Торговец Макс", role = "Снабжение и торговля", at = Vector3.new(34, 3, -103), color = Color3.fromRGB(137, 89, 51), text = "Патроны, вода, лекарства. Покупки за Survival Cash, не за Coins. А ещё мне нужен кто-то, кто найдёт ящики в старом MERIDIAN."},
        victor = {name = "Инженер Виктор", role = "Мастерская и энергия", at = Vector3.new(-45, 3, -128), color = Color3.fromRGB(68, 100, 117), text = "Без света не будет города. Генератор берёт топливо со склада. Мастерская перерабатывает лом, а работникам нужны еда и вода."},
    }
    function Service:Near(player, id)
        local npc = self.NPCs[id]; local root = ctx:Root(player)
        return npc and root and (npc.model.PrimaryPart.Position - root.Position).Magnitude <= ctx.Config.InteractRange
            and ctx:LineOfSight(root.Position, npc.model.PrimaryPart.Position, {player.Character, npc.model})
    end
    function Service:Talk(player, id)
        if not self:Near(player, id) then return end
        local npc = self.NPCs[id]; local def = definitions[id]
        local s = ctx.Sessions[player]; if not s then return end
        npc.talkUntil = os.clock() + 18; npc.humanoid:MoveTo(npc.model.PrimaryPart.Position)
        npc.model:SetAttribute("State", "Talking")
        ctx.Progression.fact(s.Campaign, "talk")
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {id = id, name = def.name, role = def.role, text = def.text, model = npc.model})
        ctx:Changed(player)
    end
    local function label(model, title, subtitle)
        local gui = Instance.new("BillboardGui"); gui.Size = UDim2.fromOffset(200, 54); gui.StudsOffset = Vector3.new(0, 3, 0)
        gui.AlwaysOnTop = false; gui.MaxDistance = 65; gui.Adornee = model.PrimaryPart; gui.Parent = model
        local text = Instance.new("TextLabel"); text.Size = UDim2.fromScale(1, 1); text.BackgroundTransparency = 1
        text.Text = title .. "\n" .. subtitle; text.Font = Enum.Font.GothamMedium; text.TextSize = 13
        text.TextColor3 = Color3.fromRGB(249, 222, 178); text.TextStrokeTransparency = 0.3; text.Parent = gui
    end
    function Service:AddWorkers(player)
        local s = ctx.Sessions[player]; local plot = ctx.Config.Plots[s.Plot]
        self.Workers[player] = {}
        for index, worker in s.Campaign.Workers do
            local model, h = ctx.Factory.rig(worker.name, CFrame.new(plot.x + index * 5 - 8, 3, plot.z + 34), Color3.fromRGB(95, 108, 90), Color3.fromRGB(177, 140, 108))
            model.Name = "Worker_" .. player.UserId .. "_" .. index; model:SetAttribute("Owner", player.UserId)
            model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil)
            h.MaxHealth = 100; h.Health = 100
            label(model, worker.name, "Житель вашего убежища")
            table.insert(self.Workers[player], {model = model, humanoid = h, nextMove = 0, path = nil, pathIndex = 1, index = index})
        end
    end
    function Service:RemovePlayer(player)
        for _, worker in self.Workers[player] or {} do worker.model:Destroy() end
        self.Workers[player] = nil
    end
    function Service:Walk(actor, target)
        if actor.path then
            local waypoint = actor.path[actor.pathIndex]
            if waypoint and (actor.model.PrimaryPart.Position - waypoint.Position).Magnitude < 4 then actor.pathIndex += 1; waypoint = actor.path[actor.pathIndex] end
            if waypoint then
                if waypoint.Action == Enum.PathWaypointAction.Jump then actor.humanoid.Jump = true end
                actor.humanoid:MoveTo(waypoint.Position)
            else actor.path = nil end
        end
        if os.clock() < actor.nextMove or self.PathBusy then return end
        actor.nextMove = os.clock() + 4 + math.random(); self.PathBusy = true
        task.spawn(function()
            local path = Pathfinding:CreatePath({AgentRadius = 2, AgentHeight = 5, AgentCanJump = true, WaypointSpacing = 6})
            local ok = pcall(function() path:ComputeAsync(actor.model.PrimaryPart.Position, target) end)
            self.PathBusy = false
            if not actor.model.Parent then return end
            if ok and path.Status == Enum.PathStatus.Success then actor.path = path:GetWaypoints(); actor.pathIndex = 2
            else actor.path = nil; actor.humanoid:MoveTo(target) end
        end)
    end
    function Service:Init()
        for id, def in definitions do
            local model, h = ctx.Factory.rig(def.name, CFrame.new(def.at), def.color, Color3.fromRGB(181, 148, 115))
            model.Name = "NPC_" .. id; model:SetAttribute("NPCId", id); model:SetAttribute("State", "Idle")
            model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil)
            h.MaxHealth = 1000; h.Health = 1000
            label(model, def.name, def.role)
            local prompt = Instance.new("ProximityPrompt"); prompt.ActionText = "Поговорить"; prompt.ObjectText = def.name
            prompt.MaxActivationDistance = 10; prompt.HoldDuration = 0; prompt.RequiresLineOfSight = true; prompt.Parent = model.PrimaryPart
            local actor = {model = model, humanoid = h, at = def.at, talkUntil = 0, nextMove = 0, pathIndex = 1}
            self.NPCs[id] = actor
            prompt.Triggered:Connect(function(player)
                if ctx.Services.NetworkService:Permit(player, "dialogue", true) then self:Talk(player, id) end
            end)
        end
        ctx.Services.NetworkService:Register("NPCRemote", true, function(player, s, p)
            if p.action == "talk" then self:Talk(player, p.id)
            elseif p.action == "assign" and ctx:InPlot(player, 12) and ctx.Util.integer(p.worker, 1, #s.Campaign.Workers) then
                local b = ctx.Building.find(s.Campaign, p.uid)
                if p.uid == "" or b and ctx.BuildingDefs[b.id].worker then
                    if b then for _, w in s.Campaign.Workers do if w.job == b.uid then w.job = "" end end end
                    s.Campaign.Workers[p.worker].job = p.uid; ctx:Changed(player)
                end
            end
        end)
        task.spawn(function()
            while task.wait(0.5) do
                local hour = game:GetService("Lighting").ClockTime
                for _, npc in self.NPCs do
                    if os.clock() > npc.talkUntil then
                        local offset = Vector3.new(math.sin(os.clock() / 16) * 5, 0, math.cos(os.clock() / 16) * 5)
                        self:Walk(npc, npc.at + offset); npc.model:SetAttribute("State", "Walking")
                    end
                    ctx.Factory.animate(npc.model, npc.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1, os.clock() * 5, false)
                end
                for player, actors in self.Workers do
                    local s = ctx.Sessions[player]; if not s or not s.Loaded or s.Closing then continue end
                    local plot = ctx.Config.Plots[s.Plot]
                    for index, actor in actors do
                        local worker = s.Campaign.Workers[index]
                        local building = ctx.Building.find(s.Campaign, worker.job)
                        local target = Vector3.new(plot.x + index * 5 - 8, 3, plot.z + 34)
                        local state = "Idle"
                        if hour >= 21 or hour < 6 then state = "Sleeping"
                        elseif hour >= 12 and hour < 13 then state = "Eating"
                        elseif building and building.readyAt <= os.time() then
                            local def = ctx.BuildingDefs[building.id]
                            target = Vector3.new(plot.x + building.x, 3, plot.z + building.z + def.size[3] / 2 + 3); state = "Working"
                        end
                        if ctx.Services.ZombieService:Nearest(actor.model.PrimaryPart.Position, 24) then
                            target = Vector3.new(plot.x, 3, plot.z + 36); state = "Fleeing"
                        end
                        self:Walk(actor, target)
                        worker.atWork = state == "Working" and (actor.model.PrimaryPart.Position - target).Magnitude < 8
                        local walking = actor.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1
                        actor.model:SetAttribute("State", walking and state ~= "Fleeing" and "Walking" or state)
                        ctx.Factory.animate(actor.model, walking, os.clock() * 6, false)
                    end
                end
            end
        end)
    end
    return Service
end
