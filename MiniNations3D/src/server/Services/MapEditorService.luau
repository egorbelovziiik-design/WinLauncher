local MapEditorService = {}
local DSS = game:GetService("DataStoreService")
local store
pcall(function() store=DSS:GetDataStore("MiniNations_Maps_v1") end)
local cache: {[string]: any} = {}

function MapEditorService.save(player:Player, mapName:string, data:any): boolean
    -- защита: лимит размера ~4MB, проверяем JSON длину
    local ok, json = pcall(function() return game:GetService("HttpService"):JSONEncode(data) end)
    if not ok then return false end
    if #json > 3800000 then return false end -- резерв под ключ
    -- простая фильтрация контента: запрещаем слова
    local lower=json:lower()
    if lower:find("badword") then return false end
    cache[mapName]=data
    if store then pcall(function() store:SetAsync("map_"..player.UserId.."_"..mapName, data) end) end
    local rem=game.ReplicatedStorage.Remotes:FindFirstChild("MapSaved")
    if rem then rem:FireClient(player, mapName) end
    return true
end
function MapEditorService.load(player:Player, mapName:string)
    if cache[mapName] then return cache[mapName] end
    if store then
        local ok,res=pcall(function() return store:GetAsync("map_"..player.UserId.."_"..mapName) end)
        if ok and res then cache[mapName]=res; return res end
    end
    return nil
end
function MapEditorService.list(player:Player): {string}
    local keys={} for k,_ in pairs(cache) do table.insert(keys,k) end return keys
end
return MapEditorService
