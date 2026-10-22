local ProfileUI = {}
function ProfileUI.open()
    local pl=game.Players.LocalPlayer
    local pg=pl:WaitForChild("PlayerGui")
    if pg:FindFirstChild("Profile") then pg.Profile:Destroy() end
    local gui=Instance.new("ScreenGui"); gui.Name="Profile"; gui.ResetOnSpawn=false; gui.Parent=pg
    local bg=Instance.new("Frame"); bg.Size=UDim2.fromOffset(520,400); bg.Position=UDim2.fromScale(0.5,0.5); bg.AnchorPoint=Vector2.new(0.5,0.5); bg.BackgroundColor3=Color3.fromHex("#0F172A"); bg.Parent=gui; Instance.new("UICorner",bg).CornerRadius=UDim.new(0,16)
    local thumb=game.Players:GetUserThumbnailAsync(pl.UserId, Enum.ThumbnailType.HeadShot, Enum.ThumbnailSize.Size150x150)
    local img=Instance.new("ImageLabel"); img.Size=UDim2.fromOffset(80,80); img.Position=UDim2.fromOffset(20,20); img.Image=thumb; img.BackgroundTransparency=1; img.Parent=bg; Instance.new("UICorner",img).CornerRadius=UDim.new(1,0)
    local nameLbl=Instance.new("TextLabel"); nameLbl.Size=UDim2.fromOffset(300,28); nameLbl.Position=UDim2.fromOffset(110,24); nameLbl.BackgroundTransparency=1; nameLbl.Text=pl.Name.."  •  Level 7"; nameLbl.TextColor3=Color3.new(1,1,1); nameLbl.Font=Enum.Font.GothamBold; nameLbl.TextSize=16; nameLbl.TextXAlignment=Enum.TextXAlignment.Left; nameLbl.Parent=bg
    local stats=Instance.new("TextLabel"); stats.Size=UDim2.new(1,-24,0,180); stats.Position=UDim2.fromOffset(12,110); stats.BackgroundTransparency=1; stats.Text="🏆 Побед: 12  •  Поражений: 5\n⏱️ Время игры: 8.4 ч\n🗺️ Захвачено провинций: 84\n🇺🇸 Любимая страна: USA\n🪙 Coins: 1 240  •  XP: 3 420\n📈 Уровень жизни: 72%"; stats.TextColor3=Color3.fromHex("#CBD5E1"); stats.Font=Enum.Font.Gotham; stats.TextSize=12; stats.TextXAlignment=Enum.TextXAlignment.Left; stats.TextYAlignment=Enum.TextYAlignment.Top; stats.Parent=bg
    local close=Instance.new("TextButton"); close.Size=UDim2.fromOffset(32,32); close.Position=UDim2.fromScale(1,0); close.AnchorPoint=Vector2.new(1,0); close.Text="✕"; close.BackgroundColor3=Color3.fromHex("#EF4444"); close.TextColor3=Color3.new(1,1,1); close.Font=Enum.Font.GothamBold; close.Parent=bg; Instance.new("UICorner",close).CornerRadius=UDim.new(1,0)
    close.MouseButton1Click:Connect(function() gui:Destroy() end)
end
return ProfileUI
