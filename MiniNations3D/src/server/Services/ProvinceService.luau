local ProvinceService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
function ProvinceService.get(id:string) return ProvinceData.get(id) end
function ProvinceService.byOwner(owner:string)
    local t={} for _,p in pairs(ProvinceData.list) do if p.owner==owner then table.insert(t,p) end end return t
end
function ProvinceService.info(id:string)
    local p=ProvinceData.get(id)
    if not p then return nil end
    return {id=p.id, name=p.name, owner=p.owner, population=p.population, economy=p.economy, infrastructure=p.infrastructure, stability=p.stability, buildings=p.buildings, army=p.army, pos=p.pos}
end
return ProvinceService
