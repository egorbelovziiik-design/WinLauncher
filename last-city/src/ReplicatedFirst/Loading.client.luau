--!nonstrict
local Players = game:GetService("Players")
local ReplicatedFirst = game:GetService("ReplicatedFirst")
local player = Players.LocalPlayer
local gui = Instance.new("ScreenGui"); gui.Name = "LastCityLoading"; gui.IgnoreGuiInset = true; gui.DisplayOrder = 100; gui.ResetOnSpawn = false
local frame = Instance.new("Frame"); frame.Size = UDim2.fromScale(1, 1); frame.BackgroundColor3 = Color3.fromRGB(16, 19, 23); frame.Parent = gui
local title = Instance.new("TextLabel"); title.Size = UDim2.fromScale(1, 0.15); title.Position = UDim2.fromScale(0, 0.32)
title.BackgroundTransparency = 1; title.Text = "LAST CITY 3D"; title.Font = Enum.Font.GothamBlack; title.TextSize = 44; title.TextColor3 = Color3.fromRGB(249, 115, 22); title.Parent = frame
local note = Instance.new("TextLabel"); note.Size = UDim2.fromScale(0.9, 0.3); note.Position = UDim2.fromScale(0.05, 0.48)
note.BackgroundTransparency = 1; note.TextWrapped = true; note.Font = Enum.Font.Gotham; note.TextSize = 15; note.TextColor3 = Color3.fromRGB(171, 184, 192); note.Parent = frame
gui.Parent = player:WaitForChild("PlayerGui"); ReplicatedFirst:RemoveDefaultLoadingScreen()
local connection
local function complete()
    if not player:GetAttribute("LastCityClientReady") then return false end
    if connection then connection:Disconnect() end
    gui:Destroy(); return true
end
connection = player:GetAttributeChangedSignal("LastCityClientReady"):Connect(complete)
local started = os.clock()
while gui.Parent and not complete() do
    local elapsed = math.floor(os.clock() - started)
    local diagnostic = "Server: " .. tostring(workspace:GetAttribute("LastCityServerPhase") or "waiting")
        .. " / Profile: " .. tostring(player:GetAttribute("LastCityLoadPhase") or "waiting")
        .. " / Client: " .. tostring(player:GetAttribute("LastCityClientPhase") or "waiting")
    local err = workspace:GetAttribute("LastCityServerError") or player:GetAttribute("LastCityLoadError") or player:GetAttribute("LastCityClientError")
    if err or elapsed >= 45 then
        title.Text = "НЕ УДАЛОСЬ ЗАПУСТИТЬ ИГРУ"
        title.TextSize = 28
        note.TextColor3 = Color3.fromRGB(247, 163, 130)
        note.Text = (err or "Сервер не прислал готовое состояние за 45 секунд.") .. "\n\n" .. diagnostic
            .. "\n\nНе ждите бесконечно. Остановите тест и откройте View → Output.\nПришлите первую ошибку с префиксом [LastCity]. Версия 0.5.0."
        warn("[LastCity LOADING] " .. diagnostic .. " / " .. tostring(err or "timeout"))
        break -- Keep a real error screen, never fake readiness or hide a broken boot.
    end
    note.Text = "IRON HORIZON / 0.5.0\nЗагрузка: " .. elapsed .. " с\n" .. diagnostic .. "\nStudio по умолчанию: локальная сессия без облачного сохранения."
    task.wait(0.25)
end
