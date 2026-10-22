local SaveService = {}
local DSS = game:GetService("DataStoreService")
local CoinService = require(script.Parent.CoinService)
local profileStore
pcall(function() profileStore = DSS:GetDataStore("MiniNations_Profiles_v1") end)

local cache: {[number]: any} = {}
local autosaveInterval = 60

function SaveService.load(player:Player)
    local data
    if profileStore then
        local ok, res = pcall(function() return profileStore:GetAsync("p_"..player.UserId) end)
        if ok and res then data=res end
    end
    if data then
        cache[player.UserId]=data
        -- hydrate CoinService
        local c = CoinService.ensure(player)
        for k,v in pairs(data) do c[k]=v end
    else
        cache[player.UserId]=CoinService.ensure(player)
    end
    return cache[player.UserId]
end

function SaveService.save(player:Player)
    local p = CoinService.getProfile(player) or cache[player.UserId]
    if not p or not profileStore then return end
    pcall(function() profileStore:SetAsync("p_"..player.UserId, p) end)
    -- отдельный ключ для мультиплеерных сессий
    pcall(function()
        local ms = DSS:GetDataStore("MiniNations_Sessions_v1")
        if p.lastSession then ms:SetAsync("s_"..player.UserId.."_"..os.time(), p.lastSession) end
    end)
end

function SaveService.startAutosave()
    task.spawn(function()
        while true do task.wait(autosaveInterval)
            for _,pl in ipairs(game.Players:GetPlayers()) do SaveService.save(pl) end
        end
    end)
    game.Players.PlayerRemoving:Connect(function(pl) SaveService.save(pl); cache[pl.UserId]=nil end)
    game:BindToClose(function()
        for _,pl in ipairs(game.Players:GetPlayers()) do SaveService.save(pl) end
        task.wait(2)
    end)
end
return SaveService
