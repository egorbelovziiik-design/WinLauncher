--!nonstrict
return function(ctx)
    local Pathfinding = game:GetService("PathfindingService")
    local CollectionService = game:GetService("CollectionService")
    local Service = {NPCs = {}, Workers = {}, PendingPaths = 0}
    local definitions = {
        alex = {
            name = "Капитан Алекс", role = "Лидер убежища", at = Vector3.new(-18, 3, -55), style = "alex", speed = 8,
            color = Color3.fromRGB(69, 91, 77), skin = Color3.fromRGB(181, 148, 115), pause = 2.4,
            patrol = {Vector3.new(-18, 3, -55), Vector3.new(-7, 3, -46), Vector3.new(9, 3, -46), Vector3.new(18, 3, -56), Vector3.new(0, 3, -62)},
            texts = {
                "Мы потеряли город, но не друг друга. За воротами — заражённые. Помоги расчистить подход, и я выдам тебе оружие.",
                "Держись освещённых улиц и не трать патроны впустую. Если выполнил задачу — возвращайся ко мне за наградой.",
            },
        },
        max = {
            name = "Торговец Макс", role = "Снабжение и торговля", at = Vector3.new(34, 3, -103), style = "max", speed = 7.2,
            color = Color3.fromRGB(137, 89, 51), skin = Color3.fromRGB(193, 156, 121), pause = 4,
            patrol = {Vector3.new(34, 3, -103), Vector3.new(47, 3, -99), Vector3.new(48, 3, -117), Vector3.new(35, 3, -121)},
            texts = {
                "Патроны, вода, лекарства. Покупки за Survival Cash, не за Coins. А ещё мне нужен кто-то, кто найдёт ящики в старом MERIDIAN.",
                "Аптека к западу ещё не полностью разграблена. Нажми E рядом со мной, когда захочешь принять задание или открыть торговлю.",
            },
        },
        victor = {
            name = "Инженер Виктор", role = "Мастерская и энергия", at = Vector3.new(-45, 3, -128), style = "victor", speed = 8.6,
            color = Color3.fromRGB(68, 100, 117), skin = Color3.fromRGB(174, 139, 106), pause = 1.5,
            patrol = {Vector3.new(-45, 3, -128), Vector3.new(-58, 3, -125), Vector3.new(-54, 3, -146), Vector3.new(-37, 3, -145)},
            texts = {
                "Без света не будет города. Генератор берёт топливо со склада. Мастерская перерабатывает лом, а работникам нужны еда и вода.",
                "Ставь постройки вплотную, если их края только касаются. Настоящее пересечение сервер всё равно отклонит.",
            },
        },
    }
    local function actorPrompt(model, objectText, callback)
        local parent = model:FindFirstChild("Head") or model.PrimaryPart
        local prompt = Instance.new("ProximityPrompt")
        prompt.Name = "TalkPrompt"; prompt.ActionText = "Поговорить"; prompt.ObjectText = objectText
        prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.GamepadKeyCode = Enum.KeyCode.ButtonX
        prompt.MaxActivationDistance = 11; prompt.HoldDuration = 0; prompt.RequiresLineOfSight = true
        prompt.ClickablePrompt = true; prompt.Exclusivity = Enum.ProximityPromptExclusivity.OnePerButton
        prompt:SetAttribute("ServerAuthoritative", true); prompt.Parent = parent
        prompt.Triggered:Connect(callback)
        return prompt
    end
    function Service:Near(player, id)
        local npc = self.NPCs[id]; local root = ctx:Root(player)
        return npc and root and npc.model.PrimaryPart and (npc.model.PrimaryPart.Position - root.Position).Magnitude <= ctx.Config.InteractRange
            and ctx:LineOfSight(root.Position, npc.model.PrimaryPart.Position, {player.Character, npc.model})
    end
    function Service:Talk(player, id)
        if not self:Near(player, id) then return end
        local npc = self.NPCs[id]; local def = definitions[id]
        local s = ctx.Sessions[player]; if not s then return end
        npc.talkUntil = os.clock() + 18; npc.humanoid:MoveTo(npc.model.PrimaryPart.Position)
        npc.path = nil; npc.model:SetAttribute("State", "Talking"); npc.talkCount = (npc.talkCount or 0) + 1
        local root = ctx:Root(player); local at = npc.model.PrimaryPart.Position
        if root and (Vector3.new(root.Position.X, at.Y, root.Position.Z) - at).Magnitude > 0.1 then
            npc.model:PivotTo(CFrame.lookAt(at, Vector3.new(root.Position.X, at.Y, root.Position.Z)))
        end
        ctx.Progression.fact(s.Campaign, "talk")
        local text = def.texts[(npc.talkCount - 1) % #def.texts + 1]
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {id = id, name = def.name, role = def.role, text = text, model = npc.model})
        ctx:Changed(player)
    end
    local function label(model, title, subtitle)
        local gui = Instance.new("BillboardGui"); gui.Size = UDim2.fromOffset(220, 56); gui.StudsOffset = Vector3.new(0, 2.4, 0)
        gui.AlwaysOnTop = false; gui.MaxDistance = 65; gui.Adornee = model:FindFirstChild("Head") or model.PrimaryPart; gui.Parent = model
        local text = Instance.new("TextLabel"); text.Size = UDim2.fromScale(1, 1); text.BackgroundTransparency = 1
        text.Text = title .. "\n" .. subtitle; text.Font = Enum.Font.GothamMedium; text.TextSize = 13
        text.TextColor3 = Color3.fromRGB(249, 222, 178); text.TextStrokeTransparency = 0.3; text.Parent = gui
    end
    function Service:TalkWorker(player, actor, worker, plotNumber)
        local root = ctx:Root(player)
        if not root or not actor.model.Parent or (actor.model.PrimaryPart.Position - root.Position).Magnitude > ctx.Config.InteractRange then return end
        if not ctx:LineOfSight(root.Position, actor.model.PrimaryPart.Position, {player.Character, actor.model}) then return end
        actor.talkUntil = os.clock() + 12; actor.model:SetAttribute("State", "Talking"); actor.humanoid:MoveTo(actor.model.PrimaryPart.Position)
        local state = actor.model:GetAttribute("WorkState") or "Idle"
        local lines = {
            Working = "Я на смене. Пока есть еда, вода и энергия, производство будет работать.",
            Fleeing = "Заражённые слишком близко! Сначала очисти участок, потом поговорим.",
            Eating = "Короткий перерыв. Без запасов еды и воды люди не смогут работать.",
            Sleeping = "Ночная смена закончена. Утром вернусь к назначенной постройке.",
            Idle = "Назначь меня на ферму или мастерскую через терминал участка — тогда я начну настоящую работу.",
        }
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {
            id = "worker", name = worker.name, role = "Житель участка №" .. plotNumber,
            text = lines[state] or lines.Idle, model = actor.model,
        })
    end
    function Service:AddWorkers(player)
        local s = ctx.Sessions[player]; local plot = ctx.Config.Plots[s.Plot]
        self.Workers[player] = {}
        for index, worker in s.Campaign.Workers do
            local style = index == 1 and "worker_mira" or "worker_lev"
            local skin = index == 1 and Color3.fromRGB(181, 140, 108) or Color3.fromRGB(151, 113, 86)
            local model, h = ctx.Factory.rig(worker.name, CFrame.new(plot.x + index * 5 - 8, 3, plot.z + 34), Color3.fromRGB(95, 108, 90), skin, 1, style)
            model.Name = "Worker_" .. player.UserId .. "_" .. index; model:SetAttribute("Owner", player.UserId)
            model:SetAttribute("AnimationSeed", (player.UserId + index * 17) % 97); model:SetAttribute("WorkState", "Idle")
            model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(model, "LastCityActor")
            h.MaxHealth = 100; h.Health = 100; h.WalkSpeed = index == 1 and 8.2 or 9.2
            label(model, worker.name, "Житель вашего убежища • E")
            local actor = {model = model, humanoid = h, nextMove = 0, path = nil, pathIndex = 1, index = index, talkUntil = 0}
            actorPrompt(model, worker.name, function(visitor)
                if visitor ~= player then ctx:Notify(visitor, "Это житель другого участка."); return end
                if ctx.Services.NetworkService:Permit(visitor, "dialogue", true) then self:TalkWorker(visitor, actor, worker, s.Plot) end
            end)
            table.insert(self.Workers[player], actor)
        end
    end
    function Service:RemovePlayer(player)
        for _, worker in self.Workers[player] or {} do worker.model:Destroy() end
        self.Workers[player] = nil
    end
    function Service:Walk(actor, target)
        if not actor.model.Parent then return end
        local root = actor.model.PrimaryPart
        local horizontalTarget = Vector3.new(target.X, root.Position.Y, target.Z)
        if (horizontalTarget - root.Position).Magnitude < 1.8 then actor.humanoid:MoveTo(root.Position); actor.path = nil; return end
        if ctx:LineOfSight(root.Position + Vector3.new(0, 1, 0), horizontalTarget + Vector3.new(0, 1, 0), {actor.model}) then
            actor.path = nil; actor.humanoid:MoveTo(target); return
        end
        if actor.path then
            local waypoint = actor.path[actor.pathIndex]
            if waypoint and (root.Position - waypoint.Position).Magnitude < 4 then actor.pathIndex += 1; waypoint = actor.path[actor.pathIndex] end
            if waypoint then
                if waypoint.Action == Enum.PathWaypointAction.Jump then actor.humanoid.Jump = true end
                actor.humanoid:MoveTo(waypoint.Position)
            else actor.path = nil end
        end
        if actor.pending or os.clock() < actor.nextMove or self.PendingPaths >= 2 then return end
        actor.nextMove = os.clock() + 2.5 + math.random(); actor.pending = true; self.PendingPaths += 1
        task.spawn(function()
            local path = Pathfinding:CreatePath({AgentRadius = 2, AgentHeight = 6, AgentCanJump = true, WaypointSpacing = 5})
            local ok = pcall(function() path:ComputeAsync(root.Position, target) end)
            self.PendingPaths -= 1; actor.pending = false
            if not actor.model.Parent then return end
            if ok and path.Status == Enum.PathStatus.Success then actor.path = path:GetWaypoints(); actor.pathIndex = 2
            else actor.path = nil; actor.humanoid:MoveTo(target) end
        end)
    end
    function Service:Init()
        local order = 0
        for id, def in definitions do
            order += 1
            local model, h = ctx.Factory.rig(def.name, CFrame.new(def.at), def.color, def.skin, 1, def.style)
            model.Name = "NPC_" .. id; model:SetAttribute("NPCId", id); model:SetAttribute("State", "Idle"); model:SetAttribute("AnimationSeed", order * 19)
            model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(model, "LastCityActor")
            h.MaxHealth = 1000; h.Health = 1000; h.WalkSpeed = def.speed
            label(model, def.name, def.role .. " • E")
            local actor = {model = model, humanoid = h, at = def.at, talkUntil = 0, nextMove = 0, pathIndex = 1, patrolIndex = 1, pauseUntil = 0, talkCount = 0}
            self.NPCs[id] = actor
            actorPrompt(model, def.name, function(player)
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
            while task.wait(0.25) do
                local now = os.clock(); local hour = game:GetService("Lighting").ClockTime
                for id, npc in self.NPCs do
                    local def = definitions[id]
                    if now <= npc.talkUntil then
                        npc.model:SetAttribute("State", "Talking")
                    else
                        local target = def.patrol[npc.patrolIndex]
                        local distance = (Vector3.new(target.X, npc.model.PrimaryPart.Position.Y, target.Z) - npc.model.PrimaryPart.Position).Magnitude
                        if distance < 2.5 then
                            if npc.pauseUntil == 0 then npc.pauseUntil = now + def.pause end
                            if now >= npc.pauseUntil then npc.patrolIndex = npc.patrolIndex % #def.patrol + 1; npc.pauseUntil = 0; target = def.patrol[npc.patrolIndex]
                            else npc.humanoid:MoveTo(npc.model.PrimaryPart.Position) end
                        end
                        if npc.pauseUntil == 0 then self:Walk(npc, target) end
                        npc.model:SetAttribute("State", npc.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1 and "Walking" or "Idle")
                    end
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
                        if now <= actor.talkUntil and state ~= "Fleeing" then
                            actor.humanoid:MoveTo(actor.model.PrimaryPart.Position); actor.model:SetAttribute("State", "Talking")
                        else
                            self:Walk(actor, target)
                            local atTarget = (Vector3.new(target.X, actor.model.PrimaryPart.Position.Y, target.Z) - actor.model.PrimaryPart.Position).Magnitude < 8
                            worker.atWork = state == "Working" and atTarget
                            local walking = actor.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1
                            actor.model:SetAttribute("State", walking and (state == "Fleeing" and "Fleeing" or "Walking") or state)
                        end
                        actor.model:SetAttribute("WorkState", state)
                    end
                end
            end
        end)
    end
    return Service
end
