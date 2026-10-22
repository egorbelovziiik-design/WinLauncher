local SettingsUI = {}
function SettingsUI.open()
    local pg=game.Players.LocalPlayer:WaitForChild("PlayerGui")
    if pg:FindFirstChild("Settings") then pg.Settings:Destroy() end
    local gui=Instance.new("ScreenGui"); gui.Name="Settings"; gui.ResetOnSpawn=false; gui.Parent=pg
    local bg=Instance.new("Frame"); bg.Size=UDim2.fromOffset(520,420); bg.Position=UDim2.fromScale(0.5,0.5); bg.AnchorPoint=Vector2.new(0.5,0.5); bg.BackgroundColor3=Color3.fromHex("#0F172A"); bg.Parent=gui; Instance.new("UICorner",bg).CornerRadius=UDim.new(0,16)
    local title=Instance.new("TextLabel"); title.Size=UDim2.new(1,0,0,42); title.BackgroundTransparency=1; title.Text="⚙️ НАСТРОЙКИ"; title.TextColor3=Color3.new(1,1,1); title.Font=Enum.Font.GothamBold; title.TextSize=16; title.Parent=bg
    local function row(y:number, label:string, options:{string})
        local f=Instance.new("Frame"); f.Size=UDim2.new(1,-24,0,38); f.Position=UDim2.fromOffset(12,50+y*44); f.BackgroundColor3=Color3.fromHex("#1E293B"); f.Parent=bg; Instance.new("UICorner",f).CornerRadius=UDim.new(0,8)
        local l=Instance.new("TextLabel"); l.Size=UDim2.fromScale(0.45,1); l.BackgroundTransparency=1; l.Text=label; l.TextColor3=Color3.new(1,1,1); l.Font=Enum.Font.Gotham; l.TextSize=12; l.TextXAlignment=Enum.TextXAlignment.Left; l.Parent=f; Instance.new("UIPadding",l).PaddingLeft=UDim.new(0,12)
        for i,opt in ipairs(options) do
            local b=Instance.new("TextButton"); b.Size=UDim2.fromOffset(68,26); b.Position=UDim2.fromScale(0.48 + (i-1)*0.13,0.5); b.AnchorPoint=Vector2.new(0,0.5); b.Text=opt; b.Font=Enum.Font.GothamMedium; b.TextSize=10; b.BackgroundColor3=Color3.fromHex("#334155"); b.TextColor3=Color3.new(1,1,1); b.Parent=f; Instance.new("UICorner",b).CornerRadius=UDim.new(0,6)
            b.MouseButton1Click:Connect(function() b.BackgroundColor3=Color3.fromHex("#3B82F6") end)
        end
    end
    row(0,"Графика",{"Low","Medium","High","Ultra"})
    row(1,"Звук Music",{"0%","50%","100%"})
    row(2,"Камера скорость",{"Медл.","Норм.","Быстро"})
    row(3,"Масштаб UI",{"80%","100%","120%"})
    row(4,"Язык",{"RU","EN"})
    row(5,"Анимации",{"Вкл","Выкл"})
    local save=Instance.new("TextButton"); save.Size=UDim2.new(1,-24,0,38); save.Position=UDim2.fromOffset(12,360); save.Text="СОХРАНИТЬ"; save.Font=Enum.Font.GothamBold; save.TextSize=13; save.BackgroundColor3=Color3.fromHex("#22C55E"); save.TextColor3=Color3.new(1,1,1); save.Parent=bg; Instance.new("UICorner",save).CornerRadius=UDim.new(0,10)
    save.MouseButton1Click:Connect(function() gui:Destroy() end)
    local close=Instance.new("TextButton"); close.Size=UDim2.fromOffset(32,32); close.Position=UDim2.fromScale(1,0); close.AnchorPoint=Vector2.new(1,0); close.Text="✕"; close.BackgroundColor3=Color3.fromHex("#EF4444"); close.TextColor3=Color3.new(1,1,1); close.Font=Enum.Font.GothamBold; close.Parent=bg; Instance.new("UICorner",close).CornerRadius=UDim.new(1,0)
    close.MouseButton1Click:Connect(function() gui:Destroy() end)
end
return SettingsUI
