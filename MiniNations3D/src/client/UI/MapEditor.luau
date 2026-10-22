local MapEditor = {}
function MapEditor.open()
    local pg=game.Players.LocalPlayer:WaitForChild("PlayerGui")
    if pg:FindFirstChild("MapEditorGui") then return end
    local gui=Instance.new("ScreenGui"); gui.Name="MapEditorGui"; gui.ResetOnSpawn=false; gui.Parent=pg
    local panel=Instance.new("Frame"); panel.Size=UDim2.fromOffset(220,520); panel.Position=UDim2.fromScale(1,0.15); panel.AnchorPoint=Vector2.new(1,0); panel.BackgroundColor3=Color3.fromHex("#111827"); panel.BackgroundTransparency=0.15; panel.Parent=gui; Instance.new("UICorner",panel).CornerRadius=UDim.new(0,14)
    local pad=Instance.new("UIPadding", panel); pad.PaddingTop=UDim.new(0,12); pad.PaddingLeft=UDim.new(0,12); pad.PaddingRight=UDim.new(0,12)
    local list=Instance.new("UIListLayout", panel); list.Padding=UDim.new(0,8)
    local title=Instance.new("TextLabel"); title.Size=UDim2.new(1,0,0,20); title.BackgroundTransparency=1; title.Text="🗺️ РЕДАКТОР КАРТ"; title.TextColor3=Color3.fromHex("#F59E0B"); title.Font=Enum.Font.GothamBold; title.TextSize=12; title.Parent=panel
    local tools={"Кисть земли","Кисть воды","Кисть гор","Кисть леса","Кисть границ","Создать страну","Провинция","Город","Ресурсы","Ластик","Отмена","Повтор"}
    for _,t in ipairs(tools) do
        local b=Instance.new("TextButton"); b.Size=UDim2.new(1,0,0,28); b.Text=t; b.Font=Enum.Font.Gotham; b.TextSize=11; b.BackgroundColor3=Color3.fromHex("#1F2937"); b.TextColor3=Color3.new(1,1,1); b.Parent=panel; Instance.new("UICorner",b).CornerRadius=UDim.new(0,8)
        b.MouseButton1Click:Connect(function() b.BackgroundColor3=Color3.fromHex("#F59E0B") end)
    end
    local save=Instance.new("TextButton"); save.Size=UDim2.new(1,0,0,34); save.Text="💾 СОХРАНИТЬ КАРТУ"; save.Font=Enum.Font.GothamBold; save.TextSize=11; save.BackgroundColor3=Color3.fromHex("#22C55E"); save.TextColor3=Color3.new(1,1,1); save.Parent=panel; Instance.new("UICorner",save).CornerRadius=UDim.new(0,8)
    save.MouseButton1Click:Connect(function()
        local rem=game:GetService("ReplicatedStorage").Remotes:FindFirstChild("SaveMap")
        if rem then rem:FireServer("MyMap_"..math.random(1000,9999), {size="Large", provinces=120, custom=true}) end
    end)
    local playBtn=Instance.new("TextButton"); playBtn.Size=UDim2.new(1,0,0,34); playBtn.Text="▶ ИГРАТЬ НА КАРТЕ"; playBtn.Font=Enum.Font.GothamBold; playBtn.TextSize=11; playBtn.BackgroundColor3=Color3.fromHex("#3B82F6"); playBtn.TextColor3=Color3.new(1,1,1); playBtn.Parent=panel; Instance.new("UICorner",playBtn).CornerRadius=UDim.new(0,8)
    local close=Instance.new("TextButton"); close.Size=UDim2.fromOffset(28,28); close.Position=UDim2.fromScale(1,0); close.AnchorPoint=Vector2.new(1,0); close.Text="✕"; close.BackgroundColor3=Color3.fromHex("#EF4444"); close.TextColor3=Color3.new(1,1,1); close.Font=Enum.Font.GothamBold; close.Parent=panel; Instance.new("UICorner",close).CornerRadius=UDim.new(1,0)
    close.MouseButton1Click:Connect(function() gui:Destroy() end)
end
return MapEditor
