--!nonstrict
return function(app)
    local RunService = game:GetService("RunService")
    local Input = game:GetService("UserInputService")
    local GuiService = game:GetService("GuiService")
    local Build = {Active = false, Rotation = 0, X = 0, Z = 0, Valid = false}
    local self = Build
    function Build:Cancel()
        self.Active = false
        if self.Ghost then self.Ghost:Destroy(); self.Ghost = nil end
        if app.BuildBar then app.BuildBar.Visible = false end
    end
    function Build:Start(id)
        self:Cancel(); app:ClosePanel()
        self.Id = id; self.Rotation = 0; self.Active = true
        self.Ghost = app.Factory.building(id, app.Buildings[id], CFrame.new())
        self.Ghost.Name = "PlacementPreview"
        for _, p in self.Ghost:GetDescendants() do
            if p:IsA("BasePart") then p.CanCollide = false; p.CanQuery = false; p.CanTouch = false; p.Transparency = 0.55; p.CastShadow = false
            elseif p:IsA("Light") then p.Enabled = false end
        end
        self.Ghost.Parent = workspace
        app.BuildBar.Visible = true
    end
    function Build:Rotate() self.Rotation = (self.Rotation + 1) % 4 end
    function Build:Place()
        if not self.Active then return end
        if not self.Valid then app:Toast(self.Reason or "Здесь нельзя строить", "error"); return end
        app:Send("BuildRemote", {action = "place", id = self.Id, x = self.X, z = self.Z, r = self.Rotation})
    end
    local function extents(def, r)
        if r % 2 == 0 then return def.size[1] / 2, def.size[3] / 2 end
        return def.size[3] / 2, def.size[1] / 2
    end
    function Build:Check()
        local s = app.State; local c = s.campaign; local def = app.Buildings[self.Id]
        local plot = app.Config.Plots[s.plot]; local root = app.Player.Character and app.Player.Character:FindFirstChild("HumanoidRootPart")
        if not root or math.abs(root.Position.X - plot.x) > 60 or math.abs(root.Position.Z - plot.z) > 60 then return false, "Подойдите к участку " .. s.plot .. ". Маркер на карте M." end
        local hx, hz = extents(def, self.Rotation)
        if math.abs(self.X) + hx > 48 or math.abs(self.Z) + hz > 48 then return false, "За пределами вашего участка" end
        for _, zone in app.Config.PlotReserved do
            if math.abs(self.X - zone.x) < hx + zone.hx and math.abs(self.Z - zone.z) < hz + zone.hz then return false, zone.reason end
        end
        if #c.Buildings >= app.Config.MaxBuildings then return false, "Достигнут лимит построек" end
        for _, b in c.Buildings do
            local ox, oz = extents(app.Buildings[b.id], b.r)
            if math.abs(self.X - b.x) < hx + ox + 0.1 and math.abs(self.Z - b.z) < hz + oz + 0.1 then return false, "Пересечение с постройкой" end
        end
        if c.Cash < def.cash then return false, "Недостаточно Survival Cash" end
        for id, count in def.cost do if (c.Stockpile[id] or 0) + app:Count(id) < count then return false, "Не хватает: " .. app.Items[id].name end end
        if def.level and app:Level() < def.level then return false, "Нужен уровень " .. def.level end
        if def.blueprint and app:Count(def.blueprint) < 1 then return false, "Нужен чертёж Виктора" end
        return true, "ЛКМ / Разместить • R / Повернуть • Esc / Отмена"
    end
    RunService.RenderStepped:Connect(function()
        if not self.Active or not self.Ghost or not app.State then return end
        local camera = workspace.CurrentCamera; if not camera then return end
        local mouse = Input:GetMouseLocation() - GuiService:GetGuiInset()
        if Input.TouchEnabled then mouse = camera.ViewportSize / 2 end
        local ray = camera:ViewportPointToRay(mouse.X, mouse.Y)
        if math.abs(ray.Direction.Y) < 0.01 then self.Valid = false; return end
        local t = -ray.Origin.Y / ray.Direction.Y
        if t <= 0 or t > 1500 then self.Valid = false; return end
        local position = ray.Origin + ray.Direction * t
        local plot = app.Config.Plots[app.State.plot]
        self.X = math.round((position.X - plot.x) / 4) * 4; self.Z = math.round((position.Z - plot.z) / 4) * 4
        -- Pivot is the foundation centre, 0.3 studs above the placement plane.
        self.Ghost:PivotTo(CFrame.new(plot.x + self.X, 0.3, plot.z + self.Z) * CFrame.Angles(0, self.Rotation * math.pi / 2, 0))
        self.Valid, self.Reason = self:Check()
        local color = self.Valid and app.UI.Theme.success or app.UI.Theme.danger
        for _, p in self.Ghost:GetDescendants() do if p:IsA("BasePart") then p.Color = color end end
        app.BuildHint.Text = app.Buildings[self.Id].name .. "  /  " .. self.Reason; app.BuildHint.TextColor3 = color
    end)
    return Build
end
