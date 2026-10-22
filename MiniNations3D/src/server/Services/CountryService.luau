local CountryService = {}
local CountryData = require(game.ReplicatedStorage.Shared.CountryData)
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local EconomyService = require(script.Parent.EconomyService)

function CountryService.get(id:string)
    return CountryData.byId[id], EconomyService.get(id)
end
function CountryService.list()
    local t={} for _,c in ipairs(CountryData) do if type(c)=="table" and c.id then table.insert(t,c) end end return t
end
function CountryService.provincesOf(countryId:string)
    local t={} for _,p in pairs(ProvinceData.list) do if p.owner==countryId then table.insert(t,p) end end return t
end
function CountryService.transferProvince(provId:string, newOwner:string)
    local p = ProvinceData.get(provId)
    if not p then return false end
    p.owner = newOwner
    -- огонь Remote для обновления цвета границ
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("ProvinceOwnerChanged")
    if rem then rem:FireAllClients(provId, newOwner) end
    return true
end
return CountryService
