local MainMenu = {}
local TweenService = game:GetService("TweenService")
local Players = game:GetService("Players")
local pl = Players.LocalPlayer

-- Создаёт стильное тёмное меню с 3D фоном
function MainMenu.build(): ScreenGui
    local gui = Instance.new("ScreenGui")
    gui.Name="MainMenu"; gui.IgnoreGuiInset=true; gui.ResetOnSpawn=false; gui.ZIndexBehavior=Enum.ZIndexBehavior.Sibling
    gui.DisplayOrder=10

    -- Фон градиент
    local bg = Instance.new("Frame"); bg.Size=UDim2.fromScale(1,1); bg.BackgroundColor3=Color3.fromHex("#0B0E14"); bg.BorderSizePixel=0; bg.Parent=gui
    local grad = Instance.new("UIGradient"); grad.Color=ColorSequence.new{ColorSequenceKeypoint.new(0,Color3.fromHex("#0F172A")), ColorSequenceKeypoint.new(1,Color3.fromHex("#020617"))}; grad.Rotation=90; grad.Parent=bg
    -- Декоративные частицы
    for i=1,9 do
        local dot=Instance.new("Frame"); dot.Size=UDim2.fromOffset(3,3); dot.Position=UDim2.fromScale(math.random(), math.random()*0.6+0.1); dot.BackgroundColor3=Color3.fromHex("#38BDF8"); dot.BackgroundTransparency=0.4; dot.BorderSizePixel=0; dot.Parent=bg
        Instance.new("UICorner", dot).CornerRadius=UDim.new(1,0)
    end

    -- Заголовок
    local title = Instance.new("TextLabel"); title.Size=UDim2.fromOffset(680,72); title.Position=UDim2.fromScale(0.5,0.08); title.AnchorPoint=Vector2.new(0.5,0); title.BackgroundTransparency=1; title.Text="MINI NATIONS 3D"; title.TextColor3=Color3.fromHex("#F8FAFC"); title.Font=Enum.Font.GothamBlack; title.TextSize=54; title.TextScaled=false; title.Parent=bg
    local sub = Instance.new("TextLabel"); sub.Size=UDim2.fromOffset(680,28); sub.Position=UDim2.fromScale(0.5,0.15); sub.AnchorPoint=Vector2.new(0.5,0); sub.BackgroundTransparency=1; sub.Text="WORLD DOMINATION — ULTIMATE EDITION  •  PREMIUM LOW POLY"; sub.TextColor3=Color3.fromHex("#38BDF8"); sub.Font=Enum.Font.GothamMedium; sub.TextSize=12; sub.Parent=bg

    -- Левая панель кнопок (стек)
    local panel = Instance.new("Frame"); panel.Size=UDim2.fromOffset(360,560); panel.Position=UDim2.fromScale(0.06,0.22); panel.BackgroundColor3=Color3.fromHex("#111827"); panel.BackgroundTransparency=0.15; panel.BorderSizePixel=0; panel.Parent=bg
    Instance.new("UICorner", panel).CornerRadius=UDim.new(0,18)
    local stroke=Instance.new("UIStroke", panel); stroke.Color=Color3.fromHex("#1E293B"); stroke.Thickness=1; stroke.Transparency=0.3
    local pad=Instance.new("UIPadding", panel); pad.PaddingLeft=UDim.new(0,18); pad.PaddingRight=UDim.new(0,18); pad.PaddingTop=UDim.new(0,20); pad.PaddingBottom=UDim.new(0,20)
    local list=Instance.new("UIListLayout", panel); list.FillDirection=Enum.FillDirection.Vertical; list.Padding=UDim.new(0,10); list.SortOrder=Enum.SortOrder.LayoutOrder

    local function btn(text:string, accent:Color3, icon:string, order:number)
        local b=Instance.new("TextButton"); b.LayoutOrder=order; b.Size=UDim2.new(1,0,0,48); b.BackgroundColor3=Color3.fromHex("#1E293B"); b.Text=""; b.AutoButtonColor=false; b.Parent=panel
        Instance.new("UICorner", b).CornerRadius=UDim.new(0,12)
        local lbl=Instance.new("TextLabel"); lbl.Size=UDim2.fromScale(1,1); lbl.BackgroundTransparency=1; lbl.Text=icon.."  "..text; lbl.TextColor3=Color3.new(1,1,1); lbl.Font=Enum.Font.GothamBold; lbl.TextSize=14; lbl.TextXAlignment=Enum.TextXAlignment.Left; lbl.Parent=b
        local pad2=Instance.new("UIPadding", lbl); pad2.PaddingLeft=UDim.new(0,16)
        local acc=Instance.new("Frame"); acc.Size=UDim2.fromOffset(4,28); acc.Position=UDim2.new(0,8,0.5,0); acc.AnchorPoint=Vector2.new(0,0.5); acc.BackgroundColor3=accent; acc.BorderSizePixel=0; acc.Parent=b; Instance.new("UICorner", acc).CornerRadius=UDim.new(1,0)
        b.MouseEnter:Connect(function() TweenService:Create(b, TweenInfo.new(0.15), {BackgroundColor3=Color3.fromHex("#334155")}):Play() end)
        b.MouseLeave:Connect(function() TweenService:Create(b, TweenInfo.new(0.15), {BackgroundColor3=Color3.fromHex("#1E293B")}):Play() end)
        return b
    end

    local b1=btn("ОДИНОЧНАЯ ИГРА", Color3.fromHex("#22C55E"), "▶",1)
    local b2=btn("МУЛЬТИПЛЕЕР", Color3.fromHex("#3B82F6"), "🌐",2)
    local b3=btn("РЕДАКТОР КАРТ", Color3.fromHex("#F59E0B"), "🗺️",3)
    local b4=btn("ОБУЧЕНИЕ", Color3.fromHex("#A78BFA"), "🎓",4)
    local b5=btn("МАГАЗИН", Color3.fromHex("#EC4899"), "🛒",5)
    local b6=btn("ДОСТИЖЕНИЯ", Color3.fromHex("#FACC15"), "🏆",6)
    local b7=btn("ПРОФИЛЬ", Color3.fromHex("#06B6D4"), "👤",7)
    local b8=btn("НАСТРОЙКИ", Color3.fromHex("#94A3B8"), "⚙️",8)
    local b9=btn("ОБНОВЛЕНИЯ", Color3.fromHex("#64748B"), "📜",9)

    -- нижняя полоса
    local bottom = Instance.new("Frame"); bottom.Size=UDim2.new(1,0,0,46); bottom.Position=UDim2.fromScale(0,1); bottom.AnchorPoint=Vector2.new(0,1); bottom.BackgroundColor3=Color3.fromHex("#020617"); bottom.BackgroundTransparency=0.35; bottom.BorderSizePixel=0; bottom.Parent=bg
    local bpad=Instance.new("UIPadding", bottom); bpad.PaddingLeft=UDim.new(0,18); bpad.PaddingRight=UDim.new(0,18)
    local ver=Instance.new("TextLabel"); ver.Size=UDim2.fromScale(0.3,1); ver.BackgroundTransparency=1; ver.Text="v1.0.1 Ultimate  •  "..(pl and pl.Name or "Player"); ver.TextColor3=Color3.fromHex("#64748B"); ver.Font=Enum.Font.Gotham; ver.TextSize=11; ver.TextXAlignment=Enum.TextXAlignment.Left; ver.Parent=bottom
    local coinsLbl=Instance.new("TextLabel"); coinsLbl.Name="CoinsLabel"; coinsLbl.Size=UDim2.fromScale(0.3,1); coinsLbl.Position=UDim2.fromScale(0.7,0); coinsLbl.BackgroundTransparency=1; coinsLbl.Text="🪙 500 COINS"; coinsLbl.TextColor3=Color3.fromHex("#FACC15"); coinsLbl.Font=Enum.Font.GothamBold; coinsLbl.TextSize=12; coinsLbl.TextXAlignment=Enum.TextXAlignment.Right; coinsLbl.Parent=bottom

    -- Подключить логику кнопок (заглушки с переходом)
    local function toast(msg:string)
        local t=Instance.new("TextLabel"); t.Size=UDim2.fromOffset(340,42); t.Position=UDim2.fromScale(0.5,0.92); t.AnchorPoint=Vector2.new(0.5,0); t.BackgroundColor3=Color3.fromHex("#1E293B"); t.TextColor3=Color3.new(1,1,1); t.Text=msg; t.Font=Enum.Font.GothamMedium; t.TextSize=13; t.Parent=gui; Instance.new("UICorner",t).CornerRadius=UDim.new(0,10); task.delay(2, function() t:Destroy() end)
    end

    b1.MouseButton1Click:Connect(function()
        gui.Enabled=false
        local HUD=require(script.Parent.HUD)
        HUD.showGame()
        -- показать выбор года/страны
        require(script.Parent.Parent.Controllers.AudioController).play("peace",0.5)
        toast("Кампания 2026 загружена — выбери провинцию!")
    end)
    b2.MouseButton1Click:Connect(function() toast("Мультиплеер: создай комнату → пригласи друзей → ГОТОВ") end)
    b3.MouseButton1Click:Connect(function()
        local editor = require(script.Parent.Parent.Controllers.MapController)
        toast("Редактор карт — кисти активны (см. панель справа)")
        -- открыть редактор UI
        local MapEditor = require(script.Parent.MapEditor)
        MapEditor.open()
    end)
    b4.MouseButton1Click:Connect(function()
        local tut=require(script.Parent.TutorialUI); tut.start()
    end)
    b5.MouseButton1Click:Connect(function()
        local shop=require(script.Parent.ShopUI); shop.open()
    end)
    b6.MouseButton1Click:Connect(function() toast("Достижения: 10 трофеев — собирай!") end)
    b7.MouseButton1Click:Connect(function() local prof=require(script.Parent.ProfileUI); prof.open() end)
    b8.MouseButton1Click:Connect(function() local s=require(script.Parent.SettingsUI); s.open() end)
    b9.MouseButton1Click:Connect(function() toast("Обновления: v1.0.1 — добавлены AI, торговля, погода") end)

    -- Анимация появления
    bg.BackgroundTransparency=1
    TweenService:Create(bg, TweenInfo.new(0.6, Enum.EasingStyle.Quad), {BackgroundTransparency=0}):Play()
    title.TextTransparency=1; TweenService:Create(title, TweenInfo.new(0.8), {TextTransparency=0}):Play()

    -- Обновление коинов
    local RS=game:GetService("ReplicatedStorage")
    local rem=RS:FindFirstChild("Remotes") and RS.Remotes:FindFirstChild("CoinsUpdated")
    if rem then rem.OnClientEvent:Connect(function(newTotal, delta, reason) coinsLbl.Text="🪙 "..tostring(newTotal).." COINS" end) end

    -- Аватар игрока слева внизу
    if pl then
        local thumb=Players:GetUserThumbnailAsync(pl.UserId, Enum.ThumbnailType.HeadShot, Enum.ThumbnailSize.Size100x100)
        local img=Instance.new("ImageLabel"); img.Size=UDim2.fromOffset(28,28); img.Position=UDim2.fromOffset(18,18); img.BackgroundTransparency=1; img.Image=thumb; img.Parent=bg
        Instance.new("UICorner", img).CornerRadius=UDim.new(1,0)
    end

    return gui
end
return MainMenu
