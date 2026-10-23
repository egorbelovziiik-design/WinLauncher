--!nonstrict
return function(app)
    local Controller = {Count = 0, Stock = -1}
    local model = Instance.new("Model"); model.Name = "ClientWoodStorage"; model.Parent = workspace
    local boards = Instance.new("Folder"); boards.Name = "Boards"; boards.Parent = model
    local gui = Instance.new("BillboardGui"); gui.Name = "WoodStorageLabel"; gui.Size = UDim2.fromOffset(250, 54)
    gui.AlwaysOnTop = true; gui.MaxDistance = 110; gui.Enabled = false; gui.Parent = model
    local label = Instance.new("TextLabel"); label.Size = UDim2.fromScale(1, 1); label.BackgroundColor3 = Color3.fromRGB(31, 40, 42)
    label.BackgroundTransparency = 0.18; label.TextColor3 = Color3.fromRGB(242, 187, 104); label.TextStrokeTransparency = 0.45
    label.Font = Enum.Font.GothamBold; label.TextSize = 13; label.TextWrapped = true; label.Parent = gui
    local colors = {Color3.fromRGB(116, 91, 61), Color3.fromRGB(138, 103, 66), Color3.fromRGB(103, 82, 58)}
    local function origin()
        local world = workspace:FindFirstChild("World")
        local cities = world and world:FindFirstChild("Cities")
        local shelter = cities and cities:FindFirstChild("01_MeridianShelter")
        local depot = shelter and shelter:FindFirstChild("WoodDepot")
        return depot and depot:FindFirstChild("PileOrigin")
    end
    local function clear()
        for _, board in boards:GetChildren() do board:Destroy() end
        Controller.Count = 0
    end
    function Controller:Refresh()
        local point = origin()
        if not point or not point.Parent then gui.Enabled = false; return end
        local stock = app.State and app.State.campaign and app.State.campaign.Stockpile.wood or 0
        stock = math.max(0, math.floor(stock))
        local count = stock > 0 and math.min(app.Config.MaxBuildings or 36, math.ceil(stock / 10)) or 0
        gui.Adornee = point; gui.StudsOffset = Vector3.new(0, 5.2 + math.floor(math.max(0, count - 1) / 4) * 0.44, 0)
        gui.Enabled = true; label.Text = "ДЕРЕВО НА СКЛАДЕ: " .. stock .. "\n[E]  СЛОЖИТЬ ВСЁ ИЗ РЮКЗАКА"
        if self.Origin == point and self.Stock == stock and self.Count == count then return end
        self.Origin = point; self.Stock = stock; clear()
        for index = 1, count do
            local layer = math.floor((index - 1) / 4); local slot = (index - 1) % 4
            local cross = layer % 2 == 1
            local board = Instance.new("Part"); board.Name = "StoredWoodBoard"
            board.Size = cross and Vector3.new(11.2, 0.42, 1.45) or Vector3.new(2.55, 0.42, 7.2)
            local x = cross and 0 or (slot - 1.5) * 2.75
            local z = cross and (slot - 1.5) * 1.55 or 0
            board.CFrame = point.CFrame * CFrame.new(x, 0.34 + layer * 0.44, z)
            board.Color = colors[index % #colors + 1]; board.Material = Enum.Material.WoodPlanks
            board.Anchored = true; board.CanCollide = false; board.CanTouch = false; board.CanQuery = false
            board.CastShadow = true; board.Parent = boards
        end
        self.Count = count
    end
    Controller.Model = model; Controller.Label = label
    return Controller
end
