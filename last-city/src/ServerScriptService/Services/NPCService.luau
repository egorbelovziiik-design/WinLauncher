--!nonstrict
return function(ctx)
    local Pathfinding = game:GetService("PathfindingService")
    local CollectionService = game:GetService("CollectionService")
    local Service = {NPCs = {}, Workers = {}, Builders = {}, PendingPaths = 0}
    local cityTwoNPC = {makar = true, nora = true, quartermaster = true, pilot = true}
    local function cityTwoUnlocked(c)
        local quest = c and c.Quests[ctx.Config.SecondCity.unlockQuest]
        return quest and quest.status == "RewardClaimed"
    end
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
            name = "Инженер Виктор", role = "Мастерская и энергия", at = Vector3.new(-48, 3, -121), style = "victor", speed = 8.2,
            color = Color3.fromRGB(68, 100, 117), skin = Color3.fromRGB(174, 139, 106), pause = 2.5,
            -- This route stays on the open apron instead of entering the cramped table/back-wall geometry.
            patrol = {Vector3.new(-48, 3, -121), Vector3.new(-38, 3, -122), Vector3.new(-39, 3, -130), Vector3.new(-57, 3, -130), Vector3.new(-58, 3, -122)},
            texts = {
                "Без света не будет города. Генератор берёт топливо со склада. Мастерская перерабатывает лом, а работникам нужны еда и вода.",
                "Ставь постройки вплотную, если их края только касаются. Настоящее пересечение сервер всё равно отклонит.",
            },
        },
        ira = {
            name = "Ира Волкова", role = "Жительница восточного карьера", at = Vector3.new(304, 3, 154), style = "ira", speed = 8,
            color = Color3.fromRGB(111, 82, 62), skin = Color3.fromRGB(191, 149, 116), pause = 3,
            patrol = {Vector3.new(304, 3, 154), Vector3.new(294, 3, 145), Vector3.new(286, 3, 158), Vector3.new(299, 3, 170)},
            texts = {
                "Из шахты снова доносится кирка. Но все шахтёры погибли. Прими задание и останови Бригадира в дальней выработке.",
                "Внутри держись рельсов и ищи аварийные фонари. За победу сразу получишь Glock: 10 в магазине и ещё 48 патронов.",
            },
        },
        lina = {
            name = "Доктор Лина", role = "Полевой медик", at = Vector3.new(-62, 3, 178), style = "resident_medic", speed = 7.4, ambient = true,
            color = Color3.fromRGB(142, 162, 147), skin = Color3.fromRGB(198, 158, 125), pause = 4,
            patrol = {Vector3.new(-62, 3, 178), Vector3.new(-78, 3, 181), Vector3.new(-46, 3, 181)},
            texts = {"Аптека впереди заражена, но на стеллажах ещё есть лекарства.", "Бронежилет снижает каждый удар, но сам изнашивается. Держи запас бинтов."},
        },
        roman = {
            name = "Роман Кедр", role = "Дежурный полиции", at = Vector3.new(62, 3, 177), style = "resident_guard", speed = 8.4, ambient = true,
            color = Color3.fromRGB(55, 78, 94), skin = Color3.fromRGB(167, 127, 97), pause = 2.5,
            patrol = {Vector3.new(62, 3, 177), Vector3.new(45, 3, 181), Vector3.new(79, 3, 181)},
            texts = {"В участке остались патроны и архивы. Дверь открывается снаружи клавишей E.", "Бегуны быстры, но хрупки. Не дай им окружить тебя."},
        },
        oleg = {
            name = "Олег Север", role = "Механик АЗС", at = Vector3.new(274, 3, 143), style = "resident_mechanic", speed = 7.8, ambient = true,
            color = Color3.fromRGB(91, 111, 102), skin = Color3.fromRGB(178, 136, 102), pause = 3.5,
            patrol = {Vector3.new(274, 3, 143), Vector3.new(257, 3, 137), Vector3.new(289, 3, 137), Vector3.new(297, 3, 151)},
            texts = {"Дорога на восток ведёт к карьеру и шахте. Возьми воду до спуска.", "Топливо нужно генераторам, а аккумуляторы — сложным рецептам мастерской."},
        },
        anya = {
            name = "Аня Рэй", role = "Волонтёр эвакуации", at = Vector3.new(-63, 3, -8), style = "resident_volunteer", speed = 8.1, ambient = true,
            color = Color3.fromRGB(108, 125, 93), skin = Color3.fromRGB(205, 166, 133), pause = 3,
            patrol = {Vector3.new(-63, 3, -8), Vector3.new(-86, 3, -12), Vector3.new(-39, 3, -12), Vector3.new(-63, 3, 16)},
            texts = {"Лагерь опустел быстро. Алекс разыскивает список, передатчик и жетоны спасателей.", "Костры ещё горят. Бери ткань и воду — здесь они никому больше не помогут."},
        },
        boris = {
            name = "Борис Камень", role = "Страж северных ворот", at = Vector3.new(18, 3, -34), style = "resident_guard", speed = 8, ambient = true,
            color = Color3.fromRGB(72, 91, 70), skin = Color3.fromRGB(151, 112, 83), pause = 2,
            patrol = {Vector3.new(18, 3, -34), Vector3.new(31, 3, -36), Vector3.new(18, 3, -48), Vector3.new(5, 3, -36)},
            texts = {"Обычный заражённый снимает пять здоровья за удар. Более редкие опаснее — смотри на размер и броню.", "За стенами не стой на месте. Отступай к воротам, если кончается выносливость."},
        },
        vera = {
            name = "Вера Сокол", role = "Агроном убежища", at = Vector3.new(54, 3, -77), style = "resident_farmer", speed = 7.2, ambient = true,
            color = Color3.fromRGB(102, 119, 75), skin = Color3.fromRGB(186, 143, 110), pause = 4,
            patrol = {Vector3.new(54, 3, -77), Vector3.new(66, 3, -83), Vector3.new(62, 3, -67), Vector3.new(47, 3, -68)},
            texts = {"Ферма производит еду только с назначенным жителем и водой на складе.", "Работники теперь гуляют по участку и обходят всё, что ты построишь."},
        },
        timur = {
            name = "Тимур Ветер", role = "Разведчик Меридиана", at = Vector3.new(16, 3, 53), style = "resident_scout", speed = 10, ambient = true,
            color = Color3.fromRGB(77, 91, 87), skin = Color3.fromRGB(173, 126, 92), pause = 1.8,
            patrol = {Vector3.new(16, 3, 53), Vector3.new(35, 3, 48), Vector3.new(23, 3, 72), Vector3.new(-8, 3, 67)},
            texts = {"Город стал больше. Аптека на западе, полиция на востоке, шахта — дальше за АЗС.", "Бронированных заражённых лучше бить дробовиком или улучшенным оружием."},
        },
        mila = {
            name = "Мила Яр", role = "Повар убежища", at = Vector3.new(8, 3, -93), style = "resident_cook", speed = 7.6, ambient = true,
            color = Color3.fromRGB(126, 91, 75), skin = Color3.fromRGB(213, 171, 136), pause = 4.5,
            patrol = {Vector3.new(8, 3, -93), Vector3.new(20, 3, -101), Vector3.new(7, 3, -116), Vector3.new(-8, 3, -105)},
            texts = {"Рацион сытнее консервов, а энергетик быстро возвращает выносливость.", "Не продавай последние воду и еду: без них жители базы перестанут работать."},
        },
        makar = {
            name = "Майор Макар", role = "Командир гарнизона НОВОГО РУБЕЖА", at = Vector3.new(888, 3, 72), style = "major", speed = 8.2,
            color = Color3.fromRGB(61, 82, 64), skin = Color3.fromRGB(173, 132, 99), pause = 2.2,
            patrol = {Vector3.new(888, 3, 72), Vector3.new(905, 3, 71), Vector3.new(905, 3, 87), Vector3.new(884, 3, 88)},
            texts = {"НОВЫЙ РУБЕЖ держится, но восточный патруль отрезан. Зачисти заражённых — и два бойца перейдут под твоё командование.", "Рада и Глеб носят военную броню и работают только по серверным приказам: сопровождение, охрана базы или экспедиция."},
        },
        nora = {
            name = "Доктор Нора", role = "Врач второго города", at = Vector3.new(952, 3, 82), style = "resident_medic", speed = 7.3,
            color = Color3.fromRGB(152, 165, 148), skin = Color3.fromRGB(202, 159, 124), pause = 3.8,
            patrol = {Vector3.new(952, 3, 82), Vector3.new(966, 3, 88), Vector3.new(966, 3, 70), Vector3.new(946, 3, 68)},
            texts = {"За 25 Survival Cash я полностью обработаю раны. Контейнеры для полевого госпиталя всё ещё в гражданском квартале.", "Коррозийные плюются на расстоянии. Разрывай дистанцию и держи броню исправной."},
        },
        quartermaster = {
            name = "Квартирмейстер Феликс", role = "Снабжение гарнизона", at = Vector3.new(850, 3, 115), style = "quartermaster", speed = 7.5,
            color = Color3.fromRGB(76, 91, 72), skin = Color3.fromRGB(157, 119, 91), pause = 3,
            patrol = {Vector3.new(850, 3, 115), Vector3.new(862, 3, 114), Vector3.new(861, 3, 126), Vector3.new(846, 3, 126)},
            texts = {"Стальные пластины, бетон и стекло нужны для нового периметра. Могу открыть тот же защищённый склад снабжения.", "Военный генератор даёт 55 кВт. Чертёж получишь только после спасения патруля."},
        },
        pilot = {
            name = "Пилот Яна", role = "Воздушный маршрут", at = Vector3.new(833, 3, 53), style = "pilot", speed = 8,
            color = Color3.fromRGB(65, 78, 73), skin = Color3.fromRGB(190, 145, 111), pause = 2.5, ambient = true,
            patrol = {Vector3.new(833, 3, 53), Vector3.new(820, 3, 50), Vector3.new(817, 3, 66), Vector3.new(835, 3, 68)},
            texts = {"Вертолёт летает только между двумя защищёнными площадками. Подойди к жёлтому маркеру и удерживай E.", "Во время перелёта управление блокируется сервером. После посадки ты снова получишь контроль."},
        },
    }
    Service.Definitions = definitions
    local function actorPrompt(model, objectText, callback)
        local parent = model:FindFirstChild("Head") or model.PrimaryPart
        local prompt = Instance.new("ProximityPrompt")
        prompt.Name = "TalkPrompt"; prompt.ActionText = "Поговорить"; prompt.ObjectText = objectText
        prompt.KeyboardKeyCode = Enum.KeyCode.E; prompt.GamepadKeyCode = Enum.KeyCode.ButtonX
        prompt.MaxActivationDistance = 11; prompt.HoldDuration = 0; prompt.RequiresLineOfSight = false
        prompt.ClickablePrompt = true; prompt.Exclusivity = Enum.ProximityPromptExclusivity.OnePerButton
        prompt:SetAttribute("ServerAuthoritative", true); prompt.Parent = parent
        prompt.Triggered:Connect(callback)
        return prompt
    end
    function Service:Near(player, id)
        local npc = self.NPCs[id]; local root = ctx:Root(player)
        if not npc or not root or not npc.model.PrimaryPart or (npc.model.PrimaryPart.Position - root.Position).Magnitude > ctx.Config.InteractRange then return false end
        -- Head-to-head validation avoids false failures from floors, tables and the actors' own lower bodies.
        local playerHead = player.Character and player.Character:FindFirstChild("Head")
        local npcHead = npc.model:FindFirstChild("Head")
        local origin = playerHead and playerHead.Position or root.Position + Vector3.new(0, 2, 0)
        local target = npcHead and npcHead.Position or npc.model.PrimaryPart.Position + Vector3.new(0, 2, 0)
        return ctx:LineOfSight(origin, target, {player.Character, npc.model})
    end
    function Service:NearBuilder(player)
        local actor = self.Builders[player]; local root = ctx:Root(player)
        if not actor or not root or not actor.model.Parent or (actor.model.PrimaryPart.Position - root.Position).Magnitude > ctx.Config.InteractRange then return false end
        local head = player.Character and player.Character:FindFirstChild("Head")
        local actorHead = actor.model:FindFirstChild("Head")
        return ctx:LineOfSight(head and head.Position or root.Position + Vector3.new(0, 2, 0), actorHead and actorHead.Position or actor.model.PrimaryPart.Position + Vector3.new(0, 2, 0), {player.Character, actor.model})
    end
    function Service:Talk(player, id)
        if not self:Near(player, id) then return end
        local npc = self.NPCs[id]; local def = definitions[id]
        local s = ctx.Sessions[player]; if not s then return end
        if cityTwoNPC[id] and not cityTwoUnlocked(s.Campaign) then
            ctx:Notify(player, "НОВЫЙ РУБЕЖ закрыт до выполнения задания Алекса «ВТОРОЙ РУБЕЖ».", "error")
            return
        end
        npc.talkUntil = os.clock() + 18; npc.humanoid:MoveTo(npc.model.PrimaryPart.Position)
        npc.path = nil; npc.model:SetAttribute("State", "Talking"); npc.talkCount = (npc.talkCount or 0) + 1
        local root = ctx:Root(player); local at = npc.model.PrimaryPart.Position
        if root and (Vector3.new(root.Position.X, at.Y, root.Position.Z) - at).Magnitude > 0.1 then
            npc.model:PivotTo(CFrame.lookAt(at, Vector3.new(root.Position.X, at.Y, root.Position.Z)))
        end
        ctx.Progression.fact(s.Campaign, "talk")
        local text = def.texts[(npc.talkCount - 1) % #def.texts + 1]
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {id = id, name = def.name, role = def.role, text = text, model = npc.model, ambient = def.ambient == true})
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
        local playerHead = player.Character and player.Character:FindFirstChild("Head"); local actorHead = actor.model:FindFirstChild("Head")
        if not ctx:LineOfSight(playerHead and playerHead.Position or root.Position + Vector3.new(0, 2, 0), actorHead and actorHead.Position or actor.model.PrimaryPart.Position + Vector3.new(0, 2, 0), {player.Character, actor.model}) then return end
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
    function Service:TalkBuilder(player)
        local actor = self.Builders[player]; local s = ctx.Sessions[player]
        if not actor or not s or not self:NearBuilder(player) then return end
        actor.talkUntil = os.clock() + 14; actor.model:SetAttribute("State", "Talking"); actor.humanoid:MoveTo(actor.model.PrimaryPart.Position)
        local order = s.Campaign.Builder and ctx.Building.find(s.Campaign, s.Campaign.Builder.order)
        local text
        if order and order.readyAt > os.time() then
            text = "Я строю «" .. ctx.BuildingDefs[order.id].name .. "». Осталось примерно " .. math.max(0, order.readyAt - os.time()) .. " секунд."
        else
            text = "Принеси материалы в рюкзаке или положи их на склад. Выбери проект — я сам найду свободное место, оплачу его из твоих запасов и пойду строить."
        end
        ctx.Remotes.StateRemote:FireClient(player, "dialogue", {
            id = "builder", name = s.Campaign.Builder.name, role = "Строитель участка №" .. s.Plot,
            text = text, model = actor.model, builder = true,
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
            local actor = {model = model, humanoid = h, nextMove = 0, path = nil, pathIndex = 1, index = index, talkUntil = 0,
                forcePath = true, nextWander = 0, nextGesture = os.clock() + 8 + index * 3, gestureUntil = 0}
            actorPrompt(model, worker.name, function(visitor)
                if visitor ~= player then ctx:Notify(visitor, "Это житель другого участка."); return end
                if ctx.Services.NetworkService:Permit(visitor, "dialogue", true) then self:TalkWorker(visitor, actor, worker, s.Plot) end
            end)
            table.insert(self.Workers[player], actor)
        end
        local builder = s.Campaign.Builder
        local model, h = ctx.Factory.rig(builder.name, CFrame.new(plot.x + 30, 3, plot.z + 30), Color3.fromRGB(92, 105, 108), Color3.fromRGB(184, 139, 103), 1, "builder")
        model.Name = "Builder_" .. player.UserId; model:SetAttribute("Owner", player.UserId)
        model:SetAttribute("AnimationSeed", (player.UserId + 71) % 97); model:SetAttribute("WorkState", "Idle")
        model.Parent = ctx.World.NPCSpawns; model.PrimaryPart:SetNetworkOwner(nil); CollectionService:AddTag(model, "LastCityActor")
        h.MaxHealth = 140; h.Health = 140; h.WalkSpeed = 8.4
        label(model, builder.name, "Строитель вашей базы • E")
        local actor = {model = model, humanoid = h, nextMove = 0, path = nil, pathIndex = 1, talkUntil = 0,
            forcePath = true, nextWander = 0, nextGesture = os.clock() + 11, gestureUntil = 0}
        actorPrompt(model, builder.name, function(visitor)
            if visitor ~= player then ctx:Notify(visitor, "Это строитель другого участка."); return end
            if ctx.Services.NetworkService:Permit(visitor, "dialogue", true) then self:TalkBuilder(visitor) end
        end)
        self.Builders[player] = actor
    end
    function Service:RemovePlayer(player)
        for _, worker in self.Workers[player] or {} do worker.model:Destroy() end
        local builder = self.Builders[player]; if builder then builder.model:Destroy() end
        self.Workers[player] = nil; self.Builders[player] = nil
    end
    function Service:PointClear(s, x, z, padding, excludeUid)
        padding = padding or 2
        if math.abs(x) > ctx.Config.PlotHalfSize - padding or math.abs(z) > ctx.Config.PlotHalfSize - padding then return false end
        for _, zone in ctx.Config.PlotReserved do
            if math.abs(x - zone.x) < zone.hx + padding and math.abs(z - zone.z) < zone.hz + padding then return false end
        end
        for _, b in s.Campaign.Buildings do
            if b.uid == excludeUid then continue end
            local hx, hz = ctx.Building.extents(b.id, b.r)
            if math.abs(x - b.x) < hx + padding and math.abs(z - b.z) < hz + padding then return false end
        end
        return true
    end
    function Service:WanderTarget(s, actor)
        local plot = ctx.Config.Plots[s.Plot]
        for _ = 1, 16 do
            local x, z = math.random(-10, 10) * 4, math.random(-10, 8) * 4
            if self:PointClear(s, x, z, 3) then return Vector3.new(plot.x + x, 3, plot.z + z) end
        end
        for _, point in {{32, 8}, {-36, 5}, {30, -34}, {-34, -34}, {0, -40}} do
            if self:PointClear(s, point[1], point[2], 3) then return Vector3.new(plot.x + point[1], 3, plot.z + point[2]) end
        end
        -- The reserved entrance is guaranteed to remain construction-free.
        return Vector3.new(plot.x, 3, plot.z + 24)
    end
    function Service:WorkTarget(s, building)
        local plot = ctx.Config.Plots[s.Plot]; local hx, hz = ctx.Building.extents(building.id, building.r)
        for _, point in {{building.x, building.z + hz + 4}, {building.x + hx + 4, building.z}, {building.x, building.z - hz - 4}, {building.x - hx - 4, building.z}} do
            if self:PointClear(s, point[1], point[2], 2, building.uid) then return Vector3.new(plot.x + point[1], 3, plot.z + point[2]) end
        end
        return self:WanderTarget(s, {})
    end
    function Service:Evacuate(player, building)
        local s = ctx.Sessions[player]; local workers = self.Workers[player]
        if not s or not workers then return end
        local plot = ctx.Config.Plots[s.Plot]; local hx, hz = ctx.Building.extents(building.id, building.r)
        local function move(actor)
            if not actor or not actor.model.Parent then return end
            local position = actor.model.PrimaryPart.Position
            if math.abs(position.X - (plot.x + building.x)) < hx + 2.5 and math.abs(position.Z - (plot.z + building.z)) < hz + 2.5 then
                local safe = self:WanderTarget(s, actor)
                actor.humanoid:MoveTo(safe); actor.model:PivotTo(CFrame.new(safe)); actor.path = nil
                actor.pathToken = (actor.pathToken or 0) + 1; actor.pathTarget = nil; actor.nextWander = 0
                actor.model:SetAttribute("State", "Walking")
            end
        end
        for _, actor in workers do move(actor) end
        move(self.Builders[player])
        if ctx.Services.SquadService and ctx.Services.SquadService.Evacuate then ctx.Services.SquadService:Evacuate(player, building) end
    end
    function Service:Walk(actor, target)
        if not actor.model.Parent then return end
        local root = actor.model.PrimaryPart
        local horizontalTarget = Vector3.new(target.X, root.Position.Y, target.Z)
        if not actor.pathTarget or (actor.pathTarget - horizontalTarget).Magnitude > 2 then
            actor.pathTarget = horizontalTarget; actor.path = nil; actor.pathToken = (actor.pathToken or 0) + 1
        end
        if (horizontalTarget - root.Position).Magnitude < 1.8 then
            actor.humanoid:MoveTo(root.Position); actor.path = nil; return
        end
        if not actor.forcePath and ctx:LineOfSight(root.Position + Vector3.new(0, 1, 0), horizontalTarget + Vector3.new(0, 1, 0), {actor.model}) then
            actor.path = nil; actor.humanoid:MoveTo(target); return
        end
        if actor.path then
            local waypoint = actor.path[actor.pathIndex]
            if waypoint and (root.Position - waypoint.Position).Magnitude < 4 then actor.pathIndex += 1; waypoint = actor.path[actor.pathIndex] end
            if waypoint then
                actor.humanoid:MoveTo(waypoint.Position); return
            else actor.path = nil end
        end
        if actor.pending or os.clock() < actor.nextMove or self.PendingPaths >= 2 then return end
        actor.nextMove = os.clock() + 2.5 + math.random(); actor.pending = true; self.PendingPaths += 1
        local token = actor.pathToken
        task.spawn(function()
            -- Residents/builders must route around player walls, never solve a route by jumping over them.
            local path = Pathfinding:CreatePath({AgentRadius = 2, AgentHeight = 6, AgentCanJump = false, WaypointSpacing = 5})
            local ok = pcall(function() path:ComputeAsync(root.Position, target) end)
            self.PendingPaths -= 1; actor.pending = false
            if not actor.model.Parent or actor.pathToken ~= token then return end
            if ok and path.Status == Enum.PathStatus.Success then actor.path = path:GetWaypoints(); actor.pathIndex = 2
            elseif actor.forcePath then actor.path = nil; actor.humanoid:MoveTo(root.Position); actor.nextWander = 0
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
            local actor = {model = model, humanoid = h, at = def.at, talkUntil = 0, nextMove = 0, pathIndex = 1, patrolIndex = 1,
                pauseUntil = 0, talkCount = 0, nextGesture = os.clock() + 4 + order % 7, gestureUntil = 0}
            self.NPCs[id] = actor
            actorPrompt(model, def.name, function(player)
                if ctx.Services.NetworkService:Permit(player, "dialogue", true) then self:Talk(player, id) end
            end)
        end
        ctx.Services.NetworkService:Register("NPCRemote", true, function(player, s, p)
            if p.action == "talk" then self:Talk(player, p.id)
            elseif p.action == "builder" then
                if not self:NearBuilder(player) then ctx:Notify(player, "Подойдите к строителю Степану.", "error"); return end
                local ok, result = ctx.Services.BuildingService:AutoPlace(player, p.id)
                if not ok then ctx:Notify(player, result or "Строитель не может начать проект", "error") end
            elseif p.action == "squad" then
                if ctx.Services.SquadService then ctx.Services.SquadService:Command(player, p.command, p.resource) end
            elseif p.action == "heal" then
                if not cityTwoUnlocked(s.Campaign) then ctx:Notify(player, "Сначала откройте маршрут в НОВЫЙ РУБЕЖ у Алекса.", "error"); return end
                if not self:Near(player, "nora") then ctx:Notify(player, "Подойдите к доктору Норе.", "error"); return end
                local humanoid = ctx:Alive(player)
                if not humanoid or humanoid.Health >= humanoid.MaxHealth then ctx:Notify(player, "Лечение сейчас не требуется."); return end
                if s.Campaign.Cash < 25 then ctx:Notify(player, "Для лечения нужно 25 Survival Cash.", "error"); return end
                s.Campaign.Cash -= 25; humanoid.Health = humanoid.MaxHealth
                ctx:Notify(player, "Нора обработала раны. Здоровье восстановлено.", "success"); ctx:Changed(player)
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
                        local walking = npc.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1
                        if not walking and now >= npc.nextGesture then
                            npc.gestureUntil = now + 2.3; npc.nextGesture = now + 17 + (npc.model:GetAttribute("AnimationSeed") % 9)
                        end
                        if walking then npc.model:SetAttribute("State", "Walking")
                        elseif now < npc.gestureUntil then npc.model:SetAttribute("State", id == "makar" and "Salute" or "HeadScratch")
                        else npc.model:SetAttribute("State", "Idle") end
                    end
                end
                for player, actors in self.Workers do
                    local s = ctx.Sessions[player]; if not s or not s.Loaded or s.Closing then continue end
                    local plot = ctx.Config.Plots[s.Plot]
                    for index, actor in actors do
                        local worker = s.Campaign.Workers[index]
                        local building = ctx.Building.find(s.Campaign, worker.job)
                        local state = "Idle"
                        if not actor.wanderTarget or now >= actor.nextWander then
                            actor.wanderTarget = self:WanderTarget(s, actor); actor.nextWander = now + math.random(7, 14)
                        end
                        local target = actor.wanderTarget
                        if hour >= 21 or hour < 6 then
                            state = "Sleeping"; target = Vector3.new(plot.x - 24 + index * 2, 3, plot.z + 30)
                        elseif hour >= 12 and hour < 13 then
                            state = "Eating"; target = Vector3.new(plot.x - 8 + index * 4, 3, plot.z + 22)
                        elseif building and building.readyAt <= os.time() then
                            target = self:WorkTarget(s, building); state = "Working"
                        end
                        if ctx.Services.ZombieService:Nearest(actor.model.PrimaryPart.Position, 24) then
                            target = Vector3.new(plot.x, 3, plot.z + 24); state = "Fleeing"
                        end
                        if now <= actor.talkUntil and state ~= "Fleeing" then
                            actor.humanoid:MoveTo(actor.model.PrimaryPart.Position); actor.model:SetAttribute("State", "Talking")
                        else
                            self:Walk(actor, target)
                            local atTarget = (Vector3.new(target.X, actor.model.PrimaryPart.Position.Y, target.Z) - actor.model.PrimaryPart.Position).Magnitude < 8
                            worker.atWork = state == "Working" and atTarget
                            local walking = actor.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1
                            if not walking and state == "Idle" and now >= actor.nextGesture then
                                actor.gestureUntil = now + 2.3; actor.nextGesture = now + 19 + index * 3
                            end
                            actor.model:SetAttribute("State", walking and (state == "Fleeing" and "Fleeing" or "Walking")
                                or (state == "Idle" and now < actor.gestureUntil and "HeadScratch" or state))
                        end
                        actor.model:SetAttribute("WorkState", state)
                    end
                    local builder = self.Builders[player]
                    if builder and builder.model.Parent then
                        local order = s.Campaign.Builder and ctx.Building.find(s.Campaign, s.Campaign.Builder.order)
                        local state, target = "Idle", builder.wanderTarget
                        if order and order.readyAt > os.time() then
                            state = "Building"; target = self:WorkTarget(s, order)
                        else
                            if s.Campaign.Builder then s.Campaign.Builder.order = "" end
                            if not target or now >= builder.nextWander then
                                builder.wanderTarget = self:WanderTarget(s, builder); builder.nextWander = now + math.random(8, 15); target = builder.wanderTarget
                            end
                        end
                        if ctx.Services.ZombieService:Nearest(builder.model.PrimaryPart.Position, 24) then
                            state = "Fleeing"; target = Vector3.new(plot.x, 3, plot.z + 24)
                        end
                        if now <= builder.talkUntil and state ~= "Fleeing" then
                            builder.humanoid:MoveTo(builder.model.PrimaryPart.Position); builder.model:SetAttribute("State", "Talking")
                        else
                            self:Walk(builder, target or Vector3.new(plot.x + 30, 3, plot.z + 30))
                            local walking = builder.model.PrimaryPart.AssemblyLinearVelocity.Magnitude > 1
                            if not walking and state == "Idle" and now >= builder.nextGesture then
                                builder.gestureUntil = now + 2.3; builder.nextGesture = now + 21
                            end
                            builder.model:SetAttribute("State", walking and (state == "Fleeing" and "Fleeing" or "Walking")
                                or (state == "Idle" and now < builder.gestureUntil and "HeadScratch" or state))
                        end
                        builder.model:SetAttribute("WorkState", state)
                    end
                end
            end
        end)
    end
    return Service
end
