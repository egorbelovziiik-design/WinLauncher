--!nonstrict
local View = {}
local statuses = {Available = "ДОСТУПНО У NPC", Active = "АКТИВНО", Completed = "ВЕРНИТЕСЬ ЗА НАГРАДОЙ", RewardClaimed = "ЗАВЕРШЕНО", Failed = "ПРОВАЛЕНО"}
function View.reward(app, def)
    local items = app.UI.price(def.reward.items, app.Items)
    for _, loaded in def.reward.loadedWeapons or {} do
        items ..= (items == "" and "" or "  ·  ") .. app.Items[loaded.id].name .. " " .. loaded.ammo .. "/" .. app.Weapons[loaded.id].magazine
    end
    return def.reward.coins .. " Coins  /  " .. def.reward.xp .. " XP\n" .. items
end
function View.card(app, body, id, dialogue)
    local UI = app.UI; local T = UI.Theme; local def = app.Quests[id]; local state = app.State.campaign.Quests[id]
    local status = state and state.status or "Available"
    local locked = def.requires and (not app.State.campaign.Quests[def.requires] or app.State.campaign.Quests[def.requires].status ~= "RewardClaimed")
    local height = 174 + #def.objectives * 24
    local card = UI.card(body, height)
    UI.label(card, (locked and "НЕДОСТУПНО" or statuses[status]) .. "  /  " .. def.type, 10, {Position = UDim2.fromOffset(14, 10), Size = UDim2.new(1, -28, 0, 20), TextColor3 = status == "Completed" and T.success or T.accent})
    UI.label(card, def.name, 16, {Position = UDim2.fromOffset(14, 36), Size = UDim2.new(1, -28, 0, 25), Font = Enum.Font.GothamBold})
    UI.label(card, def.location, 11, {Position = UDim2.fromOffset(14, 65), Size = UDim2.new(1, -28, 0, 20), TextColor3 = T.muted})
    for index, objective in def.objectives do
        local progress = state and state.progress[index] or 0
        UI.label(card, (progress >= objective.count and "✓  " or "–  ") .. objective.label .. "    " .. progress .. "/" .. objective.count, 12,
            {Position = UDim2.fromOffset(14, 89 + (index - 1) * 24), Size = UDim2.new(1, -28, 0, 22), TextColor3 = progress >= objective.count and T.success or T.text})
    end
    local offset = 89 + #def.objectives * 24
    UI.label(card, View.reward(app, def), 11, {Position = UDim2.fromOffset(14, offset), Size = UDim2.new(1, -28, 0, 38), TextColor3 = T.muted, TextWrapped = true})
    local row = UI.row(card, 30); row.Position = UDim2.fromOffset(14, height - 40); row.Size = UDim2.new(1, -28, 0, 30)
    if dialogue and not locked and status == "Available" then
        UI.button(row, "ПРИНЯТЬ", function() app:Send("QuestRemote", {action = "accept", id = id}) end, {Size = UDim2.new(0.32, -5, 1, 0), BackgroundColor3 = T.accent, TextSize = 11})
    elseif dialogue and status == "Completed" then
        UI.button(row, "НАГРАДА", function() app:Send("QuestRemote", {action = "claim", id = id}) end, {Size = UDim2.new(0.32, -5, 1, 0), BackgroundColor3 = T.success, TextColor3 = T.background, TextSize = 11})
    elseif not locked and status ~= "RewardClaimed" then
        UI.button(row, "Отслеживать", function() app.TrackedQuest = id; app:RefreshMiniMap(); app:Toast("Цель отмечена в HUD и на карте"); app:UpdateHUD() end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11})
    end
    UI.button(row, "Подробнее", function()
        if dialogue then app.DialogueText = def.description; app:RefreshPanel(true)
        else app:Toast(def.description) end
    end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11})
    if dialogue then UI.button(row, "Отказаться", function() app:ClosePanel() end, {Size = UDim2.new(0.32, -5, 1, 0), TextSize = 11}) end
end
function View.page(app, body)
    local UI = app.UI
    UI.paragraph(body, "Задания принимаются и сдаются лично у NPC. Прогресс начисляет сервер. Ежедневное дежурство обновляется в 00:00 UTC.", 42)
    for _, id in app:Sorted(app.Quests) do View.card(app, body, id, false) end
end
function View.dialogue(app, body)
    local UI = app.UI; local T = UI.Theme; local d = app.Dialogue
    if not d then return end
    local compact = app.Width < 700
    local intro = UI.card(body, compact and 252 or 178)
    local portrait = UI.new("ViewportFrame", {Size = UDim2.fromOffset(96, 130), Position = UDim2.fromOffset(12, 15), BackgroundColor3 = T.raised, BorderSizePixel = 0, Ambient = Color3.fromRGB(200, 200, 200), LightColor = Color3.fromRGB(255, 226, 189)}, intro); UI.corner(portrait)
    if d.model and d.model.Parent then
        local model = d.model:Clone()
        for _, child in model:GetDescendants() do
            if child:IsA("Script") or child:IsA("LocalScript") or child:IsA("BillboardGui") or child:IsA("ProximityPrompt") then child:Destroy()
            elseif child:IsA("BasePart") then child.Anchored = true end
        end
        local world = UI.new("WorldModel", {}, portrait); model.Parent = world
        local camera = UI.new("Camera", {}, portrait); portrait.CurrentCamera = camera
        local head = model:FindFirstChild("Head")
        if head then camera.CFrame = CFrame.lookAt(head.Position + Vector3.new(1, 0.1, -4.8), head.Position + Vector3.new(0, -0.35, 0)); camera.FieldOfView = 42 end
    end
    UI.label(intro, d.name, 17, {Position = UDim2.fromOffset(122, 13), Size = UDim2.new(1, -135, 0, 25), Font = Enum.Font.GothamBold})
    UI.label(intro, d.role, 11, {Position = UDim2.fromOffset(122, 43), Size = UDim2.new(1, -135, 0, 20), TextColor3 = T.accent})
    local text = app.DialogueText or d.text
    local label = UI.label(intro, text, 13, {Position = UDim2.fromOffset(122, 72), Size = UDim2.new(1, -135, 0, compact and 164 or 92), TextWrapped = true, TextYAlignment = Enum.TextYAlignment.Top, TextColor3 = T.muted})
    if app.LastDialogueText ~= text then
        app.LastDialogueText = text; label.MaxVisibleGraphemes = 0
        task.spawn(function()
            for i = 1, utf8.len(text) or #text do
                if not label.Parent then return end
                label.MaxVisibleGraphemes = i; task.wait(0.012)
            end
        end)
    end
    local row = UI.row(body, 36)
    if d.id == "worker" then
        UI.button(row, "Как идёт работа?", function() app.DialogueText = "Я действительно хожу к назначенной постройке. Производство работает только когда смена активна, хватает припасов и есть нужная энергия."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
        UI.button(row, "Как назначить смену?", function() app.DialogueText = "Открой терминал своего участка клавишей E, затем управление базой. Назначить жителя можно только на производственное здание с рабочим местом."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
    elseif d.id == "builder" then
        UI.button(row, "Какие ресурсы нужны?", function() app.DialogueText = "Цена проекта показана в списке. Я использую сначала склад, затем рюкзак. Если свободного места или материалов нет, сервер ничего не спишет."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
        UI.button(row, "Как выбирается место?", function() app.DialogueText = "Я проверяю ту же сетку, границы, проход к терминалу и пересечения, что ручное строительство. Жители освобождают footprint до появления модели."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
    elseif d.id == "squad" then
        UI.button(row, "Боеприпасы", function() app.DialogueText = "Каждый выстрел списывает один карабинный патрон со склада. Без патронов мы продолжим следовать приказу, но стрелять не будем."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
        UI.button(row, "Экспедиция", function() app.DialogueText = "Экспедиция занимает 90 секунд и сохраняется. Радиомачта с энергией увеличит добычу на 50 процентов."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
    elseif d.ambient then
        UI.button(row, "Ещё совет", function() app.DialogueText = nil; app:Send("NPCRemote", {action = "talk", id = d.id}) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
        UI.button(row, "Спасибо", function() app:ClosePanel() end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
    else
        UI.button(row, "Я помогу", function() app.DialogueText = "Спасибо. Выбери задание ниже — и возвращайся живым."; app:RefreshPanel(true) end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
        UI.button(row, "Что я получу?", function()
            app.DialogueText = "Coins — в постоянный профиль. XP, оружие и припасы — в эту кампанию. Забрать награду можно только один раз после выполнения целей."; app:RefreshPanel(true)
        end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 12})
    end
    if d.id == "lada" then UI.button(body, "Нанять специалистов · Survival Cash", function() app:OpenPanel("recruits") end, {BackgroundColor3 = T.accent})
    elseif d.id == "max" or d.id == "quartermaster" then UI.button(body, "Открыть торговлю · Survival Cash", function() app:OpenPanel("trade") end, {BackgroundColor3 = T.accent})
    elseif d.id == "victor" then UI.button(body, "Ремонт и улучшения оружия", function() app:OpenPanel("weapons") end, {BackgroundColor3 = T.accent})
    elseif d.id == "builder" then UI.button(body, "Выбрать настоящий строительный заказ", function() app:OpenPanel("builder") end, {BackgroundColor3 = T.accent})
    elseif d.id == "squad" then UI.button(body, "Открыть команды отряду", function() app:OpenPanel("squad") end, {BackgroundColor3 = T.accent})
    elseif d.id == "nora" then UI.button(body, "Полное лечение · 25 Survival Cash", function() app:Send("NPCRemote", {action = "heal"}) end, {BackgroundColor3 = T.success, TextColor3 = T.background}) end
    for _, id in app:Sorted(app.Quests) do if app.Quests[id].npc == d.id then View.card(app, body, id, true) end end
    UI.button(body, "Это слишком опасно. До встречи.", function() app:ClosePanel() end)
end
function View.tutorial(app, body)
    local UI = app.UI; local c = app.State.campaign
    UI.heading(body, "ДОБРО ПОЖАЛОВАТЬ В DEAD WORLD!")
    UI.paragraph(body, "Обучение проходит в настоящем мире, а не в меню. Первая награда: 500 Coins, 100 XP и набор выжившего. Повторное прохождение не выдаёт награду ещё раз.", 62)
    local row = UI.row(body, 40)
    UI.button(row, c.Tutorial.status == "New" and "ПРОЙТИ ОБУЧЕНИЕ" or "ПРОЙТИ ЗАНОВО", function()
        if app.State.active then app:Send("SessionRemote", {action = "tutorial", choice = "start"}); app:ClosePanel()
        else app.StartTutorialOnPlay = true; app:Send("SessionRemote", {action = "play"}) end
    end, {Size = UDim2.new(0.5, -5, 1, 0), BackgroundColor3 = UI.Theme.accent, TextSize = 11})
    UI.button(row, "ПРОПУСТИТЬ", function()
        if app.State.active then app:Send("SessionRemote", {action = "tutorial", choice = "skip"}); app:ClosePanel()
        else app.SkipTutorialOnPlay = true; app:Send("SessionRemote", {action = "play"}) end
    end, {Size = UDim2.new(0.5, -5, 1, 0), TextSize = 11})
    for index, step in app.Config.TutorialSteps do
        local done = c.Tutorial.facts[step.key]
        UI.paragraph(body, (done and "✓ " or string.format("%02d ", index)) .. step.text, 35, done and UI.Theme.success or UI.Theme.muted)
    end
    if app.State.stage > #app.Config.TutorialSteps and c.Tutorial.status == "Active" then
        UI.button(body, app.State.profile.TutorialRewarded and "ЗАВЕРШИТЬ ПОВТОР" or "ЗАБРАТЬ НАГРАДУ В УБЕЖИЩЕ", function() app:Send("SessionRemote", {action = "tutorialReward"}) end, {BackgroundColor3 = UI.Theme.accent})
    end
end
return View
