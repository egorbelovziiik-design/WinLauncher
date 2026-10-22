local TutorialUI = {}
local steps = {
    "Перемести камеру WASD — проведи мышью к краю экрана",
    "Приблизь карту колесом — найди свою столицу",
    "Кликни по провинции — выбери государство",
    "Открой меню строительства слева — выбери House",
    "Поставь здание — зелёный призрак, клик",
    "Построй электростанцию — CoalPlant",
    "Построй завод — MilitaryFactory",
    "Создай армию — кнопка в HUD справа",
    "Перемести армию в соседнюю провинцию",
    "Захвати территорию — объяви войну",
    "Заключи альянс — кнопка 🤝",
    "Обменяй ресурсы — торговля",
    "Исследуй технологию — Army → ModernInfantry",
    "Проверь бюджет — верхняя панель",
    "Подпиши мир — заверши войну",
}
local idx=1
function TutorialUI.start()
    local pg=game.Players.LocalPlayer:WaitForChild("PlayerGui")
    local gui=Instance.new("ScreenGui"); gui.Name="Tutorial"; gui.ResetOnSpawn=false; gui.Parent=pg
    local frame=Instance.new("Frame"); frame.Size=UDim2.fromOffset(420,150); frame.Position=UDim2.fromScale(0.5,0.18); frame.AnchorPoint=Vector2.new(0.5,0); frame.BackgroundColor3=Color3.fromHex("#111827"); frame.Parent=gui; Instance.new("UICorner",frame).CornerRadius=UDim.new(0,14)
    local pad=Instance.new("UIPadding", frame); pad.PaddingLeft=UDim.new(0,16); pad.PaddingRight=UDim.new(0,16); pad.PaddingTop=UDim.new(0,14)
    local title=Instance.new("TextLabel"); title.Size=UDim2.new(1,0,0,22); title.BackgroundTransparency=1; title.Text="🎓 ОБУЧЕНИЕ — ШАГ "..idx.."/"..#steps; title.TextColor3=Color3.fromHex("#FACC15"); title.Font=Enum.Font.GothamBold; title.TextSize=12; title.Parent=frame
    local txt=Instance.new("TextLabel"); txt.Name="StepText"; txt.Size=UDim2.new(1,0,0,60); txt.Position=UDim2.fromOffset(0,28); txt.BackgroundTransparency=1; txt.Text=steps[1]; txt.TextColor3=Color3.new(1,1,1); txt.Font=Enum.Font.GothamMedium; txt.TextSize=13; txt.TextWrapped=true; txt.Parent=frame
    local btn=Instance.new("TextButton"); btn.Size=UDim2.new(1,0,0,38); btn.Position=UDim2.fromOffset(0,96); btn.Text="ДАЛЕЕ →"; btn.Font=Enum.Font.GothamBold; btn.TextSize=13; btn.BackgroundColor3=Color3.fromHex("#22C55E"); btn.TextColor3=Color3.new(1,1,1); btn.Parent=frame; Instance.new("UICorner",btn).CornerRadius=UDim.new(0,10)
    local skip=Instance.new("TextButton"); skip.Size=UDim2.fromOffset(100,28); skip.Position=UDim2.fromScale(1,0); skip.AnchorPoint=Vector2.new(1,0); skip.Text="Пропустить"; skip.Font=Enum.Font.Gotham; skip.TextSize=11; skip.BackgroundTransparency=1; skip.TextColor3=Color3.fromHex("#94A3B8"); skip.Parent=frame
    btn.MouseButton1Click:Connect(function()
        idx+=1
        if idx>#steps then
            title.Text="✅ ОБУЧЕНИЕ ЗАВЕРШЕНО! +500 COINS"
            txt.Text="Ты готов к мировому господству. Удачи, стратег!"
            btn.Text="ЗАВЕРШИТЬ"
            btn.MouseButton1Click:Once(function()
                gui:Destroy()
                local rem=game:GetService("ReplicatedStorage").Remotes:FindFirstChild("ToggleTutorial")
                if rem then rem:FireServer(true) end
                -- награда локально
                local s=Instance.new("Sound"); s.SoundId="rbxassetid://12222030"; s.Volume=0.8; s.Parent=workspace; s:Play(); game.Debris:AddItem(s,3)
            end)
            return
        end
        title.Text="🎓 ОБУЧЕНИЕ — ШАГ "..idx.."/"..#steps
        txt.Text=steps[idx]
    end)
    skip.MouseButton1Click:Connect(function() gui:Destroy() end)
    -- стрелка-подсветка (простая)
    local arrow=Instance.new("TextLabel"); arrow.Size=UDim2.fromOffset(40,40); arrow.Position=UDim2.fromScale(0.12,0.5); arrow.BackgroundTransparency=1; arrow.Text="👉"; arrow.TextSize=28; arrow.Parent=gui
    game:GetService("TweenService"):Create(arrow, TweenInfo.new(0.6, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut, -1, true), {Position=UDim2.fromScale(0.14,0.5)}):Play()
end
return TutorialUI
