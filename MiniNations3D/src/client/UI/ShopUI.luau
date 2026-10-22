local ShopUI = {}
function ShopUI.open()
    local pg=game.Players.LocalPlayer:WaitForChild("PlayerGui")
    if pg:FindFirstChild("Shop") then pg.Shop:Destroy() end
    local gui=Instance.new("ScreenGui"); gui.Name="Shop"; gui.ResetOnSpawn=false; gui.Parent=pg
    local bg=Instance.new("Frame"); bg.Size=UDim2.fromOffset(760,520); bg.Position=UDim2.fromScale(0.5,0.5); bg.AnchorPoint=Vector2.new(0.5,0.5); bg.BackgroundColor3=Color3.fromHex("#0F172A"); bg.Parent=gui; Instance.new("UICorner",bg).CornerRadius=UDim.new(0,16); Instance.new("UIStroke",bg).Color=Color3.fromHex("#334155")
    local title=Instance.new("TextLabel"); title.Size=UDim2.new(1,0,0,44); title.BackgroundTransparency=1; title.Text="🛒 МАГАЗИН — только косметика, без Pay-to-Win"; title.TextColor3=Color3.new(1,1,1); title.Font=Enum.Font.GothamBold; title.TextSize=16; title.Parent=bg
    local grid=Instance.new("Frame"); grid.Size=UDim2.new(1,-24,1,-60); grid.Position=UDim2.fromOffset(12,48); grid.BackgroundTransparency=1; grid.Parent=bg
    local gl=Instance.new("UIGridLayout", grid); gl.CellSize=UDim2.fromOffset(170,120); gl.CellPadding=UDim2.fromOffset(12,12)
    local catalog={
        {id="gold_borders", name="Золотые границы", price=400},
        {id="neon_borders", name="Неоновые границы", price=600},
        {id="modern_city", name="Современный город", price=800},
        {id="gold_tank", name="Золотые танки", price=700},
        {id="music_war", name="Боевой саундтрек", price=500},
        {id="ui_neon", name="Неоновая тема", price=450},
        {id="flag_goldframe", name="Золотая рамка", price=350},
        {id="diamond_borders", name="Алмазные границы", price=1200},
    }
    for _,it in ipairs(catalog) do
        local c=Instance.new("TextButton"); c.BackgroundColor3=Color3.fromHex("#1E293B"); c.Text=""; c.Parent=grid; Instance.new("UICorner",c).CornerRadius=UDim.new(0,12)
        local n=Instance.new("TextLabel"); n.Size=UDim2.new(1,0,0,36); n.BackgroundTransparency=1; n.Text=it.name; n.TextColor3=Color3.new(1,1,1); n.Font=Enum.Font.GothamMedium; n.TextSize=11; n.TextWrapped=true; n.Parent=c
        local p=Instance.new("TextLabel"); p.Size=UDim2.new(1,0,0,22); p.Position=UDim2.fromScale(0,0.55); p.BackgroundTransparency=1; p.Text="🪙 "..it.price; p.TextColor3=Color3.fromHex("#FACC15"); p.Font=Enum.Font.GothamBold; p.TextSize=13; p.Parent=c
        local buy=Instance.new("TextButton"); buy.Size=UDim2.new(1,-12,0,28); buy.Position=UDim2.fromOffset(6,86); buy.Text="КУПИТЬ"; buy.Font=Enum.Font.GothamBold; buy.TextSize=11; buy.BackgroundColor3=Color3.fromHex("#22C55E"); buy.TextColor3=Color3.new(1,1,1); buy.Parent=c; Instance.new("UICorner",buy).CornerRadius=UDim.new(0,8)
        buy.MouseButton1Click:Connect(function()
            require(script.Parent.Parent.Controllers.ShopController).buy(it.id)
            buy.Text="✓ КУПЛЕНО"; buy.BackgroundColor3=Color3.fromHex("#64748B")
        end)
    end
    local close=Instance.new("TextButton"); close.Size=UDim2.fromOffset(32,32); close.Position=UDim2.fromScale(1,0); close.AnchorPoint=Vector2.new(1,0); close.Text="✕"; close.Font=Enum.Font.GothamBold; close.TextSize=16; close.BackgroundColor3=Color3.fromHex("#EF4444"); close.TextColor3=Color3.new(1,1,1); close.Parent=bg; Instance.new("UICorner",close).CornerRadius=UDim.new(1,0)
    close.MouseButton1Click:Connect(function() gui:Destroy() end)
end
return ShopUI
