--!nonstrict
local Players = game:GetService("Players")
local ReplicatedFirst = game:GetService("ReplicatedFirst")
local player = Players.LocalPlayer
local gui = Instance.new("ScreenGui"); gui.Name = "LastCityLoading"; gui.IgnoreGuiInset = true; gui.DisplayOrder = 100; gui.ResetOnSpawn = false
local frame = Instance.new("Frame"); frame.Size = UDim2.fromScale(1, 1); frame.BackgroundColor3 = Color3.fromRGB(16, 19, 23); frame.Parent = gui
local title = Instance.new("TextLabel"); title.Size = UDim2.fromScale(1, 0.15); title.Position = UDim2.fromScale(0, 0.38)
title.BackgroundTransparency = 1; title.Text = "LAST CITY 3D"; title.Font = Enum.Font.GothamBlack; title.TextSize = 48; title.TextColor3 = Color3.fromRGB(249, 115, 22); title.Parent = frame
local note = Instance.new("TextLabel"); note.Size = UDim2.fromScale(0.9, 0.15); note.Position = UDim2.fromScale(0.05, 0.52)
note.BackgroundTransparency = 1; note.TextWrapped = true; note.Font = Enum.Font.Gotham; note.TextSize = 16; note.TextColor3 = Color3.fromRGB(171, 184, 192); note.Parent = frame
gui.Parent = player:WaitForChild("PlayerGui"); ReplicatedFirst:RemoveDefaultLoadingScreen()
local started = os.clock()
while not player:GetAttribute("LastCityClientReady") do
    note.Text = os.clock() - started > 18 and "Соединяемся с сервером и загружаем сохранение…\nНе закрывайте Studio во время загрузки данных." or "DEAD WORLD • Восстанавливаем связь с убежищем" .. string.rep(".", math.floor(os.clock()) % 4)
    task.wait(0.3)
end
gui:Destroy()
