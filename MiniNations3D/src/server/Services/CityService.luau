local CityService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
function CityService.found(player:Player, provinceId:string, cityName:string)
    local p=ProvinceData.get(provinceId)
    if not p then return false end
    if p.buildings[1]=="CityCenter" then return false, "Already city" end
    table.insert(p.buildings, 1, "CityCenter")
    p.population += math.random(5000,20000)
    local rem=game.ReplicatedStorage.Remotes:FindFirstChild("CityFounded")
    if rem then rem:FireAllClients(provinceId, cityName) end
    return true
end
return CityService
