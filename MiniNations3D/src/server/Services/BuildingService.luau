local BuildingService = {}
local ProvinceData = require(game.ReplicatedStorage.Shared.ProvinceData)
local EconomyService = require(script.Parent.EconomyService)
local Costs = {
    House=120, Apartment=400, Skyscraper=1500, Shop=300, Mall=900, Office=700,
    CityHall=1200, School=500, Hospital=800, Park=200, Stadium=1400,
    CoalPlant=2000, Solar=1800, Wind=1600, Nuclear=5000,
    MilitaryFactory=2500, Barracks=1800, Airbase=3200, Port=2800,
    Road=150, Railway=400, Bridge=900,
}
function BuildingService.canBuild(player:Player, provinceId:string, building:string): (boolean,string)
    local prov = ProvinceData.get(provinceId)
    if not prov then return false, "No province" end
    local eco = EconomyService.get(prov.owner)
    if eco and eco.budget < (Costs[building] or 500) then return false, "Not enough budget" end
    if #prov.buildings >= 12 then return false, "Province full" end
    return true, ""
end
function BuildingService.build(player:Player, provinceId:string, building:string)
    local ok,msg = BuildingService.canBuild(player, provinceId, building)
    if not ok then return false, msg end
    local prov = ProvinceData.get(provinceId)
    local eco = EconomyService.get(prov.owner)
    eco.budget -= (Costs[building] or 500)
    table.insert(prov.buildings, building)
    -- добавить экономику/энергию
    if building:find("Plant") or building=="Solar" or building=="Nuclear" then prov.economy += 60 else prov.economy += 20 end
    prov.stability = math.clamp(prov.stability+1, 0, 100)
    local rem = game.ReplicatedStorage.Remotes:FindFirstChild("BuildingPlaced")
    if rem then rem:FireAllClients(provinceId, building, prov.pos) end
    return true, "Built"
end
function BuildingService.listCosts() return Costs end
return BuildingService
