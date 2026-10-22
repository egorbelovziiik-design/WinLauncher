-- Main.client — точка входа клиента
print("🌍 Mini Nations 3D — Client starting...")

local RS = game:GetService("ReplicatedStorage")
local Players = game:GetService("Players")
local pl = Players.LocalPlayer

-- дождаться ремоутов
local remotes = RS:WaitForChild("Remotes", 10)

-- Инициализация контроллеров
local CameraController = require(script.Controllers.CameraController)
local MapController = require(script.Controllers.MapController)
local AudioController = require(script.Controllers.AudioController)
local BuildingController = require(script.Controllers.BuildingController)

CameraController.init()
AudioController.init()
AudioController.play("menu", 0.45)

-- Построить карту (после загрузки)
MapController.init()

-- Построить UI
local MainMenu = require(script.UI.MainMenu)
local HUD = require(script.UI.HUD)

local pg = pl:WaitForChild("PlayerGui")
-- очистить старый
for _,g in ipairs(pg:GetChildren()) do if g.Name=="MainMenu" or g.Name=="HUD" then g:Destroy() end end

local menuGui = MainMenu.build()
menuGui.Parent = pg

local hudGui = HUD.build()
hudGui.Parent = pg -- внутри HUD.Enabled=false до начала игры

-- Профиль
local coins = 500
if remotes and remotes:FindFirstChild("GetProfile") then
    local ok, prof = pcall(function() return remotes.GetProfile:InvokeServer() end)
    if ok and prof then
        coins = prof.coins or 500
        print("[Client] Profile loaded — coins:", coins, " level:", prof.level)
        if prof.tutorialDone == false then
            -- показать приветствие
            task.delay(1, function()
                local gui=Instance.new("ScreenGui"); gui.Name="Welcome"; gui.ResetOnSpawn=false; gui.Parent=pg
                local f=Instance.new("Frame"); f.Size=UDim2.fromOffset(420,180); f.Position=UDim2.fromScale(0.5,0.5); f.AnchorPoint=Vector2.new(0.5,0.5); f.BackgroundColor3=Color3.fromHex("#0F172A"); f.Parent=gui; Instance.new("UICorner",f).CornerRadius=UDim.new(0,16)
                local t=Instance.new("TextLabel"); t.Size=UDim2.new(1,0,0,60); t.BackgroundTransparency=1; t.Text="Добро пожаловать в Mini Nations 3D!"; t.TextColor3=Color3.new(1,1,1); t.Font=Enum.Font.GothamBold; t.TextSize=16; t.Parent=f
                local b1=Instance.new("TextButton"); b1.Size=UDim2.new(1,-24,0,36); b1.Position=UDim2.fromOffset(12,70); b1.Text="🎓 ПРОЙТИ ОБУЧЕНИЕ"; b1.BackgroundColor3=Color3.fromHex("#22C55E"); b1.TextColor3=Color3.new(1,1,1); b1.Font=Enum.Font.GothamBold; b1.Parent=f; Instance.new("UICorner",b1).CornerRadius=UDim.new(0,10)
                local b2=Instance.new("TextButton"); b2.Size=UDim2.new(1,-24,0,36); b2.Position=UDim2.fromOffset(12,114); b2.Text="⏭️ ПРОПУСТИТЬ"; b2.BackgroundColor3=Color3.fromHex("#334155"); b2.TextColor3=Color3.new(1,1,1); b2.Font=Enum.Font.Gotham; b2.Parent=f; Instance.new("UICorner",b2).CornerRadius=UDim.new(0,10)
                b1.MouseButton1Click:Connect(function() gui:Destroy(); require(script.UI.TutorialUI).start() end)
                b2.MouseButton1Click:Connect(function() gui:Destroy() end)
            end)
        end
    end
end

-- Глобальные слушатели ремоутов для уведомлений
if remotes then
    local function notify(msg:string, color:Color3?)
        local gui=Instance.new("ScreenGui"); gui.Name="Notify"; gui.ResetOnSpawn=false; gui.Parent=pg
        local l=Instance.new("TextLabel"); l.Size=UDim2.fromOffset(360,36); l.Position=UDim2.fromScale(0.5,0.08); l.AnchorPoint=Vector2.new(0.5,0); l.BackgroundColor3=color or Color3.fromHex("#1E293B"); l.TextColor3=Color3.new(1,1,1); l.Text=msg; l.Font=Enum.Font.GothamMedium; l.TextSize=12; l.Parent=gui; Instance.new("UICorner",l).CornerRadius=UDim.new(0,10)
        task.delay(2.5, function() gui:Destroy() end)
    end
    remotes:FindFirstChild("WarDeclared").OnClientEvent:Connect(function(_,a,d) notify("⚔️ Война: "..a.." → "..d, Color3.fromHex("#EF4444")) ; AudioController.playSFX("war") end)
    remotes:FindFirstChild("BattleResult").OnClientEvent:Connect(function(_,prov, win) notify((win and "✓ Победа в " or "✗ Поражение в ")..prov, win and Color3.fromHex("#22C55E") or Color3.fromHex("#EF4444")) end)
    remotes:FindFirstChild("AchievementUnlocked").OnClientEvent:Connect(function(id, def) notify("🏆 Достижение: "..def.name.." +"..def.reward.." COINS", Color3.fromHex("#FACC15")) ; AudioController.playSFX("achieve") end)
    remotes:FindFirstChild("CoinsUpdated").OnClientEvent:Connect(function(new, delta) notify("🪙 +"..delta.." COINS", Color3.fromHex("#FACC15")) end)
end

-- Мобильная поддержка — показать джойстик подсказку
if game:GetService("UserInputService").TouchEnabled then
    print("[Client] Touch device detected — edge scroll & pinch enabled")
end

-- День/Ночь цикл (визуальный)
task.spawn(function()
    local lighting = game:GetService("Lighting")
    while true do
        task.wait(40)
        -- плавный переход
        local ts = game:GetService("TweenService")
        ts:Create(lighting, TweenInfo.new(8), {ClockTime = lighting.ClockTime > 12 and 2 or 14}):Play()
    end
end)

print("✅ Mini Nations 3D Client ready")
