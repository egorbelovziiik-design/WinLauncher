local HUD = {}
local panel: Frame? = nil
local provinceLbl: TextLabel? = nil

function HUD.build(): ScreenGui
    local gui=Instance.new("ScreenGui"); gui.Name="HUD"; gui.ResetOnSpawn=false; gui.Enabled=false; gui.ZIndexBehavior=Enum.ZIndexBehavior.Sibling
    -- верхняя полоса ресурсов
    local top=Instance.new("Frame"); top.Size=UDim2.new(1,0,0,44); top.BackgroundColor3=Color3.fromHex("#0F172A"); top.BackgroundTransparency=0.15; top.BorderSizePixel=0; top.Parent=gui
    Instance.new("UIStroke", top).Color=Color3.fromHex("#1E293B"); top.BackgroundTransparency=0.2
    local layout=Instance.new("UIListLayout", top); layout.FillDirection=Enum.FillDirection.Horizontal; layout.Padding=UDim.new(0,14); layout.VerticalAlignment=Enum.VerticalAlignment.Center; layout.HorizontalAlignment=Enum.HorizontalAlignment.Center

    local function chip(text:string) local l=Instance.new("TextLabel"); l.Size=UDim2.fromOffset(120,28); l.BackgroundColor3=Color3.fromHex("#1E293B"); l.Text=text; l.TextColor3=Color3.new(1,1,1); l.Font=Enum.Font.GothamBold; l.TextSize=12; l.Parent=top; Instance.new("UICorner",l).CornerRadius=UDim.new(0,8); return l end
    chip("💰 $12.4K"); chip("⚡ 340 / 420 MW"); chip("🍞 Food 820"); chip("🛢️ Oil 540"); chip("👥 42M")
    -- режимы карты
    local modes=Instance.new("Frame"); modes.Size=UDim2.fromOffset(520,36); modes.Position=UDim2.fromScale(0.5,0.085); modes.AnchorPoint=Vector2.new(0.5,0); modes.BackgroundTransparency=1; modes.Parent=gui
    local ml=Instance.new("UIListLayout", modes); ml.FillDirection=Enum.FillDirection.Horizontal; ml.Padding=UDim.new(0,6)
    for _,m in ipairs({"Political","Economic","Military","Resource"}) do
        local b=Instance.new("TextButton"); b.Size=UDim2.fromOffset(120,32); b.Text=m; b.Font=Enum.Font.GothamMedium; b.TextSize=11; b.TextColor3=Color3.new(1,1,1); b.BackgroundColor3=Color3.fromHex("#1E293B"); b.Parent=modes; Instance.new("UICorner",b).CornerRadius=UDim.new(0,8)
        b.MouseButton1Click:Connect(function() require(script.Parent.Parent.Controllers.MapController).setMapMode(m) end)
    end
    -- левая панель строительства
    local left=Instance.new("Frame"); left.Size=UDim2.fromOffset(210,420); left.Position=UDim2.fromScale(0.015,0.18); left.BackgroundColor3=Color3.fromHex("#111827"); left.BackgroundTransparency=0.2; left.Parent=gui; Instance.new("UICorner",left).CornerRadius=UDim.new(0,14)
    local lp=Instance.new("UIPadding", left); lp.PaddingTop=UDim.new(0,14); lp.PaddingLeft=UDim.new(0,12); lp.PaddingRight=UDim.new(0,12)
    local ll=Instance.new("UIListLayout", left); ll.Padding=UDim.new(0,8)
    local title=Instance.new("TextLabel"); title.Size=UDim2.new(1,0,0,22); title.BackgroundTransparency=1; title.Text="🏙️ СТРОИТЕЛЬСТВО"; title.TextColor3=Color3.fromHex("#93C5FD"); title.Font=Enum.Font.GothamBold; title.TextSize=12; title.Parent=left
    local cats={"House","Shop","CoalPlant","MilitaryFactory","Barracks","Road","Port","Airport"}
    for _,c in ipairs(cats) do
        local b=Instance.new("TextButton"); b.Size=UDim2.new(1,0,0,36); b.Text=c; b.Font=Enum.Font.Gotham; b.TextSize=12; b.BackgroundColor3=Color3.fromHex("#1F2937"); b.TextColor3=Color3.new(1,1,1); b.Parent=left; Instance.new("UICorner",b).CornerRadius=UDim.new(0,8)
        b.MouseButton1Click:Connect(function() require(script.Parent.Parent.Controllers.BuildingController).select(c); require(script.Parent.Parent.Controllers.BuildingController).place() end)
    end
    -- правая панель провинции
    panel=Instance.new("Frame"); panel.Name="ProvincePanel"; panel.Size=UDim2.fromOffset(300,380); panel.Position=UDim2.fromScale(1,0.18); panel.AnchorPoint=Vector2.new(1,0); panel.BackgroundColor3=Color3.fromHex("#111827"); panel.BackgroundTransparency=0.18; panel.Parent=gui; Instance.new("UICorner",panel).CornerRadius=UDim.new(0,14)
    local pp=Instance.new("UIPadding", panel); pp.PaddingLeft=UDim.new(0,16); pp.PaddingRight=UDim.new(0,16); pp.PaddingTop=UDim.new(0,16)
    provinceLbl=Instance.new("TextLabel"); provinceLbl.Size=UDim2.new(1,0,0,70); provinceLbl.BackgroundTransparency=1; provinceLbl.Text="Выбери провинцию\nЛКМ по карте"; provinceLbl.TextColor3=Color3.new(1,1,1); provinceLbl.Font=Enum.Font.GothamBold; provinceLbl.TextSize=13; provinceLbl.TextWrapped=true; provinceLbl.Parent=panel
    -- кнопки действий
    local function act(text:string, cb:()->())
        local b=Instance.new("TextButton"); b.Size=UDim2.new(1,0,0,34); b.Text=text; b.Font=Enum.Font.GothamMedium; b.TextSize=12; b.BackgroundColor3=Color3.fromHex("#2563EB"); b.TextColor3=Color3.new(1,1,1); b.Parent=panel; Instance.new("UICorner",b).CornerRadius=UDim.new(0,8); b.MouseButton1Click:Connect(cb); return b
    end
    act("⚔️ Объявить войну", function()
        local MapCtrl=require(script.Parent.Parent.Controllers.MapController)
        local sel=MapCtrl.getSelectedCountry()
        if sel then require(script.Parent.Parent.Controllers.DiplomacyController).declareWar(sel, "RUS") end
    end)
    act("🤝 Альянс", function()
        require(script.Parent.Parent.Controllers.DiplomacyController).createAlliance("Pact", Color3.fromHex("#22C55E"), "USA")
    end)
    act("💡 Исследовать", function()
        local rem=game:GetService("ReplicatedStorage").Remotes:FindFirstChild("ResearchTech")
        if rem then rem:FireServer("USA","Army","ModernInfantry") end
    end)
    -- нижняя панель времени
    local timeBar=Instance.new("Frame"); timeBar.Size=UDim2.new(0.42,0,0,38); timeBar.Position=UDim2.fromScale(0.5,1); timeBar.AnchorPoint=Vector2.new(0.5,1); timeBar.BackgroundColor3=Color3.fromHex("#020617"); timeBar.Parent=gui; Instance.new("UICorner",timeBar).CornerRadius=UDim.new(0,12)
    local tl=Instance.new("TextLabel"); tl.Size=UDim2.fromScale(1,1); tl.BackgroundTransparency=1; tl.Text="📅 14 ИЮНЯ 2026  •  ⏩ x2  •  ☀️ ЯСНО"; tl.TextColor3=Color3.new(1,1,1); tl.Font=Enum.Font.GothamMedium; tl.TextSize=11; tl.Parent=timeBar
    -- день/ночь индикатор
    local dayNight=Instance.new("TextLabel"); dayNight.Position=UDim2.fromScale(0.85,0.18); dayNight.Size=UDim2.fromOffset(90,26); dayNight.BackgroundColor3=Color3.fromHex("#1E293B"); dayNight.Text="🌙 ДЕНЬ"; dayNight.TextColor3=Color3.new(1,1,1); dayNight.Font=Enum.Font.Gotham; dayNight.TextSize=11; dayNight.Parent=gui; Instance.new("UICorner",dayNight).CornerRadius=UDim.new(1,0)
    return gui
end

function HUD.showGame()
    for _,g in ipairs(game.Players.LocalPlayer:WaitForChild("PlayerGui"):GetChildren()) do
        if g.Name=="MainMenu" then g.Enabled=false end
        if g.Name=="HUD" then g.Enabled=true end
    end
    -- если HUD ещё не создан — создай
    local pg=game.Players.LocalPlayer:FindFirstChild("PlayerGui")
    if pg and not pg:FindFirstChild("HUD") then local h=HUD.build(); h.Parent=pg; h.Enabled=true end
end

function HUD.showProvince(prov:any)
    if provinceLbl then
        provinceLbl.Text = string.format("%s\nВладелец: %s\nНаселение: %d\nЭкономика: %d\nЗданий: %d", prov.name, prov.owner, prov.population, prov.economy, #prov.buildings)
    end
end
return HUD
