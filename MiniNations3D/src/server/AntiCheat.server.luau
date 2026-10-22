-- AntiCheat — серверная валидация
local RS=game:GetService("ReplicatedStorage")
local remotes=RS:WaitForChild("Remotes",10)
if not remotes then return end
-- Пример: проверка BuildRequest дистанции
remotes:FindFirstChild("BuildRequest").OnServerEvent:Connect(function(pl, provId, building)
    -- нельзя строить если провинция не твоя (упрощено: всегда можно в одиночке)
    -- но нельзя спамить
    -- логика уже в Main.server checkLimit
end)
print("[AntiCheat] enabled")
