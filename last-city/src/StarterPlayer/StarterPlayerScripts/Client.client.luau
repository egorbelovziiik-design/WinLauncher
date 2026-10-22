--!nonstrict
local player = game:GetService("Players").LocalPlayer
local app
player:SetAttribute("LastCityClientPhase", "Interface")
local function boot()
    local Players = game:GetService("Players")
    local ReplicatedStorage = game:GetService("ReplicatedStorage")
    local RunService = game:GetService("RunService")
    local Input = game:GetService("UserInputService")
    local GuiService = game:GetService("GuiService")
    local Lighting = game:GetService("Lighting")
    local TweenService = game:GetService("TweenService")
    local StarterGui = game:GetService("StarterGui")
    local Shared = ReplicatedStorage:WaitForChild("Shared")
    local Views = script.Parent:WaitForChild("Views")
    app = {
        Player = Players.LocalPlayer, Remotes = ReplicatedStorage:WaitForChild("Remotes"),
        UI = require(Views.UI), Config = require(Shared.GameConfig), Items = require(Shared.ItemDefinitions),
        Weapons = require(Shared.WeaponDefinitions), Quests = require(Shared.QuestDefinitions),
        Buildings = require(Shared.BuildingDefinitions), Economy = require(Shared.EconomyConfig),
        Factory = require(Shared.ModelFactory), Map = require(Views.MapView), Width = 1280, Height = 720,
        Notices = {}, InventoryFilter = "Все",
    }
    local UI = app.UI; local T = UI.Theme
    app.Gui = UI.new("ScreenGui", {Name = "LastCity", ResetOnSpawn = false, IgnoreGuiInset = false, ZIndexBehavior = Enum.ZIndexBehavior.Sibling, DisplayOrder = 10}, app.Player:WaitForChild("PlayerGui"))
    app.Root = UI.frame(app.Gui, {Name = "ResponsiveCanvas", Size = UDim2.fromScale(1, 1), BackgroundTransparency = 1})
    app.Scale = UI.new("UIScale", {Scale = 1}, app.Root)
    local sound = UI.new("Sound", {Name = "InterfaceSound", SoundId = app.Config.Audio.UI, Volume = 0.2}, game:GetService("SoundService"))
    UI.Sound = function(multiplier)
        sound.Volume = (app.State and app.State.profile.Settings.uiSound or 0.45) * multiplier * 0.3
        sound.TimePosition = 0; sound:Play()
    end
    function app:Send(name, payload) self.Remotes[name]:FireServer(payload) end
    function app:Sorted(dictionary)
        local ids = {}; for id in dictionary do table.insert(ids, id) end
        table.sort(ids, function(a, b)
            local oa, ob = dictionary[a].order or 100, dictionary[b].order or 100
            return oa == ob and a < b or oa < ob
        end)
        return ids
    end
    function app:Count(id)
        local n = 0
        for _, entry in self.State and self.State.campaign.Inventory or {} do if entry.id == id then n += entry.count end end
        return n
    end
    function app:Equipped()
        if not self.State then return nil end
        for _, entry in self.State.campaign.Inventory do if entry.uid == self.State.campaign.Equipped then return entry end end
        return nil
    end
    function app:Level() return 1 + math.floor(math.sqrt(math.max(0, self.State.campaign.XP) / 100)) end
    function app:FindBuilding(uid)
        for _, b in self.State.campaign.Buildings do if b.uid == uid then return b end end
        return nil
    end
    function app:QuickSlot(index)
        local id = index == 1 and "crowbar" or index == 2 and "pistol" or "medkit"
        for _, e in self.State and self.State.campaign.Inventory or {} do
            if e.id == id then self:Send("InventoryRemote", {action = index == 3 and "use" or "equip", uid = e.uid}); return end
        end
        self:Toast("В рюкзаке нет: " .. self.Items[id].name)
    end
    function app:SaveText()
        if not self.State then return "ЗАГРУЗКА" end
        local state = self.State.save
        if state == "SessionOnly" then return "ТОЛЬКО СЕССИЯ • Studio, без облачного сохранения" end
        if state == "Error" then return "ОШИБКА СОХРАНЕНИЯ • повторная попытка" end
        if state == "Saving" then return "СОХРАНЯЕМ…" end
        return "СОХРАНЕНО " .. os.date("!%H:%M:%S", self.State.lastSave) .. " UTC"
    end
    local noticeRoot = UI.frame(app.Root, {Name = "Notifications", AnchorPoint = Vector2.new(0.5, 0), Position = UDim2.new(0.5, 0, 0, 81), Size = UDim2.fromOffset(390, 250), BackgroundTransparency = 1, ZIndex = 40})
    UI.list(noticeRoot, 6)
    function app:Toast(text, kind)
        if #self.Notices >= 3 then local old = table.remove(self.Notices, 1); if old.Parent then old:Destroy() end end
        local frame = UI.card(noticeRoot, #text > 85 and 82 or 52); frame.ZIndex = 40; frame.BackgroundColor3 = T.background; frame.BackgroundTransparency = 0.08
        UI.stroke(frame, kind == "error" and T.danger or kind == "success" and T.success or T.accent, 0.25)
        UI.label(frame, text, 12, {Position = UDim2.fromOffset(14, 6), Size = UDim2.new(1, -28, 1, -12), TextWrapped = true, ZIndex = 41})
        table.insert(self.Notices, frame)
        task.delay(#text > 85 and 8 or 4, function()
            local index = table.find(self.Notices, frame); if index then table.remove(self.Notices, index) end
            if frame.Parent then frame:Destroy() end
        end)
    end
    app.Menu = require(Views.MenuView)(app)
    app.HUD = require(Views.HUDView)(app)
    local transition = UI.frame(app.Root, {Name = "Transition", Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 1, ZIndex = 60, Visible = false})
    local scrim = UI.frame(app.Root, {Name = "PanelOverlay", Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.35, Visible = false, ZIndex = 20})
    app.Shell = UI.frame(scrim, {AnchorPoint = Vector2.new(0.5, 0.5), Position = UDim2.fromScale(0.5, 0.5), Size = UDim2.fromOffset(900, 650), BackgroundColor3 = T.background, ZIndex = 21}); UI.corner(app.Shell, 10); UI.stroke(app.Shell, T.line, 0.1)
    UI.frame(app.Shell, {Size = UDim2.new(1, 0, 0, 3), BackgroundColor3 = T.accent, ZIndex = 22})
    app.PanelTitle = UI.label(app.Shell, "", 25, {Position = UDim2.fromOffset(22, 18), Size = UDim2.new(1, -85, 0, 35), Font = Enum.Font.GothamBlack, ZIndex = 22})
    UI.label(app.Shell, "LAST CITY  /  MERIDIAN NETWORK", 9, {Position = UDim2.fromOffset(24, 55), Size = UDim2.new(1, -60, 0, 18), TextColor3 = T.muted, ZIndex = 22})
    UI.button(app.Shell, "×", function() app:ClosePanel() end, {AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -16, 0, 16), Size = UDim2.fromOffset(36, 36), TextSize = 22, ZIndex = 22})
    app.Body = UI.new("ScrollingFrame", {Name = "PanelBody", Position = UDim2.fromOffset(22, 84), Size = UDim2.new(1, -44, 1, -102), BackgroundTransparency = 1, BorderSizePixel = 0,
        CanvasSize = UDim2.new(), AutomaticCanvasSize = Enum.AutomaticSize.Y, ScrollBarThickness = 3, ScrollBarImageColor3 = T.accent, ZIndex = 22}, app.Shell)
    UI.list(app.Body, 9)
    local QuestView = require(Views.QuestView); local BaseView = require(Views.BaseView); local ShopView = require(Views.ShopView); local ProfileView = require(Views.ProfileView)
    local panels = {
        inventory = {"РЮКЗАК", require(Views.InventoryView)}, quests = {"ЖУРНАЛ ЗАДАНИЙ", QuestView.page}, dialogue = {"РАДИ СЛЕДУЮЩЕГО ДНЯ", QuestView.dialogue},
        tutorial = {"ПЕРВЫЙ ДЕНЬ", QuestView.tutorial}, build = {"СТРОИТЕЛЬСТВО", BaseView.build}, base = {"УПРАВЛЕНИЕ БАЗОЙ", BaseView.base}, craft = {"МАСТЕРСКАЯ", BaseView.craft},
        cosmetics = {"МАГАЗИН / COINS", ShopView.cosmetics}, trade = {"ТОРГОВЕЦ МАКС", ShopView.trade}, weapons = {"ОРУЖЕЙНАЯ", ShopView.weapons},
        profile = {"ПРОФИЛЬ", ProfileView.profile}, achievements = {"ДОСТИЖЕНИЯ", ProfileView.achievements}, settings = {"НАСТРОЙКИ", ProfileView.settings},
        updates = {"ЖУРНАЛ ОБНОВЛЕНИЙ", ProfileView.updates}, map = {"КАРТА РАЙОНА", app.Map.page},
    }
    function app:RefreshPanel(reset)
        if not self.PanelName or not self.State then return end
        local scroll = self.Body.CanvasPosition
        UI.clear(self.Body); self.MapDot = nil
        panels[self.PanelName][2](self, self.Body)
        -- Descendants of the modal stay above the HUD, regardless of default ZIndex.
        for _, child in self.Body:GetDescendants() do if child:IsA("GuiObject") then child.ZIndex = 23 end end
        self.Body.CanvasPosition = reset and Vector2.zero or scroll
    end
    function app:OpenPanel(name)
        if not self.State or not panels[name] then return end
        if self.Build then self.Build:Cancel() end
        if name ~= "dialogue" and self.Camera then self.Camera:SetDialogue(nil) end
        self.PanelName = name; self.PanelTitle.Text = panels[name][1]; scrim.Visible = true
        self:RefreshPanel(true)
    end
    function app:ClosePanel()
        self.PanelName = nil; scrim.Visible = false; UI.clear(self.Body); self.MapDot = nil
        self.Dialogue = nil; self.DialogueText = nil; self.LastDialogueText = nil
        if self.Camera then self.Camera:SetDialogue(nil) end
    end
    function app:Layout()
        local camera = workspace.CurrentCamera; if not camera then return end
        local viewport = camera.ViewportSize; local inset = GuiService:GetGuiInset()
        local value = math.clamp(viewport.X / 1280, 0.8, 1.25) * (self.State and self.State.profile.Settings.scale or 1)
        self.Scale.Scale = value; self.Root.Size = UDim2.fromScale(1 / value, 1 / value)
        self.Width = viewport.X / value; self.Height = (viewport.Y - inset.Y) / value
        self.Shell.Size = UDim2.fromOffset(math.min(900, self.Width - 30), math.min(670, self.Height - 30))
        self.PanelTitle.TextSize = self.Width < 650 and 20 or 25
        noticeRoot.Size = UDim2.fromOffset(math.min(390, self.Width - 30), 250)
        self:LayoutMenu(); self:LayoutHUD()
    end
    function app:ApplySettings()
        local s = self.State.profile.Settings
        local hash = s.graphics .. ":" .. s.scale .. ":" .. s.sensitivity
        if self.AppliedSettings == hash then return end
        self.AppliedSettings = hash
        Input.MouseDeltaSensitivity = s.sensitivity
        Lighting.GlobalShadows = s.graphics == "High" or s.graphics == "Ultra"
        for _, effect in Lighting:GetChildren() do
            if effect:IsA("BloomEffect") or effect:IsA("SunRaysEffect") then effect.Enabled = s.graphics == "High" or s.graphics == "Ultra"
            elseif effect:IsA("ColorCorrectionEffect") then effect.Enabled = s.graphics ~= "Low" end
        end
        self:Layout()
    end
    function app:RefreshMiniMap()
        UI.clear(self.MiniMapHolder); self.MiniDot = self.Map.draw(self, self.MiniMapHolder, true)
    end
    function app:UpdateHUD()
        local s = self.State; if not s then return end
        local c = s.campaign
        local resources = c.Cash .. " CASH   /   " .. s.profile.Coins .. " COINS"
        if self.Width >= 900 then resources ..= "     ·     МЕТАЛЛ " .. ((c.Stockpile.metal or 0) + self:Count("metal")) .. "     ·     " .. s.power.supply .. "/" .. s.power.demand .. " кВт" end
        self.TopResources.Text = resources
        local hour = Lighting.ClockTime
        self.Clock.Text = string.format("%02d:%02d  /  %s", math.floor(hour), math.floor(hour % 1 * 60), s.weather)
        self.StaminaBar:set(s.stamina or 100, 100)
        local h = self.Player.Character and self.Player.Character:FindFirstChildOfClass("Humanoid")
        self.HealthBar:set(h and h.Health or 100, h and h.MaxHealth or 100)
        self.SurvivalLabel.Text = "ЕДА " .. math.floor(c.Hunger) .. "%    ВОДА " .. math.floor(c.Thirst) .. "%"
        local e = self:Equipped()
        self.EquipmentTitle.Text = e and self.Items[e.id].name:upper() or "БЕЗ ОРУЖИЯ"
        self.AmmoLabel.Text = e and (self.Weapons[e.id].melee and "Ближний бой" or e.ammo .. " / " .. self.Weapons[e.id].magazine .. "  ·  запас " .. self:Count("ammo")) or "Экипируйте оружие: I"
        if e then self.AmmoLabel.Text ..= "  ·  " .. math.floor(e.durability) .. "%" end
        self.SaveLabel.Text = self:SaveText(); self.SaveLabel.TextColor3 = (s.save == "SessionOnly" or s.save == "Error") and T.danger or T.muted
        if s.save == "SessionOnly" then self.SaveLabel.TextColor3 = T.accent end
        local track = self.TrackedQuest
        if track and c.Quests[track] and c.Quests[track].status == "RewardClaimed" then self.TrackedQuest = nil; track = nil end
        if not track then
            track = nil
            for _, id in self:Sorted(self.Quests) do if c.Quests[id] and (c.Quests[id].status == "Active" or c.Quests[id].status == "Completed") then track = id; break end end
        end
        if track then
            local def, q = self.Quests[track], c.Quests[track]
            self.TrackerTitle.Text = def.name
            if not q then self.TrackerText.Text = "Подойдите к NPC и нажмите E.\n" .. ({alex = "Капитан Алекс", max = "Торговец Макс", victor = "Инженер Виктор"})[def.npc]
            elseif q.status == "Completed" then self.TrackerText.Text = "ЦЕЛИ ВЫПОЛНЕНЫ\nВернитесь к NPC за наградой.\n" .. ({alex = "Капитан Алекс", max = "Торговец Макс", victor = "Инженер Виктор"})[def.npc]
            else
                local lines = {}
                for i, objective in def.objectives do table.insert(lines, objective.label .. ": " .. q.progress[i] .. "/" .. objective.count) end
                self.TrackerText.Text = table.concat(lines, "\n")
            end
        else self.TrackerTitle.Text = "ПЕРВЫЙ ДЕНЬ"; self.TrackerText.Text = "Подойдите к Алексу у северных ворот и нажмите E.\nВаш участок: №" .. s.plot .. ". Карта: M." end
        self.TutorialCard.Visible = c.Tutorial.status == "Active"
        local step = self.Config.TutorialSteps[s.stage]
        self.TutorialLabel.Text = step and ("ОБУЧЕНИЕ  /  " .. s.stage .. " из 12\n\n" .. step.text) or "ВСЕ ЭТАПЫ ПРОЙДЕНЫ\nОткройте обучение в настройках и заберите награду в убежище."
        self.Crosshair.Visible = s.active and not self.PanelName and not self.Camera.Commander
    end
    player:SetAttribute("LastCityClientPhase", "Controllers")
    app.Camera = require(script.Parent.Controllers.CameraController)(app)
    app.Build = require(script.Parent.Controllers.BuildController)(app)
    app.Combat = require(script.Parent.Controllers.CombatController)(app)
    app.Effects = require(script.Parent.Controllers.EffectsController)(app)
    app.ActorAnimation = require(script.Parent.Controllers.ActorAnimationController)(app)
    local function onStateMessage(kind, data)
        if kind == "state" then
            local previous = app.State
            app.State = data
            if not previous or previous.plot ~= data.plot or previous.revision ~= data.revision then
                app:RefreshMiniMap()
            end
            app.Menu.Visible = not data.active; app.HUD.Visible = data.active
            app.PlayButton.Text = data.campaign.Started and "ПРОДОЛЖИТЬ                              →" or "ИГРАТЬ                                      →"
            if app.SoloNote then app.SoloButton.Visible = not data.studio; app.SoloNote.Visible = data.studio end
            app:ApplySettings(); app:UpdateHUD()
            if app.PanelName and (not previous or previous.revision ~= data.revision or app.PanelName == "craft" or app.PanelName == "base") then app:RefreshPanel() end
            if data.active and (not previous or not previous.active) then
                transition.Visible = true; transition.BackgroundTransparency = 0
                TweenService:Create(transition, TweenInfo.new(0.55), {BackgroundTransparency = 1}):Play()
                task.delay(0.6, function() transition.Visible = false end)
                app:ClosePanel()
                if app.StartTutorialOnPlay then app.StartTutorialOnPlay = false; app:Send("SessionRemote", {action = "tutorial", choice = "start"})
                elseif app.SkipTutorialOnPlay then app.SkipTutorialOnPlay = false; app:Send("SessionRemote", {action = "tutorial", choice = "skip"})
                elseif data.campaign.Tutorial.status == "New" then app:OpenPanel("tutorial") end
                app:Toast("МЕРИДИАН  /  Ваш участок №" .. data.plot .. " к югу от убежища. Карта: M.")
            end
            player:SetAttribute("LastCityClientPhase", "Ready")
            app.Player:SetAttribute("LastCityClientReady", true)
        elseif kind == "notice" then app:Toast(data.text, data.kind)
        elseif kind == "dialogue" then
            app.Dialogue = data; app.DialogueText = nil; app.LastDialogueText = nil
            app:OpenPanel("dialogue"); app.Camera:SetDialogue(data.model)
        elseif kind == "commander" then app.Camera:SetCommander(data)
        elseif kind == "panel" then app:OpenPanel(data)
        elseif kind == "reload" then app:Toast("ПЕРЕЗАРЯДКА  /  " .. data .. " c") end
    end
    app.Remotes.StateRemote.OnClientEvent:Connect(function(kind, data)
        local ok, err = xpcall(function() onStateMessage(kind, data) end, debug.traceback)
        if not ok then player:SetAttribute("LastCityClientError", "Ошибка интерфейса: LC_STATE"); warn("[LastCity LC_STATE] " .. tostring(err)) end
    end)
    local healthConnection
    app.Player.CharacterAdded:Connect(function(character)
        app.Camera:SetCommander(false); app.Crouching = false
        if healthConnection then healthConnection:Disconnect() end
        local h = character:WaitForChild("Humanoid", 10)
        if h then healthConnection = h.HealthChanged:Connect(function() app:UpdateHUD() end) end
    end)
    pcall(function() StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Backpack, false); StarterGui:SetCoreGuiEnabled(Enum.CoreGuiType.Health, false) end)
    local accumulator = 0
    RunService.RenderStepped:Connect(function(dt)
        accumulator += dt
        if accumulator < 0.1 then return end
        accumulator = 0
        local camera = workspace.CurrentCamera
        if camera and (not app.LastViewport or app.LastViewport ~= camera.ViewportSize) then app.LastViewport = camera.ViewportSize; app:Layout() end
        app:UpdateHUD()
        local root = app.Player.Character and app.Player.Character:FindFirstChild("HumanoidRootPart")
        if root then
            for _, dot in {app.MiniDot, app.MapDot} do
                if dot and dot.Parent then
                    dot.Position = UDim2.fromScale(math.clamp((root.Position.X + 300) / 600, 0.02, 0.98), math.clamp((450 - root.Position.Z) / 1000, 0.02, 0.98))
                    dot.Rotation = math.deg(math.atan2(root.CFrame.LookVector.X, root.CFrame.LookVector.Z))
                end
            end
        end
    end)
    app:Layout()
    player:SetAttribute("LastCityClientPhase", "AwaitingState")
    task.spawn(function()
        local deadline = os.clock() + 45
        while not app.State and os.clock() < deadline do app:Send("SessionRemote", {action = "sync"}); task.wait(2) end
    end)
end
local ok, err = xpcall(boot, debug.traceback)
if not ok then
    player:SetAttribute("LastCityClientError", "Ошибка запуска интерфейса: LC_CLIENT")
    warn("[LastCity LC_CLIENT] " .. tostring(err))
end
